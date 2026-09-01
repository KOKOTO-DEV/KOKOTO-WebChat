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

Write-Host ('FORGE BUILD CONFIG PASS: {0} FG7 targets use CP25/CP29-compatible ForgeGradle range, Xmx3G, JDK 25 preflight, and unrestricted Gradle toolchain detection.' -f $fg7Targets.Count)
exit 0
