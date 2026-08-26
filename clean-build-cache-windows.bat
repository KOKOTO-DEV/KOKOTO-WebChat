@echo off
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
