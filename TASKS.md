# OpenSageTV Vibe Android Client tasks

This is the only authoritative task backlog and durable master checklist for
this subproject. Completed work remains checked here and release-relevant
evidence is also recorded in `CHANGELOG.md` and `HANDOFF.md`.
Workspace-wide dependencies and release ordering may also be mirrored in the
parent workspace `task.md`, but Android-only work must remain current here so
the repository can be developed independently of Codex.

Checklist revision: **71** (2026-09-22)

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

- [x] **PULL-001 - Growing-recording backward-position guard.** Protect
  Media3 Pull playback when a growing MPEG-TS reports an unexplained large
  backward position jump: preserve the last stable position and recover the
  source once without changing explicit seeks or completed-file behavior.
  Unit coverage, Android shared Gradle tests, and a direct stock-server
  non-Pro `.25` run of `TheChase-26742651-0` passed; the 90-second observation
  advanced monotonically with no reset. The original 10--20 minute report was
  not reproduced, so retain it as affected-device follow-up evidence rather
  than treating this focused gate as proof of universal closure.

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
- [ ] **AUDIO-001 - Matched Pro/non-Pro A/V-sync characterization.** Determine
  why the same `PBSNewsHour` recording needs approximately `+500 ms` audio
  correction on Fire TV Pro AFTKRT but appears synchronized on non-Pro AFTMM.
  Compare the exact same MediaFile and interval with matched Media3 Pull and
  legacy-Exo Pull hardware configurations, primary audio track, decoded-PCM
  policy, display refresh mode, HDMI route, and server. Record source audio/video
  PTS, decoder/output identity, AudioTrack latency/underruns, rendered counters,
  and a synchronized HDMI capture. A/B the direct HDMI-capture PCM route against
  the original TV/surround/ARC route and record EDID capabilities, Fire OS encoded-
  surround policy, PCM versus encoded/direct output, and external processing.
  Do not infer a device quirk from either the current unmatched Pro GSY/legacy-
  Exo Dynamic versus non-Pro Media3 Pull comparison or the latency-free capture
  route alone.
  - [x] Pro A/B establishes that the delay follows the external surround/output
    route: Fire OS PCM and the HDMI-capture route are synchronized, while Best
    Available through the original surround path exposes the reported delay.
  - [ ] Capture the original TV/surround/ARC EDID and synchronized measurement
    with the same interval before declaring the remaining external latency
    fully characterized.
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
- [ ] **AUDIO-005 - Physical offset and regression gates.** On Fire TV Pro,
  reproduce and measure the `PBSNewsHour` correction on the original TV/surround
  path, compare the same interval through the HDMI-capture PCM path, and verify
  that changing/resetting the offset is audible and visible in synchronized
  evidence. On non-Pro, prove the same APK and matched playback remain
  synchronized at `0 ms`. Then exercise the
  generated synchronized A/V-pulse fixture plus seek, pause/resume, audio-track
  change, format transition, HOME/return, and teardown across direct Media3,
  direct legacy Exo, and their applicable GSY delegates on stock SageTV first.
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
  - [ ] Complete the original surround-path synchronized measurement and the
    generated A/V-pulse seek/lifecycle matrix before closing this physical gate.
- [ ] **AUDIO-006 - Encoded-passthrough A/V clock offset.** Add an Audio
  settings option to enable signed audio offset while AC-3/E-AC-3/DTS
  passthrough is active, without modifying, padding, or corrupting encoded
  bursts. Follow Kodi's player-level `SetAVDelay` architecture: for audio-later
  correction, delay release of encoded access units to Android `AudioTrack`;
  for audio-earlier correction, delay video presentation because submitted
  receiver audio cannot be pulled backward. Implement independent Media3 and
  legacy-Exo clock/render scheduling paths and let their GSY delegates inherit
  them. Re-anchor safely across seek, pause/resume, audio-track and format
  changes, live-program transitions, source replacement, decoder rebuild, and
  teardown. Keep the menu option disabled/unavailable until the active backend
  truthfully supports it; keep IJK unavailable until a real clock API is
  proven. Validate both signs and zero reset through a real TV/receiver/ARC
  passthrough path before enabling the option by default.
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
  - [ ] Validate `-4.000..+4.000 s`, both signs, zero reset, seek,
    pause/resume, track/format changes, live transition, HOME/return, and
    teardown on a real encoded TV/receiver/ARC route for direct Media3,
    legacy Exo, and their GSY delegates. Keep the saved default off until this
    physical gate passes.
