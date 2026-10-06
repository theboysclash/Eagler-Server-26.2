@echo off
REM Skip setup - only open the dashboard (window stays open).
cd /d "%~dp0.."
title KyleTurski MC dashboard
cmd /k "call "%~dp0start-server.bat""
