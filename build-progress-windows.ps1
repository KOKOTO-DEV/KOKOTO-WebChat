param(
    [Parameter(Mandatory = $true)][string]$Root,
    [Parameter(Mandatory = $true)][string]$LogDir,
    [Parameter(Mandatory = $true)][string]$Platforms,
    [int]$Parallel = 0
)

$ErrorActionPreference = 'Stop'
$Root = [System.IO.Path]::GetFullPath($Root)
$LogDir = [System.IO.Path]::GetFullPath($LogDir)
$Validator = Join-Path $Root 'validate-release-windows.bat'
$WorkerRunner = Join-Path $Root 'build-worker-windows.ps1'
$script:OpenWorkerWindows = ($Parallel -ne 0)
$Selected = @($Platforms.Split(',') | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ })

$definitions = [ordered]@{
    bukkit   = @{ Label = 'B';   FullLabel = 'Bukkit';   Expected = 1;  Log = '01-bukkit.log' }
    fabric   = @{ Label = 'Fab'; FullLabel = 'Fabric';   Expected = 16; Log = '02-fabric-all.log' }
    neoforge = @{ Label = 'Neo'; FullLabel = 'NeoForge'; Expected = 12; Log = '03-neoforge-all.log' }
    forge    = @{ Label = 'For'; FullLabel = 'Forge';    Expected = 16; Log = '04-forge-all.log' }
}

foreach ($name in $Selected) {
    if (-not $definitions.Contains($name)) {
        throw "Unknown build platform '$name'."
    }
}

$states = @()
foreach ($name in $Selected) {
    $def = $definitions[$name]
    $states += [pscustomobject]@{
        Name = $name
        Label = $def.Label
        FullLabel = $def.FullLabel
        Expected = [int]$def.Expected
        Completed = New-Object 'System.Collections.Generic.HashSet[string]'
        Current = ''
        TerminalSuccess = $false
        Status = 'PENDING'
        Process = $null
        Reader = $null
        Stream = $null
        LogPath = Join-Path $LogDir $def.Log
        WorkerPath = Join-Path $LogDir ("_worker-{0}.bat" -f $name)
        WindowPath = Join-Path $LogDir ("_window-{0}.bat" -f $name)
        ExitCodePath = Join-Path $LogDir ("_worker-{0}.exitcode" -f $name)
        ExitCode = $null
    }
}

function Write-WorkerFile([object]$state) {
    if ($script:OpenWorkerWindows) {
        $workerLines = @(
            '@echo off',
            'setlocal',
            ('call "{0}" --internal-worker {1} 2>&1' -f $Validator, $state.Name),
            'exit /b %ERRORLEVEL%'
        )
        [System.IO.File]::WriteAllLines($state.WorkerPath, $workerLines, [System.Text.Encoding]::Default)

        $title = "KWC 5.2.0 - $($state.FullLabel) build"
        $windowLines = @(
            '@echo off',
            ('title {0}' -f $title),
            ('powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "{0}" -WorkerPath "{1}" -LogPath "{2}" -ExitCodePath "{3}" -WindowTitle "{4}" -ProjectRoot "{5}" -Platform "{6}"' -f $WorkerRunner, $state.WorkerPath, $state.LogPath, $state.ExitCodePath, $title, $Root, $state.Name),
            'exit /b %ERRORLEVEL%'
        )
        [System.IO.File]::WriteAllLines($state.WindowPath, $windowLines, [System.Text.Encoding]::Default)
    } else {
        # The PowerShell worker runner owns log capture and exit-code publication in
        # both sequential and parallel modes so Gradle-cache recovery behaves the same.
        $lines = @(
            '@echo off',
            'setlocal',
            ('call "{0}" --internal-worker {1} 2>&1' -f $Validator, $state.Name),
            'exit /b %ERRORLEVEL%'
        )
        [System.IO.File]::WriteAllLines($state.WorkerPath, $lines, [System.Text.Encoding]::Default)
    }
}

