# OpenSageTV Vibe Android Client tasks

> **Pre-commit task maintenance:** Immediately before every repository commit, move
> completed `[x]` items out of active sections and into
> `## Checklist change ledger`. Preserve IDs, evidence, and context; never
> discard completion history. Active sections contain unchecked work only.

This is the only authoritative task backlog and durable master checklist for
this subproject. Active sections contain unchecked work; completed work remains
checked in `## Checklist change ledger`. Release-relevant evidence is also
recorded in `CHANGELOG.md` and `HANDOFF.md`.
Workspace-wide dependencies and release ordering may also be mirrored in the
parent workspace `task.md`, but Android-only work must remain current here so
the repository can be developed independently of Codex.

Checklist revision: **306** (2026-10-08)

## Stable checklist rules

- Every task has a permanent ID. Status updates change only `[ ]` to `[x]`;
  immediately before commit, checked items move to the change ledger.
- Completed tasks are never silently removed. They remain checked in the
  change ledger. A user-requested removal is retained there with its former ID
  and reason.
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

## Suggested execution order (avoid repeated matrices)

This ordering supplements the task sections below; it does not move, rename,
duplicate, or replace their stable tasks or acceptance criteria. Review this
section whenever a task is added, completed, removed, deferred, unblocked, or
changes dependencies. Change it only when the dependency graph or matrix timing
actually changes.

Order reviewed against checklist revision **306** (2026-10-08). A completed
sub-gate must disappear from the next-action wording even when its parent task
remains open; the next unfinished gate takes its place.

During implementation, run only focused tests and physical smoke gates affected
by each change. Broad compatibility matrices run once after all release-bound
client changes are stable. If a late shared-code change invalidates evidence,
rerun only the affected rows and final matrix portion, not every completed
device/player combination.

1. **ONN-003** - proceed when affected-device diagnostics reproduce the
   persistent OSD; complete any resulting shared correction before code freeze.
2. **DEVICE-001** - proceed when the affected tablet evidence/hardware exists;
   complete any release-bound capability correction before code freeze.
3. **EXT-001/002/003/005/006/007** - retain as conditional future work until
   each required device, legal fixture, secure asset, or reproduced defect
   exists; these do not block unrelated release work.
4. **STORE-001 through STORE-004** - perform production signing, inspection,
   listing, and publication only after external ownership/prerequisites are
   approved.

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

**Shared physical A/V evidence for AUDIO-001, AUDIO-005, AUDIO-006, and
DVD-001:** a baseline-calibrated Logitech C920 (`V-U0028`, P/N `860-000334`)
may record the displayed calibration fixture and the final TV/receiver speaker
output in one USB audio/video capture. Its 30 fps video provides approximately
33 ms measurement resolution, which is sufficient to prove or reject the
reported roughly 500 ms route delay and other large regressions. Pair every run
with the matching diagnostic bundle and ADB audio-route/EDID state. This webcam
evidence does not certify 25 ms adjustment accuracy or 50/59.94 fps cadence and
does not replace HDMI/client telemetry for decoder, frame, seek, or protocol
results.

The reproducible physical path now includes matching embedded H.264/AC-3 and
stock-server 1080i MPEG-2/AC-3 common-clock fixtures, smooth outlined-ball
motion with a measurable impact hold, direct C920 capture without Logi Capture,
and an offline analyzer that
reports signed ball-impact/click offset from the same Matroska clock. Generator
self-tests measure the authored fixtures within one capture frame. Direct C920
video and microphone capture now passes together at 1920x1080/30 through the
Vibe FFmpeg DirectShow path. Opening the DirectShow graph was reapplying stale
Zoom 144, Pan 1, and Tilt -10 controls after an earlier reset, making a complete
physical view look cropped. The capture helper now resets Zoom/Pan/Tilt both
before opening and again after the graph owns the camera. All four authored
registration corners are visible and the analyzer accepts the resulting
complete-TV recording as primary physical evidence.

The 2026-10-03 cropped diagnostic run nevertheless proves the implementation
direction and scale on Pro `.29` / stock `.175`: direct Media3 Pull measured
`-416.667/+410.000 ms` for requested `-400/+400 ms`, retained `+389.333 ms`
after pause/resume and `+382.667 ms` after seek; GSY Media3 measured
`+430.000 ms`; direct legacy Exo Pull had a 1.016 endpoint-to-endpoint slope;
and GSY legacy Exo measured `-389.333 ms`. The embedded test measured
`-370/+410/+4034 ms`, decoded PCM zero remained stable, and HOME stopped later
clicks. Legacy Exo Dynamic negotiated actual Push and correctly reported its
live offset change as deferred. Direct Media3, direct legacy Exo, GSY Media3,
and GSY legacy Exo then each passed a real stock-server `2.1 -> 5.1` live
source/channel replacement with advancing hardware A/V, visible full-screen
playback, no crash signature, and all 234 settings restored. The same four
backends passed the generated PMT/audio-format transition fixture; the focused
matrix now ends after its selected evidence instead of allowing unrelated AV1
or malformed-input rows to turn that result into a false overall failure. The
Pro HDMI audio path remains the closure target; the non-Pro zero case is not a
prerequisite for these Pro-only audio tasks. Selectable-track lifecycle is complete on all four Exo
paths: the generated dual-AC-3 fixture switched 5.1/stereo in both directions
while retaining encoded `-400 ms`, and the UK Breakfast AC-3/MPEG-L2 pair
separately passed decoded-mode fallback through Media3's FFmpeg audio decoder.

A later same-day recheck identified and fixed the apparent framing failure.
DirectShow changed the camera controls only after FFmpeg opened the graph; an
in-graph reset restored Zoom 100, Pan 0, and Tilt 0. A full-frame zero-offset
capture measured a stable `+611.333 ms` physical route baseline. Moving the
same open encoded calibration dialog to `-400 ms` measured `+251.333 ms`, or a
`-360 ms` baseline-corrected effect with a `40 ms` residual. This proves the
offset direction and route-scale behavior within approximately one 30 fps
camera frame. AudioFlinger also showed an active DIRECT AC-3 output thread on
the Pro: `FORCE_NONE` does not disprove encoded passthrough. The earlier cropped captures and
endpoint stress remain diagnostic history rather than primary evidence.

The user confirmed the centered C920 was filming the original TV/surround
route. Full-frame stock `.175` fixture captures now pass the four-corner gate
for direct Media3, direct legacy Exo, GSY Media3, and GSY legacy Exo at
`0/-400/+400 ms` encoded offsets. Direct Media3 decoded PCM zero and a live
encoded zero reset also passed. At the PBS NewsHour 23-minute speaking
interval, source-matched webcam speech yielded about `662 ms` audio-after-
picture at encoded zero, `288 ms` at encoded -400, `119 ms` at encoded -650,
and `730 ms` at PCM zero. That matcher has 200 ms temporal-video resolution,
so the authored fixture remains the primary physical timing gate. See
`docs/AV_SYNC_PHYSICAL_GATE.md`.

- [ ] **DEVICE-001 - Android tablet compatibility report.** After D6, diagnose
  the reported Android 8.0.0 tablet (Linux 4.4.23+) intermittent MPEG-2/H.264
  Colossus 2 capture stutter and Dolby Digital audio silence. Capture codec,
  audio-route/passthrough, renderer, frame-cadence, and diagnostic-bundle
  evidence; apply only capability-based corrections that preserve TV devices.
  Separately verify why the APK cannot install/open on an Android 5 Galaxy Tab,
  document the actual minSdk/API or ABI constraint, and either restore safe
  compatibility or provide an explicit supported-version/install message. Do
  not claim a physical pass without the affected hardware or its diagnostics.
  - [ ] Obtain the affected Android 8 tablet bundle and physically resolve or
    characterize its intermittent cadence and Dolby Digital silence.

## 3. Final physical playback matrix and release gate

Run this only after the targeted ONN work and Kodi/VLC/FFmpeg-derived player
corrections are stable. Focused smoke tests still run immediately after each
fix; this phase is the single comprehensive rerun that prevents repeated full
DVD/video matrices during implementation.

## FEATURE-EXPANSION BOUNDARY

The commissioned foundation above is complete. New features must retain the
established player, lifecycle, caption, fullscreen, compatibility, and teardown
gates. External-device-only checks block release sign-off, not unrelated work.

## 4. GitHub source and APK refresh after active phases

Amazon Appstore and Google Play publication are not part of the current release
scope. Do not commit, push, tag, or replace a GitHub release until the user
explicitly approves publication after the active hardware phases.

## 4A. Active post-release feature work

DVD-002 is complete under the user's revised functional acceptance
(playback and skipping, not exact destination or frame-exact HD200 parity).
Its observations and accepted limitations remain in revision148 of the
checklist change ledger and artifacts/results/DVD-002.


## 5. Cross-device evidence synchronization

MATRIX-003 affected physical rows and MATRIX-004 evidence sync are complete;
stable tasks and detailed
device/result context are retained in the checklist ledger and compact reports.

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
- [ ] **EXT-005 - Measure legacy Push-buffer negotiation.** Deferred; do not
  start until a reproducible Android Push playback defect provides a user-
  visible reason to change the current bounded behavior. Compare the absent
  `PUSH_BUFFER_LIMIT` baseline with negotiated limits up to stock Core's
  effective 128 KiB clamp. Measure write cadence, free-space replies, first-
  frame time, underruns, memory use, seek/flush recovery, live transitions and
  fallback on an unmodified stock server. Do not advertise the HD300's 512 KiB
  value without evidence that the Core clamp and Android path benefit.
- [ ] **EXT-006 - Expand malformed-caption regression fixtures.** Deferred; do
  not start until a reported CEA, Teletext or DVB parsing failure supplies a
  reproducible malformed/truncated pattern, or caption-parser code is otherwise
  changed. Add generated, non-copyrighted corrupt-packet fixtures for the
  affected parser, prove bounded rejection without crash/stall, and rerun only
  that parser's normal stock-server caption gates. Do not copy firmware/vendor
  parser behavior.
- [ ] **EXT-007 - Re-evaluate legacy hardware-output controls.** Deferred and
  currently rejected; do not start without an independently reproduced Android
  problem that existing player/OS controls cannot solve. If triggered, evaluate
  HDMI/HBR audio, RC5, advanced deinterlacing, aspect/output mode or unified-YUV
  behavior individually. Never advertise Sigma/HD200/HD300 identity or unlock
  `isStandaloneMediaPlayer()` wholesale; each accepted capability requires its
  own stock-server compatibility and physical-device gate.

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

| 306 | 2026-10-08 | [x] RELEASE-001 published v0.5.101 at a541d0f4; all 12 existing Vibe repositories pushed with exact-HEAD green CI. Independently downloaded four public assets and verified hashes/source manifest. Both orders306 remove completed publication; remaining plugin/device limits stay open. |

- [x] **RELEASE-001 - Approved GitHub refresh and v0.5.101 sideload release.**
  User approved related repository source updates and a new Android release.
  Original acceptance: reuse completed per-device matrices with honest limits;
  run affected player/MCP/build/package checks and required repository CI;
  preserve Dev signer/package/settings, increment versionCode, package exact
  clean committed source/APK, pilot-gate each push, publish bullet-point notes
  and independently verify public hashes. No plugin runtime/catalog release,
  historical archive publication or unapproved companion prototype publication.
  Closed2026-10-08: 12 existing repositories green; v0.5.101 published at
  a541d0f4d259d1ff9ec58e65288bc8240081c0c8, versionCode2101110, unchanged
  Dev signer. APK SHA2566777841db4782cc9728090f5bc52945a89de03d6cb30de55952efb89764012f7.
  Four public assets match GitHub digests/checksums; source manifest1603 files
  matches the clean release commit. CI749 tests/one skip; JVM156/MCP161 pass.
  Non-Pro25/stock175 generated Pull startup/seek and corrected pause-only gate
  pass; GetPlaybackRate was a faulty pause oracle, not a player defect.
  Both groups restore113 preferences/original power. Final Android Home,
  forceStopped=true and zero server contexts/clients; pending pid is not proof
  of playback. No server restart/HDMI/speaker measurement or full matrix rerun.
  Compact evidence: artifacts/results/RELEASE-001/qualification.json.
  Completed raw/staging retires recoverably to workspace deleteme; canonical
  release assets remain. This closure is documentation-only after the release
  snapshot; do not move the published tag or rebuild an identical APK.

| 305 | 2026-10-08 | User approved RELEASE-001: related source updates and v0.5.101 Dev sideload publication take priority over recording-blocked companion work. Preserve completed matrices and open limits; affected tests and required CI only. Both order stamps305. |

- Revision305 (2026-10-08): user-approved RELEASE-001 moves source/APK
  publication ahead of recording-blocked companion work. Existing completed
  device matrices stay closed; only affected release and required CI checks.

- [x] **Revision 304 / DEVICE-002, TABLET-MIM-001, GSY-CAP-001 closure,
  2026-10-08.** COMPLETE_WITH_EXPLICIT_LIMITS under the user's best-effort
  disposition rule. The historical in-progress notes below are superseded by
  this final record, not removed. Installed c62911ae76be7b3e861e0050af4cc6ea8a3e72ba9d0a2b0e94032f3f8839bf4c
  is independently stream-hashed; native rows retain their original per-row
  build provenance. Both GSY delegates pass stock175 real Direct-only recovery
  61.328/64.402s,232 owned 2s captions/seek/pause86.354/77.837s, and optional
  HTTP unavailable ordinary Fixed69.333/68.573s. Media3/legacy startup,
  nonzero bookmark/latest-intent/canceled-ticket, playing/paused public API,
  real H.264 output and final recovery Seek gates pass.
  Fresh232 VAAPI job proves hardware decode+encode; no GPU-load measurement.
  CPU175 500ms CEA sustained readability is NOT_WORKING, exact cause unproven;
  readable 2s controls pass175/232. Original UK IJK picture remains NOT_WORKING,
  native MPEG-2 DVD safely unsupported, optional transformed title controls
  pass, physical speaker A/V sync UNMEASURED. No false full-player parity.
  Final15 prefs/group, power7/600000 and borrowed175 caption-listenerfalse
  restored; both servers activeJobs/contexts empty, protected Core/root FFmpeg
  hashes unchanged. Temporary232 import/shared generated file retired, case
  disabled.366 owned completed/redundant outputs and staging324,482,575bytes
  moved recoverably to rootdeleteme; minimum uncorrected stress/IJK evidence
  and persistent warm cache retained. No app clear/key reset, publication or
  unrelated matrix rerun. Compact artifacts/results/DEVICE-002/matrix.json
  and owned-watch-recovery.json retain exact observations/provenance.
 12 recovery/13 session/2 resolver JVM,65 selected source/backend/lifecycle/
  order,15 MCP adapter tests plus stock Java8/JDK8/11/17 plugin/runtime gates
  pass; final source validator/manifests are the closure check.

- [x] **DEVICE-002 - Samsung Tab S6 Lite client commissioning.** Added at the
  user's request2026-10-07, address192.168.10.51, ignored alias tab-s6-lite.
  Full device matrix now explicitly authorized; DVD-003 is complete.
  Verify actual model/OS/API/ABI via authorized persistent container
  ADB and timeout0; preserve existing app data and settings. If fresh, require
  normal manual SageTV setup and retain its actual generated client ID, not
  borrowed DEV001. Install guarded current Dev APK via update only; stock175
  first, hardware MPEG-2/H.264/HEVC and decoded AC-3 audio, CEA/Teletext/DVB
  where supported, touchscreen/navigation/layout, seek/pause/HOME/teardown and
  native DVD smoke. Optional232 owned Copy/Transcode only after baseline, with
  actual GPU proof (175 has no usable hardware transcoding). Fix proven client
  failures and rerun affected rows only, no full historical-device matrix.
  This newer tablet cannot close DEVICE-001's unrelated Android8 report.
  Readiness2026-10-07 PASS: default5555 refused; user supplied TLS connection
  port42491 and pairing port45773. Paired through persistent container ADB,
  connected actual SM-P610/gta4xlwifi, Android13/API33, ARM64+ARM32, verified
  adb_allowed_connection_time=0. Initial device probe found no Dev package;
  subsequent guarded installation and normal user setup are complete.
  Pairing code is not retained.
  Explicit serial forwarding is corrected in both wrappers/eight tests;
  never count `.25` as `.51` proof. Refresh the dynamic TLS port if it changes.
  No HDMI capture on this tablet. Verify real app compositing using streamed
  ADB screenshots and bounded screen recordings/mirroring where needed,
  alongside actual Surface, decoder/render/audio counters, server API state
  and crash/lifecycle probes. Speaker audibility/physical A/V sync cannot be
  certified from these alone; label those unmeasured, not PASS. Preserve other
  device matrix closure; record tablet rows under DEVICE-002 and add its
  compatibility-matrix row only as each gate is actually measured.
  Current381 guarded install and streamed installed-APK hash pass; normal
  launcher screenshot2000x1200 works. Read-only inventory29 video/35 audio
  entries advertises hardware AVC/HEVC but no Android MPEG-2 decoder; require
  truthful unsupported/safe handling versus separately measured alternate
  decode/transcode paths, not a fake hardware PASS. User completed175/232
  setup; current normal232 Main Menu/client identity verified.14 preferences
  checkpointed, generated identity saved privately, never DEV001. Manual awake checkpoint active;
  original7/2147483647 power values recorded exactly, nested gates borrow it.
  Current67e07f95 installed base.apk stream-hash independently matches the
  built candidate (55019483bytes). Stock175 all40 available positive hardware
  codec rows pass: Media3 A/B257.722/172.166s, Legacy321.802/174.576s,
  GSY/Media3319.174/174.533s and GSY/legacy450.721/174.871s. Actual Exynos
  decoders/requested delegates required. Legacy/GSY burned labels reviewed;
  short EOF rows can show the normal STV card/preview, not unobstructed full
  screen certification. Supplementary Media3 visual controls pass215.099s on9d85.
  IJK controls77.527s/lifecycle83.771s passed only their old clock/control
  oracles; settled physical software capture now proves blank video with
  Exynos MediaCodec errors. These are not complete IJK playback passes.
  TABLET-IJK-001 is closed COMPLETE_WITH_EXPLICIT_LIMITS; other four backend
  output rows remain valid. Its latest644596ae clean same-coded MP4 lifecycle
 126.416s has independently real HOME/user-resume/replay pictures; original
  UK hardware startup remains NOT_WORKING, not a blanket IJK backend failure.
  Four extractor-backed stock175 lifecycle paths pass HOME/return, recreated Surface,
  explicit user-pause preservation, exact-source replay and teardown:
  Media3 75.664s, Legacy77.793s, GSY/Media379.293s,
  GSY/legacy80.346s. Each restores all15 current preferences and borrows the
  manual keep-awake owner. Caption correction/Copy continuity and native DVD
  safe unsupported handling are complete, as are optional232 transformed
  chapter/pause/cadence and tablet posture/touch-layout gates. Native MPEG-2
  DVD menus/SPU are unsupported on this device, not a hardware playback PASS.
  Full owned Transcode remains; this is partial DEVICE-002, not closure.
  - [x] Stock175 native hardware codec matrix across available Media3, legacy
    Exo, IJK and commissioned GSY delegates; distinguish unsupported codecs
    from safe fallback, prove real selected hardware and decoded AC-3 output.
  - [x] Stock175 seek/pause/STOP-rewatch/HOME/user-pause/teardown controls,
    CEA/Teletext/DVB and subtitle authority with visibly readable output.
  - [x] Optional232 owned Copy/Transcode and captions/controls/fallback,
    with independent real GPU decode/encode and zero-orphan evidence.
  - [x] Synchronize final compact matrix provenance, restore settings/power
    and retire completed raw evidence after remaining investigations.





- [x] **GSY-CAP-001 - Negotiate selected GSY delegate capabilities.**
  Child/dependency of DEVICE-002/TABLET-MIM-001. Real175 GSY/Media3 failure
  recovery139.247s lacked plugin custody: AndroidMiniClientOptions treats
  GSY as an unknown IJK-like player and advertises all codecs, so MPEG-2-less
  hardware incorrectly appears capable of the native Pull fallback. Resolve
  active backend/GSY engine to Media3 or legacy Exo for codec/audio/container
  negotiation; keep IJK behavior and explicit user overrides unchanged. Auto/
  System use conservative platform/Media3-fallback capability, not invented
  software support. Add resolver/ownership/expiry tests and verify both GSY
  delegates real175 Direct-failure recovery, missing-plugin/off fallback and
  affected232 owned caption/seek controls. Do not repeat other native matrices.

- [x] **TABLET-MIM-001 - Owned Transcode input capability on MPEG-2-less
  clients.** Child of DEVICE-002. On232/current9d85, Direct mode is ready but
  native VIDEO_CODECS omits MPEG-2, so Core sends Push rather than an original
  Pull path; no fresh MIM job starts and tablet gets audio-only MPEG-2.
  Verify against stock MiniPlayer's video/audio support checks. Advertise
  MPEG-2 source acceptance only for negotiated ready Transcode with native
  H.264 output support; do not change actual decoder inventory or Copy/off/
  missing-plugin/native-MPEG2 clients. Physically prove real owned H.264,
  fresh232 GPU decode/encode, readable CEA and seek/pause/STOP cleanup;
  gate creation failure/unsupported local Pull fallback without a new black
  screen. Use stock175 plugin path too, noting its CPU/no-GPU limit. No Core
  jar/plugin protocol changes; only affected negotiation/fallback rows rerun.
  Progress: source-acceptance correction starts actual owned H.264/Exynos with
  readable CEA, FF/REW and pause/resume on232. Settled gate still FAIL because
  a later replacement returns HTTP502; no all-controls/GPU-load closure yet.
  Real Direct-only creation fault exposes cached native socket-reconnect
  capabilities and audio-only Pull; full-session capability refresh is being
  tested. Native decoder inventory and user settings remain unchanged.
  Current decision: withdraw invented MPEG-2 source capability and the failed
  fresh-session experiment. Socket reconnect caches capabilities; fresh-session
  replacement loses the watch session. Guard opted-in Transcode before Core
  negotiation when native H.264/MPEG-2 SD/HD Pull fallback is unavailable;
  report unsupported_video_stock_fixed and use ordinary Fixed without changing
  saved preference or Copy. Physically verify this safe boundary on175/232.
  Full owned Transcode on MPEG-2-less clients remains open: a negotiated plugin
  recovery contract using stock public API/watch state is needed before claiming
  support. Do not reintroduce the rejected capability advertisement or require
  MCP as a normal runtime dependency. Restart502 is separately unproven.
  Linked FFmpeg-plugin MIM-DIRECT-005 now owns that recovery-contract work.
  Source review confirms stock Pull codec gate and public GetUIContextNames/
  GetCurrentMediaFile/Watch/Seek/Pause boundary. GetMediaTime is non-DVD airing/
  wall-clock based while GetRawMediaTime is current-file-segment-relative;
  retain coordinates explicitly in any recovery snapshot, not interchangeable
  offsets. Core MCP already exposes both for testing. No contract is enabled
  or advertised until cancellation/expiry/exact-context/latest-intent and
  forced-failure physical gates pass. Ordinary Fixed guard remains unchanged.
  Plugin read-only DirectWatchSnapshot now compiles against stockJARd76ded98
  and passes exact-context/distinct-coordinate/race/unavailable tests on JDK8/
 11/17. It is not wired to capability negotiation or HTTP, performs no Watch/
  Seek/Pause and cannot certify atomic recovery. Ticket/latest-intent/expiry/
  cancellation and physical restore remain; no tablet runtime change yet.
  Revision299: candidate staged coordinator and optional HTTP routes pass
  isolated stock-API compilation/Java8 bytecode and JDK8/11/17 tests. Claims
  stay cancelable after custody transfer; Pause is deferred until replacement
  readiness because stock Watch returns an asynchronous task.
  Public-API boundary proof on175/tablet/current644596ae passes playing91.843s
  and paused98.834s: exact same source/context, real Exynos H.264 A/V,42s Seek,
  visually reviewed burned PTS43.710s/42.042s paused/46.947s resumed;15 prefs
  and power restored. Core MCP only controls this test, not normal runtime.
  Candidate recovery routes are absent from existing production construction
  and not advertised/deployed. Android negotiation/integration and real forced-
  failure/owned-producer/caption gates remain. No full owned-support PASS.
  Compact proof: artifacts/results/DEVICE-002/stock-watch-recovery-boundary.json.
  Revision301 supersedes the local-only299 notes: production recovery is wired
  and plugin-only175 deployed, stock Core/FFmpeg hashes unchanged. Real forced
  Direct+Pull failure68.390s passes fresh ordinary Fixed H.264 video plus final
  Seek, independently visible generated PTS1.335;15prefs/power restored.
  Normal owned74.794s failed with diagnostic-broadcast ANR; both Exo backends
  now perform bounded Direct HTTP seek outside the UI progress-position lock.
  Latest inspected APK6ecfee4a is installed, normal seeks/pause qualification
  underway.63 affected source/order/lifecycle and14 MCP adapter tests pass;
  affected JVM and APK build pass. Explicit MIM feature gating/old-wrapper
  refusal passes local tests. Direct-only trigger,232 GPU/caption and cancel/
  latest nonzero intent qualification remain; no full owned/device PASS.
  Safe boundary proof: candidate167d3911 stock175 ordinary Fixed/Exynos H.264
  passes105.145s, including seeks/large jumps/pause/STOP-rewatch and explicit
  unsupported_video_stock_fixed assertion. Fifteen preferences restored.
 232 ordinary Fixed/CEA delivery passes88.305s, but visual CEA is malformed;
  that visual gate belongs to TABLET-CC-001, not a caption PASS. Runtime shows
  real VAAPI encoding with CPU decoding; intel_gpu_top is unavailable. Do not
  call it full GPU decode/encode or owned Direct.

| 304 | 2026-10-08 | [x] DEVICE-002/TABLET-MIM-001/GSY-CAP-001 close COMPLETE_WITH_EXPLICIT_LIMITS on installed c62911ae; both GSY recovery/owned-caption/unavailable gates pass. Preserve per-row native provenance, CPU175500ms/IJK/nativeDVD/speaker limits. Settings/power/captionfalse restored, zero jobs/contexts, protected files unchanged;366 outputs324,482,575bytes retire recoverably. Remove device from both orders304; no unrelated matrix/publication. |
| 303 | 2026-10-08 | [x]1752s caption control113.704s independently readable PTS0/2/4 at video5.372;2322s77.913s PTS22/24/26 at26.927; A/V/owned source/seek/pause/settings restoration pass. [x] Nonzero stock recovery109.349s preserves44.691s bookmark, raw50.838/picture50.384, canceled actual HTTP Watch409 without source/time reset. [x] Legacy Exo real Direct-only59.881s with visible PTS3.303. GSY/Media3139.247s FAIL exposes assume-all native inventory, add GSY-CAP-001 and resolve actual delegate capabilities. Expired UI custody retires locally without main-thread HTTP. Both orders303 prioritize affected GSY then cleanup; no parent closure or broad rerun. |

| 302 | 2026-10-08 | [x] TABLET-MIM-001 normal owned175 A/V/FF/REW/pause78.483s after position-lock correction. [x] Real Direct-only failure59.769s on170ad8e4 after format-based unsupported discovery: fresh Fixed/actual video/final Seek, independently reviewed PTS4.538;15prefs/power restored. [x]232 owned caption transport83.770s with advancing A/V/seek/pause, independently readable settled PTS23.5/24 at video24.725, fresh VAAPI h264_vaapi hardwareDecode/Encode=true and zero active jobs after exit. CPU175 fast500ms still shows malformed roll-up text, not visual PASS; generate configured2s control and qualify next. Both orders302; full parent stays open. |

| 301 | 2026-10-08 | [x] MIM-DIRECT-005 forced Direct+Pull failure on stock175/tabletd9e9e500:68.390s, fresh session, real H.264 A/V, final captured Seek, independently reviewed generated picture and15prefs/power restoration. Normal owned run FAIL74.794s: broadcast ANR with HTTP replacement under a lock also used by UI progress setter. Both Exo paths move Direct HTTP before the position lock, retaining owner/session fences and ordinary seek path;6ecfee4a build/inspection/install and affected63 source/order/lifecycle+14 MCP tests pass. Both orders301 advance normal correction/direct-only/232 GPU rather than repeating completed matrices. Parent stays open. |

| 300 | 2026-10-08 | Android/plugin recovery is now wired; stock175 plugin-only deployment preserves Sage.jar/root FFmpeg. First normal tablet gate FAIL141.747s: old MIM forwarded private Direct flags into FFmpeg, while native MPEG-2-less Pull reached audio-only READY without an error. Update plugin-owned binaries with execution/ABI preflight, add explicit ownedDirectStreams capability gate and both Exo unsupported-track triggers; qualify next APK before physical rerun. MCP-PLUGIN-CONFIG-001 provides typed public-API caption-listener commissioning (false/true/false verified; true borrowed for the active gate and must restore false). Both orders300 advance affected normal/failure gates; no owned-support/device PASS or repeated broad matrix. |

| 299 | 2026-10-08 | [x] MIM-DIRECT-005 local staged coordinator/optional HTTP candidate and stock175 public Watch/Seek/pause boundary. Claim cancellation survives transfer; source/owner/intent/expiry/replay/stop guards and deferred ready Pause pass JDK8/11/17; all plugin Java compiles against stock API. Tablet644596ae playing91.843s/paused98.834s have independent burned-PTS picture checks and15prefs/power restoration. Earlier apparent Seek/pause failures were oracle errors (backend FLUSH clock resets; initial pre-FLUSH sample), not runtime fixes. Both orders299 advance actual Android integration/deployment/forced-failure gates. Production constructor still has no recovery routes/capability; full owned Transcode and DEVICE-002 stay open. |

| 298 | 2026-10-08 | Plugin initial-failure capture now uses exact UI/library typed source identity and global metadata plus explicit client intent, not decoder-owned clock/state reads. Single-segment/non-DVD, zero target, source race/mismatch, negative/overflow and provenance guards pass JDK8/11/17. Both orders298 keep coordinator/latest-intent-after-claim/physical restore next. No HTTP capability/replay or client/runtime deployment; safe Fixed retained. |

| 297 | 2026-10-08 | MIM-DIRECT-005 ticket custody passes JDK8/11/17 capacity4/120s expiry/owner-source-intent/one-use/cancel/supersession tests; all plugin Java compiles against stock API/Java8 in isolated staging. Coordinator must guard cancel after claim and avoid decoder/socket reads during OPENURL/SEEK/replacement; Core review confirms that blocking boundary. Both orders297 advance coordinator then physical recovery; no enabled endpoint/recovery/capability or full owned support PASS. |

| 296 | 2026-10-08 | MIM-DIRECT-005 read-only snapshot sub-stage implemented/tested against actual stock Sage.jar with Java8 target/JDK8/11/17: exact context, typed source, distinct clocks, race/missing/API-error rejection. No endpoint, replay, enabled capability, runtime MCP dependency or deployment. Both orders296 advance bounded ticket/restore then TABLET-MIM-001 enablement; safe ordinary Fixed unchanged. Read-only175 Core MCP available; restart window expired, no restart used. |

| 295 | 2026-10-08 | Link FFmpeg-plugin MIM-DIRECT-005 before TABLET-MIM-001 enablement. Verified stock Pull codec gate and public watch/seek/time APIs; non-DVD GetMediaTime is airing/wall-clock based, GetRawMediaTime is segment-relative. Existing MCP supports both test clocks; no Core change/runtime MCP dependency. Both orders295 prioritize optional recovery proof before client negotiation; safe ordinary Fixed unchanged. Stock175 restart window expired19:00 UTC with no restart used. |

| 294 | 2026-10-08 | [x] TABLET-IJK-001 best-effort disposition closes with original UK hardware row NOT_WORKING and only proven readiness/Surface fixes retained. Tablet clean lifecycle126.416s and MiniMX original259.585s have real reviewed pictures. Corrected MiniMX Breakfast Teletext183.970s PASS:67 cues/20184ms in20s, longest gap1.697s, Off/On, paused-clock hold/resumed cues/readable output;32prefs/serverCC/exact power restored. Both orders294 advance TABLET-MIM-001/final provenance, preserve40 other tablet hardware rows and do not claim full owned support. |

+Closure2026-10-08 (revision294): COMPLETE_WITH_EXPLICIT_LIMITS under the user's
best-effort matrix instruction. Original UK IJK hardware row remains NOT_WORKING;
no decoder rewrite, probe retention, native option/order/read-reset workaround
or native-library upgrade retained. Independently proven pending-seek and
Surface corrections pass tablet clean MP4 lifecycle126.416s and MiniMX original
lifecycle259.585s. Corrected MiniMX Breakfast Teletext183.970s PASS with67 new
cue updates/20184ms in20s, longest gap1.697s, Off clear/re-enable, pause-clock
hold/resumed cues and independently readable output.32prefs/serverCC/exact
power restored. Holby EOF and Exo-only native-playing test failures are not
renderer regression proof.16 caption/first-frame/27 MCP health tests pass;
other40 tablet hardware rows stay valid. Parent DEVICE-002 remains open for
TABLET-MIM-001; full owned Transcode is not implemented or passed.

Historical controlled-trial provenance (superseded candidates, not current runtime):


