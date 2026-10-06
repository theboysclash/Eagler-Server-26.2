@echo off
cd /d "%~dp0"
if /I "%~1"=="RUN" goto :run
start "KyleTurski MC" cmd /k "%~f0" RUN
exit /b 0

:run
title KyleTurski MC
cd /d "%~dp0"
echo.
echo  Server folder:
echo  %CD%
echo.
if not exist "%~dp0launch\KyleTurski-MC.bat" (
  echo  This folder is incomplete. You need the full project, not only this file.
  echo  Download ZIP from GitHub, Extract All, then double-click KyleTurski-MC.bat
  echo  inside the extracted folder.
  echo.
  echo  Type exit and press Enter to close.
  exit /b 1
)
call "%~dp0launch\KyleTurski-MC.bat"
echo.
echo  Launcher finished. This window stays open.
echo  Type exit and press Enter to close.
exit /b 0
