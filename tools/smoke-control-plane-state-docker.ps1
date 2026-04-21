param(
    [string]$EnvFile = ".env.platform",
    [string]$ComposeFile = "compose.platform.yml",
    [string]$InputJar = "grunt-bootstrap/build/libs/grunt-bootstrap.jar",
    [int]$TaskTimeoutSeconds = 240,
    [switch]$RestartWholeStack,
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
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) {
            continue
        }
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
    $stdoutPath = Join-Path $tmpDir ("compose-" + [guid]::NewGuid().ToString("N") + ".out.log")
    $stderrPath = Join-Path $tmpDir ("compose-" + [guid]::NewGuid().ToString("N") + ".err.log")
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

function Resolve-Url {
    param([string]$Url)

    if ($Url -match "^https?://") {
        return $Url
    }
    if ($Url.StartsWith("/")) {
        return "$BaseUrl$Url"
    }
    return "$BaseUrl/$Url"
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

function Get-HttpStatusCode {
    param(
        [string]$Url,
        [string]$OutFile
    )

    $curlArgs = @("-sS", "-o")
    if ($OutFile) {
        $curlArgs += $OutFile
    } else {
        $curlArgs += "NUL"
    }
    $curlArgs += @("-w", "%{http_code}", $Url)
    $status = & curl.exe @curlArgs
    if ($LASTEXITCODE -ne 0) {
        throw "curl request failed for $Url"
    }
    return [int]$status
}

function Get-DownloadGrantUrl {
    param(
        [string]$TaskId,
        [int]$RetryCount = 5
    )

    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        try {
            $grant = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/tasks/$TaskId/artifacts/output-url"
            $downloadUrl = [string]$grant.downloadUrl
            if (-not [string]::IsNullOrWhiteSpace($downloadUrl)) {
                return Resolve-Url -Url $downloadUrl
            }
        } catch {
            if ($attempt -eq $RetryCount) {
                throw
            }
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for download grant url for task: $TaskId"
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

function Invoke-MinioList {
    $script = @'
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mc ls -r local/"$MINIO_BUCKET"
'@
    $output = & docker run --rm `
        --network $ComposeProjectName `
        -e "MINIO_ROOT_USER=$MinioUser" `
        -e "MINIO_ROOT_PASSWORD=$MinioPassword" `
        -e "MINIO_BUCKET=$MinioBucket" `
        --entrypoint /bin/sh `
        minio/mc:RELEASE.2025-02-15T10-36-16Z `
        -c $script 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker run minio/mc failed:`n$($output | Out-String)"
    }
    return (($output | Out-String).Trim())
}

function Parse-DelimitedRow {
    param(
        [string]$Text,
        [string[]]$Columns
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }
    $parts = $Text.Trim() -split "\|", $Columns.Length
    $row = [ordered]@{}
    for ($i = 0; $i -lt $Columns.Length; $i++) {
        $row[$Columns[$i]] = if ($i -lt $parts.Length) { $parts[$i] } else { $null }
    }
    return [pscustomobject]$row
}

function Parse-DelimitedRows {
    param(
        [string]$Text,
        [string[]]$Columns
    )

    $rows = @()
    foreach ($line in ($Text -split "(`r`n|`n|`r)")) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $rows += Parse-DelimitedRow -Text $line -Columns $Columns
    }
    return $rows
}

function Wait-TaskTerminalState {
    param(
        [string]$TaskId,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $task = Invoke-RestMethod -Uri "$BaseUrl/api/control/tasks/$TaskId" -Method Get
        $status = [string]$task.task.status
        if ($status -in @("COMPLETED", "FAILED")) {
            return $task
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for task terminal state: $TaskId"
}

function Wait-PostgresRow {
    param(
        [string]$Query,
        [string[]]$Columns,
        [string[]]$RequiredFields = @(),
        [int]$TimeoutSeconds = 30,
        [string]$Description = "Postgres row"
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $raw = Invoke-PostgresQuery -Query $Query
        $row = Parse-DelimitedRow -Text $raw -Columns $Columns
        if ($null -ne $row) {
            $missingRequired = $false
            foreach ($field in $RequiredFields) {
                $value = $row.$field
                if ([string]::IsNullOrWhiteSpace([string]$value)) {
                    $missingRequired = $true
                    break
                }
            }
            if (-not $missingRequired) {
                return $row
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for $Description"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI not found in PATH. Run this script on a Docker-enabled machine."
}

if (-not (Test-Path $InputJar)) {
    throw "Input jar not found: $InputJar"
}

$platformEnv = Read-EnvFile -Path $EnvFile
$ComposeProjectName = if ($platformEnv.ContainsKey("COMPOSE_PROJECT_NAME")) { $platformEnv["COMPOSE_PROJECT_NAME"] } else { "grunteon-platform" }
$port = if ($platformEnv.ContainsKey("GRUNT_BACK_HTTP_PORT")) { $platformEnv["GRUNT_BACK_HTTP_PORT"] } else { "8080" }
$BaseUrl = "http://127.0.0.1:$port"
$PostgresDb = if ($platformEnv.ContainsKey("POSTGRES_DB")) { $platformEnv["POSTGRES_DB"] } else { "grunteon" }
$PostgresUser = if ($platformEnv.ContainsKey("POSTGRES_USER")) { $platformEnv["POSTGRES_USER"] } else { "grunteon" }
$PostgresPassword = if ($platformEnv.ContainsKey("POSTGRES_PASSWORD")) { $platformEnv["POSTGRES_PASSWORD"] } else { "grunteon" }
$MinioUser = if ($platformEnv.ContainsKey("MINIO_ROOT_USER")) { $platformEnv["MINIO_ROOT_USER"] } else { "grunteon" }
$MinioPassword = if ($platformEnv.ContainsKey("MINIO_ROOT_PASSWORD")) { $platformEnv["MINIO_ROOT_PASSWORD"] } else { "grunteon123" }
$MinioBucket = if ($platformEnv.ContainsKey("MINIO_BUCKET")) { $platformEnv["MINIO_BUCKET"] } else { "grunteon-artifacts" }

$tmpDir = Join-Path ".tmp-smoke" "docker-recovery"
if (Test-Path $tmpDir) {
    Remove-Item -Path $tmpDir -Recurse -Force
}
New-Item -ItemType Directory -Path $tmpDir | Out-Null

$configPath = Join-Path $tmpDir "config.json"
$assetPath = Join-Path $tmpDir "asset.txt"
$downloadBefore = Join-Path $tmpDir "output-before-restart.jar"
$downloadAfter = Join-Path $tmpDir "output-after-restart.jar"
Set-Content -Path $configPath -Value "{}" -Encoding UTF8
Set-Content -Path $assetPath -Value "control-plane-asset" -Encoding UTF8

$sessionId = $null
$taskId = $null

try {
    Invoke-Compose -Arguments @("config") | Out-Null
    Invoke-Compose -Arguments @("build", "--no-cache", "grunt-back", "worker") | Out-Null
    Invoke-Compose -Arguments @("up", "-d") | Out-Null

    $healthBefore = Wait-HealthyEndpoint -Url "$BaseUrl/actuator/health"
    $startupLogs = Invoke-Compose -Arguments @("logs", "--no-color", "grunt-back")
    $probeSummary = [ordered]@{
        Postgres = $startupLogs -match "PostgreSQL reachable"
        Redis = $startupLogs -match "Redis reachable"
        Kafka = $startupLogs -match "Kafka reachable"
        MinIO = $startupLogs -match "MinIO reachable"
        Bootstrap = $startupLogs -match "Control plane state bootstrap restored"
    }

    $sessionResponse = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/control/sessions" -ContentType "application/json" -Body "{}"
    $sessionId = [string]$sessionResponse.sessionId

    $configUploadJson = & curl.exe -sS -F "file=@$configPath;type=application/json" "$BaseUrl/api/control/sessions/$sessionId/artifacts/config"
    if ($LASTEXITCODE -ne 0) {
        throw "Config upload failed"
    }
    $configUpload = $configUploadJson | ConvertFrom-Json

    $inputUploadJson = & curl.exe -sS -F "file=@$InputJar;type=application/java-archive" "$BaseUrl/api/control/sessions/$sessionId/artifacts/input"
    if ($LASTEXITCODE -ne 0) {
        throw "Input upload failed"
    }
    $inputUpload = $inputUploadJson | ConvertFrom-Json

    $libraryUploadJson = & curl.exe -sS -F "files=@$InputJar;type=application/java-archive;filename=helper-lib.jar" "$BaseUrl/api/control/sessions/$sessionId/artifacts/libraries"
    if ($LASTEXITCODE -ne 0) {
        throw "Library upload failed"
    }
    $libraryUpload = $libraryUploadJson | ConvertFrom-Json

    $assetUploadJson = & curl.exe -sS -F "files=@$assetPath;type=text/plain" "$BaseUrl/api/control/sessions/$sessionId/artifacts/assets"
    if ($LASTEXITCODE -ne 0) {
        throw "Asset upload failed"
    }
    $assetUpload = $assetUploadJson | ConvertFrom-Json

    $ticket = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/control/artifacts/upload-url" -ContentType "application/json" -Body '{"fileName":"input.jar","kind":"input"}'
    Invoke-WebRequest -Method Put -Uri (Resolve-Url -Url $ticket.uploadUrl) -ContentType "application/octet-stream" -InFile $InputJar | Out-Null

    $taskRequest = @{
        projectName = "docker-recovery-task"
        inputObjectKey = $ticket.objectKey
    } | ConvertTo-Json
    $taskCreate = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/control/tasks" -ContentType "application/json" -Body $taskRequest
    $taskId = [string]$taskCreate.task.id

    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/control/tasks/$taskId/actions/start" | Out-Null
    $taskAfterStart = Wait-TaskTerminalState -TaskId $taskId -TimeoutSeconds $TaskTimeoutSeconds
    if ($taskAfterStart.task.status -ne "COMPLETED") {
        throw "Task did not complete successfully. Status=$($taskAfterStart.task.status)"
    }
    $taskListBeforeRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/tasks"
    $sessionListBeforeRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/sessions"
    $sessionStatusBeforeRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/sessions/$sessionId"
    $taskStatusBeforeRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/tasks/$taskId"
    $sessionArtifactsBeforeRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/sessions/$sessionId/artifacts"
    $taskArtifactsBeforeRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/tasks/$taskId/artifacts"
    if (@($sessionArtifactsBeforeRestart.artifacts).Count -lt 4) {
        throw "Session artifact read model did not return expected entries before restart"
    }
    if (@($taskArtifactsBeforeRestart.artifacts).Count -lt 2) {
        throw "Task artifact read model did not return expected entries before restart"
    }

    $taskRow = Wait-PostgresRow `
        -Description "task metadata for $taskId" `
        -TimeoutSeconds 45 `
        -Query "SELECT task_id,status,session_id,input_object_key,config_object_key,output_object_key FROM control_task_state WHERE task_id = '$taskId';" `
        -Columns @("task_id", "status", "session_id", "input_object_key", "config_object_key", "output_object_key") `
        -RequiredFields @("session_id", "output_object_key")
    if (-not $taskRow) {
        throw "Task metadata not found in Postgres for taskId=$taskId"
    }
    $taskSessionId = [string]$taskRow.session_id
    if ([string]::IsNullOrWhiteSpace($taskSessionId)) {
        throw "Task session id not found in Postgres for taskId=$taskId"
    }

    $sessionRow = Wait-PostgresRow `
        -Description "control session metadata for $sessionId" `
        -TimeoutSeconds 30 `
        -Query "SELECT session_id,status,input_object_key,config_object_key,output_object_key FROM control_session_state WHERE session_id = '$sessionId';" `
        -Columns @("session_id", "status", "input_object_key", "config_object_key", "output_object_key") `
        -RequiredFields @("input_object_key")
    if (-not $sessionRow) {
        throw "Session metadata not found in Postgres for sessionId=$sessionId"
    }

    $taskSessionRow = Wait-PostgresRow `
        -Description "task session metadata for $taskSessionId" `
        -TimeoutSeconds 45 `
        -Query "SELECT session_id,status,input_object_key,config_object_key,output_object_key FROM control_session_state WHERE session_id = '$taskSessionId';" `
        -Columns @("session_id", "status", "input_object_key", "config_object_key", "output_object_key") `
        -RequiredFields @("input_object_key", "output_object_key")
    if (-not $taskSessionRow) {
        throw "Task session metadata not found in Postgres for sessionId=$taskSessionId"
    }

    $artifactRowsText = Invoke-PostgresQuery -Query @"
SELECT r.owner_type,r.owner_id,r.owner_role,m.object_key,m.bucket_name,m.storage_backend
FROM control_artifact_ref r
JOIN control_artifact_manifest m ON m.object_key = r.object_key
WHERE r.owner_id IN ('$sessionId', '$taskId', '$taskSessionId')
ORDER BY r.owner_type, r.owner_id, r.owner_role, m.object_key;
"@
    $artifactRows = Parse-DelimitedRows -Text $artifactRowsText -Columns @("owner_type", "owner_id", "owner_role", "object_key", "bucket_name", "storage_backend")

    $minioListing = Invoke-MinioList
    foreach ($expectedObjectKey in @(
        [string]$ticket.objectKey,
        [string]$taskRow.output_object_key,
        [string]$sessionRow.input_object_key,
        [string]$sessionRow.config_object_key,
        [string]$taskSessionRow.input_object_key,
        [string]$taskSessionRow.output_object_key
    )) {
        if (-not [string]::IsNullOrWhiteSpace($expectedObjectKey) -and $minioListing -notmatch [Regex]::Escape($expectedObjectKey)) {
            throw "Expected object key missing from MinIO listing: $expectedObjectKey"
        }
    }

    $directDownloadStatusBefore = Get-HttpStatusCode -Url "$BaseUrl/api/control/tasks/$taskId/artifacts/output"

    $downloadUrlBefore = Get-DownloadGrantUrl -TaskId $taskId
    $downloadStatusBefore = Get-HttpStatusCode -Url $downloadUrlBefore -OutFile $downloadBefore
    $downloadReplayStatusBefore = Get-HttpStatusCode -Url $downloadUrlBefore

    if ($RestartWholeStack) {
        Invoke-Compose -Arguments @("down")
        Invoke-Compose -Arguments @("build", "--no-cache", "grunt-back", "worker") | Out-Null
        Invoke-Compose -Arguments @("up", "-d") | Out-Null
    } else {
        Invoke-Compose -Arguments @("restart", "grunt-back") | Out-Null
    }

    $healthAfter = Wait-HealthyEndpoint -Url "$BaseUrl/actuator/health"
    $restartLogs = Invoke-Compose -Arguments @("logs", "--no-color", "grunt-back")
    $bootstrapSeenAfterRestart = $restartLogs -match "Control plane state bootstrap restored"

    $sessionListAfterRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/sessions"
    $taskAfterRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/tasks/$taskId"
    $taskListAfterRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/tasks"
    $sessionAfterRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/sessions/$sessionId"
    $sessionTaskAfterRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/sessions/$taskSessionId"
    $sessionArtifactsAfterRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/sessions/$sessionId/artifacts"
    $taskArtifactsAfterRestart = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/control/tasks/$taskId/artifacts"
    if (@($sessionArtifactsAfterRestart.artifacts).Count -lt 4) {
        throw "Session artifact read model did not return expected entries after restart"
    }
    if (@($taskArtifactsAfterRestart.artifacts).Count -lt 2) {
        throw "Task artifact read model did not return expected entries after restart"
    }

    $downloadUrlAfter = Get-DownloadGrantUrl -TaskId $taskId
    $downloadStatusAfter = Get-HttpStatusCode -Url $downloadUrlAfter -OutFile $downloadAfter
    $downloadReplayStatusAfter = Get-HttpStatusCode -Url $downloadUrlAfter

    [ordered]@{
        ComposeConfigValidated = $true
        HealthBeforeRestart = $healthBefore.status
        HealthAfterRestart = $healthAfter.status
        DependencyProbe = $probeSummary
        BootstrapSeenAfterRestart = $bootstrapSeenAfterRestart
        SessionId = $sessionId
        TaskId = $taskId
        TaskSessionId = $taskSessionId
        SessionUpload = [ordered]@{
            ConfigAccepted = $configUpload.accepted
            InputClassCount = $inputUpload.classCount
            LibraryCount = $libraryUpload.count
            AssetCount = $assetUpload.count
        }
        TaskState = [ordered]@{
            BeforeRestart = $taskAfterStart.task.status
            AfterRestart = $taskAfterRestart.task.status
        }
        SessionState = [ordered]@{
            ControlSessionBeforeRestart = $sessionStatusBeforeRestart.status
            ControlSessionAfterRestart = $sessionAfterRestart.status
            TaskSessionAfterRestart = $sessionTaskAfterRestart.status
        }
        ApiReadModel = [ordered]@{
            SessionListBeforeRestartCount = @($sessionListBeforeRestart.sessions).Count
            TaskListBeforeRestartCount = @($taskListBeforeRestart.tasks).Count
            SessionArtifactsBeforeRestartCount = @($sessionArtifactsBeforeRestart.artifacts).Count
            TaskArtifactsBeforeRestartCount = @($taskArtifactsBeforeRestart.artifacts).Count
            SessionListAfterRestartCount = @($sessionListAfterRestart.sessions).Count
            TaskListAfterRestartCount = @($taskListAfterRestart.tasks).Count
            SessionArtifactsAfterRestartCount = @($sessionArtifactsAfterRestart.artifacts).Count
            TaskArtifactsAfterRestartCount = @($taskArtifactsAfterRestart.artifacts).Count
        }
        Postgres = [ordered]@{
            SessionRow = $sessionRow
            TaskSessionRow = $taskSessionRow
            TaskRow = $taskRow
            ArtifactRows = $artifactRows
        }
        MinIO = [ordered]@{
            Bucket = $MinioBucket
            ContainsSessionInput = $minioListing -match [Regex]::Escape([string]$sessionRow.input_object_key)
            ContainsSessionConfig = $minioListing -match [Regex]::Escape([string]$sessionRow.config_object_key)
            ContainsTaskInput = $minioListing -match [Regex]::Escape([string]$ticket.objectKey)
            ContainsTaskOutput = $minioListing -match [Regex]::Escape([string]$taskRow.output_object_key)
        }
        Download = [ordered]@{
            DirectSecureDownloadStatus = $directDownloadStatusBefore
            BeforeRestartStatus = $downloadStatusBefore
            BeforeRestartReplayStatus = $downloadReplayStatusBefore
            BeforeRestartBytes = if (Test-Path $downloadBefore) { (Get-Item $downloadBefore).Length } else { 0 }
            AfterRestartStatus = $downloadStatusAfter
            AfterRestartReplayStatus = $downloadReplayStatusAfter
            AfterRestartBytes = if (Test-Path $downloadAfter) { (Get-Item $downloadAfter).Length } else { 0 }
        }
    } | ConvertTo-Json -Depth 8
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
