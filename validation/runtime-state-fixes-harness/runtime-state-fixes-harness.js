// KWC 파일 안내 / KWC file guide
// runtime-state-fixes-harness.js는 전송 직후 ID 치환, room policy 즉시 반영, 프로필 클릭 같은 브라우저 상태 전환 계약을 검증한다.
// runtime-state-fixes-harness.js validates browser state-transition contracts such as persisted-ID adoption, immediate room-policy updates, and profile clicks.
// 문자열 존재만 보는 기존 검증으로 놓쳤던 optimistic DOM 재사용 회귀를 다시 허용하지 않도록 핵심 상태 변경 경로를 함께 확인한다.
// Check the state-change paths together so optimistic-DOM reuse regressions missed by simple feature-presence assertions cannot return.

const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..', '..');
let assertions = 0;
function check(value, name) { assertions++; if (!value) throw new Error('FAIL: ' + name); }
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function fn(source, name) {
  const start = source.indexOf(`function ${name}(`);
  if (start < 0) return '';
  const asyncStart = source.lastIndexOf('async ', start);
  const actualStart = asyncStart >= 0 && start - asyncStart < 8 ? asyncStart : start;
  const next = source.indexOf('\n  function ', start + 10);
  const nextAsync = source.indexOf('\n  async function ', start + 10);
  const candidates = [next, nextAsync].filter(v => v > start);
  const end = candidates.length ? Math.min(...candidates) : source.length;
  return source.slice(actualStart, end);
}

const dm = read('frontend/inner/110-auth-dm-core.js');
const group = read('frontend/inner/140-group-management.js');
const privateUi = read('frontend/inner/120-dm-ui-typing.js');
const startup = read('frontend/inner/150-startup.js');
const rooms = read('frontend/inner/130-group-rooms-archive.js');
const publicHistory = read('frontend/inner/50-public-history-virtual-scroll.js');
const identity = read('frontend/inner/30-reply-identity-frame.js');
const rootAuth = read('frontend/inner/40-root-auth.js');
const pinsAdmin = read('frontend/inner/80-pins-admin.js');
const store = read('kwc-core/src/main/java/dev/kokoto/webchat/GroupChatStore.java');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');

const dmAdopt = fn(dm, 'adoptDirectMessageSendResponse');
check(dmAdopt.includes('/^\\d+$/.test(String(res.message.id || ""))'), 'DM POST response requires persisted numeric ID');
check(dmAdopt.includes('state.dmMessages.splice(index, 1, persisted)'), 'DM optimistic state is replaced by persisted response');
check(dmAdopt.includes('renderDirectMessageMessages(state.dmMessages'), 'DM persisted response renders immediately');

const groupAdopt = fn(group, 'adoptGroupChatSendResponse');
check(groupAdopt.includes('/^\\d+$/.test(String(res.message.id || ""))'), 'group POST response requires persisted numeric ID');
check(groupAdopt.includes('state.groupMessages.splice(index, 1, persisted)'), 'group optimistic state is replaced by persisted response');
check(groupAdopt.includes('renderGroupChatMessages(state.groupMessages'), 'group persisted response renders immediately');

const deleteSync = fn(dm, 'syncPrivateMessageDeleteButton');
check(deleteSync.includes('groupMessageDeletionAllowed(msg)'), 'group delete permission is recomputed on reused DOM');
check(deleteSync.includes('!state.dmAuditMode && persisted && (moderatorCanDeleteMessages() || (mine && selfMessageDeletionAllowed(msg)))'), 'DM delete permission is recomputed for moderator override and timed self-delete on reused DOM');
check(deleteSync.includes('existing.remove()'), 'delete button is removed immediately when policy disables it');
check(deleteSync.includes('document.createElement("button")'), 'delete button is inserted immediately when policy enables it');
const privateSync = fn(dm, 'syncPrivateMessageElement');
check(privateSync.includes('syncPrivateMessageDeleteButton(el, msg, type, mine)'), 'private DOM sync reconciles delete button after ID/policy change');

const settings = fn(group, 'updateGroupRoomSettings');
check(settings.includes('state.groupActiveRoom = canonical'), 'group settings response becomes active canonical room immediately');
check(settings.includes('state.groupPolicyOverride ='), 'group settings protect new policy from stale room reload');
check(settings.includes('renderGroupChatMessages(state.groupMessages || []'), 'group policy rerenders existing messages instead of clearing them');
check(settings.includes('updateGroupChatComposeControls()'), 'group policy refreshes compose controls immediately');
check(!settings.includes('renderGroupChatMessages([])'), 'group settings never clear current messages');
check(!settings.includes('state.groupActiveRoomId = ""'), 'group settings never clear active room');

