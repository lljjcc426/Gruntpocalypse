param(
    [string]$EnvFile = ".env.platform",
    [string]$ComposeFile = "compose.platform.yml",
    [switch]$ShutdownOnSuccess
)

$ErrorActionPreference = "Stop"

function Read-EnvFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        throw "Environment file not found: $Path"
    }

    $result = @{}
    foreach ($line in Get-Content -Path $Path) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) { continue }
        $parts = $trimmed -split "=", 2
        if ($parts.Length -eq 2) {
            $result[$parts[0]] = $parts[1]
        }
    }
    return $result
}

function Invoke-Compose {
    param([string[]]$Arguments)

    $all = @("compose", "--env-file", $EnvFile, "-f", $ComposeFile) + $Arguments
    $stdoutPath = Join-Path $env:TEMP ("compose-" + [guid]::NewGuid().ToString("N") + ".out.log")
    $stderrPath = Join-Path $env:TEMP ("compose-" + [guid]::NewGuid().ToString("N") + ".err.log")
    try {
        $process = Start-Process -FilePath "docker" `
            -ArgumentList $all `
            -Wait `
            -NoNewWindow `
            -PassThru `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath
        $stdout = if (Test-Path $stdoutPath) { Get-Content -Path $stdoutPath -Raw } else { "" }
        $stderr = if (Test-Path $stderrPath) { Get-Content -Path $stderrPath -Raw } else { "" }
        $output = @($stdout, $stderr) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    }
    finally {
        foreach ($path in @($stdoutPath, $stderrPath)) {
            if (Test-Path $path) {
                Remove-Item -Path $path -Force
            }
        }
    }
    if ($process.ExitCode -ne 0) {
        throw "docker compose failed:`n$($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine)
}

function Invoke-PostgresQuery {
    param([string]$Query)

    $containerName = "$ComposeProjectName-postgres-1"
    $output = & docker exec `
        -e "PGPASSWORD=$PostgresPassword" `
        $containerName `
        psql `
        -U $PostgresUser `
        -d $PostgresDb `
        -t -A -F "|" `
        -c $Query 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker exec psql failed:`n$($output | Out-String)"
    }
    return (($output | Out-String).Trim())
}

function Wait-HealthyEndpoint {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $health = Invoke-RestMethod -Uri $Url -Method Get
            if ($health.status -eq "UP") {
                return $health
            }
        } catch {
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for healthy endpoint: $Url"
}

function Parse-DelimitedRows {
    param(
        [string]$Text,
        [string[]]$Columns
    )

    $rows = @()
    foreach ($line in ($Text -split "(`r`n|`n|`r)")) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $parts = $line.Trim() -split "\|", $Columns.Length
        $row = [ordered]@{}
        for ($i = 0; $i -lt $Columns.Length; $i++) {
            $row[$Columns[$i]] = if ($i -lt $parts.Length) { $parts[$i] } else { $null }
        }
        $rows += [pscustomobject]$row
    }
    return $rows
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI not found in PATH."
}

$platformEnv = Read-EnvFile -Path $EnvFile
$ComposeProjectName = if ($platformEnv.ContainsKey("COMPOSE_PROJECT_NAME")) { $platformEnv["COMPOSE_PROJECT_NAME"] } else { "grunteon-platform" }
$port = if ($platformEnv.ContainsKey("GRUNT_BACK_HTTP_PORT")) { $platformEnv["GRUNT_BACK_HTTP_PORT"] } else { "8080" }
$BaseUrl = "http://127.0.0.1:$port"
$PostgresDb = if ($platformEnv.ContainsKey("POSTGRES_DB")) { $platformEnv["POSTGRES_DB"] } else { "grunteon" }
$PostgresUser = if ($platformEnv.ContainsKey("POSTGRES_USER")) { $platformEnv["POSTGRES_USER"] } else { "grunteon" }
$PostgresPassword = if ($platformEnv.ContainsKey("POSTGRES_PASSWORD")) { $platformEnv["POSTGRES_PASSWORD"] } else { "grunteon" }

$statusSeeds = @(
    @{ Status = "CREATED"; Expected = "CREATED"; Recoverable = $false },
    @{ Status = "QUEUED"; Expected = "INTERRUPTED"; Recoverable = $true },
    @{ Status = "STARTING"; Expected = "INTERRUPTED"; Recoverable = $true },
    @{ Status = "RUNNING"; Expected = "INTERRUPTED"; Recoverable = $true },
    @{ Status = "COMPLETED"; Expected = "COMPLETED"; Recoverable = $false },
    @{ Status = "FAILED"; Expected = "FAILED"; Recoverable = $false },
    @{ Status = "CANCELLED"; Expected = "CANCELLED"; Recoverable = $false }
)

