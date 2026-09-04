# Receipt-anchored IBI estimates and cue history

First implementation slice from the user's acceleration context, 2026-09-04.
Existing uncommitted demo and observation documents are preserved, not rewritten.
The active repository remains the original PulseBreath tree, not the old clean-build
copy mentioned as an example in that context. SDK timestamps are already retained
since `07b8e27`; the attachment's statement that they are unused is partly outdated.

## Algorithm: receipt_anchor_v1 (experimental)

`expandBatch` assumes the retained IBI list is chronological and the final retained
beat ends at callback receipt. These are explicit estimation assumptions, not newly
validated Samsung timing guarantees. It does not use the opaque SDK timestamp.

For [800, 900, 850] at receipt T, chronological estimated ends are
[T - 850 - 900, T - 850, T]. Each result preserves its IBI and source index.
No IBI values are invented or interpolated. Delivery jitter remains in the estimate.

`TimedIbi.endMillis` is nullable, intentionally differing from the attachment's
non-null suggestion. An unknown rejected duration makes earlier beat placement
underdetermined: preserve the value with no estimated time rather than glue it to
the next continuous segment. Break markers are applied when walking backwards;
a trailing break invalidates the receipt anchor for the entire list. An unlocated
rejection likewise prevents placement. Nonpositive IBI or non-GOOD quality makes
the entry unaccepted and prevents backtracking through it. Underflow gives no time.
The batch retains rejected-entry count and trailing-break metadata. No records are
silently dropped, and no continuous relation across callbacks is asserted.

## Phase history

`PhaseHistory` retains at most 90 seconds and 6000 snapshots, whichever is smaller.
Strictly increasing snapshot timestamps are required. `phaseAt` returns the last
recorded cue at/before an estimated end, only within an explicit caller-supplied
maximum snapshot age, and never beyond the most recent snapshot. There is no
interpolation or extrapolation. Callers must clear history at session/rate-epoch
changes and pauses; this helper is not yet wired into Activity lifecycle.

## Integration boundary

This slice adds pure helpers and tests only. It does not change quality_v1.1,
Pearson alignment_v1, the Samsung adapter, UI, installed APK or timing gate.
One-observation-per-IBI integration must still preserve source-event quality:
dropping empty/rejected events would artificially improve the existing event
coverage metric. Do not feed only accepted unwrapped entries into that gate and
claim its original semantics survived. Keep raw-event quality evaluation separate.

Next: define and test the attachment's experimental `pace_v1` estimator, including
its detrending, uneven-sample handling, lag grid, minimum window/segments, peak
criterion and fallback. No resampling or correlation threshold is selected here.
Then connect one session owner and explicitly label receipt-based alignment as
estimated, not a measurement of chest respiration or personal resonance. This
does not implement continuous physiological adaptation or validate a 35-second
personal calibration. No new human measurement is performed in this slice.

Verification command:
```powershell
.\gradlew.bat :app:testDemoDebugUnitTest :app:lintDemoDebug --offline
```

Local commit: `feat: add receipt-anchored IBI and phase history helpers`.
Approved by the user on 2026-09-04 and resumed for local commit completion.

Verification: BUILD SUCCESSFUL; seven new helper tests passed (four batch and
three history tests), with no failure/error in these suites. Kotlin compilation
and demo lint completed. Full demo suite: 58 tests, zero failures/errors; lint: zero errors, two existing warnings. No APK assembly, install or sensor test in this slice.

Exercise: if an interval in the middle of a batch was rejected and its duration
removed, why is the end time of the earlier retained beat not uniquely recoverable?
