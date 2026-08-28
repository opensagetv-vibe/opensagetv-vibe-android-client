## v0.5.75
- Automated MCP/player test wrappers now default to client ID `44:45:56:30:30:31` (`DEV001`) when `--client-id` is omitted; explicit `--client-id` wins.
- The test wrapper uses client-ID ensure semantics, avoiding an app restart when the requested test ID is already configured/active.

- Restored the original OpenSageTV client-ID behavior: generate a random six-character ID only when no saved `client_id` exists, persist it, and reuse it afterward.
- Restored original `MiniClientConnection` handling so the normal ID is used unless a connection-specific `ServerInfo.macAddress` override is configured.
- Removed the source-level fixed `DEV001` / `44:45:56:30:30:31` enforcement and re-enabled the Android client-ID setting. Existing installs retain their already-saved ID across this upgrade.
- Added debug receiver/MCP client-ID read/write controls plus `./dev.sh client-id --show|--set ID|--generate`. Plain text `DEV001` maps to `44:45:56:30:30:31`; setting/generating via the CLI restarts the Dev app so the next connection uses the selected ID.
- Added configured-versus-active client-ID diagnostics to `dev_player_state`.
- Added a mandatory test rule: on fresh install/cleared data, complete first-time setup manually before running **any** automated MCP/player test.
- Bumped debug control contract to `debugStatusVersion=14`; Android/core/debug code changed, so rebuild/install the Dev APK.

## v0.5.74

- Added persistent **MediaCodec Queueing Mode** settings to both Media3 and legacy ExoPlayer player settings. Choices are `Sync (Recommended)`, `Auto`, and `Async`.
- Promoted `sync` to the normal/default codec queueing mode at the user's request, based on the latest Media3 Push matrix where Sync recovered in 3.768 s versus Auto at 4.967 s and Async at 7.103 s.
- Saved player preference is used as the baseline on each new player. Explicit MCP runtime tuning still overrides the saved preference for the active tuning session; `reset` clears that override so the next player returns to the saved preference.
- Runtime tuning/matrix compiled defaults now report `codecMode=sync`; added the `auto_codec` named profile so Auto remains easy to A/B against the new default.
- Android behavior/settings changed, so one debug APK rebuild/install is required. Debug control contract remains `debugStatusVersion=13`.

## v0.5.73

- Reduced runtime tuning-matrix startup overhead with a fast replay path. Combination 1 still uses the full deterministic clean-start + Search workflow, then MCP caches the exact SageTV MediaFile ID selected by that playback.
- Combinations 2+ default to player/session isolation without force-stopping the Android app or repeating Search: disconnect SageTV session, apply tuning/config, reconnect, and invoke Sagex `Watch` on the cached MediaFile ID. This still creates a fresh player instance for every combination.
- Added MCP tools `dev_current_media_file` and `dev_play_media_file_id` for exact deterministic replay without media-library enumeration.
- Added `--startup-mode fast|isolated` (default `fast`). `isolated` preserves the old force-stop/reconnect/Search behavior for every combination.
- Fast replay automatically falls back to the full isolated startup for that combination if reconnect/replay verification fails.
- Added `--fast-ui-stable-ms` (default 500 ms) and per-combination `startupMs` / `startupPath` report fields plus `fastReplayFallbackCount`.
- Host/MCP/test only; Android `debugStatusVersion` remains 13 and no APK rebuild is required.

## v0.5.72

- Fixed `mcp-player-tuning-matrix` startup: it now calls MCP `adb_connect` before the first device-backed tool.
- Added an `adb_session_status` preflight so an unavailable device fails once before the tuning loop instead of producing one `INFRA_ERROR` per combination.
- Host/test only; Android `debugStatusVersion` remains 13 and no APK rebuild is required.

# v0.5.71 - Combined Push/Pull runtime tuning matrix

- `mcp-player-tuning-matrix --streaming` now accepts comma-separated modes such as `push,pull` or `pull,push`.
- A combined run executes each streaming mode with fresh playback per tuning combination and records the streaming mode on every result/ranking row.
- Push automatically ignores Pull-only tuning dimensions: TS timestamp search, Pull read size, Pull load-control thresholds, Pull seek-recovery settings, and Pull directional seek policy/threshold.
- From the runtime tuning surface, Push varies only the effective MediaCodec queueing mode (`auto`, `async`, `sync`), avoiding duplicate tests for settings that the Push path never reads.
- Equivalent named profiles are deduplicated automatically for Push.
- Reports now include `streamingModes`, `combinationCountByStreaming`, and `ignoredTuningDimensionsByStreaming`.
- The example `TS 4,8 x seek closest,next x read 256,512 x codec auto,async,sync` with `--streaming push,pull` produces 24 Pull + 3 Push = 27 meaningful tests.
- Host/test/docs only. The installed v0.5.70 tuning-capable APK remains valid at `debugStatusVersion=13`; no APK rebuild is required for v0.5.71.

# v0.5.70 - Dev runtime player tuning + MCP tuning matrix

- Added `PlayerRuntimeTuning`, an in-memory override layer whose defaults exactly preserve v0.5.69 behavior and reset on process restart.
- Media3 and legacy Exo2 can now read runtime TS-search, Pull read-size, load-control, seek-policy, seek-recovery, directional-threshold, and codec queueing settings.
- Added debug receiver operations `tuning` / `tuning_get`; `dev_player_state` reports all tuning values.
- Added MCP tools `dev_set_player_tuning` / `dev_player_tuning`.
- Added `./dev.sh mcp-player-tune` for direct runtime adjustment and `mcp-player-tuning-matrix` for fresh-playback Cartesian/profile sweeps with A/V recovery ranking.
- Tuning matrix supports `--slow-recovery-ms` / `--slow_recover_ms`.
- Added named profiles: `default`, `next_sync`, `directional`, `large_read`, `async_codec`, `sync_codec`, `no_reprepare`, `fast_buffer`.
- Bumped debug contract to `debugStatusVersion=13`. One APK rebuild is required to install the hooks; tuning iterations after that do not require recompilation.

# v0.5.69 - Backend-specific recovery corrections + configurable slow threshold

- Media3 Pull returns to `CLOSEST_SYNC` for seek policy. The v0.5.67 direction-aware policy did not improve Media3's long MPEG-2 Pull Comskip path.
- Disabled Media3's blind 10-second Pull reprepare fallback. The 20260828_015224 matrix proved it could discard useful decode/extractor progress and stretch ~35 second recovery to ~44 seconds. Legacy Exo2 keeps its one-shot fallback because its affected forward Comskip recovered before the 10-second trigger.
- Kept legacy Exo2 directional Pull seeking (`NEXT_SYNC` forward / `PREVIOUS_SYNC` backward) because the isolated forward Comskip improved from a ~50.6 second watchdog to ~8.5 seconds.
- Increased only Exo2 Pull network block size from 256 KiB to 512 KiB. New Exo2 telemetry showed synchronous SageTV READ wait was a large fraction of the remaining recovery; Media3 remains 256 KiB.
- GSY `system` no longer terminates the recording through the known-broken Android `MediaDataSource` bridge. The legacy preference remains accepted but now falls back to the SageTV-aware Media3 delegate; the settings label makes the fallback explicit.
- Added matrix CLI aliases `--slow-recovery-ms`, `--slow_recover_ms`, and `--slow-recover-ms`. The selected value is still written to `slowRecoveryMs` in the report.
- Bumped debug contract to `debugStatusVersion=12`; rebuild/install the Dev APK before evaluating v0.5.69 Android changes.

# v0.5.68 - Focused retest exclusions

- Added `mcp-player-matrix --exclude-players` so issue-only reruns can omit entire unchanged player families.
- Added `--exclude-gsy-engines` so individual GSY engines such as `system` can be skipped while retaining GSY Auto/Media3/legacy Exo coverage.
- Added `--exclude-case-id` for exact case-level exclusions.
- Exclusion metadata is written under `retestExclusions` in every matrix report, including the exact generated case IDs removed from the run.
- `--list-cases` now reports the number of excluded cases.
- Codex guidance now says unchanged abnormal backends should be excluded from focused reruns unless they are intentionally being used as a comparison/control.
- Host/test/docs only. Android playback code and `debugStatusVersion=11` are unchanged from v0.5.67; no APK rebuild is required solely for v0.5.68.

# v0.5.67 - Direction-aware Pull seeks + one-shot recovery

- Restored Media3 Pull `PULL_TS_TIMESTAMP_SEARCH_MULTIPLIER` to 8x. The v0.5.66 4x experiment did not materially improve the repeatable ~35 second Media3 Pull Comskip recovery.
- Added direction-aware Pull seek policy to Media3 and legacy Exo2. Seeks >=2 seconds forward use `NEXT_SYNC`; seeks >=2 seconds backward use `PREVIOUS_SYNC`; smaller seeks retain `CLOSEST_SYNC` so frame-step/small adjustments do not jump a full GOP.
- Added a one-shot 10 second Pull seek recovery watchdog. If the same seek generation remains `STATE_BUFFERING`, the player reprepares the existing MediaSource at the target once and preserves `playWhenReady`. Newer seeks, READY/first-frame recovery, errors, and release cancel the pending recovery.
- Added exact traps: `pull_seek_policy_*`, `pull_seek_recovery_armed`, `pull_seek_reprepare_before/after/error_*`.
- Hardened Media3/Exo2 Pull DataSource reads: non-zero reads now fail deterministically instead of returning an invalid zero when the backing source is closed or unexpectedly returns zero bytes.
- Added cumulative READ/open diagnostics to `Exo2PullDataSource`, matching Media3, so server/socket time can be separated from Exo2 decoder/extractor time.
- Bumped debug status contract to 11 and matrix APK requirement to v0.5.67.

# v0.5.66 - Isolated issue reruns + Media3 Pull 4x TS seek experiment

- `--issues-only` now gives every selected operation its own full clean playback session instead of sharing a case session and restarting only after a failure.
- Added per-operation `checkStartups` and `issueCheckIsolation=fresh_playback_per_operation` report metadata.
- A playback failure while preparing an isolated operation is now `STARTUP_PLAYBACK_FAILED` / `STARTUP_PLAYER_CRASHED`, not a misleading MCP `INFRA_ERROR`.
- Report-derived issue selection understands per-check startup failures and the `PER_CHECK_ISOLATED` case startup marker.
- Changed only Media3 Pull `PULL_TS_TIMESTAMP_SEARCH_MULTIPLIER` from 8x to 4x as the first controlled experiment for the repeatable ~33-34 second MPEG-TS Comskip recovery.
- Constant-bitrate seeking, `CLOSEST_SYNC`, datasource read sizing, LoadControl, and all Push behavior remain unchanged so the 4x result is attributable.
- Added `MATRIX_REVIEW_20260828_002208.md` documenting the latest issues-only evidence and the deliberate non-changes.
- Android source changed; rebuild/install the Dev APK before evaluating the Media3 Pull experiment. Debug contract remains `debugStatusVersion=10`.

# v0.5.65 - Issue-only matrix reruns + v0.5.64 Android review

- Added `mcp-player-matrix --issues-only` to rerun only the current known abnormal hardware Push/Pull cases and operations.
- Added `mcp-player-matrix --issues-only <previous-matrix.json>` to derive the exact startup failures and non-`RECOVERED` operations from a prior report, so focused retesting automatically shrinks as fixes land.
- Issue-only execution supports startup-only cases (currently GSY/System Push and Pull) and per-case operation lists instead of applying one global check list to every selected backend.
- The current known profile from `20260827_232446_player_full_matrix.json` contains 9 configurations, 16 media operations, and 2 startup-only checks.
- Added `MATRIX_REVIEW_20260827_232446.md` with evidence-ranked Android recommendations. No speculative Android playback behavior was changed in v0.5.65.
- Host/test/docs only; debug APK `debugStatusVersion=10` from v0.5.64 remains current and no APK rebuild is required solely for v0.5.65.

