#!/usr/bin/env node
/* KWC 파일 안내 / KWC file guide
 * RC21 창/팝업/멀티윈도우/최소화/Guest 인증 UI 계약을 검증한다.
 * Verifies the RC21 static contract for window parity, scoped overlays, multi-window fallbacks, minimized geometry, guest-safe auth, and event announcement scope/i18n.
 */
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../..');
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(value, message) { assertions++; if (!value) throw new Error(message); }
function has(text, needle, message) { check(text.includes(needle), message || `missing: ${needle}`); }

const manifest = read('frontend/inner/manifest.txt').split(/\r?\n/).map(s => s.trim()).filter(Boolean);
check(manifest.length === 18, 'RC21 must keep exactly 18 ordered inner fragments');
check(manifest.includes('115-private-multiwindow.js'), 'multi-window fragment must be in the inner manifest');

const state = read('frontend/inner/00-bootstrap-state.js');
has(state, 'privateMultiWindowMinWidth: 900', 'multi-window width cutoff');
has(state, 'privateMultiWindowMinHeight: 480', 'multi-window height cutoff');
has(state, 'dmConversationWindows: new Map()', 'DM child-window registry');
has(state, 'groupConversationWindows: new Map()', 'group child-window registry');
has(state, 'frameMinimizedHeight: 48', 'minimized public chat keeps compact header height');
has(state, 'token: ""', 'persisted token is not trusted directly into the live auth token');
has(state, 'authPendingToken: localStorage.getItem("kwc.token") || ""', 'persisted token is staged for server verification');
has(state, 'authVerified: false', 'auth starts unverified');

const multi = read('frontend/inner/115-private-multiwindow.js');
has(multi, 'window.innerWidth >= Number(state.privateMultiWindowMinWidth || 900)', 'width gate uses viewport');
has(multi, 'window.innerHeight >= Number(state.privateMultiWindowMinHeight || 480)', 'height gate uses viewport');
has(multi, 'data-kwc-private-child-window', 'child windows are explicitly identified');
has(multi, 'kwc-private-child-close', 'child window uses the existing Close control');
has(multi, 'kwc-dm-modal kwc-dm-thread-mode kwc-private-child-modal', 'DM child window enters thread mode so conversation content is visible');
has(multi, 't("button.close", "Close")', 'child close button is localized existing close text');
has(multi, 'closeAllPrivateConversationWindows', 'parent close can close all child windows');
has(multi, 'collapsePrivateConversationWindowsToSinglePane("dm")', 'narrow viewport collapses DM children');
has(multi, 'collapsePrivateConversationWindowsToSinglePane("group")', 'narrow viewport collapses group children');
has(multi, 'const layout = privateListLayout(type);\n    if (layout) layout.appendChild(live);', 'live conversation DOM is preserved when snapshotting inactive child');
has(multi, 'mountWindowOwnedOverlay', 'owner-scoped overlay helper exists');
has(multi, 'mountPrivateWindowOwnedOverlay', 'private owner-scoped overlay helper exists');

const windowing = read('frontend/inner/120-dm-ui-typing.js');
for (const d of ['nw','n','ne','e','se','s','sw','w']) has(windowing, `"${d}"`, `transparent resize direction ${d}`);
has(windowing, 'kwc-window-resize-zone', 'transparent resize zones are created');
has(windowing, 'modal.addEventListener("dblclick"', 'DM/group delegated double-click maximize is installed');
has(windowing, 'target.closest(".kwc-dm-head, .kwc-dm-title")', 'outer and inner DM/group titles are maximize surfaces');
has(windowing, 'modal.style.setProperty("left", "0px", "important")', 'DM/group maximize reaches left viewport edge');
has(windowing, 'modal.style.setProperty("top", "0px", "important")', 'DM/group maximize reaches top viewport edge');
has(windowing, 'modal.style.setProperty("width", "100vw", "important")', 'DM/group maximize fills viewport width');
has(windowing, 'modal.style.setProperty("height", "100vh", "important")', 'DM/group maximize fills viewport height');
has(windowing, 'modal.dataset.kwcMaximized', 'DM/group maximize state is tracked');
has(windowing, 'wrap.__kwcWindowChromeCleanup', 'child window chrome has teardown hook');
has(windowing, 'overlayObserver.observe(modal, {childList:true})', 'resize zones react to owned popup lifetime');

