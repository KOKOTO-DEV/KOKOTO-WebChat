@echo off
REM KWC 파일 안내 / KWC file guide
REM 이 배치 파일은 Windows 빌드/검증 진입점 또는 helper이며 PowerShell/Gradle/Maven 작업을 안정적으로 연결한다.
REM This batch file is a Windows build/validation entry point or helper that coordinates PowerShell, Gradle, and Maven work.
REM CMD label/call 동작을 위해 CRLF 줄바꿈을 반드시 유지한다.
REM Keep CRLF line endings because CMD label/call behavior depends on the Windows batch format.

setlocal EnableExtensions
set "MC=%~1"
if /I "%~2"=="--fast" (
  set "KWC_SKIP_CLEAN=1"
  if defined KWC_GRADLE_ARGS (set "KWC_GRADLE_ARGS=--build-cache %KWC_GRADLE_ARGS%") else set "KWC_GRADLE_ARGS=--build-cache"
) else if not "%~2"=="" (
  echo Usage: build-target.bat ^<minecraft-version^> [--fast] 1>&2
  exit /b 2
)
for %%I in ("%~dp0..") do set "KWC_ROOT=%%~fI"
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
if defined KWC_BUILD_TEMP (
  set "KWC_TARGET_TEMP=%KWC_BUILD_TEMP%\forge\%MC%"
) else (
  set "KWC_TARGET_TEMP=%KWC_ROOT%\.build-cache\tmp\forge\%MC%"
)
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>&1
if not exist "%KWC_TARGET_TEMP%" mkdir "%KWC_TARGET_TEMP%" >nul 2>&1
set "TEMP=%KWC_TARGET_TEMP%"
set "TMP=%KWC_TARGET_TEMP%"
if "%MC%"=="" (
  echo Usage: build-target.bat ^<minecraft-version^> [--fast] 1>&2
  exit /b 2
)

set "GV=9.3.1"
set "JV=21"
if "%MC%"=="1.18.2" (set "GV=7.6.4"& set "JV=17")
if "%MC%"=="1.19.2" (set "GV=7.6.4"& set "JV=17")
if "%MC%"=="1.19.4" (set "GV=7.6.4"& set "JV=17")
if "%MC%"=="1.20.1" (set "GV=8.8"& set "JV=17")
if "%MC%"=="1.20.2" (set "GV=8.8"& set "JV=17")
if "%MC%"=="1.20.4" (set "GV=8.8"& set "JV=17")
if "%MC%"=="1.20.6" (set "GV=8.8"& set "JV=21")
if "%MC%"=="26.1.2" set "JV=25"
if "%MC%"=="26.2" set "JV=25"
if "%MC%"=="26.3" set "JV=25"

if not exist "%~dp0targets\%MC%\build.gradle" (
  echo ERROR: Unknown Forge target: %MC% 1>&2
  exit /b 2
)

REM 준비 타깃은 디렉터리가 존재해도 명시적으로 활성화하기 전에는 빌드하지 않는다.
REM Preparation targets remain unbuildable even when their directory exists until explicitly activated.
set "KWC_PREP_ENABLED="
for /f "tokens=2 delims==" %%A in ('findstr /b "kwc.prep.enabled=" "%~dp0targets\%MC%\gradle.properties" 2^>nul') do set "KWC_PREP_ENABLED=%%A"
if /I "%KWC_PREP_ENABLED%"=="false" (
  echo ERROR: Forge %MC% is a disabled PREP target and is not part of the release matrix. 1>&2
  echo        Finalize exact pins and set kwc.prep.enabled=true before a manual compatibility build. 1>&2
  exit /b 3
)

set "KWC_JAVA_RESULT=%TEMP%\kwc-java-%JV%-%RANDOM%-%RANDOM%.txt"
if exist "%KWC_JAVA_RESULT%" del /q "%KWC_JAVA_RESULT%" >nul 2>&1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0select-java.ps1" -Major %JV% -OutputFile "%KWC_JAVA_RESULT%"
if errorlevel 1 goto :jdkFailed
if not exist "%KWC_JAVA_RESULT%" goto :jdkFailed
set "SELECTED_JAVA_HOME="
set /p "SELECTED_JAVA_HOME="<"%KWC_JAVA_RESULT%"
del /q "%KWC_JAVA_RESULT%" >nul 2>&1
set "KWC_JAVA_RESULT="
if not defined SELECTED_JAVA_HOME goto :jdkEmpty
if not exist "%SELECTED_JAVA_HOME%\bin\java.exe" goto :jdkInvalid
if not exist "%SELECTED_JAVA_HOME%\bin\javac.exe" goto :jdkInvalid

set "JAVA_HOME=%SELECTED_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "KWC_GRADLE_VERSION=%GV%"

echo.
echo [KWC Forge] Minecraft %MC%
echo [KWC Forge] Gradle   %GV%
echo [KWC Forge] JDK      %JV% ^(%JAVA_HOME%^)
echo [KWC Forge] Gradle user home: %GRADLE_USER_HOME%
echo [KWC Forge] Temp: %TEMP%
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 exit /b 1

echo [KWC Forge] Building...
set "KWC_GRADLE_TASKS=clean build"
if /I "%KWC_SKIP_CLEAN%"=="1" set "KWC_GRADLE_TASKS=build"
call "%~dp0gradlew.bat" -p "%~dp0targets\%MC%" %KWC_GRADLE_TASKS% %KWC_GRADLE_ARGS%
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
  echo [KWC Forge] FAILED: Minecraft %MC% 1>&2
  exit /b %RC%
)

echo [KWC Forge] OK: Minecraft %MC%
exit /b 0

:jdkFailed
if exist "%KWC_JAVA_RESULT%" del /q "%KWC_JAVA_RESULT%" >nul 2>&1
set "KWC_JAVA_RESULT="
echo ERROR: JDK %JV% is required for Minecraft %MC%. 1>&2
echo        Automatic Temurin download also failed. Retry with network access or set KWC_JAVA%JV%_HOME. 1>&2
exit /b 1

:jdkEmpty
echo ERROR: JDK %JV% resolver returned an empty path for Minecraft %MC%. 1>&2
echo        Set KWC_JAVA%JV%_HOME explicitly and retry. 1>&2
exit /b 1

:jdkInvalid
echo ERROR: Resolved JDK %JV% path is invalid: "%SELECTED_JAVA_HOME%" 1>&2
echo        Expected bin\java.exe and bin\javac.exe below that directory. 1>&2
exit /b 1
