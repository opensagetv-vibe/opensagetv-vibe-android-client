- [x] v0.5.75 automated test client ID defaults to `44:45:56:30:30:31` unless `--client-id` is supplied; normal app ID generation remains original behavior.
- [x] v0.5.75 restore original generated/persisted client ID behavior; add `./dev.sh client-id --show|--set|--generate`; document mandatory first-time setup before automated tests.
## v0.5.74

- [x] Add persistent MediaCodec queueing mode settings for Media3 and legacy ExoPlayer.
- [x] Make Sync the recommended/default value while retaining Auto and Async choices.
- [x] Preserve MCP runtime override priority and return to saved preference after tuning reset.
- [x] Update tuning-matrix defaults to Sync and add `auto_codec` profile.
- [ ] Rebuild/install debug APK and verify both settings screens plus effective Sync mode on-device.

- [x] v0.5.73: accelerate tuning sweeps by caching the first Search-selected MediaFile ID and replaying it directly with a fresh player for later combinations.
- [x] v0.5.73: add fast/isolated startup modes, automatic isolated fallback, and per-combination startup timing.
- [x] v0.5.72: connect/verify ADB before runtime tuning-matrix cases begin.
# v0.5.71 host tuning-matrix tasks

- [x] Accept comma-separated `--streaming push,pull` / `pull,push`.
- [x] Run each streaming mode with fresh playback and preserve mode on every result.
- [x] Ignore/collapse Pull-only tuning dimensions during Push tests.
- [x] Deduplicate Push named profiles that become equivalent after Pull-only settings are removed.
- [x] Report per-streaming combination counts and ignored tuning dimensions.
- [x] Keep `debugStatusVersion=13`; no APK rebuild required.
- [ ] Run the first 27-case combined Media3 tuning matrix and compare Pull winners separately from Push codec-mode results.

# v0.5.70 validation tasks

- [x] Add Dev-only in-memory runtime tuning defaults matching v0.5.69 behavior.
- [x] Wire Media3/legacy Exo2 TS search, Pull read size, seek policy, load-control, seek recovery, directional threshold, and codec queueing to runtime tuning.
- [x] Add MCP `dev_set_player_tuning` / `dev_player_tuning`.
- [x] Add `mcp-player-tune` and fresh-playback `mcp-player-tuning-matrix`.
- [x] Preserve `--slow-recovery-ms` in tuning matrix reports.
- [x] Add runtime-tuning tests and bump debug contract to 13.
- [ ] Rebuild/install v0.5.70 and verify `debugStatusVersion=13`.
- [ ] Run the first Media3 Pull sweep and compare the top result against compiled-default control at least twice.
- [ ] Run the first legacy Exo2 Pull read/seek/codec sweep.
- [ ] Promote only repeatable winners into normal compiled defaults.

# v0.5.69 validation tasks

- [x] Add `--slow-recovery-ms` / `--slow_recover_ms` / `--slow-recover-ms` CLI aliases.
- [x] Disable Media3's fixed 10 s Pull reprepare after report evidence showed it worsened recovery.
- [x] Restore Media3 Pull seek policy to CLOSEST_SYNC.
- [x] Keep proven Exo2 directional sync policy.
- [x] Increase Exo2-only Pull network block to 512 KiB.
- [x] Make GSY/System fall back to Media3 rather than immediately terminate playback.
- [ ] Rebuild/install APK and verify `debugStatusVersion=12`.
- [ ] Run the 7-case focused v0.5.69 matrix from the 20260828_015224 report.
- [ ] If Exo2 READ wait materially drops, retain 512 KiB; otherwise restore 256 KiB before another tuning variable.
- [ ] Verify Media3 Pull no longer emits `pull_seek_reprepare_before/after`.
- [ ] Verify requested GSY/System reports effective Media3 backend and A/V starts instead of opening `AskToDeleteRecording`.

## v0.5.68

- [x] Add `--exclude-players` to omit unchanged player families from focused matrix reruns.
- [x] Add `--exclude-gsy-engines` so GSY/System can be skipped independently of the other GSY engines.
- [x] Add `--exclude-case-id` for exact case-level retest exclusions.
- [x] Persist exclusions in matrix JSON as `retestExclusions`.
- [ ] Use exclusions on the next issue-only run so unchanged System startup failures do not consume retest time.

## v0.5.67

- [x] Restore Media3 Pull TS timestamp search to 8x after the 4x experiment showed no meaningful improvement.
- [x] Add direction-aware Pull seek sync policy to Media3 and Exo2.
- [x] Add one-shot 10 s Pull BUFFERING recovery reprepare.
- [x] Harden Media3/Exo2 Pull DataSource non-zero reads against invalid 0 returns.
- [x] Add Exo2 Pull READ/open telemetry.
- [ ] Rebuild/install v0.5.67 Dev APK; verify `debugStatusVersion=11`.
- [ ] Run report-driven `--issues-only` and compare Media3/GSY-Media3 Pull Comskip plus Exo2 Pull Comskip against the 20260828_010837 baseline.
- [ ] Verify `pull_seek_policy_next_sync` / `previous_sync` and whether `pull_seek_reprepare_*` fires.
- [ ] If directional sync fixes Pull recovery, keep it and reassess whether the 10 s reprepare fallback is still needed.

## v0.5.66

