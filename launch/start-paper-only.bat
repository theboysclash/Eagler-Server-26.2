@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "server-26.2\paper.jar" (
  echo Run: python launch\setup.py
  pause
  exit /b 1
)

set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-25*") do set "JAVA=%%D\bin\java.exe"
for /d %%D in ("C:\Program Files\Java\jdk-25*") do set "JAVA=%%D\bin\java.exe"

echo Using: %JAVA%
"%JAVA%" -version 2>nul || (
  echo Install Java 25 from https://adoptium.net/temurin/releases/?version=25
  pause
  exit /b 1
)

cd server-26.2
echo Starting Paper. Leave this window open.
"%JAVA%" -Xms2G -Xmx4G -jar paper.jar nogui
pause
