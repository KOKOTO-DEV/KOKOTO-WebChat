@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "ROOT=%~dp0"
set "KWC_BUILD_CACHE_ROOT=%ROOT%.build-cache"
if defined KWC_GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_GRADLE_USER_HOME%"
) else if not defined GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_BUILD_CACHE_ROOT%\gradle"
)
if defined KWC_BUILD_TEMP (
  set "KWC_VALIDATION_TEMP=%KWC_BUILD_TEMP%"
) else (
  set "KWC_VALIDATION_TEMP=%KWC_BUILD_CACHE_ROOT%\tmp\validation"
)
set "KWC_MAVEN_REPO=%KWC_BUILD_CACHE_ROOT%\maven"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>&1
if not exist "%KWC_VALIDATION_TEMP%" mkdir "%KWC_VALIDATION_TEMP%" >nul 2>&1
if not exist "%KWC_MAVEN_REPO%" mkdir "%KWC_MAVEN_REPO%" >nul 2>&1
set "TEMP=%KWC_VALIDATION_TEMP%"
set "TMP=%KWC_VALIDATION_TEMP%"
set "LOGDIR=%ROOT%validation-logs"
set "OUTDIR=%ROOT%release-5.0.0"
if exist "%LOGDIR%" rmdir /s /q "%LOGDIR%"
if exist "%OUTDIR%" rmdir /s /q "%OUTDIR%"
mkdir "%LOGDIR%" || exit /b 1
mkdir "%OUTDIR%" || exit /b 1

echo ============================================================
echo KOKOTO WebChat 5.0.0 full exact-target release validation
echo Bukkit 1 + Fabric 16 + NeoForge 12 + Forge 16 = 45 JARs
echo ============================================================
echo.
echo Build cache root: %KWC_BUILD_CACHE_ROOT%
echo Gradle user home: %GRADLE_USER_HOME%
echo Maven repository: %KWC_MAVEN_REPO%
echo Build temp:       %TEMP%
echo.

set "MAVEN_CMD="
for /f "delims=" %%I in ('where mvn.cmd 2^>nul') do if not defined MAVEN_CMD set "MAVEN_CMD=%%I"
if not defined MAVEN_CMD for /f "delims=" %%I in ('where mvn.exe 2^>nul') do if not defined MAVEN_CMD set "MAVEN_CMD=%%I"
if not defined MAVEN_CMD (
  echo Maven was not found on PATH. Bootstrapping Apache Maven 3.9.16 locally...
  call :bootstrapMaven
  if errorlevel 1 exit /b 1
)
echo Maven: %MAVEN_CMD%

call :selectJava 17
if errorlevel 1 exit /b 1
set "JAVA_HOME=%SELECTED_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [1/4] Bukkit Maven build with JDK 17: %JAVA_HOME%
pushd "%ROOT%"
call "%MAVEN_CMD%" -Dmaven.repo.local="%KWC_MAVEN_REPO%" clean package > "%LOGDIR%\01-bukkit.log" 2>&1
set "RC=%ERRORLEVEL%"
popd
type "%LOGDIR%\01-bukkit.log"
if not "%RC%"=="0" exit /b %RC%
set "BUKKIT_JAR=%ROOT%kwc-platform-bukkit\target\KOKOTO-WebChat-5.0.0-Bukkit-1.18-26.2.jar"
if not exist "%BUKKIT_JAR%" (echo ERROR: Bukkit JAR missing. 1>&2& exit /b 1)
copy /y "%BUKKIT_JAR%" "%OUTDIR%\" >nul

echo.
echo [2/4] Fabric exact-target matrix ^(16 builds^)
echo [Fabric] Progress is being written to validation-logs\02-fabric-all.log.
echo [Fabric] To watch it live in another PowerShell: Get-Content .\validation-logs\02-fabric-all.log -Wait
call "%ROOT%kwc-platform-fabric\build-all.bat" > "%LOGDIR%\02-fabric-all.log" 2>&1
set "RC=%ERRORLEVEL%"
type "%LOGDIR%\02-fabric-all.log"
if not "%RC%"=="0" (echo ERROR: Fabric matrix failed. 1>&2& exit /b %RC%)
for %%M in (1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  set "J=%ROOT%kwc-platform-fabric\targets\%%M\build\libs\KOKOTO-WebChat-5.0.0-Fabric-%%M.jar"
  if not exist "!J!" (echo ERROR: Fabric %%M JAR missing: !J! 1>&2& exit /b 1)
  copy /y "!J!" "%OUTDIR%\" >nul
)

