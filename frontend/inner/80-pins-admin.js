// [KWC 유지보수 주석 / KWC maintenance notes]
// 공개 채팅 pinned-message 표시/정렬과 웹 관리자 화면의 메시지·설정 관리 기능을 모은 조각이다.
// This fragment contains public pinned-message display/reordering and web-admin message/configuration management.
// 일반 사용자의 pin 조회와 관리자 pin 변경 권한은 분리되어 있으며, 프런트 버튼 표시 여부와 무관하게 서버가 최종 권한 검사를 수행한다.
// Pin visibility for ordinary users is separate from mutation permission; the server performs final authorization regardless of whether the frontend shows a button.
// 관리자 화면은 대량 데이터를 다룰 수 있으므로 DOM 전체 재생성, 필터 요청 크기, 삭제 후 stale row를 특히 주의한다.
// Admin views can handle large datasets, so full-DOM rerenders, filter request size, and stale rows after deletion require special care.

  function isMessagePinned(messageId) {
    if (!messageId) return false;
    return state.pins.some(pin => pin && pin.messageId === messageId);
  }

  function groupCanManage() {
    const room = state.groupActiveRoom || (state.groupRooms || []).find(r => String(r.id || "") === String(state.groupActiveRoomId || ""));
    return !!room && !state.groupAuditMode && (room.role === "owner" || room.role === "admin");
  }

  function isGroupMessagePinned(messageId) {
    const id = String(messageId || "");
    return !!id && Array.isArray(state.groupPins) && state.groupPins.some(pin => String(pin && pin.messageId || "") === id);
  }

  function pinnedByIdentityParts(pin) {
    pin = pin || {};
    const display = String(pin.pinnedByDisplayName || pin.pinnedBy || "").trim();
    const real = String(pin.pinnedByUsername || "").trim();
    const uuid = String(pin.pinnedByUuid || "").trim();
    return {display: display || real || uuid || "-", real, uuid};
  }

  function pinnedByIdentityHtml(pin) {
    const identity = pinnedByIdentityParts(pin);
    if (!identity.real || plainMinecraftName(identity.real).trim().toLowerCase() === plainMinecraftName(identity.display).trim().toLowerCase()) {
      return `<span class="kwc-pinned-by-user">${minecraftNameHtml(identity.display, shouldRenderMinecraftNameColors())}</span>`;
    }
    const showingReal = state.senderIdentityMode === "real";
    const title = showingReal ? senderDisplayTitle(identity.display) : senderOriginalTitle(identity.real);
    return `<span class="kwc-pinned-by-user kwc-sender-has-real" data-kwc-identity-toggle="pinned-by" data-display-sender="${esc(identity.display)}" data-real-sender="${esc(identity.real)}" data-source="web" data-showing-real="${showingReal ? "1" : "0"}" role="button" tabindex="0" title="${esc(title)}" aria-label="${esc(title)}">${senderNameHtml(identity.display, identity.real, "web")}</span>`;
  }

  function pinnedByDetailHtml(pin) {
    const identityHtml = pinnedByIdentityHtml(pin);
    const marker = "__KWC_PINNED_BY_IDENTITY__";
    let template = String(t("pinned.pinnedBy", "pinned by {user}") || "pinned by {user}");
    if (template.includes("{user}")) template = template.replace("{user}", marker);
    else template += " " + marker;
    return template.split(marker).map(esc).join(identityHtml);
  }

  function groupPinnedTitle(pin) {
    const text = plainLegacyText(String(pin && pin.body || "")).replace(/\s+/g, " ").trim();
    if (text) return text;
    if (pin && (pin.eventType === "member_join" || pin.eventType === "member_leave")) {
      return groupMembershipEventText({eventType: pin.eventType, senderDisplayName: pin.senderDisplayName, senderUsername: pin.senderUsername, senderUuid: pin.senderUuid});
    }
    return t("pinned.untitled", "Pinned message");
  }

  function renderGroupPinnedBar() {
    const bar = document.getElementById("kwc-group-pinned-bar");
    const open = document.getElementById("kwc-group-pinned-open");
    const label = document.getElementById("kwc-group-pinned-label");
    if (!bar || !open || !label) return;
    const pins = Array.isArray(state.groupPins) ? state.groupPins : [];
    const visible = !!state.groupActiveRoomId && !state.groupAuditMode && state.groupPinsEnabled !== false && pins.length > 0;
    bar.classList.toggle("kwc-hidden", !visible);
    if (!visible) { label.textContent = ""; label.title = ""; return; }
    if (pins.length === 1) {
      label.innerHTML = renderCustomEmojiTokens(fmt("pinned.single", "{title}", {title: groupPinnedTitle(pins[0])}), false, true);
      label.title = groupPinnedTitle(pins[0]);
    } else {
      label.textContent = "";
      const titlePart = document.createElement("span");
      titlePart.className = "kwc-pinned-summary-title";
      titlePart.innerHTML = renderCustomEmojiTokens(groupPinnedTitle(pins[0]), false, true);
      const rest = document.createElement("span");
      rest.className = "kwc-pinned-summary-rest";
      rest.textContent = fmt("pinned.more", "and {rest} more", {rest: pins.length - 1});
      label.append(titlePart, rest);
      label.title = fmt("pinned.multiple", "{title} and {rest} more", {title: groupPinnedTitle(pins[0]), rest: pins.length - 1});
    }
    installCustomEmojiImageRecovery(label);
  }

  async function loadGroupPins(roomId = state.groupActiveRoomId) {
    roomId = String(roomId || "").trim();
    if (!state.token || !roomId || state.groupAuditMode) {
      state.groupPins = []; state.groupPinsCanPin = false; renderGroupPinnedBar(); return;
    }
    try {
      const res = await api("/group/pins?roomId=" + encodeURIComponent(roomId), {method: "GET"});
      if (roomId !== String(state.groupActiveRoomId || "")) return;
      state.groupPinsEnabled = res.enabled !== false;
      state.groupPinsCanPin = !!res.canPin;
      state.groupPins = Array.isArray(res.pins) ? res.pins : [];
    } catch (_) {
      if (roomId === String(state.groupActiveRoomId || "")) { state.groupPins = []; state.groupPinsCanPin = false; }
    }
    renderGroupPinnedBar();
  }

  // room-local manager 권한으로 메시지를 고정하고 서버 응답 후 pin 목록을 다시 읽는다. max-pins와 실제 권한 판정은 서버가 최종 결정한다.

  // Pins a message using room-local manager authority and reloads pins after the server response. Max-pin limits and final authorization are enforced by the server.

  async function pinGroupMessage(messageId) {
    const roomId = String(state.groupActiveRoomId || "");
    if (!messageId || !roomId || !state.token || !groupCanManage()) return;
    try {
      await api("/group/pin-message", {method: "POST", body: JSON.stringify({roomId, messageId})});
      await loadGroupPins(roomId);
      renderGroupChatMessages(state.groupMessages, {preserveScroll: true});
    } catch (e) { alertResponse("alert.pinFailed", "Pin failed: {error}", e.response || {error: e.message || "error"}); }
  }

  async function moveGroupPinnedMessage(pinId, direction) {
    const roomId = String(state.groupActiveRoomId || "");
    if (!pinId || !direction || !roomId || !groupCanManage()) return;
    try {
      await api("/group/move-pin", {method: "POST", body: JSON.stringify({roomId, pinId, direction})});
      await loadGroupPins(roomId); refreshOpenGroupPinnedModal();
    } catch (e) { alertResponse("alert.movePinFailed", "Move failed: {error}", e.response || {error: e.message || "error"}); }
  }

  async function unpinGroupMessage(pinId) {
    const roomId = String(state.groupActiveRoomId || "");
    if (!pinId || !roomId || !groupCanManage()) return;
    try {
      await api("/group/unpin-message", {method: "POST", body: JSON.stringify({roomId, pinId})});
      await loadGroupPins(roomId); refreshOpenGroupPinnedModal();
      renderGroupChatMessages(state.groupMessages, {preserveScroll: true});
    } catch (e) { alertResponse("alert.unpinFailed", "Unpin failed: {error}", e.response || {error: e.message || "error"}); }
  }

  function renderGroupPinnedItem(pin, index, total) {
    const el = document.createElement("div");
    el.className = "kwc-pinned-item kwc-group-pinned-item";
    const senderIdentity = directMessageIdentityHtml({senderDisplayName: pin.senderDisplayName || "", senderUsername: pin.senderUsername || "", senderUuid: pin.senderUuid || ""}, "kwc-sender");
    // Keep the pinner on the same global display-name / real-account-name mode as
    // normal chat identities. Old pins without structured identity fields fall back
    // to the legacy pinnedBy snapshot without inventing a real account name.
    const detail = pinnedByDetailHtml(pin);
    const content = (pin.eventType === "member_join" || pin.eventType === "member_leave")
      ? `<div class="kwc-text">${groupMembershipEventHtml({eventType: pin.eventType, senderDisplayName: pin.senderDisplayName, senderUsername: pin.senderUsername, senderUuid: pin.senderUuid})}</div>`
      : `<div class="kwc-text kwc-dm-message-body">${directMessageBodyHtml(String(pin.body || ""))}</div>`;
    const controls = state.groupPinsCanPin && groupCanManage() ? `<span class="kwc-mini-actions kwc-pinned-actions"><button class="kwc-mini-action kwc-pinned-action" data-group-pin-move="${esc(pin.pinId || "")}" data-direction="up" ${index <= 0 ? "disabled" : ""} title="${esc(t("button.moveUp", "Move up"))}">↑</button><button class="kwc-mini-action kwc-pinned-action" data-group-pin-move="${esc(pin.pinId || "")}" data-direction="down" ${index >= total - 1 ? "disabled" : ""} title="${esc(t("button.moveDown", "Move down"))}">↓</button><button class="kwc-mini-action kwc-pinned-action" data-group-unpin="${esc(pin.pinId || "")}">${esc(t("button.unpin", "unpin"))}</button></span>` : "";
    el.innerHTML = `<div class="kwc-meta">${senderIdentity}<span class="kwc-meta-sep" aria-hidden="true">·</span><span class="kwc-time" data-time="${esc(pin.time || "")}">${esc(formatMessageTime(pin.time))}</span><span class="kwc-meta-sep" aria-hidden="true">·</span><span class="kwc-pinned-detail">${detail}</span>${controls}</div>${content}`;
    installCustomEmojiImageRecovery(el);
    installSenderIdentityToggle(el);
    return el;
  }

  function refreshOpenGroupPinnedModal() {
    const list = document.getElementById("kwc-group-pinned-list");
    if (!list) return;
    list.innerHTML = "";
    if (!state.groupPins.length) { list.innerHTML = `<p>${esc(t("pinned.empty", "No pinned messages."))}</p>`; return; }
    state.groupPins.forEach((pin, index) => list.appendChild(renderGroupPinnedItem(pin, index, state.groupPins.length)));
  }

  function openGroupPinnedModal() {
    if (!state.groupActiveRoomId || state.groupAuditMode || !state.groupPins.length) return;
    const old = document.getElementById("kwc-group-pinned-modal"); if (old) old.remove();
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-pinned-backdrop"; applyDetachedModalTheme(wrap); wrap.id = "kwc-group-pinned-modal";
    wrap.innerHTML = `<div class="kwc-modal kwc-pinned-modal"><div class="kwc-modal-head"><h3>${esc(t("pinned.title", "Pinned messages"))}</h3><button class="kwc-button" id="kwc-group-pinned-close">${esc(t("button.close", "Close"))}</button></div><div class="kwc-pinned-list" id="kwc-group-pinned-list"></div></div>`;
    mountPrivateWindowOwnedOverlay("group", wrap); refreshOpenGroupPinnedModal();
    wrap.querySelector("#kwc-group-pinned-close").onclick = () => wrap.remove();
    wrap.addEventListener("click", e => {
      const move = e.target && e.target.closest ? e.target.closest("[data-group-pin-move]") : null;
      if (move && wrap.contains(move)) { e.preventDefault(); e.stopPropagation(); moveGroupPinnedMessage(move.dataset.groupPinMove, move.dataset.direction || ""); return; }
      const unpin = e.target && e.target.closest ? e.target.closest("[data-group-unpin]") : null;
      if (unpin && wrap.contains(unpin)) { e.preventDefault(); e.stopPropagation(); unpinGroupMessage(unpin.dataset.groupUnpin); return; }
      if (e.target === wrap) wrap.remove();
    });
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

  function applyPinnedBarEmojiSizing(opener, label) {
    if (!label) return;
    // A multi-pin summary contains an emoji-rendered first title plus a textual
    // "and N more" suffix. Classify the title span independently so an emoji-only
    // first title can use the collapsed-bar cap without making the suffix disappear.
    label.classList.remove("kwc-emoji-only");
    label.querySelectorAll(".kwc-pinned-summary-title.kwc-emoji-only").forEach(node => node.classList.remove("kwc-emoji-only"));
    const titlePart = label.querySelector(".kwc-pinned-summary-title");
    const emojiTarget = titlePart || label;
    const emojiOnly = customEmojiOnlyElement(emojiTarget);
    if (!emojiOnly) return;
    if (opener) {
      try {
        const style = getComputedStyle(opener);
        const outerHeight = Number(opener.getBoundingClientRect().height || opener.offsetHeight || 0);
        const borderTop = parseFloat(style.borderTopWidth || "0") || 0;
        const borderBottom = parseFloat(style.borderBottomWidth || "0") || 0;
        // Maximum image height stops 2px inside the top and bottom outer edges of
        // the fixed collapsed pinned box. This is a cap only, never an upscale.
        const maxHeight = Math.max(1, Math.floor(outerHeight - borderTop - borderBottom - 4));
        opener.style.setProperty("--kwc-pinned-emoji-max-height", maxHeight + "px");
      } catch (_) {}
    }
    emojiTarget.classList.add("kwc-emoji-only");
  }

  function renderPinnedBar() {
    const root = document.getElementById("kwc-root");
    const bar = document.getElementById("kwc-pinned-bar");
    const opener = document.getElementById("kwc-pinned-open");
    const label = document.getElementById("kwc-pinned-label");
    const search = document.getElementById("kwc-search-open");
    if (!bar || !label) return;
    const count = Array.isArray(state.pins) ? state.pins.length : 0;
    const pinsVisible = canViewPinnedMessages() && state.pinsEnabled !== false && count > 0 && !state.minimized;
    const searchVisible = searchEnabled() && !state.minimized && !guestChatHidden();
    bar.classList.toggle("kwc-hidden", !pinsVisible);
    if (root) root.classList.toggle("kwc-has-pinned-bar", !!pinsVisible);
    if (opener) opener.classList.toggle("kwc-hidden", !pinsVisible);
    if (search) search.classList.toggle("kwc-hidden", !searchVisible);
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
      label.innerHTML = renderCustomEmojiTokens(fmt("pinned.single", "{title}", {title: pinnedTitle(state.pins[0])}), false, true);
      label.title = pinnedTooltip(state.pins[0]);
    } else {
      // Keep the remaining-count suffix visible even when the first pinned title
      // is wider than the bar. Only the title span is allowed to ellipsize.
      label.textContent = "";
      const titlePart = document.createElement("span");
      titlePart.className = "kwc-pinned-summary-title";
      titlePart.innerHTML = renderCustomEmojiTokens(pinnedTitle(state.pins[0]), false, true);
      const restPart = document.createElement("span");
      restPart.className = "kwc-pinned-summary-rest";
      restPart.textContent = fmt("pinned.more", "and {rest} more", {rest: count - 1});
      label.append(titlePart, restPart);
      label.title = fmt("pinned.multiple", "{title} and {rest} more", {title: pinnedTooltip(state.pins[0]), rest: count - 1});
    }
    installCustomEmojiImageRecovery(label);
    applyPinnedBarEmojiSizing(opener, label);
    if (opener) opener.title = label.title || label.textContent || "";
    if (bar) bar.title = label.title || label.textContent || "";
  }

  function renderPinnedItem(pin, index = 0, total = 0) {
    const msg = Object.assign({}, pin, {id: pin.messageId || pin.pinId});
    const el = renderMessageElement(msg);
    el.classList.add("kwc-pinned-item");
    el.querySelectorAll(".kwc-mini-actions, [data-delete], [data-pin], [data-reply], [data-reaction-open]").forEach(node => node.remove());
    el.classList.remove("kwc-has-mini-actions");
    const meta = el.querySelector(".kwc-meta");
    if (meta) {
      const detail = document.createElement("span");
      detail.className = "kwc-pinned-detail";
      detail.innerHTML = pinnedByDetailHtml(pin);
      const detailSep = document.createElement("span");
      detailSep.className = "kwc-meta-sep";
      detailSep.setAttribute("aria-hidden", "true");
      detailSep.textContent = "·";
      meta.appendChild(detailSep);
      meta.appendChild(detail);
      if (state.pinsCanPin && pin.pinId) {
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
    installSenderIdentityToggle(el);
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
    mountWindowOwnedOverlay(wrap, publicChatWindowOwner());
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
    if (!id || !confirmPlain(t("alert.confirmDelete", "Delete this message?"))) return;
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

  async function openAdminModal(initialPanel = "summary") {
    if (!state.token) return;

    const adminPanelAllowed = !state.config || state.config.allowWebAdminPanel !== false;
    const isOperator = adminPanelAllowed && (state.role === "ADMIN" || state.role === "MODERATOR");
    let summary = null;
    if (isOperator) {
      try { summary = await adminApi("/admin/summary"); } catch (_) {}
    }
    state.adminCapabilities = summary && summary.capabilities && typeof summary.capabilities === "object" ? summary.capabilities : {};
    const cap = name => isOperator && (state.role === "ADMIN" || state.adminCapabilities[name] === true);
    const canManageMutes = (!state.config || state.config.moderationEnabled !== false) && cap("guest-mute");
    const canUserControls = cap("user-restrictions") || cap("profile-avatar-delete");
    const canBlockOperations = canManageMutes || canUserControls;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-admin-modal">
        <div class="kwc-modal-head kwc-admin-modal-head">
          <h3 class="kwc-admin-drag-handle">${t("admin.title", "KOKOTO WebChat Admin")}</h3>
          <div class="kwc-modal-head-actions"><button class="kwc-button kwc-hidden" type="button" id="kwc-admin-header-save">${t("button.save", "Save")}</button><button class="kwc-button" type="button" id="kwc-admin-close">${t("button.close", "Close")}</button></div>
        </div>
        <div class="kwc-admin-scroll-area">
        ${isOperator ? `<div class="kwc-admin-nav-groups">
          <div class="kwc-admin-nav-group"><div class="kwc-admin-nav-label">${esc(t("admin.navAdministration", "Administration"))}</div><div class="kwc-tabs">
            <button class="kwc-button kwc-tab" data-panel="summary">${t("admin.summary", "Summary")}</button>
            ${state.role === "ADMIN" ? `<button class="kwc-button kwc-tab" data-panel="settings">${t("admin.settings", "Settings")}</button>` : ""}
            ${state.role === "ADMIN" ? `<button class="kwc-button kwc-tab" data-panel="accounts">${t("admin.accounts", "Accounts / sessions")}</button>` : ""}
            ${state.role === "ADMIN" ? `<button class="kwc-button kwc-tab" data-panel="moderator-permissions">${t("admin.moderatorPermissions", "Moderator permissions")}</button>` : ""}
          </div></div>
          <div class="kwc-admin-nav-group"><div class="kwc-admin-nav-label">${esc(t("admin.navOperations", "Operations"))}</div><div class="kwc-tabs">
            ${canBlockOperations ? `<button class="kwc-button kwc-tab" data-panel="mutes">${t("admin.blocksAndRestrictions", "Blocks / restrictions")}</button>` : ""}
            ${cap("content-filter-manage") ? `<button class="kwc-button kwc-tab" data-panel="filter">${t("admin.filter", "Filter")}</button>` : ""}
            ${cap("emoji-manage") ? `<button class="kwc-button kwc-tab" data-panel="emojis">${t("admin.emojis", "Emojis")}</button>` : ""}
          </div></div>
        </div>` : ""}
        <div id="kwc-admin-content">${t("admin.loading", "Loading...")}</div>
        </div>
      </div>
    `;
    protectHistoryEndNotice("admin-open", 6000);
    document.body.appendChild(wrap);
    makeModalDraggable(wrap, "kwc.localAdminModalPos");
    wrap.querySelector("#kwc-admin-close").onclick = () => { protectHistoryEndNotice("admin-close", 6000); if (wrap.__kwcDragCleanup) wrap.__kwcDragCleanup(); wrap.remove(); scheduleScrollAffordanceRefresh("admin-close"); };
    wrap.querySelectorAll("[data-panel]").forEach(btn => { btn.onclick = () => loadAdminPanel(wrap, btn.dataset.panel); });
    const normalizedInitial = initialPanel === "user-controls" ? "mutes" : initialPanel;
    const allowedInitial = !isOperator ? "online" : state.role === "ADMIN"
      ? normalizedInitial
      : (normalizedInitial === "mutes" && canBlockOperations) || (normalizedInitial === "filter" && cap("content-filter-manage")) || (normalizedInitial === "emojis" && cap("emoji-manage")) ? normalizedInitial : "summary";
    await loadAdminPanel(wrap, allowedInitial);
  }

  async function loadAdminPanel(wrap, panel) {
    const content = wrap.querySelector("#kwc-admin-content");
    const headerSave = wrap.querySelector("#kwc-admin-header-save");
    if (headerSave) { headerSave.classList.add("kwc-hidden"); headerSave.onclick = null; }
    content.dataset.panel = String(panel || "");
    content.textContent = t("admin.loading", "Loading...");
    try {
      if (panel === "online") return renderAdminOnline(content);
      if (panel === "summary") return renderAdminSummary(content);
      if (panel === "mutes" || panel === "user-controls") return renderAdminMutes(content);
      if (panel === "moderator-permissions") return renderAdminModeratorPermissions(content);
      if (panel === "accounts") return renderAdminAccounts(content);
      // Compatibility for any stale in-page state that still references the old Sessions tab.
      if (panel === "sessions") return renderAdminAccounts(content);
      if (panel === "emojis") return renderAdminEmojis(content);
      if (panel === "reactions") return renderAdminReactions(content);
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
    const saveButton = content.querySelector("#kwc-settings-save") || content.querySelector("#kwc-filter-settings-save") || content.closest(".kwc-admin-modal")?.querySelector("#kwc-admin-header-save");
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
      await loadConfig();
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

  function reactionAdminCategoryLabel(id) {
    const map = {
      smileys:["reaction.categorySmileys","Smileys"], people:["reaction.categoryPeople","People"],
      animals:["reaction.categoryAnimals","Animals & nature"], food:["reaction.categoryFood","Food"],
      activities:["reaction.categoryActivities","Activities"], objects:["reaction.categoryObjects","Objects"],
      symbols:["reaction.categorySymbols","Symbols"]
    };
    const row = map[String(id || "")] || ["admin.reactions", String(id || "")];
    return t(row[0], row[1]);
  }

  function reactionAliasEditorText(catalog) {
    const aliases = catalog && catalog.searchAliases && typeof catalog.searchAliases === "object" ? catalog.searchAliases : {};
    const seen = new Set();
    const lines = [];
    const categories = Array.isArray(catalog && catalog.categories) ? catalog.categories : [];
    categories.forEach(row => (Array.isArray(row && row.items) ? row.items : []).forEach(value => {
      const emoji = String(value || "");
      if (!emoji || seen.has(emoji)) return;
      seen.add(emoji);
      const words = String(aliases[emoji] || "").trim();
      if (words) lines.push(`${emoji} = ${words}`);
    }));
    Object.keys(aliases).forEach(emoji => {
      if (seen.has(emoji)) return;
      const words = String(aliases[emoji] || "").trim();
      if (words) lines.push(`${emoji} = ${words}`);
    });
    return lines.join("\n");
  }

  async function renderAdminReactions(content) {
    if (state.role !== "ADMIN" && state.adminCapabilities["emoji-manage"] !== true) return;
    const data = await adminApi("/admin/reactions");
    const catalog = data && data.catalog ? data.catalog : defaultReactionCatalog();
    const categories = Array.isArray(catalog.categories) ? catalog.categories : [];
    content.innerHTML = `<div class="kwc-admin-reaction-heading"><h4>${esc(t("admin.reactionCatalog", "Reaction icons"))}</h4></div>
      <p><small>${esc(t("admin.reactionCatalogHint", "Choose which Unicode icons appear in the reaction picker. Removing an icon prevents new use but does not delete existing reactions."))}</small></p>
      <div class="kwc-admin-section-stack kwc-settings-stack kwc-admin-reaction-settings-stack">
        <section class="kwc-admin-section-card kwc-admin-reaction-options-card"><div class="kwc-admin-list kwc-admin-group-list kwc-admin-reaction-options">
          <label class="kwc-admin-item kwc-admin-setting-item"><span><strong>${esc(t("admin.reactionEnabled", "Enable reaction icons"))}</strong><small>${esc(t("admin.reactionEnabledHint", "Turning this off blocks new reaction activity and hides the add/picker controls without deleting stored reactions."))}</small></span><input type="checkbox" id="kwc-reaction-enabled" ${catalog.enabled !== false ? "checked" : ""}></label>
          <label class="kwc-admin-item kwc-admin-setting-item"><span><strong>${esc(t("admin.reactionCustomEmoji", "Allow KWC custom emoji in reactions"))}</strong></span><input type="checkbox" id="kwc-reaction-custom-enabled" ${catalog.customEmojiEnabled !== false ? "checked" : ""}></label>
          <label class="kwc-admin-item kwc-admin-setting-item"><span><strong>${esc(t("admin.reactionShowActors", "Show who reacted"))}</strong><small>${esc(t("admin.reactionShowActorsHint", "When disabled, reactor display names are not included in reaction responses or shown in tooltips."))}</small></span><input type="checkbox" id="kwc-reaction-show-actors" ${catalog.showActorList !== false ? "checked" : ""}></label>
        </div></section>
      </div>
      <div class="kwc-admin-section-stack kwc-reaction-admin-stack">${categories.map(row => `<section class="kwc-admin-section-card"><div class="kwc-admin-section-title">${esc(reactionAdminCategoryLabel(row.id))}</div><textarea class="kwc-input kwc-reaction-admin-list" data-reaction-admin-category="${esc(row.id || "")}" rows="3">${esc((Array.isArray(row.items) ? row.items : []).join(" "))}</textarea><small>${esc(t("admin.reactionCategoryHint", "Separate emoji with spaces. Order here is the picker order."))}</small></section>`).join("")}<section class="kwc-admin-section-card"><div class="kwc-admin-section-title">${esc(t("admin.reactionSearchAliases", "Search aliases"))}</div><textarea class="kwc-input kwc-reaction-admin-aliases" id="kwc-reaction-search-aliases" rows="10">${esc(reactionAliasEditorText(catalog))}</textarea><small>${esc(t("admin.reactionSearchAliasesHint", "One emoji per line: emoji = search words. Add aliases here when you add a new Unicode reaction icon."))}</small></section></div>
      <div class="kwc-row kwc-admin-save-row"><button class="kwc-button" type="button" id="kwc-reaction-admin-save">${esc(t("button.save", "Save"))}</button><button class="kwc-button" type="button" id="kwc-reaction-admin-reset">${esc(t("button.reset", "Reset"))}</button><small class="kwc-admin-result" id="kwc-reaction-admin-result"></small></div>`;
    const result = content.querySelector("#kwc-reaction-admin-result");
    const applyCatalog = next => {
      if (!next) return;
      state.reactionCatalog = next;
      state.reactionCatalogLoadedAt = Date.now();
      closeReactionPicker();
      refreshVisibleReactionBars();
    };
    const save = content.querySelector("#kwc-reaction-admin-save");
    if (save) save.onclick = async () => {
      const fields = {
        enabled:String(!!content.querySelector("#kwc-reaction-enabled")?.checked),
        customEmojiEnabled:String(!!content.querySelector("#kwc-reaction-custom-enabled")?.checked),
        showActorList:String(!!content.querySelector("#kwc-reaction-show-actors")?.checked),
        searchAliases:String(content.querySelector("#kwc-reaction-search-aliases")?.value || "")
      };
      content.querySelectorAll("[data-reaction-admin-category]").forEach(el => { fields[el.dataset.reactionAdminCategory] = String(el.value || ""); });
      save.disabled = true; if (result) result.textContent = t("admin.settingsSaving", "Saving...");
      try {
        const res = await adminWrite("/admin/reactions", fields);
        if (!res?.ok) throw new Error(res?.error || "save_failed");
        applyCatalog(res.catalog);
        if (result) result.textContent = t("admin.reactionSaved", "Reaction icons saved.");
        await renderAdminReactions(content);
      } catch (e) { if (result) result.textContent = fmt("admin.failed", "Failed: {error}", {error:e.message || "error"}); }
      finally { if (save.isConnected) save.disabled = false; }
    };
    const reset = content.querySelector("#kwc-reaction-admin-reset");
    if (reset) reset.onclick = async () => {
      if (!confirmPlain(t("admin.reactionResetConfirm", "Reset the reaction icon catalog to defaults?"))) return;
      try {
        const res = await adminWrite("/admin/reactions", {action:"reset"});
        if (!res?.ok) throw new Error(res?.error || "reset_failed");
        applyCatalog(res.catalog);
        await renderAdminReactions(content);
      } catch (e) { if (result) result.textContent = fmt("admin.failed", "Failed: {error}", {error:e.message || "error"}); }
    };
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
      adminSettingInput("captcha.mode", v["captcha.mode"], "text", [{value:"off", label:t("admin.optionCaptchaOff", "Off")}, {value:"math", label:t("admin.optionCaptchaMath", "Math")}, {value:"text", label:t("admin.optionCaptchaText", "Text code")}, {value:"mixed", label:t("admin.optionCaptchaMixed", "Mixed")}]),
      adminSettingInput("captcha.math-complexity", v["captcha.math-complexity"], "text", [{value:"easy", label:t("admin.optionCaptchaMathEasy", "Easy")}, {value:"normal", label:t("admin.optionCaptchaMathNormal", "Normal")}, {value:"hard", label:t("admin.optionCaptchaMathHard", "Hard")}]),
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
    const typingRows = [
      adminSettingInput("chat.typing-indicator.user-display-control", v["chat.typing-indicator.user-display-control"], "boolean"),
      adminSettingInput("chat.typing-indicator.open-chat.enabled", v["chat.typing-indicator.open-chat.enabled"], "boolean"),
      adminSettingInput("chat.typing-indicator.dm.enabled", v["chat.typing-indicator.dm.enabled"], "boolean"),
      adminSettingInput("chat.typing-indicator.group-chat.enabled", v["chat.typing-indicator.group-chat.enabled"], "boolean")
    ];
    const moderationRows = [
      adminSettingInput("moderation.allow-user-self-message-delete", v["moderation.allow-user-self-message-delete"], "boolean"),
      adminSettingInput("moderation.self-message-delete-window-minutes", v["moderation.self-message-delete-window-minutes"], "text", [
        {value:0, label:t("admin.optionDeleteWindowAlways", "Always")},
        {value:5, label:fmt("admin.optionDeleteWindowMinutes", "Within {minutes} minutes", {minutes:5})},
        {value:10, label:fmt("admin.optionDeleteWindowMinutes", "Within {minutes} minutes", {minutes:10})},
        {value:30, label:fmt("admin.optionDeleteWindowMinutes", "Within {minutes} minutes", {minutes:30})},
        {value:60, label:fmt("admin.optionDeleteWindowMinutes", "Within {minutes} minutes", {minutes:60})}
      ])
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
        ${adminSettingGroup("admin.settingsGroupTyping", "Typing indicators", typingRows)}
        ${adminSettingGroup("admin.settingsGroupModeration", "Message deletion", moderationRows)}
        ${adminSettingGroup("admin.settingsGroupUploads", "Uploads", uploadRows)}
        ${adminSettingGroup("admin.settingsGroupDiscordAlerts", "Discord admin alerts", discordAlertRows)}
      </div>
      <div class="kwc-row kwc-admin-save-row"><small class="kwc-admin-result" id="kwc-settings-result"></small></div>`;
    const headerSave = content.closest(".kwc-admin-modal")?.querySelector("#kwc-admin-header-save");
    if (headerSave) {
      headerSave.classList.remove("kwc-hidden");
      headerSave.onclick = () => saveAdminSettingElements(content);
    }
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
    if (state.role !== "ADMIN" && state.adminCapabilities["content-filter-manage"] !== true) return;
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
    state.adminCapabilities = summary && summary.capabilities && typeof summary.capabilities === "object" ? summary.capabilities : {};
    let showOfflineUsers = false;
    try { showOfflineUsers = localStorage.getItem("kwc.adminShowOfflineUsers") === "1"; } catch (_) {}
    const online = await adminApi("/admin/online" + (showOfflineUsers ? "?includeOffline=true" : ""));
    content.innerHTML = `
      <div class="kwc-admin-grid">
        <div>${t("admin.online", "Online")}</div><strong>${esc(summary.onlineCount)}</strong>
        <div>${t("admin.accounts", "Accounts")}</div><strong>${esc(summary.accountCount)}</strong>
        <div>${t("admin.sessions", "Sessions")}</div><strong>${esc(summary.sessionCount)}</strong>
        <div>${t("admin.mutes", "Mutes")}</div><strong>${esc(summary.muteCount)}</strong>
      </div>
      <div class="kwc-filter-section-head"><h4>${t("admin.onlineUsers", "Online users")}</h4><label class="kwc-filter-toggle"><input type="checkbox" id="kwc-admin-show-offline-users"${showOfflineUsers ? " checked" : ""}> ${t("admin.showOfflineUsers", "Show offline users")}</label></div><div class="kwc-admin-list kwc-admin-online-list">${(online.players || []).map(p => `<div>${directMessageIdentityHtml({displayName: p.displayName || p.name || "", username: p.name || "", uuid: p.uuid || ""}, "kwc-sender")} ${presenceCompactHtml(p, p.uuid || "")}</div>`).join("") || `<em>${t("admin.none", "none")}</em>`}</div>
      ${state.role === "ADMIN" ? `<br><div class="kwc-row kwc-admin-actions-row"><button class="kwc-button" id="kwc-clear-history">${t("button.clearHistory", "Delete all public chat history")}</button></div>` : ""}
    `;
    installSenderIdentityToggle(content);
    const showOffline = content.querySelector("#kwc-admin-show-offline-users");
    if (showOffline) showOffline.onchange = () => { try { localStorage.setItem("kwc.adminShowOfflineUsers", showOffline.checked ? "1" : "0"); } catch (_) {} renderAdminSummary(content).catch(err => { content.innerHTML = `<pre>${esc(err && err.message || err)}</pre>`; }); };
    const clear = content.querySelector("#kwc-clear-history");
    if (clear) clear.onclick = async () => { if (!confirmPlain(t("alert.confirmClearHistory", "WARNING: All public chat history, related reactions, and saved public-chat archives will be permanently deleted from the server. This cannot be undone or recovered. Are you sure you want to delete them?"))) return; const res = await adminWrite("/admin/clear-history", {}); if (!res.ok) alertResponse("alert.failed", "Failed: {error}", res); };
    const toggleModeration = content.querySelector("#kwc-toggle-moderation-actions");
    updateModerationActionsToggleButton(toggleModeration);
    if (toggleModeration) toggleModeration.onclick = () => { setModerationActionsVisible(!state.moderationActionsVisible); updateModerationActionsToggleButton(toggleModeration); };
  }

  async function renderAdminOnline(content) {
    let showOfflineUsers = false;
    try { showOfflineUsers = localStorage.getItem("kwc.adminShowOfflineUsers") === "1"; } catch (_) {}
    const online = await adminApi("/admin/online" + (showOfflineUsers ? "?includeOffline=true" : ""));
    content.innerHTML = `
      <div class="kwc-filter-section-head"><h4>${t("admin.onlineUsers", "Online users")}</h4><label class="kwc-filter-toggle"><input type="checkbox" id="kwc-admin-show-offline-users"${showOfflineUsers ? " checked" : ""}> ${t("admin.showOfflineUsers", "Show offline users")}</label></div>
      <div class="kwc-admin-list kwc-admin-online-list">${(online.players || []).map(p => `<div>${directMessageIdentityHtml({displayName: p.displayName || p.name || "", username: p.name || "", uuid: p.uuid || ""}, "kwc-sender")} ${presenceCompactHtml(p, p.uuid || "")}</div>`).join("") || `<em>${t("admin.none", "none")}</em>`}</div>
    `;
    installSenderIdentityToggle(content);
    const showOffline = content.querySelector("#kwc-admin-show-offline-users");
    if (showOffline) showOffline.onchange = () => { try { localStorage.setItem("kwc.adminShowOfflineUsers", showOffline.checked ? "1" : "0"); } catch (_) {} renderAdminOnline(content).catch(err => { content.innerHTML = `<pre>${esc(err && err.message || err)}</pre>`; }); };
  }

  async function renderAdminMutes(content) {
    const canManageMutes = (!state.config || state.config.moderationEnabled !== false) && (state.role === "ADMIN" || state.adminCapabilities["guest-mute"] === true);
    const canUserControls = state.role === "ADMIN" || state.adminCapabilities["user-restrictions"] === true || state.adminCapabilities["profile-avatar-delete"] === true;
    const [muteData, userData] = await Promise.all([
      canManageMutes ? adminApi("/admin/mutes") : Promise.resolve({mutes:[]}),
      canUserControls ? adminApi("/admin/user-controls") : Promise.resolve({users:[]})
    ]);
    content.innerHTML = `
      <h4>${esc(t("admin.blocksAndRestrictions", "Blocks / restrictions"))}</h4>
      ${canManageMutes ? `<section class="kwc-admin-section-card"><div class="kwc-admin-section-title">${esc(t("admin.guestIpBlocks", "Guest / IP mutes"))}</div>
        <div class="kwc-row">
          <select class="kwc-input" id="kwc-mute-type"><option value="guest">${t("admin.typeGuest", "guest")}</option><option value="ip">${t("admin.typeIp", "ip")}</option></select>
          <input class="kwc-input" id="kwc-mute-value" placeholder="${t("placeholder.muteTarget", "Guest name or IP")}">
        </div>
        <div class="kwc-row">
          <input class="kwc-input" id="kwc-mute-min" placeholder="${t("placeholder.minutes", "minutes")}" value="${esc(state.config?.defaultMuteMinutes || 60)}">
          <input class="kwc-input" id="kwc-mute-reason" placeholder="${t("placeholder.reason", "reason")}">
          <button class="kwc-button" id="kwc-mute-add">${t("button.mute", "Mute")}</button>
        </div>
        <div class="kwc-admin-list">${(muteData.mutes || []).map(m => `<div class="kwc-admin-item"><div><strong>${esc(m.type)}</strong>: ${esc(m.value)}<br><small>${esc(m.reason || "")}</small></div><button class="kwc-button" data-unmute-type="${esc(m.type)}" data-unmute-value="${esc(m.value)}">${t("button.unmute", "Unmute")}</button></div>`).join("") || `<em>${t("admin.none", "none")}</em>`}</div></section>` : ""}
      ${canUserControls ? `<section class="kwc-admin-section-card"><div class="kwc-admin-section-title">${esc(t("admin.accountRestrictions", "Signed-in user restrictions"))}</div><p><small>${esc(t("admin.userControlsHint", "Chat ban blocks KWC public/DM/group sending. Upload ban blocks chat and profile-image uploads."))}</small></p><div id="kwc-admin-combined-user-controls"></div></section>` : ""}
    `;
    if (canManageMutes) {
      const add = content.querySelector("#kwc-mute-add");
      if (add) add.onclick = async () => {
        const body = {type:content.querySelector("#kwc-mute-type").value, value:content.querySelector("#kwc-mute-value").value, minutes:content.querySelector("#kwc-mute-min").value, reason:content.querySelector("#kwc-mute-reason").value};
        const res = await adminWrite("/admin/mute", body);
        if (!res.ok) return alertResponse("alert.failed", "Failed: {error}", res);
        renderAdminMutes(content);
      };
      content.querySelectorAll("[data-unmute-type]").forEach(btn => btn.onclick = async () => {
        const res = await adminWrite("/admin/unmute", {type:btn.dataset.unmuteType, value:btn.dataset.unmuteValue});
        if (!res.ok) return alertResponse("alert.failed", "Failed: {error}", res);
        renderAdminMutes(content);
      });
    }
    const userHost = content.querySelector("#kwc-admin-combined-user-controls");
    if (userHost) renderAdminUserControlsInto(userHost, Array.isArray(userData && userData.users) ? userData.users : []);
  }


  async function renderAdminEmojis(content) {
    if (state.role !== "ADMIN" && state.adminCapabilities["emoji-manage"] !== true) return;
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
          <div class="kwc-admin-emoji-reaction-actions">
            <button class="kwc-button" id="kwc-emoji-reaction-manage" type="button">${esc(t("admin.reactionCatalogManage", "Manage reaction icons"))}</button>
          </div>
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
    const reactionManageBtn = content.querySelector("#kwc-emoji-reaction-manage");
    if (reactionManageBtn) reactionManageBtn.onclick = () => renderAdminReactions(content);
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

  function moderatorCapabilityLabel(capability) {
    const labels = {
      "view-online": ["admin.capabilityViewOnline", "View online/offline users"],
      "message-delete": ["admin.capabilityMessageDelete", "Delete public messages"],
      "guest-mute": ["admin.capabilityGuestMute", "Mute/unmute guests and IPs"],
      "pin-manage": ["admin.capabilityPinManage", "Manage public pinned messages"],
      "user-restrictions": ["admin.capabilityUserRestrictions", "Set user chat/upload bans"],
      "profile-avatar-delete": ["admin.capabilityProfileAvatarDelete", "Delete user profile images"],
      "content-filter-manage": ["admin.capabilityContentFilterManage", "Manage message filters and word lists"],
      "emoji-manage": ["admin.capabilityEmojiManage", "Manage emoji uploads, packs, and reactions"],
      "game-manage": ["admin.capabilityGameManage", "Create, draw, and close events"]
    };
    const row = labels[String(capability || "")] || ["", String(capability || "")];
    return row[0] ? t(row[0], row[1]) : row[1];
  }

  function renderAdminUserControlsInto(content, users) {
    const canRestrictions = state.role === "ADMIN" || state.adminCapabilities["user-restrictions"] === true;
    const canAvatarDelete = state.role === "ADMIN" || state.adminCapabilities["profile-avatar-delete"] === true;
    content.innerHTML = `<div class="kwc-admin-list kwc-admin-user-controls">${users.map(user => {
      const uuid = String(user.uuid || "");
      const protectedTarget = state.role !== "ADMIN" && (String(user.role || "") === "ADMIN" || String(user.role || "") === "MODERATOR");
      return `<div class="kwc-admin-item kwc-admin-user-control" data-user-control="${esc(uuid)}"><div><strong>${directMessageIdentityHtml({displayName:user.displayName || user.username || "", username:user.username || "", uuid}, "kwc-sender")}</strong> <small>${esc(user.role || "")}</small><div class="kwc-row">${canRestrictions ? `<label><input type="checkbox" data-user-chat-ban${user.chatBanned ? " checked" : ""}${protectedTarget ? " disabled" : ""}> ${esc(t("admin.chatBan", "Chat ban"))}</label><label><input type="checkbox" data-user-upload-ban${user.uploadBanned ? " checked" : ""}${protectedTarget ? " disabled" : ""}> ${esc(t("admin.uploadBan", "Upload ban"))}</label><button type="button" class="kwc-button" data-user-control-save${protectedTarget ? " disabled" : ""}>${esc(t("button.save", "Save"))}</button>` : ""}${canAvatarDelete && user.hasCustomAvatar ? `<button type="button" class="kwc-button" data-user-avatar-delete${protectedTarget ? " disabled" : ""}>${esc(t("admin.deleteProfileImage", "Delete profile image"))}</button>` : ""}</div></div></div>`;
    }).join("") || `<em>${esc(t("admin.none", "none"))}</em>`}</div>`;
    installSenderIdentityToggle(content);
    content.querySelectorAll("[data-user-control]").forEach(row => {
      const uuid = String(row.getAttribute("data-user-control") || "");
      const save = row.querySelector("[data-user-control-save]");
      if (save) save.onclick = async () => {
        save.disabled = true;
        try {
          const res = await adminWrite("/admin/user-controls", {uuid, chatBanned:String(!!row.querySelector("[data-user-chat-ban]")?.checked), uploadBanned:String(!!row.querySelector("[data-user-upload-ban]")?.checked)});
          if (!res || res.ok === false) throw new Error(res && res.error || "save_failed");
        } catch (e) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:e.message || "save_failed"})); }
        finally { save.disabled = false; }
      };
      const del = row.querySelector("[data-user-avatar-delete]");
      if (del) del.onclick = async () => {
        if (!confirmPlain(t("admin.deleteProfileImageConfirm", "Delete this user's custom profile image?"))) return;
        del.disabled = true;
        try {
          const res = await adminWrite("/admin/profile-avatar/delete", {uuid});
          if (!res || res.ok === false) throw new Error(res && res.error || "delete_failed");
          del.remove();
        } catch (e) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:e.message || "delete_failed"})); del.disabled = false; }
      };
    });
  }

  async function renderAdminUserControls(content) {
    const data = await adminApi("/admin/user-controls");
    renderAdminUserControlsInto(content, Array.isArray(data && data.users) ? data.users : []);
  }


  async function renderAdminModeratorPermissions(content) {
    if (state.role !== "ADMIN") return;
    const data = await adminApi("/admin/moderator-permissions");
    const capabilities = Array.isArray(data && data.capabilities) ? data.capabilities : [];
    const moderators = Array.isArray(data && data.moderators) ? data.moderators : [];
    content.innerHTML = `<h4>${esc(t("admin.moderatorPermissions", "Moderator permissions"))}</h4><p><small>${esc(t("admin.moderatorPermissionsHint", "Delegate moderation functions per moderator. Administrator-only server/account/session settings are never delegated."))}</small></p><div class="kwc-admin-list kwc-admin-moderator-permissions">${moderators.map(mod => { const displayName = String(mod.displayName || mod.username || ""); const realName = String(mod.username || ""); const realNameHtml = realName && realName.toLowerCase() !== displayName.toLowerCase() ? `<span class="kwc-moderator-real-name">(${esc(realName)})</span>` : ""; const splitAt = Math.ceil(capabilities.length / 2); const capabilityColumns = [capabilities.slice(0, splitAt), capabilities.slice(splitAt)]; return `<div class="kwc-admin-item" data-moderator-permissions="${esc(mod.uuid || "")}"><div><strong>${directMessageIdentityHtml({displayName, username:realName, uuid:mod.uuid || ""}, "kwc-sender")}</strong>${realNameHtml}<div class="kwc-admin-capability-grid">${capabilityColumns.map(column => `<div class="kwc-admin-capability-column">${column.map(capability => `<label><input type="checkbox" data-moderator-capability="${esc(capability)}"${mod.permissions && mod.permissions[capability] === true ? " checked" : ""}> ${esc(moderatorCapabilityLabel(capability))}</label>`).join("")}</div>`).join("")}</div><button type="button" class="kwc-button" data-moderator-save>${esc(t("button.save", "Save"))}</button></div></div>`; }).join("") || `<em>${esc(t("admin.none", "none"))}</em>`}</div>`;
    installSenderIdentityToggle(content);
    content.querySelectorAll("[data-moderator-permissions]").forEach(row => {
      const save = row.querySelector("[data-moderator-save]");
      if (!save) return;
      save.onclick = async () => {
        const body = {uuid:String(row.getAttribute("data-moderator-permissions") || "")};
        row.querySelectorAll("[data-moderator-capability]").forEach(input => { body[input.getAttribute("data-moderator-capability")] = String(!!input.checked); });
        save.disabled = true;
        try {
          const res = await adminWrite("/admin/moderator-permissions", body);
          if (!res || res.ok === false) throw new Error(res && res.error || "save_failed");
        } catch (e) { alertPlain(fmt("alert.failed", "Failed: {error}", {error:e.message || "save_failed"})); }
        finally { save.disabled = false; }
      };
    });
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


  function visibleViewportRect() {
    const viewport = window.visualViewport;
    const width = Math.max(1, Number(viewport && viewport.width) || Number(window.innerWidth) || document.documentElement.clientWidth || 1);
    const height = Math.max(1, Number(viewport && viewport.height) || Number(window.innerHeight) || document.documentElement.clientHeight || 1);
    return {
      left:Math.max(0, Number(viewport && viewport.offsetLeft) || 0),
      top:Math.max(0, Number(viewport && viewport.offsetTop) || 0),
      width, height
    };
  }

  function clampModalPosition(modal, left, top) {
    const rect = modal.getBoundingClientRect();
    const viewport = visibleViewportRect();
    const pad = 8;
    const minLeft = viewport.left + pad;
    const minTop = viewport.top + pad;
    const maxLeft = Math.max(minLeft, viewport.left + viewport.width - Math.min(rect.width, viewport.width - pad * 2) - pad);
    const maxTop = Math.max(minTop, viewport.top + viewport.height - Math.min(rect.height, viewport.height - pad * 2) - pad);
    return {
      left: Math.max(minLeft, Math.min(maxLeft, Number(left) || minLeft)),
      top: Math.max(minTop, Math.min(maxTop, Number(top) || minTop))
    };
  }

  function reflowModalIntoVisibleViewport(modal, storageKey = "") {
    if (!modal || !document.body.contains(modal)) return;
    const wrap = modal.closest(".kwc-modal-backdrop");
    if (wrap && wrap.classList.contains("kwc-private-mobile-viewport")) return;
    const viewport = visibleViewportRect();
    const pad = 8;
    if (modal.dataset.kwcMaximized === "1") {
      modal.style.setProperty("position", "absolute", "important");
      modal.style.setProperty("left", Math.round(viewport.left) + "px", "important");
      modal.style.setProperty("top", Math.round(viewport.top) + "px", "important");
      modal.style.setProperty("width", Math.round(viewport.width) + "px", "important");
      modal.style.setProperty("height", Math.round(viewport.height) + "px", "important");
      return;
    }
    modal.style.setProperty("max-width", Math.max(1, Math.floor(viewport.width - pad * 2)) + "px", "important");
    modal.style.setProperty("max-height", Math.max(1, Math.floor(viewport.height - pad * 2)) + "px", "important");
    const rect = modal.getBoundingClientRect();
    const clamped = clampModalPosition(modal, rect.left, rect.top);
    if (wrap && wrap.classList.contains("kwc-modal-dragging-ready")) {
      modal.style.setProperty("position", "absolute", "important");
      modal.style.setProperty("left", clamped.left + "px", "important");
      modal.style.setProperty("top", clamped.top + "px", "important");
      modal.style.setProperty("margin", "0", "important");
      if (storageKey) localStorage.setItem(storageKey, JSON.stringify({left:Math.round(clamped.left), top:Math.round(clamped.top)}));
    }
    if (modal.__kwcResizeZoneUpdate) modal.__kwcResizeZoneUpdate();
  }

  function reflowAllKwcModalsToViewport() {
    document.querySelectorAll(".kwc-modal-backdrop > .kwc-modal").forEach(modal => reflowModalIntoVisibleViewport(modal, ""));
  }

  function makeModalDraggable(wrap, storageKey) {
    const modal = wrap && wrap.querySelector(".kwc-modal");
    // Chat windows use the whole title bar as the drag surface. Other dialogs
    // keep their existing h3-only drag affordance. Buttons/inputs are excluded
    // by begin(), so Close and other header controls remain fully clickable.
    const handle = modal && (modal.classList.contains("kwc-dm-modal")
      ? modal.querySelector(".kwc-dm-head")
      : (modal.querySelector(":scope > .kwc-modal-head") || modal.querySelector("h3")));
    if (!modal || !handle) return;
    if (modal.dataset.kwcDraggableInstalled === "1") return;
    modal.dataset.kwcDraggableInstalled = "1";

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

    let pending = false;
    let active = false;
    let activePointerId = null;
    let startX = 0;
    let startY = 0;
    let startLeft = 0;
    let startTop = 0;
    let offsetX = 0;
    let offsetY = 0;
    // A title click must remain a click long enough for the browser to emit dblclick.
    // Moving only a few pixels while pressing is common mouse jitter and must not turn
    // the first/second click into a window drag. Start dragging only after this threshold.
    const dragStartThresholdPx = 6;

    const point = event => {
      const src = event.touches && event.touches.length ? event.touches[0] :
                  event.changedTouches && event.changedTouches.length ? event.changedTouches[0] :
                  event;
      return {x: Number(src.clientX) || 0, y: Number(src.clientY) || 0};
    };

    const persist = () => {
      if (!storageKey) return;
      localStorage.setItem(storageKey, JSON.stringify({
        left: parseFloat(modal.style.left) || 0,
        top: parseFloat(modal.style.top) || 0
      }));
    };

    const end = event => {
      if (!active && !pending) return;
      const pointerId = activePointerId;
      const wasActive = active;
      pending = false;
      active = false;
      activePointerId = null;
      try { if (pointerId != null && handle.hasPointerCapture && handle.hasPointerCapture(pointerId)) handle.releasePointerCapture(pointerId); } catch (_) {}
      handle.classList.remove("kwc-dragging");
      if (wasActive) {
        persist();
        if (modal.__kwcResizeZoneUpdate) modal.__kwcResizeZoneUpdate();
        if (event && event.cancelable) event.preventDefault();
        if (event && event.stopPropagation) event.stopPropagation();
      }
    };

    const begin = event => {
      if (modal.dataset.kwcMaximized === "1") return;
      if (event.pointerType !== "touch" && event.button != null && event.button !== 0) return;
      const target = event.target;
      if (target && target.closest && target.closest("button, input, select, textarea, a, [role=button]")) return;
      const p = point(event);
      const rect = modal.getBoundingClientRect();

      pending = true;
      active = false;
      activePointerId = event.pointerId == null ? null : event.pointerId;
      startX = p.x;
      startY = p.y;
      startLeft = rect.left;
      startTop = rect.top;
      offsetX = p.x - rect.left;
      offsetY = p.y - rect.top;
      // Deliberately do not preventDefault/capture/reposition yet. Doing so on the
      // first pointerdown interferes with native double-click detection and caused
      // the window to creep a few pixels instead of maximizing.
    };

    const move = event => {
      if (!active && !pending) return;
      if (activePointerId != null && event.pointerId != null && event.pointerId !== activePointerId) return;
      // Pointer capture can occasionally survive a lost mouseup outside the browser.
      // If no primary mouse button is held anymore, terminate instead of leaving a sticky drag.
      if (event.pointerType !== "touch" && typeof event.buttons === "number" && (event.buttons & 1) === 0) { end(event); return; }
      const p = point(event);
      if (!active) {
        const dx = p.x - startX;
        const dy = p.y - startY;
        if ((dx * dx) + (dy * dy) < dragStartThresholdPx * dragStartThresholdPx) return;
        pending = false;
        active = true;
        modal.style.position = "absolute";
        modal.style.left = startLeft + "px";
        modal.style.top = startTop + "px";
        modal.style.margin = "0";
        wrap.classList.add("kwc-modal-dragging-ready");
        handle.classList.add("kwc-dragging");
        try { if (activePointerId != null && handle.setPointerCapture) handle.setPointerCapture(activePointerId); } catch (_) {}
      }
      const clamped = clampModalPosition(modal, p.x - offsetX, p.y - offsetY);
      modal.style.left = clamped.left + "px";
      modal.style.top = clamped.top + "px";
      if (modal.__kwcResizeZoneUpdate) modal.__kwcResizeZoneUpdate();
      if (event.cancelable) event.preventDefault();
      event.stopPropagation();
    };

    const endOnVisibilityLoss = () => { if (document.hidden) end(); };
    handle.addEventListener("pointerdown", begin);
    handle.addEventListener("lostpointercapture", end);
    window.addEventListener("pointermove", move, true);
    window.addEventListener("pointerup", end, true);
    window.addEventListener("pointercancel", end, true);
    window.addEventListener("blur", end, true);
    document.addEventListener("visibilitychange", endOnVisibilityLoss, true);
    const useTouchFallback = !("PointerEvent" in window);
    if (useTouchFallback) {
      handle.addEventListener("touchstart", begin, {passive: false});
      window.addEventListener("touchmove", move, {capture: true, passive: false});
      window.addEventListener("touchend", end, {capture: true, passive: false});
      window.addEventListener("touchcancel", end, {capture: true, passive: false});
    }
    const viewportReflow = () => {
      reflowModalIntoVisibleViewport(modal, storageKey);
      setTimeout(() => reflowModalIntoVisibleViewport(modal, storageKey), 80);
    };
    window.addEventListener("resize", viewportReflow, {passive:true});
    window.addEventListener("orientationchange", viewportReflow, {passive:true});
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", viewportReflow, {passive:true});
      window.visualViewport.addEventListener("scroll", viewportReflow, {passive:true});
    }
    wrap.__kwcDragCleanup = () => {
      handle.removeEventListener("pointerdown", begin);
      handle.removeEventListener("lostpointercapture", end);
      window.removeEventListener("pointermove", move, true);
      window.removeEventListener("pointerup", end, true);
      window.removeEventListener("pointercancel", end, true);
      window.removeEventListener("blur", end, true);
      document.removeEventListener("visibilitychange", endOnVisibilityLoss, true);
      if (useTouchFallback) {
        handle.removeEventListener("touchstart", begin, {passive: false});
        window.removeEventListener("touchmove", move, {capture: true, passive: false});
        window.removeEventListener("touchend", end, {capture: true, passive: false});
        window.removeEventListener("touchcancel", end, {capture: true, passive: false});
      }
      window.removeEventListener("resize", viewportReflow, {passive:true});
      window.removeEventListener("orientationchange", viewportReflow, {passive:true});
      if (window.visualViewport) {
        window.visualViewport.removeEventListener("resize", viewportReflow, {passive:true});
        window.visualViewport.removeEventListener("scroll", viewportReflow, {passive:true});
      }
    };
  }

  function installAutomaticModalDragging() {
    if (document.body.dataset.kwcAutomaticModalDragging === "1") return;
    document.body.dataset.kwcAutomaticModalDragging = "1";
    const install = root => {
      const wraps = [];
      if (root && root.matches && root.matches(".kwc-modal-backdrop")) wraps.push(root);
      if (root && root.querySelectorAll) root.querySelectorAll(".kwc-modal-backdrop").forEach(item => wraps.push(item));
      wraps.forEach(wrap => {
        const modal = wrap.querySelector(":scope > .kwc-modal");
        if (!modal || modal.classList.contains("kwc-dm-modal")) return;
        makeModalDraggable(wrap, "");
      });
    };
    install(document.body);
    new MutationObserver(records => records.forEach(record => record.addedNodes.forEach(node => {
      if (node && node.nodeType === 1) install(node);
    }))).observe(document.body, {childList: true, subtree: true});
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
    return {note: "", drag: ""};
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
