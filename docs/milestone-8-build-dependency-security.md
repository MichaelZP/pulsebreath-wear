# Milestone 8 follow-up — Build dependency security

Date: 2026-09-04. Base: `3ed6247`. Branch: `feature/milestone-8-build-dependency-security`.
No commit, remote CI run, device installation or publication is authorized by this checkpoint.

## Changes and scope

Root `buildscript.dependencies.constraints` sets minimum fixed versions for existing
transitive build dependencies. Constraints do not add these libraries to application runtime.
Gradle resolution confirmed these exact coordinates, all solely in `build:::classpath`:

| Module | Previous | Resolved | Advisory |
| --- | --- | --- | --- |
| org.jdom:jdom2 | 2.0.6 | 2.0.6.1 | [CVE-2021-33813](https://github.com/advisories/GHSA-2363-cqg2-863c) |
| org.bitbucket.b_c:jose4j | 0.9.5 | 0.9.6 | [CVE-2024-29371](https://github.com/advisories/GHSA-3677-xxcr-wjqv) |
| org.apache.commons:commons-lang3 | 3.16.0 | 3.18.0 | [CVE-2025-48924](https://github.com/advisories/GHSA-j288-q9x7-2f5v) |

AGP, Kotlin, application dependencies, sensor code, HRV algorithms, permissions and CI
workflow were not changed. Test assertions pin the reviewed resolution baseline and
require review before KAPT is introduced. They are not a replacement for live advisory scans.

The license parser now supports POMs without XML namespaces, with a regression test.
This recovers original POM declarations for JDOM, kXML, javax.inject and Bouncy Castle;
only two Foojay fallbacks remain active. Historical unused fallback entries are retained.
The generated Maven inventory still has 259 coordinates / 28 configurations and zero
unresolved license entries. Its 41 referenced upstream legal texts replace the obsolete
Commons Lang NOTICE; the previous NOTICE remains recoverable from Git history.
The 119-coordinate application runtime inventory is unchanged.

## Repeat OSV scan

The live OSV API was queried after resolution for all 259 exact Maven coordinates and
four pinned CI action commits. Inventory SHA-256:
`8f3b084e17f64aa8be0a996baaca9e2b2f20dcd448612afeb15d80a479d7bad7`.

The three findings above no longer match. Three raw matches remain, all build-only:

- Kotlin Gradle Plugin 2.4.10: [CVE-2026-53914](https://github.com/advisories/GHSA-r937-wjx7-w2jp).
  The [upstream fix](https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6)
  changes KAPT incremental-cache deserialization. No KAPT declaration exists in the reviewed
  build files; exploitability is not established. This is NOT a fixed vulnerability.
  The [release schedule](https://kotlinlang.org/docs/releases.html), checked on 2026-09-04,
  still identifies 2.4.10 as the released stable version and 2.4.20 as upcoming.
  OSV's first fixed boundary is 2.4.20-Beta1. No prerelease migration was performed.
  Reassess a stable compatible fix before enabling KAPT; do not import untrusted caches.
  Gradle configuration cache and KAPT incremental cache must not be conflated.
- Bouncy Castle bcprov 1.80.2: OSV matches CVE-2026-0636, but the
  [vendor advisory](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%900636)
  explicitly lists 1.80.2 as fixed. This exact advisory/version match is a false positive.
- Bouncy Castle bcpkix 1.80.2: same backport-range issue for CVE-2026-5588; the
  [vendor advisory](https://github.com/bcgit/bc-java/wiki/CVE%E2%80%902026%E2%80%905588)
  explicitly lists 1.80.2 as fixed. No blanket Bouncy Castle suppression is justified.

No matches were returned for the 119 main-runtime coordinates or four action commits.
No match does not establish database coverage, binary reachability or absence of unknown
vulnerabilities. Proprietary Samsung SDK, host JDK/Gradle/Android SDK distributions,
embedded CI tool dependencies and the runner OS remain outside this scan.

## Verification

```powershell
.\gradlew.bat --no-daemon --no-configuration-cache -I scripts/license-inventory.init.gradle licenseInventory --console=plain
python scripts/license_inventory.py --resolution build/reports/licenses/resolution.json --cache "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1" --output docs/licenses --assets app/src/main/assets/open_source
python -m unittest discover -s scripts -p "test_*.py"
.\gradlew.bat --offline --no-daemon testDemoDebugUnitTest testSamsungDebugUnitTest lintDemoDebug lintSamsungDebug assembleDemoDebug assembleSamsungDebug --console=plain
```

Resolution passed with zero failures. All nine Python regression tests passed.
Both-variant build: BUILD SUCCESSFUL in 10m 57s, 101 tasks executed; demo 23 tests and
Samsung 29 tests, zero failures/errors. Lint has zero errors and the existing
`ModifierParameter` warning in each variant. Both debug APKs were built.
Clean source-copy demo: BUILD SUCCESSFUL in 6m 7s, 51 tasks executed, 23 tests with
zero failures/errors, zero lint errors and the same existing warning. Command:
`./gradlew.bat --offline --no-daemon --no-build-cache testDemoDebugUnitTest lintDemoDebug assembleDemoDebug --console=plain`.
The clean source copy excludes ignored files including Samsung AAR and local.properties,
uses ANDROID_HOME and existing dependency downloads, and disables the task-output build cache.
It is not a fully uncached or Linux/GitHub CI run.
Git whitespace checks passed, all 41 legal-text files are referenced, and app source and
runtime license assets are unchanged. No emulator or physical-watch tests were repeated.

## Exercise and checkpoint

Explain why a constraint in the root build classpath fixes a build dependency but adding
the same coordinate to app `implementation` would not fix that classpath.

Proposed local commit: `fix: patch vulnerable build dependencies`.
Wait for approval of the exact commit. Keep the Kotlin finding and publication gates open.
