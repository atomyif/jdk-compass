# Report format, schema version 1

```json
{
  "schemaVersion": 1,
  "toolVersion": "0.4.0",
  "exitCode": 0,
  "findings": [
    {
      "code": "REQUIREMENT",
      "severity": "INFO",
      "message": "Required Java major: 17; source: [.java-version]",
      "suggestion": ""
    }
  ]
}
```

The example shows one finding, not a complete run. Severity is `INFO`, `WARN`, or `ERROR`. Findings are an array; a code may appear more than once. `JVM_OPTIONS_PRESENT` can appear separately for each relevant variable. Consumers must tolerate new codes, message changes, additional fields, and additional findings within schema version 1. Do not parse the prose.

With `--search-parents`, requirement sources and invalid-file messages can include relative paths such as `../../.sdkmanrc`. Absolute paths remain omitted. Discovery uses only the nearest directory containing a supported filename and does not merge ancestor configurations; existing finding codes and schema version 1 are unchanged.

Current codes:

| Code | Meaning |
| --- | --- |
| PROJECT_NOT_FOUND | Requested project directory unavailable |
| VERSION_FILE_INVALID | Unsupported, ambiguous, unreadable, or oversized version declaration |
| REQUIREMENT_UNKNOWN | No supported requirement identified |
| REQUIREMENT_CONFLICT | Supported declarations specify different major versions |
| REQUIREMENT | Resolved requirement and its sources |
| RUNTIME | Java major of this diagnostic process |
| RUNTIME_VERSION_MISMATCH | Runtime major differs from requirement |
| JAVA_HOME_UNSET | JAVA_HOME absent or blank, with no explicit home supplied |
| JAVA_HOME_INVALID | JAVA_HOME contains an invalid filesystem path |
| JDK_HOME_RELATIVE | Selected JDK path is not absolute |
| JDK_HOME_NOT_FOUND | Selected JDK directory is unavailable |
| JDK_JAVA_MISSING / JDK_JAVAC_MISSING | Required executable absent or not executable |
| JDK_HOME_VERSION | Java major read from selected JDK metadata |
| JDK_HOME_VERSION_MISMATCH | Selected JDK major differs from requirement |
| JDK_ARCHITECTURE | Recognized selected-JDK and runtime architectures, normalized for comparison (INFO) |
| JDK_ARCHITECTURE_MISMATCH | Recognized selected-JDK architecture differs from runtime architecture (WARN) |
| JDK_HOME_DIFFERS_FROM_RUNTIME | Selected and running JDK directories differ |
| JDK_RELEASE_UNREADABLE | Release metadata or home comparison could not be read/validated |
| JVM_OPTIONS_PRESENT | A JVM startup variable is set; its value is omitted |

An invalid invocation returns exit code 2 and prints a short error to stderr; it produces no report. Diagnostic errors return code 1 and remain represented in JSON. Warnings affect the exit code only under `--strict`.

## Architecture findings

`JDK_ARCHITECTURE` and `JDK_ARCHITECTURE_MISMATCH` are emitted only when the selected JDK has valid version metadata and both architecture values are recognized. The comparison maps `amd64`/`x86_64`/`x64` to `x86_64`, `aarch64`/`arm64` to `arm64`, and `x86`/`i386`/`i486`/`i586`/`i686` to `x86`, ignoring case and surrounding whitespace. Missing or unsupported values are omitted rather than reported as matches. These additions retain JSON schema version 1.

The selected value comes from the `OS_ARCH` field in the JDK's `release` metadata; the runtime value comes from `System.getProperty("os.arch")`. See [OpenJDK release metadata](https://github.com/openjdk/jdk17u/blob/master/make/ReleaseFile.gmk) and [Java system properties](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html). Both are reported metadata, not executable-header validation or physical-hardware detection. A mismatch is a warning because using another architecture can be intentional.

## Markdown reports

`--markdown` emits a human-readable report to stdout. It contains a heading, a PASS/FAIL result, and one table row per finding:

```markdown
## JDK Compass 0.4.0

Result: **PASS**

| Severity | Code | Message | Suggestion |
| --- | --- | --- | --- |
| INFO | RUNTIME | This diagnostic process runs on Java 17. | This is not necessarily the JDK selected inside your IDE. |
```

This excerpt shows one finding, not a complete run. Table separators, backslashes, inline Markdown markup, and HTML delimiters in cells are escaped; line breaks become `<br>`. Empty suggestions remain blank. Environment-variable values and absolute paths remain omitted.

Markdown uses the same exit policy as text and JSON. Warnings can appear in a PASS report unless `--strict` is selected; diagnostic errors produce a FAIL report and exit code 1. `--json --markdown` is invalid in either order and produces exit code 2 with stderr only. Shell redirection creates or overwrites the destination file, not the diagnostic program itself.

Markdown layout is intended for people and may evolve; use JSON schema version 1 for automation.
