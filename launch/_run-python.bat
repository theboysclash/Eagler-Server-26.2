@echo off
REM Run: call _run-python.bat path\to\script.py [args...]
if defined USE_PY_LAUNCHER (
  py -3 %*
  exit /b %ERRORLEVEL%
)
if defined USE_PYTHON (
  python %*
  exit /b %ERRORLEVEL%
)
echo Internal error: Python was not resolved. Run KyleTurski-MC.bat again.
exit /b 1
