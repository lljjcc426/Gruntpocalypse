$ErrorActionPreference = "Stop"

$toolsRoot = Join-Path $env:USERPROFILE "dev-tools"
$javaHome = Join-Path $toolsRoot "microsoft-jdk-21\jdk-21.0.7+6"
$mavenHome = Join-Path $toolsRoot "apache-maven-3.9.11\apache-maven-3.9.11"
$cacheRoot = "D:\dev-cache"
$gradleUserHome = Join-Path $cacheRoot "gradle"
$mavenRepoLocal = Join-Path $cacheRoot "maven"

if (-not (Test-Path $javaHome)) {
    throw "JDK 21 not found: $javaHome"
}

if (-not (Test-Path $mavenHome)) {
    throw "Maven not found: $mavenHome"
}

$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome
$env:GRADLE_USER_HOME = $gradleUserHome
$mavenOptsParts = @(
    $env:MAVEN_OPTS,
    "-Dmaven.repo.local=$mavenRepoLocal"
) | Where-Object { $_ -and $_.Trim() } | Select-Object -Unique | ForEach-Object { $_.Trim() }
$env:MAVEN_OPTS = $mavenOptsParts -join " "

New-Item -ItemType Directory -Force -Path $gradleUserHome, $mavenRepoLocal | Out-Null

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
