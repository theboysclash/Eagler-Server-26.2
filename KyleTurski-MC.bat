@echo off
title KyleTurski MC
cd /d "%~dp0"
echo.
echo  KyleTurski MC
echo  Folder: %CD%
echo.
if not exist "%~dp0launch\KyleTurski-MC.bat" (
  echo  This file is not inside the full server folder.
  echo  Extract the ZIP, open that folder, and double-click KyleTurski-MC.bat there.
  echo.
  pause
  exit /b 1
)
call "%~dp0launch\KyleTurski-MC.bat"
echo.
echo  The launcher stopped. Read any errors above.
echo  Press any key to close this window.
pause >nul
exit /b 0
