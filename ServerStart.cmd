@echo off
setlocal

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0StartServer.ps1"
set "SERVER_EXIT=%ERRORLEVEL%"

echo.
if not "%SERVER_EXIT%"=="0" echo Server exited with code %SERVER_EXIT%.
pause
exit /b %SERVER_EXIT%
