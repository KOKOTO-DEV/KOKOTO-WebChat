#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function ok(cond, msg) { assertions++; if (!cond) { console.error(`FAIL ${msg}`); process.exitCode = 1; } }
function has(text, needle, msg) { ok(text.includes(needle), msg || `missing ${needle}`); }
function lacks(text, needle, msg) { ok(!text.includes(needle), msg || `unexpected ${needle}`); }

const configRef = read('distribution/config-reference-5.3.0.yml');
has(configRef, 'allow-user-self-message-delete:', 'config reference own-message delete toggle');
has(configRef, 'self-message-delete-window-minutes:', 'config reference own-message delete window');

const langs = ['en-US','ko-KR','ja-JP','zh-CN'];
for (const lang of langs) {
  const text = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  has(text, "    open: '", `${lang} dm.open/general open key present`);
  has(text, '  reply:\n', `${lang} reply section`);
  has(text, '    deleteOwnMessage:', `${lang} DM delete label`);
  has(text, '    hideRoom:', `${lang} room-list hide label`);
  has(text, '    gameCreated:', `${lang} command gameCreated`);
  has(text, '    gameJoined:', `${lang} command gameJoined`);
  has(text, '    gameClosed:', `${lang} command gameClosed`);
  has(text, '    gameFailed:', `${lang} command gameFailed`);
  has(text, '    gameListTitle:', `${lang} command game list title`);
  has(text, '    gameListEntry:', `${lang} command game list entry`);
  has(text, '    list:', `${lang} web event list`);
  has(text, '    backToList:', `${lang} web event back to list`);
  has(text, '    noEvents:', `${lang} web event noEvents`);
  has(text, '    remoteUnavailable:', `${lang} web remote unavailable`);
  has(text, '    error.game_relay_unavailable:', `${lang} web relay error`);
  lacks(text, 'error.game_already_open:', `${lang} obsolete singleton event error removed`);
}

// Exact DM/reply UI keys must exist in all languages.
for (const lang of langs) {
  const text = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  const dmBlock = text.match(/\n  dm:\n([\s\S]*?)(?=\n  [a-zA-Z0-9_-]+:\n)/);
  ok(dmBlock && /\n    open:/.test('\n' + dmBlock[1]), `${lang} exact dm.open`);
  const replyBlock = text.match(/\n  reply:\n([\s\S]*?)(?=\n  [a-zA-Z0-9_-]+:\n)/);
  ok(replyBlock && /\n    reply:/.test('\n' + replyBlock[1]), `${lang} exact reply.reply`);
}

const manager = read('kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java');
has(manager, 'dataDirectory.resolve("chat-games")', 'event directory persistence');
has(manager, 'DirectoryStream<Path>', 'multi-event directory scan');
has(manager, 'public synchronized List<Map<String,Object>> list', 'event list API');
has(manager, 'games.put(next.id, next)', 'multiple event insertion');
ok(!manager.includes('current.properties') && !manager.includes('legacyFile'), 'final event store has no intermediate single-event migration');
has(manager, 'StandardCopyOption', 'atomic event persistence handling');
lacks(manager, 'game_already_open', 'multi-event create has no singleton-open rejection');

const command = read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
has(command, 'action.equals("list")', 'game list command');
has(command, 'gameListTitle', 'translated game list title');
has(command, 'gameCreated', 'translated game create success');
has(command, 'gameJoined', 'translated game join success');
has(command, 'gameClosed', 'translated game close success');
has(command, 'gameFailed', 'translated game failure');
has(command, 'gameTypeFirstCome', 'translated game type output');
has(command, 'gameStatusCompleted', 'translated game status output');
has(command, 'raw.length() <= id.length()', 'event ID prefix boundary guard');
has(command, 'regionMatches(true, 0, raw, 0, raw.length())', 'event ID prefix exact requested length');

