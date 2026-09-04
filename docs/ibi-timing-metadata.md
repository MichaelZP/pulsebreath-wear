# IBI timing metadata checkpoint

Date: 2026-09-04. Follows the [timing audit](samsung-ibi-timing-audit.md).

## Changes

`SensorSample.timing` is optional to preserve existing fake-data and HRV callers.
The Samsung adapter now captures one elapsed-realtime receipt timestamp per callback
and retains the raw SDK timestamp, local callback sequence, point index/count and
the original IBI/status list counts. The mapper retains existing rejection markers.
The sequence is an in-memory counter, not a device identifier; empty callbacks can
produce gaps. No metadata is logged or persisted.

The SDK clock domain and beat anchor are still unknown. No timestamp subtraction
is interpreted as sensor latency. The change does not replace receipt-time HRV
windowing with measurement-time windowing, nor change RMSSD or coverage formulas.

`AdaptationTimingGate.assess` is a pure, conservative prerequisite for future
integration. It returns a blocking reason in every case; it has no eligible outcome.
Even fresh, ordered GOOD data is `UNKNOWN_BEAT_ALIGNMENT`. It is not connected to
the simulation controller or UI, and it does not claim to validate signal quality.

Checks include missing/invalid metadata, receipt age against an explicit current
elapsed time, backward clocks, point/callback ordering, repeated SDK timestamps,
list-length mismatch, rejected entries and source quality. Freshness is a caller-
supplied software limit, not a selected human-protocol threshold.

A GOOD empty later point in a multi-point callback is classified as an empty batch
companion, not confirmed sensor loss. This classification does not prove coverage.
Duplicate SDK timestamps are conservatively blocked, not declared a hardware fault.
Assess points serially, passing the immediately preceding metadata from the same
stream; reset that context at a new stream. Concurrent callback reordering is not
repaired. UI lifecycle and callback-generation isolation are unchanged in this step.

## Verification

Six new SDK-independent tests cover unknown alignment, age without arrivals,
metadata errors, ordering, empty batch companions, mismatches and rejection.
Both variants must compile and pass their unit suites before acceptance:

```powershell
.\gradlew.bat :app:testDemoDebugUnitTest :app:testSamsungDebugUnitTest :app:lintSamsungDebug --offline
```

No real sensor collection, APK installation, permission change or public release
is part of this checkpoint. Device diagnostics and empirical clock checks remain
pending separate approval. The installed application does not change.

Verified: BUILD SUCCESSFUL, 45 demo and 51 Samsung unit tests, zero failures/errors;
both Kotlin variants compiled. Samsung lint completed with zero errors and one
warning. `git diff --check` passed. These counts overlap in shared tests and do
not represent 96 distinct test cases. No on-device timing claim is made.

Proposed local commit: `feat: preserve Samsung IBI timing metadata`.
Do not include unrelated emulator-report edits. Do not commit without specific approval.
