#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..', '..');
let assertions = 0;
function check(cond, msg) { assertions++; if (!cond) throw new Error(msg); }
function read(rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }

const runtimeFiles = [
  'kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java',
  'kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java',
  'kwc-core/src/main/java/dev/kokoto/webchat/NativeWhisperCommand.java',
  'kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/KwcCommand.java',
  'kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/ChatListener.java',
  'kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/KokotoWebChatPlugin.java',
  'frontend/inner/110-auth-dm-core.js',
  'kwc-platform-bukkit/src/main/resources/plugin.yml',
  'kwc-platform-bukkit/src/main/resources/lang/en-US.yml',
  'kwc-platform-bukkit/src/main/resources/lang/ko-KR.yml',
  'kwc-platform-bukkit/src/main/resources/lang/ja-JP.yml',
  'kwc-platform-bukkit/src/main/resources/lang/zh-CN.yml',
  'kwc-platform-bukkit/src/main/resources/config.yml',
  'kwc-platform-bukkit/src/main/resources/config-baselines/config-5.3.0.yml',
  'distribution/config-reference-5.3.0.yml',
  'distribution/config-setting-semantics-5.3.0.tsv',
  'inner.js',
  'kwc-standalone-frontend/src/main/resources/standalone/chat.js'
];
for (const rel of runtimeFiles) {
  const s = read(rel);
  check(!s.includes('/kwc '), `${rel} must not expose obsolete /kwc commands`);
  check(!s.includes('/kc '), `${rel} must not advertise the short alias as the canonical command`);
}

for (const rel of [
  'kwc-platform-bukkit/src/main/resources/lang/en-US.yml',
  'kwc-platform-bukkit/src/main/resources/lang/ko-KR.yml',
  'kwc-platform-bukkit/src/main/resources/lang/ja-JP.yml',
  'kwc-platform-bukkit/src/main/resources/lang/zh-CN.yml'
]) {
  const s = read(rel);
  check(s.includes('/kchat auth <code>'), `${rel} account-link hint must use /kchat`);
  check(s.includes('/kchat dm list'), `${rel} DM hints must use /kchat`);
}

const game = read('kwc-core/src/main/java/dev/kokoto/webchat/GameCommandService.java');
check(game.includes('"/kchat dm "'), 'game DM clickable command must use /kchat');
check(game.includes('"/kchat reply "'), 'game reply command parser/generator must use /kchat');
const web = read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
check(web.includes('"/kchat dm "'), 'web-to-game DM action must use /kchat');
check(web.includes('"/kchat reply "'), 'web-to-game reply action must use /kchat');
const nativeWhisper = read('kwc-core/src/main/java/dev/kokoto/webchat/NativeWhisperCommand.java');
check(nativeWhisper.includes('return "/kchat dm "'), 'native whisper conversion must use /kchat');
const plugin = read('kwc-platform-bukkit/src/main/resources/plugin.yml');
check(plugin.includes('aliases: [kc, kwc]'), 'Bukkit must keep /kc and /kwc as executable aliases');
check(plugin.includes('usage: /kchat '), 'Bukkit usage must advertise /kchat');
for (const rel of [
  'kwc-platform-fabric/src/compat118/java/dev/kokoto/webchat/fabric/KwcFabricMod.java',
  'kwc-platform-fabric/src/compatV2/java/dev/kokoto/webchat/fabric/KwcFabricMod.java',
  'kwc-platform-neoforge/src/compatEvents/java/dev/kokoto/webchat/neoforge/KwcNeoForgeMod.java',
  'kwc-platform-forge/src/common/java/dev/kokoto/webchat/forge/ForgeCommandBridge.java'
]) {
  const loader = read(rel);
  check(loader.includes('literal("kc")'), `${rel} must keep kc alias registration`);
  check(loader.includes('literal("kwc")'), `${rel} must keep kwc alias registration without advertising it`);
}

const listener = read('kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/ChatListener.java');
check(listener.includes('root.equals("kwc")'), 'Bukkit raw-command capture must recognize /kwc');
const bukkitCommand = read('kwc-platform-bukkit/src/main/java/dev/kokoto/webchat/KwcCommand.java');
check(bukkitCommand.includes('root.equals("kwc")'), 'Bukkit command-body recovery must recognize /kwc');

const legacy = read('kwc-core/src/main/java/dev/kokoto/webchat/BuiltinLanguageRevision.java');
check(legacy.includes('current.equals("/kwc auth <code>")'), 'legacy /kwc built-in language values must still be recognized for migration only');
check(legacy.includes('current.equals("/kc auth <code>")'), 'RC9 /kc built-in language values must migrate back to canonical /kchat');

console.log(`COMMAND_SURFACE_PASS assertions=${assertions}`);
