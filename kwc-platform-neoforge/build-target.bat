@echo off
setlocal EnableExtensions
set "MC=%~1"
for %%I in ("%~dp0..") do set "KWC_ROOT=%%~fI"
if defined KWC_GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_GRADLE_USER_HOME%"
) else if not defined GRADLE_USER_HOME (
  set "GRADLE_USER_HOME=%KWC_ROOT%\.build-cache\gradle"
)
if defined KWC_BUILD_TEMP (
  set "KWC_TARGET_TEMP=%KWC_BUILD_TEMP%\neoforge\%MC%"
) else (
  set "KWC_TARGET_TEMP=%KWC_ROOT%\.build-cache\tmp\neoforge\%MC%"
)
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%" >nul 2>&1
if not exist "%KWC_TARGET_TEMP%" mkdir "%KWC_TARGET_TEMP%" >nul 2>&1
set "TEMP=%KWC_TARGET_TEMP%"
set "TMP=%KWC_TARGET_TEMP%"
if "%MC%"=="" (echo Usage: build-target.bat ^<minecraft-version^> 1>&2& exit /b 2)
if not exist "%~dp0targets\%MC%\build.gradle" (echo ERROR: Unknown NeoForge target: %MC% 1>&2& exit /b 2)
for /f "tokens=2 delims==" %%A in ('findstr /b "kwc.java=" "%~dp0targets\%MC%\gradle.properties"') do set "JV=%%A"
for /f "tokens=2 delims==" %%A in ('findstr /b "kwc.gradle=" "%~dp0targets\%MC%\gradle.properties"') do set "GV=%%A"
set "KWC_JAVA_RESULT=%TEMP%\kwc-neo-java-%JV%-%RANDOM%-%RANDOM%.txt"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0select-java.ps1" -Major %JV% -OutputFile "%KWC_JAVA_RESULT%"
if errorlevel 1 goto :jdkFailed
set /p "SELECTED_JAVA_HOME="<"%KWC_JAVA_RESULT%"
del /q "%KWC_JAVA_RESULT%" >nul 2>&1
if not exist "%SELECTED_JAVA_HOME%\bin\java.exe" goto :jdkFailed
set "JAVA_HOME=%SELECTED_JAVA_HOME%"& set "PATH=%JAVA_HOME%\bin;%PATH%"& set "KWC_GRADLE_VERSION=%GV%"
echo [KWC NeoForge] Minecraft %MC% / Gradle %GV% / JDK %JV%
echo [KWC NeoForge] Gradle user home: %GRADLE_USER_HOME%
echo [KWC NeoForge] Temp: %TEMP%
call "%~dp0gradlew.bat" -p "%~dp0targets\%MC%" clean build %KWC_GRADLE_ARGS%
if errorlevel 1 exit /b %ERRORLEVEL%
echo [KWC NeoForge] OK: %MC%
exit /b 0
:jdkFailed
echo ERROR: JDK %JV% is required for NeoForge %MC%. Set KWC_JAVA%JV%_HOME or allow automatic Temurin setup. 1>&2
exit /b 1
