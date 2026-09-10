# KWC 파일 안내 / KWC file guide
# KWC Windows 개발/검증 흐름을 자동화하는 PowerShell 스크립트다.
# PowerShell script automating part of the KWC Windows development/validation workflow.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

param(
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [Parameter(Mandatory = $true)][string]$DestinationPath,
    [string]$Label = 'Archive'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$ZipPath = [System.IO.Path]::GetFullPath($ZipPath)
$DestinationPath = [System.IO.Path]::GetFullPath($DestinationPath)

if (-not (Test-Path -LiteralPath $ZipPath)) {
    throw "ZIP archive not found: $ZipPath"
}
if (-not (Test-Path -LiteralPath $DestinationPath)) {
    [void](New-Item -ItemType Directory -Force -Path $DestinationPath)
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$trimChars = [char[]]@([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$destinationRoot = $DestinationPath.TrimEnd($trimChars) + [IO.Path]::DirectorySeparatorChar
$archive = [System.IO.Compression.ZipFile]::OpenRead($ZipPath)
try {
    $entries = @($archive.Entries)
    $total = $entries.Count
    $nextPercent = 10
    if ($total -eq 0) {
        Write-Host ("{0} Extracting 100%" -f $Label)
        return
    }

    for ($index = 0; $index -lt $total; $index++) {
        $entry = $entries[$index]
        $relative = $entry.FullName.Replace('/', [IO.Path]::DirectorySeparatorChar)
        $target = [System.IO.Path]::GetFullPath((Join-Path $DestinationPath $relative))
        if (-not $target.StartsWith($destinationRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Unsafe ZIP entry path: $($entry.FullName)"
        }

        if ([string]::IsNullOrEmpty($entry.Name)) {
            if (-not (Test-Path -LiteralPath $target)) {
                [void](New-Item -ItemType Directory -Force -Path $target)
            }
        } else {
            $parent = Split-Path -Parent $target
            if (-not (Test-Path -LiteralPath $parent)) {
                [void](New-Item -ItemType Directory -Force -Path $parent)
            }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }

        $pct = [int][Math]::Floor((($index + 1) * 100.0) / $total)
        while ($nextPercent -le 100 -and $pct -ge $nextPercent) {
            Write-Host ("{0} Extracting {1}%" -f $Label, $nextPercent)
            $nextPercent += 10
        }
    }
}
finally {
    $archive.Dispose()
}
