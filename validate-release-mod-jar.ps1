# KWC 파일 안내 / KWC file guide
# KWC Windows 개발/검증 흐름을 자동화하는 PowerShell 스크립트다.
# PowerShell script automating part of the KWC Windows development/validation workflow.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

param(
  [Parameter(Mandatory=$true)][string]$JarPath,
  [Parameter(Mandatory=$true)][ValidateSet('Fabric','NeoForge','Forge')][string]$Platform,
  [Parameter(Mandatory=$true)][string]$MinecraftVersion
)

$ErrorActionPreference = 'Stop'
$ExpectedVersion = '5.3.1'
$ExpectedModId = 'kokoto_webchat'

if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
  throw "JAR not found: $JarPath"
}

$resolved = (Resolve-Path -LiteralPath $JarPath).Path
$expectedFileName = "KOKOTO-WebChat-$ExpectedVersion-$Platform-$MinecraftVersion.jar"
$actualFileName = [System.IO.Path]::GetFileName($resolved)
if ($actualFileName -cne $expectedFileName) {
  throw "Release JAR filename mismatch: expected '$expectedFileName', got '$actualFileName'."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($resolved)
try {
  $entryGroups = @{}
  foreach ($entry in $zip.Entries) {
    $key = $entry.FullName.ToLowerInvariant()
    if (-not $entryGroups.ContainsKey($key)) { $entryGroups[$key] = @() }
    $entryGroups[$key] += $entry
  }

  function Get-EntryCount([string]$name) {
    $key = $name.ToLowerInvariant()
    if (-not $entryGroups.ContainsKey($key)) { return 0 }
    return @($entryGroups[$key]).Count
  }

  function Get-SingleEntry([string]$name) {
    $count = Get-EntryCount $name
    if ($count -eq 0) { return $null }
    if ($count -ne 1) { throw "Duplicate JAR entry '$name' found $count times." }
    return @($entryGroups[$name.ToLowerInvariant()])[0]
  }

  function Read-EntryText([string]$name) {
    $entry = Get-SingleEntry $name
    if ($null -eq $entry) { throw "Missing required JAR entry: $name" }
    $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8, $true)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  }

  function Require-Entry([string]$name) {
    if ((Get-EntryCount $name) -ne 1) {
      $count = Get-EntryCount $name
      if ($count -eq 0) { throw "Missing required JAR entry: $name" }
      throw "Duplicate required JAR entry '$name' found $count times."
    }
  }

  function Reject-Entry([string]$name) {
    $count = Get-EntryCount $name
    if ($count -gt 0) { throw "Unexpected JAR entry present: $name" }
  }

  function Test-BundledDependency([string]$directClass, [string]$jarToken) {
    if ((Get-EntryCount $directClass) -gt 0) { return $true }
    $token = $jarToken.ToLowerInvariant()
    foreach ($entry in $zip.Entries) {
      $n = $entry.FullName.ToLowerInvariant()
      if ($n.EndsWith('.jar') -and $n.Contains($token)) { return $true }
    }
    return $false
  }

  # Release archives must not contain source/build helper files. Nested dependency
  # JARs are represented only as .jar entries here, so their internals are not
  # incorrectly rejected by this outer-archive check.
  $forbidden = @(
    '(?i)(^|/)src/',
    '(?i)(^|/)\.git/',
    '(?i)(^|/)\.github/',
    '(?i)(^|/)build\.gradle(?:\.kts)?$',
    '(?i)(^|/)settings\.gradle(?:\.kts)?$',
    '(?i)(^|/)gradle\.properties$',
    '(?i)\.(?:java|kt|groovy|ps1|bat|cmd)$'
  )
  foreach ($entry in $zip.Entries) {
    foreach ($pattern in $forbidden) {
      if ($entry.FullName -match $pattern) {
        throw "Forbidden development/source file in release JAR: $($entry.FullName)"
      }
    }
  }

  # Common runtime payload required by every mod-loader JAR.
  Require-Entry 'standalone/chat.js'
  Require-Entry 'standalone/chat.css'
  Require-Entry 'reaction-search-aliases.txt'
  if (-not (Test-BundledDependency 'org/yaml/snakeyaml/Yaml.class' 'snakeyaml')) {
    throw 'Missing bundled runtime dependency: SnakeYAML.'
  }
  if (-not (Test-BundledDependency 'org/sqlite/JDBC.class' 'sqlite-jdbc')) {
    throw 'Missing bundled runtime dependency: SQLite JDBC.'
  }

  switch ($Platform) {
    'Fabric' {
      Require-Entry 'fabric.mod.json'
      Reject-Entry 'META-INF/mods.toml'
      Reject-Entry 'META-INF/neoforge.mods.toml'
      Require-Entry 'dev/kokoto/webchat/fabric/KwcFabricMod.class'

      $metadataText = Read-EntryText 'fabric.mod.json'
      if ($metadataText.Contains('${')) { throw 'Fabric metadata contains an unresolved template placeholder.' }
      try { $metadata = $metadataText | ConvertFrom-Json } catch { throw "Invalid fabric.mod.json: $($_.Exception.Message)" }
      if ([string]$metadata.id -cne $ExpectedModId) { throw "Fabric mod id mismatch: '$($metadata.id)'." }
      if ([string]$metadata.version -cne $ExpectedVersion) { throw "Fabric version mismatch: '$($metadata.version)'." }
      if ([string]$metadata.depends.minecraft -cne $MinecraftVersion) {
        throw "Fabric Minecraft target mismatch: metadata='$($metadata.depends.minecraft)', expected='$MinecraftVersion'."
      }
      $mainEntrypoints = @($metadata.entrypoints.main)
      if ($mainEntrypoints -notcontains 'dev.kokoto.webchat.fabric.KwcFabricMod') {
        throw 'Fabric main entrypoint dev.kokoto.webchat.fabric.KwcFabricMod is missing.'
      }
    }

    'Forge' {
      Require-Entry 'META-INF/mods.toml'
      Reject-Entry 'META-INF/neoforge.mods.toml'
      Require-Entry 'dev/kokoto/webchat/forge/KwcForgeMod.class'
      Require-Entry 'kwc-build.properties'

      $buildMetadata = Read-EntryText 'kwc-build.properties'
      if ($buildMetadata.Contains('${')) { throw 'Forge kwc-build.properties contains an unresolved template placeholder.' }
      if ($buildMetadata -notmatch ('(?m)^minecraft\.version=' + [regex]::Escape($MinecraftVersion) + '\s*$')) {
        throw "Forge kwc-build.properties target mismatch; expected minecraft.version=$MinecraftVersion."
      }

      $metadataText = Read-EntryText 'META-INF/mods.toml'
      if ($metadataText.Contains('${')) { throw 'Forge mods.toml contains an unresolved template placeholder.' }
      $checks = [ordered]@{
        'modLoader="javafml"' = '(?m)^\s*modLoader\s*=\s*"javafml"\s*(?:#.*)?$'
        'modId="kokoto_webchat"' = '(?m)^\s*modId\s*=\s*"kokoto_webchat"\s*(?:#.*)?$'
        'version' = ('(?m)^\s*version\s*=\s*"' + [regex]::Escape($ExpectedVersion) + '"\s*(?:#.*)?$')
        'Forge dependency' = '(?ms)\[\[dependencies\.kokoto_webchat\]\].*?modId\s*=\s*"forge"'
        'exact Minecraft dependency' = ('(?ms)\[\[dependencies\.kokoto_webchat\]\]\s*modId\s*=\s*"minecraft".*?versionRange\s*=\s*"\[' + [regex]::Escape($MinecraftVersion) + ',')
      }
      foreach ($check in $checks.GetEnumerator()) {
        if ($metadataText -notmatch $check.Value) { throw "Forge metadata validation failed: missing $($check.Key)." }
      }
    }

    'NeoForge' {
      $modsCount = Get-EntryCount 'META-INF/mods.toml'
      $neoModsCount = Get-EntryCount 'META-INF/neoforge.mods.toml'
      if (($modsCount + $neoModsCount) -ne 1) {
        throw "NeoForge must contain exactly one loader metadata file; mods.toml=$modsCount, neoforge.mods.toml=$neoModsCount."
      }
      $metadataName = if ($neoModsCount -eq 1) { 'META-INF/neoforge.mods.toml' } else { 'META-INF/mods.toml' }
      Require-Entry 'dev/kokoto/webchat/neoforge/KwcNeoForgeMod.class'
      Require-Entry 'kwc-build.properties'

      $buildMetadata = Read-EntryText 'kwc-build.properties'
      if ($buildMetadata.Contains('${')) { throw 'NeoForge kwc-build.properties contains an unresolved template placeholder.' }
      if ($buildMetadata -notmatch ('(?m)^minecraft\.version=' + [regex]::Escape($MinecraftVersion) + '\s*$')) {
        throw "NeoForge kwc-build.properties target mismatch; expected minecraft.version=$MinecraftVersion."
      }

      $metadataText = Read-EntryText $metadataName
      if ($metadataText.Contains('${')) { throw "NeoForge $metadataName contains an unresolved template placeholder." }
      $checks = [ordered]@{
        'modLoader="javafml"' = '(?m)^\s*modLoader\s*=\s*"javafml"\s*(?:#.*)?$'
        'loaderVersion="[1,)"' = '(?m)^\s*loaderVersion\s*=\s*"\[1,\)"\s*(?:#.*)?$'
        'modId="kokoto_webchat"' = '(?m)^\s*modId\s*=\s*"kokoto_webchat"\s*(?:#.*)?$'
        'version' = ('(?m)^\s*version\s*=\s*"' + [regex]::Escape($ExpectedVersion) + '"\s*(?:#.*)?$')
        'NeoForge dependency' = '(?ms)\[\[dependencies\.kokoto_webchat\]\].*?modId\s*=\s*"neoforge"'
        'exact Minecraft dependency' = ('(?ms)\[\[dependencies\.kokoto_webchat\]\]\s*modId\s*=\s*"minecraft".*?versionRange\s*=\s*"\[' + [regex]::Escape($MinecraftVersion) + '\]"')
      }
      foreach ($check in $checks.GetEnumerator()) {
        if ($metadataText -notmatch $check.Value) { throw "NeoForge metadata validation failed: missing $($check.Key) in $metadataName." }
      }
    }
  }

  Write-Host ("[KWC Release JAR] PASS {0} {1}: filename, metadata, exact target, entrypoint, runtime libs, frontend resources, and archive hygiene verified." -f $Platform,$MinecraftVersion)
}
finally {
  $zip.Dispose()
}
exit 0
