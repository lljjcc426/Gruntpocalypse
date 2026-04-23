Grunteon 3.0.0
================

Requirements
- Java 21 or newer is required.
- Make sure `java` or `JAVA_HOME` is available in the system environment.

Installed files
- Grunteon.exe         Windows launcher, starts Web UI mode by default
- start-web.bat        Web UI launch script
- start-cli.bat        CLI launch script
- grunt-main-all.jar   Main Grunteon runtime jar

Usage
1. Double-click `Grunteon.exe`, or launch Grunteon from the Start Menu.
2. For CLI mode, run this command in a terminal:
   start-cli.bat config.json
3. If Java is not found, install Java 21 and try again.

Notes
- This installer targets standalone local Grunteon usage.
- The installed package does not include the source tree, Gradle build, or backend `bootRun` environment.
