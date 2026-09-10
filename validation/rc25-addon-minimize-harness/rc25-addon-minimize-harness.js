#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function check(ok, message) {
  assertions++;
  if (!ok) { console.error('RC25_ADDON_MINIMIZE_FAIL:', message); process.exit(1); }
}
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
const ui = read('frontend/inner/40-root-auth.js');
const multi = read('frontend/inner/115-private-multiwindow.js');
check(ui.includes('function publicChatMinimizeViewport()'), 'public-chat host viewport helper missing');
check(ui.includes('if (!state.isStandalone && !state.isPip)'), 'adapter/add-on viewport branch missing');
check(ui.includes('target = window.parent'), 'adapter/add-on minimize does not use the map parent viewport');
check(ui.includes('target.visualViewport || null'), 'parent visualViewport is not preferred');
check(ui.includes('Number(target.innerWidth)'), 'parent innerWidth fallback missing');
check(ui.includes('Number(target.innerHeight)'), 'parent innerHeight fallback missing');
check(ui.includes('const viewport = publicChatMinimizeViewport();'), 'minimize availability does not use host viewport helper');
check(ui.includes('viewport.width >= minW && viewport.height >= minH'), 'minimize threshold is not applied to host viewport');
check(!ui.includes('typeof privateMultiWindowSupported === "function" && privateMultiWindowSupported()'), 'public minimize is still coupled directly to detached private-window capability');
check(multi.includes('window.innerWidth >= Number(state.privateMultiWindowMinWidth || 900)'), 'detached DM/group width must remain iframe/runtime based');
check(multi.includes('window.innerHeight >= Number(state.privateMultiWindowMinHeight || 480)'), 'detached DM/group height must remain iframe/runtime based');
check(ui.includes('parentWindow.addEventListener("resize", sync'), 'parent resize does not refresh minimize availability');
check(ui.includes('parentWindow.addEventListener("orientationchange", sync'), 'parent orientation change does not refresh minimize availability');
check(ui.includes('parentWindow.visualViewport.addEventListener("resize", sync'), 'parent visualViewport resize is not tracked');
check(ui.includes('if (willMinimize && !publicChatMinimizeAvailable())'), 'toggle path does not enforce host minimize availability');
check(ui.includes('if (!publicChatMinimizeAvailable() && state.minimized)'), 'stale minimized state is not reconciled against host viewport');
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
  check(text.includes('function publicChatMinimizeViewport()'), `${wrapper} missing generated host viewport policy`);
}
console.log(`RC25_ADDON_MINIMIZE_PASS assertions=${assertions} wrappers=${wrappers.length}`);
