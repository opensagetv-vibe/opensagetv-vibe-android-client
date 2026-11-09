# Android dependency audit

This audit covers the active `source/dev` Gradle projects. It does not treat
the frozen `source/existing` comparison tree as a release input. Machine-readable
reports are generated under `artifacts/reports/` and are not substitutes for
the locked build. The direct runtime redistribution inventory is maintained in
the release-root `THIRD_PARTY_NOTICES.md`.

## Reproducible evidence

EXT-018's default-off hardware trial rebuilds only pinned k0.8.8 player glue:
ARMv7 AAR54b4494000c4748f63a736c96f4eeb81e3421b1ec14ae3f260e6eb83ecb75422,
ARM64cd358f4b13307d600eb86355d01e1a967d7f9a3f0eeb66acbf9e0ef599d62ef4.
Exports712/714, dependencies and every non-player entry match the prior ARM
artifacts. Exact hardware-step patch/helper source is in the existing LGPL
offer. No Maven, FFmpeg/SDL/Java dependency or license change. ARM32 physical
trial/normalized Fixed correction pass; ARM64 is build/ABI only. Trial remains
off pending further lifecycle/backend/full-protocol work; no broad parity claim.

EXT-010 uses a separate detailed-ordinary-Push implementation with distinctly
named VibeForwardPushMediaPeriod/Source copies of public Apache Media3 1.11.0
source. Original source SHA256 respectively6c94579591091bbf00e2571a5163ae197bf30ef99e012e488f98fb68a6347257
and7e54939633b1b98864fd2032c0b00df3072fb9168582f1dd4a89737d5c504dd8;
original copyright/license retained. No Maven/AAR version or native dependency
changes. Only actual selected queues may seek, with atomic AV rollback; byte
SeekMap, FIFO reopening and synthetic clocks are not introduced. Basic Push,
Pull/Direct and authored DVD retain existing sources. Protocol capability stays
conservative: forward queues do not imply general random access or raw-PTS
decode-to. Caption/epoch prototype gates pass; final default/track/negative
qualification is recorded under EXT-010, not claimed from compilation. Record
future maintenance against this exact version rather than tracking latest.

Legacy Exo uses its own public2.18.1 adaptation in com/google/android/exoplayer2/
source, not Media3 binary types. Exact upstream ProgressiveMediaPeriod SHA256
3aee2b9d38d5be4e41153082e12a3c96ea8c79d75e1f94ea7b871f40705fe1a3 and Source
52f388b53ffd9e3ce10bac62ea3af1dc31879007bed1398754189010bd78693c, from the
official google/ExoPlayer r2.18.1 tag. Original2016 Apache headers retained;
obsolete compile-only compatqual.NullableType uses existing qual.Nullable.
No new dependency, native relink, annotation stub or verification bypass.
Physical legacy37/39/40/corrected41 and final basic42 qualification now passes;
Media3 default34/35/36 and earlier CEA/DVB rows pass independently. Exact scope
and unsupported IJK/System cache targets remain in compact EXT-010 result.
No general protocol capability or all-device hardware equivalence is claimed.

DIAG-012 adds only pinned com.google.zxing:core:3.5.3 for offline QR encoding.
The new release runtime report selects123 coordinates; inventory/notice updated,
only one entry added to each Android lockfile and no existing versions changed.
Core JAR and both ZXing POM SHA256s were independently checked against Maven
Central bytes and published checksums, then explicitly added to verification
metadata. Verification remains enabled. Versioned upstream Apache-2.0 license
reviewed. This does not rerun or supersede the older full OSV audit below.

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
| IJK / bundled FFmpeg AARs | pinned k0.8.8 ARM player glue; baseline FFmpeg/SDL/Java | IJK LGPL-2.1-or-later and upstream component notices recorded; embedded FFmpeg has `--disable-gpl --disable-nonfree` | Narrow reviewed player-only deinterlace/Annex-B/A53/FIFO-flush/paused-step/output-clock patches reconstruct through scripts/build_ijk_deinterlace.py; every other AAR entry stays byte-identical. scripts/verify_ijk_native_candidate.py checks export/dependency ABI and unchanged entries before strict hash review. Software ARM normal-Pause Push/Fixed and affected native clock gates pass; hardware/DVD/reverse unsupported, no bulk runtime upgrade |
| FFmpeg Exo audio extensions | checked-in legacy Exo 2.18.0 and Media3 1.11.0 artifacts | Exo/Media3 wrappers Apache-2.0; pinned decoder-only FFmpeg configurations retain LGPL obligations and enable neither GPL nor nonfree code | Audio fallback only; MediaCodec/passthrough remain first, experimental Media3 FFmpeg video is disabled, and distinct JNI library names prevent cross-generation binding |
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
