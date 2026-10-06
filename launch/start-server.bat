@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "server-26.2\paper.jar" (
  echo Server not set up yet. Run:  launch\setup.bat
  echo   or:  py -3 launch\setup.py
  pause
  exit /b 1
)

where py >nul 2>&1 && (set "PY=py -3") || (set "PY=python")
echo Opening KyleTurski MC dashboard at http://127.0.0.1:8765
echo Leave this window open. If the browser does not open, go to that address yourself.
echo.
%PY% dashboard\app.py --open --autostart
if errorlevel 1 (
  echo.
  echo Dashboard failed to start. Run launch\doctor.bat for help.
  echo You can still run: launch\start-paper-only.bat
  pause
)
