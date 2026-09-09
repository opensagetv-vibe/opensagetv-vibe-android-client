# OpenSageTV Vibe Android Client handoff

## Stock-server all-player MKV checkpoint (2026-09-09)

The current v0.5.89 APK plays ordinary MKVs without a modified server. The
strict aggregate report is
`artifacts/firetv/20260909_stock_mkv_all_player_matrix.json`. On non-Pro Fire
TV `.25` against unmodified SageTV `.175`, `The Lion King` (H.264/AAC,
5,303,721 ms) passed 28/28 startup/control checks and `Scream_1`
(MPEG-2/AC-3, 6,656,404 ms) passed all 28 checks across legacy Exo, Media3,
IJK, and GSY Auto/Media3/legacy-Exo/System selections. One GSY/Media3 forward
seek on `Scream_1` recovered slowly in 15.352 seconds after 18.510 seconds of
Pull datasource read wait; no case crashed, exhausted the watchdog, or failed
to recover. MTK hardware AVC/MPEG-2 decoder identity is proven for the
Exo/Media3-backed cases. IJK's legacy health API proves control/timeline
recovery but does not report its decoder name; the existing AFTMM rule
intentionally rejects that device's broken MPEG-2 MediaCodec and falls back to
bundled FFmpeg. GSY System selection uses the datasource-compatible Media3
backend for Pull, which the report records rather than claiming native Android
System MediaPlayer playback.

Use `dev.cmd mcp-stock-mkv-matrix` (or the shell equivalent) to repeat the
gate. It reads `fixtures.mkv_searches` from the private TOML and uses stock
Sagex `Watch`, avoiding STV-dependent Search keyboard navigation. The common
matrix verifies the active post-launch configuration and retries one clean
apply/connect cycle when a stale prior setting is observed. These are host
workflow changes only; no Android player source or APK changed. The local gate
passes 503 project tests, 72 MCP/workflow tests, Core Gradle tests, structural
validation, manifest verification, and diff checks.

Stock SageTV supplied valid durations and seek targets. A separate `The Lion
King` entry on the modified test server was indexed as a 1 ms Matroska file
with no streams, so that server could only request a 1 ms seek. Treat that as
invalid server library metadata, not a player-side MKV failure. Prefer
stock-compatible client/protocol behavior; optional Sage.jar/MIM changes are
last-resort enhancements and must retain a safe stock fallback.

All generated video, caption, audio-codec, Kodi-codec, and authored-DVD
regression fixtures now live beneath
`/var/media/OpenSageTV_Vibe_Tests` / SMB share directory
`OpenSageTV_Vibe_Tests`. The obsolete authored-DVD backup was moved to the SMB
recycle directory; real recordings and commercial DVDs were not moved. The
post-move local gate passes 496 project tests, 72 MCP/workflow tests, and Core
Gradle tests.

## MCP connection reuse checkpoint (2026-09-09)

`dev_connect_server` now returns the existing healthy session when its address
and port already match and no renderer switch was requested. This prevents a
short follow-up MCP process from replacing the MiniClient sockets established
by the preceding process. Explicit renderer selection retains the established
reconnect path, and unavailable/older state snapshots safely fall through to
normal connect behavior. Unit tests and the physical MCP smoke test on non-Pro
Fire TV `.25` pass. This is MCP host tooling only; the v0.5.89 APK is unchanged.

## SageMC HOME and user-pause lifecycle gate (2026-09-08)

The lifecycle runner now accepts `--repeat 0` so HOME/background behavior can
be gated independently from the server's optional exact-watch replay loop. On
non-Pro `.25` against isolated `.232`, Media3/hardware Pull preserved the exact
MiniClient connection, hid/released and recreated the surface, resumed
advancing A/V after HOME only when playback had been active, preserved an
explicit user pause without emitting an automatic PLAY, and completed clean
teardown. The follow-on same-file replay remains a separate Core activation
gate rather than invalidating this lifecycle evidence.

## SageMC native DVD control completion (2026-09-08)

Native Media3/hardware DVD pause and resume pass on non-Pro Fire TV `.25` and
isolated server `.232`. Playback paused at 6,976 ms, resumed with both backend
play signals true, and advanced to 18,168 ms with renewed audio/video output.
The complete matrix, including STOP cleanup, is
`artifacts/firetv/sagemc-dvd-pause-resume-20260908-rerun.json`.

DVD Return was then verified visually on the commercial
`SCOOBY_DOO_AND_BATMAN` disc. The Sage command path opened the root menu, moved
RIGHT to Languages, selected its submenu, and returned to the root with
`dvd_return`. The four screenshots are `20260908-115225_screen.png` through
`20260908-115251_screen.png`; structured evidence is
`artifacts/firetv/sagemc-real-dvd-return-20260908-horizontal.json`. Earlier
transport-green attempts using DOWN were rejected after screenshot review
because this disc's root buttons are horizontal. Physical video evidence,
not merely an active player object, is the completion criterion.

SageMC may keep the stopped player object after HOME closes `StopPopup`.
`mcp_disc_test.py` now accepts that cleanup state only after the exact popup
was observed and dismissed and both `health_isPlaying` and
`health_playWhenReady` remain false. Full teardown is still accepted normally.
The combined playback-automation/DVD-protocol suite passes all 106 tests.
The complete post-change local gate also passes: 494 project/static tests, 70
MCP tests, Core Gradle tests, structural validation, and a clean 60-task APK
build. Because this slice changes only test automation and durable documents,
the rebuilt APK remains byte-identical to v0.5.88 with SHA-256
`f1791bf74cefc1c8ca98718c75912e2e7d4f0c12dc8de26ae7522f8cce247e3b`.

## SageMC retained-player interoperability (2026-09-08)

SageMC leaves a stopped MiniPlayer loaded behind `StopPopup`. The debug-only
exact-file helper now treats that player as stopped only when
`health_isPlaying=false` and `health_playWhenReady=false`, then sends the
neutral HOME command and waits for the popup to close before issuing the next
watch request. It reports `retainedStoppedPlayer` and `stopPopupDismissal`, and
fails closed if the old backend remains active or the popup remains open. The
focused 33-test MCP playback-automation suite and Python compilation pass.

The resulting physical run proved that the popup no longer consumes the next
request. It also exposed a separate server behavior: a redundant watch request
for the same stopped MediaFile returns success without restarting playback.
That correction belongs to the opt-in Vibe Core commissioning event and is
being physically gated on isolated server `.232`; stock `.175` remains
untouched.

## Post-release SageMC automation compatibility (2026-09-08)

The shared physical MCP root guard now accepts either stock `Main Menu` or SageMC's
historical `Dynamic Menu by nielm` as an idle automation root, while retaining
the connected/no-player/no-popup/no-text-input safety checks. Connection-order,
playback, Media3/player/tuning matrices, SMB A/B, embedded-preview, lifecycle,
and session runners all consume that one guard. The focused 38-test
playback-automation suite passes. Exact-file Media3 Pull and stock-compatible
Live TV both completed through SageMC on non-Pro Fire TV `.25` against isolated
server `.232`; the Live TV gate verified stable full-screen advancing audio and
video. Visual exact-file evidence is
`artifacts/firetv/20260908-085858_collect_screen.png` with matching trace and
system diagnostics. The first UI matrix also rendered Home, Guide, Recordings,
Schedule, Search, Video Library, Setup, Guide navigation, and recording details;
its screenshots use the `20260908-0914*` through `20260908-0917*` artifact names.
This is test-harness compatibility only; it does not alter the APK or playback
implementation.

## GitHub v0.5.88 release (2026-09-08)

Growing-file seek is corrected and physically gated on the commissioned
non-Pro AFTMM/API-25 Fire TV (`192.168.10.25:5555`, DEV001) with hardware
decoding. The completed-file Pull matrix at
`artifacts/firetv/20260907_all_player_pull_hardware_seek_matrix.json` ran seven
player/engine selections and all 42 absolute seek, forward, backward,
pause/resume, Comskip-right, and Comskip-left checks recovered. Stock `.175`
growing Live TV then passed server-owned rewind/forward on legacy ExoPlayer,
Media3, IJK, GSY Auto, GSY Media3, and GSY Legacy Exo. The guarded GSY System
selection is deliberately a compatibility fallback on this device: its native
probe records `GenericSource: Failed to init from data source` and
`android_system_player_error`, then Media3 hardware playback recovers.

IJK's native bridge must expose the current growing file size to FFmpeg; `-1`
causes the demuxer to cache the source as permanently unseekable. After a
successful seek, only a proven backward raw-clock discontinuity enables the
segment-relative clock offset. The final strict IJK run rewound 8,190 ms and
advanced 11,823 ms. The live test now promotes and verifies full-screen output
before seek measurement because its earlier timeline-only verdict passed while
HDMI still showed an STV menu. Corrected visual evidence is
`artifacts/firetv/20260908_ijk_live_seek_fullscreen.mp4` and its contact sheet.

Local release gates are green: 494 project/static tests, 70 MCP tests, Core
Gradle tests, validation, and a clean 60-task build. The APK is
`artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk`, SHA-256
`f1791bf74cefc1c8ca98718c75912e2e7d4f0c12dc8de26ae7522f8cce247e3b`.
Standalone CI repairs are included. GitHub Repository checks run `34194405901`
passed from a clean standalone checkout, including the repaired
`source-contracts` job. The deterministic source archive, development-signed
APK, checksums, and review bundle were independently inspected before the
`v0.5.88` GitHub release was tagged.

## GitHub v0.5.87 release (2026-09-07)

The final source tree, deterministic source archive, development-signed APK,
release manifest, and SHA-256 checksums are published from the `main` branch
under tag `v0.5.87`. This is a GitHub source/APK release; Amazon Appstore and
Google Play submission remain explicitly deferred in `TASKS.md`.

## Final playback-recovery regression (2026-09-07)

The remaining executable playback gates are complete on the commissioned
AFTMM/API-25 Fire TV (`DEV001`). The Fire TV Pro retains its independent
`DEV002` client ID and was not used by the final automation.

STOP followed by PLAY now exercises the actual SageTV retained-session
contract. Media3, legacy Exo, IJK, and guarded GSY/System pass Pull, Push, and
Fixed with hardware MPEG-2 output. Media3 and legacy Exo also pass SMB Direct;
their SMB/shadow sessions remain open across STOP and close only on terminal
FREE/DEINIT. IJK avoids its native 0.8.8 `stop()`/`prepareAsync()` crash path by
pausing and retaining the loaded decoder/source until DEINIT. IJK and
GSY/System perform resume-time Surface work on the Android UI thread and reject
stale callbacks. The physical runner clears old logcat before each session so
an earlier tombstone cannot fail a healthy rerun.

Media3 distinct-file switching passed over Pull and SMB Direct from the
generated caption fixture to `MeetthePress-65149351-0.ts`, retained
`OMX.MTK.VIDEO.DECODER.MPEG2`, and recorded zero fallback. Full replacement for
legacy Exo, IJK, and guarded GSY/System passed across Pull, Push, and Fixed.
All four player selections passed real Vibe-server tuner transitions limited
to 2.1 -> 5.1 -> 2.1. An unmodified stock server cannot acknowledge the
test-only Vibe channel event, so `mcp-live-test --current-channel-only` now
proves ordinary Live TV independently; Media3 and legacy Exo both passed with
advancing A/V. The normal startup timeout covered the bounded legacy SIZE
growth probe and HDD wake delay without an unbounded wait.

Caption regression passed without an Android enable gate. On stock `.175`,
Media3 and legacy Exo each passed SageTV/STV Off -> CC1 -> CC2 -> Off -> CC1
while event 225 continued and the local duplicate overlay stayed detached. On
Vibe `.232`, both passed continuous event-225 delivery from the generated A/53
fixture. Automated STV-state cycling is intentionally a stock-server evidence
path here because `.232` has no configured Web/Sagex endpoint; the underlying
Vibe callback gate itself passed.

Final local results: 489 project/static tests, 70 MCP/workflow tests, Core
Gradle tests, project validation, strict debug APK inspection, and a clean
60-task build all pass. APK:
`artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk`; SHA-256:
`8953aaef148c45fb035b5bad048daf062152f0ac0cb9deaebcaebcffd4e6a006`.

## Unified local commissioning environment (2026-09-07)

`config/firetv.toml` is now the only local commissioning/regression settings
file. It is ignored and excluded from packages. Its schema-2 form supports
multiple named/aliased Android clients and SageTV servers; each server keeps
its Web/Sagex details and flat `smb_*` fields in the same table, so changing
`active_server` cannot accidentally retain another server's SMB credentials or
mapping. A selected device may supply its own automated client ID. The old
root `device` format is still accepted.

All physical Python runners derive the default SageTV address and MiniClient
port from the selected server. The session, SMB A/B, fast-switch, and playback-
rate workflows also consume that server's SMB mapping/credentials. Sagex and
the stock Web Interface match credentials by the server address. Use
`SAGETV_TEST_DEVICE_ALIAS` or `SAGETV_TEST_SERVER_ALIAS` for a one-shell
selection override. `dev.cmd config-check --summary` prints only a redacted
view.

New contributors run `commission_test_environment.cmd` or
`./commission_test_environment.sh`. The first invocation creates the ignored
file from the sanitized example and exits for editing. Subsequent runs validate
the configuration and unified toolchain, create/reuse canonical regression
fixtures, and run test/validate/build; install/launch is opt-in. It never
publishes or overwrites remote SMB media automatically. Durable instructions
are `docs/TEST_ENVIRONMENT.md` and `docs/COMMISSIONING.md`. Acceptance passed
487 project tests, 70 MCP/workflow tests, Core Gradle tests, both shell syntax
checks, PowerShell parsing, and the real bounded
`commission_test_environment.cmd -SkipFixtures -SkipBuild` preflight on the
selected AFTMM/API-25 device. The completed TOML/commissioning item has been
removed from `TASKS.md`.

## STOP/restart and SageMC startup OSD diagnosis (2026-09-07)

SageTV STOP is not terminal: stock Core may later issue SEEK/PLAY against the
same loaded MiniPlayer without sending another OPENURL. Android previously
ended the playback session generation at STOP, so the later PLAY was rejected
as stale and produced a black stall. STOP now advances only the operation
token; FREE/DEINIT remains the terminal session boundary. Physical `.25`
testing against stock `.175` confirmed STOP, restart at zero, prepare from
idle, and a new first frame.

