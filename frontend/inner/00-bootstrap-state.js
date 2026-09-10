// KWC inner runtime bundle source is maintained as ordered fragments under frontend/inner/.
// Edit the fragments, then run: node tools/build-inner-bundle.js --write
// [KWC 유지보수 주석 / KWC maintenance notes]
// 이 파일은 KWC iframe 런타임의 가장 먼저 실행되는 조각으로, 전역 설정·브라우저 저장소 마이그레이션·공유 state 객체의 초기값을 만든다.
// This is the first fragment executed by the KWC iframe runtime; it initializes global configuration, browser-state migration, and the shared state object.
// 뒤쪽 fragment들은 이 파일에서 만든 cfg/state/상수/기본 유틸리티를 같은 IIFE lexical scope에서 직접 참조하므로 manifest 순서를 바꾸면 안 된다.
// Later fragments reference cfg/state/constants/basic helpers from this file through the same IIFE lexical scope, so the manifest order must not be changed casually.
// API base 정규화는 BlueMap·standalone·reverse proxy 배치에 모두 영향을 주며, 잘못된 기본값은 모든 /api 요청과 SSE 연결을 동시에 깨뜨릴 수 있다.
// API-base normalization affects BlueMap, standalone, and reverse-proxy deployments; an incorrect base can break every /api request and the SSE connection at once.
// localStorage 마이그레이션은 구 BMWC 키를 KWC 키로 한 번만 옮기고 원본을 제거하며, 실패해도 채팅 자체가 중단되지 않도록 예외를 의도적으로 무시한다.
// The localStorage migration moves legacy BMWC keys to KWC keys once and removes the originals; failures are intentionally ignored so chat startup is not blocked.

