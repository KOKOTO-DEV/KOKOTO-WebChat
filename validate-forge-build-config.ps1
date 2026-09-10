# KWC 파일 안내 / KWC file guide
# KWC Windows 개발/검증 흐름을 자동화하는 PowerShell 스크립트다.
# PowerShell script automating part of the KWC Windows development/validation workflow.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

param(
    [Parameter(Mandatory = $true)][string]$ProjectRoot
)
$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath($ProjectRoot)
$forge = Join-Path $root 'kwc-platform-forge'
$targetBat = Join-Path $forge 'build-target.bat'
$targetSh = Join-Path $forge 'build-target.sh'
$allBat = Join-Path $forge 'build-all.bat'
$allSh = Join-Path $forge 'build-all.sh'
$fg7Targets = @('1.21.1','1.21.3','1.21.4','1.21.5','1.21.8','1.21.10','1.21.11','26.1.2','26.2')
$forgeVersions = [ordered]@{
    '1.18.2'  = '40.2.24'
    '1.19.2'  = '43.4.2'
    '1.19.4'  = '45.3.12'
    '1.20.1'  = '47.4.23'
    '1.20.2'  = '48.1.0'
    '1.20.4'  = '49.2.9'
    '1.20.6'  = '50.2.0'
    '1.21.1'  = '52.1.16'
    '1.21.3'  = '53.1.0'
    '1.21.4'  = '54.1.14'
    '1.21.5'  = '55.1.0'
    '1.21.8'  = '58.1.0'
    '1.21.10' = '60.1.0'
    '1.21.11' = '61.2.0'
    '26.1.2'  = '64.1.0'
    '26.2'    = '65.1.0'
}

function Read-Text([string]$Path) {
    return [System.IO.File]::ReadAllText($Path)
}
function Require-Text([string]$Path, [string]$Pattern, [string]$Label) {
    $text = Read-Text $Path
    if ($text -notmatch $Pattern) { throw "$Label missing in $Path" }
}
function Reject-Text([string]$Path, [string]$Pattern, [string]$Label) {
    $text = Read-Text $Path
    if ($text -match $Pattern) { throw "$Label must not be present in $Path" }
}

# Exact Forge artifact versions are release pins, not moving 'latest' values.
# These versions are the compatibility matrix already validated before 5.2.0.
# Guard both dependency resolution and generated loader metadata so a version bump
# cannot silently change one side or invent a non-existent userdev artifact.
foreach ($entry in $forgeVersions.GetEnumerator()) {
    $mc = [string]$entry.Key
    $forgeVersion = [string]$entry.Value
    $buildPath = Join-Path (Join-Path (Join-Path $forge 'targets') $mc) 'build.gradle'
    $build = Read-Text $buildPath
    $dependency = [regex]::Escape("net.minecraftforge:forge:$mc-$forgeVersion")
    $metadata = [regex]::Escape("forge_version:'$forgeVersion'")
    if ($build -notmatch $dependency) { throw "Forge dependency pin mismatch for $mc; expected $forgeVersion" }
    if ($build -notmatch $metadata) { throw "Forge metadata pin mismatch for $mc; expected $forgeVersion" }
}

# 1.21.x Forge starts Gradle with JDK 21, while Forge Mavenizer may request a
# separate JDK 25 toolchain. Do not hide other installed JDKs from Gradle.
Reject-Text $targetBat 'org\.gradle\.java\.home=' 'Forced Windows Gradle JVM pin'
Reject-Text $targetBat 'org\.gradle\.java\.installations\.auto-detect=false' 'Disabled Windows Java toolchain auto-detection'
Reject-Text $targetBat 'org\.gradle\.java\.installations\.paths=' 'Restricted Windows Java toolchain path'
Reject-Text $targetSh 'org\.gradle\.java\.home=' 'Forced Unix Gradle JVM pin'
Reject-Text $targetSh 'org\.gradle\.java\.installations\.auto-detect=false' 'Disabled Unix Java toolchain auto-detection'
Reject-Text $targetSh 'org\.gradle\.java\.installations\.paths=' 'Restricted Unix Java toolchain path'

# Full Forge matrix intentionally preflights all JDKs that are needed either by
# the target build itself or by ForgeGradle/Mavenizer toolchains.
Require-Text $allBat 'JDK 17, 21 and 25' 'Windows JDK preflight banner'
Require-Text $allBat 'call :checkJdk 25' 'Windows JDK 25 preflight'
Require-Text $allSh 'for v in 17 21 25' 'Unix JDK 25 preflight'

foreach ($mc in $fg7Targets) {
    $dir = Join-Path (Join-Path $forge 'targets') $mc
    $build = Read-Text (Join-Path $dir 'build.gradle')
    $props = Read-Text (Join-Path $dir 'gradle.properties')
    if ($build -notmatch "id 'net\.minecraftforge\.gradle' version '\[7\.0\.17,8\)'") {
        throw "ForgeGradle compatibility range mismatch for $mc"
    }
    if ($props -notmatch 'org\.gradle\.jvmargs=-Xmx3G') {
        throw "ForgeGradle JVM heap mismatch for $mc"
    }
}

Write-Host ('FORGE BUILD CONFIG PASS: {0} exact Forge pins + {1} FG7 targets validated.' -f $forgeVersions.Count, $fg7Targets.Count)
exit 0
