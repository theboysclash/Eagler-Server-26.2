@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "server-26.2\paper.jar" (
  echo Server not set up yet. Run KyleTurski-MC.bat or:  launch\setup.bat
  pause
  exit /b 1
)

call "%~dp0_resolve-python.bat"
if errorlevel 1 (
  pause
  exit /b 1
)

echo Opening KyleTurski MC dashboard at http://127.0.0.1:8765
echo Leave this window open while you play. Closing it stops the dashboard.
echo.
%PY_CMD% dashboard\app.py --open --autostart
set "RC=%ERRORLEVEL%"
echo.
if not "%RC%"=="0" (
  echo Dashboard exited with error code %RC%.
  echo Run launch\doctor.bat - need Python 3 and Java 25.
) else (
  echo Dashboard closed.
)
echo If the window vanished instantly before, another dashboard may already be running.
echo Open http://127.0.0.1:8765 in your browser or close the other black window first.
echo.
pause
exit /b %RC%