try {
    Invoke-Compose -Arguments @("config") | Out-Null
    Invoke-Compose -Arguments @("build", "grunt-back") | Out-Null
    Invoke-Compose -Arguments @("up", "-d", "postgres", "redis", "kafka", "minio", "worker", "grunt-back") | Out-Null
    $healthBefore = Wait-HealthyEndpoint -Url "$BaseUrl/actuator/health"

    $createdAt = [DateTimeOffset]::UtcNow.ToString("o")
    $taskRows = @()
    foreach ($seed in $statusSeeds) {
        $taskId = [guid]::NewGuid().ToString()
        $taskRows += [ordered]@{
            taskId = $taskId
            status = $seed.Status
            expected = $seed.Expected
            recoverable = $seed.Recoverable
        }
        $query = @"
INSERT INTO control_task_state (
    task_id, project_name, input_object_key, config_object_key, output_object_key,
    session_id, policy_mode, status, current_stage, progress, message,
    logs_json, stages_json, recovery_previous_status, recovery_reason, recovered_at,
    created_at, updated_at
) VALUES (
    '$taskId',
    'recovery-$($seed.Status.ToLower())',
    'artifacts/input/recovery/$taskId/input.jar',
    NULL,
    NULL,
    NULL,
    'SECURE',
    '$($seed.Status)',
    '$($seed.Status)',
    0,
    'seeded $($seed.Status)',
    '[]',
    '[]',
    NULL,
    NULL,
    NULL,
    '$createdAt',
    '$createdAt'
)
ON CONFLICT (task_id) DO UPDATE SET
    status = EXCLUDED.status,
    current_stage = EXCLUDED.current_stage,
    message = EXCLUDED.message,
    recovery_previous_status = NULL,
    recovery_reason = NULL,
    recovered_at = NULL,
    updated_at = EXCLUDED.updated_at;
"@
        Invoke-PostgresQuery -Query $query | Out-Null
    }

    Invoke-Compose -Arguments @("restart", "grunt-back") | Out-Null
    $healthAfter = Wait-HealthyEndpoint -Url "$BaseUrl/actuator/health"

    $apiTasks = Invoke-RestMethod -Uri "$BaseUrl/api/control/tasks"
    $taskIndex = @{}
    foreach ($task in @($apiTasks.tasks)) {
        $taskIndex[$task.id] = $task
    }

    $results = @()
    foreach ($row in $taskRows) {
        $task = $taskIndex[$row.taskId]
        if ($null -eq $task) {
            throw "Recovered task missing from API: $($row.taskId)"
        }
        $dbRowText = Invoke-PostgresQuery -Query "SELECT task_id,status,recovery_previous_status,recovery_reason,recovered_at FROM control_task_state WHERE task_id = '$($row.taskId)';"
        $dbRow = (Parse-DelimitedRows -Text $dbRowText -Columns @("task_id","status","recovery_previous_status","recovery_reason","recovered_at")) | Select-Object -First 1
        if ($task.status -ne $row.expected) {
            throw "Unexpected task status for $($row.status): api=$($task.status) expected=$($row.expected)"
        }
        if ($dbRow.status -ne $row.expected) {
            throw "Unexpected DB status for $($row.status): db=$($dbRow.status) expected=$($row.expected)"
        }
        if ($row.recoverable) {
            if ($dbRow.recovery_previous_status -ne $row.status) {
                throw "Recovery previous status mismatch for $($row.taskId)"
            }
            if ($dbRow.recovery_reason -ne "CONTROL_PLANE_RESTARTED") {
                throw "Recovery reason missing for $($row.taskId)"
            }
            if ([string]::IsNullOrWhiteSpace($dbRow.recovered_at)) {
                throw "Recovered timestamp missing for $($row.taskId)"
            }
        } else {
            if (-not [string]::IsNullOrWhiteSpace($dbRow.recovery_previous_status) -or -not [string]::IsNullOrWhiteSpace($dbRow.recovery_reason)) {
                throw "Non-recoverable task should not receive recovery metadata: $($row.taskId)"
            }
        }
        $results += [pscustomobject]@{
            TaskId = $row.taskId
            SeedStatus = $row.status
            ApiStatus = $task.status
            DbStatus = $dbRow.status
            RecoveryPreviousStatus = $dbRow.recovery_previous_status
            RecoveryReason = $dbRow.recovery_reason
            RecoveredAt = $dbRow.recovered_at
        }
    }

    [ordered]@{
        ComposeConfigValidated = $true
        HealthBeforeRestart = $healthBefore.status
        HealthAfterRestart = $healthAfter.status
        Results = $results
    } | ConvertTo-Json -Depth 6
}
finally {
    if ($ShutdownOnSuccess) {
        try {
            Invoke-Compose -Arguments @("down") | Out-Null
        } catch {
            Write-Warning $_
        }
    }
}