A second captured symptom was only cosmetic. On a never-watched recording,
SageMC 169 briefly displayed `1:22:00` at the end of a `1:02:00` airing before
correcting to zero. Twelve bounded startup trace replies from Android were all
exactly zero, with no EOS or end seek, while the server opened its OSD during
its initial zero-duration window. The stock SageTV7 STV did not reproduce it.
The opt-in **Wait for playback before first OSD** setting now coalesces the
first playback-OSD presentation until the active backend reports its first
presented video frame, with a hard five-second failure release. The guard is
enforced at both `flipBuffer()` and the actual
OpenGL/libGDX render boundary so an already-pending render request cannot expose
the stale frame. A successful MiniPlayer load also re-arms the guard when
SageMC keeps `MC MediaPlayer OSD` active across a file switch and therefore
does not emit a second menu-entry transition. It does not change the player
clock, seek target, protocol
reply, or STV state, and it has no per-frame player query after the bounded
startup window. The earlier clock-based release remained too early and menu-
entry-only arming missed consecutive files when SageMC retained the same OSD.
Physical `.25` testing against stock `.175` confirmed the load-triggered first-
frame release removes the visible end-to-zero jump. Evidence is also preserved
under `artifacts/firetv/20260907-234226_sage-mc-start-osd-first-frame_*`. MCP now provides
`dev_reset_media_watch_state`: its unconfirmed call is read-only and returns a
warning; `confirm=true` clears the complete Watched/resume row for one explicit
MediaFile through Sagex or Nielm's stock Web Interface. Evidence is
`artifacts/firetv/20260907-zero-start-watch.mp4`,
`artifacts/firetv/20260907-zero-start-watch-flash.png`, and
`artifacts/firetv/20260907-210419_player_playback-trace.jsonl`.

The associated backend-parity audit covers all selectable player paths.
Media3 and legacy ExoPlayer use their rendered-first-frame callbacks; IJK and
GSY/System use their native video-rendering-start callbacks; the GSY adapter
delegates the resulting state. The audit also corrected GSY forwarding for
explicit growing-file metadata and the complete extended player contract.
These code/host gates cover every backend, while the physical confirmation
above is specifically Media3/Pull/hardware on `.25`; the broader player/mode
matrix remains active in `TASKS.md`.

## SageMC Guide texture lifetime and channel-logo optimization (2026-09-07)

Stock server `.175` with SageMC exposed two related OpenGL defects on the
commissioned AFTMM/API-25 `.25`. Guide background textures were sometimes
black because `ImageCache.unloadImage()` nulled a shared holder on the protocol
thread before the GL thread executed its queued draw. Image disposal is now
owned by each renderer; OpenGL and libGDX enqueue it after earlier draws, and
OpenGL deletes its texture on the owning GL context.

SageMC's channel-logo files are commonly 1024x768 despite being displayed as
small Guide thumbnails. Full decode/upload consumed about 3 MiB per logo and
the 64 MiB logical cache repeatedly churned while paging. OpenGL now recognizes
only the stable `ChannelLogos` resource-cache path, samples those bitmaps to a
maximum 256-pixel edge, and retains the original logical dimensions for SageTV
source rectangles. Other artwork remains unchanged. The default logical image
cache is now 128 MiB; persisted user choices remain authoritative.

The exact physical build (`45b8898a8c2d47a341dde01c46853315c935f9da23f67d5e5e880b3a833012dd`)
is installed on `.25`. The initial and three-page SageMC Guide checks show the
correct blue background, sharp logos, and correct selection cells. No texture
render exception, fatal error, slow image decode, or slow texture upload was
logged. `dumpsys meminfo` reported GL memory near 39 MiB after the change,
versus about 115 MiB in the reproduced pre-optimization state. Evidence is
`artifacts/firetv/vibe-guide-opt-first.png`,
`artifacts/firetv/vibe-guide-opt-nav.png`, and
`artifacts/firetv/vibe-guide-opt-paged.png`.

## Persistent trace and stock full-file switching (2026-09-07)

A stock `.175` SageMC file switch was captured with SageTV's generic
`sage.PlaybackException`. The server log supplies the decisive cause: after
closing the prior MiniPlayer it waited for a new player socket, could not open a
connection to `.25`, requested a media reconnect, and timed out in
`MiniPlayer.load()`. Android previously waited for TCP EOF after replying to
DEINIT. It now breaks that read loop after the reply and immediately registers
a fresh media socket. A repeated switch later found that Exo media-session
release could itself block the media-command thread before that reply was
written: Media3 and legacy Exo now deactivate/release `MediaSessionCompat` on
Android's main thread, after the protocol path is free to answer DEINIT. The
same capture also proved why stock resume began at zero. Both Exo
implementations called `seekTo()` before attaching the new
MediaSource, so source attachment discarded the old-timeline seek. In addition,
stock SageTV provides no explicit active-file metadata and the compatibility
fallback marked every `.ts` as growing. Publishing unknown length for a
completed transport stream prevented the extractor from seeking even after the
source-attachment order was corrected. The queued position is now supplied to
`setMediaSource(source, position)` atomically, and all four Pull bridges verify
the legacy-only active-file guess with one bounded 750 ms SIZE-growth probe.
Explicit Vibe metadata remains authoritative.

The debug APK now writes a bounded app-private `playback-trace.jsonl` on a
low-priority worker. One 2 MiB current file and three rotations retain exact
events through player teardown; URI credentials/secrets are redacted. Every
record includes wall/monotonic timing, connection/reconnect and playback
generation, positions/duration/buffering, state, surface, and decoder counters.
Seek and OPENURL records include useful sanitized detail. Player-diag and MCP
diagnostic bundles export all rotations; MCP can query status, persistently
enable/disable new records without deleting evidence, or clear only the trace
files. Analysis sorts asynchronous records by event time while retaining an
out-of-order count and checking actual sequence loss.

Physical AFTMM/API-25 `.25` results against unmodified `.175` are complete:

- Meet the Press: requested/resumed at 1,274,317 ms; first physical read was
  byte 828,250,732 and hardware MPEG-2 A/V advanced.
- Direct switch to `VibeSeekTest-1080i-MPEG2-AC3-CC`: DEINIT received a reply,
  Android recycled the media socket, reconnect succeeded, and replacement
  OPENURL arrived 48 ms after DEINIT. It resumed at 543,079 ms and rendered its
  first frame 2.18 seconds after the server seek with no traced error.
- A stock live recording changed SIZE from 1,349,612 to 1,419,964 during the
  750 ms probe, remained open-ended, and continued hardware video/audio. The
  runner's channel-identity failure is expected because `.175` lacks the
  Vibe-only debug channel-event acknowledgement, not because playback failed.
- Six consecutive stock SageMC full switches on `.25` crossed Media3 hardware
  MPEG-2 sessions without an exception. Every DEINIT was followed immediately
  by `media_socket_recycle_after_deinit`, `.175` accepted every replacement
  socket/OPENURL, and the server recorded zero `PlaybackException` and zero
  `Did not find a player socket` timeout.

Evidence is in
`artifacts/firetv/20260907-154249_stock-resume-growth-fixed_*`,
`artifacts/firetv/20260907-154648_stock-file-switch-fixed_*`, and
`artifacts/firetv/20260907-154909_live-tv-failure_*`, and
`artifacts/firetv/20260907-185555_stock175-user-switch-test-followup_*`. The
installed APK SHA-256 for the repeated-switch fix is
`75e6c3119cb45ff3535f217be0b5f41ff251dfcc2ac1ec3e8f759c6f88383022`.

## Growing live-TV boundary and recovery fix (2026-09-07)

The Fire TV Pro live-TV rewind was reproduced and traced to Media3 1.11's
`StuckPlayingNotEndingDetector`. The active recording had been exposed as a
finite resource using its size at `OPEN`; after the decoder reached that stale
timeline end and remained there for 60 seconds, Media3 raised
`ERROR_CODE_TIMEOUT`. The generic retry then called `seekTo()` on the errored
player and `prepare()`, which restarted the recording at zero. No SageTV seek,
flush, client reconnect, datasource error, or server restart preceded it.

Both Media3 and legacy-Exo Pull adapters now return unknown length for a
growing recording and perform a bounded SIZE/growth check only at the actual
read edge. Pull/SMB error recovery reattaches the media source at the position
captured by the error callback; PUSH/FIXED position remains server-owned.
Media3 player retention is explicitly rejected while the current item is
growing so a program-boundary `OPENURL` receives a clean player/datasource.

The native IJK and GSY/System bridges received the equivalent growing-source
contract, with a bounded ten-second edge wait for tuner write gaps. Their
state-changing callbacks are now session-generation guarded. Physical testing
also found and fixed IJK's null media-clock dereference after release: a queued
DVD/SPU clock probe could otherwise crash the whole MiniClient while a live
program player was being replaced.

Physical AFTKRT/API-30 `.29` results against test server `.232`:

- Media3 Pull/hardware: six alternating 2.1/5.1 changes, PASS.
- Legacy Exo Pull/hardware: four alternating changes, PASS.
- IJK Pull: four alternating lifecycle changes, PASS with no crash. Its old
  runtime disables MPEG-2 MediaCodec on this model, so this is not recorded as
  an IJK hardware-decoder pass.
- GSY Auto (Media3), GSY legacy Exo, and GSY System: two alternating changes
  each, PASS; System was intentionally run last.

A separate Media3 Pull/hardware run against unmodified server `.175` remained
healthy for roughly two minutes, beyond the old 60-second failure point. Its
duration stayed unknown/dynamic, media position and reads advanced, the active
decoder was `OMX.MTK.VIDEO.DECODER.MPEG2`, and retry/read-error counts remained
zero. `.175` accepted but did not act on the Vibe debug channel event, so the
runner's final `channel_identity_not_confirmed` label is an automation/control
limitation, not a playback failure. Evidence is retained in
`artifacts/firetv/20260907-132101_pro-stock-live-growing-120s-post-fix_*`.
The installed APK SHA-256 is
`3ba4eb5f07be6481784d742fca306fbc62d1b2dd3aca582065e5f6d5f9454598`.
The same APK was finally installed on the normal AFTMM/API-25 `.25` target and
passed a two-change Media3 Pull/hardware 2.1/5.1 smoke; `config/firetv.toml` is
restored to `.25` for subsequent work.

## Fire OS service-lifecycle playback fix (2026-09-07)

The latest Fire TV Pro failure was reproduced before changing code. SageTV
displayed `sage.PlaybackException`, while Android logcat showed the actual
failure: `RejectedExecutionException` from
`Media3MediaPlayerImpl.resetLegacyCaptionsForDiscontinuity()` during
`BaseMediaPlayerImpl.load()`. `MiniclientService.onDestroy()` had terminated the
Application-owned `MiniClient.backgroundService`, even though Fire OS retained
the Application and foreground Activity. Every later `OPENURL` in that process
therefore failed before video could start. This was a client lifecycle defect,
not a `.175`/`.232` server failure and not a cost of sampling Playback Stats
CPU.

`MiniclientService` no longer shuts down the singleton client. Explicit
Application teardown retains ownership of `MiniClient.shutdown()`, and both
Media3 and legacy Exo defensively ignore executor rejection for optional legacy
caption flush/drain work during final teardown. Static lifecycle coverage
locks both rules.

The rebuilt APK passed full tests, validation, build, and APK inspection. It
then passed real hardware Media3 Pull playback on AFTKRT/API-30 `.29`; the user
confirmed playback and requested that all further testing return to non-Pro
`.25`. On AFTMM/API-25 `.25`, the same APK passed hardware MPEG-2 Pull against
Vibe server `.232` and an independently invoked stock-server `.175` Watch of
Meet the Press. The `.175` result reported advancing video/audio,
`OMX.MTK.VIDEO.DECODER.MPEG2`, a valid 1920x1080 surface, zero Pull read errors,
and no `RejectedExecutionException`, `PlaybackException`, or fatal exception.
Primary evidence is
`artifacts/firetv/20260907-002425_pro-video-exception-before-repro_logcat.txt`,
`artifacts/firetv/20260907-004801_pro-post-fix-second-open_screen.png`, and
`artifacts/firetv/20260907-005950_nonpro-stock-post-fix_logcat.txt`.

## Fire OS total-CPU compatibility (2026-09-06)

The Playback Stats CPU sampler no longer depends exclusively on aggregate
`/proc/stat`, which newer Fire OS application sandboxes may hide. It first uses
the original aggregate tick source and otherwise derives the same 0-100%
device-capacity value from `/proc/uptime` cumulative idle time and the available
CPU count. If Fire OS hides that file too, it uses cached read-only
`/sys/devices/system/cpu/cpu*/cpuidle/state*/time` counters. A source identifier
prevents a runtime source change from comparing different units. No permission,
shell helper, persistent process, or sampling outside the visible overlay was
added. The `/proc/uptime` fallback physically reported Vibe, Other, and Total
CPU on AFTMM/API-25. AFTKRT/API-30 denies both proc sources and physically
passed through the sysfs fallback; evidence is
`artifacts/firetv/vibe-pro-cpu-sysfs.png` (Vibe 1.4%, Other 44.5%, Total 45.9%
for the captured interval).

## Stock-server caption seek/placement correction (2026-09-06)

The standard legacy-extender callback path is now robust across MPEG-2 seeks.
The apparent STV placement problem was corrupted CEA-608 decoder state: MPEG-2
B-picture caption packets were reaching SageTV in decode order, and parallel
CEA-608/708 extractor callbacks could repeat non-adjacent packets. The bridge
now keeps a bounded exact duplicate history and sorts pending callbacks by PTS.
Both Media3 and legacy Exo extractor wrappers also discard incomplete caption
samples at every extractor seek, while the player-level reset clears read-ahead
and resets the stock SageTV decoder before new packets are delivered.

Physical AFTMM/API-25 `.25` validation against unmodified server `.175` passed
the STV Off/CC1/CC2/Off/CC1 sequence and FF/REW/FF2/REW2 for both hardware Pull
backends. The final Media3 evidence is
`artifacts/firetv/20260906-181311_caption-media3-pull-legacy-callback.png`; the
legacy Exo evidence is
`artifacts/firetv/20260906-181613_caption-exoplayer-pull-legacy-callback.png`.
Both show ordered roll-up captions in the normal lower-screen region after all
seeks, and their latest displayed caption is within two seconds of the burned
fixture PTS. Installed APK SHA-256:
`c44cacb8e9e8f6c784842e15a1b6d79cd1d819a417f37cfc387950a999a2b3ec`.