# v0.5.64 - Long-wait crash probes + evidence-driven error diagnostics

- Added 5/15/30-second crash/process probes to operation recovery waits and startup playback verification.
- New Dev-app crash signatures, process death, or PID restart stop the wait early and are classified as `PLAYER_CRASHED` / `STARTUP_PLAYER_CRASHED`.
- Milestone records preserve compact player state, A/V counters, Pull I/O counters, event-trap state, and crash/process evidence for Codex.
- Dev-specific crash-signature fingerprints prevent unrelated Android crash-buffer changes from false-triggering a player crash.
- Reclassified player/backend startup failures such as GSY/System as media startup failures instead of MCP infrastructure failures.
- Enriched Android player-error traps with Media3/Exo error-code names and IJK/System `what`/`extra` values; setup/surface errors are also identified.
- Exo/Media3 Push flush/reprepare exceptions are logged and trapped instead of being silently swallowed.
- Bumped debug snapshot contract to `debugStatusVersion=10`; rebuild/install is required.
- Added `MATRIX_REVIEW_20260827_221623.md` documenting the four Media3-family Push local-seek watchdogs, the repeatable ~33-second Media3 Pull Comskip behavior, and GSY/System startup evidence.

# v0.5.63 - Exact-event MCP traps + timeline diagnostics

- Added debug-only exact-event playback traps for Codex/MCP. Traps capture the same video-render, audio-render, AudioTrack-head, decoder lifecycle, player-state, and surface evidence used by `PlaybackHealthProbe` at the moment important Android events occur.
- Added event traps around backend seek invocation/return, seek completion/discontinuity, player prepared/READY/state changes, is-playing changes, first rendered video frame, pause/play, push flush/reprepare, completion, and player errors. Existing Exo/Media3 listener objects are reused; no continuous AnalyticsListener or background telemetry loop is installed.
- Added non-blocking SageTV protocol traps for server seek, server flush, server play/pause, and post-flush server anchor establishment. The protocol-event timestamp is recorded immediately and the counter snapshot is captured on the Android UI thread with `snapshotLagMs` reported.
- Added MCP `dev_player_events` and `dev_clear_player_events`. The matrix clears the 32-event ring before each media action and attaches the resulting exact-event history to that check's JSON result.
- Debug snapshot contract is now `debugStatusVersion=9` and exposes `serverRequestedSeekMs`, server seek/flush/anchor sequence + monotonic timestamps/ages, server seek wall time, player-local position, SageTV-reported timeline, and event-trap summary.
- A/V output recovery remains the only seek/skip/pause/Comskip PASS/FAIL verdict. Seek landing is diagnostic-only with a default +/-10 second sanity tolerance (`NORMAL`, `WARN`, `SUSPICIOUS`) and never fails recovery.
- Hardened host A/V-counter recovery so a counter reset to zero cannot false-PASS. A reset only counts after the replacement counter has advanced above zero. Video/audio expectations are latched from the pre-action state so transient decoder/format disappearance cannot create a vacuous PASS.
- Raised the matrix's default `SLOW_RECOVERY` threshold from 2 seconds to 5 seconds so normal ~3 second host/MCP polling overhead is not mislabeled as slow; the threshold remains configurable.
- Android debug APK rebuild/install is required for status version 9 and exact-event traps.

# v0.5.62 - Host-polled A/V recovery + live single-line watchdog status

- Reworked the full player matrix so every post-startup media action uses one common host-side A/V recovery loop over the persistent ADB/MCP session.
- Absolute seek, forward/backward relative seek, pause/resume, and Comskip RIGHT/LEFT now PASS as soon as the expected video/audio output counters advance again.
- Seek/comskip landing position, wrapper READY/isPlaying/seekPending/flushed state, surface validity, buffering/loading, and player errors are diagnostic-only and cannot block PASS while real A/V counters are moving.
- Removed long blocking matrix calls to `dev_run_relative_seek_check`/`dev_run_comskip_check`; actions are issued quickly and `dev_player_state` is polled sequentially.
- Watchdog progress now rewrites one terminal line dynamically instead of appending repeated WAIT lines. The live line reports `waitingFor`, video/audio advancement, playback state, surface validity, and current error.
- Restored detailed per-step CLI output after completion: A/V counter before/after/delta values, wrapper state (diagnostic-only), decoder names/kinds, and Pull I/O deltas when available.
- Added Pull data-source counters to compact MCP player snapshots so host-side recovery diagnostics retain READ/open/wait/error metrics.
- Host/MCP-only; debug APK status version 8 remains sufficient and no APK rebuild is required.

# v0.5.60 - Hardware-only selector + host-side Comskip recovery

- Added `--hardware-only` as a convenience alias for `--decoders hardware`; hardware-only full-matrix coverage is 21 cases.
- Existing `--decoders` remains available for explicit decoder selection.
- Reworked player-matrix Comskip checks to send a short native SageTV `right`/`left` command and then use host-side A/V-counter recovery.
- Comskip no longer holds one Android `goAsync()` broadcast open for the full media watchdog, avoiding the ~60-second broadcast/transport failure seen while video was already playing.
- Comskip landing position remains diagnostic-only; recovery verdict is video/audio counters advancing.
- Because Comskip infrastructure errors should no longer be created by the long broadcast path, the same configuration session can continue through RIGHT and LEFT without an unnecessary restart when playback remains healthy.
- Host/MCP-only; no APK rebuild is required.

# v0.5.59 - A/V-counter recovery verdict

- Simplified direct absolute-seek verification: the matrix no longer cares where a seek lands.
- A seek/recovery passes when real video and audio output counters resume advancing.
- Player position, target tolerance, wrapper seek/readiness flags, and SageTV timeline are diagnostic-only for this verdict.
- Restored detailed CLI health output after every media step (`videoAdvancing`, `audioAdvancing`, `stillPlaying`, `outputHealthy`, recovery time).
- Keeps the full per-step CLI sequence visible: absolute seek, forward/backward seek, pause/resume, Comskip right/left.

# v0.5.58 - Seek verifier ignores stale wrapper flags

- Fixed another false long wait in `dev_seek_time`: Legacy Exo can leave `BaseMediaPlayerImpl.seekPending=true` after a no-op/same-position seek because Exo may not emit a seek-discontinuity callback.
- Direct seek PASS/FAIL now uses the backend-local player position plus the backend's own READY/error state. `playerReady`, `seekPending`, and `flushed` remain diagnostic only and no longer block a valid seek result.
- Added counters for stale wrapper-state samples so reports expose `stale_seek_pending_samples`, `stale_flushed_samples`, and `stale_player_ready_false_samples`.
- Host/MCP-only; debug APK status version 6 remains valid and no APK rebuild is required.

# v0.5.57 - Local player-position seek verification

- Fixed direct absolute-seek verification to compare against the backend-local player position used by `MiniPlayerPlugin.seek()` instead of SageTV's server-anchored display timeline.
- Fixes false 180-second waits in Dynamic mode where video was already playing but `serverAnchorMs + playerPositionMs` could never approach a target such as 0 ms.
- Fixed direct relative-seek debug commands to calculate targets from local player position instead of the anchored SageTV timeline.
- SageTV timeline and server anchor remain in diagnostics, but are no longer used as the coordinate system for direct player seeks.
- Android debug source changed; rebuild/reinstall is required.
- Android debug status is now version 6; direct seek tools reject older debug APKs to prevent mixed host/APK coordinate behavior.

# v0.5.55 - Fire TV HOME wake + direct app launch


## v0.5.56 - Debug/MCP IME suppression

- Added debug-only Android IME suppression for MCP native MiniClient text entry.
- MCP enables suppression before opening SageTV Search, so `hasTextInput` remains available without showing the Fire TV keyboard.
- Native SageTV text entry no longer needs Android keyboard focus.
- Keyboard dismissal is conditional: the IME is hidden directly only when it is actually visible; no BACK key is injected.
- Suppression is restored after the Search/start sequence so manual app behavior is unchanged.
- Debug status version 5 reports `imeSuppressedForDebug`.

- Fire TV wake now sends `KEYCODE_WAKEUP` followed by `KEYCODE_HOME`, so Dreaming/screensaver state is exited before automation proceeds.
- `./dev.sh launch` no longer relies on `monkey` as the primary launcher. It resolves the Dev package MAIN/LAUNCHER activity and starts it with `am start -W`.
- `monkey` remains only as a last-resort fallback.
- Fixes Fire TV builds where `monkey` exits 251 with `SYS_KEYS has no physical keys`.
- Host/MCP-only; no APK rebuild required.

# v0.5.51 - Restore one-shot Search text for matrix reliability

- Restored normal recording-name entry to the proven one-shot Android `adb input text` path.
- `--text-char-delay-ms` now defaults to `0`; positive values explicitly opt into experimental per-character diagnostics.
- Avoids visible Fire TV keyboard navigation/highlight behavior caused by stretched character-by-character injection.
- Applies consistently to playback, Media3, Comskip, and full player-matrix startup.

# v0.5.47 - true per-step watchdog + startup DOWN

- Fixed the 3-minute media watchdog so it is actually applied independently to each media action.
- Android debug recovery checks now advertise/support `MAX_RECOVERY_WATCHDOG_MS=300000` and snapshots expose `debugStatusVersion=3` plus `maxRecoveryWatchdogMs`.
- MCP refuses to trust a long watchdog when the installed APK advertises a smaller limit, preventing false `180000 ms` labels from an older APK.
- ADB debug broadcasts now derive their shell timeout from each action's recovery + verification window instead of using the old fixed 90-second transport timeout.
- Comskip and direct relative-seek results report requested/applied watchdog values and step elapsed time.
- Complete player harness applies a fresh `--watchdog-ms` to every media check; absolute seek now inherits the watchdog unless `--seek-timeout-s` is explicitly overridden.
- Recording-start navigation now sends `DOWN` after the first `play_pause`, before the second `play_pause`, as requested.
- Android debug source changed; rebuild/reinstall is required.

# v0.5.43 - explicit Search/IME startup diagnostics

- Removed `dev_send_sequence` from the normal Media3 playback startup path.
- Added `dev_open_search`, which sends the native SageTV Search command and waits for the SageTV text-input state plus verified Android IME visibility.
- Added `dev_wait_for_ime` and preserved IME fields in compact MCP status.
- Standard Search startup now calls Android OS text input, direct IME hide, verified IME hidden, then direct SageTV UI commands as separate MCP operations.
- Updated both Media3 seek/resume and Comskip matrices to use the shared explicit Search startup.
- Added phase-specific errors so Search-command failures are distinguishable from text-input and keyboard failures.
- Host/MCP-only; no Android source changed in this release.

# v0.5.42 - keyboard text packaging and dead-path cleanup

- Fixed the v0.5.41 overwrite package omission that left `scripts/mcp_playback_test.py` on the older `directtext` sequence in existing projects.
- Standard recording startup now explicitly uses `keyboardtext`, which routes through Android OS `input text` only after `waittextinput` and verified `waitimevisible`.
- Removed the obsolete debug-receiver `input_text` implementation that posted raw character values as SageTV keycodes.
- Removed the v0.5.40 in-app `KeyCharacterMap` / `dispatchKeyEvent()` debug text dispatcher because that path can block the debug BroadcastReceiver.
- `directtext` remains a host/MCP compatibility alias for `keyboardtext`; `sendtext` and `sendkey` remain diagnostic input primitives.
- Android source changed to remove the dead debug path, so rebuild/reinstall is required for a source-consistent v0.5.42 APK.

