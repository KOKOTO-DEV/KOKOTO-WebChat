#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../..');
let assertions = 0;
function ok(cond, msg) { assertions++; if (!cond) { console.error('FAIL', msg); process.exit(1); } }
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
const groupUi = read('frontend/inner/140-group-management.js');
const groupRooms = read('frontend/inner/130-group-rooms-archive.js');
const cssPaths = [
  'kwc-adapter-bluemap/src/main/resources/web/chat.css',
  'kwc-adapter-dynmap/src/main/resources/dynmap/chat.css',
  'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.css',
  'kwc-adapter-overviewer/src/main/resources/overviewer/chat.css',
  'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.css',
  'kwc-adapter-squaremap/src/main/resources/squaremap/chat.css',
  'kwc-adapter-unmined/src/main/resources/unmined/chat.css',
  'kwc-standalone-frontend/src/main/resources/standalone/chat.css'
];
const css = read(cssPaths[0]);

// DM search is no longer squeezed into the title action cluster.
ok(groupUi.includes('<div class="kwc-private-title-actions"><button type="button" class="kwc-button kwc-private-back-to-list kwc-hidden" id="kwc-dm-back-to-list"'), 'DM title actions remain');
ok(!groupUi.includes('<span class="kwc-private-window-tools"><button class="kwc-button kwc-private-window-tool kwc-hidden" id="kwc-dm-message-search-open"'), 'DM search not in title actions');
ok(groupUi.includes('<div class="kwc-private-search-float-row"><button class="kwc-button kwc-private-window-tool kwc-private-search-float kwc-hidden" id="kwc-dm-message-search-open"'), 'DM floating search exists');
ok(groupUi.indexOf('id="kwc-dm-message-search-open"') < groupUi.indexOf('id="kwc-dm-messages"'), 'DM search anchor precedes message surface');

// Group search follows the public-chat pattern and sits after the pinned bar.
const pinnedPos = groupUi.indexOf('id="kwc-group-pinned-bar"');
const searchPos = groupUi.indexOf('id="kwc-group-message-search-open"');
const messagesPos = groupUi.indexOf('id="kwc-group-messages"');
ok(pinnedPos >= 0 && searchPos > pinnedPos && messagesPos > searchPos, 'Group search is below pinned bar and above messages');
ok(groupUi.includes('const groupMessageSearch = wrap.querySelector("#kwc-group-message-search-open");'), 'Group floating search gets a stable click handler');
ok(groupUi.includes('openPrivateMessageSearchModal("group")'), 'Group floating search opens message search');
ok(!groupRooms.includes('<span class="kwc-private-window-tools"><button class="kwc-button kwc-private-window-tool${searchEnabled() ? "" : " kwc-hidden"}" id="kwc-group-message-search-open"'), 'Group search removed from dynamic title actions');
ok(groupRooms.includes('groupMessageSearch.classList.toggle("kwc-hidden", !searchEnabled() || !room || state.groupAuditMode)'), 'Group search visibility tracks active room/search/audit state');
ok((groupRooms.match(/groupMessageSearch\.classList\.add\("kwc-hidden"\)/g) || []).length >= 2, 'Group search hides for empty and audit views');

// Floating control is visually equivalent to the public search affordance without taking layout height.
ok(css.includes('.kwc-private-search-float-row {'), 'Private search anchor CSS exists');
ok(css.includes('flex: 0 0 0;'), 'Private search anchor consumes no vertical layout');
ok(css.includes('pointer-events: none;'), 'Search anchor itself does not steal clicks');
ok(css.includes('.kwc-private-search-float {'), 'Private search button CSS exists');
ok(css.includes('position: absolute !important;'), 'Private search floats over messages');
ok(css.includes('top: 8px;'), 'Private search offset below preceding pinned/title content');
ok(css.includes('right: 10px;'), 'Private search stays at right edge');
ok(css.includes('width: 34px !important;'), 'Private search matches public control width');
ok(css.includes('height: 34px !important;'), 'Private search matches public control height');
ok(css.includes('border-radius: 999px !important;'), 'Private search uses circular public-search treatment');
ok(css.includes('pointer-events: auto;'), 'Private search remains clickable');

// Wrapped group actions stay right aligned when they fit but remain scrollable when they do not.
ok(css.includes('.kwc-group-modal .kwc-group-title.kwc-group-title-wrapped > .kwc-group-actions::before {'), 'Wrapped group action spacer exists');
ok(css.includes('flex: 1 1 auto;'), 'Wrapped group action spacer absorbs spare width');
ok(css.includes('overflow-x: auto !important;'), 'Wrapped group action row can scroll when narrow');

// Event Open/Delete area is vertically centered against the two-line metadata block.
ok(/\.kwc-game-list-row \{[\s\S]*?align-items: center !important;/.test(css), 'Event list row centers columns vertically');
ok(/\.kwc-game-list-actions \{[\s\S]*?align-self: center;/.test(css), 'Event actions center themselves vertically');

// All adapter/standalone CSS copies remain byte-identical.
const base = fs.readFileSync(path.join(root, cssPaths[0]));
for (const rel of cssPaths) {
  ok(Buffer.compare(base, fs.readFileSync(path.join(root, rel))) === 0, `${rel}: CSS synchronized`);
}
console.log(`RC30_PRIVATE_SEARCH_LAYOUT_PASS assertions=${assertions} css=${cssPaths.length}`);
