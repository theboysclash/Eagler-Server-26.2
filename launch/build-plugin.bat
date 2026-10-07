@echo off
setlocal
set "ROOT=%~dp0.."
set "PLUGIN_DIR=%ROOT%\plugins\hub-economy"
set "JAR=%PLUGIN_DIR%\build\libs\HubEconomy.jar"
set "DEST=%ROOT%\server-26.2\plugins"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%PLUGIN_DIR%"
if not exist "gradlew.bat" goto :nogradle
call gradlew.bat build
if errorlevel 1 goto :buildfail
if not exist "%JAR%" goto :nojar
if not exist "%DEST%" goto :nodest
copy /Y "%JAR%" "%DEST%\"
if errorlevel 1 goto :copyfail
echo Copied HubEconomy.jar into server-26.2\plugins
exit /b 0

:nogradle
echo Gradle wrapper missing in plugins\hub-economy
exit /b 1

:buildfail
echo.
echo HubEconomy build failed. Install Java 25 and run launch\doctor.bat
exit /b 1

:nojar
echo Build finished but HubEconomy.jar was not found.
exit /b 1

:nodest
echo Plugin folder is missing. Run launch\setup.bat first.
exit /b 1

:copyfail
echo Could not copy HubEconomy.jar into server-26.2\plugins
exit /b 1
