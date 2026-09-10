#!/usr/bin/env node
// KWC 파일 안내 / KWC file guide
// CAPTCHA 다중 모드가 runtime setting, 기본 설정/배포 메타데이터, 관리자 UI에 일관되게 연결됐는지 검증한다.
// Verifies that multiple CAPTCHA modes are consistently wired through runtime settings, default/distribution metadata, and admin UI.
'use strict';
const fs=require('fs'), path=require('path');
const root=path.resolve(process.argv[2]||path.join(__dirname,'../..'));
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
let n=0; const check=(c,m)=>{n++; if(!c){console.error('CAPTCHA_MODES_UI_FAIL: '+m); process.exit(1);}};
const manager=read('kwc-core/src/main/java/dev/kokoto/webchat/CaptchaManager.java');
const runtime=read('kwc-core/src/main/java/dev/kokoto/webchat/RuntimeSettingsController.java');
const server=read('kwc-core/src/main/java/dev/kokoto/webchat/WebChatServer.java');
const inner=read('inner.js');
const stream=read('frontend/inner/60-theme-config-stream.js');
check(manager.includes('"mixed".equals(mode)'), 'mixed mode dispatcher');
check(manager.includes('issueText'), 'text challenge generator');
check(manager.includes('TEXT_ALPHABET'), 'text challenge alphabet');
check(manager.includes('equalsIgnoreCase(actual)'), 'text code case-insensitive verification');
check(runtime.includes('Set.of("off", "math", "text", "mixed")'), 'runtime mode allowlist');
check(server.includes('captcha.issue(config.captchaMode') && server.includes('config.captchaMathComplexity'), 'server issues configured mode and math complexity');
check(manager.includes('issueMath(int expireSeconds, String complexity)') && manager.includes('"easy".equals(level)') && manager.includes('"hard".equals(level)'), 'math complexity generator supports easy/normal/hard');
check(server.includes('m.put("type", c.type)'), 'captcha API exposes challenge type');
check(inner.includes('optionCaptchaText'), 'admin text mode option');
check(inner.includes('optionCaptchaMixed'), 'admin mixed mode option');
check(inner.includes('captcha.math-complexity') && inner.includes('optionCaptchaMathEasy') && inner.includes('optionCaptchaMathHard'), 'admin math complexity selector');
check(stream.includes('fmt("captcha.enterCode", "Enter code: {code}"'), 'text CAPTCHA prompt is localized through i18n');
check(!manager.includes('Solve:'), 'math CAPTCHA shows expression without Solve prefix');
const configFiles=[
 'kwc-platform-bukkit/src/main/resources/config.yml',
 'kwc-platform-bukkit/src/main/resources/config-baselines/config-5.3.0.yml',
 'kwc-platform-bukkit/src/main/resources/config-templates/config-ko-KR.yml',
 'kwc-platform-bukkit/src/main/resources/config-templates/config-ja-JP.yml',
 'kwc-platform-bukkit/src/main/resources/config-templates/config-zh-CN.yml',
 'distribution/config-reference-5.3.0.yml'
];
for(const file of configFiles){const x=read(file); check(x.includes('mode: "math"'), file+' default math'); check(x.includes('math-complexity: "normal"'), file+' default math complexity'); check(x.includes('text')&&x.includes('mixed')&&x.includes('off'), file+' mode docs');}
const spec=read('distribution/config-setting-input-specs-5.3.0.tsv');
const sem=read('distribution/config-setting-semantics-5.3.0.tsv');
check(spec.includes('captcha.mode\tstr\tmath\tmath | text | mixed | off'), 'input spec mode choices');
check(spec.includes('captcha.math-complexity\tstr\tnormal\teasy | normal | hard'), 'input spec math complexity choices');
check(sem.includes('captcha.mode\tmath\tGuest CAPTCHA mode'), 'semantics mode description');
check(sem.includes('captcha.math-complexity\tnormal'), 'semantics math complexity description');
for(const lang of ['en-US','ko-KR','ja-JP','zh-CN']){
 const y=read(`kwc-platform-bukkit/src/main/resources/lang/${lang}.yml`);
 check(y.includes('optionCaptchaText:'), `${lang} text mode label`);
 check(y.includes('optionCaptchaMixed:'), `${lang} mixed mode label`);
 check(y.includes('enterCode:') && y.includes('optionCaptchaMathEasy:') && y.includes('optionCaptchaMathHard:'), `${lang} text prompt and math complexity labels`);
}
console.log('CAPTCHA_MODES_UI_PASS assertions='+n);
