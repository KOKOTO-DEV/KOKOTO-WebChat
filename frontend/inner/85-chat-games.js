// [KWC 유지보수 주석 / KWC maintenance notes]
// 이벤트는 ID가 있는 목록으로 관리되고 공지 링크는 eventId + originServerId를 보존해
// Relay 공지를 눌렀을 때 현재 서버가 아니라 원본 서버의 정확한 이벤트를 조회한다.
// Events are managed as an ID-addressable list. Announcement links preserve eventId + originServerId
// so a relayed notice opens the exact event on its origin server.

  function chatGameLocalePrefix() {
    const raw = String(state.selectedLanguage || localStorage.getItem("kwc.language") || (state.config && state.config.language) || navigator.language || "en-US").toLowerCase();
    if (raw.startsWith("ko")) return "ko";
    if (raw.startsWith("ja")) return "ja";
    if (raw.startsWith("zh")) return "zh";
    return "en";
  }

  function chatGameLocalizedText(key, fallbacks) {
    const lang = chatGameLocalePrefix();
    const translated = state.lang && state.lang[key];
    if (translated != null && String(translated).trim()) {
      const text = String(translated);
      // Some historical non-English bundles stored the English built-in value.
      // Treat only that exact old built-in as stale; custom wording stays intact.
      const englishBuiltin = String(fallbacks && fallbacks.en || "");
      if (lang === "en" || !englishBuiltin || text.trim() !== englishBuiltin.trim()) return text;
    }
    return String((fallbacks && (fallbacks[lang] || fallbacks.en)) || key);
  }

  function chatGameDeletedNotice() {
    return chatGameLocalizedText("game.deletedNotice", {
      en:"This event has been deleted and can no longer be opened.",
      ko:"삭제되어 더 이상 열 수 없는 이벤트입니다.",
      ja:"このイベントは削除されたため、開くことができません。",
      zh:"此活动已被删除，无法再打开。"
    });
  }

  function chatGameDeletedLabel() {
    return chatGameLocalizedText("game.deleted", {en:"Deleted event", ko:"삭제된 이벤트", ja:"削除済みイベント", zh:"已删除活动"});
  }

  function chatGameTypeFieldLabel() {
    return chatGameLocalizedText("game.type", {en:"Type", ko:"방식", ja:"方式", zh:"类型"});
  }

  function chatGameDisplayCapacity(game) {
    if (!game) return 0;
    const type = String(game.type || "").toLowerCase();
    if (type === "firstcome") return Math.max(0, Number(game.winnerCount || 0));
    if (type === "poll") return Math.max(0, Number(game.voteCount || 0));
    return Math.max(0, Number(game.maxParticipants || 0));
  }

  function chatGameListMetric(game) {
    const type = String(game && game.type || "").toLowerCase();
    const participants = Array.isArray(game && game.participants) ? game.participants : [];
    if (type === "poll") return fmt("game.voteCount", "{count} votes", {count:Number(game && game.voteCount || 0)});
    if (type === "recruitment") return fmt("game.recruitmentMetric", "{accepted}/{capacity} accepted · {waiting} waiting", {
      accepted:Number(game && game.acceptedCount || 0), capacity:Number(game && game.maxParticipants || 0), waiting:Number(game && game.waitingCount || 0)
    });
    return `${participants.length}/${chatGameDisplayCapacity(game)}`;
  }

  function chatGameErrorCode(error) {
    if (error && typeof error === "object") {
      const responseCode = error.response && typeof error.response === "object" ? String(error.response.error || "").trim() : "";
      if (responseCode) return responseCode;
      const directCode = String(error.error || "").trim();
      if (directCode) return directCode;
      const message = String(error.message || "").trim();
      if (message && !/^HTTP\s+\d+$/i.test(message)) return message;
      return message;
    }
    return String(error || "").trim();
  }

  function chatGameErrorText(error) {
    const key = chatGameErrorCode(error);
    const fallbacks = {
      game_not_found:"There is no matching event.", game_not_open:"This event is not open.", game_full:"This event is full.",
      not_enough_participants:"There are not enough participants.", not_lottery:"This event is not a lottery.",
      invalid_type:"Choose first come, lottery, poll, or recruitment.", invalid_title:"Enter a title.",
      invalid_participant_count:"Enter at least 2 participants.",
      invalid_winner_count:"Winner count must be between 1 and the participant count.",
      invalid_auto_end_time:"Choose a future automatic end date/time.", invalid_auto_end_count:"Automatic-end response count must be between 1 and 500, or disabled.",
      invalid_poll_options:"Add at least two unique poll options.", invalid_poll_option:"Choose a valid poll option.", not_poll:"This event is not a poll.", vote_required:"Use a poll option to vote.",
      invalid_recruitment_roles:"Add at least one role as role:count.", invalid_recruitment_role:"Choose a valid recruitment role.", not_recruitment:"This event is not recruitment.", role_required:"Choose a recruitment role.", not_applied:"You have not applied to this recruitment event.",
      remote_server_unavailable:"The origin server is unavailable.", game_relay_unavailable:"The origin server does not support event lookup.",
      remote_game_manage_not_allowed:"Remote events must be managed on their origin server.",
      permission_denied:"You do not have permission to manage this event."
    };
    return t("game.error." + key, fallbacks[key] || key || "Failed");
  }


  function chatGameAvailabilityKey(serverId, gameId) {
    return String(serverId || "").trim().toLowerCase() + "\n" + String(gameId || "").trim();
  }

  function chatGameUnavailable(serverId, gameId) {
    const id = String(gameId || "").trim();
    if (!id) return false;
    const set = state.unavailableChatGames;
    if (!(set instanceof Set)) return false;
    const server = String(serverId || "").trim().toLowerCase();
    return set.has(chatGameAvailabilityKey(server, id)) || set.has(chatGameAvailabilityKey("", id));
  }

  function markChatGameUnavailable(serverId, gameId) {
    const id = String(gameId || "").trim();
    if (!id) return;
    if (!(state.unavailableChatGames instanceof Set)) state.unavailableChatGames = new Set();
    const key = chatGameAvailabilityKey(serverId, id);
    state.unavailableChatGames.add(key);
    document.querySelectorAll('[data-open-chat-game][data-game-id]').forEach(button => {
      if (String(button.dataset.gameId || "") !== id) return;
      const buttonServer = String(button.dataset.gameServerId || "").trim().toLowerCase();
      const expectedServer = String(serverId || "").trim().toLowerCase();
      if (expectedServer && buttonServer && buttonServer !== expectedServer) return;
      button.disabled = true;
      button.classList.add("kwc-game-chat-link-unavailable");
      button.textContent = chatGameDeletedLabel();
      button.title = chatGameDeletedNotice();
      button.setAttribute("aria-label", button.title);
    });
  }

  function chatGameStatusLabel(status) {
    return t("game.status." + String(status || ""), String(status || ""));
  }

  function chatGameTypeLabel(type) {
    const normalized = String(type || "lottery").toLowerCase();
    if (normalized === "firstcome") return chatGameLocalizedText("game.type.firstcome", {en:"First come", ko:"선착순", ja:"先着順", zh:"先到先得"});
    if (normalized === "poll") return chatGameLocalizedText("game.type.poll", {en:"Poll", ko:"투표", ja:"投票", zh:"投票"});
    if (normalized === "recruitment") return chatGameLocalizedText("game.type.recruitment", {en:"Recruitment", ko:"모집", ja:"募集", zh:"招募"});
    return chatGameLocalizedText("game.type.lottery", {en:"Lottery", ko:"추첨", ja:"抽選", zh:"抽奖"});
  }

  function chatGameMessageTarget(msg) {
    let vars = {};
    try { vars = msg && msg.i18nArgs ? JSON.parse(String(msg.i18nArgs)) : {}; } catch (_) { vars = {}; }
    const serverId = String(vars.serverId || (msg && msg.originServerId) || "").trim();
    const gameId = String(vars.eventId || vars.gameId || "").trim();
    const fallback = gameId ? {
      id:gameId,
      title:String(vars.title || ""),
      type:String(vars.type || "lottery"),
      status:String(vars.status || (String(msg && msg.i18nKey || "").endsWith("results") ? "completed" : "open")),
      maxParticipants:Number(vars.participants || 0),
      winnerCount:Number(vars.winnerCount || vars.winnersCount || (/^\d+$/.test(String(vars.winners || "")) ? vars.winners : 0) || 0),
      winnersText:/^\d+$/.test(String(vars.winners || "")) ? "" : String(vars.winners || ""),
      serverId,
      serverName:String(vars.serverName || serverId || "")
    } : null;
    return {serverId, gameId, fallback};
  }

  function chatGameQuery(targetServerId, gameId) {
    const query = [];
    if (targetServerId) query.push("targetServerId=" + encodeURIComponent(targetServerId));
    if (gameId) query.push("gameId=" + encodeURIComponent(gameId));
    return "/games" + (query.length ? "?" + query.join("&") : "");
  }

  async function chatGameAction(action, values = {}) {
    return api("/games", {method:"POST", body:JSON.stringify(Object.assign({action}, values))});
  }

  async function openChatGameModal(options = {}) {
    if (!state.token) return openLoginModal();
    const old = document.querySelector(".kwc-game-backdrop");
    if (old) old.remove();
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop kwc-game-backdrop";
    wrap.__kwcGameTargetServerId = String(options.serverId || "").trim();
    wrap.__kwcGameId = String(options.gameId || "").trim();
    wrap.__kwcGameFallback = options.fallback || null;
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `<div class="kwc-modal kwc-game-modal"><div class="kwc-modal-head"><h3>${esc(t("game.title", "Events"))}</h3><button class="kwc-button" id="kwc-game-close">${esc(t("button.close", "Close"))}</button></div><div id="kwc-game-content">${esc(t("admin.loading", "Loading..."))}</div></div>`;
    mountWindowOwnedOverlay(wrap, publicChatWindowOwner());
    wrap.querySelector("#kwc-game-close").onclick = () => wrap.remove();
    wrap.addEventListener("click", event => { if (event.target === wrap) wrap.remove(); });
    await refreshChatGameModal(wrap);
  }

  function chatGameListRow(game, serverName, canManage) {
    const participants = Array.isArray(game && game.participants) ? game.participants : [];
    const id = String(game && game.id || "");
    const createdAt = Number(game && game.createdAt || 0);
    const timeText = createdAt > 0 ? formatMessageTimeFull(createdAt) : "";
    return `<div class="kwc-game-list-row" data-game-list-id="${esc(id)}">
      <div class="kwc-game-list-main"><strong title="${esc(game && game.title || "")}">${esc(game && game.title || "")}</strong><span>${esc(chatGameTypeLabel(game && game.type))} · ${esc(chatGameListMetric(game))} · ${esc(chatGameStatusLabel(game && game.status))}${serverName ? ` · ${esc(serverName)}` : ""}${timeText ? ` · ${esc(timeText)}` : ""}</span></div>
      <div class="kwc-game-list-actions"><button type="button" class="kwc-button kwc-game-list-open" data-game-open-id="${esc(id)}">${esc(t("game.select", "Open"))}</button>${canManage ? `<button type="button" class="kwc-button kwc-game-list-delete" data-game-delete-id="${esc(id)}" title="${esc(t("button.delete", "Delete"))}" aria-label="${esc(t("button.delete", "Delete"))}">${esc(t("button.delete", "Delete"))}</button>` : ""}</div>
    </div>`;
  }

  function chatGameCreateForm() {
    const relayScopeField = state.config && state.config.eventRelayTopology === true
      ? `<label><span>${esc(t("game.notificationScope", "Notification scope"))}</span><select class="kwc-input" id="kwc-game-notification-scope"><option value="relay">${esc(t("game.scopeRelay", "Current server + Relay servers"))}</option><option value="local">${esc(t("game.scopeLocal", "Current server only"))}</option></select></label>`
      : "";
    return `<div class="kwc-game-create-form">
      <h4>${esc(t("game.newEvent", "New event"))}</h4>
      <label><span>${esc(chatGameTypeFieldLabel())}</span><select class="kwc-input" id="kwc-game-type"><option value="firstcome">${esc(chatGameTypeLabel("firstcome"))}</option><option value="lottery">${esc(chatGameTypeLabel("lottery"))}</option><option value="poll">${esc(chatGameTypeLabel("poll"))}</option><option value="recruitment">${esc(chatGameTypeLabel("recruitment"))}</option></select></label>
      <label><span>${esc(t("game.eventTitle", "Title"))}</span><input class="kwc-input" id="kwc-game-title" maxlength="80"></label>
      ${relayScopeField}
      <div class="kwc-game-number-row" id="kwc-game-number-fields"><label id="kwc-game-max-field"><span>${esc(t("game.maxParticipants", "Participants"))}</span><input class="kwc-input" id="kwc-game-max" type="number" min="2" value="2"></label><label id="kwc-game-winner-field"><span>${esc(t("game.winnerCountLabel", "Winners"))}</span><input class="kwc-input" id="kwc-game-winner-count" type="number" min="1" max="500" value="1"></label></div>
      <label id="kwc-game-special-field" hidden><span id="kwc-game-special-label"></span><textarea class="kwc-input" id="kwc-game-special" rows="4"></textarea><small id="kwc-game-special-help"></small></label>
      <section class="kwc-admin-section-card kwc-game-auto-end-card">
        <div class="kwc-admin-section-title">${esc(t("game.autoEnd", "Automatic end"))}</div>
        <label id="kwc-game-auto-capacity-field"><span id="kwc-game-auto-capacity-label">${esc(t("game.autoEndWhenFull", "When participant capacity is reached"))}</span><input type="checkbox" id="kwc-game-auto-capacity"></label>
        <label id="kwc-game-auto-response-field" hidden><span>${esc(t("game.autoEndResponseCount", "End after this many responses"))}</span><div class="kwc-row"><input type="checkbox" id="kwc-game-auto-response-enabled"><input class="kwc-input" id="kwc-game-auto-response-count" type="number" min="1" max="500" value="2" disabled></div></label>
        <label><span>${esc(t("game.autoEndDateTime", "End at date/time (optional)"))}</span><input class="kwc-input" id="kwc-game-auto-end-at" type="datetime-local"></label>
        <small>${esc(t("game.autoEndFirstMatch", "If multiple conditions are enabled, the first one reached ends the event."))}</small>
      </section>
      <button class="kwc-button" id="kwc-game-create">${esc(t("game.create", "Create event"))}</button><div class="kwc-admin-result" id="kwc-game-result"></div>
    </div>`;
  }

  function chatGameSpecialCreateValue(type, raw) {
    const lines = String(raw || "").split(/\r?\n/).map(value => value.trim()).filter(Boolean);
    if (type === "poll") return lines.join("|");
    if (type === "recruitment") return lines.join("|");
    return "";
  }

  function chatGameRecruitmentStateLabel(state) {
    const normalized = String(state || "").toLowerCase();
    if (normalized === "accepted") return t("game.recruitmentAccepted", "Accepted");
    if (normalized === "waiting") return t("game.recruitmentWaiting", "Waiting");
    return normalized;
  }

  function chatGameAutoEndText(game) {
    if (!game) return "";
    const type = String(game.type || "").toLowerCase();
    const conditions = [];
    if (game.autoEndOnCapacity === true) {
      conditions.push(type === "recruitment"
        ? t("game.autoEndWhenRolesFull", "when all role slots are filled")
        : t("game.autoEndWhenFull", "when participant capacity is reached"));
    }
    const responseCount = Math.max(0, Number(game.autoEndResponseCount || 0));
    if (type === "poll" && responseCount > 0) conditions.push(fmt("game.autoEndAtResponses", "at {count} responses", {count:responseCount}));
    const endAt = Number(game.autoEndAt || 0);
    if (endAt > 0) conditions.push(fmt("game.autoEndAtTime", "at {time}", {time:formatMessageTimeFull(endAt)}));
    if (!conditions.length) return t("game.autoEndOff", "Automatic end: Off");
    return fmt("game.autoEndSummary", "Automatic end: {conditions}", {conditions:conditions.join(" · ")});
  }

  function chatGameAutoEndTimestamp(value) {
    const raw = String(value || "").trim();
    if (!raw) return 0;
    const parsed = new Date(raw).getTime();
    return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : 0;
  }

  function chatGameFallbackDetail(wrap, content, error) {
    const code = chatGameErrorCode(error);
    const game = wrap && wrap.__kwcGameFallback;
    if (code === "game_not_found") {
      markChatGameUnavailable(wrap && wrap.__kwcGameTargetServerId, wrap && wrap.__kwcGameId);
    }
    if (!game) {
      content.textContent = code === "game_not_found"
        ? chatGameDeletedNotice()
        : chatGameErrorText(code);
      return;
    }
    const serverName = game.serverName || game.serverId || "";
    const problem = code === "game_not_found"
      ? chatGameDeletedNotice()
      : `${t("game.remoteUnavailable", "Could not load the event from its origin server.")} ${chatGameErrorText(code)}`;
    content.innerHTML = `<div class="kwc-game-actions"><button class="kwc-button" id="kwc-game-back-list">${esc(t("game.backToList", "Back to event list"))}</button></div>
      <div class="kwc-game-summary"><strong>${esc(game.title || "")}</strong><span>${esc(chatGameTypeLabel(game.type))} · ${esc(chatGameStatusLabel(game.status))}${serverName ? ` · ${esc(t("game.server", "Server"))}: ${esc(serverName)}` : ""}</span></div>
      <p class="kwc-admin-result">${esc(problem)}</p>`;
    content.querySelector("#kwc-game-back-list")?.addEventListener("click", () => {
      wrap.__kwcGameId = ""; wrap.__kwcGameFallback = null; refreshChatGameModal(wrap);
    });
  }

  async function refreshChatGameModal(wrap) {
    if (!wrap || !document.body.contains(wrap)) return;
    const content = wrap.querySelector("#kwc-game-content");
    if (!content) return;
    const targetServerId = String(wrap.__kwcGameTargetServerId || "").trim();
    const selectedGameId = String(wrap.__kwcGameId || "").trim();
    let data;
    try { data = await api(chatGameQuery(targetServerId, selectedGameId)); }
    catch (error) { chatGameFallbackDetail(wrap, content, error); return; }
    if (!data || data.ok === false) { chatGameFallbackDetail(wrap, content, data && data.error || "failed"); return; }
    const serverId = String(data.serverId || targetServerId || "");
    const serverName = String(data.serverName || serverId || "");
    const remote = data.remote === true;
    const canManage = data.canManage === true && !remote;
    const games = Array.isArray(data.games) ? data.games : [];
    const game = selectedGameId ? data.game : null;

    if (!selectedGameId) {
      content.innerHTML = `<section class="kwc-game-list"><h4>${esc(t("game.list", "Event list"))}</h4><div class="kwc-game-list-body">${games.map(item => chatGameListRow(item, serverName, canManage)).join("") || `<em>${esc(t("game.noEvents", "There are no events."))}</em>`}</div></section>${canManage ? chatGameCreateForm() : ""}`;
      content.querySelectorAll("[data-game-open-id]").forEach(btn => btn.addEventListener("click", () => {
        wrap.__kwcGameId = String(btn.dataset.gameOpenId || ""); refreshChatGameModal(wrap);
      }));
      content.querySelectorAll("[data-game-delete-id]").forEach(btn => btn.addEventListener("click", async () => {
        const gameId = String(btn.dataset.gameDeleteId || "");
        if (!gameId || !confirmPlain(t("game.confirmDeleteEvent", "Delete this event?"))) return;
        btn.disabled = true;
        try {
          const response = await chatGameAction("delete", {gameId});
          if (!response || response.ok === false) throw new Error(response && response.error || "failed");
          markChatGameUnavailable(serverId, gameId);
          if (String(wrap.__kwcGameId || "") === gameId) wrap.__kwcGameId = "";
          await refreshChatGameModal(wrap);
        } catch (error) {
          btn.disabled = false;
          const resultBox = content.querySelector("#kwc-game-result");
          if (resultBox) resultBox.textContent = chatGameErrorText(error && error.message || error);
          else alertPlain(chatGameErrorText(error && error.message || error));
        }
      }));
      const typeSelect = content.querySelector("#kwc-game-type");
      const numberFields = content.querySelector("#kwc-game-number-fields");
      const maxField = content.querySelector("#kwc-game-max-field");
      const maxInput = content.querySelector("#kwc-game-max");
      const winnerInput = content.querySelector("#kwc-game-winner-count");
      const specialField = content.querySelector("#kwc-game-special-field");
      const specialLabel = content.querySelector("#kwc-game-special-label");
      const specialInput = content.querySelector("#kwc-game-special");
      const specialHelp = content.querySelector("#kwc-game-special-help");
      const autoCapacityField = content.querySelector("#kwc-game-auto-capacity-field");
      const autoCapacityLabel = content.querySelector("#kwc-game-auto-capacity-label");
      const autoCapacity = content.querySelector("#kwc-game-auto-capacity");
      const autoResponseField = content.querySelector("#kwc-game-auto-response-field");
      const autoResponseEnabled = content.querySelector("#kwc-game-auto-response-enabled");
      const autoResponseCount = content.querySelector("#kwc-game-auto-response-count");
      const syncGameCapacityFields = () => {
        const firstCome = typeSelect?.value === "firstcome";
        const type = typeSelect?.value || "firstcome";
        const special = type === "poll" || type === "recruitment";
        if (numberFields) numberFields.hidden = special;
        if (maxField) {
          maxField.hidden = false;
          maxField.classList.remove("kwc-game-max-field-firstcome-hidden");
        }
        if (maxInput) {
          maxInput.disabled = firstCome;
          if (special) maxInput.disabled = true;
          maxInput.readOnly = firstCome || special;
          if (firstCome) maxInput.value = String(Math.max(1, Number(winnerInput?.value || 1)));
        }
        if (winnerInput) winnerInput.disabled = special;
        if (specialField) specialField.hidden = !special;
        if (specialLabel) specialLabel.textContent = type === "poll"
          ? t("game.pollOptions", "Poll options")
          : type === "recruitment"
            ? t("game.recruitmentRoles", "Recruitment roles")
            : "";
        if (specialHelp) specialHelp.textContent = type === "poll"
          ? t("game.pollOptionsHelp", "One option per line. At least two options.")
          : type === "recruitment"
            ? t("game.recruitmentRolesHelp", "One role per line as Role:count. Full roles continue into a wait queue.")
            : "";
        if (specialInput) specialInput.placeholder = type === "poll"
          ? "Option A\nOption B"
          : type === "recruitment"
            ? "Tank:1\nHealer:1\nDPS:3"
            : "";
        const isPoll = type === "poll";
        if (autoCapacityField) autoCapacityField.hidden = isPoll;
        if (autoResponseField) autoResponseField.hidden = !isPoll;
        if (autoCapacity) {
          if (firstCome) { autoCapacity.checked = true; autoCapacity.disabled = true; }
          else { autoCapacity.disabled = false; }
        }
        if (autoCapacityLabel) autoCapacityLabel.textContent = type === "recruitment"
          ? t("game.autoEndWhenRolesFull", "When all role slots are filled")
          : firstCome
            ? t("game.autoEndWhenFirstComeFull", "When winner slots are filled (always on for first come)")
            : t("game.autoEndWhenFull", "When participant capacity is reached");
        if (autoResponseCount) autoResponseCount.disabled = !(autoResponseEnabled && autoResponseEnabled.checked);
      };
      typeSelect?.addEventListener("change", syncGameCapacityFields);
      winnerInput?.addEventListener("input", syncGameCapacityFields);
      autoResponseEnabled?.addEventListener("change", syncGameCapacityFields);
      syncGameCapacityFields();
      const create = content.querySelector("#kwc-game-create");
      if (create) create.addEventListener("click", async () => {
        const resultBox = content.querySelector("#kwc-game-result");
        try {
          const selectedType = content.querySelector("#kwc-game-type")?.value || "lottery";
          const winnerCount = content.querySelector("#kwc-game-winner-count")?.value || "";
          const specialValue = chatGameSpecialCreateValue(selectedType, content.querySelector("#kwc-game-special")?.value || "");
          const response = await chatGameAction("create", {
            type:selectedType,
            title:content.querySelector("#kwc-game-title")?.value || "",
            maxParticipants:selectedType === "firstcome" ? "" : ((selectedType === "poll" || selectedType === "recruitment") ? "" : (content.querySelector("#kwc-game-max")?.value || "")),
            winnerCount:(selectedType === "poll" || selectedType === "recruitment") ? "" : winnerCount,
            pollOptions:selectedType === "poll" ? specialValue : "",
            recruitmentRoles:selectedType === "recruitment" ? specialValue : "",
            autoEndOnCapacity:selectedType === "firstcome" ? true : (selectedType !== "poll" && content.querySelector("#kwc-game-auto-capacity")?.checked === true),
            autoEndResponseCount:selectedType === "poll" && content.querySelector("#kwc-game-auto-response-enabled")?.checked === true ? (content.querySelector("#kwc-game-auto-response-count")?.value || "") : "0",
            autoEndAt:chatGameAutoEndTimestamp(content.querySelector("#kwc-game-auto-end-at")?.value || ""),
            notificationScope:state.config && state.config.eventRelayTopology === true ? (content.querySelector("#kwc-game-notification-scope")?.value || "relay") : "local"
          });
          if (!response || response.ok === false) throw new Error(response && response.error || "failed");
          wrap.__kwcGameId = String(response.game && response.game.id || "");
          await refreshChatGameModal(wrap);
        } catch (error) { if (resultBox) resultBox.textContent = chatGameErrorText(error && error.message || error); }
      });
      return;
    }

    if (!game) { chatGameFallbackDetail(wrap, content, "game_not_found"); return; }
    const participants = Array.isArray(game.participants) ? game.participants : [];
    const winners = Array.isArray(game.winners) ? game.winners : [];
    const type = String(game.type || "").toLowerCase();
    const isPoll = type === "poll";
    const isRecruitment = type === "recruitment";
    const canJoin = !isPoll && !isRecruitment && game.status === "open" && game.joined !== true && participants.length < Number(game.maxParticipants || 0);
    const canFinish = canManage && (game.status === "open" || game.status === "ready");
    const canClose = canManage && game.status === "completed" && game.type !== "firstcome";
    const summaryMetric = isPoll
      ? fmt("game.voteCount", "{count} votes", {count:Number(game.voteCount || 0)})
      : isRecruitment
        ? fmt("game.recruitmentMetric", "{accepted}/{capacity} accepted · {waiting} waiting", {accepted:Number(game.acceptedCount || 0), capacity:Number(game.maxParticipants || 0), waiting:Number(game.waitingCount || 0)})
        : `${participants.length}/${chatGameDisplayCapacity(game)} · ${fmt("game.winnerCount", "{count} winners", {count:game.winnerCount || 0})}`;

    const pollOptions = Array.isArray(game.pollOptions) ? game.pollOptions : [];
    const selectedPollOption = Number(game.pollSelectedOption || 0);
    const pollSection = isPoll ? `<section class="kwc-game-participants"><h4>${esc(t("game.pollOptions", "Poll options"))}</h4><div class="kwc-game-participant-list">${pollOptions.map(option => {
      const index = Number(option && option.index || 0);
      const selected = selectedPollOption === index;
      const disabled = game.status !== "open";
      return `<div class="kwc-game-participant"><span class="kwc-game-participant-number">${index}</span><span>${esc(option && option.label || "")}</span><span>${esc(fmt("game.voteCount", "{count} votes", {count:Number(option && option.count || 0)}))}</span>${game.status === "open" ? `<button type="button" class="kwc-button kwc-game-vote" data-game-vote="${index}" ${selected ? "disabled" : ""}>${esc(selected ? t("game.voted", "Voted") : t("game.vote", "Vote"))}</button>` : ""}</div>`;
    }).join("")}</div></section>` : "";

    const recruitmentRoles = Array.isArray(game.recruitmentRoles) ? game.recruitmentRoles : [];
    const recruitmentState = String(game.recruitmentState || "");
    const recruitmentRole = String(game.recruitmentRole || "");
    const recruitmentQueuePosition = Number(game.recruitmentQueuePosition || 0);
    const recruitmentSection = isRecruitment ? `<section class="kwc-game-participants"><h4>${esc(t("game.recruitmentRoles", "Recruitment roles"))}</h4><div class="kwc-game-participant-list">${recruitmentRoles.map(role => {
      const roleName = String(role && role.name || "");
      const mine = recruitmentRole && roleName.toLowerCase() === recruitmentRole.toLowerCase();
      return `<div class="kwc-game-participant"><strong>${esc(roleName)}</strong><span>${esc(fmt("game.recruitmentRoleMetric", "{accepted}/{capacity} accepted · {waiting} waiting", {accepted:Number(role && role.accepted || 0), capacity:Number(role && role.capacity || 0), waiting:Number(role && role.waiting || 0)}))}</span>${game.status === "open" ? `<button type="button" class="kwc-button kwc-game-apply" data-game-role="${esc(roleName)}" ${mine ? "disabled" : ""}>${esc(mine ? t("game.applied", "Applied") : t("game.apply", "Apply"))}</button>` : ""}</div>`;
    }).join("")}</div>${recruitmentRole ? `<p>${esc(fmt("game.myRecruitment", "My application: {role} · {state}{queue}", {role:recruitmentRole, state:chatGameRecruitmentStateLabel(recruitmentState), queue:recruitmentState === "waiting" ? ` #${recruitmentQueuePosition}` : ""}))}</p>` : ""}</section>` : "";

    const participantRows = participants.map((item, index) => {
      const identity = directMessageIdentityHtml({displayName:item.displayName || item.label || item.username || item.uuid || "", username:item.username || "", uuid:item.uuid || ""}, "kwc-sender");
      if (!isRecruitment) return `<div class="kwc-game-participant"><span class="kwc-game-participant-number">${index + 1}</span>${identity}</div>`;
      const stateLabel = chatGameRecruitmentStateLabel(item.state);
      const queue = String(item.state || "") === "waiting" ? ` #${Number(item.queuePosition || 0)}` : "";
      return `<div class="kwc-game-participant"><span class="kwc-game-participant-number">${index + 1}</span>${identity}<span>${esc(String(item.role || ""))} · ${esc(stateLabel + queue)}</span></div>`;
    }).join("");

    content.innerHTML = `
      <div class="kwc-game-actions"><button class="kwc-button" id="kwc-game-back-list">${esc(t("game.backToList", "Back to event list"))}</button><button class="kwc-button" id="kwc-game-refresh">${esc(t("game.refresh", "Refresh"))}</button></div>
      <div class="kwc-game-summary"><strong>${esc(game.title || "")}</strong><span>${esc(chatGameTypeLabel(game.type))} · ${esc(summaryMetric)} · ${esc(chatGameStatusLabel(game.status))} · ${esc(t("game.server", "Server"))}: ${esc(serverName || serverId)}</span><small>${esc(chatGameAutoEndText(game))}</small></div>
      <div class="kwc-game-actions">${canJoin ? `<button class="kwc-button" id="kwc-game-join">${esc(t("game.join", "Join"))}</button>` : ""}${(!isPoll && !isRecruitment && game.joined) ? `<span>${esc(t("game.joined", "Joined"))}</span>` : ""}${isRecruitment && game.joined && game.status === "open" ? `<button class="kwc-button" id="kwc-game-withdraw">${esc(t("game.withdraw", "Withdraw"))}</button>` : ""}${canFinish ? `<button class="kwc-button" id="kwc-game-finish">${esc(t(game.type === "lottery" ? "game.finishLottery" : "game.finish", game.type === "lottery" ? "Close and draw" : "Close registration"))}</button>` : ""}${canClose ? `<button class="kwc-button" id="kwc-game-end">${esc(t("game.close", "Close event"))}</button>` : ""}</div>
      ${remote ? `<small>${esc(t("game.remoteReadOnly", "Management is available only on the event origin server."))}</small>` : ""}
      ${pollSection}
      ${recruitmentSection}
      ${winners.length ? `<section><h4>${esc(t("game.winners", "Winners"))}</h4><div class="kwc-game-winners">${winners.map(item => directMessageIdentityHtml({displayName:item.displayName || item.label || item.username || item.uuid || "", username:item.username || "", uuid:item.uuid || ""}, "kwc-sender")).join("")}</div></section>` : ""}
      <section class="kwc-game-participants"><h4>${esc(fmt(isPoll ? "game.voterHeading" : isRecruitment ? "game.applicantHeading" : "game.participantHeading", isPoll ? "Voters ({count})" : isRecruitment ? "Applicants ({count})" : "Participants ({count})", {count:participants.length}))}</h4><div class="kwc-game-participant-list">${participantRows || `<em>${esc(t(isPoll ? "game.noVoters" : isRecruitment ? "game.noApplicants" : "game.noParticipants", isPoll ? "No votes yet." : isRecruitment ? "No applicants yet." : "No participants yet."))}</em>`}</div></section>
      <div class="kwc-admin-result" id="kwc-game-result"></div>`;
    installSenderIdentityToggle(content);
    const act = async (action, extra = {}) => {
      const resultBox = content.querySelector("#kwc-game-result");
      try {
        const response = await chatGameAction(action, Object.assign({gameId:String(game.id || selectedGameId), targetServerId}, extra));
        if (!response || response.ok === false) throw new Error(response && response.error || "failed");
        await refreshChatGameModal(wrap);
      } catch (error) { if (resultBox) resultBox.textContent = chatGameErrorText(error && error.message || error); }
    };
    content.querySelector("#kwc-game-back-list")?.addEventListener("click", () => {
      wrap.__kwcGameId = ""; wrap.__kwcGameFallback = null; refreshChatGameModal(wrap);
    });
    content.querySelector("#kwc-game-join")?.addEventListener("click", () => act("join"));
    content.querySelectorAll("[data-game-vote]").forEach(button => button.addEventListener("click", () => act("vote", {option:Number(button.dataset.gameVote || 0)})));
    content.querySelectorAll("[data-game-role]").forEach(button => button.addEventListener("click", () => act("apply", {role:String(button.dataset.gameRole || "")})));
    content.querySelector("#kwc-game-withdraw")?.addEventListener("click", () => act("withdraw"));
    content.querySelector("#kwc-game-finish")?.addEventListener("click", () => act("finish"));
    content.querySelector("#kwc-game-end")?.addEventListener("click", () => act("close"));
    content.querySelector("#kwc-game-refresh")?.addEventListener("click", () => refreshChatGameModal(wrap));
  }
