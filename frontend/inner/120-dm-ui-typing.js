// [KWC 유지보수 주석 / KWC maintenance notes]
// DM/그룹 메시지 공통 action 버튼, private reply, typing indicator, private message DOM 렌더링을 담당한다.
// This fragment handles shared DM/group message actions, private replies, typing indicators, and private-message DOM rendering.
// delete/pin/retry 버튼은 data-* 속성으로 실제 message id를 전달하며, 렌더된 버튼의 존재를 권한 근거로 사용하지 않는다.
// Delete/pin/retry controls pass real message IDs through data-* attributes; the existence of a rendered button is never an authorization boundary.
// typing 상태는 저장되는 메시지가 아니라 짧은 TTL의 ephemeral presence이므로 재연결·room 변경 때 stale 표시를 제거해야 한다.
// Typing state is ephemeral TTL-based presence rather than stored chat data, so stale indicators must be cleared on reconnect and room/thread changes.

  // 렌더된 DM/그룹 메시지의 reply/delete/pin/retry 버튼에 이벤트를 한 번만 연결한다. dataset 설치 플래그로 중복 handler가 누적되는 것을 방지한다.

  // Attaches reply/delete/pin/retry handlers to rendered DM/group messages exactly once. Dataset installation flags prevent duplicate handlers from accumulating across rerenders.

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
      root.querySelectorAll("[data-group-delete-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); deleteGroupMessage(btn.dataset.groupDeleteMessage || ""); });
      });
      root.querySelectorAll("[data-group-pin-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); pinGroupMessage(btn.dataset.groupPinMessage || ""); });
      });
      root.querySelectorAll("[data-group-retry-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); retryGroupChatMessage(btn.dataset.groupRetryMessage || ""); });
      });
    } else {
      root.querySelectorAll("[data-dm-delete-message]").forEach(btn => {
        if (btn.dataset.kwcPrivateActionInstalled === "1") return;
        btn.dataset.kwcPrivateActionInstalled = "1";
        btn.addEventListener("click", event => { event.preventDefault(); event.stopPropagation(); deleteDirectMessage(btn.dataset.dmDeleteMessage || ""); });
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

  const CHAT_VIEW_STATE_VERSION = 2;
  const CHAT_VIEW_STATE_PREFIX = "kwc.chatViewState.v2";
  const CHAT_VIEW_STATE_MAX_ENTRIES = 256;
  const CHAT_VIEW_SAVE_DEBOUNCE_MS = 160;
  const chatViewMemoryFallback = new Map();
  const chatViewSaveTimers = new Map();

  // 읽던 위치는 메시지 본문/상대 이름을 저장하지 않는다. origin+path, relay id, user UUID로 namespace를 나누고
  // 각 conversation에는 첫 visible message id, pixel offset, bottom 여부, 마지막 접근시각만 저장한다.
  // Read-position persistence stores no message body or peer name. The namespace is split by origin+path,
  // relay id, and user UUID; each conversation stores only the first visible message id, pixel offset,
  // bottom state, and last-access time.
  function chatViewNamespace() {
    let server = "";
    try {
      const path = String(window.location && window.location.pathname || "/").replace(/\/{2,}/g, "/") || "/";
      server = `${String(window.location && window.location.origin || "")}${path}`;
    } catch (_) { server = "kwc"; }
    const relayServerId = String(state.config && state.config.serverRelayServerId || "local").trim() || "local";
    const userId = String(state.userUuid || "guest").trim().toLowerCase() || "guest";
    return {server, relayServerId, userId};
  }

  function chatViewStorageKey() {
    const ns = chatViewNamespace();
    return `${CHAT_VIEW_STATE_PREFIX}:${encodeURIComponent(ns.server)}:${encodeURIComponent(ns.relayServerId)}:${encodeURIComponent(ns.userId)}`;
  }

  function newChatViewStore() {
    const ns = chatViewNamespace();
    return {
      version: CHAT_VIEW_STATE_VERSION,
      server: ns.server,
      relayServerId: ns.relayServerId,
      userId: ns.userId,
      updatedAt: Date.now(),
      activeView: {type: "public", conversationId: ""},
      views: {}
    };
  }

  function readChatViewStore() {
    const key = chatViewStorageKey();
    let raw = "";
    try { raw = localStorage.getItem(key) || ""; } catch (_) {}
    if (!raw && chatViewMemoryFallback.has(key)) raw = chatViewMemoryFallback.get(key) || "";
    if (!raw) return newChatViewStore();
    try {
      const parsed = JSON.parse(raw);
      if (!parsed || Number(parsed.version || 0) !== CHAT_VIEW_STATE_VERSION || typeof parsed.views !== "object") return newChatViewStore();
      if (!parsed.activeView || typeof parsed.activeView !== "object") parsed.activeView = {type:"public", conversationId:""};
      return parsed;
    } catch (_) { return newChatViewStore(); }
  }

  function pruneChatViewStore(store) {
    const views = store && store.views && typeof store.views === "object" ? store.views : {};
    const entries = Object.entries(views);
    if (entries.length <= CHAT_VIEW_STATE_MAX_ENTRIES) return;
    entries.sort((a, b) => Number(b[1] && b[1].lastAccess || 0) - Number(a[1] && a[1].lastAccess || 0));
    store.views = Object.fromEntries(entries.slice(0, CHAT_VIEW_STATE_MAX_ENTRIES));
  }

  function writeChatViewStore(store) {
    if (!store || typeof store !== "object") return false;
    const key = chatViewStorageKey();
    const ns = chatViewNamespace();
    store.version = CHAT_VIEW_STATE_VERSION;
    store.server = ns.server;
    store.relayServerId = ns.relayServerId;
    store.userId = ns.userId;
    store.updatedAt = Date.now();
    pruneChatViewStore(store);
    let raw = "";
    try { raw = JSON.stringify(store); } catch (_) { return false; }
    chatViewMemoryFallback.set(key, raw);
    try {
      localStorage.setItem(key, raw);
      return true;
    } catch (_) {
      // localStorage가 차단되었거나 quota/security 오류가 나면 이 페이지 수명 동안 memory fallback을 사용한다.
      // If localStorage is blocked or raises quota/security errors, keep the state in memory for this page lifetime.
      return false;
    }
  }

  function chatViewConversationKey(type, conversationId = "") {
    type = String(type || "public");
    if (type === "public") return "public";
    const id = String(conversationId || "").trim();
    return id ? `${type}:${id}` : "";
  }

  function chatViewNearBottom(box) {
    if (!box) return true;
    return isAutoFollowBottom(box);
  }

  function captureChatViewAnchor(type) {
    const groupMode = type === "group";
    const dmMode = type === "dm";
    const box = document.getElementById(groupMode ? "kwc-group-messages" : dmMode ? "kwc-dm-messages" : "kwc-messages");
    if (!box) return {messageId:"", offset:0, atBottom:true};
    const viewport = box.getBoundingClientRect();
    const selector = groupMode
      ? ":scope > .kwc-msg[data-group-message-id]"
      : dmMode ? ":scope > .kwc-msg[data-dm-message-id]" : ":scope > .kwc-msg[data-id]";
    for (const el of Array.from(box.querySelectorAll(selector))) {
      const rect = el.getBoundingClientRect();
      if (rect.bottom < viewport.top + 1) continue;
      const messageId = String(groupMode ? el.dataset.groupMessageId || "" : dmMode ? el.dataset.dmMessageId || "" : el.dataset.id || "").trim();
      if (!messageId) continue;
      if ((groupMode || dmMode) && !/^\d+$/.test(messageId)) continue; // optimistic private IDs are not durable anchors.
      return {
        messageId,
        offset: Number.isFinite(rect.top - viewport.top) ? rect.top - viewport.top : 0,
        atBottom: chatViewNearBottom(box)
      };
    }
    return {messageId:"", offset:0, atBottom:chatViewNearBottom(box)};
  }

  function saveConversationView(type, conversationId = "") {
    if (state.chatViewRestoreInProgress) return false;
    // A minimized public viewport has no meaningful geometry. Persisting an anchor
    // from that hidden state causes cumulative upward drift after each restore.
    if (type === "public" && state.minimized) return false;
    if ((type === "dm" && state.dmAuditMode) || (type === "group" && state.groupAuditMode)) return false;
    const key = chatViewConversationKey(type, conversationId);
    if (!key) return false;
    const anchor = captureChatViewAnchor(type);
    const store = readChatViewStore();
    store.views[key] = {
      messageId: String(anchor.messageId || ""),
      offset: Number.isFinite(Number(anchor.offset)) ? Number(anchor.offset) : 0,
      atBottom: anchor.atBottom === true,
      lastAccess: Date.now()
    };
    writeChatViewStore(store);
    return true;
  }

  function setActiveChatView(type, conversationId = "") {
    type = type === "dm" || type === "group" ? type : "public";
    const store = readChatViewStore();
    store.activeView = {type, conversationId: type === "public" ? "" : String(conversationId || "")};
    writeChatViewStore(store);
  }

  function saveCurrentChatViewPosition() {
    if (state.groupModalOpen && !state.groupAuditMode) {
      if (state.groupActiveRoomId) saveConversationView("group", state.groupActiveRoomId);
      setActiveChatView("group", state.groupActiveRoomId || "");
      return;
    }
    if (state.dmModalOpen && !state.dmAuditMode) {
      if (state.dmActiveThreadId) saveConversationView("dm", state.dmActiveThreadId);
      setActiveChatView("dm", state.dmActiveThreadId || "");
      return;
    }
    saveConversationView("public", "");
    setActiveChatView("public", "");
  }

  function scheduleChatViewSave(type, conversationIdProvider) {
    if (state.chatViewRestoreInProgress) return;
    const timerKey = String(type || "public");
    clearTimeout(chatViewSaveTimers.get(timerKey));
    chatViewSaveTimers.set(timerKey, setTimeout(() => {
      chatViewSaveTimers.delete(timerKey);
      if (type === "dm" && !state.dmModalOpen) return;
      if (type === "group" && !state.groupModalOpen) return;
      const conversationId = typeof conversationIdProvider === "function" ? conversationIdProvider() : conversationIdProvider;
      if (type === "public" || conversationId) saveConversationView(type, conversationId || "");
    }, CHAT_VIEW_SAVE_DEBOUNCE_MS));
  }

  function installChatViewScrollPersistence(type, box) {
    if (!box) return;
    const flag = `kwcChatViewInstalled${type}`;
    if (box.dataset && box.dataset[flag] === "1") return;
    if (box.dataset) box.dataset[flag] = "1";
    box.addEventListener("scroll", () => {
      const provider = type === "group" ? () => state.groupActiveRoomId : type === "dm" ? () => state.dmActiveThreadId : () => "";
      scheduleChatViewSave(type, provider);
    }, {passive:true});
  }

  function readConversationView(type, conversationId = "") {
    const key = chatViewConversationKey(type, conversationId);
    if (!key) return null;
    const store = readChatViewStore();
    const saved = store.views && store.views[key];
    if (!saved || typeof saved !== "object") return null;
    return {
      messageId: String(saved.messageId || ""),
      offset: Number.isFinite(Number(saved.offset)) ? Number(saved.offset) : 0,
      atBottom: saved.atBottom === true,
      lastAccess: Number(saved.lastAccess || 0)
    };
  }

  function restoreChatViewAnchorNow(type, saved) {
    const groupMode = type === "group";
    const dmMode = type === "dm";
    const box = document.getElementById(groupMode ? "kwc-group-messages" : dmMode ? "kwc-dm-messages" : "kwc-messages");
    if (!box || !saved) return false;
    if (saved.atBottom === true) {
      if (type === "public" && typeof stickToBottomStable === "function") stickToBottomStable(box);
      else box.scrollTop = box.scrollHeight;
      return true;
    }
    const messageId = String(saved.messageId || "").trim();
    if (!messageId) return false;
    const attr = groupMode ? "data-group-message-id" : dmMode ? "data-dm-message-id" : "data-id";
    let el = box.querySelector(`[${attr}="${cssEscape(messageId)}"]`);
    if (!el && type === "public") {
      const idx = (state.messages || []).findIndex(msg => String(msg && msg.id || "") === messageId);
      if (idx >= 0) {
        renderVirtualMessages({stickToBottom:false, preserveScroll:false, preserveVisualAnchor:false, forcePreservePosition:true, suppressBottomStick:true, ignoreVisibleRangeProtection:true, focusIndex:idx, deferDuringScroll:false});
        el = box.querySelector(`[${attr}="${cssEscape(messageId)}"]`);
      }
    }
    if (!el) return false;
    const viewport = box.getBoundingClientRect();
    const rect = el.getBoundingClientRect();
    const desiredTop = viewport.top + Number(saved.offset || 0);
    const nextTop = Math.max(0, Number(box.scrollTop || 0) + (rect.top - desiredTop));
    if (type === "public" && typeof setScrollTopPreserved === "function") {
      setScrollTopPreserved(box, nextTop, {allowAwayFromBottom:true, reason:"chat-view-restore", suppressRenderMs:450, suppressUpdateMs:450});
    } else {
      box.scrollTop = nextTop;
    }
    return true;
  }

  async function ensurePublicChatViewMessage(saved) {
    const messageId = String(saved && saved.messageId || "").trim();
    if (!messageId || (state.messages || []).some(msg => String(msg && msg.id || "") === messageId)) return true;
    try {
      const data = await api(`/history/around?id=${encodeURIComponent(messageId)}&before=60&after=60`, {timeoutMs:15000});
      if (!data || !data.ok || !Array.isArray(data.messages) || !data.messages.length) return false;
      state.messages = [];
      state.nextLocalMessageId = 1;
      data.messages.forEach(msg => addMessage(msg, {skipRender:true, suppressAutoFollow:true}));
      state.historyHasMore = data.hasBefore != null ? !!data.hasBefore : !!data.hasMore;
      state.historyHasAfter = !!data.hasAfter;
      state.historyOldestId = data.oldestId || (state.messages[0] && state.messages[0].id) || "";
      state.historyNewestId = data.newestId || (state.messages[state.messages.length - 1] && state.messages[state.messages.length - 1].id) || "";
      state.autoFollowLatest = false;
      state.explicitLatestFollowUntil = 0;
      state.forceLatestJumpUntil = 0;
      return true;
    } catch (_) { return false; }
  }

  async function restoreChatViewAnchor(type, conversationId = "") {
    const saved = readConversationView(type, conversationId);
    if (!saved) return false;
    state.chatViewRestoreInProgress = true;
    try {
      if (saved.atBottom !== true) {
        state.autoFollowLatest = false;
        state.explicitLatestFollowUntil = 0;
        state.explicitLatestFollowReason = "";
        state.forceLatestJumpUntil = 0;
        state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), Date.now() + 4000);
      }
      if (type === "public") {
        if (saved.atBottom !== true) await ensurePublicChatViewMessage(saved);
        if (!restoreChatViewAnchorNow("public", saved)) return false;
        await new Promise(resolve => requestAnimationFrame(() => resolve()));
        restoreChatViewAnchorNow("public", saved);
        return true;
      }
      const groupMode = type === "group";
      const box = document.getElementById(groupMode ? "kwc-group-messages" : "kwc-dm-messages");
      if (!box) return false;
      if (saved.atBottom === true) return restoreChatViewAnchorNow(type, saved);
      for (let page = 0; page < 24; page++) {
        if (restoreChatViewAnchorNow(type, saved)) return true;
        const hasMore = groupMode ? state.groupMessagesHasMore : state.dmMessagesHasMore;
        if (!hasMore) break;
        const loaded = groupMode
          ? await loadOlderGroupChatMessagesFromEdge(box, "view-restore")
          : await loadOlderDirectMessageMessagesFromEdge(box, "view-restore");
        if (!loaded) break;
      }
      return restoreChatViewAnchorNow(type, saved);
    } finally {
      setTimeout(() => { state.chatViewRestoreInProgress = false; }, 220);
    }
  }

  async function restoreLastChatViewState() {
    const store = readChatViewStore();
    const active = store && store.activeView || {type:"public", conversationId:""};
    const type = active.type === "dm" || active.type === "group" ? active.type : "public";
    const conversationId = String(active.conversationId || "");
    if (type === "public" || !state.token) {
      setActiveChatView("public", "");
      return restoreChatViewAnchor("public", "");
    }
    if (type === "dm") {
      if (!state.directMessageEnabled) return false;
      await openDirectMessageModal();
      if (!state.dmModalOpen || !conversationId) return true;
      await loadDirectMessageThreads(true);
      const thread = (state.dmThreads || []).find(item => String(item && item.id || "") === conversationId);
      if (!thread) return true;
      state.dmDraftTarget = null;
      state.dmActiveThreadId = conversationId;
      setActiveChatView("dm", conversationId);
      renderDirectMessageThreads();
      updateDirectMessageViewMode();
      await loadDirectMessageMessages(conversationId);
      await restoreChatViewAnchor("dm", conversationId);
      return true;
    }
    if (!state.groupChatEnabled) return false;
    await openGroupChatModal();
    if (!state.groupModalOpen || !conversationId) return true;
    await loadGroupChatRooms(true);
    const room = (state.groupRooms || []).find(item => String(item && item.id || "") === conversationId);
    if (!room) return true;
    await openGroupRoom(conversationId);
    return true;
  }

  function installChatViewPersistence() {
    if (state.chatViewPersistenceInstalled) return;
    state.chatViewPersistenceInstalled = true;
    installChatViewScrollPersistence("public", document.getElementById("kwc-messages"));
    const save = () => saveCurrentChatViewPosition();
    window.addEventListener("pagehide", save, true);
    window.addEventListener("beforeunload", save, true);
  }

  function reconcilePrivateMessageList(box, messages, type, conversationKey, auditNoticeHtml, emptyHtml) {
    const arr = (Array.isArray(messages) ? messages : []).filter(msg => !isPersonallyBlockedMessage(msg));
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
    arr.forEach(msg => {
      const el = box.querySelector(`[data-kwc-private-message-key="${cssEscape(privateMessageDomKey(msg, type))}"]`);
      if (el) installReactionHandlers(el, msg);
    });
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
      ? `<div class="kwc-admin-audit-notice">${kwcFaIcon("shield-halved")} ${esc(t("admin.dmAuditReadOnly", "This administrator audit view is read-only. Every access is recorded in the audit log."))}</div>`
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
      clearTypingIndicatorsFromMessages("dm", messages);
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

  // 자기 소유 DM만 삭제한다. 원격 서버 메시지는 relay delete acknowledgement가 성공한 뒤 UI를 갱신하며 실패 시 로컬만 사라지는 상태를 만들지 않는다.

  // Deletes only a DM owned by the current sender. For remote-server messages, UI state changes only after relay delete acknowledgement, preventing local-only disappearance on failure.

  async function deleteDirectMessage(messageId) {
    messageId = String(messageId || "").trim();
    if (!messageId || !state.token) return;
    if (state.directMessageConfirmDelete && !confirmPlain(t("dm.confirmDeleteOwnMessage", "Delete this message for both participants?"))) return;
    try {
      const res = await api("/dm/delete-message", {method: "POST", body: JSON.stringify({messageId})});
      state.dmUnread = Number(res.unread || 0);
      updateDirectMessageButton();
      state.dmMessages = (state.dmMessages || []).filter(msg => String(msg && msg.id || "") !== messageId);
      if (state.dmReplyTarget && String(state.dmReplyTarget.id || "") === messageId) clearPrivateReply("dm");
      renderDirectMessageMessages(state.dmMessages, {stickToBottom: false});
      if (state.dmActiveThreadId && !state.dmMessagesLoading) await loadDirectMessageMessages(state.dmActiveThreadId);
      await loadDirectMessageThreads(true);
      renderDirectMessageThreads();
    } catch (e) {
      alertResponse("alert.dmDeleteFailed", "Failed to delete message: {error}", e.response || {error: e.message || "error"});
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
    if (state.dmEmojiPanelOpen) markNonScrollUiAction();
    state.dmEmojiPanelOpen = false;
    resetEmojiSearchState("dm");
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
    markNonScrollUiAction();
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
    if (state.dmEmojiSearchOpen) requestAnimationFrame(() => positionEmojiSearchOverlay("dm"));
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


  function raiseIndependentChatWindow(wrap) {
    if (!wrap) return;
    state.chatWindowZ = Math.max(1000, Number(state.chatWindowZ) || 1000) + 1;
    wrap.style.zIndex = String(state.chatWindowZ);
    // Standalone public-chat resize zones live under <body>, not inside the root
    // stacking context. Keep their hit-test layer exactly with the public root so
    // any DM/group window raised above it blocks click-through resizing.
    if (wrap.id === "kwc-root" && typeof wrap.__kwcStandaloneResizeUpdate === "function") {
      wrap.__kwcStandaloneResizeUpdate();
    }
  }

  function independentWindowPoint(event) {
    const src = event.touches && event.touches.length ? event.touches[0] :
                event.changedTouches && event.changedTouches.length ? event.changedTouches[0] : event;
    return {x:Number(src && src.clientX) || 0, y:Number(src && src.clientY) || 0};
  }

  function installTransparentWindowResize(wrap, modal, storageKey) {
    if (!wrap || !modal || wrap.dataset.kwcTransparentResizeInstalled === "1") return;
    wrap.dataset.kwcTransparentResizeInstalled = "1";
    const directions = ["nw","n","ne","e","se","s","sw","w"];
    const edgeOutset = 16;
    const edgeOverlap = 3;
    const edgeInset = 3;
    const cornerOverlap = 5;
    const handles = directions.map(direction => {
      const handle = document.createElement("div");
      handle.className = "kwc-window-resize-zone kwc-window-resize-zone-" + direction;
      handle.dataset.resizeDirection = direction;
      handle.setAttribute("aria-hidden", "true");
      wrap.appendChild(handle);
      return handle;
    });
    let resize = null;
    let resizeFrame = 0;
    let pendingGeometry = null;

    const update = (geometry = null) => {
      // During an active resize the caller already knows the new rectangle.
      // Reusing it avoids a synchronous getBoundingClientRect() after every style
      // write, which previously forced layout on every pointermove.
      const rect = geometry || modal.getBoundingClientRect();
      const rectRight = Number.isFinite(Number(rect.right)) ? Number(rect.right) : Number(rect.left) + Number(rect.width);
      const rectBottom = Number.isFinite(Number(rect.bottom)) ? Number(rect.bottom) : Number(rect.top) + Number(rect.height);
      const maximized = modal.dataset.kwcMaximized === "1";
      handles.forEach(handle => {
        const d = handle.dataset.resizeDirection || "se";
        handle.style.display = maximized || !privateMultiWindowSupported() || !!modal.querySelector(":scope > .kwc-window-owned-overlay") ? "none" : "block";
        if (handle.style.display === "none") return;
        let left, top, width, height;
        if (d === "n" || d === "s") {
          left = rect.left + edgeInset;
          top = d === "n" ? rect.top - edgeOutset : rectBottom - edgeOverlap;
          width = Math.max(1, rect.width - (edgeInset * 2));
          height = edgeOutset + edgeOverlap;
        } else if (d === "e" || d === "w") {
          left = d === "w" ? rect.left - edgeOutset : rectRight - edgeOverlap;
          top = rect.top + edgeInset;
          width = edgeOutset + edgeOverlap;
          height = Math.max(1, rect.height - (edgeInset * 2));
        } else {
          const isLeft = d === "nw" || d === "sw";
          const isTop = d === "nw" || d === "ne";
          left = isLeft ? rect.left - edgeOutset : rectRight - cornerOverlap;
          top = isTop ? rect.top - edgeOutset : rectBottom - cornerOverlap;
          width = edgeOutset + cornerOverlap;
          height = edgeOutset + cornerOverlap;
        }
        // Keep a small inward overlap so the edge can still be grabbed when the
        // window touches the viewport boundary. Most of the target remains outside
        // the conversation, leaving the scrollbar usable.
        const clippedLeft = Math.max(0, left);
        const clippedTop = Math.max(0, top);
        const clippedRight = Math.min(window.innerWidth, left + width);
        const clippedBottom = Math.min(window.innerHeight, top + height);
        const clippedWidth = Math.max(0, clippedRight - clippedLeft);
        const clippedHeight = Math.max(0, clippedBottom - clippedTop);
        if (clippedWidth <= 0 || clippedHeight <= 0) {
          handle.style.display = "none";
          return;
        }
        handle.style.left = Math.round(clippedLeft) + "px";
        handle.style.top = Math.round(clippedTop) + "px";
        handle.style.width = Math.round(clippedWidth) + "px";
        handle.style.height = Math.round(clippedHeight) + "px";
      });
    };
    const applyGeometry = geometry => {
      if (!geometry) return;
      modal.style.setProperty("position", "absolute", "important");
      modal.style.setProperty("left", geometry.left + "px", "important");
      modal.style.setProperty("top", geometry.top + "px", "important");
      modal.style.setProperty("width", geometry.width + "px", "important");
      modal.style.setProperty("height", geometry.height + "px", "important");
      modal.style.setProperty("margin", "0", "important");
      wrap.classList.add("kwc-modal-dragging-ready");
      update(geometry);
    };
    const flushGeometry = () => {
      resizeFrame = 0;
      const geometry = pendingGeometry;
      pendingGeometry = null;
      if (geometry) applyGeometry(geometry);
    };
    const scheduleGeometry = geometry => {
      pendingGeometry = geometry;
      if (resizeFrame) return;
      resizeFrame = requestAnimationFrame(flushGeometry);
    };
    const begin = event => {
      if (modal.dataset.kwcMaximized === "1" || !privateMultiWindowSupported()) return;
      const handle = event.currentTarget;
      const d = String(handle && handle.dataset.resizeDirection || "se");
      const point = independentWindowPoint(event);
      const rect = modal.getBoundingClientRect();
      resize = {direction:d, x:point.x, y:point.y, left:rect.left, top:rect.top, width:rect.width, height:rect.height};
      raiseIndependentChatWindow(wrap);
      event.preventDefault(); event.stopPropagation();
    };
    const move = event => {
      if (!resize) return;
      const point = independentWindowPoint(event);
      const dx = point.x - resize.x, dy = point.y - resize.y;
      const d = resize.direction;
      const north = d.includes("n"), south = d.includes("s"), west = d.includes("w"), east = d.includes("e");
      let left = resize.left, top = resize.top, width = resize.width, height = resize.height;
      if (west) { left += dx; width -= dx; }
      if (east) width += dx;
      if (north) { top += dy; height -= dy; }
      if (south) height += dy;
      const minW = 320, minH = 300, pad = 0;
      if (width < minW) { if (west) left -= (minW - width); width = minW; }
      if (height < minH) { if (north) top -= (minH - height); height = minH; }
      left = Math.max(pad, Math.min(left, window.innerWidth - minW - pad));
      top = Math.max(pad, Math.min(top, window.innerHeight - minH - pad));
      width = Math.min(width, window.innerWidth - left - pad);
      height = Math.min(height, window.innerHeight - top - pad);
      scheduleGeometry({left, top, width, height, right:left + width, bottom:top + height});
      event.preventDefault(); event.stopPropagation();
    };
    const end = event => {
      if (!resize) return;
      if (resizeFrame) { cancelAnimationFrame(resizeFrame); resizeFrame = 0; }
      if (pendingGeometry) {
        const geometry = pendingGeometry;
        pendingGeometry = null;
        applyGeometry(geometry);
      }
      resize = null;
      const rect = modal.getBoundingClientRect();
      if (storageKey) {
        localStorage.setItem(storageKey + ".size", JSON.stringify({width:Math.round(rect.width), height:Math.round(rect.height)}));
        localStorage.setItem(storageKey + ".position", JSON.stringify({left:Math.round(rect.left), top:Math.round(rect.top)}));
      }
      update();
      if (event) { event.preventDefault(); event.stopPropagation(); }
    };
    handles.forEach(handle => {
      handle.addEventListener("pointerdown", begin);
      handle.addEventListener("touchstart", begin, {passive:false});
    });
    window.addEventListener("pointermove", move, true);
    window.addEventListener("pointerup", end, true);
    window.addEventListener("pointercancel", end, true);
    window.addEventListener("touchmove", move, {capture:true, passive:false});
    window.addEventListener("touchend", end, {capture:true, passive:false});
    window.addEventListener("touchcancel", end, {capture:true, passive:false});
    window.addEventListener("resize", update, {passive:true});
    const overlayObserver = new MutationObserver(() => update());
    overlayObserver.observe(modal, {childList:true});
    modal.__kwcResizeZoneUpdate = update;
    update();
    wrap.__kwcWindowResizeCleanup = () => {
      overlayObserver.disconnect();
      if (resizeFrame) cancelAnimationFrame(resizeFrame);
      resizeFrame = 0;
      pendingGeometry = null;
      handles.forEach(handle => handle.remove());
      window.removeEventListener("pointermove", move, true);
      window.removeEventListener("pointerup", end, true);
      window.removeEventListener("pointercancel", end, true);
      window.removeEventListener("touchmove", move, {capture:true, passive:false});
      window.removeEventListener("touchend", end, {capture:true, passive:false});
      window.removeEventListener("touchcancel", end, {capture:true, passive:false});
      window.removeEventListener("resize", update, {passive:true});
    };
  }

  function installIndependentWindowMaximize(wrap, modal, storageKey) {
    if (!modal || modal.dataset.kwcMaximizeInstalled === "1") return;
    modal.dataset.kwcMaximizeInstalled = "1";
    // Delegate from the modal rather than binding only the outer window title.
    // The live DM/group conversation can be moved between the parent list window
    // and child windows, so its inner title may not exist when chrome is installed.
    // Delegation keeps both the outer title bar and the current inner title usable.
    modal.addEventListener("dblclick", event => {
      const target = event.target;
      if (!target || !target.closest) return;
      if (target.closest("button, input, select, textarea, a, [role=button]")) return;
      const titleSurface = target.closest(".kwc-dm-head, .kwc-dm-title");
      if (!titleSurface || titleSurface.closest(".kwc-dm-modal") !== modal) return;
      event.preventDefault(); event.stopPropagation();
      const maximized = modal.dataset.kwcMaximized === "1";
      if (!maximized) {
        const rect = modal.getBoundingClientRect();
        modal.__kwcMaxRestore = {left:rect.left, top:rect.top, width:rect.width, height:rect.height};
        modal.dataset.kwcMaximized = "1";
        modal.style.setProperty("position", "absolute", "important");
        modal.style.setProperty("left", "0px", "important");
        modal.style.setProperty("top", "0px", "important");
        modal.style.setProperty("width", "100vw", "important");
        modal.style.setProperty("height", "100vh", "important");
        modal.style.setProperty("margin", "0", "important");
      } else {
        const restore = modal.__kwcMaxRestore || {};
        modal.dataset.kwcMaximized = "0";
        modal.style.setProperty("left", Math.max(0, Number.isFinite(Number(restore.left)) ? Number(restore.left) : 24) + "px", "important");
        modal.style.setProperty("top", Math.max(0, Number.isFinite(Number(restore.top)) ? Number(restore.top) : 24) + "px", "important");
        modal.style.setProperty("width", Math.max(320, Number(restore.width) || 720) + "px", "important");
        modal.style.setProperty("height", Math.max(300, Number(restore.height) || 620) + "px", "important");
        if (storageKey) {
          localStorage.setItem(storageKey + ".size", JSON.stringify({width:Math.round(Number(restore.width) || 720), height:Math.round(Number(restore.height) || 620)}));
          localStorage.setItem(storageKey + ".position", JSON.stringify({left:Math.round(Number(restore.left) || 24), top:Math.round(Number(restore.top) || 24)}));
        }
      }
      raiseIndependentChatWindow(wrap);
      if (modal.__kwcResizeZoneUpdate) modal.__kwcResizeZoneUpdate();
    });
  }

  function installIndependentChatWindow(wrap, options = {}) {
    if (!wrap || wrap.dataset.kwcIndependentChatWindow === "1" || !privateMultiWindowSupported()) return;
    wrap.dataset.kwcIndependentChatWindow = "1";
    const modal = wrap.querySelector(":scope > .kwc-dm-modal");
    if (!modal) return;
    const group = modal.classList.contains("kwc-group-modal");
    const key = String(options.storageKey || (group ? "kwc.groupWindow" : "kwc.dmWindow"));
    const savedSize = localStorage.getItem(key + ".size");
    if (savedSize) try {
      const size = JSON.parse(savedSize);
      const width = Math.max(320, Math.min(window.innerWidth - 16, Number(size.width) || (options.child ? 620 : 720)));
      const height = Math.max(300, Math.min(window.innerHeight - 16, Number(size.height) || (options.child ? 560 : 620)));
      modal.style.setProperty("width", width + "px", "important");
      modal.style.setProperty("height", height + "px", "important");
    } catch (_) {}

    makeModalDraggable(wrap, key + ".position");

    if (!localStorage.getItem(key + ".position")) {
      const rect = modal.getBoundingClientRect();
      const cascade = Math.min(8, (privateConversationRegistry(group ? "group" : "dm") || new Map()).size || 0) * 28;
      const left = group ? Math.max(12, window.innerWidth - rect.width - 24 - cascade) : 24 + cascade;
      const top = (group ? 48 : 24) + cascade;
      modal.style.setProperty("position", "absolute", "important");
      modal.style.setProperty("left", Math.max(8, left) + "px", "important");
      modal.style.setProperty("top", Math.min(top, Math.max(8, window.innerHeight - rect.height - 8)) + "px", "important");
      modal.style.setProperty("margin", "0", "important");
      wrap.classList.add("kwc-modal-dragging-ready");
    }

    const raise = () => raiseIndependentChatWindow(wrap);
    modal.addEventListener("pointerdown", raise, {capture:true});
    raise();
    installTransparentWindowResize(wrap, modal, key);
    installIndependentWindowMaximize(wrap, modal, key);
    wrap.__kwcWindowChromeCleanup = () => {
      if (wrap.__kwcWindowResizeCleanup) wrap.__kwcWindowResizeCleanup();
      if (wrap.__kwcDragCleanup) wrap.__kwcDragCleanup();
    };
  }

  function installDirectMessageWindowDrag(wrap) {
    installIndependentChatWindow(wrap);
  }

  function renderDirectMessageEmojiPanel() {
    const panel = document.getElementById("kwc-dm-emoji-panel");
    if (!panel) return;
    if (!state.dmEmojiPanelOpen || !canUseCustomEmoji()) {
      closeDirectMessageEmojiPanel();
      return;
    }
    renderCustomEmojiPanel(panel, "dm");
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
      return `<button type="button" class="kwc-dm-player" data-dm-player="${esc(uuid)}" ${directMessageTargetDataAttributes(target)} title="${esc(directMessagePlainLabel(label))}"><span>${directMessageLabelHtml(label)}</span>${presenceCompactHtml(player, uuid, false)}</button>`;
    }).join("");
    box.querySelectorAll("[data-dm-player]").forEach(btn => {
      btn.addEventListener("click", async () => {
        const target = {
          uuid: btn.dataset.dmTargetUuid || btn.dataset.dmPlayer || "",
          label: btn.dataset.dmTargetLabel || "",
          displayName: btn.dataset.dmTargetDisplayName || "",
          username: btn.dataset.dmTargetUsername || "",
          remote: btn.dataset.dmTargetRemote === "1",
          serverId: btn.dataset.dmTargetServerId || "",
          serverName: btn.dataset.dmTargetServerName || ""
        };
        if (privateMultiWindowSupported()) {
          closeDirectMessagePlayerSearch();
          await openPrivateConversationWindow("dm", "", {draftTarget:target});
          const input = document.getElementById("kwc-dm-input");
          if (input) { setActiveComposeInput(input); input.focus(); }
          return;
        }
        state.dmDraftTarget = target;
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

  function typingIndicatorEnabled(kind) {
    if (kind === "public") return state.typingOpenChatEnabled === true;
    if (kind === "dm") return state.typingDmEnabled === true;
    if (kind === "group") return state.typingGroupChatEnabled === true;
    return false;
  }

  // Server scope policy controls whether typing activity exists/sends.
  // The account preference below is display-only for the current viewer.
  function typingIndicatorVisible(kind) {
    return typingIndicatorEnabled(kind) && typingDisplayAllowedForCurrentUser();
  }

  function plainTypingName(value) {
    return String(value == null ? "" : value)
      .replace(/[§&]x(?:[§&][0-9a-f]){6}/gi, "")
      .replace(/&#[0-9a-f]{6}/gi, "")
      .replace(/[§&][0-9a-fk-or]/gi, "")
      .replace(/<[^>]*>/g, "")
      .replace(/§/g, "")
      .replace(/\s+/g, " ")
      .trim();
  }

  function typingIdentity(entry) {
    const display = plainTypingName(entry && (entry.fromDisplayName || entry.fromUsername) || "");
    const username = plainTypingName(entry && entry.fromUsername || "");
    const real = username && (!display || username.toLowerCase() !== display.toLowerCase()) ? username : "";
    return {display: display || username, real};
  }

  function typingIdentityHtml(entry) {
    const identity = typingIdentity(entry);
    if (!identity.display) return "";
    if (!identity.real) return `<span class="kwc-typing-identity">${esc(identity.display)}</span>`;
    const shown = preferredSenderText(identity.display, identity.real);
    const title = state.senderIdentityMode === "real" ? senderDisplayTitle(identity.display) : senderOriginalTitle(identity.real);
    return `<span class="kwc-typing-identity" data-kwc-identity-toggle="typing" data-display-sender="${esc(identity.display)}" data-real-sender="${esc(identity.real)}" data-source="typing" data-showing-real="${state.senderIdentityMode === "real" ? "1" : "0"}" title="${esc(title)}" aria-label="${esc(title)}">${esc(shown)}</span>`;
  }

  function typingTemplateHtml(key, fallback, values) {
    let out = esc(t(key, fallback));
    Object.entries(values || {}).forEach(([name, html]) => { out = out.split("{" + name + "}").join(String(html)); });
    return out;
  }

  function currentDirectTypingTarget() {
    if (state.dmAuditMode) return null;
    if (state.dmActiveThreadId) {
      const thread = (state.dmThreads || []).find(t => String(t.id || "") === String(state.dmActiveThreadId));
      if (!thread) return null;
      return {
        uuid: thread.otherPlayerUuid || thread.otherUuid || "",
        remote: thread.otherRemote === true,
        serverId: thread.otherServerId || ""
      };
    }
    if (state.dmDraftTarget && state.dmDraftTarget.uuid) {
      return {uuid: state.dmDraftTarget.uuid, remote: state.dmDraftTarget.remote === true, serverId: state.dmDraftTarget.serverId || ""};
    }
    return null;
  }

  function notifyPublicTyping() {
    if (!typingIndicatorEnabled("public") || state.publicTypingInFlight) return;
    const now = Date.now();
    if (now < Number(state.publicTypingNextAllowedAt || 0)) return;
    const input = document.getElementById("kwc-message");
    const message = String(input && input.value || "").trim();
    if (!message || message.startsWith("/")) return;
    const body = {clientId: state.publicTypingClientId};
    if (!state.token) {
      const guest = document.getElementById("kwc-guest-name");
      body.guestName = String(guest && guest.value || state.guestName || "").trim();
    }
    // Do not start the five-second client cooldown until the server actually
    // accepts the typing event. Advancing the cooldown before fetch() would make
    // an HTTP rejection look like a successful send and suppress immediate retry,
    // unnecessarily difficult while suppressing immediate retry.
    state.publicTypingInFlight = true;
    api("/typing", {
      method: "POST",
      body: JSON.stringify(body),
      timeoutMs: 5000,
      returnHttpErrorResponse: true
    }).then(res => {
      if (res && res.ok !== false) {
        state.publicTypingNextAllowedAt = Date.now() + 5000;
        return;
      }
      state.publicTypingNextAllowedAt = 0;
      if (res && res.error === "typing_disabled") loadConfig().catch(() => {});
    }).catch(() => {
      state.publicTypingNextAllowedAt = 0;
    }).finally(() => {
      state.publicTypingInFlight = false;
    });
  }

  function notifyDirectTyping() {
    if (!typingIndicatorEnabled("dm") || !state.token || !state.directMessageEnabled || state.dmAuditMode) return;
    const target = currentDirectTypingTarget();
    if (!target || !target.uuid) return;
    const now = Date.now();
    if (now < Number(state.dmTypingNextAllowedAt || 0)) return;
    state.dmTypingNextAllowedAt = now + 5000;
    const body = {targetUuid: target.uuid};
    if (target.remote && target.serverId) body.targetServerId = target.serverId;
    api("/dm/typing", {method: "POST", body: JSON.stringify(body), timeoutMs: 5000}).catch(() => {});
  }

  function notifyGroupTyping() {
    if (!typingIndicatorEnabled("group") || !state.token || !state.groupChatEnabled || state.groupAuditMode || !state.groupActiveRoomId) return;
    const now = Date.now();
    if (now < Number(state.groupTypingNextAllowedAt || 0)) return;
    state.groupTypingNextAllowedAt = now + 5000;
    api("/group/typing", {method: "POST", body: JSON.stringify({roomId: state.groupActiveRoomId}), timeoutMs: 5000}).catch(() => {});
  }

  function directTypingMatchesActive(entry) {
    if (!entry || !state.dmModalOpen || state.dmAuditMode) return false;
    const from = String(entry.fromUuid || "").trim().toLowerCase();
    if (!from) return false;
    if (state.dmActiveThreadId) {
      const thread = (state.dmThreads || []).find(t => String(t.id || "") === String(state.dmActiveThreadId));
      if (!thread) return false;
      if (thread.otherRemote === true) return String(thread.otherUuid || "").trim().toLowerCase() === from;
      return String(thread.otherPlayerUuid || thread.otherUuid || "").trim().toLowerCase() === from;
    }
    return false;
  }

  function typingIndicatorOverflows(element) {
    if (!element || element.classList.contains("kwc-hidden")) return false;
    return Number(element.clientWidth || 0) > 0 && Number(element.scrollWidth || 0) > Number(element.clientWidth || 0) + 1;
  }

  function activeTypingEntries(map, predicate = null) {
    const out = [];
    const now = Date.now();
    for (const [key, value] of Array.from(map.entries())) {
      if (!value || Number(value.expiresAt || 0) <= now) { map.delete(key); continue; }
      if (predicate && !predicate(value)) continue;
      out.push(value);
    }
    return out;
  }

  function renderTypingList(element, entries) {
    if (!element) return;
    const valid = (entries || []).filter(entry => typingIdentity(entry).display);
    element.classList.toggle("kwc-hidden", valid.length === 0);
    if (!valid.length) { element.textContent = ""; return; }
    if (valid.length === 1) {
      element.innerHTML = typingTemplateHtml("typing.user", "{user} is typing...", {user: typingIdentityHtml(valid[0])});
    } else if (valid.length === 2) {
      element.innerHTML = typingTemplateHtml("typing.two", "{user1}, {user2} are typing...", {user1: typingIdentityHtml(valid[0]), user2: typingIdentityHtml(valid[1])});
      if (typingIndicatorOverflows(element)) {
        element.innerHTML = typingTemplateHtml("typing.count", "{count} people are typing...", {count: "2"});
      }
    } else {
      element.innerHTML = typingTemplateHtml("typing.count", "{count} people are typing...", {count: String(valid.length)});
    }
  }

  function renderTypingIndicators() {
    const now = Date.now();
    const publicEl = document.getElementById("kwc-public-typing");
    if (publicEl) {
      const captchaRow = document.getElementById("kwc-captcha-row");
      const captchaBlocksTyping = !state.token && !!(captchaRow && captchaRow.classList.contains("kwc-show"));
      const ownUuid = String(state.userUuid || "").trim().toLowerCase();
      const entries = !captchaBlocksTyping && typingIndicatorVisible("public") ? activeTypingEntries(state.publicTypingEntries, value => {
        const from = String(value && value.fromUuid || "").trim().toLowerCase();
        if (ownUuid && from && ownUuid === from) return false;
        return String(value && value.clientId || "") !== String(state.publicTypingClientId || "");
      }) : [];
      renderTypingList(publicEl, entries);
    }

    const dm = document.getElementById("kwc-dm-typing");
    const entry = state.dmTypingEntry;
    if (dm) {
      const visible = typingIndicatorVisible("dm") && entry && Number(entry.expiresAt || 0) > now && directTypingMatchesActive(entry);
      renderTypingList(dm, visible ? [entry] : []);
    }

    const group = document.getElementById("kwc-group-typing");
    if (group) {
      const entries = typingIndicatorVisible("group") ? activeTypingEntries(state.groupTypingEntries, value => String(value.roomId || "") === String(state.groupActiveRoomId || "")) : [];
      renderTypingList(group, entries);
    }
  }

  function scheduleTypingIndicatorRefresh() {
    clearTimeout(state.publicTypingTimer);
    clearTimeout(state.dmTypingTimer);
    clearTimeout(state.groupTypingTimer);
    let next = 0;
    const now = Date.now();
    for (const value of state.publicTypingEntries.values()) {
      const expires = Number(value && value.expiresAt || 0);
      if (expires > now && (!next || expires < next)) next = expires;
    }
    if (state.dmTypingEntry && Number(state.dmTypingEntry.expiresAt || 0) > now) next = !next ? Number(state.dmTypingEntry.expiresAt) : Math.min(next, Number(state.dmTypingEntry.expiresAt));
    for (const value of state.groupTypingEntries.values()) {
      const expires = Number(value && value.expiresAt || 0);
      if (expires > now && (!next || expires < next)) next = expires;
    }
    renderTypingIndicators();
    if (!next) return;
    const timer = setTimeout(() => { renderTypingIndicators(); scheduleTypingIndicatorRefresh(); }, Math.max(50, next - now + 25));
    state.publicTypingTimer = timer;
    state.dmTypingTimer = timer;
    state.groupTypingTimer = timer;
  }

  function handleTypingEvent(data) {
    if (!data || Number(data.expiresAt || 0) <= Date.now()) return;
    if (data.kind === "public") {
      if (!typingIndicatorEnabled("public")) return;
      if (String(data.clientId || "") === String(state.publicTypingClientId || "")) return;
      const from = String(data.fromUuid || "").trim().toLowerCase();
      if (from && state.userUuid && from === String(state.userUuid).trim().toLowerCase()) return;
      const key = String(data.originServerId || "local") + "|" + (from || String(data.clientId || data.fromDisplayName || ""));
      if (!key.endsWith("|")) state.publicTypingEntries.set(key, data);
    } else if (data.kind === "dm") {
      if (!typingIndicatorEnabled("dm")) return;
      state.dmTypingEntry = data;
    } else if (data.kind === "group") {
      if (!typingIndicatorEnabled("group")) return;
      const key = String(data.roomId || "") + "|" + String(data.fromUuid || "");
      if (!data.roomId || !data.fromUuid) return;
      state.groupTypingEntries.set(key, data);
    } else return;
    scheduleTypingIndicatorRefresh();
  }

  function clearTypingIndicatorsFromMessages(kind, messages, roomId = "") {
    if (!Array.isArray(messages) || !messages.length) return;
    const now = Date.now();
    const recentSenderMessage = (entry) => {
      const from = String(entry && entry.fromUuid || "").trim().toLowerCase();
      const fromName = plainTypingName(entry && (entry.fromDisplayName || entry.fromUsername) || "").toLowerCase();
      const expiresAt = Number(entry && entry.expiresAt || 0);
      if (!(expiresAt > 0)) return false;
      const startedAt = expiresAt - 5500;
      return messages.some(msg => {
        const sender = String(msg && (msg.senderUuid || msg.playerUuid) || "").trim().toLowerCase();
        const senderName = plainTypingName(msg && (msg.sender || msg.realSender) || "").toLowerCase();
        if (from ? (!sender || sender !== from) : (!fromName || senderName !== fromName)) return false;
        let sentAt = Number(msg && msg.time || 0);
        if (!Number.isFinite(sentAt) || sentAt <= 0) sentAt = Date.parse(String(msg && msg.time || ""));
        return Number.isFinite(sentAt) && sentAt >= startedAt && sentAt <= now + 5000;
      });
    };

    let changed = false;
    if (kind === "public") {
      for (const [key, entry] of Array.from(state.publicTypingEntries.entries())) {
        if (!recentSenderMessage(entry)) continue;
        state.publicTypingEntries.delete(key);
        changed = true;
      }
    } else if (kind === "dm" && state.dmTypingEntry && recentSenderMessage(state.dmTypingEntry)) {
      state.dmTypingEntry = null;
      changed = true;
    } else if (kind === "group") {
      const activeRoom = String(roomId || state.groupActiveRoomId || "");
      for (const [key, entry] of Array.from(state.groupTypingEntries.entries())) {
        if (String(entry && entry.roomId || "") !== activeRoom) continue;
        if (!recentSenderMessage(entry)) continue;
        state.groupTypingEntries.delete(key);
        changed = true;
      }
    }
    if (changed) scheduleTypingIndicatorRefresh();
  }

  async function sendDirectMessageFromModal() {
    hideMentionAutocomplete();
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
    state.dmTypingNextAllowedAt = Date.now() + 5000;
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
