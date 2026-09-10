# KWC 파일 안내 / KWC file guide
# KWC Windows 개발/검증 흐름을 자동화하는 PowerShell 스크립트다.
# PowerShell script automating part of the KWC Windows development/validation workflow.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

param(
    [Parameter(Mandatory = $true)]
    [string]$Root
)

$ErrorActionPreference = 'Stop'

# Empirical Windows build-path guard.
# A complete 45-artifact build succeeded from:
#   Z:\KOKOTO-WebChat-5.0.0-source  (root length 30)
# The longest observed full path was 257 characters, so the longest measured
# root-relative build/cache suffix is 227 characters. Keep 43 characters of
# additional headroom and stop before the estimated full path exceeds 300.
$MeasuredRootLength = 30
$MeasuredLongestFullPath = 257
$MeasuredSuffixLength = $MeasuredLongestFullPath - $MeasuredRootLength
$SafeFullPathLimit = 300
$recommendedMaxRoot = $SafeFullPathLimit - $MeasuredSuffixLength

# All KWC Windows build entry points pass an absolute path produced by cmd.exe
# (%%~fI). Do not call GetFullPath()/Resolve-Path here: on Windows PowerShell or
# older .NET those APIs can fail before this guard gets a chance to report that
# an intentionally long path is unsafe. The preflight only needs the absolute
# path string and its length.
$rawRoot = $Root.Trim()
if ([string]::IsNullOrWhiteSpace($rawRoot)) {
    Write-Error '[KWC Build] Project root path is empty.'
    exit 1
}

$isDriveAbsolute = $rawRoot -match '^[A-Za-z]:[\\/]'
$isUncAbsolute = $rawRoot -match '^\\\\[^\\]+\\[^\\]+'
if (-not ($isDriveAbsolute -or $isUncAbsolute)) {
    Write-Error "[KWC Build] Project root must be an absolute Windows path: $rawRoot"
    exit 1
}

$rootLength = $rawRoot.Length
$estimatedLongest = $rootLength + $MeasuredSuffixLength

Write-Host "[KWC Build] Project root length: $rootLength"
Write-Host "[KWC Build] Estimated longest build path: $estimatedLongest / $SafeFullPathLimit"

if ($estimatedLongest -gt $SafeFullPathLimit) {
    Write-Error @"
[KWC Build] Project path is too long for the validated Windows build layout.
[KWC Build] Root: $rawRoot
[KWC Build] Maximum recommended project-root length: $recommendedMaxRoot characters.
[KWC Build] Move the COMPLETE source tree to a shorter path, for example:
[KWC Build]   Z:\KWC
[KWC Build] Build aborted before Maven/Gradle starts.
"@
    exit 1
}

exit 0
