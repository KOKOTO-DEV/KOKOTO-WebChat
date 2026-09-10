const fs = require('fs');
const path = require('path');
const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
let assertions = 0;
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function has(text, needle, message) { assertions++; if (!text.includes(needle)) throw new Error(message + `: missing ${needle}`); }

const model = read('kwc-core/src/main/java/dev/kokoto/webchat/PinnedMessage.java');
for (const field of ['pinnedByUuid', 'pinnedByUsername', 'pinnedByDisplayName', 'pinnedBy']) {
  has(model, `public String ${field};`, `public pin model persists ${field}`);
  has(model, `m.put("${field}"`, `public pin JSON exposes ${field}`);
}

const server = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
has(server, 'storage.pinMessage(found, pinnerUuid, pinnerUsername, pinnerDisplayName, config.pinnedMaxPins)', 'public pin captures canonical pinner identity');
has(server, 'private String publicPinnedJson(PinnedMessage pin)', 'public pin response refreshes current pinner identity');
has(server, 'pin.pinnedByUsername = account.safeUsername();', 'public pin real account name refreshes from account');
has(server, 'pin.pinnedByDisplayName = displayName == null || displayName.isBlank() ? pin.pinnedByUsername : displayName;', 'public pin display name refreshes from account');

const stores = [
  'kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/Storage.java',
  'kwc-platform-fabric/src/common/java/dev/kokoto/webchat/fabric/FabricStorage.java',
  'kwc-platform-forge/src/common/java/dev/kokoto/webchat/forge/ForgeStorage.java',
  'kwc-platform-neoforge/src/common/java/dev/kokoto/webchat/neoforge/NeoForgeStorage.java'
];
for (const rel of stores) {
  const text = read(rel);
  has(text, 's.getString("pinnedByUuid")', `${rel} loads pinner UUID`);
  has(text, 's.getString("pinnedByUsername")', `${rel} loads pinner real username`);
  has(text, 's.getString("pinnedByDisplayName")', `${rel} loads pinner display name`);
  has(text, 'yml.set(p + "pinnedByUuid", pin.pinnedByUuid)', `${rel} saves pinner UUID`);
  has(text, 'yml.set(p + "pinnedByUsername", pin.pinnedByUsername)', `${rel} saves pinner username`);
  has(text, 'yml.set(p + "pinnedByDisplayName", pin.pinnedByDisplayName)', `${rel} saves pinner display name`);
  has(text, 'pin.pinnedByDisplayName = yamlValue(s.getString("pinnedByDisplayName"), pin.pinnedBy);', `${rel} keeps legacy pinnedBy compatibility`);
}

const groupModel = read('kwc-core/src/main/java/dev/kokoto/webchat/GroupPinnedMessage.java');
for (const field of ['pinnedByUuid', 'pinnedByUsername', 'pinnedByDisplayName']) {
  has(groupModel, `m.put("${field}"`, `group pin JSON retains ${field}`);
}
const ui = read('frontend/inner/80-pins-admin.js');
has(ui, 'function pinnedByIdentityParts(pin)', 'shared pinner identity normalizer exists');
has(ui, 'function pinnedByDetailHtml(pin)', 'shared pinner identity renderer exists');
has(ui, 'directMessageIdentityHtml({', 'pinner uses common display/real identity renderer');
has(ui, 'detail.innerHTML = pinnedByDetailHtml(pin);', 'public pinned modal uses name mode aware pinner');
has(ui, 'const detail = pinnedByDetailHtml(pin);', 'group pinned modal uses name mode aware pinner');
const identityUi = read('frontend/inner/110-auth-dm-core.js');
has(identityUi, 'data-real-sender=', 'common identity renderer remains global name-mode compatible');

console.log(`RC32_PIN_IDENTITY_PASS assertions=${assertions} surfaces=public,group stores=${stores.length}`);
