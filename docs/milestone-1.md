# Milestone 1 - minimal Wear OS application

Status: implemented and verified on 2026-09-03; awaiting user acceptance.

## Scope

Milestone 1 intentionally contains only:

- one standalone Wear OS application module;
- one round-screen home view;
- the application name;
- one working **Start** button;
- a preview and one device UI test.

It does not contain breathing timing, sensors, Samsung SDK integration, persistence, networking, or health-data handling.

## Generated baseline

The project was generated with Android Studio's current **Empty Wear App** template and then reduced to the milestone requirements.

- build scripts: Kotlin DSL;
- application language: Kotlin;
- UI: Compose for Wear OS Material 3;
- application ID: provisional `com.example.pulsebreathwear`;
- minimum SDK: API 30;
- compile and target SDK: API 37;
- phone companion module: disabled;
- Gradle Wrapper: 9.6.0;
- Android Gradle Plugin: 9.4.0.
- Kotlin Compose plugin: 2.4.10;
- Compose BOM: 2026.08.00;
- Compose for Wear OS Material 3 and Foundation: 1.6.2.

## Structure

| Path | Responsibility |
| --- | --- |
| `settings.gradle.kts` | Names the project, selects repositories, and includes the `app` module |
| `build.gradle.kts` | Declares shared plugins without applying them at the root |
| `gradle/libs.versions.toml` | Keeps plugin and library versions in one catalog |
| `app/build.gradle.kts` | Configures the Wear OS application, SDK levels, build type, and dependencies |
| `app/src/main/AndroidManifest.xml` | Declares a standalone watch application and launcher activity |
| `presentation/MainActivity.kt` | Enters Compose and defines the minimal home screen |
| `presentation/theme/Theme.kt` | Provides the Wear Material 3 theme boundary |
| `PulseBreathHomeScreenTest.kt` | Verifies the title, button semantics, click action, and callback |

`MainActivity` owns only Android lifecycle entry. `PulseBreathApp` applies the application theme and scaffold. `PulseBreathHomeScreen` receives an `onStart` callback, so future navigation or session logic can be added without embedding it in the visual component.

## Verification commands

The cache is kept on disk D to protect the limited free space on disk C.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'D:\Android\Sdk'
$env:GRADLE_USER_HOME = 'D:\Android\Gradle'
$env:ANDROID_SERIAL = 'emulator-5554'

.\gradlew.bat --no-daemon :app:assembleDebug :app:lintDebug :app:connectedDebugAndroidTest
```

Final result:

- debug APK: built successfully;
- lint: passed;
- instrumented Compose UI test: 1 passed on the Wear OS 7.0/API 37 emulator;
- installation: successful;
- launch: `MainActivity` confirmed as the top resumed activity;
- visual check: title and the single centered **Start** button fit the 384 x 384 round display.

Resource note: the project build directory was about 0.07 GB and the Android Studio cache about 0.20 GB, while Gradle's cache is on disk D. A transient reading of 2.3 GB free on disk C recovered to about 14.5 GB after tool activity completed. Free space should still be checked before each dependency-heavy milestone.

## Test compatibility correction

The template initially resolved Espresso 3.5.0 transitively. On Android API 37 it failed before the assertion with a reflective lookup for the removed `InputManager.getInstance()` method.

The project now pins the current Google Maven releases used for device testing:

- AndroidX Test runner 1.7.0;
- AndroidX Test JUnit extension 1.3.0;
- Espresso Core 3.7.0.

After the update, the unchanged UI test passed. The failure therefore came from test-infrastructure compatibility, not from the application screen or assertion.

## Known limitation

The production `onStart` callback is intentionally empty. Starting a timed breathing session belongs to Milestone 2 and must not be introduced before Milestone 1 is accepted.
