param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$ToolRoot
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$ToolRoot = [System.IO.Path]::GetFullPath($ToolRoot)
$mavenHome = Join-Path $ToolRoot ("apache-maven-$Version")
$mavenCmd = Join-Path $mavenHome 'bin\mvn.cmd'
$zip = Join-Path $ToolRoot ("apache-maven-$Version-bin.zip")
$shaFile = $zip + '.sha512'
$extractor = Join-Path $PSScriptRoot 'extract-zip-progress-windows.ps1'
$label = "[KWC Bukkit] Maven $Version"

if (Test-Path -LiteralPath $mavenCmd) {
    Write-Host "$label Ready"
    exit 0
}

[void](New-Item -ItemType Directory -Force -Path $ToolRoot)
$sources = @(
    @{
        Zip = "https://dlcdn.apache.org/maven/maven-3/$Version/binaries/apache-maven-$Version-bin.zip"
        Sha = "https://downloads.apache.org/maven/maven-3/$Version/binaries/apache-maven-$Version-bin.zip.sha512"
    },
    @{
        Zip = "https://archive.apache.org/dist/maven/maven-3/$Version/binaries/apache-maven-$Version-bin.zip"
        Sha = "https://archive.apache.org/dist/maven/maven-3/$Version/binaries/apache-maven-$Version-bin.zip.sha512"
    }
)

$downloaded = $false
foreach ($source in $sources) {
    try {
        Write-Host "$label Downloading $($source.Zip)"
        Invoke-WebRequest -UseBasicParsing -Uri $source.Zip -OutFile $zip
        Invoke-WebRequest -UseBasicParsing -Uri $source.Sha -OutFile $shaFile
        $downloaded = $true
        break
    } catch {
        Write-Host "$label Download source failed: $($_.Exception.Message)"
    }
}
if (-not $downloaded) { throw 'Unable to download Apache Maven from official mirrors.' }

$expected = ((Get-Content -LiteralPath $shaFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
$actual = (Get-FileHash -Algorithm SHA512 -LiteralPath $zip).Hash.ToLowerInvariant()
if ($expected -ne $actual) { throw "Maven SHA-512 mismatch. Expected $expected, got $actual" }
Write-Host "$label SHA-512 verified."

$extractRoot = Join-Path $ToolRoot (".maven-extract-$PID-" + [Guid]::NewGuid().ToString('N'))
try {
    [void](New-Item -ItemType Directory -Force -Path $extractRoot)
    & $extractor -ZipPath $zip -DestinationPath $extractRoot -Label $label
    $candidate = Join-Path $extractRoot ("apache-maven-$Version")
    if (-not (Test-Path -LiteralPath (Join-Path $candidate 'bin\mvn.cmd'))) {
        throw 'Maven extraction completed but mvn.cmd is missing.'
    }
    if (Test-Path -LiteralPath $mavenHome) { Remove-Item -LiteralPath $mavenHome -Recurse -Force }
    Move-Item -LiteralPath $candidate -Destination $mavenHome
}
finally {
    if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $shaFile) { Remove-Item -LiteralPath $shaFile -Force -ErrorAction SilentlyContinue }
}

if (-not (Test-Path -LiteralPath $mavenCmd)) { throw 'Maven bootstrap completed but mvn.cmd is missing.' }
Write-Host "$label Ready"
exit 0
