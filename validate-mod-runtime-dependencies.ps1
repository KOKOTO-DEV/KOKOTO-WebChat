param(
  [Parameter(Mandatory=$true)][string]$JarPath,
  [Parameter(Mandatory=$true)][ValidateSet('Fabric','NeoForge','Forge')][string]$Platform,
  [Parameter(Mandatory=$true)][string]$MinecraftVersion
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
  throw "JAR not found: $JarPath"
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$resolved = (Resolve-Path -LiteralPath $JarPath).Path
$zip = [System.IO.Compression.ZipFile]::OpenRead($resolved)
try {
  $names = @($zip.Entries | ForEach-Object { $_.FullName.ToLowerInvariant() })
  function Test-BundledDependency([string]$directClass, [string]$jarToken) {
    $direct = $directClass.ToLowerInvariant()
    $token = $jarToken.ToLowerInvariant()
    if ($names -contains $direct) { return $true }
    foreach ($n in $names) {
      if ($n.EndsWith('.jar') -and $n.Contains($token)) { return $true }
    }
    return $false
  }
  $missing = [System.Collections.Generic.List[string]]::new()
  if (-not (Test-BundledDependency 'org/yaml/snakeyaml/Yaml.class' 'snakeyaml')) { $missing.Add('SnakeYAML') }
  if (-not (Test-BundledDependency 'org/sqlite/JDBC.class' 'sqlite-jdbc')) { $missing.Add('SQLite JDBC') }
  if ($missing.Count -gt 0) {
    throw ("{0} {1} runtime dependency packaging FAIL: missing {2} in {3}" -f $Platform,$MinecraftVersion,($missing -join ', '),[System.IO.Path]::GetFileName($JarPath))
  }
  Write-Host ("[KWC Runtime Deps] PASS {0} {1}: SnakeYAML + SQLite JDBC packaged" -f $Platform,$MinecraftVersion)
}
finally {
  $zip.Dispose()
}
