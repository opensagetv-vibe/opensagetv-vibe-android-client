@echo off
setlocal
python "%~dp0scripts\capture_hdmi_validation.py" %*
exit /b %ERRORLEVEL%
