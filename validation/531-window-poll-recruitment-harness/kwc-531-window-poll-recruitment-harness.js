const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function check(value, message) {
  assertions++;
  if (!value) throw new Error(`FAIL: ${message}`);
}
function has(text, needle, message) { check(text.includes(needle), message); }

const multi = read('frontend/inner/115-private-multiwindow.js');
const privateMgmt = read('frontend/inner/140-group-management.js');
const gamesUi = read('frontend/inner/85-chat-games.js');
const manager = read('kwc-core/src/main/java/dev/kokoto/webchat/ChatGameManager.java');
const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const commands = read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
const rootPom = read('pom.xml');
const pluginYml = read('kwc-platform-bukkit/src/main/resources/plugin.yml');
const migration = read('kwc-core/src/main/java/dev/kokoto/webchat/PortableConfigMigration.java');
const bukkitMigration = read('kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/ConfigMigrationManager.java');

has(multi, 'function raiseExistingPrivateListWindow(type)', 'shared private-list bring-to-front helper exists');
has(multi, 'raiseIndependentChatWindow(wrap);', 'existing private list is raised through shared z-order path');
has(multi, 'function mountPrivateListWindowOwnedOverlay(type, wrap)', 'shared list-owned overlay helper exists for hub-level actions');
has(privateMgmt, 'if (raiseExistingPrivateListWindow("dm")) return;', 'DM header action raises an already-open DM list');
has(privateMgmt, 'if (raiseExistingPrivateListWindow("group")) return;', 'Group header action raises an already-open group list');
has(privateMgmt, 'mountPrivateListWindowOwnedOverlay("group", wrap);', 'new group room form is owned by the group list window');
has(privateMgmt, 'mountPrivateWindowOwnedOverlay("group", wrap);', 'room-local settings keep using the active group conversation owner');
has(privateMgmt, 'state.dmModalOpen = false;', 'DM stale-open state can recover when DOM is missing');
has(privateMgmt, 'state.groupModalOpen = false;', 'Group stale-open state can recover when DOM is missing');

has(rootPom, '<version>5.3.1</version>', 'Maven product version is 5.3.1');
has(pluginYml, 'version: 5.3.1', 'Bukkit product version is 5.3.1');
has(migration, 'CONFIG_SCHEMA_VERSION = "5.3.0"', '5.3.1 keeps the unchanged 5.3.0 config schema');
has(bukkitMigration, 'PortableConfigMigration.CONFIG_SCHEMA_VERSION', 'Bukkit migration uses schema version rather than product patch version');

has(manager, 'createPoll(', 'poll creation exists');
has(manager, 'createRecruitment(', 'recruitment creation exists');
has(manager, 'vote(', 'poll vote action exists');
has(manager, 'applyRecruitment(', 'recruitment apply action exists');
has(manager, 'withdrawRecruitment(', 'recruitment withdrawal exists');
has(manager, 'pollOptions', 'poll options are persisted/snapshotted');
has(manager, 'pollVotes', 'poll votes are persisted/snapshotted');
has(manager, 'recruitmentRoles', 'recruitment role capacities are persisted/snapshotted');
has(manager, 'participantStates', 'recruitment accepted/waiting state is persisted');
has(manager, 'promoteNextWaiting', 'recruitment has automatic oldest-waiter promotion');

has(server, 'voteChatGame(', 'web/server vote wrapper exists');
has(server, 'applyRecruitmentChatGame(', 'web/server recruitment apply wrapper exists');
has(server, 'withdrawRecruitmentChatGame(', 'web/server recruitment withdraw wrapper exists');
has(server, '"vote".equals(action)', 'Relay/web action dispatcher accepts vote');
has(server, '"apply".equals(action)', 'Relay/web action dispatcher accepts apply');
has(server, '"withdraw".equals(action)', 'Relay/web action dispatcher accepts withdraw');
has(server, '"snapshot".equals(action)', 'Relay event status/snapshot request remains supported');

has(commands, '/kchat game vote <eventId> <optionNumber>', 'in-game poll vote syntax exists');
has(commands, '/kchat game apply <eventId> <role>', 'in-game recruitment apply syntax exists');
has(commands, 'withdraw [eventId]', 'in-game recruitment withdraw syntax exists');
has(commands, 'gameCreateUsageLines', 'in-game poll creation usage is generated dynamically');
has(commands, 'create recruitment', 'in-game recruitment creation path exists');

has(gamesUi, '<option value="poll">', 'web create form exposes poll type');
has(gamesUi, '<option value="recruitment">', 'web create form exposes recruitment type');
has(gamesUi, 'act("vote"', 'web poll vote action exists');
has(gamesUi, 'act("apply"', 'web recruitment apply action exists');
has(gamesUi, 'act("withdraw"', 'web recruitment withdraw action exists');

for (const lang of ['en-US', 'ko-KR', 'ja-JP', 'zh-CN']) {
  const text = read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
  has(text, 'poll:', `${lang} includes poll localization`);
  has(text, 'recruitment:', `${lang} includes recruitment localization`);
}

console.log(`KWC_531_WINDOW_POLL_RECRUITMENT_PASS assertions=${assertions}`);
