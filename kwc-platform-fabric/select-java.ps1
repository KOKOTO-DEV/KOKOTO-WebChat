param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(17,21,25)]
    [int]$Major,

    [Parameter(Mandatory = $false)]
    [string]$OutputFile,

    [Parameter(Mandatory = $false)]
    [string]$Label = '[KWC Fabric]'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$ScriptRoot = Split-Path -Parent $PSCommandPath
$LocalJdkRoot = Join-Path $ScriptRoot '.jdks'
$LocalJdkHome = Join-Path $LocalJdkRoot ("jdk-{0}" -f $Major)

# Windows PowerShell 5.1 can otherwise negotiate an obsolete TLS protocol on
# some systems. Keep TLS 1.2 available before contacting Adoptium/GitHub CDNs.
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch {}

function Get-JavaMajor([string]$JavaHome) {
    if ([string]::IsNullOrWhiteSpace($JavaHome)) { return $null }
    $java = Join-Path $JavaHome 'bin\java.exe'
    $javac = Join-Path $JavaHome 'bin\javac.exe'
    if (!(Test-Path -LiteralPath $java) -or !(Test-Path -LiteralPath $javac)) { return $null }

    # Prefer the standard JDK release metadata instead of executing java. Windows
    # PowerShell 5.x can surface native stderr as ErrorRecord objects, and with
    # ErrorActionPreference=Stop a normal `java -version` may otherwise look like
    # a failed JDK probe even though the executable is valid.
    $releaseFile = Join-Path $JavaHome 'release'
    if (Test-Path -LiteralPath $releaseFile) {
        try {
            $releaseText = [IO.File]::ReadAllText($releaseFile)
            if ($releaseText -match '(?m)^JAVA_VERSION="(\d+)(?:\.|"|$)') {
                return [int]$Matches[1]
            }
        } catch {}
    }

    # Fallback for unusual JDK layouts without a release file. Use Process rather
    # than PowerShell's native stderr pipeline so stderr does not become a script
    # error under Windows PowerShell 5.x.
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $java
        $psi.Arguments = '-XshowSettings:properties -version'
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $proc = [Diagnostics.Process]::Start($psi)
        $stdout = $proc.StandardOutput.ReadToEnd()
        $stderr = $proc.StandardError.ReadToEnd()
        $proc.WaitForExit()
        $text = $stdout + "`n" + $stderr
        if ($text -match 'java\.specification\.version\s*=\s*(\d+)') {
            return [int]$Matches[1]
        }
        if ($text -match '(?i)version\s+"(\d+)(?:\.|"|$)') {
            return [int]$Matches[1]
        }
    } catch {}
    return $null
}

function Return-JavaHome([string]$JavaHome) {
    $full = [IO.Path]::GetFullPath($JavaHome).TrimEnd([char]92)
    if ([string]::IsNullOrWhiteSpace($full)) {
        throw "Resolved JDK $Major home is empty."
    }
    if ($OutputFile) {
        $parent = Split-Path -Parent $OutputFile
        if ($parent -and !(Test-Path -LiteralPath $parent)) {
            New-Item -ItemType Directory -Path $parent -Force | Out-Null
        }
        [IO.File]::WriteAllText($OutputFile, $full, [Text.Encoding]::Default)
    } else {
        Write-Output $full
    }
    exit 0
}

function Invoke-KwcJsonRequest([string]$Uri) {
    $headers = @{ 'User-Agent' = 'KOKOTO-WebChat-Build/5.2.0' }
    $lastError = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            return Invoke-RestMethod -Uri $Uri -Headers $headers -TimeoutSec 30
        } catch {
            $lastError = $_.Exception.Message
            if ($attempt -lt 3) {
                Write-Host "$Label network request failed (attempt $attempt/3); retrying..."
                Start-Sleep -Seconds (2 * $attempt)
            }
        }
    }

    # curl.exe uses a different Windows HTTP/TLS stack and is a useful fallback
    # when Invoke-RestMethod is blocked by a proxy or WinHTTP/TLS quirk.
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        Write-Host "$Label PowerShell web request failed; trying curl.exe fallback..."
        $tmp = Join-Path ([IO.Path]::GetTempPath()) ("kwc-adoptium-{0}-{1}.json" -f $PID, [Guid]::NewGuid().ToString('N'))
        try {
            & $curl.Source '--fail' '--silent' '--show-error' '--location' '--retry' '3' '--retry-delay' '2' '--connect-timeout' '20' '--max-time' '90' '--user-agent' 'KOKOTO-WebChat-Build/5.2.0' '--output' $tmp $Uri
            if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $tmp)) {
                $json = [IO.File]::ReadAllText($tmp)
                if (![string]::IsNullOrWhiteSpace($json)) {
                    return ($json | ConvertFrom-Json)
                }
            }
        } finally {
            if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue }
        }
    }
    throw "Network request failed for $Uri. Last PowerShell error: $lastError"
}

