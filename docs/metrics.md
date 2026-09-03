# PulseBreath metrics specification

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
