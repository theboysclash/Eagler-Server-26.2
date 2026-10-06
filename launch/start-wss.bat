@echo off
setlocal
cd /d "%~dp0.."

where caddy >nul 2>&1
if errorlevel 1 (
  echo Caddy is required for wss://KyleTurski.MC
  echo Install it from https://caddyserver.com/docs/install
  exit /b 1
)

echo Eaglercraft join address: wss://KyleTurski.MC
echo DNS for KyleTurski.MC must point at this computer. Ports 80 and 443 must be open.
echo Start the Minecraft server first (launch\start-server.cmd).
caddy run --config "%cd%\launch\Caddyfile"
if errorlevel 1 pause
