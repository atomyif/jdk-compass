# Local validation — 2026-10-12

These results describe local verification only, not hosted CI runs or community adoption.

| Check | Result |
| --- | --- |
| macOS, Oracle JDK 17.0.12: `java Build.java` | 25 scenarios passed; runnable JAR built |
| macOS, JetBrains Runtime 25.0.4: `java Build.java` | 25 scenarios passed; runnable JAR built |
| Packaged JAR, Java 17, matching explicit requirement, strict JSON | Exit 0; JSON parsed successfully |
| Packaged JAR, Java 25, matching explicit requirement, strict JSON | Exit 0; JSON parsed successfully |
| Conflicting project version files | Exit 1; `REQUIREMENT_CONFLICT` present |
| Java major mismatch | Exit 1; mismatch findings present |
| Invalid arguments | Exit 2; error on stderr, no stdout |
| `--help` and `--version` | Exit 0 |
| Report privacy smoke check | No local `/Users/` paths in tested JSON output |
| Repository documentation links | Local links resolved |

The behavior tests additionally cover legacy version syntax, vendor suffixes, oversized files, non-regular files, unsupported aliases, missing compiler, invalid release metadata, environment-variable value suppression, JSON escaping, and read-only inspection.

Windows/Linux and Java 21 have not been run locally. A GitHub Actions matrix is included; its presence is not evidence that those runs have passed.
