# PulseBreath Wear

![PulseBreath heart and lungs](docs/assets/pulsebreath-logo.svg)

Standalone Wear OS breathing trainer and experimental sensor diagnostics.
Educational wellness/research prototype, not a medical or diagnostic device.
No public release or accuracy certification is implied by the internal version `1.0`.

## Current capabilities and limits

- Sensor-free guided breathing with pause, resume, stop, animation and haptics.
- Deterministic simulated BPM/IBI scenarios and debug diagnostics.
- A separate Samsung guided session: it calibrates from real IBI, then runs the
  trainer with that pace or an explicit 4.5 s inhale / 5.5 s exhale fallback.
- The Samsung session reports real BPM/IBI, quality gating and basic HRV during
  its guided run.
- Rejected IBI boundaries are preserved; insufficient windows display RMSSD as unavailable.
- `alignment_v1` on live IBI is experimental and wellness-only; it is neither a
  clinical measure nor validated resonance or measured respiration.
- `pace_v1.1` estimates IBI periodicity, not chest respiration. Wrist PPG IBI is
  not established ECG NN data; weak or discontinuous data uses the explicit
  fallback and shows its reason.
- **Blunt quality note:** consumer wrist "HRV/IBI" is usually PPG/PRV, not ECG.
  Vendors rarely state that gap in marketing. See
  [docs/wrist-ppg-ibi-quality.md](docs/wrist-ppg-ibi-quality.md).
- The sensor-free `MainActivity` trainer remains a separate mode.
- No cloud, account, raw sensor persistence or export feature. Samsung stores only bounded,
  derived local guided-session summaries (up to 200), with per-record deletion and clear-all.
  Accuracy and Polar H10 comparison remain unvalidated.

## Build locally

Install Android Studio, JDK 25, Android SDK platform 37 and Build Tools 36.0.0.
Set `ANDROID_HOME` to your SDK directory, or configure untracked `local.properties`
with `sdk.dir`. The checked-in wrapper uses Gradle 9.6.0; dependency versions are
in `gradle/libs.versions.toml`. First use requires network access for dependencies.
The daemon uses JDK 25; Java source/target compatibility remains 11.

PowerShell, from the repository root:

```powershell
.\gradlew.bat --no-daemon testDemoDebugUnitTest lintDemoDebug assembleDemoDebug
```

Linux/macOS: replace `.\gradlew.bat` with `bash ./gradlew`.
The `demoDebug` variant needs no Samsung SDK or signing secrets.
APK: `app/build/outputs/apk/demo/debug/app-demo-debug.apk`.

## Install and open

Select `demoDebug` in Android Studio Build Variants, start a Wear OS emulator,
select it as the deployment target and run `app`. Alternatively:

```powershell
adb devices
adb -s emulator-5554 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
adb -s emulator-5554 shell am start -n dev.prylski.breath.demo/pl.pulsebreath.wear.presentation.MainActivity
```

Replace the example serial with the intended device from `adb devices`.
From the watch application launcher, open **PulseBreath Wear**; debug builds also
provide **PulseBreath Diagnostics**, which uses simulated data.

## Optional Samsung setup

Obtain Samsung Health Sensor SDK **1.4.1** directly from
[Samsung](https://developer.samsung.com/health/sensor/overview.html), accept its
terms yourself, and place `samsung-health-sensor-api-1.4.1.aar` in `app/libs/`.
This proprietary binary is not supplied or relicensed by this project.
Use a compatible physical watch, grant heart-rate permission, and follow the
[local developer-mode guide](docs/samsung-health-sensor-developer-mode.md).

```powershell
.\gradlew.bat --no-daemon testSamsungDebugUnitTest lintSamsungDebug assembleSamsungDebug
adb -s WATCH_SERIAL install --no-streaming -r app/build/outputs/apk/samsung/debug/app-samsung-debug.apk
adb -s WATCH_SERIAL shell am start -n dev.prylski.breath/pl.pulsebreath.wear.presentation.SamsungSensorActivity
```

`WATCH_SERIAL` is a placeholder: do not paste it literally. Open **PulseBreath Sensor**
from the watch launcher. Developer mode is for local testing, not distribution approval;
review [Samsung app verification](https://developer.samsung.com/health/sensor/guide/app-verification.html)
before distribution. Emulator results do not verify Samsung sensor support.

## Project map and verification

`app/src/main` contains the trainer and pure signal/session logic;
`app/src/debug` contains simulated diagnostics; `app/src/samsung` contains the SDK adapter.
`app/src/test` and `app/src/testSamsung` contain unit tests; `app/src/androidTest`
contains device UI tests. CSV test fixtures are synthetic.

The [demo workflow](.github/workflows/demo-ci.yml) runs unit tests, lint and APK compilation
without the Samsung AAR. It does not run watch UI tests, publish artifacts, sign releases
or validate physiological accuracy. Its first actual GitHub run requires a separately
approved repository publication.

Read [working agreements](AGENTS.md), [project plan](PROJECT_PLAN.md),
[metric definitions](docs/metrics.md), [validation protocol](docs/validation_protocol.md),
[Samsung guided-session limits](docs/samsung-guided-session.md), and
[pace_v1.1 limits](docs/pace_v1.md),
[security guidance](SECURITY.md), and [release checklist](docs/release-checklist.md).
The [draft privacy information](PRIVACY.md) describes the current local data flow and
remaining distribution gates; it is not yet a finalized or in-app privacy policy.

## Provider and contact

Provider and maintainer: **Michał Przybylski**.
General contact: [LinkedIn](https://www.linkedin.com/in/micha%C5%82-przybylski-323a4948/).
This profile was supplied by the maintainer; message availability has not been verified.
No project email address is designated. LinkedIn is not a designated confidential
vulnerability-reporting channel. Do not send health data, credentials or sensitive
security details through public posts or comments; see [security guidance](SECURITY.md).

## License

Original project source is licensed under [Apache-2.0](LICENSE).
Third-party libraries, SDKs and artwork retain their own terms. No Samsung affiliation,
trademark clearance or right to redistribute the Samsung SDK is claimed.
See [third-party licenses](THIRD_PARTY_LICENSES.md) for the resolved dependency list,
retained upstream notices, tooling terms and pending Samsung distribution clearance.
The current logo is original heart-and-lungs artwork; historical Android robot credits
are retained in [NOTICE](NOTICE).
