# Samsung shared guided session (pace_v1.1 integration)

The Samsung screen owns one `GuidedSessionCoordinator`; the coordinator owns the
cue state, phase history, and one streaming sensor subscription. MainActivity
remains the independent sensor-free trainer. No adaptive controller is enabled.

## States and lifetime

IDLE -> CALIBRATING -> READY -> RUNNING <-> PAUSED -> SUMMARY.
Each calibration attempt runs for 35,000 monotonic milliseconds, including
connection time. The timer completes even without sensor callbacks. A fallback
estimate automatically starts a fresh attempt, up to ten attempts total; each
attempt disconnects and reconnects the sensor and shows its number plus the last
fallback reason. A non-fallback estimate exits immediately to READY. After ten
fallbacks, READY clearly reports insufficient signal coverage and offers a manual
retry or cancellation; it never loops indefinitely. `PaceCalibrator.estimate` sets
inhale/exhale durations exactly once per attempt. Connection failure is shown;
absent/insufficient IBI still reaches the ordinary pace_v1.1 fallback. No trial
search, rate updates, or lower quality threshold occurs. The guided run lasts
120,000 active milliseconds; pause excludes time.

Calibration completion, pause, stop, cancellation, backgrounding and destruction
release the sensor subscription. Resume creates a fresh Samsung source, retains
pace and active elapsed time, and starts fresh signal/phase windows. Generation
tokens discard queued callbacks from older subscriptions. Sensor errors pause a
running session. The activity drives the owner on the UI thread and ticks every
50 ms only during calibration/running. Keep-screen-on also remains active in READY,
so the user can read the calibration result until choosing the next action. READY and
PAUSED do not consume sensor power.

## Beat mapping and quality

Both cue snapshots and receipt times use `SystemClock.elapsedRealtime` in ms.
Each raw sample passes through `expandBatch`; each positive accepted, placed IBI
with a matching cue snapshot produces a singleton `AlignmentObservation` at its
own estimated end time. PhaseHistory uses the previous recorded phase, at most
250 ms old. There is no interpolation or extrapolation. Pre-epoch, overlapping,
unordered, unplaceable and history-missing beats cannot be scored. A break or
unusable interval clears the current alignment segment. Empty/trailing breaks
are carried into the next batch and preserved as unplaced markers for calibration.
No missing beat is synthesized. Multi-point callbacks sharing a receipt anchor
may be unusable; availability is preferable to an invented beat timeline.

HrvAnalyzer receives the unchanged raw events on a rolling 60,000 ms window,
pruned against the current clock. Alignment observations are also pruned against
that clock. The unchanged alignment_v1 Pearson analyzer runs on the per-beat
observations. Its result is additionally unavailable if raw-event quality_v1.1
is insufficient or raw invalid IBI exists. Thus batch expansion cannot turn one
valid raw event into many events and hide poor event coverage. Break handling is
conservative and may reduce availability. No changes to quality_v1.1 formulas,
thresholds or break semantics were made.

The summary shows the final rolling alignment availability/score and HRV metrics,
plus the arithmetic mean of finite positive BPM values from GOOD raw events during
RUNNING across the session (excluding calibration/pause). This is event-weighted,
not time-weighted or derived from IBI. `usedFallback` and reason are displayed;
cancellation before calibration completes explicitly has no estimate. Stop clears
raw samples, beats, phase history and latest live reading, retaining aggregate
metrics and session-only timing diagnostics in memory. There is no export/storage.

## Validation scope

Synthetic coordinator tests cover timed calibration, no-data fallback, mapping a
nonfallback PaceEstimate, trailing rejection continuity, per-beat phase attribution
against the unchanged Pearson analyzer, raw-event coverage, pause/resume epoch
isolation, late callbacks, cancellation, sensor failure and natural completion.
Synthetic oscillations are wiring tests, not evidence of live Samsung alignment.
Receipt-anchored IBI timing and wrist PPG alignment remain unvalidated on hardware.
This is wellness software, not measured respiration or a medical assessment.

Run with the repository wrapper and installed local Android SDK/Samsung AAR:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'D:\Android\Gradle'
.\gradlew.bat :app:testDemoDebugUnitTest :app:testSamsungDebugUnitTest :app:lintDemoDebug :app:lintSamsungDebug :app:assembleDemoDebug :app:assembleSamsungDebug --offline
```

No emulator or physical-watch execution was performed for this change.

Verified on 2026-09-04: all 76 demo and 79 Samsung unit tests passed, including
nine coordinator tests in each flavor. Both debug APKs assembled. Lint reported
zero errors, two existing demo warnings (`ModifierParameter`, `WearRecents`) and
one existing Samsung warning (`ModifierParameter`). No new lint warnings remain.
The pre-existing uncommitted demo screen/tests were present during verification
and are excluded from this focused commit.
