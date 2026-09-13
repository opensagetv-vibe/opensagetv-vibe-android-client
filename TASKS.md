# OpenSageTV Vibe Android Client tasks

This is the only authoritative task backlog and durable master checklist for
this subproject. Completed work remains checked here and release-relevant
evidence is also recorded in `CHANGELOG.md` and `HANDOFF.md`.
Workspace-wide dependencies and release ordering may also be mirrored in the
parent workspace `task.md`, but Android-only work must remain current here so
the repository can be developed independently of Codex.

Checklist revision: **17** (2026-09-13)

## Stable checklist rules

- Every task has a permanent ID. Status updates change only `[ ]` to `[x]`.
- Completed tasks are never silently removed. A user-requested removal is
  retained in the change ledger with its former ID and reason.
- New findings are appended with a new ID under the appropriate phase; they do
  not silently replace an existing task.
- Reordering keeps the same ID and is recorded in the change ledger.
- Every status/add/remove/reorder change updates the revision and ledger below.
- Status reports reproduce this file rather than reconstructing a new list from
  chat history.

Before playback work, read `docs/PLAYBACK_DIAGNOSTICS.md`. Do not use historical
version checklists as current instructions.

Tasks are ordered by executability. Work that can be completed with the current
unified container, test server, generated fixtures, and commissioned non-Pro
Fire TV (`192.168.10.25:5555`, AFTMM/API 25) comes first. Tasks needing another
device, fixture, credential, signing decision, or external account are grouped
separately and must not block available work.

## 0. Completed foundation retained for continuity

- [x] **FOUND-001 - Stock-server-first compatibility.** Make the unmodified
  SageTV server the first playback gate and keep optional Sage.jar/MIM/FFmpeg
  extensions negotiated with safe fallback.
- [x] **FOUND-002 - Universal playback diagnostics overlay.** Provide dynamic
  player/source/decoder/audio/buffer/network/CPU diagnostics and MCP control
  without continuously instrumenting playback.
- [x] **FOUND-003 - Test Current Video.** Provide a bounded, reversible
  in-client playback test whose redacted result is included in diagnostic
  exports.
- [x] **FOUND-004 - Diagnostic bundle export.** Support manual and automatic
  export, independent authenticated/anonymous diagnostic SMB destinations,
  connection tests, and exit-time flushing for Always mode.
- [x] **FOUND-005 - Generated regression fixtures.** Maintain MPEG-2/AC3/CC,
  seek/comskip, live, discontinuity, and authored-DVD fixtures and their
  reproducible generation scripts under the shared test directory.
- [x] **FOUND-006 - DVD Native and compatibility foundation.** Implement DVD
  menu/title/audio/subtitle/chapter controls, main-feature behavior, hardware
  playback, Hybrid/MIM option, and safe stock-server fallback.
- [x] **FOUND-007 - Player and remote control foundation.** Preserve Media3,
  legacy ExoPlayer, IJK, and GSY delegates with play/pause/seek/FF/RW/Stop,
  captions, lifecycle recovery, fullscreen, and long-press diagnostics.
- [x] **FOUND-008 - ONN v1 commissioning and basic hardware matrix.** Commission
  ADB/MCP/HDMI and prove applicable Media3, legacy Exo, IJK, and both GSY
  delegates can start, play audio, seek, pause, and recover on stock-server
  MPEG-2 content.
- [x] **FOUND-009 - Same-file Pull seek investigation.** Prove repeated PCR
  timestamp discovery, not repeated decoder discovery, dominates distant seeks;
  retain the validated 256 KiB/16x search and bounded 8 MiB exact-byte cache.
- [x] **FOUND-010 - Long-recording control test.** Exercise a completed 5.5-hour
  recording on stock and Vibe servers with Wait-for-playback On/Off, shuttle,
  Stop, and restart; record that ONN v1 did not reproduce the reported stuck
  OSD.
- [x] **FOUND-011 - Durable fixture registry.** Store every durable physical
  fixture as a named TOML record with a generic path, path interpretation,
  per-fixture and per-mode switches, expected media metadata, and comments.
  Discover enabled tests by mode rather than hard-coded fixture IDs; record
  server stock/Vibe selection, Web-control capability, expected extensions,
  device/server STV, and adaptive storage defaults.