## Long-duration native DVD cadence closure (2026-09-06)

The reopened non-Pro Fire TV DVD cadence task is complete. The disc harness
previously capped every cadence observation at 180 seconds even when 600 was
requested; the bounded maximum is now 900 seconds and has regression coverage.
The exact installed APK played Aladdin from the same 8:00 motion scene for a
true 601.349-second observation. Media advanced 601.096 seconds (0.99958x),
hardware MPEG-2 rendered 28,837 frames with zero drops, only two isolated
skips, and no new long release gap or non-positive release interval. Audio had
zero drops and the final A/V sample delta was 131 ms. The final detailed overlay
reported approximately 7.9 seconds buffered, 7.7% Vibe CPU, and no transient
EOS. This does not reproduce a client buffer, CPU, decoder, or timestamp-drift
failure and therefore closes the current bounded task without another playback
tuning change.

Machine evidence:
`artifacts/firetv/dvd-aladdin-10m-true-long-cadence-20260906.json`.
Visual evidence:
`artifacts/firetv/20260906-dvd-aladdin-10m-sample-2m.png` and
`artifacts/firetv/20260906-183728_screen.png`. After the final clean build and
install, a separate 20-second startup/cadence/STOP smoke also passed in
`artifacts/firetv/dvd-aladdin-final-clean-apk-smoke-20260906.json`.

## Playback Stats and reopened DVD cadence investigation (2026-09-06)

The long-press Active Player Adjustments menu now owns a universal, mode-aware
Playback Stats panel. Compact/detailed persistent modes, a bounded 30-second
mode, hide, and redacted export are available. Sampling runs once per second
only while the panel is attached and is cancelled when the playback Activity
pauses or is destroyed. Common decoder, video/audio, timing, frame, display,
buffer, synchronization, and recovery evidence is joined only by the current
Pull, SMB Direct, Push/Fixed, caption, or DVD section. The three live bars show
measured media-byte activity, mode-scaled buffered playback time, and fixed-scale
device/Vibe CPU. Each value is shown only above its bar; the duplicate text rows
and invariant estimated link-capacity bar were removed. Consumer-only fields
from the visual reference are deliberately excluded. The long-press navigation
overlay also has a direct bar-chart toggle
beside the gear and CC controls. It is white when disabled and green when
active. MCP's `dev_set_active_player_overlay` supports `toggle`, `off`,
`compact`, `detailed`, and `detailed_30s`, while retaining the prior Boolean
contract. The CPU bar separates Vibe from the remainder of total device usage
on a 0-100% scale and does not poll either value after the panel closes.

The final simplified three-bar layout is physically verified in
`artifacts/firetv/20260906-154036_playback-stats-cpu-color-font-final-20260906_screen.png`.
It shows media activity, buffer health, and a 0-100% stacked CPU bar only. All
detail text uses the graph-label font and size; the sample color-matches blue
`Vibe 20.3%`, orange `Other 17.6%`, and neutral `Total 37.9%` to the bar. The
exact installed APK SHA-256 is
`8141957fb0a4d318ba9158616d390244c3d990431da34b2e6efbc71246841cda`.

The same build fixes a real Media3 release crash in which display-refresh
inspection called `getVideoFormat()` from SageTV's GFX worker. Display-mode
application is now marshaled to Android's main thread and the OFF/restore path
never queries player metadata. A clean hardware Media3 DVD start, seek, play,
and stop cycle passes in
`artifacts/firetv/dvd-aladdin-refresh-thread-fix-smoke-20260906.json`.

The actual submenu was exercised on AFTMM/API-25 `.25`. Hardware Media3 Pull
and native Aladdin DVD views were both readable over active video and contained
only their applicable transport rows. HOME removed the overlay and sampler.
Evidence is retained in
`artifacts/firetv/20260906-134458_screenshot_screen.png` and
`artifacts/firetv/20260906-135003_screenshot_screen.png`. The DVD snapshot at
about 8:00 showed a six-second decoder buffer, no renderer drops, five
cumulative release gaps, active timestamp correction, and a sampled +252 ms
A/V delta. Long-duration DVD cadence degradation reported after extended
playback is therefore reopened as the active investigation; short healthy
windows do not close it.

The direct icon and corrected grid were physically verified in
`artifacts/firetv/20260906-playback-stats-nav-icon-fixed.png`; its active green
state is in `artifacts/firetv/20260906-playback-stats-nav-icon-green.png`.
MCP toggled the snapshot state false and then true. Earlier CPU sampling
evidence remains in `artifacts/firetv/20260906-playback-stats-cpu.png`; the
final evidence and installed hash are recorded above.

## GitHub publication preparation (2026-09-05)

The public repository is a true fork of
`https://github.com/OpenSageTV/sagetv-miniclient` at
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client`. Local
`origin` points to the Vibe fork; `upstream` fetches from OpenSageTV and has a
disabled push URL. The Vibe branch is `main`; upstream history remains
available on the fork's `master` branch.

The local prepublication gate passed 451 repository/static tests, 63 MCP
tests, Core JUnit, every `dev.cmd validate` contract, a clean 60-task debug APK
build, and the strict debug APK inspector. The inspected package is
`opensagetv.vibe.miniclient.debug`; permissions are limited to network state,
Wi-Fi state, Internet, wake lock, and Android's generated dynamic-receiver
permission. The APK is development-signed as documented. Final exact-commit
hashes and GitHub release assets are recorded after the clean-tree rerun.

Public source no longer contains a preselected SMB configuration share or SMB
test username/password. Commissioning values remain in ignored
`config/firetv.toml` and must be supplied explicitly. The protected original
`C:\TMP_SAGETV_DOCKER\SageTV-MiniClient-Dev` remains untouched.

Release `v0.5.85` is published at
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.85`.
The exact APK SHA-256 is
`b143d8201496bd12039ebf5a452e591dfc2ec3684f5464e35dc93014abcbcd65`;
the verified source archive SHA-256 is
`311a88e1089ad18cc096f3317cdfa540c4ae307a0007009baf2e1d5fc546b0be`.

## Current physical DVD baseline (2026-09-05)

Android now has a physically commissioned implementation of the standard
legacy-extender caption producer. Media3/legacy Exo/GSY extractor delegates return raw CEA
packets through event 225 and advertise `GFX_SUBTITLES`; IJK advertises false.
This is intended to let an unmodified SageTV STV own and render CC exactly as
the hardware-extender path did, while retaining `VIDEO_CC_STATE` as an optional
compatibility extension. The exact APK passed `.25`/`.175` negotiation, raw
decoded and wire counters, the STV Off/CC1/CC2/Off/CC1 cycle, post-seek
recovery, and no-duplicate-overlay checks. Media3 passed the complete stock-STV
cycle; legacy Exo produced 1,017 events/35,096 bytes and recovered after FF in
1.02 seconds after its attached-overlay race was fixed. IJK stayed playable,
advertised no callback producer, and emitted zero event-225 bytes.

Physical remote long press is restored on `.25`. Key ownership is now at the
MiniClient Activity above OpenGL/GDX/player-surface focus, while the original
configured key maps remain authoritative. DVD menus consume only short D-pad
and Select presses. Both Android-TV navigation layouts contain Active Player
Adjustments, and the dialog treats that optional view defensively. The exact
installed APK passed an injected Fire OS hold and visibly opened the complete
navigation/player-controls overlay without a crash. It remained visible in
captures at 100 ms and one second after injection while Meet the Press played;
evidence is `artifacts/firetv/longpress-100ms.png` and
`artifacts/firetv/longpress-1000ms.png`.

The server-owned DVD seek and on-the-fly Active Player Adjustments gates pass
on the commissioned non-Pro Fire TV (`.25`). The submenu applies safe controls
immediately and uses a bounded server-owned same-position reload only for
decoder construction changes. Subtitle `+500 ms`, 29.970-to-59.94 Hz matching,
active AC-3 track selection, hardware decoder retention, and diagnostics export
were physically verified. Aladdin's HDMI output at 8:00 matches the raw VOB's
authored 3:2 film cadence and has no sustained freeze or decoder drops, so no
destructive timestamp change was made. The latest deterministic authored-DVD
matrix passes menus, title, chapters, audio, subtitles, pause/resume, STOP, and
teardown; the missing-disc safe-failure gate also passes. The stock-JAR caption
audit is complete: the original protocol does not publish the STV checkbox
state, while proprietary extenders returned decoder-extracted caption packets
through callback 225. Android now implements that callback for extractor-backed
players and retains explicit Off/CC1/CC2 controls only for IJK or failed
negotiation. The optional Vibe `VIDEO_CC_STATE` extension remains accepted.
All bounded legacy-extender semantics currently implementable and physically
testable in the commissioned environment are complete. The active local work
is the final manifest/test/validation/build refresh, followed by GitHub
remote/tag approval and, only after explicit authorization, publishing the
already prepared source and APK artifacts.

Capability-safe Media3 file switching is complete. Retained completed-file
Pull and SMB Direct replacements preserve the player/Surface and now have an
eight-second first-frame watchdog in addition to the existing asynchronous
error fallback. Either path makes exactly one normal full-player fallback;
success, release, or a superseding OPENURL cancels it. Exact APK
`c5658c7d0036107e0b30b4eaecdf6514bcec2cdc21d02e7c7b59ebf1fc739605`
passed same-fixture and distinct-file switches over both transports on `.25`
with `OMX.MTK.VIDEO.DECODER.MPEG2`, one attempt/one success, and zero fallback.
Core JUnit and the clean 60-task Android build also pass. The stale active
entries for this work, already-commissioned Home/background recovery, and
already-commissioned paused frame step were removed from `TASKS.md`.

Audio capability reporting is now device-derived and truthful. MediaCodec
decoder support, the currently connected encoded AudioSink formats, and the
combined ability to play a codec are separate MCP fields; the app never calls
a compressed stream "passthrough" merely because of its MIME type. SageTV
`AUDIO_OUTPUTS`/`AUDIO_OUTPUT` remain empty and
`audioPassthroughAdvertised=false`. The generated AC3/EAC3/DTS matrix passes on
AFTMM/API-25 `.25`: AC3 and EAC3 play directly over Pull, while DTS has no
decoder or encoded-sink support and is excluded from direct Pull. Evidence is
`artifacts/firetv/audio-capability-media3-pull.json`; the exact installed APK
is SHA-256
`04f6d5106d3043c93163cd1af390027e3cfd8d072190aa869a17f07f26d90c1d`.

MPEG-2 interlace reporting is also complete without inventing a desktop-style
Android deinterlacer. A bounded H.262 header scanner observes sequence and
picture-coding extensions in ordinary Media3/legacy-Exo playback and the native
DVD extractor. It reports the bitstream classification and frame/field counts;
`codecDeinterlaceControl=not_exposed_by_android` remains explicit. Media3 and
legacy Exo hardware Pull both physically report
`interlaced_sequence_interlaced_frames` for the canonical 1080i fixture.
Native DVD passed with the same classification, 172 interlaced frame-picture
headers, hardware MTK MPEG-2, real-time A/V progress, and zero drops. Evidence
is `artifacts/firetv/codec-capability-media3-pull.json`,
`codec-capability-exoplayer-pull.json`, and `dvd-interlace-observation.json`.
The exact installed APK is SHA-256
`7bcf83da0967785cf283eb60c0a042fa7f27084cce6caaa7b597a5a628cd8a58`.

Bounded GFX image-allocation recovery is complete. A failed bitmap/texture
allocation may evict exactly one ordinary LRU UI image and retry once; surfaces
are never candidates, and a missing candidate or second `OutOfMemoryError` is
re-thrown. Invalid/overflowing image dimensions fail before allocation, and a
closed or superseded connection is never sent an unload event. MCP exposes
attempt/eviction/success/failure counters. Core, protocol, and MCP tests pass.
The exact APK `58d8affb6d41ce6e1c1166cf9daa9af8c156f16bc0e2473ad54c77bc4af37efd`
passed hardware Media3 Pull on `.25`; an in-session snapshot recorded 2,577
rendered MPEG-2 frames, zero drops, zero recovery counters, and no attached
Android caption overlay. Evidence is
`artifacts/firetv/20260905-222630_caption-media3-pull-legacy-callback.png`.

That extension now includes live Media3/legacy-Exo text-caption safe area,
size, and style. The latest physical `.25` Pull session applied a 25% bottom
safe area without reloading the decoder; MCP confirmed the new value, active
hardware MPEG-2, advancing playback, and zero dropped frames. DVD bitmap SPUs
remain intentionally unaffected. It also includes a compact, opt-in 30-second
process overlay with deterministic MCP show/hide control. Physical `.25`
evidence proves the overlay reports live player/buffer/subtitle state without
reloading the decoder, then removes itself while playback remains active. A
0-1500 ms HDMI settle setting is also live and bounded; it delays only a
server-owned DVD decoder replacement after a real display-mode change. No
generic local pause/resume is used. PCM gain/downmix/audio delay is not exposed
until an output-sink implementation can prove correct clock accounting;
encoded AC-3 and unsupported outputs continue to fail closed.

## Standard takeover

Read `AGENTS.md`, `README.md`, `TASKS.md`, `WORKFLOW.md`, and playback diagnostics
before work. Use the root dev/update/package commands from any CWD. Update ZIPs
live in `artifacts/downloads`; install remains guarded to the Dev package.

This file records only the current takeover state. Historical release details
belong in `CHANGELOG.md`; open work belongs in `TASKS.md`.

## Current state

- Checkout version: `0.5.85`.
- Machine metadata: `release.properties` with `REQUIRES_BUILD=true`.
- Incremental update preflight/resume gate: PASS (19/19), including forced
  validate failure, first-incomplete-step resumption, strict Windows
  CRLF-equivalence acceptance, and real-content-drift rejection.
