// KWC 파일 안내 / KWC file guide
// RC33 live-browser feedback fixes: event lifecycle/i18n, auth retention, title drag/maximize, pin identity, and resize performance.
// Statically locks the source/generated contracts that were missed by the RC32 live acceptance pass.
'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(ok, msg) { assertions++; if (!ok) throw new Error(msg); }
function has(text, needle, msg) { check(text.includes(needle), msg + ` [missing: ${needle}]`); }
function notHas(text, needle, msg) { check(!text.includes(needle), msg + ` [unexpected: ${needle}]`); }

const games = read('frontend/inner/85-chat-games.js');
has(games, 'id="kwc-game-max" type="number" min="2" value="2"', 'lottery participant UI starts at 2 with default 2');
notHas(games, 'id="kwc-game-max" type="number" min="2" max="500"', 'lottery participant UI has no fixed product upper cap');
has(games, 'maxField.hidden = false', 'firstcome keeps participant capacity visible only as an informational mirror');
has(games, 'maxInput.disabled = firstCome', 'firstcome disables participant-capacity field');
has(games, 'if (firstCome) maxInput.value = String(Math.max(1, Number(winnerInput?.value || 1)))', 'firstcome participant display mirrors winner count');
has(games, 'maxParticipants:selectedType === "firstcome" ? ""', 'firstcome request omits stale participant capacity');
has(games, 'game.status === "completed" && game.type !== "firstcome"', 'completed firstcome does not expose a second close step');
has(games, 'markChatGameUnavailable', 'deleted events are tombstoned in the UI');
has(games, 'game.deletedNotice', 'deleted event gets a dedicated unavailable state');

const history = read('frontend/inner/50-public-history-virtual-scroll.js');
has(history, 'kwc-game-chat-link-unavailable', 'old event chat cards render a disabled unavailable state after tombstone');
has(history, 'chatGameDeletedLabel()', 'old event chat cards replace Open event with the localized deleted label');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(server, '"game_not_found".equals(selected.error()) ? 200 : 400', 'deleted event lookup uses logical game_not_found instead of HTTP 404');

const manager = read('kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java');
has(manager, 'if (type.equals("firstcome")) maxParticipants = winnerCount;', 'server makes firstcome winner count authoritative');
has(manager, 'else if (maxParticipants < 2)', 'lottery requires at least two participants');
notHas(manager, 'maxParticipants > 500', 'lottery has no fixed 500 participant cap');
has(manager, '"firstcome".equals(game.type) && game.winners.size() >= game.winnerCount', 'full firstcome auto-completes through shared lifecycle');

const rootAuth = read('frontend/inner/40-root-auth.js');
has(rootAuth, 'return status === 401;', 'only HTTP 401 is generic session expiry');
notHas(rootAuth, 'status === 403 ||', 'generic 403 no longer expires a session');
has(rootAuth, 'code === "not_logged_in"', 'explicit auth rejection still expires session');
const groupManagement = read('frontend/inner/140-group-management.js');
has(groupManagement, 'for (let attempt = 0; attempt < 3; attempt++)', 'stored-session verification retries transient failures');
has(groupManagement, 'state.authPendingToken = candidate;', 'transient verify failure keeps pending credential instead of deleting it');
notHas(groupManagement, 'status === 401 || status === 403', 'stored-session verification does not treat generic 403 as logout');

const pins = read('frontend/inner/80-pins-admin.js');
has(pins, 'function pinnedByDetailHtml(pin)', 'pinner uses structured identity renderer');
check((pins.match(/installSenderIdentityToggle\(el\);/g) || []).length >= 2, 'both public and group pinned rows install identity-mode refresh');
has(pins, 'const dragStartThresholdPx = 6;', 'modal drag waits for real pointer movement');
has(pins, 'if ((dx * dx) + (dy * dy) < dragStartThresholdPx * dragStartThresholdPx) return;', 'mouse jitter remains click/double-click instead of drag');