- [x] Isolate every `--issues-only` media operation in its own clean playback session.
- [x] Preserve per-operation startup evidence under `checkStartups`.
- [x] Reclassify isolated/restart playback startup failures as media startup failures rather than `INFRA_ERROR`.
- [x] Make report-derived issue selection retain per-check startup failures.
- [x] Change Media3 Pull TS timestamp-search multiplier from 8x to 4x as a one-variable experiment.
- [x] Keep constant-bitrate seeking, `CLOSEST_SYNC`, datasource read size, LoadControl, and Push behavior unchanged during the 4x experiment.
- [x] Review `20260828_002208_player_full_matrix.json` and document the evidence in `MATRIX_REVIEW_20260828_002208.md`.
- [ ] Rebuild/install v0.5.66 Dev APK; verify `debugStatusVersion=10`.
- [ ] Run issue-only from `20260828_002208_player_full_matrix.json` and compare Media3 Pull/GSY Media3/GSY Auto Comskip recovery against the ~33-34 second baseline.
- [ ] Verify absolute seek-to-zero still recovers correctly with the 4x TS search window.
- [ ] If 4x does not improve recovery or regresses seek correctness, revert to 8x before testing constant-bitrate seeking as the next single variable.
- [ ] Capture a real server-originated Push seek before changing Media3 Push local-seek behavior.
- [ ] Re-run GSY legacy Exo issues under per-check isolation before modifying its Android backend.

## v0.5.64

- [x] Add 5/15/30-second crash/process probes during long operation recovery waits.
- [x] Add crash/process milestones to startup playback verification.
- [x] Stop a wait early when Dev-app process death/restart or a new Dev-specific crash signature is proven.
- [x] Classify backend startup playback failures separately from MCP infrastructure failures.
- [x] Enrich Media3/Exo/IJK/GSY-System player-error traps with the actual backend error identifier.
- [x] Trap/log Exo/Media3 Push flush/reprepare exceptions instead of silently swallowing them.
- [x] Review `20260827_221623_player_full_matrix.json`; record findings in `MATRIX_REVIEW_20260827_221623.md`.
- [ ] Rebuild/install v0.5.64 and verify `debugStatusVersion=10`.
- [ ] Rerun the same hardware Push/Pull matrix and inspect `longWaitProbes` at 5/15/30 seconds.
- [ ] For Media3 Pull ~33 s Comskip, use milestone I/O to decide one controlled A/B experiment: explicit Pull target-buffer bytes OR TS timestamp-search size; change one variable only.
- [ ] Re-run GSY/System and capture the new `player_error_<what>_<extra>`/setup error trap before attempting a behavioral System-backend rewrite.
- [ ] Separate PUSH debug-local seek stress from normal server-owned PUSH seek/Comskip semantics in the matrix report so Codex does not conflate them.

## v0.5.60

- [x] Add `--hardware-only` alias for `--decoders hardware` (21 cases).
- [x] Replace long player-matrix Android Comskip broadcast with short SageTV RIGHT/LEFT + host-side A/V-counter recovery.
- [x] Keep Comskip landing/marker position diagnostic-only.
- [ ] Device-run the 21-case hardware-only matrix and confirm Comskip RIGHT/LEFT no longer hits the ~60-second infrastructure error.

## v0.5.48

- [x] Replace the second startup `play_pause` with native SageTV `select` after `down`, so Start From Beginning is confirmed instead of resuming playback.
- [ ] Device-check that the recording visibly starts from the beginning before the direct debug seek/Comskip harness begins.
- [ ] Continue full player-matrix harness validation after startup selection is confirmed.

## v0.5.47

- [x] Make `--watchdog-ms` independent per media step rather than a total-process/transport limit.
- [x] Remove the fixed 90-second ADB transport truncation for long debug media checks.
- [x] Advertise/verify debug APK max watchdog capacity (`300000 ms`).
- [x] Make absolute seek inherit the player-matrix watchdog unless separately overridden.
- [x] Add `DOWN` after the first play action in Search recording startup.
- [ ] Rebuild/install v0.5.47 and visually confirm one Comskip step can remain under observation for the full requested 180000 ms before moving to the next step.
- [ ] Continue expanding the complete player harness toward correlated Android + SageTV server + FFmpeg evidence.

## v0.5.43

- [x] Replace standard startup `dev_send_sequence` call with explicit Search MCP steps.
- [x] Add `dev_open_search` with structured Search/text-input/IME readiness reporting.
- [x] Add `dev_wait_for_ime`.
- [x] Preserve IME fields in compact MCP state.
- [x] Update Media3 seek and Comskip matrices to pass Search text into the explicit startup path.
- [x] Keep `dev_send_sequence`, `sendkey`, and `sendtext` for diagnostics.
- [ ] Device-test `mcp-media3-comskip-matrix --modes pull --start-ms 0`.

## v0.5.42

- [x] Package the corrected `mcp_playback_test.py` with explicit `keyboardtext`.
- [x] Remove raw-character debug receiver text injection.
- [x] Remove the known-hanging in-app `KeyCharacterMap` / `dispatchKeyEvent()` text dispatcher.
- [x] Keep `keyboardtext`/`directtext` on the Android OS input-service path and preserve legacy `sendtext`/`sendkey` diagnostics.
- [ ] Rebuild/install and rerun Media3 Pull Comskip startup.

## v0.5.41

- [x] Replace hanging debug `View.dispatchKeyEvent()` Search text path with synchronized Android OS `input text`.
- [ ] Device retest Media3 Pull Comskip with v0.5.40 APK + v0.5.41 host/MCP scripts.

## v0.5.40 Android keyboard text path

