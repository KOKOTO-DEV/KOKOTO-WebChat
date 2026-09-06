param(
  [Parameter(Mandatory=$true)][string]$ProjectRoot,
  [Parameter(Mandatory=$true)][string]$JarPath
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $ProjectRoot).Path
$jar=(Resolve-Path -LiteralPath $JarPath).Path
$tmp=Join-Path $root '.build-cache\tmp\archive-runtime-harness'
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$jdkFile=Join-Path $tmp 'jdk17.txt'
& (Join-Path $root 'kwc-platform-fabric\select-java.ps1') -Major 17 -OutputFile $jdkFile
if($LASTEXITCODE -ne 0){ exit $LASTEXITCODE }
$javaHome=[IO.File]::ReadAllText($jdkFile,[Text.Encoding]::Default).Trim()
$javac=Join-Path $javaHome 'bin\javac.exe'; $java=Join-Path $javaHome 'bin\java.exe'
if(-not (Test-Path -LiteralPath $javac)){ throw "JDK 17 javac not found: $javac" }
$source=Join-Path $root 'validation\archive-runtime-harness\ConversationArchiveRuntimeHarness.java'
& $javac '--release' '17' '-encoding' 'UTF-8' '-cp' $jar '-d' $tmp $source
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$cp="$jar;$tmp"
& $java '-cp' $cp 'ConversationArchiveRuntimeHarness'
exit $LASTEXITCODE
