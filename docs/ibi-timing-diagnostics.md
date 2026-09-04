# Session-local timing diagnostics

Date: 2026-09-04. Preparation only; no device measurements authorized or performed.

## Implementation

`TimingDiagnostics` is a constant-size immutable summary holding counters, two
min/max delta ranges and the immediately preceding timing envelope. It retains no
BPM or IBI values. It is updated serially on the Activity UI thread.

The Samsung sensor screen displays it **below existing Start/Stop controls**:
point count, observed callback-group transitions, multi-point groups, maximum
batch size, empty companions, missing metadata, ordering flags, within-SDK-clock
point deltas, within-receipt-clock callback deltas, last blocking reason and receipt
age. These are not sensor-latency measurements or proof of precise beat alignment.
Repeated/reordered callback groups may increase observed group counts; these are
diagnostic observations, not a deduplicated transport log.

Negative or overflowing deltas are omitted. Zero deltas are retained; ordering
flags remain separately visible. SDK and receipt clocks are never subtracted.
Missing metadata clears the previous comparison point. Empty callbacks have no
sample to display and may produce callback-sequence gaps.

While connecting/tracking, receipt age refreshes approximately every second even
without incoming samples. Stopping shows a retained summary rather than a live-age
claim. Start resets the summary; onStop stops the sensor as before. Activity
generation checks reject already queued UI callbacks after Stop or a new Start;
this is not a redesign of SDK listener lifecycle or a guarantee against all late
service callbacks. The panel does not keep the device awake independently.

No file logging, export, networking, automatic start, additional permission or HRV
formula change is added. The existing sensor screen still displays health values;
avoid including them in diagnostic screenshots or reports without explicit consent.
Adaptation remains disabled and this panel cannot select a breathing rate.

## Verification and handoff

Three new pure tests cover group counts, separate clock deltas, empty companions,
age without callbacks, backward time, duplicates, missing metadata and reset state.
Compilation does not substitute for visual watch verification or lifecycle tests.

```powershell
.\gradlew.bat :app:testDemoDebugUnitTest :app:testSamsungDebugUnitTest :app:lintSamsungDebug --offline
```

Before testing on a watch: request approval for APK update and a bounded real-sensor
diagnostic session. User starts/stops it explicitly. Report only aggregate timing
fields; no raw health stream. Verify scroll readability, live age, retained summary,
new-Start reset and leaving the screen. This foreground-only screen cannot verify
background batching: onStop shuts collection down. Do not alter that rule silently.

Proposed commit: `feat: add session-local IBI timing diagnostics`.
Commit, installation, real measurement and publication are separate approvals.

Verification result: BUILD SUCCESSFUL; 48 demo and 54 Samsung unit tests passed
(shared cases overlap), zero failures/errors. Samsung lint: zero errors, one warning
and one hint. `git diff --check` passed. APK installation and visual/lifecycle watch
verification have not been performed for this checkpoint.
