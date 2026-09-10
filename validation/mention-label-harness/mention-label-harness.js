// KWC 파일 안내 / KWC file guide
// mention-label-harness.js는 KWC 개발/배포 과정에서 사용하는 JavaScript 보조 코드다.
// mention-label-harness.js is JavaScript support code used by the KWC development or packaging workflow.
// 배포 runtime 코드와 생성 코드를 구분하고, generated 산출물을 수동 편집하지 않도록 source-of-truth 경로를 유지한다.
// Keep runtime source separate from generated artifacts and preserve the source-of-truth path instead of manually editing generated output.

const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..', '..');
let checks = 0;
function check(v, m) { if (!v) throw new Error(m); checks++; }
const inner = fs.readFileSync(path.join(root, 'inner.js'), 'utf8');
check(inner.includes('function normalizeMentionNotificationLabel(value)'), 'mention label normalizer exists');
check(inner.includes('return text.startsWith("@") ? text : `@${text}`;'), 'normalizer prefixes stale labels');
check(inner.includes('name === "mentions" ? normalizeMentionNotificationLabel(rawText) : rawText'), 'notification checkbox uses mention normalizer');
check(inner.includes('${row("mentions", "@Mention")}'), 'mention fallback contains @');
const expected = {
  'en-US.yml': '@Mention',
  'ko-KR.yml': '@멘션',
  'ja-JP.yml': '@メンション',
  'zh-CN.yml': '@提及'
};
for (const [name, value] of Object.entries(expected)) {
  const text = fs.readFileSync(path.join(root, 'kwc-platform-bukkit', 'src', 'main', 'resources', 'lang', name), 'utf8');
  const escaped = value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  check(new RegExp(`^\\s*notifyMentions:\\s*['\"]?${escaped}['\"]?\\s*$`, 'm').test(text), `${name} mention label`);
}
console.log(`MENTION_LABEL_HARNESS_PASS assertions=${checks}`);
