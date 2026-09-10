#!/usr/bin/env node
'use strict';
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function ok(v, m) { assertions++; if (!v) { console.error('FAIL ' + m); process.exitCode = 1; } }
function has(t, n, m) { ok(t.includes(n), m || `missing ${n}`); }
function lacks(t, n, m) { ok(!t.includes(n), m || `unexpected ${n}`); }

const admin = read('frontend/inner/80-pins-admin.js');
has(admin, 'makeModalDraggable(wrap, "kwc.localAdminModalPos")', 'admin pages use shared modal drag implementation');
lacks(admin, 'installAdminModalDrag(wrap)', 'duplicate admin-specific drag installer removed');
has(admin, 'modal.querySelector(":scope > .kwc-modal-head")', 'whole modal header is drag handle');
has(admin, 'button, input, select, textarea, a, [role=button]', 'header controls do not initiate drag');
has(admin, 'handle.setPointerCapture', 'drag captures active pointer');
has(admin, 'handle.releasePointerCapture', 'drag releases pointer capture');
has(admin, 'lostpointercapture', 'lost capture terminates drag');
has(admin, 'window.addEventListener("blur", end, true)', 'window blur terminates drag');
has(admin, 'visibilitychange', 'tab visibility loss terminates drag');
has(admin, '(event.buttons & 1) === 0', 'stale mouse move terminates sticky drag');
has(admin, 'if (wrap.__kwcDragCleanup) wrap.__kwcDragCleanup(); wrap.remove()', 'admin close removes global drag listeners');

const prefs = read('frontend/inner/100-preferences-search.js');
has(prefs, 'makeModalDraggable(wrap, "kwc.localUserPrefsModalPos")', 'user settings remains draggable');
has(prefs, 'if (wrap.__kwcDragCleanup) wrap.__kwcDragCleanup(); wrap.remove()', 'user settings close removes global drag listeners');

const gameUi = read('frontend/inner/85-chat-games.js');
has(gameUi, 'data-game-delete-id=', 'event history rows have explicit delete action');
has(gameUi, 'confirmPlain(t("game.confirmDeleteEvent"', 'event delete reuses confirmation flow');
has(gameUi, 'chatGameAction("delete", {gameId})', 'event delete calls server delete action');
has(gameUi, 'await refreshChatGameModal(wrap)', 'event list refreshes immediately after delete');
has(gameUi, 'kwc-game-list-delete', 'event delete has dedicated always-visible list action');
lacks(gameUi, 'kwc-private-message-delete kwc-game-list-delete', 'event delete no longer inherits hover-only message delete hiding');

const manager = read('kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java');
has(manager, 'public synchronized Result delete(String gameId)', 'event manager has permanent delete operation');
has(manager, 'games.remove(id)', 'event is removed from retained list');
has(manager, 'Files.deleteIfExists(directory.resolve(game.id + ".properties"))', 'event persistence record is deleted');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(server, '"delete".equals(action)', 'web event API exposes delete action');
has(server, 'deleteChatGame(gameId)', 'web API routes to event deletion');
const cmd = read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
has(cmd, 'delete <eventId>', 'game command help documents delete');
has(cmd, 'action.equals("delete") || action.equals("remove")', 'game command supports explicit deletion');

for (const lang of ['en-US','ko-KR','ja-JP','zh-CN']) {
  const text = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  has(text, 'gameDeleted:', `${lang} command event deletion translation`);
  has(text, 'confirmDeleteEvent:', `${lang} UI event deletion confirmation translation`);
  has(text, 'error.permission_denied:', `${lang} event permission error translation`);
}

const rootAuth = read('frontend/inner/40-root-auth.js');
has(rootAuth, 'const accountButtonName = String(state.username', 'account button keeps complete username for tooltip');
has(rootAuth, '`${accountButtonName} · ${t("preferences.title"', 'account tooltip exposes full username');

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
const cssTexts = cssPaths.map(read);
const hashes = cssTexts.map(t => crypto.createHash('sha256').update(t).digest('hex'));
ok(new Set(hashes).size === 1, 'all eight CSS copies byte-identical');
const css = cssTexts[0];
has(css, '.kwc-modal-backdrop.kwc-user-prefs-backdrop {\n  background: rgba(0,0,0,0.56) !important;', 'blocking settings/admin backdrop is visibly dark');
has(css, '> .kwc-modal > .kwc-modal-head,', 'modal header is styled as drag surface');
has(css, '.kwc-status.kwc-status-admin-action {', 'wrapped header keeps dedicated administrator action sizing');
has(css, 'height: 26px !important;', 'wrapped administrator action grows modestly');
has(css, 'flex: 1 1 36px !important;', 'wrapped row-2 chat icons use same nominal 36px size');
has(css, 'min-width: 26px !important;', 'left chat icons may shrink before username is compressed');
has(css, '#kwc-root #kwc-login.kwc-login-user {', 'signed-in account button has dedicated overflow policy');
has(css, 'text-overflow: ellipsis !important;', 'long username ellipsizes');
has(css, 'width: min(calc(16ch + 22px), 44vw) !important;', 'wrapped username reserves approximately 16-character content width');
has(css, '.kwc-group-title.kwc-group-title-wrapped > .kwc-group-actions', 'group action row has wrapped policy');
has(css, 'overflow-x: auto !important;', 'narrow wrapped group actions scroll instead of overlap');
has(css, '.kwc-game-list-body {\n  max-height: min(46vh, 340px) !important;', 'event history list has bounded scrollable space');
has(css, '.kwc-game-list-delete {', 'event delete action has compact list styling');

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
  has(text, 'data-game-delete-id', `${rel}: bundled event deletion`);
  has(text, 'event.buttons & 1', `${rel}: bundled sticky-drag guard`);
  has(text, 'accountButtonName', `${rel}: bundled account username protection`);
}

if (!process.exitCode) console.log(`RC28_MODAL_EVENT_HEADER_PASS assertions=${assertions} wrappers=${wrapperPaths.length} css=${cssPaths.length}`);
