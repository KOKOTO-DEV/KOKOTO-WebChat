@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "ROOT=%~dp0"
for %%I in ("%~dp0.") do set "ROOT_PATH=%%~fI"

if /I "%~1"=="--internal-worker" goto :internalWorker

set "SEL_BUKKIT=0"
set "SEL_FABRIC=0"
set "SEL_NEOFORGE=0"
set "SEL_FORGE=0"
set "SELECT_ANY=0"
set "SELECT_ALL=0"
set "FAST_MODE=0"
set "PARALLEL_MODE=0"

:parseArgs
if "%~1"=="" goto :argsDone
if /I "%~1"=="--all" (
  set "SELECT_ALL=1"
  set "SELECT_ANY=1"
  shift
  goto :parseArgs
)
if /I "%~1"=="--bukkit" (
  set "SEL_BUKKIT=1"
  set "SELECT_ANY=1"
  shift
  goto :parseArgs
)
if /I "%~1"=="--fabric" (
  set "SEL_FABRIC=1"
  set "SELECT_ANY=1"
  shift
  goto :parseArgs
)
if /I "%~1"=="--neoforge" (
  set "SEL_NEOFORGE=1"
  set "SELECT_ANY=1"
  shift
  goto :parseArgs
)
if /I "%~1"=="--forge" (
  set "SEL_FORGE=1"
  set "SELECT_ANY=1"
  shift
  goto :parseArgs
)
if /I "%~1"=="--fast" (
  set "FAST_MODE=1"
  shift
  goto :parseArgs
)
if /I "%~1"=="--parallel" (
  set "PARALLEL_MODE=1"
  shift
  goto :parseArgs
)
if /I "%~1"=="--help" goto :usage
if /I "%~1"=="-h" goto :usage
if /I "%~1"=="/?" goto :usage

echo ERROR: Unknown option: %~1 1>&2
goto :usageError

:argsDone
if "%SELECT_ANY%"=="0" set "SELECT_ALL=1"
if "%SELECT_ALL%"=="1" (
  set "SEL_BUKKIT=1"
  set "SEL_FABRIC=1"
  set "SEL_NEOFORGE=1"
  set "SEL_FORGE=1"
)

call :setupCommon
if errorlevel 1 exit /b %ERRORLEVEL%

set /a EXPECTED_JARS=0
set /a SELECTED_PLATFORMS=0
set "PLATFORMS="
set "SCOPE_LABEL="
if "%SEL_BUKKIT%"=="1" (
  set /a EXPECTED_JARS+=1
  set /a SELECTED_PLATFORMS+=1
  call :appendPlatform bukkit
)
if "%SEL_FABRIC%"=="1" (
  set /a EXPECTED_JARS+=16
  set /a SELECTED_PLATFORMS+=1
  call :appendPlatform fabric
)
if "%SEL_NEOFORGE%"=="1" (
  set /a EXPECTED_JARS+=12
  set /a SELECTED_PLATFORMS+=1
  call :appendPlatform neoforge
)
if "%SEL_FORGE%"=="1" (
  set /a EXPECTED_JARS+=16
  set /a SELECTED_PLATFORMS+=1
  call :appendPlatform forge
)
if "%EXPECTED_JARS%"=="0" (
  echo ERROR: No build platform selected. 1>&2
  exit /b 2
)

set "FULL_MATRIX=0"
if "%EXPECTED_JARS%"=="45" if "%SELECTED_PLATFORMS%"=="4" set "FULL_MATRIX=1"
set "FINAL_RELEASE_MODE=0"
if "%FULL_MATRIX%"=="1" if "%FAST_MODE%"=="0" set "FINAL_RELEASE_MODE=1"
set "BUKKIT_FIRST_PARALLEL=0"
if "%PARALLEL_MODE%"=="1" if "%SEL_BUKKIT%"=="1" if %SELECTED_PLATFORMS% GTR 1 set "BUKKIT_FIRST_PARALLEL=1"
if "%FULL_MATRIX%"=="1" (
  set "SCOPE_LABEL=all"
) else (
  set "SCOPE_LABEL=%PLATFORMS:,=-%"
)