- Normal automation must use `dev_input_text_keyboard(text)` / `keyboardtext <text>` after `hasTextInput=true` and verified `imeVisible=true`.
- Do not send raw character values directly with `MiniClientConnection.postKeyEvent(...)`; lowercase ASCII is not a valid Java/Sage keycode mapping.
- The debug APK generates Android virtual-keyboard `KeyEvent`s and dispatches them to the focused MiniClient view, so the existing Android key listener performs the same conversion as real Fire TV keyboard input.
- `dev_input_text_direct` / `directtext` are compatibility aliases to the Android-keyboard path. `sendkey` / `sendtext` remain diagnostic input-simulation tools.
- v0.5.40 changes Android shared/debug code; rebuild/install before device testing.

## v0.5.39 direct-control conversion

- [x] Add debug Android virtual-keyboard text input that dispatches to the focused MiniClient view without ADB `input text` or raw SageTV keycodes.
- [x] Add debug-direct IME hide so BACK is not injected just to close the keyboard.
- [x] Add direct Android player play/pause/stop controls.
- [x] Add direct relative seek and explicit forward/back skip MCP helpers using caller-supplied milliseconds.
- [x] Convert Media3 seek/resume matrix to direct relative player seeks and direct pause/play.
- [x] Add dedicated debug Comskip control that bypasses Android keys and arrow mappings while still allowing SageTV/STV to resolve marker targets.
- [x] Preserve legacy `sendkey`, `sendtext`, and command-based semantic suites for diagnostics.
- [ ] Device-build/install v0.5.39 and rerun Media3 Pull Comskip/seek matrices.

## v0.5.37 startup correction / next run

- [x] Fix `dev_app_status` false-negative when a Dev Activity is resumed but Fire OS `pidof` returns no PID.
- [x] Add `ps -A` process fallback and `runningSource` diagnostics.
- [x] Remove generic `launch_dev_app` from playback/session/Media3 automated startup.
- [x] Preserve clean-stop -> wake -> direct SageTV connect -> Android readiness flow.
- [ ] Re-run Pull Comskip without rebuilding if v0.5.36 APK is installed.
- [ ] Confirm direct connect launches `MiniClientOpenGLActivity` or `MiniClientGDXActivity`, Search reaches verified IME-visible state, text is entered, and Pull I/O diagnostics run.

## v0.5.36 device verification

- [ ] Rebuild/install the debug APK.
- [ ] Confirm Search reports `hasTextInput=true`, `imeVisibleKnown=true`, `imeVisible=true` before the test title is typed.
- [ ] Confirm BACK is followed by `imeVisibleKnown=true`, `imeVisible=false` before playback navigation continues.
- [ ] Re-run Media3 Pull Comskip diagnostics after Search/IME startup passes.

## v0.5.35 build repair / next run

- [x] Repair the v0.5.34 `DevTestReceiver.java` missing-helper compile failure.
- [x] Add regression assertions for `configuredValues(PrefStore)` and playback-health snapshot append.
- [ ] Rebuild/install the v0.5.35 debug APK.
- [ ] Rerun `./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>" --modes pull` and collect the v0.5.33 Pull I/O diagnostics.

## v0.5.34 deterministic startup / next run

- [x] Add app-owned debug `uiState` and `automationReady` status.
- [x] Add MCP wake, app-running status, force-stop, and clean-start controls.
- [x] Make fresh automation check whether the app is running before launch, request normal exit first, force-stop only on fallback, and verify stopped/running states.
- [x] Normalize stale SageTV Search/text-input context with direct SageTV `home` before native Search.
- [ ] Rebuild/install the v0.5.35 debug APK.
- [ ] Re-run the v0.5.33 Pull Comskip I/O diagnostic with explicit `--text` and confirm the console reports `automationReady=true` before Search.
- [ ] Use the resulting Pull I/O counters to continue the 3.6 s large-seek investigation.

## v0.5.33 next device test

- [ ] Rebuild/install the debug APK (Android/core source changed).
- [ ] Run Pull-only direct Comskip matrix with explicit `--text`.
- [ ] Compare `recoveryMs` with the new `pull I/O` line, especially `readWait` and `READs`.
- [ ] If readWait accounts for most latency, optimize Pull network/probe behavior; otherwise inspect Media3 extractor/rebuffer path.

## v0.5.32 Media3 Pull large-seek latency

- [x] Record v0.5.31 direct Comskip device result: Dynamic RIGHT 820 ms / LEFT 566 ms; Pull RIGHT 3630 ms / LEFT 3632 ms; all A/V recovery PASS.
- [x] Isolate latency as Pull + large-seek specific; normal v0.5.28 Pull FF/REW stayed below 2 seconds.
- [x] Increase Media3 Pull network read buffer only from 256 KiB to 1 MiB.
- [x] Preserve seek-map/timestamp-search, LoadControl, decoders, Dynamic/PUSH, Legacy Exo, and IJK.
- [ ] Rebuild/install debug APK.
- [ ] Run focused Pull Comskip matrix and compare recovery against ~3630 ms v0.5.31 baseline.
- [ ] If materially faster and still correct, keep the change; otherwise revert before broader backend work.

## v0.5.31 Comskip direct-command correction

- [x] Confirm manually that `mcp-send-sequence` + `command right` reaches the next Comskip point.
- [x] Confirm the corresponding backward command is native SageTV `left`.
- [x] Stop resolving normal video RIGHT/LEFT mappings for Comskip; those short-press mappings are FF/REW.
- [x] Route instrumented Comskip checks through direct SageTV `right` / `left` commands while preserving A/V recovery and landing diagnostics.
- [ ] Re-run `./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>"` on Fire TV.

## v0.5.30 explicit recording selection

