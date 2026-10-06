@echo off
REM Keeps the window open even if setup fails instantly (cmd /k).
cd /d "%~dp0"
if not exist "launch\KyleTurski-MC.bat" (
  echo Run this file from the Eagler-Server repo folder.
  echo This folder is missing launch\KyleTurski-MC.bat
  pause
  exit /b 1
)
title KyleTurski MC
echo.
echo This window will stay open. If setup fails, read the red text below.
echo.
cmd /k "call "%~dp0launch\KyleTurski-MC.bat""
