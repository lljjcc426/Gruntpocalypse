# Grunteon

Grunteon is the current integrated Grunt codebase: a JVM bytecode obfuscation project that preserves the 2.x web workflow while running on the refactored 3.x execution core.

This repository is not a bare framework snapshot. It is a runnable obfuscation workstation with:

- the retained 2.x-style web UI
- the current 3.x typed pipeline core
- a new Spring control plane in `grunt-back`
- Docker bring-up for the platform stack

## Current Positioning

The repository currently serves three purposes at once:

- a local web obfuscation tool
- a CLI obfuscation tool
- a backend/control-plane integration base for the future worker-plane architecture

That means the codebase still carries both:

- compatibility for the legacy 2.x-style frontend workflow
- the newer control-plane direction built around PostgreSQL, MinIO, and recovery-safe backend state

## Repository Layout

- `grunt-back`
  Spring Boot control plane, WebFlux API layer, security, recovery bootstrap, Docker-facing backend shell
- `grunt-main`
  main obfuscation core, transformers, typed config, legacy web/session services, shared backend services
- `grunt-bootstrap`
  bootstrap launcher, plugin/module scanning, client/server entry selection
- `genesis`
  local shared dependency module used by the integrated build
- `grunt-testcase`
  test inputs and verification samples
- `grunt-yapyap`
  extension pack module
- `docker`
  Dockerfiles and infrastructure init scripts
- `docs`
  topology, Docker environment, recovery validation, integration notes
- `tools`
  local environment scripts and smoke validation scripts

## Overall Architecture

The current architecture is split into four layers.

### 1. Execution Core

`grunt-main` contains the real obfuscation engine:

- `ObfConfig`
- `Grunteon`
- transformer pipeline
- ASM-based bytecode processing
- jar dumping and output generation

This is the part that actually obfuscates jars.

### 2. Control Plane

`grunt-back` is the new Spring Boot control plane.

It is responsible for:

- session APIs
- task APIs
- artifact APIs
- policy enforcement
- one-time secure download grants
- restart recovery bootstrap
- infrastructure dependency probing

### 3. State and Artifact Storage

The source of truth is now split by responsibility:

- `PostgreSQL`
  session/task/artifact metadata
- `MinIO`
  artifact file bodies
- `Redis`
  cache / policy cache / future coordination cache
- `Kafka`
  event bus entry point for orchestration and execution concerns

Current durable metadata tables:

- `control_session_state`
- `control_task_state`
- `control_artifact_manifest`
- `control_artifact_ref`

Meaning:

- `control_artifact_manifest`
  represents the file body and object metadata
- `control_artifact_ref`
  represents who uses that artifact and in what role

### 4. Worker Direction

The target topology already includes a `worker` service, but the current code still uses a local execution gateway internally.

So the current status is:

- control plane is real
- externalized state is real
- worker topology is prepared
- fully remote worker execution is the next major phase

## Current Stack

### Core

- Java 21
- Kotlin
- Gradle
- ASM

### Control Plane

- Spring Boot 3.3.5
- Spring WebFlux
- Spring Security
- Spring Data R2DBC
- springdoc OpenAPI

### Platform Dependencies

- PostgreSQL
- Redis
- Kafka
- MinIO
- Docker Compose

### Reserved Direction

- Temporal
- OpenTelemetry

These are already part of the intended architecture direction, but Temporal is not the active workflow engine yet.

## What Was Completed Today

Today’s work was focused on making the control plane safer and more durable rather than adding another new subsystem.

### Control-plane read side was tightened to PostgreSQL-first

Session, task, and artifact reads now prefer PostgreSQL instead of relying on in-memory runtime state.

That means:

- `grunt-back` restart no longer depends on live memory to answer control-plane reads
- control-plane APIs can rebuild their view from persisted metadata

### Artifact ownership/binding was cleaned up

Artifact persistence is now explicitly split into:

- file-body metadata in `control_artifact_manifest`
- ownership/usage bindings in `control_artifact_ref`

This makes future worker and orchestration changes safer because file identity and usage relationships are no longer mixed together.

### Non-terminal task restart recovery rules were implemented

The backend now has an explicit restart policy for persisted tasks:

- `CREATED` stays `CREATED`
- `QUEUED` becomes `INTERRUPTED`
- `STARTING` becomes `INTERRUPTED`
- `RUNNING` becomes `INTERRUPTED`
- `COMPLETED` stays `COMPLETED`
- `FAILED` stays `FAILED`
- `CANCELLED` stays `CANCELLED`

