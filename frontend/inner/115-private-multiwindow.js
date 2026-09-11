// [KWC 유지보수 주석 / KWC maintenance notes]
// Standalone의 넓은 데스크톱 화면에서만 DM/그룹 목록을 부모 창으로 유지하고 각 대화를 독립 자식 창으로 띄운다.
// Only Standalone on a sufficiently large desktop viewport keeps DM/group lists as parent windows with independent conversation children.
// 네트워크 연결과 전역 메시지 state는 기존 하나를 공유한다. 비활성 자식 창은 마지막 렌더 스냅샷을 보관하고 다시 활성화될 때 최신 데이터를 불러온다.
// Network connections and global message state stay shared. Inactive child windows keep a last-render snapshot and refresh when reactivated.

  function privateMultiWindowSupported() {
    // Detached private child windows are a Standalone-only desktop feature.
    // Embedded map adapters keep the single-pane private-chat presentation even
    // when the iframe/page happens to have enough viewport space.
    return state.isStandalone === true
      && window.innerWidth >= Number(state.privateMultiWindowMinWidth || 900)
      && window.innerHeight >= Number(state.privateMultiWindowMinHeight || 480);
  }

  function privateConversationRegistry(type) {
    return type === "group" ? state.groupConversationWindows : state.dmConversationWindows;
  }

  function privateActiveConversationWindowKey(type) {
    return type === "group" ? String(state.groupActiveConversationWindow || "") : String(state.dmActiveConversationWindow || "");
  }

  function setPrivateActiveConversationWindowKey(type, key) {
    if (type === "group") state.groupActiveConversationWindow = String(key || "");
    else state.dmActiveConversationWindow = String(key || "");
  }

  function privateListWrap(type) {
    return document.querySelector(`[data-kwc-private-list-window="${type === "group" ? "group" : "dm"}"]`);
  }

  function privateListModal(type) {
    const wrap = privateListWrap(type);
    return wrap && wrap.querySelector(":scope > .kwc-dm-modal");
  }

  function privateListLayout(type) {
    const modal = privateListModal(type);
    return modal && modal.querySelector(":scope > .kwc-dm-layout");
  }

  function privateLiveConversation(type) {
    return document.querySelector(`[data-kwc-live-conversation="${type === "group" ? "group" : "dm"}"]`);
  }

  function privateChildWindowRecord(type, key) {
    return privateConversationRegistry(type).get(String(key || "")) || null;
  }

  function privateChildWindowOwner(type) {
    const record = privateChildWindowRecord(type, privateActiveConversationWindowKey(type));
    return record && record.modal && document.body.contains(record.modal) ? record.modal : privateListModal(type);
  }

  function publicChatWindowOwner() {
    return document.getElementById("kwc-root") || document.body;
  }

  function chatWindowOwnerTypeForNode(node) {
    if (!node || !node.closest) return "public";
    const child = node.closest("[data-kwc-private-child-window]");
    if (child) return child.getAttribute("data-kwc-private-child-window") === "group" ? "group" : "dm";
    const list = node.closest("[data-kwc-private-list-window]");
    if (list) return list.getAttribute("data-kwc-private-list-window") === "group" ? "group" : "dm";
    return "public";
  }

  function mountChatWindowOwnedOverlay(ownerType, wrap) {
    if (ownerType === "dm" || ownerType === "group") return mountPrivateWindowOwnedOverlay(ownerType, wrap);
    return mountWindowOwnedOverlay(wrap, publicChatWindowOwner());
  }

  function mountWindowOwnedOverlay(wrap, owner) {
    if (!wrap) return wrap;
    const target = owner || document.body;
    wrap.classList.add("kwc-window-owned-overlay");
    target.classList && target.classList.add("kwc-window-owner");
    target.appendChild(wrap);
    return wrap;
  }

  function mountPrivateWindowOwnedOverlay(type, wrap) {
    return mountWindowOwnedOverlay(wrap, privateChildWindowOwner(type));
  }

  function privateCloneMessages(values) {
    return Array.isArray(values) ? values.map(item => item && typeof item === "object" ? Object.assign({}, item) : item) : [];
  }

  function capturePrivateConversationContext(type) {
    if (type === "group") {
      return {
        roomId:String(state.groupActiveRoomId || ""), room:state.groupActiveRoom ? Object.assign({}, state.groupActiveRoom) : null,
        auditMode:state.groupAuditMode === true, auditRoom:state.groupAuditRoom ? Object.assign({}, state.groupAuditRoom) : null,
        policyOverride:state.groupPolicyOverride ? Object.assign({}, state.groupPolicyOverride) : null,
        pins:privateCloneMessages(state.groupPins), pinsCanPin:state.groupPinsCanPin === true,
        messages:privateCloneMessages(state.groupMessages), replyTarget:state.groupReplyTarget ? Object.assign({}, state.groupReplyTarget) : null,
        messagesHasMore:state.groupMessagesHasMore === true
      };
    }
    return {
      threadId:String(state.dmActiveThreadId || ""), draftTarget:state.dmDraftTarget ? Object.assign({}, state.dmDraftTarget) : null,
      auditMode:state.dmAuditMode === true, auditThread:state.dmAuditThread ? Object.assign({}, state.dmAuditThread) : null,
      messages:privateCloneMessages(state.dmMessages), replyTarget:state.dmReplyTarget ? Object.assign({}, state.dmReplyTarget) : null,
      messagesHasMore:state.dmMessagesHasMore === true
    };
  }

  function restorePrivateConversationContext(type, context) {
    const c = context || {};
    if (type === "group") {
      state.groupActiveRoomId = String(c.roomId || "");
      state.groupActiveRoom = c.room ? Object.assign({}, c.room) : null;
      state.groupAuditMode = c.auditMode === true;
      state.groupAuditRoom = c.auditRoom ? Object.assign({}, c.auditRoom) : null;
      state.groupPolicyOverride = c.policyOverride ? Object.assign({}, c.policyOverride) : null;
      state.groupPins = privateCloneMessages(c.pins);
      state.groupPinsCanPin = c.pinsCanPin === true;
      state.groupMessages = privateCloneMessages(c.messages);
      state.groupReplyTarget = c.replyTarget ? Object.assign({}, c.replyTarget) : null;
      state.groupMessagesHasMore = c.messagesHasMore === true;
      return;
    }
    state.dmActiveThreadId = String(c.threadId || "");
    state.dmDraftTarget = c.draftTarget ? Object.assign({}, c.draftTarget) : null;
    state.dmAuditMode = c.auditMode === true;
    state.dmAuditThread = c.auditThread ? Object.assign({}, c.auditThread) : null;
    state.dmMessages = privateCloneMessages(c.messages);
    state.dmReplyTarget = c.replyTarget ? Object.assign({}, c.replyTarget) : null;
    state.dmMessagesHasMore = c.messagesHasMore === true;
  }

  function preparePrivateConversationSnapshot(section) {
    if (!section) return section;
    section.classList.add("kwc-private-conversation-snapshot");
    section.removeAttribute("data-kwc-live-conversation");
    section.querySelectorAll("[id]").forEach(node => node.removeAttribute("id"));
    section.querySelectorAll("button, input, textarea, select").forEach(node => {
      node.disabled = true;
      node.tabIndex = -1;
    });
    section.querySelectorAll("[contenteditable]").forEach(node => node.removeAttribute("contenteditable"));
    return section;
  }

  function snapshotActivePrivateConversation(type) {
    const key = privateActiveConversationWindowKey(type);
    const record = privateChildWindowRecord(type, key);
    const live = privateLiveConversation(type);
    if (!record || !live || !record.body || !record.body.contains(live)) return;
    record.context = capturePrivateConversationContext(type);
    const scrollBox = live.querySelector(type === "group" ? "#kwc-group-messages" : "#kwc-dm-messages");
    record.scrollTop = scrollBox ? Number(scrollBox.scrollTop || 0) : 0;
    const snapshot = preparePrivateConversationSnapshot(live.cloneNode(true));
    // Keep the one interactive conversation DOM alive in the parent layout while
    // this child window keeps a read-only snapshot. Removing the live node here
    // would destroy the shared conversation surface and make the next child
    // activation unable to reattach it.
    const layout = privateListLayout(type);
    if (layout) layout.appendChild(live);
    record.body.replaceChildren(snapshot);
    record.live = false;
  }

  function privateConversationWindowTitle(type, contextId, draftTarget) {
    if (type === "group") {
      const room = (state.groupRooms || []).find(item => String(item && item.id || "") === String(contextId || ""))
        || (state.groupAdminRooms || []).find(item => String(item && item.id || "") === String(contextId || ""));
      return room ? groupRoomLabel(room) : t("group.title", "Group chats");
    }
    if (draftTarget) return directMessagePlainLabel(draftTarget.label || draftTarget.displayName || draftTarget.username || draftTarget.uuid || t("dm.title", "Messages"));
    const thread = (state.dmThreads || []).find(item => String(item && item.id || "") === String(contextId || ""))
      || (state.dmAdminThreads || []).find(item => String(item && item.id || "") === String(contextId || ""));
    return thread ? directMessageHeaderPlainLabel(thread, directMessageLabel(thread)) : t("dm.title", "Messages");
  }

  function privateConversationStorageKey(type, key) {
    const safe = String(key || "").replace(/[^A-Za-z0-9._:-]/g, "_").slice(0, 96);
    return `kwc.${type === "group" ? "group" : "dm"}ConversationWindow.${safe}`;
  }

  // Detached DM/group conversation windows are independent drop targets.
  // The parent list window used to own the only drag/drop listeners, so dropping
  // on a detached child did nothing unless the user dragged back over the inbox.
  // Resolve the child window first, activate its own thread/room, and only then
  // hand the files to the shared upload pipeline.
  function privateChildConversationAuditMode(record) {
    if (!record) return true;
    if (record.context && record.context.auditMode === true) return true;
    if (privateActiveConversationWindowKey(record.type) !== String(record.key || "")) return false;
    return record.type === "group" ? state.groupAuditMode === true : state.dmAuditMode === true;
  }

  function installPrivateChildDragAndDropUpload(record) {
    const wrap = record && record.wrap;
    const modal = record && record.modal;
    if (!wrap || !modal || wrap.dataset.kwcPrivateChildDropInstalled === "1") return;
    wrap.dataset.kwcPrivateChildDropInstalled = "1";

    const setOver = visible => {
      try { modal.classList.toggle("kwc-dm-drag-over", !!visible); } catch (_) {}
    };
    const allowed = () => !state.uploadActive && canUpload() && !privateChildConversationAuditMode(record);

    const onEnterOrOver = event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      const ok = allowed();
      if (event.dataTransfer) event.dataTransfer.dropEffect = ok ? "copy" : "none";
      setOver(ok);
    };
    const onLeave = event => {
      if (!isFileDragEvent(event)) return;
      const next = event.relatedTarget;
      if (next && wrap.contains(next)) return;
      setOver(false);
    };
    const onDrop = async event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      setOver(false);

      const files = dropEventFiles(event);
      if (!files.length) return;
      if (state.uploadActive) {
        alert(t("upload.dropBusy", "Upload is already in progress."));
        return;
      }
      if (!canUpload()) {
        alert(t("upload.dropDenied", "File upload is not allowed."));
        return;
      }
      if (privateChildConversationAuditMode(record)) return;

      // A drag does not generate the pointerdown that normally activates an
      // inactive snapshot window. Make the drop destination authoritative.
      await activatePrivateConversationWindow(record.type, record.key);
      if (privateActiveConversationWindowKey(record.type) !== String(record.key || "")) return;
      if (record.type === "group" ? state.groupAuditMode === true : state.dmAuditMode === true) return;

      const inputId = record.type === "group" ? "kwc-group-input" : "kwc-dm-input";
      const input = document.getElementById(inputId);
      if (!input || !record.body || !record.body.contains(input)) return;
      setActiveComposeInput(input);
      await uploadFiles(files, "drop");
    };
    const clearOver = () => setOver(false);

    wrap.addEventListener("dragenter", onEnterOrOver, {capture:true});
    wrap.addEventListener("dragover", onEnterOrOver, {capture:true});
    wrap.addEventListener("dragleave", onLeave, {capture:true});
    wrap.addEventListener("drop", onDrop, {capture:true});
    document.addEventListener("dragend", clearOver, {capture:true});

    record.dropCleanup = () => {
      try { document.removeEventListener("dragend", clearOver, {capture:true}); } catch (_) {}
      setOver(false);
    };
  }

  function createPrivateConversationWindow(type, key, contextId, draftTarget) {
    const registry = privateConversationRegistry(type);
    const existing = registry.get(key);
    if (existing && existing.wrap && document.body.contains(existing.wrap)) return existing;
    const wrap = document.createElement("div");
    wrap.className = `kwc-modal-backdrop kwc-dm-modal-backdrop kwc-private-child-backdrop${type === "group" ? " kwc-group-modal-backdrop" : ""}`;
    wrap.dataset.kwcPrivateChildWindow = type;
    wrap.dataset.kwcPrivateChildKey = key;
    applyDetachedModalTheme(wrap);
    const title = privateConversationWindowTitle(type, contextId, draftTarget);
    wrap.innerHTML = `<div class="kwc-modal kwc-dm-modal kwc-dm-thread-mode kwc-private-child-modal${type === "group" ? " kwc-group-modal" : ""}"><div class="kwc-dm-head"><h3 class="kwc-dm-main-title"><span class="kwc-private-child-title">${esc(title)}</span></h3><div class="kwc-dm-head-actions"><button type="button" class="kwc-button kwc-private-child-close">${esc(t("button.close", "Close"))}</button></div></div><div class="kwc-private-child-body"></div></div>`;
    document.body.appendChild(wrap);
    const record = {type, key, contextId:String(contextId || ""), draftTarget:draftTarget ? Object.assign({}, draftTarget) : null, wrap, modal:wrap.querySelector(":scope > .kwc-dm-modal"), body:wrap.querySelector(".kwc-private-child-body"), context:null, live:false, lastFocusedAt:Date.now(), scrollTop:0};
    registry.set(key, record);
    installIndependentChatWindow(wrap, {storageKey:privateConversationStorageKey(type, key), child:true});
    installPrivateChildDragAndDropUpload(record);
    record.modal.addEventListener("pointerdown", event => {
      if (event.target && event.target.closest && event.target.closest(".kwc-private-child-close, .kwc-window-owned-overlay")) return;
      if (privateActiveConversationWindowKey(type) !== key) activatePrivateConversationWindow(type, key).catch(() => {});
    }, {capture:true});
    wrap.querySelector(".kwc-private-child-close").onclick = event => {
      event.preventDefault(); event.stopPropagation(); closePrivateConversationWindow(type, key);
    };
    return record;
  }

  async function activatePrivateConversationWindow(type, key) {
    const registry = privateConversationRegistry(type);
    const record = registry.get(String(key || ""));
    if (!record || !record.wrap || !document.body.contains(record.wrap)) return;
    const currentKey = privateActiveConversationWindowKey(type);
    if (currentKey && currentKey !== record.key) snapshotActivePrivateConversation(type);
    let live = privateLiveConversation(type);
    if (!live) {
      const layout = privateListLayout(type);
      live = layout && layout.querySelector(".kwc-dm-conversation");
      if (live) live.dataset.kwcLiveConversation = type;
    }
    if (!live) return;
    if (record.context) restorePrivateConversationContext(type, record.context);
    record.modal.classList.add("kwc-dm-thread-mode");
    record.body.replaceChildren(live);
    record.live = true;
    record.lastFocusedAt = Date.now();
    setPrivateActiveConversationWindowKey(type, record.key);
    raiseIndependentChatWindow(record.wrap);
    if (type === "group") {
      if (!record.context) {
        state.groupAuditMode = false; state.groupAuditRoom = null; state.groupActiveRoomId = record.contextId; state.groupActiveRoom = null;
      }
      await openGroupRoom(record.contextId);
      const box = document.getElementById("kwc-group-messages"); if (box && record.scrollTop > 0) box.scrollTop = record.scrollTop;
    } else {
      if (!record.context) {
        state.dmAuditMode = false; state.dmAuditThread = null;
        state.dmActiveThreadId = record.contextId;
        state.dmDraftTarget = record.draftTarget ? Object.assign({}, record.draftTarget) : null;
      }
      setActiveChatView("dm", state.dmActiveThreadId || "");
      clearPrivateReply("dm");
      updateDirectMessageComposeControls();
      renderDirectMessageThreads();
      renderDirectMessageHeader(state.dmActiveThreadId || "");
      updateDirectMessageViewMode();
      if (state.dmActiveThreadId) {
        await loadDirectMessageMessages(state.dmActiveThreadId);
        await restoreChatViewAnchor("dm", state.dmActiveThreadId);
      } else {
        renderDirectMessageMessages([]);
      }
      const box = document.getElementById("kwc-dm-messages"); if (box && record.scrollTop > 0) box.scrollTop = record.scrollTop;
    }
    syncPrivateConversationWindowTitle(type);
  }

  async function openPrivateConversationWindow(type, contextId, options = {}) {
    if (!privateMultiWindowSupported()) return false;
    contextId = String(contextId || "").trim();
    const draftTarget = options.draftTarget || null;
    const key = contextId || (draftTarget && draftTarget.uuid ? `draft:${String(draftTarget.uuid).toLowerCase()}` : "");
    if (!key) return false;
    const record = createPrivateConversationWindow(type, key, contextId, draftTarget);
    await activatePrivateConversationWindow(type, record.key);
    const parentModal = privateListModal(type);
    if (parentModal) parentModal.classList.add("kwc-private-multi-list");
    return true;
  }

  function closePrivateConversationWindow(type, key, options = {}) {
    const registry = privateConversationRegistry(type);
    key = String(key || "");
    const record = registry.get(key);
    if (!record) return;
    const active = privateActiveConversationWindowKey(type) === key;
    if (active) {
      record.context = capturePrivateConversationContext(type);
      const live = privateLiveConversation(type);
      const others = Array.from(registry.values()).filter(item => item.key !== key && item.wrap && document.body.contains(item.wrap)).sort((a,b) => Number(b.lastFocusedAt || 0) - Number(a.lastFocusedAt || 0));
      if (live) {
        const layout = privateListLayout(type);
        if (layout) layout.appendChild(live);
      }
      setPrivateActiveConversationWindowKey(type, "");
      if (!options.skipActivate && others.length) setTimeout(() => activatePrivateConversationWindow(type, others[0].key).catch(() => {}), 0);
      else if (!others.length) {
        if (type === "group") { state.groupActiveRoomId = ""; state.groupActiveRoom = null; state.groupAuditMode = false; state.groupAuditRoom = null; renderGroupChatHeader(); renderGroupChatMessages([]); }
        else { state.dmActiveThreadId = ""; state.dmDraftTarget = null; state.dmAuditMode = false; state.dmAuditThread = null; renderDirectMessageHeader(""); renderDirectMessageMessages([]); updateDirectMessageViewMode(); }
      }
    }
    if (record.dropCleanup) record.dropCleanup();
    if (record.wrap && record.wrap.__kwcWindowChromeCleanup) record.wrap.__kwcWindowChromeCleanup();
    if (record.wrap) record.wrap.remove();
    registry.delete(key);
    const parentModal = privateListModal(type);
    if (parentModal && registry.size === 0) parentModal.classList.remove("kwc-private-multi-list");
  }

  function closeAllPrivateConversationWindows(type, options = {}) {
    const registry = privateConversationRegistry(type);
    Array.from(registry.keys()).forEach(key => closePrivateConversationWindow(type, key, {skipActivate:true}));
    registry.clear();
    setPrivateActiveConversationWindowKey(type, "");
    const parentModal = privateListModal(type);
    if (parentModal) parentModal.classList.remove("kwc-private-multi-list");
  }

  function collapsePrivateConversationWindowsToSinglePane(type) {
    const registry = privateConversationRegistry(type);
    if (!registry.size) return;
    const activeKey = privateActiveConversationWindowKey(type);
    const active = registry.get(activeKey) || Array.from(registry.values()).sort((a,b) => Number(b.lastFocusedAt || 0) - Number(a.lastFocusedAt || 0))[0];
    if (activeKey && active) active.context = capturePrivateConversationContext(type);
    let live = privateLiveConversation(type);
    const layout = privateListLayout(type);
    if (live && layout) layout.appendChild(live);
    if (active && active.context) restorePrivateConversationContext(type, active.context);
    Array.from(registry.values()).forEach(record => {
      if (record.wrap && record.wrap.__kwcWindowChromeCleanup) record.wrap.__kwcWindowChromeCleanup();
      if (record.wrap) record.wrap.remove();
    });
    registry.clear();
    setPrivateActiveConversationWindowKey(type, "");
    const parentModal = privateListModal(type);
    if (parentModal) parentModal.classList.remove("kwc-private-multi-list");
    if (type === "group") { renderGroupChatHeader(); renderGroupChatMessages(state.groupMessages || []); updateGroupChatComposeControls(); }
    else { renderDirectMessageHeader(state.dmActiveThreadId || ""); renderDirectMessageMessages(state.dmMessages || []); updateDirectMessageViewMode(); updateDirectMessageComposeControls(); }
  }

  function installPrivateMultiWindowViewportGuard() {
    if (state.privateMultiWindowResizeInstalled) return;
    state.privateMultiWindowResizeInstalled = true;
    const sync = () => {
      if (!privateMultiWindowSupported()) {
        collapsePrivateConversationWindowsToSinglePane("dm");
        collapsePrivateConversationWindowsToSinglePane("group");
      }
      if (typeof reflowAllKwcModalsToViewport === "function") reflowAllKwcModalsToViewport();
    };
    const syncSettled = () => { sync(); setTimeout(sync, 80); setTimeout(sync, 260); };
    window.addEventListener("resize", syncSettled, {passive:true});
    window.addEventListener("orientationchange", syncSettled, {passive:true});
    if (window.visualViewport) window.visualViewport.addEventListener("resize", syncSettled, {passive:true});
  }

  // Mobile viewport guard. DM/group backdrops live on document.body, so a
  // plain 100vh/100% can extend below the actually visible mobile viewport when
  // browser chrome or the virtual keyboard changes height. Follow visualViewport
  // directly and keep the composer inside the visible region.
  function installPrivateMobileViewportFit(wrap) {
    if (!wrap || wrap.dataset.kwcPrivateMobileViewportFit === "1") return;
    wrap.dataset.kwcPrivateMobileViewportFit = "1";
    const modal = wrap.querySelector(":scope > .kwc-dm-modal");
    if (!modal) return;
    let mobileActive = false;
    let savedWrapStyle = "";
    let savedModalStyle = "";

    const restoreDesktopStyle = () => {
      if (!mobileActive) return;
      if (savedWrapStyle) wrap.setAttribute("style", savedWrapStyle);
      else wrap.removeAttribute("style");
      if (savedModalStyle) modal.setAttribute("style", savedModalStyle);
      else modal.removeAttribute("style");
      wrap.classList.remove("kwc-private-mobile-viewport");
      mobileActive = false;
      if (typeof installIndependentChatWindow === "function") installIndependentChatWindow(wrap);
      if (typeof reflowModalIntoVisibleViewport === "function") reflowModalIntoVisibleViewport(modal, "");
      if (modal.__kwcResizeZoneUpdate) modal.__kwcResizeZoneUpdate();
    };

    const sync = () => {
      if (!document.body.contains(wrap)) return;
      if (privateMultiWindowSupported()) {
        restoreDesktopStyle();
        return;
      }
      if (!mobileActive) {
        savedWrapStyle = wrap.getAttribute("style") || "";
        savedModalStyle = modal.getAttribute("style") || "";
        mobileActive = true;
      }
      const viewport = window.visualViewport;
      const width = Math.max(1, Math.round(Number(viewport && viewport.width) || Number(window.innerWidth) || document.documentElement.clientWidth || 1));
      const height = Math.max(1, Math.round(Number(viewport && viewport.height) || Number(window.innerHeight) || document.documentElement.clientHeight || 1));
      const left = Math.max(0, Math.round(Number(viewport && viewport.offsetLeft) || 0));
      const top = Math.max(0, Math.round(Number(viewport && viewport.offsetTop) || 0));
      wrap.classList.add("kwc-private-mobile-viewport");
      wrap.style.setProperty("inset", "auto", "important");
      wrap.style.setProperty("left", left + "px", "important");
      wrap.style.setProperty("top", top + "px", "important");
      wrap.style.setProperty("right", "auto", "important");
      wrap.style.setProperty("bottom", "auto", "important");
      wrap.style.setProperty("width", width + "px", "important");
      wrap.style.setProperty("height", height + "px", "important");
      wrap.style.setProperty("max-width", width + "px", "important");
      wrap.style.setProperty("max-height", height + "px", "important");
      modal.style.setProperty("position", "relative", "important");
      modal.style.setProperty("left", "0", "important");
      modal.style.setProperty("top", "0", "important");
      modal.style.setProperty("width", "100%", "important");
      modal.style.setProperty("height", "100%", "important");
      modal.style.setProperty("max-width", "100%", "important");
      modal.style.setProperty("max-height", "100%", "important");
      modal.style.setProperty("margin", "0", "important");
    };

    window.addEventListener("resize", sync, {passive:true});
    window.addEventListener("orientationchange", sync, {passive:true});
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", sync, {passive:true});
      window.visualViewport.addEventListener("scroll", sync, {passive:true});
    }
    wrap.__kwcMobileViewportCleanup = () => {
      window.removeEventListener("resize", sync, {passive:true});
      window.removeEventListener("orientationchange", sync, {passive:true});
      if (window.visualViewport) {
        window.visualViewport.removeEventListener("resize", sync, {passive:true});
        window.visualViewport.removeEventListener("scroll", sync, {passive:true});
      }
    };
    sync();
    setTimeout(sync, 80);
    setTimeout(sync, 260);
  }

  function syncPrivateConversationWindowTitle(type) {
    const key = privateActiveConversationWindowKey(type);
    const record = privateChildWindowRecord(type, key);
    if (!record || !record.wrap) return;
    const title = record.wrap.querySelector(".kwc-private-child-title");
    if (!title) return;
    const contextId = type === "group" ? state.groupActiveRoomId : state.dmActiveThreadId;
    title.textContent = privateConversationWindowTitle(type, contextId, type === "dm" ? state.dmDraftTarget : null);
  }

  function rekeyActivePrivateConversationWindow(type, nextKey) {
    nextKey = String(nextKey || "").trim();
    if (!nextKey) return;
    const registry = privateConversationRegistry(type);
    const oldKey = privateActiveConversationWindowKey(type);
    if (!oldKey || oldKey === nextKey) return;
    const record = registry.get(oldKey);
    if (!record) return;
    const duplicate = registry.get(nextKey);
    if (duplicate && duplicate !== record) closePrivateConversationWindow(type, nextKey, {skipActivate:true});
    registry.delete(oldKey);
    record.key = nextKey;
    record.contextId = nextKey;
    record.draftTarget = null;
    record.wrap.dataset.kwcPrivateChildKey = nextKey;
    registry.set(nextKey, record);
    setPrivateActiveConversationWindowKey(type, nextKey);
    syncPrivateConversationWindowTitle(type);
  }
