@echo off
REM Run: call _run-python.bat script.py [args...]
if defined PY_LAUNCH (
  %PY_LAUNCH% %*
  exit /b %ERRORLEVEL%
)
if defined USE_PYTHON (
  python %*
  exit /b %ERRORLEVEL%
)
echo Python was not resolved. Install Python 3 and check "Add python.exe to PATH".
exit /b 1
