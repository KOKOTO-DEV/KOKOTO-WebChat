# KWC 파일 안내 / KWC file guide
# Windows 빌드에 필요한 Maven을 프로젝트 로컬 cache에 준비해 시스템 전역 설치 상태에 대한 의존을 줄인다.
# Bootstraps Maven into the project-local cache for Windows builds, reducing dependence on global machine installation.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

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
