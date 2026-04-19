# Grunteon

Grunteon is the current integrated Grunt codebase: a JVM bytecode obfuscation project that keeps the 2.x web workflow and feature surface while running on the refactored 3.x core pipeline.

This repository is the practical integration branch rather than a pure upstream 3.x snapshot. Its current focus is:

- preserve the 2.x style web UI and configuration experience
- restore major 2.x obfuscation features into the 3.x architecture
- expose both legacy session APIs and newer service-style task APIs
- keep the project runnable locally as a real obfuscation tool

## Current Positioning

The current repository state is:

- 2.x-style frontend UI retained
- 2.x configuration categories retained
- 3.x typed config and execution pipeline retained
- service-style backend routes added on top of the working web workflow

This means the project is not just a framework demo. It can be used as:

- a local web obfuscation workstation
- a CLI obfuscation tool
- a backend integration base for future control-plane / worker-plane designs

## Repository Layout

- `grunt-main`
  Main obfuscation core, web backend, transformers, config adapter, tests
- `grunt-bootstrap`
  Bootstrap / launcher support
- `grunt-testcase`
  Test inputs and verification samples
- `grunt-yapyap`
  Extension pack module
- `buildSrc`
  Shared Gradle conventions

## Implemented Areas

The items below reflect the current integrated repository state.

### Framework

- Typed config system
- Web schema adapter for legacy UI config
- Resource management
- Filter system
- Parallel execution pipeline
- Web UI/session backend
- Platform-style task/storage backend routes

### Encrypt

- Number encryption
- String encryption
- Arithmetic substitution
- Const pool extraction / encryption

### Miscellaneous

- Declared fields extractor
- Parameter obfuscation
- Trash class generator
- Hardware ID authentication
- Native candidate
- Anti-debug
- Cloned class

### Optimize

- Class shrinking
- Dead code removal
- Enum optimize
- Kotlin class shrinking
- Source debug info hide
- String equals optimize

### Renaming

- Class renamer
- Field renamer
- Method renamer
- Local variable renamer
- Reflection support
- Mixin class rename
- Mixin field rename

### Controlflow

- Bogus conditional jump
- Mangled conditional jump
- Reverse existing conditionals
- Table-switch based jump rewriting
- Trapped switch cases
- Switch extractor
- Switch protect
- Mutated conditional jumps
- Arithmetic expression noise
- Junk code

### Redirect

- Field access proxy
- Invoke proxy
- Invoke dispatcher
- Invoke dynamic

### Other

- Decompiler crasher
- Fake synthetic bridge
- Shuffle members
- Watermark
- Post-process

## Web Backend

The backend currently exposes two layers of API.

### Legacy workflow API

Used by the retained 2.x-style frontend:

- `/api/session/**`

Supported workflow:

- create session
- upload config
- upload input jar
- upload libraries / assets
- start obfuscation
- query status
- stream logs and progress
- inspect input/output tree
- inspect decompiled source
- download artifact

### Service-style backend API

Added as a cleaner backend integration surface:

- `/api/v1/tasks/**`
- `/api/v1/storage/**`
- `/api/v1/artifacts/upload-url`

Supported capabilities:

- task creation and execution
- task logs
- task stages
- SSE event stream
- artifact upload/download indirection
- project meta/tree/source inspection

## Startup

### 1. Build executable jar

```powershell
.\gradlew.bat :grunt-main:jar
```

### 2. Start Web UI

Use the helper script:

```powershell
.\start-web.bat
```

Or specify a port:

```powershell
.\start-web.bat --port=8081
```

Then open:

```text
http://127.0.0.1:8081/login
```

Notes:

- the project currently runs with Java 21 preview enabled
- the helper script already adds `--enable-preview`

### 3. Start CLI

```powershell
.\start-cli.bat config.json
```

## Configuration

There are currently two practical configuration paths:

### Web

The web UI uses the retained 2.x-style config layout and maps it into the current typed backend config through the web adapter.

### CLI

CLI reads the current native `ObfConfig` format directly.

This means:

- web is the most compatible route for old-style 2.x config documents
- CLI is the most direct route for native current-version config

## Current Notes

- this repository intentionally keeps the old UI experience
- not every historical 2.x field has been restored to full depth yet
- major core features are already integrated and usable
- the current backend is Ktor-based, not Spring-based
- the current service-style backend is real and executes the actual obfuscation pipeline

## Development Validation

The integrated repository has been repeatedly validated with:

- `.\gradlew.bat test --no-daemon --rerun-tasks`

as the main regression check during integration work.

## License

Grunteon is licensed under Apache License 2.0.

Yapyap is licensed under PolyForm Strict License 1.0.0.

Historical generation overview:

| Generation     | Versions    | Aim of obfuscation             | License | Commercial Use |
|----------------|-------------|--------------------------------|---------|----------------|
| Grunt          | 1.0.0-1.5.x | Lightweight and stability      | MIT     | Allowed        |
| Gruntpocalypse | 2.0.0-2.5.x | Diversity and intensity        | LGPL3   | Restricted     |
| Grunteon       | 3.0.0-      | Industrial-grade and efficient | Apache2 | Allowed        |

## Stargazers over time

[![Stargazers over time](https://starchart.cc/SpartanB312/Grunt.svg?variant=adaptive)](https://starchart.cc/SpartanB312/Grunt)

![Alt](https://repobeats.axiom.co/api/embed/ea2d273dbb7a9cb21f070102f01e31234afc2627.svg "Repobeats analytics image")
