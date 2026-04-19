# Grunteon

Grunteon is the third generation of Grunt. A high concurrency JVM bytecode obfuscator framework written in kotlin.

This project is under development starting from November 2025. The alpha test will commence in April

Feel free to join our Discord server for suggestions: https://discord.gg/ysB2fMfeYW

## Features

Working in progress. The following is a list of features that have been completed or are currently being developed in
the near future

### Framework

* [X] Config system (typed backend + web schema adapter)
* [X] Resource management
* [X] Filter system
* [X] Parallel execution pipeline
* [ ] Module system 
* [ ] Plugin system
* [X] Web UI/session backend
* [ ] UI with I18N (Jetpack Compose)

### Encrypt

* [X] Number encryption
* [X] String encryption
* [X] Arithmetic substitution
* [X] ConstPool extractor

### Miscellaneous

* [X] Declared fields extractor
* [X] Parameter obfuscation
* [X] Trash class generator
* [X] HardwareID authenticator
* [X] Native candidate
* [X] Anti debug

### Optimize

* [X] Class shrinking
* [X] Dead code remove
* [X] Enum optimize
* [X] Kotlin class shrinking
* [X] Source debug info hide
* [X] String equals optimize
* [ ] Method inliner

### Renaming

* [X] Class renamer
* [X] Field renamer
* [X] Method renamer
* [X] Localvar renamer
* [X] Mixin renamer

### Controlflow

* [X] Bogus conditional jump
* [X] Mangled conditional jump
* [X] Reversed conditional jump
* [X] Table switch multi jump
* [X] Trapped switch case
* [X] Switch extractor
* [X] Mutate conditional jump
* [ ] Chaos switch multi jump
* [ ] Controlflow flattening
* [ ] Anti simulation
* [X] Junk code

### Redirect

* [X] Field access proxy
* [X] Invoke proxy
* [X] Invoke dispatcher
* [X] Invoke dynamic

### Other

* [X] Decompiler crasher
* [X] Fake synthetic bridge
* [X] Shuffle members
* [X] Watermarks
* [ ] Reference obfuscation
* [X] Post process

## Web Backend

Current repository state is an integrated 2.x + 3.x web workflow:

* Keeps the 2.x style web UI resources and config layout
* Preserves `/api/session/**` for the legacy session-based workflow
* Adds `/api/v1/storage/**` and `/api/v1/tasks/**` as the service-style backend
* Platform tasks run the real Grunteon obfuscation pipeline instead of a placeholder copy flow
* Task APIs support logs, stages, SSE events, project meta/tree/source inspection and artifact download

## License

Grunteon is a free and open source obfuscator framework licensed under Apache License 2.0

Yapyap is a grunt extension pack licensed under PolyForm Strict License 1.0.0

The license of each Grunt version：

| Generation     | Versions    | Aim of obfuscation             | License | Commercial Use |
|----------------|-------------|--------------------------------|---------|----------------|
| Grunt          | 1.0.0-1.5.x | Lightweight and stability      | MIT     | Allowed        |
| Gruntpocalypse | 2.0.0-2.5.x | Diversity and intensity        | LGPL3   | Restricted     |
| Grunteon       | 3.0.0-      | Industrial-grade and efficient | Apache2 | Allowed        |

## Stargazers over time

[![Stargazers over time](https://starchart.cc/SpartanB312/Grunt.svg?variant=adaptive)](https://starchart.cc/SpartanB312/Grunt)

![Alt](https://repobeats.axiom.co/api/embed/ea2d273dbb7a9cb21f070102f01e31234afc2627.svg "Repobeats analytics image")
