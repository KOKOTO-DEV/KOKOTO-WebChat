// [KWC 유지보수 주석 / KWC maintenance notes]
// 문자열 escaping, Minecraft 색 코드, URL/미디어 판별, 공통 API 호출처럼 대부분의 화면 기능이 재사용하는 저수준 유틸리티를 모아 둔 조각이다.
// This fragment contains low-level utilities reused across most UI features: escaping, Minecraft color codes, URL/media handling, and the common API request path.
// esc()를 거치지 않은 사용자 입력을 innerHTML에 직접 넣지 말아야 하며, 미디어/링크 관련 함수는 XSS와 URL scheme 검증의 1차 방어선이다.
// User-controlled text must not be inserted into innerHTML without esc(); media/link helpers are part of the first-line XSS and URL-scheme defense.
// api() 계열은 인증 토큰, timeout, 오류 정규화, API base를 한곳에서 처리하므로 기능별 fetch를 별도로 만들기보다 이 경로를 우선 재사용한다.
// The api() family centralizes auth tokens, timeouts, error normalization, and API base handling; feature code should reuse it instead of creating ad-hoc fetch paths.

  function normalizeCommandMaxLength(value, fallback = 0) {
    const n = Number(value);
    if (!Number.isFinite(n)) return fallback;
    // 0 means unlimited. Positive values are used directly; no hard upper cap.
    return Math.max(0, Math.floor(n));
  }

  function esc(s) {
    return String(s ?? "").replace(/[&<>"']/g, c => ({
      "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;"
    })[c]);
  }

  function cssEscape(value) {
    const text = String(value ?? "");
    if (window.CSS && typeof window.CSS.escape === "function") {
      return window.CSS.escape(text);
    }
    // Minimal CSS.escape fallback for attribute selectors. This keeps message
    // action sync working in older/embedded browser contexts where CSS.escape
    // is not available.
    return text.replace(/[^a-zA-Z0-9_-]/g, ch => "\\" + ch);
  }


  const MC_LEGACY_COLORS = {
    "0": "#000000", "1": "#0000aa", "2": "#00aa00", "3": "#00aaaa",
    "4": "#aa0000", "5": "#aa00aa", "6": "#ffaa00", "7": "#aaaaaa",
    "8": "#555555", "9": "#5555ff", "a": "#55ff55", "b": "#55ffff",
    "c": "#ff5555", "d": "#ff55ff", "e": "#ffff55", "f": "#ffffff"
  };

  function stripMinecraftColorCodes(value) {
    let text = String(value ?? "");
    text = text.replace(/[§&]x(?:[§&][0-9a-fA-F]){6}/g, "");
    text = text.replace(/&#[0-9a-fA-F]{6}/g, "");
    text = text.replace(/[§&][0-9a-fA-Fk-oK-OrR]/g, "");
    return text;
  }

  function shouldRenderMinecraftNameColors() {
    return !!state.config && state.config.playerNameStripColors === false;
  }

  function sourceMayRenderMinecraftNameColors(source) {
    const s = String(source || "").toLowerCase();
    // Only actual chat senders may render Minecraft legacy colors.
    // Server/event/system lines keep legacy codes stripped even when
    // player-display.strip-colors is false.
    return s === "game" || s === "web" || s === "guest" || s === "discord" || s === "dm";
  }

  function normalizeMinecraftLegacySource(value) {
    // Reply previews may come from persisted JSON, plugin text, copied HTML,
    // or mis-decoded section signs. Normalize the common escaped/entity forms
    // before parsing so compact reply UI does not leak raw tags such as
    // &a, §a, \u00A7a, &amp;a, &#167;a, &sect;a, or Â§a.
    let text = String(value ?? "");
    for (let i = 0; i < 3; i++) {
      const next = text
        .replace(/\\u00a7/gi, "§")
        .replace(/\\xA7/gi, "§")
        .replace(/\\u0026/gi, "&")
        .replace(/\u00c2\u00a7/g, "§")
        .replace(/Â§/g, "§")
        .replace(/&amp;/gi, "&")
        .replace(/&#0*167;?/gi, "§")
        .replace(/&#x0*a7;?/gi, "§")
        .replace(/&sect;?/gi, "§");
      if (next === text) break;
      text = next;
    }
    return text;
  }

  function plainLegacyText(value) {
    return stripMinecraftColorCodes(normalizeMinecraftLegacySource(value));
  }

  function plainDialogText(value) {
    let text = plainLegacyText(value);
    text = text.replace(/<\/?[a-zA-Z][^>]*>/g, "");
    text = text.replace(/[\r\n]+/g, "\n").trim();
    return text;
  }

  function confirmPlain(value) {
    return confirm(plainDialogText(value));
  }

  function formatReplyComposeLabelHtml(sender) {
    const marker = "__KWC_REPLY_SENDER__";
    const template = fmt("reply.composing", "Replying to {sender}", {sender: marker});
    const parts = String(template || "").split(marker);
    if (parts.length < 2) return esc(fmt("reply.composing", "Replying to {sender}", {sender: plainLegacyText(sender)}));
    return parts.map(esc).join(minecraftLegacyTextHtml(sender, true));
  }

  function minecraftLegacyTextHtml(value, renderColors = true, allowLinks = true, readableUrlsWithoutLinks = false) {
    const text = normalizeMinecraftLegacySource(value);
    if (!renderColors) return allowLinks
      ? renderCustomEmojiTokens(stripMinecraftColorCodes(text), true, false)
      : renderCustomEmojiTokens(stripMinecraftColorCodes(text), false, readableUrlsWithoutLinks);

    let out = "";
    let buf = "";
    let style = {};

    const styleAttr = () => {
      const parts = [];
      if (style.color) parts.push("color:" + style.color);
      if (style.bold) parts.push("font-weight:700");
      if (style.italic) parts.push("font-style:italic");
      const deco = [];
      if (style.underline) deco.push("underline");
      if (style.strikethrough) deco.push("line-through");
      if (deco.length) parts.push("text-decoration:" + deco.join(" "));
      return parts.join(";");
    };
    const flush = () => {
      if (!buf) return;
      const attr = styleAttr();
      const html = renderCustomEmojiTokens(buf, allowLinks, readableUrlsWithoutLinks);
      out += attr ? `<span class="kwc-mc-legacy" style="${esc(attr)}">${html}</span>` : html;
      buf = "";
    };
    const resetFormatting = () => {
      style = {};
    };
    const setColor = color => {
      style = Object.assign({}, style, {color});
      // Minecraft color codes reset formatting in normal legacy text.
      delete style.bold;
      delete style.italic;
      delete style.underline;
      delete style.strikethrough;
    };

    for (let i = 0; i < text.length; i++) {
      const ch = text[i];
      if ((ch === "§" || ch === "&") && i + 1 < text.length) {
        const code = String(text[i + 1] || "").toLowerCase();
        if (code === "x" && i + 13 < text.length) {
          let hex = "";
          let ok = true;
          for (let j = 0; j < 6; j++) {
            const sep = text[i + 2 + j * 2];
            const digit = text[i + 3 + j * 2];
            if ((sep !== "§" && sep !== "&") || !/[0-9a-fA-F]/.test(digit || "")) {
              ok = false;
              break;
            }
            hex += digit;
          }
          if (ok) {
            flush();
            setColor("#" + hex);
            i += 13;
            continue;
          }
        }
        if (code === "#" && /^[0-9a-fA-F]{6}$/.test(text.slice(i + 2, i + 8))) {
          flush();
          setColor("#" + text.slice(i + 2, i + 8));
          i += 7;
          continue;
        }
        if (Object.prototype.hasOwnProperty.call(MC_LEGACY_COLORS, code)) {
          flush();
          setColor(MC_LEGACY_COLORS[code]);
          i++;
          continue;
        }
        if (code === "r") {
          flush();
          resetFormatting();
          i++;
          continue;
        }
        if ("lmnok".includes(code)) {
          flush();
          if (code === "l") style.bold = true;
          if (code === "o") style.italic = true;
          if (code === "n") style.underline = true;
          if (code === "m") style.strikethrough = true;
          // Obfuscated text (&k/§k) is intentionally not reproduced in the web UI.
          i++;
          continue;
        }
      }
      if (ch === "&" && text[i + 1] === "#" && /^[0-9a-fA-F]{6}$/.test(text.slice(i + 2, i + 8))) {
        flush();
        setColor("#" + text.slice(i + 2, i + 8));
        i += 7;
        continue;
      }
      buf += ch;
    }
    flush();
    return out;
  }

  function minecraftNameHtml(value, renderColors = shouldRenderMinecraftNameColors()) {
    return minecraftLegacyTextHtml(value, renderColors);
  }

  function plainMinecraftName(value) {
    return stripMinecraftColorCodes(String(value ?? ""));
  }

  function normalizeUrl(raw) {
    const url = String(raw || "");
    return /^https?:\/\//i.test(url) ? url : "https://" + url;
  }

  function isImageUrl(url) {
    try {
      const u = new URL(normalizeUrl(url), location.href);
      return /\.(?:png|jpe?g|gif|webp|avif|bmp)(?:$|[?#])/i.test(u.pathname + u.search);
    } catch (_) {
      return /\.(?:png|jpe?g|gif|webp|avif|bmp)(?:$|[?#])/i.test(String(url || ""));
    }
  }

  function parseUrls(value) {
    const text = String(value ?? "");
    const urlRe = /\b((?:https?:\/\/|www\.)[^\s<>"']+)/gi;
    const found = [];
    let match;
    while ((match = urlRe.exec(text)) !== null) {
      const raw = match[1];
      let url = raw;
      while (/[.,!?;:)\]\}]+$/.test(url)) {
        url = url.slice(0, -1);
      }
      if (url) found.push(url);
    }
    return found;
  }

  function displayLinkText(value) {
    const text = String(value ?? "");
    try { return decodeURI(text); } catch (_) { return text; }
  }

  function displayUrlsAsText(value) {
    const text = String(value ?? "");
    const urlRe = /\b((?:https?:\/\/|www\.)[^\s<>"']+)/gi;
    let out = "";
    let last = 0;
    let match;
    while ((match = urlRe.exec(text)) !== null) {
      const raw = match[1];
      let url = raw;
      let trailing = "";
      while (/[.,!?;:)\]\}]+$/.test(url)) {
        trailing = url.slice(-1) + trailing;
        url = url.slice(0, -1);
      }
      if (!url) continue;
      out += esc(text.slice(last, match.index));
      out += esc(displayLinkText(url));
      out += esc(trailing);
      last = match.index + raw.length;
    }
    out += esc(text.slice(last));
    return out;
  }

  function linkifyText(value) {
    const text = String(value ?? "");
    if (state.config && state.config.linkifyUrls === false) return esc(text);
    const urlRe = /\b((?:https?:\/\/|www\.)[^\s<>"']+)/gi;
    let out = "";
    let last = 0;
    let match;
    while ((match = urlRe.exec(text)) !== null) {
      const raw = match[1];
      let url = raw;
      let trailing = "";
      while (/[.,!?;:)\]\}]+$/.test(url)) {
        trailing = url.slice(-1) + trailing;
        url = url.slice(0, -1);
      }
      if (!url) continue;
      out += esc(text.slice(last, match.index));
      const href = safeExternalUrl(url);
      if (href) {
        out += `<a class="kwc-link" href="${esc(href)}" target="_blank" rel="noopener noreferrer">${esc(displayLinkText(url))}</a>`;
      } else {
        out += esc(raw);
      }
      out += esc(trailing);
      last = match.index + raw.length;
    }
    out += esc(text.slice(last));
    return out;
  }

  let lastExternalLinkOpen = {href: "", time: 0};

  function openChatExternalLink(href) {
    href = String(href || "").trim();
    if (!/^https?:\/\//i.test(href)) return false;
    let key = href;
    try { key = new URL(href, location.href).href; } catch (_) {}
    const now = Date.now();
    if (key && key === lastExternalLinkOpen.href && now - lastExternalLinkOpen.time < 1600) {
      return true;
    }
    lastExternalLinkOpen = {href: key, time: now};
    try {
      const win = window.open(href, "_blank", "noopener,noreferrer");
      if (win) {
        try { win.opener = null; } catch (_) {}
        return true;
      }
    } catch (_) {}
    // Keep the debounce even when popup creation returns null. On some mobile
    // browsers the native anchor click can still be delivered after pointerup,
    // and clearing the guard here can reopen the same link multiple times.
    return false;
  }

  function normalizeReturnedUploadUrl(raw) {
    const value = String(raw || "").trim();
    if (!value) return value;
    if (/^https?:\/\//i.test(value)) return value;
    try {
      return new URL(value, location.href).href;
    } catch (_) {
      return value;
    }
  }

  function safeHttpUrl(raw, options = {}) {
    const value = String(raw || "").trim();
    if (!value || /[\u0000-\u001f\u007f]/.test(value)) return "";
    const allowRelative = options.allowRelative === true;
    try {
      const normalized = /^www\./i.test(value) ? "https://" + value : value;
      if (!allowRelative && !/^https?:\/\//i.test(normalized)) return "";
      const u = new URL(normalized, location.href);
      if (u.protocol !== "http:" && u.protocol !== "https:") return "";
      return u.href;
    } catch (_) {
      return "";
    }
  }

  function safeExternalUrl(raw) {
    return safeHttpUrl(raw, {allowRelative: false});
  }

  function apiBasePath() {
    try {
      return new URL(String(apiBase || ""), location.href).pathname.replace(/\/+$/, "");
    } catch (_) {
      return "";
    }
  }

  function firstApiResourceSuffix(path) {
    const value = String(path || "");
    const names = ["/emojis", "/uploads", "/external-media", "/fonts"];
    let best = -1;
    for (const name of names) {
      const idx = value.indexOf(name);
      if (idx >= 0 && (best < 0 || idx < best)) best = idx;
    }
    return best >= 0 ? value.slice(best) : "";
  }

  function apiResourceUrl(raw) {
    const value = String(raw || "").trim();
    if (!value) return "";
    if (/^https?:\/\//i.test(value)) return value;

    const base = String(apiBase || "").replace(/\/+$/, "");
    if (!base) return value;

    try {
      if (value.startsWith("/")) {
        const basePath = apiBasePath();
        const valuePath = new URL(value, location.href).pathname.replace(/\/+$/, "");
        // If the server already returned the same public path, keep it as-is.
        // This is important for explicit settings such as /chat/api/emojis.
        if (basePath && (valuePath === basePath || valuePath.startsWith(basePath + "/"))) {
          return new URL(value, location.href).href;
        }
        // Also keep explicitly configured prefixed API resource paths such as
        // /chat/api/uploads even if the runtime API base is currently /api.
        // Only plain internal /api/... paths are candidates for rewriting.
        if (/^\/.+\/api\/(?:emojis|uploads|external-media|fonts)(?:\/|$)/i.test(valuePath)) {
          return new URL(value, location.href).href;
        }
        // If the server returned an internal path such as /api/emojis while the
        // browser uses /chat/api, keep only the resource suffix and attach it to
        // the runtime API base.
        const suffix = firstApiResourceSuffix(value);
        if (suffix) return new URL(base + suffix, location.href).href;
        return new URL(value, location.href).href;
      }
      return new URL(base + "/" + value.replace(/^\/+/, ""), location.href).href;
    } catch (_) {
      return value;
    }
  }

  function safePreviewUrl(raw) {
    // Preview URLs may be external http(s) URLs or same-origin relative API
    // URLs generated by KOKOTO WebChat, such as /chat/api/uploads/... .
    // Normalize internal /api/... resource paths to the runtime API base so
    // explicit reverse-proxy settings keep working.
    const value = String(raw || "").trim();
    const normalized = /^https?:\/\//i.test(value) ? value : apiResourceUrl(value);
    return safeHttpUrl(normalized || value, {allowRelative: true});
  }

  function safeYouTubeEmbedUrl(raw) {
    const href = safePreviewUrl(raw);
    if (!href) return "";
    try {
      const u = new URL(href, location.href);
      const host = u.hostname.toLowerCase();
      if ((host === "www.youtube.com" || host === "www.youtube-nocookie.com") && u.pathname.startsWith("/embed/")) return u.href;
    } catch (_) {}
    return "";
  }

  function installMessageActionDelegation(root) {
    if (!root || root.__kwcActionDelegationInstalled) return;
    root.__kwcActionDelegationInstalled = true;

    const selector = "[data-delete], [data-pin], [data-unpin], [data-pin-move], [data-open-pins], a.kwc-link, a.kwc-image-link";
    let pointerDownAction = null;
    let touchDownAction = null;
    let lastPointerAction = {key: "", time: 0};

    const actionTarget = event => {
      const target = event.target && event.target.closest
        ? event.target.closest(selector)
        : null;
      return target && root.contains(target) ? target : null;
    };

    const actionKey = target => {
      if (!target) return "";
      const deleteId = target.getAttribute("data-delete");
      if (deleteId) return "delete:" + deleteId;
      const pinId = target.getAttribute("data-pin");
      if (pinId) return "pin:" + pinId;
      const unpinId = target.getAttribute("data-unpin");
      if (unpinId) return "unpin:" + unpinId;
      const movePinId = target.getAttribute("data-pin-move");
      if (movePinId) return "move-pin:" + movePinId + ":" + (target.getAttribute("data-direction") || "");
      if (target.hasAttribute("data-open-pins")) return "open-pins";
      const href = target.getAttribute("href") || "";
      if (href && target.matches("a.kwc-link, a.kwc-image-link")) return "link:" + href;
      return "";
    };

    const releasePointerActionScrollState = () => {
      // Action controls run in a capturing pointer handler and intentionally
      // stop propagation to avoid duplicate click actions. On touch browsers
      // that can prevent the history-paging window pointerup listener from
      // seeing the end of the gesture, leaving touchScrollActive true. If that
      // state remains set, virtual render/history refresh work is deferred and
      // scrolling can feel temporarily stuck after opening a link.
      try {
        pointerDownAction = null;
        const hadInteraction = !!state.touchScrollActive || !!state.scrollbarDragActive || Date.now() < Number(state.scrollInteractionUntil || 0);
        state.touchScrollActive = false;
        state.scrollbarDragActive = false;
        state.scrollInteractionUntil = 0;
        if (hadInteraction) {
          clearTimeout(state.scrollIdleTimer);
          state.scrollIdleTimer = setTimeout(flushScrollInteractionWork, 0);
        }
      } catch (_) {}
    };

    const runAction = (event, target) => {
      const deleteId = target.getAttribute("data-delete");
      if (deleteId) {
        releasePointerActionScrollState();
        event.preventDefault();
        event.stopPropagation();
        deleteMessage(deleteId);
        return true;
      }

      const pinId = target.getAttribute("data-pin");
      if (pinId) {
        releasePointerActionScrollState();
        event.preventDefault();
        event.stopPropagation();
        pinMessage(pinId);
        return true;
      }

      const unpinId = target.getAttribute("data-unpin");
      if (unpinId) {
        releasePointerActionScrollState();
        event.preventDefault();
        event.stopPropagation();
        unpinMessage(unpinId);
        return true;
      }

      const movePinId = target.getAttribute("data-pin-move");
      if (movePinId) {
        releasePointerActionScrollState();
        event.preventDefault();
        event.stopPropagation();
        movePinnedMessage(movePinId, target.getAttribute("data-direction") || "");
        return true;
      }

      if (target.hasAttribute("data-open-pins")) {
        releasePointerActionScrollState();
        event.preventDefault();
        event.stopPropagation();
        openPinnedModal();
        return true;
      }

      const href = target.getAttribute("href") || "";
      if (href && target.matches("a.kwc-link, a.kwc-image-link") && /^https?:\/\//i.test(href)) {
        releasePointerActionScrollState();
        event.preventDefault();
        event.stopPropagation();
        openChatExternalLink(href);
        return true;
      }
      return false;
    };

    root.addEventListener("pointerdown", event => {
      const target = actionTarget(event);
      if (!target || event.button !== 0) return;
      pointerDownAction = {
        key: actionKey(target),
        x: event.clientX,
        y: event.clientY,
        button: event.button
      };
      if (target.hasAttribute("data-open-pins")) {
        // Mobile browsers can turn a small tap on the pinned bar into a tiny
        // scroll/drag and then drop the synthetic click. Keep the gesture local
        // to the chat iframe and let pointerup/touchend handle the open action.
        event.preventDefault();
        event.stopPropagation();
      }
    }, true);

    root.addEventListener("pointerup", event => {
      const target = actionTarget(event);
      if (!target || !pointerDownAction) return;
      const down = pointerDownAction;
      pointerDownAction = null;
      if (event.button !== 0 || down.button !== 0) return;

      const key = actionKey(target);
      const moved = Math.hypot(event.clientX - down.x, event.clientY - down.y);
      const moveLimit = target.hasAttribute("data-open-pins") ? 32 : 8;
      if (!key || key !== down.key || moved > moveLimit) return;

      const now = Date.now();
      if (key === lastPointerAction.key && now - lastPointerAction.time < 500) {
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      if (runAction(event, target)) {
        // Native confirm() blocks the event loop. If we keep the pre-confirm
        // timestamp, the browser's follow-up click can arrive after the
        // duplicate-action window and ask for confirmation a second time.
        // Stamp the action after runAction() returns so OK/Cancel is handled
        // exactly once for pointer-driven taps/clicks.
        lastPointerAction = {key, time: Date.now()};
      }
    }, true);

    root.addEventListener("pointercancel", () => {
      pointerDownAction = null;
    }, true);

    root.addEventListener("touchstart", event => {
      const target = actionTarget(event);
      if (!target || !target.hasAttribute("data-open-pins")) return;
      const t = event.touches && event.touches[0] ? event.touches[0] : null;
      if (!t) return;
      touchDownAction = {
        key: actionKey(target),
        x: t.clientX,
        y: t.clientY,
        target
      };
      event.preventDefault();
      event.stopPropagation();
    }, {capture: true, passive: false});

    root.addEventListener("touchend", event => {
      if (!touchDownAction) return;
      const down = touchDownAction;
      touchDownAction = null;
      const t = event.changedTouches && event.changedTouches[0] ? event.changedTouches[0] : null;
      const target = actionTarget(event) || down.target;
      const key = actionKey(target);
      const moved = t ? Math.hypot(t.clientX - down.x, t.clientY - down.y) : 0;
      const now = Date.now();
      if (key && key === down.key && moved <= 32) {
        if (!(key === lastPointerAction.key && now - lastPointerAction.time < 1200) && runAction(event, target)) {
          lastPointerAction = {key, time: Date.now()};
        }
      }
      event.preventDefault();
      event.stopPropagation();
    }, {capture: true, passive: false});

    root.addEventListener("touchcancel", () => {
      touchDownAction = null;
    }, {capture: true, passive: true});

    root.addEventListener("click", event => {
      const target = actionTarget(event);
      if (!target) return;

      const key = actionKey(target);
      const now = Date.now();
      if (key && key === lastPointerAction.key && now - lastPointerAction.time < 1800) {
        event.preventDefault();
        event.stopPropagation();
        return;
      }

      if (runAction(event, target) && key) {
        lastPointerAction = {key, time: Date.now()};
      }
    }, true);
  }

  function isVideoUrl(url) {
    try {
      const u = new URL(normalizeUrl(url), location.href);
      return /\.(?:mp4|webm|mov)(?:$|[?#])/i.test(u.pathname + u.search);
    } catch (_) {
      return /\.(?:mp4|webm|mov)(?:$|[?#])/i.test(String(url || ""));
    }
  }

  function isAudioUrl(url) {
    try {
      const u = new URL(normalizeUrl(url), location.href);
      return /\.(?:mp3|m4a|ogg|oga|wav|flac|aac)(?:$|[?#])/i.test(u.pathname + u.search);
    } catch (_) {
      return /\.(?:mp3|m4a|ogg|oga|wav|flac|aac)(?:$|[?#])/i.test(String(url || ""));
    }
  }

  function isDiscordCdnUrl(raw) {
    try {
      const u = new URL(normalizeUrl(raw), location.href);
      if (u.protocol !== "https:") return false;
      const host = u.hostname.toLowerCase();
      const okHost = host === "cdn.discordapp.com"
        || host === "media.discordapp.net"
        || host === "cdn.discordapp.net"
        || /^images-ext-\d+\.discordapp\.net$/.test(host);
      if (!okHost) return false;

      const path = u.pathname.toLowerCase();
      if (/\.(?:png|jpe?g|gif|webp|avif|bmp|mp4|webm|mov|mp3|m4a|ogg|oga|wav|flac|aac)(?:$|[?#])/i.test(u.pathname + u.search)) return true;
      if (/(?:^|[&?])format=(?:png|jpe?g|gif|webp|avif|bmp|mp4|webm|mp3|m4a|ogg|oga|wav|flac|aac)(?:$|&)/i.test(u.search)) return true;

      // Broad candidate support: let the server fetch and verify Content-Type.
      // Failed/invalid/non-media resources are hidden by the media onerror handler.
      return path.includes("/attachments/")
        || path.includes("/ephemeral-attachments/")
        || path.startsWith("/external/");
    } catch (_) {
      return false;
    }
  }


  function discordCdnPreviewUrl(raw) {
    if (!state.config || !state.config.externalMediaCacheEnabled || !state.config.cacheDiscordCdn) return "";
    const href = normalizeUrl(raw);
    if (!isDiscordCdnUrl(href)) return "";
    return apiBase + "/external-media?url=" + encodeURIComponent(href);
  }

  function previewMediaType(raw) {
    if (isVideoUrl(raw)) return "video";
    if (isAudioUrl(raw)) return "audio";
    if (isImageUrl(raw)) return "image";
    return "";
  }

  function mediaClickToLoadEnabled() {
    return !state.config || state.config.mediaClickToLoad !== false;
  }

  function googleDriveFileId(raw) {
    try {
      const u = new URL(normalizeUrl(raw), location.href);
      const host = u.hostname.toLowerCase();
      if (!host.endsWith("google.com") && !host.endsWith("googleusercontent.com")) return "";

      let match = u.pathname.match(/\/file\/d\/([^/]+)/);
      if (match && match[1]) return decodeURIComponent(match[1]);

      match = u.pathname.match(/\/uc$/);
      if (match && u.searchParams.get("id")) return u.searchParams.get("id");

      match = u.pathname.match(/\/open$/);
      if (match && u.searchParams.get("id")) return u.searchParams.get("id");

      if (u.searchParams.get("id")) return u.searchParams.get("id");
    } catch (_) {}
    return "";
  }

  function googleDrivePreviewUrl(raw) {
    if (!state.config || state.config.googleDriveImagePreview !== true) return "";
    const id = googleDriveFileId(raw);
    if (!id || !/^[A-Za-z0-9_-]+$/.test(id)) return "";

    const mode = String(state.config.googleDrivePreviewMode || "thumbnail").toLowerCase();
    if (mode === "uc") {
      return "https://drive.google.com/uc?export=view&id=" + encodeURIComponent(id);
    }

    return "https://drive.google.com/thumbnail?id=" + encodeURIComponent(id) + "&sz=w1600";
  }

  function youtubeVideoInfo(raw) {
    if (!state.config || state.config.youtubeEmbedEnabled === false) return {id: "", shorts: false};
    try {
      const u = new URL(normalizeUrl(raw), location.href);
      const host = u.hostname.toLowerCase().replace(/^www\./, "");
      if (host === "youtu.be") {
        const id = u.pathname.split("/").filter(Boolean)[0] || "";
        return /^[A-Za-z0-9_-]{6,20}$/.test(id) ? {id, shorts: false} : {id: "", shorts: false};
      }
      if (host === "youtube.com" || host === "m.youtube.com" || host === "music.youtube.com" || host === "youtube-nocookie.com") {
        let id = u.searchParams.get("v") || "";
        let shorts = false;
        if (!id && u.pathname.startsWith("/shorts/")) {
          id = u.pathname.split("/").filter(Boolean)[1] || "";
          shorts = true;
        }
        if (!id && u.pathname.startsWith("/embed/")) id = u.pathname.split("/").filter(Boolean)[1] || "";
        return /^[A-Za-z0-9_-]{6,20}$/.test(id) ? {id, shorts} : {id: "", shorts: false};
      }
    } catch (_) {}
    return {id: "", shorts: false};
  }

  function youtubeVideoId(raw) {
    return youtubeVideoInfo(raw).id || "";
  }

  function youtubeEmbedUrl(id, autoplay = false, loop = false) {
    const host = state.config && state.config.youtubeNoCookie === false ? "www.youtube.com" : "www.youtube-nocookie.com";
    const params = new URLSearchParams();
    params.set("rel", "0");
    params.set("modestbranding", "1");
    params.set("playsinline", "1");
    if (loop) {
      params.set("loop", "1");
      params.set("playlist", id);
    }
    if (autoplay) params.set("autoplay", "1");
    try { if (location && location.origin && location.origin !== "null") params.set("origin", location.origin); } catch (_) {}
    return "https://" + host + "/embed/" + encodeURIComponent(id) + "?" + params.toString();
  }

  function youtubeShellStyle(shorts, maxHeightCss = "") {
    if (shorts) {
      return "position:relative;width:min(100%,260px);max-width:100%;aspect-ratio:9/16;max-height:420px;border-radius:10px;overflow:hidden;background:#000;margin:6px auto 0;";
    }
    return "position:relative;width:100%;aspect-ratio:16/9;" + String(maxHeightCss || "") + "border-radius:10px;overflow:hidden;background:#000;margin-top:6px;";
  }

  function socialVerticalLoadCardStyle() {
    return "position:relative;width:min(100%,260px);max-width:100%;height:180px;border-radius:10px;overflow:hidden;background:#000;margin:6px auto 0;display:flex;align-items:center;justify-content:center;";
  }

  function youtubeThumbUrl(id) {
    return "https://i.ytimg.com/vi/" + encodeURIComponent(id) + "/hqdefault.jpg";
  }

  function socialEmbedsEnabled() {
    return !!(state.config && state.config.socialEmbedsEnabled === true);
  }

  function socialClickToLoadEnabled() {
    return !state.config || state.config.socialEmbedsClickToLoad !== false;
  }

  function tiktokVideoId(raw) {
    if (!socialEmbedsEnabled() || !state.config.tiktokEmbedEnabled) return "";
    try {
      const u = new URL(normalizeUrl(raw), location.href);
      const host = u.hostname.toLowerCase().replace(/^www\./, "");
      if (host !== "tiktok.com" && host !== "m.tiktok.com") return "";
      let match = u.pathname.match(/\/video\/(\d{8,32})/);
      if (match && match[1]) return match[1];
      match = u.pathname.match(/\/embed\/v2\/(\d{8,32})/);
      if (match && match[1]) return match[1];
    } catch (_) {}
    return "";
  }

  function xPostInfo(raw) {
    if (!socialEmbedsEnabled() || !state.config.xEmbedEnabled) return null;
    try {
      const u = new URL(normalizeUrl(raw), location.href);
      const host = u.hostname.toLowerCase().replace(/^www\./, "");
      if (host !== "x.com" && host !== "twitter.com" && host !== "mobile.twitter.com") return null;
      const match = u.pathname.match(/^\/([^\/]+)\/status(?:es)?\/(\d{6,32})/i);
      if (!match || !match[1] || !match[2]) return null;
      const url = "https://x.com/" + encodeURIComponent(match[1]) + "/status/" + encodeURIComponent(match[2]);
      return {user: match[1], id: match[2], url};
    } catch (_) {}
    return null;
  }

  function tiktokCanonicalUrl(raw, id) {
    const safe = safeExternalUrl(raw);
    if (safe && /(^|\.)tiktok\.com$/i.test((() => { try { return new URL(safe).hostname.replace(/^www\./, ""); } catch (_) { return ""; } })())) return safe;
    if (!/^\d{8,32}$/.test(String(id || ""))) return "";
    return "https://www.tiktok.com/@_/video/" + encodeURIComponent(id);
  }

  function tiktokPlayerUrl(id) {
    if (!/^\d{8,32}$/.test(String(id || ""))) return "";
    const params = new URLSearchParams();
    params.set("controls", "1");
    params.set("progress_bar", "1");
    params.set("play_button", "1");
    params.set("volume_control", "1");
    params.set("fullscreen_button", "1");
    params.set("timestamp", "1");
    params.set("loop", "1");
    params.set("autoplay", "0");
    params.set("music_info", "0");
    params.set("description", "0");
    params.set("rel", "0");
    params.set("native_context_menu", "1");
    params.set("closed_caption", "1");
    return "https://www.tiktok.com/player/v1/" + encodeURIComponent(id) + "?" + params.toString();
  }

  function tiktokPlayerShellStyle() {
    return "position:relative;width:min(100%,325px);max-width:100%;height:575px;border-radius:10px;overflow:hidden;background:#000;margin:6px auto 0;";
  }

  function xThemeValue() {
    const v = String(state.config && state.config.xEmbedTheme || "auto").toLowerCase();
    if (v === "dark" || v === "light") return v;
    const root = document.getElementById("kwc-root");
    return root && root.classList.contains("kwc-theme-light") ? "light" : "dark";
  }

  let xWidgetsLoading = false;
  function loadXWidgets(root) {
    try {
      if (window.twttr && window.twttr.widgets && typeof window.twttr.widgets.load === "function") {
        window.twttr.widgets.load(root || document.body);
        return;
      }
      if (!document.querySelector('script[src="https://platform.twitter.com/widgets.js"]')) {
        const script = document.createElement("script");
        script.async = true;
        script.charset = "utf-8";
        script.src = "https://platform.twitter.com/widgets.js";
        document.head.appendChild(script);
      }
      if (!xWidgetsLoading) {
        xWidgetsLoading = true;
        setTimeout(() => {
          xWidgetsLoading = false;
          if (window.twttr && window.twttr.widgets && typeof window.twttr.widgets.load === "function") window.twttr.widgets.load(root || document.body);
        }, 1200);
      }
    } catch (_) {}
  }

  function socialEmbedHtml(item, maxHeightCss = "") {
    const key = item.previewKey || previewKey(item.type, item.href);
    if (item.type === "tiktok") {
      const id = String(item.tiktokId || "");
      if (!/^\d{8,32}$/.test(id)) return "";
      const href = tiktokCanonicalUrl(item.href, id);
      const player = tiktokPlayerUrl(id);
      if (!href || !player) return "";
      if (socialClickToLoadEnabled() && !state.mediaOpen.has(key)) {
        return `<div class="kwc-social-card kwc-tiktok-card" data-social-kind="tiktok" data-social-src="${esc(href)}" data-tiktok-id="${esc(id)}" data-preview-key="${esc(key)}" style="${socialVerticalLoadCardStyle()}">
          <button type="button" class="kwc-media-load kwc-button">${kwcFaIcon("play")}<span>${esc(t("media.loadTikTok", "TikTok"))}</span></button>
        </div>`;
      }
      return `<div class="kwc-social-embed kwc-tiktok-embed" data-preview-key="${esc(key)}" style="${tiktokPlayerShellStyle()}">
        <iframe class="kwc-social-frame" src="${esc(player)}" title="TikTok" loading="lazy" referrerpolicy="strict-origin-when-cross-origin" allow="encrypted-media; fullscreen; picture-in-picture; web-share" allowfullscreen style="position:absolute;inset:0;width:100%;height:100%;border:0;background:#000;color-scheme:dark;"></iframe>
      </div>
      <div class="kwc-social-open" style="width:min(100%,325px);max-width:100%;margin:4px auto 0;font-size:11px;opacity:.78;text-align:right;">
        <a class="kwc-link" href="${esc(href)}" target="_blank" rel="noopener noreferrer">${esc(t("media.openTikTok", "Open on TikTok"))}</a>
      </div>`;
    }
    if (item.type === "x") {
      const href = safeExternalUrl(item.href);
      if (!href) return "";
      if (socialClickToLoadEnabled() && !state.mediaOpen.has(key)) {
        return `<div class="kwc-social-card kwc-x-card" data-social-kind="x" data-social-src="${esc(href)}" data-preview-key="${esc(key)}" style="${maxHeightCss}">
          <button type="button" class="kwc-media-load kwc-button">${kwcFaIcon("play")}<span>${esc(t("media.loadXPost", "X post"))}</span></button>
        </div>`;
      }
      const theme = xThemeValue();
      const dnt = state.config && state.config.xEmbedDnt !== false ? "true" : "false";
      const hideMedia = state.config && state.config.xEmbedHideMedia === true ? ' data-cards="hidden"' : "";
      const hideThread = state.config && state.config.xEmbedHideThread !== false ? ' data-conversation="none"' : "";
      return `<div class="kwc-social-embed kwc-x-embed" data-preview-key="${esc(key)}" style="margin-top:6px;${maxHeightCss}">
        <blockquote class="twitter-tweet" data-theme="${esc(theme)}" data-dnt="${esc(dnt)}"${hideMedia}${hideThread}><a href="${esc(href)}"></a></blockquote>
      </div>`;
    }
    return "";
  }

  function previewKey(kind, href) {
    return String(kind || "media") + ":" + String(href || "");
  }

  function scopedPreviewKey(kind, href, messageKey = "") {
    const base = previewKey(kind, href);
    const scope = String(messageKey || "");
    return scope.startsWith("private:") ? scope + ":" + base : base;
  }

  window.__kwcPreviewFailed = function(key) {
    if (key) state.failedMediaPreviews.add(String(key));
  };

  window.__kwcPreviewLoaded = function() {
    // Layout is tracked at the message container level by ResizeObserver.
    // Individual media elements do not own virtual-scroll behavior.
  };

  function mediaErrorText(kind) {
    if (kind === "audio") return t("media.audioUnavailable", "Audio preview unavailable");
    if (kind === "image") return t("media.imageUnavailable", "Image preview unavailable");
    return t("media.videoUnavailable", "Video preview unavailable");
  }

  function setMediaError(wrap, kind) {
    if (!wrap) return;
    wrap.textContent = "";
    wrap.classList.add("kwc-media-failed");
    const span = document.createElement("span");
    span.className = kind === "image" ? "kwc-media-error kwc-image-error" : "kwc-media-error";
    span.textContent = mediaErrorText(kind);
    wrap.appendChild(span);
  }

  function createMediaElement(kind, src, key, maxHeightStyle = "") {
    const safeSrc = safePreviewUrl(src);
    if (!safeSrc) return null;
    const media = document.createElement(kind === "audio" ? "audio" : "video");
    media.className = kind === "audio" ? "kwc-audio-preview" : "kwc-video-preview";
    media.src = safeSrc;
    media.controls = true;
    if (kind === "audio") {
      media.preload = "none";
    } else {
      media.playsInline = true;
      media.setAttribute("webkit-playsinline", "");
      media.preload = "metadata";
      if (maxHeightStyle) media.setAttribute("style", maxHeightStyle);
    }
    if (key) media.dataset.previewKey = key;
    return media;
  }

  function hydrateSocialEmbeds(root) {
    if (!root) return;
    if (root.querySelector && root.querySelector(".twitter-tweet")) loadXWidgets(root);
  }

  function hydratePreviewMedia(root) {
    if (!root) return;
    root.querySelectorAll(".kwc-image-preview").forEach(img => {
      if (img.dataset.kwcPreviewHydrated === "1") return;
      img.dataset.kwcPreviewHydrated = "1";
      const src = safePreviewUrl(img.getAttribute("src"));
      if (!src) {
        setMediaError(img.closest(".kwc-image-link"), "image");
        return;
      }
      img.src = src;
      img.addEventListener("load", () => window.__kwcPreviewLoaded && window.__kwcPreviewLoaded(img), {once: true});
      img.addEventListener("error", () => {
        window.__kwcPreviewFailed && window.__kwcPreviewFailed(img.dataset.previewKey);
        setMediaError(img.closest(".kwc-image-link"), "image");
        window.__kwcPreviewLoaded && window.__kwcPreviewLoaded(img);
      }, {once: true});
    });

    root.querySelectorAll(".kwc-video-preview, .kwc-audio-preview").forEach(media => {
      if (media.dataset.kwcPreviewHydrated === "1") return;
      media.dataset.kwcPreviewHydrated = "1";
      const kind = media.tagName === "AUDIO" ? "audio" : "video";
      const src = safePreviewUrl(media.getAttribute("src"));
      const wrap = media.closest(kind === "audio" ? ".kwc-audio-wrap" : ".kwc-video-wrap");
      if (!src) {
        setMediaError(wrap, kind);
        return;
      }
      media.src = src;
      media.addEventListener("loadedmetadata", () => window.__kwcPreviewLoaded && window.__kwcPreviewLoaded(media), {once: true});
      media.addEventListener("error", () => {
        window.__kwcPreviewFailed && window.__kwcPreviewFailed(media.dataset.previewKey);
        setMediaError(wrap, kind);
      }, {once: true});
    });
  }

  function safeImagePreviews(value, messageKey = "") {
    try {
      return imagePreviews(value, messageKey);
    } catch (err) {
      try { console.warn("media preview failed", err); } catch (_) {}
      return "";
    }
  }

  function imagePreviews(value, messageKey = "") {
    if (!state.config) return "";
    const configuredMax = Number(state.config.imagePreviewMaxPerMessage);
    const fallbackMax = Number(state.config.uploadMaxFilesPerMessage);
    const max = Number.isFinite(configuredMax)
      ? Math.max(0, Math.floor(configuredMax))
      : (Number.isFinite(fallbackMax) ? Math.max(0, Math.floor(fallbackMax)) : 3);
    const unlimitedPreviews = max === 0;
    const heightConfig = Number(state.config.imagePreviewMaxHeight);
    const height = Number.isFinite(heightConfig) && heightConfig > 0 ? Math.floor(heightConfig) : 0;
    const maxHeightStyle = height > 0 ? `max-height:${height}px` : "";
    const maxHeightCss = height > 0 ? `max-height:${height}px;` : "";
    const items = [];
    for (const url of parseUrls(value)) {
      if (!unlimitedPreviews && items.length >= max) break;
      const href = safeExternalUrl(url);
      if (!href) continue;
      const youtubeInfo = youtubeVideoInfo(url);
      const youtubeId = youtubeInfo.id || "";
      const tiktokId = tiktokVideoId(url);
      const xPost = xPostInfo(url);
      const discordPreview = safePreviewUrl(discordCdnPreviewUrl(url));
      const drivePreview = safePreviewUrl(googleDrivePreviewUrl(url));
      if (items.some(item => item.href === href || item.linkHref === href || (discordPreview && item.href === discordPreview) || (drivePreview && item.href === drivePreview) || (youtubeId && item.youtubeId === youtubeId) || (tiktokId && item.tiktokId === tiktokId) || (xPost && item.xPostId === xPost.id))) continue;
      const imageKey = scopedPreviewKey("image", drivePreview || discordPreview || href, messageKey);
      const videoKey = scopedPreviewKey("video", discordPreview || href, messageKey);
      const audioKey = scopedPreviewKey("audio", discordPreview || href, messageKey);
      const socialMaxConfig = Number(state.config.socialEmbedsMaxPerMessage);
      const maxSocial = Number.isFinite(socialMaxConfig) ? Math.max(0, Math.floor(socialMaxConfig)) : 2;
      const socialCount = items.filter(item => item.type === "tiktok" || item.type === "x").length;
      if (youtubeId) {
        const configuredYoutubeMax = Number(state.config.youtubeMaxEmbedsPerMessage);
        const maxYoutube = Number.isFinite(configuredYoutubeMax) ? Math.max(0, Math.floor(configuredYoutubeMax)) : 1;
        const youtubeCount = items.filter(item => item.type === "youtube").length;
        if (maxYoutube === 0 || youtubeCount < maxYoutube) items.push({type: "youtube", href, youtubeId, youtubeShorts: youtubeInfo.shorts === true, youtubeKey: String(messageKey || "message") + ":" + youtubeId});
      } else if (tiktokId && (maxSocial === 0 || socialCount < maxSocial)) {
        items.push({type: "tiktok", href, tiktokId, previewKey: scopedPreviewKey("tiktok", tiktokId, messageKey)});
      } else if (xPost && (maxSocial === 0 || socialCount < maxSocial)) {
        items.push({type: "x", href: xPost.url, xPostId: xPost.id, previewKey: scopedPreviewKey("x", xPost.id, messageKey)});
      } else if (discordPreview) {
        const mediaType = previewMediaType(url);
        if (mediaType === "video" && state.config.uploadPreviewVideos && !state.failedMediaPreviews.has(videoKey)) {
          items.push({type: "video", href: discordPreview, linkHref: href, previewKey: videoKey});
        } else if (mediaType === "audio" && state.config.uploadPreviewAudio && !state.failedMediaPreviews.has(audioKey)) {
          items.push({type: "audio", href: discordPreview, linkHref: href, previewKey: audioKey});
        } else if (mediaType === "image" && state.config.imagePreviewEnabled && state.config.uploadPreviewImages !== false && !state.failedMediaPreviews.has(imageKey)) {
          items.push({type: "image", href: discordPreview, linkHref: href, previewKey: imageKey});
        }
      } else if (state.config.imagePreviewEnabled && state.config.uploadPreviewImages !== false && drivePreview) {
        if (!state.failedMediaPreviews.has(imageKey)) items.push({type: "image", href: drivePreview, linkHref: href, previewKey: imageKey});
      } else if (state.config.imagePreviewEnabled && state.config.uploadPreviewImages !== false && isImageUrl(url)) {
        if (!state.failedMediaPreviews.has(imageKey)) items.push({type: "image", href, previewKey: imageKey});
      } else if (state.config.uploadPreviewVideos && isVideoUrl(url)) {
        if (!state.failedMediaPreviews.has(videoKey)) items.push({type: "video", href, previewKey: videoKey});
      } else if (state.config.uploadPreviewAudio && isAudioUrl(url)) {
        if (!state.failedMediaPreviews.has(audioKey)) items.push({type: "audio", href, previewKey: audioKey});
      }
    }
    if (!items.length) return "";
    return `<div class="kwc-image-previews">` + items.map(item => {
      if (item.type === "youtube") {
        const id = item.youtubeId;
        const thumb = youtubeThumbUrl(id);
        const key = item.youtubeKey || (String(messageKey || "message") + ":" + id);
        const isShorts = item.youtubeShorts === true;
        const shouldAutoplay = state.config && state.config.youtubeAutoplayOnOpen === true;
        const embed = safeYouTubeEmbedUrl(youtubeEmbedUrl(id, shouldAutoplay, isShorts));
        const shellStyle = youtubeShellStyle(isShorts, maxHeightCss);
        if (!embed) return "";
        const rememberedOpen = state.config.youtubeRememberExpanded !== false && state.youtubeExpanded.has(key);
        const currentlyOpen = state.youtubeOpen.has(key);
        if (state.config.youtubeClickToLoad === false || rememberedOpen || currentlyOpen) {
          return `<div class="kwc-youtube-wrap${isShorts ? " kwc-youtube-shorts-wrap" : ""}" data-youtube-key="${esc(key)}" style="${shellStyle}">
            <iframe class="kwc-youtube-frame" style="position:absolute;inset:0;width:100%;height:100%;border:0;" src="${esc(embed)}" title="${esc(isShorts ? t("media.youtubeShortsTitle", "YouTube Shorts") : t("media.youtubeTitle", "YouTube video"))}" referrerpolicy="strict-origin-when-cross-origin" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
          </div>`;
        }
        return `<button type="button" class="kwc-youtube-card${isShorts ? " kwc-youtube-shorts-card" : ""}" data-youtube-embed="${esc(embed)}" data-youtube-key="${esc(key)}" data-youtube-shorts="${isShorts ? "1" : "0"}" style="${shellStyle}border:0;background-size:cover;background-position:center;cursor:pointer;color:#fff;background-image:url('${esc(thumb)}')">
          <span class="kwc-youtube-play" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);font-size:34px;text-shadow:0 2px 8px rgba(0,0,0,.85);">${kwcFaIcon("play")}</span>
          <span class="kwc-youtube-label" style="position:absolute;left:8px;bottom:8px;font-size:12px;font-weight:700;text-shadow:0 2px 8px rgba(0,0,0,.85);">${esc(isShorts ? t("media.youtubeShorts", "YouTube Shorts") : t("media.youtube", "YouTube"))}</span>
        </button>`;
      }
      if (item.type === "tiktok" || item.type === "x") {
        return socialEmbedHtml(item, maxHeightCss);
      }
      if (item.type === "video") {
        const key = item.previewKey || previewKey("video", item.href);
        const src = safePreviewUrl(item.href);
        const openHref = safeExternalUrl(item.linkHref || item.href) || src;
        if (!src) return "";
        if (mediaClickToLoadEnabled() && !state.mediaOpen.has(key)) {
          return `<div class="kwc-media-card kwc-video-card" data-media-kind="video" data-media-src="${esc(src)}" data-media-open="${esc(openHref)}" data-preview-key="${esc(key)}" style="${maxHeightStyle}">
            <button type="button" class="kwc-media-load kwc-button">${kwcFaIcon("play")}<span>${esc(t("media.loadVideo", "Video"))}</span></button>
          </div>`;
        }
        return `<div class="kwc-video-wrap" data-preview-key="${esc(key)}">
          <video class="kwc-video-preview" src="${esc(src)}" controls playsinline webkit-playsinline preload="metadata" style="${maxHeightStyle}" data-preview-key="${esc(key)}"></video>
        </div>`;
      }
      if (item.type === "audio") {
        const key = item.previewKey || previewKey("audio", item.href);
        const src = safePreviewUrl(item.href);
        const openHref = safeExternalUrl(item.linkHref || item.href) || src;
        if (!src) return "";
        if (mediaClickToLoadEnabled() && !state.mediaOpen.has(key)) {
          return `<div class="kwc-media-card kwc-audio-card" data-media-kind="audio" data-media-src="${esc(src)}" data-media-open="${esc(openHref)}" data-preview-key="${esc(key)}">
            <button type="button" class="kwc-media-load kwc-button">${kwcFaIcon("play")}<span>${esc(t("media.loadAudio", "Audio"))}</span></button>
          </div>`;
        }
        return `<div class="kwc-audio-wrap" data-preview-key="${esc(key)}">
          <audio class="kwc-audio-preview" src="${esc(src)}" controls preload="none" data-preview-key="${esc(key)}"></audio>
        </div>`;
      }
      const src = safePreviewUrl(item.href);
      const linkHref = safeExternalUrl(item.linkHref || item.href) || src;
      if (!src || !linkHref) return "";
      return `<a class="kwc-image-link" href="${esc(linkHref)}" target="_blank" rel="noopener noreferrer">
        <img class="kwc-image-preview" src="${esc(src)}" loading="eager" decoding="async" referrerpolicy="no-referrer" style="${maxHeightStyle}" alt="image preview" data-preview-key="${esc(item.previewKey || previewKey("image", src))}">
      </a>`;
    }).join("") + `</div>`;
  }



  // Shared browser-side policy for recurring operational HTTP/network failures.
  // Functional handling (for example auth expiration or a caller choosing to retry)
  // stays in the caller. This only prevents the same automatic failure from filling
  // DevTools on every retry.
  const operationalIssueState = new Map();
  const OPERATIONAL_ISSUE_REPEAT_MS = 30 * 60 * 1000;

  function operationalApiKey(path) {
    const raw = String(path || "");
    const q = raw.indexOf("?");
    return "api:" + (q >= 0 ? raw.substring(0, q) : raw);
  }

  function reportOperationalIssue(key, fingerprint, message, details) {
    const now = Date.now();
    const k = String(key || "unknown");
    const fp = String(fingerprint || "unknown");
    const previous = operationalIssueState.get(k);
    if (!previous || previous.fingerprint !== fp) {
      operationalIssueState.set(k, {fingerprint: fp, lastLoggedAt: now, suppressed: 0});
      console.warn(message, details || {});
      return;
    }
    if (now - previous.lastLoggedAt < OPERATIONAL_ISSUE_REPEAT_MS) {
      previous.suppressed += 1;
      return;
    }
    const repeated = previous.suppressed + 1;
    previous.suppressed = 0;
    previous.lastLoggedAt = now;
    console.warn(message + ` (same issue repeated ${repeated} time(s); intermediate logs were suppressed)`, details || {});
  }

  function recoverOperationalIssue(key) {
    operationalIssueState.delete(String(key || "unknown"));
  }

  function isOperationalHttpFailure(status) {
    const code = Number(status || 0);
    return code === 429 || code >= 500;
  }

  function api(path, opts = {}) {
    const timeoutMs = Number(opts.timeoutMs || 0);
    const fetchOpts = Object.assign({}, opts);
    if (!("cache" in fetchOpts)) fetchOpts.cache = "no-store";
    delete fetchOpts.timeoutMs;
    const returnHttpErrorResponse = fetchOpts.returnHttpErrorResponse === true;
    delete fetchOpts.returnHttpErrorResponse;
    const isFormData = typeof FormData !== "undefined" && fetchOpts.body instanceof FormData;
    const isUrlEncoded = typeof URLSearchParams !== "undefined" && fetchOpts.body instanceof URLSearchParams;
    fetchOpts.headers = Object.assign((isFormData || isUrlEncoded) ? {} : {"Content-Type": "application/json"}, fetchOpts.headers || {});
    if (state.token && !fetchOpts.headers.Authorization && !fetchOpts.headers.authorization) {
      fetchOpts.headers.Authorization = "Bearer " + state.token;
    }

    let timeoutId = null;
    let controller = null;
    if (timeoutMs > 0 && !fetchOpts.signal && typeof AbortController === "function") {
      controller = new AbortController();
      fetchOpts.signal = controller.signal;
      timeoutId = setTimeout(() => {
        try { controller.abort(); } catch (_) {}
      }, Math.max(1000, Math.min(60000, Math.round(timeoutMs))));
    }

    return fetch(apiBase + path, fetchOpts).then(async r => {
      if (!r.ok) {
        let response = null;
        try { response = await r.clone().json(); } catch (_) {}
        if (isOperationalHttpFailure(r.status)) {
          const errorCode = response && typeof response === "object" ? String(response.error || "") : "";
          reportOperationalIssue(operationalApiKey(path), `http:${r.status}:${errorCode || "unknown"}`,
            "KOKOTO WebChat API request failed", {endpoint: path, status: r.status, error: errorCode || "unknown"});
        }
        if (returnHttpErrorResponse && response && typeof response === "object") {
          if (response.ok === undefined) response.ok = false;
          return response;
        }
        const err = new Error("HTTP " + r.status);
        err.status = r.status;
        err.response = response;
        throw err;
      }
      recoverOperationalIssue(operationalApiKey(path));
      return r.json();
    }).catch(err => {
      if (!err || !err.status) {
        const timedOut = !!(controller && err && err.name === "AbortError");
        const name = String(err && err.name || "NetworkError");
        const message = String(err && err.message || "network failure");
        reportOperationalIssue(operationalApiKey(path), timedOut ? "timeout" : `network:${name}:${message}`,
          "KOKOTO WebChat API network request failed", {endpoint: path, error: timedOut ? "timeout" : message});
      }
      if (isAuthExpiredApiError(err)) {
        handleAuthExpired("api");
      }
      if (controller && err && err.name === "AbortError") {
        err.kwcTimeout = true;
      }
      throw err;
    }).finally(() => {
      if (timeoutId) clearTimeout(timeoutId);
    });
  }

  function t(key, fallback = "") {
    return (state.lang && state.lang[key]) || fallback || key;
  }

  function fmt(key, fallback, vars = {}) {
    let s = t(key, fallback);
    for (const [k, v] of Object.entries(vars)) {
      s = s.replaceAll("{" + k + "}", String(v ?? ""));
    }
    return s;
  }

  function formatBytes(bytes) {
    const n = Number(bytes || 0);
    if (!Number.isFinite(n) || n <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let value = n;
    let idx = 0;
    while (value >= 1024 && idx < units.length - 1) {
      value /= 1024;
      idx++;
    }
    const digits = idx === 0 || value >= 100 ? 0 : value >= 10 ? 1 : 2;
    return value.toFixed(digits).replace(/\.0+$/, "").replace(/(\.\d*[1-9])0+$/, "$1") + " " + units[idx];
  }

  function humanizeErrorCode(code) {
    const value = String(code || "unknown").trim();
    if (!value) return t("error.unknown", "Unknown error");
    return value.replace(/[_-]+/g, " ").replace(/\b\w/g, c => c.toUpperCase());
  }

  function localizedError(code, extra = {}) {
    const raw = String(code || "unknown").trim() || "unknown";
    const safe = raw.replace(/[^A-Za-z0-9_.-]/g, "_");
    const fallback = humanizeErrorCode(raw);
    return fmt("error." + safe, fallback, Object.assign({error: raw}, extra || {}));
  }

  function responseError(res, fallbackCode = "unknown") {
    const code = res && res.error ? String(res.error) : fallbackCode;
    if (code === "content_blocked") {
      const word = String(res && res.matchedWord || "").trim();
      if (word) return fmt("error.content_blockedWord", "Message cannot be sent because it contains a blocked word: {word}", {word});
      return t("error.content_blocked", "This message cannot be sent because it contains blocked content.");
    }
    if (code === "guest_muted" && res && res.reason) {
      return fmt("error.guest_mutedWithReason", "Guest chat is muted: {reason}", {reason: res.reason});
    }
    if (code === "total_size_exceeded" && res && Number(res.maxTotalSize || 0) > 0) {
      const current = Number(res.currentSize || 0);
      const file = Number(res.fileSize || 0);
      const max = Number(res.maxTotalSize || 0);
      return fmt("error.total_size_exceededWithUsage", "Total emoji storage limit exceeded. Current {current}, selected {file}, maximum {max}.", {
        current: formatBytes(current),
        file: formatBytes(file),
        max: formatBytes(max)
      });
    }
    return localizedError(code);
  }