const rootWindowing = read('frontend/inner/30-reply-identity-frame.js');
has(rootWindowing, 'header.addEventListener("dblclick"', 'public header double-click maximize is installed');
has(rootWindowing, 'toggleStandaloneRootMaximize(root)', 'standalone public maximize toggles locally');
has(rootWindowing, 'postFrame("maximizeToggle", {})', 'adapter public maximize is delegated to parent');
has(rootWindowing, 'installStandaloneRootResizeZones(root)', 'standalone public window uses transparent resize zones');
has(rootWindowing, 'if (root.__kwcStandaloneResizeUpdate) root.__kwcStandaloneResizeUpdate();', 'public resize zones follow window movement');
has(rootWindowing, 'root.style.setProperty("--kwc-standalone-left", "0px")', 'standalone maximize reaches left edge');
has(rootWindowing, 'root.style.setProperty("--kwc-standalone-top", "0px")', 'standalone maximize reaches top edge');
has(rootWindowing, 'root.style.setProperty("--kwc-standalone-width", "100vw")', 'standalone maximize fills viewport width');
has(rootWindowing, 'root.style.setProperty("--kwc-standalone-height", "100vh")', 'standalone maximize fills viewport height');
has(rootWindowing, 'state.config && state.config.uiResizable === true', 'standalone resize obeys the same uiResizable gate as adapters');
has(rootWindowing, 'width: state.minimized ? 48 : state.frameNormalWidth', 'minimized public frame requests only the restore-button width');
has(rootWindowing, 'handle.style.zIndex = String(rootZ)', 'public resize zones track the public root stacking level');


const rootAuth = read('frontend/inner/40-root-auth.js');
has(rootAuth, '${!state.isPip ? `<button class=\"kwc-button\" id=\"kwc-min\">_</button>` : ""}', 'standalone renders the same minimize control as adapter mode');
has(rootAuth, 'if (willMinimize && state.isStandalone && root && root.dataset.kwcMaximized === "1")', 'standalone minimize first restores a maximized window like adapter mode');
has(rootAuth, 'raiseIndependentChatWindow(root)', 'public chat participates in shared click-to-front z ordering');
check(!rootAuth.includes('updateResizeLockButton('), 'removed resize-lock callback must not remain and break updateLoginState');
has(rootAuth, 'function authenticatedSession()', 'verified-session helper exists');
has(rootAuth, 'state.token && state.authVerified === true', 'account UI requires a server-verified token');
has(rootAuth, '!authenticatedSession()', 'guest-hidden policy treats unverified persisted credentials as guest');
has(rootAuth, 'btn.textContent = headerAccountDisplayName(accountButtonName, 16)', 'authenticated account button replaces Login text and truncates only names over 16 code points');
has(rootAuth, 'root.dataset.kwcMinimizedSide', 'standalone minimize records left/right side from pre-minimize geometry');
has(rootAuth, 'rect.left + (rect.width / 2)', 'standalone minimize side is selected by window center');
check(!state.includes('resizeLocked'), 'resize-lock state is removed');
check(!rootAuth.includes('kwc-resize-lock'), 'public resize-lock button is removed');
check(!windowing.includes('resizeLocked'), 'private resize-lock behavior is removed');

const group = read('frontend/inner/140-group-management.js');
has(group, 'mountPrivateWindowOwnedOverlay("group", wrap)', 'group forms/settings are owner scoped');
has(group, 'setTimeout(() => nameInput.focus(), 0)', 'group room-create input receives focus');
has(group, 'closeAllPrivateConversationWindows("group"', 'closing group parent closes child rooms');
has(group, 'closeAllPrivateConversationWindows("dm"', 'closing DM parent closes child conversations');
has(group, 'installIndependentChatWindow(wrap);', 'DM/group parent list windows receive desktop drag/resize/maximize chrome');
check((group.match(/installIndependentChatWindow\(wrap\);/g) || []).length >= 2, 'both DM inbox and group list parent windows install independent chrome');
const dm = read('frontend/inner/110-auth-dm-core.js');
has(dm, 'mountPrivateWindowOwnedOverlay("dm", wrap)', 'DM settings are owner scoped');
check(!dm.includes('kwc-dm-title-back";') && !dm.includes('title.onclick = returnDirectMessageToList'), 'DM title click-to-close/back must not be installed');

const search = read('frontend/inner/100-preferences-search.js');
has(search, 'mountWindowOwnedOverlay(wrap, publicChatWindowOwner())', 'public search popup is scoped to public chat');
has(search, 'mountPrivateWindowOwnedOverlay(type, wrap)', 'private search popup is scoped to its conversation');
const pins = read('frontend/inner/80-pins-admin.js');
has(pins, 'modal.querySelector(".kwc-dm-head")', 'DM/group full header is the drag surface');
has(pins, 'mountWindowOwnedOverlay(wrap, publicChatWindowOwner())', 'public pin popup is scoped to public chat');
has(pins, 'mountPrivateWindowOwnedOverlay("group", wrap)', 'group pin popup is scoped to group chat');
const games = read('frontend/inner/85-chat-games.js');
has(games, 'mountWindowOwnedOverlay(wrap, publicChatWindowOwner())', 'event popup is scoped to public chat');
has(games, 'id="kwc-game-notification-scope"', 'event form exposes notification scope');
has(games, 'value="relay"', 'event scope defaults to Relay behavior');
has(games, 'value="local"', 'event scope offers current-server only');
has(games, 'notificationScope:content.querySelector', 'event creation sends notification scope');

