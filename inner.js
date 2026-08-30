(() => {
  const KWC_INNER_IFRAME_MARKER_296 = true;
  const cfg = window.KokotoWebChatConfig || window.BlueMapWebChatConfig || {};
  function migrateLegacyBrowserState() {
    try {
      const legacyKeys = [];
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.startsWith("bmwc.")) legacyKeys.push(key);
      }
      for (const legacyKey of legacyKeys) {
        const currentKey = "kwc." + legacyKey.substring("bmwc.".length);
        if (localStorage.getItem(currentKey) == null) {
          const value = localStorage.getItem(legacyKey);
          if (value != null) localStorage.setItem(currentKey, value);
        }
        localStorage.removeItem(legacyKey);
      }
    } catch (_) {}
  }
  migrateLegacyBrowserState();
  try { localStorage.removeItem("kwc.notify.ownMessages"); } catch (_) {}
  function kwcDefaultApiBase() {
    return location.origin + "/api";
  }

  function kwcNormalizeApiBase(value) {
    let v = String(value || "").trim();
    if (!v) v = kwcDefaultApiBase();
    v = v.replace(/\/+$/, "");
    // Normalize resource URLs accidentally placed in the API-base option.
    // Upload and emoji suffixes are appended separately.
    v = v.replace(/\/(?:uploads|emojis)$/i, "");
    return v;
  }
  const apiBase = kwcNormalizeApiBase(cfg.apiBase || cfg.apiBaseUrl || "");
  const runtimeMode = {
    pip: cfg.pip === true,
    standalone: cfg.standalone === true
  };
  // Capture the exact inner application source while this inline script is executing.
  // Standalone Document PiP can then bootstrap a second same-origin chat document
  // directly from the user click without depending on the BlueMap parent bridge.
  const KWC_INNER_SELF_SOURCE = (() => {
    try { return document.currentScript && document.currentScript.textContent ? document.currentScript.textContent : ""; } catch (_) { return ""; }
  })();
  let standalonePipWindow = null;
  let standalonePipRelay = null;
  let standalonePipRelayId = String(cfg.pipStreamChannel || "");
  let standalonePipRestoreMinimized = false;

  const state = {
    config: null,
    token: localStorage.getItem("kwc.token") || "",
    username: localStorage.getItem("kwc.username") || "",
    role: localStorage.getItem("kwc.role") || "",
    guestName: localStorage.getItem("kwc.guestName") || "",
    captcha: null,
    captchaPass: localStorage.getItem("kwc.captchaPass") || "",
    isPip: runtimeMode.pip,
    isStandalone: runtimeMode.standalone,
    minimized: (runtimeMode.pip || runtimeMode.standalone) ? false : localStorage.getItem("kwc.minimized") === "1",
    eventSource: null,
    streamGeneration: 0,
    streamReconnectTimer: null,
    streamReconnectAttempt: 0,
    streamReconnectAfterOpen: false,
    streamReconnectReason: "",
    streamReconnectInFlight: false,
    streamLastOpenAt: 0,
    streamLastEventAt: 0,
    streamHealthTimer: null,
    serverVersion: "",
    lang: {},
    selectedLanguage: localStorage.getItem("kwc.language") || "",
    availableLanguages: [],
    historyLoading: false,
    historyHasMore: true,
    historyHasAfter: false,
    historyOldestId: "",
    historyNewestId: "",
    historyPageSize: 20,
    frameMinimizedHeight: 71,
    frameNormalWidth: 372,
    frameNormalHeight: 462,
    resizeLocked: localStorage.getItem("kwc.resizeLocked") === "1",
    resizeStart: null,
    themeSyncTimer: null,
    loginModalOpen: false,
    prefsModalOpen: false,
    searchModalOpen: false,
    lastLoginButtonActivateAt: 0,
    dragStart: null,
    messages: [],
    replyTarget: null,
    pins: [],
    pinsEnabled: true,
    pinsCanPin: false,
    moderationActionsVisible: false,
    commands: [],
    commandsCanRun: false,
    commandsEnabled: false,
    commandsAllowAll: false,
    commandsShowButton: true,
    commandsShowSlashPanel: true,
    commandsRunFromChatInput: false,
    commandsRequireConfirm: true,

    directMessageEnabled: false,
    directMessageAllowWebSend: true,
    directMessageMaxMessageLength: 500,
    directMessageRetentionDays: 0,
    directMessageWebUnreadBadge: true,
    directMessageConfirmHide: true,
    dmUnread: 0,
    dmThreads: [],
    dmAdminThreads: [],
    dmCleanupPreview: null,
    privateChatContentAccess: false,
    dmModalOpen: false,
    dmActiveThreadId: "",
    dmDraftTarget: null,
    dmAuditMode: false,
    dmAuditThread: null,
    dmSearchTimer: null,
    dmSearchPanelOpen: false,
    dmConversationFocus: localStorage.getItem("kwc.dmConversationFocus") === "1",
    dmEmojiPanelOpen: false,
    dmEdgeToastVisible: false,
    dmEdgeToastVisibleUntil: 0,
    dmEdgeToastLastShownAt: 0,
    dmEdgeToastTimer: null,
    dmEdgePendingTopUntil: 0,
    dmEdgePendingBottomUntil: 0,
    dmEdgeBottomExtraScrollCount: 0,
    dmMessages: [],
    dmReplyTarget: null,
    dmReplyJumpGeneration: 0,
    dmReplyJumpStartedAt: 0,
    dmReplyJumpLastCenteredScrollTop: NaN,
    dmReplyJumpStabilizeTimer: null,
    dmMessagesHasMore: false,
    dmMessagesLoading: false,
    dmBottomRetryInFlight: false,
    dmLastBottomRetryAt: 0,

    groupChatEnabled: false,
    groupChatAllowWebSend: true,
    groupChatAllowPublicRooms: true,
    groupChatAllowRoomPasswords: true,
    groupChatMaxMessageLength: 500,
    groupChatRetentionDays: 30,
    groupChatConfirmLeave: true,
    groupChatConfirmHide: true,
    groupUnread: 0,
    groupRooms: [],
    groupInvites: [],
    groupHiddenRooms: [],
    groupAdminRooms: [],
    groupCleanupPreview: null,
    privateChatSuperAdmin: false,
    groupChatContentAccess: false,
    groupAuditMode: false,
    groupAuditRoom: null,
    groupModalOpen: false,
    groupActiveRoomId: "",
    groupActiveRoom: null,
    groupSearchPanelOpen: false,
    groupSearchTimer: null,
    groupEmojiPanelOpen: false,
    groupEmojiSelectedPack: "",
    groupEdgeToastVisible: false,
    groupEdgeToastVisibleUntil: 0,
    groupEdgeToastLastShownAt: 0,
    groupEdgeToastTimer: null,
    groupEdgePendingTopUntil: 0,
    groupEdgePendingBottomUntil: 0,
    groupEdgeBottomExtraScrollCount: 0,
    groupMessages: [],
    groupReplyTarget: null,
    groupReplyJumpGeneration: 0,
    groupReplyJumpStartedAt: 0,
    groupReplyJumpLastCenteredScrollTop: NaN,
    groupReplyJumpStabilizeTimer: null,
    groupMessagesHasMore: false,
    groupMessagesLoading: false,
    groupBottomRetryInFlight: false,
    groupLastBottomRetryAt: 0,
    groupScrollbarDragActive: false,
    groupScrollbarDragLastX: null,
    groupScrollbarDragLastY: null,

    activeComposeInputId: "kwc-message",

    emojiEnabled: false,
    emojiShowButton: true,
    emojiRenderSizePx: 32,
    emojiPickerSizePx: 44,
    emojiMessageTokenLimit: 12,
    emojiTokenFormat: "short",
    emojiPacks: [],
    emojiItems: [],
    emojiById: new Map(),
    emojiByAlias: new Map(),
    emojiPanelOpen: false,
    emojiLoading: false,
    emojiRetryTimer: null,
    emojiRetryAttempt: 0,
    emojiPanelHeightPx: Math.max(56, Math.min(420, Number(localStorage.getItem("kwc.emojiPanelHeightPx") || 180) || 180)),
    emojiPanelResizeStart: null,
    adminEmojiSelectedPack: localStorage.getItem("kwc.adminEmojiPack") || "default",
    commandMaxLength: 0,
    nextLocalMessageId: 1,
    sendInFlight: false,
    sendInFlightSince: 0,
    sendInFlightText: "",
    virtualRenderStart: 0,
    virtualRenderEnd: 0,
    virtualAverageMessageHeight: 42,
    virtualRenderScheduled: false,
    virtualPendingRenderOptions: null,
    virtualResizeObserver: null,
    virtualMessageResizeObserver: null,
    resumeRefreshInFlight: false,
    lastResumeRefreshAt: 0,
    autoFollowLatest: true,
    suppressAutoFollowUpdate: false,
    suppressScrollRenderUntil: 0,
    preventBottomStickUntil: 0,
    forceHistoryEndNoticeUntil: 0,
    youtubeExpanded: new Set(),
    youtubeOpen: new Set(),
    mediaOpen: new Set(),
    failedMediaPreviews: new Set(),
    lastUserScrollAt: 0,
    lastDirectScrollInputAt: 0,
    lastNonScrollUiActionAt: 0,
    historyEndNoticeStickySince: 0,
    historyEndNoticeProtectedUntil: 0,
    historyEndNoticeVisible: false,
    historyEndNoticeUiTransitionUntil: 0,
    historyEndNoticeTimer: null,
    historyEndNoticeVisibleUntil: 0,
    historyEndNoticeLastShownAt: 0,
    historyEndNoticePendingUserTopUntil: 0,
    historyEndNoticePendingUserBottomUntil: 0,
    historyEndNoticeBottomExtraScrollCount: 0,
    scrollbarDragLastX: null,
    scrollbarDragLastY: null,
    historyEndNoticePosition: "top",
    historyEndNoticeKey: "history.end",
    historyEndNoticeFallback: "No more messages to display.",
    historySlowNoticeTimer: null,
    forceLatestJumpUntil: 0,
    replyJumpUntil: 0,
    replyJumpGeneration: 0,
    replyJumpTargetId: "",
    replyJumpStartedAt: 0,
    replyJumpLastCenteredScrollTop: NaN,
    replyJumpStabilizeTimer: null,
    explicitLatestFollowUntil: 0,
    explicitLatestFollowReason: "",
    scrollInteractionUntil: 0,
    scrollIdleTimer: null,
    scrollbarDragActive: false,
    touchScrollActive: false,
    pendingScrollRenderOptions: null,
    pendingOlderHistoryLoad: false,
    pendingNewerHistoryLoad: false,
    pendingTopOlderHistoryTimer: null,
    pendingTopOlderHistoryDueAt: 0,
    pendingBottomNewerHistoryTimer: null,
    pendingBottomNewerHistoryDueAt: 0,
    olderHistorySettleUntil: 0,
    historyTopEdgeIntentUntil: 0,
    historyBottomEdgeIntentUntil: 0,
    lastTopOlderHistoryRequestAt: 0,
    historyLoadingSince: 0,
    historyLoadSeq: 0,
    lastBottomNewerHistoryRequestAt: 0,
    pendingResumeRefreshReason: "",
    uploadXhr: null,
    uploadCancelRequested: false,
    uploadActive: false,
    historyViewportFillTimer: null,
    historyViewportFillAttempts: 0,
    viewportMaintenanceTimer: null,
    viewportMaintenanceDueAt: 0,
    dragUploadDepth: 0,
    senderIdentityMode: localStorage.getItem("kwc.senderIdentityMode") === "real" ? "real" : "display",
    timeDisplayMode: localStorage.getItem("kwc.timeDisplayMode") === "full" ? "full" : "short",

    browserNotificationsEnabled: true,
    browserNotificationsOnlyWhenHidden: true,
    browserNotificationsNotifyNormalChat: true,
    browserNotificationsNotifyDm: true,
    browserNotificationsNotifyGroupChat: true,
    browserNotificationsNotifyMentions: true,
    browserNotificationsNotifyReplies: true,
    browserNotificationsNotifySystem: true,
    browserNotificationsNotifyKeywords: true,
    browserNotificationsShowMessagePreview: true,
    webPushEnabled: false,
    webPushAvailable: false,
    webPushVapidPublicKey: "",
    webPushNotificationTitle: "",
    standaloneWebEnabled: false,
    standaloneWebPath: "",
    standaloneWebPublicUrl: "",
    standaloneWebAppName: "",
    standaloneWebAppShortName: "",
    parentPageUrl: String(cfg.parentPageUrl || cfg.pageUrl || ""),
    webPushNotifyNormalChat: true,
    webPushNotifyDm: true,
    webPushNotifyGroupChat: true,
    webPushNotifyMentions: true,
    webPushNotifyReplies: true,
    webPushNotifySystem: true,
    webPushNotifyKeywords: true,
    webPushShowMessagePreview: true,
    webPushRegistering: false,
    webPushLastError: "",
    webPushSubscriptionActive: false,
    webPushAutoRetryAfter: 0,
    webPushAutoFailure: "",
    accountNotificationSyncTimer: null,
    applyingAccountNotificationPreferences: false,
    userProfilesEnabled: false,
    userProfilesMaxProfiles: 5,
    userProfilesAllowImportExport: true,
    accountProfiles: [],
    accountProfilesLoading: false,
    notificationInboxUnread: 0
  };

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
          <button type="button" class="kwc-media-load kwc-button">${esc(t("media.loadTikTok", "▶ TikTok"))}</button>
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
          <button type="button" class="kwc-media-load kwc-button">${esc(t("media.loadXPost", "▶ X post"))}</button>
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
          <span class="kwc-youtube-play" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);font-size:34px;text-shadow:0 2px 8px rgba(0,0,0,.85);">▶</span>
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
            <button type="button" class="kwc-media-load kwc-button">${esc(t("media.loadVideo", "▶ Video"))}</button>
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
            <button type="button" class="kwc-media-load kwc-button">${esc(t("media.loadAudio", "▶ Audio"))}</button>
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
    const fallbackTabsHeight = Array.isArray(state.emojiPacks) && state.emojiPacks.length > 1 ? 30 : 0;
    const fallback = 16 + fallbackTabsHeight + fallbackItemHeight;
    if (!panel || panel.classList.contains("kwc-hidden")) return Math.max(56, Math.ceil(fallback));

    const panelStyle = getComputedStyle(panel);
    const padTop = parseFloat(panelStyle.paddingTop || "0") || 0;
    const padBottom = parseFloat(panelStyle.paddingBottom || "0") || 0;
    const borderTop = parseFloat(panelStyle.borderTopWidth || "0") || 0;
    const borderBottom = parseFloat(panelStyle.borderBottomWidth || "0") || 0;
    const tabs = panel.querySelector(".kwc-emoji-tabs");
    const item = panel.querySelector(".kwc-emoji-item");

    let tabsHeight = 0;
    if (tabs) {
      const tabsStyle = getComputedStyle(tabs);
      tabsHeight = Number(tabs.getBoundingClientRect().height || tabs.offsetHeight || fallbackTabsHeight);
      tabsHeight += parseFloat(tabsStyle.marginTop || "0") || 0;
      tabsHeight += parseFloat(tabsStyle.marginBottom || "0") || 0;
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

  function replyPreviewPlain(msg) {
    if (!msg) return "";
    const value = String(msg.replyToPreview || "").trim();
    if (value) return value;
    return plainDisplayMessageText(msg).replace(/[\r\n]+/g, " ").trim();
  }

  function messageById(id) {
    id = String(id || "");
    if (!id) return null;
    return state.messages.find(m => m && String(m.id || "") === id) || null;
  }

  function replyTargetFromMessage(msg) {
    if (!msg || !msg.id || msg.hidden) return null;
    const sender = displaySender(msg) || msg.sender || "";
    // Preserve the complete original message in the reply payload. Compact reply
    // presentation is a rendering concern (ellipsis/nowrap), not stored data.
    const preview = plainDisplayMessageText(msg);
    return {id: String(msg.id), sender, preview};
  }

  function renderReplyCompose() {
    const wrap = document.getElementById("kwc-reply-compose");
    if (!wrap) return;
    const target = state.replyTarget;
    wrap.classList.toggle("kwc-hidden", !target || !target.id);
    const label = document.getElementById("kwc-reply-compose-label");
    const preview = document.getElementById("kwc-reply-compose-preview");
    if (label) label.innerHTML = target && target.id ? formatReplyComposeLabelHtml(target.sender || "") : "";
    if (preview) preview.innerHTML = target && target.id ? replyPreviewHtml(target.preview || "") : "";
  }

  function startReplyToMessage(msg) {
    const target = replyTargetFromMessage(msg);
    if (!target) return;
    state.replyTarget = target;
    renderReplyCompose();
    const input = document.getElementById("kwc-message");
    if (input) input.focus();
  }

  function clearReplyTarget() {
    state.replyTarget = null;
    renderReplyCompose();
  }

  function replyPreviewHtml(value) {
    // replyToPreview keeps the complete original text. Collapse line breaks only
    // for this compact one-line UI, keep registered emojis rendered, and decode
    // percent-encoded URL text for readability without creating nested anchors
    // inside the reply jump button.
    const compact = String(value || "").replace(/[\r\n]+/g, " ");
    return minecraftLegacyTextHtml(compact, true, false, true);
  }

  function replyReferenceHtml(msg) {
    if (!msg || !msg.replyToId) return "";
    const sender = msg.replyToSender || t("sender.unknown", "Unknown");
    const preview = msg.replyToPreview || "";
    const plainSender = plainLegacyText(sender).trim() || t("sender.unknown", "Unknown");
    const plainPreview = plainLegacyText(preview).replace(/[\r\n]+/g, " ").trim();
    const titlePreview = plainPreview.length > 240 ? plainPreview.slice(0, 237) + "..." : plainPreview;
    const title = titlePreview ? fmt("reply.jump", "Jump to replied message") + ": " + plainSender + " - " + titlePreview : t("reply.jump", "Jump to replied message");
    return `<button type="button" class="kwc-reply-ref" data-reply-jump="${esc(msg.replyToId)}" title="${esc(title)}">
      <span class="kwc-reply-ref-sender">${minecraftLegacyTextHtml(sender, true)}</span>
      <span class="kwc-reply-ref-preview">${replyPreviewHtml(preview)}</span>
    </button>`;
  }

  function highlightMessageElement(el) {
    if (!el) return;
    el.classList.remove("kwc-reply-highlight");
    void el.offsetWidth;
    el.classList.add("kwc-reply-highlight");
    setTimeout(() => { try { el.classList.remove("kwc-reply-highlight"); } catch (_) {} }, 2600);
  }

  function cancelReplyJumpDeferredWork(reason = "reply-jump") {
    // A reply jump is an explicit navigation request. Any delayed scroll-idle,
    // viewport-fill, maintenance, or history paging job that was queued for the
    // previous viewport may otherwise run a few frames later and pull the chat
    // back down. Cancel them and invalidate in-flight history pages before the
    // target-focused render starts.
    clearTimeout(state.scrollIdleTimer);
    state.scrollIdleTimer = null;
    clearTimeout(state.historyViewportFillTimer);
    state.historyViewportFillTimer = null;
    state.historyViewportFillAttempts = 0;
    clearTimeout(state.viewportMaintenanceTimer);
    state.viewportMaintenanceTimer = null;
    state.viewportMaintenanceDueAt = 0;
    clearTimeout(state.pendingTopOlderHistoryTimer);
    state.pendingTopOlderHistoryTimer = null;
    state.pendingTopOlderHistoryDueAt = 0;
    clearTimeout(state.pendingBottomNewerHistoryTimer);
    state.pendingBottomNewerHistoryTimer = null;
    state.pendingBottomNewerHistoryDueAt = 0;
    state.historyTopEdgeIntentUntil = 0;
    state.historyBottomEdgeIntentUntil = 0;
    clearTimeout(state.replyJumpStabilizeTimer);
    state.replyJumpStabilizeTimer = null;

    state.pendingScrollRenderOptions = null;
    state.virtualPendingRenderOptions = null;
    state.pendingOlderHistoryLoad = false;
    state.pendingNewerHistoryLoad = false;
    state.pendingResumeRefreshReason = "";
    state.replyJumpLastCenteredScrollTop = NaN;
    state.olderHistorySettleUntil = 0;
    state.scrollInteractionUntil = 0;
    state.scrollbarDragActive = false;
    state.touchScrollActive = false;

    if (state.historyLoading) {
      state.historyLoading = false;
      state.historyLoadingSince = 0;
    }
    state.historyLoadSeq++;
    state.forceLatestJumpUntil = 0;
    state.explicitLatestFollowUntil = 0;
    state.explicitLatestFollowReason = "";
  }

  function extendReplyJumpLock(ms = 900) {
    const now = Date.now();
    const duration = Math.max(250, Number(ms) || 900);
    state.replyJumpUntil = Math.max(Number(state.replyJumpUntil || 0), now + duration);
    state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), now + duration);
    state.suppressScrollRenderUntil = Math.max(Number(state.suppressScrollRenderUntil || 0), now + duration);
    state.suppressAutoFollowUpdate = true;
    state.autoFollowLatest = false;
    state.explicitLatestFollowUntil = 0;
    state.forceLatestJumpUntil = 0;
  }

  function beginReplyJumpLock(ms = 1400) {
    cancelReplyJumpDeferredWork("reply-jump");
    const duration = Math.max(300, Number(ms) || 1400);
    const generation = ++state.replyJumpGeneration;
    state.replyJumpStartedAt = Date.now();
    state.replyJumpLastCenteredScrollTop = NaN;
    extendReplyJumpLock(duration);
    setTimeout(() => {
      if (state.replyJumpGeneration === generation && Date.now() >= Number(state.replyJumpUntil || 0) - 30) {
        state.suppressAutoFollowUpdate = false;
        state.replyJumpTargetId = "";
        state.replyJumpStartedAt = 0;
        state.replyJumpLastCenteredScrollTop = NaN;
      }
    }, duration + 40);
    return generation;
  }

  function cancelReplyJumpForUserScroll(reason = "user-scroll") {
    const now = Date.now();
    const hasActiveReplyJump = now < Number(state.replyJumpUntil || 0) || !!state.replyJumpStabilizeTimer || !!state.replyJumpTargetId;
    if (!hasActiveReplyJump) return;

    // Once the user starts wheel/touch/key/scrollbar scrolling, the reply jump is
    // no longer allowed to keep re-centering the target. The previous
    // stabilization loop intentionally rechecked the target for ~2s so late
    // virtual-scroll/media height changes would not knock it out of view. That
    // same loop becomes harmful after real user input: it feels like the scroll
    // is being rewound. Incrementing the generation invalidates any pending
    // requestAnimationFrame/setTimeout callbacks from the old jump.
    clearTimeout(state.replyJumpStabilizeTimer);
    state.replyJumpStabilizeTimer = null;
    state.replyJumpGeneration++;
    state.replyJumpUntil = 0;
    state.replyJumpTargetId = "";
    state.replyJumpStartedAt = 0;
    state.replyJumpLastCenteredScrollTop = NaN;
    state.pendingScrollRenderOptions = null;
    state.virtualPendingRenderOptions = null;
    state.suppressScrollRenderUntil = 0;
    state.suppressAutoFollowUpdate = false;
    state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), now + 350);
    state.forceLatestJumpUntil = 0;
    state.explicitLatestFollowUntil = 0;
    state.explicitLatestFollowReason = "";
    state.autoFollowLatest = false;
  }

  function isMessageElementCentered(box, el, tolerancePx = 18) {
    if (!box || !el) return false;
    try {
      const boxRect = box.getBoundingClientRect();
      const elRect = el.getBoundingClientRect();
      if (elRect.bottom < boxRect.top + 4 || elRect.top > boxRect.bottom - 4) return false;
      const boxCenter = boxRect.top + Math.max(1, boxRect.height || box.clientHeight || 1) / 2;
      const elCenter = elRect.top + Math.max(1, elRect.height || 1) / 2;
      return Math.abs(elCenter - boxCenter) <= Math.max(4, Number(tolerancePx) || 18);
    } catch (_) {
      return false;
    }
  }

  function renderReplyJumpFocusedRange(id, generation, options = {}) {
    if (state.replyJumpGeneration !== generation) return false;
    id = String(id || "");
    if (!id) return false;
    const idx = state.messages.findIndex(m => m && String(m.id || "") === id);
    if (idx < 0) return false;
    renderVirtualMessages({
      stickToBottom: false,
      preserveScroll: false,
      preserveVisualAnchor: false,
      forcePreservePosition: true,
      suppressBottomStick: true,
      ignoreVisibleRangeProtection: true,
      focusIndex: idx,
      deferDuringScroll: false
    });
    return true;
  }

  function replyJumpLooksUserMoved(box, startedAt) {
    if (!box) return false;
    const lastDirect = Number(state.lastDirectScrollInputAt || 0);
    const lastUserScroll = Number(state.lastUserScrollAt || 0);
    if (lastDirect > 0 && lastDirect >= Number(startedAt || 0) - 10) return true;
    if (lastUserScroll > 0 && lastUserScroll >= Number(startedAt || 0) - 10) return true;
    const lastCentered = Number(state.replyJumpLastCenteredScrollTop);
    if (!Number.isFinite(lastCentered)) return false;
    const drift = Math.abs(Number(box.scrollTop || 0) - lastCentered);
    return drift > Math.max(28, Math.round(Math.max(1, Number(box.clientHeight || 1)) * 0.07));
  }

  function stabilizeReplyJumpTarget(id, generation, options = {}) {
    id = String(id || "");
    if (!id) return;
    clearTimeout(state.replyJumpStabilizeTimer);
    const selector = `.kwc-msg[data-id="${cssEscape(id)}"]`;
    const lockMs = Math.max(450, Number(options.lockMs || 1100));
    const delays = Array.isArray(options.delays) ? options.delays : [0, 60, 160, 360, 720];
    const startedAt = Number(options.startedAt || state.replyJumpStartedAt || Date.now());
    let pos = 0;

    const run = () => {
      if (state.replyJumpGeneration !== generation) return;
      const box = document.getElementById("kwc-messages");
      if (!box) return;
      if (pos > 0 && replyJumpLooksUserMoved(box, startedAt)) {
        cancelReplyJumpForUserScroll("reply-jump-user-moved");
        return;
      }
      extendReplyJumpLock(Math.max(260, Math.min(lockMs, 650)));
      let el = box.querySelector(selector);
      if (!el) {
        renderReplyJumpFocusedRange(id, generation, {reason: "reply-jump-stabilize-render"});
        el = box.querySelector(selector);
      }
      if (el && (!isMessageElementCentered(box, el, pos === 0 ? 8 : 22) || pos <= 1)) {
        centerMessageElementInBox(box, el, {
          reason: "reply-jump-stabilize",
          suppressRenderMs: Math.max(450, Math.min(lockMs, 900)),
          suppressUpdateMs: Math.max(450, Math.min(lockMs, 900)),
          highlight: pos <= 1
        });
      }
      const delay = delays[++pos];
      if (state.replyJumpGeneration === generation && Number.isFinite(Number(delay))) {
        state.replyJumpStabilizeTimer = setTimeout(run, Math.max(0, Number(delay)));
      } else if (state.replyJumpGeneration === generation) {
        state.replyJumpStabilizeTimer = null;
      }
    };

    state.replyJumpStabilizeTimer = setTimeout(run, Math.max(0, Number(delays[0]) || 0));
  }

  function centerMessageElementInBox(box, el, options = {}) {
    if (!box || !el) return false;
    let desired = Number(box.scrollTop || 0);
    try {
      const boxRect = box.getBoundingClientRect();
      const elRect = el.getBoundingClientRect();
      const centerOffset = Math.max(0, (Number(box.clientHeight || 0) - Number(elRect.height || 0)) / 2);
      desired = desired + (elRect.top - boxRect.top) - centerOffset;
    } catch (_) {
      desired = Number(box.scrollTop || 0);
    }
    setScrollTopPreserved(box, desired, {
      allowAwayFromBottom: true,
      reason: options.reason || "reply-jump-center",
      suppressRenderMs: Number(options.suppressRenderMs || 900),
      suppressUpdateMs: Number(options.suppressUpdateMs || 900)
    });
    if (String(options.reason || "").indexOf("reply-jump") === 0) {
      state.replyJumpLastCenteredScrollTop = desired;
    }
    if (options.highlight !== false) highlightMessageElement(el);
    refreshScrollAffordances(box);
    return true;
  }

  function scrollToMessageId(id, options = {}) {
    id = String(id || "");
    if (!id) return false;
    const box = document.getElementById("kwc-messages");
    if (!box) return false;
    const selector = `.kwc-msg[data-id="${cssEscape(id)}"]`;
    const lockMs = Math.max(1000, Number(options.lockMs || 1800));
    state.replyJumpTargetId = id;

    let el = box.querySelector(selector);
    if (el) {
      const generation = beginReplyJumpLock(lockMs);
      centerMessageElementInBox(box, el, {reason: "reply-jump-visible", suppressRenderMs: lockMs, suppressUpdateMs: lockMs});
      stabilizeReplyJumpTarget(id, generation, {lockMs});
      return true;
    }

    const idx = state.messages.findIndex(m => m && String(m.id || "") === id);
    if (idx < 0) return false;

    const generation = beginReplyJumpLock(lockMs);
    renderReplyJumpFocusedRange(id, generation, {reason: "reply-jump-virtual"});

    const finish = () => {
      if (state.replyJumpGeneration !== generation) return;
      const currentBox = document.getElementById("kwc-messages");
      const later = currentBox ? currentBox.querySelector(selector) : null;
      if (later) centerMessageElementInBox(currentBox, later, {reason: "reply-jump-virtual", suppressRenderMs: lockMs, suppressUpdateMs: lockMs});
      else renderReplyJumpFocusedRange(id, generation, {reason: "reply-jump-virtual-retry"});
    };
    requestAnimationFrame(finish);
    setTimeout(finish, 80);
    setTimeout(finish, 220);
    stabilizeReplyJumpTarget(id, generation, {lockMs});
    return true;
  }

  async function jumpToReplyTarget(id) {
    id = String(id || "");
    if (!id) return;
    state.replyJumpTargetId = id;
    if (scrollToMessageId(id, {lockMs: 2200})) return;

    const generation = beginReplyJumpLock(3200);
    try {
      const data = await api(`/history/around?id=${encodeURIComponent(id)}&before=40&after=40`, {timeoutMs: 15000});
      if (state.replyJumpGeneration !== generation) return;
      if (!data || !data.ok || !Array.isArray(data.messages) || !data.messages.length) {
        alert(t("reply.notFound", "The referenced message could not be found."));
        return;
      }
      extendReplyJumpLock(2600);
      state.messages = [];
      state.nextLocalMessageId = 1;
      data.messages.forEach(msg => addMessage(msg, {skipRender: true, suppressAutoFollow: true}));
      state.historyHasMore = !!data.hasBefore;
      state.historyHasAfter = !!data.hasAfter;
      state.historyOldestId = data.oldestId || (state.messages[0] && state.messages[0].id) || "";
      state.historyNewestId = data.newestId || (state.messages[state.messages.length - 1] && state.messages[state.messages.length - 1].id) || "";
      state.autoFollowLatest = false;
      state.explicitLatestFollowUntil = 0;
      state.forceLatestJumpUntil = 0;

      if (!renderReplyJumpFocusedRange(id, generation, {reason: "reply-jump-around"})) {
        alert(t("reply.notFound", "The referenced message could not be found."));
        return;
      }
      stabilizeReplyJumpTarget(id, generation, {lockMs: 1000, delays: [0, 50, 120, 260, 520]});
    } catch (e) {
      if (state.replyJumpGeneration === generation) alert(t("reply.notFound", "The referenced message could not be found."));
    }
  }

  function displaySender(msg) {
    if (!msg) return "";
    const source = String(msg.source || "").toLowerCase();
    const sender = String(msg.sender || "");
    const senderKey = sender.toLowerCase();
    if (source === "event" || source === "system" || source === "server") {
      if (senderKey === "server") return t("sender.server", "Server");
      if (senderKey === "command") return t("sender.command", "Command");
      if (senderKey === "system") return t("sender.system", "Server");
    }
    if (source === "discord" && senderKey === "discord") return t("sender.discord", "Discord");
    return sender;
  }

  function realSender(msg) {
    if (!msg) return "";
    const real = String(msg.realSender || msg.realName || "").trim();
    if (!real) return "";
    const shown = String(displaySender(msg) || "").trim();
    const shownPlain = plainMinecraftName(shown).trim();
    const realPlain = plainMinecraftName(real).trim();
    if (!shownPlain || realPlain.toLowerCase() === shownPlain.toLowerCase()) return "";
    return real;
  }

  function senderOriginalTitle(real) {
    return fmt("sender.originalId", "Original ID: {name}", {name: plainMinecraftName(real)});
  }

  function senderDisplayTitle(display) {
    return fmt("sender.displayName", "Display name: {name}", {name: plainMinecraftName(display)});
  }

  function preferredSenderText(display, real) {
    return state.senderIdentityMode === "real" && real ? real : display;
  }

  function senderNameHtml(display, real, source = "") {
    return minecraftNameHtml(preferredSenderText(display, real), shouldRenderMinecraftNameColors() && sourceMayRenderMinecraftNameColors(source));
  }

  function updateSenderIdentityElement(sender) {
    if (!sender) return;
    const real = sender.dataset.realSender || "";
    const display = sender.dataset.displaySender || sender.textContent || "";
    const showingReal = state.senderIdentityMode === "real" && !!real;
    const source = sender.dataset.source || "";
    sender.innerHTML = minecraftNameHtml(showingReal ? real : display, shouldRenderMinecraftNameColors() && sourceMayRenderMinecraftNameColors(source));
    sender.dataset.showingReal = showingReal ? "1" : "0";
    sender.title = showingReal ? senderDisplayTitle(display) : senderOriginalTitle(real);
    sender.setAttribute("aria-label", sender.title);
  }

  function senderIdentitySelector() {
    return ".kwc-sender[data-real-sender], .kwc-dm-identity[data-real-sender], [data-kwc-identity-toggle][data-real-sender]";
  }

  function applySenderIdentityMode() {
    document.querySelectorAll(senderIdentitySelector()).forEach(updateSenderIdentityElement);
  }

  function toggleSenderIdentityMode() {
    state.senderIdentityMode = state.senderIdentityMode === "real" ? "display" : "real";
    localStorage.setItem("kwc.senderIdentityMode", state.senderIdentityMode);
    applySenderIdentityMode();
  }

  let senderIdentityDelegationInstalled = false;

  function handleSenderIdentityToggleEvent(event) {
    const rawTarget = event && event.target;
    const target = rawTarget && rawTarget.closest ? rawTarget.closest(senderIdentitySelector()) : null;
    if (!target || !target.dataset || !target.dataset.realSender) return;
    if (event.type === "keydown" && event.key !== "Enter" && event.key !== " ") return;
    event.preventDefault();
    event.stopPropagation();
    if (typeof event.stopImmediatePropagation === "function") event.stopImmediatePropagation();
    toggleSenderIdentityMode();
  }

  function installSenderIdentityDelegation() {
    if (senderIdentityDelegationInstalled) return;
    senderIdentityDelegationInstalled = true;
    document.addEventListener("click", handleSenderIdentityToggleEvent, true);
    document.addEventListener("keydown", handleSenderIdentityToggleEvent, true);
  }

  function installSenderIdentityToggle(root) {
    installSenderIdentityDelegation();
    if (!root) return;
    const targets = root.matches && root.matches(senderIdentitySelector())
      ? [root]
      : Array.from(root.querySelectorAll(senderIdentitySelector()));
    targets.forEach(sender => {
      if (sender.dataset.identityToggleInstalled !== "1") {
        sender.dataset.identityToggleInstalled = "1";
        sender.addEventListener("click", event => {
          event.preventDefault();
          event.stopPropagation();
          if (typeof event.stopImmediatePropagation === "function") event.stopImmediatePropagation();
          toggleSenderIdentityMode();
        });
        sender.addEventListener("keydown", event => {
          if (event.key !== "Enter" && event.key !== " ") return;
          event.preventDefault();
          event.stopPropagation();
          if (typeof event.stopImmediatePropagation === "function") event.stopImmediatePropagation();
          toggleSenderIdentityMode();
        });
      }
      updateSenderIdentityElement(sender);
    });
  }

  function installDirectMessageIdentityToggleGuard(root) {
    if (!root || root.dataset.dmIdentityToggleGuard === "1") return;
    root.dataset.dmIdentityToggleGuard = "1";
    root.addEventListener("click", handleSenderIdentityToggleEvent, true);
    root.addEventListener("keydown", handleSenderIdentityToggleEvent, true);
  }

  function displaySource(msg) {
    const source = String(msg && msg.source || "").toLowerCase();
    return source ? t("source." + source, source) : "";
  }

  function messageServerInfo(msg) {
    const id = String(msg && msg.originServerId || "").trim();
    const name = String(msg && msg.originServerName || "").trim();
    const label = name || id;
    const title = name && id && name !== id ? name + " (" + id + ")" : label;
    return {id, name, label, title};
  }

  function isLocalServerMessage(msg) {
    const server = messageServerInfo(msg);
    if (!server.id && !server.name) return true;
    const currentId = String(state.config && state.config.serverRelayServerId || "").trim();
    if (currentId && server.id) return currentId.toLowerCase() === server.id.toLowerCase();
    const currentName = String(state.config && state.config.serverRelayServerName || "").trim();
    return !!(currentName && server.name && currentName.toLowerCase() === server.name.toLowerCase());
  }

  function serverBadgeHtml(msg) {
    const server = messageServerInfo(msg);
    if (!server.label || isLocalServerMessage(msg)) return "";
    const key = server.id || server.label;
    const palette = [198, 28, 132, 278, 52, 342, 168, 225, 12, 102, 310, 74];
    let hash = 0;
    for (let i = 0; i < key.length; i++) hash = ((hash * 31) + key.charCodeAt(i)) >>> 0;
    const hue = palette[hash % palette.length];
    return `<span class="kwc-server-badge" data-server-id="${esc(server.id)}" style="--kwc-server-hue:${hue}" title="${esc(server.title)}">${esc(server.label)}</span><span class="kwc-meta-sep" aria-hidden="true">·</span>`;
  }

  function publicMessageDirectMessageTarget(msg) {
    const uuid = String(msg && msg.playerUuid || "").trim();
    const source = String(msg && msg.source || "").trim().toLowerCase();
    if (!uuid || (source !== "game" && source !== "web")) return null;
    const displayName = String(displaySender(msg) || "").trim();
    const username = String(realSender(msg) || "").trim();
    const server = messageServerInfo(msg);
    const remote = !isLocalServerMessage(msg) && !!server.id;
    const baseLabel = displayName || username || uuid;
    const label = remote && server.label ? baseLabel + " · " + server.label : baseLabel;
    return {
      uuid,
      label,
      displayName,
      username,
      remote,
      serverId: remote ? server.id : "",
      serverName: remote ? (server.name || server.label || server.id) : ""
    };
  }

  function directMessageTargetDataAttributes(target) {
    if (!target) return "";
    return [
      `data-dm-target-uuid="${esc(target.uuid || "")}"`,
      `data-dm-target-label="${esc(target.label || "")}"`,
      `data-dm-target-display-name="${esc(target.displayName || "")}"`,
      `data-dm-target-username="${esc(target.username || "")}"`,
      `data-dm-target-remote="${target.remote ? "1" : "0"}"`,
      `data-dm-target-server-id="${esc(target.serverId || "")}"`,
      `data-dm-target-server-name="${esc(target.serverName || "")}"`
    ].join(" ");
  }

  function publicMessageDirectMessageTargetFromElement(button, messageElement, fallbackMsg) {
    const root = messageElement || (button && button.closest ? button.closest(".kwc-msg") : null);
    const fromMessage = publicMessageDirectMessageTarget(fallbackMsg);
    const read = (buttonKey, rootKey, fallback) => {
      const buttonValue = button && button.dataset ? String(button.dataset[buttonKey] || "").trim() : "";
      if (buttonValue) return buttonValue;
      const rootValue = root && root.dataset ? String(root.dataset[rootKey] || "").trim() : "";
      return rootValue || String(fallback || "").trim();
    };
    const uuid = read("dmTargetUuid", "playerUuid", fromMessage && fromMessage.uuid);
    if (!uuid) return null;
    const serverId = read("dmTargetServerId", "originServerId", fromMessage && fromMessage.serverId);
    const serverName = read("dmTargetServerName", "originServerName", fromMessage && fromMessage.serverName);
    const displayName = read("dmTargetDisplayName", "displaySender", fromMessage && fromMessage.displayName);
    const username = read("dmTargetUsername", "realSender", fromMessage && fromMessage.username);
    const remoteValue = button && button.dataset && button.dataset.dmTargetRemote !== undefined
      ? String(button.dataset.dmTargetRemote)
      : (root && root.dataset && root.dataset.remoteMessage !== undefined ? String(root.dataset.remoteMessage) : "");
    const remote = remoteValue ? remoteValue === "1" : (!!serverId && !(fallbackMsg && isLocalServerMessage(fallbackMsg)));
    const baseLabel = read("dmTargetLabel", "dmTargetLabel", fromMessage && fromMessage.label) || displayName || username || uuid;
    return {
      uuid,
      label: baseLabel,
      displayName,
      username,
      remote,
      serverId: remote ? serverId : "",
      serverName: remote ? (serverName || serverId) : ""
    };
  }

  function messageOriginSourceHtml(msg) {
    const content = `${serverBadgeHtml(msg)}<span class="kwc-source-label">${esc(displaySource(msg))}</span>`;
    const target = publicMessageDirectMessageTarget(msg);
    if (!target || !state.directMessageEnabled) return content;
    const player = directMessagePlainLabel(target.label) || target.uuid;
    const title = fmt("dm.openForPlayer", "Open direct message with {player}", {player});
    return `<button type="button" class="kwc-message-dm-target" ${directMessageTargetDataAttributes(target)} title="${esc(title)}" aria-label="${esc(title)}">${content}</button>`;
  }

  async function openDirectMessageForTarget(target) {
    if (!target || !target.uuid || !state.directMessageEnabled) return;
    if (!state.token) {
      openLoginModal();
      return;
    }

    const wasOpen = state.dmModalOpen;
    await openDirectMessageModal();
    if (!state.dmModalOpen) return;
    if (wasOpen) await loadDirectMessageThreads(true);

    closeDirectMessagePlayerSearch();
    closeDirectMessageEmojiPanel();
    state.dmAuditMode = false;
    state.dmAuditThread = null;

    const uuid = target.uuid.toLowerCase();
    const serverId = String(target.serverId || "").trim().toLowerCase();
    const existing = (state.dmThreads || []).find(thread => {
      if (target.remote) {
        return String(thread && thread.otherServerId || "").trim().toLowerCase() === serverId
          && String(thread && thread.otherPlayerUuid || "").trim().toLowerCase() === uuid;
      }
      return !thread.otherRemote
        && String(thread && (thread.otherPlayerUuid || thread.otherUuid) || "").trim().toLowerCase() === uuid;
    });
    if (existing && existing.id) {
      state.dmDraftTarget = null;
      state.dmActiveThreadId = existing.id;
      updateDirectMessageComposeControls();
      renderDirectMessageThreads();
      updateDirectMessageViewMode();
      await loadDirectMessageMessages(existing.id);
    } else {
      state.dmActiveThreadId = "";
      state.dmDraftTarget = {
        uuid: target.uuid,
        label: target.label,
        displayName: target.displayName,
        username: target.username,
        remote: target.remote,
        serverId: target.serverId,
        serverName: target.serverName
      };
      updateDirectMessageComposeControls();
      renderDirectMessageThreads();
      renderDirectMessageHeader(target.label);
      renderDirectMessageMessages([]);
      updateDirectMessageViewMode();
    }

    const input = document.getElementById("kwc-dm-input");
    if (input) {
      setActiveComposeInput(input);
      input.focus();
    }
  }

  async function openDirectMessageForPublicMessage(msg) {
    return openDirectMessageForTarget(publicMessageDirectMessageTarget(msg));
  }


  async function loadLang() {
    try {
      const lang = String(state.selectedLanguage || localStorage.getItem("kwc.language") || "").trim();
      const data = await api("/lang" + (lang ? "?lang=" + encodeURIComponent(lang) : ""));
      if (data && data.ok && data.strings) {
        state.lang = data.strings;
        state.availableLanguages = Array.isArray(data.available) ? data.available.map(String) : state.availableLanguages;
      }
    } catch (e) {
      console.warn("KOKOTO WebChat lang failed", e);
    }
  }

  function languageLabel(code) {
    const labels = {
      "": t("preferences.languageDefault", "Default"),
      "ko-KR": "Korean",
      "en-US": "English",
      "ja-JP": "Japanese",
      "zh-CN": "Simplified Chinese"
    };
    return labels[String(code || "")] || String(code || "");
  }

  function savedUserLanguage() {
    return String(localStorage.getItem("kwc.language") || "");
  }

  function selectedLocale() {
    const lang = String(state.selectedLanguage || localStorage.getItem("kwc.language") || (state.config && state.config.language) || navigator.language || "en-US").trim();
    return lang || "en-US";
  }

  function configuredTimeZone() {
    const raw = String((state.config && state.config.uiTimeZone) || "local").trim();
    if (!raw || raw.toLowerCase() === "local" || raw.toLowerCase() === "browser" || raw.toLowerCase() === "device") {
      return "";
    }
    return raw;
  }

  function timeFormatOptions(options) {
    const out = Object.assign({}, options || {});
    const tz = configuredTimeZone();
    if (tz) out.timeZone = tz;
    return out;
  }

  function formatMessageTimeShort(value) {
    const d = new Date(value || Date.now());
    const locale = selectedLocale();
    try {
      return d.toLocaleTimeString(locale, timeFormatOptions({hour: "2-digit", minute: "2-digit"}));
    } catch (_) {
      return d.toLocaleTimeString(undefined, {hour: "2-digit", minute: "2-digit"});
    }
  }

  function formatMessageTimeFull(value) {
    const d = new Date(value || Date.now());
    const locale = selectedLocale();
    const baseOpts = {year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit"};
    const opts = timeFormatOptions(baseOpts);
    try {
      return d.toLocaleString(locale, opts);
    } catch (_) {
      return d.toLocaleString(undefined, baseOpts);
    }
  }

  function formatMessageTime(value) {
    return state.timeDisplayMode === "full" ? formatMessageTimeFull(value) : formatMessageTimeShort(value);
  }

  function timeToggleTitle(value) {
    return state.timeDisplayMode === "full" ? formatMessageTimeShort(value) : formatMessageTimeFull(value);
  }

  function updateTimeElement(el) {
    if (!el) return;
    const raw = Number(el.dataset.time || 0) || Date.now();
    el.textContent = formatMessageTime(raw);
    el.title = timeToggleTitle(raw);
    el.setAttribute("aria-label", el.title);
    el.dataset.timeMode = state.timeDisplayMode;
  }

  function applyTimeDisplayMode() {
    document.querySelectorAll(".kwc-time[data-time]").forEach(updateTimeElement);
  }

  function toggleTimeDisplayMode() {
    state.timeDisplayMode = state.timeDisplayMode === "full" ? "short" : "full";
    localStorage.setItem("kwc.timeDisplayMode", state.timeDisplayMode);
    applyTimeDisplayMode();
  }

  function installTimeDisplayDelegation() {
    if (document.__kwcTimeDisplayDelegationInstalled) return;
    document.__kwcTimeDisplayDelegationInstalled = true;

    const timeTarget = event => {
      const target = event.target && event.target.closest
        ? event.target.closest(".kwc-time[data-time]")
        : null;
      if (!target) return null;
      const root = document.getElementById("kwc-root");
      const dmModal = document.querySelector(".kwc-dm-modal-backdrop");
      const pinnedModal = document.querySelector(".kwc-pinned-backdrop");
      return (!root || root.contains(target)
        || (dmModal && dmModal.contains(target))
        || (pinnedModal && pinnedModal.contains(target))) ? target : null;
    };

    document.addEventListener("click", event => {
      const target = timeTarget(event);
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      toggleTimeDisplayMode();
    }, true);

    document.addEventListener("keydown", event => {
      if (event.key !== "Enter" && event.key !== " ") return;
      const target = timeTarget(event);
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      toggleTimeDisplayMode();
    }, true);
  }

  function installTimeToggle(root) {
    if (!root) return;
    installTimeDisplayDelegation();
    root.querySelectorAll(".kwc-time[data-time]").forEach(updateTimeElement);
  }

  async function setUserLanguage(value) {
    const lang = String(value || "").trim();
    state.selectedLanguage = lang;
    if (lang) localStorage.setItem("kwc.language", lang);
    else localStorage.removeItem("kwc.language");
    await loadLang();
    refreshStaticLabels();
    refreshRenderedMessagesForLocale();
    if (state.prefsModalOpen) {
      openUserPreferencesModal(true);
    }
    scheduleVirtualRender({preserveScroll: true, stickToBottom: false, deferDuringScroll: false});
  }

  async function resetUserLanguage() {
    state.selectedLanguage = "";
    localStorage.removeItem("kwc.language");
    await loadLang();
    refreshStaticLabels();
    refreshRenderedMessagesForLocale();
    if (state.prefsModalOpen) {
      openUserPreferencesModal(true);
    }
    scheduleVirtualRender({preserveScroll: true, stickToBottom: false, deferDuringScroll: false});
  }



  function postFrame(type, payload = {}) {
    try {
      window.parent.postMessage(Object.assign({source: "KWC", type}, payload), "*");
    } catch (_) {}
  }

  function trustedParentMessageEvent(event) {
    try {
      if (!event) return false;
      const expected = (window.parent && window.parent !== window) ? window.parent : window;
      return event.source === expected;
    } catch (_) {
      return false;
    }
  }

  function clampNumber(value, min, max, fallback) {
    value = Number(value);
    if (!Number.isFinite(value)) value = fallback;
    if (Number.isFinite(min)) value = Math.max(min, value);
    if (Number.isFinite(max) && max > 0) value = Math.min(max, value);
    return Math.round(value);
  }

  function resizeBounds() {
    const c = state.config || {};
    const minW = Math.max(240, Number(c.uiMinWidth) || 280);
    const minH = Math.max(180, Number(c.uiMinHeight) || 240);
    const rawMaxW = Number(c.uiMaxWidth);
    const rawMaxH = Number(c.uiMaxHeight);
    const maxW = Number.isFinite(rawMaxW) && rawMaxW > 0 ? Math.max(minW, rawMaxW) : Infinity;
    const maxH = Number.isFinite(rawMaxH) && rawMaxH > 0 ? Math.max(minH, rawMaxH) : Infinity;
    return {minW, minH, maxW, maxH};
  }

  function sanitizeSavedWindowSize(width, height) {
    if (!Number.isFinite(width) || !Number.isFinite(height)) return null;
    if (width < 120 || height < 120) return null;
    return {width, height};
  }

  function applyWindowSizeConfig() {
    const c = state.config || {};
    const b = resizeBounds();
    let width = Number(c.uiDefaultWidth) || 372;
    let height = Number(c.uiDefaultHeight) || 462;

    if (c.uiRememberWindowSize !== false) {
      const savedW = Number(localStorage.getItem("kwc.windowWidth"));
      const savedH = Number(localStorage.getItem("kwc.windowHeight"));
      const saved = sanitizeSavedWindowSize(savedW, savedH);
      if (saved) {
        width = saved.width;
        height = saved.height;
      }
    }

    state.frameNormalWidth = clampNumber(width, b.minW, b.maxW, 372);
    state.frameNormalHeight = clampNumber(height, b.minH, b.maxH, 462);
  }

  function saveWindowSize() {
    const c = state.config || {};
    if (c.uiRememberWindowSize === false) return;
    localStorage.setItem("kwc.windowWidth", String(state.frameNormalWidth));
    localStorage.setItem("kwc.windowHeight", String(state.frameNormalHeight));
  }


  function updateResizeLockButton() {
    const btn = document.getElementById("kwc-resize-lock");
    const root = document.getElementById("kwc-root");
    const canResize = !!(state.config && state.config.uiResizable);
    const visible = canResize && !state.minimized && !guestChatHidden();
    if (root) {
      root.classList.toggle("kwc-resizable", canResize && !state.resizeLocked);
      root.classList.toggle("kwc-resize-locked", !!state.resizeLocked);
    }
    if (!btn) return;
    btn.classList.toggle("kwc-hidden", !visible);
    btn.setAttribute("aria-pressed", state.resizeLocked ? "true" : "false");
    btn.textContent = state.resizeLocked ? "🔒" : "⇲";
    btn.title = state.resizeLocked ? t("button.resizeUnlock", "Unlock resize") : t("button.resizeLock", "Lock resize");
    btn.setAttribute("aria-label", btn.title);
  }

  function toggleResizeLocked() {
    state.resizeLocked = !state.resizeLocked;
    localStorage.setItem("kwc.resizeLocked", state.resizeLocked ? "1" : "0");
    if (state.resizeStart) state.resizeStart = null;
    updateResizeLockButton();
    updateFrameSize();
  }

  function updateFrameSize() {
    if (state.isPip) {
      const title = document.querySelector(".kwc-title");
      if (title) title.textContent = t("title.full", "KOKOTO WebChat");
      return;
    }
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");
    const loginOnly = guestChatHidden() && !state.minimized;
    postFrame("resize", {
      minimized: state.minimized,
      height: state.minimized ? state.frameMinimizedHeight : state.frameNormalHeight,
      width: state.minimized ? 124 : state.frameNormalWidth,
      resizable: !!(state.config && state.config.uiResizable && !state.resizeLocked),
      minW: state.config ? state.config.uiMinWidth : 280,
      minH: state.config ? state.config.uiMinHeight : 240,
      maxW: state.config ? state.config.uiMaxWidth : 640,
      maxH: state.config ? state.config.uiMaxHeight : 720
    });
    updateResizeLockButton();
  }


  function installMapPointerRelayBridge() {
    if (window.__kwcMapPointerRelayBridgeInstalled) return;
    window.__kwcMapPointerRelayBridgeInstalled = true;

    const relay = (eventName, event) => {
      try {
        const frame = window.frameElement;
        const fr = frame && frame.getBoundingClientRect ? frame.getBoundingClientRect() : {left: 0, top: 0};
        postFrame("mapPointerRelay", {
          eventName,
          clientX: Number(fr.left || 0) + Number(event.clientX || 0),
          clientY: Number(fr.top || 0) + Number(event.clientY || 0),
          screenX: Number(event.screenX || 0),
          screenY: Number(event.screenY || 0),
          button: Number.isFinite(Number(event.button)) ? Number(event.button) : 0,
          buttons: Number.isFinite(Number(event.buttons)) ? Number(event.buttons) : 0,
          pointerId: event.pointerId,
          pointerType: event.pointerType || "mouse",
          isPrimary: event.isPrimary !== false,
          ctrlKey: !!event.ctrlKey,
          shiftKey: !!event.shiftKey,
          altKey: !!event.altKey,
          metaKey: !!event.metaKey
        });
      } catch (_) {}
    };

    if (window.PointerEvent) {
      document.addEventListener("pointermove", event => {
        if ((Number(event.buttons) & 1) === 1) relay("pointermove", event);
      }, {capture: true, passive: true});
      document.addEventListener("pointerup", event => relay("pointerup", event), {capture: true, passive: true});
      document.addEventListener("pointercancel", event => relay("pointercancel", event), {capture: true, passive: true});
    }

    document.addEventListener("mousemove", event => {
      if ((Number(event.buttons) & 1) === 1) relay("mousemove", event);
    }, {capture: true, passive: true});
    document.addEventListener("mouseup", event => relay("mouseup", event), {capture: true, passive: true});
  }

  async function refreshParentUserPreferences(profileStatus = "") {
    if (serverUserProfilesActive()) await loadAccountProfiles();
    const payload = buildUserPreferencesPayload();
    if (profileStatus) payload.profileStatus = String(profileStatus);
    postFrame("openUserPreferences", payload);
  }

  function installParentResizeBridge() {
    window.addEventListener("message", event => {
      if (!trustedParentMessageEvent(event)) return;
      const data = event.data || {};
      if (!data || data.source !== "KWCParent") return;
      if (data.type === "parentResized") {
        const b = resizeBounds();
        state.frameNormalWidth = clampNumber(data.width, b.minW, b.maxW, state.frameNormalWidth);
        state.frameNormalHeight = clampNumber(data.height, b.minH, b.maxH, state.frameNormalHeight);
        // Do not write localStorage on every resize frame; the parent frame already
        // persists the final size at resize end.
      } else if (data.type === "notificationAction") {
        (async () => {
          const action = String(data.action || "");
          if (action === "togglePage") {
            if (notificationsEnabledLocal()) setNotificationsEnabledLocal(false);
            else setNotificationsEnabledLocal(true);
          } else if (action === "setPage") {
            setNotificationsEnabledLocal(data.enabled !== false);
            if (data.enabled === false) await disableWebPush();
            else if (canUseWebPush()) await enableWebPush();
          } else if (action === "testPage") {
            setNotificationsEnabledLocal(true);
            if (notificationUsesMobilePushUi() && canUseWebPush()) await testWebPush();
            else showBrowserNotification(configuredNotificationTitle(), t("preferences.notificationsTest", "Test notification"), {tag: "kwc-test", force: true});
          } else if (action === "setNotificationOptions") {
            const opts = data.options && typeof data.options === "object" ? data.options : {};
            const hasSystemMode = Object.prototype.hasOwnProperty.call(opts, "systemMode");
            if (hasSystemMode) setNotificationSystemMode(opts.systemMode);
            Object.keys(opts).forEach(name => {
              if (name === "systemMode") return;
              // The legacy system boolean is only the on/off companion for
              // notifySystemMode. When a concrete mode is sent, applying the
              // boolean afterwards would change join-leave back to all.
              if (hasSystemMode && name === "system") return;
              setNotificationOption(name, opts[name] === true);
            });
            if (notificationsEnabledLocal()) await enableWebPush();
          } else if (action === "setKeywords") {
            setNotificationKeywordsText(data.keywords || "");
            if (notificationsEnabledLocal()) await enableWebPush();
          } else if (action === "applyKeywords") {
            const keywordText = String(data.keywords || "");
            setNotificationKeywordsText(keywordText);
            if (keywordText.trim() && notificationServerAllows("keywords")) setNotificationOption("keywords", true);
            if (notificationsEnabledLocal()) await enableWebPush();
          } else if (action === "enableWebPush") {
            const opts = data.options && typeof data.options === "object" ? data.options : null;
            if (opts) {
              const hasSystemMode = Object.prototype.hasOwnProperty.call(opts, "systemMode");
              if (hasSystemMode) setNotificationSystemMode(opts.systemMode);
              Object.keys(opts).forEach(name => {
                if (name === "systemMode") return;
                if (hasSystemMode && name === "system") return;
                setNotificationOption(name, opts[name] === true);
              });
            }
            await enableWebPush();
          } else if (action === "disableWebPush") {
            await disableWebPush();
          } else if (action === "testWebPush") {
            await testWebPush();
          }
          postFrame("notificationStatus", {
            notificationsStatus: notificationStatusText({}),
            webPushStatus: webPushStatusText({}),
            notificationsEnabledLocal: notificationsEnabledLocal(),
            webPushEnabledLocal: notificationsEnabledLocal() && canUseWebPush(),
            webPushAvailable: canUseWebPush(),
            notificationOptions: currentNotificationOptions(),
            notificationOptionsAllowed: currentNotificationOptionsAllowed(),
            notificationKeywords: notificationKeywordsText()
          });
        })();
      } else if (data.type === "userProfileSave") {
        (async () => {
          try {
            const profile = await saveAccountProfile(data.id || "", data.name || "");
            await refreshParentUserPreferences((t("preferences.presetSaved", "Saved.")) + " " + String(profile.name || ""));
          } catch (e) {
            await refreshParentUserPreferences((t("preferences.presetSaveFailed", "Save failed.")) + " " + String(e && e.message || ""));
          }
        })();
      } else if (data.type === "userProfileLoad") {
        const id = String(data.id || "");
        const profile = (state.accountProfiles || []).find(item => String(item && item.id || "") === id);
        if (profile) {
          applyAccountProfile(profile);
          refreshParentUserPreferences((t("preferences.presetLoaded", "Loaded.")) + " " + String(profile.name || "")).catch(() => {});
        }
      } else if (data.type === "userProfileDelete") {
        (async () => {
          try {
            const id = String(data.id || "");
            const profile = (state.accountProfiles || []).find(item => String(item && item.id || "") === id);
            const name = profile && profile.name || "";
            await deleteAccountProfile(id);
            await refreshParentUserPreferences((t("preferences.presetDeleted", "Deleted.")) + (name ? " " + name : ""));
          } catch (e) {
            await refreshParentUserPreferences((t("preferences.presetSaveFailed", "Save failed.")) + " " + String(e && e.message || ""));
          }
        })();
      } else if (data.type === "userProfileExport") {
        (async () => {
          try {
            const exported = await fetchAccountProfileExport(data.id || "");
            postFrame("userProfileExportData", {json: exported.json, name: exported.name});
          } catch (e) {
            postFrame("userProfileStatus", {message: (t("preferences.presetExportFailed", "Export failed.")) + " " + String(e && e.message || "")});
          }
        })();
      } else if (data.type === "userProfileImport") {
        (async () => {
          try {
            const profile = await importAccountProfileJson(data.profileJson || "");
            await refreshParentUserPreferences((t("preferences.presetSaved", "Saved.")) + " " + String(profile.name || ""));
          } catch (e) {
            await refreshParentUserPreferences((t("preferences.presetImportFailed", "Import failed.")) + " " + String(e && e.message || ""));
          }
        })();
      } else if (data.type === "userPreferencesSet") {
        const persist = data.final !== false;
        if (data.key === "opacity") setUserOpacity(data.value, persist);
        else if (data.key === "fontSize") setUserFontSize(data.value, persist);
        else if (data.key === "fontFamily") setUserFontFamily(data.value);
        else if (data.key === "textColor") setUserTextColor(data.value);
        else if (data.key === "uiTextColor") setUserUiTextColor(data.value);
        else if (data.key === "textShadowMode") setUserTextShadowMode(data.value);
        else if (data.key === "textShadowCustom") setUserTextShadowCustom(data.value);
        else if (data.key === "backgroundColor") setUserBackgroundColor(data.value);
        else if (data.key === "inputBackgroundColor") setUserInputBackgroundColor(data.value);
        else if (data.key === "language") setUserLanguage(data.value);
        else if (data.key === "theme") setUserTheme(data.value);
      } else if (data.type === "userPreferencesApplyStorage") {
        applyChatSettingPresetStorage(data.storage || {});
        applyFontSizeConfig();
        applyThemeConfig();
        refreshRenderedMessagesForLocale();
        scheduleVirtualRender({preserveScroll: true, stickToBottom: false, deferDuringScroll: false});
      } else if (data.type === "userPreferencesReset") {
        resetUserOpacity();
        resetUserFontSize();
        resetUserFontFamily();
        resetUserTextColor();
        resetUserUiTextColor();
        resetUserTextShadowMode();
        resetUserTextShadowCustom();
        resetUserBackgroundColor();
        resetUserInputBackgroundColor();
        resetUserLanguage();
      } else if (data.type === "userPreferencesClosed") {
        state.prefsModalOpen = false;
      } else if (data.type === "pipOpened") {
        // The original chat window should behave exactly as if the user pressed
        // the minimize button once a duplicate PIP window has actually opened.
        // This applies to both BlueMap addon and standalone pages.
        protectHistoryEndNotice("pip-opened", 7000);
        if (!state.isPip && !state.minimized) toggleMin();
        state.forceHistoryEndNoticeUntil = Math.max(Number(state.forceHistoryEndNoticeUntil || 0), Date.now() + 7000);
        scheduleScrollAffordanceRefresh("pip-opened");
      } else if (data.type === "pipClosed") {
        // When the PIP window is closed, restore the original chat window just
        // like pressing the minimized + button. This keeps BlueMap addon and
        // standalone behavior consistent with common PIP workflows.
        protectHistoryEndNotice("pip-closed", 8000);
        if (!state.isPip && state.minimized) toggleMin();
        state.forceHistoryEndNoticeUntil = Math.max(Number(state.forceHistoryEndNoticeUntil || 0), Date.now() + 8000);
        scheduleScrollAffordanceRefresh("pip-closed");
      } else if (data.type === "pipResult") {
        state.lastPipResultAt = Date.now();
      }
    });
  }

  function installFrameFocusBridge() {
    window.addEventListener("focus", () => postFrame("active", {active: true}), true);
    window.addEventListener("blur", () => postFrame("active", {active: false}), true);
    document.addEventListener("focusin", () => postFrame("active", {active: true}), true);
    document.addEventListener("focusout", () => {
      setTimeout(() => {
        const active = document.activeElement && document.activeElement !== document.body;
        postFrame("active", {active: !!active});
      }, 0);
    }, true);
  }

  function installDrag(root) {
    if (state.isPip) return;
    const header = root.querySelector(".kwc-header");
    if (!header) return;

    let active = false;
    let lastX = 0;
    let lastY = 0;
    const pointFromEvent = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return {
        clientX: Number(src.clientX) || 0,
        clientY: Number(src.clientY) || 0,
        screenX: Number(src.screenX) || Number(src.clientX) || 0,
        screenY: Number(src.screenY) || Number(src.clientY) || 0
      };
    };

    const begin = event => {
      const target = event.target;
      if (target && target.closest && target.closest("button, input, select, textarea")) return;

      const p = pointFromEvent(event);
      active = true;
      state.dragStart = {x: p.clientX, y: p.clientY};
      lastX = p.clientX;
      lastY = p.clientY;

      postFrame("dragStart", {screenX: p.screenX, screenY: p.screenY});
      event.preventDefault();
      event.stopPropagation();
    };

    const move = event => {
      if (!active || !state.dragStart) return;

      const p = pointFromEvent(event);
      const dx = p.clientX - lastX;
      const dy = p.clientY - lastY;
      lastX = p.clientX;
      lastY = p.clientY;

      postFrame("dragMove", {dx, dy, screenX: p.screenX, screenY: p.screenY});
      event.preventDefault();
      event.stopPropagation();
    };

    const endDrag = event => {
      if (!active) return;
      active = false;
      state.dragStart = null;
      postFrame("dragEnd", {});
      event.preventDefault();
      event.stopPropagation();
    };

    if (window.PointerEvent) {
      header.addEventListener("pointerdown", begin, {capture: true});
      document.addEventListener("pointermove", move, {capture: true});
      document.addEventListener("pointerup", endDrag, {capture: true});
      document.addEventListener("pointercancel", endDrag, {capture: true});
    } else {
      header.addEventListener("touchstart", begin, {capture: true, passive: false});
      document.addEventListener("touchmove", move, {capture: true, passive: false});
      document.addEventListener("touchend", endDrag, {capture: true, passive: false});
      document.addEventListener("touchcancel", endDrag, {capture: true, passive: false});
      header.addEventListener("mousedown", begin, {capture: true});
      document.addEventListener("mousemove", move, {capture: true});
      document.addEventListener("mouseup", endDrag, {capture: true});
    }
  }

  function randomGuestName(prefix) {
    return (prefix || "Guest-") + Math.floor(1000 + Math.random() * 9000);
  }

  function generatedGuestNameMatches(name, prefix) {
    name = String(name || "");
    prefix = String(prefix || "Guest-");
    if (!name.startsWith(prefix)) return false;
    return /^\d{4,8}$/.test(name.slice(prefix.length));
  }

  function ensureGuestNameForConfig(force = false) {
    const prefix = (state.config && state.config.guestNamePrefix) || "Guest-";
    const customDisabled = state.config && state.config.guestAllowCustomName === false;
    const stored = String(state.guestName || localStorage.getItem("kwc.guestName") || "").trim();
    const mustRegenerate = force || !stored || (customDisabled && !generatedGuestNameMatches(stored, prefix));
    if (mustRegenerate) {
      state.guestName = randomGuestName(prefix);
      localStorage.setItem("kwc.guestName", state.guestName);
      return state.guestName;
    }
    state.guestName = customDisabled ? stored : limitGuestNameCodePoints(stored);
    localStorage.setItem("kwc.guestName", state.guestName);
    return state.guestName;
  }

  function limitGuestNameCodePoints(value) {
    return Array.from(String(value || "")).slice(0, 16).join("");
  }

  function currentGuestNameForSubmit() {
    if (state.config && state.config.guestAllowCustomName === false) {
      return ensureGuestNameForConfig();
    }
    const guestInput = document.getElementById("kwc-guest-name");
    state.guestName = limitGuestNameCodePoints((guestInput && guestInput.value.trim()) || state.guestName || ensureGuestNameForConfig());
    localStorage.setItem("kwc.guestName", state.guestName);
    return state.guestName;
  }

  function installResize(root) {
    const handle = root.querySelector("#kwc-resize-handle");
    if (!handle) return;

    const pointFromEvent = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return {
        clientX: Number(src.clientX) || 0,
        clientY: Number(src.clientY) || 0
      };
    };

    const begin = event => {
      if (state.minimized || state.resizeLocked || !state.config || !state.config.uiResizable) return;
      if (state.resizeStart) return;

      const p = pointFromEvent(event);
      if (state.isPip) {
        const rect = root.getBoundingClientRect();
        state.resizeStart = {
          pip: true,
          x: p.clientX,
          y: p.clientY,
          width: Math.max(240, Number(window.outerWidth) || Number(window.innerWidth) || Number(rect.width) || state.frameNormalWidth),
          height: Math.max(180, Number(window.outerHeight) || Number(window.innerHeight) || Number(rect.height) || state.frameNormalHeight),
          bounds: resizeBounds()
        };
      } else {
        state.resizeStart = true;
        postFrame("resizeStart", {clientX: p.clientX, clientY: p.clientY});
      }
      event.preventDefault();
      event.stopPropagation();
    };

    const move = event => {
      if (!state.resizeStart) return;
      const p = pointFromEvent(event);
      if (state.resizeStart && state.resizeStart.pip) {
        const r = state.resizeStart;
        const b = r.bounds || resizeBounds();
        const nextW = clampNumber(r.width + (p.clientX - r.x), b.minW, b.maxW, r.width);
        const nextH = clampNumber(r.height + (p.clientY - r.y), b.minH, b.maxH, r.height);
        try {
          if (typeof window.resizeTo === "function") window.resizeTo(nextW, nextH);
        } catch (_) {}
      } else {
        postFrame("resizeMove", {clientX: p.clientX, clientY: p.clientY});
      }
      event.preventDefault();
      event.stopPropagation();
    };

    const end = event => {
      if (!state.resizeStart) return;
      const wasPip = !!(state.resizeStart && state.resizeStart.pip);
      state.resizeStart = null;
      if (!wasPip) {
        postFrame("resizeEnd", {});
        saveWindowSize();
      }
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
    };

    if (window.PointerEvent) {
      handle.addEventListener("pointerdown", begin, {capture: true});
      document.addEventListener("pointermove", move, {capture: true});
      document.addEventListener("pointerup", end, {capture: true});
      document.addEventListener("pointercancel", end, {capture: true});
    } else {
      handle.addEventListener("mousedown", begin, {capture: true});
      handle.addEventListener("touchstart", begin, {capture: true, passive: false});
      document.addEventListener("mousemove", move, {capture: true});
      document.addEventListener("mouseup", end, {capture: true});
      document.addEventListener("touchmove", move, {capture: true, passive: false});
      document.addEventListener("touchend", end, {capture: true, passive: false});
      document.addEventListener("touchcancel", end, {capture: true, passive: false});
    }
  }


  function installModalAffordanceObserver() {
    if (state.modalAffordanceObserverInstalled) return;
    state.modalAffordanceObserverInstalled = true;
    try {
      const observer = new MutationObserver(mutations => {
        for (const m of mutations || []) {
          const nodes = [...Array.from(m.addedNodes || []), ...Array.from(m.removedNodes || [])];
          if (nodes.some(n => n && n.nodeType === 1 && n.classList && (n.classList.contains("kwc-modal-backdrop") || n.classList.contains("kwc-modal-wrap")))) {
            scheduleScrollAffordanceRefresh("modal-change");
            break;
          }
        }
      });
      observer.observe(document.body, {childList: true});
      state.modalAffordanceObserver = observer;
    } catch (_) {}
  }

  function standalonePipRelaySupported() {
    return typeof BroadcastChannel !== "undefined";
  }

  function closeStandalonePipRelay() {
    if (!standalonePipRelay) return;
    try { standalonePipRelay.close(); } catch (_) {}
    standalonePipRelay = null;
  }

  function handleMirroredStreamEvent(type, rawData) {
    const eventType = String(type || "");
    try {
      if (eventType === "ready") {
        state.streamLastOpenAt = Date.now();
        const status = document.getElementById("kwc-status");
        if (status && !state.token) status.textContent = t("status.guest", "guest");
        updateLoginState();
        return;
      }
      if (eventType === "reconnecting") {
        markStreamStatusReconnecting();
        return;
      }
      if (eventType === "chat") {
        if (guestChatHidden()) return;
        const msg = JSON.parse(rawData || "{}");
        if (state.historyHasAfter) {
          refreshScrollAffordances(document.getElementById("kwc-messages"));
          return;
        }
        addMessage(msg);
        return;
      }
      if (eventType === "delete") {
        markMessageDeleted(JSON.parse(rawData || "{}").id);
        return;
      }
      if (eventType === "dm") {
        const data = JSON.parse(rawData || "{}");
        if (!state.directMessageEnabled || !state.token) return;
        loadDirectMessageThreads(true).then(() => {
          if (state.dmModalOpen && state.dmActiveThreadId && (!data.threadId || data.threadId === state.dmActiveThreadId)) {
            loadDirectMessageMessages(state.dmActiveThreadId);
          }
        });
        return;
      }
      if (eventType === "group") {
        const data = JSON.parse(rawData || "{}");
        if (!state.groupChatEnabled || !state.token) return;
        loadGroupChatRooms(true).then(() => {
          if (state.groupModalOpen && state.groupActiveRoomId && (!data.roomId || data.roomId === state.groupActiveRoomId)) {
            loadGroupChatMessages(state.groupActiveRoomId);
          }
        });
        return;
      }
      if (eventType === "pins") {
        const data = JSON.parse(rawData || "{}");
        if (data && Array.isArray(data.pins)) {
          state.pins = canViewPinnedMessages() ? data.pins : [];
          renderPinnedBar();
          if (state.messages && state.messages.length) scheduleVirtualRender({preserveScroll: true});
        }
        return;
      }
      if (eventType === "auth") {
        const data = JSON.parse(rawData || "{}");
        handleAuthExpired(data.reason || "expired", {reconnect: false});
        return;
      }
      if (eventType === "clear") {
        state.messages = [];
        state.nextLocalMessageId = 1;
        renderVirtualMessages({stickToBottom: true});
        state.historyHasMore = false;
        state.historyHasAfter = false;
        state.historyOldestId = "";
        state.historyNewestId = "";
      }
    } catch (_) {}
  }

  function installStandalonePipRelaySubscriber() {
    if (!state.isPip || !standalonePipRelayId || !standalonePipRelaySupported()) return false;
    closeStandalonePipRelay();
    try {
      standalonePipRelay = new BroadcastChannel(standalonePipRelayId);
      standalonePipRelay.onmessage = event => {
        const data = event && event.data || {};
        if (!data || data.source !== "KWCStandalonePip") return;
        if (data.kind === "stream") handleMirroredStreamEvent(data.type, data.data || "");
      };
      standalonePipRelay.postMessage({source: "KWCStandalonePip", kind: "hello"});
      return true;
    } catch (_) {
      closeStandalonePipRelay();
      return false;
    }
  }

  function ensureStandalonePipRelayPublisher() {
    if (!state.isStandalone || state.isPip || !standalonePipRelaySupported()) return "";
    if (standalonePipRelay && standalonePipRelayId) return standalonePipRelayId;
    closeStandalonePipRelay();
    standalonePipRelayId = "kwc-pip-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 12);
    try {
      standalonePipRelay = new BroadcastChannel(standalonePipRelayId);
      standalonePipRelay.onmessage = event => {
        const data = event && event.data || {};
        if (!data || data.source !== "KWCStandalonePip" || data.kind !== "hello") return;
        standalonePipRelay.postMessage({source: "KWCStandalonePip", kind: "stream", type: "ready", data: ""});
      };
      return standalonePipRelayId;
    } catch (_) {
      closeStandalonePipRelay();
      standalonePipRelayId = "";
      return "";
    }
  }

  function publishStandalonePipStream(type, rawData = "") {
    if (!state.isStandalone || state.isPip || !standalonePipRelay) return;
    try {
      standalonePipRelay.postMessage({
        source: "KWCStandalonePip",
        kind: "stream",
        type: String(type || ""),
        data: String(rawData == null ? "" : rawData)
      });
    } catch (_) {}
  }

  async function toggleStandalonePictureInPicture(labels = {}) {
    const pipLabel = (key, fallback, values = {}) => {
      let value = String(labels && labels[key] || fallback || key);
      Object.keys(values || {}).forEach(k => { value = value.replace("{" + k + "}", String(values[k] ?? "")); });
      return value;
    };

    if (standalonePipWindow && !standalonePipWindow.closed) {
      standalonePipWindow.close();
      return;
    }

    const api = window.documentPictureInPicture;
    if (!api || typeof api.requestWindow !== "function") {
      state.lastPipResultAt = Date.now();
      const message = pipLabel("unsupported", "Document Picture-in-Picture is not supported by this browser. Try desktop Chrome or Edge.");
      try { alert(message); } catch (_) {}
      return;
    }

    try {
      const root = document.getElementById("kwc-root");
      const rect = root ? root.getBoundingClientRect() : null;
      const width = Math.max(300, Math.min(640, rect && rect.width ? rect.width : 372));
      const height = Math.max(260, Math.min(720, rect && rect.height ? rect.height : 462));
      const pipWindow = await api.requestWindow({width, height, disallowReturnToOpener: true});
      standalonePipWindow = pipWindow;
      state.lastPipResultAt = Date.now();

      const doc = pipWindow.document;
      doc.open();
      doc.write("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='strict-origin-when-cross-origin'></head><body></body></html>");
      doc.close();

      // Standalone owns its complete UI in this document, so copy the already
      // active inline styles rather than asking the BlueMap parent bootstrap to
      // recreate them.
      document.querySelectorAll("style").forEach(sourceStyle => {
        const style = doc.createElement("style");
        style.textContent = sourceStyle.textContent || "";
        doc.head.appendChild(style);
      });
      const pipStyle = doc.createElement("style");
      pipStyle.textContent = "html,body{margin:0;width:100%;height:100%;background:transparent;overflow:hidden;}#kwc-root{right:0!important;bottom:0!important;width:100%!important;max-width:100%!important;height:100%!important;max-height:100%!important;}#kwc-pip,#kwc-min{display:none!important;}.kwc-header{cursor:default!important;}";
      doc.head.appendChild(pipStyle);

      const relayId = ensureStandalonePipRelayPublisher();
      if (!relayId) throw new Error("standalone_pip_relay_unavailable");

      pipWindow.KokotoWebChatConfig = Object.assign({}, cfg, {
        apiBase: apiBase,
        apiBaseUrl: apiBase,
        pip: true,
        standalone: false,
        pipStreamChannel: relayId,
        parentPageUrl: window.location.href
      });
      const script = doc.createElement("script");
      script.textContent = KWC_INNER_SELF_SOURCE;
      doc.body.appendChild(script);

      // Keep the original standalone chat instance alive. Only its UI is folded
      // while the PiP view is open; SSE, session state and Web Push remain owned
      // by the original page.
      standalonePipRestoreMinimized = state.minimized;
      if (!state.minimized) toggleMin({persist: false});

      pipWindow.addEventListener("pagehide", () => {
        standalonePipWindow = null;
        if (!standalonePipRestoreMinimized && state.minimized) toggleMin({persist: false});
        standalonePipRestoreMinimized = false;
        closeStandalonePipRelay();
        standalonePipRelayId = "";
      }, {once: true});
    } catch (err) {
      standalonePipWindow = null;
      closeStandalonePipRelay();
      standalonePipRelayId = "";
      if (!standalonePipRestoreMinimized && state.minimized) toggleMin({persist: false});
      standalonePipRestoreMinimized = false;
      state.lastPipResultAt = Date.now();
      const detail = err && err.message ? ((err.name ? err.name + ": " : "") + err.message) : String(err || "unknown error");
      const message = pipLabel("openFailed", "Failed to open Picture-in-Picture window: {error}", {error: detail});
      try { alert(message); } catch (_) {}
    }
  }

  let responsiveHeaderResizeObserver = null;
  let responsiveHeaderMutationObserver = null;
  let responsiveHeaderSyncFrame = 0;

  function headerElementVisible(el) {
    if (!el) return false;
    const style = getComputedStyle(el);
    return style.display !== "none" && style.visibility !== "hidden";
  }

  function headerOuterWidth(el) {
    if (!headerElementVisible(el)) return 0;
    const style = getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    const width = Math.max(Number(rect.width || 0), Number(el.scrollWidth || 0));
    return width + (parseFloat(style.marginLeft) || 0) + (parseFloat(style.marginRight) || 0);
  }

  function headerActionGroupNaturalWidth(group) {
    if (!group || !headerElementVisible(group)) return 0;
    const visible = Array.from(group.children).filter(headerElementVisible);
    if (!visible.length) return 0;
    const style = getComputedStyle(group);
    const gap = parseFloat(style.columnGap || style.gap) || 0;
    return visible.reduce((sum, child) => sum + headerOuterWidth(child), 0) + gap * Math.max(0, visible.length - 1);
  }

  function syncResponsiveHeaderLayout() {
    responsiveHeaderSyncFrame = 0;
    const root = document.getElementById("kwc-root");
    const header = root && root.querySelector(".kwc-header");
    if (!root || !header) return;
    if (root.classList.contains("kwc-minimized")) {
      root.classList.remove("kwc-header-wrapped");
      return;
    }

    const status = header.querySelector(".kwc-header-identity .kwc-status");
    const secondary = header.querySelector(".kwc-actions-secondary");
    const primary = header.querySelector(".kwc-actions-primary");
    const headerStyle = getComputedStyle(header);
    const contentWidth = Math.max(0,
      Number(header.clientWidth || 0)
      - (parseFloat(headerStyle.paddingLeft) || 0)
      - (parseFloat(headerStyle.paddingRight) || 0));
    // Measure against the normal one-row gap, not the current computed gap.
    // Wrapped mode uses a smaller vertical gap, and reading that value here
    // would make the layout oscillate near the threshold.
    const headerGap = 8;

    // The title deliberately does not participate in the required width. It may
    // ellipsize all the way down before we use a second row. The status element
    // is different: for administrators it is the actual Admin button, so it must
    // remain fully visible and clickable.
    const protectedIdentityWidth = headerOuterWidth(status);
    const secondaryWidth = headerActionGroupNaturalWidth(secondary);
    const primaryWidth = headerActionGroupNaturalWidth(primary);
    const requiredOneRowWidth = protectedIdentityWidth + secondaryWidth + primaryWidth + headerGap * 2;

    // A small tolerance avoids oscillation at fractional-pixel boundaries.
    root.classList.toggle("kwc-header-wrapped", contentWidth + 1 < requiredOneRowWidth);
  }

  function scheduleResponsiveHeaderLayout() {
    if (responsiveHeaderSyncFrame) return;
    responsiveHeaderSyncFrame = requestAnimationFrame(syncResponsiveHeaderLayout);
  }

  function installResponsiveHeaderLayout() {
    const root = document.getElementById("kwc-root");
    const header = root && root.querySelector(".kwc-header");
    if (!root || !header) return;
    if (responsiveHeaderResizeObserver) responsiveHeaderResizeObserver.disconnect();
    if (responsiveHeaderMutationObserver) responsiveHeaderMutationObserver.disconnect();
    if (window.ResizeObserver) {
      responsiveHeaderResizeObserver = new ResizeObserver(scheduleResponsiveHeaderLayout);
      responsiveHeaderResizeObserver.observe(header);
    } else {
      window.addEventListener("resize", scheduleResponsiveHeaderLayout, {passive: true});
    }
    if (window.MutationObserver) {
      responsiveHeaderMutationObserver = new MutationObserver(scheduleResponsiveHeaderLayout);
      responsiveHeaderMutationObserver.observe(header, {
        subtree: true,
        attributes: true,
        attributeFilter: ["class", "style", "hidden"],
        childList: true,
        characterData: true
      });
    }
    scheduleResponsiveHeaderLayout();
  }

  function makeRoot() {
    if (document.getElementById("kwc-root")) return;
    ensureGuestNameForConfig();

    const root = document.createElement("div");
    root.id = "kwc-root";
    root.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
    root.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
    root.style.setProperty("--kwc-emoji-panel-min-height", emojiPanelMinHeightPx() + "px");
    if (state.isPip) {
      document.documentElement.classList.add("kwc-pip-mode");
      document.body.classList.add("kwc-pip-mode");
      root.classList.add("kwc-pip-mode");
    }
    if (state.isStandalone) {
      document.documentElement.classList.add("kwc-standalone-mode");
      document.body.classList.add("kwc-standalone-mode");
      root.classList.add("kwc-standalone-mode");
    }
    root.innerHTML = `
      <div class="kwc-panel">
        <div class="kwc-header">
          <div class="kwc-header-primary">
            <div class="kwc-header-identity">
              <span class="kwc-title">${t("title.full", "KOKOTO WebChat")}</span>
              <span class="kwc-status" id="kwc-status">${t("status.connecting", "connecting...")}</span>
            </div>
            <div class="kwc-actions kwc-actions-primary">
              ${state.config && state.config.uiPictureInPictureEnabled === true && !state.isPip ? `<button class="kwc-button kwc-pip" id="kwc-pip" title="${t("button.pip", "PIP")}">▣</button>` : ""}
              ${(!state.isStandalone && !state.isPip) ? `<button class="kwc-button" id="kwc-min">_</button>` : ""}
            </div>
          </div>
          <div class="kwc-actions kwc-actions-secondary">
            ${state.directMessageEnabled ? `<button class="kwc-button kwc-dm-button kwc-hidden" id="kwc-dm" title="${t("button.directMessages", "Messages")}">✉<span class="kwc-dm-badge kwc-hidden" id="kwc-dm-badge">0</span></button>` : ""}
            ${state.groupChatEnabled ? `<button class="kwc-button kwc-group-button kwc-hidden" id="kwc-group" title="${t("group.title", "Group chats")}">👥<span class="kwc-dm-badge kwc-hidden" id="kwc-group-badge">0</span></button>` : ""}
            <button class="kwc-button kwc-notification-button" id="kwc-notifications" title="${t("notifications.inbox", "Notification inbox")}">🔔<span class="kwc-dm-badge kwc-hidden" id="kwc-notification-badge">0</span></button>
            <button class="kwc-button" id="kwc-login">${t("button.login", "Login")}</button>
          </div>
        </div>
        <div class="kwc-pinned-bar kwc-hidden" id="kwc-pinned-bar">
          <button class="kwc-pinned-open" id="kwc-pinned-open" type="button" data-open-pins="1">
            <span class="kwc-pinned-icon">📌</span>
            <span id="kwc-pinned-label">${t("pinned.count", "Pinned messages: {count}").replace("{count}", "0")}</span>
          </button>
        </div>
        <div class="kwc-messages" id="kwc-messages">
          <div class="kwc-virtual-spacer kwc-virtual-top-spacer"></div>
          <div class="kwc-history-end kwc-hidden" id="kwc-history-end">${t("history.end", "No more messages to display.")}</div>
          <div class="kwc-virtual-spacer kwc-virtual-bottom-spacer"></div>
        </div>
        <button class="kwc-jump-latest kwc-hidden" id="kwc-jump-latest" type="button" title="${t("button.jumpLatest", "Jump to latest")}">
          <span class="kwc-jump-latest-icon">↓</span>
          <span id="kwc-jump-latest-label">${t("button.jumpLatest", "Jump to latest")}</span>
        </button>
        <button class="kwc-button kwc-search-button kwc-search-float kwc-hidden" id="kwc-search-open" type="button" title="${t("button.search", "Search")}" aria-label="${t("button.search", "Search")}">⌕</button>
        <button class="kwc-button kwc-resize-lock-button kwc-resize-lock-float kwc-hidden" id="kwc-resize-lock" type="button" title="${t("button.resizeLock", "Lock resize")}" aria-label="${t("button.resizeLock", "Lock resize")}" aria-pressed="false">⇲</button>
        <div class="kwc-emoji-resize-handle kwc-hidden" id="kwc-emoji-resize" title="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}" aria-label="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}"></div>
        <div class="kwc-form">
          <div class="kwc-row" id="kwc-guest-row">
            <input class="kwc-input" id="kwc-guest-name" placeholder="${t("placeholder.guestName", "Guest name")}">
          </div>
          <div class="kwc-row kwc-captcha" id="kwc-captcha-row">
            <span id="kwc-captcha-q"></span>
            <input class="kwc-input" id="kwc-captcha-a" maxlength="6" placeholder="${t("placeholder.captchaAnswer", "answer")}">
          </div>
          <div class="kwc-reply-compose kwc-hidden" id="kwc-reply-compose">
            <button type="button" class="kwc-reply-compose-main" id="kwc-reply-compose-main" title="${t("reply.jump", "Jump to replied message")}">
              <span class="kwc-reply-compose-label" id="kwc-reply-compose-label"></span>
              <span class="kwc-reply-compose-preview" id="kwc-reply-compose-preview"></span>
            </button>
            <button type="button" class="kwc-mini-action kwc-reply-cancel" id="kwc-reply-cancel" title="${t("button.cancel", "Cancel")}">×</button>
          </div>
          <div class="kwc-row">
            <textarea class="kwc-input kwc-chat-composer" id="kwc-message" rows="1" autocomplete="off" enterkeyhint="send" maxlength="2048" placeholder="${t("placeholder.message", "message")}"></textarea>
            <button class="kwc-button kwc-command kwc-hidden" id="kwc-command" title="${t("button.commands", "Commands")}">/</button>
            <button class="kwc-button kwc-emoji-button kwc-hidden" id="kwc-emoji" title="${t("button.emoji", "Emoji")}">☺</button>
            <button class="kwc-button kwc-upload kwc-hidden" id="kwc-upload" title="${t("button.upload", "Attach")}">&#128206;</button>
            <button class="kwc-button kwc-send" id="kwc-send">${t("button.send", "Send")}</button>
            <input type="file" id="kwc-file" class="kwc-file-input" multiple hidden style="display:none !important;">
          </div>
          <div class="kwc-command-panel kwc-hidden" id="kwc-command-panel"></div>
          <div class="kwc-emoji-panel kwc-hidden" id="kwc-emoji-panel" aria-live="polite"></div>
          <div class="kwc-upload-progress kwc-hidden" id="kwc-upload-progress" aria-live="polite">
            <div class="kwc-upload-progress-head">
              <span id="kwc-upload-progress-text">${t("upload.ready", "Ready")}</span>
              <button class="kwc-button kwc-upload-cancel" id="kwc-upload-cancel" type="button">${t("button.cancel", "Cancel")}</button>
            </div>
            <div class="kwc-upload-progress-bar"><div id="kwc-upload-progress-fill"></div></div>
          </div>
        </div>
        <div class="kwc-drop-overlay kwc-hidden" id="kwc-drop-overlay" aria-hidden="true">
          <div class="kwc-drop-box">
            <div class="kwc-drop-title" id="kwc-drop-title">${t("upload.dropTitle", "Drop files to upload")}</div>
            <div class="kwc-drop-subtitle" id="kwc-drop-subtitle">${t("upload.dropSubtitle", "Release inside the chat panel.")}</div>
          </div>
        </div>
        <div class="kwc-resize-handle" id="kwc-resize-handle" title="${t("button.resize", "Resize")}"></div>
      </div>
    `;
    document.body.appendChild(root);
    installModalAffordanceObserver();
    installMessageActionDelegation(root);
    applyWebFontsConfig();
    applyFontSizeConfig();
    applyMediaViewportConfig();
    applyThemeConfig();


    const guestNameField = document.getElementById("kwc-guest-name");
    guestNameField.value = limitGuestNameCodePoints(state.guestName);
    guestNameField.addEventListener("input", () => {
      const limited = limitGuestNameCodePoints(guestNameField.value);
      if (limited !== guestNameField.value) guestNameField.value = limited;
    });
    document.getElementById("kwc-send").addEventListener("click", e => {
      if (state.sendInFlight) {
        e.preventDefault();
        return;
      }
      sendMessage();
    });
    const jumpLatest = document.getElementById("kwc-jump-latest");
    if (jumpLatest) jumpLatest.addEventListener("click", () => {
      forceLatestChatView("jump-latest");
    });
    const searchOpen = document.getElementById("kwc-search-open");
    if (searchOpen) searchOpen.addEventListener("click", event => {
      event.preventDefault();
      event.stopPropagation();
      if (!state.minimized) openSearchModal();
    });
    const resizeLockBtn = document.getElementById("kwc-resize-lock");
    if (resizeLockBtn) resizeLockBtn.addEventListener("click", event => {
      event.preventDefault();
      event.stopPropagation();
      toggleResizeLocked();
    });
    const dmBtn = document.getElementById("kwc-dm");
    if (dmBtn) dmBtn.addEventListener("click", () => openDirectMessageModal());
    const groupBtn = document.getElementById("kwc-group");
    if (groupBtn) groupBtn.addEventListener("click", () => openGroupChatModal());
    const notificationBtn = document.getElementById("kwc-notifications");
    if (notificationBtn) notificationBtn.addEventListener("click", () => {
      if (!state.minimized) openNotificationInboxModal();
    });
    updateNotificationInboxButton();
    const commandBtn = document.getElementById("kwc-command");
    if (commandBtn) commandBtn.addEventListener("click", () => openCommandModal());
    const emojiBtn = document.getElementById("kwc-emoji");
    if (emojiBtn) emojiBtn.addEventListener("click", () => toggleEmojiPanel());
    installEmojiPanelResize(root);
    document.getElementById("kwc-upload").addEventListener("click", () => {
      const input = document.getElementById("kwc-file");
      if (input) input.click();
    });
    document.getElementById("kwc-file").addEventListener("change", uploadSelectedFiles);
    installDragAndDropUpload(root);
    const uploadCancelBtn = document.getElementById("kwc-upload-cancel");
    if (uploadCancelBtn) uploadCancelBtn.addEventListener("click", cancelCurrentUpload);
    document.getElementById("kwc-message").addEventListener("paste", handlePasteUpload);
    const replyCancel = document.getElementById("kwc-reply-cancel");
    if (replyCancel) replyCancel.addEventListener("click", clearReplyTarget);
    const replyComposeMain = document.getElementById("kwc-reply-compose-main");
    if (replyComposeMain) replyComposeMain.addEventListener("click", () => {
      if (state.replyTarget && state.replyTarget.id) jumpToReplyTarget(state.replyTarget.id);
    });
    renderReplyCompose();
    const messageInput = document.getElementById("kwc-message");
    messageInput.addEventListener("focus", () => setActiveComposeInput(messageInput));
    messageInput.addEventListener("compositionstart", () => {
      messageInputComposing = true;
      if (commandPanelRenderFrame) {
        cancelAnimationFrame(commandPanelRenderFrame);
        commandPanelRenderFrame = 0;
      }
    });
    messageInput.addEventListener("compositionend", () => {
      messageInputComposing = false;
      scheduleCommandPanelUpdate();
    });
    messageInput.addEventListener("keydown", e => {
      if (e.key === "Enter") {
        // Enter may be used to commit Korean/Japanese/Chinese IME composition.
        // Never consume it as a chat send while composition is still active.
        if (e.isComposing || messageInputComposing || e.keyCode === 229) return;
        e.preventDefault();
        if (e.repeat || state.sendInFlight) return;
        sendMessage();
      }
      if (e.key === "Escape") { hideCommandPanel(); hideEmojiPanel(); if (state.replyTarget) clearReplyTarget(); }
    });
    messageInput.addEventListener("input", () => {
      normalizeSingleLineComposer(messageInput);
      scheduleCommandPanelUpdate();
    });
    messageInput.addEventListener("focus", scheduleCommandPanelUpdate);
    messageInput.addEventListener("blur", () => setTimeout(hideCommandPanel, 160));
    installHistoryPaging();
    document.getElementById("kwc-login").addEventListener("click", () => {
      if (!state.minimized) openLoginModal();
    });
    const legacyAdminBtn = document.getElementById("kwc-admin");
    if (legacyAdminBtn) legacyAdminBtn.addEventListener("click", () => {
      if (!state.minimized) openAdminModal();
    });
    const pipBtn = document.getElementById("kwc-pip");
    if (pipBtn) pipBtn.addEventListener("click", () => {
      const c = state.config || {};
      if (c.uiPictureInPictureEnabled !== true || state.isPip) {
        updatePipButton();
        return;
      }
      const unsupportedMessage = t("pip.unsupported", "Document Picture-in-Picture is not supported by this browser. Try desktop Chrome or Edge.");
      const openFailedMessage = t("pip.openFailed", "Failed to open Picture-in-Picture window: {error}");
      state.lastPipResultAt = 0;
      const pipLabels = {
        unsupported: unsupportedMessage,
        openFailed: openFailedMessage
      };
      if (state.isStandalone) {
        // Direct top-level Document PiP call. This keeps the request in the
        // original click activation and removes the standalone dependency on
        // the BlueMap iframe/parent message bridge. Actual API failures are
        // reported by toggleStandalonePictureInPicture().
        void toggleStandalonePictureInPicture(pipLabels);
      } else if (window.parent === window) {
        try { alert(unsupportedMessage); } catch (_) {}
      } else {
        postFrame("togglePip", {
          pipEnabled: true,
          labels: pipLabels
        });
        setTimeout(() => {
          if (!state.lastPipResultAt) {
            try { alert(unsupportedMessage); } catch (_) {}
          }
        }, 1200);
      }
    });
    updatePipButton();
    const minButton = document.getElementById("kwc-min");
    if (minButton) minButton.addEventListener("click", toggleMin);
    installDrag(root);
    installResize(root);

    if (!state.isPip && state.minimized) {
      root.classList.add("kwc-minimized");
      document.getElementById("kwc-messages").classList.add("kwc-hidden");
      document.querySelector(".kwc-form").classList.add("kwc-hidden");
    }
    const minBtn = document.getElementById("kwc-min");
    if (minBtn) minBtn.textContent = state.minimized ? "+" : "-";
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");

    installResponsiveHeaderLayout();
    updateLoginState();
  }

  function closeAllModals() {
    document.querySelectorAll(".kwc-modal-backdrop, .kwc-modal-wrap").forEach(el => el.remove());
    state.loginModalOpen = false;
    state.prefsModalOpen = false;
  }

  function toggleMin(options = {}) {
    protectHistoryEndNotice("toggle-min", 7000);
    state.minimized = !state.minimized;
    if (options.persist !== false) localStorage.setItem("kwc.minimized", state.minimized ? "1" : "0");

    if (state.minimized) {
      closeAllModals();
    }

    const root = document.getElementById("kwc-root");
    if (root) root.classList.toggle("kwc-minimized", state.minimized);
    const messages = document.getElementById("kwc-messages");
    if (messages) messages.classList.toggle("kwc-hidden", state.minimized);
    const form = document.querySelector(".kwc-form");
    if (form) form.classList.toggle("kwc-hidden", state.minimized);
    updateEmojiResizeHandleVisibility();
    const minBtn = document.getElementById("kwc-min");
    if (minBtn) minBtn.textContent = state.minimized ? "+" : "-";
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");
    updateFrameSize();
    updatePipButton();
    updateDirectMessageButton();
    updateGroupChatButton();
    updateNotificationInboxButton();
    scheduleResponsiveHeaderLayout();
    if (!state.minimized) {
      scheduleVirtualRender();
      protectHistoryEndNotice("unminimize", 8000);
      state.forceHistoryEndNoticeUntil = Math.max(Number(state.forceHistoryEndNoticeUntil || 0), Date.now() + 8000);
      scheduleScrollAffordanceRefresh("unminimize");
    }
  }


  function clearLoginStorage() {
    state.token = "";
    state.username = "";
    state.role = "";
    try {
      localStorage.removeItem("kwc.token");
      localStorage.removeItem("kwc.username");
      localStorage.removeItem("kwc.role");
    } catch (_) {}
  }

  function resetPrivateChatState() {
    state.dmUnread = 0;
    state.dmThreads = [];
    state.dmAdminThreads = [];
    state.dmMessages = [];
    state.dmActiveThreadId = "";
    state.dmActiveThread = null;
    state.dmCleanupPreview = null;
    state.groupUnread = 0;
    state.groupRooms = [];
    state.groupInvites = [];
    state.groupHiddenRooms = [];
    state.groupAdminRooms = [];
    state.groupMessages = [];
    state.groupActiveRoomId = "";
    state.groupActiveRoom = null;
    state.groupCleanupPreview = null;
    state.privateChatSuperAdmin = false;
    state.groupChatContentAccess = false;
    state.groupAuditMode = false;
    state.groupAuditRoom = null;
    state.privateChatContentAccess = false;
    state.dmAuditMode = false;
    state.dmAuditThread = null;
  }

  function clearPrivateChatForAuthLoss() {
    try { document.querySelectorAll(".kwc-modal-backdrop, .kwc-modal-wrap").forEach(el => el.remove()); } catch (_) {}
    resetPrivateChatState();
    updateDirectMessageButton();
    updateGroupChatButton();
    updateNotificationInboxButton();
  }

  function clearVisibleChatForLoggedOutHidden(reason = "auth-expired") {
    if (!guestChatHidden()) return;
    state.messages = [];
    state.nextLocalMessageId = 1;
    state.replyTarget = null;
    state.pins = [];
    state.historyHasMore = false;
    state.historyHasAfter = false;
    state.historyOldestId = "";
    state.historyNewestId = "";
    state.historyLoading = false;
    state.historyLoadSeq++;
    try { if (state.eventSource) state.eventSource.close(); } catch (_) {}
    state.eventSource = null;
    clearStreamReconnectTimer();
    renderPinnedBar();
    renderVirtualMessages({stickToBottom: true, ignoreVisibleRangeProtection: true});
    updateGuestVisibility();
    updateFrameSize();
  }

  function handleAuthExpired(reason = "expired", options = {}) {
    const hadToken = !!state.token;
    clearLoginStorage();
    clearPrivateChatForAuthLoss();
    updateLoginState();
    updateGuestVisibility();
    clearVisibleChatForLoggedOutHidden(reason);
    if (!state.isPip && !guestChatHidden() && hadToken && options.reconnect !== false) connectStream({refreshAfterOpen: true, reason: "auth-" + reason});
  }

  function isAuthExpiredApiError(err) {
    if (!err || !state.token) return false;
    const status = Number(err.status || 0);
    const code = err.response && err.response.error ? String(err.response.error) : "";
    if (code === "not_logged_in" || code === "auth_expired" || code === "invalid_token") return true;
    return (status === 401 || status === 403) && /(?:token|auth|logged|permission)/i.test(code || String(err.message || ""));
  }

  function guestChatHidden() {
    return !!(
      state.config &&
      state.config.guestEnabled === false &&
      state.config.hideChatForGuestsWhenGuestDisabled &&
      !state.token
    );
  }

  function updateGuestVisibility() {
    const root = document.getElementById("kwc-root");
    const box = document.getElementById("kwc-messages");
    const form = document.querySelector(".kwc-form");
    const guestRow = document.getElementById("kwc-guest-row");
    const captchaRow = document.getElementById("kwc-captcha-row");
    if (!root) return;

    const hidden = guestChatHidden();
    root.classList.toggle("kwc-guest-hidden", hidden);

    if (hidden) {
      if (box) box.classList.add("kwc-hidden");
      if (form) form.classList.add("kwc-hidden");
      if (guestRow) guestRow.classList.add("kwc-hidden");
      if (captchaRow) captchaRow.classList.remove("kwc-show");
      state.captcha = null;
      updateEmojiResizeHandleVisibility();
      updateFrameSize();
      return;
    }

    if (!state.minimized) {
      if (box) box.classList.remove("kwc-hidden");
      if (form) form.classList.remove("kwc-hidden");
    }
    updateEmojiResizeHandleVisibility();
  }

  function roleLabel(role) {
    const key = String(role || "").toLowerCase();
    if (key === "moderator") return t("role.moderator.short", "MOD");
    if (key === "admin") return t("role.admin.short", "ADMIN");
    if (key === "user") return t("role.user.short", "USER");
    if (key === "guest") return t("role.guest.short", "GUEST");
    return role || t("status.loggedIn", "logged in");
  }

  function refreshStaticLabels() {
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");
    const adminStatus = document.getElementById("kwc-status");
    if (adminStatus && adminStatus.classList.contains("kwc-status-admin-action")) adminStatus.title = t("button.admin", "Admin");
    updateResizeLockButton();
    const sendBtn = document.getElementById("kwc-send");
    if (sendBtn) sendBtn.textContent = t("button.send", "Send");
    const uploadBtn = document.getElementById("kwc-upload");
    if (uploadBtn) uploadBtn.title = t("button.upload", "Attach");
    const emojiBtn = document.getElementById("kwc-emoji");
    if (emojiBtn) emojiBtn.title = t("button.emoji", "Emoji");
    const commandBtn = document.getElementById("kwc-command");
    if (commandBtn) commandBtn.title = t("button.commands", "Commands");
    const jumpBtn = document.getElementById("kwc-jump-latest");
    if (jumpBtn) jumpBtn.title = t("button.jumpLatest", "Jump to latest");
    const jumpLabel = document.getElementById("kwc-jump-latest-label");
    if (jumpLabel) jumpLabel.textContent = t("button.jumpLatest", "Jump to latest");
    const historyEnd = ensureHistoryEndNotice(document.getElementById("kwc-messages"));
    if (historyEnd) historyEnd.textContent = t("history.end", "No more messages to display.");
    const pipBtn = document.getElementById("kwc-pip");
    if (pipBtn) pipBtn.title = t("button.pip", "PIP");
    const guest = document.getElementById("kwc-guest-name");
    if (guest) guest.placeholder = t("placeholder.guestName", "Guest name");
    const captcha = document.getElementById("kwc-captcha-a");
    if (captcha) captcha.placeholder = t("placeholder.captchaAnswer", "answer");
    const input = document.getElementById("kwc-message");
    if (input) input.placeholder = t("placeholder.message", "message");
    const resize = document.getElementById("kwc-resize-handle");
    if (resize) resize.title = t("button.resize", "Resize");
    const dropTitle = document.getElementById("kwc-drop-title");
    if (dropTitle) dropTitle.textContent = t("upload.dropTitle", "Drop files to upload");
    const dropSubtitle = document.getElementById("kwc-drop-subtitle");
    if (dropSubtitle) dropSubtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    renderPinnedBar();
    updateLoginState();
  }

  function updateLoginState() {
    const btn = document.getElementById("kwc-login");
    const status = document.getElementById("kwc-status");
    const guestRow = document.getElementById("kwc-guest-row");
    const dmBtn = document.getElementById("kwc-dm");
    const groupBtn = document.getElementById("kwc-group");
    const uploadBtn = document.getElementById("kwc-upload");
    const emojiBtn = document.getElementById("kwc-emoji");
    const commandBtn = document.getElementById("kwc-command");
    if (!btn || !status) return;
    status.classList.remove("kwc-status-role-ADMIN", "kwc-status-role-MODERATOR", "kwc-status-role-USER", "kwc-status-role-GUEST");

    const adminPanelAllowed = !state.config || state.config.allowWebAdminPanel !== false;
    const moderationEnabled = !state.config || state.config.moderationEnabled !== false;
    const canManageMutes = moderationEnabled && state.token && (state.role === "ADMIN" || (state.role === "MODERATOR" && (!state.config || state.config.allowModeratorGuestMute !== false)));
    const canUseAdminPanel = state.token && adminPanelAllowed && (state.role === "ADMIN" || canManageMutes);
    if (dmBtn) dmBtn.classList.toggle("kwc-hidden", !(state.token && state.directMessageEnabled) || state.minimized);
    if (groupBtn) groupBtn.classList.toggle("kwc-hidden", !(state.token && state.groupChatEnabled) || state.minimized);
    updateDirectMessageButton();
    updateGroupChatButton();
    if (uploadBtn) uploadBtn.classList.toggle("kwc-hidden", !canUpload());
    updateEmojiButton();
    updateDirectMessageComposeControls();
    updateCommandButton();
    updatePipButton();
    updateResizeLockButton();

    btn.classList.remove("kwc-login-user", "kwc-user-role-ADMIN", "kwc-user-role-MODERATOR", "kwc-user-role-USER", "kwc-user-role-GUEST");
    if (state.token) {
      btn.textContent = state.username || t("status.loggedIn", "User");
      btn.title = t("preferences.title", "Chat settings");
      btn.classList.add("kwc-login-user", "kwc-user-role-" + String(state.role || "USER"));
      status.textContent = roleLabel(state.role);
      status.title = canUseAdminPanel ? t("button.admin", "Admin") : (state.role || "");
      status.classList.add("kwc-status-role-" + String(state.role || "USER"));
      status.classList.toggle("kwc-status-admin-action", !!canUseAdminPanel);
      if (canUseAdminPanel) {
        status.setAttribute("role", "button");
        status.setAttribute("tabindex", "0");
        status.onclick = event => { event.preventDefault(); event.stopPropagation(); if (!state.minimized) openAdminModal(); };
        status.onkeydown = event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); if (!state.minimized) openAdminModal(); } };
      } else {
        status.removeAttribute("role");
        status.removeAttribute("tabindex");
        status.onclick = null;
        status.onkeydown = null;
      }
      if (guestRow) guestRow.classList.add("kwc-hidden");
    } else {
      btn.title = t("button.login", "Login");
      btn.textContent = t("button.login", "Login");
      status.textContent = t("status.guest", "guest");
      status.title = "";
      status.classList.remove("kwc-status-admin-action");
      status.removeAttribute("role");
      status.removeAttribute("tabindex");
      status.onclick = null;
      status.onkeydown = null;
      const allowGuestName = !state.config || state.config.guestAllowCustomName !== false;
      if (guestRow) guestRow.classList.toggle("kwc-hidden", !allowGuestName || guestChatHidden());
    }
    scheduleResponsiveHeaderLayout();
    if (state.messages && state.messages.length) scheduleVirtualRender();
  }


  function virtualScrollEnabled() {
    const c = state.config || {};
    return c.uiVirtualScrollEnabled !== false;
  }

  function virtualOverscanScreens() {
    const n = Number(state.config && state.config.uiVirtualScrollOverscanScreens);
    return Number.isFinite(n) && n >= 0 ? n : 1;
  }

  function virtualMinRenderedMessages() {
    const n = Number(state.config && state.config.uiVirtualScrollMinRenderedMessages);
    return Number.isFinite(n) && n >= 0 ? Math.floor(n) : 20;
  }

  function virtualRenderTargetMessageCount(viewport, overscanPx) {
    const base = virtualMinRenderedMessages();
    const avg = Math.max(24, Math.min(120, Number(state.virtualAverageMessageHeight) || 42));
    const px = Math.max(1, Number(viewport) || 1) + Math.max(0, Number(overscanPx) || 0) * 2;
    const byViewport = Math.ceil(px / avg) + 4;
    return Math.max(base, byViewport);
  }

  function scheduleHistoryViewportFill(reason = "") {
    clearTimeout(state.historyViewportFillTimer);
    state.historyViewportFillTimer = setTimeout(() => {
      state.historyViewportFillTimer = null;
      const box = document.getElementById("kwc-messages");
      if (!box || state.minimized || guestChatHidden()) return;
      if (!state.historyHasMore || state.historyLoading || !state.historyOldestId) return;
      if (!bottomFollowAllowed(box)) return;
      if (isScrollInteractionActive()) {
        scheduleHistoryViewportFill(reason || "scroll-idle");
        return;
      }

      // If the first history page is shorter than a large chat window, there is
      // no scrollbar, so the usual top-scroll preload can never fire. Pull older
      // pages until the viewport is filled or there is no more history.
      if (box.scrollHeight <= box.clientHeight + 4 && Number(state.historyViewportFillAttempts || 0) < 8) {
        state.historyViewportFillAttempts = Number(state.historyViewportFillAttempts || 0) + 1;
        loadHistory(true, {viewportFill: true});
      }
    }, 40);
  }

  function historyEndEligible() {
    return !state.historyLoading && !state.historyHasMore && state.messages.length > 0;
  }

  function historyLatestEndEligible() {
    return !state.historyLoading && !state.historyHasAfter && state.messages.length > 0;
  }

  function protectHistoryEndNotice(reason = "", ms = 0) {
    // The oldest-history notice is a user-scroll toast, not a persistent state
    // banner. Modal/focus/resize/PIP/delete transitions may refresh layout, but
    // they must not force the notice to appear or keep it latched.
    state.historyEndNoticeProtectedUntil = 0;
    state.historyEndNoticeUiTransitionUntil = 0;
    state.forceHistoryEndNoticeUntil = 0;
  }


  function updateScrollAffordanceLayout(box) {
    if (!box) box = document.getElementById("kwc-messages");
    const root = document.getElementById("kwc-root");
    const panel = root && root.querySelector ? root.querySelector(".kwc-panel") : null;
    if (!box || !root || !panel) return;
    try {
      const br = box.getBoundingClientRect();
      const pr = panel.getBoundingClientRect();
      const top = Math.max(8, Math.round(br.top - pr.top + 8));
      const bottom = Math.max(8, Math.round(pr.bottom - br.bottom + 12));
      root.style.setProperty("--kwc-affordance-top", top + "px");
      root.style.setProperty("--kwc-affordance-bottom", bottom + "px");
    } catch (_) {}
  }

  function setHistoryNoticeText(notice, key = "history.end", fallback = "No more messages to display.") {
    state.historyEndNoticeKey = key || "history.end";
    state.historyEndNoticeFallback = fallback || "";
    if (notice) notice.textContent = t(state.historyEndNoticeKey, state.historyEndNoticeFallback);
  }

  function resetHistoryNoticeText(notice = null) {
    setHistoryNoticeText(notice || document.getElementById("kwc-history-end"), "history.end", "No more messages to display.");
  }

  function ensureHistoryEndNotice(box, key = null, fallback = null, position = null) {
    if (!box) box = document.getElementById("kwc-messages");
    const root = document.getElementById("kwc-root");
    const panel = root && root.querySelector ? root.querySelector(".kwc-panel") : null;
    if (!box || !panel) return null;
    let notice = document.getElementById("kwc-history-end");
    if (!notice) {
      notice = document.createElement("div");
      notice.className = "kwc-history-end kwc-history-end-top kwc-hidden";
      notice.id = "kwc-history-end";
    }
    if (key) {
      setHistoryNoticeText(notice, key, fallback || key);
    } else {
      setHistoryNoticeText(notice, state.historyEndNoticeKey || "history.end", state.historyEndNoticeFallback || "No more messages to display.");
    }

    const pos = position || state.historyEndNoticePosition || "top";
    state.historyEndNoticePosition = pos === "bottom" ? "bottom" : "top";
    notice.classList.toggle("kwc-history-end-bottom", state.historyEndNoticePosition === "bottom");
    notice.classList.toggle("kwc-history-end-top", state.historyEndNoticePosition !== "bottom");

    // Keep this as a panel-level overlay, not as a child of the virtualized
    // message list.  When the notice lived inside .kwc-messages it competed
    // with top/bottom spacers and virtual re-renders, so modal/PIP transitions
    // and scrolling could repeatedly hide/reinsert it.
    if (notice.parentNode !== panel) {
      panel.appendChild(notice);
    }
    updateScrollAffordanceLayout(box);
    return notice;
  }

  function historyEndNoticeThresholdPx(box) {
    // Tight physical-top check.  This is deliberately much smaller than the
    // preload threshold so the toast cannot appear while older messages remain
    // just above the viewport.
    return Math.max(2, Math.min(8, Math.floor(Number(box && box.clientHeight || 0) * 0.01)));
  }

  function historyEndNoticeAtTop(box) {
    return !!box && Number(box.scrollTop || 0) <= historyEndNoticeThresholdPx(box);
  }

  function historyEndNoticeAtBottom(box) {
    return !!box && bottomGapPx(box) <= historyEndNoticeThresholdPx(box);
  }

  function hideHistoryEndNoticeToast(clearPending = false) {
    if (state.historyEndNoticeTimer) {
      clearTimeout(state.historyEndNoticeTimer);
      state.historyEndNoticeTimer = null;
    }
    state.historyEndNoticeVisibleUntil = 0;
    state.historyEndNoticeVisible = false;
    state.historyEndNoticeSticky = false;
    state.historyEndNoticeStickySince = 0;
    state.historyEndNoticeProtectedUntil = 0;
    state.historyEndNoticeUiTransitionUntil = 0;
    state.forceHistoryEndNoticeUntil = 0;
    if (clearPending) {
      state.historyEndNoticePendingUserTopUntil = 0;
      state.historyEndNoticePendingUserBottomUntil = 0;
      state.historyEndNoticeBottomExtraScrollCount = 0;
    }
    const notice = document.getElementById("kwc-history-end");
    if (notice) {
      notice.classList.add("kwc-hidden");
      notice.classList.remove("kwc-history-end-bottom");
      notice.classList.add("kwc-history-end-top");
    }
    resetHistoryNoticeText(notice);
  }

  function showHistoryStatusNoticeToast(box, key, fallback, durationMs = 3500) {
    if (!box) box = document.getElementById("kwc-messages");
    if (!box || state.minimized || guestChatHidden()) return;
    if (Number(box.scrollTop || 0) > historyPreloadThresholdPx(box)) return;

    const notice = ensureHistoryEndNotice(box, key, fallback, "top");
    if (!notice) return;
    const now = Date.now();
    if (state.historyEndNoticeTimer) clearTimeout(state.historyEndNoticeTimer);
    state.historyEndNoticeTimer = null;
    state.historyEndNoticeVisible = true;
    state.historyEndNoticeVisibleUntil = now + Math.max(800, Math.min(10000, Number(durationMs) || 3500));
    notice.classList.remove("kwc-hidden");

    state.historyEndNoticeTimer = setTimeout(() => {
      if (Date.now() >= Number(state.historyEndNoticeVisibleUntil || 0)) {
        hideHistoryEndNoticeToast(false);
      }
    }, Math.max(850, Math.min(10050, Number(durationMs) || 3500)) + 50);
  }

  function hideHistoryStatusNoticeIfActive() {
    const key = String(state.historyEndNoticeKey || "history.end");
    if (key !== "history.end") hideHistoryEndNoticeToast(false);
  }

  function showHistoryEndNoticeToast(box, reason = "", position = "top") {
    if (!box) box = document.getElementById("kwc-messages");
    const pos = position === "bottom" ? "bottom" : "top";
    const notice = ensureHistoryEndNotice(box, "history.end", "No more messages to display.", pos);
    if (!notice || !box) return;
    const eligible = pos === "bottom"
      ? (historyLatestEndEligible() && historyEndNoticeAtBottom(box))
      : (historyEndEligible() && historyEndNoticeAtTop(box));
    if (state.minimized || guestChatHidden() || !eligible) return;

    const now = Date.now();
    // Ignore duplicate callbacks fired in the same frame, but do not latch the
    // notice across focus changes. A new wheel/touch/key/scrollbar input at the
    // edge can show it again after the previous toast has disappeared.
    if (state.historyEndNoticeVisible && now < Number(state.historyEndNoticeVisibleUntil || 0)) return;
    if (now - Number(state.historyEndNoticeLastShownAt || 0) < 250) return;

    if (state.historyEndNoticeTimer) clearTimeout(state.historyEndNoticeTimer);
    state.historyEndNoticeTimer = null;
    state.historyEndNoticeVisible = true;
    state.historyEndNoticeSticky = false;
    state.historyEndNoticeStickySince = 0;
    state.historyEndNoticeVisibleUntil = now + 2500;
    state.historyEndNoticeLastShownAt = now;
    if (pos === "bottom") {
      state.historyEndNoticePendingUserBottomUntil = 0;
      state.historyEndNoticeBottomExtraScrollCount = 0;
    } else state.historyEndNoticePendingUserTopUntil = 0;
    notice.classList.remove("kwc-hidden");

    state.historyEndNoticeTimer = setTimeout(() => {
      if (Date.now() >= Number(state.historyEndNoticeVisibleUntil || 0)) {
        hideHistoryEndNoticeToast(false);
      }
    }, 2550);
  }

  function clearHistorySlowNoticeTimer() {
    if (state.historySlowNoticeTimer) {
      clearTimeout(state.historySlowNoticeTimer);
      state.historySlowNoticeTimer = null;
    }
  }

  function scheduleHistorySlowNotice(box, loadSeq, older) {
    clearHistorySlowNoticeTimer();
    if (!older) return;
    state.historySlowNoticeTimer = setTimeout(() => {
      state.historySlowNoticeTimer = null;
      if (!state.historyLoading || loadSeq !== state.historyLoadSeq) return;
      const currentBox = document.getElementById("kwc-messages") || box;
      showHistoryStatusNoticeToast(currentBox, "history.loading", "Loading history.\nPlease wait.", 3500);
    }, 1400);
  }

  function historyFailureNoticeKey(error) {
    if (typeof navigator !== "undefined" && navigator && navigator.onLine === false) return "history.offline";
    if (error && (error.kwcTimeout || error.name === "AbortError")) return "history.timeout";
    return "history.failed";
  }

  function historyFailureNoticeFallback(key) {
    if (key === "history.offline") return "Offline.\nCheck connection.";
    if (key === "history.timeout") return "Slow response.\nTry again shortly.";
    return "Load failed.\nTry again shortly.";
  }

  function showHistoryFailureNotice(box, error) {
    const key = historyFailureNoticeKey(error);
    showHistoryStatusNoticeToast(box, key, historyFailureNoticeFallback(key), 4500);
  }

  function recentHistoryEndUserScrollInput(now = Date.now()) {
    const lastDirect = Number(state.lastDirectScrollInputAt || 0);
    const lastNonScrollUi = Number(state.lastNonScrollUiActionAt || 0);
    if (lastDirect <= 0) return false;
    // Any UI click/key after the scroll input cancels the user-top intent. This
    // prevents delete/admin/settings/modal/layout changes from creating a toast.
    if (lastNonScrollUi > 0 && lastNonScrollUi >= lastDirect - 20) return false;
    return now - lastDirect <= Math.max(1400, scrollInteractionIdleMs() * 4);
  }

  function rememberUserTopIntent(box) {
    if (!box || !recentHistoryEndUserScrollInput()) return;
    if (Number(box.scrollTop || 0) <= historyPreloadThresholdPx(box)) {
      state.historyEndNoticePendingUserTopUntil = Date.now() + 5000;
    }
  }

  function markHistoryEndTopUserIntent(box, reason = "") {
    if (!box || state.minimized || guestChatHidden()) return;
    // This is only called from direct user scroll inputs.  It intentionally
    // records intent while a focus/resume history refresh is in flight, then
    // loadHistory() rechecks after the response settles.
    const nearTop = Number(box.scrollTop || 0) <= historyPreloadThresholdPx(box);
    if (!nearTop) return;
    state.historyEndNoticePendingUserTopUntil = Date.now() + 6000;
    setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, reason || "top-user-input"), 0);
    setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, reason || "top-user-input"), 80);
    setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, reason || "top-user-input"), 220);
  }

  function resetHistoryEndBottomExtraScrollCount() {
    state.historyEndNoticeBottomExtraScrollCount = 0;
  }

  function bottomEndNoticeExtraScrollAttemptReason(reason) {
    return /^(wheel|key|touch|scrollbar)-bottom$/.test(String(reason || ""));
  }

  function bottomEndNoticeExtraScrollCountReached() {
    return Number(state.historyEndNoticeBottomExtraScrollCount || 0) >= 10;
  }

  function markHistoryEndBottomUserIntent(box, reason = "") {
    if (!box || state.minimized || guestChatHidden()) return;
    const nearBottom = bottomGapPx(box) <= historyPreloadThresholdPx(box);
    if (!nearBottom) {
      resetHistoryEndBottomExtraScrollCount();
      return;
    }
    if (bottomEndNoticeExtraScrollAttemptReason(reason)) {
      state.historyEndNoticeBottomExtraScrollCount = Math.max(0, Number(state.historyEndNoticeBottomExtraScrollCount || 0)) + 1;
    }
    state.historyEndNoticePendingUserBottomUntil = Date.now() + 6000;
    setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, reason || "bottom-user-input"), 0);
    setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, reason || "bottom-user-input"), 80);
    setTimeout(() => maybeShowHistoryEndNoticeFromUserScroll(box, reason || "bottom-user-input"), 220);
  }

  function maybeShowHistoryEndNoticeFromUserScroll(box, reason = "") {
    if (!box) box = document.getElementById("kwc-messages");
    if (!box) return;

    const now = Date.now();
    const atTop = historyEndNoticeAtTop(box);
    const atBottom = historyEndNoticeAtBottom(box);
    if (!atBottom) resetHistoryEndBottomExtraScrollCount();
    if (!atTop && !atBottom) {
      hideHistoryEndNoticeToast(false);
      return;
    }

    const fromRecentScroll = recentHistoryEndUserScrollInput(now);
    const fromPendingTopIntent = Number(state.historyEndNoticePendingUserTopUntil || 0) > now;
    const fromPendingBottomIntent = Number(state.historyEndNoticePendingUserBottomUntil || 0) > now;
    if (!fromRecentScroll && !fromPendingTopIntent && !fromPendingBottomIntent) return;

    if (state.minimized || guestChatHidden()) return;

    if (atTop && (fromRecentScroll || fromPendingTopIntent)) {
      if (!historyEndEligible()) {
        // While history is still loading or hasMore is still true, keep only the
        // user intent. The load completion path will call this again and show the
        // toast only if the final response proves there is no older history.
        if (fromRecentScroll) state.historyEndNoticePendingUserTopUntil = now + 5000;
      } else {
        showHistoryEndNoticeToast(box, reason, "top");
        return;
      }
    }

    if (atBottom && (fromRecentScroll || fromPendingBottomIntent)) {
      if (!historyLatestEndEligible()) {
        // If newer messages are still loading or still known to exist, keep the
        // bottom-edge intent. loadNewerHistory() will recheck after it settles.
        if (fromRecentScroll) state.historyEndNoticePendingUserBottomUntil = now + 5000;
      } else if (bottomEndNoticeExtraScrollCountReached()) {
        showHistoryEndNoticeToast(box, reason, "bottom");
      }
    }
  }
  function updateHistoryEndNotice(box) {
    if (!box) box = document.getElementById("kwc-messages");
    const notice = ensureHistoryEndNotice(box);
    if (!notice || !box) return;
    updateScrollAffordanceLayout(box);

    const now = Date.now();
    const noticeAtExpectedEdge = state.historyEndNoticePosition === "bottom"
      ? (historyLatestEndEligible() && historyEndNoticeAtBottom(box))
      : (historyEndEligible() && historyEndNoticeAtTop(box));
    const shouldRemainVisible =
      state.historyEndNoticeVisible &&
      now < Number(state.historyEndNoticeVisibleUntil || 0) &&
      noticeAtExpectedEdge &&
      !state.minimized &&
      !guestChatHidden();

    notice.classList.toggle("kwc-hidden", !shouldRemainVisible);
    if (!shouldRemainVisible && state.historyEndNoticeVisible) {
      state.historyEndNoticeVisible = false;
      state.historyEndNoticeVisibleUntil = 0;
    }
  }


  function scheduleScrollAffordanceRefresh(reason = "") {
    const reasonText = String(reason || "").toLowerCase();
    if (/(modal|admin|pref|setting|pip|minimize|unminimize|resize|restore|focus)/.test(reasonText)) {
      protectHistoryEndNotice(reasonText, /pip/.test(reasonText) ? 7000 : 5000);
    }
    const box = document.getElementById("kwc-messages");
    if (box) refreshScrollAffordances(box);
    setTimeout(() => {
      const laterBox = document.getElementById("kwc-messages");
      if (laterBox) refreshScrollAffordances(laterBox);
    }, 60);
    setTimeout(() => {
      const laterBox = document.getElementById("kwc-messages");
      if (laterBox) refreshScrollAffordances(laterBox);
    }, 220);
  }

  function updateJumpLatestButton(box) {
    const button = document.getElementById("kwc-jump-latest");
    if (!button || !box) return;
    updateScrollAffordanceLayout(box);
    const root = document.getElementById("kwc-root");
    const atBottom = isAutoFollowBottom(box);
    const hasUnloadedNewer = !!state.historyHasAfter;
    const show = !!root && !state.minimized && !guestChatHidden() && state.messages.length > 0 && (!atBottom || hasUnloadedNewer);
    button.classList.toggle("kwc-hidden", !show);
  }

  function refreshScrollAffordances(box) {
    if (!box) box = document.getElementById("kwc-messages");
    if (!box) return;
    applyMediaViewportConfig();
    updateHistoryEndNotice(box);
    updateJumpLatestButton(box);
  }

  function applyBottomStackFiller(box, start, end, shouldStickBottom) {
    if (!box || !shouldStickBottom || start !== 0 || end !== state.messages.length) return;
    if (state.historyHasMore) return;
    const sp = ensureVirtualSpacers(box);
    if (!sp.top) return;

    // When all available messages fit inside a tall chat panel, keep them
    // visually stacked from the bottom instead of leaving a large blank area
    // below the latest message. This filler is only visual; it is not used while
    // older history still exists because that case should auto-load more pages.
    sp.top.style.height = "0px";
    const missing = Math.max(0, Math.ceil(box.clientHeight - box.scrollHeight));
    if (missing > 1) sp.top.style.height = missing + "px";
  }

  function scrollInteractionIdleMs() {
    const n = Number(state.config && state.config.uiScrollInteractionIdleMs);
    return Number.isFinite(n) && n >= 50 ? Math.max(50, Math.min(1000, Math.round(n))) : 160;
  }

  function isScrollInteractionActive() {
    return state.scrollbarDragActive || state.touchScrollActive || Date.now() < Number(state.scrollInteractionUntil || 0);
  }

  function mergeRenderOptions(base, next) {
    const merged = Object.assign({}, base || {}, next || {});
    // Later render requests must be able to cancel a previously queued
    // bottom-stick render. The previous OR-merge kept stickToBottom=true even
    // after the user scrolled away, which caused the viewport to be dragged
    // back to the bottom while the virtual range was being recalculated.
    if (next && Object.prototype.hasOwnProperty.call(next, "stickToBottom")) {
      merged.stickToBottom = !!next.stickToBottom;
    } else if (base && Object.prototype.hasOwnProperty.call(base, "stickToBottom")) {
      merged.stickToBottom = !!base.stickToBottom;
    }
    return merged;
  }

  function flushScrollInteractionWork() {
    if (isScrollInteractionActive()) {
      clearTimeout(state.scrollIdleTimer);
      state.scrollIdleTimer = setTimeout(flushScrollInteractionWork, scrollInteractionIdleMs());
      return;
    }
    const pending = state.pendingScrollRenderOptions;
    state.pendingScrollRenderOptions = null;
    if (pending) scheduleVirtualRender(Object.assign({}, pending, {deferDuringScroll: false}));
    if (state.pendingOlderHistoryLoad) {
      state.pendingOlderHistoryLoad = false;
      loadHistory(true);
    }
    if (state.pendingNewerHistoryLoad) {
      state.pendingNewerHistoryLoad = false;
      loadNewerHistory();
    }
    if (state.pendingResumeRefreshReason) {
      const reason = state.pendingResumeRefreshReason;
      state.pendingResumeRefreshReason = "";
      setTimeout(() => refreshOnResume(reason), 0);
    }
  }

  function markScrollInteraction() {
    state.scrollInteractionUntil = Date.now() + scrollInteractionIdleMs();
    clearTimeout(state.scrollIdleTimer);
    state.scrollIdleTimer = setTimeout(flushScrollInteractionWork, scrollInteractionIdleMs());
  }

  function cancelExplicitLatestFollowForUserScroll(reason = "user-scroll") {
    const now = Date.now();
    const hadLatestFollow = now < Number(state.explicitLatestFollowUntil || 0)
      || now < Number(state.forceLatestJumpUntil || 0);
    if (!hadLatestFollow) return;

    // Upload/send/latest actions temporarily request latest-follow so the new
    // message is visible. As soon as the user starts a real scroll, cancel that
    // request so later layout changes cannot pull the viewport back to latest.
    state.explicitLatestFollowUntil = 0;
    state.explicitLatestFollowReason = "";
    state.forceLatestJumpUntil = 0;
    state.autoFollowLatest = false;
    state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), now + 420);
    state.pendingScrollRenderOptions = mergeRenderOptions(state.pendingScrollRenderOptions, {
      preserveScroll: true,
      stickToBottom: false,
      suppressBottomStick: true,
      forcePreservePosition: true
    });
  }

  function markDirectScrollInput() {
    state.lastDirectScrollInputAt = Date.now();
    cancelReplyJumpForUserScroll("direct-scroll-input");
    cancelExplicitLatestFollowForUserScroll("direct-scroll-input");
    markScrollInteraction();
  }

  function markNonScrollUiAction() {
    state.lastNonScrollUiActionAt = Date.now();
    state.historyEndNoticePendingUserTopUntil = 0;
    state.historyEndNoticePendingUserBottomUntil = 0;
  }

  function deferRenderUntilScrollIdle(options = {}) {
    state.pendingScrollRenderOptions = mergeRenderOptions(state.pendingScrollRenderOptions, options);
    markScrollInteraction();
  }

  function requestOlderHistoryAfterScrollIdle() {
    state.pendingOlderHistoryLoad = true;
    markScrollInteraction();
  }

  function requestNewerHistoryAfterScrollIdle() {
    state.pendingNewerHistoryLoad = true;
    markScrollInteraction();
  }

  function newerHistoryRequestCooldownMs() {
    const n = Number(state.config && state.config.uiHistoryNewerRequestCooldownMs);
    if (Number.isFinite(n) && n >= 120) return Math.max(120, Math.min(2500, Math.round(n)));
    return Math.max(300, scrollInteractionIdleMs() * 2);
  }

  function requestNewerHistoryFromBottomInput(reason = "bottom-input") {
    const box = document.getElementById("kwc-messages");
    if (!box || state.minimized || guestChatHidden()) return;
    const atBottomEdge = isAtHistoryBottomRequestZone(box);
    if (atBottomEdge) markHistoryBottomEdgeIntent(reason);
    if (!state.historyHasAfter || !state.historyNewestId) return;
    if (!atBottomEdge && !hasHistoryBottomEdgeIntent()) return;
    if (state.historyLoading) {
      requestNewerHistoryAfterScrollIdle();
      scheduleBottomNewerHistoryRetry(reason);
      return;
    }
    const now = Date.now();
    if (now - Number(state.lastBottomNewerHistoryRequestAt || 0) < newerHistoryRequestCooldownMs()) {
      requestNewerHistoryAfterScrollIdle();
      scheduleBottomNewerHistoryRetry(reason);
      return;
    }
    state.lastBottomNewerHistoryRequestAt = now;
    loadNewerHistory({forceDuringScroll: true, reason});
  }

  function requestOlderHistoryFromTopInput(reason = "top-input") {
    const box = document.getElementById("kwc-messages");
    if (!box || state.minimized || guestChatHidden()) return;
    const atTopEdge = isAtHistoryTopRequestZone(box);
    if (atTopEdge) markHistoryTopEdgeIntent(reason);
    if (!state.historyHasMore || !state.historyOldestId) return;
    if (!atTopEdge && !hasHistoryTopEdgeIntent()) return;

    if (topHistoryLoadBusy()) {
      scheduleTopOlderHistoryRetry(reason);
      return;
    }

    state.lastTopOlderHistoryRequestAt = Date.now();
    rememberUserTopIntent(box);

    // At the physical top of the scroll container additional wheel/touch input
    // does not always produce another scroll event, so the old preload trigger
    // could be missed until the user moved down and up again. Fetch immediately,
    // but do not let repeated wheel ticks start a chain of overlapping height
    // corrections. A short settle window keeps scrollTop preservation from
    // fighting the user's next wheel movement.
    loadHistory(true, {forceDuringScroll: true, reason});
  }

  function requestResumeRefreshAfterScrollIdle(reason) {
    state.pendingResumeRefreshReason = reason || "resume";
    markScrollInteraction();
  }

  function beginTouchScrollInteraction() {
    state.touchScrollActive = true;
    markScrollInteraction();
  }

  function endTouchScrollInteraction() {
    if (!state.touchScrollActive) return;
    state.touchScrollActive = false;
    markScrollInteraction();
  }

  function historyPreloadThresholdPx(box) {
    const c = state.config || {};
    const screens = Number(c.uiHistoryPreloadScreens);
    const safeScreens = Number.isFinite(screens) && screens >= 0 ? Math.min(5, screens) : 0.75;
    const viewport = Math.max(1, box && box.clientHeight ? box.clientHeight : 1);
    const minPx = Number(c.uiHistoryPreloadMinPx);
    const safeMinPx = Number.isFinite(minPx) && minPx >= 0 ? Math.min(1000, minPx) : 160;
    return Math.max(safeMinPx, Math.round(viewport * safeScreens));
  }

  function topHistorySettleMs() {
    const n = Number(state.config && state.config.uiHistoryTopSettleMs);
    if (Number.isFinite(n) && n >= 100) return Math.max(100, Math.min(2000, Math.round(n)));
    return Math.max(520, scrollInteractionIdleMs() * 3);
  }

  function topHistoryRequestCooldownMs() {
    const n = Number(state.config && state.config.uiHistoryTopRequestCooldownMs);
    if (Number.isFinite(n) && n >= 120) return Math.max(120, Math.min(2500, Math.round(n)));
    return Math.max(650, topHistorySettleMs());
  }

  function markOlderHistorySettling() {
    state.olderHistorySettleUntil = Date.now() + topHistorySettleMs();
  }

  function historyEdgeIntentMs() {
    const n = Number(state.config && state.config.uiHistoryEdgeIntentMs);
    if (Number.isFinite(n) && n >= 300) return Math.max(300, Math.min(5000, Math.round(n)));
    return Math.max(1200, Math.min(3200, scrollInteractionIdleMs() * 5));
  }

  function markHistoryTopEdgeIntent(reason = "") {
    state.historyTopEdgeIntentUntil = Date.now() + historyEdgeIntentMs();
  }

  function markHistoryBottomEdgeIntent(reason = "") {
    state.historyBottomEdgeIntentUntil = Date.now() + historyEdgeIntentMs();
  }

  function hasHistoryTopEdgeIntent() {
    return Date.now() < Number(state.historyTopEdgeIntentUntil || 0);
  }

  function hasHistoryBottomEdgeIntent() {
    return Date.now() < Number(state.historyBottomEdgeIntentUntil || 0);
  }

  function isAtHistoryTopRequestZone(box) {
    return !!box && Number(box.scrollTop || 0) <= historyPreloadThresholdPx(box);
  }

  function isAtHistoryBottomRequestZone(box) {
    return !!box && bottomGapPx(box) <= historyPreloadThresholdPx(box);
  }

  function topHistoryBusyTimeoutMs() {
    const n = Number(state.config && state.config.uiHistoryTopBusyTimeoutMs);
    if (Number.isFinite(n) && n >= 3000) return Math.max(3000, Math.min(30000, Math.round(n)));
    return 10000;
  }

  function topHistoryLoadBusy() {
    const now = Date.now();
    const sinceRequest = now - Number(state.lastTopOlderHistoryRequestAt || 0);

    if (state.historyLoading) {
      const since = Number(state.historyLoadingSince || 0);
      const maxBusy = topHistoryBusyTimeoutMs();
      if (since > 0 && now - since > maxBusy) {
        console.warn("[KWC] history loading busy timeout; forcing retry unlock", {
          elapsed: now - since,
          maxBusy
        });
        state.historyLoading = false;
        state.historyLoadingSince = 0;
      } else {
        return true;
      }
    }

    const settleUntil = Number(state.olderHistorySettleUntil || 0);
    if (settleUntil > now) {
      const remaining = settleUntil - now;
      const maxBusy = topHistoryBusyTimeoutMs();
      if (remaining > maxBusy) {
        console.warn("[KWC] older history settle timeout too large; clearing", {
          remaining,
          maxBusy
        });
        state.olderHistorySettleUntil = 0;
      } else {
        return true;
      }
    }

    return sinceRequest < topHistoryRequestCooldownMs();
  }

  function scheduleTopOlderHistoryRetry(reason = "top-retry") {
    const now = Date.now();
    const settleUntil = Number(state.olderHistorySettleUntil || 0);
    const cooldownUntil = Number(state.lastTopOlderHistoryRequestAt || 0) + topHistoryRequestCooldownMs();
    const busyUntil = Math.max(
      Number.isFinite(settleUntil) ? settleUntil : 0,
      Number.isFinite(cooldownUntil) ? cooldownUntil : 0
    );
    const delay = Math.max(120, Math.min(1200, busyUntil > now ? busyUntil - now + 40 : 220));
    const safeDelay = Number.isFinite(delay) ? delay : 220;
    const dueAt = now + safeDelay;

    // Keep one safe retry point. Most repeated wheel/touch retries are ignored,
    // but if the current settle/cooldown window moved later, reschedule to that
    // safer due time instead of firing while virtual-scroll height correction or
    // top-history request throttling is still settling.
    if (state.pendingTopOlderHistoryTimer) {
      const previousDueAt = Number(state.pendingTopOlderHistoryDueAt || 0);
      if (previousDueAt && dueAt <= previousDueAt) return;
      clearTimeout(state.pendingTopOlderHistoryTimer);
    }

    state.pendingTopOlderHistoryDueAt = dueAt;
    state.pendingTopOlderHistoryTimer = setTimeout(() => {
      state.pendingTopOlderHistoryTimer = null;
      state.pendingTopOlderHistoryDueAt = 0;
      requestOlderHistoryFromTopInput(reason + "-retry");
    }, safeDelay);
  }

  function scheduleBottomNewerHistoryRetry(reason = "bottom-retry") {
    const now = Date.now();
    const cooldownUntil = Number(state.lastBottomNewerHistoryRequestAt || 0) + newerHistoryRequestCooldownMs();
    const loadingSince = Number(state.historyLoadingSince || 0);
    const loadingUntil = state.historyLoading && loadingSince > 0 ? loadingSince + 15000 : 0;
    const busyUntil = Math.max(
      Number.isFinite(cooldownUntil) ? cooldownUntil : 0,
      Number.isFinite(loadingUntil) ? loadingUntil : 0
    );
    const delay = Math.max(120, Math.min(1200, busyUntil > now ? busyUntil - now + 40 : 220));
    const safeDelay = Number.isFinite(delay) ? delay : 220;
    const dueAt = now + safeDelay;

    if (state.pendingBottomNewerHistoryTimer) {
      const previousDueAt = Number(state.pendingBottomNewerHistoryDueAt || 0);
      if (previousDueAt && dueAt <= previousDueAt) return;
      clearTimeout(state.pendingBottomNewerHistoryTimer);
    }

    state.pendingBottomNewerHistoryDueAt = dueAt;
    state.pendingBottomNewerHistoryTimer = setTimeout(() => {
      state.pendingBottomNewerHistoryTimer = null;
      state.pendingBottomNewerHistoryDueAt = 0;
      requestNewerHistoryFromBottomInput(reason + "-retry");
    }, safeDelay);
  }

  function isNearBottom(box, tolerance = 2) {
    if (!box) return false;
    const remaining = box.scrollHeight - box.scrollTop - box.clientHeight;
    return remaining <= tolerance;
  }

  function autoFollowBottomThresholdPx(box) {
    const c = state.config || {};
    const configured = Number(c.uiAutoFollowBottomThresholdPx);
    const base = Number.isFinite(configured) ? Math.max(2, Math.min(300, configured)) : 80;
    const viewport = Math.max(1, box && box.clientHeight ? box.clientHeight : 1);
    // Do not let the near-bottom zone become too large on very small or mobile windows.
    return Math.max(2, Math.min(base, Math.round(viewport * 0.35)));
  }

  function isAutoFollowBottom(box) {
    return isNearBottom(box, autoFollowBottomThresholdPx(box));
  }

  function markExplicitLatestFollow(reason = "", ms = 4500) {
    const now = Date.now();
    state.explicitLatestFollowUntil = Math.max(Number(state.explicitLatestFollowUntil || 0), now + Math.max(500, Number(ms) || 4500));
    state.explicitLatestFollowReason = String(reason || "explicit");
    state.preventBottomStickUntil = 0;
    state.autoFollowLatest = true;
  }

  function hasExplicitLatestFollow() {
    return Date.now() <= Number(state.explicitLatestFollowUntil || 0);
  }

  function bottomFollowAllowed(box, options = {}) {
    if (!box) return false;
    const explicit = !!options.latestJump ||
      !!options.forceLatestFollow ||
      !!options.forceStickToBottom ||
      hasExplicitLatestFollow();
    if (explicit) return true;
    // If the current in-memory range is a middle slice loaded by reply jump,
    // the physical bottom is only the bottom of that slice, not the real latest
    // chat position. Do not treat it as auto-follow/latest until newer pages
    // have been loaded or the latest button explicitly reloads the tail page.
    if (state.historyHasAfter) return false;
    return isAutoFollowBottom(box);
  }


  // Virtual scrolling is content-type agnostic. Dynamic content (images,
  // videos, audio, social embeds, YouTube/other iframes, link previews, fonts)
  // is handled by one message-level ResizeObserver below. No media type may
  // extend the virtual range, park an off-range node, or pause reconciliation.

  function setScrollTopPreserved(box, value, options = {}) {
    if (!box) return;

    const maxTop = Math.max(0, Number(box.scrollHeight || 0) - Number(box.clientHeight || 0));
    let requested = Number(value);
    if (!Number.isFinite(requested)) requested = 0;
    requested = Math.max(0, Math.min(maxTop, requested));

    const currentTop = Number(box.scrollTop || 0);
    const threshold = autoFollowBottomThresholdPx(box);
    const beforeGap = Math.max(0, Number(box.scrollHeight || 0) - currentTop - Number(box.clientHeight || 0));
    const requestedGap = Math.max(0, maxTop - requested);

    // A render that was scheduled while the chat was at the latest message must
    // not restore an old scrollTop and create a gap from the bottom. Older-history
    // prepends are the only normal case that may intentionally move away from the
    // bottom via scroll preservation.
    if (!options.allowAwayFromBottom && beforeGap <= threshold && requestedGap > threshold) {
      requested = maxTop;
      state.autoFollowLatest = true;
    }

    // At the bottom, browsers often report maxTop/scrollTop with a 1px rounding
    // difference while video metadata, image dimensions, or virtual spacers settle.
    // Writing scrollTop again for that tiny difference fires another scroll event,
    // which schedules another virtual render, which can look like bottom jitter.
    // Use a much smaller tolerance than before. The old 1-3px tolerance hid
    // harmless scroll writes, but it also allowed visible 1-2px twitching after
    // new messages, virtual spacer recalculation, or anchor restoration.
    // Browser scrollTop can be fractional, so keep a tiny epsilon only to avoid
    // write/read loops caused by sub-pixel rounding.
    const tolerance = Number.isFinite(Number(options.tolerancePx))
      ? Math.max(0, Number(options.tolerancePx))
      : (options.bottomStick ? 0.35 : 0.15);
    if (Math.abs(currentTop - requested) <= tolerance) {
      if (requestedGap <= threshold) state.autoFollowLatest = true;
      return;
    }

    state.suppressAutoFollowUpdate = true;
    state.suppressScrollRenderUntil = Date.now() + Math.max(80, Number(options.suppressRenderMs || 160));
    box.scrollTop = requested;
    setTimeout(() => { state.suppressAutoFollowUpdate = false; }, Math.max(40, Number(options.suppressUpdateMs || 120)));
  }

  function bottomGapPx(box) {
    if (!box) return Infinity;
    return Math.max(0, Number(box.scrollHeight || 0) - Number(box.scrollTop || 0) - Number(box.clientHeight || 0));
  }

  function stickToBottomStable(box) {
    if (!box) return;
    state.autoFollowLatest = true;

    const setBottom = (phase, tolerance = 0.35) => {
      if (!box) return false;
      const maxTop = Math.max(0, Number(box.scrollHeight || 0) - Number(box.clientHeight || 0));
      const gap = bottomGapPx(box);
      if (gap <= tolerance && Math.abs(Number(box.scrollTop || 0) - maxTop) <= tolerance) return false;
      setScrollTopPreserved(box, maxTop, {bottomStick: true, reason: "stick-bottom-" + phase, tolerancePx: tolerance, suppressRenderMs: 180, suppressUpdateMs: 140});
      return true;
    };

    // Do only one synchronous bottom write. Delayed rAF/setTimeout corrections
    // can look like a visible 1-2px second movement after new messages or the
    // latest-jump button. If later media changes create a small gap, leave it
    // alone until the user scrolls or a real latest render happens again.
    setBottom("now", 0.35);
  }

  async function forceLatestChatView(reason = "") {
    const box = document.getElementById("kwc-messages");
    const now = Date.now();
    markExplicitLatestFollow(reason || "latest", 4500);
    state.preventBottomStickUntil = 0;
    state.suppressScrollRenderUntil = now + 220;
    state.forceLatestJumpUntil = now + 900;
    state.autoFollowLatest = true;
    state.pendingScrollRenderOptions = null;
    state.pendingNewerHistoryLoad = false;
    state.virtualPendingRenderOptions = null;

    const options = {
      stickToBottom: true,
      preserveScroll: false,
      preserveVisualAnchor: false,
      latestJump: true,
      forceLatestFollow: true,
      ignoreVisibleRangeProtection: true,
      deferDuringScroll: false,
      allowBottomStickDuringLock: true
    };

    if (box) {
      renderVirtualMessages(options);
      // Latest button is an explicit jump. Render the current tail immediately,
      // then reload the real latest history page. This matters after reply-jump,
      // where state.messages may be a middle slice with unloaded newer records.
      stickToBottomStable(box);
      refreshScrollAffordances(box);
    } else {
      state.virtualPendingRenderOptions = options;
    }

    await loadHistory(false, {forceLatest: true, forceDuringScroll: true});
  }

  function assignMessageKey(msg) {
    if (!msg) msg = {};
    if (msg._kwcKey) return msg._kwcKey;
    if (msg.id) {
      msg._kwcKey = "id:" + String(msg.id);
    } else {
      msg._kwcKey = "local:" + (state.nextLocalMessageId++);
    }
    return msg._kwcKey;
  }

  function messageHeightAt(index) {
    const msg = state.messages[index];
    if (!msg) return state.virtualAverageMessageHeight;
    const h = Number(msg._kwcHeight);
    return Number.isFinite(h) && h > 0 ? h : state.virtualAverageMessageHeight;
  }

  function estimatedHeightUntil(index) {
    let total = 0;
    const max = Math.max(0, Math.min(index, state.messages.length));
    for (let i = 0; i < max; i++) total += messageHeightAt(i);
    return total;
  }

  function estimatedTotalHeight() {
    return estimatedHeightUntil(state.messages.length);
  }

  function updateVirtualSpacersFromMeasuredHeights(box) {
    if (!box || !virtualScrollEnabled()) return;
    const sp = ensureVirtualSpacers(box);
    if (sp.top) sp.top.style.height = Math.max(0, Math.round(estimatedHeightUntil(state.virtualRenderStart))) + "px";
    if (sp.bottom) sp.bottom.style.height = Math.max(0, Math.round(estimatedTotalHeight() - estimatedHeightUntil(state.virtualRenderEnd))) + "px";
  }

  function messageIndexByVirtualKey(key) {
    if (!key) return -1;
    for (let i = 0; i < state.messages.length; i++) {
      const msg = state.messages[i];
      if (msg && assignMessageKey(msg) === key) return i;
    }
    return -1;
  }

  function rectIntersectsViewport(rect, viewport, margin = 24) {
    if (!rect || !viewport) return false;
    return rect.bottom >= viewport.top - margin &&
      rect.top <= viewport.bottom + margin &&
      rect.right >= viewport.left - margin &&
      rect.left <= viewport.right + margin;
  }

  function syncVirtualMessageResizeObserver(box) {
    if (!box || !window.ResizeObserver) return;
    if (!state.virtualMessageResizeObserver) {
      state.virtualMessageResizeObserver = new ResizeObserver(entries => {
        if (!entries || !entries.length) return;
        const liveBox = document.getElementById("kwc-messages");
        if (!liveBox || liveBox !== box || state.minimized || guestChatHidden()) return;

        const keepBottom = !!state.autoFollowLatest && !state.historyHasAfter && !isScrollInteractionActive();
        const anchor = keepBottom ? null : captureScrollAnchor(liveBox);
        const anchorIndex = anchor && anchor.key ? messageIndexByVirtualKey(anchor.key) : -1;
        let deltaAboveAnchor = 0;
        let changed = false;

        for (const entry of entries) {
          const el = entry && entry.target;
          if (!el || !el.classList || !el.classList.contains("kwc-msg") || !liveBox.contains(el)) continue;
          const key = el.dataset && el.dataset.virtualKey;
          const index = messageIndexByVirtualKey(key);
          if (index < 0) continue;
          const msg = state.messages[index];
          if (!msg) continue;
          let marginBottom = 0;
          try { marginBottom = parseFloat(getComputedStyle(el).marginBottom || "0") || 0; } catch (_) {}
          let rect;
          try { rect = el.getBoundingClientRect(); } catch (_) { rect = null; }
          if (!rect) continue;
          const nextHeight = Math.max(1, Math.ceil(Number(rect.height || 0) + marginBottom));
          const oldHeight = Number(msg._kwcHeight) || 0;
          if (!oldHeight) {
            msg._kwcHeight = nextHeight;
            changed = true;
            continue;
          }
          const delta = nextHeight - oldHeight;
          if (Math.abs(delta) <= 0.5) continue;
          msg._kwcHeight = nextHeight;
          changed = true;
          if (!keepBottom && anchorIndex >= 0 && index < anchorIndex) deltaAboveAnchor += delta;
        }

        if (!changed) return;
        updateVirtualSpacersFromMeasuredHeights(liveBox);
        if (keepBottom) {
          stickToBottomStable(liveBox);
        } else if (Math.abs(deltaAboveAnchor) > 0.5) {
          // overflow-anchor is intentionally disabled in CSS, so compensate only
          // for height changes above the current visual anchor. The content type
          // that caused the resize is irrelevant.
          setScrollTopPreserved(liveBox, Number(liveBox.scrollTop || 0) + deltaAboveAnchor, {
            allowAwayFromBottom: true,
            reason: "message-resize-anchor",
            tolerancePx: 0.15,
            suppressRenderMs: 120,
            suppressUpdateMs: 90
          });
        }
        refreshScrollAffordances(liveBox);
      });
    }
    state.virtualMessageResizeObserver.disconnect();
    box.querySelectorAll(":scope > .kwc-msg").forEach(el => state.virtualMessageResizeObserver.observe(el));
  }

  function ensureVirtualSpacers(box) {
    if (!box) return {top: null, bottom: null};
    let top = box.querySelector(":scope > .kwc-virtual-top-spacer");
    let bottom = box.querySelector(":scope > .kwc-virtual-bottom-spacer");
    if (!top) {
      top = document.createElement("div");
      top.className = "kwc-virtual-spacer kwc-virtual-top-spacer";
      box.insertBefore(top, box.firstChild);
    }
    if (!bottom) {
      bottom = document.createElement("div");
      bottom.className = "kwc-virtual-spacer kwc-virtual-bottom-spacer";
      box.appendChild(bottom);
    }
    ensureHistoryEndNotice(box);
    return {top, bottom};
  }

  function renderMessageElement(msg) {
    const el = document.createElement("div");
    el.className = `kwc-msg kwc-role-${esc(msg.role)} kwc-source-${esc(msg.source)}${msg.hidden ? " kwc-deleted" : ""}`;
    const key = assignMessageKey(msg);
    el.dataset.virtualKey = key;
    el.dataset.hidden = msg.hidden ? "1" : "0";
    if (msg.id) el.dataset.id = msg.id;
    if (msg.playerUuid) el.dataset.playerUuid = String(msg.playerUuid);
    if (msg.originServerId) el.dataset.originServerId = String(msg.originServerId);
    if (msg.originServerName) el.dataset.originServerName = String(msg.originServerName);
    el.dataset.displaySender = String(displaySender(msg) || "");
    el.dataset.realSender = String(realSender(msg) || "");
    const messageDmTarget = publicMessageDirectMessageTarget(msg);
    if (messageDmTarget) {
      el.dataset.dmTargetLabel = String(messageDmTarget.label || "");
      el.dataset.remoteMessage = messageDmTarget.remote ? "1" : "0";
    }

    const time = formatMessageTime(msg.time);
    const shownSender = displaySender(msg);
    const originalSender = realSender(msg);
    const renderedSender = originalSender ? preferredSenderText(shownSender, originalSender) : shownSender;
    const senderAttrs = originalSender
      ? ` title="${esc(state.senderIdentityMode === "real" ? senderDisplayTitle(shownSender) : senderOriginalTitle(originalSender))}" data-display-sender="${esc(shownSender)}" data-real-sender="${esc(originalSender)}" data-source="${esc(msg.source || "")}" data-showing-real="${state.senderIdentityMode === "real" ? "1" : "0"}" role="button" tabindex="0"`
      : "";
    const actions = messageActionAvailability(msg);
    const canDelete = actions.canDelete;
    const canPin = actions.canPin;
    const canReply = actions.canReply;
    const miniActionsHtml = (canReply || canPin || canDelete)
      ? `<span class="kwc-mini-actions">${canReply ? `<button class="kwc-mini-action kwc-reply-action" data-reply="${esc(msg.id)}">${t("button.reply", "reply")}</button>` : ""}${canPin ? `<button class="kwc-mini-action" data-pin="${esc(msg.id)}">${t("button.pin", "pin")}</button>` : ""}${canDelete ? `<button class="kwc-mini-action" data-delete="${esc(msg.id)}">${t("button.delete", "delete")}</button>` : ""}</span>`
      : "";
    el.classList.toggle("kwc-has-mini-actions", !!(canReply || canPin || canDelete));
    el.innerHTML = `
      <div class="kwc-meta">
        <span class="kwc-sender${originalSender ? " kwc-sender-has-real" : ""}"${senderAttrs}>${originalSender ? senderNameHtml(shownSender, originalSender, msg.source) : minecraftNameHtml(renderedSender, shouldRenderMinecraftNameColors() && sourceMayRenderMinecraftNameColors(msg.source))}</span><span class="kwc-meta-sep" aria-hidden="true">·</span>${messageOriginSourceHtml(msg)}<span class="kwc-meta-sep" aria-hidden="true">·</span><span class="kwc-time-actions"><span class="kwc-time" data-time="${esc(msg.time || "")}" title="${esc(timeToggleTitle(msg.time))}" role="button" tabindex="0">${esc(time)}</span>${miniActionsHtml}</span>
      </div>
      ${replyReferenceHtml(msg)}
      <div class="kwc-text">${messageTextHtml(msg)}</div>
      ${safeImagePreviews(plainDisplayMessageText(msg), key)}
    `;
    installSenderIdentityToggle(el);
    installTimeToggle(el);
    el.querySelectorAll("[data-dm-target-uuid]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        const target = publicMessageDirectMessageTargetFromElement(btn, el, msg);
        openDirectMessageForTarget(target);
      });
    });
    el.querySelectorAll("[data-reply]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        startReplyToMessage(messageById(btn.dataset.reply || "") || msg);
      });
    });
    el.querySelectorAll("[data-reply-jump]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        jumpToReplyTarget(btn.dataset.replyJump || "");
      });
    });
    el.querySelectorAll(".kwc-youtube-card").forEach(btn => {
      btn.addEventListener("click", () => {
        const embed = btn.dataset.youtubeEmbed || "";
        if (!/^https:\/\/(www\.)?youtube(-nocookie)?\.com\/embed\//i.test(embed)) return;
        const key = btn.dataset.youtubeKey || "";
        if (key) {
          state.youtubeOpen.add(key);
          if (!state.config || state.config.youtubeRememberExpanded !== false) state.youtubeExpanded.add(key);
        }
        const isShorts = btn.dataset.youtubeShorts === "1";
        const wrap = document.createElement("div");
        wrap.className = isShorts ? "kwc-youtube-wrap kwc-youtube-shorts-wrap" : "kwc-youtube-wrap";
        if (key) wrap.setAttribute("data-youtube-key", key);
        wrap.style.cssText = youtubeShellStyle(isShorts, "");
        const safeEmbed = safeYouTubeEmbedUrl(embed);
        if (!safeEmbed) return;
        const iframe = document.createElement("iframe");
        iframe.className = "kwc-youtube-frame";
        iframe.style.cssText = "position:absolute;inset:0;width:100%;height:100%;border:0;";
        iframe.src = safeEmbed;
        iframe.title = t("media.youtubeTitle", "YouTube video");
        iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
        iframe.referrerPolicy = "strict-origin-when-cross-origin";
        iframe.allowFullscreen = true;
        wrap.appendChild(iframe);
        btn.replaceWith(wrap);
      }, {once: true});
    });
    el.querySelectorAll(".kwc-social-card").forEach(card => {
      const load = card.querySelector(".kwc-media-load");
      if (!load) return;
      load.addEventListener("click", () => {
        const kind = card.dataset.socialKind || "";
        const src = card.dataset.socialSrc || "";
        const key = card.dataset.previewKey || previewKey(kind, src);
        if (key) state.mediaOpen.add(key);
        const html = socialEmbedHtml({type: kind, href: src, tiktokId: kind === "tiktok" ? (card.dataset.tiktokId || "") : "", previewKey: key}, "");
        const wrap = document.createElement("div");
        wrap.innerHTML = html;
        const next = wrap.firstElementChild;
        if (!next) return;
        card.replaceWith(next);
        if (kind === "x") loadXWidgets(next);
      }, {once: true});
    });
    hydrateSocialEmbeds(el);
    el.querySelectorAll(".kwc-media-card").forEach(card => {
      const load = card.querySelector(".kwc-media-load");
      if (!load) return;
      load.addEventListener("click", () => {
        const kind = card.dataset.mediaKind || "";
        const src = card.dataset.mediaSrc || "";
        const openHref = card.dataset.mediaOpen || src;
        const key = card.dataset.previewKey || previewKey(kind, src);
        const safeSrc = safePreviewUrl(src);
        if (!safeSrc) return;
        if (key) state.mediaOpen.add(key);
        const wrap = document.createElement("div");
        wrap.className = kind === "audio" ? "kwc-audio-wrap" : "kwc-video-wrap";
        if (key) wrap.setAttribute("data-preview-key", key);
        const media = createMediaElement(kind, safeSrc, key);
        if (media) wrap.appendChild(media);
        if (media) {
          media.addEventListener("error", () => {
            window.__kwcPreviewFailed && window.__kwcPreviewFailed(key);
            setMediaError(wrap, kind);
          }, {once: true});
        }
        card.replaceWith(wrap);
        if (media && kind === "video" && typeof media.play === "function") {
          const playPromise = media.play();
          if (playPromise && typeof playPromise.catch === "function") playPromise.catch(() => {});
        }
      }, {once: true});
    });
    hydratePreviewMedia(el);
    // Scroll/layout lifecycle is message-level, not media-level. Keep only
    // preview failure handling here; ResizeObserver handles every height change
    // uniformly regardless of the child element type.
    el.querySelectorAll("img:not(.kwc-custom-emoji), video, audio").forEach(media => {
      media.addEventListener("error", () => {
        if (media.classList.contains("kwc-image-preview")) {
          window.__kwcPreviewFailed && window.__kwcPreviewFailed(media.dataset.previewKey);
          setMediaError(media.closest(".kwc-image-link"), "image");
        } else if (media.classList.contains("kwc-video-preview") || media.classList.contains("kwc-audio-preview")) {
          const kind = media.tagName === "AUDIO" ? "audio" : "video";
          window.__kwcPreviewFailed && window.__kwcPreviewFailed(media.dataset.previewKey);
          setMediaError(media.closest(kind === "audio" ? ".kwc-audio-wrap" : ".kwc-video-wrap"), kind);
        }
      }, {once: true});
    });
    return el;
  }

  function renderAllMessages(box) {
    const {top, bottom} = ensureVirtualSpacers(box);
    Array.from(box.querySelectorAll(":scope > .kwc-msg")).forEach(el => el.remove());
    if (top) top.style.height = "0px";
    if (bottom) bottom.style.height = "0px";
    for (const msg of state.messages) {
      box.insertBefore(renderMessageElement(msg), bottom || null);
    }
    measureRenderedMessages(box);
    syncVirtualMessageResizeObserver(box);
    syncTransientYoutubeOpen(box);
  }


  function messageActionAvailability(msg) {
    const moderationEnabled = !state.config || state.config.moderationEnabled !== false;
    const canModerate = state.token && moderationEnabled && (state.role === "ADMIN" || state.role === "MODERATOR");
    const canReply = !!(msg && msg.id && !msg.hidden);
    return {
      canReply,
      canDelete: state.moderationActionsVisible && canModerate && msg && msg.id && !msg.hidden && (state.role === "ADMIN" || !state.config || state.config.allowModeratorMessageDelete !== false),
      canPin: state.moderationActionsVisible && state.token && (state.role === "ADMIN" || state.role === "MODERATOR") && state.pinsEnabled !== false && state.pinsCanPin !== false && msg && msg.id && !msg.hidden && !isMessagePinned(msg.id)
    };
  }

  function syncMessageElementActions(el, msg) {
    if (!el || !msg) return;
    const meta = el.querySelector(":scope > .kwc-meta");
    if (!meta) return;

    meta.querySelectorAll(":scope > .kwc-mini-actions, :scope > .kwc-mini-action[data-pin], :scope > .kwc-mini-action[data-delete], :scope .kwc-time-actions > .kwc-mini-actions").forEach(btn => btn.remove());

    const actions = messageActionAvailability(msg);
    const hasActions = !!(actions.canReply || actions.canPin || actions.canDelete);
    el.classList.toggle("kwc-has-mini-actions", hasActions);
    if (!hasActions) return;

    let timeActions = meta.querySelector(":scope > .kwc-time-actions");
    if (!timeActions) {
      const timeEl = meta.querySelector(":scope > .kwc-time");
      timeActions = document.createElement("span");
      timeActions.className = "kwc-time-actions";
      if (timeEl) {
        meta.insertBefore(timeActions, timeEl);
        timeActions.appendChild(timeEl);
      } else {
        meta.appendChild(timeActions);
      }
    }

    const wrap = document.createElement("span");
    wrap.className = "kwc-mini-actions";

    if (actions.canReply) {
      const reply = document.createElement("button");
      reply.className = "kwc-mini-action kwc-reply-action";
      reply.type = "button";
      reply.setAttribute("data-reply", String(msg.id));
      reply.textContent = t("button.reply", "reply");
      reply.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        startReplyToMessage(messageById(String(msg.id)) || msg);
      });
      wrap.appendChild(reply);
    }

    if (actions.canPin) {
      const pin = document.createElement("button");
      pin.className = "kwc-mini-action";
      pin.type = "button";
      pin.setAttribute("data-pin", String(msg.id));
      pin.textContent = t("button.pin", "pin");
      wrap.appendChild(pin);
    }
    if (actions.canDelete) {
      const del = document.createElement("button");
      del.className = "kwc-mini-action";
      del.type = "button";
      del.setAttribute("data-delete", String(msg.id));
      del.textContent = t("button.delete", "delete");
      wrap.appendChild(del);
    }
    timeActions.appendChild(wrap);
  }

  function messageElementNeedsRebuild(el, msg) {
    if (!el || !msg) return false;
    const hiddenNow = el.dataset && el.dataset.hidden === "1";
    if (hiddenNow !== !!msg.hidden) return true;
    // A message can be replaced in-place by moderation events while virtual
    // scroll keeps the existing DOM node alive. If the rendered text does not
    // match the hidden/deleted state, rebuild immediately instead of waiting
    // for the node to be culled and recreated by a later scroll.
    if (msg.hidden && !el.classList.contains("kwc-deleted")) return true;
    return false;
  }

  function rebuildRenderedMessageElement(el, msg) {
    if (!el || !msg || !el.parentNode) return el;
    const fresh = renderMessageElement(msg);
    el.replaceWith(fresh);
    try { if (state.virtualMessageResizeObserver) state.virtualMessageResizeObserver.observe(fresh); } catch (_) {}
    return fresh;
  }

  function syncRenderedMessageStateForId(id) {
    if (!id) return;
    const box = document.getElementById("kwc-messages");
    if (!box) return;
    const sid = String(id);
    const msg = state.messages.find(m => m && String(m.id) === sid);
    if (!msg) return;
    box.querySelectorAll(`:scope > .kwc-msg[data-id="${cssEscape(sid)}"]`).forEach(el => {
      if (messageElementNeedsRebuild(el, msg)) el = rebuildRenderedMessageElement(el, msg);
      syncMessageElementActions(el, msg);
    });
  }

  function syncRenderedMessageActions() {
    const box = document.getElementById("kwc-messages");
    if (!box) return;
    box.querySelectorAll(":scope > .kwc-msg").forEach(el => {
      const key = el.dataset && el.dataset.virtualKey;
      const id = el.dataset && el.dataset.id;
      const msg = state.messages.find(m => m && ((key && m._kwcKey === key) || (id && m.id === id)));
      if (msg) syncMessageElementActions(el, msg);
    });
  }


  function refreshRenderedMessagesForLocale() {
    const box = document.getElementById("kwc-messages");
    if (!box) return;
    const anchor = captureScrollAnchor(box);
    const wasNearBottom = isAutoFollowBottom(box);
    box.querySelectorAll(":scope > .kwc-msg").forEach(el => {
      const key = el.dataset && el.dataset.virtualKey;
      const id = el.dataset && el.dataset.id;
      const msg = state.messages.find(m => m && ((key && m._kwcKey === key) || (id && String(m.id) === String(id))));
      if (!msg) return;
      const fresh = renderMessageElement(msg);
      el.replaceWith(fresh);
    });
    applyTimeDisplayMode();
    syncVirtualMessageResizeObserver(box);
    if (wasNearBottom) stickToBottomStable(box);
    else if (anchor) restoreScrollAnchor(box, anchor, {thresholdPx: 2.5, reason: "maintenance-anchor-restore"});
  }

  function syncTransientYoutubeOpen(box) {
    if (!box || !state.config || state.config.youtubeRememberExpanded !== false) return;
    const visible = new Set();
    box.querySelectorAll(".kwc-youtube-wrap[data-youtube-key]").forEach(el => {
      const key = el.getAttribute("data-youtube-key") || "";
      if (key) visible.add(key);
    });
    for (const key of Array.from(state.youtubeOpen)) {
      if (!visible.has(key)) state.youtubeOpen.delete(key);
    }
  }

  function measureRenderedMessages(box) {
    if (!box) return;
    const rendered = Array.from(box.querySelectorAll(":scope > .kwc-msg"));
    let total = 0;
    let count = 0;
    for (const el of rendered) {
      const key = el.dataset.virtualKey;
      const msg = state.messages.find(m => m && m._kwcKey === key);
      const rect = el.getBoundingClientRect();
      const h = Math.max(1, Math.ceil(rect.height + parseFloat(getComputedStyle(el).marginBottom || "0")));
      if (msg) msg._kwcHeight = h;
      total += h;
      count++;
    }
    if (count > 0) {
      state.virtualAverageMessageHeight = Math.max(18, Math.min(240, total / count));
    }
  }

  function findIndexForOffset(offset) {
    let y = 0;
    for (let i = 0; i < state.messages.length; i++) {
      const h = messageHeightAt(i);
      if (y + h >= offset) return i;
      y += h;
    }
    return Math.max(0, state.messages.length - 1);
  }

  function captureScrollAnchor(box) {
    if (!box) return null;
    const viewport = box.getBoundingClientRect();
    const messages = Array.from(box.querySelectorAll(":scope > .kwc-msg"));
    for (const el of messages) {
      const rect = el.getBoundingClientRect();
      if (rect.bottom >= viewport.top + 1) {
        const key = el.dataset && el.dataset.virtualKey;
        if (!key) continue;
        return {
          key,
          offset: rect.top - viewport.top
        };
      }
    }
    return null;
  }

  function restoreScrollAnchor(box, anchor, options = {}) {
    if (!box || !anchor || !anchor.key) return false;
    const el = box.querySelector(`:scope > .kwc-msg[data-virtual-key="${CSS.escape(anchor.key)}"]`);
    if (!el) return false;
    const viewport = box.getBoundingClientRect();
    const rect = el.getBoundingClientRect();
    const desiredTop = viewport.top + Number(anchor.offset || 0);
    const delta = rect.top - desiredTop;
    // Avoid visible micro-corrections after a jump/render. Corrections smaller
    // than this are usually fractional spacer/layout settling and look worse
    // when written back as a second 1-2px scroll movement. Older-history prepends
    // pass a smaller threshold explicitly because that path needs exact anchoring.
    const threshold = Number.isFinite(Number(options.thresholdPx)) ? Math.max(0, Number(options.thresholdPx)) : 1.75;
    if (Math.abs(delta) > threshold) {
      setScrollTopPreserved(box, box.scrollTop + delta, {
        allowAwayFromBottom: true,
        reason: options.reason || "anchor-restore",
        tolerancePx: Math.min(0.75, Math.max(0.15, threshold / 3))
      });
    }
    return true;
  }


  function scheduleViewportMaintenance(reason = "", delay = 900) {
    // This is a soft, invisible housekeeping pass. It does not remove loaded
    // message history; it only clears transient caches and re-commits the
    // current virtual range when the user is idle. The goal is to avoid long
    // sessions accumulating stale media/layout state while preserving the exact
    // visible anchor.
    clearTimeout(state.viewportMaintenanceTimer);
    const wait = Math.max(400, Math.min(5000, Number(delay) || 900));
    state.viewportMaintenanceDueAt = Date.now() + wait;
    state.viewportMaintenanceTimer = setTimeout(() => runViewportMaintenance(reason), wait);
  }

  function runViewportMaintenance(reason = "") {
    state.viewportMaintenanceTimer = null;
    state.viewportMaintenanceDueAt = 0;
    const box = document.getElementById("kwc-messages");
    if (!box || state.minimized || guestChatHidden()) return;
    if (state.historyLoading || state.virtualRenderScheduled || isScrollInteractionActive()) {
      scheduleViewportMaintenance(reason || "busy", 1200);
      return;
    }

    // Keep this pass visually silent. Earlier versions re-rendered the current
    // virtual range and restored an anchor here, but that can still appear as a
    // small late movement. Cache cleanup is safe and invisible; virtual range
    // cleanup is left to normal scroll/history renders.
    if (!virtualScrollEnabled() || state.messages.length === 0) return;
    refreshScrollAffordances(box);
  }

  function preserveOlderHistoryViewportAfterRender(box, prevTop, prevHeight, anchor, label = "older-history") {
    if (!box) return;
    const expectedFromCurrentHeight = () => {
      const delta = Math.max(0, Number(box.scrollHeight || 0) - Number(prevHeight || 0));
      return { delta, expectedTop: Math.max(0, Number(prevTop || 0) + delta) };
    };
    const restore = () => {
      if (!box || !document.body.contains(box)) return;
      const {delta, expectedTop} = expectedFromCurrentHeight();
      const restoredAnchor = anchor ? restoreScrollAnchor(box, anchor, {thresholdPx: 0.5, reason: label + "-anchor"}) : false;

      // If older rows/spacers were inserted above the viewport, scrollTop must
      // be moved down by the height delta. Without this fallback, wheel-up at
      // the physical top can keep loading older pages while scrollTop remains 0,
      // which appears as a frozen chat log.
      if (delta > 0 && (!restoredAnchor || (Number(prevTop || 0) <= 2 && Number(box.scrollTop || 0) <= 2))) {
        setScrollTopPreserved(box, expectedTop, {allowAwayFromBottom: true, reason: label});
      }
    };

    restore();
    requestAnimationFrame(restore);
    setTimeout(restore, 80);
  }

  function visibleMessageIndices(box, margin = 0) {
    const indices = new Set();
    if (!box) return indices;
    const viewport = box.getBoundingClientRect();
    for (const el of box.querySelectorAll(":scope > .kwc-msg")) {
      if (!el || !el.dataset) continue;
      const key = el.dataset.virtualKey;
      if (!key) continue;
      let rect;
      try {
        rect = el.getBoundingClientRect();
      } catch (_) {
        continue;
      }
      // While any message content changes size, the estimated-height model can
      // temporarily lag. Keep messages that are physically visible or close to
      // the viewport in the render range until the observer updates the height.
      if (!rectIntersectsViewport(rect, viewport, margin)) continue;
      const index = messageIndexByVirtualKey(key);
      if (index >= 0) indices.add(index);
    }
    return indices;
  }

  function viewportVisibleMessageArea(box, margin = 0) {
    const result = {count: 0, totalHeight: 0, firstKey: "", lastKey: ""};
    if (!box) return result;
    const viewport = box.getBoundingClientRect();
    for (const el of box.querySelectorAll(":scope > .kwc-msg")) {
      if (!el || !el.dataset) continue;
      let rect;
      try {
        rect = el.getBoundingClientRect();
      } catch (_) {
        continue;
      }
      if (!rectIntersectsViewport(rect, viewport, margin)) continue;
      let style;
      try {
        style = getComputedStyle(el);
      } catch (_) {
        style = null;
      }
      if (style && (style.display === "none" || style.visibility === "hidden" || Number(style.opacity) === 0)) continue;
      const clippedTop = Math.max(rect.top, viewport.top);
      const clippedBottom = Math.min(rect.bottom, viewport.bottom);
      const visibleHeight = Math.max(0, clippedBottom - clippedTop);
      if (margin <= 0 && visibleHeight <= 0) continue;
      result.count++;
      result.totalHeight += Math.max(0, visibleHeight);
      const key = el.dataset.virtualKey || "";
      if (!result.firstKey) result.firstKey = key;
      result.lastKey = key;
    }
    return result;
  }

  function isVirtualViewportNearEmpty(box) {
    if (!box || !state.messages || state.messages.length === 0) return false;
    const area = viewportVisibleMessageArea(box, 0);
    const minArea = Math.min(120, Math.max(40, Number(box.clientHeight || 1) * 0.20));
    return area.count === 0 || area.totalHeight < minArea;
  }

  function rememberGoodVirtualViewport(box) {
    if (!box || !state.messages || state.messages.length === 0) return;
    const area = viewportVisibleMessageArea(box, 0);
    const minArea = Math.min(120, Math.max(40, Number(box.clientHeight || 1) * 0.20));
    if (area.count > 0 && area.totalHeight >= minArea) {
      const anchor = captureScrollAnchor(box);
      if (anchor && anchor.key) {
        state.lastGoodVirtualAnchor = anchor;
        state.lastGoodVirtualAnchorAt = Date.now();
      }
    }
  }

  function maybeScheduleBlankRescueRender(box, options = {}) {
    if (!box || !virtualScrollEnabled() || !state.messages || state.messages.length === 0) return;
    if (options.blankRescue === true) return;
    if (!isVirtualViewportNearEmpty(box)) {
      rememberGoodVirtualViewport(box);
      return;
    }
    const now = Date.now();
    if (now - Number(state.lastBlankRescueAt || 0) < 350) return;
    state.lastBlankRescueAt = now;
    scheduleVirtualRender({
      preserveScroll: true,
      deferDuringScroll: false,
      blankRescue: true,
      anchor: state.lastGoodVirtualAnchor || null
    });
  }

  function expandVirtualRangeForVisibleMessages(start, end, protectedIndices, count, guard = 2) {
    if (!protectedIndices || protectedIndices.size === 0) return {start, end};
    let min = Infinity;
    let max = -1;
    for (const index of protectedIndices) {
      if (!Number.isFinite(index) || index < 0 || index >= count) continue;
      min = Math.min(min, index);
      max = Math.max(max, index);
    }
    if (!Number.isFinite(min) || max < 0) return {start, end};
    const pad = Math.max(1, Math.min(12, Math.floor(Number(guard) || 2)));
    return {
      start: Math.max(0, Math.min(start, min - pad)),
      end: Math.min(count, Math.max(end, max + pad + 1))
    };
  }

  function renderVirtualMessages(options = {}) {
    if (Date.now() < Number(state.forceLatestJumpUntil || 0)) {
      options = Object.assign({}, options, {
        stickToBottom: true,
        preserveScroll: false,
        preserveVisualAnchor: false,
        latestJump: true,
        ignoreVisibleRangeProtection: true,
        deferDuringScroll: false,
          allowBottomStickDuringLock: true
      });
    }
    const box = document.getElementById("kwc-messages");
    if (!box) return;
    // Virtual scrolling is authoritative over message-node lifetime regardless of
    // content type. Only the requested [start,end) range is kept in the scroller;
    // off-range nodes are removed and recreated from message state when needed.
    const prevScrollTop = box.scrollTop;
    const bottomStickSuppressed = options.forcePreservePosition === true || options.suppressBottomStick === true || (Date.now() < Number(state.preventBottomStickUntil || 0) && options.allowBottomStickDuringLock !== true);
    const explicitLatestFollow = !bottomStickSuppressed && (
      !!options.latestJump ||
      !!options.forceLatestFollow ||
      !!options.forceStickToBottom ||
      hasExplicitLatestFollow()
    );
    const actuallyNearBottom = bottomStickSuppressed ? false : (!state.historyHasAfter && isAutoFollowBottom(box));
    const explicitlyStickBottom = !!options.stickToBottom && (explicitLatestFollow || actuallyNearBottom);
    let shouldStickBottom = !bottomStickSuppressed && (explicitlyStickBottom || actuallyNearBottom);
    const preserveBottomAfterRender = !options.anchor && shouldStickBottom;
    if (preserveBottomAfterRender) {
      state.autoFollowLatest = true;
    } else if (!actuallyNearBottom && !explicitLatestFollow) {
      state.autoFollowLatest = false;
    }

    // For normal non-bottom virtual renders, preserve the user's visual anchor
    // instead of the raw scrollTop. Media load/error, spacer recalculation, and
    // range expansion can change the height above the viewport after scrolling
    // stops. Keeping only scrollTop stable makes the visible image/text appear
    // to jump. Restoring the first visible message keeps the pixels the user was
    // looking at in place while the scrollbar size/position settles.
    const visualAnchor = (!preserveBottomAfterRender && !options.anchor && options.preserveScroll !== false && options.preserveVisualAnchor !== false)
      ? captureScrollAnchor(box)
      : null;

    if (!virtualScrollEnabled()) {
      renderAllMessages(box);
      if (preserveBottomAfterRender) stickToBottomStable(box);
      else if (options.preserveScroll !== false) setScrollTopPreserved(box, prevScrollTop);
      return;
    }

    const {top, bottom} = ensureVirtualSpacers(box);
    const count = state.messages.length;
    if (count === 0) {
      Array.from(box.querySelectorAll(":scope > .kwc-msg")).forEach(el => el.remove());
      if (top) top.style.height = "0px";
      if (bottom) bottom.style.height = "0px";
      state.virtualRenderStart = 0;
      state.virtualRenderEnd = 0;
      refreshScrollAffordances(box);
      return;
    }

    const viewport = Math.max(1, box.clientHeight || 1);
    const overscanPx = viewport * virtualOverscanScreens();
    const totalHeight = estimatedTotalHeight();
    const targetCount = virtualRenderTargetMessageCount(viewport, overscanPx);

    let start;
    let end;
    if (shouldStickBottom) {
      // When entering the latest-chat view, render the newest messages first.
      // Rendering from scrollTop=0 and then jumping to bottom leaves the viewport
      // sitting on the bottom spacer, which looks like an empty/black chat area
      // until a resize or large scroll forces a second render.
      end = count;
      const startOffset = Math.max(0, totalHeight - viewport - overscanPx);
      start = findIndexForOffset(startOffset);
    } else if (Number.isFinite(Number(options.focusIndex))) {
      const focusIndex = Math.max(0, Math.min(count - 1, Math.floor(Number(options.focusIndex))));
      const focusCenter = estimatedHeightUntil(focusIndex) + Math.max(1, messageHeightAt(focusIndex)) / 2;
      const startOffset = Math.max(0, focusCenter - viewport / 2 - overscanPx);
      const endOffset = Math.min(totalHeight, focusCenter + viewport / 2 + overscanPx);
      start = findIndexForOffset(startOffset);
      end = findIndexForOffset(endOffset) + 1;
    } else {
      const startOffset = Math.max(0, box.scrollTop - overscanPx);
      const endOffset = Math.min(totalHeight, box.scrollTop + viewport + overscanPx);
      start = findIndexForOffset(startOffset);
      end = findIndexForOffset(endOffset) + 1;
    }

    if (end - start < targetCount) {
      if (shouldStickBottom) {
        start = Math.max(0, end - targetCount);
      } else {
        const need = targetCount - (end - start);
        const before = Math.floor(need / 2);
        start = Math.max(0, start - before);
        end = Math.min(count, start + targetCount);
        start = Math.max(0, Math.min(start, end - targetCount));
      }
    }
    start = Math.max(0, Math.min(start, count));
    end = Math.max(start, Math.min(end, count));

    const skipVisibleRangeProtection = !!options.ignoreVisibleRangeProtection || !!options.latestJump;
    // Keep only a small guard around messages that are physically visible. No
    // child content gets a separate retention window: the range is driven only by
    // scroll position and measured message heights.
    const visibleProtectMargin = Math.max(120, Math.min(480, Math.round(viewport * 0.5)));
    const protectedVisibleIndices = skipVisibleRangeProtection ? new Set() : visibleMessageIndices(box, visibleProtectMargin);
    if (!skipVisibleRangeProtection) {
      const expanded = expandVirtualRangeForVisibleMessages(start, end, protectedVisibleIndices, count, 3);
      start = expanded.start;
      end = expanded.end;
    }

    if (options.blankRescue === true) {
      let center = findIndexForOffset(Math.max(0, Number(box.scrollTop || 0) + viewport / 2));
      const anchorKey = options.anchor && options.anchor.key ? options.anchor.key : (state.lastGoodVirtualAnchor && state.lastGoodVirtualAnchor.key);
      const anchorIndex = anchorKey ? messageIndexByVirtualKey(anchorKey) : -1;
      if (anchorIndex >= 0) center = anchorIndex;
      const rescueSpan = Math.max(targetCount * 4, 120);
      start = Math.max(0, Math.min(start, center - Math.floor(rescueSpan / 2)));
      end = Math.min(count, Math.max(end, center + Math.ceil(rescueSpan / 2)));
    }

    // Reconcile the virtual range deterministically. The DOM order must always be
    // exactly state.messages[start..end). Existing nodes are left attached when
    // they are already in the correct relative position, so an on-screen iframe
    // is not needlessly moved/reloaded. Off-range nodes are removed unconditionally
    // instead of being parked invisibly inside the scroller.
    const desiredKeys = [];
    const desiredKeySet = new Set();
    for (let i = start; i < end; i++) {
      const msg = state.messages[i];
      if (!msg) continue;
      const key = assignMessageKey(msg);
      desiredKeys.push(key);
      desiredKeySet.add(key);
    }

    Array.from(box.querySelectorAll(":scope > .kwc-msg")).forEach(el => {
      const key = el.dataset && el.dataset.virtualKey;
      if (!key || !desiredKeySet.has(key)) el.remove();
    });

    if (top) top.style.height = Math.max(0, Math.round(estimatedHeightUntil(start))) + "px";
    if (bottom) bottom.style.height = Math.max(0, Math.round(estimatedTotalHeight() - estimatedHeightUntil(end))) + "px";

    const renderedByKey = new Map();
    Array.from(box.querySelectorAll(":scope > .kwc-msg")).forEach(el => {
      const key = el.dataset && el.dataset.virtualKey;
      // A duplicate DOM key is never valid. Keep the first node and remove the
      // duplicate immediately so anchor lookup has one unambiguous target.
      if (!key || renderedByKey.has(key)) {
        el.remove();
        return;
      }
      renderedByKey.set(key, el);
    });

    let cursor = top ? top.nextSibling : box.firstChild;
    for (let i = start; i < end; i++) {
      const msg = state.messages[i];
      if (!msg) continue;
      const key = assignMessageKey(msg);
      let el = renderedByKey.get(key);
      if (el && messageElementNeedsRebuild(el, msg)) {
        const fresh = renderMessageElement(msg);
        el.replaceWith(fresh);
        el = fresh;
        renderedByKey.set(key, el);
      } else if (el) {
        syncMessageElementActions(el, msg);
      } else {
        el = renderMessageElement(msg);
        renderedByKey.set(key, el);
      }

      // In the normal scrolling case cursor === el, so no DOM move occurs. Only
      // move a node when the DOM is actually out of canonical message order.
      if (cursor !== el) box.insertBefore(el, cursor || bottom || null);
      cursor = el.nextSibling;
    }

    // The bottom spacer is the only valid direct child after the rendered range.
    // Remove any stale message node left behind by a previous inconsistent render.
    while (cursor && cursor !== bottom) {
      const next = cursor.nextSibling;
      if (cursor.classList && cursor.classList.contains("kwc-msg")) cursor.remove();
      cursor = next;
    }
    state.virtualRenderStart = start;
    state.virtualRenderEnd = end;
    measureRenderedMessages(box);
    syncVirtualMessageResizeObserver(box);
    syncTransientYoutubeOpen(box);
    if (top) top.style.height = Math.max(0, Math.round(estimatedHeightUntil(start))) + "px";
    if (bottom) bottom.style.height = Math.max(0, Math.round(estimatedTotalHeight() - estimatedHeightUntil(end))) + "px";
    applyBottomStackFiller(box, start, end, shouldStickBottom);
    if (preserveBottomAfterRender) scheduleHistoryViewportFill("bottom-render");
    if (preserveBottomAfterRender) {
      // Bottom auto-follow wins over scroll-anchor restoration so late layout
      // changes cannot pull the latest chat upward.
      stickToBottomStable(box);
    } else if (options.anchor && restoreScrollAnchor(box, options.anchor, {thresholdPx: Number.isFinite(Number(options.anchorThresholdPx)) ? Number(options.anchorThresholdPx) : 0.5, reason: "explicit-anchor-restore"})) {
      // Anchor restore keeps the user's current viewport stable after prepending older history.
    } else if (visualAnchor && restoreScrollAnchor(box, visualAnchor, {thresholdPx: options.maintenanceRender ? 2.5 : 1.75, reason: options.maintenanceRender ? "maintenance-visual-anchor" : "visual-anchor-restore"})) {
      // Normal virtual renders should keep the visible message in place. The
      // scrollbar can resize, but already visible content should not jump when
      // spacers are recalculated.
    } else if (shouldStickBottom && state.autoFollowLatest) stickToBottomStable(box);
    else if (options.preserveScroll !== false) setScrollTopPreserved(box, prevScrollTop);

    // A late content resize can change estimated heights after the range was
    // calculated. If the viewport becomes effectively empty, immediately
    // re-render a wider rescue range instead of waiting for another scroll.
    if (!options.latestJump) maybeScheduleBlankRescueRender(box, options);
    refreshScrollAffordances(box);
  }

  function scheduleVirtualRender(options = {}) {
    if (options.deferDuringScroll !== false && isScrollInteractionActive() && !options.stickToBottom && !options.anchor) {
      deferRenderUntilScrollIdle(options);
      return;
    }
    state.virtualPendingRenderOptions = mergeRenderOptions(state.virtualPendingRenderOptions, options);
    if (state.virtualRenderScheduled) return;
    state.virtualRenderScheduled = true;
    requestAnimationFrame(() => {
      const opts = state.virtualPendingRenderOptions || {};
      state.virtualPendingRenderOptions = null;
      state.virtualRenderScheduled = false;
      renderVirtualMessages(opts);
    });
  }

  function addMessage(msg, options = {}) {
    const box = document.getElementById("kwc-messages");
    if (!box || !msg) return;
    // Auto-follow new incoming chat only when the viewport is physically near
    // the bottom or a user action explicitly requested the latest view
    // (send/upload/latest button). Do not trust a stale autoFollowLatest flag.
    const explicitLatestFollow = !options.prepend && options.suppressAutoFollow !== true && (options.forceStickToBottom || hasExplicitLatestFollow());
    const canUsePhysicalBottomForFollow = !options.prepend && options.suppressAutoFollow !== true && !state.historyHasAfter;
    const wasNearBottom = canUsePhysicalBottomForFollow && isAutoFollowBottom(box);
    const shouldFollowLatest = !options.prepend && (wasNearBottom || explicitLatestFollow);
    if (shouldFollowLatest) {
      state.autoFollowLatest = true;
    } else if (!options.prepend) {
      state.autoFollowLatest = false;
    }
    const key = assignMessageKey(msg);
    let idx = -1;
    if (msg.id) idx = state.messages.findIndex(m => m && m.id === msg.id);
    if (idx < 0) idx = state.messages.findIndex(m => m && m._kwcKey === key);
    if (idx >= 0) {
      state.messages[idx] = Object.assign(state.messages[idx], msg, {_kwcKey: state.messages[idx]._kwcKey});
    } else if (options.prepend) {
      state.messages.unshift(msg);
    } else {
      state.messages.push(msg);
    }
    if (!options.skipRender) {
      const renderOptions = {
        stickToBottom: shouldFollowLatest,
        preserveScroll: !shouldFollowLatest,
        deferDuringScroll: !shouldFollowLatest,
        forceLatestFollow: explicitLatestFollow,
};
      if (!options.prepend && !shouldFollowLatest && isScrollInteractionActive()) deferRenderUntilScrollIdle(renderOptions);
      else renderVirtualMessages(renderOptions);
      if (!options.prepend && shouldFollowLatest) stickToBottomStable(box);
      if (!options.prepend) scheduleViewportMaintenance("message", shouldFollowLatest ? 1400 : 2200);
    }
  }

  function markMessageDeleted(id) {
    if (!id) return;
    const box = document.getElementById("kwc-messages");
    const wasNearBottom = box ? isAutoFollowBottom(box) : !!state.autoFollowLatest;
    const wasAtHistoryEnd = !!box && !state.historyLoading && !state.historyHasMore && state.messages.length > 0 && Number(box.scrollTop || 0) <= historyPreloadThresholdPx(box) + 6;
    if (wasAtHistoryEnd) state.forceHistoryEndNoticeUntil = Math.max(Number(state.forceHistoryEndNoticeUntil || 0), Date.now() + 1600);
    const anchor = box && !wasNearBottom && !wasAtHistoryEnd ? captureScrollAnchor(box) : null;
    const prevTop = box ? Number(box.scrollTop || 0) : 0;
    const prevAutoFollow = !!state.autoFollowLatest;
    const msg = state.messages.find(m => m && String(m.id) === String(id));
    if (msg && msg.hidden === true) {
      if (!wasNearBottom) {
        state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), Date.now() + 1400);
        state.autoFollowLatest = false;
        if (wasAtHistoryEnd && box) setScrollTopPreserved(box, 0, {allowAwayFromBottom: true, reason: "delete-history-end-preserve", suppressRenderMs: 220, suppressUpdateMs: 180});
        refreshScrollAffordances(box);
      }
      return;
    }
    if (msg) {
      msg.hidden = true;
      msg.message = t("message.deleted", "[deleted]");
    }
    // Deleting/rebuilding a message can shrink the estimated content height.
    // If we decide bottom-follow after that shrink, a mid-history viewport may
    // suddenly look "near bottom" and get dragged to the latest message. Capture
    // the pre-delete anchor first and force a non-bottom render unless the user
    // really was already at the bottom before pressing delete. Keep a short
    // bottom-stick lock as well, because the optimistic delete and the SSE delete
    // event can arrive in separate frames and otherwise re-enable auto-follow.
    if (!wasNearBottom) {
      state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), Date.now() + 1400);
      state.autoFollowLatest = false;
    }
    renderVirtualMessages({
      stickToBottom: wasNearBottom,
      preserveScroll: !wasNearBottom,
      anchor: !wasNearBottom && !wasAtHistoryEnd ? anchor : null,
      forcePreservePosition: !wasNearBottom,
      suppressBottomStick: !wasNearBottom,
      deferDuringScroll: false
    });
    if (!wasNearBottom) {
      state.autoFollowLatest = false;
      const afterBox = document.getElementById("kwc-messages");
      if (afterBox && wasAtHistoryEnd) {
        state.forceHistoryEndNoticeUntil = Math.max(Number(state.forceHistoryEndNoticeUntil || 0), Date.now() + 1600);
        setScrollTopPreserved(afterBox, 0, {allowAwayFromBottom: true, reason: "delete-history-end-preserve", suppressRenderMs: 260, suppressUpdateMs: 180});
      } else if (afterBox && !anchor) {
        setScrollTopPreserved(afterBox, prevTop, {allowAwayFromBottom: true, reason: "delete-preserve-scroll", suppressRenderMs: 260, suppressUpdateMs: 180});
      }
      refreshScrollAffordances(afterBox);
    } else {
      state.autoFollowLatest = prevAutoFollow || wasNearBottom;
    }
  }

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
    root.style.setProperty("--kwc-chat-message-font-size", fontPx(userSize == null ? configuredMessageSize : userSize, configuredMessageSize));
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

  async function loadConfig() {
    try {
      const data = await api("/config");
      state.config = data;
      state.userProfilesEnabled = data.uiUserProfilesEnabled === true;
      state.userProfilesMaxProfiles = Math.max(0, Math.min(20, Math.floor(Number(data.uiUserProfilesMaxProfiles) || 0)));
      state.userProfilesAllowImportExport = data.uiUserProfilesAllowImportExport !== false;
      const pageSize = Number(data.historyPageSize);
      state.historyPageSize = Number.isFinite(pageSize) && pageSize >= 0 ? Math.floor(pageSize) : 20;
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
      state.directMessageConfirmHide = data.directMessageConfirmHide !== false;
      state.groupChatEnabled = data.groupChatEnabled === true;
      state.groupChatAllowWebSend = data.groupChatAllowWebSend !== false;
      state.groupChatAllowPublicRooms = data.groupChatAllowPublicRooms !== false;
      state.groupChatAllowRoomPasswords = data.groupChatAllowRoomPasswords !== false;
      state.groupChatMaxMessageLength = Math.max(0, Math.floor(Number(data.groupChatMaxMessageLength) || 0));
      state.groupChatRetentionDays = Math.max(0, Math.floor(Number(data.groupChatRetentionDays) || 0));
      state.groupChatConfirmLeave = data.groupChatConfirmLeave !== false;
      state.groupChatConfirmHide = data.groupChatConfirmHide !== false;
      state.browserNotificationsEnabled = data.browserNotificationsEnabled !== false;
      state.browserNotificationsOnlyWhenHidden = data.browserNotificationsOnlyWhenHidden !== false;
      state.browserNotificationsNotifyNormalChat = data.browserNotificationsNotifyNormalChat !== false;
      state.browserNotificationsNotifyDm = data.browserNotificationsNotifyDm !== false;
      state.browserNotificationsNotifyGroupChat = data.browserNotificationsNotifyGroupChat !== false;
      state.browserNotificationsNotifyMentions = data.browserNotificationsNotifyMentions !== false;
      state.browserNotificationsNotifyReplies = data.browserNotificationsNotifyReplies !== false;
      state.browserNotificationsNotifySystem = data.browserNotificationsNotifySystem !== false;
      state.browserNotificationsNotifyKeywords = data.browserNotificationsNotifyKeywords !== false;
      state.browserNotificationsShowMessagePreview = data.browserNotificationsShowMessagePreview !== false;
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
      state.webPushNotifySystem = data.webPushNotifySystem !== false;
      state.webPushNotifyKeywords = data.webPushNotifyKeywords !== false;
      state.webPushShowMessagePreview = data.webPushShowMessagePreview !== false;
      state.emojiEnabled = data.emojiEnabled !== false;
      state.emojiShowButton = data.emojiShowButton !== false;
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
        document.getElementById("kwc-captcha-q").textContent = data.question;
        document.getElementById("kwc-captcha-a").value = "";
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
      let replyProgrammaticScroll = now < Number(state.replyJumpUntil || 0);
      if (replyProgrammaticScroll && !directScrollInputActive && replyJumpLooksUserMoved(box, Number(state.replyJumpStartedAt || 0))) {
        cancelReplyJumpForUserScroll("scroll-drift");
        replyProgrammaticScroll = false;
      }
      const programmaticScroll = !directScrollInputActive && (now < Number(state.suppressScrollRenderUntil || 0) || replyProgrammaticScroll);
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

      if (!state.suppressAutoFollowUpdate && !programmaticScroll) {
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
        addMessage(msg);
        maybeNotifyChatMessage(msg);
      } catch (_) {}
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
        loadDirectMessageThreads(true).then(() => {
          const thread = (state.dmThreads || []).find(t => String(t.id || "") === String(data.threadId || ""));
          if (thread) maybeNotifyDirectThread(thread);
          if (state.dmModalOpen && state.dmActiveThreadId && (!data.threadId || data.threadId === state.dmActiveThreadId)) {
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
        loadGroupChatRooms(true).then(() => {
          const room = (state.groupRooms || []).find(r => String(r.id || "") === String(data.roomId || ""));
          if (room) maybeNotifyGroupRoom(room);
          if (state.groupModalOpen && state.groupActiveRoomId && (!data.roomId || data.roomId === state.groupActiveRoomId)) {
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


  function canUseCustomEmoji() {
    return !!(state.emojiEnabled && state.emojiShowButton !== false && Array.isArray(state.emojiItems) && state.emojiItems.length > 0 && !state.minimized);
  }

  function updateEmojiButton() {
    const btn = document.getElementById("kwc-emoji");
    if (!btn) return;
    const visible = canUseCustomEmoji();
    btn.classList.toggle("kwc-hidden", !visible);
    btn.title = t("button.emoji", "Emoji");
    if (!visible) hideEmojiPanel();
  }

  function hideEmojiPanel() {
    state.emojiPanelOpen = false;
    const panel = document.getElementById("kwc-emoji-panel");
    if (panel) panel.classList.add("kwc-hidden");
    updateEmojiResizeHandleVisibility();
  }

  function toggleEmojiPanel() {
    if (!canUseCustomEmoji()) return;
    state.emojiPanelOpen = !state.emojiPanelOpen;
    renderEmojiPanel();
  }

  function emojiTokenCount(text) {
    let count = 0;
    const re = customEmojiTokenRegex();
    let match;
    const source = String(text || "");
    while ((match = re.exec(source)) !== null) {
      if (customEmojiByToken(match[1])) count++;
    }
    return count;
  }

  function endsWithCustomEmojiToken(text) {
    const source = String(text || "");
    const re = customEmojiBoundaryRegex("end");
    const match = re.exec(source);
    if (!match) return false;
    const tokenMatch = customEmojiTokenRegex().exec(match[0]);
    return !!(tokenMatch && customEmojiByToken(tokenMatch[1]));
  }

  function startsWithCustomEmojiToken(text) {
    const source = String(text || "");
    const re = customEmojiBoundaryRegex("start");
    const match = re.exec(source);
    if (!match) return false;
    const tokenMatch = customEmojiTokenRegex().exec(match[0]);
    return !!(tokenMatch && customEmojiByToken(tokenMatch[1]));
  }

  function setActiveComposeInput(inputOrId) {
    const id = typeof inputOrId === "string" ? inputOrId : (inputOrId && inputOrId.id);
    if (id) state.activeComposeInputId = id;
  }

  function activeComposeInput() {
    const preferred = document.getElementById(state.activeComposeInputId || "");
    if (preferred && !preferred.disabled && document.body.contains(preferred)) return preferred;
    const active = document.activeElement;
    if (active && active.id && (active.id === "kwc-group-input" || active.id === "kwc-dm-input" || active.id === "kwc-message")) return active;
    return document.getElementById("kwc-message") || document.getElementById("kwc-dm-input") || document.getElementById("kwc-group-input");
  }

  function insertCustomEmoji(id) {
    id = String(id || "");
    if (!customEmojiById(id)) return;
    const input = activeComposeInput();
    if (!input) return;
    setActiveComposeInput(input);
    const limit = Number(state.emojiMessageTokenLimit || 0);
    if (limit > 0 && emojiTokenCount(input.value) >= limit) {
      alert(fmt("alert.emojiTooMany", "Only {max} emoji(s) can be used in one message.", {max: limit}));
      return;
    }
    const token = customEmojiTokenForId(id);
    const start = Number(input.selectionStart);
    const end = Number(input.selectionEnd);
    const hasSelection = Number.isFinite(start) && Number.isFinite(end);
    const current = input.value || "";
    if (hasSelection) {
      const before = current.slice(0, start);
      const after = current.slice(end);
      // Insert exactly the selected emoji token. Do not synthesize whitespace:
      // spacing around emojis is user-authored and consecutive tokens stay exact.
      input.value = before + token + after;
      const caret = (before + token).length;
      try { input.selectionStart = input.selectionEnd = caret; } catch (_) {}
    } else {
      const current = input.value || "";
      input.value = current + token;
      try { input.selectionStart = input.selectionEnd = input.value.length; } catch (_) {}
    }
    input.focus();
  }


  function hideEmojiAutocomplete() {
    const panel = document.getElementById("kwc-emoji-autocomplete");
    if (panel) panel.remove();
    state.emojiAutocomplete = null;
  }

  function emojiButtonHtml(item) {
    if (!item || !item.id || !item.url) return "";
    const label = item.label || item.name || item.id;
    const size = emojiPickerSizePx();
    return `<button type="button" class="kwc-emoji-item" data-emoji-id="${esc(item.id)}" title="${esc(label)}"><img src="${esc(item.url)}" alt="${esc(label)}" loading="lazy" draggable="false" style="width:${size}px;height:${size}px;"><span>${esc(label)}</span></button>`;
  }

  function installEmojiItemHandlers(panel) {
    panel.querySelectorAll("[data-emoji-id]").forEach(btn => {
      btn.addEventListener("click", () => insertCustomEmoji(btn.dataset.emojiId || ""));
    });
  }

  function renderEmojiPanel() {
    const panel = document.getElementById("kwc-emoji-panel");
    if (!panel) return;
    if (!state.emojiPanelOpen || !canUseCustomEmoji()) {
      hideEmojiPanel();
      return;
    }

    const packs = Array.isArray(state.emojiPacks) ? state.emojiPacks : [];
    const items = Array.isArray(state.emojiItems) ? state.emojiItems : [];
    if (!items.length) {
      panel.innerHTML = `<div class="kwc-emoji-scroll"><div class="kwc-emoji-empty">${esc(t("emoji.empty", "No emojis configured."))}</div></div>`;
      panel.classList.remove("kwc-hidden");
      setEmojiPanelHeight(emojiPanelHeightPx(), false);
      installEmojiPanelWheelStep(panel);
      updateEmojiResizeHandleVisibility();
      return;
    }

    let selectedPack = String(state.emojiSelectedPack || localStorage.getItem("kwc.emojiPack") || "");
    if (!selectedPack || (packs.length && !packs.some(pack => String(pack.id || "") === selectedPack))) {
      selectedPack = packs[0] && packs[0].id ? String(packs[0].id) : "";
    }
    state.emojiSelectedPack = selectedPack;
    try { localStorage.setItem("kwc.emojiPack", selectedPack); } catch (_) {}

    const packTabs = packs.length > 1
      ? `<div class="kwc-emoji-tabs">${packs.map(pack => {
          const id = String(pack.id || "");
          return `<button type="button" class="kwc-emoji-tab${id === selectedPack ? " kwc-active" : ""}" data-emoji-pack="${esc(id)}">${esc(pack.label || id)} <span>${esc(pack.count || "")}</span></button>`;
        }).join("")}</div>`
      : "";
    const shown = selectedPack ? items.filter(item => String(item.pack || "") === selectedPack) : items;
    panel.innerHTML = packTabs + `<div class="kwc-emoji-scroll"><div class="kwc-emoji-grid">${shown.map(emojiButtonHtml).join("")}</div></div>`;
    panel.classList.remove("kwc-hidden");
    setEmojiPanelHeight(emojiPanelHeightPx(), false);
    installEmojiPanelWheelStep(panel);

    panel.querySelectorAll("[data-emoji-pack]").forEach(btn => {
      btn.addEventListener("click", () => {
        const pack = btn.dataset.emojiPack || "";
        state.emojiSelectedPack = pack;
        try { localStorage.setItem("kwc.emojiPack", pack); } catch (_) {}
        panel.querySelectorAll(".kwc-emoji-tab").forEach(tab => tab.classList.toggle("kwc-active", tab === btn));
        const grid = panel.querySelector(".kwc-emoji-grid");
        if (grid) grid.innerHTML = items.filter(item => String(item.pack || "") === pack).map(emojiButtonHtml).join("");
        const scroll = emojiScrollElement(panel);
        if (scroll) scroll.scrollTop = 0;
        setEmojiPanelHeight(emojiPanelHeightPx(), false);
        installEmojiItemHandlers(panel);
        updateEmojiResizeHandleVisibility();
      });
    });
    installEmojiItemHandlers(panel);
    updateEmojiResizeHandleVisibility();
  }

  function clearEmojiRetryTimer() {
    if (!state.emojiRetryTimer) return;
    clearTimeout(state.emojiRetryTimer);
    state.emojiRetryTimer = null;
  }

  function emojiRetryDelayMs() {
    const attempt = Math.max(0, Number(state.emojiRetryAttempt || 0));
    const base = Math.min(60000, 1000 * Math.pow(2, attempt));
    const jitter = Math.floor(Math.random() * 500);
    return Math.max(1000, Math.floor(base + jitter));
  }

  function scheduleEmojiRetry(reason = "catalog-load-failed") {
    if (state.emojiRetryTimer || !state.config || state.config.emojiEnabled === false) return;
    const delay = emojiRetryDelayMs();
    state.emojiRetryAttempt = Math.min(10, Number(state.emojiRetryAttempt || 0) + 1);
    state.emojiRetryTimer = setTimeout(() => {
      state.emojiRetryTimer = null;
      loadEmojis({force: true, retryOnFailure: true, reason}).catch(() => {});
    }, delay);
  }

  async function loadEmojis(options = {}) {
    if (!state.config || state.config.emojiEnabled === false) {
      clearEmojiRetryTimer();
      state.emojiRetryAttempt = 0;
      state.emojiEnabled = false;
      state.emojiPacks = [];
      state.emojiItems = [];
      state.emojiById = new Map();
      state.emojiByAlias = new Map();
      updateEmojiButton();
      updateDirectMessageComposeControls();
      updateGroupChatComposeControls();
      return;
    }
    state.emojiLoading = true;
    try {
      const force = options && options.force === true;
      const res = await api("/emojis" + (force ? ("?_=" + Date.now()) : ""), force ? {cache: "no-store"} : {});
      state.emojiEnabled = res.enabled !== false;
      state.emojiPacks = Array.isArray(res.packs) ? res.packs : [];
      state.emojiItems = (Array.isArray(res.items) ? res.items : []).map(item => Object.assign({}, item, {
        url: apiResourceUrl(item && item.url)
      }));
      rebuildCustomEmojiLookups(state.emojiItems);
      // Do not bulk-preload every registered emoji here. Large catalogs can flood
      // the same HTTP origin with image requests and compete with the long-lived
      // SSE connection. Message emoji <img> nodes already use an empty alt value,
      // so the transport token is never painted while an image is loading.
      state.emojiRenderSizePx = Math.max(16, Math.min(1024, Number(res.renderSizePx ?? state.emojiRenderSizePx ?? 32)));
      state.emojiPickerSizePx = Math.max(24, Math.min(1024, Number(res.pickerSizePx ?? state.emojiPickerSizePx ?? 44)));
      applyEmojiPickerSize();
      updateDirectMessageComposeControls();
      updateGroupChatComposeControls();
      // Zero is a valid server value meaning unlimited; do not use `||` here.
      state.emojiMessageTokenLimit = Math.max(0, Math.floor(Number(res.messageTokenLimit ?? state.emojiMessageTokenLimit ?? 0)));
      state.emojiTokenFormat = normalizeEmojiTokenFormat(res.tokenFormat ?? state.emojiTokenFormat);
      clearEmojiRetryTimer();
      state.emojiRetryAttempt = 0;
      if (state.messages && state.messages.length) scheduleVirtualRender({preserveScroll: true, deferDuringScroll: false});
    } catch (e) {
      // A transient catalog failure must not erase the last known-good emoji list.
      // Existing messages/pickers continue using the cached client-side catalog
      // while a bounded exponential retry obtains a fresh copy.
      console.warn("KOKOTO WebChat emoji list failed; keeping previous catalog", {
        endpoint: "/emojis",
        status: Number(e && e.status || 0) || undefined,
        error: e && e.response && e.response.error ? String(e.response.error) : String(e && e.message || e || "unknown")
      });
      if (options.retryOnFailure !== false) scheduleEmojiRetry(options.reason || "catalog-load-failed");
      if (options.throwOnFailure === true) throw e;
      return false;
    } finally {
      state.emojiLoading = false;
      updateEmojiButton();
      updateDirectMessageComposeControls();
      updateGroupChatComposeControls();
      renderEmojiPanel();
    }
  }

  function canUpload() {
    const c = state.config || {};
    if (!c.uploadEnabled) return false;
    if (state.token) {
      if (state.role === "ADMIN") return c.uploadAllowAdmin !== false;
      if (state.role === "MODERATOR") return c.uploadAllowModerator !== false;
      return c.uploadAllowUser !== false;
    }
    return !!c.uploadAllowGuest && c.guestEnabled !== false;
  }

  function uploadAcceptList() {
    const exts = (state.config && Array.isArray(state.config.uploadAllowedExtensions))
      ? state.config.uploadAllowedExtensions
      : [];
    return exts.map(e => "." + String(e).replace(/^\./, "").trim()).filter(Boolean).join(",");
  }

  function normalizeInsertedMediaLinks(text) {
    const parts = String(text || "")
      .split(/\s+/)
      .map(part => part.trim())
      .filter(Boolean);
    return parts.length ? parts.join(" ") + " " : "";
  }

  function appendToMessage(text, options = {}) {
    const input = activeComposeInput();
    if (!input) return;
    setActiveComposeInput(input);
    const inserted = options.mediaLinks ? normalizeInsertedMediaLinks(text) : String(text || "");
    if (!inserted) return;

    const current = input.value || "";
    const needsSeparator = current && !/\s$/.test(current);
    input.value = current + (needsSeparator ? " " : "") + inserted;
    input.focus();
    try {
      input.selectionStart = input.selectionEnd = input.value.length;
    } catch (_) {}
  }

  function extensionFromMime(type) {
    type = String(type || "").toLowerCase();
    if (type === "image/jpeg") return "jpg";
    if (type === "image/png") return "png";
    if (type === "image/gif") return "gif";
    if (type === "image/webp") return "webp";
    if (type === "image/avif") return "avif";
    if (type === "image/bmp") return "bmp";
    if (type === "video/mp4") return "mp4";
    if (type === "video/webm") return "webm";
    if (type === "application/zip") return "zip";
    return "";
  }

  function looksLikeWindowsShortFileName(name) {
    const raw = String(name || "").trim();
    const dot = raw.lastIndexOf(".");
    const base = dot > 0 ? raw.slice(0, dot) : raw;
    const ext = dot > 0 ? raw.slice(dot + 1) : "";
    return /^[^~\/]{1,6}~[0-9]+$/i.test(base) && (!ext || /^[A-Za-z0-9]{1,3}$/.test(ext));
  }

  function clipboardFileName(file, index) {
    const rawName = String(file && file.name || "").trim();
    // Windows/Chromium can expose a DOS 8.3 alias (for example
    // 202608~1.JPG) for files pasted from Explorer. That alias is not the
    // user's actual source filename, so never preserve it as an "original"
    // name when no better clipboard File entry is available.
    if (rawName && rawName.includes(".") && !looksLikeWindowsShortFileName(rawName)) return rawName;

    const c = state.config || {};
    const fromMime = extensionFromMime(file && file.type);
    const rawExt = rawName.includes(".") ? rawName.slice(rawName.lastIndexOf(".") + 1).toLowerCase() : "";
    const configured = String(c.uploadClipboardImageDefaultExtension || "png").replace(/^\./, "").trim().toLowerCase();
    const ext = fromMime || rawExt || configured || "png";
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    return `clipboard-${stamp}-${index + 1}.${ext}`;
  }

  function normalizeUploadFile(file, index, source) {
    if (!file) return null;
    const name = source === "clipboard" ? clipboardFileName(file, index) : (file.name || clipboardFileName(file, index));
    if (typeof File !== "undefined" && file.name !== name) {
      try {
        return new File([file], name, {type: file.type || "application/octet-stream", lastModified: file.lastModified || Date.now()});
      } catch (_) {
        // Some older browsers do not allow File construction; fall through.
      }
    }
    return file;
  }


  function uploadProgressScopeId() {
    const id = String(state.activeComposeInputId || "");
    if (id === "kwc-dm-input" && document.getElementById("kwc-dm-upload-progress")) return "dm";
    if (id === "kwc-group-input" && document.getElementById("kwc-group-upload-progress")) return "group";
    return "main";
  }

  function uploadProgressElements(scope = null) {
    const name = scope || uploadProgressScopeId();
    const prefix = name === "dm" ? "kwc-dm-upload-progress" : (name === "group" ? "kwc-group-upload-progress" : "kwc-upload-progress");
    return {
      panel: document.getElementById(prefix),
      text: document.getElementById(prefix + "-text") || document.getElementById("kwc-upload-progress-text"),
      fill: document.getElementById(prefix + "-fill") || document.getElementById("kwc-upload-progress-fill"),
      cancel: document.getElementById(prefix + "-cancel") || document.getElementById("kwc-upload-cancel")
    };
  }

  function hideInactiveUploadProgressPanels(activeScope = null) {
    const keep = activeScope || uploadProgressScopeId();
    ["main", "dm", "group"].forEach(scope => {
      if (scope === keep) return;
      const el = uploadProgressElements(scope);
      if (el.panel) el.panel.classList.add("kwc-hidden");
    });
  }

  function setUploadProgressVisible(visible) {
    const scope = uploadProgressScopeId();
    hideInactiveUploadProgressPanels(scope);
    const panel = uploadProgressElements(scope).panel;
    if (panel) panel.classList.toggle("kwc-hidden", !visible);
  }

  function setUploadControlsBusy(busy) {
    const uploadBtn = document.getElementById("kwc-upload");
    const fileInput = document.getElementById("kwc-file");
    const dmUploadBtn = document.getElementById("kwc-dm-upload");
    const dmFileInput = document.getElementById("kwc-dm-file");
    const groupUploadBtn = document.getElementById("kwc-group-upload");
    const groupFileInput = document.getElementById("kwc-group-file");
    if (uploadBtn) uploadBtn.disabled = !!busy;
    if (fileInput) fileInput.disabled = !!busy;
    if (dmUploadBtn) dmUploadBtn.disabled = !!busy;
    if (dmFileInput) dmFileInput.disabled = !!busy;
    if (groupUploadBtn) groupUploadBtn.disabled = !!busy;
    if (groupFileInput) groupFileInput.disabled = !!busy;
  }

  function updateUploadProgress(label, percent, active = true) {
    const scope = uploadProgressScopeId();
    hideInactiveUploadProgressPanels(scope);
    const {panel, text, fill, cancel} = uploadProgressElements(scope);

    if (panel) panel.classList.toggle("kwc-hidden", !active);
    if (text) text.textContent = label || "";
    if (fill) {
      const p = Math.max(0, Math.min(100, Number(percent) || 0));
      fill.style.width = p.toFixed(1) + "%";
    }
    if (cancel) {
      cancel.disabled = !active || !state.uploadActive;
      cancel.textContent = state.uploadCancelRequested ? t("upload.canceling", "Canceling...") : t("button.cancel", "Cancel");
    }
  }

  function hideUploadProgressSoon(label) {
    if (label) updateUploadProgress(label, 100, true);
    setTimeout(() => {
      if (state.uploadActive) return;
      setUploadProgressVisible(false);
      updateUploadProgress("", 0, false);
    }, 900);
  }

  function cancelCurrentUpload() {
    if (!state.uploadActive) return;
    state.uploadCancelRequested = true;
    updateUploadProgress(t("upload.canceling", "Canceling..."), 0, true);
    try {
      if (state.uploadXhr) state.uploadXhr.abort();
    } catch (_) {}
  }

  function uploadFormWithProgress(form, progressCallback) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      state.uploadXhr = xhr;

      xhr.upload.onprogress = event => {
        if (event && event.lengthComputable && typeof progressCallback === "function") {
          progressCallback(event.loaded, event.total);
        }
      };

      xhr.onload = () => {
        state.uploadXhr = null;
        let data = null;
        try {
          data = JSON.parse(xhr.responseText || "{}");
        } catch (_) {
          data = {ok: false, error: "invalid_response"};
        }
        if (xhr.status < 200 || xhr.status >= 300) {
          reject({error: data && data.error ? data.error : ("HTTP " + xhr.status)});
          return;
        }
        resolve(data);
      };

      xhr.onerror = () => {
        state.uploadXhr = null;
        reject({error: "network"});
      };

      xhr.onabort = () => {
        state.uploadXhr = null;
        reject({aborted: true});
      };

      xhr.open("POST", apiBase + "/upload", true);
      if (state.token) xhr.setRequestHeader("Authorization", "Bearer " + state.token);
      xhr.send(form);
    });
  }

  function clipboardFiles(event) {
    const dt = event.clipboardData;
    if (!dt) return [];

    // On Windows Chromium, DataTransferItem#getAsFile() may expose the DOS
    // 8.3 alias while DataTransfer.files exposes the long filename. Prefer the
    // FileList and use item files only as a fallback/repair source.
    const listFiles = dt.files && dt.files.length ? Array.from(dt.files).filter(Boolean) : [];
    const itemFiles = [];
    if (dt.items && dt.items.length) {
      for (const item of Array.from(dt.items)) {
        if (item.kind !== "file") continue;
        const file = item.getAsFile();
        if (file) itemFiles.push(file);
      }
    }

    if (!listFiles.length) return itemFiles;
    if (!itemFiles.length) return listFiles;

    const count = Math.max(listFiles.length, itemFiles.length);
    const files = [];
    for (let i = 0; i < count; i++) {
      const listFile = listFiles[i] || null;
      const itemFile = itemFiles[i] || null;
      if (!listFile) { if (itemFile) files.push(itemFile); continue; }
      if (!itemFile) { files.push(listFile); continue; }
      const listShort = looksLikeWindowsShortFileName(listFile.name);
      const itemShort = looksLikeWindowsShortFileName(itemFile.name);
      files.push(listShort && !itemShort ? itemFile : listFile);
    }
    return files;
  }

  async function handlePasteUpload(event) {
    const c = state.config || {};
    if (!c.uploadClipboardEnabled) return;

    const files = clipboardFiles(event);
    if (!files.length) return;

    event.preventDefault();
    event.stopPropagation();
    await uploadFiles(files, "clipboard");
  }

  function isFileDragEvent(event) {
    const dt = event && event.dataTransfer;
    if (!dt) return false;
    try {
      const types = Array.from(dt.types || []);
      return types.includes("Files") || types.includes("application/x-moz-file");
    } catch (_) {
      return false;
    }
  }

  function dropEventFiles(event) {
    const dt = event && event.dataTransfer;
    if (!dt || !dt.files || !dt.files.length) return [];
    return Array.from(dt.files).filter(file => file && (file.name || file.size || file.type));
  }

  function setDropOverlayVisible(visible, messageKey = "") {
    const overlay = document.getElementById("kwc-drop-overlay");
    if (!overlay) return;

    const title = document.getElementById("kwc-drop-title");
    const subtitle = document.getElementById("kwc-drop-subtitle");

    if (messageKey === "busy") {
      if (title) title.textContent = t("upload.dropBusy", "Upload is already in progress.");
      if (subtitle) subtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    } else if (messageKey === "denied") {
      if (title) title.textContent = t("upload.dropDenied", "File upload is not allowed.");
      if (subtitle) subtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    } else {
      if (title) title.textContent = t("upload.dropTitle", "Drop files to upload");
      if (subtitle) subtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    }

    overlay.classList.toggle("kwc-hidden", !visible);
    overlay.setAttribute("aria-hidden", visible ? "false" : "true");
  }

  function hideDropOverlay() {
    state.dragUploadDepth = 0;
    setDropOverlayVisible(false);
  }

  function installDragAndDropUpload(root) {
    const panel = root && root.querySelector ? root.querySelector(".kwc-panel") : null;
    if (!panel || panel.dataset.dropUploadInstalled === "1") return;
    panel.dataset.dropUploadInstalled = "1";

    const overlayState = () => {
      if (state.uploadActive) return "busy";
      if (!canUpload()) return "denied";
      return "ready";
    };

    panel.addEventListener("dragenter", event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      if (state.minimized) {
        hideDropOverlay();
        if (event.dataTransfer) event.dataTransfer.dropEffect = "none";
        return;
      }
      state.dragUploadDepth++;
      const status = overlayState();
      if (event.dataTransfer) event.dataTransfer.dropEffect = status === "ready" ? "copy" : "none";
      setDropOverlayVisible(true, status === "ready" ? "" : status);
    }, {capture: true});

    panel.addEventListener("dragover", event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      if (state.minimized) {
        hideDropOverlay();
        if (event.dataTransfer) event.dataTransfer.dropEffect = "none";
        return;
      }
      const status = overlayState();
      if (event.dataTransfer) event.dataTransfer.dropEffect = status === "ready" ? "copy" : "none";
      setDropOverlayVisible(true, status === "ready" ? "" : status);
    }, {capture: true});

    panel.addEventListener("dragleave", event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      state.dragUploadDepth = Math.max(0, state.dragUploadDepth - 1);
      if (state.dragUploadDepth === 0) setDropOverlayVisible(false);
    }, {capture: true});

    panel.addEventListener("drop", async event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      const files = dropEventFiles(event);
      hideDropOverlay();
      if (state.minimized || !files.length) return;
      if (state.uploadActive) {
        alert(t("upload.dropBusy", "Upload is already in progress."));
        return;
      }
      await uploadFiles(files, "drop");
    }, {capture: true});

    document.addEventListener("dragend", hideDropOverlay, {capture: true});
    document.addEventListener("drop", event => {
      if (isFileDragEvent(event)) hideDropOverlay();
    }, {capture: true});
  }

  async function uploadSelectedFiles(e) {
    const input = e.target;
    const files = Array.from(input.files || []);
    input.value = "";
    await uploadFiles(files, "file");
  }

  function uploadProgressHtml(prefix) {
    const id = String(prefix || "kwc-upload-progress");
    return `<div class="kwc-upload-progress kwc-hidden" id="${id}" aria-live="polite">
      <div class="kwc-upload-progress-head">
        <span id="${id}-text">${esc(t("upload.ready", "Ready"))}</span>
        <button class="kwc-button kwc-upload-cancel" id="${id}-cancel" type="button">${esc(t("button.cancel", "Cancel"))}</button>
      </div>
      <div class="kwc-upload-progress-bar"><div id="${id}-fill"></div></div>
    </div>`;
  }

  async function uploadFiles(files, source) {
    files = Array.from(files || []).map((file, index) => normalizeUploadFile(file, index, source)).filter(Boolean);
    if (!files.length) return;

    if (state.uploadActive) {
      alert(t("upload.dropBusy", "Upload is already in progress."));
      return;
    }

    if (!canUpload()) {
      alert(t("alert.uploadNotAllowed", "File upload is not allowed."));
      return;
    }

    const c = state.config || {};
    const maxFilesConfig = Number(c.uploadMaxFilesPerMessage);
    const maxFiles = Number.isFinite(maxFilesConfig) && maxFilesConfig > 0 ? Math.floor(maxFilesConfig) : 0;
    const maxFileSizeConfig = Number(c.uploadMaxFileSizeMb);
    const maxBytes = Number.isFinite(maxFileSizeConfig) && maxFileSizeConfig > 0 ? maxFileSizeConfig * 1024 * 1024 : 0;
    const allowed = new Set((c.uploadAllowedExtensions || []).map(x => String(x).toLowerCase().replace(/^\./, "")));
    const selected = maxFiles > 0 ? files.slice(0, maxFiles) : files;

    if (maxFiles > 0 && files.length > maxFiles) {
      alert(fmt("alert.uploadTooMany", "Only {max} file(s) can be selected at once.", {max: maxFiles}));
    }

    const valid = [];
    for (const file of selected) {
      const ext = (file.name.split(".").pop() || "").toLowerCase();
      if (allowed.size && !allowed.has(ext)) {
        alert(fmt("alert.uploadExtensionDenied", "This file type is not allowed: {name}", {name: file.name}));
        continue;
      }
      if (maxBytes > 0 && file.size > maxBytes) {
        alert(fmt("alert.uploadTooLarge", "File is too large: {name}", {name: file.name}));
        continue;
      }
      valid.push(file);
    }

    if (!valid.length) return;

    const totalBytes = valid.reduce((sum, file) => sum + Math.max(1, Number(file.size) || 1), 0);
    let completedBytes = 0;
    const uploaded = [];

    state.uploadCancelRequested = false;
    state.uploadActive = true;
    const uploadIntoModal = state.activeComposeInputId === "kwc-dm-input" || state.activeComposeInputId === "kwc-group-input";
    if (!uploadIntoModal) markExplicitLatestFollow("upload", 8000);
    setUploadControlsBusy(true);
    updateUploadProgress(t("upload.preparing", "Preparing upload..."), 0, true);

    try {
      for (let i = 0; i < valid.length; i++) {
        if (state.uploadCancelRequested) break;
        const file = valid[i];
        const form = new FormData();
        form.append("file", file, file.name);

        if (state.token) {
          // Authentication is sent with the Authorization header.
        } else {
          form.append("guestName", currentGuestNameForSubmit());
        }

        const baseLabel = fmt("upload.progress", "Uploading {current}/{total}: {name}", {
          current: i + 1,
          total: valid.length,
          name: file.name
        });

        try {
          const res = await uploadFormWithProgress(form, (loaded, size) => {
            const fileSize = Math.max(1, Number(size) || Number(file.size) || 1);
            const overall = totalBytes > 0
              ? ((completedBytes + Math.min(fileSize, loaded)) / totalBytes) * 100
              : ((i + Math.min(1, loaded / fileSize)) / valid.length) * 100;
            updateUploadProgress(baseLabel, overall, true);
          });
          completedBytes += Math.max(1, Number(file.size) || 1);

          if (!res.ok) {
            alertResponse("alert.uploadFailed", "Upload failed: {error}", res);
            continue;
          }
          uploaded.push(normalizeReturnedUploadUrl(res.url));
          updateUploadProgress(baseLabel, totalBytes > 0 ? (completedBytes / totalBytes) * 100 : ((i + 1) / valid.length) * 100, true);
        } catch (err) {
          if (err && err.aborted) {
            updateUploadProgress(t("upload.canceled", "Upload canceled."), 0, true);
            break;
          }
          alert(err && err.error && err.error !== "network"
            ? fmt("alert.uploadFailed", "Upload failed: {error}", {error: responseError(err)})
            : t("alert.serverUnavailable", "Cannot connect to chat server."));
        }
      }
    } finally {
      state.uploadXhr = null;
      state.uploadActive = false;
      setUploadControlsBusy(false);
    }

    if (uploaded.length) {
      const dmTargetActive = state.activeComposeInputId === "kwc-dm-input" && !!document.getElementById("kwc-dm-input");
      const groupTargetActive = state.activeComposeInputId === "kwc-group-input" && !!document.getElementById("kwc-group-input");
      const modalTargetActive = dmTargetActive || groupTargetActive;
      if (!modalTargetActive) forceLatestChatView("upload");
      const text = normalizeInsertedMediaLinks(uploaded.join(" "));
      const mode = String((state.config && state.config.uploadClipboardSendMode) || "insert").toLowerCase();
      if (!modalTargetActive && source === "clipboard" && mode === "send") {
        const ok = await sendMessageText(text, null, {forceLatest: true});
        if (!ok) appendToMessage(text, {mediaLinks: true});
      } else {
        appendToMessage(text, {mediaLinks: true});
      }
      if (!modalTargetActive) forceLatestChatView("upload-complete");
      hideUploadProgressSoon(t("upload.complete", "Upload complete."));
    } else if (state.uploadCancelRequested) {
      hideUploadProgressSoon(t("upload.canceled", "Upload canceled."));
    } else {
      hideUploadProgressSoon("");
    }
  }



  function updatePipButton() {
    const btn = document.getElementById("kwc-pip");
    const c = state.config || {};
    const enabled = c.uiPictureInPictureEnabled === true && !state.isPip;
    if (!enabled) {
      if (btn) btn.remove();
      return;
    }
    if (!btn) return;
    const visible = !state.minimized;
    btn.classList.toggle("kwc-hidden", !visible);
    btn.hidden = !visible;
    btn.setAttribute("aria-hidden", visible ? "false" : "true");
    btn.style.display = visible ? "" : "none";
    btn.disabled = !visible;
    btn.title = t("button.pip", "PIP");
  }

  function canRunWebCommands() {
    return !!(state.commandsEnabled && state.commandsCanRun && state.token && (state.commandsAllowAll || (Array.isArray(state.commands) && state.commands.length)));
  }

  function updateCommandButton() {
    const btn = document.getElementById("kwc-command");
    if (!btn) return;
    const visible = canRunWebCommands() && state.commandsShowButton !== false && !state.minimized;
    btn.classList.toggle("kwc-hidden", !visible);
    btn.title = t("button.commands", "Commands");
    if (!visible) hideCommandPanel();
  }

  async function loadCommands() {
    if (!state.config || !state.config.commandsEnabled || !state.token) {
      state.commands = [];
      state.commandsCanRun = false;
      state.commandsEnabled = !!(state.config && state.config.commandsEnabled);
      state.commandsAllowAll = !!(state.config && state.config.commandsAllowAll);
      state.commandsShowButton = state.config ? state.config.commandsShowButton !== false : true;
      state.commandsShowSlashPanel = state.config ? state.config.commandsShowSlashPanel !== false : true;
      state.commandsRunFromChatInput = state.config ? state.config.commandsRunFromChatInput === true : false;
      state.commandsRequireConfirm = state.config ? state.config.commandsRequireConfirm !== false : true;
      state.commandMaxLength = state.config ? normalizeCommandMaxLength(state.config.commandsMaxLength, 0) : 0;
      updateCommandButton();
      hideCommandPanel();
      return;
    }

    try {
      const res = await api("/commands");
      state.commandsEnabled = !!res.enabled;
      state.commandsCanRun = !!res.canRun;
      state.commandsAllowAll = !!res.allowAll;
      state.commandsShowButton = res.showButton !== false;
      state.commandsShowSlashPanel = res.showSlashPanel !== false;
      state.commandsRunFromChatInput = res.runFromChatInput === true;
      state.commandsRequireConfirm = res.requireConfirm !== false;
      state.commandMaxLength = normalizeCommandMaxLength(res.maxLength, state.commandMaxLength || 0);
      state.commands = Array.isArray(res.presets) ? res.presets : [];
    } catch (e) {
      state.commands = [];
      state.commandsCanRun = false;
    }
    updateCommandButton();
    updateCommandPanel();
  }

  function commandMatches(preset, query) {
    if (!query) return true;
    const q = query.toLowerCase();
    return [preset.id, preset.label, preset.description, preset.command]
      .some(v => String(v || "").toLowerCase().includes(q));
  }

  function hasCommandMaxLength() {
    return Number(state.commandMaxLength || 0) > 0;
  }

  function commandMaxLengthAttr() {
    return hasCommandMaxLength() ? ` maxlength="${esc(state.commandMaxLength)}"` : "";
  }

  function commandMaxLengthHintHtml() {
    return hasCommandMaxLength()
      ? `<small class="kwc-command-limit">${esc(fmt("commands.maxLengthHint", "Maximum: {max} characters", {max: state.commandMaxLength}))}</small>`
      : "";
  }

  function hideCommandPanel() {
    const panel = document.getElementById("kwc-command-panel");
    if (panel && !panel.classList.contains("kwc-hidden")) panel.classList.add("kwc-hidden");
  }

  let commandPanelRenderFrame = 0;
  let messageInputComposing = false;

  function scheduleCommandPanelUpdate() {
    if (messageInputComposing || commandPanelRenderFrame) return;
    commandPanelRenderFrame = requestAnimationFrame(() => {
      commandPanelRenderFrame = 0;
      if (!messageInputComposing) updateCommandPanel();
    });
  }

  function updateCommandPanel() {
    const panel = document.getElementById("kwc-command-panel");
    const input = document.getElementById("kwc-message");
    if (!panel || !input) return;
    const value = String(input.value || "").trim();
    if (!canRunWebCommands() || state.commandsRunFromChatInput !== true || state.commandsShowSlashPanel === false || !value.startsWith("/")) {
      // Normal chat typing is the hot path. Do not rewrite hidden command-panel
      // DOM for every keystroke; that forces unnecessary style/compositor work
      // and can make the input caret/text visibly trail behind on map views.
      hideCommandPanel();
      return;
    }

    const query = value.slice(1).trim();
    if (state.commandsAllowAll) {
      if (!query) {
        panel.classList.add("kwc-hidden");
        panel.innerHTML = "";
        return;
      }
      const tooLong = hasCommandMaxLength() && query.length > state.commandMaxLength;
      panel.innerHTML = tooLong ?
        `<div class="kwc-command-empty">${esc(fmt("commands.tooLong", "Command is too long. Maximum: {max} characters.", {max: state.commandMaxLength}))}</div>` :
        `<button type="button" class="kwc-command-inline-item kwc-command-direct" data-run-direct-command="${esc(query)}">
          <span class="kwc-command-label">${esc(t("commands.runDirect", "Run command"))}</span>
          <span class="kwc-command-preview">/${esc(query)}</span>
        </button>`;
      panel.querySelectorAll("[data-run-direct-command]").forEach(btn => {
        btn.onclick = async e => {
          e.preventDefault();
          e.stopPropagation();
          await runDirectCommand(btn.getAttribute("data-run-direct-command"));
          hideCommandPanel();
          input.value = "";
        };
      });
      panel.classList.remove("kwc-hidden");
      return;
    }

    const items = (Array.isArray(state.commands) ? state.commands : []).filter(p => commandMatches(p, query)).slice(0, 8);
    if (!items.length) {
      panel.innerHTML = `<div class="kwc-command-empty">${esc(t("commands.noMatches", "No matching commands."))}</div>`;
      panel.classList.remove("kwc-hidden");
      return;
    }

    panel.innerHTML = items.map(p => `
      <button type="button" class="kwc-command-inline-item" data-run-command="${esc(p.id)}">
        <span class="kwc-command-label">${esc(p.label || p.id)}</span>
        <span class="kwc-command-preview">/${esc(p.command || "")}</span>
      </button>
    `).join("");
    panel.querySelectorAll("[data-run-command]").forEach(btn => {
      btn.onclick = async e => {
        e.preventDefault();
        e.stopPropagation();
        await runCommandPreset(btn.getAttribute("data-run-command"));
        hideCommandPanel();
        input.value = "";
      };
    });
    panel.querySelectorAll("[data-run-direct-command]").forEach(btn => {
      btn.onclick = async e => {
        e.preventDefault();
        e.stopPropagation();
        await runDirectCommand(btn.getAttribute("data-run-direct-command"));
        hideCommandPanel();
        input.value = "";
      };
    });
    panel.classList.remove("kwc-hidden");
  }

  function openCommandModal() {
    if (!canRunWebCommands()) {
      alert(t("commands.notAvailable", "Command panel is not available."));
      return;
    }

    const old = document.getElementById("kwc-command-modal");
    if (old) old.remove();
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    wrap.id = "kwc-command-modal";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-command-modal">
        <div class="kwc-modal-head">
          <h3>${t("commands.title", "Server commands")}</h3>
          <button class="kwc-button" id="kwc-command-close">${t("button.close", "Close")}</button>
        </div>
        <p>${state.commandsAllowAll ? t("commands.descriptionAll", "Run any server console command from the web UI.") : t("commands.description", "Run a pre-approved server command from the web UI.")}</p>
        ${state.commandsAllowAll ? `<div class="kwc-command-direct-box"><input class="kwc-input" id="kwc-command-direct"${commandMaxLengthAttr()} placeholder="${t("commands.directPlaceholder", "Command without /")}"><button class="kwc-button" id="kwc-command-direct-run">${t("button.run", "Run")}</button></div>${commandMaxLengthHintHtml()}` : `<input class="kwc-input" id="kwc-command-search" placeholder="${t("commands.search", "Search preset commands")}"><div class="kwc-command-list" id="kwc-command-list"></div>`}
      </div>
    `;
    document.body.appendChild(wrap);
    const search = wrap.querySelector("#kwc-command-search");
    const directInput = wrap.querySelector("#kwc-command-direct");
    const directRun = wrap.querySelector("#kwc-command-direct-run");
    if (directRun && directInput) {
      const submitDirect = async () => {
        const ok = await runDirectCommand(String(directInput.value || ""));
        if (ok) wrap.remove();
      };
      directRun.onclick = submitDirect;
      directInput.addEventListener("keydown", e => { if (e.key === "Enter" && !e.isComposing) submitDirect(); });
    }
    const render = () => renderCommandList(wrap, String(search ? search.value : ""));
    wrap.querySelector("#kwc-command-close").onclick = () => wrap.remove();
    wrap.addEventListener("click", e => { if (e.target === wrap) wrap.remove(); });
    if (search) {
      search.addEventListener("input", render);
      render();
      setTimeout(() => search.focus(), 0);
    } else if (directInput) {
      setTimeout(() => directInput.focus(), 0);
    }
  }

  function renderCommandList(wrap, query) {
    const list = wrap.querySelector("#kwc-command-list");
    if (!list) return;
    const q = String(query || "").trim();
    const items = (Array.isArray(state.commands) ? state.commands : []).filter(p => commandMatches(p, q));
    if (!items.length) {
      list.innerHTML = `<div class="kwc-command-empty">${esc(t("commands.noMatches", "No matching commands."))}</div>`;
      return;
    }
    list.innerHTML = items.map(p => `
      <div class="kwc-command-item">
        <div class="kwc-command-main">
          <strong>${esc(p.label || p.id)}</strong>
          ${p.description ? `<small>${esc(p.description)}</small>` : ""}
          <code>/${esc(p.command || "")}</code>
        </div>
        <button class="kwc-button" data-run-command="${esc(p.id)}">${t("button.run", "Run")}</button>
      </div>
    `).join("");
    list.querySelectorAll("[data-run-command]").forEach(btn => {
      btn.onclick = async () => {
        const ok = await runCommandPreset(btn.getAttribute("data-run-command"));
        if (ok) wrap.remove();
      };
    });
  }


  async function runDirectCommand(command) {
    command = String(command || "").trim();
    if (command.startsWith("/")) command = command.slice(1).trim();
    if (!state.commandsAllowAll || !command) return false;
    if (hasCommandMaxLength() && command.length > state.commandMaxLength) {
      alert(fmt("commands.tooLong", "Command is too long. Maximum: {max} characters.", {max: state.commandMaxLength}));
      return false;
    }
    const needConfirm = state.commandsRequireConfirm !== false;
    if (needConfirm && !confirmPlain(fmt("commands.confirm", "Run command: /{command}?", {command}))) return false;
    try {
      const res = await api("/commands/run", {method: "POST", body: JSON.stringify({command})});
      if (!res.ok) {
        alertResponse("commands.failed", "Command failed: {error}", res);
        return false;
      }
      alert(fmt("commands.submitted", "Command submitted: {label}", {label: command}));
      return true;
    } catch (e) {
      alert(t("alert.serverUnavailable", "Cannot connect to chat server."));
      return false;
    }
  }

  async function runCommandPreset(id) {
    const preset = state.commands.find(p => p && p.id === id);
    if (!preset) return false;
    const needConfirm = state.commandsRequireConfirm !== false || preset.confirm !== false;
    if (needConfirm && !confirmPlain(fmt("commands.confirm", "Run command: /{command}?", {command: preset.command || preset.label || id}))) return false;
    try {
      const res = await api("/commands/run", {method: "POST", body: JSON.stringify({id})});
      if (!res.ok) {
        alertResponse("commands.failed", "Command failed: {error}", res);
        return false;
      }
      alert(fmt("commands.submitted", "Command submitted: {label}", {label: preset.label || id}));
      return true;
    } catch (e) {
      alert(t("alert.serverUnavailable", "Cannot connect to chat server."));
      return false;
    }
  }

  function setSendControlsBusy(busy) {
    const sendBtn = document.getElementById("kwc-send");
    const input = document.getElementById("kwc-message");
    if (sendBtn) {
      sendBtn.disabled = !!busy;
      sendBtn.classList.toggle("kwc-busy", !!busy);
    }
    if (input) {
      input.dataset.kwcSending = busy ? "1" : "0";
    }
  }

  async function sendMessageText(text, inputToClear, options = {}) {
    text = String(text || "").trim();
    if (!text) return false;
    const emojiLimit = Number(state.emojiMessageTokenLimit || 0);
    if (emojiLimit > 0 && emojiTokenCount(text) > emojiLimit) {
      alert(fmt("alert.emojiTooMany", "Only {max} emoji(s) can be used in one message.", {max: emojiLimit}));
      return false;
    }
    if (state.sendInFlight && options.allowConcurrent !== true) {
      return false;
    }
    if (options.allowConcurrent !== true) {
      state.sendInFlight = true;
      state.sendInFlightSince = Date.now();
      state.sendInFlightText = text;
      setSendControlsBusy(true);
    }

    const payload = {message: text};
    if (state.replyTarget && state.replyTarget.id) {
      payload.replyToId = state.replyTarget.id;
      payload.replyToSender = state.replyTarget.sender || "";
      payload.replyToPreview = state.replyTarget.preview || "";
    }
    if (state.token) {
      payload.token = state.token;
    } else {
      payload.guestName = currentGuestNameForSubmit();
      if (state.captchaPass) {
        payload.captchaPass = state.captchaPass;
      }
      if (state.captcha) {
        payload.captchaId = state.captcha.id;
        const captchaInput = document.getElementById("kwc-captcha-a");
        payload.captchaAnswer = captchaInput ? captchaInput.value.trim() : "";
      }
    }

    // Clear the compose UI before starting network work. The server can publish
    // the message over SSE before the POST response returns (for example while
    // Web Push/notification work is still finishing). Waiting for the response
    // made the sent text visibly remain in the input for hundreds of ms.
    if (inputToClear) inputToClear.value = "";
    clearReplyTarget();

    try {
      markExplicitLatestFollow(options.forceLatest ? "send-forced" : "send", 4500);
      const res = await api("/send", {method: "POST", body: JSON.stringify(payload), returnHttpErrorResponse: true});
      if (!res.ok) {
        if (res.captchaPass) {
          state.captchaPass = res.captchaPass;
          localStorage.setItem("kwc.captchaPass", state.captchaPass);
        }

        if (state.captchaPass && state.config && state.config.captchaEnabled && !state.config.captchaRequireOnEachMessage) {
          hideCaptchaUi();
        }

        if (res.error === "captcha_failed") {
          state.captchaPass = "";
          localStorage.removeItem("kwc.captchaPass");
          await refreshCaptcha(true);
        } else {
          await refreshCaptcha();
        }

        if (res.error === "rate_limited") {
          alert(t("alert.rateLimited", "You are sending messages too quickly. Please wait."));
        } else if (res.error === "emoji_limit") {
          alert(fmt("alert.emojiTooMany", "Only {max} emoji(s) can be used in one message.", {max: state.emojiMessageTokenLimit || ""}));
        } else {
          alertResponse("alert.sendFailed", "Send failed: {error}", res);
        }
        return false;
      }
      if (res.captchaPass) {
        state.captchaPass = res.captchaPass;
        localStorage.setItem("kwc.captchaPass", state.captchaPass);
      }
      forceLatestChatView(options.forceLatest ? "send-forced" : "send");
      setTimeout(() => {
        if (state.autoFollowLatest) loadHistory(false, {skipIfUnchanged: true});
      }, 180);
      await refreshCaptcha();
      return true;
    } catch (e) {
      alert(t("alert.serverUnavailable", "Cannot connect to chat server."));
      return false;
    } finally {
      if (options.allowConcurrent !== true) {
        state.sendInFlight = false;
        state.sendInFlightSince = 0;
        state.sendInFlightText = "";
        setSendControlsBusy(false);
      }
    }
  }

  async function sendMessage() {
    const input = document.getElementById("kwc-message");
    const text = input ? input.value.trim() : "";
    if (!text) return;
    if (state.sendInFlight) return;
    if (state.commandsAllowAll && state.commandsRunFromChatInput === true && canRunWebCommands() && text.startsWith("/")) {
      state.sendInFlight = true;
      state.sendInFlightSince = Date.now();
      state.sendInFlightText = text;
      setSendControlsBusy(true);
      try {
        // Match normal chat sends: once execution is attempted the compose box
        // is cleared immediately and is not restored on failure.
        if (input) input.value = "";
        await runDirectCommand(text);
      } finally {
        state.sendInFlight = false;
        state.sendInFlightSince = 0;
        state.sendInFlightText = "";
        setSendControlsBusy(false);
        hideCommandPanel();
      }
      return;
    }
    await sendMessageText(text, input);
  }


  function isMessagePinned(messageId) {
    if (!messageId) return false;
    return state.pins.some(pin => pin && pin.messageId === messageId);
  }

  function languagePrefix() {
    const selected = String(state.selectedLanguage || "").trim();
    if (selected) return selected.toLowerCase();
    const browser = String(navigator.language || "").trim();
    return browser.toLowerCase();
  }

  function moderationActionsFallback(enabled) {
    const lang = languagePrefix();
    if (lang.startsWith("ko")) return enabled ? "고정 / 삭제 비활성화" : "고정 / 삭제 활성화";
    if (lang.startsWith("ja")) return enabled ? "固定 / 削除を無効化" : "固定 / 削除を有効化";
    if (lang.startsWith("zh")) return enabled ? "停用固定/删除" : "启用固定/删除";
    return enabled ? "Disable pin/delete" : "Enable pin/delete";
  }

  function moderationActionsToggleLabel() {
    return state.moderationActionsVisible
      ? t("button.moderationActionsDisable", moderationActionsFallback(true))
      : t("button.moderationActionsEnable", moderationActionsFallback(false));
  }

  function updateModerationActionsToggleButton(button) {
    if (!button) return;
    button.textContent = moderationActionsToggleLabel();
    button.setAttribute("aria-pressed", state.moderationActionsVisible ? "true" : "false");
  }

  function refreshOpenPinnedModal() {
    const list = document.getElementById("kwc-pinned-list");
    if (!list) return;
    list.innerHTML = "";
    if (!state.pins.length) {
      list.innerHTML = `<p>${esc(t("pinned.empty", "No pinned messages."))}</p>`;
      return;
    }
    state.pins.forEach((pin, index) => list.appendChild(renderPinnedItem(pin, index, state.pins.length)));
  }

  function setModerationActionsVisible(visible) {
    state.moderationActionsVisible = !!visible;
    document.querySelectorAll("#kwc-toggle-moderation-actions").forEach(updateModerationActionsToggleButton);
    refreshOpenPinnedModal();
    // Existing virtual-scroll message nodes are normally reused instead of being
    // rebuilt. Update the admin mini-actions in-place so pin/delete buttons do
    // not wait for a later scroll-driven re-render.
    syncRenderedMessageActions();
    if (state.messages && state.messages.length) scheduleVirtualRender({preserveScroll: true});
  }


  function pinnedTitle(pin) {
    // The collapsed pinned bar uses plain text (`textContent`/title), not the
    // rich message renderer. Strip Minecraft legacy color codes here so pinned
    // summaries do not leak raw values such as &7/§7/&#RRGGBB.
    const text = plainLegacyText(displayMessageText(pin)).replace(/\s+/g, " ").trim();
    if (!text) return t("pinned.untitled", "Pinned message");
    return text;
  }

  function pinnedTooltip(pin) {
    const title = pinnedTitle(pin);
    const real = realSender(pin);
    return real ? title + " — " + senderOriginalTitle(real) : title;
  }


  function canViewPinnedMessages() {
    return !!state.token || !state.config || state.config.pinnedShowToLoggedOut !== false;
  }

  function renderPinnedBar() {
    const root = document.getElementById("kwc-root");
    const bar = document.getElementById("kwc-pinned-bar");
    const opener = document.getElementById("kwc-pinned-open");
    const label = document.getElementById("kwc-pinned-label");
    const search = document.getElementById("kwc-search-open");
    const resizeLock = document.getElementById("kwc-resize-lock");
    if (!bar || !label) return;
    const count = Array.isArray(state.pins) ? state.pins.length : 0;
    const pinsVisible = canViewPinnedMessages() && state.pinsEnabled !== false && count > 0 && !state.minimized;
    const searchVisible = searchEnabled() && !state.minimized && !guestChatHidden();
    const resizeLockVisible = !!(state.config && state.config.uiResizable) && !state.minimized && !guestChatHidden();
    bar.classList.toggle("kwc-hidden", !pinsVisible);
    if (root) root.classList.toggle("kwc-has-pinned-bar", !!pinsVisible);
    if (opener) opener.classList.toggle("kwc-hidden", !pinsVisible);
    if (search) search.classList.toggle("kwc-hidden", !searchVisible);
    if (resizeLock) resizeLock.classList.toggle("kwc-hidden", !resizeLockVisible);
    updateResizeLockButton();
    if (!pinsVisible) {
      if (label) {
        label.textContent = "";
        label.title = "";
      }
      if (opener) opener.title = "";
      bar.title = "";
      return;
    }
    const rootWidth = root?.getBoundingClientRect().width || window.innerWidth || 999;
    // Default chat width is around 372px, so do not collapse to a plain count there.
    // Only use compact count when the bar is truly too narrow to show a useful title.
    const compact = rootWidth < 260;
    if (compact) {
      label.textContent = fmt("pinned.compact", "{count}", {count});
      label.title = count === 1 ? pinnedTooltip(state.pins[0]) : fmt("pinned.multiple", "{title} and {rest} more", {title: pinnedTooltip(state.pins[0]), rest: count - 1});
    } else if (count === 1) {
      label.textContent = fmt("pinned.single", "{title}", {title: pinnedTitle(state.pins[0])});
      label.title = pinnedTooltip(state.pins[0]);
    } else {
      // Keep the remaining-count suffix visible even when the first pinned title
      // is wider than the bar. Only the title span is allowed to ellipsize.
      label.textContent = "";
      const titlePart = document.createElement("span");
      titlePart.className = "kwc-pinned-summary-title";
      titlePart.textContent = pinnedTitle(state.pins[0]);
      const restPart = document.createElement("span");
      restPart.className = "kwc-pinned-summary-rest";
      restPart.textContent = fmt("pinned.more", "and {rest} more", {rest: count - 1});
      label.append(titlePart, restPart);
      label.title = fmt("pinned.multiple", "{title} and {rest} more", {title: pinnedTooltip(state.pins[0]), rest: count - 1});
    }
    if (opener) opener.title = label.title || label.textContent || "";
    if (bar) bar.title = label.title || label.textContent || "";
  }

  function renderPinnedItem(pin, index = 0, total = 0) {
    const msg = Object.assign({}, pin, {id: pin.messageId || pin.pinId});
    const el = renderMessageElement(msg);
    el.classList.add("kwc-pinned-item");
    el.querySelectorAll(".kwc-mini-actions, [data-delete], [data-pin], [data-reply]").forEach(node => node.remove());
    el.classList.remove("kwc-has-mini-actions");
    const meta = el.querySelector(".kwc-meta");
    if (meta) {
      const detail = document.createElement("span");
      detail.className = "kwc-pinned-detail";
      detail.textContent = fmt("pinned.pinnedBy", "pinned by {user}", {user: pin.pinnedBy || "-"});
      const detailSep = document.createElement("span");
      detailSep.className = "kwc-meta-sep";
      detailSep.setAttribute("aria-hidden", "true");
      detailSep.textContent = "·";
      meta.appendChild(detailSep);
      meta.appendChild(detail);
      if (state.moderationActionsVisible && state.pinsCanPin && pin.pinId) {
        const controls = document.createElement("span");
        controls.className = "kwc-mini-actions kwc-pinned-actions";

        const up = document.createElement("button");
        up.className = "kwc-mini-action kwc-pinned-action kwc-pinned-move-action";
        up.type = "button";
        up.setAttribute("data-pin-move", pin.pinId);
        up.setAttribute("data-direction", "up");
        up.title = t("button.moveUp", "Move up");
        up.textContent = "↑";
        if (index <= 0) up.disabled = true;
        controls.appendChild(up);

        const down = document.createElement("button");
        down.className = "kwc-mini-action kwc-pinned-action kwc-pinned-move-action";
        down.type = "button";
        down.setAttribute("data-pin-move", pin.pinId);
        down.setAttribute("data-direction", "down");
        down.title = t("button.moveDown", "Move down");
        down.textContent = "↓";
        if (total > 0 && index >= total - 1) down.disabled = true;
        controls.appendChild(down);

        const unpin = document.createElement("button");
        unpin.className = "kwc-mini-action kwc-pinned-action kwc-pinned-unpin-action";
        unpin.type = "button";
        unpin.setAttribute("data-unpin", pin.pinId);
        unpin.title = t("button.unpin", "unpin");
        unpin.setAttribute("aria-label", t("button.unpin", "unpin"));
        unpin.textContent = t("button.unpin", "unpin");
        controls.appendChild(unpin);

        meta.appendChild(controls);
      }
    }
    return el;
  }

  async function loadPins() {
    try {
      const res = await api("/pins", {method: "GET"});
      if (!res || !res.ok) return;
      state.pinsEnabled = res.enabled !== false;
      state.pinsCanPin = !!res.canPin;
      state.pins = canViewPinnedMessages() && Array.isArray(res.pins) ? res.pins : [];
      renderPinnedBar();
      syncRenderedMessageActions();
      if (state.messages && state.messages.length) scheduleVirtualRender({preserveScroll: true});
    } catch (_) {}
  }

  async function pinMessage(id) {
    if (!id || !state.token || !(state.role === "ADMIN" || state.role === "MODERATOR")) return;
    const res = await adminWrite("/admin/pin-message", {id});
    if (!res.ok) {
      alertResponse("alert.pinFailed", "Pin failed: {error}", res);
      return;
    }
    await loadPins();
  }

  async function movePinnedMessage(pinId, direction) {
    if (!pinId || !direction || !state.token || !(state.role === "ADMIN" || state.role === "MODERATOR")) return;
    const res = await adminWrite("/admin/move-pin", {pinId, direction});
    if (!res.ok) {
      alertResponse("alert.movePinFailed", "Move failed: {error}", res);
      return;
    }
    await loadPins();
    refreshOpenPinnedModal();
  }

  async function unpinMessage(pinId) {
    if (!pinId || !state.token || !(state.role === "ADMIN" || state.role === "MODERATOR")) return;
    const res = await adminWrite("/admin/unpin-message", {pinId});
    if (!res.ok) {
      alertResponse("alert.unpinFailed", "Unpin failed: {error}", res);
      return;
    }
    await loadPins();
    refreshOpenPinnedModal();
  }

  function openPinnedModal() {
    if (!state.pinsEnabled || !state.pins.length) return;
    const old = document.getElementById("kwc-pinned-modal");
    if (old) old.remove();
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-pinned-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.id = "kwc-pinned-modal";
    wrap.innerHTML = `
      <div class="kwc-modal kwc-pinned-modal">
        <div class="kwc-modal-head">
          <h3>${t("pinned.title", "Pinned messages")}</h3>
          <button class="kwc-button" id="kwc-pinned-close">${t("button.close", "Close")}</button>
        </div>
        <div class="kwc-pinned-list" id="kwc-pinned-list"></div>
      </div>
    `;
    document.body.appendChild(wrap);
    const list = wrap.querySelector("#kwc-pinned-list");
    if (list) {
      if (!state.pins.length) list.innerHTML = `<p>${esc(t("pinned.empty", "No pinned messages."))}</p>`;
      state.pins.forEach((pin, index) => list.appendChild(renderPinnedItem(pin, index, state.pins.length)));
    }
    wrap.querySelector("#kwc-pinned-close").onclick = () => wrap.remove();
    wrap.addEventListener("click", e => {
      const move = e.target && e.target.closest ? e.target.closest("[data-pin-move]") : null;
      if (move && wrap.contains(move)) {
        e.preventDefault();
        e.stopPropagation();
        movePinnedMessage(move.getAttribute("data-pin-move"), move.getAttribute("data-direction") || "");
        return;
      }
      const unpin = e.target && e.target.closest ? e.target.closest("[data-unpin]") : null;
      if (unpin && wrap.contains(unpin)) {
        e.preventDefault();
        e.stopPropagation();
        unpinMessage(unpin.getAttribute("data-unpin"));
        return;
      }
      if (e.target === wrap) wrap.remove();
    });
  }

  let adminRequestSerial = 0;
  function nextAdminRequestId() {
    adminRequestSerial = (adminRequestSerial + 1) % 0x7fffffff;
    return `${Date.now().toString(36)}-${adminRequestSerial.toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
  }

  function adminApi(path, opts = {}) {
    const requestOpts = Object.assign({}, opts);
    const requestId = String(requestOpts.kwcRequestId || nextAdminRequestId());
    delete requestOpts.kwcRequestId;
    // Every Admin request gets a unique URL. This is stronger than relying on
    // browser/proxy cache directives and avoids stale verification reads when a
    // map host or reverse proxy caches /admin/* unexpectedly.
    const joiner = path.includes("?") ? "&" : "?";
    const url = path + joiner + "_kwc=" + encodeURIComponent(requestId);
    if (!("cache" in requestOpts)) requestOpts.cache = "no-store";
    return api(url, requestOpts).then(res => {
      if (res && typeof res === "object") res._clientRequestId = requestId;
      return res;
    });
  }

  function adminWrite(path, fields = {}, opts = {}) {
    const requestId = nextAdminRequestId();
    const form = new URLSearchParams();
    for (const [key, value] of Object.entries(fields || {})) form.set(key, value == null ? "" : String(value));
    form.set("_requestId", requestId);
    // Use the browser's native URL-encoded form body. It is CORS-safelisted,
    // preserves Unicode/newlines through percent encoding, and avoids maintaining
    // a second hand-written JSON transport just for Admin writes.
    return adminApi(path, Object.assign({
      method: "POST",
      cache: "no-store",
      returnHttpErrorResponse: true,
      body: form,
      kwcRequestId: requestId
    }, opts)).then(res => {
      if (res && typeof res === "object") res._clientRequestId = requestId;
      return res;
    });
  }

  async function deleteMessage(id) {
    if (!id || !confirmPlain(t("alert.confirmDelete", "Hide this message?"))) return;
    const res = await adminWrite("/admin/delete-message", {id});
    if (!res.ok) {
      alertResponse("alert.deleteFailed", "Delete failed: {error}", res);
      return;
    }
    // Do not rely solely on the SSE delete event. Older history pages or a
    // temporarily stale EventSource can leave the clicked message visible until
    // the next refresh even though the server already hid it.
    markMessageDeleted(id);
  }

  async function openAdminModal() {
    if (!state.token || !(state.role === "ADMIN" || state.role === "MODERATOR")) return;

    const canManageMutes = (!state.config || state.config.moderationEnabled !== false) && (state.role === "ADMIN" || (!state.config || state.config.allowModeratorGuestMute !== false));
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-admin-modal">
        <h3>${t("admin.title", "KOKOTO WebChat Admin")}</h3>
        <div class="kwc-tabs">
          <button class="kwc-button kwc-tab" data-panel="summary">${t("admin.summary", "Summary")}</button>
          ${canManageMutes ? `<button class="kwc-button kwc-tab" data-panel="mutes">${t("admin.mutes", "Mutes")}</button>` : ""}
          ${state.role === "ADMIN" ? `<button class="kwc-button kwc-tab" data-panel="accounts">${t("admin.accounts", "Accounts")}</button>` : ""}
          ${state.role === "ADMIN" ? `<button class="kwc-button kwc-tab" data-panel="emojis">${t("admin.emojis", "Emojis")}</button>` : ""}
          ${state.role === "ADMIN" ? `<button class="kwc-button kwc-tab" data-panel="filter">${t("admin.filter", "Filter")}</button>` : ""}
          ${state.role === "ADMIN" ? `<button class="kwc-button kwc-tab" data-panel="settings">${t("admin.settings", "Settings")}</button>` : ""}
        </div>
        <div id="kwc-admin-content">${t("admin.loading", "Loading...")}</div>
        <br>
        <button class="kwc-button" id="kwc-admin-close">${t("button.close", "Close")}</button>
      </div>
    `;
    protectHistoryEndNotice("admin-open", 6000);
    document.body.appendChild(wrap);
    wrap.querySelector("#kwc-admin-close").onclick = () => { protectHistoryEndNotice("admin-close", 6000); wrap.remove(); scheduleScrollAffordanceRefresh("admin-close"); };
    wrap.querySelectorAll("[data-panel]").forEach(btn => {
      btn.onclick = () => loadAdminPanel(wrap, btn.dataset.panel);
    });
    await loadAdminPanel(wrap, "summary");
  }

  async function loadAdminPanel(wrap, panel) {
    const content = wrap.querySelector("#kwc-admin-content");
    content.textContent = t("admin.loading", "Loading...");
    try {
      if (panel === "summary") return renderAdminSummary(content);
      if (panel === "mutes") return renderAdminMutes(content);
      if (panel === "accounts") return renderAdminAccounts(content);
      // Compatibility for any stale in-page state that still references the old Sessions tab.
      if (panel === "sessions") return renderAdminAccounts(content);
      if (panel === "emojis") return renderAdminEmojis(content);
      if (panel === "filter") return renderAdminFilter(content);
      if (panel === "settings") return renderAdminSettings(content);
    } catch (e) {
      content.textContent = fmt("admin.failed", "Failed: {error}", {error: e.message});
    }
  }

  async function adminUpdateSetting(path, value) {
    return adminWrite("/admin/settings", {path, value: String(value)});
  }

  function adminSettingLabel(path) {
    const key = "admin.setting." + String(path || "").replaceAll(".", "-");
    return t(key, path);
  }

  function adminSettingInput(path, value, type = "text", options = null) {
    const id = "kwc-setting-" + path.replace(/[^a-z0-9]+/gi, "-");
    const label = adminSettingLabel(path);
    const initial = type === "boolean" ? String(value === true) : String(value ?? "");
    if (type === "boolean") {
      return `<label class="kwc-admin-item kwc-admin-setting-item"><span><strong>${esc(label)}</strong></span><input id="${esc(id)}" data-setting-path="${esc(path)}" data-setting-initial="${esc(initial)}" type="checkbox" ${value ? "checked" : ""}></label>`;
    }
    if (options) {
      const optionHtml = options.map(option => {
        const optionValue = option && typeof option === "object" ? option.value : option;
        const optionLabel = option && typeof option === "object" ? option.label : optionValue;
        return `<option value="${esc(optionValue)}" ${String(value) === String(optionValue) ? "selected" : ""}>${esc(optionLabel)}</option>`;
      }).join("");
      return `<label class="kwc-admin-item kwc-admin-setting-item"><span><strong>${esc(label)}</strong></span><select class="kwc-input" id="${esc(id)}" data-setting-path="${esc(path)}" data-setting-initial="${esc(initial)}">${optionHtml}</select></label>`;
    }
    return `<label class="kwc-admin-item kwc-admin-setting-item"><span><strong>${esc(label)}</strong></span><input class="kwc-input" id="${esc(id)}" data-setting-path="${esc(path)}" data-setting-initial="${esc(initial)}" type="${type}" value="${esc(value ?? "")}"></label>`;
  }

  function adminSettingGroup(titleKey, fallback, rows) {
    const body = Array.isArray(rows) ? rows.join("") : String(rows || "");
    return `<section class="kwc-admin-section-card">
      <div class="kwc-admin-section-title">${esc(t(titleKey, fallback))}</div>
      <div class="kwc-admin-list kwc-admin-group-list">${body}</div>
    </section>`;
  }

  function syncAdminSettingElements(content, settings, selector = "[data-setting-path]", updateInitial = false) {
    if (!settings || typeof settings !== "object") return;
    content.querySelectorAll(selector).forEach(el => {
      const path = el.dataset.settingPath;
      if (!Object.prototype.hasOwnProperty.call(settings, path)) return;
      const value = settings[path];
      if (el.type === "checkbox") el.checked = value === true;
      else el.value = value ?? "";
      if (updateInitial) el.dataset.settingInitial = el.type === "checkbox" ? String(value === true) : String(value ?? "");
    });
  }

  async function saveAdminSettingElements(content, selector = "[data-setting-path]") {
    const resultBox = content.querySelector("#kwc-settings-result") || content.querySelector("#kwc-filter-result");
    const saveButton = content.querySelector("#kwc-settings-save") || content.querySelector("#kwc-filter-settings-save");
    const changes = {};
    for (const el of content.querySelectorAll(selector)) {
      // Submit the complete visible panel on every Save. The server validates and
      // persists the batch atomically, so repeated saves do not depend on a browser
      // copy of the previous values being perfectly synchronized.
      changes[el.dataset.settingPath] = el.type === "checkbox" ? String(el.checked) : String(el.value ?? "");
    }
    if (!Object.keys(changes).length) {
      if (resultBox) resultBox.textContent = t("admin.settingsNoChanges", "No changes.");
      return true;
    }
    if (saveButton) saveButton.disabled = true;
    if (resultBox) resultBox.textContent = t("admin.settingsSaving", "Saving...");
    try {
      const res = await adminWrite("/admin/settings", changes);
      if (!res?.ok) {
        if (resultBox) resultBox.textContent = fmt("admin.failed", "Failed: {error}", {error: res?.error || "unknown"});
        return false;
      }
      if (Number(res.writeProtocol || 0) !== 5) {
        if (resultBox) resultBox.textContent = fmt("admin.failed", "Failed: {error}", {error: "admin_api_mismatch"});
        return false;
      }
      // The POST handler itself writes config.yml and reads it back before returning.
      // Use that same-request disk snapshot as the source of truth. A second GET here
      // can be served by a stale intermediary and was the source of false rollback/
      // verification failures in previous audit builds.
      const persisted = res.settings;
      if (!persisted || typeof persisted !== "object") {
        if (resultBox) resultBox.textContent = t("admin.settingsVerifyFailed", "Saved response could not be verified from config.yml.");
        return false;
      }
      // RuntimeSettingsController has already parsed each requested value, written
      // config.yml, read the same paths back and compared the typed values before
      // returning ok=true. Do not repeat that comparison as raw browser strings:
      // valid normalization such as numeric 01 -> 1 must not be reported as a save
      // failure. The returned settings object is the verified disk snapshot.
      syncAdminSettingElements(content, persisted, selector, true);
      // Refresh public runtime config after the Admin form has been verified. It is
      // intentionally not used as the source of truth for the form itself.
      loadConfig().catch(() => {});
      if (resultBox) resultBox.textContent = fmt("admin.settingsSaved", "Saved. Sessions updated: {updated}, expired: {expired}", {updated:Number(res.sessionsUpdated || 0), expired:Number(res.sessionsExpired || 0)});
      return true;
    } catch (err) {
      const message = err?.response?.error || err?.message || "unknown";
      if (resultBox) resultBox.textContent = fmt("admin.failed", "Failed: {error}", {error: message});
      return false;
    } finally {
      if (saveButton) saveButton.disabled = false;
    }
  }

  async function renderAdminSettings(content) {
    const data = await adminApi("/admin/settings");
    if (!data?.ok) throw new Error(data?.error || "settings_failed");
    if (Number(data.writeProtocol || 0) !== 5) throw new Error("admin_api_mismatch");
    const v = data.settings || {};
    const configuredAlertChannel = String(v["admin-alerts.discord.channel"] ?? "").trim();
    const discordAlertChannels = Array.from(new Set((Array.isArray(data.discordAlertChannels) ? data.discordAlertChannels : [])
      .map(value => String(value ?? "").trim()).filter(Boolean)));
    if (configuredAlertChannel && !discordAlertChannels.includes(configuredAlertChannel)) discordAlertChannels.push(configuredAlertChannel);
    const discordAlertChannelOptions = discordAlertChannels.length
      ? discordAlertChannels.map(value => ({value, label:value}))
      : [{value: configuredAlertChannel, label: configuredAlertChannel || t("admin.discordAlertNoChannels", "No DiscordSRV channels available")}];

    const guestCaptchaRows = [
      adminSettingInput("guest.enabled", v["guest.enabled"], "boolean"),
      adminSettingInput("guest.allow-custom-name", v["guest.allow-custom-name"], "boolean"),
      adminSettingInput("guest.cooldown-seconds", v["guest.cooldown-seconds"], "number"),
      adminSettingInput("guest.max-messages-per-minute", v["guest.max-messages-per-minute"], "number"),
      adminSettingInput("captcha.mode", v["captcha.mode"], "text", [{value:"off", label:t("admin.optionCaptchaOff", "Off")}, {value:"math", label:t("admin.optionCaptchaMath", "Math")}]),
      adminSettingInput("captcha.require-on-each-message", v["captcha.require-on-each-message"], "boolean"),
      adminSettingInput("captcha.pass-valid-minutes", v["captcha.pass-valid-minutes"], "number")
    ];
    const authSessionRows = [
      adminSettingInput("auth.password-login", v["auth.password-login"], "boolean"),
      adminSettingInput("auth.remember-session-days", v["auth.remember-session-days"], "number"),
      adminSettingInput("admin.admin-session-expire-hours", v["admin.admin-session-expire-hours"], "number")
    ];
    const profileRows = [
      adminSettingInput("ui.user-profiles.enabled", v["ui.user-profiles.enabled"], "boolean"),
      adminSettingInput("ui.user-profiles.max-profiles", v["ui.user-profiles.max-profiles"], "number"),
      adminSettingInput("ui.user-profiles.allow-import-export", v["ui.user-profiles.allow-import-export"], "boolean")
    ];
    const uploadRows = [
      adminSettingInput("upload.enabled", v["upload.enabled"], "boolean"),
      adminSettingInput("upload.allow-guest-upload", v["upload.allow-guest-upload"], "boolean"),
      adminSettingInput("upload.allow-user-upload", v["upload.allow-user-upload"], "boolean"),
      adminSettingInput("upload.allow-moderator-upload", v["upload.allow-moderator-upload"], "boolean"),
      adminSettingInput("upload.allow-admin-upload", v["upload.allow-admin-upload"], "boolean"),
      adminSettingInput("upload.cooldown-seconds", v["upload.cooldown-seconds"], "number"),
      adminSettingInput("upload.max-uploads-per-minute", v["upload.max-uploads-per-minute"], "number"),
      adminSettingInput("upload.max-file-size-mb", v["upload.max-file-size-mb"], "number"),
      adminSettingInput("upload.max-total-size-mb", v["upload.max-total-size-mb"], "number"),
      adminSettingInput("upload.max-files-per-message", v["upload.max-files-per-message"], "number"),
      adminSettingInput("upload.retention-days", v["upload.retention-days"], "number"),
      adminSettingInput("upload.filename-mode", v["upload.filename-mode"], "text", [{value:"random", label:t("admin.optionFilenameRandom", "Random")}, {value:"original", label:t("admin.optionFilenameOriginal", "Original")}])
    ];
    const discordAlertRows = [
      adminSettingInput("admin-alerts.discord.enabled", v["admin-alerts.discord.enabled"], "boolean"),
      adminSettingInput("admin-alerts.discord.channel", v["admin-alerts.discord.channel"], "text", discordAlertChannelOptions),
      adminSettingInput("admin-alerts.discord.sources.public-chat", v["admin-alerts.discord.sources.public-chat"], "boolean"),
      adminSettingInput("admin-alerts.discord.sources.relay-chat", v["admin-alerts.discord.sources.relay-chat"], "boolean"),
      adminSettingInput("admin-alerts.discord.sources.dm", v["admin-alerts.discord.sources.dm"], "boolean"),
      adminSettingInput("admin-alerts.discord.sources.group-chat", v["admin-alerts.discord.sources.group-chat"], "boolean"),
      adminSettingInput("admin-alerts.discord.mention", v["admin-alerts.discord.mention"], "text", [{value:"none", label:t("admin.optionMentionNone", "None")}, {value:"here", label:"@here"}, {value:"everyone", label:"@everyone"}]),
      adminSettingInput("admin-alerts.discord.case-sensitive", v["admin-alerts.discord.case-sensitive"], "boolean"),
      adminSettingInput("admin-alerts.discord.keywords", v["admin-alerts.discord.keywords"], "text")
    ];

    content.innerHTML = `
      <h4>${t("admin.settings", "Settings")}</h4>
      <p><small>${t("admin.settingsHint", "These settings are applied immediately and written to config.yml. Session lifetime changes also recalculate currently valid sessions from their creation time.")}</small></p>
      <div class="kwc-admin-section-stack kwc-settings-stack">
        ${adminSettingGroup("admin.settingsGroupGuestCaptcha", "Guest & CAPTCHA", guestCaptchaRows)}
        ${adminSettingGroup("admin.settingsGroupAuthSessions", "Authentication & sessions", authSessionRows)}
        ${adminSettingGroup("admin.settingsGroupProfiles", "User profiles", profileRows)}
        ${adminSettingGroup("admin.settingsGroupUploads", "Uploads", uploadRows)}
        ${adminSettingGroup("admin.settingsGroupDiscordAlerts", "Discord admin alerts", discordAlertRows)}
      </div>
      <div class="kwc-row kwc-admin-save-row"><button class="kwc-button" type="button" id="kwc-settings-save">${t("button.save", "Save")}</button><small class="kwc-admin-result" id="kwc-settings-result"></small></div>`;
    content.querySelector("#kwc-settings-save").onclick = () => saveAdminSettingElements(content);
  }

  function filterRuleMappingText(rule) {
    return Object.entries(rule?.mappings || {}).map(([k,v]) => `${k} => ${v}`).join("\n");
  }

  function filterActionLabel(action) {
    const value = String(action || "block").toLowerCase();
    if (value === "mask") return t("admin.filterActionMask", "Mask");
    if (value === "replace") return t("admin.filterActionReplace", "Replace");
    return t("admin.filterActionBlock", "Block");
  }

  function filterRuleCard(rule) {
    const words = Array.isArray(rule?.words) ? rule.words : [];
    return `<div class="kwc-filter-rule-card" data-filter-rule-card="${esc(rule?.id || "")}">
      <div class="kwc-filter-rule-card-main">
        <div class="kwc-filter-rule-card-title"><strong>${esc(rule?.id || "-")}</strong><span class="kwc-filter-rule-badge">${esc(filterActionLabel(rule?.action))}</span>${rule?.enabled === false ? `<span class="kwc-filter-rule-disabled">${t("admin.filterDisabled", "Disabled")}</span>` : ""}</div>
        <small>${esc(words.join(", "))}</small>
      </div>
      <div class="kwc-filter-rule-card-actions"><button class="kwc-button" type="button" data-filter-edit="${esc(rule?.id || "")}">${t("button.edit", "Edit")}</button><button class="kwc-button" type="button" data-filter-remove="${esc(rule?.id || "")}">${t("button.delete", "Delete")}</button></div>
    </div>`;
  }

  function filterWordListCard(file) {
    const name = String(file?.name || "");
    const enabled = file?.enabled !== false;
    const action = String(file?.action || "block").toLowerCase() === "mask" ? "mask" : "block";
    const count = Number(file?.wordCount || 0);
    const actionLabel = action === "mask" ? t("admin.filterListActionMask", "Filter") : t("admin.filterActionBlock", "Block");
    return `<div class="kwc-filter-list-card" data-filter-list-card="${esc(name)}">
      <div class="kwc-filter-list-card-main">
        <div class="kwc-filter-rule-card-title"><strong>${esc(name || "-")}</strong><span class="kwc-filter-rule-badge">${fmt("admin.filterListWordCount", "{count} words", {count})}</span><span class="kwc-filter-rule-badge">${esc(actionLabel)}</span>${enabled ? "" : `<span class="kwc-filter-rule-disabled">${t("admin.filterDisabled", "Disabled")}</span>`}</div>
      </div>
      <div class="kwc-filter-rule-card-actions">
        <button class="kwc-button" type="button" data-filter-list-edit="${esc(name)}">${t("button.edit", "Edit")}</button>
        <button class="kwc-button" type="button" data-filter-list-toggle="${esc(name)}" data-enabled="${enabled ? "true" : "false"}">${enabled ? t("admin.filterListDisable", "Disable") : t("admin.filterListEnable", "Enable")}</button>
        <button class="kwc-button" type="button" data-filter-list-delete="${esc(name)}">${t("button.delete", "Delete")}</button>
      </div>
    </div>`;
  }

  function filterRuleGuideHtml() {
    return `<details class="kwc-filter-rule-guide">
      <summary>${t("admin.filterGuideTitle", "Custom rule examples")}</summary>
      <div class="kwc-filter-rule-guide-body">
        <p>${t("admin.filterGuideChoose", "Use filter-word TXT lists for many simple Block/Filter words. Use custom rules when you need a different action, replacement candidates, or per-word replacements.")}</p>
        <div class="kwc-filter-rule-guide-example"><strong>${t("admin.filterGuideBlockTitle", "1. Block a whole message")}</strong><code>${esc(t("admin.filterGuideBlockExample", "Action: Block\nTarget words:\nadvertisement\nscam-link"))}</code></div>
        <div class="kwc-filter-rule-guide-example"><strong>${t("admin.filterGuideMaskTitle", "2. Mask only the matched text")}</strong><code>${esc(t("admin.filterGuideMaskExample", "Action: Mask\nTarget words:\nword1\nword2\nResult: matched text → content-filter.mask.text"))}</code></div>
        <div class="kwc-filter-rule-guide-example"><strong>${t("admin.filterGuideReplaceTitle", "3. Replace with shared candidates")}</strong><code>${esc(t("admin.filterGuideReplaceExample", "Action: Replace\nTarget words:\nword1\nword2\nReplacement candidates:\nsoft expression\nanother expression\nMode: First or Random"))}</code></div>
        <div class="kwc-filter-rule-guide-example"><strong>${t("admin.filterGuideMappingTitle", "4. Give each word its own replacement")}</strong><code>${esc(t("admin.filterGuideMappingExample", "Per-word replacements:\nword1 => replacement A\nword2 => replacement B"))}</code><small>${t("admin.filterGuideMappingNote", "A mapping key is automatically treated as a target word even if it is omitted from Target words. Per-word mappings take priority over shared replacement candidates.")}</small></div>
        <div class="kwc-filter-rule-guide-example"><strong>${t("admin.filterGuideEvasionTitle", "5. Anti-evasion examples")}</strong><small>${t("admin.filterGuideEvasionText", "Compact matching catches separators such as 'word 1' or 'word-1' when the compact form matches. Interleave matching catches inserted letters/numbers such as 'woXrd' within interleave-max-gap. A Hangul jamo-only rule such as 'ㅅㅂ' remains a jamo shorthand rule and does not mean every complete Korean word with those initials.")}</small></div>
        <p class="kwc-filter-rule-guide-test">${t("admin.filterGuideTest", "After saving, use Test below. It evaluates both TXT lists and custom rules without sending a message, even while the live filter is disabled.")}</p>
      </div>
    </details>`;
  }

  async function renderAdminFilter(content, suppliedData = null) {
    const data = suppliedData && suppliedData.ok ? suppliedData : await adminApi("/admin/filter");
    if (!data?.ok) throw new Error(data?.error || "filter_failed");
    if (Number(data.writeProtocol || 0) !== 5) throw new Error("admin_api_mismatch");
    const v = data.settings || {}, rules = Array.isArray(data.rules) ? data.rules : [];
    const wordLists = Array.isArray(data.wordLists) ? data.wordLists : [];
    content.innerHTML = `
      <h4>${t("admin.filter", "Filter")}</h4>
      <p><small>${t("admin.filterHint", "Registered KWC emoji tokens are protected from blocking/filtering. Unregistered :fake: tokens are treated as ordinary text.")}</small></p>
      <div class="kwc-admin-section-stack kwc-filter-settings-stack">
        ${adminSettingGroup("admin.filterGroupGeneral", "General & scopes", [
          adminSettingInput("content-filter.enabled", v["content-filter.enabled"], "boolean"),
          adminSettingInput("content-filter.scopes.public", v["content-filter.scopes.public"], "boolean"),
          adminSettingInput("content-filter.scopes.group", v["content-filter.scopes.group"], "boolean"),
          adminSettingInput("content-filter.scopes.dm", v["content-filter.scopes.dm"], "boolean")
        ])}
        ${adminSettingGroup("admin.filterGroupBehavior", "Blocking & filtering", [
          adminSettingInput("content-filter.block.show-matched-word", v["content-filter.block.show-matched-word"], "boolean"),
          adminSettingInput("content-filter.mask.text", v["content-filter.mask.text"], "text")
        ])}
        ${adminSettingGroup("admin.filterGroupAntiEvasion", "Anti-evasion", [
          adminSettingInput("content-filter.anti-evasion.unicode-normalization", v["content-filter.anti-evasion.unicode-normalization"], "boolean"),
          adminSettingInput("content-filter.anti-evasion.compact-match", v["content-filter.anti-evasion.compact-match"], "boolean"),
          adminSettingInput("content-filter.anti-evasion.interleave-match", v["content-filter.anti-evasion.interleave-match"], "boolean"),
          adminSettingInput("content-filter.anti-evasion.interleave-max-gap", v["content-filter.anti-evasion.interleave-max-gap"], "number"),
          adminSettingInput("content-filter.anti-evasion.interleave-unlimited-gap", v["content-filter.anti-evasion.interleave-unlimited-gap"], "boolean"),
          adminSettingInput("content-filter.anti-evasion.collapse-repeats", v["content-filter.anti-evasion.collapse-repeats"], "boolean"),
          adminSettingInput("content-filter.anti-evasion.repeat-limit", v["content-filter.anti-evasion.repeat-limit"], "number")
        ])}
      </div>
      <div class="kwc-row kwc-admin-save-row"><button class="kwc-button" type="button" id="kwc-filter-settings-save">${t("button.save", "Save")}</button><small class="kwc-admin-result" id="kwc-filter-result"></small></div>

      <section class="kwc-admin-section-card kwc-filter-management-card">
        <div class="kwc-filter-section-head"><h4>${t("admin.filterWordLists", "Filter word lists")}</h4><div class="kwc-row"><span class="kwc-filter-rule-count">${fmt("admin.filterWordListsSummary", "{files} file(s) · {words} active words", {files:wordLists.length, words:Number(data.activeWordCount || 0)})}</span><button class="kwc-button" type="button" id="kwc-filter-list-new">${t("admin.filterListNew", "New list")}</button><button class="kwc-button" type="button" id="kwc-filter-list-import">${t("admin.filterListImport", "Import TXT")}</button></div></div>
        <small class="kwc-filter-list-hint">${t("admin.filterWordListsHint", "UTF-8 .txt files in filter-lists/. Use one word per line. Choose Block to reject the whole message or Filter to mask matched text using content-filter.mask.text. Blank lines and lines beginning with # are ignored.")}</small>
        <div class="kwc-filter-list-list" id="kwc-filter-list-list">
          ${wordLists.map(filterWordListCard).join("") || `<div class="kwc-filter-empty">${t("admin.filterWordListsEmpty", "No filter word list files are registered.")}</div>`}
        </div>
        <input type="file" id="kwc-filter-list-file" accept=".txt,text/plain" hidden>
        <div class="kwc-filter-list-editor" id="kwc-filter-list-editor" hidden>
          <div class="kwc-filter-editor-top kwc-filter-list-editor-top">
            <label class="kwc-filter-field"><span>${t("admin.filterListName", "File name")}</span><input class="kwc-input" id="kwc-filter-list-name" placeholder="filter-words.txt"></label>
            <label class="kwc-filter-field"><span>${t("admin.filterAction", "Action")}</span><select class="kwc-input" id="kwc-filter-list-action"><option value="block">${t("admin.filterActionBlock", "Block")}</option><option value="mask">${t("admin.filterListActionMask", "Filter")}</option></select></label>
            <label class="kwc-filter-toggle"><input type="checkbox" id="kwc-filter-list-enabled" checked><span>${t("admin.enabled", "Enabled")}</span></label>
          </div>
          <label class="kwc-filter-field"><span>${t("admin.filterListWords", "Filter words")}</span><textarea class="kwc-input" id="kwc-filter-list-text" rows="10" placeholder="${t("admin.filterListWordsHint", "One word per line. # starts a comment.")}"></textarea></label>
          <div class="kwc-filter-editor-actions"><button class="kwc-button" type="button" id="kwc-filter-list-save">${t("button.save", "Save")}</button><button class="kwc-button" type="button" id="kwc-filter-list-close">${t("button.close", "Close")}</button><small class="kwc-admin-result" id="kwc-filter-list-result"></small></div>
        </div>
      </section>

      <section class="kwc-admin-section-card kwc-filter-management-card">
        <div class="kwc-filter-section-head"><h4>${t("admin.filterRules", "Custom rules")}</h4><div class="kwc-row"><span class="kwc-filter-rule-count">${fmt("admin.filterRulesCount", "{count} rule(s)", {count:rules.length})}</span><button class="kwc-button" type="button" id="kwc-filter-rule-new">${t("admin.filterNewRule", "New rule")}</button></div></div>
        ${filterRuleGuideHtml()}
        <div class="kwc-filter-rule-list" id="kwc-filter-rule-list">
          ${rules.map(filterRuleCard).join("") || `<div class="kwc-filter-empty">${t("admin.filterRulesEmpty", "No filter rules are registered in config.yml.")}</div>`}
        </div>

        <div class="kwc-admin-subsection-title">${t("admin.filterRuleEditor", "Rule editor")}</div>
        <div class="kwc-filter-editor-card">
          <div class="kwc-filter-editor-top">
            <label class="kwc-filter-field kwc-filter-field-id"><span>${t("admin.filterRuleId", "Rule ID")}</span><input class="kwc-input" id="kwc-filter-id" placeholder="rule-id"></label>
            <label class="kwc-filter-field"><span>${t("admin.filterAction", "Action")}</span><select class="kwc-input" id="kwc-filter-action"><option value="block">${t("admin.filterActionBlock", "Block")}</option><option value="mask">${t("admin.filterActionMask", "Mask")}</option><option value="replace">${t("admin.filterActionReplace", "Replace")}</option></select></label>
            <label class="kwc-filter-toggle"><input type="checkbox" id="kwc-filter-rule-enabled" checked><span>${t("admin.enabled", "Enabled")}</span></label>
          </div>
          <label class="kwc-filter-field"><span>${t("admin.filterWords", "Target words")}</span><textarea class="kwc-input" id="kwc-filter-words" rows="5" placeholder="${t("admin.filterWordsHint", "One target word per line")}"></textarea></label>
          <div class="kwc-filter-replace-options" id="kwc-filter-replace-options">
            <label class="kwc-filter-field kwc-filter-mode-field"><span>${t("admin.filterReplacementMode", "Replacement mode")}</span><select class="kwc-input" id="kwc-filter-replacement-mode"><option value="first">${t("admin.filterModeFirst", "Use first replacement")}</option><option value="random">${t("admin.filterModeRandom", "Choose a random replacement")}</option></select><small>${t("admin.filterReplacementModeHint", "Per-word replacements take priority when configured.")}</small></label>
            <label class="kwc-filter-field"><span>${t("admin.filterReplacements", "Replacement candidates")}</span><textarea class="kwc-input" id="kwc-filter-replacements" rows="4" placeholder="${t("admin.filterReplacementsHint", "One replacement per line; used by replace action")}"></textarea></label>
            <label class="kwc-filter-field"><span>${t("admin.filterMappings", "Per-word replacements")}</span><textarea class="kwc-input" id="kwc-filter-mappings" rows="4" placeholder="${t("admin.filterMappingsHint", "word => replacement")}"></textarea></label>
          </div>
          <div class="kwc-filter-editor-actions"><button class="kwc-button" type="button" id="kwc-filter-rule-save">${t("button.save", "Save")}</button><button class="kwc-button" type="button" id="kwc-filter-rule-clear">${t("admin.filterClearEditor", "Clear")}</button><small class="kwc-admin-result" id="kwc-filter-rule-result"></small></div>
        </div>
      </section>

      <section class="kwc-admin-section-card kwc-filter-management-card">
        <div class="kwc-admin-section-title">${t("admin.filterTest", "Test")}</div>
        <div class="kwc-filter-test-card kwc-filter-test-card-grouped">
          <small class="kwc-filter-test-hint">${t("admin.filterTestHint", "Tests filter word lists and custom rules without sending a message, even when the live filter is disabled.")}</small>
          <div class="kwc-filter-test-row"><select class="kwc-input" id="kwc-filter-test-scope"><option value="public">${t("admin.filterScopePublic", "Public")}</option><option value="group">${t("admin.filterScopeGroup", "Group")}</option><option value="dm">${t("admin.filterScopeDm", "DM")}</option></select><input class="kwc-input" id="kwc-filter-test-text" placeholder="${t("admin.filterTestText", "Text to test")}"><button class="kwc-button" type="button" id="kwc-filter-test-run">${t("button.test", "Test")}</button></div>
          <div class="kwc-filter-test-result" id="kwc-filter-test-result"></div>
        </div>
      </section>`

    content.querySelector("#kwc-filter-settings-save").onclick = () => saveAdminSettingElements(content, "[data-setting-path]");

    const listEditor = content.querySelector("#kwc-filter-list-editor");
    const listName = content.querySelector("#kwc-filter-list-name");
    const listText = content.querySelector("#kwc-filter-list-text");
    const listEnabled = content.querySelector("#kwc-filter-list-enabled");
    const listAction = content.querySelector("#kwc-filter-list-action");
    const listResult = content.querySelector("#kwc-filter-list-result");
    const openListEditor = (name = "filter-words.txt", text = "", enabled = true, action = "block", lockName = false) => {
      if (!listEditor || !listName || !listText || !listEnabled || !listAction) return;
      listName.value = name || "filter-words.txt";
      listName.disabled = !!lockName;
      listName.dataset.originalName = lockName ? String(name || "") : "";
      listText.value = String(text || "");
      listEnabled.checked = enabled !== false;
      listAction.value = String(action || "block").toLowerCase() === "mask" ? "mask" : "block";
      if (listResult) listResult.textContent = "";
      listEditor.hidden = false;
      if (listEditor.scrollIntoView) listEditor.scrollIntoView({behavior:"smooth", block:"nearest"});
      setTimeout(() => (lockName ? listText : listName).focus(), 0);
    };
    const closeListEditor = () => { if (listEditor) listEditor.hidden = true; };
    content.querySelector("#kwc-filter-list-new").onclick = () => openListEditor("filter-words.txt", "", true, "block", false);
    content.querySelector("#kwc-filter-list-close").onclick = closeListEditor;
    const importInput = content.querySelector("#kwc-filter-list-file");
    content.querySelector("#kwc-filter-list-import").onclick = () => importInput?.click();
    if (importInput) importInput.onchange = async () => {
      const file = importInput.files && importInput.files[0];
      importInput.value = "";
      if (!file) return;
      try {
        const text = await file.text();
        openListEditor(file.name || "filter-words.txt", text, true, "block", false);
      } catch (err) {
        if (listResult) listResult.textContent = fmt("admin.failed", "Failed: {error}", {error:err?.message || "file_read_failed"});
      }
    };
    content.querySelectorAll("[data-filter-list-edit]").forEach(btn => btn.onclick = async () => {
      const name = String(btn.dataset.filterListEdit || "");
      btn.disabled = true;
      try {
        const res = await adminApi("/admin/filter/lists?name=" + encodeURIComponent(name));
        if (!res?.ok) throw new Error(res?.error || "filter_list_read_failed");
        openListEditor(res.file?.name || name, res.text || "", res.file?.enabled !== false, res.file?.action || "block", true);
      } catch (err) {
        alert(fmt("admin.failed", "Failed: {error}", {error:err?.response?.error || err?.message || "unknown"}));
      } finally { btn.disabled = false; }
    });
    content.querySelectorAll("[data-filter-list-toggle]").forEach(btn => btn.onclick = async () => {
      const name = String(btn.dataset.filterListToggle || "");
      const enabled = String(btn.dataset.enabled || "true") !== "true";
      btn.disabled = true;
      try {
        const res = await adminWrite("/admin/filter/lists", {operation:"toggle", name, enabled:String(enabled)});
        if (!res?.ok) throw new Error(res?.error || "filter_list_write_failed");
        await renderAdminFilter(content);
      } catch (err) {
        alert(fmt("admin.failed", "Failed: {error}", {error:err?.response?.error || err?.message || "unknown"}));
      } finally { if (btn.isConnected) btn.disabled = false; }
    });
    content.querySelectorAll("[data-filter-list-delete]").forEach(btn => btn.onclick = async () => {
      const name = String(btn.dataset.filterListDelete || "");
      if (!confirmPlain(fmt("admin.filterListDeleteConfirm", "Delete {name}?", {name}))) return;
      btn.disabled = true;
      try {
        const res = await adminWrite("/admin/filter/lists", {operation:"delete", name});
        if (!res?.ok) throw new Error(res?.error || "filter_list_write_failed");
        await renderAdminFilter(content);
      } catch (err) {
        alert(fmt("admin.failed", "Failed: {error}", {error:err?.response?.error || err?.message || "unknown"}));
      } finally { if (btn.isConnected) btn.disabled = false; }
    });
    content.querySelector("#kwc-filter-list-save").onclick = async () => {
      const save = content.querySelector("#kwc-filter-list-save");
      if (!listName || !listText || !listEnabled || !listAction || !save) return;
      save.disabled = true;
      if (listResult) listResult.textContent = t("admin.settingsSaving", "Saving...");
      try {
        const res = await adminWrite("/admin/filter/lists", {operation:"save", name:listName.value, text:listText.value, enabled:String(listEnabled.checked), action:listAction.value});
        if (!res?.ok) throw new Error(res?.error || "filter_list_write_failed");
        await renderAdminFilter(content);
      } catch (err) {
        if (listResult) listResult.textContent = fmt("admin.failed", "Failed: {error}", {error:err?.response?.error || err?.message || "unknown"});
      } finally { if (save.isConnected) save.disabled = false; }
    };

    const replaceOptions = content.querySelector("#kwc-filter-replace-options");
    const actionEl = content.querySelector("#kwc-filter-action");
    const syncReplaceVisibility = () => {
      if (replaceOptions) replaceOptions.hidden = actionEl?.value !== "replace";
    };
    let editingRuleId = "";
    const fillRule = (rule, originalId) => {
      // An explicit empty originalId means CREATE. Do not fall back to rule.id:
      // doing so turns a new rule into an edit of a rule that does not exist.
      editingRuleId = originalId !== undefined ? String(originalId || "") : String(rule?.id || "");
      content.querySelector("#kwc-filter-id").value = rule?.id || "";
      content.querySelector("#kwc-filter-action").value = rule?.action || "block";
      content.querySelector("#kwc-filter-replacement-mode").value = rule?.replacementMode || "first";
      content.querySelector("#kwc-filter-words").value = (rule?.words || []).join("\n");
      content.querySelector("#kwc-filter-replacements").value = (rule?.replacements || []).join("\n");
      content.querySelector("#kwc-filter-mappings").value = filterRuleMappingText(rule);
      content.querySelector("#kwc-filter-rule-enabled").checked = rule?.enabled !== false;
      syncReplaceVisibility();
    };
    const newRuleId = () => {
      const existing = new Set(rules.map(r => String(r?.id || "").toLowerCase()));
      let n = 1;
      while (existing.has(`rule-${n}`)) n++;
      return `rule-${n}`;
    };
    const startNewRule = () => {
      const id = newRuleId();
      fillRule({id, enabled:true, action:"block", replacementMode:"first", words:[], replacements:[], mappings:{}}, "");
      const editor = content.querySelector(".kwc-filter-editor-card");
      const words = content.querySelector("#kwc-filter-words");
      if (editor?.scrollIntoView) editor.scrollIntoView({behavior:"smooth", block:"nearest"});
      if (words) setTimeout(() => words.focus(), 0);
    };
    actionEl.onchange = syncReplaceVisibility;
    syncReplaceVisibility();
    content.querySelectorAll("[data-filter-edit]").forEach(btn => btn.onclick = () => {
      const rule = rules.find(r => r.id === btn.dataset.filterEdit);
      if (rule) fillRule(rule, rule.id);
    });
    content.querySelector("#kwc-filter-rule-new").onclick = startNewRule;
    content.querySelector("#kwc-filter-rule-clear").onclick = () => fillRule(null, "");
    content.querySelectorAll("[data-filter-remove]").forEach(btn => btn.onclick = async () => {
      const result = content.querySelector("#kwc-filter-rule-result");
      const id = String(btn.dataset.filterRemove || "");
      btn.disabled = true;
      if (result) result.textContent = t("admin.settingsSaving", "Saving...");
      try {
        const res = await adminWrite("/admin/filter/rules", {operation:"remove", id});
        if (!res?.ok) {
          if (result) result.textContent = fmt("admin.failed", "Failed: {error}", {error:res?.error || "unknown"});
          return;
        }
        if (Number(res.writeProtocol || 0) !== 5) throw new Error("admin_api_mismatch");
        if (!Array.isArray(res.rules)) throw new Error("filter_verify_failed");
        // The remove transaction only returns ok=true after config.yml has been
        // written and read back successfully. Render that authoritative snapshot
        // directly instead of performing a second client-side semantic verify.
        await renderAdminFilter(content, res);
      } catch (err) {
        if (result && result.isConnected) result.textContent = fmt("admin.failed", "Failed: {error}", {error:err?.response?.error || err?.message || "unknown"});
      } finally {
        if (btn.isConnected) btn.disabled = false;
      }
    });
    content.querySelector("#kwc-filter-rule-save").onclick = async () => {
      const save = content.querySelector("#kwc-filter-rule-save");
      const result = content.querySelector("#kwc-filter-rule-result");
      if (save) save.disabled = true;
      if (result) result.textContent = t("admin.settingsSaving", "Saving...");
      const body = {operation:editingRuleId ? "update" : "create", originalId:editingRuleId, id:content.querySelector("#kwc-filter-id").value, action:content.querySelector("#kwc-filter-action").value,
        replacementMode:content.querySelector("#kwc-filter-replacement-mode").value, words:content.querySelector("#kwc-filter-words").value,
        replacements:content.querySelector("#kwc-filter-replacements").value, mappings:content.querySelector("#kwc-filter-mappings").value,
        enabled:String(content.querySelector("#kwc-filter-rule-enabled").checked)};
      const normalizeRuleId = value => String(value || "").trim().toLowerCase().replace(/[^a-z0-9._-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 64);
      body.id = normalizeRuleId(body.id);
      if (!body.id) body.id = newRuleId();
      content.querySelector("#kwc-filter-id").value = body.id;
      try {
        const res = await adminWrite("/admin/filter/rules", body);
        if (!res?.ok) {
          if (result) result.textContent = fmt("admin.failed", "Failed: {error}", {error:res?.error || "unknown"});
          return;
        }
        if (Number(res.writeProtocol || 0) !== 5) throw new Error("admin_api_mismatch");
        if (!Array.isArray(res.rules)) throw new Error("filter_verify_failed");
        // The backend compares the complete normalized rule list against the file
        // it just wrote before returning ok=true. Rendering that exact persisted
        // snapshot avoids false failures from browser-side comparisons of fields
        // that the server legitimately normalizes for the selected action.
        await renderAdminFilter(content, res);
      } catch (err) {
        if (result && result.isConnected) result.textContent = fmt("admin.failed", "Failed: {error}", {error:err?.response?.error || err?.message || "unknown"});
      } finally {
        if (save && save.isConnected) save.disabled = false;
      }
    };

    const runFilterTest = async () => {
      const button = content.querySelector("#kwc-filter-test-run");
      const box = content.querySelector("#kwc-filter-test-result");
      const scopeEl = content.querySelector("#kwc-filter-test-scope");
      const textEl = content.querySelector("#kwc-filter-test-text");
      if (!button || !box || !scopeEl || !textEl) return;
      button.disabled = true;
      box.textContent = t("admin.filterTesting", "Testing...");
      try {
        const requestedText = String(textEl.value ?? "");
        const requestedScope = String(scopeEl.value || "public").toLowerCase();
        const res = await adminWrite("/admin/filter/test", {scope:requestedScope, text:requestedText});
        if (!res?.ok) {
          box.textContent = fmt("admin.failed", "Failed: {error}", {error:res?.error || "unknown"});
          return;
        }
        if (Number(res.writeProtocol || 0) !== 5) throw new Error("admin_api_mismatch");
        const testedText = String(res.testedText ?? "");
        const stateText = res.blocked ? t("admin.filterTestBlocked", "Blocked") : (res.changed ? t("admin.filterTestChanged", "Changed") : t("admin.filterTestNoMatch", "No match"));
        const meta = [];
        if (res.ruleId) meta.push(`${t("admin.filterTestRule", "Rule")}: ${esc(res.ruleId)}`);
        if (res.matchedWord) meta.push(`${t("admin.filterTestWord", "Word")}: ${esc(res.matchedWord)}`);
        if (res.matchMode) meta.push(`${t("admin.filterTestMatch", "Match")}: ${esc(res.matchMode)}`);
        const output = res.message == null ? "" : String(res.message);
        box.innerHTML = `<strong>${esc(stateText)}</strong>${meta.length ? `<small>${meta.join(" · ")}</small>` : ""}<small>${esc(t("admin.filterTestInput", "Tested input"))}: ${esc(testedText)}</small><div>${esc(output)}</div><small>${fmt("admin.filterTestRuleCount", "Loaded rules: {count}", {count:Number(res.ruleCount || 0)})}${Number(res.wordListWordCount || 0) > 0 ? ` · ${fmt("admin.filterTestListWordCount", "List words: {count}", {count:Number(res.wordListWordCount || 0)})}` : ""}</small>`;
      } catch (err) {
        box.textContent = fmt("admin.failed", "Failed: {error}", {error:err?.response?.error || err?.message || "unknown"});
      } finally {
        button.disabled = false;
      }
    };
    content.querySelector("#kwc-filter-test-run").onclick = runFilterTest;
    content.querySelector("#kwc-filter-test-text").addEventListener("keydown", e => {
      if (e.key === "Enter" && !e.isComposing) { e.preventDefault(); runFilterTest(); }
    });
  }

  async function renderAdminSummary(content) {
    const summary = await adminApi("/admin/summary");
    const online = await adminApi("/admin/online");
    content.innerHTML = `
      <div class="kwc-admin-grid">
        <div>${t("admin.online", "Online")}</div><strong>${esc(summary.onlineCount)}</strong>
        <div>${t("admin.accounts", "Accounts")}</div><strong>${esc(summary.accountCount)}</strong>
        <div>${t("admin.sessions", "Sessions")}</div><strong>${esc(summary.sessionCount)}</strong>
        <div>${t("admin.mutes", "Mutes")}</div><strong>${esc(summary.muteCount)}</strong>
      </div>
      <h4>${t("admin.onlinePlayers", "Online players")}</h4>
      <div class="kwc-admin-list">
        ${(online.players || []).map(p => `<div>${directMessageIdentityHtml({displayName: p.displayName || p.name || "", username: p.name || "", uuid: p.uuid || ""}, "kwc-sender")}</div>`).join("") || `<em>${t("admin.none", "none")}</em>`}
      </div>
      <br>
      <div class="kwc-row kwc-admin-actions-row">
        <button class="kwc-button" id="kwc-clear-history">${t("button.clearHistory", "Clear web history")}</button>
        <button class="kwc-button" id="kwc-toggle-moderation-actions" type="button" aria-pressed="${state.moderationActionsVisible ? "true" : "false"}">${moderationActionsToggleLabel()}</button>
      </div>
    `;
    installSenderIdentityToggle(content);
    const clear = content.querySelector("#kwc-clear-history");
    if (clear) clear.onclick = async () => {
      if (!confirmPlain(t("alert.confirmClearHistory", "Clear web chat history?"))) return;
      const res = await adminWrite("/admin/clear-history", {});
      if (!res.ok) alertResponse("alert.failed", "Failed: {error}", res);
    };
    const toggleModeration = content.querySelector("#kwc-toggle-moderation-actions");
    updateModerationActionsToggleButton(toggleModeration);
    if (toggleModeration) toggleModeration.onclick = () => {
      setModerationActionsVisible(!state.moderationActionsVisible);
      updateModerationActionsToggleButton(toggleModeration);
    };
  }

  async function renderAdminMutes(content) {
    const data = await adminApi("/admin/mutes");
    content.innerHTML = `
      <h4>${t("admin.muteGuestIp", "Mute guest/IP")}</h4>
      <div class="kwc-row">
        <select class="kwc-input" id="kwc-mute-type">
          <option value="guest">${t("admin.typeGuest", "guest")}</option>
          <option value="ip">${t("admin.typeIp", "ip")}</option>
        </select>
        <input class="kwc-input" id="kwc-mute-value" placeholder="${t("placeholder.muteTarget", "Guest name or IP")}">
      </div>
      <div class="kwc-row">
        <input class="kwc-input" id="kwc-mute-min" placeholder="${t("placeholder.minutes", "minutes")}" value="${esc(state.config?.defaultMuteMinutes || 60)}">
        <input class="kwc-input" id="kwc-mute-reason" placeholder="${t("placeholder.reason", "reason")}">
        <button class="kwc-button" id="kwc-mute-add">${t("button.mute", "Mute")}</button>
      </div>
      <h4>${t("admin.currentMutes", "Current mutes")}</h4>
      <div class="kwc-admin-list">
        ${(data.mutes || []).map(m => `
          <div class="kwc-admin-item">
            <div><strong>${esc(m.type)}</strong>: ${esc(m.value)}<br><small>${esc(m.reason || "")}</small></div>
            <button class="kwc-button" data-unmute-type="${esc(m.type)}" data-unmute-value="${esc(m.value)}">${t("button.unmute", "Unmute")}</button>
          </div>
        `).join("") || `<em>${t("admin.none", "none")}</em>`}
      </div>
    `;
    content.querySelector("#kwc-mute-add").onclick = async () => {
      const body = {
        type: content.querySelector("#kwc-mute-type").value,
        value: content.querySelector("#kwc-mute-value").value,
        minutes: content.querySelector("#kwc-mute-min").value,
        reason: content.querySelector("#kwc-mute-reason").value
      };
      const res = await adminWrite("/admin/mute", body);
      if (!res.ok) alertResponse("alert.failed", "Failed: {error}", res);
      await renderAdminMutes(content);
    };
    content.querySelectorAll("[data-unmute-type]").forEach(btn => {
      btn.onclick = async () => {
        const res = await adminWrite("/admin/unmute", {type: btn.dataset.unmuteType, value: btn.dataset.unmuteValue});
        if (!res.ok) alertResponse("alert.failed", "Failed: {error}", res);
        await renderAdminMutes(content);
      };
    });
  }


  async function renderAdminEmojis(content) {
    if (state.role !== "ADMIN") return;
    await loadEmojis({force: true});
    const data = await adminApi("/admin/emojis?_=" + Date.now(), {cache: "no-store"});
    const packs = Array.isArray(data.packs) ? data.packs : [];
    const items = Array.isArray(state.emojiItems) ? state.emojiItems : [];
    const packIds = new Set(packs.map(pack => String(pack.id || "default")));
    let selectedPack = String(state.adminEmojiSelectedPack || "default");
    if (!packIds.has(selectedPack)) selectedPack = packs[0] && packs[0].id ? String(packs[0].id) : "default";
    state.adminEmojiSelectedPack = selectedPack;
    localStorage.setItem("kwc.adminEmojiPack", selectedPack);

    const selectedPackInfo = packs.find(pack => String(pack.id || "default") === selectedPack) || {id: selectedPack, label: selectedPack, count: 0};
    const shown = items.filter(item => String(item.pack || "default") === selectedPack);
    const packOptions = packs.map(pack => `<option value="${esc(pack.id)}"${String(pack.id) === selectedPack ? " selected" : ""}>${esc(pack.label || pack.id)} (${esc(pack.count || 0)})</option>`).join("") || `<option value="default">Default</option>`;
    const moveTargetPacks = packs.filter(pack => String(pack.id || "default") !== selectedPack);
    const defaultMoveTarget = moveTargetPacks[0] ? String(moveTargetPacks[0].id || "default") : "";
    const movePackOptions = moveTargetPacks.map(pack => `<option value="${esc(pack.id)}"${String(pack.id) === defaultMoveTarget ? " selected" : ""}>${esc(pack.label || pack.id)} (${esc(pack.count || 0)})</option>`).join("");
    const packTabs = packs.map(pack => `<button type="button" class="kwc-button kwc-admin-emoji-tab${String(pack.id) === selectedPack ? " kwc-active" : ""}" data-admin-emoji-pack="${esc(pack.id)}">${esc(pack.label || pack.id)} <span>${esc(pack.count || 0)}</span></button>`).join("");
    const showStorageUsage = data.showStorageUsage !== false;
    const showStorageLimit = data.showStorageLimit !== false;
    const maxTotalBytes = Number(data.maxTotalSize || 0) || (Number(data.maxTotalSizeMb || 0) > 0 ? Number(data.maxTotalSizeMb) * 1024 * 1024 : 0);
    const limitLines = [
      `<small>${esc(fmt("admin.emojiFileLimit", "Per file {file}", {file: data.maxFileSizeKb ? (data.maxFileSizeKb + " KB") : t("admin.unlimited", "unlimited")}))}</small>`
    ];
    if (showStorageUsage || showStorageLimit) {
      const currentText = formatBytes(data.totalSize || 0);
      const totalText = maxTotalBytes > 0 ? formatBytes(maxTotalBytes) : t("admin.unlimited", "unlimited");
      if (showStorageUsage && showStorageLimit) {
        limitLines.push(`<small>${esc(fmt("admin.emojiStorageUsage", "Storage {current} / {total}", {current: currentText, total: totalText}))}</small>`);
      } else if (showStorageUsage) {
        limitLines.push(`<small>${esc(fmt("admin.emojiStorageCurrent", "Storage {current}", {current: currentText}))}</small>`);
      } else {
        limitLines.push(`<small>${esc(fmt("admin.emojiStorageLimit", "Storage limit {total}", {total: totalText}))}</small>`);
      }
    }

    content.innerHTML = `
      <h4>${t("admin.emojiTitle", "Custom emojis")}</h4>
      <div class="kwc-admin-emoji-tools">
        <div class="kwc-admin-emoji-toolbar">
          <div class="kwc-admin-emoji-upload-row">
            <button class="kwc-button" id="kwc-emoji-upload" type="button">${t("button.uploadEmoji", "Upload")}</button>
            <button class="kwc-button" id="kwc-emoji-pack-create" type="button">${t("button.createPack", "Create folder")}</button>
            <input id="kwc-emoji-upload-file" type="file" multiple accept=".png,.jpg,.jpeg,.gif,.webp,image/png,image/jpeg,image/gif,image/webp" hidden style="display:none !important;">
          </div>
          <div class="kwc-admin-emoji-limit-lines">${limitLines.join("")}</div>
        </div>
        <div class="kwc-upload-progress kwc-admin-emoji-upload-stage kwc-hidden" id="kwc-emoji-upload-stage" aria-live="polite">
          <div class="kwc-upload-progress-head">
            <span id="kwc-emoji-upload-stage-text">${esc(t("upload.ready", "Ready"))}</span>
            <button class="kwc-button kwc-upload-cancel" id="kwc-emoji-upload-cancel" type="button">${esc(t("button.cancel", "Cancel"))}</button>
          </div>
          <div class="kwc-upload-progress-bar"><div id="kwc-emoji-upload-fill"></div></div>
        </div>
      </div>
      <h4>${t("admin.emojiCurrent", "Current emojis")}</h4>
      <div class="kwc-admin-emoji-tabs">${packTabs || `<button type="button" class="kwc-button kwc-admin-emoji-tab kwc-active" data-admin-emoji-pack="default">Default <span>0</span></button>`}</div>
      <div class="kwc-admin-emoji-pack-actions">
        <div class="kwc-admin-emoji-control-row kwc-admin-emoji-selected-row">
          <div class="kwc-admin-emoji-control-main">
            <strong class="kwc-admin-emoji-control-label">${esc(t("admin.emojiFolderSelect", "Selected folder"))}</strong>
            <select class="kwc-input" id="kwc-emoji-pack-select" aria-label="${esc(t("admin.emojiFolderSelect", "Selected folder"))}">${packOptions}</select>
            <span class="kwc-admin-emoji-pack-count">${esc(fmt("admin.emojiPackCount", "{count} emojis", {count: shown.length}))}</span>
          </div>
          <div class="kwc-admin-emoji-control-actions kwc-admin-emoji-folder-actions">
            ${selectedPack !== "default" ? `<button class="kwc-button" id="kwc-emoji-rename-pack" type="button">${t("button.renamePack", "Rename folder")}</button><button class="kwc-button" id="kwc-emoji-delete-pack" type="button">${t("button.deletePack", "Delete folder")}</button>` : ""}
          </div>
        </div>
        <div class="kwc-admin-emoji-control-row kwc-admin-emoji-move-row">
          <div class="kwc-admin-emoji-control-main">
            <strong class="kwc-admin-emoji-control-label">${esc(t("admin.emojiMoveTarget", "Destination folder"))}</strong>
            <select class="kwc-input kwc-admin-emoji-move-select" id="kwc-emoji-move-pack" aria-label="${esc(t("admin.emojiMoveTarget", "Destination folder"))}" ${movePackOptions ? "" : "disabled"}>${movePackOptions || `<option value="">${esc(t("admin.emojiNoOtherFolder", "No other folder"))}</option>`}</select>
            <button class="kwc-button" id="kwc-emoji-move-selected" type="button" ${shown.length && movePackOptions ? "" : "disabled"}>${t("button.moveSelected", "Move selected")}</button>
          </div>
          <div class="kwc-admin-emoji-control-actions kwc-admin-emoji-selection-actions">
            <button class="kwc-button" id="kwc-emoji-select-all" type="button" ${shown.length ? "" : "disabled"}>${t("button.selectAll", "Select all")}</button>
            <button class="kwc-button" id="kwc-emoji-delete-selected" type="button" ${shown.length ? "" : "disabled"}>${t("button.deleteSelected", "Delete selected")}</button>
          </div>
        </div>
      </div>
      <div class="kwc-admin-list kwc-admin-emoji-list">
        ${shown.map(item => `
          <label class="kwc-admin-emoji-item">
            <input type="checkbox" class="kwc-admin-emoji-check" data-emoji-delete-id="${esc(item.id)}">
            <img src="${esc(item.url)}" alt="${esc(item.label || item.name || item.id)}" title="${esc(item.label || item.name || item.id)}" loading="lazy" draggable="false">
            <div class="kwc-admin-emoji-item-label" title="${esc(item.label || item.name || item.id)}"><strong title="${esc(item.label || item.name || item.id)}">${esc(item.label || item.name || item.id)}</strong></div>
            <div class="kwc-admin-emoji-item-actions">
              <button class="kwc-mini-action" data-emoji-rename-one="${esc(item.id)}" data-emoji-current-name="${esc(item.label || item.name || item.id)}" type="button">${t("button.change", "Change")}</button>
              <button class="kwc-mini-action" data-emoji-move-one="${esc(item.id)}" data-emoji-current-pack="${esc(item.pack || "default")}" type="button" ${movePackOptions ? "" : "disabled"}>${t("button.move", "Move")}</button>
              <button class="kwc-mini-action" data-emoji-delete-one="${esc(item.id)}" type="button">${t("button.delete", "delete")}</button>
            </div>
          </label>
        `).join("") || `<div class="kwc-admin-emoji-empty" title="${esc(t("emoji.emptyPack", "No emojis in this folder."))}">${esc(t("emoji.emptyPack", "No emojis here."))}</div>`}
      </div>
    `;

    const rerenderPack = async pack => {
      state.adminEmojiSelectedPack = String(pack || "default");
      localStorage.setItem("kwc.adminEmojiPack", state.adminEmojiSelectedPack);
      await renderAdminEmojis(content);
    };

    content.querySelectorAll("[data-admin-emoji-pack]").forEach(btn => {
      btn.onclick = () => rerenderPack(btn.dataset.adminEmojiPack || "default");
    });

    const packSelect = content.querySelector("#kwc-emoji-pack-select");
    if (packSelect) packSelect.onchange = () => rerenderPack(packSelect.value || "default");

    const uploadFileInput = content.querySelector("#kwc-emoji-upload-file");
    const uploadStage = content.querySelector("#kwc-emoji-upload-stage");
    const uploadStageText = content.querySelector("#kwc-emoji-upload-stage-text");
    const uploadFill = content.querySelector("#kwc-emoji-upload-fill");
    const uploadBtn = content.querySelector("#kwc-emoji-upload");
    const uploadCancelBtn = content.querySelector("#kwc-emoji-upload-cancel");
    const createBtn = content.querySelector("#kwc-emoji-pack-create");
    let emojiUploadXhr = null;
    let emojiUploadCancelRequested = false;
    let emojiUploadActive = false;

    const setEmojiUploadFill = percent => {
      if (!uploadFill) return;
      const value = Math.max(0, Math.min(100, Number(percent) || 0));
      uploadFill.style.width = value.toFixed(1) + "%";
    };

    const updateEmojiUploadProgress = (label, percent, active = true) => {
      if (uploadStage) uploadStage.classList.toggle("kwc-hidden", !active);
      if (uploadStageText) uploadStageText.textContent = label || "";
      setEmojiUploadFill(percent);
      if (uploadCancelBtn) {
        uploadCancelBtn.disabled = !active || !emojiUploadActive;
        uploadCancelBtn.textContent = emojiUploadCancelRequested ? t("upload.canceling", "Canceling...") : t("button.cancel", "Cancel");
      }
    };

    const setEmojiUploadControlsBusy = busy => {
      if (uploadBtn) uploadBtn.disabled = !!busy;
      if (uploadFileInput) uploadFileInput.disabled = !!busy;
      if (createBtn) createBtn.disabled = !!busy;
    };

    const uploadEmojiFormWithProgress = (form, progressCallback) => new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      emojiUploadXhr = xhr;
      xhr.upload.onprogress = event => {
        if (event && event.lengthComputable && typeof progressCallback === "function") {
          progressCallback(event.loaded, event.total);
        }
      };
      xhr.onload = () => {
        emojiUploadXhr = null;
        let data = null;
        try { data = JSON.parse(xhr.responseText || "{}"); }
        catch (_) { data = {ok: false, error: "invalid_response"}; }
        if (xhr.status < 200 || xhr.status >= 300) {
          reject({error: data && data.error ? data.error : ("HTTP " + xhr.status)});
          return;
        }
        resolve(data);
      };
      xhr.onerror = () => {
        emojiUploadXhr = null;
        reject({error: "network"});
      };
      xhr.onabort = () => {
        emojiUploadXhr = null;
        reject({aborted: true});
      };
      xhr.open("POST", apiBase + "/admin/emojis/upload", true);
      if (state.token) xhr.setRequestHeader("Authorization", "Bearer " + state.token);
      xhr.send(form);
    });

    const adminEmojiAllowedExtensions = new Set(["png", "jpg", "jpeg", "gif", "webp"]);

    const adminEmojiUploadExtension = file => {
      const rawName = String(file && file.name || "").trim();
      const dot = rawName.lastIndexOf(".");
      const rawExt = dot > 0 ? rawName.slice(dot + 1).toLowerCase() : "";
      if (adminEmojiAllowedExtensions.has(rawExt)) return rawExt;
      const mimeExt = extensionFromMime(file && file.type);
      return adminEmojiAllowedExtensions.has(mimeExt) ? mimeExt : "";
    };

    const looksLikeAndroidPhotoPickerSyntheticFile = file => {
      if (!file || typeof navigator === "undefined" || !/Android/i.test(String(navigator.userAgent || ""))) return false;
      if (!/^image\//i.test(String(file.type || ""))) return false;
      const rawName = String(file.name || "").trim();
      const dot = rawName.lastIndexOf(".");
      const base = dot > 0 ? rawName.slice(0, dot) : rawName;
      // Android Photo Picker can expose its internal media id (for example
      // 1000019131.jpg) instead of the source filename. A normal file-manager
      // selection keeps the actual filename, so only guard the observed
      // picker-shaped form: an image filename whose base is a 10+-digit number
      // beginning with "10". Pack/folder names are intentionally irrelevant.
      return /^10\d{8,}$/.test(base);
    };

    const adminEmojiFallbackBaseName = (file, index) => {
      const stampValue = Number(file && file.lastModified);
      const date = new Date(Number.isFinite(stampValue) && stampValue > 0 ? stampValue : Date.now());
      const pad = value => String(value).padStart(2, "0");
      const stamp = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
      return `emoji-${stamp}${index > 0 ? "-" + (index + 1) : ""}`;
    };

    const adminEmojiUploadName = (file, index) => {
      const rawName = String(file && file.name || "").trim();
      const ext = adminEmojiUploadExtension(file);
      if (!looksLikeAndroidPhotoPickerSyntheticFile(file)) {
        if (rawName) return rawName;
        const fallback = adminEmojiFallbackBaseName(file, index);
        return ext ? `${fallback}.${ext}` : fallback;
      }

      const suggested = adminEmojiFallbackBaseName(file, index);
      const entered = String(prompt(
        t("prompt.androidPhotoPickerEmojiName", "Android's photo picker did not provide the original filename. Enter the emoji name."),
        suggested
      ) || "").trim();
      const chosen = entered || suggested;
      const dot = chosen.lastIndexOf(".");
      const chosenExt = dot > 0 ? chosen.slice(dot + 1).toLowerCase() : "";
      const base = adminEmojiAllowedExtensions.has(chosenExt) ? chosen.slice(0, dot) : chosen;
      return ext ? `${base}.${ext}` : chosen;
    };

    const uploadAdminEmojiFiles = async files => {
      // Match the normal chat upload path: copy FileList immediately, release the
      // native input, then process that ordinary File array asynchronously.
      files = Array.from(files || []).filter(Boolean);
      if (!files.length || emojiUploadActive) return;

      const select = content.querySelector("#kwc-emoji-pack-select");
      const pack = select ? select.value : selectedPack;
      const totalBytes = files.reduce((sum, file) => sum + Math.max(1, Number(file.size) || 1), 0);
      const failures = [];
      let completedBytes = 0;
      let uploaded = 0;
      let finalPack = pack || "default";

      emojiUploadCancelRequested = false;
      emojiUploadActive = true;
      setEmojiUploadControlsBusy(true);
      updateEmojiUploadProgress(t("upload.preparing", "Preparing upload..."), 0, true);

      try {
        for (let i = 0; i < files.length; i++) {
          if (emojiUploadCancelRequested) break;
          const file = files[i];
          const uploadName = adminEmojiUploadName(file, i);
          const form = new FormData();
          form.append("pack", pack || "default");
          form.append("file", file, uploadName);
          const baseLabel = fmt("upload.progress", "Uploading {current}/{total}: {name}", {
            current: i + 1,
            total: files.length,
            name: uploadName || ""
          });
          try {
            const res = await uploadEmojiFormWithProgress(form, (loaded, size) => {
              const fileSize = Math.max(1, Number(size) || Number(file.size) || 1);
              const overall = totalBytes > 0
                ? ((completedBytes + Math.min(fileSize, loaded)) / totalBytes) * 100
                : ((i + Math.min(1, loaded / fileSize)) / files.length) * 100;
              updateEmojiUploadProgress(baseLabel, overall, true);
            });
            completedBytes += Math.max(1, Number(file.size) || 1);
            if (!res || res.ok === false) {
              failures.push({name: uploadName || file.name || "", error: res && res.error ? res.error : "upload_failed"});
              continue;
            }
            uploaded++;
            finalPack = res.pack || finalPack;
            updateEmojiUploadProgress(baseLabel, totalBytes > 0 ? (completedBytes / totalBytes) * 100 : ((i + 1) / files.length) * 100, true);
          } catch (err) {
            if (err && err.aborted) {
              emojiUploadCancelRequested = true;
              updateEmojiUploadProgress(t("upload.canceled", "Upload canceled."), 0, true);
              break;
            }
            completedBytes += Math.max(1, Number(file.size) || 1);
            failures.push({name: uploadName || file.name || "", error: err && err.error ? err.error : "network"});
          }
        }
      } finally {
        emojiUploadXhr = null;
        emojiUploadActive = false;
        setEmojiUploadControlsBusy(false);
      }

      if (uploaded > 0) {
        state.adminEmojiSelectedPack = finalPack;
        localStorage.setItem("kwc.adminEmojiPack", state.adminEmojiSelectedPack);
        await loadEmojis({force: true});
        updateEmojiButton();
      }

      if (emojiUploadCancelRequested) {
        updateEmojiUploadProgress(t("upload.canceled", "Upload canceled."), 0, true);
      } else if (failures.length) {
        updateEmojiUploadProgress(fmt("admin.emojiBatchResult", "Uploaded {uploaded}/{total}", {uploaded, total: files.length}), uploaded === files.length ? 100 : (files.length ? (uploaded / files.length) * 100 : 0), true);
        const shownFailures = failures.slice(0, 8).map(item => `${item.name} (${item.error})`);
        const extra = failures.length > shownFailures.length ? ` … (+${failures.length - shownFailures.length})` : "";
        alert(fmt("alert.emojiBatchUploadFailed", "Uploaded {uploaded}/{total}. Failed: {failed}", {
          uploaded,
          total: files.length,
          failed: shownFailures.join(", ") + extra
        }));
      } else {
        updateEmojiUploadProgress(t("upload.complete", "Upload complete."), 100, true);
      }

      setTimeout(() => {
        if (!emojiUploadActive && uploadStage) uploadStage.classList.add("kwc-hidden");
      }, 900);

      if (uploaded > 0) await renderAdminEmojis(content);
    };

    if (uploadBtn && uploadFileInput) {
      // Android Chrome routes image-only <input type=file> through the system
      // Photo Picker. Some picker-provided File handles can fail later during
      // multipart upload even though the same image works through "Browse".
      // For administrator emoji uploads, deliberately leave accept unset on
      // Android so Chrome opens the generic DocumentsUI/file picker instead.
      // File type/extension validation still happens in KWC and on the server.
      const useAndroidEmojiFilePicker = typeof navigator !== "undefined"
        && /Android/i.test(String(navigator.userAgent || ""));
      if (useAndroidEmojiFilePicker) uploadFileInput.removeAttribute("accept");
      uploadBtn.onclick = () => {
        if (useAndroidEmojiFilePicker) uploadFileInput.removeAttribute("accept");
        uploadFileInput.click();
      };
      uploadFileInput.addEventListener("change", async event => {
        const input = event.target;
        const files = Array.from(input.files || []);
        // Android Photo Picker may back File objects with a transient content URI.
        // Keep the native selection alive through the optional rename prompt and
        // multipart upload; clearing it first can invalidate the lazy file handle
        // and surface as XMLHttpRequest.onerror (reported as "network").
        try {
          await uploadAdminEmojiFiles(files);
        } finally {
          input.value = "";
        }
      });
    }

    if (uploadCancelBtn) {
      uploadCancelBtn.onclick = () => {
        if (!emojiUploadActive) return;
        emojiUploadCancelRequested = true;
        updateEmojiUploadProgress(t("upload.canceling", "Canceling..."), 0, true);
        try { if (emojiUploadXhr) emojiUploadXhr.abort(); } catch (_) {}
      };
    }

    if (createBtn) {
      createBtn.onclick = async () => {
        const next = prompt(t("prompt.createEmojiPack", "New folder name"), "");
        if (next == null) return;
        const pack = String(next || "").trim();
        if (!pack) return alert(t("alert.emojiPackNameRequired", "Enter a folder name."));
        const res = await adminWrite("/admin/emojis/create-pack", {pack});
        if (!res.ok) return alertResponse("alert.failed", "Failed: {error}", res);
        state.adminEmojiSelectedPack = res.pack || pack;
        localStorage.setItem("kwc.adminEmojiPack", state.adminEmojiSelectedPack);
        await loadEmojis({force: true});
        updateEmojiButton();
        await renderAdminEmojis(content);
      };
    }

    const deleteOne = async id => {
      if (!id) return;
      if (!confirmPlain(t("alert.confirmDeleteEmoji", "Delete this emoji?"))) return;
      const res = await adminWrite("/admin/emojis/delete", {type: "item", id});
      if (!res.ok) return alertResponse("alert.failed", "Failed: {error}", res);
      await loadEmojis({force: true});
      updateEmojiButton();
      await renderAdminEmojis(content);
    };

    const renameOne = async (id, currentName) => {
      if (!id) return;
      const next = prompt(t("prompt.renameEmoji", "New emoji name"), currentName || "");
      if (next == null) return;
      const name = String(next || "").trim();
      if (!name) return alert(t("alert.emojiRenameNameRequired", "Enter a new name."));
      if (!confirmPlain(fmt("alert.confirmRenameEmoji", "Rename this emoji to {name}? Existing emoji tokens using the old name will no longer match.", {name}))) return;
      const res = await adminWrite("/admin/emojis/rename", {type: "item", id, name});
      if (!res.ok) return alertResponse("alert.renameFailed", "Rename failed: {error}", res);
      await loadEmojis({force: true});
      updateEmojiButton();
      await renderAdminEmojis(content);
    };

    const selectedMoveTarget = () => {
      const select = content.querySelector("#kwc-emoji-move-pack");
      return select ? String(select.value || "").trim() : "";
    };

    const moveOne = async (id, targetPack, currentPack) => {
      if (!id) return false;
      const pack = String(targetPack || "").trim();
      if (!pack) {
        alert(t("alert.emojiMoveTargetRequired", "Choose a destination folder."));
        return false;
      }
      if (currentPack && String(currentPack) === pack) {
        alert(t("alert.emojiMoveTargetSame", "Choose a different folder."));
        return false;
      }
      const res = await adminWrite("/admin/emojis/move", {type: "item", id, pack});
      if (!res.ok) {
        alertResponse("alert.moveFailed", "Move failed: {error}", res);
        return false;
      }
      return true;
    };

    content.querySelectorAll("[data-emoji-delete-one]").forEach(btn => {
      btn.onclick = event => {
        event.preventDefault();
        event.stopPropagation();
        deleteOne(btn.dataset.emojiDeleteOne || "");
      };
    });

    content.querySelectorAll("[data-emoji-rename-one]").forEach(btn => {
      btn.onclick = event => {
        event.preventDefault();
        event.stopPropagation();
        renameOne(btn.dataset.emojiRenameOne || "", btn.dataset.emojiCurrentName || "");
      };
    });

    content.querySelectorAll("[data-emoji-move-one]").forEach(btn => {
      btn.onclick = async event => {
        event.preventDefault();
        event.stopPropagation();
        const targetPack = selectedMoveTarget();
        const label = packs.find(pack => String(pack.id || "default") === targetPack);
        const targetLabel = label ? (label.label || label.id) : targetPack;
        if (!targetPack) return alert(t("alert.emojiMoveTargetRequired", "Choose a destination folder."));
        if (!confirmPlain(fmt("alert.confirmMoveEmoji", "Move this emoji to {pack}?", {pack: targetLabel}))) return;
        const ok = await moveOne(btn.dataset.emojiMoveOne || "", targetPack, btn.dataset.emojiCurrentPack || "");
        if (!ok) return;
        state.adminEmojiSelectedPack = targetPack;
        localStorage.setItem("kwc.adminEmojiPack", state.adminEmojiSelectedPack);
        await loadEmojis({force: true});
        updateEmojiButton();
        await renderAdminEmojis(content);
      };
    });

    const emojiChecks = () => Array.from(content.querySelectorAll(".kwc-admin-emoji-check"));
    const updateSelectAllButton = () => {
      const btn = content.querySelector("#kwc-emoji-select-all");
      if (!btn) return;
      const checks = emojiChecks();
      const allChecked = checks.length > 0 && checks.every(check => check.checked);
      btn.textContent = allChecked ? t("button.deselectAll", "Clear all") : t("button.selectAll", "Select all");
      btn.setAttribute("aria-pressed", allChecked ? "true" : "false");
    };

    const selectAll = content.querySelector("#kwc-emoji-select-all");
    if (selectAll) {
      content.querySelectorAll(".kwc-admin-emoji-check").forEach(check => {
        check.onchange = updateSelectAllButton;
      });
      updateSelectAllButton();
      selectAll.onclick = event => {
        event.preventDefault();
        event.stopPropagation();
        const checks = emojiChecks();
        const allChecked = checks.length > 0 && checks.every(check => check.checked);
        checks.forEach(check => { check.checked = !allChecked; });
        updateSelectAllButton();
      };
    }

    const moveSelected = content.querySelector("#kwc-emoji-move-selected");
    if (moveSelected) {
      moveSelected.onclick = async () => {
        const ids = Array.from(content.querySelectorAll(".kwc-admin-emoji-check:checked")).map(el => el.dataset.emojiDeleteId).filter(Boolean);
        if (!ids.length) return alert(t("alert.emojiMoveSelectRequired", "Select emojis to move."));
        const targetPack = selectedMoveTarget();
        if (!targetPack) return alert(t("alert.emojiMoveTargetRequired", "Choose a destination folder."));
        const label = packs.find(pack => String(pack.id || "default") === targetPack);
        const targetLabel = label ? (label.label || label.id) : targetPack;
        if (!confirmPlain(fmt("alert.confirmMoveSelectedEmoji", "Move {count} selected emojis to {pack}?", {count: ids.length, pack: targetLabel}))) return;
        moveSelected.disabled = true;
        try {
          for (const id of ids) {
            const ok = await moveOne(id, targetPack, selectedPack);
            if (!ok) return;
          }
          state.adminEmojiSelectedPack = targetPack;
          localStorage.setItem("kwc.adminEmojiPack", state.adminEmojiSelectedPack);
          await loadEmojis({force: true});
          updateEmojiButton();
          await renderAdminEmojis(content);
        } finally {
          moveSelected.disabled = false;
        }
      };
    }

    const deleteSelected = content.querySelector("#kwc-emoji-delete-selected");
    if (deleteSelected) {
      deleteSelected.onclick = async () => {
        const ids = Array.from(content.querySelectorAll(".kwc-admin-emoji-check:checked")).map(el => el.dataset.emojiDeleteId).filter(Boolean);
        if (!ids.length) return alert(t("alert.emojiSelectRequired", "Select emojis to delete."));
        if (!confirmPlain(fmt("alert.confirmDeleteSelectedEmoji", "Delete {count} selected emojis?", {count: ids.length}))) return;
        deleteSelected.disabled = true;
        try {
          for (const id of ids) {
            const res = await adminWrite("/admin/emojis/delete", {type: "item", id});
            if (!res.ok) return alertResponse("alert.failed", "Failed: {error}", res);
          }
          await loadEmojis({force: true});
          updateEmojiButton();
          await renderAdminEmojis(content);
        } finally {
          deleteSelected.disabled = false;
        }
      };
    }

    const renamePack = content.querySelector("#kwc-emoji-rename-pack");
    if (renamePack) {
      renamePack.onclick = async () => {
        const next = prompt(t("prompt.renameEmojiPack", "New folder name"), selectedPackInfo.label || selectedPack);
        if (next == null) return;
        const name = String(next || "").trim();
        if (!name) return alert(t("alert.emojiRenameNameRequired", "Enter a new name."));
        if (!confirmPlain(fmt("alert.confirmRenameEmojiPack", "Rename folder {pack} to {name}? Emoji tokens in this folder will change.", {pack: selectedPackInfo.label || selectedPack, name}))) return;
        const res = await adminWrite("/admin/emojis/rename", {type: "pack", pack: selectedPack, name});
        if (!res.ok) return alertResponse("alert.renameFailed", "Rename failed: {error}", res);
        state.adminEmojiSelectedPack = res.pack || name;
        localStorage.setItem("kwc.adminEmojiPack", state.adminEmojiSelectedPack);
        await loadEmojis({force: true});
        updateEmojiButton();
        await renderAdminEmojis(content);
      };
    }

    const deletePack = content.querySelector("#kwc-emoji-delete-pack");
    if (deletePack) {
      deletePack.onclick = async () => {
        if (!confirmPlain(fmt("alert.confirmDeleteEmojiPack", "Delete folder {pack} and all emojis inside?", {pack: selectedPack}))) return;
        const res = await adminWrite("/admin/emojis/delete", {type: "pack", pack: selectedPack});
        if (!res.ok) return alertResponse("alert.failed", "Failed: {error}", res);
        state.adminEmojiSelectedPack = "default";
        localStorage.setItem("kwc.adminEmojiPack", "default");
        await loadEmojis({force: true});
        updateEmojiButton();
        await renderAdminEmojis(content);
      };
    }
  }

  async function renderAdminAccounts(content) {
    if (state.role !== "ADMIN") return;
    const [accountData, sessionData] = await Promise.all([
      adminApi("/admin/accounts"),
      adminApi("/admin/sessions")
    ]);
    const accounts = Array.isArray(accountData?.accounts) ? accountData.accounts : [];
    const sessions = Array.isArray(sessionData?.sessions) ? sessionData.sessions : [];
    content.innerHTML = `
      <div class="kwc-admin-account-session-stack">
        <section class="kwc-admin-record-section">
          <h4>${t("admin.accounts", "Accounts")}</h4>
          <div class="kwc-admin-list kwc-admin-record-list kwc-admin-account-list">
            ${accounts.map(a => `
              <div class="kwc-admin-item">
                <div>
                  ${directMessageIdentityHtml({displayName: a.displayName || a.username || "", username: a.username || "", uuid: a.uuid || ""}, "kwc-sender")} <strong>${esc(a.role || "")}</strong><br>
                  <small>${a.local ? esc(t("account.local", "Local")) : esc(t("account.linked", "Linked"))} / ${t("admin.passwordSet", "password")} ${a.passwordSet ? esc(t("admin.yes", "yes")) : esc(t("admin.no", "no"))} / ${t("admin.lastLogin", "last login")} ${a.lastLogin ? esc(formatMessageTimeFull(a.lastLogin)) : t("admin.never", "never")}</small>
                </div>
              </div>
            `).join("") || `<em>${t("admin.none", "none")}</em>`}
          </div>
        </section>
        <section class="kwc-admin-record-section">
          <h4>${t("admin.sessions", "Sessions")}</h4>
          <div class="kwc-admin-list kwc-admin-record-list kwc-admin-session-list">
            ${sessions.map(s => `
              <div class="kwc-admin-item">
                <div>
                  ${directMessageIdentityHtml({displayName: s.displayName || s.username || "", username: s.username || "", uuid: s.uuid || ""}, "kwc-sender")} <strong>${esc(s.role)}</strong><br>
                  <small>${esc(s.lastIp || "")} / ${t("admin.expires", "expires")} ${s.expiresAt ? esc(formatMessageTimeFull(s.expiresAt)) : t("admin.never", "never")}</small>
                </div>
                <button class="kwc-button" data-revoke="${esc(s.username)}">${t("button.revoke", "Revoke")}</button>
              </div>
            `).join("") || `<em>${t("admin.none", "none")}</em>`}
          </div>
        </section>
      </div>
    `;
    installSenderIdentityToggle(content);
    content.querySelectorAll("[data-revoke]").forEach(btn => {
      btn.onclick = async () => {
        if (!confirmPlain(fmt("admin.revokeConfirm", "Revoke all sessions for {username}?", {username: btn.dataset.revoke}))) return;
        const res = await adminWrite("/admin/revoke", {username: btn.dataset.revoke});
        if (!res.ok) alertResponse("alert.failed", "Failed: {error}", res);
        await renderAdminAccounts(content);
      };
    });
  }

  async function renderAdminSessions(content) {
    // Kept as an internal compatibility alias; Sessions is now part of Accounts.
    return renderAdminAccounts(content);
  }


  function clampModalPosition(modal, left, top) {
    const rect = modal.getBoundingClientRect();
    const pad = 8;
    const maxLeft = Math.max(pad, window.innerWidth - rect.width - pad);
    const maxTop = Math.max(pad, window.innerHeight - rect.height - pad);
    return {
      left: Math.max(pad, Math.min(maxLeft, left)),
      top: Math.max(pad, Math.min(maxTop, top))
    };
  }

  function makeModalDraggable(wrap, storageKey) {
    const modal = wrap && wrap.querySelector(".kwc-modal");
    const handle = modal && modal.querySelector("h3");
    if (!modal || !handle) return;

    modal.classList.add("kwc-draggable-modal");
    handle.classList.add("kwc-modal-drag-handle");

    const saved = storageKey ? localStorage.getItem(storageKey) : "";
    if (saved) {
      try {
        const pos = JSON.parse(saved);
        const clamped = clampModalPosition(modal, Number(pos.left) || 0, Number(pos.top) || 0);
        modal.style.position = "absolute";
        modal.style.left = clamped.left + "px";
        modal.style.top = clamped.top + "px";
        modal.style.margin = "0";
        wrap.classList.add("kwc-modal-dragging-ready");
      } catch (_) {}
    }

    let active = false;
    let offsetX = 0;
    let offsetY = 0;

    const point = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return {x: Number(src.clientX) || 0, y: Number(src.clientY) || 0};
    };

    const begin = event => {
      const target = event.target;
      if (target && target.closest && target.closest("button, input, select, textarea")) return;
      const p = point(event);
      const rect = modal.getBoundingClientRect();

      active = true;
      offsetX = p.x - rect.left;
      offsetY = p.y - rect.top;

      modal.style.position = "absolute";
      modal.style.left = rect.left + "px";
      modal.style.top = rect.top + "px";
      modal.style.margin = "0";
      wrap.classList.add("kwc-modal-dragging-ready");

      event.preventDefault();
      event.stopPropagation();
    };

    const move = event => {
      if (!active) return;
      const p = point(event);
      const clamped = clampModalPosition(modal, p.x - offsetX, p.y - offsetY);
      modal.style.left = clamped.left + "px";
      modal.style.top = clamped.top + "px";
      event.preventDefault();
      event.stopPropagation();
    };

    const end = event => {
      if (!active) return;
      active = false;
      if (storageKey) {
        localStorage.setItem(storageKey, JSON.stringify({
          left: parseFloat(modal.style.left) || 0,
          top: parseFloat(modal.style.top) || 0
        }));
      }
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
    };

    handle.addEventListener("pointerdown", begin);
    window.addEventListener("pointermove", move, true);
    window.addEventListener("pointerup", end, true);
    window.addEventListener("pointercancel", end, true);
    handle.addEventListener("touchstart", begin, {passive: false});
    window.addEventListener("touchmove", move, {capture: true, passive: false});
    window.addEventListener("touchend", end, {capture: true, passive: false});
    window.addEventListener("touchcancel", end, {capture: true, passive: false});
  }

  function fontOptionLabel(value) {
    value = String(value || "");
    if (!value) return t("preferences.fontDefault", "Default");
    if (value === "system-ui, sans-serif") return t("preferences.fontSystem", "System");
    return value.replace(/"/g, "");
  }


  function normalizePreferenceLine(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
  }

  function uniquePreferenceLines(lines) {
    const out = [];
    const seen = new Set();
    for (const line of lines || []) {
      const clean = normalizePreferenceLine(line);
      if (!clean) continue;
      const key = clean.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(clean);
    }
    return out;
  }

  function splitLegacyFontHelp(value) {
    const text = normalizePreferenceLine(value);
    if (!text) return {help: "", example: ""};
    const markerRe = /(?:^|\s)(Examples?:\s*|예:\s*|例:\s*|示例[:：]\s*)/i;
    const match = markerRe.exec(text);
    if (!match) return {help: text, example: ""};
    const help = text.slice(0, match.index).trim();
    const example = text.slice(match.index).trim();
    return {help, example};
  }

  function preferencesFontHelpParts(labels = {}) {
    const fallbackHelp = "Find the font family name in your OS font settings.";
    const fallbackExample = "Examples: Malgun Gothic, Noto Sans KR, D2Coding.";
    const rawHelp = labels && labels.fontHelp ? String(labels.fontHelp) : t("preferences.fontHelp", fallbackHelp);
    const split = splitLegacyFontHelp(rawHelp);
    const rawExample = labels && labels.fontExample ? String(labels.fontExample) : t("preferences.fontExample", split.example || fallbackExample);
    const example = split.example || rawExample;
    return uniquePreferenceLines([split.help || rawHelp || fallbackHelp, example || fallbackExample]);
  }

  function splitLegacyPreferenceNote(value) {
    const text = normalizePreferenceLine(value);
    if (!text) return {note: "", drag: ""};
    const dragMarkers = [
      "Drag the title to move this window.",
      "제목을 잡고 이동할 수 있습니다.",
      "タイトルをドラッグして移動できます。",
      "可拖动标题移动此窗口。"
    ];
    let best = -1;
    let marker = "";
    for (const m of dragMarkers) {
      const idx = text.indexOf(m);
      if (idx >= 0 && (best < 0 || idx < best)) {
        best = idx;
        marker = m;
      }
    }
    if (best < 0) return {note: text, drag: ""};
    return {
      note: text.slice(0, best).trim(),
      drag: text.slice(best).trim() || marker
    };
  }

  function stripLegacyBrowserOnlyPreferenceNote(value) {
    let text = normalizePreferenceLine(value);
    const stale = [
      "These settings are stored only in this browser.",
      "This setting is stored only in this browser.",
      "이 설정은 이 브라우저에만 저장됩니다.",
      "この設定はこのブラウザにのみ保存されます。",
      "この設定はこのブラウザーにのみ保存されます。",
      "此设置仅保存在此浏览器中。"
    ];
    for (const line of stale) text = normalizePreferenceLine(text.replace(line, ""));
    return text;
  }

  function preferencesNoteParts(labels = {}) {
    const fallbackNote = "This settings window can be moved by dragging its title.";
    const fallbackDrag = "This settings window can be moved by dragging its title.";
    const rawNote = stripLegacyBrowserOnlyPreferenceNote(labels && labels.note ? String(labels.note) : t("preferences.note", fallbackNote));
    const rawDrag = normalizePreferenceLine(labels && labels.noteDrag ? String(labels.noteDrag) : t("preferences.noteDrag", fallbackDrag));
    if (rawNote && rawDrag && (rawNote === rawDrag || rawNote.includes(rawDrag))) {
      return {note: rawNote, drag: ""};
    }
    const split = splitLegacyPreferenceNote(rawNote);
    const note = split.note || rawNote || fallbackNote;
    const drag = split.drag || rawDrag || "";
    if (drag && (note === drag || note.includes(drag))) return {note, drag: ""};
    return {note, drag};
  }

  function preferencesNoteText(labels = {}) {
    return preferencesNoteParts(labels).note;
  }

  function preferencesNoteDragText(labels = {}) {
    return preferencesNoteParts(labels).drag;
  }

  function preferencesNoteHtml(labels = {}, includeDrag = false, escapeFn = esc) {
    const parts = preferencesNoteParts(labels);
    const lines = [parts.note];
    if (includeDrag && parts.drag) lines.push(parts.drag);
    return uniquePreferenceLines(lines).filter(Boolean).map(line => escapeFn(line)).join("<br>");
  }

  function preferencesFontHelpHtml(labels = {}, escapeFn = esc) {
    return preferencesFontHelpParts(labels).map(line => escapeFn(line)).join("<br>");
  }




  const NOTIFICATION_INBOX_KEY = "kwc.notificationInbox";
  const NOTIFICATION_INBOX_READ_AT_KEY = "kwc.notificationInboxReadAt";

  function readNotificationInbox() {
    try {
      const parsed = JSON.parse(localStorage.getItem(NOTIFICATION_INBOX_KEY) || "[]");
      return Array.isArray(parsed) ? parsed.filter(Boolean).slice(0, 100) : [];
    } catch (_) { return []; }
  }

  function writeNotificationInbox(items) {
    try { localStorage.setItem(NOTIFICATION_INBOX_KEY, JSON.stringify((items || []).slice(0, 100))); } catch (_) {}
  }

  function notificationInboxReadAt() {
    const n = Number(localStorage.getItem(NOTIFICATION_INBOX_READ_AT_KEY) || "0");
    return Number.isFinite(n) ? n : 0;
  }

  function addNotificationInboxItem(item) {
    item = item || {};
    const now = Date.now();
    const entry = {
      id: String(item.id || ("n" + now + "-" + Math.random().toString(36).slice(2, 8))),
      time: Number(item.time || now),
      type: String(item.type || "notification"),
      title: plainNotificationText(item.title || configuredNotificationTitle(), 120),
      body: plainNotificationText(item.body || "", 240),
      messageId: String(item.messageId || ""),
      dmThreadId: String(item.dmThreadId || ""),
      dmMessageId: String(item.dmMessageId || ""),
      groupRoomId: String(item.groupRoomId || ""),
      groupMessageId: String(item.groupMessageId || ""),
      url: String(item.url || ""),
      tag: String(item.tag || "")
    };
    const items = readNotificationInbox().filter(x => !(entry.tag && x && x.tag === entry.tag && Math.abs(Number(x.time || 0) - entry.time) < 1500));
    items.unshift(entry);
    writeNotificationInbox(items);
    updateNotificationInboxButton();
  }

  function updateNotificationInboxButton() {
    const button = document.getElementById("kwc-notifications");
    if (button) {
      const hidden = !!state.minimized;
      button.classList.toggle("kwc-hidden", hidden);
      button.hidden = hidden;
      button.disabled = hidden;
      button.setAttribute("aria-hidden", hidden ? "true" : "false");
    }
    const badge = document.getElementById("kwc-notification-badge");
    if (!badge) return;
    const readAt = notificationInboxReadAt();
    const unread = readNotificationInbox().filter(item => Number(item.time || 0) > readAt).length;
    state.notificationInboxUnread = unread;
    badge.textContent = unread > 99 ? "99+" : String(unread);
    badge.classList.toggle("kwc-hidden", unread <= 0);
  }

  function openNotificationInboxModal() {
    const existing = document.querySelector(".kwc-notification-inbox-backdrop");
    if (existing) existing.remove();
    localStorage.setItem(NOTIFICATION_INBOX_READ_AT_KEY, String(Date.now()));
    updateNotificationInboxButton();
    const items = readNotificationInbox();
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop kwc-notification-inbox-backdrop";
    applyDetachedModalTheme(wrap);
    const rows = items.length ? items.map(item => `
      <button type="button" class="kwc-notification-row" data-message-id="${esc(item.messageId || "")}" data-dm-thread-id="${esc(item.dmThreadId || "")}" data-dm-message-id="${esc(item.dmMessageId || "")}" data-group-room-id="${esc(item.groupRoomId || "")}" data-group-message-id="${esc(item.groupMessageId || "")}" data-url="${esc(item.url || "")}">
        <span class="kwc-notification-row-title">${esc(item.title || configuredNotificationTitle())}</span>
        ${item.body ? `<span class="kwc-notification-row-body">${esc(item.body)}</span>` : ""}
        <span class="kwc-notification-row-time">${esc(formatMessageTime(Number(item.time || Date.now())))}</span>
      </button>
    `).join("") : `<div class="kwc-dm-empty">${esc(t("notifications.empty", "No missed notifications."))}</div>`;
    wrap.innerHTML = `
      <div class="kwc-modal kwc-notification-inbox-modal">
        <div class="kwc-modal-head"><h3>${esc(t("notifications.inbox", "Notification inbox"))}</h3><button class="kwc-button" id="kwc-notification-close">${esc(t("button.close", "Close"))}</button></div>
        <div class="kwc-notification-list">${rows}</div>
        <div class="kwc-notification-actions"><button class="kwc-button" id="kwc-notification-clear">${esc(t("notifications.clear", "Clear notifications"))}</button></div>
      </div>
    `;
    document.body.appendChild(wrap);
    wrap.querySelector("#kwc-notification-close").addEventListener("click", () => wrap.remove());
    wrap.querySelector("#kwc-notification-clear").addEventListener("click", () => {
      writeNotificationInbox([]);
      updateNotificationInboxButton();
      wrap.remove();
    });
    wrap.querySelectorAll("[data-message-id]").forEach(btn => {
      btn.addEventListener("click", () => {
        const nav = {
          messageId: btn.dataset.messageId || "",
          dmThreadId: btn.dataset.dmThreadId || "",
          dmMessageId: btn.dataset.dmMessageId || "",
          groupRoomId: btn.dataset.groupRoomId || "",
          groupMessageId: btn.dataset.groupMessageId || "",
          url: btn.dataset.url || ""
        };
        wrap.remove();
        navigateFromNotification(nav);
      });
    });
  }

  function cssEscapeValue(value) {
    const text = String(value || "");
    if (window.CSS && typeof window.CSS.escape === "function") return window.CSS.escape(text);
    return text.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  }

  function parseNotificationNavigation(value) {
    const nav = {};
    if (value && typeof value === "object") {
      nav.messageId = String(value.messageId || "");
      nav.dmThreadId = String(value.dmThreadId || "");
      nav.dmMessageId = String(value.dmMessageId || "");
      nav.groupRoomId = String(value.groupRoomId || "");
      nav.groupMessageId = String(value.groupMessageId || "");
      value = value.url || "";
    }
    try {
      const url = new URL(String(value || window.location.href), window.location.href);
      nav.messageId = nav.messageId || url.searchParams.get("kwcMessage") || url.searchParams.get("bmwcMessage") || "";
      nav.dmThreadId = nav.dmThreadId || url.searchParams.get("kwcDmThread") || url.searchParams.get("bmwcDmThread") || "";
      nav.dmMessageId = nav.dmMessageId || url.searchParams.get("kwcDmMessage") || url.searchParams.get("bmwcDmMessage") || "";
      nav.groupRoomId = nav.groupRoomId || url.searchParams.get("kwcGroupRoom") || url.searchParams.get("bmwcGroupRoom") || "";
      nav.groupMessageId = nav.groupMessageId || url.searchParams.get("kwcGroupMessage") || url.searchParams.get("bmwcGroupMessage") || "";
    } catch (_) {}
    return nav;
  }

  function clearNotificationNavigationParams() {
    try {
      const url = new URL(window.location.href);
      ["kwcMessage", "kwcDmThread", "kwcDmMessage", "kwcGroupRoom", "kwcGroupMessage", "bmwcMessage", "bmwcDmThread", "bmwcDmMessage", "bmwcGroupRoom", "bmwcGroupMessage"].forEach(k => url.searchParams.delete(k));
      window.history.replaceState(window.history.state, document.title, url.pathname + url.search + url.hash);
    } catch (_) {}
  }

  async function centerPrivateMessage(box, selector) {
    if (!box || !selector) return false;
    const el = box.querySelector(selector);
    if (!el) return false;
    try { el.scrollIntoView({block: "center", behavior: "smooth"}); } catch (_) { try { el.scrollIntoView({block: "center"}); } catch (__) {} }
    try { highlightMessageElement(el); } catch (_) {
      el.classList.add("kwc-reply-highlight");
      setTimeout(() => { try { el.classList.remove("kwc-reply-highlight"); } catch (__) {} }, 2600);
    }
    return true;
  }

  async function openDirectMessageNavigation(threadId, messageId) {
    threadId = String(threadId || "").trim();
    messageId = String(messageId || "").trim();
    if (!threadId) return false;
    if (!state.dmModalOpen) await openDirectMessageModal();
    if (!state.dmModalOpen) return false;
    await loadDirectMessageThreads(true);
    state.dmDraftTarget = null;
    state.dmActiveThreadId = threadId;
    renderDirectMessageThreads();
    updateDirectMessageViewMode();
    await loadDirectMessageMessages(threadId);
    if (!messageId || messageId === "0") return true;
    const box = document.getElementById("kwc-dm-messages");
    const selector = `[data-dm-message-id="${cssEscapeValue(messageId)}"]`;
    for (let i = 0; i < 20; i++) {
      if (await centerPrivateMessage(box, selector)) return true;
      if (!state.dmMessagesHasMore) break;
      const loaded = await loadOlderDirectMessageMessagesFromEdge(box, "notification-click");
      if (!loaded) break;
    }
    return false;
  }

  async function openGroupMessageNavigation(roomId, messageId) {
    roomId = String(roomId || "").trim();
    messageId = String(messageId || "").trim();
    if (!roomId) return false;
    if (!state.groupModalOpen) await openGroupChatModal();
    if (!state.groupModalOpen) return false;
    await loadGroupChatRooms(true);
    await openGroupRoom(roomId);
    if (!messageId || messageId === "0") return true;
    const box = document.getElementById("kwc-group-messages");
    const selector = `[data-group-message-id="${cssEscapeValue(messageId)}"]`;
    for (let i = 0; i < 20; i++) {
      if (await centerPrivateMessage(box, selector)) return true;
      if (!state.groupMessagesHasMore) break;
      const loaded = await loadOlderGroupChatMessagesFromEdge(box, "notification-click");
      if (!loaded) break;
    }
    return false;
  }

  async function navigateFromNotification(value) {
    const nav = parseNotificationNavigation(value);
    if (nav.dmThreadId) { await openDirectMessageNavigation(nav.dmThreadId, nav.dmMessageId); clearNotificationNavigationParams(); return; }
    if (nav.groupRoomId) { await openGroupMessageNavigation(nav.groupRoomId, nav.groupMessageId); clearNotificationNavigationParams(); return; }
    if (nav.messageId) { jumpToReplyTarget(nav.messageId); clearNotificationNavigationParams(); return; }
  }

  function notificationNavigationUrl(options = {}) {
    try {
      const url = new URL(window.location.href);
      ["kwcMessage", "kwcDmThread", "kwcDmMessage", "kwcGroupRoom", "kwcGroupMessage", "bmwcMessage", "bmwcDmThread", "bmwcDmMessage", "bmwcGroupRoom", "bmwcGroupMessage"].forEach(k => url.searchParams.delete(k));
      if (options.messageId) url.searchParams.set("kwcMessage", String(options.messageId));
      if (options.dmThreadId) url.searchParams.set("kwcDmThread", String(options.dmThreadId));
      if (options.dmMessageId) url.searchParams.set("kwcDmMessage", String(options.dmMessageId));
      if (options.groupRoomId) url.searchParams.set("kwcGroupRoom", String(options.groupRoomId));
      if (options.groupMessageId) url.searchParams.set("kwcGroupMessage", String(options.groupMessageId));
      return url.href;
    } catch (_) { return ""; }
  }


  function notificationApiSupported() {
    return typeof window !== "undefined" && "Notification" in window;
  }

  const NOTIFICATION_ENABLED_KEY = "kwc.notify.enabled";
  const LEGACY_NOTIFICATIONS_ENABLED_KEY = "kwc.notifications.enabled";
  const LEGACY_WEB_PUSH_ENABLED_KEY = "kwc.webPush.enabled";

  function readStorageValue(key) {
    try { return localStorage.getItem(key); } catch (_) { return null; }
  }

  function writeStorageValue(key, value) {
    try {
      if (value === null || value === undefined) localStorage.removeItem(key);
      else localStorage.setItem(key, String(value));
    } catch (_) {}
  }

  function migrateNotificationEnabledStorage() {
    const current = readStorageValue(NOTIFICATION_ENABLED_KEY);
    if (current === "1" || current === "0") return current === "1";
    const legacyPage = readStorageValue(LEGACY_NOTIFICATIONS_ENABLED_KEY);
    const legacyPush = readStorageValue(LEGACY_WEB_PUSH_ENABLED_KEY);
    let enabled = false;
    if (legacyPage === "1" || legacyPush === "1") enabled = true;
    else if (legacyPage === "0" || legacyPush === "0") enabled = false;
    writeStorageValue(NOTIFICATION_ENABLED_KEY, enabled ? "1" : "0");
    return enabled;
  }

  function notificationsEnabledLocal() {
    return migrateNotificationEnabledStorage();
  }

  function setNotificationsEnabledLocal(enabled, options = {}) {
    const on = enabled === true;
    writeStorageValue(NOTIFICATION_ENABLED_KEY, on ? "1" : "0");
    // Legacy keys are migration/compatibility inputs only. Remove them after the
    // unified key is written so the settings UI has a single source of truth.
    if (options.keepLegacy !== true) {
      writeStorageValue(LEGACY_NOTIFICATIONS_ENABLED_KEY, null);
      writeStorageValue(LEGACY_WEB_PUSH_ENABLED_KEY, null);
    }
  }

  function readLegacyNotificationEnabledFromStorage(storage) {
    if (!storage || typeof storage !== "object") return null;
    if (Object.prototype.hasOwnProperty.call(storage, NOTIFICATION_ENABLED_KEY)) {
      const value = storage[NOTIFICATION_ENABLED_KEY];
      if (value === "1" || value === 1 || value === true) return true;
      if (value === "0" || value === 0 || value === false) return false;
    }
    let seen = false;
    let enabled = false;
    [LEGACY_NOTIFICATIONS_ENABLED_KEY, LEGACY_WEB_PUSH_ENABLED_KEY].forEach(key => {
      if (!Object.prototype.hasOwnProperty.call(storage, key)) return;
      seen = true;
      const value = storage[key];
      if (value === "1" || value === 1 || value === true) enabled = true;
    });
    return seen ? enabled : null;
  }

  const WEB_PUSH_DEVICE_ID_KEY = "kwc.webPush.deviceId";
  let webPushDeviceIdMemory = "";

  function webPushDeviceId() {
    if (webPushDeviceIdMemory) return webPushDeviceIdMemory;
    const stored = String(readStorageValue(WEB_PUSH_DEVICE_ID_KEY) || "").trim();
    if (/^[A-Za-z0-9_-]{12,96}$/.test(stored)) {
      webPushDeviceIdMemory = stored;
      return stored;
    }
    let generated = "";
    try {
      if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
        generated = "d_" + crypto.randomUUID().replace(/-/g, "");
      } else if (typeof crypto !== "undefined" && typeof crypto.getRandomValues === "function") {
        const bytes = crypto.getRandomValues(new Uint8Array(16));
        generated = "d_" + Array.from(bytes, v => v.toString(16).padStart(2, "0")).join("");
      }
    } catch (_) {}
    if (!generated) generated = "d_" + Date.now().toString(36) + Math.random().toString(36).slice(2, 18);
    webPushDeviceIdMemory = generated.slice(0, 96);
    writeStorageValue(WEB_PUSH_DEVICE_ID_KEY, webPushDeviceIdMemory);
    return webPushDeviceIdMemory;
  }

  const NOTIFICATION_OPTION_DEFS = [
    {name: "normalChat", key: "kwc.notify.normalChat", label: "notifyNormalChat", fallback: () => notificationServerDefault("normalChat")},
    {name: "dm", key: "kwc.notify.dm", label: "notifyDm", fallback: () => notificationServerDefault("dm")},
    {name: "groupChat", key: "kwc.notify.groupChat", label: "notifyGroupChat", fallback: () => notificationServerDefault("groupChat")},
    {name: "mentions", key: "kwc.notify.mentions", label: "notifyMentions", fallback: () => notificationServerDefault("mentions")},
    {name: "replies", key: "kwc.notify.replies", label: "notifyReplies", fallback: () => notificationServerDefault("replies")},
    {name: "system", key: "kwc.notify.system", label: "notifySystem", fallback: () => notificationServerDefault("system")},
    {name: "keywords", key: "kwc.notify.keywords", label: "notifyKeywords", fallback: () => notificationServerDefault("keywords")},
    {name: "preview", key: "kwc.notify.preview", label: "notifyPreview", fallback: () => notificationServerDefault("preview")}
  ];

  function browserNotificationServerAllows(name) {
    if (name === "normalChat") return state.browserNotificationsNotifyNormalChat !== false;
    if (name === "dm") return state.browserNotificationsNotifyDm !== false;
    if (name === "groupChat") return state.browserNotificationsNotifyGroupChat !== false;
    if (name === "mentions") return state.browserNotificationsNotifyMentions !== false;
    if (name === "replies") return state.browserNotificationsNotifyReplies !== false;
    if (name === "system") return state.browserNotificationsNotifySystem !== false;
    if (name === "keywords") return state.browserNotificationsNotifyKeywords !== false;
    if (name === "preview") return state.browserNotificationsShowMessagePreview !== false;
    return true;
  }

  function webPushServerAllows(name) {
    if (name === "normalChat") return state.webPushNotifyNormalChat !== false;
    if (name === "dm") return state.webPushNotifyDm !== false;
    if (name === "groupChat") return state.webPushNotifyGroupChat !== false;
    if (name === "mentions") return state.webPushNotifyMentions !== false;
    if (name === "replies") return state.webPushNotifyReplies !== false;
    if (name === "system") return state.webPushNotifySystem !== false;
    if (name === "keywords") return state.webPushNotifyKeywords !== false;
    if (name === "preview") return state.webPushShowMessagePreview !== false;
    return true;
  }

  function notificationServerAllows(name) {
    return (state.browserNotificationsEnabled && browserNotificationServerAllows(name)) || (state.webPushEnabled && webPushServerAllows(name));
  }

  function notificationServerDefault(name) {
    return notificationServerAllows(name);
  }

  function browserNotificationOption(name) {
    return browserNotificationServerAllows(name) && notificationOption(name);
  }

  function readStoredBool(key, fallback) {
    try {
      const value = localStorage.getItem(key);
      if (value === "1") return true;
      if (value === "0") return false;
    } catch (_) {}
    return !!fallback;
  }

  function writeStoredBool(key, value) {
    try { localStorage.setItem(key, value ? "1" : "0"); } catch (_) {}
  }

  function notificationOptionDef(name) {
    return NOTIFICATION_OPTION_DEFS.find(def => def.name === name) || null;
  }

  function notificationOption(name) {
    const def = notificationOptionDef(name);
    if (!def) return false;
    if (!notificationServerAllows(name)) return false;
    return readStoredBool(def.key, def.fallback());
  }

  const NOTIFICATION_SYSTEM_MODE_KEY = "kwc.notify.systemMode";

  function normalizeNotificationSystemMode(value, fallback = "all") {
    const v = String(value || "").trim().toLowerCase().replace(/_/g, "-");
    if (v === "all" || v === "join-leave" || v === "off") return v;
    const f = String(fallback || "all").trim().toLowerCase().replace(/_/g, "-");
    return (f === "all" || f === "join-leave" || f === "off") ? f : "all";
  }

  function notificationSystemMode() {
    if (!notificationServerAllows("system")) return "off";
    try {
      const stored = localStorage.getItem(NOTIFICATION_SYSTEM_MODE_KEY);
      if (stored !== null) return normalizeNotificationSystemMode(stored, "all");
    } catch (_) {}
    const def = notificationOptionDef("system");
    return readStoredBool(def && def.key || "kwc.notify.system", notificationServerDefault("system")) ? "all" : "off";
  }

  function setNotificationSystemMode(mode) {
    const value = notificationServerAllows("system") ? normalizeNotificationSystemMode(mode, "all") : "off";
    try { localStorage.setItem(NOTIFICATION_SYSTEM_MODE_KEY, value); } catch (_) {}
    const def = notificationOptionDef("system");
    if (def) writeStoredBool(def.key, value !== "off");
    scheduleAccountNotificationPreferencesSave();
  }

  function isJoinLeaveSystemMessage(msg) {
    if (!msg) return false;
    const key = String(msg.i18nKey || "").trim().toLowerCase();
    return key.endsWith("minecraft-join") || key.endsWith("minecraft-quit") || key.endsWith("first-join");
  }

  function systemNotificationAllowedForMessage(msg) {
    const mode = notificationSystemMode();
    if (mode === "off") return false;
    if (mode === "join-leave") return isJoinLeaveSystemMessage(msg);
    return true;
  }

  function setNotificationOption(name, value) {
    if (name === "system") { setNotificationSystemMode(value ? "all" : "off"); return; }
    const def = notificationOptionDef(name);
    if (!def) return;
    if (!notificationServerAllows(name)) {
      writeStoredBool(def.key, false);
      return;
    }
    writeStoredBool(def.key, !!value);
    scheduleAccountNotificationPreferencesSave();
  }

  function currentNotificationOptions() {
    const out = {};
    NOTIFICATION_OPTION_DEFS.forEach(def => { out[def.name] = def.name === "system" ? notificationSystemMode() !== "off" : notificationOption(def.name); });
    out.systemMode = notificationSystemMode();
    return out;
  }

  function currentNotificationOptionsAllowed() {
    const out = {};
    NOTIFICATION_OPTION_DEFS.forEach(def => { out[def.name] = notificationServerAllows(def.name); });
    return out;
  }

  const NOTIFICATION_KEYWORDS_KEY = "kwc.notify.keywords.list";
  const LEGACY_NOTIFICATION_KEYWORDS_KEY = "kwc.notify.keywords.text";
  const isPollutedNotificationKeywordText = value => /^(?:on|off|true|false|1|0)$/i.test(String(value || "").trim());

  function notificationKeywordsText() {
    try {
      const current = localStorage.getItem(NOTIFICATION_KEYWORDS_KEY);
      if (current !== null) return isPollutedNotificationKeywordText(current) ? "" : current;
      const legacy = localStorage.getItem(LEGACY_NOTIFICATION_KEYWORDS_KEY);
      if (legacy !== null && !isPollutedNotificationKeywordText(legacy)) {
        localStorage.setItem(NOTIFICATION_KEYWORDS_KEY, legacy);
        return legacy;
      }
      if (isPollutedNotificationKeywordText(legacy)) localStorage.removeItem(LEGACY_NOTIFICATION_KEYWORDS_KEY);
      return "";
    } catch (_) { return ""; }
  }

  function setNotificationKeywordsText(value) {
    const text = isPollutedNotificationKeywordText(value) ? "" : String(value || "");
    try {
      localStorage.setItem(NOTIFICATION_KEYWORDS_KEY, text);
      if (isPollutedNotificationKeywordText(localStorage.getItem(LEGACY_NOTIFICATION_KEYWORDS_KEY))) {
        localStorage.removeItem(LEGACY_NOTIFICATION_KEYWORDS_KEY);
      }
    } catch (_) {}
    scheduleAccountNotificationPreferencesSave();
  }

  function notificationKeywords() {
    const seen = new Set();
    const out = [];
    String(notificationKeywordsText() || "").split(/[\r\n,]+/).forEach(part => {
      const keyword = String(part || "").replace(/[\u0000-\u001f]/g, "").trim();
      if (!keyword) return;
      const lower = keyword.toLowerCase();
      if (seen.has(lower)) return;
      seen.add(lower);
      out.push(keyword.slice(0, 80));
    });
    return out.slice(0, 40);
  }

  function notificationKeywordMatch(value) {
    if (!browserNotificationOption("keywords") && !notificationOption("keywords")) return "";
    const haystack = plainNotificationText(value, 0).toLowerCase();
    for (const keyword of notificationKeywords()) {
      if (haystack.includes(String(keyword).toLowerCase())) return keyword;
    }
    return "";
  }

  function keywordNotificationTitle(keyword) {
    return fmt("preferences.keywordNotificationTitle", "Keyword: {keyword}", {keyword});
  }

  function configuredNotificationTitle() {
    const title = String(state.webPushNotificationTitle || (state.config && state.config.webPushNotificationTitle) || "").trim();
    if (title) return title;
    const appName = String(state.standaloneWebAppName || (state.config && state.config.standaloneWebAppName) || "").trim();
    return appName || "Web Chat";
  }


  function notifyLocaleShortLabel(kind) {
    const navLang = (typeof navigator !== "undefined" && navigator.language) ? navigator.language : "";
    const lang = String(state.selectedLanguage || navLang || "").toLowerCase();
    const ko = lang.startsWith("ko");
    const ja = lang.startsWith("ja");
    const zh = lang.startsWith("zh");
    if (kind === "server") return ko ? "서버" : ja ? "サーバー" : zh ? "服务器" : "Server";
    if (kind === "all") return ko ? "전체" : ja ? "すべて" : zh ? "全部" : "All";
    if (kind === "joinLeave") return ko ? "입장/퇴장만" : ja ? "参加/退出のみ" : zh ? "仅加入/退出" : "Join/leave only";
    if (kind === "off") return ko ? "끄기" : ja ? "オフ" : zh ? "关闭" : "Off";
    return "";
  }

  function normalizeNotifySystemLabel(value) {
    const s = String(value || "").trim();
    if (!s) return notifyLocaleShortLabel("server");
    if (/시스템\s*[\/]\s*서버/i.test(s) || /system\s*[\/]\s*server/i.test(s)) return notifyLocaleShortLabel("server");
    return s;
  }

  function normalizeNotifySystemModeLabel(mode, value) {
    const s = String(value || "").trim();
    const hasServerWord = /시스템|서버|system|server|サーバー|服务器/i.test(s);
    if (mode === "all") {
      if (!s || hasServerWord || /^(?:all|전체|すべて|全部)$/i.test(s)) return notifyLocaleShortLabel("all");
      return s;
    }
    if (mode === "join-leave") {
      if (!s || hasServerWord) return notifyLocaleShortLabel("joinLeave");
      return s;
    }
    if (mode === "off") {
      if (!s || hasServerWord) return notifyLocaleShortLabel("off");
      return s;
    }
    return s;
  }

  function notificationOptionsHtml(prefix, labels = {}) {
    const row = (name, fallback) => {
      const def = notificationOptionDef(name);
      const allowed = notificationServerAllows(name);
      const checked = notificationOption(name) ? " checked" : "";
      const disabled = allowed ? "" : " disabled";
      const title = allowed ? "" : ` title="${esc(labels.notifyDisabledByServer || "Disabled by server configuration.")}"`;
      const text = labels[def && def.label] || fallback;
      return `<label class="kwc-notify-option${allowed ? "" : " kwc-notify-option-disabled"}"${title}><input id="${prefix}-${name}" type="checkbox" data-kwc-notify-option="${name}"${checked}${disabled}> <span>${esc(text)}</span></label>`;
    };
    const systemAllowed = notificationServerAllows("system");
    const systemMode = notificationSystemMode();
    const systemTitle = systemAllowed ? "" : ` title="${esc(labels.notifyDisabledByServer || "Disabled by server configuration.")}"`;
    const systemLabel = normalizeNotifySystemLabel(labels.notifySystem || "");
    const systemAllLabel = normalizeNotifySystemModeLabel("all", labels.notifySystemAll || "");
    const systemJoinLeaveLabel = normalizeNotifySystemModeLabel("join-leave", labels.notifySystemJoinLeave || "");
    const systemOffLabel = normalizeNotifySystemModeLabel("off", labels.notifySystemOff || "");
    const systemSelect = `<label class="kwc-notify-option${systemAllowed ? "" : " kwc-notify-option-disabled"}"${systemTitle}><span>${esc(systemLabel)}</span><select id="${prefix}-system-mode" data-kwc-notify-system-mode ${systemAllowed ? "" : "disabled"}><option value="all"${systemMode === "all" ? " selected" : ""}>${esc(systemAllLabel)}</option><option value="join-leave"${systemMode === "join-leave" ? " selected" : ""}>${esc(systemJoinLeaveLabel)}</option><option value="off"${systemMode === "off" ? " selected" : ""}>${esc(systemOffLabel)}</option></select></label>`;
    return `<div class="kwc-notify-options">
      ${row("normalChat", "Normal chat")}
      ${row("dm", "DM")}
      ${row("groupChat", "Group chat")}
      ${row("mentions", "Mentions")}
      ${row("replies", "Replies")}
      ${systemSelect}
      ${row("keywords", "Keyword alerts")}
      ${row("preview", "Message preview")}
    </div>`;
  }

  function bindNotificationOptionInputs(container, onChange) {
    if (!container) return;
    container.querySelectorAll("[data-kwc-notify-system-mode]").forEach(select => {
      if (select.disabled) { select.value = "off"; return; }
      select.addEventListener("change", () => {
        setNotificationSystemMode(select.value);
        if (typeof onChange === "function") onChange(currentNotificationOptions());
      });
    });
    container.querySelectorAll("[data-kwc-notify-option]").forEach(input => {
      if (input.disabled) { input.checked = false; return; }
      input.addEventListener("change", () => {
        setNotificationOption(input.dataset.kwcNotifyOption, input.checked);
        if (typeof onChange === "function") onChange(currentNotificationOptions());
      });
    });
  }

  function prefStatusLabel(labels, key, fallback) {
    return labels && labels[key] ? String(labels[key]) : t("preferences." + key, fallback);
  }

  function notificationStatusText(labels = {}) {
    if (!state.browserNotificationsEnabled) return prefStatusLabel(labels, "notificationsServerDisabled", "Notifications are disabled by server configuration.");
    if (!notificationApiSupported()) return prefStatusLabel(labels, "notificationsUnsupported", "This browser does not support notifications.");
    if (Notification.permission === "denied") return prefStatusLabel(labels, "notificationsPermissionDenied", "Notification permission is blocked in this browser.");
    if (notificationsEnabledLocal() && Notification.permission === "granted") return prefStatusLabel(labels, "notificationsEnabledStatus", "Enabled in this browser.");
    if (Notification.permission === "granted") return prefStatusLabel(labels, "notificationsAllowedDisabledStatus", "Allowed by browser, disabled in chat settings.");
    return prefStatusLabel(labels, "notificationsNotRequestedStatus", "Permission is not requested yet.");
  }

  function webPushIsIosLike() {
    try {
      const ua = String(navigator.userAgent || "");
      const platform = String(navigator.platform || "");
      return /iPad|iPhone|iPod/i.test(ua) || (platform === "MacIntel" && Number(navigator.maxTouchPoints || 0) > 1);
    } catch (_) {
      return false;
    }
  }

  function webPushIsInstalledWebApp() {
    try { if (state.isStandalone) return true; } catch (_) {}
    try { if (navigator.standalone === true) return true; } catch (_) {}
    try { if (window.matchMedia && window.matchMedia("(display-mode: standalone)").matches) return true; } catch (_) {}
    try { if (window.matchMedia && window.matchMedia("(display-mode: fullscreen)").matches) return true; } catch (_) {}
    return false;
  }

  function webPushRequiresInstalledWebApp() {
    // Android can use the background Web Push path directly. iOS/iPadOS Web Push
    // is the special case: it must run as an installed Home Screen web app.
    // Desktop clients intentionally use the foreground Notification API instead.
    return webPushIsIosLike() && !webPushIsInstalledWebApp();
  }

  function webPushUnavailableReason(labels = {}) {
    // Document PiP mirrors the original page's live connection and notification
    // owner. Never attempt Service Worker / Push registration in the PiP document.
    if (state.isPip) return prefStatusLabel(labels, "webPushUnsupported", "Web Push is not available in this server configuration.");
    if (!state.webPushEnabled) return prefStatusLabel(labels, "webPushServerDisabled", "Web Push is disabled by server configuration.");
    if (!state.webPushAvailable || !state.webPushVapidPublicKey) return prefStatusLabel(labels, "webPushUnsupported", "Web Push is not available in this server configuration.");
    if (!state.token) return t("error.not_logged_in", "Not logged in.");
    if (webPushRequiresInstalledWebApp()) return prefStatusLabel(labels, "webPushStandaloneRequired", "On iOS/iPadOS, add this chat page to the Home Screen and open it as a web app to use mobile/background push.");
    if (typeof window !== "undefined" && window.isSecureContext === false) return prefStatusLabel(labels, "webPushInsecure", "Web Push requires HTTPS or localhost.");
    if (!("serviceWorker" in navigator)) return prefStatusLabel(labels, "webPushNoServiceWorker", "This browser does not support Service Worker.");
    if (!("PushManager" in window)) return prefStatusLabel(labels, "webPushNoPushManager", "This browser does not support Push API.");
    if (!notificationApiSupported()) return prefStatusLabel(labels, "notificationsUnsupported", "This browser does not support notifications.");
    if (Notification.permission === "denied") return prefStatusLabel(labels, "notificationsPermissionDenied", "Notification permission is blocked in this browser.");
    return "";
  }

  function canUseWebPush() {
    return !webPushUnavailableReason({});
  }

  function webPushStatusText(labels = {}) {
    if (state.webPushLastError) return state.webPushLastError;
    const reason = webPushUnavailableReason(labels);
    if (reason) return reason;
    return notificationsEnabledLocal() ? prefStatusLabel(labels, "webPushEnabledStatus", "Enabled in this browser.") : prefStatusLabel(labels, "webPushDisabledStatus", "Disabled in this browser.");
  }

  function notificationUsesMobilePushUi() {
    try {
      // Web Push is the background/mobile delivery path. Do not infer "mobile"
      // from touch capability alone: Windows touch PCs and 2-in-1 devices often
      // expose multiple coarse touch points but should keep the desktop
      // Notification API path.
      if (webPushIsIosLike()) return true;
      const ua = String(navigator.userAgent || "");
      if (/Android/i.test(ua)) return true;
      try {
        if (navigator.userAgentData && typeof navigator.userAgentData.mobile === "boolean") {
          return navigator.userAgentData.mobile === true;
        }
      } catch (_) {}
      if (/Windows NT|Macintosh|CrOS|X11|Linux/i.test(ua)) return false;
      return /Mobile|Phone|Tablet/i.test(ua);
    } catch (_) { return false; }
  }

  function unifiedNotificationStatusText(labels = {}) {
    return notificationUsesMobilePushUi() ? webPushStatusText(labels) : notificationStatusText(labels);
  }

  function unifiedNotificationHelpText(labels = {}) {
    return notificationUsesMobilePushUi()
      ? prefStatusLabel(labels, "webPushHelp", "Mobile push requires mobile push to be enabled and notification permission to be allowed.")
      : prefStatusLabel(labels, "notificationsPageHelp", "Browser notifications are available only in supported browsers.");
  }

  async function requestBrowserNotifications() {
    if (!state.browserNotificationsEnabled || !notificationApiSupported()) return false;
    if (Notification.permission === "granted") return true;
    if (Notification.permission === "denied") return false;
    try {
      const result = await Notification.requestPermission();
      return result === "granted";
    } catch (_) {
      return false;
    }
  }

  function attentionNeededForNotification(force = false) {
    if (force) return true;
    if (!state.browserNotificationsOnlyWhenHidden) return true;
    return document.hidden || state.minimized || !document.hasFocus();
  }

  function showBrowserNotification(title, body, options = {}) {
    if (!state.browserNotificationsEnabled || !notificationsEnabledLocal()) return false;
    const finalTitle = title || configuredNotificationTitle();
    const finalBody = String(body || "");
    const navUrl = options.url || notificationNavigationUrl(options);
    if (options.store !== false) addNotificationInboxItem({title: finalTitle, body: finalBody, type: options.type || "notification", tag: options.tag || "", messageId: options.messageId || "", dmThreadId: options.dmThreadId || "", dmMessageId: options.dmMessageId || "", groupRoomId: options.groupRoomId || "", groupMessageId: options.groupMessageId || "", url: navUrl || ""});
    // When this browser has an active Web Push subscription, the push service is
    // the single OS-notification path. Keep the in-chat inbox entry but suppress
    // the page Notification API copy so the same keyword/message cannot ring twice.
    if (state.webPushSubscriptionActive && options.force !== true) return false;
    if (!attentionNeededForNotification(options.force === true)) return false;
    const visibleBody = browserNotificationOption("preview") ? String(body || "") : "";
    if (window.parent && window.parent !== window && !state.isPip) {
      postFrame("showNotification", {
        title: finalTitle,
        body: visibleBody,
        tag: options.tag || "kwc",
        force: options.force === true,
        url: navUrl || "",
        messageId: options.messageId || "",
        dmThreadId: options.dmThreadId || "",
        dmMessageId: options.dmMessageId || "",
        groupRoomId: options.groupRoomId || "",
        groupMessageId: options.groupMessageId || ""
      });
      return true;
    }
    if (!notificationApiSupported() || Notification.permission !== "granted") return false;
    try {
      const n = new Notification(finalTitle, {
        body: visibleBody,
        tag: options.tag || "kwc",
        renotify: true,
        silent: false
      });
      n.onclick = () => {
        try { window.focus(); } catch (_) {}
        try { navigateFromNotification({url: navUrl, messageId: options.messageId || "", dmThreadId: options.dmThreadId || "", dmMessageId: options.dmMessageId || "", groupRoomId: options.groupRoomId || "", groupMessageId: options.groupMessageId || ""}); } catch (_) {}
        try { n.close(); } catch (_) {}
      };
      return true;
    } catch (_) {
      return false;
    }
  }

  function plainNotificationText(value, limit = 180) {
    let text = String(value || "");
    text = text.replace(/[§&]x(?:[§&][0-9a-fA-F]){6}/g, "");
    text = text.replace(/[§&][0-9a-fA-Fk-oK-OrR]/g, "");
    text = text.replace(/<[^>]+>/g, "");
    text = text.replace(/\s+/g, " ").trim();
    if (limit > 0 && text.length > limit) text = text.slice(0, Math.max(0, limit - 1)) + "…";
    return text;
  }

  function currentUserMatchesMessage(msg) {
    if (!msg) return false;
    const username = String(state.username || "").toLowerCase();
    const senderUsername = String(msg.senderUsername || msg.realSender || msg.sender || "").toLowerCase();
    if (username && senderUsername && username === senderUsername) return true;
    return false;
  }

  function messageMentionsCurrentUser(text) {
    const name = String(state.username || "").trim();
    if (!name) return false;
    const body = String(text || "").toLowerCase();
    const lower = name.toLowerCase();
    return body.includes("@" + lower) || body.includes(lower);
  }

  function messageRepliesToCurrentUser(msg) {
    if (!msg || !msg.replyToId) return false;
    const target = messageById(msg.replyToId);
    if (target && currentUserMatchesMessage(target)) return true;
    const replySender = plainMinecraftName(String(msg.replyToSender || "")).trim().toLowerCase();
    const username = plainMinecraftName(String(state.username || "")).trim().toLowerCase();
    if (replySender && username && replySender === username) return true;
    return false;
  }

  function replyNotificationTitle(sender) {
    return fmt("notifications.replyTitle", "Reply from {sender}", {sender: sender || configuredNotificationTitle()});
  }

  function maybeNotifyChatMessage(msg) {
    if (!msg) return;
    const own = currentUserMatchesMessage(msg);
    if (own) return;
    const source = String(msg.source || "").toLowerCase();
    const system = source === "event" || source === "system" || source === "server";
    const sender = plainNotificationText(msg.sender || configuredNotificationTitle(), 80);
    const body = plainNotificationText(displayMessageText(msg) || msg.message || "", 180);
    const systemAllowed = !system || systemNotificationAllowedForMessage(msg);
    const keyword = systemAllowed ? notificationKeywordMatch(sender + " " + body) : "";
    if (keyword && browserNotificationOption("keywords")) {
      showBrowserNotification(keywordNotificationTitle(keyword), (system ? configuredNotificationTitle() : sender) + (body ? ": " + body : ""), {tag: "kwc-keyword-" + keyword, type: "keyword", messageId: msg.id || ""});
      return;
    }
    const replyToMe = messageRepliesToCurrentUser(msg);
    if (!system && replyToMe && browserNotificationOption("replies")) {
      showBrowserNotification(replyNotificationTitle(sender), body, {tag: "kwc-reply-" + String(msg.replyToId || msg.id || ""), type: "reply", messageId: msg.id || ""});
      return;
    }
    const mention = messageMentionsCurrentUser(msg.message || "");
    if (system) {
      if (!systemAllowed) return;
    } else if (mention) {
      if (!browserNotificationOption("mentions")) return;
    } else {
      if (!browserNotificationOption("normalChat")) return;
    }
    showBrowserNotification(system ? configuredNotificationTitle() : sender, body, {tag: system ? "kwc-system" : "kwc-chat", type: system ? "system" : (mention ? "mention" : "chat"), messageId: msg.id || ""});
  }

  function maybeNotifyDirectMessage(message, threadId) {
    if (!message) return;
    const own = currentUserMatchesMessage(message);
    if (own) return;
    const sender = plainNotificationText(message.senderDisplayName || message.senderUsername || t("dm.title", "Messages"), 80);
    const body = plainNotificationText(message.body || "", 180);
    const keyword = notificationKeywordMatch(sender + " " + body);
    if (keyword && browserNotificationOption("keywords")) {
      showBrowserNotification(keywordNotificationTitle(keyword), t("dm.title", "Messages") + ": " + sender + (body ? " · " + body : ""), {tag: "kwc-keyword-" + keyword, type: "keyword", dmThreadId: threadId || message.threadId || "", dmMessageId: message.id || ""});
      return;
    }
    if (!browserNotificationOption("dm")) return;
    showBrowserNotification(t("dm.title", "Messages") + ": " + sender, body, {tag: "kwc-dm-" + String(threadId || message.threadId || ""), dmThreadId: threadId || message.threadId || "", dmMessageId: message.id || ""});
  }

  function maybeNotifyDirectThread(thread) {
    if (!thread || Number(thread.unread || 0) <= 0) return;
    const sender = plainNotificationText(thread.otherLabel || thread.otherDisplayName || thread.otherUsername || t("dm.title", "Messages"), 80);
    const body = plainNotificationText(thread.lastMessage || "", 180);
    const keyword = notificationKeywordMatch(sender + " " + body);
    if (keyword && browserNotificationOption("keywords")) {
      showBrowserNotification(keywordNotificationTitle(keyword), t("dm.title", "Messages") + ": " + sender + (body ? " · " + body : ""), {tag: "kwc-keyword-" + keyword, type: "keyword", dmThreadId: thread.id || ""});
      return;
    }
    if (!browserNotificationOption("dm")) return;
    showBrowserNotification(t("dm.title", "Messages") + ": " + sender, body, {tag: "kwc-dm-" + String(thread.id || ""), dmThreadId: thread.id || ""});
  }

  function maybeNotifyGroupRoom(room) {
    if (!room || Number(room.unread || 0) <= 0) return;
    const roomName = plainNotificationText(room.name || t("group.title", "Group chats"), 80);
    const body = plainNotificationText(room.lastMessage || "", 180);
    const keyword = notificationKeywordMatch(roomName + " " + body);
    if (keyword && browserNotificationOption("keywords")) {
      showBrowserNotification(keywordNotificationTitle(keyword), roomName + (body ? " · " + body : ""), {tag: "kwc-keyword-" + keyword, type: "keyword", groupRoomId: room.id || ""});
      return;
    }
    if (!browserNotificationOption("groupChat")) return;
    showBrowserNotification(roomName, body, {tag: "kwc-group-" + String(room.id || ""), groupRoomId: room.id || ""});
  }

  function maybeNotifyGroupMessage(message, room) {
    if (!message) return;
    const own = currentUserMatchesMessage(message);
    if (own) return;
    const roomName = plainNotificationText(room && room.name || t("group.title", "Group chats"), 80);
    const sender = plainNotificationText(message.senderDisplayName || message.senderUsername || "", 60);
    const body = plainNotificationText(message.body || "", 180);
    const keyword = notificationKeywordMatch(roomName + " " + sender + " " + body);
    if (keyword && browserNotificationOption("keywords")) {
      showBrowserNotification(keywordNotificationTitle(keyword), roomName + (sender ? " · " + sender : "") + (body ? " · " + body : ""), {tag: "kwc-keyword-" + keyword, type: "keyword", groupRoomId: room && room.id || message.roomId || "", groupMessageId: message.id || ""});
      return;
    }
    if (!browserNotificationOption("groupChat")) return;
    showBrowserNotification(roomName + (sender ? " · " + sender : ""), body, {tag: "kwc-group-" + String(room && room.id || message.roomId || ""), groupRoomId: room && room.id || message.roomId || "", groupMessageId: message.id || ""});
  }

  function base64UrlToUint8Array(value) {
    const padding = "=".repeat((4 - String(value).length % 4) % 4);
    const base64 = (String(value) + padding).replace(/-/g, "+").replace(/_/g, "/");
    const raw = atob(base64);
    const out = new Uint8Array(raw.length);
    for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
    return out;
  }

  function webPushScopeUrl() {
    // Keep the service worker scope wide enough to see/focus the page where the
    // user enabled push. In addon mode the chat app itself runs in an about:blank
    // iframe, so using window.location would incorrectly fall back to /api/push/.
    // Prefer the parent BlueMap URL that was captured at mount time.
    try {
      const pageHref = currentPageOpenUrl() || (window.location.href === "about:blank" ? window.location.origin + "/" : window.location.href);
      const api = new URL(apiBase + "/push/", pageHref);
      const current = new URL(pageHref, window.location.href === "about:blank" ? window.location.origin + "/" : window.location.href);
      if (api.origin !== current.origin) return apiBase + "/push/";
      const a = api.pathname.split("/").filter(Boolean);
      const b = current.pathname.split("/").filter(Boolean);
      const out = [];
      for (let i = 0; i < Math.min(a.length, b.length); i++) {
        if (a[i] !== b[i]) break;
        out.push(a[i]);
      }
      return current.origin + "/" + (out.length ? out.join("/") + "/" : "");
    } catch (_) {
      try { return new URL("/", currentPageOpenUrl() || window.location.href).href; } catch (__) { return apiBase + "/push/"; }
    }
  }

  function cleanNavigationBaseUrl(value) {
    const raw = String(value || "").trim();
    if (!raw || raw === "about:blank") return "";
    try { return new URL(raw, window.location.href === "about:blank" ? window.location.origin + "/" : window.location.href).href; } catch (_) { return ""; }
  }

  function currentPageOpenUrl() {
    const cfg = typeof window !== "undefined" && window.KokotoWebChatConfig ? window.KokotoWebChatConfig : {};
    const candidates = [
      cfg.parentPageUrl,
      cfg.pageUrl,
      state.parentPageUrl,
      window.location.href
    ];
    for (const item of candidates) {
      const url = cleanNavigationBaseUrl(item);
      if (url) return url;
    }
    return "";
  }

  function configuredStandaloneOpenUrl() {
    const cfg = typeof window !== "undefined" && window.KokotoWebChatConfig ? window.KokotoWebChatConfig : {};
    const standaloneEnabled = state.isStandalone || state.standaloneWebEnabled === true || (state.config && state.config.standaloneWebEnabled === true) || cfg.standalone === true;
    if (!standaloneEnabled) return "";
    const candidates = [
      state.standaloneWebPublicUrl,
      state.config && state.config.standaloneWebPublicUrl,
      cfg.standalonePublicUrl,
      state.standaloneWebPath,
      state.config && state.config.standaloneWebPath,
      cfg.standalonePath
    ];
    for (const item of candidates) {
      const url = cleanNavigationBaseUrl(item);
      if (url) return url;
    }
    return "";
  }

  function notificationOpenUrl() {
    try {
      // A push subscription belongs to the page where the user enabled it.
      // Therefore the open URL must prefer that current/parent page, regardless
      // of frontend.standalone settings. Standalone URLs are only a fallback for
      // standalone pages that cannot expose a clean current URL.
      const base = currentPageOpenUrl() || configuredStandaloneOpenUrl() || window.location.href;
      const url = new URL(base, window.location.href === "about:blank" ? window.location.origin + "/" : window.location.href);
      ["kwcMessage", "kwcDmThread", "kwcDmMessage", "kwcGroupRoom", "kwcGroupMessage", "bmwcMessage", "bmwcDmThread", "bmwcDmMessage", "bmwcGroupRoom", "bmwcGroupMessage"].forEach(k => url.searchParams.delete(k));
      return url.href;
    } catch (_) {
      return currentPageOpenUrl() || configuredStandaloneOpenUrl() || String(window.location.href || "");
    }
  }

  function waitForServiceWorkerActive(reg, timeoutMs = 8000) {
    if (!reg) return Promise.resolve(reg);
    if (reg.active) return Promise.resolve(reg);
    const worker = reg.installing || reg.waiting;
    if (!worker) return Promise.resolve(reg);
    return new Promise(resolve => {
      let done = false;
      const finish = () => {
        if (done) return;
        done = true;
        try { worker.removeEventListener("statechange", onStateChange); } catch (_) {}
        resolve(reg);
      };
      const onStateChange = () => {
        if (worker.state === "activated" || worker.state === "redundant") finish();
      };
      try { worker.addEventListener("statechange", onStateChange); } catch (_) {}
      if (worker.state === "activated" || worker.state === "redundant") finish();
      else setTimeout(finish, Math.max(1000, Math.min(30000, Number(timeoutMs) || 8000)));
    });
  }

  let webPushParentRequestSeq = 0;

  function webPushNeedsParentRegistration() {
    try {
      if (state.isStandalone || state.isPip) return false;
      if (!window.parent || window.parent === window) return false;
      // Addon mode runs the chat app in a script-written about:blank iframe.
      // That document can read the parent origin, but Chromium rejects Service
      // Worker registration from it with InvalidStateError. Register from the
      // real BlueMap parent document instead, then keep the same subscribe API.
      return String(window.location.href || "") === "about:blank" || !!state.parentPageUrl;
    } catch (_) {
      return false;
    }
  }

  function requestParentWebPush(action, payload = {}, timeoutMs = 15000) {
    return new Promise((resolve, reject) => {
      if (!webPushNeedsParentRegistration()) {
        reject(new Error("parent_web_push_unavailable"));
        return;
      }
      const requestId = "kwc-webpush-" + Date.now() + "-" + (++webPushParentRequestSeq);
      let timer = null;
      const cleanup = () => {
        try { window.removeEventListener("message", onMessage); } catch (_) {}
        if (timer) clearTimeout(timer);
      };
      const onMessage = event => {
        if (!trustedParentMessageEvent(event)) return;
        const data = event && event.data || {};
        if (!data || data.source !== "KWCParent" || data.type !== "webPushParentResult" || data.requestId !== requestId) return;
        cleanup();
        if (data.ok === true) resolve(data.result || {});
        else reject(new Error(String(data.error || "parent_web_push_failed")));
      };
      window.addEventListener("message", onMessage);
      timer = setTimeout(() => {
        cleanup();
        reject(new Error("parent_web_push_timeout"));
      }, Math.max(3000, Math.min(45000, Number(timeoutMs) || 15000)));
      postFrame("webPushParentRequest", {requestId, action, payload});
    });
  }

  async function registerWebPushServiceWorker() {
    const swUrl = apiBase + "/push/sw.js?v=" + encodeURIComponent(String(state.serverVersion || Date.now()));
    const scope = webPushScopeUrl();
    const reg = await navigator.serviceWorker.register(swUrl, {scope, updateViaCache: "none"});
    try { await reg.update(); } catch (_) {}
    await waitForServiceWorkerActive(reg);
    return reg;
  }

  async function createWebPushSubscriptionJson() {
    if (webPushNeedsParentRegistration()) {
      const result = await requestParentWebPush("subscribe", {
        apiBase,
        serverVersion: state.serverVersion || "",
        vapidPublicKey: state.webPushVapidPublicKey || "",
        scope: webPushScopeUrl(),
        openUrl: notificationOpenUrl()
      });
      if (!result || !result.subscription) throw new Error("parent_web_push_empty_subscription");
      return result.subscription;
    }
    const reg = await registerWebPushServiceWorker();
    const existing = await reg.pushManager.getSubscription();
    const sub = existing || await reg.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: base64UrlToUint8Array(state.webPushVapidPublicKey)});
    return sub.toJSON();
  }

  async function unsubscribeWebPushBrowserSubscription() {
    if (webPushNeedsParentRegistration()) {
      const result = await requestParentWebPush("unsubscribe", {apiBase, scope: webPushScopeUrl()}).catch(e => ({error: e && e.message ? e.message : String(e || "")}));
      return result && result.endpoint ? String(result.endpoint || "") : "";
    }
    if (!("serviceWorker" in navigator)) return "";
    const reg = await navigator.serviceWorker.getRegistration(webPushScopeUrl()).catch(() => null);
    if (!reg || !reg.pushManager) return "";
    const sub = await reg.pushManager.getSubscription();
    if (!sub) return "";
    const endpoint = sub.endpoint || "";
    await sub.unsubscribe().catch(() => false);
    return endpoint;
  }

  function webPushPushServiceFailure(error) {
    const name = error && error.name ? String(error.name) : "";
    const message = error && (error.message || error.name) ? String(error.message || error.name) : "";
    return /AbortError/i.test(name + " " + message) && /push service|registration failed/i.test(message);
  }

  function noteWebPushAutomaticFailure(error) {
    if (!webPushPushServiceFailure(error)) return false;
    // Browser push-service failures are outside KWC. Repeating subscribe from
    // startup/login/account-sync only creates a retry loop, so pause automatic
    // attempts for this page. User-triggered enable/test can still retry now.
    state.webPushAutoFailure = "push-service";
    state.webPushAutoRetryAfter = Date.now() + 15 * 60 * 1000;
    return true;
  }

  function clearWebPushAutomaticFailure() {
    state.webPushAutoFailure = "";
    state.webPushAutoRetryAfter = 0;
  }

  function webPushErrorText(error) {
    const message = error && (error.message || error.name) ? String(error.message || error.name) : "";
    if (error && error.status) return t("preferences.webPushFailedHttp", "Web Push failed: HTTP {status}").replace("{status}", String(error.status));
    if (/permission/i.test(message)) return t("preferences.notificationsPermissionDenied", "Notification permission is blocked in this browser.");
    if (/secure|ssl|https/i.test(message)) return t("preferences.webPushInsecure", "Web Push requires HTTPS or localhost.");
    if (/VAPID|applicationServerKey/i.test(message)) return t("preferences.webPushInvalidVapid", "Web Push failed: invalid VAPID key.");
    if (/push service|registration failed/i.test(message)) return t("preferences.webPushServiceUnavailable", "Web Push failed: the browser push service is unavailable. Retry later or use Test notification to retry now.");
    if (/AbortError|timeout/i.test(message)) return t("preferences.webPushTimeout", "Web Push failed: request timed out.");
    if (/invalid state/i.test(message)) return t("preferences.webPushInvalidDocument", "Web Push failed: the addon iframe document cannot register a Service Worker directly. Update chat.js so the BlueMap parent page performs the registration.");
    if (/parent_web_push_unavailable/i.test(message)) return t("preferences.webPushParentUnavailable", "Web Push failed: addon parent registration bridge is not available.");
    return message ? t("preferences.webPushFailedWithMessage", "Web Push failed: {message}").replace("{message}", message) : t("preferences.webPushFailed", "Web Push failed.");
  }

  let desktopWebPushCleanupDone = false;

  async function cleanupDesktopWebPushSubscription() {
    if (desktopWebPushCleanupDone || notificationUsesMobilePushUi()) return;
    desktopWebPushCleanupDone = true;
    // Older 5.0.0 builds could create a PushManager subscription on desktop even
    // though desktop notifications use the page Notification API. Remove that
    // stale browser/server subscription once, without disabling page notifications.
    await disableWebPush();
  }

  async function enableWebPush(options = {}) {
    if (!notificationUsesMobilePushUi()) {
      await cleanupDesktopWebPushSubscription();
      return false;
    }
    const reason = webPushUnavailableReason({
      webPushServerDisabled: t("preferences.webPushServerDisabled", "Web Push is disabled by server configuration."),
      webPushUnsupported: t("preferences.webPushUnsupported", "Web Push is not available in this browser or server configuration."),
      webPushInsecure: t("preferences.webPushInsecure", "Web Push requires HTTPS or localhost."),
      webPushNoServiceWorker: t("preferences.webPushNoServiceWorker", "This browser does not support Service Worker."),
      webPushNoPushManager: t("preferences.webPushNoPushManager", "This browser does not support Push API."),
      notificationsUnsupported: t("preferences.notificationsUnsupported", "This browser does not support notifications."),
      notificationsPermissionDenied: t("preferences.notificationsPermissionDenied", "Notification permission is blocked in this browser.")
    });
    if (reason) {
      state.webPushLastError = reason;
      writeStorageValue(LEGACY_WEB_PUSH_ENABLED_KEY, null);
      return false;
    }
    if (options.automatic === true && Date.now() < Number(state.webPushAutoRetryAfter || 0)) return false;
    if (state.webPushRegistering) return false;
    state.webPushRegistering = true;
    state.webPushLastError = "";
    try {
      const ok = webPushNeedsParentRegistration() ? true : await requestBrowserNotifications();
      if (!ok) {
        state.webPushLastError = t("preferences.notificationsPermissionDenied", "Notification permission is blocked in this browser.");
        return false;
      }
      setNotificationsEnabledLocal(true);
      const json = await createWebPushSubscriptionJson();
      const opts = currentNotificationOptions();
      await api("/push/subscribe", {method: "POST", body: JSON.stringify({
        deviceId: webPushDeviceId(),
        endpoint: json.endpoint || "",
        p256dh: json.keys && json.keys.p256dh || "",
        auth: json.keys && json.keys.auth || "",
        notifyNormalChat: opts.normalChat === true,
        notifyDm: opts.dm === true,
        notifyGroupChat: opts.groupChat === true,
        notifyMentions: opts.mentions === true,
        notifyReplies: opts.replies === true,
        notifySystem: opts.system === true,
        notifySystemMode: opts.systemMode || (opts.system === true ? "all" : "off"),
        notifyKeywords: opts.keywords === true,
        keywords: notificationKeywordsText(),
        language: selectedLocale(),
        openUrl: notificationOpenUrl(),
        showMessagePreview: opts.preview === true
      })});
      writeStorageValue(LEGACY_WEB_PUSH_ENABLED_KEY, null);
      state.webPushLastError = "";
      state.webPushSubscriptionActive = true;
      clearWebPushAutomaticFailure();
      return true;
    } catch (e) {
      const pushServiceFailure = noteWebPushAutomaticFailure(e);
      if (!pushServiceFailure || options.automatic !== true) console.warn("KOKOTO WebChat Web Push subscribe failed", e);
      state.webPushLastError = webPushErrorText(e);
      writeStorageValue(LEGACY_WEB_PUSH_ENABLED_KEY, null);
      return false;
    } finally {
      state.webPushRegistering = false;
    }
  }

  async function disableWebPush() {
    try {
      const endpoint = await unsubscribeWebPushBrowserSubscription();
      if (state.token) await api("/push/unsubscribe", {method: "POST", body: JSON.stringify({
        endpoint,
        deviceId: webPushDeviceId(),
        clearLegacy: true
      })}).catch(() => {});
    } catch (_) {
    } finally {
      writeStorageValue(LEGACY_WEB_PUSH_ENABLED_KEY, null);
      state.webPushLastError = "";
      state.webPushSubscriptionActive = false;
      clearWebPushAutomaticFailure();
    }
  }

  async function testWebPush() {
    state.webPushLastError = "";
    const ok = await enableWebPush({automatic: false});
    if (!ok) return false;
    try {
      const res = await api("/push/test", {method: "POST", body: JSON.stringify({deviceId: webPushDeviceId()})});
      if (!res || res.ok === false) throw new Error(res && res.error || "push_test_failed");
      state.webPushLastError = t("preferences.webPushTestSent", "Test push sent. Check this device's notification area.");
      return true;
    } catch (e) {
      state.webPushLastError = webPushErrorText(e);
      return false;
    }
  }

  function accountNotificationPayload() {
    const opts = currentNotificationOptions();
    return {
            normalChat: opts.normalChat === true,
      dm: opts.dm === true,
      groupChat: opts.groupChat === true,
      mentions: opts.mentions === true,
      replies: opts.replies === true,
      systemMode: opts.systemMode || (opts.system === true ? "all" : "off"),
      keywords: opts.keywords === true,
      preview: opts.preview === true,
      keywordText: notificationKeywordsText()
    };
  }

  async function saveAccountNotificationPreferences() {
    if (!state.token || state.applyingAccountNotificationPreferences) return false;
    try {
      const res = await api("/preferences/notifications", {method: "POST", body: JSON.stringify(accountNotificationPayload())});
      return !!(res && res.ok !== false);
    } catch (_) {
      return false;
    }
  }

  function scheduleAccountNotificationPreferencesSave() {
    if (!state.token || state.applyingAccountNotificationPreferences) return;
    if (state.accountNotificationSyncTimer) clearTimeout(state.accountNotificationSyncTimer);
    state.accountNotificationSyncTimer = setTimeout(() => {
      state.accountNotificationSyncTimer = null;
      saveAccountNotificationPreferences().catch(() => {});
    }, 350);
  }

  async function loadAccountNotificationPreferences() {
    if (!state.token) return false;
    try {
      const res = await api("/preferences/notifications");
      const prefs = res && res.preferences;
      if (!prefs || typeof prefs !== "object") return false;
      // First 5.0.0 use: preserve the browser's existing KWC notification/keyword
      // settings by promoting them into the account store once. Later browsers
      // then receive the same account-wide settings instead of starting empty.
      if (prefs.configured !== true) return await saveAccountNotificationPreferences();
      state.applyingAccountNotificationPreferences = true;
      try {
        setNotificationSystemMode(prefs.systemMode || (prefs.system === false ? "off" : "all"));
        ["normalChat", "dm", "groupChat", "mentions", "replies", "keywords", "preview"].forEach(name => {
          if (Object.prototype.hasOwnProperty.call(prefs, name)) setNotificationOption(name, prefs[name] === true);
        });
        if (Object.prototype.hasOwnProperty.call(prefs, "keywordText")) setNotificationKeywordsText(prefs.keywordText || "");
      } finally {
        state.applyingAccountNotificationPreferences = false;
      }
      return true;
    } catch (_) {
      return false;
    }
  }

  async function ensurePreferredWebPush() {
    if (!notificationUsesMobilePushUi()) {
      await cleanupDesktopWebPushSubscription();
      return;
    }
    if (notificationsEnabledLocal() && canUseWebPush()) await enableWebPush({automatic: true});
  }

  function buildUserPreferencesPayload() {
    const configured = Array.isArray(state.config && state.config.uiUserFontOptions) ? state.config.uiUserFontOptions : [];
    const options = configured.length ? configured : ["", "system-ui, sans-serif", "Arial, sans-serif", "Verdana, sans-serif", "Georgia, serif", "serif", "monospace"];
    const seen = new Set();
    const fontOptions = options.filter(v => {
      v = String(v || "");
      if (seen.has(v)) return false;
      seen.add(v);
      return true;
    }).map(v => ({value: String(v || ""), label: fontOptionLabel(v)}));

    const fontHelpParts = preferencesFontHelpParts();
    const noteParts = preferencesNoteParts();

    return {
      labels: {
        title: t("preferences.title", "Chat settings"),
        theme: t("preferences.theme", "Theme"),
        themeDefault: t("preferences.themeDefault", "Default"),
        themeSystem: t("preferences.themeSystem", "System"),
        themeDark: t("preferences.themeDark", "Dark"),
        themeLight: t("preferences.themeLight", "Light"),
        themeHighContrast: t("preferences.themeHighContrast", "High contrast"),
        languageAndTheme: t("preferences.languageAndTheme", "Language and theme"),
        windowSettings: t("preferences.windowSettings", "Window settings"),
        fontSettings: t("preferences.fontSettings", "Font settings"),
        themeResetNote: t("preferences.themeResetNote", "Changing the theme resets visual chat settings to the theme defaults."),
        opacity: t("opacity.title", "Opacity"),
        fontSize: t("preferences.fontSize", "Font size"),
        fontFamily: t("preferences.fontFamily", "Font"),
        fontCustom: t("preferences.fontCustom", "Custom font"),
        fontCustomPlaceholder: t("preferences.fontCustomPlaceholder", "Installed font name or CSS font-family"),
        fontApply: t("preferences.fontApply", "Apply"),
        fontTest: t("preferences.fontTest", "Test"),
        fontHelp: fontHelpParts[0] || t("preferences.fontHelp", "Find the font family name in your OS font settings."),
        fontExample: fontHelpParts[1] || t("preferences.fontExample", "Examples: Malgun Gothic, Noto Sans KR, D2Coding."),
        fontDetected: t("preferences.fontDetected", "Detected in this browser: {name}"),
        fontNotDetected: t("preferences.fontNotDetected", "Not detected. Check the font family name or install the font on this device."),
        fontGeneric: t("preferences.fontGeneric", "Generic CSS family: {name}"),
        fontUnknown: t("preferences.fontUnknown", "Could not test this font in this browser."),
        textColor: t("preferences.textColor", "Message text color"),
        uiTextColor: t("preferences.uiTextColor", "UI text color"),
        textShadow: t("preferences.textShadow", "Text shadow"),
        textShadowNone: t("preferences.textShadowNone", "None"),
        textShadowAuto: t("preferences.textShadowAuto", "Auto"),
        textShadowDark: t("preferences.textShadowDark", "Dark shadow"),
        textShadowLight: t("preferences.textShadowLight", "Light shadow"),
        textShadowCustom: t("preferences.textShadowCustom", "Custom"),
        textShadowCustomValue: t("preferences.textShadowCustomValue", "Custom shadow"),
        textShadowCustomColor: t("preferences.textShadowCustomColor", "Shadow color"),
        textShadowCustomX: t("preferences.textShadowCustomX", "X offset"),
        textShadowCustomY: t("preferences.textShadowCustomY", "Y offset"),
        textShadowCustomBlur: t("preferences.textShadowCustomBlur", "Blur"),
        textShadowCustomOpacity: t("preferences.textShadowCustomOpacity", "Opacity"),
        textShadowCustomPreview: t("preferences.textShadowCustomPreview", "Shadow preview"),
        textShadowCustomPlaceholder: t("preferences.textShadowCustomPlaceholder", "0 1px 2px rgba(0, 0, 0, 0.85)"),
        backgroundColor: t("preferences.backgroundColor", "Background color"),
        inputBackgroundColor: t("preferences.inputBackgroundColor", "Input background color"),
        notifications: t("preferences.notifications", "Notifications"),
        notificationsPage: t("preferences.notificationsPage", "Browser system notifications"),
        notificationsPageHelp: t("preferences.notificationsPageHelp", "Browser notifications are available only in supported browsers."),
        notificationsEnable: t("preferences.notificationsEnable", "Enable notifications"),
        notificationsDisable: t("preferences.notificationsDisable", "Disable notifications"),
        notificationsTest: t("preferences.notificationsTest", "Test notification"),
        notificationsPermissionDenied: t("preferences.notificationsPermissionDenied", "Notification permission is blocked in this browser."),
        notificationsUnsupported: t("preferences.notificationsUnsupported", "This browser does not support notifications."),
        notificationsServerDisabled: t("preferences.notificationsServerDisabled", "Notifications are disabled by server configuration."),
        notificationsEnabledStatus: t("preferences.notificationsEnabledStatus", "Enabled in this browser."),
        notificationsAllowedDisabledStatus: t("preferences.notificationsAllowedDisabledStatus", "Allowed by browser, disabled in chat settings."),
        notificationsNotRequestedStatus: t("preferences.notificationsNotRequestedStatus", "Permission is not requested yet."),
        webPush: t("preferences.webPush", "Mobile/background push"),
        webPushHelp: t("preferences.webPushHelp", "Mobile push requires mobile push to be enabled and notification permission to be allowed."),
        webPushEnable: t("preferences.webPushEnable", "Enable notifications"),
        webPushDisable: t("preferences.webPushDisable", "Disable notifications"),
        webPushTest: t("preferences.webPushTest", "Test notification"),
        webPushTestSent: t("preferences.webPushTestSent", "Test push sent. Check this device's notification area."),
        webPushHowToTest: t("preferences.webPushHowToTest", "Mobile/background push uses the same notification switch and type options. Supported browsers subscribe automatically when notifications are enabled."),
        advancedSettings: t("preferences.advancedSettings", "Advanced settings"),
        webPushUnsupported: t("preferences.webPushUnsupported", "Web Push is not available in this browser or server configuration."),
        webPushServerDisabled: t("preferences.webPushServerDisabled", "Web Push is disabled by server configuration."),
        webPushInsecure: t("preferences.webPushInsecure", "Web Push requires HTTPS or localhost."),
        webPushNoServiceWorker: t("preferences.webPushNoServiceWorker", "This browser does not support Service Worker."),
        webPushNoPushManager: t("preferences.webPushNoPushManager", "This browser does not support Push API."),
        webPushStandaloneRequired: t("preferences.webPushStandaloneRequired", "On iOS/iPadOS, add this chat page to the Home Screen and open it as a web app to use mobile/background push."),
        webPushEnabledStatus: t("preferences.webPushEnabledStatus", "Enabled on this browser."),
        webPushDisabledStatus: t("preferences.webPushDisabledStatus", "Disabled on this browser."),
        notifyTypes: t("preferences.notifyTypes", "Notification types"),
        notifyTypesHelp: t("preferences.notifyTypesHelp", "Signed-in users share these notification type settings across their KWC account; browser notification permission remains device-specific."),
        notifyNormalChat: t("preferences.notifyNormalChat", "Normal chat"),
        notifyDm: t("preferences.notifyDm", "DM"),
        notifyGroupChat: t("preferences.notifyGroupChat", "Group chat"),
        notifyMentions: t("preferences.notifyMentions", "Mentions"),
        notifyReplies: t("preferences.notifyReplies", "Replies"),
        notifySystem: t("preferences.notifySystem", "Server"),
        notifySystemAll: t("preferences.notifySystemAll", "All"),
        notifySystemJoinLeave: t("preferences.notifySystemJoinLeave", "Join/leave only"),
        notifySystemOff: t("preferences.notifySystemOff", "Off"),
        notifyKeywords: t("preferences.notifyKeywords", "Keyword alerts"),
        notifyKeywordsList: t("preferences.notifyKeywordsList", "Keyword alert words"),
        notifyKeywordsHelp: t("preferences.notifyKeywordsHelp", "Comma or line separated. Signed-in users share this keyword list across their KWC account and all registered Web Push devices."),
        notifyKeywordsApply: t("preferences.notifyKeywordsApply", "Apply keywords"),
        notifyKeywordsSaved: t("preferences.notifyKeywordsSaved", "Keyword alerts saved."),
        notifyKeywordsNeedsApply: t("preferences.notifyKeywordsNeedsApply", "Keyword list changed. Tap Apply keywords to update push filtering."),
        notifyDisabledByServer: t("preferences.notifyDisabledByServer", "Disabled by server configuration."),
        keywordNotificationTitle: t("preferences.keywordNotificationTitle", "Keyword: {keyword}"),
        notifyPreview: t("preferences.notifyPreview", "Message preview"),
        presetName: t("preferences.presetName", "Preset name"),
        presets: t("preferences.presets", "Saved chat settings"),
        presetSave: t("preferences.presetSave", "Save"),
        presetLoad: t("preferences.presetLoad", "Load"),
        presetDelete: t("preferences.presetDelete", "Delete"),
        presetExport: t("preferences.presetExport", "Export"),
        presetImport: t("preferences.presetImport", "Import"),
        presetNew: t("preferences.presetNew", "New profile"),
        presetServerHelp: t("preferences.presetServerHelp", "Signed-in users can save multiple chat-setting profiles to their KWC account and load them on other devices."),
        presetLocalHelp: t("preferences.presetLocalHelp", "Guest presets are stored only in this browser."),
        presetImportFailed: t("preferences.presetImportFailed", "Import failed."),
        presetExportFailed: t("preferences.presetExportFailed", "Export failed."),
        presetNamePrompt: t("preferences.presetNamePrompt", "Preset name"),
        presetEmpty: t("preferences.presetEmpty", "No saved settings."),
        presetSaved: t("preferences.presetSaved", "Saved."),
        presetLoaded: t("preferences.presetLoaded", "Loaded."),
        presetDeleted: t("preferences.presetDeleted", "Deleted."),
        presetSaveFailed: t("preferences.presetSaveFailed", "Save failed. Browser storage may be blocked."),
        presetSelectRequired: t("preferences.presetSelectRequired", "Select saved settings first."),
        presetConfirmDelete: t("preferences.presetConfirmDelete", "Delete saved settings {name}?"),
        language: t("preferences.language", "Language"),
        reset: t("button.reset", "Reset"),
        close: t("button.close", "Close"),
        note: noteParts.note,
        noteDrag: noteParts.drag,
        fontDefault: t("preferences.fontDefault", "Default"),
        fontSystem: t("preferences.fontSystem", "System"),
        languageDefault: t("preferences.languageDefault", "Default")
      },
      opacityPercent: Math.round(Number(effectiveOpacity()) * 100),
      defaultOpacityPercent: Math.round(Number(clampOpacity(state.config.uiOpacity)) * 100),
      fontSizePx: Number(formatDecimalNumber(Number(effectiveBaseFontSize()) || 13, 2)),
      defaultFontSizePx: Number(formatDecimalNumber(Number(state.config.uiFontSize || 13), 2)),
      fontFamily: savedUserFontFamily(),
      textColor: effectiveUserTextColor(),
      uiTextColor: effectiveUserUiTextColor(),
      textShadowMode: effectiveUserTextShadowMode(),
      textShadowCustom: effectiveUserTextShadowCustom(),
      backgroundColor: effectiveUserBackgroundColor(),
      inputBackgroundColor: effectiveUserInputBackgroundColor(),
      notificationsEnabled: notificationsEnabledLocal(),
      webPushEnabledLocal: notificationsEnabledLocal() && canUseWebPush(),
      webPushAvailable: canUseWebPush(),
      webPushNotificationTitle: configuredNotificationTitle(),
      notificationOptions: currentNotificationOptions(),
      notificationOptionsAllowed: currentNotificationOptionsAllowed(),
      notificationKeywords: notificationKeywordsText(),
      accountPreferencesActive: !!state.token,
      fontOptions,
      serverProfilesEnabled: serverUserProfilesActive(),
      serverProfilesMax: state.userProfilesMaxProfiles,
      serverProfilesAllowImportExport: state.userProfilesAllowImportExport,
      serverProfiles: serverUserProfilesActive() ? (state.accountProfiles || []) : [],
      selectedServerProfileId: serverUserProfilesActive() ? selectedAccountProfileId() : "",
      theme: savedUserTheme(),
      themeOptions: [
        {value: "", label: t("preferences.themeDefault", "Default")},
        {value: "system", label: t("preferences.themeSystem", "System")},
        {value: "dark", label: t("preferences.themeDark", "Dark")},
        {value: "light", label: t("preferences.themeLight", "Light")},
        {value: "high-contrast", label: t("preferences.themeHighContrast", "High contrast")}
      ],
      language: savedUserLanguage(),
      languageOptions: [""].concat((state.availableLanguages && state.availableLanguages.length ? state.availableLanguages : ["ko-KR", "en-US", "ja-JP", "zh-CN"]).filter(Boolean)).map(code => ({value: code, label: languageLabel(code)}))
    };
  }

  function optionListHtml(items, current) {
    return (items || []).map(item => `<option value="${esc(item.value)}"${String(item.value || "") === String(current || "") ? " selected" : ""}>${esc(item.label)}</option>`).join("");
  }


  const CHAT_SETTING_PRESETS_KEY = "kwc.chatSettingPresets";
  const CHAT_SETTING_PRESET_STORAGE_KEYS = [
    "kwc.userTheme", "kwc.userOpacity", "kwc.userFontSize", "kwc.userFontFamily",
    "kwc.userTextColor", "kwc.userUiTextColor", "kwc.userTextShadowMode", "kwc.userTextShadowCustom",
    "kwc.userBackgroundColor", "kwc.userInputBackgroundColor", "kwc.language",
    "kwc.senderIdentityMode", "kwc.timeDisplayMode", "kwc.dmConversationFocus",
    "kwc.emojiPanelHeightPx", "kwc.windowWidth", "kwc.windowHeight",
    "kwc.resizeLocked", "kwc.minimized", NOTIFICATION_ENABLED_KEY,
    "kwc.notify.normalChat", "kwc.notify.dm", "kwc.notify.groupChat", "kwc.notify.mentions",
    "kwc.notify.system", "kwc.notify.keywords", "kwc.notify.keywords.list", "kwc.notify.preview",
    "kwc.parentFramePosition", "kwc.parentUserPrefsModalPos", "kwc.localUserPrefsModalPos"
  ];

  function readLocalStorageValue(key) {
    try { return localStorage.getItem(key); } catch (_) { return null; }
  }

  function writeLocalStorageValue(key, value) {
    try {
      if (value === null || value === undefined) localStorage.removeItem(key);
      else localStorage.setItem(key, String(value));
    } catch (_) {}
  }

  function collectChatSettingPresetStorage() {
    const out = {};
    CHAT_SETTING_PRESET_STORAGE_KEYS.forEach(key => { out[key] = readLocalStorageValue(key); });
    return out;
  }

  function applyChatSettingPresetStorage(storage) {
    if (!storage || typeof storage !== "object") return;
    CHAT_SETTING_PRESET_STORAGE_KEYS.forEach(key => {
      if (!Object.prototype.hasOwnProperty.call(storage, key)) return;
      let value = storage[key];
      if (key === NOTIFICATION_KEYWORDS_KEY && isPollutedNotificationKeywordText(value)) value = "";
      writeLocalStorageValue(key, value);
    });
    if (Object.prototype.hasOwnProperty.call(storage, LEGACY_NOTIFICATION_KEYWORDS_KEY)
        && !Object.prototype.hasOwnProperty.call(storage, NOTIFICATION_KEYWORDS_KEY)) {
      const legacyKeywords = storage[LEGACY_NOTIFICATION_KEYWORDS_KEY];
      writeLocalStorageValue(NOTIFICATION_KEYWORDS_KEY, isPollutedNotificationKeywordText(legacyKeywords) ? "" : legacyKeywords);
    }
    state.resizeLocked = localStorage.getItem("kwc.resizeLocked") === "1";
    if (Object.prototype.hasOwnProperty.call(storage, "kwc.minimized")) {
      state.minimized = localStorage.getItem("kwc.minimized") === "1";
    }
    state.senderIdentityMode = localStorage.getItem("kwc.senderIdentityMode") === "real" ? "real" : "display";
    state.timeDisplayMode = localStorage.getItem("kwc.timeDisplayMode") === "full" ? "full" : "short";
    state.dmConversationFocus = localStorage.getItem("kwc.dmConversationFocus") === "1";
    const emojiHeight = Number(localStorage.getItem("kwc.emojiPanelHeightPx") || state.emojiPanelHeightPx || 180);
    if (Number.isFinite(emojiHeight)) state.emojiPanelHeightPx = Math.max(56, Math.min(420, emojiHeight));
    applyWindowSizeConfig();
    updateResizeLockButton();
    updateFrameSize();
    const migratedNotificationEnabled = readLegacyNotificationEnabledFromStorage(storage);
    if (migratedNotificationEnabled !== null) setNotificationsEnabledLocal(migratedNotificationEnabled);
    if (notificationsEnabledLocal()) ensurePreferredWebPush().catch(() => {});
    else disableWebPush().catch(() => {});
  }

  function loadChatSettingPresets() {
    try {
      const raw = localStorage.getItem(CHAT_SETTING_PRESETS_KEY) || "[]";
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.filter(item => item && typeof item === "object" && String(item.name || "").trim()) : [];
    } catch (_) {
      return [];
    }
  }

  function saveChatSettingPresets(list) {
    try {
      localStorage.setItem(CHAT_SETTING_PRESETS_KEY, JSON.stringify(Array.isArray(list) ? list.slice(0, 20) : []));
      return true;
    } catch (_) {
      return false;
    }
  }

  function currentChatSettingPresetData() {
    const storage = collectChatSettingPresetStorage();
    const opts = currentNotificationOptions();
    Object.keys(opts).forEach(name => {
      const def = notificationOptionDef(name);
      if (def) storage[def.key] = opts[name] ? "1" : "0";
    });
    return {
      version: 2,
      theme: savedUserTheme(),
      opacity: savedUserOpacity(),
      fontSize: savedUserFontSize(),
      fontFamily: savedUserFontFamily(),
      textColor: savedUserTextColor(),
      uiTextColor: savedUserUiTextColor(),
      textShadowMode: savedUserTextShadowMode(),
      textShadowCustom: savedUserTextShadowCustom(),
      backgroundColor: savedUserBackgroundColor(),
      inputBackgroundColor: savedUserInputBackgroundColor(),
      language: savedUserLanguage(),
      storage
    };
  }

  function applyNullablePreference(value, setter, resetter) {
    if (value === null || value === undefined || value === "") resetter();
    else setter(value);
  }

  function applyChatSettingPresetData(data) {
    data = data || {};
    if (data.storage && typeof data.storage === "object") applyChatSettingPresetStorage(data.storage);
    if (Object.prototype.hasOwnProperty.call(data, "theme")) {
      const theme = String(data.theme || "");
      if (theme) localStorage.setItem("kwc.userTheme", normalizedTheme(theme));
      else localStorage.removeItem("kwc.userTheme");
    }
    applyNullablePreference(data.opacity, setUserOpacity, resetUserOpacity);
    applyNullablePreference(data.fontSize, setUserFontSize, resetUserFontSize);
    applyNullablePreference(data.fontFamily, setUserFontFamily, resetUserFontFamily);
    applyNullablePreference(data.textColor, setUserTextColor, resetUserTextColor);
    applyNullablePreference(data.uiTextColor, setUserUiTextColor, resetUserUiTextColor);
    applyNullablePreference(data.textShadowMode, setUserTextShadowMode, resetUserTextShadowMode);
    applyNullablePreference(data.textShadowCustom, setUserTextShadowCustom, resetUserTextShadowCustom);
    applyNullablePreference(data.backgroundColor, setUserBackgroundColor, resetUserBackgroundColor);
    applyNullablePreference(data.inputBackgroundColor, setUserInputBackgroundColor, resetUserInputBackgroundColor);
    applyNullablePreference(data.language, setUserLanguage, resetUserLanguage);
    applyFontSizeConfig();
    applyThemeConfig();
    refreshRenderedMessagesForLocale();
    scheduleVirtualRender({preserveScroll: true, stickToBottom: false, deferDuringScroll: false});
  }

  function cleanPresetSaveButtonLabel(value) {
    const text = String(value || "Save")
      .replace(/^\s*(?:현재|Current)\s+/i, "")
      .replace(/\s+(?:현재|current)\s*$/i, "")
      .trim();
    return text || "Save";
  }

  const USER_PROFILE_SELECTED_KEY = "kwc.userProfileId";

  function serverUserProfilesActive() {
    return !!(state.token && state.userProfilesEnabled && state.userProfilesMaxProfiles > 0);
  }

  function selectedAccountProfileId() {
    try { return localStorage.getItem(USER_PROFILE_SELECTED_KEY) || ""; } catch (_) { return ""; }
  }

  function setSelectedAccountProfileId(id) {
    try {
      if (id) localStorage.setItem(USER_PROFILE_SELECTED_KEY, String(id));
      else localStorage.removeItem(USER_PROFILE_SELECTED_KEY);
    } catch (_) {}
  }

  async function loadAccountProfiles() {
    if (!serverUserProfilesActive() || state.accountProfilesLoading) return state.accountProfiles || [];
    state.accountProfilesLoading = true;
    try {
      const res = await api("/preferences/profiles");
      state.userProfilesEnabled = res && res.enabled === true;
      state.userProfilesMaxProfiles = Math.max(0, Math.min(20, Math.floor(Number(res && res.maxProfiles) || 0)));
      state.userProfilesAllowImportExport = !res || res.allowImportExport !== false;
      state.accountProfiles = Array.isArray(res && res.profiles) ? res.profiles : [];
      const selected = selectedAccountProfileId();
      if (selected && !state.accountProfiles.some(item => String(item && item.id || "") === selected)) setSelectedAccountProfileId("");
      return state.accountProfiles;
    } catch (_) {
      return state.accountProfiles || [];
    } finally {
      state.accountProfilesLoading = false;
    }
  }

  function currentAccountProfileData(name, id = "") {
    return {
            id: String(id || ""),
      name: String(name || "").trim(),
      theme: savedUserTheme(),
      // Account profiles must preserve the same explicit-vs-unset state as local
      // chat-setting presets. Saving effective server defaults here made a profile
      // change font size/opacity when loaded on another server/device.
      opacity: savedUserOpacity(),
      fontSize: savedUserFontSize(),
      fontFamily: savedUserFontFamily(),
      textColor: savedUserTextColor(),
      uiTextColor: savedUserUiTextColor(),
      textShadowMode: savedUserTextShadowMode(),
      textShadowCustom: savedUserTextShadowCustom(),
      backgroundColor: savedUserBackgroundColor(),
      inputBackgroundColor: savedUserInputBackgroundColor(),
      language: savedUserLanguage()
    };
  }

  async function saveAccountProfile(id, name) {
    const res = await api("/preferences/profile/save", {method: "POST", body: JSON.stringify(currentAccountProfileData(name, id))});
    if (!res || res.ok === false || !res.profile) throw new Error(res && res.error || "profile_save_failed");
    await loadAccountProfiles();
    setSelectedAccountProfileId(res.profile.id || "");
    return res.profile;
  }

  function applyAccountProfile(profile) {
    if (!profile || typeof profile !== "object") return false;
    applyChatSettingPresetData(profile);
    setSelectedAccountProfileId(profile.id || "");
    return true;
  }

  async function deleteAccountProfile(id) {
    const res = await api("/preferences/profile/delete", {method: "POST", body: JSON.stringify({id: String(id || "")})});
    if (!res || res.ok === false) throw new Error(res && res.error || "profile_delete_failed");
    if (selectedAccountProfileId() === String(id || "")) setSelectedAccountProfileId("");
    await loadAccountProfiles();
    return true;
  }

  function safeProfileDownloadName(name) {
    const base = String(name || "profile").replace(/[\\/:*?"<>|\x00-\x1f]/g, "_").trim().slice(0, 80) || "profile";
    return "KWC-profile-" + base + ".json";
  }

  async function fetchAccountProfileExport(id) {
    if (!state.userProfilesAllowImportExport) throw new Error("profile_export_disabled");
    const res = await api("/preferences/profile/export?id=" + encodeURIComponent(String(id || "")));
    if (!res || res.ok === false || typeof res.json !== "string") throw new Error(res && res.error || "profile_export_failed");
    const profile = (state.accountProfiles || []).find(item => String(item && item.id || "") === String(id || ""));
    return {json: res.json, name: profile && profile.name || "profile"};
  }

  async function exportAccountProfile(id) {
    const exported = await fetchAccountProfileExport(id);
    const blob = new Blob([exported.json], {type: "application/json;charset=utf-8"});
    const url = URL.createObjectURL(blob);
    try {
      const a = document.createElement("a");
      a.href = url;
      a.download = safeProfileDownloadName(exported.name);
      document.body.appendChild(a);
      a.click();
      a.remove();
    } finally {
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  }

  async function importAccountProfileJson(json) {
    if (!state.userProfilesAllowImportExport) throw new Error("profile_import_disabled");
    const text = String(json || "");
    if (new TextEncoder().encode(text).length > 16 * 1024) throw new Error("profile_import_too_large");
    const res = await api("/preferences/profile/import", {method: "POST", body: JSON.stringify({profileJson: text})});
    if (!res || res.ok === false || !res.profile) throw new Error(res && res.error || "profile_import_failed");
    await loadAccountProfiles();
    setSelectedAccountProfileId(res.profile.id || "");
    return res.profile;
  }

  function accountProfileOptionsHtml(selected = "") {
    const list = Array.isArray(state.accountProfiles) ? state.accountProfiles : [];
    const current = String(selected || "");
    const createOption = `<option value=""${current ? "" : " selected"}>${esc(t("preferences.presetNew", "New profile"))}</option>`;
    if (!list.length) return createOption;
    return createOption + list.map(item => {
      const id = String(item && item.id || "");
      const name = String(item && item.name || "").trim();
      return `<option value="${esc(id)}" ${id === current ? "selected" : ""}>${esc(name)}</option>`;
    }).join("");
  }

  function chatSettingPresetOptionsHtml(selected = "") {
    if (serverUserProfilesActive()) return accountProfileOptionsHtml(selected || selectedAccountProfileId());
    const list = loadChatSettingPresets();
    if (!list.length) return `<option value="">${esc(t("preferences.presetEmpty", "No saved settings."))}</option>`;
    return list.map(item => {
      const name = String(item.name || "").trim();
      return `<option value="${esc(name)}" ${name === selected ? "selected" : ""}>${esc(name)}</option>`;
    }).join("");
  }

  const USER_PREF_SECTION_STATE_KEY = "kwc.userPrefsSectionsOpen";

  function readUserPrefSectionsOpen() {
    try {
      const raw = localStorage.getItem(USER_PREF_SECTION_STATE_KEY) || "{}";
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch (_) {
      return {};
    }
  }

  function userPrefSectionOpen(name) {
    const map = readUserPrefSectionsOpen();
    return map[String(name || "")] === true;
  }

  function setUserPrefSectionOpen(name, open) {
    const key = String(name || "");
    if (!key) return;
    const map = readUserPrefSectionsOpen();
    map[key] = open === true;
    try { localStorage.setItem(USER_PREF_SECTION_STATE_KEY, JSON.stringify(map)); } catch (_) {}
  }

  function bindUserPrefSectionPersistence(root) {
    if (!root) return;
    root.querySelectorAll("details[data-kwc-pref-section]").forEach(details => {
      const name = details.getAttribute("data-kwc-pref-section") || "";
      details.open = userPrefSectionOpen(name);
      details.addEventListener("toggle", () => setUserPrefSectionOpen(name, details.open));
    });
  }

  function openLocalUserPreferencesModal(payload) {
    const old = document.getElementById("kwc-user-prefs-modal");
    if (old) old.remove();
    const labels = payload.labels || {};
    const includeDragNote = !state.isPip;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    wrap.id = "kwc-user-prefs-modal";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-user-prefs-modal">
        <div class="kwc-modal-head">
          <h3>${esc(labels.title || "Chat settings")}</h3>
          <div class="kwc-modal-head-actions">
            <button class="kwc-button" id="kwc-prefs-reset">${esc(labels.reset || "Reset")}</button>
            <button class="kwc-button" id="kwc-prefs-close">${esc(labels.close || "Close")}</button>
          </div>
        </div>

        <div class="kwc-user-prefs-scroll">
        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="languageTheme">
          <summary>${esc(labels.languageAndTheme || "Language and theme")}</summary>
          <label class="kwc-pref-label"><span>${esc(labels.language || "Language")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-language">${optionListHtml(payload.languageOptions, payload.language)}</select>
          <label class="kwc-pref-label"><span>${esc(labels.theme || "Theme")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-theme">${optionListHtml(payload.themeOptions, payload.theme)}</select>
          <p class="kwc-pref-font-help">${esc(labels.themeResetNote || "Changing the theme resets visual chat settings to the theme defaults.")}</p>
        </details>

        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="window">
          <summary>${esc(labels.windowSettings || "Window settings")}</summary>
          <label class="kwc-pref-label"><span>${esc(labels.opacity || "Opacity")}</span><strong id="kwc-prefs-opacity-value">${Math.round(payload.opacityPercent || 100)}%</strong></label>
          <input class="kwc-pref-range" id="kwc-prefs-opacity" type="range" min="10" max="100" step="1" value="${Math.round(payload.opacityPercent || 100)}">
          <div class="kwc-pref-hints"><span>10%</span><span>100%</span></div>
          <label class="kwc-pref-label"><span>${esc(labels.backgroundColor || "Background color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-background-color" type="color" value="${esc(payload.backgroundColor || "#121216")}"></label>
          <label class="kwc-pref-label"><span>${esc(labels.inputBackgroundColor || "Input background color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-input-background-color" type="color" value="${esc(payload.inputBackgroundColor || "#000000")}"></label>
        </details>

        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="font">
          <summary>${esc(labels.fontSettings || "Font settings")}</summary>
          <label class="kwc-pref-label"><span>${esc(labels.fontFamily || "Font")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-font-family">${optionListHtml(payload.fontOptions, payload.fontFamily)}</select>
          <label class="kwc-pref-label"><span>${esc(labels.fontCustom || "Custom font")}</span></label>
          <div class="kwc-pref-inline-row">
            <input class="kwc-input" id="kwc-prefs-font-family-custom" type="text" value="${esc(payload.fontFamily || "")}" placeholder="${esc(labels.fontCustomPlaceholder || "Installed font name or CSS font-family")}">
            <button class="kwc-button" id="kwc-prefs-font-family-test" type="button">${esc(labels.fontTest || "Test")}</button>
            <button class="kwc-button" id="kwc-prefs-font-family-apply" type="button">${esc(labels.fontApply || "Apply")}</button>
          </div>
          <p class="kwc-pref-font-help">${preferencesFontHelpHtml(labels, esc)}</p>
          <p class="kwc-pref-font-status" id="kwc-prefs-font-family-status" aria-live="polite"></p>
          <label class="kwc-pref-label"><span>${esc(labels.fontSize || "Font size")}</span><strong id="kwc-prefs-font-size-value">${pxLabel(payload.fontSizePx || 13)}</strong></label>
          <input class="kwc-pref-range" id="kwc-prefs-font-size" type="range" min="8" max="36" step="0.1" value="${formatDecimalNumber(payload.fontSizePx || 13, 2)}">
          <div class="kwc-pref-hints"><span>8px</span><span>36px</span></div>
          <label class="kwc-pref-label"><span>${esc(labels.textColor || "Message text color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-text-color" type="color" value="${esc(payload.textColor || "#ffffff")}"></label>
          <label class="kwc-pref-label"><span>${esc(labels.uiTextColor || "UI text color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-ui-text-color" type="color" value="${esc(payload.uiTextColor || "#ffffff")}"></label>
          <label class="kwc-pref-label"><span>${esc(labels.textShadow || "Text shadow")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-text-shadow-mode">
            <option value="none" ${payload.textShadowMode === "none" ? "selected" : ""}>${esc(labels.textShadowNone || "None")}</option>
            <option value="auto" ${payload.textShadowMode === "auto" ? "selected" : ""}>${esc(labels.textShadowAuto || "Auto")}</option>
            <option value="dark" ${payload.textShadowMode === "dark" ? "selected" : ""}>${esc(labels.textShadowDark || "Dark shadow")}</option>
            <option value="light" ${payload.textShadowMode === "light" ? "selected" : ""}>${esc(labels.textShadowLight || "Light shadow")}</option>
            <option value="custom" ${payload.textShadowMode === "custom" ? "selected" : ""}>${esc(labels.textShadowCustom || "Custom")}</option>
          </select>
          <div class="kwc-shadow-custom-panel" id="kwc-prefs-text-shadow-custom-panel">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomColor || "Shadow color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-text-shadow-color" type="color"></label>
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomX || "X offset")}</span><strong id="kwc-prefs-text-shadow-x-value">0px</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-x" type="range" min="-12" max="12" step="0.1">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomY || "Y offset")}</span><strong id="kwc-prefs-text-shadow-y-value">1px</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-y" type="range" min="-12" max="12" step="0.1">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomBlur || "Blur")}</span><strong id="kwc-prefs-text-shadow-blur-value">2px</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-blur" type="range" min="0" max="24" step="0.1">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomOpacity || "Opacity")}</span><strong id="kwc-prefs-text-shadow-opacity-value">85%</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-opacity" type="range" min="0" max="100" step="1">
            <div class="kwc-shadow-preview" id="kwc-prefs-text-shadow-preview">${esc(labels.textShadowCustomPreview || "Shadow preview")}</div>
          </div>
        </details>

        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="notifications">
          <summary>${esc(labels.notifications || "Notifications")}</summary>
          <p class="kwc-pref-font-help" id="kwc-prefs-notifications-help">${esc(unifiedNotificationHelpText(labels))}</p>
          <div class="kwc-pref-button-row kwc-pref-notification-actions">
            <button class="kwc-button" id="kwc-prefs-notifications-toggle" type="button">${esc(notificationsEnabledLocal() ? (labels.notificationsDisable || "Disable notifications") : (labels.notificationsEnable || "Enable notifications"))}</button>
            <button class="kwc-button" id="kwc-prefs-notifications-test" type="button">${esc(labels.notificationsTest || "Test notification")}</button>
          </div>
          <p class="kwc-pref-font-help" id="kwc-prefs-notifications-status" aria-live="polite">${esc(unifiedNotificationStatusText(labels))}</p>
          <label class="kwc-pref-label"><span>${esc(labels.notifyTypes || "Notification types")}</span></label>
          ${notificationOptionsHtml("kwc-prefs-notify", labels)}
          <label class="kwc-pref-label"><span>${esc(labels.notifyKeywordsList || "Keyword alert words")}</span></label>
          <textarea class="kwc-input kwc-pref-textarea" id="kwc-prefs-notify-keywords-list" rows="3" placeholder="keyword1, keyword2">${esc(notificationKeywordsText())}</textarea>
          <div class="kwc-pref-button-row kwc-pref-keyword-actions">
            <button class="kwc-button" id="kwc-prefs-notify-keywords-apply" type="button">${esc(labels.notifyKeywordsApply || "Apply keywords")}</button>
            <span class="kwc-pref-inline-status" id="kwc-prefs-notify-keywords-status" aria-live="polite"></span>
          </div>
          <p class="kwc-pref-font-help">${esc(labels.notifyKeywordsHelp || "Comma or line separated. Signed-in users share this keyword list across their KWC account and all registered Web Push devices.")}</p>
        </details>

        <div class="kwc-pref-section kwc-pref-static">
          <div class="kwc-pref-section-title">${esc(labels.presets || "Saved chat settings")}</div>
          <div class="kwc-preset-row kwc-preset-row-stacked">
            <select class="kwc-input kwc-pref-select" id="kwc-prefs-preset-select">${chatSettingPresetOptionsHtml("")}</select>
            <div class="kwc-preset-actions kwc-preset-actions-primary">
              <button class="kwc-button" id="kwc-prefs-preset-save" type="button">${esc(cleanPresetSaveButtonLabel(labels.presetSave || "Save"))}</button>
              <button class="kwc-button" id="kwc-prefs-preset-load" type="button">${esc(labels.presetLoad || "Load")}</button>
              <button class="kwc-button" id="kwc-prefs-preset-delete" type="button">${esc(labels.presetDelete || "Delete")}</button>
            </div>
            ${payload.serverProfilesEnabled && payload.serverProfilesAllowImportExport ? `<div class="kwc-preset-actions kwc-preset-actions-secondary"><button class="kwc-button" id="kwc-prefs-preset-export" type="button">${esc(labels.presetExport || "Export")}</button><button class="kwc-button" id="kwc-prefs-preset-import" type="button">${esc(labels.presetImport || "Import")}</button></div>` : ""}
          </div>
          <p class="kwc-pref-font-help">${esc(payload.serverProfilesEnabled ? (labels.presetServerHelp || "Signed-in users can save multiple chat-setting profiles to their KWC account and load them on other devices.") : (labels.presetLocalHelp || "Guest presets are stored only in this browser."))}</p>
          <p class="kwc-pref-font-help" id="kwc-prefs-preset-status" aria-live="polite"></p>
        </div>

        <p class="kwc-opacity-note">${preferencesNoteHtml(labels, includeDragNote, esc)}</p>
        </div>
      </div>
    `;
    document.body.appendChild(wrap);
    makeModalDraggable(wrap, "kwc.localUserPrefsModalPos");
    bindUserPrefSectionPersistence(wrap);

    const themeSelect = wrap.querySelector("#kwc-prefs-theme");
    const opacityInput = wrap.querySelector("#kwc-prefs-opacity");
    const opacityValue = wrap.querySelector("#kwc-prefs-opacity-value");
    const sizeInput = wrap.querySelector("#kwc-prefs-font-size");
    const sizeValue = wrap.querySelector("#kwc-prefs-font-size-value");
    const familySelect = wrap.querySelector("#kwc-prefs-font-family");
    const familyCustomInput = wrap.querySelector("#kwc-prefs-font-family-custom");
    const familyApplyButton = wrap.querySelector("#kwc-prefs-font-family-apply");
    const familyTestButton = wrap.querySelector("#kwc-prefs-font-family-test");
    const familyStatus = wrap.querySelector("#kwc-prefs-font-family-status");
    const textColorInput = wrap.querySelector("#kwc-prefs-text-color");
    const uiTextColorInput = wrap.querySelector("#kwc-prefs-ui-text-color");
    const textShadowModeInput = wrap.querySelector("#kwc-prefs-text-shadow-mode");
    const textShadowCustomPanel = wrap.querySelector("#kwc-prefs-text-shadow-custom-panel");
    const textShadowColorInput = wrap.querySelector("#kwc-prefs-text-shadow-color");
    const textShadowXInput = wrap.querySelector("#kwc-prefs-text-shadow-x");
    const textShadowYInput = wrap.querySelector("#kwc-prefs-text-shadow-y");
    const textShadowBlurInput = wrap.querySelector("#kwc-prefs-text-shadow-blur");
    const textShadowOpacityInput = wrap.querySelector("#kwc-prefs-text-shadow-opacity");
    const textShadowXValue = wrap.querySelector("#kwc-prefs-text-shadow-x-value");
    const textShadowYValue = wrap.querySelector("#kwc-prefs-text-shadow-y-value");
    const textShadowBlurValue = wrap.querySelector("#kwc-prefs-text-shadow-blur-value");
    const textShadowOpacityValue = wrap.querySelector("#kwc-prefs-text-shadow-opacity-value");
    const textShadowPreview = wrap.querySelector("#kwc-prefs-text-shadow-preview");
    const backgroundColorInput = wrap.querySelector("#kwc-prefs-background-color");
    const inputBackgroundColorInput = wrap.querySelector("#kwc-prefs-input-background-color");
    const notifyKeywordsInput = wrap.querySelector("#kwc-prefs-notify-keywords-list");
    const notifyKeywordsApply = wrap.querySelector("#kwc-prefs-notify-keywords-apply");
    const notifyKeywordsStatus = wrap.querySelector("#kwc-prefs-notify-keywords-status");
    const languageSelect = wrap.querySelector("#kwc-prefs-language");
    const notificationsToggle = wrap.querySelector("#kwc-prefs-notifications-toggle");
    const notificationsTest = wrap.querySelector("#kwc-prefs-notifications-test");
    const notificationsStatus = wrap.querySelector("#kwc-prefs-notifications-status");
    const presetSelect = wrap.querySelector("#kwc-prefs-preset-select");
    const presetNameInput = null;
    const presetSave = wrap.querySelector("#kwc-prefs-preset-save");
    const presetLoad = wrap.querySelector("#kwc-prefs-preset-load");
    const presetDelete = wrap.querySelector("#kwc-prefs-preset-delete");
    const presetExport = wrap.querySelector("#kwc-prefs-preset-export");
    const presetImport = wrap.querySelector("#kwc-prefs-preset-import");
    const presetStatus = wrap.querySelector("#kwc-prefs-preset-status");
    const setPresetStatus = message => { if (presetStatus) presetStatus.textContent = message || ""; };

    const close = () => { wrap.remove(); state.prefsModalOpen = false; };
    wrap.querySelector("#kwc-prefs-close").onclick = close;
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });

    if (themeSelect) themeSelect.addEventListener("change", () => setUserTheme(themeSelect.value));

    opacityInput.addEventListener("input", () => {
      const v = Math.max(10, Math.min(100, Number(opacityInput.value) || payload.defaultOpacityPercent || 100));
      opacityValue.textContent = Math.round(v) + "%";
      setUserOpacity(v / 100, false);
    });
    opacityInput.addEventListener("change", () => setUserOpacity((Number(opacityInput.value) || payload.defaultOpacityPercent || 100) / 100, true));
    opacityInput.addEventListener("pointerup", () => setUserOpacity((Number(opacityInput.value) || payload.defaultOpacityPercent || 100) / 100, true));

    sizeInput.addEventListener("input", () => {
      const v = Math.max(8, Math.min(36, Number(sizeInput.value) || payload.defaultFontSizePx || 13));
      sizeValue.textContent = pxLabel(v);
      setUserFontSize(v, false);
    });
    sizeInput.addEventListener("change", () => setUserFontSize(Number(sizeInput.value) || payload.defaultFontSizePx || 13, true));
    sizeInput.addEventListener("pointerup", () => setUserFontSize(Number(sizeInput.value) || payload.defaultFontSizePx || 13, true));

    familySelect.addEventListener("change", () => {
      if (familyCustomInput) familyCustomInput.value = familySelect.value;
      setUserFontFamily(familySelect.value);
    });
    const fontStatusText = status => {
      const name = status && status.name ? status.name : "";
      if (!status || status.state === "empty") return "";
      if (status.state === "detected") return fmt("preferences.fontDetected", "Detected in this browser: {name}", {name});
      if (status.state === "notDetected") return t("preferences.fontNotDetected", "Not detected. Check the font family name or install the font on this device.");
      if (status.state === "generic") return fmt("preferences.fontGeneric", "Generic CSS family: {name}", {name});
      return t("preferences.fontUnknown", "Could not test this font in this browser.");
    };
    const updateFontStatus = () => {
      if (!familyStatus) return;
      const value = familyCustomInput ? familyCustomInput.value : (familySelect ? familySelect.value : "");
      const status = fontDetectionStatus(value);
      familyStatus.textContent = fontStatusText(status);
      familyStatus.dataset.state = status.state || "";
    };
    const applyCustomFont = () => {
      setUserFontFamily(familyCustomInput ? familyCustomInput.value : "");
      updateFontStatus();
    };
    if (familyTestButton) familyTestButton.addEventListener("click", updateFontStatus);
    if (familyApplyButton) familyApplyButton.addEventListener("click", applyCustomFont);
    if (familyCustomInput) familyCustomInput.addEventListener("keydown", event => {
      if (event.key !== "Enter" || event.isComposing) return;
      event.preventDefault();
      updateFontStatus();
    });
    if (familyCustomInput) familyCustomInput.addEventListener("input", () => {
      if (familyStatus) familyStatus.textContent = "";
    });
    textColorInput.addEventListener("input", () => setUserTextColor(textColorInput.value));
    uiTextColorInput.addEventListener("input", () => setUserUiTextColor(uiTextColorInput.value));
    const readShadowParts = () => ({
      color: textShadowColorInput ? textShadowColorInput.value : "#000000",
      x: textShadowXInput ? Number(textShadowXInput.value) : 0,
      y: textShadowYInput ? Number(textShadowYInput.value) : 1,
      blur: textShadowBlurInput ? Number(textShadowBlurInput.value) : 2,
      opacity: textShadowOpacityInput ? Number(textShadowOpacityInput.value) : 85
    });
    const syncShadowControls = (parts = parseTextShadowParts(payload.textShadowCustom)) => {
      if (textShadowColorInput) textShadowColorInput.value = parts.color;
      if (textShadowXInput) textShadowXInput.value = formatDecimalNumber(parts.x, 2);
      if (textShadowYInput) textShadowYInput.value = formatDecimalNumber(parts.y, 2);
      if (textShadowBlurInput) textShadowBlurInput.value = formatDecimalNumber(parts.blur, 2);
      if (textShadowOpacityInput) textShadowOpacityInput.value = String(parts.opacity);
      if (textShadowXValue) textShadowXValue.textContent = pxLabel(parts.x);
      if (textShadowYValue) textShadowYValue.textContent = pxLabel(parts.y);
      if (textShadowBlurValue) textShadowBlurValue.textContent = pxLabel(parts.blur);
      if (textShadowOpacityValue) textShadowOpacityValue.textContent = parts.opacity + "%";
      const css = buildTextShadowFromParts(parts);
      if (textShadowPreview) textShadowPreview.style.textShadow = css;
    };
    const updateShadowCustomFromControls = () => {
      const parts = readShadowParts();
      syncShadowControls(parts);
      if (textShadowModeInput && textShadowModeInput.value !== "custom") {
        textShadowModeInput.value = "custom";
        setUserTextShadowMode("custom");
      }
      setUserTextShadowCustom(buildTextShadowFromParts(parts));
    };
    const updateShadowPanelVisibility = () => {
      if (textShadowCustomPanel) textShadowCustomPanel.classList.toggle("kwc-hidden", !textShadowModeInput || textShadowModeInput.value !== "custom");
    };
    syncShadowControls(parseTextShadowParts(payload.textShadowCustom));
    updateShadowPanelVisibility();
    if (textShadowModeInput) textShadowModeInput.addEventListener("change", () => {
      setUserTextShadowMode(textShadowModeInput.value);
      updateShadowPanelVisibility();
    });
    [textShadowColorInput, textShadowXInput, textShadowYInput, textShadowBlurInput, textShadowOpacityInput].forEach(input => {
      if (input) input.addEventListener("input", updateShadowCustomFromControls);
    });
    backgroundColorInput.addEventListener("input", () => setUserBackgroundColor(backgroundColorInput.value));
    inputBackgroundColorInput.addEventListener("input", () => setUserInputBackgroundColor(inputBackgroundColorInput.value));
    languageSelect.addEventListener("change", () => setUserLanguage(languageSelect.value));

    const updateNotificationStatuses = () => {
      if (notificationsStatus) notificationsStatus.textContent = unifiedNotificationStatusText(labels);
      if (notificationsToggle) notificationsToggle.textContent = notificationsEnabledLocal() ? (labels.notificationsDisable || "Disable notifications") : (labels.notificationsEnable || "Enable notifications");
    };
    updateNotificationStatuses();
    bindNotificationOptionInputs(wrap, () => {
      if (notificationsEnabledLocal()) enableWebPush().catch(() => {});
      updateNotificationStatuses();
    });
    const updateNotifyKeywordOptionInputs = () => {
      wrap.querySelectorAll("[data-kwc-notify-option]").forEach(input => {
        const name = input.dataset.kwcNotifyOption;
        input.disabled = !notificationServerAllows(name);
        input.checked = notificationOption(name);
      });
    };
    const applyNotifyKeywords = async () => {
      if (!notifyKeywordsInput) return;
      const keywordText = notifyKeywordsInput.value || "";
      setNotificationKeywordsText(keywordText);
      const keywordAllowed = notificationServerAllows("keywords");
      if (keywordText.trim() && keywordAllowed) setNotificationOption("keywords", true);
      updateNotifyKeywordOptionInputs();
      if (notificationsEnabledLocal()) await enableWebPush();
      updateNotificationStatuses();
      if (notifyKeywordsStatus) notifyKeywordsStatus.textContent = keywordText.trim() && !keywordAllowed
        ? (labels.notifyDisabledByServer || "Disabled by server configuration.")
        : (labels.notifyKeywordsSaved || "Keyword alerts saved.");
    };
    if (notifyKeywordsInput) {
      notifyKeywordsInput.addEventListener("input", () => {
        setNotificationKeywordsText(notifyKeywordsInput.value || "");
        if (notifyKeywordsStatus) notifyKeywordsStatus.textContent = labels.notifyKeywordsNeedsApply || "Keyword list changed. Tap Apply keywords to update push filtering.";
      });
      notifyKeywordsInput.addEventListener("change", () => setNotificationKeywordsText(notifyKeywordsInput.value || ""));
      notifyKeywordsInput.addEventListener("blur", () => setNotificationKeywordsText(notifyKeywordsInput.value || ""));
    }
    if (notifyKeywordsApply) notifyKeywordsApply.addEventListener("click", () => applyNotifyKeywords().catch(() => {
      if (notifyKeywordsStatus) notifyKeywordsStatus.textContent = labels.presetSaveFailed || "Save failed. Browser storage may be blocked.";
    }));
    if (notificationsToggle) notificationsToggle.addEventListener("click", async () => {
      if (notificationsEnabledLocal()) {
        setNotificationsEnabledLocal(false);
        await disableWebPush();
      } else {
        const ok = await requestBrowserNotifications();
        if (ok) {
          setNotificationsEnabledLocal(true);
          if (canUseWebPush()) await enableWebPush();
        }
      }
      updateNotificationStatuses();
    });
    if (notificationsTest) notificationsTest.addEventListener("click", async () => {
      const ok = await requestBrowserNotifications();
      if (ok) {
        setNotificationsEnabledLocal(true);
        if (notificationUsesMobilePushUi() && canUseWebPush()) await testWebPush();
        else showBrowserNotification(configuredNotificationTitle(), labels.notificationsTest || "Test notification", {tag: "kwc-test", force: true});
      }
      updateNotificationStatuses();
    });

    const refreshPresetSelect = selected => {
      if (!presetSelect) return;
      presetSelect.innerHTML = chatSettingPresetOptionsHtml(selected || "");
    };
    if (presetSave) presetSave.addEventListener("click", async () => {
      const selectedValue = String((presetSelect && presetSelect.value) || "").trim();
      const currentServer = serverUserProfilesActive() ? (state.accountProfiles || []).find(item => String(item && item.id || "") === selectedValue) : null;
      const suggested = currentServer ? String(currentServer.name || "") : selectedValue;
      const name = String(window.prompt(labels.presetNamePrompt || labels.presetName || "Preset name", suggested) || "").trim();
      if (!name) return;
      if (themeSelect) {
        const theme = String(themeSelect.value || "");
        if (theme) localStorage.setItem("kwc.userTheme", normalizedTheme(theme));
        else localStorage.removeItem("kwc.userTheme");
      }
      if (opacityInput) setUserOpacity((Number(opacityInput.value) || payload.defaultOpacityPercent || 100) / 100, true);
      if (sizeInput) setUserFontSize(Number(sizeInput.value) || payload.defaultFontSizePx || 13, true);
      if (familyCustomInput) setUserFontFamily(familyCustomInput.value);
      if (textColorInput) setUserTextColor(textColorInput.value);
      if (uiTextColorInput) setUserUiTextColor(uiTextColorInput.value);
      if (textShadowModeInput) setUserTextShadowMode(textShadowModeInput.value);
      if (textShadowModeInput && textShadowModeInput.value === "custom") setUserTextShadowCustom(buildTextShadowFromParts(readShadowParts()));
      if (backgroundColorInput) setUserBackgroundColor(backgroundColorInput.value);
      if (inputBackgroundColorInput) setUserInputBackgroundColor(inputBackgroundColorInput.value);
      if (languageSelect) setUserLanguage(languageSelect.value);
      if (serverUserProfilesActive()) {
        try {
          const profile = await saveAccountProfile(selectedValue, name);
          refreshPresetSelect(profile.id || "");
          setPresetStatus((labels.presetSaved || "Saved.") + " " + name);
        } catch (e) {
          setPresetStatus((labels.presetSaveFailed || "Save failed.") + " " + String(e && e.message || ""));
        }
        return;
      }
      const list = loadChatSettingPresets();
      const data = currentChatSettingPresetData();
      const existing = list.findIndex(item => String(item.name || "") === name);
      const item = {name, savedAt: Date.now(), data};
      if (existing >= 0) list[existing] = item;
      else list.unshift(item);
      if (!saveChatSettingPresets(list)) {
        setPresetStatus(labels.presetSaveFailed || "Save failed. Browser storage may be blocked.");
        return;
      }
      refreshPresetSelect(name);
      setPresetStatus((labels.presetSaved || "Saved.") + " " + name);
    });
    if (presetLoad) presetLoad.addEventListener("click", () => {
      const value = String(presetSelect && presetSelect.value || "").trim();
      if (!value) return alert(labels.presetSelectRequired || "Select saved settings first.");
      if (serverUserProfilesActive()) {
        const item = (state.accountProfiles || []).find(p => String(p && p.id || "") === value);
        if (!item) return alert(labels.presetSelectRequired || "Select saved settings first.");
        applyAccountProfile(item);
        wrap.remove();
        state.prefsModalOpen = false;
        openUserPreferencesModal(true);
        return;
      }
      const item = loadChatSettingPresets().find(p => String(p.name || "") === value);
      if (!item) return alert(labels.presetSelectRequired || "Select saved settings first.");
      applyChatSettingPresetData(item.data || {});
      wrap.remove();
      state.prefsModalOpen = false;
      openUserPreferencesModal(true);
    });
    if (presetDelete) presetDelete.addEventListener("click", async () => {
      const value = String(presetSelect && presetSelect.value || "").trim();
      if (!value) return alert(labels.presetSelectRequired || "Select saved settings first.");
      const currentServer = serverUserProfilesActive() ? (state.accountProfiles || []).find(p => String(p && p.id || "") === value) : null;
      const displayName = currentServer ? String(currentServer.name || "") : value;
      const message = (labels.presetConfirmDelete || "Delete saved settings {name}?").replace("{name}", displayName);
      if (!confirmPlain(message)) return;
      if (serverUserProfilesActive()) {
        try {
          await deleteAccountProfile(value);
          refreshPresetSelect("");
          setPresetStatus((labels.presetDeleted || "Deleted.") + " " + displayName);
        } catch (e) {
          setPresetStatus((labels.presetSaveFailed || "Save failed.") + " " + String(e && e.message || ""));
        }
        return;
      }
      if (!saveChatSettingPresets(loadChatSettingPresets().filter(p => String(p.name || "") !== value))) {
        setPresetStatus(labels.presetSaveFailed || "Save failed. Browser storage may be blocked.");
        return;
      }
      refreshPresetSelect("");
      setPresetStatus((labels.presetDeleted || "Deleted.") + " " + displayName);
    });
    if (presetExport) presetExport.addEventListener("click", async () => {
      const id = String(presetSelect && presetSelect.value || "").trim();
      if (!id) return alert(labels.presetSelectRequired || "Select saved settings first.");
      try { await exportAccountProfile(id); }
      catch (e) { setPresetStatus((labels.presetExportFailed || "Export failed.") + " " + String(e && e.message || "")); }
    });
    if (presetImport) {
      presetImport.addEventListener("click", () => {
        // Do not keep a file input inside the settings modal. Some map frontends
        // override the HTML hidden/display rules for form controls and exposed the
        // browser's native "Choose file" control next to the preset buttons.
        // A short-lived off-screen picker cannot affect the modal layout.
        const picker = document.createElement("input");
        picker.type = "file";
        picker.accept = "application/json,.json";
        picker.tabIndex = -1;
        picker.setAttribute("aria-hidden", "true");
        picker.style.cssText = "position:fixed;left:-10000px;top:-10000px;width:1px;height:1px;opacity:0;pointer-events:none;";
        const cleanup = () => { try { picker.remove(); } catch (_) {} };
        picker.addEventListener("cancel", cleanup, {once: true});
        picker.addEventListener("change", async () => {
          const file = picker.files && picker.files[0];
          if (!file) { cleanup(); return; }
          if (file.size > 16 * 1024) {
            setPresetStatus((labels.presetImportFailed || "Import failed.") + " profile_import_too_large");
            cleanup();
            return;
          }
          try {
            const profile = await importAccountProfileJson(await file.text());
            refreshPresetSelect(profile.id || "");
            setPresetStatus((labels.presetSaved || "Saved.") + " " + String(profile.name || ""));
          } catch (e) {
            setPresetStatus((labels.presetImportFailed || "Import failed.") + " " + String(e && e.message || ""));
          } finally {
            cleanup();
          }
        }, {once: true});
        document.body.appendChild(picker);
        picker.click();
      });
    }

    wrap.querySelector("#kwc-prefs-reset").onclick = () => {
      resetUserOpacity();
      resetUserFontSize();
      resetUserFontFamily();
      if (familySelect) familySelect.value = "";
      if (familyCustomInput) familyCustomInput.value = "";
      resetUserTextColor();
      resetUserUiTextColor();
      resetUserTextShadowMode();
      resetUserTextShadowCustom();
      if (textShadowModeInput) textShadowModeInput.value = payload.textShadowMode || "auto";
      syncShadowControls(parseTextShadowParts(payload.textShadowCustom));
      updateShadowPanelVisibility();
      resetUserBackgroundColor();
      resetUserInputBackgroundColor();
      localStorage.removeItem("kwc.userTheme");
      resetUserLanguage();
      wrap.remove();
      state.prefsModalOpen = false;
    };
  }

  async function openUserPreferencesModal(force = false) {
    if ((state.prefsModalOpen && !force) || !state.config || state.config.uiUserPreferencesControl === false) return;
    state.prefsModalOpen = true;
    if (serverUserProfilesActive()) await loadAccountProfiles();
    const payload = buildUserPreferencesPayload();
    if (state.isPip || window.parent === window) {
      openLocalUserPreferencesModal(payload);
      return;
    }
    postFrame("openUserPreferences", payload);
  }



  function searchResultPreviewText(msg) {
    const text = plainLegacyText(plainDisplayMessageText(msg)).replace(/\s+/g, " ").trim();
    if (text.length <= 180) return text;
    return text.slice(0, 177) + "...";
  }

  function renderSearchResults(container, messages) {
    if (!container) return;
    if (!Array.isArray(messages) || messages.length === 0) {
      container.innerHTML = `<div class="kwc-search-status">${t("search.noResults", "No matching messages.")}</div>`;
      return;
    }
    container.innerHTML = messages.map(msg => {
      const id = esc(msg.id || "");
      const sender = esc(stripMinecraftColorCodes(displaySender(msg) || ""));
      const time = esc(formatMessageTime(msg.time || Date.now()));
      const source = esc(msg.source || "");
      const preview = esc(searchResultPreviewText(msg));
      return `<button type="button" class="kwc-search-result" data-id="${id}">
        <span class="kwc-search-result-meta"><strong>${sender}</strong> <span>${time}</span> <span>${source}</span></span>
        <span class="kwc-search-result-preview">${preview}</span>
      </button>`;
    }).join("");
  }

  function syncDetachedModalThemeVariables() {
    // DM/group/pinned/settings windows are detached from #kwc-root. They receive a
    // snapshot of CSS variables when opened, so a loaded profile previously changed
    // the main chat but left already-open detached chat windows at the old font/theme.
    document.querySelectorAll(".kwc-modal-backdrop").forEach(backdrop => {
      try { applyDetachedModalTheme(backdrop); } catch (_) {}
    });
  }

  function applyDetachedModalTheme(backdrop) {
    const root = document.getElementById("kwc-root");
    if (!root || !backdrop) return;
    const cs = getComputedStyle(root);
    const vars = [
      "--kwc-font-size", "--kwc-message-font-size", "--kwc-input-font-size", "--kwc-button-font-size", "--kwc-badge-font-size",
      "--kwc-chat-font-family", "--kwc-chat-message-font-size", "--kwc-chat-text-color", "--kwc-chat-ui-text-color", "--kwc-chat-background-color", "--kwc-chat-text-shadow",
      "--kwc-text-color", "--kwc-ui-color", "--kwc-ui-text-color", "--kwc-muted-color", "--kwc-border-color", "--kwc-button-text",
      "--kwc-button-bg", "--kwc-button-hover-bg", "--kwc-input-bg", "--kwc-compose-input-bg", "--kwc-link-color",
      "--kwc-modal-bg-rgb", "--kwc-panel-bg-rgb", "--kwc-panel-opacity", "--kwc-shadow-color",
      "--kwc-surface-bg", "--kwc-surface-strong-bg", "--kwc-surface-hover-bg", "--kwc-admin-list-bg", "--kwc-admin-row-bg", "--kwc-text-shadow", "--kwc-ui-text-shadow",
      "--kwc-emoji-render-size", "--kwc-emoji-picker-size", "--kwc-emoji-panel-height", "--kwc-emoji-panel-min-height"
    ];
    vars.forEach(name => {
      const value = cs.getPropertyValue(name);
      if (value) backdrop.style.setProperty(name, value.trim());
    });
    backdrop.style.fontFamily = cs.fontFamily || "";
    ["kwc-theme-light", "kwc-theme-dark", "kwc-theme-system", "kwc-theme-high-contrast"].forEach(cls => {
      backdrop.classList.toggle(cls, root.classList.contains(cls));
    });
  }

  function applySearchModalTheme(backdrop) {
    applyDetachedModalTheme(backdrop);
  }

  function currentSearchLanguage() {
    return String(state.selectedLanguage || localStorage.getItem("kwc.language") || "").trim();
  }

  function searchEnabled() {
    const c = state.config || {};
    return c.searchEnabled !== false;
  }

  function configuredSearchResultLimit() {
    const c = state.config || {};
    const raw = Number(c.searchResultLimit);
    const limit = Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 50;
    return Math.max(1, limit);
  }

  function searchDateMillis(input) {
    const raw = String(input && input.value || "").trim();
    if (!raw) return "";
    const time = new Date(raw).getTime();
    return Number.isFinite(time) ? String(time) : "";
  }

  function searchSourceValue(select) {
    const value = String(select && select.value || "").trim().toLowerCase();
    return ["game", "web", "discord", "system"].includes(value) ? value : "";
  }

  function openSearchModal() {
    if (!searchEnabled()) return;
    if (state.searchModalOpen) {
      const existing = document.querySelector(".kwc-search-modal-backdrop");
      const input = existing && existing.querySelector("#kwc-search-query");
      if (input) input.focus();
      return;
    }
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-search-modal-backdrop";
    applySearchModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-search-modal" role="dialog" aria-modal="true" aria-label="${t("search.title", "Search messages")}">
        <div class="kwc-search-head">
          <h3>${t("search.title", "Search messages")}</h3>
          <button class="kwc-button kwc-search-x" id="kwc-search-close-x" type="button" aria-label="${t("button.close", "Close")}">×</button>
        </div>
        <div class="kwc-search-row">
          <input class="kwc-input" id="kwc-search-query" maxlength="120" placeholder="${t("search.placeholder", "Search message text or sender")}">
          <button class="kwc-button" id="kwc-search-run" type="button">${t("button.search", "Search")}</button>
        </div>
        <details class="kwc-search-options" id="kwc-search-options">
          <summary>${t("search.options", "Options")}</summary>
          <div class="kwc-search-options-grid">
            <label><span>${t("search.from", "From")}</span><input class="kwc-input" id="kwc-search-from" type="datetime-local"></label>
            <label><span>${t("search.to", "To")}</span><input class="kwc-input" id="kwc-search-to" type="datetime-local"></label>
            <label><span>${t("search.sender", "Sender")}</span><input class="kwc-input" id="kwc-search-sender" maxlength="64" placeholder="${t("search.senderPlaceholder", "Optional sender")}"></label>
            <label><span>${t("search.source", "Source")}</span><select class="kwc-input" id="kwc-search-source">
              <option value="">${t("search.sourceAll", "All")}</option>
              <option value="game">${t("search.sourceGame", "Game")}</option>
              <option value="web">${t("search.sourceWeb", "Web")}</option>
              <option value="discord">${t("search.sourceDiscord", "Discord")}</option>
              <option value="system">${t("search.sourceSystem", "System/Event")}</option>
            </select></label>
          </div>
          <label class="kwc-search-check"><input id="kwc-search-include-system" type="checkbox" checked> <span>${t("search.includeSystem", "Include system/event messages")}</span></label>
        </details>
        <div class="kwc-search-status" id="kwc-search-status"></div>
        <div class="kwc-search-results" id="kwc-search-results"></div>
        <div class="kwc-search-footer">
          <button class="kwc-button" id="kwc-search-close" type="button">${t("button.close", "Close")}</button>
        </div>
      </div>
    `;
    document.body.appendChild(wrap);
    state.searchModalOpen = true;

    const input = wrap.querySelector("#kwc-search-query");
    const run = wrap.querySelector("#kwc-search-run");
    const close = wrap.querySelector("#kwc-search-close");
    const closeX = wrap.querySelector("#kwc-search-close-x");
    const status = wrap.querySelector("#kwc-search-status");
    const results = wrap.querySelector("#kwc-search-results");
    const fromInput = wrap.querySelector("#kwc-search-from");
    const toInput = wrap.querySelector("#kwc-search-to");
    const senderInput = wrap.querySelector("#kwc-search-sender");
    const sourceSelect = wrap.querySelector("#kwc-search-source");
    const includeSystemInput = wrap.querySelector("#kwc-search-include-system");

    const closeModal = () => {
      if (wrap.parentNode) wrap.remove();
      state.searchModalOpen = false;
    };

    const stopMapEvent = event => {
      event.stopPropagation();
    };
    ["click", "dblclick", "mousedown", "mouseup", "pointerdown", "pointerup", "pointermove", "touchstart", "touchmove", "touchend", "wheel", "keydown", "keyup", "keypress"].forEach(type => {
      wrap.addEventListener(type, stopMapEvent, false);
    });
    wrap.addEventListener("click", event => {
      if (event.target === wrap) closeModal();
    });

    const doSearch = async () => {
      const query = String(input && input.value || "").trim();
      const from = searchDateMillis(fromInput);
      const to = searchDateMillis(toInput);
      const sender = String(senderInput && senderInput.value || "").trim();
      const source = searchSourceValue(sourceSelect);
      const includeSystem = !includeSystemInput || includeSystemInput.checked;
      const hasFilter = !!(from || to || sender || source || !includeSystem);
      if (!query && !hasFilter) {
        if (results) results.innerHTML = "";
        if (status) status.textContent = t("search.enterQueryOrFilter", "Enter a search term or choose at least one option.");
        if (input) input.focus();
        return;
      }
      if (status) status.textContent = t("search.searching", "Searching...");
      if (results) results.innerHTML = "";
      if (run) run.disabled = true;
      try {
        const lang = currentSearchLanguage();
        const limit = configuredSearchResultLimit();
        const params = new URLSearchParams();
        if (query) params.set("q", query);
        params.set("limit", String(limit));
        if (lang) params.set("lang", lang);
        if (from) params.set("from", from);
        if (to) params.set("to", to);
        if (sender) params.set("sender", sender);
        if (source) params.set("source", source);
        if (!includeSystem) params.set("includeSystem", "false");
        const data = await api(`/history/search?${params.toString()}`, {timeoutMs: 15000});
        const messages = Array.isArray(data && data.messages) ? data.messages : [];
        if (status) status.textContent = messages.length ? fmt("search.resultCount", "{count} results", {count: messages.length}) : "";
        renderSearchResults(results, messages);
      } catch (_) {
        if (status) status.textContent = t("search.failed", "Search failed.");
      } finally {
        if (run) run.disabled = false;
      }
    };

    if (run) run.addEventListener("click", doSearch);
    if (close) close.addEventListener("click", closeModal);
    if (closeX) closeX.addEventListener("click", closeModal);
    [input, fromInput, toInput, senderInput].forEach(el => {
      if (!el) return;
      el.addEventListener("keydown", event => {
        if (event.key === "Enter" && !event.isComposing) {
          event.preventDefault();
          doSearch();
        } else if (event.key === "Escape") {
          event.preventDefault();
          closeModal();
        }
      });
    });
    if (sourceSelect) sourceSelect.addEventListener("keydown", event => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeModal();
      }
    });
    if (results) results.addEventListener("click", event => {
      const item = event.target && event.target.closest ? event.target.closest(".kwc-search-result[data-id]") : null;
      if (!item) return;
      const id = item.dataset.id || "";
      closeModal();
      if (id) jumpToReplyTarget(id);
    });
    setTimeout(() => { if (input) input.focus(); }, 0);
  }

  function openLoginModal() {
    if (state.minimized) {
      toggleMin();
    }
    state.loginModalOpen = true;
    updateFrameSize();

    if (state.token) {
      state.loginModalOpen = false;
      openAccountModal();
      return;
    }

    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal">
        <h3>${t("login.title", "KOKOTO WebChat Login")}</h3>
        <div class="kwc-tabs">
          <button class="kwc-button kwc-tab" id="kwc-tab-login">${t("login.tabLogin", "Login")}</button>
          <button class="kwc-button kwc-tab" id="kwc-tab-link">${t("login.tabLink", "Link")}</button>
        </div>

        <div id="kwc-login-pane">
          <p>${t("login.description", "Log in with an already linked account.")}</p>
          <input class="kwc-input" id="kwc-login-id" placeholder="${t("placeholder.username", "username")}">
          <br><br>
          <input class="kwc-input" id="kwc-login-pw" type="password" placeholder="${t("placeholder.password", "password")}">
          <br><br>
          <button class="kwc-button" id="kwc-login-submit">${t("button.login", "Login")}</button>
          <button class="kwc-button" id="kwc-close">${t("button.close", "Close")}</button>
        </div>

        <div id="kwc-link-pane" class="kwc-hidden">
          <p>${t("link.description", "Run the command below in game.")}</p>
          <div class="kwc-code" id="kwc-link-code">----</div>
          <p><code>${t("link.commandHint", "/kchat auth <code>").replace("<", "&lt;").replace(">", "&gt;")}</code></p>
          <p id="kwc-link-status">${t("link.statusReady", "Press Start to issue a code.")}</p>
          <button class="kwc-button" id="kwc-link-start">${t("button.start", "Start")}</button>
          <button class="kwc-button" id="kwc-close2">${t("button.close", "Close")}</button>
        </div>
      </div>
    `;
    document.body.appendChild(wrap);

    const loginPane = wrap.querySelector("#kwc-login-pane");
    const linkPane = wrap.querySelector("#kwc-link-pane");

    wrap.querySelector("#kwc-tab-login").onclick = () => {
      loginPane.classList.remove("kwc-hidden");
      linkPane.classList.add("kwc-hidden");
    };
    wrap.querySelector("#kwc-tab-link").onclick = () => {
      loginPane.classList.add("kwc-hidden");
      linkPane.classList.remove("kwc-hidden");
    };
    const closeLoginModal = () => {
      wrap.remove();
      state.loginModalOpen = false;
      updateFrameSize();
    };
    wrap.querySelector("#kwc-close").onclick = closeLoginModal;
    wrap.querySelector("#kwc-close2").onclick = closeLoginModal;

    const submitLogin = async () => {
      const submit = wrap.querySelector("#kwc-login-submit");
      if (submit && submit.disabled) return;
      const username = wrap.querySelector("#kwc-login-id").value.trim();
      const password = wrap.querySelector("#kwc-login-pw").value;
      if (submit) submit.disabled = true;
      try {
        const res = await api("/auth/login", {method: "POST", body: JSON.stringify({username, password})});
        if (!res.ok) {
          alertResponse("alert.loginFailed", "Login failed: {error}", res);
          return;
        }
        setLogin(res);
        wrap.remove();
        state.loginModalOpen = false;
        updateFrameSize();
      } finally {
        if (submit && document.body.contains(wrap)) submit.disabled = false;
      }
    };

    wrap.querySelector("#kwc-login-submit").onclick = submitLogin;
    const loginIdInput = wrap.querySelector("#kwc-login-id");
    const loginPasswordInput = wrap.querySelector("#kwc-login-pw");
    if (loginIdInput) {
      loginIdInput.addEventListener("keydown", event => {
        if (event.key !== "Enter" || event.isComposing) return;
        event.preventDefault();
        if (loginPasswordInput) loginPasswordInput.focus();
      });
    }
    if (loginPasswordInput) {
      loginPasswordInput.addEventListener("keydown", event => {
        if (event.key !== "Enter" || event.isComposing) return;
        event.preventDefault();
        submitLogin();
      });
    }
    if (loginIdInput) setTimeout(() => loginIdInput.focus(), 0);

    wrap.querySelector("#kwc-link-start").onclick = async () => {
      const res = await api("/auth/code", {method: "POST", body: "{}"});
      if (!res.ok) {
        alertResponse("alert.codeFailed", "Failed to issue code: {error}", res);
        return;
      }
      wrap.querySelector("#kwc-link-code").textContent = res.code;
      wrap.querySelector("#kwc-link-status").textContent = fmt("link.statusWaiting", "Waiting for /kchat auth {code} in game...", {code: res.code});
      pollLink(res.poll, wrap);
    };
  }

  async function pollLink(poll, modal) {
    let tries = 0;
    const timer = setInterval(async () => {
      tries++;
      if (!document.body.contains(modal) || tries > 180) {
        clearInterval(timer);
        return;
      }
      const res = await api("/auth/status?poll=" + encodeURIComponent(poll));
      if (res.status === "linked") {
        clearInterval(timer);
        setLogin(res);
        modal.querySelector("#kwc-link-status").textContent = t("link.statusLinked", "Linked.");
        if (!res.passwordSet) {
          setTimeout(() => {
            modal.remove();
            state.loginModalOpen = false;
            updateFrameSize();
            openSetPasswordModal();
          }, 300);
        } else {
          setTimeout(() => {
            modal.remove();
            state.loginModalOpen = false;
            updateFrameSize();
          }, 500);
        }
      } else if (res.status === "expired") {
        clearInterval(timer);
        modal.querySelector("#kwc-link-status").textContent = t("link.statusExpired", "Code expired.");
      }
    }, 1000);
  }



  function directMessageRetentionNoticeText() {
    const days = Math.max(0, Math.floor(Number(state.directMessageRetentionDays) || 0));
    const text = days <= 0
      ? t("dm.retentionUnlimited", "DM retention: no time limit")
      : fmt("dm.retentionLimited", "DM retention: {days} days", {days: String(days)});
    return stripWrappingParentheses(text);
  }


  function retentionRemainingText(baseAt, days, scopeKey, expiresAtValue) {
    const d = Math.max(0, Math.floor(Number(days) || 0));
    if (d <= 0) return t("admin.retentionUnlimited", "auto-delete: no time limit");
    let expiresAt = Number(expiresAtValue || 0);
    if (!Number.isFinite(expiresAt) || expiresAt <= 0) {
      const base = Number(baseAt || 0);
      if (!Number.isFinite(base) || base <= 0) return t("admin.retentionUnknown", "auto-delete: unknown");
      // Retention data is stored in milliseconds. Guard against clearly bogus
      // future metadata so a reload cannot show thousands of days remaining.
      const now = Date.now();
      if (base > now + d * 24 * 60 * 60 * 1000 + 24 * 60 * 60 * 1000) return t("admin.retentionUnknown", "auto-delete: unknown");
      expiresAt = base + d * 24 * 60 * 60 * 1000;
    }
    const remaining = expiresAt - Date.now();
    if (remaining <= 0) return t("admin.retentionExpired", "auto-delete: soon");
    const minutes = Math.ceil(remaining / 60000);
    const hours = Math.ceil(remaining / 3600000);
    const daysLeft = Math.ceil(remaining / 86400000);
    let value;
    if (minutes < 60) value = fmt("admin.retentionMinutes", "{value} min left", {value: String(minutes)});
    else if (hours < 48) value = fmt("admin.retentionHours", "{value} h left", {value: String(hours)});
    else value = fmt("admin.retentionDays", "{value} d left", {value: String(daysLeft)});
    return fmt("admin.retentionRemaining", "auto-delete: {time}", {time: value});
  }

  function directMessageAdminIdentityHtml(item) {
    const a = {displayName: item.userADisplayName || item.userALabel || "", username: item.userAUsername || "", uuid: item.userAUuid || ""};
    const b = {displayName: item.userBDisplayName || item.userBLabel || "", username: item.userBUsername || "", uuid: item.userBUuid || ""};
    return `<span class="kwc-admin-meta-identities">${directMessageIdentityHtml(a, "kwc-admin-meta-user")} <span class="kwc-admin-meta-separator">↔</span> ${directMessageIdentityHtml(b, "kwc-admin-meta-user")}</span>`;
  }

  async function deleteAdminDmThread(threadId) {
    threadId = String(threadId || "").trim();
    if (!threadId || !state.token || !state.privateChatSuperAdmin) return;
    if (!confirmPlain(t("admin.confirmDeleteDmThread", "Delete this DM session and all of its metadata/messages/uploads? This cannot be undone."))) return;
    try {
      const res = await adminWrite("/admin/delete-dm-thread", {threadId});
      if (!res?.ok) throw Object.assign(new Error(res?.error || "delete_failed"), {response: res});
      if (state.dmActiveThreadId === threadId) returnDirectMessageToList();
      await loadDirectMessageThreads(true);
      renderDirectMessageThreads();
    } catch (e) {
      alertResponse("alert.deleteFailed", "Delete failed: {error}", e.response || {error: e.message || "error"});
    }
  }


  function cleanupPreviewHtml(preview, scope) {
    if (!preview || typeof preview !== "object") return "";
    const days = Number(preview.retentionDays || 0);
    const expired = Number(preview.expiredMessages || 0);
    const empty = Number(preview.emptySessions || 0);
    const locked = Number(preview.lockedSessions || 0);
    const exempt = Number(preview.retentionExemptSessions || 0);
    const title = scope === "group" ? t("admin.groupCleanupPreview", "Group cleanup preview") : t("admin.dmCleanupPreview", "DM cleanup preview");
    const retention = days > 0 ? fmt("admin.cleanupRetentionDays", "retention {days} days", {days: String(days)}) : t("admin.cleanupNoRetention", "no time limit");
    const body = fmt("admin.cleanupPreviewBody", "{expired} old messages, {empty} empty sessions, {locked} locked, {exempt} excluded", {
      expired: String(expired), empty: String(empty), locked: String(locked), exempt: String(exempt)
    });
    return `<div class="kwc-admin-cleanup-preview"><strong>${esc(title)}</strong><span>${esc(retention)} · ${esc(body)}</span></div>`;
  }

  async function setAdminSessionFlag(type, id, patch) {
    id = String(id || "").trim();
    if (!id || !state.token || !state.privateChatSuperAdmin) return;
    try {
      const body = Object.assign({type, id}, patch || {});
      const res = await adminWrite("/admin/session-flags", body);
      if (!res?.ok) throw Object.assign(new Error(res?.error || "admin_action_failed"), {response: res});
      if (type === "dm") {
        await loadDirectMessageThreads(true);
        renderDirectMessageThreads();
      } else {
        await loadGroupChatRooms(true);
        renderGroupChatRooms();
      }
    } catch (e) {
      alertResponse("alert.adminActionFailed", "Admin action failed: {error}", e.response || {error: e.message || "error"});
    }
  }

  function updateDirectMessageButton() {
    const btn = document.getElementById("kwc-dm");
    const badge = document.getElementById("kwc-dm-badge");
    if (btn) btn.classList.toggle("kwc-hidden", !(state.token && state.directMessageEnabled) || state.minimized);
    if (!badge) return;
    const unread = Math.max(0, Number(state.dmUnread || 0));
    badge.textContent = unread > 99 ? "99+" : String(unread);
    badge.classList.toggle("kwc-hidden", !(state.directMessageWebUnreadBadge && unread > 0));
  }

  async function loadDirectMessageThreads(silent = false) {
    if (!state.directMessageEnabled || !state.token) {
      state.dmUnread = 0;
      state.dmThreads = [];
      state.groupUnread = 0;
      state.groupRooms = [];
      state.groupInvites = [];
      state.groupHiddenRooms = [];
      state.groupAdminRooms = [];
      state.dmAdminThreads = [];
      state.dmCleanupPreview = null;
      state.groupCleanupPreview = null;
      state.privateChatSuperAdmin = false;
      state.groupChatContentAccess = false;
      state.groupAuditMode = false;
      state.groupAuditRoom = null;
    state.privateChatContentAccess = false;
    state.dmAuditMode = false;
    state.dmAuditThread = null;
      updateDirectMessageButton();
      updateGroupChatButton();
      return null;
    }
    try {
      const res = await api("/dm/threads");
      if (!res || res.enabled === false) {
        state.dmUnread = 0;
        state.dmThreads = [];
        state.dmAdminThreads = [];
        state.dmCleanupPreview = null;
        state.privateChatContentAccess = false;
      } else {
        state.dmUnread = Number(res.unread || 0);
        state.dmThreads = Array.isArray(res.threads) ? res.threads : [];
        state.privateChatSuperAdmin = res.privateChatSuperAdmin === true || state.privateChatSuperAdmin === true;
        state.privateChatContentAccess = res.privateChatContentAccess === true;
        state.dmAdminThreads = Array.isArray(res.adminThreads) ? res.adminThreads : [];
        state.dmCleanupPreview = res.cleanupPreview || null;
      }
      updateDirectMessageButton();
      if (state.dmModalOpen) renderDirectMessageThreads();
      return res;
    } catch (e) {
      if (!silent) alertResponse("alert.dmLoadFailed", "Failed to load messages: {error}", e.response || {error: e.message || "error"});
      return null;
    }
  }

  function directMessageLabel(item) {
    if (!item) return "";
    const identity = directMessageIdentityParts(item);
    return identity.display || item.otherLabel || item.label || item.displayName || item.username || item.uuid || item.otherUuid || "";
  }

  function directMessageLabelHtml(value) {
    return minecraftLegacyTextHtml(String(value || ""), true);
  }

  function directMessageIdentityParts(item) {
    item = item || {};
    const display = String(item.otherDisplayName || item.displayName || item.senderDisplayName || "").trim();
    const real = String(item.otherUsername || item.username || item.senderUsername || "").trim();
    const uuid = String(item.otherUuid || item.uuid || item.senderUuid || "").trim();
    let shown = display || real || String(item.otherLabel || item.label || "").trim() || uuid;
    let original = real;
    if (original && plainMinecraftName(original).trim().toLowerCase() === plainMinecraftName(shown).trim().toLowerCase()) {
      original = "";
    }
    return {display: shown, real: original, uuid};
  }

  function directMessageIdentityHtml(item, className = "") {
    const identity = directMessageIdentityParts(item);
    const extra = className ? " " + className : "";
    if (identity.real) {
      const title = state.senderIdentityMode === "real" ? senderDisplayTitle(identity.display) : senderOriginalTitle(identity.real);
      return `<span class="kwc-dm-identity kwc-sender-has-real${extra}" title="${esc(title)}" data-kwc-identity-toggle="1" data-display-sender="${esc(identity.display)}" data-real-sender="${esc(identity.real)}" data-source="dm" data-showing-real="${state.senderIdentityMode === "real" ? "1" : "0"}" role="button" tabindex="0">${senderNameHtml(identity.display, identity.real, "dm")}</span>`;
    }
    return `<span class="kwc-dm-identity${extra}" title="${esc(directMessagePlainLabel(identity.display))}">${directMessageLabelHtml(identity.display)}</span>`;
  }

  function directMessageRemoteServer(item) {
    item = item || {};
    const id = String(item.otherServerId || item.serverId || "").trim();
    const name = String(item.otherServerName || item.serverName || "").trim();
    const label = name || id;
    const remote = item.otherRemote === true || item.remote === true || !!id;
    return {remote, id, name, label};
  }

  function stripDirectMessageServerAffixes(value, server) {
    let result = String(value || "").trim();
    if (!result || !server) return result;
    const labels = [server.name, server.id, server.label]
      .map(value => String(value || "").trim())
      .filter((value, index, values) => value && values.findIndex(other => other.toLowerCase() === value.toLowerCase()) === index)
      .sort((a, b) => b.length - a.length);
    for (let pass = 0; pass < 4; pass++) {
      const before = result;
      labels.forEach(label => {
        const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        result = result.replace(new RegExp("^\\s*\\[" + escaped + "\\]\\s*", "i"), "");
        result = result.replace(new RegExp("\\s*\\[" + escaped + "\\]\\s*$", "i"), "");
      });
      result = result.trim();
      if (result === before) break;
    }
    return result;
  }

  function directMessageHeaderIdentity(item) {
    const server = directMessageRemoteServer(item);
    const identity = directMessageIdentityParts(item);
    if (!server.remote || !server.label) return {server, identity};
    return {
      server,
      identity: {
        display: stripDirectMessageServerAffixes(identity.display, server) || identity.display,
        real: stripDirectMessageServerAffixes(identity.real, server),
        uuid: identity.uuid
      }
    };
  }

  function directMessageHeaderIdentityHtml(item, className = "") {
    const resolved = directMessageHeaderIdentity(item);
    const identityItem = {
      displayName: resolved.identity.display,
      username: resolved.identity.real,
      uuid: resolved.identity.uuid
    };
    const identityHtml = directMessageIdentityHtml(identityItem, className);
    if (!resolved.server.remote || !resolved.server.label) return identityHtml;
    return `<span class="kwc-dm-title-server">[${esc(resolved.server.label)}]</span> ${identityHtml}`;
  }

  function directMessageHeaderPlainLabel(item, fallback = "") {
    if (!item) return directMessagePlainLabel(fallback);
    const resolved = directMessageHeaderIdentity(item);
    const player = directMessagePlainLabel(resolved.identity.display || fallback);
    if (!resolved.server.remote || !resolved.server.label) return player;
    return `[${resolved.server.label}] ${player}`.trim();
  }

  function directMessageBodyHtml(value) {
    // Direct messages use the same text renderer as normal chat text: URLs,
    // Minecraft legacy color codes, and BM Web Chat emoji tokens are rendered
    // on the web side, while the stored/sent message remains the raw text token.
    return renderMessageTokenLines(String(value || ""));
  }

  function directMessagePreviewHtml(value, messageId = "", type = "dm") {
    // Keep private-chat media previews aligned with public chat preview settings,
    // but scope click-to-load/open state per private message so leaving one DM or
    // group conversation cannot keep media open in another conversation.
    return safeImagePreviews(String(value || ""), "private:" + String(type || "dm") + ":" + String(messageId || ""));
  }

  function hydrateDirectMessageRenderedContent(root) {
    if (!root) return;
    root.querySelectorAll(".kwc-youtube-card").forEach(card => {
      if (card.dataset.kwcDmYoutubeInstalled === "1") return;
      card.dataset.kwcDmYoutubeInstalled = "1";
      card.addEventListener("click", () => {
        const embed = card.dataset.youtubeEmbed || "";
        if (!/^https:\/\/(www\.)?youtube(-nocookie)?\.com\/embed\//i.test(embed)) return;
        const key = card.dataset.youtubeKey || "";
        if (key) {
          state.youtubeOpen.add(key);
          if (!state.config || state.config.youtubeRememberExpanded !== false) state.youtubeExpanded.add(key);
        }
        const isShorts = card.dataset.youtubeShorts === "1";
        const wrap = document.createElement("div");
        wrap.className = isShorts ? "kwc-youtube-wrap kwc-youtube-shorts-wrap" : "kwc-youtube-wrap";
        if (key) wrap.setAttribute("data-youtube-key", key);
        wrap.style.cssText = youtubeShellStyle(isShorts, "");
        const safeEmbed = safeYouTubeEmbedUrl(embed);
        if (!safeEmbed) return;
        const iframe = document.createElement("iframe");
        iframe.className = "kwc-youtube-frame";
        iframe.style.cssText = "position:absolute;inset:0;width:100%;height:100%;border:0;";
        iframe.src = safeEmbed;
        iframe.title = t("media.youtubeTitle", "YouTube video");
        iframe.allow = "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share";
        iframe.referrerPolicy = "strict-origin-when-cross-origin";
        iframe.allowFullscreen = true;
        wrap.appendChild(iframe);
        card.replaceWith(wrap);
      }, {once: true});
    });
    root.querySelectorAll(".kwc-social-card").forEach(card => {
      if (card.dataset.kwcDmSocialInstalled === "1") return;
      card.dataset.kwcDmSocialInstalled = "1";
      const load = card.querySelector(".kwc-media-load");
      if (!load) return;
      load.addEventListener("click", () => {
        const kind = card.dataset.socialKind || "";
        const src = card.dataset.socialSrc || "";
        const key = card.dataset.previewKey || previewKey(kind, src);
        if (key) state.mediaOpen.add(key);
        const html = socialEmbedHtml({type: kind, href: src, tiktokId: kind === "tiktok" ? (card.dataset.tiktokId || "") : "", previewKey: key}, "");
        const wrap = document.createElement("div");
        wrap.innerHTML = html;
        const next = wrap.firstElementChild;
        if (!next) return;
        card.replaceWith(next);
        if (kind === "x") loadXWidgets(next);
      }, {once: true});
    });
    hydrateSocialEmbeds(root);
    root.querySelectorAll(".kwc-media-card").forEach(card => {
      if (card.dataset.kwcDmMediaCardInstalled === "1") return;
      card.dataset.kwcDmMediaCardInstalled = "1";
      const load = card.querySelector(".kwc-media-load");
      if (!load) return;
      load.addEventListener("click", () => {
        const kind = card.dataset.mediaKind || "";
        const src = card.dataset.mediaSrc || "";
        const key = card.dataset.previewKey || previewKey(kind, src);
        const safeSrc = safePreviewUrl(src);
        if (!safeSrc) return;
        if (key) state.mediaOpen.add(key);
        const wrap = document.createElement("div");
        wrap.className = kind === "audio" ? "kwc-audio-wrap" : "kwc-video-wrap";
        if (key) wrap.setAttribute("data-preview-key", key);
        const media = createMediaElement(kind, safeSrc, key);
        if (media) wrap.appendChild(media);
        if (media) {
          media.addEventListener("error", () => {
            window.__kwcPreviewFailed && window.__kwcPreviewFailed(key);
            setMediaError(wrap, kind);
          }, {once: true});
        }
        card.replaceWith(wrap);
        if (media && kind === "video" && typeof media.play === "function") {
          const playPromise = media.play();
          if (playPromise && typeof playPromise.catch === "function") playPromise.catch(() => {});
        }
      }, {once: true});
    });
    hydratePreviewMedia(root);
    root.querySelectorAll("a.kwc-link, a.kwc-image-link").forEach(link => {
      if (link.dataset.kwcDmLinkInstalled === "1") return;
      link.dataset.kwcDmLinkInstalled = "1";
      link.addEventListener("click", event => {
        const href = link.getAttribute("href") || "";
        if (/^https?:\/\//i.test(href) && openChatExternalLink(href)) {
          event.preventDefault();
          event.stopPropagation();
        }
      });
    });
  }

  function directMessagePlainLabel(value) {
    return plainLegacyText(value).trim();
  }

  function hasDirectMessageConversationOpen() {
    return !!state.dmActiveThreadId || !!(state.dmDraftTarget && state.dmDraftTarget.uuid);
  }

  function updateDirectMessageViewMode() {
    const modal = document.querySelector(".kwc-dm-modal");
    const open = hasDirectMessageConversationOpen();
    if (modal) modal.classList.toggle("kwc-dm-thread-mode", open);
    const title = document.getElementById("kwc-dm-title");
    if (title) {
      title.classList.toggle("kwc-dm-title-back", open);
      const label = open ? t("dm.backToList", "Back to conversation list") : t("dm.selectThread", "Select a thread");
      title.title = open ? label : "";
      title.setAttribute("aria-label", open ? label : t("dm.selectThread", "Select a thread"));
      title.setAttribute("role", open ? "button" : "heading");
      title.tabIndex = open ? 0 : -1;
    }
  }

  function returnDirectMessageToList() {
    if (!hasDirectMessageConversationOpen()) return;
    clearPrivateReply("dm");
    closeDirectMessageEmojiPanel();
    closeDirectMessagePlayerSearch();
    state.dmActiveThreadId = "";
    state.dmDraftTarget = null;
    state.dmAuditMode = false;
    state.dmAuditThread = null;
    updateDirectMessageComposeControls();
    renderDirectMessageThreads();
    renderDirectMessageMessages([]);
    renderDirectMessageHeader("");
    updateDirectMessageViewMode();
  }

  function renderDirectMessageThreads() {
    const list = document.getElementById("kwc-dm-thread-list");
    if (!list) return;
    const threads = Array.isArray(state.dmThreads) ? state.dmThreads : [];
    const adminThreads = Array.isArray(state.dmAdminThreads) ? state.dmAdminThreads : [];
    if (!threads.length && !adminThreads.length) {
      list.innerHTML = `<div class="kwc-dm-empty">${esc(t("dm.noThreads", "No message threads."))}</div>`;
      updateDirectMessageViewMode();
      return;
    }
    const userHtml = threads.map(thread => {
      const active = thread.id === state.dmActiveThreadId ? " kwc-active" : "";
      const unread = Number(thread.unread || 0);
      const badge = unread > 0 ? `<span class="kwc-dm-thread-badge">${esc(unread > 99 ? "99+" : String(unread))}</span>` : "";
      return `<button type="button" class="kwc-dm-thread${active}" data-dm-thread="${esc(thread.id)}">
        ${directMessageIdentityHtml(thread, "kwc-dm-thread-name")}${badge}
        <span class="kwc-dm-thread-preview" title="${esc(plainLegacyText(thread.lastMessage || ""))}">${directMessageBodyHtml(thread.lastMessage || "")}</span>
      </button>`;
    }).join("");
    const adminTitle = state.privateChatContentAccess
      ? t("admin.privateContentAccess", "Admin DM audit (contents available)")
      : t("admin.privateMetaOnly", "Admin metadata only");
    const adminHtml = adminThreads.length ? `<div class="kwc-admin-meta-title">🛡 ${esc(adminTitle)}</div>` + adminThreads.map(item => {
      const retention = retentionRemainingText(item.retentionBaseAt || item.latestMessageAt || item.updatedAt, item.retentionDays ?? state.directMessageRetentionDays, "dm", item.retentionExpiresAt);
      const flags = `${item.locked ? esc(t("admin.locked", "locked")) + " · " : ""}${item.retentionExempt ? esc(t("admin.retentionExempt", "auto-delete excluded")) + " · " : ""}`;
      const meta = `${esc(retention)} · ${flags}${esc(t("admin.messages", "messages"))}: ${esc(item.messageCount || 0)} · ${esc(t("admin.storage", "storage"))}: ${esc(formatBytes(item.storageBytes || 0))}`;
      const lockLabel = item.locked ? t("admin.unlock", "Unlock") : t("admin.lock", "Lock");
      const exemptLabel = item.retentionExempt ? t("admin.includeRetention", "Include") : t("admin.excludeRetention", "Exclude");
      const lockTitle = item.locked ? t("admin.unlockDmThreadHint", "Unlock this DM session so messages can be sent again.") : t("admin.lockDmThreadHint", "Lock this DM session to prevent new messages.");
      const exemptTitle = item.retentionExempt ? t("admin.includeDmRetentionHint", "Include this DM session in automatic cleanup again.") : t("admin.excludeDmRetentionHint", "Exclude this DM session from automatic cleanup.");
      const deleteTitle = t("admin.deleteDmThreadHint", "Delete this DM session, including metadata, messages, and uploads.");
      const openTitle = state.privateChatContentAccess ? t("admin.openDmAudit", "Open this DM session in read-only audit view.") : t("admin.noContentAccess", "Message contents are not accessible from this view.");
      const openAttrs = state.privateChatContentAccess ? ` data-dm-admin-open-thread="${esc(item.id || "")}" role="button" tabindex="0"` : "";
      const openClass = state.privateChatContentAccess ? " kwc-admin-meta-open" : "";
      return `<div class="kwc-dm-thread kwc-admin-meta-row${openClass}"${openAttrs} title="${esc(openTitle)}"><span class="kwc-dm-thread-name">🛡 ${directMessageAdminIdentityHtml(item)}</span><span class="kwc-admin-meta-actions"><button type="button" class="kwc-button" data-dm-admin-lock-thread="${esc(item.id || "")}" data-next-locked="${item.locked ? "false" : "true"}" title="${esc(lockTitle)}" aria-label="${esc(lockTitle)}">${esc(lockLabel)}</button><button type="button" class="kwc-button" data-dm-admin-retention-thread="${esc(item.id || "")}" data-next-exempt="${item.retentionExempt ? "false" : "true"}" title="${esc(exemptTitle)}" aria-label="${esc(exemptTitle)}">${esc(exemptLabel)}</button><button type="button" class="kwc-button kwc-admin-meta-danger" data-dm-admin-delete-thread="${esc(item.id || "")}" title="${esc(deleteTitle)}" aria-label="${esc(deleteTitle)}">${esc(t("admin.deleteThread", "Delete"))}</button></span><span class="kwc-dm-thread-preview" title="${esc(meta.replace(/<[^>]*>/g, ""))}">${meta}</span></div>`;
    }).join("") : "";
    const previewHtml = state.privateChatSuperAdmin ? cleanupPreviewHtml(state.dmCleanupPreview, "dm") : "";
    list.innerHTML = userHtml + previewHtml + adminHtml;
    list.querySelectorAll("[data-dm-thread]").forEach(btn => {
      btn.addEventListener("click", event => {
        if (event && event.target && event.target.closest && event.target.closest(senderIdentitySelector())) return;
        state.dmDraftTarget = null;
        state.dmAuditMode = false;
        state.dmAuditThread = null;
        state.dmActiveThreadId = btn.dataset.dmThread || "";
        clearPrivateReply("dm");
        updateDirectMessageComposeControls();
        renderDirectMessageThreads();
        updateDirectMessageViewMode();
        loadDirectMessageMessages(state.dmActiveThreadId);
      });
    });
    list.querySelectorAll("[data-dm-admin-open-thread]").forEach(row => {
      const openAudit = event => {
        if (event && event.target && event.target.closest && (event.target.closest("button") || event.target.closest(senderIdentitySelector()))) return;
        if (event && event.type === "keydown" && event.key !== "Enter" && event.key !== " ") return;
        if (event) { event.preventDefault(); event.stopPropagation(); }
        const threadId = row.dataset.dmAdminOpenThread || "";
        const item = adminThreads.find(candidate => String(candidate.id || "") === threadId);
        if (!threadId || !item || !state.privateChatContentAccess) return;
        state.dmDraftTarget = null;
        state.dmAuditMode = true;
        state.dmAuditThread = item;
        state.dmActiveThreadId = threadId;
        clearPrivateReply("dm");
        updateDirectMessageComposeControls();
        renderDirectMessageThreads();
        updateDirectMessageViewMode();
        loadDirectMessageMessages(threadId);
      };
      row.addEventListener("click", openAudit);
      row.addEventListener("keydown", openAudit);
    });
    list.querySelectorAll("[data-dm-admin-lock-thread]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        setAdminSessionFlag("dm", btn.dataset.dmAdminLockThread || "", {locked: btn.dataset.nextLocked === "true"});
      });
    });
    list.querySelectorAll("[data-dm-admin-retention-thread]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        setAdminSessionFlag("dm", btn.dataset.dmAdminRetentionThread || "", {retentionExempt: btn.dataset.nextExempt === "true"});
      });
    });
    list.querySelectorAll("[data-dm-admin-delete-thread]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        deleteAdminDmThread(btn.dataset.dmAdminDeleteThread || "");
      });
    });
    installSenderIdentityToggle(list);
    updateDirectMessageViewMode();
  }

  function renderDirectMessageHeader(label) {
    const title = document.getElementById("kwc-dm-title");
    if (!title) return;
    const thread = state.dmActiveThreadId ? (state.dmThreads || []).find(t => t.id === state.dmActiveThreadId) : null;
    const target = thread || state.dmDraftTarget || null;
    const value = label || (target ? directMessageLabel(target) : t("dm.selectThread", "Select a thread"));
    if (state.dmAuditMode && state.dmAuditThread) {
      title.innerHTML = `<span class="kwc-admin-meta-title">🛡 ${esc(t("admin.dmAuditView", "DM audit (read-only)"))}</span> ${directMessageAdminIdentityHtml(state.dmAuditThread)}`;
      title.dataset.dmPlainTitle = directMessagePlainLabel(value);
    } else if (target) {
      title.innerHTML = directMessageHeaderIdentityHtml(target, "kwc-dm-title-name");
      title.dataset.dmPlainTitle = directMessageHeaderPlainLabel(target, value);
    } else {
      title.innerHTML = directMessageLabelHtml(value);
      title.dataset.dmPlainTitle = directMessagePlainLabel(value);
    }
    installSenderIdentityToggle(title);
    updateDirectMessageViewMode();
  }

  function privateMessagePageLimit() {
    return 100;
  }

  function privateMessageIdValue(msg) {
    const n = Number(msg && msg.id);
    return Number.isFinite(n) ? n : 0;
  }

  function privateMessageOldestId(messages) {
    const arr = Array.isArray(messages) ? messages : [];
    let min = 0;
    arr.forEach(msg => {
      const id = privateMessageIdValue(msg);
      if (id > 0 && (min <= 0 || id < min)) min = id;
    });
    return min;
  }

  function privateMessageNewestId(messages) {
    const arr = Array.isArray(messages) ? messages : [];
    let max = 0;
    arr.forEach(msg => {
      const id = privateMessageIdValue(msg);
      if (id > max) max = id;
    });
    return max;
  }

  function mergePrivateMessagePages(older, current) {
    const seen = new Set();
    const out = [];
    (Array.isArray(older) ? older : []).concat(Array.isArray(current) ? current : []).forEach(msg => {
      const key = String(msg && msg.id || "");
      if (!key || seen.has(key)) return;
      seen.add(key);
      out.push(msg);
    });
    out.sort((a, b) => privateMessageIdValue(a) - privateMessageIdValue(b));
    return out;
  }

  function privateClientMessageId(prefix = "msg") {
    try {
      if (window.crypto && typeof window.crypto.randomUUID === "function") return prefix + "-" + window.crypto.randomUUID();
    } catch (_) {}
    return prefix + "-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 14);
  }

  function privateDeliveryStatusHtml(msg, mine, type = "dm") {
    if (!mine || !msg) return "";
    const status = String(msg.deliveryStatus || "delivered").toLowerCase();
    if (status === "pending") {
      return `<span class="kwc-delivery-status kwc-delivery-pending">${esc(t("delivery.pending", "Sending"))}</span>`;
    }
    if (status === "failed") {
      const error = String(msg.deliveryError || "").trim();
      const attr = type === "group" ? "data-group-retry-message" : "data-dm-retry-message";
      return `<span class="kwc-delivery-status kwc-delivery-failed"${error ? ` title="${esc(error)}"` : ""}><span>${esc(t("delivery.failed", "Failed"))}</span><button type="button" class="kwc-delivery-retry" ${attr}="${esc(msg.id || "")}">${esc(t("delivery.retry", "Retry"))}</button></span>`;
    }
    return "";
  }

  function privateReadReceiptHtml(msg, type = "dm") {
    if (!msg) return "";
    const status = String(msg.deliveryStatus || "delivered").toLowerCase();
    if (status === "pending" || status === "failed") return "";
    if (type === "dm") {
      const count = Number.isFinite(Number(msg.unreadRecipientCount))
        ? Math.max(0, Number(msg.unreadRecipientCount))
        : (msg.readByOther === true ? 0 : 1);
      if (count <= 0) {
        const label = t("receipt.read", "Read");
        return `<span class="kwc-read-receipt kwc-read-receipt-dm" title="${esc(label)}" aria-label="${esc(label)}">✓</span>`;
      }
      const label = t("receipt.unread", "Unread");
      return `<span class="kwc-read-receipt kwc-read-receipt-dm" title="${esc(label)}" aria-label="${esc(label)}">${esc(label)}</span>`;
    }
    if (type === "group") {
      const count = Math.max(0, Number(msg.unreadMemberCount || 0));
      if (count <= 0) {
        const label = t("receipt.readAll", "Read by everyone");
        return `<span class="kwc-read-receipt kwc-read-receipt-group" title="${esc(label)}" aria-label="${esc(label)}">✓</span>`;
      }
      const label = fmt("receipt.unreadCount", "{count} people have not read this message", {count: String(count)});
      return `<span class="kwc-read-receipt kwc-read-receipt-group" title="${esc(label)}" aria-label="${esc(label)}">${esc(String(count))}</span>`;
    }
    return "";
  }

  function privateMessageMetaStatusHtml(msg, mine, type = "dm") {
    const delivery = privateDeliveryStatusHtml(msg, mine, type);
    const receipt = privateReadReceiptHtml(msg, type);
    if (!delivery && !receipt) return "";
    return `<span class="kwc-private-meta-status">${delivery}${receipt}</span>`;
  }

  function directMessageOptimisticMessage(clientMessageId, message, requestBody, replyTarget = null) {
    return {
      id: "local-dm-" + clientMessageId,
      threadId: state.dmActiveThreadId || "",
      senderUuid: "",
      senderUsername: state.username || "",
      senderDisplayName: state.username || "",
      body: message,
      time: Date.now(),
      deliveryStatus: "pending",
      deliveryError: "",
      replyToId: replyTarget && replyTarget.id ? replyTarget.id : 0,
      replyToSender: replyTarget && replyTarget.sender ? replyTarget.sender : "",
      replyToPreview: replyTarget && replyTarget.preview ? replyTarget.preview : "",
      clientMessageId,
      _kwcDmRequestBody: Object.assign({}, requestBody || {})
    };
  }

  function updateOptimisticDirectMessage(clientMessageId, status, error = "") {
    const item = (state.dmMessages || []).find(msg => String(msg && msg.clientMessageId || "") === String(clientMessageId || ""));
    if (!item) return false;
    item.deliveryStatus = status;
    item.deliveryError = error || "";
    renderDirectMessageMessages(state.dmMessages, {stickToBottom: true});
    return true;
  }

  async function applyDirectMessageSendResponse(res) {
    if (res && res.thread && res.thread.id) {
      state.dmActiveThreadId = res.thread.id;
      state.dmDraftTarget = null;
    }
    await loadDirectMessageThreads(true);
    if (state.dmActiveThreadId) await loadDirectMessageMessages(state.dmActiveThreadId);
  }

  async function sendDirectMessageAttempt(requestBody, clientMessageId) {
    try {
      const res = await api("/dm/send", {method: "POST", body: JSON.stringify(requestBody)});
      await applyDirectMessageSendResponse(res);
      return true;
    } catch (e) {
      const response = e && e.response || {};
      updateOptimisticDirectMessage(clientMessageId, "failed", responseError(response, e.message || "send_failed"));
      return false;
    }
  }

  async function retryDirectMessageDelivery(messageId) {
    messageId = String(messageId || "").trim();
    if (!messageId || !state.token) return;

    // A local optimistic ID means the request to this KWC server itself was
    // uncertain. Re-submit the exact same clientMessageId so the server can
    // return the already-created message instead of creating a duplicate.
    if (messageId.startsWith("local-dm-")) {
      const item = (state.dmMessages || []).find(msg => String(msg && msg.id || "") === messageId);
      const clientMessageId = String(item && item.clientMessageId || "").trim();
      const requestBody = item && item._kwcDmRequestBody ? Object.assign({}, item._kwcDmRequestBody) : null;
      if (!item || !clientMessageId || !requestBody) return;
      item.deliveryStatus = "pending";
      item.deliveryError = "";
      renderDirectMessageMessages(state.dmMessages, {stickToBottom: true});
      await sendDirectMessageAttempt(requestBody, clientMessageId);
      return;
    }

    try {
      await api("/dm/retry", {method: "POST", body: JSON.stringify({messageId})});
      if (state.dmActiveThreadId) await loadDirectMessageMessages(state.dmActiveThreadId);
    } catch (e) {
      alertResponse("alert.dmRetryFailed", "Failed to retry message: {error}", e.response || {error: e.message || "error"});
    }
  }

  function privateReplyState(type = "dm") {
    return type === "group" ? state.groupReplyTarget : state.dmReplyTarget;
  }

  function setPrivateReplyState(type, value) {
    if (type === "group") state.groupReplyTarget = value;
    else state.dmReplyTarget = value;
  }

  function privateReplyConversationId(type = "dm") {
    return type === "group" ? String(state.groupActiveRoomId || "") : String(state.dmActiveThreadId || "");
  }

  function privateReplyTargetFromMessage(msg, type = "dm") {
    if (!msg || !/^\d+$/.test(String(msg.id || ""))) return null;
    const sender = String(msg.senderDisplayName || msg.senderUsername || msg.senderUuid || t("sender.unknown", "Unknown"));
    const preview = String(msg.body || "");
    return {id: Number(msg.id), sender, preview, conversationId: privateReplyConversationId(type)};
  }

  function renderPrivateReplyCompose(type = "dm") {
    const prefix = type === "group" ? "kwc-group" : "kwc-dm";
    const wrap = document.getElementById(prefix + "-reply-compose");
    if (!wrap) return;
    const target = privateReplyState(type);
    const valid = !!(target && target.id && String(target.conversationId || "") === privateReplyConversationId(type));
    if (!valid && target) setPrivateReplyState(type, null);
    wrap.classList.toggle("kwc-hidden", !valid);
    const label = document.getElementById(prefix + "-reply-compose-label");
    const preview = document.getElementById(prefix + "-reply-compose-preview");
    if (label) label.innerHTML = valid ? formatReplyComposeLabelHtml(target.sender || "") : "";
    if (preview) preview.innerHTML = valid ? replyPreviewHtml(target.preview || "") : "";
  }

  function startPrivateReply(msg, type = "dm") {
    if ((type === "group" && state.groupAuditMode) || (type === "dm" && state.dmAuditMode)) return;
    const target = privateReplyTargetFromMessage(msg, type);
    if (!target) return;
    setPrivateReplyState(type, target);
    renderPrivateReplyCompose(type);
    const input = document.getElementById(type === "group" ? "kwc-group-input" : "kwc-dm-input");
    if (input) input.focus();
  }

  function clearPrivateReply(type = "dm") {
    setPrivateReplyState(type, null);
    renderPrivateReplyCompose(type);
  }

  function privateReplyReferenceHtml(msg, type = "dm") {
    if (!msg || !(Number(msg.replyToId || 0) > 0)) return "";
    const sender = msg.replyToSender || t("sender.unknown", "Unknown");
    const preview = msg.replyToPreview || "";
    const plainSender = plainLegacyText(sender).trim() || t("sender.unknown", "Unknown");
    const plainPreview = plainLegacyText(preview).replace(/[\r\n]+/g, " ").trim();
    const titlePreview = plainPreview.length > 240 ? plainPreview.slice(0, 237) + "..." : plainPreview;
    const title = titlePreview ? t("reply.jump", "Jump to replied message") + ": " + plainSender + " - " + titlePreview : t("reply.jump", "Jump to replied message");
    return `<button type="button" class="kwc-reply-ref kwc-private-reply-ref" data-private-reply-jump="${esc(msg.replyToId)}" data-private-reply-type="${type}" title="${esc(title)}"><span class="kwc-reply-ref-sender">${minecraftLegacyTextHtml(sender, true)}</span><span class="kwc-reply-ref-preview">${replyPreviewHtml(preview)}</span></button>`;
  }

  function privateReplySignature(msg) {
    return [Number(msg && msg.replyToId || 0), String(msg && msg.replyToSender || ""), String(msg && msg.replyToPreview || "")].join("|");
  }

  function privateReplyJumpKeys(type = "dm") {
    return type === "group"
      ? {generation: "groupReplyJumpGeneration", startedAt: "groupReplyJumpStartedAt", lastCentered: "groupReplyJumpLastCenteredScrollTop", timer: "groupReplyJumpStabilizeTimer"}
      : {generation: "dmReplyJumpGeneration", startedAt: "dmReplyJumpStartedAt", lastCentered: "dmReplyJumpLastCenteredScrollTop", timer: "dmReplyJumpStabilizeTimer"};
  }

  function cancelPrivateReplyJump(type = "dm") {
    const keys = privateReplyJumpKeys(type);
    clearTimeout(state[keys.timer]);
    state[keys.timer] = null;
    state[keys.generation] = Number(state[keys.generation] || 0) + 1;
    state[keys.startedAt] = 0;
    state[keys.lastCentered] = NaN;
  }

  function privateReplyMessageElement(box, messageId, type = "dm") {
    if (!box) return null;
    const attr = type === "group" ? "data-group-message-id" : "data-dm-message-id";
    const id = Number(messageId || 0);
    if (!(id > 0)) return null;
    return Array.from(box.querySelectorAll(`[${attr}]`)).find(node => Number(node.getAttribute(attr) || 0) === id) || null;
  }

  function centerPrivateReplyMessage(box, el, type = "dm", highlight = true) {
    if (!box || !el) return false;
    let desired = Number(box.scrollTop || 0);
    try {
      const boxRect = box.getBoundingClientRect();
      const elRect = el.getBoundingClientRect();
      const centerOffset = Math.max(0, (Number(box.clientHeight || 0) - Number(elRect.height || 0)) / 2);
      desired = desired + (elRect.top - boxRect.top) - centerOffset;
    } catch (_) {}
    box.scrollTop = Math.max(0, desired);
    const keys = privateReplyJumpKeys(type);
    state[keys.lastCentered] = Number(box.scrollTop || 0);
    if (highlight) highlightMessageElement(el);
    return true;
  }

  function stabilizePrivateReplyJump(messageId, type, generation) {
    const keys = privateReplyJumpKeys(type);
    clearTimeout(state[keys.timer]);
    const delays = [0, 60, 160, 360, 720];
    let pos = 0;
    const run = () => {
      if (Number(state[keys.generation] || 0) !== generation) return;
      const box = document.getElementById(type === "group" ? "kwc-group-messages" : "kwc-dm-messages");
      if (!box) return;
      const lastCentered = Number(state[keys.lastCentered]);
      if (pos > 0 && Number.isFinite(lastCentered)) {
        const drift = Math.abs(Number(box.scrollTop || 0) - lastCentered);
        if (drift > Math.max(28, Math.round(Math.max(1, Number(box.clientHeight || 1)) * 0.07))) {
          cancelPrivateReplyJump(type);
          return;
        }
      }
      const el = privateReplyMessageElement(box, messageId, type);
      if (el) {
        const boxRect = box.getBoundingClientRect();
        const elRect = el.getBoundingClientRect();
        const boxCenter = boxRect.top + Math.max(1, boxRect.height || box.clientHeight || 1) / 2;
        const elCenter = elRect.top + Math.max(1, elRect.height || 1) / 2;
        if (pos <= 1 || Math.abs(elCenter - boxCenter) > 22) centerPrivateReplyMessage(box, el, type, pos <= 1);
      }
      pos++;
      if (pos < delays.length && Number(state[keys.generation] || 0) === generation) {
        state[keys.timer] = setTimeout(run, delays[pos]);
      } else {
        state[keys.timer] = null;
      }
    };
    state[keys.timer] = setTimeout(run, delays[0]);
  }

  async function jumpToPrivateReplyTarget(messageId, type = "dm") {
    const id = Number(messageId || 0);
    if (!(id > 0)) return;
    const messages = () => type === "group" ? (state.groupMessages || []) : (state.dmMessages || []);
    const hasMore = () => type === "group" ? !!state.groupMessagesHasMore : !!state.dmMessagesHasMore;
    const box = document.getElementById(type === "group" ? "kwc-group-messages" : "kwc-dm-messages");
    if (!box) return;

    cancelPrivateReplyJump(type);
    if (type === "group") {
      state.groupEdgePendingTopUntil = 0;
      state.groupEdgePendingBottomUntil = 0;
      hideGroupChatEdgeToast(true);
    } else {
      state.dmEdgePendingTopUntil = 0;
      state.dmEdgePendingBottomUntil = 0;
      hideDirectMessageEdgeToast(true);
    }

    let found = messages().find(msg => Number(msg && msg.id || 0) === id) || null;
    let rounds = 0;
    while (!found && hasMore() && rounds++ < 40) {
      const loaded = type === "group"
        ? await loadOlderGroupChatMessagesFromEdge(box, "reply-jump")
        : await loadOlderDirectMessageMessagesFromEdge(box, "reply-jump");
      found = messages().find(msg => Number(msg && msg.id || 0) === id) || null;
      if (!loaded) break;
      const oldest = privateMessageOldestId(messages());
      if (oldest > 0 && oldest <= id && !found) break;
    }
    if (!found) { alert(t("reply.notFound", "The referenced message could not be found.")); return; }

    const el = privateReplyMessageElement(box, id, type);
    if (!el) { alert(t("reply.notFound", "The referenced message could not be found.")); return; }
    const keys = privateReplyJumpKeys(type);
    const generation = Number(state[keys.generation] || 0) + 1;
    state[keys.generation] = generation;
    state[keys.startedAt] = Date.now();
    centerPrivateReplyMessage(box, el, type, true);
    stabilizePrivateReplyJump(id, type, generation);
  }

  function privateMessageDomKey(msg, type = "dm") {
    const clientMessageId = String(msg && msg.clientMessageId || "").trim();
    if (clientMessageId) return type + ":client:" + clientMessageId;
    const id = String(msg && msg.id || "").trim();
    if (id) return type + ":id:" + id;
    return type + ":fallback:" + [String(msg && msg.time || ""), String(msg && msg.senderUuid || msg && msg.senderUsername || ""), String(msg && msg.body || "")].join("|");
  }

  function privateMessageNearBottom(box) {
    if (!box) return true;
    return Number(box.scrollHeight || 0) - Number(box.scrollTop || 0) - Number(box.clientHeight || 0) <= 56;
  }

  function privateMessageMetaHtml(msg, mine, type = "dm") {
    const sender = msg.senderDisplayName || msg.senderUsername || msg.senderUuid || "";
    const senderIdentity = {senderDisplayName: sender, senderUsername: msg.senderUsername || "", senderUuid: msg.senderUuid || ""};
    const statusHtml = type === "dm"
      ? (state.dmAuditMode ? "" : privateMessageMetaStatusHtml(msg, mine, "dm"))
      : (state.groupAuditMode ? "" : privateMessageMetaStatusHtml(msg, mine, "group"));
    return `${directMessageIdentityHtml(senderIdentity, "kwc-sender")}<span class="kwc-meta-sep" aria-hidden="true">·</span><span class="kwc-time-actions"><span class="kwc-time" data-time="${esc(msg.time || "")}" title="${esc(timeToggleTitle(msg.time))}" role="button" tabindex="0">${esc(formatMessageTime(msg.time))}</span>${privateReplyActionHtml(msg, type)}${statusHtml}</span>`;
  }

  function privateReplyActionHtml(msg, type = "dm") {
    const rawMessageId = String(msg && msg.id || "");
    const persisted = /^\d+$/.test(rawMessageId);
    const canReply = persisted && !(type === "group" ? state.groupAuditMode : state.dmAuditMode);
    if (!canReply) return "";
    return `<span class="kwc-mini-actions"><button type="button" class="kwc-mini-action kwc-reply-action" data-private-reply-message="${esc(rawMessageId)}" data-private-reply-type="${type}">${esc(t("button.reply", "reply"))}</button></span>`;
  }

  function groupMembershipEventText(msg) {
    const player = directMessagePlainLabel(msg && (msg.senderDisplayName || msg.senderUsername || msg.senderUuid) || "");
    if (String(msg && msg.eventType || "") === "member_leave") {
      return fmt("group.memberLeft", "{player} left the room.", {player});
    }
    return fmt("group.memberJoined", "{player} joined the room.", {player});
  }

  function createPrivateMessageElement(msg, type = "dm") {
    const mine = !!(state.username && msg.senderUsername && String(msg.senderUsername).toLowerCase() === String(state.username).toLowerCase());
    const rawMessageId = String(msg.id || "");
    const body = String(msg.body || "");
    const eventType = type === "group" ? String(msg.eventType || "") : "";
    const el = document.createElement("div");
    if (eventType === "member_join" || eventType === "member_leave") {
      el.className = "kwc-msg kwc-group-message kwc-group-membership-event";
      el.dataset.kwcPrivateMessageKey = privateMessageDomKey(msg, type);
      el.dataset.kwcPrivateBody = body;
      el.dataset.kwcPrivateEventType = eventType;
      el.dataset.groupMessageId = rawMessageId;
      el.innerHTML = `<span class="kwc-group-membership-event-text">${esc(groupMembershipEventText(msg))}</span><span class="kwc-group-membership-event-time kwc-time" data-time="${esc(msg.time || "")}" title="${esc(timeToggleTitle(msg.time))}" role="button" tabindex="0">${esc(formatMessageTime(msg.time))}</span>`;
      return el;
    }
    el.className = `kwc-msg kwc-dm-message${type === "group" ? " kwc-group-message" : ""}${mine ? " kwc-mine" : ""}`;
    el.dataset.kwcPrivateMessageKey = privateMessageDomKey(msg, type);
    el.dataset.kwcPrivateBody = body;
    if (type === "group") el.dataset.groupMessageId = rawMessageId;
    else el.dataset.dmMessageId = rawMessageId;
    const persisted = /^\d+$/.test(rawMessageId);
    const hideButton = type === "group"
      ? (!state.groupAuditMode && persisted ? `<button type="button" class="kwc-dm-message-hide" data-group-hide-message="${esc(rawMessageId)}" title="${esc(t("dm.hideMessage", "Hide this message"))}">×</button>` : "")
      : (state.dmAuditMode ? "" : `<button type="button" class="kwc-dm-message-hide" data-dm-hide-message="${esc(rawMessageId)}" title="${esc(t("dm.hideMessage", "Hide this message"))}" aria-label="${esc(t("dm.hideMessage", "Hide this message"))}">×</button>`);
    el.dataset.kwcPrivateReplySignature = privateReplySignature(msg);
    el.innerHTML = `<div class="kwc-meta kwc-dm-message-meta">${privateMessageMetaHtml(msg, mine, type)}</div>${hideButton}${privateReplyReferenceHtml(msg, type)}<div class="kwc-text kwc-dm-message-body">${directMessageBodyHtml(body)}</div>${directMessagePreviewHtml(body, rawMessageId, type)}`;
    return el;
  }

  function syncPrivateMessageElement(el, msg, type = "dm") {
    if (!el || !msg) return el;
    const eventType = type === "group" ? String(msg.eventType || "") : "";
    if (eventType === "member_join" || eventType === "member_leave") {
      if (String(el.dataset.kwcPrivateEventType || "") !== eventType) {
        const replacement = createPrivateMessageElement(msg, type);
        el.replaceWith(replacement);
        return replacement;
      }
      const text = el.querySelector(":scope > .kwc-group-membership-event-text");
      if (text) text.textContent = groupMembershipEventText(msg);
      const time = el.querySelector(":scope > .kwc-group-membership-event-time");
      if (time) {
        time.dataset.time = String(msg.time || "");
        time.title = timeToggleTitle(msg.time);
        time.textContent = formatMessageTime(msg.time);
      }
      return el;
    }
    if (el.dataset.kwcPrivateEventType) {
      const replacement = createPrivateMessageElement(msg, type);
      el.replaceWith(replacement);
      return replacement;
    }
    const mine = !!(state.username && msg.senderUsername && String(msg.senderUsername).toLowerCase() === String(state.username).toLowerCase());
    el.classList.toggle("kwc-mine", mine);
    const rawMessageId = String(msg.id || "");
    if (type === "group") el.dataset.groupMessageId = rawMessageId;
    else el.dataset.dmMessageId = rawMessageId;
    const meta = el.querySelector(":scope > .kwc-dm-message-meta");
    if (meta) meta.innerHTML = privateMessageMetaHtml(msg, mine, type);
    // Private chat message bodies are immutable after storage. Do not rebuild the
    // body/preview on delivery/read refreshes: a loaded video/audio element must
    // remain mounted in exactly the same message DOM node, like public chat.
    const body = String(msg.body || "");
    if (String(el.dataset.kwcPrivateBody || "") !== body || String(el.dataset.kwcPrivateReplySignature || "") !== privateReplySignature(msg)) {
      const replacement = createPrivateMessageElement(msg, type);
      el.replaceWith(replacement);
      return replacement;
    }
    return el;
  }

  function installPrivateMessageActions(root, type = "dm") {
    if (!root) return;
    root.querySelectorAll("[data-private-reply-message]").forEach(btn => {
      if (btn.dataset.kwcPrivateReplyInstalled === "1") return;
      btn.dataset.kwcPrivateReplyInstalled = "1";
      btn.addEventListener("click", event => {
        event.preventDefault(); event.stopPropagation();
        const buttonType = btn.dataset.privateReplyType === "group" ? "group" : "dm";
        const arr = buttonType === "group" ? state.groupMessages : state.dmMessages;
        const msg = (arr || []).find(item => String(item && item.id || "") === String(btn.dataset.privateReplyMessage || ""));
        startPrivateReply(msg, buttonType);
      });
    });
    root.querySelectorAll("[data-private-reply-jump]").forEach(btn => {
      if (btn.dataset.kwcPrivateReplyJumpInstalled === "1") return;
      btn.dataset.kwcPrivateReplyJumpInstalled = "1";
      btn.addEventListener("click", event => {
        event.preventDefault(); event.stopPropagation();
        jumpToPrivateReplyTarget(btn.dataset.privateReplyJump || "", btn.dataset.privateReplyType === "group" ? "group" : "dm");
      });
    });
    if (type === "group") {
      root.querySelectorAll("[data-group-hide-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); hideGroupMessage(btn.dataset.groupHideMessage || ""); });
      });
      root.querySelectorAll("[data-group-retry-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); retryGroupChatMessage(btn.dataset.groupRetryMessage || ""); });
      });
    } else {
      root.querySelectorAll("[data-dm-hide-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); hideDirectMessageForMe(btn.dataset.dmHideMessage || ""); });
      });
      root.querySelectorAll("[data-dm-retry-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); retryDirectMessageDelivery(btn.dataset.dmRetryMessage || ""); });
      });
    }
  }

  function discardPrivateMessageDom(box) {
    if (!box) return;
    // Leaving/switching a private conversation is a hard boundary: destroy the
    // mounted message/media DOM and forget click-to-load expansion state for
    // media that belonged to that conversation. Do not pause/reparent/restore
    // players; a later re-entry must create a completely new, unopened DOM.
    box.querySelectorAll("[data-preview-key]").forEach(node => {
      const key = String(node.dataset && node.dataset.previewKey || "");
      if (key) state.mediaOpen.delete(key);
    });
    box.querySelectorAll("[data-youtube-key]").forEach(node => {
      const key = String(node.dataset && node.dataset.youtubeKey || "");
      if (!key) return;
      state.youtubeOpen.delete(key);
      state.youtubeExpanded.delete(key);
    });
    box.replaceChildren();
    box.removeAttribute("data-kwc-private-media-conversation");
    box.scrollTop = 0;
  }

  function reconcilePrivateMessageList(box, messages, type, conversationKey, auditNoticeHtml, emptyHtml) {
    const arr = Array.isArray(messages) ? messages : [];
    const sameConversation = String(box.dataset.kwcPrivateMediaConversation || "") === String(conversationKey || "");
    const wasNearBottom = sameConversation ? privateMessageNearBottom(box) : true;
    if (!sameConversation) {
      // Conversation changes intentionally discard the old DOM and its
      // click-to-load/open state. Same-conversation refreshes never
      // clear/reparent an existing message/media node.
      discardPrivateMessageDom(box);
      box.dataset.kwcPrivateMediaConversation = String(conversationKey || "");
    }

    let notice = box.querySelector(":scope > .kwc-admin-audit-notice");
    if (auditNoticeHtml) {
      if (!notice) {
        const holder = document.createElement("div");
        holder.innerHTML = auditNoticeHtml;
        notice = holder.firstElementChild;
        if (notice) box.insertBefore(notice, box.firstChild);
      } else if (notice.outerHTML !== auditNoticeHtml) {
        const holder = document.createElement("div");
        holder.innerHTML = auditNoticeHtml;
        const fresh = holder.firstElementChild;
        if (fresh) { notice.replaceWith(fresh); notice = fresh; }
      }
    } else if (notice) {
      notice.remove();
      notice = null;
    }

    const empty = box.querySelector(":scope > .kwc-dm-empty");
    if (!arr.length) {
      box.querySelectorAll(":scope > .kwc-msg[data-kwc-private-message-key]").forEach(el => el.remove());
      if (empty) empty.outerHTML = emptyHtml;
      else {
        const holder = document.createElement("div");
        holder.innerHTML = emptyHtml;
        const node = holder.firstElementChild;
        if (node) box.appendChild(node);
      }
      return {sameConversation, wasNearBottom};
    }
    if (empty) empty.remove();

    const renderedByKey = new Map();
    box.querySelectorAll(":scope > .kwc-msg[data-kwc-private-message-key]").forEach(el => {
      const key = String(el.dataset.kwcPrivateMessageKey || "");
      if (key) renderedByKey.set(key, el);
    });
    const desiredKeys = new Set(arr.map(msg => privateMessageDomKey(msg, type)));
    renderedByKey.forEach((el, key) => {
      if (!desiredKeys.has(key)) { el.remove(); renderedByKey.delete(key); }
    });

    const findNextExisting = fromIndex => {
      for (let j = fromIndex + 1; j < arr.length; j++) {
        const next = renderedByKey.get(privateMessageDomKey(arr[j], type));
        if (next && next.parentNode === box) return next;
      }
      return null;
    };

    for (let i = 0; i < arr.length; i++) {
      const msg = arr[i];
      const key = privateMessageDomKey(msg, type);
      let el = renderedByKey.get(key);
      if (el) {
        el = syncPrivateMessageElement(el, msg, type);
        renderedByKey.set(key, el);
        continue;
      }
      el = createPrivateMessageElement(msg, type);
      const before = findNextExisting(i);
      if (before) box.insertBefore(el, before);
      else box.appendChild(el);
      renderedByKey.set(key, el);
    }
    box.dataset.kwcPrivateMediaConversation = String(conversationKey || "");
    hydrateDirectMessageRenderedContent(box);
    installSenderIdentityToggle(box);
    installTimeToggle(box);
    installPrivateMessageActions(box, type);
    return {sameConversation, wasNearBottom};
  }

  function renderDirectMessageMessages(messages, options = {}) {
    const box = document.getElementById("kwc-dm-messages");
    if (!box) return;
    renderPrivateReplyCompose("dm");
    hideDirectMessageEdgeToast(true);
    const arr = Array.isArray(messages) ? messages : [];
    const prevTop = Number(options.previousScrollTop != null ? options.previousScrollTop : box.scrollTop || 0);
    const prevHeight = Number(options.previousScrollHeight != null ? options.previousScrollHeight : box.scrollHeight || 0);
    if (!state.dmActiveThreadId && !state.dmDraftTarget) {
      state.dmMessages = [];
      state.dmMessagesHasMore = false;
      discardPrivateMessageDom(box);
      const empty = document.createElement("div");
      empty.className = "kwc-dm-empty";
      empty.textContent = t("dm.selectThread", "Select a thread");
      box.appendChild(empty);
      renderDirectMessageHeader("");
      return;
    }
    const conversationKey = "dm:" + String(state.dmActiveThreadId || (state.dmDraftTarget && state.dmDraftTarget.uuid) || "");
    const auditNotice = state.dmAuditMode
      ? `<div class="kwc-admin-audit-notice">🛡 ${esc(t("admin.dmAuditReadOnly", "This administrator audit view is read-only. Every access is recorded in the audit log."))}</div>`
      : "";
    const result = reconcilePrivateMessageList(box, arr, "dm", conversationKey, auditNotice, `<div class="kwc-dm-empty">${esc(t("dm.emptyThread", "No messages yet."))}</div>`);
    if (options && options.preserveTop) {
      const delta = Math.max(0, Number(box.scrollHeight || 0) - prevHeight);
      box.scrollTop = prevTop + delta;
    } else if ((!options || options.stickToBottom !== false) && (!result.sameConversation || result.wasNearBottom)) {
      box.scrollTop = box.scrollHeight;
    }
  }

  function directMessageMessagesUrl(threadId, beforeId = 0, limit = 100) {
    const path = state.dmAuditMode ? "/admin/dm/messages" : "/dm/messages";
    let url = path + "?threadId=" + encodeURIComponent(threadId)
      + "&limit=" + encodeURIComponent(String(limit));
    if (Number(beforeId || 0) > 0) url += "&before=" + encodeURIComponent(String(beforeId));
    return url;
  }

  async function loadDirectMessageMessages(threadId) {
    if (!state.token || !threadId) return;
    if (state.dmMessagesLoading) return;
    state.dmMessagesLoading = true;
    try {
      const limit = privateMessagePageLimit();
      const res = await api(directMessageMessagesUrl(threadId, 0, limit));
      const thread = (state.dmThreads || []).find(t => t.id === threadId);
      const messages = Array.isArray(res.messages) ? res.messages : [];
      state.dmMessages = messages;
      state.dmMessagesHasMore = messages.length >= limit;
      renderDirectMessageHeader(thread ? directMessageLabel(thread) : "");
      renderDirectMessageMessages(state.dmMessages, {stickToBottom: true});
      if (!state.dmAuditMode) {
        state.dmUnread = Number(res.unread || 0);
        updateDirectMessageButton();
        await loadDirectMessageThreads(true);
      }
    } catch (e) {
      alertResponse(state.dmAuditMode ? "alert.dmAuditLoadFailed" : "alert.dmLoadFailed", state.dmAuditMode ? "Failed to load DM audit: {error}" : "Failed to load messages: {error}", e.response || {error: e.message || "error"});
    } finally {
      state.dmMessagesLoading = false;
    }
  }

  async function loadOlderDirectMessageMessagesFromEdge(box, reason = "") {
    if (!state.token || !state.dmActiveThreadId || state.dmMessagesLoading || !state.dmMessagesHasMore) return false;
    const oldest = privateMessageOldestId(state.dmMessages);
    if (!(oldest > 0)) {
      state.dmMessagesHasMore = false;
      return false;
    }
    if (!box) box = document.getElementById("kwc-dm-messages");
    const prevTop = box ? Number(box.scrollTop || 0) : 0;
    const prevHeight = box ? Number(box.scrollHeight || 0) : 0;
    state.dmMessagesLoading = true;
    try {
      const limit = privateMessagePageLimit();
      const res = await api(directMessageMessagesUrl(state.dmActiveThreadId, oldest, limit), {timeoutMs: 15000});
      const older = Array.isArray(res.messages) ? res.messages : [];
      const beforeCount = state.dmMessages.length;
      state.dmMessages = mergePrivateMessagePages(older, state.dmMessages);
      const added = Math.max(0, state.dmMessages.length - beforeCount);
      state.dmMessagesHasMore = older.length >= limit;
      if (added > 0) renderDirectMessageMessages(state.dmMessages, {preserveTop: true, previousScrollTop: prevTop, previousScrollHeight: prevHeight, stickToBottom: false});
      else state.dmMessagesHasMore = false;
      return added > 0;
    } catch (e) {
      return false;
    } finally {
      state.dmMessagesLoading = false;
      setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-older-loaded"), 80);
    }
  }

  async function retryDirectMessageLatestFromBottomEdge(box, reason = "") {
    if (!state.token || !state.dmActiveThreadId || state.dmMessagesLoading || state.dmBottomRetryInFlight) return false;
    const now = Date.now();
    if (now - Number(state.dmLastBottomRetryAt || 0) < 1200) return false;
    state.dmLastBottomRetryAt = now;
    state.dmBottomRetryInFlight = true;
    const beforeNewest = privateMessageNewestId(state.dmMessages);
    try {
      const limit = privateMessagePageLimit();
      const res = await api(directMessageMessagesUrl(state.dmActiveThreadId, 0, limit), {timeoutMs: 15000});
      const messages = Array.isArray(res.messages) ? res.messages : [];
      const afterNewest = privateMessageNewestId(messages);
      state.dmMessages = messages;
      state.dmMessagesHasMore = messages.length >= limit;
      renderDirectMessageMessages(state.dmMessages, {stickToBottom: true});
      if (!state.dmAuditMode && Number(res.unread || 0) >= 0) {
        state.dmUnread = Number(res.unread || 0);
        updateDirectMessageButton();
      }
      return afterNewest > beforeNewest;
    } catch (_) {
      return false;
    } finally {
      state.dmBottomRetryInFlight = false;
      setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-bottom-retried"), 100);
    }
  }

  async function hideDirectMessageForMe(messageId) {
    messageId = String(messageId || "").trim();
    if (!messageId || !state.token) return;
    if (state.directMessageConfirmHide && !confirmPlain(t("dm.confirmHideMessage", "Hide this message from your view?"))) return;
    try {
      const res = await api("/dm/hide-message", {method: "POST", body: JSON.stringify({messageId})});
      state.dmUnread = Number(res.unread || 0);
      updateDirectMessageButton();
      if (state.dmActiveThreadId) await loadDirectMessageMessages(state.dmActiveThreadId);
      await loadDirectMessageThreads(true);
      renderDirectMessageThreads();
    } catch (e) {
      alertResponse("alert.dmHideFailed", "Failed to hide message: {error}", e.response || {error: e.message || "error"});
    }
  }

  function syncDirectMessagePlayerSearchPanelSize() {
    const panel = document.getElementById("kwc-dm-search-panel");
    const modal = panel && panel.closest ? panel.closest(".kwc-dm-modal") : null;
    if (!panel || !modal) return;

    // This panel had an older compact-width rule with !important.  Apply the
    // full modal width only while the player search is open, using inline
    // priority so it reliably overrides the legacy 360px cap without adding a
    // broad global CSS rule that can interfere with the chat frame resize hit
    // zones.
    const sideGap = (Number(modal.getBoundingClientRect().width || 0) <= 360) ? 6 : 10;
    panel.style.setProperty("left", sideGap + "px", "important");
    panel.style.setProperty("right", sideGap + "px", "important");
    panel.style.setProperty("width", "auto", "important");
    panel.style.setProperty("max-width", "none", "important");
    panel.style.setProperty("box-sizing", "border-box", "important");
  }

  function resetDirectMessagePlayerSearchPanelSize() {
    const panel = document.getElementById("kwc-dm-search-panel");
    if (!panel) return;
    ["left", "right", "width", "max-width", "box-sizing"].forEach(name => panel.style.removeProperty(name));
  }

  function closeDirectMessagePlayerSearch() {
    state.dmSearchPanelOpen = false;
    const panel = document.getElementById("kwc-dm-search-panel");
    if (panel) panel.classList.add("kwc-hidden");
    resetDirectMessagePlayerSearchPanelSize();
  }

  function openDirectMessagePlayerSearch() {
    state.dmSearchPanelOpen = true;
    const panel = document.getElementById("kwc-dm-search-panel");
    const input = document.getElementById("kwc-dm-search");
    if (panel) {
      panel.classList.remove("kwc-hidden");
      syncDirectMessagePlayerSearchPanelSize();
    }
    if (input) {
      input.value = "";
      setTimeout(() => input.focus(), 0);
    }
    renderDirectMessagePlayers([]);
  }

  function closeDirectMessageEmojiPanel() {
    state.dmEmojiPanelOpen = false;
    const panel = document.getElementById("kwc-dm-emoji-panel");
    if (panel) {
      panel.classList.add("kwc-hidden");
      panel.hidden = true;
      panel.style.display = "none";
      panel.style.height = "0px";
      panel.style.minHeight = "0px";
      panel.style.maxHeight = "0px";
    }
    updateDirectMessageEmojiResizeHandleVisibility();
  }

  function toggleDirectMessageEmojiPanel() {
    if (!canUseCustomEmoji()) return;
    setActiveComposeInput("kwc-dm-input");
    state.dmEmojiPanelOpen = !state.dmEmojiPanelOpen;
    renderDirectMessageEmojiPanel();
  }

  function setDirectMessageEmojiPanelHeight(panel, px = null, persist = false, options = {}) {
    if (!panel) return 0;
    const minHeight = emojiPanelMinHeightPx(panel);
    const maxHeight = emojiPanelMaxHeightPx();
    let height = Math.round(Number(px == null ? emojiPanelHeightPx() : px) || emojiPanelHeightPx());
    height = Math.max(minHeight, Math.min(maxHeight, height));
    if (!options || options.snap !== false) {
      height = snapEmojiPanelHeightPx(height, panel);
    }
    state.emojiPanelHeightPx = height;
    if (persist) {
      try { localStorage.setItem("kwc.emojiPanelHeightPx", String(height)); } catch (_) {}
    }
    const root = document.getElementById("kwc-root");
    if (root) {
      root.style.setProperty("--kwc-emoji-panel-height", height + "px");
      root.style.setProperty("--kwc-emoji-panel-min-height", minHeight + "px");
    }
    const wrap = document.querySelector(".kwc-dm-modal-backdrop");
    if (wrap) {
      wrap.style.setProperty("--kwc-emoji-panel-height", height + "px");
      wrap.style.setProperty("--kwc-emoji-panel-min-height", minHeight + "px");
    }
    panel.hidden = false;
    panel.style.display = "flex";
    panel.style.height = height + "px";
    panel.style.maxHeight = height + "px";
    panel.style.minHeight = minHeight + "px";
    if (!options || options.snapScroll !== false) snapEmojiPanelScrollTop(panel);
    updateDirectMessageEmojiResizeHandleVisibility();
    return height;
  }


  function updateDirectMessageEmojiResizeHandleVisibility() {
    const handle = document.getElementById("kwc-dm-emoji-resize");
    if (!handle) return;
    const visible = !!(state.dmModalOpen && state.dmEmojiPanelOpen && canUseCustomEmoji());
    handle.classList.toggle("kwc-hidden", !visible);
    handle.hidden = !visible;
  }

  function installDirectMessageEmojiPanelResize(wrap) {
    const handle = document.getElementById("kwc-dm-emoji-resize");
    const panel = document.getElementById("kwc-dm-emoji-panel");
    if (!wrap || !handle || !panel || handle.dataset.kwcInstalled === "1") return;
    handle.dataset.kwcInstalled = "1";
    const pointY = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return Number(src.clientY) || 0;
    };
    const begin = event => {
      if (!state.dmEmojiPanelOpen || !canUseCustomEmoji()) return;
      event.preventDefault();
      event.stopPropagation();
      markNonScrollUiAction();
      state.emojiPanelResizeStart = {
        dm: true,
        y: pointY(event),
        height: Number(panel.getBoundingClientRect().height || emojiPanelHeightPx()),
        currentHeight: Number(panel.getBoundingClientRect().height || emojiPanelHeightPx())
      };
      document.body.classList.add("kwc-emoji-resizing");
      try { handle.setPointerCapture && event.pointerId != null && handle.setPointerCapture(event.pointerId); } catch (_) {}
    };
    const move = event => {
      const start = state.emojiPanelResizeStart;
      if (!start || !start.dm) return;
      event.preventDefault();
      event.stopPropagation();
      const delta = start.y - pointY(event);
      start.currentHeight = setDirectMessageEmojiPanelHeight(panel, start.height + delta, false, {snap: false, snapScroll: false});
    };
    const end = event => {
      const start = state.emojiPanelResizeStart;
      if (!start || !start.dm) return;
      event.preventDefault();
      event.stopPropagation();
      setDirectMessageEmojiPanelHeight(panel, start.currentHeight || emojiPanelHeightPx(), true, {snap: false, snapScroll: true});
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
    updateDirectMessageEmojiResizeHandleVisibility();
  }

  function directMessageEdgeToastThresholdPx(box) {
    return Math.max(2, Math.min(8, Math.floor(Number(box && box.clientHeight || 0) * 0.01)));
  }

  function directMessageEdgeAtTop(box) {
    return !!box && Number(box.scrollTop || 0) <= directMessageEdgeToastThresholdPx(box);
  }

  function directMessageEdgeAtBottom(box) {
    return !!box && bottomGapPx(box) <= directMessageEdgeToastThresholdPx(box);
  }

  function directMessageEdgeToastEligible(box, position = "top") {
    if (!state.dmModalOpen || !hasDirectMessageConversationOpen()) return false;
    if (!box || !box.querySelector || !box.querySelector(".kwc-dm-message")) return false;
    return position === "bottom" ? directMessageEdgeAtBottom(box) : directMessageEdgeAtTop(box);
  }

  function hideDirectMessageEdgeToast(clearPending = false) {
    if (state.dmEdgeToastTimer) {
      clearTimeout(state.dmEdgeToastTimer);
      state.dmEdgeToastTimer = null;
    }
    state.dmEdgeToastVisible = false;
    state.dmEdgeToastVisibleUntil = 0;
    if (clearPending) {
      state.dmEdgePendingTopUntil = 0;
      state.dmEdgePendingBottomUntil = 0;
      state.dmEdgeBottomExtraScrollCount = 0;
    }
    const toast = document.getElementById("kwc-dm-edge-toast");
    if (toast) toast.classList.add("kwc-hidden");
  }

  function showDirectMessageEdgeToast(position = "top") {
    const box = document.getElementById("kwc-dm-messages");
    const pos = position === "bottom" ? "bottom" : "top";
    if (!directMessageEdgeToastEligible(box, pos)) return;

    const conv = document.querySelector(".kwc-dm-conversation");
    if (!conv) return;
    let toast = document.getElementById("kwc-dm-edge-toast");
    if (!toast) {
      toast = document.createElement("div");
      toast.id = "kwc-dm-edge-toast";
      toast.className = "kwc-dm-edge-toast kwc-hidden";
      conv.appendChild(toast);
    }

    const now = Date.now();
    if (state.dmEdgeToastVisible && now < Number(state.dmEdgeToastVisibleUntil || 0)) return;
    if (now - Number(state.dmEdgeToastLastShownAt || 0) < 250) return;

    toast.textContent = t("history.end", "No more messages to display.");
    toast.classList.toggle("kwc-dm-edge-bottom", pos === "bottom");
    toast.classList.toggle("kwc-dm-edge-top", pos !== "bottom");
    toast.classList.remove("kwc-hidden");

    state.dmEdgeToastVisible = true;
    state.dmEdgeToastVisibleUntil = now + 2500;
    state.dmEdgeToastLastShownAt = now;
    if (pos === "bottom") {
      state.dmEdgePendingBottomUntil = 0;
      state.dmEdgeBottomExtraScrollCount = 0;
    } else {
      state.dmEdgePendingTopUntil = 0;
    }
    clearTimeout(state.dmEdgeToastTimer);
    state.dmEdgeToastTimer = setTimeout(() => {
      if (Date.now() >= Number(state.dmEdgeToastVisibleUntil || 0)) hideDirectMessageEdgeToast(false);
    }, 2550);
  }

  function maybeShowDirectMessageEdgeToastFromUserScroll(box, reason = "") {
    if (!box) box = document.getElementById("kwc-dm-messages");
    if (!box) return;
    const now = Date.now();
    const atTop = directMessageEdgeAtTop(box);
    const atBottom = directMessageEdgeAtBottom(box);
    if (!atBottom) state.dmEdgeBottomExtraScrollCount = 0;
    if (!atTop && !atBottom) {
      hideDirectMessageEdgeToast(false);
      return;
    }

    const topIntent = Number(state.dmEdgePendingTopUntil || 0) > now;
    const bottomIntent = Number(state.dmEdgePendingBottomUntil || 0) > now;
    if (atTop && topIntent) {
      if (state.dmMessagesHasMore) {
        loadOlderDirectMessageMessagesFromEdge(box, reason || "dm-top-edge");
        return;
      }
      if (!state.dmMessagesLoading) showDirectMessageEdgeToast("top");
      return;
    }
    // Match the normal chat bottom-edge behavior: do not show the toast just
    // because the conversation is already at the latest message.  Require
    // repeated extra downward scroll input at the bottom, and run one latest
    // refresh probe first so the toast only appears after the retry path has
    // also concluded that nothing newer is available.
    if (atBottom && bottomIntent && Number(state.dmEdgeBottomExtraScrollCount || 0) >= 10) {
      const retriedRecently = Date.now() - Number(state.dmLastBottomRetryAt || 0) < 1200;
      if (!retriedRecently && !state.dmBottomRetryInFlight) {
        retryDirectMessageLatestFromBottomEdge(box, reason || "dm-bottom-edge");
        return;
      }
      if (!state.dmMessagesLoading && !state.dmBottomRetryInFlight) showDirectMessageEdgeToast("bottom");
    }
  }

  function markDirectMessageTopEdgeIntent(box, reason = "") {
    if (!box || !directMessageEdgeAtTop(box)) return;
    state.dmEdgePendingTopUntil = Date.now() + 6000;
    setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-top"), 0);
    setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-top"), 80);
    setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-top"), 220);
  }

  function markDirectMessageBottomEdgeIntent(box, reason = "") {
    if (!box) return;
    if (!directMessageEdgeAtBottom(box)) {
      state.dmEdgeBottomExtraScrollCount = 0;
      return;
    }
    if (/^(wheel|key|touch|scrollbar)-bottom$/.test(String(reason || ""))) {
      state.dmEdgeBottomExtraScrollCount = Math.max(0, Number(state.dmEdgeBottomExtraScrollCount || 0)) + 1;
    }
    state.dmEdgePendingBottomUntil = Date.now() + 6000;
    setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-bottom"), 0);
    setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-bottom"), 80);
    setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, reason || "dm-bottom"), 220);
  }

  function installDirectMessageEdgeToasts(wrap) {
    const box = document.getElementById("kwc-dm-messages");
    if (!wrap || !box || box.dataset.kwcDmEdgeInstalled === "1") return;
    box.dataset.kwcDmEdgeInstalled = "1";

    const interactiveTarget = target => {
      try {
        return !!(target && target.closest && target.closest(
          "button, input, textarea, select, a, .kwc-media-card, .kwc-youtube-card, .kwc-social-card, .kwc-social-embed"
        ));
      } catch (_) {
        return false;
      }
    };

    box.addEventListener("wheel", event => {
      if (interactiveTarget(event.target)) return;
      const deltaY = Number(event.deltaY || 0);
      if (deltaY < 0) markDirectMessageTopEdgeIntent(box, "wheel-top");
      else if (deltaY > 0) markDirectMessageBottomEdgeIntent(box, "wheel-bottom");
      setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, "wheel"), 0);
    }, {passive: true});

    box.addEventListener("keydown", event => {
      if (!["ArrowUp", "ArrowDown", "PageUp", "PageDown", "Home", "End", " "].includes(event.key)) return;
      if (["ArrowUp", "PageUp", "Home"].includes(event.key)) markDirectMessageTopEdgeIntent(box, "key-top");
      if (["ArrowDown", "PageDown", "End", " "].includes(event.key)) markDirectMessageBottomEdgeIntent(box, "key-bottom");
      setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, "key"), 0);
    }, {passive: true});

    let touchStartY = null;
    box.addEventListener("touchstart", event => {
      if (interactiveTarget(event.target)) return;
      touchStartY = event.touches && event.touches[0] ? event.touches[0].clientY : null;
    }, {passive: true});
    box.addEventListener("touchmove", event => {
      if (interactiveTarget(event.target)) return;
      const y = event.touches && event.touches[0] ? event.touches[0].clientY : null;
      if (touchStartY != null && y != null && y - touchStartY > 18) markDirectMessageTopEdgeIntent(box, "touch-top");
      else if (touchStartY != null && y != null && touchStartY - y > 18) markDirectMessageBottomEdgeIntent(box, "touch-bottom");
      setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, "touch"), 0);
    }, {passive: true});
    box.addEventListener("touchend", () => { touchStartY = null; }, {passive: true});
    box.addEventListener("touchcancel", () => { touchStartY = null; }, {passive: true});

    box.addEventListener("pointerdown", event => {
      if (interactiveTarget(event.target)) return;
      const rect = box.getBoundingClientRect();
      const nearVerticalScrollbar = event.clientX >= rect.right - 18;
      const nearHorizontalScrollbar = event.clientY >= rect.bottom - 18;
      if (nearVerticalScrollbar || nearHorizontalScrollbar) {
        state.dmScrollbarDragActive = true;
        state.dmScrollbarDragLastX = event.clientX;
        state.dmScrollbarDragLastY = event.clientY;
      }
    }, {passive: true});
    window.addEventListener("pointermove", event => {
      if (!state.dmScrollbarDragActive) return;
      const lastY = Number.isFinite(Number(state.dmScrollbarDragLastY)) ? Number(state.dmScrollbarDragLastY) : event.clientY;
      const dy = event.clientY - lastY;
      state.dmScrollbarDragLastX = event.clientX;
      state.dmScrollbarDragLastY = event.clientY;
      if (dy < -2) markDirectMessageTopEdgeIntent(box, "scrollbar-top");
      else if (dy > 2) markDirectMessageBottomEdgeIntent(box, "scrollbar-bottom");
    }, {capture: true, passive: true});
    window.addEventListener("pointerup", () => {
      if (!state.dmScrollbarDragActive) return;
      state.dmScrollbarDragActive = false;
      state.dmScrollbarDragLastX = null;
      state.dmScrollbarDragLastY = null;
      setTimeout(() => maybeShowDirectMessageEdgeToastFromUserScroll(box, "scrollbar"), 0);
    }, {capture: true, passive: true});
    window.addEventListener("pointercancel", () => {
      if (!state.dmScrollbarDragActive) return;
      state.dmScrollbarDragActive = false;
      state.dmScrollbarDragLastX = null;
      state.dmScrollbarDragLastY = null;
      hideDirectMessageEdgeToast(false);
    }, {capture: true, passive: true});
    window.addEventListener("blur", () => {
      if (!state.dmScrollbarDragActive) return;
      state.dmScrollbarDragActive = false;
      state.dmScrollbarDragLastX = null;
      state.dmScrollbarDragLastY = null;
      hideDirectMessageEdgeToast(false);
    });

    box.addEventListener("scroll", () => {
      if (!state.dmModalOpen || !hasDirectMessageConversationOpen()) return;
      if (!directMessageEdgeAtTop(box) && !directMessageEdgeAtBottom(box)) hideDirectMessageEdgeToast(false);
      maybeShowDirectMessageEdgeToastFromUserScroll(box, "scroll");
    }, {passive: true});
  }


  function hasGroupChatConversationOpen() {
    return !!(state.groupModalOpen && state.groupActiveRoomId);
  }

  function groupChatEdgeToastThresholdPx(box) {
    return Math.max(2, Math.min(8, Math.floor(Number(box && box.clientHeight || 0) * 0.01)));
  }

  function groupChatEdgeAtTop(box) {
    return !!box && Number(box.scrollTop || 0) <= groupChatEdgeToastThresholdPx(box);
  }

  function groupChatEdgeAtBottom(box) {
    return !!box && bottomGapPx(box) <= groupChatEdgeToastThresholdPx(box);
  }

  function groupChatEdgeToastEligible(box, position = "top") {
    if (!state.groupModalOpen || !hasGroupChatConversationOpen()) return false;
    if (!box || !box.querySelector || !box.querySelector(".kwc-group-message")) return false;
    return position === "bottom" ? groupChatEdgeAtBottom(box) : groupChatEdgeAtTop(box);
  }

  function hideGroupChatEdgeToast(clearPending = false) {
    if (state.groupEdgeToastTimer) {
      clearTimeout(state.groupEdgeToastTimer);
      state.groupEdgeToastTimer = null;
    }
    state.groupEdgeToastVisible = false;
    state.groupEdgeToastVisibleUntil = 0;
    if (clearPending) {
      state.groupEdgePendingTopUntil = 0;
      state.groupEdgePendingBottomUntil = 0;
      state.groupEdgeBottomExtraScrollCount = 0;
    }
    const toast = document.getElementById("kwc-group-edge-toast");
    if (toast) toast.classList.add("kwc-hidden");
  }

  function showGroupChatEdgeToast(position = "top") {
    const box = document.getElementById("kwc-group-messages");
    const pos = position === "bottom" ? "bottom" : "top";
    if (!groupChatEdgeToastEligible(box, pos)) return;

    const conv = document.querySelector(".kwc-group-modal .kwc-dm-conversation");
    if (!conv) return;
    let toast = document.getElementById("kwc-group-edge-toast");
    if (!toast) {
      toast = document.createElement("div");
      toast.id = "kwc-group-edge-toast";
      toast.className = "kwc-dm-edge-toast kwc-hidden";
      conv.appendChild(toast);
    }

    const now = Date.now();
    if (state.groupEdgeToastVisible && now < Number(state.groupEdgeToastVisibleUntil || 0)) return;
    if (now - Number(state.groupEdgeToastLastShownAt || 0) < 250) return;

    toast.textContent = t("history.end", "No more messages to display.");
    toast.classList.toggle("kwc-dm-edge-bottom", pos === "bottom");
    toast.classList.toggle("kwc-dm-edge-top", pos !== "bottom");
    toast.classList.remove("kwc-hidden");

    state.groupEdgeToastVisible = true;
    state.groupEdgeToastVisibleUntil = now + 2500;
    state.groupEdgeToastLastShownAt = now;
    if (pos === "bottom") {
      state.groupEdgePendingBottomUntil = 0;
      state.groupEdgeBottomExtraScrollCount = 0;
    } else {
      state.groupEdgePendingTopUntil = 0;
    }
    clearTimeout(state.groupEdgeToastTimer);
    state.groupEdgeToastTimer = setTimeout(() => {
      if (Date.now() >= Number(state.groupEdgeToastVisibleUntil || 0)) hideGroupChatEdgeToast(false);
    }, 2550);
  }

  function maybeShowGroupChatEdgeToastFromUserScroll(box, reason = "") {
    if (!box) box = document.getElementById("kwc-group-messages");
    if (!box) return;
    const now = Date.now();
    const atTop = groupChatEdgeAtTop(box);
    const atBottom = groupChatEdgeAtBottom(box);
    if (!atBottom) state.groupEdgeBottomExtraScrollCount = 0;
    if (!atTop && !atBottom) {
      hideGroupChatEdgeToast(false);
      return;
    }

    const topIntent = Number(state.groupEdgePendingTopUntil || 0) > now;
    const bottomIntent = Number(state.groupEdgePendingBottomUntil || 0) > now;
    if (atTop && topIntent) {
      if (state.groupMessagesHasMore) {
        loadOlderGroupChatMessagesFromEdge(box, reason || "group-top-edge");
        return;
      }
      if (!state.groupMessagesLoading) showGroupChatEdgeToast("top");
      return;
    }
    // Keep the group chat edge toast behavior identical to the normal chat/DM
    // edge flow: require repeated bottom input and run one latest refresh probe
    // before declaring that there are no newer messages.
    if (atBottom && bottomIntent && Number(state.groupEdgeBottomExtraScrollCount || 0) >= 10) {
      const retriedRecently = Date.now() - Number(state.groupLastBottomRetryAt || 0) < 1200;
      if (!retriedRecently && !state.groupBottomRetryInFlight) {
        retryGroupChatLatestFromBottomEdge(box, reason || "group-bottom-edge");
        return;
      }
      if (!state.groupMessagesLoading && !state.groupBottomRetryInFlight) showGroupChatEdgeToast("bottom");
    }
  }

  function markGroupChatTopEdgeIntent(box, reason = "") {
    if (!box || !groupChatEdgeAtTop(box)) return;
    state.groupEdgePendingTopUntil = Date.now() + 6000;
    setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-top"), 0);
    setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-top"), 80);
    setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-top"), 220);
  }

  function markGroupChatBottomEdgeIntent(box, reason = "") {
    if (!box) return;
    if (!groupChatEdgeAtBottom(box)) {
      state.groupEdgeBottomExtraScrollCount = 0;
      return;
    }
    if (/^(wheel|key|touch|scrollbar)-bottom$/.test(String(reason || ""))) {
      state.groupEdgeBottomExtraScrollCount = Math.max(0, Number(state.groupEdgeBottomExtraScrollCount || 0)) + 1;
    }
    state.groupEdgePendingBottomUntil = Date.now() + 6000;
    setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-bottom"), 0);
    setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-bottom"), 80);
    setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-bottom"), 220);
  }

  function installGroupChatEdgeToasts(wrap) {
    const box = document.getElementById("kwc-group-messages");
    if (!wrap || !box || box.dataset.kwcGroupEdgeInstalled === "1") return;
    box.dataset.kwcGroupEdgeInstalled = "1";

    const interactiveTarget = target => {
      try {
        return !!(target && target.closest && target.closest(
          "button, input, textarea, select, a, .kwc-media-card, .kwc-youtube-card, .kwc-social-card, .kwc-social-embed"
        ));
      } catch (_) {
        return false;
      }
    };

    box.addEventListener("wheel", event => {
      if (interactiveTarget(event.target)) return;
      const deltaY = Number(event.deltaY || 0);
      if (deltaY < 0) markGroupChatTopEdgeIntent(box, "wheel-top");
      else if (deltaY > 0) markGroupChatBottomEdgeIntent(box, "wheel-bottom");
      setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, "wheel"), 0);
    }, {passive: true});

    box.addEventListener("keydown", event => {
      if (!["ArrowUp", "ArrowDown", "PageUp", "PageDown", "Home", "End", " "].includes(event.key)) return;
      if (["ArrowUp", "PageUp", "Home"].includes(event.key)) markGroupChatTopEdgeIntent(box, "key-top");
      if (["ArrowDown", "PageDown", "End", " "].includes(event.key)) markGroupChatBottomEdgeIntent(box, "key-bottom");
      setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, "key"), 0);
    }, {passive: true});

    let touchStartY = null;
    box.addEventListener("touchstart", event => {
      if (interactiveTarget(event.target)) return;
      touchStartY = event.touches && event.touches[0] ? event.touches[0].clientY : null;
    }, {passive: true});
    box.addEventListener("touchmove", event => {
      if (interactiveTarget(event.target)) return;
      const y = event.touches && event.touches[0] ? event.touches[0].clientY : null;
      if (touchStartY != null && y != null && y - touchStartY > 18) markGroupChatTopEdgeIntent(box, "touch-top");
      else if (touchStartY != null && y != null && touchStartY - y > 18) markGroupChatBottomEdgeIntent(box, "touch-bottom");
      setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, "touch"), 0);
    }, {passive: true});
    box.addEventListener("touchend", () => { touchStartY = null; }, {passive: true});
    box.addEventListener("touchcancel", () => { touchStartY = null; }, {passive: true});

    box.addEventListener("pointerdown", event => {
      if (interactiveTarget(event.target)) return;
      const rect = box.getBoundingClientRect();
      const nearVerticalScrollbar = event.clientX >= rect.right - 18;
      const nearHorizontalScrollbar = event.clientY >= rect.bottom - 18;
      if (nearVerticalScrollbar || nearHorizontalScrollbar) {
        state.groupScrollbarDragActive = true;
        state.groupScrollbarDragLastX = event.clientX;
        state.groupScrollbarDragLastY = event.clientY;
      }
    }, {passive: true});
    window.addEventListener("pointermove", event => {
      if (!state.groupScrollbarDragActive) return;
      const lastY = Number.isFinite(Number(state.groupScrollbarDragLastY)) ? Number(state.groupScrollbarDragLastY) : event.clientY;
      const dy = event.clientY - lastY;
      state.groupScrollbarDragLastX = event.clientX;
      state.groupScrollbarDragLastY = event.clientY;
      if (dy < -2) markGroupChatTopEdgeIntent(box, "scrollbar-top");
      else if (dy > 2) markGroupChatBottomEdgeIntent(box, "scrollbar-bottom");
    }, {capture: true, passive: true});
    window.addEventListener("pointerup", () => {
      if (!state.groupScrollbarDragActive) return;
      state.groupScrollbarDragActive = false;
      state.groupScrollbarDragLastX = null;
      state.groupScrollbarDragLastY = null;
      setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, "scrollbar"), 0);
    }, {capture: true, passive: true});
    window.addEventListener("pointercancel", () => {
      if (!state.groupScrollbarDragActive) return;
      state.groupScrollbarDragActive = false;
      state.groupScrollbarDragLastX = null;
      state.groupScrollbarDragLastY = null;
      hideGroupChatEdgeToast(false);
    }, {capture: true, passive: true});
    window.addEventListener("blur", () => {
      if (!state.groupScrollbarDragActive) return;
      state.groupScrollbarDragActive = false;
      state.groupScrollbarDragLastX = null;
      state.groupScrollbarDragLastY = null;
      hideGroupChatEdgeToast(false);
    });

    box.addEventListener("scroll", () => {
      if (!state.groupModalOpen || !hasGroupChatConversationOpen()) return;
      if (!groupChatEdgeAtTop(box) && !groupChatEdgeAtBottom(box)) hideGroupChatEdgeToast(false);
      maybeShowGroupChatEdgeToastFromUserScroll(box, "scroll");
    }, {passive: true});
  }


  function installDirectMessageWindowDrag(wrap) {
    if (!wrap || wrap.dataset.kwcDmWindowDragInstalled === "1") return;
    wrap.dataset.kwcDmWindowDragInstalled = "1";
    const header = wrap.querySelector(".kwc-dm-head");
    if (!header) return;
    let active = false;
    let lastX = 0;
    let lastY = 0;
    const pointFromEvent = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return {
        clientX: Number(src.clientX) || 0,
        clientY: Number(src.clientY) || 0,
        screenX: Number(src.screenX) || Number(src.clientX) || 0,
        screenY: Number(src.screenY) || Number(src.clientY) || 0
      };
    };
    const begin = event => {
      const target = event.target;
      if (target && target.closest && target.closest("button, input, select, textarea, a")) return;
      if (state.isPip) return;
      const p = pointFromEvent(event);
      active = true;
      lastX = p.clientX;
      lastY = p.clientY;
      postFrame("dragStart", {screenX: p.screenX, screenY: p.screenY});
      event.preventDefault();
      event.stopPropagation();
    };
    const move = event => {
      if (!active) return;
      const p = pointFromEvent(event);
      const dx = p.clientX - lastX;
      const dy = p.clientY - lastY;
      lastX = p.clientX;
      lastY = p.clientY;
      postFrame("dragMove", {dx, dy, screenX: p.screenX, screenY: p.screenY});
      event.preventDefault();
      event.stopPropagation();
    };
    const end = event => {
      if (!active) return;
      active = false;
      postFrame("dragEnd", {});
      event.preventDefault();
      event.stopPropagation();
    };
    header.addEventListener("pointerdown", begin, {capture: true});
    document.addEventListener("pointermove", move, {capture: true});
    document.addEventListener("pointerup", end, {capture: true});
    document.addEventListener("pointercancel", end, {capture: true});
    header.addEventListener("touchstart", begin, {capture: true, passive: false});
    document.addEventListener("touchmove", move, {capture: true, passive: false});
    document.addEventListener("touchend", end, {capture: true, passive: false});
    document.addEventListener("touchcancel", end, {capture: true, passive: false});
  }

  function renderDirectMessageEmojiPanel() {
    const panel = document.getElementById("kwc-dm-emoji-panel");
    if (!panel) return;
    if (!state.dmEmojiPanelOpen || !canUseCustomEmoji()) {
      closeDirectMessageEmojiPanel();
      return;
    }
    const packs = Array.isArray(state.emojiPacks) ? state.emojiPacks : [];
    const items = Array.isArray(state.emojiItems) ? state.emojiItems : [];
    if (!items.length) {
      panel.innerHTML = `<div class="kwc-emoji-scroll"><div class="kwc-emoji-empty">${esc(t("emoji.empty", "No emojis configured."))}</div></div>`;
      panel.classList.remove("kwc-hidden");
      setDirectMessageEmojiPanelHeight(panel);
      installEmojiPanelWheelStep(panel);
      return;
    }
    let selectedPack = String(state.dmEmojiSelectedPack || "");
    if (!selectedPack || (packs.length && !packs.some(pack => String(pack.id || "") === selectedPack))) {
      selectedPack = packs[0] && packs[0].id ? String(packs[0].id) : "";
    }
    state.dmEmojiSelectedPack = selectedPack;
    const packTabs = packs.length > 1
      ? `<div class="kwc-emoji-tabs">${packs.map(pack => {
          const id = String(pack.id || "");
          return `<button type="button" class="kwc-emoji-tab${id === selectedPack ? " kwc-active" : ""}" data-emoji-pack="${esc(id)}">${esc(pack.label || id)} <span>${esc(pack.count || "")}</span></button>`;
        }).join("")}</div>`
      : "";
    const shown = selectedPack ? items.filter(item => String(item.pack || "") === selectedPack) : items;
    panel.innerHTML = packTabs + `<div class="kwc-emoji-scroll"><div class="kwc-emoji-grid">${shown.map(emojiButtonHtml).join("")}</div></div>`;
    panel.classList.remove("kwc-hidden");
    setDirectMessageEmojiPanelHeight(panel);
    installEmojiPanelWheelStep(panel);

    panel.querySelectorAll("[data-emoji-pack]").forEach(btn => {
      btn.addEventListener("click", () => {
        const pack = btn.dataset.emojiPack || "";
        state.dmEmojiSelectedPack = pack;
        panel.querySelectorAll(".kwc-emoji-tab").forEach(tab => tab.classList.toggle("kwc-active", tab === btn));
        const grid = panel.querySelector(".kwc-emoji-grid");
        if (grid) grid.innerHTML = items.filter(item => String(item.pack || "") === pack).map(emojiButtonHtml).join("");
        const scroll = emojiScrollElement(panel);
        if (scroll) scroll.scrollTop = 0;
        setDirectMessageEmojiPanelHeight(panel);
        installEmojiItemHandlers(panel);
      });
    });
    installEmojiItemHandlers(panel);
  }

  function renderDirectMessagePlayers(players) {
    const box = document.getElementById("kwc-dm-player-results");
    if (!box) return;
    const arr = Array.isArray(players) ? players : [];
    if (!arr.length) {
      box.innerHTML = "";
      return;
    }
    box.innerHTML = arr.map(player => {
      const remote = player.remote === true;
      const uuid = String(player.playerUuid || player.uuid || "");
      const label = player.label || player.displayName || player.username || uuid;
      const target = {
        uuid,
        label,
        displayName: player.displayName || "",
        username: player.username || "",
        remote,
        serverId: remote ? String(player.serverId || "") : "",
        serverName: remote ? String(player.serverName || player.serverId || "") : ""
      };
      return `<button type="button" class="kwc-dm-player" data-dm-player="${esc(uuid)}" ${directMessageTargetDataAttributes(target)} title="${esc(directMessagePlainLabel(label))}">${directMessageLabelHtml(label)}</button>`;
    }).join("");
    box.querySelectorAll("[data-dm-player]").forEach(btn => {
      btn.addEventListener("click", () => {
        state.dmDraftTarget = {
          uuid: btn.dataset.dmTargetUuid || btn.dataset.dmPlayer || "",
          label: btn.dataset.dmTargetLabel || "",
          displayName: btn.dataset.dmTargetDisplayName || "",
          username: btn.dataset.dmTargetUsername || "",
          remote: btn.dataset.dmTargetRemote === "1",
          serverId: btn.dataset.dmTargetServerId || "",
          serverName: btn.dataset.dmTargetServerName || ""
        };
        state.dmActiveThreadId = "";
        state.dmAuditMode = false;
        state.dmAuditThread = null;
        renderDirectMessageHeader(state.dmDraftTarget.label);
        renderDirectMessageMessages([]);
        updateDirectMessageViewMode();
        box.innerHTML = "";
        closeDirectMessagePlayerSearch();
        const input = document.getElementById("kwc-dm-input");
        if (input) {
          setActiveComposeInput(input);
          input.focus();
        }
      });
    });
  }

  async function searchDirectMessagePlayers(query) {
    if (!state.token || !state.directMessageEnabled) return;
    if (!String(query || "").trim()) {
      renderDirectMessagePlayers([]);
      return;
    }
    try {
      const cleanQuery = directMessagePlainLabel(stripMinecraftColorCodes(query));
      const res = await api("/dm/players?q=" + encodeURIComponent(cleanQuery) + "&limit=20");
      renderDirectMessagePlayers(res.players || []);
    } catch (_) {}
  }

  function installDirectMessageDragAndDropUpload(wrap) {
    if (!wrap || wrap.dataset.dmDropInstalled === "1") return;
    wrap.dataset.dmDropInstalled = "1";
    const modal = wrap.querySelector(".kwc-dm-modal") || wrap;
    const setOver = visible => {
      try { modal.classList.toggle("kwc-dm-drag-over", !!visible); } catch (_) {}
    };
    const allowed = () => !state.dmAuditMode && !state.uploadActive && canUpload();
    ["dragenter", "dragover"].forEach(type => {
      wrap.addEventListener(type, event => {
        if (!isFileDragEvent(event)) return;
        event.preventDefault();
        event.stopPropagation();
        if (event.dataTransfer) event.dataTransfer.dropEffect = allowed() ? "copy" : "none";
        setOver(!state.dmAuditMode);
      }, {capture: true});
    });
    wrap.addEventListener("dragleave", event => {
      if (!isFileDragEvent(event)) return;
      const next = event.relatedTarget;
      if (next && wrap.contains(next)) return;
      setOver(false);
    }, {capture: true});
    wrap.addEventListener("drop", async event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      setOver(false);
      if (state.dmAuditMode) return;
      const files = dropEventFiles(event);
      if (!files.length) return;
      setActiveComposeInput("kwc-dm-input");
      if (state.uploadActive) {
        alert(t("upload.dropBusy", "Upload is already in progress."));
        return;
      }
      if (!canUpload()) {
        alert(t("upload.dropDenied", "File upload is not allowed."));
        return;
      }
      await uploadFiles(files, "drop");
    }, {capture: true});
    document.addEventListener("dragend", () => setOver(false), {capture: true});
  }

  async function sendDirectMessageFromModal() {
    if (state.dmAuditMode) return;
    if (!state.token || !state.directMessageEnabled || !state.directMessageAllowWebSend) return;
    const input = document.getElementById("kwc-dm-input");
    if (!input) return;
    let message = String(input.value || "").trim();
    if (!message) return;
    if (state.directMessageMaxMessageLength > 0 && message.length > state.directMessageMaxMessageLength) {
      message = message.slice(0, state.directMessageMaxMessageLength);
    }
    const clientMessageId = privateClientMessageId("dm");
    const replyTarget = state.dmReplyTarget && String(state.dmReplyTarget.conversationId || "") === privateReplyConversationId("dm") ? Object.assign({}, state.dmReplyTarget) : null;
    const body = {message, clientMessageId};
    if (replyTarget && replyTarget.id) body.replyToId = replyTarget.id;
    if (state.dmActiveThreadId) {
      const thread = (state.dmThreads || []).find(t => t.id === state.dmActiveThreadId);
      if (thread) {
        const remote = thread.otherRemote === true && !!thread.otherServerId;
        body.targetUuid = thread.otherPlayerUuid || thread.otherUuid;
        if (remote) {
          body.targetServerId = thread.otherServerId || "";
          body.targetServerName = thread.otherServerName || thread.otherServerId || "";
          body.targetUsername = thread.otherUsername || "";
          body.targetDisplayName = thread.otherDisplayName || "";
          body.targetLabel = thread.otherLabel || directMessageLabel(thread) || "";
        }
      }
    } else if (state.dmDraftTarget && state.dmDraftTarget.uuid) {
      body.targetUuid = state.dmDraftTarget.uuid;
      if (state.dmDraftTarget.remote && state.dmDraftTarget.serverId) {
        body.targetServerId = state.dmDraftTarget.serverId;
        body.targetServerName = state.dmDraftTarget.serverName || "";
        body.targetUsername = state.dmDraftTarget.username || "";
        body.targetDisplayName = state.dmDraftTarget.displayName || "";
        body.targetLabel = state.dmDraftTarget.label || "";
      }
    }
    if (!body.targetUuid) {
      alert(t("dm.selectPlayerFirst", "Select a player first."));
      return;
    }
    input.value = "";
    clearPrivateReply("dm");
    state.dmMessages = (state.dmMessages || []).concat([directMessageOptimisticMessage(clientMessageId, message, body, replyTarget)]);
    renderDirectMessageMessages(state.dmMessages, {stickToBottom: true});
    input.disabled = true;
    try {
      await sendDirectMessageAttempt(body, clientMessageId);
    } finally {
      input.disabled = false;
      input.focus();
    }
  }


  function updateGroupChatButton() {
    const btn = document.getElementById("kwc-group");
    const badge = document.getElementById("kwc-group-badge");
    if (btn) btn.classList.toggle("kwc-hidden", !(state.token && state.groupChatEnabled) || state.minimized);
    if (!badge) return;
    const unread = Math.max(0, Number(state.groupUnread || 0));
    badge.textContent = unread > 99 ? "99+" : String(unread);
    badge.classList.toggle("kwc-hidden", !(state.token && state.groupChatEnabled && unread > 0));
  }

  function reconcileActiveGroupRoomAfterRoomLoad() {
    if (!state.groupActiveRoomId) return false;
    if (state.groupAuditMode) {
      const activeAudit = (state.groupAdminRooms || []).find(r => String(r.id || "") === String(state.groupActiveRoomId));
      if (activeAudit && state.groupChatContentAccess) {
        state.groupAuditRoom = activeAudit;
        state.groupActiveRoom = activeAudit;
        return false;
      }
    } else {
      const active = (state.groupRooms || []).find(r => String(r.id || "") === String(state.groupActiveRoomId));
      if (active && active.member !== false) {
        state.groupActiveRoom = active;
        return false;
      }
    }
    state.groupActiveRoomId = "";
    state.groupActiveRoom = null;
    state.groupAuditMode = false;
    state.groupAuditRoom = null;
    renderGroupChatMessages([]);
    renderGroupChatHeader();
    return true;
  }

  async function loadGroupChatRooms(silent = false) {
    if (!state.token || !state.groupChatEnabled) return;
    try {
      const res = await api("/group/rooms?limit=200");
      state.groupRooms = Array.isArray(res.rooms) ? res.rooms : [];
      state.groupInvites = Array.isArray(res.invites) ? res.invites : [];
      state.groupHiddenRooms = Array.isArray(res.hiddenRooms) ? res.hiddenRooms : [];
      state.groupAdminRooms = Array.isArray(res.adminRooms) ? res.adminRooms : [];
      state.groupCleanupPreview = res.cleanupPreview || null;
      state.privateChatSuperAdmin = res.privateChatSuperAdmin === true || state.privateChatSuperAdmin === true;
      state.groupChatContentAccess = res.groupChatContentAccess === true;
      state.groupUnread = Number(res.unread || 0);
      const activeCleared = reconcileActiveGroupRoomAfterRoomLoad();
      updateGroupChatButton();
      if (state.groupModalOpen) {
        renderGroupChatRooms();
        if (activeCleared) renderGroupChatHeader();
      }
      return res;
    } catch (e) {
      if (!silent) alertResponse("alert.groupLoadFailed", "Failed to load group chats: {error}", e.response || {error: e.message || "error"});
    }
  }

  function groupRoomLabel(room) {
    return String(room && room.name || t("group.untitled", "Untitled room"));
  }

  function stripWrappingParentheses(value) {
    let text = String(value || "").trim();
    if ((text.startsWith("(") && text.endsWith(")")) || (text.startsWith("（") && text.endsWith("）"))) {
      text = text.slice(1, -1).trim();
    }
    return text;
  }

  function groupRoomRetentionText() {
    const days = Math.max(0, Number(state.groupChatRetentionDays || 0));
    const text = days > 0 ? fmt("group.retentionLimited", "Retention: {days} days", {days}) : t("group.retentionUnlimited", "Retention: no time limit");
    return stripWrappingParentheses(text);
  }

  function renderGroupChatRooms() {
    const list = document.getElementById("kwc-group-room-list");
    const invites = document.getElementById("kwc-group-invites");
    if (!list) return;
    const rooms = Array.isArray(state.groupRooms) ? state.groupRooms : [];
    const hiddenRooms = Array.isArray(state.groupHiddenRooms) ? state.groupHiddenRooms : [];
    const adminRooms = Array.isArray(state.groupAdminRooms) ? state.groupAdminRooms : [];
    if (invites) {
      const arr = Array.isArray(state.groupInvites) ? state.groupInvites : [];
      invites.innerHTML = arr.length ? `<div class="kwc-group-invite-title">${esc(t("group.invites", "Invites"))}</div>` + arr.map(inv => `<div class="kwc-group-invite"><span>${esc(inv.roomName || "")}</span><button class="kwc-button" data-group-accept="${esc(inv.id)}">${esc(t("button.accept", "Accept"))}</button><button class="kwc-button" data-group-decline="${esc(inv.id)}">${esc(t("button.decline", "Decline"))}</button></div>`).join("") : "";
      invites.querySelectorAll("[data-group-accept]").forEach(btn => btn.addEventListener("click", () => respondGroupInvite(btn.dataset.groupAccept, true)));
      invites.querySelectorAll("[data-group-decline]").forEach(btn => btn.addEventListener("click", () => respondGroupInvite(btn.dataset.groupDecline, false)));
    }
    if (!rooms.length && !hiddenRooms.length && !adminRooms.length) {
      list.innerHTML = `<div class="kwc-dm-empty">${esc(t("group.noRooms", "No group chats."))}</div>`;
      return;
    }
    const roomHtml = rooms.map(room => {
      const active = room.id === state.groupActiveRoomId ? " kwc-active" : "";
      const unread = Number(room.unread || 0);
      const badge = unread > 0 ? `<span class="kwc-dm-thread-badge">${esc(unread > 99 ? "99+" : String(unread))}</span>` : "";
      const visibility = room.visibility === "public" ? t("group.public", "public") : t("group.private", "private");
      const privacyIcon = room.visibility === "public" ? "🌐" : "🔒";
      const passwordIcon = room.passwordProtected ? " 🔑" : "";
      const join = room.member ? "" : ` <span class="kwc-group-join-hint">${esc(t("group.join", "join"))}</span>`;
      return `<button type="button" class="kwc-dm-thread kwc-group-room${active}" data-group-room="${esc(room.id)}"><span class="kwc-dm-thread-name"><span class="kwc-group-room-icon" aria-hidden="true">${privacyIcon}</span> ${esc(groupRoomLabel(room))}${passwordIcon}</span>${badge}<span class="kwc-dm-thread-preview">${esc(visibility)} · ${esc(room.memberCount || 0)} ${esc(t("group.membersShort", "members"))}${join}</span></button>`;
    }).join("");
    const hiddenHtml = hiddenRooms.length ? `<div class="kwc-admin-meta-title">${esc(t("group.hiddenRooms", "Hidden rooms"))}</div>` + hiddenRooms.map(room => {
      const privacyIcon = room.visibility === "public" ? "🌐" : "🔒";
      const passwordIcon = room.passwordProtected ? " 🔑" : "";
      return `<div class="kwc-dm-thread kwc-group-hidden-row"><span class="kwc-dm-thread-name"><span class="kwc-group-room-icon" aria-hidden="true">${privacyIcon}</span> ${esc(groupRoomLabel(room))}${passwordIcon}</span><span class="kwc-admin-meta-actions"><button type="button" class="kwc-button" data-group-unhide-room="${esc(room.id || "")}">${esc(t("group.showRoom", "Show"))}</button></span><span class="kwc-dm-thread-preview">${esc(t("group.hiddenRoomHint", "Hidden from your list"))}</span></div>`;
    }).join("") : "";
    const adminTitle = state.groupChatContentAccess
      ? t("admin.groupAuditTitle", "Admin group audit")
      : t("admin.groupMetaOnly", "Admin room metadata");
    const adminHtml = adminRooms.length ? `<div class="kwc-admin-meta-title">🛡 ${esc(adminTitle)}</div>` + adminRooms.map(room => {
      const privacyIcon = room.visibility === "public" ? "🌐" : "🔒";
      const passwordIcon = room.passwordProtected ? " 🔑" : "";
      const archived = room.archived ? ` · ${esc(t("admin.archived", "archived"))}` : "";
      const retention = retentionRemainingText(room.retentionBaseAt || room.latestMessageAt || room.updatedAt, room.retentionDays ?? state.groupChatRetentionDays, "group", room.retentionExpiresAt);
      const flags = `${room.locked ? esc(t("admin.locked", "locked")) + " · " : ""}${room.retentionExempt ? esc(t("admin.retentionExempt", "auto-delete excluded")) + " · " : ""}`;
      const meta = `${esc(retention)} · ${flags}${esc(t("admin.messages", "messages"))}: ${esc(room.messageCount || 0)} · ${esc(t("admin.storage", "storage"))}: ${esc(formatBytes(room.storageBytes || 0))} · ${esc(room.memberCount || 0)} ${esc(t("group.membersShort", "members"))}${archived}`;
      const lockLabel = room.locked ? t("admin.unlock", "Unlock") : t("admin.lock", "Lock");
      const exemptLabel = room.retentionExempt ? t("admin.includeRetention", "Include") : t("admin.excludeRetention", "Exclude");
      const lockTitle = room.locked ? t("admin.unlockGroupRoomHint", "Unlock this group room so messages can be sent again.") : t("admin.lockGroupRoomHint", "Lock this group room to prevent new messages.");
      const exemptTitle = room.retentionExempt ? t("admin.includeGroupRetentionHint", "Include this group room in automatic cleanup again.") : t("admin.excludeGroupRetentionHint", "Exclude this group room from automatic cleanup.");
      const deleteTitle = t("admin.deleteGroupRoomHint", "Delete this group room, including metadata, messages, and uploads.");
      const openTitle = state.groupChatContentAccess ? t("admin.openGroupAudit", "Open this group chat in read-only audit view.") : t("admin.noContentAccess", "Message contents are not accessible from this view.");
      const openAttrs = state.groupChatContentAccess ? ` data-group-admin-open-room="${esc(room.id || "")}" role="button" tabindex="0"` : "";
      const openClass = state.groupChatContentAccess ? " kwc-admin-meta-open" : "";
      return `<div class="kwc-dm-thread kwc-admin-meta-row${openClass}"${openAttrs}><span class="kwc-dm-thread-name" title="${esc(openTitle)}">🛡 ${privacyIcon} ${esc(room.name || t("group.untitled", "Untitled room"))}${passwordIcon}</span><span class="kwc-admin-meta-actions"><button type="button" class="kwc-button" data-group-admin-lock-room="${esc(room.id || "")}" data-next-locked="${room.locked ? "false" : "true"}" title="${esc(lockTitle)}" aria-label="${esc(lockTitle)}">${esc(lockLabel)}</button><button type="button" class="kwc-button" data-group-admin-retention-room="${esc(room.id || "")}" data-next-exempt="${room.retentionExempt ? "false" : "true"}" title="${esc(exemptTitle)}" aria-label="${esc(exemptTitle)}">${esc(exemptLabel)}</button><button type="button" class="kwc-button kwc-admin-meta-danger" data-group-admin-delete-room="${esc(room.id || "")}" title="${esc(deleteTitle)}" aria-label="${esc(deleteTitle)}">${esc(t("admin.deleteRoom", "Delete"))}</button></span><span class="kwc-dm-thread-preview" title="${esc(meta.replace(/<[^>]*>/g, ""))}">${meta}</span></div>`;
    }).join("") : "";
    const previewHtml = state.privateChatSuperAdmin ? cleanupPreviewHtml(state.groupCleanupPreview, "group") : "";
    list.innerHTML = roomHtml + hiddenHtml + previewHtml + adminHtml;
    list.querySelectorAll("[data-group-admin-open-room]").forEach(row => {
      const open = event => {
        if (event && event.target && event.target.closest && event.target.closest(".kwc-admin-meta-actions")) return;
        const roomId = row.dataset.groupAdminOpenRoom || "";
        if (!roomId || !state.groupChatContentAccess) return;
        openGroupAuditRoom(roomId);
      };
      row.addEventListener("click", open);
      row.addEventListener("keydown", event => {
        if (!event || (event.key !== "Enter" && event.key !== " ")) return;
        event.preventDefault();
        open(event);
      });
    });
    list.querySelectorAll("[data-group-admin-lock-room]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        setAdminSessionFlag("group", btn.dataset.groupAdminLockRoom || "", {locked: btn.dataset.nextLocked === "true"});
      });
    });
    list.querySelectorAll("[data-group-admin-retention-room]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        setAdminSessionFlag("group", btn.dataset.groupAdminRetentionRoom || "", {retentionExempt: btn.dataset.nextExempt === "true"});
      });
    });
    list.querySelectorAll("[data-group-admin-delete-room]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        deleteAdminGroupRoom(btn.dataset.groupAdminDeleteRoom || "");
      });
    });
  }

  function handleGroupRoomListClick(event) {
    const raw = event && event.target;
    const unhide = raw && raw.closest ? raw.closest("[data-group-unhide-room]") : null;
    if (unhide) {
      event.preventDefault();
      event.stopPropagation();
      unhideGroupRoomForMe(unhide.dataset.groupUnhideRoom || "");
      return;
    }
    const btn = raw && raw.closest ? raw.closest("[data-group-room]") : null;
    if (!btn) return;
    event.preventDefault();
    event.stopPropagation();
    const roomId = String(btn.dataset.groupRoom || "").trim();
    if (!roomId) return;
    openGroupRoom(roomId);
  }

  async function unhideGroupRoomForMe(roomId) {
    roomId = String(roomId || "").trim();
    if (!roomId || !state.token) return;
    try {
      await api("/group/unhide-room", {method: "POST", body: JSON.stringify({roomId})});
      await loadGroupChatRooms(true);
      renderGroupChatRooms();
    } catch (e) {
      alertResponse("alert.groupActionFailed", "Group action failed: {error}", e.response || {error: e.message || "error"});
    }
  }

  async function deleteAdminGroupRoom(roomId) {
    roomId = String(roomId || "").trim();
    if (!roomId || !state.token || !state.privateChatSuperAdmin) return;
    if (!confirmPlain(t("admin.confirmDeleteGroupRoom", "Delete this group chat session and all of its metadata/messages/uploads? This cannot be undone."))) return;
    try {
      const res = await adminWrite("/admin/delete-group-room", {roomId});
      if (!res?.ok) throw Object.assign(new Error(res?.error || "delete_failed"), {response: res});
      if (state.groupActiveRoomId === roomId) {
        state.groupActiveRoomId = "";
        state.groupActiveRoom = null;
        state.groupAuditMode = false;
        state.groupAuditRoom = null;
        renderGroupChatMessages([]);
        renderGroupChatHeader();
      }
      await loadGroupChatRooms(true);
      renderGroupChatRooms();
    } catch (e) {
      alertResponse("alert.deleteFailed", "Delete failed: {error}", e.response || {error: e.message || "error"});
    }
  }

  async function openGroupRoom(roomId) {
    roomId = String(roomId || "").trim();
    clearPrivateReply("group");
    if (!roomId) return;
    state.groupAuditMode = false;
    state.groupAuditRoom = null;
    let room = (state.groupRooms || []).find(r => r.id === roomId);
    if (!room) {
      await loadGroupChatRooms(true);
      room = (state.groupRooms || []).find(r => r.id === roomId);
      if (!room) return;
    }

    const previousRoomId = state.groupActiveRoomId;
    const previousRoom = state.groupActiveRoom;

    state.groupActiveRoomId = roomId;
    state.groupActiveRoom = room;
    renderGroupChatRooms();
    renderGroupChatHeader();
    renderGroupChatMessages([]);

    if (!room.member) {
      let password = "";
      if (room.passwordProtected) password = prompt(t("group.passwordPrompt", "Room password")) || "";
      try {
        const res = await api("/group/join", {method: "POST", body: JSON.stringify({roomId, password})});
        if (res.room) state.groupActiveRoom = res.room;
        await loadGroupChatRooms(true);
        const refreshed = (state.groupRooms || []).find(r => r.id === roomId);
        if (refreshed) state.groupActiveRoom = refreshed;
      } catch (e) {
        state.groupActiveRoomId = previousRoomId || "";
        state.groupActiveRoom = previousRoom || null;
        renderGroupChatRooms();
        renderGroupChatHeader();
        renderGroupChatMessages([]);
        alertResponse("alert.groupJoinFailed", "Failed to join room: {error}", e.response || {error: e.message || "error"});
        return;
      }
    }

    state.groupActiveRoomId = roomId;
    state.groupActiveRoom = (state.groupRooms || []).find(r => r.id === roomId) || state.groupActiveRoom || room;
    renderGroupChatRooms();
    renderGroupChatHeader();
    await loadGroupChatMessages(roomId);
  }

  async function openGroupAuditRoom(roomId) {
    roomId = String(roomId || "").trim();
    clearPrivateReply("group");
    if (!roomId || !state.groupChatContentAccess) return;
    let room = (state.groupAdminRooms || []).find(r => String(r.id || "") === roomId);
    if (!room) {
      await loadGroupChatRooms(true);
      room = (state.groupAdminRooms || []).find(r => String(r.id || "") === roomId);
      if (!room) return;
    }
    state.groupAuditMode = true;
    state.groupAuditRoom = room;
    state.groupActiveRoomId = roomId;
    state.groupActiveRoom = room;
    closeGroupPlayerSearch();
    closeGroupChatEmojiPanel();
    renderGroupChatRooms();
    renderGroupChatHeader();
    renderGroupChatMessages([]);
    updateGroupChatComposeControls();
    await loadGroupChatMessages(roomId);
  }

  function renderGroupChatHeader() {
    const title = document.getElementById("kwc-group-title");
    if (!title) return;
    const modal = title.closest(".kwc-group-modal");
    const room = state.groupActiveRoom || (state.groupRooms || []).find(r => r.id === state.groupActiveRoomId);
    if (modal) modal.classList.toggle("kwc-dm-thread-mode", !!room);
    if (!room) {
      title.textContent = t("group.selectRoom", "Select a room");
      title.classList.remove("kwc-dm-title-back");
      title.title = "";
      title.removeAttribute("aria-label");
      title.removeAttribute("role");
      title.tabIndex = -1;
      title.onclick = null;
      title.onkeydown = null;
      return;
    }
    if (state.groupAuditMode) {
      const privacyIcon = room.visibility === "public" ? "🌐" : "🔒";
      const backLabel = t("group.backToList", "Back to group chat list");
      title.classList.add("kwc-dm-title-back");
      title.title = backLabel;
      title.setAttribute("aria-label", backLabel);
      title.setAttribute("role", "button");
      title.tabIndex = 0;
      title.innerHTML = `<span class="kwc-group-title-main"><span class="kwc-group-title-name">🛡 ${privacyIcon} ${esc(groupRoomLabel(room))}</span></span><small>${esc(t("admin.groupAuditReadOnly", "Read-only audit · every access is logged"))}</small>`;
      title.onclick = () => returnGroupChatToList();
      title.onkeydown = event => {
        if (!event || (event.key !== "Enter" && event.key !== " ")) return;
        event.preventDefault();
        returnGroupChatToList();
      };
      updateGroupChatComposeControls();
      return;
    }
    const manage = room.role === "owner" || room.role === "admin";
    const privacyLabel = room.visibility === "public" ? t("group.public", "public") : t("group.private", "private");
    const privacyIcon = room.visibility === "public" ? "🌐" : "🔒";
    const passwordText = room.passwordProtected ? ` · 🔑 ${esc(t("group.passwordProtected", "password"))}` : "";
    const backLabel = t("group.backToList", "Back to group chat list");
    title.classList.add("kwc-dm-title-back");
    title.title = backLabel;
    title.setAttribute("aria-label", backLabel);
    title.setAttribute("role", "button");
    title.tabIndex = 0;
    const memberCount = Math.max(0, Number(room.memberCount || 0));
    const onlineCount = Math.max(0, Number(room.onlineMemberCount || 0));
    const countText = fmt("group.memberOnlineCount", "{online}/{total} online", {online: onlineCount, total: memberCount});
    title.innerHTML = `<span class="kwc-group-title-main"><span class="kwc-group-title-name">${privacyIcon} ${esc(groupRoomLabel(room))}</span><span class="kwc-group-member-counts" id="kwc-group-member-counts" role="button" tabindex="0" title="${esc(t("group.members", "Members"))}" aria-label="${esc(t("group.members", "Members"))}">${esc(countText)}</span></span><small>${esc(privacyLabel)}${passwordText}</small><span class="kwc-group-actions">${manage ? `<button class="kwc-button" id="kwc-group-settings">${esc(t("group.settings", "Settings"))}</button><button class="kwc-button" id="kwc-group-invite">${esc(t("group.invite", "Invite"))}</button>` : ""}<button class="kwc-button" id="kwc-group-hide-room">${esc(t("button.hide", "Hide"))}</button><button class="kwc-button" id="kwc-group-leave">${esc(t("group.leave", "Leave"))}</button></span>`;
    title.onclick = event => {
      if (event && event.target && event.target.closest && event.target.closest(".kwc-group-actions")) return;
      if (event && event.target && event.target.closest && event.target.closest(".kwc-group-member-counts")) return;
      returnGroupChatToList();
    };
    title.onkeydown = event => {
      if (!event || (event.key !== "Enter" && event.key !== " ")) return;
      event.preventDefault();
      returnGroupChatToList();
    };
    const settings = document.getElementById("kwc-group-settings");
    if (settings) settings.onclick = event => { event.preventDefault(); event.stopPropagation(); updateGroupRoomSettings(); };
    const invite = document.getElementById("kwc-group-invite");
    if (invite) invite.onclick = event => { event.preventDefault(); event.stopPropagation(); inviteToGroupRoom(); };
    const memberCounts = document.getElementById("kwc-group-member-counts");
    if (memberCounts) {
      memberCounts.onclick = event => { event.preventDefault(); event.stopPropagation(); openGroupManagePanel(); };
      memberCounts.onkeydown = event => {
        if (!event || (event.key !== "Enter" && event.key !== " ")) return;
        event.preventDefault();
        event.stopPropagation();
        openGroupManagePanel();
      };
    }
    const hideRoom = document.getElementById("kwc-group-hide-room");
    if (hideRoom) hideRoom.onclick = event => { event.preventDefault(); event.stopPropagation(); hideGroupRoomForMe(); };
    const leave = document.getElementById("kwc-group-leave");
    if (leave) leave.onclick = event => { event.preventDefault(); event.stopPropagation(); leaveGroupRoom(); };
    updateGroupChatComposeControls();
  }


  async function hideGroupRoomForMe() {
    const roomId = state.groupActiveRoomId;
    if (!roomId || !state.token) return;
    if (!confirmPlain(t("group.confirmHideRoom", "Hide this room from your group chat list?"))) return;
    try {
      await api("/group/hide-room", {method: "POST", body: JSON.stringify({roomId})});
      state.groupActiveRoomId = "";
      state.groupActiveRoom = null;
      await loadGroupChatRooms(true);
      renderGroupChatRooms();
      renderGroupChatMessages([]);
      renderGroupChatHeader();
    } catch (e) {
      alertResponse("alert.groupActionFailed", "Group action failed: {error}", e.response || {error: e.message || "error"});
    }
  }

  async function openGroupManagePanel() {
    const room = state.groupActiveRoom;
    const roomId = state.groupActiveRoomId;
    if (!room || !roomId || !state.token) return;
    try {
      const res = await api("/group/members?roomId=" + encodeURIComponent(roomId));
      const members = Array.isArray(res.members) ? res.members : [];
      const bans = Array.isArray(res.bans) ? res.bans : [];
      const wrap = document.createElement("div");
      wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
      applyDetachedModalTheme(wrap);
      const canManage = room.role === "owner" || room.role === "admin";
      const canTransfer = room.role === "owner";
      const memberRows = members.map(m => {
        const online = m.online === true;
        const onlineText = online ? t("group.online", "Online") : t("group.offline", "Offline");
        const actions = canManage ? `${m.role !== "owner" ? `<button class="kwc-button" data-group-kick="${esc(m.uuid)}">${esc(t("group.kick", "Kick"))}</button><button class="kwc-button" data-group-ban="${esc(m.uuid)}">${esc(t("group.ban", "Ban"))}</button>` : ""}${canTransfer && m.role !== "owner" ? `<button class="kwc-button" data-group-transfer="${esc(m.uuid)}">${esc(t("group.transferOwner", "Transfer owner"))}</button>` : ""}` : "";
        return `<div class="kwc-group-member-row"><span>${directMessageIdentityHtml({displayName: m.displayName || m.label || m.username || "", username: m.username || "", uuid: m.uuid || ""}, "kwc-sender")}<small>${esc(m.role || "member")} · <span class="kwc-group-online-state"><span class="kwc-group-online-dot${online ? "" : " kwc-offline"}"></span>${esc(onlineText)}</span></small></span><span class="kwc-group-member-actions">${actions}</span></div>`;
      }).join("") || `<div class="kwc-dm-empty">${esc(t("group.noMembers", "No members."))}</div>`;
      const banRows = bans.map(b => `<div class="kwc-group-member-row"><span>${directMessageIdentityHtml({displayName: b.displayName || b.label || b.username || "", username: b.username || "", uuid: b.uuid || ""}, "kwc-sender")}<small>${esc(t("group.banned", "Banned"))}${b.bannedByLabel ? " · " + esc(b.bannedByLabel) : ""}</small></span><span class="kwc-group-member-actions"><button class="kwc-button" data-group-unban="${esc(b.uuid)}">${esc(t("group.unban", "Unban"))}</button></span></div>`).join("") || `<div class="kwc-dm-empty">${esc(t("group.noBans", "No banned users."))}</div>`;
      const manageNote = canManage ? t("group.manageNote", "Room managers can kick, ban, or transfer ownership. Message contents are not shown here.") : t("group.memberListNote", "Members can view the participant list. Management actions are only shown to room managers.");
      const bansSection = canManage ? `<h4>${esc(t("group.bannedUsers", "Banned users"))}</h4><div class="kwc-group-member-list">${banRows}</div>` : "";
      wrap.innerHTML = `<div class="kwc-modal kwc-group-manage-modal"><h3>${esc(t("group.manage", "Manage"))} · ${esc(groupRoomLabel(room))}</h3><p class="kwc-admin-meta-note">${esc(manageNote)}</p><h4>${esc(t("group.members", "Members"))}</h4><div class="kwc-group-member-list">${memberRows}</div>${bansSection}<div class="kwc-row"><button class="kwc-button" id="kwc-group-manage-close">${esc(t("button.close", "Close"))}</button></div></div>`;
      document.body.appendChild(wrap);
      installSenderIdentityToggle(wrap);
      const close = () => wrap.remove();
      wrap.addEventListener("click", e => { if (e.target === wrap) close(); });
      wrap.querySelector("#kwc-group-manage-close").onclick = close;
      const labelForTarget = targetUuid => {
        const found = (members.find(m => m.uuid === targetUuid) || bans.find(b => b.uuid === targetUuid) || {});
        return found.label || found.displayName || found.username || targetUuid;
      };
      const act = async (endpoint, targetUuid, confirmKey, fallback) => {
        if (!targetUuid) return;
        const label = labelForTarget(targetUuid);
        if (!confirmPlain(fmt(confirmKey, fallback, {player: label}))) return;
        try {
          await api(endpoint, {method: "POST", body: JSON.stringify({roomId, targetUuid})});
          close();
          await loadGroupChatRooms(true);
          const refreshed = (state.groupRooms || []).find(r => r.id === roomId);
          if (refreshed) state.groupActiveRoom = refreshed;
          renderGroupChatRooms();
          renderGroupChatHeader();
        } catch (e) {
          alertResponse("alert.groupActionFailed", "Group action failed: {error}", e.response || {error: e.message || "error"});
        }
      };
      wrap.querySelectorAll("[data-group-kick]").forEach(btn => btn.onclick = () => act("/group/kick", btn.dataset.groupKick, "group.confirmKick", "Kick {player} from this room?"));
      wrap.querySelectorAll("[data-group-ban]").forEach(btn => btn.onclick = () => act("/group/ban", btn.dataset.groupBan, "group.confirmBan", "Ban {player} from this room?"));
      wrap.querySelectorAll("[data-group-unban]").forEach(btn => btn.onclick = () => act("/group/unban", btn.dataset.groupUnban, "group.confirmUnban", "Unban {player} from this room?"));
      wrap.querySelectorAll("[data-group-transfer]").forEach(btn => btn.onclick = () => act("/group/transfer-owner", btn.dataset.groupTransfer, "group.confirmTransferOwner", "Transfer room ownership to {player}?"));
    } catch (e) {
      alertResponse("alert.groupActionFailed", "Group action failed: {error}", e.response || {error: e.message || "error"});
    }
  }

  function returnGroupChatToList() {
    clearPrivateReply("group");
    state.groupActiveRoomId = "";
    state.groupActiveRoom = null;
    state.groupAuditMode = false;
    state.groupAuditRoom = null;
    renderGroupChatRooms();
    renderGroupChatMessages([]);
    renderGroupChatHeader();
  }

  function syncGroupPlayerSearchPanelSize() {
    const panel = document.getElementById("kwc-group-search-panel");
    const modal = panel && panel.closest ? panel.closest(".kwc-group-modal") : null;
    if (!panel || !modal) return;
    const sideGap = (Number(modal.getBoundingClientRect().width || 0) <= 360) ? 6 : 10;
    panel.style.setProperty("left", sideGap + "px", "important");
    panel.style.setProperty("right", sideGap + "px", "important");
    panel.style.setProperty("width", "auto", "important");
    panel.style.setProperty("max-width", "none", "important");
    panel.style.setProperty("box-sizing", "border-box", "important");
  }

  function resetGroupPlayerSearchPanelSize() {
    const panel = document.getElementById("kwc-group-search-panel");
    if (!panel) return;
    ["left", "right", "width", "max-width", "box-sizing"].forEach(name => panel.style.removeProperty(name));
  }

  function closeGroupPlayerSearch() {
    state.groupSearchPanelOpen = false;
    const panel = document.getElementById("kwc-group-search-panel");
    if (panel) panel.classList.add("kwc-hidden");
    resetGroupPlayerSearchPanelSize();
  }

  function openGroupPlayerSearch() {
    if (!state.groupActiveRoomId) return;
    state.groupSearchPanelOpen = true;
    const panel = document.getElementById("kwc-group-search-panel");
    const input = document.getElementById("kwc-group-search");
    if (panel) {
      panel.classList.remove("kwc-hidden");
      syncGroupPlayerSearchPanelSize();
    }
    if (input) {
      input.value = "";
      setTimeout(() => input.focus(), 0);
    }
    renderGroupPlayers([]);
  }

  function renderGroupPlayers(players) {
    const box = document.getElementById("kwc-group-player-results");
    if (!box) return;
    const arr = Array.isArray(players) ? players : [];
    if (!arr.length) {
      box.innerHTML = "";
      return;
    }
    box.innerHTML = arr.map(player => {
      const label = player.label || player.displayName || player.username || player.uuid;
      return `<button type="button" class="kwc-dm-player" data-group-player="${esc(player.uuid)}" data-group-player-label="${esc(label)}" title="${esc(directMessagePlainLabel(label))}">${directMessageLabelHtml(label)}</button>`;
    }).join("");
    box.querySelectorAll("[data-group-player]").forEach(btn => {
      btn.addEventListener("click", async event => {
        event.preventDefault();
        event.stopPropagation();
        await invitePlayerToGroupRoom(btn.dataset.groupPlayer || "", btn.dataset.groupPlayerLabel || "");
      });
    });
  }

  async function searchGroupPlayers(query) {
    if (!state.token || !state.groupChatEnabled) return;
    if (!String(query || "").trim()) {
      renderGroupPlayers([]);
      return;
    }
    try {
      const cleanQuery = directMessagePlainLabel(stripMinecraftColorCodes(query));
      const res = await api("/group/players?q=" + encodeURIComponent(cleanQuery) + "&limit=20");
      renderGroupPlayers(res.players || []);
    } catch (_) {}
  }

  function closeGroupChatEmojiPanel() {
    state.groupEmojiPanelOpen = false;
    const panel = document.getElementById("kwc-group-emoji-panel");
    if (panel) {
      panel.classList.add("kwc-hidden");
      panel.hidden = true;
      panel.style.display = "none";
      panel.style.height = "0px";
      panel.style.minHeight = "0px";
      panel.style.maxHeight = "0px";
    }
    updateGroupChatEmojiResizeHandleVisibility();
  }

  function toggleGroupChatEmojiPanel() {
    if (!canUseCustomEmoji()) return;
    setActiveComposeInput("kwc-group-input");
    state.groupEmojiPanelOpen = !state.groupEmojiPanelOpen;
    renderGroupChatEmojiPanel();
  }

  function setGroupChatEmojiPanelHeight(panel, px = null, persist = false, options = {}) {
    if (!panel) return 0;
    const minHeight = emojiPanelMinHeightPx(panel);
    const maxHeight = emojiPanelMaxHeightPx();
    let height = Math.round(Number(px == null ? emojiPanelHeightPx() : px) || emojiPanelHeightPx());
    height = Math.max(minHeight, Math.min(maxHeight, height));
    if (!options || options.snap !== false) height = snapEmojiPanelHeightPx(height, panel);
    state.emojiPanelHeightPx = height;
    if (persist) {
      try { localStorage.setItem("kwc.emojiPanelHeightPx", String(height)); } catch (_) {}
    }
    const root = document.getElementById("kwc-root");
    if (root) {
      root.style.setProperty("--kwc-emoji-panel-height", height + "px");
      root.style.setProperty("--kwc-emoji-panel-min-height", minHeight + "px");
    }
    const wrap = document.querySelector(".kwc-group-modal-backdrop");
    if (wrap) {
      wrap.style.setProperty("--kwc-emoji-panel-height", height + "px");
      wrap.style.setProperty("--kwc-emoji-panel-min-height", minHeight + "px");
    }
    panel.hidden = false;
    panel.style.display = "flex";
    panel.style.height = height + "px";
    panel.style.maxHeight = height + "px";
    panel.style.minHeight = minHeight + "px";
    if (!options || options.snapScroll !== false) snapEmojiPanelScrollTop(panel);
    updateGroupChatEmojiResizeHandleVisibility();
    return height;
  }

  function updateGroupChatEmojiResizeHandleVisibility() {
    const handle = document.getElementById("kwc-group-emoji-resize");
    if (!handle) return;
    const visible = !!(state.groupModalOpen && state.groupEmojiPanelOpen && canUseCustomEmoji());
    handle.classList.toggle("kwc-hidden", !visible);
    handle.hidden = !visible;
  }

  function installGroupChatEmojiPanelResize(wrap) {
    const handle = document.getElementById("kwc-group-emoji-resize");
    const panel = document.getElementById("kwc-group-emoji-panel");
    if (!wrap || !handle || !panel || handle.dataset.kwcInstalled === "1") return;
    handle.dataset.kwcInstalled = "1";
    const pointY = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return Number(src.clientY) || 0;
    };
    const begin = event => {
      if (!state.groupEmojiPanelOpen || !canUseCustomEmoji()) return;
      event.preventDefault();
      event.stopPropagation();
      markNonScrollUiAction();
      state.emojiPanelResizeStart = {
        group: true,
        y: pointY(event),
        height: Number(panel.getBoundingClientRect().height || emojiPanelHeightPx()),
        currentHeight: Number(panel.getBoundingClientRect().height || emojiPanelHeightPx())
      };
      document.body.classList.add("kwc-emoji-resizing");
      try { handle.setPointerCapture && event.pointerId != null && handle.setPointerCapture(event.pointerId); } catch (_) {}
    };
    const move = event => {
      const start = state.emojiPanelResizeStart;
      if (!start || !start.group) return;
      event.preventDefault();
      event.stopPropagation();
      const delta = start.y - pointY(event);
      start.currentHeight = setGroupChatEmojiPanelHeight(panel, start.height + delta, false, {snap: false, snapScroll: false});
    };
    const end = event => {
      const start = state.emojiPanelResizeStart;
      if (!start || !start.group) return;
      event.preventDefault();
      event.stopPropagation();
      setGroupChatEmojiPanelHeight(panel, start.currentHeight || emojiPanelHeightPx(), true, {snap: false, snapScroll: true});
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
    updateGroupChatEmojiResizeHandleVisibility();
  }

  function renderGroupChatEmojiPanel() {
    const panel = document.getElementById("kwc-group-emoji-panel");
    if (!panel) return;
    if (!state.groupEmojiPanelOpen || !canUseCustomEmoji()) {
      closeGroupChatEmojiPanel();
      return;
    }
    const packs = Array.isArray(state.emojiPacks) ? state.emojiPacks : [];
    const items = Array.isArray(state.emojiItems) ? state.emojiItems : [];
    if (!items.length) {
      panel.innerHTML = `<div class="kwc-emoji-scroll"><div class="kwc-emoji-empty">${esc(t("emoji.empty", "No emojis configured."))}</div></div>`;
      panel.classList.remove("kwc-hidden");
      setGroupChatEmojiPanelHeight(panel);
      installEmojiPanelWheelStep(panel);
      return;
    }
    let selectedPack = String(state.groupEmojiSelectedPack || "");
    if (!selectedPack || (packs.length && !packs.some(pack => String(pack.id || "") === selectedPack))) selectedPack = packs[0] && packs[0].id ? String(packs[0].id) : "";
    state.groupEmojiSelectedPack = selectedPack;
    const packTabs = packs.length > 1 ? `<div class="kwc-emoji-tabs">${packs.map(pack => {
      const id = String(pack.id || "");
      return `<button type="button" class="kwc-emoji-tab${id === selectedPack ? " kwc-active" : ""}" data-emoji-pack="${esc(id)}">${esc(pack.label || id)} <span>${esc(pack.count || "")}</span></button>`;
    }).join("")}</div>` : "";
    const shown = selectedPack ? items.filter(item => String(item.pack || "") === selectedPack) : items;
    panel.innerHTML = packTabs + `<div class="kwc-emoji-scroll"><div class="kwc-emoji-grid">${shown.map(emojiButtonHtml).join("")}</div></div>`;
    panel.classList.remove("kwc-hidden");
    setGroupChatEmojiPanelHeight(panel);
    installEmojiPanelWheelStep(panel);
    panel.querySelectorAll("[data-emoji-pack]").forEach(btn => {
      btn.addEventListener("click", () => {
        const pack = btn.dataset.emojiPack || "";
        state.groupEmojiSelectedPack = pack;
        panel.querySelectorAll(".kwc-emoji-tab").forEach(tab => tab.classList.toggle("kwc-active", tab === btn));
        const grid = panel.querySelector(".kwc-emoji-grid");
        if (grid) grid.innerHTML = items.filter(item => String(item.pack || "") === pack).map(emojiButtonHtml).join("");
        const scroll = emojiScrollElement(panel);
        if (scroll) scroll.scrollTop = 0;
        setGroupChatEmojiPanelHeight(panel);
        installEmojiItemHandlers(panel);
      });
    });
    installEmojiItemHandlers(panel);
  }

  function installGroupChatDragAndDropUpload(wrap) {
    if (!wrap || wrap.dataset.groupDropInstalled === "1") return;
    wrap.dataset.groupDropInstalled = "1";
    const modal = wrap.querySelector(".kwc-group-modal") || wrap;
    const setOver = visible => {
      try { modal.classList.toggle("kwc-dm-drag-over", !!visible); } catch (_) {}
    };
    const allowed = () => !state.groupAuditMode && !state.uploadActive && canUpload();
    ["dragenter", "dragover"].forEach(type => {
      wrap.addEventListener(type, event => {
        if (!isFileDragEvent(event)) return;
        event.preventDefault();
        event.stopPropagation();
        if (event.dataTransfer) event.dataTransfer.dropEffect = allowed() ? "copy" : "none";
        setOver(allowed());
      }, {capture: true});
    });
    wrap.addEventListener("dragleave", event => {
      if (!isFileDragEvent(event)) return;
      const next = event.relatedTarget;
      if (next && wrap.contains(next)) return;
      setOver(false);
    }, {capture: true});
    wrap.addEventListener("drop", async event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      setOver(false);
      if (state.groupAuditMode) return;
      const files = dropEventFiles(event);
      if (!files.length) return;
      setActiveComposeInput("kwc-group-input");
      if (state.uploadActive) {
        alert(t("upload.dropBusy", "Upload is already in progress."));
        return;
      }
      if (!canUpload()) {
        alert(t("upload.dropDenied", "File upload is not allowed."));
        return;
      }
      await uploadFiles(files, "drop");
    }, {capture: true});
    document.addEventListener("dragend", () => setOver(false), {capture: true});
  }

  function renderGroupChatMessages(messages, options = {}) {
    const box = document.getElementById("kwc-group-messages");
    if (!box) return;
    renderPrivateReplyCompose("group");
    hideGroupChatEdgeToast(true);
    const arr = Array.isArray(messages) ? messages : [];
    const prevTop = Number(options.previousScrollTop != null ? options.previousScrollTop : box.scrollTop || 0);
    const prevHeight = Number(options.previousScrollHeight != null ? options.previousScrollHeight : box.scrollHeight || 0);
    if (!state.groupActiveRoomId) {
      state.groupMessages = [];
      state.groupMessagesHasMore = false;
      discardPrivateMessageDom(box);
      const empty = document.createElement("div");
      empty.className = "kwc-dm-empty";
      empty.textContent = t("group.selectRoom", "Select a room");
      box.appendChild(empty);
      updateGroupChatComposeControls();
      return;
    }
    const conversationKey = "group:" + String(state.groupActiveRoomId || "");
    const auditNotice = state.groupAuditMode
      ? `<div class="kwc-admin-audit-notice">🛡 ${esc(t("admin.groupAuditReadOnly", "This administrator audit view is read-only. Every access is recorded in the audit log."))}</div>`
      : "";
    const result = reconcilePrivateMessageList(box, arr, "group", conversationKey, auditNotice, `<div class="kwc-dm-empty">${esc(t("group.emptyRoom", "No messages yet."))}</div>`);
    if (options && options.preserveTop) {
      const delta = Math.max(0, Number(box.scrollHeight || 0) - prevHeight);
      box.scrollTop = prevTop + delta;
    } else if ((!options || options.stickToBottom !== false) && (!result.sameConversation || result.wasNearBottom)) {
      box.scrollTop = box.scrollHeight;
    }
    updateGroupChatComposeControls();
  }

  async function loadGroupChatMessages(roomId) {
    roomId = String(roomId || "").trim();
    if (!state.token || !roomId) return;
    if (state.groupMessagesLoading) return;
    state.groupMessagesLoading = true;
    try {
      const limit = privateMessagePageLimit();
      const path = state.groupAuditMode ? "/admin/group/messages" : "/group/messages";
      const res = await api(path + "?roomId=" + encodeURIComponent(roomId) + "&limit=" + encodeURIComponent(String(limit)));
      state.groupActiveRoomId = roomId;
      state.groupMessages = Array.isArray(res.messages) ? res.messages : [];
      state.groupMessagesHasMore = state.groupMessages.length >= limit;
      renderGroupChatMessages(state.groupMessages, {stickToBottom: true});
      if (!state.groupAuditMode) {
        state.groupUnread = Number(res.unread || 0);
        updateGroupChatButton();
        await api("/group/read", {method: "POST", body: JSON.stringify({roomId})}).catch(() => {});
        await loadGroupChatRooms(true);
        const refreshed = (state.groupRooms || []).find(r => r.id === roomId);
        if (refreshed) state.groupActiveRoom = refreshed;
        renderGroupChatRooms();
        renderGroupChatHeader();
      }
    } catch (e) {
      alertResponse(state.groupAuditMode ? "alert.groupAuditLoadFailed" : "alert.groupLoadFailed", state.groupAuditMode ? "Failed to load group audit: {error}" : "Failed to load group chats: {error}", e.response || {error: e.message || "error"});
    } finally {
      state.groupMessagesLoading = false;
    }
  }

  async function loadOlderGroupChatMessagesFromEdge(box, reason = "") {
    if (!state.token || !state.groupActiveRoomId || state.groupMessagesLoading || !state.groupMessagesHasMore) return false;
    const oldest = privateMessageOldestId(state.groupMessages);
    if (!(oldest > 0)) {
      state.groupMessagesHasMore = false;
      return false;
    }
    if (!box) box = document.getElementById("kwc-group-messages");
    const prevTop = box ? Number(box.scrollTop || 0) : 0;
    const prevHeight = box ? Number(box.scrollHeight || 0) : 0;
    state.groupMessagesLoading = true;
    try {
      const limit = privateMessagePageLimit();
      const path = state.groupAuditMode ? "/admin/group/messages" : "/group/messages";
      const res = await api(path + "?roomId=" + encodeURIComponent(state.groupActiveRoomId) + "&before=" + encodeURIComponent(String(oldest)) + "&limit=" + encodeURIComponent(String(limit)), {timeoutMs: 15000});
      const older = Array.isArray(res.messages) ? res.messages : [];
      const beforeCount = state.groupMessages.length;
      state.groupMessages = mergePrivateMessagePages(older, state.groupMessages);
      const added = Math.max(0, state.groupMessages.length - beforeCount);
      state.groupMessagesHasMore = older.length >= limit;
      if (added > 0) renderGroupChatMessages(state.groupMessages, {preserveTop: true, previousScrollTop: prevTop, previousScrollHeight: prevHeight, stickToBottom: false});
      else state.groupMessagesHasMore = false;
      return added > 0;
    } catch (_) {
      return false;
    } finally {
      state.groupMessagesLoading = false;
      setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-older-loaded"), 80);
    }
  }

  async function retryGroupChatLatestFromBottomEdge(box, reason = "") {
    if (!state.token || !state.groupActiveRoomId || state.groupMessagesLoading || state.groupBottomRetryInFlight) return false;
    const now = Date.now();
    if (now - Number(state.groupLastBottomRetryAt || 0) < 1200) return false;
    state.groupLastBottomRetryAt = now;
    state.groupBottomRetryInFlight = true;
    const beforeNewest = privateMessageNewestId(state.groupMessages);
    try {
      const limit = privateMessagePageLimit();
      const path = state.groupAuditMode ? "/admin/group/messages" : "/group/messages";
      const res = await api(path + "?roomId=" + encodeURIComponent(state.groupActiveRoomId) + "&limit=" + encodeURIComponent(String(limit)), {timeoutMs: 15000});
      const messages = Array.isArray(res.messages) ? res.messages : [];
      const afterNewest = privateMessageNewestId(messages);
      state.groupMessages = messages;
      state.groupMessagesHasMore = messages.length >= limit;
      renderGroupChatMessages(state.groupMessages, {stickToBottom: true});
      if (!state.groupAuditMode && Number(res.unread || 0) >= 0) {
        state.groupUnread = Number(res.unread || 0);
        updateGroupChatButton();
      }
      return afterNewest > beforeNewest;
    } catch (_) {
      return false;
    } finally {
      state.groupBottomRetryInFlight = false;
      setTimeout(() => maybeShowGroupChatEdgeToastFromUserScroll(box, reason || "group-bottom-retried"), 100);
    }
  }

  function groupOptimisticMessage(clientMessageId, message, replyTarget = null, status = "pending", error = "") {
    return {
      id: "local-" + clientMessageId,
      roomId: state.groupActiveRoomId,
      senderUuid: "",
      senderUsername: state.username || "",
      senderDisplayName: state.username || "",
      body: message,
      time: Date.now(),
      deliveryStatus: status,
      deliveryError: error,
      replyToId: replyTarget && replyTarget.id ? replyTarget.id : 0,
      replyToSender: replyTarget && replyTarget.sender ? replyTarget.sender : "",
      replyToPreview: replyTarget && replyTarget.preview ? replyTarget.preview : "",
      clientMessageId
    };
  }

  function updateOptimisticGroupMessage(clientMessageId, status, error = "") {
    const item = (state.groupMessages || []).find(msg => String(msg && msg.clientMessageId || "") === String(clientMessageId || ""));
    if (!item) return false;
    item.deliveryStatus = status;
    item.deliveryError = error || "";
    renderGroupChatMessages(state.groupMessages, {stickToBottom: true});
    return true;
  }

  async function sendGroupChatAttempt(roomId, message, clientMessageId, replyToId = 0) {
    try {
      const body = {roomId, message, clientMessageId};
      if (Number(replyToId || 0) > 0) body.replyToId = Number(replyToId);
      const res = await api("/group/send", {method: "POST", body: JSON.stringify(body)});
      if (res.room) state.groupActiveRoom = res.room;
      await loadGroupChatRooms(true);
      if (state.groupActiveRoomId === roomId) await loadGroupChatMessages(roomId);
      return true;
    } catch (e) {
      const response = e && e.response || {};
      updateOptimisticGroupMessage(clientMessageId, "failed", responseError(response, e.message || "send_failed"));
      return false;
    }
  }

  async function retryGroupChatMessage(messageId) {
    const item = (state.groupMessages || []).find(msg => String(msg && msg.id || "") === String(messageId || ""));
    if (!item || String(item.deliveryStatus || "") !== "failed") return;
    const clientMessageId = String(item.clientMessageId || "").trim();
    if (!clientMessageId) return;
    item.deliveryStatus = "pending";
    item.deliveryError = "";
    renderGroupChatMessages(state.groupMessages, {stickToBottom: true});
    await sendGroupChatAttempt(String(item.roomId || state.groupActiveRoomId || ""), String(item.body || ""), clientMessageId, Number(item.replyToId || 0));
  }

  async function sendGroupChatMessage() {
    if (state.groupAuditMode) return;
    if (!state.token || !state.groupChatEnabled || !state.groupChatAllowWebSend || !state.groupActiveRoomId) return;
    const input = document.getElementById("kwc-group-input");
    if (!input) return;
    let message = String(input.value || "").trim();
    if (!message) return;
    if (state.groupChatMaxMessageLength > 0 && message.length > state.groupChatMaxMessageLength) message = message.slice(0, state.groupChatMaxMessageLength);
    const roomId = state.groupActiveRoomId;
    const clientMessageId = privateClientMessageId("group");
    const replyTarget = state.groupReplyTarget && String(state.groupReplyTarget.conversationId || "") === privateReplyConversationId("group") ? Object.assign({}, state.groupReplyTarget) : null;
    input.value = "";
    clearPrivateReply("group");
    closeGroupChatEmojiPanel();
    state.groupMessages = (state.groupMessages || []).concat([groupOptimisticMessage(clientMessageId, message, replyTarget)]);
    renderGroupChatMessages(state.groupMessages, {stickToBottom: true});
    input.disabled = true;
    try {
      await sendGroupChatAttempt(roomId, message, clientMessageId, replyTarget && replyTarget.id ? replyTarget.id : 0);
    } finally {
      input.disabled = false;
      input.focus();
    }
  }

  function groupVisibilityOptionsHtml(current = "private") {
    const cur = String(current || "private").toLowerCase() === "public" ? "public" : "private";
    if (!state.groupChatAllowPublicRooms) {
      return `<select class="kwc-input" id="kwc-group-form-visibility" disabled><option value="private" selected>${esc(t("group.private", "private"))}</option></select>`;
    }
    return `<select class="kwc-input" id="kwc-group-form-visibility"><option value="private"${cur === "private" ? " selected" : ""}>${esc(t("group.private", "private"))}</option><option value="public"${cur === "public" ? " selected" : ""}>${esc(t("group.public", "public"))}</option></select>`;
  }

  function openGroupRoomForm(options = {}) {
    return new Promise(resolve => {
      const room = options.room || {};
      const isSettings = options.mode === "settings";
      const wrap = document.createElement("div");
      wrap.className = "kwc-modal-backdrop kwc-dm-modal-backdrop kwc-group-form-backdrop";
      applyDetachedModalTheme(wrap);
      const title = isSettings ? t("group.settings", "Settings") : t("group.newRoom", "New room");
      const currentName = isSettings ? groupRoomLabel(room) : "";
      const passwordBlock = state.groupChatAllowRoomPasswords ? `<label class="kwc-group-form-field"><span>${esc(isSettings ? t("group.passwordSettingsLabel", "Password (blank removes it)") : t("group.passwordOptionalLabel", "Password (optional)"))}</span><input class="kwc-input" id="kwc-group-form-password" type="password" autocomplete="new-password"></label>` : "";
      const membershipEventsChecked = !isSettings || room.membershipEventsEnabled !== false;
      const membershipEventsBlock = `<label class="kwc-group-form-toggle"><input type="checkbox" id="kwc-group-form-membership-events" ${membershipEventsChecked ? "checked" : ""}><span>${esc(t("group.membershipEvents", "Show member join/leave notices"))}</span></label>`;
      wrap.innerHTML = `<div class="kwc-modal kwc-group-form-modal"><div class="kwc-group-form-head"><h3>${esc(title)}</h3></div><div class="kwc-group-form-grid"><label class="kwc-group-form-field"><span>${esc(t("group.roomName", "Room name"))}</span><input class="kwc-input" id="kwc-group-form-name" value="${esc(currentName)}" maxlength="80"></label><label class="kwc-group-form-field"><span>${esc(t("group.visibility", "Visibility"))}</span>${groupVisibilityOptionsHtml(room.visibility || "private")}</label>${passwordBlock}${membershipEventsBlock}</div><div class="kwc-row kwc-group-form-actions"><button type="button" class="kwc-button" id="kwc-group-form-save">${esc(t("button.save", "Save"))}</button><button type="button" class="kwc-button" id="kwc-group-form-cancel">${esc(t("button.cancel", "Cancel"))}</button></div></div>`;
      document.body.appendChild(wrap);
      const close = value => { wrap.remove(); resolve(value); };
      wrap.addEventListener("click", event => { if (event.target === wrap) close(null); });
      const nameInput = wrap.querySelector("#kwc-group-form-name");
      const visibilityInput = wrap.querySelector("#kwc-group-form-visibility");
      const passwordInput = wrap.querySelector("#kwc-group-form-password");
      const membershipEventsInput = wrap.querySelector("#kwc-group-form-membership-events");
      const submit = () => {
        const name = String(nameInput && nameInput.value || "").trim();
        if (!name) { if (nameInput) nameInput.focus(); return; }
        const visibility = state.groupChatAllowPublicRooms ? String(visibilityInput && visibilityInput.value || "private").toLowerCase() : "private";
        const out = {name, visibility: visibility === "public" ? "public" : "private", membershipEventsEnabled: !membershipEventsInput || !!membershipEventsInput.checked};
        if (state.groupChatAllowRoomPasswords && passwordInput) out.password = String(passwordInput.value || "");
        close(out);
      };
      wrap.querySelector("#kwc-group-form-save").addEventListener("click", submit);
      wrap.querySelector("#kwc-group-form-cancel").addEventListener("click", () => close(null));
      wrap.addEventListener("keydown", event => {
        if (event.key === "Escape") { event.preventDefault(); close(null); }
        if (event.key === "Enter" && event.target && event.target.tagName !== "SELECT") { event.preventDefault(); submit(); }
      });
      if (nameInput) { setTimeout(() => nameInput.focus(), 0); }
    });
  }

  async function createGroupRoom() {
    const form = await openGroupRoomForm({mode: "create"});
    if (!form) return;
    try {
      const res = await api("/group/create", {method: "POST", body: JSON.stringify({name: form.name, visibility: form.visibility, password: form.password || "", membershipEventsEnabled: form.membershipEventsEnabled !== false})});
      if (res.room) { state.groupActiveRoomId = String(res.room.id || ""); state.groupActiveRoom = res.room; }
      await loadGroupChatRooms(true);
      const refreshed = (state.groupRooms || []).find(r => r.id === state.groupActiveRoomId);
      if (refreshed) state.groupActiveRoom = refreshed;
      renderGroupChatRooms();
      renderGroupChatHeader();
      if (state.groupActiveRoomId) await loadGroupChatMessages(state.groupActiveRoomId);
    } catch (e) { alertResponse("alert.groupCreateFailed", "Failed to create room: {error}", e.response || {error: e.message || "error"}); }
  }

  async function invitePlayerToGroupRoom(targetUuid, label = "") {
    if (!state.groupActiveRoomId) return;
    targetUuid = String(targetUuid || "").trim();
    if (!targetUuid) return;
    const plainLabel = directMessagePlainLabel(label) || targetUuid;
    if (!confirmPlain(fmt("group.confirmInvite", "Invite {player} to this group chat?", {player: plainLabel}))) return;
    try {
      await api("/group/invite", {method: "POST", body: JSON.stringify({roomId: state.groupActiveRoomId, targetUuid})});
      closeGroupPlayerSearch();
      alert(label ? fmt("group.inviteSentTo", "Invitation sent to {player}.", {player: plainLabel}) : t("group.inviteSent", "Invitation sent."));
      await loadGroupChatRooms(true);
    } catch (e) {
      alertResponse("alert.groupInviteFailed", "Failed to invite player: {error}", e.response || {error: e.message || "error"});
    }
  }

  async function inviteToGroupRoom() {
    openGroupPlayerSearch();
  }

  async function leaveGroupRoom() {
    if (!state.groupActiveRoomId) return;
    if (state.groupChatConfirmLeave && !confirmPlain(t("group.confirmLeave", "Leave this group chat?"))) return;
    const roomId = state.groupActiveRoomId;
    try { await api("/group/leave", {method: "POST", body: JSON.stringify({roomId})}); state.groupActiveRoomId = ""; state.groupActiveRoom = null; renderGroupChatMessages([]); renderGroupChatHeader(); await loadGroupChatRooms(true); }
    catch (e) { alertResponse("alert.groupLeaveFailed", "Failed to leave room: {error}", e.response || {error: e.message || "error"}); }
  }

  async function updateGroupRoomSettings() {
    const room = state.groupActiveRoom || (state.groupRooms || []).find(r => r.id === state.groupActiveRoomId);
    if (!room) return;
    const form = await openGroupRoomForm({mode: "settings", room});
    if (!form) return;
    const body = {roomId: state.groupActiveRoomId, name: form.name, visibility: form.visibility, membershipEventsEnabled: form.membershipEventsEnabled !== false};
    if (state.groupChatAllowRoomPasswords) body.password = form.password || "";
    try {
      const res = await api("/group/settings", {method: "POST", body: JSON.stringify(body)});
      if (res.room) { state.groupActiveRoom = res.room; state.groupActiveRoomId = String(res.room.id || state.groupActiveRoomId || ""); }
      await loadGroupChatRooms(true);
      const refreshed = (state.groupRooms || []).find(r => r.id === state.groupActiveRoomId);
      if (refreshed) state.groupActiveRoom = refreshed;
      renderGroupChatRooms();
      renderGroupChatHeader();
    }
    catch (e) { alertResponse("alert.groupSettingsFailed", "Failed to update room: {error}", e.response || {error: e.message || "error"}); }
  }

  async function respondGroupInvite(inviteId, accept) {
    try { const res = await api("/group/invite/respond", {method: "POST", body: JSON.stringify({inviteId, accept})}); if (accept && res.room) { state.groupActiveRoomId = res.room.id; state.groupActiveRoom = res.room; } await loadGroupChatRooms(true); if (state.groupActiveRoomId) await loadGroupChatMessages(state.groupActiveRoomId); }
    catch (e) { alertResponse("alert.groupInviteFailed", "Failed to update invitation: {error}", e.response || {error: e.message || "error"}); }
  }

  async function hideGroupMessage(messageId) {
    if (state.groupAuditMode) return;
    messageId = String(messageId || "").trim();
    if (!messageId || !state.token) return;
    if (state.groupChatConfirmHide && !confirmPlain(t("group.confirmHideMessage", "Hide this message from your view?"))) return;
    try { await api("/group/hide-message", {method: "POST", body: JSON.stringify({messageId})}); if (state.groupActiveRoomId) await loadGroupChatMessages(state.groupActiveRoomId); await loadGroupChatRooms(true); }
    catch (e) { alertResponse("alert.groupHideFailed", "Failed to hide message: {error}", e.response || {error: e.message || "error"}); }
  }

  async function openGroupChatModal() {
    if (!state.token) { openLoginModal(); return; }
    if (!state.groupChatEnabled) return;
    if (state.groupModalOpen) return;
    state.groupModalOpen = true;
    state.groupActiveRoomId = "";
    state.groupActiveRoom = null;
    state.groupAuditMode = false;
    state.groupAuditRoom = null;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-dm-modal-backdrop kwc-group-modal-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-panel-height", emojiPanelHeightPx() + "px");
    wrap.style.setProperty("--kwc-emoji-panel-min-height", emojiPanelMinHeightPx() + "px");
    wrap.innerHTML = `<div class="kwc-modal kwc-dm-modal kwc-group-modal"><div class="kwc-dm-head"><h3 class="kwc-dm-main-title"><span>${esc(t("group.title", "Group chats"))}</span><span class="kwc-dm-retention" title="${esc(groupRoomRetentionText())}">${esc(groupRoomRetentionText())}</span></h3><button class="kwc-button" id="kwc-group-close">${esc(t("button.close", "Close"))}</button></div><div class="kwc-dm-layout"><aside class="kwc-dm-sidebar"><button type="button" class="kwc-button kwc-dm-new" id="kwc-group-create">${esc(t("group.newRoom", "New room"))}</button><div class="kwc-group-invites" id="kwc-group-invites"></div><div class="kwc-dm-thread-list" id="kwc-group-room-list"></div></aside><section class="kwc-dm-conversation"><div class="kwc-dm-title kwc-group-title" id="kwc-group-title">${esc(t("group.selectRoom", "Select a room"))}</div><div class="kwc-dm-messages" id="kwc-group-messages"></div><div class="kwc-emoji-resize-handle kwc-dm-emoji-resize kwc-hidden" id="kwc-group-emoji-resize" title="${esc(t("button.resizeEmojiPanel", "Drag to resize emoji picker"))}" aria-label="${esc(t("button.resizeEmojiPanel", "Drag to resize emoji picker"))}"></div><div class="kwc-reply-compose kwc-private-reply-compose kwc-hidden" id="kwc-group-reply-compose"><button type="button" class="kwc-reply-compose-main" id="kwc-group-reply-compose-main" title="${esc(t("reply.jump", "Jump to replied message"))}"><span class="kwc-reply-compose-label" id="kwc-group-reply-compose-label"></span><span class="kwc-reply-compose-preview" id="kwc-group-reply-compose-preview"></span></button><button type="button" class="kwc-mini-action kwc-reply-cancel" id="kwc-group-reply-cancel" title="${esc(t("button.cancel", "Cancel"))}">×</button></div><div class="kwc-dm-compose kwc-row"><textarea class="kwc-input kwc-chat-composer" id="kwc-group-input" rows="1" autocomplete="off" enterkeyhint="send" placeholder="${esc(t("placeholder.message", "message"))}" ${state.groupChatMaxMessageLength > 0 ? `maxlength="${state.groupChatMaxMessageLength}"` : ""}></textarea><button class="kwc-button kwc-dm-emoji-button kwc-hidden" id="kwc-group-emoji" title="${esc(t("button.emoji", "Emoji"))}">☺</button><button class="kwc-button kwc-dm-upload kwc-hidden" id="kwc-group-upload" title="${esc(t("button.upload", "Attach"))}">&#128206;</button><button class="kwc-button kwc-dm-send" id="kwc-group-send">${esc(t("button.send", "Send"))}</button><input type="file" id="kwc-group-file" class="kwc-file-input" multiple hidden style="display:none !important;"></div><div class="kwc-emoji-panel kwc-dm-emoji-panel kwc-group-emoji-panel kwc-hidden" id="kwc-group-emoji-panel" aria-live="polite"></div>${uploadProgressHtml("kwc-group-upload-progress")}</section></div><div class="kwc-dm-search-panel kwc-hidden" id="kwc-group-search-panel"><div class="kwc-dm-search-head"><strong>${esc(t("group.searchPlayer", "Search player to invite"))}</strong><button class="kwc-button" id="kwc-group-search-close" type="button">${esc(t("button.close", "Close"))}</button></div><input class="kwc-input" id="kwc-group-search" placeholder="${esc(t("group.searchPlayer", "Search player to invite"))}"><div class="kwc-dm-player-results" id="kwc-group-player-results"></div></div></div>`;
    document.body.appendChild(wrap);
    installDirectMessageIdentityToggleGuard(wrap);
    const close = () => {
      hideEmojiAutocomplete();
      closeGroupChatEmojiPanel();
      closeGroupPlayerSearch();
      hideGroupChatEdgeToast(true);
      discardPrivateMessageDom(wrap.querySelector("#kwc-group-messages"));
      wrap.remove();
      state.groupModalOpen = false;
      state.groupActiveRoomId = "";
      state.groupActiveRoom = null;
      state.groupAuditMode = false;
      state.groupAuditRoom = null;
      state.groupReplyTarget = null;
      if (state.activeComposeInputId === "kwc-group-input") state.activeComposeInputId = "kwc-message";
    };
    wrap.querySelector("#kwc-group-close").onclick = close;
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });
    wrap.addEventListener("click", e => {
      if (!state.groupSearchPanelOpen) return;
      const panel = wrap.querySelector("#kwc-group-search-panel");
      const inviteButton = wrap.querySelector("#kwc-group-invite");
      const target = e.target;
      if (panel && panel.contains(target)) return;
      if (inviteButton && inviteButton.contains(target)) return;
      closeGroupPlayerSearch();
    });
    wrap.querySelector("#kwc-group-create").onclick = createGroupRoom;
    wrap.querySelector("#kwc-group-send").onclick = sendGroupChatMessage;
    const groupReplyCancel = wrap.querySelector("#kwc-group-reply-cancel");
    if (groupReplyCancel) groupReplyCancel.onclick = () => clearPrivateReply("group");
    const groupReplyMain = wrap.querySelector("#kwc-group-reply-compose-main");
    if (groupReplyMain) groupReplyMain.onclick = () => {
      const target = privateReplyState("group");
      if (target && target.id) jumpToPrivateReplyTarget(target.id, "group");
    };
    const roomList = wrap.querySelector("#kwc-group-room-list");
    if (roomList) roomList.addEventListener("click", handleGroupRoomListClick);
    const searchClose = wrap.querySelector("#kwc-group-search-close");
    if (searchClose) searchClose.addEventListener("click", closeGroupPlayerSearch);
    const search = wrap.querySelector("#kwc-group-search");
    if (search) search.addEventListener("input", () => {
      clearTimeout(state.groupSearchTimer);
      state.groupSearchTimer = setTimeout(() => searchGroupPlayers(search.value), 180);
    });
    window.addEventListener("resize", () => {
      if (state.groupModalOpen && state.groupSearchPanelOpen) syncGroupPlayerSearchPanelSize();
    }, {passive: true});
    const groupEmoji = wrap.querySelector("#kwc-group-emoji");
    if (groupEmoji) groupEmoji.addEventListener("click", () => toggleGroupChatEmojiPanel());
    const groupUpload = wrap.querySelector("#kwc-group-upload");
    const groupFile = wrap.querySelector("#kwc-group-file");
    if (groupUpload) {
      groupUpload.addEventListener("click", () => {
        setActiveComposeInput("kwc-group-input");
        if (groupFile) groupFile.click();
      });
    }
    if (groupFile) {
      groupFile.accept = uploadAcceptList();
      groupFile.addEventListener("change", async e => {
        setActiveComposeInput("kwc-group-input");
        await uploadSelectedFiles(e);
      });
    }
    const groupUploadCancel = wrap.querySelector("#kwc-group-upload-progress-cancel");
    if (groupUploadCancel) groupUploadCancel.addEventListener("click", cancelCurrentUpload);
    const input = wrap.querySelector("#kwc-group-input");
    input.addEventListener("focus", () => setActiveComposeInput(input));
    input.addEventListener("input", () => normalizeSingleLineComposer(input));
    input.addEventListener("paste", async e => {
      setActiveComposeInput(input);
      await handlePasteUpload(e);
    });
    input.addEventListener("keydown", e => {
      if (e.key === "Escape") {
        closeGroupChatEmojiPanel();
        if (state.groupReplyTarget) clearPrivateReply("group");
        return;
      }
      if (e.key !== "Enter" || e.isComposing || e.keyCode === 229) return;
      e.preventDefault();
      closeGroupChatEmojiPanel();
      sendGroupChatMessage();
    });
    installDirectMessageWindowDrag(wrap);
    installGroupChatDragAndDropUpload(wrap);
    installGroupChatEmojiPanelResize(wrap);
    installGroupChatEdgeToasts(wrap);
    updateGroupChatComposeControls();
    await loadGroupChatRooms(true);
    renderGroupChatRooms();
    renderGroupChatHeader();
    renderGroupChatMessages([]);
    updateGroupChatComposeControls();
  }

  async function openDirectMessageModal() {
    if (!state.token) {
      openLoginModal();
      return;
    }
    if (!state.directMessageEnabled) return;
    if (state.dmModalOpen) return;
    state.dmModalOpen = true;
    state.dmActiveThreadId = "";
    state.dmDraftTarget = null;
    state.dmAuditMode = false;
    state.dmAuditThread = null;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-dm-modal-backdrop";
    applyDetachedModalTheme(wrap);
    // The DM modal is attached to document.body instead of inside #kwc-root.
    // Copy live emoji size variables explicitly so DM rendering/picker follows
    // the same emoji.render-size-px and emoji.picker-size-px settings as public chat.
    wrap.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-panel-height", emojiPanelHeightPx() + "px");
    wrap.style.setProperty("--kwc-emoji-panel-min-height", emojiPanelMinHeightPx() + "px");
    wrap.innerHTML = `
      <div class="kwc-modal kwc-dm-modal">
        <div class="kwc-dm-head">
          <h3 class="kwc-dm-main-title"><span>${t("dm.title", "Messages")}</span><span class="kwc-dm-retention" id="kwc-dm-retention" title="${esc(directMessageRetentionNoticeText())}">${esc(directMessageRetentionNoticeText())}</span></h3>
          <div class="kwc-dm-head-actions">
            <button class="kwc-button" id="kwc-dm-close">${t("button.close", "Close")}</button>
          </div>
        </div>
        <div class="kwc-dm-layout">
          <aside class="kwc-dm-sidebar" id="kwc-dm-sidebar">
            <button type="button" class="kwc-button kwc-dm-new" id="kwc-dm-new">${t("dm.newMessage", "New message")}</button>
            <div class="kwc-dm-thread-list" id="kwc-dm-thread-list"></div>
          </aside>
          <section class="kwc-dm-conversation">
            <div class="kwc-dm-title" id="kwc-dm-title">${t("dm.selectThread", "Select a thread")}</div>
            <div class="kwc-dm-messages" id="kwc-dm-messages"></div>
            <div class="kwc-emoji-resize-handle kwc-dm-emoji-resize kwc-hidden" id="kwc-dm-emoji-resize" title="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}" aria-label="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}"></div>
            <div class="kwc-reply-compose kwc-private-reply-compose kwc-hidden" id="kwc-dm-reply-compose"><button type="button" class="kwc-reply-compose-main" id="kwc-dm-reply-compose-main" title="${t("reply.jump", "Jump to replied message")}"><span class="kwc-reply-compose-label" id="kwc-dm-reply-compose-label"></span><span class="kwc-reply-compose-preview" id="kwc-dm-reply-compose-preview"></span></button><button type="button" class="kwc-mini-action kwc-reply-cancel" id="kwc-dm-reply-cancel" title="${t("button.cancel", "Cancel")}">×</button></div>
            <div class="kwc-dm-compose kwc-row">
              <textarea class="kwc-input kwc-chat-composer" id="kwc-dm-input" rows="1" autocomplete="off" enterkeyhint="send" placeholder="${t("placeholder.message", "message")}" ${state.directMessageMaxMessageLength > 0 ? `maxlength="${state.directMessageMaxMessageLength}"` : ""}></textarea>
              <button class="kwc-button kwc-dm-emoji-button kwc-hidden" id="kwc-dm-emoji" title="${t("button.emoji", "Emoji")}">☺</button>
              <button class="kwc-button kwc-dm-upload kwc-hidden" id="kwc-dm-upload" title="${t("button.upload", "Attach")}">&#128206;</button>
              <button class="kwc-button kwc-dm-send" id="kwc-dm-send">${t("button.send", "Send")}</button>
              <input type="file" id="kwc-dm-file" class="kwc-file-input" multiple hidden style="display:none !important;">
            </div>
            <div class="kwc-emoji-panel kwc-dm-emoji-panel kwc-hidden" id="kwc-dm-emoji-panel" aria-live="polite"></div>
            ${uploadProgressHtml("kwc-dm-upload-progress")}
          </section>
        </div>
        <div class="kwc-dm-search-panel kwc-hidden" id="kwc-dm-search-panel">
          <div class="kwc-dm-search-head">
            <strong>${t("dm.searchPlayer", "Search player")}</strong>
            <button class="kwc-button" id="kwc-dm-search-close" type="button">${t("button.close", "Close")}</button>
          </div>
          <input class="kwc-input" id="kwc-dm-search" placeholder="${t("dm.searchPlayer", "Search player")}">
          <div class="kwc-dm-player-results" id="kwc-dm-player-results"></div>
        </div>
      </div>`;
    document.body.appendChild(wrap);
    installDirectMessageIdentityToggleGuard(wrap);
    const close = () => { hideEmojiAutocomplete(); closeDirectMessageEmojiPanel(); closeDirectMessagePlayerSearch(); hideDirectMessageEdgeToast(true); discardPrivateMessageDom(wrap.querySelector("#kwc-dm-messages")); wrap.remove(); state.dmModalOpen = false; state.dmAuditMode = false; state.dmAuditThread = null; state.dmReplyTarget = null; if (state.activeComposeInputId === "kwc-dm-input") state.activeComposeInputId = "kwc-message"; };
    wrap.querySelector("#kwc-dm-close").onclick = close;
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });
    wrap.addEventListener("click", e => {
      if (!state.dmSearchPanelOpen) return;
      const panel = wrap.querySelector("#kwc-dm-search-panel");
      const newButton = wrap.querySelector("#kwc-dm-new");
      const target = e.target;
      if (panel && panel.contains(target)) return;
      if (newButton && newButton.contains(target)) return;
      closeDirectMessagePlayerSearch();
    });
    const title = wrap.querySelector("#kwc-dm-title");
    if (title) {
      title.addEventListener("click", e => {
        if (e && e.target && e.target.closest && e.target.closest(senderIdentitySelector())) return;
        returnDirectMessageToList();
      });
      title.addEventListener("keydown", e => {
        if (e.target && e.target.closest && e.target.closest(senderIdentitySelector())) return;
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          returnDirectMessageToList();
        }
      });
    }
    updateDirectMessageViewMode();
    const newBtn = wrap.querySelector("#kwc-dm-new");
    if (newBtn) newBtn.addEventListener("click", openDirectMessagePlayerSearch);
    window.addEventListener("resize", () => {
      if (state.dmModalOpen && state.dmSearchPanelOpen) syncDirectMessagePlayerSearchPanelSize();
    }, {passive: true});
    const searchClose = wrap.querySelector("#kwc-dm-search-close");
    if (searchClose) searchClose.addEventListener("click", closeDirectMessagePlayerSearch);
    const search = wrap.querySelector("#kwc-dm-search");
    if (search) search.addEventListener("input", () => {
      clearTimeout(state.dmSearchTimer);
      state.dmSearchTimer = setTimeout(() => searchDirectMessagePlayers(search.value), 180);
    });
    wrap.querySelector("#kwc-dm-send").onclick = sendDirectMessageFromModal;
    const dmReplyCancel = wrap.querySelector("#kwc-dm-reply-cancel");
    if (dmReplyCancel) dmReplyCancel.onclick = () => clearPrivateReply("dm");
    const dmReplyMain = wrap.querySelector("#kwc-dm-reply-compose-main");
    if (dmReplyMain) dmReplyMain.onclick = () => {
      const target = privateReplyState("dm");
      if (target && target.id) jumpToPrivateReplyTarget(target.id, "dm");
    };
    const dmEmoji = wrap.querySelector("#kwc-dm-emoji");
    if (dmEmoji) {
      dmEmoji.addEventListener("click", () => toggleDirectMessageEmojiPanel());
    }
    const dmUpload = wrap.querySelector("#kwc-dm-upload");
    const dmFile = wrap.querySelector("#kwc-dm-file");
    if (dmUpload) {
      dmUpload.addEventListener("click", () => {
        setActiveComposeInput("kwc-dm-input");
        if (dmFile) dmFile.click();
      });
    }
    if (dmFile) {
      dmFile.accept = uploadAcceptList();
      dmFile.addEventListener("change", async e => {
        setActiveComposeInput("kwc-dm-input");
        await uploadSelectedFiles(e);
      });
    }
    const dmUploadCancel = wrap.querySelector("#kwc-dm-upload-progress-cancel");
    if (dmUploadCancel) dmUploadCancel.addEventListener("click", cancelCurrentUpload);
    const dmInput = wrap.querySelector("#kwc-dm-input");
    dmInput.addEventListener("focus", () => setActiveComposeInput(dmInput));
    dmInput.addEventListener("input", () => normalizeSingleLineComposer(dmInput));
    dmInput.addEventListener("paste", async e => {
      setActiveComposeInput(dmInput);
      await handlePasteUpload(e);
    });
    dmInput.addEventListener("keydown", e => {
      if (e.key === "Escape") {
        closeDirectMessageEmojiPanel();
        if (state.dmReplyTarget) clearPrivateReply("dm");
        return;
      }
      if (e.key !== "Enter" || e.isComposing || e.keyCode === 229) return;
      e.preventDefault();
      closeDirectMessageEmojiPanel();
      sendDirectMessageFromModal();
    });
    installDirectMessageWindowDrag(wrap);
    installDirectMessageDragAndDropUpload(wrap);
    installDirectMessageEmojiPanelResize(wrap);
    installDirectMessageEdgeToasts(wrap);
    updateDirectMessageComposeControls();
    await loadDirectMessageThreads(true);
    renderDirectMessageThreads();
    renderDirectMessageMessages([]);
    updateDirectMessageViewMode();
  }

  function openSetPasswordModal() {
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal">
        <h3>${t("password.title", "Set password")}</h3>
        <p>${t("password.description", "Set a web password so you can log in without joining the game next time.")}</p>
        <input class="kwc-input" id="kwc-new-pw" type="password" placeholder="${t("placeholder.newPassword", "new password")}">
        <br><br>
        <button class="kwc-button" id="kwc-save-pw">${t("button.save", "Save")}</button>
        <button class="kwc-button" id="kwc-skip-pw">${t("button.skip", "Skip")}</button>
      </div>
    `;
    document.body.appendChild(wrap);
    wrap.querySelector("#kwc-save-pw").onclick = async () => {
      const password = wrap.querySelector("#kwc-new-pw").value;
      const res = await api("/auth/set-password", {method: "POST", body: JSON.stringify({password})});
      if (!res.ok) {
        alertResponse("alert.saveFailed", "Save failed: {error}", res);
        return;
      }
      wrap.remove();
    };
    wrap.querySelector("#kwc-skip-pw").onclick = () => wrap.remove();
  }

  function openAccountModal() {
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-account-modal">
        <h3>${esc(state.username)}</h3>
        <p>${t("account.role", "Role")}: ${esc(state.role)}</p>
        <div class="kwc-account-actions">
          ${(!state.config || state.config.uiUserPreferencesControl !== false) ? `<button class="kwc-button" id="kwc-user-prefs">${t("preferences.title", "Chat settings")}</button>` : ""}
          <button class="kwc-button" id="kwc-set-pw">${t("button.setPassword", "Set password")}</button>
          <button class="kwc-button" id="kwc-logout">${t("button.logout", "Logout")}</button>
          <button class="kwc-button" id="kwc-close">${t("button.close", "Close")}</button>
        </div>
      </div>
    `;
    document.body.appendChild(wrap);
    wrap.querySelector("#kwc-close").onclick = () => { wrap.remove(); state.loginModalOpen = false; };
    const prefsBtn = wrap.querySelector("#kwc-user-prefs");
    if (prefsBtn) prefsBtn.onclick = () => {
      wrap.remove();
      state.loginModalOpen = false;
      state.prefsModalOpen = false;
      openUserPreferencesModal();
    };
    wrap.querySelector("#kwc-set-pw").onclick = () => { wrap.remove(); openSetPasswordModal(); };
    wrap.querySelector("#kwc-logout").onclick = async () => {
      try { await api("/auth/logout", {method: "POST", body: JSON.stringify({})}); } catch (_) {}
      handleAuthExpired("logout");
      await loadCommands();
      await refreshCaptcha();
      wrap.remove();
    };
  }

  function setLogin(res) {
    state.token = res.token;
    state.username = res.username;
    state.role = res.role;
    localStorage.setItem("kwc.token", state.token);
    localStorage.setItem("kwc.username", state.username);
    localStorage.setItem("kwc.role", state.role);
    updateLoginState();
    updateGuestVisibility();
    refreshCaptcha();
    loadPins();
    loadCommands();
    loadDirectMessageThreads(true);
    loadGroupChatRooms(true);
    loadAccountNotificationPreferences().then(() => { if (!state.isPip) ensurePreferredWebPush().catch(() => {}); }).catch(() => {});
    if (!state.isPip) connectStream({refreshAfterOpen: true, reason: "login"});
  }

  async function verifyStoredToken() {
    if (!state.token) return false;
    try {
      const res = await api("/auth/me");
      if (!res.ok) {
        handleAuthExpired("verify", {reconnect: false});
        return false;
      } else {
        state.username = res.username;
        state.role = res.role;
        localStorage.setItem("kwc.username", state.username || "");
        localStorage.setItem("kwc.role", state.role || "");
      }
    } catch (_) {
      return false;
    }
    updateLoginState();
    updateGuestVisibility();
    return true;
  }

  if (typeof navigator !== "undefined" && navigator.serviceWorker) {
    navigator.serviceWorker.addEventListener("message", event => {
      const data = event && event.data || {};
      if (!data || (data.source !== "KWC" && data.source !== "KWCParent") || data.type !== "notificationNavigate") return;
      navigateFromNotification(data.url || data);
    });
  }

  window.addEventListener("message", event => {
    if (!trustedParentMessageEvent(event)) return;
    const data = event && event.data || {};
    if (!data || (data.source !== "KWC" && data.source !== "KWCParent") || data.type !== "notificationNavigate") return;
    navigateFromNotification(data.url || data);
  });


  async function start() {
    try { localStorage.removeItem("kwc.loginRequiredUntilLogin"); } catch (_) {}
    if (state.captchaPass === "frontend-ok") {
      state.captchaPass = "";
      try { localStorage.removeItem("kwc.captchaPass"); } catch (_) {}
    }
    installFrameFocusBridge();
    installMapPointerRelayBridge();
    installParentResizeBridge();
    installResumeRefreshHandlers();
    await loadConfig();
    await loadLang();
    makeRoot();
    await loadEmojis();
    installTimeDisplayDelegation();
    updateFrameSize();
    updateGuestVisibility();
    await refreshCaptcha();
    const verified = await verifyStoredToken();
    if (verified) await loadAccountNotificationPreferences();
    await loadPins();
    await loadCommands();
    await loadDirectMessageThreads(true);
    await loadGroupChatRooms(true);
    if (!guestChatHidden()) await loadHistory();
    await navigateFromNotification(window.location.href);
    if (state.isPip && standalonePipRelayId) {
      // PiP is a live mirror of the original standalone connection. Do not open
      // a second EventSource or register another ServiceWorker/Web Push client.
      installStandalonePipRelaySubscriber();
      updateLoginState();
    } else {
      connectStream();
      // Web Push is optional. Browser push-service registration can take several
      // seconds or fail transiently, so it must never block the chat UI startup.
      ensurePreferredWebPush().catch(() => {});
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
