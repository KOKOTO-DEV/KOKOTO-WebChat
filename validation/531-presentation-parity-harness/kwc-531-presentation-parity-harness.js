'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(ok, msg) { assertions++; if (!ok) throw new Error(msg); }
function has(text, needle, msg) { check(text.includes(needle), msg + ` [missing: ${needle}]`); }
function no(text, needle, msg) { check(!text.includes(needle), msg + ` [unexpected: ${needle}]`); }
function sha(text) { return crypto.createHash('sha256').update(text).digest('hex'); }

const bootstrap = read('frontend/inner/00-bootstrap-state.js');
has(bootstrap, 'const runtimePresentation = Object.freeze({', 'common presentation contract exists');
has(bootstrap, 'presentationMode: runtimePresentation.mode', 'state records canonical presentation mode');
has(bootstrap, 'isAddon: runtimePresentation.addon', 'addon is presentation identity, not an adapter setting');
has(bootstrap, 'function presentationCapabilities()', 'shared presentation capability helper exists');
has(bootstrap, 'ownsRealtimeConnection: !pip', 'PiP transport ownership is presentation-only');
has(bootstrap, 'ownsWebPushRegistration: !pip', 'PiP push ownership is presentation-only');
for (const adapter of ['bluemap','dynmap','liveatlas','overviewer','pl3xmap','squaremap','unmined']) {
  no(bootstrap, `kwc.${adapter}.`, `common state has no ${adapter}-specific settings namespace`);
}

const eventUi = read('frontend/inner/85-chat-games.js');
for (const forbidden of ['state.isPip', 'state.isStandalone', 'state.isAddon', 'navigator.userAgentData.mobile']) {
  no(eventUi, forbidden, `Event UI/state does not fork on presentation via ${forbidden}`);
}
for (const needle of ['kwc-game-auto-capacity','kwc-game-auto-response-enabled','kwc-game-auto-end-at','chatGameAutoEndText(game)']) {
  has(eventUi, needle, `automatic-end Event UI remains common: ${needle}`);
}

const auth = read('frontend/inner/40-root-auth.js');
has(auth, 'status.onclick = event =>', 'admin control uses the common auth/header path');
has(auth, 'openAdminModal()', 'admin modal opening remains common');
has(auth, 'presentation.publicMinimizeBase', 'minimize is presentation capability, not separate setting');
const frame = read('frontend/inner/30-reply-identity-frame.js');
has(frame, 'button, input, select, textarea, a, [role=\\"button\\"]', 'common drag layer excludes semantic interactive targets');
has(frame, 'presentationCapabilities().draggableWindowBase', 'drag availability uses presentation capability');
const privateMulti = read('frontend/inner/115-private-multiwindow.js');
has(privateMulti, 'presentation.privateMultiWindowBase', 'private multi-window is an explicit presentation capability');

const notifications = read('frontend/inner/90-notifications.js');
for (const endpoint of ['/preferences/notifications','/preferences/presence']) has(notifications, endpoint, `account preference endpoint ${endpoint} is canonical`);

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
const css = [
  'kwc-adapter-bluemap/src/main/resources/web/chat.css',
  'kwc-adapter-dynmap/src/main/resources/dynmap/chat.css',
  'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.css',
  'kwc-adapter-overviewer/src/main/resources/overviewer/chat.css',
  'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.css',
  'kwc-adapter-squaremap/src/main/resources/squaremap/chat.css',
  'kwc-adapter-unmined/src/main/resources/unmined/chat.css',
  'kwc-standalone-frontend/src/main/resources/standalone/chat.css'
];
const inner = read('inner.js');
for (const rel of wrappers) {
  const text = read(rel);
  const match = text.match(/^\s*const KWC_EMBEDDED_INNER_TEXT = (.*);$/m);
  check(!!match, `${rel} has embedded inner payload`);
  check(JSON.parse(match[1]) === inner, `${rel} embeds canonical inner.js`);
}
const cssTexts = css.map(read);
const cssHash = sha(cssTexts[0]);
cssTexts.forEach((text, i) => check(sha(text) === cssHash, `${css[i]} matches canonical CSS`));

const prefs = read('frontend/inner/100-preferences-search.js');
for (const scope of ['bluemap','dynmap','liveatlas','overviewer','pl3xmap','squaremap','unmined','standalone','pip','mobile','desktop']) {
  no(prefs, `kwc.${scope}.`, `preference storage has no presentation-specific namespace kwc.${scope}.`);
}
console.log(`KWC_531_PRESENTATION_PARITY_PASS assertions=${assertions} cssSha256=${cssHash}`);