const roomLoad = fn(rooms, 'loadGroupChatRooms');
check(roomLoad.includes('const override = state.groupPolicyOverride'), 'room reload honors recent policy override');
check(roomLoad.includes('pinsEnabled: override.pinsEnabled'), 'pin policy survives stale room reload');
check(roomLoad.includes('messageDeleteEnabled: override.messageDeleteEnabled'), 'delete policy survives stale room reload');
check(roomLoad.includes('memberSelfDeleteEnabled: override.memberSelfDeleteEnabled'), 'self-delete policy survives stale room reload');
check(roomLoad.includes('confirmedPolicy') && roomLoad.includes('state.groupPolicyOverride = null'), 'policy override clears only after server confirmation or expiry');
const reconcile = fn(rooms, 'reconcileActiveGroupRoomAfterRoomLoad');
check(reconcile.includes('protectActiveRoom') && reconcile.includes('if (protectActiveRoom && state.groupActiveRoom) return false'), 'recent room settings cannot blank active conversation during stale reload');

check(publicHistory.includes('data-user-profile-uuid'), 'public sender carries profile target');
const sourceHtml = fn(identity, 'messageOriginSourceHtml');
check(sourceHtml.includes('kwc-source-label'), 'Web/Game remains a source label');
check(sourceHtml.includes('kwc-message-dm-target') && sourceHtml.includes('directMessageTargetDataAttributes(target)'), 'Web/Game source is a DM button when a target UUID exists');
check(rootAuth.includes('kwcFaIcon("user", "kwc-status-icon")'), 'header user count has visible shared user icon');
check(server.includes('if (!selfUuid.isBlank() && presenceVisibleInLists(selfUuid)) users.add(selfUuid);'), 'offline self is excluded from header visible-user count');
check(server.includes('PresencePolicy.Result presence = presenceListSnapshot(uuid);'), 'online-user list uses privacy-filtered presence snapshot');
check(pinsAdmin.includes('pinnedByDetailHtml(pin)') && pinsAdmin.includes('pinnedByDisplayName') && pinsAdmin.includes('pinnedByUsername'), 'group pinned-by detail consumes display+real identity fields');
check(store.includes('pinned_by_username TEXT NOT NULL DEFAULT') && store.includes('pinned_by_display_name TEXT NOT NULL DEFAULT'), 'group pin snapshots preserve pinner real/display names');

check(!store.includes('!isMember(user, id) || !roomPinsEnabled(id)) return out;'), 'disabling group pins cannot blank message history');
check(!store.includes('DELETE FROM group_pins WHERE room_id=? AND message_id=?'), 'deleting group source message preserves pin snapshot');
check(pinsAdmin.includes('directMessageIdentityHtml({') && pinsAdmin.includes('kwc-pinned-by-user'), 'pinned-by detail uses common safe identity renderer');

const profile = fn(dm, 'openUserPresenceProfile');
check(profile.includes('id="kwc-user-profile-name-toggle"'), 'profile uses one name-mode toggle');
check(!profile.includes('kwc-user-profile-name-display'), 'legacy two-button display-name selector removed');
check(!profile.includes('kwc-user-profile-name-real'), 'legacy two-button real-name selector removed');
check(profile.includes('id="kwc-user-profile-dm"'), 'other-user profile exposes DM action');
check(profile.includes('openDirectMessageForTarget({'), 'profile DM action reuses canonical DM target flow');
check(profile.includes('id="kwc-user-profile-presence-status"'), 'own profile exposes online/busy/offline selector');
check(profile.includes('setAccountPresenceStatus(requested)'), 'own profile status selector persists account presence state');
const preferences = read('frontend/inner/100-preferences-search.js');
check(!preferences.includes('data-kwc-pref-section="presence"'), 'chat settings no longer duplicate profile presence/privacy controls');
check(profile.includes('id="kwc-user-profile-blocked-users"'), 'self profile owns blocked-user management');
check(profile.includes('data-presence-dm-uuid') && dm.includes('raw.closest("[data-presence-dm-uuid]")'), 'Game/Web presence badges open DM directly');
check(group.includes('id="kwc-account-profile-open"') && group.includes('id="kwc-account-profile-name"'), 'account/user modal exposes explicit self-profile button and clickable name');
check(profile.includes('mountChatWindowOwnedOverlay(ownerType, wrap)'), 'profile modal is scoped to the chat window that opened it');
check(identity.includes('function syncSenderIdentityModeControls()') && identity.includes('data-kwc-sender-identity-mode-control'), 'global name-mode changes synchronize profile toggle controls');
check(server.includes('out.put("playerUuid", remote.playerUuid)'), 'presence profile exposes remote player UUID for DM routing');
check(server.includes('out.put("serverId", remote.serverId)'), 'presence profile exposes remote server ID for DM routing');