if "%FAST_MODE%"=="1" (
  set "KWC_SKIP_CLEAN=1"
  if defined KWC_GRADLE_ARGS (
    set "KWC_GRADLE_ARGS=--build-cache !KWC_GRADLE_ARGS!"
  ) else (
    set "KWC_GRADLE_ARGS=--build-cache"
  )
) else (
  set "KWC_SKIP_CLEAN=0"
)

set "LOGDIR=%ROOT%validation-logs"
if "%FINAL_RELEASE_MODE%"=="1" (
  set "OUTDIR=%ROOT%release-5.2.1"
) else (
  set "OUTDIR=%ROOT%build-5.2.1\%SCOPE_LABEL%"
)

if exist "%LOGDIR%" rmdir /s /q "%LOGDIR%"
if exist "%OUTDIR%" rmdir /s /q "%OUTDIR%"
mkdir "%LOGDIR%" || exit /b 1
mkdir "%OUTDIR%" || exit /b 1

set "KWC_VALIDATION_LOGDIR=%LOGDIR%"
set "KWC_VALIDATION_OUTDIR=%OUTDIR%"
set "KWC_VALIDATION_FAST=%FAST_MODE%"
set "KWC_VALIDATION_PARALLEL=%PARALLEL_MODE%"

echo ============================================================
if "%FINAL_RELEASE_MODE%"=="1" (
  echo KOKOTO WebChat 5.2.1 exact-target release validation
  echo Bukkit 1 + Fabric 16 + NeoForge 12 + Forge 16 = 45 JARs
) else (
  echo KOKOTO WebChat 5.2.1 Windows build
  echo Platforms: %PLATFORMS%
)
echo ============================================================
echo.
echo Build cache root: %KWC_BUILD_CACHE_ROOT%
echo Gradle user home: %GRADLE_USER_HOME%
echo Maven repository: %KWC_MAVEN_REPO%
echo Build temp:       %TEMP%
if "%FAST_MODE%"=="1" (
  echo Build mode:       FAST ^(skip clean + reuse outputs/cache^)
) else (
  echo Build mode:       CLEAN
)
if "%BUKKIT_FIRST_PARALLEL%"=="1" (
  echo Scheduling:       BUKKIT FIRST, THEN PARALLEL LOADERS
) else if "%PARALLEL_MODE%"=="1" (
  echo Scheduling:       PARALLEL
) else (
  echo Scheduling:       SEQUENTIAL
)
echo Progress:         live overall/platform target counts; --parallel opens one build window per active platform
echo Output:           %OUTDIR%
echo Logs:             %LOGDIR%
echo.

echo Running Forge build configuration guard...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-forge-build-config.ps1" -ProjectRoot "%ROOT_PATH%"
if errorlevel 1 exit /b 1

echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%build-progress-windows.ps1" -Root "%ROOT_PATH%" -LogDir "%LOGDIR%" -Platforms "%PLATFORMS%" -Parallel %PARALLEL_MODE%
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" exit /b %RC%

echo.
echo Running loader-neutral security / relay regression harnesses...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-core-regression-harness.ps1" -ProjectRoot "%ROOT_PATH%"
if errorlevel 1 exit /b 1

echo.
echo Running loader-neutral adapter behavior harness...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-adapter-harness.ps1" -ProjectRoot "%ROOT_PATH%"
if errorlevel 1 exit /b 1

echo.
echo Running loader-neutral config migration regression harness...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-config-migration-harness.ps1" -ProjectRoot "%ROOT_PATH%"
if errorlevel 1 exit /b 1

rem Deployable JARs are validated and copied by each platform worker as soon as
rem that platform passes. This keeps successful outputs available even when a
rem different parallel loader later fails.
goto :finalize

