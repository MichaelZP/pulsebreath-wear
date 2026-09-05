# Adaptive breathing — implementation and validation gates

Status: checkpoint 1 approved for implementation, 2026-09-04.
See [simulation controller](adaptive-controller-simulation.md) for its exact scope.
No real-data protocol approved by this document.

## Requested outcome

The user requests both pre-session calibration and ongoing rate adaptation during
the session. Publication preparation is paused in favor of this functional work.
Keep the sensor-free trainer available as an explicitly separate mode.

## Verified implementation gap

- `MainActivity.kt` starts `BreathingSessionState` with a fixed configuration and
  does not connect a sensor source.
- `BreathingSession.kt` defaults to 4.5-second inhalation and 5.5-second exhalation.
  Replacing its configuration while running is not a phase-continuous rate controller.
- `BreathingAlignmentAnalyzer.kt` implements fixed-template Pearson correlation;
  milestone 6 explicitly deferred real-sensor correlation and multi-rate calibration.
- `SamsungSensorDataSource.kt` assigns callback receipt time using
  `SystemClock.elapsedRealtime()`. `SensorSample` holds a list of IBI values with
  one event timestamp, not independently established beat timestamps.
- A score against a simulated cue is not a validated selector of personal resonance.

Samsung documents multiple IBI values per event and different batching behavior
with the display off. This makes timing and coverage interpretation a prerequisite,
not a minor display change:
[data specifications](https://developer.samsung.com/health/sensor/guide/data-specifications.html),
[HeartRateSet](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/ValueKey.HeartRateSet.html).
Reviewed 2026-09-04. Exact beat alignment remains unverified.

## Small implementation checkpoints

1. Build a pure, SDK-independent controller and deterministic tests first. States:
   idle, acquiring signal, calibration trial, inter-trial rest, calibration unavailable,
   ready, adaptive session, adaptation suspended, paused, completed, cancelled, error.
   Test fixtures are software inputs, not a human breathing prescription.
2. Establish an explicit timing contract: sensor versus receipt timestamps, clock
   domains, event ordering, batched IBI, gaps, rejection markers and data age.
   If reliable phase association cannot be established, keep real adaptation unavailable.
   Never silently reconstruct missing beats or treat arrival time as exact beat time.
3. Review and approve a real-session experimental protocol before enabling it:
   candidate rates, order, equal trial durations, settling/rest periods, timeout,
   quality/coverage gates, minimum usable cycles, comparison metric, repeatability,
   tie handling, maximum duration and cancellation. Specify actual numerical values
   and their evidence before implementation for real users; none are selected here.
4. Integrate visibly simulated calibration and adaptation in the demo variant.
   Review that workflow before connecting the Samsung variant.
5. After timing/protocol approval, connect the Samsung source with permission and
   explicit Start, session cancellation, lifecycle cleanup and local-only state.
   Verify on a watch; do not present software tests as physiological validation.

## Controller requirements

- Evaluate fresh, sufficiently long windows; do not react to each BPM update.
- Define and test a bounded adjustment policy, hysteresis and a decision interval.
  Apply an accepted rate change only at a complete breathing-cycle boundary.
- Do not combine calibration trials or pre/post-change samples into one scored window.
- Require comparable quality and reproducible evidence for a rate preference.
  Neither maximum RMSSD nor the existing correlation alone establishes resonance.
- On missing, stale, rejected or inadequate data: do not select or update a rate;
  display the reason. Offer stopping or an explicitly chosen fixed-rate trainer.
- Pause, loss of foreground, source error and cancellation invalidate pending decisions;
  stop sensor collection on exit. Resume requires fresh evidence, not old scores.
- Preserve manual Stop at every stage. No forced completion or automatic retry loop.
- Do not store a personal rate across app restarts or export health samples in this scope.
- UI reports an experimental candidate, not a diagnosis or certified optimum.

## Required deterministic tests

Cover ideal input, constant IBI, phase lag, noise, ties, unequal coverage, invalid
IBI, missing/reordered/batched events, no callbacks, stale data, clock discontinuity,
rate bounds, cycle continuity, pause/resume, Stop in every state and source error.
Fixture results must not depend on the controller choosing the fixture's own cue.

## Acceptance and outstanding decisions

The gap analysis is complete; the approved first checkpoint adds a disconnected
simulation controller. No SDK integration, permission or installed APK changed.
Keep existing emulator-report edits separate; they have not been committed.

Checkpoint 1 implementation and tests require review before the next checkpoint.
The real-session protocol, actual measurements, installation, commit and publication
retain separate gates.

Exercise: why can a delayed batch of valid IBI still be unsuitable for assigning
each beat to the displayed inhalation/exhalation phase?
