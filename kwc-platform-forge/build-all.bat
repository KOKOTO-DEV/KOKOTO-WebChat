@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "KWC_ROOT=%%~fI"
if defined KWC_GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_GRADLE_USER_HOME%"
) else if not defined GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_ROOT%\.build-cache\gradle"
)
if not defined KWC_BUILD_TEMP set "KWC_BUILD_TEMP=%KWC_ROOT%\.build-cache\tmp"
set "TEMP=%KWC_BUILD_TEMP%\forge\preflight"
set "TMP=%TEMP%"
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>&1
if not exist "%TEMP%" mkdir "%TEMP%" >nul 2>&1

echo [KWC Forge] Preflight: locating or downloading JDK 17, 21 and 25...
call :checkJdk 17
if errorlevel 1 exit /b 1
call :checkJdk 21
if errorlevel 1 exit /b 1
call :checkJdk 25
if errorlevel 1 exit /b 1

echo.
echo [KWC Forge] Building all exact targets...
for %%M in (1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  call "%~dp0build-target.bat" %%M
  if errorlevel 1 exit /b 1
)

echo.
echo [KWC Forge] ALL TARGETS BUILT SUCCESSFULLY.
exit /b 0

:checkJdk
set "KWC_CHECK_MAJOR=%~1"
set "KWC_JAVA_RESULT=%TEMP%\kwc-java-%~1-%RANDOM%-%RANDOM%.txt"
if exist "%KWC_JAVA_RESULT%" del /q "%KWC_JAVA_RESULT%" >nul 2>&1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0select-java.ps1" -Major %~1 -OutputFile "%KWC_JAVA_RESULT%"
if errorlevel 1 goto :jdkFailed
if not exist "%KWC_JAVA_RESULT%" goto :jdkFailed
set "KWC_CHECK_HOME="
set /p "KWC_CHECK_HOME="<"%KWC_JAVA_RESULT%"
del /q "%KWC_JAVA_RESULT%" >nul 2>&1
set "KWC_JAVA_RESULT="
if not defined KWC_CHECK_HOME goto :jdkFailedNoFile
if not exist "%KWC_CHECK_HOME%\bin\java.exe" goto :jdkFailedInvalid
if not exist "%KWC_CHECK_HOME%\bin\javac.exe" goto :jdkFailedInvalid
echo [KWC Forge] JDK %~1: %KWC_CHECK_HOME%
set "KWC_CHECK_HOME="
set "KWC_CHECK_MAJOR="
exit /b 0

:jdkFailed
if exist "%KWC_JAVA_RESULT%" del /q "%KWC_JAVA_RESULT%" >nul 2>&1
set "KWC_JAVA_RESULT="
echo ERROR: JDK %KWC_CHECK_MAJOR% is required by the full Forge target matrix. 1>&2
echo        Automatic Temurin download also failed. Retry with network access or set KWC_JAVA%KWC_CHECK_MAJOR%_HOME. 1>&2
set "KWC_CHECK_MAJOR="
exit /b 1

:jdkFailedNoFile
echo ERROR: JDK %KWC_CHECK_MAJOR% resolver returned an empty path. 1>&2
echo        Set KWC_JAVA%KWC_CHECK_MAJOR%_HOME explicitly and retry. 1>&2
set "KWC_CHECK_HOME="
set "KWC_CHECK_MAJOR="
exit /b 1

:jdkFailedInvalid
echo ERROR: Resolved JDK %KWC_CHECK_MAJOR% path is invalid: "%KWC_CHECK_HOME%" 1>&2
echo        Expected bin\java.exe and bin\javac.exe below that directory. 1>&2
set "KWC_CHECK_HOME="
set "KWC_CHECK_MAJOR="
exit /b 1