## 1. ONN hardware MPEG-2 and long-recording UI regression

Start after the current non-Pro Fire TV build and HDMI closure. Use the V1 ONN
Android TV 4K UHD Streaming Device (model `100026240`) and move the commissioned
USB HDMI capture only when requested. Keep this phase hardware/native-transport
only; software decoding and Fixed/MIM transcoding are outside this gate.

Progress: ONN ADB/MCP commissioning is complete. The stock-server `Meet the
Press` AC-3 silence was traced to invalid vendor raw passthrough and corrected
for Media3 and legacy ExoPlayer with hardware video plus decoded PCM audio.
Media3, legacy ExoPlayer, IJK, and both GSY delegates now have physical audio
evidence and applicable seek/pause recovery passes. Stock-server OTA MPEG-2
startup and controls are now reproduced through Media3, legacy ExoPlayer, and
both GSY extractor delegates. The exact legacy-GSY failure was an active TS
timestamp search being replaced by the seek watchdog; recovery now defers
while physical Pull reads are advancing. A completed 5.5-hour
recording cleared its OSD normally on both stock and Vibe servers after
Wait-for-playback On/Off, shuttle, Stop, and restart tests; recording duration
alone therefore does not reproduce the affected Fire TV report. MPEG-2
seek-hint optimization, cross-player comparison, universal correction,
Stop-key, and affected-device OSD reproduction remain active. Diagnostic
export and all three SMB destination tests are complete.
- Same-file investigation confirms that ordinary seeks retain the selected
  MPEG-2 hardware decoder and audio renderer; the delay is repeated PCR
  time-to-byte search, not repeated codec discovery. A physical multiplier-4
  search experiment regressed recovery from 8.4 seconds to 29.6 seconds. An
  inferred-byte shortcut also regressed a repeated seek and was removed. A
  controlled 128 KiB versus 256 KiB Media3 Pull comparison recovered in 7.612
  versus 6.296 seconds, proving that smaller probe transfers lose more time to
  MediaServer round trips. Keep the proven 256 KiB/16x search and accept only
  extractor-validated hints.
- A release-safe **Test Current Video** action is complete. Its separate
  play/pulse icon runs cadence, pause/resume, reversible first/away/repeat seek,
  decoder, buffer, source-read, landing, and recovery measurements against the
  already loaded stream. Unsafe checks are explicitly skipped, the prior
  position/state is restored, and the latest four redacted results are included
  in manual and SMB diagnostic exports. Physical ONN v1 Media3/Pull/hardware
  validation completed 6/6 checks in 9.889 seconds and the exported ZIP was
  inspected for the expected report and absence of media/server paths.
- The bounded same-file Pull probe cache is complete for Media3 and legacy Exo.
  It retains exact bytes only for stable completed files, is scoped to one
  player/file session, and clears on path, size/growth, disable, or release.
  Physical ONN testing proved a nearby repeat hit 524 KiB and recovered in
  362 ms without recreating the decoder. A controlled 8 MiB versus 16 MiB
  distant seek-away/return comparison showed no material recovery/read benefit
  from the extra memory (about 6.1--6.2 seconds), so the safer 8 MiB bound is
  retained. Video/audio decoder init remained one with zero releases; the
  remaining distant-seek cost is TS timestamp/demux discovery, not codec setup.
- [x] **ONN-001 - Universal hardware correction.** Make the correction universal by container/stream/decoder capability or
  decoder implementation family. Do not key production behavior to ONN model
  `100026240` unless no technically accurate runtime signal exists; retain
  Fire TV, NVIDIA H.264/H.265, captions, seeking, and file-transition behavior.
  Completed with no production model-name branch: decoded-PCM AC-3 policy and
  strict hardware video selection pass MediaTek Fire TV, Amlogic ONN v1, and
  NVIDIA Shield physical matrices across Media3, legacy ExoPlayer, and both
  GSY delegates. Resolution/PMT transitions, malformed containment, captions,
  seeking, and lifecycle evidence remain green or explicitly hardware-skipped.
