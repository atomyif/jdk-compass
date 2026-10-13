# Changelog

## 0.2.0 — no tagged release yet

- Add `--markdown` reports with the result, severities, finding codes, explanations, and suggested actions.
- Preserve diagnostic exit codes and strict mode in Markdown output; reject simultaneous JSON and Markdown selection.
- Escape Markdown table cells and keep environment values and absolute paths out of reports.
- Add behavior tests for Markdown formatting, exit policies, conflicting flags, and report privacy.

## 0.1.0 — initial MVP, no tagged release yet

- Detect Java major requirements from three common root version files.
- Compare the running JVM, selected JDK metadata, and project requirement.
- Report missing compiler, conflicting declarations, unsupported version formats, and JVM option variable presence.
- Provide text/JSON reports, strict mode, and documented exit codes.
- Include dependency-free build, behavior tests, bilingual documentation, and a proposed CI matrix.

This entry describes the initial implementation, not a tagged GitHub release.