- Independent v0.5.75-to-v0.5.85 update workflow: PASS. The runner applied the
  changed-files package in a detached worktree, canonicalized 483 proven text
  equivalents, removed 355 obsolete paths, passed 438 project and 57 MCP tests,
  passed Core/validation, completed a clean 60-task APK build, and
  installed/launched the Dev package on `.25`. The unified container mounted
  the independent checkout itself; it did not test the canonical tree.
- Final canonical gate: 445 project tests, 57 MCP tests, and Core Gradle tests
  pass, including Git-less GitHub-bundle provenance coverage.
- Active source: `source/dev`.
- Frozen comparison source: `source/existing`.
- Protected external reference: `../../SageTV-MiniClient-Dev` (read-only).
- Normal development image/container:
  `opensagetv-vibe-build-env:u26-j11` / `opensagetv-vibe-dev`.
- Canonical artwork is owned by sibling `opensagetv-vibe-logo`; unified Android
  gates regenerate and SHA-verify its 29 launcher/banner/in-app/store resources before
  Gradle runs. `config/logo-assets.sha256` records installed provenance.
- Android production/development IDs are `opensagetv.vibe.miniclient` and
  `opensagetv.vibe.miniclient.debug`; both labels are `OpenSageTV Vibe`.
- Every active Java package and component is under the independent
  `opensagetv.vibe.miniclient.*` root. The established launcher class endings
  remain `android.phone.ServersActivity` and `android.tv.MainActivity`; only
  frozen `source/existing` retains `sagex.miniclient.*` for comparison.
- Fire TV builds now produce one client APK with both standard `LAUNCHER` and
  TV `LEANBACK_LAUNCHER` activities. The unsuccessful API-29 companion launcher
  and its install/launch automation were removed. Launcher resource precedence
  matches the working APK: the standard launcher inherits the application
  square/adaptive icon, while the Leanback activity explicitly declares the
  320x180 banner as icon, banner, and logo. No TV-module mipmap overrides
  remain. The protected JVL package was not modified.
- Fire OS launcher-cache recovery and package identity are characterized.
  Normal launcher-artwork gates use a clean Dev-package install and identify
  the package/component rather than inferring ownership from similar artwork.
  On the commissioned AFTMM/API-25 Fire OS launcher, a newly sideloaded package
  is intentionally presented with its square application icon even though its
  Leanback activity exposes the correct 320x180 banner. Rapid update installs
  can show a temporary blank tile; a version-code change followed by library
  refresh/restart restored the visible Vibe artwork. Evidence is
  `artifacts/firetv/appsgrid25-final-vibe-focus.png`; selecting the card launched
  `opensagetv.vibe.miniclient.android.tv.MainActivity`.
- The logo pipeline also emits validated Amazon 114x114/512x512 tablet icons,
  an opaque 1280x720 Fire TV app icon with critical artwork inside the 882x448
  safe area, and a title-free opaque 1920x1080 Fire TV background under the
  Android TV module's `store-assets/`. Three to ten reviewed 1920x1080 application
  screenshots and catalog publication remain release tasks.
- Default scripted test identity: `44:45:56:30:30:31` (`DEV001`).
- Default player remains Legacy ExoPlayer.
- Native packaging is now release-consistent: only paired ARMv7/ARM64 ABIs are
  shipped, each with the same five required libraries. The rebuilt pinned
  ExoPlayer FFmpeg AAR is 16 KB ELF-aligned (SHA-256
  `e9e34c833298c1177247b3f7cfef8e8be45035ff4f8076d667b8f5c9dc9c4b12`).
  The clean 60-task APK (`5538372d6431f6419a8d152d97072c7b6142cfb80b8bb569631be56cda07cdb4`)
  passed strict ABI/alignment inspection, APK ZIP alignment, in-place install,
  and hardware MPEG-2/AC3 Pull playback on `.25`.
- Connection/UI configuration and bounded keyboard telemetry have explicit
  owners. `ConnectionCapabilityProfile` owns negotiated capability/profile
  configuration, `UiSessionConfiguration` owns the immutable background
  policy loaded for one Activity session, and `UiKeyboardDebugState` owns only
  the debug IME observation/control state. Full host gates pass. Physical
  `.25` evidence is
  `artifacts/firetv/connection-ordering-20260905-082026.json`; the companion
  retained HOME/return lifecycle gate also passed exact connection identity,
  Surface recreation, auto-resume, manual-pause preservation, and teardown.
- Android event ownership is explicit and Otto-free. `VibeEventBus` dispatches
  the typed `VibeEventListener` callbacks synchronously on the posting thread
  in registration order; `docs/EVENT_OWNERSHIP.md` is the publisher/subscriber
  inventory. The full host gate and physical `.25` connection/lifecycle gates
  pass, and direct physical probes also proved the navigation overlay,
  video-info display/refresh, and native keyboard event. The commissioned APK
  SHA-256 is
  `96074525bc2136f107e1138881dc6008c9279665f98e820090ee0898344db16a`.
- Media3 supports conservative fast replacement of compatible completed Pull
  and SMB Direct files while retaining the player and Surface. Eligibility is
  fail-closed; Push, Fixed, live/growing, circular, HTTP/external-link, DVD,
  uninitialized, and legacy-unknown loads use the established full-load path.
  Setup or asynchronous failure makes exactly one full-load attempt. The
  `mcp-fast-switch-test` physically passed on `.25` over both SageTV Pull and
  SMB Direct with hardware MPEG-2 decode, one attempt/one rendered-first-frame
  success, and zero fallbacks. Missing-file injection proved fallback without
  process death. The exact final clean APK SHA-256 is
  `15a28b08c7c3febe6e08b7ff6519ca375110c30bb3e7282ed10b0ee67b7d7ae8`;
  it repeated both physical gates after an in-place install. Manual evidence is retained under
  `artifacts/firetv/fast-switch-*-20260905.txt`.
- Negotiated command 30 now provides modest native forward playback rates
  (0.5x-2x) and seek-based forward/reverse scan (4x-256x) for completed Pull
  and SMB Direct media on Media3, legacy ExoPlayer, and their GSY delegates.
  Unsupported transports and GSY/System do not advertise the feature. A
  three-second scan interval is required to let the physical SMB path recover
  a rendered frame between seeks. Hardware-only `.25` gates passed both Exo
  engines over Pull and SMB, both GSY delegates over Pull, pause/resume, STOP
  reset, and server-driven Smooth FF/REW. The commissioned test server runs
  `Sage.jar` SHA-256
  `32563ce0ae9e174b1212a9c7fa0bb4255483bc81410276ab78e3e2d311683c48`.
  The final host gate passed 423 project/static tests, 56 MCP tests, Core JUnit,
  validation, and a clean 60-task build. The exact clean APK SHA-256 is
  `8500c503b79869bfaafa3e012916972e95c4b75fb2d6a5b0ed050898b1d10a3f`.
  That exact artifact was installed in place on `.25` and repeated both the
  server-negotiated Media3 Pull gate and the Media3 SMB Direct gate using
  `OMX.MTK.VIDEO.DECODER.MPEG2`.
- GSY/System is physically resolved rather than left as an ambiguous backend.
  Its commissioning-only real `MediaDataSource` probe fails on AFTMM/API 25
  with Android MediaPlayer error `1/-2147483648`; the new bounded fail-safe
  falls back once to Media3 and restores hardware MPEG-2 A/V in the same
  SageTV session. Normal users never enter the failing System path because the
  probe gate defaults false. Debug state records `gsyResolvedEngine`,
  `gsySystemFallbackCount`, and `gsySystemFallbackReason`.
- Preferred audio/subtitle language and CEA-608/708 service selection are
  implemented under Audio and Caption Track Settings. SageTV/STV remains the
  caption Off/On authority; there is deliberately no Android enable gate.
- Unmodified servers that do not publish `VIDEO_CC_STATE` are supported by the
  long-press CC submenu (`STV`, `Off`, `CC1`, `CC2`). A renderer-state defect
  that required restarting video after Off-to-CC1 was fixed in both Media3 and
  legacy ExoPlayer. The deterministic caption fixture passed same-session
  Off-to-On without restart on both engines; visual evidence is
  `artifacts/firetv/20260904-235030_caption-media3-dynamic-visible.png` and
  `artifacts/firetv/20260904-235234_caption-exoplayer-dynamic-visible.png`.
  The follow-up eight-second Media3 continuity gate produced 183 more non-empty
  cues with a longest progress gap of 1.691 seconds; its final screenshot is
  `artifacts/firetv/20260904-235525_caption-media3-dynamic-visible.png`.
- The bounded MediaCodec capability profile is complete. It inventories the
  platform decoders on request and both primary selectors use its hardware/
  software classification; it deliberately does not install a continuous
  analytics listener or infer deinterlace quality Android does not report.
- The generated Kodi-derived codec matrix is complete on the normal
  AFTMM/API-25 device with Media3 Pull and hardware video decoding. Eleven
  positive formats/profile/bitstream cases pass, including a real H.264
  resolution transition; malformed H.264 startup is contained. The separate
  MPEG-4 Part 2 fault plan uses eight reordered MP4 packets with independent
  PTS/DTS and passes controlled PTS-unset/DTS-fallback injection without a
  production rewrite. Canonical evidence is
  `artifacts/firetv/kodi-codec-matrix-media3-pull-aftmm.json`.
- The GitHub publication dependency/license gate is complete.
  `third_party/RUNTIME_DEPENDENCIES.csv` fail-closed maps all 122 selected
  runtime coordinates to packaged notices and full license texts;
  `third_party/source-offers/README.md` records exact native AAR hashes and
  pinned LGPL rebuild sources. The project-owned Apache-2.0 bounded circular
  buffer replaces the former Ostermiller GPL runtime dependency. The inspected
  review bundle contains hash-verified source, APK, manifests, notices,
  licenses, and source-offer material. Its clean APK also passed physical
  Media3 hardware Push playback on the normal AFTMM/API-25 device.
- Commissioned SageTV test target: Unraid container
  `sagetv-vibe-server-u26-gpu-j11` at `192.168.10.232`.
- The Android 11/API-30 Amazon AFTKRT at `192.168.10.29` is now a commissioned
  compatibility device for launcher, Settings, discovery, and connection
  lifecycle checks. Fire OS's unattached-decor `WindowInsetsController` crash,
  early error-view null dereference, restart-after-shutdown discovery rejection,
  MCP focused-window omission, and background-receiver Activity-launch failure
  are fixed. Three Settings reopen cycles, discovered `.175`/`.232` servers,
  GDX connection, MCP smoke, and OpenGL hardware Media3 Pull with pause/play,
  HOME reconnect and teardown pass without a fatal exception. Evidence includes
  `artifacts/firetv/api30-mcp-smoke.log` and
  `artifacts/firetv/connection-ordering-20260904-120843.json`. Its retained-
  session matrix passed on incremental APK SHA-256
  `3295d35f427ae895ee66ea6474b4061375f3270fce5b5192a693508659094a12`;
  Pro testing is paused at the user's request after that PASS.
  The device is currently ADB-authorized. Its independent Vibe launcher/icon
  and STV-controlled CEA-608 caption fixes are owner-verified working. Native
  hardware DVD cadence is also commissioned: the exact clean `0.5.85` APK held
  1.0217x real time on `.29` and 1.0131x on `.25`, with zero drops, invalid
  release intervals, or long gaps, and every pause/play, 2x FF/RW, and chapter
  recovery passed. The authored DVD
  menu/audio/subtitle/chapter regression also passed.
- Normal ongoing physical automation should return to the configured
  `192.168.10.25` Fire TV after the API-30 compatibility gate; use `.29` only
  when explicitly repeating the newer-platform check.
- The current logo-integrated debug client and compatibility launcher are
  installed on that normal AFTMM/API-25 device. Saved development preferences
  were restored after the client identity migration. The client reports
  `1.14.0-DEV-DEBUG`/code `2101102`, resumes `MainActivity`, and has SHA-256
  `269b4425218d055ac26c175a521de7b347ddb18e2d429c325917b090b4d4cf92`.
  The API-29 launcher SHA-256 is
  `c0f10f80b7dd09a7cafeb0daa03572fd1870c7dce9b34999c7544c1b73d9db36`.
- The deterministic regular-video fixture now carries forward the authored-DVD
  synchronization contract: burned PTS/frame, synchronized visual/audio pulses,
  dual AC-3, CEA-608/708, and a Comskip sidecar. Unified image contract
  `u26-j11-release-v7` was rebuilt as image `27acc132879e...`; its single
  reusable container generated and probed a real 1920x1080i sample and passed
  the 14 focused regular/authored fixture tests.
- Home/background recovery is opt-in and implemented behind a dedicated Options
  submenu. Media3 hardware Pull passes same-session recovery on both the API-25
  and API-30 Fire TVs with auto-resume on and off, preserves a user pause,
  honors the configured timeout, and tears down explicitly. Automatic PLAY is
  emitted only for media the app itself paused while entering background.
  Legacy Exo now binds its `SurfaceView` instead of retaining a released raw
  `Surface`; two hardware Pull HOME/return cycles pass with advancing A/V,
  user-pause preservation, and teardown on API 25. API-30 Fire OS's early
  MediaSession pause is coalesced into application ownership, and resume repaint
  is routed through the ordered event worker; the former main-thread socket
  write caused `NetworkOnMainThreadException`, duplicate reconnects, and stale
  playback teardown. The tested incremental API-25/API-30 APK SHA-256 is
  `3295d35f427ae895ee66ea6474b4061375f3270fce5b5192a693508659094a12`.
  The final clean-build artifact SHA-256 is
  `c18cfdee04868b250b99381f85d45c00b6c5e2bd8fbf1e499fbe5fa27a78351b`
  and repeated the exact-session auto-resume, user-pause, replay, and teardown
  gate on API 25.
- Remote DVD playback is commissioned and its active implementation task is
  closed. Native remains the stable default; Hybrid/MIM remains opt-in and
  experimental, but its authored-fixture title/control matrix and explicit MIM
  main-feature policy physically pass on the commissioned Fire TV. Blu-ray/
  BDMV remains separately `SKIPPED` until a valid physical fixture exists.
  Updated Core selects `MiniDVDPlayer` when this client advertises
  `DVD_REMOTE_NAV`; Android implements commands 32-37 and a DVD-only Media3
  MPEG-PS/AC-3 path. DISC policy/menu/preview/fallback preferences are queried
  by Core for each client. Unsupported Hybrid/MIM fails closed when requested,
  or falls back to Native without corrupting the session when allowed.