- [x] **TABLET-IJK-001 - IJK blank hardware AVC on SM-P610.** Child of
  DEVICE-002. Actual stock175 Taskmaster Pull selects OMX.Exynos.avc.dec,
  then errors0x8000100b; settled screenshot/eight-second capture stays blank
  while source clock advances1316974..1327110ms. Preserve this failure;
  do not label old IJK clock/control/lifecycle verdicts as complete video
  PASS. Expose its existing real first-frame callback in bounded debug
  snapshots and reject a clock-only startup/recovery. Compare generated
  progressive AVC MP4/TS controls before attributing to interlace, CSD or
  device hardware. Implement only a proven bounded client correction if
  feasible; no silent software/backend switch, Core patch or native-library
  modernization. Verify corrected negative/positive oracles and affected
  IJK rows only; other40 hardware rows remain valid on their recorded build.
  Current6b71 restart-from-beginning UK control also fails67.327s with the real
  first-frame oracle; saved resume position alone does not explain the failure.
  The exact bundled library and upstream0.8.8 source expose mediacodec-sync;
  the completed SM-P610/API33 hardware-only queueing trial below was withdrawn
  because it produced no visible improvement. Do not repeat it or treat it as
  a proven fix/native dependency upgrade; next work needs a different evidenced
  decoder/container hypothesis while preserving the working other backends.
  Trial A553 first-frame/control oracles pass41.816s/78.703s, but193814 settled
  screenshot is blank and8s screenrecord has no usable video frames. D8A6
  bounded debug MediaInfo confirms real OMX.Exynos.avc.dec while fresh195042
  view is still blank, so callback/clock are insufficient visual proof.
  Synchronous option exception withdrawn; owned untracked839-byte test moved
  recoverably to deleteme. Keep actual on-demand decoder identity diagnostics,
  original IJK options and open failure. No runtime/native-library fix claimed.
  Read-only review: working Annex-B TS ALSO logs nakedCSD; failing Taskmaster
  is1920x1080 versus positive MP4/TS640x360/1280x720. Container/CSD alone is
  not isolated. Next discriminator: same Taskmaster start-GOP/AVC bytes in TS
  and stream-copy MP4, verify normalized SPS/PPS/VCL hashes,1080/interlace/
  access-unit size and actual Exynos decoder/real picture. Remux is not video
  transcoding but changes packetization/timestamps, so success still does not
  prove CSD alone. Native input-capacity/chunk handling is another unproven
  hypothesis. Java data-source/display exposes no MediaFormat CSD hook.
  Legacy k0.8.8 source/build scripts exist but pinned source/NDKr13b absent;
  containerNDK21/current flatAAR consumer is not a working native build path.
  Any proven native compatibility patch needs isolated pinned staging,
  bothARM ABI/provenance/packaging and affected IJK gates, no bulk upgrade.
  Bounded read-only metadata now finds11 non-IDR units before first complete
  SPS/PPS/IDR on Taskmaster;1080High4.0 MBAFF, first60 packet max211776bytes
  versus38673 on working progressive720TS (starts keypacket0). This strengthens
  bootstrap/large-AU hypotheses, not a proven cause. Source accessible through
  stored Unraid credentials/container bind; no file download or scan required.
  Compact sanitized commands/counts/prefix-only hashes are in
  artifacts/results/DEVICE-002/ijk-bootstrap-metadata.json. Next controlled
  comparison starts stream-copy TS at complete IDR and derives MP4 from it;
  verify ordered VCL/SPS/PPS and relative PTS/DTS before actual-picture tests.
  Timestamp normalization is a confound, no fix/native change implemented.
  Current2026-10-08: same-IDR TS and derived MP4 prepared without transcoding;
  both2272 packets/4544 VCLs/hash007ca26924cdf9e9, SPS/PPS and exact relative
  PTS/DTS match. Physical comparison88.177s on unchanged tablet IJK options
  shows changing real picture in both clips with OMX.Exynos.avc.dec, no codec
  errors in captured selection logs;15 prefs/power restored. This rules out
  blanket1080/MBAFF/large-AU/container inability, NOT a full-recording fix.
  Follow-up retains the original absolute timestamps in a complete-IDR TS,
  and11 incomplete leading packets in a normalized TS, then retests the full
  original multiplex with identical options. All post-IDR coded frames verified;
  discard only initial non-IDR units for the comparison hash, never playback.
  Controls75382 active; independent picture review required. Temporary stages
  imported only via supported175 API, configs documented; no stock files/Core
  modification, transcoding or native-library upgrade.
  Controlled follow-up208.791s: clean-IDR/original absolute timestamps has
  changing actual picture; normalized TS retaining11 leading non-key packets
  and original multiplex are blank with15/6 Exynos0x8000100b entries. Same
  post-IDR VCL/SPS/PPS hashes and relative packet timings match;15 prefs restored.
  Candidatec8323282 uses existing0.8.8 seek-at-start1ms only on exactSM-P610/
  API33 hardware IJK completed native TS Pull (not Push/HTTP/growing/software).
  Intended to initialize the demuxer at a complete random-access point; not
  a source rewrite/transcoding/clock offset. Two JVM tests/54 contracts,57s
  build/strict APK inspection PASS, guarded24.432s install preserves15 prefs
  and independently verifies SHA. Actual pre-trial installed APK was56603387,
  rollback copied/hash-verified to rootartifacts/temp, not assumed67e07f95.
  Physical trial72285 active; NOT a proven correction. Withdraw if ineffective
  or if resume/seek/ordinary clips regress; do not extend device tuple by brand.
  Trial72285 completed214.242s/15 prefs restored, no original/prefix correction;
  original seek option withdrawn. Eligibility used the conservative stock
  potentially-growing hint before its loader proof, so no applied-marker proof
  exists; do not claim native1ms semantics were disproven. Replacement60ab1bc8
  uses loader-confirmed not-growing state, exact SM-P610/API33 hardware TS Pull
  and bounded2MiB PSI/PES analysis. Only an incomplete initial video GOP before
  a proven SPS/PPS/IDR PES is nulled, byte coordinates/SIZE/audio/captions/times
  retained. Other codecs/valid starts/unknown or damaged PSI/scramble/open-GOP
  parameter starts are no-ops. Four parser/two tuple JVM tests and54 contracts
  pass; build28s plus optional probe-error containment21s pass. Physical
  correctness still pending, no native replacement/transcoding/Core change.
  Prefix trial53280/60ab1bc8 completes171.845s: broken-prefix clip now has
  real picture/no Exynos errors, full original still blank15 errors.15 prefs
  restored. Pure offline check of bounded private source proves first parser
  returns0 for original versus990/control: original PAT arrives50196bytes,
  PMT52640bytes, after video began. Two-pass PSI resolution plus handling an
  initial incomplete PES now recognizes991 original video packets before
  complete IDR byte200032 (PID512), versus990/control at188188 (PID256).
  Original byte positions/other PIDs/times retained. Eight JVM tests/54 contracts
  and2 passive-debug getter contracts/build/inspection pass; debug fields
  explicitly expose eligibility/reason/packet count/boundary without I/O/locks.
  Candidate5c27e21b installs with15-pref restore, rollback566 retained.
  Actual original picture/control proof still required before closure.
  5c27 trial176.462s/15 prefs restored: positive/prefix pictures pass, original
  still blank. MCP had dropped new health fields; whitelist corrected,27 health
  tests pass. Single original90.647s confirms actual eligibility1/state4/991
  filtered/IDR200032 but firstFramefalse/Exynos failure. A second boundary is
  now proven by executable buffer test: repeated position0 yields byte1 under
  legacy sequential buffering, but JNI readAt requires byte0. Keep global
  BufferedPullDataSource behavior untouched; scoped IJK native adapter flushes
  only on nonsequential absolute reads after resolved-not-growing. Two policy/
  executable-buffer tests +8 parser/tuple JVM tests/build27s pass. Candidate
  d04b6d64 inspection/install and original picture proof pending; no fix closure.
  d04 install34.106s/15prefs and hash verified. Original90.95s still has no
  firstFrame; active991-packet filter and41 absolute-read resets proven. Direct
  local1000ms seek93.315s also stays blank despite real9677ms clock. Do not
  treat these as corrected original playback. AVC+AC3 video-only-subtitle-excluded
  controls89.544s both show real picture/Exynos/AC3,15prefs restored; all post-IDR
  coded hashes/timings match2272 packets/4544VCLs. Original subtitle/probe path
  remains a discriminator. Native nobuffer discards stream-info probe packets;
  candidate175bd3ec retains those only after loader confirms static source,
  dedicated thread/session/player/source guards and obsolete-source cleanup.
  Existing live/growing/other tuple options unchanged, no UI I/O. Build25+20s,
 3 source guards/54 backend tests/inspection pass. Original physical check next;
  no full-recording correction PASS yet, keep rollback566.
  Follow-up281 retention trial fails93.233s before demux: bundled FFmpeg rejects
  empty fflags, not a disproven retention hypothesis. Numeric0L candidate
  a1a19c59 builds20s/3 source guards/strict inspection, installs19.039s preserving
  15prefs. Original-only51.344s now shows independently reviewed changing real
  video, firstFrametrue/clock5343->12603, retention1,991 filtered and7 read resets;
  no captured Exynos errors. Narrowing to retention-only (no prefix byte overlay
  or read-reset behavior) before accepting the runtime correction. Then affected
  original seek/pause/STOP/HOME/Teletext/positive controls. Not closed yet.
  Retention-only0aa69f33 still fails90.838s/15prefs restored. Prefix+retention
  without read-reset candidate19b27669 installs18.22s/15prefs restored; original
  first/settled captures160250/160258 visibly change, FF pair160304/160308
  visibly changes and REW160314 has video/timeline25s. During next capture ADB
  disconnected; original port42491 no longer accepts TCP, device still pings.
  No full control/lifecycle PASS or settings-restoration claim for that run;
  current port requested. Exact tablet stock175 context is already absent, no
  other client touched. Preference/power checkpoints retained for restoration.
  Unneeded read-reset experiment source/test moved recoverably to deleteme;
  shared sequential buffer unchanged. Guard async prepare exceptions in current
  source; this latest build needs guarded install after reconnection. Remaining
  original controls/lifecycle/TT, clean/prefix controls and growing exclusion
  stay before TABLET-MIM-001. Temporary driver now persists each phase and
  records cleanup errors without losing its partial report. No publication.
  User supplies new port45219. Actual SM-P610/API33 reconnect succeeds using
  per-command SAGETV_ADB_SERIAL override, non-expiring authorization verified;
  restore all15 preferences and exact power7/600000 through verified same
  tablet. Old42491 power checkpoint retired recoverably after restoration,
  active ignored aliases unchanged. Guarded3c806417 install and complete original
  controls next; no false interrupted-run PASS or repeated broad matrix.
  Fresh3c806417 original91.036s FAIL despite retention1/filter991; actual
  Exynos0x8000100b/firstFramefalse,15prefs restored. Earlier narrowed pictures
  are partial, not a reliable fix. Restore scoped JNI absolute-read correction:
  the executable legacy-buffer test proves repeated readAt positions otherwise
  consume different bytes. Candidate86ee20a4 includes all three protections,
  ten JVM/38s build/3 diagnostic guards/strict inspection PASS. Also clear
  delayed server-restored playback with the established lifecycle helper before
  the exact-source request; this corrects a potential oracle race, not Core.
  Guarded install and same original physical controls next. Not closed.
  Combined86ee20a4 initial/FF/REW/pause pictures advance, but full193.024s gate
  FAIL: largeFF2 sampled clock stalls182761 and STOP-rewatch yields codec error.
  All15prefs restored. Concrete replay trace: native start/seek0 both return
  -3 before prepared; later callback starts/seeks stale119349 bookmark. seek()
  relied on PLAY state but did not check playerReady. Queue newest seek while
  unprepared even if PLAY is reported; scoped completed tuple applies pending
  seek before starting native rendering. Candidate9c6726b8 build26s/4 source
  guards/strict inspection PASS. Focused replay and ordinary IJK startup gates
  next; native-library/remote-key/server behavior unchanged. Large-seek oracle
  now uses existing30s health budget before independent pictures, not fixed6s.
  9c6726b8 controls172.531s PASS for tested origin: initial, FF/REW/large skips,
  pause clock61620 stable then PLAY68222->72737, actual changing captures and
  STOP/replay picture. Replay native seek0 now returns0, not-3; actual Exynos
  selected/no captured replay errors.15prefs restored. Normal-resume lifecycle
  still FAIL100.728s at initial playback, so no general/native closure yet.
  Candidate120bb6b9 uses existing native seek-at-start before decoder startup,
  captures newest pending resume offset, avoids duplicate callback seek when
  already applied; newer Core seek still wins. Completed exact tuple only,
  no native upgrade.24s build/5 guards/11 background/6 scheduling/inspection
  PASS. Optional bounded lifecycle phase images now support independent review.
  Normal saved-position lifecycle next. User extends175 restarts until19:00 UTC
  (2 p.m. Central); stock files remain protected, no restart performed here.
  Native prepare-seek lifecycle also FAIL100.306s at initial normal-resume
  playback;15prefs/power restored. IJK hardware1080i UK row is NOT_WORKING after
  documented best effort, not a reliable generic decoder fix. All unqualified
  prefix rewriting, probe-retention, native seek/order, buffer-reset runtime
  code removed; seven owned untracked trial sources/tests retired recoverably.
  IJKPullMediaSource is unchanged from pre-task baseline. Keep only evidenced
  !playerReady pending-seek guard (latest request/zero survives invalid-state
  race), actual first-frame/decoder diagnostics, and prior Teletext retry.
  Three readiness/no-workaround/opt-in-lifecycle source tests PASS. Final APK
  build plus positive tablet/MiniMX IJK startup/STOP/lifecycle/caption checks
  precede disposition closure; other40 hardware rows remain valid. Do not
  repeat unsuccessful original options or bulk-upgrade native dependencies.
  Guard-onlybc6ab965 builds27s/5 MIM policy JVM/inspection/3 readiness/54 backend/
  26 MCP health contracts PASS, installs21.152s/15prefs restored; no removed
  trial class remains in core JAR. Clean MP4 lifecycle113.123s clock oracle PASS
  but independent pairs show initial real picture then brown blank HOME return
  and user-resume. Verdict is VISUAL_FAIL, not lifecycle video PASS. IJK only
  setDisplay at load/STOP-resume and lacked SurfaceHolder callbacks; unlike
  supported renderers it never binds a recreated HOME Surface. Add guarded
  display-only observer with valid holder/player/session checks, detach on
  destruction and invalidate/remove before release; no seek/play/prepare from
  surface callbacks.644596ae builds24s/4 readiness-surface/3 TT/11 background/
  inspection PASS, actual clean lifecycle recheck next. SD Holby TT gate114.794s
  fails initial hardware playback, all15prefs/server CC/power restored; not
  caption functionality PASS or source rewrite justification.
  644596ae guarded install23.398s/15prefs restored; cleanMP4 lifecycle126.416s
  PASS with independent changing HOME pair174806/174809 and user-resume
  174819/174822, connection preserved/user pause not auto-resumed, three exact
  replays and no crash signature/teardown. Original UK recheck on same source-
  unchanged/default-options build next, then affected MiniMX IJK qualification.
  No blanket IJK failure or native-original correction claimed from clean MP4.
  Same644596ae original101.939s still FAIL/firstFramefalse, actual debug decoder
  OMX.Exynos.avc.dec/hardware1920x1080;15prefs restored. MiniMX backup-only stage
  95.867s times out90s after23,658,496bytes; no install/pref mutation, old121062d7
  unchanged. Honor configured slow-device transfer budget and independently
  hash fresh rollback against device before install; retry then affected IJK
  startup/STOP/HOME/TT only. No repeated original workaround or codec matrix.
  MiniMX guarded install493.945s completes on644596ae with independently
  verified rollback121062d7/current644596ae hashes and32 preferences restored.
  Same-source IJK lifecycle259.585s PASS: actual Amlogic hardware, real changing
  HOME-return and user-resume pictures, manual pause preserved,3 exact replays,
  no crash and final teardown;32prefs/power1/3600000 restored. Eight-second
  HDMI capture overlapped HOME and is not sustained-video evidence. Teletext
  initially renders27 updates, then Off/On197.673s fails its45s cue budget
  during a commercial break;32prefs/server CC/power restored. Longer bounded
  observation is underway to distinguish natural caption silence from a
  renderer failure, without relaxing continuity or changing playback code.
  Tablet original remains NOT_WORKING, not a blanket IJK failure.
  Read-only inventory proves Holby82.688s/23,569,848bytes; both cue-budget
  failures reach its commercial break/EOF, so no renderer regression proved.
  Breakfast391.405s/445,884,675bytes is suitable. On same644596ae, Off/On
  resumes68 new updates/20097ms in20s and HDMI shows matching dolphin video
  with readable Teletext. Pause clock holds and native PLAY resumes43s with
  new captions, but test243.129s incorrectly reads Exo-only isPlaying=false
  on unsupported IJK probe. Preserve exported health_basicIsPlaying in MCP
  compact snapshots and use it only when probeSupported=false; missing/false
  still fails, Exo gate unchanged.16 caption/first-frame tests and27 MCP health
  tests PASS. Corrected physical retry follows a pre-checkpoint21.347s ADB
  bootstrap timeout; scoped222 disconnect/reconnect restores same model/keys.
  No APK/runtime/server change for this test-tool correction;32prefs/serverCC/
  power restored after the completed243.129s attempt. Both orders293 unchanged.

| 293 | 2026-10-08 | Proven inadequate82.688s Holby fixture replaced by391.405s Breakfast for affected IJK caption gate. Off/On20s68 new cues/20097ms and independently readable HDMI pass, but pause oracle243.129s falsely used Exo-only isPlaying default. Correct MCP native-playing exposure and guarded oracle;16 caption/first-frame and27 health tests pass. Pre-checkpoint21.347s ADB timeout recovered by exact222 disconnect/reconnect without new key or runtime-command replay. Corrected physical retry next; both orders293 retain dependencies, no APK/Core change or unrelated matrix rerun. |

| 292 | 2026-10-08 | MiniMX guarded install/hash493.945s and independently reviewed IJK original HOME/replay lifecycle259.585s pass;32prefs/power restored. Teletext initial cues work but Off/On197.673s cue timeout falls in commercial silence; longer bounded observation next, no runtime workaround or relaxed continuity. Tablet original remains NOT_WORKING. Both orders292 reviewed, dependencies unchanged; no broader matrix or server mutation. |
| 291 | 2026-10-08 | Latest Surface-build original still FAIL101.939s on actual hardware Exynos/no first frame;15prefs restored. MiniMX backup-only95.867s exceeded fixed90s before mutation; fix temporary transfer budget/rollback hash, retry then affected IJK only. Both orders291 retain final qualification/limitation; no broad matrix or Core change. |

| 290 | 2026-10-08 | IJK Surface fix physically PASS126.416s on tablet clean same-coded MP4: independently changing HOME and user-resume pictures, preserved connection/manual pause,3 replays/no crash/teardown;15prefs/exact power restored. Same-build original UK recheck then affected MiniMX IJK only before closing disposition. Both orders290 advance unfinished gates, no unrelated matrix repetition. |

| 289 | 2026-10-08 | Guard-only clean lifecycle113.123s clock-PASS invalidated by independent blank HOME/user-resume images; initial actual picture positive. Proven missing IJK SurfaceHolder rebind corrected with session/player/holder guards and release-owned observer;644596ae build24s/4 guard/3 TT/11 background/inspection pass, physical recheck next. UK SD caption114.794s fails initial hardware playback/restoration verified. Both orders289 retain focused guard/lifecycle qualification, no original workaround revival or broad matrix. |

| 288 | 2026-10-08 | Native prepare-seek100.306s fails saved-position startup;15prefs/power restored. Record tablet UK IJK hardware NOT_WORKING after bounded best effort; withdraw all unqualified byte/native/buffer workarounds and retire seven owned sources/tests recoverably. Keep proven pending-seek readiness guard and real picture diagnostics;3 focused contracts pass. Both orders288 advance final guard qualification/limitation, not another original option sweep or broad matrix. |

| 287 | 2026-10-08 | Origin controls172.531s/replay picture pass after readiness guard, but normal-resume lifecycle100.728s still fails startup. Test scoped existing native prepare-time seek120bb6b9 before claiming closure; build24s/5 guards/11 background/6 scheduling/inspection pass. Bounded lifecycle captures opt-in.15prefs/power restored, both orders287 targeted. User extends175 restart cutoff19:00 UTC, stock files protected. |

| 286 | 2026-10-08 | Combined original193.024s still fails replay: start/seek0 rejected -3 before prepare, stale119349 bookmark applied afterward. Add evidenced playerReady pending-seek guard and exact completed-tuple pre-start seek ordering;4 contracts/build26s/inspection pass for9c6726b8. Full replay/control proof remains.15prefs restored; both orders286 targeted, no native upgrade/Core/key change. |

| 285 | 2026-10-08 | Tablet fresh prefix+retention fails91.036s with actual Exynos errors despite applied markers;15prefs restored, no reliable correction claimed. Restore/re-test scoped JNI absolute read contract plus established delayed-restore cleanup in harness.10 JVM/3 guards/build38s/inspection pass for86ee20a4. Both orders285 remain focused original controls before lifecycle/captions/MIM; no broader reruns or stock/Core change. |

| 284 | 2026-10-08 | Tablet unblocked at user-provided45219; verify actual SM-P610/API33, restore interrupted15 preferences and exact power7/600000, retire old-endpoint checkpoint recoverably. Guarded narrowed candidate install/control qualification resumes. Both orders284 advance from reconnection/restoration to actual unfinished physical gates; broader matrix unchanged. |

| 283 | 2026-10-08 | Retention-only original still blank; prefix+retention19b27669 shows real original/FF/REW picture without read-reset workaround. Tablet wireless ADB disappears mid-controls; restoration and remaining physical gates pending current port, not falsely passed. Exact stock175 test context already absent. Retire only owned unused read-reset source/test; keep shared buffer and all failure/current comparison evidence. Both orders283 remain targeted tablet first; compile/JVM/backend/diagnostic checks selected, no broad matrix or publication. |

| 282 | 2026-10-08 | Original tablet IJK real-picture positive51.344s after numeric probe-retention correction; empty-flag trial failed before demux. Isolate retention without packet overlay/read resets, then affected controls/lifecycle/captions before closure.15 prefs restored on install/gates. Both orders282 reviewed unchanged; no new matrix, server file change or publication. |

| 281 | 2026-10-08 | IJK original remains blank after proven991-packet protection/41 absolute-read resets and local seek. Matched AVC+AC3 controls render, ruling out AC3 alone; additional caption mux/probe path remains. Source-reviewed native nobuffer discards probed packets; guarded loader-confirmed-static retention candidate175bd3ec tests original next. Both orders281 reviewed unchanged; no original fix, matrix closure or publication claimed. |

| 280 | 2026-10-08 | Actual original991-packet protection proven but original still fails; safe fields now survive MCP whitelist,27 health tests pass. Executable test proves JNI absolute-read mismatch with existing sequential buffer; scoped adapter reset candidate d04b6d64 preserves shared-buffer/growing/other-device behavior.10 JVM/build/inspection, original physical check next. Both orders280 reviewed unchanged; no repeated matrix or full-recording PASS. |

| 279 | 2026-10-08 | TABLET-IJK-001 first prefix candidate fixes the synthetic incomplete-start clip but not original. Bounded read-only original framing proves late PAT/PMT, so two-pass parser validates known AVC PID before revisiting early video, preserves coordinates/nonvideo data and protects original991 packets before byte200032.8 JVM/54 backend/2 passive-diagnostics contracts/build/inspection pass. Candidate5c27e21b physical original/control gate next, not closed. Both orders279 reviewed unchanged. |

| 278 | 2026-10-08 | TABLET-IJK-001 seek-option trial does not fix214.242s and is withdrawn; stock growing-candidate hint prevented conclusive activation. Replacement60ab1bc8 gates at loader's resolved not-growing boundary and detects exact incomplete AVC start before first full SPS/PPS/IDR.6 JVM/54 contracts/build/inspection candidate; physical proof pending, no source-byte offsets/audio/caption/time rewrite or native-library change. Both orders278 reviewed unchanged. |

| 277 | 2026-10-08 | TABLET-IJK-001 comparison isolates incomplete startup packet boundary: clean TS/MP4/absolute-PTS pictures PASS, normalized leading-prefix TS/original FAIL with Exynos errors. Existing native-option candidatec8323282 scoped to completed SM-P610/API33 hardware TS Pull;2 JVM/54 contracts/build/inspection and preserved15-pref install pass. Actual physical correction pending; no matrix closure. Both orders277 reviewed unchanged, IJK fix before remaining owned-stream work. |

| 276 | 2026-10-08 | [x] DEVICE-003 closed with explicit older-hardware limits after optional owned DVD Transcode/Copy/hybrid and stock175 native fallback pass; settings/power/temp fixture restoration complete. Both orders276 remove MiniMX and advance DEVICE-002 IJK/owned investigations without repeated codec matrices. |

- [x] **DEVICE-003 - MiniMX Android6 compatibility and full device matrix.**
  User added2026-10-07. Check ADB192.168.10.222 now in parallel using the
  persistent container identity, explicit serial override and timeout0;
  do not change the active device/server to commission another box. Verify
  actual model/API/ABI/codec inventory and installed package before updates.
  Current user directive: finish DEVICE-003 before returning to DEVICE-002;
  do not test in parallel on a different unverified profile. Preserve all
  data/settings. Review the reported
  differing startup screen and gear-only settings access: distinguish old APK,
  non-TV feature/layout detection, remote focus and actual client defects.
  Implement bounded compatibility/UI corrections where feasible. Gate actual
  supported codec/backends, controls/lifecycle, CC/subtitles/DVD, available
  stock/optional transports and readable UI using appropriate captured evidence.
  Unsupported older-hardware capabilities are explicit limits, not successful
  hardware playback or silent software/transcoding fallback. Do not reopen
  unrelated completed device rows; record results/builds in compact matrix.
  Current revision275: original native/stock rows remain on verified99a47797;
  focused optional-HTTP correction/diagnostics are separate candidates. Codecs/captions/IJK/
  System boundary/containment and stock DVD controls complete. Native smooth
  reverse-8 preview is NOT_WORKING/root cause unproven after withdrawn attempts;
  working normal/timed/chapter/menu/SPU paths retain their passes. Optional232
  setup completed by user2026-10-08; actual Main Menu/SageTV7 verified against
  matching public context. Owned Transcode/CEA control transport181.783s passes,
  fresh running VAAPI/h264_vaapi hardwareDecode=true/hardwareEncode=true verified;
  CC1 screenshot has visible timestamps but one malformed row, so clean display
  is not yet certified. Copy/DVB Off-On20s continuity passes, FF then stalls its
  playlist/404 is now corrected by plugin cleanup; MINIMX-MIM-001 closed.
  Clean2s CEA Transcode200.781s now passes readable CC1 initial/post-seek/Off;
  fresh full-GPU flags and zero orphans pass. Owned legacy TT236.949s readable
  visual closes MINIMX-MIM-002. Optional DVD Transcode195.607s active/full_gpu
  and Copy134.345s active/copy pass32-pref/power restoration; their ff/rew
  command acceptance is not exact seek evidence. Hybrid selector/menu/ff_2/rew_2
  check59232 running. Finish fallback and final settings/power/fixture/artifact
  closure.164 corrected/completed raw files124435274bytes retired recoverably.
  32 current prefs restored
  after each completed group. Compact matrix and ledger preserve results;
  dated readiness history below is not a
  current blocker or instruction to repeat completed gates.
  Readiness PASS: authorized222:5555 MINIMX/AM2 Ugoos, Android6.0.1/API23,
  Amlogic S905/gxbaby, ARM32 only. Existing Dev0.5.100/2101109 remains intact;
  actual persisted identity commissioned privately, timeout0 verified. No TV
  or Leanback features: phone.ServersActivity chosen by existing launcher;
  use_tv_ui_on_tablet is an existing explicit selector, not a missing APK.
  User confirms HDMI USB capture on this box. Bounded startup PTY handshake
  fixes contaminated Android6 diagnostic output;65 ADB and130 MCP tests pass,
  read-only222/51 connections remain clean. At readiness211 matrix had not started.
  Historical pre214 HDMI readiness FAIL: DirectShow/Windows PnP did not enumerate
  the USB capture device (only integrated camera/Logi Capture); user asked
  asynchronously to check its USB connection. Do not claim physical HDMI proof.
  Existing TV-browser choice labels/onResume redirect corrected with12 focused
  tests and40s build, physical verification pending. Read-only codec query
  exposes another Android6 tooling gap: am broadcast rejects newer
  --receiver-foreground CLI switch; replace with equivalent stable intent flag,
  never retry/replay a failed ordered runtime command or treat CLI NPE as an
  app crash. ADB readiness itself remains PASS.
  Stable `-f 0x10000000` preserves the foreground receiver intent without the
  unsupported CLI switch; actual222/API23 and51/API33 codec queries now pass,
 131 MCP tests and49 affected contracts pass. Inventory22 video entries uses
  pre-API29 name classification (9Amlogic hardware-labelled/13Google software),
  not a runtime decoder/playback PASS. Hardware-labelled MPEG-2/AVC/HEVC entries
  are available for actual testing; native IJK software capability is separate.
  CLI connection cleanup also passes: owned shell closes in finally on every
  return/error path; four new tests/all135 MCP tests and real5.636s read-only
  connection pass without an orphan. Shared ADB server/keys/apps untouched.
  Current214: user reconnected capture and said continue; USB Video/Digital
  Audio now enumerate and real five-second FFmpeg capture is readable1080p.
  Start MiniMX now while unresolved tablet IJK/MIM stay separate/open. Guarded
  settings-preserving update/UI verification precedes stock175 playback rows.
  Update/UI PASS: installedB7fd7dd9 independently sha256-checked,27 saved
  preferences preserved/restored. Old dex optimization exceeded the120s MCP
  caller budget; installer completed, no overlapping retry/data reset. Normal
  launch now correct via installed metadata on Android6 (cmd absent); vendor
  pidof's system-wide false positives/header-only ps -A corrected.142 MCP
  tests pass. Existing TV-browser choice returns directly to MainActivity;
  its original false setting restored. Native Media3 stock175 matrix active,
  first six MPEG-2/MPEG-4/AVC rows pass with actual Amlogic decoders. Physical
  AVC fixture visible with nonsilent HDMI audio(-37.2/-6.9dB), but early18s
  MPEG-2 capture caught only launcher/rebuild and is not playback proof.
  Allow older startup budgets; unsupported capabilities are explicit limits,
  not blanket client failure or silent software/transcoding PASS.
  Media3 remaining four transition rows pass209.935s (batchA807.239s), three
  VP8/VP9 rows explicitly hardware-unsupported. Non-blocking inventory helper
  has six behavior/two capture tests;45s first-access threshold for expected
  slow device startup changes only warm-up discard, not A/V/error gates.
  Long MPEG2 controls pass basic/large seeks and pause but STOP->PLAY exposes
  MINIMX-IO-001. Its private27-pref restore initially fails behind the blocked
  UI; isolated Dev force-stop/normal launch then restores all27, snapshots work.
  Preserve unique failure trace until corrected; no server/Core restart.

  MINIMX-IO-001 closes217: candidateB69b4cf0 installed/complete hash independently
  confirmed despite180s SDK install timeout, no overlapping retry. Stock175
  Media3 MPEG2 STOP->PLAY/output recovery128.306s PASS, responsive diagnostics
  and all27 preferences restored. Current non-blocking observer fix leaves
  source/cache/decoder/seek behavior intact, no general SIZE-timeout claim.
  Continue legacy/GSY/IJK/lifecycle/captions/DVD and optional232; other12 codec
  rows stay valid onB7fd rather than rerunning a broad matrix after a getter fix.
  Native legacy445.706s and GSY/Media3466.971s scopes also PASS onB69:
 12 hardware rows/3 explicit software-only VP8/VP9 limits each; all27 prefs
  restored. GSY/legacy also PASS448.341s:12 hardware rows/3 unsupported,
  actual legacy delegate and27 preferences restored. Four native cohorts are
  complete; continue IJK/system, lifecycle/captions/DVD/optional232 without
  repeating completed native codec rows.
  Actual client's identity matches commissioned config; public captions.get
  reports /opt/sagetv/server/STVs/SageTV7/SageTV7.xml on175. Earlier visual
  theme inference "SageMC" is corrected, not a server/STV change. Persist
  stock expectedSTV SageTV7 privately, verify232 independently later.
  Bounded per-device install_timeout_seconds30..900/default180 now propagates
  CLI/MCP to one verified install; MiniMX600, tablet/FireTV180,149 MCP tests
  pass. Outer install caller must allow630 for this600 profile, no auto retry.
  Closure2026-10-08/revision276: available MiniMX rows tested; limitations
  dispositioned honestly. Retained native/stock codecs, controls/lifecycle,
  captions/DVD. Corrected Direct Copy both Exo families/TT/clean CEA Transcode/
  no-orphan gates pass on APK121062d7/plugin232e62bee77. Optional DVD Transcode
  195.607s(full_gpu)/Copy134.345s(copy)/hybrid180.557s passes real active relay,
  ff_2/rew_2 A/V recovery, audio/SPU controls and native menu/owned return.
  Stock175 nativeMPEG2 unavailable_stock_fixed fallback142.159s passes title
  selection/pause/play/large seeks;187.356s pause-on-root caller error corrected,
  not app failure.32 prefs/server CC/power1/3600000 restored, APIs zero sessions.
  Readable2s CEA complements500ms stress; no universal fast-stress visual PASS
  from a partial still. Native reverse-8 remains NOT_WORKING/root cause unproven,
  ineffective changes withdrawn. VP8/VP9 HW and IJK CEA/DVB explicit unsupported.
  Temporary fixture imports removed on175/232; exact SHA-verified generated
  file/empty task directory removed remotely;149977940byte local copy remains
  recoverable under rootdeleteme. Fixture disabled. Compact matrix retains
  provenance; completed/corrected raw retired, minimum reverse evidence retained.
  No Core/stock175 file modification, publication or broad matrix rerun.


| 275 | 2026-10-08 | [x] MINIMX-MIM-002 closed after stock175 readable TT148.187s, owned legacy controls162.469s and readable STV TT236.949s. MIM-DIRECT-004 closes in the owning plugin after unchanged-budget Copy224.062/247.007s, clean2s CEA Transcode200.781s with fresh VAAPI hardware decode+encode, and zero session/process orphans. CC2 is an expected negative for this one-channel608/service1-708 fixture, not a two-channel display PASS.32 prefs/server CC/power restored. Both orders275 advance optional DVD/fallback/closure, preserving native rows. Four-hour175 restart exception recorded with UTC expiry17:07:16; stock files remain protected. |

- [x] **MINIMX-MIM-002 - Legacy Direct HTTP source/Teletext observer.** Child of
  DEVICE-003. Actual legacy Copy121.891s has active_copy/real211 video frames,
  but its snapshot reports empty playbackSource/dataSourceClass: strict ownership
  fails honestly, not a playable-fallback result. Legacy HLS used an unobserved
  default HTTP factory unlike Media3. Add equivalent segment-only Teletext and
  real factory-created datasource observation, clear it at load/release; do not
  change stock Pull/Push, HTTP byte/retry policy, or fake diagnostic ownership.
  Build68s/four observer JVM tests/53 backend source tests pass;1bb354cd installed
  221.635s/hash verified/32 prefs restored. Owned legacy source now identified;
  DVB Off-On20s13 cues/18520ms passes, but208.361s group reproduces shared
  MINIMX-MIM-001 after FF. Finish Teletext/stock smoke after that shared failure.
  Stock175 legacy Pull Teletext smoke148.187s on121062d7 passes wire104events/
  28944bytes, seek/pause and32 prefs/server CC/power restoration. Screenshot
  review passes readable STV rows. Owned TT162.469s also passes real legacy
  HTTP/event225/idle-independent clock/FF7272/REW5263/pause/prefs restoration;
  one post-seek frame has no rendered caption, so bounded visual cycle still
  pending rather than claiming readable owned text. Source/ownership and
  original Copy-seek blocker are corrected; no broad native rerun required.
  Closure2026-10-08: after producer correction, legacy Copy/DVB247.007s
  passes20s Off-On14 cues/FF5937/REW11648/pause/strict actual HTTP ownership.
  Owned Teletext162.469s passes FF7272/REW5263/pause and idle-independent
  wire clock;236.949s bounded visual cycle shows readable STV text 'sightings
  of individuals. It is not necessarily'. No Android-local duplicate overlay.
  Stock175 legacy TT148.187s/readable 'Wide distribution...' also passes.
  All32 prefs/server CC/power1/3600000 restored. Old blank snapshot alone was
  a capture without positive visual proof, not a proven renderer defect;
  invalid21.803s --stv-state argument was configuration-only and restored.
  Source clears observer on load/release and observes only real TS bytes,
  unchanged HTTP retries/clock/defaults; four JVM/54 backend source tests.


