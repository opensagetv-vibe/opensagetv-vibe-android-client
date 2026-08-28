# Source Patches

- `firebase-removal.patch` — Firebase/Crashlytics/Google Services removal against the uploaded baseline, without the separate Dev application-identity change.
- `dev-phase0-full.patch` — complete current diff from `source/existing` to `source/dev`, including Dev application identity, Firebase removal, documentation/verification additions, and Dev metadata.

The authoritative working trees are already bundled under `source/existing` and `source/dev`; these patches are included for review/audit and are not required during normal setup.
