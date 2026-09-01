param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$CacheRoot,
    [Parameter(Mandatory = $true)][string]$DistributionUrl
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$CacheRoot = [System.IO.Path]::GetFullPath($CacheRoot)
$distName = "gradle-$Version"
$distDir = Join-Path $CacheRoot $distName
$gradleBat = Join-Path $distDir 'bin\gradle.bat'
$zipPath = Join-Path $CacheRoot ("$distName-bin.zip")
$partialZip = $zipPath + '.partial'
$lockPath = Join-Path $CacheRoot '.bootstrap.lock'
$extractor = Join-Path $PSScriptRoot 'extract-zip-progress-windows.ps1'
$label = "[KWC Gradle $Version]"

if (Test-Path -LiteralPath $gradleBat) {
    Write-Host "$label Ready"
    exit 0
}

[void](New-Item -ItemType Directory -Force -Path $CacheRoot)
$lock = $null
$waitingShown = $false
try {
    while ($null -eq $lock) {
        try {
            $lock = New-Object System.IO.FileStream($lockPath, [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        } catch [System.IO.IOException] {
            if (-not $waitingShown) {
                Write-Host "$label Waiting for another bootstrap process..."
                $waitingShown = $true
            }
            Start-Sleep -Milliseconds 500
        }
    }

    # Another worker may have completed the same Gradle version while this process waited.
    if (Test-Path -LiteralPath $gradleBat) {
        Write-Host "$label Ready"
        exit 0
    }

    if (-not (Test-Path -LiteralPath $zipPath)) {
        if (Test-Path -LiteralPath $partialZip) { Remove-Item -LiteralPath $partialZip -Force -ErrorAction SilentlyContinue }
        Write-Host "$label Downloading $DistributionUrl"
        Invoke-WebRequest -UseBasicParsing -Uri $DistributionUrl -OutFile $partialZip
        Move-Item -LiteralPath $partialZip -Destination $zipPath -Force
    } else {
        Write-Host "$label Downloading skipped; cached archive is ready."
    }

    $extractRoot = Join-Path $CacheRoot (".extract-$PID-" + [Guid]::NewGuid().ToString('N'))
    try {
        [void](New-Item -ItemType Directory -Force -Path $extractRoot)
        & $extractor -ZipPath $zipPath -DestinationPath $extractRoot -Label $label
        $candidate = Join-Path $extractRoot $distName
        $candidateGradle = Join-Path $candidate 'bin\gradle.bat'
        if (-not (Test-Path -LiteralPath $candidateGradle)) {
            throw "Gradle extraction completed but gradle.bat is missing: $candidateGradle"
        }
        if (Test-Path -LiteralPath $distDir) { Remove-Item -LiteralPath $distDir -Recurse -Force }
        Move-Item -LiteralPath $candidate -Destination $distDir
    }
    finally {
        if (Test-Path -LiteralPath $extractRoot) { Remove-Item -LiteralPath $extractRoot -Recurse -Force -ErrorAction SilentlyContinue }
    }

    if (-not (Test-Path -LiteralPath $gradleBat)) {
        throw "Gradle $Version bootstrap completed but gradle.bat is missing."
    }
    Write-Host "$label Ready"
}
finally {
    if ($null -ne $lock) { $lock.Dispose() }
    if (Test-Path -LiteralPath $partialZip) { Remove-Item -LiteralPath $partialZip -Force -ErrorAction SilentlyContinue }
}
