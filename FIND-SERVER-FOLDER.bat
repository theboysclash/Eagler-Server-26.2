@echo off
title Find KyleTurski MC folder
echo.
echo Searching for KyleTurski-MC.bat under your user folder...
echo This can take 1-2 minutes. Please wait.
echo.

set "FOUND=0"
for /f "delims=" %%D in ('dir /s /b "%USERPROFILE%\KyleTurski-MC.bat" 2^>nul') do (
  echo FOUND: %%~dpD
  set "FOUND=1"
)

if "%FOUND%"=="0" (
  echo.
  echo No copy found yet. Common places to check manually:
  echo   Downloads\Eagler-Server-26.2-main
  echo   Documents\GitHub\Eagler-Server-26.2
  echo   Desktop
  echo.
  echo Download the project ZIP from GitHub if you have not:
  echo   https://github.com/theboysclash/Eagler-Server-26.2
  echo   Code - Download ZIP - Extract All
) else (
  echo.
  echo Open one of the folders above in File Explorer.
  echo Double-click KyleTurski-MC.bat inside that folder.
)

echo.
cmd /k
