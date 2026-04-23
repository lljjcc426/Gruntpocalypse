@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%grunt-main-all.jar"

if exist "%JAR_PATH%" goto jar_found
echo [ERROR] Runtime jar not found:
echo         %JAR_PATH%
echo.
echo Please reinstall Grunteon or restore the missing file.
exit /b 1

:jar_found

if "%~1"=="" (
    echo [ERROR] CLI mode requires a config file path.
    echo.
    echo Usage:
    echo     start-cli.bat config.json
    echo     start-cli.bat path\to\config.json
    exit /b 1
)

echo Starting Grunteon CLI...
echo JAR: %JAR_PATH%
echo.

java --enable-preview -jar "%JAR_PATH%" %*
exit /b %errorlevel%
