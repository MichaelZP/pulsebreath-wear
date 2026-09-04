# pace_v1: experimental IBI periodicity estimate

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
`analyzedIbiCount` reports the selected continuous segment. Unplaced, rejected and
nonpositive entries split segments, as do `breakBefore` markers. Non-increasing
or negative placed timestamps reject the estimate rather than being sorted.

An end-to-end time difference that differs from the later IBI by more than 250 ms
also splits the segment. This is an explicit conservative delivery-jitter heuristic,
not a claim that smaller discrepancies validate beat timestamps. The caller must
carry trailing/empty-event break metadata into the next batch (or a rejected marker);
simply concatenating `EstimatedIbiBatch.intervals` loses that information. Null
timestamps must remain in the input as break markers. No IBI is invented.

At least 12 eligible intervals are required. Select the longest segment containing
at least 12 intervals (first on a tie), requiring at least 24 seconds between its
first and last end. Other segments are not combined. These gates deliberately
allow fallback even when the total count is high.

## Detrending and irregular-time autocorrelation

For the selected segment, let t be milliseconds relative to its first end and y
be IBI milliseconds. Least-squares linear detrending uses
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
`usedFallback=true`. Reasons distinguish too few intervals, a short continuous
segment, invalid time ordering and no clear peak. `peakCorrelation` is nullable;
it contains the candidate local peak when one exists, even if rejected as weak.

The caller must display fallback honestly and map the durations into
`BreathingSessionConfig` only after calibration. Existing `quality_v1.1` and
`alignment_v1` are unchanged. This estimator's count is not a replacement for
raw-event quality coverage. Receipt anchoring remains an unvalidated timing
estimate. Wrist PPG intervals are not established ECG NN intervals, and synthetic
period recovery does not establish live breathing accuracy.

## Verification

```powershell
.\gradlew.bat :app:testDemoDebugUnitTest :app:lintDemoDebug --offline
```

Tests cover a synthetic 10-second oscillation with linear drift, output clamping,
fallback, discontinuities, timing disorder, delivery gaps, nonperiodic noise and
the rolling-window boundary. On 2026-09-04 compilation and all 67 demo unit tests
passed (nine new pace tests). Lint completed with zero errors and two existing
warnings (`ModifierParameter`, `WearRecents`). No APK assembly, emulator execution
or watch measurement was performed at that checkpoint. The combined session is
now implemented; see the integration document above for its verification.
Hardware validation remains pending.
