// [KWC 유지보수 주석 / KWC maintenance notes]
// KWC의 기본 DOM 뼈대, 상단 버튼, 입력창, 로그인/계정 진입점을 생성하는 루트 UI 조각이다.
// This fragment creates the primary KWC DOM shell, header actions, composer, and login/account entry points.
// makeRoot()는 한 번만 실행되어야 하며, config/lang 로드 후 호출되어야 번역 문자열과 기능 enable/disable 상태가 최초 DOM에 올바르게 반영된다.
// makeRoot() must run only once and after config/lang loading so translated labels and feature enable/disable state are correct in the initial DOM.
// 로그인 여부에 따라 DM/그룹/관리자 UI가 조건부로 렌더링되므로 권한 버튼을 CSS로만 숨기지 말고 가능하면 DOM 자체를 생성하지 않는 원칙을 유지한다.
// Because DM/group/admin UI depends on authentication, privileged controls should preferably not be created in the DOM at all rather than merely hidden with CSS.

  function makeRoot() {
    if (document.getElementById("kwc-root")) return;
    ensureGuestNameForConfig();

    const root = document.createElement("div");
    root.id = "kwc-root";
    root.style.setProperty("--kwc-emoji-render-size", emojiRenderSizePx() + "px");
    root.style.setProperty("--kwc-emoji-picker-size", emojiPickerSizePx() + "px");
    root.style.setProperty("--kwc-emoji-panel-min-height", emojiPanelMinHeightPx() + "px");
    if (state.isPip) {
      document.documentElement.classList.add("kwc-pip-mode");
      document.body.classList.add("kwc-pip-mode");
      root.classList.add("kwc-pip-mode");
    }
    if (state.isStandalone) {
      document.documentElement.classList.add("kwc-standalone-mode");
      document.body.classList.add("kwc-standalone-mode");
      root.classList.add("kwc-standalone-mode");
    }
    root.innerHTML = `
      <div class="kwc-panel">
        <div class="kwc-header">
          <div class="kwc-header-primary">
            <div class="kwc-header-identity">
              <span class="kwc-title">${t("title.full", "KOKOTO WebChat")}</span>
              <span class="kwc-status" id="kwc-status">${t("status.connecting", "connecting...")}</span>
            </div>
            <div class="kwc-actions kwc-actions-primary">
              ${state.config && state.config.uiPictureInPictureEnabled === true && !state.isPip && documentPictureInPictureSupported() ? `<button class="kwc-button kwc-pip kwc-icon-button" id="kwc-pip" title="${t("button.pip", "PIP")}" aria-label="${t("button.pip", "PIP")}">${kwcFaIcon("window-restore")}</button>` : ""}
              ${!state.isPip ? `<button class="kwc-button kwc-icon-button" id="kwc-min" title="${t("button.minimize", "Minimize")}" aria-label="${t("button.minimize", "Minimize")}">${kwcFaIcon("minus")}</button>` : ""}
            </div>
          </div>
          <div class="kwc-actions kwc-actions-secondary">
            <div class="kwc-action-cluster kwc-action-cluster-chat">
              ${state.directMessageEnabled ? `<button class="kwc-button kwc-dm-button kwc-icon-button kwc-hidden" id="kwc-dm" title="${t("button.directMessages", "Messages")}" aria-label="${t("button.directMessages", "Messages")}">${kwcFaIcon("envelope")}<span class="kwc-dm-badge kwc-hidden" id="kwc-dm-badge">0</span></button>` : ""}
              ${state.groupChatEnabled ? `<button class="kwc-button kwc-group-button kwc-icon-button kwc-hidden" id="kwc-group" title="${t("group.title", "Group chats")}" aria-label="${t("group.title", "Group chats")}">${kwcFaIcon("user-group")}<span class="kwc-dm-badge kwc-hidden" id="kwc-group-badge">0</span></button>` : ""}
              <button class="kwc-button kwc-icon-button kwc-hidden" id="kwc-game" title="${t("game.title", "Events")}" aria-label="${t("game.title", "Events")}">${kwcFaIcon("calendar-days")}</button>
              <button class="kwc-button kwc-notification-button kwc-icon-button" id="kwc-notifications" title="${t("notifications.inbox", "Notification inbox")}" aria-label="${t("notifications.inbox", "Notification inbox")}">${kwcFaIcon("bell")}<span class="kwc-dm-badge kwc-hidden" id="kwc-notification-badge">0</span></button>
            </div>
            <div class="kwc-action-cluster kwc-action-cluster-account">
              <button class="kwc-button" id="kwc-login">${t("button.login", "Login")}</button>
            </div>
          </div>
        </div>
        <div class="kwc-pinned-bar kwc-hidden" id="kwc-pinned-bar">
          <button class="kwc-pinned-open" id="kwc-pinned-open" type="button" data-open-pins="1">
            <span class="kwc-pinned-icon">${kwcFaIcon("thumbtack")}</span>
            <span id="kwc-pinned-label">${t("pinned.count", "Pinned messages: {count}").replace("{count}", "0")}</span>
          </button>
        </div>
        <div class="kwc-messages" id="kwc-messages">
          <div class="kwc-virtual-spacer kwc-virtual-top-spacer"></div>
          <div class="kwc-history-end kwc-hidden" id="kwc-history-end">${t("history.end", "No more messages to display.")}</div>
          <div class="kwc-virtual-spacer kwc-virtual-bottom-spacer"></div>
        </div>
        <button class="kwc-jump-latest kwc-hidden" id="kwc-jump-latest" type="button" title="${t("button.jumpLatest", "Jump to latest")}">
          <span class="kwc-jump-latest-icon">${kwcFaIcon("arrow-down")}</span>
          <span id="kwc-jump-latest-label">${t("button.jumpLatest", "Jump to latest")}</span>
        </button>
        <button class="kwc-button kwc-search-button kwc-search-float kwc-icon-button kwc-hidden" id="kwc-search-open" type="button" title="${t("button.search", "Search")}" aria-label="${t("button.search", "Search")}">${kwcFaIcon("magnifying-glass")}</button>
        <div class="kwc-emoji-resize-handle kwc-hidden" id="kwc-emoji-resize" title="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}" aria-label="${t("button.resizeEmojiPanel", "Drag to resize emoji picker")}"></div>
        <div class="kwc-form">
          <div class="kwc-row" id="kwc-guest-row">
            <input class="kwc-input" id="kwc-guest-name" placeholder="${t("placeholder.guestName", "Guest name")}">
          </div>
          <div class="kwc-row kwc-captcha" id="kwc-captcha-row">
            <span id="kwc-captcha-q"></span>
            <input class="kwc-input" id="kwc-captcha-a" maxlength="6" placeholder="${t("placeholder.captchaAnswer", "answer")}">
          </div>
          <div class="kwc-reply-compose kwc-hidden" id="kwc-reply-compose">
            <button type="button" class="kwc-reply-compose-main" id="kwc-reply-compose-main" title="${t("reply.jump", "Jump to replied message")}">
              <span class="kwc-reply-compose-label" id="kwc-reply-compose-label"></span>
              <span class="kwc-reply-compose-preview" id="kwc-reply-compose-preview"></span>
            </button>
            <button type="button" class="kwc-mini-action kwc-reply-cancel kwc-icon-button" id="kwc-reply-cancel" title="${t("button.cancel", "Cancel")}" aria-label="${t("button.cancel", "Cancel")}">${kwcFaIcon("xmark")}</button>
          </div>
          <div class="kwc-row kwc-typing-anchor">
            <div class="kwc-typing-indicator kwc-hidden" id="kwc-public-typing" aria-live="polite"></div>
            <textarea class="kwc-input kwc-chat-composer" id="kwc-message" rows="1" autocomplete="off" enterkeyhint="send" maxlength="2048" placeholder="${t("placeholder.message", "message")}"></textarea>
            <button class="kwc-button kwc-command kwc-hidden" id="kwc-command" title="${t("button.commands", "Commands")}">/</button>
            <button class="kwc-button kwc-emoji-button kwc-icon-button kwc-hidden" id="kwc-emoji" title="${t("button.emoji", "Emoji")}" aria-label="${t("button.emoji", "Emoji")}">${kwcFaIcon("face-smile")}</button>
            <button class="kwc-button kwc-upload kwc-icon-button kwc-hidden" id="kwc-upload" title="${t("button.upload", "Attach")}" aria-label="${t("button.upload", "Attach")}">${kwcFaIcon("paperclip")}</button>
            <button class="kwc-button kwc-send" id="kwc-send">${t("button.send", "Send")}</button>
            <input type="file" id="kwc-file" class="kwc-file-input" multiple hidden style="display:none !important;">
          </div>
          <div class="kwc-command-panel kwc-hidden" id="kwc-command-panel"></div>
          <div class="kwc-emoji-panel kwc-hidden" id="kwc-emoji-panel" aria-live="polite"></div>
          <div class="kwc-upload-progress kwc-hidden" id="kwc-upload-progress" aria-live="polite">
            <div class="kwc-upload-progress-head">
              <span id="kwc-upload-progress-text">${t("upload.ready", "Ready")}</span>
              <button class="kwc-button kwc-upload-cancel" id="kwc-upload-cancel" type="button">${t("button.cancel", "Cancel")}</button>
            </div>
            <div class="kwc-upload-progress-bar"><div id="kwc-upload-progress-fill"></div></div>
          </div>
        </div>
        <div class="kwc-drop-overlay kwc-hidden" id="kwc-drop-overlay" aria-hidden="true">
          <div class="kwc-drop-box">
            <div class="kwc-drop-title" id="kwc-drop-title">${t("upload.dropTitle", "Drop files to upload")}</div>
            <div class="kwc-drop-subtitle" id="kwc-drop-subtitle">${t("upload.dropSubtitle", "Release inside the chat panel.")}</div>
          </div>
        </div>
        <div class="kwc-resize-handle" id="kwc-resize-handle" title="${t("button.resize", "Resize")}"></div>
      </div>
    `;
    document.body.appendChild(root);
    root.addEventListener("pointerdown", () => raiseIndependentChatWindow(root), {capture: true});
    raiseIndependentChatWindow(root);
    installModalAffordanceObserver();
    installMessageActionDelegation(root);
    applyWebFontsConfig();
    applyFontSizeConfig();
    applyMediaViewportConfig();
    applyThemeConfig();


    const guestNameField = document.getElementById("kwc-guest-name");
    guestNameField.value = limitGuestNameCodePoints(state.guestName);
    guestNameField.addEventListener("input", () => {
      const limited = limitGuestNameCodePoints(guestNameField.value);
      if (limited !== guestNameField.value) guestNameField.value = limited;
    });
    document.getElementById("kwc-send").addEventListener("click", e => {
      if (state.sendInFlight) {
        e.preventDefault();
        return;
      }
      sendMessage();
    });
    const jumpLatest = document.getElementById("kwc-jump-latest");
    if (jumpLatest) jumpLatest.addEventListener("click", () => {
      forceLatestChatView("jump-latest");
    });
    const searchOpen = document.getElementById("kwc-search-open");
    if (searchOpen) searchOpen.addEventListener("click", event => {
      event.preventDefault();
      event.stopPropagation();
      if (!state.minimized) openSearchModal();
    });
    const dmBtn = document.getElementById("kwc-dm");
    if (dmBtn) dmBtn.addEventListener("click", () => openDirectMessageModal());
    const groupBtn = document.getElementById("kwc-group");
    if (groupBtn) groupBtn.addEventListener("click", () => openGroupChatModal());
    const gameBtn = document.getElementById("kwc-game");
    if (gameBtn) gameBtn.addEventListener("click", () => openChatGameModal());
    const notificationBtn = document.getElementById("kwc-notifications");
    if (notificationBtn) notificationBtn.addEventListener("click", () => {
      if (!state.minimized) openNotificationInboxModal();
    });
    updateNotificationInboxButton();
    const commandBtn = document.getElementById("kwc-command");
    if (commandBtn) commandBtn.addEventListener("click", () => openCommandModal());
    const emojiBtn = document.getElementById("kwc-emoji");
    if (emojiBtn) emojiBtn.addEventListener("click", () => toggleEmojiPanel());
    installEmojiPanelResize(root);
    document.getElementById("kwc-upload").addEventListener("click", () => {
      const input = document.getElementById("kwc-file");
      if (input) input.click();
    });
    document.getElementById("kwc-file").addEventListener("change", uploadSelectedFiles);
    installDragAndDropUpload(root);
    const uploadCancelBtn = document.getElementById("kwc-upload-cancel");
    if (uploadCancelBtn) uploadCancelBtn.addEventListener("click", cancelCurrentUpload);
    document.getElementById("kwc-message").addEventListener("paste", handlePasteUpload);
    const replyCancel = document.getElementById("kwc-reply-cancel");
    if (replyCancel) replyCancel.addEventListener("click", clearReplyTarget);
    const replyComposeMain = document.getElementById("kwc-reply-compose-main");
    if (replyComposeMain) replyComposeMain.addEventListener("click", () => {
      if (state.replyTarget && state.replyTarget.id) jumpToReplyTarget(state.replyTarget.id);
    });
    renderReplyCompose();
    const messageInput = document.getElementById("kwc-message");
    messageInput.addEventListener("focus", () => setActiveComposeInput(messageInput));
    messageInput.addEventListener("compositionstart", () => {
      messageInputComposing = true;
      hideMentionAutocomplete();
      if (commandPanelRenderFrame) {
        cancelAnimationFrame(commandPanelRenderFrame);
        commandPanelRenderFrame = 0;
      }
    });
    messageInput.addEventListener("compositionend", () => {
      messageInputComposing = false;
      scheduleCommandPanelUpdate();
      scheduleMentionAutocomplete(messageInput);
    });
    messageInput.addEventListener("keydown", e => {
      if (handleMentionAutocompleteKeydown(e, messageInput)) return;
      if (e.key === "Enter") {
        // Enter may be used to commit Korean/Japanese/Chinese IME composition.
        // Never consume it as a chat send while composition is still active.
        if (e.isComposing || messageInputComposing || e.keyCode === 229) return;
        e.preventDefault();
        if (e.repeat || state.sendInFlight) return;
        sendMessage();
      }
      if (e.key === "Escape") { hideCommandPanel(); hideEmojiPanel(); if (state.replyTarget) clearReplyTarget(); }
    });
    messageInput.addEventListener("input", () => {
      normalizeSingleLineComposer(messageInput);
      scheduleCommandPanelUpdate();
      scheduleMentionAutocomplete(messageInput);
      if (String(messageInput.value || "").trim()) notifyPublicTyping();
    });
    messageInput.addEventListener("focus", () => { scheduleCommandPanelUpdate(); scheduleMentionAutocomplete(messageInput); });
    messageInput.addEventListener("click", () => scheduleMentionAutocomplete(messageInput));
    messageInput.addEventListener("blur", () => setTimeout(() => { hideCommandPanel(); if (!document.getElementById("kwc-mention-autocomplete")?.matches(":hover")) hideMentionAutocomplete(); }, 160));
    installHistoryPaging();
    document.getElementById("kwc-login").addEventListener("click", () => {
      if (!state.minimized) openLoginModal();
    });
    const legacyAdminBtn = document.getElementById("kwc-admin");
    if (legacyAdminBtn) legacyAdminBtn.addEventListener("click", () => {
      if (!state.minimized) openAdminModal();
    });
    const pipBtn = document.getElementById("kwc-pip");
    if (pipBtn) pipBtn.addEventListener("click", () => {
      const c = state.config || {};
      if (c.uiPictureInPictureEnabled !== true || state.isPip) {
        updatePipButton();
        return;
      }
      const unsupportedMessage = t("pip.unsupported", "Document Picture-in-Picture is not supported by this browser. Try desktop Chrome or Edge.");
      const openFailedMessage = t("pip.openFailed", "Failed to open Picture-in-Picture window: {error}");
      state.lastPipResultAt = 0;
      const pipLabels = {
        unsupported: unsupportedMessage,
        openFailed: openFailedMessage
      };
      if (state.isStandalone) {
        // Direct top-level Document PiP call. This keeps the request in the
        // original click activation and removes the standalone dependency on
        // the BlueMap iframe/parent message bridge. Actual API failures are
        // reported by toggleStandalonePictureInPicture().
        void toggleStandalonePictureInPicture(pipLabels);
      } else if (window.parent === window) {
        try { alert(unsupportedMessage); } catch (_) {}
      } else {
        postFrame("togglePip", {
          pipEnabled: true,
          labels: pipLabels
        });
        setTimeout(() => {
          if (!state.lastPipResultAt) {
            try { alert(unsupportedMessage); } catch (_) {}
          }
        }, 1200);
      }
    });
    updatePipButton();
    const minButton = document.getElementById("kwc-min");
    if (minButton) minButton.addEventListener("click", toggleMin);
    installMinimizeAvailabilityGuard();
    reconcileMinimizeAvailability();
    installDrag(root);
    installResize(root);

    if (!state.isPip && state.minimized) {
      if (state.isStandalone) {
        const rect = root.getBoundingClientRect();
        const viewportMid = Math.max(0, Number(window.innerWidth) || 0) / 2;
        root.dataset.kwcMinimizedSide = (rect.left + (rect.width / 2)) < viewportMid ? "left" : "right";
      }
      root.classList.add("kwc-minimized");
      document.getElementById("kwc-messages").classList.add("kwc-hidden");
      document.querySelector(".kwc-form").classList.add("kwc-hidden");
    }
    updateMinimizeButtonAppearance();
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");

    installResponsiveHeaderLayout();
    updateLoginState();
  }

  function closeAllModals() {
    document.querySelectorAll(".kwc-modal-backdrop, .kwc-modal-wrap").forEach(el => el.remove());
    state.loginModalOpen = false;
    state.prefsModalOpen = false;
  }

  function publicChatMinimizeViewport() {
    // Standalone is itself the host viewport. Adapter/add-on runtimes live inside
    // a deliberately small iframe, so use the map page viewport rather than the
    // iframe dimensions; otherwise desktop add-ons incorrectly lose minimize.
    let target = window;
    const presentation = presentationCapabilities();
    if (presentation.addon) {
      try { if (window.parent && window.parent !== window) target = window.parent; } catch (_) {}
    }
    try {
      const vv = target.visualViewport || null;
      const width = Number(vv && vv.width) || Number(target.innerWidth) || 0;
      const height = Number(vv && vv.height) || Number(target.innerHeight) || 0;
      return {width, height};
    } catch (_) {
      return {width:Number(window.innerWidth) || 0, height:Number(window.innerHeight) || 0};
    }
  }

  function publicChatMinimizeAvailable() {
    const presentation = presentationCapabilities();
    if (!presentation.publicMinimizeBase) return false;
    // Embedded map/add-on chat must remain minimizable even on phones/tablets.
    // The 900x480 threshold belongs only to Standalone detached DM/group
    // multi-window behavior; reusing it here incorrectly removed the minimize
    // button from mobile add-ons. Standalone keeps the existing mobile/fullscreen
    // policy so its small viewport is not collapsed into an unusable floating pill.
    if (presentation.addon) return true;
    const viewport = publicChatMinimizeViewport();
    const minW = Number(state.privateMultiWindowMinWidth || 900);
    const minH = Number(state.privateMultiWindowMinHeight || 480);
    return viewport.width >= minW && viewport.height >= minH;
  }

  function reconcileMinimizeAvailability() {
    // Standalone may disable minimize on a small/mobile viewport. Embedded map/add-on
    // runtimes remain minimizable at every viewport size. If an old Standalone state
    // is no longer valid, restore before hiding the control so the UI cannot get stuck.
    if (!publicChatMinimizeAvailable() && state.minimized) {
      toggleMin({persist: true, availabilityRestore: true});
      return;
    }
    updateMinimizeButtonVisibility();
  }

  function updateMinimizeButtonAppearance() {
    const btn = document.getElementById("kwc-min");
    if (!btn) return;
    const label = state.minimized ? t("button.restore", "Restore") : t("button.minimize", "Minimize");
    setKwcFaIcon(btn, state.minimized ? "plus" : "minus");
    btn.title = label;
    btn.setAttribute("aria-label", label);
  }

  function updateMinimizeButtonVisibility() {
    const btn = document.getElementById("kwc-min");
    if (!btn) return;
    const visible = publicChatMinimizeAvailable();
    btn.classList.toggle("kwc-hidden", !visible);
    btn.hidden = !visible;
    btn.setAttribute("aria-hidden", visible ? "false" : "true");
    // Several legacy minimized selectors intentionally use display: ... !important.
    // Use an inline important hide so an unavailable control cannot be resurrected
    // by those compatibility rules on mobile/embedded runtimes.
    if (visible) btn.style.removeProperty("display");
    else btn.style.setProperty("display", "none", "important");
    btn.disabled = !visible;
  }

  function installMinimizeAvailabilityGuard() {
    if (window.__kwcMinimizeAvailabilityGuardInstalled) return;
    window.__kwcMinimizeAvailabilityGuardInstalled = true;
    const sync = () => reconcileMinimizeAvailability();
    window.addEventListener("resize", sync, {passive:true});
    window.addEventListener("orientationchange", sync, {passive:true});
    if (window.visualViewport) window.visualViewport.addEventListener("resize", sync, {passive:true});
    // Adapter/add-on iframe dimensions normally stay at the configured chat size,
    // while the actual map viewport can change independently. Observe the parent
    // viewport too so desktop/mobile transitions update the minimize control.
    if (!state.isStandalone && !state.isPip) {
      try {
        const parentWindow = window.parent;
        if (parentWindow && parentWindow !== window) {
          parentWindow.addEventListener("resize", sync, {passive:true});
          parentWindow.addEventListener("orientationchange", sync, {passive:true});
          if (parentWindow.visualViewport) {
            parentWindow.visualViewport.addEventListener("resize", sync, {passive:true});
            parentWindow.visualViewport.addEventListener("scroll", sync, {passive:true});
          }
        }
      } catch (_) {}
    }
  }

  function toggleMin(options = {}) {
    protectHistoryEndNotice("toggle-min", 7000);
    const root = document.getElementById("kwc-root");
    const willMinimize = !state.minimized;
    // A stale/briefly visible button must never enter minimized mode when detached
    // DM/group windows are unavailable. Restoration is always allowed.
    if (willMinimize && !publicChatMinimizeAvailable()) {
      updateMinimizeButtonVisibility();
      return;
    }
    // Match adapter behavior: minimizing a maximized window first restores its
    // normal geometry, then collapses to the header-only minimized state.
    if (willMinimize && state.isStandalone && root && root.dataset.kwcMaximized === "1") {
      toggleStandaloneRootMaximize(root);
    }
    if (willMinimize && state.isStandalone && root) {
      const rect = root.getBoundingClientRect();
      const viewportMid = Math.max(0, Number(window.innerWidth) || 0) / 2;
      root.dataset.kwcMinimizedSide = (rect.left + (rect.width / 2)) < viewportMid ? "left" : "right";
    }
    // Minimize changes the message viewport height to zero/near-zero. Capture a
    // durable message anchor before changing the layout so restore can return to
    // exactly the same visible message/offset instead of accumulating scroll drift.
    if (willMinimize && typeof captureChatViewAnchor === "function") {
      state.publicMinimizeViewAnchor = captureChatViewAnchor("public");
      if (typeof saveConversationView === "function") saveConversationView("public", "");
    }

    state.minimized = willMinimize;
    if (options.persist !== false) localStorage.setItem("kwc.minimized", state.minimized ? "1" : "0");

    if (state.minimized) {
      closeAllModals();
    }

    if (root) root.classList.toggle("kwc-minimized", state.minimized);
    const messages = document.getElementById("kwc-messages");
    if (messages) messages.classList.toggle("kwc-hidden", state.minimized);
    const form = document.querySelector(".kwc-form");
    if (form) form.classList.toggle("kwc-hidden", state.minimized);
    updateEmojiResizeHandleVisibility();
    updateMinimizeButtonAppearance();
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");
    updateFrameSize();
    updatePipButton();
    updateMinimizeButtonVisibility();
    updateDirectMessageButton();
    updateGroupChatButton();
    updateNotificationInboxButton();
    scheduleResponsiveHeaderLayout();
    if (!state.minimized) {
      const restoreAnchor = state.publicMinimizeViewAnchor && typeof state.publicMinimizeViewAnchor === "object"
        ? Object.assign({}, state.publicMinimizeViewAnchor)
        : null;
      state.publicMinimizeViewAnchor = null;
      if (restoreAnchor && restoreAnchor.atBottom !== true) {
        state.autoFollowLatest = false;
        state.explicitLatestFollowUntil = 0;
        state.explicitLatestFollowReason = "";
        state.forceLatestJumpUntil = 0;
        state.preventBottomStickUntil = Math.max(Number(state.preventBottomStickUntil || 0), Date.now() + 1400);
      }
      scheduleVirtualRender({
        preserveScroll: true,
        preserveVisualAnchor: true,
        stickToBottom: !!(restoreAnchor && restoreAnchor.atBottom === true),
        suppressBottomStick: !!(restoreAnchor && restoreAnchor.atBottom !== true),
        forcePreservePosition: !!(restoreAnchor && restoreAnchor.atBottom !== true)
      });
      // The iframe/root regains its normal height asynchronously. Re-apply the same
      // message anchor after layout settles; repeated minimize/restore cycles must
      // therefore be idempotent instead of moving the reader upward each time.
      const restoreMinimizedPublicView = () => {
        if (state.minimized || !restoreAnchor || typeof restoreChatViewAnchorNow !== "function") return;
        restoreChatViewAnchorNow("public", restoreAnchor);
        scheduleScrollAffordanceRefresh("unminimize-anchor");
      };
      requestAnimationFrame(() => {
        restoreMinimizedPublicView();
        requestAnimationFrame(restoreMinimizedPublicView);
      });
      setTimeout(restoreMinimizedPublicView, 120);
      protectHistoryEndNotice("unminimize", 8000);
      state.forceHistoryEndNoticeUntil = Math.max(Number(state.forceHistoryEndNoticeUntil || 0), Date.now() + 8000);
      scheduleScrollAffordanceRefresh("unminimize");
    }
  }


  function clearLoginStorage() {
    state.token = "";
    state.authPendingToken = "";
    state.authVerified = false;
    state.username = "";
    state.userUuid = "";
    state.role = "";
    state.typingDisplayEnabled = true;
    state.typingPreferenceLoaded = false;
    state.presenceStatus = "online";
    state.loggedInCount = 0;
    state.presencePreferenceLoaded = false;
    state.notificationAccountDmViews = new Map();
    state.notificationAccountGroupViews = new Map();
    state.groupNotificationSeen = new Set();
    state.notificationInboxUnread = 0;
    if (state.accountNotificationSyncTimer) {
      clearTimeout(state.accountNotificationSyncTimer);
      state.accountNotificationSyncTimer = null;
    }
    state.blockedUsers = [];
    state.blockedUserUuids = [];
    state.adminCapabilities = {};
    if (typeof clearAccountNotificationUiState === "function") clearAccountNotificationUiState();
    scheduleTypingIndicatorRefresh();
    if (state.emojiFavoritesStorage === "account") {
      state.emojiFavorites = [];
      state.emojiFavoritesLoaded = false;
      refreshFavoriteEmojiUi();
    }
    try {
      localStorage.removeItem("kwc.token");
      localStorage.removeItem("kwc.username");
      localStorage.removeItem("kwc.role");
    } catch (_) {}
  }

  function resetPrivateChatState() {
    state.dmUnread = 0;
    state.dmThreads = [];
    state.dmAdminThreads = [];
    state.dmMessages = [];
    state.dmActiveThreadId = "";
    state.dmActiveThread = null;
    state.dmCleanupPreview = null;
    state.groupUnread = 0;
    state.groupRooms = [];
    state.groupInvites = [];
    state.groupHiddenRooms = [];
    state.groupAdminRooms = [];
    state.groupMessages = [];
    state.groupActiveRoomId = "";
    state.groupActiveRoom = null;
    state.groupCleanupPreview = null;
    state.privateChatSuperAdmin = false;
    state.groupChatContentAccess = false;
    state.groupAuditMode = false;
    state.groupAuditRoom = null;
    state.privateChatContentAccess = false;
    state.dmAuditMode = false;
    state.dmAuditThread = null;
  }

  function clearPrivateChatForAuthLoss() {
    try { document.querySelectorAll(".kwc-modal-backdrop, .kwc-modal-wrap").forEach(el => el.remove()); } catch (_) {}
    resetPrivateChatState();
    updateDirectMessageButton();
    updateGroupChatButton();
    updateNotificationInboxButton();
    updatePipButton();
  }

  function clearVisibleChatForLoggedOutHidden(reason = "auth-expired") {
    if (!guestChatHidden()) return;
    state.messages = [];
    state.nextLocalMessageId = 1;
    state.replyTarget = null;
    state.pins = [];
    state.historyHasMore = false;
    state.historyHasAfter = false;
    state.historyOldestId = "";
    state.historyNewestId = "";
    state.historyLoading = false;
    state.historyLoadSeq++;
    try { if (state.eventSource) state.eventSource.close(); } catch (_) {}
    state.eventSource = null;
    clearStreamReconnectTimer();
    renderPinnedBar();
    renderVirtualMessages({stickToBottom: true, ignoreVisibleRangeProtection: true});
    updateGuestVisibility();
    updateFrameSize();
  }

  function handleAuthExpired(reason = "expired", options = {}) {
    const hadToken = !!state.token;
    if (hadToken) publishWebPushViewState(true, false).catch(() => {});
    clearLoginStorage();
    clearPrivateChatForAuthLoss();
    updateLoginState();
    updateGuestVisibility();
    clearVisibleChatForLoggedOutHidden(reason);
    if (!state.isPip && !guestChatHidden() && hadToken && options.reconnect !== false) connectStream({refreshAfterOpen: true, reason: "auth-" + reason});
  }

  function isAuthExpiredApiError(err) {
    if (!err || !state.token) return false;
    const status = Number(err.status || 0);
    const code = err.response && err.response.error ? String(err.response.error) : "";
    // 403 is widely used by KWC for ordinary authorization/capability failures
    // (permission_denied, upload_banned, search_disabled, etc.). Treating any 403
    // containing "permission" as an expired session caused unrelated feature
    // failures to clear a perfectly valid login. Only explicit session failures,
    // or a conventional HTTP 401, are allowed to invalidate the stored token.
    if (code === "not_logged_in" || code === "login_required" || code === "auth_expired" || code === "invalid_token") return true;
    return status === 401;
  }

  function authenticatedSession() {
    return !!(state.token && state.authVerified === true);
  }

  function guestChatHidden() {
    return !!(
      state.config &&
      state.config.guestEnabled === false &&
      state.config.hideChatForGuestsWhenGuestDisabled &&
      !authenticatedSession()
    );
  }

  function updateGuestVisibility() {
    const root = document.getElementById("kwc-root");
    const box = document.getElementById("kwc-messages");
    const form = document.querySelector(".kwc-form");
    const guestRow = document.getElementById("kwc-guest-row");
    const captchaRow = document.getElementById("kwc-captcha-row");
    if (!root) return;

    const hidden = guestChatHidden();
    root.classList.toggle("kwc-guest-hidden", hidden);

    if (hidden) {
      if (box) box.classList.add("kwc-hidden");
      if (form) form.classList.add("kwc-hidden");
      if (guestRow) guestRow.classList.add("kwc-hidden");
      if (captchaRow) captchaRow.classList.remove("kwc-show");
      state.captcha = null;
      if (state.publicTypingEntries && typeof state.publicTypingEntries.clear === "function") state.publicTypingEntries.clear();
      if (typeof renderTypingIndicators === "function") renderTypingIndicators();
      updateDirectMessageButton();
      updateGroupChatButton();
      updateNotificationInboxButton();
      updatePipButton();
      updateEmojiResizeHandleVisibility();
      updateFrameSize();
      return;
    }

    if (!state.minimized) {
      if (box) box.classList.remove("kwc-hidden");
      if (form) form.classList.remove("kwc-hidden");
    }
    updateEmojiResizeHandleVisibility();
  }

  function roleLabel(role) {
    const key = String(role || "").toLowerCase();
    if (key === "moderator") return t("role.moderator.short", "MOD");
    if (key === "admin") return t("role.admin.short", "ADMIN");
    if (key === "user") return t("role.user.short", "USER");
    if (key === "guest") return t("role.guest.short", "GUEST");
    return role || t("status.loggedIn", "logged in");
  }

  function refreshStaticLabels() {
    const title = document.querySelector(".kwc-title");
    if (title) title.textContent = state.minimized ? t("title.minimized", "Chat") : t("title.full", "KOKOTO WebChat");
    const adminStatus = document.getElementById("kwc-status");
    if (adminStatus && adminStatus.classList.contains("kwc-status-admin-action")) adminStatus.title = t("button.admin", "Admin");
    const sendBtn = document.getElementById("kwc-send");
    if (sendBtn) sendBtn.textContent = t("button.send", "Send");
    const uploadBtn = document.getElementById("kwc-upload");
    if (uploadBtn) uploadBtn.title = t("button.upload", "Attach");
    const emojiBtn = document.getElementById("kwc-emoji");
    if (emojiBtn) emojiBtn.title = t("button.emoji", "Emoji");
    const commandBtn = document.getElementById("kwc-command");
    if (commandBtn) commandBtn.title = t("button.commands", "Commands");
    const jumpBtn = document.getElementById("kwc-jump-latest");
    if (jumpBtn) jumpBtn.title = t("button.jumpLatest", "Jump to latest");
    const jumpLabel = document.getElementById("kwc-jump-latest-label");
    if (jumpLabel) jumpLabel.textContent = t("button.jumpLatest", "Jump to latest");
    const historyEnd = ensureHistoryEndNotice(document.getElementById("kwc-messages"));
    if (historyEnd) historyEnd.textContent = t("history.end", "No more messages to display.");
    const pipBtn = document.getElementById("kwc-pip");
    if (pipBtn) pipBtn.title = t("button.pip", "PIP");
    const guest = document.getElementById("kwc-guest-name");
    if (guest) guest.placeholder = t("placeholder.guestName", "Guest name");
    const captcha = document.getElementById("kwc-captcha-a");
    if (captcha) captcha.placeholder = t("placeholder.captchaAnswer", "answer");
    const input = document.getElementById("kwc-message");
    if (input) input.placeholder = t("placeholder.message", "message");
    const resize = document.getElementById("kwc-resize-handle");
    if (resize) resize.title = t("button.resize", "Resize");
    const dropTitle = document.getElementById("kwc-drop-title");
    if (dropTitle) dropTitle.textContent = t("upload.dropTitle", "Drop files to upload");
    const dropSubtitle = document.getElementById("kwc-drop-subtitle");
    if (dropSubtitle) dropSubtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    renderPinnedBar();
    updateLoginState();
  }

  function headerAccountDisplayName(value, maxCodePoints = 16) {
    const text = String(value || "");
    const chars = Array.from(text);
    const max = Math.max(1, Number(maxCodePoints) || 16);
    return chars.length > max ? chars.slice(0, max).join("") + "…" : text;
  }

  function updateLoginState() {
    const btn = document.getElementById("kwc-login");
    const status = document.getElementById("kwc-status");
    const guestRow = document.getElementById("kwc-guest-row");
    const dmBtn = document.getElementById("kwc-dm");
    const groupBtn = document.getElementById("kwc-group");
    const gameBtn = document.getElementById("kwc-game");
    const uploadBtn = document.getElementById("kwc-upload");
    const emojiBtn = document.getElementById("kwc-emoji");
    const commandBtn = document.getElementById("kwc-command");
    if (!btn || !status) return;
    status.classList.remove("kwc-status-role-ADMIN", "kwc-status-role-MODERATOR", "kwc-status-role-USER", "kwc-status-role-GUEST");

    const loggedIn = authenticatedSession();
    const moderationEnabled = !state.config || state.config.moderationEnabled !== false;
    const canManageMutes = moderationEnabled && loggedIn && (state.role === "ADMIN" || (state.role === "MODERATOR" && (!state.config || state.config.allowModeratorGuestMute !== false)));
    // The people counter doubles as the signed-in user list for every account.
    // Operator-only panels are filtered separately inside the modal.
    const canUseAdminPanel = loggedIn;
    if (dmBtn) dmBtn.classList.toggle("kwc-hidden", !(loggedIn && state.directMessageEnabled) || state.minimized);
    if (groupBtn) groupBtn.classList.toggle("kwc-hidden", !(loggedIn && state.groupChatEnabled) || state.minimized);
    if (gameBtn) gameBtn.classList.toggle("kwc-hidden", !loggedIn || state.minimized);
    updateDirectMessageButton();
    updateGroupChatButton();
    if (uploadBtn) uploadBtn.classList.toggle("kwc-hidden", !canUpload());
    updateEmojiButton();
    updateDirectMessageComposeControls();
    updateCommandButton();
    updatePipButton();
    updateNotificationInboxButton();

    btn.classList.remove("kwc-login-user", "kwc-user-role-ADMIN", "kwc-user-role-MODERATOR", "kwc-user-role-USER", "kwc-user-role-GUEST");
    if (loggedIn) {
      const accountButtonName = String(state.username || t("status.loggedIn", "User"));
      btn.textContent = headerAccountDisplayName(accountButtonName, 16);
      btn.title = `${accountButtonName} · ${t("preferences.title", "Chat settings")}`;
      btn.classList.add("kwc-login-user", "kwc-user-role-" + String(state.role || "USER"));
      const loggedInCount = Math.max(0, Number(state.loggedInCount || 0));
      status.innerHTML = `${kwcFaIcon("user", "kwc-status-icon")} <span>${esc(String(loggedInCount))}</span>`;
      status.title = canUseAdminPanel ? `${t("button.admin", "Admin")} · ${fmt("status.loggedInCount", "{count} logged in", {count: loggedInCount})}` : fmt("status.loggedInCount", "{count} logged in", {count: loggedInCount});
      status.classList.add("kwc-status-role-" + String(state.role || "USER"));
      status.classList.toggle("kwc-status-admin-action", !!canUseAdminPanel);
      if (canUseAdminPanel) {
        status.setAttribute("role", "button");
        status.setAttribute("tabindex", "0");
        status.onclick = event => { event.preventDefault(); event.stopPropagation(); if (!state.minimized) openAdminModal(); };
        status.onkeydown = event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); if (!state.minimized) openAdminModal(); } };
      } else {
        status.removeAttribute("role");
        status.removeAttribute("tabindex");
        status.onclick = null;
        status.onkeydown = null;
      }
      if (guestRow) guestRow.classList.add("kwc-hidden");
    } else {
      btn.title = t("button.login", "Login");
      btn.textContent = t("button.login", "Login");
      status.textContent = t("status.guest", "guest");
      status.title = "";
      status.classList.remove("kwc-status-admin-action");
      status.removeAttribute("role");
      status.removeAttribute("tabindex");
      status.onclick = null;
      status.onkeydown = null;
      const allowGuestName = !state.config || state.config.guestAllowCustomName !== false;
      if (guestRow) guestRow.classList.toggle("kwc-hidden", !allowGuestName || guestChatHidden());
    }
    scheduleResponsiveHeaderLayout();
    if (state.messages && state.messages.length) scheduleVirtualRender();
  }