const css = read('kwc-standalone-frontend/src/main/resources/standalone/chat.css');
has(css, '.kwc-chat-window-resize-handle { display: none !important; }', 'visible lower-right resize grip is hidden');
has(css, '.kwc-window-owned-overlay.kwc-modal-backdrop', 'owned overlay CSS exists');
has(css, 'pointer-events: auto !important;', 'owned popup accepts pointer input');
has(css, '.kwc-modal-backdrop.kwc-user-prefs-backdrop .kwc-admin-user-controls > .kwc-admin-item', 'signed-in restriction user row is flattened without removing the surrounding list');
has(css, '.kwc-modal-backdrop.kwc-user-prefs-backdrop .kwc-admin-moderator-permissions > .kwc-admin-item', 'moderator user row is flattened without removing the surrounding list');
check(!css.includes('\n.kwc-admin-user-controls,\n.kwc-admin-moderator-permissions {\n  padding: 0 !important;'), 'incorrect outer/list border removal is gone');
has(css, '#kwc-root {', 'public root CSS exists');
has(css, 'z-index: 1000;', 'public root no longer has a permanently dominant z-index');
check(!css.includes('z-index: 999999;'), 'public chat is not pinned above other chat windows');
has(css, '#kwc-root .kwc-msg.kwc-has-delete-action { position: relative; }', 'public delete X no longer reserves message width');
check(!css.includes('padding-right: 30px !important;'), 'public/private delete X does not reserve a permanent right gutter');
has(css, '5.3.0 RC21: restore the legacy compact minimized pill and guest-only chrome policy.', 'RC21 minimized title-plus-restore override exists');
has(css, '#kwc-root.kwc-minimized .kwc-header-identity,', 'minimized title/identity has an explicit RC21 rule');
has(css, 'display: flex !important;', 'minimized title/identity is restored');
has(css, '#kwc-root.kwc-minimized .kwc-actions-primary > :not(#kwc-min)', 'minimized primary actions hide everything except restore');
has(css, '5.3.0 RC33: final public minimized chrome contract', 'final minimized public-chat override exists');
has(css, 'width: 48px !important;', 'minimized public chat is a compact restore-only width');
has(css, '#kwc-root.kwc-guest-hidden:not(.kwc-minimized) #kwc-pip', 'guest-hidden mode explicitly hides PiP');
has(css, 'data-kwc-minimized-side="left"', 'standalone left-bottom minimized snap rule exists');
has(css, 'data-kwc-minimized-side="right"', 'standalone right-bottom minimized snap rule exists');
check(!css.includes('#kwc-root.kwc-minimized #kwc-search-open,\n\n#kwc-root.kwc-minimized .kwc-actions > #kwc-min'), 'minimized hide/show CSS rules are not accidentally merged');

const startup = read('frontend/inner/150-startup.js');
has(startup, 'const verified = await verifyStoredToken();', 'startup verifies persisted auth before account state is loaded');
check(startup.indexOf('const verified = await verifyStoredToken();') < startup.indexOf('await refreshCaptcha();'), 'CAPTCHA/guest UI is decided after auth verification');
has(startup, 'if (verified) {\n      await loadDirectMessageThreads(true);', 'private DM/group loads are gated by verified auth');
has(startup, 'if (!guestChatHidden()) connectStream();', 'guest-hidden startup does not reopen public stream');
const authGroup = read('frontend/inner/140-group-management.js');
has(authGroup, 'const candidate = String(state.token || state.authPendingToken || "").trim();', 'stored token candidate is verified explicitly');
has(authGroup, 'Authorization: "Bearer " + candidate', 'auth/me verification uses pending bearer token without unlocking UI');
has(authGroup, 'state.authVerified = true;', 'successful login/verification marks session verified');
const notifications = read('frontend/inner/90-notifications.js');
has(notifications, 'authenticatedSession() && state.userUuid', 'notification inbox ownership requires verified session');
has(notifications, 'clearAccountNotificationUiState', 'auth loss can clear account notification UI');
const typing = read('frontend/inner/120-dm-ui-typing.js');
has(typing, 'captchaBlocksTyping', 'guest CAPTCHA suppresses public typing indicator');
has(windowing, 'wrap.__kwcStandaloneResizeUpdate();', 'raising public root refreshes its resize hit-test layer');