const dmUi = read('frontend/inner/120-dm-ui-typing.js');
has(dmUi, 'target.closest(".kwc-dm-head, .kwc-dm-title")', 'outer and inner DM/group titles both toggle maximize');
has(dmUi, 'button, input, select, textarea, a, [role=button]', 'interactive title controls are excluded from maximize');
has(dmUi, 'resizeFrame = requestAnimationFrame(flushGeometry)', 'private resize writes are frame-coalesced');
has(dmUi, 'update(geometry);', 'private resize reuses computed geometry without forced rect read');
const frame = read('frontend/inner/30-reply-identity-frame.js');
has(frame, 'resizeFrame = requestAnimationFrame(flushGeometry)', 'public standalone resize writes are frame-coalesced');
has(frame, 'update(geometry);', 'public standalone resize reuses computed geometry');
has(frame, 'width: state.minimized ? 124 : state.frameNormalWidth', 'child requests the title-plus-restore minimized public width');
for (const rel of [
  'kwc-adapter-bluemap/src/main/resources/web/chat.js',
  'kwc-adapter-dynmap/src/main/resources/dynmap/chat.js',
  'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.js',
  'kwc-adapter-overviewer/src/main/resources/overviewer/chat.js',
  'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.js',
  'kwc-adapter-squaremap/src/main/resources/squaremap/chat.js',
  'kwc-adapter-unmined/src/main/resources/unmined/chat.js'
]) {
  has(read(rel), 'const requestedWidth = frameMinimized ? 124 :', rel + ' parent frame reserves title-plus-restore minimized width');
}

const langRevision = read('kwc-core/src/main/java/dev/kokoto/webchat/BuiltinLanguageRevision.java');
for (const old of ['Hide this message?', '이 메시지를 숨길까요?', 'このメッセージを非表示にしますか？', '要隐藏这条消息吗？']) {
  has(langRevision, old, 'legacy bundled hide confirmation is migratable');
}
check((langRevision.match(/"game\.type", "Type"/g) || []).length >= 3, 'non-English legacy Type labels are migratable');
const langs = {
  'en-US.yml': ['confirmDelete: Delete this message?', 'type: Type'],
  'ko-KR.yml': ['confirmDelete: 이 메시지를 삭제할까요?', 'type: 방식'],
  'ja-JP.yml': ['confirmDelete: このメッセージを削除しますか？', 'type: 方式'],
  'zh-CN.yml': ['confirmDelete: 要删除这条消息吗？', 'type: 类型']
};
for (const [file, needles] of Object.entries(langs)) {
  const text = read('kwc-platform-bukkit/src/main/resources/lang/' + file);
  for (const needle of needles) has(text, needle, `${file} current localized wording`);
}

const css = read('kwc-standalone-frontend/src/main/resources/standalone/chat.css');
has(css, '.kwc-game-list-actions > .kwc-button {\n  width: 52px !important;', 'event Open/Delete buttons use the same fixed column width');
has(css, '.kwc-game-list-delete {\n  position: static !important;', 'event Delete no longer inherits message-delete positioning');
has(css, 'Do not add an audit-only pixel offset', 'DM audit back action intentionally inherits common title-row inset');
const rc33Min = css.lastIndexOf('5.3.0 RC33: final public minimized chrome contract');
const finalMin = css.lastIndexOf('5.3.0 RC38 window hotfix: minimized title + restore button contract.');
check(finalMin > rc33Min, 'current minimized title-plus-restore rule overrides the older RC33 + only rule');
has(css.slice(finalMin), '.kwc-actions-primary > :not(#kwc-min)', 'current minimized rule hides every primary action except restore');
has(css.slice(finalMin), 'width: 124px !important;', 'current minimized public root reserves title plus restore width');
has(css.slice(finalMin), '.kwc-header-identity', 'current minimized public root restores the title identity');

const migration = read('kwc-core/src/main/java/dev/kokoto/webchat/PortableConfigMigration.java');
has(migration, 'augmentRelayPeerPolicies(actual)', 'same-version existing configs physically augment peer policies');
for (const key of ['"public-chat"', '"event"', '"dm"', '"profile"']) has(migration, key, 'peer policy default includes ' + key);
has(migration, 'if (raw != null && !(raw instanceof Map<?,?>))', 'scalar send/receive shorthand remains intact');

console.log(`RC33_LIVE_FEEDBACK_PASS assertions=${assertions}`);