- Native MPEG-2 DVD on the API-30 AFTKRT now carries a Kodi-derived guarded
  missing-PTS repair. At the identical 61.795-second input point the repair
  reduced recurring frame-release gaps from 31 to 10 while both sides used
  `OMX.MTK.VIDEO.DECODER.MPEG2`, consumed 47,417,344 Push bytes, and reported
  no player error. `Auto` targets MediaTek OMX/Codec2 and NVIDIA OMX, and the
  Disc submenu plus MCP expose `On`/`Off` for A/B testing. The final clean
  workflow APK installed on `.29` has SHA-256
  `d8bec749d33eb5331a5d046ac5638297a52266780808400d828b714efa6c1bb0`
  and passed exact-path native hardware start, pause/play recovery, advancing
  A/V, and STOP teardown.
  Evidence: `artifacts/firetv/dvd-firetv-pro-29-repair-off-state.json`,
  `artifacts/firetv/dvd-firetv-pro-29-repair-on-state.json`, and
  `artifacts/firetv/dvd-firetv-pro-29-native-kodi-rules-controls.json`, plus
  `artifacts/firetv/dvd-firetv-pro-29-kodi-rules-clean-build.json` for the
  exact clean artifact.
- A true mid-session MIM failure after Hybrid negotiation now falls back to
  native hardware MPEG-2 with advancing A/V and no player error. Android
  exposes `discMimRuntimeFallback=true`, retains Core's fallback URL, and shows
  a bounded user message. Evidence is
  `artifacts/test-results/vibe-authored-dvd-mim-runtime-native-fallback.json`.
  The explicit `mim_main_feature` authored-disc policy gate also passes with
  hardware startup, command recovery, and STOP/teardown on `mcp-disc-test` with
  evidence `artifacts/test-results/vibe-authored-dvd-mim-main-feature-policy-gate-attempt2.json`.
  Test-server `ffmpeg` was restored executable at SHA-256
  `8d031f6cad22867ca2b912a73cdb8695dcbc11247d10c2461462594a26b83e9c`.
- The deterministic authored fixture exposes synchronization in the picture:
  every English/Spanish normal SPU cue prints its cue number, authored PTS,
  nearest video frame, and chapter against a video-burned live PTS/frame clock.
  DVD `SetSTN` values 64/65 enable English/Spanish and 63 disables normal
  subtitles. Strict physical English and Spanish selector tests pass after the
  fixture correction; `vibe-authored-dvd-v6-spanish-subtitle-title-retry.json`
  and screenshot `20260901-134510_screen.png` are the current sync evidence.
- The same installed APK passes the preserved pre-DISC Core binary with normal
  Pull, SMB Direct, Fixed/MIM, Native DISC, and safe Hybrid-to-Native behavior.
  Old Core cannot query the new per-client DISC policy, so the client now
  exposes `discOldServerNativeFallback` plus a concise compatibility reason
  instead of claiming Hybrid. Updated Core explicit Hybrid/no-fallback fails
  closed. The updated test JAR was restored afterward at SHA-256
  `89d77793830d954ef187318246030910462075e0917abfdefa3735c40319994b`.
- Backward-compatible optional-extension fallback is now a permanent
  `AGENTS.md` playback rule rather than an open one-time task. The physical
  preserved/current Core, missing/old/current MIM, FFmpeg runtime failure,
  explicit fail-closed, Native/Pull recovery, and SMB Auto-fallback evidence
  must be rerun whenever their boundaries change.
- Missing MIM and preserved MIM 0.4.5 fault injection both prevent explicit
  Fixed startup without crashing SageTV; ordinary Pull remains recoverable.
  The exact current MIM 0.4.7 `ffmpeg`, `ffmpeg_MIM`, `ffmpeg.real`, and INI were
  restored from the test backup and all four SHA-256 checks pass.
- Hardware embedded preview now physically passes Legacy Exo Push, Pull, SMB
  Direct, Fixed/MIM and Media3 Native DISC. The evidence includes a screenshot
  after a bounded settle and a second A/V state verification, so a green/blank
  Surface cannot produce a false pass. See the `embedded-preview-*` JSON and
  PNG files under `artifacts/firetv`.
- The Apache-2.0 DVD Presentation Engine v2.1 handoff archive was verified
  source-by-source, test-by-test, and document-by-document. Vibe imports only
  its bounded platform-neutral SPU/audio presentation primitives and wraps
  them with the existing Android overlay/STV policy. The archive's complete
  bridge, input/drain gate, timestamp rewriter, Surface view, and forced-only
  stream selection were intentionally rejected because they overlap the
  physically proven Vibe path or conflict with STV subtitle-off authority.
  Imported self-tests, a 5,000-fragment malformed-SPU stress test, focused
  static tests, Core JUnit, and debug APK compilation pass.
  `docs/DISC_PLAYBACK_DISCOVERY.md` contains the final component-level merge
  table, including every intentionally retained Vibe equivalent and the reason
  it is safer than replacing the physically proven path.
- Physical hardware decoding now crosses the 159,199,232-byte Scooby title
  boundary without the former audio-stop/READY-BUFFERING deadlock, then renders
  the authored root menu and buttons with continuing audio. Generation-scoped
  Push EOF is driven by MiniDVDPlayer's actual `0x100` drain protocol, and only
  complete one-picture menu sequences receive bounded 500 ms readiness samples.
  The menu remained READY/playing beyond 130 seconds and drained normally with
  no player error. Menu-less ALADDIN also has earlier bounded A/V evidence, but
  these observations do not commission the complete DVD workflow. Re-run every
  remaining gate listed in `TASKS.md`. Do not replace the DVD extractor
  with stock Media3 `PsExtractor`: it merges DVD private AC-3 substreams and
  crashes this Fire TV's Dolby decoder. No BDMV fixture was found, so physical
  Blu-ray commissioning remains SKIPPED rather than reported as passing.
- A read-only Unraid scan found 52 `VIDEO_TS` directories: 51 populated DVD
  structures and one empty invalid structure. No `BDMV` directory was found.
  Use the entire indexed set for bounded start/STOP/crash screening and retain
  the deeper representative menu/stream/chapter matrix in `TASKS.md`.
- The post-presentation-engine representative hardware matrix passes menu-less
  ALADDIN, authored LEGO, Polish/PAL, and the largest five-IFO/fifteen-VOB
  fixture. The empty `ROGUE_ONE` structure fails startup safely with no Android
  crash signature. Independent HDMI evidence captures the authored Scooby root
  menu at 1920x1080/~29.75 fps with stereo audio; see
  `artifacts/firetv/dvd-presentation-v21-hdmi-av-15s.mp4` and
  `artifacts/firetv/dvd-presentation-v21-hdmi-frame.png`.
- The first complete populated-disc sweep found three video-only starts. Raw
  telemetry proved AC-3 samples were advancing but Media3 had no audio
  TrackGroup: SPU-only `private_stream_1` had caused premature `endTracks()`.
  Track discovery now waits for a physical AC-3 substream, and retained audio
  selection is committed on the player looper only after the replacement group
  exists. All three affected titles pass the focused hardware regression in
  `artifacts/firetv/dvd-late-ac3-three-disc-regression.json`.
- The corrected extractor then passed the final bounded inventory sweep on all
  51 populated Unraid DVD structures. The empty ROGUE_ONE directory is an
  expected safe failure; no BDMV exists, so Blu-ray remains SKIPPED. See
  `artifacts/firetv/dvd-presentation-v21-all-unraid-dvds-final.json` and
  `artifacts/firetv/dvd-empty-structure-safe-failure.json`.
- The exact final installed APK/current-Core rerun also passes all 51 populated
  structures in `dvd-all-unraid-final-latest-apk-current-core.json`; the empty
  structure fails safely in
  `dvd-empty-structure-final-latest-apk-current-core.json`. Fresh HDMI evidence
  is `dvd-final-current-apk-current-core-hdmi-18s.avi` plus its ffprobe JSON,
  frame, frame hashes and volume analysis. It contains 1920x1080 moving video,
  stereo 44.1 kHz audio, 29 sampled unique frames, and non-silent audio at
  -22.5 dB mean / -3.7 dB maximum.
- The strict transport gate found that Core passed a negative time to its DVD
  VM when Skip Back crossed the beginning of a title. Vibe Core now clamps disc
  seek time to zero before `MiniDVDPlayer.seek()`. Core also selects the longest
  authored VM title for skip-menu startup, treats skip-preview as a root-menu
  jump, and enforces negotiated fail-closed/fallback policy. The latest
  rebuilt/deployed test server `Sage.jar` SHA-256 is
  `89d77793830d954ef187318246030910462075e0917abfdefa3735c40319994b`;
  the prior JAR is retained on Unraid as
  `Sage.jar.backup-before-disc-seek-20260901`. The exact strict sequence now
  passes, as do five stop/restart cycles and the ALADDIN/RAYA/SOUL/
  SAVING_PRIVATE_RYAN/F9/POLAR_EXPRESS representative control matrix.
- Physical policy evidence is retained in
  `dvd-hybrid-no-mim-no-fallback-safe-failure.json`,
  `dvd-hybrid-no-mim-native-fallback.json`, and
  `dvd-scooby-native-final-strict.json`. Direct legacy Exo passed only two of
  six representative discs, so DVD now resolves to Media3 with a bounded user
  message; this is a safe compatibility fallback, not a legacy-Exo PASS.
- Final independent HDMI evidence is
  `artifacts/firetv/dvd-presentation-v21-final-hdmi-av-18s.avi`: ffprobe finds
  1920x1080 MJPEG and stereo 44.1 kHz PCM, and
  `dvd-presentation-v21-final-hdmi-frame.png` visibly confirms clean title
  video. `capture_hdmi_validation.cmd` makes this check repeatable without
  depending on PowerShell script execution policy.
- The final clean-source APK after policy/backend integration is
  `artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk`, SHA-256
  `d0735a0da6e6413d13194daafda108e066cef2efee21ddbefb441080daffc110`.
  It passed 365 project/static tests, 53 MCP tests, validation, a clean Gradle
  build, exact package install, the complete strict Native DVD command matrix,
  and MCP discovery. Independent evidence
  `dvd-clean-apk-final-hdmi-av.avi` contains 1920x1080 MJPEG plus stereo 44.1
  kHz PCM; `dvd-clean-apk-final-hdmi-frame.png` visibly confirms clean title
  rendering. The final strict MCP record is
  `dvd-scooby-clean-apk-native-final-strict.json`.
- Current headless gate: PASS on 2026-08-31 with 298 project/static tests, 49
  MCP tests, Core JUnit, full validation, clean debug APK, debug and release-
  candidate AABs, lint/signing, bundletool validation, and universal APK-set
  generation. The current transport-ownership clean APK is
  `cb57f588955b07bd18a310e8de8ef20a151861f48a3ca568b50d3cd548e733e4`;
  the 289/47/Core/validator headless gate passes against that source tree. Its
  exact-install physical ordering gate passes as
  `artifacts/firetv/connection-ordering-20260831-005301.json`, including A/V
  recovery, HOME teardown, newer-generation reconnect, and explicit close.
  The preceding lifecycle clean artifact was installed and passed physical
  ordering and teardown. The preceding backend-neutral clean APK was installed and
  passed a final Media3 hardware Pull smoke with
  advancing 1080i MPEG-2/AC-3 output, captions, full-screen 1920x1080 surface,
  typed Pull counters, and no player error.
- The current preferred-track clean APK is
  `b9ee3c91ca3ad74b15bfe74efe3f6592aebbf885af9b458993f8eb99fd647841`.
  Its exact in-place install passed Media3 hardware Pull with STV-owned
  CEA-708 Service 1, 43 rendered cue updates, and 498 ms caption/timeline
  drift. Evidence is
  `artifacts/firetv/20260831-022831_caption-media3-pull-visible.png`.
- The current capability-profile clean APK is
  `ad7e89e3ec92c9116b61e39ce887706069edc6c820e8cfc29d2436172d64f005`.
  Its exact in-place install passed hardware Pull on Media3 and legacy Exo;
  both selected `OMX.MTK.VIDEO.DECODER.MPEG2` with no player error. Evidence
  is `artifacts/firetv/codec-capability-media3-pull.json` and
  `artifacts/firetv/codec-capability-exoplayer-pull.json`.

Connection startup now has bounded payload-free diagnostics and a lifecycle-
owned single-thread executor/Future rather than an unretained activity thread.
Pause/destroy invalidates the request generation, cancels the Future, and
rejects/closes a late exact connection before it can publish stale UI state.
The post-change Amazon AFTMM gate passed media-before-GFX ordering, protocol
replies, FIFO drain, all-worker HOME teardown, newer-generation reconnect,
three hardware Pull replays, surface recreation, and final no-process
teardown. Evidence is
`artifacts/firetv/connection-ordering-20260830-230236.json`. Connection-owned
worker/socket state, bounded idempotent close, explicit Media readiness, the
single event FIFO, one-frame GFX handoff, and cancellable named remote file
transfers now pass that same gate. Reconnect eligibility/event suppression and
replaceable GFX/event protocol streams are also extracted and passed the
physical gate using the canonical captioned/comskip fixture; evidence is
`artifacts/firetv/connection-ordering-20260830-231530.json`. Split `GFXCMD2`
then completed behind a stable family map. Lifecycle/frame, drawing,
image/cache, font, surface/video, and transform/batch handlers retain one
serial dispatcher and shared handle namespace. The exact split passed OpenGL
on the final clean APK as `connection-ordering-20260830-235310.json` and GDX as
`connection-ordering-20260830-235518.json`, both using the canonical
captioned/comskip fixture. Renderer, keyboard, player scheduling, Push, SMB,
and overlay ownership were subsequently completed behind the same gates.
Further large-class splitting is maintainability work and must not be mixed
with unrelated player behavior.

OpenGL and GDX renderer initialization now share `RendererReadinessGate` rather
than polling a plain boolean every 100 ms. Resize marks the one-shot gate ready;
close/deinit cancels it and releases the GFX initializer; interrupt status is
preserved. The post-change physical gates pass as
`connection-ordering-20260830-235947.json` (OpenGL) and
`connection-ordering-20260831-000156.json` (GDX).

