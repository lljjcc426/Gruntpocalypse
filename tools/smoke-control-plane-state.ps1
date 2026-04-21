param(
    [int]$Port = 18105
)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    $jdk = "C:\Users\zyc\dev-tools\microsoft-jdk-21\jdk-21.0.7+6"
    if (Test-Path $jdk) {
        $env:JAVA_HOME = $jdk
        $env:PATH = "$jdk\bin;$env:PATH"
    }
}

$stdoutLog = "grunt-back-smoke-state.out.log"
$stderrLog = "grunt-back-smoke-state.err.log"
foreach ($path in @($stdoutLog, $stderrLog)) {
    if (Test-Path $path) {
        Remove-Item $path -Force
    }
}

$process = Start-Process -FilePath "java" `
    -ArgumentList "--enable-preview", "-jar", "grunt-back/build/libs/grunt-back.jar", "--server.port=$Port" `
    -WorkingDirectory "." `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

Start-Sleep -Seconds 14

try {
    $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health"
    $session = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/control/sessions" -ContentType "application/json" -Body "{}"

    $ticket = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/v1/artifacts/upload-url" `
        -ContentType "application/json" `
        -Body '{"fileName":"input.jar","kind":"input"}'

    Invoke-RestMethod -Method Put -Uri ("http://127.0.0.1:$Port" + $ticket.uploadUrl) `
        -ContentType "application/octet-stream" `
        -InFile "grunt-bootstrap/build/libs/grunt-bootstrap.jar" | Out-Null

    $taskBody = @{
        projectName = "state-smoke-task"
        inputObjectKey = $ticket.objectKey
        policyProfile = "RESEARCH"
    } | ConvertTo-Json

    $task = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$Port/api/control/tasks" -ContentType "application/json" -Body $taskBody
    $tasks = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/control/tasks"

    [ordered]@{
        Health = $health.status
        SessionId = $session.sessionId
        TaskId = $task.task.id
        TaskStatus = $task.task.status
        TaskListCount = $tasks.tasks.Count
        FirstTaskStatus = if ($tasks.tasks.Count -gt 0) { $tasks.tasks[0].status } else { $null }
    } | ConvertTo-Json -Depth 4
}
finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
    }
}