# v0.5.41 - Android OS keyboard input synchronization

- Fixed the v0.5.40 Search hang where debug-receiver-side `View.dispatchKeyEvent()` could block indefinitely after `automationReady=true`.
- `dev_input_text_keyboard` / `keyboardtext` now use Android's OS `input text` service after the automation has positively verified the Fire TV IME is visible.
- Preserved `sendtext`, `sendkey`, and `mcp-send-sequence` diagnostic primitives. `directtext` remains a compatibility alias.
- No Android source change in this release; no APK rebuild is required solely for v0.5.41.

# v0.5.40 - Android virtual-keyboard text injection

- Fixed v0.5.39 debug text entry: raw `MiniClientConnection.postKeyEvent((int) ch, ...)` used character values as keycodes, so lowercase letters could be interpreted as unrelated/numeric remote keys.
- Debug `input_text` now requires the SageTV text field and visible IME, generates Android `KeyEvent`s using `KeyCharacterMap.VIRTUAL_KEYBOARD`, and dispatches them to the currently focused MiniClient view.
- This routes text through the existing `MiniClientKeyListener` / `KeyMapProcessor`, matching real Android keyboard handling and preserving the correct Java/Sage keycode + Unicode character conversion.
- Added MCP `dev_input_text_keyboard(text)` and `keyboardtext <text>` sequence action. `dev_input_text_direct` / `directtext` remain compatibility aliases but now use the Android virtual-keyboard path.
- Legacy `sendtext` and `sendkey` remain available for diagnostics.
- Standard playback/Search automation now uses `keyboardtext`.
- Android shared/debug source changed; rebuild/reinstall is required.

# v0.5.39 - direct Android/MiniClient debug controls

- Added debug-only direct text input through `MiniClientConnection.postKeyEvent(...)`; normal automation no longer requires ADB `input text`.
- Added direct IME hide through the resumed MiniClient Activity, avoiding BACK-key injection.
- Added direct player `play`, `pause`, `stop`, absolute seek, relative seek, and explicit forward/back skip MCP controls.
- Added instrumented direct relative-seek checks and converted the Media3 Push/Pull seek matrix away from SageTV FF/REW commands.
- Converted Media3 pause/resume measurement to direct `MiniPlayerPlugin.pause()` / `play()`.
- Added dedicated Android debug Comskip and Comskip-check operations. They post SageTV RIGHT/LEFT internally via `EventRouter` because commercial-marker targets are server/STV-owned; no Android key or configurable arrow mapping is used.
- Standard Search/startup sequence now uses direct MiniClient text + direct IME hide + direct SageTV commands where possible. Legacy `sendkey` / `sendtext` sequence actions remain supported.
- Added `directtext` and `hideime` actions to `mcp-send-sequence`.
- Updated tests to cover the new direct-control broadcasts and sequence parser behavior.
- Android source changed; rebuild/reinstall is required.

# v0.5.38 - generic debug seek-to-time command

- Added debug-only Android `seek_time` control with a required caller-supplied `target_ms`; `0` means beginning and any non-negative millisecond target is supported.
- Added MCP `dev_seek_time(target_ms, tolerance_ms, timeout_s)`, which commands the Android player then verifies the SageTV-visible media timeline reaches the passed target.
- Kept `dev_local_seek_absolute` / `seek_absolute` as compatibility aliases for older backend-isolation tests.
- Added `./dev.sh mcp-seek-time --target-ms N` for direct copy/paste testing.
- Media3 Comskip matrix now performs the generic debug time seek before Comskip checks; `--start-ms` controls the passed target and defaults to `0` for deterministic beginning-of-recording tests.
- No production behavior changed; the new seek control remains debug-build-only.

# v0.5.37 - direct-connect automation startup and robust app status

- Fixed a Fire TV false-negative where `pidof org.opensagetv.miniclient.dev.debug` returned no PID even though Android reported a resumed Dev `ServersActivity`; `dev_app_status` now uses `pidof`, `ps -A`, and resumed-Activity evidence and reports `runningSource`.
- Fresh playback/session/Media3 matrix automation no longer calls generic `launch_dev_app` after clean-stop. That launcher enters `ServersActivity`; automation now goes directly: clean stop -> wake -> debug config -> `dev_connect_server` -> verify app/UI -> readiness gate.
- `launch_dev_app` remains available as a manual MCP control only.
- Added regressions for resumed-Activity and `ps` process detection, plus assertions that automated playback/session/matrix scripts do not reintroduce generic launcher startup.
- Host/MCP-only update; no Android source changed from v0.5.36, so no APK rebuild is required if the v0.5.36 debug APK is already installed.

# v0.5.36 - verify Android IME before and after Search text entry

- Android debug status now separates the MiniClient keyboard request from actual on-screen IME visibility: `imeRequested`, `imeVisibleKnown`, and `imeVisible`. A known `imeVisible=false` explicitly verifies that the keypad is not displayed.
- `mcp-send-sequence` adds `waitimevisible` and `waitimehidden` in addition to `waittextinput`.
- Native recording startup now waits for the Search text field **and** visible Fire TV/Android keyboard before `sendtext`, then waits for the keyboard to be verified hidden after BACK.
- `debugStatusVersion` is now 2.

# v0.5.35 - Android debug receiver compile repair

- Fixed the v0.5.34 Android debug build failure in `DevTestReceiver.java`. The packaged v0.5.34 receiver referenced `configuredValues(PrefStore)` and `appendPlaybackHealth(...)` without definitions, causing `:android-tv:compileDebugJavaWithJavac` to fail.
- Restored the original `configuredValues(PrefStore)` helper and directly appends `PlaybackHealthProbe.capture(player).compactWire("health_")` in the debug snapshot path.
- Added static regression assertions that the configuration helper and playback-health snapshot implementation are present and that the dangling `appendPlaybackHealth(...)` call cannot return.
- v0.5.34 deterministic startup/readiness behavior and v0.5.33 Pull seek I/O diagnostics are otherwise unchanged.
- Android debug source changed; rebuild/reinstall is required.

# v0.5.34 - deterministic Android/MCP automation startup

- Fixed the startup race exposed when a prior failed run left SageTV on `Search - All Media Types` with Android text input active. A merely stable/non-empty menu is no longer considered automation-ready.
- Debug APK snapshots now publish `debugStatusVersion=1`, `uiState`, and `automationReady`. `automationReady=true` requires a connected `Main Menu`, no popup, no active text input, and no active player.
- Added MCP `firetv_wake`, `dev_app_status`, `kill_dev_app`, and `dev_prepare_clean_start` tools.
- Clean start checks process state first, requests a normal session/task exit, re-checks, uses force-stop only if necessary, verifies the process is gone, wakes the device, then allows relaunch.
- Playback/session/Media3 matrix startup now verifies the relaunched process and trusts the Android debug readiness signal. If the fixed client ID reconnects into stale server UI, automation sends direct SageTV `home` and waits for `automationReady=true` before Search.
- The observed `MiniClientOpenGLActivity` launch is not treated as failure; the chosen UI activity follows the app's `use_opengl_ui` preference.
- Android debug source changed; rebuild/reinstall is required. v0.5.33 Pull seek I/O diagnostics are preserved.

# v0.5.33 - Pull seek I/O diagnostics

- Restored Media3 Pull's 256 KiB buffer after the v0.5.32 1 MiB experiment failed to improve the slow large-seek case.
- Added diagnostic-only counters for Pull socket opens and SageTV READ traffic: request count, requested/received bytes, READ wait time, max request size, errors, and positions.
- Exposed those counters through debug playback-health snapshots and the Media3 Comskip matrix report.
- The next device run should identify whether slow Pull Comskip recovery is dominated by network READ time or by extractor/decoder work after the reads complete.

## 0.5.32 — 2026-08-27

- Fire TV v0.5.31 proved direct SageTV Comskip commands are correct: Dynamic recovered RIGHT in 820 ms and LEFT in 566 ms; Pull recovered both directions but took about 3.63 s.
- The Pull slowdown is specific to large Comskip-style seeks; the v0.5.28 normal Pull FF/REW matrix stayed below 2 seconds. Hardware MPEG-2/AC-3 decoders, Surface, and A/V output remained healthy.
- Increased only `Media3PullDataSource.PULL_READ_BUFFER_BYTES` from 256 KiB to 1 MiB to reduce synchronous SageTV `READ` round trips during the existing MPEG-TS timestamp/PCR scan after large seeks.
- Preserved the 8x TS timestamp search window, constant-bitrate seek support, `CLOSEST_SYNC`, Pull LoadControl values, Dynamic/PUSH behavior, Legacy Exo Pull buffer size, IJK, decoder policy, and Comskip command path.
- This is an Android player/DataSource experiment and requires rebuilding/reinstalling the debug APK before device retest.

## 0.5.31 — 2026-08-27

- Corrected Comskip automation after device testing proved `mcp-send-sequence` + `command right` / `command left` is the working SageTV Comskip path.
- `android_comskip_check` no longer resolves `videoplaying_right` / `videoplaying_left`; those are normal short-press FF/REW mappings.
- Instrumented Comskip now sends the native SageTV `right` / `left` command directly through the existing `skip_check` output-health path, preserving decoder/surface/A/V recovery and landing-timeline diagnostics.
- Updated standalone `mcp-comskip-test`, Media3 Push/Pull Comskip matrix output, tests, help, README, tasks, and handoff documentation to distinguish direct Comskip commands from Android arrow mappings/long-press emulation.
- Host/MCP-only correction; no Android APK/player source changed and no APK rebuild is required.

## 0.5.30 — 2026-08-27

- Removed the hard-coded/default native Search recording title from playback automation.
- `mcp-playback-test`, `mcp-media3-matrix`, `mcp-media3-comskip-matrix`, and `mcp-search-test` now require `--text`; omitting it is an argparse error before any device/session action.
- MCP `dev_search_text` now requires its `text` argument as well, eliminating the hidden server-side fallback.
- Updated current docs/help/tests so future test-recording changes are passed explicitly instead of requiring code edits.
- No Android player implementation changes; no APK rebuild is required solely for v0.5.30.

# v0.5.29

## Media3 Push/Dynamic vs Pull Comskip matrix

- Recorded the v0.5.28 Fire TV result as a Phase-A pass: Media3 startup, single FF, single REW, and rapid mixed seek recovered real video/audio output in both Push/Dynamic and Pull. Pull's slowest seek was single REW at 1585 ms, below the 2000 ms slow-recovery threshold.
- Pause/resume measured about 5.3-5.5 seconds in both Push and Pull. Because the latency is common to both streaming modes, do not tune Pull buffering/DataSource based on that result; keep it as a separate common-path measurement item.
- Added `./dev.sh mcp-media3-comskip-matrix` to run fresh Media3 Push/Dynamic and Pull sessions, start the known recording through the proven native Search sequence, then exercise the configured video right/left arrow mappings.
- Comskip PASS/FAIL is based on real decoded video/audio recovery. The MiniClient does not receive semantic server/STV Comskip marker timestamps, so landing timeline/jump size is diagnostic rather than a guessed marker-target assertion.
- Recovery over 2000 ms captures diagnostics without automatically failing healthy output. A failed direction captures a checkpoint and stops additional arrow stress in that mode while allowing the other streaming mode to run.
- Host/MCP-only update; no Android APK/player/DataSource source changed.

# v0.5.28

## Media3 Push/Dynamic vs Pull seek-resume matrix

