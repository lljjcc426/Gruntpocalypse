@echo off
setlocal

powershell -ExecutionPolicy Bypass -File "%~dp0build-installer.ps1" %*
exit /b %errorlevel%
