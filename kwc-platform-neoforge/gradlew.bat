@echo off
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
set "ZIP_FILE=%CACHE_ROOT%\%DIST_NAME%-bin.zip"
set "DIST_URL=https://services.gradle.org/distributions/%DIST_NAME%-bin.zip"
set "GRADLE_BAT=%DIST_DIR%\bin\gradle.bat"

if exist "%GRADLE_BAT%" goto runGradle

if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"
if exist "%ZIP_FILE%" goto extractGradle

echo Downloading Gradle %GRADLE_VERSION%...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing '%DIST_URL%' -OutFile '%ZIP_FILE%'"
if errorlevel 1 goto downloadFailed

:extractGradle
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%CACHE_ROOT%' -Force"
if errorlevel 1 goto extractFailed

:runGradle
pushd "%APP_HOME%"
if errorlevel 1 exit /b 1
call "%GRADLE_BAT%" %*
set "GRADLE_EXIT=%ERRORLEVEL%"
popd
exit /b %GRADLE_EXIT%

:downloadFailed
echo ERROR: Failed to download Gradle %GRADLE_VERSION%. 1>&2
exit /b 1

:extractFailed
echo ERROR: Failed to extract Gradle %GRADLE_VERSION%. 1>&2
exit /b 1
