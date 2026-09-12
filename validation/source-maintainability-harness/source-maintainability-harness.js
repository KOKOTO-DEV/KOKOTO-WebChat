// KWC 파일 안내 / KWC file guide
// 소스 전반의 이중언어 유지보수 주석, generated frontend 동기화 전제, Windows BAT 줄바꿈과 active runtime 버전 잔여값을 정적 검사한다.
// Statically checks bilingual maintenance comments across the source tree, generated-frontend assumptions, Windows BAT line endings, and stale active-runtime version literals.
// 이 검사는 주석의 문장 품질을 자동 판정하지 않고 “유지보수 설명이 빠진 파일을 새로 만들지 않았는지”를 릴리스 전에 확인하는 안전망이다.
// This harness does not judge prose quality; it is a release-time safety net that prevents newly added maintainable source files from silently lacking maintenance guidance.

'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const root = path.resolve(process.argv[2] || path.join(__dirname, '..', '..'));
const FILE_GUIDE = 'KWC 파일 안내 / KWC file guide';
const FRONT_GUIDE = '[KWC 유지보수 주석 / KWC maintenance notes]';
const STYLE_GUIDE = 'KWC 스타일 구조 안내 / KWC style guide';
let assertions = 0;

function check(ok, message) {
  if (!ok) throw new Error(message);
  assertions++;
}

