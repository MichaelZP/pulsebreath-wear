# Security and privacy

This unreleased prototype has no production support commitment.
Provider and maintainer: **Michał Przybylski**.
For an initial, non-sensitive contact, use the maintainer-supplied
[LinkedIn profile](https://www.linkedin.com/in/micha%C5%82-przybylski-323a4948/).
No project email address is designated. LinkedIn messaging availability has not been
verified, and the profile is not a designated confidential vulnerability-reporting channel.
Use initial contact only to arrange an appropriate private channel; do not post sensitive
reports publicly or send health records through LinkedIn.

Do not submit real health measurements, watch identifiers, pairing codes, credentials,
keystores or unredacted screenshots in issues, logs or pull requests.
Use synthetic minimal reproductions. No private reporting channel is configured yet;
establish one with the maintainer before transmitting sensitive vulnerability details.

Keep `local.properties`, signing material, SDK binaries and participant records outside Git.
Ignore rules are safeguards, not secret scanners: previously tracked files remain tracked.
If a secret leaks, revoke/rotate it first, then coordinate history cleanup; do not rewrite
shared history without approval. Disabling backups does not secure exported files.

The current app processes readings in memory, requests sensor access only for the Samsung
variant, and does not provide persistence, export, networking or analytics features.
Do not add those features without an explicit data-minimization and retention review.
Stop measurements when done and disable wireless debugging after device testing.

CI has read-only repository permissions, pinned action commits and no signing secrets.
Review dependency/action updates, fork contributions and workflow changes before execution.
Do not use `pull_request_target` to execute untrusted contribution code with privileges.