- Added `./dev.sh mcp-media3-matrix` to run the next Phase A device comparison without changing Media3/player code first.
- Each mode runs as a fresh MiniClient session: Media3 + Hardware -> direct server connect (`save=false`) -> wait for the v0.5.27 stable UI gate -> start `meet the press` through the exact native Search/key sequence.
- The matrix runs identical single FF, single REW, rapid `FF,FF,REW,FF,REW`, and pause -> play recovery checks in Push/Dynamic and Pull.
- Seek PASS/FAIL uses the existing real video/audio output-health probe. SageTV timeline movement remains diagnostic only.
- Recovery time is recorded side by side in a JSON report under `artifacts/firetv`. Recovery over 2000 ms is a warning and automatically captures a checkpoint so slow Pull resume can be diagnosed without mislabeling healthy playback as a seek failure.
- A failed basic FF/REW check stops further stress in that mode, captures diagnostics, then continues with the other streaming mode so one failure does not destroy the comparison.
- Host/MCP-only update: no Android APK/player source changed. The currently proven v0.5.26 debug APK can be used with the v0.5.27/v0.5.28 host tooling.

# v0.5.27

## Wait for a stable SageTV UI before native Search playback test

- Fixed an MCP startup race found on Fire TV: `connected=true` can be reported while the SageTV MiniClient UI is still loading, so v0.5.26 could send the native `Search` command too early.
- `dev_wait_for_ui` now optionally supports `menu_present` plus `stable_ms`. This preserves the old immediate behavior unless those options are explicitly requested.
- `mcp-playback-test` now requires a non-empty SageTV menu hint to remain unchanged for 2000 ms after connection before sending the exact Search/text/key sequence.
- `mcp-session-test` uses the same stable-UI readiness gate before starting video by name.
- `--ui-stable-ms` can override the 2000 ms readiness interval. The native playback input sequence itself is unchanged.
- MCP/host-only change: no Android player/APK source changed, so an APK rebuild is not required solely for v0.5.27 when v0.5.26 debug control code is already installed.

# v0.5.26

## Native end-to-end playback start test + v0.5.25 rollback revert

- Reverted the temporary v0.5.25 playback-bisect rollback after device evidence showed the Fire TV itself had entered a bad state: even the older MiniClient app failed until the Fire TV was rebooted. The v0.5.24 Android debug/session-control state is restored.
- Added `./dev.sh mcp-playback-test` for a complete native-client startup test: launch Dev app -> set player/streaming/decoder settings -> connect directly to a specified SageTV server -> run the exact native Search/key sequence -> verify real playback starts.
- Default server is `192.168.10.175`; connection uses `save=false`, so the test does not add a third saved server entry beside `192.168.10.175` and `192.168.10.232`.
- Default playback settings are Media3 + Push/Dynamic + Hardware. All are command-line selectable.
- Playback is started only through the native MiniClient input path. The test does **not** call `dev_play_video`, Sagex, HTTP, or server-side `Watch`.
- Default test recording sequence is exactly: Search -> 50 ms -> type `meet the press` -> 50 ms -> BACK -> 100 ms -> FAST_FORWARD -> RIGHT -> PLAY_PAUSE -> PLAY_PAUSE. No additional keys are inserted.
- After the sequence, MCP waits for `dev_wait_for_playback_started` and requires the existing real A/V playback-health check to pass.

# v0.5.24

## Explicit MCP input sequences

- Added first-class MCP tool `dev_send_sequence(sequence)` with four explicit line actions:
  - `command <sage-command>`
  - `sendkey <android-key>`
  - `sendtext <text>`
  - `delay <milliseconds>`
- Added host command `./dev.sh mcp-send-sequence` that reads a multiline sequence from stdin (or `--file` / `--sequence`), initializes MCP, connects ADB, then sends the whole sequence in one MCP tool call.
- Sequence execution adds no hidden keys, text, or delays. Blank lines and `#` comments are ignored.
- No Android APK/player code changed; no APK rebuild is required.

# v0.5.23

- Fixed native Search automation when the Android keyboard remained open after text injection.
- After typing, `dev_search_text` now dismisses the visible Android IME with `KEYCODE_BACK` by default. While the IME is open this closes the keyboard instead of navigating Back in SageTV.
- The default sequence is now: Search -> type `meet the press` -> dismiss keyboard -> Down -> Right -> Right -> Right -> Play/Pause -> Play/Pause.
- Removed the extra initial Play/Pause that had been incorrectly used as the keyboard-close action.
- MCP/host-only change; no APK rebuild required.

# Changelog

## v0.5.22

- Fixed the native MCP Search test to emulate the **physical Fire TV Play/Pause button** after text entry.
- The Search workflow now sends Android `KEYCODE_MEDIA_PLAY_PAUSE` (`play_pause`) instead of `KEYCODE_MEDIA_PLAY` (`play`).
- Default sequence: Search -> type `meet the press` -> Play/Pause -> Down -> Right -> Right -> Right -> Play/Pause -> Play/Pause.
- Generic MCP `PLAY` remains mapped to `KEYCODE_MEDIA_PLAY`; only this remote-emulation workflow uses `PLAY_PAUSE`.
- MCP/host-only change; no Android APK rebuild is required.

## v0.5.21 - Search submit uses Play, not Enter

- Changed the native MCP Search test default so Android keyboard Enter/Next is **not** sent after typing.
- Default sequence is now: Search -> type `meet the press` -> Play -> Down -> Right -> Right -> Right -> Play -> Play.
- `dev_search_text` now defaults `submit=false`; explicit `submit=true` remains available for diagnostics.
- `mcp-search-test --next` can explicitly add Enter/Next when needed, but the default path does not use it.

## v0.5.20 - Native Search text entry through MCP

- Added `dev_type_text` to inject text through Android's focused keyboard/input path.
- Added `dev_search_text`: native SageTV Search -> wait for `hasTextInput` -> type query -> optional Android keyboard Next/Enter.
- `./dev.sh mcp-search-test` now defaults to typing `meet the press` and pressing keyboard Next/Enter.
- Added `--text` and `--no-next` options to the Search test helper.
- No Android/player source changes; no APK rebuild is required from v0.5.19.

# v0.5.18 — MCP end-to-end client session + play-by-name

## 0.5.19

- Added first-class MCP tool `dev_search`, which sends the native SageTV `search` SageCommand to the currently connected MiniClient.
- Added `./dev.sh mcp-search-test`; it initializes MCP, calls `adb_connect`, then calls `dev_search` in the same Docker/MCP session so short-lived container ADB state cannot cause `no devices/emulators found`.
- MCP-only update: no Android/player code changed and no APK rebuild is required from v0.5.18.


- Added debug-only MiniClient session controls to launch the app, apply player/streaming/decoding/GSY settings before playback, connect to a saved/direct/last SageTV server, and cleanly exit back to the server screen or force-stop the Dev app.
- Added `dev_play_video(video_name)` as the preferred MCP playback-start tool. The caller supplies only a recording/video name. MCP reads the connected server and Dev MiniClient client ID from Android, auto-discovers the Sagex Remote API, resolves this MiniClient's SageTV UI context, finds a unique MediaFile by title, invokes SageTV `Watch` in that UI context, and verifies real A/V output starts.
- Exact case-insensitive media-title matches are preferred; a unique substring match is accepted. Ambiguous names return candidate MediaFile IDs/titles instead of starting an arbitrary recording.
- After A/V starts, MCP queries the current MediaFile in the same UI context and verifies its MediaFile ID matches the requested item when the server returns that information.
- Android debug snapshots now expose the fixed Dev `clientId` and normalized `uiContextHint`; no production runtime telemetry/listeners were added.
- `mcp-session-test` now uses `--video-name` instead of STV-specific Recordings-menu navigation. The end-to-end order is settings -> connect -> play-by-name -> verify output -> optional seek/comskip -> exit.
- Sagex endpoint discovery defaults to the connected server on ports 8080, 8081 and 80. Custom installations can set `SAGETV_SAGEX_BASE`; optional Basic Auth uses `SAGETV_SAGEX_USER` / `SAGETV_SAGEX_PASSWORD`.

# v0.5.17 — MCP Pull stability / AC-3 rewind probe correction

- Fixed a false `audio_not_advancing` failure after backward Pull seeks when Android reuses the same encoded/passthrough `AudioTrack` session but resets its playback-head counter to a smaller value. The health probe now recognizes a live same-session playback-head reset as renewed audio output, and reports reset detection explicitly.
- Long-running `skip_check` / `comskip_check` ADB broadcasts no longer use `--receiver-foreground`. Snapshot/config commands remain foreground. This avoids Android's tighter foreground-broadcast execution window from timing out slow Pull recovery checks and destabilizing Media3/GSY test sessions.
- `mcp-seek-test` now stops before the rapid 16-command stress and pause/resume checks if a basic FF/REW health check fails. The failing playback state and checkpoint are preserved instead of compounding the failure.
- Added MCP output fields `audioHeadResetDuringRecovery` and `audioHeadResetDuringVerify` so encoded-audio seek resets are visible in diagnostics.
- No Exo2/Media3/IJK/GSY playback, seek, buffering, decoder-selection, or transport implementation changed in v0.5.17. This release corrects the debug/test harness based on device evidence before changing player code.

# v0.5.16 — MCP Comskip arrow landing + A/V recovery validation

- Added debug-only `comskip_check`, which resolves the Android `videoplaying_right` / `videoplaying_left` mappings and sends the same SageTV command that the physical video arrows use. Default mappings remain FF/REW unless the user changed them.
- Added A/V recovery timeline checkpoints: first recovered video output, first recovered audio output, full output recovery, and the final recovery landing timeline.
- Added `dev_run_comskip_check` and `./dev.sh mcp-comskip-test` for dedicated Comskip testing. Output health remains authoritative; a known marker target may optionally be supplied for landing validation.
- The MiniClient does not receive semantic Comskip marker start/end metadata from the SageTV STV/plugin, so the debug API explicitly reports `comskipMarkerDataAvailable=false` rather than guessing a marker target.
- Existing decoder/output diagnostics remain attached to Comskip checks: decoder identity/type, renderer counters, AudioTrack advancement, Surface health, buffering/loading, datasource/file position, errors, and sustained playback.
- No Exo2/Media3/IJK/GSY playback, decoder, buffering, or seek implementation changed in v0.5.16.

# v0.5.15 — MCP real playback-output health checks

- Changed MCP seek/forward PASS/FAIL from timeline accuracy to **actual playback output health**. SageTV timeline values remain diagnostic only because they can advance while video is black or audio is stalled.
- Added debug-only `PlaybackHealthProbe`, invoked on demand only during MCP requests. It installs no permanent player/analytics listeners.
- Exo2/Media3/GSY-Media3 checks now inspect the live video renderer `DecoderCounters`, actual decoder name/type, Android `AudioTrack` playback-head advancement, Surface validity, player READY/playing state, datasource, buffering/loading state, retry/error state, and buffer/file position.
- After each FF/REW sequence, MCP requires video output and audio output to recover within the timeout **and continue advancing** through a verification window.
- Added explicit health failure reasons such as `video_not_rendering`, `audio_not_advancing`, `player_not_ready_playing`, `surface_not_valid`, and post-recovery stalls.
- Skip results include before/post/final decoder/output snapshots, decoder init/release deltas, datasource changes, buffer/file-read information, output counters, recovery time, and player errors.
- Diagnostic checkpoints now include normal logcat, crash buffer, MediaCodec/extractor/SurfaceFlinger dumps, audio-system dumps, focused window, screenshot, and compact player/decoder state.
- `./dev.sh mcp-seek-test` no longer performs timeline calibration by default. It runs single FF, single REW, and rapid mixed FF/REW output-health stress immediately. `--calibrate-timeline` retains the old semantic timing diagnostics when needed.
- No production/release player behavior, decoder policy, buffering, streaming transport, or IJK runtime is changed. The debug APK must be rebuilt because debug Android code changed.

