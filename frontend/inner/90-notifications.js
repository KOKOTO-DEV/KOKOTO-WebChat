// [KWC 유지보수 주석 / KWC maintenance notes]
// 브라우저 Notification API, 로컬 알림함, Web Push eligibility, 계정 전체 active-private-view 억제 규칙을 담당한다.
// This fragment handles Browser Notification API output, the local notification inbox, Web Push eligibility, and account-wide active-private-view suppression.
// DM/그룹 알림은 같은 계정의 어느 활성 KWC 화면이 정확한 대화를 보고 있으면 억제되어야 하며, 단순히 현재 탭의 modal 상태만 보면 안 된다.
// DM/group alerts must be suppressed when any active KWC client on the account is viewing that exact conversation; checking only the current tab modal is insufficient.
// 알림 설정 체크박스는 브라우저 알림과 Push의 공통 카테고리 정책이고, 실제 전송 가능 여부는 브라우저 지원/권한/subscription 상태에서 추가로 결정된다.
// Notification preference checkboxes are shared category policy for browser notifications and Push; actual delivery still depends on browser support, permission, and subscription state.


  const NOTIFICATION_INBOX_OWNER_KEY = "kwc.notificationInboxOwner";

  function currentNotificationInboxOwner() {
    return authenticatedSession() && state.userUuid ? String(state.userUuid).trim().toLowerCase() : "";
  }

  function ensureNotificationInboxOwner() {
    const current = currentNotificationInboxOwner();
    if (!current) return false;
    try {
      const stored = String(localStorage.getItem(NOTIFICATION_INBOX_OWNER_KEY) || "").trim().toLowerCase();
      if (stored && stored !== current) {
        localStorage.removeItem(NOTIFICATION_INBOX_KEY);
        localStorage.removeItem(NOTIFICATION_INBOX_READ_AT_KEY);
      }
      if (stored !== current) localStorage.setItem(NOTIFICATION_INBOX_OWNER_KEY, current);
    } catch (_) {}
    return true;
  }

  function clearAccountNotificationUiState() {
    state.notificationInboxUnread = 0;
    try { document.querySelectorAll(".kwc-notification-inbox-backdrop").forEach(el => el.remove()); } catch (_) {}
    const button = document.getElementById("kwc-notifications");
    if (button) {
      button.classList.add("kwc-hidden");
      button.hidden = true;
      button.disabled = true;
      button.setAttribute("aria-hidden", "true");
    }
    const badge = document.getElementById("kwc-notification-badge");
    if (badge) { badge.textContent = "0"; badge.classList.add("kwc-hidden"); }
  }

  function readNotificationInbox() {
    if (!ensureNotificationInboxOwner()) return [];
    try {
      const parsed = JSON.parse(localStorage.getItem(NOTIFICATION_INBOX_KEY) || "[]");
      return Array.isArray(parsed) ? parsed.filter(Boolean).slice(0, 100) : [];
    } catch (_) { return []; }
  }

  function writeNotificationInbox(items) {
    if (!ensureNotificationInboxOwner()) return;
    try { localStorage.setItem(NOTIFICATION_INBOX_KEY, JSON.stringify((items || []).slice(0, 100))); } catch (_) {}
  }

  function notificationInboxReadAt() {
    const n = Number(localStorage.getItem(NOTIFICATION_INBOX_READ_AT_KEY) || "0");
    return Number.isFinite(n) ? n : 0;
  }

  function addNotificationInboxItem(item) {
    if (!authenticatedSession() || !state.userUuid) return;
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
    const accountVisible = !!(authenticatedSession() && state.userUuid && !guestChatHidden());
    const hidden = !accountVisible || !!state.minimized;
    if (button) {
      button.classList.toggle("kwc-hidden", hidden);
      button.hidden = hidden;
      button.disabled = hidden;
      button.setAttribute("aria-hidden", hidden ? "true" : "false");
    }
    const badge = document.getElementById("kwc-notification-badge");
    if (!badge) return;
    if (!accountVisible) {
      state.notificationInboxUnread = 0;
      badge.textContent = "0";
      badge.classList.add("kwc-hidden");
      return;
    }
    const readAt = notificationInboxReadAt();
    const unread = readNotificationInbox().filter(item => Number(item.time || 0) > readAt).length;
    state.notificationInboxUnread = unread;
    badge.textContent = unread > 99 ? "99+" : String(unread);
    badge.classList.toggle("kwc-hidden", unread <= 0);
  }

  function openNotificationInboxModal() {
    if (!authenticatedSession() || !state.userUuid || guestChatHidden()) return;
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
        <span class="kwc-notification-row-title">${renderCustomEmojiTokens(item.title || configuredNotificationTitle(), false, true)}</span>
        ${item.body ? `<span class="kwc-notification-row-body">${renderCustomEmojiTokens(item.body, false, true)}</span>` : ""}
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
    installCustomEmojiImageRecovery(wrap);
    wrap.querySelectorAll(".kwc-notification-row-title, .kwc-notification-row-body").forEach(updateCustomEmojiOnlyClass);
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
    if (state.dmActiveThreadId && String(state.dmActiveThreadId) !== threadId && !state.dmAuditMode) saveConversationView("dm", state.dmActiveThreadId);
    state.dmDraftTarget = null;
    state.dmActiveThreadId = threadId;
    setActiveChatView("dm", threadId);
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
  const WEB_PUSH_VIEW_HEARTBEAT_MS = 4000;
  const webPushViewClientId = "pv_" + Math.random().toString(36).slice(2, 12) + Date.now().toString(36);
  let webPushViewTimer = null;
  let webPushViewLastSignature = "";
  let webPushViewLastSentAt = 0;

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
    // Builds before the dedicated Reactions preference used the Reply option for reaction attention.
    // If this new key is absent, inherit the browser's previous Reply choice once.
    {name: "reactions", key: "kwc.notify.reactions", label: "notifyReactions", fallback: () => readStoredBool("kwc.notify.replies", notificationServerDefault("reactions"))},
    {name: "system", key: "kwc.notify.system", label: "notifySystem", fallback: () => notificationServerDefault("system")},
    {name: "keywords", key: "kwc.notify.keywords", label: "notifyKeywords", fallback: () => notificationServerDefault("keywords")}
  ];

  function browserNotificationServerAllows(name) {
    if (name === "normalChat") return state.browserNotificationsNotifyNormalChat !== false;
    if (name === "dm") return state.browserNotificationsNotifyDm !== false;
    if (name === "groupChat") return state.browserNotificationsNotifyGroupChat !== false;
    if (name === "mentions") return state.browserNotificationsNotifyMentions !== false;
    if (name === "replies") return state.browserNotificationsNotifyReplies !== false;
    if (name === "reactions") return state.browserNotificationsNotifyReactions !== false;
    if (name === "system") return state.browserNotificationsNotifySystem !== false;
    if (name === "keywords") return state.browserNotificationsNotifyKeywords !== false;
    return true;
  }

  function webPushServerAllows(name) {
    if (name === "normalChat") return state.webPushNotifyNormalChat !== false;
    if (name === "dm") return state.webPushNotifyDm !== false;
    if (name === "groupChat") return state.webPushNotifyGroupChat !== false;
    if (name === "mentions") return state.webPushNotifyMentions !== false;
    if (name === "replies") return state.webPushNotifyReplies !== false;
    if (name === "reactions") return state.webPushNotifyReactions !== false;
    if (name === "system") return state.webPushNotifySystem !== false;
    if (name === "keywords") return state.webPushNotifyKeywords !== false;
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

  function normalizeMentionNotificationLabel(value) {
    const text = String(value == null ? "" : value).trim();
    if (!text) return "@Mention";
    return text.startsWith("@") ? text : `@${text}`;
  }

  function notificationOptionsHtml(prefix, labels = {}) {
    const row = (name, fallback) => {
      const def = notificationOptionDef(name);
      const allowed = notificationServerAllows(name);
      const checked = notificationOption(name) ? " checked" : "";
      const disabled = allowed ? "" : " disabled";
      const title = allowed ? "" : ` title="${esc(labels.notifyDisabledByServer || "Disabled by server configuration.")}"`;
      const rawText = labels[def && def.label] || fallback;
      const text = name === "mentions" ? normalizeMentionNotificationLabel(rawText) : rawText;
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
    const typingRow = typingDisplayPreferenceAvailable()
      ? `<label class="kwc-notify-option"><input id="kwc-prefs-typing-display" type="checkbox" ${state.typingDisplayEnabled !== false ? "checked" : ""}> <span>${esc(labels.showTypingIndicator || "Show typing indicators")}</span></label>`
      : "";
    return `<div class="kwc-notify-options">
      ${row("normalChat", "Normal chat")}
      ${row("dm", "DM")}
      ${row("groupChat", "Group chat")}
      ${typingRow}
      ${row("mentions", "@Mention")}
      ${row("replies", "Replies")}
      ${row("reactions", "Reactions")}
      ${systemSelect}
      ${row("keywords", "Keyword alerts")}
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

  function notificationMobileVisibilitySemantics() {
    // Mobile browsers/PWAs commonly report document.hasFocus() as false while
    // the page is visibly in the foreground. visibilityState is the reliable
    // foreground signal there; desktop keeps the stricter focus requirement.
    try { return notificationUsesMobilePushUi(); } catch (_) { return false; }
  }

  function notificationDocumentForeground(doc) {
    const target = doc || document;
    if (!target || target.hidden || target.visibilityState === "hidden") return false;
    if (notificationMobileVisibilitySemantics()) return true;
    try {
      if (typeof target.hasFocus === "function" && !target.hasFocus()) return false;
    } catch (_) {}
    return true;
  }

  function notificationHostActivelyViewed() {
    if (state.minimized) return false;
    if (state.hostPageVisible === false) return false;
    const mobileVisibility = notificationMobileVisibilitySemantics();
    if (!mobileVisibility && state.hostPageFocused === false) return false;
    // The iframe document itself does not need focus when the parent map page is
    // focused. Requiring iframe document.hasFocus() caused false notifications on
    // desktop after clicking the map outside the chat frame.
    if (document.hidden || document.visibilityState === "hidden") return false;
    try {
      const parentWindow = window.parent && window.parent !== window ? window.parent : window;
      const parentDocument = parentWindow.document || document;
      if (parentWindow === window) {
        if (!notificationDocumentForeground(parentDocument)) return false;
      } else {
        if (parentDocument.hidden || parentDocument.visibilityState === "hidden") return false;
        if (!mobileVisibility && typeof parentDocument.hasFocus === "function" && !parentDocument.hasFocus()) return false;
      }
    } catch (_) {
      // Cross-origin parent access may fail; hostAttention is the authoritative
      // fallback in that case and was already checked above.
    }
    return true;
  }

  function notificationViewStatePayload() {
    return {
      active: notificationHostActivelyViewed(),
      dmThreadId: state.dmModalOpen && !state.dmAuditMode ? String(state.dmActiveThreadId || "") : "",
      groupRoomId: state.groupModalOpen && !state.groupAuditMode ? String(state.groupActiveRoomId || "") : ""
    };
  }

  function activePrivateNotificationView() {
    const view = notificationViewStatePayload();
    if (!view.active) return {active:false, dmThreadId:"", groupRoomId:""};
    const dmThreadId = String(view.dmThreadId || "").trim();
    const groupRoomId = String(view.groupRoomId || "").trim();
    if (!dmThreadId && !groupRoomId) return {active:false, dmThreadId:"", groupRoomId:""};
    return {active:true, dmThreadId, groupRoomId};
  }

  // 현재 탭의 private-view attention 상태를 서버 heartbeat로 보낸다. 이 정보는 같은 계정의 다른 기기 Push까지 억제할 수 있으므로 짧은 TTL의 일시 상태로만 취급한다.

  // Publishes this tab’s private-view attention as a server heartbeat. Because it can suppress Push on other devices of the same account, it is treated only as short-lived TTL state.

  function publishWebPushViewState(force = false, overrideActive = null) {
    // Active private-conversation viewing is account attention state. Report it
    // even when this browser itself has no Push subscription, so a foreground
    // desktop session can suppress duplicate Push on the same account's phone.
    if (!state.token) return Promise.resolve(false);
    const view = activePrivateNotificationView();
    if (overrideActive === false) {
      view.active = false;
      view.dmThreadId = "";
      view.groupRoomId = "";
    }
    const signature = [view.active ? "1" : "0", view.dmThreadId || "", view.groupRoomId || ""].join("|");
    const now = Date.now();
    if (!force && signature === webPushViewLastSignature && now - webPushViewLastSentAt < WEB_PUSH_VIEW_HEARTBEAT_MS - 250) {
      return Promise.resolve(true);
    }
    webPushViewLastSignature = signature;
    webPushViewLastSentAt = now;
    return api("/push/view-state", {
      method: "POST",
      body: JSON.stringify({
        deviceId: webPushDeviceId(),
        clientId: webPushViewClientId,
        active: view.active === true,
        dmThreadId: view.dmThreadId || "",
        groupRoomId: view.groupRoomId || ""
      }),
      timeoutMs: 5000,
      keepalive: true,
      returnHttpErrorResponse: true
    }).then(res => !!(res && res.ok !== false)).catch(() => false);
  }

  function startWebPushViewHeartbeat() {
    if (webPushViewTimer) return;
    webPushViewTimer = setInterval(() => { publishWebPushViewState(false).catch(() => {}); }, WEB_PUSH_VIEW_HEARTBEAT_MS);
    const refresh = () => {
      publishNotificationViewState();
      publishWebPushViewState(true).catch(() => {});
    };
    document.addEventListener("visibilitychange", refresh, true);
    window.addEventListener("focus", refresh, true);
    window.addEventListener("blur", refresh, true);
    window.addEventListener("pagehide", () => { publishWebPushViewState(true, false).catch(() => {}); }, true);
    window.addEventListener("beforeunload", () => { publishWebPushViewState(true, false).catch(() => {}); }, true);
  }

  function publishNotificationViewState() {
    const view = notificationViewStatePayload();
    if (window.parent && window.parent !== window && !state.isPip) {
      postFrame("notificationViewState", view);
    }
    publishWebPushViewState(true).catch(() => {});
  }

  // 알림 대상 DM/그룹을 현재 사용자가 실제로 보고 있는지 판단한다. modal 선택만이 아니라 page visibility, focus, minimize 상태까지 모두 만족해야 한다.

  // Determines whether the notification target DM/group is genuinely being viewed. Matching the selected modal alone is insufficient; page visibility, focus, and non-minimized state must also match.

  function notificationTargetCurrentlyVisible(options = {}) {
    if (!notificationHostActivelyViewed()) return false;
    if (options.publicChat === true) return true;
    const dmThreadId = String(options.dmThreadId || "").trim();
    if (dmThreadId) {
      const modal = document.querySelector(".kwc-dm-modal-backdrop:not(.kwc-group-modal-backdrop) .kwc-dm-modal");
      if (modal && state.dmModalOpen && !state.dmAuditMode && String(state.dmActiveThreadId || "") === dmThreadId) return true;
    }
    const groupRoomId = String(options.groupRoomId || "").trim();
    if (groupRoomId) {
      const modal = document.querySelector(".kwc-group-modal-backdrop .kwc-group-modal");
      if (modal && state.groupModalOpen && !state.groupAuditMode && String(state.groupActiveRoomId || "") === groupRoomId) return true;
    }
    return false;
  }

  function applyAccountNotificationViewState(data) {
    const now = Date.now();
    const serverTime = Number(data && data.serverTime || 0);
    const hasServerClock = Number.isFinite(serverTime) && serverTime > 0;
    const dm = new Map();
    const group = new Map();
    const addEntries = (target, items) => {
      if (!Array.isArray(items)) return;
      for (const item of items) {
        if (!item || typeof item !== "object") continue;
        const id = String(item.id || "").trim();
        const serverExpiresAt = Number(item.expiresAt || 0);
        if (!id || !Number.isFinite(serverExpiresAt)) continue;
        const expiresAt = hasServerClock ? now + Math.max(0, serverExpiresAt - serverTime) : serverExpiresAt;
        if (!Number.isFinite(expiresAt) || expiresAt <= now) continue;
        const previous = Number(target.get(id) || 0);
        if (expiresAt > previous) target.set(id, expiresAt);
      }
    };
    addEntries(dm, data && data.dmThreads);
    addEntries(group, data && data.groupRooms);
    state.notificationAccountDmViews = dm;
    state.notificationAccountGroupViews = group;
  }

  function accountNotificationTargetActivelyViewed(options = {}) {
    if (notificationTargetCurrentlyVisible(options)) return true;
    // Public-chat visibility is intentionally local to this browser. The server
    // account-wide active-view cache remains private-conversation-only.
    if (options.publicChat === true) return false;
    const now = Date.now();
    const lookup = (map, id) => {
      if (!map || !id) return false;
      const expiresAt = Number(map.get(id) || 0);
      if (!Number.isFinite(expiresAt) || expiresAt <= now) {
        if (typeof map.delete === "function") map.delete(id);
        return false;
      }
      return true;
    };
    const dmThreadId = String(options.dmThreadId || "").trim();
    if (dmThreadId && lookup(state.notificationAccountDmViews, dmThreadId)) return true;
    const groupRoomId = String(options.groupRoomId || "").trim();
    if (groupRoomId && lookup(state.notificationAccountGroupViews, groupRoomId)) return true;
    return false;
  }

  function attentionNeededForNotification(force = false) {
    if (force) return true;
    if (!state.browserNotificationsOnlyWhenHidden) return true;
    // Use the host page foreground state rather than iframe focus. This keeps
    // desktop embedded maps and mobile/PWA visibility behavior consistent.
    return !notificationHostActivelyViewed();
  }

  function showBrowserNotification(title, body, options = {}) {
    if (!state.browserNotificationsEnabled || !notificationsEnabledLocal()) return false;
    if (options.force !== true && accountNotificationTargetActivelyViewed(options)) return false;
    const finalTitle = title || configuredNotificationTitle();
    const finalBody = String(body || "");
    const navUrl = options.url || notificationNavigationUrl(options);
    if (options.store !== false) addNotificationInboxItem({title: finalTitle, body: finalBody, type: options.type || "notification", tag: options.tag || "", messageId: options.messageId || "", dmThreadId: options.dmThreadId || "", dmMessageId: options.dmMessageId || "", groupRoomId: options.groupRoomId || "", groupMessageId: options.groupMessageId || "", url: navUrl || ""});
    // When this browser has an active Web Push subscription, the push service is
    // the single OS-notification path. Keep the in-chat inbox entry but suppress
    // the page Notification API copy so the same keyword/message cannot ring twice.
    if (state.webPushSubscriptionActive && options.force !== true) return false;
    if (!attentionNeededForNotification(options.force === true)) return false;
    // Message preview is built in for eligible notifications; there is no user/admin option.
    const visibleBody = String(body || "");
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
    const userUuid = String(state.userUuid || "").trim().toLowerCase();
    const senderUuid = String(msg.senderUuid || msg.actorUuid || "").trim().toLowerCase();
    if (userUuid && senderUuid && userUuid === senderUuid) return true;
    const username = String(state.username || "").toLowerCase();
    const senderUsername = String(msg.senderUsername || msg.realSender || msg.sender || "").toLowerCase();
    if (username && senderUsername && username === senderUsername) return true;
    return false;
  }

  function messageMentionsCurrentUser(msg) {
    if (!msg || typeof msg !== "object") return false;
    // 5.2.0 resolves @mentions on the server against real names and visible
    // display names. The per-viewer boolean also preserves longest-name wins
    // and same-display-name team mentions without exposing account UUID lists.
    if (Object.prototype.hasOwnProperty.call(msg, "mentioned")) return msg.mentioned === true;
    // Compatibility fallback for an older peer/page payload: require @ and the
    // complete real username. Never treat an unprefixed name as a mention.
    const name = plainMinecraftName(String(state.username || "")).trim();
    if (!name) return false;
    return String(msg.message || "").toLowerCase().includes("@" + name.toLowerCase());
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

  function maybeNotifyReaction(data) {
    if (!data || !state.token) return;
    if (isPersonallyBlockedUuid(data.actorUuid || "")) return;
    const actor = plainMinecraftName(String(data.actorLabel || "")).trim() || t("sender.unknown", "Unknown");
    const reaction = String(data.reaction || "");
    const messageId = String(data.messageId || "");
    if (!reaction || !messageId) return;
    const title = t("reaction.notificationTitle", "Reaction");
    const body = fmt("reaction.notificationBody", "{user} reacted with {reaction}.", {user: actor, reaction});
    const tag = "kwc-reaction-" + messageId + "-" + String(data.actorUuid || "") + "-" + reaction;
    // Match the other notification categories: one shared account preference
    // controls the browser/in-chat notification path, while the same preference
    // is synchronized to background Web Push subscriptions server-side.
    if (!browserNotificationOption("reactions")) return;
    showBrowserNotification(title, body, {type:"reaction", tag, messageId});
  }

  function maybeNotifyChatMessage(msg) {
    if (!msg) return;
    if (isPersonallyBlockedMessage(msg)) return;
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
    const mention = messageMentionsCurrentUser(msg);
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
    if (isPersonallyBlockedMessage(message)) return;
    const targetThreadId = String(threadId || message.threadId || "").trim();
    if (accountNotificationTargetActivelyViewed({dmThreadId: targetThreadId})) return;
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
    if (isPersonallyBlockedUuid(thread.otherUuid || thread.otherPlayerUuid || "")) return;
    if (accountNotificationTargetActivelyViewed({dmThreadId: String(thread.id || "")})) return;
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
    if (accountNotificationTargetActivelyViewed({groupRoomId: String(room.id || "")})) return;
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
    if (isPersonallyBlockedMessage(message)) return;
    const roomId = String(room && room.id || message.roomId || "").trim();
    if (accountNotificationTargetActivelyViewed({groupRoomId: roomId})) return;
    const messageId = String(message.id || "").trim();
    const notificationKey = roomId && messageId ? roomId + ":" + messageId : "";
    if (notificationKey) {
      if (state.groupNotificationSeen.has(notificationKey)) return;
      state.groupNotificationSeen.add(notificationKey);
      while (state.groupNotificationSeen.size > 512) {
        const oldest = state.groupNotificationSeen.values().next().value;
        if (oldest == null) break;
        state.groupNotificationSeen.delete(oldest);
      }
    }
    const own = currentUserMatchesMessage(message);
    if (own) return;
    const roomName = plainNotificationText(room && room.name || t("group.title", "Group chats"), 80);
    const sender = plainNotificationText(message.senderDisplayName || message.senderUsername || "", 60);
    const membershipEvent = message.eventType === "member_join" || message.eventType === "member_leave";
    const body = plainNotificationText(membershipEvent ? groupMembershipEventText(message) : (message.body || ""), 180);
    const keyword = notificationKeywordMatch(roomName + " " + sender + " " + body);
    if (keyword && browserNotificationOption("keywords")) {
      showBrowserNotification(keywordNotificationTitle(keyword), membershipEvent ? (roomName + (body ? " · " + body : "")) : (roomName + (sender ? " · " + sender : "") + (body ? " · " + body : "")), {tag: "kwc-keyword-" + keyword, type: "keyword", groupRoomId: roomId, groupMessageId: message.id || ""});
      return;
    }
    if (!browserNotificationOption("groupChat")) return;
    showBrowserNotification(membershipEvent ? roomName : (roomName + (sender ? " · " + sender : "")), body, {tag: "kwc-group-" + roomId, groupRoomId: roomId, groupMessageId: message.id || ""});
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
        notifyReactions: opts.reactions === true,
        notifySystem: opts.system === true,
        notifySystemMode: opts.systemMode || (opts.system === true ? "all" : "off"),
        notifyKeywords: opts.keywords === true,
        keywords: notificationKeywordsText(),
        language: selectedLocale(),
        openUrl: notificationOpenUrl()
      })});
      writeStorageValue(LEGACY_WEB_PUSH_ENABLED_KEY, null);
      state.webPushLastError = "";
      state.webPushSubscriptionActive = true;
      clearWebPushAutomaticFailure();
      publishWebPushViewState(true).catch(() => {});
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
      // Viewing-state heartbeat is independent from this device's Push
      // subscription. Turning Push off here must not make another device alert
      // while this account is still actively reading the exact private room.
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
      reactions: opts.reactions === true,
      systemMode: opts.systemMode || (opts.system === true ? "all" : "off"),
      keywords: opts.keywords === true,
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
        ["normalChat", "dm", "groupChat", "mentions", "replies", "reactions", "keywords"].forEach(name => {
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

  async function loadAccountPresencePreferences() {
    if (!state.token) {
      state.presenceInvisible = false;
      state.presenceStatus = "online";
      state.presencePreferenceLoaded = false;
      return false;
    }
    try {
      const res = await api("/preferences/presence", {timeoutMs: 8000});
      const prefs = res && res.preferences && typeof res.preferences === "object" ? res.preferences : res;
      const status = String(prefs && prefs.status || (prefs && prefs.invisible === true ? "offline" : "online")).toLowerCase();
      state.presenceStatus = status === "busy" ? "busy" : status === "offline" ? "offline" : "online";
      state.presenceInvisible = state.presenceStatus === "offline";
      state.presencePreferenceLoaded = true;
      return true;
    } catch (_) {
      state.presencePreferenceLoaded = false;
      return false;
    }
  }

  async function setAccountPresenceStatus(status) {
    if (!state.token) return false;
    status = String(status || "online").toLowerCase();
    if (status !== "busy" && status !== "offline") status = "online";
    const previousStatus = state.presenceStatus || "online";
    const previousInvisible = state.presenceInvisible === true;
    state.presenceStatus = status;
    state.presenceInvisible = status === "offline";
    try {
      const res = await api("/preferences/presence", {
        method: "POST",
        body: JSON.stringify({status}),
        timeoutMs: 8000,
        returnHttpErrorResponse: true
      });
      if (!res || res.ok === false) throw new Error(String(res && res.error || "presence_preferences_save_failed"));
      const prefs = res.preferences && typeof res.preferences === "object" ? res.preferences : res;
      const saved = String(prefs && prefs.status || (prefs && prefs.invisible === true ? "offline" : "online")).toLowerCase();
      state.presenceStatus = saved === "busy" ? "busy" : saved === "offline" ? "offline" : "online";
      state.presenceInvisible = state.presenceStatus === "offline";
      state.presencePreferenceLoaded = true;
      refreshPresenceSurfaces().catch(() => {});
      return true;
    } catch (_) {
      state.presenceStatus = previousStatus;
      state.presenceInvisible = previousInvisible;
      return false;
    }
  }

  async function loadAccountTypingPreferences() {
    if (!state.token) {
      state.typingDisplayEnabled = true;
      state.typingPreferenceLoaded = false;
      scheduleTypingIndicatorRefresh();
      return false;
    }
    try {
      const res = await api("/preferences/typing", {timeoutMs: 8000});
      if (res && Object.prototype.hasOwnProperty.call(res, "enabled")) state.typingUserDisplayControl = res.enabled === true;
      state.typingDisplayEnabled = !res || res.displayEnabled !== false;
      state.typingPreferenceLoaded = true;
      scheduleTypingIndicatorRefresh();
      return true;
    } catch (_) {
      state.typingPreferenceLoaded = false;
      return false;
    }
  }

  async function setAccountTypingDisplayEnabled(enabled) {
    if (!state.token || !state.typingUserDisplayControl) return false;
    const previous = state.typingDisplayEnabled !== false;
    state.typingDisplayEnabled = enabled !== false;
    scheduleTypingIndicatorRefresh();
    try {
      const res = await api("/preferences/typing", {
        method: "POST",
        body: JSON.stringify({displayEnabled: state.typingDisplayEnabled}),
        timeoutMs: 8000,
        returnHttpErrorResponse: true
      });
      if (!res || res.ok === false) throw new Error(String(res && res.error || "typing_preferences_save_failed"));
      const prefs = res.preferences && typeof res.preferences === "object" ? res.preferences : res;
      state.typingDisplayEnabled = !prefs || prefs.displayEnabled !== false;
      state.typingPreferenceLoaded = true;
      scheduleTypingIndicatorRefresh();
      return true;
    } catch (_) {
      state.typingDisplayEnabled = previous;
      scheduleTypingIndicatorRefresh();
      return false;
    }
  }

  function typingDisplayPreferenceAvailable() {
    // Prefer either freshly loaded source of truth. The public config and the
    // account preference endpoint normally agree, but using both prevents a
    // transient refresh/order mismatch from hiding the per-account control.
    return !!(state.token && (state.typingUserDisplayControl || (state.config && state.config.typingUserDisplayControl === true)));
  }

  function typingDisplayAllowedForCurrentUser() {
    if (!state.typingUserDisplayControl || !state.token) return true;
    return state.typingDisplayEnabled !== false;
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
        chatBehavior: t("preferences.chatBehavior", "Chat behavior"),
        showTypingIndicator: t("preferences.showTypingIndicator", "Show typing indicators"),
        showTypingIndicatorHelp: t("preferences.showTypingIndicatorHelp", "Show other users' typing activity on this screen. This setting is saved to your KWC account and does not stop your own typing activity from being sent."),
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
        notifyMentions: t("preferences.notifyMentions", "@Mention"),
        notifyReplies: t("preferences.notifyReplies", "Replies"),
        notifyReactions: t("preferences.notifyReactions", "Reactions"),
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
      typingDisplayPreferenceAvailable: typingDisplayPreferenceAvailable(),
      typingDisplayEnabled: state.typingDisplayEnabled !== false,
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

