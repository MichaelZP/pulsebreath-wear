# Milestone 3: Simulated BPM and IBI

Date: 2026-09-03

## Result

The project now contains a sensor-source boundary and a deterministic synthetic implementation. A debug-only launcher activity displays simulated beats per minute (BPM), inter-beat intervals (IBI), signal quality, breathing phase, and the active test scenario on the Wear OS emulator.

The diagnostic UI always displays `DANE SYMULOWANE`. No sensor permission, Samsung SDK, health data, network access, storage, or device identifier is used.

## Data contract

`SensorSample` contains:

- `monotonicTimestampMillis`: monotonic sample timestamp in milliseconds;
- `beatsPerMinute`: nullable BPM value;
- `ibiMillis`: zero or more IBI values in milliseconds;
- `quality`: explicit signal-quality state;
- `sourceType`: explicit simulated or Samsung source identity.

`SensorDataSource` accepts a `SensorSampleRequest` and returns one sample. The request carries monotonic session time plus the current breathing phase and its normalized progress. This small pull-style contract is sufficient for deterministic desktop and emulator tests. The Samsung integration milestone must review the contract against the SDK's callback lifecycle before adding the real implementation.

## Deterministic script

The fake source repeats this 40-second script:

| Active time | Scenario | Output |
| --- | --- | --- |
| 0-10 s | Calm | 60 BPM, 1000 ms IBI, good quality |
| 10-20 s | Respiratory sinus arrhythmia (RSA) | BPM follows breathing phase, good quality |
| 20-25 s | Motion artifact | deliberately inconsistent BPM and IBI, motion quality |
| 25-30 s | Signal loss | null BPM, empty IBI list, lost quality |
| 30-40 s | Recovery | BPM falls deterministically from 72 to 60, recovering quality |

The sequence repeats to make every state easy to reproduce during development.

## Working RSA model

This is synthetic test behavior, not a physiological estimator or validated model.

For phase progress `p` in the closed interval `[0, 1]`:

```text
respiratoryWave = -1 + 2p    during inhale
respiratoryWave =  1 - 2p    during exhale
BPM = roundTo0.1(60 + 6 * respiratoryWave)
IBI_ms = round(60000 / BPM)
```

The generated heart rate therefore rises from 54 to 66 BPM during inhale and falls from 66 to 54 BPM during exhale. The numbers are working synthetic values chosen to make phase coupling visible and testable. They are not medical thresholds and are not yet validated against real data.

## Quality states

- `GOOD`: synthetic values are internally usable for the scenario.
- `MOTION_ARTIFACT`: values are deliberately inconsistent and must not be treated as trustworthy.
- `SIGNAL_LOST`: no BPM or IBI value is invented.
- `RECOVERING`: values have returned but are kept distinct from good quality.

Signal quality remains separate from BPM/IBI and from any future biofeedback score.

## Debug-only diagnostics

`DebugDiagnosticsActivity` and its launcher manifest entry live under `app/src/debug`. The debug APK therefore exposes `PulseBreath Diagnostics`, while the release APK contains only the main PulseBreath launcher activity.

The diagnostics screen changes automatically according to the scripted timeline. It is intentionally read-only and does not claim to be connected to a watch sensor.

## Independent fixture

Expected sample rows are stored in `app/src/test/resources/fixtures/fake_sensor_expected.csv`. The CSV contains explicit expected BPM, IBI, quality, and scenario values rather than calculating expectations with production formulas. Unit tests parse this file and compare every field with generator output.

## Verification

Development target: `PulseBreath_WearOS_7_API_37`, Wear OS 7/API 37, 384 x 384 round emulator.

Command:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:connectedDebugAndroidTest
```

Results:

- eleven JVM unit tests passed: seven breathing-session tests and four fake-source tests;
- six connected Compose UI tests passed: four trainer tests and two diagnostics tests;
- debug APK, debug test APK, and release APK assembled successfully;
- Android lint reported no issues;
- merged-manifest inspection found the diagnostics activity only in the debug variant;
- calm, RSA, motion artifact, signal loss, and recovery screens were inspected on the round emulator;
- no physical watch or Samsung sensor was used or required.

## Known limitations

- The generator emits test samples on demand; it is not yet a continuous callback or `Flow` implementation.
- Synthetic values are intentionally simple and are not suitable for physiological conclusions.
- IBI lists contain only one normal synthetic interval, except for the explicit motion-artifact pair.
- No filtering, HRV calculation, persistence, or export is part of this milestone.