- [ ] **DVD-001 - Fire TV Pro sustained ALADDIN playback regression.** Fix the
  captured stock `.175` / Pro `.29` failure without attributing it to Unified
  graphics: the failing session negotiated legacy `SEPARATE`, then a ZLIB GFX
  read failure received a transient type-5 reconnect rejection and teardown
  raced player track diagnostics. Bound and retry the stock reconnect handshake,
  make Media3 and legacy-Exo track diagnostics session-safe and change-driven,
  then A/B ALADDIN encoded passthrough with offset enabled/disabled against
  decoded PCM to isolate its repeated Fire OS AudioTrack underruns. Validate
  sustained playback, GFX recovery, Stop/teardown, and Unified Off/On without
  hiding a genuine connection or decoder failure.
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
  - [ ] Force and verify the exact GFX type-5 reconnect retry, then complete
    Stop/teardown and Unified On comparison before closing DVD-001.
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
- [x] **GH-004 - Temporary forum migration post.** After GH-003 and the updated
  GitHub issue workflow are publicly verified, create a ready-to-paste Markdown
  post explaining that playback discussion and issue submission should move to
  GitHub Issues. Include the Test Current Video and diagnostic-export process
  with screenshots. Store it only under the workspace `artifacts/temp`
  directory so it is excluded from project source/releases and can be deleted
  after use.

## 4A. Active post-release feature work

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
- [ ] **SEEK-001 - Fire TV Pro skip and Comskip landing regression.** Preserve
  the current Pro diagnostic evidence, reproduce ordinary seek/skip and
  commercial-skip on a stock-compatible server path, and correct any shared
  requested-target/anchor/recovery defect without a device-model special case.
  Require exact target/landing telemetry, sustained A/V, and no backing-up loop
  on Pro `.29`, then run the affected non-Pro reference gate.
  - [x] Correct the transitional-zero Push timeline defect and pass Pro stress
    plus the affected non-Pro stock-server reference.
  - [x] Correct the stock Push mux-end versus decoder epoch-start mismatch from
    a bounded MPEG-TS PTS span, with raw fallback and no remote-key changes.
  - [ ] Obtain final user-visible Pro acceptance for exact skip/Comskip landing;
    USB HDMI was not routed to `.29` during the corrected stress run.
- [x] **GH-008 - Publish v0.5.95 focus and Push-timeline update.** Package the
  Fire TV SMB D-pad focus correction, settings-preserving automated-test
  transaction, ordinary-Push FLUSH continuity, and bounded MPEG-TS mux-end
  calibration. Run primary and independent Git-less source/APK gates; publish
  the versioned APK/source/checksum/manifest/review assets with grouped bullet
  notes; verify public hashes, repository checks, and the Pages latest-APK
  redirect. Keep final user-visible SEEK-001 Pro landing acceptance explicit
  as pending rather than claiming it passed.

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
- [ ] **EXT-004 - Complete legacy-extender firmware behavior audit.** After the
  active caption work and release gates, analyze the archived HD200/HD300
  firmware binaries, the surviving MiniClient/server source, exported symbols,
  capability negotiation, and public behavior reports for reusable Android
  improvements beyond captions and DISC. Cover buffering/bandwidth adaptation,
  growing/live files, seek/skip and A/V sync, decoder/timestamp/subtitle paths,
  aspect/interlace/output modes, standby/reconnect, remote input, fast switching,
  and server-side extender compatibility branches. Distinguish proven protocol
  and binary evidence from inference, do not copy proprietary/vendor code, and
  place each useful implementation behind existing physical playback and stock-
  compatibility gates. This is a future task and must not start during TTX-003.

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