function Start-State([object]$state) {
    if (Test-Path -LiteralPath $state.LogPath) { Remove-Item -LiteralPath $state.LogPath -Force }
    if (Test-Path -LiteralPath $state.ExitCodePath) { Remove-Item -LiteralPath $state.ExitCodePath -Force }
    if (Test-Path -LiteralPath ($state.ExitCodePath + '.tmp')) { Remove-Item -LiteralPath ($state.ExitCodePath + '.tmp') -Force }
    if (Test-Path -LiteralPath $state.WindowPath) { Remove-Item -LiteralPath $state.WindowPath -Force }
    Write-WorkerFile $state

    if ($script:OpenWorkerWindows) {
        if (-not (Test-Path -LiteralPath $WorkerRunner)) { throw "Worker runner not found: $WorkerRunner" }
        $arg = '/d /c ""{0}""' -f $state.WindowPath
        # Deliberately omit -NoNewWindow: each selected platform gets its own CMD console.
        $state.Process = Start-Process -FilePath $env:ComSpec -ArgumentList $arg -WorkingDirectory $Root -PassThru
    } else {
        if (-not (Test-Path -LiteralPath $WorkerRunner)) { throw "Worker runner not found: $WorkerRunner" }
        $title = "KWC 5.2.0 - $($state.FullLabel) build"
        $runnerArgs = '-NoLogo -NoProfile -ExecutionPolicy Bypass -File "{0}" -WorkerPath "{1}" -LogPath "{2}" -ExitCodePath "{3}" -WindowTitle "{4}" -ProjectRoot "{5}" -Platform "{6}"' -f $WorkerRunner, $state.WorkerPath, $state.LogPath, $state.ExitCodePath, $title, $Root, $state.Name
        $state.Process = Start-Process -FilePath 'powershell.exe' -ArgumentList $runnerArgs -PassThru -NoNewWindow
    }
    $state.Status = 'RUNNING'
}

function Read-WorkerExitCode([object]$state) {
    if (-not (Test-Path -LiteralPath $state.ExitCodePath)) { return $null }
    try {
        $raw = (Get-Content -LiteralPath $state.ExitCodePath -Raw).Trim()
        $value = 0
        if ([int]::TryParse($raw, [ref]$value)) { return $value }
    } catch {
        # A just-finished worker may still be atomically publishing the sidecar.
    }
    return $null
}

