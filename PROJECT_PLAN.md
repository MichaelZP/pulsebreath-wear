# PulseBreath Wear project plan

Status date: 2026-09-03  
Current milestone: 5 - implemented and verified; awaiting user review and acceptance

## Objective

Develop a standalone Wear OS breathing trainer for Samsung Galaxy Watch in small, teachable, and verifiable milestones. The app will start without sensors, then add deterministic simulated heart-rate and inter-beat interval (IBI) data, and only later integrate the Samsung Health Sensor SDK. Signal-quality and biofeedback results must remain distinct, documented, testable, and non-diagnostic.

## Confirmed decisions

| Decision | Value | Status |
| --- | --- | --- |
| Project directory | `C:\Users\user\Documents\Codex\pulsebreath-wear` | Confirmed |
| App name | `PulseBreath Wear` | Working name |
| Repository name | `pulsebreath-wear` | Working name; no remote repository authorized |
| Application ID | `pl.pulsebreath.wear` | Confirmed before Samsung integration |
| Source-code license | Apache-2.0 | Confirmed; add the license file in the appropriate repository milestone |
| GitHub visibility | Private proposed | Not yet authorized; no GitHub operation may be performed |
| Product category | Wellness/research | Confirmed; not diagnostic or medical software |

## Target device

- Product: Samsung Galaxy Watch9
- Model: SM-L355F
- Memory: 2 GB
- Wear OS version: pending verification
- Health Sensor Service: version 1.8.00.07, developer mode enabled and confirmed on the physical watch
- Samsung Health Monitor: confirmed visible by the user; this consumer app is not evidence that Health Sensor Service is exposed in the watch's app list
- Persistent device identifiers must not be copied into project files, logs, issues, commits, or documentation.

The Samsung Health Sensor SDK is intended for Galaxy Watch4 and later devices running Wear OS Powered by Samsung. Actual tracker support must still be checked at runtime because it can vary by device model and software version.

The supplied screenshots came from the Galaxy Wearable phone app, so they did not establish the watch's Wear OS or Health Sensor Service version. On this Galaxy Watch9 build, the ordinary application-details page did not expose the developer-mode control. The user enabled it through the service's internal settings activity after pairing the physical watch with ADB. The verified, identifier-free procedure is recorded in `docs/samsung-health-sensor-developer-mode.md`.

## Environment audit

| Component | Observed state | Required action |
| --- | --- | --- |
| Windows | Windows 11 Education, 64-bit, build 26100 | Suitable |
| CPU | Intel Core i7-4720HQ, 8 logical processors | Older than current emulator guidance; verify performance |
| RAM | 15.9 GB | Meets the current minimum for Studio plus one emulator, with little margin |
| Virtualization | WHPX 10.0.26100 detected and operational | Verified by a complete Wear OS AVD boot |
| Disk C | 14.5 GB free at the final Milestone 1 audit; a transient 2.3 GB low was observed during tool activity | Monitor free space while keeping SDK, AVD, and Gradle cache on D |
| Disk D | 51.6 GB free after SDK and AVD installation | SDK and AVD data installed here |
| Git | 2.55.0.windows.4 | Available |
| Java | System Java 8 remains unchanged; Android Studio bundles OpenJDK 25.0.3 | Use the bundled JDK for Android/Gradle |
| Android Studio | Quail 4, package 2026.1.4.7, installed in `C:\Program Files\Android\Android Studio` | First-launch privacy choice and setup completed by the user |
| Android CLI | 1.0.16251017 | Installed; invoke with `--no-metrics` unless the user opts in |
| Android SDK tools | Command-line Tools 22.0; SDK Manager is deprecated in favor of Android CLI | Available in `D:\Android\Sdk` |
| Android SDK / ADB | Platform 37.0, Build Tools 37.0.0, Platform-Tools 37.0.1 | Verified; ADB reports the emulator as `device` |
| Emulator | 37.1.11 with Wear OS 7.0/API 37 x86_64 image | Complete boot verified on a 384 x 384 round AVD |
| Gradle | No global installation | Expected; use the project Gradle Wrapper |
| GitHub CLI | Not detected | Optional until the GitHub milestone |

Configured local locations:

