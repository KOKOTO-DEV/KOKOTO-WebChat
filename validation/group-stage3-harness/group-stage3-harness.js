// KWC 파일 안내 / KWC file guide
// group-stage3-harness.js는 KWC 개발/배포 과정에서 사용하는 JavaScript 보조 코드다.
// group-stage3-harness.js is JavaScript support code used by the KWC development or packaging workflow.
// 배포 runtime 코드와 생성 코드를 구분하고, generated 산출물을 수동 편집하지 않도록 source-of-truth 경로를 유지한다.
// Keep runtime source separate from generated artifacts and preserve the source-of-truth path instead of manually editing generated output.

const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..', '..');
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const store = read('kwc-core/src/main/java/dev/kokoto/webchat/GroupChatStore.java');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const dmStore = read('kwc-core/src/main/java/dev/kokoto/webchat/DirectMessageStore.java');
const game = read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
const bukkit = read('kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/KwcCommand.java');
const inner = read('inner.js');
const cfg = read('kwc-platform-bukkit/src/main/resources/config.yml');
let checks = 0;
function yes(cond, msg) { if (!cond) throw new Error('FAIL: ' + msg); checks++; }
function has(text, needle, msg=needle) { yes(text.includes(needle), msg); }
function no(text, needle, msg=needle) { yes(!text.includes(needle), msg); }

// Group room-local role contract.
has(store, 'return "owner".equals(role) || "admin".equals(role);', 'group management is room-local owner/admin');
has(store, 'if (!"owner".equals(roleOf(owner, id)))', 'only room owner may change room roles');
has(store, '"admin".equals(role) || "member".equals(role)', 'role changes are admin/member only');
has(store, 'UPDATE group_members SET role=? WHERE room_id=? AND user_uuid=?', 'roles are stored per room membership');
has(server, '"/group/set-role"', 'group role API exists');
has(inner, 'data-group-role="admin"', 'owner UI can promote room admin');
has(inner, 'data-group-role="member"', 'owner UI can demote room admin');
has(inner, 'group.confirmMakeAdmin', 'promotion confirmation is localized');
has(inner, 'group.confirmRemoveAdmin', 'demotion confirmation is localized');

// Room-local pinned messages.
has(store, 'CREATE TABLE IF NOT EXISTS group_pins', 'group pin table exists');
has(store, 'sender_display_name TEXT NOT NULL DEFAULT', 'group pins snapshot sender display name');
has(store, 'body TEXT NOT NULL DEFAULT', 'group pins snapshot body');
has(store, 'event_type TEXT NOT NULL DEFAULT', 'group pins snapshot event type');
has(store, 'pinned_by_username TEXT NOT NULL DEFAULT', 'group pins snapshot pinner real name');
has(store, 'pinned_by_display_name TEXT NOT NULL DEFAULT', 'group pins snapshot pinner display name');
has(store, 'cleanIdentitySnapshot(managerDisplayName)', 'group pin creation snapshots pinner display identity');
has(store, 'public synchronized List<GroupPinnedMessage> listPins', 'member pin list API exists');
has(store, 'if (connection == null || !isMember(user, id)) return out;', 'only room members can list retained pin snapshots');
no(store, 'if (connection == null || !isMember(user, id) || !roomPinsEnabled(id)) return out;', 'disabling pin UI does not discard or hide retained pin snapshots in storage');
has(store, 'public synchronized GroupPinnedMessage pinMessage', 'pin creation exists');
has(store, '!canManage(manager, id)', 'pin writes require room manager');
has(store, 'SELECT COUNT(*) FROM group_pins WHERE room_id=?', 'room pin limit enforced');
has(store, 'public synchronized boolean unpinMessage', 'unpin exists');
has(store, 'public synchronized boolean movePin', 'pin reorder exists');
has(store, 'ORDER BY sort_order ASC,pinned_at ASC,pin_id ASC', 'pin display order is stable');
has(server, '"/group/pins"', 'group pins GET route exists');
has(server, '"/group/pin-message"', 'group pin route exists');
has(server, '"/group/unpin-message"', 'group unpin route exists');
has(server, '"/group/move-pin"', 'group pin reorder route exists');
has(server, 'roomForMember(ctx.account.uuid, roomId)', 'pin listing validates room membership');
has(server, '"owner".equals(room.role) || "admin".equals(room.role)', 'pin management uses room role');
has(server, 'config.pinnedMaxPins', 'group pins reuse public pin limit');
has(inner, 'id="kwc-group-pinned-bar"', 'group modal has pinned bar');
has(inner, 'id="kwc-group-pinned-open"', 'group pinned bar opens panel');
has(inner, 'id="kwc-group-pinned-list"', 'group pinned list exists');
has(inner, 'data-group-pin-move=', 'group pin reorder controls exist');
has(inner, 'data-group-unpin=', 'group unpin control exists');
has(inner, 'state.groupPinsCanPin && groupCanManage()', 'ordinary members do not see pin management controls');
has(inner, 'await loadGroupPins(roomId)', 'opening a room loads its pins');
has(store, 'pins_enabled INTEGER NOT NULL DEFAULT 1', 'room pin policy defaults enabled for existing behavior');
has(store, 'message_delete_enabled INTEGER NOT NULL DEFAULT 1', 'room delete policy defaults enabled for existing behavior');
has(store, 'member_self_delete_enabled INTEGER NOT NULL DEFAULT 1', 'member self-delete policy defaults enabled for existing behavior');
has(store, 'roomPinsEnabled(id)', 'pin writes enforce room policy in storage');
no(store, '!isMember(user, id) || !roomPinsEnabled(id)) return out;', 'pin policy never disables group message history retrieval');
has(store, 'roomBooleanSetting(roomId, "message_delete_enabled", false)', 'message-delete policy fails closed on storage read failure');
has(server, 'room.pinsEnabled', 'group pin API exposes/enforces room-local pin policy');
has(inner, 'pinsEnabled', 'group settings UI carries room pin policy');
has(inner, 'messageDeleteEnabled', 'group settings UI carries room delete policy');
has(inner, 'memberSelfDeleteEnabled', 'group settings UI carries member own-delete policy');

