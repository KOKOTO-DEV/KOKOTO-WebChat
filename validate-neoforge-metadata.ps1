param(
    [Parameter(Mandatory = $true)][string]$JarPath,
    [Parameter(Mandatory = $true)][string]$MinecraftVersion,
    [Parameter(Mandatory = $true)][string]$ProjectRoot
)

$ErrorActionPreference = 'Stop'
$JarPath = [System.IO.Path]::GetFullPath($JarPath)
$ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
$targetBuild = Join-Path $ProjectRoot ("kwc-platform-neoforge\targets\{0}\build.gradle" -f $MinecraftVersion)
if (-not (Test-Path -LiteralPath $JarPath)) { throw "NeoForge JAR not found: $JarPath" }
if (-not (Test-Path -LiteralPath $targetBuild)) { throw "NeoForge target build.gradle not found: $targetBuild" }

$buildText = [IO.File]::ReadAllText($targetBuild)
if ($buildText -match 'templates-new') {
    $expectedEntry = 'META-INF/neoforge.mods.toml'
    $unexpectedEntry = 'META-INF/mods.toml'
} elseif ($buildText -match 'templates-old') {
    $expectedEntry = 'META-INF/mods.toml'
    $unexpectedEntry = 'META-INF/neoforge.mods.toml'
} else {
    throw "Cannot determine NeoForge metadata template for Minecraft $MinecraftVersion."
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
try {
    $entry = $archive.GetEntry($expectedEntry)
    if ($null -eq $entry) { throw "Missing required $expectedEntry in $(Split-Path -Leaf $JarPath)." }
    if ($null -ne $archive.GetEntry($unexpectedEntry)) { throw "Unexpected alternate metadata file $unexpectedEntry is also present." }
    $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8, $true)
    try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
}
finally {
    $archive.Dispose()
}

$checks = [ordered]@{
    'modLoader="javafml"' = '(?m)^\s*modLoader\s*=\s*"javafml"\s*(?:#.*)?$'
    'loaderVersion="[1,)"' = '(?m)^\s*loaderVersion\s*=\s*"\[1,\)"\s*(?:#.*)?$'
    'license="MIT"' = '(?m)^\s*license\s*=\s*"MIT"\s*(?:#.*)?$'
    'modId="kokoto_webchat"' = '(?m)^\s*modId\s*=\s*"kokoto_webchat"\s*(?:#.*)?$'
    'version="5.1.0"' = '(?m)^\s*version\s*=\s*"5\.1\.0"\s*(?:#.*)?$'
    'NeoForge dependency' = '(?ms)\[\[dependencies\.kokoto_webchat\]\].*?modId\s*=\s*"neoforge"'
    'exact Minecraft dependency' = ('(?m)^\s*versionRange\s*=\s*"\[' + [regex]::Escape($MinecraftVersion) + '\]"\s*(?:#.*)?$')
}
foreach ($item in $checks.GetEnumerator()) {
    if ($text -notmatch $item.Value) { throw "NeoForge $MinecraftVersion metadata validation failed: missing $($item.Key) in $expectedEntry." }
}
if ($text.Contains('${')) { throw "NeoForge $MinecraftVersion metadata validation failed: unresolved template placeholder remains in $expectedEntry." }

Write-Host ("[KWC NeoForge] Metadata PASS: {0} -> {1}" -f $MinecraftVersion, $expectedEntry)
exit 0