- [x] Remove the hard-coded/default test recording title from native Search-based automation.
- [x] Require `--text` for playback, Media3 seek matrix, Media3 Comskip matrix, and Search test commands.
- [x] Require `text` in the MCP `dev_search_text` tool itself.
- [x] Fail immediately at argument parsing when the recording text is not supplied.
- [ ] Run the next Fire TV Comskip matrix with `--text "<recording search text>"`.

## v0.5.29 Media3 Comskip Push/Pull matrix

- [x] Record v0.5.28 Fire TV seek matrix as PASS for Media3 Dynamic and Pull.
- [x] Do not change Pull buffering/DataSource: all Pull seek recovery stayed below 2 seconds and pause/resume latency was common to both modes.
- [x] Add fresh-session `mcp-media3-comskip-matrix` using the proven native recording-start/UI-ready path.
- [x] Correct the Comskip matrix to use direct SageTV RIGHT/LEFT commands with real A/V recovery, while recording landing timeline/jump size without inventing unavailable marker metadata.
- [ ] Run `./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>"` on Fire TV.
- [ ] If both modes recover correctly, close Media3 Comskip Phase-A coverage and move to the next backend comparison before Phase B timeline-state work.
- [ ] Keep the ~5.3 s common pause/resume measurement as a separate follow-up; improve measurement granularity before changing player code.

## v0.5.28 Media3 Push/Pull matrix

- [x] Add a fresh-session Media3 Push/Dynamic vs Pull test harness.
- [x] Reuse the proven v0.5.27 stable-UI gate and exact native recording-start sequence.
- [x] Compare single FF, single REW, rapid mixed seek, and pause/resume using real A/V output health.
- [x] Record recovery timing in a persistent JSON report and auto-capture diagnostics when recovery exceeds 2 seconds.
- [x] Run `./dev.sh mcp-media3-matrix --server 192.168.10.175` on Fire TV — PASS in Dynamic and Pull.
- [x] Pull seek recovery was not repeatedly >2 seconds; no Pull buffer/load-control/player change is justified by v0.5.28.
- [x] Both modes passed normal seek recovery; close the Media3 Push/Pull seek gate and move to Comskip coverage before Phase B timeline-state work.

## v0.5.27

- [x] Fix the startup race where `mcp-playback-test` could send Search while the MiniClient UI was still loading.
- [x] Require a non-empty SageTV menu hint stable for 2000 ms before native playback-start input.
- [x] Apply the same ready-state gate to `mcp-session-test`.
- [x] Re-run `mcp-playback-test` on Fire TV and confirm Search is not sent until the SageTV UI is visibly ready — PASS.

## v0.5.26

- [x] Revert the temporary v0.5.25 Android/session-control rollback.
- [x] Add one end-to-end MCP playback-start test.
- [x] Address server `192.168.10.175` explicitly instead of relying on saved-list position.
- [x] Avoid creating another saved server entry (`save=false`).
- [x] Apply player/streaming/decoder settings before connection/playback.
- [x] Start `meet the press` through the exact native Search/key sequence only.
- [x] Verify real playback start after the sequence.
- [ ] Run the new test on Fire TV after rebuild/install.

## v0.5.24

- Added explicit multiline `mcp-send-sequence` workflow (`command`, `sendkey`, `sendtext`, `delay`).
- Next device test: reproduce Search -> text -> keyboard close -> navigation using only the explicit sequence.

## v0.5.23
- [x] Dismiss Android keyboard after MCP Search text injection before sending SageTV navigation keys.

## v0.5.18 MCP end-to-end session / play by name

- [x] Launch Dev MiniClient through MCP.
- [x] Apply player/streaming/decoding/GSY settings before video start.
- [x] Connect by saved server name, direct address, or last connected server.
- [x] Expose fixed Dev client ID/UI-context hint in one-shot Android snapshots.
- [x] Add `dev_play_video(video_name)` so the MCP caller only supplies a title.
- [x] Auto-discover Sagex Remote API from the connected server and resolve this MiniClient UI context.
- [x] Resolve exact/unique MediaFile title and reject ambiguous matches.
- [x] Invoke `Watch` in this MiniClient context and verify real A/V startup plus current MediaFile ID when available.
- [x] Add clean session exit and optional force-stop.
- [x] Convert `mcp-session-test` from STV navigation to `--video-name`.

## v0.5.16 Comskip automation
- [x] Resolve actual Android video left/right mappings for MCP Comskip tests.
- [x] Capture video/audio/full-output recovery landing timeline.
- [x] Add `dev_run_comskip_check` + `mcp-comskip-test`.
- [ ] If SageTV later exposes Comskip marker metadata to MiniClient, ingest marker start/end instead of requiring an optional expected target.

# SageTV MiniClient Dev Tasks — v0.5.15

Statuses: `[x]` implemented, `[~]` implemented but awaiting real Fire TV validation, `[ ]` pending.

## Baseline safety

- [x] Dev package isolated as `org.opensagetv.miniclient.dev.debug`.
- [x] Fixed SageTV ID `DEV001` (`44:45:56:30:30:31`).
- [x] Fresh install defaults to Dynamic streaming mode.
- [x] Dev installer uninstalls Dev package before fresh install.
- [x] Keep internal player telemetry disabled.
- [x] Keep `BaseMediaPlayerImpl` lifecycle frozen to the known-good source baseline.

## Four-player architecture