- [x] **ONN-002 - Stop-key compatibility.** Verify Android `KEYCODE_MEDIA_STOP` and relevant remote scan/key events
  reach the SageTV Stop command without changing D-pad behavior or swallowing
  keys owned by DVD menus. Cover the Control4 report with raw key telemetry and
  a physical Stop/playback teardown gate where that remote is available.
  The shared Activity/key-map path now records key code/name, scan code, action,
  repeat count, source, device ID, long-press state, and mapped Sage command.
  ONN v1 physically passed injected Android `KEYCODE_MEDIA_STOP` -> SageTV
  `STOP` -> quiescent player teardown. A Control4 diagnostic bundle can now
  prove whether its Android TV driver emits key code 86 or a different event;
  no DVD D-pad mapping was changed.
- [ ] **ONN-003 - Persistent long-recording OSD.** Reproduce the persistent timeline/OSD on a completed three-hour-or-longer
  MPEG-2 TS. Compare Wait-for-playback-before-first-OSD On/Off, OpenGL On/Off,
  STV identity, remote repeats, and GFX frame/render queues. Fix the shared
  client GFX/state cause without a player-specific or forced-hide timer. ONN
  v1 did not reproduce this with the 5.5-hour regression recording on stock or
  Vibe; obtain diagnostics from the affected Fire TV rather than treating that
  non-reproduction as closure.
## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase

Status: **active after the completed audit-first review**. The complete pinned
Kodi/VLC/Media3/legacy-Exo comparison and its license boundary are in
`docs/DECODER_STABILITY_SOURCE_COMPARISON.md`. No reference player is launched;
MX Player is closed source and remains user-reported capability evidence only.

- [x] **D1 - Playback error classification.** Classify datasource/open/read,
  container/parser, codec initialization, fatal runtime codec, audio output,
  and active seek/flush/teardown errors. Unit-test recovery and presentation
  decisions before changing player listeners.
- [x] **D2 - Policy-filtered initialization fallback.** Let Media3 and legacy
  Exo walk the already-filtered candidate list. Prove strict `Hardware` remains
  hardware-only and strict `Software` remains software-only; GSY delegates
  inherit the change and IJK remains unchanged.
- [x] **D3 - Decoder-attempt telemetry.** Expose ordered candidates, attempted
  and selected decoder, initialization failures, session exclusions, fallback
  reason, codec-error count, and recovery result through stats, MCP, Test
  Current Video, and diagnostic exports.
- [x] **D4 - Session-local fatal-decoder quarantine.** Only after D1/D3 prove a
  fatal codec error, exclude that decoder for this playback session, rebuild
  once at the preserved position, and try the next policy-eligible candidate.
  Never persist an automatic exclusion or quarantine datasource/parser/seek/
  flush/teardown failures.
- [x] **D5 - Transition/audio evidence.** Add bounded audio-underrun,
  audio-output-error, program/track-change, and format-change counters. Extend
  generated TS discontinuity, PMT/track-change, MPEG-2 sequence-change, and
  seek/flush tests; make no new behavior rule without a failing fixture.
- [x] **D6 - Hardware-only regression matrix.** Validate Media3, legacy Exo,
  and their GSY delegates using MPEG-2 1080i/720p59.94, H.264, HEVC, TS
  discontinuity, seek/flush, file transition, and HOME/return cases. Commission
  MediaTek Fire TV first; NVIDIA and ONN/Amlogic sign-off require those physical
  devices and must not block unrelated work. MediaTek `.25` passed all four
  backend/delegate codec matrices, the four-configuration seek/FLUSH matrix,
  and all four HOME/return lifecycle cases. NVIDIA Shield Tube and ONN v1
  physically passed the same codec/delegate coverage; isolated resume reports
  close rows affected by stock-server control timeouts in their original runs.
  A guarded test-only stale-StopPopup cleanup now waits until the replacement
  MediaFile is current and active, so it cannot close the SageMC context before
  Watch. Unsupported profiles remain explicit skips rather than false passes.
