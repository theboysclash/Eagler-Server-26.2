@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
echo === KyleTurski MC doctor ===
echo.

where py >nul 2>&1 && (set "PY=py -3") || (set "PY=python")
echo Python: %PY%
%PY% --version 2>nul || echo   MISSING - install Python 3 from python.org

echo.
echo Java:
java -version 2>nul || echo   java not on PATH
if exist "%JAVA_HOME%\bin\java.exe" "%JAVA_HOME%\bin\java.exe" -version 2>nul
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-25*") do (
  echo Found: %%D
  "%%D\bin\java.exe" -version 2>nul
)

echo.
if exist "server-26.2\paper.jar" (echo OK paper.jar) else (echo MISSING server - run: %PY% launch\setup.py)
if exist "server-26.2\plugins\HubEconomy.jar" (echo OK HubEconomy.jar) else (
  echo MISSING HubEconomy.jar - commands /sell /shop will NOT work
  echo   Run: launch\build-plugin.bat
  echo   Then: %PY% launch\setup.py
)
if exist "server-26.2\plugins\EaglerXPaper.jar" (echo OK EaglerXPaper) else if exist "server-26.2\plugins\EaglerXPaper*.jar" (echo OK Eagler plugin) else (echo MISSING EaglerXPaper - run setup.py)

if exist "server-26.2\hub\level.dat" (echo OK custom hub world folder) else (echo Hub world: will use small stone platform OR run launch\import-hub.bat)

echo.
echo Start: KyleTurski-MC.bat  (repo root)
echo Log:   launch\last-run.log
echo Dashboard: http://127.0.0.1:8765
echo If Start does nothing, read errors above then try launch\start-paper-only.bat
echo.
pause
