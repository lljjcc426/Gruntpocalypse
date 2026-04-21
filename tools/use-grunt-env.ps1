$ErrorActionPreference = "Stop"

$toolsRoot = Join-Path $env:USERPROFILE "dev-tools"
$javaHome = Join-Path $toolsRoot "microsoft-jdk-21\jdk-21.0.7+6"
$mavenHome = Join-Path $toolsRoot "apache-maven-3.9.11\apache-maven-3.9.11"

if (-not (Test-Path $javaHome)) {
    throw "JDK 21 not found: $javaHome"
}

if (-not (Test-Path $mavenHome)) {
    throw "Maven not found: $mavenHome"
}

$env:JAVA_HOME = $javaHome
$env:MAVEN_HOME = $mavenHome

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
Write-Host ""
& java -version
& mvn -version
