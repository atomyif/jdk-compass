# Contributing

Start with a concrete Java environment problem and a minimal, sanitized reproduction. Check existing issues before opening another one. Feature proposals should explain who is affected and why the current checks do not resolve the problem.

## Workflow

1. Build the current code with `java Build.java` using a full JDK 17+.
2. For a bug, add a fixture or behavior test that fails before the fix.
3. Implement the smallest useful change. Keep inspection read-only and offline.
4. Run `java Build.java`, then test the packaged JAR against the reproduction.
5. Update user-facing documentation if behavior changed. Explain the problem and validation in the pull request.

Tests belong in `src/test/java/jdkcompass/MainTest.java`. Add a named scenario and use its temporary directory. Avoid depending on a contributor's actual JDK paths, network access, locale, or shell configuration.

## Design rules

- Java 17 remains the minimum runtime until a documented compatibility decision changes it.
- Prefer JDK APIs and a small codebase; justify new dependencies with user benefit.
- Do not execute project build files or scripts to infer their settings.
- Do not include credentials, local usernames, absolute paths, or full environment dumps in diagnostic reports.
- Treat finding codes and the JSON schema as public interfaces. Document incompatible changes.
- Keep AI-assisted contributions reviewable. Authors must understand the code, validate it, and disclose substantial generated code in their pull request.

Contributions are provided under the repository's MIT license. Respectful technical disagreement is welcome. Do not post personal information or credentials in issues.
