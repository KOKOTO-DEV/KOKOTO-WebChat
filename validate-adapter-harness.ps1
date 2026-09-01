param([Parameter(Mandatory=$true)][string]$ProjectRoot)
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $ProjectRoot).Path
$tmp=Join-Path $root '.build-cache\tmp\adapter-harness'
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$jdkFile=Join-Path $tmp 'jdk17.txt'
& (Join-Path $root 'kwc-platform-fabric\select-java.ps1') -Major 17 -OutputFile $jdkFile
if($LASTEXITCODE -ne 0){ exit $LASTEXITCODE }
$javaHome=[IO.File]::ReadAllText($jdkFile,[Text.Encoding]::Default).Trim()
$javac=Join-Path $javaHome 'bin\javac.exe'; $java=Join-Path $javaHome 'bin\java.exe'
if(-not (Test-Path -LiteralPath $javac)){ throw "JDK 17 javac not found: $javac" }
$sources=@(
 'kwc-core\src\main\java\dev\kokoto\webchat\MessageTokenConfig.java',
 'kwc-core\src\main\java\dev\kokoto\webchat\ConfigValues.java',
 'kwc-core\src\main\java\dev\kokoto\webchat\CoreLogger.java',
 'kwc-core\src\main\java\dev\kokoto\webchat\JsonUtil.java',
 'kwc-core\src\main\java\dev\kokoto\webchat\Role.java',
 'kwc-core\src\main\java\dev\kokoto\webchat\ContentFilterRule.java',
 'kwc-adapter-bluemap\src\main\java\dev\kokoto\webchat\adapter\bluemap\BlueMapAdapterHost.java',
 'kwc-adapter-bluemap\src\main\java\dev\kokoto\webchat\adapter\bluemap\BlueMapAdapter.java',
 'kwc-adapter-squaremap\src\main\java\dev\kokoto\webchat\adapter\squaremap\SquaremapAdapterHost.java',
 'kwc-adapter-squaremap\src\main\java\dev\kokoto\webchat\adapter\squaremap\SquaremapAdapter.java',
 'kwc-adapter-dynmap\src\main\java\dev\kokoto\webchat\adapter\dynmap\DynmapAdapterHost.java',
 'kwc-adapter-dynmap\src\main\java\dev\kokoto\webchat\adapter\dynmap\DynmapAdapter.java',
 'kwc-adapter-pl3xmap\src\main\java\dev\kokoto\webchat\adapter\pl3xmap\Pl3xMapAdapterHost.java',
 'kwc-adapter-pl3xmap\src\main\java\dev\kokoto\webchat\adapter\pl3xmap\Pl3xMapAdapter.java',
 'kwc-adapter-liveatlas\src\main\java\dev\kokoto\webchat\adapter\liveatlas\LiveAtlasAdapterHost.java',
 'kwc-adapter-liveatlas\src\main\java\dev\kokoto\webchat\adapter\liveatlas\LiveAtlasAdapter.java',
 'kwc-adapter-unmined\src\main\java\dev\kokoto\webchat\adapter\unmined\UnminedAdapterHost.java',
 'kwc-adapter-unmined\src\main\java\dev\kokoto\webchat\adapter\unmined\UnminedAdapter.java',
 'kwc-adapter-overviewer\src\main\java\dev\kokoto\webchat\adapter\overviewer\OverviewerAdapterHost.java',
 'kwc-adapter-overviewer\src\main\java\dev\kokoto\webchat\adapter\overviewer\OverviewerAdapter.java',
 'validation\adapter-harness\AdapterFilesystemHarness.java'
) | ForEach-Object { Join-Path $root $_ }
& $javac '--release' '17' '-encoding' 'UTF-8' '-d' $tmp @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java '-cp' $tmp 'AdapterFilesystemHarness' $root
exit $LASTEXITCODE
