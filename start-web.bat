@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%grunt-main\build\libs\grunt-main-all.jar"

if not exist "%JAR_PATH%" (
    echo [ERROR] Project jar not found:
    echo         %JAR_PATH%
    echo.
    echo Build it first with:
    echo     .\gradlew.bat :grunt-main:distJar
    exit /b 1
)

echo Starting Grunteon Web UI...
echo JAR: %JAR_PATH%
echo.

java --enable-preview -jar "%JAR_PATH%" --web %*
exit /b %errorlevel%
