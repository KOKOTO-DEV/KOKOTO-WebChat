# KWC 파일 안내 / KWC file guide
# map adapter 설치/패키징이 플랫폼별 파일 구조와 idempotency 계약을 지키는지 자동 검증한다.
# Automatically validates map-adapter installation/packaging against platform file-layout and idempotency contracts.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

param(
  [Parameter(Mandatory=$true)][string]$JarPath,
  [Parameter(Mandatory=$true)][ValidateSet('Bukkit','Fabric','NeoForge','Forge')][string]$Platform,
  [string]$MinecraftVersion=''
)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar=(Resolve-Path -LiteralPath $JarPath).Path
$zip=[System.IO.Compression.ZipFile]::OpenRead($jar)
try {
  $names=[System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
  foreach($e in $zip.Entries){ [void]$names.Add($e.FullName) }
  $required=[System.Collections.Generic.List[string]]::new()
  foreach($p in @('standalone/chat.js','standalone/chat.css',
    'reaction-search-aliases.txt')) { $required.Add($p) }
  $adapters=@('bluemap','squaremap','dynmap','liveatlas','unmined','overviewer')
  if($Platform -ne 'Forge') { $adapters += 'pl3xmap' }
  if($Platform -eq 'Bukkit') { $adapters=@('bluemap','squaremap','dynmap','pl3xmap','liveatlas','unmined','overviewer') }
  $className=@{bluemap='BlueMapAdapter';squaremap='SquaremapAdapter';dynmap='DynmapAdapter';pl3xmap='Pl3xMapAdapter';liveatlas='LiveAtlasAdapter';unmined='UnminedAdapter';overviewer='OverviewerAdapter'}
  foreach($a in $adapters){
    $required.Add("dev/kokoto/webchat/adapter/$a/$($className[$a]).class")
    $res = if($a -eq 'bluemap'){'web'}else{$a}
    $required.Add("$res/chat.js"); $required.Add("$res/chat.css")
  }
  $hostPrefix = switch($Platform){ 'Bukkit'{'dev/kokoto/webchat/Bukkit'} 'Fabric'{'dev/kokoto/webchat/fabric/Fabric'} 'NeoForge'{'dev/kokoto/webchat/neoforge/NeoForge'} 'Forge'{'dev/kokoto/webchat/forge/Forge'} }
  foreach($a in $adapters){
    $hostName = switch($a){ 'bluemap'{'BlueMap'} 'squaremap'{'Squaremap'} 'dynmap'{'Dynmap'} 'pl3xmap'{'Pl3xMap'} 'liveatlas'{'LiveAtlas'} 'unmined'{'Unmined'} 'overviewer'{'Overviewer'} }
    $required.Add("$hostPrefix$hostName`AdapterHost.class")
  }
  if($Platform -ne 'Bukkit' -and $MinecraftVersion -in @('26.1.2','26.2')){
    $required.Add('dev/kokoto/webchat/adapter/bluemap/api/BlueMapApiIntegration.class')
  }
  $missing=@($required | Where-Object { -not $names.Contains($_) })
  if($missing.Count -gt 0){
    Write-Error ("Adapter packaging validation failed for {0} {1}: missing {2}" -f $Platform,$MinecraftVersion,($missing -join ', '))
    exit 1
  }
  Write-Host ("[KWC Adapter Gate] PASS {0} {1}: {2} required adapter/runtime entries present." -f $Platform,$MinecraftVersion,$required.Count)
} finally { $zip.Dispose() }
