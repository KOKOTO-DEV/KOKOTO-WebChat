@echo off
REM KWC 파일 안내 / KWC file guide
REM 이 배치 파일은 Windows 빌드/검증 진입점 또는 helper이며 PowerShell/Gradle/Maven 작업을 안정적으로 연결한다.
REM This batch file is a Windows build/validation entry point or helper that coordinates PowerShell, Gradle, and Maven work.
REM CMD label/call 동작을 위해 CRLF 줄바꿈을 반드시 유지한다.
REM Keep CRLF line endings because CMD label/call behavior depends on the Windows batch format.

setlocal EnableExtensions
set "ROOT=%~dp0"
set "CACHE=%ROOT%.build-cache"
if not exist "%CACHE%" (
  echo [KWC] No project-local build cache exists: %CACHE%
  exit /b 0
)
echo [KWC] Removing project-local build cache:
echo       %CACHE%
rmdir /s /q "%CACHE%"
if exist "%CACHE%" (
  echo ERROR: Failed to remove %CACHE% 1>&2
  exit /b 1
)
echo [KWC] Project-local build cache removed.
exit /b 0