:appendPlatform
if not defined PLATFORMS (
  set "PLATFORMS=%~1"
) else (
  set "PLATFORMS=!PLATFORMS!,%~1"
)
exit /b 0

:setupCommon
if not defined KWC_PATH_PREFLIGHT_DONE (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%check-build-path.ps1" -Root "%ROOT_PATH%"
  if errorlevel 1 exit /b 1
  set "KWC_PATH_PREFLIGHT_DONE=1"
)
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
exit /b 0

:collectBukkit
set "J=%ROOT%kwc-platform-bukkit\target\KOKOTO-WebChat-5.2.1-Bukkit-1.18-26.2.jar"
if not exist "%J%" (echo ERROR: Bukkit JAR missing: %J% 1>&2& exit /b 1)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-adapter-packaging.ps1" -JarPath "%J%" -Platform "Bukkit"
if errorlevel 1 exit /b 1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-archive-runtime-harness.ps1" -ProjectRoot "%ROOT_PATH%" -JarPath "%J%"
if errorlevel 1 exit /b 1
copy /y "%J%" "%OUTDIR%\" >nul
exit /b 0

:collectFabric
for %%M in (1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  set "J=%ROOT%kwc-platform-fabric\targets\%%M\build\libs\KOKOTO-WebChat-5.2.1-Fabric-%%M.jar"
  if not exist "!J!" (echo ERROR: Fabric %%M JAR missing: !J! 1>&2& exit /b 1)
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-release-mod-jar.ps1" -JarPath "!J!" -Platform "Fabric" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-mod-runtime-dependencies.ps1" -JarPath "!J!" -Platform "Fabric" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-adapter-packaging.ps1" -JarPath "!J!" -Platform "Fabric" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  copy /y "!J!" "%OUTDIR%\" >nul
)
exit /b 0

:collectNeoForge
for %%M in (1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  set "J=%ROOT%kwc-platform-neoforge\targets\%%M\build\libs\KOKOTO-WebChat-5.2.1-NeoForge-%%M.jar"
  if not exist "!J!" (echo ERROR: NeoForge %%M JAR missing: !J! 1>&2& exit /b 1)
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-release-mod-jar.ps1" -JarPath "!J!" -Platform "NeoForge" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-mod-runtime-dependencies.ps1" -JarPath "!J!" -Platform "NeoForge" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-neoforge-metadata.ps1" -JarPath "!J!" -MinecraftVersion "%%M" -ProjectRoot "%ROOT_PATH%"
  if errorlevel 1 exit /b 1
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-adapter-packaging.ps1" -JarPath "!J!" -Platform "NeoForge" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  copy /y "!J!" "%OUTDIR%\" >nul
)
exit /b 0

:collectForge
for %%M in (1.18.2 1.19.2 1.19.4 1.20.1 1.20.2 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11 26.1.2 26.2) do (
  set "J=%ROOT%kwc-platform-forge\targets\%%M\build\libs\KOKOTO-WebChat-5.2.1-Forge-%%M.jar"
  if not exist "!J!" (echo ERROR: Forge %%M JAR missing: !J! 1>&2& exit /b 1)
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-release-mod-jar.ps1" -JarPath "!J!" -Platform "Forge" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-mod-runtime-dependencies.ps1" -JarPath "!J!" -Platform "Forge" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%validate-adapter-packaging.ps1" -JarPath "!J!" -Platform "Forge" -MinecraftVersion "%%M"
  if errorlevel 1 exit /b 1
  copy /y "!J!" "%OUTDIR%\" >nul
)
exit /b 0

