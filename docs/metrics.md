# PulseBreath metrics specification

## quality_v1.1 continuity correction (Milestone 7)

This revision preserves source rejection boundaries without changing the 60-second
window, 10-valid-IBI minimum or 80% event-coverage threshold from quality_v1.
The Samsung mapper retains accepted IBI and records break indices between them.
Index zero breaks continuity with the previous event; index equal to the retained
list size breaks continuity with the following event. A rejected-only event also
breaks continuity. Truly empty events (both SDK lists empty) retain the existing
behavior and do not imply a rejection.

Non-normal status, nonpositive value, missing status or missing value generates
a rejection boundary. `rejectedIbiCount` counts these source entries, including
unmatched list entries; it is separate from `invalidIbiCount`, which counts
nonpositive values supplied directly to the analyzer. Rejected values never
enter the numeric IBI list. Consecutive rejection boundaries may collapse to
one index but their count is retained. No synthetic numeric interval is inserted.

RMSSD arithmetic is unchanged, but differences never cross these boundaries.
The UI now uses `displayRmssdMillis`, absent for INSUFFICIENT windows. The raw
calculation remains internal for deterministic tests. ADEQUATE is still a
provisional completeness label, not a validation of physiological accuracy.
No missing-beat inference, de-duplication or beat-timestamp reconstruction was
added; events still use callback receipt time. These limitations remain open.

Synthetic regression example: accepted `[1000, 900]`, rejected entry, accepted
`[1400, 1300]` gives two differences of -100 ms and RMSSD 100 ms. The 500 ms
difference across the rejection must not be included. This example is fabricated.

Algorithm version: `quality_v1`  
Status: implemented and verified with deterministic fixtures; not clinically validated

## Scope and safety

These metrics describe data completeness and short-term variation in the locally observed inter-beat intervals (IBI). They are wellness and research indicators only. They do not diagnose, screen for, or rule out any medical condition.

The calculations never interpolate, resample, smooth, or replace a missing IBI. A quality result is shown separately from every future biofeedback result.

## Analysis window

The analyzer receives timestamped `SensorSample` values in monotonic milliseconds. Its window is the closed interval:

```text
[analysisEndMillis - 60_000, analysisEndMillis]
```

`analysisEndMillis` is the latest sample timestamp in the supplied sequence. Samples outside that window are excluded. The window length is fixed at 60 seconds in `quality_v1`; a shorter recording is allowed and is reported through coverage and counts rather than being padded.

## Terminology

- **sample event**: one `SensorSample` received from the selected source;
- **good sample event**: a sample whose explicit source quality is `GOOD`;
- **IBI event**: a sample event with at least one positive IBI;
- **valid IBI**: a positive IBI from a good sample event;
- **break**: `MOTION_ARTIFACT`, `SIGNAL_LOST`, or `RECOVERING`. A break prevents an RMSSD difference from crossing that point.

An IBI that is zero or negative is invalid input. It is counted as invalid and creates a break; it is not repaired or discarded silently.

## Metrics

### Mean BPM

Only positive, finite BPM values from good sample events contribute:

```text
meanBpm = sum(valid BPM values) / count(valid BPM values)
```

Unit: beats per minute (BPM).  
Result: absent when no valid BPM value is available.

### Valid IBI count

```text
validIbiCount = count(positive IBI values from good sample events)
```

Unit: intervals. This is a count, not a duration.

### IBI-event coverage

```text
ibiEventCoverage = 100 * count(good sample events containing at least one positive IBI)
                         / count(all sample events)
```

Unit: percent.  
Result: `0%` when the window has no sample event.

This is event coverage, not an estimate of missing beats. Samsung can batch several IBI values in one event and emit no IBI in a later event, so a ratio based on individual IBI values would not represent an expected beat count.

### RMSSD

For each uninterrupted segment of positive valid IBI values, calculate successive differences:

```text
d_i = IBI_(i+1) - IBI_i
RMSSD = sqrt(sum(d_i^2) / number_of_differences)
```

All segments contribute their internal differences. No difference is calculated between two segments separated by a break. At least two valid IBI values in one uninterrupted segment are required; otherwise RMSSD is absent.

Unit: milliseconds (ms).

