# Third-party licenses and attribution

Inventory snapshot: 2026-09-04, based on `e49fc59` plus the branding/license changes.
Original PulseBreath code and new heart-and-lungs artwork: [Apache-2.0](LICENSE).
Third-party terms are not replaced by that license. See [NOTICE](NOTICE).

## Inventory and coverage

- [Complete resolved Maven list](docs/licenses/DEPENDENCIES.md): **259 unique coordinates**
  across **28 configurations**, including both flavors, debug/release, configured unit/device
  tests, annotation processors, Kotlin compiler and project/settings plugin classpaths.
- [Machine-readable evidence](docs/licenses/dependencies.json): versions, exact scopes,
  cached POM hashes (including inherited licenses), artifact hashes and original legal-text paths.
- [40 deduplicated upstream legal texts](docs/licenses/upstream-notices/), extracted from JAR/AAR
  files and nested JARs. Original copyright statements and formatting are preserved.
- [Reviewed exceptions](scripts/license-overrides.json): eight coordinates without POM license
  declarations, resolved using exact artifact texts, upstream releases or publisher statements.
  Bouncy Castle evidence is current project-wide publisher terms, not a recovered release POM.
- [APK runtime inventory](app/src/main/assets/open_source/dependencies.json): union of **119**
  demo/samsung debug/release Maven runtime coordinates. BOMs and metadata modules are included;
  this is a resolution inventory, **not** proof that every listed module is packaged byte-for-byte.
  Runtime notices and Apache-2.0 text are included as `assets/open_source` in both APKs.

All 259 Maven coordinates have a declared or reviewed license entry. This does not mean that
all project distribution conditions are satisfied: the applicability and publication clearance
of the separately supplied Samsung agreement still require confirmation.
The inventory is not a vulnerability scan, legal opinion, trademark search, or approval to publish.
Custom external components downloaded at execution time, host IDE plugins and the full GitHub
runner operating-system image are not Maven dependencies and are outside this inventory.

## License families (scope matters)

| Component family | License evidence | Where used |
| --- | --- | --- |
| AndroidX, Compose, Kotlin, kotlinx, annotations, JSpecify, Guava | Apache-2.0 declarations; Guava parent POM inheritance | App and/or build/test, see exact scopes |
| JUnit 4.13.2 | EPL-1.0 | Unit and device tests, not main app runtime |
| Hamcrest 1.3 | BSD-style license with upstream copyright | Tests |
| ASM, Protobuf | BSD-3-Clause declarations | Build tools |
| JAXB and related components | EDL-1.0; some POMs also list Apache-2.0 | Build tools |
| JNA | POM lists LGPL-2.1 and Apache-2.0; retain both declarations, do not infer a choice | Build tools |
| juniversalchardet | MPL-1.1 declaration | Build tools |
| SLF4J, checker-qual, jopt-simple, kXML | MIT/MIT-style terms, retaining upstream copyright | Build tools |
| Bouncy Castle | Publisher's MIT-style Bouncy Castle License | Build tools |
| JDOM 2.0.6 | Exact JDOM LICENSE.txt, including naming restrictions | Build tools |
| Foojay toolchain resolver 1.0.0 | Apache-2.0 at the source tag | Settings/build tools |

Multiple entries in a POM are recorded verbatim, not silently converted to an `AND`/`OR`
legal interpretation. Do not label the entire toolchain or every artifact Apache-2.0.
These host tools are not included in the watch APK merely because Gradle uses them.

## Non-Maven components

| Component | Terms and evidence | Distribution boundary |
| --- | --- | --- |
| Samsung Health Sensor SDK 1.4.1 | Proprietary agreement supplied privately by the project owner for review. Its applicability to this SDK release needs confirmation. [App verification](https://developer.samsung.com/health/sensor/guide/app-verification.html) is not the SDK license. | Local AAR remains ignored; agreement text is not reproduced or relicensed; distribution remains pending approval |
| Gradle Wrapper/distribution 9.6.0 | [Distribution LICENSE](docs/licenses/tools/gradle-LICENSE.txt) and [NOTICE](docs/licenses/tools/gradle-NOTICE.txt), including bundled component terms | Wrapper JAR in source; full Gradle distribution is not shipped with the app |
| Temurin JDK 25.0.3+9 | GPL-2.0 with applicable Classpath/assembly exceptions and component-specific legal notices; see [toolchain inventory](docs/licenses/TOOLCHAIN.md) | Local build JVM; not watch application code |
| Android SDK platform/build tools | SDK agreement plus individual open-source component notices; see [toolchain inventory](docs/licenses/TOOLCHAIN.md) | Installed separately; no SDK distribution is included here |
| GitHub Actions | [Pinned action license texts](docs/licenses/tools/) and [toolchain inventory](docs/licenses/TOOLCHAIN.md) | CI only; not bundled with the app |
| Historical Android robot | Google artwork, CC BY 3.0; [historical attribution](NOTICE) | Removed from current app, remains in old Git revisions |
| New heart-and-lungs logo | Original project vector geometry, Apache-2.0 | Current launcher, monochrome icon and splash |

**Important CI exception:** Gradle Actions v6 has an MIT core and a separate proprietary
`gradle-actions-caching` component for enhanced caching. See its retained LICENSE/NOTICE and
separate terms in `docs/licenses/tools`. No GitHub run has occurred. Review configuration and
acceptance before enabling that component; MIT alone is not a complete description of this action.

## Regenerate and verify

Use Python 3 (standard library only) and the repository Gradle Wrapper:

```powershell
.\gradlew.bat --offline --no-daemon --no-configuration-cache -I scripts/license-inventory.init.gradle licenseInventory
python scripts/license_inventory.py --resolution build/reports/licenses/resolution.json --cache "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1" --output docs/licenses --assets app/src/main/assets/open_source
python -m unittest discover -s scripts -p "test_*.py"
```

If dependencies are not cached, resolve them with network access before rerunning. The generated
`build/reports/licenses/resolution.json` contains local paths and must remain ignored. Public
inventories contain only coordinates, relative notice paths and hashes. Missing/ambiguous POMs
are reported explicitly. Review every new override rather than guessing a license.

The generator does not delete obsolete files automatically. When updating versions, generate
into a fresh temporary directory, compare manifests and remove only confirmed obsolete notice
files. Recheck toolchain and pinned-action terms separately; they are not inferred from Maven.

Before distributing a binary, verify that its runtime notices match the actual artifact, preserve
all applicable upstream notices and license texts, and resolve the Samsung agreement separately.
