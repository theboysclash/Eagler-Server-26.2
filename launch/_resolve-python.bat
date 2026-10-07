@echo off
REM Sets USE_PY_LAUNCHER=1 or USE_PYTHON=1. Do not use setlocal here.
set "USE_PY_LAUNCHER="
set "USE_PYTHON="
set "PY_LAUNCH="

py -3.14 -c "import sys" 2>nul
if not errorlevel 1 (
  set "USE_PY_LAUNCHER=1"
  set "PY_LAUNCH=py -3.14"
  exit /b 0
)

py -3 -c "import sys" 2>nul
if not errorlevel 1 (
  set "USE_PY_LAUNCHER=1"
  set "PY_LAUNCH=py -3"
  exit /b 0
)

python -c "import sys" 2>nul
if not errorlevel 1 (
  set "USE_PYTHON=1"
  exit /b 0
)

echo.
echo Python was not found on PATH.
echo Install Python from https://www.python.org/downloads/
echo On the first installer page, check "Add python.exe to PATH".
echo Then close this window and open KyleTurski-MC.bat again.
exit /b 1
