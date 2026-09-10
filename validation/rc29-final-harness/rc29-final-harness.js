'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || process.cwd());
let assertions = 0;
function read(rel){ return fs.readFileSync(path.join(root, rel), 'utf8'); }
function ok(v,m){ assertions++; if(!v){ console.error('FAIL',m); process.exitCode=1; } }
function has(t,n,m){ ok(t.includes(n),m); }
function lacks(t,n,m){ ok(!t.includes(n),m); }

const dm = read('frontend/inner/110-auth-dm-core.js');
has(dm, 'kwc-private-title-row-audit', 'DM audit row gets dedicated no-line class');
has(dm, 'kwc-dm-title-audit-layout', 'DM audit title gets responsive layout class');
has(dm, 'kwc-private-audit-badge', 'DM audit badge is separate from identity');
has(dm, 'kwc-dm-audit-identity', 'DM audit identity has independent ellipsis/wrap container');
has(dm, 'makeModalDraggable(wrap, "")', 'user profile explicitly installs shared drag handler');
has(dm, 'profileTargetUuid', 'profile block identity distinguishes remote composite identity');
has(dm, 'data-presence-dm-remote', 'profile presence DM action preserves remote flag');
has(dm, 'data-presence-dm-server-id', 'profile presence DM action preserves origin server id');

const group = read('frontend/inner/130-group-rooms-archive.js');
has(group, 'kwc-group-audit-badge', 'group header has audit badge');
has(group, 'admin.groupAuditView', 'group audit badge is localized');
lacks(group, 'kwc-group-title-name">🛡', 'audit shield no longer substitutes for an explicit group audit label');

const games = read('frontend/inner/85-chat-games.js');
has(games, 'kwc-game-list-actions', 'event row has dedicated action area');
has(games, 'kwc-game-list-delete', 'event row has dedicated visible delete action');
lacks(games, 'kwc-private-message-delete kwc-game-list-delete', 'event delete does not inherit hover-only message delete hiding');
has(games, 't("button.delete", "Delete")', 'event Delete text uses shared button i18n');

const relay = read('kwc-core/src/main/java/dev/kokoto/webchat/ServerRelay.java');
has(relay, 'PROTOCOL_REVISION = "2.2"', 'Relay revision 2.2');
has(relay, 'typing,game,profile', 'Relay profile capability advertised');
has(relay, 'case "profile-request"', 'profile-request receive dispatch exists');
has(relay, 'requestUserProfile', 'targeted profile request API exists');
has(relay, 'sendProfileRequestToPeers', 'targeted profile routing exists');
const host = read('kwc-core/src/main/java/dev/kokoto/webchat/RelayHost.java');
has(host, 'handleProfileRelayRequest', 'RelayHost exposes trusted profile lookup hook');
const web = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(web, 'handleRelayedProfileRequest', 'origin server creates relayed public profile snapshot');
has(web, 'requestRemoteProfileSnapshot', 'presence endpoint requests remote profile from origin server');
has(web, 'publicProfileCardForRelay', 'relay profile uses explicit public-profile projection');
has(web, 'remote viewers never receive the self-view Invisible exception', 'remote presence preserves Invisible privacy rule');
has(web, 'out.put("restrictions", profileAccount == null', 'remote response does not expose real restriction state');

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
const css = cssTexts[0];
for (let i=1;i<cssTexts.length;i++) ok(cssTexts[i]===css, `${cssPaths[i]} CSS matches canonical copy`);
has(css, '.kwc-private-title-row.kwc-private-title-row-audit', 'DM audit row CSS exists');
has(css, 'border-bottom: 0 !important;', 'DM audit row removes unintended separator');
has(css, '.kwc-dm-title-audit-layout', 'DM audit title responsive CSS exists');
has(css, 'flex: 1 1 168px;', 'DM identity wraps below audit badge before aggressive ellipsis');
has(css, '.kwc-group-audit-badge', 'group audit header badge CSS exists');
has(css, '.kwc-status.kwc-status-admin-action', 'administrator indicator has dedicated sizing');
has(css, 'height: 26px !important;', 'wrapped administrator indicator is modestly enlarged');
has(css, '#kwc-root.kwc-header-wrapped:not(.kwc-minimized) #kwc-pip', 'PIP gets explicit original-size wrapped override');
has(css, 'font-size: 12px !important;', 'PIP wrapped control restores normal font size');
has(css, '#kwc-root:not(.kwc-minimized) #kwc-notification-badge', 'notification badge overhang policy exists');
has(css, 'transform: translate(45%, -45%) !important;', 'unread badge is positioned above/right of button');
has(css, 'z-index: 30 !important;', 'unread badge is above header button');
has(css, 'min-width: 17px !important;', 'unread badge preserves numeric width');
has(css, '.kwc-game-list-actions', 'event action alignment CSS exists');
has(css, '.kwc-game-list-actions > .kwc-button {\n  width: 52px !important;', 'Open/Delete actions share fixed width');

for (const lang of ['en-US','ko-KR','ja-JP','zh-CN']) {
 const t = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
 has(t, 'groupAuditView:', `${lang} group audit header translation exists`);
 has(t, '    delete:', `${lang} shared Delete button translation exists`);
}

for (const rel of [
 'kwc-adapter-bluemap/src/main/resources/web/chat.js','kwc-adapter-dynmap/src/main/resources/dynmap/chat.js',
 'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.js','kwc-adapter-overviewer/src/main/resources/overviewer/chat.js',
 'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.js','kwc-adapter-squaremap/src/main/resources/squaremap/chat.js',
 'kwc-adapter-unmined/src/main/resources/unmined/chat.js','kwc-standalone-frontend/src/main/resources/standalone/chat.js']) {
 const t=read(rel);
 has(t, 'kwc-private-title-row-audit', `${rel} bundled DM audit layout`);
 has(t, 'kwc-group-audit-badge', `${rel} bundled group audit badge`);
}

if(!process.exitCode) console.log(`RC29_FINAL_PASS assertions=${assertions} css=${cssPaths.length}`);