:finalize
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $files=[System.IO.Directory]::GetFiles('%OUTDIR%','*.jar'); [Array]::Sort($files,[System.StringComparer]::OrdinalIgnoreCase); $lines=[System.Collections.Generic.List[string]]::new(); foreach($f in $files){ $h=Get-FileHash -Algorithm SHA256 -LiteralPath $f; $lines.Add(('{0}  {1}' -f $h.Hash.ToLowerInvariant(),[System.IO.Path]::GetFileName($f))) }; [System.IO.File]::WriteAllLines('%OUTDIR%\SHA256SUMS.txt',$lines,[System.Text.Encoding]::ASCII)"
if errorlevel 1 exit /b 1
for /f %%C in ('dir /b /a-d "%OUTDIR%\*.jar" ^| find /c /v ""') do set "JARCOUNT=%%C"
if not "%JARCOUNT%"=="%EXPECTED_JARS%" (
  echo ERROR: Expected %EXPECTED_JARS% deployable JAR^(s^), found %JARCOUNT%. 1>&2
  exit /b 1
)
echo.
echo ============================================================
if "%FINAL_RELEASE_MODE%"=="1" (
  echo FINAL RELEASE BUILD PASS
) else (
  echo BUILD PASS
  echo Platforms: %PLATFORMS%
  if "%FAST_MODE%"=="1" echo Mode: FAST / incremental ^(not a final release validation^)
)
if "%BUKKIT_FIRST_PARALLEL%"=="1" (echo Scheduling: BUKKIT FIRST, THEN PARALLEL LOADERS) else if "%PARALLEL_MODE%"=="1" echo Scheduling: PARALLEL
echo Deployable JARs: %JARCOUNT%
echo Output: %OUTDIR%
echo Logs:   %LOGDIR%
echo ============================================================
exit /b 0

:internalWorker
set "WORKER_PLATFORM=%~2"
if "%WORKER_PLATFORM%"=="" exit /b 2
call :setupCommon
if errorlevel 1 exit /b %ERRORLEVEL%
if not defined OUTDIR if defined KWC_VALIDATION_OUTDIR set "OUTDIR=%KWC_VALIDATION_OUTDIR%"
if not defined OUTDIR (
  echo ERROR: Internal worker output directory is not defined. 1>&2
  exit /b 2
)
if not exist "%OUTDIR%" mkdir "%OUTDIR%" >nul 2>&1
if /I "%WORKER_PLATFORM%"=="bukkit" goto :workerBukkit
if /I "%WORKER_PLATFORM%"=="fabric" goto :workerFabric
if /I "%WORKER_PLATFORM%"=="neoforge" goto :workerNeoForge
if /I "%WORKER_PLATFORM%"=="forge" goto :workerForge
echo ERROR: Unknown internal worker platform: %WORKER_PLATFORM% 1>&2
exit /b 2

:workerBukkit
set "MAVEN_CMD="
for /f "delims=" %%I in ('where mvn.cmd 2^>nul') do if not defined MAVEN_CMD set "MAVEN_CMD=%%I"
if not defined MAVEN_CMD for /f "delims=" %%I in ('where mvn.exe 2^>nul') do if not defined MAVEN_CMD set "MAVEN_CMD=%%I"
if not defined MAVEN_CMD (
  echo [KWC Bukkit] Maven was not found on PATH. Bootstrapping Apache Maven 3.9.16 locally...
  call :bootstrapMaven
  if errorlevel 1 exit /b 1
)
echo [KWC Bukkit] Maven: %MAVEN_CMD%
call :selectJava 17
if errorlevel 1 exit /b 1
set "JAVA_HOME=%SELECTED_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_GOALS=clean package"
if /I "%KWC_SKIP_CLEAN%"=="1" set "MAVEN_GOALS=package"
echo [KWC Bukkit] JDK 17: %JAVA_HOME%
if /I "%KWC_SKIP_CLEAN%"=="1" (
  echo [KWC Bukkit] FAST package; existing target outputs and Maven cache are reused.
) else (
  echo [KWC Bukkit] CLEAN package.
)
pushd "%ROOT%"
call "%MAVEN_CMD%" -Dmaven.repo.local="%KWC_MAVEN_REPO%" -pl kwc-platform-bukkit -am %MAVEN_GOALS%
set "RC=%ERRORLEVEL%"
popd
if not "%RC%"=="0" exit /b %RC%
call :collectBukkit
if errorlevel 1 exit /b %ERRORLEVEL%
echo [KWC Bukkit] OK
exit /b 0

