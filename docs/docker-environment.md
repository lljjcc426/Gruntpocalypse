# Docker Environment

## Current Machine Readiness

The current Windows host already satisfies the main Docker Desktop prerequisites:

- Windows 11 / build `22631`
- WSL `2.6.3.0`
- Ubuntu `24.04.4 LTS`
- hardware virtualization available

What was missing was the Docker runtime itself:

- `docker` CLI not installed
- `winget` not available in the current shell
- WSL user does not have passwordless `sudo`, so a direct in-distro Docker Engine install is not currently automatable from this session

## Recommended Install Path

Use Docker Desktop with the WSL 2 backend.

This follows the current Docker documentation:

- Windows install guide: https://docs.docker.com/desktop/setup/install/windows-install/
- WSL backend guide: https://docs.docker.com/desktop/features/wsl/

The important installer flags for this project are:

- `--accept-license`
- `--backend=wsl-2`
- `--always-run-service`

`--always-run-service` is useful here because the current shell is not elevated by default.

## Install Script

The repository now includes:

```powershell
.\tools\install-docker-desktop.ps1
```

This script:

1. downloads the official Docker Desktop installer to `tools/downloads/DockerDesktopInstaller.exe`
2. launches the installer with elevation
3. installs Docker Desktop with the WSL 2 backend

Optional flags:

```powershell
.\tools\install-docker-desktop.ps1 -SkipDownload
.\tools\install-docker-desktop.ps1 -StartAfterInstall
```

## Post-Install Verification

After installation:

```powershell
docker version
docker compose version
```

Then run the project smoke:

```powershell
.\tools\smoke-control-plane-state-docker.ps1
```

## Notes

- If Windows asks for UAC approval, accept it.
- If Docker Desktop adds the current user to `docker-users`, a sign-out/sign-in may be required before `docker` works in a fresh shell.
- This project currently targets Linux containers on the WSL 2 backend.
