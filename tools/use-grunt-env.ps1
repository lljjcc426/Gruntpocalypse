param(
    [switch]$EmitBatch
)

$ErrorActionPreference = "Stop"

function Resolve-ExistingPath {
    param(
        [string[]]$Candidates
    )

    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    return $null
}

function Resolve-FirstChildPath {
    param(
        [string]$BasePath,
        [string]$Filter
    )

    if ([string]::IsNullOrWhiteSpace($BasePath) -or -not (Test-Path $BasePath)) {
        return $null
    }

    $match = Get-ChildItem -Path $BasePath -Directory -Filter $Filter -ErrorAction SilentlyContinue |
        Sort-Object Name |
        Select-Object -First 1
    return $match?.FullName
}

$toolsRoot = if ($env:GRUNTEON_TOOLS_ROOT) {
    $env:GRUNTEON_TOOLS_ROOT
} else {
    Join-Path $env:USERPROFILE "dev-tools"
}

$cacheRoot = if ($env:GRUNTEON_CACHE_ROOT) {
    $env:GRUNTEON_CACHE_ROOT
} else {
    Join-Path $env:USERPROFILE ".grunteon\cache"
}

$javaHome = Resolve-ExistingPath @(
    $env:GRUNTEON_JAVA_HOME,
    $env:JAVA_HOME,
    (Resolve-FirstChildPath -BasePath $toolsRoot -Filter "microsoft-jdk-21*"),
    (Resolve-FirstChildPath -BasePath $toolsRoot -Filter "jdk-21*")
)

$mavenHome = Resolve-ExistingPath @(
    $env:GRUNTEON_MAVEN_HOME,
    $env:MAVEN_HOME,
    (Resolve-FirstChildPath -BasePath $toolsRoot -Filter "apache-maven-*")
)

if (-not $javaHome) {
    throw "JDK 21 not found. Set GRUNTEON_JAVA_HOME or JAVA_HOME first."
}

if (-not $mavenHome) {
    throw "Maven not found. Set GRUNTEON_MAVEN_HOME or MAVEN_HOME first."
}

$gradleUserHome = Join-Path $cacheRoot "gradle"
$mavenRepoLocal = Join-Path $cacheRoot "maven"

New-Item -ItemType Directory -Force -Path $gradleUserHome, $mavenRepoLocal | Out-Null

$mavenOptsParts = @(
    $env:MAVEN_OPTS,
    "-Dmaven.repo.local=$mavenRepoLocal"
) | Where-Object { $_ -and $_.Trim() } | Select-Object -Unique | ForEach-Object { $_.Trim() }
$mavenOpts = $mavenOptsParts -join " "

if ($EmitBatch) {
    @(
        "@echo off"
        "set ""JAVA_HOME=$javaHome"""
        "set ""MAVEN_HOME=$mavenHome"""
        "set ""GRADLE_USER_HOME=$gradleUserHome"""
        "set ""MAVEN_OPTS=$mavenOpts"""
        "set ""PATH=$javaHome\bin;$mavenHome\bin;%PATH%"""
    ) | ForEach-Object { Write-Output $_ }
    return
}

$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome
$env:GRADLE_USER_HOME = $gradleUserHome
$env:MAVEN_OPTS = $mavenOpts

$prepend = @(
    (Join-Path $javaHome "bin"),
    (Join-Path $mavenHome "bin")
)

$currentPathParts = $env:Path -split ";" | Where-Object { $_ }
$filtered = $currentPathParts | Where-Object { $prepend -notcontains $_ }
$env:Path = ($prepend + $filtered) -join ";"

Write-Host "Grunt environment loaded."
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "MAVEN_HOME=$env:MAVEN_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "MAVEN_OPTS=$env:MAVEN_OPTS"
Write-Host ""
& java -version
& mvn -version