# v0.5.14 — Android-side MCP skip measurement

- Moved MCP seek/skip measurement into the Android debug APK. `dev_run_seek_check` now invokes one debug `skip_check` operation instead of measuring separate host-side snapshots.
- The debug receiver records the same value used by `MediaCmd.MEDIACMD_GETMEDIATIME` / SageTV's displayed playback timeline immediately before and after the command sequence.
- Skip results now return `timelineBeforeMs`, `timelineAfterMs`, `timelineDeltaMs`, `elapsedMs`, and `playbackAdjustedDeltaMs` directly from the Android process.
- One-shot player snapshots now also expose `sageTimelineMs` and `timelineSource=MEDIACMD_GETMEDIATIME` explicitly. `mediaTimeMs` is retained for compatibility.
- Calibration uses the same Android-side skip measurement and still pauses playback, takes multiple samples, rejects wrong-direction/zero outliers, and uses a robust median/5-second preference quantum.
- The visible Streaming Mode label remains **Push/Dynamic** while the persisted value stays `dynamic`.
- No Exo2, Media3, IJK, GSY playback, decoder, buffering, or transport implementation was changed in v0.5.14. The Android debug APK must be rebuilt because the debug receiver changed.

# v0.5.13 — MCP skip calibration robustness

- Fixed `mcp-seek-test` aborting when noisy player landing measurements calibrated a nominal 10-second SageTV skip as +9/-11 seconds.
- Calibration now rounds to a 5-second preference quantum by default so small player landing error remains visible instead of being learned into the expected result.
- Added `--calibration-quantum-ms` for unusual custom SageTV skip intervals; `--ff-ms` / `--rew-ms` overrides remain supported.
- Non-divisible semantic steps no longer abort the suite: the harness reports both the requested semantic target and the closest practical command-plan target, then validates the player against the commands actually sent.
- The rapid `+30,+30,-10,+60,-30` semantic sequence remains correctly defined as net +80 seconds when primary skips calibrate to +/-10 seconds.
- No Android player, decoder, streaming, buffering, transport, or settings code changed in v0.5.13. No APK rebuild is required when updating from v0.5.12.

# v0.5.12 — MCP seek calibration correction

- Fixed the MCP seek suite's incorrect assumption that SageCommand `ff` always means +30 seconds; SageTV primary skip intervals are server-configurable.
- `mcp-seek-test` now measures the active server's actual `ff` and `rew` increments before running semantic +30/-10 checks.
- Corrected the rapid semantic sequence `+30,+30,-10,+60,-30` from the previously incorrect +50-second expectation to the correct **+80-second** net movement.
- Added `--ff-ms`, `--rew-ms`, `--calibrate-settle-ms`, and `--paced-delay-ms` controls.
- Normal semantic skip checks are paced separately from the rapid stress sequence.
- No ExoPlayer, Media3, IJK, GSY, decoder, transport, buffering, or seek implementation code changed in v0.5.12.

# v0.5.11 — MCP current-playback regression automation

- Added `DevTestReceiver` in the Android TV **debug source set only**. Release/main manifests do not expose the receiver.
- Added on-demand player snapshots (backend/config, MiniPlayer state, SageTV media time/server anchor, buffer left, last file read position, video dimensions) without player callbacks or continuous telemetry.
- Added MCP tools to set player/stream/decode/GSY preferences for the next playback, send exact SageTV commands, run command sequences, validate expected seek deltas, wait for a media position, perform a local backend-only seek, and capture diagnostic checkpoints.
- Added `./dev.sh mcp-seek-test` to automate primary +30, primary -10, rapid `+30,+30,-10,+60,-30`, and pause/resume against the currently playing known recording.
- Changed the visible Streaming Mode label from **Dynamic** to **Push/Dynamic** while preserving the persisted value `dynamic` and existing default behavior.
- Fixed stale main Settings summaries: Default Player, Decoding Method, and Streaming Mode now refresh when returning from sub-settings pages.
- No ExoPlayer, Media3, IJK, GSY playback implementation, decoder, buffering, or seek behavior changed in v0.5.11.

# v0.5.10 — Exo PUSH startup blocking-read fix

- Fixed ExoPlayer Legacy and Media3 PUSH/Dynamic startup that could remain black with no audio when the SageTV 4 MiB push buffer was briefly empty.
- Added an opt-in `PushBufferDataSource.readBlocking()` path that follows the Exo/Media3 DataReader contract: zero is returned only for a zero-length request; otherwise it waits for data or returns EOS.
- Only Exo2/Media3 PUSH adapters use the blocking read. Legacy IJK keeps the historical non-blocking push read path.
- Pull seek/timeline and v0.5.9 Pull latency tuning are unchanged.

# Changelog

## 0.5.9 - 2026-08-26

### Phase A Pull resume-latency optimization

- Fire TV v0.5.8 validation confirms Pull seek correctness is fixed for Legacy ExoPlayer, Media3, and GSY Auto: rapid seeks, seek-to-start, pause/start, and large comskip now land on the correct timeline.
- Remaining symptom is delayed visual playback after a correct Pull seek. The timeline remains correct while Exo buffers/scans before releasing new frames.
- Added a configurable `BufferedPullDataSource` buffer size while preserving the historical 32 KiB default used by IJK/System paths.
- Exo2 and Media3 Pull now use a 256 KiB SageTV network read buffer to reduce synchronous `READ` round trips during the existing 8x MPEG-TS timestamp/PCR scan.
- Added Pull-only Exo LoadControl tuning: 5 s min / 20 s max forward buffer, 500 ms resume-after-seek, and 1000 ms resume-after-rebuffer. Dynamic/PUSH uses the existing defaults.
- Added Pull playback-state diagnostics with current position, buffered position, and playWhenReady for any remaining post-seek stalls.
- Seek-map tuning from v0.5.8 is preserved unchanged. IJK and Dynamic/PUSH behavior are unchanged.

## 0.5.8 - 2026-08-26

### Phase A Pull seek-map follow-up

- Corrects the v0.5.7 diagnosis: `MEDIACMD_FLUSH` is only forwarded to the player when `MediaCmd.pushMode` is true, so the Pull-mode flush guard could not be the cause of the remaining Exo seek-to-zero behavior.
- Tunes only Exo-based **Pull** playback (Legacy ExoPlayer and Media3) with a larger MPEG-TS PCR/timestamp search window (`8 x TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES`).
- Enables constant-bitrate seeking for progressive extractors that support it and uses `SeekParameters.CLOSEST_SYNC` for Pull PVR-style seeks.
- Leaves Dynamic/PUSH extractor and seek behavior unchanged.
- Adds Pull seek diagnostics that log whether Exo considers the current media item seekable, plus duration and buffered position, immediately before each seek.
- GSY Auto inherits the Media3 Pull fix through its existing Media3 delegate.
- IJK is unchanged and remains the passing Pull control.

## 0.5.7 - 2026-08-26

### Phase A second device follow-up

- Fixed ExoPlayer Legacy and Media3 Pull seeks snapping back to `0:00`. SageTV can issue `FLUSH` around a seek even in Pull mode; the backend-specific `flush()` implementations were unconditionally replacing/re-preparing the MediaSource with reset-position enabled. Pull mode now ignores that destructive player reset and leaves repositioning to Exo's seek/DataSource reopen path. PUSH/Dynamic keeps the existing reset behavior.
- GSY Auto now resolves to the known-good Media3 SageTV-aware engine for both Dynamic/PUSH and Pull. Android System MediaPlayer remains available as an explicit GSY engine while its custom `MediaDataSource` compatibility is hardened.
- Hardened `SagePushMediaDataSource.readAt()` for Android System MediaPlayer: for a non-zero request it waits for SageTV PUSH data instead of returning `0`, which Android reserves for a zero-length request.
- Fixed the GSY/System Pull teardown crash by making `SagePullMediaDataSource.close()` idempotent and `SimplePullDataSource.close()` synchronized. This prevents two teardown threads from nulling `remoteServer` between the null check and `Socket.close()`, which caused the observed JNI pending-NPE/SIGABRT.
- Added regression coverage for Pull flush semantics, GSY Auto routing, System PUSH read semantics, and concurrent/idempotent Pull close behavior.
- Decoder policy, legacy IJK runtime, Dynamic/PUSH timeline rebasing, and future HTTP/HLS/RTMP/SRT transport reserves remain unchanged.

## 0.5.6 - 2026-08-26

### Phase A device-test follow-up

- Fixed GSY Auto / Android System MediaPlayer startup crash caused by `GSYSystemMediaPlayerImpl.setupPlayer()` releasing/hiding the just-created video frame a second time, then calling `MediaPlayer.setDisplay()` with a released Surface.
- GSY/System now registers a `SurfaceHolder.Callback`, waits for a valid Surface before `prepareAsync()`, detaches the display when the Surface is destroyed, and removes the callback when the player is released.
- Fixed legacy ExoPlayer and Media3 SageTV PULL `DataSource.open()` range accounting. Non-zero byte-range opens now return the readable length of the requested range instead of the total file size.
- Exo2/Media3 PULL readers now track `bytesRemaining` and honor bounded DataSpec requests. This prevents Exo from inflating the logical resource end across reopen/seek operations, which can lead to `ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE`.
- Added regression coverage for the GSY/System Surface lifecycle and both PULL DataSource contracts.
- Added `PHASE_A_FOLLOWUP_TEST.md` focused on the device failures found during v0.5.5 validation.
- No decoder policy changes and no PUSH/Dynamic timeline-rebase changes are included. IJK Dynamic large-comskip behavior and GSY Legacy Dynamic timeline mismatch remain Phase B/C targets.

## 0.5.5 - 2026-08-26

### Phase A seek/timeline cleanup

- ExoPlayer Legacy and Media3 now accept an exact `0 ms` seek.
- Removed the old ExoPlayer/Media3 UI-thread seek polling loop (`Thread.sleep(100)` up to one second). Seeks now return immediately and completion is observed asynchronously.
- `onTimelineChanged()` no longer clears `seekPending`; seek completion is tied to `DISCONTINUITY_REASON_SEEK`.
- On confirmed Exo/Media3 seek discontinuity, the cached/media-session position is updated from `newPosition.positionMs`.
- IJK repeated-Pause frame stepping is corrected from `+1000 ms` to approximately one 30 fps frame (`+33 ms`).
- Added regression coverage and `PHASE_A_SEEK_TEST.md` with player-by-player verification steps.
- Decoder policy, GSY routing, and PUSH/PULL timeline rebasing are intentionally unchanged in this phase.

## 0.5.4 - 2026-08-26

- Fixed `GSYSystemMediaPlayerImpl` compile failure on the project Android API where `android.media.MediaPlayer.seekTo()` accepts an `int`, while SageTV provides seek time as `long`.
- Added a saturating millisecond conversion (`0..Integer.MAX_VALUE`) before calling System MediaPlayer so normal seeks compile and very large values cannot overflow negative.
- No player routing, transport, RTMP reserve, legacy IJK, ExoPlayer, or Media3 behavior changed.

## 0.5.3 - 2026-08-26

