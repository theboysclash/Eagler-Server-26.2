@echo off
REM Sets PY_CMD to a working Python 3 launcher (py -3 or python). Exits 1 on failure.
set "PY_CMD="
where py >nul 2>&1 && (
  py -3 -c "import sys" 2>nul && set "PY_CMD=py -3" && exit /b 0
)
where python >nul 2>&1 && (
  python -c "import sys" 2>nul && set "PY_CMD=python" && exit /b 0
)
echo.
echo Python 3 is missing or broken.
echo Install from https://www.python.org/downloads/ and check "Add python.exe to PATH".
echo If Windows opened the Store, disable the python.exe app execution alias in Settings.
exit /b 1
