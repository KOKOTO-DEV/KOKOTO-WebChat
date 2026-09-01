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
