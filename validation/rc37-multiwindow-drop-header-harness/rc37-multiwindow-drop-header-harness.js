// KWC file guide / KWC 파일 안내
// RC37 locks detached private-window drag/drop ownership and the public-header compact threshold.
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel){ return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(ok,msg){ assertions++; if(!ok) throw new Error(msg); }
function has(t,n,m){ check(t.includes(n), `${m} [missing: ${n}]`); }

const multi = read('frontend/inner/115-private-multiwindow.js');
has(multi, 'return presentation.privateMultiWindowBase', 'private multi-window runtime is Standalone-only through presentation capability');
has(multi, 'function installPrivateChildDragAndDropUpload(record)', 'detached private child has its own drop installer');
has(multi, 'wrap.addEventListener("dragenter", onEnterOrOver, {capture:true});', 'child dragenter is captured');
has(multi, 'wrap.addEventListener("dragover", onEnterOrOver, {capture:true});', 'child dragover is captured');
has(multi, 'wrap.addEventListener("drop", onDrop, {capture:true});', 'child drop is captured');
has(multi, 'await activatePrivateConversationWindow(record.type, record.key);', 'drop activates the exact child conversation');
has(multi, 'privateActiveConversationWindowKey(record.type) !== String(record.key || "")', 'drop verifies the requested child stayed active');
has(multi, 'const inputId = record.type === "group" ? "kwc-group-input" : "kwc-dm-input";', 'drop resolves DM/group compose input explicitly');
has(multi, '!record.body || !record.body.contains(input)', 'drop confirms live composer belongs to the destination child');
has(multi, 'setActiveComposeInput(input);', 'drop binds the shared uploader to the destination composer');
has(multi, 'await uploadFiles(files, "drop");', 'child drop uses the standard upload pipeline');
has(multi, 'installPrivateChildDragAndDropUpload(record);', 'every created child installs drop handling');
has(multi, 'if (record.dropCleanup) record.dropCleanup();', 'child drag cleanup is released on window close');

const dm = read('frontend/inner/120-dm-ui-typing.js');
const group = read('frontend/inner/140-group-management.js');
has(dm, 'installDirectMessageDragAndDropUpload(wrap)', 'original DM list-window drop remains installed');
has(group, 'installGroupChatDragAndDropUpload(wrap)', 'original group list-window drop remains installed');

const frame = read('frontend/inner/30-reply-identity-frame.js');
has(frame, 'width = Math.max(46, width);', 'normal public action measurement uses 46px wrapped-row footprint');
check((frame.match(/Math\.max\(30, Math\.round\(headerOuterWidth\(control\) \|\| 30\)\)/g) || []).length >= 2,
  'PIP/minimize compact floor is at least 30px in both metric paths');
check(!frame.includes('contentWidth + 1 >= requiredNaturalOneRowWidth'), 'old delayed unwrap threshold is removed');
has(frame, 'contentWidth + 1 >= requiredCompactOneRowWidth', 'wrapped header returns to one row as soon as compact controls fit');
has(frame, 'contentWidth + 1 < requiredCompactOneRowWidth', 'one-row header wraps only below the same compact threshold');

const cssRel = 'kwc-standalone-frontend/src/main/resources/standalone/chat.css';
const css = read(cssRel);
has(css, '5.3.0 RC37: public-header action sizing/threshold correction.', 'RC37 header CSS override exists');
has(css, 'width: 46px !important;', 'one-row normal action width is 46px');
has(css, 'min-width: 30px !important;', 'public action compact floor is 30px');
has(css, 'flex: 0 1 46px !important;', 'one-row action controls shrink from 46px');
has(css, 'flex: 1 1 46px !important;', 'wrapped action controls share the same 46px normal basis');
has(css, 'max-width: 54px !important;', 'wrapped controls may use existing spare-row expansion');

const cssFiles = [
  'kwc-adapter-bluemap/src/main/resources/web/chat.css',
  'kwc-adapter-dynmap/src/main/resources/dynmap/chat.css',
  'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.css',
  'kwc-adapter-overviewer/src/main/resources/overviewer/chat.css',
  'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.css',
  'kwc-adapter-squaremap/src/main/resources/squaremap/chat.css',
  'kwc-adapter-unmined/src/main/resources/unmined/chat.css',
  cssRel
];
const hashes = cssFiles.map(rel => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, rel))).digest('hex'));
check(new Set(hashes).size === 1, 'all eight adapter/standalone CSS files are byte-identical');

console.log(`RC37_MULTIWINDOW_DROP_HEADER_PASS assertions=${assertions}`);
