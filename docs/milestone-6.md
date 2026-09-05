# Milestone 6 - Biofeedback and resonance-calibration boundary

Status: implemented and automatically verified for simulated fixed-rate data; awaiting user review and acceptance

## Deliverables

- `signal/BreathingAlignmentAnalyzer.kt`: a pure `alignment_v1` calculation;
- `docs/metrics.md`: input contract, exact template, formula, range, availability
  rules, and safety/validation limits;
- unit tests for ideal, inverted (phase-shifted), noisy, missing-data,
  non-respiratory, and invalid-IBI inputs;
- debug-only simulated diagnostics displaying either the alignment score or an
  explicit reason that it is unavailable.

## What the debug display proves

The debug-only display exercises the same pure analyzer with the visibly
simulated sensor source. It never claims to be a reading from the Samsung
sensor, does not store the score, and does not select a breathing rate.

## Calibration boundary

This milestone intentionally does not start an automatic multi-rate calibration
or label any rate as a personal resonance frequency. A future experimental
protocol must be separately approved before collecting real-session trials. It
must predeclare the rates, equal trial durations, rest/cancellation behavior,
quality gate, comparison rule, privacy handling, and the fact that its result is
not medical advice.

## Suggested manual exercise

In the debug-only `PulseBreath Diagnostics` app, allow the simulated stream to
run for at least ten good IBI events. Observe that the line changes from `za mało
danych` to a numeric `Zgodność oddechu` only when the quality gate is met. When
the scripted motion/loss/recovery interval occurs, the score becomes unavailable
again instead of being carried forward or estimated.

## Deferred work

- real-sensor-to-breathing-session correlation is not wired into the user-facing
  trainer yet;
- no rate sweep, personal resonance-frequency claim, persistence, export,
  baseline, clinical validation, or Polar H10 comparison is included;
- a real-session validation protocol is required before interpreting the score
  beyond deterministic software tests.
