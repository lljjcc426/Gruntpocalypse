@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

echo Loading Grunt backend environment...
set "ENV_FILE=%TEMP%\grunteon-env-%RANDOM%.cmd"
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%tools\use-grunt-env.ps1" -EmitBatch > "%ENV_FILE%"
if errorlevel 1 (
    if exist "%ENV_FILE%" del "%ENV_FILE%"
    exit /b %errorlevel%
)
call "%ENV_FILE%"
del "%ENV_FILE%" >nul 2>nul

echo.
echo Starting Spring Boot backend...
echo.

set "BOOT_ARGS="
if not "%~1"=="" (
    set "BOOT_ARGS=--args=%*"
)

call "%SCRIPT_DIR%gradlew.bat" :grunt-back:bootRun --no-daemon --stacktrace %BOOT_ARGS%
exit /b %errorlevel%