(() => {
  const KWC_INNER_IFRAME_MARKER_297 = true;
  const cfg = window.KokotoWebChatConfig || window.BlueMapWebChatConfig || {};
  // 구 BMWC localStorage를 현재 KWC namespace로 이전한다. 같은 키가 이미 있으면 현재 값을 우선하고, 이전 실패는 startup을 막지 않는다.
  // Migrates legacy BMWC localStorage into the KWC namespace. Existing KWC values win, and migration failures do not block startup.
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

  // 설정에서 받은 API base를 canonical 형태로 만든다. uploads/emojis 같은 resource suffix가 실수로 포함되어도 제거해 모든 API 호출이 같은 root를 사용하게 한다.

  // Canonicalizes the configured API base. Accidental resource suffixes such as uploads/emojis are removed so every API call uses the same root.

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
    // A persisted bearer token is untrusted until /auth/me verifies it for this page load.
    // Keep it separate so cached credentials cannot expose account-only UI/private state before server verification.
    token: "",
    authPendingToken: localStorage.getItem("kwc.token") || "",
    authVerified: false,
    username: localStorage.getItem("kwc.username") || "",
    userUuid: "",
    role: localStorage.getItem("kwc.role") || "",
    guestName: localStorage.getItem("kwc.guestName") || "",
    captcha: null,
    captchaPass: localStorage.getItem("kwc.captchaPass") || "",
    isPip: runtimeMode.pip,
    isStandalone: runtimeMode.standalone,
    hostPageVisible: true,
    hostPageFocused: true,
    minimized: runtimeMode.pip ? false : localStorage.getItem("kwc.minimized") === "1",
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
    conversationArchiveEnabled: false,
    typingUserDisplayControl: false,
    typingDisplayEnabled: true,
    typingPreferenceLoaded: false,
    presenceInvisible: false,
    presenceStatus: "online",
    presencePreferenceLoaded: false,
    presenceRefreshTimer: null,
    loggedInCount: 0,
    blockedUsers: [],
    blockedUserUuids: [],
    adminCapabilities: {},
    typingOpenChatEnabled: false,
    typingDmEnabled: true,
    typingGroupChatEnabled: true,
    frameMinimizedHeight: 48,
    frameNormalWidth: 372,
    frameNormalHeight: 462,
    resizeStart: null,
    themeSyncTimer: null,
    loginModalOpen: false,
    prefsModalOpen: false,
    searchModalOpen: false,
    lastLoginButtonActivateAt: 0,
    dragStart: null,
    chatWindowZ: 1000,
    messages: [],
    replyTarget: null,
    pins: [],
    // Event announcement cards outlive the event record itself. Keep a page-local tombstone set
    // so deleted/not-found events render as unavailable instead of repeatedly issuing 404 lookups.
    unavailableChatGames: new Set(),
    pinsEnabled: true,
    pinsCanPin: false,
    moderationActionsVisible: false,
    selfMessageDeleteEnabled: false,
    selfMessageDeleteWindowMinutes: 0,
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
    directMessageConfirmDelete: true,
    dmUnread: 0,
    dmThreads: [],
    dmAdminThreads: [],
    dmCleanupPreview: null,
    privateChatContentAccess: false,
    chatViewPersistenceInstalled: false,
    chatViewRestoreInProgress: false,
    dmModalOpen: false,
    privateMultiWindowMinWidth: 900,
    privateMultiWindowMinHeight: 480,
    privateMultiWindowResizeInstalled: false,
    dmConversationWindows: new Map(),
    dmActiveConversationWindow: "",
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
    publicTypingNextAllowedAt: 0,
    publicTypingInFlight: false,
    publicTypingEntries: new Map(),
    publicTypingTimer: null,
    publicTypingClientId: "pt-" + Math.random().toString(36).slice(2) + Date.now().toString(36),
    dmTypingNextAllowedAt: 0,
    dmTypingEntry: null,
    dmTypingTimer: null,

    groupChatEnabled: false,
    groupChatAllowWebSend: true,
    groupChatAllowPublicRooms: true,
    groupChatAllowRoomPasswords: true,
    groupChatMaxMessageLength: 500,
    groupChatRetentionDays: 30,
    groupChatConfirmLeave: true,
    groupChatConfirmDelete: true,
    groupPinsEnabled: true,
    groupPinsCanPin: false,
    groupPins: [],
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
    groupConversationWindows: new Map(),
    groupActiveConversationWindow: "",
    groupActiveRoomId: "",
    groupActiveRoom: null,
    groupPolicyOverride: null,
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
    groupTypingNextAllowedAt: 0,
    groupTypingEntries: new Map(),
    groupTypingTimer: null,
    groupNotificationSeen: new Set(),
    groupScrollbarDragActive: false,
    groupScrollbarDragLastX: null,
    groupScrollbarDragLastY: null,

    archiveSelection: null,
    archiveSelectionHandler: null,
    archiveSelectionBanner: null,
    archiveModalOpen: false,

    reactionCatalog: null,
    reactionCatalogLoadedAt: 0,
    reactionPending: new Map(),
    reactionPickerCategory: "smileys",
    reactionRecent: (() => {
      try {
        const raw = JSON.parse(localStorage.getItem("kwc.reactionRecent") || "[]");
        return Array.isArray(raw) ? raw.map(String).filter(Boolean).slice(0, 24) : [];
      } catch (_) { return []; }
    })(),

    activeComposeInputId: "kwc-message",

    emojiEnabled: false,
    emojiShowButton: true,
    emojiFavoritesEnabled: true,
    emojiFavoritesStorage: "account",
    emojiFavoritesMaxPerAccount: 100,
    emojiFavoritesLoaded: false,
    emojiRenderSizePx: 32,
    emojiPickerSizePx: 44,
    emojiMessageTokenLimit: 12,
    emojiTokenFormat: "short",
    emojiPacks: [],
    emojiItems: [],
    emojiById: new Map(),
    emojiByAlias: new Map(),
    emojiPanelOpen: false,
    emojiSelectedPack: localStorage.getItem("kwc.emojiPack") || "",
    emojiSearchOpen: false,
    emojiSearchQuery: "",
    emojiRecent: (() => {
      try {
        const raw = JSON.parse(localStorage.getItem("kwc.emojiRecent") || "[]");
        return Array.isArray(raw) ? raw.map(String).filter(Boolean).slice(0, 24) : [];
      } catch (_) { return []; }
    })(),
    emojiFavorites: (() => {
      try {
        const raw = JSON.parse(localStorage.getItem("kwc.emojiFavorites") || "[]");
        return Array.isArray(raw) ? raw.map(String).filter(Boolean) : [];
      } catch (_) { return []; }
    })(),
    dmEmojiSelectedPack: "",
    dmEmojiSearchOpen: false,
    dmEmojiSearchQuery: "",
    groupEmojiSearchOpen: false,
    groupEmojiSearchQuery: "",
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
    nonScrollLayoutUntil: 0,
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
    browserNotificationsNotifyReactions: true,
    browserNotificationsNotifySystem: true,
    browserNotificationsNotifyKeywords: true,
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
    webPushNotifyReactions: true,
    webPushNotifySystem: true,
    webPushNotifyKeywords: true,
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
    notificationInboxUnread: 0,
    notificationAccountDmViews: new Map(),
    notificationAccountGroupViews: new Map()
  };
