// [KWC 유지보수 주석 / KWC maintenance notes]
// 그룹방 설정, owner/admin/member 역할 관리, kick/ban, pin panel, 실제 메시지 삭제 같은 room-local 관리 기능을 담당한다.
// This fragment handles room-local management: group settings, owner/admin/member roles, kick/ban, pin panel, and real message deletion.
// 그룹 admin은 KWC 전역 역할이 아니라 해당 room 안에서만 유효하다. owner만 admin 승격/해제가 가능하고 서버가 role을 최종 검증한다.
// A group admin is not a global KWC role; it is valid only inside that room. Only the owner can promote/demote admins, with final role checks enforced server-side.
// 모든 멤버는 pinned-message panel을 볼 수 있지만 pin/unpin/reorder는 owner/admin만 가능하도록 조회 권한과 변경 권한을 분리한다.
// Every room member may view the pinned-message panel, while pin/unpin/reorder mutations are restricted to owner/admin, keeping read and mutation permissions separate.

  // 현재 room의 role에 맞는 관리 메뉴를 만든다. owner/admin/member에 따라 DOM에 생성되는 action 자체를 달리해 불필요한 권한 UI 노출을 줄인다.

  // Builds the current room’s management menu according to the room-local role. Actions are conditionally created in the DOM for owner/admin/member to reduce unnecessary privileged UI exposure.

  function openGroupConversationSettingsMenu() {
    const room = state.groupActiveRoom;
    if (!room || !state.groupActiveRoomId || state.groupAuditMode) return;
    const canManage = room.role === "owner" || room.role === "admin";
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `<div class="kwc-modal kwc-conversation-settings-modal">
      <div class="kwc-modal-head"><h3>${esc(t("group.settings", "Settings"))} · ${esc(groupRoomLabel(room))}</h3><button class="kwc-button" id="kwc-conv-close">${esc(t("button.close", "Close"))}</button></div>
      <div class="kwc-account-actions">
        ${state.conversationArchiveEnabled ? `<button class="kwc-button" id="kwc-conv-save">${esc(t("archive.saveConversation", "Save conversation"))}</button><button class="kwc-button" id="kwc-conv-library">${esc(t("archive.library", "Saved conversations"))}</button>` : ""}
        ${canManage ? `<button class="kwc-button" id="kwc-conv-invite">${esc(t("group.invite", "Invite"))}</button><button class="kwc-button" id="kwc-conv-room-settings">${esc(t("group.roomSettings", "Room settings"))}</button>` : ""}
        <button class="kwc-button" id="kwc-conv-hide">${esc(t("group.hideRoom", "Hide from list"))}</button>
      </div>
    </div>`;
    mountPrivateWindowOwnedOverlay("group", wrap);
    const close = () => wrap.remove();
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });
    wrap.querySelector("#kwc-conv-close").onclick = close;
    const archiveSave = wrap.querySelector("#kwc-conv-save");
    if (archiveSave) archiveSave.onclick = () => { close(); beginConversationArchiveSelection("group", room.id, groupRoomLabel(room)); };
    const archiveLibrary = wrap.querySelector("#kwc-conv-library");
    if (archiveLibrary) archiveLibrary.onclick = () => { close(); openConversationArchiveLibrary(); };
    const invite = wrap.querySelector("#kwc-conv-invite");
    if (invite) invite.onclick = () => { close(); inviteToGroupRoom(); };
    const settings = wrap.querySelector("#kwc-conv-room-settings");
    if (settings) settings.onclick = () => { close(); updateGroupRoomSettings(); };
    wrap.querySelector("#kwc-conv-hide").onclick = () => { close(); hideGroupRoomForMe(); };
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
        const presenceHtml = presenceCompactHtml(m, m.uuid || "");
        const roleAction = canTransfer && m.role === "member" ? `<button class="kwc-button" data-group-role="admin" data-group-role-target="${esc(m.uuid)}">${esc(t("group.makeAdmin", "Make admin"))}</button>` : (canTransfer && m.role === "admin" ? `<button class="kwc-button" data-group-role="member" data-group-role-target="${esc(m.uuid)}">${esc(t("group.removeAdmin", "Remove admin"))}</button>` : "");
        const actions = canManage ? `${m.role !== "owner" ? `<button class="kwc-button" data-group-kick="${esc(m.uuid)}">${esc(t("group.kick", "Kick"))}</button><button class="kwc-button" data-group-ban="${esc(m.uuid)}">${esc(t("group.ban", "Ban"))}</button>` : ""}${roleAction}${canTransfer && m.role !== "owner" ? `<button class="kwc-button" data-group-transfer="${esc(m.uuid)}">${esc(t("group.transferOwner", "Transfer owner"))}</button>` : ""}` : "";
        return `<div class="kwc-group-member-row"><span>${directMessageIdentityHtml({displayName: m.displayName || m.label || m.username || "", username: m.username || "", uuid: m.uuid || ""}, "kwc-sender")}<small>${esc(m.role || "member")} · ${presenceHtml}</small></span><span class="kwc-group-member-actions">${actions}</span></div>`;
      }).join("") || `<div class="kwc-dm-empty">${esc(t("group.noMembers", "No members."))}</div>`;
      const banRows = bans.map(b => `<div class="kwc-group-member-row"><span>${directMessageIdentityHtml({displayName: b.displayName || b.label || b.username || "", username: b.username || "", uuid: b.uuid || ""}, "kwc-sender")}<small>${esc(t("group.banned", "Banned"))}${b.bannedByLabel ? " · " + esc(b.bannedByLabel) : ""}</small></span><span class="kwc-group-member-actions"><button class="kwc-button" data-group-unban="${esc(b.uuid)}">${esc(t("group.unban", "Unban"))}</button></span></div>`).join("") || `<div class="kwc-dm-empty">${esc(t("group.noBans", "No banned users."))}</div>`;
      const manageNote = canManage ? t("group.manageNote", "Room managers can kick or ban members; the owner can assign room admins or transfer ownership. Message contents are not shown here.") : t("group.memberListNote", "Members can view the participant list. Management actions are only shown to room managers.");
      const bansSection = canManage ? `<h4>${esc(t("group.bannedUsers", "Banned users"))}</h4><div class="kwc-group-member-list">${banRows}</div>` : "";
      wrap.innerHTML = `<div class="kwc-modal kwc-group-manage-modal"><div class="kwc-modal-head"><h3>${esc(t("group.manage", "Manage"))} · ${esc(groupRoomLabel(room))}</h3><button class="kwc-button" id="kwc-group-manage-close">${esc(t("button.close", "Close"))}</button></div><p class="kwc-admin-meta-note">${esc(manageNote)}</p><h4>${esc(t("group.members", "Members"))}</h4><div class="kwc-group-member-list">${memberRows}</div>${bansSection}</div>`;
      mountPrivateWindowOwnedOverlay("group", wrap);
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
      wrap.querySelectorAll("[data-group-role]").forEach(btn => btn.onclick = async () => {
        const targetUuid = btn.dataset.groupRoleTarget || "";
        const nextRole = btn.dataset.groupRole || "member";
        if (!targetUuid) return;
        const label = labelForTarget(targetUuid);
        const confirmKey = nextRole === "admin" ? "group.confirmMakeAdmin" : "group.confirmRemoveAdmin";
        const fallback = nextRole === "admin" ? "Make {player} a room admin?" : "Remove room admin from {player}?";
        if (!confirmPlain(fmt(confirmKey, fallback, {player: label}))) return;
        try {
          await api("/group/set-role", {method: "POST", body: JSON.stringify({roomId, targetUuid, role: nextRole})});
          close();
          await loadGroupChatRooms(true);
          const refreshed = (state.groupRooms || []).find(r => r.id === roomId);
          if (refreshed) state.groupActiveRoom = refreshed;
          renderGroupChatRooms(); renderGroupChatHeader();
        } catch (e) { alertResponse("alert.groupActionFailed", "Group action failed: {error}", e.response || {error: e.message || "error"}); }
      });
    } catch (e) {
      alertResponse("alert.groupActionFailed", "Group action failed: {error}", e.response || {error: e.message || "error"});
    }
  }

  function returnGroupChatToList() {
    const childKey = privateActiveConversationWindowKey("group");
    if (privateMultiWindowSupported() && childKey) { closePrivateConversationWindow("group", childKey); return; }
    if (state.groupActiveRoomId && !state.groupAuditMode) saveConversationView("group", state.groupActiveRoomId);
    clearPrivateReply("group");
    state.groupActiveRoomId = "";
    setActiveChatView("group", "");
    state.groupActiveRoom = null;
    state.groupPins = []; state.groupPinsCanPin = false; renderGroupPinnedBar();
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
      return `<button type="button" class="kwc-dm-player" data-group-player="${esc(player.uuid)}" data-group-player-label="${esc(label)}" title="${esc(directMessagePlainLabel(label))}"><span>${directMessageLabelHtml(label)}</span>${presenceCompactHtml(player, player.uuid || "", false)}</button>`;
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
    if (state.groupEmojiPanelOpen) markNonScrollUiAction();
    state.groupEmojiPanelOpen = false;
    resetEmojiSearchState("group");
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
    markNonScrollUiAction();
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
    if (state.groupEmojiSearchOpen) requestAnimationFrame(() => positionEmojiSearchOverlay("group"));
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
    renderCustomEmojiPanel(panel, "group");
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
    const result = reconcilePrivateMessageList(box, arr, "group", conversationKey, auditNotice, `<div class="kwc-dm-empty kwc-group-membership-event kwc-group-empty-event"><span class="kwc-group-membership-event-text">${esc(t("group.emptyRoom", "No messages yet."))}</span></div>`);
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
      clearTypingIndicatorsFromMessages("group", state.groupMessages, roomId);
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

  function adoptGroupChatSendResponse(res, roomId, clientMessageId) {
    if (res && res.room && String(res.room.id || "") === String(roomId || "")) {
      state.groupActiveRoom = Object.assign({}, state.groupActiveRoom || {}, res.room);
    }
    if (res && res.message && /^\d+$/.test(String(res.message.id || ""))) {
      const index = (state.groupMessages || []).findIndex(msg => String(msg && msg.clientMessageId || "") === String(clientMessageId || ""));
      const persisted = Object.assign({}, res.message, {clientMessageId: String(clientMessageId || "")});
      if (index >= 0) state.groupMessages.splice(index, 1, persisted);
      else state.groupMessages = mergePrivateMessagePages(state.groupMessages || [], [persisted]);
      clearTypingIndicatorsFromMessages("group", state.groupMessages, roomId);
      renderGroupChatMessages(state.groupMessages, {stickToBottom: true});
    } else {
      updateOptimisticGroupMessage(clientMessageId, "delivered", "");
    }
    loadGroupChatRooms(true).catch(() => {});
    if (state.groupActiveRoomId === roomId && !state.groupMessagesLoading) loadGroupChatMessages(roomId).catch(() => {});
  }

  async function sendGroupChatAttempt(roomId, message, clientMessageId, replyToId = 0) {
    try {
      const body = {roomId, message, clientMessageId};
      if (Number(replyToId || 0) > 0) body.replyToId = Number(replyToId);
      const res = await api("/group/send", {method: "POST", body: JSON.stringify(body)});
      adoptGroupChatSendResponse(res, roomId, clientMessageId);
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
    hideMentionAutocomplete();
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
    state.groupTypingNextAllowedAt = Date.now() + 5000;
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
      const pinsChecked = !isSettings || room.pinsEnabled !== false;
      const deleteChecked = !isSettings || room.messageDeleteEnabled !== false;
      const selfDeleteChecked = !isSettings || room.memberSelfDeleteEnabled !== false;
      const membershipEventsBlock = `<label class="kwc-group-form-toggle"><input type="checkbox" id="kwc-group-form-membership-events" ${membershipEventsChecked ? "checked" : ""}><span>${esc(t("group.membershipEvents", "Show member join/leave notices"))}</span></label>`;
      const messagePolicyBlock = `<label class="kwc-group-form-toggle"><input type="checkbox" id="kwc-group-form-pins-enabled" ${pinsChecked ? "checked" : ""}><span>${esc(t("group.pinsEnabled", "Enable pinned messages in this room"))}</span></label><label class="kwc-group-form-toggle"><input type="checkbox" id="kwc-group-form-delete-enabled" ${deleteChecked ? "checked" : ""}><span>${esc(t("group.messageDeleteEnabled", "Enable message deletion in this room"))}</span></label><label class="kwc-group-form-toggle"><input type="checkbox" id="kwc-group-form-self-delete-enabled" ${selfDeleteChecked ? "checked" : ""}><span>${esc(t("group.memberSelfDeleteEnabled", "Allow members to delete their own messages"))}</span></label>`;
      wrap.innerHTML = `<div class="kwc-modal kwc-group-form-modal"><div class="kwc-group-form-head"><h3>${esc(title)}</h3></div><div class="kwc-group-form-grid"><label class="kwc-group-form-field"><span>${esc(t("group.roomName", "Room name"))}</span><input class="kwc-input" id="kwc-group-form-name" value="${esc(currentName)}" maxlength="80"></label><label class="kwc-group-form-field"><span>${esc(t("group.visibility", "Visibility"))}</span>${groupVisibilityOptionsHtml(room.visibility || "private")}</label>${passwordBlock}${membershipEventsBlock}${messagePolicyBlock}</div><div class="kwc-row kwc-group-form-actions"><button type="button" class="kwc-button" id="kwc-group-form-save">${esc(t("button.save", "Save"))}</button><button type="button" class="kwc-button" id="kwc-group-form-cancel">${esc(t("button.cancel", "Cancel"))}</button></div></div>`;
      mountPrivateWindowOwnedOverlay("group", wrap);
      const close = value => { wrap.remove(); resolve(value); };
      wrap.addEventListener("click", event => { if (event.target === wrap) close(null); });
      const nameInput = wrap.querySelector("#kwc-group-form-name");
      const visibilityInput = wrap.querySelector("#kwc-group-form-visibility");
      const passwordInput = wrap.querySelector("#kwc-group-form-password");
      const membershipEventsInput = wrap.querySelector("#kwc-group-form-membership-events");
      const pinsEnabledInput = wrap.querySelector("#kwc-group-form-pins-enabled");
      const deleteEnabledInput = wrap.querySelector("#kwc-group-form-delete-enabled");
      const selfDeleteEnabledInput = wrap.querySelector("#kwc-group-form-self-delete-enabled");
      const syncDeletePolicy = () => { if (selfDeleteEnabledInput) selfDeleteEnabledInput.disabled = !!deleteEnabledInput && !deleteEnabledInput.checked; };
      if (deleteEnabledInput) deleteEnabledInput.addEventListener("change", syncDeletePolicy);
      syncDeletePolicy();
      const submit = () => {
        const name = String(nameInput && nameInput.value || "").trim();
        if (!name) { if (nameInput) nameInput.focus(); return; }
        const visibility = state.groupChatAllowPublicRooms ? String(visibilityInput && visibilityInput.value || "private").toLowerCase() : "private";
        const out = {name, visibility: visibility === "public" ? "public" : "private", membershipEventsEnabled: !membershipEventsInput || !!membershipEventsInput.checked, pinsEnabled: !pinsEnabledInput || !!pinsEnabledInput.checked, messageDeleteEnabled: !deleteEnabledInput || !!deleteEnabledInput.checked, memberSelfDeleteEnabled: !selfDeleteEnabledInput || !!selfDeleteEnabledInput.checked};
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
      const res = await api("/group/create", {method: "POST", body: JSON.stringify({name: form.name, visibility: form.visibility, password: form.password || "", membershipEventsEnabled: form.membershipEventsEnabled !== false, pinsEnabled: form.pinsEnabled !== false, messageDeleteEnabled: form.messageDeleteEnabled !== false, memberSelfDeleteEnabled: form.memberSelfDeleteEnabled !== false})});
      if (res.room) { state.groupActiveRoomId = String(res.room.id || ""); state.groupActiveRoom = res.room; }
      await loadGroupChatRooms(true);
      const refreshed = (state.groupRooms || []).find(r => r.id === state.groupActiveRoomId);
      if (refreshed) state.groupActiveRoom = refreshed;
      renderGroupChatRooms();
      renderGroupChatHeader();
      if (state.groupActiveRoomId && privateMultiWindowSupported()) {
        await openPrivateConversationWindow("group", state.groupActiveRoomId);
        return;
      }
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
    try {
      await api("/group/leave", {method: "POST", body: JSON.stringify({roomId})});
      if (privateMultiWindowSupported()) closePrivateConversationWindow("group", roomId, {skipActivate: true});
      state.groupActiveRoomId = ""; state.groupActiveRoom = null; renderGroupChatMessages([]); renderGroupChatHeader(); await loadGroupChatRooms(true);
    }
    catch (e) { alertResponse("alert.groupLeaveFailed", "Failed to leave room: {error}", e.response || {error: e.message || "error"}); }
  }

  async function updateGroupRoomSettings() {
    const room = state.groupActiveRoom || (state.groupRooms || []).find(r => r.id === state.groupActiveRoomId);
    if (!room) return;
    const form = await openGroupRoomForm({mode: "settings", room});
    if (!form) return;
    const body = {roomId: state.groupActiveRoomId, name: form.name, visibility: form.visibility, membershipEventsEnabled: form.membershipEventsEnabled !== false, pinsEnabled: form.pinsEnabled !== false, messageDeleteEnabled: form.messageDeleteEnabled !== false, memberSelfDeleteEnabled: form.memberSelfDeleteEnabled !== false};
    if (state.groupChatAllowRoomPasswords) body.password = form.password || "";
    try {
      const res = await api("/group/settings", {method: "POST", body: JSON.stringify(body)});
      const canonical = Object.assign({}, room, body, res && res.room || {});
      state.groupActiveRoom = canonical;
      state.groupActiveRoomId = String(canonical.id || state.groupActiveRoomId || "");
      state.groupPolicyOverride = {roomId: state.groupActiveRoomId, until: Date.now() + 30000, pinsEnabled: canonical.pinsEnabled !== false, messageDeleteEnabled: canonical.messageDeleteEnabled !== false, memberSelfDeleteEnabled: canonical.memberSelfDeleteEnabled !== false};
      state.groupRooms = (state.groupRooms || []).map(item => String(item && item.id || "") === state.groupActiveRoomId ? Object.assign({}, item, canonical) : item);
      if (canonical.pinsEnabled === false) {
        state.groupPinsEnabled = false;
        state.groupPins = [];
        state.groupPinsCanPin = false;
        renderGroupPinnedBar();
      } else {
        state.groupPinsEnabled = true;
      }
      renderGroupChatRooms();
      renderGroupChatHeader();
      renderGroupChatMessages(state.groupMessages || [], {stickToBottom: false});
      updateGroupChatComposeControls();
      if (state.groupActiveRoomId && canonical.pinsEnabled !== false) loadGroupPins(state.groupActiveRoomId).catch(() => {});
      loadGroupChatRooms(true).catch(() => {});
    }
    catch (e) { alertResponse("alert.groupSettingsFailed", "Failed to update room: {error}", e.response || {error: e.message || "error"}); }
  }

  async function respondGroupInvite(inviteId, accept) {
    try { const res = await api("/group/invite/respond", {method: "POST", body: JSON.stringify({inviteId, accept})}); if (accept && res.room) { state.groupActiveRoomId = res.room.id; state.groupActiveRoom = res.room; } await loadGroupChatRooms(true); if (state.groupActiveRoomId) await loadGroupChatMessages(state.groupActiveRoomId); }
    catch (e) { alertResponse("alert.groupInviteFailed", "Failed to update invitation: {error}", e.response || {error: e.message || "error"}); }
  }

  // 일반 member는 자기 메시지, owner/admin은 관리 가능한 메시지만 실제 삭제 요청한다. 삭제 후 메시지 목록과 pin snapshot을 다시 동기화한다.

  // Requests real deletion: ordinary members can target their own messages, while owner/admin can manage permitted room messages. Message and pin state are resynchronized afterward.

  async function deleteGroupMessage(messageId) {
    if (state.groupAuditMode) return;
    messageId = String(messageId || "").trim();
    if (!messageId || !state.token) return;
    if (state.groupChatConfirmDelete && !confirmPlain(t("group.confirmDeleteMessage", "Delete this message from the room?"))) return;
    try {
      await api("/group/delete-message", {method: "POST", body: JSON.stringify({messageId})});
      state.groupMessages = (state.groupMessages || []).filter(msg => String(msg && msg.id || "") !== messageId);
      if (state.groupReplyTarget && String(state.groupReplyTarget.id || "") === messageId) clearPrivateReply("group");
      renderGroupChatMessages(state.groupMessages, {stickToBottom: false});
      if (state.groupActiveRoomId) {
        await loadGroupPins(state.groupActiveRoomId);
        if (!state.groupMessagesLoading) await loadGroupChatMessages(state.groupActiveRoomId);
      }
      await loadGroupChatRooms(true);
    } catch (e) {
      alertResponse("alert.groupDeleteFailed", "Failed to delete message: {error}", e.response || {error: e.message || "error"});
    }
  }


  async function openGroupChatModal() {
    if (!state.token) { openLoginModal(); return; }
    if (!state.groupChatEnabled) return;
    if (state.groupModalOpen) return;
    state.groupModalOpen = true;
    state.groupActiveRoomId = "";
    setActiveChatView("group", "");
    publishNotificationViewState();
    state.groupActiveRoom = null;
    state.groupAuditMode = false;
    state.groupAuditRoom = null;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-dm-modal-backdrop kwc-group-modal-backdrop";
    wrap.dataset.kwcPrivateListWindow = "group";
    applyDetachedModalTheme(wrap);
    wrap.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
    wrap.style.setProperty("--kwc-emoji-panel-height", emojiPanelHeightPx() + "px");
    wrap.style.setProperty("--kwc-emoji-panel-min-height", emojiPanelMinHeightPx() + "px");
    wrap.innerHTML = `<div class="kwc-modal kwc-dm-modal kwc-group-modal"><div class="kwc-dm-head"><h3 class="kwc-dm-main-title"><span>${esc(t("group.title", "Group chats"))}</span><span class="kwc-dm-retention" title="${esc(groupRoomRetentionText())}">${esc(groupRoomRetentionText())}</span></h3><button class="kwc-button" id="kwc-group-close">${esc(t("button.closeAllGroups", "Close all group chats"))}</button></div><div class="kwc-dm-layout"><aside class="kwc-dm-sidebar"><button type="button" class="kwc-button kwc-dm-new" id="kwc-group-create">${esc(t("group.newRoom", "New room"))}</button><div class="kwc-group-invites" id="kwc-group-invites"></div><div class="kwc-dm-thread-list" id="kwc-group-room-list"></div></aside><section class="kwc-dm-conversation" data-kwc-live-conversation="group"><div class="kwc-dm-title kwc-group-title" id="kwc-group-title">${esc(t("group.selectRoom", "Select a room"))}</div><div class="kwc-pinned-bar kwc-group-pinned-bar kwc-hidden" id="kwc-group-pinned-bar"><button class="kwc-pinned-open" id="kwc-group-pinned-open" type="button"><span class="kwc-pinned-icon">📌</span><span id="kwc-group-pinned-label"></span></button></div><div class="kwc-private-search-float-row"><button class="kwc-button kwc-private-window-tool kwc-private-search-float kwc-hidden" id="kwc-group-message-search-open" type="button" title="${esc(t("button.search", "Search"))}" aria-label="${esc(t("button.search", "Search"))}">⌕</button></div><div class="kwc-dm-messages" id="kwc-group-messages"></div><div class="kwc-emoji-resize-handle kwc-dm-emoji-resize kwc-hidden" id="kwc-group-emoji-resize" title="${esc(t("button.resizeEmojiPanel", "Drag to resize emoji picker"))}" aria-label="${esc(t("button.resizeEmojiPanel", "Drag to resize emoji picker"))}"></div><div class="kwc-reply-compose kwc-private-reply-compose kwc-hidden" id="kwc-group-reply-compose"><button type="button" class="kwc-reply-compose-main" id="kwc-group-reply-compose-main" title="${esc(t("reply.jump", "Jump to replied message"))}"><span class="kwc-reply-compose-label" id="kwc-group-reply-compose-label"></span><span class="kwc-reply-compose-preview" id="kwc-group-reply-compose-preview"></span></button><button type="button" class="kwc-mini-action kwc-reply-cancel" id="kwc-group-reply-cancel" title="${esc(t("button.cancel", "Cancel"))}">×</button></div><div class="kwc-dm-compose kwc-row kwc-typing-anchor"><div class="kwc-typing-indicator kwc-hidden" id="kwc-group-typing" aria-live="polite"></div><textarea class="kwc-input kwc-chat-composer" id="kwc-group-input" rows="1" autocomplete="off" enterkeyhint="send" placeholder="${esc(t("placeholder.message", "message"))}" ${state.groupChatMaxMessageLength > 0 ? `maxlength="${state.groupChatMaxMessageLength}"` : ""}></textarea><button class="kwc-button kwc-dm-emoji-button kwc-hidden" id="kwc-group-emoji" title="${esc(t("button.emoji", "Emoji"))}">☺</button><button class="kwc-button kwc-dm-upload kwc-hidden" id="kwc-group-upload" title="${esc(t("button.upload", "Attach"))}">&#128206;</button><button class="kwc-button kwc-dm-send" id="kwc-group-send">${esc(t("button.send", "Send"))}</button><input type="file" id="kwc-group-file" class="kwc-file-input" multiple hidden style="display:none !important;"></div><div class="kwc-emoji-panel kwc-dm-emoji-panel kwc-group-emoji-panel kwc-hidden" id="kwc-group-emoji-panel" aria-live="polite"></div>${uploadProgressHtml("kwc-group-upload-progress")}</section></div><div class="kwc-dm-search-panel kwc-hidden" id="kwc-group-search-panel"><div class="kwc-dm-search-head"><strong>${esc(t("group.searchPlayer", "Search player to invite"))}</strong><button class="kwc-button" id="kwc-group-search-close" type="button">${esc(t("button.close", "Close"))}</button></div><input class="kwc-input" id="kwc-group-search" placeholder="${esc(t("group.searchPlayer", "Search player to invite"))}"><div class="kwc-dm-player-results" id="kwc-group-player-results"></div></div></div>`;
    document.body.appendChild(wrap);
    // On desktop multi-window layouts the parent inbox/group list uses the same
    // movable/resizable/maximizable chrome as child conversations. Narrow layouts
    // keep the existing single-pane behavior because installIndependentChatWindow
    // returns early below the multi-window threshold.
    installIndependentChatWindow(wrap);
    installPrivateMultiWindowViewportGuard();
    installPrivateMobileViewportFit(wrap);
    installDirectMessageIdentityToggleGuard(wrap);
    installChatViewScrollPersistence("group", wrap.querySelector("#kwc-group-messages"));
    const close = () => {
      if (state.groupActiveRoomId) saveConversationView("group", state.groupActiveRoomId);
      hideEmojiAutocomplete();
      hideMentionAutocomplete();
      closeGroupChatEmojiPanel();
      closeGroupPlayerSearch();
      hideGroupChatEdgeToast(true);
      discardPrivateMessageDom(document.getElementById("kwc-group-messages"));
      closeAllPrivateConversationWindows("group", {parentClosing:true});
      if (wrap.__kwcWindowChromeCleanup) wrap.__kwcWindowChromeCleanup();
      if (wrap.__kwcMobileViewportCleanup) wrap.__kwcMobileViewportCleanup();
      wrap.remove();
      state.groupModalOpen = false;
      state.groupActiveRoomId = "";
      if (state.dmModalOpen && state.dmActiveThreadId) setActiveChatView("dm", state.dmActiveThreadId);
      else setActiveChatView("public", "");
      state.groupActiveRoom = null;
      state.groupPins = []; state.groupPinsCanPin = false;
      publishNotificationViewState();
      state.groupAuditMode = false;
      state.groupAuditRoom = null;
      state.groupReplyTarget = null;
      state.groupTypingEntries.clear();
      renderTypingIndicators();
      if (state.activeComposeInputId === "kwc-group-input") state.activeComposeInputId = "kwc-message";
    };
    wrap.querySelector("#kwc-group-close").onclick = close;
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
    const groupPinnedOpen = wrap.querySelector("#kwc-group-pinned-open");
    if (groupPinnedOpen) groupPinnedOpen.onclick = openGroupPinnedModal;
    const groupMessageSearch = wrap.querySelector("#kwc-group-message-search-open");
    if (groupMessageSearch) groupMessageSearch.onclick = event => { event.preventDefault(); event.stopPropagation(); openPrivateMessageSearchModal("group"); };
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
    input.addEventListener("focus", () => { setActiveComposeInput(input); scheduleMentionAutocomplete(input); });
    input.addEventListener("click", () => scheduleMentionAutocomplete(input));
    input.addEventListener("blur", () => setTimeout(() => { if (!document.getElementById("kwc-mention-autocomplete")?.matches(":hover")) hideMentionAutocomplete(); }, 160));
    input.addEventListener("input", () => { normalizeSingleLineComposer(input); scheduleMentionAutocomplete(input); if (String(input.value || "").trim()) notifyGroupTyping(); });
    input.addEventListener("paste", async e => {
      setActiveComposeInput(input);
      await handlePasteUpload(e);
    });
    input.addEventListener("keydown", e => {
      if (handleMentionAutocompleteKeydown(e, input)) return;
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
    setActiveChatView("dm", "");
    publishNotificationViewState();
    state.dmDraftTarget = null;
    state.dmAuditMode = false;
    state.dmAuditThread = null;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-dm-modal-backdrop";
    wrap.dataset.kwcPrivateListWindow = "dm";
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
            <button class="kwc-button" id="kwc-dm-close">${t("button.closeAllDm", "Close all DMs")}</button>
          </div>
        </div>
        <div class="kwc-dm-layout">
          <aside class="kwc-dm-sidebar" id="kwc-dm-sidebar">
            <button type="button" class="kwc-button kwc-dm-new" id="kwc-dm-new">${t("dm.newMessage", "New message")}</button>
            <div class="kwc-dm-thread-list" id="kwc-dm-thread-list"></div>
          </aside>
          <section class="kwc-dm-conversation" data-kwc-live-conversation="dm">
            <div class="kwc-private-title-row"><div class="kwc-dm-title" id="kwc-dm-title">${t("dm.selectThread", "Select a thread")}</div><div class="kwc-private-title-actions"><button type="button" class="kwc-button kwc-private-back-to-list kwc-hidden" id="kwc-dm-back-to-list">${t("dm.backToList", "Back to conversation list")}</button>${state.conversationArchiveEnabled ? `<button class="kwc-button kwc-hidden" id="kwc-dm-settings">${t("group.settings", "Settings")}</button>` : ""}</div></div>
            <div class="kwc-private-search-float-row"><button class="kwc-button kwc-private-window-tool kwc-private-search-float kwc-hidden" id="kwc-dm-message-search-open" type="button" title="${t("button.search", "Search")}" aria-label="${t("button.search", "Search")}">⌕</button></div>
            <div class="kwc-dm-messages" id="kwc-dm-messages"></div>
            <div class="kwc-emoji-resize-handle kwc-dm-emoji-resize kwc-hidden" id="kwc-dm-emoji-resize" title="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}" aria-label="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}"></div>
            <div class="kwc-reply-compose kwc-private-reply-compose kwc-hidden" id="kwc-dm-reply-compose"><button type="button" class="kwc-reply-compose-main" id="kwc-dm-reply-compose-main" title="${t("reply.jump", "Jump to replied message")}"><span class="kwc-reply-compose-label" id="kwc-dm-reply-compose-label"></span><span class="kwc-reply-compose-preview" id="kwc-dm-reply-compose-preview"></span></button><button type="button" class="kwc-mini-action kwc-reply-cancel" id="kwc-dm-reply-cancel" title="${t("button.cancel", "Cancel")}">×</button></div>
            <div class="kwc-dm-compose kwc-row kwc-typing-anchor">
              <div class="kwc-typing-indicator kwc-hidden" id="kwc-dm-typing" aria-live="polite"></div>
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
    // On desktop multi-window layouts the parent inbox/group list uses the same
    // movable/resizable/maximizable chrome as child conversations. Narrow layouts
    // keep the existing single-pane behavior because installIndependentChatWindow
    // returns early below the multi-window threshold.
    installIndependentChatWindow(wrap);
    installPrivateMultiWindowViewportGuard();
    installPrivateMobileViewportFit(wrap);
    installDirectMessageIdentityToggleGuard(wrap);
    installChatViewScrollPersistence("dm", wrap.querySelector("#kwc-dm-messages"));
    const close = () => { if (state.dmActiveThreadId) saveConversationView("dm", state.dmActiveThreadId); hideEmojiAutocomplete(); hideMentionAutocomplete(); closeDirectMessageEmojiPanel(); closeDirectMessagePlayerSearch(); hideDirectMessageEdgeToast(true); discardPrivateMessageDom(document.getElementById("kwc-dm-messages")); closeAllPrivateConversationWindows("dm", {parentClosing:true}); if (wrap.__kwcWindowChromeCleanup) wrap.__kwcWindowChromeCleanup(); if (wrap.__kwcMobileViewportCleanup) wrap.__kwcMobileViewportCleanup(); wrap.remove(); state.dmModalOpen = false; state.dmActiveThreadId = ""; if (state.groupModalOpen && state.groupActiveRoomId) setActiveChatView("group", state.groupActiveRoomId); else setActiveChatView("public", ""); state.dmAuditMode = false; state.dmAuditThread = null; state.dmReplyTarget = null; state.dmTypingEntry = null; publishNotificationViewState(); renderTypingIndicators(); if (state.activeComposeInputId === "kwc-dm-input") state.activeComposeInputId = "kwc-message"; };
    wrap.querySelector("#kwc-dm-close").onclick = close;
    wrap.addEventListener("click", e => {
      if (!state.dmSearchPanelOpen) return;
      const panel = wrap.querySelector("#kwc-dm-search-panel");
      const newButton = wrap.querySelector("#kwc-dm-new");
      const target = e.target;
      if (panel && panel.contains(target)) return;
      if (newButton && newButton.contains(target)) return;
      closeDirectMessagePlayerSearch();
    });
    const dmBackToList = wrap.querySelector("#kwc-dm-back-to-list");
    if (dmBackToList) dmBackToList.onclick = event => { event.preventDefault(); event.stopPropagation(); returnDirectMessageToList(); };
    const dmSettings = wrap.querySelector("#kwc-dm-settings");
    if (dmSettings) dmSettings.onclick = event => { event.preventDefault(); event.stopPropagation(); openDirectConversationSettingsMenu(); };
    const dmMessageSearch = wrap.querySelector("#kwc-dm-message-search-open");
    if (dmMessageSearch) dmMessageSearch.onclick = event => { event.preventDefault(); event.stopPropagation(); openPrivateMessageSearchModal("dm"); };
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
    dmInput.addEventListener("focus", () => { setActiveComposeInput(dmInput); scheduleMentionAutocomplete(dmInput); });
    dmInput.addEventListener("click", () => scheduleMentionAutocomplete(dmInput));
    dmInput.addEventListener("blur", () => setTimeout(() => { if (!document.getElementById("kwc-mention-autocomplete")?.matches(":hover")) hideMentionAutocomplete(); }, 160));
    dmInput.addEventListener("input", () => { normalizeSingleLineComposer(dmInput); scheduleMentionAutocomplete(dmInput); if (String(dmInput.value || "").trim()) notifyDirectTyping(); });
    dmInput.addEventListener("paste", async e => {
      setActiveComposeInput(dmInput);
      await handlePasteUpload(e);
    });
    dmInput.addEventListener("keydown", e => {
      if (handleMentionAutocompleteKeydown(e, dmInput)) return;
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
        <div class="kwc-modal-head">
          <div class="kwc-account-profile-entry">
            <button type="button" class="kwc-account-profile-name" id="kwc-account-profile-name" title="${esc(t("preferences.profileButton", "Profile"))}">${esc(state.username)}</button>
            <button type="button" class="kwc-button" id="kwc-account-profile-open">${esc(t("preferences.profileButton", "Profile"))}</button>
          </div>
          <button class="kwc-button" id="kwc-close">${t("button.close", "Close")}</button>
        </div>
        <p>${t("account.role", "Role")}: ${esc(state.role)}</p>
        <div class="kwc-account-actions">
          ${(!state.config || state.config.uiUserPreferencesControl !== false) ? `<button class="kwc-button" id="kwc-user-prefs">${t("preferences.title", "Chat settings")}</button>` : ""}
          ${state.conversationArchiveEnabled ? `<button class="kwc-button" id="kwc-archives">${t("archive.accountButton", "Save conversation")}</button>` : ""}
          <button class="kwc-button" id="kwc-set-pw">${t("button.setPassword", "Set password")}</button>
          <button class="kwc-button" id="kwc-logout">${t("button.logout", "Logout")}</button>
        </div>
      </div>
    `;
    document.body.appendChild(wrap);
    wrap.querySelector("#kwc-close").onclick = () => { wrap.remove(); state.loginModalOpen = false; };
    const openSelfProfile = () => {
      const uuid = String(state.userUuid || "").trim();
      if (!uuid) return;
      wrap.remove();
      state.loginModalOpen = false;
      openUserPresenceProfile(uuid);
    };
    const profileName = wrap.querySelector("#kwc-account-profile-name");
    const profileOpen = wrap.querySelector("#kwc-account-profile-open");
    if (profileName) profileName.onclick = openSelfProfile;
    if (profileOpen) profileOpen.onclick = openSelfProfile;
    const prefsBtn = wrap.querySelector("#kwc-user-prefs");
    if (prefsBtn) prefsBtn.onclick = () => {
      wrap.remove();
      state.loginModalOpen = false;
      state.prefsModalOpen = false;
      openUserPreferencesModal();
    };
    const archivesBtn = wrap.querySelector("#kwc-archives");
    if (archivesBtn) archivesBtn.onclick = () => { wrap.remove(); state.loginModalOpen = false; openConversationArchiveLibrary(); };
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
    state.authPendingToken = "";
    state.authVerified = true;
    state.username = res.username;
    state.userUuid = String(res.uuid || "");
    state.role = res.role;
    localStorage.setItem("kwc.token", state.token);
    localStorage.setItem("kwc.username", state.username);
    localStorage.setItem("kwc.role", state.role);
    updateLoginState();
    updateGuestVisibility();
    loadConfig().then(() => {
      if (state.messages && state.messages.length) scheduleVirtualRender({preserveScroll: true});
    }).catch(() => {});
    refreshLoggedInCount().catch(() => {});
    refreshCaptcha();
    loadPins();
    loadCommands();
    loadDirectMessageThreads(true);
    loadGroupChatRooms(true);
    loadAccountNotificationPreferences().then(() => { if (!state.isPip) ensurePreferredWebPush().catch(() => {}); }).catch(() => {});
    loadAccountTypingPreferences().catch(() => {});
    loadAccountPresencePreferences().catch(() => {});
    loadBlockedUsers().catch(() => {});
    loadAccountEmojiFavorites().catch(() => {});
    loadReactionCatalog(true).then(refreshVisibleReactionBars).catch(() => {});
    if (!state.isPip) connectStream({refreshAfterOpen: true, reason: "login"});
  }

  async function verifyStoredToken() {
    const candidate = String(state.token || state.authPendingToken || "").trim();
    if (!candidate) {
      state.authVerified = false;
      return false;
    }
    let lastTransientError = null;
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const res = await api("/auth/me", {headers: {Authorization: "Bearer " + candidate}, timeoutMs: 6000});
        if (!res.ok) {
          handleAuthExpired("verify", {reconnect: false});
          return false;
        }
        state.token = candidate;
        state.authPendingToken = "";
        state.authVerified = true;
        state.username = res.username;
        state.userUuid = String(res.uuid || "");
        state.role = res.role;
        localStorage.setItem("kwc.token", state.token);
        localStorage.setItem("kwc.username", state.username || "");
        localStorage.setItem("kwc.role", state.role || "");
        updateLoginState();
        updateGuestVisibility();
        return true;
      } catch (e) {
        const status = Number(e && e.status || 0);
        const code = String(e && e.response && e.response.error || "");
        if (status === 401 || code === "not_logged_in" || code === "login_required" || code === "auth_expired" || code === "invalid_token") {
          handleAuthExpired("verify", {reconnect: false});
          return false;
        }
        lastTransientError = e;
        if (attempt < 2) await new Promise(resolve => setTimeout(resolve, attempt === 0 ? 250 : 750));
      }
    }
    // A transient network/5xx failure is not evidence that the saved session expired.
    // Keep the persisted/pending credential and retry briefly before showing the guest-safe UI.
    // Only an explicit session rejection above removes the stored login. Generic
    // 403 capability/permission errors are not evidence that the session expired.
    state.token = "";
    state.authPendingToken = candidate;
    state.authVerified = false;
    updateLoginState();
    updateGuestVisibility();
    if (lastTransientError) reportOperationalIssue("auth:verify", "transient-final", "KOKOTO WebChat stored session verification was temporarily unavailable", {error:String(lastTransientError.message || lastTransientError)});
    return false;
  }


  if (typeof navigator !== "undefined" && navigator.serviceWorker) {
    navigator.serviceWorker.addEventListener("message", event => {
      const data = event && event.data || {};
      if (!data || (data.source !== "KWC" && data.source !== "KWCParent")) return;
      if (data.type === "notificationSuppressionQuery") {
        const port = event.ports && event.ports[0];
        if (port) {
          const suppress = notificationTargetCurrentlyVisible({dmThreadId:data.dmThreadId || "", groupRoomId:data.groupRoomId || "", publicChat:data.publicChat === true});
          try { port.postMessage({suppress}); } catch (_) {}
        }
        return;
      }
      if (data.type !== "notificationNavigate") return;
      navigateFromNotification(data.url || data);
    });
  }

  window.addEventListener("message", event => {
    if (!trustedParentMessageEvent(event)) return;
    const data = event && event.data || {};
    if (!data || (data.source !== "KWC" && data.source !== "KWCParent") || data.type !== "notificationNavigate") return;
    navigateFromNotification(data.url || data);
  });