- Fixed the v0.5.2 Android build failure caused by GSYVideoPlayer Exo2 pulling the unused `com.github.mcxinyu:LibRtmp-Client-for-Android:v3.2.0.m2` dependency from legacy Sonatype repositories.
- Excluded both the unused mcxinyu RTMP client and `androidx.media3:media3-datasource-rtmp` from the GSY Exo2 dependency. SageTV does not use RTMP for its PUSH/PULL or normal HTTP playback paths.
- Kept GSY 13.1.0, Media3 1.11.0, legacy ExoPlayer 2.18.1, and original IJK 0.8.8 otherwise unchanged.
- Added regression assertions so the unwanted RTMP dependency cannot silently return.

## 0.5.2 - 2026-08-26

- Restored the original SageTV IJK 0.8.8 Java/JNI runtime as a fully separate top-level player.
- Removed GSY `ex_so` from the APK and explicitly excluded GSY's modern IJK Java bridge to prevent namespace/JNI collisions.
- Rebuilt `GSYVideoPlayer` as an independent selectable-engine adapter: Auto, Media3/ExoPlayer, Android System MediaPlayer, or Legacy ExoPlayer.
- `Auto` uses Android System MediaPlayer for SageTV PUSH/PULL custom data sources and Media3 for normal HTTP URLs.
- Added native Android `MediaDataSource` PUSH/PULL bridges so GSY/System can consume SageTV streams without `ijkmediadatasource:`.
- Legacy IJK decoder fixes are now legacy-IJK-only; GSY no longer inherits from or configures `IJKMediaPlayerImpl`.
- Kept the AFTMM/mantis MPEG-2 MediaCodec safety fallback inside legacy IJK only.


## 0.5.1 - Fire TV IJK MPEG-2 MediaCodec safety fallback

- Fixed IJKPlayer/GSYVideoPlayer MPEG-2 playback on the confirmed Amazon AFTMM / `mantis` Fire TV after the GSY 13.1.0 runtime still reproduced the repeated Android `MediaCodec.dequeueInputBuffer()` failure loop.
- The shared IJK decoder mapper now disables only MPEG-2 MediaCodec on AFTMM/`mantis` and uses the bundled IJK/FFmpeg software decoder for that stream type. AVC/H.264, HEVC, MPEG-4, and other devices keep their normal Hardware/Hardware Preferred policy.
- Added a second guard in `CodecSelector` that rejects the same known-broken MPEG-2 hardware path if the native runtime requests a codec despite the per-codec MediaCodec switch.
- Removed the v0.4.9/v0.5.0 forced preference for `OMX.MTK.VIDEO.DECODER.MPEG2` on `mantis`; real hardware testing proved it is not stable for this SageTV custom-data-source path.
- Added regression tests/validation for the AFTMM/`mantis` safety fallback. Internal player telemetry remains disabled.

## 0.5.0 - Four-player decoding policy / GSYVideoPlayer backend

- Added a shared **Decoding Method** setting used by every player backend: **Hardware** (default), **Software**, and **Hardware Preferred**.
- Legacy ExoPlayer and Media3 apply the policy only to video decoder selection; audio decoder/passthrough behavior remains unchanged.
- **Hardware Preferred** enables decoder fallback for ExoPlayer/Media3. Legacy IJK uses its own 0.8.8-compatible fallback behavior.
- Replaced the active old local IJK 0.8.8 Java/native AAR set with GSYVideoPlayer **13.1.0** `gsyvideoplayer-java` + `gsyvideoplayer-ex_so`, so the APK contains only one IJK Java/JNI runtime and gains GSY's MPEG-capable extended native package.
- Existing SageTV **IJKPlayer** now uses the maintained GSY IJK runtime while retaining SageTV's custom push/pull `IMediaDataSource` and lifecycle.
- Added **GSYVideoPlayer** as backend #4 with stable preference ID `gsyplayer`; it uses a dedicated GSY-tuned IJK profile behind the existing SageTV `MiniPlayerPlugin` abstraction.
- Added a dedicated **GSYVideoPlayer Settings** screen for MediaCodec synchronization, resolution-change handling, rotation, packet buffering, frame drop, minimum frames, probe size, analyze duration, accurate seek, OpenSLES, infinite input buffering, and hardware codec blocking.
- Kept the Fire TV `mantis` MTK MPEG-2 hardware decoder preference for IJK/GSY hardware mode.
- Kept Legacy ExoPlayer as the default backend and kept internal player telemetry disabled.
- Added `test_valitdate_build_install_lanuch.sh` to stop on the first failed test/validate/build/install/launch step.

## 0.4.9 - IJK MPEG-2 MediaCodec compatibility

- Re-enabled IJK MPEG-2 hardware decoding by default.
- Added conservative MPEG-TS probing before IJK MediaCodec startup (`probesize=2 MiB`, `analyzeduration=3 s`) and avoids `fflags=nobuffer` in MPEG-2 hardware mode.
- Enabled IJK synchronized MediaCodec mode and keeps decoder-side rotation disabled.
- Fire TV Stick 4K `mantis` explicitly prefers `OMX.MTK.VIDEO.DECODER.MPEG2` when available.
- Filters known Android software codecs from IJK hardware selection unless the existing software-codec preference allows them.
- The existing IJK MPEG-2 setting remains the fallback switch: disable it to use the known-working bundled FFmpeg software decoder.
- Legacy ExoPlayer and Media3 runtime code are unchanged.

## 0.4.8 - IJK MPEG-2 Fire TV fallback

- Legacy ExoPlayer and Media3 remain unchanged after both were confirmed playing on the Fire TV.
- IJK now defaults MPEG-2 video to its bundled FFmpeg software decoder instead of the IJK 0.8.8 MediaCodec MPEG-2 path, which produced audio with a black screen and then stopped on the test Fire TV.
- Added **IJK: MPEG-2 Hardware Decoder** in IJK settings. It defaults OFF and can be enabled for A/B testing on devices where IJK MediaCodec MPEG-2 is reliable.
- H.264/AVC and HEVC MediaCodec acceleration remain enabled in IJK.
- IJK `OnErrorListener` now returns handled after the existing `playerFailed()` path to prevent an additional completion/stop cascade.
- No internal player telemetry hooks were added.

## v0.4.6 - Legacy ExoPlayer binary alignment

- Fixed the Fire TV Legacy ExoPlayer crash caused by a binary-incompatible legacy Exo module graph (`AbstractMethodError` from `H262Reader` to `TrackOutput.sampleData`).
- Kept the legacy baseline at ExoPlayer 2.18.1, but now forces every `com.google.android.exoplayer` dependency to that exact version under Gradle 8.
- Removed `@aar` artifact-only notation from published legacy Exo modules so Gradle can use normal module metadata and dependency alignment.
- Removed `exoplayer-testutils` from the production Android APK.
- Removed the unused `ijkplayer-exo` wrapper AAR; SageTV uses native `IjkMediaPlayer` directly.
- Kept the local FFmpeg extension AAR isolated as the legacy audio extension.
- Added tests/validation to prevent mixed legacy Exo module versions from returning.

# v0.4.3 - AGP 8 Non-Transitive Resource Repair

## 0.4.5 - GDX backend routing fix and external player diagnostics

- Fixed `MiniClientGDXRenderer` to use the same `PlayerBackend` / `PlayerFactory` three-backend routing as `OpenGLRenderer`.
- Important: before this fix, selecting `media3` while running `MiniClientGDXActivity` actually instantiated IJKPlayer, so the previous Media3 audio-only result was not a real Media3 test.
- Rolled back the unsuccessful v0.4.4 SurfaceView/Z-order experiments to the known-good v0.3.8 legacy Exo/IJK/GDX behavior.
- Added external-only `./dev.sh player-diag clear` and `./dev.sh player-diag LABEL` diagnostics for logcat, crash filtering, SurfaceFlinger, MediaCodec, focused window, package state, and screenshot capture.
- No internal player telemetry hooks were added.

## 0.4.4 - Fire TV video surface lifecycle repair

- Legacy ExoPlayer now binds the `SurfaceView` with `setVideoSurfaceView(...)` before `prepare()` instead of passing a one-time raw `Surface`.
- Media3 now binds its `SurfaceView` before `prepare()` so decoder initialization starts with a lifecycle-managed video output.
- IJK restores `video_surface` visibility after its internal `releasePlayer()` call and before `setDisplay(...)`.
- The GDX OSD now uses a translucent media-overlay SurfaceView instead of absolute `setZOrderOnTop(true)`, making video/OSD composition explicit on older Fire OS.
- No internal player telemetry was reintroduced.
- Added regression/validation guards for surface ordering and lifecycle attachment.


- Fixed the `android-tv` Java compile failure caused by AGP 8 non-transitive `R` classes.
- `iconbutton_background`, `ic_add_to_queue_white_60dp`, `ic_tv_white_60dp`, and `sage_logo_256` physically belong to `android-shared`, so TV/phone presenters now reference them through `sagex.miniclient.android.R.drawable` instead of the app module's `sagex.miniclient.android.tv.R`.
- Kept AGP 8 non-transitive resource behavior enabled rather than restoring legacy transitive `R` globally or duplicating resources into `android-tv`.
- Added unit and validation guards for the shared-resource module boundary.
- The native-library `stripDebugDebugSymbols` warning is unchanged and remains non-fatal.
- No player runtime implementation changes.

# v0.4.2 - Gradle 8 Android Shared Dependency Repair

- Fixed the first real AGP 8 / Gradle 8 Java compile failure in `android-shared`.
- Added an explicit `org.slf4j:slf4j-api:1.7.6` dependency to `android-shared`; the module imports SLF4J directly and can no longer rely on the Java `core` module's `implementation` dependency leaking transitively onto its compile classpath.
- Added an explicit `androidx.appcompat:appcompat:1.3.1` dependency to `android-shared`.
- Updated `AutoConnectDialog` to use the public `androidx.appcompat.R.style.Theme_AppCompat_Dialog` resource instead of the old transitive/internal `R.style.Base_Theme_AppCompat_Dialog` symbol that is not generated under the modern Android resource model.
- Added unit and validation guards for both dependency-boundary fixes.
- Legacy ExoPlayer, IJKPlayer, and `BaseMediaPlayerImpl` remain unchanged from the confirmed v0.3.8 playback baseline.

# Changelog

## 0.4.1 - Gradle root recovery guard

- Restores the v0.4.0 Dev root `source/dev/build.gradle` containing the Media3/toolchain pins.
- Adds a regression guard that fails explicitly if `source/dev/core/build.gradle` is ever copied over the Dev root Gradle file.
- No Android player/runtime Java changes from v0.4.0.

## v0.4.0 — Three-player refactor / Media3 modernization — 2026-08-26

- Added centralized `PlayerBackend` + `PlayerFactory` architecture with stable `exoplayer`, `media3`, and `ijkplayer` preference IDs.
- Added AndroidX Media3 ExoPlayer **1.11.0** as a new selectable backend with dedicated SageTV push/pull DataSources, MediaCodec selector, subtitle handling, renderer-index-safe track selection, and decoder fallback.
- Kept the v0.3.8 `BaseMediaPlayerImpl`, legacy `Exo2MediaPlayerImpl`, and `IJKMediaPlayerImpl` byte-for-byte unchanged and kept legacy ExoPlayer as the default.
- Kept standalone ExoPlayer **2.18.1** + local FFmpeg extension **2.18.0** frozen as the known-good baseline; 2.19.1/FFmpeg modernization is intentionally deferred to a separate experiment.
- Kept IJKPlayer **0.8.8-SNAPSHOT** as the legacy fallback; removed its codec selector's source dependency on ExoPlayer `Util`.
- Added Media3 settings/decoder information UI.
- Modernized the Dev build to JDK 17, AGP 8.13.2, Gradle 8.13, compileSdk 36, Build Tools 36.0.0, and minSdk 23 while retaining targetSdk 30 and NDK 21.0.6113669.
- Preserved untouched baseline reproducibility by bundling JDK 8 plus Android platform 29/Build Tools 29.0.2 in the same Docker image; `build-existing` explicitly uses JDK 8.
- Internal player telemetry remains disabled for every backend after Fire TV testing proved those hooks can block startup.
- Fixed Dev identity `DEV001`, Dynamic fresh-install default, clean Gradle build, Firebase removal, and uninstall-before-install behavior remain intact.
- Added player-backend and dual-toolchain regression tests and expanded project validation.

