const fs=require('fs'), path=require('path');
const root=path.resolve(__dirname,'../..'); let assertions=0;
function read(rel){return fs.readFileSync(path.join(root,rel),'utf8');}
function check(v,m){assertions++; if(!v) throw new Error('FAIL: '+m);}
function has(t,n,m){check(t.includes(n),m)}
const cmd=read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
const server=read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const ui=read('frontend/inner/85-chat-games.js');
const bukkit=read('kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/KwcCommand.java');
has(cmd,'public List<String> suggest(Sender sender, String rawArguments)','core completion service exists');
for(const a of ['"vote"','"apply"','"withdraw"','"delete"','"autoend"','"filter"','"settings"','"admin"','"guest"','"dm"','"group"']) has(cmd,a,`completion/action ${a} exists`);
has(cmd,'pollOptions','poll option-number completion reads options');
has(cmd,'recruitmentRoles','recruitment role completion reads roles');
has(cmd,'server.chatGameRelayTopologyConfigured()','scope completion is topology-aware');

has(cmd,'RuntimeSettingsController.SUPPORTED_PATHS','settings completion exposes supported live-setting paths');
has(cmd,'contentFilterRules','filter remove completion reads current rule IDs');
has(cmd,'commandPlayerTarget','shared DM completion can suggest known players');
has(cmd,'addGroupTargets','shared group completion can suggest joined rooms');
has(cmd,'gameAutoEndRequired','first-come capacity auto-end cannot be disabled');
has(cmd,'/kchat game autoend <eventId>','autoend management command exists');
has(cmd,'/kchat game scope <eventId> <relay|local>','scope management command exists');
has(server,'public boolean chatGameRelayTopologyConfigured()','server topology helper exists');
has(server,'send.enabled && send.event','topology requires event send permission');
has(server,'eventRelayTopology','config exposes topology state');
has(server,'relayAnnouncements = chatGameRelayTopologyConfigured() && relayAnnouncements','creation normalizes relay scope');
has(ui,'eventRelayTopology === true','web scope UI is topology-aware');
has(ui,' : "local"','web single-server create sends local scope');
has(bukkit,'case "filter"','Bukkit filter dispatcher restored');
has(bukkit,'case "settings"','Bukkit settings dispatcher restored');
has(bukkit,'gameCommands.suggest(sharedSender(sender)','Bukkit game completion delegates to core');
for(const rel of [
 'kwc-platform-fabric/src/compat118/java/dev/kokoto/webchat/fabric/KwcFabricMod.java',
 'kwc-platform-fabric/src/compatV2/java/dev/kokoto/webchat/fabric/KwcFabricMod.java',
 'kwc-platform-neoforge/src/compatEvents/java/dev/kokoto/webchat/neoforge/KwcNeoForgeMod.java',
 'kwc-platform-forge/src/common/java/dev/kokoto/webchat/forge/ForgeCommandBridge.java']){
 const t=read(rel); has(t,'.suggests(',`${rel} installs Brigadier suggestions`); has(t,'.suggest(',`${rel} calls shared completion`);
}
for(const lang of ['en-US.yml','ko-KR.yml','ja-JP.yml','zh-CN.yml']){
 const t=read('kwc-platform-bukkit/src/main/resources/lang/'+lang);
 for(const k of ['gameAutoEndSummary:','gameScopeSummary:','gameScopeUnavailable:','gameAutoEndRequired:']) has(t,k,`${lang} has ${k}`);
}
const wiki=read('wiki/Chat-Events.md');
has(wiki,'topology-aware','wiki documents topology-aware scope');
has(wiki,'automatic-end','wiki documents automatic-end controls');
console.log(`KWC_531_COMMAND_COMPLETION_SCOPE_PASS assertions=${assertions}`);
