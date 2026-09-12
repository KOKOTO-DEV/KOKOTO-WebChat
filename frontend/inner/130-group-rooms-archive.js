// [KWC 유지보수 주석 / KWC maintenance notes]
// 그룹방 목록/초대/입장/퇴장/읽음 상태와 conversation archive UI를 담당한다.
// This fragment owns group-room lists, invites, join/leave/read state, and conversation-archive UI.
// 그룹방을 목록에서 숨기고 복원하는 기능은 메시지 단위 hide와 다른 room-list preference이므로 5.3.0에서도 유지된다.
// Hiding/restoring a group room in the room list is a room-list preference distinct from message-level hide and remains supported in 5.3.0.
// active room 재조정은 방에서 추방/탈퇴했거나 audit 모드 권한이 사라졌을 때 stale 메시지가 화면에 남지 않도록 현재 선택을 즉시 해제한다.
// Active-room reconciliation immediately clears the selection when membership/audit access is lost so stale room messages are not left visible.

  function updateGroupChatButton() {
    const btn = document.getElementById("kwc-group");
    const badge = document.getElementById("kwc-group-badge");
    if (btn) btn.classList.toggle("kwc-hidden", !(state.token && state.groupChatEnabled) || state.minimized);
    if (!badge) return;
    const unread = Math.max(0, Number(state.groupUnread || 0));
    badge.textContent = unread > 99 ? "99+" : String(unread);
    badge.classList.toggle("kwc-hidden", !(state.token && state.groupChatEnabled && unread > 0));
  }

  // room 목록을 새로 받은 뒤 현재 선택이 아직 유효한지 검증한다. 멤버십이나 audit 권한이 사라졌으면 active room과 표시 메시지를 즉시 초기화한다.

  // After reloading room lists, validates that the current selection is still authorized. If membership or audit access vanished, it immediately clears the active room and visible messages.

  function reconcileActiveGroupRoomAfterRoomLoad() {
    if (!state.groupActiveRoomId) return false;
    // A room-settings POST broadcasts a group refresh before its HTTP response can
    // finish on the browser. Preserve the canonical active room for a short policy
    // window so an older/in-flight room-list response cannot blank the conversation
    // or disable the composer while the user is still a valid member.
    const policyOverride = state.groupPolicyOverride;
    const protectActiveRoom = !state.groupAuditMode
      && policyOverride
      && Date.now() < Number(policyOverride.until || 0)
      && String(policyOverride.roomId || "") === String(state.groupActiveRoomId || "");
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
      if (protectActiveRoom && state.groupActiveRoom) return false;
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
      const override = state.groupPolicyOverride;
      if (override && Date.now() < Number(override.until || 0)) {
        const confirmed = state.groupRooms.find(room => String(room && room.id || "") === String(override.roomId || ""));
        const confirmedPolicy = confirmed
          && (confirmed.pinsEnabled !== false) === (override.pinsEnabled !== false)
          && (confirmed.messageDeleteEnabled !== false) === (override.messageDeleteEnabled !== false)
          && (confirmed.memberSelfDeleteEnabled !== false) === (override.memberSelfDeleteEnabled !== false);
        if (confirmedPolicy) {
          state.groupPolicyOverride = null;
        } else {
          state.groupRooms = state.groupRooms.map(room => String(room && room.id || "") === String(override.roomId || "") ? Object.assign({}, room, {pinsEnabled: override.pinsEnabled !== false, messageDeleteEnabled: override.messageDeleteEnabled !== false, memberSelfDeleteEnabled: override.memberSelfDeleteEnabled !== false}) : room);
        }
      } else if (override) {
        state.groupPolicyOverride = null;
      }
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
    publishNotificationViewState();
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
      const privacyIcon = room.visibility === "public" ? kwcFaIcon("globe") : kwcFaIcon("lock");
      const passwordIcon = room.passwordProtected ? ` ${kwcFaIcon("key")}` : "";
      const join = room.member ? "" : ` <span class="kwc-group-join-hint">${esc(t("group.join", "join"))}</span>`;
      return `<button type="button" class="kwc-dm-thread kwc-group-room${active}" data-group-room="${esc(room.id)}"><span class="kwc-dm-thread-name"><span class="kwc-group-room-icon" aria-hidden="true">${privacyIcon}</span> ${esc(groupRoomLabel(room))}${passwordIcon}</span>${badge}<span class="kwc-dm-thread-preview">${esc(visibility)} · ${esc(room.memberCount || 0)} ${esc(t("group.membersShort", "members"))}${join}</span></button>`;
    }).join("");
    const hiddenHtml = hiddenRooms.length ? `<div class="kwc-admin-meta-title">${esc(t("group.hiddenRooms", "Hidden rooms"))}</div>` + hiddenRooms.map(room => {
      const privacyIcon = room.visibility === "public" ? kwcFaIcon("globe") : kwcFaIcon("lock");
      const passwordIcon = room.passwordProtected ? ` ${kwcFaIcon("key")}` : "";
      return `<div class="kwc-dm-thread kwc-group-hidden-row"><span class="kwc-dm-thread-name"><span class="kwc-group-room-icon" aria-hidden="true">${privacyIcon}</span> ${esc(groupRoomLabel(room))}${passwordIcon}</span><span class="kwc-admin-meta-actions"><button type="button" class="kwc-button" data-group-unhide-room="${esc(room.id || "")}">${esc(t("group.showRoom", "Show"))}</button></span><span class="kwc-dm-thread-preview">${esc(t("group.hiddenRoomHint", "Hidden from your list"))}</span></div>`;
    }).join("") : "";
    const adminTitle = state.groupChatContentAccess
      ? t("admin.groupAuditTitle", "Admin group audit")
      : t("admin.groupMetaOnly", "Admin room metadata");
    const adminHtml = adminRooms.length ? `<div class="kwc-admin-meta-title">${kwcFaIcon("shield-halved")} ${esc(adminTitle)}</div>` + adminRooms.map(room => {
      const privacyIcon = room.visibility === "public" ? kwcFaIcon("globe") : kwcFaIcon("lock");
      const passwordIcon = room.passwordProtected ? ` ${kwcFaIcon("key")}` : "";
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
      return `<div class="kwc-dm-thread kwc-admin-meta-row${openClass}"${openAttrs}><span class="kwc-dm-thread-name" title="${esc(openTitle)}">${kwcFaIcon("shield-halved")} ${privacyIcon} ${esc(room.name || t("group.untitled", "Untitled room"))}${passwordIcon}</span><span class="kwc-admin-meta-actions"><button type="button" class="kwc-button" data-group-admin-lock-room="${esc(room.id || "")}" data-next-locked="${room.locked ? "false" : "true"}" title="${esc(lockTitle)}" aria-label="${esc(lockTitle)}">${esc(lockLabel)}</button><button type="button" class="kwc-button" data-group-admin-retention-room="${esc(room.id || "")}" data-next-exempt="${room.retentionExempt ? "false" : "true"}" title="${esc(exemptTitle)}" aria-label="${esc(exemptTitle)}">${esc(exemptLabel)}</button><button type="button" class="kwc-button kwc-admin-meta-danger" data-group-admin-delete-room="${esc(room.id || "")}" title="${esc(deleteTitle)}" aria-label="${esc(deleteTitle)}">${esc(t("admin.deleteRoom", "Delete"))}</button></span><span class="kwc-dm-thread-preview" title="${esc(meta.replace(/<[^>]*>/g, ""))}">${meta}</span></div>`;
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
    if (privateMultiWindowSupported()) { openPrivateConversationWindow("group", roomId).catch(() => {}); return; }
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
    if (previousRoomId && String(previousRoomId) !== roomId) saveConversationView("group", previousRoomId);

    state.groupActiveRoomId = roomId;
    setActiveChatView("group", roomId);
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
        setActiveChatView("group", state.groupActiveRoomId || "");
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
    await loadGroupPins(roomId);
    await loadGroupChatMessages(roomId);
    await restoreChatViewAnchor("group", roomId);
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
    state.groupPins = []; state.groupPinsCanPin = false; renderGroupPinnedBar();
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

  let groupHeaderResizeObserver = null;
  let groupHeaderObservedTitle = null;
  let groupHeaderSyncFrame = 0;

  function groupHeaderElementVisible(el) {
    if (!el) return false;
    const style = getComputedStyle(el);
    return style.display !== "none" && style.visibility !== "hidden";
  }

  function groupHeaderOuterWidth(el) {
    if (!groupHeaderElementVisible(el)) return 0;
    const style = getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    const width = Math.max(Number(rect.width || 0), Number(el.scrollWidth || 0));
    return width + (parseFloat(style.marginLeft) || 0) + (parseFloat(style.marginRight) || 0);
  }

  function groupHeaderActionsNaturalWidth(actions) {
    if (!groupHeaderElementVisible(actions)) return 0;
    const visible = Array.from(actions.querySelectorAll("button")).filter(groupHeaderElementVisible);
    if (!visible.length) return 0;
    const style = getComputedStyle(actions);
    const gap = parseFloat(style.columnGap || style.gap) || 6;
    return visible.reduce((sum, button) => sum + groupHeaderOuterWidth(button), 0) + gap * Math.max(0, visible.length - 1);
  }

  function syncGroupHeaderLayout() {
    groupHeaderSyncFrame = 0;
    const title = document.getElementById("kwc-group-title");
    if (!title || !title.isConnected) return;
    const main = title.querySelector(":scope > .kwc-group-title-main");
    const actions = title.querySelector(":scope > .kwc-group-actions");
    if (!main || !actions) {
      title.classList.remove("kwc-group-title-wrapped");
      return;
    }
    const titleStyle = getComputedStyle(title);
    const contentWidth = Math.max(0, Number(title.clientWidth || 0)
      - (parseFloat(titleStyle.paddingLeft) || 0)
      - (parseFloat(titleStyle.paddingRight) || 0));
    const name = main.querySelector(".kwc-group-title-name");
    const fixedMain = Array.from(main.children).filter(el => el !== name && groupHeaderElementVisible(el));
    const mainStyle = getComputedStyle(main);
    const mainGap = parseFloat(mainStyle.columnGap || mainStyle.gap) || 6;
    const protectedNameWidth = name ? Math.min(180, Math.max(96, groupHeaderOuterWidth(name))) : 96;
    const protectedMainWidth = protectedNameWidth
      + fixedMain.reduce((sum, el) => sum + groupHeaderOuterWidth(el), 0)
      + mainGap * Math.max(0, fixedMain.length + (name ? 1 : 0) - 1);
    const requiredOneRowWidth = protectedMainWidth + groupHeaderActionsNaturalWidth(actions) + 8;
    title.classList.toggle("kwc-group-title-wrapped", contentWidth + 1 < requiredOneRowWidth);
  }

  function scheduleGroupHeaderLayout() {
    if (groupHeaderSyncFrame) return;
    groupHeaderSyncFrame = requestAnimationFrame(syncGroupHeaderLayout);
  }

  function installGroupHeaderLayout(title) {
    if (!title) return;
    if (groupHeaderObservedTitle !== title) {
      if (groupHeaderResizeObserver) groupHeaderResizeObserver.disconnect();
      groupHeaderObservedTitle = title;
      if (window.ResizeObserver) {
        groupHeaderResizeObserver = new ResizeObserver(scheduleGroupHeaderLayout);
        groupHeaderResizeObserver.observe(title);
      }
    }
    scheduleGroupHeaderLayout();
  }

  function renderGroupChatHeader() {
    const title = document.getElementById("kwc-group-title");
    if (!title) return;
    const modal = title.closest(".kwc-group-modal");
    const room = state.groupActiveRoom || (state.groupRooms || []).find(r => r.id === state.groupActiveRoomId);
    const multi = privateMultiWindowSupported() && privateConversationRegistry("group").size > 0;
    if (modal) modal.classList.toggle("kwc-dm-thread-mode", !!room && (!multi || modal.classList.contains("kwc-private-child-modal")));
    title.classList.remove("kwc-dm-title-back");
    title.title = "";
    title.setAttribute("role", "heading");
    title.tabIndex = -1;
    title.onclick = null;
    title.onkeydown = null;
    if (!room) {
      title.classList.remove("kwc-group-title-audit");
      title.textContent = t("group.selectRoom", "Select a room");
      const groupMessageSearch = document.getElementById("kwc-group-message-search-open");
      if (groupMessageSearch) groupMessageSearch.classList.add("kwc-hidden");
      installGroupHeaderLayout(title);
      syncPrivateConversationWindowTitle("group");
      return;
    }
    const backButton = `<button type="button" class="kwc-button kwc-private-back-to-list${multi ? " kwc-hidden" : ""}" id="kwc-group-back-to-list">${esc(t("group.backToList", "Back to group chat list"))}</button>`;
    if (state.groupAuditMode) {
      title.classList.add("kwc-group-title-audit");
      const privacyLabel = room.visibility === "public" ? t("group.public", "public") : t("group.private", "private");
      title.innerHTML = `<span class="kwc-group-title-main"><span class="kwc-group-audit-badge">${kwcFaIcon("shield-halved")} ${esc(t("admin.groupAuditView", "Group audit"))}</span><span class="kwc-group-visibility-badge">${esc(privacyLabel)}</span><span class="kwc-group-title-name">${esc(groupRoomLabel(room))}</span></span><span class="kwc-group-actions">${backButton}</span>`;
      const back = document.getElementById("kwc-group-back-to-list");
      if (back) back.onclick = event => { event.preventDefault(); event.stopPropagation(); returnGroupChatToList(); };
      const groupMessageSearch = document.getElementById("kwc-group-message-search-open");
      if (groupMessageSearch) groupMessageSearch.classList.add("kwc-hidden");
      updateGroupChatComposeControls();
      installGroupHeaderLayout(title);
      syncPrivateConversationWindowTitle("group");
      return;
    }
    title.classList.remove("kwc-group-title-audit");
    const privacyLabel = room.visibility === "public" ? t("group.public", "public") : t("group.private", "private");
    const passwordBadge = room.passwordProtected ? `<span class="kwc-group-password-badge" title="${esc(t("group.passwordProtected", "password"))}" aria-label="${esc(t("group.passwordProtected", "password"))}">${kwcFaIcon("key")}</span>` : "";
    const memberCount = Math.max(0, Number(room.memberCount || 0));
    const onlineCount = Math.max(0, Number(room.onlineMemberCount || 0));
    const countText = fmt("group.memberOnlineCount", "{online}/{total} online", {online: onlineCount, total: memberCount});
    title.innerHTML = `<span class="kwc-group-title-main"><span class="kwc-group-visibility-badge">${esc(privacyLabel)}</span><span class="kwc-group-title-name">${esc(groupRoomLabel(room))}</span>${passwordBadge}<span class="kwc-group-member-counts" id="kwc-group-member-counts" role="button" tabindex="0" title="${esc(t("group.members", "Members"))}" aria-label="${esc(t("group.members", "Members"))}">${esc(countText)}</span></span><span class="kwc-group-actions">${backButton}<button class="kwc-button" id="kwc-group-settings">${esc(t("group.settings", "Settings"))}</button><button class="kwc-button" id="kwc-group-leave">${esc(t("group.leave", "Leave"))}</button></span>`;
    const back = document.getElementById("kwc-group-back-to-list");
    if (back) back.onclick = event => { event.preventDefault(); event.stopPropagation(); returnGroupChatToList(); };
    const settings = document.getElementById("kwc-group-settings");
    if (settings) settings.onclick = event => { event.preventDefault(); event.stopPropagation(); openGroupConversationSettingsMenu(); };
    const groupMessageSearch = document.getElementById("kwc-group-message-search-open");
    if (groupMessageSearch) groupMessageSearch.classList.toggle("kwc-hidden", !searchEnabled() || !room || state.groupAuditMode);
    const memberCounts = document.getElementById("kwc-group-member-counts");
    if (memberCounts) {
      memberCounts.onclick = event => { event.preventDefault(); event.stopPropagation(); openGroupManagePanel(); };
      memberCounts.onkeydown = event => {
        if (!event || (event.key !== "Enter" && event.key !== " ")) return;
        event.preventDefault(); event.stopPropagation(); openGroupManagePanel();
      };
    }
    const leave = document.getElementById("kwc-group-leave");
    if (leave) leave.onclick = event => { event.preventDefault(); event.stopPropagation(); leaveGroupRoom(); };
    renderGroupPinnedBar();
    updateGroupChatComposeControls();
    installGroupHeaderLayout(title);
    syncPrivateConversationWindowTitle("group");
  }


  function conversationArchiveMessageBox(type) {
    if (type === "dm") return document.getElementById("kwc-dm-messages");
    if (type === "group") return document.getElementById("kwc-group-messages");
    return document.getElementById("kwc-messages");
  }

  function conversationArchiveMessages(type) {
    if (type === "dm") return Array.isArray(state.dmMessages) ? state.dmMessages : [];
    if (type === "group") return Array.isArray(state.groupMessages) ? state.groupMessages : [];
    return Array.isArray(state.messages) ? state.messages : [];
  }

  function conversationArchiveElementId(el, type) {
    if (!el) return "";
    if (type === "dm") return String(el.dataset.dmMessageId || "");
    if (type === "group") return String(el.dataset.groupMessageId || "");
    return String(el.dataset.id || "");
  }

  function conversationArchiveElementSelector(type) {
    if (type === "dm") return ".kwc-msg[data-dm-message-id]";
    if (type === "group") return ".kwc-msg[data-group-message-id]";
    return ".kwc-msg[data-id]";
  }

  function cancelConversationArchiveSelection() {
    const sel = state.archiveSelection;
    const box = sel ? conversationArchiveMessageBox(sel.type) : null;
    if (box && state.archiveSelectionHandler) box.removeEventListener("click", state.archiveSelectionHandler, true);
    if (box) {
      box.classList.remove("kwc-archive-selecting");
      box.querySelectorAll(".kwc-archive-range-start,.kwc-archive-range-end,.kwc-archive-range-selected").forEach(el => el.classList.remove("kwc-archive-range-start", "kwc-archive-range-end", "kwc-archive-range-selected"));
    }
    if (state.archiveSelectionBanner && state.archiveSelectionBanner.isConnected) state.archiveSelectionBanner.remove();
    state.archiveSelection = null;
    state.archiveSelectionHandler = null;
    state.archiveSelectionBanner = null;
  }

  function archiveSelectionIndex(messages, id) {
    return messages.findIndex(msg => String(msg && msg.id || "") === String(id || ""));
  }

  function updateConversationArchiveSelectionHighlights() {
    const sel = state.archiveSelection;
    if (!sel) return;
    const box = conversationArchiveMessageBox(sel.type);
    if (!box) return;
    const messages = conversationArchiveMessages(sel.type);
    const a = archiveSelectionIndex(messages, sel.firstId);
    const b = archiveSelectionIndex(messages, sel.lastId);
    box.querySelectorAll(conversationArchiveElementSelector(sel.type)).forEach(el => {
      el.classList.remove("kwc-archive-range-start", "kwc-archive-range-end", "kwc-archive-range-selected");
      const id = conversationArchiveElementId(el, sel.type);
      const idx = archiveSelectionIndex(messages, id);
      if (id && id === sel.firstId) el.classList.add("kwc-archive-range-start");
      if (id && id === sel.lastId) el.classList.add("kwc-archive-range-end");
      if (a >= 0 && b >= 0 && idx >= Math.min(a,b) && idx <= Math.max(a,b)) el.classList.add("kwc-archive-range-selected");
    });
  }

  function updateConversationArchiveSelectionBanner() {
    const sel = state.archiveSelection;
    const banner = state.archiveSelectionBanner;
    if (!sel || !banner) return;
    const text = banner.querySelector(".kwc-archive-selection-text");
    if (text) text.textContent = !sel.firstId
      ? t("archive.selectFirst", "Select the first message to save.")
      : (!sel.lastId ? t("archive.selectLast", "Select the last message to save.") : t("archive.rangeReady", "Conversation range selected."));
  }

  function beginConversationArchiveSelection(type, sourceId, title) {
    if (!state.conversationArchiveEnabled || !state.token) return;
    cancelConversationArchiveSelection();
    const box = conversationArchiveMessageBox(type);
    if (!box) return;
    const banner = document.createElement("div");
    banner.className = "kwc-archive-selection-banner";
    banner.innerHTML = `<span class="kwc-archive-selection-text"></span><button type="button" class="kwc-button kwc-archive-selection-cancel">${esc(t("button.cancel", "Cancel"))}</button>`;
    box.parentNode.insertBefore(banner, box);
    state.archiveSelection = {type, sourceId: String(sourceId || (type === "public" ? "public" : "")), title: String(title || ""), firstId: "", lastId: ""};
    state.archiveSelectionBanner = banner;
    box.classList.add("kwc-archive-selecting");
    banner.querySelector(".kwc-archive-selection-cancel").onclick = cancelConversationArchiveSelection;
    const handler = event => {
      const current = state.archiveSelection;
      if (!current || current.type !== type) return;
      const target = event.target && event.target.closest ? event.target.closest(conversationArchiveElementSelector(type)) : null;
      if (!target || !box.contains(target)) return;
      const id = conversationArchiveElementId(target, type);
      if (!id) return;
      event.preventDefault(); event.stopPropagation();
      if (!current.firstId) {
        current.firstId = id;
        updateConversationArchiveSelectionBanner();
        updateConversationArchiveSelectionHighlights();
        return;
      }
      if (!current.lastId) {
        current.lastId = id;
        updateConversationArchiveSelectionBanner();
        updateConversationArchiveSelectionHighlights();
        openConversationArchiveRangeConfirm();
      }
    };
    state.archiveSelectionHandler = handler;
    box.addEventListener("click", handler, true);
    updateConversationArchiveSelectionBanner();
  }

  function archiveSelectionRangeSummary(sel) {
    const messages = conversationArchiveMessages(sel.type);
    const a = archiveSelectionIndex(messages, sel.firstId), b = archiveSelectionIndex(messages, sel.lastId);
    const range = a >= 0 && b >= 0 ? messages.slice(Math.min(a,b), Math.max(a,b) + 1) : [];
    const first = range.length ? Number(range[0].time || 0) : 0;
    const last = range.length ? Number(range[range.length - 1].time || 0) : 0;
    const time = first > 0 && last > 0 ? `${archiveMetadataTime(first)} ~ ${archiveMetadataTime(last)}` : "";
    return {count: range.length, time};
  }

  function openConversationArchiveRangeConfirm() {
    if (!state.conversationArchiveEnabled) return;
    const sel = state.archiveSelection;
    if (!sel || !sel.firstId || !sel.lastId) return;
    const info = archiveSelectionRangeSummary(sel);
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `<div class="kwc-modal"><h3>${esc(t("archive.saveConversation", "Save conversation"))}</h3><label><span>${esc(t("archive.title", "Title"))}</span><input class="kwc-input" id="kwc-archive-title" maxlength="160" value="${esc(sel.title || t("archive.defaultTitle", "Saved conversation"))}"></label><p class="kwc-admin-meta-note">${esc(fmt("archive.rangeSummary", "{count} messages · {time}", {count: info.count || "?", time: info.time || ""}))}</p><div class="kwc-row"><button class="kwc-button" id="kwc-archive-save">${esc(t("button.save", "Save"))}</button><button class="kwc-button" id="kwc-archive-cancel">${esc(t("button.cancel", "Cancel"))}</button></div></div>`;
    if (sel.type === "dm" || sel.type === "group") mountPrivateWindowOwnedOverlay(sel.type, wrap);
    else mountWindowOwnedOverlay(wrap, publicChatWindowOwner());
    const close = () => wrap.remove();
    wrap.querySelector("#kwc-archive-cancel").onclick = () => { close(); cancelConversationArchiveSelection(); };
    wrap.addEventListener("click", e => { if (e.target === wrap) { close(); cancelConversationArchiveSelection(); } });
    wrap.querySelector("#kwc-archive-save").onclick = async () => {
      const button = wrap.querySelector("#kwc-archive-save");
      button.disabled = true;
      try {
        const title = String(wrap.querySelector("#kwc-archive-title").value || "").trim();
        await api("/archive/save", {method: "POST", body: JSON.stringify({sourceType: sel.type, sourceId: sel.sourceId, firstId: sel.firstId, lastId: sel.lastId, title})});
        close(); cancelConversationArchiveSelection();
        alert(t("archive.saved", "Conversation saved."));
      } catch (e) {
        button.disabled = false;
        const response = e.response || {error: e.message || "error"};
        if (response.error === "archive_locked_by_admin") alert(t("archive.adminLocked", "This conversation cannot be saved because it is locked by an administrator."));
        else alertResponse("archive.saveFailed", "Could not save conversation: {error}", response);
      }
    };
  }

  function archiveSourceLabel(type) {
    if (type === "dm") return t("archive.typeDm", "DM");
    if (type === "group") return t("archive.typeGroup", "Group");
    return t("archive.typePublic", "Public chat");
  }

  function archiveMetadataTime(value) {
    const n = Number(value || 0);
    if (!(n > 0)) return "";
    const d = new Date(n);
    const opts = timeFormatOptions({year: "numeric", month: "long", day: "numeric", weekday: "long", hour: "2-digit", minute: "2-digit"});
    try { return d.toLocaleString(selectedLocale(), opts); }
    catch (_) { return d.toLocaleString(undefined, {year: "numeric", month: "long", day: "numeric", weekday: "long", hour: "2-digit", minute: "2-digit"}); }
  }

  function archiveDateKey(value) {
    const d = new Date(Number(value || 0));
    if (!Number.isFinite(d.getTime())) return "";
    try { return new Intl.DateTimeFormat("en-CA", timeFormatOptions({year: "numeric", month: "2-digit", day: "2-digit"})).format(d); }
    catch (_) { return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`; }
  }

  function archiveDateLabel(value) {
    const d = new Date(Number(value || 0));
    if (!Number.isFinite(d.getTime())) return "";
    const opts = timeFormatOptions({year: "numeric", month: "long", day: "numeric", weekday: "long"});
    try { return d.toLocaleDateString(selectedLocale(), opts); }
    catch (_) { return d.toLocaleDateString(undefined, {year: "numeric", month: "long", day: "numeric", weekday: "long"}); }
  }

  async function openConversationArchiveLibrary() {
    if (!state.conversationArchiveEnabled || !state.token || state.archiveModalOpen) return;
    state.archiveModalOpen = true;
    let res;
    try { res = await api("/archive/list"); }
    catch (e) { state.archiveModalOpen = false; alertResponse("archive.loadFailed", "Could not load saved conversations: {error}", e.response || {error: e.message || "error"}); return; }
    const archives = Array.isArray(res.archives) ? res.archives : [];
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    const rows = archives.map(item => `<div class="kwc-archive-row" data-archive-row="${esc(item.id || "")}"><div class="kwc-archive-row-main"><div class="kwc-archive-row-title">${esc(item.title || t("archive.defaultTitle", "Saved conversation"))}</div><div class="kwc-archive-row-meta">${esc(archiveSourceLabel(item.sourceType))} · ${esc(String(item.messageCount || 0))} · ${esc(archiveMetadataTime(item.createdAt))}</div></div><div class="kwc-archive-row-actions"><button class="kwc-button" data-archive-open="${esc(item.id || "")}">${esc(t("button.open", "Open"))}</button><button class="kwc-button" data-archive-rename="${esc(item.id || "")}">${esc(t("button.rename", "Rename"))}</button><button class="kwc-button" data-archive-delete="${esc(item.id || "")}">${esc(t("button.delete", "Delete"))}</button></div></div>`).join("") || `<div class="kwc-dm-empty">${esc(t("archive.empty", "No saved conversations."))}</div>`;
    wrap.innerHTML = `<div class="kwc-modal kwc-archive-library-modal"><h3>${esc(t("archive.library", "Saved conversations"))}</h3><div class="kwc-row"><button class="kwc-button" id="kwc-archive-save-public">${esc(t("archive.savePublic", "Save public chat"))}</button></div><div class="kwc-archive-list">${rows}</div><div class="kwc-row"><button class="kwc-button" id="kwc-archive-library-close">${esc(t("button.close", "Close"))}</button></div></div>`;
    document.body.appendChild(wrap);
    const close = () => { if (wrap.isConnected) wrap.remove(); state.archiveModalOpen = false; };
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });
    wrap.querySelector("#kwc-archive-library-close").onclick = close;
    wrap.querySelector("#kwc-archive-save-public").onclick = () => { close(); beginConversationArchiveSelection("public", "public", t("title.full", "KOKOTO WebChat")); };
    wrap.querySelectorAll("[data-archive-open]").forEach(btn => btn.onclick = () => openConversationArchive(btn.dataset.archiveOpen || ""));
    wrap.querySelectorAll("[data-archive-rename]").forEach(btn => btn.onclick = async () => {
      const id = btn.dataset.archiveRename || "";
      const item = archives.find(a => String(a.id || "") === id);
      const title = prompt(t("archive.renamePrompt", "New saved-conversation title"), item ? String(item.title || "") : "");
      if (title == null || !String(title).trim()) return;
      try { await api("/archive/rename", {method: "POST", body: JSON.stringify({id, title: String(title).trim()})}); close(); openConversationArchiveLibrary(); }
      catch (e) { alertResponse("archive.renameFailed", "Could not rename saved conversation: {error}", e.response || {error: e.message || "error"}); }
    });
    wrap.querySelectorAll("[data-archive-delete]").forEach(btn => btn.onclick = async () => {
      const id = btn.dataset.archiveDelete || "";
      if (!confirmPlain(t("archive.confirmDelete", "Delete this saved conversation?"))) return;
      try { await api("/archive/delete", {method: "POST", body: JSON.stringify({id})}); close(); openConversationArchiveLibrary(); }
      catch (e) { alertResponse("archive.deleteFailed", "Could not delete saved conversation: {error}", e.response || {error: e.message || "error"}); }
    });
  }

  function archiveIdentityHtml(msg) {
    const display = plainMinecraftName(msg && (msg.senderDisplayName || msg.senderUsername) || "") || t("sender.unknown", "Unknown");
    const real = plainMinecraftName(msg && msg.senderUsername || "");
    const server = String(msg && (msg.serverName || msg.serverId) || "").trim();
    // Saved/PDF conversations are static, so there is no sender-identity display toggle.
    // Always preserve both the display name and the real account name, even when
    // they currently happen to be identical.
    const realLabel = real || display;
    const realPart = realLabel ? ` <small>(${esc(realLabel)})</small>` : "";
    return `${server ? `<span class="kwc-archive-server-badge">[${esc(server)}]</span>` : ""}<span class="kwc-archive-identity">${esc(display)}${realPart}</span>`;
  }

  function archiveReplyHtml(msg, archive) {
    if (!msg || (!msg.replyToId && !msg.replyToPreview && !msg.replyToSender)) return "";
    const replyId = String(msg.replyToId || "");
    const target = replyId && archive && Array.isArray(archive.messages)
      ? archive.messages.find(item => String(item && item.id || "") === replyId)
      : null;
    // If the replied message is part of the saved snapshot, preserve the same
    // static display-name + real-account-name identity used by normal archive
    // messages. For replies outside the selected range, fall back to the reply
    // label captured by the source message.
    const identity = target
      ? archiveIdentityHtml(target)
      : `<strong>${esc(plainMinecraftName(msg.replyToSender || "").trim() || t("reply.reply", "Reply"))}</strong>`;
    return `<div class="kwc-archive-reply"><div class="kwc-archive-reply-sender">${identity}</div>${msg.replyToPreview ? `<div>${esc(msg.replyToPreview)}</div>` : ""}</div>`;
  }

  function archiveReactionHtml(msg) {
    const items = Array.isArray(msg && msg.reactions) ? msg.reactions : [];
    const html = items.filter(item => item && Number(item.count || 0) > 0).map(item => `<span class="kwc-reaction-pill${item.mine ? " kwc-active" : ""}">${reactionValueHtml(String(item.value || ""))}<span class="kwc-reaction-count">${esc(String(item.count || 0))}</span></span>`).join("");
    return html ? `<div class="kwc-archive-reactions">${html}</div>` : "";
  }

  function archiveImageHtml(body, archiveId, messageId) {
    const seen = new Set();
    const urls = parseUrls(body).filter(raw => {
      const key = String(raw || "");
      if (!isImageUrl(key) || seen.has(key)) return false;
      seen.add(key); return true;
    }).slice(0, 12);
    if (!urls.length) return "";
    return urls.map((raw, idx) => {
      const href = safePreviewUrl(normalizeUrl(raw));
      if (!href) return "";
      return `<a class="kwc-image-link" href="${esc(href)}" target="_blank" rel="noopener noreferrer"><img class="kwc-archive-image" src="${esc(href)}" alt="" loading="eager" data-archive-image="${esc(String(archiveId || "") + ":" + String(messageId || "") + ":" + idx)}"></a><div class="kwc-archive-attachment-lost kwc-hidden" data-archive-image-lost="${esc(String(archiveId || "") + ":" + String(messageId || "") + ":" + idx)}">${esc(t("archive.originalLost", "Original unavailable"))}</div>`;
    }).join("");
  }

  function hydrateArchiveImages(root) {
    if (!root) return;
    root.querySelectorAll("img[data-archive-image]").forEach(img => {
      const fail = () => {
        const key = img.getAttribute("data-archive-image") || "";
        const lost = root.querySelector(`[data-archive-image-lost="${cssEscape(key)}"]`);
        img.closest("a")?.classList.add("kwc-hidden");
        if (lost) lost.classList.remove("kwc-hidden");
      };
      img.addEventListener("error", fail, {once: true});
      if (img.complete && img.naturalWidth === 0) fail();
    });
  }

  function isLocalArchiveUploadUrl(value) {
    try {
      const url = new URL(String(value || ""), window.location.href);
      if (url.origin !== window.location.origin) return false;
      return /\/uploads\/[^/]+$/i.test(url.pathname || "");
    } catch (_) {
      return false;
    }
  }

  function hydrateArchiveAttachmentLinks(root) {
    if (!root || typeof fetch !== "function") return;
    root.querySelectorAll("a.kwc-link[href]").forEach(link => {
      if (link.dataset.kwcArchiveAttachmentChecked === "1") return;
      const href = link.href || link.getAttribute("href") || "";
      if (!href || isImageUrl(href) || !isLocalArchiveUploadUrl(href)) return;
      link.dataset.kwcArchiveAttachmentChecked = "1";
      fetch(href, {method: "HEAD", cache: "no-store", credentials: "same-origin"}).then(response => {
        if (response && response.ok) return;
        if (!response || (response.status !== 404 && response.status !== 410)) return;
        link.classList.add("kwc-hidden");
        const lost = document.createElement("span");
        lost.className = "kwc-archive-attachment-lost";
        lost.textContent = t("archive.originalLost", "Original unavailable");
        link.insertAdjacentElement("afterend", lost);
      }).catch(() => {
        // A transient network failure is not enough to declare the original lost.
      });
    });
  }

  function hydrateConversationArchiveMedia(root) {
    hydrateArchiveImages(root);
    hydrateArchiveAttachmentLinks(root);
  }

  function archiveMessagesHtml(archive) {
    const messages = Array.isArray(archive && archive.messages) ? archive.messages : [];
    let dayKey = "";
    return messages.map(msg => {
      const key = archiveDateKey(msg.time);
      let sep = "";
      if (key && key !== dayKey) {
        dayKey = key;
        sep = `<div class="kwc-archive-date-separator">${esc(archiveDateLabel(msg.time))}</div>`;
      }
      const membership = archive.sourceType === "group" && (msg.eventType === "member_join" || msg.eventType === "member_leave");
      if (membership) {
        const marker = "__KWC_GROUP_ARCHIVE_IDENTITY__";
        const leaving = msg.eventType === "member_leave";
        const template = fmt(leaving ? "group.memberLeft" : "group.memberJoined", leaving ? "{player} left the room." : "{player} joined the room.", {player: marker});
        const parts = String(template || "").split(marker);
        const identity = archiveIdentityHtml(msg);
        const eventHtml = parts.length >= 2 ? parts.map(esc).join(identity) : esc(groupMembershipEventText(msg));
        return sep + `<div class="kwc-msg kwc-group-message kwc-group-membership-event kwc-archive-message"><span class="kwc-group-membership-event-text">${eventHtml}</span><span class="kwc-group-membership-event-time kwc-time">${esc(formatMessageTime(msg.time))}</span></div>`;
      }
      const mine = !!(state.username && msg.senderUsername && String(state.username).toLowerCase() === String(msg.senderUsername).toLowerCase());
      return sep + `<div class="kwc-msg kwc-dm-message kwc-archive-message${mine ? " kwc-mine" : ""}"><div class="kwc-meta kwc-dm-message-meta">${archiveIdentityHtml(msg)}<span class="kwc-meta-sep" aria-hidden="true">·</span><span class="kwc-time">${esc(formatMessageTime(msg.time))}</span></div>${archiveReplyHtml(msg, archive)}<div class="kwc-text kwc-dm-message-body">${directMessageBodyHtml(msg.body || "")}</div>${archiveImageHtml(msg.body || "", archive.id, msg.id)}${archiveReactionHtml(msg)}</div>`;
    }).join("");
  }

  async function openConversationArchive(id) {
    if (!id) return;
    let res;
    try { res = await api("/archive/get?id=" + encodeURIComponent(id)); }
    catch (e) {
      const response = e.response || {error: e.message || "error"};
      if (response.error === "archive_locked_by_admin") alert(t("archive.adminLockedOpen", "This saved conversation is currently locked by an administrator policy."));
      else alertResponse("archive.loadFailed", "Could not load saved conversation: {error}", response);
      return;
    }
    const archive = res.archive;
    if (!archive) return;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-dm-modal-backdrop kwc-archive-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `<div class="kwc-modal kwc-archive-viewer"><div class="kwc-archive-view-head"><div><div class="kwc-archive-view-title">${esc(archive.title || t("archive.defaultTitle", "Saved conversation"))}</div><div class="kwc-archive-view-meta">${esc(archiveSourceLabel(archive.sourceType))} · ${esc(String(archive.messageCount || 0))} · ${esc(archiveMetadataTime(archive.firstMessageAt))} ~ ${esc(archiveMetadataTime(archive.lastMessageAt))}</div></div><div class="kwc-row"><button class="kwc-button" id="kwc-archive-pdf">${esc(t("archive.exportPdf", "Export PDF"))}</button><button class="kwc-button" id="kwc-archive-view-close">${esc(t("button.close", "Close"))}</button></div></div><div class="kwc-archive-messages">${archiveMessagesHtml(archive)}</div></div>`;
    document.body.appendChild(wrap);
    installSenderIdentityToggle(wrap);
    hydrateConversationArchiveMedia(wrap);
    const close = () => wrap.remove();
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });
    wrap.querySelector("#kwc-archive-view-close").onclick = close;
    wrap.querySelector("#kwc-archive-pdf").onclick = () => exportConversationArchivePdf(archive, wrap);
  }

  function exportConversationArchivePdf(archive, sourceWrap) {
    const printWindow = window.open("", "_blank");
    if (!printWindow) { alert(t("archive.popupBlocked", "The PDF export window was blocked by the browser.")); return; }
    const sourceViewer = sourceWrap.querySelector(".kwc-archive-viewer");
    const content = sourceViewer ? sourceViewer.cloneNode(true) : null;
    if (!content) { try { printWindow.close(); } catch (_) {} return; }
    content.querySelectorAll("button").forEach(btn => btn.remove());
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone || "";
    const detailed = `${archiveSourceLabel(archive.sourceType)} · ${archiveMetadataTime(archive.firstMessageAt)} ~ ${archiveMetadataTime(archive.lastMessageAt)} · ${t("archive.savedAt", "Saved")} ${archiveMetadataTime(archive.createdAt)}${zone ? " · " + zone : ""}`;
    const head = content.querySelector(".kwc-archive-view-head");
    if (head) {
      const meta = head.querySelector(".kwc-archive-view-meta");
      if (meta) meta.textContent = detailed;
    }
    const baseHref = document.baseURI || window.location.href;
    const printCss = `
      @page { size: auto; margin: 14mm 13mm 16mm; }
      * { box-sizing: border-box; }
      html, body { margin: 0; padding: 0; background: #fff !important; color: #171717 !important; }
      body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans KR", "Noto Sans JP", "Noto Sans SC", sans-serif; font-size: 10.5pt; line-height: 1.45; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      .kwc-archive-print { width: 100%; max-width: none; }
      .kwc-archive-viewer { width: 100% !important; max-width: none !important; max-height: none !important; margin: 0 !important; padding: 0 !important; border: 0 !important; border-radius: 0 !important; box-shadow: none !important; background: #fff !important; color: #171717 !important; overflow: visible !important; }
      .kwc-archive-view-head { display: block !important; margin: 0 0 7mm !important; padding: 0 0 4mm !important; border-bottom: 1px solid #bdbdbd; }
      .kwc-archive-view-title { margin: 0 0 2mm; font-size: 18pt; line-height: 1.25; font-weight: 750; overflow-wrap: anywhere; }
      .kwc-archive-view-meta, .kwc-meta, .kwc-time { color: #666 !important; }
      .kwc-archive-view-meta { font-size: 8.5pt; }
      .kwc-archive-messages { display: block !important; width: 100% !important; max-height: none !important; overflow: visible !important; padding: 0 !important; }
      .kwc-archive-date-separator { margin: 5mm 0 3mm; text-align: center; color: #666; font-size: 8.5pt; font-weight: 650; break-after: avoid-page; }
      .kwc-archive-message { display: block !important; width: 100% !important; max-width: none !important; min-width: 0 !important; margin: 0 0 2.5mm !important; padding: 3mm 3.5mm !important; border: 1px solid #dedede !important; border-radius: 2.5mm !important; background: #fff !important; color: #171717 !important; box-shadow: none !important; break-inside: avoid-page; page-break-inside: avoid; overflow: visible !important; }
      .kwc-archive-message.kwc-mine { background: #f5f7fa !important; border-color: #cfd6de !important; }
      .kwc-dm-message-meta { display: flex !important; flex-wrap: wrap; align-items: baseline; gap: 1.2mm; margin: 0 0 1.4mm !important; font-size: 8.7pt; }
      .kwc-meta-sep { opacity: .55; }
      .kwc-archive-server-badge { display: inline-block; margin-right: 1mm; font-weight: 700; color: #555; }
      .kwc-archive-identity { font-weight: 750; color: #111; overflow-wrap: anywhere; }
      .kwc-archive-identity small { font-size: .88em; font-weight: 500; color: #666; }
      .kwc-dm-message-body, .kwc-text { display: block !important; margin: 0 !important; white-space: pre-wrap !important; overflow-wrap: anywhere !important; word-break: break-word !important; color: #171717 !important; font-size: 10.5pt !important; line-height: 1.48 !important; }
      .kwc-token-line { display: block; min-height: 1em; }
      .kwc-token-line-empty { min-height: 1.25em; }
      .kwc-archive-reply { margin: 0 0 2mm !important; padding: 2mm 2.5mm !important; border-left: 3px solid #a7a7a7 !important; border-radius: 1.5mm; background: #f4f4f4 !important; color: #3b3b3b !important; font-size: 9pt; break-inside: avoid-page; }
      .kwc-archive-reply-sender { margin-bottom: .7mm; font-weight: 700; }
      .kwc-link { color: #0b57d0 !important; text-decoration: underline !important; overflow-wrap: anywhere; word-break: break-all; }
      .kwc-image-link { display: block !important; width: 100%; margin: 2.5mm 0 0; text-align: center; text-decoration: none !important; break-inside: avoid-page; page-break-inside: avoid; }
      img.kwc-archive-image { display: inline-block !important; width: auto !important; height: auto !important; max-width: 100% !important; max-height: 165mm !important; margin: 0 auto !important; object-fit: contain !important; border-radius: 2mm; break-inside: avoid-page; page-break-inside: avoid; }
      img.kwc-emoji, img.kwc-custom-emoji, img.kwc-reaction-emoji { display: inline-block !important; width: auto !important; height: 1.25em !important; max-width: 1.6em !important; object-fit: contain; vertical-align: -0.22em; }
      .kwc-archive-attachment-lost { display: block; margin-top: 1.5mm; color: #8a4b00; font-size: 8.5pt; }
      .kwc-archive-reactions { display: flex !important; flex-wrap: wrap; gap: 1.4mm; margin-top: 2mm !important; }
      .kwc-reaction-pill { display: inline-flex !important; align-items: center; gap: 1mm; min-height: 6mm; padding: 1mm 2mm !important; border: 1px solid #cfcfcf !important; border-radius: 999px !important; background: #f7f7f7 !important; color: #222 !important; font: inherit !important; }
      .kwc-reaction-count { font-size: 8.5pt; color: #555; }
      .kwc-group-membership-event { text-align: center; background: #f7f7f7 !important; color: #555 !important; font-size: 9pt; }
      .kwc-group-membership-event-time { margin-left: 2mm; }
      .kwc-hidden, .kwc-reaction-tooltip { display: none !important; }
      .kwc-archive-print-footer { margin-top: 7mm; padding-top: 3mm; border-top: 1px solid #d7d7d7; color: #777; font-size: 8pt; text-align: right; }
      @media print {
        a { color: #0b57d0 !important; }
        .kwc-archive-view-head { break-after: avoid-page; }
      }
    `;
    printWindow.document.open();
    printWindow.document.write(`<!doctype html><html><head><meta charset="utf-8"><base href="${esc(baseHref)}"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(archive.title || "KOKOTO WebChat")}</title><style>${printCss}</style></head><body><main class="kwc-archive-print">${content.outerHTML}<footer class="kwc-archive-print-footer">KOKOTO WebChat · ${esc(archiveMetadataTime(Date.now()))}</footer></main></body></html>`);
    printWindow.document.close();
    try { printWindow.opener = null; } catch (_) {}
    const printNow = async () => {
      try { if (printWindow.document.fonts && printWindow.document.fonts.ready) await printWindow.document.fonts.ready; } catch (_) {}
      const images = Array.from(printWindow.document.querySelectorAll("img"));
      const waits = images.map(img => new Promise(resolve => {
        const done = () => resolve();
        const failed = () => {
          if (img.classList.contains("kwc-archive-image")) {
            const a = img.closest("a"); if (a) a.style.display = "none";
            const lost = a && a.nextElementSibling; if (lost) lost.classList.remove("kwc-hidden");
          }
          resolve();
        };
        if (img.complete) { img.naturalWidth > 0 ? done() : failed(); return; }
        img.addEventListener("load", done, {once:true});
        img.addEventListener("error", failed, {once:true});
      }));
      await Promise.race([Promise.all(waits), new Promise(resolve => setTimeout(resolve, 5000))]);
      try { printWindow.focus(); printWindow.print(); } catch (_) {}
    };
    if (printWindow.document.readyState === "complete") setTimeout(printNow, 150);
    else printWindow.addEventListener("load", () => setTimeout(printNow, 150), {once:true});
  }

