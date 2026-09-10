#!/usr/bin/env node
// KWC 파일 안내 / KWC file guide
// 사용자 차단, 관리자 제재, 모데레이터별 capability가 API/UI/알림 경계에 연결됐는지 검증한다.
// Verifies that personal blocks, administrative user restrictions, and per-moderator capabilities are wired through API/UI/notification boundaries.
'use strict';
const fs=require('fs'), path=require('path');
const root=path.resolve(process.argv[2]||path.join(__dirname,'../..'));
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let n=0; const check=(c,m)=>{n++; if(!c){console.error('USER_CONTROL_UI_FAIL: '+m); process.exit(1);}};
const server=read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const store=read('kwc-core/src/main/java/dev/kokoto/webchat/UserControlStore.java');
const prefs=read('kwc-core/src/main/java/dev/kokoto/webchat/UserPreferenceStore.java');
const inner=read('inner.js');
const admin=read('frontend/inner/80-pins-admin.js');
const profileUi=read('frontend/inner/110-auth-dm-core.js');
const publicVirtual=read('frontend/inner/50-public-history-virtual-scroll.js');
const css=read('kwc-standalone-frontend/src/main/resources/standalone/chat.css');
const bukkit=read('kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/KwcCommand.java');
const game=read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
for(const endpoint of ['/preferences/blocked-users','/admin/user-controls','/admin/moderator-permissions','/admin/profile-avatar/delete','/admin/account-role']) check(server.includes(endpoint), 'endpoint '+endpoint);
for(const cap of ['view-online','message-delete','guest-mute','pin-manage','user-restrictions','profile-avatar-delete','content-filter-manage','emoji-manage']) check(store.includes('"'+cap+'"'), 'capability '+cap);
check(server.includes('SessionContext ctx = requireUserSession(ex);'), 'online endpoint authenticated-user access');
check(server.includes('boolean includeOffline = "1".equals(q.get("includeOffline"))'), 'offline list available to authenticated users');
check(server.includes('moderatorCapabilityAllowed(ctx, "message-delete")'), 'delete endpoint capability with ordinary-owner fallback');
check(server.includes('requireModeratorCapability(ex, "guest-mute")'), 'mute endpoint capability');
check(server.includes('requireModeratorCapability(ex, "pin-manage")'), 'pin endpoint capability');
check(server.includes('requireModeratorCapability(ex, "profile-avatar-delete")'), 'avatar delete endpoint capability');
check(server.includes('requireModeratorCapability(ex, "content-filter-manage")'), 'content-filter endpoint capability');
check(server.includes('requireModeratorCapability(ex, "emoji-manage")'), 'emoji endpoint capability');
check(server.includes('moderatorCapabilityAllowed(ctx, "user-restrictions")'), 'restriction capability');
check(server.includes('if (target.role == Role.ADMIN)'), 'admin account restriction protection');
check(server.includes('userControls.chatBanned(ctx.account.uuid)'), 'web chat ban enforcement');
check(server.includes('userControls.uploadBanned(ctx.account.uuid)'), 'web upload ban enforcement');
check(server.includes('userControls.chatBanned(playerUuid)'), 'game public chat ban enforcement');
check(game.includes('server.userChatBanned(sender.uuid().toString())'), 'game reply chat ban enforcement');
check(game.includes('filterServer.directMessageBlocked(me, target.uuid)'), 'game DM personal block enforcement');
check(bukkit.includes('policyServer.directMessageBlocked(senderUuid, target.uuid)'), 'Bukkit DM personal block enforcement');
check(server.includes('userPreferences.isUserBlocked(account, msg.playerUuid)'), 'public push block suppression');
check(server.includes('userPreferences.isUserBlocked(targetAccount, senderUuid)'), 'DM push block suppression');
check(server.includes('userPreferences.isUserBlocked(member, blockSenderUuid)'), 'group push block suppression');
check(prefs.includes('MAX_BLOCKED_USERS = 500'), 'personal block safety limit');
check(profileUi.includes('id="kwc-user-profile-block"'), 'profile block button');
check(profileUi.includes('kwc-user-profile-identity-row'), 'block button located in profile identity row');
check(profileUi.includes('id="kwc-user-profile-blocked-users"'), 'self profile blocked-user list');
check(profileUi.includes('data-kwc-unblock-user'), 'profile unblock action');
check(!read('frontend/inner/100-preferences-search.js').includes('kwc-prefs-blocked-users'), 'chat settings do not duplicate blocked-user list');
check(admin.includes('data-panel="mutes"') && admin.includes('blocksAndRestrictions'), 'admin blocks/restrictions combined operations panel');
check(!admin.includes('<button class="kwc-button kwc-tab" data-panel="user-controls"'), 'no separate user-restrictions admin tab');
check(admin.includes('data-panel="moderator-permissions"'), 'admin moderator-permissions panel');
check(admin.includes('data-user-chat-ban'), 'chat-ban checkbox');
check(admin.includes('data-user-upload-ban'), 'upload-ban checkbox');
check(admin.includes('data-user-avatar-delete'), 'admin avatar-delete action');
check(admin.includes('data-moderator-capability'), 'per-moderator capability checkboxes');
check(admin.includes('kwc-moderator-real-name'), 'moderator display name includes the actual account name');
check(css.includes('.kwc-modal-backdrop.kwc-user-prefs-backdrop .kwc-admin-user-controls > .kwc-admin-item') && css.includes('.kwc-modal-backdrop.kwc-user-prefs-backdrop .kwc-admin-moderator-permissions > .kwc-admin-item'), 'only innermost restriction/moderator user rows lose card borders');
check(admin.includes('const splitAt = Math.ceil(capabilities.length / 2)') && admin.includes('kwc-admin-capability-column'), 'moderator capabilities split into balanced 5/4 columns');
check(profileUi.includes('state.blockedUserUuids = Array.from(ids)') && profileUi.includes('renderVirtualMessages({stickToBottom:false'), 'block success updates local state and public rows immediately');
check(profileUi.includes('renderDirectMessageThreads()') && profileUi.includes('renderGroupChatMessages'), 'block success refreshes private surfaces immediately');
check(publicVirtual.includes('kwc-personally-blocked') && publicVirtual.includes('messageElementNeedsRebuild'), 'virtual public rows rebuild when personal block state changes');
check(profileUi.includes('id="kwc-user-profile-role-select"') && profileUi.includes('/admin/account-role'), 'profile role-change control uses admin API');
check(server.includes('cannot_change_own_role') && server.includes('storage.setRole(target, role)'), 'server role change blocks self-change and persists target role');
for(const lang of ['en-US','ko-KR','ja-JP','zh-CN']){
 const y=read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
 for(const key of ['blockedUsers:','blockUser:','unblockUser:','moderatorPermissions:','chatBan:','uploadBan:','deleteProfileImage:']) check(y.includes(key), `${lang} ${key}`);
}
console.log('USER_CONTROL_UI_PASS assertions='+n);
