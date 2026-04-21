# grunt-back Integration Notes

## Local environment prepared

The current workstation has been prepared with:

- JDK 21: `C:\Users\zyc\dev-tools\microsoft-jdk-21\jdk-21.0.7+6`
- Maven 3.9.11: `C:\Users\zyc\dev-tools\apache-maven-3.9.11\apache-maven-3.9.11`

Project helper script:

- `tools/use-grunt-env.ps1`

Run it before local development if the shell is still pointing at an older Java:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\use-grunt-env.ps1
```

## What grunt-back is in the old repository

In `CobePuppy/Grunt`, `grunt-back` is a standalone backend project with these properties:

- Build system: Maven
- Runtime stack: Spring Boot 2.7 WebFlux
- Language target: Java 8
- Persistence: JDBC with H2 default, PostgreSQL optional
- Messaging: RabbitMQ optional
- Static assets: copied from `../grunt-main/src/main/resources/web`

It is not wired into the old repository root Gradle multi-module build.
It exists beside the main project as a separate backend service.

## Current state of grunt-back

The old `grunt-back` is not a full production backend for the real obfuscation engine.
Its structure is meaningful, but some parts are still placeholder-oriented:

- `SessionController` exposes `/api/session/**`
- `PlatformTaskController` and `ObjectStorageController` expose `/api/v1/**`
- `SessionService` manages upload/session workspace state
- `ObjectStorageService` simulates object storage on the local filesystem
- `PlatformTaskService` simulates task orchestration, optionally through RabbitMQ
- `ObfuscationService` is placeholder-style and mainly copies the input JAR to output after staged progress simulation

That means the old `grunt-back` is architecturally useful, but it is not the strongest source of truth for actual obfuscation execution.

## What already exists in the current repository

The current `LiJiachen` branch already absorbed most of the backend architecture into `grunt-main`:

- `grunt-main/.../web/WebServer.kt`
- `grunt-main/.../web/SessionService.kt`
- `grunt-main/.../web/ObjectStorageService.kt`
- `grunt-main/.../web/PlatformTaskService.kt`
- `grunt-main/.../web/ProjectInspectionService.kt`
- `grunt-main/.../web/ObfuscationService.kt`

Unlike the old `grunt-back`, the current branch connects the backend flow to the real obfuscation session and pipeline.

## Current integration status

The current repository now contains a new Gradle module:

- `grunt-back`

It is implemented as:

- Spring Boot 3.x
- WebFlux
- JDBC starter
- RabbitMQ starter
- Java 21

It does not reuse the old Maven module directly.
Instead, it reuses the current `grunt-main/web` service layer and real obfuscation core.

That means the project is now in an intermediate state:

- `grunt-main/web` still exists as the embedded Ktor backend
- `grunt-back` exists as the new Spring transport shell
- both are backed by the same current core services

## Recommendation: how grunt-back should live in this project

Recommended path:

1. Keep old `grunt-back` as an architecture reference, not as drop-in code.
2. Keep the actual obfuscation execution adapter pointed at the current Grunteon core.
3. Move transport-facing concerns into `grunt-back` over time:
   - controller layering
   - policy/config property layout
   - storage abstraction
   - task lifecycle model
   - database / queue integration points
4. Shrink the embedded Ktor backend only after Spring routes, websocket behavior, and session/task workflows are fully covered.

## Practical migration plan

### Option A: Keep current Ktor backend and absorb architecture ideas

This is the lowest-risk path.

- Keep `grunt-main/web` as the real backend
- Introduce config classes mirroring old policy/storage/session settings
- Add persistence and queue adapters behind interfaces
- Preserve current `/api/session/**` and `/api/v1/**` behavior

This avoids duplicate backend stacks and keeps the real obfuscation path intact.

### Option B: Recreate a separate `grunt-back` module on top of the current core

This is the path that is now in progress.

Suggested shape:

- New module: `grunt-back`
- Backend stack: Spring Boot 3.x plus Java 21, not Spring Boot 2.7 plus Java 8
- Depend on current obfuscation core from `grunt-main`
- Move transport concerns only:
  - HTTP controllers
  - persistence
  - queue consumers
  - policy/config beans
- Do not duplicate obfuscation logic

In this shape, `grunt-back` becomes a transport/service shell around the current core rather than an alternate implementation.

## Why direct copy is a bad fit

Directly copying old `grunt-back` into the current repository would still create these problems:

- Maven plus Gradle mixed build maintenance
- Spring WebFlux plus Ktor duplicate backend stacks
- Java 8 plus Java 21 split baseline
- placeholder obfuscation pipeline conflicting with the current real pipeline
- duplicated APIs and duplicated session/task models

## Current local validation status

The local repository has already been adjusted to avoid the earlier remote dependency outage:

- the external `genesis-kotlin` dependency has been replaced by a local `:genesis` module

Current validation status:

- `:genesis:compileKotlin` passes
- `:grunt-main:compileKotlin` passes
- `:grunt-back:bootJar` passes
- `grunt-back.jar` starts successfully on Java 21
- `/login` responds successfully
- `/api/v1/tasks` responds successfully

Current local caveat:

- if RabbitMQ is not running, AMQP health should not be treated as a required startup dependency for development
