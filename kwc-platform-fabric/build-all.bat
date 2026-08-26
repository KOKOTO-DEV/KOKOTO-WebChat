@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "KWC_ROOT=%%~fI"
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
for %%M in (1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  call "%~dp0build-target.bat" %%M
  if errorlevel 1 exit /b 1
)
echo [KWC Fabric] ALL 16 TARGETS BUILT SUCCESSFULLY.
