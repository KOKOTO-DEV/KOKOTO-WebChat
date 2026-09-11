// KWC 파일 안내 / KWC file guide
// presence-ui-harness.js는 KWC 개발/배포 과정에서 사용하는 JavaScript 보조 코드다.
// presence-ui-harness.js is JavaScript support code used by the KWC development or packaging workflow.
// 배포 runtime 코드와 생성 코드를 구분하고, generated 산출물을 수동 편집하지 않도록 source-of-truth 경로를 유지한다.
// Keep runtime source separate from generated artifacts and preserve the source-of-truth path instead of manually editing generated output.

const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..', '..');
let assertions = 0;
function check(v, name) { assertions++; if (!v) throw new Error('FAIL: ' + name); }
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
const state = read('frontend/inner/00-bootstrap-state.js');
const prefs = read('frontend/inner/100-preferences-search.js');
const notifications = read('frontend/inner/90-notifications.js');
const dm = read('frontend/inner/110-auth-dm-core.js');
const identity = read('frontend/inner/30-reply-identity-frame.js');
const publicHistory = read('frontend/inner/50-public-history-virtual-scroll.js');
const group = read('frontend/inner/140-group-management.js');
const stream = read('frontend/inner/60-theme-config-stream.js');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const policy = read('kwc-core/src/main/java/dev/kokoto/webchat/PresencePolicy.java');
const store = read('kwc-core/src/main/java/dev/kokoto/webchat/UserPreferenceStore.java');
const css = read('kwc-standalone-frontend/src/main/resources/standalone/chat.css');

