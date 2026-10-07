@echo off
ping -n 4 127.0.0.1 >nul
start http://127.0.0.1:8765
