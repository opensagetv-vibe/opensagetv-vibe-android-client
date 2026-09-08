# OpenSageTV Vibe Android Client tasks

This is the only authoritative task backlog for this subproject. Completed work
is removed from this file and recorded in `CHANGELOG.md` and `HANDOFF.md`.
Workspace-wide dependencies and release ordering may also be mirrored in the
parent workspace `task.md`, but Android-only work must remain current here so
the repository can be developed independently of Codex.

Before playback work, read `docs/PLAYBACK_DIAGNOSTICS.md`. Do not use historical
version checklists as current instructions.

Tasks are ordered by executability. Work that can be completed with the current
unified container, test server, generated fixtures, and commissioned non-Pro
Fire TV (`192.168.10.25:5555`, AFTMM/API 25) comes first. Tasks needing another
device, fixture, credential, signing decision, or external account are grouped
separately and must not block available work.

## 1. Active and immediately executable

There are no unfinished tasks that can be executed with the currently
commissioned environment. The v0.5.87 source/APK release passed its final
validation and publication gates.

The useful legacy-extender semantics that can be implemented and physically
tested in the current environment are complete. Media3 and legacy Exo emit raw
caption event-225 data, audio and interlace capabilities are reported without
false passthrough/deinterlacer claims, and unsupported firmware-only
capabilities remain unadvertised. Add a new item here only when it has a
specific Android implementation and physical gate.

Active Player Adjustments now includes every control backed by a proven
runtime implementation: text presentation, caption/audio selection, subtitle
offset, refresh matching, bounded DVD HDMI settle, controlled decoder reload,
and bounded process diagnostics. Encoded passthrough, decoded-PCM processing,
DVD SPU/PGS styling, unsupported deinterlace controls, arbitrary codec
parameters, and resolution changes remain fail-closed until a backend exposes
a testable clock/output contract.

## 2. Foundation already complete

All discovered production scheduling, connection, transport, renderer,
keyboard, player-progress/recovery, SMB-cleanup, and overlay owners have
explicit cancellation/teardown behavior and executable inventory coverage.
OpenGL and GDX pass startup, active playback, overlays, HOME/return, reconnect,
and teardown. The only production polling retained is the bounded SageTV
growing-file SIZE retry because that protocol has no growth notification.

---

## FEATURE-EXPANSION BOUNDARY

The commissioned foundation above is complete. New features must retain the
established player, lifecycle, caption, fullscreen, compatibility, and teardown
gates. External-device-only checks block release sign-off, not unrelated work.

## 3. GitHub source and APK release track

Amazon Appstore and Google Play publication are not part of the current release
scope. The current deliverable is reviewable source plus an installable APK on
GitHub.

The v0.5.87 GitHub source/APK release is complete: durable documentation, stable machine
metadata, dependency/license notices, manifests, deterministic APK/source
bundles, and the changed-files handoff workflow are verified. An untouched
v0.5.75 Windows worktree applied the original v0.5.85 release, normalized only proven CRLF-equivalent
text, removed obsolete files, passed all test/validation/build gates, installed
the Dev APK, and launched it on `.25` from the same root workflow.
The repository is a true fork of `OpenSageTV/sagetv-miniclient`, `main` is the
Vibe default branch, and the development-signed APK, verified source archive,
manifest, and checksums are published under tag `v0.5.85`.

## 4. Requires hardware, fixtures, credentials, or user decisions
- [ ] Replace the temporary API 36 predictive-Back opt-out with callback
  handling physically tested on API 33+, then remove the restricted-
  resizability compatibility bridge before targeting API 37. The API-25 legacy
  Back path passes.
- [ ] Commission Blu-ray/BDMV main-title playback after a legal valid physical
  fixture becomes available. Until then the result is explicitly `SKIPPED` and
  is not a failure of completed DVD Native/Hybrid/MIM behavior.
- [ ] Exercise secure-decoder/DRM selection with an authorized Widevine or
  PlayReady test asset. A clear FFmpeg fixture cannot prove secure MediaCodec
  behavior.

## 5. Deferred Amazon Appstore and Google Play work

- [ ] Run the production-APK inspector with the approved production signing
  key. Debug/MCP code, disallowed permissions, actions, and secret scans pass;
  the local release candidate correctly fails because it uses the development
  certificate.
- [ ] Approve the upload/signing key, version policy, and Play App Signing
  ownership. The application ID `opensagetv.vibe.miniclient` and visible name
  `OpenSageTV Vibe` are already selected and enforced by tests.
- [ ] Complete Play Console readiness: Android TV opt-in, privacy-policy URL,
  Data Safety declaration including third-party SDK behavior, app-access
  instructions, high-resolution TV screenshots/listing assets, content rating,
  target audience, and required testing track. Validate against the official
  [Android TV quality requirements](https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality)
  and [Google Play Data Safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469).

## 6. Future store release and publication after prerequisites

- [ ] Convert the validated development-signed release-candidate AAB into the
  approved production AAB and add its strict signing/content gate. Debug and
  release-candidate AAB generation, bundletool validation, universal APK-set
  generation, guarded physical debug installation, and preserved app data
  already pass. Google Play no longer accepts APK-only Android TV releases.
