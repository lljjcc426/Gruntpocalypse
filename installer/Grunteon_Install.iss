#define AppName "Grunteon"
#define AppVersion "3.0.0"
#define AppPublisher "lljjcc426"
#define PackageDir SourcePath + "\build\package"
#define OutputDir SourcePath + "\build\output"

[Setup]
AppId={{B85A4F31-F751-4AB7-967C-5EABF5D411B4}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
PrivilegesRequired=admin
UsePreviousAppDir=no
DisableDirPage=no
OutputDir={#OutputDir}
OutputBaseFilename=Grunteon-Setup-{#AppVersion}
Compression=lzma
SolidCompression=yes
WizardStyle=modern
SetupIconFile={#SourcePath}\assets\grunteon.ico
UninstallDisplayIcon={app}\Grunteon.exe
SetupLogging=yes

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional tasks:"; Flags: unchecked

[Files]
Source: "{#PackageDir}\Grunteon.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\grunt-main-all.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\start-web.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\start-cli.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#PackageDir}\README.txt"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Grunteon"; Filename: "{app}\Grunteon.exe"
Name: "{group}\Grunteon CLI"; Filename: "{app}\start-cli.bat"
Name: "{group}\README"; Filename: "{app}\README.txt"
Name: "{group}\Uninstall Grunteon"; Filename: "{uninstallexe}"
Name: "{commondesktop}\Grunteon"; Filename: "{app}\Grunteon.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Grunteon.exe"; Description: "Launch Grunteon Web UI now"; Flags: nowait postinstall skipifsilent
