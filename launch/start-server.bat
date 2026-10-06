@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "server-26.2\paper.jar" (
  echo Server not set up yet. Run:  python launch\setup.py
  exit /b 1
)

echo Opening the KyleTurski MC dashboard. Leave this window open.
python dashboard\app.py --open --autostart
if errorlevel 1 pause