- [x] Centralized `PlayerBackend` / `PlayerFactory` used by GDX and OpenGL.
- [x] ExoPlayer Legacy 2.18.1 remains default and confirmed working.
- [x] Media3 1.11.0 confirmed working.
- [x] Keep `IJKPlayer` on the original SageTV IJK 0.8.8 Java/JNI/native runtime, isolated from GSY.
- [x] Add `GSYVideoPlayer` as backend #4 with independent Auto / Media3 / Android System / Legacy Exo selection.
- [x] Keep GSY `ex_so` / `gsyijkjava` out of the APK to avoid collision with legacy IJK.
- [x] Restore/package the original local IJK 0.8.8 AARs only for the legacy IJK backend.
- [~] Compile GSY artifacts in the user's Docker environment.
- [x] Validate IJK MPEG-2 hardware decoding on Fire TV `mantis`: GSY 13.1.0 still reproduces the MediaCodec `dequeueInputBuffer()` failure; v0.5.1 marks this path unsafe and falls back to FFmpeg software MPEG-2.
- [~] Re-test IJKPlayer and GSYVideoPlayer MPEG-2 playback on AFTMM/`mantis` with the v0.5.1 safety fallback.

## Shared Decoding Method

- [x] Shared values: Hardware / Software / Hardware Preferred.
- [x] Hardware is default.
- [x] ExoPlayer video codec selector applies the shared policy.
- [x] Media3 video codec selector applies the shared policy.
- [x] IJK MediaCodec flags apply the shared policy.
- [x] GSY advanced profile applies the shared policy.
- [x] Hardware Preferred enables Exo/Media3 decoder fallback; legacy IJK remains on its own compatible fallback path.
- [ ] Verify behavior for H.264, HEVC, MPEG-2, and any software-only test media on Fire TV.

## GSY advanced settings

- [x] MediaCodec sync.
- [x] Handle resolution changes.
- [x] Auto rotate.
- [x] Packet buffering.
- [x] Frame drop and minimum frames.
- [x] Probe size and analyze duration.
- [x] Accurate seek.
- [x] OpenSLES and infinite input buffer.
- [x] Hardware codec blacklist dialog.

## Build/test

- [x] JDK 17 / AGP 8.13.2 / Gradle 8.13 / compileSdk 36 Dev toolchain.
- [x] JDK 8 / SDK 29 baseline toolchain retained.
- [x] Exo 2.18.1 dependency graph strictly aligned.
- [x] Gradle root-file self-repair retained.
- [x] Add `test_valitdate_build_install_lanuch.sh`; stop on first failed step.
- [~] Run user Docker compile/install after applying the v0.5.1 playback fix.

## External diagnostics

- [x] `./dev.sh player-diag clear` + `./dev.sh player-diag <label>`.
- [x] Add repeatable MCP current-playback seek/rapid-skip/pause checks with one-shot state snapshots.
- [ ] Add fully unattended recording selection after a stable test recording/menu path is configured.

## v0.5.2 player isolation update

Legacy **IJKPlayer** and **GSYVideoPlayer** are separate. IJK uses the original local 0.8.8 Java/JNI AARs and never uses GSY. GSY has its own `gsy_player_engine` setting with Auto / Media3-Exo / Android System / Legacy Exo. Auto chooses System for SageTV custom PUSH/PULL data sources and Media3 for ordinary HTTP. GSY's modern IJK/ex_so runtime is intentionally not packaged because it collides with the legacy `tv.danmaku.ijk` namespace and `libijk*.so` names.
## Phase A device follow-up — v0.5.6

- [x] Record v0.5.5 Fire TV matrix for Dynamic/Pull across all player engines.
- [x] Fix GSY Auto/System released-Surface startup crash.
- [x] Fix ExoPlayer Legacy PULL DataSource range-length contract.
- [x] Fix Media3 PULL DataSource range-length contract.
- [x] Add focused v0.5.6 retest procedure.
- [ ] Re-test ExoPlayer Legacy Pull startup/seek matrix.
- [ ] Re-test Media3 Pull startup/seek matrix.
- [ ] Re-test GSY Auto + explicit System startup in Dynamic and Pull.
- [ ] Re-test GSY Media3/Legacy Exo Pull after shared DataSource fix.
- [ ] Phase B: common timeline/seek state based on the remaining Dynamic large-skip mismatches.
- [ ] Phase C: normalize PUSH/Dynamic server rebase vs local seek semantics.

## Phase A seek/timeline cleanup — v0.5.5

- [x] ExoPlayer Legacy seek-to-zero accepted.
- [x] Media3 seek-to-zero accepted.
- [x] ExoPlayer Legacy/Media3 UI-thread seek wait loops removed.
- [x] ExoPlayer Legacy/Media3 seek completion tied to seek discontinuity instead of arbitrary timeline changes.
- [x] IJK repeated-Pause frame-step corrected to approximately 33 ms.
- [x] Player-by-player verification checklist added.
- [ ] Run device verification from `PHASE_A_SEEK_TEST.md` and record backend differences.
- [ ] Phase B: introduce common pure-Java timeline/seek state only after Phase A device results are known.
- [ ] Phase C: normalize PUSH seek/rebase semantics after Phase B.


## Phase A second device follow-up — v0.5.7