function Invoke-KwcDownload([string]$Uri, [string]$OutFile) {
    $headers = @{ 'User-Agent' = 'KOKOTO-WebChat-Build/5.2.0' }
    $lastError = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            if (Test-Path -LiteralPath $OutFile) { Remove-Item -LiteralPath $OutFile -Force -ErrorAction SilentlyContinue }
            Invoke-WebRequest -Uri $Uri -OutFile $OutFile -UseBasicParsing -Headers $headers -TimeoutSec 120
            if ((Test-Path -LiteralPath $OutFile) -and ((Get-Item -LiteralPath $OutFile).Length -gt 0)) { return }
            throw 'Downloaded file is empty.'
        } catch {
            $lastError = $_.Exception.Message
            if ($attempt -lt 3) {
                Write-Host "$Label download failed (attempt $attempt/3); retrying..."
                Start-Sleep -Seconds (2 * $attempt)
            }
        }
    }

    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        Write-Host "$Label PowerShell download failed; trying curl.exe fallback..."
        if (Test-Path -LiteralPath $OutFile) { Remove-Item -LiteralPath $OutFile -Force -ErrorAction SilentlyContinue }
        & $curl.Source '--fail' '--silent' '--show-error' '--location' '--retry' '3' '--retry-delay' '2' '--connect-timeout' '20' '--user-agent' 'KOKOTO-WebChat-Build/5.2.0' '--output' $OutFile $Uri
        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $OutFile) -and ((Get-Item -LiteralPath $OutFile).Length -gt 0)) { return }
    }
    throw "Download failed for $Uri. Last PowerShell error: $lastError"
}

function Get-AdoptiumBinary([int]$Version) {
    $api = "https://api.adoptium.net/v3/assets/feature_releases/$Version/ga?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&page=0&page_size=1&project=jdk&sort_method=DEFAULT&sort_order=DESC&vendor=eclipse"
    Write-Host "$Label JDK $Version not found locally; resolving Eclipse Temurin..."
    $releases = Invoke-KwcJsonRequest $api
    if (!$releases -or $releases.Count -lt 1) {
        throw "Adoptium returned no GA JDK $Version release for Windows x64."
    }
    $binary = $null
    foreach ($candidate in $releases[0].binaries) {
        if ($candidate.image_type -eq 'jdk' -and $candidate.os -eq 'windows' -and $candidate.architecture -eq 'x64') {
            $binary = $candidate
            break
        }
    }
    if (!$binary -or !$binary.package -or [string]::IsNullOrWhiteSpace($binary.package.link)) {
        throw "Adoptium response did not contain a Windows x64 JDK $Version package."
    }
    return $binary.package
}

function Install-LocalTemurin([int]$Version) {
    if ($env:KWC_AUTO_DOWNLOAD_JDK -match '^(0|false|no|off)$') {
        throw "JDK $Version was not found and automatic JDK download is disabled by KWC_AUTO_DOWNLOAD_JDK=$($env:KWC_AUTO_DOWNLOAD_JDK)."
    }

    New-Item -ItemType Directory -Path $LocalJdkRoot -Force | Out-Null
    $package = Get-AdoptiumBinary $Version
    $downloadDir = Join-Path $LocalJdkRoot '.download'
    New-Item -ItemType Directory -Path $downloadDir -Force | Out-Null
    $zip = Join-Path $downloadDir ("temurin-jdk-{0}.zip" -f $Version)
    $extract = Join-Path $downloadDir ("extract-{0}" -f $Version)

    try {
        Write-Host "$Label Downloading Eclipse Temurin JDK $Version..."
        Invoke-KwcDownload $package.link $zip

        if ([string]::IsNullOrWhiteSpace($package.checksum)) {
            throw "Adoptium response did not include a SHA-256 checksum for JDK $Version."
        }
        $actual = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
        $expected = ([string]$package.checksum).Trim().ToLowerInvariant()
        if ($actual -ne $expected) {
            throw "JDK $Version SHA-256 mismatch. Expected $expected, got $actual."
        }
        Write-Host "$Label JDK $Version SHA-256 verified."

        if (Test-Path -LiteralPath $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
        New-Item -ItemType Directory -Path $extract -Force | Out-Null
        Write-Host "$Label Extracting JDK $Version..."
        $extractor = Join-Path (Split-Path -Parent $ScriptRoot) 'extract-zip-progress-windows.ps1'
        & $extractor -ZipPath $zip -DestinationPath $extract -Label "$Label JDK $Version"

        $jdk = Get-ChildItem -LiteralPath $extract -Directory -ErrorAction Stop |
            Where-Object { (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe')) -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe')) } |
            Select-Object -First 1
        if (!$jdk) {
            throw "Downloaded JDK $Version archive did not contain a usable JDK directory."
        }
        if ((Get-JavaMajor $jdk.FullName) -ne $Version) {
            throw "Downloaded archive did not contain JDK $Version."
        }

        if (Test-Path -LiteralPath $LocalJdkHome) { Remove-Item -LiteralPath $LocalJdkHome -Recurse -Force }
        Move-Item -LiteralPath $jdk.FullName -Destination $LocalJdkHome
        Write-Host "$Label Local JDK $Version ready: $LocalJdkHome"
    }
    finally {
        if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force -ErrorAction SilentlyContinue }
        if (Test-Path -LiteralPath $extract) { Remove-Item -LiteralPath $extract -Recurse -Force -ErrorAction SilentlyContinue }
    }

    if ((Get-JavaMajor $LocalJdkHome) -ne $Version) {
        throw "Local JDK $Version installation validation failed: $LocalJdkHome"
    }
    return $LocalJdkHome
}