Recovery metadata is recorded so the interruption reason is visible instead of looking like a normal business failure.

### Docker recovery validation was completed

The Docker recovery smoke now verifies:

- platform stack bring-up
- task completion
- metadata in PostgreSQL
- artifact bodies in MinIO
- `grunt-back` restart
- PostgreSQL-first API reads after restart
- one-time secure download grant flow after restart

### Local frontend bootstrap was repaired

The local Spring profile now works correctly for direct development usage:

- H2 uses its own schema script instead of the PostgreSQL-only script
- the frontend schema path now matches the actual static resource file
- `start-back.bat` now forwards Spring arguments correctly through Gradle

That means the browser UI can now be opened locally from `grunt-back` without hitting the schema initialization failure you saw earlier.

## Implemented Areas

The integrated repository currently has the following major obfuscation feature areas available.

### Framework

- typed config system
- web schema adapter for legacy UI config
- resource management
- filter system
- parallel execution pipeline
- web UI/session backend
- service-style task/storage backend routes

### Encrypt

- number encryption
- string encryption
- arithmetic substitution
- constant pool extraction / encryption

### Miscellaneous

- declared fields extractor
- parameter obfuscation
- trash class generator
- hardware ID authentication
- native candidate
- anti-debug
- cloned class

### Optimize

- class shrinking
- dead code removal
- enum optimize
- Kotlin class shrinking
- source debug info hide
- string equals optimize

### Renaming

- class renamer
- field renamer
- method renamer
- local variable renamer
- reflection support
- mixin class rename
- mixin field rename

### Controlflow

- bogus conditional jump
- mangled conditional jump
- reverse existing conditionals
- table-switch based jump rewriting
- trapped switch cases
- switch extractor
- switch protect
- mutated conditional jumps
- arithmetic expression noise
- junk code

### Redirect

- field access proxy
- invoke proxy
- invoke dispatcher
- invoke dynamic

### Other

- decompiler crasher
- fake synthetic bridge
- shuffle members
- watermark
- post-process

## API Surface

The repository currently exposes multiple backend layers.

### Legacy web workflow API

Used by the retained web UI:

- `/api/session/**`
- `/ws/console`
- `/ws/progress`

Capabilities:

- create session
- upload config
- upload input jar
- upload libraries/assets
- start obfuscation
- query status
- inspect input/output tree
- inspect source view
- download artifact

### Service-style backend API

- `/api/v1/tasks/**`
- `/api/v1/storage/**`
- `/api/v1/artifacts/upload-url`

Capabilities:

- task creation/execution
- task logs
- task stages
- upload indirection
- project inspection

### Control-plane API

Added by `grunt-back`:

- `/api/control/sessions/**`
- `/api/control/tasks/**`
- `/api/control/artifacts/**`

Capabilities:

- PostgreSQL-first control-plane reads
- secure policy-aware responses
- recovery-aware task status
- one-time output download grants

## How To Use The Current Obfuscator

There are three practical ways to use the current version.

### A. Use The Spring Web UI

This is the recommended local workflow when you want to use the browser frontend.

#### Start

If port `8080` is free:

```powershell
.\start-back.bat
```

If `8080` is occupied, pass a Spring port argument:

```powershell
.\start-back.bat --server.port=8082
```

Then open:

```text
http://127.0.0.1:8080/login
```

or:

```text
http://127.0.0.1:8082/login
```

#### What to do in the UI

1. Wait until the page finishes initialization.
2. Click `Upload JAR` and select the input jar.
3. Review or edit the generated configuration in the left panel.
4. Optionally upload libraries/assets if your config needs them.
5. Click `Obfuscate`.
6. Watch logs and pipeline progress in the console area.
7. When the task completes, click `Download`.

#### Minimal sanity test

You know the UI path is working if:

- configuration sections load
- project browser shows input classes after upload
- console logs move during obfuscation
- `Download` becomes available after completion

### B. Use The Embedded Web Mode

This is the older embedded web route from `grunt-main`.

#### Build

```powershell
.\gradlew.bat :grunt-main:distJar
```

#### Start

```powershell
.\start-web.bat
```

This starts the web UI from the fat jar built around `grunt-main-all.jar`.

### C. Use The CLI

Use CLI when you want direct config-driven execution without the browser UI.

```powershell
.\start-cli.bat config.json
```

CLI reads the native current config format directly.

## Frontend Source Location

The current web frontend is still a static resource frontend served by the backend.

