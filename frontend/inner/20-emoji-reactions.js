// [KWC 유지보수 주석 / KWC maintenance notes]
// 공개/DM/그룹 메시지에서 공통으로 사용하는 메시지 표시 문자열, custom emoji token 해석, reaction 렌더링과 카탈로그 보조 로직을 담당한다.
// This fragment owns shared message-display text, custom-emoji token parsing, reaction rendering, and reaction-catalog helpers used by public, DM, and group chat.
// custom emoji 정규식은 서버의 EMOJI_TOKEN_PATTERN과 의미가 같아야 하며, URL의 https:// 콜론을 emoji 시작으로 오인하지 않는 것이 중요하다.
// The custom-emoji regex must remain semantically aligned with the server EMOJI_TOKEN_PATTERN, especially so the colon in https:// is not treated as an emoji start.
// reaction 갱신은 재생 중인 미디어와 virtual-scroll 위치를 보존하기 위해 가능하면 메시지 전체를 다시 그리지 않고 reaction 부분만 갱신한다.
// Reaction updates avoid rerendering whole messages when possible so active media playback and virtual-scroll position remain stable.

  function alertResponse(key, fallback, res, fallbackCode = "unknown") {
    alert(fmt(key, fallback, {error: responseError(res, fallbackCode)}));
  }

  function displayMessageText(msg) {
    if (!msg) return "";
    if (msg.hidden) return t("message.deleted", "[deleted]");
    const key = String(msg.i18nKey || "").trim();
    if (!key) return String(msg.message || "");
    let vars = {};
    if (msg.i18nArgs) {
      try { vars = JSON.parse(String(msg.i18nArgs)); } catch (_) { vars = {}; }
    }
    // Event messages keep the stable internal type code in i18nArgs so every
    // browser can translate it using that viewer's selected language.
    if (key === "game.chat.created" && vars && vars.type) {
      const rawType = String(vars.type || "");
      vars.type = t("game.type." + rawType, rawType === "firstcome" ? "First come" : rawType === "lottery" ? "Lottery" : rawType);
    }
    return fmt(key, String(msg.message || ""), vars);
  }

  function shouldStripMessageColorCodes(msg) {
    const source = String(msg && msg.source || "").toLowerCase();
    return source === "event" || source === "system" || source === "server";
  }

  function plainDisplayMessageText(msg) {
    const text = displayMessageText(msg);
    return shouldStripMessageColorCodes(msg) ? stripMinecraftColorCodes(text) : text;
  }

  function customEmojiTokenRegex() {
    // Keep this aligned with the server-side EMOJI_TOKEN_PATTERN.
    // Do not start a custom emoji token at the ':' in URL schemes such as https://.
    try {
      return new RegExp("(?<![A-Za-z0-9+.-]):(?:emoji:)?([^:\\r\\n]{1,200}):", "gu");
    } catch (_) {
      return /:(?:emoji:)?([^:\r\n]{1,200}):/g;
    }
  }

  function customEmojiBoundaryRegex(which) {
    const token = ":(?:emoji:)?[^:\\r\\n]{1,200}:";
    try {
      if (which === "start") return new RegExp("^" + token, "u");
      if (which === "end") return new RegExp("(?<![A-Za-z0-9+.-])" + token + "$", "u");
      return new RegExp("(?<![A-Za-z0-9+.-])" + token, "u");
    } catch (_) {
      const prefix = which === "start" ? "^" : "";
      const suffix = which === "end" ? "$" : "";
      return new RegExp(prefix + token + suffix);
    }
  }

  function normalizeEmojiTokenFormat(value) {
    const v = String(value || "").trim().toLowerCase();
    return (v === "legacy" || v === "prefixed" || v === "emoji") ? "legacy" : "short";
  }

  function customEmojiTokenForId(id) {
    id = String(id || "");
    return normalizeEmojiTokenFormat(state.emojiTokenFormat) === "legacy" ? `:emoji:${id}:` : `:${id}:`;
  }

  function putCustomEmojiAlias(map, key, item) {
    key = String(key || "").trim();
    if (!key || !item) return;
    if (!map.has(key)) map.set(key, item);
    const lower = key.toLowerCase();
    if (!map.has(lower)) map.set(lower, item);
  }


  function rebuildCustomEmojiLookups(items) {
    const byId = new Map();
    const byAlias = new Map();
    (Array.isArray(items) ? items : []).forEach(item => {
      if (!item) return;
      const id = String(item.id || "").trim();
      const name = String(item.name || "").trim();
      const label = String(item.label || "").trim();
      const pack = String(item.pack || "").trim();
      if (id) byId.set(id, item);
      putCustomEmojiAlias(byAlias, id, item);
      putCustomEmojiAlias(byAlias, name, item);
      putCustomEmojiAlias(byAlias, label, item);
      if (pack && name) putCustomEmojiAlias(byAlias, pack + "/" + name, item);
      if (pack && label) putCustomEmojiAlias(byAlias, pack + "/" + label, item);
      (Array.isArray(item.aliases) ? item.aliases : []).forEach(alias => putCustomEmojiAlias(byAlias, alias, item));
    });
    state.emojiById = byId;
    state.emojiByAlias = byAlias;
  }

  function customEmojiById(id) {
    if (!state.emojiById || typeof state.emojiById.get !== "function") return null;
    return state.emojiById.get(String(id || "")) || null;
  }

  function customEmojiByToken(token) {
    token = String(token || "").trim();
    if (!token) return null;
    let item = customEmojiById(token);
    if (item) return item;
    const alias = state.emojiByAlias;
    if (alias && typeof alias.get === "function") {
      item = alias.get(token) || alias.get(token.toLowerCase());
      if (item) return item;
    }
    return null;
  }

  const KWC_EMOJI_RECENT_PACK = "__kwc_recent__";
  const KWC_EMOJI_FAVORITES_PACK = "__kwc_favorites__";

  function normalizeCustomEmojiSearchText(value) {
    let text = String(value || "").trim();
    try { text = text.normalize("NFKC"); } catch (_) {}
    return text.toLocaleLowerCase();
  }

  function customEmojiSearchText(item) {
    if (!item) return "";
    const aliases = Array.isArray(item.aliases) ? item.aliases : [];
    return normalizeCustomEmojiSearchText([
      item.id || "", item.name || "", item.label || "", item.pack || "", ...aliases
    ].join(" "));
  }

  function searchCustomEmojiItems(items, query) {
    const needle = normalizeCustomEmojiSearchText(query);
    const arr = Array.isArray(items) ? items : [];
    if (!needle) return arr;
    return arr.filter(item => customEmojiSearchText(item).includes(needle));
  }

  function recentCustomEmojiItems(items) {
    const arr = Array.isArray(items) ? items : [];
    const byId = new Map(arr.map(item => [String(item && item.id || ""), item]));
    return (Array.isArray(state.emojiRecent) ? state.emojiRecent : [])
      .map(id => byId.get(String(id || "")))
      .filter(Boolean);
  }

  function rememberRecentCustomEmoji(id) {
    const value = String(id || "").trim();
    if (!value || !customEmojiById(value)) return;
    state.emojiRecent = [value, ...(Array.isArray(state.emojiRecent) ? state.emojiRecent : []).filter(v => String(v) !== value)].slice(0, 24);
    try { localStorage.setItem("kwc.emojiRecent", JSON.stringify(state.emojiRecent)); } catch (_) {}
  }

  function emojiFavoritesAvailable() {
    if (!state.emojiFavoritesEnabled) return false;
    if (state.emojiFavoritesStorage === "account") return !!state.token && !!state.userUuid;
    return true;
  }

  function favoriteCustomEmojiItems(items) {
    if (!emojiFavoritesAvailable()) return [];
    const arr = Array.isArray(items) ? items : [];
    const byId = new Map(arr.map(item => [String(item && item.id || ""), item]));
    return (Array.isArray(state.emojiFavorites) ? state.emojiFavorites : [])
      .map(id => byId.get(String(id || "")))
      .filter(Boolean);
  }

  function isFavoriteCustomEmoji(id) {
    if (!emojiFavoritesAvailable()) return false;
    const value = String(id || "").trim();
    return !!value && (Array.isArray(state.emojiFavorites) ? state.emojiFavorites : []).some(v => String(v) === value);
  }

  function limitEmojiFavoriteIds(values) {
    const ids = Array.isArray(values) ? values.map(String).filter(Boolean) : [];
    const limit = Math.max(0, Math.floor(Number(state.emojiFavoritesMaxPerAccount) || 0));
    return limit > 0 ? ids.slice(0, limit) : ids;
  }

  async function loadAccountEmojiFavorites() {
    if (!state.emojiFavoritesEnabled || state.emojiFavoritesStorage !== "account" || !state.token || !state.userUuid) {
      if (state.emojiFavoritesStorage === "account") state.emojiFavorites = [];
      state.emojiFavoritesLoaded = state.emojiFavoritesStorage !== "account";
      refreshFavoriteEmojiUi();
      return;
    }
    try {
      const res = await api("/preferences/emoji-favorites", {timeoutMs: 8000});
      state.emojiFavorites = limitEmojiFavoriteIds(res && res.favorites);
      state.emojiFavoritesLoaded = true;
      refreshFavoriteEmojiUi();
    } catch (_) {
      state.emojiFavorites = [];
      state.emojiFavoritesLoaded = false;
      refreshFavoriteEmojiUi();
    }
  }

  async function toggleFavoriteCustomEmoji(id) {
    if (!emojiFavoritesAvailable()) return false;
    const value = String(id || "").trim();
    if (!value || !customEmojiById(value)) return false;
    const current = Array.isArray(state.emojiFavorites) ? state.emojiFavorites.map(String).filter(Boolean) : [];
    const active = current.some(v => v === value);
    if (!active && state.emojiFavoritesMaxPerAccount > 0 && current.length >= state.emojiFavoritesMaxPerAccount) {
      alert(fmt("emoji.favoriteLimitReached", "You can save up to {count} favorite emojis.", {count: state.emojiFavoritesMaxPerAccount}));
      return false;
    }
    if (state.emojiFavoritesStorage === "account") {
      try {
        const res = await api("/preferences/emoji-favorites", {
          method: "POST",
          body: JSON.stringify({emojiId: value, active: !active}),
          timeoutMs: 8000,
          returnHttpErrorResponse: true
        });
        if (!res || res.ok === false) {
          if (res && res.error === "favorite_limit_reached") alert(fmt("emoji.favoriteLimitReached", "You can save up to {count} favorite emojis.", {count: state.emojiFavoritesMaxPerAccount}));
          else alert(t("emoji.favoriteSaveFailed", "Could not save emoji favorites."));
          return false;
        }
        state.emojiFavorites = limitEmojiFavoriteIds(res.favorites);
        state.emojiFavoritesLoaded = true;
        return !active;
      } catch (_) {
        alert(t("emoji.favoriteSaveFailed", "Could not save emoji favorites."));
        return false;
      }
    }
    state.emojiFavorites = active ? current.filter(v => v !== value) : limitEmojiFavoriteIds([value, ...current.filter(v => v !== value)]);
    try { localStorage.setItem("kwc.emojiFavorites", JSON.stringify(state.emojiFavorites)); } catch (_) {}
    return !active;
  }

  function emojiRenderSizePx() {
    return Math.max(16, Math.min(1024, Number(state.emojiRenderSizePx || (state.config && state.config.emojiRenderSizePx) || 32)));
  }

  function customEmojiTooltipText(item) {
    if (!item) return "";
    // Prefer the original display label exposed by the emoji catalog. This is
    // what users expect to see when hovering ImageEmojis/custom emojis.
    return String(item.label || item.name || item.id || "emoji");
  }

  function customEmojiImgHtml(item) {
    if (!item || !item.url) return "";
    const size = emojiRenderSizePx();
    const title = customEmojiTooltipText(item);
    // Do not use :token: as img alt text. Browsers visibly paint alt text while
    // an image is still being fetched/decoded, which is exactly the token -> icon
    // delay seen with registered emojis. The fixed box + aria-label preserves
    // layout/accessibility without leaking the transport token onto the screen.
    return `<img class="kwc-custom-emoji" src="${esc(item.url)}" alt="" role="img" title="${esc(title)}" aria-label="${esc(title)}" data-emoji-title="${esc(title)}" loading="eager" decoding="async" draggable="false" width="${size}" height="${size}" style="width:${size}px;height:${size}px;">`;
  }

  function customEmojiRetryUrl(rawUrl, attempt) {
    const original = String(rawUrl || "").trim();
    if (!original || Number(attempt || 0) < 1) return original;
    try {
      const url = new URL(original, window.location.href);
      if (url.protocol === "http:" || url.protocol === "https:") {
        // Cache-bust every retry, including the first one. Some reverse proxies
        // and browser caches can retain a transient failed image response long
        // enough for a same-URL retry after page refresh to fail again.
        url.searchParams.set("_kwc_emoji_retry", String(Date.now()) + "-" + String(attempt));
        return url.href;
      }
    } catch (_) {}
    return original;
  }

  function installCustomEmojiImageRecovery(root) {
    if (!root || !root.querySelectorAll) return;
    root.querySelectorAll("img.kwc-custom-emoji").forEach(img => {
      if (!img || img.dataset.kwcEmojiRecoveryInstalled === "1") return;
      img.dataset.kwcEmojiRecoveryInstalled = "1";
      const originalSrc = String(img.getAttribute("src") || "").trim();
      if (!originalSrc) return;
      let attempts = 0;
      let retryTimer = null;
      let retryScheduled = false;
      const retryDelays = [120, 420, 1200, 3000];

      const retry = () => {
        if (retryScheduled || attempts >= retryDelays.length || !img.isConnected) return;
        retryScheduled = true;
        const nextAttempt = attempts + 1;
        const delay = retryDelays[Math.min(retryDelays.length - 1, attempts)];
        retryTimer = setTimeout(() => {
          retryTimer = null;
          retryScheduled = false;
          if (!img.isConnected || attempts >= retryDelays.length) return;
          attempts = nextAttempt;
          const retrySrc = customEmojiRetryUrl(originalSrc, attempts);
          // Clearing first forces a new element-level request. Every retry gets a
          // cache-buster, preventing a transient refresh-time failure from being
          // reused by the browser or an intermediate map/reverse-proxy cache.
          img.removeAttribute("src");
          requestAnimationFrame(() => {
            if (img.isConnected) img.setAttribute("src", retrySrc);
          });
        }, delay);
      };

      img.addEventListener("error", retry);
      img.addEventListener("load", () => {
        if (Number(img.naturalWidth || 0) <= 0) {
          retry();
          return;
        }
        if (retryTimer) clearTimeout(retryTimer);
        retryTimer = null;
        retryScheduled = false;
      });
      // A cached failed request can already be complete by the time innerHTML
      // returns and listeners are attached. Detect that state explicitly.
      setTimeout(() => {
        if (img.isConnected && img.complete && Number(img.naturalWidth || 0) <= 0) retry();
      }, 0);
    });
  }

  function customEmojiOnlyElement(el) {
    if (!el || !el.querySelectorAll) return false;
    const images = el.querySelectorAll("img.kwc-custom-emoji");
    if (!images.length) return false;
    const clone = el.cloneNode(true);
    clone.querySelectorAll("img.kwc-custom-emoji").forEach(node => node.remove());
    return String(clone.textContent || "").trim() === "";
  }

  function updateCustomEmojiOnlyClass(el) {
    if (!el || !el.classList) return false;
    const only = customEmojiOnlyElement(el);
    el.classList.toggle("kwc-emoji-only", only);
    return only;
  }


  function emojiPickerSizePx() {
    return Math.max(24, Math.min(1024, Number(state.emojiPickerSizePx || (state.config && state.config.emojiPickerSizePx) || 44)));
  }

  function emojiPanelHeightPx() {
    return clampEmojiPanelHeightPx(Number(state.emojiPanelHeightPx || 180) || 180);
  }

  function clampEmojiPanelHeightPx(px, panel = null) {
    const min = emojiPanelMinHeightPx(panel);
    const max = emojiPanelMaxHeightPx();
    return Math.max(min, Math.min(max, Math.round(Number(px) || 180)));
  }

  function emojiPanelFallbackItemHeightPx() {
    // Tile height = icon + item vertical padding + icon/name gap + one label line + border.
    // Keep this tied to picker-size-px so the one-row minimum follows icon scaling.
    return Math.ceil(emojiPickerSizePx() + 28);
  }

  function emojiPanelMinHeightPx(panel = null) {
    panel = panel || document.getElementById("kwc-emoji-panel");
    const fallbackItemHeight = emojiPanelFallbackItemHeightPx();
    const fallbackTabsHeight = Array.isArray(state.emojiItems) && state.emojiItems.length ? 30 : 0;
    const fallbackSearchHeight = 0;
    const fallback = 16 + fallbackTabsHeight + fallbackSearchHeight + fallbackItemHeight;
    if (!panel || panel.classList.contains("kwc-hidden")) return Math.max(56, Math.ceil(fallback));

    const panelStyle = getComputedStyle(panel);
    const padTop = parseFloat(panelStyle.paddingTop || "0") || 0;
    const padBottom = parseFloat(panelStyle.paddingBottom || "0") || 0;
    const borderTop = parseFloat(panelStyle.borderTopWidth || "0") || 0;
    const borderBottom = parseFloat(panelStyle.borderBottomWidth || "0") || 0;
    const toolbar = panel.querySelector(".kwc-emoji-toolbar");
    const searchRow = panel.querySelector(".kwc-emoji-search-row:not(.kwc-hidden)");
    const item = panel.querySelector(".kwc-emoji-item");

    let tabsHeight = 0;
    if (toolbar) {
      const toolbarStyle = getComputedStyle(toolbar);
      tabsHeight = Number(toolbar.getBoundingClientRect().height || toolbar.offsetHeight || fallbackTabsHeight);
      tabsHeight += parseFloat(toolbarStyle.marginTop || "0") || 0;
      tabsHeight += parseFloat(toolbarStyle.marginBottom || "0") || 0;
    }
    if (searchRow) {
      const searchStyle = getComputedStyle(searchRow);
      tabsHeight += Number(searchRow.getBoundingClientRect().height || searchRow.offsetHeight || 0);
      tabsHeight += parseFloat(searchStyle.marginTop || "0") || 0;
      tabsHeight += parseFloat(searchStyle.marginBottom || "0") || 0;
    }

    const itemHeight = item ? Number(item.getBoundingClientRect().height || item.offsetHeight || 0) : 0;
    // This is the actual one-row minimum: panel chrome + fixed pack row + one complete emoji tile.
    // Do not use the selected pack's total content height here; multi-row packs must be able to shrink
    // to the same one-row minimum as one-row packs.
    return Math.max(56, Math.ceil(borderTop + padTop + tabsHeight + (itemHeight || fallbackItemHeight) + padBottom + borderBottom));
  }

  function emojiPanelMaxHeightPx() {
    const root = document.getElementById("kwc-root");
    const panelHeight = root ? Number(root.clientHeight || 0) : 0;
    // Keep enough room for header, messages, input row, and panel padding.
    // On small windows this prevents the emoji picker from swallowing the chat list.
    const min = emojiPanelMinHeightPx();
    const viewportBound = panelHeight > 0 ? Math.max(min, panelHeight - 210) : 420;
    return Math.max(min, Math.min(420, Math.floor(viewportBound)));
  }

  function emojiScrollElement(panel = null) {
    panel = panel || document.getElementById("kwc-emoji-panel");
    return panel ? (panel.querySelector(".kwc-emoji-scroll") || panel) : null;
  }

  function emojiGridRowStepPx(panel = null) {
    panel = panel || document.getElementById("kwc-emoji-panel");
    const item = panel ? panel.querySelector(".kwc-emoji-item") : null;
    const grid = panel ? panel.querySelector(".kwc-emoji-grid") : null;
    const itemHeight = item ? Number(item.getBoundingClientRect().height || item.offsetHeight || 0) : 0;
    const gridStyle = grid ? getComputedStyle(grid) : null;
    const rowGap = gridStyle ? parseFloat(gridStyle.rowGap || gridStyle.gap || "0") || 0 : 0;
    const fallback = emojiPanelFallbackItemHeightPx();
    return Math.max(28, Math.round((itemHeight || fallback) + rowGap));
  }

  function snapEmojiPanelHeightPx(px, panel = null) {
    panel = panel || document.getElementById("kwc-emoji-panel");
    const min = emojiPanelMinHeightPx(panel);
    const max = emojiPanelMaxHeightPx();
    let height = Math.max(min, Math.min(max, Math.round(Number(px) || 180)));
    const scroll = emojiScrollElement(panel);
    const grid = panel ? panel.querySelector(".kwc-emoji-grid") : null;
    if (!grid || !scroll || panel.classList.contains("kwc-hidden")) return height;
    const panelStyle = getComputedStyle(panel);
    const padTop = parseFloat(panelStyle.paddingTop || "0") || 0;
    const padBottom = parseFloat(panelStyle.paddingBottom || "0") || 0;
    const scrollTopOffset = Number(scroll.offsetTop || 0);
    const fixedPart = Math.max(0, scrollTopOffset + padBottom);
    const row = emojiGridRowStepPx(panel);
    if (row > 0 && height > fixedPart + row) {
      const available = Math.max(row, height - fixedPart);
      const rows = Math.max(1, Math.round(available / row));
      height = Math.round(fixedPart + rows * row);
      if (height > max) {
        const maxRows = Math.max(1, Math.floor(Math.max(row, max - fixedPart) / row));
        height = Math.round(fixedPart + maxRows * row);
      }
      height = Math.max(min, Math.min(max, height));
    }
    return height;
  }

  function snapEmojiPanelScrollTop(panel = null) {
    panel = panel || document.getElementById("kwc-emoji-panel");
    if (!panel || panel.classList.contains("kwc-hidden")) return;
    const scroll = emojiScrollElement(panel);
    const grid = panel.querySelector(".kwc-emoji-grid");
    if (!grid || !scroll) return;
    const row = emojiGridRowStepPx(panel);
    if (!Number.isFinite(row) || row <= 0) return;
    const current = Number(scroll.scrollTop || 0);
    const maxTop = Math.max(0, Number(scroll.scrollHeight || 0) - Number(scroll.clientHeight || 0));
    const target = Math.max(0, Math.min(maxTop, Math.round(Math.round(current / row) * row)));
    if (Math.abs(target - current) > 0.5) scroll.scrollTop = target;
  }

  function setEmojiPanelHeight(px, persist = true, options = {}) {
    const panel = document.getElementById("kwc-emoji-panel");
    const height = options && options.snap === false
      ? clampEmojiPanelHeightPx(px, panel)
      : snapEmojiPanelHeightPx(px, panel);
    state.emojiPanelHeightPx = height;
    const root = document.getElementById("kwc-root");
    if (root) {
      root.style.setProperty("--kwc-emoji-panel-height", height + "px");
      root.style.setProperty("--kwc-emoji-panel-min-height", emojiPanelMinHeightPx(panel) + "px");
    }
    if (persist) {
      try { localStorage.setItem("kwc.emojiPanelHeightPx", String(height)); } catch (_) {}
    }
    if (panel) {
      const minHeight = emojiPanelMinHeightPx(panel);
      panel.style.maxHeight = height + "px";
      panel.style.minHeight = minHeight + "px";
      // Keep the panel as a max-height box during normal use so one-row packs do not
      // leave a large empty area. While dragging, or when the picker is clamped to
      // its minimum, use an explicit height so multi-row packs can shrink to the
      // same one-row minimum instead of being held open by their natural content.
      if ((state.emojiPanelResizeStart && (!options || options.fixed !== false)) || height <= minHeight + 1 || (options && options.fixed === true)) {
        panel.style.height = height + "px";
      } else {
        panel.style.removeProperty("height");
      }
      if (!options || options.snapScroll !== false) snapEmojiPanelScrollTop(panel);
    }
    if (state.emojiSearchOpen) requestAnimationFrame(() => positionEmojiSearchOverlay("public"));
    return height;
  }

  function updateEmojiResizeHandleVisibility() {
    const handle = document.getElementById("kwc-emoji-resize");
    if (!handle) return;
    const visible = !!(state.emojiPanelOpen && canUseCustomEmoji() && !state.minimized && !guestChatHidden());
    handle.classList.toggle("kwc-hidden", !visible);
  }

  function installEmojiPanelResize(root) {
    const handle = document.getElementById("kwc-emoji-resize");
    const panel = document.getElementById("kwc-emoji-panel");
    if (!handle || !panel || handle.dataset.kwcInstalled === "1") return;
    handle.dataset.kwcInstalled = "1";
    setEmojiPanelHeight(emojiPanelHeightPx(), false);

    const pointY = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return Number(src.clientY) || 0;
    };

    const begin = event => {
      if (!state.emojiPanelOpen) return;
      event.preventDefault();
      event.stopPropagation();
      markNonScrollUiAction();
      state.emojiPanelResizeStart = {
        y: pointY(event),
        height: Number(panel.getBoundingClientRect().height || emojiPanelHeightPx()),
        currentHeight: Number(panel.getBoundingClientRect().height || emojiPanelHeightPx())
      };
      document.body.classList.add("kwc-emoji-resizing");
      try { handle.setPointerCapture && event.pointerId != null && handle.setPointerCapture(event.pointerId); } catch (_) {}
    };

    const move = event => {
      const start = state.emojiPanelResizeStart;
      if (!start) return;
      event.preventDefault();
      event.stopPropagation();
      const delta = start.y - pointY(event);
      start.currentHeight = setEmojiPanelHeight(start.height + delta, false, {snap: false, snapScroll: false});
    };

    const end = event => {
      const start = state.emojiPanelResizeStart;
      if (!start) return;
      event.preventDefault();
      event.stopPropagation();
      setEmojiPanelHeight(start.currentHeight || emojiPanelHeightPx(), true, {snap: false, snapScroll: true});
      state.emojiPanelResizeStart = null;
      document.body.classList.remove("kwc-emoji-resizing");
    };

    handle.addEventListener("pointerdown", begin, {passive: false});
    document.addEventListener("pointermove", move, {passive: false});
    document.addEventListener("pointerup", end, {passive: false});
    document.addEventListener("pointercancel", end, {passive: false});
    handle.addEventListener("touchstart", begin, {passive: false});
    document.addEventListener("touchmove", move, {passive: false});
    document.addEventListener("touchend", end, {passive: false});
    document.addEventListener("touchcancel", end, {passive: false});
  }

  function emojiPanelRowScrollTargets(panel = null) {
    panel = panel || document.getElementById("kwc-emoji-panel");
    const scroll = emojiScrollElement(panel);
    const grid = panel ? panel.querySelector(".kwc-emoji-grid") : null;
    if (!panel || !scroll || !grid) return [];

    const scrollRect = scroll.getBoundingClientRect();
    const rawRows = [];
    const seen = [];
    grid.querySelectorAll(".kwc-emoji-item").forEach(item => {
      const rect = item.getBoundingClientRect();
      const absoluteTop = Math.max(0, Math.round((rect.top - scrollRect.top) + Number(scroll.scrollTop || 0)));
      if (!seen.some(v => Math.abs(v - absoluteTop) <= 2)) {
        seen.push(absoluteTop);
        rawRows.push(absoluteTop);
      }
    });
    rawRows.sort((a, b) => a - b);
    if (!rawRows.length) return [];

    // The grid may sit below pack tabs/header inside the scroll area. Normalize
    // the first emoji row to 0 so the first wheel tick moves exactly one emoji
    // row instead of jumping by the header height plus one row.
    const first = rawRows[0];
    return rawRows.map(v => Math.max(0, Math.round(v - first)));
  }

  function emojiPanelNextRowScrollTop(panel, direction) {
    const scroll = emojiScrollElement(panel);
    if (!scroll) return null;
    const maxTop = Math.max(0, Number(scroll.scrollHeight || 0) - Number(scroll.clientHeight || 0));
    const current = Number(scroll.scrollTop || 0);
    const rows = emojiPanelRowScrollTargets(panel).filter(v => v <= maxTop + 2);
    if (!rows.length) {
      const row = emojiGridRowStepPx(panel);
      if (!Number.isFinite(row) || row <= 0) return null;
      const base = Math.round(current / row) * row;
      return Math.max(0, Math.min(maxTop, Math.round(base + (direction > 0 ? row : -row))));
    }

    if (direction > 0) {
      const next = rows.find(v => v > current + 2);
      return Math.max(0, Math.min(maxTop, next == null ? maxTop : next));
    }

    for (let i = rows.length - 1; i >= 0; i--) {
      if (rows[i] < current - 2) return Math.max(0, Math.min(maxTop, rows[i]));
    }
    return 0;
  }

  function installEmojiPanelWheelStep(panel) {
    panel = panel || document.getElementById("kwc-emoji-panel");
    const scroll = emojiScrollElement(panel);
    if (!panel || !scroll || scroll.dataset.kwcWheelStepInstalled === "1") return;
    scroll.dataset.kwcWheelStepInstalled = "1";
    scroll.addEventListener("wheel", event => {
      const isDmPanel = panel && panel.id === "kwc-dm-emoji-panel";
      const isGroupPanel = panel && panel.id === "kwc-group-emoji-panel";
      const open = isDmPanel ? state.dmEmojiPanelOpen : (isGroupPanel ? state.groupEmojiPanelOpen : state.emojiPanelOpen);
      if (!open || panel.classList.contains("kwc-hidden")) return;
      if (event.ctrlKey || event.metaKey || event.shiftKey) return;
      const deltaY = Number(event.deltaY || 0);
      if (Math.abs(deltaY) < 1) return;
      const target = emojiPanelNextRowScrollTop(panel, deltaY > 0 ? 1 : -1);
      if (target == null) return;
      event.preventDefault();
      event.stopPropagation();
      scroll.scrollTo({top: target, behavior: "auto"});
    }, {passive: false});
  }

  function applyEmojiPickerSize() {
    const root = document.getElementById("kwc-root");
    if (!root) return;
    root.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
    root.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
    root.style.setProperty("--kwc-emoji-panel-height", emojiPanelHeightPx() + "px");
    root.style.setProperty("--kwc-emoji-panel-min-height", emojiPanelMinHeightPx() + "px");
    syncDirectMessageModalSettings();
  }

  function syncDirectMessageModalSettings() {
    const wrap = document.querySelector(".kwc-dm-modal-backdrop:not(.kwc-group-modal-backdrop)");
    if (wrap) {
      try { applyDetachedModalTheme(wrap); } catch (_) {}
      wrap.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
      wrap.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
      wrap.style.setProperty("--kwc-emoji-panel-height", emojiPanelHeightPx() + "px");
      const panel = document.getElementById("kwc-dm-emoji-panel");
      const minHeight = emojiPanelMinHeightPx(panel);
      wrap.style.setProperty("--kwc-emoji-panel-min-height", minHeight + "px");
    }
    syncGroupChatModalSettings();
  }

  function syncGroupChatModalSettings() {
    const wrap = document.querySelector(".kwc-group-modal-backdrop");
    if (!wrap) return;
    try { applyDetachedModalTheme(wrap); } catch (_) {}
    wrap.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-panel-height", emojiPanelHeightPx() + "px");
    const panel = document.getElementById("kwc-group-emoji-panel");
    const minHeight = emojiPanelMinHeightPx(panel);
    wrap.style.setProperty("--kwc-emoji-panel-min-height", minHeight + "px");
  }

  function setElementVisible(el, visible) {
    if (!el) return;
    el.classList.toggle("kwc-hidden", !visible);
    el.hidden = !visible;
    el.style.display = visible ? "" : "none";
  }

  function normalizeSingleLineComposer(input) {
    if (!input) return;
    const value = String(input.value ?? "");
    if (!/[\r\n]/.test(value)) return;
    const start = Number.isFinite(input.selectionStart) ? input.selectionStart : value.length;
    const end = Number.isFinite(input.selectionEnd) ? input.selectionEnd : start;
    const normalize = text => String(text || "").replace(/\r\n?|\n/g, " ");
    const next = normalize(value);
    const nextStart = normalize(value.slice(0, start)).length;
    const nextEnd = normalize(value.slice(0, end)).length;
    input.value = next;
    try { input.setSelectionRange(nextStart, nextEnd); } catch (_) {}
  }

  function updateDirectMessageComposeControls() {
    if (!state.dmModalOpen) return;
    syncDirectMessageModalSettings();
    const auditMode = state.dmAuditMode === true;
    const compose = document.querySelector(".kwc-dm-compose");
    const newButton = document.getElementById("kwc-dm-new");
    setElementVisible(compose, !auditMode);
    setElementVisible(newButton, !auditMode);
    if (auditMode) closeDirectMessageEmojiPanel();
    const emojiVisible = !auditMode && canUseCustomEmoji();
    const emojiBtn = document.getElementById("kwc-dm-emoji");
    setElementVisible(emojiBtn, emojiVisible);
    if (emojiBtn) emojiBtn.title = t("button.emoji", "Emoji");
    if (!emojiVisible) {
      closeDirectMessageEmojiPanel();
    } else if (state.dmEmojiPanelOpen) {
      renderDirectMessageEmojiPanel();
    }
    updateDirectMessageEmojiResizeHandleVisibility();

    const uploadVisible = !auditMode && canUpload();
    const uploadBtn = document.getElementById("kwc-dm-upload");
    const fileInput = document.getElementById("kwc-dm-file");
    setElementVisible(uploadBtn, uploadVisible);
    if (uploadBtn) uploadBtn.title = t("button.upload", "Attach");
    if (fileInput) {
      fileInput.disabled = !uploadVisible || !!state.uploadActive;
      fileInput.accept = uploadAcceptList();
    }

    const input = document.getElementById("kwc-dm-input");
    if (input) {
      input.disabled = auditMode;
      if (state.directMessageMaxMessageLength > 0) input.maxLength = state.directMessageMaxMessageLength;
      else input.removeAttribute("maxlength");
    }
  }

  function updateGroupChatComposeControls() {
    if (!state.groupModalOpen) return;
    syncGroupChatModalSettings();
    const auditMode = state.groupAuditMode === true;
    const compose = document.querySelector(".kwc-group-modal .kwc-dm-compose");
    const createButton = document.getElementById("kwc-group-create");
    setElementVisible(compose, !auditMode);
    setElementVisible(createButton, !auditMode);
    if (auditMode) {
      closeGroupChatEmojiPanel();
      closeGroupPlayerSearch();
    }
    const emojiVisible = !auditMode && canUseCustomEmoji();
    const emojiBtn = document.getElementById("kwc-group-emoji");
    setElementVisible(emojiBtn, emojiVisible);
    if (emojiBtn) emojiBtn.title = t("button.emoji", "Emoji");
    if (!emojiVisible) {
      closeGroupChatEmojiPanel();
    } else if (state.groupEmojiPanelOpen) {
      renderGroupChatEmojiPanel();
    }
    updateGroupChatEmojiResizeHandleVisibility();

    const uploadVisible = !auditMode && canUpload();
    const uploadBtn = document.getElementById("kwc-group-upload");
    const fileInput = document.getElementById("kwc-group-file");
    setElementVisible(uploadBtn, uploadVisible);
    if (uploadBtn) uploadBtn.title = t("button.upload", "Attach");
    if (fileInput) {
      fileInput.disabled = !uploadVisible || !!state.uploadActive;
      fileInput.accept = uploadAcceptList();
    }

    const input = document.getElementById("kwc-group-input");
    if (input) {
      if (state.groupChatMaxMessageLength > 0) input.maxLength = state.groupChatMaxMessageLength;
      else input.removeAttribute("maxlength");
      input.disabled = auditMode || !state.groupActiveRoomId || !state.groupChatAllowWebSend;
    }
  }

  function renderCustomEmojiTokens(text, allowLinks = true, readableUrlsWithoutLinks = false) {
    text = String(text ?? "");
    const linkEnabled = allowLinks && (!state.config || state.config.linkifyUrls !== false);
    const renderPlain = value => readableUrlsWithoutLinks ? displayUrlsAsText(value) : esc(value);
    const renderNonUrl = value => {
      value = String(value ?? "");
      if (!state.emojiEnabled || !state.emojiById || state.emojiById.size === 0) return renderPlain(value);
      const re = customEmojiTokenRegex();
      let out = "";
      let last = 0;
      let match;
      while ((match = re.exec(value)) !== null) {
        out += renderPlain(value.slice(last, match.index));
        const item = customEmojiByToken(match[1]);
        out += item ? customEmojiImgHtml(item) : esc(match[0]);
        last = match.index + match[0].length;
      }
      out += renderPlain(value.slice(last));
      return out;
    };

    // Protect complete URL spans before looking for :emoji: tokens. A registered
    // token-shaped path fragment inside a URL must remain part of the URL instead
    // of turning into an image. Reply previews reuse this path with anchors disabled.
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
      out += renderNonUrl(text.slice(last, match.index));
      if (linkEnabled) {
        const href = safeExternalUrl(url);
        out += href
          ? `<a class="kwc-link" href="${esc(href)}" target="_blank" rel="noopener noreferrer">${esc(displayLinkText(url))}</a>`
          : esc(raw);
      } else {
        out += readableUrlsWithoutLinks ? esc(displayLinkText(url)) : esc(url);
      }
      out += esc(trailing);
      last = match.index + raw.length;
    }
    out += renderNonUrl(text.slice(last));
    return out;
  }

  function emojiOnlyTokenLine(value) {
    const text = String(value ?? "");
    if (!text.trim() || !state.emojiEnabled || !state.emojiById || state.emojiById.size === 0) return false;
    const re = customEmojiTokenRegex();
    let last = 0;
    let found = false;
    let match;
    while ((match = re.exec(text)) !== null) {
      if (text.slice(last, match.index).trim()) return false;
      if (!customEmojiByToken(match[1])) return false;
      found = true;
      last = match.index + match[0].length;
    }
    return found && !text.slice(last).trim();
  }

  function renderMessageTokenLines(value, options = {}) {
    const text = String(value ?? "").replace(/\r\n?/g, "\n");
    const lines = text.split("\n");
    const allowLinks = options.allowLinks !== false;
    const readableUrlsWithoutLinks = options.readableUrlsWithoutLinks === true;
    if (lines.length === 1) return renderCustomEmojiTokens(text, allowLinks, readableUrlsWithoutLinks);
    return lines.map(line => {
      const empty = line.length === 0;
      const emojiOnly = !empty && emojiOnlyTokenLine(line);
      const classes = ["kwc-token-line"];
      if (empty) classes.push("kwc-token-line-empty");
      if (emojiOnly) classes.push("kwc-token-line-emoji-only");
      const html = empty ? "&#8203;" : renderCustomEmojiTokens(line, allowLinks, readableUrlsWithoutLinks);
      return `<span class="${classes.join(" ")}">${html}</span>`;
    }).join("");
  }

  function messageTextHtml(msg) {
    return renderMessageTokenLines(plainDisplayMessageText(msg));
  }


  function reactionValueHtml(value) {
    const raw = String(value || "");
    if (raw.startsWith(":") && raw.endsWith(":") && raw.length > 2) {
      let token = raw.slice(1, -1);
      if (token.startsWith("emoji:")) token = token.slice("emoji:".length);
      const item = customEmojiByToken(token);
      if (item && item.url) {
        const label = customEmojiTooltipText(item);
        return `<img class="kwc-reaction-emoji" src="${esc(item.url)}" alt="" role="img" aria-label="${esc(label)}" loading="eager" decoding="async" draggable="false">`;
      }
    }
    return esc(raw);
  }

  function reactionValueLabel(value) {
    const raw = String(value || "");
    if (raw.startsWith(":") && raw.endsWith(":") && raw.length > 2) {
      let token = raw.slice(1, -1);
      if (token.startsWith("emoji:")) token = token.slice("emoji:".length);
      const item = customEmojiByToken(token);
      if (item) return customEmojiTooltipText(item) || item.label || item.name || item.id || raw;
    }
    return raw;
  }

  function reactionActorListHtml(reaction) {
    if (state.reactionCatalog && state.reactionCatalog.showActorList === false) return "";
    const legacyActors = Array.isArray(reaction && reaction.actors) ? reaction.actors : [];
    const actorIdentities = Array.isArray(reaction && reaction.actorIdentities) ? reaction.actorIdentities : [];
    const identities = actorIdentities.length
      ? actorIdentities
      : legacyActors.map(value => ({displayName: String(value || ""), username: ""}));
    if (!identities.length) return "";
    const rows = identities.map(identity => {
      const display = plainMinecraftName(String(identity && (identity.displayName || identity.label || identity.username) || "")).trim() || t("sender.unknown", "Unknown");
      const real = plainMinecraftName(String(identity && identity.username || "")).trim();
      const hasReal = !!real && real.toLowerCase() !== display.toLowerCase();
      const shown = hasReal ? preferredSenderText(display, real) : display;
      const attrs = hasReal
        ? ` data-kwc-identity-toggle="reaction" data-display-sender="${esc(display)}" data-real-sender="${esc(real)}" data-source="game" data-showing-real="${state.senderIdentityMode === "real" ? "1" : "0"}" role="button" tabindex="0" title="${esc(state.senderIdentityMode === "real" ? senderDisplayTitle(display) : senderOriginalTitle(real))}" aria-label="${esc(state.senderIdentityMode === "real" ? senderDisplayTitle(display) : senderOriginalTitle(real))}"`
        : "";
      return `<span class="kwc-reaction-actor-row${hasReal ? " kwc-reaction-actor-toggle" : ""}"${attrs}>${esc(shown)}</span>`;
    }).join("");
    const header = `${reactionValueLabel(reaction && reaction.value)} · ${Number(reaction && reaction.count || identities.length || 0)}`;
    return `<span class="kwc-reaction-tooltip" role="tooltip"><span class="kwc-reaction-tooltip-title">${esc(header)}</span><span class="kwc-reaction-actors">${rows}</span></span>`;
  }

  function reactionBarHtml(msg) {
    if (!msg || msg.hidden) return "";
    const featureEnabled = !state.reactionCatalog || state.reactionCatalog.enabled !== false;
    const reactions = Array.isArray(msg.reactions) ? msg.reactions : [];
    const pills = reactions.filter(r => r && Number(r.count || 0) > 0).map(r => {
      const value = String(r.value || "");
      const label = reactionValueLabel(value);
      const mine = !!r.mine;
      const actionLabel = mine ? t("reaction.remove", "Remove reaction") : t("reaction.addSame", "Add the same reaction");
      const title = mine ? t("reaction.remove", "Remove reaction") : t("reaction.addSame", "Add the same reaction");
      return `<span class="kwc-reaction-wrap"><button type="button" class="kwc-reaction-pill${mine ? " kwc-active" : ""}" data-reaction-value="${esc(value)}" aria-label="${esc(actionLabel)}" title="${esc(title)}">${reactionValueHtml(value)}<span class="kwc-reaction-count">${esc(String(r.count || 0))}</span></button>${reactionActorListHtml(r)}</span>`;
    }).join("");
    if (!pills) return "";
    const classes = `kwc-reactions kwc-has-reactions${featureEnabled ? "" : " kwc-reactions-disabled"}`;
    return `<div class="${classes}" data-reaction-message="${esc(msg.id || "")}">${pills}</div>`;
  }

  function updateReactionBarElement(el, msg) {
    if (!el || !msg) return;
    let bar = el.querySelector(":scope > .kwc-reactions");
    const holder = document.createElement("div");
    holder.innerHTML = reactionBarHtml(msg);
    const next = holder.firstElementChild;
    if (!next) {
      if (bar) bar.remove();
      return;
    }
    if (bar) bar.replaceWith(next);
    else el.appendChild(next);
    installReactionHandlers(el, msg);
    installSenderIdentityToggle(next);
    installCustomEmojiImageRecovery(next);
  }

  function refreshVisibleReactionBars() {
    const box = document.getElementById("kwc-messages");
    if (box) {
      box.querySelectorAll(":scope > .kwc-msg").forEach(el => {
        const msg = messageById(String(el.dataset.id || ""));
        if (msg) {
          updateReactionBarElement(el, msg);
          syncMessageElementActions(el, msg);
        }
      });
    }
    const dmBox = document.getElementById("kwc-dm-messages");
    if (dmBox) (state.dmMessages || []).forEach(msg => {
      const el = dmBox.querySelector(`[data-dm-message-id="${cssEscape(String(msg && msg.id || ""))}"]`);
      if (el) syncPrivateMessageElement(el, msg, "dm");
    });
    const groupBox = document.getElementById("kwc-group-messages");
    if (groupBox) (state.groupMessages || []).forEach(msg => {
      const el = groupBox.querySelector(`[data-group-message-id="${cssEscape(String(msg && msg.id || ""))}"]`);
      if (el) syncPrivateMessageElement(el, msg, "group");
    });
  }

  function reactionContextType(msg) {
    const type = String(msg && msg._kwcReactionContextType || "").toLowerCase();
    return type === "dm" || type === "group" ? type : "public";
  }

  function reactionContextId(msg) {
    const type = reactionContextType(msg);
    if (type === "dm") return String(msg && (msg._kwcReactionContextId || msg.threadId) || "");
    if (type === "group") return String(msg && (msg._kwcReactionContextId || msg.roomId) || "");
    return "";
  }

  function reactionPendingKey(messageId, value, contextType = "public", contextId = "") {
    return String(contextType || "public") + "\u0000" + String(contextId || "") + "\u0000" + String(messageId || "") + "\u0000" + String(value || "");
  }

  function pendingReactionState(messageId, value, contextType = "public", contextId = "") {
    if (!(state.reactionPending instanceof Map)) state.reactionPending = new Map();
    return state.reactionPending.get(reactionPendingKey(messageId, value, contextType, contextId)) || null;
  }

  function setPendingReactionState(messageId, value, active, contextType = "public", contextId = "") {
    if (!(state.reactionPending instanceof Map)) state.reactionPending = new Map();
    state.reactionPending.set(reactionPendingKey(messageId, value, contextType, contextId), {
      contextType: String(contextType || "public"), contextId: String(contextId || ""),
      messageId: String(messageId || ""), reaction: String(value || ""), active: !!active, time: Date.now()
    });
  }

  function clearSettledPendingReactions(msg) {
    if (!msg || !(state.reactionPending instanceof Map) || !state.reactionPending.size) return;
    const id = String(msg.id || "");
    const contextType = reactionContextType(msg);
    const contextId = reactionContextId(msg);
    const reactions = Array.isArray(msg.reactions) ? msg.reactions : [];
    for (const [key, pending] of state.reactionPending.entries()) {
      if (!pending || String(pending.messageId || "") !== id
          || String(pending.contextType || "public") !== contextType
          || String(pending.contextId || "") !== contextId) continue;
      const row = reactions.find(item => item && String(item.value || "") === String(pending.reaction || ""));
      const authoritativeActive = !!(row && row.mine);
      if (authoritativeActive === !!pending.active) state.reactionPending.delete(key);
    }
  }

  function applyReactionUpdate(data) {
    if (!data || !Array.isArray(data.reactions)) return;
    const id = String(data.messageId || "");
    const type = String(data.contextType || "public").toLowerCase();
    const contextId = String(data.contextId || "");
    let msg = null;
    let box = null;
    let selector = "";
    if (type === "dm") {
      if (contextId && state.dmActiveThreadId && contextId !== String(state.dmActiveThreadId)) return;
      msg = (state.dmMessages || []).find(item => String(item && item.id || "") === id) || null;
      box = document.getElementById("kwc-dm-messages");
      selector = `[data-dm-message-id="${cssEscape(id)}"]`;
    } else if (type === "group") {
      if (contextId && state.groupActiveRoomId && contextId !== String(state.groupActiveRoomId)) return;
      msg = (state.groupMessages || []).find(item => String(item && item.id || "") === id) || null;
      box = document.getElementById("kwc-group-messages");
      selector = `[data-group-message-id="${cssEscape(id)}"]`;
    } else {
      msg = messageById(id);
      box = document.getElementById("kwc-messages");
      selector = `.kwc-msg[data-id="${cssEscape(id)}"]`;
    }
    if (!msg) return;
    msg.reactions = data.reactions;
    if (type === "dm" || type === "group") {
      msg._kwcReactionContextType = type;
      msg._kwcReactionContextId = contextId || (type === "dm" ? String(msg.threadId || "") : String(msg.roomId || ""));
    }
    clearSettledPendingReactions(msg);
    if (!box) return;
    box.querySelectorAll(selector).forEach(el => updateReactionBarElement(el, msg));
  }

  function handleReactionRequestStatus(data) {
    if (!data) return;
    const messageId = String(data.messageId || "");
    const value = String(data.reaction || "");
    if (!messageId || !value || !(state.reactionPending instanceof Map)) return;
    const key = reactionPendingKey(messageId, value, String(data.contextType || "public"), String(data.contextId || ""));
    const pending = state.reactionPending.get(key);
    if (!pending) return;
    const status = String(data.state || "").toLowerCase();
    if (status === "committed" || status === "failed" || status === "expired") state.reactionPending.delete(key);
  }

  async function setMessageReaction(msg, value, active = null) {
    if (!state.token || !msg || !msg.id || !value) return;
    const messageId = String(msg.id);
    const reaction = String(value);
    const contextType = reactionContextType(msg);
    const contextId = reactionContextId(msg);
    let desired = active;
    if (desired === null) {
      const pending = pendingReactionState(messageId, reaction, contextType, contextId);
      if (pending) desired = !pending.active;
      else {
        const row = (Array.isArray(msg.reactions) ? msg.reactions : []).find(item => item && String(item.value || "") === reaction);
        desired = !(row && row.mine);
      }
    }
    desired = !!desired;
    if (desired && state.reactionCatalog && state.reactionCatalog.enabled === false) return;
    const body = {messageId, reaction, active: desired};
    if (contextType !== "public") { body.contextType = contextType; body.contextId = contextId; }
    try {
      const res = await api("/reactions", {method: "POST", body: JSON.stringify(body)});
      if (res && res.ok) {
        rememberRecentReaction(reaction);
        if (res.pending === true) setPendingReactionState(messageId, reaction, desired, contextType, contextId);
        if (Array.isArray(res.reactions)) applyReactionUpdate(res);
      }
    } catch (_) {}
  }

  function defaultReactionCatalog() {
    return {
      enabled: true,
      customEmojiEnabled: true,
      showActorList: true,
      categories: [
        {id:"smileys", items:["😀","😃","😄","😁","😆","😅","😂","🙂","🙃","😉","😊","😍","🥰","😘","😎","🤔","😮","😢","😭","😡","🤯","🥳"]},
        {id:"people", items:["👍","👎","👌","✌️","🤞","🤟","🤘","👏","🙌","🫶","🙏","💪","👀","🧠","❤️","💔","💯"]},
        {id:"animals", items:["🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐸","🐵","🐔","🐧","🐦","🦄","🐝","🦋","🌸","🌈"]},
        {id:"food", items:["🍎","🍊","🍋","🍉","🍇","🍓","🍒","🍑","🍔","🍟","🍕","🌭","🍿","🍩","🍪","🎂","🍰","☕","🍺"]},
        {id:"activities", items:["⚽","🏀","🏈","⚾","🎾","🏐","🎮","🎲","🎯","🎵","🎤","🎧","🎬","📷","🚗","✈️","🚀","🎉","🎊"]},
        {id:"objects", items:["💡","🔥","⭐","✨","⚡","💥","💎","🎁","🏆","🥇","📌","📎","🔒","🔑","🛠️","🧪","💻","📱","⏰"]},
        {id:"symbols", items:["✅","❌","⭕","❗","❓","⚠️","♻️","➕","➖","➡️","⬆️","⬇️","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪"]}
      ]
    };
  }

  async function loadReactionCatalog(force = false) {
    if (!state.token) return defaultReactionCatalog();
    if (!force && state.reactionCatalog && Date.now() - Number(state.reactionCatalogLoadedAt || 0) < 60000) return state.reactionCatalog;
    try {
      const res = await api("/reaction-catalog");
      if (res && res.ok && res.catalog) {
        state.reactionCatalog = res.catalog;
        state.reactionCatalogLoadedAt = Date.now();
        return res.catalog;
      }
    } catch (_) {}
    if (!state.reactionCatalog) state.reactionCatalog = defaultReactionCatalog();
    return state.reactionCatalog;
  }

  function rememberRecentReaction(value) {
    const raw = String(value || "");
    if (!raw) return;
    state.reactionRecent = [raw, ...(Array.isArray(state.reactionRecent) ? state.reactionRecent : []).filter(v => v !== raw)].slice(0, 24);
    try { localStorage.setItem("kwc.reactionRecent", JSON.stringify(state.reactionRecent)); } catch (_) {}
  }

  function reactionCategoryDefs(catalog) {
    const defs = [
      {id:"recent", icon:"🕘", key:"reaction.categoryRecent", fallback:"Recent"},
      {id:"smileys", icon:"😀", key:"reaction.categorySmileys", fallback:"Smileys"},
      {id:"people", icon:"🧑", key:"reaction.categoryPeople", fallback:"People"},
      {id:"animals", icon:"🐾", key:"reaction.categoryAnimals", fallback:"Animals & nature"},
      {id:"food", icon:"🍔", key:"reaction.categoryFood", fallback:"Food"},
      {id:"activities", icon:"⚽", key:"reaction.categoryActivities", fallback:"Activities"},
      {id:"objects", icon:"💡", key:"reaction.categoryObjects", fallback:"Objects"},
      {id:"symbols", icon:"🔣", key:"reaction.categorySymbols", fallback:"Symbols"}
    ];
    if (catalog && catalog.customEmojiEnabled !== false && Array.isArray(state.emojiItems) && state.emojiItems.length) {
      defs.push({id:"custom", icon:"K", key:"reaction.categoryCustom", fallback:"KWC emoji"});
    }
    return defs;
  }



  const REACTION_CATEGORY_SEARCH_ALIASES = Object.freeze({
    smileys:"face faces emoji emoticon expression 표정 얼굴 이모지 顔 絵文字 表情 表情 表情符号",
    people:"people person hand hands gesture 사람 인물 손 제스처 人 手 ジェスチャー 人 手 手势",
    animals:"animal animals nature pet 동물 자연 반려동물 動物 自然 ペット 动物 自然 宠物",
    food:"food foods drink 음식 먹을것 음료 食べ物 飲み物 食物 饮料",
    activities:"activity activities sport leisure 활동 스포츠 여가 活動 スポーツ レジャー 活动 体育 休闲",
    objects:"object objects thing 물건 사물 物 オブジェクト 物品",
    symbols:"symbol symbols mark 기호 표시 記号 マーク 符号 标记"
  });


  function reactionUnicodeSearchText(value, categoryId, catalog) {
    const raw = String(value || "");
    const def = reactionCategoryDefs(catalog).find(item => item.id === String(categoryId || ""));
    return [
      raw,
      (catalog && catalog.searchNames && catalog.searchNames[raw]) || "",
      (catalog && catalog.searchAliases && catalog.searchAliases[raw]) || "",
      REACTION_CATEGORY_SEARCH_ALIASES[String(categoryId || "")] || "",
      def ? t(def.key, def.fallback) : ""
    ].filter(Boolean).join(" ").normalize("NFKC").toLowerCase();
  }

  function reactionCatalogUnicodeItems(catalog, categoryId) {
    const categories = Array.isArray(catalog && catalog.categories) ? catalog.categories : [];
    const row = categories.find(item => String(item && item.id || "") === String(categoryId || ""));
    return Array.isArray(row && row.items) ? row.items.map(String).filter(Boolean) : [];
  }

  function reactionPickerItems(catalog, categoryId, query = "") {
    const q = String(query || "").trim().normalize("NFKC").toLowerCase();
    const unicodeItems = [];
    const categories = Array.isArray(catalog && catalog.categories) ? catalog.categories : [];
    const addUnicode = (value, category) => {
      const raw = String(value);
      unicodeItems.push({value:raw, label:raw, category:String(category || ""), custom:false, search:reactionUnicodeSearchText(raw, category, catalog)});
    };
    if (q) {
      categories.forEach(row => (Array.isArray(row && row.items) ? row.items : []).forEach(value => addUnicode(value, row.id)));
    } else if (categoryId === "recent") {
      const allowed = new Set(categories.flatMap(row => Array.isArray(row && row.items) ? row.items.map(String) : []));
      const customAllowed = catalog && catalog.customEmojiEnabled !== false;
      return (Array.isArray(state.reactionRecent) ? state.reactionRecent : []).filter(value => {
        const raw = String(value || "");
        return raw.startsWith(":") ? customAllowed && !!customEmojiByToken(raw.slice(1,-1)) : allowed.has(raw);
      }).map(value => ({value:String(value), label:reactionValueLabel(value), category:"recent", custom:String(value).startsWith(":")}));
    } else if (categoryId !== "custom") {
      reactionCatalogUnicodeItems(catalog, categoryId).forEach(value => addUnicode(value, categoryId));
    }

    const custom = catalog && catalog.customEmojiEnabled !== false
      ? (Array.isArray(state.emojiItems) ? state.emojiItems : []).map(item => ({
          value:`:${item.id}:`, label:String(item.label || item.name || item.id || ""), category:"custom", custom:true,
          search:[
            item.id, item.label, item.name, item.pack,
            ...(Array.isArray(item.aliases) ? item.aliases : [])
          ].filter(Boolean).join(" ").normalize("NFKC").toLowerCase()
        }))
      : [];
    let items = q ? unicodeItems.concat(custom) : (categoryId === "custom" ? custom : unicodeItems);
    if (q) {
      items = items.filter(item => {
        if (String(item.value || "").toLowerCase().includes(q)) return true;
        if (String(item.label || "").toLowerCase().includes(q)) return true;
        if (String(item.search || "").includes(q)) return true;
        const def = reactionCategoryDefs(catalog).find(d => d.id === item.category);
        return !!def && t(def.key, def.fallback).toLowerCase().includes(q);
      });
    }
    const seen = new Set();
    return items.filter(item => item.value && !seen.has(item.value) && seen.add(item.value));
  }

  function closeReactionPicker() {
    const old = document.querySelector(".kwc-reaction-picker");
    if (!old) return;
    if (old._kwcOutsidePointerHandler) {
      document.removeEventListener("pointerdown", old._kwcOutsidePointerHandler, true);
      old._kwcOutsidePointerHandler = null;
    }
    old.remove();
  }

  async function openReactionPicker(anchor, msg) {
    if (!state.token || !anchor || !msg) return;
    closeReactionPicker();
    const catalog = await loadReactionCatalog();
    if (!anchor.isConnected || !catalog || catalog.enabled === false) return;
    const picker = document.createElement("div");
    picker.className = "kwc-reaction-picker";
    document.body.appendChild(picker);
    applyDetachedModalTheme(picker);

    const render = (query = "") => {
      const defs = reactionCategoryDefs(catalog);
      if (!defs.some(def => def.id === state.reactionPickerCategory)) state.reactionPickerCategory = defs[0]?.id || "smileys";
      const items = reactionPickerItems(catalog, state.reactionPickerCategory, query);
      const tabs = defs.map(def => `<button type="button" class="kwc-reaction-category${state.reactionPickerCategory === def.id && !query ? " kwc-active" : ""}" data-reaction-category="${esc(def.id)}" title="${esc(t(def.key, def.fallback))}" aria-label="${esc(t(def.key, def.fallback))}">${esc(def.icon)}</button>`).join("");
      const grid = items.map(item => `<button type="button" class="kwc-reaction-choice${item.custom ? " kwc-reaction-custom" : ""}" data-reaction-pick="${esc(item.value)}" title="${esc(item.label || item.value)}" aria-label="${esc(item.label || item.value)}">${reactionValueHtml(item.value)}</button>`).join("") || `<div class="kwc-reaction-empty">${esc(t("reaction.searchEmpty", "No matching reactions."))}</div>`;
      picker.innerHTML = `<div class="kwc-reaction-categories">${tabs}</div>`
        + `<div class="kwc-reaction-input-row"><input class="kwc-input kwc-reaction-input" type="search" maxlength="64" value="${esc(query)}" placeholder="${esc(t("reaction.searchPlaceholder", "Search emoji"))}"><button type="button" class="kwc-button kwc-reaction-search">${esc(t("button.search", "Search"))}</button></div>`
        + `<div class="kwc-reaction-grid">${grid}</div>`;
      installCustomEmojiImageRecovery(picker);
      picker.querySelectorAll("[data-reaction-category]").forEach(btn => btn.addEventListener("click", event => {
        event.preventDefault(); event.stopPropagation();
        state.reactionPickerCategory = btn.dataset.reactionCategory || "smileys";
        // Keep the picker at the position where it was opened. Category contents
        // have different heights; re-clamping after every render made larger
        // categories jump to the chat viewport's top-left corner.
        render("");
      }));
      picker.querySelectorAll("[data-reaction-pick]").forEach(btn => btn.addEventListener("click", event => {
        event.preventDefault(); event.stopPropagation();
        setMessageReaction(msg, btn.dataset.reactionPick || "");
        closeReactionPicker();
      }));
      const input = picker.querySelector(".kwc-reaction-input");
      const search = picker.querySelector(".kwc-reaction-search");
      const doSearch = () => render(String(input && input.value || "").trim());
      if (search) search.addEventListener("click", doSearch);
      if (input) {
        input.addEventListener("keydown", e => { if (e.key === "Enter" && !e.isComposing) { e.preventDefault(); doSearch(); } });
      }
    };

    render("");
    positionReactionPicker(picker, anchor);
    const input = picker.querySelector(".kwc-reaction-input");
    if (input) input.focus();
    const outsidePointerHandler = event => {
      if (!picker.isConnected) {
        document.removeEventListener("pointerdown", outsidePointerHandler, true);
        return;
      }
      if (picker.contains(event.target) || anchor.contains(event.target)) return;
      closeReactionPicker();
    };
    picker._kwcOutsidePointerHandler = outsidePointerHandler;
    setTimeout(() => {
      if (picker.isConnected) document.addEventListener("pointerdown", outsidePointerHandler, true);
    }, 0);
  }

  function positionReactionPicker(picker, anchor) {
    if (!picker || !anchor || !picker.isConnected || !anchor.isConnected) return;
    const rect = anchor.getBoundingClientRect();
    picker.style.left = Math.max(6, Math.min(window.innerWidth - picker.offsetWidth - 6, rect.left)) + "px";
    picker.style.top = Math.max(6, Math.min(window.innerHeight - picker.offsetHeight - 6, rect.bottom + 4)) + "px";
  }

  function installReactionHandlers(el, msg) {
    if (!el || !msg) return;
    el.querySelectorAll(".kwc-reaction-pill[data-reaction-value]").forEach(btn => {
      if (btn.dataset.kwcReactionBound === "1") return;
      btn.dataset.kwcReactionBound = "1";
      btn.addEventListener("click", event => {
        event.preventDefault(); event.stopPropagation();
        if (!state.token) return;
        if (state.reactionCatalog && state.reactionCatalog.enabled === false) return;
        const value = btn.dataset.reactionValue || "";
        const row = (Array.isArray(msg.reactions) ? msg.reactions : []).find(item => item && String(item.value || "") === value);
        // Existing chips are normal reaction toggles: join a reaction started by
        // another user, or remove it when this account is already participating.
        setMessageReaction(msg, value, !(row && row.mine));
      });
    });
    el.querySelectorAll(".kwc-reaction-action[data-reaction-open]").forEach(btn => {
      if (btn.dataset.kwcReactionBound === "1") return;
      btn.dataset.kwcReactionBound = "1";
      btn.addEventListener("click", event => {
        event.preventDefault(); event.stopPropagation();
        openReactionPicker(btn, msg);
      });
    });
    if (el.dataset.kwcReactionTouchBound !== "1") {
      el.dataset.kwcReactionTouchBound = "1";
      el.addEventListener("pointerup", event => {
        if (event.pointerType !== "touch" || state.archiveSelection) return;
        const target = event.target instanceof Element ? event.target : null;
        if (!target || target.closest("a,button,input,textarea,select,label,[role=button],.kwc-media,.kwc-reaction-tooltip")) return;
        document.querySelectorAll(".kwc-msg.kwc-reaction-touch-open").forEach(node => { if (node !== el) node.classList.remove("kwc-reaction-touch-open"); });
        el.classList.add("kwc-reaction-touch-open");
        clearTimeout(el._kwcReactionTouchTimer);
        el._kwcReactionTouchTimer = setTimeout(() => el.classList.remove("kwc-reaction-touch-open"), 5000);
      }, {passive:true});
    }
  }

