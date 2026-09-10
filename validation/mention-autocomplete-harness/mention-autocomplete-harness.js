// KWC 파일 안내 / KWC file guide
// 웹 public/DM/group composer의 @mention 자동완성 계약과 서버 후보 privacy/scope 경계를 정적·순수 로직으로 검증한다.
// Validates the web public/DM/group @mention autocomplete contract plus server-side candidate privacy/scope boundaries using static and pure-logic checks.
// 자동완성 UI가 단순 표시 기능으로 끝나지 않고 실제 username 삽입, 키보드 선택, wrapper/CSS 동기화, Offline privacy를 함께 유지하는지 확인한다.
// Ensures autocomplete preserves real-username insertion, keyboard selection, wrapper/CSS synchronization, and Offline privacy instead of only checking UI presence.

'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..', '..');
let assertions = 0;
function check(value, name) { assertions++; if (!value) throw new Error('FAIL: ' + name); }
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function javaMethod(source, name) {
  const token = ` ${name}(`;
  const hit = source.indexOf(token);
  if (hit < 0) return '';
  const start = source.lastIndexOf('\n', hit) + 1;
  const next = source.indexOf('\n    private ', hit + token.length);
  const nextPublic = source.indexOf('\n    public ', hit + token.length);
  const candidates = [next, nextPublic].filter(v => v > hit);
  const end = candidates.length ? Math.min(...candidates) : source.length;
  return source.slice(start, end);
}
function fn(source, name) {
  const token = `function ${name}(`;
  const start = source.indexOf(token);
  if (start < 0) return '';
  const asyncStart = source.lastIndexOf('async ', start);
  const actualStart = asyncStart >= 0 && start - asyncStart < 8 ? asyncStart : start;
  const next = source.indexOf('\n  function ', start + token.length);
  const nextAsync = source.indexOf('\n  async function ', start + token.length);
  const candidates = [next, nextAsync].filter(v => v > start);
  const end = candidates.length ? Math.min(...candidates) : source.length;
  return source.slice(actualStart, end);
}

const compose = read('frontend/inner/70-emoji-upload-compose.js');
const rootAuth = read('frontend/inner/40-root-auth.js');
const privateUi = read('frontend/inner/140-group-management.js');
const dmTyping = read('frontend/inner/120-dm-ui-typing.js');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');

check(server.includes('createExactContext(p + "/mentions", this::handleMentionCandidates)'), 'mentions API route registered');
const serverFn = javaMethod(server, 'handleMentionCandidates');
check(serverFn.includes('"group".equals(scope)'), 'group scope handled server-side');
check(serverFn.includes('"dm".equals(scope)'), 'DM scope handled server-side');
check(serverFn.includes('scope = "public"'), 'public scope is default');
check(serverFn.includes('host.groupChats().listMembers(viewerUuid, roomId)'), 'group candidates are room members only');
check(serverFn.includes('storage.findKnownPlayerByUuid(targetUuid)'), 'DM candidate resolves exact peer identity');
check(serverFn.includes('storage.listKnownPlayers(query'), 'public candidates use known local/relayed identities');
check(serverFn.includes('!uuid.equals(viewerUuid) && "offline".equals(presenceStatus(uuid))'), 'manual Offline users are hidden from other viewers');
check(serverFn.includes('out.put("mentionText", mentionText)'), 'server emits stable mention insertion text');
check(serverFn.includes('String mentionText = username.isBlank() ? displayName : username'), 'real username is preferred over display name');
check(serverFn.includes('out.put("serverId", remote.serverId)'), 'remote candidate keeps relay server identity');
check(serverFn.includes('presenceSnapshot(viewerUuid, player.uuid)'), 'candidate presence uses viewer-aware privacy policy');
check(serverFn.includes('int limit = boundedInt(q.get("limit"), 12, 1, 20)'), 'candidate response is bounded');

const trigger = fn(compose, 'mentionTriggerAtCaret');
check(trigger.includes('@([^\\s@]*)$'), 'trigger only tracks one @ token at caret');
check(trigger.includes('start !== end'), 'selection does not activate autocomplete');
check(trigger.includes('query.length > 80'), 'mention query length is bounded');
const scope = fn(compose, 'mentionScopeForInput');
check(scope.includes('kwc-dm-input') && scope.includes('return "dm"'), 'DM composer maps to DM scope');
check(scope.includes('kwc-group-input') && scope.includes('return "group"'), 'group composer maps to group scope');
check(scope.includes('return "public"'), 'public composer maps to public scope');
const fetch = fn(compose, 'fetchMentionAutocompleteCandidates');
check(fetch.includes('/mentions?scope='), 'frontend calls dedicated mentions API');
check(fetch.includes('&roomId=') && fetch.includes('state.groupActiveRoomId'), 'group request carries active room');
check(fetch.includes('&targetUuid=') && fetch.includes('currentDirectMentionTargetUuid()'), 'DM request carries exact peer');
check(fetch.includes('mentionLocalPublicCandidates(query)'), 'public visible-history fallback exists for guest/API failure');
check(fetch.includes('requestSeq !== mentionAutocompleteRequestSeq'), 'stale async responses cannot replace newer query');

