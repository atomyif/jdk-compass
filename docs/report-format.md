# Report format, schema version 1

```json
{
  "schemaVersion": 1,
  "toolVersion": "0.1.0",
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
