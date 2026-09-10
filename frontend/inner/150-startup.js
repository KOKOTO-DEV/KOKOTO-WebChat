// [KWC 유지보수 주석 / KWC maintenance notes]
// 모든 fragment 정의가 끝난 뒤 실제 KWC 런타임을 순서대로 부팅하는 마지막 조각이다.
// This is the final fragment: after all definitions are loaded, it boots the KWC runtime in a deliberate sequence.
// config와 language를 먼저 읽고 DOM을 만든 뒤 인증·preference·pins·DM/group·history를 로드해야 초기 화면이 잘못된 기본값으로 깜빡이거나 API를 중복 호출하지 않는다.
// Config and language are loaded before DOM creation, followed by auth/preferences/pins/private chat/history, preventing default-value flicker and duplicate startup API calls.
// startup 순서를 바꿀 때는 SSE/Push heartbeat, auth token 검증, guest visibility, notification deep-link 간 의존성을 함께 확인해야 한다.
// Any startup-order change must account for dependencies among SSE/Push heartbeat, auth-token verification, guest visibility, and notification deep linking.

  // KWC bootstrap의 최상위 orchestration 함수다. 부모 frame bridge와 heartbeat를 먼저 설치한 뒤 config/lang/DOM/auth/private chat/history를 의존 순서대로 초기화한다.

  // Top-level KWC bootstrap orchestrator. It installs parent-frame bridges and heartbeats first, then initializes config/lang/DOM/auth/private chat/history in dependency order.

  async function start() {
    try { localStorage.removeItem("kwc.loginRequiredUntilLogin"); } catch (_) {}
    if (state.captchaPass === "frontend-ok") {
      state.captchaPass = "";
      try { localStorage.removeItem("kwc.captchaPass"); } catch (_) {}
    }
    installFrameFocusBridge();
    startWebPushViewHeartbeat();
    installMapPointerRelayBridge();
    installParentResizeBridge();
    installResumeRefreshHandlers();
    await loadConfig();
    await loadLang();
    state.chatViewRestoreInProgress = true;
    makeRoot();
    installAutomaticModalDragging();
    restoreCachedEmojiCatalog();
    await loadEmojis();
    installTimeDisplayDelegation();
    updateFrameSize();
    updateGuestVisibility();
    const verified = await verifyStoredToken();
    // CAPTCHA/guest composer visibility must be decided only after the persisted
    // token has either been verified or rejected for this page load.
    await refreshCaptcha();
    if (verified) {
      await loadAccountNotificationPreferences();
      await loadAccountTypingPreferences();
      await loadAccountPresencePreferences();
      await loadBlockedUsers();
      await loadAccountEmojiFavorites();
      await loadReactionCatalog(true);
    }
    await loadPins();
    await loadCommands();
    if (verified) {
      await loadDirectMessageThreads(true);
      await loadGroupChatRooms(true);
    } else {
      resetPrivateChatState();
      updateDirectMessageButton();
      updateGroupChatButton();
      updateNotificationInboxButton();
    }
    startPresenceRefreshTimer();
    if (verified) refreshLoggedInCount().catch(() => {});
    if (!guestChatHidden()) await loadHistory(false, {forceLatest: true, forceDuringScroll: true});
    const startupNavigation = parseNotificationNavigation(window.location.href);
    const hasNotificationNavigation = !!(startupNavigation && (startupNavigation.messageId || startupNavigation.dmThreadId || startupNavigation.groupRoomId));
    if (!hasNotificationNavigation) await restoreLastChatViewState();
    await navigateFromNotification(window.location.href);
    installChatViewPersistence();
    setTimeout(() => { state.chatViewRestoreInProgress = false; }, 320);
    if (state.isPip && standalonePipRelayId) {
      // PiP is a live mirror of the original standalone connection. Do not open
      // a second EventSource or register another ServiceWorker/Web Push client.
      installStandalonePipRelaySubscriber();
      updateLoginState();
    } else {
      if (!guestChatHidden()) connectStream();
      // Web Push is optional. Browser push-service registration can take several
      // seconds or fail transiently, so it must never block the chat UI startup.
      ensurePreferredWebPush().catch(() => {});
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
