// KWC 파일 안내 / KWC file guide
// Minecraft 26.3 준비 타깃이 비활성 상태이고 기존 release matrix에 섞이지 않았는지 정적으로 검증한다.
// Statically verifies that Minecraft 26.3 preparation targets remain disabled and excluded from the existing release matrix.
// 정식 지원 전에는 이 harness가 “TBD pin + disabled + 45-target 유지”를 강제하며, 활성화 시 검사를 의도적으로 갱신해야 한다.
// Before official support, this harness enforces “TBD pins + disabled + unchanged 45-target”; activation must intentionally update this contract.

'use strict';
const fs=require('fs'), path=require('path');
const root=path.resolve(process.argv[2]||path.join(__dirname,'..','..')); let assertions=0;
function check(ok,msg){if(!ok)throw new Error(msg); assertions++;}
function read(rel){return fs.readFileSync(path.join(root,rel),'utf8');}
function prop(text,key){for(const line of text.split(/\r?\n/)){if(line.startsWith(key+'='))return line.slice(key.length+1).trim();}return null;}
const platforms=[
 {id:'fabric',required:['kwc.loader','kwc.fabric_api']},
 {id:'neoforge',required:['kwc.neoforge']},
 {id:'forge',required:['kwc.forge','kwc.loader_major','kwc.minecraft_next']},
];
for(const p of platforms){
 const base=`kwc-platform-${p.id}/targets/26.3`;
 for(const f of ['build.gradle','settings.gradle','gradle.properties'])check(fs.existsSync(path.join(root,base,f)),`missing ${base}/${f}`);
 const props=read(`${base}/gradle.properties`);
 check(prop(props,'kwc.prep.enabled')==='false',`${p.id} 26.3 prep must remain disabled`);
 check(prop(props,'kwc.minecraft')==='26.3',`${p.id} prep minecraft mismatch`);
 check(prop(props,'kwc.java')==='25',`${p.id} prep provisional Java must remain explicit`);
 for(const key of p.required)check(prop(props,key)==='TBD',`${p.id} ${key} should remain TBD until activation`);
 const settings=read(`${base}/settings.gradle`), build=read(`${base}/build.gradle`);
 check(settings.includes('kwc.prep.enabled'),`${p.id} settings missing prep gate`);
 check(settings.includes('throw new GradleException'),`${p.id} settings does not fail closed`);
 check(build.includes('requiredPin'),`${p.id} build missing exact-pin guard`);
}
const active=[
 'validate-release-windows.bat',
 'kwc-platform-fabric/build-all.bat','kwc-platform-fabric/build-all.sh','kwc-platform-fabric/build.gradle',
 'kwc-platform-neoforge/build-all.bat','kwc-platform-neoforge/build-all.sh','kwc-platform-neoforge/build.gradle',
 'kwc-platform-forge/build-all.bat','kwc-platform-forge/build-all.sh','kwc-platform-forge/build.gradle'];
for(const rel of active)check(!read(rel).includes('26.3'),`26.3 leaked into active release matrix: ${rel}`);
const release=read('validate-release-windows.bat');
check(release.includes('Bukkit 1 + Fabric 16 + NeoForge 12 + Forge 16 = 45 JARs'),'45-target banner changed');
check(release.includes('if "%EXPECTED_JARS%"=="45"'),'45-target guard changed');
for(const p of platforms){
 const bat=read(`kwc-platform-${p.id}/build-target.bat`), sh=read(`kwc-platform-${p.id}/build-target.sh`);
 check(bat.includes('kwc.prep.enabled='),`${p.id} Windows prep gate missing`);
 check(sh.includes('kwc.prep.enabled='),`${p.id} Unix prep gate missing`);
 check(bat.includes('disabled PREP target'),`${p.id} Windows prep error missing`);
 check(sh.includes('disabled PREP target'),`${p.id} Unix prep error missing`);
}
check(fs.existsSync(path.join(root,'docs','26.3-PREP.md')),'26.3 activation checklist missing');
console.log(`KWC_26_3_PREP_HARNESS_PASS assertions=${assertions} releaseTargets=45 prepPlatforms=${platforms.length}`);
