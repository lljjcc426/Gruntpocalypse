@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%grunt-main-all.jar"

if exist "%JAR_PATH%" goto run
echo [ERROR] Runtime jar not found:
echo         %JAR_PATH%
echo.
echo Please reinstall Grunteon or restore the missing file.
exit /b 1

:run

echo Starting Grunteon Web UI...
echo JAR: %JAR_PATH%
echo.

java --enable-preview -jar "%JAR_PATH%" --web %*
exit /b %errorlevel%
