@echo off
REM KWC 파일 안내 / KWC file guide
REM 이 배치 파일은 Windows 빌드/검증 진입점 또는 helper이며 PowerShell/Gradle/Maven 작업을 안정적으로 연결한다.
REM This batch file is a Windows build/validation entry point or helper that coordinates PowerShell, Gradle, and Maven work.
REM CMD label/call 동작을 위해 CRLF 줄바꿈을 반드시 유지한다.
REM Keep CRLF line endings because CMD label/call behavior depends on the Windows batch format.

setlocal EnableExtensions EnableDelayedExpansion
for %%I in ("%~dp0..") do set "KWC_ROOT=%%~fI"

if /I "%~1"=="--fast" (
  set "KWC_SKIP_CLEAN=1"
  if defined KWC_GRADLE_ARGS (set "KWC_GRADLE_ARGS=--build-cache !KWC_GRADLE_ARGS!") else set "KWC_GRADLE_ARGS=--build-cache"
) else if not "%~1"=="" (
  echo Usage: build-all.bat [--fast] 1>&2
  exit /b 2
)

if not defined KWC_PATH_PREFLIGHT_DONE (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%KWC_ROOT%\check-build-path.ps1" -Root "%KWC_ROOT%"
  if errorlevel 1 exit /b 1
  set "KWC_PATH_PREFLIGHT_DONE=1"
)
if defined KWC_GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_GRADLE_USER_HOME%"
) else if not defined GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_ROOT%\.build-cache\gradle"
)
if not defined KWC_BUILD_TEMP set "KWC_BUILD_TEMP=%KWC_ROOT%\.build-cache\tmp"
set "TEMP=%KWC_BUILD_TEMP%\fabric\preflight"
set "TMP=%TEMP%"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>&1
if not exist "%TEMP%" mkdir "%TEMP%" >nul 2>&1

set /a KWC_DONE=0
set /a KWC_TOTAL=16
for %%M in (1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  call "%~dp0build-target.bat" %%M
  if errorlevel 1 exit /b 1
  set /a KWC_DONE+=1
  echo [KWC Fabric] Progress: !KWC_DONE!/!KWC_TOTAL! completed.
)
echo [KWC Fabric] ALL 16 TARGETS BUILT SUCCESSFULLY.
exit /b 0
