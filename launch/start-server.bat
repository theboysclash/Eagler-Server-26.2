@echo off
setlocal EnableExtensions
cd /d "%~dp0.."

if not exist "server-26.2\paper.jar" (
  echo Server not set up yet. Run:  python launch\setup.py
  exit /b 1
)

for /f "delims=" %%M in ('python -c "import json;print(json.load(open('launch/manifest.json'))['defaults']['minMemory'])"') do set MIN_MEM=%%M
for /f "delims=" %%M in ('python -c "import json;print(json.load(open('launch/manifest.json'))['defaults']['maxMemory'])"') do set MAX_MEM=%%M

cd server-26.2
java -Xms%MIN_MEM% -Xmx%MAX_MEM% -jar paper.jar nogui
if errorlevel 1 pause
