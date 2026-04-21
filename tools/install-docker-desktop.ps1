param(
    [string]$InstallerPath = "tools/downloads/DockerDesktopInstaller.exe",
    [string]$DownloadUrl = "https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe?utm_campaign=docs-driven-download-windows&utm_medium=webreferral&utm_source=docker",
    [switch]$SkipDownload,
    [switch]$SkipLaunch,
    [switch]$StartAfterInstall,
    [switch]$NoWait
)

$ErrorActionPreference = "Stop"

function Ensure-DownloadDirectory {
    $directory = Split-Path -Parent $InstallerPath
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
}

function Download-Installer {
    Ensure-DownloadDirectory
    Write-Host "Downloading Docker Desktop installer..."
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $InstallerPath
}

function Launch-Installer {
    if (-not (Test-Path $InstallerPath)) {
        throw "Installer not found: $InstallerPath"
    }

    $arguments = @(
        "install",
        "--accept-license",
        "--backend=wsl-2",
        "--always-run-service"
    )

    Write-Host "Launching elevated Docker Desktop installer..."
    Write-Host "If Windows prompts for UAC, accept it to continue."
    $process = Start-Process -FilePath $InstallerPath -Verb RunAs -ArgumentList $arguments -PassThru
    if (-not $NoWait) {
        $process.WaitForExit()
    }
}

function Start-DockerDesktop {
    $dockerDesktopExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerDesktopExe) {
        Start-Process -FilePath $dockerDesktopExe
        return
    }
    Write-Warning "Docker Desktop executable not found at $dockerDesktopExe"
}

function Show-NextSteps {
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "1. If prompted, sign out and sign back in so docker-users membership takes effect."
    Write-Host "2. Start Docker Desktop and wait until the engine reports running."
    Write-Host "3. Verify with: docker version"
    Write-Host "4. Then run: .\tools\smoke-control-plane-state-docker.ps1"
}

if (-not $SkipDownload -and -not (Test-Path $InstallerPath)) {
    Download-Installer
}

if (-not $SkipLaunch) {
    Launch-Installer
}

if ($StartAfterInstall) {
    Start-DockerDesktop
}

Show-NextSteps