| 274 | 2026-10-08 | [x] MINIMX-MIM-001 CLOSED (DEVICE-003): provider unfinished-segment deletion corrected by minimal232 plugine62bee77; exact longer Copy224.062s Media3 and247.007s legacy PASS without softened budgets. Each20s Off-On14 new cues; FF/REW5439/10571 and5937/11648ms, real pause/resume, strict owned HTTP and32 app prefs/server CC/power restore. Post-fix provider lists18/no missing/segment9 present. No client recovery experiment/Core/MIM-native/INI change. Both orders274 remove completed Copy diagnosis; remaining Transcode/legacy TT visual/fallback/no-orphan gates first. |

- [x] **MINIMX-MIM-001 - Owned Copy seek playlist failure.** Child of DEVICE-003.
  Actual232/Media3/99a47797: explicit DVB Copy229.491s fails after FF target57032;
  Off-On20s continuity passes14 new cues/media21030ms, but the representation
  stops updating (PlaylistStuckException), followed by HTTP404. Source is intact
  3,597,455,756 bytes/duration4380.994722s/startPTS69793.775978. Establish whether
  reader seek, producer/session handoff or client replacement is responsible;
  do not attribute it to hardware, change production playback to hide the oracle,
  or count retained old media/fallback as strict ownership PASS. First144.367s
  attempt omitted explicit DVB while STV CC was Off: caller configuration, not
  evidence of this failure. Guard restores32 preferences/server CC. Preserve
  minimal unique corrected-run failure evidence until resolved/dispositioned.
  New2d587433 bounded diagnostics installed226.201s/32 prefs restored;206.068s
  and206.719s repeats fail. Provider observation sees new58469ms session's
  playlist pause about25s at17795ms, then resume26315/34715ms, while provider
  remains ready. This is a real publication gap, not proven Core/decoder fault.
  Latest child can be unopened (unavailable); bound-MediaItem attributionbd77
  proves current binding during202.560s failure. Fresh-source069 experiment
  fails230.514s and is withdrawn. Diagnostic/rollback121062d7 installed219.46s,
  32 prefs restored; installed/built SHA independently match121062d7 and
  original longer Copy recheck33339 fails231.986s/32 prefs restored: actual
  failed segment9 is current-session http_404_media_not_ready, pos18924ms.
  Independent repeat60494 FAIL235.280s/32 prefs+server CC restored;
  witness76039 proves segment9 changes from open/not-deleted to open/(deleted)
  before publication; later playlist lists9 but file is absent. Provider's15s
  unlisted-file cleanup unlinks an unfinished writer segment. No client retry
  can restore its lost bytes. User authorizes necessary plugin fixes/updates
  as a standing rule (stock175 plugins only). Owning MIM-DIRECT-004 candidate
  protects future/referenced segments and empty playlists, keeps old-window
  grace cleanup; stock build/session/HTTP/plugin/caption/launcher tests pass.
  Deploy minimal class overlay232 and rerun affected Copy gates; no Core/MIM
  native/runtime/configuration change or broad matrix.
  Actual failed-exception URI/404 body attribution exposes only closed tags,
  asset/index/code, never raw URL/token/body.14 session JVM/54 contracts/26
  MCP health tests pass; no runtime retry-policy change remains.
  Added34 affected caption authority tests, including real legacy HTTP
  acceptance without accepting Pull. Regenerated/configured120s/2s CEA fixture
  SHA81ddc86d9f390f6a714ee83b0f3b39925c9aeae3700dac8e307517540d2888f7,
  149977940bytes/3595 A53 packets: clean visual complement, not deletion of stress
  evidence. Public import/readable validation remains pending.
  Closure2026-10-08: actual provider unlinks unfinished segment9. Installed
  minimal plugin overlaye62bee77 preserves it, no Core/INI/MIM-native change.
  Original longer Media3 Copy224.062s and legacy247.007s PASS:20s Off-On14
  cues each, FF/REW5439/10571 and5937/11648ms, real pause/resume and strict
  owned HTTP retained.32 app prefs/server CC/power1/3600000 restored each.
  Read-only post-fix provider:18 listed segments, none missing,9 present.
  No runtime source-recreation/retry-limit experiment ships. MIM-DIRECT-004
  retains separate Transcode/no-orphan acceptance; DEVICE-003 remains open.


| 273 | 2026-10-08 | [x] WF-RESTART-001 standing rule:232 restarts authorized,175 always asks; all14 workflow/agent policies and regression updated. User confirms232 idle. MIM-DIRECT-004 minimal overlaye62bee77 activated by named-container restart; only2 class entries differ fromd2fd13c0, original non-jar backup recoverable. ExpectedIP/healthy CoreMCP/caption ready/Directavailable/zero initial sessions verified; Core/stockffmpeg/liveINI hashes unchanged,175 untouched. Original Copy Media3 rerun active. Legacy owned TT162.469s transport/ownership/FF7272/REW5263/pause/prefs32/server CC/power passes; one blank still needs bounded visual follow-up, not a readable PASS.21.803s invalid --stv-state invocation fails before playback, restores32/power, not runtime evidence. Both orders273 reviewed. |

| 272 | 2026-10-08 | [x] WF-POLICY-001 workspace-wide task-fix workflow propagation: identical policy in all14 Vibe projects' AGENTS/WORKFLOW (28 files), root rule and12 regression tests PASS. Necessary plugins fixed/tested/updated without repeat boundary approval;232 Core last resort only after proven client/plugin production gap, optional stock/older-client fallback and affected gates;175 installation PLUGINS ONLY. Stock legacy Teletext visual review passes readable STV text after148.187s transport gate. Plugin correction unit gates pass; controlled232 activation next, recording-idle question pending because existing API unavailable (zero clients verified). Owned legacy TT remains active independent work; do not restart server during it. Both orders272 reviewed; no Core/server payload mutation/publication. |

| 271 | 2026-10-08 | [x] WF-PLUGIN-001 standing user rule persisted in workspace AGENTS, Android AGENTS/WORKFLOW and FFmpeg AGENTS/WORKFLOW;10 workflow tests pass. Necessary proven plugin fixes/tests/test updates are authorized, not repeated cross-repo permission requests;175 Core/server files remain protected, installation limited to plugins. MIM-DIRECT-004 candidate cleanup correction and portable retention tests pass stock build/plugin/session/HTTP/caption/launcher suite. Deploy232/focused Copy next. Stock175 legacy Teletext148.187s transport/seek/pause smoke passes on121062d7,32 prefs/server CC/power1/3600000 restored; visual review pending, owned TT remains open. Both orders271 remove permission dependency and completed stock smoke. |

| 270 | 2026-10-08 | MINIMX-MIM-001 cause PROVEN: installed121062d7 exact hash, original231.986s/independently observed235.280s failures with32 prefs/server CC restored. Read-only210s open-file witness catches current Copy segment9 transition to `(deleted)` before publication; later M3U8 references9 but path is absent, matching actual current-session404 media_not_ready. Provider15s unlisted-file cleanup deletes active unfinished segment. No speculative client retry/fresh-source fix; asked user for other-repo FFmpeg-plugin correction/update232 permission. Both orders270 update this next-action dependency, completed native/stock rows preserved; parent remains open. No test running; manual awake restored captured1/3600000 at permission handoff. Completed helper and irrelevant older copied Pro traces retired recoverably, unique event/provider evidence retained. |

| 269 | 2026-10-08 | [x] PRO-EXIT-20261008 diagnostic request: preserved Pro `.29` logs before relaunch; Wi-Fi disconnect06:08:07CDT aborts Media/GFX sockets, ENETUNREACH immediately defeats reconnect and app-request finishes playback Activity. Process3769 survives, no new Vibe fatal/ANR/native exit;4332ms network interruption, AP cause unknown. No Pro APK/settings/control/source changes, diagnosis only (not recovery-fix closure). MiniMX rollback/exception-attribution121062d7 installer73698 succeeds219.46s/32 prefs restored; ineffective069 fresh-source recovery withdrawn after230.514s FAIL. New diagnostics have14 session JVM/54 backend/34 caption/26 MCP-health tests/build/inspection PASS; original Copy recheck and independent installed hash next. Both orders269 reviewed, dependency/matrix timing unchanged; no broad rerun or publication. |

| 268 | 2026-10-08 | Legacy HTTP observer1bb installed221.635s/32 prefs restored/hash proven. Owned source and DVB Off-On13 cues pass, but208.361s seek repeats MINIMX-MIM-001; separate2d diagnostic206.068/206.719s repeats fail. Provider new58469ms playlist pauses25s at17795ms then resumes26315/34715, still ready. Cause remains unproven; unopened HLS child gives unavailable, so added independent actual-MediaItem attribution (bounded/no raw URL/token). bd77 build12s/strict inspection/13+four JVM/54 contracts/26 MCP health/33 caption tests pass, install active. New generated120s/2s fixture recorded in ignored TOML for clean-display check. Both orders268 move shared failure before remaining legacy Teletext/stock smoke. Completed/configuration-only20 files24,203,456bytes retired recoverably; minimal unresolved evidence retained. No Core/plugin source mutation, publication or broad matrix rerun. |

| 267 | 2026-10-08 | Added MINIMX-MIM-002 after legacy Copy121.891s ownership snapshot failure despite real211 frames/active_copy. Equivalent legacy Direct HTTP/Teletext observer added; four JVM tests/53 backend contracts/build68s PASS, installation/physical correction pending. Media3 narrower Copy143.716s passes FF6638/REW6980/pause/readable DVB; original longer Off-On failure remains unproven, not silently closed. Both orders267 target real observer correction then remaining Copy diagnosis; no broad native rerun. Direct Gradle initially inherited Core JDK11, corrected explicit unified JDK17 and documented the boundary. |

| 266 | 2026-10-08 | Added MINIMX-MIM-001 after corrected Copy229.491s FF playlist stall/404; Off-On20s14 cues/21030ms passed, source intact. First144.367s caller omitted explicit DVB while STVOff, not runtime failure proof. [x] Owned Transcode/CEA control transport181.783s PASS on99a47797/actual232/SageTV7: ownership retained through FF/REW/pause/STVOff-CC1-CC2 cycle; fresh VAAPI/h264_vaapi job1791450842082 hardwareDecode+Encode true/deinterlaceOff. CC1 text visible with malformed row, clean visual display remains open.32 current preferences/server CC restored. Both orders266 advance Copy diagnosis/remaining display/fallback/no-orphans, no repeated native matrix or APK/Core modification. |

| 265 | 2026-10-08 | User completed232 MiniMX wizard and requested uninterrupted continuation. Actual Main Menu/automationReadytrue/public matching-context SageTV7/CCOff verified. Manual awake restarted from captured current settings; strict owned Copy/DVB caption/control gate active with private preference/server-CC restoration. Both orders265 remove completed setup prerequisite, keep ownership/backend/fallback/no-orphans then tablet; no repeated stock/native matrix or new APK. |

| 264 | 2026-10-07 | DEVICE-003 optional232 readiness connects99a with27 prefs restored, actual Configuration Wizard - Choose Language/automationReadyfalse/no playback. Added manual STV setup prerequisite/user asked asynchronously; do not treat API-ready/zero sessions as owned playback/GPU proof or wizard as transport failure. Both orders264 reconcile next unfinished wizard/ownership gates and current user DEVICE-003-before-DEVICE-002 directive. Stock baseline/NOT_WORKING reverse disposition preserved, parent remains open. |

| 263 | 2026-10-07 | [x] MINIMX-DVD-001 investigation disposition (parent DEVICE-003): NOT_WORKING native smooth reverse preview at-8 on AM2/API23, root cause UNPROVEN, not a hardware-unsupported declaration or playback PASS. Original123.328s and4s96.116s stall, async105.254s seek output failure; ineffective changes withdrawn. Rollback61s/three JVM tests reproduce exact99a47797, strict inspection/SDK restore251.622s/installed hash/27 prefs verified. Restored normal ALADDIN119.207s PASS, wall7280/media7262/video166/audio227/ratio0.997527/no drops; real HDMI cropped picture/nonsilent audio-35.37dB.116 affected DVD Python tests/52 backend/81 protocol tests PASS. Working normal/timed-skip/chapter/menu/SPU rows retained; no new runtime workaround. Both orders263 advance232 commissioning/owned/backend gates then tablet. Keep minimal unique unresolved-failure evidence, retire completed rollback/motion raw. |

| 262 | 2026-10-07 | MINIMX-DVD-0014s candidate1be fails reverse96.116s-8 despite input loading/696ms buffered/render733/queued840/no error;27 prefs restored. Existing async experiment105.254s also fails native seek output (render1/audio16), not a usable correction. Both runtime experiments withdrawn;61s rollback/three original JVM tests reproduce exact prior99a47797 APK. Restoring installed APK and normal DVD gate next before NOT_WORKING disposition/root cause unproven. Both orders262 reviewed, no ineffective workaround retained or full codec rerun. |

| 261 | 2026-10-07 | Add MINIMX-DVD-001 for actual123.328s reverse-8 preview stall on API23 AM2/Amlogic MPEG2, despite positive +2..64 output and earlier normal-play recovery. Candidate expands only exact reverse BUFFERING loading cap2->4s via existing callbacks, raw1MiB cap/skip/default/other tuples unchanged.52 backend/81 protocol tests PASS; JVM/build active, physical proof required. Both orders261 prioritize correction before remaining232/tablet; no runtime fix claimed yet. |

| 260 | 2026-10-07 | [x] MINIMX-DVD-TEST-006 (parent DEVICE-003) CLOSED. Ten oracle tests/actual stock175 MiniMX FF/RW/pause67.077s PASS on99a47797/27 prefs restored. FF FLUSH28/settled9607ms/source9588/ratio0.998022/max clock difference1514; RW FLUSH30/11088/source11086/ratio0.999820/max983. Each trims only one initial seek-guess sample; persistent/late drift still fails2000ms bound. Pause0ms source delta/Play A-V recovery. Stock SageTV7 dedicated keys retain timed-skip behavior, no profile edits or production clock/input changes. Both orders260 advance separate public-API decoder scans then232/tablet. |

| 259 | 2026-10-07 | Add MINIMX-DVD-TEST-006 after31.574s timed FF gate rejects initial2827ms seek-clock guess although subsequent samples agree within219ms and actual output advances. Ten oracle tests cover initial convergence, persistent offset, late divergence, frozen clocks and decoded epochs; corrected physical FF/RW/pause gate active. Existing2000ms settled bound retained/no runtime change. Both orders259 reviewed, DVD scan/232/tablet still follow. |

| 258 | 2026-10-07 | [x] MINIMX-DVD-TEST-005 (parent DEVICE-003) CLOSED. Three oracle tests/actual53.773s stock175 MiniMX chapter-index gate PASS on99a47797/27 prefs restored. Short200ms UP/DOWN chapter7->7/cells8->8; held3400ms UP7->10/cells8->11 and DOWN10->7/cells11->14, recovered1x A/V. Explicit public Sagex ordinal reads supplement Core MCP controls with no hidden fallback/server deployment. Initial39.763s counter failure is preserved as historical non-repeat proof; no runtime key change. Both orders258 advance dedicated FF/RW timed-key/decoder scan gates then232. |

| 257 | 2026-10-07 | Add MINIMX-DVD-TEST-005 after held-chapter39.763s decoder-count failure despite large forward jump/recovered output. Stock public chapter read works, three directional/unknown-index oracle tests pass; opt-in authored-ordinal physical witness active. Existing Core MCP remains control authority, explicit Sagex supplementary read only, no hidden fallback/deployment/Core changes. Both orders257 prioritize actual chapter proof then scans/232; tablet next unchanged. |

| 256 | 2026-10-07 | [x] MINIMX-DVD-TEST-004 (parent DEVICE-003) CLOSED. Five oracle tests and actual57.853s cursor gate PASS on99a47797/stock175/27 preferences restored. RIGHT target1319906/decoded anchor error9495ms/FLUSH8->10, LEFT1185601/error2819ms/FLUSH10->12, fresh A/V; Back/Play cancel without seek. Default4000ms unchanged, explicit15000ms matches user's approximate DVD acceptance. Synchronous --capture-cursor run103.950s allowed stock cursor expiration/no fresh flush; screenshot measurements are not playback failure or precise landing PASS. Use independent HDMI without delaying Center on this slow box. Immediate old server echo is diagnostic only, bounded FLUSH/source/A-V remain required. No production changes. Both orders256 advance held chapters/scan then optional232. |

| 255 | 2026-10-07 | [x] DEVICE-003 ALADDIN functional native playback126.237s PASS on99a47797/stock175 with explicit15000ms DVD landing allowance (user accepts approximate positions), target540000/observed544072. Eight-second device-clock window9329ms/media9314/video215/audio292, ratio0.998392/no output drops. HDMI12s shows actual changing picture,360 frames/248 changes above1 mean pixel; not HD200 smoothness/every telecine frame certification. Original2s positioning gate159.831s failed despite active output, not hidden. Add MINIMX-DVD-TEST-004 after premature cursor echo27.356s failure; five tests pass, corrected focused run active.27 preferences restored after completed groups. Both orders255 advance to cursor/remaining remote controls then232; tablet closure policy unchanged. |

| 254 | 2026-10-07 | [x] MINIMX-DVD-TEST-003 (parent DEVICE-003) CLOSED. Corrected packed-selector159.261s and bounded main-title HDMI witness191.317s PASS on99a47797/stock175;27 preferences restored. Requested/applied AC3 wire0xBD81 and enabled SPU0x40 verified. HDMI shows readable ENG DVD SPU/main_feature/CUE016/PTS30.000/chapter2; six one-second audio spectra all dominant440Hz (selected Spanish track), RMS-35.67..-35.02dB. Five selector/hold tests pass. Initial logical1/0 failure was test units, no runtime fix needed; later root capture excluded. Both orders254 advance to ALADDIN motion/remaining remote controls then optional232, not repeated codec rows. |

| 253 | 2026-10-07 | User directs DEVICE-003 completion then prioritized DEVICE-002 fixes. After documented best effort, genuinely uncorrectable tablet rows become NOT_WORKING with reasons, not PASS, and parent closes once all rows are dispositioned. Corrected DVD wire-selector gate159.261s PASS/27 preferences restored; bounded title hold and five selector/hold tests ready, physical SPU/tone next. Both orders253 reviewed, no completed matrix repeated. |

| 252 | 2026-10-07 | Add MINIMX-DVD-TEST-003:239.410s authored controls had A/V recovery for all8 actions, but caller expected logical1/0 instead of actual raw0xBD81/0x40. Source MiniDVDPlayer/Media3/DvdAudioStreamCode verifies units; CLI now accepts hex or prior decimal with explicit wire-ID help,4 tests PASS. Narrow corrected selector gate14819 active; readable SPU/switched audio still require physical proof. Later12s capture was root after finite title, excluded from title proof. Both orders252 prioritize that focused oracle/visual gate then remaining DVD/232, no inferred runtime fix or whole matrix repeat. |

| 251 | 2026-10-07 | [x] DEVICE-003 IJK unsupported CEA boundary93.441s PASS on99a47797: playable generated MPEG2/AC3 with no false legacy-caption negotiation/callback/event225 bytes, visible full-screen generated image,27 prefs/server CC restored. Not CEA rendering or hardware MPEG2 proof; DVB bitmap remains unavailable in original IJK native text API, supported paths already verified through other players. Both orders251 advance to native authored DVD chapters/audio/SPU/pause, ALADDIN/remote controls and optional232; guarded authored controls active. |

| 250 | 2026-10-07 | [x] MINIMX-CAPTION-TEST-003 (parent DEVICE-003) CLOSED:5 oracle tests plus corrected IJK TT187.629s, representative Media3 STV TT119.818s and all actual local DVB pause rows (Media3109.886s, legacy111.073s, GSY-M3107.213s, GSY-legacy109.683s) PASS on99a47797. Each holds state3/clock, PLAY resumes new real bitmap cues, readable cropped output,27 prefs/server CC restored. Replaces only previously ignored local pause claims; original positive codecs/toggle/seek/STV wire evidence retained. Both orders250 advance to IJK unsupported-caption boundary (71367 active), DVD/232. Retire corrected/completed caption raw after compact record. No broad matrix restart or new APK since99a47797. |

| 249 | 2026-10-07 | [x] DEVICE-003 actual local DVB pause: Media3109.886s and legacy111.073s PASS on99a47797, paused state3/clock hold, PLAY/new real bitmap cues, readable cropped images,27 app prefs/server CC restored. These replace only the earlier unexecuted pause claims; original Off/On/seek evidence remains valid. GSY-Media3 then GSY-legacy pause continue39671; MINIMX-CAPTION-TEST-003 stays open. Both orders249 target remaining two GSY pauses, not repeated codec or complete caption rows. |

| 248 | 2026-10-07 | [x] MINIMX-IJK-CC-001 (parent DEVICE-003) CLOSED: guarded discovery/prepare retry on99a47797,3 discovery/52 backend/3 source/31 STV tests/build74s/inspection/install233.829s/prefs preserved; corrected IJK TT187.629s readable local text/Off-On77 cues/FF-REW/actual pause and server CC restore. Representative unchanged Media3 STV TT119.818s readable event225 text/idle-independent clock/no local duplication/27 prefs/server CC restore passes. Prior16.911s ADB setup timeout occurred before checkpoint/runtime controls; wrapper reconnect verified222/AM2 and fresh guard passed, no ordered command replay. MINIMX-CAPTION-TEST-003 still needs four DVB pause rows in39671; both orders248 remove completed IJK fix and smoke. Retire corrected failure/raw after compact record. |

| 247 | 2026-10-07 | [x] DEVICE-003 corrected IJK local TT187.629s PASS on99a47797: page888 normal-menu selection/readable cropped two-line caption, Off/On20s77 updates/media20143ms/natural gap1.769s, FF/REW5s/new TT cues and actual pause-clock hold/PLAY cue resume,27 prefs/server CC restored. RecoveryMs=-1 is unsupported native timing, not measured latency. MINIMX-IJK-CC-001 still waits representative unchanged-extractor smoke before closure. Session76802 runs that Media3 STV TT smoke then four targeted DVB pause gates; both orders247 remove completed IJK functional retry, keep only remaining gates. No complete codec matrix repeated. |

| 246 | 2026-10-07 | Candidate99a47797 build74s/install233.829s/hash match/27 prefs restored/full static245 PASS. IJK128.329s now selects/renders TT58 updates, then fails a generic-overlay Off oracle although native TT is correctly disabled. Add MINIMX-CAPTION-TEST-003: codec-specific Off/seek and execute previously ignored local pause flag;5 new tests PASS. Correct earlier four local DVB pause claims: flag requested but not executed, valid image/Off-On/seek evidence retained. IJK corrected oracle physical active; both orders246 prioritize only affected caption gates, not completed codec rows. |

| 245 | 2026-10-07 | Add MINIMX-IJK-CC-001 (parent DEVICE-003): actual IJK Teletext125.395s discovers page888/raw21504 but remains DISABLE_TRACK8192 (not a CEA slot), no local cues/overlay,27 prefs and server CC restored. IJK lacks extractor onTracksChanged retry. Candidate inventory-only default-no-op Base hook plus IJK UI/session/player/ready guarded retry at discovery and prepare; no getter polling/transport/clock/Core change.3 source tests PASS; build/install/physical pending. Other extractor hooks stay no-op, so affected IJK and focused representative caption/lifecycle gates selected, not completed whole codec matrix. Both orders245 prioritize correction. |

| 244 | 2026-10-07 | [x] DEVICE-003 IJK UK hardware AVC controls191.423s scripted FF/REW/large jumps/pause/STOP-exact rewatch PASS,27 preferences restored. Independent final HDMI shows changing real picture at2/6s and nonsilent audio-35.7/-7.9dB; renderer counters are unavailable, so final capture is not a separate visual witness for every preceding command. IJK Teletext normal-menu local gate runs78985; CEA/DVB unsupported backend boundaries still distinct. Both orders244 target captions/DVD/optional232, not repeated IJK startup/controls. |

| 243 | 2026-10-07 | [x] DEVICE-003 optional System-player76.567s bounded fallback PASS: exactly one android_system_player_error fallback resolves to actual Media3/Amlogic MPEG2, independent full-screen generated HDMI picture/audio-33.6/-7.2dB,27 preferences restored. This is fail-safe fallback proof, not System decoding PASS. IJK UK AVC seek/jump/pause/STOP-restart group now runs session27610. Both orders243 remove completed system scope; remaining IJK/DVD/optional232 continue. |

| 242 | 2026-10-07 | [x] DEVICE-003 GSY/legacy77.923s safety-only PASS: actual truncated AVC safe failure/recovery/no new crash/death, zero positive rows requested,27 app prefs restored. All four extractor-backend malformed rows complete; static missing-PTS plan, H263/AV1/DRM exclusions remain explicitly not physical PASS. Full static source validation PASS at loaded local240; current host task-order check covers workspace mirror242. Both orders242 move to system/IJK/DVD/optional232 only. |

| 241 | 2026-10-07 | [x] DEVICE-003 legacy85.671s and GSY/Media3166.220s safety-only PASS: actual truncated AVC fails/recovers without process death/new crash, zero positive rows repeated,27 preferences restored each. Earlier9.344s CLI rejected invalid gsy-engine=auto before playback, corrected invocation uses supported value; not a runtime failure. Static fault-plan/unsupported/DRM limitations remain. Final GSY/legacy active in session78009; both orders241 remove completed containment paths from next actions. |

| 240 | 2026-10-07 | [x] DEVICE-003 IJK startup/picture scope: generated1080i MPEG2/AC3 starts70.089s with real full-screen advancing burned PTS70.771->74.741 across4s/HDMI audio-32.1 mean/-6.7peak dB, but actual decoder mpeg2video/software, not hardware PASS. UK AVC starts76.189s with actual OMX.amlogic.avc.decoder.awesome/hardware, readable independent HDMI picture and nonsilent audio-33.5/-5.0dB.27 app prefs restored each group; broadcast capture private. All156 MCP unit tests PASS. IJK controls/captions/system/DVD/optional232 and remaining backend containment stay open; both orders240 reviewed, no runtime/Core change. |

| 239 | 2026-10-07 | [x] DEVICE-003 Media3 safety-only74.918s PASS: zero positive rows requested, actual truncated AVC safely fails/recovers without process death/new crash,27 preferences restored. MPEG4 missing-PTS result validates a static injection plan, not physical playback; H263 wire renegotiation, AV1 hardware absence and unavailable authorized DRM asset remain explicit exclusions. Both orders239 move to IJK/system and remaining backend/DVD/optional232 scope; no completed codec repetition. |

| 238 | 2026-10-07 | [x] DEVICE-003 remaining GSY caption rows: Media3 DVB169.713s, legacy Teletext145.908s and legacy DVB167.712s PASS; independently readable cropped captions, FF/REW5s, pause/PLAY, server CC verified restore and27 app preferences restored. All four extractor paths now complete for CEA/Teletext/DVB. Explicit local DVB is not STV CC1/CC2 bitmap proof. Safety-only harness selection adds2 tests (plus6 inventory/2 capture PASS), preserves normal positive selection and runs existing negative scope without repeating completed positive rows; static plans/unsupported assets are not physical PASS. Both orders238 target only remaining containment/IJK/system/DVD/optional232. No new runtime/APK/Core/server change. |

| 237 | 2026-10-07 | [x] DEVICE-003 GSY/Media3 stock175 Teletext145.198s PASS/readable cropped page888 STV text/idle-independent delivery/seeks5s/pause/PLAY/server CC verified restore/27 app prefs. Sequential54575 now GSY/Media3 DVB then legacy TT/DVB. Both orders237 target unfinished rows only, no repeated codec/caption cohorts. Full static source validation PASS; host task-order check covers current workspace revision237. |

| 236 | 2026-10-07 | [x] DEVICE-003 legacy stock175 DVB168.369s PASS: explicit normal-menu local bitmap, Off-On20s11 new nonempty/media19029ms/natural gap5.307s, FF/REW5s/pause/readable cropped bitmap, server CC verified restore/27 app prefs restored. Media3 and legacy caption types complete; four GSY TT/DVB gates run sequentially in process54575/stop on first failure. Both orders236 target unfinished scopes only; not STV CC1/CC2 bitmap proof, no completed codec/caption repetition. |

| 235 | 2026-10-07 | [x] DEVICE-003 legacy stock175 Teletext146.408s PASS: actual page888/STV callback/idle-independent delivery/FF-REW5s/pause/PLAY, independently readable cropped rows/no local duplication, server CC verified restore and27 app prefs restored. Legacy DVB active in process68847. Both orders235 name only unfinished GSY TT/DVB/legacy DVB/backend/transport scopes; no completed row repetition. |

| 234 | 2026-10-07 | [x] DEVICE-003 GSY/legacy stock175 CEA169.713s PASS: standard CC/wire states, FF/REW5s/pause/PLAY, independently readable PTS rows/no local duplication, server CC verified restore and27 app prefs restored. All four typed CEA paths complete. Remaining legacy Teletext then DVB run sequentially; both orders234 target unfinished TT/DVB/backend/transport gates, no repeated completed codecs/CEA. |

| 233 | 2026-10-07 | [x] DEVICE-003 GSY/Media3 stock175 CEA172.258s PASS: Off/CC1/CC2 wire, FF/REW5s/pause/PLAY, independently readable post-seek PTS rows/no local duplication, public server CC verified restore and27 app prefs restored. GSY/legacy CEA active; both orders233 target only unfinished captions/backends/transports, no repeated codec or completed caption rows. Read-only232 API readiness also confirms contract1/state ready/zero active sessions/reservation available; not GPU/transcode/device commissioning proof. |

| 232 | 2026-10-07 | [x] MINIMX-STATE-001 (parent DEVICE-003): Core VideoFrame.setCCState persists LAST_CC_STATE separately from app prefs. Caption gate now captures public server CC before controls, restores/verifies exact state before disconnect on success/failure; restore failure nonzero, no assumed Off/Core/plugin/runtime changes.5 restore/31 authority/38 automation tests plus actual legacy CEA167.113s pass checkpoint/restore/27 app prefs restored. [x] DEVICE-003 legacy CEA: Off/CC1/CC2/Off/CC1/wire, FF/REW5s/pause/PLAY and independently readable post-seek PTS rows/no duplication. Historical server state before earlier first cycle was not captured; cannot claim it recovered. Both orders232 remove completed state child, target GSY CEA and remaining caption/player/transport scope; no completed rows repeated. |

| 231 | 2026-10-07 | [x] DEVICE-003 Media3 native DVB168.448s PASS on stock175 Taskmaster: normal-menu explicit DVB/local bitmap, Off-On20s yields13 nonempty cues/media20032ms/natural gap3.305s, FF/REW5s recovery/pause/readable bitmap,27 app prefs restored. Not an STV CC1/CC2 bitmap assertion. Add MINIMX-STATE-001 after source proof server LAST_CC_STATE persists independently of app prefs; public caption API checkpoint/restore/verifies before disconnect,5 restore/31 authority/38 automation tests pass, legacy CEA physical preservation pending. Historical pre-first-cycle server value unknown; no guessed Off restoration. Both orders231 target that safe test workflow before remaining caption backends; no APK/Core/server patch or completed rows repeated. |

| 230 | 2026-10-07 | [x] DEVICE-003 Media3 stock175 Breakfast Teletext128.859s PASS: real page888/services1/PES475/cues65, idle OSD-independent drain63/media6502ms/wire28, FF/REW5s recovery/pause/PLAY/27 prefs restored. Independently readable cropped Teletext rows in STV and no local duplication; raw extractor candidate CEA tracks are not actual UK services (existing user UI filters unobserved CEA). Both orders230 target Media3 DVB then other caption backends/IJK/system/remaining DVD/optional232/containment. Original broadcast captures stay private, not public release screenshots. |

| 229 | 2026-10-07 | [x] DEVICE-003 Media3 stock175 native CEA callback gate163.958s: Off/CC1/CC2/Off/CC1 state/wire continuity, FF/REW5s recovery, paused clock/PLAY resumed callbacks,27 prefs restored. Independent CC1/post-seek screenshots have readable PTS rows/no duplicate local overlay; CC2 blank on channel1 fixture is not additional-service proof. Initial8.551s invocation rejected incompatible local track-codec/event225 CLI before playback; corrected wire-only command passes, not a runtime fix. Both orders229 target remaining TT/DVB/other backends/IJK/system/DVD controls/optional232. Completed/corrected child raw112files348297499bytes retired recoverably after compact recording; current caption raw remains pending cohort review. |

| 228 | 2026-10-07 | [x] MINIMX-DVD-TEST-002 (parent DEVICE-003): root-menu142.172s pause assertion was not title evidence. Explicit authored-title subject verifies exact public MediaFile65513423/volume, known Play/new non-menu cell before checks;3 subject/38 automation tests pass, normal file behavior unchanged. Actual native title lifecycle134.826s PASS: same connection1, OMX init1/2/3 release0/1/2, HOME A/V, manual pause/explicit PLAY, no PID teardown,27 prefs restored. [x] MINIMX-SURFACE-001: exact AM2/API23/OMX MPEG2 renderer reinitialization now verified on Media3 (202.541s output/pause/replay plus corrected exit), legacy137.393s, GSY/Media3135.395s, GSY/legacy135.355s and native DVD134.826s, plus readable authored menu/title56.930s/cadence0.96716/independent HDMI timestamps/audio. F901 build52s/2 policy JVM/52 backend/3 startup/6 inventory/APK inspection/full validation pass; single install234.467s/hash matches. No source/audio/clock/seek/Core change, other device/codec policy unchanged. Original214.298s video freeze (queued468/rendered386/no error) corrected. Both orders228 remove these completed children, target CC/IJK/system/remaining DVD/optional232/containment. Completed/corrected raw retires after compact recording; no broad codec rerun. |

| 227 | 2026-10-07 | Add MINIMX-DVD-TEST-002 after142.172s native DVD root-menu lifecycle: HOME video/audio return passes, generic manual-pause assertion fails on menu, not title evidence. Explicit verified authored title selection added to existing lifecycle runner through public context/media/remote control, no production/core change;3 subject/38 automation tests pass, physical title retry pending. Both orders227 target that remaining affected Surface acceptance; no codec matrix repetition. |

| 226 | 2026-10-07 | [x] MINIMX-DVD-TEST-001 (parent DEVICE-003): reject looping-menu/startup cadence and preserve exact opaque/scientific-looking/leading-zero uiContextHint. Discover public control context then verify authored volume before keys.1 new/76 ADB tests,4 cadence/3 menu tests pass in configured Python. Actual corrected stock175 root/Languages/root/main cycle56.930s PASS, phases2.794/3.980/2.903/2.539s, visible highlights/main label,27 prefs restored. Actual title cadence5s/device6273ms:6067ms media/6083ms player/182 video/197 audio, ratio0.96716. Independent12s HDMI burn72.673->78.679 over6s and audio-38.2/-7.1dB verifies title, not slow menu. No new APK/Core/server change; Native DVD HOME/return still needed before Surface closure. Both orders226 remove completed harness prerequisite. |

| 225 | 2026-10-07 | Add MINIMX-DVD-TEST-001: authored startup93.007s measures looping menu as title despite CLI skip; reject false slow verdict. Menu-cycle fails on scientific-notation conversion of opaque hex context. Preserve uiContextHint string and discover/verify actual public control context;1 new/76 ADB tests,4 cadence/3 menu tests pass in configured Python, physical retry pending. Both orders225 put corrected DVD subject/context gate before Surface closure; no APK/Core/server change or repeated codec matrix. |

