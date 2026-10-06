@echo off
cd /d "%~dp0.."
where py >nul 2>&1 && (py -3 launch\setup.py) || (python launch\setup.py)
if errorlevel 1 pause
else (
  echo.
  echo Next: launch\build-plugin.bat
  echo Then: launch\start-server.cmd
  pause
)
