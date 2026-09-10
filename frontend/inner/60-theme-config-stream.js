// [KWC 유지보수 주석 / KWC maintenance notes]
// 사용자 테마·폰트·색상 적용, 서버 config 반영, SSE 연결/재연결, resume refresh처럼 런타임 설정과 연결 수명주기를 담당한다.
// This fragment owns runtime configuration and connection lifecycle: user theme/font/color application, server config, SSE connect/reconnect, and resume refresh.
// SSE는 일시적인 연결 제한이나 네트워크 오류를 정상 상황으로 취급하고 재연결해야 하며, 실패 한 번을 영구 offline 상태로 고정해서는 안 된다.
// SSE must treat temporary connection limits and network errors as recoverable conditions and reconnect; one failure must never permanently lock the UI offline.
// 테마 값은 CSS custom property를 통해 전달해 light/dark/system/high-contrast가 같은 컴포넌트 규칙을 공유하도록 유지한다.
// Theme values are propagated through CSS custom properties so light/dark/system/high-contrast variants can share the same component rules.

  function formatDecimalNumber(value, digits = 2) {
    value = Number(value);
    if (!Number.isFinite(value)) value = 0;
    const fixed = value.toFixed(Math.max(0, Math.min(6, Math.floor(Number(digits) || 0))));
    return fixed.replace(/\.0+$/, "").replace(/(\.\d*?)0+$/, "$1");
  }

  function pxLabel(value) {
    return formatDecimalNumber(value, 2) + "px";
  }

  function fontPx(value, fallback) {
    value = Number(value);
    if (!Number.isFinite(value) || value <= 0) value = fallback;
    return pxLabel(Math.max(8, Math.min(36, value)));
  }

  function savedUserFontSize() {
    const v = Number(localStorage.getItem("kwc.userFontSize"));
    return Number.isFinite(v) && v >= 8 && v <= 36 ? v : null;
  }

  function savedUserFontFamily() {
    return String(localStorage.getItem("kwc.userFontFamily") || "");
  }

  function effectiveBaseFontSize() {
    const c = state.config || {};
    const user = c.uiUserPreferencesControl === false ? null : savedUserFontSize();
    return user == null ? Number(c.uiFontSize || 13) : user;
  }

  function setUserFontSize(value, persist = true) {
    value = Number(value);
    if (!Number.isFinite(value)) return;
    value = Math.max(8, Math.min(36, value));
    state.liveUserFontSize = value;
    if (persist) {
      localStorage.setItem("kwc.userFontSize", formatDecimalNumber(value, 2));
    }
    applyFontSizeConfig();
    applyMediaViewportConfig();
  }

  function resetUserFontSize() {
    state.liveUserFontSize = null;
    localStorage.removeItem("kwc.userFontSize");
    applyFontSizeConfig();
    applyMediaViewportConfig();
  }

  function setUserFontFamily(value) {
    value = normalizeFontFamilyPreference(value);
    if (value) localStorage.setItem("kwc.userFontFamily", value);
    else localStorage.removeItem("kwc.userFontFamily");
    applyThemeConfig();
  }

  function resetUserFontFamily() {
    localStorage.removeItem("kwc.userFontFamily");
    applyThemeConfig();
  }

  function cssFontString(value) {
    return String(value || "").replace(/\\/g, "\\\\").replace(/"/g, "\\\"").replace(/\n/g, " ");
  }

  function normalizeFontFamilyPreference(value) {
    value = String(value || "").trim();
    if (!value) return "";
    // Advanced users may enter a full CSS font-family list.
    // Keep lists, quoted names, CSS variables, and generic families as-is.
    if (value.includes(",") || value.includes("'") || value.includes('"')) return value;
    if (/^(serif|sans-serif|monospace|cursive|fantasy|system-ui|ui-serif|ui-sans-serif|ui-monospace|emoji|math|fangsong)$/i.test(value)) return value;
    if (/^(var|inherit|initial|unset|revert)\s*\(/i.test(value)) return value;
    // A single installed font family such as Malgun Gothic or 맑은 고딕 must be quoted for reliable CSS parsing.
    return `"${cssFontString(value)}", sans-serif`;
  }

  function primaryFontFamilyName(value) {
    value = String(value || "").trim();
    if (!value) return "";
    if (value[0] === '"' || value[0] === "'") {
      const quote = value[0];
      let out = "";
      for (let i = 1; i < value.length; i++) {
        const ch = value[i];
        if (ch === "\\" && i + 1 < value.length) {
          out += value[++i];
          continue;
        }
        if (ch === quote) return out.trim();
        out += ch;
      }
      return out.trim();
    }
    const comma = value.indexOf(",");
    return (comma >= 0 ? value.slice(0, comma) : value).trim();
  }

  function isGenericFontFamilyName(value) {
    return /^(serif|sans-serif|monospace|cursive|fantasy|system-ui|ui-serif|ui-sans-serif|ui-monospace|emoji|math|fangsong)$/i.test(String(value || "").trim());
  }

  function fontDetectionStatus(value) {
    const raw = String(value || "").trim();
    if (!raw) return {state: "empty", name: ""};
    const normalized = normalizeFontFamilyPreference(raw);
    const primary = primaryFontFamilyName(normalized) || primaryFontFamilyName(raw);
    if (!primary) return {state: "unknown", name: raw};
    if (isGenericFontFamilyName(primary)) return {state: "generic", name: primary};

    try {
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext && canvas.getContext("2d");
      if (!ctx) return {state: "unknown", name: primary};
      const sample = "mmmmmmmmmmiiiiiiiWWWWW 가나다漢字12345";
      const size = "72px ";
      const bases = ["monospace", "serif", "sans-serif"];
      const baseWidths = bases.map(base => {
        ctx.font = size + base;
        return ctx.measureText(sample).width;
      });
      const cssCandidate = `"${cssFontString(primary)}"`;
      const differs = bases.some((base, idx) => {
        ctx.font = size + cssCandidate + ", " + base;
        const width = ctx.measureText(sample).width;
        return Math.abs(width - baseWidths[idx]) > 0.5;
      });
      return {state: differs ? "detected" : "notDetected", name: primary, normalized};
    } catch (_) {
      return {state: "unknown", name: primary};
    }
  }

  function fontFormatForFile(file) {
    file = String(file || "").toLowerCase();
    if (file.endsWith(".woff2")) return "woff2";
    if (file.endsWith(".woff")) return "woff";
    if (file.endsWith(".ttf")) return "truetype";
    if (file.endsWith(".otf")) return "opentype";
    return "";
  }

  function applyWebFontsConfig() {
    let style = document.getElementById("kwc-web-fonts-style");
    if (!state.config || !state.config.webFontsEnabled) {
      if (style) style.remove();
      return;
    }

    const items = Array.isArray(state.config.webFontsItems) ? state.config.webFontsItems : [];
    const rules = [];

    for (const item of items) {
      if (!item) continue;
      const family = String(item.family || "").trim();
      const file = String(item.file || "").trim();
      if (!family || !file || file.includes("..") || file.startsWith("/") || file.includes("\\")) continue;

      const format = fontFormatForFile(file);
      if (!format) continue;

      const weight = Number(item.weight) || 400;
      const styleValue = String(item.style || "normal").toLowerCase();
      const safeStyle = ["normal", "italic", "oblique"].includes(styleValue) ? styleValue : "normal";
      const url = apiBase + "/fonts/" + file.split("/").map(encodeURIComponent).join("/");

      rules.push(`@font-face{font-family:"${cssFontString(family)}";src:url("${url}") format("${format}");font-weight:${Math.max(100, Math.min(900, Math.round(weight)))};font-style:${safeStyle};font-display:swap;}`);
    }

    if (!style) {
      style = document.createElement("style");
      style.id = "kwc-web-fonts-style";
      document.head.appendChild(style);
    }
    style.textContent = rules.join("\n");
  }


  function applyMediaViewportConfig() {
    const root = document.getElementById("kwc-root");
    if (!root) return;
    const configured = Number(state.config && state.config.imagePreviewMaxHeight);
    const box = document.getElementById("kwc-messages");
    const viewportHeight = Math.max(160, Number(box && box.clientHeight ? box.clientHeight : window.innerHeight || 720));
    const viewportWidth = Math.max(180, Number(box && box.clientWidth ? box.clientWidth : window.innerWidth || 480));
    const viewportCap = Math.max(160, Math.floor(viewportHeight - 72));
    const configuredPx = Number.isFinite(configured) && configured > 0 ? Math.floor(configured) : viewportCap;
    const px = Math.max(120, Math.min(configuredPx, viewportCap));
    root.style.setProperty("--kwc-media-viewport-max-height", px + "px");
    root.style.setProperty("--kwc-media-viewport-max-width", Math.floor(viewportWidth) + "px");
  }

  function applyFontSizeConfig() {
    const root = document.getElementById("kwc-root");
    if (!root || !state.config) return;

    const baseFontSize = Number(state.config.uiFontSize) || 13;
    const configuredMessageSize = Number(state.config.uiMessageFontSize) || baseFontSize;
    const userSize = state.config.uiUserPreferencesControl === false ? null : (state.liveUserFontSize || savedUserFontSize());

    // Keep the UI chrome, menus, settings, sidebars, search fields, and buttons on
    // their theme/config defaults.  The user's Chat Settings font-size slider is
    // scoped to real chat message history only via --kwc-chat-message-font-size.
    root.style.setProperty("--kwc-font-size", fontPx(state.config.uiFontSize, 13));
    root.style.setProperty("--kwc-message-font-size", fontPx(state.config.uiMessageFontSize, baseFontSize));
    root.style.setProperty("--kwc-input-font-size", fontPx(state.config.uiInputFontSize, baseFontSize));
    root.style.setProperty("--kwc-button-font-size", fontPx(state.config.uiButtonFontSize, 12));
    root.style.setProperty("--kwc-badge-font-size", fontPx(state.config.uiBadgeFontSize, 10));
    const effectiveMessageSize = Number(userSize == null ? configuredMessageSize : userSize) || configuredMessageSize;
    // Typing pills follow the user's chat-message size without becoming tiny.
    // The configured UI base size is the floor; larger user sizes use about 80%.
    const typingFontSize = Math.max(baseFontSize, effectiveMessageSize * 0.80);
    root.style.setProperty("--kwc-chat-message-font-size", fontPx(effectiveMessageSize, configuredMessageSize));
    root.style.setProperty("--kwc-typing-font-size", fontPx(typingFontSize, baseFontSize));
    syncDetachedModalThemeVariables();
  }

  function clampOpacity(value) {
    value = Number(value);
    if (!Number.isFinite(value)) value = 0.92;
    return String(Math.max(0.10, Math.min(1, value)));
  }

  function normalizedTheme(value) {
    value = String(value || "system").toLowerCase().trim();
    if (value === "high_contrast" || value === "highcontrast" || value === "contrast") return "high-contrast";
    if (value === "dark" || value === "light" || value === "system" || value === "high-contrast") return value;
    return "system";
  }

  function savedUserTheme() {
    const raw = String(localStorage.getItem("kwc.userTheme") || "").trim().toLowerCase();
    if (!raw) return "";
    return normalizedTheme(raw);
  }

  function resetVisualUserPreferencesForTheme() {
    state.liveUserOpacity = null;
    localStorage.removeItem("kwc.userOpacity");
    localStorage.removeItem("kwc.userFontSize");
    localStorage.removeItem("kwc.userFontFamily");
    localStorage.removeItem("kwc.userTextColor");
    localStorage.removeItem("kwc.userUiTextColor");
    localStorage.removeItem("kwc.userTextShadowMode");
    localStorage.removeItem("kwc.userTextShadowCustom");
    localStorage.removeItem("kwc.userBackgroundColor");
    localStorage.removeItem("kwc.userInputBackgroundColor");
  }

  function setUserTheme(value) {
    const theme = normalizedTheme(value || "");
    if (theme) localStorage.setItem("kwc.userTheme", theme);
    else localStorage.removeItem("kwc.userTheme");
    resetVisualUserPreferencesForTheme();
    applyFontSizeConfig();
    applyThemeConfig();
    refreshRenderedMessagesForLocale();
    scheduleVirtualRender({preserveScroll: true, stickToBottom: false, deferDuringScroll: false});
    if (state.prefsModalOpen) {
      openUserPreferencesModal(true);
    }
  }

  function themeFromText(value) {
    value = String(value || "").toLowerCase();
    if (!value) return "";
    if (value.includes("high") || value.includes("contrast")) return "high-contrast";
    if (value.includes("light")) return "light";
    if (value.includes("dark")) return "dark";
    return "";
  }

  function detectBlueMapTheme() {
    try {
      const pdoc = window.parent && window.parent.document;
      if (pdoc) {
        const html = pdoc.documentElement;
        const body = pdoc.body;
        const text = [
          html && html.className,
          body && body.className,
          html && html.getAttribute("data-theme"),
          body && body.getAttribute("data-theme"),
          html && html.getAttribute("data-bs-theme"),
          body && body.getAttribute("data-bs-theme"),
          html && html.style && html.style.colorScheme,
          body && body.style && body.style.colorScheme
        ].join(" ");
        const fromDom = themeFromText(text);
        if (fromDom) return fromDom;
      }
    } catch (_) {}

    try {
      const storages = [window.parent && window.parent.localStorage, window.localStorage].filter(Boolean);
      for (const storage of storages) {
        for (let i = 0; i < storage.length; i++) {
          const key = storage.key(i) || "";
          if (!/theme|color|appearance|dark|light|bluemap/i.test(key)) continue;
          const value = storage.getItem(key);
          const found = themeFromText(key + " " + value);
          if (found) return found;
        }
      }
    } catch (_) {}

    return "";
  }

  function effectiveTheme() {
    const c = state.config || {};
    const userTheme = (!c || c.uiUserPreferencesControl !== false) ? savedUserTheme() : "";
    if (userTheme) return userTheme;
    if (c.uiSyncBlueMapTheme) {
      const bm = detectBlueMapTheme();
      if (bm) return bm;
    }
    return normalizedTheme(c.uiTheme || "system");
  }

  function savedUserOpacity() {
    const v = Number(localStorage.getItem("kwc.userOpacity"));
    return Number.isFinite(v) && v >= 0.10 && v <= 1 ? v : null;
  }

  function effectiveOpacity() {
    const c = state.config || {};
    const user = c.uiUserPreferencesControl === false ? null : (state.liveUserOpacity || savedUserOpacity());
    return user == null ? clampOpacity(c.uiOpacity) : clampOpacity(user);
  }

  function setUserOpacity(value, persist = true) {
    value = Number(value);
    if (!Number.isFinite(value)) return;
    value = Math.max(0.10, Math.min(1, value));
    state.liveUserOpacity = value;
    if (persist) {
      localStorage.setItem("kwc.userOpacity", String(value.toFixed(2)));
    }
    applyThemeConfig();
  }

  function resetUserOpacity() {
    state.liveUserOpacity = null;
    localStorage.removeItem("kwc.userOpacity");
    applyThemeConfig();
  }

  function normalizeHexColor(value) {
    value = String(value || "").trim();
    if (/^#[0-9a-fA-F]{6}$/.test(value)) return value.toLowerCase();
    if (/^#[0-9a-fA-F]{3}$/.test(value)) {
      return ("#" + value.slice(1).split("").map(ch => ch + ch).join("")).toLowerCase();
    }
    return "";
  }

  function hexToRgbList(hex) {
    hex = normalizeHexColor(hex);
    if (!hex) return "";
    const n = parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255].join(", ");
  }

  function savedUserTextColor() {
    return normalizeHexColor(localStorage.getItem("kwc.userTextColor") || "");
  }

  function savedUserUiTextColor() {
    return normalizeHexColor(localStorage.getItem("kwc.userUiTextColor") || "");
  }

  function normalizeTextShadowMode(value) {
    value = String(value || "").trim().toLowerCase();
    if (["none", "auto", "dark", "light", "custom"].includes(value)) return value;
    return "auto";
  }

  function sanitizeTextShadow(value) {
    value = String(value || "").trim();
    if (!value) return "";
    if (value.length > 120) value = value.slice(0, 120);
    if (/url\s*\(/i.test(value)) return "";
    if (!/^[#a-zA-Z0-9(),.%\s+\-]*$/.test(value)) return "";
    return value;
  }

  function clampShadowNumber(value, min, max, fallback, digits = 0) {
    value = Number(value);
    if (!Number.isFinite(value)) value = fallback;
    value = Math.max(min, Math.min(max, value));
    return Number(formatDecimalNumber(value, digits));
  }

  function hexFromRgb(r, g, b) {
    const toHex = n => Math.max(0, Math.min(255, Math.round(Number(n) || 0))).toString(16).padStart(2, "0");
    return ("#" + toHex(r) + toHex(g) + toHex(b)).toLowerCase();
  }

  function rgbFromHex(hex) {
    hex = normalizeHexColor(hex);
    if (!hex) return {r: 0, g: 0, b: 0};
    const n = parseInt(hex.slice(1), 16);
    return {r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255};
  }

  function parseTextShadowParts(value) {
    value = sanitizeTextShadow(value) || "0 1px 2px rgba(0, 0, 0, 0.85)";
    const parts = {x: 0, y: 1, blur: 2, color: "#000000", opacity: 85};
    const rgba = value.match(/rgba?\s*\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)(?:\s*,\s*([0-9.]+)\s*)?\)/i);
    if (rgba) {
      parts.color = hexFromRgb(rgba[1], rgba[2], rgba[3]);
      parts.opacity = clampShadowNumber((rgba[4] == null ? 1 : Number(rgba[4])) * 100, 0, 100, 85);
    } else {
      const hex = value.match(/#[0-9a-fA-F]{3,6}/);
      if (hex) parts.color = normalizeHexColor(hex[0]) || parts.color;
    }
    const numeric = value.replace(/rgba?\s*\([^)]*\)/ig, " ").replace(/#[0-9a-fA-F]{3,6}/g, " ").match(/-?\d+(?:\.\d+)?/g) || [];
    if (numeric.length >= 1) parts.x = clampShadowNumber(numeric[0], -12, 12, parts.x, 2);
    if (numeric.length >= 2) parts.y = clampShadowNumber(numeric[1], -12, 12, parts.y, 2);
    if (numeric.length >= 3) parts.blur = clampShadowNumber(numeric[2], 0, 24, parts.blur, 2);
    return parts;
  }

  function buildTextShadowFromParts(parts) {
    parts = parts || {};
    const x = clampShadowNumber(parts.x, -12, 12, 0, 2);
    const y = clampShadowNumber(parts.y, -12, 12, 1, 2);
    const blur = clampShadowNumber(parts.blur, 0, 24, 2, 2);
    const opacity = clampShadowNumber(parts.opacity, 0, 100, 85) / 100;
    const rgb = rgbFromHex(parts.color || "#000000");
    return `${pxLabel(x)} ${pxLabel(y)} ${pxLabel(blur)} rgba(${rgb.r}, ${rgb.g}, ${rgb.b}, ${opacity.toFixed(2).replace(/0+$/, "").replace(/\.$/, "")})`;
  }

  function savedUserTextShadowMode() {
    const raw = String(localStorage.getItem("kwc.userTextShadowMode") || "").trim().toLowerCase();
    return ["none", "auto", "dark", "light", "custom"].includes(raw) ? raw : "";
  }

  function savedUserTextShadowCustom() {
    return sanitizeTextShadow(localStorage.getItem("kwc.userTextShadowCustom") || "");
  }

  function savedUserBackgroundColor() {
    return normalizeHexColor(localStorage.getItem("kwc.userBackgroundColor") || "");
  }

  function savedUserInputBackgroundColor() {
    return normalizeHexColor(localStorage.getItem("kwc.userInputBackgroundColor") || "");
  }

  function setUserTextColor(value) {
    value = normalizeHexColor(value);
    if (value) localStorage.setItem("kwc.userTextColor", value);
    else localStorage.removeItem("kwc.userTextColor");
    applyThemeConfig();
  }

  function setUserUiTextColor(value) {
    value = normalizeHexColor(value);
    if (value) localStorage.setItem("kwc.userUiTextColor", value);
    else localStorage.removeItem("kwc.userUiTextColor");
    applyThemeConfig();
  }

  function setUserTextShadowMode(value) {
    value = normalizeTextShadowMode(value);
    localStorage.setItem("kwc.userTextShadowMode", value);
    applyThemeConfig();
  }

  function setUserTextShadowCustom(value) {
    value = sanitizeTextShadow(value);
    if (value) localStorage.setItem("kwc.userTextShadowCustom", value);
    else localStorage.removeItem("kwc.userTextShadowCustom");
    applyThemeConfig();
  }

  function setUserBackgroundColor(value) {
    value = normalizeHexColor(value);
    if (value) localStorage.setItem("kwc.userBackgroundColor", value);
    else localStorage.removeItem("kwc.userBackgroundColor");
    applyThemeConfig();
  }

  function setUserInputBackgroundColor(value) {
    value = normalizeHexColor(value);
    if (value) localStorage.setItem("kwc.userInputBackgroundColor", value);
    else localStorage.removeItem("kwc.userInputBackgroundColor");
    applyThemeConfig();
  }

  function resetUserTextColor() {
    localStorage.removeItem("kwc.userTextColor");
    applyThemeConfig();
  }

  function resetUserUiTextColor() {
    localStorage.removeItem("kwc.userUiTextColor");
    applyThemeConfig();
  }

  function resetUserTextShadowMode() {
    localStorage.removeItem("kwc.userTextShadowMode");
    applyThemeConfig();
  }

  function resetUserTextShadowCustom() {
    localStorage.removeItem("kwc.userTextShadowCustom");
    applyThemeConfig();
  }

  function resetUserBackgroundColor() {
    localStorage.removeItem("kwc.userBackgroundColor");
    applyThemeConfig();
  }

  function resetUserInputBackgroundColor() {
    localStorage.removeItem("kwc.userInputBackgroundColor");
    applyThemeConfig();
  }

  function fallbackTextColorForTheme() {
    const theme = effectiveTheme();
    if (theme === "light") return "#151922";
    return "#ffffff";
  }

  function fallbackBackgroundColorForTheme() {
    const theme = effectiveTheme();
    if (theme === "light") return "#f8fafc";
    return "#121216";
  }

  function fallbackInputBackgroundColorForTheme() {
    const theme = effectiveTheme();
    if (theme === "light") return "#ffffff";
    return "#000000";
  }

  function fallbackUiTextColorForTheme() {
    return normalizeHexColor(state.config && state.config.uiUiTextColor) || fallbackTextColorForTheme();
  }

  function effectiveUserTextColor() {
    return savedUserTextColor() || normalizeHexColor(state.config && state.config.uiTextColor) || fallbackTextColorForTheme();
  }

  function effectiveUserUiTextColor() {
    return savedUserUiTextColor() || fallbackUiTextColorForTheme();
  }

  function effectiveUserBackgroundColor() {
    return savedUserBackgroundColor() || fallbackBackgroundColorForTheme();
  }

  function effectiveUserInputBackgroundColor() {
    return savedUserInputBackgroundColor() || normalizeHexColor(state.config && state.config.uiInputBackgroundColor) || fallbackInputBackgroundColorForTheme();
  }

  function colorBrightness(hex) {
    hex = normalizeHexColor(hex);
    if (!hex) return 255;
    const n = parseInt(hex.slice(1), 16);
    const r = (n >> 16) & 255;
    const g = (n >> 8) & 255;
    const b = n & 255;
    return (r * 299 + g * 587 + b * 114) / 1000;
  }

  function textShadowCssForMode(mode, custom, sampleColor) {
    mode = normalizeTextShadowMode(mode);
    custom = sanitizeTextShadow(custom);
    if (mode === "none") return "none";
    if (mode === "custom") return custom || "0 1px 2px rgba(0, 0, 0, 0.85)";
    if (mode === "light") return "0 1px 2px rgba(255, 255, 255, 0.85)";
    if (mode === "dark") return "0 1px 2px rgba(0, 0, 0, 0.85)";
    const sample = normalizeHexColor(sampleColor) || effectiveUserBackgroundColor() || fallbackBackgroundColorForTheme();
    return colorBrightness(sample) >= 150 ? "0 1px 2px rgba(0, 0, 0, 0.85)" : "0 1px 2px rgba(255, 255, 255, 0.85)";
  }

  function effectiveUserTextShadowMode() {
    const prefsEnabled = !state.config || state.config.uiUserPreferencesControl !== false;
    return normalizeTextShadowMode((prefsEnabled ? savedUserTextShadowMode() : "") || (state.config && state.config.uiTextShadowMode) || "auto");
  }

  function effectiveUserTextShadowCustom() {
    const prefsEnabled = !state.config || state.config.uiUserPreferencesControl !== false;
    return sanitizeTextShadow((prefsEnabled ? savedUserTextShadowCustom() : "") || (state.config && state.config.uiTextShadowCustom) || "0 1px 2px rgba(0, 0, 0, 0.85)");
  }

  function effectiveUserTextShadowCss() {
    return textShadowCssForMode(effectiveUserTextShadowMode(), effectiveUserTextShadowCustom(), effectiveUserBackgroundColor() || fallbackBackgroundColorForTheme());
  }

  function applyUserColorOverrides(root) {
    if (!root || !state.config) return;
    const prefsEnabled = state.config.uiUserPreferencesControl !== false;
    const text = (prefsEnabled ? savedUserTextColor() : "") || normalizeHexColor(state.config && state.config.uiTextColor);
    const uiText = (prefsEnabled ? savedUserUiTextColor() : "") || normalizeHexColor(state.config && state.config.uiUiTextColor);
    // uiTextColor is scoped to the real message metadata line only: source/type,
    // separators, and timestamps. Sender/name spans are intentionally excluded so
    // Minecraft/user color-code rendering stays intact.
    const shadowMode = (prefsEnabled ? savedUserTextShadowMode() : "") || (state.config && state.config.uiTextShadowMode) || "auto";
    const shadowCustom = (prefsEnabled ? savedUserTextShadowCustom() : "") || (state.config && state.config.uiTextShadowCustom) || "0 1px 2px rgba(0, 0, 0, 0.85)";
    const bg = (prefsEnabled ? savedUserBackgroundColor() : "") || normalizeHexColor(state.config && state.config.uiBackgroundColor);
    const inputBg = (prefsEnabled ? savedUserInputBackgroundColor() : "") || normalizeHexColor(state.config && state.config.uiInputBackgroundColor);
    const shadowSampleBg = bg || fallbackBackgroundColorForTheme();
    const shadowCss = textShadowCssForMode(shadowMode, shadowCustom, shadowSampleBg);

    // Remove any stale global overrides from older builds. Theme variables must keep
    // their normal dark/light/high-contrast defaults outside real chat histories.
    [
      "--kwc-text-color",
      "--kwc-ui-color",
      "--kwc-ui-text-color",
      "--kwc-button-text",
      "--kwc-muted-color",
      "--kwc-panel-bg-rgb",
      "--kwc-modal-bg-rgb",
      "--kwc-input-bg",
      "--kwc-text-shadow",
      "--kwc-ui-text-shadow"
    ].forEach(name => root.style.removeProperty(name));

    if (text) root.style.setProperty("--kwc-chat-text-color", text);
    else root.style.removeProperty("--kwc-chat-text-color");

    if (uiText) root.style.setProperty("--kwc-chat-ui-text-color", uiText);
    else root.style.removeProperty("--kwc-chat-ui-text-color");

    if (bg) root.style.setProperty("--kwc-chat-background-color", bg);
    else root.style.removeProperty("--kwc-chat-background-color");

    root.style.setProperty("--kwc-chat-text-shadow", shadowCss || "none");

    // Input background is scoped to real compose boxes only: normal chat, DM, group.
    if (inputBg) root.style.setProperty("--kwc-compose-input-bg", inputBg);
    else root.style.removeProperty("--kwc-compose-input-bg");
  }

  function applyThemeConfig() {
    const root = document.getElementById("kwc-root");
    if (!root || !state.config) return;

    const theme = effectiveTheme();
    root.classList.remove("kwc-theme-system", "kwc-theme-dark", "kwc-theme-light", "kwc-theme-high-contrast");
    root.classList.add("kwc-theme-" + theme);
    root.style.setProperty("--kwc-panel-opacity", effectiveOpacity());

    const userFamily = state.config.uiUserPreferencesControl === false ? "" : savedUserFontFamily();
    const uiFamily = String(state.config.uiFontFamily || "").trim();
    const chatFamily = String(userFamily || uiFamily || "").trim();
    // Keep the UI chrome on the configured/theme font. The user's Chat Settings
    // font family is scoped to real chat history through --kwc-chat-font-family.
    root.style.fontFamily = uiFamily || "";
    if (chatFamily) root.style.setProperty("--kwc-chat-font-family", chatFamily);
    else root.style.removeProperty("--kwc-chat-font-family");
    applyUserColorOverrides(root);
    syncDetachedModalThemeVariables();

    if (state.themeSyncTimer) {
      clearInterval(state.themeSyncTimer);
      state.themeSyncTimer = null;
    }
    if (state.config.uiSyncBlueMapTheme) {
      state.themeSyncTimer = setInterval(() => {
        const current = effectiveTheme();
        if (!root.classList.contains("kwc-theme-" + current)) {
          applyThemeConfig();
        }
      }, 2000);
    }
  }

  // 서버의 런타임 config를 읽어 기능 enable/disable, UI 기본값, API capability를 state에 반영한다. 실패 시 이전 state를 무작정 지우지 않아 일시 장애 후 복구할 여지를 남긴다.

  // Loads runtime server config into feature flags, UI defaults, and API capabilities. A transient failure does not blindly discard all prior state, preserving a path to recovery.

  async function loadConfig() {
    try {
      const data = await api("/config");
      state.config = data;
      state.userProfilesEnabled = data.uiUserProfilesEnabled === true;
      state.userProfilesMaxProfiles = Math.max(0, Math.min(20, Math.floor(Number(data.uiUserProfilesMaxProfiles) || 0)));
      state.userProfilesAllowImportExport = data.uiUserProfilesAllowImportExport !== false;
      const pageSize = Number(data.historyPageSize);
      state.historyPageSize = Number.isFinite(pageSize) && pageSize >= 0 ? Math.floor(pageSize) : 20;
      state.conversationArchiveEnabled = data.conversationArchiveEnabled === true;
      state.typingUserDisplayControl = data.typingUserDisplayControl === true;
      state.typingOpenChatEnabled = data.typingOpenChatEnabled === true;
      state.typingDmEnabled = data.typingDmEnabled !== false;
      state.typingGroupChatEnabled = data.typingGroupChatEnabled !== false;
      if (!state.typingOpenChatEnabled) state.publicTypingEntries.clear();
      if (!state.typingDmEnabled) state.dmTypingEntry = null;
      if (!state.typingGroupChatEnabled) state.groupTypingEntries.clear();
      scheduleTypingIndicatorRefresh();
      state.serverVersion = data.serverVersion || "";
      applyWindowSizeConfig();
      applyWebFontsConfig();
      applyFontSizeConfig();
    applyMediaViewportConfig();
      applyThemeConfig();
      updatePipButton();
      updateGuestVisibility();
      const msg = document.getElementById("kwc-message");
      if (msg) {
        const inputLimit = normalizeCommandMaxLength(data.maxMessageInputLength, 0);
        if (inputLimit > 0) msg.maxLength = inputLimit;
        else msg.removeAttribute("maxlength");
      }
      state.commandMaxLength = normalizeCommandMaxLength(data.commandsMaxLength, 0);
      state.directMessageEnabled = data.directMessageEnabled === true;
      state.directMessageAllowWebSend = data.directMessageAllowWebSend !== false;
      state.directMessageMaxMessageLength = Math.max(0, Math.floor(Number(data.directMessageMaxMessageLength) || 0));
      state.directMessageRetentionDays = Math.max(0, Math.floor(Number(data.directMessageRetentionDays) || 0));
      state.directMessageWebUnreadBadge = data.directMessageWebUnreadBadge !== false;
      state.directMessageConfirmDelete = data.directMessageConfirmDelete !== false;
      state.selfMessageDeleteEnabled = data.selfMessageDeleteEnabled === true;
      state.selfMessageDeleteWindowMinutes = Math.max(0, Math.floor(Number(data.selfMessageDeleteWindowMinutes) || 0));
      state.adminCapabilities = data.moderatorCapabilities && typeof data.moderatorCapabilities === "object" ? data.moderatorCapabilities : {};
      state.groupChatEnabled = data.groupChatEnabled === true;
      state.groupChatAllowWebSend = data.groupChatAllowWebSend !== false;
      state.groupChatAllowPublicRooms = data.groupChatAllowPublicRooms !== false;
      state.groupChatAllowRoomPasswords = data.groupChatAllowRoomPasswords !== false;
      state.groupChatMaxMessageLength = Math.max(0, Math.floor(Number(data.groupChatMaxMessageLength) || 0));
      state.groupChatRetentionDays = Math.max(0, Math.floor(Number(data.groupChatRetentionDays) || 0));
      state.groupChatConfirmLeave = data.groupChatConfirmLeave !== false;
      state.groupChatConfirmDelete = data.groupChatConfirmDelete !== false;
      state.browserNotificationsEnabled = data.browserNotificationsEnabled !== false;
      state.browserNotificationsOnlyWhenHidden = data.browserNotificationsOnlyWhenHidden !== false;
      state.browserNotificationsNotifyNormalChat = data.browserNotificationsNotifyNormalChat !== false;
      state.browserNotificationsNotifyDm = data.browserNotificationsNotifyDm !== false;
      state.browserNotificationsNotifyGroupChat = data.browserNotificationsNotifyGroupChat !== false;
      state.browserNotificationsNotifyMentions = data.browserNotificationsNotifyMentions !== false;
      state.browserNotificationsNotifyReplies = data.browserNotificationsNotifyReplies !== false;
      state.browserNotificationsNotifyReactions = data.browserNotificationsNotifyReactions !== false;
      state.browserNotificationsNotifySystem = data.browserNotificationsNotifySystem !== false;
      state.browserNotificationsNotifyKeywords = data.browserNotificationsNotifyKeywords !== false;
      state.webPushEnabled = data.webPushEnabled === true;
      state.webPushAvailable = data.webPushAvailable === true;
      state.webPushVapidPublicKey = data.webPushVapidPublicKey || "";
      state.standaloneWebEnabled = data.standaloneWebEnabled === true;
      state.standaloneWebPath = String(data.standaloneWebPath || "");
      state.standaloneWebPublicUrl = String(data.standaloneWebPublicUrl || "");
      state.standaloneWebAppName = String(data.standaloneWebAppName || "");
      state.standaloneWebAppShortName = String(data.standaloneWebAppShortName || "");
      state.webPushNotificationTitle = String(data.webPushNotificationTitle || "");
      state.webPushNotifyNormalChat = data.webPushNotifyNormalChat !== false;
      state.webPushNotifyDm = data.webPushNotifyDm !== false;
      state.webPushNotifyGroupChat = data.webPushNotifyGroupChat !== false;
      state.webPushNotifyMentions = data.webPushNotifyMentions !== false;
      state.webPushNotifyReplies = data.webPushNotifyReplies !== false;
      state.webPushNotifyReactions = data.webPushNotifyReactions !== false;
      state.webPushNotifySystem = data.webPushNotifySystem !== false;
      state.webPushNotifyKeywords = data.webPushNotifyKeywords !== false;
      state.emojiEnabled = data.emojiEnabled !== false;
      state.emojiShowButton = data.emojiShowButton !== false;
      state.emojiFavoritesEnabled = data.emojiFavoritesEnabled !== false;
      state.emojiFavoritesStorage = String(data.emojiFavoritesStorage || "account").toLowerCase() === "browser" ? "browser" : "account";
      const emojiFavoritesMaxRaw = Number(data.emojiFavoritesMaxPerAccount);
      state.emojiFavoritesMaxPerAccount = Number.isFinite(emojiFavoritesMaxRaw) ? Math.max(0, Math.floor(emojiFavoritesMaxRaw)) : 100;
      state.emojiFavoritesLoaded = state.emojiFavoritesStorage !== "account";
      if (!state.emojiFavoritesEnabled || state.emojiFavoritesStorage === "account") state.emojiFavorites = [];
      else state.emojiFavorites = limitEmojiFavoriteIds(state.emojiFavorites);
      state.emojiRenderSizePx = Math.max(16, Math.min(1024, Number(data.emojiRenderSizePx) || 32));
      state.emojiPickerSizePx = Math.max(24, Math.min(1024, Number(data.emojiPickerSizePx) || 44));
      applyEmojiPickerSize();
      updateDirectMessageComposeControls();
      state.emojiMessageTokenLimit = Math.max(0, Math.floor(Number(data.emojiMessageTokenLimit) || 0));
      state.emojiTokenFormat = normalizeEmojiTokenFormat(data.emojiTokenFormat);
      const fileInput = document.getElementById("kwc-file");
      if (fileInput) fileInput.accept = uploadAcceptList();
      updateLoginState();
      ensureGuestNameForConfig();
      const guestNameInput = document.getElementById("kwc-guest-name");
      if (guestNameInput) guestNameInput.value = limitGuestNameCodePoints(state.guestName);
      await refreshCaptcha();
      await loadCommands();
    } catch (e) {
      console.warn("KOKOTO WebChat config failed", e);
    }
  }


  function hideCaptchaUi() {
    const row = document.getElementById("kwc-captcha-row");
    const a = document.getElementById("kwc-captcha-a");
    const q = document.getElementById("kwc-captcha-q");
    if (row) row.classList.remove("kwc-show");
    if (a) a.value = "";
    if (q) q.textContent = "";
    state.captcha = null;
    if (typeof renderTypingIndicators === "function") renderTypingIndicators();
  }

  async function refreshCaptcha(force = false) {
    const row = document.getElementById("kwc-captcha-row");
    if (!row || !state.config) return;
    if (state.token || !state.config.captchaEnabled) {
      hideCaptchaUi();
      return;
    }

    // If server is configured for one captcha pass per guest session, hide captcha after success.
    if (!force && !state.config.captchaRequireOnEachMessage && state.captchaPass) {
      hideCaptchaUi();
      return;
    }

    try {
      const data = await api("/captcha");
      if (data.enabled && data.passed === true && !state.config.captchaRequireOnEachMessage) {
        hideCaptchaUi();
        return;
      }
      if (data.enabled) {
        state.captcha = data;
        row.classList.add("kwc-show");
        document.getElementById("kwc-captcha-q").textContent = data.type === "text" ? fmt("captcha.enterCode", "Enter code: {code}", {code:data.question || ""}) : data.question;
        document.getElementById("kwc-captcha-a").value = "";
        if (typeof renderTypingIndicators === "function") renderTypingIndicators();
      } else {
        hideCaptchaUi();
      }
    } catch (e) {
      console.warn("captcha failed", e);
    }
  }

  function historyQuery(mode) {
    const configured = Number(state.historyPageSize);
    const limit = Number.isFinite(configured) && configured >= 0 ? Math.floor(configured) : 20;
    let url = "/history?limit=" + encodeURIComponent(limit);
    if ((mode === true || mode === "older") && state.historyOldestId) url += "&before=" + encodeURIComponent(state.historyOldestId);
    if (mode === "newer" && state.historyNewestId) url += "&after=" + encodeURIComponent(state.historyNewestId);
    return url;
  }


  function messageRefreshKey(msg) {
    if (!msg) return "";
    return [
      msg.id || "",
      msg.time || msg.timestamp || msg.createdAt || "",
      msg.sender || "",
      msg.realSender || "",
      msg.playerUuid || "",
      msg.originServerId || "",
      msg.originServerName || "",
      msg.role || "",
      msg.source || "",
      msg.message || msg.text || "",
      msg.replyToId || "",
      msg.replyToSender || "",
      msg.replyToPreview || "",
      msg.hidden ? "1" : "0"
    ].map(v => String(v)).join("\u001f");
  }

  function latestHistoryPageUnchanged(freshMessages) {
    if (!Array.isArray(freshMessages)) return false;
    if (!state.messages.length && freshMessages.length === 0) return true;
    if (freshMessages.length === 0) return state.messages.length === 0;
    if (state.messages.length < freshMessages.length) return false;
    const currentTail = state.messages.slice(state.messages.length - freshMessages.length);
    for (let i = 0; i < freshMessages.length; i++) {
      if (messageRefreshKey(currentTail[i]) !== messageRefreshKey(freshMessages[i])) return false;
    }
    return true;
  }

  function installHistoryPaging() {
    const box = document.getElementById("kwc-messages");
    if (!box || box.dataset.pagingInstalled === "1") return;
    box.dataset.pagingInstalled = "1";

    const installScrollInteractionEvents = () => {
      const isInteractiveScrollTarget = target => {
        try {
          return !!(target && target.closest && target.closest(
            "[data-delete], [data-pin], [data-unpin], [data-open-pins], " +
            "a.kwc-link, a.kwc-image-link, button, input, textarea, select, " +
            ".kwc-media-card, .kwc-youtube-card, .kwc-social-card, .kwc-social-embed"
          ));
        } catch (_) {
          return false;
        }
      };

      const cancelReplyJumpOnUserScrollInput = () => {
        cancelReplyJumpForUserScroll("user-scroll-input-capture");
      };
      box.addEventListener("wheel", cancelReplyJumpOnUserScrollInput, {capture: true, passive: true});
      box.addEventListener("touchmove", cancelReplyJumpOnUserScrollInput, {capture: true, passive: true});
      box.addEventListener("keydown", event => {
        if (["ArrowUp", "ArrowDown", "PageUp", "PageDown", "Home", "End", " "].includes(event.key)) cancelReplyJumpOnUserScrollInput();
      }, {capture: true, passive: true});

      box.addEventListener("wheel", (ev) => {
        markDirectScrollInput();
        rememberUserTopIntent(box);
        if (ev.deltaY < 0) {
          markHistoryEndTopUserIntent(box, "wheel-top");
          requestOlderHistoryFromTopInput("wheel");
        } else if (ev.deltaY > 0) {
          markHistoryEndBottomUserIntent(box, "wheel-bottom");
          requestNewerHistoryFromBottomInput("wheel");
        }
        setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, "wheel"), 0);
      }, {passive: true});
      box.addEventListener("keydown", (ev) => {
        if (["ArrowUp", "ArrowDown", "PageUp", "PageDown", "Home", "End", " "].includes(ev.key)) {
          markDirectScrollInput();
          rememberUserTopIntent(box);
          if (["ArrowUp", "PageUp", "Home"].includes(ev.key)) markHistoryEndTopUserIntent(box, "key-top");
          if (["ArrowDown", "PageDown", "End", " "].includes(ev.key)) {
            markHistoryEndBottomUserIntent(box, "key-bottom");
            setTimeout(() => requestNewerHistoryFromBottomInput("key"), 0);
          }
          setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, "key"), 0);
        }
      }, {passive: true});
      let historyTouchStartY = null;
      box.addEventListener("touchstart", (ev) => {
        historyTouchStartY = ev.touches && ev.touches[0] ? ev.touches[0].clientY : null;
        if (!isInteractiveScrollTarget(ev.target)) beginTouchScrollInteraction();
      }, {passive: true});
      box.addEventListener("touchmove", (ev) => {
        markDirectScrollInput();
        rememberUserTopIntent(box);
        setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, "touch"), 0);
        const y = ev.touches && ev.touches[0] ? ev.touches[0].clientY : null;
        if (historyTouchStartY != null && y != null && y - historyTouchStartY > 18) {
          markHistoryEndTopUserIntent(box, "touch-top");
          requestOlderHistoryFromTopInput("touch");
        } else if (historyTouchStartY != null && y != null && historyTouchStartY - y > 18) {
          markHistoryEndBottomUserIntent(box, "touch-bottom");
          requestNewerHistoryFromBottomInput("touch");
        }
      }, {passive: true});
      box.addEventListener("touchend", () => { historyTouchStartY = null; endTouchScrollInteraction(); }, {passive: true});
      box.addEventListener("touchcancel", () => { historyTouchStartY = null; endTouchScrollInteraction(); }, {passive: true});
      box.addEventListener("pointerdown", (ev) => {
        if (ev.pointerType === "touch" && !isInteractiveScrollTarget(ev.target)) beginTouchScrollInteraction();
        const rect = box.getBoundingClientRect();
        const nearVerticalScrollbar = ev.clientX >= rect.right - 18;
        const nearHorizontalScrollbar = ev.clientY >= rect.bottom - 18;
        if (nearVerticalScrollbar || nearHorizontalScrollbar) {
          state.scrollbarDragActive = true;
          state.scrollbarDragLastX = ev.clientX;
          state.scrollbarDragLastY = ev.clientY;
          markDirectScrollInput();
          markHistoryEndTopUserIntent(box, "scrollbar-down");
          if (isAtHistoryBottomRequestZone(box)) {
            markHistoryEndBottomUserIntent(box, "scrollbar-bottom");
            requestNewerHistoryFromBottomInput("scrollbar");
          }
        }
      }, {passive: true});
      window.addEventListener("pointermove", (ev) => {
        if (!state.scrollbarDragActive) return;
        markDirectScrollInput();
        markScrollInteraction();
        const lastX = Number.isFinite(Number(state.scrollbarDragLastX)) ? Number(state.scrollbarDragLastX) : ev.clientX;
        const lastY = Number.isFinite(Number(state.scrollbarDragLastY)) ? Number(state.scrollbarDragLastY) : ev.clientY;
        const dx = ev.clientX - lastX;
        const dy = ev.clientY - lastY;
        state.scrollbarDragLastX = ev.clientX;
        state.scrollbarDragLastY = ev.clientY;
        if (dy > 2 || dx > 2) {
          if (isAtHistoryBottomRequestZone(box)) {
            markHistoryEndBottomUserIntent(box, "scrollbar-bottom");
            requestNewerHistoryFromBottomInput("scrollbar");
          }
        } else if (dy < -2 || dx < -2) {
          markHistoryEndTopUserIntent(box, "scrollbar-top");
          requestOlderHistoryFromTopInput("scrollbar");
        }
      }, {capture: true, passive: true});
      window.addEventListener("pointerup", (ev) => {
        if (ev.pointerType === "touch") endTouchScrollInteraction();
        if (!state.scrollbarDragActive) return;
        state.scrollbarDragActive = false;
        state.scrollbarDragLastX = null;
        state.scrollbarDragLastY = null;
        markScrollInteraction();
        markHistoryEndTopUserIntent(box, "scrollbar");
        if (isAtHistoryBottomRequestZone(box)) markHistoryEndBottomUserIntent(box, "scrollbar-bottom");
        setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, "scrollbar"), 0);
      }, {capture: true, passive: true});
      window.addEventListener("pointercancel", (ev) => {
        if (ev.pointerType === "touch") endTouchScrollInteraction();
        if (!state.scrollbarDragActive) return;
        state.scrollbarDragActive = false;
        state.scrollbarDragLastX = null;
        state.scrollbarDragLastY = null;
        markScrollInteraction();
        setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, "scrollbar-cancel"), 0);
      }, {capture: true, passive: true});
      window.addEventListener("blur", () => {
        endTouchScrollInteraction();
        if (!state.scrollbarDragActive) return;
        state.scrollbarDragActive = false;
        state.scrollbarDragLastX = null;
        state.scrollbarDragLastY = null;
        markScrollInteraction();
        setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, "scrollbar-blur"), 0);
      });
    };
    const installNonScrollUiActionEvents = () => {
      const root = document.getElementById("kwc-root");
      if (!root || root.dataset.nonScrollUiActionInstalled === "1") return;
      root.dataset.nonScrollUiActionInstalled = "1";
      const markIfUiControl = (ev) => {
        const target = ev && ev.target;
        try {
          if (target && target.closest && target.closest(
            "button, input, textarea, select, a, [data-delete], [data-pin], [data-unpin], [data-pin-move], [data-open-pins], " +
            ".kwc-modal, .kwc-modal-backdrop, .kwc-login, .kwc-admin-modal, .kwc-preferences-modal, " +
            ".kwc-media-card, .kwc-youtube-card, .kwc-social-card, .kwc-social-embed"
          )) markNonScrollUiAction();
        } catch (_) {}
      };
      root.addEventListener("pointerdown", markIfUiControl, true);
      root.addEventListener("click", markIfUiControl, true);
      root.addEventListener("keydown", markIfUiControl, true);
    };

    installScrollInteractionEvents();
    installNonScrollUiActionEvents();

    box.addEventListener("scroll", () => {
      if (state.minimized || guestChatHidden()) return;
      const now = Date.now();
      const directScrollInputActive = now - Number(state.lastDirectScrollInputAt || 0) <= Math.max(220, scrollInteractionIdleMs() + 120);
      const nonScrollLayoutChange = !directScrollInputActive && now < Number(state.nonScrollLayoutUntil || 0);
      let replyProgrammaticScroll = now < Number(state.replyJumpUntil || 0);
      if (replyProgrammaticScroll && !directScrollInputActive && replyJumpLooksUserMoved(box, Number(state.replyJumpStartedAt || 0))) {
        cancelReplyJumpForUserScroll("scroll-drift");
        replyProgrammaticScroll = false;
      }
      const programmaticScroll = !directScrollInputActive && (now < Number(state.suppressScrollRenderUntil || 0) || replyProgrammaticScroll || nonScrollLayoutChange);
      const keepBottom = isAutoFollowBottom(box) && !state.historyHasAfter;
      const preloadThreshold = historyPreloadThresholdPx(box);
      const shouldLoadOlder = box.scrollTop <= preloadThreshold;
      const shouldLoadNewer = !!state.historyHasAfter && bottomGapPx(box) <= preloadThreshold;
      refreshScrollAffordances(box);
      if (!programmaticScroll) {
        if (shouldLoadOlder) markHistoryEndTopUserIntent(box, "scroll-top");
        if (isAtHistoryBottomRequestZone(box)) {
          const bottomScrollReason = state.scrollbarDragActive ? "scrollbar-bottom" : (state.touchScrollActive ? "touch-bottom" : "scroll-bottom");
          markHistoryEndBottomUserIntent(box, bottomScrollReason);
        }
        maybeShowHistoryEndNoticeFromUserScroll(box, "scroll");
      }

      if (nonScrollLayoutChange) {
        // Preserve the existing follow intent. A panel resize is not user scroll.
      } else if (!state.suppressAutoFollowUpdate && !programmaticScroll) {
        state.lastUserScrollAt = Date.now();
        state.autoFollowLatest = keepBottom && Date.now() >= Number(state.preventBottomStickUntil || 0);
        markScrollInteraction();
      } else {
        state.autoFollowLatest = keepBottom && Date.now() >= Number(state.preventBottomStickUntil || 0);
      }

      // Programmatic bottom corrections can emit scroll events for several
      // frames. Do not turn those events into more virtual renders unless they
      // actually reached the top-history preload zone.
      if (programmaticScroll && (replyProgrammaticScroll || !shouldLoadOlder)) return;

      // During a real scroll event, physical scroll position is authoritative.
      // All message contents use the same virtual-range policy; images, videos,
      // audio and iframes do not get a separate retention/reconciliation path.
      if (isScrollInteractionActive()) {
        deferRenderUntilScrollIdle({preserveScroll: !keepBottom, stickToBottom: keepBottom});
      } else {
        scheduleVirtualRender({preserveScroll: !keepBottom, stickToBottom: keepBottom});
      }
      if (shouldLoadOlder) {
        // Reaching the top is the user's explicit request for older history,
        // but route it through the same top-load throttle used by wheel/touch.
        // Directly calling loadHistory() here lets repeated scroll events start
        // a fetch -> scrollTop correction -> heavy mutation loop.
        requestOlderHistoryFromTopInput("scroll-top");
      }
      if (shouldLoadNewer) {
        // Reply-jump can place the viewport in a middle slice. Reaching the
        // bottom of that slice should page newer messages, not pretend that this
        // is already the latest chat position.
        requestNewerHistoryFromBottomInput("scroll-bottom");
      }
    }, {passive: true});

    if (window.ResizeObserver && !state.virtualResizeObserver) {
      state.virtualResizeObserver = new ResizeObserver(() => {
        const keepBottom = isAutoFollowBottom(box) && !state.historyHasAfter;
        scheduleVirtualRender({preserveScroll: !keepBottom, stickToBottom: keepBottom, deferDuringScroll: false});
        if (keepBottom) {
          state.historyViewportFillAttempts = 0;
          scheduleHistoryViewportFill("resize");
        }
      });
      state.virtualResizeObserver.observe(box);
    }
  }

  // 공개 history pagination의 중심 함수다. forceLatest/around/scroll 상태를 함께 고려하고, 새 페이지를 병합한 뒤 virtual-scroll anchor와 bottom-follow 여부를 복원한다.

  // Central public-history pagination routine. It considers force-latest/around/scroll state together, merges the new page, then restores virtual-scroll anchoring and bottom-follow intent.

  async function loadHistory(older = false, options = {}) {
    if (guestChatHidden()) return;
    if (state.historyLoading) return;
    if (older && !state.historyHasMore) return;
    if (older && isScrollInteractionActive() && !options.forceDuringScroll) {
      requestOlderHistoryAfterScrollIdle();
      return;
    }
    // Do not skip fetching fresh history just because a YouTube/video/audio
    // player is open. The fetch may discover newer records, but a mid-history
    // viewport is preserved below instead of being replaced by the latest page.

    const box = document.getElementById("kwc-messages");
    const prevHeight = box ? box.scrollHeight : 0;
    const prevTop = box ? box.scrollTop : 0;
    const wasNearBottom = box ? (isAutoFollowBottom(box) && !state.historyHasAfter) : true;
    const explicitLatestFollowBeforeHistory = box ? hasExplicitLatestFollow() : true;

    const historyLoadSeq = ++state.historyLoadSeq;
    state.historyLoading = true;
    state.historyLoadingSince = Date.now();
    if (older) markOlderHistorySettling();
    if (!older) state.historyViewportFillAttempts = 0;
    scheduleHistorySlowNotice(box, historyLoadSeq, older);
    let historyLoadSucceeded = false;
    try {
      const data = await api(historyQuery(older ? "older" : "latest"), {timeoutMs: older ? topHistoryBusyTimeoutMs() : 15000});
      if (historyLoadSeq !== state.historyLoadSeq) return;
      historyLoadSucceeded = true;
      if (data.ok && Array.isArray(data.messages)) {
        const nextHasBefore = data.hasBefore != null ? !!data.hasBefore : !!data.hasMore;
        const nextHasAfter = data.hasAfter != null ? !!data.hasAfter : false;
        const nextOldestId = data.oldestId || state.historyOldestId;
        const nextNewestId = data.newestId || state.historyNewestId;

        // A focus/visibility/SSE-reconnect refresh must never replace the message
        // window while the user is reading older history. Cross-origin media such
        // as YouTube commonly causes window blur/focus transitions; replacing the
        // array here kept the old scrollTop but swapped in the latest page, which
        // visually looked like messages had changed order. Preserve the current
        // contiguous window and only mark that newer history exists. Scrolling to
        // the bottom will fetch it through the normal `after` paging path.
        if (!older && !options.forceLatest && state.messages.length > 0 && box && !wasNearBottom) {
          const remoteNewestId = nextNewestId || (data.messages[data.messages.length - 1] && data.messages[data.messages.length - 1].id) || "";
          if (remoteNewestId && state.historyNewestId && String(remoteNewestId) !== String(state.historyNewestId)) {
            state.historyHasAfter = true;
          }
          refreshScrollAffordances(box);
          return;
        }

        if (!older && state.messages.length > 0 && latestHistoryPageUnchanged(data.messages)) {
          state.historyHasMore = nextHasBefore;
          state.historyHasAfter = nextHasAfter;
          state.historyOldestId = nextOldestId;
          state.historyNewestId = nextNewestId || (state.messages[state.messages.length - 1] && state.messages[state.messages.length - 1].id) || "";
          refreshScrollAffordances(box);
          if (box && (options.forceLatest || bottomFollowAllowed(box, {forceLatestFollow: !!options.forceLatest}))) {
            state.autoFollowLatest = true;
            renderVirtualMessages({stickToBottom: true, preserveScroll: false, latestJump: !!options.forceLatest, forceLatestFollow: !!options.forceLatest, ignoreVisibleRangeProtection: !!options.forceLatest, deferDuringScroll: false, allowBottomStickDuringLock: !!options.forceLatest});
            stickToBottomStable(box);
            scheduleHistoryViewportFill("unchanged-history");
          }
          return;
        }
        if (older) {
          const anchor = captureScrollAnchor(box);
          const beforeCount = state.messages.length;
          [...data.messages].reverse().forEach(msg => addMessage(msg, {prepend: true, skipRender: true, suppressAutoFollow: true}));
          const addedCount = Math.max(0, state.messages.length - beforeCount);
          state.historyHasMore = nextHasBefore;
          state.historyOldestId = nextOldestId;
          // Keep the existing newer-side state. A page fetched before the current
          // oldest message naturally has server-side hasAfter=true, but that does
          // not mean the current loaded range is missing newer messages.
          if (options.viewportFill && box && bottomFollowAllowed(box)) {
            renderVirtualMessages({stickToBottom: true, preserveScroll: false, forceLatestFollow: hasExplicitLatestFollow()});
            if (box) stickToBottomStable(box);
            scheduleHistoryViewportFill("viewport-fill");
          } else if (box && addedCount > 0) {
            // Pre-adjust by estimated height so the same viewport remains in the
            // virtual range before the final anchor correction runs. This avoids
            // the scroll thumb twitching at the very top while older history is appended.
            let estimatedAddedHeight = 0;
            for (let i = 0; i < addedCount; i++) estimatedAddedHeight += messageHeightAt(i);
            setScrollTopPreserved(box, prevTop + estimatedAddedHeight, {allowAwayFromBottom: true, reason: "older-history-preadjust"});
          }
          if (options.viewportFill && box && bottomFollowAllowed(box)) {
            // Already rendered above as a latest-chat view.
          } else {
            // Always reconcile the actual message sequence after prepending. A
            // visible media node is allowed to be recreated if necessary; keeping
            // a stale player DOM is never allowed to override chat ordering.
            renderVirtualMessages({stickToBottom: false, preserveScroll: false, anchor});
            if (box && addedCount > 0) {
              preserveOlderHistoryViewportAfterRender(box, prevTop, prevHeight, anchor, "older-history");
            }
          }
        } else {
          state.messages = [];
          state.nextLocalMessageId = 1;
          data.messages.forEach(msg => addMessage(msg, {skipRender: true, suppressAutoFollow: true}));
          state.historyHasMore = nextHasBefore;
          state.historyHasAfter = nextHasAfter;
          state.historyOldestId = nextOldestId || (state.messages[0] && state.messages[0].id) || "";
          state.historyNewestId = nextNewestId || (state.messages[state.messages.length - 1] && state.messages[state.messages.length - 1].id) || "";
          const shouldFollowLatest = !!options.forceLatest || wasNearBottom || explicitLatestFollowBeforeHistory || state.messages.length === 0;
          state.autoFollowLatest = shouldFollowLatest;
          renderVirtualMessages({stickToBottom: shouldFollowLatest, preserveScroll: !shouldFollowLatest, latestJump: !!options.forceLatest, forceLatestFollow: !!options.forceLatest || explicitLatestFollowBeforeHistory, ignoreVisibleRangeProtection: !!options.forceLatest, deferDuringScroll: !!options.forceLatest ? false : undefined, allowBottomStickDuringLock: !!options.forceLatest});
          if (box && !shouldFollowLatest) setScrollTopPreserved(box, prevTop, {allowAwayFromBottom: true, reason: "latest-history-preserve"});
          if (shouldFollowLatest) scheduleHistoryViewportFill("initial-history");
        }
      }
    } catch (e) {
      console.warn("history failed", e);
      if (historyLoadSeq === state.historyLoadSeq) {
        clearHistorySlowNoticeTimer();
        if (older) {
          state.pendingOlderHistoryLoad = false;
          state.olderHistorySettleUntil = 0;
          if (state.pendingTopOlderHistoryTimer) {
            clearTimeout(state.pendingTopOlderHistoryTimer);
            state.pendingTopOlderHistoryTimer = null;
            state.pendingTopOlderHistoryDueAt = 0;
          }
          showHistoryFailureNotice(box, e);
        }
      }
    } finally {
      if (historyLoadSeq === state.historyLoadSeq) {
        clearHistorySlowNoticeTimer();
        if (String(state.historyEndNoticeKey || "history.end") === "history.loading") hideHistoryStatusNoticeIfActive();
        state.historyLoading = false;
        state.historyLoadingSince = 0;
        if (older && historyLoadSucceeded) {
          markOlderHistorySettling();
        } else if (older) {
          state.olderHistorySettleUntil = 0;
        }
        const finalBox = document.getElementById("kwc-messages");
        if (historyLoadSucceeded && older && finalBox && state.historyHasMore && (isAtHistoryTopRequestZone(finalBox) || hasHistoryTopEdgeIntent())) {
          // Some browsers do not emit another scroll/wheel event once the user is
          // already pinned to the physical top. If there is still older history
          // and the user's recent edge intent is still active, queue another
          // guarded pass instead of leaving the viewport apparently stuck.
          scheduleTopOlderHistoryRetry("older-continuation");
        }
        const hasPendingHistoryEndTopIntent = Number(state.historyEndNoticePendingUserTopUntil || 0) > Date.now();
        const hasPendingHistoryEndBottomIntent = Number(state.historyEndNoticePendingUserBottomUntil || 0) > Date.now();
        if (finalBox && (older || hasPendingHistoryEndTopIntent || hasPendingHistoryEndBottomIntent)) {
          setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(finalBox, older ? "history-loaded" : "history-refreshed"), 0);
          setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(finalBox, older ? "history-loaded-late" : "history-refreshed-late"), 120);
        }
        if (historyLoadSucceeded) scheduleViewportMaintenance(older ? "older-history" : "history", older ? 1600 : 2200);
      }
    }
  }

  async function loadNewerHistory(options = {}) {
    if (state.historyLoading) return;
    if (!state.historyHasAfter || !state.historyNewestId) return;
    if (isScrollInteractionActive() && !options.forceDuringScroll) {
      requestNewerHistoryAfterScrollIdle();
      return;
    }

    const box = document.getElementById("kwc-messages");
    const prevTop = box ? Number(box.scrollTop || 0) : 0;
    const anchor = captureScrollAnchor(box);
    const historyLoadSeq = ++state.historyLoadSeq;
    state.historyLoading = true;
    state.historyLoadingSince = Date.now();
    let historyLoadSucceeded = false;
    try {
      const data = await api(historyQuery("newer"), {timeoutMs: 15000});
      if (historyLoadSeq !== state.historyLoadSeq) return;
      historyLoadSucceeded = true;
      if (data.ok && Array.isArray(data.messages)) {
        const beforeCount = state.messages.length;
        data.messages.forEach(msg => addMessage(msg, {skipRender: true, suppressAutoFollow: true}));
        const addedCount = Math.max(0, state.messages.length - beforeCount);
        state.historyHasAfter = data.hasAfter != null ? !!data.hasAfter : false;
        state.historyNewestId = data.newestId || (state.messages[state.messages.length - 1] && state.messages[state.messages.length - 1].id) || state.historyNewestId;
        // Keep older-side state unchanged. The server's hasBefore for an after
        // page only means there are records before that returned page; those may
        // already be loaded in the current contiguous range.
        state.autoFollowLatest = false;
        if (box && addedCount > 0) {
          renderVirtualMessages({
            stickToBottom: false,
            preserveScroll: true,
            anchor,
            forcePreservePosition: true,
            suppressBottomStick: true,
            deferDuringScroll: false
          });
          if (anchor) restoreScrollAnchor(box, anchor, {thresholdPx: 0.5, reason: "newer-history-anchor"});
          else setScrollTopPreserved(box, prevTop, {allowAwayFromBottom: true, reason: "newer-history-preserve"});
        } else if (box) {
          refreshScrollAffordances(box);
        }
      }
    } catch (e) {
      console.warn("newer history failed", e);
    } finally {
      if (historyLoadSeq === state.historyLoadSeq) {
        state.historyLoading = false;
        state.historyLoadingSince = 0;
        state.pendingNewerHistoryLoad = false;
        if (historyLoadSucceeded) scheduleViewportMaintenance("newer-history", 1600);
        const finalBox = document.getElementById("kwc-messages");
        if (historyLoadSucceeded && finalBox && state.historyHasAfter && (isAtHistoryBottomRequestZone(finalBox) || hasHistoryBottomEdgeIntent())) {
          // If the fetched page was too short to create additional scroll room,
          // or the user is still trying to continue toward the newer edge, queue
          // another guarded pass. This covers the physical-bottom case where no
          // additional scroll event is fired after a blocked/cooldowned request.
          requestNewerHistoryAfterScrollIdle();
          scheduleBottomNewerHistoryRetry("newer-continuation");
        } else if (historyLoadSucceeded && finalBox && !state.historyHasAfter && Number(state.historyEndNoticePendingUserBottomUntil || 0) > Date.now()) {
          setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(finalBox, "newer-history-end"), 0);
          setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(finalBox, "newer-history-end-late"), 120);
        }
      }
    }
  }

  function resumeRefreshEnabled() {
    const c = state.config || {};
    return c.uiResumeRefreshEnabled !== false;
  }

  function resumeRefreshMinIntervalMs() {
    const c = state.config || {};
    const n = Number(c.uiResumeRefreshMinIntervalSeconds);
    return Math.max(1000, Math.min(300000, (Number.isFinite(n) && n > 0 ? n : 5) * 1000));
  }

  async function refreshOnResume(reason = "resume") {
    if (!resumeRefreshEnabled()) return;
    if (guestChatHidden()) return;
    if (Date.now() < Number(state.replyJumpUntil || 0)) return;
    if (isScrollInteractionActive() || (Date.now() - Number(state.lastUserScrollAt || 0)) < Math.max(250, scrollInteractionIdleMs() * 2)) {
      requestResumeRefreshAfterScrollIdle(reason);
      return;
    }
    // Resume/focus may reconcile history, but it must not replace a middle
    // history slice or give any child content a special scroll lifecycle.
    const now = Date.now();
    if (state.resumeRefreshInFlight) return;
    if (now - state.lastResumeRefreshAt < resumeRefreshMinIntervalMs()) return;
    state.lastResumeRefreshAt = now;
    state.resumeRefreshInFlight = true;
    try {
      // Browsers, especially mobile browsers, may pause or close SSE/EventSource
      // while the tab/app is in the background. Reconnect and pull one fresh
      // history page when the UI becomes active again.
      if (!state.isPip && (!state.eventSource || state.eventSource.readyState === EventSource.CLOSED)) {
        connectStream();
      }
      await loadHistory(false, {skipIfUnchanged: true});
    } catch (e) {
      console.warn("KOKOTO WebChat resume refresh failed", reason, e);
    } finally {
      state.resumeRefreshInFlight = false;
    }
  }

  function installResumeRefreshHandlers() {
    const trigger = (reason) => {
      if (document.visibilityState && document.visibilityState !== "visible") return;
      setTimeout(() => refreshOnResume(reason), 150);
    };
    document.addEventListener("visibilitychange", () => trigger("visibilitychange"));
    window.addEventListener("focus", () => trigger("focus"));
    window.addEventListener("pageshow", () => trigger("pageshow"));
  }

  function clearStreamReconnectTimer() {
    if (state.streamReconnectTimer) {
      clearTimeout(state.streamReconnectTimer);
      state.streamReconnectTimer = null;
    }
  }

  function streamReconnectDelayMs() {
    const attempt = Math.max(0, Number(state.streamReconnectAttempt || 0));
    const base = Math.min(30000, 1000 * Math.pow(1.7, attempt));
    const jitter = Math.floor(Math.random() * 350);
    return Math.max(800, Math.floor(base + jitter));
  }

  function markStreamStatusReconnecting() {
    publishStandalonePipStream("reconnecting", "");
    const status = document.getElementById("kwc-status");
    if (status) status.textContent = t("status.reconnecting", "reconnecting...");
  }

  function markStreamActivity() {
    state.streamLastEventAt = Date.now();
  }

  function ensureStreamHealthWatchdog() {
    if (state.streamHealthTimer) return;
    state.streamHealthTimer = setInterval(() => {
      if (state.isPip || guestChatHidden()) return;
      const es = state.eventSource;
      if (!es) return;
      const last = Number(state.streamLastEventAt || state.streamLastOpenAt || 0);
      if (!last || Date.now() - last <= 65000) return;
      // Browsers can occasionally leave a dead SSE socket reporting OPEN after a
      // server restart. Force a fresh one-time stream ticket instead of waiting
      // indefinitely for native EventSource recovery on the consumed ticket URL.
      try { es.close(); } catch (_) {}
      if (state.eventSource === es) state.eventSource = null;
      scheduleStreamReconnect("stream-heartbeat-timeout");
    }, 15000);
  }

  function scheduleStreamReconnect(reason = "stream-error") {
    if (guestChatHidden()) return;
    state.streamReconnectAfterOpen = true;
    state.streamReconnectReason = reason || state.streamReconnectReason || "stream-error";
    markStreamStatusReconnecting();
    if (state.streamReconnectTimer) return;

    const delay = streamReconnectDelayMs();
    state.streamReconnectAttempt = Math.min(12, Number(state.streamReconnectAttempt || 0) + 1);
    state.streamReconnectTimer = setTimeout(() => {
      state.streamReconnectTimer = null;

      // Native EventSource may have recovered before our fallback timer fired.
      if (state.eventSource && state.eventSource.readyState === EventSource.OPEN) {
        state.streamReconnectAttempt = 0;
        return;
      }

      connectStream({refreshAfterOpen: true, reason: state.streamReconnectReason || reason});
    }, delay);
  }

  async function reconcileAfterStreamReconnect(reason = "stream-reconnect") {
    if (state.streamReconnectInFlight) return;
    if (guestChatHidden()) return;
    state.streamReconnectInFlight = true;
    try {
      // A server restart loses the old SSE connection and may also refresh the
      // web config/captcha state. Re-read config and one latest history page so
      // the page recovers without requiring a manual browser refresh.
      await loadConfig();
      await loadEmojis({force: true, retryOnFailure: true});
      await loadHistory(false, {skipIfUnchanged: false});
    } catch (e) {
      console.warn("KOKOTO WebChat stream reconnect refresh failed", reason, e);
      scheduleStreamReconnect("reconnect-refresh-failed");
    } finally {
      state.streamReconnectInFlight = false;
    }
  }

  // SSE 연결을 열고 서버 event를 단일 진입점에서 분배한다. 연결 종료는 정상적인 재시도 대상이며 backoff/중복 연결 방지 상태를 반드시 같이 관리한다.

  // Opens the SSE connection and dispatches server events from one entry point. Connection loss is recoverable; backoff and duplicate-connection guards must be maintained together.

  async function connectStream(options = {}) {
    const generation = ++state.streamGeneration;
    clearStreamReconnectTimer();

    if (state.eventSource) {
      try { state.eventSource.close(); } catch (_) {}
    }

    state.streamReconnectAfterOpen = !!options.refreshAfterOpen || state.streamReconnectAfterOpen;
    state.streamReconnectReason = options.reason || state.streamReconnectReason || "stream-connect";

    let streamUrl = apiBase + "/stream";
    if (state.token) {
      try {
        const ticketRes = await api("/stream-ticket", {method: "POST", body: "{}", timeoutMs: 10000});
        if (generation !== state.streamGeneration) return;
        const ticket = ticketRes && String(ticketRes.ticket || "").trim();
        if (!ticket) throw new Error("stream_ticket_missing");
        streamUrl += "?ticket=" + encodeURIComponent(ticket);
      } catch (_) {
        if (generation === state.streamGeneration) scheduleStreamReconnect("stream-ticket-error");
        return;
      }
    }
    if (generation !== state.streamGeneration) return;
    const es = new EventSource(streamUrl);
    state.eventSource = es;

    const handleConnected = () => {
      if (generation !== state.streamGeneration || state.eventSource !== es) return;
      state.streamLastOpenAt = Date.now();
      markStreamActivity();
      ensureStreamHealthWatchdog();
      publishStandalonePipStream("ready", "");
      state.streamReconnectAttempt = 0;
      clearStreamReconnectTimer();

      const status = document.getElementById("kwc-status");
      if (status && !state.token) status.textContent = t("status.guest", "guest");
      updateLoginState();
      if (state.token) setTimeout(() => refreshPresenceSurfaces().catch(() => {}), 100);

      if (state.streamReconnectAfterOpen) {
        const reason = state.streamReconnectReason || "stream-reconnect";
        state.streamReconnectAfterOpen = false;
        setTimeout(() => reconcileAfterStreamReconnect(reason), 150);
      }
    };

    es.onopen = handleConnected;
    es.addEventListener("ready", handleConnected);
    es.addEventListener("ping", () => markStreamActivity());
    es.addEventListener("emoji-catalog", () => {
      markStreamActivity();
      loadEmojis({force: true, retryOnFailure: true}).catch(() => {});
    });
    es.addEventListener("reaction-catalog", () => {
      markStreamActivity();
      state.reactionCatalog = null;
      state.reactionCatalogLoadedAt = 0;
      closeReactionPicker();
      loadReactionCatalog(true).then(refreshVisibleReactionBars).catch(() => {});
    });
    es.addEventListener("notification-view-state", e => {
      markStreamActivity();
      try { applyAccountNotificationViewState(JSON.parse(e.data || "{}")); } catch (_) {}
    });
    es.addEventListener("typing-config", () => {
      markStreamActivity();
      loadConfig().catch(() => {});
    });
    es.addEventListener("presence-update", e => {
      markStreamActivity();
      try {
        const data = JSON.parse(e.data || "{}");
        // Presence changes must not dismiss an open profile. The profile is a user-controlled
        // modal, while compact presence badges and group counts can refresh independently.
        const presenceUuid = String(data.uuid || "");
        refreshPresenceSurfaces(presenceUuid).catch(() => {});
        if (!presenceUuid) refreshAllVisiblePresenceBadges().catch(() => {});
      } catch (_) {}
    });
    es.addEventListener("game", e => {
      markStreamActivity();
      try {
        const update = JSON.parse(e && e.data || "{}");
        if (String(update.action || "") === "delete") {
          const game = update.game && typeof update.game === "object" ? update.game : {};
          markChatGameUnavailable(update.serverId || game.serverId || "", game.id || "");
        }
      } catch (_) {}
      const modal = document.querySelector(".kwc-game-modal");
      if (modal) refreshChatGameModal(modal.closest(".kwc-modal-backdrop")).catch(() => {});
    });
    es.addEventListener("chat", e => {
      markStreamActivity();
      publishStandalonePipStream("chat", e.data || "");
      if (guestChatHidden()) return;
      try {
        const msg = JSON.parse(e.data);
        if (state.historyHasAfter) {
          // The current viewport is a reply-jump middle slice. Do not append a
          // live tail message after a gap; keep the slice contiguous and let
          // newer-history paging or the latest button fetch the missing range.
          refreshScrollAffordances(document.getElementById("kwc-messages"));
          return;
        }
        clearTypingIndicatorsFromMessages("public", [msg]);
        addMessage(msg);
        maybeNotifyChatMessage(msg);
      } catch (_) {}
    });
    es.addEventListener("reaction", e => {
      markStreamActivity();
      publishStandalonePipStream("reaction", e.data || "");
      try { applyReactionUpdate(JSON.parse(e.data || "{}")); } catch (_) {}
    });
    es.addEventListener("reaction-status", e => {
      markStreamActivity();
      publishStandalonePipStream("reaction-status", e.data || "");
      try { handleReactionRequestStatus(JSON.parse(e.data || "{}")); } catch (_) {}
    });
    es.addEventListener("reaction-notification", e => {
      markStreamActivity();
      try { maybeNotifyReaction(JSON.parse(e.data || "{}")); } catch (_) {}
    });
    es.addEventListener("typing", e => {
      markStreamActivity();
      publishStandalonePipStream("typing", e.data || "");
      try { handleTypingEvent(JSON.parse(e.data || "{}")); } catch (_) {}
    });
    es.addEventListener("delete", e => {
      publishStandalonePipStream("delete", e.data || "");
      try { markMessageDeleted(JSON.parse(e.data).id); } catch (_) {}
    });
    es.addEventListener("dm", e => {
      publishStandalonePipStream("dm", e.data || "");
      try {
        const data = JSON.parse(e.data || "{}");
        if (!state.directMessageEnabled || !state.token) return;
        const eventThreadId = String(data.threadId || "").trim();
        const wasVisible = accountNotificationTargetActivelyViewed({dmThreadId: eventThreadId});
        loadDirectMessageThreads(true).then(() => {
          const thread = (state.dmThreads || []).find(t => String(t.id || "") === eventThreadId);
          if (thread && !wasVisible && !accountNotificationTargetActivelyViewed({dmThreadId: eventThreadId})) maybeNotifyDirectThread(thread);
          if (state.dmModalOpen && state.dmActiveThreadId && (!eventThreadId || eventThreadId === String(state.dmActiveThreadId || ""))) {
            loadDirectMessageMessages(state.dmActiveThreadId);
          }
        });
      } catch (_) {}
    });
    es.addEventListener("group", e => {
      publishStandalonePipStream("group", e.data || "");
      try {
        const data = JSON.parse(e.data || "{}");
        if (!state.groupChatEnabled || !state.token) return;
        const eventRoomId = String(data.roomId || "").trim();
        const wasVisible = accountNotificationTargetActivelyViewed({groupRoomId: eventRoomId});
        loadGroupChatRooms(true).then(() => {
          const room = (state.groupRooms || []).find(r => String(r.id || "") === eventRoomId);
          if (room && data.message && typeof data.message === "object" && !wasVisible && !accountNotificationTargetActivelyViewed({groupRoomId: eventRoomId})) maybeNotifyGroupMessage(data.message, room);
          if (state.groupModalOpen && state.groupActiveRoomId && (!eventRoomId || eventRoomId === String(state.groupActiveRoomId || ""))) {
            loadGroupPins(state.groupActiveRoomId).then(() => {
              renderGroupChatMessages(state.groupMessages, {preserveScroll: true});
            });
            loadGroupChatMessages(state.groupActiveRoomId);
          }
        });
      } catch (_) {}
    });
    es.addEventListener("pins", e => {
      publishStandalonePipStream("pins", e.data || "");
      try {
        const data = JSON.parse(e.data);
        if (data && Array.isArray(data.pins)) {
          state.pins = canViewPinnedMessages() ? data.pins : [];
          renderPinnedBar();
          if (state.messages && state.messages.length) scheduleVirtualRender({preserveScroll: true});
        }
      } catch (_) {}
    });
    es.addEventListener("auth", e => {
      publishStandalonePipStream("auth", e.data || "");
      try {
        const data = JSON.parse(e.data || "{}");
        handleAuthExpired(data.reason || "expired");
      } catch (_) {
        handleAuthExpired("expired");
      }
    });
    es.addEventListener("clear", () => {
      publishStandalonePipStream("clear", "");
      state.messages = [];
      state.nextLocalMessageId = 1;
      renderVirtualMessages({stickToBottom: true});
      state.historyHasMore = false;
      state.historyHasAfter = false;
      state.historyOldestId = "";
      state.historyNewestId = "";
    });
    es.onerror = () => {
      if (generation !== state.streamGeneration || state.eventSource !== es) return;
      // The authenticated stream URL contains a one-time ticket. Native
      // EventSource retry would reuse that consumed URL while KWC also schedules
      // its own fresh-ticket reconnect, causing duplicate/competing reconnects.
      // Stop the browser retry first and let exactly one KWC reconnect path run.
      try { es.close(); } catch (_) {}
      if (state.eventSource === es) state.eventSource = null;
      scheduleStreamReconnect("stream-error");
    };
  }