echo.
echo [3/4] NeoForge exact-target matrix ^(12 builds^)
echo [NeoForge] Progress is being written to validation-logs\03-neoforge-all.log.
echo [NeoForge] To watch it live in another PowerShell: Get-Content .\validation-logs\03-neoforge-all.log -Wait
call "%ROOT%kwc-platform-neoforge\build-all.bat" > "%LOGDIR%\03-neoforge-all.log" 2>&1
set "RC=%ERRORLEVEL%"
type "%LOGDIR%\03-neoforge-all.log"
if not "%RC%"=="0" (echo ERROR: NeoForge matrix failed. 1>&2& exit /b %RC%)
for %%M in (1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  set "J=%ROOT%kwc-platform-neoforge\targets\%%M\build\libs\KOKOTO-WebChat-5.0.0-NeoForge-%%M.jar"
  if not exist "!J!" (echo ERROR: NeoForge %%M JAR missing: !J! 1>&2& exit /b 1)
  copy /y "!J!" "%OUTDIR%\" >nul
)

echo.
echo [4/4] Forge exact-target matrix ^(16 builds^)
echo [Forge] Progress is being written to validation-logs\04-forge-all.log.
echo [Forge] To watch it live in another PowerShell: Get-Content .\validation-logs\04-forge-all.log -Wait
call "%ROOT%kwc-platform-forge\build-all.bat" > "%LOGDIR%\04-forge-all.log" 2>&1
set "RC=%ERRORLEVEL%"
type "%LOGDIR%\04-forge-all.log"
if not "%RC%"=="0" (echo ERROR: Forge matrix failed. 1>&2& exit /b %RC%)
for %%M in (1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  set "J=%ROOT%kwc-platform-forge\targets\%%M\build\libs\KOKOTO-WebChat-5.0.0-Forge-%%M.jar"
  if not exist "!J!" (echo ERROR: Forge %%M JAR missing: !J! 1>&2& exit /b 1)
  copy /y "!J!" "%OUTDIR%\" >nul
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $files=[System.IO.Directory]::GetFiles('%OUTDIR%','*.jar'); [Array]::Sort($files,[System.StringComparer]::OrdinalIgnoreCase); $lines=[System.Collections.Generic.List[string]]::new(); foreach($f in $files){ $h=Get-FileHash -Algorithm SHA256 -LiteralPath $f; $lines.Add(('{0}  {1}' -f $h.Hash.ToLowerInvariant(),[System.IO.Path]::GetFileName($f))) }; [System.IO.File]::WriteAllLines('%OUTDIR%\SHA256SUMS.txt',$lines,[System.Text.Encoding]::ASCII)"
if errorlevel 1 exit /b 1
for /f %%C in ('dir /b /a-d "%OUTDIR%\*.jar" ^| find /c /v ""') do set "JARCOUNT=%%C"
if not "%JARCOUNT%"=="45" (
  echo ERROR: Expected 45 deployable JARs ^(1 Bukkit + 16 Fabric + 12 NeoForge + 16 Forge^), found %JARCOUNT%. 1>&2
  exit /b 1
)
echo.
echo ============================================================
echo FINAL RELEASE BUILD PASS
echo Deployable JARs: %JARCOUNT%
echo Output: %OUTDIR%
echo Logs:   %LOGDIR%
echo ============================================================
exit /b 0
:bootstrapMaven
set "MAVEN_VERSION=3.9.16"
set "MAVEN_TOOL_ROOT=%ROOT%.tools"
set "MAVEN_HOME=%MAVEN_TOOL_ROOT%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if exist "%MAVEN_CMD%" exit /b 0

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $v='%MAVEN_VERSION%'; $toolRoot='%MAVEN_TOOL_ROOT%'; $mavenHome='%MAVEN_HOME%'; $null=New-Item -ItemType Directory -Force -Path $toolRoot; $zip=Join-Path $toolRoot ('apache-maven-'+$v+'-bin.zip'); $shaFile=$zip+'.sha512'; $sources=@(@{Zip='https://dlcdn.apache.org/maven/maven-3/'+$v+'/binaries/apache-maven-'+$v+'-bin.zip';Sha='https://downloads.apache.org/maven/maven-3/'+$v+'/binaries/apache-maven-'+$v+'-bin.zip.sha512'},@{Zip='https://archive.apache.org/dist/maven/maven-3/'+$v+'/binaries/apache-maven-'+$v+'-bin.zip';Sha='https://archive.apache.org/dist/maven/maven-3/'+$v+'/binaries/apache-maven-'+$v+'-bin.zip.sha512'}); $ok=$false; foreach($src in $sources){ try { Write-Host ('[KWC] Downloading Apache Maven '+$v+' from '+$src.Zip); Invoke-WebRequest -UseBasicParsing -Uri $src.Zip -OutFile $zip; Invoke-WebRequest -UseBasicParsing -Uri $src.Sha -OutFile $shaFile; $ok=$true; break } catch { Write-Host ('[KWC] Maven download source failed: '+$_.Exception.Message) } }; if(-not $ok){ throw 'Unable to download Apache Maven from official mirrors.' }; $expected=((Get-Content -LiteralPath $shaFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant(); $actual=(Get-FileHash -Algorithm SHA512 -LiteralPath $zip).Hash.ToLowerInvariant(); if($expected -ne $actual){ throw ('Maven SHA-512 mismatch. Expected '+$expected+', got '+$actual) }; Write-Host '[KWC] Maven SHA-512 verified.'; if(Test-Path -LiteralPath $mavenHome){ Remove-Item -LiteralPath $mavenHome -Recurse -Force }; Expand-Archive -LiteralPath $zip -DestinationPath $toolRoot -Force; Remove-Item -LiteralPath $zip,$shaFile -Force -ErrorAction SilentlyContinue; if(-not (Test-Path -LiteralPath (Join-Path $mavenHome 'bin\mvn.cmd'))){ throw 'Maven extraction completed but mvn.cmd is missing.' }"
if errorlevel 1 (
  echo ERROR: Apache Maven bootstrap failed. 1>&2
  echo        Install Maven manually or make mvn.cmd available on PATH, then retry. 1>&2
  exit /b 1
)
if not exist "%MAVEN_CMD%" (
  echo ERROR: Local Maven bootstrap completed but mvn.cmd is missing: 1>&2
  echo        %MAVEN_CMD% 1>&2
  exit /b 1
)
exit /b 0

:selectJava
set "SELECTED_JAVA_HOME="
set "KWC_JAVA_RESULT=%TEMP%\kwc-final-java-%~1-%RANDOM%-%RANDOM%.txt"
if exist "%KWC_JAVA_RESULT%" del /q "%KWC_JAVA_RESULT%" >nul 2>&1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%kwc-platform-forge\select-java.ps1" -Major %~1 -OutputFile "%KWC_JAVA_RESULT%"
if errorlevel 1 goto :javaFail
if not exist "%KWC_JAVA_RESULT%" goto :javaFail
set /p "SELECTED_JAVA_HOME="<"%KWC_JAVA_RESULT%"
del /q "%KWC_JAVA_RESULT%" >nul 2>&1
if not defined SELECTED_JAVA_HOME goto :javaFail
if not exist "%SELECTED_JAVA_HOME%\bin\java.exe" goto :javaFail
if not exist "%SELECTED_JAVA_HOME%\bin\javac.exe" goto :javaFail
exit /b 0
:javaFail
if exist "%KWC_JAVA_RESULT%" del /q "%KWC_JAVA_RESULT%" >nul 2>&1
echo ERROR: JDK %~1 could not be resolved. Set KWC_JAVA%~1_HOME and retry. 1>&2
exit /b 1
