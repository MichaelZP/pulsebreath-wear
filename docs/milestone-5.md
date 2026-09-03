# Milestone 5 - Signal quality and basic HRV

Status: implemented and automatically verified; awaiting user review and acceptance

## Deliverables

- `signal/HrvAnalyzer.kt`: a pure source-independent `quality_v1` analyzer;
- `docs/metrics.md`: formulas, units, 60-second window, thresholds, inputs, exclusions, and limits;
- static fixture `hrv_quality_v1.csv` with independently calculated expected results;
- unit tests for constant intervals, an explicit artifact break, invalid input, a window boundary, an empty good event, and an invalid IBI in a non-good event;
- debug diagnostics that show the source signal state separately from HRV-window quality, IBI-event coverage, and RMSSD;
- Samsung sensor screen integration that maintains an in-memory 60-second session window and shows the same metrics for real readings.

## Algorithm behavior

The analyzer never interpolates, resamples, smooths, or replaces an IBI. It only calculates RMSSD differences inside uninterrupted good-quality segments. An explicit motion, lost, recovering, or nonpositive-IBI event breaks the segment.

The UI's `ADEQUATE` and `INSUFFICIENT` labels are completeness guards defined by `quality_v1`; they are not medical categories or individual health scores.

## Verification

```text
testDemoDebugUnitTest       16 passed
testSamsungDebugUnitTest    19 passed
assembleDemoDebug           passed
assembleSamsungDebug        passed
lintDemoDebug               passed
lintSamsungDebug            passed
connectedDemoDebugAndroidTest passed on Wear OS 7/API 37 emulator
```

The physical watch had previously verified real BPM and IBI in Milestone 4. The latest HRV-display APK was built successfully, but was not reinstalled during this checkpoint because its transient wireless ADB endpoint became unavailable. This does not affect the deterministic calculation tests or the emulator UI regression test.

## Suggested manual exercise

In the debug-only `PulseBreath Diagnostics` app, let the simulated stream run through calm, RSA, motion, loss, and recovery. Observe that:

1. source quality can change even when a window has historical valid IBI values;
2. the window remains `INSUFFICIENT` until it has at least 10 valid IBI and 80% IBI-event coverage;
3. RMSSD is absent before two uninterrupted valid IBI values and does not bridge the motion/loss/recovery break.

## Deferred work

- no RSA alignment or resonance score is calculated yet;
- no clinical validation, baseline, alert, medical interpretation, persistence, export, or Polar comparison is included;
- the `quality_v1` thresholds must be reviewed against the validation protocol before any external-data comparison.
