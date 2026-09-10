// [KWC 유지보수 주석 / KWC maintenance notes]
// 사용자 채팅 설정 preset, account preference UI, 공개 메시지 검색과 검색 결과 이동 기능을 담당한다.
// This fragment owns chat-setting presets, account preference UI, public-message search, and navigation to search results.
// localStorage 기반 화면 설정과 서버 계정에 저장되는 알림/typing/presence 설정은 수명이 다르므로 저장 위치를 섞지 않는다.
// localStorage visual settings and server-side account notification/typing/presence settings have different lifetimes and must not be mixed.
// 검색 결과 이동은 현재 virtual-scroll window에 대상이 없을 수 있으므로 필요하면 history-around API로 대상 주변 페이지를 다시 구성한다.
// A search target may not exist in the current virtual-scroll window, so navigation can rebuild the window around the target through the history-around API.

  function optionListHtml(items, current) {
    return (items || []).map(item => `<option value="${esc(item.value)}"${String(item.value || "") === String(current || "") ? " selected" : ""}>${esc(item.label)}</option>`).join("");
  }


  const CHAT_SETTING_PRESETS_KEY = "kwc.chatSettingPresets";
  const CHAT_SETTING_PRESET_STORAGE_KEYS = [
    "kwc.userTheme", "kwc.userOpacity", "kwc.userFontSize", "kwc.userFontFamily",
    "kwc.userTextColor", "kwc.userUiTextColor", "kwc.userTextShadowMode", "kwc.userTextShadowCustom",
    "kwc.userBackgroundColor", "kwc.userInputBackgroundColor", "kwc.language",
    "kwc.senderIdentityMode", "kwc.timeDisplayMode", "kwc.dmConversationFocus",
    "kwc.emojiPanelHeightPx", "kwc.windowWidth", "kwc.windowHeight",
    "kwc.minimized", NOTIFICATION_ENABLED_KEY,
    "kwc.notify.normalChat", "kwc.notify.dm", "kwc.notify.groupChat", "kwc.notify.mentions",
    "kwc.notify.replies", "kwc.notify.reactions", "kwc.notify.system", "kwc.notify.keywords", "kwc.notify.keywords.list",
    "kwc.parentFramePosition", "kwc.parentUserPrefsModalPos", "kwc.localUserPrefsModalPos"
  ];

  function readLocalStorageValue(key) {
    try { return localStorage.getItem(key); } catch (_) { return null; }
  }

  function writeLocalStorageValue(key, value) {
    try {
      if (value === null || value === undefined) localStorage.removeItem(key);
      else localStorage.setItem(key, String(value));
    } catch (_) {}
  }

  function collectChatSettingPresetStorage() {
    const out = {};
    CHAT_SETTING_PRESET_STORAGE_KEYS.forEach(key => { out[key] = readLocalStorageValue(key); });
    return out;
  }

  function applyChatSettingPresetStorage(storage) {
    if (!storage || typeof storage !== "object") return;
    CHAT_SETTING_PRESET_STORAGE_KEYS.forEach(key => {
      if (!Object.prototype.hasOwnProperty.call(storage, key)) return;
      let value = storage[key];
      if (key === NOTIFICATION_KEYWORDS_KEY && isPollutedNotificationKeywordText(value)) value = "";
      writeLocalStorageValue(key, value);
    });
    if (Object.prototype.hasOwnProperty.call(storage, LEGACY_NOTIFICATION_KEYWORDS_KEY)
        && !Object.prototype.hasOwnProperty.call(storage, NOTIFICATION_KEYWORDS_KEY)) {
      const legacyKeywords = storage[LEGACY_NOTIFICATION_KEYWORDS_KEY];
      writeLocalStorageValue(NOTIFICATION_KEYWORDS_KEY, isPollutedNotificationKeywordText(legacyKeywords) ? "" : legacyKeywords);
    }
    if (Object.prototype.hasOwnProperty.call(storage, "kwc.minimized")) {
      state.minimized = localStorage.getItem("kwc.minimized") === "1";
    }
    state.senderIdentityMode = localStorage.getItem("kwc.senderIdentityMode") === "real" ? "real" : "display";
    state.timeDisplayMode = localStorage.getItem("kwc.timeDisplayMode") === "full" ? "full" : "short";
    state.dmConversationFocus = localStorage.getItem("kwc.dmConversationFocus") === "1";
    const emojiHeight = Number(localStorage.getItem("kwc.emojiPanelHeightPx") || state.emojiPanelHeightPx || 180);
    if (Number.isFinite(emojiHeight)) state.emojiPanelHeightPx = Math.max(56, Math.min(420, emojiHeight));
    applyWindowSizeConfig();
    updateFrameSize();
    const migratedNotificationEnabled = readLegacyNotificationEnabledFromStorage(storage);
    if (migratedNotificationEnabled !== null) setNotificationsEnabledLocal(migratedNotificationEnabled);
    if (notificationsEnabledLocal()) ensurePreferredWebPush().catch(() => {});
    else disableWebPush().catch(() => {});
    scheduleTypingIndicatorRefresh();
  }

  function loadChatSettingPresets() {
    try {
      const raw = localStorage.getItem(CHAT_SETTING_PRESETS_KEY) || "[]";
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.filter(item => item && typeof item === "object" && String(item.name || "").trim()) : [];
    } catch (_) {
      return [];
    }
  }

  function saveChatSettingPresets(list) {
    try {
      localStorage.setItem(CHAT_SETTING_PRESETS_KEY, JSON.stringify(Array.isArray(list) ? list.slice(0, 20) : []));
      return true;
    } catch (_) {
      return false;
    }
  }

  function currentChatSettingPresetData() {
    const storage = collectChatSettingPresetStorage();
    const opts = currentNotificationOptions();
    Object.keys(opts).forEach(name => {
      const def = notificationOptionDef(name);
      if (def) storage[def.key] = opts[name] ? "1" : "0";
    });
    return {
      version: 2,
      theme: savedUserTheme(),
      opacity: savedUserOpacity(),
      fontSize: savedUserFontSize(),
      fontFamily: savedUserFontFamily(),
      textColor: savedUserTextColor(),
      uiTextColor: savedUserUiTextColor(),
      textShadowMode: savedUserTextShadowMode(),
      textShadowCustom: savedUserTextShadowCustom(),
      backgroundColor: savedUserBackgroundColor(),
      inputBackgroundColor: savedUserInputBackgroundColor(),
      language: savedUserLanguage(),
      storage
    };
  }

  function applyNullablePreference(value, setter, resetter) {
    if (value === null || value === undefined || value === "") resetter();
    else setter(value);
  }

  function applyChatSettingPresetData(data) {
    data = data || {};
    if (data.storage && typeof data.storage === "object") applyChatSettingPresetStorage(data.storage);
    if (Object.prototype.hasOwnProperty.call(data, "theme")) {
      const theme = String(data.theme || "");
      if (theme) localStorage.setItem("kwc.userTheme", normalizedTheme(theme));
      else localStorage.removeItem("kwc.userTheme");
    }
    applyNullablePreference(data.opacity, setUserOpacity, resetUserOpacity);
    applyNullablePreference(data.fontSize, setUserFontSize, resetUserFontSize);
    applyNullablePreference(data.fontFamily, setUserFontFamily, resetUserFontFamily);
    applyNullablePreference(data.textColor, setUserTextColor, resetUserTextColor);
    applyNullablePreference(data.uiTextColor, setUserUiTextColor, resetUserUiTextColor);
    applyNullablePreference(data.textShadowMode, setUserTextShadowMode, resetUserTextShadowMode);
    applyNullablePreference(data.textShadowCustom, setUserTextShadowCustom, resetUserTextShadowCustom);
    applyNullablePreference(data.backgroundColor, setUserBackgroundColor, resetUserBackgroundColor);
    applyNullablePreference(data.inputBackgroundColor, setUserInputBackgroundColor, resetUserInputBackgroundColor);
    applyNullablePreference(data.language, setUserLanguage, resetUserLanguage);
    applyFontSizeConfig();
    applyThemeConfig();
    refreshRenderedMessagesForLocale();
    scheduleVirtualRender({preserveScroll: true, stickToBottom: false, deferDuringScroll: false});
  }

  function cleanPresetSaveButtonLabel(value) {
    const text = String(value || "Save")
      .replace(/^\s*(?:현재|Current)\s+/i, "")
      .replace(/\s+(?:현재|current)\s*$/i, "")
      .trim();
    return text || "Save";
  }

  const USER_PROFILE_SELECTED_KEY = "kwc.userProfileId";

  function serverUserProfilesActive() {
    return !!(state.token && state.userProfilesEnabled && state.userProfilesMaxProfiles > 0);
  }

  function selectedAccountProfileId() {
    try { return localStorage.getItem(USER_PROFILE_SELECTED_KEY) || ""; } catch (_) { return ""; }
  }

  function setSelectedAccountProfileId(id) {
    try {
      if (id) localStorage.setItem(USER_PROFILE_SELECTED_KEY, String(id));
      else localStorage.removeItem(USER_PROFILE_SELECTED_KEY);
    } catch (_) {}
  }

  async function loadAccountProfiles() {
    if (!serverUserProfilesActive() || state.accountProfilesLoading) return state.accountProfiles || [];
    state.accountProfilesLoading = true;
    try {
      const res = await api("/preferences/profiles");
      state.userProfilesEnabled = res && res.enabled === true;
      state.userProfilesMaxProfiles = Math.max(0, Math.min(20, Math.floor(Number(res && res.maxProfiles) || 0)));
      state.userProfilesAllowImportExport = !res || res.allowImportExport !== false;
      state.accountProfiles = Array.isArray(res && res.profiles) ? res.profiles : [];
      const selected = selectedAccountProfileId();
      if (selected && !state.accountProfiles.some(item => String(item && item.id || "") === selected)) setSelectedAccountProfileId("");
      return state.accountProfiles;
    } catch (_) {
      return state.accountProfiles || [];
    } finally {
      state.accountProfilesLoading = false;
    }
  }

  function currentAccountProfileData(name, id = "") {
    return {
            id: String(id || ""),
      name: String(name || "").trim(),
      theme: savedUserTheme(),
      // Account profiles must preserve the same explicit-vs-unset state as local
      // chat-setting presets. Saving effective server defaults here made a profile
      // change font size/opacity when loaded on another server/device.
      opacity: savedUserOpacity(),
      fontSize: savedUserFontSize(),
      fontFamily: savedUserFontFamily(),
      textColor: savedUserTextColor(),
      uiTextColor: savedUserUiTextColor(),
      textShadowMode: savedUserTextShadowMode(),
      textShadowCustom: savedUserTextShadowCustom(),
      backgroundColor: savedUserBackgroundColor(),
      inputBackgroundColor: savedUserInputBackgroundColor(),
      language: savedUserLanguage()
    };
  }

  async function saveAccountProfile(id, name) {
    const res = await api("/preferences/profile/save", {method: "POST", body: JSON.stringify(currentAccountProfileData(name, id))});
    if (!res || res.ok === false || !res.profile) throw new Error(res && res.error || "profile_save_failed");
    await loadAccountProfiles();
    setSelectedAccountProfileId(res.profile.id || "");
    return res.profile;
  }

  function applyAccountProfile(profile) {
    if (!profile || typeof profile !== "object") return false;
    applyChatSettingPresetData(profile);
    setSelectedAccountProfileId(profile.id || "");
    return true;
  }

  async function deleteAccountProfile(id) {
    const res = await api("/preferences/profile/delete", {method: "POST", body: JSON.stringify({id: String(id || "")})});
    if (!res || res.ok === false) throw new Error(res && res.error || "profile_delete_failed");
    if (selectedAccountProfileId() === String(id || "")) setSelectedAccountProfileId("");
    await loadAccountProfiles();
    return true;
  }

  function safeProfileDownloadName(name) {
    const base = String(name || "profile").replace(/[\\/:*?"<>|\x00-\x1f]/g, "_").trim().slice(0, 80) || "profile";
    return "KWC-profile-" + base + ".json";
  }

  async function fetchAccountProfileExport(id) {
    if (!state.userProfilesAllowImportExport) throw new Error("profile_export_disabled");
    const res = await api("/preferences/profile/export?id=" + encodeURIComponent(String(id || "")));
    if (!res || res.ok === false || typeof res.json !== "string") throw new Error(res && res.error || "profile_export_failed");
    const profile = (state.accountProfiles || []).find(item => String(item && item.id || "") === String(id || ""));
    return {json: res.json, name: profile && profile.name || "profile"};
  }

  async function exportAccountProfile(id) {
    const exported = await fetchAccountProfileExport(id);
    const blob = new Blob([exported.json], {type: "application/json;charset=utf-8"});
    const url = URL.createObjectURL(blob);
    try {
      const a = document.createElement("a");
      a.href = url;
      a.download = safeProfileDownloadName(exported.name);
      document.body.appendChild(a);
      a.click();
      a.remove();
    } finally {
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
  }

  async function importAccountProfileJson(json) {
    if (!state.userProfilesAllowImportExport) throw new Error("profile_import_disabled");
    const text = String(json || "");
    if (new TextEncoder().encode(text).length > 16 * 1024) throw new Error("profile_import_too_large");
    const res = await api("/preferences/profile/import", {method: "POST", body: JSON.stringify({profileJson: text})});
    if (!res || res.ok === false || !res.profile) throw new Error(res && res.error || "profile_import_failed");
    await loadAccountProfiles();
    setSelectedAccountProfileId(res.profile.id || "");
    return res.profile;
  }

  function accountProfileOptionsHtml(selected = "") {
    const list = Array.isArray(state.accountProfiles) ? state.accountProfiles : [];
    const current = String(selected || "");
    const createOption = `<option value=""${current ? "" : " selected"}>${esc(t("preferences.presetNew", "New profile"))}</option>`;
    if (!list.length) return createOption;
    return createOption + list.map(item => {
      const id = String(item && item.id || "");
      const name = String(item && item.name || "").trim();
      return `<option value="${esc(id)}" ${id === current ? "selected" : ""}>${esc(name)}</option>`;
    }).join("");
  }

  function chatSettingPresetOptionsHtml(selected = "") {
    if (serverUserProfilesActive()) return accountProfileOptionsHtml(selected || selectedAccountProfileId());
    const list = loadChatSettingPresets();
    if (!list.length) return `<option value="">${esc(t("preferences.presetEmpty", "No saved settings."))}</option>`;
    return list.map(item => {
      const name = String(item.name || "").trim();
      return `<option value="${esc(name)}" ${name === selected ? "selected" : ""}>${esc(name)}</option>`;
    }).join("");
  }

  const USER_PREF_SECTION_STATE_KEY = "kwc.userPrefsSectionsOpen";

  function readUserPrefSectionsOpen() {
    try {
      const raw = localStorage.getItem(USER_PREF_SECTION_STATE_KEY) || "{}";
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch (_) {
      return {};
    }
  }

  function userPrefSectionOpen(name) {
    const map = readUserPrefSectionsOpen();
    return map[String(name || "")] === true;
  }

  function setUserPrefSectionOpen(name, open) {
    const key = String(name || "");
    if (!key) return;
    const map = readUserPrefSectionsOpen();
    map[key] = open === true;
    try { localStorage.setItem(USER_PREF_SECTION_STATE_KEY, JSON.stringify(map)); } catch (_) {}
  }

  function bindUserPrefSectionPersistence(root) {
    if (!root) return;
    root.querySelectorAll("details[data-kwc-pref-section]").forEach(details => {
      const name = details.getAttribute("data-kwc-pref-section") || "";
      details.open = userPrefSectionOpen(name);
      details.addEventListener("toggle", () => setUserPrefSectionOpen(name, details.open));
    });
  }

  function openLocalUserPreferencesModal(payload) {
    const old = document.getElementById("kwc-user-prefs-modal");
    if (old) old.remove();
    const labels = payload.labels || {};
    const includeDragNote = !state.isPip;
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    wrap.id = "kwc-user-prefs-modal";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-user-prefs-modal">
        <div class="kwc-modal-head">
          <h3>${esc(labels.title || "Chat settings")}</h3>
          <div class="kwc-modal-head-actions">
            <button class="kwc-button" id="kwc-prefs-reset">${esc(labels.reset || "Reset")}</button>
            <button class="kwc-button" id="kwc-prefs-close">${esc(labels.close || "Close")}</button>
          </div>
        </div>

        <div class="kwc-user-prefs-scroll">
        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="languageTheme">
          <summary>${esc(labels.languageAndTheme || "Language and theme")}</summary>
          <label class="kwc-pref-label"><span>${esc(labels.language || "Language")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-language">${optionListHtml(payload.languageOptions, payload.language)}</select>
          <label class="kwc-pref-label"><span>${esc(labels.theme || "Theme")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-theme">${optionListHtml(payload.themeOptions, payload.theme)}</select>
          <p class="kwc-pref-font-help">${esc(labels.themeResetNote || "Changing the theme resets visual chat settings to the theme defaults.")}</p>
        </details>

        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="window">
          <summary>${esc(labels.windowSettings || "Window settings")}</summary>
          <label class="kwc-pref-label"><span>${esc(labels.opacity || "Opacity")}</span><strong id="kwc-prefs-opacity-value">${Math.round(payload.opacityPercent || 100)}%</strong></label>
          <input class="kwc-pref-range" id="kwc-prefs-opacity" type="range" min="10" max="100" step="1" value="${Math.round(payload.opacityPercent || 100)}">
          <div class="kwc-pref-hints"><span>10%</span><span>100%</span></div>
          <label class="kwc-pref-label"><span>${esc(labels.backgroundColor || "Background color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-background-color" type="color" value="${esc(payload.backgroundColor || "#121216")}"></label>
          <label class="kwc-pref-label"><span>${esc(labels.inputBackgroundColor || "Input background color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-input-background-color" type="color" value="${esc(payload.inputBackgroundColor || "#000000")}"></label>
        </details>

        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="font">
          <summary>${esc(labels.fontSettings || "Font settings")}</summary>
          <label class="kwc-pref-label"><span>${esc(labels.fontFamily || "Font")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-font-family">${optionListHtml(payload.fontOptions, payload.fontFamily)}</select>
          <label class="kwc-pref-label"><span>${esc(labels.fontCustom || "Custom font")}</span></label>
          <div class="kwc-pref-inline-row">
            <input class="kwc-input" id="kwc-prefs-font-family-custom" type="text" value="${esc(payload.fontFamily || "")}" placeholder="${esc(labels.fontCustomPlaceholder || "Installed font name or CSS font-family")}">
            <button class="kwc-button" id="kwc-prefs-font-family-test" type="button">${esc(labels.fontTest || "Test")}</button>
            <button class="kwc-button" id="kwc-prefs-font-family-apply" type="button">${esc(labels.fontApply || "Apply")}</button>
          </div>
          <p class="kwc-pref-font-help">${preferencesFontHelpHtml(labels, esc)}</p>
          <p class="kwc-pref-font-status" id="kwc-prefs-font-family-status" aria-live="polite"></p>
          <label class="kwc-pref-label"><span>${esc(labels.fontSize || "Font size")}</span><strong id="kwc-prefs-font-size-value">${pxLabel(payload.fontSizePx || 13)}</strong></label>
          <input class="kwc-pref-range" id="kwc-prefs-font-size" type="range" min="8" max="36" step="0.1" value="${formatDecimalNumber(payload.fontSizePx || 13, 2)}">
          <div class="kwc-pref-hints"><span>8px</span><span>36px</span></div>
          <label class="kwc-pref-label"><span>${esc(labels.textColor || "Message text color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-text-color" type="color" value="${esc(payload.textColor || "#ffffff")}"></label>
          <label class="kwc-pref-label"><span>${esc(labels.uiTextColor || "UI text color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-ui-text-color" type="color" value="${esc(payload.uiTextColor || "#ffffff")}"></label>
          <label class="kwc-pref-label"><span>${esc(labels.textShadow || "Text shadow")}</span></label>
          <select class="kwc-input kwc-pref-select" id="kwc-prefs-text-shadow-mode">
            <option value="none" ${payload.textShadowMode === "none" ? "selected" : ""}>${esc(labels.textShadowNone || "None")}</option>
            <option value="auto" ${payload.textShadowMode === "auto" ? "selected" : ""}>${esc(labels.textShadowAuto || "Auto")}</option>
            <option value="dark" ${payload.textShadowMode === "dark" ? "selected" : ""}>${esc(labels.textShadowDark || "Dark shadow")}</option>
            <option value="light" ${payload.textShadowMode === "light" ? "selected" : ""}>${esc(labels.textShadowLight || "Light shadow")}</option>
            <option value="custom" ${payload.textShadowMode === "custom" ? "selected" : ""}>${esc(labels.textShadowCustom || "Custom")}</option>
          </select>
          <div class="kwc-shadow-custom-panel" id="kwc-prefs-text-shadow-custom-panel">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomColor || "Shadow color")}</span><input class="kwc-pref-color-input" id="kwc-prefs-text-shadow-color" type="color"></label>
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomX || "X offset")}</span><strong id="kwc-prefs-text-shadow-x-value">0px</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-x" type="range" min="-12" max="12" step="0.1">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomY || "Y offset")}</span><strong id="kwc-prefs-text-shadow-y-value">1px</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-y" type="range" min="-12" max="12" step="0.1">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomBlur || "Blur")}</span><strong id="kwc-prefs-text-shadow-blur-value">2px</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-blur" type="range" min="0" max="24" step="0.1">
            <label class="kwc-pref-label"><span>${esc(labels.textShadowCustomOpacity || "Opacity")}</span><strong id="kwc-prefs-text-shadow-opacity-value">85%</strong></label>
            <input class="kwc-pref-range" id="kwc-prefs-text-shadow-opacity" type="range" min="0" max="100" step="1">
            <div class="kwc-shadow-preview" id="kwc-prefs-text-shadow-preview">${esc(labels.textShadowCustomPreview || "Shadow preview")}</div>
          </div>
        </details>

        <details class="kwc-pref-section kwc-pref-collapsible" data-kwc-pref-section="notifications">
          <summary>${esc(labels.notifications || "Notifications")}</summary>
          <p class="kwc-pref-font-help" id="kwc-prefs-notifications-help">${esc(unifiedNotificationHelpText(labels))}</p>
          <div class="kwc-pref-button-row kwc-pref-notification-actions">
            <button class="kwc-button" id="kwc-prefs-notifications-toggle" type="button">${esc(notificationsEnabledLocal() ? (labels.notificationsDisable || "Disable notifications") : (labels.notificationsEnable || "Enable notifications"))}</button>
            <button class="kwc-button" id="kwc-prefs-notifications-test" type="button">${esc(labels.notificationsTest || "Test notification")}</button>
          </div>
          <p class="kwc-pref-font-help" id="kwc-prefs-notifications-status" aria-live="polite">${esc(unifiedNotificationStatusText(labels))}</p>
          <label class="kwc-pref-label"><span>${esc(labels.notifyTypes || "Notification types")}</span></label>
          ${notificationOptionsHtml("kwc-prefs-notify", labels)}
          <label class="kwc-pref-label"><span>${esc(labels.notifyKeywordsList || "Keyword alert words")}</span></label>
          <textarea class="kwc-input kwc-pref-textarea" id="kwc-prefs-notify-keywords-list" rows="3" placeholder="keyword1, keyword2">${esc(notificationKeywordsText())}</textarea>
          <div class="kwc-pref-button-row kwc-pref-keyword-actions">
            <button class="kwc-button" id="kwc-prefs-notify-keywords-apply" type="button">${esc(labels.notifyKeywordsApply || "Apply keywords")}</button>
            <span class="kwc-pref-inline-status" id="kwc-prefs-notify-keywords-status" aria-live="polite"></span>
          </div>
          <p class="kwc-pref-font-help">${esc(labels.notifyKeywordsHelp || "Comma or line separated. Signed-in users share this keyword list across their KWC account and all registered Web Push devices.")}</p>
        </details>

        <div class="kwc-pref-section kwc-pref-static">
          <div class="kwc-pref-section-title">${esc(labels.presets || "Saved chat settings")}</div>
          <div class="kwc-preset-row kwc-preset-row-stacked">
            <select class="kwc-input kwc-pref-select" id="kwc-prefs-preset-select">${chatSettingPresetOptionsHtml("")}</select>
            <div class="kwc-preset-actions kwc-preset-actions-primary">
              <button class="kwc-button" id="kwc-prefs-preset-save" type="button">${esc(cleanPresetSaveButtonLabel(labels.presetSave || "Save"))}</button>
              <button class="kwc-button" id="kwc-prefs-preset-load" type="button">${esc(labels.presetLoad || "Load")}</button>
              <button class="kwc-button" id="kwc-prefs-preset-delete" type="button">${esc(labels.presetDelete || "Delete")}</button>
            </div>
            ${payload.serverProfilesEnabled && payload.serverProfilesAllowImportExport ? `<div class="kwc-preset-actions kwc-preset-actions-secondary"><button class="kwc-button" id="kwc-prefs-preset-export" type="button">${esc(labels.presetExport || "Export")}</button><button class="kwc-button" id="kwc-prefs-preset-import" type="button">${esc(labels.presetImport || "Import")}</button></div>` : ""}
          </div>
          <p class="kwc-pref-font-help">${esc(payload.serverProfilesEnabled ? (labels.presetServerHelp || "Signed-in users can save multiple chat-setting profiles to their KWC account and load them on other devices.") : (labels.presetLocalHelp || "Guest presets are stored only in this browser."))}</p>
          <p class="kwc-pref-font-help" id="kwc-prefs-preset-status" aria-live="polite"></p>
        </div>

        <p class="kwc-opacity-note">${preferencesNoteHtml(labels, includeDragNote, esc)}</p>
        </div>
      </div>
    `;
    document.body.appendChild(wrap);
    makeModalDraggable(wrap, "kwc.localUserPrefsModalPos");
    bindUserPrefSectionPersistence(wrap);

    const themeSelect = wrap.querySelector("#kwc-prefs-theme");
    const opacityInput = wrap.querySelector("#kwc-prefs-opacity");
    const opacityValue = wrap.querySelector("#kwc-prefs-opacity-value");
    const sizeInput = wrap.querySelector("#kwc-prefs-font-size");
    const sizeValue = wrap.querySelector("#kwc-prefs-font-size-value");
    const familySelect = wrap.querySelector("#kwc-prefs-font-family");
    const familyCustomInput = wrap.querySelector("#kwc-prefs-font-family-custom");
    const familyApplyButton = wrap.querySelector("#kwc-prefs-font-family-apply");
    const familyTestButton = wrap.querySelector("#kwc-prefs-font-family-test");
    const familyStatus = wrap.querySelector("#kwc-prefs-font-family-status");
    const textColorInput = wrap.querySelector("#kwc-prefs-text-color");
    const uiTextColorInput = wrap.querySelector("#kwc-prefs-ui-text-color");
    const textShadowModeInput = wrap.querySelector("#kwc-prefs-text-shadow-mode");
    const textShadowCustomPanel = wrap.querySelector("#kwc-prefs-text-shadow-custom-panel");
    const textShadowColorInput = wrap.querySelector("#kwc-prefs-text-shadow-color");
    const textShadowXInput = wrap.querySelector("#kwc-prefs-text-shadow-x");
    const textShadowYInput = wrap.querySelector("#kwc-prefs-text-shadow-y");
    const textShadowBlurInput = wrap.querySelector("#kwc-prefs-text-shadow-blur");
    const textShadowOpacityInput = wrap.querySelector("#kwc-prefs-text-shadow-opacity");
    const textShadowXValue = wrap.querySelector("#kwc-prefs-text-shadow-x-value");
    const textShadowYValue = wrap.querySelector("#kwc-prefs-text-shadow-y-value");
    const textShadowBlurValue = wrap.querySelector("#kwc-prefs-text-shadow-blur-value");
    const textShadowOpacityValue = wrap.querySelector("#kwc-prefs-text-shadow-opacity-value");
    const textShadowPreview = wrap.querySelector("#kwc-prefs-text-shadow-preview");
    const backgroundColorInput = wrap.querySelector("#kwc-prefs-background-color");
    const inputBackgroundColorInput = wrap.querySelector("#kwc-prefs-input-background-color");
    const notifyKeywordsInput = wrap.querySelector("#kwc-prefs-notify-keywords-list");
    const notifyKeywordsApply = wrap.querySelector("#kwc-prefs-notify-keywords-apply");
    const notifyKeywordsStatus = wrap.querySelector("#kwc-prefs-notify-keywords-status");
    const languageSelect = wrap.querySelector("#kwc-prefs-language");
    const notificationsToggle = wrap.querySelector("#kwc-prefs-notifications-toggle");
    const notificationsTest = wrap.querySelector("#kwc-prefs-notifications-test");
    const typingDisplayInput = wrap.querySelector("#kwc-prefs-typing-display");
    const notificationsStatus = wrap.querySelector("#kwc-prefs-notifications-status");
    const presetSelect = wrap.querySelector("#kwc-prefs-preset-select");
    const presetNameInput = null;
    const presetSave = wrap.querySelector("#kwc-prefs-preset-save");
    const presetLoad = wrap.querySelector("#kwc-prefs-preset-load");
    const presetDelete = wrap.querySelector("#kwc-prefs-preset-delete");
    const presetExport = wrap.querySelector("#kwc-prefs-preset-export");
    const presetImport = wrap.querySelector("#kwc-prefs-preset-import");
    const presetStatus = wrap.querySelector("#kwc-prefs-preset-status");
    const setPresetStatus = message => { if (presetStatus) presetStatus.textContent = message || ""; };

    const close = () => { if (wrap.__kwcDragCleanup) wrap.__kwcDragCleanup(); wrap.remove(); state.prefsModalOpen = false; };
    wrap.querySelector("#kwc-prefs-close").onclick = close;
    wrap.addEventListener("click", e => { if (e.target === wrap) close(); });

    if (themeSelect) themeSelect.addEventListener("change", () => setUserTheme(themeSelect.value));

    opacityInput.addEventListener("input", () => {
      const v = Math.max(10, Math.min(100, Number(opacityInput.value) || payload.defaultOpacityPercent || 100));
      opacityValue.textContent = Math.round(v) + "%";
      setUserOpacity(v / 100, false);
    });
    opacityInput.addEventListener("change", () => setUserOpacity((Number(opacityInput.value) || payload.defaultOpacityPercent || 100) / 100, true));
    opacityInput.addEventListener("pointerup", () => setUserOpacity((Number(opacityInput.value) || payload.defaultOpacityPercent || 100) / 100, true));

    sizeInput.addEventListener("input", () => {
      const v = Math.max(8, Math.min(36, Number(sizeInput.value) || payload.defaultFontSizePx || 13));
      sizeValue.textContent = pxLabel(v);
      setUserFontSize(v, false);
    });
    sizeInput.addEventListener("change", () => setUserFontSize(Number(sizeInput.value) || payload.defaultFontSizePx || 13, true));
    sizeInput.addEventListener("pointerup", () => setUserFontSize(Number(sizeInput.value) || payload.defaultFontSizePx || 13, true));

    familySelect.addEventListener("change", () => {
      if (familyCustomInput) familyCustomInput.value = familySelect.value;
      setUserFontFamily(familySelect.value);
    });
    const fontStatusText = status => {
      const name = status && status.name ? status.name : "";
      if (!status || status.state === "empty") return "";
      if (status.state === "detected") return fmt("preferences.fontDetected", "Detected in this browser: {name}", {name});
      if (status.state === "notDetected") return t("preferences.fontNotDetected", "Not detected. Check the font family name or install the font on this device.");
      if (status.state === "generic") return fmt("preferences.fontGeneric", "Generic CSS family: {name}", {name});
      return t("preferences.fontUnknown", "Could not test this font in this browser.");
    };
    const updateFontStatus = () => {
      if (!familyStatus) return;
      const value = familyCustomInput ? familyCustomInput.value : (familySelect ? familySelect.value : "");
      const status = fontDetectionStatus(value);
      familyStatus.textContent = fontStatusText(status);
      familyStatus.dataset.state = status.state || "";
    };
    const applyCustomFont = () => {
      setUserFontFamily(familyCustomInput ? familyCustomInput.value : "");
      updateFontStatus();
    };
    if (familyTestButton) familyTestButton.addEventListener("click", updateFontStatus);
    if (familyApplyButton) familyApplyButton.addEventListener("click", applyCustomFont);
    if (familyCustomInput) familyCustomInput.addEventListener("keydown", event => {
      if (event.key !== "Enter" || event.isComposing) return;
      event.preventDefault();
      updateFontStatus();
    });
    if (familyCustomInput) familyCustomInput.addEventListener("input", () => {
      if (familyStatus) familyStatus.textContent = "";
    });
    textColorInput.addEventListener("input", () => setUserTextColor(textColorInput.value));
    uiTextColorInput.addEventListener("input", () => setUserUiTextColor(uiTextColorInput.value));
    const readShadowParts = () => ({
      color: textShadowColorInput ? textShadowColorInput.value : "#000000",
      x: textShadowXInput ? Number(textShadowXInput.value) : 0,
      y: textShadowYInput ? Number(textShadowYInput.value) : 1,
      blur: textShadowBlurInput ? Number(textShadowBlurInput.value) : 2,
      opacity: textShadowOpacityInput ? Number(textShadowOpacityInput.value) : 85
    });
    const syncShadowControls = (parts = parseTextShadowParts(payload.textShadowCustom)) => {
      if (textShadowColorInput) textShadowColorInput.value = parts.color;
      if (textShadowXInput) textShadowXInput.value = formatDecimalNumber(parts.x, 2);
      if (textShadowYInput) textShadowYInput.value = formatDecimalNumber(parts.y, 2);
      if (textShadowBlurInput) textShadowBlurInput.value = formatDecimalNumber(parts.blur, 2);
      if (textShadowOpacityInput) textShadowOpacityInput.value = String(parts.opacity);
      if (textShadowXValue) textShadowXValue.textContent = pxLabel(parts.x);
      if (textShadowYValue) textShadowYValue.textContent = pxLabel(parts.y);
      if (textShadowBlurValue) textShadowBlurValue.textContent = pxLabel(parts.blur);
      if (textShadowOpacityValue) textShadowOpacityValue.textContent = parts.opacity + "%";
      const css = buildTextShadowFromParts(parts);
      if (textShadowPreview) textShadowPreview.style.textShadow = css;
    };
    const updateShadowCustomFromControls = () => {
      const parts = readShadowParts();
      syncShadowControls(parts);
      if (textShadowModeInput && textShadowModeInput.value !== "custom") {
        textShadowModeInput.value = "custom";
        setUserTextShadowMode("custom");
      }
      setUserTextShadowCustom(buildTextShadowFromParts(parts));
    };
    const updateShadowPanelVisibility = () => {
      if (textShadowCustomPanel) textShadowCustomPanel.classList.toggle("kwc-hidden", !textShadowModeInput || textShadowModeInput.value !== "custom");
    };
    syncShadowControls(parseTextShadowParts(payload.textShadowCustom));
    updateShadowPanelVisibility();
    if (textShadowModeInput) textShadowModeInput.addEventListener("change", () => {
      setUserTextShadowMode(textShadowModeInput.value);
      updateShadowPanelVisibility();
    });
    [textShadowColorInput, textShadowXInput, textShadowYInput, textShadowBlurInput, textShadowOpacityInput].forEach(input => {
      if (input) input.addEventListener("input", updateShadowCustomFromControls);
    });
    backgroundColorInput.addEventListener("input", () => setUserBackgroundColor(backgroundColorInput.value));
    inputBackgroundColorInput.addEventListener("input", () => setUserInputBackgroundColor(inputBackgroundColorInput.value));
    languageSelect.addEventListener("change", () => setUserLanguage(languageSelect.value));

    if (typingDisplayInput) typingDisplayInput.addEventListener("change", async () => {
      const wanted = typingDisplayInput.checked;
      const ok = await setAccountTypingDisplayEnabled(wanted);
      typingDisplayInput.checked = ok ? (state.typingDisplayEnabled !== false) : !wanted;
    });

    const updateNotificationStatuses = () => {
      if (notificationsStatus) notificationsStatus.textContent = unifiedNotificationStatusText(labels);
      if (notificationsToggle) notificationsToggle.textContent = notificationsEnabledLocal() ? (labels.notificationsDisable || "Disable notifications") : (labels.notificationsEnable || "Enable notifications");
    };
    updateNotificationStatuses();
    bindNotificationOptionInputs(wrap, () => {
      if (notificationsEnabledLocal()) enableWebPush().catch(() => {});
      updateNotificationStatuses();
    });
    const updateNotifyKeywordOptionInputs = () => {
      wrap.querySelectorAll("[data-kwc-notify-option]").forEach(input => {
        const name = input.dataset.kwcNotifyOption;
        input.disabled = !notificationServerAllows(name);
        input.checked = notificationOption(name);
      });
    };
    const applyNotifyKeywords = async () => {
      if (!notifyKeywordsInput) return;
      const keywordText = notifyKeywordsInput.value || "";
      setNotificationKeywordsText(keywordText);
      const keywordAllowed = notificationServerAllows("keywords");
      if (keywordText.trim() && keywordAllowed) setNotificationOption("keywords", true);
      updateNotifyKeywordOptionInputs();
      if (notificationsEnabledLocal()) await enableWebPush();
      updateNotificationStatuses();
      if (notifyKeywordsStatus) notifyKeywordsStatus.textContent = keywordText.trim() && !keywordAllowed
        ? (labels.notifyDisabledByServer || "Disabled by server configuration.")
        : (labels.notifyKeywordsSaved || "Keyword alerts saved.");
    };
    if (notifyKeywordsInput) {
      notifyKeywordsInput.addEventListener("input", () => {
        setNotificationKeywordsText(notifyKeywordsInput.value || "");
        if (notifyKeywordsStatus) notifyKeywordsStatus.textContent = labels.notifyKeywordsNeedsApply || "Keyword list changed. Tap Apply keywords to update push filtering.";
      });
      notifyKeywordsInput.addEventListener("change", () => setNotificationKeywordsText(notifyKeywordsInput.value || ""));
      notifyKeywordsInput.addEventListener("blur", () => setNotificationKeywordsText(notifyKeywordsInput.value || ""));
    }
    if (notifyKeywordsApply) notifyKeywordsApply.addEventListener("click", () => applyNotifyKeywords().catch(() => {
      if (notifyKeywordsStatus) notifyKeywordsStatus.textContent = labels.presetSaveFailed || "Save failed. Browser storage may be blocked.";
    }));
    if (notificationsToggle) notificationsToggle.addEventListener("click", async () => {
      if (notificationsEnabledLocal()) {
        setNotificationsEnabledLocal(false);
        await disableWebPush();
      } else {
        const ok = await requestBrowserNotifications();
        if (ok) {
          setNotificationsEnabledLocal(true);
          if (canUseWebPush()) await enableWebPush();
        }
      }
      updateNotificationStatuses();
    });
    if (notificationsTest) notificationsTest.addEventListener("click", async () => {
      const ok = await requestBrowserNotifications();
      if (ok) {
        setNotificationsEnabledLocal(true);
        if (notificationUsesMobilePushUi() && canUseWebPush()) await testWebPush();
        else showBrowserNotification(configuredNotificationTitle(), labels.notificationsTest || "Test notification", {tag: "kwc-test", force: true});
      }
      updateNotificationStatuses();
    });

    const refreshPresetSelect = selected => {
      if (!presetSelect) return;
      presetSelect.innerHTML = chatSettingPresetOptionsHtml(selected || "");
    };
    if (presetSave) presetSave.addEventListener("click", async () => {
      const selectedValue = String((presetSelect && presetSelect.value) || "").trim();
      const currentServer = serverUserProfilesActive() ? (state.accountProfiles || []).find(item => String(item && item.id || "") === selectedValue) : null;
      const suggested = currentServer ? String(currentServer.name || "") : selectedValue;
      const name = String(window.prompt(labels.presetNamePrompt || labels.presetName || "Preset name", suggested) || "").trim();
      if (!name) return;
      if (themeSelect) {
        const theme = String(themeSelect.value || "");
        if (theme) localStorage.setItem("kwc.userTheme", normalizedTheme(theme));
        else localStorage.removeItem("kwc.userTheme");
      }
      if (opacityInput) setUserOpacity((Number(opacityInput.value) || payload.defaultOpacityPercent || 100) / 100, true);
      if (sizeInput) setUserFontSize(Number(sizeInput.value) || payload.defaultFontSizePx || 13, true);
      if (familyCustomInput) setUserFontFamily(familyCustomInput.value);
      if (textColorInput) setUserTextColor(textColorInput.value);
      if (uiTextColorInput) setUserUiTextColor(uiTextColorInput.value);
      if (textShadowModeInput) setUserTextShadowMode(textShadowModeInput.value);
      if (textShadowModeInput && textShadowModeInput.value === "custom") setUserTextShadowCustom(buildTextShadowFromParts(readShadowParts()));
      if (backgroundColorInput) setUserBackgroundColor(backgroundColorInput.value);
      if (inputBackgroundColorInput) setUserInputBackgroundColor(inputBackgroundColorInput.value);
      if (languageSelect) setUserLanguage(languageSelect.value);
      if (serverUserProfilesActive()) {
        try {
          const profile = await saveAccountProfile(selectedValue, name);
          refreshPresetSelect(profile.id || "");
          setPresetStatus((labels.presetSaved || "Saved.") + " " + name);
        } catch (e) {
          setPresetStatus((labels.presetSaveFailed || "Save failed.") + " " + String(e && e.message || ""));
        }
        return;
      }
      const list = loadChatSettingPresets();
      const data = currentChatSettingPresetData();
      const existing = list.findIndex(item => String(item.name || "") === name);
      const item = {name, savedAt: Date.now(), data};
      if (existing >= 0) list[existing] = item;
      else list.unshift(item);
      if (!saveChatSettingPresets(list)) {
        setPresetStatus(labels.presetSaveFailed || "Save failed. Browser storage may be blocked.");
        return;
      }
      refreshPresetSelect(name);
      setPresetStatus((labels.presetSaved || "Saved.") + " " + name);
    });
    if (presetLoad) presetLoad.addEventListener("click", () => {
      const value = String(presetSelect && presetSelect.value || "").trim();
      if (!value) return alert(labels.presetSelectRequired || "Select saved settings first.");
      if (serverUserProfilesActive()) {
        const item = (state.accountProfiles || []).find(p => String(p && p.id || "") === value);
        if (!item) return alert(labels.presetSelectRequired || "Select saved settings first.");
        applyAccountProfile(item);
        wrap.remove();
        state.prefsModalOpen = false;
        openUserPreferencesModal(true);
        return;
      }
      const item = loadChatSettingPresets().find(p => String(p.name || "") === value);
      if (!item) return alert(labels.presetSelectRequired || "Select saved settings first.");
      applyChatSettingPresetData(item.data || {});
      wrap.remove();
      state.prefsModalOpen = false;
      openUserPreferencesModal(true);
    });
    if (presetDelete) presetDelete.addEventListener("click", async () => {
      const value = String(presetSelect && presetSelect.value || "").trim();
      if (!value) return alert(labels.presetSelectRequired || "Select saved settings first.");
      const currentServer = serverUserProfilesActive() ? (state.accountProfiles || []).find(p => String(p && p.id || "") === value) : null;
      const displayName = currentServer ? String(currentServer.name || "") : value;
      const message = (labels.presetConfirmDelete || "Delete saved settings {name}?").replace("{name}", displayName);
      if (!confirmPlain(message)) return;
      if (serverUserProfilesActive()) {
        try {
          await deleteAccountProfile(value);
          refreshPresetSelect("");
          setPresetStatus((labels.presetDeleted || "Deleted.") + " " + displayName);
        } catch (e) {
          setPresetStatus((labels.presetSaveFailed || "Save failed.") + " " + String(e && e.message || ""));
        }
        return;
      }
      if (!saveChatSettingPresets(loadChatSettingPresets().filter(p => String(p.name || "") !== value))) {
        setPresetStatus(labels.presetSaveFailed || "Save failed. Browser storage may be blocked.");
        return;
      }
      refreshPresetSelect("");
      setPresetStatus((labels.presetDeleted || "Deleted.") + " " + displayName);
    });
    if (presetExport) presetExport.addEventListener("click", async () => {
      const id = String(presetSelect && presetSelect.value || "").trim();
      if (!id) return alert(labels.presetSelectRequired || "Select saved settings first.");
      try { await exportAccountProfile(id); }
      catch (e) { setPresetStatus((labels.presetExportFailed || "Export failed.") + " " + String(e && e.message || "")); }
    });
    if (presetImport) {
      presetImport.addEventListener("click", () => {
        // Do not keep a file input inside the settings modal. Some map frontends
        // override the HTML hidden/display rules for form controls and exposed the
        // browser's native "Choose file" control next to the preset buttons.
        // A short-lived off-screen picker cannot affect the modal layout.
        const picker = document.createElement("input");
        picker.type = "file";
        picker.accept = "application/json,.json";
        picker.tabIndex = -1;
        picker.setAttribute("aria-hidden", "true");
        picker.style.cssText = "position:fixed;left:-10000px;top:-10000px;width:1px;height:1px;opacity:0;pointer-events:none;";
        const cleanup = () => { try { picker.remove(); } catch (_) {} };
        picker.addEventListener("cancel", cleanup, {once: true});
        picker.addEventListener("change", async () => {
          const file = picker.files && picker.files[0];
          if (!file) { cleanup(); return; }
          if (file.size > 16 * 1024) {
            setPresetStatus((labels.presetImportFailed || "Import failed.") + " profile_import_too_large");
            cleanup();
            return;
          }
          try {
            const profile = await importAccountProfileJson(await file.text());
            refreshPresetSelect(profile.id || "");
            setPresetStatus((labels.presetSaved || "Saved.") + " " + String(profile.name || ""));
          } catch (e) {
            setPresetStatus((labels.presetImportFailed || "Import failed.") + " " + String(e && e.message || ""));
          } finally {
            cleanup();
          }
        }, {once: true});
        document.body.appendChild(picker);
        picker.click();
      });
    }

    wrap.querySelector("#kwc-prefs-reset").onclick = () => {
      resetUserOpacity();
      resetUserFontSize();
      resetUserFontFamily();
      if (familySelect) familySelect.value = "";
      if (familyCustomInput) familyCustomInput.value = "";
      resetUserTextColor();
      resetUserUiTextColor();
      resetUserTextShadowMode();
      resetUserTextShadowCustom();
      if (textShadowModeInput) textShadowModeInput.value = payload.textShadowMode || "auto";
      syncShadowControls(parseTextShadowParts(payload.textShadowCustom));
      updateShadowPanelVisibility();
      resetUserBackgroundColor();
      resetUserInputBackgroundColor();
      localStorage.removeItem("kwc.userTheme");
      resetUserLanguage();
      wrap.remove();
      state.prefsModalOpen = false;
    };
  }

  async function openUserPreferencesModal(force = false) {
    if (state.prefsModalOpen && !force) return;
    state.prefsModalOpen = true;
    // Refresh the public config when the user opens Chat settings so an admin
    // change to user-display-control is visible immediately across sessions.
    await loadConfig();
    if (!state.config || state.config.uiUserPreferencesControl === false) {
      state.prefsModalOpen = false;
      return;
    }
    if (state.token) {
      await loadAccountTypingPreferences();
      await loadAccountPresencePreferences();
    }
    if (serverUserProfilesActive()) await loadAccountProfiles();
    const payload = buildUserPreferencesPayload();
    if (state.isPip || window.parent === window) {
      openLocalUserPreferencesModal(payload);
      return;
    }
    postFrame("openUserPreferences", payload);
  }



  function searchResultPreviewText(msg) {
    const text = plainLegacyText(plainDisplayMessageText(msg)).replace(/\s+/g, " ").trim();
    if (text.length <= 180) return text;
    return text.slice(0, 177) + "...";
  }

  function renderSearchResults(container, messages) {
    if (!container) return;
    messages = (Array.isArray(messages) ? messages : []).filter(msg => !isPersonallyBlockedMessage(msg));
    if (messages.length === 0) {
      container.innerHTML = `<div class="kwc-search-status">${t("search.noResults", "No matching messages.")}</div>`;
      return;
    }
    container.innerHTML = messages.map(msg => {
      const id = esc(msg.id || "");
      const sender = esc(stripMinecraftColorCodes(displaySender(msg) || ""));
      const time = esc(formatMessageTime(msg.time || Date.now()));
      const source = esc(msg.source || "");
      const preview = esc(searchResultPreviewText(msg));
      return `<button type="button" class="kwc-search-result" data-id="${id}">
        <span class="kwc-search-result-meta"><strong>${sender}</strong> <span>${time}</span> <span>${source}</span></span>
        <span class="kwc-search-result-preview">${preview}</span>
      </button>`;
    }).join("");
  }

  function syncDetachedModalThemeVariables() {
    // DM/group/pinned/settings windows are detached from #kwc-root. They receive a
    // snapshot of CSS variables when opened, so a loaded profile previously changed
    // the main chat but left already-open detached chat windows at the old font/theme.
    document.querySelectorAll(".kwc-modal-backdrop").forEach(backdrop => {
      try { applyDetachedModalTheme(backdrop); } catch (_) {}
    });
  }

  function applyDetachedModalTheme(backdrop) {
    const root = document.getElementById("kwc-root");
    if (!root || !backdrop) return;
    const cs = getComputedStyle(root);
    const vars = [
      "--kwc-font-size", "--kwc-message-font-size", "--kwc-input-font-size", "--kwc-button-font-size", "--kwc-badge-font-size", "--kwc-typing-font-size",
      "--kwc-chat-font-family", "--kwc-chat-message-font-size", "--kwc-chat-text-color", "--kwc-chat-ui-text-color", "--kwc-chat-background-color", "--kwc-chat-text-shadow",
      "--kwc-text-color", "--kwc-ui-color", "--kwc-ui-text-color", "--kwc-muted-color", "--kwc-border-color", "--kwc-button-text",
      "--kwc-button-bg", "--kwc-button-hover-bg", "--kwc-input-bg", "--kwc-compose-input-bg", "--kwc-link-color",
      "--kwc-modal-bg-rgb", "--kwc-panel-bg-rgb", "--kwc-panel-opacity", "--kwc-shadow-color",
      "--kwc-surface-bg", "--kwc-surface-strong-bg", "--kwc-surface-hover-bg", "--kwc-admin-list-bg", "--kwc-admin-row-bg", "--kwc-text-shadow", "--kwc-ui-text-shadow",
      "--kwc-emoji-render-size", "--kwc-emoji-picker-size", "--kwc-emoji-panel-height", "--kwc-emoji-panel-min-height"
    ];
    vars.forEach(name => {
      const value = cs.getPropertyValue(name);
      if (value) backdrop.style.setProperty(name, value.trim());
    });
    backdrop.style.fontFamily = cs.fontFamily || "";
    ["kwc-theme-light", "kwc-theme-dark", "kwc-theme-system", "kwc-theme-high-contrast"].forEach(cls => {
      backdrop.classList.toggle(cls, root.classList.contains(cls));
    });
  }

  function applySearchModalTheme(backdrop) {
    applyDetachedModalTheme(backdrop);
  }

  function currentSearchLanguage() {
    return String(state.selectedLanguage || localStorage.getItem("kwc.language") || "").trim();
  }

  function searchEnabled() {
    const c = state.config || {};
    return c.searchEnabled !== false;
  }

  function configuredSearchResultLimit() {
    const c = state.config || {};
    const raw = Number(c.searchResultLimit);
    const limit = Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : 50;
    return Math.max(1, limit);
  }

  function searchDateMillis(input) {
    const raw = String(input && input.value || "").trim();
    if (!raw) return "";
    const time = new Date(raw).getTime();
    return Number.isFinite(time) ? String(time) : "";
  }

  function searchSourceValue(select) {
    const value = String(select && select.value || "").trim().toLowerCase();
    return ["game", "web", "discord", "system"].includes(value) ? value : "";
  }

  function openSearchModal() {
    if (!searchEnabled()) return;
    if (state.searchModalOpen) {
      const existing = document.querySelector(".kwc-search-modal-backdrop");
      const input = existing && existing.querySelector("#kwc-search-query");
      if (input) input.focus();
      return;
    }
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-search-modal-backdrop";
    applySearchModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-search-modal" role="dialog" aria-modal="true" aria-label="${t("search.title", "Search messages")}">
        <div class="kwc-search-head">
          <h3>${t("search.title", "Search messages")}</h3>
          <button class="kwc-button kwc-search-x" id="kwc-search-close-x" type="button" aria-label="${t("button.close", "Close")}">×</button>
        </div>
        <div class="kwc-search-row">
          <input class="kwc-input" id="kwc-search-query" maxlength="120" placeholder="${t("search.placeholder", "Search message text or sender")}">
          <button class="kwc-button" id="kwc-search-run" type="button">${t("button.search", "Search")}</button>
        </div>
        <details class="kwc-search-options" id="kwc-search-options">
          <summary>${t("search.options", "Options")}</summary>
          <div class="kwc-search-options-grid">
            <label><span>${t("search.from", "From")}</span><input class="kwc-input" id="kwc-search-from" type="datetime-local"></label>
            <label><span>${t("search.to", "To")}</span><input class="kwc-input" id="kwc-search-to" type="datetime-local"></label>
            <label><span>${t("search.sender", "Sender")}</span><input class="kwc-input" id="kwc-search-sender" maxlength="64" placeholder="${t("search.senderPlaceholder", "Optional sender")}"></label>
            <label><span>${t("search.source", "Source")}</span><select class="kwc-input" id="kwc-search-source">
              <option value="">${t("search.sourceAll", "All")}</option>
              <option value="game">${t("search.sourceGame", "Game")}</option>
              <option value="web">${t("search.sourceWeb", "Web")}</option>
              <option value="discord">${t("search.sourceDiscord", "Discord")}</option>
              <option value="system">${t("search.sourceSystem", "System/Event")}</option>
            </select></label>
          </div>
          <label class="kwc-search-check"><input id="kwc-search-include-system" type="checkbox" checked> <span>${t("search.includeSystem", "Include system/event messages")}</span></label>
        </details>
        <div class="kwc-search-status" id="kwc-search-status"></div>
        <div class="kwc-search-results" id="kwc-search-results"></div>
        <div class="kwc-search-footer">
          <button class="kwc-button" id="kwc-search-close" type="button">${t("button.close", "Close")}</button>
        </div>
      </div>
    `;
    mountWindowOwnedOverlay(wrap, publicChatWindowOwner());
    state.searchModalOpen = true;

    const input = wrap.querySelector("#kwc-search-query");
    const run = wrap.querySelector("#kwc-search-run");
    const close = wrap.querySelector("#kwc-search-close");
    const closeX = wrap.querySelector("#kwc-search-close-x");
    const status = wrap.querySelector("#kwc-search-status");
    const results = wrap.querySelector("#kwc-search-results");
    const fromInput = wrap.querySelector("#kwc-search-from");
    const toInput = wrap.querySelector("#kwc-search-to");
    const senderInput = wrap.querySelector("#kwc-search-sender");
    const sourceSelect = wrap.querySelector("#kwc-search-source");
    const includeSystemInput = wrap.querySelector("#kwc-search-include-system");

    const closeModal = () => {
      if (wrap.parentNode) wrap.remove();
      state.searchModalOpen = false;
    };

    const stopMapEvent = event => {
      event.stopPropagation();
    };
    ["click", "dblclick", "mousedown", "mouseup", "pointerdown", "pointerup", "pointermove", "touchstart", "touchmove", "touchend", "wheel", "keydown", "keyup", "keypress"].forEach(type => {
      wrap.addEventListener(type, stopMapEvent, false);
    });
    wrap.addEventListener("click", event => {
      if (event.target === wrap) closeModal();
    });

    const doSearch = async () => {
      const query = String(input && input.value || "").trim();
      const from = searchDateMillis(fromInput);
      const to = searchDateMillis(toInput);
      const sender = String(senderInput && senderInput.value || "").trim();
      const source = searchSourceValue(sourceSelect);
      const includeSystem = !includeSystemInput || includeSystemInput.checked;
      const hasFilter = !!(from || to || sender || source || !includeSystem);
      if (!query && !hasFilter) {
        if (results) results.innerHTML = "";
        if (status) status.textContent = t("search.enterQueryOrFilter", "Enter a search term or choose at least one option.");
        if (input) input.focus();
        return;
      }
      if (status) status.textContent = t("search.searching", "Searching...");
      if (results) results.innerHTML = "";
      if (run) run.disabled = true;
      try {
        const lang = currentSearchLanguage();
        const limit = configuredSearchResultLimit();
        const params = new URLSearchParams();
        if (query) params.set("q", query);
        params.set("limit", String(limit));
        if (lang) params.set("lang", lang);
        if (from) params.set("from", from);
        if (to) params.set("to", to);
        if (sender) params.set("sender", sender);
        if (source) params.set("source", source);
        if (!includeSystem) params.set("includeSystem", "false");
        const data = await api(`/history/search?${params.toString()}`, {timeoutMs: 15000});
        const messages = Array.isArray(data && data.messages) ? data.messages : [];
        if (status) status.textContent = messages.length ? fmt("search.resultCount", "{count} results", {count: messages.length}) : "";
        renderSearchResults(results, messages);
      } catch (_) {
        if (status) status.textContent = t("search.failed", "Search failed.");
      } finally {
        if (run) run.disabled = false;
      }
    };

    if (run) run.addEventListener("click", doSearch);
    if (close) close.addEventListener("click", closeModal);
    if (closeX) closeX.addEventListener("click", closeModal);
    [input, fromInput, toInput, senderInput].forEach(el => {
      if (!el) return;
      el.addEventListener("keydown", event => {
        if (event.key === "Enter" && !event.isComposing) {
          event.preventDefault();
          doSearch();
        } else if (event.key === "Escape") {
          event.preventDefault();
          closeModal();
        }
      });
    });
    if (sourceSelect) sourceSelect.addEventListener("keydown", event => {
      if (event.key === "Escape") {
        event.preventDefault();
        closeModal();
      }
    });
    if (results) results.addEventListener("click", event => {
      const item = event.target && event.target.closest ? event.target.closest(".kwc-search-result[data-id]") : null;
      if (!item) return;
      const id = item.dataset.id || "";
      closeModal();
      if (id) jumpToReplyTarget(id);
    });
    setTimeout(() => { if (input) input.focus(); }, 0);
  }


  function privateSearchPreviewText(msg, type = "dm") {
    const raw = type === "group" && String(msg && msg.eventType || "")
      ? groupMembershipEventText(msg)
      : String(msg && msg.body || "");
    const text = plainLegacyText(raw).replace(/\s+/g, " ").trim();
    if (text.length <= 180) return text;
    return text.slice(0, 177) + "...";
  }

  function renderPrivateSearchResults(container, messages, type = "dm") {
    if (!container) return;
    messages = (Array.isArray(messages) ? messages : []).filter(msg => !isPersonallyBlockedMessage(msg));
    if (messages.length === 0) {
      container.innerHTML = `<div class="kwc-search-status">${t("search.noResults", "No matching messages.")}</div>`;
      return;
    }
    container.innerHTML = messages.map(msg => {
      const id = esc(msg.id || "");
      const sender = esc(directMessagePlainLabel(msg.senderDisplayName || msg.senderUsername || msg.senderUuid || ""));
      const time = esc(formatMessageTime(msg.time || Date.now()));
      const preview = esc(privateSearchPreviewText(msg, type));
      return `<button type="button" class="kwc-search-result" data-id="${id}">
        <span class="kwc-search-result-meta"><strong>${sender}</strong> <span>${time}</span></span>
        <span class="kwc-search-result-preview">${preview}</span>
      </button>`;
    }).join("");
  }

  async function jumpToPrivateSearchTarget(messageId, type = "dm", expectedContextId = "") {
    const id = Number(messageId || 0);
    const contextId = type === "group" ? String(state.groupActiveRoomId || "") : String(state.dmActiveThreadId || "");
    if (!(id > 0) || !contextId || (expectedContextId && contextId !== expectedContextId)) return;
    const loaded = (type === "group" ? state.groupMessages : state.dmMessages) || [];
    if (loaded.some(msg => Number(msg && msg.id || 0) === id)) {
      await jumpToPrivateReplyTarget(id, type);
      return;
    }
    const limit = privateMessagePageLimit();
    const before = id + 1;
    try {
      if (type === "group") {
        const res = await api(`/group/messages?roomId=${encodeURIComponent(contextId)}&before=${encodeURIComponent(String(before))}&limit=${encodeURIComponent(String(limit))}`, {timeoutMs: 15000});
        if (String(state.groupActiveRoomId || "") !== contextId) return;
        const page = Array.isArray(res && res.messages) ? res.messages : [];
        if (!page.some(msg => Number(msg && msg.id || 0) === id)) throw new Error("target_not_found");
        state.groupMessages = page;
        state.groupMessagesHasMore = page.length >= limit;
        renderGroupChatMessages(page, {stickToBottom: false});
      } else {
        const res = await api(`/dm/messages?threadId=${encodeURIComponent(contextId)}&before=${encodeURIComponent(String(before))}&limit=${encodeURIComponent(String(limit))}`, {timeoutMs: 15000});
        if (String(state.dmActiveThreadId || "") !== contextId) return;
        const page = Array.isArray(res && res.messages) ? res.messages : [];
        if (!page.some(msg => Number(msg && msg.id || 0) === id)) throw new Error("target_not_found");
        state.dmMessages = page;
        state.dmMessagesHasMore = page.length >= limit;
        renderDirectMessageMessages(page, {stickToBottom: false});
        state.dmUnread = Number(res && res.unread || state.dmUnread || 0);
        updateDirectMessageButton();
      }
      await jumpToPrivateReplyTarget(id, type);
    } catch (_) {
      alert(t("reply.notFound", "The referenced message could not be found."));
    }
  }

  function openPrivateMessageSearchModal(type = "dm") {
    type = type === "group" ? "group" : "dm";
    if (!searchEnabled() || !state.token) return;
    if ((type === "dm" && state.dmAuditMode) || (type === "group" && state.groupAuditMode)) return;
    const contextId = type === "group" ? String(state.groupActiveRoomId || "") : String(state.dmActiveThreadId || "");
    if (!contextId) return;
    const existing = document.querySelector(".kwc-private-search-modal-backdrop");
    if (existing) existing.remove();

    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-search-modal-backdrop kwc-private-search-modal-backdrop";
    applySearchModalTheme(wrap);
    const scopeLabel = type === "group"
      ? groupRoomLabel(state.groupActiveRoom || {})
      : directMessageHeaderPlainLabel((state.dmThreads || []).find(item => item.id === contextId) || state.dmDraftTarget || {}, "");
    wrap.innerHTML = `
      <div class="kwc-modal kwc-search-modal" role="dialog" aria-modal="true" aria-label="${esc(t("search.title", "Search messages"))}">
        <div class="kwc-search-head"><div><h3>${esc(t("search.title", "Search messages"))}</h3><div class="kwc-private-search-scope">${esc(scopeLabel)}</div></div><button class="kwc-button kwc-search-x" id="kwc-private-search-close-x" type="button" aria-label="${esc(t("button.close", "Close"))}">×</button></div>
        <div class="kwc-search-row"><input class="kwc-input" id="kwc-private-search-query" maxlength="120" placeholder="${esc(t("search.placeholder", "Search message text or sender"))}"><button class="kwc-button" id="kwc-private-search-run" type="button">${esc(t("button.search", "Search"))}</button></div>
        <details class="kwc-search-options"><summary>${esc(t("search.options", "Options"))}</summary><div class="kwc-search-options-grid">
          <label><span>${esc(t("search.from", "From"))}</span><input class="kwc-input" id="kwc-private-search-from" type="datetime-local"></label>
          <label><span>${esc(t("search.to", "To"))}</span><input class="kwc-input" id="kwc-private-search-to" type="datetime-local"></label>
          <label><span>${esc(t("search.sender", "Sender"))}</span><input class="kwc-input" id="kwc-private-search-sender" maxlength="64" placeholder="${esc(t("search.senderPlaceholder", "Optional sender"))}"></label>
        </div>${type === "group" ? `<label class="kwc-search-check"><input id="kwc-private-search-include-system" type="checkbox" checked> <span>${esc(t("search.includeSystem", "Include system/event messages"))}</span></label>` : ""}</details>
        <div class="kwc-search-status" id="kwc-private-search-status"></div><div class="kwc-search-results" id="kwc-private-search-results"></div>
        <div class="kwc-search-footer"><button class="kwc-button" id="kwc-private-search-close" type="button">${esc(t("button.close", "Close"))}</button></div>
      </div>`;
    mountPrivateWindowOwnedOverlay(type, wrap);
    const input = wrap.querySelector("#kwc-private-search-query");
    const run = wrap.querySelector("#kwc-private-search-run");
    const status = wrap.querySelector("#kwc-private-search-status");
    const results = wrap.querySelector("#kwc-private-search-results");
    const fromInput = wrap.querySelector("#kwc-private-search-from");
    const toInput = wrap.querySelector("#kwc-private-search-to");
    const senderInput = wrap.querySelector("#kwc-private-search-sender");
    const includeSystemInput = wrap.querySelector("#kwc-private-search-include-system");
    const closeModal = () => { if (wrap.parentNode) wrap.remove(); };
    ["click","dblclick","mousedown","mouseup","pointerdown","pointerup","pointermove","touchstart","touchmove","touchend","wheel","keydown","keyup","keypress"].forEach(name => wrap.addEventListener(name, event => event.stopPropagation(), false));
    wrap.addEventListener("click", event => { if (event.target === wrap) closeModal(); });
    const doSearch = async () => {
      if ((type === "group" ? String(state.groupActiveRoomId || "") : String(state.dmActiveThreadId || "")) !== contextId) { closeModal(); return; }
      const query = String(input && input.value || "").trim();
      const from = searchDateMillis(fromInput);
      const to = searchDateMillis(toInput);
      const sender = String(senderInput && senderInput.value || "").trim();
      const includeSystem = !includeSystemInput || includeSystemInput.checked;
      const hasFilter = !!(from || to || sender || (type === "group" && !includeSystem));
      if (!query && !hasFilter) {
        results.innerHTML = "";
        status.textContent = t("search.enterQueryOrFilter", "Enter a search term or choose at least one option.");
        input.focus();
        return;
      }
      status.textContent = t("search.searching", "Searching...");
      results.innerHTML = "";
      run.disabled = true;
      try {
        const params = new URLSearchParams();
        params.set(type === "group" ? "roomId" : "threadId", contextId);
        params.set("limit", String(configuredSearchResultLimit()));
        if (query) params.set("q", query);
        if (from) params.set("from", from);
        if (to) params.set("to", to);
        if (sender) params.set("sender", sender);
        if (type === "group" && !includeSystem) params.set("includeSystem", "false");
        const data = await api(`/${type === "group" ? "group" : "dm"}/search?${params.toString()}`, {timeoutMs: 15000});
        const messages = Array.isArray(data && data.messages) ? data.messages : [];
        status.textContent = messages.length ? fmt("search.resultCount", "{count} results", {count: messages.length}) : "";
        renderPrivateSearchResults(results, messages, type);
      } catch (_) {
        status.textContent = t("search.failed", "Search failed.");
      } finally { run.disabled = false; }
    };
    run.addEventListener("click", doSearch);
    wrap.querySelector("#kwc-private-search-close").addEventListener("click", closeModal);
    wrap.querySelector("#kwc-private-search-close-x").addEventListener("click", closeModal);
    [input, fromInput, toInput, senderInput].forEach(el => el && el.addEventListener("keydown", event => {
      if (event.key === "Enter" && !event.isComposing) { event.preventDefault(); doSearch(); }
      else if (event.key === "Escape") { event.preventDefault(); closeModal(); }
    }));
    results.addEventListener("click", event => {
      const item = event.target && event.target.closest ? event.target.closest(".kwc-search-result[data-id]") : null;
      if (!item) return;
      const id = item.dataset.id || "";
      closeModal();
      if (id) jumpToPrivateSearchTarget(id, type, contextId);
    });
    setTimeout(() => input && input.focus(), 0);
  }

