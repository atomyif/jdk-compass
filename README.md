# JDK Compass

[中文说明](README.zh-CN.md)

A small, dependency-free command-line tool that explains why a Java project's version requirement, the running JVM, and `JAVA_HOME` disagree.

JDK Compass is useful when a project builds on one machine but fails on another, or when an IDE and a terminal appear to use different JDKs. It inspects configuration; it does not switch your JDK or run the project's build scripts.

**Status:** early MVP, version 0.1.0. Build from source using the instructions below; packaged GitHub releases are not available yet. The initial implementation was developed with AI assistance.

## Quick start

Install a full JDK 17 or newer. From this repository's root:

```sh
java Build.java
java -jar build/jdk-compass.jar --project examples/java17 --expect 17
```

The build compiles with `--release 17`, runs the test suite, and creates a runnable JAR. It downloads no build dependencies. The same commands work in a terminal or PowerShell.

For your own project:

```sh
java -jar build/jdk-compass.jar --project /path/to/project
java -jar build/jdk-compass.jar --project /path/to/project --expect 21 --json
java -jar build/jdk-compass.jar --project /path/to/project --expect 21 --strict
```

In your editor, open this directory, select a JDK 17+ SDK, and use a terminal to run `java Build.java`. This project intentionally does not require Maven or Gradle for its first release.

## Checks

| Input | Behavior |
| --- | --- |
| `.java-version` | One concrete version such as `17`, `21.0.5`, or `1.8.0_452` |
| `.sdkmanrc` | One `java=21.0.5-tem` declaration; other tools are ignored |
| `.tool-versions` | One `java temurin-21.0.5+11` declaration; other tools are ignored |
| Conflicting version files | An error, rather than silently choosing a file |
| `--expect MAJOR` | Explicit override of all project version files |
| Running JVM | Its Java major must equal the selected project requirement |
| `JAVA_HOME` / `--java-home` | Checks an absolute JDK path, Java/compiler executables, and `release` metadata |
| Different runtime/JDK home | Warning, even when both have the same major version |
| JVM option environment variables | Reports presence only, not their contents |

All three version files are read from the supplied project root only. Version comparison is exact at the **major version** level, not a claim about source compatibility, patch level, or vendor compatibility. Aliases such as `latest`, `lts`, and `system`, and multiple fallback versions, are not interpreted. A single optional alphanumeric vendor prefix followed by `-` is accepted; this is not a complete SDKMAN/asdf version grammar.

## Reports and exit codes

Use `--json` for a machine-readable report with `schemaVersion`, `toolVersion`, `exitCode`, and `findings`. Each finding contains `code`, `severity`, `message`, and `suggestion`.

| Exit code | Meaning |
| --- | --- |
| `0` | No errors; warnings may still exist |
| `1` | At least one error, or at least one warning when `--strict` is set |
| `2` | Invalid command-line arguments; error goes to stderr, with no JSON output |

`--help` and `--version` must be used on their own. Scripts should key on finding codes, not English message text. See [report format](docs/report-format.md).

## Privacy and boundaries

The diagnostic code makes no network calls and does not execute project files, JDK executables found at the inspected path, or shell startup scripts. It reads only the root version files and selected JDK's `release` file, with a 64 KiB limit per file; ordinary filesystem symbolic links are followed. Reports omit absolute paths and environment-variable values.

This is not a sandbox for the Java launcher: `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, and `_JAVA_OPTIONS` may already affect the JVM or be printed by Java **before** this program starts. Review raw terminal output before sharing it. The build command executes this repository's own build and test code.

## Current limitations

- Requires JDK 17+ to build and Java 17+ to run. It can inspect an older JDK's metadata but cannot itself run on Java 8/11.
- Does not read Maven profiles, `pom.xml`, Gradle toolchains, or editor-specific project settings yet.
- Examines the JVM that launched the tool, not every Java executable on `PATH` or an IDE's selected SDK.
- Does not validate that an inspected executable's contents match its `release` metadata.
- Local validation covers macOS; the included GitHub Actions matrix must pass on actual hosted runners before claiming Windows/Linux support is verified.

## Development

Run `java Build.java` after code changes. Tests use isolated temporary fixtures, including missing compilers, conflicting versions, invalid files, JSON escaping, and secret-value suppression. See [CONTRIBUTING.md](CONTRIBUTING.md) and the [build and release instructions](docs/publishing.md).

The [development and maintenance roadmap (Chinese)](docs/roadmap.md) outlines the first-year milestones and longer-term direction.

## License

[MIT](LICENSE).
