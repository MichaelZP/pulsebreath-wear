# Milestone 8 - Local repository and CI preparation

## Scope

Branch: `feature/milestone-8-repository-ci`. No remote, push, tag or release is authorized.
No application or signal-processing code changes are part of this milestone checkpoint.

- README: build, installation, variant boundaries, limitations and documentation map.
- LICENSE: official Apache-2.0 text, accepted by the project owner; third-party terms remain separate.
- SECURITY and release checklist: privacy, signing, licensing and explicit publication gates.
- CHANGELOG: unreleased status rather than an invented public version.
- Demo CI: read-only permissions, immutable action commits, JDK 25, SDK 37,
  Build Tools 36.0.0, wrapper validation through setup-gradle, unit tests, lint and compilation.
- Ignore rules protect local environment files, signing containers, private data and binary outputs;
  attributes ensure Unix line endings for the wrapper and YAML.

The action commits were resolved from the upstream version tags on 2026-09-04:
[checkout v7](https://github.com/actions/checkout/tree/3d3c42e5aac5ba805825da76410c181273ba90b1),
[setup-java v6](https://github.com/actions/setup-java/tree/dd06d9cba3e5552c54d9f8ea23572deb30010f7c),
[setup-gradle v6](https://github.com/gradle/actions/tree/4733eaac7c1b0da527e4206b7671e0061de1ce37),
[setup-android v4](https://github.com/android-actions/setup-android/tree/40fd30fb8d7440372e1316f5d1809ec01dcd3699).

## Audit scope and remaining gates

All reachable commits were scanned for selected private-key headers, GitHub/AWS token patterns,
local-network addresses and the known device-identifier pattern; no matches were found.
The reachable-history filename scan found no AAR/APK/AAB, keystore/JKS, PEM/P12 or
`local.properties` paths. Ignore checks passed for SDK, local properties, keystores,
environment files, private-data directory and APK outputs. This is a limited pattern audit,
not a comprehensive secret, vulnerability or legal audit; binary contents were not scanned.

No app networking permission, health-data logging or persistence implementation was found in
the reviewed source. Backups and data transfer are disabled in the main configuration.
Full dependency/license/vulnerability review, artwork/name clearance, private reporting channel,
Samsung distribution approval and Google Play policy review remain pending. See the
[release checklist](release-checklist.md). No clinical accuracy claim is permitted.

## Verification

A fresh temporary source copy was made from tracked and milestone files, excluding ignored
files. The Samsung AAR and `local.properties` were absent. Android SDK was supplied via
`ANDROID_HOME`; the existing Gradle download cache was reused. This tests source independence,
not a fully uncached or Linux build. The command was:

```powershell
.\gradlew.bat --no-daemon testDemoDebugUnitTest lintDemoDebug assembleDemoDebug --console=plain
```

Result on 2026-09-04: BUILD SUCCESSFUL in 5m 13s, 51 tasks executed; 23 unit tests,
zero failures/errors. Lint: zero errors, one pre-existing `ModifierParameter` warning at
`DebugDiagnosticsActivity.kt:129`. The demo debug APK was compiled without the Samsung AAR.
Gradle installed its default Build Tools 36.0.0 using the already accepted local SDK license.
Local Markdown link checks and Git whitespace checks passed. YAML was manually reviewed;
no standalone YAML/actionlint validator was available locally.
The GitHub workflow has not run remotely; no emulator or physical-watch tests
were repeated for these documentation/CI changes. No application was installed on a device.

## Learning exercise and acceptance

Find the workflow step that asserts the Samsung AAR is absent and explain why only `Demo`
tasks follow it. Then compare an APK compilation result with actual sensor accuracy validation.
Review the README and unresolved release gates before approving the proposed local commit:
`chore: add repository documentation and demo CI`.
Approval of this local commit does not authorize any GitHub or distribution operation.
