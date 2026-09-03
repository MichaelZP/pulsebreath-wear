# PulseBreath Wear working agreements

## Project context

- Build a standalone Wear OS wellness and research app in Kotlin with Compose for Wear OS.
- Work on exactly one milestone at a time. Do not start the next milestone until the user explicitly accepts the current checkpoint.
- Keep the implementation small and educational. Explain changes in Polish; keep code, code comments, commit messages, and public technical documentation in English.

## Architecture boundaries

- `ui`: screens, breathing animation, haptics, and state presentation.
- `session`: breathing-session state machine and monotonic timing.
- `sensor`: sensor interfaces plus clearly separated fake and Samsung implementations.
- `signal`: pure, unit-tested signal-quality, HRV, RSA, and biofeedback calculations.
- `data`: local persistence and explicitly authorized export only.
- `docs`: decisions, metric definitions, validation protocol, and milestone log.
- Do not add a phone module, cloud service, account system, database, dependency-injection framework, or multi-module architecture without a demonstrated need and user approval.

## Safety and privacy

- Treat the app as wellness/research software, not a medical device or diagnostic tool.
- Never commit secrets, signing keys, keystores, `local.properties`, raw health data, device identifiers, or proprietary SDK binaries without confirmed redistribution rights.
- Never log persistent identifiers or full health-data streams by default.
- Keep simulated and real sensor sources visibly distinct in code, builds, UI, and exported data.
- Document every signal-processing formula, unit, threshold, window, source, validation status, and algorithm version.
- Do not silently interpolate, resample, filter, or discard physiological data.

## Build and verification

- Use the repository Gradle Wrapper; do not depend on a globally installed Gradle.
- Record exact build, unit-test, and lint commands after the Android Studio template is generated.
- Report separately: file edits, compilation, automated tests, lint, emulator execution, and physical-watch verification.
- Samsung Health Sensor SDK tests require a compatible physical Galaxy Watch; the emulator is only for SDK-independent builds.
- A change is complete only when relevant checks pass, documentation matches the code, failure and cancellation paths are handled, and the diff has been reviewed for regressions, lifecycle behavior, battery impact, privacy, and secrets.

## Git and publication

- Inspect `git status`, the active branch, remotes, and unrelated changes before editing.
- Keep branches, diffs, and proposed commits small and milestone-scoped.
- Show the changed files, concise diff, verification results, and proposed English commit message before committing.
- Do not commit unless the user approves the specific commit.
- Never push, create a GitHub repository, pull request, tag, release, or change repository visibility without explicit approval for that exact operation.
- CI must build and test an SDK-independent variant without private Samsung AAR files or signing secrets.

