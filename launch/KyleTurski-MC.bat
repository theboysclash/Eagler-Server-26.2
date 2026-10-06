@echo off
setlocal EnableExtensions
title KyleTurski MC - setup and dashboard
cd /d "%~dp0.."

echo.
echo  ========================================
echo   KyleTurski MC - one-click launcher
echo  ========================================
echo.

call "%~dp0_resolve-python.bat"
if errorlevel 1 goto :fail

echo Using: %PY_CMD%
%PY_CMD% --version
if errorlevel 1 goto :fail

java -version >nul 2>&1
if errorlevel 1 (
  echo.
  echo WARNING: Java not found on PATH. Setup may fail; you need Java 25 to run the server.
  echo Install Temurin 25: https://adoptium.net/temurin/releases/?version=25
  echo.
)

echo [1/4] Server files (Paper 26.2 + EaglerXPaper)...
%PY_CMD% launch\setup.py
if errorlevel 1 goto :fail

if not exist "server-26.2\plugins\HubEconomy.jar" (
  echo.
  echo [2/4] Building HubEconomy (/sell, /shop, hub) - needs Java 25...
  call "%~dp0build-plugin.bat"
  if errorlevel 1 goto :fail
) else (
  echo.
  echo [2/4] HubEconomy already built.
)

echo.
echo [3/4] Syncing plugins into server-26.2...
%PY_CMD% launch\setup.py
if errorlevel 1 goto :fail

if exist "import-worlds\hub\level.dat" (
  if not exist "server-26.2\hub\level.dat" (
    echo.
    echo Importing custom hub from import-worlds\hub ...
    call "%~dp0import-hub.bat" /nopause
    if errorlevel 1 goto :fail
  )
)

echo.
echo [4/4] Opening dashboard - click Start if the server is not already running.
echo       Use Kill all in the sidebar to force-stop Minecraft and Caddy.
echo.
call "%~dp0start-server.bat"
exit /b %ERRORLEVEL%

:fail
echo.
echo === Launcher stopped ===
echo Run launch\doctor.bat for a checklist.
echo.
pause
exit /b 1