$explicitNames = @("KWC_JAVA${Major}_HOME", "JAVA${Major}_HOME", "JDK${Major}_HOME")
foreach ($name in $explicitNames) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if (![string]::IsNullOrWhiteSpace($value)) {
        $value = $value.Trim('"')
        $detected = Get-JavaMajor $value
        if ($detected -eq $Major) {
            Return-JavaHome $value
        }
        throw "$name is set to '$value', but it is not a JDK $Major installation."
    }
}

# Prefer the project-local JDK cache once it has been downloaded.
if ((Get-JavaMajor $LocalJdkHome) -eq $Major) {
    Return-JavaHome $LocalJdkHome
}

$roots = @()
if ($env:ProgramFiles) {
    $roots += (Join-Path $env:ProgramFiles 'Java')
    $roots += (Join-Path $env:ProgramFiles 'Eclipse Adoptium')
    $roots += (Join-Path $env:ProgramFiles 'Microsoft')
    $roots += (Join-Path $env:ProgramFiles 'Zulu')
    $roots += (Join-Path $env:ProgramFiles 'BellSoft')
    $roots += (Join-Path $env:ProgramFiles 'Amazon Corretto')
}
if (${env:ProgramFiles(x86)}) {
    $roots += (Join-Path ${env:ProgramFiles(x86)} 'Java')
}
if ($env:LOCALAPPDATA) {
    $roots += (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium')
}
if ($env:USERPROFILE) {
    $roots += (Join-Path $env:USERPROFILE '.jdks')
    $roots += (Join-Path $env:USERPROFILE '.jabba\jdk')
}

$candidates = New-Object System.Collections.Generic.List[string]
foreach ($root in ($roots | Select-Object -Unique)) {
    if (!(Test-Path -LiteralPath $root)) { continue }
    try {
        Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $candidates.Add($_.FullName)
        }
    } catch {}
}

try {
    $pathJavacs = @(Get-Command javac.exe -All -ErrorAction SilentlyContinue)
    foreach ($cmd in $pathJavacs) {
        $exe = $cmd.Source
        if ([string]::IsNullOrWhiteSpace($exe)) { $exe = $cmd.Definition }
        if (![string]::IsNullOrWhiteSpace($exe)) {
            $bin = Split-Path -Parent $exe
            $home = Split-Path -Parent $bin
            if (![string]::IsNullOrWhiteSpace($home)) { $candidates.Insert(0, $home) }
        }
    }
} catch {}

if ($env:JAVA_HOME) { $candidates.Insert(0, $env:JAVA_HOME.Trim('"')) }

foreach ($candidate in ($candidates | Select-Object -Unique)) {
    if ((Get-JavaMajor $candidate) -eq $Major) {
        Return-JavaHome $candidate
    }
}

try {
    $installed = Install-LocalTemurin $Major
    Return-JavaHome $installed
}
catch {
    Write-Error @"
JDK $Major was not found and automatic Eclipse Temurin download failed.
$($_.Exception.Message)

You can retry with network access, install JDK $Major manually, or set KWC_JAVA${Major}_HOME.
To intentionally disable automatic JDK downloads, set KWC_AUTO_DOWNLOAD_JDK=0.
"@
    exit 1
}
