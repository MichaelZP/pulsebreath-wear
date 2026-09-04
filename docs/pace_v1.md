# pace_v1.1: experimental receipt-anchored IBI periodicity estimate

This pure Kotlin estimator suggests a breathing cue period from estimated IBI end
times. It is not a respiration measurement or a validated resonance-frequency
search. The session owner must collect 35 seconds before calling it; the helper
does not run a timer. The Samsung shared owner now supplies that timer and maps the result to the cue;
see [Samsung guided session](samsung-guided-session.md). The estimator itself adds
no sensor, network, persistence or logging.

## Input and continuity

Input is ordered `TimedIbi` records, with milliseconds on one monotonic session
clock. Only the final 35,000 ms relative to the latest placed input are eligible.
`acceptedIbiCount` counts positive accepted, placed intervals in that window;
`analyzedIbiCount` reports the records used by the selected mode. Unplaced,
rejected and nonpositive entries split continuous segments, as do `breakBefore`
markers. Non-increasing or negative placed timestamps also split continuous
segments; they are never sorted, repaired, or used to invent a beat timeline.

For Samsung, `SamsungHeartRateReadingMapper` places only positive
`IBI_STATUS_NORMAL` values into `SensorSample.ibiMillis`. pace_v1.1 accepts such
already-mapped intervals for receipt placement even when the accompanying BPM
quality status is not `GOOD`. BPM-only callbacks do not create a pace break.
Explicit IBI reject/break metadata still creates a break. This is pace-only
acceptance: `quality_v1.1`, HRV and alignment retain their existing GOOD/ADEQUATE
quality gates and are not evidence supplied to the pace estimator.

An end-to-end time difference that differs from the later IBI by more than 250 ms
also splits the segment. This is an explicit conservative delivery-jitter heuristic,
not a claim that smaller discrepancies validate beat timestamps. The caller must
carry trailing/empty-event break metadata into the next batch (or a rejected marker);
simply concatenating `EstimatedIbiBatch.intervals` loses that information. Null
timestamps must remain in the input as break markers. No IBI is invented.

At least 12 eligible intervals are required in every mode. `CONTINUOUS` is always
preferred: select the longest continuous segment containing at least 12 intervals
(first on a tie), requiring at least 24 seconds between its first and last end.
The existing timing-gap heuristic remains a continuous-segment split.

If the continuous path cannot produce an estimate, `POOLED` may use all accepted,
placed records in their original delivery order across breaks. It still requires
at least 12 records and a raw last-minus-first span of at least 20 seconds. It does
not sort, interpolate, fill gaps, claim continuity, or relax raw-sample quality.
`estimateMode` reports `CONTINUOUS`, `POOLED`, or `FALLBACK`. Pooled evidence is a
receipt-anchored heuristic for intermittent wrist delivery, not proof of measured
beat timing or respiration.

## Detrending and irregular-time autocorrelation

For the records selected by either mode, let t be milliseconds relative to the
first delivered end and y be IBI milliseconds. Least-squares linear detrending uses
`b = sum((t-mean(t))*(y-mean(y))) / sum((t-mean(t))^2)` and residual
`r = y-mean(y)-b*(t-mean(t))`. Mean squared residual below 1 ms^2 yields fallback.
The original records are unchanged; no resampling, interpolation or filtering
is performed beyond this explicitly documented detrending for estimation.

Evaluate lags 6,000 through 16,000 ms every 250 ms. For each lag, use every pair
of actual residuals whose timestamp difference is within +/-400 ms of that lag.
Compute Pearson correlation of the two residual vectors, centering each vector
separately. Require at least eight pairs and segment span >= twice the lag.
Variance sums <= 1e-9 or nonfinite results make that bin unavailable.

Choose the strongest interior local maximum with available neighbors (>= left,
> right; first on a tie). Require correlation >=0.6 and a difference of >=0.3
from the minimum available lag correlation. A boundary-only peak is unavailable.
These bin widths, counts and thresholds are engineering heuristics, not clinically
validated cutoffs. A pair may contribute to neighboring lag bins, so bins are not
independent evidence. Harmonics, noise and delivery jitter can bias the result.

Clamp the selected lag to 8,000..14,000 ms. Inhale is `round(cycle*0.45)` and
exhale is the remainder, preserving the 45:55 default ratio and exact cycle sum.
An estimate outside the output range is clamped, not reported as the detected
period itself. This is a one-shot pace estimate, not continuous adaptation.

## Explicit fallback and integration

Fallback always returns cycle 10,000 ms, inhale 4,500 ms, exhale 5,500 ms and
`usedFallback=true`. Reasons distinguish too few intervals, insufficient usable
time/continuous data, and no clear peak. Non-increasing receipt ends no longer
cause a whole-window `INVALID_TIME_ORDER` fallback; they are explicit segment
breaks. `peakCorrelation` is nullable;
it contains the candidate local peak when one exists, even if rejected as weak.

The caller must display fallback honestly and map the durations into
`BreathingSessionConfig` only after calibration. Existing `quality_v1.1` and
`alignment_v1` are unchanged. This estimator's count is not a replacement for
raw-event quality coverage. Receipt anchoring remains an unvalidated timing
estimate. Wrist PPG intervals are not established ECG NN intervals, calibration
does not measure chest respiration, and an estimate is never a validated
resonance frequency. Synthetic period recovery and the live-session wiring do
not establish live breathing accuracy.

## Verification

```powershell
.\gradlew.bat :app:testDemoDebugUnitTest :app:lintDemoDebug --offline
```

Tests cover continuous 10-second recovery with linear drift, output clamping,
the >=12 gate, pooled recovery across explicit breaks and delivery gaps, time-order
glitches without sorting, nonperiodic noise and the rolling-window boundary.
Pooled mode keeps the same ACF peak thresholds as continuous mode. Unit tests do
not establish live Samsung accuracy; physical-watch validation remains required.