| 224 | 2026-10-07 | [x] DEVICE-003 GSY/legacy affected MPEG2 lifecycle135.355s on F901: actual legacy_exo delegate/Amlogic OMX, same connection1, init1/2/3 release0/1/2, HOME/manual pause/PLAY/teardown,27 prefs restored. Four affected renderer paths now have recovery evidence; both orders224 target authored native DVD Surface/title/menu gate before closing MINIMX-SURFACE-001. No broad codec repetition. |

| 223 | 2026-10-07 | [x] DEVICE-003 GSY/Media3 affected MPEG2 lifecycle135.395s on F901: actual media3 delegate/Amlogic OMX, same connection1, codec init1/2/3 and release0/1/2, HOME/manual pause/PLAY/teardown,27 prefs restored. Both orders223 target remaining GSY/legacy and authored DVD Surface gates; parent MINIMX-SURFACE-001 remains open. No native codec rerun. |

| 222 | 2026-10-07 | [x] MINIMX-EXIT-001 (parent DEVICE-003): Android6 force-stop exposes previous phone task and restarts background process after active playback. Browser-settle-only standalone PASS did not generalize (post-playback177.680s FAIL); withdrawn. Existing MCP shutdown now sends HOME once, observes bounded stable background then one stop, no exit/stop replay or APK/server/runtime change.6 revised tests and actual stock175 legacy MPEG2 lifecycle137.393s PASS: same connection1, video init1/2/3 and release0/1/2, Home/manual pause/explicit Play, stable_background/noPID/stoppedtrue/no pending termination,27 prefs restored. Surface child still needs GSY and DVD; both orders222 remove completed exit from next-action wording. Corrected exit raw can retire after compact recording; unique Surface failure remains live. |

| 221 | 2026-10-07 | Add MINIMX-EXIT-001 after F901 Media3 group202.541s passes HOME A/V/manual pause/replay but Android6 queues phone launcher after one force-stop. Existing MCP exit now observes bounded stable browser before stop, no replay;6 regression/all155 MCP tests and standalone actual teardown pass, post-playback repeat pending. MINIMX-SURFACE-001 candidate installed234.467s, hash matches/27 prefs restored, renderer reinit1->2 then3 with same connection; other backend/whole lifecycle gates remain open. Both orders221 put exit verification before remaining scope; no unrelated codec rerun. |

| 220 | 2026-10-07 | Add MINIMX-SURFACE-001 after Media3 stock175 lifecycle214.298s: audio resumes but MPEG2 video stops after Surface replacement, no crash;27 prefs restored. Fullscreen labelled MPEG2 HDMI picture independently verified before HOME, capture audio-37.0/-7.2dB. Exact AM2/API23/MPEG2 renderer replacement candidate added with narrow boundary tests; physical correction pending. Read-only ro.product.device verifies AM2;gxbaby is board, not Build.DEVICE. Both orders220 put this targeted fix before remaining MiniMX gates, no broad codec rerun. |

| 219 | 2026-10-07 | [x] DEVICE-003 GSY/legacy native scope448.341s:12 hardware rows and3 explicitly unsupported VP8/VP9 rows, actual legacy delegate, B69 and27 preferences restored. Four native cohorts now complete; both orders219 move to IJK/system/lifecycle/captions/DVD/optional232, not another codec rerun. Short clean EOF remains distinct from sustained fullscreen/HDR display proof. Media3 lifecycle gate started on long generated MPEG2/AC3/CEA fixture. |

| 218 | 2026-10-07 | [x] DEVICE-003 native legacy445.706s and GSY/Media3466.971s:12 hardware rows and3 explicit unsupported VP8/VP9 rows each, B69,27 prefs restored. GSY/legacy active; both orders218 name remaining cohorts, no repeated completed ones. [x] Installer budget workflow: finite per-device30..900/default180, MiniMX600 selected through Config/CLI/server/verified SDK helper;149 MCP tests pass, no retry/reset or changed active aliases. Actual client identity matches commissioning; public captions.get reports stock175 SageTV7.xml, correcting earlier visual SageMC inference. ExpectedSTV persisted privately;232 must be verified independently. |

| 217 | 2026-10-07 | [x] MINIMX-IO-001 (parent DEVICE-003): volatile ARM32 counters/read-only monitor-free getters correct the proven main-thread diagnostic lock behind loader SIZE I/O.2 new blocked-monitor/publication plus13 existing Pull JVM tests,53s build/APK inspection pass. InstalledB69b4cf0 hash/complete status verified after180s SDK caller timeout, no overlapping retry/reset. Actual stock175 Media3 MPEG2 STOP->PLAY/output recovery128.306s PASS; diagnostics responsive and all27 prefs restored. No source bytes/cache/decoder/seek change or general SIZE-timeout claim. Both orders217 remove completed fix from next-action wording; remaining legacy/GSY/IJK/lifecycle/caption/DVD/optional232 scopes continue.12 earlier codec rows remain valid, no blanket rerun. |

| 216 | 2026-10-07 | [x] DEVICE-003 native Media3 codec scope:12 hardware rows pass,3 VP8/VP9 rows explicitly unsupported,27 prefs restored in both guarded batches807.239/209.935s. Hardware inventory skip has6 behavior/2 capture tests; unknown/malformed inventories still run real gate. New MINIMX-IO-001 after long MPEG2 STOP->PLAY: ANR trace proves main debug snapshot blocked on synchronized retained-source counter while loader waits for SIZE. Volatile non-blocking counter candidate passes2 regression/13 existing Pull JVM tests,53s build/inspection; physical correction pending. Old private27 prefs restored after isolated Dev stop/launch. Both orders216 target this fix before remaining matrix, no broad codec rerun. Tablet read-only IJK review adds matched1080 bitstream/remux test and native-build gap; workingTS nakedCSD prevents unsupported causal claim. |

| 215 | 2026-10-07 | [x] DEVICE-003 guarded update/startup UI: installedB7fd7dd9 hash matches,27 prefs preserved/restored; TV-browser choice applies immediately on Settings return. Android6 missing cmd/implicit resolution and defective pidof/header-only ps diagnosed and corrected in MCP tooling;142 tests plus actual launch/status pass. Install exceeded caller120s while dex optimization completed; inspect hash/no duplicate install, document210s caller budget. First six native Media3 MPEG2/MPEG4/AVC rows pass; actual AVC HDMI picture/audio reviewed, early MPEG2 launcher-only capture excluded. Both orders215 reviewed, older limits explicit. Tablet IJK/MIM still open; add primary-source Annex-B CSD hypothesis, not an unproven runtime fix. |

| 214 | 2026-10-07 | [x] DEVICE-003 HDMI readiness: user reconnects USB capture and requests continue; Windows enumerates USB Video/Digital Audio, FFmpeg five-second recording9,277,646bytes succeeds and independently reviewed1920x1080 MiniMX server browser. Both orders214 move currently connected MiniMX first; tablet IJK/owned Transcode remain open, its completed rows are not repeated. Guarded MiniMX update/UI/full stock-first matrix begins; no matrix PASS claimed. |

| 213 | 2026-10-07 | [x] DEVICE-003 CLI diagnostic cleanup gate: finally closes only each invocation's AdbClient across success, early return and exceptions. Four new focused tests/all135 MCP tests pass; real MiniMX read-only connection5.636s leaves no orphan shell. Three earlier exact-target abandoned shells closed individually without shared ADB/key/app changes. Both orders213 reviewed; tablet IJK/owned Transcode and MiniMX full matrix/physical USB readiness remain open, ordering unchanged. |

| 212 | 2026-10-07 | [x] DEVICE-003 Android6 diagnostic-control gate: replace new Am --receiver-foreground option with equivalent stable -f0x10000000, no SDK probe/replay; actual MiniMX/API23 and Samsung/API33 capability queries pass.131 MCP tests/49 affected contracts pass.22 MiniMX video entries are name-classified inventory, not playback. TV-browser settings-return correction builds40s/12 focused tests; physical UI pending. Host DirectShow/PnP has no USB HDMI capture; user asynchronously asked to connect it, camera/Logi Capture not substitutes. Reviewed both orders212 with explicit physical-capture prerequisite, no completed matrix rerun. Tablet validated56603387 installed/stream-hashed and its actual saved power7/600000 restored; IJK queue experiment fully withdrawn/open. |

| 211 | 2026-10-07 | [x] DEVICE-003 readiness gate: actual authorized MINIMX/AM2 Android6.0.1/API23 ARM32, existing Dev app/identity preserved, HDMI capture confirmed, no TV/Leanback features explains phone browser. New-shell PTY handshake corrects echoed/redrawn diagnostics without runtime replay;65 ADB/130 MCP tests plus read-only222/51 physical connections pass. Full MiniMX matrix remains queued after current tablet work. TABLET-IJK-001 sync candidate's first-frame/control positives are invalid as visual PASS: two settled captures remain blank, actual Exynos decoder confirmed by bounded MediaInfo; withdraw queueing exception/test rather than ship an unproven fix. Both orders211 remove the completed readiness prerequisite, preserve remaining open fixes. |

| 210 | 2026-10-07 | User adds DEVICE-003 MiniMX Android6.x/.222. Read-only ADB/model/features/package readiness delegated in parallel now; full matrix/startup/gear-access investigation queued after current tablet work. Both suggested orders210 preserve the dependency and stable existing IDs. No app install/settings/server change on MiniMX is authorized by this readiness phase. |

| 209 | 2026-10-07 | [x] TABLET-DVD-001 - Safe missing native DVD decoder, child DEVICE-002. Explicit Native uses each backend's policy-filtered MPEG-2 selector before player creation; known-empty produces bounded feedback/stock EOS with no audio renderer. Auto/Hybrid wait for actual unsupported MPEG-2 track discovery, preserving Core MiniDVDPlayer's empty native OPENURL before first transformed title payload (verified source lines1230/1804/1837). Initial da25 guard wrongly rejected that transition; corrected6b71b3fc passes6 policy JVM/3 source/81 DVD contracts,64s compile/inspection. Stock175 Native safe refusal37.277s has no playing audio/crash; prior explicit-native Media3/legacy-request37-39s both resolve through commissioned Media3 DVD engine, not separate legacy physical proof. Ordinary AVC pause/STOP-rewatch76.115s on da25 and current6b71 actual A/V throughout layout gate pass. Optional232 transformed DVD93.543s renders Exynos AVC/readable authored title, near-real-time ratio0.999422 with device-clock cadence, chapter+/chapter-/pause/play recovery. No stock native MPEG-2/menu/SPU or strict Direct ownership claim. [x] DEVICE-002 posture/touch layout91.196s: all four requested rotation values retain declared landscape2000x1200, real A/V, normal900ms touch opens menu, all four right-icon column pairs align and Back retains output; original two rotation settings and15 app prefs restored.4 layout-oracle tests pass; initial /dev/tty hierarchy probe was a tooling failure, corrected unique device-temp dump is removed in finally. Completed codec63 raw files/29,112,623 bytes retired recoverably after compact recording. Both orders209 advance to IJK and full owned-support isolation, no broad matrix restart. |

| 208 | 2026-10-07 | Add TABLET-DVD-001 after valid stock175 native DVD starts audio without any MPEG-2 video decoder. Optional232 transformed main feature70.498s passes actual Exynos AVC and independent fixture-label review; native absence must fail safely, not claim successful playback. Both orders208 prioritize the narrow unsupported-DVD correction and its affected positive/negative gates; no broad codec matrix repeat. |

| 207 | 2026-10-07 | [x] TABLET-CC-001 - Caption source authority and owned Copy continuity, child of DEVICE-002. C56c631a native CEA preference suppresses duplicate source-tap delivery while retaining stripped-output fallback; exact malformed708 unchecked offset signature becomes a checked subtitle-only error through each stock TextRenderer. Encoded-only isolation76.150s proved the source choice; rejected side-only725 trial75.024s and pre-guard ABE48.188s are not successful builds. Four signature/six adapter JVM, three authority/52 backend contracts, compile/inspection pass. Stock175 native DVB88.579s and232 CEA Media394.269s, legacy110.676s, GSY/Media3100.535s, GSY/legacy104.999s pass CC states/seeks/pause with independently readable output. Strict owned Copy20s Off/On104.151s passes with10 new nonempty cues,19.136s media progress, natural gap5.189s, readable DVB and FF/REW recovery; earlier5s failure was an inadequate observation interval, not a persistent freeze. Fifteen preferences restored. Compact evidence artifacts/results/DEVICE-002/matrix.json; no server-producer repair, full-GPU or physical speaker claim. Both orders207 remove this completed child and proceed to native/optional DVD capability gates, not another broad matrix. |

| 206 | 2026-10-07 | [x] TABLET-CC-001 CEA correction sub-gate: native-source preference plus narrow checked-error containment, C56c631a, stock175 native DVB88.579s and all four232 CEA paths pass with independently readable snapshots, CC states/seeks/pause;4 signature/6 adapter JVM,3 authority/52 backend contracts/inspection pass. Copy20s repeat remains, so parent not closed. Verified tablet232 STV SageTV7.xml. Completed audio8 raw reports/17,077 bytes retired recoverably after compact result recording. Both orders206 replace completed CEA prerequisite with next Copy continuity gate, no unrelated matrix rerun. |

| 205 | 2026-10-07 | [x] DEVICE-002 decoded audio track1/0 switching passes on725 with hardware AVC/decoded PCM/offset0: Media357.795s, legacy52.952s, GSY/Media358.402s, GSY/legacy53.473s;15 preferences restored, no speaker-audibility claim. Native-source CEA preference compiled but new physical startup exposes exact unchecked Cea708 packet-offset failure, not a fatal app crash. Add caption-only checked-error adapters using existing stock TextRenderer recovery, pinned Media3/legacy APIs;4 signature JVM/60s compile pass, adapter/physical checks pending. Both orders205 reviewed; caption correction remains first, completed audio leaves next-action wording. |

| 204 | 2026-10-07 | [x] TABLET-CC-001 source isolation: side-channel OFF encoded CEA passes76.150s and independently readable PTS rows. Withdraw failed side-preference; new candidate prefers actual native CEA evidence through existing MiniPlayerPlugin, leaving source-tap fallback for stripped output and bounding pending queues.2 authority/38 automation source tests pass; build and affected physical proof pending. Native decoded-audio four-backend batch running. Both orders204 reviewed; only affected caption branches rerun, no repeated codec/device matrix. |

| 203 | 2026-10-07 | [x] TABLET-MIM-001 safe ordinary Fixed boundary on stock175:105.145s actual H.264/Exynos, explicit unsupported reason, seeks/jumps/pause/STOP-rewatch,15 settings restored; full owned support remains open. [x] DEVICE-002 native GSY/Media3 Teletext94.872s and GSY/legacy DVB87.817s independently readable. Add TABLET-CC-001 after232 CEA wire-only88.305s exposes visible overlapping text with two callback sources. Side-channel-only trial725 still has malformed pre-seek text/FF audio-recovery failure; encoded-callback isolation next. Owned Copy short continuity row remains open,20s repeat next. Both orders203 prioritize caption correction; no native-codec/device matrix restart. |

| 202 | 2026-10-07 | TABLET-MIM-001: fresh-session replacement also fails to restore playback, so withdraw both source-capability and reconnect experiments. Add conservative pre-negotiation native fallback guard for opted-in Transcode; retain native capability inventory and Copy/off, expose reason and user notice. Stock175 currently has no MIM HTTP API, but ordinary Fixed produces actual Exynos H.264; do not claim a Direct-only fault PASS there. Add fault precondition and explicit ordinary-Fixed oracle;53 source/MCP contracts plus5 policy/12 session JVM are selected affected validation,91s build before notice. Current proof and remaining tablet gates come next; full owned MPEG-2-less Transcode needs optional stock-plugin recovery, remains open. Both orders202 reviewed, no broad matrix restart. |

| 201 | 2026-10-07 | TABLET-MIM-001 progress: optional MPEG-2 source acceptance now starts owned H.264 and readable CEA on232, but a later restart502 keeps the full gate open. A real Direct-only fault proves socket reconnect retains unsupported source capabilities; add pre-first-frame unsupported-video detection in both Exo paths/GSY delegates and use existing fresh-session handoff for this case.5 policy/13 session JVM,60 MCP ADB and6 lifecycle contracts pass;76s build and APK inspection pass before full-session adjustment. Update both order stamps201; dependency order unchanged, no unrelated matrix rerun or server Core mutation. |

