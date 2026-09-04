# Samsung IBI timing audit

Date: 2026-09-04. Code baseline: `9395fc4`. Read-only source and public API review;
no physical measurement, SDK modification, installation or physiological validation.

## Verified facts and unresolved assumptions

- Samsung `DataPoint.getTimestamp()` returns milliseconds. The reviewed method
  description does not specify the epoch/clock domain or the exact beat anchor.
  Do not equate it with `SystemClock.elapsedRealtime()` without further evidence.
- Samsung documents event batching, with IBI accumulated in the first data point
  and null IBI in later points when batched. Null IBI is not necessarily signal loss.
- The current adapter discards the SDK timestamp and callback grouping. It assigns
  receipt time inside the per-point loop. Multiple historical events may therefore
  appear nearly simultaneous. Their real age cannot be recovered from this model.
- `SamsungHeartRateReadingMapper` preserves rejection counts and continuity breaks,
  which must remain intact in any replacement ingestion path.

Sources reviewed:
[DataPoint](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/DataPoint.html),
[data specifications](https://developer.samsung.com/health/sensor/guide/data-specifications.html).

## Consequences for existing diagnostics

1. `HrvAnalyzer` selects its 60-second window by receipt timestamp, not established
   measurement time. A delayed batch can bring older measurements into that window.
2. Its coverage is the fraction of sample events containing valid IBI, not elapsed
   time coverage or the fraction of expected beats. For example, one IBI-bearing
   point plus nine empty batch points gives 10% event coverage even if those entries
   reflect the SDK's documented packaging. This is a synthetic example, not an
   explanation proven for the user's earlier measurements.
3. The window ends at the latest sample; without new arrivals it does not age out
   by wall-clock progress. A live adaptation gate needs an independent current-time
   freshness check. Retained post-Stop summaries have different semantics.
4. A GOOD event with an empty IBI list does not reset `previousValidIbi` in the
   current RMSSD loop unless it contains an explicit break marker. That may be
   harmless batching or an unobserved gap; event contents alone do not prove continuity.
5. Assigning every IBI in an event to one cue phase is insufficient evidence for
   real-time respiratory phase alignment. The existing synthetic correlation
   must not be used as a validated physiological calibration score.

## Next implementation boundary

Introduce an SDK-independent timing envelope and tests before replacing any HRV
formula. Preserve receipt elapsed time, the raw SDK timestamp with clock domain
explicitly unknown, callback sequence and point index/count, and raw list counts.
Keep rejected-entry markers and provenance. Do not call timestamp subtraction
sensor latency until both clocks and the timestamp anchor have been established.

The timing gate should explicitly reject unknown beat alignment for real adaptation;
detect duplicate/out-of-order metadata and stale callbacks; and keep grouping-only
empty IBI distinct from confirmed loss. No interpolation or guessed beat timestamps.
Tests must cover delayed batches, empty companion points, duplicate timestamps,
ordering faults, mismatched lists, rejected entries and no new callbacks.

Initial diagnostics can use only bounded in-memory aggregate counts and timestamp
delta ranges, without BPM/IBI streams, persistent device IDs or automatic exports.
Even such diagnostics require explicit approval before a real sensor test.
Empirical timestamp agreement is useful evidence, not proof of an exact beat anchor.

## Outcome

The current sensor path is not ready to drive adaptation. This does not mean BPM
display is broken or that every retained IBI is invalid. No code or thresholds were
changed in this audit; the installed trainer remains fixed-rate.
The next scoped implementation is timing metadata plus a conservative eligibility
gate, not automatic resonance selection. A separate real-session protocol remains required.
