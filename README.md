# JDK Compass

[中文说明](README.zh-CN.md)

A small, dependency-free command-line tool that explains why a Java project's version requirement, the running JVM, and `JAVA_HOME` disagree.

JDK Compass is useful when a project builds on one machine but fails on another, or when an IDE and a terminal appear to use different JDKs. It inspects configuration; it does not switch your JDK or run the project's build scripts.

**Status:** early MVP, version 0.5.0. Build from source using the instructions below; packaged GitHub releases are not available yet. The initial implementation was developed with AI assistance.

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
| Different runtime/JDK major | Warning with both versions, even without a project requirement |
| JDK architecture | Compares recognized `release` / `OS_ARCH` metadata with the running Java process's `os.arch`; warns on a difference |
| JVM option environment variables | Reports presence only, not their contents |

By default, all three version files are read only from the directory selected by `--project`. Version comparison is exact at the **major version** level, not a claim about source compatibility, patch level, or vendor compatibility. Aliases such as `latest`, `lts`, and `system`, and multiple fallback versions, are not interpreted. A single optional alphanumeric vendor prefix followed by `-` is accepted; this is not a complete SDKMAN/asdf version grammar.

### Compare the running JVM and selected JDK

The tool compares the running JVM's Java major with the selected JDK's `release` metadata, even when no supported project version file exists. For example, launching the tool on Java 17 with `JAVA_HOME` pointing to Java 21 adds `JDK_RUNTIME_VERSION_MISMATCH`, showing both versions and suggesting how to align them.

The same comparison applies to an explicit `--java-home`, including when `--expect` is supplied or project version files conflict. It is a warning because inspecting a different JDK can be intentional; `--strict` treats it as a failure. Existing project-requirement errors and the separate directory-difference warning still apply. Unreadable or invalid version metadata produces `JDK_RELEASE_UNREADABLE` instead of a version comparison. Only major versions are compared, without executing the selected JDK.

### Check JDK architectures

Architecture comparison runs automatically for `JAVA_HOME` or an explicit `--java-home`:

```sh
java -jar build/jdk-compass.jar --project /path/to/project --java-home /path/to/jdk --json
```

`amd64`, `x86_64`, and `x64` are treated as `x86_64`; `aarch64` and `arm64` as `arm64`; `x86` and `i386`/`i486`/`i586`/`i686` as `x86`. Names are case-insensitive. If both values are recognized, `JDK_ARCHITECTURE` reports the normalized pair. A difference also adds a `JDK_ARCHITECTURE_MISMATCH` warning, which fails only under `--strict` unless other errors exist.

Missing or unsupported architecture values skip this comparison without printing the raw values. No architecture finding means the comparison was unavailable, not that architectures matched. The tool compares metadata and the runtime-reported property; it does not inspect executable headers or detect physical hardware, emulation, or whether a different architecture can run successfully. Architecture checks still run when `--expect` overrides the project's Java version.

### Find configuration from a subdirectory

Use `--search-parents` when checking a directory below the project root:

```sh
java -jar build/jdk-compass.jar --project /path/to/project/src --search-parents --json
```

The search starts at the selected directory and walks its normalized path's parents. It stops at the nearest directory containing any of the three supported filenames, or at a directory containing a `.git` entry (a directory or worktree file), or the filesystem root. Configuration in the stopping directory is checked; files from different directories are never merged. A nearest configuration with invalid content, or no Java declaration, does not fall back to a more distant directory. `--expect` bypasses discovery entirely.

Reported sources stay relative to the selected directory, such as `../.java-version`. The tool does not invoke Git, follow the destination described in a `.git` file, or traverse sibling directories. Parent traversal is lexical; symbolic links are followed when accessing files, so this is not a filesystem isolation boundary.

## Reports and exit codes

Use `--json` for a machine-readable report with `schemaVersion`, `toolVersion`, `exitCode`, and `findings`. Each finding contains `code`, `severity`, `message`, and `suggestion`.

Use `--markdown` for a table you can paste into an issue or pull request. To save a report, redirect standard output:

```sh
java -jar build/jdk-compass.jar --project /path/to/project --expect 21 --markdown > jdk-report.md
```

The report includes the PASS/FAIL result, severity, finding code, message, and suggested action. Markdown uses the same checks and exit codes as text and JSON, including `--strict`. Choose either `--json` or `--markdown`; combining them is an argument error. See [report format](docs/report-format.md) for an example.

| Exit code | Meaning |
| --- | --- |
| `0` | No errors; warnings may still exist |
| `1` | At least one error, or at least one warning when `--strict` is set |
| `2` | Invalid command-line arguments; error goes to stderr, with no report |

`--help` and `--version` must be used on their own. Scripts should key on finding codes, not English message text. See [report format](docs/report-format.md).

## Privacy and boundaries

The diagnostic code makes no network calls and does not execute project files, JDK executables found at the inspected path, or shell startup scripts. It reads supported version files from the selected directory (or the nearest configuration directory with `--search-parents`) and the selected JDK's `release` file, with a 64 KiB limit per file; ordinary filesystem symbolic links are followed. Reports omit absolute paths and environment-variable values.

This is not a sandbox for the Java launcher: `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, and `_JAVA_OPTIONS` may already affect the JVM or be printed by Java **before** this program starts. Review raw terminal output before sharing it. The build command executes this repository's own build and test code.

## Current limitations

- Requires JDK 17+ to build and Java 17+ to run. It can inspect an older JDK's metadata but cannot itself run on Java 8/11.
- Does not read Maven profiles, `pom.xml`, Gradle toolchains, or editor-specific project settings yet.
- Examines the JVM that launched the tool, not every Java executable on `PATH` or an IDE's selected SDK.
- Does not validate that an inspected executable's contents match its `release` metadata.
- The initial GitHub Actions matrix passed on macOS, Windows, and Linux with Java 17, 21, and 25. Check [CI](https://github.com/atomyif/jdk-compass/actions) for the status of each subsequent commit.

## Development

Run `java Build.java` after code changes. Tests use isolated temporary fixtures, including missing compilers, conflicting versions, invalid files, JSON escaping, and secret-value suppression. See [CONTRIBUTING.md](CONTRIBUTING.md) and the [build and release instructions](docs/publishing.md).

The [development and maintenance roadmap (Chinese)](docs/roadmap.md) outlines the first-year milestones and longer-term direction.

## License

[MIT](LICENSE).