## v0.3.8 - Telemetry hook rollback confirmed

- Fire TV testing confirmed that enabling the Android player telemetry hooks prevents video startup even with clean uninstall/install and logcat-only transport.
- Restored BaseMediaPlayerImpl, Exo2MediaPlayerImpl, and IJKMediaPlayerImpl to the v0.3.6 playback-working runtime.
- PlayerTelemetry remains inert and is not called by the media players.
- MCP player telemetry is intentionally disabled so stale logcat/file events cannot be mistaken for current playback state.
- Keeps v0.3.6 fixed client ID DEV001, Dynamic default streaming mode, clean Gradle build, and uninstall-before-install behavior.
- Future playback diagnostics must be external to the player runtime unless introduced one hook at a time behind an explicit opt-in switch.

## v0.3.6 - 2026-08-25

- Fixed the Dev SageTV client identity to `DEV001` (`44:45:56:30:30:31`) at both resolver and wire-connection layers so clean reinstalls reuse the same SageTV client profile.
- Disabled editing of the global Dev client ID in Settings; generated/per-server IDs cannot override the connection identity.
- Dev APK installation now always performs `adb uninstall org.opensagetv.miniclient.dev.debug` before a fresh `adb install`; `install -r` is no longer used.

- Hard-rolled back all Android media-player telemetry hooks to the pre-telemetry v0.2.10 runtime baseline.
- Restored `BaseMediaPlayerImpl`, `Exo2MediaPlayerImpl`, and `IJKMediaPlayerImpl` byte-for-byte from the pre-telemetry source.
- Kept Docker/ADB/MCP infrastructure intact; player telemetry MCP tools may return no events until telemetry is reintroduced using a safer design.
- Dev APK builds now run `gradle clean` before `assembleDebug` to prevent stale telemetry classes from surviving incremental builds.
- Validation now fails if Android player telemetry hooks are present during baseline recovery.

## v0.3.5 - Validation alignment

- Fixed `scripts/validate_project.py` to validate the intentional v0.3.4 logcat-only Android telemetry rollback instead of requiring the removed app-private file transport.
- Added a guard that fails validation if Android `PlayerTelemetry` reintroduces app-private file I/O while the playback-safe rollback is active.
- No MiniClient runtime or Docker image changes.

## v0.3.3 - Telemetry source normalization

## v0.3.4 - Playback-safe telemetry rollback

- Restored `PlayerTelemetry.java` to the exact v0.3.0 logcat-only implementation that was confirmed to allow video playback on the Fire TV AFTMM.
- Restored `BaseMediaPlayerImpl` telemetry construction to the matching v0.3.0 constructor.
- ExoPlayer and IJKPlayer telemetry hooks were not changed; they are byte-for-byte identical to v0.3.0.
- Removed app-private telemetry file writing from the Android runtime. MCP retains logcat fallback support.
- Updated telemetry regression tests to lock this playback-safe transport until a non-invasive query transport is implemented.


- Fixed mixed telemetry source state exposed by the v0.3.2 update packaging.
- Re-ships the matched `BaseMediaPlayerImpl` and `PlayerTelemetry` implementation together so the constructor contract is consistent.
- Re-ships the telemetry MCP parser/client and telemetry regression tests as one coherent set.
- Ensures `BaseMediaPlayerImpl` constructs telemetry with the Android application context required for private-file transport.
- No playback, seek, buffering, decoder-selection, or player-state decision logic is changed.

## v0.3.2 - Non-blocking telemetry file transport

- Fixed a playback-start regression introduced in v0.3.1 by synchronous app-private telemetry file I/O.
- All telemetry file initialization and append operations now run on a dedicated background writer thread (`SageTVDevTelemetryWriter`).
- Player/UI/Exo/IJK callback threads now only format/log/enqueue telemetry; they never open, truncate, or write the telemetry file directly.
- Preserved the reliable `run-as` MCP file transport and logcat fallback.
- Added regression checks that require queued file writes and prohibit the old synchronous file-lock path.
- No seek, buffering, decoder, or playback-state decision logic was changed.

## v0.3.1 - Fire TV telemetry transport hardening

- Added app-private player telemetry file `sagetv_dev_player_telemetry.log`.
- MCP now reads telemetry with Android `run-as` first and falls back to logcat.
- Added `transport` and telemetry-file fields to MCP telemetry results.
- Updated `mcp-telemetry` output to expose the active transport.
- Added regression tests for raw private-file parsing and file-first MCP aggregation.
- Playback behavior remains unchanged.

## v0.3.0 - Player telemetry / MCP metrics phase

## v0.2.8 - Logger contract test harness fix

## v0.2.10 - MCP smoke-test dispatch fix

- Fixed `./dev.sh mcp-test` when using an existing Docker image whose baked-in entrypoint predates the `mcp-test` command.
- `dev.sh` now invokes the bind-mounted `/workspace/scripts/mcp_smoke_test.py` directly with Python, so no image rebuild is required.

## v0.2.9 - 2026-08-25

- Added `./dev.sh mcp-test` for a real end-to-end MCP stdio smoke test without Codex or Node.
- The smoke test performs MCP initialize, `tools/list`, `adb_connect`, device info, Dev package info, focused-window lookup, and screenshot capture.
- Clarified that `./dev.sh mcp` is a long-running stdio server and is expected to wait until an MCP client connects.


- Fixed `tests/test_logger_contract.py` to create its temporary `javac -d` output directory before invoking `javac`.
- This corrects a test-harness-only failure (`javac: directory not found: .../classes`); no MiniClient source or Docker image changes are required.

## v0.2.7 - SageTV debug signing alias fix

- Fixed Android debug signing for this repository: `android-tv/build.gradle` expects alias `client`, not the standard Android `androiddebugkey` alias.
- `scripts/ensure_debug_keystore.sh` now creates/verifies alias `client` with the Android debug passwords expected by AGP.
- Existing readable `adb/debug.keystore` files are preserved; if they contain `androiddebugkey` but not `client`, the script adds `client` in place rather than deleting the keystore.
- Added regression coverage tying the generated alias to the real Gradle signing configuration.

## v0.2.6 - Logger interface contract fix

- Fixed Firebase-free Android `Logger` to implement the real `ILogger.getLoggerInstance(Class/String)` methods.
- Removed incorrect `isTraceEnabled()` / `isDebugEnabled()` overrides that are not part of this project's `ILogger` interface.
- Added a Java compile regression test using the real `ILogger.java` plus lightweight SLF4J stubs.
- `./dev.sh test` now discovers all project tests automatically.
- `./dev.sh validate` now checks the Logger/ILogger contract shape.

# Changelog

## 0.2.3 - WSL bind-mount fix

- `dev.sh` now exports the absolute directory containing itself as `SAGETV_WINDOWS_ROOT` before invoking Docker Compose.
- This removes reliance on Compose interpreting `.` correctly across WSL/Docker Desktop.
- An explicitly **exported** `SAGETV_WINDOWS_ROOT` still overrides the automatic path.

## v0.2.2 - 2026-08-25

- Fixed Docker workspace bind default: the directory containing the project is now mounted to `/workspace` automatically.
- `SAGETV_WINDOWS_ROOT` remains available only as an optional explicit override.
- Added container-side project mount validation with a clear error when Docker is pointed at the wrong folder.
- Added regression coverage ensuring `scripts/run_unit_tests.sh` is packaged and the Compose default does not point at a hard-coded Windows path.
- Updated Windows/WSL environment examples and setup guidance.

## 0.2.1 — 2026-08-25 — Flattened full-project package

- Removed the confusing host-side `project/` nesting.
- Docker, MCP server, scripts, docs, source trees, artifacts, logs, ADB keys, and configuration now live directly under `C:\SageTV-MiniClient-Dev`.
- Docker now bind-mounts the single Windows root at `/workspace`.
- Updated Docker working directory, MCP `PYTHONPATH`, preflight logic, setup scripts, and documentation for the flattened layout.
- Added `.dockerignore` so bundled source/artifacts are not unnecessarily sent as Docker build context.


## 0.2.0 — 2026-08-25 — Exact source integration + complete Firebase removal

- Integrated the exact user-supplied `sagetv-miniclient-master.zip` into the full workspace.
- Added untouched `source/existing` and separately refactored `source/dev` trees.
- Recorded source archive SHA-256 `f45c930f3810b56d88f751120ca6fa692ae9b01dd2adce62a3d926b2ab336a79` outside the untouched baseline tree.
- Expanded the Dev refactor to remove all identified active Firebase/Crashlytics/Google Services build, runtime, settings, manifest, CI, and ignore-file wiring.
- Reworked Android `Logger` to remain SLF4J-only while preserving `ILogger` and exception stack traces.
- Added `source/dev/scripts/verify_no_firebase.sh`.
- Updated import/refactor validation to fail closed if active Firebase references remain.
- Source replacement now recreates the same complete Firebase-free Dev transform automatically.
- Updated documentation for bundled-source workflow; GitHub bootstrap is now fallback-only.
- Player behavior remains unchanged in Phase 0.

## 0.1.1 — 2026-08-25 — Docker-only Dev naming/workspace hardening

### Development naming

- Standardized the application/project runtime name on **Dev** rather than Codex.
- Development application ID is `org.opensagetv.miniclient.dev`; debug package is `org.opensagetv.miniclient.dev.debug`.
- Development source tree is `/workspace/source/dev`.
- Development APK is `SageTV-MiniClient-Dev-debug.apk`.
- Python MCP package/runtime naming is `sagetv_dev_mcp` / `sagetv-dev-firetv`.
- Codex terminology remains only where documentation or a command refers to the actual Codex client.

### Docker-only workspace

- Added Windows-backed workspace default `C:\\SageTV-MiniClient-Dev`.
- Added WSL and Windows `.env` examples for the same root folder.
- Persist ADB authorization keys under the Windows workspace.
- Keep Gradle cache in a Docker named volume for build performance.
- Android SDK/ADB, JDK, NDK, Python and MCP all run inside Docker.
- Host build helper names now redirect into Docker instead of invoking a second Android toolchain.
- Added Docker preflight and shell/ADB helpers.

### Source and baseline builds

- Added exact source ZIP importer that creates untouched `source/existing` and isolated `source/dev` copies.
- Added root `compile_existing_app.sh` to compile the untouched/current app without installing it.
- Added `build-existing` Docker command and baseline artifact metadata.

### Safety hardening

- Safe Fire TV installer now defaults to the Windows-backed Dev APK artifact and always verifies package ID before install.
- MCP default install path now resolves through the configured `/workspace/artifacts/firetv` directory.
- Existing `jvl.sage.miniclient...` namespace remains blocked from MCP package mutations.

## 0.1.0 — 2026-08-25 — Phase 0 hardware-in-loop development scaffold

### Code review

- Reviewed OpenSageTV SageTV MiniClient v1.14.0 project/module layout.
- Confirmed existing common player abstraction with ExoPlayer and IJKPlayer implementations.
- Documented ExoPlayer startup/seek flow and lack of dedicated first-rendered-frame telemetry in reviewed code.
- Documented IJK deferred/special seek timing behavior.
- Documented SageTV compatibility considerations in common media-time/seek handling.

