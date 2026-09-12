'use strict';
const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(ok, msg) { assertions++; if (!ok) throw new Error(msg); }
function has(text, needle, msg) { check(text.includes(needle), msg + ` [missing: ${needle}]`); }

const frame = read('frontend/inner/30-reply-identity-frame.js');
has(frame, 'button, input, select, textarea, a, [role=\\"button\\"]', 'public header drag excludes role=button touch targets');
const auth = read('frontend/inner/40-root-auth.js');
has(auth, 'status.setAttribute("role", "button")', 'admin/user-count status remains an explicit interactive control');
has(auth, 'status.onclick = event =>', 'admin/user-count status has click activation');

const manager = read('kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java');
has(manager, 'boolean autoEndOnCapacity;', 'capacity automatic-end policy persisted');
has(manager, 'int autoEndResponseCount;', 'poll response threshold policy persisted');
has(manager, 'long autoEndAt;', 'absolute automatic-end timestamp persisted');
has(manager, 'public synchronized List<Result> finishDue(long now)', 'time lifecycle has a scheduler-facing due method');
has(manager, '"lottery".equals(game.type) && game.autoEndOnCapacity', 'lottery can auto-draw at capacity');
has(manager, '"poll".equals(game.type) && game.autoEndResponseCount > 0', 'poll can auto-end at response threshold');
has(manager, '"recruitment".equals(game.type) && game.autoEndOnCapacity && recruitmentCapacityFilled(game)', 'recruitment can auto-end when all role slots fill');

const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(server, 'startChatGameLifecycle();', 'server starts event lifecycle scheduler');
has(server, 'chatGames.finishDue(System.currentTimeMillis())', 'scheduler evaluates absolute end times');
has(server, 'publishChatGameUpdate("auto-finish", result)', 'automatic time completion is pushed to web clients');
has(server, 'announceChatGameResult(result.game())', 'automatic time completion announces results');

const ui = read('frontend/inner/85-chat-games.js');
for (const needle of ['id="kwc-game-auto-capacity"','id="kwc-game-auto-response-enabled"','id="kwc-game-auto-response-count"','id="kwc-game-auto-end-at"']) {
  has(ui, needle, 'web event create form exposes ' + needle);
}
has(ui, 'autoEndOnCapacity:selectedType === "firstcome" ? true', 'firstcome auto-end remains mandatory');
has(ui, 'autoEndResponseCount:selectedType === "poll"', 'poll sends response threshold');
has(ui, 'autoEndAt:chatGameAutoEndTimestamp', 'web sends absolute automatic-end time');
has(ui, 'chatGameAutoEndText(game)', 'event detail displays automatic-end policy');

const langs = ['en-US.yml','ko-KR.yml','ja-JP.yml','zh-CN.yml'];
for (const name of langs) {
  const lang = read(`kwc-platform-bukkit/src/main/resources/lang/${name}`);
  for (const key of ['autoEnd:', 'autoEndWhenFull:', 'autoEndWhenRolesFull:', 'autoEndResponseCount:', 'autoEndDateTime:', 'autoEndFirstMatch:', 'error.invalid_auto_end_time:', 'error.invalid_auto_end_count:']) {
    has(lang, key, `${name} contains ${key}`);
  }
}
console.log(`KWC_531_AUTO_END_MOBILE_ADMIN_PASS assertions=${assertions}`);
