@echo off
setlocal
pushd "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0tools\start-server.ps1"
set "ASTAPS_EXIT=%ERRORLEVEL%"
echo.
echo AstaPS exited with code %ASTAPS_EXIT%.
popd
pause
exit /b %ASTAPS_EXIT%