Delayed keyboard work is now held by an explicit main-looper owner and removed
on pause/destroy. Media3 and legacy Exo progress plus seek-recovery scheduling
share one explicit main-looper handler per player; release/replacement removes
  the progress callback. Both hardware Pull lifecycle gates pass with the
captioned fixture after the change. The final OpenGL connection run passes as
`connection-ordering-20260831-002155.json` from the exact clean-installed APK,
and direct preference inspection on
the commissioned Fire TV confirms `use_opengl_ui=true`.

Push back-pressure is now notification-driven rather than sleep-polled. Core
open/data/EOS/close/release signals wake Media3, legacy Exo, GSY-adapter, and
IJK waiters; IJK additionally has release-aware datasource creation and a
correct unconditional zero-length seek probe. Hardware Push playback passes on
Media3, legacy Exo, and IJK; the IJK gate includes exact-file startup,
pause/play recovery, no crash signature, and clean exit. SMB cleanup is owned
by one retained idempotent per-session executor. Post-change Pull/SMB seek A/B
passes on Media3 at 415/772 ms and legacy Exo at 333/1013 ms, with SMB media
bytes at 92,425,984/86,396,672 and MediaServer media-read bytes at zero. Do not
remove `SimplePullDataSource`'s bounded 100 ms remote SIZE retry unless the
SageTV protocol gains an explicit growing-file notification; it is the only
intentional production transport sleep left by this pass.

The serialized `PlaybackSessionController` now owns monotonic load/session and
operation generations. Base, Media3, and legacy ExoPlayer reject delayed
listener, progress, seek-recovery, queued UI, and surface work from replaced or
stopped players. The reviewed `BaseMediaPlayerImpl` SHA-256, including its
bounded DVD presentation diagnostics accessor, is
`63fc269f13e7e5a75f4e55b8e3b39016e9056ffc6c71baf117e6c8b596ebc9dd`.
Hardware physical gates pass on both Exo backends for Pull lifecycle/surface
replacement and repeated loads, Pull and Push FF/REW with caption retention,
clean exact EOF, and a real 2.1 to 5.1 live-TV transition. Evidence is under
`artifacts/firetv/session-controller-*-20260830.log`; the accompanying caption
screenshots begin `20260830-20` and end `caption-*-visible.png`.

The proven Media3/legacy-Exo common behavior is now expressed through typed
backend-neutral components rather than duplicated policy or debug reflection:
an immutable `PlayerRuntimeConfig`, pure-Core `PlaybackSyncPointPolicy`,
generation-safe `PullSeekRecoveryMonitor`, `PlaybackHealthSnapshot`, and
`PlaybackDataSourceTelemetry`. Player creation, decoder integration, and
backend-specific reprepare actions remain separate. Hardware device gates
passed Media3 and legacy Exo Pull frame/seek behavior plus Media3 Push
rejection. Pull/SMB Direct A/B passed on both backends; SMB source counters
showed 94,508,032/95,556,608 SMB bytes, zero MediaServer media bytes, and the
expected 23-byte shadow reply. This closes the narrow backend-neutral
configuration/seek/recovery/health/telemetry extraction; do not broaden it by
merging the player implementations.

MiniPlayer protocol command 28 now has a real dispatcher, explicit result,
and backend contract. Media3, legacy ExoPlayer, and IJK support bounded signed
frame stepping only while paused on a random-access Pull/SMB source; Push,
playing, stale-session, zero-count, and GSY System cases fail safely. The
repeatable `mcp-frame-step-test` physically passed Media3 and legacy ExoPlayer
hardware Pull with a 33 ms advance, one newly rendered frame, retained pause,
and exact command/invoke/return events. It also passed playing and physical
Media3 Push rejection. The clean installed APK repeated the Media3 gate.

The canonical v0.5.85 fixture is
`VibeSeekTest-1080i-MPEG2-AC3-CC.ts`: 900 seconds of 1080i MPEG-2, dual AC-3,
burned-in time/frame markers, actual in-band ATSC A/53 GA94 CEA-608 CC1 and
CEA-708 Service 1 payloads every 0.5 seconds, plus a matching Comskip `.edl`.
The generator verified 26,971 final pictures and exactly 26,971 GA94 payloads;
the owner also user-verified working captions on 2026-08-30. Automated
Android/STV transport and retention tests remain distinct physical gates. The
commissioned canonical file now uses CEA-608 row 14 and has SHA-256
`b54b5475e4edce1dd248d263e04e54721a01d3dc4ab5b7f41ec125dc692a473e`.
The preferred-track physical gate passed on Amazon AFTMM/API 25 with hardware
Pull: Media3 rendered explicit CEA-708 Service 1 and legacy Exo2 rendered
explicit CEA-608 CC1 while the STV remained authoritative. Screenshots are
`artifacts/firetv/20260831-021518_caption-media3-pull-visible.png` and
`artifacts/firetv/20260831-021650_caption-exoplayer-pull-visible.png`. The
settings page itself is captured as
`artifacts/firetv/track-selection-settings.png`.
Hardware Fixed/MIM and Push screenshot gates pass at
`artifacts/firetv/20260830-190054_caption-media3-fixed-visible.png` and
`artifacts/firetv/20260830-190557_caption-media3-push-visible.png`: the stable
middle cue is within one second of the STV timeline and clears the time bar.
The test-only PAUSE keepalive now provides a default 12-second inspection
window without changing normal application/STV timeout behavior. The stable
middle cue is authoritative and the automated cadence tolerance is one second.
The same harness now accepts repeatable server-owned seeks. Fixed/MIM FF, REW,
and FF_2 recovered A/V and stable captions in 328-331 ms, then passed a 4 ms
cadence-drift gate; post-seek evidence is
`artifacts/firetv/20260830-191129_caption-media3-fixed-visible.png`.
Hardware Push passes the same sequence with its automatic 3,000 ms
replacement-stream settle; evidence
`artifacts/firetv/20260830-191636_caption-media3-push-visible.png` shows stable
cue 3:37.0 versus STV 3:38 and 4 ms cadence drift. Do not reduce Push to the
failed 300 ms window, which races the server's second FLUSH.

The unknown-duration buffered live-edge fallback is commissioned on both Exo
backends. On real channel 2.1 hardware Pull, a request to 86,400,000 ms emitted
`seek_clamped_live_edge`: Media3 reached 9,174 ms and recovered A/V in 9,491
ms, its independent repeat reached 5,734 ms and recovered in 6,262 ms, and
legacy ExoPlayer reached 8,643 ms and recovered in 9,222 ms. Core/static tests
prove completed media and unknown/no-buffer cases retain old behavior. The
installed APK SHA-256 is
`4f3cd3653ed7c0cf62b0ce692da88762cacf67563de7758e4f114b9089044983`.
The same seek-policy build uses a five-second completed-tail guard. A 500 ms
guard physically landed inside the final MPEG-2 GOP and caused repeated
Media3 timeout/recovery cycles. With five seconds, both Media3 and legacy
ExoPlayer hardware Pull reach the canonical fixture's natural 899,959 ms EOF,
report a clean ended state, show no delete prompt, and keep the process alive.
Evidence is `artifacts/firetv/eof-media3-tail5s-20260830.log` and
`artifacts/firetv/eof-exoplayer-tail5s-20260830.log`.

The v0.5.84 fullscreen race is closed in both runtime behavior and test
instrumentation. The Android compatibility fallback waits 2.5 seconds and
checks SageTV `MENU_HINT` before sending the toggle-style `TV` command; it
cannot undo an existing OSD or act through a popup. Debug status version 18
reports SageTV's requested video destination and UI dimensions, and MCP no
longer mistakes the full-size Android SurfaceView for full-screen video. The
physical pre-fix screenshot shows Main Menu plus preview; the post-fix initial
and three repeated starts all report `MediaPlayer OSD`, destination
`0,0,1920,1080`, advancing A/V, and no crash. The installed APK SHA-256 is
`2e6ceab87dfd17d032e10701131b0db167587a4d6638b11634c8548a6faf3451`.

GSY's unconditional Media3 Cast/Session dependencies are now excluded. The
previously selected non-Cast AndroidX/Kotlin versions are pinned explicitly so
this removal cannot silently downgrade the tested graph. Cast, MediaRouter,
DataTransport, `BluetoothValidationActivity`, and ProfileInstaller initializer/
receiver entries are absent from debug and release-candidate manifests. The
release APK inspector rejects their return. Debug/release bundles and the
release-candidate APK pass their content gates; the candidate remains
non-publishable only because the approved production identity/key are absent.
The physical API-25 Fire TV passes generated-fixture hardware Pull start,
fullscreen, ordinary FF/REW, pause, and resume with APK SHA-256
`7b90ccd6d3574213340b28827e3512f9c29721c60d55be8ef5f73635fc5efbf4`.
A 16-command rapid mixed FF/REW stress produced one intermittent active-audio/
playback-state failure; its diagnostics remain under
`artifacts/firetv/20260830-080924_mcp_rapid_output_health_*`. The harness now
supports `--rapid-only`, `--rapid-repeats`, and `--rapid-reset-ms`. Subsequent
zero-delay testing passed three fixed-position repeats on each of Media3 Pull,
legacy Exo Pull, Media3 SMB Direct, and legacy Exo SMB Direct using
`VibeSeekTest-1080i-MPEG2-AC3.ts`. Pull recovered in 0.906-1.662 seconds; SMB
Direct recovered in 2.541-3.636 seconds. Treat this gate as passing but retain
the old artifact for future soak comparison.

The generated fixture's 120-180 second marker now passes the repeatable
server-owned Comskip gate on Media3 and legacy ExoPlayer over Pull and SMB
Direct. All four runs observed SageTV requesting exactly 180000 ms, then
recovered fullscreen hardware-decoded A/V. First source-read/first-frame timing
was 625/799 ms (Media3 SMB), 539/728 ms (legacy Exo SMB), 911/1112 ms (Media3
Pull), and 2483/2560 ms (legacy Exo Pull).

The repository currently has reviewed and host-validated API 36,
FileProvider/logging, launcher branding, deterministic launch, update workflow,
dependency-locking, and documentation changes. Commissioned exact-path
prerecorded playback and background/reconnect/repeated-start/teardown are now
proven. Completed-file exact EOF and genuine growing live-TV also pass on
Media3 and legacy ExoPlayer. Ten authoritative alternating 2.1/5.1 changes per
backend passed with advancing A/V; the final exported server image then passed
two more transitions per backend. GSY Auto/Media3/legacy Exo passed four each,
and a deliberately last GSY System run passed two without crashing. Explicit
HOME surface release/hide and foreground surface recreation now pass on both
Exo backends. Audio-focus loss/recovery now passes on both Exo backends;
real CEA-608/708 caption discovery, selection, visible rendering, disable, and
crash checks now pass in Push/Dynamic and Pull on both Exo backends. Caption
On/Off is owned by the SageTV STV; there is no separate Android navigation
gate. Core sends `VIDEO_CC_STATE`, and the default physical caption test proves
visible captions without calling Android's debug track selector. Real
Meet-the-Press `.edl` marker coverage now passes over SMB Direct: Media3 RIGHT
lands at 985.220 seconds and legacy Exo LEFT lands within 22 ms of that prior
marker endpoint, with a new server seek sequence and sustained A/V.

Fixed/FFmpeg-MIM commissioning uses the server's Intel VAAPI backend and
retains machine-readable evidence in `artifacts/firetv/fixed-mim-*`. Legacy
Exo hardware/software/fallback and Media3 hardware/software/fallback pass.
GSY Auto/Media3/legacy Exo also pass; GSY System is a bounded Media3 safety
fallback rather than native System evidence. A deliberately missing render
device selected reported server software/libx264 and passed Media3 completed/
live playback. Intel Fixed is supported as an opt-in configuration; MIM
remains disabled by default and AMD/NVIDIA remain physical-hardware skips.

The latest exact 0.4.7 hardware-only all-selection rerun is
`artifacts/firetv/fixed-mim-20260830_180555/FIXED_MIM_MATRIX.json`. Legacy Exo,
Media3, IJK, GSY Auto, GSY Media3, GSY legacy Exo, and the bounded GSY System
safe delegate passed prerecorded controls and real 2.1/5.1 live gates. Every
job reported fresh Intel VAAPI/`h264_vaapi`, stopped state, matching input, and
zero orphans. Do not rerun Android software-decoder cases unless the owner
changes the current hardware-only commissioning scope.

The final supported Media3 hardware rerun used the exact deployed FFmpeg/MIM
0.4.6 artifact and is retained at
`artifacts/firetv/fixed-mim-20260829_234208/FIXED_MIM_MATRIX.json`. Both
prerecorded and four-change live gates passed. Status evidence reports fresh
`vaapi`/`h264_vaapi` jobs, `hardwareEncode=true`, matching source input,
`state=stopped`, and `activeJobs=[]` after each gate. Temporary SSH test keys
were removed from Unraid after evidence collection.

On 2026-08-30 the active appdata wrapper was found to have drifted back to MIM
0.4.5. Exact 0.4.6 Linux output was restored after backing up the mounted files
under `.commissioning-backups/mim-before-046-20260830`. With that verified
artifact, legacy Exo software decode passed the full generated prerecorded
Fixed controls/restarts. IJK passed generated prerecorded controls plus four
2.1/5.1 live changes in three consecutive runs:
`fixed-mim-20260830_085919`, `fixed-mim-20260830_090300`, and
`fixed-mim-20260830_090610`. Status was fresh VAAPI/h264_vaapi with matching
input and zero orphan jobs. The matrix's new `--caption-gate skip` is used only
because the synthetic fixture has track metadata but no non-empty cues; real
caption retention remains established by the captioned recording.

The restart persistence defect is now closed. The isolated container's active
`/opt/sagetv/server/ffmpeg`, appdata wrapper, and image-pinned `ffmpeg_MIM`
all report 0.4.6 after restart. The wrapper SHA-256 is
`668c056eb05c77e2ad3303ac1b351103f7367a93a44904e7b430b971da724f80`;
`ffmpeg.real` is
`fc36882f4c0bfd94910f15cc285c0daf29b31b11598bc3e2df9f089fb3578519`.
The matching container build context was refreshed through
`stage-artifacts.sh`. The post-restart Exo software/Intel VAAPI Fixed run is
`artifacts/firetv/fixed-mim-20260830_132155/FIXED_MIM_MATRIX.json` and passes.

