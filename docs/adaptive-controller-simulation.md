# Simulation controller v1

This checkpoint implements an SDK-independent state controller, not a physiological
scoring algorithm. `SimulatedAdaptiveController` is deliberately disconnected from
the trainer UI and Samsung source. No installed application is updated.

## Input contract

`SimulationProtocol` has no defaults: callers supply positive cycle durations,
window/rest/freshness/acquisition/session durations, a positive score margin, and
a maximum cycle-duration adjustment. Times are monotonic milliseconds in one clock
domain. `SimulatedWindow` carries **synthetic scores**, not BPM, IBI or RMSSD.
The tests use cycles of 10 and 20 milliseconds solely to exercise state transitions.
Those numbers must never be used for human breathing.

An evidence window must have exactly the configured duration, be adequate, fresh,
finite, non-overlapping with accepted evidence and entirely within the current
trial or rate epoch. A quality flag here is supplied by a test fixture; this class
does not establish actual signal quality. It cannot compare real rates concurrently.
Future real integration needs a separate validated, sequential comparison protocol.

## State and selection rules

- Start clears prior results and enters signal acquisition. Acquisition success
  starts the first trial; an acquisition/trial timeout makes calibration unavailable.
- Calibration visits the supplied candidate order twice, with an explicit rest
  between trials. Every trial contributes one equal-duration single-rate window.
- Each pass ranks synthetic scores. Its winner must exceed the runner-up by at
  least `minMargin`; both passes must choose the same candidate. Otherwise no
  candidate is returned. Selection enters Ready, not automatic exercise.
- BeginSession requires a new acquisition window before starting the session clock.
- Adaptation needs complete synthetic comparisons of all candidates in two consecutive,
  independent windows with the same winner and required margin. The winner must
  differ from the current cycle by at most `maxCycleChangeMillis`.
- A pending change applies at the next complete cycle boundary. The new epoch
  starts at that boundary even if the next tick arrives late. Progress is
  `(now - epochStart) % cycleMillis / cycleMillis`; changing the cycle does not
  reinterpret the previous epoch. Inhale/exhale subdivision and haptic UI integration
  are not part of this controller checkpoint.
- Stale/invalid evidence suspends adaptation and clears pending decisions; the
  last cycle continues as a reference. Recovery needs a wholly fresh window.
  A product UI must explain suspension and offer Stop/fixed-mode choice.
- Pause invalidates decisions. Resume reacquires evidence and starts a new cycle;
  it does not claim within-breath continuation after a pause. Interrupted calibration
  restarts both passes. Adaptive-session elapsed time excludes pauses/acquisition,
  but includes time spent suspended.
- Stop cancels; source failure or backwards clock gives Error. Later evidence
  cannot restart these states. An explicit Start resets the controller.

## Validation scope

Tests exercise synthetic score windows, repeatability, ties, alternating preferences,
freshness, overlap, missing comparisons, clock errors, phase boundaries, step limits,
pause/resume, cancellation and failure. They do not validate raw IBI ingestion,
constant-IBI or phase-delay physiology, SDK batching, HRV accuracy, battery use,
onStop sensor cleanup or watch UI. Those belong to subsequent checkpoints.

There are no persistence, export, networking or SDK calls in the controller.
Existing trainer and sensor-screen behavior is unchanged.

Verification command (repository wrapper, Android Studio JBR):

```powershell
.\gradlew.bat :app:testDemoDebugUnitTest :app:lintDemoDebug
```

Verified 2026-09-04: BUILD SUCCESSFUL; all 39 demo unit tests passed, including
16 new controller tests (zero failures/errors). Kotlin production and test
compilation passed. Lint completed; detailed warning count is in the generated
local report. No APK assembly, emulator run, Samsung build or physical-watch test
was performed for this disconnected controller checkpoint.
The initial offline attempt lacked three previously selected build-dependency
versions in the chosen cache; the normal wrapper run resolved them successfully.

Proposed local commit: `feat: add simulation-only adaptive session controller`.
Requires specific user approval. Previous emulator-report edits remain separate.

Exercise: why are two repeatable synthetic score windows useful for testing the
controller but insufficient to prove that a breathing rate suits a person?
