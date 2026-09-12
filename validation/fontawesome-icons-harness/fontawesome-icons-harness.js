#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../..');
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(v, m) { assertions++; if (!v) throw new Error(m); }
function has(t, n, m) { check(t.includes(n), m || `missing: ${n}`); }

const manifest = read('frontend/inner/manifest.txt').split(/\r?\n/).map(s => s.trim()).filter(Boolean);
check(manifest.includes('05-fontawesome-icons.js'), 'Font Awesome registry fragment must be bundled');
const icons = read('frontend/inner/05-fontawesome-icons.js');
has(icons, 'Font Awesome Free 6.7.2', 'registry attribution comment');
for (const name of ['window-restore','minus','envelope','user-group','calendar-days','bell','thumbtack','arrow-down','magnifying-glass','face-smile','paperclip','xmark','arrow-up','shield-halved','key','check','play','globe','lock','user']) {
  has(icons, `"${name}"`, `registry icon ${name}`);
}
has(icons, 'function kwcFaIcon(', 'shared icon renderer');
has(icons, 'fill="currentColor"', 'icons inherit active theme color');

const rootUi = read('frontend/inner/40-root-auth.js');
for (const [name, needle] of [
  ['PiP','kwcFaIcon("window-restore")'], ['minimize','kwcFaIcon("minus")'], ['DM','kwcFaIcon("envelope")'],
  ['group','kwcFaIcon("user-group")'], ['Event','kwcFaIcon("calendar-days")'], ['notification','kwcFaIcon("bell")'],
  ['pin','kwcFaIcon("thumbtack")'], ['latest','kwcFaIcon("arrow-down")'], ['search','kwcFaIcon("magnifying-glass")'],
  ['emoji','kwcFaIcon("face-smile")'], ['upload','kwcFaIcon("paperclip")']
]) has(rootUi, needle, `${name} uses Font Awesome`);
for (const legacy of ['>▣</button>','>✉','>👥','>🎲</button>','>🔔','>📌</span>','>⌕</button>','>☺</button>','&#128206;']) {
  check(!rootUi.includes(legacy), `legacy core glyph removed: ${legacy}`);
}

const privateUi = read('frontend/inner/140-group-management.js');
has(privateUi, 'id="kwc-dm-message-search-open"', 'DM message search control exists');
has(privateUi, 'id="kwc-group-message-search-open"', 'group message search control exists');
has(privateUi, 'id="kwc-group-pinned-open"', 'group pinned-message control exists');
has(privateUi, 'id="kwc-dm-emoji"', 'DM composer emoji control exists');
has(privateUi, 'id="kwc-group-emoji"', 'group composer emoji control exists');
has(privateUi, 'id="kwc-dm-upload"', 'DM composer upload control exists');
has(privateUi, 'id="kwc-group-upload"', 'group composer upload control exists');
has(privateUi, 'kwcFaIcon("magnifying-glass")', 'private search controls use Font Awesome');
has(privateUi, 'kwcFaIcon("thumbtack")', 'group pinned control uses Font Awesome');
has(privateUi, 'kwcFaIcon("face-smile")', 'private composer emoji controls use Font Awesome');
has(privateUi, 'kwcFaIcon("paperclip")', 'private composer upload controls use Font Awesome');
check(!privateUi.includes('&#128206;'), 'legacy private paperclip glyph removed');

const notice = read('THIRD_PARTY_NOTICES.md');
has(notice, 'Font Awesome Free 6.7.2', 'root third-party notice');
has(notice, 'Creative Commons Attribution 4.0', 'CC BY attribution');
check(fs.existsSync(path.join(root, 'frontend/vendor/fontawesome-free-6.7.2/LICENSE.txt')), 'upstream Font Awesome license retained');
check(fs.existsSync(path.join(root, 'kwc-core/src/main/resources/META-INF/LICENSE-fontawesome-free.txt')), 'Font Awesome license embedded into JAR resources');
check(fs.existsSync(path.join(root, 'kwc-core/src/main/resources/META-INF/THIRD_PARTY_NOTICES.md')), 'third-party notice embedded into JAR resources');

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
const canonical = read(cssPaths[0]);
has(canonical, '\n.kwc-fa-icon {', 'shared icon CSS applies outside #kwc-root for detached private windows');
check(!canonical.includes('#kwc-root .kwc-fa-icon {'), 'Font Awesome normalization is not restricted to public #kwc-root');
has(canonical, 'color: currentColor;', 'icon CSS uses currentColor');
has(canonical, '.kwc-admin-audit-notice .kwc-fa-icon', 'admin audit badge has compact icon geometry');
has(canonical, '.kwc-private-audit-badge .kwc-fa-icon', 'DM audit badge has compact icon geometry');
has(canonical, '.kwc-group-audit-badge .kwc-fa-icon', 'group audit badge has compact icon geometry');
has(canonical, '.kwc-private-search-float .kwc-fa-icon', 'private search icon geometry is normalized');
has(canonical, 'width: 15px;', 'private search artwork is bounded inside its button');
has(canonical, '.kwc-private-message-delete .kwc-fa-icon', 'private delete icon geometry is normalized');
has(canonical, 'width: 14px;', 'private delete artwork is bounded inside its 20px control');
has(canonical, '.kwc-dm-compose .kwc-icon-button', 'private composer icon buttons have fixed public-compatible geometry');
has(canonical, '.kwc-dm-compose .kwc-dm-emoji-button', 'private composer emoji size has a dedicated parity rule');
has(canonical, 'width: 17px;', 'private emoji artwork matches public 17px visual size');
has(canonical, '.kwc-dm-compose .kwc-dm-upload', 'private composer upload size has a dedicated parity rule');
has(canonical, 'width: 16px;', 'private upload artwork matches public 16px visual size');
has(canonical, 'width: 11px;', 'audit/admin shield artwork uses a fixed compact badge size');
has(canonical, '.kwc-group-modal .kwc-pinned-icon .kwc-fa-icon', 'group pinned icon has explicit detached-window geometry');
for (const rel of cssPaths) check(read(rel) === canonical, `${rel} stays byte-identical with canonical CSS`);

for (const rel of ['en-US.yml','ko-KR.yml','ja-JP.yml','zh-CN.yml']) {
  const lang = read('kwc-platform-bukkit/src/main/resources/lang/' + rel);
  check(!/load(?:TikTok|XPost|Video|Audio):\s*▶/.test(lang), `${rel} media labels no longer embed play glyph`);
}
console.log(`FONT_AWESOME_ICONS_PASS assertions=${assertions} version=6.7.2 presentations=${cssPaths.length}`);
