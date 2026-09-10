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
  set "KWC_TARGET_TEMP=%KWC_BUILD_TEMP%\fabric\%MC%"
) else (
  set "KWC_TARGET_TEMP=%KWC_ROOT%\.build-cache\tmp\fabric\%MC%"
)
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>&1
if not exist "%KWC_TARGET_TEMP%" mkdir "%KWC_TARGET_TEMP%" >nul 2>&1
set "TEMP=%KWC_TARGET_TEMP%"
set "TMP=%KWC_TARGET_TEMP%"
if "%MC%"=="" (echo Usage: build-target.bat ^<minecraft-version^> [--fast] 1>&2& exit /b 2)
if not exist "%~dp0targets\%MC%\build.gradle" (echo ERROR: Unknown Fabric target: %MC% 1>&2& exit /b 2)

REM 준비 타깃은 디렉터리가 존재해도 명시적으로 활성화하기 전에는 빌드하지 않는다.
REM Preparation targets remain unbuildable even when their directory exists until explicitly activated.
set "KWC_PREP_ENABLED="
for /f "tokens=2 delims==" %%A in ('findstr /b "kwc.prep.enabled=" "%~dp0targets\%MC%\gradle.properties" 2^>nul') do set "KWC_PREP_ENABLED=%%A"
if /I "%KWC_PREP_ENABLED%"=="false" (
  echo ERROR: Fabric %MC% is a disabled PREP target and is not part of the release matrix. 1>&2
  echo        Finalize exact pins and set kwc.prep.enabled=true before a manual compatibility build. 1>&2
  exit /b 3
)
for /f "tokens=2 delims==" %%A in ('findstr /b "kwc.java=" "%~dp0targets\%MC%\gradle.properties"') do set "TARGET_JV=%%A"
for /f "tokens=2 delims==" %%A in ('findstr /b "kwc.build-java=" "%~dp0targets\%MC%\gradle.properties"') do set "BUILD_JV=%%A"
if not defined BUILD_JV set "BUILD_JV=%TARGET_JV%"
for /f "tokens=2 delims==" %%A in ('findstr /b "kwc.gradle=" "%~dp0targets\%MC%\gradle.properties"') do set "GV=%%A"
set "KWC_JAVA_RESULT=%TEMP%\kwc-fabric-java-%BUILD_JV%-%RANDOM%-%RANDOM%.txt"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0select-java.ps1" -Major %BUILD_JV% -OutputFile "%KWC_JAVA_RESULT%"
if errorlevel 1 goto :jdkFailed
set /p "SELECTED_JAVA_HOME="<"%KWC_JAVA_RESULT%"
del /q "%KWC_JAVA_RESULT%" >nul 2>&1
if not exist "%SELECTED_JAVA_HOME%\bin\java.exe" goto :jdkFailed
set "JAVA_HOME=%SELECTED_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "KWC_GRADLE_VERSION=%GV%"
echo [KWC Fabric] Minecraft %MC% / Gradle %GV% / build JDK %BUILD_JV% / target Java %TARGET_JV%
echo [KWC Fabric] Gradle user home: %GRADLE_USER_HOME%
echo [KWC Fabric] Temp: %TEMP%
set "KWC_GRADLE_TASKS=clean build"
if /I "%KWC_SKIP_CLEAN%"=="1" set "KWC_GRADLE_TASKS=build"
call "%~dp0gradlew.bat" -p "%~dp0targets\%MC%" %KWC_GRADLE_TASKS% %KWC_GRADLE_ARGS%
if errorlevel 1 exit /b %ERRORLEVEL%
echo [KWC Fabric] OK: %MC%
exit /b 0
:jdkFailed
echo ERROR: Build JDK %BUILD_JV% is required for Fabric %MC% ^(target Java %TARGET_JV%^). Set KWC_JAVA%BUILD_JV%_HOME or allow automatic Temurin setup. 1>&2
exit /b 1