- [x] Confirm v0.5.6 fixed Exo Legacy Pull startup (`READ_POSITION_OUT_OF_RANGE` no longer blocks startup).
- [x] Fix Exo Legacy Pull FLUSH resetting a successful seek to `0:00`.
- [x] Apply the same Pull FLUSH correction to Media3.
- [x] Make GSY Auto prefer Media3 instead of System for current SageTV streams.
- [x] Make GSY/System PUSH `MediaDataSource.readAt()` wait for actual data rather than return zero for non-zero requests.
- [x] Make GSY/System Pull MediaDataSource close idempotent and synchronize core Pull socket close.
- [ ] Device re-test Exo Legacy Pull rapid/zero/comskip after v0.5.7.
- [ ] Device re-test Media3 Pull after v0.5.7.
- [ ] Device re-test GSY Auto Dynamic/Pull; it should now follow Media3 behavior.
- [ ] Device re-test explicit GSY System Dynamic/Pull; failure must be graceful (no process/SIGABRT crash).
- [ ] Phase B: common timeline/seek state after the Pull reset regression is closed.
- [ ] Phase C: normalize PUSH/Dynamic server rebase versus local seek semantics.


## v0.5.8 device verification

- [ ] ExoPlayer Legacy Pull: confirm `Pull seek capability: seekable=true` and run rapid seek matrix.
- [ ] Media3 Pull: confirm `Pull seek capability: seekable=true` and run rapid seek matrix.
- [ ] GSY Auto Pull: verify it matches Media3 Pull behavior.
- [ ] If any still resolve to 0:00, capture player diagnostics before Phase B and record the seekability/duration/byte-open lines.


## v0.5.9 Pull resume-latency verification

- [x] Record v0.5.8 device result: Exo2/Media3/GSY-Auto Pull seek correctness passes; timeline no longer resets.
- [x] Preserve v0.5.8 8x MPEG-TS timestamp search and closest-sync seek policy.
- [x] Add configurable core Pull read buffer while retaining 32 KiB default for IJK/System.
- [x] Use 256 KiB Pull read buffer for Exo2/Media3 only.
- [x] Add Pull-only Exo load-control profile for faster resume after seek/rebuffer.
- [x] Add Pull playback-state/buffer diagnostics.
- [ ] Device-test visible resume latency after +30/-10, rapid sequence and large comskip.
- [ ] If stalls remain >2 seconds, capture player diagnostics while stalled and inspect playback state/buffer position before further tuning.
- [ ] Begin Phase B only after Pull resume behavior is acceptable or clearly isolated to decoder/render latency.


## v0.5.11 MCP playback automation

- [x] Add debug-source-only `DevTestReceiver`; do not expose it in release/main manifest.
- [x] Add one-shot player/config snapshot with media timeline, buffer, file position and video dimensions.
- [x] Add exact SageCommand injection independent of Android remote key mappings.
- [x] Add MCP seek-sequence and expected-delta validation tools.
- [x] Add `./dev.sh mcp-seek-test` for +30, -10, rapid mixed seek and pause/resume.
- [x] Capture diagnostics automatically for failed automated checks.
- [x] Rename visible `Dynamic` option to `Push/Dynamic`; keep stored preference `dynamic`.
- [x] Refresh Default Player / Decoding Method / Streaming Mode summaries when returning to Settings.
- [ ] Configure a deterministic "open known recording" navigation path for fully unattended backend × streaming matrix runs.
- [ ] Keep IJK small-window jitter/tearing as a visual/manual test until a safe rendering fix is isolated.


## v0.5.12 MCP calibration correction

- [x] Stop assuming SageCommand `ff` is always +30 seconds.
- [x] Calibrate the active server's primary FF and REW increments before semantic skip tests.
- [x] Correct rapid `+30,+30,-10,+60,-30` expected net movement from +50 s to +80 s.
- [x] Add FF/REW override options and paced-vs-rapid command delays.
- [ ] Re-run GSY Auto / Push-Dynamic with the corrected suite before treating rapid-skip output as a backend failure.


## v0.5.13 MCP calibration robustness

- [x] Prevent noisy +8.8/-11.1 second player landing from being learned as +9/-11 skip configuration.
- [x] Default FF/REW calibration to a 5-second preference quantum.
- [x] Add `--calibration-quantum-ms` override.
- [x] Allow closest practical command plans instead of aborting on non-divisible semantic targets.
- [x] Report semantic and represented rapid-plan totals separately.
- [x] Keep Android/player code unchanged.
## v0.5.14 MCP timeline measurement

- [x] Measure skip delta inside Android debug code rather than host-side before/after inference.
- [x] Expose explicit `sageTimelineMs` from the same value used by `MEDIACMD_GETMEDIATIME`.
- [x] Return before/after/raw/adjusted skip timeline values to MCP.
- [x] Use Android-side measurement for FF/REW calibration.
- [ ] Device-validate `./dev.sh mcp-seek-test` after rebuilding/installing the v0.5.14 debug APK.


## v0.5.15 MCP real-output health

- [x] Treat SageTV timeline as diagnostic context, not authoritative playback-health proof.
- [x] Add debug-only on-demand Exo2/Media3 renderer and AudioTrack health probe.
- [x] Capture actual video decoder name/type and renderer output counters around MCP FF/REW.
- [x] Capture Android AudioTrack state and playback-head advancement around MCP FF/REW.
- [x] Require video/audio to recover and remain advancing through a verification window.
- [x] Add Surface validity, buffering/loading, datasource, retry/error, buffer/file-position, and decoder lifecycle diagnostics.
- [x] Capture crash/audio/codec/surface/window/screenshot/state bundle on failed checks.
- [x] Make timeline calibration optional (`--calibrate-timeline`) instead of a default gate.
- [ ] Device-validate v0.5.15 health probe on GSY Auto/Media3 and Legacy Exo in Push/Dynamic and Pull.
- [ ] Add strict native-output probe for legacy IJK only if needed; do not destabilize its working playback path.


## v0.5.17 validation

