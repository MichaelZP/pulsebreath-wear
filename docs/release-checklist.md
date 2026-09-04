# Publication and distribution gates

Repository preparation is not approval to publish. Ask separately for the exact repository
creation, visibility, push, tag, release and store submission operation. No remote is configured.

## Before source publication

- Inspect all tracked files and all reachable history for credentials, signing material,
  device identifiers, participant data and proprietary binaries. Pattern scans are not proof
  of absence; review images and archives too. Never publish local build output.
- Inventory resolved direct/transitive dependencies and preserve required license/NOTICE files.
  The 259-coordinate inventory and extracted notices are now in `THIRD_PARTY_LICENSES.md`.
  A scoped OSV scan and three build-dependency fixes are recorded in
  [the security checkpoint](milestone-8-build-dependency-security.md). The Kotlin finding,
  full toolchain scanning and final distribution compliance review remain pending. Apache-2.0
  covers only original project material. Samsung terms were supplied for private review;
  confirm applicability and source/binary publication clearance separately.
- Review provenance of Android Studio template icons, other artwork, project name and branding.
  Current icons are original heart-and-lungs vectors; old robot rasters are removed and
  historical credit is retained in NOTICE. Name/trademark clearance remains pending;
  do not imply affiliation or certified accuracy.
- Configure a private security-reporting channel and contributor/privacy policy before soliciting users.
- Build the demo from a clean SDK-free checkout and run unit tests and lint.
- After publication is explicitly approved, verify the actual remote and first GitHub CI run.

## Before any binary distribution

- Recheck [Samsung app verification](https://developer.samsung.com/health/sensor/guide/app-verification.html).
  Developer mode is not a substitute for production approval. Confirm package/signature registration.
- Review current [Google Play policies](https://play.google.com/about/developer-content-policy/),
  health permissions, Data safety, privacy policy, target API and Wear OS requirements.
  No store-policy compliance review or partner registration is complete.
- Approve release signing ownership, secure offline backups and recovery plan. Never commit keys.
- Complete relevant physical-watch lifecycle, battery, UI and sensor tests; do not label protocol
  preparation or a functional self-test as accuracy validation.
- Review each claim against the validation evidence. Calibration and clinical validation are absent.

## Versioning

Use SemVer for future reproducible checkpoints; document algorithm versions independently
in `metrics.md`. Select the first public version explicitly (an experimental `0.x.y` is an option).
Increase Android `versionCode` for every distributed update. Current `versionName=1.0` and
`versionCode=1` are internal build metadata, not an existing public release.
Tag only the accepted commit after tests, approval and review of its exact contents.
No automatic signing, artifact upload, release creation or store deployment is configured.