Exact-path events 230/232 now enter the Core's canonical `MediaPlayer OSD`
directly after `Watch`; event 232 additionally queues the first-segment time.
MCP waits for this asynchronous STV transition before attempting its stock-
server `TV` fallback and requires 750 ms of stable fullscreen state. This
removes the observed OSD-to-Main-Menu double-toggle. Physical evidence is
`artifacts/firetv/fullscreen-regression-20260830/passive-grace-stable-2.png`.
The currently deployed isolated-server `Sage.jar` SHA-256 is
`89d77793830d954ef187318246030910462075e0917abfdefa3735c40319994b`.

SMB Direct/Shadow Pull is implemented for Media3 and legacy ExoPlayer. It keeps
normal MiniPlayer control and codec declarations, negotiates ordinary Pull,
and replaces only the byte source with Apache-2.0 SMBJ. The shadow port-7818
session sends OPEN/SIZE/CLOSE plus one counted byte at the same position for
each non-sequential SMB access; physical A/B testing proved OPEN/SIZE alone and
a byte-zero-only probe do not keep stock SageTV's STV seek/Comskip position in
sync. Full generated-fixture matrices, explicit failure, Auto fallback,
60-second playback, repeated start/stop, cleanup, and real marker tests pass.
Named SMB configuration profiles are implemented with a checksummed schema,
atomic rename, credential exclusion, overwrite confirmation, and separate
duplicate-Client-ID confirmation. Physical commissioning against
`smb://192.168.10.175/sagemedia/config/` passed save/list/load/overwrite/delete,
valid-checksum schema and credential rejection, partial/corrupt file rejection,
missing-share failure, concurrent-write exclusion, credential redaction, UI
selection, and the two-step warned Client-ID replacement path. The device was
left on `DEV001`; the remote test profiles were removed.

Large-jump Pull/SMB timing and byte-ownership A/B tests use the deterministic
`VibeSeekTest-1080i-MPEG2-AC3.ts` fixture on the same Fire TV, decoder, and
player backend. Legacy Exo and Media3 both pass. The evidence correlates the
exact server seek command, first physical source read, first observed decoder
input (100 ms observation precision), exact rendered-frame callback, and
sustained recovery. Pull reports positive SageTV MediaServer media bytes; SMB
reports positive SMB bytes, zero ordinary MediaServer media bytes, and only 17
bounded same-offset shadow bytes. See the two
`artifacts/firetv/generated-pull-smb-seek-ab-*.log` files.

The generated fixture now includes deterministic `.edl` intervals at
120-180, 360-420, and 660-720 seconds. Automatic STV skipping is observed but
is not deterministic after every artificial reposition, so the harness
requires the actual server-requested marker target and does not infer a pass
from elapsed timeline alone. Pull and SMB Direct automatic-marker recovery
passed on Media3 and legacy ExoPlayer. Push must be positioned through SageTV
commands; a client-local seek is intentionally not accepted as Push evidence.

The MediaServer NIO A/B is complete. With all other variables fixed,
`use_nio_transfers=false` produced 340/434 ms sustained recovery and
`use_nio_transfers=true` produced 447/638 ms. The corresponding evidence is in
`artifacts/firetv/generated-pull-smb-nio-*.log`. No missing/partial transfer was
observed and NIO did not improve the case. The isolated Unraid test server was
restored to `use_nio_transfers=false`; a recovery copy is retained under its
`.commissioning-backups` appdata directory.

Genuine server-driven Push reposition/flush/anchor ordering is captured in
`artifacts/firetv/recovered-container-artifacts/20260830-070947_mcp_comskip_right_fail_state.json`
and the adjacent checkpoints. Multiple sequences show server flush, anchor,
backend reprepare, first video frame, and resumed A/V. These files were
recovered after correcting the stale direct-MCP artifact path; all active
configuration now writes to the bind-mounted Android repository.

Media3 Push codec-queueing commissioning is complete using exact path
`/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3.ts`. Three isolated repeats are
stored as `artifacts/firetv/generated-push-codec-repeat1.json` through
`repeat3.json`. Sync recovered in 7196/6477/7418 ms. Auto recovered in
8752/4236 ms and timed out once at 50650 ms. The Sync default is retained for
its two-of-three advantage and bounded variance, but this does not reclassify
Push seek recovery as fast.

