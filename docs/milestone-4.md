# Milestone 4 - Samsung Health Sensor SDK

Status: implemented and verified on a physical Galaxy Watch

## Scope

This milestone adds an opt-in Samsung Health Sensor SDK path for continuous heart-rate and inter-beat interval (IBI) readings. The existing deterministic simulator remains available without the proprietary SDK.

The feature is wellness and research software. It is not intended for diagnosis or treatment.

## Final application ID

The confirmed application ID is:

```text
dev.prylski.breath
```

The `demo` flavor adds `.demo`, so its installed package is `dev.prylski.breath.demo`. The `samsung` flavor retains the final application ID `dev.prylski.breath` because Samsung app verification later binds the package name and signing certificate SHA-256. This application ID change does not rename the Kotlin packages or Android namespace; uninstall the old `pl.pulsebreath.wear` and `pl.pulsebreath.wear.demo` installs before sideloading these IDs.

## Local SDK setup

Download Samsung Health Sensor SDK 1.4.1 from the official Samsung Developer portal. Keep the proprietary library out of Git and place it at:

```text
app/libs/samsung-health-sensor-api-1.4.1.aar
```

The exact path is excluded in `.gitignore`. Do not commit, redistribute, or upload the AAR to CI.

## Build separation

The project has two product flavors:

- `demo`: simulator and breathing trainer; does not resolve the Samsung AAR;
- `samsung`: includes the locally supplied AAR and the real-sensor screen.

SDK-independent commands suitable for CI:

```powershell
.\gradlew.bat testDemoDebugUnitTest assembleDemoDebug assembleDemoRelease lintDemoDebug
```

Local Samsung commands requiring the AAR:

```powershell
.\gradlew.bat testSamsungDebugUnitTest assembleSamsungDebug lintSamsungDebug
```

The Samsung SDK does not support the emulator. Emulator results apply only to the `demo` flavor.

## Permission behavior

The Samsung flavor declares both permissions needed across supported platform versions:

- Wear OS based on Android 15/API 35 or earlier: `android.permission.BODY_SENSORS`;
- Wear OS based on Android 16/API 36 or later: `android.permission.health.READ_HEART_RATE`.

Only the permission applicable to the running watch is requested. Before the system prompt, the real-sensor screen explains that access is used for the current local BPM/IBI measurement and that this screen does not store or share readings.

## Data and status mapping

`SamsungSensorDataSource` requests `HEART_RATE_CONTINUOUS` only after permission has been granted and after the capability API confirms support.

For every received heart-rate data point:

- BPM is accepted only when `HEART_RATE_STATUS == 1`;
- an IBI value is accepted only when the corresponding `IBI_STATUS_LIST` value is `0` and the IBI is positive;
- motion and weak-signal heart-rate statuses are exposed as `MOTION_ARTIFACT`;
- other non-success statuses are exposed as `SIGNAL_LOST`;
- the app records receipt time using the monotonic Android clock;
- no persistent device identifier or full sensor stream is logged.

Samsung sends between zero and four IBI values with a heart-rate event, and some subsequent data points can contain no IBI list. The UI therefore keeps the most recently received valid IBI visible as `Last valid IBI` instead of replacing it with a dash on the next empty event. This is presentation state only: the application does not synthesize, interpolate, or copy the retained value into a new sensor sample.

The status-to-model conversion is isolated in a pure mapper with unit tests. Signal-quality formulas and more detailed artifact policy remain Milestone 5 work.

## Lifecycle and battery safety

Starting measurement follows this sequence:

1. connect to Health Sensor Service;
2. query supported tracker types;
3. obtain `HEART_RATE_CONTINUOUS`;
4. attach one event listener.

The screen stays awake only while connecting or tracking so that a short foreground measurement is not interrupted by the watch display timeout. Stopping, leaving the activity, capability failure, tracker error, and service disconnection all lead to cleanup. Cleanup clears the keep-screen-on flag, unsets the tracker event listener, and then disconnects Health Sensor Service. This prevents an abandoned measurement from continuing to consume display, sensor, and battery resources.

## Physical-watch verification

Prerequisites:

- compatible Galaxy Watch running Wear OS powered by Samsung;
- Health Sensor Service developer mode enabled for local debug signing;
- wireless ADB connected for the current session;
- the watch worn snugly and kept still during initial acquisition.

Install and open the Samsung screen, replacing `<watch-address:port>` with the current transient ADB endpoint:

```powershell
adb -s <watch-address:port> install -r app\build\outputs\apk\samsung\debug\app-samsung-debug.apk
adb -s <watch-address:port> shell am start -n dev.prylski.breath/.presentation.SamsungSensorActivity
```

On the watch:

1. read the permission rationale;
2. tap `Allow heart rate` and grant the system permission;
3. tap `Start sensor`;
4. confirm that a nonempty BPM appears and at least one valid IBI is observed;
5. tap `Stop sensor` and confirm the stopped state;
6. leave the screen and confirm that returning requires starting a new measurement.

The milestone is not accepted until this procedure succeeds on the physical watch. A successful build or emulator run is not evidence of real Samsung sensor access.

## Verification performed

After package migration and flavor separation:

```text
testDemoDebugUnitTest       11 passed
testSamsungDebugUnitTest    14 passed (including 3 Samsung mapper tests)
assembleDemoDebug           passed
assembleDemoRelease         passed
assembleSamsungDebug        passed
lintDemoDebug               passed
lintSamsungDebug            passed
connectedDemoDebugAndroidTest  6 passed on Wear OS 7/API 37 emulator
```

Physical Galaxy Watch verification:

- Samsung debug APK installation succeeded on an API 37 Galaxy Watch;
- connection, capability check, and `HEART_RATE_CONTINUOUS` produced nonempty real BPM and IBI with a good signal state;
- moving the activity out of the foreground returned the UI to the stopped state, confirming lifecycle cleanup;
- the first physical run exposed that the display timeout could interrupt a short foreground test, so the implementation was changed to keep the screen awake only while connecting or tracking;
- the updated APK passed build, 14 unit tests, and lint;
- the updated APK was installed successfully after reconnecting wireless ADB, and the user confirmed that explicit stopping returns the screen to `Sensor is stopped.`;
- the real-sensor start, acquisition, and explicit stop paths are therefore physically demonstrated for this milestone. The retained last-valid-IBI presentation was included in the installed build; it does not alter or synthesize sensor samples.

No observed physiological values, device address, transient port, or persistent identifier is stored in this document.

## Official references

- https://developer.samsung.com/health/sensor/overview.html
- https://developer.samsung.com/health/sensor/guide/getting-started.html
- https://developer.samsung.com/health/sensor/guide/permission-request.html
- https://developer.samsung.com/health/sensor/guide/data-specifications.html
- https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/ValueKey.HeartRateSet.html
- https://developer.samsung.com/health/sensor/guide/app-verification.html
