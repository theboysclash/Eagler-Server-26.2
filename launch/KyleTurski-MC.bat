@echo off
setlocal EnableExtensions
title KyleTurski MC — setup and dashboard
cd /d "%~dp0.."

where py >nul 2>&1 && (set "PY=py -3") || (set "PY=python")

echo.
echo  ========================================
echo   KyleTurski MC — one-click launcher
echo  ========================================
echo.

%PY% --version >nul 2>&1 || (
  echo Python 3 is required. Install from https://www.python.org/downloads/
  echo Then run this file again.
  pause
  exit /b 1
)

echo [1/4] Server files (Paper 26.2 + EaglerXPaper)...
%PY% launch\setup.py
if errorlevel 1 goto :fail

if not exist "server-26.2\plugins\HubEconomy.jar" (
  echo.
  echo [2/4] Building HubEconomy (/sell, /shop, hub)...
  call "%~dp0build-plugin.bat"
  if errorlevel 1 goto :fail
) else (
  echo.
  echo [2/4] HubEconomy already built.
)

echo.
echo [3/4] Syncing plugins into server-26.2...
%PY% launch\setup.py
if errorlevel 1 goto :fail

if exist "import-worlds\hub\level.dat" (
  if not exist "server-26.2\hub\level.dat" (
    echo.
    echo Importing custom hub from import-worlds\hub ...
    call "%~dp0import-hub.bat"
  )
)

echo.
echo [4/4] Opening dashboard — click Start if the server is not already running.
echo       Kill ALL: use the red Kill all button in the sidebar.
echo.
call "%~dp0start-server.bat"
exit /b 0

:fail
echo.
echo Something failed. Run launch\doctor.bat for details.
pause
exit /b 1