This follows the conventional time-domain definition of RMSSD over successive normal-to-normal intervals, but PulseBreath does not claim that every supplied watch IBI is a clinical NN interval. [Task Force standard](https://pubmed.ncbi.nlm.nih.gov/8598068/), [ACC/AHA/HRS definition](https://www.jacc.org/doi/10.1016/j.jacc.2006.09.020)

## Quality label

`quality_v1` uses two visible thresholds:

| Label | Rule |
| --- | --- |
| `INSUFFICIENT` | fewer than 10 valid IBI values, or IBI-event coverage below 80% |
| `ADEQUATE` | at least 10 valid IBI values and IBI-event coverage at least 80% |

The thresholds are usability guards for this app, not medical limits. They are deliberately documented as provisional and must be revisited during the validation milestone.

## Worked example

For one uninterrupted sequence `[1000, 900, 1100]` ms:

```text
differences = [-100, 200] ms
RMSSD = sqrt((10000 + 40000) / 2) = 158.113883 ms
```

For events `GOOD([1000, 900])`, `MOTION_ARTIFACT([])`, `GOOD([1100])`, no RMSSD difference is calculated between `900` and `1100` because the motion event is an explicit break.

## Validation status and limits

- Inputs are source-normalized, timestamped watch samples; no ECG validation has been performed.
- The app does not infer beat normality beyond the explicit source status and positivity rule above.
- The 60-second window, 10-IBI minimum, and 80% threshold are product decisions for `quality_v1`, not normative values.
- No frequency-domain metric, clinical baseline, medical alert, or biofeedback score is calculated in this milestone.
- Polar H10 comparison is deferred to the validation milestone and requires a written protocol before collecting any data.

---

# PulseBreath breathing-alignment specification

Algorithm version: `alignment_v1`
Status: implemented for deterministic simulated data only; not clinically validated

## Scope and safety

`alignment_v1` is a short-window signal-consistency indicator. It compares the
observed IBI pattern with the phase and progress of the app's *fixed-rate guided
breathing cue*. It is not a diagnostic, a measure of autonomic health, a
measurement of baroreflex sensitivity, or a determination of an individual's
resonance frequency.

The score is deliberately unavailable instead of estimated when the IBI window
does not satisfy `quality_v1`, contains an invalid IBI, or lacks variation. No
IBI is interpolated, resampled, filtered, or replaced. The source and
`quality_v1` result remain separate from the alignment result.

## Input and fixed-rate template

Each `AlignmentObservation` combines one timestamped `SensorSample` with the
guided `BreathingPhase` and `phaseProgress` that were visible at that timestamp.
Only positive IBI values from `GOOD` source events are paired with the template.
The same closed 60-second interval used by `quality_v1` is applied.

For each retained IBI, the dimensionless template value `x` is:

```text
x =  1 - 2 * phaseProgress    during INHALE
x = -1 + 2 * phaseProgress    during EXHALE
```

Thus `x` describes the simulated IBI pattern used by this app: longer IBI near
the beginning of inhalation and the end of exhalation, shorter IBI near the
inhalation/exhalation transition. It is a software reference, not a claim about
the timing of every person's physiology.

## Formula and range

Let `x_i` be the template value and `y_i` the matching positive IBI in
milliseconds. `alignment_v1` is their Pearson correlation:

```text
alignment = sum((x_i - mean(x)) * (y_i - mean(y)))
            / sqrt(sum((x_i - mean(x))^2) * sum((y_i - mean(y))^2))
```

Range: `[-1, 1]`.

- `+1` means the retained IBI values have an exactly proportional pattern to
  the fixed-rate template;
- `0` means no linear relationship in the retained window;
- `-1` means an exactly inverted pattern.

The application does not convert this continuous value into a health category,
achievement, recommendation, or target. A phase delay, sensor latency, posture,
movement, cue-following error, and a short window can all change it.

## Availability rules

`alignment_v1` is `AVAILABLE` only when all of the following are true:

1. `quality_v1` is `ADEQUATE` (at least 10 valid IBI values and at least 80%
   IBI-event coverage);
2. `quality_v1` counted no zero or negative IBI values in the window;
3. the retained template and retained IBI values both have non-zero variation.

Otherwise the result is absent with one of these explicit states:

- `INSUFFICIENT_QUALITY`: the IBI window does not meet the first two rules;
- `INSUFFICIENT_PHASE_VARIATION`: all retained template values are equal;
- `NO_IBI_VARIATION`: all retained IBI values are equal.

## Calibration boundary

The fixed-rate score is a prerequisite for an experimental multi-rate protocol;
it is not itself a calibration. No rate is selected, persisted, or presented as
an individual's resonance frequency in this milestone. Any future calibration
must compare predeclared equal-duration trials, report the raw score and data
quality of every trial, allow cancellation, and be validated against a written
protocol before it is used with real data.

## Validation status and limits

- Unit tests cover ideal, inverted (phase-shifted), noisy, missing-data, and
  non-respiratory inputs.
- The simulated input proves only arithmetic and state handling. It does not
  validate the formula against a person, an ECG, or a chest-respiration signal.
- The watch may batch IBI values and its timestamps may not identify the exact
  beat phase; this version attaches an event's IBI values to the visible cue at
  that event timestamp.
- Evidence about slow paced breathing and HRV biofeedback is method-dependent;
  individual resonance frequency cannot be inferred from this score alone.
  See [HRVB methods review](https://pubmed.ncbi.nlm.nih.gov/36917418/) and
  [resonance-frequency stability study](https://pubmed.ncbi.nlm.nih.gov/33863966/).
