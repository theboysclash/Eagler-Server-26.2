@echo off
setlocal
cd /d "%~dp0.."
set "SRC=%CD%\import-worlds\hub"
set "DEST=%CD%\server-26.2\hub"

if not exist "%SRC%\level.dat" (
  echo No hub world found.
  echo Put your map in: import-worlds\hub\level.dat and region\
  exit /b 1
)
if not exist "%CD%\server-26.2" (
  echo Run: python launch\setup.py
  exit /b 1
)

echo Copying hub world to server-26.2\hub ...
if exist "%DEST%" rmdir /s /q "%DEST%"
mkdir "%DEST%"
xcopy /e /i /y "%SRC%\*" "%DEST%\"
echo Done. Restart the server. Run /sethub at your spawn once.
pause