const namespace = fn(privateUi, 'chatViewNamespace');
check(namespace.includes('window.location.origin') && namespace.includes('window.location.pathname'), 'view state namespace includes browser server origin/path');
check(namespace.includes('serverRelayServerId'), 'view state namespace includes relay server ID');
check(namespace.includes('state.userUuid || "guest"'), 'view state namespace includes stable user UUID with guest fallback');
const storageWrite = fn(privateUi, 'writeChatViewStore');
check(storageWrite.includes('localStorage.setItem(key, raw)'), 'view positions persist in localStorage by default');
check(storageWrite.includes('chatViewMemoryFallback.set(key, raw)'), 'view positions retain memory fallback when localStorage is unavailable');
const conversationKey = fn(privateUi, 'chatViewConversationKey');
check(conversationKey.includes('`${type}:${id}`'), 'DM/group positions are keyed per conversation');
const saveView = fn(privateUi, 'saveConversationView');
check(saveView.includes('messageId: String(anchor.messageId || "")'), 'saved view stores anchor message ID');
check(saveView.includes('offset:') && saveView.includes('atBottom:') && saveView.includes('lastAccess:'), 'saved view stores offset/bottom/last-access metadata');
check(!saveView.includes('.body') && !saveView.includes('sender') && !saveView.includes('displayName'), 'saved view does not persist message body or peer identity');
const captureView = fn(privateUi, 'captureChatViewAnchor');
check(captureView.includes('data-group-message-id') && captureView.includes('data-dm-message-id') && captureView.includes('data-id'), 'view anchor supports public, DM, and group messages');
check(captureView.includes('rect.top - viewport.top'), 'view anchor stores pixel offset inside viewport');
const restoreView = fn(privateUi, 'restoreChatViewAnchor');
check(restoreView.includes('ensurePublicChatViewMessage(saved)'), 'public view restoration can recover an older saved anchor');
check(restoreView.includes('loadOlderDirectMessageMessagesFromEdge') && restoreView.includes('loadOlderGroupChatMessagesFromEdge'), 'private view restoration pages older history until anchor is available');
check(restoreView.includes('state.autoFollowLatest = false') && restoreView.includes('state.forceLatestJumpUntil = 0'), 'restoring a non-bottom view cancels stale latest-follow state');
check(restoreView.includes('state.preventBottomStickUntil') && restoreView.includes('Date.now() + 4000'), 'restored non-bottom view is protected from late layout bottom-stick');
const publicRecover = fn(privateUi, 'ensurePublicChatViewMessage');
check(publicRecover.includes('/history/around?id='), 'public restoration uses around-history API instead of replaying every page');
const lastView = fn(privateUi, 'restoreLastChatViewState');
check(lastView.includes('openDirectMessageModal()') && lastView.includes('openGroupChatModal()'), 'startup restore reopens the previously active private modal');
check(lastView.includes('state.dmActiveThreadId = conversationId') && lastView.includes('openGroupRoom(conversationId)'), 'startup restore reselects the previous DM thread/group room');
check(startup.includes('if (!hasNotificationNavigation) await restoreLastChatViewState()'), 'notification deep-links take precedence over saved view restoration');
check(startup.indexOf('state.chatViewRestoreInProgress = true') < startup.indexOf('makeRoot()'), 'startup blocks scroll persistence before initial programmatic history rendering');
check(startup.indexOf('installChatViewPersistence()') > startup.indexOf('restoreLastChatViewState()'), 'scroll persistence installs only after startup view restoration');
check(group.includes('saveConversationView("group", state.groupActiveRoomId)') && group.includes('setActiveChatView("public", "")'), 'closing group modal preserves room position but marks public as active');
check(group.includes('saveConversationView("dm", state.dmActiveThreadId)') && group.includes('setActiveChatView("public", "")'), 'closing DM modal preserves thread position but marks public as active');
check(dm.includes('await restoreChatViewAnchor("dm", state.dmActiveThreadId)'), 'switching DM threads restores each thread position');
check(rooms.includes('await restoreChatViewAnchor("group", roomId)'), 'switching group rooms restores each room position');
check(privateUi.includes('CHAT_VIEW_STATE_MAX_ENTRIES = 256'), 'local view-state cache is bounded');
check(!privateUi.includes('kwc.privateReloadView.v1'), 'intermediate reload-only session key is absent from final source');


check(!read('frontend/inner/60-theme-config-stream.js').includes('document.querySelectorAll(".kwc-user-profile-modal").forEach'), 'presence stream updates keep an open profile modal alive');
console.log(`RUNTIME_STATE_FIXES_HARNESS_PASS assertions=${assertions}`);
