@echo off
REM KWC 파일 안내 / KWC file guide
REM 이 배치 파일은 Windows 빌드/검증 진입점 또는 helper이며 PowerShell/Gradle/Maven 작업을 안정적으로 연결한다.
REM This batch file is a Windows build/validation entry point or helper that coordinates PowerShell, Gradle, and Maven work.
REM CMD label/call 동작을 위해 CRLF 줄바꿈을 반드시 유지한다.
REM Keep CRLF line endings because CMD label/call behavior depends on the Windows batch format.

setlocal EnableExtensions

for %%I in ("%~dp0.") do set "APP_HOME=%%~fI"
if not defined KWC_GRADLE_VERSION set "KWC_GRADLE_VERSION=9.3.1"
set "GRADLE_VERSION=%KWC_GRADLE_VERSION%"
set "DIST_NAME=gradle-%GRADLE_VERSION%"
if defined GRADLE_USER_HOME (
  set "CACHE_ROOT=%GRADLE_USER_HOME%\wrapper\dists\kwc-bootstrap-%GRADLE_VERSION%"
) else (
  set "CACHE_ROOT=%USERPROFILE%\.gradle\wrapper\dists\kwc-bootstrap-%GRADLE_VERSION%"
)
set "DIST_DIR=%CACHE_ROOT%\%DIST_NAME%"
set "DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%-bin.zip"
set "GRADLE_BAT=%DIST_DIR%\bin\gradle.bat"
set "BOOTSTRAP_SCRIPT=%APP_HOME%\..\bootstrap-gradle-windows.ps1"

if not exist "%GRADLE_BAT%" (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%BOOTSTRAP_SCRIPT%" -Version "%GRADLE_VERSION%" -CacheRoot "%CACHE_ROOT%" -DistributionUrl "%DIST_URL%"
  if errorlevel 1 goto bootstrapFailed
)
if not exist "%GRADLE_BAT%" goto bootstrapFailed

pushd "%APP_HOME%"
if errorlevel 1 exit /b 1
call "%GRADLE_BAT%" %*
set "GRADLE_EXIT=%ERRORLEVEL%"
popd
exit /b %GRADLE_EXIT%

:bootstrapFailed
echo ERROR: Failed to prepare Gradle %GRADLE_VERSION%. 1>&2
exit /b 1
