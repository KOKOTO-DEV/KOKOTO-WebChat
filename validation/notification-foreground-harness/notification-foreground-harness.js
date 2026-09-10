// KWC notification foreground regression checks.
// Ensures mobile visibility does not depend on document.hasFocus(), desktop
// embedded notification decisions use host attention, and service-worker Push
// suppresses public notifications while a visible KWC client is present.
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '../..');
let passed = 0;
function read(rel){ return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(v, name){ if(!v) throw new Error('FAIL: ' + name); passed++; }

const notifications = read('frontend/inner/90-notifications.js');
const frame = read('frontend/inner/30-reply-identity-frame.js');
const group = read('frontend/inner/140-group-management.js');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
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

check(notifications.includes('function notificationMobileVisibilitySemantics()'), 'mobile notification visibility helper exists');
check(notifications.includes('if (notificationMobileVisibilitySemantics()) return true;'), 'mobile foreground trusts visibility before hasFocus');
check(notifications.includes('if (!mobileVisibility && state.hostPageFocused === false) return false;'), 'desktop still honors host focus');
check(notifications.includes('The iframe document itself does not need focus'), 'desktop iframe focus is not required when host page is focused');
check(!notifications.includes('if (!notificationDocumentForeground(document)) return false;'), 'no stale iframe-focus gate remains');
check(notifications.includes('return !notificationHostActivelyViewed();'), 'browser Notification API uses host foreground state');
check(notifications.includes('if (options.publicChat === true) return true;'), 'visible page can suppress public push query');
check(frame.includes('publicChat:data.publicChat === true'), 'parent iframe bridge forwards public push suppression query');
check(group.includes('publicChat:data.publicChat === true'), 'direct service-worker listener handles public push suppression query');
check(server.includes("var out={dmThreadId:'',groupRoomId:'',publicChat:false};"), 'service worker tracks public target');
check(server.includes("type!==\'test\'"), 'service-worker test push remains unsuppressed');
check(server.includes('publicChat:target.publicChat===true'), 'service worker queries clients for public foreground state');
check(server.includes("target.publicChat!==true"), 'service worker no longer rejects all non-private pushes before client query');

for (const rel of wrappers) {
  const text = read(rel);
  check(text.includes('function parentMobileNotificationVisibilitySemantics()'), rel + ' has mobile visibility helper');
  check(text.includes('function parentNotificationHostForeground()'), rel + ' has unified parent foreground helper');
  check(text.includes('const publicChat = data.publicChat === true;'), rel + ' receives public suppression query');
  check(text.includes('const visible = parentNotificationHostForeground() && !frameMinimized;'), rel + ' service-worker bridge uses unified foreground');
  check(text.includes('publicChat}, "*", [channel.port2])'), rel + ' forwards publicChat to child');
  check(text.includes('const visiblePublicOrGeneric = foreground && !dmThreadId && !groupRoomId;'), rel + ' browser notification parent fallback suppresses visible public notifications');
  check(!text.includes('const visible = document.visibilityState === "visible" && document.hasFocus() && !frameMinimized;'), rel + ' no stale mobile-hostile service-worker focus check');
  check(text.includes('let parentNotificationDefaults =') && text.includes('parentNotificationDefaults[name] = value === true'), rel + ' account notification checkbox updates parent cached state immediately');
  check(text.includes('parentNotificationDefaults = Object.assign({}, status.notificationOptions)'), rel + ' server notification refresh replaces parent cached defaults');
}

console.log('NOTIFICATION_FOREGROUND_PASS ' + passed);