const render = fn(compose, 'renderMentionAutocomplete');
check(render.includes('role", "listbox"'), 'popup exposes listbox semantics');
check(render.includes('role="option"'), 'candidate rows expose option semantics');
check(render.includes('data-mention-index'), 'candidate rows are selectable');
check(render.includes('directMessageLabelHtml(display)'), 'display-name formatting uses existing identity renderer');
check(render.includes('mentionCandidateMetaHtml(item)'), 'candidate metadata includes real-name/presence context');
const select = fn(compose, 'selectMentionAutocomplete');
check(select.includes('token = "@" + mentionText + " "'), 'selection inserts @username plus trailing space');
check(select.includes('trigger.start !== current.trigger.start'), 'selection rejects stale caret trigger');
check(select.includes('input.selectionStart = input.selectionEnd = caret'), 'caret moves after inserted mention');
check(select.includes('input.dispatchEvent(new Event("input"'), 'selection reuses normal composer input pipeline');
const keys = fn(compose, 'handleMentionAutocompleteKeydown');
check(keys.includes('ArrowDown') && keys.includes('ArrowUp'), 'arrow-key selection supported');
check(keys.includes('event.key === "Tab" || event.key === "Enter"'), 'Tab/Enter selection supported');
check(keys.includes('event.key === "Escape"'), 'Escape closes autocomplete');
check(keys.includes('event.isComposing || event.keyCode === 229'), 'IME composition is not intercepted');
check(compose.includes('function setMentionAutocompleteSelected(index, scroll = true)'), 'arrow navigation updates selection without rebuilding the popup');
check(keys.includes('setMentionAutocompleteSelected(next, true)'), 'arrow keys use stable in-place active-row update');

check(rootAuth.includes('handleMentionAutocompleteKeydown(e, messageInput)'), 'public composer keydown integrates mention picker before send');
check(rootAuth.includes('scheduleMentionAutocomplete(messageInput)'), 'public input changes refresh mention picker');
check(privateUi.includes('handleMentionAutocompleteKeydown(e, input)'), 'group composer keydown integrates mention picker');
check(privateUi.includes('scheduleMentionAutocomplete(input)'), 'group input changes refresh mention picker');
check(privateUi.includes('handleMentionAutocompleteKeydown(e, dmInput)'), 'DM composer keydown integrates mention picker');
check(privateUi.includes('scheduleMentionAutocomplete(dmInput)'), 'DM input changes refresh mention picker');
check(dmTyping.includes('hideMentionAutocomplete();') && privateUi.includes('async function sendGroupChatMessage()'), 'private sends close autocomplete');
check(compose.includes('async function sendMessage()') && compose.includes('hideMentionAutocomplete();'), 'public send closes autocomplete');

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
const canonicalCss = read(cssPaths[7]);
for (const rel of cssPaths) {
  const css = read(rel);
  check(css === canonicalCss, `shared chat CSS synchronized: ${rel}`);
  check(css.includes('.kwc-mention-autocomplete'), `mention popup CSS present: ${rel}`);
}
check(canonicalCss.includes('max-height: 232px'), 'desktop mention list height is bounded');
check(canonicalCss.includes('@media (max-width: 560px)'), 'mobile touch layout has dedicated sizing');
check(canonicalCss.includes('display: flex') && canonicalCss.includes('.kwc-mention-option.kwc-active'), 'mention popup has explicit layout and active-row styling');
const wrapperJsPaths = cssPaths.map(rel => rel.replace(/chat\.css$/, 'chat.js'));
for (const rel of wrapperJsPaths) {
  const js = read(rel);
  const match = js.match(/^\s*const KWC_EMBEDDED_CSS_TEXT = (.*);$/m);
  check(!!match, `embedded CSS payload exists: ${rel}`);
  const embeddedCss = match ? JSON.parse(match[1]) : '';
  check(embeddedCss === canonicalCss, `embedded CSS matches shared chat.css: ${rel}`);
  check(embeddedCss.includes('.kwc-mention-autocomplete'), `embedded mention CSS present: ${rel}`);
}

const lang = {
  'en-US.yml': 'Mention suggestions',
  'ko-KR.yml': '멘션 자동완성',
  'ja-JP.yml': 'メンション候補',
  'zh-CN.yml': '提及候选'
};
for (const [file, label] of Object.entries(lang)) {
  const text = read('kwc-platform-bukkit/src/main/resources/lang/' + file);
  check(text.includes('  mention:\n') && text.includes('    suggestions: ' + label), `mention aria label localized: ${file}`);
}

// Pure trigger behavior mirror: protect emails/normal @ inside a word while allowing start/whitespace/punctuation triggers.
function parseTrigger(value, caret = value.length) {
  const before = String(value).slice(0, caret);
  const match = /(^|[\s([{<'".,!?;:])@([^\s@]*)$/u.exec(before);
  if (!match) return null;
  return {start: before.lastIndexOf('@'), query: String(match[2] || '')};
}
check(parseTrigger('@')?.query === '', 'bare @ opens candidate list');
check(parseTrigger('hello @ko')?.query === 'ko', 'query after whitespace is detected');
check(parseTrigger('(@name')?.query === 'name', 'query after punctuation is detected');
check(parseTrigger('mail@example.com') === null, 'email-like text does not trigger');
check(parseTrigger('word@name') === null, '@ inside ordinary token does not trigger');
check(parseTrigger('hello @one two') === null, 'whitespace after query closes trigger');

console.log(`MENTION_AUTOCOMPLETE_HARNESS_PASS assertions=${assertions}`);
