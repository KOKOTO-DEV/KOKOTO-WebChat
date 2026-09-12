// KWC 파일 안내 / KWC file guide
// BlueMapRefreshBootstrapHarness.js는 KWC 개발/배포 과정에서 사용하는 JavaScript 보조 코드다.
// BlueMapRefreshBootstrapHarness.js is JavaScript support code used by the KWC development or packaging workflow.
// 배포 runtime 코드와 생성 코드를 구분하고, generated 산출물을 수동 편집하지 않도록 source-of-truth 경로를 유지한다.
// Keep runtime source separate from generated artifacts and preserve the source-of-truth path instead of manually editing generated output.

const fs = require('fs');
const vm = require('vm');
const path = require('path');

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const source = fs.readFileSync(path.join(root, 'kwc-adapter-bluemap/src/main/resources/web/chat.js'), 'utf8');
let assertions = 0;
function ok(value, message) {
  if (!value) throw new Error('ASSERT FAILED: ' + message);
  assertions++;
}
function makeContext() {
  const appended = [];
  const timers = [];
  const location = {origin:'https://example.test', href:'https://example.test/maps/', protocol:'https:', hostname:'example.test'};
  const window = {location};
  const document = {
    currentScript: {src:'https://example.test/addons/kokoto-webchat/chat.js?v=5.3.0-test'},
    createElement(tag) { return {tagName:String(tag).toUpperCase(), src:'', async:true, onload:null, onerror:null}; },
    head: {appendChild(node) { appended.push(node); return node; }},
    documentElement: {appendChild(node) { appended.push(node); return node; }}
  };
  const context = vm.createContext({window, document, location, URL, console, Date, Math,
    setTimeout(fn, delay) { timers.push({fn, delay:Number(delay)||0}); return timers.length; },
    clearTimeout() {}
  });
  return {context, window, document, appended, timers};
}

// Missing config: the first execution must stop before normal chat bootstrap and recover config.js.
{
  const t = makeContext();
  vm.runInContext(source, t.context, {filename:'chat.js'});
  ok(t.appended.length === 0, 'no script is injected synchronously before the recovery task turn');
  ok(t.timers.length === 1 && t.timers[0].delay === 0, 'recovery waits one task turn for BlueMap config ordering');
  t.timers.shift().fn();
  ok(t.appended.length === 1, 'missing config injects one recovery script');
  ok(/\/config\.js(?:\?|$)/.test(t.appended[0].src), 'recovery script is config.js in the same addon directory');
  ok(t.appended[0].src.includes('kwc-recover='), 'recovery config bypasses stale browser cache');
  t.window.KokotoWebChatConfig = {apiBase:'https://example.test/chat/api', apiBaseUrl:'https://example.test/chat/api'};
  t.appended[0].onload();
  ok(t.appended.length === 2, 'loaded config reboots the chat wrapper');
  ok(/\/chat\.js(?:\?|$)/.test(t.appended[1].src), 'second injected script is chat.js');
  ok(t.appended[1].src.includes('kwc-recover='), 'rebooted chat bypasses stale browser cache');
}

// Normal race: if BlueMap's original config.js finishes before the zero-delay recovery callback,
// do not fetch config again; just reboot the wrapper with the now-correct API base.
{
  const t = makeContext();
  vm.runInContext(source, t.context, {filename:'chat.js'});
  t.window.KokotoWebChatConfig = {apiBase:'https://example.test/chat/api'};
  t.timers.shift().fn();
  ok(t.appended.length === 1, 'late original config causes direct chat reboot only');
  ok(/\/chat\.js(?:\?|$)/.test(t.appended[0].src), 'late-config path reboots chat.js');
  ok(!/\/config\.js(?:\?|$)/.test(t.appended[0].src), 'late-config path does not duplicate config.js');
}

// Transient recovery failure must schedule another attempt instead of leaving the page dead.
{
  const t = makeContext();
  vm.runInContext(source, t.context, {filename:'chat.js'});
  t.timers.shift().fn();
  ok(t.appended.length === 1 && typeof t.appended[0].onerror === 'function', 'config recovery exposes retryable error path');
  t.appended[0].onerror();
  ok(t.timers.length === 1 && t.timers[0].delay >= 250, 'failed recovery schedules backoff retry');
}

console.log('BLUEMAP_REFRESH_BOOTSTRAP_PASS assertions=' + assertions);
