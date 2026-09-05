# Milestone 2: Sensor-free breathing trainer

Date: 2026-09-03

## Result

The Wear OS app runs a two-minute breathing session without sensors. The default cycle is configurable in code and consists of a 4.5-second inhale followed by a 5.5-second exhale. The active screen shows the current phase, an expanding or contracting circle, whole-session progress, remaining time, and pause/resume/stop controls.

No Samsung SDK, health permission, health data, network access, phone module, or persistent storage is used in this milestone.

## State machine

```text
IDLE ------ start ------> RUNNING ------ elapsed 120 s ------> COMPLETED
                           |   ^
                         pause resume
                           v   |
                         PAUSED
                           |
                stop from RUNNING or PAUSED
                           v
                       CANCELLED ------ back ------> IDLE

COMPLETED ------ restart ------> RUNNING
```

`BreathingSessionState` is immutable. Each action returns a new state, which keeps state transitions deterministic and directly unit-testable.

## Timing model

The activity supplies `SystemClock.elapsedRealtime()` through a small `MonotonicTimeSource` interface. This clock cannot jump when wall-clock time, time zone, or network time changes.

For a running session:

```text
active elapsed = accumulated active time + (monotonic now - running start)
cycle position = active elapsed mod (inhale duration + exhale duration)
overall progress = active elapsed / session duration
```

Paused time is excluded by moving the current active duration into `accumulatedActiveMillis` and clearing the running start timestamp. Values are clamped to the configured session duration.

The breathing circle expands linearly from its minimum to maximum radius during inhale and contracts linearly during exhale. The outer arc displays whole-session progress independently of the current breathing phase.

## Haptics and screen behavior

A short `Confirm` haptic is requested when a running session starts or changes between inhale and exhale. The emulator validates the code path and UI integration but cannot validate how the vibration feels; that requires the physical watch.

The root activity view sets `keepScreenOn` while the session status is `RUNNING`. The completed screen receives a five-second grace period before the request is cleared. The request is also cleared during pause, cancellation, or disposal. This is intentionally limited to the short, user-attended two-minute visual exercise. No foreground service, explicit wake lock, Ongoing Activity, or Live Update is added in this milestone.

This decision follows Android guidance to use the lightest mechanism that meets the need and to keep the screen on for as short a time as possible. Wear OS guidance describes screen-on operation as high-impact and recommends ambient mode for longer experiences. If a later milestone permits sessions to continue after the user leaves the activity, it must add and test an appropriate background/ongoing-session design rather than silently extending this implementation.

Official references:

- https://developer.android.com/develop/background-work/background-tasks/awake/screen-on
- https://developer.android.com/training/wearables/apps/power
- https://developer.android.com/training/wearables/always-on

## Verification

Development target: `PulseBreath_WearOS_7_API_37`, Wear OS 7/API 37, 384 x 384 round emulator.

Commands:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest
```

Results:

- seven JVM unit tests passed for configuration, phase boundaries, monotonic elapsed time, pause/resume, completion, cancellation, and circle expansion;
- four connected Compose UI tests passed for the home, running, paused, and completed screens;
- debug application and test APKs assembled successfully;
- Android lint completed successfully;
- the home, running, paused, and cancelled states were inspected on the round emulator;
- automatic screen timeout was tested while a session was running; the activity stayed interactive because `keepScreenOn` was scoped to `RUNNING`;
- physical-watch haptic strength and feel were not verified in this milestone.

## Battery and lifecycle review

- The animation is active only while `RUNNING`.
- Pausing ends the animation loop and permits the screen to time out.
- Completion ends the animation loop and releases the screen-on request after a five-second result grace period; cancellation releases it immediately.
- Elapsed physiological time is calculated from the monotonic clock, not frame count.
- The state is currently held in Compose memory; process death does not restore an unfinished session. Persistence is deliberately outside this milestone.
