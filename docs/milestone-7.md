# Milestone 7 - Validation protocol

Status: preliminary self-test confirmed by the user; accuracy validation pending

## Continuity correction and manual verification

The user confirmed that a physical-watch measurement can be stopped and its
summary remains visible. This verifies the interaction, not measurement accuracy.
No participant measurement values are stored in this document.

Code review identified lost rejection boundaries in the Samsung mapper.
`quality_v1.1` now preserves these boundaries and counts rejected entries,
including mismatched SDK lists. Both diagnostic screens suppress numeric RMSSD
when the window is INSUFFICIENT. See `docs/metrics.md` for the revision contract.
The corrected APK was installed on the physical watch and the sensor activity
launched successfully. The user confirmed that RMSSD displays unavailable,
the rejected-entry counter is visible and populated, and the summary remains
visible after stopping. This is functional verification only; accuracy
validation remains pending.

Verification of the correction (2026-09-04):

```text
gradlew.bat --no-daemon testDemoDebugUnitTest testSamsungDebugUnitTest assembleSamsungDebug lintSamsungDebug lintDemoDebug --console=plain
BUILD SUCCESSFUL
demo unit tests: 23 passed
Samsung unit tests: 29 passed
Samsung debug APK: built
demo and Samsung lint: passed
```

Regression cases cover a rejected entry at the beginning, middle and end of a
batch, a rejected-only batch, unequal value/status list lengths, preservation of
valid within-segment differences, and suppressing RMSSD below the quality gate.
Installation succeeded using ADB push installation after a streamed installation
failed. No new emulator UI run is claimed for this patch.

## Deliverables

- `docs/validation_protocol.md` defines a self-test-first procedure before any
  data collection;
- explicit 60-second window inclusion and exclusion rules;
- predeclared comparison fields for BPM, RMSSD, valid IBI count, and coverage;
- descriptive bias, mean absolute difference, and Bland-Altman limits of
  agreement formulas, with correlation explicitly insufficient on its own;
- data minimization and a hard boundary before involving other participants.

## What this milestone does not do

- It does not collect, commit, upload, or export raw health data.
- It does not claim that Galaxy Watch IBI and Polar H10/ECG values are
  interchangeable.
- It does not add a Polar SDK, account, cloud service, database, rate sweep, or
  automatic calibration.
- It does not start a test with another person.

## Before a self-test can be recorded

Review `docs/validation_protocol.md`, confirm that any optional Polar H10 export
method can produce matching window summaries, choose a storage location outside
Git, and authorize collection. The user has authorized the initial self-test;
this is not a new approval requirement for repeating that same scoped check.
The real sensor screen has a rolling window and manually controlled duration;
its BPM label is instantaneous, not the protocol's window mean. A full paired
validation run therefore remains pending suitable timing and summary support.
