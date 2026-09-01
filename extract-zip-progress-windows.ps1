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