:workerFabric
call "%ROOT%kwc-platform-fabric\build-all.bat"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" exit /b %RC%
call :collectFabric
if errorlevel 1 exit /b %ERRORLEVEL%
exit /b 0

:workerNeoForge
call "%ROOT%kwc-platform-neoforge\build-all.bat"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" exit /b %RC%
call :collectNeoForge
if errorlevel 1 exit /b %ERRORLEVEL%
exit /b 0

:workerForge
call "%ROOT%kwc-platform-forge\build-all.bat"
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" exit /b %RC%
call :collectForge
if errorlevel 1 exit /b %ERRORLEVEL%
exit /b 0

:bootstrapMaven
set "MAVEN_VERSION=3.9.16"
set "MAVEN_TOOL_ROOT=%ROOT%.tools"
set "MAVEN_HOME=%MAVEN_TOOL_ROOT%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if exist "%MAVEN_CMD%" exit /b 0
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%bootstrap-maven-windows.ps1" -Version "%MAVEN_VERSION%" -ToolRoot "%MAVEN_TOOL_ROOT%"
if errorlevel 1 (
  echo ERROR: Apache Maven bootstrap failed. 1>&2
  echo        Install Maven manually or make mvn.cmd available on PATH, then retry. 1>&2
  exit /b 1
)
if not exist "%MAVEN_CMD%" (
  echo ERROR: Local Maven bootstrap completed but mvn.cmd is missing: %MAVEN_CMD% 1>&2
  exit /b 1
)
exit /b 0

:selectJava
set "SELECTED_JAVA_HOME="
set "KWC_JAVA_RESULT=%TEMP%\kwc-final-java-%~1-%RANDOM%-%RANDOM%.txt"
if exist "%KWC_JAVA_RESULT%" del /q "%KWC_JAVA_RESULT%" >nul 2>&1
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%kwc-platform-forge\select-java.ps1" -Major %~1 -OutputFile "%KWC_JAVA_RESULT%" -Label "[KWC Bukkit]"
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

:usage
echo Usage: validate-release-windows.bat [platform ...] [--fast] [--parallel]
echo.
echo Platforms ^(may be combined; default is all^):
echo   --all        Build Bukkit + Fabric + NeoForge + Forge.
echo   --bukkit     Build only the Bukkit/Paper JAR.
echo   --fabric     Build all 16 Fabric exact-target JARs.
echo   --neoforge   Build all 12 NeoForge exact-target JARs.
echo   --forge      Build all 16 Forge exact-target JARs.
echo.
echo Build mode:
echo   ^(default^)   Clean selected build outputs before compiling.
echo   --fast       Skip clean, reuse existing outputs/dependency caches,
echo                and enable the Gradle build cache. Not final validation.
echo   --parallel   If Bukkit is selected, build Bukkit first; after it passes,
echo                open Fabric / NeoForge / Forge in separate build windows and run them in parallel. Clean is
echo                still performed unless --fast is also supplied.
echo.
echo Progress:
echo   The main window shows elapsed time, overall completed targets,
echo   each platform count, and the current target. In --parallel mode,
echo   each platform also has its own live build window. A platform is validated
echo   and copied to the output as soon as that platform passes; successful
echo   outputs remain available even if another parallel platform later fails.
echo   Full logs remain under validation-logs\.
echo.
echo Examples:
echo   validate-release-windows.bat --bukkit --fast
echo   validate-release-windows.bat --forge --fast
echo   validate-release-windows.bat --fabric --neoforge --fast
echo   validate-release-windows.bat --parallel
echo   validate-release-windows.bat --all --parallel
echo   validate-release-windows.bat
echo.
echo A clean full 45-target build prints FINAL RELEASE BUILD PASS whether
echo it is sequential or uses Bukkit-first + parallel loader scheduling.
exit /b 0

:usageError
call :usage
exit /b 2
