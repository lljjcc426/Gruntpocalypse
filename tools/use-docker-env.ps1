$ErrorActionPreference = "Stop"

$dockerPaths = @(
    "C:\Program Files\Docker\Docker\resources\bin",
    "C:\Program Files\Docker\cli-plugins"
) | Where-Object { Test-Path $_ }

if ($dockerPaths.Count -eq 0) {
    throw "Docker Desktop paths were not found. Install Docker Desktop first."
}

$currentPathParts = $env:Path -split ";" | Where-Object { $_ }
$filtered = $currentPathParts | Where-Object { $dockerPaths -notcontains $_ }
$env:Path = ($dockerPaths + $filtered) -join ";"

Write-Host "Docker environment loaded."
Write-Host ""
& docker version