function Ensure-Reader([object]$state) {
    if ($null -ne $state.Reader) { return }
    if (-not (Test-Path -LiteralPath $state.LogPath)) { return }
    try {
        $state.Stream = New-Object System.IO.FileStream($state.LogPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        $state.Reader = New-Object System.IO.StreamReader($state.Stream)
    } catch {
        # The writer may still be creating the file. Retry on the next refresh.
    }
}

function Consume-Log([object]$state) {
    Ensure-Reader $state
    if ($null -eq $state.Reader) { return }
    while (($line = $state.Reader.ReadLine()) -ne $null) {
        if ($line -match '^\[KWC (?:Fabric|NeoForge|Forge)\] Minecraft\s+([^\s/]+)') {
            $state.Current = $matches[1]
        } elseif ($line -match '^\[KWC Bukkit\] (?:CLEAN|FAST|Maven|JDK)') {
            $state.Current = 'Maven'
        }

        if ($state.Name -eq 'bukkit') {
            if ($line -match '^\[KWC Bukkit\] OK\s*$') {
                [void]$state.Completed.Add('bukkit')
                $state.TerminalSuccess = $true
            }
        } elseif ($state.Name -eq 'fabric') {
            if ($line -match '^\[KWC Fabric\] OK:\s*(.+?)\s*$') {
                [void]$state.Completed.Add($matches[1])
            } elseif ($line -match '^\[KWC Fabric\] ALL 16 TARGETS BUILT SUCCESSFULLY\.\s*$') {
                $state.TerminalSuccess = $true
            }
        } elseif ($state.Name -eq 'neoforge') {
            if ($line -match '^\[KWC NeoForge\] OK:\s*(.+?)\s*$') {
                [void]$state.Completed.Add($matches[1])
            } elseif ($line -match '^\[KWC NeoForge\] ALL 12 TARGETS BUILT SUCCESSFULLY\.\s*$') {
                $state.TerminalSuccess = $true
            }
        } elseif ($state.Name -eq 'forge') {
            if ($line -match '^\[KWC Forge\] OK:\s*(?:Minecraft\s+)?(.+?)\s*$') {
                [void]$state.Completed.Add($matches[1])
            } elseif ($line -match '^\[KWC Forge\] ALL 16 TARGETS BUILT SUCCESSFULLY\.\s*$') {
                $state.TerminalSuccess = $true
            }
        }
    }
}

function Refresh-State([object]$state) {
    if ($state.Status -ne 'RUNNING') { return }
    Consume-Log $state
    if ($state.Process.HasExited) {
        # WaitForExit flushes the native process state. The worker also publishes an
        # explicit exit-code sidecar because Process.ExitCode can be unavailable on
        # some Windows PowerShell / cmd.exe combinations even after HasExited.
        try { $state.Process.WaitForExit() } catch { }
        Start-Sleep -Milliseconds 40
        Consume-Log $state

        $workerExitCode = Read-WorkerExitCode $state
        if ($null -eq $workerExitCode) {
            # The sidecar is written before cmd.exe exits, but retry briefly in case
            # antivirus/indexing delays the final atomic rename.
            for ($i = 0; $i -lt 10 -and $null -eq $workerExitCode; $i++) {
                Start-Sleep -Milliseconds 25
                $workerExitCode = Read-WorkerExitCode $state
            }
        }
        if ($null -eq $workerExitCode) {
            try {
                $candidate = $state.Process.ExitCode
                if ($null -ne $candidate) { $workerExitCode = [int]$candidate }
            } catch { }
        }

        $state.ExitCode = $workerExitCode
        $exitSaysSuccess = ($null -ne $state.ExitCode -and $state.ExitCode -eq 0)
        $terminalMarkerFallback = ($null -eq $state.ExitCode -and $state.TerminalSuccess)
        if ($exitSaysSuccess -or $terminalMarkerFallback) {
            if ($terminalMarkerFallback) { $state.ExitCode = 0 }
            $state.Status = 'PASS'
            # A successful worker is authoritative even when individual buffered target
            # markers were not all observed by the live reader.
            while ($state.Completed.Count -lt $state.Expected) {
                [void]$state.Completed.Add("pass-$($state.Completed.Count)")
            }
        } else {
            $state.Status = 'FAIL'
        }
    }
}

function Format-Elapsed([TimeSpan]$elapsed) {
    return ('{0:00}:{1:00}:{2:00}' -f [int]$elapsed.TotalHours, $elapsed.Minutes, $elapsed.Seconds)
}

$script:lastWidth = 0
function Render-Progress([Diagnostics.Stopwatch]$timer) {
    $done = 0
    $total = 0
    $parts = @()
    foreach ($state in $states) {
        $count = [Math]::Min($state.Completed.Count, $state.Expected)
        $done += $count
        $total += $state.Expected
        if ($state.Status -eq 'RUNNING') {
            $statusText = "$count/$($state.Expected)"
            if ($state.Current) { $statusText += " $($state.Current)" }
        } elseif ($state.Status -eq 'PENDING') {
            $statusText = "0/$($state.Expected) WAIT"
        } elseif ($state.Status -eq 'PASS') {
            $statusText = "$($state.Expected)/$($state.Expected) PASS"
        } else {
            $statusText = "$count/$($state.Expected) FAIL"
        }
        $parts += ("{0} {1}" -f $state.Label, $statusText)
    }
    $pct = 0
    if ($total -gt 0) { $pct = [int][Math]::Floor(($done * 100.0) / $total) }
    $line = '[{0}] {1} | {2}/{3} {4}% | {5}' -f (Format-Elapsed $timer.Elapsed), $script:phaseLabel, $done, $total, $pct, ($parts -join ' | ')
    if ($line.Length -lt $script:lastWidth) { $line = $line.PadRight($script:lastWidth) }
    $script:lastWidth = $line.Length
    [Console]::Write("`r$line")
}

function Close-State([object]$state) {
    if ($null -ne $state.Reader) { $state.Reader.Dispose(); $state.Reader = $null }
    if ($null -ne $state.Stream) { $state.Stream.Dispose(); $state.Stream = $null }
    if ($null -ne $state.Process) { $state.Process.Dispose(); $state.Process = $null }
    if (Test-Path -LiteralPath $state.WorkerPath) { Remove-Item -LiteralPath $state.WorkerPath -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $state.WindowPath) { Remove-Item -LiteralPath $state.WindowPath -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $state.ExitCodePath) { Remove-Item -LiteralPath $state.ExitCodePath -Force -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath ($state.ExitCodePath + '.tmp')) { Remove-Item -LiteralPath ($state.ExitCodePath + '.tmp') -Force -ErrorAction SilentlyContinue }
}

if (-not (Test-Path -LiteralPath $Validator)) { throw "Validator not found: $Validator" }
if ($script:OpenWorkerWindows -and -not (Test-Path -LiteralPath $WorkerRunner)) { throw "Worker runner not found: $WorkerRunner" }
if (-not (Test-Path -LiteralPath $LogDir)) { [void](New-Item -ItemType Directory -Force -Path $LogDir) }

$timer = [Diagnostics.Stopwatch]::StartNew()
$parallelMode = ($Parallel -ne 0)
$bukkitState = $states | Where-Object { $_.Name -eq 'bukkit' } | Select-Object -First 1
$bukkitFirst = $parallelMode -and ($null -ne $bukkitState) -and ($states.Count -gt 1)
$parallelLoadersStarted = $false
if ($bukkitFirst) {
    $script:phaseLabel = 'Phase 1/2 Bukkit'
} elseif ($parallelMode) {
    $script:phaseLabel = 'Parallel'
} else {
    $script:phaseLabel = 'Sequential'
}

try {
    if ($parallelMode) {
        if ($bukkitFirst) {
            Start-State $bukkitState
        } else {
            foreach ($state in $states) { Start-State $state }
            $parallelLoadersStarted = $true
        }
    }

    while ($true) {
        if (-not $parallelMode) {
            $running = @($states | Where-Object { $_.Status -eq 'RUNNING' })
            if ($running.Count -eq 0) {
                $next = $states | Where-Object { $_.Status -eq 'PENDING' } | Select-Object -First 1
                if ($null -ne $next) { Start-State $next }
            }
        }

        foreach ($state in $states) { Refresh-State $state }

        if ($bukkitFirst -and -not $parallelLoadersStarted) {
            if ($bukkitState.Status -eq 'PASS') {
                $script:phaseLabel = 'Phase 2/2 Loader windows'
                foreach ($state in @($states | Where-Object { $_.Status -eq 'PENDING' })) { Start-State $state }
                $parallelLoadersStarted = $true
            } elseif ($bukkitState.Status -eq 'FAIL') {
                Render-Progress $timer
                break
            }
        }

        Render-Progress $timer

        $unfinished = @($states | Where-Object { $_.Status -eq 'PENDING' -or $_.Status -eq 'RUNNING' })
        if ($unfinished.Count -eq 0) { break }
        Start-Sleep -Seconds 2
    }
    [Console]::WriteLine()

    $failed = @($states | Where-Object { $_.Status -eq 'FAIL' })
    if ($failed.Count -gt 0) {
        foreach ($state in $failed) {
            Write-Host ''
            $exitText = if ($null -eq $state.ExitCode) { '<unavailable>' } else { [string]$state.ExitCode }
            Write-Host ("[{0}] FAILED with exit code {1}. Last log lines:" -f $state.FullLabel, $exitText)
            if (Test-Path -LiteralPath $state.LogPath) {
                Get-Content -LiteralPath $state.LogPath -Tail 35 | ForEach-Object { Write-Host $_ }
            }
            Write-Host ("Full log: {0}" -f $state.LogPath)
        }
        exit 1
    }

    Write-Host ("All selected platform builds passed in {0}." -f (Format-Elapsed $timer.Elapsed))
    foreach ($state in $states) {
        Write-Host ("  {0}: PASS ({1}/{1}) - {2}" -f $state.FullLabel, $state.Expected, $state.LogPath)
    }
    exit 0
}
finally {
    foreach ($state in $states) { Close-State $state }
}
