# KWC 파일 안내 / KWC file guide
# 실제 PortableConfigMigration을 여러 과거 설정 fixture에 적용해 값 보존과 5.3.0 canonical 결과를 회귀검증한다.
# Regression-tests PortableConfigMigration against historical configuration fixtures for value preservation and canonical 5.3.0 output.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

param([Parameter(Mandatory=$true)][string]$ProjectRoot)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $ProjectRoot).Path
$tmp=Join-Path $root '.build-cache\tmp\config-migration-harness'
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$jdkFile=Join-Path $tmp 'jdk17.txt'
& (Join-Path $root 'kwc-platform-fabric\select-java.ps1') -Major 17 -OutputFile $jdkFile
if($LASTEXITCODE -ne 0){ exit $LASTEXITCODE }
$javaHome=[IO.File]::ReadAllText($jdkFile,[Text.Encoding]::Default).Trim()
$javac=Join-Path $javaHome 'bin\javac.exe'; $java=Join-Path $javaHome 'bin\java.exe'
if(-not (Test-Path -LiteralPath $javac)){ throw "JDK 17 javac not found: $javac" }
$sources=@(
 'kwc-core\src\main\java\dev\kokoto\webchat\ContentFilterRule.java',
 'kwc-core\src\main\java\dev\kokoto\webchat\ConfigTextEditor.java',
 'kwc-core\src\main\java\dev\kokoto\webchat\PortableConfigMigration.java',
 'validation\config-migration-harness\PortableConfigMigrationHarness.java'
) | ForEach-Object { Join-Path $root $_ }
& $javac '--release' '17' '-encoding' 'UTF-8' '-d' $tmp @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java '-cp' $tmp 'PortableConfigMigrationHarness'
exit $LASTEXITCODE
