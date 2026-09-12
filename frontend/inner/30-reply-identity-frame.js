// [KWC 유지보수 주석 / KWC maintenance notes]
// 답글 snapshot, 발신자 identity 표시, 메시지로 점프, 부모 frame과의 크기/포커스 연동처럼 “메시지 주변 컨텍스트” 기능을 담당한다.
// This fragment handles message-adjacent context: reply snapshots, sender identity display, jump-to-message behavior, and parent-frame size/focus coordination.
// reply preview는 저장 데이터와 화면 축약을 분리한다. 원문 snapshot은 가능한 완전하게 유지하고, 말줄임표는 CSS/렌더링 단계에서만 적용한다.
// Reply preview storage is separated from visual truncation: preserve the complete snapshot where possible and apply ellipsis only at rendering/CSS time.
// frame bridge는 BlueMap 등 부모 페이지와 iframe 사이의 포커스·크기 상태를 전달하므로 notification suppression과 resize lock에도 간접적으로 영향을 준다.
// The frame bridge transfers focus/size state between hosts such as BlueMap and the iframe, indirectly affecting notification suppression and resize locking.

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

  function senderIdentityModeControlLabel() {
    return fmt("presence.nameToggle", "Name display: {mode}", {mode: state.senderIdentityMode === "real" ? t("presence.realName", "Real name") : t("presence.displayName", "Display name")});
  }

  function syncSenderIdentityModeControls() {
    document.querySelectorAll("[data-kwc-sender-identity-mode-control]").forEach(control => {
      control.textContent = senderIdentityModeControlLabel();
      control.setAttribute("aria-pressed", state.senderIdentityMode === "real" ? "true" : "false");
      control.dataset.identityMode = state.senderIdentityMode;
    });
  }

  function applySenderIdentityMode() {
    document.querySelectorAll(senderIdentitySelector()).forEach(updateSenderIdentityElement);
    syncSenderIdentityModeControls();
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
    // Private-chat identities with a profile target reserve click/keyboard activation for the profile modal.
    if (target.dataset.userProfileUuid) return;
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
          if (sender.dataset.userProfileUuid) return;
          event.preventDefault();
          event.stopPropagation();
          if (typeof event.stopImmediatePropagation === "function") event.stopImmediatePropagation();
          toggleSenderIdentityMode();
        });
        sender.addEventListener("keydown", event => {
          if (sender.dataset.userProfileUuid) return;
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

  function publicMessageProfileUuid(target) {
    if (!target) return "";
    const uuid = String(target.uuid || "").trim().toLowerCase();
    if (!uuid || uuid.includes("~") || uuid.includes(":")) return "";
    if (!target.remote) return uuid;
    let serverId = String(target.serverId || "").trim().toLowerCase().replace(/[^a-z0-9._-]/g, "-");
    while (serverId.includes("--")) serverId = serverId.replace(/--/g, "-");
    serverId = serverId.replace(/^-+|-+$/g, "").slice(0, 64);
    return serverId ? `remote~${serverId}~${uuid}` : "";
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
    const body = `${serverBadgeHtml(msg)}<span class="kwc-source-label">${esc(displaySource(msg))}</span>`;
    const target = publicMessageDirectMessageTarget(msg);
    if (!target) return body;
    return `<button type="button" class="kwc-message-dm-target" ${directMessageTargetDataAttributes(target)} title="${esc(t("dm.open", "Open direct message"))}">${body}</button>`;
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
      if (state.dmActiveThreadId && String(state.dmActiveThreadId) !== String(existing.id) && !state.dmAuditMode) saveConversationView("dm", state.dmActiveThreadId);
      state.dmDraftTarget = null;
      state.dmActiveThreadId = existing.id;
      setActiveChatView("dm", existing.id);
      updateDirectMessageComposeControls();
      renderDirectMessageThreads();
      updateDirectMessageViewMode();
      await loadDirectMessageMessages(existing.id);
      await restoreChatViewAnchor("dm", existing.id);
    } else {
      if (state.dmActiveThreadId && !state.dmAuditMode) saveConversationView("dm", state.dmActiveThreadId);
      state.dmActiveThreadId = "";
      setActiveChatView("dm", "");
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


  function uiResizeEnabled() {
    // ui.resizable defaults to enabled. During refresh the map addon can start
    // before /api/config has fully recovered, so an unavailable/not-yet-loaded
    // config must not temporarily disable every resize hit target. Only an
    // explicit false from the loaded config disables resizing.
    return !state.config || state.config.uiResizable !== false;
  }

  function updateFrameSize() {
    if (state.isPip) {
      const title = document.querySelector(".kwc-title");
      if (title) title.textContent = t("title.full", "KOKOTO WebChat");
      return;
    }
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");
    const root = document.getElementById("kwc-root");
    if (state.isStandalone && root && !state.minimized) {
      if (standaloneMobileWindowLocked()) {
        forceStandaloneMobileMaximized(root);
      } else {
        root.style.setProperty("--kwc-standalone-width", state.frameNormalWidth + "px");
        root.style.setProperty("--kwc-standalone-height", state.frameNormalHeight + "px");
      }
    }
    const loginOnly = guestChatHidden() && !state.minimized;
    postFrame("resize", {
      minimized: state.minimized,
      height: state.minimized ? state.frameMinimizedHeight : state.frameNormalHeight,
      width: state.minimized ? 124 : state.frameNormalWidth,
      resizable: uiResizeEnabled(),
      minW: state.config ? state.config.uiMinWidth : 280,
      minH: state.config ? state.config.uiMinHeight : 240,
      maxW: state.config ? state.config.uiMaxWidth : 640,
      maxH: state.config ? state.config.uiMaxHeight : 720
    });
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
    if (state.token) {
      await loadAccountTypingPreferences();
      await loadAccountPresencePreferences();
    }
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
      if (data.type === "notificationSuppressionQuery") {
        const port = event.ports && event.ports[0];
        if (port) {
          const suppress = accountNotificationTargetActivelyViewed({dmThreadId:data.dmThreadId || "", groupRoomId:data.groupRoomId || "", publicChat:data.publicChat === true});
          try { port.postMessage({suppress}); } catch (_) {}
        }
        return;
      }
      if (data.type === "hostAttention") {
        state.hostPageVisible = data.visible !== false;
        state.hostPageFocused = data.focused === true;
        publishNotificationViewState();
      } else if (data.type === "parentResized") {
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
        else if (data.key === "typingDisplayEnabled") setAccountTypingDisplayEnabled(data.value !== false).catch(() => {});
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

  function standaloneMobileWindowLocked() {
    if (!state.isStandalone || state.isPip) return false;
    try {
      if (navigator.userAgentData && navigator.userAgentData.mobile === true) return true;
      const ua = String(navigator.userAgent || "");
      if (/Android|iPhone|iPad|iPod|Mobile|Phone/i.test(ua)) return true;
      // iPadOS can expose a desktop-style Macintosh UA while still using touch.
      if (/Macintosh/i.test(ua) && Number(navigator.maxTouchPoints || 0) > 1) return true;
    } catch (_) {}
    return false;
  }

  function standaloneMobileViewportRect() {
    const viewport = window.visualViewport || null;
    const doc = document.documentElement || {};
    const width = Math.max(1, Math.round(Number(viewport && viewport.width) || Number(window.innerWidth) || Number(doc.clientWidth) || 1));
    const height = Math.max(1, Math.round(Number(viewport && viewport.height) || Number(window.innerHeight) || Number(doc.clientHeight) || 1));
    const left = Math.round(Number(viewport && viewport.offsetLeft) || 0);
    const top = Math.round(Number(viewport && viewport.offsetTop) || 0);
    return {left, top, width, height};
  }

  function forceStandaloneMobileMaximized(root) {
    if (!root || !standaloneMobileWindowLocked()) return false;
    const viewport = standaloneMobileViewportRect();
    root.dataset.kwcMaximized = "1";
    root.classList.add("kwc-window-maximized", "kwc-mobile-window-locked");
    root.classList.remove("kwc-standalone-positioned");
    root.style.setProperty("--kwc-standalone-left", viewport.left + "px");
    root.style.setProperty("--kwc-standalone-top", viewport.top + "px");
    root.style.setProperty("--kwc-standalone-width", viewport.width + "px");
    root.style.setProperty("--kwc-standalone-height", viewport.height + "px");
    if (root.__kwcStandaloneResizeUpdate) root.__kwcStandaloneResizeUpdate();

    if (root.dataset.kwcMobileViewportLockInstalled !== "1") {
      root.dataset.kwcMobileViewportLockInstalled = "1";
      const sync = () => {
        if (!root.isConnected || !standaloneMobileWindowLocked() || state.minimized) return;
        const next = standaloneMobileViewportRect();
        root.dataset.kwcMaximized = "1";
        root.classList.add("kwc-window-maximized", "kwc-mobile-window-locked");
        root.classList.remove("kwc-standalone-positioned");
        root.style.setProperty("--kwc-standalone-left", next.left + "px");
        root.style.setProperty("--kwc-standalone-top", next.top + "px");
        root.style.setProperty("--kwc-standalone-width", next.width + "px");
        root.style.setProperty("--kwc-standalone-height", next.height + "px");
        if (root.__kwcStandaloneResizeUpdate) root.__kwcStandaloneResizeUpdate();
      };
      const syncSettled = () => {
        sync();
        setTimeout(sync, 80);
        setTimeout(sync, 260);
      };
      root.__kwcMobileViewportSync = syncSettled;
      window.addEventListener("resize", syncSettled, {passive:true});
      window.addEventListener("orientationchange", syncSettled, {passive:true});
      if (window.visualViewport) {
        window.visualViewport.addEventListener("resize", syncSettled, {passive:true});
        window.visualViewport.addEventListener("scroll", sync, {passive:true});
      }
    }
    return true;
  }

  function installDrag(root) {
    if (!presentationCapabilities().draggableWindowBase) return;
    forceStandaloneMobileMaximized(root);
    const header = root.querySelector(".kwc-header");
    if (!header) return;
    if (header.dataset.kwcMaximizeToggleInstalled !== "1") {
      header.dataset.kwcMaximizeToggleInstalled = "1";
      header.addEventListener("dblclick", event => {
        if (event.target && event.target.closest && event.target.closest("button, input, select, textarea, a, [role=\"button\"]")) return;
        if (standaloneMobileWindowLocked()) { forceStandaloneMobileMaximized(root); return; }
        event.preventDefault(); event.stopPropagation();
        if (state.isStandalone) toggleStandaloneRootMaximize(root);
        else postFrame("maximizeToggle", {});
      });
    }

    if (state.isStandalone && !standaloneMobileWindowLocked()) {
      const savedLeft = Number(localStorage.getItem("kwc.standaloneLeft"));
      const savedTop = Number(localStorage.getItem("kwc.standaloneTop"));
      if (Number.isFinite(savedLeft) && Number.isFinite(savedTop)) {
        root.classList.add("kwc-standalone-positioned");
        root.style.setProperty("--kwc-standalone-left", Math.max(0, savedLeft) + "px");
        root.style.setProperty("--kwc-standalone-top", Math.max(0, savedTop) + "px");
      }
    }

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
      if (target && target.closest && target.closest("button, input, select, textarea, a, [role=\"button\"]")) return;
      // Mobile standalone is intentionally a fixed full-screen surface. Let OS/browser
      // edge-navigation gestures pass through instead of treating them as window drag.
      if (standaloneMobileWindowLocked()) { forceStandaloneMobileMaximized(root); return; }

      const p = pointFromEvent(event);
      if (state.isStandalone && root.dataset.kwcMaximized === "1") return;
      if (state.isStandalone) {
        const rect = root.getBoundingClientRect();
        state.dragStart = {standalone: true, offsetX: p.clientX - rect.left, offsetY: p.clientY - rect.top};
        active = true;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
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
      if (state.dragStart && state.dragStart.standalone) {
        const rect = root.getBoundingClientRect();
        const left = Math.max(0, Math.min(window.innerWidth - rect.width, p.clientX - state.dragStart.offsetX));
        const top = Math.max(0, Math.min(window.innerHeight - rect.height, p.clientY - state.dragStart.offsetY));
        root.classList.add("kwc-standalone-positioned");
        root.style.setProperty("--kwc-standalone-left", left + "px");
        root.style.setProperty("--kwc-standalone-top", top + "px");
        if (root.__kwcStandaloneResizeUpdate) root.__kwcStandaloneResizeUpdate();
        event.preventDefault();
        event.stopPropagation();
        return;
      }
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
      if (state.dragStart && state.dragStart.standalone) {
        const rect = root.getBoundingClientRect();
        localStorage.setItem("kwc.standaloneLeft", String(Math.round(rect.left)));
        localStorage.setItem("kwc.standaloneTop", String(Math.round(rect.top)));
        if (root.__kwcStandaloneResizeUpdate) root.__kwcStandaloneResizeUpdate();
        state.dragStart = null;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
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

  function toggleStandaloneRootMaximize(root) {
    if (!root || !state.isStandalone || state.minimized) return;
    if (standaloneMobileWindowLocked()) { forceStandaloneMobileMaximized(root); return; }
    const maximized = root.dataset.kwcMaximized === "1";
    if (!maximized) {
      const rect = root.getBoundingClientRect();
      root.__kwcMaxRestore = {left:rect.left, top:rect.top, width:rect.width, height:rect.height};
      root.dataset.kwcMaximized = "1";
      root.classList.add("kwc-standalone-positioned", "kwc-window-maximized");
      root.style.setProperty("--kwc-standalone-left", "0px");
      root.style.setProperty("--kwc-standalone-top", "0px");
      root.style.setProperty("--kwc-standalone-width", "100vw");
      root.style.setProperty("--kwc-standalone-height", "100vh");
    } else {
      const restore = root.__kwcMaxRestore || {};
      root.dataset.kwcMaximized = "0";
      root.classList.remove("kwc-window-maximized");
      const left = Math.max(0, Number(restore.left) || 12);
      const top = Math.max(0, Number(restore.top) || 12);
      const width = Math.max(280, Number(restore.width) || state.frameNormalWidth || 372);
      const height = Math.max(240, Number(restore.height) || state.frameNormalHeight || 462);
      state.frameNormalWidth = width; state.frameNormalHeight = height;
      root.style.setProperty("--kwc-standalone-left", left + "px");
      root.style.setProperty("--kwc-standalone-top", top + "px");
      root.style.setProperty("--kwc-standalone-width", width + "px");
      root.style.setProperty("--kwc-standalone-height", height + "px");
      localStorage.setItem("kwc.standaloneLeft", String(Math.round(left)));
      localStorage.setItem("kwc.standaloneTop", String(Math.round(top)));
      saveWindowSize();
    }
    if (root.__kwcStandaloneResizeUpdate) root.__kwcStandaloneResizeUpdate();
  }

  function installStandaloneRootResizeZones(root) {
    if (!root || root.dataset.kwcStandaloneResizeZones === "1") return;
    root.dataset.kwcStandaloneResizeZones = "1";
    const directions = ["nw","n","ne","e","se","s","sw","w"];
    const handles = directions.map(direction => {
      const node = document.createElement("div");
      node.className = "kwc-window-resize-zone kwc-root-resize-zone kwc-window-resize-zone-" + direction;
      node.dataset.resizeDirection = direction;
      node.setAttribute("aria-hidden", "true");
      document.body.appendChild(node);
      return node;
    });
    let resize = null;
    let resizeFrame = 0;
    let pendingGeometry = null;
    const update = (geometry = null) => {
      // Active resize already has an exact target rectangle. Reuse it rather than
      // forcing layout with getBoundingClientRect() immediately after every CSS
      // variable write; this keeps edge handles responsive on large chat histories.
      const rect = geometry || root.getBoundingClientRect();
      const rectRight = Number.isFinite(Number(rect.right)) ? Number(rect.right) : Number(rect.left) + Number(rect.width);
      const rectBottom = Number.isFinite(Number(rect.bottom)) ? Number(rect.bottom) : Number(rect.top) + Number(rect.height);
      const hidden = standaloneMobileWindowLocked() || state.minimized || root.dataset.kwcMaximized === "1" || !!root.querySelector(":scope > .kwc-window-owned-overlay") || !uiResizeEnabled();
      const edgeOutset = 16, edgeOverlap = 3, edgeInset = 3, cornerOverlap = 5;
      const rootZ = Math.max(1000, Number.parseInt(root.style.zIndex || "", 10) || Number(state.chatWindowZ) || 1000);
      handles.forEach(handle => {
        const d = handle.dataset.resizeDirection || "se";
        // Resize zones must paint above the chat root. Otherwise the inward
        // overlap is visually transparent but pointer-inaccessible.
        handle.style.zIndex = String(rootZ + 1);
        handle.style.display = hidden ? "none" : "block";
        if (hidden) return;
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
        // Keep only a small 3px inward edge overlap (5px at corners) so the
        // scrollbar remains usable, while providing the requested 16px target
        // outside the window. At viewport edges the outside portion is clipped;
        // the inward overlap remains available instead of the resize target vanishing.
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
      state.frameNormalWidth = geometry.width;
      state.frameNormalHeight = geometry.height;
      root.classList.add("kwc-standalone-positioned");
      root.style.setProperty("--kwc-standalone-left", geometry.left + "px");
      root.style.setProperty("--kwc-standalone-top", geometry.top + "px");
      root.style.setProperty("--kwc-standalone-width", geometry.width + "px");
      root.style.setProperty("--kwc-standalone-height", geometry.height + "px");
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
      if (standaloneMobileWindowLocked()) { forceStandaloneMobileMaximized(root); return; }
      if (state.minimized || root.dataset.kwcMaximized === "1" || !uiResizeEnabled()) return;
      const point = independentWindowPoint(event);
      const rect = root.getBoundingClientRect();
      resize = {direction:String(event.currentTarget.dataset.resizeDirection || "se"), x:point.x, y:point.y, left:rect.left, top:rect.top, width:rect.width, height:rect.height, bounds:resizeBounds()};
      event.preventDefault(); event.stopPropagation();
    };
    const move = event => {
      if (!resize) return;
      const point = independentWindowPoint(event); const dx=point.x-resize.x, dy=point.y-resize.y; const d=resize.direction;
      const north=d.includes("n"), south=d.includes("s"), west=d.includes("w"), east=d.includes("e");
      let left=resize.left, top=resize.top, width=resize.width, height=resize.height;
      if (west) {left+=dx; width-=dx;} if (east) width+=dx; if (north) {top+=dy; height-=dy;} if (south) height+=dy;
      const b=resize.bounds||resizeBounds(), pad=0;
      if (width < b.minW) {if (west) left -= b.minW-width; width=b.minW;} if (height < b.minH) {if (north) top -= b.minH-height; height=b.minH;}
      width=Math.min(width, Math.min(b.maxW, window.innerWidth-left-pad)); height=Math.min(height, Math.min(b.maxH, window.innerHeight-top-pad));
      left=Math.max(0, Math.min(left, window.innerWidth-width)); top=Math.max(0, Math.min(top, window.innerHeight-height));
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
      resize=null; const rect=root.getBoundingClientRect();
      localStorage.setItem("kwc.standaloneLeft", String(Math.round(rect.left))); localStorage.setItem("kwc.standaloneTop", String(Math.round(rect.top))); saveWindowSize(); update();
      if (event) {event.preventDefault(); event.stopPropagation();}
    };
    handles.forEach(handle => { handle.addEventListener("pointerdown", begin); handle.addEventListener("touchstart", begin, {passive:false}); });
    window.addEventListener("pointermove", move, true); window.addEventListener("pointerup", end, true); window.addEventListener("pointercancel", end, true);
    window.addEventListener("touchmove", move, {capture:true,passive:false}); window.addEventListener("touchend", end, {capture:true,passive:false}); window.addEventListener("touchcancel", end, {capture:true,passive:false});
    window.addEventListener("resize", update, {passive:true});
    const overlayObserver = new MutationObserver(() => update());
    overlayObserver.observe(root, {childList:true});
    root.__kwcStandaloneResizeUpdate=update; update();
  }

  function installResize(root) {
    if (state.isStandalone) { installStandaloneRootResizeZones(root); return; }
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
      if (state.minimized || (!state.isStandalone && !uiResizeEnabled())) return;
      if (state.resizeStart) return;

      const p = pointFromEvent(event);
      if (state.isStandalone) {
        const rect = root.getBoundingClientRect();
        state.resizeStart = {
          standalone: true,
          x: p.clientX,
          y: p.clientY,
          width: rect.width,
          height: rect.height,
          bounds: resizeBounds()
        };
      } else if (state.isPip) {
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
      if (state.resizeStart && state.resizeStart.standalone) {
        const r = state.resizeStart;
        const b = r.bounds || resizeBounds();
        state.frameNormalWidth = clampNumber(r.width + (p.clientX - r.x), b.minW, Math.min(b.maxW, window.innerWidth), r.width);
        state.frameNormalHeight = clampNumber(r.height + (p.clientY - r.y), b.minH, Math.min(b.maxH, window.innerHeight), r.height);
        root.style.setProperty("--kwc-standalone-width", state.frameNormalWidth + "px");
        root.style.setProperty("--kwc-standalone-height", state.frameNormalHeight + "px");
      } else if (state.resizeStart && state.resizeStart.pip) {
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
      const wasStandalone = !!(state.resizeStart && state.resizeStart.standalone);
      state.resizeStart = null;
      if (wasStandalone) {
        saveWindowSize();
      } else if (!wasPip) {
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
      if (eventType === "reaction") {
        applyReactionUpdate(JSON.parse(rawData || "{}"));
        return;
      }
      if (eventType === "reaction-status") {
        handleReactionRequestStatus(JSON.parse(rawData || "{}"));
        return;
      }
      if (eventType === "typing") {
        handleTypingEvent(JSON.parse(rawData || "{}"));
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

  function headerActionGroupNaturalWidth(group, compactChatWidth = 0) {
    if (!group || !headerElementVisible(group)) return 0;
    // Secondary actions are grouped semantically (chat/account). Natural width
    // uses each button's intrinsic/scroll width; the compact variant only lets
    // chat/event/notification icon buttons shrink to the PIP/minimize control width.
    const visible = Array.from(group.querySelectorAll("button")).filter(headerElementVisible);
    if (!visible.length) return 0;
    const style = getComputedStyle(group);
    const gap = parseFloat(style.columnGap || style.gap) || 0;
    return visible.reduce((sum, child) => {
      let width = headerOuterWidth(child);
      const chatAction = child.closest && child.closest(".kwc-action-cluster-chat");
      if (chatAction) {
        // Use the same 46px normal footprint as the wrapped second-row menu
        // controls, while still allowing the one-row buttons to compress
        // to the PIP/minimize footprint before wrapping.
        width = Math.max(46, width);
        if (compactChatWidth > 0) width = Math.min(width, compactChatWidth);
      }
      return sum + width;
    }, 0) + gap * Math.max(0, visible.length - 1);
  }

  function captureOneRowHeaderMetrics(root, header, title, status, secondary, primary) {
    if (!root || !header || root.classList.contains("kwc-header-wrapped")) return root && root.__kwcOneRowHeaderMetrics || null;
    const pip = header.querySelector("#kwc-pip");
    const min = header.querySelector("#kwc-min");
    const control = headerElementVisible(pip) ? pip : min;
    const controlWidth = Math.max(30, Math.round(headerOuterWidth(control) || 30));
    const titleNaturalWidth = headerOuterWidth(title);
    const protectedTitleWidth = titleNaturalWidth > 0 ? Math.min(150, Math.max(92, titleNaturalWidth)) : 92;
    const metrics = {
      protectedIdentityWidth: protectedTitleWidth + headerOuterWidth(status) + 6,
      secondaryNaturalWidth: headerActionGroupNaturalWidth(secondary),
      secondaryCompactWidth: headerActionGroupNaturalWidth(secondary, controlWidth),
      primaryWidth: headerActionGroupNaturalWidth(primary),
      controlWidth
    };
    root.__kwcOneRowHeaderMetrics = metrics;
    return metrics;
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

    const title = header.querySelector(".kwc-header-identity .kwc-title");
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

    // Preserve the title/status slot and let only the four chat/event/notification
    // buttons compress from their wrapped-row normal footprint to the PIP/minimize
    // footprint. Wrap only below that compact geometry, and unwrap as soon as the
    // same compact geometry fits again. Using the same threshold in both directions
    // prevents a needlessly wide two-row state while keeping the title untouched.
    let metrics = captureOneRowHeaderMetrics(root, header, title, status, secondary, primary);
    if (!metrics) {
      const titleNaturalWidth = headerOuterWidth(title);
      const protectedTitleWidth = titleNaturalWidth > 0 ? Math.min(150, Math.max(92, titleNaturalWidth)) : 92;
      const pip = header.querySelector("#kwc-pip");
      const min = header.querySelector("#kwc-min");
      const control = headerElementVisible(pip) ? pip : min;
      const controlWidth = Math.max(30, Math.round(headerOuterWidth(control) || 30));
      metrics = {
        protectedIdentityWidth: protectedTitleWidth + headerOuterWidth(status) + 6,
        secondaryNaturalWidth: headerActionGroupNaturalWidth(secondary),
        secondaryCompactWidth: headerActionGroupNaturalWidth(secondary, controlWidth),
        primaryWidth: headerActionGroupNaturalWidth(primary),
        controlWidth
      };
    }
    const requiredNaturalOneRowWidth = metrics.protectedIdentityWidth + metrics.secondaryNaturalWidth + metrics.primaryWidth + headerGap * 2;
    const requiredCompactOneRowWidth = metrics.protectedIdentityWidth + metrics.secondaryCompactWidth + metrics.primaryWidth + headerGap * 2;
    const wrapped = root.classList.contains("kwc-header-wrapped");

    // A small tolerance avoids oscillation at fractional-pixel boundaries.
    if (wrapped) {
      if (contentWidth + 1 >= requiredCompactOneRowWidth) root.classList.remove("kwc-header-wrapped");
    } else if (contentWidth + 1 < requiredCompactOneRowWidth) {
      root.classList.add("kwc-header-wrapped");
    }
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
