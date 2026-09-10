#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function check(ok, message) {
  assertions++;
  if (!ok) { console.error('RC23_WINDOW_CONTROLS_FAIL:', message); process.exit(1); }
}
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }

const rootUi = read('frontend/inner/40-root-auth.js');
const pipUi = read('frontend/inner/70-emoji-upload-compose.js');
const dmGroup = read('frontend/inner/140-group-management.js');
const profile = read('frontend/inner/110-auth-dm-core.js');
const multi = read('frontend/inner/115-private-multiwindow.js');

check(rootUi.includes('function publicChatMinimizeAvailable()'), 'minimize availability helper missing');
check(rootUi.includes('const viewport = publicChatMinimizeViewport();'), 'minimize availability is not tied to the public-chat viewport policy');
check(!rootUi.includes('if (state.minimized) return true;'), 'stale minimized state still bypasses the capability gate');
check(rootUi.includes('function reconcileMinimizeAvailability()'), 'minimize capability reconciliation missing');
check(rootUi.includes('toggleMin({persist: true, availabilityRestore: true})'), 'unsupported stale minimized state is not restored');
check(rootUi.includes('if (willMinimize && !publicChatMinimizeAvailable())'), 'toggle path does not reject unavailable minimize');
check(rootUi.includes('btn.style.setProperty("display", "none", "important")'), 'unavailable minimize button is not protected from legacy important CSS');
check(rootUi.includes('function updateMinimizeButtonVisibility()'), 'minimize button visibility updater missing');
check(rootUi.includes('installMinimizeAvailabilityGuard();'), 'minimize viewport guard not installed');
check(rootUi.includes('reconcileMinimizeAvailability();'), 'initial minimize capability reconciliation is not run');
check(rootUi.includes('window.addEventListener("resize", sync'), 'minimize visibility does not track resize');
check(rootUi.includes('window.visualViewport.addEventListener("resize", sync'), 'minimize visibility does not track visual viewport resize');
check(multi.includes('window.innerWidth >= Number(state.privateMultiWindowMinWidth || 900)'), 'private multi-window width threshold changed unexpectedly');
check(multi.includes('window.innerHeight >= Number(state.privateMultiWindowMinHeight || 480)'), 'private multi-window height threshold changed unexpectedly');

check(pipUi.includes('function documentPictureInPictureSupported()'), 'Document PiP feature detector missing');
check(pipUi.includes('typeof candidate.requestWindow === "function"'), 'Document PiP feature detector does not require requestWindow');
check(pipUi.includes('window.parent.documentPictureInPicture'), 'adapter parent Document PiP capability is not checked');
check(rootUi.includes('documentPictureInPictureSupported() ? `<button class="kwc-button kwc-pip"'), 'unsupported PiP button is still rendered');
check(pipUi.includes('&& documentPictureInPictureSupported();'), 'runtime PiP visibility does not re-check browser capability');

check(dmGroup.includes('t("button.closeAllDm", "Close all DMs")'), 'DM hub close-all label missing');
check(dmGroup.includes('t("button.closeAllGroups", "Close all group chats")'), 'group hub close-all label missing');
check((dmGroup.match(/closeAllPrivateConversationWindows\("dm"/g) || []).length >= 1, 'DM hub close does not close all detached DM windows');
check((dmGroup.match(/closeAllPrivateConversationWindows\("group"/g) || []).length >= 1, 'group hub close does not close all detached group windows');

check(profile.includes('ownerType === "global"'), 'global user-profile overlay path missing');
check(profile.includes('wrap.classList.add("kwc-user-profile-global-overlay")'), 'global user-profile overlay class missing');
check(profile.includes('wrap.style.zIndex = "2147483000"'), 'global user-profile overlay is not raised above management modals');
check(profile.includes('target.closest(".kwc-modal-backdrop, .kwc-modal-wrap")'), 'profile delegation does not detect body-level management modal');
check(profile.includes('ownerType = "global"'), 'profile delegation does not route management-list profile globally');

const lang = {
  'en-US.yml':['Close all DMs','Close all group chats'],
  'ko-KR.yml':['모든 DM 닫기','모든 그룹채팅 닫기'],
  'ja-JP.yml':['すべてのDMを閉じる','すべてのグループチャットを閉じる'],
  'zh-CN.yml':['关闭所有私信','关闭所有群聊']
};
for (const [file, values] of Object.entries(lang)) {
  const text = read(`kwc-platform-bukkit/src/main/resources/lang/${file}`);
  check(text.includes(`closeAllDm: ${values[0]}`), `${file} closeAllDm translation missing`);
  check(text.includes(`closeAllGroups: ${values[1]}`), `${file} closeAllGroups translation missing`);
}

const wrappers = [
  'kwc-adapter-bluemap/src/main/resources/web/chat.js',
  'kwc-adapter-dynmap/src/main/resources/dynmap/chat.js',
  'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.js',
  'kwc-adapter-overviewer/src/main/resources/overviewer/chat.js',
  'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.js',
  'kwc-adapter-squaremap/src/main/resources/squaremap/chat.js',
  'kwc-adapter-unmined/src/main/resources/unmined/chat.js',
  'kwc-standalone-frontend/src/main/resources/standalone/chat.js'
];
for (const wrapper of wrappers) {
  const text = read(wrapper);
  check(text.includes('function publicChatMinimizeAvailable()'), `${wrapper} missing generated minimize policy`);
  check(text.includes('function documentPictureInPictureSupported()'), `${wrapper} missing generated PiP feature detection`);
  check(text.includes('button.closeAllDm'), `${wrapper} missing generated close-all labels`);
  check(text.includes('kwc-user-profile-global-overlay'), `${wrapper} missing generated profile stacking fix`);
}

console.log(`RC23_WINDOW_CONTROLS_PASS assertions=${assertions} wrappers=${wrappers.length}`);
