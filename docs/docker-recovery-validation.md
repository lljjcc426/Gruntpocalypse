# Docker Recovery Validation

This runbook is the P0 acceptance path for the current control-plane phase.

The goal is to prove all of the following under the `docker` profile:

- PostgreSQL stores session/task/artifact metadata.
- MinIO stores the artifact file bodies.
- `grunt-back` can restart and preload state back into the live cache.
- Control-plane APIs can still read session/task state after restart.
- one-time output download grants still work after restart.

## Prerequisites

- Docker Desktop or Docker Engine is installed and available as `docker`.
- `grunt-bootstrap/build/libs/grunt-bootstrap.jar` exists locally.
- `.env.platform` exists.

Quick setup:

```powershell
Copy-Item .env.platform.example .env.platform
```

If the input JAR is missing:

```powershell
. .\tools\use-grunt-env.ps1
.\gradlew.bat :grunt-bootstrap:jar --no-daemon
```

## Fast Path

Run the Docker smoke directly:

```powershell
.\tools\smoke-control-plane-state-docker.ps1
```

This script performs:

1. `docker compose config`
2. `docker compose up -d --build`
3. health check and dependency-probe confirmation
4. control-session creation plus config/input/library/asset upload
5. control-task creation, start, and output generation
6. Postgres metadata inspection
7. MinIO object inspection
8. `grunt-back` restart
9. post-restart API checks
10. one-time output download checks before and after restart

Optional switches:

```powershell
.\tools\smoke-control-plane-state-docker.ps1 -RestartWholeStack
.\tools\smoke-control-plane-state-docker.ps1 -ShutdownOnSuccess
```

## Manual Step-By-Step

Validate the compose file:

```powershell
docker compose --env-file .env.platform -f compose.platform.yml config
```

Start the stack:

```powershell
docker compose --env-file .env.platform -f compose.platform.yml up -d --build
docker compose --env-file .env.platform -f compose.platform.yml ps
docker compose --env-file .env.platform -f compose.platform.yml logs -f grunt-back
```

Expected `grunt-back` log markers:

- `Control plane dependency probe: PostgreSQL reachable`
- `Control plane dependency probe: Redis reachable`
- `Control plane dependency probe: Kafka reachable`
- `Control plane dependency probe: MinIO reachable`
- `Control plane state bootstrap restored`

Inspect Postgres metadata:

```powershell
docker compose --env-file .env.platform -f compose.platform.yml exec -T postgres `
  psql -U grunteon -d grunteon -c "select * from control_session_state;"

docker compose --env-file .env.platform -f compose.platform.yml exec -T postgres `
  psql -U grunteon -d grunteon -c "select * from control_task_state;"

docker compose --env-file .env.platform -f compose.platform.yml exec -T postgres `
  psql -U grunteon -d grunteon -c "select * from control_artifact_manifest order by owner_type, owner_id, owner_role;"
```

Inspect MinIO objects:

```powershell
docker compose --env-file .env.platform -f compose.platform.yml run --rm --no-deps `
  -e MINIO_ROOT_USER=grunteon `
  -e MINIO_ROOT_PASSWORD=grunteon123 `
  -e MINIO_BUCKET=grunteon-artifacts `
  --entrypoint /bin/sh minio-init `
  -c 'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc ls -r local/"$MINIO_BUCKET"'
```

Restart only `grunt-back` first:

```powershell
docker compose --env-file .env.platform -f compose.platform.yml restart grunt-back
```

Then rerun the key API checks:

- `GET /actuator/health`
- `GET /api/control/sessions/{sessionId}`
- `GET /api/control/tasks/{taskId}`
- `GET /api/control/tasks/{taskId}/artifacts/output-url`
- `GET /api/control/artifacts/download/{grantId}`

Full-stack restart comes after that:

```powershell
docker compose --env-file .env.platform -f compose.platform.yml down
docker compose --env-file .env.platform -f compose.platform.yml up -d --build
```

Do not add `-v` during this step, or the Postgres and MinIO volumes will be removed.

## Acceptance Checklist

- `docker compose config` succeeds.
- `postgres`, `redis`, `kafka`, `minio`, and `grunt-back` all start.
- `grunt-back /actuator/health` returns `UP`.
- dependency probes succeed for PostgreSQL, Redis, Kafka, and MinIO.
- session/task/artifact rows exist in Postgres.
- expected object keys exist in MinIO.
- restarting only `grunt-back` does not lose state.
- one-time download grants return the output once and reject replay.
- local profile flows remain unaffected.
