#!/usr/bin/env node
// KWC 파일 안내 / KWC file guide
// 18개 frontend fragment를 manifest 순서로 결합해 배포용 inner.js를 만들고 wrapper 8종 embedded JS/CSS payload까지 동기화하는 생성기다.
// Builds deployable inner.js from 18 frontend fragments in manifest order and synchronizes embedded JS/CSS payloads in all eight wrappers.
// --check는 generated 파일 drift를 CI/릴리스 전에 잡는 검증 모드이고, --write만 실제 파일을 갱신한다.
// --check detects generated-file drift before CI/release, while --write is the mode that actually updates files.

'use strict';

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const sourceDir = path.join(root, 'frontend', 'inner');
const manifestPath = path.join(sourceDir, 'manifest.txt');
const outputPath = path.join(root, 'inner.js');
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

function fail(message) {
  console.error(`INNER_BUNDLE_FAIL: ${message}`);
  process.exit(1);
}

function manifestFiles() {
  if (!fs.existsSync(manifestPath)) fail(`missing manifest: ${manifestPath}`);
  const names = fs.readFileSync(manifestPath, 'utf8')
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line && !line.startsWith('#'));
  if (!names.length) fail('manifest has no source fragments');
  const seen = new Set();
  for (const name of names) {
    if (seen.has(name)) fail(`duplicate manifest entry: ${name}`);
    seen.add(name);
    if (path.basename(name) !== name || !name.endsWith('.js')) fail(`invalid fragment name: ${name}`);
    const file = path.join(sourceDir, name);
    if (!fs.existsSync(file)) fail(`missing fragment: ${name}`);
  }
  return names;
}

function buildBundle() {
  return Buffer.concat(manifestFiles().map(name => fs.readFileSync(path.join(sourceDir, name))));
}

function embeddedCss(text, wrapper) {
  const match = text.match(/^\s*const KWC_EMBEDDED_CSS_TEXT = (.*);$/m);
  if (!match) fail(`embedded CSS payload not found: ${wrapper}`);
  try {
    return JSON.parse(match[1]);
  } catch (error) {
    fail(`embedded CSS payload is not valid JSON string in ${wrapper}: ${error.message}`);
  }
}

function wrapperCssPath(wrapper) {
  if (!wrapper.endsWith('chat.js')) fail(`wrapper does not end with chat.js: ${wrapper}`);
  return path.join(root, wrapper.slice(0, -'chat.js'.length) + 'chat.css');
}

function embeddedInner(text, wrapper) {
  const match = text.match(/^\s*const KWC_EMBEDDED_INNER_TEXT = (.*);$/m);
  if (!match) fail(`embedded inner payload not found: ${wrapper}`);
  try {
    return JSON.parse(match[1]);
  } catch (error) {
    fail(`embedded inner payload is not valid JSON string in ${wrapper}: ${error.message}`);
  }
}

function writeWrapper(wrapper, bundleText) {
  const file = path.join(root, wrapper);
  let text = fs.readFileSync(file, 'utf8');
  const innerPattern = /^\s*const KWC_EMBEDDED_INNER_TEXT = .*;$/m;
  const cssPattern = /^\s*const KWC_EMBEDDED_CSS_TEXT = .*;$/m;
  if (!innerPattern.test(text)) fail(`embedded inner payload not found: ${wrapper}`);
  if (!cssPattern.test(text)) fail(`embedded CSS payload not found: ${wrapper}`);
  const cssFile = wrapperCssPath(wrapper);
  if (!fs.existsSync(cssFile)) fail(`wrapper CSS is missing: ${path.relative(root, cssFile)}`);
  const cssText = fs.readFileSync(cssFile, 'utf8');
  text = text.replace(innerPattern, () => `  const KWC_EMBEDDED_INNER_TEXT = ${JSON.stringify(bundleText)};`);
  text = text.replace(cssPattern, () => `  const KWC_EMBEDDED_CSS_TEXT = ${JSON.stringify(cssText)};`);
  fs.writeFileSync(file, text, 'utf8');
}

function check(bundle) {
  if (!fs.existsSync(outputPath)) fail('generated inner.js is missing');
  const output = fs.readFileSync(outputPath);
  if (!output.equals(bundle)) fail('inner.js differs from frontend/inner manifest bundle; run with --write');
  const bundleText = bundle.toString('utf8');
  for (const wrapper of wrappers) {
    const text = fs.readFileSync(path.join(root, wrapper), 'utf8');
    if (embeddedInner(text, wrapper) !== bundleText) {
      fail(`${wrapper} embedded inner payload differs from generated inner.js; run with --write`);
    }
    const cssFile = wrapperCssPath(wrapper);
    if (!fs.existsSync(cssFile)) fail(`wrapper CSS is missing: ${path.relative(root, cssFile)}`);
    const cssText = fs.readFileSync(cssFile, 'utf8');
    if (embeddedCss(text, wrapper) !== cssText) {
      fail(`${wrapper} embedded CSS payload differs from ${path.relative(root, cssFile)}; run with --write`);
    }
  }
  console.log(`INNER_BUNDLE_CHECK_PASS fragments=${manifestFiles().length} wrappers=${wrappers.length} css=${wrappers.length} bytes=${bundle.length}`);
}

const args = new Set(process.argv.slice(2));
if (args.has('--help') || args.has('-h')) {
  console.log('Usage: node tools/build-inner-bundle.js [--check|--write]');
  process.exit(0);
}
if (args.has('--check') && args.has('--write')) fail('choose either --check or --write');

const bundle = buildBundle();
if (args.has('--write')) {
  fs.writeFileSync(outputPath, bundle);
  const bundleText = bundle.toString('utf8');
  for (const wrapper of wrappers) writeWrapper(wrapper, bundleText);
  console.log(`INNER_BUNDLE_WRITE_PASS fragments=${manifestFiles().length} wrappers=${wrappers.length} css=${wrappers.length} bytes=${bundle.length}`);
  check(bundle);
} else {
  check(bundle);
}
