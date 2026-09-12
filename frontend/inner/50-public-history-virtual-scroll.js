// [KWC 유지보수 주석 / KWC maintenance notes]
// 공개 채팅 history pagination, virtual scroll window, scroll anchoring, 오래된 메시지 추가 로드와 최신 메시지 복귀 동작을 담당한다.
// This fragment owns public-chat history pagination, the virtual-scroll window, scroll anchoring, loading older messages, and returning to the latest message.
// virtual scroll은 DOM 노드 수를 제한하면서 사용자가 보고 있던 메시지의 화면 위치를 보존해야 하므로, 단순 array slice보다 anchor/height 보정 로직이 중요하다.
// Virtual scrolling must limit DOM nodes while preserving the viewed message position, so anchor/height compensation is more important than a simple array slice.
// 재생 중 미디어, 사용자의 수동 스크롤, resize 직후에는 자동 최신 이동을 억제해야 하며 이 규칙을 깨면 화면이 갑자기 아래로 끌려가는 회귀가 생긴다.
// Automatic bottom-follow must be suppressed during active media, manual scrolling, and resize transitions; violating this rule causes the historical jump-to-bottom regressions.

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

  // viewport가 충분히 채워지지 않았고 사용자가 최신 영역을 따라가는 중일 때만 오래된 history를 추가 로드한다. 수동 스크롤 중에는 재귀 예약만 하고 즉시 DOM을 흔들지 않는다.

  // Loads older history only when the viewport is underfilled and the user is still following the latest area. During active manual scrolling it reschedules instead of mutating the DOM immediately.

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

  function markNonScrollLayoutChange(ms = 420) {
    markNonScrollUiAction();
    const now = Date.now();
    const duration = Math.max(120, Math.min(1200, Number(ms) || 420));
    state.nonScrollLayoutUntil = Math.max(Number(state.nonScrollLayoutUntil || 0), now + duration);
    // Opening/closing a compose-side panel can resize the message viewport and
    // emit a synthetic scroll event. Preserve the visible position and do not
    // let that layout-only event immediately trigger a bottom correction.
    state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), now + duration);
    state.suppressScrollRenderUntil = Math.max(Number(state.suppressScrollRenderUntil || 0), now + duration);
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

  function activeChatLineHeightPx(box) {
    if (!box) return Math.max(16, Number(state.config && state.config.uiMessageFontSize || 13) * 1.4);
    const candidates = [];
    try {
      const rendered = box.querySelector(".kwc-text, .kwc-dm-message-body, .kwc-msg");
      if (rendered) candidates.push(rendered);
    } catch (_) {}
    candidates.push(box);
    for (const el of candidates) {
      try {
        const style = getComputedStyle(el);
        const lineHeight = Number.parseFloat(style.lineHeight);
        if (Number.isFinite(lineHeight) && lineHeight > 0) return lineHeight;
        const fontSize = Number.parseFloat(style.fontSize);
        if (Number.isFinite(fontSize) && fontSize > 0) return fontSize * 1.4;
      } catch (_) {}
    }
    return Math.max(16, Number(state.config && state.config.uiMessageFontSize || 13) * 1.4);
  }

  function autoFollowBottomThresholdPx(box) {
    const c = state.config || {};
    const configured = Number(c.uiAutoFollowBottomThresholdLines);
    const lines = Number.isFinite(configured) ? Math.max(0.25, Math.min(10, configured)) : 2;
    return Math.max(1, activeChatLineHeightPx(box) * lines);
  }

  function isAutoFollowBottom(box) {
    return !!box && bottomGapPx(box) < autoFollowBottomThresholdPx(box);
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
    if (!options.allowAwayFromBottom && beforeGap < threshold && requestedGap >= threshold) {
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
    el.className = `kwc-msg kwc-role-${esc(msg.role)} kwc-source-${esc(msg.source)}${msg.hidden ? " kwc-deleted" : ""}${isPersonallyBlockedMessage(msg) ? " kwc-hidden kwc-personally-blocked" : ""}`;
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
    const profileUuid = publicMessageProfileUuid(messageDmTarget);
    const profileAttrs = profileUuid ? ` data-user-profile-uuid="${esc(profileUuid)}" role="button" tabindex="0"` : "";
    const senderAttrs = originalSender
      ? ` title="${esc(state.senderIdentityMode === "real" ? senderDisplayTitle(shownSender) : senderOriginalTitle(originalSender))}" data-display-sender="${esc(shownSender)}" data-real-sender="${esc(originalSender)}" data-source="${esc(msg.source || "")}" data-showing-real="${state.senderIdentityMode === "real" ? "1" : "0"}"${profileAttrs || ' role="button" tabindex="0"'}`
      : profileAttrs;
    const actions = messageActionAvailability(msg);
    const canDelete = actions.canDelete;
    const canPin = actions.canPin;
    const canReply = actions.canReply;
    const canReact = actions.canReact;
    const miniActionsHtml = (canReply || canReact || canPin)
      ? `<span class="kwc-mini-actions">${canReply ? `<button class="kwc-mini-action kwc-reply-action" data-reply="${esc(msg.id)}">${t("button.reply", "reply")}</button>` : ""}${canReact ? `<button class="kwc-mini-action kwc-reaction-action" data-reaction-open="${esc(msg.id)}" title="${esc(t("reaction.add", "Add reaction"))}" aria-label="${esc(t("reaction.add", "Add reaction"))}">${t("button.react", "React")}</button>` : ""}${canPin ? `<button class="kwc-mini-action" data-pin="${esc(msg.id)}">${t("button.pin", "pin")}</button>` : ""}</span>`
      : "";
    const deleteButtonHtml = canDelete ? `<button type="button" class="kwc-private-message-delete kwc-public-message-delete" data-delete="${esc(msg.id)}" title="${esc(t("button.delete", "delete"))}" aria-label="${esc(t("button.delete", "delete"))}">${kwcFaIcon("xmark")}</button>` : "";
    const gameTarget = String(msg.i18nKey || "").startsWith("game.chat.") ? chatGameMessageTarget(msg) : null;
    const gameUnavailable = !!(gameTarget && gameTarget.gameId && chatGameUnavailable(gameTarget.serverId, gameTarget.gameId));
    const gameLinkHtml = gameTarget && gameTarget.gameId
      ? `<button type="button" class="kwc-button kwc-game-chat-link${gameUnavailable ? " kwc-game-chat-link-unavailable" : ""}" data-open-chat-game="1" data-game-id="${esc(gameTarget.gameId)}" data-game-server-id="${esc(gameTarget.serverId)}"${gameUnavailable ? ` disabled title="${esc(chatGameDeletedNotice())}"` : ""}>${esc(gameUnavailable ? chatGameDeletedLabel() : t("game.open", "Open event"))}</button>`
      : "";
    el.classList.toggle("kwc-has-mini-actions", !!(canReply || canReact || canPin || canDelete));
    el.classList.toggle("kwc-has-delete-action", !!canDelete);
    el.innerHTML = `
      <div class="kwc-meta">
        <span class="kwc-sender${originalSender ? " kwc-sender-has-real" : ""}"${senderAttrs}>${originalSender ? senderNameHtml(shownSender, originalSender, msg.source) : minecraftNameHtml(renderedSender, shouldRenderMinecraftNameColors() && sourceMayRenderMinecraftNameColors(msg.source))}</span><span class="kwc-meta-sep" aria-hidden="true">·</span>${messageOriginSourceHtml(msg)}<span class="kwc-meta-sep" aria-hidden="true">·</span><span class="kwc-time-actions"><span class="kwc-time" data-time="${esc(msg.time || "")}" title="${esc(timeToggleTitle(msg.time))}" role="button" tabindex="0">${esc(time)}</span>${miniActionsHtml}</span>
      </div>
      ${deleteButtonHtml}
      ${replyReferenceHtml(msg)}
      <div class="kwc-text">${messageTextHtml(msg)}${gameLinkHtml}</div>
      ${safeImagePreviews(plainDisplayMessageText(msg), key)}
      ${reactionBarHtml(msg)}
    `;
    installCustomEmojiImageRecovery(el);
    installReactionHandlers(el, msg);
    updateCustomEmojiOnlyClass(el.querySelector(".kwc-text"));
    installSenderIdentityToggle(el);
    installTimeToggle(el);
    el.querySelectorAll(".kwc-message-dm-target").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        const target = publicMessageDirectMessageTargetFromElement(btn, el, msg);
        if (target) openDirectMessageForTarget(target);
      });
    });
    el.querySelectorAll("[data-open-chat-game]").forEach(btn => {
      btn.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        if (btn.disabled || btn.classList.contains("kwc-game-chat-link-unavailable")) return;
        const target = chatGameMessageTarget(msg);
        openChatGameModal({serverId:String(btn.dataset.gameServerId || target.serverId || ""), gameId:String(btn.dataset.gameId || target.gameId || ""), fallback:target.fallback});
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
    const canModerate = state.token && moderationEnabled && moderatorCanDeleteMessages();
    const canSelfDelete = state.token && publicMessageIsMine(msg) && selfMessageDeletionAllowed(msg);
    const canReply = !!(msg && msg.id && !msg.hidden);
    const reactionsEnabled = !state.reactionCatalog || state.reactionCatalog.enabled !== false;
    const canReact = !!(state.token && reactionsEnabled && msg && msg.id && !msg.hidden);
    return {
      canReply,
      canReact,
      canDelete: !!(msg && msg.id && !msg.hidden && (canModerate || canSelfDelete)),
      canPin: state.token && (state.role === "ADMIN" || state.role === "MODERATOR") && state.pinsEnabled !== false && state.pinsCanPin !== false && msg && msg.id && !msg.hidden && !isMessagePinned(msg.id)
    };
  }

  function syncMessageElementActions(el, msg) {
    if (!el || !msg) return;
    const meta = el.querySelector(":scope > .kwc-meta");
    if (!meta) return;

    meta.querySelectorAll(":scope > .kwc-mini-actions, :scope > .kwc-mini-action[data-pin], :scope > .kwc-mini-action[data-delete], :scope .kwc-time-actions > .kwc-mini-actions").forEach(btn => btn.remove());
    el.querySelectorAll(":scope > .kwc-public-message-delete").forEach(btn => btn.remove());

    const actions = messageActionAvailability(msg);
    const hasActions = !!(actions.canReply || actions.canReact || actions.canPin || actions.canDelete);
    el.classList.toggle("kwc-has-mini-actions", hasActions);
    el.classList.toggle("kwc-has-delete-action", !!actions.canDelete);
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

    if (actions.canReact) {
      const react = document.createElement("button");
      react.className = "kwc-mini-action kwc-reaction-action";
      react.type = "button";
      react.setAttribute("data-reaction-open", String(msg.id));
      react.textContent = t("button.react", "React");
      react.title = t("reaction.add", "Add reaction");
      react.setAttribute("aria-label", t("reaction.add", "Add reaction"));
      react.addEventListener("click", event => {
        event.preventDefault();
        event.stopPropagation();
        openReactionPicker(react, messageById(String(msg.id)) || msg);
      });
      react.dataset.kwcReactionBound = "1";
      wrap.appendChild(react);
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
      del.className = "kwc-private-message-delete kwc-public-message-delete";
      del.type = "button";
      del.setAttribute("data-delete", String(msg.id));
      del.title = t("button.delete", "delete");
      del.setAttribute("aria-label", t("button.delete", "delete"));
      setKwcFaIcon(del, "xmark");
      el.appendChild(del);
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
    const blockedNow = el.classList.contains("kwc-personally-blocked");
    if (blockedNow !== isPersonallyBlockedMessage(msg)) return true;
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
