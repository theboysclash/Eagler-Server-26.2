@echo off
setlocal
set "ROOT=%~dp0.."
set "PLUGIN_DIR=%ROOT%\plugins\hub-economy"
set "JAR=%PLUGIN_DIR%\build\libs\HubEconomy.jar"
set "DEST=%ROOT%\server-26.2\plugins"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%PLUGIN_DIR%"
if not exist "gradlew.bat" (
  echo Gradle wrapper missing in plugins\hub-economy
  exit /b 1
)
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo HubEconomy build failed. Install Java 25 and run launch\doctor.bat
  exit /b 1
)

if exist "%DEST%" (
  copy /Y "%JAR%" "%DEST%\"
  echo Copied HubEconomy.jar to %DEST%
)