// Explicit room-wide group deletion, no per-user message hide write path.
has(store, 'public synchronized DeleteResult deleteMessage', 'group message delete exists');
has(store, 'boolean ownNormalMessage = requester.equals(senderUuid) && eventType.isBlank();', 'member can delete own normal message');
has(store, 'if (!manager && (!ownNormalMessage || !roomMemberSelfDeleteEnabled(roomId) || !selfDeleteEnabled))', 'non-manager cannot delete another member message and member self-delete obeys room and global policy');
has(store, 'self_delete_window_expired', 'ordinary member deletion enforces the shared deletion window');
has(server, 'selfMessageDeleteWindowMinutes', 'group delete endpoint passes the shared deletion window to storage');
has(inner, 'selfMessageDeletionAllowed(msg)', 'group delete controls hide after the shared deletion window');
has(store, 'UPDATE group_messages SET hidden=1 WHERE id=? AND hidden=0', 'explicit delete tombstones the room message');
no(store, 'DELETE FROM group_pins WHERE room_id=? AND message_id=?', 'explicit message delete preserves matching pin snapshot');
has(store, 'out.pinRemoved = false;', 'message delete reports that preserved pin snapshot was not removed');
has(store, "UPDATE group_messages SET reply_to_id=0,reply_to_sender='',reply_to_preview=''", 'explicit delete scrubs reply snapshot');
no(store, 'INSERT INTO group_message_state', 'no new per-user group message hide state is written');
has(server, '"/group/delete-message"', 'group delete route exists');
no(server, '"/group/hide-message"', 'group message-hide route removed');
has(inner, 'data-group-delete-message=', 'group delete control exists');
has(inner, 'state.groupMessages = (state.groupMessages || []).filter', 'group delete removes the message from the active view immediately');
has(inner, 'state.dmMessages = (state.dmMessages || []).filter', 'DM delete removes the message from the active view immediately');
no(inner, 'data-group-hide-message=', 'group message-hide control removed');

// DM hide is gone end-to-end; only sender delete remains.
no(dmStore, 'boolean hideMessage(', 'DM hide store write API removed');
no(server, '"/dm/hide-message"', 'DM hide HTTP route removed');
has(server, '"/dm/delete-message"', 'DM delete HTTP route exists');
has(server, 'not_message_owner', 'DM server rejects non-owner delete');
no(game, 'action.equals("hide")', 'core game command has no dm hide alias');
has(game, 'action.equals("delete")', 'core game command exposes dm delete');
no(bukkit, '"hide".equalsIgnoreCase(args[1])', 'Bukkit command has no dm hide alias');
has(bukkit, '"delete".equalsIgnoreCase(args[1])', 'Bukkit command exposes dm delete');
no(inner, '/dm/hide-message', 'frontend has no DM hide endpoint');
has(inner, '/dm/delete-message', 'frontend uses DM delete endpoint');
no(inner, 'data-dm-hide-message', 'frontend has no DM hide button');
has(inner, 'data-dm-delete-message', 'frontend has an authorized DM delete button');
has(inner, 'moderatorCanDeleteMessages() || (mine && selfMessageDeletionAllowed(msg))', 'DM delete button supports moderator override and timed owner deletion');
has(inner, 'selfMessageDeleteWindowMinutes', 'DM delete controls enforce the shared deletion window');
no(inner, 'kwc-dm-message-hide', 'legacy DM hide CSS class removed from frontend');

// Current config schema uses delete confirmations; old names exist only in migration code/baselines.
has(cfg, 'confirm-delete: true', 'current config uses delete confirmation');
no(cfg, 'confirm-hide:', 'current config has no message hide confirmation');

for (const lang of ['en-US','ko-KR','ja-JP','zh-CN']) {
  const y = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  has(y, 'helpDmDelete:', `${lang} has DM delete help`);
  has(y, 'dmDeleteNotOwner:', `${lang} has owner-only DM delete message`);
  has(y, 'confirmDeleteMessage:', `${lang} has group delete confirmation`);
  has(y, 'makeAdmin:', `${lang} has room admin promotion label`);
  no(y, 'helpDmHide:', `${lang} has no DM hide help`);
}

console.log(`GROUP_STAGE3_HARNESS_PASS assertions=${checks}`);
