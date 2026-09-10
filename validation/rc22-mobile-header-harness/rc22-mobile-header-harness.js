#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function check(ok, message) {
  assertions++;
  if (!ok) { console.error('RC22_MOBILE_HEADER_FAIL:', message); process.exit(1); }
}
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
const rootUi = read('frontend/inner/40-root-auth.js');
const frame = read('frontend/inner/30-reply-identity-frame.js');
const multi = read('frontend/inner/115-private-multiwindow.js');
const group = read('frontend/inner/140-group-management.js');
check(rootUi.includes('kwc-action-cluster-chat'), 'chat action cluster missing');
check(rootUi.includes('kwc-action-cluster-account'), 'account action cluster missing');
check(/kwc-action-cluster-chat[\s\S]*id="kwc-dm"[\s\S]*id="kwc-group"[\s\S]*id="kwc-game"[\s\S]*id="kwc-notifications"/.test(rootUi), 'chat/event/notification controls are not grouped');
check(/kwc-action-cluster-account[\s\S]*id="kwc-login"/.test(rootUi), 'login/account control is not grouped');
check(frame.includes('protectedTitleWidth'), 'responsive header does not reserve title width');
check(frame.includes('Math.min(150, Math.max(92, titleNaturalWidth))'), 'title reservation bounds changed unexpectedly');
check(frame.includes('group.querySelectorAll("button")'), 'nested action buttons are not measured');
check(multi.includes('function installPrivateMobileViewportFit(wrap)'), 'mobile visual viewport fitter missing');
check(multi.includes('window.visualViewport'), 'visualViewport not used');
check(multi.includes('window.visualViewport.addEventListener("resize", sync'), 'visualViewport resize listener missing');
check(multi.includes('window.visualViewport.addEventListener("scroll", sync'), 'visualViewport scroll listener missing');
check(multi.includes('wrap.style.setProperty("height", height + "px", "important")'), 'visible viewport height not applied');
check(multi.includes('wrap.style.setProperty("top", top + "px", "important")'), 'visible viewport top offset not applied');
check(multi.includes('modal.style.setProperty("height", "100%", "important")'), 'private modal does not fit the guarded backdrop');
check((group.match(/installPrivateMobileViewportFit\(wrap\);/g) || []).length === 2, 'DM and group must both install mobile viewport fit');
check((group.match(/__kwcMobileViewportCleanup/g) || []).length >= 2, 'DM and group close paths must cleanup viewport listeners');
const cssFiles = [
  'kwc-adapter-bluemap/src/main/resources/web/chat.css',
  'kwc-adapter-dynmap/src/main/resources/dynmap/chat.css',
  'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.css',
  'kwc-adapter-overviewer/src/main/resources/overviewer/chat.css',
  'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.css',
  'kwc-adapter-squaremap/src/main/resources/squaremap/chat.css',
  'kwc-adapter-unmined/src/main/resources/unmined/chat.css',
  'kwc-standalone-frontend/src/main/resources/standalone/chat.css'
];
const cssTexts = cssFiles.map(read);
cssTexts.forEach((css, i) => {
  check(css.includes('.kwc-dm-modal-backdrop.kwc-private-mobile-viewport'), `${cssFiles[i]} mobile viewport css missing`);
  check(css.includes('.kwc-action-cluster-chat'), `${cssFiles[i]} header cluster css missing`);
});
check(cssTexts.every(css => css === cssTexts[0]), '8 wrapper CSS files are not byte-identical');
console.log(`RC22_MOBILE_HEADER_PASS assertions=${assertions} wrappers=${cssFiles.length}`);
