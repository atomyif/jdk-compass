# Report format, schema version 1

```json
{
  "schemaVersion": 1,
  "toolVersion": "0.3.0",
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
| JDK_HOME_DIFFERS_FROM_RUNTIME | Selected and running JDK directories differ |
| JDK_RELEASE_UNREADABLE | Release metadata or home comparison could not be read/validated |
| JVM_OPTIONS_PRESENT | A JVM startup variable is set; its value is omitted |

An invalid invocation returns exit code 2 and prints a short error to stderr; it produces no report. Diagnostic errors return code 1 and remain represented in JSON. Warnings affect the exit code only under `--strict`.

## Markdown reports

`--markdown` emits a human-readable report to stdout. It contains a heading, a PASS/FAIL result, and one table row per finding:

```markdown
## JDK Compass 0.3.0

Result: **PASS**

| Severity | Code | Message | Suggestion |
| --- | --- | --- | --- |
| INFO | RUNTIME | This diagnostic process runs on Java 17. | This is not necessarily the JDK selected inside your IDE. |
```

This excerpt shows one finding, not a complete run. Table separators, backslashes, inline Markdown markup, and HTML delimiters in cells are escaped; line breaks become `<br>`. Empty suggestions remain blank. Environment-variable values and absolute paths remain omitted.

Markdown uses the same exit policy as text and JSON. Warnings can appear in a PASS report unless `--strict` is selected; diagnostic errors produce a FAIL report and exit code 1. `--json --markdown` is invalid in either order and produces exit code 2 with stderr only. Shell redirection creates or overwrites the destination file, not the diagnostic program itself.

Markdown layout is intended for people and may evolve; use JSON schema version 1 for automation.