- [ ] Rebuild/install debug APK.
- [ ] Re-run ExoPlayer Legacy / Pull `./dev.sh mcp-seek-test`; confirm backward REW no longer false-fails on AC-3 AudioTrack head reset.
- [ ] Re-run Media3 / Pull and GSY Auto / Pull. Confirm long MCP checks no longer disconnect/crash the MiniClient.
- [ ] If Media3/GSY still crash, preserve checkpoint + `player-diag` and move investigation into Media3 Pull runtime/datasource code.

### v0.5.21 MCP Search correction
- Default Search automation does not send keyboard Enter/Next after text entry. It sends Play first, then Down, Right, Right, Right, Play, Play.
## v0.5.38 debug time seek

The debug APK exposes a generic absolute time seek through MCP. Use `dev_seek_time(target_ms=...)` or `./dev.sh mcp-seek-time --target-ms N`; the target is always supplied by the caller (`0` = beginning). The Media3 Comskip matrix passes `--start-ms` into this same command before Comskip checks so test playback can start from a deterministic time without hard-coding the seek target inside Android. The older `dev_local_seek_absolute` remains for compatibility/backend isolation.



## v0.5.44 - Restore proven Search text timing
- Standard recording startup now restores the proven Android OS `adb input text` path (`dev_type_text` / legacy `sendtext`) immediately after SageTV reports `hasTextInput=true`.
- IME visibility remains instrumented, but is verified after text injection instead of gating text entry.
- Startup no longer uses `dev_sage_command_sequence` before playback; post-Search UI commands are issued individually with `dev_sage_command`, because the sequence measurement helper requires an active player timeline.
- No Android APK source changes in this update.

- [x] v0.5.45: eliminate false PASS when direct seek transiently reports 0 during Media3 seek/rebuffer.
- [ ] If v0.5.45 reports a stable-seek FAIL on device, use its final `health_playerPositionMs`, `sageTimelineMs`, and `serverAnchorMs` to determine whether Pull absolute seeking itself needs a data-source/player change.

## v0.5.46 complete MCP player harness
- [x] Make Comskip recovery watchdog configurable and default it to 180000 ms / 3 minutes.
- [x] Treat watchdog expiry as non-fatal observation with cause undetermined.
- [x] Add complete player configuration matrix including all GSY engines.
- [x] Add direct seek, pause/resume, and Comskip observations per configuration.
- [x] Add list/filter/exact-case controls for future Codex orchestration.
- [ ] Future: add correlated SageTV server-side evidence collectors keyed to matrix case/time window.
- [ ] Future: add FFmpeg/transcoder process/log/command telemetry keyed to the same case/time window.

## v0.5.49 Search text reliability
- [x] Restore exact old Search -> 50 ms -> Android OS text -> 50 ms -> BACK timing.
- [x] Remove readiness waits from the timing-sensitive injection path.
- [x] Stop treating empty `adb input text` stdout as evidence that the SageTV field received text.
- [ ] Device verify v0.5.49 recording startup before expanding the 63-case matrix run.

## v0.5.50 - paced Android text entry
- Recording Search text is now injected one character at a time through Android's OS input service instead of one whole fast phrase.
- Spaces use `KEYCODE_SPACE`; normal characters use `adb shell input text <char>`.
- New CLI option `--text-char-delay-ms` controls pacing for playback, Media3, Comskip, and full player-matrix harnesses. Default is 0, which uses the proven one-shot Android `input text` path; values 1-2000 ms enable the experimental per-character diagnostic mode.
- The full player matrix prints the active text-character delay so repeated cases are reproducible.


## v0.5.52 persistent ADB shell
- MCP runtime commands reuse one persistent `adb shell` for the entire MCP server/test run.
- `adb_session_status` reports persistent shell state, PID, restart count, and command count.
- Keep normal recording Search text at `--text-char-delay-ms 0`; paced character mode is diagnostic only.
- APK rebuild is not required.

## v0.5.53 matrix progress visibility
- [x] Print the exact check name and per-step watchdog before each long player-matrix operation.
- [x] Emit 10-second host-side wait heartbeats while blocking MCP calls are active.
- [x] Print persistent ADB shell PID/status at matrix startup.
- [ ] Device verify that a 180-second absolute-seek/Comskip observation shows continuous progress instead of looking hung.

- [x] v0.5.54: Replace intermittent Fire TV/ADB Search text entry in normal harness with MiniClient-native key event encoding matching KeyMapProcessor.

- [x] v0.5.55: Wake Fire TV with WAKEUP+HOME and replace primary `monkey` Dev launch with resolved MAIN/LAUNCHER `am start -W`.

### v0.5.56 MCP native text / IME behavior
MCP native SageTV text entry temporarily suppresses the Android/Fire TV soft keyboard before opening Search. The SageTV `hasTextInput` hint still works, but `showHideKeyboard(true)` is ignored while the debug suppression flag is active. If an IME was already visible it is hidden directly; otherwise no hide/BACK action is sent. Suppression is restored after the automated Search/start sequence, so manual app text entry remains unchanged. Debug snapshots report `imeSuppressedForDebug` (status version 5).

### v0.5.57 seek-coordinate correction
Direct debug seeks now use one coordinate system end-to-end: backend-local player milliseconds. `dev_seek_time(0)` is verified against `health_playerPositionMs` (or a local-position fallback), not SageTV's anchored timeline. Relative debug seeks also start from local player position before applying deltas. `sageTimelineMs` and `serverAnchorMs` remain diagnostic context only. This fixes false long watchdog waits where playback was healthy but the anchored timeline could never be near the local target.