- [ ] **DEVICE-001 - Android tablet compatibility report.** After D6, diagnose
  the reported Android 8.0.0 tablet (Linux 4.4.23+) intermittent MPEG-2/H.264
  Colossus 2 capture stutter and Dolby Digital audio silence. Capture codec,
  audio-route/passthrough, renderer, frame-cadence, and diagnostic-bundle
  evidence; apply only capability-based corrections that preserve TV devices.
  Separately verify why the APK cannot install/open on an Android 5 Galaxy Tab,
  document the actual minSdk/API or ABI constraint, and either restore safe
  compatibility or provide an explicit supported-version/install message. Do
  not claim a physical pass without the affected hardware or its diagnostics.
  - [x] Built-APK inspection proves `minSdkVersion=23`, target API 36, and both
    ARM32/ARM64 ABIs. Documentation now states Android 6.0+ accurately; Android
    5 API 21/22 is an explicit unsupported install boundary.
  - [x] The diagnostic contract documents the exact device, decoder, cadence,
    AudioTrack, passthrough, underrun, buffer, and current-video evidence an
    Android 8 reporter can export without ADB.
  - [ ] Obtain the affected Android 8 tablet bundle and physically resolve or
    characterize its intermittent cadence and Dolby Digital silence.

## 3. Final physical playback matrix and release gate

Run this only after the targeted ONN work and Kodi/VLC/FFmpeg-derived player
corrections are stable. Focused smoke tests still run immediately after each
fix; this phase is the single comprehensive rerun that prevents repeated full
DVD/video matrices during implementation.

- [x] **MATRIX-001 - ONN v1 video matrix.** Run the consolidated video matrix on ONN v1: generated MPEG-2/AC3/CC,
  long OTA MPEG-2 TS, UK H.264/AC3/DVB/Teletext samples, H.264/H.265 fixtures,
  TS discontinuity/file transition, and stock-server MKV seek cases. Cover
  Media3, legacy ExoPlayer, IJK, and applicable GSY delegates with native
  hardware video decoding; verify startup, sustained cadence, audio, captions,
  pause/resume, seek/FF/RW/Stop, transition, and HOME/return.
  - [x] Taskmaster filtered UK HD H.264 sample: 5 hardware configurations and
    30 control recoveries passed on ONN v1.
  - [x] Breakfast UK HD H.264/AC3/multi-subtitle sample: 5 hardware
    configurations and 30 control recoveries passed on ONN v1.
  - [x] Classic Holby City UK SD H.264/multi-stream sample: 5 hardware
    configurations and 30 control recoveries passed on ONN v1.
  - [x] Generated 1080i MPEG-2/AC3/CC fixture: ONN v1 used the Amlogic
    hardware MPEG-2 decoder and passed 5 configurations/30 control recoveries.
  - [x] Long OTA MPEG-2 recording matrix executed: all 30 operations recovered
    with no crash/watchdog, while 4 large comskip-right recoveries consistently
    exposed a 6.1-6.6 second Pull/extractor read-amplification bottleneck.
  - [x] Correct or explicitly bound the long-recording large-jump recovery
    bottleneck, then rerun its focused hardware gate. The final focused ONN
    Media3 run recovered hardware video plus audio in 7.030 seconds with one
    retained decoder initialization/no release and no crash/watchdog. Its
    40.6 MiB/155-read delta spent 6.203 seconds in Pull reads, explicitly
    bounding the remaining stock MediaServer/MPEG-TS timestamp-search cost.
  - [x] Stock-server Beauty and the Beast MKV seek case: 5 ONN hardware
    configurations and 20 startup/seek/pause recoveries passed.
  - [x] Stock-server Lion King MKV seek case: legacy/IJK/GSY rows passed and a
    focused warm Media3 retry passed startup plus all seek/pause checks.
  - [x] **MATRIX-001-MKV3 - Stock Scream MKV.** Run the enabled third
    independently authored MKV through the stock-server hardware Pull
    startup/timeline/seek/pause gate so the MKV conclusion is not based on two
    files alone. Seven configurations passed startup and all 28 absolute-seek,
    forward/backward-skip, and pause/resume recoveries with no slow recovery,
    watchdog, crash, startup failure, or infrastructure failure.
  - [x] Validate cold-storage/HDD-spin-up startup tolerance; retain the first
    Lion King Media3 timeout as transient evidence until that gate is proven.
    - [x] Add an adaptive per-server/per-file first-use allowance: keep a
      normal first startup as measured, but discard/retry only a proven slow
      first startup; refresh a sliding recent-use timeout after every success.
    - [x] Physically prove normal-first, slow-discard/retry, and recent-file
      paths on ONN v1 without rerunning the completed full matrix. Beauty and
      the Beast Media3 hardware Pull passed at 7.354 seconds as the retained
      first result, passed the recent-file path, and passed a forced-threshold
      discard/retry at 7.488/7.706 seconds with two sliding-TTL refreshes.