function walk(dir, out = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    if (['target', 'build', '.gradle', '.build-cache', 'release-5.3.0'].includes(ent.name)) continue;
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

const all = walk(root);
const javaFiles = all.filter(p => p.endsWith('.java'));
check(javaFiles.length >= 220, `unexpected Java source count: ${javaFiles.length}`);
for (const p of javaFiles) {
  const text = fs.readFileSync(p, 'utf8');
  check(text.includes(FILE_GUIDE), `missing bilingual file guide: ${path.relative(root, p)}`);
}

const fragmentsDir = path.join(root, 'frontend', 'inner');
const fragments = fs.readFileSync(path.join(fragmentsDir, 'manifest.txt'), 'utf8')
  .split(/\r?\n/).map(s => s.trim()).filter(Boolean);
check(fragments.length === 19, `expected 19 frontend fragments, got ${fragments.length}`);
for (const name of fragments) {
  const text = fs.readFileSync(path.join(fragmentsDir, name), 'utf8');
  check(text.includes(FRONT_GUIDE), `missing detailed frontend maintenance notes: ${name}`);
  check(/[가-힣]/.test(text), `frontend fragment lacks Korean explanation: ${name}`);
}

for (const ext of ['.ps1', '.bat', '.sh']) {
  const files = all.filter(p => p.endsWith(ext));
  check(files.length > 0, `no ${ext} files found`);
  for (const p of files) {
    const text = fs.readFileSync(p, 'utf8');
    check(text.includes(FILE_GUIDE), `missing bilingual script guide: ${path.relative(root, p)}`);
  }
}

const pomFiles = all.filter(p => path.basename(p) === 'pom.xml');
check(pomFiles.length > 0, 'no Maven POM files found');
for (const p of pomFiles) {
  const text = fs.readFileSync(p, 'utf8');
  check(text.includes(FILE_GUIDE), `missing Maven POM guide: ${path.relative(root, p)}`);
}

const gradleFiles = all.filter(p => ['build.gradle', 'settings.gradle'].includes(path.basename(p)));
check(gradleFiles.length > 0, 'no Gradle files found');
for (const p of gradleFiles) {
  const text = fs.readFileSync(p, 'utf8');
  check(text.includes(FILE_GUIDE), `missing Gradle guide: ${path.relative(root, p)}`);
}

const cssFiles = all.filter(p => path.basename(p) === 'chat.css');
check(cssFiles.length === 8, `expected 8 chat.css copies, got ${cssFiles.length}`);
const cssHashes = new Set();
for (const p of cssFiles) {
  const buf = fs.readFileSync(p);
  const text = buf.toString('utf8');
  check(text.includes(STYLE_GUIDE), `missing CSS style guide: ${path.relative(root, p)}`);
  cssHashes.add(crypto.createHash('sha256').update(buf).digest('hex'));
}
check(cssHashes.size === 1, 'chat.css copies are not byte-identical');

const batFiles = all.filter(p => p.endsWith('.bat'));
for (const p of batFiles) {
  const buf = fs.readFileSync(p);
  const lf = [...buf].filter(b => b === 0x0a).length;
  const crlf = buf.toString('binary').split('\r\n').length - 1;
  check(lf === crlf && crlf > 0, `BAT is not pure CRLF: ${path.relative(root, p)}`);
}

// 현재 loader/runtime에서 5.2.0을 출력하면 5.3.1 JAR이 구버전으로 보인다.
// If an active loader/runtime prints 5.2.0, a 5.3.1 JAR reports itself as the old release.
const activeRoots = ['kwc-platform-bukkit', 'kwc-platform-fabric', 'kwc-platform-forge', 'kwc-platform-neoforge'];
for (const top of activeRoots) {
  for (const p of walk(path.join(root, top))) {
    if (!p.endsWith('.java')) continue;
    const text = fs.readFileSync(p, 'utf8');
    check(!text.includes('5.2.0'), `stale active runtime 5.2.0 literal: ${path.relative(root, p)}`);
  }
}

const pluginYml = fs.readFileSync(path.join(root, 'kwc-platform-bukkit', 'src', 'main', 'resources', 'plugin.yml'), 'utf8');
check(/^version:\s*5\.3\.1\s*$/m.test(pluginYml), 'Bukkit plugin.yml runtime version is not 5.3.1');
const mainConfig = fs.readFileSync(path.join(root, 'kwc-platform-bukkit', 'src', 'main', 'resources', 'config.yml'), 'utf8');
check(mainConfig.includes('config-version: "5.3.0"'), 'current config.yml schema is not 5.3.0');
for (const lang of ['ko-KR','ja-JP','zh-CN']) {
  const template = fs.readFileSync(path.join(root, 'kwc-platform-bukkit', 'src', 'main', 'resources', 'config-templates', `config-${lang}.yml`), 'utf8');
  check(template.includes('KOKOTO WebChat 5.3.0'), `localized config template header is stale: ${lang}`);
  check(template.includes('config-version: "5.3.0"'), `localized config template schema is stale: ${lang}`);
}

// 배포용 설정 사양/설명 파일도 현재 스키마 버전과 함께 움직여야 한다.
// Distribution input/semantics metadata must move with the current config schema version.
for (const file of ['config-setting-input-specs-5.3.0.tsv', 'config-setting-semantics-5.3.0.tsv']) {
  const text = fs.readFileSync(path.join(root, 'distribution', file), 'utf8');
  const firstDataLine = text.split(/\r?\n/)[1] || '';
  check(firstDataLine.startsWith('config-version\t'), `config-version row is missing from ${file}`);
  check(firstDataLine.includes('\t5.3.0\t'), `config-version default is stale in ${file}`);
  check(!firstDataLine.includes('5.2.0_auto_migration'), `stale 5.2.0 migration marker remains in ${file}`);
  check(!firstDataLine.includes('exactly 5.2.0') && !firstDataLine.includes('정확히 5.2.0') && !firstDataLine.includes('5.2.0 と完全一致') && !firstDataLine.includes('恰好为 5.2.0'), `stale 5.2.0 semantics text remains in ${file}`);
}

const relay = fs.readFileSync(path.join(root, 'kwc-core', 'src', 'main', 'java', 'dev', 'kokoto', 'webchat', 'ServerRelay.java'), 'utf8');
check(relay.includes('LEGACY_V20_HANDSHAKE_PRODUCT_VERSION = "5.2.0"'), 'Relay 2.0 legacy handshake compatibility constant was lost');
check(relay.includes('PROTOCOL_REVISION = "2.2"'), 'Relay protocol revision is not 2.2');
check(relay.includes('game,profile'), 'Relay 2.2 profile capability is missing');

console.log(`SOURCE_MAINTAINABILITY_HARNESS_PASS assertions=${assertions} java=${javaFiles.length} fragments=${fragments.length} css=${cssFiles.length} bat=${batFiles.length}`);