const emoji = read('frontend/inner/20-emoji-reactions.js');
has(emoji, 't("game.type." + rawType', 'rendered event notification localizes internal type code');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(server, 'body.getOrDefault("notificationScope", "relay")', 'server defaults event scope to relay');
has(server, 'publishChatGameEvent("Game", text, "game.chat.created", JsonUtil.obj(vars), relayAnnouncements)', 'created announcement honors relay scope');
has(server, 'publishChatGameEvent("Game", text, "game.chat.results", JsonUtil.obj(vars), relayAnnouncements)', 'result announcement honors relay scope');
has(server, 'if (relayAnnouncement) publishServerRelay(msg);', 'relay publication is gated');
has(server, 'publishSystemMessage("event"', 'chat events use a relay source distinct from ordinary system notices');
has(server, 'host.language().text("game.type." + type', 'local server announcement uses localized event type');
const manager = read('kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java');
has(manager, 'boolean relayAnnouncements = true;', 'event state defaults relay announcements on');
has(manager, 'out.put("relayAnnouncements", game.relayAnnouncements)', 'event snapshot exposes relay scope');
has(manager, 'p.setProperty("relayAnnouncements"', 'event scope persists');
has(manager, 'p.getProperty("relayAnnouncements", "true")', 'legacy persisted events default to relay');
const push = read('kwc-core/src/main/java/dev/kokoto/webchat/WebPushManager.java');
has(push, 'subscriptionText(sub, "game.type." + rawType', 'push notification localizes event type per subscriber');

const languageExpectations = {
  'ko-KR.yml':['type.firstcome: 선착순','type.lottery: 추첨','notificationScope: 알림 범위','scopeLocal: 현재 서버만','scopeRelay: Relay 서버에도 전달'],
  'en-US.yml':['type.firstcome: First come','type.lottery: Lottery','notificationScope: Notification scope','scopeLocal: Current server only','scopeRelay: Current server + Relay servers'],
  'ja-JP.yml':['type.firstcome: 先着順','type.lottery: 抽選','notificationScope: 通知範囲','scopeLocal: 現在のサーバーのみ','scopeRelay: Relay サーバーにも配信'],
  'zh-CN.yml':['type.firstcome: 先到先得','type.lottery: 抽奖','notificationScope: 通知范围','scopeLocal: 仅当前服务器','scopeRelay: 同时发送到 Relay 服务器']
};
for (const [file, needles] of Object.entries(languageExpectations)) {
  const text = read(`kwc-platform-bukkit/src/main/resources/lang/${file}`);
  for (const needle of needles) has(text, needle, `${file}: ${needle}`);
}

const adapters = [
  'kwc-adapter-bluemap/src/main/resources/web/chat.js',
  'kwc-adapter-squaremap/src/main/resources/squaremap/chat.js',
  'kwc-adapter-dynmap/src/main/resources/dynmap/chat.js',
  'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.js',
  'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.js',
  'kwc-adapter-unmined/src/main/resources/unmined/chat.js',
  'kwc-adapter-overviewer/src/main/resources/overviewer/chat.js'
];
for (const rel of adapters) {
  const text = read(rel);
  has(text, 'frameMaximized', `${rel}: parent maximize state`);
  has(text, 'data.type === "maximizeToggle"', `${rel}: maximizeToggle bridge`);
  has(text, 'if (frameMaximized) return;', `${rel}: drag/resize blocked while maximized`);
  has(text, 'chatFrame.style.left = "0px";', `${rel}: maximized frame reaches left edge`);
  has(text, 'chatFrame.style.top = "0px";', `${rel}: maximized frame reaches top edge`);
  has(text, 'const width = Math.max(240, window.innerWidth);', `${rel}: maximized frame fills viewport width without 8px pad`);
  has(text, 'if (frameMaximized && frameMinimized) toggleParentFrameMaximize(true);', `${rel}: minimize restores maximize state before collapsing`);
  has(text, 'frameMinimizeRestoreRect', `${rel}: pre-minimize normal geometry is retained`);
  has(text, 'function applyMinimizedFrameSnap()', `${rel}: minimized edge snap helper exists`);
  has(text, 'frameMinimizedSide === "left"', `${rel}: minimized side supports left edge`);
  has(text, 'Number(vp.height || window.innerHeight) - height', `${rel}: minimized window snaps to viewport bottom`);
  has(text, 'frameMinimized ? 48', `${rel}: minimized iframe width keeps only the restore control`);
  has(text, 'if (!chatFrame || standaloneMode || frameMinimized) return;', `${rel}: minimized corner is never saved as normal position`);
  has(text, 'if (frameMinimized) applyMinimizedFrameSnap();', `${rel}: viewport/setSize updates preserve minimized corner snap`);
}

console.log(`WINDOW_UI_EVENTS_HARNESS_PASS assertions=${assertions}`);
