#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
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
let assertions = 0;
function ok(v, m) { if (!v) throw new Error('ASSERT FAILED: ' + m); assertions++; }
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
const cssTexts = cssFiles.map(read);
const hashes = cssTexts.map(t => crypto.createHash('sha256').update(t).digest('hex'));
ok(new Set(hashes).size === 1, 'all eight CSS copies remain byte-identical');
for (let i = 0; i < cssFiles.length; i++) {
  const css = cssTexts[i];
  const tag = cssFiles[i];
  ok(css.includes('--kwc-reaction-picker-cell: clamp(36px, 10vw, 40px);'), `${tag}: reaction cell clamp`);
  ok(css.includes('--kwc-reaction-picker-icon: clamp(21px, 6vw, 24px);'), `${tag}: reaction icon clamp`);
  ok(css.includes('width: min(380px, calc(100vw - 12px));'), `${tag}: picker width enlarged without exceeding viewport`);
  ok(/\.kwc-reaction-categories \{[\s\S]*?overflow-x: auto;[\s\S]*?overflow-y: hidden;/.test(css), `${tag}: category strip scrolls horizontally`);
  ok(/\.kwc-reaction-category,[\s\S]*?\.kwc-reaction-choice \{[\s\S]*?min-width: var\(--kwc-reaction-picker-cell\);[\s\S]*?height: var\(--kwc-reaction-picker-cell\);/.test(css), `${tag}: tappable cell geometry enlarged`);
  ok(/\.kwc-reaction-grid \{[\s\S]*?grid-template-columns: repeat\(8, var\(--kwc-reaction-picker-cell\)\);[\s\S]*?overflow: auto;/.test(css), `${tag}: reaction grid permits both-axis scrolling`);
  ok(/\.kwc-reaction-choice \.kwc-reaction-emoji \{[\s\S]*?width: clamp\(26px, 7vw, 28px\);[\s\S]*?height: clamp\(26px, 7vw, 28px\);/.test(css), `${tag}: custom reaction images enlarged`);
  ok(/\.kwc-reaction-emoji \{\s*width: 17px;\s*height: 17px;/.test(css), `${tag}: message reaction chip image size unchanged`);
}
const js = read('frontend/inner/20-emoji-reactions.js');
ok(js.includes('picker.className = "kwc-reaction-picker";'), 'reaction picker still uses dedicated picker class');
ok(js.includes('class="kwc-reaction-choice'), 'reaction choices still use dedicated choice class');
console.log(`RC27_REACTION_PICKER_PASS assertions=${assertions} css=${cssFiles.length}`);
