# Privacy information — draft for local prototype review

Reviewed: 2026-09-04, against source commit `d33642d`.
This draft describes the current prototype. It is not a completed distribution privacy
policy, legal compliance assessment or permission to publish the application.

## Provider and contact

PulseBreath Wear is provided and maintained by **Michał Przybylski**.
Initial, non-sensitive contact: [LinkedIn](https://www.linkedin.com/in/micha%C5%82-przybylski-323a4948/).
No project email is designated. Messaging availability is unverified; an accessible route
for privacy requests and confidential security reports remains to be established.
Do not post health readings or sensitive reports in public comments. LinkedIn is an external
service, not an app account or an automatic destination for measurements.

## Data used by the prototype

| Feature | Data | Purpose |
| --- | --- | --- |
| Sensor-free breathing trainer | Session timing, breathing phase and UI state | Guide a breathing session without sensor readings |
| Simulated diagnostics | Artificial BPM, IBI, timing and signal-quality scenarios | Demonstrate and test calculations; these are not measurements of the user |
| Samsung real-sensor screen | BPM, IBI intervals, sensor validity/status information and local monotonic timestamps | Display current readings, signal-quality indicators and experimental HRV metrics |

The source also derives valid/rejected IBI counts, event coverage and RMSSD. Results may be
unavailable when quality or continuity is insufficient. Fixed-rate alignment diagnostics use
simulated data; no clinical accuracy or automatic resonance calibration is established.
The current Samsung adapter does not request ECG, SpO2, skin temperature or location data.
Importing a permission constant from Android Health Connect does not mean the app reads
historical Health Connect records: the current adapter uses Samsung Health Sensor Service.

## Starting and stopping access

The Samsung screen presents a short explanation, offers an Android heart-rate permission
request and requires a separate **Start sensor** action. Without permission, this screen cannot
start a real reading. The code selects BODY_SENSORS on older supported Android versions and
READ_HEART_RATE on API 36 and later. The demo flavor does not request heart-rate access.

**Stop sensor** releases the tracker and service connection. The screen's `onStop` lifecycle
callback also stops tracking. Users can revoke access using the device's app-permission controls.
This describes the source implementation, not a guarantee about every device/OS lifecycle case.
An Android permission grant is not asserted to satisfy every legal or contractual consent duty.

## Storage, retention and deletion limits

The current app source holds measurements in memory rather than writing a measurement database,
file or account history. During streaming, the Samsung screen prunes its analysis list using a
60-second window relative to the latest arriving sample. It also retains the last sample and
last valid IBI display. **This is not a 60-second deletion timer.**

Stopping leaves the final readings and analysis available while the activity state remains alive.
Starting a new sensor session clears the previous in-memory sample list and displayed values
before beginning a new reading. They are not deliberately restored from persistent storage after
process death. No explicit secure-memory erasure or dedicated erase-results button is implemented;
backgrounding or pressing Stop must not be described as guaranteed deletion.

The application declares backups disabled. This does not erase screenshots, device diagnostics,
operating-system records, information held by Samsung services, or copies a user creates elsewhere.

## Networking, third parties and support

No application feature for measurement upload, export, accounts, advertising or analytics was
found in the reviewed source. The app's source manifests do not request INTERNET permission.
This is deliberately narrower than claiming that nothing on the watch communicates externally.

The Samsung flavor uses the proprietary Samsung Health Sensor SDK and Health Sensor Service.
Their independent data handling, telemetry, retention and applicable terms have not been fully
audited here. The demo flavor has no Samsung AAR dependency at runtime.
Samsung SDK redistribution and production registration remain separate release gates.

The app does not automatically send measurements to the provider or to LinkedIn. If a user
chooses to contact the provider, the content of that communication is a separate data flow.
Do not send measurements, device identifiers, pairing codes, credentials or unredacted screenshots.
Use synthetic examples. Support-message access, retention and deletion procedures have not yet
been finalized; this draft does not claim a particular retention period for such messages.

## Before public distribution

The following must be resolved before this draft can be presented as a complete privacy policy:

- Confirm applicable jurisdiction, provider/controller obligations, legal bases, health-data
  conditions, required contact details and a practical privacy-rights request procedure.
- Review Samsung SDK/service practices and the applicability of the privately supplied agreement;
  do not reproduce confidential contract text in this public-facing document.
- Finalize support-message handling, recipient/transfer information, retention, deletion and
  incident-response procedures. Do not infer these from the lack of measurement storage.
- Review the intended audience and any research-participant or child-related requirements before
  involving other people. No participant-study consent or ethics clearance is claimed.
- Finalize necessary pre-use disclosures and their in-app presentation, provide access to the
  approved policy and review actual release artifacts. This Markdown draft is not displayed in
  the current app and does not by itself implement these requirements.
- For a future store release, publish an approved accessible policy and complete the store's
  data declarations based on the actual app and SDK behavior, not only this source review.

References checked for release planning: [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)
and [Samsung app verification](https://developer.samsung.com/health/sensor/guide/app-verification.html).
These links are not statements that the prototype meets those requirements.

Any future storage, export, cloud, analytics or background-measurement feature requires a new
privacy review and an update to this document before enabling that feature for users.