The default, explicit override, and direct Leanback-launch client-ID paths pass
physically and the test device was restored to DEV001. A current Play audit
confirms target SDK, paired ARM native contents, 16 KB ARM64 ELF alignment, and
APK ZIP alignment pass. Store publication remains blocked by production
identity/signing and Play Console policy/listing setup. Debug and development-
signed release-candidate AABs now build and validate with bundletool 1.18.3;
the debug APK set physically installs while preserving app data. This proves
the bundle pipeline but is not a production-signed artifact. The publication
gate must be checked against Google's current
[target API policy](https://support.google.com/googleplay/android-developer/answer/11926878),
[Android App Bundle requirement](https://support.google.com/googleplay/android-developer/answer/2481797),
[16 KB page-size guidance](https://developer.android.com/guide/practices/page-sizes),
[TV quality requirements](https://developer.android.com/develop/adaptive-apps/quality-guidelines/tv-app-quality),
and [Data Safety requirements](https://support.google.com/googleplay/android-developer/answer/10787469).

## Work completed in the current review

- SageTV Core forwards the STV caption state to supporting Vibe MiniClients
  after media load, Android retains the request until tracks are ready, and
  both Exo backends passed visible CEA-608/708 hardware Push/Dynamic tests
  without invoking `dev_set_subtitle_track`. This true STV authority requires
  the Vibe-patched `Sage.jar`, because stock SageTV does not publish
  `VIDEO_CC_STATE`. For IJK or failed callback negotiation, the long-press MiniClient CC
  submenu and Audio and Caption settings expose explicit `Off`, `CC1`, and
  `CC2` compatibility choices. A later received STV state always overrides
  that fallback.
- Added root `bundle`/`bundle-install` commands backed by the unified image's
  pinned bundletool 1.18.3. Both AABs validate, the debug universal APK set was
  physically installed on AFTMM/API 25 after an exact package check, and the
  two configured servers remained present. Current hashes are debug AAB
  `6c29d43089fa7a442c2ebb57b49c10e142e7aa899da20ef0cc5ba0d7c99b950b`,
  release-candidate AAB
  `2b8570577ba0c09c10df76eb29315a0616b157fb268cc7a4802499dd35500cf1`,
  and debug APKS
  `ed0c8a99efc897ff5e1236dcc33c8d136c601521f2eafc4ab706165d9017044a`.
  The release candidate uses the development identity and key and must not be
  uploaded to Google Play.
- Restored broadcast CEA-608/708 captions in Push/Dynamic and Pull MPEG-TS. Media3 declares
  caption formats through its default extractor factory; legacy ExoPlayer 2.18
  preserves its normal extractor set/order and replaces only the TS extractor
  because that version lacks `setTsSubtitleFormats`. Added null-safe track
  reporting, SageTV disabled-track sentinel translation, MCP current-cue and
  overlay state, and `mcp-caption-test`. Caption views attach to the Activity
  content root above the SageTV GL surface. The gate now captures the Fire TV
  screen while a current cue is non-empty and the overlay is attached. Both
  backends passed Push/Dynamic and Pull with visibly rendered Meet the Press
  captions on Amazon AFTMM/API 25 without a crash. GSY System was deliberately
  not invoked. One brief user-observed runtime-check message was not reproduced
  in a subsequent 120-second recording and had no fatal logcat signature.
- Replaced `AudioUtil` with a lifecycle-owned `AudioFocusController`, with
  modern `AudioFocusRequest`/media attributes on API 26+, the required API-25
  fallback, explicit transient/duck/permanent behavior, user-pause protection,
  idempotent abandonment, and stale-callback generations. Added debug-only MCP
  focus contention plus `mcp-audio-focus-test`. Media3 and legacy ExoPlayer
  hardware Pull both passed the complete physical focus gate with advancing
  A/V recovery and no crash signature. GSY System was deliberately excluded
  and remains last in future full player matrices.
- Added negotiated SageTV Core media-state metadata and Android parsing without
  changing older-client URLs. Physical server logs prove `active=0` for Meet
  the Press and `active=1` for live 2.1/5.1 files; format/encoder hints are
  retained by the backend-owned media context.
- Added opt-in exact-channel MiniClient event 231 plus MCP
  `dev_set_live_channel`. Unlike legacy numeric UI input, it preserves dotted
  ATSC channel numbers. The negotiated URL also carries authoritative channel
  identity, so the gate distinguishes an already-active healthy channel from
  a failed transition and confirms the destination after a real switch.
  Media3 and legacy ExoPlayer hardware Pull each passed 10 alternating 2.1/5.1
  changes. GSY Auto, Media3, and legacy Exo passed four each; GSY System passed
  a bounded two-change crash-checked run on the commissioned Fire TV. After
  the exact final server export was installed, Media3, legacy ExoPlayer, and a
  deliberately last GSY System run each passed fresh A/V plus exact 5.1/2.1
  transitions again.
- Correlated successful live starts with OpenDCT. Its embedded FFmpeg probing
  completed in roughly 0.4–1.8 seconds despite transient incomplete-frame
  warnings. This path is independent of SageTV MIM; MIM remains disabled until
  its own physical tests pass.
- Added and passed `dev mcp-eof-test` on the commissioned Fire TV for Media3
  and legacy ExoPlayer using the exact 2.1 GB Meet the Press MPEG-TS recording.
  Pull sources now bound completed-file reads, recheck potentially growing
  SageTV boundaries, and report confirmed Media3 tail EOF without a crash or
  permanent buffering loop. FFprobe evidence ruled out a damaged recording
  and FFmpeg/MIM involvement in this direct Pull path.
- Added and passed `dev mcp-lifecycle-test` on the commissioned Amazon
  AFTMM/API 25. It proves advancing A/V before HOME, process survival in the
  background, a stable foreground reconnect, three repeated exact-path starts,
  crash-free operation, and final no-process teardown. The gate passed all
  four Media3/legacy-ExoPlayer and Push/Pull combinations; server logs show no
  new 30-second media-command timeout after the correction.
- Fixed the physical failure uncovered by that gate. SageTV was waiting in
  `MiniPlayer.DVDStream` for a four-byte reply that Android omitted when Media3
  rejected audio-track selection from the network thread. Media3/Exo2 track
  selection is now marshalled to the main/player thread and
  `MEDIACMD_DVD_STREAMS` replies on every branch.
- Made MiniClient connection shutdown ownership-aware so a reconnect finishing
  after close cannot publish its socket or clear a replacement session.
- Added `docs/CONNECTION_PROTOCOL_LIFECYCLE.md` and executable
  characterization for Media-before-GFX startup, GFX reader/dispatcher buffer
  ownership, serialized event replies, reconnect conditions, connection close
  order, renderer dispatch, and activity startup/pause behavior. Structural
  extraction remains physically gated; no production connection or playback
  behavior changed.
- Migrated all settings fragments and host activities to AndroidX/AppCompat,
  moved the codec dialog to the AndroidX fragment manager, and added a static
  regression guard. Remaining platform fragments are outside the settings
  package and remain an explicit task.
- Centralized fullscreen control in `AppUtil`; ConnectingActivity now uses a
  main-looper Handler and cancels delayed work during destruction.
- Removed the unused `RECORD_AUDIO` permission/feature after confirming there
  is no active microphone implementation.
- Added `scripts/inspect_apk.py` and root `inspect-apk` workflow support. The
  release content boundary passes; strict release inspection correctly blocks
  the development-signed candidate until a production key is supplied.
- Completed the `DevTestReceiver` split. The receiver is now only a debug
  broadcast dispatcher; session/UI, synchronous player, asynchronous health
  checks, executor ownership, parsing, client ID, tuning, configuration,
  snapshots, and response formatting have separate debug-only owners with
  unchanged wire strings and targeted characterization/compilation gates.
- Replaced anonymous discovery threads and the TV background Timer with named
  or explicit-main-looper lifecycle owners. Core tests now use modern
  Mockito/JUnit and pass under the unified JDK 17 toolchain.
- Hardened playback startup so expected video/audio output, surface validity,
  player errors, and `AskToDeleteRecording`/EOF generate a structured verdict
  before any tuning/player-matrix operation.
- Added `dev_play_server_path` and the debug-only MiniClient event sender for
  deterministic playback of an exact indexed server file without Search UI
  navigation. Restored the omitted stream-expectation helper that had caused
  a false MCP tool error after successful startup.
- Commissioned `/var/media/tv/MeetthePress-65149351-0.ts` against the final
  Unraid image at `192.168.10.232`. Media3 Push/Dynamic hardware mode entered
  `MediaPlayer OSD`, exposed a 1920x1080 surface, advanced MPEG-2 and AC-3
  output counters, and passed FF/REW recovery.
- Captured the resolved dependency graph under `artifacts/reports`. A trial
  removal of unused Cast dependencies was rejected because it also changed 28
  aligned AndroidX artifacts; do not retry it without explicit constraints and
  the physical playback/UI matrix.
- `docs/DEPENDENCY_AUDIT.md` records the direct families, license-review gaps,
  Cast/exported-surface finding, and an OSV scan of all 342 locked Maven
  coordinates. The 14 advisory-bearing Protobuf/Netty coordinates are absent
  from the captured application runtime graphs and belong to build/test paths.
- Started physical commissioning on the configured Amazon AFTMM/API 25 Fire TV.
  The guarded clean install touched only
  `org.opensagetv.miniclient.dev.debug`, discovery showed servers at
  `192.168.10.175` and `192.168.10.232`; the commissioned Vibe test server is
  container `sagetv-vibe-server-u26-gpu-j11` at `192.168.10.232`. The
  installed APK was pulled back and verified
  byte-for-byte against the built artifact. The protected
  `jvl.sage.miniclient.android.tv.debug` package remains installed and was not
  mutated.
- Disabled automatic sleep/screensaver on the test Fire TV for long physical
  runs. The device reported `mStayOn=true`, plug modes `7`, display timeout
  `2147483647`, sleep timeout `0`, and screensaver disabled.
- Added `docs/ANDROID_FRAMEWORK_MODERNIZATION.md` and an executable inventory
  guard covering all active raw scheduling owners, legacy Fragment/settings
  owners, permission surface, audio focus, fullscreen, and dependency
  boundaries. The completed inventory item was removed from `TASKS.md`.
- Fixed raw dash-prefixed ADB argument forwarding in `dev.ps1` and added a
  regression test using the real Windows/WSL path.
- Converted the previously undiscovered pytest-style API-36 checks to
  unittest. The current host gate passes 222 scaffold/static tests, 41 MCP
  tests, and full validation in the existing unified container.
- Reviewed the other-AI changes without modifying the protected original tree.
- Consolidated task tracking into `TASKS.md` and moved durable playback rules to
  `docs/PLAYBACK_DIAGNOSTICS.md`.
- Consolidated release history into `CHANGELOG.md` and removed version-specific
  update/review/prompt documents.
- Replaced per-version update metadata with stable `release.properties`.
- Hardened changed-files ZIP preflight with path, duplicate-entry, manifest,
  payload-hash, and untouched-baseline validation.
- Added restart coverage for interrupted extraction/preparation plus corrupt,
  unsafe, duplicate, mismatched, drifted, build-required, host-only, and
  package-selection update cases.
- Added `dev.ps1` and `update.ps1` so Windows uses the sibling unified build
  environment reliably; the component-owned standalone image path was removed.
- Added `update.sh` as the clean cross-platform update entry point.
- Preserved Android's fully tested self-contained updater and guarded DEV001
  commissioning launch while aligning its root commands, metadata, package
  directory, manifests, and four resumable preparation gates with the shared
  contract.
- Locked the Gradle distribution checksum, Gradle dependency resolutions,
  artifact verification checksums, the exact Python/MCP environment, and the
  Android platform-tools revision. The unified Dockerfile keeps authoritative
  version metadata after the large SDK layer so source/metadata-only changes do
  not invalidate that toolchain layer.
- Passed the Android suite through both the component root entry point and the
  unified build-environment `android-all` entry point. The latter now exports
  its mounted build-environment root to component tests.

## Validation status

The consolidated workflow was run from Windows through `dev.cmd`, which
explicitly selected `/workspace/android-client` in the one
`opensagetv-vibe-dev` container:

```text
./dev test (Windows):           445 project/static + 57 MCP + Core JUnit PASS
./dev validate:                 PASS
./dev build:                    BUILD SUCCESSFUL, 60/60 tasks
./dev mcp-lifecycle-test:       PASS (API-25/API-30 auto-resume on/off,
                                      manual pause, timeout, replay, teardown)
./dev mcp-eof-test (Media3):    PASS (real Fire TV, exact server path)
./dev mcp-eof-test (Exo2):      PASS (real Fire TV, exact server path)
./dev mcp-audio-focus-test (Media3): PASS (real Fire TV, exact server path)
./dev mcp-audio-focus-test (Exo2):   PASS (real Fire TV, exact server path)
./dev mcp-caption-test (Media3 Push/Pull): PASS (visible CEA-608/708 cues)
./dev mcp-caption-test (Exo2 Push/Pull):   PASS (visible CEA-608/708 cues)
debug APK inspection:           PASS
release content inspection:     PASS
strict production signing:      FAIL (development signer; expected blocker)
```

Current clean debug APK SHA-256:
`92b92345ecd3328a41f917bf232a65006af8e0532770b97e94b5686fefd0a5ff`.
This long-press repair APK is installed on the non-Pro `.25` control device;
an injected Fire OS hold opened the complete navigation/player overlay. The
refreshed 1,340-file source archive passed all 445 tests, then a separate build
from that extracted source using an empty Gradle cache executed all 60 tasks
and reproduced this APK byte-for-byte. The Pro `.29` retains the physically
passing incremental lifecycle build
`3295d35f427ae895ee66ea6474b4061375f3270fce5b5192a693508659094a12`
and is paused from further testing by user request.

Built artifact:

```text
artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk
SHA-256: 92b92345ecd3328a41f917bf232a65006af8e0532770b97e94b5686fefd0a5ff
package: opensagetv.vibe.miniclient.debug
min SDK: 23
target/compile SDK: 36/36
```

APK inspection confirmed the expected adaptive/legacy/round Vibe launcher
resources across all five densities, the separate TV banner, non-exported
`${applicationId}.fileprovider`, and app-private internal/external file paths.
Requested Android permissions are `INTERNET`, `ACCESS_WIFI_STATE`,
`ACCESS_NETWORK_STATE`, and `WAKE_LOCK` plus Android's
generated not-exported receiver permission. `RECORD_AUDIO` and the optional
microphone feature were removed because the active source has no recording
implementation. Merged third-party exported components remain an explicit
audit task.

The local GitHub release bundle and changed-files handoff package have been
regenerated and inspected. The final 1,340-file source ZIP was extracted into
an isolated Git-less directory and passed all 445 project/static tests. Its
60-task debug-APK build used a new Gradle user home and reproduced the canonical
APK byte-for-byte after the dependency-verification repair. Git-less bundles
record `project-manifest` provenance;
Git checkouts retain exact revision/dirty-state enforcement. Strict production
signing remains intentionally unsatisfied; no production-signed APK is claimed.

The seven new connection/protocol characterization tests also pass directly on
the host. The full root count above must be reconfirmed after the manifest is
refreshed.

Physical Fire TV commissioning has started but is incomplete. The current APK
(`393c3b0557642fb07fb7ec8a5cda145b2149139f31e858dcbcd62189c5cdc279`)
was clean-installed on Amazon AFTMM/API 25 to invalidate Fire OS launcher
artwork, after backing up and restoring the Dev-only preferences.
The installed target-SDK-36 package no longer requests `RECORD_AUDIO`; the
71-tool MCP smoke test passes, and discovery displays servers
`192.168.10.175` and `192.168.10.232`; the intended commissioned test server is
Unraid container `sagetv-vibe-server-u26-gpu-j11` at `192.168.10.232`.
Screenshot evidence includes
`artifacts/firetv/20260829-044949_mcp_smoke.png`. Direct MCP connection to
`192.168.10.232` and exact-path prerecorded playback now pass without manual
server or Search-result selection. Exact EOF, teardown, genuine growing live
playback, repeated exact channel changes, and HOME/foreground surface
recreation and the Media3/legacy-Exo audio-focus matrix pass. GSY System was
not invoked by the focus or caption gates. The real CEA-608/708 Push/Pull caption matrix
passes visibly on Media3 and legacy ExoPlayer. Automated screenshot evidence is
stored under `artifacts/firetv/*_caption-*-visible.png`. Subsequent real `.edl`
marker tests pass on SMB Direct for both Exo backends; see the current-state
summary and compatibility evidence above.

The API-36 storage/share flow is physically commissioned. App-private file
logging creates `Documents/logs/sagetv-miniclient.txt`, Share Log selects the
newest file, FileProvider grants it to X-plore without storage permission, and
the test restored file logging to off. Evidence is retained as
`artifacts/firetv/share-log-chooser.png`. The generated seek fixture also fills
the Fire TV's calibrated display viewport with 1920x1080 surface telemetry;
see `artifacts/firetv/vibe-generated-fullscreen.png`.

Upgrade and cleanup are also physically commissioned. Disposable internal and
app-specific external sentinels survived an exact in-place `adb install -r`;
uninstall then removed the Dev package's entire external Android/data tree.
The exact clean APK was reinstalled after the test. This was isolated to
`org.opensagetv.miniclient.dev.debug`; no production package or media path was
used.

Centralized fullscreen is physically complete on the commissioned Fire TV.
Repeated exact playback, calibrated 1920x1080 video/destination telemetry,
OpenGL/libGDX overlays, Help/Video Info dialogs, system overlays, HOME/return,
and teardown pass. The Fire TV IME was shown over active playback without
resizing the calibrated window and was then hidden successfully; screenshot
evidence is `artifacts/firetv/keyboard-fullscreen.png`. Production system-UI
flags remain centralized in `AppUtil`.

The target-SDK-36 launcher and settings flow also passes physical navigation:
Settings opens from the Leanback Configure row, the default-player preference
dialog opens and cancels, HOME/relaunch returns without a fatal exception or
ANR, and both original server records remain. The Add Server dialog now handles
Fire OS API 25's `EditText` D-pad behavior explicitly; Server Name -> Server
Address -> Add passes using only D-pad Down. No test server entry was saved.

The Leanback launcher/server-browser batch is now AndroidX-based:
`MainActivity` and `ServersActivity` use support fragment managers,
`MainFragment` uses `BrowseSupportFragment`, Add Server and Auto Connect use
support `DialogFragment`, and the launcher layout uses
`FragmentContainerView`. Static checks and compilation pass, and the migrated
launcher, Add Server D-pad path, and temporary Auto Connect path were exercised
on the commissioned Fire TV without a fragment/runtime failure. Auto Connect
was restored to its prior `false` value after validation.

The playback overlays were then removed from the framework FragmentManager
entirely. `NavigationDialog`, `VideoInfoDialog`, and `HelpDialog` are retained
by `UIActivityLifeCycleHandler`, dismissed together during pause/destroy, and
work unchanged from both the plain OpenGL `Activity` and libGDX
`AndroidApplication`. OpenGL physically rendered all three over the canonical
fixture; libGDX rendered navigation, and overlay-visible HOME teardown had no
window/fragment/runtime failure. The exact clean APK SHA-256 is
`ef16e5a24cb1d6cb41e7aaa160bdb559cf7dec3055746adb25366d7c8056a398`;
its final ordering evidence is
`connection-ordering-20260831-013302.json` (OpenGL) and
`connection-ordering-20260831-013520.json` (GDX).

All root CMD, PowerShell, and shell entry points were audited. PowerShell and
Bash syntax pass, and `dev.cmd`/`update.cmd` resolve their own project directory
and the sibling build environment correctly when launched from outside this
repository. Normal work uses the installed
`opensagetv-vibe-build-env:u26-j11` image and existing
`opensagetv-vibe-dev` container; it does not create an Android-specific image.
The shared wrapper now detects a changed sibling-workspace root and rebinds the
same named container instead of executing against stale mounts.

An independently committed temporary sibling layout passed its own root
`test`, `validate`, and clean `build` commands without the protected original
workspace. It generated a fresh private debug key as expected, then the single
container was rebound to this real checkout and the temporary tree was removed.

## v0.5.89 release-candidate state

The SageMC interoperability gate is complete on the commissioned non-Pro
Fire TV (`192.168.10.25:5555`) against isolated Vibe server `.232`. The exact
`/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts` path passed initial
playback, same-connection HOME return and Surface recreation, manual-pause
preservation, three consecutive exact-file watch cycles, and teardown with the
staged Core redundant-watch correction active. The lifecycle runner now clears
the Android crash buffer before evaluating the current process, matching the
other physical runners and excluding stale-PID failures.

The v0.5.89 Android candidate also fixes the Media Keys settings
`SwitchPreference`/`SwitchPreferenceCompat` mismatch and applies one bounded
error-presentation rule to Media3 and legacy ExoPlayer: a transient
`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` remains logged and recovered but is
not shown after the current load has already rendered its first frame. Startup
container errors, other errors, and maximum-retry failures stay user-visible.
The package version is sourced from root `VERSION`; both `dumpsys package` and
the physically rendered Settings screen confirmed the version name rather than
the inherited upstream `1.14.0`. The final clean APK reports
`0.5.89-DEV-DEBUG`, has SHA-256
`6a9fe6f7a416d4f7a42de103a0a84319372794a5e6835fdce712978344b944d6`, and is
installed on the Fire TV Pro (`192.168.10.29:5555`). Against the unmodified
`.175` server, Media3 hardware MPEG-2 Push passed seek, large-jump,
pause/resume, and six rapid FF/rewind cycles with 821-2,093 ms recovery. The
user then passed the same remote operations manually with legacy ExoPlayer
hardware Push. The captured trace contained no player error, crash,
maximum-retry failure, or visible unsupported-container warning.

Release `v0.5.89` is published at
<https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/releases/tag/v0.5.89>.
Commit `c37e4a46bc41112b39bebc52c28e4197773f29af` passed GitHub's
`source-contracts` workflow. The manifest-exact source ZIP independently
passed all 496 project/static tests, all 70 MCP/workflow tests, Gradle core
tests, project validation, and a clean 60-task APK build from a separate
extracted checkout. All five public assets were downloaded into a fresh local
directory and matched GitHub's SHA-256 digests; the published APK retains the
physically tested SHA-256 above.

## Exact resume sequence

Fire TV Pro testing on `192.168.10.29` was resumed by the user on 2026-09-05.
Its ADB transport is authorized. Launcher/icon, captions, Native hardware DVD
cadence, A/V progress, transport/chapter recovery, and authored-disc transitions
pass. The affected cadence/control gate was repeated successfully on `.25`.
Resume with the remaining ordered items in `TASKS.md`; do not reopen DVD
cadence unless a reproducible physical regression appears.

From Windows Command Prompt or PowerShell:

```bat
dev.cmd test
dev.cmd validate
dev.cmd build
```

From Linux/WSL:

```bash
./dev.sh test
./dev.sh validate
./dev.sh build
```

Then record the APK SHA-256 and inspect package ID, target SDK, permissions,
exported components, FileProvider authority, and launcher resources. Only after
that should a guarded physical install/launch and the first-time setup/device
matrix proceed.

## Invariants

- Never modify `SageTV-MiniClient-Dev` during this migration.
- Never mutate a `jvl.sage.miniclient*` package.
- Do not make Media3 the default or change the frozen base player contract
  without explicit evidence and authorization.
- Do not enable continuous player telemetry; use bounded/on-demand diagnostics.
- Do not call a watchdog an Android/server/FFmpeg failure without correlated
  evidence.
- Do not create another task list, per-version update note, AI prompt file, or
  review document. Update the existing durable files.
- Do not claim a physical-device PASS from host-only tests.

## Known remaining gates

See `TASKS.md`. Its feature-expansion foundation is complete on the
commissioned API-25 Fire TV. Matching-device checks (adaptive icons and API
33+ predictive Back) still block release sign-off when their hardware is
unavailable, but do not block unrelated feature development. Dependency churn,
  remaining large-class splitting, signing, and publication
remain deliberately deferred. The
host-only unified workflow and deterministic clean APK gate are complete.
