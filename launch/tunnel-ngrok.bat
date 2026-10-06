@echo off
REM Optional: expose the Eagler/WebSocket port through ngrok (requires ngrok + authtoken).
REM Usage: tunnel-ngrok.bat [port]
set PORT=%~1
if "%PORT%"=="" set PORT=25565
echo Forwarding wss via ngrok on port %PORT% ...
echo In Eaglercraft Direct Connect use the ngrok URL as wss://YOUR-SUBDOMAIN.ngrok-free.app
ngrok http %PORT%