### Development app isolation

- Added deterministic refactor script.
- Changed Android application ID base to `org.opensagetv.miniclient.dev`.
- Expected debug package becomes `org.opensagetv.miniclient.dev.debug` under the existing debug suffix.
- Changed visible development app names to `SageTV MiniClient Dev` / `SageTV MiniClient Dev Debug`.
- Preserved Java package namespace to avoid unnecessary first-pass source churn.
- Added project validation script.

### Firebase removal

- Remove Google Services and Crashlytics Gradle plugin wiring.
- Remove Firebase BoM, Analytics and Crashlytics app dependencies.
- Remove shared Crashlytics dependency.
- Remove `FirebaseCrashlytics` import and runtime initialization calls.
- Remove Crashlytics manifest collection metadata.
- Remove `android-tv/google-services.json` when present.
- Added scan/report of remaining textual Firebase/Crashlytics references for deliberate orphan-preference cleanup.

### Build tooling

- Added upstream bootstrap script pinned to `v1.14.0` by default.
- Records exact upstream Git commit in `UPSTREAM_BASELINE.txt`.
- Added Docker builder using JDK 8, Android platform 29, build tools 29.0.2 and NDK 21.0.6113669.
- Added Docker Compose project with Gradle cache.
- Added local build helper.
- Standardized output APK name under `artifacts/` with SHA-256 from Docker build.

### Fire TV MCP

- Added Python MCP SDK v2 project.
- Added ADB connect/device info tools.
- Added guarded dev-package install/launch/stop/uninstall.
- Added APK application-ID verification before MCP installation.
- Explicitly protects `jvl.sage.miniclient...` namespace from package mutation through MCP.
- Added Fire TV remote key and sequence tools.
- Added logcat clear/tail/wait tools.
- Added screenshot and short screen-record tools.
- Added MediaCodec/extractor/Surface diagnostic collection.
- Added combined playback-failure artifact collection.

### Documentation

- Added `README.md`.
- Added `CODE_REVIEW.md`.
- Added `TASKS.md` with phased acceptance criteria.
- Added `QUESTIONS.md` with non-blocking defaults.
- Added `AGENTS.md` Codex safety/testing rules.
- Added full `HANDOFF.md`.
- Added `CODEX_MCP_SETUP.md` and Codex MCP registration helper.
- Added `BASELINE_TEST_TEMPLATE.md` and scaffold test runner.

### Tests

- Added fixture test for separate application identity and Firebase active-code removal.
- Added MCP tests protecting production namespace and validating key mapping.
- Python compilation and included tests pass in the handoff environment.

### Deliberately not changed yet

- No ExoPlayer seek/startup algorithm changes.
- No IJKPlayer behavioral changes.
- No Media3 migration.
- No Android Gradle Plugin/SDK modernization.
- No claim of playback fixes until a real Fire TV baseline is captured.

## v0.2.5 - 2026-08-25

- Fixed first Android debug build failure when `/root/.android/debug.keystore` does not yet exist.
- Added `scripts/ensure_debug_keystore.sh` to create and verify the standard Android debug signing key (`androiddebugkey`).
- `./dev.sh build` and `./dev.sh build-existing` now create/reuse the persistent debug keystore before Gradle runs.
- The keystore is stored through the existing `/root/.android -> /workspace/adb` mapping, so it survives short-lived containers and image rebuilds.
- Added `./dev.sh ensure-debug-keystore` for explicit verification/generation.
- Added `keytool` and debug-keystore status to container preflight.

## 0.4.7 - Gradle layout self-repair

- Added `config/dev-root-build.gradle.canonical` as the canonical Dev root Gradle file.
- Added `scripts/repair_dev_gradle.py` to detect and repair the repeated `source/dev/build.gradle` / `android-shared/build.gradle` swap seen after update extraction.
- `./dev.sh test`, `./dev.sh validate`, and `./dev.sh build` now repair/verify the Dev root Gradle layout before running.
- A damaged root Gradle file is preserved as `source/dev/build.gradle.bad` before repair.
- Added regression coverage that intentionally overwrites the root file with the android-shared module file and proves repair succeeds.
- No Android player/runtime code changes.

### v0.5.20 - Native Search text/remote sequence
- Added MCP Android text injection and `dev_search_text`.
- `mcp-search-test` now defaults to Search -> `meet the press` -> Next/Enter -> Down -> Right -> Right -> Right -> Play -> Play.
- No APK/player changes.


## v0.5.44 - Restore proven Search text timing
- Standard recording startup now restores the proven Android OS `adb input text` path (`dev_type_text` / legacy `sendtext`) immediately after SageTV reports `hasTextInput=true`.
- IME visibility remains instrumented, but is verified after text injection instead of gating text entry.
- Startup no longer uses `dev_sage_command_sequence` before playback; post-Search UI commands are issued individually with `dev_sage_command`, because the sequence measurement helper requires an active player timeline.
- No Android APK source changes in this update.

## v0.5.45 - Stable verified debug seek
- Fixed a false PASS in `dev_seek_time`: `BaseMediaPlayerImpl.getMediaTimeMillis()` intentionally reports 0 while the player is not ready/flushing/seeking, which could be mistaken for a successful seek to 0 ms.
- `dev_seek_time` now ignores transient not-ready/seek-pending/flushed samples and requires Media3/Exo to return to READY before evaluating the landing.
- A landing must remain consistent with the requested target for a configurable stability window (default 1200 ms). If the player briefly reports the target and then snaps back, the seek FAILs.
- Seek results now report `landed_ms`, `stable_observed_ms`, `ready_samples`, `transient_samples_ignored`, `failure_reason`, and the final compact player state including Exo position and server anchor.
- `mcp-media3-comskip-matrix` adds `--seek-stable-ms`; no Android APK rebuild is required for this host/MCP verification fix.

## v0.5.46 - Three-minute observational watchdog + complete player matrix
- Comskip recovery observation now defaults to a 180000 ms (3 minute) configurable `--watchdog-ms`.
- Watchdog expiry is a non-fatal media observation (`WATCHDOG_EXPIRED`) with `cause=undetermined`; it does not blame Android, SageTV server, or FFmpeg and does not abort the matrix.
- Added `mcp-player-matrix`, a complete 63-case configuration harness covering Legacy Exo, Media3, IJK, and GSYVideoPlayer with Auto/Media3/System/Legacy Exo engines across Dynamic/Pull/Fixed and Hardware/Software/Hardware Preferred.
- The player matrix runs startup/A-V verification, absolute seek, forward/back direct seeks, pause/resume, and Comskip right/left checks, continuing after media recovery problems.
- Added case listing/filtering (`--list-cases`, `--case-id`, and dimension filters) for future Codex automation.
- Increased host-side playback observation ceiling to 300 seconds so a 3-minute watchdog is not truncated at 120 seconds.
- Added `PLAYER_TEST_HARNESS_v0.5.46.md` documenting matrix expansion, watchdog semantics, and future SageTV server/FFmpeg correlation.
- No Android APK source changes in this update.

## v0.5.48 - Confirm Start From Beginning with Select
- Changed the post-Search recording startup navigation from `ff`, `right`, `play_pause`, `down`, `play_pause` to `ff`, `right`, `play_pause`, `down`, `select`.
- Uses native SageTV `select` (event 20 / Enter) to confirm the highlighted Start From Beginning choice instead of toggling playback/resume with a second `play_pause`.
- Updated the legacy diagnostic sequence and regression tests to match the active startup helper.
- Host/script-only change; no Android APK rebuild required.

## v0.5.49 - Restore exact proven Search text timing
- Restored the exact historically working recording-name path: native SageTV Search command -> 50 ms -> Android OS `adb input text` -> 50 ms -> Android BACK -> 100 ms -> post-Search commands.
- Removed Search/IME readiness waits from the timing-sensitive text injection path; debug UI/IME state is collected only after injection so instrumentation cannot delay text entry.
- `dev_type_text` now reports command completion separately from field-content verification; empty ADB stdout is no longer described as proof that text was entered.
- Kept corrected Start From Beginning navigation: `ff`, `right`, `play_pause`, `down`, `select`.
- Host/MCP/script-only; no APK rebuild required.

## v0.5.50 - paced Android text entry
- Recording Search text is now injected one character at a time through Android's OS input service instead of one whole fast phrase.
- Spaces use `KEYCODE_SPACE`; normal characters use `adb shell input text <char>`.
- New CLI option `--text-char-delay-ms` controls pacing for playback, Media3, Comskip, and full player-matrix harnesses. Default is 100 ms per character; valid range is 0-2000 ms.
- The full player matrix prints the active text-character delay so repeated cases are reproducible.

## v0.5.52 - Persistent ADB shell for MCP runtime
- MCP now opens one persistent `adb shell` after `adb_connect` and reuses it for runtime test commands instead of spawning a new `adb shell` process for every status/input/debug operation.
- Runtime shell commands are framed and serialized so command output cannot interleave; the shell is automatically closed when the MCP server exits.
- Added `adb_session_status` plus persistent-shell PID/restart/command counters so Codex/test harnesses can verify reuse.
- `app_status`, wake/key/input/debug broadcasts, `dumpsys`, and other shell-based runtime operations use the persistent shell. Install/uninstall/pull/screenshot and `adb connect` remain separate ADB subcommands where required.
- Normal Search text still defaults to the proven one-shot `adb input text` path (`--text-char-delay-ms 0`); nonzero per-character pacing remains diagnostic only.
- Host/MCP-only change; no Android APK rebuild required.

## v0.5.53 - Matrix step progress / watchdog visibility
- Full `mcp-player-matrix` now prints each media check before it starts, including the per-step watchdog value.
- Long-running checks emit host-side `WAIT` heartbeats every 10 seconds with elapsed time and watchdog progress, then a `DONE` line when the MCP call returns.
- Matrix startup prints persistent ADB shell status/PID/restart count immediately after `adb_connect`, making transport reuse visible during long runs.
- This fixes the appearance of a hang after `PASS: startup ...` when the first `absolute_seek` is legitimately using its 180000 ms per-step watchdog.
- Host/script-only change; no Android APK rebuild required.

## v0.5.54
- Added debug-only native MiniClient text injection (`input_text_native` / `dev_input_text_native`) for reliable repeated Search automation.
- Native text reproduces `KeyMapProcessor.handleDefaultEvent()` keyCode/keyChar encoding instead of depending on Fire TV IME/ADB focus timing.
- Normal player-matrix startup now waits for SageTV `hasTextInput`, injects text directly over the MiniClient event channel, then hides the IME.
- Kept ADB `sendtext`/keyboard text and paced text paths for diagnostics/fallbacks.
- Debug status version advanced to 4.


## v0.5.61 - Selection names + complete Fixed encoding parameters
- Standardized user-facing streaming selections to **Push / Pull / Fixed** while preserving persisted values `dynamic / pull / fixed`.
- Standardized decoding selections to **Hardware / Software / Fallback** while preserving persisted values `hardware / software / hardware_preferred`.
- Extended `dev_set_player_config` and debug snapshots with the complete Fixed encoding parameter set: encoding policy/container, video bitrate/FPS/key-frame/B-frames/resolution, audio codec/bitrate/channels, and remux policy/container.
- Full player matrix passes and verifies every Fixed parameter before playback.
- Fixed matrix defaults force real encoding (`fixed_encoding/preference=always`, `fixed_remuxing/preference=off`) instead of allowing SageTV to skip encoding or remux instead.
- Added the same Fixed parameter CLI options to single playback/session and Media3 matrix entry points.
- Debug status version advanced to 8 because the debug receiver configuration contract changed.
