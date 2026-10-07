@echo off
cd /d "%~dp0.."
set "LOG=%~dp0last-run.log"
set "DASH=http://127.0.0.1:8765"

if not exist "server-26.2\paper.jar" (
  echo Server not set up. Run KyleTurski-MC.bat first.
  echo.>>"%LOG%"
  echo ERROR: paper.jar missing>>"%LOG%"
  pause
  exit /b 1
)

call "%~dp0_resolve-python.bat"
if errorlevel 1 (
  echo Python was not found, so the dashboard cannot start.
  echo Opening the address anyway. It will load after Python is fixed.
  start "" "%DASH%"
  pause
  exit /b 1
)

echo.
echo  Dashboard: %DASH%
echo  Leave this window OPEN while you play.
echo.
echo Dashboard starting...>>"%LOG%"

REM Open the browser even if Python's own browser call fails.
start "KyleTurski browser" /MIN "%~dp0open-browser.bat"

call "%~dp0_run-python.bat" dashboard\app.py --open --autostart
set "RC=%ERRORLEVEL%"
echo Dashboard exit code: %RC%>>"%LOG%"

echo.
if not "%RC%"=="0" echo Dashboard error code %RC%. See launch\last-run.log
if "%RC%"=="0" echo Dashboard closed.
echo The address is still %DASH%
echo.
start "" "%DASH%"
pause
exit /b %RC%
