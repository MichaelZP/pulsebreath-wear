# Milestone 8 follow-up — Privacy draft

Date: 2026-09-04. Base: `d33642d`. Branch: `feature/milestone-8-privacy-draft`.

Documentation-only checkpoint: PRIVACY.md plus links from README.md and SECURITY.md.
Provider identity and LinkedIn contact are the user's approved details. No email, retention
deadline, legal basis, private reporting channel or Samsung approval has been invented.

## Source evidence reviewed

- `app/src/main/AndroidManifest.xml`: backup declarations, no INTERNET permission.
- `app/src/samsung/AndroidManifest.xml`: heart-rate permissions, separate real-sensor screen.
- `sensor/SensorModels.kt`: measured/simulated data fields and source labels.
- `sensor/SamsungSensorDataSource.kt`: heart-rate/IBI fields, capability check, stop/disconnect.
- `presentation/SamsungSensorActivity.kt`: explicit permission/start actions; onStop cleanup;
  60-second incoming-sample window; last-IBI state; retained results after Stop;
  state cleared on a new session, no persistence restoration.
- `signal/HrvAnalyzer.kt`: derived quality indicators and guarded displayed RMSSD.
- Simulated diagnostics use in-memory artificial samples, not physical sensor measurements.

The provider's source is distinct from the proprietary SDK, Samsung services, device OS and
user-initiated support messages. Their behavior is not inferred from source-manifest permissions.
The public document does not reproduce privately supplied Samsung contract terms.

## Verification and limitations

Review Markdown links, approved contact spelling/URL, diff scope and whitespace before commit.
No application code, permissions, runtime UI, dependencies or CI configuration changed.
Android compilation and watch tests need not be repeated for this documentation-only change.
No legal compliance certification, in-app consent flow or secure deletion feature was implemented.
Provider contact alone does not complete privacy and security release gates.

## Exercise and acceptance

Explain the difference between stopping collection, keeping the final result in memory and
deleting a durable record. Why is a 60-second analysis window not a deletion guarantee?

Proposed local commit: `docs: draft privacy information for local prototype`.
Wait for the user's approval; do not publish the draft or application automatically.