- [x] **MATRIX-002 - ONN v1 DISC matrix.** Run the consolidated DISC matrix on ONN v1: authored diagnostic DVD plus
  representative menu and menu-less physical DVDs. Verify menu navigation,
  main-title selection, audio/subtitle changes, chapters, seek, sustained
  cadence, teardown, and native compatibility fallback.
  - [x] Authored diagnostic DVD on stock `.175`: normal Web MediaFile Watch,
    hardware MPEG-2, root/submenu/title navigation, chapter/audio/subtitle
    controls, pause/resume recovery, 69 decoded SPU events with zero malformed
    packets, screenshots, and complete Stop teardown passed.
  - [x] Representative physical menu DVD on stock `.175`: three focused
    SCOOBY runs passed 22 remote commands, root/title-menu/return transitions,
    hardware MPEG-2 plus AC-3 rendering, decoded menu SPU with zero malformed
    packets, and complete teardown. ONN ADB screenshots do not capture the
    separate video Surface, so the protocol/decoder/menu-overlay counters are
    the retained device evidence rather than interpreting black ADB frames.
  - [x] Menu-less ALADDIN motion/seek/sustained-cadence gate on stock `.175`:
    stock Sagex positioned the title at 483,583 ms for the 480,000 ms target;
    FF/REW, chapter, pause/play recovery, two-minute cadence, hardware MPEG-2,
    AC-3 rendering, and STOP teardown passed.
  - [x] Consolidate native fallback and teardown evidence and close MATRIX-002.
    Every retained authored/physical run used the stock `.175` Web/Sagex Watch
    path and native compatibility fallback, and ended with both `playerActive`
    and `dvdSessionPending` false.
---

## FEATURE-EXPANSION BOUNDARY

The commissioned foundation above is complete. New features must retain the
established player, lifecycle, caption, fullscreen, compatibility, and teardown
gates. External-device-only checks block release sign-off, not unrelated work.

## 4. GitHub source and APK refresh after active phases

Amazon Appstore and Google Play publication are not part of the current release
scope. Do not commit, push, tag, or replace a GitHub release until the user
explicitly approves publication after the active hardware phases.

- [x] **GH-001 - Release documentation and metadata.** Update `CHANGELOG.md`, `HANDOFF.md`, durable compatibility/diagnostic
  documentation, task state, release metadata, and complete manifests from the
  final validated tree. Update the GitHub issue/reporting instructions and
  screenshots to show Test Current Video and diagnostic ZIP export/upload.
- [x] **GH-002 - Independent release verification.** Build and verify the clean APK, source archive, handoff/update bundle,
  checksums, and GitHub workflow from an independent checkout.
- [x] **GH-003 - Approved GitHub publication.** After explicit approval only, create logical commits, push `main`, create
  the release tag, attach the versioned APK/source artifacts, and verify the
  public download hashes and GitHub Pages latest-APK redirect.
- [ ] **GH-004 - Temporary forum migration post.** After GH-003 and the updated
  GitHub issue workflow are publicly verified, create a ready-to-paste Markdown
  post explaining that playback discussion and issue submission should move to
  GitHub Issues. Include the Test Current Video and diagnostic-export process
  with screenshots. Store it only under the workspace `artifacts/temp`
  directory so it is excluded from project source/releases and can be deleted
  after use.

## 5. Deferred cross-device matrix after GitHub release publication