const web = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(web, 'out.put("remote", false)', 'local event response explicit remote=false');
has(web, 'out.put("remote", true)', 'relayed event response explicit remote=true');
has(web, 'out.put("games"', 'event list returned by API');
has(web, 'targetServerId', 'remote target server parameter');
has(web, 'relay.requestChatGame(targetServerId', 'targeted remote event query');
has(web, '"eventId", eventId', 'event announcement carries event ID');
has(web, '"serverId", serverId', 'event announcement carries origin server ID');

const relay = read('kwc-core/src/main/java/dev/kokoto/webchat/ServerRelay.java');
has(relay, 'PROTOCOL_REVISION = "2.2"', 'Relay revision 2.2');
has(relay, 'CAPABILITIES_CSV = "public,dm,read,delete,reaction,reaction-authority,typing,game,profile"', 'Relay game/profile capabilities advertised');
ok(/\\"typing\\",\\"game\\"/.test(relay), 'handshake response advertises game capability');
has(relay, 'case "game-request"', 'game-request receive dispatch');
has(relay, 'sendChatGameRequestToPeers', 'targeted game request routing');
has(relay, 'uniqueForwardingNextHop', 'deterministic targeted forwarding');

const gameUi = read('frontend/inner/85-chat-games.js');
has(gameUi, 'const remote = data.remote === true;', 'frontend trusts explicit remote flag');
has(gameUi, 'data-game-open-id', 'event list selectable rows');
has(gameUi, 'targetServerId=', 'remote event server query');
has(gameUi, 'gameId=', 'specific event query');
has(gameUi, 'Back to event list', 'event detail list navigation');
lacks(gameUi, 'wrap.__kwcGameTargetServerId = ""; wrap.__kwcGameId = "";', 'back-to-list preserves remote origin server context');
has(gameUi, '/^\\d+$/.test(String(vars.winners || ""))', 'announcement fallback distinguishes winner count from winner names');

const historyUi = read('frontend/inner/50-public-history-virtual-scroll.js');
has(historyUi, 'chatGameMessageTarget(msg)', 'announcement click resolves event target');
has(historyUi, 'target.serverId', 'announcement preserves origin server');
has(historyUi, 'target.gameId', 'announcement preserves event ID');

const groupMgmt = read('frontend/inner/140-group-management.js');
has(groupMgmt, 't("group.hideRoom", "Hide from list")', 'room-list hide has dedicated label');
lacks(groupMgmt, 't("button.hide", "Hide")', 'group room hide no longer uses generic message hide label');

const dmCore = read('frontend/inner/110-auth-dm-core.js');
has(dmCore, 'deleteOwnMessage', 'DM message action is delete');
lacks(dmCore, 't(\"button.hide\"', 'no runtime DM hide message action');

const rootWindowUi = read('frontend/inner/30-reply-identity-frame.js');
has(rootWindowUi, 'standaloneMobileWindowLocked()', 'mobile standalone lock helper');
has(rootWindowUi, 'standaloneMobileViewportRect()', 'mobile standalone visual viewport geometry');
has(rootWindowUi, 'window.visualViewport', 'mobile standalone follows visualViewport');
has(rootWindowUi, 'setTimeout(sync, 80)', 'mobile standalone orientation settle pass 80ms');
has(rootWindowUi, 'setTimeout(sync, 260)', 'mobile standalone orientation settle pass 260ms');
has(rootWindowUi, 'state.isStandalone && !standaloneMobileWindowLocked()', 'mobile standalone ignores saved desktop position');
has(rootWindowUi, 'const hidden = standaloneMobileWindowLocked() || state.minimized', 'mobile standalone resize zones are disabled');
has(rootWindowUi, 'if (standaloneMobileWindowLocked()) {\n        forceStandaloneMobileMaximized(root);', 'normal size sync cannot unmaximize mobile standalone');

