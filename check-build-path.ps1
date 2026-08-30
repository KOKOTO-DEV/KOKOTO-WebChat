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