- [ ] **MATRIX-003 - Cross-device affected matrix.** Do not start until GH-001,
  GH-002, and GH-003 are complete: the current GitHub source/APK release is
  committed and published, repository updates are verified, and the issue and
  diagnostic-submission pages are updated. Then run only the affected
  release rows rather than repeating every completed matrix: NVIDIA Shield Tube
  DVD cadence/audio and UK H.264/AC-3/subtitles; ONN 4K Pro DVD motion plus
  captions/seek; and one MPEG-2/AC-3/CC, DVD, seek, and lifecycle reference
  smoke on the non-Pro Fire TV. Expand only when a shared-code regression or
  failure requires it.
- [ ] **MATRIX-004 - Final cross-device evidence synchronization.** Update
  compatibility, diagnostics, changelog, handoff, exact manifests, and task
  state from that deferred physical evidence.

## 6. Requires hardware, fixtures, credentials, or user decisions
- [ ] **EXT-001 - API 36 Back migration.** Replace the temporary API 36 predictive-Back opt-out with callback
  handling physically tested on API 33+, then remove the restricted-
  resizability compatibility bridge before targeting API 37. The API-25 legacy
  Back path passes.
- [ ] **EXT-002 - Blu-ray/BDMV commissioning.** Commission Blu-ray/BDMV main-title playback after a legal valid physical
  fixture becomes available. Until then the result is explicitly `SKIPPED` and
  is not a failure of completed DVD Native/Hybrid/MIM behavior.
- [ ] **EXT-003 - Secure decoder/DRM validation.** Exercise secure-decoder/DRM selection with an authorized Widevine or
  PlayReady test asset. A clear FFmpeg fixture cannot prove secure MediaCodec
  behavior.

## 7. Deferred Amazon Appstore and Google Play work

- [ ] **STORE-001 - Production APK inspection.** Run the production-APK inspector with the approved production signing
  key. Debug/MCP code, disallowed permissions, actions, and secret scans pass;
  the local release candidate correctly fails because it uses the development
  certificate.
- [ ] **STORE-002 - Signing ownership.** Approve the upload/signing key, version policy, and Play App Signing
  ownership. The application ID `opensagetv.vibe.miniclient` and visible name
  `OpenSageTV Vibe` are already selected and enforced by tests.
- [ ] **STORE-003 - Console and listing readiness.** Complete Play Console readiness: Android TV opt-in, privacy-policy URL,
  Data Safety declaration including third-party SDK behavior, app-access
  instructions, high-resolution TV screenshots/listing assets, content rating,
  target audience, and required testing track. Validate against the official
  [Android TV quality requirements](https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality)
  and [Google Play Data Safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469).

## 8. Future store release and publication after prerequisites

- [ ] **STORE-004 - Production store bundle.** Convert the validated development-signed release-candidate AAB into the
  approved production AAB and add its strict signing/content gate. Debug and
  release-candidate AAB generation, bundletool validation, universal APK-set
  generation, guarded physical debug installation, and preserved app data
  already pass. Google Play no longer accepts APK-only Android TV releases.

## Checklist change ledger

