@echo off
title KyleTurski MC - setup and dashboard
cd /d "%~dp0.."

set "LOG=%~dp0last-run.log"
echo ===== KyleTurski MC %date% %time% =====>"%LOG%"
echo Repo: %CD%>>"%LOG%"

echo.
echo  ========================================
echo   KyleTurski MC - one-click launcher
echo  ========================================
echo   Log: launch\last-run.log
echo.

call "%~dp0_resolve-python.bat"
if errorlevel 1 goto :fail
if not defined USE_PY_LAUNCHER if not defined USE_PYTHON goto :nopy

if defined USE_PY_LAUNCHER (
  echo Using: py -3
  echo Using: py -3>>"%LOG%"
  py -3 --version
) else (
  echo Using: python
  echo Using: python>>"%LOG%"
  python --version
)
if errorlevel 1 goto :fail

java -version >>"%LOG%" 2>&1
if errorlevel 1 (
  echo.
  echo WARNING: Java not on PATH. You need Java 25 for the server and HubEconomy build.
  echo https://adoptium.net/temurin/releases/?version=25
  echo.
)

echo [1/4] Server files (Paper 26.2 + EaglerXPaper)...
call "%~dp0_run-python.bat" launch\setup.py
if errorlevel 1 (
  echo Setup failed>>"%LOG%"
  goto :fail
)

echo.
echo [2/4] Building HubEconomy (login, shop, hub)...
call "%~dp0build-plugin.bat"
if errorlevel 1 (
  echo HubEconomy build failed>>"%LOG%"
  goto :fail
)

echo.
echo [3/4] Syncing plugins...
call "%~dp0_run-python.bat" launch\setup.py
if errorlevel 1 goto :fail

if exist "import-worlds\hub\level.dat" (
  if not exist "server-26.2\hub\level.dat" (
    echo Importing hub world...
    call "%~dp0import-hub.bat" /nopause
  )
)

echo.
echo [4/4] Starting dashboard (this window must stay open)...
echo.
call "%~dp0start-server.bat"
exit /b %ERRORLEVEL%

:nopy
echo Python was not configured after resolve - see launch\last-run.log
goto :fail

:fail
echo.>>"%LOG%"
echo FAILED>>"%LOG%"
echo.
echo === Something went wrong ===
echo Open this file in Notepad:  launch\last-run.log
echo Or run:  launch\doctor.bat
echo.
type "%LOG%"
echo.
pause
exit /b 1