- entry page: [grunt-main/src/main/resources/web/index.html](/d:/新建文件夹%20(2)/初稿/Grunt/grunt-main/src/main/resources/web/index.html)
- login page: [grunt-main/src/main/resources/web/login.html](/d:/新建文件夹%20(2)/初稿/Grunt/grunt-main/src/main/resources/web/login.html)
- main UI logic: [grunt-main/src/main/resources/web/js/app.js](/d:/新建文件夹%20(2)/初稿/Grunt/grunt-main/src/main/resources/web/js/app.js)
- API binding: [grunt-main/src/main/resources/web/js/api.js](/d:/新建文件夹%20(2)/初稿/Grunt/grunt-main/src/main/resources/web/js/api.js)
- styles: [grunt-main/src/main/resources/web/css/style.css](/d:/新建文件夹%20(2)/初稿/Grunt/grunt-main/src/main/resources/web/css/style.css)
- config schema: [grunt-main/src/main/resources/web/schema/config-schema.json](/d:/新建文件夹%20(2)/初稿/Grunt/grunt-main/src/main/resources/web/schema/config-schema.json)

## Docker Bring-up

The repository includes a platform compose layout for:

- `grunt-back`
- `worker`
- `postgres`
- `redis`
- `kafka`
- `minio`

### Start the platform

```powershell
Copy-Item .env.platform.example .env.platform
docker compose --env-file .env.platform -f compose.platform.yml up -d --build
```

### Recovery validation

Main recovery smoke:

```powershell
.\tools\smoke-control-plane-state-docker.ps1
```

Interrupted-task recovery smoke:

```powershell
.\tools\smoke-control-plane-recovery-interrupted.ps1
```

## Current Recovery Model

The control plane now has explicit restart behavior.

### Sessions

Session metadata is restored from PostgreSQL and object references are resolved back to storage paths as needed.

### Tasks

Task metadata is restored from PostgreSQL first.

Non-terminal tasks are handled with deterministic recovery rules:

- `QUEUED -> INTERRUPTED`
- `STARTING -> INTERRUPTED`
- `RUNNING -> INTERRUPTED`

### Artifacts

Artifacts are not deleted during restart recovery.

Inputs, configs, libraries, assets, and persisted outputs remain available as long as:

- PostgreSQL still has metadata
- MinIO still has the object bodies

## Local Development Notes

### Java

The project currently expects Java 21.

### Cache locations

This repository is configured to avoid reusing `C:` for large Gradle/Maven caches:

- Gradle cache: `D:\dev-cache\gradle`
- Maven local repo: `D:\dev-cache\maven`

### Frontend development note

If the browser still shows stale frontend resources after a backend restart, use:

```text
Ctrl + F5
```

to force-refresh static resources.

### Port conflict

If you see:

```text
Port 8080 was already in use
```

start the backend on another port:

```powershell
.\start-back.bat --server.port=8082
```

## What Is Still Not Final

The project is already usable, but the final architecture is not fully complete yet.

Still pending:

- fully remote worker execution
- Temporal-driven durable orchestration
- complete retirement of the legacy dual-track web layer

Current status:

- control plane is real
- PostgreSQL-first reads are real
- MinIO artifact storage is real
- restart recovery is real
- worker remote execution is the next major phase

## Additional Documents

- [docs/container-topology.md](/d:/新建文件夹%20(2)/初稿/Grunt/docs/container-topology.md)
- [docs/docker-recovery-validation.md](/d:/新建文件夹%20(2)/初稿/Grunt/docs/docker-recovery-validation.md)
- [docs/grunt-back-integration.md](/d:/新建文件夹%20(2)/初稿/Grunt/docs/grunt-back-integration.md)
- [docs/docker-environment.md](/d:/新建文件夹%20(2)/初稿/Grunt/docs/docker-environment.md)

## Development Validation

The recent integration work has been validated with:

- direct `grunt-back` local startup
- browser UI startup through Spring control plane
- Docker recovery smoke
- interrupted-task recovery smoke
- repeated `bootJar` / backend runtime checks during integration

## License

Grunteon is licensed under Apache License 2.0.

Yapyap is licensed under PolyForm Strict License 1.0.0.

Historical generation overview:

| Generation     | Versions    | Aim of obfuscation             | License | Commercial Use |
|----------------|-------------|--------------------------------|---------|----------------|
| Grunt          | 1.0.0-1.5.x | Lightweight and stability      | MIT     | Allowed        |
| Gruntpocalypse | 2.0.0-2.5.x | Diversity and intensity        | LGPL3   | Restricted     |
| Grunteon       | 3.0.0-      | Industrial-grade and efficient | Apache2 | Allowed        |