Debug APK capability marker: `debugStatusVersion=6`. MCP direct seek tools reject older debug APKs so the full matrix cannot silently use the old anchored-coordinate relative seek implementation.
### v0.5.58 seek-verifier wrapper-state rule
Direct absolute-seek verification must not require `BaseMediaPlayerImpl.playerReady`, `seekPending`, or `flushed` to clear. Legacy Exo can leave `seekPending=true` indefinitely after a seek that is effectively a no-op (for example, seeking to 0 when already near 0) because no `DISCONTINUITY_REASON_SEEK` callback is guaranteed. Use backend-local position + backend READY/error state for the verdict. Keep wrapper flags diagnostic-only and report stale-sample counts. Host/MCP-only; debug status version 6 remains sufficient.
### v0.5.59 A/V-counter recovery rule

For player-matrix recovery tests, landing position is not a pass/fail criterion. After a seek or other media action, recovery is proven by the existing playback-health counters: rendered video advances and audio playback-head/render counters advance again. Wrapper flags and timeline/position remain diagnostic only. The CLI prints each step before execution and prints the A/V counter verdict after completion.



## v0.5.61 configuration selections / Fixed encoding
- [x] Rename user-facing streaming choices to Push / Pull / Fixed while preserving `dynamic` storage for Push.
- [x] Rename user-facing decoding choices to Hardware / Software / Fallback while preserving `hardware_preferred` storage for Fallback.
- [x] Pass every Fixed encoding/remux parameter through MCP/debug configuration.
- [x] Verify every Fixed parameter before playback in `mcp-player-matrix`.
- [x] Default Fixed matrix cases to forced encoding (`always`) with remux disabled (`off`).
- [ ] Device-build/install v0.5.61 and verify one Fixed case reports the selected `FIXED_PUSH_MEDIA_FORMAT` behavior from SageTV/FFmpeg.

## v0.5.62 host-polled matrix recovery
- [x] Use the same A/V-counter recovery loop for absolute seek, relative seeks, pause/resume, and both Comskip directions.
- [x] Make video/audio counter movement authoritative; keep wrapper/position/surface/error fields diagnostic-only.
- [x] Rewrite one dynamic WAIT line with the current blocking condition instead of adding heartbeat lines.
- [x] Restore detailed final per-step CLI counter/decoder/state output and Pull I/O deltas.
- [ ] Device-run hardware Push/Pull matrix and confirm every visibly recovered action advances to the next step immediately.

## v0.5.63 MCP/Codex exact-event timeline diagnostics

- [x] Keep A/V counter recovery as the only media-operation PASS/FAIL verdict.
- [x] Add diagnostic-only seek landing sanity; default tolerance +/-10000 ms and never fail recovery from it.
- [x] Expose SageTV `MEDIACMD_SEEK` target as `serverRequestedSeekMs`.
- [x] Expose server seek, flush, and Push anchor sequence/timestamp/age fields.
- [x] Preserve separate `serverAnchorMs`, backend-local `health_playerPositionMs`, and `sageTimelineMs` values for Codex reasoning.
- [x] Add debug-only exact-event trap ring with counter snapshots at Android playback events.
- [x] Trap backend seek invoke/return, seek callback/discontinuity, READY/state, playing, first frame, pause/play, flush/reprepare, completion, and errors.
- [x] Trap SageTV protocol seek/flush/play/pause and Push anchor events without blocking the media-command thread.
- [x] Add MCP `dev_player_events` / `dev_clear_player_events` and attach traps to each matrix action.
- [x] Prevent counter reset-to-zero from counting as recovered output.
- [x] Latch pre-action audio/video expectations so temporary metadata disappearance cannot false-PASS.
- [x] Change default slow-recovery classification threshold from 2000 ms to 5000 ms.
- [ ] Rebuild/install the v0.5.63 debug APK and confirm `debugStatusVersion=9` on Fire TV.
- [ ] Re-run hardware Push/Pull matrix and compare host-polled recovery time to exact event-trap timestamps/counters.
- [ ] Investigate Media3-family long Comskip recovery only after v0.5.63 event traps identify where counters/state stop and restart.


## v0.5.65 Android player follow-up from 20260827_232446 matrix

- [x] Add `mcp-player-matrix --issues-only` built-in focused profile.
- [x] Add `--issues-only <previous-report.json>` automatic issue-set derivation with per-case operations and startup-only cases.
- [ ] Media3 PUSH ownership experiment: verify a server-originated `MEDIACMD_SEEK` in PUSH is followed by server FLUSH/anchor, then test suppressing local `player.seekTo()` in PUSH. Do not change this permanently until that server sequence is proven.
- [ ] Media3 Pull experiment A: reduce TS timestamp search multiplier from 8x to 4x only; keep CBR seeking, CLOSEST_SYNC, LoadControl and 256 KiB network buffer unchanged. Rerun issue-only.
- [ ] If experiment A preserves correctness but does not improve latency, restore 8x and disable constant-bitrate seeking only; rerun issue-only.
- [ ] Add per-open `DataSpec.position/length` and seek-map/extractor diagnostics if the repeatable 191 x 256 KiB (~50 MB) post-Comskip read burst remains.
- [ ] GSY/System: add `MediaDataSource` readAt/getSize/error counters and compare the same MPEG-2/AC3 recording as a local file. If local file works but custom MediaDataSource fails, investigate a local HTTP/pipe bridge instead of retrying MediaPlayer blindly.
- [ ] After the experiments, consider extracting small shared Exo2/Media3 Pull policy helpers; do not merge backend implementations while behavior is still being isolated.