const privateUi = read('frontend/inner/115-private-multiwindow.js');
has(privateUi, 'orientationchange', 'private windows react to orientation change');
has(privateUi, 'window.visualViewport', 'private windows use visualViewport');
has(privateUi, 'setTimeout(sync, 80)', 'private window viewport settle pass 80ms');
has(privateUi, 'setTimeout(sync, 260)', 'private window viewport settle pass 260ms');
has(privateUi, 'collapsePrivateConversationWindowsToSinglePane("dm")', 'DM multiwindow collapses when viewport becomes unsupported');
has(privateUi, 'collapsePrivateConversationWindowsToSinglePane("group")', 'group multiwindow collapses when viewport becomes unsupported');

const modalUi = read('frontend/inner/80-pins-admin.js');
has(modalUi, 'visibleViewportRect()', 'general modal visible viewport helper');
has(modalUi, 'reflowAllKwcModalsToViewport()', 'general modal reflow helper');
has(modalUi, 'window.visualViewport', 'general modals use visualViewport');

const groupUi = read('frontend/inner/130-group-rooms-archive.js');
has(groupUi, 'kwc-group-title-wrapped', 'group header responsive class');
has(groupUi, 'ResizeObserver', 'group header observes resizes');
has(groupUi, 'protectedNameWidth', 'group header protects title width before wrapping');
has(groupUi, 'groupHeaderActionsNaturalWidth', 'group header measures action width');

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
const cssBuffers = cssPaths.map(rel => fs.readFileSync(path.join(root, rel)));
const cssHash = b => crypto.createHash('sha256').update(b).digest('hex');
const firstCss = cssHash(cssBuffers[0]);
for (let i=0;i<cssBuffers.length;i++) ok(cssHash(cssBuffers[i]) === firstCss, `CSS copy ${i+1}/8 byte-identical`);
const css = cssBuffers[0].toString('utf8');
has(css, '.kwc-group-title.kwc-group-title-wrapped', 'group header two-row CSS');
has(css, 'grid-template-areas:', 'group wrapped grid areas');
has(css, '"main"\n    "actions"', 'group identity/actions rows');
has(css, '#kwc-root.kwc-header-wrapped:not(.kwc-minimized) .kwc-action-cluster-chat > .kwc-button', 'public wrapped chat buttons expanded');
has(css, 'flex: 1 1 46px !important;', 'public wrapped buttons fill more width');
has(css, '.kwc-mobile-window-locked:not(.kwc-minimized)', 'mobile standalone lock CSS');
has(css, 'height: var(--kwc-standalone-height, 100dvh) !important;', 'mobile standalone uses dynamic viewport fallback');
has(css, 'cursor: default !important;', 'mobile standalone header is not presented as draggable');

const wrapperPaths = [
 'kwc-adapter-bluemap/src/main/resources/web/chat.js',
 'kwc-adapter-dynmap/src/main/resources/dynmap/chat.js',
 'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.js',
 'kwc-adapter-overviewer/src/main/resources/overviewer/chat.js',
 'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.js',
 'kwc-adapter-squaremap/src/main/resources/squaremap/chat.js',
 'kwc-adapter-unmined/src/main/resources/unmined/chat.js',
 'kwc-standalone-frontend/src/main/resources/standalone/chat.js'
];
for (const rel of wrapperPaths) {
  const text = read(rel);
  has(text, 'kwc-group-title-wrapped', `${rel} contains group responsive bundle`);
  has(text, 'const remote = data.remote === true;', `${rel} contains explicit remote event UI`);
  has(text, 'setTimeout(sync, 260)', `${rel} contains orientation settle pass`);
}

for (const rel of ['docs/en/SERVER_RELAY.md','docs/ko/SERVER_RELAY.md','docs/ja/SERVER_RELAY.md','docs/zh-CN/SERVER_RELAY.md','wiki/Server-Relay.md']) {
  const text = read(rel);
  has(text, '2.2', `${rel} current Relay revision`);
  has(text, 'game', `${rel} game capability documented`);
  has(text, 'game-request', `${rel} targeted event request documented`);
  lacks(text, 'game`, and `game', `${rel} no duplicate game capability`);
}

if (!process.exitCode) console.log(`RC26_EVENT_LAYOUT_PASS assertions=${assertions} wrappers=${wrapperPaths.length} css=${cssPaths.length}`);
