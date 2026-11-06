# Kotlin Quality Compass

Kotlin Quality Compass is a Kotlin/JVM prototype for a CRAP-style complexity-and-coverage report. It is designed around Kotlin Multiplatform source sets while keeping Kotlin PSI, JaCoCo, and Gradle execution in JVM adapters.

The score is:

```text
score = complexity² × (1 - coverage)³ + complexity
```

This is an independent Kotlin implementation inspired by the behavior of `crap4java`; it is not a fork and does not include copied source. The JSON schema is versioned but may evolve between prototype releases.

## Install

Download the versioned ZIP from [GitHub Releases](https://github.com/simtop/kotlin-quality-compass/releases).
Unpack it and run `bin/kotlin-quality-compass --help` with Java 17 or later. On Windows, use the `.bat` launcher.

To build from source, run `./gradlew :cli:test :core:jvmTest :cli:installDist`.
The launcher is in `cli/build/install/kotlin-quality-compass/bin`.

## Run against one file

```bash
kotlin-quality-compass \
  --project /path/to/project \
  --source core-common/src/main/kotlin/example/Logic.kt \
  --coverage-report core-common/build/reports/jacoco/test/jacocoTestReport.xml \
  --threshold 8 --format text
```

`--coverage-task` and `--test-task` are optional Gradle task hooks. The test task runs first, followed by the coverage task. `--scan` reports parsed functions without reading coverage or running Gradle. Use `--reuse-coverage` with an explicit `--coverage-report` for single-file analysis.

## Run against a whole project

```bash
kotlin-quality-compass \
  --project /path/to/project \
  --all \
  --reuse-coverage \
  --coverage-report build/reports/jacoco/jacocoRootReport/jacocoRootReport.xml \
  --format json \
  --output compass-report.json
```

`--all` discovers production Kotlin in `src/main/kotlin`, `src/main/java`, and KMP `*Main` source sets. It excludes generated code, tests, build metadata, nested `.claude` worktrees, and support sources by default. Use `--include-support`, `--include-previews`, `--exclude-path`, `--exclude-module`, or `.quality-compass.properties` to customize scope. `--explain-scope` prints exclusions to stderr.

Without `--reuse-coverage`, whole-project mode runs `:jacocoRootReport` unless an explicit `--coverage-task` is supplied. Projects with a different aggregate task should pass `--coverage-task` explicitly. The resulting report is written to `--output` (relative paths are resolved from `--project`) or stdout when no output path is supplied; diagnostics always remain on stderr.

The report includes `schemaVersion`, summary counts, threshold status, project-relative source paths, coverage notes, and per-function metrics. Exit codes are:

- `0`: analysis completed without a scored function above the threshold;
- `1`: usage, configuration, Gradle, coverage, or analysis failure;
- `2`: analysis completed and at least one scored function exceeded the threshold.

Coverage is derived from JaCoCo source lines spanning each Kotlin function. This is approximate because Kotlin compiler-generated methods, inline functions, default arguments, Compose lowering, and KMP `expect`/`actual` declarations can prevent exact Java-bytecode method attribution. Inline functions are reported as coverage-unavailable because Kotlin inlines their bodies into callers, so JaCoCo cannot reliably attribute coverage to the declaration.

## License

Copyright 2026 Simon Topchyan. Licensed under [Apache 2.0](LICENSE).
