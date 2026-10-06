@echo off
cd /d "%~dp0.."
set "LOG=%~dp0last-run.log"

if not exist "server-26.2\paper.jar" (
  echo Server not set up. Run KyleTurski-MC.bat first.
  echo.>>"%LOG%"
  echo ERROR: paper.jar missing>>"%LOG%"
  pause
  exit /b 1
)

call "%~dp0_resolve-python.bat"
if errorlevel 1 (
  pause
  exit /b 1
)

echo Opening dashboard: http://127.0.0.1:8765
echo Leave this window OPEN while you play.
echo Dashboard starting...>>"%LOG%"
echo.

call "%~dp0_run-python.bat" dashboard\app.py --open --autostart
set "RC=%ERRORLEVEL%"
echo Dashboard exit code: %RC%>>"%LOG%"

echo.
if not "%RC%"=="0" echo Dashboard error code %RC%. See launch\last-run.log
if "%RC%"=="0" echo Dashboard closed.
echo.
pause
exit /b %RC%
