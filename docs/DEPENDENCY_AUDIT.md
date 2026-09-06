# Android dependency audit

This audit covers the active `source/dev` Gradle projects. It does not treat
the frozen `source/existing` comparison tree as a release input. Machine-readable
reports are generated under `artifacts/reports/` and are not substitutes for
the locked build. The direct runtime redistribution inventory is maintained in
the release-root `THIRD_PARTY_NOTICES.md`.

## Reproducible evidence

The resolved debug runtime graphs for `core`, `android-shared`, and `android-tv`
are captured in `artifacts/reports/gradle-runtime-dependencies.txt`. They select
122 unique Maven coordinates, all reconciled in
`third_party/RUNTIME_DEPENDENCIES.csv`. The inventory generator fails on an
unreviewed coordinate. The three
Gradle lockfiles contained 342 unique Maven coordinate/version pairs across all
locked configurations when queried against the OSV batch API on 2026-08-29;
the response is in `artifacts/reports/osv-gradle-lock-scan.json`.

OSV returned advisories for 14 coordinates: Protobuf 3.24.4 and Netty 4.1.93/
4.1.110 modules. None of those coordinates occurs in the captured application
runtime graphs; they enter non-runtime build/test configurations. They must
still be reassessed whenever AGP, Gradle tooling, or Android test dependencies
are updated. An empty OSV result is not proof that a dependency is safe.

## Direct runtime families

| Family | Selected version/boundary | License review | Change boundary |
| --- | --- | --- | --- |
| AndroidX UI/Preference/Leanback | resolved graph; declarations remain mixed-age | Apache-2.0; consolidated direct notice recorded | Update one UI family at a time and physically verify settings/TV navigation |
| Legacy ExoPlayer | 2.18.1 aligned | Apache-2.0 | Frozen playback baseline |
| AndroidX Media3 | 1.11.0 aligned | Apache-2.0 | Isolated player experiment; physical A/V matrix required |
| GSYVideoPlayer | 13.1.0 | Apache-2.0; versioned upstream license recorded in the release notice | Unused Cast/Session dependencies excluded; ordinary Media3 graph retained |
| libGDX | 1.9.14 plus four native ABIs | Apache-2.0 | Renderer behavior and native ABI checks required |
| Guava | resolves to 33.3.1-android | Apache-2.0 | Remove duplicate declarations only with a graph-equivalence test |
| Application event dispatch | Project-owned typed `VibeEventBus` | Apache-2.0 project code; Square Otto removed | Preserve synchronous posting-thread and registration-order behavior documented in `EVENT_OWNERSHIP.md` |
| Glide | 3.8.0 | BSD-3-Clause plus its bundled Apache/MIT/other retained sections; recorded without flattening to one license | Update separately from TV artwork lifecycle changes |
| SLF4J / Logback Android | SLF4J 1.7.6; Logback Android 1.1.1-6 | SLF4J MIT; Logback Android EPL-1.0 or LGPL-2.1; published metadata recorded | Logging and exported-file acceptance required |
| IJK / bundled FFmpeg AARs | checked-in 0.8.8-era artifacts | IJK LGPL-2.1-or-later and upstream component notices recorded; embedded FFmpeg has `--disable-gpl --disable-nonfree` | Do not replace while legacy IJK remains a tested backend |
| FFmpeg Exo audio extension | checked-in 2.18.0 artifact | Exo wrapper Apache-2.0; embedded decoder-only FFmpeg configuration has no GPL/nonfree enable flag and retains LGPL obligations | Audio decoder extension only; keep isolated from Exo core |
| Core utilities | jzlib 1.1.3 and NanoHTTPD 2.2.0 | JZlib BSD-style and NanoHTTPD BSD-3-Clause; each recorded explicitly | The GPL-2.0 Ostermiller circular buffer was replaced by the project's Apache-2.0 implementation; Core protocol and physical Push tests are required |
| SMBJ | 0.13.0, Android-safe dependency subset | Apache-2.0; upstream license reviewed | SMB2/SMB3 only; desktop SPNEGO authenticator excluded, NTLM/guest retained, credentials kept out of profiles and telemetry |

The complete selected runtime graph is now reconciled against the release
notice and full license/source-availability material under `third_party/`.
`scripts/create_github_release_bundle.py` rejects a stale source manifest,
missing release legal material, unsafe/duplicate ZIP paths, hash drift, a dirty
release worktree (unless explicitly producing a local review snapshot), or an
unexpected publication file set.

## Deterministic caption-fixture tool

`scripts/generate_a53_seek_fixture.py` is repository test tooling, not an APK
runtime dependency. Its focused CEA-608/708 transport design was informed by
Joey Parrish's Apache-2.0 `open-cea` project; the checked-in implementation was
written for OpenSageTV Vibe and is distributed under this repository's
Apache-2.0 license. It has no Python package dependency on `open-cea` and does
not bundle that project. FFmpeg comes from the unified development image.

The tool injects synthetic timestamp cues only into an explicitly generated
test file. The Android app and MIM runtime contain no generator and cannot add
synthetic captions to a recording or live ATSC program.

## Exported/transitive surface

`gsyvideoplayer-exo2` publishes `androidx.media3:media3-cast` and
`androidx.media3:media3-session` unconditionally even though its bytecode and
the active SageTV source do not reference Cast or Session APIs. Both optional
modules are now excluded. The dependency declaration explicitly constrains the
previously selected non-Cast AndroidX/Kotlin versions, avoiding the 32-module
version drift observed during the original exclusion trial. Regenerated locks
remove the Cast, MediaRouter, DataTransport, Compose-only, and Media3 Session
subgraphs while retaining the tested versions of every surviving coordinate.

The app manifest also removes the transitive ProfileInstaller initializer and
receiver. Debug and release-candidate manifests contain no Cast,
DataTransport, `BluetoothValidationActivity`, or ProfileInstaller component.
The release APK inspector makes those names a failing boundary. Host tests,
strict verified builds, both AABs, the release-candidate APK, launcher/MCP, and
generated MPEG-2/AC3 hardware Pull playback pass. The rapid mixed FF/REW stress
has a separate audio-state failure and remains a playback task rather than a
false dependency-audit pass.

## Required update procedure

For each dependency family:

1. Capture the before/after resolved runtime graphs and lockfiles.
2. Refresh verification metadata only from trusted repositories.
3. Run host tests, validator, clean debug/release builds, and APK inspection.
4. Verify licenses/notices and rerun the OSV scan.
5. Run launcher/settings plus completed/growing MPEG-2 playback on the physical
   target before accepting a runtime dependency change.
