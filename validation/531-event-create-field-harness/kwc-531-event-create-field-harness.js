const fs=require('fs'), path=require('path');
const root=path.resolve(__dirname,'../..'); let assertions=0;
function read(rel){return fs.readFileSync(path.join(root,rel),'utf8');}
function check(v,m){assertions++; if(!v) throw new Error('FAIL: '+m);}
function has(t,n,m){check(t.includes(n),m);}
const ui=read('frontend/inner/85-chat-games.js');
const css=read('kwc-standalone-frontend/src/main/resources/standalone/chat.css');
has(css,'.kwc-game-create-form [hidden] { display: none !important; }','event create form preserves hidden fields despite label/grid display rules');
has(ui,'const special = type === "poll" || type === "recruitment";','only poll/recruitment use the special multiline field');
has(ui,'if (specialField) specialField.hidden = !special;','special field visibility follows event type');
has(ui,'type === "recruitment"\n            ? t("game.recruitmentRoles", "Recruitment roles")\n            : ""','recruitment-role label is not used as a fallback for firstcome/lottery');
has(ui,'type === "recruitment"\n            ? t("game.recruitmentRolesHelp"','recruitment help is recruitment-only');
has(ui,'type === "recruitment"\n            ? "Tank:1\\nHealer:1\\nDPS:3"\n            : ""','recruitment placeholder is recruitment-only');
has(ui,'if (numberFields) numberFields.hidden = special;','poll/recruitment hide firstcome/lottery numeric fields');
has(ui,'if (autoResponseField) autoResponseField.hidden = !isPoll;','response-count automatic-end control is poll-only');
for(const rel of [
 'kwc-adapter-bluemap/src/main/resources/web/chat.css',
 'kwc-adapter-dynmap/src/main/resources/dynmap/chat.css',
 'kwc-adapter-liveatlas/src/main/resources/liveatlas/chat.css',
 'kwc-adapter-overviewer/src/main/resources/overviewer/chat.css',
 'kwc-adapter-pl3xmap/src/main/resources/pl3xmap/chat.css',
 'kwc-adapter-squaremap/src/main/resources/squaremap/chat.css',
 'kwc-adapter-unmined/src/main/resources/unmined/chat.css',
 'kwc-standalone-frontend/src/main/resources/standalone/chat.css']) {
  has(read(rel),'.kwc-game-create-form [hidden] { display: none !important; }',`${rel} keeps type-specific event fields hidden`);
}
console.log(`KWC_531_EVENT_CREATE_FIELD_PASS assertions=${assertions}`);
