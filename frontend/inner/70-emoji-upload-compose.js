// [KWC 유지보수 주석 / KWC maintenance notes]
// emoji picker 열기/닫기, custom emoji 검색·즐겨찾기, 파일 업로드, 메시지 composer 입력 보조를 담당한다.
// This fragment handles emoji-picker lifecycle, custom-emoji search/favorites, file uploads, and message-composer input assistance.
// emoji panel의 높이와 가로 scrollbar는 사용자 설정/테마와 연결되어 있으므로 wrapper CSS와 root CSS를 항상 같은 generated bundle 기준으로 동기화해야 한다.
// Emoji-panel sizing and horizontal scrollbar styling depend on preferences/themes, so wrapper CSS and root CSS must stay synchronized with the generated bundle.
// 업로드는 서버가 반환한 안전한 파일 식별자만 composer에 삽입하고, 브라우저의 로컬 파일 경로나 임의 URL을 서버 경로처럼 신뢰하지 않는다.
// Uploads insert only server-returned safe file identifiers into the composer; local browser paths or arbitrary URLs must never be trusted as server paths.

  function canUseCustomEmoji() {
    return !!(state.emojiEnabled && state.emojiShowButton !== false && Array.isArray(state.emojiItems) && state.emojiItems.length > 0 && !state.minimized);
  }

  function updateEmojiButton() {
    const btn = document.getElementById("kwc-emoji");
    if (!btn) return;
    const visible = canUseCustomEmoji();
    btn.classList.toggle("kwc-hidden", !visible);
    btn.title = t("button.emoji", "Emoji");
    if (!visible) hideEmojiPanel();
  }

  function hideEmojiPanel() {
    const panel = document.getElementById("kwc-emoji-panel");
    if (state.emojiPanelOpen || (panel && !panel.classList.contains("kwc-hidden"))) markNonScrollLayoutChange();
    state.emojiPanelOpen = false;
    resetEmojiSearchState("public");
    if (panel) panel.classList.add("kwc-hidden");
    updateEmojiResizeHandleVisibility();
  }

  function toggleEmojiPanel() {
    if (!canUseCustomEmoji()) return;
    markNonScrollLayoutChange();
    state.emojiPanelOpen = !state.emojiPanelOpen;
    renderEmojiPanel();
  }

  function emojiTokenCount(text) {
    let count = 0;
    const re = customEmojiTokenRegex();
    let match;
    const source = String(text || "");
    while ((match = re.exec(source)) !== null) {
      if (customEmojiByToken(match[1])) count++;
    }
    return count;
  }

  function endsWithCustomEmojiToken(text) {
    const source = String(text || "");
    const re = customEmojiBoundaryRegex("end");
    const match = re.exec(source);
    if (!match) return false;
    const tokenMatch = customEmojiTokenRegex().exec(match[0]);
    return !!(tokenMatch && customEmojiByToken(tokenMatch[1]));
  }

  function startsWithCustomEmojiToken(text) {
    const source = String(text || "");
    const re = customEmojiBoundaryRegex("start");
    const match = re.exec(source);
    if (!match) return false;
    const tokenMatch = customEmojiTokenRegex().exec(match[0]);
    return !!(tokenMatch && customEmojiByToken(tokenMatch[1]));
  }

  function setActiveComposeInput(inputOrId) {
    const id = typeof inputOrId === "string" ? inputOrId : (inputOrId && inputOrId.id);
    if (id) state.activeComposeInputId = id;
  }

  function activeComposeInput() {
    const preferred = document.getElementById(state.activeComposeInputId || "");
    if (preferred && !preferred.disabled && document.body.contains(preferred)) return preferred;
    const active = document.activeElement;
    if (active && active.id && (active.id === "kwc-group-input" || active.id === "kwc-dm-input" || active.id === "kwc-message")) return active;
    return document.getElementById("kwc-message") || document.getElementById("kwc-dm-input") || document.getElementById("kwc-group-input");
  }

  function insertCustomEmoji(id) {
    id = String(id || "");
    if (!customEmojiById(id)) return;
    const input = activeComposeInput();
    if (!input) return;
    setActiveComposeInput(input);
    const limit = Number(state.emojiMessageTokenLimit || 0);
    if (limit > 0 && emojiTokenCount(input.value) >= limit) {
      alert(fmt("alert.emojiTooMany", "Only {max} emoji(s) can be used in one message.", {max: limit}));
      return;
    }
    const token = customEmojiTokenForId(id);
    const start = Number(input.selectionStart);
    const end = Number(input.selectionEnd);
    const hasSelection = Number.isFinite(start) && Number.isFinite(end);
    const current = input.value || "";
    if (hasSelection) {
      const before = current.slice(0, start);
      const after = current.slice(end);
      // Insert exactly the selected emoji token. Do not synthesize whitespace:
      // spacing around emojis is user-authored and consecutive tokens stay exact.
      input.value = before + token + after;
      const caret = (before + token).length;
      try { input.selectionStart = input.selectionEnd = caret; } catch (_) {}
    } else {
      const current = input.value || "";
      input.value = current + token;
      try { input.selectionStart = input.selectionEnd = input.value.length; } catch (_) {}
    }
    rememberRecentCustomEmoji(id);
    input.focus();
  }


  function hideEmojiAutocomplete() {
    const panel = document.getElementById("kwc-emoji-autocomplete");
    if (panel) panel.remove();
    state.emojiAutocomplete = null;
  }

  let mentionAutocompleteState = null;
  let mentionAutocompleteTimer = 0;
  let mentionAutocompleteRequestSeq = 0;
  let mentionAutocompletePositionInstalled = false;

  function hideMentionAutocomplete() {
    if (mentionAutocompleteTimer) {
      clearTimeout(mentionAutocompleteTimer);
      mentionAutocompleteTimer = 0;
    }
    mentionAutocompleteRequestSeq++;
    const panel = document.getElementById("kwc-mention-autocomplete");
    if (panel) panel.remove();
    mentionAutocompleteState = null;
  }

  function mentionTriggerAtCaret(input) {
    if (!input || input.disabled) return null;
    const start = Number(input.selectionStart);
    const end = Number(input.selectionEnd);
    if (!Number.isFinite(start) || !Number.isFinite(end) || start !== end) return null;
    const text = String(input.value || "");
    const before = text.slice(0, start);
    const match = /(^|[\s([{<'".,!?;:])@([^\s@]*)$/u.exec(before);
    if (!match) return null;
    const at = before.lastIndexOf("@");
    if (at < 0) return null;
    const query = String(match[2] || "");
    if (query.length > 80) return null;
    return {start: at, end: start, query};
  }

  function mentionScopeForInput(input) {
    const id = String(input && input.id || "");
    if (id === "kwc-dm-input") return "dm";
    if (id === "kwc-group-input") return "group";
    return "public";
  }

  function currentDirectMentionTargetUuid() {
    if (state.dmAuditMode) return "";
    if (state.dmActiveThreadId) {
      const thread = (state.dmThreads || []).find(item => String(item && item.id || "") === String(state.dmActiveThreadId || ""));
      if (thread) return String(thread.otherUuid || thread.otherPlayerUuid || "").trim();
    }
    return String(state.dmDraftTarget && state.dmDraftTarget.uuid || "").trim();
  }

  function mentionLocalPublicCandidates(query) {
    const q = String(query || "").toLowerCase();
    const rows = [];
    const seen = new Set();
    const messages = Array.isArray(state.messages) ? state.messages : [];
    for (let i = messages.length - 1; i >= 0 && rows.length < 12; i--) {
      const msg = messages[i] || {};
      const username = directMessagePlainLabel(stripMinecraftColorCodes(realSender(msg) || msg.username || ""));
      const displayName = String(displaySender(msg) || msg.sender || username || "");
      const mentionText = username || directMessagePlainLabel(stripMinecraftColorCodes(displayName));
      if (!mentionText) continue;
      const key = mentionText.toLowerCase();
      if (seen.has(key)) continue;
      const haystack = (directMessagePlainLabel(stripMinecraftColorCodes(displayName)) + " " + username).toLowerCase();
      if (q && !haystack.includes(q)) continue;
      seen.add(key);
      rows.push({
        uuid: String(msg.playerUuid || msg.uuid || ""),
        username,
        displayName,
        label: displayName || username,
        mentionText,
        remote: !!(msg.originServerId || msg.remote),
        serverId: String(msg.originServerId || ""),
        serverName: String(msg.originServerName || ""),
        presence: null
      });
    }
    return rows;
  }

  function mentionCandidateMetaHtml(item) {
    const username = String(item && item.username || item && item.mentionText || "").trim();
    const parts = [];
    if (username) parts.push("@" + username);
    const server = String(item && (item.serverName || item.serverId) || "").trim();
    if (server) parts.push("[" + server + "]");
    try {
      const p = presenceData(item || {});
      if (p && p.source && p.source !== "offline") {
        const source = presenceSourceLabel(p.source);
        parts.push(p.status === "busy" ? source + " · " + presenceStatusLabel("busy") : source);
      }
    } catch (_) {}
    return esc(parts.join(" · "));
  }

  function positionMentionAutocomplete() {
    const current = mentionAutocompleteState;
    const panel = document.getElementById("kwc-mention-autocomplete");
    const input = current && current.input;
    if (!panel || !input || !input.isConnected) return;
    const rect = input.getBoundingClientRect();
    const viewport = window.visualViewport;
    const viewLeft = viewport ? Number(viewport.offsetLeft || 0) : 0;
    const viewTop = viewport ? Number(viewport.offsetTop || 0) : 0;
    const viewWidth = viewport ? Number(viewport.width || window.innerWidth || 0) : Number(window.innerWidth || 0);
    const viewHeight = viewport ? Number(viewport.height || window.innerHeight || 0) : Number(window.innerHeight || 0);
    const margin = 8;
    const width = Math.max(220, Math.min(420, rect.width || 320, Math.max(220, viewWidth - margin * 2)));
    let left = Math.max(viewLeft + margin, Math.min(rect.left, viewLeft + viewWidth - width - margin));
    const estimated = Math.min(232, Math.max(48, panel.scrollHeight || 160));
    const roomAbove = rect.top - viewTop;
    const roomBelow = viewTop + viewHeight - rect.bottom;
    let top;
    if (roomAbove >= Math.min(estimated + 8, Math.max(96, roomBelow))) {
      top = Math.max(viewTop + margin, rect.top - estimated - 6);
    } else {
      top = Math.min(viewTop + viewHeight - estimated - margin, rect.bottom + 6);
    }
    panel.style.left = Math.round(left) + "px";
    panel.style.top = Math.round(Math.max(viewTop + margin, top)) + "px";
    panel.style.width = Math.round(width) + "px";
  }

  function ensureMentionAutocompletePositionListeners() {
    if (mentionAutocompletePositionInstalled) return;
    mentionAutocompletePositionInstalled = true;
    const refresh = () => { if (mentionAutocompleteState) positionMentionAutocomplete(); };
    window.addEventListener("resize", refresh, {passive: true});
    window.addEventListener("scroll", refresh, {passive: true, capture: true});
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", refresh, {passive: true});
      window.visualViewport.addEventListener("scroll", refresh, {passive: true});
    }
  }

  function renderMentionAutocomplete(input, trigger, items, selected = 0) {
    const candidates = Array.isArray(items) ? items.filter(item => item && item.mentionText && !isPersonallyBlockedUuid(item.uuid || "")) : [];
    if (!input || !trigger || !candidates.length) {
      hideMentionAutocomplete();
      return;
    }
    let panel = document.getElementById("kwc-mention-autocomplete");
    if (!panel) {
      panel = document.createElement("div");
      panel.id = "kwc-mention-autocomplete";
      panel.className = "kwc-mention-autocomplete";
      panel.setAttribute("role", "listbox");
      panel.setAttribute("aria-label", t("mention.suggestions", "Mention suggestions"));
    }
    const host = input.closest(".kwc-modal-backdrop") || document.getElementById("kwc-root") || document.body;
    if (panel.parentNode !== host) host.appendChild(panel);
    const nextSelected = Math.max(0, Math.min(Number(selected || 0), candidates.length - 1));
    panel.innerHTML = candidates.map((item, index) => {
      const display = String(item.displayName || item.label || item.username || item.mentionText || "");
      const active = index === nextSelected ? " kwc-active" : "";
      return `<button type="button" class="kwc-mention-option${active}" role="option" aria-selected="${index === nextSelected ? "true" : "false"}" data-mention-index="${index}"><span class="kwc-mention-option-name">${directMessageLabelHtml(display)}</span><span class="kwc-mention-option-meta">${mentionCandidateMetaHtml(item)}</span></button>`;
    }).join("");
    mentionAutocompleteState = {input, trigger, items: candidates, selected: nextSelected};
    panel.querySelectorAll("[data-mention-index]").forEach(button => {
      button.addEventListener("mousedown", event => event.preventDefault());
      button.addEventListener("click", () => selectMentionAutocomplete(Number(button.dataset.mentionIndex || 0)));
    });
    ensureMentionAutocompletePositionListeners();
    requestAnimationFrame(positionMentionAutocomplete);
  }


  function setMentionAutocompleteSelected(index, scroll = true) {
    const current = mentionAutocompleteState;
    const panel = document.getElementById("kwc-mention-autocomplete");
    if (!current || !panel || !current.items || !current.items.length) return false;
    const next = Math.max(0, Math.min(Number(index || 0), current.items.length - 1));
    current.selected = next;
    let active = null;
    panel.querySelectorAll("[data-mention-index]").forEach(button => {
      const selected = Number(button.dataset.mentionIndex || 0) === next;
      button.classList.toggle("kwc-active", selected);
      button.setAttribute("aria-selected", selected ? "true" : "false");
      if (selected) active = button;
    });
    if (scroll && active && active.scrollIntoView) active.scrollIntoView({block: "nearest"});
    return true;
  }

  function selectMentionAutocomplete(index = null) {
    const current = mentionAutocompleteState;
    if (!current || !current.input || !current.input.isConnected) return false;
    const selected = index == null ? current.selected : Number(index);
    const item = current.items && current.items[selected];
    if (!item) return false;
    const input = current.input;
    const trigger = mentionTriggerAtCaret(input);
    if (!trigger || trigger.start !== current.trigger.start) {
      hideMentionAutocomplete();
      return false;
    }
    const mentionText = String(item.mentionText || "").replace(/^@+/, "").trim();
    if (!mentionText) return false;
    const before = String(input.value || "").slice(0, trigger.start);
    const after = String(input.value || "").slice(trigger.end);
    let token = "@" + mentionText + " ";
    const maxLength = Number(input.maxLength || -1);
    if (maxLength > 0 && before.length + token.length + after.length > maxLength) {
      token = "@" + mentionText;
      if (before.length + token.length + after.length > maxLength) return false;
    }
    input.value = before + token + after;
    const caret = before.length + token.length;
    try { input.selectionStart = input.selectionEnd = caret; } catch (_) {}
    hideMentionAutocomplete();
    setActiveComposeInput(input);
    input.focus();
    try { input.dispatchEvent(new Event("input", {bubbles: true})); } catch (_) {}
    return true;
  }

  async function fetchMentionAutocompleteCandidates(input, trigger, requestSeq) {
    const scope = mentionScopeForInput(input);
    const query = String(trigger.query || "");
    if (!state.token) {
      if (scope === "public") renderMentionAutocomplete(input, trigger, mentionLocalPublicCandidates(query), 0);
      else hideMentionAutocomplete();
      return;
    }
    let url = "/mentions?scope=" + encodeURIComponent(scope) + "&q=" + encodeURIComponent(query) + "&limit=12";
    if (scope === "group") {
      const roomId = String(state.groupActiveRoomId || "");
      if (!roomId || state.groupAuditMode) { hideMentionAutocomplete(); return; }
      url += "&roomId=" + encodeURIComponent(roomId);
    } else if (scope === "dm") {
      const targetUuid = currentDirectMentionTargetUuid();
      if (!targetUuid || state.dmAuditMode) { hideMentionAutocomplete(); return; }
      url += "&targetUuid=" + encodeURIComponent(targetUuid);
    }
    try {
      const res = await api(url, {timeoutMs: 6000});
      if (requestSeq !== mentionAutocompleteRequestSeq) return;
      const latest = mentionTriggerAtCaret(input);
      if (!latest || latest.start !== trigger.start || latest.query !== trigger.query) return;
      renderMentionAutocomplete(input, latest, Array.isArray(res.players) ? res.players : [], 0);
    } catch (_) {
      if (requestSeq === mentionAutocompleteRequestSeq) {
        if (scope === "public") renderMentionAutocomplete(input, trigger, mentionLocalPublicCandidates(query), 0);
        else hideMentionAutocomplete();
      }
    }
  }

  function scheduleMentionAutocomplete(input) {
    if (!input || input.disabled) { hideMentionAutocomplete(); return; }
    const trigger = mentionTriggerAtCaret(input);
    if (!trigger) { hideMentionAutocomplete(); return; }
    if (mentionAutocompleteTimer) clearTimeout(mentionAutocompleteTimer);
    const requestSeq = ++mentionAutocompleteRequestSeq;
    mentionAutocompleteTimer = setTimeout(() => {
      mentionAutocompleteTimer = 0;
      fetchMentionAutocompleteCandidates(input, trigger, requestSeq);
    }, trigger.query ? 90 : 40);
  }

  function handleMentionAutocompleteKeydown(event, input) {
    const current = mentionAutocompleteState;
    if (!current || current.input !== input || !current.items || !current.items.length) return false;
    if (event.isComposing || event.keyCode === 229) return false;
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      event.stopPropagation();
      const delta = event.key === "ArrowDown" ? 1 : -1;
      const next = (current.selected + delta + current.items.length) % current.items.length;
      setMentionAutocompleteSelected(next, true);
      return true;
    }
    if (event.key === "Tab" || event.key === "Enter") {
      event.preventDefault();
      event.stopPropagation();
      return selectMentionAutocomplete(current.selected);
    }
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      hideMentionAutocomplete();
      return true;
    }
    return false;
  }

  function emojiButtonHtml(item) {
    if (!item || !item.id || !item.url) return "";
    const label = item.label || item.name || item.id;
    const size = emojiPickerSizePx();
    const favorite = isFavoriteCustomEmoji(item.id);
    const favoriteLabel = favorite
      ? t("emoji.favoriteRemove", "Remove from favorites")
      : t("emoji.favoriteAdd", "Add to favorites");
    const favoriteControl = emojiFavoritesAvailable()
      ? `<button type="button" class="kwc-emoji-favorite-toggle${favorite ? " kwc-active" : ""}" data-emoji-favorite-id="${esc(item.id)}" title="${esc(favoriteLabel)}" aria-label="${esc(favoriteLabel)}">${favorite ? "★" : "☆"}</button>`
      : "";
    return `<div class="kwc-emoji-item-wrap">`
      + `<button type="button" class="kwc-emoji-item" data-emoji-id="${esc(item.id)}" title="${esc(label)}"><img src="${esc(item.url)}" alt="${esc(label)}" loading="lazy" draggable="false" style="width:${size}px;height:${size}px;"><span>${esc(label)}</span></button>`
      + favoriteControl
      + `</div>`;
  }

  function syncFavoriteCustomEmojiButtons(root = document) {
    if (!root || !root.querySelectorAll) return;
    root.querySelectorAll("[data-emoji-favorite-id]").forEach(btn => {
      const active = isFavoriteCustomEmoji(btn.dataset.emojiFavoriteId || "");
      const label = active
        ? t("emoji.favoriteRemove", "Remove from favorites")
        : t("emoji.favoriteAdd", "Add to favorites");
      btn.classList.toggle("kwc-active", active);
      btn.textContent = active ? "★" : "☆";
      btn.title = label;
      btn.setAttribute("aria-label", label);
    });
  }

  function updateEmojiVirtualTabCounts(root = document) {
    if (!root || !root.querySelectorAll) return;
    const counts = new Map([
      [KWC_EMOJI_RECENT_PACK, recentCustomEmojiItems(state.emojiItems).length],
      [KWC_EMOJI_FAVORITES_PACK, favoriteCustomEmojiItems(state.emojiItems).length]
    ]);
    counts.forEach((count, packId) => {
      root.querySelectorAll(`[data-emoji-pack="${packId}"] .kwc-emoji-tab-count`).forEach(node => {
        node.textContent = String(count);
      });
    });
  }

  function refreshFavoriteEmojiGridIfVisible(panel) {
    if (!panel || !panel.isConnected) return;
    const kind = panel.id === "kwc-dm-emoji-panel" ? "dm" : (panel.id === "kwc-group-emoji-panel" ? "group" : "public");
    const ctx = emojiPanelContext(kind);
    if (ctx.searchOpen || ctx.selected !== KWC_EMOJI_FAVORITES_PACK) return;
    const scroll = panel.querySelector(".kwc-emoji-scroll");
    if (!scroll) return;
    const oldTop = Number(scroll.scrollTop || 0);
    const shown = favoriteCustomEmojiItems(state.emojiItems);
    scroll.innerHTML = emojiPanelGridHtml(shown, ctx.selected, false, "");
    installEmojiItemHandlers(panel);
    requestAnimationFrame(() => {
      if (scroll.isConnected) scroll.scrollTop = Math.max(0, Math.min(oldTop, scroll.scrollHeight - scroll.clientHeight));
    });
  }

  function refreshFavoriteEmojiUi() {
    ["kwc-emoji-panel", "kwc-dm-emoji-panel", "kwc-group-emoji-panel"].forEach(id => {
      const panel = document.getElementById(id);
      if (panel) refreshFavoriteEmojiGridIfVisible(panel);
    });
    syncFavoriteCustomEmojiButtons(document);
    updateEmojiVirtualTabCounts(document);
  }

  function installEmojiItemHandlers(panel) {
    panel.querySelectorAll("[data-emoji-id]").forEach(btn => {
      if (btn.dataset.kwcEmojiPickInstalled === "1") return;
      btn.dataset.kwcEmojiPickInstalled = "1";
      btn.addEventListener("click", () => {
        insertCustomEmoji(btn.dataset.emojiId || "");
        refreshRecentEmojiGridIfVisible(panel);
        updateEmojiVirtualTabCounts(document);
      });
    });
    panel.querySelectorAll("[data-emoji-favorite-id]").forEach(btn => {
      if (btn.dataset.kwcEmojiFavoriteInstalled === "1") return;
      btn.dataset.kwcEmojiFavoriteInstalled = "1";
      btn.addEventListener("click", async event => {
        event.preventDefault();
        if (btn.disabled) return;
        btn.disabled = true;
        try { await toggleFavoriteCustomEmoji(btn.dataset.emojiFavoriteId || ""); }
        finally { btn.disabled = false; refreshFavoriteEmojiUi(); }
      });
    });
  }

  function emojiPanelContext(kind = "public") {
    if (kind === "dm") return {
      selected: String(state.dmEmojiSelectedPack || ""),
      setSelected: value => { state.dmEmojiSelectedPack = String(value || ""); },
      searchOpen: !!state.dmEmojiSearchOpen,
      setSearchOpen: value => { state.dmEmojiSearchOpen = !!value; },
      query: String(state.dmEmojiSearchQuery || ""),
      setQuery: value => { state.dmEmojiSearchQuery = String(value || ""); }
    };
    if (kind === "group") return {
      selected: String(state.groupEmojiSelectedPack || ""),
      setSelected: value => { state.groupEmojiSelectedPack = String(value || ""); },
      searchOpen: !!state.groupEmojiSearchOpen,
      setSearchOpen: value => { state.groupEmojiSearchOpen = !!value; },
      query: String(state.groupEmojiSearchQuery || ""),
      setQuery: value => { state.groupEmojiSearchQuery = String(value || ""); }
    };
    return {
      selected: String(state.emojiSelectedPack || localStorage.getItem("kwc.emojiPack") || ""),
      setSelected: value => {
        state.emojiSelectedPack = String(value || "");
        try { localStorage.setItem("kwc.emojiPack", state.emojiSelectedPack); } catch (_) {}
      },
      searchOpen: !!state.emojiSearchOpen,
      setSearchOpen: value => { state.emojiSearchOpen = !!value; },
      query: String(state.emojiSearchQuery || ""),
      setQuery: value => { state.emojiSearchQuery = String(value || ""); }
    };
  }

  function emojiPanelElement(kind = "public") {
    if (kind === "dm") return document.getElementById("kwc-dm-emoji-panel");
    if (kind === "group") return document.getElementById("kwc-group-emoji-panel");
    return document.getElementById("kwc-emoji-panel");
  }

  function emojiComposeInput(kind = "public") {
    if (kind === "dm") return document.getElementById("kwc-dm-input");
    if (kind === "group") return document.getElementById("kwc-group-input");
    return document.getElementById("kwc-message");
  }

  function emojiSearchHost(kind = "public") {
    if (kind === "dm") {
      const input = document.getElementById("kwc-dm-input");
      return input ? input.closest(".kwc-dm-conversation") : null;
    }
    if (kind === "group") {
      const input = document.getElementById("kwc-group-input");
      return input ? input.closest(".kwc-dm-conversation") : null;
    }
    const input = document.getElementById("kwc-message");
    return input ? input.closest(".kwc-form") : null;
  }

  function emojiSearchOverlay(kind = "public") {
    const host = emojiSearchHost(kind);
    return host ? host.querySelector(`:scope > .kwc-emoji-search-overlay[data-emoji-search-kind="${kind}"]`) : null;
  }

  function resetEmojiSearchState(kind = "public") {
    const ctx = emojiPanelContext(kind);
    ctx.setSearchOpen(false);
    ctx.setQuery("");
    const overlay = emojiSearchOverlay(kind);
    if (overlay) overlay.remove();
  }

  function positionEmojiSearchOverlay(kind = "public") {
    const overlay = emojiSearchOverlay(kind);
    const host = emojiSearchHost(kind);
    const compose = emojiComposeInput(kind);
    if (!overlay || !host || !compose || !compose.isConnected) return;
    const hostRect = host.getBoundingClientRect();
    const rect = compose.getBoundingClientRect();
    if (!rect || rect.width <= 0 || rect.height <= 0) return;
    overlay.style.left = Math.round(rect.left - hostRect.left - 1) + "px";
    overlay.style.top = Math.round(rect.top - hostRect.top - 1) + "px";
    overlay.style.width = Math.round(rect.width + 2) + "px";
    overlay.style.height = Math.round(Math.max(32, rect.height + 2)) + "px";
  }

  function refreshEmojiSearchOverlayPositions() {
    if (state.emojiSearchOpen) positionEmojiSearchOverlay("public");
    if (state.dmEmojiSearchOpen) positionEmojiSearchOverlay("dm");
    if (state.groupEmojiSearchOpen) positionEmojiSearchOverlay("group");
  }

  function updateEmojiSearchResults(kind = "public") {
    const panel = emojiPanelElement(kind);
    if (!panel) return;
    const current = emojiPanelContext(kind);
    const scroll = panel.querySelector(".kwc-emoji-scroll");
    if (!scroll) return;
    const matches = emojiPanelShownItems(state.emojiItems, current.selected, true, current.query);
    scroll.innerHTML = emojiPanelGridHtml(matches, current.selected, true, current.query);
    scroll.scrollTop = 0;
    installEmojiItemHandlers(panel);
  }

  function renderEmojiSearchOverlay(kind = "public", focus = false) {
    const ctx = emojiPanelContext(kind);
    const host = emojiSearchHost(kind);
    if (!ctx.searchOpen || !host) {
      const old = emojiSearchOverlay(kind);
      if (old) old.remove();
      return;
    }
    let overlay = emojiSearchOverlay(kind);
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.className = "kwc-emoji-search-overlay";
      overlay.dataset.emojiSearchKind = kind;
      overlay.innerHTML = `<span class="kwc-emoji-search-float-icon" aria-hidden="true">🔍</span><input type="search" class="kwc-input kwc-emoji-search-input" data-emoji-search-input maxlength="80" placeholder="${esc(t("emoji.searchPlaceholder", "Search emojis"))}">`;
      host.appendChild(overlay);
      const input = overlay.querySelector("[data-emoji-search-input]");
      if (input) {
        input.addEventListener("input", () => {
          const current = emojiPanelContext(kind);
          current.setQuery(input.value || "");
          updateEmojiSearchResults(kind);
        });
        input.addEventListener("keydown", event => {
          if (event.key !== "Escape") return;
          event.preventDefault();
          event.stopPropagation();
          closeEmojiSearch(kind);
        });
      }
    }
    const input = overlay.querySelector("[data-emoji-search-input]");
    if (input && input.value !== ctx.query) input.value = ctx.query;
    positionEmojiSearchOverlay(kind);
    if (focus && input) {
      setTimeout(() => {
        if (!input.isConnected) return;
        input.focus();
        try { input.select(); } catch (_) {}
      }, 0);
    }
  }

  function closeEmojiSearch(kind = "public", options = {}) {
    const panel = emojiPanelElement(kind);
    const ctx = emojiPanelContext(kind);
    if (!ctx.searchOpen && !ctx.query && !emojiSearchOverlay(kind)) return;
    resetEmojiSearchState(kind);
    if (options.render !== false && panel && panel.isConnected && !panel.classList.contains("kwc-hidden")) {
      renderCustomEmojiPanel(panel, kind);
    }
  }

  function installEmojiSearchDismissHandlers() {
    if (document.documentElement.dataset.kwcEmojiSearchDismissInstalled === "1") return;
    document.documentElement.dataset.kwcEmojiSearchDismissInstalled = "1";
    document.addEventListener("click", event => {
      const target = event.target && event.target.closest ? event.target : null;
      if (!target) return;
      if (target.closest(".kwc-emoji-search-overlay") || target.closest("[data-emoji-search-toggle]")) return;
      if (state.emojiSearchOpen) closeEmojiSearch("public");
      if (state.dmEmojiSearchOpen) closeEmojiSearch("dm");
      if (state.groupEmojiSearchOpen) closeEmojiSearch("group");
    });
    window.addEventListener("resize", refreshEmojiSearchOverlayPositions, {passive: true});
  }

  function emojiPanelShownItems(items, selectedPack, searchOpen, query) {
    const arr = Array.isArray(items) ? items : [];
    if (searchOpen && normalizeCustomEmojiSearchText(query)) return searchCustomEmojiItems(arr, query);
    if (selectedPack === KWC_EMOJI_RECENT_PACK) return recentCustomEmojiItems(arr);
    if (selectedPack === KWC_EMOJI_FAVORITES_PACK) return favoriteCustomEmojiItems(arr);
    return selectedPack ? arr.filter(item => String(item && item.pack || "") === selectedPack) : arr;
  }

  function emojiPanelGridHtml(shown, selectedPack, searchOpen, query) {
    const arr = Array.isArray(shown) ? shown : [];
    if (arr.length) return `<div class="kwc-emoji-grid">${arr.map(emojiButtonHtml).join("")}</div>`;
    const searching = searchOpen && !!normalizeCustomEmojiSearchText(query);
    const text = searching
      ? t("emoji.searchEmpty", "No matching emojis.")
      : selectedPack === KWC_EMOJI_RECENT_PACK
        ? t("emoji.emptyRecent", "No recently used emojis.")
        : selectedPack === KWC_EMOJI_FAVORITES_PACK
          ? t("emoji.emptyFavorites", "No favorite emojis.")
          : t("emoji.emptyPack", "No emojis here.");
    return `<div class="kwc-emoji-empty">${esc(text)}</div>`;
  }

  function setRenderedEmojiPanelHeight(kind, panel) {
    if (kind === "dm") setDirectMessageEmojiPanelHeight(panel);
    else if (kind === "group") setGroupChatEmojiPanelHeight(panel);
    else setEmojiPanelHeight(emojiPanelHeightPx(), false);
  }

  function refreshRecentEmojiGridIfVisible(panel) {
    if (!panel || !panel.isConnected) return;
    const kind = panel.id === "kwc-dm-emoji-panel" ? "dm" : (panel.id === "kwc-group-emoji-panel" ? "group" : "public");
    const ctx = emojiPanelContext(kind);
    if (ctx.searchOpen || ctx.selected !== KWC_EMOJI_RECENT_PACK) return;
    const scroll = panel.querySelector(".kwc-emoji-scroll");
    if (!scroll) return;
    const shown = recentCustomEmojiItems(state.emojiItems);
    scroll.innerHTML = emojiPanelGridHtml(shown, ctx.selected, false, "");
    installEmojiItemHandlers(panel);
  }

  function renderCustomEmojiPanel(panel, kind = "public") {
    if (!panel) return;
    const previousTabs = panel.querySelector(".kwc-emoji-tabs");
    const previousTabsScrollLeft = previousTabs ? Number(previousTabs.scrollLeft || 0) : 0;
    const packs = Array.isArray(state.emojiPacks) ? state.emojiPacks : [];
    const items = Array.isArray(state.emojiItems) ? state.emojiItems : [];
    if (!items.length) {
      panel.innerHTML = `<div class="kwc-emoji-scroll"><div class="kwc-emoji-empty">${esc(t("emoji.empty", "No emojis configured."))}</div></div>`;
      panel.classList.remove("kwc-hidden");
      setRenderedEmojiPanelHeight(kind, panel);
      installEmojiPanelWheelStep(panel);
      if (kind === "public") updateEmojiResizeHandleVisibility();
      return;
    }

    const ctx = emojiPanelContext(kind);
    let selectedPack = ctx.selected;
    const realPackIds = new Set(packs.map(pack => String(pack && pack.id || "")).filter(Boolean));
    if (selectedPack === KWC_EMOJI_FAVORITES_PACK && !emojiFavoritesAvailable()) selectedPack = "";
    if (selectedPack !== KWC_EMOJI_RECENT_PACK && selectedPack !== KWC_EMOJI_FAVORITES_PACK && (!selectedPack || !realPackIds.has(selectedPack))) {
      selectedPack = packs[0] && packs[0].id ? String(packs[0].id) : "";
      ctx.setSelected(selectedPack);
    }
    const recentCount = recentCustomEmojiItems(items).length;
    const favoriteCount = favoriteCustomEmojiItems(items).length;
    const tabs = [
      `<button type="button" class="kwc-emoji-tab kwc-emoji-recent-tab${selectedPack === KWC_EMOJI_RECENT_PACK && !ctx.searchOpen ? " kwc-active" : ""}" data-emoji-pack="${KWC_EMOJI_RECENT_PACK}">${esc(t("emoji.recent", "Recent"))} <span class="kwc-emoji-tab-count">${esc(String(recentCount))}</span></button>`,
      ...(emojiFavoritesAvailable() ? [`<button type="button" class="kwc-emoji-tab kwc-emoji-favorites-tab${selectedPack === KWC_EMOJI_FAVORITES_PACK && !ctx.searchOpen ? " kwc-active" : ""}" data-emoji-pack="${KWC_EMOJI_FAVORITES_PACK}">${esc(t("emoji.favorites", "Favorites"))} <span class="kwc-emoji-tab-count">${esc(String(favoriteCount))}</span></button>`] : []),
      ...packs.map(pack => {
        const id = String(pack.id || "");
        return `<button type="button" class="kwc-emoji-tab${id === selectedPack && !ctx.searchOpen ? " kwc-active" : ""}" data-emoji-pack="${esc(id)}">${esc(pack.label || id)} <span>${esc(pack.count || "")}</span></button>`;
      })
    ].join("");
    const shown = emojiPanelShownItems(items, selectedPack, ctx.searchOpen, ctx.query);
    panel.innerHTML = `<div class="kwc-emoji-toolbar"><button type="button" class="kwc-emoji-search-toggle${ctx.searchOpen ? " kwc-active" : ""}" data-emoji-search-toggle title="${esc(t("button.search", "Search"))}" aria-label="${esc(t("button.search", "Search"))}">🔍</button><div class="kwc-emoji-tabs">${tabs}</div></div>`
      + `<div class="kwc-emoji-scroll">${emojiPanelGridHtml(shown, selectedPack, ctx.searchOpen, ctx.query)}</div>`;
    panel.classList.remove("kwc-hidden");
    const renderedTabs = panel.querySelector(".kwc-emoji-tabs");
    if (renderedTabs && previousTabsScrollLeft > 0) {
      const maxScrollLeft = Math.max(0, renderedTabs.scrollWidth - renderedTabs.clientWidth);
      renderedTabs.scrollLeft = Math.min(previousTabsScrollLeft, maxScrollLeft);
    }
    setRenderedEmojiPanelHeight(kind, panel);
    installEmojiPanelWheelStep(panel);
    installEmojiSearchDismissHandlers();
    renderEmojiSearchOverlay(kind, false);

    panel.querySelectorAll("[data-emoji-pack]").forEach(btn => {
      btn.addEventListener("click", () => {
        const next = String(btn.dataset.emojiPack || "");
        ctx.setSelected(next);
        ctx.setSearchOpen(false);
        ctx.setQuery("");
        renderCustomEmojiPanel(panel, kind);
      });
    });
    const toggle = panel.querySelector("[data-emoji-search-toggle]");
    if (toggle) toggle.addEventListener("click", () => {
      const current = emojiPanelContext(kind);
      const open = !current.searchOpen;
      current.setSearchOpen(open);
      if (!open) current.setQuery("");
      renderCustomEmojiPanel(panel, kind);
      if (open) renderEmojiSearchOverlay(kind, true);
    });
    installEmojiItemHandlers(panel);
    if (kind === "public") updateEmojiResizeHandleVisibility();
  }

  function renderEmojiPanel() {
    const panel = document.getElementById("kwc-emoji-panel");
    if (!panel) return;
    if (!state.emojiPanelOpen || !canUseCustomEmoji()) {
      hideEmojiPanel();
      return;
    }
    renderCustomEmojiPanel(panel, "public");
  }

  function emojiCatalogCacheKey() {
    return "kwc.emojiCatalog.v2." + encodeURIComponent(String(apiBase || "default"));
  }

  function restoreCachedEmojiCatalog() {
    if (!state.config || state.config.emojiEnabled === false) return false;
    try {
      const raw = localStorage.getItem(emojiCatalogCacheKey());
      if (!raw) return false;
      const cached = JSON.parse(raw);
      if (!cached || !Array.isArray(cached.items)) return false;
      // Keep this only as a startup/retry bridge. A successful /emojis request
      // immediately replaces it, and very old browser state is ignored.
      const age = Date.now() - Number(cached.savedAt || 0);
      if (!Number.isFinite(age) || age < 0 || age > 7 * 24 * 60 * 60 * 1000) return false;
      state.emojiEnabled = cached.enabled !== false;
      state.emojiPacks = Array.isArray(cached.packs) ? cached.packs : [];
      state.emojiItems = cached.items.map(item => Object.assign({}, item, {url: apiResourceUrl(item && item.url)}));
      rebuildCustomEmojiLookups(state.emojiItems);
      state.emojiRenderSizePx = Math.max(16, Math.min(1024, Number(cached.renderSizePx ?? state.emojiRenderSizePx ?? 32)));
      state.emojiPickerSizePx = Math.max(24, Math.min(1024, Number(cached.pickerSizePx ?? state.emojiPickerSizePx ?? 44)));
      state.emojiMessageTokenLimit = Math.max(0, Math.floor(Number(cached.messageTokenLimit ?? state.emojiMessageTokenLimit ?? 0)));
      state.emojiTokenFormat = normalizeEmojiTokenFormat(cached.tokenFormat ?? state.emojiTokenFormat);
      applyEmojiPickerSize();
      return state.emojiItems.length > 0;
    } catch (_) {
      return false;
    }
  }

  function storeEmojiCatalogCache(payload) {
    if (!payload || payload.enabled === false || !Array.isArray(payload.items)) return;
    try {
      const compact = {
        savedAt: Date.now(),
        enabled: payload.enabled !== false,
        packs: Array.isArray(payload.packs) ? payload.packs : [],
        items: payload.items,
        renderSizePx: payload.renderSizePx,
        pickerSizePx: payload.pickerSizePx,
        messageTokenLimit: payload.messageTokenLimit,
        tokenFormat: payload.tokenFormat
      };
      localStorage.setItem(emojiCatalogCacheKey(), JSON.stringify(compact));
    } catch (_) {}
  }

  function refreshCustomEmojiRenderedSurfaces() {
    if (state.messages && state.messages.length) scheduleVirtualRender({preserveScroll: true, deferDuringScroll: false});
    renderPinnedBar();
    refreshOpenPinnedModal();
    if (state.dmModalOpen && Array.isArray(state.dmMessages)) {
      try { renderDirectMessageMessages(state.dmMessages, {preserveScroll: true}); } catch (_) {}
    }
    if (state.groupModalOpen && Array.isArray(state.groupMessages)) {
      try { renderGroupChatMessages(state.groupMessages, {preserveScroll: true}); } catch (_) {}
    }
    const notificationWrap = document.querySelector(".kwc-notification-inbox-backdrop");
    if (notificationWrap) {
      const items = readNotificationInbox();
      const rows = notificationWrap.querySelectorAll(".kwc-notification-row");
      rows.forEach((row, index) => {
        const item = items[index];
        if (!item) return;
        const title = row.querySelector(".kwc-notification-row-title");
        const body = row.querySelector(".kwc-notification-row-body");
        if (title) {
          title.innerHTML = renderCustomEmojiTokens(item.title || configuredNotificationTitle(), false, true);
          updateCustomEmojiOnlyClass(title);
        }
        if (body) {
          body.innerHTML = renderCustomEmojiTokens(item.body || "", false, true);
          updateCustomEmojiOnlyClass(body);
        }
      });
      installCustomEmojiImageRecovery(notificationWrap);
    }
  }

  function clearEmojiRetryTimer() {
    if (!state.emojiRetryTimer) return;
    clearTimeout(state.emojiRetryTimer);
    state.emojiRetryTimer = null;
  }

  function emojiRetryDelayMs() {
    const attempt = Math.max(0, Number(state.emojiRetryAttempt || 0));
    const base = attempt === 0 ? 250 : attempt === 1 ? 750 : Math.min(60000, 2000 * Math.pow(2, attempt - 2));
    const jitter = Math.floor(Math.random() * Math.min(350, Math.max(80, base * 0.2)));
    return Math.max(200, Math.floor(base + jitter));
  }

  function scheduleEmojiRetry(reason = "catalog-load-failed") {
    if (state.emojiRetryTimer || !state.config || state.config.emojiEnabled === false) return;
    const delay = emojiRetryDelayMs();
    state.emojiRetryAttempt = Math.min(10, Number(state.emojiRetryAttempt || 0) + 1);
    state.emojiRetryTimer = setTimeout(() => {
      state.emojiRetryTimer = null;
      loadEmojis({force: true, retryOnFailure: true, reason}).catch(() => {});
    }, delay);
  }

  async function loadEmojis(options = {}) {
    if (!state.config || state.config.emojiEnabled === false) {
      clearEmojiRetryTimer();
      state.emojiRetryAttempt = 0;
      state.emojiEnabled = false;
      state.emojiPacks = [];
      state.emojiItems = [];
      state.emojiById = new Map();
      state.emojiByAlias = new Map();
      updateEmojiButton();
      updateDirectMessageComposeControls();
      updateGroupChatComposeControls();
      return;
    }
    state.emojiLoading = true;
    try {
      const force = options && options.force === true;
      const res = await api("/emojis" + (force ? ("?_=" + Date.now()) : ""), force ? {cache: "no-store"} : {});
      state.emojiEnabled = res.enabled !== false;
      state.emojiPacks = Array.isArray(res.packs) ? res.packs : [];
      state.emojiItems = (Array.isArray(res.items) ? res.items : []).map(item => Object.assign({}, item, {
        url: apiResourceUrl(item && item.url)
      }));
      rebuildCustomEmojiLookups(state.emojiItems);
      // Do not bulk-preload every registered emoji here. Large catalogs can flood
      // the same HTTP origin with image requests and compete with the long-lived
      // SSE connection. Message emoji <img> nodes already use an empty alt value,
      // so the transport token is never painted while an image is loading.
      state.emojiRenderSizePx = Math.max(16, Math.min(1024, Number(res.renderSizePx ?? state.emojiRenderSizePx ?? 32)));
      state.emojiPickerSizePx = Math.max(24, Math.min(1024, Number(res.pickerSizePx ?? state.emojiPickerSizePx ?? 44)));
      applyEmojiPickerSize();
      updateDirectMessageComposeControls();
      updateGroupChatComposeControls();
      // Zero is a valid server value meaning unlimited; do not use `||` here.
      state.emojiMessageTokenLimit = Math.max(0, Math.floor(Number(res.messageTokenLimit ?? state.emojiMessageTokenLimit ?? 0)));
      state.emojiTokenFormat = normalizeEmojiTokenFormat(res.tokenFormat ?? state.emojiTokenFormat);
      storeEmojiCatalogCache(res);
      clearEmojiRetryTimer();
      state.emojiRetryAttempt = 0;
      refreshCustomEmojiRenderedSurfaces();
    } catch (e) {
      // A transient catalog failure must not erase the last known-good emoji list.
      // Existing messages/pickers continue using the cached client-side catalog
      // while a bounded exponential retry obtains a fresh copy.
      console.warn("KOKOTO WebChat emoji list failed; keeping previous catalog", {
        endpoint: "/emojis",
        status: Number(e && e.status || 0) || undefined,
        error: e && e.response && e.response.error ? String(e.response.error) : String(e && e.message || e || "unknown")
      });
      if (options.retryOnFailure !== false) scheduleEmojiRetry(options.reason || "catalog-load-failed");
      if (options.throwOnFailure === true) throw e;
      return false;
    } finally {
      state.emojiLoading = false;
      updateEmojiButton();
      updateDirectMessageComposeControls();
      updateGroupChatComposeControls();
      renderEmojiPanel();
    }
  }

  function canUpload() {
    const c = state.config || {};
    if (!c.uploadEnabled) return false;
    if (state.token) {
      if (state.role === "ADMIN") return c.uploadAllowAdmin !== false;
      if (state.role === "MODERATOR") return c.uploadAllowModerator !== false;
      return c.uploadAllowUser !== false;
    }
    return !!c.uploadAllowGuest && c.guestEnabled !== false;
  }

  function uploadAcceptList() {
    const exts = (state.config && Array.isArray(state.config.uploadAllowedExtensions))
      ? state.config.uploadAllowedExtensions
      : [];
    return exts.map(e => "." + String(e).replace(/^\./, "").trim()).filter(Boolean).join(",");
  }

  function normalizeInsertedMediaLinks(text) {
    const parts = String(text || "")
      .split(/\s+/)
      .map(part => part.trim())
      .filter(Boolean);
    return parts.length ? parts.join(" ") + " " : "";
  }

  function appendToMessage(text, options = {}) {
    const input = activeComposeInput();
    if (!input) return;
    setActiveComposeInput(input);
    const inserted = options.mediaLinks ? normalizeInsertedMediaLinks(text) : String(text || "");
    if (!inserted) return;

    const current = input.value || "";
    const needsSeparator = current && !/\s$/.test(current);
    input.value = current + (needsSeparator ? " " : "") + inserted;
    input.focus();
    try {
      input.selectionStart = input.selectionEnd = input.value.length;
    } catch (_) {}
  }

  function extensionFromMime(type) {
    type = String(type || "").toLowerCase();
    if (type === "image/jpeg") return "jpg";
    if (type === "image/png") return "png";
    if (type === "image/gif") return "gif";
    if (type === "image/webp") return "webp";
    if (type === "image/avif") return "avif";
    if (type === "image/bmp") return "bmp";
    if (type === "video/mp4") return "mp4";
    if (type === "video/webm") return "webm";
    if (type === "application/zip") return "zip";
    return "";
  }

  function looksLikeWindowsShortFileName(name) {
    const raw = String(name || "").trim();
    const dot = raw.lastIndexOf(".");
    const base = dot > 0 ? raw.slice(0, dot) : raw;
    const ext = dot > 0 ? raw.slice(dot + 1) : "";
    return /^[^~\/]{1,6}~[0-9]+$/i.test(base) && (!ext || /^[A-Za-z0-9]{1,3}$/.test(ext));
  }

  function clipboardFileName(file, index) {
    const rawName = String(file && file.name || "").trim();
    // Windows/Chromium can expose a DOS 8.3 alias (for example
    // 202608~1.JPG) for files pasted from Explorer. That alias is not the
    // user's actual source filename, so never preserve it as an "original"
    // name when no better clipboard File entry is available.
    if (rawName && rawName.includes(".") && !looksLikeWindowsShortFileName(rawName)) return rawName;

    const c = state.config || {};
    const fromMime = extensionFromMime(file && file.type);
    const rawExt = rawName.includes(".") ? rawName.slice(rawName.lastIndexOf(".") + 1).toLowerCase() : "";
    const configured = String(c.uploadClipboardImageDefaultExtension || "png").replace(/^\./, "").trim().toLowerCase();
    const ext = fromMime || rawExt || configured || "png";
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    return `clipboard-${stamp}-${index + 1}.${ext}`;
  }

  function normalizeUploadFile(file, index, source) {
    if (!file) return null;
    const name = source === "clipboard" ? clipboardFileName(file, index) : (file.name || clipboardFileName(file, index));
    if (typeof File !== "undefined" && file.name !== name) {
      try {
        return new File([file], name, {type: file.type || "application/octet-stream", lastModified: file.lastModified || Date.now()});
      } catch (_) {
        // Some older browsers do not allow File construction; fall through.
      }
    }
    return file;
  }


  function uploadProgressScopeId() {
    const id = String(state.activeComposeInputId || "");
    if (id === "kwc-dm-input" && document.getElementById("kwc-dm-upload-progress")) return "dm";
    if (id === "kwc-group-input" && document.getElementById("kwc-group-upload-progress")) return "group";
    return "main";
  }

  function uploadProgressElements(scope = null) {
    const name = scope || uploadProgressScopeId();
    const prefix = name === "dm" ? "kwc-dm-upload-progress" : (name === "group" ? "kwc-group-upload-progress" : "kwc-upload-progress");
    return {
      panel: document.getElementById(prefix),
      text: document.getElementById(prefix + "-text") || document.getElementById("kwc-upload-progress-text"),
      fill: document.getElementById(prefix + "-fill") || document.getElementById("kwc-upload-progress-fill"),
      cancel: document.getElementById(prefix + "-cancel") || document.getElementById("kwc-upload-cancel")
    };
  }

  function hideInactiveUploadProgressPanels(activeScope = null) {
    const keep = activeScope || uploadProgressScopeId();
    ["main", "dm", "group"].forEach(scope => {
      if (scope === keep) return;
      const el = uploadProgressElements(scope);
      if (el.panel) el.panel.classList.add("kwc-hidden");
    });
  }

  function setUploadProgressVisible(visible) {
    const scope = uploadProgressScopeId();
    hideInactiveUploadProgressPanels(scope);
    const panel = uploadProgressElements(scope).panel;
    if (panel) panel.classList.toggle("kwc-hidden", !visible);
  }

  function setUploadControlsBusy(busy) {
    const uploadBtn = document.getElementById("kwc-upload");
    const fileInput = document.getElementById("kwc-file");
    const dmUploadBtn = document.getElementById("kwc-dm-upload");
    const dmFileInput = document.getElementById("kwc-dm-file");
    const groupUploadBtn = document.getElementById("kwc-group-upload");
    const groupFileInput = document.getElementById("kwc-group-file");
    if (uploadBtn) uploadBtn.disabled = !!busy;
    if (fileInput) fileInput.disabled = !!busy;
    if (dmUploadBtn) dmUploadBtn.disabled = !!busy;
    if (dmFileInput) dmFileInput.disabled = !!busy;
    if (groupUploadBtn) groupUploadBtn.disabled = !!busy;
    if (groupFileInput) groupFileInput.disabled = !!busy;
  }

  function updateUploadProgress(label, percent, active = true) {
    const scope = uploadProgressScopeId();
    hideInactiveUploadProgressPanels(scope);
    const {panel, text, fill, cancel} = uploadProgressElements(scope);

    if (panel) panel.classList.toggle("kwc-hidden", !active);
    if (text) text.textContent = label || "";
    if (fill) {
      const p = Math.max(0, Math.min(100, Number(percent) || 0));
      fill.style.width = p.toFixed(1) + "%";
    }
    if (cancel) {
      cancel.disabled = !active || !state.uploadActive;
      cancel.textContent = state.uploadCancelRequested ? t("upload.canceling", "Canceling...") : t("button.cancel", "Cancel");
    }
  }

  function hideUploadProgressSoon(label) {
    if (label) updateUploadProgress(label, 100, true);
    setTimeout(() => {
      if (state.uploadActive) return;
      setUploadProgressVisible(false);
      updateUploadProgress("", 0, false);
    }, 900);
  }

  function cancelCurrentUpload() {
    if (!state.uploadActive) return;
    state.uploadCancelRequested = true;
    updateUploadProgress(t("upload.canceling", "Canceling..."), 0, true);
    try {
      if (state.uploadXhr) state.uploadXhr.abort();
    } catch (_) {}
  }

  function uploadFormWithProgress(form, progressCallback) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      state.uploadXhr = xhr;

      xhr.upload.onprogress = event => {
        if (event && event.lengthComputable && typeof progressCallback === "function") {
          progressCallback(event.loaded, event.total);
        }
      };

      xhr.onload = () => {
        state.uploadXhr = null;
        let data = null;
        try {
          data = JSON.parse(xhr.responseText || "{}");
        } catch (_) {
          data = {ok: false, error: "invalid_response"};
        }
        if (xhr.status < 200 || xhr.status >= 300) {
          reject({error: data && data.error ? data.error : ("HTTP " + xhr.status)});
          return;
        }
        resolve(data);
      };

      xhr.onerror = () => {
        state.uploadXhr = null;
        reject({error: "network"});
      };

      xhr.onabort = () => {
        state.uploadXhr = null;
        reject({aborted: true});
      };

      xhr.open("POST", apiBase + "/upload", true);
      if (state.token) xhr.setRequestHeader("Authorization", "Bearer " + state.token);
      xhr.send(form);
    });
  }

  function clipboardFiles(event) {
    const dt = event.clipboardData;
    if (!dt) return [];

    // On Windows Chromium, DataTransferItem#getAsFile() may expose the DOS
    // 8.3 alias while DataTransfer.files exposes the long filename. Prefer the
    // FileList and use item files only as a fallback/repair source.
    const listFiles = dt.files && dt.files.length ? Array.from(dt.files).filter(Boolean) : [];
    const itemFiles = [];
    if (dt.items && dt.items.length) {
      for (const item of Array.from(dt.items)) {
        if (item.kind !== "file") continue;
        const file = item.getAsFile();
        if (file) itemFiles.push(file);
      }
    }

    if (!listFiles.length) return itemFiles;
    if (!itemFiles.length) return listFiles;

    const count = Math.max(listFiles.length, itemFiles.length);
    const files = [];
    for (let i = 0; i < count; i++) {
      const listFile = listFiles[i] || null;
      const itemFile = itemFiles[i] || null;
      if (!listFile) { if (itemFile) files.push(itemFile); continue; }
      if (!itemFile) { files.push(listFile); continue; }
      const listShort = looksLikeWindowsShortFileName(listFile.name);
      const itemShort = looksLikeWindowsShortFileName(itemFile.name);
      files.push(listShort && !itemShort ? itemFile : listFile);
    }
    return files;
  }

  async function handlePasteUpload(event) {
    const c = state.config || {};
    if (!c.uploadClipboardEnabled) return;

    const files = clipboardFiles(event);
    if (!files.length) return;

    event.preventDefault();
    event.stopPropagation();
    await uploadFiles(files, "clipboard");
  }

  function isFileDragEvent(event) {
    const dt = event && event.dataTransfer;
    if (!dt) return false;
    try {
      const types = Array.from(dt.types || []);
      return types.includes("Files") || types.includes("application/x-moz-file");
    } catch (_) {
      return false;
    }
  }

  function dropEventFiles(event) {
    const dt = event && event.dataTransfer;
    if (!dt || !dt.files || !dt.files.length) return [];
    return Array.from(dt.files).filter(file => file && (file.name || file.size || file.type));
  }

  function setDropOverlayVisible(visible, messageKey = "") {
    const overlay = document.getElementById("kwc-drop-overlay");
    if (!overlay) return;

    const title = document.getElementById("kwc-drop-title");
    const subtitle = document.getElementById("kwc-drop-subtitle");

    if (messageKey === "busy") {
      if (title) title.textContent = t("upload.dropBusy", "Upload is already in progress.");
      if (subtitle) subtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    } else if (messageKey === "denied") {
      if (title) title.textContent = t("upload.dropDenied", "File upload is not allowed.");
      if (subtitle) subtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    } else {
      if (title) title.textContent = t("upload.dropTitle", "Drop files to upload");
      if (subtitle) subtitle.textContent = t("upload.dropSubtitle", "Release inside the chat panel.");
    }

    overlay.classList.toggle("kwc-hidden", !visible);
    overlay.setAttribute("aria-hidden", visible ? "false" : "true");
  }

  function hideDropOverlay() {
    state.dragUploadDepth = 0;
    setDropOverlayVisible(false);
  }

  function installDragAndDropUpload(root) {
    const panel = root && root.querySelector ? root.querySelector(".kwc-panel") : null;
    if (!panel || panel.dataset.dropUploadInstalled === "1") return;
    panel.dataset.dropUploadInstalled = "1";

    const overlayState = () => {
      if (state.uploadActive) return "busy";
      if (!canUpload()) return "denied";
      return "ready";
    };

    panel.addEventListener("dragenter", event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      if (state.minimized) {
        hideDropOverlay();
        if (event.dataTransfer) event.dataTransfer.dropEffect = "none";
        return;
      }
      state.dragUploadDepth++;
      const status = overlayState();
      if (event.dataTransfer) event.dataTransfer.dropEffect = status === "ready" ? "copy" : "none";
      setDropOverlayVisible(true, status === "ready" ? "" : status);
    }, {capture: true});

    panel.addEventListener("dragover", event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      if (state.minimized) {
        hideDropOverlay();
        if (event.dataTransfer) event.dataTransfer.dropEffect = "none";
        return;
      }
      const status = overlayState();
      if (event.dataTransfer) event.dataTransfer.dropEffect = status === "ready" ? "copy" : "none";
      setDropOverlayVisible(true, status === "ready" ? "" : status);
    }, {capture: true});

    panel.addEventListener("dragleave", event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      state.dragUploadDepth = Math.max(0, state.dragUploadDepth - 1);
      if (state.dragUploadDepth === 0) setDropOverlayVisible(false);
    }, {capture: true});

    panel.addEventListener("drop", async event => {
      if (!isFileDragEvent(event)) return;
      event.preventDefault();
      event.stopPropagation();
      const files = dropEventFiles(event);
      hideDropOverlay();
      if (state.minimized || !files.length) return;
      if (state.uploadActive) {
        alert(t("upload.dropBusy", "Upload is already in progress."));
        return;
      }
      await uploadFiles(files, "drop");
    }, {capture: true});

    document.addEventListener("dragend", hideDropOverlay, {capture: true});
    document.addEventListener("drop", event => {
      if (isFileDragEvent(event)) hideDropOverlay();
    }, {capture: true});
  }

  async function uploadSelectedFiles(e) {
    const input = e.target;
    const files = Array.from(input.files || []);
    input.value = "";
    await uploadFiles(files, "file");
  }

  function uploadProgressHtml(prefix) {
    const id = String(prefix || "kwc-upload-progress");
    return `<div class="kwc-upload-progress kwc-hidden" id="${id}" aria-live="polite">
      <div class="kwc-upload-progress-head">
        <span id="${id}-text">${esc(t("upload.ready", "Ready"))}</span>
        <button class="kwc-button kwc-upload-cancel" id="${id}-cancel" type="button">${esc(t("button.cancel", "Cancel"))}</button>
      </div>
      <div class="kwc-upload-progress-bar"><div id="${id}-fill"></div></div>
    </div>`;
  }

  async function uploadFiles(files, source) {
    files = Array.from(files || []).map((file, index) => normalizeUploadFile(file, index, source)).filter(Boolean);
    if (!files.length) return;

    if (state.uploadActive) {
      alert(t("upload.dropBusy", "Upload is already in progress."));
      return;
    }

    if (!canUpload()) {
      alert(t("alert.uploadNotAllowed", "File upload is not allowed."));
      return;
    }

    const c = state.config || {};
    const maxFilesConfig = Number(c.uploadMaxFilesPerMessage);
    const maxFiles = Number.isFinite(maxFilesConfig) && maxFilesConfig > 0 ? Math.floor(maxFilesConfig) : 0;
    const maxFileSizeConfig = Number(c.uploadMaxFileSizeMb);
    const maxBytes = Number.isFinite(maxFileSizeConfig) && maxFileSizeConfig > 0 ? maxFileSizeConfig * 1024 * 1024 : 0;
    const allowed = new Set((c.uploadAllowedExtensions || []).map(x => String(x).toLowerCase().replace(/^\./, "")));
    const selected = maxFiles > 0 ? files.slice(0, maxFiles) : files;

    if (maxFiles > 0 && files.length > maxFiles) {
      alert(fmt("alert.uploadTooMany", "Only {max} file(s) can be selected at once.", {max: maxFiles}));
    }

    const valid = [];
    for (const file of selected) {
      const ext = (file.name.split(".").pop() || "").toLowerCase();
      if (allowed.size && !allowed.has(ext)) {
        alert(fmt("alert.uploadExtensionDenied", "This file type is not allowed: {name}", {name: file.name}));
        continue;
      }
      if (maxBytes > 0 && file.size > maxBytes) {
        alert(fmt("alert.uploadTooLarge", "File is too large: {name}", {name: file.name}));
        continue;
      }
      valid.push(file);
    }

    if (!valid.length) return;

    const totalBytes = valid.reduce((sum, file) => sum + Math.max(1, Number(file.size) || 1), 0);
    let completedBytes = 0;
    const uploaded = [];

    state.uploadCancelRequested = false;
    state.uploadActive = true;
    const uploadIntoModal = state.activeComposeInputId === "kwc-dm-input" || state.activeComposeInputId === "kwc-group-input";
    setUploadControlsBusy(true);
    updateUploadProgress(t("upload.preparing", "Preparing upload..."), 0, true);

    try {
      for (let i = 0; i < valid.length; i++) {
        if (state.uploadCancelRequested) break;
        const file = valid[i];
        const form = new FormData();
        form.append("file", file, file.name);

        if (state.token) {
          // Authentication is sent with the Authorization header.
        } else {
          form.append("guestName", currentGuestNameForSubmit());
        }

        const baseLabel = fmt("upload.progress", "Uploading {current}/{total}: {name}", {
          current: i + 1,
          total: valid.length,
          name: file.name
        });

        try {
          const res = await uploadFormWithProgress(form, (loaded, size) => {
            const fileSize = Math.max(1, Number(size) || Number(file.size) || 1);
            const overall = totalBytes > 0
              ? ((completedBytes + Math.min(fileSize, loaded)) / totalBytes) * 100
              : ((i + Math.min(1, loaded / fileSize)) / valid.length) * 100;
            updateUploadProgress(baseLabel, overall, true);
          });
          completedBytes += Math.max(1, Number(file.size) || 1);

          if (!res.ok) {
            alertResponse("alert.uploadFailed", "Upload failed: {error}", res);
            continue;
          }
          uploaded.push(normalizeReturnedUploadUrl(res.url));
          updateUploadProgress(baseLabel, totalBytes > 0 ? (completedBytes / totalBytes) * 100 : ((i + 1) / valid.length) * 100, true);
        } catch (err) {
          if (err && err.aborted) {
            updateUploadProgress(t("upload.canceled", "Upload canceled."), 0, true);
            break;
          }
          alert(err && err.error && err.error !== "network"
            ? fmt("alert.uploadFailed", "Upload failed: {error}", {error: responseError(err)})
            : t("alert.serverUnavailable", "Cannot connect to chat server."));
        }
      }
    } finally {
      state.uploadXhr = null;
      state.uploadActive = false;
      setUploadControlsBusy(false);
    }

    if (uploaded.length) {
      const dmTargetActive = state.activeComposeInputId === "kwc-dm-input" && !!document.getElementById("kwc-dm-input");
      const groupTargetActive = state.activeComposeInputId === "kwc-group-input" && !!document.getElementById("kwc-group-input");
      const modalTargetActive = dmTargetActive || groupTargetActive;
      const text = normalizeInsertedMediaLinks(uploaded.join(" "));
      const mode = String((state.config && state.config.uploadClipboardSendMode) || "insert").toLowerCase();
      if (!modalTargetActive && source === "clipboard" && mode === "send") {
        const ok = await sendMessageText(text, null, {forceLatest: true});
        if (!ok) appendToMessage(text, {mediaLinks: true});
      } else {
        appendToMessage(text, {mediaLinks: true});
      }
      hideUploadProgressSoon(t("upload.complete", "Upload complete."));
    } else if (state.uploadCancelRequested) {
      hideUploadProgressSoon(t("upload.canceled", "Upload canceled."));
    } else {
      hideUploadProgressSoon("");
    }
  }



  function documentPictureInPictureSupported() {
    if (state.isPip) return false;
    const valid = candidate => !!candidate && typeof candidate.requestWindow === "function";
    try { if (valid(window.documentPictureInPicture)) return true; } catch (_) {}
    // Adapter mode runs in a same-origin generated iframe. Some browser builds expose
    // Document PiP only on the top-level Window, so also feature-detect the parent.
    try { if (window.parent && window.parent !== window && valid(window.parent.documentPictureInPicture)) return true; } catch (_) {}
    return false;
  }

  function updatePipButton() {
    const btn = document.getElementById("kwc-pip");
    const c = state.config || {};
    const enabled = c.uiPictureInPictureEnabled === true && !state.isPip && !guestChatHidden() && documentPictureInPictureSupported();
    if (!btn) return;
    if (!enabled) {
      btn.classList.add("kwc-hidden");
      btn.hidden = true;
      btn.setAttribute("aria-hidden", "true");
      btn.style.display = "none";
      btn.disabled = true;
      return;
    }
    const visible = !state.minimized;
    btn.classList.toggle("kwc-hidden", !visible);
    btn.hidden = !visible;
    btn.setAttribute("aria-hidden", visible ? "false" : "true");
    btn.style.display = visible ? "" : "none";
    btn.disabled = !visible;
    btn.title = t("button.pip", "PIP");
  }

  function canRunWebCommands() {
    return !!(state.commandsEnabled && state.commandsCanRun && state.token && (state.commandsAllowAll || (Array.isArray(state.commands) && state.commands.length)));
  }

  function updateCommandButton() {
    const btn = document.getElementById("kwc-command");
    if (!btn) return;
    const visible = canRunWebCommands() && state.commandsShowButton !== false && !state.minimized;
    btn.classList.toggle("kwc-hidden", !visible);
    btn.title = t("button.commands", "Commands");
    if (!visible) hideCommandPanel();
  }

  async function loadCommands() {
    if (!state.config || !state.config.commandsEnabled || !state.token) {
      state.commands = [];
      state.commandsCanRun = false;
      state.commandsEnabled = !!(state.config && state.config.commandsEnabled);
      state.commandsAllowAll = !!(state.config && state.config.commandsAllowAll);
      state.commandsShowButton = state.config ? state.config.commandsShowButton !== false : true;
      state.commandsShowSlashPanel = state.config ? state.config.commandsShowSlashPanel !== false : true;
      state.commandsRunFromChatInput = state.config ? state.config.commandsRunFromChatInput === true : false;
      state.commandsRequireConfirm = state.config ? state.config.commandsRequireConfirm !== false : true;
      state.commandMaxLength = state.config ? normalizeCommandMaxLength(state.config.commandsMaxLength, 0) : 0;
      updateCommandButton();
      hideCommandPanel();
      return;
    }

    try {
      const res = await api("/commands");
      state.commandsEnabled = !!res.enabled;
      state.commandsCanRun = !!res.canRun;
      state.commandsAllowAll = !!res.allowAll;
      state.commandsShowButton = res.showButton !== false;
      state.commandsShowSlashPanel = res.showSlashPanel !== false;
      state.commandsRunFromChatInput = res.runFromChatInput === true;
      state.commandsRequireConfirm = res.requireConfirm !== false;
      state.commandMaxLength = normalizeCommandMaxLength(res.maxLength, state.commandMaxLength || 0);
      state.commands = Array.isArray(res.presets) ? res.presets : [];
    } catch (e) {
      state.commands = [];
      state.commandsCanRun = false;
    }
    updateCommandButton();
    updateCommandPanel();
  }

  function commandMatches(preset, query) {
    if (!query) return true;
    const q = query.toLowerCase();
    return [preset.id, preset.label, preset.description, preset.command]
      .some(v => String(v || "").toLowerCase().includes(q));
  }

  function hasCommandMaxLength() {
    return Number(state.commandMaxLength || 0) > 0;
  }

  function commandMaxLengthAttr() {
    return hasCommandMaxLength() ? ` maxlength="${esc(state.commandMaxLength)}"` : "";
  }

  function commandMaxLengthHintHtml() {
    return hasCommandMaxLength()
      ? `<small class="kwc-command-limit">${esc(fmt("commands.maxLengthHint", "Maximum: {max} characters", {max: state.commandMaxLength}))}</small>`
      : "";
  }

  function hideCommandPanel() {
    const panel = document.getElementById("kwc-command-panel");
    if (panel && !panel.classList.contains("kwc-hidden")) panel.classList.add("kwc-hidden");
  }

  let commandPanelRenderFrame = 0;
  let messageInputComposing = false;

  function scheduleCommandPanelUpdate() {
    if (messageInputComposing || commandPanelRenderFrame) return;
    commandPanelRenderFrame = requestAnimationFrame(() => {
      commandPanelRenderFrame = 0;
      if (!messageInputComposing) updateCommandPanel();
    });
  }

  function updateCommandPanel() {
    const panel = document.getElementById("kwc-command-panel");
    const input = document.getElementById("kwc-message");
    if (!panel || !input) return;
    const value = String(input.value || "").trim();
    if (!canRunWebCommands() || state.commandsRunFromChatInput !== true || state.commandsShowSlashPanel === false || !value.startsWith("/")) {
      // Normal chat typing is the hot path. Do not rewrite hidden command-panel
      // DOM for every keystroke; that forces unnecessary style/compositor work
      // and can make the input caret/text visibly trail behind on map views.
      hideCommandPanel();
      return;
    }

    const query = value.slice(1).trim();
    if (state.commandsAllowAll) {
      if (!query) {
        panel.classList.add("kwc-hidden");
        panel.innerHTML = "";
        return;
      }
      const tooLong = hasCommandMaxLength() && query.length > state.commandMaxLength;
      panel.innerHTML = tooLong ?
        `<div class="kwc-command-empty">${esc(fmt("commands.tooLong", "Command is too long. Maximum: {max} characters.", {max: state.commandMaxLength}))}</div>` :
        `<button type="button" class="kwc-command-inline-item kwc-command-direct" data-run-direct-command="${esc(query)}">
          <span class="kwc-command-label">${esc(t("commands.runDirect", "Run command"))}</span>
          <span class="kwc-command-preview">/${esc(query)}</span>
        </button>`;
      panel.querySelectorAll("[data-run-direct-command]").forEach(btn => {
        btn.onclick = async e => {
          e.preventDefault();
          e.stopPropagation();
          await runDirectCommand(btn.getAttribute("data-run-direct-command"));
          hideCommandPanel();
          input.value = "";
        };
      });
      panel.classList.remove("kwc-hidden");
      return;
    }

    const items = (Array.isArray(state.commands) ? state.commands : []).filter(p => commandMatches(p, query)).slice(0, 8);
    if (!items.length) {
      panel.innerHTML = `<div class="kwc-command-empty">${esc(t("commands.noMatches", "No matching commands."))}</div>`;
      panel.classList.remove("kwc-hidden");
      return;
    }

    panel.innerHTML = items.map(p => `
      <button type="button" class="kwc-command-inline-item" data-run-command="${esc(p.id)}">
        <span class="kwc-command-label">${esc(p.label || p.id)}</span>
        <span class="kwc-command-preview">/${esc(p.command || "")}</span>
      </button>
    `).join("");
    panel.querySelectorAll("[data-run-command]").forEach(btn => {
      btn.onclick = async e => {
        e.preventDefault();
        e.stopPropagation();
        await runCommandPreset(btn.getAttribute("data-run-command"));
        hideCommandPanel();
        input.value = "";
      };
    });
    panel.querySelectorAll("[data-run-direct-command]").forEach(btn => {
      btn.onclick = async e => {
        e.preventDefault();
        e.stopPropagation();
        await runDirectCommand(btn.getAttribute("data-run-direct-command"));
        hideCommandPanel();
        input.value = "";
      };
    });
    panel.classList.remove("kwc-hidden");
  }

  function openCommandModal() {
    if (!canRunWebCommands()) {
      alert(t("commands.notAvailable", "Command panel is not available."));
      return;
    }

    const old = document.getElementById("kwc-command-modal");
    if (old) old.remove();
    const wrap = document.createElement("div");
    wrap.className = "kwc-modal-backdrop kwc-user-prefs-backdrop";
    wrap.id = "kwc-command-modal";
    applyDetachedModalTheme(wrap);
    wrap.innerHTML = `
      <div class="kwc-modal kwc-command-modal">
        <div class="kwc-modal-head">
          <h3>${t("commands.title", "Server commands")}</h3>
          <button class="kwc-button" id="kwc-command-close">${t("button.close", "Close")}</button>
        </div>
        <p>${state.commandsAllowAll ? t("commands.descriptionAll", "Run any server console command from the web UI.") : t("commands.description", "Run a pre-approved server command from the web UI.")}</p>
        ${state.commandsAllowAll ? `<div class="kwc-command-direct-box"><input class="kwc-input" id="kwc-command-direct"${commandMaxLengthAttr()} placeholder="${t("commands.directPlaceholder", "Command without /")}"><button class="kwc-button" id="kwc-command-direct-run">${t("button.run", "Run")}</button></div>${commandMaxLengthHintHtml()}` : `<input class="kwc-input" id="kwc-command-search" placeholder="${t("commands.search", "Search preset commands")}"><div class="kwc-command-list" id="kwc-command-list"></div>`}
      </div>
    `;
    document.body.appendChild(wrap);
    const search = wrap.querySelector("#kwc-command-search");
    const directInput = wrap.querySelector("#kwc-command-direct");
    const directRun = wrap.querySelector("#kwc-command-direct-run");
    if (directRun && directInput) {
      const submitDirect = async () => {
        const ok = await runDirectCommand(String(directInput.value || ""));
        if (ok) wrap.remove();
      };
      directRun.onclick = submitDirect;
      directInput.addEventListener("keydown", e => { if (e.key === "Enter" && !e.isComposing) submitDirect(); });
    }
    const render = () => renderCommandList(wrap, String(search ? search.value : ""));
    wrap.querySelector("#kwc-command-close").onclick = () => wrap.remove();
    wrap.addEventListener("click", e => { if (e.target === wrap) wrap.remove(); });
    if (search) {
      search.addEventListener("input", render);
      render();
      setTimeout(() => search.focus(), 0);
    } else if (directInput) {
      setTimeout(() => directInput.focus(), 0);
    }
  }

  function renderCommandList(wrap, query) {
    const list = wrap.querySelector("#kwc-command-list");
    if (!list) return;
    const q = String(query || "").trim();
    const items = (Array.isArray(state.commands) ? state.commands : []).filter(p => commandMatches(p, q));
    if (!items.length) {
      list.innerHTML = `<div class="kwc-command-empty">${esc(t("commands.noMatches", "No matching commands."))}</div>`;
      return;
    }
    list.innerHTML = items.map(p => `
      <div class="kwc-command-item">
        <div class="kwc-command-main">
          <strong>${esc(p.label || p.id)}</strong>
          ${p.description ? `<small>${esc(p.description)}</small>` : ""}
          <code>/${esc(p.command || "")}</code>
        </div>
        <button class="kwc-button" data-run-command="${esc(p.id)}">${t("button.run", "Run")}</button>
      </div>
    `).join("");
    list.querySelectorAll("[data-run-command]").forEach(btn => {
      btn.onclick = async () => {
        const ok = await runCommandPreset(btn.getAttribute("data-run-command"));
        if (ok) wrap.remove();
      };
    });
  }


  async function runDirectCommand(command) {
    command = String(command || "").trim();
    if (command.startsWith("/")) command = command.slice(1).trim();
    if (!state.commandsAllowAll || !command) return false;
    if (hasCommandMaxLength() && command.length > state.commandMaxLength) {
      alert(fmt("commands.tooLong", "Command is too long. Maximum: {max} characters.", {max: state.commandMaxLength}));
      return false;
    }
    const needConfirm = state.commandsRequireConfirm !== false;
    if (needConfirm && !confirmPlain(fmt("commands.confirm", "Run command: /{command}?", {command}))) return false;
    try {
      const res = await api("/commands/run", {method: "POST", body: JSON.stringify({command})});
      if (!res.ok) {
        alertResponse("commands.failed", "Command failed: {error}", res);
        return false;
      }
      alert(fmt("commands.submitted", "Command submitted: {label}", {label: command}));
      return true;
    } catch (e) {
      alert(t("alert.serverUnavailable", "Cannot connect to chat server."));
      return false;
    }
  }

  async function runCommandPreset(id) {
    const preset = state.commands.find(p => p && p.id === id);
    if (!preset) return false;
    const needConfirm = state.commandsRequireConfirm !== false || preset.confirm !== false;
    if (needConfirm && !confirmPlain(fmt("commands.confirm", "Run command: /{command}?", {command: preset.command || preset.label || id}))) return false;
    try {
      const res = await api("/commands/run", {method: "POST", body: JSON.stringify({id})});
      if (!res.ok) {
        alertResponse("commands.failed", "Command failed: {error}", res);
        return false;
      }
      alert(fmt("commands.submitted", "Command submitted: {label}", {label: preset.label || id}));
      return true;
    } catch (e) {
      alert(t("alert.serverUnavailable", "Cannot connect to chat server."));
      return false;
    }
  }

  function setSendControlsBusy(busy) {
    const sendBtn = document.getElementById("kwc-send");
    const input = document.getElementById("kwc-message");
    if (sendBtn) {
      sendBtn.disabled = !!busy;
      sendBtn.classList.toggle("kwc-busy", !!busy);
    }
    if (input) {
      input.dataset.kwcSending = busy ? "1" : "0";
    }
  }

  async function sendMessageText(text, inputToClear, options = {}) {
    text = String(text || "").trim();
    if (!text) return false;
    const emojiLimit = Number(state.emojiMessageTokenLimit || 0);
    if (emojiLimit > 0 && emojiTokenCount(text) > emojiLimit) {
      alert(fmt("alert.emojiTooMany", "Only {max} emoji(s) can be used in one message.", {max: emojiLimit}));
      return false;
    }
    if (state.sendInFlight && options.allowConcurrent !== true) {
      return false;
    }
    if (options.allowConcurrent !== true) {
      state.sendInFlight = true;
      state.sendInFlightSince = Date.now();
      state.sendInFlightText = text;
      setSendControlsBusy(true);
    }

    const payload = {message: text};
    if (state.replyTarget && state.replyTarget.id) {
      payload.replyToId = state.replyTarget.id;
      payload.replyToSender = state.replyTarget.sender || "";
      payload.replyToPreview = state.replyTarget.preview || "";
    }
    if (state.token) {
      payload.token = state.token;
    } else {
      payload.guestName = currentGuestNameForSubmit();
      if (state.captchaPass) {
        payload.captchaPass = state.captchaPass;
      }
      if (state.captcha) {
        payload.captchaId = state.captcha.id;
        const captchaInput = document.getElementById("kwc-captcha-a");
        payload.captchaAnswer = captchaInput ? captchaInput.value.trim() : "";
      }
    }

    // Clear the compose UI before starting network work. The server can publish
    // the message over SSE before the POST response returns (for example while
    // Web Push/notification work is still finishing). Waiting for the response
    // made the sent text visibly remain in the input for hundreds of ms.
    if (inputToClear) inputToClear.value = "";
    clearReplyTarget();

    const publicMessageBox = document.getElementById("kwc-messages");
    const followLatestAfterSend = !!publicMessageBox && !state.historyHasAfter && isAutoFollowBottom(publicMessageBox);
    try {
      if (followLatestAfterSend) markExplicitLatestFollow(options.forceLatest ? "send-forced" : "send", 4500);
      const res = await api("/send", {method: "POST", body: JSON.stringify(payload), returnHttpErrorResponse: true});
      if (!res.ok) {
        if (res.captchaPass) {
          state.captchaPass = res.captchaPass;
          localStorage.setItem("kwc.captchaPass", state.captchaPass);
        }

        if (state.captchaPass && state.config && state.config.captchaEnabled && !state.config.captchaRequireOnEachMessage) {
          hideCaptchaUi();
        }

        if (res.error === "captcha_failed") {
          state.captchaPass = "";
          localStorage.removeItem("kwc.captchaPass");
          await refreshCaptcha(true);
        } else {
          await refreshCaptcha();
        }

        if (res.error === "rate_limited") {
          alert(t("alert.rateLimited", "You are sending messages too quickly. Please wait."));
        } else if (res.error === "emoji_limit") {
          alert(fmt("alert.emojiTooMany", "Only {max} emoji(s) can be used in one message.", {max: state.emojiMessageTokenLimit || ""}));
        } else {
          alertResponse("alert.sendFailed", "Send failed: {error}", res);
        }
        return false;
      }
      if (res.captchaPass) {
        state.captchaPass = res.captchaPass;
        localStorage.setItem("kwc.captchaPass", state.captchaPass);
      }
      if (followLatestAfterSend) forceLatestChatView(options.forceLatest ? "send-forced" : "send");
      setTimeout(() => {
        if (followLatestAfterSend && state.autoFollowLatest) loadHistory(false, {skipIfUnchanged: true});
      }, 180);
      await refreshCaptcha();
      return true;
    } catch (e) {
      alert(t("alert.serverUnavailable", "Cannot connect to chat server."));
      return false;
    } finally {
      if (options.allowConcurrent !== true) {
        state.sendInFlight = false;
        state.sendInFlightSince = 0;
        state.sendInFlightText = "";
        setSendControlsBusy(false);
      }
    }
  }

  async function sendMessage() {
    hideMentionAutocomplete();
    const input = document.getElementById("kwc-message");
    const text = input ? input.value.trim() : "";
    if (!text) return;
    if (state.sendInFlight) return;
    if (state.commandsAllowAll && state.commandsRunFromChatInput === true && canRunWebCommands() && text.startsWith("/")) {
      state.sendInFlight = true;
      state.sendInFlightSince = Date.now();
      state.sendInFlightText = text;
      setSendControlsBusy(true);
      try {
        // Match normal chat sends: once execution is attempted the compose box
        // is cleared immediately and is not restored on failure.
        if (input) input.value = "";
        await runDirectCommand(text);
      } finally {
        state.sendInFlight = false;
        state.sendInFlightSince = 0;
        state.sendInFlightText = "";
        setSendControlsBusy(false);
        hideCommandPanel();
      }
      return;
    }
    await sendMessageText(text, input);
  }


