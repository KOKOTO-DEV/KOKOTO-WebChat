// [KWC 유지보수 주석 / KWC maintenance notes]
// 로그인/계정 modal과 DM의 데이터 모델·thread 로드·전송·읽음·검색·삭제 같은 핵심 동작을 담당한다.
// This fragment contains login/account modals and the core DM model: thread loading, send, read state, search, and deletion.
// 5.3.0 DM에는 “나에게만 숨김”이 없다. 사용자는 자기 메시지만 삭제할 수 있고, 상대 메시지는 프런트와 서버 모두 삭제 경로를 제공하지 않는다.
// KWC 5.3.0 has no DM “hide for me”: users may delete only their own messages, and neither frontend nor server exposes a delete path for the other participant’s message.
// 원격 DM 삭제는 대상 서버의 서명된 relay acknowledgement가 먼저 성공해야 로컬 tombstone을 적용해 양쪽 서버 상태가 갈라지는 것을 방지한다.
// Remote DM deletion applies the local tombstone only after the target server acknowledges the signed relay delete, preventing the two servers from diverging.

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
    return `<span class="kwc-admin-meta-identities">${directMessageIdentityHtml(a, "kwc-admin-meta-user")} <span class="kwc-admin-meta-separator">${kwcFaIcon("arrows-left-right")}</span> ${directMessageIdentityHtml(b, "kwc-admin-meta-user")}</span>`;
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

  // 현재 계정의 DM thread 목록과 unread/presence 정보를 다시 읽는다. 강제 refresh는 SSE event나 로그인 전환 후 stale thread 상태를 버릴 때 사용한다.

  // Reloads the current account’s DM threads with unread/presence metadata. Forced refresh is used after SSE events or auth transitions to discard stale thread state.

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
    const profileUuid = String(identity.uuid || "").trim();
    const profileAttrs = profileUuid ? ` data-user-profile-uuid="${esc(profileUuid)}" role="button" tabindex="0"` : "";
    if (identity.real) {
      const title = state.senderIdentityMode === "real" ? senderDisplayTitle(identity.display) : senderOriginalTitle(identity.real);
      return `<span class="kwc-dm-identity kwc-sender-has-real${extra}" title="${esc(title)}" data-kwc-identity-toggle="1" data-display-sender="${esc(identity.display)}" data-real-sender="${esc(identity.real)}" data-source="dm" data-showing-real="${state.senderIdentityMode === "real" ? "1" : "0"}"${profileAttrs}>${senderNameHtml(identity.display, identity.real, "dm")}</span>`;
    }
    return `<span class="kwc-dm-identity${extra}" title="${esc(directMessagePlainLabel(identity.display))}"${profileAttrs}>${directMessageLabelHtml(identity.display)}</span>`;
  }

  function presenceData(item) {
    const p = item && item.presence && typeof item.presence === "object" ? item.presence : {};
    const source = String(p.source || (p.gameOnline ? "game" : p.webOnline ? "web" : "offline")).toLowerCase();
    const statusRaw = String(p.status || (source === "offline" ? "offline" : "online")).toLowerCase();
    const status = statusRaw === "busy" ? "busy" : statusRaw === "offline" ? "offline" : "online";
    return {
      source: source === "game" || source === "web" ? source : "offline",
      status,
      online: p.online === true || p.gameOnline === true || p.webOnline === true,
      gameOnline: p.gameOnline === true,
      webOnline: p.webOnline === true
    };
  }

  function presenceSourceLabel(source) {
    if (source === "game") return t("presence.game", "Game");
    if (source === "web") return t("presence.web", "Web");
    return t("presence.offline", "Offline");
  }

  function presenceStatusLabel(status) {
    if (status === "busy") return t("presence.busy", "Busy");
    if (status === "offline") return t("presence.offline", "Offline");
    return t("presence.online", "Online");
  }

  function presenceCompactHtml(item, uuid = "", clickable = true) {
    if (!item || item.otherRemote === true || item.remote === true) return "";
    const p = presenceData(item);
    const targetUuid = String(uuid || item.otherUuid || item.uuid || "").trim();
    const presenceAttr = targetUuid ? ` data-presence-uuid="${esc(targetUuid)}"` : "";
    const targetLabel = String(item && (item.otherLabel || item.label || item.displayName || item.otherDisplayName || item.name || item.username) || targetUuid);
    const dmAttrs = clickable && targetUuid ? ` data-presence-dm-uuid="${esc(targetUuid)}" data-presence-dm-label="${esc(targetLabel)}" role="button" tabindex="0"` : "";
    const sourceTitle = presenceSourceLabel(p.source);
    const title = p.status === "busy" && p.source !== "offline" ? `${sourceTitle} · ${presenceStatusLabel("busy")}` : sourceTitle;
    const statusClass = p.status === "busy" ? " kwc-presence-busy" : "";
    return `<span class="kwc-presence-compact kwc-presence-${esc(p.source)}${statusClass}"${presenceAttr}${dmAttrs} title="${esc(title)}" aria-label="${esc(title)}"><span class="kwc-presence-dot"></span><span class="kwc-presence-label">${esc(title)}</span></span>`;
  }

  let presenceProfileDelegationInstalled = false;

  function normalizedPersonalBlockId(value) {
    return String(value || "").trim().toLowerCase().replace(/[^a-z0-9._~:-]/g, "");
  }

  function isPersonallyBlockedUuid(uuid) {
    const id = normalizedPersonalBlockId(uuid);
    if (!id) return false;
    return (state.blockedUserUuids || []).includes(id);
  }

  function messagePersonalBlockUuid(message) {
    if (!message || typeof message !== "object") return "";
    return String(message.senderUuid || message.playerUuid || message.uuid || message.actorUuid || "").trim();
  }

  function isPersonallyBlockedMessage(message) {
    return isPersonallyBlockedUuid(messagePersonalBlockUuid(message));
  }

  async function loadBlockedUsers() {
    if (!state.token) {
      state.blockedUsers = [];
      state.blockedUserUuids = [];
      return [];
    }
    try {
      const res = await api("/preferences/blocked-users", {timeoutMs: 8000});
      const items = Array.isArray(res && res.blockedUsers) ? res.blockedUsers : [];
      state.blockedUsers = items;
      state.blockedUserUuids = Array.from(new Set(items.map(item => normalizedPersonalBlockId(item && item.uuid)).filter(Boolean)));
      return items;
    } catch (_) { return state.blockedUsers || []; }
  }

  async function setPersonalUserBlocked(uuid, blocked) {
    const id = String(uuid || "").trim();
    const normalized = normalizedPersonalBlockId(id);
    if (!state.token || !id || !normalized) return false;
    try {
      const res = await api("/preferences/blocked-users", {method:"POST", body:JSON.stringify({uuid:id, blocked:blocked === true})});
      if (!res || res.ok === false) throw new Error(res && res.error || "block_failed");

      // Apply the successful server write to every currently rendered surface immediately.
      // A verification GET still follows, but the user must not need a page reload to see the block.
      const ids = new Set((state.blockedUserUuids || []).map(normalizedPersonalBlockId).filter(Boolean));
      if (blocked === true) ids.add(normalized); else ids.delete(normalized);
      state.blockedUserUuids = Array.from(ids);
      if (blocked === true) {
        if (!(state.blockedUsers || []).some(item => normalizedPersonalBlockId(item && item.uuid) === normalized)) {
          state.blockedUsers = (state.blockedUsers || []).concat([{uuid:id, label:id}]);
        }
      } else {
        state.blockedUsers = (state.blockedUsers || []).filter(item => normalizedPersonalBlockId(item && item.uuid) !== normalized);
      }

      renderVirtualMessages({stickToBottom:false, preserveScroll:true, forcePreservePosition:true, suppressBottomStick:true, deferDuringScroll:false});
      if (state.dmModalOpen) {
        const activeThread = (state.dmThreads || []).find(thread => String(thread && thread.id || "") === String(state.dmActiveThreadId || ""));
        const activeOther = normalizedPersonalBlockId(activeThread && (activeThread.otherUuid || activeThread.otherPlayerUuid));
        if (blocked === true && activeOther === normalized) returnDirectMessageToList();
        renderDirectMessageThreads();
        renderDirectMessageMessages(state.dmMessages || [], {stickToBottom:false});
      }
      if (state.groupModalOpen) renderGroupChatMessages(state.groupMessages || [], {stickToBottom:false});
      hideMentionAutocomplete();
      document.querySelectorAll("#kwc-prefs-blocked-users, #kwc-user-profile-blocked-users").forEach(box => {
        const host = box.closest(".kwc-modal-backdrop") || box.parentElement;
        if (host) renderBlockedUserSettingsList(host);
      });
      loadBlockedUsers().then(() => {
        if (state.dmModalOpen) renderDirectMessageThreads();
        document.querySelectorAll("#kwc-prefs-blocked-users, #kwc-user-profile-blocked-users").forEach(box => {
          const host = box.closest(".kwc-modal-backdrop") || box.parentElement;
          if (host) renderBlockedUserSettingsList(host);
        });
      }).catch(() => {});
      return true;
    } catch (err) {
      alertPlain(fmt("alert.failed", "Failed: {error}", {error:err && err.message || "block_failed"}));
      return false;
    }
  }

  function renderBlockedUserSettingsList(root) {
    const box = root && root.querySelector ? (root.querySelector("#kwc-user-profile-blocked-users") || root.querySelector("#kwc-prefs-blocked-users")) : null;
    if (!box) return;
    const items = Array.isArray(state.blockedUsers) ? state.blockedUsers : [];
    if (!items.length) {
      box.innerHTML = `<div class="kwc-pref-font-help">${esc(t("preferences.blockedUsersEmpty", "No blocked users."))}</div>`;
      return;
    }
    box.innerHTML = items.map(item => {
      const uuid = String(item && item.uuid || "");
      const label = String(item && (item.label || item.displayName || item.username) || uuid);
      return `<div class="kwc-pref-blocked-user"><span>${esc(label)}</span><button type="button" class="kwc-button" data-kwc-unblock-user="${esc(uuid)}">${esc(t("presence.unblockUser", "Unblock"))}</button></div>`;
    }).join("");
    box.querySelectorAll("[data-kwc-unblock-user]").forEach(button => {
      button.onclick = async () => {
        const uuid = String(button.getAttribute("data-kwc-unblock-user") || "");
        button.disabled = true;
        if (await setPersonalUserBlocked(uuid, false)) renderBlockedUserSettingsList(root);
        else button.disabled = false;
      };
    });
  }

  async function openUserPresenceProfile(uuid, ownerType = "public") {
    const targetUuid = String(uuid || "").trim();
    if (!state.token || !targetUuid) return;
    let res;
    try { res = await api("/presence?uuid=" + encodeURIComponent(targetUuid), {timeoutMs: 8000}); }
    catch (_) { return; }
    if (!res || res.ok === false) return;
    const p = presenceData(res);
    const remote = res.remote === true;
    const playerUuid = String(res.playerUuid || res.uuid || "").trim();
    const profileTargetUuid = String(res.uuid || playerUuid || "").trim();
    const selfProfile = !remote && playerUuid && playerUuid.toLowerCase() === String(state.userUuid || "").trim().toLowerCase();
    const card = Object.assign({about:"", avatarMode:"minecraft", avatarUrl:"", minecraftHeadUrl:"", defaultHeadUrl:"", avatarRevision:0, avatarUploadAllowed:true}, res.profile && typeof res.profile === "object" ? res.profile : {});
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    const identity = directMessageIdentityHtml({displayName: res.displayName || res.label || res.username || "", username: res.username || "", uuid: ""}, "kwc-user-profile-name");
    const onlineText = value => value ? t("presence.online", "Online") : t("presence.offline", "Offline");
    const nameModeLabel = () => senderIdentityModeControlLabel();
    const statusControl = selfProfile ? `<label class="kwc-user-profile-status-control"><span>${esc(t("preferences.presenceStatus", "Online status"))}</span><select class="kwc-input" id="kwc-user-profile-presence-status"><option value="online"${p.status === "online" ? " selected" : ""}>${esc(t("presence.online", "Online"))}</option><option value="busy"${p.status === "busy" ? " selected" : ""}>${esc(t("presence.busy", "Busy"))}</option><option value="offline"${p.status === "offline" ? " selected" : ""}>${esc(t("presence.offline", "Offline"))}</option></select></label>` : "";
    const offlineNote = selfProfile ? `<p class="kwc-admin-meta-note${p.status === "offline" ? "" : " kwc-hidden"}" id="kwc-user-profile-offline-note">${esc(t("presence.offlineSelfNote", "You appear Offline to other users. Your own profile still shows your actual Game/Web connection."))}</p>` : "";
    const dmButton = !selfProfile && state.directMessageEnabled && playerUuid ? `<button type="button" class="kwc-button" id="kwc-user-profile-dm">${esc(t("presence.sendDirectMessage", "Send DM"))}</button>` : "";
    const blockButton = !selfProfile && profileTargetUuid ? `<button type="button" class="kwc-button kwc-user-profile-block" id="kwc-user-profile-block">${esc(res.blockedByMe === true || isPersonallyBlockedUuid(profileTargetUuid) ? t("presence.unblockUser", "Unblock") : t("presence.blockUser", "Block"))}</button>` : "";
    const initial = String(res.displayName || res.username || "?").trim().slice(0, 1).toUpperCase() || "?";
    const avatarModeOptions = selfProfile ? `<label class="kwc-user-profile-avatar-mode"><span>${esc(t("presence.profileImage", "Profile image"))}</span><select class="kwc-input" id="kwc-user-profile-avatar-mode"><option value="minecraft"${card.avatarMode !== "custom" ? " selected" : ""}>${esc(t("presence.profileImageMinecraft", "Minecraft Head"))}</option><option value="custom"${card.avatarMode === "custom" ? " selected" : ""}>${esc(t("presence.profileImageCustom", "Custom image"))}</option></select></label>` : "";
    const aboutHtml = selfProfile
      ? `<label class="kwc-user-profile-about-edit"><span>${esc(t("presence.about", "About / status message"))}</span><textarea class="kwc-input" id="kwc-user-profile-about" maxlength="280" rows="3" placeholder="${esc(t("presence.aboutPlaceholder", "Write a short introduction or status message."))}">${esc(String(card.about || ""))}</textarea></label>`
      : `<div class="kwc-user-profile-about"><span>${esc(t("presence.about", "About / status message"))}</span><p>${esc(String(card.about || "").trim() || t("presence.aboutEmpty", "No introduction or status message."))}</p></div>`;
    const avatarActions = selfProfile ? `<div class="kwc-row kwc-user-profile-avatar-actions"><button type="button" class="kwc-button" id="kwc-user-profile-avatar-upload"${card.avatarUploadAllowed === false ? " disabled" : ""}>${esc(t("presence.uploadProfileImage", "Upload image"))}</button><button type="button" class="kwc-button" id="kwc-user-profile-avatar-delete">${esc(t("presence.deleteProfileImage", "Delete image"))}</button><input type="file" id="kwc-user-profile-avatar-file" accept="image/png,image/jpeg,image/webp" hidden></div>` : "";
    const saveProfileButton = selfProfile ? `<button type="button" class="kwc-button" id="kwc-user-profile-card-save">${esc(t("presence.saveProfile", "Save profile"))}</button>` : "";
    let role = String(res.role || (selfProfile ? state.role : "")).toUpperCase();
    const roleText = value => value === "ADMIN" ? t("presence.roleAdmin", "Admin") : value === "MODERATOR" ? t("presence.roleModerator", "Moderator") : value === "USER" ? t("presence.roleUser", "User") : value;
    const roleDisplay = role ? `<div class="kwc-user-profile-role"><span>${esc(t("account.role", "Role"))}</span><strong id="kwc-user-profile-role-label">${esc(roleText(role))}</strong></div>` : "";
    const roleControl = !selfProfile && res.viewerCanChangeRole === true && playerUuid ? `<label class="kwc-user-profile-role-control"><span>${esc(t("presence.roleChange", "Change role"))}</span><select class="kwc-input" id="kwc-user-profile-role-select"><option value="USER"${role === "USER" ? " selected" : ""}>${esc(t("presence.roleUser", "User"))}</option><option value="MODERATOR"${role === "MODERATOR" ? " selected" : ""}>${esc(t("presence.roleModerator", "Moderator"))}</option><option value="ADMIN"${role === "ADMIN" ? " selected" : ""}>${esc(t("presence.roleAdmin", "Admin"))}</option></select></label>` : "";
    const restrictions = res.restrictions && typeof res.restrictions === "object" ? res.restrictions : {};
    const moderationControls = !selfProfile && playerUuid && (res.viewerCanRestrict === true || res.viewerCanDeleteAvatar === true) ? `<section class="kwc-user-profile-management"><strong>${esc(t("presence.userManagement", "User management"))}</strong>${res.viewerCanRestrict === true ? `<label><input type="checkbox" id="kwc-user-profile-chat-ban"${restrictions.chatBanned === true ? " checked" : ""}> ${esc(t("admin.chatBan", "Chat ban"))}</label><label><input type="checkbox" id="kwc-user-profile-upload-ban"${restrictions.uploadBanned === true ? " checked" : ""}> ${esc(t("admin.uploadBan", "Upload ban"))}</label><button type="button" class="kwc-button" id="kwc-user-profile-restrictions-save">${esc(t("button.save", "Save"))}</button>` : ""}${res.viewerCanDeleteAvatar === true && String(card.avatarMode || "") === "custom" ? `<button type="button" class="kwc-button" id="kwc-user-profile-admin-avatar-delete">${esc(t("admin.deleteProfileImage", "Delete profile image"))}</button>` : ""}</section>` : "";
    const blockedUsersSection = selfProfile ? `<section class="kwc-user-profile-blocked-section"><strong>${esc(t("preferences.blockedUsers", "Blocked users"))}</strong><div id="kwc-user-profile-blocked-users" class="kwc-pref-blocked-users"></div></section>` : "";
    wrap.innerHTML = `<div class="kwc-modal kwc-user-profile-modal"><div class="kwc-modal-head"><h3>${esc(t("presence.profileTitle", "User profile"))}</h3><button class="kwc-button" id="kwc-user-profile-close">${esc(t("button.close", "Close"))}</button></div><div class="kwc-user-profile-top"><div class="kwc-user-profile-avatar" id="kwc-user-profile-avatar"><span class="kwc-user-profile-avatar-placeholder">${esc(initial)}</span><img class="kwc-hidden" alt=""></div><div class="kwc-user-profile-top-main"><div class="kwc-user-profile-identity-row"><div class="kwc-user-profile-identity">${identity}</div>${blockButton}</div><div class="kwc-user-profile-name-mode"><button type="button" class="kwc-button" id="kwc-user-profile-name-toggle" data-kwc-sender-identity-mode-control="1" data-identity-mode="${esc(state.senderIdentityMode)}" aria-pressed="${state.senderIdentityMode === "real" ? "true" : "false"}">${esc(nameModeLabel())}</button></div></div></div>${roleDisplay}${roleControl}${avatarModeOptions}${avatarActions}${aboutHtml}${statusControl}<div class="kwc-user-profile-presence"><div${!selfProfile && state.directMessageEnabled && playerUuid ? ` data-presence-dm-uuid="${esc(playerUuid)}" data-presence-dm-label="${esc(res.label || res.displayName || res.username || playerUuid)}" data-presence-dm-remote="${remote ? "1" : "0"}" data-presence-dm-server-id="${esc(String(res.serverId || ""))}" data-presence-dm-server-name="${esc(String(res.serverName || ""))}" role="button" tabindex="0"` : ""}><span>${esc(t("presence.game", "Game"))}</span><strong>${esc(onlineText(p.gameOnline))}</strong></div><div${!selfProfile && state.directMessageEnabled && playerUuid ? ` data-presence-dm-uuid="${esc(playerUuid)}" data-presence-dm-label="${esc(res.label || res.displayName || res.username || playerUuid)}" data-presence-dm-remote="${remote ? "1" : "0"}" data-presence-dm-server-id="${esc(String(res.serverId || ""))}" data-presence-dm-server-name="${esc(String(res.serverName || ""))}" role="button" tabindex="0"` : ""}><span>${esc(t("presence.web", "Web"))}</span><strong>${esc(onlineText(p.webOnline))}</strong></div><div><span>${esc(t("presence.visibleStatus", "Visible status"))}</span><strong id="kwc-user-profile-visible-status">${esc(presenceStatusLabel(p.status))}</strong></div></div>${offlineNote}${moderationControls}${blockedUsersSection}<div class="kwc-row">${saveProfileButton}${dmButton}</div></div>`;
    if (ownerType === "global") {
      // Admin/user-list modals live directly under <body>. Mounting the profile under
      // #kwc-root would put it below their backdrop and make the visible card unclickable.
      // A global profile overlay stays inside the KWC iframe but above all KWC modal layers.
      document.body.appendChild(wrap);
      wrap.classList.add("kwc-user-profile-global-overlay");
      wrap.style.zIndex = "2147483000";
    } else {
      mountChatWindowOwnedOverlay(ownerType, wrap);
    }
    // Profiles can be mounted under the public chat root as well as <body>. Install
    // the shared modal drag handler explicitly so MutationObserver timing/stacking
    // never leaves a visible profile card immovable.
    makeModalDraggable(wrap, "");
    syncSenderIdentityModeControls();
    const close = () => { if (wrap.__kwcDragCleanup) wrap.__kwcDragCleanup(); wrap.remove(); };
    wrap.addEventListener("click", event => { if (event.target === wrap) close(); });

    const renderAvatar = () => {
      const holder = wrap.querySelector("#kwc-user-profile-avatar");
      if (!holder) return;
      const img = holder.querySelector("img");
      const placeholder = holder.querySelector(".kwc-user-profile-avatar-placeholder");
      if (!img || !placeholder) return;
      const sources = [];
      if (String(card.avatarMode || "minecraft") === "custom" && String(card.avatarUrl || "")) sources.push(String(card.avatarUrl || ""));
      if (String(card.minecraftHeadUrl || "")) sources.push(String(card.minecraftHeadUrl || ""));
      if (String(card.defaultHeadUrl || "")) sources.push(String(card.defaultHeadUrl || ""));
      let sourceIndex = 0;
      const useNext = () => {
        const src = sources[sourceIndex++];
        if (!src) {
          img.classList.add("kwc-hidden");
          img.removeAttribute("src");
          placeholder.classList.remove("kwc-hidden");
          return;
        }
        img.src = src;
      };
      img.referrerPolicy = "no-referrer";
      img.onload = () => { img.classList.remove("kwc-hidden"); placeholder.classList.add("kwc-hidden"); };
      img.onerror = useNext;
      useNext();
    };
    renderAvatar();

    const nameToggle = wrap.querySelector("#kwc-user-profile-name-toggle");
    if (nameToggle) nameToggle.onclick = event => {
      event.preventDefault();
      event.stopPropagation();
      toggleSenderIdentityMode();
    };
    const statusSelect = wrap.querySelector("#kwc-user-profile-presence-status");
    if (statusSelect) statusSelect.onchange = async () => {
      const requested = String(statusSelect.value || "online");
      statusSelect.disabled = true;
      const ok = await setAccountPresenceStatus(requested);
      const saved = ok ? String(state.presenceStatus || requested) : String(state.presenceStatus || p.status || "online");
      statusSelect.value = saved;
      statusSelect.disabled = false;
      const visible = wrap.querySelector("#kwc-user-profile-visible-status");
      if (visible) visible.textContent = presenceStatusLabel(saved);
      const note = wrap.querySelector("#kwc-user-profile-offline-note");
      if (note) note.classList.toggle("kwc-hidden", saved !== "offline");
      refreshLoggedInCount().catch(() => {});
    };
    const avatarMode = wrap.querySelector("#kwc-user-profile-avatar-mode");
    if (avatarMode) avatarMode.onchange = () => { card.avatarMode = String(avatarMode.value || "minecraft"); renderAvatar(); };
    const avatarFile = wrap.querySelector("#kwc-user-profile-avatar-file");
    const avatarUpload = wrap.querySelector("#kwc-user-profile-avatar-upload");
    if (avatarUpload && avatarFile) {
      avatarUpload.onclick = () => avatarFile.click();
      avatarFile.onchange = async () => {
        const file = avatarFile.files && avatarFile.files[0];
        if (!file) return;
        avatarUpload.disabled = true;
        try {
          const form = new FormData();
          form.append("file", file, file.name || "profile.png");
          const uploaded = await api("/preferences/profile-avatar", {method:"POST", body:form, timeoutMs:30000});
          if (!uploaded || uploaded.ok === false || !uploaded.profile) throw new Error(uploaded && uploaded.error || "profile_avatar_upload_failed");
          Object.assign(card, uploaded.profile);
          if (avatarMode) avatarMode.value = String(card.avatarMode || "custom");
          renderAvatar();
        } catch (err) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:err && err.message || "profile_avatar_upload_failed"})); }
        finally { avatarUpload.disabled = false; avatarFile.value = ""; }
      };
    }
    const avatarDelete = wrap.querySelector("#kwc-user-profile-avatar-delete");
    if (avatarDelete) avatarDelete.onclick = async () => {
      avatarDelete.disabled = true;
      try {
        const deleted = await api("/preferences/profile-avatar", {method:"DELETE", body:JSON.stringify({})});
        if (!deleted || deleted.ok === false || !deleted.profile) throw new Error(deleted && deleted.error || "profile_avatar_delete_failed");
        Object.assign(card, deleted.profile);
        if (avatarMode) avatarMode.value = String(card.avatarMode || "minecraft");
        renderAvatar();
      } catch (err) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:err && err.message || "profile_avatar_delete_failed"})); }
      finally { avatarDelete.disabled = false; }
    };
    const saveCard = wrap.querySelector("#kwc-user-profile-card-save");
    if (saveCard) saveCard.onclick = async () => {
      saveCard.disabled = true;
      const about = wrap.querySelector("#kwc-user-profile-about");
      try {
        const saved = await api("/preferences/profile-card", {method:"POST", body:JSON.stringify({about:about ? about.value : "", avatarMode:avatarMode ? avatarMode.value : String(card.avatarMode || "minecraft")})});
        if (!saved || saved.ok === false || !saved.profile) throw new Error(saved && saved.error || "profile_card_save_failed");
        Object.assign(card, saved.profile);
        if (avatarMode) avatarMode.value = String(card.avatarMode || "minecraft");
        renderAvatar();
      } catch (err) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:err && err.message || "profile_card_save_failed"})); }
      finally { saveCard.disabled = false; }
    };
    const dm = wrap.querySelector("#kwc-user-profile-dm");
    if (dm) dm.onclick = async event => {
      event.preventDefault();
      event.stopPropagation();
      close();
      await openDirectMessageForTarget({
        uuid: playerUuid,
        label: res.label || res.displayName || res.username || playerUuid,
        displayName: res.displayName || "",
        username: res.username || "",
        remote,
        serverId: String(res.serverId || ""),
        serverName: String(res.serverName || "")
      });
    };
    const roleSelect = wrap.querySelector("#kwc-user-profile-role-select");
    if (roleSelect) roleSelect.onchange = async () => {
      const previous = role || "USER";
      const requested = String(roleSelect.value || previous).toUpperCase();
      roleSelect.disabled = true;
      try {
        const changed = await adminWrite("/admin/account-role", {uuid:playerUuid, role:requested});
        if (!changed || changed.ok === false) throw new Error(changed && changed.error || "role_change_failed");
        res.role = String(changed.role || requested).toUpperCase();
        role = res.role;
        const label = wrap.querySelector("#kwc-user-profile-role-label");
        if (label) label.textContent = roleText(role);
        if (role === "ADMIN") {
          wrap.querySelectorAll("#kwc-user-profile-chat-ban, #kwc-user-profile-upload-ban, #kwc-user-profile-restrictions-save").forEach(el => { el.disabled = true; });
        }
      } catch (err) {
        roleSelect.value = String(res.role || previous);
        alertPlain(fmt("alert.failed", "Failed: {error}", {error:err && err.message || "role_change_failed"}));
      } finally { roleSelect.disabled = false; }
    };
    const restrictionsSave = wrap.querySelector("#kwc-user-profile-restrictions-save");
    if (restrictionsSave) restrictionsSave.onclick = async () => {
      restrictionsSave.disabled = true;
      try {
        const changed = await adminWrite("/admin/user-controls", {
          uuid:playerUuid,
          chatBanned:String(!!wrap.querySelector("#kwc-user-profile-chat-ban")?.checked),
          uploadBanned:String(!!wrap.querySelector("#kwc-user-profile-upload-ban")?.checked)
        });
        if (!changed || changed.ok === false) throw new Error(changed && changed.error || "save_failed");
      } catch (err) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:err && err.message || "save_failed"})); }
      finally { restrictionsSave.disabled = false; }
    };
    const adminAvatarDelete = wrap.querySelector("#kwc-user-profile-admin-avatar-delete");
    if (adminAvatarDelete) adminAvatarDelete.onclick = async () => {
      if (!confirmPlain(t("admin.deleteProfileImageConfirm", "Delete this user's custom profile image?"))) return;
      adminAvatarDelete.disabled = true;
      try {
        const deleted = await adminWrite("/admin/profile-avatar/delete", {uuid:playerUuid});
        if (!deleted || deleted.ok === false) throw new Error(deleted && deleted.error || "delete_failed");
        card.avatarMode = "minecraft"; card.avatarUrl = "";
        renderAvatar();
        adminAvatarDelete.remove();
      } catch (err) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:err && err.message || "delete_failed"})); adminAvatarDelete.disabled = false; }
    };
    if (selfProfile) {
      loadBlockedUsers().then(() => renderBlockedUserSettingsList(wrap)).catch(() => renderBlockedUserSettingsList(wrap));
    }
    const block = wrap.querySelector("#kwc-user-profile-block");
    if (block) block.onclick = async event => {
      event.preventDefault();
      event.stopPropagation();
      const currently = isPersonallyBlockedUuid(profileTargetUuid) || res.blockedByMe === true;
      block.disabled = true;
      const ok = await setPersonalUserBlocked(profileTargetUuid, !currently);
      if (ok) {
        res.blockedByMe = !currently;
        block.textContent = !currently ? t("presence.unblockUser", "Unblock") : t("presence.blockUser", "Block");
      }
      block.disabled = false;
    };
    const closeBtn = wrap.querySelector("#kwc-user-profile-close");
    if (closeBtn) closeBtn.onclick = close;
  }

  function installPresenceProfileDelegation() {
    if (presenceProfileDelegationInstalled) return;
    presenceProfileDelegationInstalled = true;
    const handler = event => {
      const raw = event && event.target;
      if (event.type === "keydown" && event.key !== "Enter" && event.key !== " ") return;
      const dmTarget = raw && raw.closest ? raw.closest("[data-presence-dm-uuid]") : null;
      if (dmTarget) {
        event.preventDefault();
        event.stopPropagation();
        if (typeof event.stopImmediatePropagation === "function") event.stopImmediatePropagation();
        const uuid = String(dmTarget.getAttribute("data-presence-dm-uuid") || "").trim();
        const label = String(dmTarget.getAttribute("data-presence-dm-label") || uuid);
        const remote = String(dmTarget.getAttribute("data-presence-dm-remote") || "") === "1";
        const serverId = String(dmTarget.getAttribute("data-presence-dm-server-id") || "").trim();
        const serverName = String(dmTarget.getAttribute("data-presence-dm-server-name") || serverId).trim();
        const profileModal = dmTarget.closest && dmTarget.closest(".kwc-user-profile-modal");
        if (profileModal) {
          const backdrop = profileModal.closest(".kwc-modal-backdrop");
          if (backdrop) { if (backdrop.__kwcDragCleanup) backdrop.__kwcDragCleanup(); backdrop.remove(); }
        }
        if (uuid) openDirectMessageForTarget({uuid, label, displayName:label, username:"", remote, serverId:remote ? serverId : "", serverName:remote ? serverName : ""});
        return;
      }
      const target = raw && raw.closest ? raw.closest("[data-user-profile-uuid]") : null;
      if (!target) return;
      event.preventDefault();
      event.stopPropagation();
      if (typeof event.stopImmediatePropagation === "function") event.stopImmediatePropagation();
      let ownerType = chatWindowOwnerTypeForNode(target);
      if (ownerType === "public") {
        const hostModal = target.closest && target.closest(".kwc-modal-backdrop, .kwc-modal-wrap");
        if (hostModal && !hostModal.classList.contains("kwc-window-owned-overlay")) ownerType = "global";
      }
      openUserPresenceProfile(target.getAttribute("data-user-profile-uuid") || "", ownerType);
    };
    document.addEventListener("click", handler, true);
    document.addEventListener("keydown", handler, true);
  }

  async function refreshVisiblePresenceBadge(uuid) {
    const targetUuid = String(uuid || "").trim();
    if (!state.token || !targetUuid) return;
    let res;
    try { res = await api("/presence?uuid=" + encodeURIComponent(targetUuid), {timeoutMs: 6000}); }
    catch (_) { return; }
    if (!res || res.ok === false) return;
    const p = presenceData(res);
    document.querySelectorAll(`[data-presence-uuid="${cssEscapeValue(targetUuid)}"]`).forEach(node => {
      node.classList.remove("kwc-presence-game", "kwc-presence-web", "kwc-presence-offline", "kwc-presence-busy");
      node.classList.add("kwc-presence-" + p.source);
      if (p.status === "busy") node.classList.add("kwc-presence-busy");
      const sourceLabel = presenceSourceLabel(p.source);
      const label = p.status === "busy" && p.source !== "offline" ? `${sourceLabel} · ${presenceStatusLabel("busy")}` : sourceLabel;
      node.title = label;
      node.setAttribute("aria-label", label);
      const text = node.querySelector(".kwc-presence-label");
      if (text) text.textContent = label;
    });
  }

  async function refreshAllVisiblePresenceBadges() {
    if (!state.token) return;
    const uuids = new Set();
    document.querySelectorAll("[data-presence-uuid]").forEach(node => {
      const uuid = String(node.getAttribute("data-presence-uuid") || "").trim();
      if (uuid) uuids.add(uuid);
    });
    await Promise.all(Array.from(uuids).slice(0, 60).map(uuid => refreshVisiblePresenceBadge(uuid)));
  }

  async function refreshLoggedInCount() {
    if (!state.token) { state.loggedInCount = 0; updateLoginState(); return; }
    try {
      const res = await api("/presence/summary", {timeoutMs: 6000});
      state.loggedInCount = Math.max(0, Number(res && res.loggedInCount || 0));
      updateLoginState();
    } catch (_) {}
  }

  async function refreshPresenceSurfaces(targetUuid = "") {
    if (!state.token) return;
    refreshLoggedInCount().catch(() => {});
    if (state.dmModalOpen && !state.dmAuditMode) loadDirectMessageThreads(true).catch(() => {});
    if (state.groupModalOpen && !state.groupAuditMode) {
      loadGroupChatRooms(true).then(() => {
        renderGroupChatHeader();
        refreshAllVisiblePresenceBadges().catch(() => {});
      }).catch(() => {});
    }
    const adminSummary = document.querySelector('#kwc-admin-content[data-panel="summary"]');
    if (adminSummary) renderAdminSummary(adminSummary).catch(() => {});
    if (targetUuid) await refreshVisiblePresenceBadge(targetUuid);
  }

  function startPresenceRefreshTimer() {
    installPresenceProfileDelegation();
    if (state.presenceRefreshTimer) return;
    state.presenceRefreshTimer = setInterval(() => {
      if (!state.token || document.hidden) return;
      refreshPresenceSurfaces().catch(() => {});
    }, 30000);
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
    const parentModal = privateListModal("dm");
    const open = hasDirectMessageConversationOpen();
    const multi = privateMultiWindowSupported() && privateConversationRegistry("dm").size > 0;
    if (parentModal) {
      parentModal.classList.toggle("kwc-dm-thread-mode", open && !multi);
      parentModal.classList.toggle("kwc-private-multi-list", multi);
    }
    const settings = document.getElementById("kwc-dm-settings");
    if (settings) settings.classList.toggle("kwc-hidden", !state.conversationArchiveEnabled || !open || state.dmAuditMode || !state.dmActiveThreadId);
    const search = document.getElementById("kwc-dm-message-search-open");
    if (search) search.classList.toggle("kwc-hidden", !searchEnabled() || !state.dmActiveThreadId || state.dmAuditMode);
    const back = document.getElementById("kwc-dm-back-to-list");
    if (back) back.classList.toggle("kwc-hidden", !open || multi);
    const title = document.getElementById("kwc-dm-title");
    const titleRow = title && title.closest ? title.closest(".kwc-private-title-row") : null;
    if (titleRow) {
      titleRow.classList.remove("kwc-private-title-row-back");
      titleRow.classList.toggle("kwc-private-title-row-audit", state.dmAuditMode === true && !!state.dmAuditThread);
    }
    if (title) {
      title.classList.remove("kwc-dm-title-back");
      title.classList.toggle("kwc-dm-title-audit-layout", state.dmAuditMode === true && !!state.dmAuditThread);
      title.title = "";
      title.setAttribute("aria-label", open ? directMessagePlainLabel(title.dataset.dmPlainTitle || title.textContent || "") : t("dm.selectThread", "Select a thread"));
      title.setAttribute("role", "heading");
      title.tabIndex = -1;
    }
  }

  function openDirectConversationSettingsMenu() {
    if (!state.conversationArchiveEnabled) return;
    const thread = state.dmActiveThreadId ? (state.dmThreads || []).find(item => item.id === state.dmActiveThreadId) : null;
    if (!thread || state.dmAuditMode) return;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `<div class="kwc-modal kwc-conversation-settings-modal"><div class="kwc-modal-head"><h3>${esc(t("group.settings", "Settings"))} · ${directMessageHeaderIdentityHtml(thread, "kwc-dm-title-name")}</h3><button class="kwc-button" id="kwc-conv-close">${esc(t("button.close", "Close"))}</button></div><div class="kwc-account-actions"><button class="kwc-button" id="kwc-conv-save">${esc(t("archive.saveConversation", "Save conversation"))}</button><button class="kwc-button" id="kwc-conv-library">${esc(t("archive.library", "Saved conversations"))}</button></div></div>`;
    mountPrivateWindowOwnedOverlay("dm", wrap);
    installSenderIdentityToggle(wrap);
    const close = () => wrap.remove();
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });
    wrap.querySelector("#kwc-conv-close").onclick = close;
    wrap.querySelector("#kwc-conv-save").onclick = () => { const label = directMessageHeaderPlainLabel(thread, directMessageLabel(thread)); close(); beginConversationArchiveSelection("dm", thread.id, label); };
    wrap.querySelector("#kwc-conv-library").onclick = () => { close(); openConversationArchiveLibrary(); };
  }

  function returnDirectMessageToList() {
    const childKey = privateActiveConversationWindowKey("dm");
    if (privateMultiWindowSupported() && childKey) { closePrivateConversationWindow("dm", childKey); return; }
    if (!hasDirectMessageConversationOpen()) return;
    if (state.dmActiveThreadId && !state.dmAuditMode) saveConversationView("dm", state.dmActiveThreadId);
    clearPrivateReply("dm");
    closeDirectMessageEmojiPanel();
    closeDirectMessagePlayerSearch();
    state.dmActiveThreadId = "";
    setActiveChatView("dm", "");
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
    publishNotificationViewState();
    const list = document.getElementById("kwc-dm-thread-list");
    if (!list) return;
    const threads = (Array.isArray(state.dmThreads) ? state.dmThreads : []).filter(thread => !isPersonallyBlockedUuid(thread && (thread.otherUuid || thread.otherPlayerUuid)));
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
        ${directMessageIdentityHtml(thread, "kwc-dm-thread-name")}${presenceCompactHtml(thread, thread.otherUuid || "")}${badge}
        <span class="kwc-dm-thread-preview" title="${esc(plainLegacyText(thread.lastMessage || ""))}">${directMessageBodyHtml(thread.lastMessage || "")}</span>
      </button>`;
    }).join("");
    const adminTitle = state.privateChatContentAccess
      ? t("admin.privateContentAccess", "Admin DM audit (contents available)")
      : t("admin.privateMetaOnly", "Admin metadata only");
    const adminHtml = adminThreads.length ? `<div class="kwc-admin-meta-title">${kwcFaIcon("shield-halved")} ${esc(adminTitle)}</div>` + adminThreads.map(item => {
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
      return `<div class="kwc-dm-thread kwc-admin-meta-row${openClass}"${openAttrs} title="${esc(openTitle)}"><span class="kwc-dm-thread-name">${kwcFaIcon("shield-halved")} ${directMessageAdminIdentityHtml(item)}</span><span class="kwc-admin-meta-actions"><button type="button" class="kwc-button" data-dm-admin-lock-thread="${esc(item.id || "")}" data-next-locked="${item.locked ? "false" : "true"}" title="${esc(lockTitle)}" aria-label="${esc(lockTitle)}">${esc(lockLabel)}</button><button type="button" class="kwc-button" data-dm-admin-retention-thread="${esc(item.id || "")}" data-next-exempt="${item.retentionExempt ? "false" : "true"}" title="${esc(exemptTitle)}" aria-label="${esc(exemptTitle)}">${esc(exemptLabel)}</button><button type="button" class="kwc-button kwc-admin-meta-danger" data-dm-admin-delete-thread="${esc(item.id || "")}" title="${esc(deleteTitle)}" aria-label="${esc(deleteTitle)}">${esc(t("admin.deleteThread", "Delete"))}</button></span><span class="kwc-dm-thread-preview" title="${esc(meta.replace(/<[^>]*>/g, ""))}">${meta}</span></div>`;
    }).join("") : "";
    const previewHtml = state.privateChatSuperAdmin ? cleanupPreviewHtml(state.dmCleanupPreview, "dm") : "";
    list.innerHTML = userHtml + previewHtml + adminHtml;
    list.querySelectorAll("[data-dm-thread]").forEach(btn => {
      btn.addEventListener("click", async event => {
        if (event && event.target && event.target.closest && event.target.closest(senderIdentitySelector())) return;
        const nextThreadId = btn.dataset.dmThread || "";
        if (privateMultiWindowSupported()) {
          await openPrivateConversationWindow("dm", nextThreadId);
          return;
        }
        if (state.dmActiveThreadId && String(state.dmActiveThreadId) !== String(nextThreadId) && !state.dmAuditMode) saveConversationView("dm", state.dmActiveThreadId);
        state.dmDraftTarget = null;
        state.dmAuditMode = false;
        state.dmAuditThread = null;
        state.dmActiveThreadId = nextThreadId;
        setActiveChatView("dm", nextThreadId);
        clearPrivateReply("dm");
        updateDirectMessageComposeControls();
        renderDirectMessageThreads();
        updateDirectMessageViewMode();
        await loadDirectMessageMessages(state.dmActiveThreadId);
        await restoreChatViewAnchor("dm", state.dmActiveThreadId);
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
      title.innerHTML = `<span class="kwc-private-audit-badge">${kwcFaIcon("shield-halved")} ${esc(t("admin.dmAuditView", "DM audit"))}</span><span class="kwc-dm-audit-identity">${directMessageAdminIdentityHtml(state.dmAuditThread)}</span>`;
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
    syncPrivateConversationWindowTitle("dm");
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
        return `<span class="kwc-read-receipt kwc-read-receipt-dm" title="${esc(label)}" aria-label="${esc(label)}">${kwcFaIcon("check")}</span>`;
      }
      const label = t("receipt.unread", "Unread");
      return `<span class="kwc-read-receipt kwc-read-receipt-dm" title="${esc(label)}" aria-label="${esc(label)}">${esc(label)}</span>`;
    }
    if (type === "group") {
      const count = Math.max(0, Number(msg.unreadMemberCount || 0));
      if (count <= 0) {
        const label = t("receipt.readAll", "Read by everyone");
        return `<span class="kwc-read-receipt kwc-read-receipt-group" title="${esc(label)}" aria-label="${esc(label)}">${kwcFaIcon("check")}</span>`;
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

  function adoptDirectMessageSendResponse(res, clientMessageId) {
    if (res && res.thread && res.thread.id) {
      state.dmActiveThreadId = String(res.thread.id || state.dmActiveThreadId || "");
      setActiveChatView("dm", state.dmActiveThreadId);
      state.dmDraftTarget = null;
      rekeyActivePrivateConversationWindow("dm", state.dmActiveThreadId);
    }
    if (res && res.message && /^\d+$/.test(String(res.message.id || ""))) {
      const index = (state.dmMessages || []).findIndex(msg => String(msg && msg.clientMessageId || "") === String(clientMessageId || ""));
      const persisted = Object.assign({}, res.message, {clientMessageId: String(clientMessageId || res.message.clientMessageId || "")});
      if (index >= 0) state.dmMessages.splice(index, 1, persisted);
      else state.dmMessages = mergePrivateMessagePages(state.dmMessages || [], [persisted]);
      clearTypingIndicatorsFromMessages("dm", state.dmMessages, state.dmActiveThreadId);
      renderDirectMessageMessages(state.dmMessages, {stickToBottom: true});
    } else {
      updateOptimisticDirectMessage(clientMessageId, "delivered", "");
    }
    loadDirectMessageThreads(true).catch(() => {});
    if (state.dmActiveThreadId && !state.dmMessagesLoading) loadDirectMessageMessages(state.dmActiveThreadId).catch(() => {});
  }

  async function sendDirectMessageAttempt(requestBody, clientMessageId) {
    try {
      const res = await api("/dm/send", {method: "POST", body: JSON.stringify(requestBody)});
      adoptDirectMessageSendResponse(res, clientMessageId);
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
    return isAutoFollowBottom(box);
  }

  function privateMessageMetaHtml(msg, mine, type = "dm") {
    const sender = msg.senderDisplayName || msg.senderUsername || msg.senderUuid || "";
    const senderIdentity = {senderDisplayName: sender, senderUsername: msg.senderUsername || "", senderUuid: msg.senderUuid || ""};
    const statusHtml = type === "dm"
      ? (state.dmAuditMode ? "" : privateMessageMetaStatusHtml(msg, mine, "dm"))
      : (state.groupAuditMode ? "" : privateMessageMetaStatusHtml(msg, mine, "group"));
    return `${directMessageIdentityHtml(senderIdentity, "kwc-sender")}<span class="kwc-meta-sep" aria-hidden="true">·</span><span class="kwc-time-actions"><span class="kwc-time" data-time="${esc(msg.time || "")}" title="${esc(timeToggleTitle(msg.time))}" role="button" tabindex="0">${esc(formatMessageTime(msg.time))}</span>${statusHtml}${privateReplyActionHtml(msg, type)}</span>`;
  }

  function privateReplyActionHtml(msg, type = "dm") {
    const rawMessageId = String(msg && msg.id || "");
    const persisted = /^\d+$/.test(rawMessageId);
    const audit = type === "group" ? state.groupAuditMode : state.dmAuditMode;
    if (!persisted || audit) return "";
    const reply = `<button type="button" class="kwc-mini-action kwc-reply-action" data-private-reply-message="${esc(rawMessageId)}" data-private-reply-type="${type}">${esc(t("button.reply", "reply"))}</button>`;
    const react = (!state.reactionCatalog || state.reactionCatalog.enabled !== false)
      ? `<button type="button" class="kwc-mini-action kwc-reaction-action" data-reaction-open="${esc(rawMessageId)}">${esc(t("button.react", "React"))}</button>`
      : "";
    const groupPin = type === "group" && groupCanManage() && state.groupPinsEnabled !== false && ((state.groupActiveRoom || {}).pinsEnabled !== false) && !isGroupMessagePinned(rawMessageId)
      ? `<button type="button" class="kwc-mini-action kwc-group-pin-action" data-group-pin-message="${esc(rawMessageId)}">${esc(t("button.pin", "pin"))}</button>`
      : "";
    return `<span class="kwc-mini-actions">${reply}${react}${groupPin}</span>`;
  }

  function groupMembershipEventText(msg) {
    const player = directMessagePlainLabel(msg && (msg.senderDisplayName || msg.senderUsername || msg.senderUuid) || "");
    if (String(msg && msg.eventType || "") === "member_leave") {
      return fmt("group.memberLeft", "{player} left the room.", {player});
    }
    return fmt("group.memberJoined", "{player} joined the room.", {player});
  }

  function groupMembershipEventHtml(msg) {
    const marker = "__KWC_GROUP_MEMBER_IDENTITY__";
    const leaving = String(msg && msg.eventType || "") === "member_leave";
    const template = fmt(leaving ? "group.memberLeft" : "group.memberJoined", leaving ? "{player} left the room." : "{player} joined the room.", {player: marker});
    const identityHtml = directMessageIdentityHtml({
      senderDisplayName: msg && msg.senderDisplayName || "",
      senderUsername: msg && msg.senderUsername || "",
      senderUuid: msg && msg.senderUuid || ""
    }, "kwc-group-membership-identity");
    const parts = String(template || "").split(marker);
    if (parts.length < 2) return esc(groupMembershipEventText(msg));
    return parts.map(esc).join(identityHtml);
  }

  function messageTimestampMillis(msg) {
    const value = Number(msg && (msg.time ?? msg.createdAt));
    if (!Number.isFinite(value) || value <= 0) return 0;
    return value < 100000000000 ? value * 1000 : value;
  }

  function moderatorCanDeleteMessages() {
    return state.role === "ADMIN" || (state.role === "MODERATOR" && state.adminCapabilities && state.adminCapabilities["message-delete"] === true);
  }

  function selfMessageDeletionAllowed(msg) {
    if (!state.config || state.config.selfMessageDeleteEnabled !== true) return false;
    const minutes = Math.max(0, Math.floor(Number(state.config.selfMessageDeleteWindowMinutes) || 0));
    if (minutes === 0) return true;
    const createdAt = messageTimestampMillis(msg);
    return createdAt > 0 && Date.now() - createdAt <= minutes * 60000;
  }

  function publicMessageIsMine(msg) {
    const myUuid = String(state.userUuid || "").trim().toLowerCase();
    const senderUuid = String(msg && msg.playerUuid || "").trim().toLowerCase();
    if (myUuid && senderUuid) return myUuid === senderUuid;
    return !!(state.username && msg && msg.realSender && String(msg.realSender).toLowerCase() === String(state.username).toLowerCase());
  }

  function groupMessageDeletionAllowed(msg) {
    if (state.groupAuditMode) return false;
    const room = state.groupActiveRoom || (state.groupRooms || []).find(r => String(r.id || "") === String(state.groupActiveRoomId || ""));
    if (!room || room.messageDeleteEnabled === false) return false;
    if (groupCanManage() || moderatorCanDeleteMessages()) return true;
    if (room.memberSelfDeleteEnabled === false) return false;
    return !String(msg && msg.eventType || "") && privateMessageIsMine(msg) && selfMessageDeletionAllowed(msg);
  }

  function privateMessageIsMine(msg) {
    const myUuid = String(state.userUuid || "").trim().toLowerCase();
    const senderUuid = String(msg && msg.senderUuid || "").trim().toLowerCase();
    if (myUuid && senderUuid && myUuid === senderUuid) return true;
    return !!(state.username && msg && msg.senderUsername && String(msg.senderUsername).toLowerCase() === String(state.username).toLowerCase());
  }

  function createPrivateMessageElement(msg, type = "dm") {
    msg._kwcReactionContextType = type === "group" ? "group" : "dm";
    msg._kwcReactionContextId = type === "group" ? String(msg.roomId || state.groupActiveRoomId || "") : String(msg.threadId || state.dmActiveThreadId || "");
    const mine = privateMessageIsMine(msg);
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
      const eventDelete = /^\d+$/.test(rawMessageId) && groupMessageDeletionAllowed(msg) && (groupCanManage() || moderatorCanDeleteMessages()) ? `<button type="button" class="kwc-private-message-delete kwc-group-message-delete" data-group-delete-message="${esc(rawMessageId)}" title="${esc(t("button.delete", "delete"))}" aria-label="${esc(t("button.delete", "delete"))}">${kwcFaIcon("xmark")}</button>` : "";
      el.innerHTML = `<span class="kwc-group-membership-event-text">${groupMembershipEventHtml(msg)}</span><span class="kwc-group-membership-event-time kwc-time" data-time="${esc(msg.time || "")}" title="${esc(timeToggleTitle(msg.time))}" role="button" tabindex="0">${esc(formatMessageTime(msg.time))}</span>${eventDelete}`;
      return el;
    }
    el.className = `kwc-msg kwc-dm-message${type === "group" ? " kwc-group-message" : ""}${mine ? " kwc-mine" : ""}`;
    el.dataset.kwcPrivateMessageKey = privateMessageDomKey(msg, type);
    el.dataset.kwcPrivateBody = body;
    if (type === "group") el.dataset.groupMessageId = rawMessageId;
    else el.dataset.dmMessageId = rawMessageId;
    const persisted = /^\d+$/.test(rawMessageId);
    const deleteLabel = t("button.delete", "delete");
    const deleteButton = type === "group"
      ? (persisted && groupMessageDeletionAllowed(msg) ? `<button type="button" class="kwc-private-message-delete kwc-group-message-delete" data-group-delete-message="${esc(rawMessageId)}" title="${esc(deleteLabel)}" aria-label="${esc(deleteLabel)}">${kwcFaIcon("xmark")}</button>` : "")
      : (!state.dmAuditMode && persisted && (moderatorCanDeleteMessages() || (mine && selfMessageDeletionAllowed(msg))) ? `<button type="button" class="kwc-private-message-delete kwc-dm-message-delete" data-dm-delete-message="${esc(rawMessageId)}" title="${esc(t("dm.deleteOwnMessage", "Delete message"))}" aria-label="${esc(t("dm.deleteOwnMessage", "Delete message"))}">${kwcFaIcon("xmark")}</button>` : "");
    el.dataset.kwcPrivateReplySignature = privateReplySignature(msg);
    el.innerHTML = `<div class="kwc-meta kwc-dm-message-meta">${privateMessageMetaHtml(msg, mine, type)}</div>${deleteButton}${privateReplyReferenceHtml(msg, type)}<div class="kwc-text kwc-dm-message-body">${directMessageBodyHtml(body)}</div>${directMessagePreviewHtml(body, rawMessageId, type)}${reactionBarHtml(msg)}`;
    installReactionHandlers(el, msg);
    return el;
  }

  function syncPrivateMessageDeleteButton(el, msg, type = "dm", mine = privateMessageIsMine(msg)) {
    if (!el || !msg) return;
    const rawMessageId = String(msg.id || "");
    const persisted = /^\d+$/.test(rawMessageId);
    const groupMode = type === "group";
    const shouldShow = groupMode
      ? persisted && groupMessageDeletionAllowed(msg)
      : !state.dmAuditMode && persisted && (moderatorCanDeleteMessages() || (mine && selfMessageDeletionAllowed(msg)));
    const selector = groupMode ? ":scope > .kwc-group-message-delete" : ":scope > .kwc-dm-message-delete";
    const existing = el.querySelector(selector);
    if (!shouldShow) {
      if (existing) existing.remove();
      return;
    }
    const attr = groupMode ? "data-group-delete-message" : "data-dm-delete-message";
    const title = groupMode ? t("button.delete", "delete") : t("dm.deleteOwnMessage", "Delete message");
    if (existing) {
      existing.setAttribute(attr, rawMessageId);
      existing.title = title;
      existing.setAttribute("aria-label", title);
      return;
    }
    const button = document.createElement("button");
    button.type = "button";
    button.className = "kwc-private-message-delete " + (groupMode ? "kwc-group-message-delete" : "kwc-dm-message-delete");
    button.setAttribute(attr, rawMessageId);
    button.title = title;
    button.setAttribute("aria-label", title);
    setKwcFaIcon(button, "xmark");
    const meta = el.querySelector(":scope > .kwc-dm-message-meta");
    if (meta && meta.nextSibling) el.insertBefore(button, meta.nextSibling);
    else if (meta) el.appendChild(button);
    else el.prepend(button);
  }

  function syncPrivateMessageElement(el, msg, type = "dm") {
    if (!el || !msg) return el;
    msg._kwcReactionContextType = type === "group" ? "group" : "dm";
    msg._kwcReactionContextId = type === "group" ? String(msg.roomId || state.groupActiveRoomId || "") : String(msg.threadId || state.dmActiveThreadId || "");
    const eventType = type === "group" ? String(msg.eventType || "") : "";
    if (eventType === "member_join" || eventType === "member_leave") {
      if (String(el.dataset.kwcPrivateEventType || "") !== eventType) {
        const replacement = createPrivateMessageElement(msg, type);
        el.replaceWith(replacement);
        return replacement;
      }
      const rawMessageId = String(msg.id || "");
      const shouldDelete = /^\d+$/.test(rawMessageId) && groupMessageDeletionAllowed(msg) && groupCanManage();
      const hasDelete = !!el.querySelector(":scope > .kwc-group-message-delete");
      if (hasDelete !== shouldDelete) {
        const replacement = createPrivateMessageElement(msg, type);
        el.replaceWith(replacement);
        return replacement;
      }
      const text = el.querySelector(":scope > .kwc-group-membership-event-text");
      if (text) text.innerHTML = groupMembershipEventHtml(msg);
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
    const mine = privateMessageIsMine(msg);
    el.classList.toggle("kwc-mine", mine);
    const rawMessageId = String(msg.id || "");
    if (type === "group") el.dataset.groupMessageId = rawMessageId;
    else el.dataset.dmMessageId = rawMessageId;
    const meta = el.querySelector(":scope > .kwc-dm-message-meta");
    if (meta) meta.innerHTML = privateMessageMetaHtml(msg, mine, type);
    // The DOM node is keyed by clientMessageId so an optimistic message survives
    // when the POST response replaces local-* with the persisted numeric ID.
    // Reconcile the delete button in place as well; otherwise delete permissions
    // only become visible after a full thread/room reload.
    syncPrivateMessageDeleteButton(el, msg, type, mine);
    // Private chat message bodies are immutable after storage. Do not rebuild the
    // body/preview on delivery/read refreshes: a loaded video/audio element must
    // remain mounted in exactly the same message DOM node, like public chat.
    const body = String(msg.body || "");
    if (String(el.dataset.kwcPrivateBody || "") !== body || String(el.dataset.kwcPrivateReplySignature || "") !== privateReplySignature(msg)) {
      const replacement = createPrivateMessageElement(msg, type);
      el.replaceWith(replacement);
      return replacement;
    }
    updateReactionBarElement(el, msg);
    installReactionHandlers(el, msg);
    return el;
  }
