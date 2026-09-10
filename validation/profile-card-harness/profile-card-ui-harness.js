#!/usr/bin/env node
// KWC 파일 안내 / KWC file guide
// 5.3.0 프로필 카드 API/UI와 오프라인 목록 옵션이 배포 surface에 연결됐는지 검증한다.
// Verifies that the 5.3.0 profile-card API/UI and offline-list option are wired into deployable surfaces.
'use strict';
const fs=require('fs'), path=require('path');
const root=path.resolve(process.argv[2]||path.join(__dirname,'../..'));
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let n=0; const check=(c,m)=>{n++; if(!c){console.error('PROFILE_CARD_UI_FAIL: '+m); process.exit(1);}};
const server=read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const prefs=read('kwc-core/src/main/java/dev/kokoto/webchat/UserPreferenceStore.java');
const inner=read('inner.js');
check(server.includes('/preferences/profile-card'), 'profile-card endpoint');
check(server.includes('/preferences/profile-avatar'), 'profile-avatar endpoint');
check(server.includes('/profile/avatar'), 'public avatar endpoint');
check(server.includes('ImageMetadataStripper.stripForUpload(file.data, ext)'), 'avatar metadata stripping');
check(server.includes('2L * 1024L * 1024L'), 'avatar 2MiB limit');
check(server.includes('profile_avatar_type_not_allowed'), 'avatar type allowlist');
check(server.includes('out.put("profile", publicProfileCard'), 'presence profile payload');
check(server.includes('https://mc-heads.net/avatar/'), 'minecraft head URL');
check(server.includes('/profile/default-head'), 'local default head endpoint');
check(server.includes('out.put("defaultHeadUrl", defaultHead)'), 'profile payload default head fallback');
check(inner.includes('t("presence.aboutEmpty"'), 'other-user empty about rendering');
check(inner.includes('const aboutHtml = selfProfile'), 'self edit versus other-user about display split');
check(prefs.includes('MAX_PROFILE_ABOUT = 280'), 'about limit');
check(prefs.includes('profile-assets'), 'dedicated avatar storage');
check(prefs.includes('PROFILE_AVATAR_MODES = Set.of("none", "custom", "minecraft")'), 'avatar mode allowlist');
check(inner.includes('id="kwc-user-profile-about"'), 'about editor');
check(inner.includes('id="kwc-user-profile-avatar-mode"'), 'avatar mode selector');
check(inner.includes('id="kwc-user-profile-avatar-upload"'), 'avatar upload button');
check(inner.includes('id="kwc-user-profile-avatar-delete"'), 'avatar delete button');
check(inner.includes('id="kwc-user-profile-card-save"'), 'profile save button');
check(inner.includes('referrerPolicy = "no-referrer"'), 'external head referrer suppression');
check(inner.includes('kwc.adminShowOfflineUsers'), 'offline-list local option');
check(server.includes('includeOffline'), 'offline-list server query');
check(!inner.includes('Math.max(1, Number(res && res.loggedInCount'), 'logged-in count can reach zero');
for (const lang of ['en-US','ko-KR','ja-JP','zh-CN']) {
 const y=read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
 check(y.includes('profileImage:'), `${lang} profile image label`);
 check(y.includes('aboutPlaceholder:'), `${lang} about placeholder`);
 check(y.includes('showOfflineUsers:'), `${lang} offline user option label`);
}
console.log('PROFILE_CARD_UI_PASS assertions='+n);
