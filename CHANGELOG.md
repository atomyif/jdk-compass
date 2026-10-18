# Changelog

## 0.4.0 — no tagged release yet

- Compare recognized JDK `OS_ARCH` metadata with the running Java process's `os.arch` value.
- Normalize common x86_64, ARM64, and x86 aliases; report mismatches as warnings that fail strict mode.
- Skip missing or unsupported architecture values without exposing raw metadata or changing version checks.
- Add coverage for alias equivalence, mismatch exit policies, explicit JDK selection, absent metadata, and report privacy.

## 0.3.0 — no tagged release yet

- Add opt-in `--search-parents` discovery for checks started inside project subdirectories.
- Use the nearest directory containing supported version filenames; stop at a `.git` entry or filesystem root without merging different directories.
- Preserve explicit `--expect` overrides, invalid-file and conflict diagnostics, and relative source paths in all report formats.
- Add regression coverage for default behavior, nearest-directory precedence, repository boundaries, invalid configurations, and report privacy.

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
