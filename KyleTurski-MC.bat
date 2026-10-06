@echo off
cd /d "%~dp0"
title KyleTurski MC

if not exist "%~dp0launch\KyleTurski-MC.bat" (
  echo.
  echo  *** WRONG FOLDER ***
  echo.
  echo  This file must be inside the Eagler-Server-26.2 project folder
  echo  (the folder that contains a "launch" folder).
  echo.
  echo  You ran it from:
  echo    %CD%
  echo.
  echo  Do NOT copy only this .bat to Desktop.
  echo.
  echo  Get the project:
  echo    1. Download ZIP from GitHub and Extract All
  echo    2. Open the extracted folder
  echo    3. Double-click KyleTurski-MC.bat THERE
  echo.
  echo  Or run FIND-SERVER-FOLDER.bat from the ZIP to search your PC.
  echo.
  cmd /k
  exit /b 1
)

echo.
echo  Server folder: %CD%
echo  This window stays open. Wait for setup to finish or read any errors.
echo.
cmd /k call "%~dp0launch\KyleTurski-MC.bat" --inner
