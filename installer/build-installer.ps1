param(
    [switch]$SkipJarBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-ToolExists {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Name not found: $Path"
    }
}

function Reset-Directory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
    }
    New-Item -ItemType Directory -Path $Path | Out-Null
}

$installerRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $installerRoot
$buildRoot = Join-Path $installerRoot 'build'
$packageDir = Join-Path $buildRoot 'package'
$outputDir = Join-Path $buildRoot 'output'
$runtimeDir = Join-Path $installerRoot 'runtime'
$iconFile = Join-Path $installerRoot 'assets\grunteon.ico'
$launch4jConfig = Join-Path $installerRoot 'Grunteon-web.xml'
$innoScript = Join-Path $installerRoot 'Grunteon_Install.iss'
$launch4jExe = 'D:\Launch4j\launch4jc.exe'
$isccExe = 'D:\Inno Setup 6\ISCC.exe'
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
$jarSource = Join-Path $repoRoot 'grunt-main\build\libs\grunt-main-all.jar'
$webExePath = Join-Path $packageDir 'Grunteon.exe'

Assert-ToolExists -Path $launch4jExe -Name 'Launch4j'
Assert-ToolExists -Path $isccExe -Name 'Inno Setup'
Assert-ToolExists -Path $gradleWrapper -Name 'Gradle wrapper'
Assert-ToolExists -Path $iconFile -Name 'Installer icon'
Assert-ToolExists -Path $launch4jConfig -Name 'Launch4j config'
Assert-ToolExists -Path $innoScript -Name 'Inno Setup script'

Write-Host '==> Preparing installer workspace'
Reset-Directory -Path $packageDir
Reset-Directory -Path $outputDir

if (-not $SkipJarBuild) {
    Write-Host '==> Building standalone jar'
    Push-Location $repoRoot
    try {
        & $gradleWrapper ':grunt-main:distJar'
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

Assert-ToolExists -Path $jarSource -Name 'Standalone jar'

Write-Host '==> Copying package files'
Copy-Item -LiteralPath $jarSource -Destination (Join-Path $packageDir 'grunt-main-all.jar')
Copy-Item -LiteralPath (Join-Path $runtimeDir 'start-web.bat') -Destination (Join-Path $packageDir 'start-web.bat')
Copy-Item -LiteralPath (Join-Path $runtimeDir 'start-cli.bat') -Destination (Join-Path $packageDir 'start-cli.bat')
Copy-Item -LiteralPath (Join-Path $runtimeDir 'README.txt') -Destination (Join-Path $packageDir 'README.txt')

Write-Host '==> Generating Windows launcher with Launch4j'
Push-Location $installerRoot
try {
    & $launch4jExe $launch4jConfig
    if ($LASTEXITCODE -ne 0) {
        throw "Launch4j failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Assert-ToolExists -Path $webExePath -Name 'Generated launcher'

Write-Host '==> Building installer with Inno Setup'
& $isccExe $innoScript
if ($LASTEXITCODE -ne 0) {
    throw "Inno Setup failed with exit code $LASTEXITCODE."
}

$installerExe = Join-Path $outputDir 'Grunteon-Setup-3.0.0.exe'
Assert-ToolExists -Path $installerExe -Name 'Installer output'

Write-Host ''
Write-Host 'Installer build complete:'
Write-Host "  Launcher : $webExePath"
Write-Host "  Installer: $installerExe"
