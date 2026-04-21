@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

echo Loading Grunt backend environment...
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%tools\use-grunt-env.ps1"
if errorlevel 1 exit /b %errorlevel%

echo.
echo Starting Spring Boot backend...
echo.

set "JAVA_HOME=C:\Users\zyc\dev-tools\microsoft-jdk-21\jdk-21.0.7+6"
set "MAVEN_HOME=C:\Users\zyc\dev-tools\apache-maven-3.9.11\apache-maven-3.9.11"
set "GRADLE_USER_HOME=D:\dev-cache\gradle"
set "MAVEN_OPTS=%MAVEN_OPTS% -Dmaven.repo.local=D:\dev-cache\maven"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

call "%SCRIPT_DIR%gradlew.bat" :grunt-back:bootRun --no-daemon --stacktrace %*
exit /b %errorlevel%
