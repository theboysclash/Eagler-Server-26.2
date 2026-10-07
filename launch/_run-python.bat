@echo off
REM Run: call _run-python.bat script.py [args...]
if defined PY_LAUNCH goto :pylaunch
if defined USE_PYTHON goto :python
echo Python was not resolved. Install Python 3 and check "Add python.exe to PATH".
exit /b 1

:pylaunch
%PY_LAUNCH% %*
exit /b

:python
python %*
exit /b