- Android SDK: `D:\Android\Sdk`
- Android Virtual Devices: `D:\Android\Avd`
- AVD: `PulseBreath_WearOS_7_API_37`
- ADB authentication: existing user key selected through `ADB_VENDOR_KEYS`; key contents are never stored in the project
- Persistent user environment: `ANDROID_HOME`, `ANDROID_AVD_HOME`, `ADB_VENDOR_KEYS`, and the three SDK tool directories in `PATH` are verified
- Global `JAVA_HOME`: intentionally unchanged; Android commands and future Gradle builds will use Android Studio's bundled JDK

The installer ignored the requested D: location for the IDE itself and installed Android Studio on C:. No relocation or uninstall is authorized. Heavy SDK and AVD content remains on D:.

## Milestones and acceptance criteria

### Milestone 0 - Environment and plan

Deliverables:

- local project directory and Git repository;
- this plan and a concise `AGENTS.md`;
- Android Studio, SDK tools, Platform-Tools, and one Wear OS test target;
- recorded tool versions and device information;
- reviewed risks and explicit approval before Milestone 1.

Acceptance:

- `git status` works locally and no remote is configured;
- Android Studio starts and uses a compatible bundled JDK;
- `adb version` works;
- either a Wear OS AVD starts or a documented hardware-only fallback is accepted;
- the physical watch can later be connected for Samsung SDK work;
- the user accepts this plan.

### Milestone 1 - Minimal runnable Wear OS app

- Generate the current official Empty Wear App template using Kotlin and Compose for Wear OS.
- Show the app name and one start button on a round display.
- Run the relevant build, unit test, and lint tasks.
- Explain Gradle structure, manifest, activity, and composables.

Acceptance: the smallest app builds and is manually visible on an emulator or explicitly accepted physical-device fallback.

Implementation status (2026-09-03): complete on `feature/milestone-1-basic-wear-app`; automated and visual verification passed. See `docs/milestone-1.md`.

### Milestone 2 - Sensor-free breathing trainer

- Add a two-minute session with configurable 4.5-second inhale and 5.5-second exhale phases.
- Use monotonic time, a session state machine, round-screen animation, progress, pause/resume/cancel, and transition haptics.
- Unit-test timing and state transitions.

Acceptance: a complete two-minute session works without sensors, including pause, resume, and cancel.

Implementation status (2026-09-03): complete on `feature/milestone-2-breathing-trainer`; automated and visual verification passed. See `docs/milestone-2.md`.

### Milestone 3 - Simulated BPM and IBI

- Define timestamped BPM/IBI/quality models and a sensor-source interface.
- Add deterministic scenarios for respiratory sinus arrhythmia, motion artifacts, signal loss, and recovery.
- Keep diagnostics limited to debug or demo builds and use independently calculated test fixtures.

Acceptance: sensor-independent algorithms run repeatably on the development computer and emulator.

Implementation status (2026-09-03): complete on `feature/milestone-3-simulated-bpm-ibi`; automated and visual verification passed. See `docs/milestone-3.md`.

### Milestone 4 - Samsung Health Sensor SDK

- Select a final application ID before integration.
- Add the locally supplied Samsung AAR without committing it.
- Keep an SDK-independent CI build and a separate Samsung-enabled build.
- Implement permissions, service lifecycle, capability checks, continuous heart-rate/IBI tracking, and documented error handling.

Acceptance: real BPM and IBI are demonstrated on the physical watch in developer mode; emulator claims are prohibited.

Implementation status (2026-09-03): complete on `feature/milestone-4-samsung-sensor`; automated checks, emulator regression tests, and physical Galaxy Watch BPM/IBI plus stop-lifecycle verification passed. See `docs/milestone-4.md`.

### Milestone 5 - Signal quality and basic HRV

- Specify formulas, units, examples, thresholds, window lengths, and validation status in `docs/metrics.md` before implementation.
- Calculate mean BPM, valid-IBI count and coverage, and RMSSD using pure tested functions.
- Display signal quality independently from biofeedback results.

Acceptance: results match documented test vectors and artifact handling is explicit.

Implementation status (2026-09-04): complete on `feature/milestone-5-signal-quality-hrv`; documented fixtures, automated calculations, lint, and emulator UI regression tests passed. See `docs/metrics.md` and `docs/milestone-5.md`.

### Milestone 6 - Biofeedback and resonance calibration

- Define and version `alignment_v1` for fixed-rate breathing.
- Test ideal, phase-shifted, noisy, missing-data, and non-respiratory cases.
- Add slow multi-rate calibration only after the fixed-rate method is stable.

