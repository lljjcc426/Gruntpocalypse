@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%grunt-main\build\libs\grunt-main.jar"

if not exist "%JAR_PATH%" (
    echo [ERROR] Project jar not found:
    echo         %JAR_PATH%
    echo.
    echo Build it first with:
    echo     .\gradlew.bat :grunt-main:jar
    exit /b 1
)

if "%~1"=="" (
    echo [ERROR] CLI mode requires a config file path.
    echo.
    echo Usage:
    echo     start-cli.bat config.json
    echo     start-cli.bat D:\path\to\config.json
    exit /b 1
)

echo Starting Grunteon CLI...
echo JAR: %JAR_PATH%
echo.

java --enable-preview -jar "%JAR_PATH%" %*
exit /b %errorlevel%
