# KWC 파일 안내 / KWC file guide
# 병렬 빌드 창 하나에서 특정 loader target 목록을 순서대로 빌드하고 progress 상태/로그를 기록하는 worker다.
# Worker that builds one loader target list sequentially in a parallel build window while writing progress state and logs.
# 실패 시 부분 산출물을 최종 릴리스로 오인하지 않도록 exit code와 검증 marker를 유지한다.
# Preserve exit codes and validation markers so partial output cannot be mistaken for a final release.

param(
    [Parameter(Mandatory = $true)][string]$WorkerPath,
    [Parameter(Mandatory = $true)][string]$LogPath,
    [Parameter(Mandatory = $true)][string]$ExitCodePath,
    [Parameter(Mandatory = $true)][string]$WindowTitle,
    [Parameter(Mandatory = $true)][string]$ProjectRoot,
    [string]$Platform = 'worker'
)

$ErrorActionPreference = 'Stop'
try { $Host.UI.RawUI.WindowTitle = $WindowTitle } catch { }
$WorkerPath = [System.IO.Path]::GetFullPath($WorkerPath)
$LogPath = [System.IO.Path]::GetFullPath($LogPath)
$ExitCodePath = [System.IO.Path]::GetFullPath($ExitCodePath)
$ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
$exitTmp = $ExitCodePath + '.tmp'
$logDir = Split-Path -Parent $LogPath
if (-not (Test-Path -LiteralPath $logDir)) { [void](New-Item -ItemType Directory -Force -Path $logDir) }

function Test-GradleCacheFailure([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return $false }

    # Only retry failures that clearly point at Gradle's own cache/workspace state.
    # Source compilation, dependency resolution, Java errors, etc. must remain hard failures.
    $patterns = @(
        'Could not read workspace metadata from .*?[\\/]caches[\\/][^\\/]+[\\/]transforms[\\/].*?metadata\.bin',
        'Could not move temporary workspace .*?[\\/]caches[\\/]',
        'Timeout waiting to lock .*?(?:Gradle|cache|journal|file hash|artifact)',
        'Could not acquire lock .*?(?:Gradle|cache|journal|file hash|artifact)',
        '(?:Gradle|cache|journal|file hash|artifact).*?is locked by another Gradle instance',
        '(?:\.build-cache[\\/]gradle|GRADLE_USER_HOME).*?(?:being used by another process|Access is denied|sharing violation)'
    )
    foreach ($pattern in $patterns) {
        if ($text -match $pattern) { return $true }
    }
    return $false
}

function Test-TransientNetworkFailure([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return $false }

    # Mavenizer and dependency downloads use their own HTTP clients, outside some
    # of Gradle's normal retry handling. Retry only unmistakably transient transport
    # failures; dependency/version/source errors remain hard failures.
    $patterns = @(
        'java\.net\.SocketException:\s*Connection reset',
        'java\.io\.IOException:\s*Connection reset',
        'java\.net\.(?:ConnectException|SocketTimeoutException)',
        '(?:Read timed out|Connection timed out|Connection refused)',
        '(?:Could not GET|Could not HEAD).*?(?:timed out|reset|temporar)',
        '(?:Remote host|peer).*?(?:closed|reset).*?connection'
    )
    foreach ($pattern in $patterns) {
        if ($text -match $pattern) { return $true }
    }
    return $false
}

function Invoke-WorkerAttempt(
    [System.IO.StreamWriter]$writer,
    [string]$gradleUserHome,
    [string]$attemptLabel
) {
    if ($attemptLabel) {
        $line = "[KWC worker] $attemptLabel"
        Write-Host $line
        $writer.WriteLine($line)
    }

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $env:ComSpec
    $psi.Arguments = '/d /c ""{0}""' -f $WorkerPath
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $false
    if (-not [string]::IsNullOrWhiteSpace($gradleUserHome)) {
        $psi.EnvironmentVariables['KWC_GRADLE_USER_HOME'] = $gradleUserHome
        $psi.EnvironmentVariables['GRADLE_USER_HOME'] = $gradleUserHome
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    if (-not $process.Start()) { throw "Failed to start worker: $WorkerPath" }
    try {
        while (($line = $process.StandardOutput.ReadLine()) -ne $null) {
            Write-Host $line
            $writer.WriteLine($line)
        }
        $process.WaitForExit()
        return [int]$process.ExitCode
    }
    finally {
        $process.Dispose()
    }
}

$rc = 1
$writer = $null
try {
    $writer = New-Object System.IO.StreamWriter($LogPath, $false, (New-Object System.Text.UTF8Encoding($false)))
    $writer.AutoFlush = $true

    $rc = Invoke-WorkerAttempt $writer $null ''
    if ($rc -ne 0) {
        $writer.Flush()
        $firstAttemptLog = ''
        try { $firstAttemptLog = [System.IO.File]::ReadAllText($LogPath) } catch { }

        if (Test-GradleCacheFailure $firstAttemptLog) {
            $safePlatform = ($Platform -replace '[^A-Za-z0-9_.-]', '_')
            $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
            $recoveryHome = Join-Path $ProjectRoot ('.build-cache\gradle-recovery\{0}-{1}-{2}' -f $safePlatform, $stamp, $PID)
            [void](New-Item -ItemType Directory -Force -Path $recoveryHome)

            $notice = '[KWC worker] Detected a Gradle cache/workspace corruption or lock failure.'
            Write-Host $notice; $writer.WriteLine($notice)
            $notice = '[KWC worker] Primary Gradle cache will NOT be deleted automatically; it may still be locked by Explorer, antivirus, or another process.'
            Write-Host $notice; $writer.WriteLine($notice)
            $notice = "[KWC worker] Retrying this platform once with isolated Gradle cache: $recoveryHome"
            Write-Host $notice; $writer.WriteLine($notice)

            $rc = Invoke-WorkerAttempt $writer $recoveryHome 'Gradle cache recovery retry 1/1'
            if ($rc -eq 0) {
                $notice = '[KWC worker] Gradle cache recovery PASS. The original Gradle cache was left untouched.'
                Write-Host $notice; $writer.WriteLine($notice)
            } else {
                $notice = '[KWC worker] Gradle cache recovery retry FAILED. Treating this as a normal build failure.'
                Write-Host $notice; $writer.WriteLine($notice)
            }
        } elseif (Test-TransientNetworkFailure $firstAttemptLog) {
            $notice = '[KWC worker] Detected a transient dependency/Mavenizer network failure.'
            Write-Host $notice; $writer.WriteLine($notice)
            $notice = '[KWC worker] Retrying this platform once with the same cache after 5 seconds.'
            Write-Host $notice; $writer.WriteLine($notice)
            Start-Sleep -Seconds 5

            $rc = Invoke-WorkerAttempt $writer $null 'Transient network retry 1/1'
            if ($rc -eq 0) {
                $notice = '[KWC worker] Transient network retry PASS.'
                Write-Host $notice; $writer.WriteLine($notice)
            } else {
                $notice = '[KWC worker] Transient network retry FAILED. Treating this as a normal build failure.'
                Write-Host $notice; $writer.WriteLine($notice)
            }
        }
    }
}
catch {
    $message = "[KWC worker] ERROR: $($_.Exception.Message)"
    Write-Host $message
    if ($null -ne $writer) { $writer.WriteLine($message) }
    $rc = 1
}
finally {
    if ($null -ne $writer) { $writer.Dispose() }
    [System.IO.File]::WriteAllText($exitTmp, [string]$rc, [System.Text.Encoding]::ASCII)
    Move-Item -LiteralPath $exitTmp -Destination $ExitCodePath -Force
}

exit $rc