Acceptance: the metric has an explicit formula and range, repeatable tests, and clearly presented limitations.

Implementation status (2026-09-04): `alignment_v1` is implemented and automatically verified for the fixed-rate simulated diagnostic stream. It is a guarded, non-clinical correlation score and deliberately does not run an automatic rate calibration. See `docs/metrics.md` and `docs/milestone-6.md`.

### Milestone 7 - Validation

- Write `docs/validation_protocol.md` before collecting data.
- Compare IBI differences, valid coverage, and HRV metrics; do not rely on correlation alone.
- Consider Polar H10 only after stable watch IBI collection.
- Keep participant data outside Git and pause for ethics/privacy review before involving other people.

Acceptance: the protocol, exclusion rules, comparison metrics, and limitations are reproducible.

Implementation status (2026-09-04): preliminary self-test confirmed start/stop and summary retention. Accuracy validation remains open. A `quality_v1.1` correction preserves rejected-IBI boundaries and suppresses displayed RMSSD for insufficient windows. See `docs/validation_protocol.md` and `docs/milestone-7.md`; participant readings are not stored in Git.

### Milestone 8 - GitHub, releases, and distribution review

- Add complete repository documentation, SDK installation instructions, security guidance, and an SDK-independent GitHub Actions workflow.
- Audit secrets, history, dependencies, licenses, data privacy, artwork/name rights, signing, and Samsung/Google requirements.
- Apply semantic versioning only to reproducible checkpoints.

Acceptance: each GitHub or distribution action has separate explicit user approval; no proprietary SDK, secrets, or health data are published.

## Main risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Samsung SDK cannot run in an emulator | Keep deterministic fake data and an SDK-independent build from the beginning |
| Samsung AAR redistribution restrictions | Store it locally, ignore only the exact binary path, and document manual setup |
| Provisional application ID later becomes costly to change | Choose the durable ID before Milestone 4, signing, partner registration, or distribution |
| Emulator performance on older CPU or limited RAM | Test one small round Wear OS AVD early; use a physical watch fallback if necessary |
| Sensitive health/device data enters Git or logs | Use synthetic fixtures, data minimization, explicit export, and pre-commit review |
| Unvalidated HRV or biofeedback claims | Version formulas, label working assumptions, test numerically, and separate wellness feedback from diagnosis |
| Background execution or haptics drains battery | Use monotonic timing, lifecycle-aware cleanup, short sessions, and milestone-specific battery review |
| Samsung policy or service-version incompatibility | Perform runtime capability checks and record the installed Health Sensor Service version before integration |

## Completed setup actions

1. Completed: initialize the local Git repository on `main` without committing or configuring a remote.
2. Completed: install the latest stable Android Studio and the required Android SDK components.
3. Completed: put SDK and AVD data on disk D.
4. Completed: verify the bundled JDK, ADB, WHPX acceleration, round emulator display, and a full Wear OS boot.
5. Completed: the user made the Android Studio privacy choice and finished the first-launch screen.
6. Completed: a fresh PowerShell session resolved `adb` and `emulator` from the persistent user `PATH`.
7. Verified by the user: `adb version` reports Platform-Tools 37.0.1 and `emulator -list-avds` reports `PulseBreath_WearOS_7_API_37`.
8. Completed: confirm Health Sensor Service 1.8.00.07 and enable its developer mode on the physical Galaxy Watch9. The exact Wear OS version can be recorded before Samsung SDK integration.
9. Completed: document the device-specific ADB route and observed wireless-debugging failures without storing the watch address, transient ports, pairing code, or persistent identifiers.
10. Milestone 0 was accepted and committed locally as `7d597db`.

## Immediate next actions

1. Review the validation protocol and its data-minimization boundary.
2. Repeat the already authorized self-test after verifying and installing the continuity correction.
3. Pause for privacy and ethics review before involving another participant.

## Official references

- OpenAI Codex best practices: https://learn.chatgpt.com/guides/best-practices
- Android Studio installation: https://developer.android.com/studio/install
- First Wear OS app: https://developer.android.com/training/wearables/get-started/creating
- Samsung Health Sensor SDK introduction: https://developer.samsung.com/health/sensor/guide/introduction.html
- Samsung SDK/app verification: https://developer.samsung.com/health/sensor/guide/app-verification.html
