#!/usr/bin/env node
// KWC 파일 안내 / KWC file guide
// private-search-ui-harness.js는 KWC 개발/배포 과정에서 사용하는 JavaScript 보조 코드다.
// private-search-ui-harness.js is JavaScript support code used by the KWC development or packaging workflow.
// 배포 runtime 코드와 생성 코드를 구분하고, generated 산출물을 수동 편집하지 않도록 source-of-truth 경로를 유지한다.
// Keep runtime source separate from generated artifacts and preserve the source-of-truth path instead of manually editing generated output.

const fs = require('fs');
const path = require('path');

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function ok(cond, label) {
  assertions++;
  if (!cond) throw new Error(`ASSERTION FAILED: ${label}`);
}
function has(text, needle, label) { ok(text.includes(needle), label); }
function checkNoResizeLock() {
  ok(!inner.includes('data-kwc-resize-lock-toggle'), 'resize-lock DOM markers are removed');
  ok(!inner.includes('kwc.resizeLocked'), 'resize-lock localStorage/state is removed');
  ok(!inner.includes('toggleResizeLocked'), 'resize-lock action is removed');
}

const inner = read('inner.js');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const dmStore = read('kwc-core/src/main/java/dev/kokoto/webchat/DirectMessageStore.java');
const groupStore = read('kwc-core/src/main/java/dev/kokoto/webchat/GroupChatStore.java');

has(inner, 'function openPrivateMessageSearchModal(type = "dm")', 'private search modal exists');
has(inner, 'function jumpToPrivateSearchTarget(messageId, type = "dm", expectedContextId = "")', 'private result jump exists');
has(inner, 'const before = id + 1;', 'old-result direct page lookup uses target boundary');
has(inner, '/dm/messages?threadId=${encodeURIComponent(contextId)}&before=', 'DM target page fetch exists');
has(inner, '/group/messages?roomId=${encodeURIComponent(contextId)}&before=', 'group target page fetch exists');
has(inner, '`/${type === "group" ? "group" : "dm"}/search?${params.toString()}`', 'private search selects correct API');
checkNoResizeLock();
has(inner, 'kwc-window-resize-zone', 'public/private transparent edge-resize zones remain available');
has(inner, 'installStandaloneRootResizeZones(root)', 'standalone public chat retains transparent edge resize');
has(inner, 'installTransparentWindowResize(wrap, modal, key)', 'DM/group child windows retain transparent edge resize');
has(inner, 'openPrivateMessageSearchModal("dm")', 'DM search button binding exists');
has(inner, 'openPrivateMessageSearchModal("group")', 'group search button binding exists');

has(server, 'createExactContext(p + "/dm/search", this::handleDmSearch);', 'DM search route registered');
has(server, 'createExactContext(p + "/group/search", this::handleGroupSearch);', 'group search route registered');
has(server, 'private void handleDmSearch(HttpExchange ex)', 'DM search handler exists');
has(server, 'private void handleGroupSearch(HttpExchange ex)', 'group search handler exists');
has(server, '!config.searchEnabled || !config.directMessageEnabled', 'DM search obeys global/DM gates');
has(server, '!config.searchEnabled || !config.groupChatEnabled', 'group search obeys global/group gates');
has(server, 'host.directMessages().searchMessages(', 'DM handler uses retained-history store search');
has(server, 'host.groupChats().searchMessages(', 'group handler uses retained-history store search');

has(dmStore, 'public synchronized List<DirectMessageMessage> searchMessages(', 'DM store search API exists');
has(dmStore, 'if (!jsonlIsParticipant(tid, user)) return out;', 'JSONL DM search enforces participant scope');
has(dmStore, 'isParticipant(threadId, userUuid)', 'SQLite DM search enforces participant scope');
has(groupStore, 'public synchronized List<GroupMessage> searchMessages(', 'group store search API exists');
has(groupStore, '!isMember(user, id)) return out;', 'group search enforces active membership');

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
const cssFiles = wrappers.map(p => p.replace(/chat\.js$/, 'chat.css'));
for (const rel of wrappers) {
  const text = read(rel);
  const match = text.match(/const KWC_EMBEDDED_INNER_TEXT = ("(?:\\.|[^"\\])*");/s);
  ok(!!match, `${rel}: embedded inner payload found`);
  const decoded = JSON.parse(match[1]);
  ok(decoded === inner, `${rel}: embedded inner payload synchronized`);
}
for (const rel of cssFiles) {
  const text = read(rel);
  has(text, '.kwc-private-window-tools', `${rel}: private header tools CSS exists`);
  has(text, '.kwc-private-search-scope', `${rel}: private search scope CSS exists`);
}

console.log(`PRIVATE_SEARCH_UI_HARNESS_PASS assertions=${assertions} wrappers=${wrappers.length}`);
