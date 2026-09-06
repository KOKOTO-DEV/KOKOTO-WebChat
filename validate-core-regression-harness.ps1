param([Parameter(Mandatory=$true)][string]$ProjectRoot)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $ProjectRoot).Path
$tmp=Join-Path $root '.build-cache\tmp\core-regression-harness'
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$jdkFile=Join-Path $tmp 'jdk17.txt'
& (Join-Path $root 'kwc-platform-fabric\select-java.ps1') -Major 17 -OutputFile $jdkFile
if($LASTEXITCODE -ne 0){ exit $LASTEXITCODE }
$javaHome=[IO.File]::ReadAllText($jdkFile,[Text.Encoding]::Default).Trim()
$javac=Join-Path $javaHome 'bin\javac.exe'; $java=Join-Path $javaHome 'bin\java.exe'
if(-not (Test-Path -LiteralPath $javac)){ throw "JDK 17 javac not found: $javac" }
$coreRoot=Join-Path $root 'kwc-core\src\main\java'
$sources=@(Get-ChildItem -LiteralPath $coreRoot -Recurse -Filter '*.java' -File | ForEach-Object { $_.FullName })
$sources += Join-Path $root 'validation\security-harness\SecurityRegressionHarness.java'
$sources += Join-Path $root 'validation\reaction-harness\ReactionRelayHarness.java'
$sources += Join-Path $root 'validation\web-push-active-view-harness\WebPushActiveViewHarness.java'
& $javac '--release' '17' '-encoding' 'UTF-8' '-d' $tmp @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item -LiteralPath (Join-Path $root 'kwc-standalone-frontend\src\main\resources\reaction-search-aliases.txt') -Destination (Join-Path $tmp 'reaction-search-aliases.txt') -Force
& $java '-cp' $tmp 'dev.kokoto.webchat.SecurityRegressionHarness'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java '-cp' $tmp 'ReactionRelayHarness'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java '-cp' $tmp 'dev.kokoto.webchat.WebPushActiveViewHarness'
exit $LASTEXITCODE