check(state.includes('presenceStatus: "online"'), 'manual presence state exists');
check(!prefs.includes('data-kwc-pref-section="presence"'), 'chat settings no longer duplicate profile presence/privacy section');
check(!prefs.includes('kwc-prefs-presence-status') && !prefs.includes('kwc-prefs-blocked-users'), 'chat settings no longer own presence or blocked-user controls');
check(dm.includes('id="kwc-user-profile-presence-status"'), 'self profile owns presence status selector');
check(dm.includes('value="online"') && dm.includes('value="busy"') && dm.includes('value="offline"'), 'profile presence selector exposes online busy offline');
check(notifications.includes('function setAccountPresenceStatus(status)'), 'presence status save function exists');
check(notifications.includes('body: JSON.stringify({status})'), 'presence status POST uses status field');
check(store.includes('"presence.status"') && store.includes('normalizePresenceStatus'), 'manual presence status persists server-side');
check(!store.includes('presence.invisible'), 'intermediate invisible preference key is absent from final 5.3.0');
check(dm.includes('function presenceCompactHtml'), 'compact presence renderer exists');
check(dm.includes('function openUserPresenceProfile'), 'profile presence popup exists');
check(dm.includes('presence.game') && dm.includes('presence.web'), 'profile shows separate game/web');
check(dm.includes('kwc-user-profile-name-toggle') && !dm.includes('kwc-user-profile-name-display') && !dm.includes('kwc-user-profile-name-real'), 'profile owns one display/real name toggle');
check(dm.includes('id="kwc-user-profile-dm"') && dm.includes('openDirectMessageForTarget({'), 'other-user profile exposes canonical DM action');
check(dm.includes('data-presence-dm-uuid') && dm.includes('const dmTarget = raw && raw.closest ? raw.closest("[data-presence-dm-uuid]")'), 'Game/Web presence badges route directly to DM');
check(dm.includes('profileModal.closest(".kwc-modal-backdrop")') && dm.includes('backdrop.remove()'), 'profile Game/Web DM action closes profile before opening DM');
check(dm.includes('id="kwc-user-profile-blocked-users"'), 'self profile owns blocked-user management');
check(dm.includes('id="kwc-user-profile-presence-status"') && dm.includes('setAccountPresenceStatus(requested)'), 'own profile can change online busy offline state');
check(dm.includes('presenceStatusLabel("busy")'), 'busy presence rendering exists');
check(group.includes('presenceCompactHtml(m, m.uuid || "")'), 'group member compact presence');
check(stream.includes('addEventListener("presence-update"'), 'SSE presence update listener');
check(server.includes('presenceWebOnline') && server.includes('sseHub.snapshot()'), 'web presence derives from live SSE');
check(server.includes('"/presence/summary"') && server.includes('loggedInCount'), 'authenticated Web login-count summary API exists');
check(server.includes('presenceVisibleInLists(selfUuid)') && server.includes('presenceListSnapshot(uuid)'), 'offline visibility is excluded from self count and online lists');
check(server.includes('PresencePolicy.resolve(presenceGameOnline(target), presenceWebOnline(target), presenceStatus(target), false)'), 'online list privacy never uses self-view exception');
check(dm.includes('function refreshLoggedInCount()'), 'frontend refreshes active authenticated Web user count');
check(server.includes('PresencePolicy.resolve(game, web, manualStatus, selfView)'), 'server uses viewer-aware manual status policy');
check(policy.includes('boolean privacyMasked = "offline".equals(manual)'), 'offline status is privacy mode');
check(policy.includes('connected && "busy".equals(manual) ? "busy"'), 'busy status remains visible while connected');
check(policy.includes('if (privacyMasked && !selfView)'), 'offline masks underlying presence for other viewers');
check(server.includes('String visibleUuid = privacyMasked && !selfView ? "" : target;'), 'offline SSE updates do not expose target UUID');
check(dm.includes('renderAdminSummary(adminSummary)'), 'generic presence refresh updates admin online surface');
check(dm.includes('loadGroupChatRooms(true).then(() => {') && dm.includes('renderGroupChatHeader();'), 'presence refresh rerenders active group header online count in real time');
check(group.includes('id="kwc-account-profile-open"') && group.includes('openUserPresenceProfile(uuid)'), 'account/user modal has direct self-profile entry');
check(dm.includes('mountChatWindowOwnedOverlay(ownerType, wrap)'), 'profile popup is scoped to the chat window that opened it');
check(identity.includes('syncSenderIdentityModeControls()'), 'display/real-name mode updates all profile toggle controls');
check(css.includes('.kwc-presence-compact') && css.includes('.kwc-user-profile-presence'), 'presence CSS exists');
check(css.includes('.kwc-presence-busy') && css.includes('.kwc-user-profile-name-mode'), 'busy/profile-name CSS exists');
check(css.includes('var(--kwc-link-color') && css.includes('var(--kwc-border-color'), 'presence CSS follows theme variables');
check(publicHistory.includes('data-user-profile-uuid'), 'public message sender can open user profile');
check(identity.includes('function messageOriginSourceHtml(msg)') && identity.includes('<span class="kwc-source-label">'), 'Web/Game source keeps its source label');
const sourceFn = identity.slice(identity.indexOf('function messageOriginSourceHtml(msg)'), identity.indexOf('function messageOriginSourceHtml(msg)') + 600);
check(sourceFn.includes('kwc-message-dm-target') && sourceFn.includes('directMessageTargetDataAttributes(target)'), 'Web/Game source opens its direct-message target');
for (const lang of ['en-US','ko-KR','ja-JP','zh-CN']) {
  const y = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  check(y.includes('presenceStatus:') && y.includes('profileButton:') && y.includes('busy:') && y.includes('nameDisplay:') && y.includes('\n  presence:\n'), lang + ' presence/profile translations');
}
check(identity.includes('if (target.dataset.userProfileUuid) return;'), 'profile-target identities bypass legacy direct display/real-name toggle');


check(!read('frontend/inner/60-theme-config-stream.js').includes('document.querySelectorAll(".kwc-user-profile-modal").forEach'), 'presence stream updates keep an open profile modal alive');
console.log(`PRESENCE_UI_HARNESS_PASS assertions=${assertions}`);
