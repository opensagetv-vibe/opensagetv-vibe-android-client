@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\commission-test-environment.ps1" %*
exit /b %ERRORLEVEL%
