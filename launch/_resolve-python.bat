@echo off
REM Sets USE_PY_LAUNCHER=1 or USE_PYTHON=1. Do not use setlocal here.
set "USE_PY_LAUNCHER="
set "USE_PYTHON="

py -3 -c "import sys" 2>nul
if not errorlevel 1 (
  set "USE_PY_LAUNCHER=1"
  exit /b 0
)

python -c "import sys" 2>nul
if not errorlevel 1 (
  set "USE_PYTHON=1"
  exit /b 0
)

echo.
echo Python 3 is missing or broken.
echo Install from https://www.python.org/downloads/ and check "Add python.exe to PATH".
echo In Windows Settings, turn OFF the Store "python.exe" app execution aliases.
exit /b 1