| Revision | Date | Change |
|---|---|---|
| 200 | 2026-10-07 | [x] TABLET-IJK-001 diagnostic sub-gate: new first-frame oracle correctly rejects blank UK AVC and accepts independent visible progressive AVC MP4/TS on actual Exynos hardware. Probe-packet-retention runtime experiment does not fix it, fully reverted; native bug remains open. [x] DEVICE-002 four typed backend controls, additional Media3 visual HEVC/VPx and visible Legacy/GSY captions pass.232 owned Copy121.551s passes strict ownership/DVB/seeks/pause/STOP; zero jobs/sessions after teardown. Add TABLET-MIM-001: Transcode ready but missing native MPEG-2 declaration makes Core choose Push, no MIM job/video. Target optional source-capability/fallback correction before remaining tablet gates; both orders200 reflect dependency, no unrelated native/device rerun. |
| 199 | 2026-10-07 | Add TABLET-IJK-001 under DEVICE-002 after settled IJK screenshot/8s recording proves blank video and actual Exynos codec errors while audio/source clock advances. Prior IJK clock-only lifecycle/control verdicts are not complete video PASS; preserve failure raw. Add debug-only first-frame snapshot/negative startup-recovery oracle, compare progressive AVC controls before any runtime fix. Four typed-output backend hardware/lifecycle rows remain valid; both orders199 targeted IJK investigation before remaining tablet matrix, no repeated other devices or Core/SW switch. |
| 198 | 2026-10-07 | DEVICE-002 progress: current67e07f95 installed stream-hash matches55019483bytes; all40 available hardware rows pass across four extractor/delegate paths. [x] Stock175 HOME/return, Surface recreation, user-pause preservation, exact replay and teardown pass independently on Media3, Legacy, IJK and both GSY delegates;15 current preferences restored each. IJK controls pass but unsupported health probe/one ambiguous capture do not prove its hardware/picture. Legacy/GSY codec burned labels reviewed; EOF cards/previews not full-screen certification. Both orders198 keep remaining visual/caption/DVD/owned gates first without repeating completed other devices. Parent DEVICE-002 remains open. |
| 197 | 2026-10-07 | [x] **TOUCH-001 - Tablet touch long-press/navigation rows** closes: absent single-finger default NAV_OSD only; explicit/custom/NONE, multi-finger, remote, IDs/actions and notouch columns preserved. Final67e07f95 builds/inspection pass;4 resolver+8 focus JVM,2 touch+18 remote+4 all-layout contracts pass. Actual finger hold opens175/232 main-menu panel; final175 Media3 Pull hardware AVC/decoded AC3 renders aligned four-column rows, Audio/Video/CC taps and dismissal work. Seek/pause gate46.813s passes,14 settings restored. [x] DEVICE-002 commissioning sub-gate: guarded candidate install, installed381 hash baseline, user175/232 wizards, actual generated identity privately persisted,14 prefs/power7+2147483647 checkpoints, actual2000x1200 touch/layout inventory. Compact TOUCH result records software-visual limits; completed raw retired recoverably. DEVICE-002 codec/media/lifecycle/caption/DVD/owned matrix remains active on67; no broad other-device rerun, Core/STV/key migration or release. Both orders197 remove completed touch prerequisite; tablet hardware rows begin. |
| 196 | 2026-10-07 | User adds two right-bottom icon-row alignment to active **TOUCH-001**. Four-column touch/shared grids remove the empty leading bottom cell; IDs/actions and already-aligned notouch columns remain. Regression covers all three resources. First default-only3278 candidate actual touch opens NAV on175/232 and stock175 hardware AVC/decoded AC3;14 settings retained. Rebuild final combined candidate before closing touch task/starting broad tablet matrix. Both orders196 same dependency order, no other-device matrix invalidation. |
| 195 | 2026-10-07 | User completed both175/232 normal wizards. Actual SM-P610 generated identity persisted privately,14 preferences checkpointed. Add **TOUCH-001** under DEVICE-002: long finger hold fails to open client panel because unset touch mapping defaults to STV OPTIONS, while explicit NAV_OSD renders correctly. Implement only absent-default correction; explicit/custom/NONE, multi-finger and remote mappings preserved, no migration/Core/STV change. Both orders195 targeted touch correction before full new tablet matrix. No new physical fix or playback PASS until candidate tested. |
| 194 | 2026-10-07 | User starts **DEVICE-002** and explicitly requests full Tab S6 Lite device matrix without HDMI. Stock175 first, current guarded APK then manual first-run; ADB screenshots/recordings with actual render/decoder/server state provide visual software evidence, not physical speaker/HDMI A-V certification. Optional232 owned/GPU rows follow native baseline. Added stable acceptance sub-gates without reopening completed MATRIX-003/004 or other devices. Both orders194 keep tablet first; no settings wipe, Core patch, release or playback PASS yet. |
| 193 | 2026-10-07 | [x] **DVD-003 - SageMC FF/RW timed-skip timeline** (split from DVD-002/revision143, resumed191) closes on current381ae608 stock175/non-Pro25. Preserved113 preferences and SageMC timed-skip profile. Actual remote FF/RW, independent public skip API, decoded source/server clocks and visible elapsed label pass; pause holds0ms and resumes A/V. STOP/exact-path ALADDIN rewatch161.181s passes, target3195000/actual3180393 (approximate accepted), cadence0.999477x/video459/audio298. Rewatch repeat58.322s passes FF1.000487x/max474ms and RW1.000383x/max642ms, HDMI settled54:30..34/54:32..41 then normal hide. Historical bb2793 freeze root cause remains unproven; do not claim a new runtime fix or restore reverted64-to1024 experiment. Eight oracle/22 MCP/eight wrapper tests pass; malformed invocation failures corrected, not player failures. Compact report artifacts/results/DVD-003/timed-skip.json; completed current raw retired recoverably, unique historical mixed evidence retained. DEVICE-002 readiness passes actual SM-P610/Android13/API33/.51:42491 after persistent-key pairing; no tablet APK/media/install. Both orders193 remove DVD-003, tablet next; no full matrix/Core/STV/key changes or publication. |
| 192 | 2026-10-07 | Added **DEVICE-002** for user Tab S6 Lite .51 after active DVD-003; ADB readiness only in parallel, no tablet media/install/profile changes yet. Persist ignored alias with model/API explicitly unverified and no invented client ID. First lower-level serial override through native wrapper incorrectly selected .25, so do not count that as .51 connection evidence; retry proper alias and inspect forwarding before claiming success. User switched HDMI to non-Pro. Current381 settled clocks and visible standalone label advance; strict timed-key proof underway, no new runtime clock fix claimed. Both orders192 put tablet after DVD-003; completed matrices retained. |
| 191 | 2026-10-07 | User resumes **DVD-003** and requests completion. Preserve non-Pro SageMC Default DVD FF/REW timed skips; correlate settled source clock, server time and visible label on stock175 before a bounded client correction. Current HDMI is Pro; existing public Skip API can isolate the same production seek boundary without changing its key profile. Earlier failed64-to1024 history experiment remains reverted. Both orders191 put this targeted task first; completed MATRIX-003/004 evidence is not globally reopened, rerun only rows a proven change affects. No code/Core/plugin/settings change yet. |
| 190 | 2026-10-07 | [x] **MATRIX-004 - Final cross-device evidence synchronization** complete: compatibility/diagnostics/changelog/handoff and compact per-device reports reconcile exact measured/carry-forward builds, guarded settings restoration, actual232 GPU execution and175 CPU/no usable hardware-transcoding constraint. Source manifest regenerated/checked1563 files; task-order/workspace mirror and four focused order/artifact workflow tests pass, whitespace clean. Pro/non-Pro installed candidate/canonical381 hashes agree (non-Pro device streamed hash verified); normal and native scan/menu gates, corrected oracle limits and unproven original rejection/automation causes remain explicit.195 raw/helper files/164572567bytes retire recoverably plus prior0d canonical; final inspection and unique open failure evidence remain, no deletion. Both orders190 remove completed matrix IDs; conditional ONN/tablet work or approved publication next. No commit/release/server/keys/settings reset. |
| 189 | 2026-10-07 | [x] **MATRIX-003 - Cross-device affected matrix** complete under the user's pre-publication exceptions (Shield/v1/Pro), not a new full historical matrix on one APK. Pro381ae608 completes stock175 CEA/Teletext/local DVB, scoped MPEG-2 HOME/manual-pause recovery, authored menus/ALADDIN cadence/audio/chapters and corrected FF/RW256x/release/PLAY/TS. Exact9min scan54.901s and normal70.112s/menu27.578s pass. Strict232 Copy73.020s/Transcode115.834s/visible CEA/real VAAPI decode+encode and controlled409 retained-seek guard29.307s pass on preceding shared-path candidate; scan-only PES change does not touch HLS success. API25 shared extractor isolation66.048s passes public ±2..64 rates and release A/V; keep intentionally selected SageMC timed skips, not a256x physical-key PASS. Corrected oracle waits for new output after cell-counter reset;18 remote/222 selected Python+MCP tests/build/JVM/inspection/structure pass. Retain completed Non-Pro revision152, Shield155 and ONN v1169 evidence, per-row APK provenance and functional DVD-002 acceptance (not exact landing/HD200 parity). Restore Pro15/non-Pro113 preferences and Pro power3/600000; borrow non-Pro's manual keep-awake owner. Apps stopped,232 owners0. Canonical381 promoted with old0d recoverable;194 owned raw files/164568930bytes retire recoverably, final inspection/compact reports retained. Original server-rejection and automation-timeout causes remain unproven, unique evidence kept; no Core/plugin/resource changes.175 CPU/no hardware transcoding documented and actual windows measured. Both orders189 reviewed: MATRIX-004 sync next, then approved publication/conditional work; no commit/release. |
| 188 | 2026-10-07 | [x] Candidate381ae608 installed preserving15 preferences;10 trick-mode JVM/81 DVD source/build/inspection pass. Scan-only muted-audio PES exclusion passes full46.492s and identical9min repeat54.901s with FF/RW256x, release A/V, PLAY cancel and TS separation. Repeated EMPTY probes show0ms ahead versus prior6.020s stall; concurrent88.890s CPU11.982%mean/69.051%peak of one core, throttle0/no transcode jobs. User-confirmed175 CPU/no usable hardware transcoding remains documented and is not automatic native-Push fault attribution. Normal ALADDIN/menu/audio and shared affected DVD scan follow-up remain. Both orders188 reviewed; parent open, no commit/release/server/resource changes. |
| 187 | 2026-10-07 | Drain-field retry PASS49.317s both256x, but returning via public Seek to543200/approx9min reproduces RW64 stall FAIL60.028s. Full probes show EMPTY flag256, epoch bytes18898944==read bytes, free scan capacity1048576 and video277/input261 fixed while declared buffered tail jumps1079→6020ms then ages down. Concurrent90s server context11.710% mean/34.684%peak of one core, throttling0/no transcode processes; CPU-only video encoding is not this observed wait. Media3 pinned1.11.0 source confirms EOF duration includes disabled-track timestamps. Hypothesis: muted scan AC-3/PES queues extend that tail; experiment skips only scan audio before PTS/queue while preserving SPU and ordinary audio. New source regression red-before,81 DVD source tests green; focused JVM/build active. Do not claim correction/release until identical9min scan and normal/menu/audio gates pass. Both orders187; unchanged other-device evidence remains. |
| 186 | 2026-10-07 | Stock175 controlled ALADDIN retry PASS80.249s, approximate543093 for540000; default adaptive storage gates retained,15 preferences/power restored. Instrumented scan FAIL42.234s: FF reaches256, RW128 output303/input311 freezes until release then normal A/V. Overlapping180s container context36.052% mean of one core,263.984%2s peak, throttling0 and no ffmpeg/MIM/transcoder processes; no server-video-transcoding cause. Does not exclude host contention or assign root cause. Found MCP compact filter dropped already-computed dvdEpochPushedBytes/dvdDecoderBufferedAheadMs; restored only those existing fields, new regression red-before/21 bridge tests green. Expanded bounded scan samples with byte/clock/reply fields, no added runtime instrumentation/APK change. Drain-probe retry+90s CPU context active;31 caption authority tests pass with correct MCP config/PYTHONPATH. Both orders186; parent remains open for scan. |
| 185 | 2026-10-07 | [x] Normal public90000 seek after controlled409 rejection passes at91535ms with active_copy/HTTP and advancing video94/audio74, no error; exit releases owner. Final5f419983 Transcode PASS115.834s: FF2969/REW3092ms/pause, strict active HTTP startup+settled, packets810/wire1638 and HDMI readable40.5/41/41.5 over42.142s, audio mean-21.8/peak-7.1.15 preferences/power restored. Build12 JVM/52 backend Python/APK inspection and reviewed source structure pass. Stock175 ALADDIN controlled retry now active with concurrent bounded180s CPU/process/throttle context; scan remains next. Both orders185; no whole matrix rerun, no release/Core/resource change. |
| 184 | 2026-10-07 | [x] Controlled real HTTP409 Copy-slot rejection PASS29.307s on5f419983: after normal public60000 seek,3 disposable Copy slots force direct_session_limit. Rejected150000 backend request keeps old epoch67014→69796ms over2999ms (progress2782), video423→603/audio245→339, actual HTTP409 diagnostic and no decoder error. All3 gate-owned tokens teardown200; Android owner not touched.15 preferences/power restored. Normal90000 retry/recovery active, then final Transcode and stock175 scan. This proves the rejected-owned-seek correction, not the original intermittent server rejection cause. Both orders184. |
| 183 | 2026-10-07 | [x] Diagnostic343de9 candidate strict232 Transcode rerun PASS133.964s, FF1219/REW4692ms/pause, active HTTP at startup+settled, packets628/wire1576 and HDMI readable29/29.5/30 over30.464 video with non-silent audio; real VAAPI decode+encode verified separately. Copy PASS73.020s through per-control/final ownership and sustained output. These passing retries do not identify earlier intermittent rejection. Reviewed actual client bug: failed Direct replacement fell through into ordinary absolute local seek of old HLS epoch. New5f419983 candidate consumes owned failed seek, retains actual clock/stream and reports failure; stock paths unchanged.52 backend Python/new red-before source contract,12 MIM JVM/build/APK inspection pass. Installed15-pref update; initial restore RPC timed out, subsequent guarded roundtrip confirmed15 preserved/restored. Controlled real HTTP409 Copy-slot gate active at nonzero source epoch, then final positive rows. Stock175 cold-start automation timed out123.391s after its warm-up cleanup; no client crash/CPU attribution. Subsequent59.734s idle resource probe5.159% of one core/no throttling/no transcode processes is not measurement of earlier failure window. Both orders reviewed183; DVD scan remains open. |
| 182 | 2026-10-07 | [x] Added safe client MIM restart rejection diagnostics: numeric HTTP status plus closed known error codes, no arbitrary response/token/URL leakage; retention/seek logic unchanged.12 focused JVM tests/build/APK inspection/51 backend Python checks pass; new test fails before implementation. Candidate343de9f966569fddbb1b6a938ebdaa0649ef8540cc5ff182e6218b51ef98a8b0 installed preserving15 preferences. Repeated strict232 Transcode active. Independent server start/39000/31000 restart probe all200 in2.297/2.797/2.828s, all3 owned tokens torn down; this does not resolve intermittent client failure. Keep unique failure evidence and investigate safe numeric reason. Both orders reviewed182; no server/Core change or full matrix rerun. |
| 181 | 2026-10-07 | Separate Pro232 wizard complete, SageMC Dynamic Menu by nielm visibly verified; no remaining setup block. Current preferences now15 (user changes retained), all restored with power3/600000 after each gate. Transcode136.230s: real HTTP/VAAPI hardware decode+encode, visible CEA, FF3216/REW1855ms/pause and wire1514, but settled state restart_failed_session_retained_IOException; strict row correctly FAIL, prior stream A/V is not seek success. Isolated server start/restart probe active to identify response before client fix. User confirms175 CPU-constrained/no hardware transcoding; document in diagnostics/environment and require actual transport/jobs/resource evidence before attributing native DVD stalls. Read-only Docker inspection finds no explicit CPU quota/cpuset on either server;175 /dev/dri mapping is not proof of usable driver acceleration. Native Push/Pull are not server video transcoding. Both orders reviewed181; Copy/scan still pending. |
| 180 | 2026-10-06 | First Pro232 Transcode attempt32.409s stopped before Watch: new generated identity opens Configuration Wizard - Choose Language on this server. User must finish this separate STV wizard; normal232 connection is now displayed, no test input during manual setup. Corrected earlier advice that175 setup alone sufficed. Pro scan harness now records full last bounded probe, decoder/byte/drain/UI fields and collects failure diagnostics before cleanup PLAY; actual playerClass field corrected. New source contract fails before/passes after,17 remote tests pass. No APK/Core/plugin behavior change, no claim scan64 fixed. Parent MATRIX-003 remains open for strict232 Copy/Transcode and scan investigation; both orders reviewed180. |
| 179 | 2026-10-06 | [x] Pro509c ALADDIN stock175 PASS97.727s: approximate543189ms for540000 request,0.999508x over10157ms, video579/audio318,18 dropped/5 skipped frames/6 release gaps/13 nonpositive releases recorded; pause/PLAY and actual HDMI picture/audio pass, not HD200 parity. Held chapters PASS56.260s. Scan taps/reverse/ramp/PLAY/TS separation settled rerun PASS59.715s at both256x, but earlier31.650s hold stalled at64 and immediate24.009s retry missed2x acknowledgement. Keep those failures open; do not claim a runtime fix from retry alone. Strict232 Transcode active, Copy and scan investigation remain.9 preferences/power restored; both orders reviewed179. |
| 178 | 2026-10-06 | [x] Pro509c authored native DVD startup PASS69.277s and normal remote root/Languages/root/main menu cycle PASS27.169s on stock175; phase budgets2.794/1.333/1.152/3.242s, fresh cell A/V verified, HDMI shows actual Languages menu/yellow highlight.9 preferences/power restored. ALADDIN approximate9-minute/cadence/pause gate active, then controls and strict232 HTTP. Both orders reviewed178. |
| 177 | 2026-10-06 | [x] Pro509c UK stock175 rows pass: Media3 Teletext131.195s, full STV cycle/FF1019ms/REW2060ms/pause, wire491 and readable HDMI; GSY Media3 local DVB93.065s, Off/On with3 new nonempty cues/4501ms/2.014s natural gap, FF1021/REW1545ms and visible bitmap. Discovered DVB row2 selected normally; UK declared CEA formats are not payload proof.9 preferences/power restored. Authored DVD startup active, ALADDIN/menu/owned HTTP next; both orders reviewed177. |
| 176 | 2026-10-06 | [x] Pro509c stock175 GSY legacy/GDX fast CEA PASS114.125s: Off/CC1/CC2/Off/CC1, FF1017/REW1333ms, pause/PLAY and final wire1860. HDMI shows actual picture50.150s with readable48.5/49/49.5 roll-up captions; transport alone is not visual proof or exact-sync certification.9 preferences/power restored. UK Teletext active, followed by DVB/native DVD/strict owned HTTP; both orders reviewed176. |
| 175 | 2026-10-06 | [x] Pro509c Media3 MPEG-2 lifecycle PASS77.430s: same connection/A-V return, user PAUSE across HOME preserved, explicit PLAY and teardown,9 prefs/power restored. Both Exo renderer hooks now physically verified on affected Pro codec; passing AVC remains unmodified. GSY legacy/GDX fast CEA final row active with HDMI, then UK/native DVD/owned HTTP. Earlier v1/older-API policy truth unchanged, retain their completed evidence rather than repeat whole matrices. Both orders reviewed175. |
| 174 | 2026-10-06 | [x] Corrected Pro MPEG-2 HOME freeze on509c01ad: exact legacy stock175 rerun PASS79.957s, same connection1, background video317/init1/release1 then foreground488/init2/release1 and audio312; user pause retained through second HOME, explicit PLAY and clean teardown. Candidate build/policy JVM/139 Python/APK inspection pass;9 prefs/power restored. Media3 MPEG-2 lifecycle active to verify second renderer, then remaining captions/DVD/HTTP. Canonical still0d until final affected evidence; existing v1 tuple/other-API behavior unchanged, no matrix restart. Both orders reviewed174. |
| 173 | 2026-10-06 | ONN Pro legacy MPEG-2 HOME fails108.374s: hardware c2.amlogic.mpeg2.decoder video222/queued173 fixed, audio1631/head2494080 advancing, valid shown1920x1080 Surface, no error/reconnect, decoderinit1/release0. Preserve032929 failure and report. Add only independently observed SNA/API34/MPEG-2 tuple to supported Surface replacement policy in both Exo renderers/native DVD inheritance; Pro AVC stays normal (passing172), v1 policy unchanged. Regression explicitly covers SNA MPEG-2 positive/AVC negative and other APIs negative; candidate509c01ad build/policy JUnit/139 affected Python/APK inspection pass; guarded install restores9 preferences and exact MPEG-2 rerun is active, no physical fix claimed.9 prefs/power restored; both orders reviewed173, no server/keys/Core changes. |
| 172 | 2026-10-06 | [x] ONN Pro stock175 GSY legacy AVC lifecycle PASS85.437s: same connection, advancing video/audio, manual PAUSE across HOME with no automatic PLAY, explicit PLAY, exact rewatch and teardown. Actual Exo2 delegate/c2.amlogic.avc.decoder, decoder init1/release0 and foreground outputs322; SNA successfully switches its Surface without v1 workaround.9 prefs/power restored. Direct legacy MPEG-2 HOME control active, then affected captions/DVD/owned HTTP. Both orders reviewed172; no source change justified. |
| 171 | 2026-10-06 | [x] ONN Pro0d55918 stock175 Media3 fast CEA PASS114.439s: CC cycle, FF1278/REW1316ms, pause/PLAY and wire1793, no local duplicate. HDMI confirms actual picture46.046s and readable44.5/45/45.5 captions (normal stress/roll-up, no exact A/V claim). Paired initial Main Menu route proved175 setup;9 private prefs and original globalstayOn3/systemtimeout600000 restored per run. GSY legacy UK AVC HOME/manual-pause/replay/teardown active; no Pro client fix inferred from v1 brand/API similarity. Both orders reviewed171; parent remains open. |
| 170 | 2026-10-06 | User supersedes ONN Pro deferral and requests MATRIX-003 completion now plus client fixes for failed rows. .50/SNA/API34 authorized, timeout0 verified, guarded exact0d55918 Dev fresh install/normal launch; user completes175 wizard and switches HDMI. Paired SDK/USB Main Menu capture confirms route/idle175 connection, actual generated client4b:52:46:51:4c:48 saved in ignored alias (no invented DEV identity). First Media3 stock175 CEA row active; preserve newly commissioned prefs/power and follow affected DVD/UK/GSY/lifecycle/owned HTTP scope. Completed v1 raw184 files/138745103 bytes plus old9404 canonical retire recoverably; compact results/inspection/Pro prep and current0d remain, no deletion/other device changes. Both orders reviewed/reordered170 to put remaining Pro rows first before release, not repeat complete devices. |
| 169 | 2026-10-06 | [x] Completed current ONN non-Pro/v1 MATRIX-003 affected sub-matrix, not parent: final0d55918, both AVC/MPEG-2 HOME corrections, visible CEA/DVB, Teletext/keys retained, native DVD/menu/cadence,232 Transcode CC with real VAAPI decode/encode, and strict Copy73.260s. Copy first FF/REW probes were transiently unhealthy; bounded sustained recovery plus active_copy/MIM_DIRECT/HTTP after each control/final state pass, no runtime speculation or false zero-latency claim.139 focused Python, policy JUnit, build/APK inspection/structure pass;58 typed settings roundtrip/power restored, app stopped/Home,232 session counts0. Raw completed/corrected evidence retires recoverably, compact report retained. Separately user requested ONN Pro .50 ADB: saved alias onn-pro, authorized after retry/approval, actual onn/SNA/API34, timeout0 twice verified, no installed Vibe package or playback mutation. Keep Pro physical rows after publication. Both orders reviewed/reordered169: remove completed v1 prerequisites and put residual MATRIX-003 after approved source/APK refresh, before MATRIX-004. |
| 168 | 2026-10-06 | [x] ONN232 Media3 Direct Transcode PASS117.549s: active owned HTTP at startup/after controls, side-channel packets774/wire1412, CC cycling, FF3497/REW2890ms and pause; HDMI real video36.403 with readable35/35.5 captions, nonsilent audio(-26.3/-7.4dB). MIM0.4.9 active job independently reports vaapi/h264_vaapi hardwareDecode/Encode true, deinterlaceOff. Copy68.114s passed A/V with transient first FF probe failure, owned HTTP recovered; server lastJob copy/copy with both hardware flagsfalse,0 jobs after exit. Do not rely on old startup-only strict session check: reuse existing ownership predicate after each control and final settled state; regression fails before/passes after,139 affected tests pass. Copy-only strict rerun active, no APK/runtime change or whole matrix rerun. Both orders reviewed168. |
| 167 | 2026-10-06 | [x] Final0d55918 ONN native DVD refresh passes stock175: authored startup75.162s; normal remote root/Languages/root/main cycle26.107s (phase2.807/1.339/1.285/2.768s), HDMI visibly confirms authored menu/highlight. ALADDIN107.604s accepts approximate543291ms for540000 request, cadence1.000789x over device10133ms, video591/audio317 advancing,0 drops/14 video skips/1 release gap/12 nonpositive releases recorded, pause/PLAY/STOP teardown pass. Do not claim exact DVD landing or frame-perfect HD200 parity.58 prefs/power restored. Only232 Transcode/Copy and final docs/cleanup remain; both orders reviewed167. |
| 166 | 2026-10-06 | [x] Final0d55918 ONN GSY/Media3 DVB retry PASS93.849s: normal explicit local DVB inventory/service, Off/On continuity (+2 nonempty cues,5023ms progress,4.308s natural cue gap), FF1015/REW1281ms and visible video/yellow bitmaps on HDMI with AC-3 decoded PCM audio(-27.1dB mean/-7.7 peak). Original Android HOME-interrupted pre-Watch attempt remains failed, input origin unproven. Media3 MPEG-2 lifecycle PASS80.595s, same connection, A/V recovery and user-pause persistence/teardown.58 prefs/power restored; native DVD and optional232 Copy/Transcode remain, both orders reviewed166. |
| 165 | 2026-10-06 | [x] ONN0d55918 stock175 GSY legacy/GDX fast CEA passes107.470s: Off/CC1/CC2/Off/CC1, FF1018ms/REW1307ms, pause/PLAY, final wire/cue continuity and58-pref restore. Six-second HDMI proves actual test video and readable PTS40.5/41.0 over video41.909s (not a claim of frame-exact sync). First GSY/Media3 DVB failed before Watch because an actual Android HOME key arrived21:38:14 and lifecycle closed normally; no player existed, input origin unproven, keep evidence and repeat exact row rather than patch runtime. Retry/HDMI active. Both orders reviewed165; DVD/owned HTTP remain. |
| 164 | 2026-10-06 | [x] Corrected ONN MPEG-2 HOME stall with0d55918: exact legacy Exo stock175 gate passes83.821s, same connection1, release1/init2 and video224→409 after foreground, audio182→293, manual pause persists, explicit PLAY and crash-free teardown. Restore58 prefs and0/900000 power values. AVC8e13 correction is unchanged; no HEVC/device generalization. Focused GSY legacy fast CEA/HDMI is active, then normal DVB/DVD/owned HTTP. Both suggested orders reviewed164; parent still pending ONN Pro after publication. |
| 163 | 2026-10-06 | ONN legacy MPEG-2 HOME gate on8e13 fails independently:212 video outputs/121 queued, no player error, valid surface, both video AND audio stalled (not AVC's audio-only continuation); audio171 and clock162061ms remain fixed while20s buffered. Extend supported replacement exception only to observed c2.amlogic.mpeg2.decoder on same sti6140d360/API34.0d55918 build/policy JUnit/100 focused contracts pass;58-pref guarded install and MPEG-2 physical recheck active. AVC8e13 passing path unchanged; HEVC/other devices remain upstream policy. Both orders reviewed163, no false MPEG-2 PASS. |
| 162 | 2026-10-06 | [x] Corrected ONN AVC HOME/return video stall:8e13 passes exact previously failing GSY legacy and direct Media3 lifecycle rows on stock175 (88.442/87.704s), same connection, advancing hardware video/audio, manual pause retained with no automatic PLAY, explicit PLAY, exact-path replay and crash-free teardown; all58 settings/power restored. GSY video releases once in background and initializes second decoder on foreground (200 to362 output buffers); audio retained. Attachment candidate failure remains historical, not relabelled. MPEG-2 unaffected control, visible GSY captions/DVB, DVD smoke and owned232 HTTP remain before ONN sub-matrix closure. Both orders reviewed162; no lower-API full retest or server patch. |
| 161 | 2026-10-06 | ONN HOME/return failure reproduced with both GSY legacy Exo and Media3; attachment-lifetime7e did not prevent invalid background Surface or video stall and is removed. Candidate8e13b226 uses each player's supported codecNeedsSetOutputSurfaceWorkaround hook, scoped only to verified sti6140d360/API34/c2.amlogic.avc.decoder, retaining upstream workarounds, selectors/adapter/fallback, extensions, streams and audio/seek clocks; native DVD inherits without changing its existing recovery. Build, pure-policy JUnit and100 affected background/backend/caption/DVD source tests pass; guarded update restores58 preferences. Original failure recheck active, no physical fix claimed. Both orders reviewed161; other devices/codecs unchanged. |
| 160 | 2026-10-06 | ONN UK Teletext and explicit local DVB cue/control rows pass; original GSY legacy-Exo HOME/return fails with video stopped at210 outputs/337 queued while audio reaches1699, valid shown surface, no player error/reconnect/session loss. Preserve failure. Shared PlayerSurfaceView now has API34+ attachment lifetime (official Android/Media3 custom-SurfaceView guidance), nested guarded API helper used by all3 constructors, unchanged API23-33 behavior and background/decoder/seek/audio policy. Source regression failed before and12 background/51 backend checks pass after;7e966a84 build/inspection and guarded install preserve58 prefs. Candidate lifecycle retest active, not a proven fix yet. Both orders reviewed160; only affected API34 rows need refresh, not completed lower-API devices. |
| 159 | 2026-10-06 | [x] Completed ONN current9404 stock175 initial affected rows: Media3 fast CEA/CC cycle/seek/pause with readable HDMI captions/audio; legacy Exo/GDX CEA/control transport and hardware c2.amlogic.mpeg2.decoder; authored DVD root/Languages/root/main A/V; ALADDIN approximate9min landing, pause/resume and1.000726x device-clock cadence over15s with0 dropped/21 skipped video outputs and2 release gaps recorded, not hidden. Settled USB HDMI verifies actual visible/audible GDX DVD despite SDK screencap omitting video. Dedicated tap rates2/4/2/1, held FF/RW256x/release/PLAY cancel and TS separation pass; short Up/Down do not change chapter, held gestures each advance3 cells and restore1x. All58 preferences/power restored. Remaining UK/GSY/lifecycle/232 HTTP work stays active; both orders reviewed159 with completed prerequisites removed. No new runtime/Core/plugin changes justified so far. |
| 158 | 2026-10-06 | [x] ONN non-Pro/v1 .141 authorized and identity verified as sti6140d360/Android14/API34, DEV005; adb_allowed_connection_time=0 and matching USB HDMI launcher verified. Guarded9404 update succeeded over actual0.5.91. Old debug receiver rejected settings_checkpoint as unsupported_op, not a playback failure; all58 XML preference entries privately hashed/read before and after install-r were byte-identical, then new checkpoint/restore passed58. Original app was stopped/Home. No clear/uninstall/new keys/other device or server change. Physical rows active; both orders reviewed158 with blocked prerequisite removed, matrix timing unchanged. |
| 157 | 2026-10-06 | ONN connection-only retries used the same saved keys and disconnected only .141. Before debug toggle it remained unauthorized; afterward .141:5555 repeatedly returned Connection refused and adb mdns services found no advertised endpoint. No approval dialog can be requested through a closed port. Await verified current IP and network/wireless-debug setting/port; no reset, new keys, install or playback mutation. Both orders reviewed157; dependencies/order unchanged, ONN current-APK gate remains first. |
| 156 | 2026-10-06 | Added user-requested latest-APK ONN non-Pro/v1 affected rows to existing MATRIX-003 before publication. Historical September13 ONN passes are not current9404 APK evidence, and Fire TV non-Pro is a different device. dev.cmd connect via onn-v1/stock-compatibility reached192.168.10.141 but returned device unauthorized while reading adb_allowed_connection_time; persistent container keys are preserved, no ADB reset/install/player mutation. Await on-device authorization and verified HDMI route. Both suggested orders reviewed/reordered156 to put this scoped work first; ONN Pro remains pending after publication and previous Shield/Fire TV results are not reopened. |
| 155 | 2026-10-06 | [x] Completed Shield **MATRIX-003 affected rows**, not its independently pending ONN Pro parent. GSY/Media3 owned Transcode now passes twice after the harness reuses the lifecycle stable-idle gate before exact-path Watch and observes the single PAUSE request's completion. Both runs verify owned HTTP at startup and after FF/REW/pause, readable captions and no crash; USB HDMI confirms18.585s video with17.5/18s settled caption rows and non-silent audio. Four isolated producer restarts returned200. Historical retained-session/startup-fallback attempts are recorded separately; no unsupported claim that every I/O failure is fixed, and no new runtime/Core/plugin change beyond the verified shared HTTP opt-in.30 caption/51 backend tests, debug build/inspection and project validation pass; all44 preferences/power restored. User chose ONN Pro pending after publication. Both orders reviewed/reordered155, removing finished Shield prerequisites; raw completed/corrected artifacts retire recoverably after compact evidence. |
| 154 | 2026-10-06 | [x] Completed Shield stock175 affected DVD/menu/cadence/audio, CEA, UK Teletext/DVB, GSY-legacy seek/HOME/manual-pause and updated legacy-Exo CEA rows, restoring all44 preferences and temporary power settings. Shared Android9+ HTTP opt-in fixes the API30 legacy-Fixed fallback: direct Media3/232 now proves owned HTTP, active/readable CEA, seeks/pause and VAAPI decode/encode. Merged9404 APK inspection/build and29 caption/51 backend tests pass; no Core/STV/plugin change. Keep authored-loop cadence and wrong DVB-row assertions as invalid measurements, not decoder failures. GSY/Media3 Direct follow-up remains open for a retained-session restart error and separate Pull fallback; no whole Shield/parent PASS yet. User explicitly chose ONN Pro pending, after publication. Both orders reviewed at154 and completed prerequisites removed from next-action wording. |
| 153 | 2026-10-06 | [x] Shield Tube .68 ADB authorized and USB HDMI capture matched its launcher. Existing Dev0.5.91 updated to inspected38ef APK preserving settings; DEV004 identity and per-command aliases retained. User explicitly authorizes Shield MATRIX-003 affected rows before publication; tests in progress, not PASS from install alone. Both orders reviewed/reordered at153; previous Non-Pro evidence stays complete and ONN Pro remains independently gated. |
| 152 | 2026-10-06 | [x] MATRIX-003 Non-Pro affected caption correction complete. Media3/legacy Exo use a session-owned 33 ms active caption clock instead of the 500 ms timeline tick. Fixed/MIM has one ordered presentation worker independent of its unchanged 250 ms HTTP polling budget; a slow-HTTP regression failed before that separation and passes afterward. Stale generation/bridge responses are rejected, pause holds the real clock, teardown cancels timers. 13 cadence/bridge plus 7 Fixed Java tests and 74 affected Python checks pass; build/APK inspection pass. Candidate38efadaac011562ac9791037ad4a36721d9383cb8463d434f94a3ad05183f5aa installed preserving settings. Stock175 Media3/legacy Pull and232 Media3 Pull/Off-CC1-CC2-Off-CC1, Direct Transcode, GSY legacy/GDX Pull and GSY Media3 Direct show readable 500 ms captions after FF/REW; pause/resume and crash probes pass. Screenshot-free HDMI startup shows video19.520/22.522/25.526s with newest complete captions19/22/25s, and post-seek38.372s with stable37/37.5s rows; no debug logging. Android screencap stalls can give an apparent lead, not a production clock correction. Gray moving rectangles match the source testsrc2 frame and are not caption corruption. Server MIM0.4.9 VAAPI decode/encode remains verified. No Core/STV/plugin/runtime preference change, release, or unrelated matrix rerun. Raw corrected evidence retired recoverably; compact result retained. Parent remains open only for other-device post-publication rows; both execution orders reviewed/reordered at152. |
| 151 | 2026-10-06 | User requested correction of the500ms CEA failure. GDX reproduces it too, ruling out an OpenGL-only fault. Found raw CEA drain shared the500ms timeline tick, batching RU/CR/PAC/text instead of extender-style real-time delivery. Added a dedicated session-owned caption clock to Media3 and legacy Exo, inherited by matching GSY delegates;33ms only during playing/active caption transport,500ms while idle/paused, canceled with progress updates and identity/token guarded. Timeline/growing guard, seeking, decoder and audio clocks unchanged; no Core/plugin patch. Candidate4e1a5a76 builds,11 Java cadence/bridge and22 Python contracts pass, APK inspection passes, settings-preserving Non-Pro install succeeds. Focused physical stock175/232 caption gates in progress; fast visual row remains open until proof. Both orders reviewed at151; no full matrix/publication. |
| 150 | 2026-10-06 | [x] MATRIX-003 focused .232 caption isolation: verified installed modified Sage.jar dc6891c8 and MIM0.4.9 VAAPI/h264_vaapi hardware decode/encode using stored Unraid credentials and SageTVTranscoder launcher. Fresh matched120s fixtures differ only in cue interval:2s displays readable STV CEA on Pull and Direct Transcode before/after FF/REW;500ms Pull repeats empty/gray regions despite event225. Fast tracing-on shows text; same Off/On cycle tracing-off does not, so selection/rearming alone is not the cause and debug logging is not a fix. Generator sends14 field1 pairs per16-character line at29.97fps (~467ms);500ms stress allows almost no settled display, alongside Core300ms roll-up animation. Timing-sensitive roll-up/render failure is isolated, exact faulty component remains unproven; fast stress row stays open. Normal visual controls and GPU sub-gates complete; no runtime patch/Core/STV/plugin change/release or whole MATRIX closure. Every guarded run restored113 preferences; temporary import/files retired, unique open-failure evidence preserved. Both orders reviewed at150 with completed comparisons removed; other devices remain post-publication. |
| 149 | 2026-10-06 | [x] MATRIX-003 Non-Pro affected sub-gates: stock175 Media3/Pull MPEG-2/AC-3 seek/pause/STOP-rewatch and visibly readable CEA/event225 after both seeks pass;232 verified MIM-owned Copy controls and native generated-DVD root/Languages/root/main cycle pass. Every guarded run restored113 preferences; existing manual keep-awake borrowed.232 Transcode wire delivery resumes after seeks, but no captions are visible; ordinary232 Pull reproduces that visual failure, so do not blame Transcode alone or close the caption row. The Copy callback assertion was a wrong contract expectation; local in-band CEA is visibly present, no STV-rendered Copy PASS. Server Transcode GPU execution remains unverified without the status helper's absent SSH identity. Retain open caption evidence; retire only completed-gate raw files. Parent MATRIX-003 stays open, other devices post-publication, DVD-002 closed under revised functional acceptance and DVD-003 still deferred. Both orders reviewed at149 with completed sub-gates removed from next-action wording; no runtime code, Core/STV/plugin change or publication. |
| 148 | 2026-10-06 | [x] Closed DVD-002 - Android ALADDIN DVD playback/cadence and navigation under the user's explicit revised acceptance: DVD need not land on the exact spot; working playback and skipping are sufficient. Real-time source/device-clock progress1.00083x, generated native menu/title cycles, actual audio selection, pause/resume, STOP/exact rewatch, TS adjustment/commit/cancel, dedicated FF/RW tap/hold/release and held chapter controls pass. Optional bounded decoder recovery passes its synthetic retained-sample gate. Stock forward seek was about11s late; accepted as approximate, not changed into a precision PASS. Matched59.94fps HD200 presentation parity remains uncertified and is no longer a functional closure gate under this acceptance. DVD-003 timed-skip timeline remains deferred unchanged. Candidate9c27f55e preserves settings;134 affected Python/MCP tests, build/APK inspection/project validation pass. Began MATRIX-003 planning for affected Non-Pro .25 rows on stock175/232; other devices still await publication. Removed completed DVD-002 from both execution orders, reviewed at148. No Core/plugin/settings reset or release. |
| 147 | 2026-10-06 | [x] DVD-002 optional native retained-sample codec recovery physical sub-gate passed on stock .175/non-Pro: debug output withholding, exactly two initializations/one release, video99->389/audio100->249, FLUSH5 unchanged during post-recovery verification, no error; fault disarmed. [x] Final candidate normal menu/title cycles3, actual English/Spanish/English audio selection, main-title pause/resume and ALADDIN STOP/exact rewatch pass. Final source/device clock ratio1.00083x with638 video/414 audio and no new drops/gaps. Candidate9c27f55e installed preserving settings; APK inspection and114 affected Python contracts pass. The recovery checkbox can restore original Media3 rendering, queueing/decoding/clock stay unchanged, no Core/STV/plugin changes. Parent DVD-002 stays open for proven stock-server forward precision scope and matched HD200 presentation criteria; DVD-003 deferred unchanged and MATRIX-003 not started. Both orders reviewed at147; do not redo completed decoder/context gates. |
| 146 | 2026-10-06 | Reverted the unproven stable-Surface experiment and restored prior queueing. [x] Controlled native decoder cycles pass three times with async and three with restored sync, using existing stock MCP DVD APIs; no causality claim or default change. [x] Default sync Fire TV-key cycles pass three times, and natural title end/root/title restart advances A/V. [x] Debug first-input/output metadata probe passes three further key cycles; it records no payload and runs only in debug APKs. Added default-on Native DVD decoder recovery setting: disable/position-reset guard plus at most one locally queued-sample codec restart for the observed queued-input/no-decoder-output case, never for a held output, empty/slow input or another codec family. Keeps source/Surface/A/V clock and queueing/hardware policy; no server seek. Candidate `9c27f55e072d9aa7e0a188ed62714cba78405f5d49af2f5ada165dce88ab077d` builds/installs preserving settings. Deterministic debug-only local output-withholding fault is under physical validation; stock Sage API cannot control Android codec callbacks, so only the existing local debug receiver was extended, no Core/private event/GFX/socket fault. Both orders reviewed at146; DVD-002 and MATRIX-003 remain open for recovery proof, precision scope and matched presentation evidence. |
| 145 | 2026-10-06 | [x] SageMC embedded-preview/MyTVPopup input gate passes: 452x254 preview, ordinary RIGHT/DOWN focus, advancing video, no TS. A final 5cbf root/submenu/main smoke passed (video252->557/audio149->310), but a later reopen stalled at eight inputs/zero output with 12.4s buffered; playback thread idle, no native flush block. Keep DVD-002 open; earlier three-cycle evidence is limited, not universal closure. Stable-Surface experiment `bcabc9199ea5c89488d00e3ebb12d10e1358b111e9d0d9257d1abc49f566a2bd` builds, passes three policy tests/five contracts and APK inspection; physical cycle initially delayed then recovered, but the stronger bounded fresh-cell gate failed on Languages. Continue packet/decoder attribution rather than calling a setting or accepted key PASS. Added a bounded existing-control authored-cycle harness; both task orders reviewed at145. Retired27 corrected lifecycle/context raw files recoverably after retaining compact results; preserved new failure and unique precision/mixed evidence. No Core/STV/plugin or saved-preference change, no matrix start/publication. |
| 144 | 2026-10-06 | [x] DVD-002 native codec lifecycle sub-gate: release before affected MediaTek MPEG-2 disable and keyframe position reset; three authored root/submenu/root/main cycles pass with advancing A/V. Final APK `5cbf804e7f2f34d5e57a10f721c533c015538c24122773a7744283b346443bbc` removes an unused manual recovery experiment and retains the supported lifecycle hooks. [x] Stock .175 STOP/exact-path restart and SageMC fullscreen popup arrows pass. [x] Stock STV .232 Wide OSD Options and embedded-preview arrows pass; Back, not an assumed Full Screen Off effect, visibly establishes preview. 107 affected Python contracts pass, including four retained-intent cursor tests. Forward TS precision stays FAIL: requested 698170ms, server STC destination 709108ms, decoded anchor about 709166ms; stock VM sector interpolation, not client clock fabrication. Reverse TS and Back/Play cancellation pass. Keep DVD-002 open for scope/deferral, SageMC preview and matched presentation evidence; DVD-003 deferred unchanged and MATRIX-003 not started. Both orders reviewed at 144. Compact result `artifacts/results/DVD-002/native-codec-lifecycle.json`; settings/Core/STV/plugins preserved. |
| 143 | 2026-10-06 | Split the user-deferred timed-skip timeline investigation into unchecked DVD-003; DVD-002 remains open for its other physical criteria. User authorized affected Non-Pro MATRIX-003 rows immediately after DVD-002 closure, with .175/.232 as needed; other-device publication gates stay unchanged. [x] DVD-002 focused diagnostic correction: cadence uses existing device health timestamps rather than ADB delivery latency (three tests); ALADDIN observed 1.00003x with advancing A/V and no new drops/gaps. [x] Focused pause/resume gate passed. Native DVD menu-to-title flush hang was reproduced, and a scoped codec-family renderer workaround builds/passes two policy tests, three contracts, affected JUnit and 80 DVD protocol contracts. First corrected title recovered, but repeat failed at queued input/no video output; no full closure or MATRIX start. Reverted the unproven clock-ring enlargement; source clock behavior stays unchanged. Settings, Core and STV preserved. Both execution orders reviewed at 143. |
| 142 | 2026-10-06 | Deferred DVD-002's SageMC FF/RW timed-skip timeline sub-gate at the user's explicit request; parent DVD-002 remains open and other acceptance criteria are unchanged. Preserved actual frozen-label and backward-clock evidence, diagnostic findings and experimental installed APK identity in HANDOFF.md. Focused tests/build passed but no physical correction is proven; clock-history exhaustion is not established for this sample. Both orders reviewed at 142, excluding this sub-gate until requested; no further test, settings change, Core change, commit or release. |
| 141 | 2026-10-06 | [x] Added/closed UI-003: Last/Previous Channel vector icon fills the middle Page Up/Down cell in shared, TV and no-touch layouts; existing stock event 60 is wired through the normal click handler (three contracts/build pass). Physical non-Pro hierarchy confirms Last at x960..1070/y586..696, between Page Up/Down, and Page Down Up reaches Last. [x] Added/closed UI-004 per revised user requirement: prefer same-axis icons, otherwise choose nearest icon in the requested half-plane; never wrap backwards. Eight focus JUnit tests and real Page Down Right to Video Info / Video Info Up to Page Down checks pass. APK 3eac679559e8c2aa06e05d005a7c7dbbc3b3cc633e419637493c7892b40bf1d5 installed preserving settings. DVD-002 remains open: full-screen/window/popup guards are implemented and three context JUnit plus 16 remote contracts pass, but final physical STV menu ownership approval remains pending; user asked which visible menu still fails. Stock FF/REW API defaults are +/-10000ms; SageMC can intercept them for rate changes. No timed-skip API endpoint was implemented and no SageMC setting changed. Both orders reviewed at 141; targeted menu-context gates precede remaining TS precision/cadence, no broad matrix/Core change/restart/commit/release. |
| 140 | 2026-10-06 | [x] Added/closed UI-002: local icon focus preserves both axes, skips empty rows/columns, excludes hidden/disabled controls and retains focus at edges. Six focus tests and non-Pro hierarchy checks pass: Options Right/Right to Down across gaps; Right/Right to Page Down without leaving the row; Left back to Down; Down/Up across a blank row to Pause and back to Down. [x] DVD-002 mapping default sub-gate: main DVD Playback settings expose default-on arrow navigation and dedicated FF/RW tap-and-hold, with missing values true and disabled choices preserved (two JUnit tests, 15 remote contracts, build PASS). User confirmed the keys work perfectly. Chase-scene Up/Down recheck passes four rows (dedicated-scan-chapter-chase.json); retain the earlier mid-film one-cell compact diagnostic without declaring it a client regression or fixed Core defect. Latest installed APK ded7be21bbe13b167fbf0e7b7e77a71d307d1bdb1afcc53d25fb5ebb3406e813 preserves settings. Both orders reviewed at 140; DVD-002 remains open for forward TS precision, authored menus, actual high-rate throughput and cadence. No Core change/server restart/commit/release. |
| 139 | 2026-10-06 | [x] Implemented the user's dedicated DVD FF/RW tap/hold behavior in a separate foreground gesture controller: one tap step, one-second acknowledged hold ramp capped at signed 256x, hold-release Play, Play cancellation and opposite-direction reduction to logical zero/normal 1x. Removed Android DVD/rate scan banners without removing the STV timeline. Focused policy/build, 14 remote contracts and four MCP held-input contracts pass. Physical non-Pro `.25` / stock `.175` ALADDIN gate passes eight rows: +2/+4 taps, opposite taps to +2/1x, both held ramps reaching signed 256x, release to independently advancing A/V, Play cancelling an active timer, and TS/dedicated-scan separation (`dedicated-scan-hold-stock.json`). HDMI capture confirms no Android rate banner. APK 62f5658ba63c54d026d9d10f2b4a8bc1cb595979bb82b855d4e79c800326993c installed preserving settings. The STV's independent rate label can remain 16x above that range; requested 256x is not measured scan throughput. Held Up/Down recheck pending; forward TS precision, authored menus, high-rate throughput and cadence remain open under DVD-002. Both orders reviewed at 139; focused corrections still precede publication, no broad matrix/Core change/restart/commit/release. |
| 138 | 2026-10-06 | [x] Reconfirmed unchanged Up/Down behavior physically on non-Pro `.25` / unmodified stock `.175`: 200ms Up/Down taps caused no chapter/flush, 3.4s holds repeated next/previous chapters, release restored advancing 1x A/V without player errors (`ts-up-down-chapters-stock.json`, PASS). [x] TS control sub-gates pass: repeated/opposite Left/Right adjusts one cursor, Back/Play cancels without seek/exit, and dedicated FF/RW exits the cursor then scans at +2/-2x. Center causes real FLUSH/replacement A/V in both directions; reverse source-anchor error was -720ms. Forward source-anchor mismatch remains open (+10.3s near the chase; +2.9s/+8.4s late-film examples), so the combined cursor gate and DVD-002 are not closed. The MCP compact snapshot now preserves TS intent fields, with a regression test; initial missing-field failures were test-adapter defects, not proof of cursor failure. APK db6fc4824dda40bcf154a517efead28a616afba30750ba8ea1150a5e8da256e1 installed with settings preserved. Both orders reviewed at 138; next work is precision/menu/cadence, not repeating completed input gates or broad matrices. No server restart/Core change/commit/release. |
| 137 | 2026-10-06 | Revised DVD-002 per user discovery/request: stock Time Scroll enters the cursor, primary Skip -/+ adjusts it, and a second TS commits a real seek. Left/Right uses this sequence; Center accepts, Back/Play cancels. Dedicated FF/RW retains scanning and Up/Down retains deliberate one-second repeating chapters. This replaces the 256x held-arrow approach and its optional Companion seek proposal; no Core/runtime plugin dependency is introduced. Existing MiniClient event 10 is now exposed as time_scroll and the MCP bridge allowlist is extended separately (not deployed). Focused policy/command contracts pass; physical commit/cancel/chapter/FF/menu gates are pending. Both orders reviewed at 137; targeted stock-wire gates still precede publication and no broad matrix is restarted. |
| 136 | 2026-10-05 | [x] Passed DVD-002 held-input sub-gates on non-Pro `.25` / unchanged stock `.175`: short Up/Down caused no chapter/flush, 3.4s holds repeated three chapters, Left/Right reached signed 256x and returned to advancing 1x A/V, client/server clocks agreed, and HDMI capture showed the persistent source-position display. `held-arrows-256-stock.json` reports six passing rows. These do not prove requested scan throughput: measured 64x was 31.47x; a rejected full-pipe experiment measured 39.81/49.19/40.74x for selected 64/128/256x and was reverted. High-speed throughput, final landing, authored menus and cadence remain open under DVD-002. Asked whether optional stock-compatible Companion public-seek coarse navigation is wanted; no server change/deployment. Both orders reviewed at 136; dependencies and broad matrix timing unchanged. |
| 135 | 2026-10-05 | Updated DVD-002 to held-arrow requirements: Left/Right gradual scans through 256x and release-to-Play, short Up/Down ignored, deliberate one-second Up/Down holds repeat chapters, and persistent navigation position display. Superseded approximate virtual tap skips without closing the parent. [x] Stock public exact-seek PES-anchor sub-gate passed (+108/-743ms source-anchor error; resumed normal A/V at 0.99784/0.99377x). Focused held/extractor unit/build, 12 remote contracts and 2 MCP tests pass; high-rate physical and release/display gates remain open. Both suggested orders reviewed at 135; dependencies and broad matrix timing unchanged. No new Sage.jar dependency or deployed companion seek bridge. |
| 134 | 2026-10-05 | [x] Expanded completed FOUND-019 retirement to old run logs/snapshots, corrected-failure captures, obsolete reproduction/build trees, completed temporary fixtures and loose workspace-root captures. Verified clean source commits remain in active Git; preserved settings/keys, original handoffs, ignored source and verification differences before moving caches. Whole obsolete root plugin-repo-pr/dvd-plan-review/teletext and Android quarantine/reports/probe/test-result folders were retired too, with canonical plans and unique differences preserved. Root `deleteme` now holds about 36.9 GiB; current DVD-002 evidence, unresolved OSD/tablet cases and canonical fixtures remain protected. New work uses one workspace temp area, per-task project artifacts/active paths, compact final results and root deleteme; no new quarantine folders or loose screenshots. Both orders reviewed at revision 134: no dependency/matrix-timing change; DVD-002 remains next. |
| 133 | 2026-10-05 | [x] Completed FOUND-019 local artifact retirement and workflow maintenance. Moved superseded release binaries, byte-identical redundant screenshots, and raw captures for completed D6/audio/seek/caption/SMB/plugin-setup cases into workspace-root `deleteme/`, preserving relative paths. Written closure/results remain; corrected failures do not require retaining all raw captures. Current DVD-002/OSD/tablet evidence, fixtures, settings, ADB keys, databases, current release/build, source and unknown mixed folders remain protected. Workspace cleanup report documents historical raw-path retirement and recovery. Added explicit end-of-test and pre-commit/release retirement rules to WORKFLOW/AGENTS. Both suggested orders reviewed: DVD-002 remains first and dependencies/matrix timing are unchanged. |
| 132 | 2026-10-05 | Expanded DVD-002's focused acceptance gates to cover every forward/reverse scan rate, timeline/label agreement, opposite-direction decrease, Play/Pause cancellation, tap skips and chapter/menu arrows. Stock VM evidence selects VOBU-table distances using clamped integer rate/3; Android's fixed 6fps preview undershot actual requested rates. Added source-distance pacing, repeated-PTS bounds, local rate-epoch clock holding, scan audio suspension and a bounded current-DVD rate harness. Focused unit/compile and source-contract gates pass; physical scans exposed forward-8x and reverse-transition failures and remain under correction, not closed. Evidence: local artifacts/dvd002/nonpro-dvd-all-scan-rates-stock-continued.json. Both suggested orders reviewed; targeted DVD-002 gates still precede publication and no broad matrix was restarted. |
| 131 | 2026-10-05 | [x] Closed MIMFIX-003 after completing its final legacy-extender row on a replacement HD200 (`001d6a4bfafe`) against unmodified stock `.175`. The physical HDMI/audio gate passed generated MPEG-2/AC-3/CEA playback and post-seek CC1, H.264/AAC and H.264/AC-3 transition fixtures, generated authored-DVD root/menu/title/chapter/pause/audio/subpicture/return controls, STOP/Home STV repaint with the same connected UI context, and a real growing 5.1-to-7.1 channel transition with distinct advancing A/V. Taskmaster, Breakfast, and Classic Holby retained stable H.264/AC-3 playback and controls; absent Teletext/DVB rendering was classified as the stock 2010-era FFmpeg limitation after direct probe/remux comparison, not an optional-plugin regression. The unexpected shutdown of two HD200 units was traced to the separately installed Automatic Power Off 1.0.7 plugin; after the user removed it and restarted SageTV, its JAR/property were absent and the replacement completed the gates without another policy power-off. Earlier Android cross-player, missing/disabled/old/failed-plugin, Linux/Windows completed/growing, CEA/Teletext/DVB, seek, fallback, restart, and zero-orphan evidence remains applicable. No Exit command was sent and stock `Sage.jar` remained unchanged. Written evidence relocated to `artifacts/results/MIMFIX-003/HD200.md`; completed raw captures were retired to root deleteme, while the ALADDIN comparison remains under `artifacts/active/DVD-002/hd200-reference/`. Suggested order reviewed: removed the completed broad pre-release matrix; DVD-002 remains the next targeted client task and publication follows its focused gates. |
| 130 | 2026-10-05 | Clarified DVD-002 from the physical observation: the slow-looking Android playback was ALADDIN, and the replacement HD200 played that same ALADDIN DVD smoothly. The generated authored fixture remains the copyright-safe measurement oracle, with ALADDIN retained as the real-world confirmation gate. Suggested order and matrix timing are unchanged. |
| 129 | 2026-10-05 | Added DVD-002 after direct physical comparison showed the same authored DVD smooth on an HD200 but apparently uniformly slow on Vibe Android. The task requires a same-fixture 59.94 Hz capture with burned PTS/frame and elapsed-time markers to distinguish real clock-rate error from telecine/interlace/frame-release cadence, followed by a capability-based correction and focused menu/seek/pause/A/V/STOP gates. It is ordered before MIMFIX-003 so any shared presentation change invalidates only the affected final matrix rows instead of forcing a second broad run. |
| 128 | 2026-10-05 | [x] Completed MIMFIX-003's remaining stock-Windows `.185` current-plugin lifecycle portion on non-Pro `.25` without changing stock `Sage.jar`. Media3 hardware Fixed/MIM Direct Transcode and Direct Copy both proved plugin-owned HTTP playback from the mapped `V:/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts`: Transcode delivered hardware H.264 plus AC-3, while Copy preserved hardware MPEG-2 plus AC-3. The affected gates passed deterministic seek, FF/REW recovery, pause/play, repeated source start, Stop/exact rewatch, crash checks, and restoration of all 110 settings and the prior sleep policy. A real `SageTV64` service restart terminated the active Direct session, the plugin returned `ready` with zero sessions, and a fresh post-restart Direct Transcode session again reached sustained A/V. Final API and Windows process checks showed zero caption/Direct sessions and no `SageTVTranscoder`, MIM, or FFmpeg process. The service remained Running and its LocalSystem-visible persistent `V:` mapping survived restart. Existing Windows QSV/software, CEA/Teletext/DVB, and caption-after-seek evidence remains applicable; unrelated matrices were not repeated. MIMFIX-003 now retains only legacy-client/extender and remaining plugin-state/codec/caption compatibility rows. Suggested order reviewed: the Windows lifecycle sub-gate was removed; broad matrix timing is unchanged. |
| 127 | 2026-10-05 | [x] Closed SEEK-001 without requiring another Pro session. Existing Pro `.29` / stock `.175` evidence already proved exact `180000 ms` Pull and Push landings, an injected physical long-Right landing at the next `420000 ms` marker, fresh Left/Right behavior, retained encoded offset, sustained A/V, and no player error; the user's explicit visible report that long-Right/Comskip works supplies the final Pro acceptance. The remaining focused non-Pro `.25` reference used the generated `VibeSeekTest-1080i-MPEG2-AC3-CC.ts` and its first commercial marker on unmodified stock `.175`: Media3 hardware Pull positioned inside the commercial at `137372 ms`, the real SageTV right/Comskip command requested and landed video/audio exactly at `180000 ms` (`0 ms` error), recovered A/V in `572 ms`, and remained playing with both clocks advancing, no failure, and no backing-up loop. The harness restored all 110 Android settings and the device's existing sleep values. Suggested order reviewed: SEEK-001 was removed and broad matrices remain after release-bound stabilization. |
| 126 | 2026-10-05 | [x] Closed GFX-002 and DVD-001 on Fire TV Pro `.29` against unmodified stock `.175`. The client now discards an interrupted pre-reconnect GFX command and performs a bounded type-5 retry; a debug-only seam rejected the first attempt before contacting the server, then the second attempt was accepted (`reconnect_test_first_attempt_rejected`, socket type 5 accepted) without causing the artificial double server reload from the earlier seam. The stock-compatible Client Extension now verifies the post-reload DVD clock after a three-second settle and retries public `Seek(long)` at most three times when stock DVD Push echoes the request without moving its STC. In the decisive ALADDIN run, the plugin rejected attempts 1 and 2 at `19519 ms`, applied attempt 3 at `520586 ms`, and the client/server timelines later agreed near `580.9 s`; Media3 hardware MPEG-2/AC-3 output remained active with advancing A/V and zero dropped frames/errors. The separate sustained Pro gate positioned near eight minutes, ran at `0.9996x` with zero A/V drops, and prior focused evidence covers Unified Off/On, encoded/PCM A/B, live passthrough offset, and audio-output restart. Stop/Back after both the normal and forced-retry reconnects returned complete SageMC artwork/text; no post-fix deterministic recurrence of the stale/garbled GFX frame occurred across non-Pro and Pro repeats. Stock `Sage.jar` remained SHA-256 `d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0`; plugin JAR `3b51ae250aa845903dfb5e2cc051514d10a06fda3c6fa6f11abf778d6e15b9f5`; debug APK `4a8ccb1309e2c92c8bdd7a7e81087ab3e1608de99d6da7ea7c61e851cd3e75d3`. Per-UI overrides and recovery/trace were restored to blank/false/false, device settings/sleep state were preserved, and temporary on-device captures were removed. Suggested order reviewed: both completed tasks were removed; SEEK-001 is now the first Android action and broad matrices remain after release-bound stabilization. |
| 125 | 2026-10-05 | Passed the focused DVD-001 stock-plugin position-recovery sub-gate on non-Pro `.25` / unmodified `.175`: after moving title selection out of the player's `load()` call, two generated-DVD GFX-socket faults resumed the main title from `14948 ms` and `38471 ms`; server logs confirmed each deferred title selection and resume seek, and a screenshot showed the second run in the later chapter. Stop/Back returned to a complete SageMC start screen. Plugin recovery, tracing, and per-UI player override were restored to pre-test values. This does not close the Pro ALADDIN/type-5 rejection/intentional-restart parent. Suggested order reviewed: the remaining targeted gates still precede the broad matrix; no dependency or matrix-timing change. |
| 124 | 2026-10-04 | Corrected the earlier GFX-002 visual verdict: saved `20261004-221824_gfx-dvd-repeat-mytv.png` is mostly black with fragmented text after authored DVD/GFX testing. Found and fixed a definite MiniClient reader bug that published a partly read old GFX command after installing a new socket. The debug APK built and was installed on non-Pro `.25` with settings preserved; 24 focused connection tests passed. Two post-fix exact generated-DVD/GFX-fault/Stop/Home repeats against stock `.175` returned readable SageMC; the second opened a complete `My TV` list. No server restart or Core change. Parent stays open for natural recurrence/cause confirmation; DVD root-menu reset remains DVD-001. Suggested order reviewed: same targeted-before-matrix dependency, updated next-action evidence only. |
| 123 | 2026-10-04 | Passed GFX-002's focused non-Pro/stock `.175` recovery-path gate: debug APK built and installed without clearing settings, 23 connection source-contract tests passed, exact-server-guarded context trigger logged one fresh session and restored complete SageMC `My TV`; an authored DVD/GFX-read-fault/Stop/Home sequence also returned complete artwork/text. The real fault did not recreate EGL or repeat the original corruption, so root-cause confirmation and GFX-002 remain open. DVD-001 still reset to its root menu after the fault. Suggested order reviewed: targeted graphics investigation remains before DVD-001; broad matrix timing unchanged. |
| 122 | 2026-10-04 | Added GFX-002 after a distinct non-Pro SageMC text/artwork corruption survived navigation but cleared on a fresh Android client connection. The plugin was not selected for that UI. Added a bounded OpenGL context-loss recovery path and a debug-only exact-server trigger; compile/focused/physical gates are pending. Reviewed suggested execution order: this targeted graphics gate precedes DVD-001 and the later broad matrices. |
| 121 | 2026-10-04 | [x] Completed GFX-001, the stock SageMC missing-image recovery found after DVD Stop/GFX reconnect. The OpenGL renderer was receiving `null` image holders for handles such as `-54` and throwing repeated `Failed to Render Texture` errors, leaving artwork and sometimes text blank until a new client connection. The client now skips only absent-image draws, sends bounded standard MiniClient image-unload notifications, and coalesces a full repaint after the frame; handle reload or connection cleanup resets recovery state. The debug APK builds and the three focused recovery-policy JUnit cases pass. On non-Pro `.25` with stock `.175` and unchanged `Sage.jar`, generated DVD Watch/Select/Stop/Home and a separate DVD GFX-read-fault/Stop/Home both returned to complete SageMC artwork and text without clearing app data or reconnecting to repair the UI. No texture-null error appeared in the sampled post-gate log. This focused gate does not close DVD-001's separate reader-position/type-5-rejection requirements. Suggested execution order reviewed: no dependency or broad-matrix timing change. |
| 120 | 2026-10-03 | [x] Closed the focused MIMFIX-003 generated CEA-608 caption-after-seek visual sub-gate on non-Pro `.25` with equal-duration 90-second 0.5-second and 2-second cue fixtures. Stock `.175` Pull, stock Windows `.185` Direct, and Vibe `.232` Direct all retained event-225 traffic after FF and displayed correctly spelled PTS text; the sustained 2-second `.232` Direct screenshot `20261004-025227_caption-media3-fixed-legacy-callback-settled.png` shows 30/32/34 seconds at video 35.9, and stock Windows shows the same 30/32/34 pattern. The fast fixture shows correct text in captured frames but can be blank at a single instant, so it is not a reliable still-frame oracle. Linux Direct's prior malformed slow rows came from lost CEA pairs in the small UDP receive queue; the FFmpeg plugin now requests a bounded 4 MiB buffer before binding and does not advance its cursor past a future decode-order PTS. A temporary test-only class overlay is installed on `.232`, not published. Removed an ineffective 350 ms Android Fixed-caption delay, corrected the bridge flush lock order, and added settled clock/packet telemetry to the harness. Android build, 20 focused Python tests, and targeted core/android-shared JUnit tests pass; the FFmpeg plugin's focused Java suite passes. All 108 settings and device sleep values were restored. Exact generated fixture imports/files were removed and rescanned on `.175`, `.232`, and `.185`; `.232`'s old Core MCP bridge was upgraded to tested 0.1.4 solely to perform stock-API import cleanup. Stock `Sage.jar` and `.185` FFmpeg JAR remained unchanged. The parent MIMFIX-003 matrix remains open. Reviewed suggested order: remove this completed caption row, retain the post-code-freeze matrix timing. |
| 119 | 2026-10-03 | [x] Closed MIMFIX-003's focused stock-Windows exact-path and bounded owned seek/pause sub-gates: a forward-slash Windows path resolved and started through the stock Core MCP plugin; Media3 Fixed/MIM Direct Transcode retained owned HTTP A/V, two STV-authoritative FF/REW seeks recovered event-225 output in 3.282/3.535 seconds, and a separate deterministic-zero session reported seek and pause/play recovery. A third pause-only physical run with the revised harness proved `beforeState=2`, `afterState=2`, `outputHealthy=true`, advancing audio/video, and 3.041 s recovery. The first session's near-EOF resume exposed a harness false positive; the harness now rejects end-of-media state 5, with 38 focused tests passing. The post-seek generated-caption screenshot shows visible but malformed roll-up PTS text, so caption visual quality remains open. The 112,483,408-byte Windows import/file was removed and rescanned; the exact local generated copy is recoverable under `artifacts/cleanup-quarantine/` because direct deletion was blocked. All 108 settings and device sleep values were restored; `cc_debug=false`, stock `Sage.jar`, and the known-working FFmpeg JAR were verified. Reviewed suggested orders: only the completed sub-gate wording changed; matrix timing is unchanged. |
| 118 | 2026-10-03 | Reclassified the 0.5-second generated CEA fixture's blank STV screenshots after a real PBS broadcast visibly rendered CC1 on stock `.175`/Pull and a separate 2-second-dwell generated fixture visibly passed CC1/Off/CC1 on stock Windows `.185`/MIM Direct Transcode. The Windows side channel and event-225 callbacks were active with no Android-local overlay; this closes the visual-display hypothesis, not MIMFIX-003's owned seek/pause or legacy matrix. Added a deterministic caption-side-channel switch to the caption harness (20 focused tests pass). The temporary hash-verified 112,483,408-byte Windows/local fixture was removed, the stock library rescanned, all 108 settings restored, `cc_debug=false` verified, and stock Core/FFmpeg binaries preserved. Windows exact-path Watch failed at the wrapper boundary while MediaFile-ID Watch passed; retain as an open automation sub-gate. Reviewed both suggested orders: dependency/matrix timing unchanged. |
| 117 | 2026-10-03 | Clarified the Windows MIMFIX-003 caption blocker with visual evidence. Direct A/V and 1,807 side-channel CEA packets pass; explicit Android CC1 visibly renders authored text, while stock-STV CC1 shows none after 10-second holds even though event-225 traffic exceeds 2,000 callbacks. The caption harness now distinguishes wire continuity from rendered text. An unproven stock-STV renderer-suppression experiment was reverted; the known-good debug APK was restored with settings preserved. FF/REW each recovered event-225 output in about 2.7 seconds but broader seek stability remains open. The temporary 1.125 GB Windows duplicate and stock-API import were safely removed and rescanned; stock `Sage.jar` and installed FFmpeg plugin stayed unchanged. Reviewed suggested order: no dependency or matrix timing change. |
| 116 | 2026-10-03 | Advanced MIMFIX-003's Windows/non-Pro gate without closing its parent. The stock-compatible Core MCP 0.1.4 bridge is deployed on `.185`, and a local hash-verified generated fixture passed strict Media3 Fixed/MIM Direct-owned A/V startup with deinterlacing Off. One FF/REW attempt failed and a repeat passed, so seek stability remains open. A requested-caption gate attached and activated the side channel with 281 clock updates but rendered zero non-empty CEA cues; added an explicit open sub-gate. The harness now controls the caption-side-channel option for the current gate and restores all 108 settings afterward; 38 focused automation tests pass. The temporary stock-API library import and duplicate fixture were removed, and the known-working FFmpeg JAR was restored after a diagnostic replacement changed Direct startup. Reconciled both suggested orders to remove completed Core deployment/ownership as next actions; the overall matrix timing and dependency graph are unchanged. |
| 115 | 2026-10-03 | [x] Closed SEEK-001's **Restart audio output** sub-gate on the user's explicit physical Pro report that it works. The earlier automated healthy rebuild independently retained encoded passthrough, selected AC-3 stream, `-400 ms` offset, and SageTV-owned playback position without an extra seek; the user's report supplies fault-recovery acceptance that automation could not reproduce. DVD-001 remains open: a debug-build-only, exact-server-guarded GFX socket fault during ALADDIN at about `512790 ms` made stock `.175` accept type-5 on the first attempt and reconnect media, with A/V continuing and no player error, but the DVD timeline restarted near `14629 ms`. Source inspection found that DVD reload captures target time and then suppresses the seek on initial load. This is a failed position-preservation gate and does not prove the transient-rejection retry. Updated both execution orders; no broad matrix was rerun. |
| 114 | 2026-10-03 | [x] Completed SEEK-001's Pro long-Right/fresh-key/encoded-offset sub-gate on stock `.175` with generated `VibeSeekTest-1080i-MPEG2-AC3-CC.ts` and its `120-180`, `360-420`, `660-720` EDL sidecar. Media3 Pull and native Push each requested and landed exactly `180000 ms` from the first commercial, with sustained A/V and no error. A debug preposition to `370000 ms` followed by a real injected long-Right requested exactly `420000 ms`; the burned PTS screenshot showed `00:07:03.256` while playback advanced. Two fresh Left/Right commands caused two additional server seek sequences, and encoded AC-3 retained its saved `-400 ms` offset. The visible Audio settings menu showed encoded passthrough, stream `1 ac3 6ch`, and `-0.400 s`; selecting **Restart audio output** produced `audio_output_live_rebuild`, retained stream index 0 and `-400 ms`, advanced playback without a new server seek or error. This proves the healthy rebuild/preservation path, not recovery from the rare HDMI sync fault because that fault was absent. The generated remote EDL was preserved for repeatable tests; local temporary staging and Pro stay-awake override were cleaned/restored. Non-Pro was offline and is deferred until the user switches it; final user-visible Pro acceptance is still open. The execution order was reviewed and narrowed to the remaining gates. |
| 113 | 2026-10-03 | [x] Closed AUDIO-005 and AUDIO-006 on the current Android build at the user's requested priority. Original TV/surround C920 measurements on stock `.175` / Pro `.29` already covered direct Media3, legacy Exo, and both GSY delegates at `0/-400/+400 ms`, direct Media3 seek/pause response, full-range signed `-3750/+3750 ms` with four-corner registration, visible `-4000/+4000 ms` endpoints, and zero reset; the alternate direct-HDMI PCM A/B and PBS NewsHour speaking-interval comparison also passed. Previously recorded generated dual-AC-3 track switching, PMT/audio-format changes, live 2.1-to-5.1 source replacement, and immediate-open AudioTrack handoff passed on the same stock server. A new focused `--encoded-offset-ms -400` lifecycle gate passed direct Media3, direct legacy Exo, GSY Media3, and GSY legacy Exo: each retained the applied encoded clock path across HOME/return, explicit user pause, second HOME/return, and PLAY, then exited without a new crash; all 234 saved settings and Android sleep policy were restored. The harness assertion initially checked an absent diagnostic capability key; it was corrected to require the reported active offset/path, with no player change. Ten focused background-session tests pass. This completes the current-code audio gate before MIMFIX-003 by explicit user priority; any later shared A/V change requires only affected audio rows to be rerun under MIMFIX-003, not a blanket replay. IJK remains unsupported, the encoded option remains user opt-in, and the exact ±4 s two-second-loop fixture endpoints are not claimed as independent timing measurements. Evidence: `docs/AV_SYNC_PHYSICAL_GATE.md` and private local C920 analyzer reports. Both suggested execution orders were reconciled. |
| 112 | 2026-10-03 | Completed the remaining AUDIO-006 full-range original TV/surround C920 measurement on Pro `.29` / stock `.175`. Direct FFmpeg camera/mic capture resets C920 Zoom/Pan/Tilt before and after graph open; settled 1920x1080 frames show all four marks. The analyzer now ignores transient pre-reset frames and uses warm-color-biased marks so neutral bezel glare does not cause a false crop; a genuinely cropped older capture still fails. Direct Media3 embedded AC-3 measured `-3777.334 ms` for `-3750 ms` requested (27.334 ms residual; 3 pairs) and `+3719.333 ms` for `+3750 ms` requested (30.667 ms residual; 4 pairs), each with the four-corner physical gate true against a nearby zero baseline. Both exact `-4000/+4000 ms` endpoints were accepted and visible on complete-TV C920 frames; timing at those exact two-second multiples is fixture-sequence ambiguous. Zero reset returned to within one C920 frame of the baseline; that retry was diagnostic-only because top-left glare washed its color mark, while an earlier four-corner live reset had passed. Prior four-backend `0/-400/+400 ms` physical rows remain valid. Close/reopen calibration between initial-offset changes: an already-open dialog does not inherit the new player value. Saved encoded `-400 ms` and Android stay-awake settings were restored, playback exited. AUDIO-006 remains active only for focused cross-player encoded lifecycle acceptance; AUDIO-005 remains the post-MIMFIX-003 final matrix. Evidence: `docs/AV_SYNC_PHYSICAL_GATE.md` and private local analyzer JSON. Reviewed the execution order at revision 112; task timing is unchanged. |
| 111 | 2026-10-03 | Completed AUDIO-005's alternate-route webcam/HDMI A/B sub-gate on Pro `.29` and stock `.175`, leaving its final post-MIMFIX-003 lifecycle matrix active. Vibe FFmpeg captured 1920x1080 plus 48 kHz stereo from the USB HDMI device; the full-raster authored-corner gate passed after adding a distinct direct-HDMI analyzer mode that does not require a camera bezel. At Media3 Pull/hardware video/decoded PCM/zero offset, the generated 1080i MPEG-2/AC-3 fixture measured -91.667 ms audio-after-picture across 10 events (4.0 ms standard deviation), versus +628.0 ms on the original TV/surround C920 route. Source-matched PBS NewsHour speech at 23 minutes measured -179.4 ms on HDMI capture with 0.9948 audio and 0.9886 temporal-video correlation, versus +730 ms on the original route; the speech matcher is diagnostic at 200 ms resolution. Live player counters remained zero for audio underruns/output errors with advancing playback. This difference is route-specific evidence, not a fixed Pro-wide correction or proof that the TV/AVR alone caused it because HDMI EDID and capture-card alignment also changed. The Pro's encoded -400 ms setting and Android sleep values were restored, playback exited, and both suggested execution orders now name only AUDIO-005's genuinely open final matrix. Evidence: `docs/AV_SYNC_PHYSICAL_GATE.md` and private local analyzer JSON. |
| 110 | 2026-10-03 | [x] Completed **AUDIO-001 - Pro HDMI-path A/V-sync characterization** and moved it from active work to this ledger, preserving the original parent scope: determine the Pro AFTKRT's output-route delay on stock SageTV without imposing a model-wide correction; compare decoded PCM and DIRECT AC-3, source PTS, decoder/output identity, AudioTrack state/underruns, and synchronized complete-TV webcam video/audio. The user confirmed the C920 captured the original TV/surround path. Full-frame authored 1080i MPEG-2/AC-3 captures measured direct Media3 encoded `0/-400/+400 ms` at `+514.7/+183.0/+974.7 ms`, direct legacy Exo at `+604.7/+201.3/+1048.7 ms`, and both GSY delegates at matching signed directions; decoded PCM zero was `+628.0 ms`. Source-matched PBS NewsHour speech at 23 minutes showed `+662/+288/+119 ms` for encoded `0/-400/-650` and `+730 ms` for PCM zero at diagnostic 200 ms temporal-video resolution. Compiled Vibe FFprobe showed primary AC-3 PTS `1380.000000 s` versus nearby MPEG-2 PTS `1379.995833 s`, not a 500 ms source gap. Live Media3/Pull at that interval and the authored fixture each had `audioUnderrunCount=0`, `audioOutputErrorCount=0`, `errorState=false`; AudioFlinger confirmed DIRECT AC-3 versus PCM mixer identity. The prior immediate-open AudioTrack race gate passed at revision 107. Removed AUDIO-001 from both suggested execution orders. Alternate HDMI-capture PCM comparison and final lifecycle matrix remain AUDIO-005; full-range/end-state encoded offset remains AUDIO-006. Evidence: `docs/AV_SYNC_PHYSICAL_GATE.md` and private local analyzer JSON. |
| 109 | 2026-10-03 | Completed the original TV/surround-path webcam sub-gate for AUDIO-001/AUDIO-005/AUDIO-006 on Pro `.29` and stock `.175`, without closing the parent tasks. The user confirmed the route and centered C920; the bezel-aware four-corner analyzer accepts all new authored captures and still rejects the clipped old capture. Direct Media3, legacy Exo, and both GSY delegates each passed physical `0/-400/+400 ms` encoded rows on the authored 1080i MPEG-2/AC-3 fixture. Direct Media3 decoded PCM zero and a live encoded zero reset also passed. Source-matched PBS NewsHour speech at 23 minutes showed delay in both encoded and decoded output, with the expected signed change as offset moved. AUDIO-001 still needs source-PTS/underrun-counter correlation; AUDIO-005 still needs the alternate HDMI-capture route and final post-MIMFIX-003 lifecycle matrix; AUDIO-006 still needs full-range/end-state acceptance. Reconciled both suggested orders to remove the completed webcam sub-gate as a future action. The Pro's saved -400 ms offset and Android stay-awake values were restored. |
| 108 | 2026-10-03 | Reconciled both suggested Android execution orders after revision 107: the completed AudioTrack handoff and immediate-open regression no longer appear as future prerequisites under AUDIO-001/AUDIO-005/AUDIO-006; the next open work is the repeated synchronized Pro HDMI measurement and zero reset. Parent audio tasks remain unchecked. Added an explicit same-revision order-review stamp and validation gate so a later task-status change cannot leave the execution order silently stale. |
| 107 | 2026-10-03 | Corrected AUDIO-001/AUDIO-005/AUDIO-006 closure scope to the Fire TV Pro's HDMI audio path; non-Pro is no longer a prerequisite. The earlier `FORCE_NONE` inference was wrong: AudioFlinger confirmed an active DIRECT AC-3 thread. The visible client error was `ERROR_CODE_AUDIO_TRACK_INIT_FAILED` followed by `Playback recovery limit reached (AUDIO_OUTPUT)` when a diagnostic AC-3 player opened during an in-flight output rebuild. Media3 and legacy Exo now preserve the diagnostic's exclusive audio suspension across replacement-player setup and audio-track selection; MCP normally waits for the rebuild to settle and has an explicit immediate-open stress gate. Focused 88-test source/automation suite and 60-task debug APK build pass. On Pro `.29` / stock `.175`, the generated AC-3 server fixture started by exact path; the immediate stress gate recorded `diagnostic_audio_suspended` before `audio_output_live_rebuild` and ended at normal EOS with `audioOutputErrorCount=0` and `health_errorState=false`. The new log had no AudioTrack-init failure, all 234 saved settings and stay-awake values were restored. Repeated synchronized receiver measurement remains open. |
| 106 | 2026-10-03 | Corrected the C920 framing root cause and completed the complete-TV camera gate. FFmpeg's DirectShow graph reapplied stale Zoom 144/Pan 1/Tilt -10 after the pre-open reset; the capture helper now performs a verified second reset after graph open, restoring Zoom 100/Pan 0/Tilt 0 for every capture. All four authored corners are visible at 1920x1080/30, including when yellow clips to neutral white under camera exposure. The full-frame embedded zero row measured a stable +611.333 ms physical route baseline; a same-dialog -400 ms change measured +251.333 ms raw, -360 ms baseline-corrected, and a 40 ms residual. Automated MCP tests now snapshot, override, and exactly restore Android stay-awake and screen-timeout settings, with interrupted-session recovery and a manual `test-awake begin/status/end` mode for multi-command gates. This completes framing and prevents idle sleep during tests, but does not close the original encoded receiver/ARC or matched non-Pro rows. |
| 105 | 2026-10-03 | Exhausted the remaining software-only C920 framing and encoded endpoint checks without falsely closing the physical gates. Direct captures at 640x480, 800x600, 960x720, and 1920x1080 retained the same center crop and detected zero of four authored yellow corners; larger raw modes were rejected by the camera driver. The embedded encoded path accepted -4000 ms, reset to zero, and exited without a crash. Its cropped zero capture measured -44.667 ms and its negative endpoint measured -4298.667 ms raw, retained only as diagnostic evidence because exact two-period pairing is ambiguous and the four-corner gate failed. The reusable `mcp-av-sync-screen` command now opens this screen without menu coordinates and can configure the active diagnostic offset. The Pro was restored to encoded -400 ms and stopped. Matched non-Pro and original TV/receiver/ARC closure remain physically blocked: `.25` is reachable on TCP 5555 but ADB remains offline after disconnect and server restart, while Pro reports `mHdmiSystemAudioSupported=false` and `FORCE_NONE` on the present route. |
| 104 | 2026-10-02 | Added the reproducible physical A/V evidence path without closing AUDIO-001/AUDIO-005/AUDIO-006: matching embedded and PBS-profile stock-server fixtures now use a 200 ms post-impact hold plus camera-safe 100 ms motion positions; a direct C920 helper supports a clearly labeled video-only preflight; and the offline analyzer measures signed impact/click offsets from one capture clock. Source self-tests measured the embedded fixture at a 6.166 ms median and the interlaced server fixture at 4.716 ms, both below the C920's one-frame limit. Known-delay controls measured `+400 ms` as `+408.5 ms`, `-400 ms` as `-393.0 ms`, and `+4000 ms` as `+4009.5 ms`; the expected-offset input disambiguates the repeating pattern across the complete slider range. The revised server fixture is installed on stock `.175` with verified SHA-256 `3c81a7447650e8fdc719c9d04d258131db0f1c2496401d74e249c2d5294b8391`. Physical receiver closure remains open because this restricted Windows process cannot open the C920 microphone and the revised APK still requires the normal unified-container build/install gate. |
| 103 | 2026-10-02 | Added a stable Suggested execution order inside this authoritative Android task file without moving the existing task sections. Targeted seek/audio/DVD work precedes broad validation; MIMFIX-003 and AUDIO-005 are the final pre-release matrix phase; publication precedes MATRIX-003/MATRIX-004 affected-device evidence. The workflow requires reviewing this order whenever task status or dependencies change and repeating only evidence invalidated by a late shared-code change. |
| 102 | 2026-10-02 | Completed DIAG-001. The new main Diagnostics Settings screen consolidates the default-on automatic Push-stall switch with existing file-log, log-level, log-share, bundle-export, unmapped-key, aspect, and independent SMB diagnostic controls without changing their keys or saved values. The bounded recorder retains only redacted state/counter samples during an already-ready ordinary-Push rebuffer of at least 1.5 seconds, writes asynchronously, keeps at most four incidents, and includes them in manual and Always-mode exports. Focused Core tests prove identical Push bytes/order and free-space behavior under backpressure; Android compilation, 18 diagnostic/settings tests, strict validation, and a clean 60-task APK build pass. On stock `.175` / non-Pro `.25`, enabled fixed Push captured the real 1.892-second rebuffer plus recovery tail while A/V advanced and no crash occurred; the same playback with the setting disabled passed without creating another file, and all 108 saved settings plus the original enabled value were restored. |
| 101 | 2026-10-02 | Added DIAG-001 after a stock `.175` / Pro `.29` incident showed a 12.47-second ordinary-Push underrun while the source recording continued growing. The task is deliberately client-only and observation-only so the next bundle can distinguish stopped server delivery, stale free-space accounting, blocked client writes, decoder-read starvation, and extractor/render stalls without modifying `sage.jar` or changing playback. |
| 100 | 2026-10-01 | Completed GH-013. Commit `ff2549c` passed the affected source/Core tests, strict validation, the 1,487-file manifest, clean 60-task APK build, strict APK inspection, and the recorded stock `.175` / non-Pro Push physical gate. GitHub repository checks and Pages deployment pass; v0.5.100 targets that exact commit and publishes all five bullet-documented assets. Every public download matches its local SHA-256, the latest-release API returns v0.5.100, and the permanent Pages downloader returns HTTP 200. Final visible Pro Comskip/input/audio-reset acceptance remains open under SEEK-001. |
| 99 | 2026-10-01 | Added GH-013 at user direction after the recovery/audio implementation commit: publish the affected Android changes as v0.5.100 with grouped bullet release notes, deterministic assets, public hash verification, successful repository/Pages workflows, latest-release API confirmation, and permanent downloader validation. SEEK-001 retains its explicit Pro visible-acceptance checks after publication. |
| 98 | 2026-10-01 | Contained the Pro Fire OS stale-long-press case without changing mappings: fresh physical key gestures now discard an abandoned prior hold, so Left/Right returns to configured FF/RW after long-Right Comskip. Added a live **Restart audio output** action for Media3, legacy Exo, and their GSY delegates; it recreates the app-owned player/AudioTrack while retaining transport, selected stream, passthrough policy, offset, and playback position. The affected 188 Linux tests, focused Core JUnit, project validation, and clean 60-task APK build pass; the settings-preserving APK was installed on Pro `.29`. Final visible Pro input/audio acceptance remains explicitly open under SEEK-001. |
| 97 | 2026-10-01 | Completed the SEEK-001 one-shot post-Comskip Push recovery subtask. Media3, legacy Exo, IJK, and their GSY delegates now share a bounded recovery controller that arms only for long-Right, requires the following stock-server FLUSH plus first non-empty Push payload, and may restart only the local reader once without sending another seek or changing the STV-selected destination. Six focused controller tests plus a zero-mux Push-epoch protocol test, 576 repository tests, 104 MCP tests, Core JUnit, validation, and a clean 60-task APK build pass. On non-Pro `.25` against stock `.175`, a real long-Right plus exact stock `Seek(long)` produced arm/FLUSH/zero-mux epoch/first-frame/healthy evidence with advancing A/V and no redundant recovery; the settings transaction restored all 107 values. The settings-preserving APK was also installed on Pro `.29`; final visible Pro landing acceptance remains open under SEEK-001. |
| 96 | 2026-10-01 | Completed ADB-001: every MCP `adb_connect` now sets and verifies device-global `adb_allowed_connection_time=0`, reports the prior/current policy, and fails explicitly if a vendor build does not retain it. The focused 55-test ADB suite passes, and real MCP stdio calls on non-Pro `.25` and Pro `.29` both reported `currentValue=0`, `nonExpiring=true`, and a live persistent shell. This preserves an already-approved workspace key but does not bypass initial user authorization. |
| 95 | 2026-09-30 | Documented the calibrated Logitech C920 as reusable primary physical A/V evidence for AUDIO-001/AUDIO-005/AUDIO-006 and supporting DVD-001 validation. One-device camera/microphone capture can resolve the reported roughly 500 ms receiver-route delay at about 33 ms precision, but does not replace HDMI/client telemetry or certify 25 ms adjustments and 50/59.94 fps cadence. |
| 94 | 2026-09-30 | Normalized pre-commit task maintenance across all active Vibe repositories. Android active sections now contain only unchecked work; all 117 completed checkoffs were moved intact under this ledger with their stable IDs, evidence, source section, and open-parent context. The repository rules now require this cleanup immediately before every commit. |
| 93 | 2026-09-30 | Added deferred follow-ups EXT-005 through EXT-007 without starting implementation: a measured stock-Core-clamped Push-buffer A/B only after a reproduced Push defect, generated malformed-caption regression expansion only after a concrete parser failure or parser change, and individual legacy hardware-output re-evaluation only after an Android problem that existing player/OS controls cannot solve. The HD300 512 KiB value and Sigma/extender identity remain rejected as defaults. |
| 92 | 2026-09-30 | Completed EXT-004. The checksum-verified HD200 ROMFS was extracted read-only; its MiniClient resides inside an encoded `FNIB` kernel payload, so the audit records that clean-room boundary instead of inventing binary evidence. HD300 release/beta symbols and properties were reconciled with the surviving Apache MiniClient/Core source, public firmware/server notes, Android capabilities, and every required behavior domain. No speculative runtime capability was enabled: Sigma output/decoder workarounds remain rejected, the 512 KiB Push advertisement remains deferred, and existing targeted Android behavior stays stock-compatible. A stock `.175` / non-Pro `.25` Media3 hardware session passed seek, FF/REW, large-jump and pause/resume output health with no crash and all 103 settings restored; dynamic mode truthfully negotiated MediaServer Pull. |
| 91 | 2026-09-30 | Completed GH-012. Commits `14eef99` and `fc9aacb` passed focused growth-policy/source tests, strict validation, a clean 60-task build, strict APK inspection, active Live TV and finite completed-recording stock `.175` / non-Pro `.25` gates, deterministic source/review packaging, repository checks, and Pages deployment. All five downloaded public v0.5.99 assets match GitHub SHA-256 digests; the latest-release API and permanent downloader are correct. The root cause, validation, and release link were posted to issue #3, which remains open for reporter confirmation. |
| 90 | 2026-09-30 | Added GH-012 from the reporter's post-v0.5.98 diagnostic bundle. The log proved an ambiguous stock `stv://` source still ran its one-shot MediaServer growth probe on Android's main thread; the swallowed `NetworkOnMainThreadException` resolved the active recording as completed and exposed only its roughly 1.2 MiB opening snapshot. Prebuild is now nonblocking, and the retained datasource performs the bounded proof on Media3's loader. Focused tests and affected stock `.175` / non-Pro `.25` physical gates pass: Live TV sustains fullscreen A/V, while the finite 899,959 ms completed control passes FF/REW recovery with no player error/crash and all 103 settings restored. Publication and reporter confirmation remain. |
| 89 | 2026-09-30 | Completed GH-011. Commit `5251547` passed the affected source/Core/Media3 tests, deterministic manifest, clean APK build, strict debug APK inspection, repeated `.232` / non-Pro Direct starts, and the matched Pull control. The public v0.5.98 APK/source/review/manifest/hash assets match local SHA-256 values; repository and Pages checks pass; the tag, latest-release API, and permanent downloader are correct. The evidence-backed result was posted to issue #3 without closing it, pending reporter confirmation. |
| 88 | 2026-09-30 | Closed the already-published GH-010 and added GH-011 after reproducing issue #3 on v0.5.97. The Direct-growing startup path performed a UI-thread source probe while a connection replacement could clear Media3's mutable datasource, causing `NetworkOnMainThreadException`, a null datasource factory result, and a fatal setup exit. The candidate trusts explicit active metadata, snapshots the setup datasource, and enters the existing bounded stock-Fixed reconnect if synchronous fallback setup still has no source. Publication remains open pending repeated `.232` / non-Pro physical gates. |
| 87 | 2026-09-29 | Closed the Linux MIMFIX-003 growing/transition row with impact-only validation. The plugin now receives active-file state, retries one stale active hint, rejects incomplete/unreadable startup media, accepts valid duplicate MPEG-TS FFprobe records, and returns an effective bounded live-edge offset. Non-Pro `.25` / `.232` passed hardware Direct Transcode startup, a 24-hour live-edge clamp, REW/FF recovery, two owned channel transitions, zero-session teardown, and restoration of all 103 settings. |
| 86 | 2026-09-29 | Ran only the focused clean-Windows DVD ownership boundary instead of repeating the completed Windows/general or Linux/device matrices. Stock-Core `.185` started the authored DVD through the Core-MCP `Watch` path, but did not negotiate `DVD_DISC_*`; the client correctly remained native hardware MPEG-2 with `discTransformedTransport=false`. The no-false-pass gate rejected the 0.168x native cadence as not being plugin-owned Direct Transcode, restored all 103 settings, and leaves the Windows DVD portion explicitly open without weakening the already-passed ordinary-video QSV Direct result. |
| 85 | 2026-09-29 | Closed the DVD-specific MIMFIX-003 failure row. Against unmodified stock `.175` with no FFmpeg plugin, a requested Direct Copy reported `unavailable_stock_fixed`, retained native hardware-decoded MPEG-2, and passed authored menu activation plus pause/resume. On `.232`, a guarded DVD-only provider shim first exited before input and then, in a separate gate, consumed 2 MiB before failing; both conditions set `discTransformRuntimeFallback`, restored native MPEG-2 with advancing A/V and working pause/resume, produced no crash/black screen, and restored all 103 settings. The original MIM executable was restored at mode 755 and its exact SHA-256 reverified. |
| 84 | 2026-09-29 | Closed the four locally executable MIMFIX-003 owned-DVD success rows on non-Pro `.25` / `.232`. Direct Copy passed authored root/submenu highlight and repeated navigation, menu/title/return transitions, main-title STOP/restart, chapters, repeated FF/REW, exact seek, pause/resume, and clean session reuse. STV-issued track commands selected Spanish audio `48513`, English subpicture `64`, Spanish `65`, then Spanish-off `129` without changing audio, with A/V recovery after every flush/reseek. Direct Copy reported execution path `copy`; Direct Transcode reported `full_gpu`, transport `dvd_mim_transcode_v1`, AVC output, 1.009x 30-second cadence with advancing video/audio, clean STOP, and restoration of all 103 settings. Per release policy, these were the affected DVD gates; no unrelated full matrix was rerun. |
| 83 | 2026-09-29 | Completed GH-009: published v0.5.96 after the focused late-Direct-fallback physical gate, affected APK/AAB/source/MCP/player/Core validation, and deterministic packaging. All downloaded public assets match local SHA-256 values; repository and Pages checks pass; the latest-release API returns v0.5.96; and the permanent downloader returns HTTP 200. The release policy now defaults every future release to affected gates only unless a full matrix is explicitly requested. |
| 82 | 2026-09-29 | Closed the MIMFIX-003 late-start fallback row. A pre-first-frame Direct/Pull failure now retires Direct only for the current connection and uses the existing native MiniClient GFX/media reconnect to renegotiate ordinary Fixed/Pull without replacing the Activity, UI, or watch session. The one-shot debug fault passed physically on non-Pro `.25` / `.232` with `connectionGeneration=1`, reconnect activity, `SAGETV_PULL`, advancing hardware video/audio, no crash/exit, and restoration of all 103 preferences. The exact-path MCP verifier was narrowed to wait through only the named reconnect state; its 18 focused playback-health tests pass. Added the workspace-wide impact-based release rule: rerun affected gates by default and full matrices only by explicit request or documented broad-change necessity. |
| 81 | 2026-09-28 | Added the missing persistent `Deinterlace` selector to Fixed Transcoding Settings. Its saved Auto/On/Off value is the baseline sent to each new plugin-owned Direct Transcode session; the live Video-menu selection remains an explicit current-session override. The settings-preserving APK build passed, was installed on non-Pro `.25`, and the physical preference screen showed the selector and current Auto value. All 575 Android/source tests, 99 MCP tests, Core JUnit, the strict validator, the 1,483-file manifest, and diff checks pass. |
| 80 | 2026-09-28 | Added and physically validated Fixed/MIM deinterlace Auto/On/Off. Direct Transcode with Off reached full-GPU VAAPI on Linux `.232` and full-GPU QSV on Windows `.185`; non-Pro `.25` passed owned startup, hardware decode, seek, pause/resume, and crash gates on both. Fixed a Linux-only duplicate-caption race by reapplying the server-published STV CC state when Fixed side-channel evidence arrives and disabling the preserved local CEA renderer. Event-225 CC1/CC2/Off cycling and visual CC1/Off evidence pass with empty local cue state, clean teardown, zero orphan sessions/processes, and all 102 settings restored. Windows AC standby was restored to 10 minutes and only the two verified deinterlace staging files were removed. |
| 79 | 2026-09-28 | Corrected and physically passed Direct-transcode CEA selection and seek continuity on non-Pro `.25` / `.232`. Asynchronous Media3/legacy-Exo track publication now preserves an explicit active-session subtitle choice instead of letting an unrelated saved DVB/CC mode turn it off; backend release clears only applied selection state so the requested track and overlay are rebound after a Direct seek. The debug seek gate now tolerates only transient segmented-stream BUFFERING within its existing bounded recovery deadline while retaining strict decoder-progress, surface, and error requirements. The generated MPEG-2/AC-3/CEA fixture passed discovery, timestamped rendering, Off/On, continuous cues, FF A/V plus caption recovery, and 102-setting restoration. Added the requested complete owned-stream DVD menu, control, audio/subpicture, Copy/Transcode, transition, cadence, teardown, and failure-fallback gates under MIMFIX-003. |
| 78 | 2026-09-28 | Completed the locally available MIMFIX-003 compatibility conditions on non-Pro `.25` / `.232`. A disabled plugin and a real pre-Direct caption-plugin JAR both reported `unavailable_stock_fixed` and retained ordinary `SAGETV_PUSH`; playback, pause, STOP/restart where exercised, crash checks, exit, and restoration of all 102 client settings passed. A deliberately non-executable MIM binary kept the current API available but forced Direct session creation to fail; the client reported `start_failed_pull_fallback`, played the server-exposed source through `SAGETV_PULL`, and passed FF/REW, pause, crash, and exit gates. Every server file/mode was restored to its exact prior SHA-256/mode and temporary backups were removed. MIMFIX-003 stays open because late failure does not yet renegotiate ordinary Fixed when the Pull source itself is unusable, and Windows/legacy/growing/transition rows remain. |
| 77 | 2026-09-28 | Completed the non-Pro `.25` / `.232` MIMFIX-003 cross-player sub-gate. Media3, legacy Exo, and both GSY Media3/legacy-Exo delegates pass Direct Copy and Direct Transcode with advancing A/V, pause recovery, crash checks, and teardown. IJK passes Direct Copy; its frozen 0.8.8 MediaCodec path cannot consume the proven Direct Transcode output, so an explicit Transcode request now reports `unsupported_player_stock_fixed` and safely retains ordinary SageTV Fixed/Push, which passed the same physical gate. A shared replacement-player race was also corrected so release of the old player cannot erase a new-generation `SETVIDEORECT` and strand legacy/GSY playback in preview. MIMFIX-003 remains open for stock Windows, old/disabled/failed-plugin, legacy-client, and the remaining media/lifecycle matrix. |
| 76 | 2026-09-28 | Completed MIMFIX-001 and MIMFIX-002. The tokenless LAN-scoped Standard-plugin API now supports bounded caption reservations and explicit Direct Copy/Direct Transcode sessions without changing `Sage.jar`. Direct MPEG-TS segments retain CEA, Teletext, and DVB streams; Media3 observes Direct segment bytes for Android-local caption decoding. Non-Pro `.25` / `.232` passed Copy, full-GPU Transcode, CEA Off/On, Teletext CC1/CC2/Off cycling, DVB Off/On continuity, Direct/Pull visual comparison, pause/restart/reconnect/teardown, and settings restoration. Stock `.175` without the optional plugin reported `unavailable_stock_fixed`, retained ordinary SageTV Push with hardware video decode, passed pause/restart, and produced no crash signature. MIMFIX-003 remains open for the broader stock-Windows, old/disabled/failed-plugin, legacy-client, and cross-player matrix. |
| 75 | 2026-09-27 | Added MIMFIX-001 through MIMFIX-003 at user direction: first add a caption-only MIM side channel that permits full GPU Fixed transcoding, then an optional plugin-owned MIM Direct transport with explicit no-decode/no-encode Direct Copy and Direct Transcode policies, and finally the stock/legacy/cross-player matrix. Every mode requires an unmodified stock `Sage.jar`, explicit capability negotiation, and automatic fallback to ordinary Fixed when the optional Standard plugin path is absent or fails. |
| 74 | 2026-09-27 | Completed PULL-002. Media3 now performs stock-server SIZE-growth classification over a separate one-shot MediaServer connection before player construction and relaxes only the inapplicable 60-second `StuckPlayingNotEnding` detector for proven growing Pull media. The completed BargainHunt recording and a greater-than-60-second real Live TV run passed Media3 Pull/hardware A/V on non-Pro `.25` against stock `.175`/SageMC with no timeout, retry, jump-back, datasource error, or settings loss. |
| 73 | 2026-09-27 | Completed FOUND-018: removed 282 generated test files from the non-Pro shared-storage root, reclaimed about 202 MB plus an 11 MB test-tool ART cache, and changed MCP screen recording to use a unique dedicated device temporary path with unconditional non-recursive cleanup. Fifty MCP ADB tests and a physical one-second capture/cleanup gate passed. |
| 72 | 2026-09-27 | Added PULL-002 after the supplied affected-session log and screen recording proved Media3 1.11 raises `STUCK_PLAYING_NOT_ENDING` after its initial finite TS duration becomes stale during actively growing stock-server Pull playback, then generically reprepares from 392.261 seconds and lands at 332.272 seconds. |
| 71 | 2026-09-22 | Completed GH-008: pushed implementation/release commits `f203a1f` and `98ad7f6`, passed primary and independent Git-less source/APK gates, published five v0.5.95 assets with grouped bullet notes, matched every downloaded public asset hash, and verified successful repository checks, Pages deployment, latest-release API, tag target, and HTTP 200 downloader. SEEK-001 retains final visible Pro Comskip landing acceptance as an explicit open item. |
| 70 | 2026-09-22 | Added GH-008 to publish the completed SMB focus, settings-preserving automation, and two bounded ordinary-Push timeline corrections as v0.5.95 while retaining the final user-visible Pro Comskip landing acceptance as an explicit open gate. |
| 69 | 2026-09-21 | Corrected the remaining SEEK-001 timeline basis without changing keys. Stock SageTV reports detailed Push mux time at the end of the queued bytes, while Android players report from the start of the post-FLUSH epoch; the client now subtracts a bounded MPEG-TS PES-PTS span, emits raw/effective anchor evidence, and safely retains the old value for DVD Push, Pull/SMB, malformed, discontinuous, or non-TS input. Core unit tests cover the estimator and end-to-end GETMEDIATIME result; Pro user acceptance remains open. |
| 68 | 2026-09-21 | Completed SMB-002 and implemented the shared SEEK-001 correction. Server and mapping editors now enter every enabled control by D-pad on Pro `.29`. Ordinary Push now holds only its last backend-proven media time between FLUSH and the replacement timestamp anchor, preventing a rapid second skip from being based on transitional zero; Pull/SMB, initial loads, and DVD Push retain existing semantics. Pro stock-server stress produced one bounded hold for each of 10 sampled FLUSHes and sustained A/V after 20 physical FF/REW commands; the affected `.25` Fixed/Push FF, REW, large-jump, and both Comskip reference session completed without a crash. Exact user-visible Pro landing acceptance remains open because HDMI was not routed to `.29`. |
| 67 | 2026-09-21 | Added SMB-002 and SEEK-001 for the reported Fire TV Pro SMB-editor focus trap and repeated backward/wrong landing after seek or commercial skip. |
| 66 | 2026-09-21 | Completed GH-007: passed primary and independent Git-less source/APK gates, pushed `0e70947`, published five v0.5.94 assets with grouped bullet notes, matched every downloaded public asset hash, and verified green repository/Pages checks plus the latest-release API and downloader. |
| 65 | 2026-09-21 | Added GH-007 after confirming that five post-v0.5.93 commits changed Android runtime and protocol code and therefore require a new APK release rather than only a source push. |
| 64 | 2026-09-20 | Completed FOUND-017: Android now advertises only the provider-neutral `dvd_mpegts_v1` transport and `transformed_main_feature` policy, retains bounded old-value migration compatibility, and passes focused protocol/policy tests against the generic Core SPI and plugin-owned MIM implementation. |
| 63 | 2026-09-20 | Completed FOUND-016: replaced Vibe-branded DVD/playback-rate wire names with functional names and no legacy aliases, added explicit unknown-property fail-safe tests, passed Core/Android builds, and installed/launched the preserved-data APK on `.25` and `.29`. |
| 61 | 2026-09-20 | Completed FOUND-014: removed Android/Core private commissioning events 230-232 after their stock-plugin `Watch`, from-beginning, and `ChannelSet` replacements passed on `.175`; routed MCP seek through public `Seek(long)` and physically proved stable ALADDIN DVD Push forward/backward seeks. |
| 62 | 2026-09-20 | Completed FOUND-015: replaced event-233 DVD reload with a client-local Media3 Surface refresh that performs no seek or transport replacement, removed the event from Android/Core, and added bounded MCP verification for the stock-server/unified-off physical gate. |
| 58 | 2026-09-19 | Completed GH-006: pushed implementation/release commits `3cf637d` and `6c4b890`, passed the primary and fresh Git-less source/APK gates, published five v0.5.93 assets with grouped bullet notes, matched every downloaded public asset to its local SHA-256, and verified successful repository checks, Pages deployment, latest-release API, and HTTP 200 downloader. |
| 57 | 2026-09-19 | Added GH-006 after explicit user approval to publish the accumulated 0.5.93 playback update, including grouped bullet-formatted release notes and public artifact/workflow/Pages verification. |
| 56 | 2026-09-19 | Completed UI-001: consolidated the long-press Video/Audio/Subtitles-CC/Aspect controls, flattened the Video settings rows, removed duplicate and obsolete shortcuts, added explicit GSY delegate choices, prevented label/value truncation, combined Video Information with Vibe diagnostics, and fixed its themed-context Activity crash. |
| 55 | 2026-09-19 | Corrected the embedded A/V sync calibration path after proving that a fully buffered fixture could retain already-extracted timestamps. Every settled adjustment now recreates the local source at the same position so samples are extracted with the new offset; both decoded and encoded calibration routes use the byte-preserving timestamp controller, and a quiet continuous pilot tone keeps HDMI/receiver audio paths awake between clicks. Pro `.29` ADB evidence proved `+1.125 s` shifted audio, `-1.125 s` delayed video, zero reset, and clean return to the MiniClient. USB HDMI was not routed to the Pro, so audible receiver synchronization remains explicitly open. The full 562 client, 87 MCP, and core Java gates pass. |
| 54 | 2026-09-19 | Increased the DVD-only Media3 Push startup/rebuffer reserve to 5 seconds after reproducing ALADDIN's opening READY/BUFFERING oscillation; Pro `.29` / stock `.175` then ran for more than 60 seconds without another transition or AudioTrack underrun while preserving the menu-cell drain bypass. The embedded A/V sync fixture also regained its full-height bounce, moved text to side columns, and reduced the bottom slider panel. Its initial UI/process check was on-device only; revision 55 records the corrected timestamp proof and explicitly leaves HDMI/receiver validation open. |
| 53 | 2026-09-19 | DVD-001 now has controlled Pro `.29` / stock `.175` evidence. Unified remained off. A DVD-specific Media3 load control keeps a 750 ms rebuffer reserve during titles but bypasses it when a reader generation must drain. ALADDIN passed the 8:00 seek and 60-second cadence gate at 0.933x with zero A/V drops; a live encoded +500 ms offset remained stable, and the authored root/Languages menus still rendered across short cells. Exact forced GFX type-5 recovery, Stop/teardown, and Unified On remain open. |
| 52 | 2026-09-19 | Added DVD-001 after reproducing the Fire TV Pro ALADDIN exit. The captured session had Unified graphics disabled and negotiated legacy `SEPARATE`; a ZLIB GFX failure was followed by a transient stock-server type-5 reconnect rejection, player teardown, and a secondary Media3 track-diagnostics null dereference. The task also retains the separate encoded-passthrough/DVD AudioTrack-underrun A/B rather than incorrectly blaming the offset from TV-video evidence. |
| 51 | 2026-09-19 | Added the built-in bouncing-ball A/V synchronization test and deterministic embedded media generator. The local Media3 test exercises H.264 decode plus the selected PCM/AC-3 output route, aligns impact/flash/click, supports live 25 ms correction and zero reset, restores the underlying program mute state, and applies the result to the session on Back. Host generation/probe/contracts/build pass; physical receiver calibration remains open. |
| 50 | 2026-09-19 | Pro `.29` / stock `.175` physically passed the AUDIO-006 ANR regression through both debug stress and the real on-screen offset slider. Rapid `8x` right/left adjustment plus Back/Save retained the process and active playback with no new ANR/fatal exception; cleanup restored decoded PCM/off/zero. The broader receiver/ARC lifecycle matrix remains open. |
| 49 | 2026-09-19 | Reproduced the Pro passthrough-offset failure as repeated Fire OS input-dispatch ANRs, not a Java exception. Offset/toggle changes no longer release and rebuild encoded player/AudioTrack state on the UI thread; the existing atomic extractor controller updates in place and one debounced same-position seek flushes old timestamps. Output-mode changes still use the required rebuild. |
| 48 | 2026-09-19 | Added and physically proved debug-only MCP `dev_set_active_audio`, removing menu-coordinate dependence from future AUDIO-006 receiver/ARC tests. The rebuilt settings-preserving APK leaves the live session at decoded PCM, zero offset, and passthrough offset off. |
| 47 | 2026-09-19 | AUDIO-006 passed its first physical client-path smoke on non-Pro `.25` / stock `.175`: direct Media3 and legacy Exo Pull each applied `+25 ms` to audio timestamps, `-25 ms` to video timestamps, reset to zero, remained healthy, and returned to decoded PCM/off. The real encoded receiver/ARC lifecycle matrix remains open. |
| 46 | 2026-09-19 | Started AUDIO-006: added the default-off encoded-passthrough offset menu contract, byte-preserving Media3/legacy-Exo timestamp adapters (GSY inherits; IJK stays unavailable), debounced same-position re-anchor, diagnostics, and automated tests. The task remains open and the saved default remains off pending real TV/receiver/ARC validation of both signs and lifecycle transitions. |
| 45 | 2026-09-19 | Completed the Kodi-inspired AUDIO-002 UI refinement while retaining Vibe colors: compact two-column Audio settings, top live offset slider, `25 ms` increments across `-4.000..+4.000 s`, Back-to-parent session behavior, and an explicit device-default action. Stock `.175` / non-Pro `.25` physical validation confirmed adjustment, reset, focus return, and navigation. AUDIO-006 remains open for a separately proven encoded-passthrough player-clock implementation. |
| 44 | 2026-09-19 | Added AUDIO-006 for a truthful Kodi-style encoded-passthrough A/V clock offset and Audio settings option. The task preserves encoded bursts, handles positive/negative correction at the appropriate audio/video scheduling layer, requires Media3/legacy-Exo plus lifecycle/receiver validation, and leaves unsupported backends disabled rather than reporting a false offset. |
| 43 | 2026-09-19 | Refined AUDIO-002 UI: Audio Output & Sync and every nested output/offset/track chooser now use the compact caption-dialog layout. Hardware Back and the Back button return to the prior audio level, and completed selections reopen the parent audio menu. Python/Android build gates and stock `.175` / non-Pro `.25` physical navigation passed; clean APK SHA-256 `bb0e513db8f2b739283a15c908ecb632bb95f36eb314cab22fb724f37fc5120d`. |
| 42 | 2026-09-19 | Completed AUDIO-002 through AUDIO-004: added the dedicated long-press Audio Output & Sync icon/menu, forced stereo PCM plus bounded signed live offset in direct Media3/legacy Exo and their GSY delegates, truthful IJK/system capability handling, live same-position output rebuild, and debug/stats export. Recorded Pro/non-Pro stock-server physical results while leaving the original surround-path synchronized measurement and full lifecycle pulse matrix open under AUDIO-001/AUDIO-005. |
| 41 | 2026-09-19 | Expanded AUDIO-001/AUDIO-005 after the Fire TV Pro became synchronized through the HDMI capture: require an A/B against the original TV/surround/ARC chain. Fire OS changed encoded-surround policy from `FORCE_ENCODED_SURROUND_ALWAYS` to `FORCE_NONE`, and the capture route exposes active 48 kHz stereo PCM, so no model-wide `+500 ms` correction may be inferred. |
| 40 | 2026-09-19 | Added AUDIO-001 through AUDIO-005 for the reported Fire TV Pro `PBSNewsHour` approximately `+500 ms` device/output-path offset: require matched Pro/non-Pro characterization, decoded-PCM Media3/legacy-Exo implementation, truthful GSY/IJK capability, encoded-passthrough safety, and physical HDMI regression without model-specific automatic offsets. |
| 39 | 2026-09-19 | Completed CC-007. DVB is no longer a CC1/CC2 type or Auto candidate; one top-level DVB selection now owns bitmap captions. Legacy slot-DVB preferences normalize to Auto. Stock `.175` / non-Pro `.25` selected DVB track 2 with local bitmap cues and no Teletext overlay. APK SHA-256 `e5551f9b1af0dd178aae86b12f05702b2ef1352646d95f1e0b7d6eb26db31944`. |
| 38 | 2026-09-19 | Completed CC-006. Explicit local CC1/CC2/DVB now has exclusive renderer ownership and sends a one-time legacy CC reset, preventing simultaneous STV Teletext and Android DVB captions. Stock `.175` / non-Pro `.25` Media3 Pull selected DVB track 2 and rendered one caption surface. APK SHA-256 `b8dc2dd5a16e900dba06086b9b5e221c6fe8ebe83323bcadda1b8b12241249f4`. |
| 37 | 2026-09-19 | Completed CC-005. Broadcast CC and SRT/DVD subtitle selection are now independent; Auto ignores synthetic CEA tracks until real CEA samples are observed, allowing stock UK DVB/Teletext streams to resolve correctly. Full 552 Python, 86 MCP, 238 Core, static validation, clean build, settings-preserving install, and stock `.175`/non-Pro `.25` Media3 Pull hardware caption-cycle gate passed. APK SHA-256 `c51ce222718d4f44694c05db3ac81c5416c8d3c1666065e32ff4a3445533b6a5`. |
| 36 | 2026-09-19 | Added CC-005: separate STV broadcast CC1/CC2 resolution from the ordinary SRT/PGS/DVD subtitle preference, remove subtitle-language fallback from CC slots, and clarify the long-press/settings labels. Physical stock `.175` / non-Pro `.25` validation remains pending. |
| 60 | 2026-09-20 | Completed FOUND-013: reproduced the Fixed Transcoding Settings exit as an AndroidX `ListPreference` integer/string `ClassCastException`, added a pre-inflation value-preserving migration and dual-form runtime reads, changed debug writes to the canonical string form, and physically proved the preserved `.25` settings/activity/chooser with no fatal exception. |
| 59 | 2026-09-20 | Completed FOUND-012: fixed updated Core DVD transcoder resolution to honor the stock `SageTVTranscoder`-first path, made Android diagnostics/harness distinguish real MIM from Native fallback, and passed the generated authored DVD on `.232` / `.25` through VAAPI `h264_vaapi` and hardware AVC at 1.002x with zero drops plus control/STOP recovery. Stock `.175` and stock `ffmpeg` remained untouched. |
| 35 | 2026-09-16 | Completed UNIFIED-001: regenerated the 1,449-file manifest, rebuilt/installed APK `0f827b7b3955594e67fef4a763b5644c8fca5a555bfc81b63262b7bfe2769489`, passed 552 static tests, 86 MCP tests, Core/Android tests, and physical unified-graphics OFF/ON stock `.175` sessions on non-Pro `.25`; fixed subsequent format-256 GDX texture rows and documented the HD300 video-plane fallback. |
| 34 | 2026-09-16 | Added UNIFIED-001: opt-in stock HD200/HD300 unified graphics capability, format-256 Y/UV image bridge, safe video-plane fallback, Playback Settings switch, and MCP A/B control. |
| 33 | 2026-09-16 | Fixed CC-004 startup timing: Media3 and legacy Exo reapply the persisted caption slot when asynchronous subtitle tracks are discovered, so DVB captions activate automatically on a new playback session without reselecting the menu item. |
| 32 | 2026-09-16 | Fixed CC-004 follow-up: preserve the stored `dvb` mode in the MediaCmd getter and show SageTV authority feedback only for explicit STV selection, so the caption menu no longer reverts DVB to the previous mode. Rebuilt and installed in-place on non-Pro `.25` without clearing settings. |
| 31 | 2026-09-16 | Completed CC-004: added explicit Android-local DVB caption mode beside OFF/CC1/CC2/STV, prevented server caption state and Teletext fallback from replacing it, and kept the UI/diagnostics truthful about DVB bitmap versus Teletext. |
| 30 | 2026-09-14 | Completed TTX-003 on stock `.175` / non-Pro `.25`: compact persistent CC1/CC2 profile UI, verified stream inventory, asynchronous re-resolution, continuous Teletext delivery, and physical Off/CC1/CC2/Off/CC1 evidence all pass. A caption-scoped HD300 binary/source audit proved DVB bitmaps were decoded to a local surface and selected separately by command 36/type 1; Android now accepts that PID command without falsely advertising the HD300 unified-YUV graphics cache. The broader EXT-004 audit remains deferred and phase #5 remains stopped. |
| 29 | 2026-09-14 | Added deferred EXT-004 at user direction: perform a complete clean-room HD200/HD300 firmware and legacy-extender behavior audit for reusable Android features after current caption/release work; do not start it during TTX-003. |
| 28 | 2026-09-14 | Added TTX-003 at user direction: make CC1/CC2 virtual stream-aware slots, expose dynamic type/language controls, and add a read-only current-video inventory plus resolved-service diagnostics. Phase #5 remains stopped. |
| 27 | 2026-09-14 | Completed TTX-002 on stock `.175` / non-Pro `.25`: independent Level-1 Teletext decoding, CC1/CC2 event-225 mapping, direct device rendering, long-press CC selection, lifecycle resets, ordered concurrent delivery, and an independent player-clock pump. Fixed the reported intermittent caption freeze caused by idle STV OSDs stopping `GETMEDIATIME`; HDMI, pause/resume, seek, Breakfast, and Classic Holby evidence pass. Phase #5 remains stopped. |
| 26 | 2026-09-14 | Added TTX-002 at user direction as the active post-release feature: independently implement and physically validate stock-server DVB Teletext subtitle-page decoding, timing, selection, shared rendering, and lifecycle safety without copying GPL reference-player decoder code. Phase #5 remains stopped. |
| 25 | 2026-09-14 | Completed TTX-001. Fixed a case-sensitive Media3 FFmpeg MPEG-L2 capability alias that had made stock `.175` reject otherwise supported Pull playback and fall back to a 352x240 MPEG-2 transcode. On non-Pro `.25`, the corrected APK played the original H.264 TS with hardware AVC and physically passed bounded Teletext preservation for Breakfast (PID `0x157f`, page 888, 446 timestamped PES/1,338 units) and Classic Holby (PID `0x0947`, page 888, 76 timestamped PES/98 units), both with zero PTS regressions and continuity errors. Taskmaster played through an exact stock MediaServer path and correctly returned `not-detected` as the DVB-bitmap-only negative control. Phase #5 remains stopped. |
| 24 | 2026-09-14 | Added TTX-001 at user direction: implement the bounded pre-decoder Teletext PES preservation test inside Test Current Video and physically validate the two Teletext-positive USER_TEST recordings plus the Taskmaster negative control on non-Pro `.25` against stock `.175`. Phase #5 remains stopped. |
| 23 | 2026-09-13 | Completed GH-005: committed the SMB implementation and v0.5.92 release state as `d7dda33` and `8a3def2`, passed the primary and fresh Git-less source test/validation/clean-build gates, published five bullet-documented assets at `v0.5.92`, re-downloaded and verified the public APK SHA-256, confirmed successful GitHub source contracts, and verified the Pages latest-release endpoint. Phase #5 remains stopped. |
| 22 | 2026-09-13 | Added GH-005 after explicit user approval to publish the completed SMB-001 work as GitHub source/APK release v0.5.92; phase #5 remains stopped. |
| 21 | 2026-09-13 | Completed SMB-001: added reusable authenticated/anonymous server-share profiles, compact labeled server editor, separate server and folder chooser dialogs, folder and parent icons, root-parent return to server selection, mapping-local edit resolution, port-445 default/custom-port parsing, compatibility materialization for all SMB consumers, and settings-preserving APK updates. Static validation, 51 focused Linux tests, Core/Android JUnit, a 63-task Android build, physical `.25` browsing/Test/Apply against stock `.175`, and preserved-settings reinstall all passed. |
| 20 | 2026-09-13 | Added SMB-001 at user direction after the published GitHub release while keeping phase #5 stopped: redesign normal SMB setup around reusable servers, separate mapping/folder dialogs, and compatibility-preserving derived values. |
| 19 | 2026-09-13 | Completed GH-004: created the excluded temporary ready-to-paste forum announcement under workspace `artifacts/temp`, directing playback reports to the GitHub issue form and documenting Test Current Video plus no-email SMB diagnostic export. All four tightly cropped generated-fixture screenshot URLs returned HTTP 200. Also published a separate tested New feature request form and provisioned the playback labels referenced by the existing issue form. |
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

### Archived completed checklist items (2026-09-30)

These completed items were moved from active task sections immediately
before commit. Stable IDs, acceptance evidence, and source context are
preserved; active sections contain unchecked work only.

#### From `## 0. Completed foundation retained for continuity`

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

- [x] **FOUND-012 - DVD MIM plugin integration.** Route the updated Core DVD
  transform through SageTV's stock transcoder-path precedence, expose the real
  negotiated MIM transport in Android diagnostics, apply transport-specific
  DISC gates, and physically prove the generated authored DVD through the
  optional FFmpeg plugin on isolated `.232` / non-Pro `.25` without replacing
  stock `ffmpeg` or modifying stock `.175`.

- [x] **FOUND-013 - Fixed-transcoding settings type migration.** Normalize
  legacy integer bitrate preferences to AndroidX list strings before screen
  inflation, preserve their exact values and every unrelated setting, make
  runtime integer reads accept both representations, and prove the retained
  `.25` preferences render and remain selectable without a process exit.

- [x] **FOUND-014 - Stock API commissioning-event replacement.** Remove private
  MiniClient events 230-232 from Android/Core after exact indexed watch,
  from-beginning, and dotted-channel controls pass through the stock-compatible
  Core MCP plugin. Route automation seeks through public `Seek(long)` and prove
  stable stock DVD Push seeks physically.

- [x] **FOUND-015 - Stock-compatible local DVD output refresh.** Replace the
  event-233 server-seek decoder-reload handshake with a bounded Media3 Surface
  refresh that retains the player, Push datasource, DVD logical clock, audio,
  and playback intent. Remove event 233 from Android and Core, expose a bounded
  MCP verification control, and gate it on stock Core with unified graphics
  disabled.

- [x] **FOUND-016 - Functional capability protocol names.** Replace the
  Vibe-branded DVD and general playback-rate properties with the functional
  `DVD_DISC_*` and `VIDEO_PLAYBACK_RATE` names without legacy wire aliases,
  make unknown GET/SET properties fail safe, rebuild Core and Android, and
  install/launch the settings-preserving APK on Pro and non-Pro Fire TVs.

- [x] **FOUND-017 - Provider-neutral DVD transform.** Replace MIM-specific DVD
  transport and policy values with `dvd_mpegts_v1` and
  `transformed_main_feature`, preserve only bounded saved-setting/URL migration
  compatibility, and prove negotiation and fallback with focused tests. Core
  owns only the generic SPI; the optional FFmpeg plugin owns MIM execution.

- [x] **FOUND-018 - Device test-artifact hygiene.** Remove accumulated
  root-level screenshots, UI dumps, logs, and screen recordings from the
  non-Pro test device. Capture screenshots directly to the host and stage
  screen recordings only in a dedicated device directory that is removed in a
  `finally` block after pull success or failure. Preserve app settings,
  diagnostics, unrelated media, and other applications.


#### From `## 1. ONN hardware MPEG-2 and long-recording UI regression`

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

- [x] **PULL-001 - Growing-recording backward-position guard.** Protect
  Media3 Pull playback when a growing MPEG-TS reports an unexplained large
  backward position jump: preserve the last stable position and recover the
  source once without changing explicit seeks or completed-file behavior.
  Unit coverage, Android shared Gradle tests, and a direct stock-server
  non-Pro `.25` run of `TheChase-26742651-0` passed; the 90-second observation
  advanced monotonically with no reset. The original 10--20 minute report was
  not reproduced, so retain it as affected-device follow-up evidence rather
  than treating this focused gate as proof of universal closure.

- [x] **PULL-002 - Growing-recording stale-duration timeout.** Correct the
  Media3 1.11 `STUCK_PLAYING_NOT_ENDING` false positive on stock-server
  timeshifted Pull playback. A growing TS may continue delivering and rendering
  appended bytes after Media3's initial finite period duration; the generic
  60-second detector must not show `ERROR_CODE_TIMEOUT` and reprepare at an
  approximate TS sync point. Preserve every other stuck-player detector,
  completed-file behavior, explicit seeks, and non-Pull transports. Validate
  the supplied `BargainHunt-Naseby31-26776208-0.ts` evidence and a real growing
  recording on stock `.175`, SageMC, and non-Pro `.25` without changing Core.

- [x] **CC-004 - Explicit DVB caption mode.** Add `DVB` beside `OFF`, `CC1`,
  `CC2`, and `STV` in the Android caption selector. DVB selects a local bitmap
  subtitle track, bypasses server CC-state and Teletext fallback while active,
  and keeps the displayed mode truthful. STV remains server/STV-controlled and
  actual Teletext is only bridged when a Teletext track is selected.

- [x] **CC-005 - Separate broadcast CC from ordinary subtitles.** Ensure stock
  SageTV `VIDEO_CC_STATE`/CC1/CC2 resolves only broadcast caption services,
  never the generic SRT/PGS/DVD subtitle preference. Label the
  long-press UI and playback settings so users can tell **Broadcast captions
  (CC)** from **Subtitles (SRT/DVD)**, preserve English-first Auto behavior,
  and validate the fix on stock `.175` / non-Pro `.25` without clearing data.
  Completed with evidence-based Auto fallback for synthetic CEA tracks: the
  stock UK Breakfast stream selected Teletext for the legacy event-225 path,
  passed continuous clock delivery, and passed Off/CC1/CC2/Off/CC1 cycling.

- [x] **CC-006 - Single caption renderer and truthful stock-DVB ownership.**
  When stock SageTV has no `VIDEO_CC_STATE`, make explicit local CC1/CC2/DVB
  selection disable and flush the event-225 Teletext bridge before Android
  renders Teletext or DVB. Label stock STV DVB as `STV Subtitles`, omit
  synthetic/unobserved CEA services from the active inventory, and prevent a
  previously selected STV CC channel from remaining as a second caption.
  Non-Pro `.25` / stock `.175` Media3 Pull hardware playback selected DVB track
  2 and rendered one bitmap-caption surface; evidence is
  `artifacts/firetv/20260919-152730_caption-media3-pull-visible.png`.

- [x] **CC-007 - Make DVB an explicit mode, not a CC1/CC2 mapping.** Remove
  DVB from both virtual-slot type menus and from CC1/CC2 Auto resolution.
  Preserve CEA/Teletext virtual slots, show DVB in the inventory with a direct
  `select DVB` instruction, and normalize the short-lived saved CC-slot DVB
  value to Auto. Selecting the one top-level `DVB` mode remains the only local
  bitmap override. Core policy tests, 92 focused Python tests, source-contract
  validation, clean build, and stock `.175` / non-Pro `.25` Media3 Pull passed;
  DVB mode selected track 2 with bitmap cues and no Teletext overlay.

- [x] **UNIFIED-001 - Opt-in HD media-player graphics capability.** Audit the
  stock SageTV `GFX_YUV_IMAGE_CACHE=UNIFIED` contract and implement it behind a
  persisted Playback Settings switch. When enabled, negotiate the stock
  unified capability, retain DVB/TS behavior, decode format-256 Y/UV image
  lines through the Android renderers, and diagnose/fail safely for an
  unsupported HD300 video-plane handle. When disabled, preserve the existing
  handshake exactly. Validate the setting and MCP control on non-Pro `.25`
  against stock `.175`, including reconnect, ordinary playback, DVB captions,
  seek, and renderer fallback. Completed: Core/Android unit tests, 552 static
  tests, 86 MCP tests, clean APK build/install, and stock `.175`/non-Pro `.25`
  physical OFF/ON sessions all passed playback, audio, fullscreen, seek,
  pause/resume, stop/teardown, reconnect, and error-state gates. The GDX
  renderer now updates subsequent format-256 Y/UV rows in the existing texture;
  unsupported HD300 handles remain a safe logged SurfaceView fallback.


#### From `## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase`

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


#### From `## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase`

Parent context: `- [ ] **AUDIO-001 - Matched Pro/non-Pro A/V-sync characterization.** Determine`

  - [x] Pro A/B establishes that the delay follows the external surround/output
    route: Fire OS PCM and the HDMI-capture route are synchronized, while Best
    Available through the original surround path exposes the reported delay.


#### From `## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase`

- [x] **AUDIO-002 - Runtime decoded-PCM audio offset.** Implement a bounded,
  timestamp-aware signed audio offset for Media3 and legacy ExoPlayer using
  their supported audio-sink/renderer contracts rather than adding a second
  master clock or an unbounded sleep. Match Kodi's user-facing sign convention,
  provide a `-4000..+4000 ms` range in `25 ms` increments, apply changes to active playback,
  and preserve SageTV's media timeline, seeking, pause/resume, track switching,
  growing-stream, and lifecycle ownership.
  - [x] Replace the generic full-size audio dialogs with a Kodi-inspired
    two-column Audio settings panel while retaining Vibe colors. The live
    offset editor is a compact top slider with `25 ms` Left/Right increments
    over `-4.000..+4.000 s`; Back keeps the current-playback value and returns
    to Audio settings, while **Set as default for all media** is the separate
    persistent action. Physical non-Pro validation confirmed the layout,
    focus return, live adjustment, zero reset, and Back navigation at 1080p.

- [x] **AUDIO-003 - Backend delegation, scope, and truthful capability.** Make
  GSY Media3 and GSY legacy-Exo delegates inherit the proven offset behavior;
  preserve session-only and saved-device-default scopes and export requested,
  effective, and applied offsets through stats/MCP/diagnostic bundles. Keep IJK
  unavailable unless its real output API can apply the same contract; never
  report success for a backend that ignored the setting.

- [x] **AUDIO-004 - Encoded-passthrough safety boundary.** Support audio offset
  first for decoded PCM. When AC-3/E-AC-3/DTS encoded passthrough is active,
  either use a separately proven timestamp-safe mechanism or fail closed with
  a precise explanation. Do not silently disable passthrough, corrupt IEC61937
  bursts, or apply an automatic Fire-TV-model offset.


#### From `## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase`

- [x] **AUDIO-005 - Physical offset and regression gates.** On Fire TV Pro and
  unmodified stock `.175`, measure the original TV/surround route and compare
  the alternate HDMI-capture PCM route with the authored pulse fixture and the
  PBS NewsHour speaking interval. Test seek, pause/resume, audio-track and
  PMT/format transitions, live replacement, HOME/return, and teardown across
  direct Media3, direct legacy Exo, and their GSY delegates. The current-code
  gate passed using same-clock C920 video/microphone evidence plus the focused
  four-backend encoded lifecycle checks at revision 113. The user prioritized
  completion before MIMFIX-003; only a later change touching the A/V path
  invalidates the affected rows.

Parent context: `- [x] **AUDIO-005 - Physical offset and regression gates.** On Fire TV Pro,`

  - [x] Pro `.29` / stock `.175` Media3 Pull changed decoded PCM -> encoded
    passthrough -> decoded PCM during active playback, preserved position, and
    changed telemetry between software AC-3 decode and hardware passthrough.

  - [x] Pro live `+250 ms` application and reset to `0 ms` completed without a
    playback restart or error.

  - [x] Non-Pro `.25` / stock `.175` passed Media3, legacy Exo, IJK, and GSY
    Auto startup with decoded audio; legacy Exo also passed the live
    decoded/passthrough/decoded transition.

  - [x] Add a server-independent, in-client bouncing-ball calibration test to
    Audio settings. Its reproducible embedded 1280x720/59.94 H.264 plus 48 kHz
    stereo AC-3 fixture aligns ball impact and a 25 ms click; it
    loops through Media3 using the selected decoded/passthrough route, adjusts
    in `25 ms` steps, restores the program mute state, and applies the selected
    session offset on Back. Host generation, probe, contracts, and APK build
    pass; physical receiver/display calibration remains part of the open gate.

  - [x] Restore the full-height bounce path, move all fixture identity and
    impact instructions into the unused left/right side columns, and move the
    compact slider panel into the lower-right side column so it cannot cover
    the impact travel. A Pro `.29` screenshot confirmed the center path remained
    clear. On-device ADB/log validation confirmed that `+1.125 s` shifted 1,742
    audio samples, `-1.125 s` delayed 3,555 video samples, Center reset to
    `0.000 s`, and Back returned to the active MiniClient without a fatal
    exception. This is internal timestamp proof, not HDMI/receiver audible
    synchronization evidence.


#### From `## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase`

- [x] **AUDIO-006 - Encoded-passthrough A/V clock offset.** The opt-in
  Media3/legacy-Exo timestamp paths shift audio for positive and video for
  negative correction without changing encoded sample bytes; their GSY
  delegates inherit the policy. The original Pro TV/surround C920 measurements
  passed both signs, near-full-range values, visible endpoints, and zero reset.
  The four-backend stock-server lifecycle gate retained an applied `-400 ms`
  encoded path through HOME, user pause, return, PLAY, and teardown at revision
  113. IJK remains unavailable; the saved default remains off.

Parent context: `- [x] **AUDIO-006 - Encoded-passthrough A/V clock offset.** Add an Audio`

  - [x] Add an opt-in, default-off Audio settings toggle and preserve the
    existing fail-closed behavior when the active backend cannot support it.

  - [x] Implement independent Media3 and legacy-Exo extractor timestamp
    adapters. Positive values shift audio presentation timestamps; negative
    values shift video presentation timestamps; encoded sample bytes continue
    through the original `sampleData` path unchanged. GSY inherits the active
    delegate and IJK remains unavailable.

  - [x] Apply offset/toggle changes to the active atomic timestamp controller,
    then perform one debounced same-position seek to flush pre-change queued
    samples. Do not release/rebuild the encoded player or AudioTrack for an
    offset-only change; cancel pending work at teardown and preserve existing
    source-replacement, track, format, seek, and pause/resume lifecycle paths.

  - [x] Export requested/applied path, enabled state, and shifted-sample
    counters through Playback Stats, debug/MCP state, and diagnostic bundles;
    add controller unit and source-contract tests.

  - [x] On non-Pro `.25` against stock `.175`, Media3 Pull and legacy Exo Pull
    both passed live `+25 ms` audio-delay, `-25 ms` video-delay, and zero-reset
    smoke checks on indexed `Scream_1`. Debug counters proved the intended
    timestamp side changed, playback remained active without an error, and the
    session was restored to decoded PCM with passthrough offset off. This is a
    client-path smoke check, not the receiver/ARC synchronization gate below.

  - [x] Add debug-only MCP `dev_set_active_audio` control for deterministic
    output-mode, enable/disable, and signed-offset changes. Tool discovery and
    a live Media3 `+25 ms` application/reset passed on `.25`; the final state
    was decoded PCM, zero offset, passthrough offset off, and healthy playback.

  - [x] Reproduce the Pro `.29` failure as a Fire OS input-dispatch ANR while
    the old offset-only path released/rebuilt the encoded player and AudioTrack
    on the UI thread. With the in-place controller/re-anchor fix installed,
    direct Media3 Pull on stock `.175` passed rapid debug changes and the real
    on-screen slider (`8x` right, `8x` left, Back/Save): the PID remained
    stable, playback stayed active, and clean logcat contained no new ANR or
    fatal exception. The session was restored to decoded PCM, offset disabled,
    and `0 ms`.


#### From `## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase`

Parent context: `- [ ] **DVD-001 - Fire TV Pro sustained ALADDIN playback regression.** Fix the`

  - [x] Proved the captured exit was not caused by Unified graphics: the
    failing session had `unifiedGraphicsSurfaces=false` and negotiated the
    legacy `SEPARATE` connection path.

  - [x] Added bounded stock-server type-5 GFX reconnect retries and made
    Media3/legacy-Exo track diagnostics change-driven and teardown-safe.

  - [x] Replaced Media3 DVD Push's zero/short rebuffer threshold with a
    `5,000 ms` startup and rebuffer reserve (`12,000 ms` maximum) while
    explicitly bypassing that reserve when a DVD reader generation must drain.
    This preserves short authored menu cells.

  - [x] Pro `.29` / stock `.175` Media3 hardware ALADDIN sought to `479846 ms`
    and advanced `56123 ms` in `60145 ms` (`0.933x`) with zero dropped video
    or audio frames and no process exit. A live encoded `+500 ms` offset also
    remained active for the follow-up observation with zero drops.

  - [x] Physically rendered the authored fixture root menu and Languages
    submenu after the load-control change, proving the segment-drain bypass
    retained short-cell navigation.

  - [x] Reproduced the opening READY/BUFFERING oscillation on Pro `.29` against
    stock `.175`, then repeated ALADDIN with the five-second reserve for more
    than 60 seconds. No READY/BUFFERING transition or AudioTrack underrun was
    observed after startup; decoded media stayed roughly `5.7..13.5 s` ahead.


#### From `## 2. Kodi/VLC/FFmpeg-derived hardware-decoder stability phase`

Parent context: `- [ ] **DEVICE-001 - Android tablet compatibility report.** After D6, diagnose`

  - [x] Built-APK inspection proves `minSdkVersion=23`, target API 36, and both
    ARM32/ARM64 ABIs. Documentation now states Android 6.0+ accurately; Android
    5 API 21/22 is an explicit unsupported install boundary.

  - [x] The diagnostic contract documents the exact device, decoder, cadence,
    AudioTrack, passthrough, underrun, buffer, and current-video evidence an
    Android 8 reporter can export without ADB.


#### From `## 3. Final physical playback matrix and release gate`

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


#### From `## 4. GitHub source and APK refresh after active phases`

- [x] **GH-001 - Release documentation and metadata.** Update `CHANGELOG.md`, `HANDOFF.md`, durable compatibility/diagnostic
  documentation, task state, release metadata, and complete manifests from the
  final validated tree. Update the GitHub issue/reporting instructions and
  screenshots to show Test Current Video and diagnostic ZIP export/upload.

- [x] **GH-002 - Independent release verification.** Build and verify the clean APK, source archive, handoff/update bundle,
  checksums, and GitHub workflow from an independent checkout.

- [x] **GH-003 - Approved GitHub publication.** After explicit approval only, create logical commits, push `main`, create
  the release tag, attach the versioned APK/source artifacts, and verify the
  public download hashes and GitHub Pages latest-APK redirect.

- [x] **GH-004 - Temporary forum migration post.** After GH-003 and the updated
  GitHub issue workflow are publicly verified, create a ready-to-paste Markdown
  post explaining that playback discussion and issue submission should move to
  GitHub Issues. Include the Test Current Video and diagnostic-export process
  with screenshots. Store it only under the workspace `artifacts/temp`
  directory so it is excluded from project source/releases and can be deleted
  after use.


#### From `## 4A. Active post-release feature work`

- [x] **SMB-001 - Server-first SMB setup and folder browser.** Replace normal
  raw URL/credential/mapping entry with reusable device-local SMB server/share
  profiles. Provide separate server management, media-mapping, server-choice,
  and remote-folder dialogs; persistent folder and parent icons; implicit port
  445 with optional `host:port`; authentication-controlled credential fields;
  per-profile playback credentials; and compatibility materialization for the
  existing media, configuration, and diagnostic SMB implementations. Complete
  unit/static/build gates and physical non-Pro `.25` acceptance against the
  stock `.175` share. Phase #5 remains stopped by user direction.

- [x] **GH-005 - Publish v0.5.92 SMB release.** Synchronize release metadata,
  changelog, handoff, task state, and complete manifest; run full and
  independent source/APK gates; create logical commits; push `main`; publish
  the versioned APK/source/checksum/manifest assets with bullet-formatted
  notes; and verify public hashes, GitHub checks, and the Pages latest-APK
  redirect. Do not start the stopped phase #5 matrix as part of this release.

- [x] **TTX-001 - Bounded DVB Teletext PES preservation test.** Extend Test
  Current Video with a user-initiated, metadata-only MPEG-TS probe that proves
  PAT/PMT Teletext descriptor discovery, subtitle-page identity, Teletext PES
  and data-unit delivery, PTS presence/progression, continuity, and active
  Pull/Push/SMB transport. Keep the probe dormant outside the bounded test and
  never retain media payload or decoded text. Validate positive
  `Breakfast-26711345-0.ts` and `ClassicHolbyCity-SinsoftheFather-26696712-0.ts`
  plus negative-control `Taskmaster-ThisIsFoodGlue-26714235-0.ts` from stock
  `.175` on non-Pro Fire TV `.25`, and retain the results in diagnostic export.

- [x] **TTX-002 - DVB Teletext subtitle-page decoding and rendering.** Build an
  independently implemented, stock-server-compatible Level 1 subtitle-page
  path on top of the proven TTX-001 transport parser. Discover type-2/type-5
  subtitle services, decode Hamming-protected packet/page/row identity and the
  Latin G0 character/control subset needed by broadcast subtitle page 888,
  synchronize page updates and clears from PES PTS, and render through one
  Android overlay shared by Media3, legacy Exo, IJK, and GSY delegates. Expose
  Teletext tracks in the long-press subtitle selector, honor Off and preferred
  language without changing DVB bitmap or CEA behavior, reset safely on seek,
  flush, source replacement, and teardown, and retain bounded diagnostics.
  Validate Breakfast and Classic Holby on non-Pro `.25` against stock `.175`
  over original-TS Pull, including visible text, timing, Off/On, seek recovery,
  and no regression to hardware video or audio playback. Do not copy GPL
  decoder source; record the standards/reference behavior and implementation
  provenance. Completed with an independent Level-1/page-888 decoder, stock
  event-225 CC1/CC2 bridge, optional device overlay, direct long-press CC
  selector, independent 100 ms player-clock delivery, ordered cross-thread
  draining, and bounded diagnostics. Breakfast and Classic Holby physically
  pass original-TS hardware Pull on non-Pro `.25` against stock `.175`;
  Breakfast additionally passes continuous HDMI evidence, pause/resume, and
  large-seek recovery without relying on the SageTV OSD clock.

- [x] **TTX-003 - Stream-aware virtual CC1/CC2 controls.** Replace the crowded
  long-press caption selector with persistent virtual CC1/CC2 profiles for
  Auto, Teletext, DVB, CEA-608, and CEA-708 plus discovered-language choices.
  Show a read-only inventory of caption services in the active video and the
  actual service resolved for each slot, never infer a CEA-608 language, keep
  STV authority separate, and re-resolve profiles after asynchronous track
  discovery. Validate the UI and selection on non-Pro `.25` against stock
  `.175` without regressing continuous Teletext delivery. Completed with
  compact nested dialogs that remain open across selections, dynamically
  filtered type/language choices, verified service inventory, and distinct
  virtual-slot resolution. Stock `.175` on non-Pro `.25` physically passed
  Off/CC1/CC2/Off/CC1 with continuous event-225 delivery; CC2 visibly rendered
  the sole Teletext service and Off visibly removed it. A caption-scoped HD300
  binary audit also proved DVB bitmaps used a separate local surface and server
  command 36/type 1, not event 225. Vibe accepts that ordinary-video PID/disable
  command without changing DVD SPU behavior and retains client-local DVB
  selection when stock SageTV suppresses the command for non-HD300 graphics
  capabilities. Phase #5 remains stopped.

- [x] **UI-003 - Last-channel quick action.** Add a matching white vector icon
  between Page Up and Page Down in every active menu layout, labelled Last
  (Previous Channel), using the existing stock prev_channel event 60. Source
  binding/layout/vector contracts, compile and physical middle-cell focus pass.
  No new server endpoint or two-channel playback matrix is required for this
  UI addition; an actual live-TV recall was not performed in this gate.

- [x] **UI-004 - Nearest icon fallback.** Preserve the requested row/column
  when possible; otherwise choose the closest visible enabled focusable icon
  ahead in any row/column. Keep focus only if nothing exists in that direction.
  Eight unit tests and physical non-Pro horizontal/vertical fallback checks
  pass. This supersedes UI-002's strict empty-axis edge behavior without
  discarding its historical completion evidence.

- [x] **UI-002 - Sparse long-press menu focus.** Up/Down retains the column
  across empty rows; Left/Right retains the row across empty columns. Select
  the nearest visible, enabled, focusable icon along that axis; no candidate
  retains focus rather than wrapping. Scope this to the local icon dialog;
  preserve Center activation, Back and playback/STV arrows. Six policy JUnit
  tests, build, and physical non-Pro `.25` hierarchy checks pass (2026-10-06,
  APK ded7be21). Raw XML is retired after this written completion record.

- [x] **UI-001 - Consolidated playback controls and video information.** Keep
  Aspect ratio and place Video, Audio, and Subtitles/CC together in the
  long-press row; remove the redundant player-switch and four-arrow shortcuts;
  remove audio/caption/stats/test duplicates from Video settings; expose
  Player, Decoding, Codec Queueing, Source buffering, Display, DVD playback,
  decoder restart, and reset in a compact non-truncating panel; identify each
  GSY delegate; and make the triangle open the combined compact SageTV Video
  Information and Vibe Diagnostics screen without the themed-context crash.
  Focused contracts and the complete project/MCP/Core/build gates pass.

- [x] **GH-006 - Publish v0.5.93 playback update.** Synchronize release
  metadata, changelog, handoff, task state, and complete manifest; run primary
  and fresh Git-less source/APK gates; create logical commits; push `main`;
  publish the versioned APK, source, checksums, manifest, and review bundle;
  use concise bullet-formatted release notes grouped by Changes, Fixes,
  Compatibility, Validation, and Known limitations; then verify public hashes,
  GitHub checks, and the Pages latest-APK redirect.

- [x] **GH-007 - Publish v0.5.94 compatibility update.** Package the completed
  stock Core MCP bridge, private-event removal, Fixed Transcoding preference
  migration, functional capability names, and provider-neutral DVD transform
  negotiation. Run primary and independent Git-less source/APK gates; publish
  versioned APK/source/checksum/manifest/review assets with grouped bullet
  notes; verify public hashes, repository checks, and the Pages latest-APK
  redirect.

- [x] **SMB-002 - Fire TV Pro SMB editor D-pad focus.** Correct the reusable
  SMB server and media-mapping dialogs so remote focus starts on the first
  editable field, follows enabled authentication/folder controls, and reaches
  action buttons without a layout container stealing focus. Preserve all
  installed settings and validate the complete field path on Pro `.29`.


#### From `## 4A. Active post-release feature work`

Parent context: `- [x] **SEEK-001 - Fire TV Pro skip and Comskip landing regression.** Preserve`

  - [x] Correct the transitional-zero Push timeline defect and pass Pro stress
    plus the affected non-Pro stock-server reference.

  - [x] Correct the stock Push mux-end versus decoder epoch-start mismatch from
    a bounded MPEG-TS PTS span, with raw fallback and no remote-key changes.


#### From `## 4A. Active post-release feature work`

- [x] **GH-008 - Publish v0.5.95 focus and Push-timeline update.** Package the
  Fire TV SMB D-pad focus correction, settings-preserving automated-test
  transaction, ordinary-Push FLUSH continuity, and bounded MPEG-TS mux-end
  calibration. Run primary and independent Git-less source/APK gates; publish
  the versioned APK/source/checksum/manifest/review assets with grouped bullet
  notes; verify public hashes, repository checks, and the Pages latest-APK
  redirect. Keep final user-visible SEEK-001 Pro landing acceptance explicit
  as pending rather than claiming it passed.

- [x] **MIMFIX-001 - Optional stock-server Fixed caption side channel.** When
  Fixed is selected, capability-probe the separately installed OpenSageTV Vibe
  FFmpeg Standard plugin and optionally bind to its LAN-scoped, bounded
  caption session. Keep video on the established SageTV Fixed transport while
  consuming synchronized CEA-608/708 records extracted before GPU decode.
  Reset/rebind on seek, FLUSH, pause/resume, source/program replacement,
  reconnect, and teardown. Never require a modified `Sage.jar`, private
  MiniClient event, or Core protocol extension. Plugin absent/old/disabled,
  connection failure, malformed data, or caption timeout must retain ordinary
  Fixed playback and existing Android-local/STV caption behavior.

- [x] **MIMFIX-002 - Optional MIM Direct Fixed transport.** Only after
  MIMFIX-001 passes, add a separately visible `MIM Direct` option under Fixed.
  Negotiate a plugin-owned media session and consume its synchronized media,
  captions/subtitles, status, buffer, seek, reconnect, and teardown endpoints
  while preserving SageTV UI/OSD, watched state, and control authority. Show
  explicit `Direct Copy` and `Direct Transcode` choices. Direct Copy owns the
  stream but performs no video/audio decode or encode; it preserves source
  streams and captions/subtitles and permits only container remuxing required
  by the negotiated client contract. Direct Transcode shows the actual
  full-GPU, mixed, or software fallback stage in diagnostics. An incompatible
  Copy request must fall back rather than silently transcode unless an explicit
  Auto policy permits it. Auto must never select an unproven transport, and any
  startup/runtime failure must fall back safely to ordinary stock Fixed without
  a crash or lost session.


#### From `## 4A. Active post-release feature work`

Parent context: `- [ ] **MIMFIX-003 - Cross-player and stock compatibility gates.** On an`

  - [x] Complete the non-Pro cross-player Direct Copy/Transcode sub-matrix,
    including the bounded IJK Transcode fallback.

  - [x] Prove missing, disabled, and pre-Direct old-plugin negotiation retains
    ordinary Fixed/Push with advancing A/V, lifecycle recovery, no crash, and
    settings restoration.

  - [x] Prove a deliberately failed post-negotiation MIM launch remains
    playable through the stock-compatible Pull source, including FF/REW and
    pause recovery, then restore the exact executable mode and hash.

  - [x] Correct Direct-transcode caption selection/rebind across asynchronous
    track publication and backend replacement. On non-Pro `.25` / `.232`, the
    generated MPEG-2/AC-3/CEA fixture passed timestamped CEA rendering,
    same-session Off/On, continuous cues, FF A/V recovery, post-seek caption
    recovery, crash-free teardown, and restoration of all 102 settings.

  - [x] Add persistent Fixed Transcoding Settings and session-scoped live
    Fixed/MIM deinterlace `Auto` / `On` / `Off` controls
    and prove Direct Transcode with deinterlacing Off uses full hardware decode
    and encode on Linux VAAPI `.232` and Windows QSV `.185`. On non-Pro `.25`,
    both platforms passed owned-stream startup, hardware Android decode,
    seek, pause/resume, and crash gates. Correct STV-authority ownership so an
    active Fixed caption side channel feeds only SageTV event 225 and never
    also selects the preserved CEA track locally; Linux CC1/CC2/Off cycling,
    visual CC1/Off evidence, teardown, and 102-setting restoration pass.

  - [x] Close the late-start fallback gap: if the exposed Pull source is not
    playable, use SageTV's existing GFX/media reconnect to renegotiate and
    reopen ordinary Fixed/Pull without replacing the Activity, UI, watch
    session, or saved Direct preference. A debug-only one-shot fault forced
    both Direct creation and its first Pull source to fail on non-Pro `.25`
    against `.232`; the same connection generation reconnected its sockets,
    resumed advancing hardware video/audio through `SAGETV_PULL`, remained
    fullscreen and crash-free, and restored all 103 settings. The MCP exact-
    path verifier now recognizes only this named transition and still treats
    unrelated player errors as terminal.

  - [x] Run the complete owned-stream DVD startup and menu gate, including
    root/submenu presentation, highlight/cursor placement, activation, return,
    repeated submenu entry, menu/title transitions, and end-of-title return.

  - [x] Run owned-stream DVD playback controls: main-title start, STOP/restart,
    chapter next/previous, repeated FF/REW, exact seek, pause/resume, and
    repeated playback without stale sessions or incorrect timeline landing.

  - [x] Run owned-stream DVD audio/subpicture gates: audio-language changes,
    subtitle-language changes, Off and STV authority, timestamp/sync evidence,
    and no accidental audio change when selecting a subtitle.

  - [x] Run DVD Direct Copy and Direct Transcode policy gates. Prove no
    decode/encode in Copy, actual GPU/mixed/software stages in Transcode,
    stable A/V cadence, no nuisance error overlays, and preserved settings.

  - [x] Run DVD owned-stream failure gates with the plugin unavailable and with
    deliberate session-start/runtime failure. Preserve menu/control authority
    and fall back safely without a crash, black screen, or lost session.

  - [x] Complete the Linux growing-media and channel-transition row. Non-Pro
    `.25` / `.232` passed active Direct Transcode startup, hardware A/V, a
    bounded 24-hour live-edge request, REW/FF recovery, two channel changes,
    retained ownership, clean teardown, and 103-setting restoration.


#### From `## 4A. Active post-release feature work`

- [x] **GH-009 - Publish v0.5.96 Direct fallback update.** Build and inspect
  the affected APK/AAB boundaries, run only impacted source/MCP/player/Core
  gates, publish the APK/source/manifest/review assets with bullet-formatted
  notes, verify every downloaded hash and successful repository/Pages checks,
  and confirm the latest-release API plus permanent downloader endpoint.

- [x] **GH-010 - Publish v0.5.97 growing-stream update.** Package the validated
  MIM Direct active/growing client correction, run only its affected
  source/MCP/player/build/archive gates, publish the versioned APK and source
  assets with grouped bullet notes, and verify public hashes, repository and
  Pages checks, the latest-release API, and permanent downloader endpoint.

- [x] **GH-011 - Publish v0.5.98 in-progress recording recovery.** Correct the
  Direct-growing startup/fallback race reproduced from issue #3, run repeated
  Fixed/MIM starts and the matched ordinary-Pull control on non-Pro `.25`
  against `.232`, publish affected APK/source assets with bullet-formatted
  notes, verify public hashes and workflows, and comment the evidence and
  release link on the issue.

- [x] **GH-012 - Publish v0.5.99 stock growing-Pull correction.** Use the
  reporter's post-v0.5.98 diagnostic bundle to remove the remaining UI-thread
  legacy growth probe, defer real size classification to Media3's loader,
  preserve finite completed-recording seek behavior, run only the affected
  stock `.175` / non-Pro `.25` gates, publish bullet-formatted APK/source
  assets, verify public hashes and workflows, and comment on issue #3 without
  closing it.


#### From `## 6. Requires hardware, fixtures, credentials, or user decisions`

- [x] **EXT-004 - Complete legacy-extender firmware behavior audit.** After the
  active caption work and release gates, analyze the archived HD200/HD300
  firmware binaries, the surviving MiniClient/server source, exported symbols,
  capability negotiation, and public behavior reports for reusable Android
  improvements beyond captions and DISC. Cover buffering/bandwidth adaptation,
  growing/live files, seek/skip and A/V sync, decoder/timestamp/subtitle paths,
  aspect/interlace/output modes, standby/reconnect, remote input, fast switching,
  and server-side extender compatibility branches. Distinguish proven protocol
  and binary evidence from inference, do not copy proprietary/vendor code, and
  place each useful implementation behind existing physical playback and stock-
  compatibility gates.
  - [x] Start the audit after TTX-003 and the affected release gates; hash the
    archived images, verify their manifests, and extract the HD300 release and
    latest archived beta only into workspace temporary storage.
  - [x] Establish the evidence rubric and reconcile the first HD300 exported-
    symbol/property inventory with surviving MiniClient/Core source, current
    Android capability replies, and public SageTV release/firmware reports.
  - [x] Extract the HD200 compressed ROMFS payload without altering the archive,
    then compare its MiniClient behavior surface with HD300 release and beta.
  - [x] Complete domain-by-domain call/branch tracing for buffering and
    bandwidth, growing/live files, seek/skip and A/V sync, decoder/timestamp and
    subtitle paths, aspect/interlace/output, standby/reconnect, remote input,
    fast switching, and every server-side extender compatibility branch.
  - [x] Convert only corroborated, currently useful findings into focused
    Android candidates. Reject hardware/vendor-only behavior and keep each
    accepted change stock-compatible and behind its affected physical gate.
  - [x] Run the affected physical/stock-server gates, synchronize the durable
    compatibility and handoff evidence, and remove the temporary extraction.
