# Toolchain and non-Maven license inventory

Snapshot: 2026-09-04. These tools run on the build/CI host, not on the watch.
Their licenses do not automatically become the license of original PulseBreath code.

| Component/version | License record | Primary source / provenance |
| --- | --- | --- |
| Gradle 9.6.0, including Wrapper | [LICENSE](tools/gradle-LICENSE.txt), [NOTICE](tools/gradle-NOTICE.txt) | Actual Wrapper-downloaded 9.6.0 distribution. LICENSE includes its bundled library list and additional license families. |
| Temurin 25.0.3+9-LTS | [GPL-2.0 and Classpath exception](tools/jdk-LICENSE.txt), [assembly exception](tools/jdk-ASSEMBLY_EXCEPTION.txt), [NOTICE](tools/jdk-NOTICE.txt) | Actual Gradle-provisioned JVM, confirmed from its `release` metadata. Its `legal/` directory contains 253 component-specific legal files. Those additional files remain with the separately installed JDK; this repo does not distribute that JDK. [Upstream](https://adoptium.net/). |
| Android SDK platform 37.0, Build Tools 36.0.0 | [SDK agreement](tools/android-sdk-agreement.txt) | Agreement read from the installed platform `package.xml`; component NOTICE files remain in the installed SDK packages. [SDK terms](https://developer.android.com/studio/terms). |
| Android Studio | Separate IDE distribution and its bundled notices | Optional development host tool, not a pinned project dependency or redistributed application component. This snapshot does not inventory unrelated IDE plugins. |
| Foojay resolver 1.0.0 | [Apache-2.0](tools/foojay.txt) | Exact upstream tag resolves to commit `2a6cc60ed8d35e025e44193c895502996c8edf4e`; Maven implementation and plugin marker are also in dependencies.json. |
| Samsung Health Sensor SDK 1.4.1 | Proprietary, privately supplied agreement | Not redistributed as a standalone AAR. Confirmation of applicable terms and required approvals remains a separate gate. No confidential contract text is included here. |

## Pinned CI actions

| Action | Pinned commit | License |
| --- | --- | --- |
| actions/checkout v7 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | [MIT](tools/ci-checkout.txt) |
| actions/setup-java v6 | `dd06d9cba3e5552c54d9f8ea23572deb30010f7c` | [MIT](tools/ci-setup-java.txt) |
| android-actions/setup-android v4 | `40fd30fb8d7440372e1316f5d1809ec01dcd3699` | [MIT](tools/ci-setup-android.txt) |
| gradle/actions/setup-gradle v6 | `4733eaac7c1b0da527e4206b7671e0061de1ce37` | [MIT core and proprietary-component exception](tools/ci-gradle-LICENSE.txt), [NOTICE](tools/ci-gradle-NOTICE.txt), [enhanced caching terms](tools/ci-gradle-licenses-gradle-actions-caching-license.txt) |

Action texts were read from these exact commits, not assumed from their project names.
The Gradle action publisher states that proprietary caching is not loaded for basic/disabled
caching. This work does not change the action configuration, select/accept a commercial plan,
or execute a remote workflow. Review the pinned action's inputs and
[distribution breakdown](https://github.com/gradle/actions/blob/4733eaac7c1b0da527e4206b7671e0061de1ce37/DISTRIBUTION.md)
before the first CI run. Bundled npm/native dependencies of actions and all Ubuntu runner
packages are governed by their upstream distribution notices; they are not recursively
enumerated in the Maven list or redistributed in the watch APK.

## Scope and handling

License texts here retain publisher attribution; line endings are normalized to LF.
They do not grant new rights, supersede the agreements or authorize distribution of SDK/JDK
binaries. If distributing a toolchain or CI action rather than just app source, obtain the
entire corresponding distribution with all component notices, sources/offers where required,
and review its exact terms separately. No toolchain binary redistribution is part of this project.