| Revision | Date | Change |
|---|---|---|
| 18 | 2026-09-13 | Completed GH-003: published three logical commits through `71401a4`, observed successful repository source-contract, build, status, and Pages checks, created the public `v0.5.91` release with bullet-formatted notes and five APK/source/hash/manifest assets, downloaded and re-hashed the public APK, and verified that the Pages endpoint returns HTTP 200 and resolves `v0.5.91`. |
| 17 | 2026-09-13 | Completed GH-002: the primary tree passed full validation, a clean 60-task APK build, strict APK inspection, and handoff/GitHub bundle creation. A fresh Windows-extracted, Git-less source archive then passed its 1,423-file manifest, 537 scaffold/static tests (one Git-metadata-only skip), 84 MCP tests, Core JUnit, full validation, and an independent clean 60-task APK build. The gate also fixed parent-Git index inheritance in extracted manifest checks. |
| 16 | 2026-09-13 | Completed GH-001: consolidated the Unreleased changelog, recorded ONN/DVD and exact-path evidence, published cropped generated-fixture diagnostic screenshots and GitHub issue instructions, verified `VERSION=0.5.91`/`REQUIRES_BUILD=true`, scoped raw ADB to the selected device, and regenerated the complete 1,423-file manifest after 536 static/scaffold, 84 MCP, and Core JUnit tests passed. |
| 12 | 2026-09-13 | At user direction, kept MATRIX-002 active through ONN closure, moved GitHub documentation/issue-submission/release preparation immediately afterward, and deferred a reduced affected-row MATRIX-003 plus final cross-device synchronization until after the GitHub refresh. |
| 13 | 2026-09-13 | Made the ordering gate explicit: MATRIX-003 cannot start until the current GitHub release, commits, repository update, and issue/diagnostic submission page updates are complete. |
| 14 | 2026-09-13 | Closed MATRIX-002 from ONN v1 stock-server evidence: authored DVD, physical SCOOBY menus, exact 8:00 ALADDIN seek/cadence/control recovery, native fallback, and teardown all passed. GitHub release work is now the active phase. |
| 15 | 2026-09-13 | Added GH-004 after GitHub publication: a temporary, screenshot-backed forum post under workspace artifacts/temp that directs playback discussion and diagnostic submissions to the new GitHub issue workflow. |
| 11 | 2026-09-13 | Connected the DISC harness to mode-discovered TOML fixtures with stock-Web versus Vibe exact-path selection, added the representative physical-menu DVD fixture, and completed the authored-DVD ONN/stock-server sub-gate. |
| 10 | 2026-09-13 | Completed MATRIX-001 after the enabled Scream MKV was discovered by mode and passed seven stock-server Pull configurations plus 28 control recoveries on ONN v1 with no slow/error/watchdog result. |
| 9 | 2026-09-13 | Explicitly bounded MATRIX-001 long-recording Comskip recovery with a focused physical ONN run (7.030s, retained hardware decoder, no crash/watchdog, Pull read wait dominant) and added the configured third MKV closure case as MATRIX-001-MKV3. |
| 8 | 2026-09-13 | Completed the adaptive cold-storage gate on ONN v1: normal-first, recent-file, and forced slow-discard/retry paths all produced healthy hardware A/V with zero infrastructure/startup failures; reports now count every sliding-TTL refresh. |
| 7 | 2026-09-13 | Completed FOUND-011: replaced role-specific fixture keys with a validated multi-fixture/multi-mode registry, added mode-driven discovery, server selection/Web/extension metadata, device/server STV expectations, expected media traits, and configurable adaptive-storage defaults. |
| 6 | 2026-09-13 | Completed D6 physical hardware validation. Non-Pro MediaTek passed the full Media3, legacy Exo, GSY/Media3, and GSY/legacy-Exo codec/transition matrices plus seek/FLUSH and HOME/return gates. Shield Tube and ONN v1 codec/delegate coverage passed through full and focused-resume evidence. FFmpeg HDMI review found the matrix-only SageMC stopped-popup race; cleanup is now opt-in and guarded by the replacement MediaFile ID plus active player. |
| 5 | 2026-09-12 | Completed D5 instrumentation and fixtures: both Exo generations expose bounded audio underrun/output error plus track/format transition events, and FFmpeg generation now proves TS timestamp discontinuity, PMT audio-type change, and MPEG-2 sequence-resolution change fixtures. Forty-seven focused tests, fixture generation, Android compilation, and diff checks pass. |
| 4 | 2026-09-12 | Completed D4: only a classified fatal runtime video-codec failure can exclude the selected decoder for the current playback session and trigger one position-preserving renderer/codec reconstruction; static/Core/Android compilation gates pass and physical proof remains in D6. |
| 3 | 2026-09-12 | Marked D3 complete after Core telemetry tests, 40 player-backend static tests, and Android TV Java compilation passed; stats, MCP, Test Current Video, and diagnostic exports share the bounded telemetry. |
| 2 | 2026-09-12 | Added DEVICE-001 at the user-requested end of active work for Android 8 tablet MPEG-2/H.264 cadence, Dolby Digital silence, and Android 5 install compatibility. |
| 1 | 2026-09-12 | Converted the volatile active-only backlog into a stable, ID-based master checklist at the user's request; retained completed foundation work, marked D1/D2 complete from passing code/static/build evidence, and preserved every active/deferred task in its existing dependency order. |
