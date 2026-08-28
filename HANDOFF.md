# OpenSageTV Vibe Android Client — Handoff

## Migration state (2026-08-28)

The exact known-good v0.5.75 copy is preserved at Git commit `e770f9f`. Phase 1
work occurs only in this new repository; the original `SageTV-MiniClient-Dev`
directory remains an unmodified rollback source and is not required by any
build or test.

Pre-refactor validation passed 145 scaffold tests, 35 MCP tests, shell syntax,
the full project validator, and a clean Docker APK build. Current migration
evidence and architecture are in `MIGRATION_TO_OPENSAGETV_VIBE.md` and `docs/`.
No Android device was modified during the structural migration.

Phase 1 subsequently passed 150 scaffold/static tests, 35 MCP tests, the full
validator, and a clean 60-task Gradle build in the renamed reusable container.
The resulting Vibe-named APK has the same SHA-256 as the pre-refactor build,
proving the structural changes did not alter Android output. Exact evidence is
in `docs/PHASE1_VALIDATION.md`.

The Android application ID intentionally remains
`org.opensagetv.miniclient.dev.debug`. Keep legacy Exo as the default and do not
promote MIM/Media3 behavior until real device commissioning passes. Normal
build/test/MCP commands now reuse `opensagetv-vibe-android-dev`; do not re-add
phase-specific or disposable build containers.

## Preserved v0.5.75 handoff

### v0.5.75 client ID / test setup rule

The Dev app has returned to the original generated-and-persisted client-ID behavior. Do not hard-code `DEV001` in Android/core code. Existing installs keep whatever `client_id` is already persisted; fresh installs generate an ID on first use. Original connection-specific `ServerInfo.macAddress` override behavior is restored. Debug contract is now `debugStatusVersion=14`.

**Before any automated MCP/player test on a fresh install or cleared-data app, manually complete first-time setup and reach the normal MiniClient UI once. Tests are not allowed to substitute for first-time setup.** After setup, every automated MCP/player test wrapper defaults to `44:45:56:30:30:31` (`DEV001`) when `--client-id` is omitted. An explicit `--client-id` wins. The wrapper uses ensure semantics so an already-active matching ID does not cause another restart. Normal interactive app use remains original generated/persisted behavior.

### v0.5.74 codec queueing preference

Both Media3 and legacy ExoPlayer expose a persistent `MediaCodec Queueing Mode` setting with `sync`, `auto`, and `async`. The normal/default value is now `sync` (`Sync (Recommended)`). MCP runtime tuning remains higher priority while explicitly active; after `reset`, the next player reads the saved preference again. This is an Android code/settings change and requires one debug APK rebuild/install. `debugStatusVersion` remains 13.

### v0.5.73 tuning matrix fast-start rule

Runtime tuning sweeps default to `--startup-mode fast`: combination 1 uses the full clean-start/Search path and caches the exact current SageTV MediaFile ID; later combinations disconnect the SageTV session, apply the new tuning/config, reconnect, and replay that exact MediaFile ID through Sagex. This still creates a fresh player for each combination but avoids repeated app force-stop/Search overhead. Fast replay must fall back to the full isolated path if reconnect/replay verification fails. Use `--startup-mode isolated` when full app restart + Search isolation is specifically required. Report `startupPath`, `startupMs`, cached MediaFile ID, and fallback count.

### v0.5.72 tuning matrix startup rule

The runtime tuning matrix must establish MCP ADB with `adb_connect` and verify `adb_session_status` before the first `dev_prepare_clean_start`. This mirrors the regular player matrix and prevents an unconnected ADB transport from contaminating every combination with `INFRA_ERROR`.

# Current handoff baseline: v0.5.75

## v0.5.71 combined Push/Pull tuning

Host-side tuning matrix enhancement only; keep the installed v0.5.70 APK (`debugStatusVersion=13`). `--streaming push,pull` and `--streaming pull,push` are valid. Pull executes the full requested grid. Push automatically normalizes/ignores Pull-only parameters and varies only codec mode from the current tuning surface, so the common 2x2x2x3 example is 24 Pull + 3 Push = 27 tests. Reports include `streamingModes`, `combinationCountByStreaming`, and `ignoredTuningDimensionsByStreaming`.

Recommended combined Media3 run:

```bash
./dev.sh mcp-player-tuning-matrix --server 192.168.10.175 --text "meet the press" --player media3 --streaming push,pull --ts-search 4,8 --seek-policy closest,next --pull-read-kb 256,512 --codec-mode auto,async,sync --seek-recovery off --slow-recovery-ms 5000 --watchdog-ms 50000
```

## v0.5.70 runtime tuning workflow

Rebuild/install once and verify `debugStatusVersion=13`. Then prefer MCP tuning sweeps over recompiling one constant at a time. Runtime overrides are in-memory and reset when the Dev process restarts. `mcp-player-tuning-matrix` deliberately performs a clean playback start for every combination and reapplies the tuning immediately after clean-start.

Recommended first Media3 Pull sweep:

```bash
./dev.sh mcp-player-tuning-matrix --server 192.168.10.175 --text "meet the press" --player media3 --streaming pull --check comskip_right --ts-search 4,8 --seek-policy closest,next --pull-read-kb 256,512 --codec-mode auto,async,sync --seek-recovery off --slow-recovery-ms 5000 --watchdog-ms 50000
```

Recommended first legacy Exo2 Pull sweep:

```bash
./dev.sh mcp-player-tuning-matrix --server 192.168.10.175 --text "meet the press" --player exoplayer --streaming pull --check comskip_right --pull-read-kb 256,512,1024 --seek-policy closest,next,directional --seek-recovery off,on --codec-mode auto,async,sync --slow-recovery-ms 5000 --watchdog-ms 50000
```

A/V recovery is the ranking criterion. Seek landing is telemetry only. Do not promote a tuning combination to the compiled default from a single run; repeat the winner and a baseline control before changing production constants.



## v0.5.69 next focused test

After rebuilding/installing and verifying `debugStatusVersion=12`, derive from `20260828_015224_player_full_matrix.json`, exclude unchanged native/GSY Media3 Push cases, and use the configurable slow threshold:

```bash
./dev.sh mcp-player-matrix --server 192.168.10.175 --text "meet the press" --hardware-only --streaming push,pull --issues-only /workspace/artifacts/firetv/20260828_015224_player_full_matrix.json --exclude-case-id gsyplayer__push__hardware__gsy_media3,media3__push__hardware --slow_recover_ms 5000 --watchdog-ms 50000
```

Interpretation priorities:
- Exo2/GSY Legacy Exo Pull: compare READ count/wait and total recovery against 8.54/9.21 s.
- Media3/GSY Auto/GSY Media3 Pull: verify no `pull_seek_reprepare_*` trap fires and recovery is no longer inflated by the 10 s reset.
- GSY/System Pull/Push: verify the recording no longer terminates immediately; effective backend health should identify Media3 even though requested `gsyEngine=system` remains visible.
- Do not use unchanged Media3 Push cases to judge this Android change.

The current continuation baseline is v0.5.69 / `debugStatusVersion=12`. The authoritative recovery rule remains **A/V output recovered = PASS/FAIL; seek/timeline values are diagnostic only**. v0.5.69 applies backend-specific fixes proven by the 20260828_015224 issue-only matrix: Media3 disables the harmful 10-second Pull reprepare and returns to CLOSEST_SYNC; legacy Exo2 keeps directional sync seeking and moves to a 512 KiB Pull network block; GSY/System falls back to Media3 instead of immediately ending playback. Rebuild/install the debug APK before validation.

## v0.5.68 focused retest exclusions

- `--exclude-players` omits entire unchanged player families from full or issue-only runs.
- `--exclude-gsy-engines` omits selected GSY engines while retaining the rest; use `--exclude-gsy-engines system` while System is unchanged.
- `--exclude-case-id` omits exact generated cases.
- Reports persist all exclusions under `retestExclusions`.
- Codex rule: focused retests should skip unchanged abnormal backends unless that backend is intentionally serving as a comparison/control.
- No APK rebuild is required; `debugStatusVersion=11` remains current.

## v0.5.67 implementation

- Media3 Pull 4x experiment is closed as ineffective; baseline restored to 8x.
- >=2 s forward Pull seeks -> `NEXT_SYNC`; >=2 s backward -> `PREVIOUS_SYNC`; small seeks -> `CLOSEST_SYNC`.
- One-shot 10 s recovery reprepare if the same Pull seek generation is still BUFFERING.
- Media3/Exo2 Pull DataSource non-zero reads no longer return 0 on close/zero-read races.
- Exo2 Pull exposes cumulative server READ/open telemetry through the existing health probe reflection fields.
- Matrix requires `debugStatusVersion>=11`.

## v0.5.66 isolated issue checks + Media3 Pull 4x experiment

- Every issue-only operation performs a fresh clean stop/config/connect/Search/playback startup before the action.
- Per-check startup evidence is stored in `checkStartups`; a backend playback startup failure is a media failure, not infrastructure failure.
- Full-matrix mode retains the existing shared-session behavior.
- Media3 Pull only: `PULL_TS_TIMESTAMP_SEARCH_MULTIPLIER` is 4 instead of 8.
- Do not change constant-bitrate seeking, `CLOSEST_SYNC`, datasource read size, or LoadControl during this experiment.
- Rerun `media3 pull`, `gsy auto pull`, and `gsy media3 pull` issue cases. Keep 4x only if large Comskip recovery improves without breaking seek-to-zero behavior.
- Media3 Push, GSY System, and GSY legacy Exo behavior are deliberately unchanged in this revision.

## v0.5.64 long-wait crash/process diagnostics

- Every operation recovery wait records compact crash/process + player/A-V/Pull-I/O evidence at 5 s, 15 s, and 30 s.
- Startup playback verification performs the same milestone checks.
- A Dev-app crash signature, process death, or PID restart ends the wait early as `PLAYER_CRASHED` / `STARTUP_PLAYER_CRASHED`; full checkpoint capture still follows.
- Backend playback startup failures are not infrastructure errors. GSY/System should now appear as `STARTUP_PLAYBACK_FAILED` unless a real process crash is proven.
- Android error traps include backend error identities; debug contract is version 10.
- Do not change Media3 Pull buffering/extractor parameters until the next milestone-enabled run is compared with `MATRIX_REVIEW_20260827_221623.md`.

## v0.5.60 hardware-only selector + player-matrix Comskip rule

- Full matrix default remains 63 cases. Use `--hardware-only` (alias of `--decoders hardware`) for 21 hardware-only cases.
- `--decoders hardware|software|hardware_preferred` remains the explicit selector.
- Player-matrix Comskip must NOT hold the Android debug receiver open for the full watchdog. Send native SageTV `right`/`left` as a short command, then use host-side `dev_wait_for_playback_started` A/V-counter recovery.
- For Comskip/seek recovery, marker/landing position is not a verdict. Video/audio counters advancing again is the verdict.
- The previous duplicate-looking case header was a restart of the same configuration before the next check after an infrastructure error, not a second matrix case. With the long Comskip broadcast removed, that specific restart path should not be triggered by a healthy recovered video.
- v0.5.60 is host/MCP-only; no APK rebuild is required.

## v0.5.47 per-step watchdog contract

- `--watchdog-ms` is **per media step**, never a total-suite budget. A fresh timer starts for each absolute seek, relative seek, pause/resume, Comskip RIGHT, and Comskip LEFT observation.
- Default is 180000 ms (3 minutes) per step; max supported by the debug APK is 300000 ms.
- Debug snapshot contract is now `debugStatusVersion=3` with `maxRecoveryWatchdogMs=300000`. MCP must reject a requested long watchdog if the installed APK advertises less.
- Long debug broadcasts must not use the historical fixed 90-second ADB shell timeout; transport timeout is derived from the requested recovery/verify/settle window.
- Media watchdog expiry remains observational (`WATCHDOG_EXPIRED`, cause undetermined), not an Android/SageTV/FFmpeg attribution.
- Startup navigation after Search text entry is now: `ff`, `right`, `play_pause`, `down`, `select`. The first `play_pause` opens the resume/start-choice flow; `down` selects Start From Beginning; native SageTV `select` confirms it. Do not use a second play/pause for confirmation.
- v0.5.47 changes Android debug source, so rebuild/install before validating long watchdog timing.

## v0.5.43 deterministic Search handoff

The normal recording startup path is now explicit MCP orchestration, not a monolithic sequence:

`dev_open_search` -> verified SageTV text input + visible Fire TV IME -> `dev_input_text_keyboard` -> `dev_hide_ime` -> `dev_wait_for_ime(false)` -> direct SageTV post-search command sequence.

This was changed after a device run returned a generic `dev_send_sequence` error before Search or the Android keyboard appeared. The new path preserves phase-specific status and IME fields in compact debug state. `dev_send_sequence`, `sendkey`, and `sendtext` remain available for diagnostics only. This release is host/MCP-only and does not require an APK rebuild if v0.5.42 is already installed.

## v0.5.42 keyboard text path correction

- Preferred automated Search text path is **only**: `waittextinput` -> `waitimevisible` -> `keyboardtext <text>` -> `hideime` -> `waitimehidden`.
- `keyboardtext` uses Android OS `input text` from MCP/ADB. It must not route through `DevTestReceiver` or `View.dispatchKeyEvent()`.
- The debug APK no longer exposes the obsolete `input_text` receiver operation or the virtual-keyboard dispatch helper; both old in-app paths were removed to prevent hangs/raw-keycode mistakes.
- `directtext` is a compatibility alias to the same OS keyboard path. Keep `sendtext` / `sendkey` for diagnostics only.
- v0.5.41 accidentally omitted `scripts/mcp_playback_test.py` from its overwrite ZIP; v0.5.42 explicitly includes it.
- Rebuild/install v0.5.42 before the next device test because Android source was cleaned up.

## v0.5.41 Android OS keyboard text injection

- Preferred automated text path: wait for `hasTextInput=true` and `imeVisibleKnown=true, imeVisible=true`, then use `keyboardtext`.
- `keyboardtext` now uses Android's OS `input text` service. Do not use debug-receiver-side `View.dispatchKeyEvent()` for Search text; it can stall the receiver because the SageTV text field is not a native Android `EditText`.
- Keep `sendtext` / `sendkey` for diagnostics and physical-input reproduction. `directtext` is only a compatibility alias to `keyboardtext`.
- v0.5.41 is host/MCP-only; no APK rebuild is required when v0.5.40 is already installed.

## v0.5.40 Android keyboard text path

- Normal automation must use `dev_input_text_keyboard(text)` / `keyboardtext <text>` after `hasTextInput=true` and verified `imeVisible=true`.
- Do not send raw character values directly with `MiniClientConnection.postKeyEvent(...)`; lowercase ASCII is not a valid Java/Sage keycode mapping.
- The debug APK generates Android virtual-keyboard `KeyEvent`s and dispatches them to the focused MiniClient view, so the existing Android key listener performs the same conversion as real Fire TV keyboard input.
- `dev_input_text_direct` / `directtext` are compatibility aliases to the Android-keyboard path. `sendkey` / `sendtext` remain diagnostic input-simulation tools.
- v0.5.40 changes Android shared/debug code; rebuild/install before device testing.

## v0.5.39 direct-debug automation baseline

- Prefer debug/MCP direct operations over Android key simulation whenever a direct MiniClient/player API exists.
- Direct controls: `dev_input_text_keyboard(text)` (`dev_input_text_direct` compatibility alias), `dev_hide_ime()`, `dev_player_control(play|pause|stop)`, `dev_seek_time(target_ms)`, `dev_seek_relative(delta_ms)`, `dev_skip_forward(skip_ms)`, `dev_skip_backward(skip_ms)`, `dev_comskip(direction)`, and instrumented `dev_run_relative_seek_check`.
- `dev_skip_forward` / `dev_skip_backward` require the caller to pass the skip duration; do not hide a fixed SageTV skip interval inside the MCP tool.
- Standard recording startup uses direct SageTV Search/event commands + Android virtual-keyboard text into the focused MiniClient view + direct IME hide. Keep `sendkey` / `sendtext` / `mcp-send-sequence` as legacy diagnostic tools, not the preferred automation path.
- Media3 seek/resume matrix now uses direct relative `player.seek(...)` and direct `player.pause()` / `player.play()`; it no longer uses SageTV FF/REW commands for player recovery testing.
- Comskip marker targets are not exposed to the MiniClient. `dev_comskip` therefore posts SageTV RIGHT/LEFT directly inside the debug APK via `EventRouter`; this is not Android key injection and does not use `videoplaying_right/left` mappings. Do not replace this with a guessed local seek target.
- v0.5.33 Pull I/O diagnostics remain active around Comskip recovery.
- Android debug/shared source changed in v0.5.39; rebuild/install is required.
- Continue delivering normal updates as small overwrite ZIPs containing only changed/new files.

## v0.5.37 deterministic direct-connect startup

- Do **not** call `launch_dev_app` in automated playback/search matrices after `dev_prepare_clean_start`. The generic Android launcher resolves to the server-picker (`ServersActivity`) and is not the desired test UI.
- Required fresh-session order is: `dev_prepare_clean_start(wake=true)` -> `dev_set_player_config` -> `dev_connect_server` -> `dev_wait_for_ui(connected=true)` -> `dev_app_status` -> normalize stale SageTV UI if needed -> wait for `automationReady=true` -> Search sequence.
- `dev_app_status.running` is based on multiple Android signals. It may use `pidof`, fall back to `ps -A`, or use a resumed Dev Activity as authoritative evidence. Inspect `runningSource` when diagnosing startup.
- Keep `launch_dev_app` for manual launcher/server-picker testing only.
- v0.5.37 is host/MCP-only. The v0.5.36 debug APK contains the current IME visibility/readiness status and does not need rebuilding solely for this update.

## v0.5.36 Search/Android-keyboard synchronization

- Do not infer that the Android keyboard is shown merely because SageTV reports `hasTextInput=true`. Debug snapshots now report `imeRequested`, `imeVisibleKnown`, and `imeVisible`.
- `imeVisibleKnown=true, imeVisible=false` means the Android app has positively verified that the IME/keypad is not displayed.
- Standard test-video startup is: `command search` -> `waittextinput 8000` -> `waitimevisible 8000` -> `sendtext <required --text>` -> BACK -> `waitimehidden 5000` -> remaining playback keys.
- If IME visibility cannot be determined, `imeVisibleKnown=false`; automation must not type blindly.

## v0.5.35 Android debug build repair

- v0.5.34 device build exposed a packaging/edit regression in `DevTestReceiver.java`: calls to `configuredValues(PrefStore)` and `appendPlaybackHealth(...)` were left without matching method definitions. This was not a Media3/runtime failure; Javac correctly stopped the build.
- v0.5.35 restores `configuredValues(PrefStore)` and uses the existing `PlaybackHealthProbe.capture(player).compactWire("health_")` path directly.
- Preserve all v0.5.34 deterministic startup behavior: check running -> graceful exit -> re-check -> force-stop fallback -> verify stopped -> wake -> relaunch -> Android-owned `automationReady=true` before Search.
- Static tests now guard the receiver helper definitions so this compile regression is caught before packaging.
- Rebuild/install the debug APK before rerunning Pull Comskip diagnostics.
- Normal update delivery remains a small overwrite ZIP containing only changed/new files.

## v0.5.34 deterministic Android/MCP startup baseline

- Device failure that prompted this change: a previous run left the client at `Search - All Media Types` with `hasTextInput=true`; the old stable-menu gate incorrectly passed and the next Search sequence ran while the Android keyboard was still active. This was a startup-state failure, not a Media3 Pull playback failure.
- Debug APK snapshot owns readiness now: `debugStatusVersion=1`, `uiState` (`disconnected`, `loading`, `main_menu`, `text_input`, `menu`, `playback`) and `automationReady`. Only connected Main Menu + no popup + no text input + no player produces `automationReady=true`.
- MCP device controls added: `firetv_wake`, `dev_app_status`, `kill_dev_app`, `dev_prepare_clean_start`.
- Required fresh-test startup order: check app status -> if running request normal exit -> re-check -> force-stop only if still running -> verify stopped -> wake device -> launch -> verify process running -> configure/connect -> if stale UI send direct SageTV `home` -> wait for `automationReady=true` -> send Search/test input.
- Do not infer readiness from `menu_present` alone again. Do not treat `MiniClientOpenGLActivity` by itself as a launch failure; OpenGL vs GDX follows the persisted UI renderer preference.
- v0.5.33 Pull seek I/O diagnostics are still the active playback investigation after this startup fix. Rebuild/install the debug APK before rerunning the Pull Comskip diagnostic.
- Normal update delivery remains a small overwrite ZIP containing only changed/new files.

## v0.5.33 current device-test focus

The v0.5.32 1 MiB Media3 Pull buffer experiment was negative/inconclusive: a small ~1 s RIGHT landing recovered in 314 ms, but a ~20.8 s LEFT landing still took 3629 ms. v0.5.33 restores the 256 KiB Pull buffer and adds diagnostic-only Pull I/O counters. Do not make another seek/buffer tuning change until the Comskip matrix reports how much of recovery time is spent in socket OPEN/SageTV READ work.

## v0.5.32 Media3 Pull large-seek latency experiment

- v0.5.31 device Comskip matrix passed correctness in both modes. Dynamic recovered direct SageTV `right` in 820 ms and `left` in 566 ms; Pull recovered both but took ~3630 ms. This isolates the remaining problem to Media3 Pull large-seek recovery latency rather than Comskip command routing.
- Media3 Pull now uses a 1 MiB `BufferedPullDataSource` network read buffer instead of 256 KiB. The intent is to reduce synchronous SageTV READ round trips while Media3 scans the existing 8x MPEG-TS timestamp/PCR search window after a large marker jump.
- Do not change the 8x timestamp-search window, Pull LoadControl, decoder policy, direct Comskip `right`/`left` commands, Legacy Exo Pull, IJK, or Dynamic/PUSH as part of this experiment.
- This changes Android player source, so rebuild/install the debug APK before retesting. Then run `./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>" --modes pull` first. Compare Pull recovery against the v0.5.31 ~3630 ms baseline.
- Normal update delivery remains a small overwrite ZIP containing only changed/new files.

## v0.5.31 Comskip correction — direct SageTV RIGHT/LEFT

- Device proof: `./dev.sh mcp-send-sequence` with `command right` jumps to the next Comskip point; `command left` jumps to the previous point.
- The previous Comskip helper incorrectly resolved the normal `videoplaying_right` / `videoplaying_left` mapping. Those are short-press FF/REW mappings and are not the Comskip action.
- `dev_run_comskip_check` now sends direct SageTV `right` / `left` through the existing instrumented `skip_check` path. This is the same native Sage command as `mcp-send-sequence`, with A/V recovery/timeline diagnostics wrapped around it.
- Do not emulate Android long-press timing in automation and do not substitute FF/REW. For Comskip tests, send the native SageTV command directly.
- No Android source changed for this correction; no APK rebuild is required.

## v0.5.30 mandatory test-recording text

- Native Search-based test automation has **no default recording title**. The recording/search text must always be supplied with `--text`.
- `mcp-playback-test`, `mcp-media3-matrix`, `mcp-media3-comskip-matrix`, and `mcp-search-test` fail argument parsing immediately when `--text` is omitted.
- MCP `dev_search_text` also requires its `text` argument; do not add a fallback title back into the MCP server.
- Current next command: `./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>"`.
- Preserve the native Search/key sequence itself; only the search text is parameterized.
- Normal update delivery remains a small overwrite ZIP containing only changed/new files.

## Media3 Comskip Push/Dynamic vs Pull next device test

- v0.5.28 device matrix **passed** in both modes using real A/V output health: Dynamic FF 815 ms / REW 816 ms / rapid mixed 310 ms; Pull FF 567 ms / REW 1585 ms / rapid mixed 324 ms. This closes the Media3 Push/Pull normal seek-recovery gate; do not make speculative Media3 Pull buffer/DataSource changes from this result.
- Pause/resume was slow in both modes (Dynamic 5456 ms, Pull 5267 ms). Treat this as a common-path latency item, not a Pull-specific regression.
- Next command: `./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>"`. It runs fresh Media3 Hardware sessions in Dynamic then Pull, waits for the proven stable UI, starts the explicitly selected recording through the exact native Search sequence, then tests direct SageTV `right` and `left` Comskip recovery.
- Comskip marker timestamps are not semantically exposed to the MiniClient. Verdicts therefore require real decoded A/V recovery; the report records the direct SageTV command, recovery latency, landing timeline and jump magnitude for comparison.
- Normal update delivery remains a small overwrite ZIP containing only changed/new files.

## v0.5.28 current next-phase baseline — Media3 Push/Dynamic vs Pull

- v0.5.27 device result: the native playback-start workflow passes after requiring a non-empty SageTV UI/menu state to remain stable for 2000 ms before Search. Keep that readiness gate.
- Host command: `./dev.sh mcp-media3-matrix --server 192.168.10.175 --text "<recording search text>"`.
- The matrix intentionally changes **no Media3/DataSource/player code**. First collect comparable evidence from fresh Push/Dynamic and Pull sessions.
- Per mode: configure Media3 + Hardware, connect with `save=false`, wait for stable UI, run the exact native Search sequence using the required `--text` value, verify startup, then test single FF, single REW, rapid `FF,FF,REW,FF,REW`, and pause -> play.
- Seek verdicts are based on real decoded video/audio recovery. Timeline deltas are informational.
- The matrix writes a JSON side-by-side recovery report under `artifacts/firetv`. A recovery time above 2000 ms is a warning that triggers a diagnostic checkpoint; it does not fail an otherwise healthy seek.
- If either basic FF/REW check fails, preserve diagnostics and do not continue rapid/pause stress in that mode. The other streaming mode should still run.
- Normal update delivery remains a small overwrite ZIP containing only changed/new files.

## v0.5.27 stable UI readiness before playback automation

- Device testing showed `connected=true` can occur while the MiniClient is still displaying/loading the SageTV UI. Do not send native Search/navigation immediately on connection alone.
- `dev_wait_for_ui` supports `menu_present` and `stable_ms`; `mcp-playback-test` and `mcp-session-test` require a non-empty menu hint stable for 2000 ms before playback-start actions.
- Keep the exact v0.5.26 native Search/text/key sequence unchanged; the fix is only a readiness gate before that sequence.
- This is MCP/host-side only. No Android player source changed.

## v0.5.26 native playback-start automation

- Fire TV reboot proved the prior all-player failure was device/session state: the older MiniClient app also failed until reboot. The temporary v0.5.25 source rollback is reverted; v0.5.24 Android debug/session-control behavior is restored.
- New host command: `./dev.sh mcp-playback-test`.
- Default server: `192.168.10.175`; connect is direct by address with `save=false` so no additional saved-server entry is created.
- Default settings: `player=media3`, `streaming=dynamic`, `decoder=hardware`, `gsy_engine=auto`. All are overridable.
- Start-video path is native MiniClient only, using the exact user-validated sequence: Search, 50 ms, required `--text` value, 50 ms, BACK, 100 ms, FAST_FORWARD, RIGHT, PLAY_PAUSE, PLAY_PAUSE.
- Do not reintroduce Sagex/HTTP as the normal start path. `dev_play_video` remains available from v0.5.18 for compatibility but `mcp-playback-test` does not use it.
- PASS requires `dev_wait_for_playback_started` to verify real playback after the input sequence.

## Project handoff and AI-chat packaging rule

- **Default update delivery:** package only the new/changed file(s) in a small ZIP, preserving their project-relative paths, so the ZIP can be extracted directly over the main `SageTV-MiniClient-Dev` folder. Do **not** send a full project ZIP for normal fixes or incremental work unless the user explicitly asks for one.
- **Starting a new AI chat / project handoff:** run `create_ai_handoff_zip.cmd` from the project root. It creates a compact handoff ZIP containing the authoritative code, documentation, scripts, tests, MCP tooling, Gradle files, and required local Android libraries needed to continue the project.
- `source\dev` is the authoritative active source tree. `source\existing` is a frozen/original comparison tree and is intentionally omitted from the normal AI handoff ZIP to avoid duplicating the project. The compact package carries `source\FROZEN_BASELINE.sha256` so the required frozen playback invariant can still be validated and `./dev.sh test`, `./dev.sh validate`, and `./dev.sh build` remain usable without the duplicate tree. Include the full `source\existing` tree separately only when a task specifically requires broader original-source comparison or an untouched-baseline build.
- The AI handoff ZIP must retain required build inputs such as `.aar`, `.jar`, and `.so` libraries. It must exclude generated or machine-local data such as `build` directories, `.gradle`, `.idea`, `.git`, `__pycache__`, APK/AAB outputs, ADB downloads/caches, `artifacts`, `incoming`, `logs`, `screenshots`, `recordings`, `.env`, `local.properties`, signing/keystore files, temporary files, prior ZIP outputs, and `source\dev\playstore` store-listing artwork that is not part of the Android TV build.
- The packaging script regenerates `PROJECT_MANIFEST.sha256` inside the handoff package so the manifest describes the compact package rather than stale/generated files from a prior build.
- The handoff ZIP must be portable across Windows and Linux/ChatGPT environments. ZIP entry names are written with `/` separators rather than Windows-only `\` separators.
- When an AI chat produces a fix, continue using the **small overwrite ZIP** workflow above. A full project/handoff ZIP is for a new-chat baseline or only when explicitly requested.

## v0.5.24 explicit MCP input sequence

MCP now supports one explicit multiline `dev_send_sequence` call using only `command`, `sendkey`, `sendtext`, and `delay`. `./dev.sh mcp-send-sequence` reads the multiline script, connects ADB, and invokes that MCP tool once. There are no implicit post-search keys. No APK change.

## v0.5.23 keyboard-dismiss correction

The native MCP Search flow now uses Android BACK immediately after text injection to dismiss the visible IME before navigation. Default sequence: Search -> text -> keyboard dismiss -> Down -> Right x3 -> Play/Pause x2. No APK changes.

# SageTV MiniClient Dev Handoff — v0.5.17

> v0.5.18 adds end-to-end client session control and `dev_play_video(video_name)`: MCP applies settings before playback, connects the Dev MiniClient, resolves a named MediaFile through the connected SageTV server API in this client's UI context, verifies real A/V startup, and can exit cleanly. v0.5.17 keeps MCP seek validation output-health-first and fixes two debug-harness problems found on-device: AC-3 AudioTrack head resets after REW and foreground-broadcast timeouts during slow Pull checks. Timeline movement is diagnostic only; Exo2/Media3-based checks require actual rendered video buffers, advancing Android AudioTrack output, a valid Surface, READY/playing state, and continued output after recovery.

## Proven device state

- Legacy ExoPlayer Pull: startup/rapid/zero/pause/comskip pass, but video can remain stalled after a seek before eventually resuming.
- Media3 Pull: same.
- GSY Auto Pull: same because Auto delegates to Media3.
- GSY Auto Dynamic: passes.
- IJK Pull: passes the full functional seek matrix and remains unchanged.

This closes the previous seek-to-zero correctness problem. Do not undo the v0.5.8 8x TS timestamp search or closest-sync seeking without new device evidence.

## v0.5.9 change

The remaining latency is addressed at two layers, only for Exo-based Pull:

1. `BufferedPullDataSource` now supports a caller-selected network read buffer. Its default remains 32 KiB for compatibility. Exo2/Media3 use 256 KiB so the TS timestamp scan and post-seek refill require fewer synchronous SageTV `READ` commands.
2. Exo2/Media3 use a Pull-only `DefaultLoadControl`: 5 s min, 20 s max, 500 ms resume after a user seek, 1000 ms resume after a rebuffer. Dynamic/PUSH retains its existing behavior.

Additional Pull state logs report playback state, current position, buffered position and `playWhenReady`.

## Test priority

Use `PHASE_A_FOLLOWUP_TEST_v0.5.9.md`. Measure visible picture-resume delay after a single +30/-10 and large comskip. If a correct seek still stalls, collect `./dev.sh player-diag` while stalled so the new buffer/state logs can separate datasource delay from decoder/render delay.

## Deferred

- Phase B: common SageTV timeline/seek state and remaining Dynamic/comskip timeline anomalies.
- Phase C: PUSH/Dynamic server rebase versus local seek semantics.
- IJK MPEG-2 hardware decoding remains frozen.
- Future HTTP MPEG-TS, HLS, RTMP, and SRT transport reserves remain preserved.

Handoff version: **0.5.28**  
Date: **2026-08-27**


## v0.5.10 PUSH fix
Exo2/Media3 PUSH adapters now use `PushBufferDataSource.readBlocking()` so transient empty push buffers wait for bytes instead of returning an invalid zero-length read to Exo. IJK remains on the historical non-blocking path.


## v0.5.11–v0.5.13 MCP automation

- `DevTestReceiver` exists only under `android-tv/src/debug`; production/release manifests do not expose it.
- Snapshot requests are pull-only/on-demand and do not install player listeners.
- MCP can set player/stream/decode/GSY preferences, send exact SageTV commands, read current media time/state/buffer/file position, validate timeline deltas, and capture checkpoints.
- `./dev.sh mcp-seek-test` automates +30, -10, rapid `+30,+30,-10,+60,-30`, and pause/resume against the currently playing known recording.
- Recording selection and visual tearing remain manual gates for now.
- No Exo/Media3/IJK/GSY playback implementation is changed by v0.5.12 or v0.5.13; these releases correct the automation harness only.
- v0.5.13 defaults calibration to a 5-second preference quantum and reports semantic vs represented targets instead of aborting on non-divisible custom intervals.
## v0.5.14 Android-side skip measurement

- `DevTestReceiver` adds debug-only `skip_check`.
- Android captures `timelineBeforeMs` and `timelineAfterMs` from the same `player.getMediaTimeMillis(mediaCmd.getLastServerStartPosition())` expression used by `MEDIACMD_GETMEDIATIME`.
- Android returns raw `timelineDeltaMs` plus `playbackAdjustedDeltaMs` when playback remained in PLAY state.
- `dev_run_seek_check` now uses this Android result directly.
- Snapshot output includes explicit `sageTimelineMs`/`timelineSource` aliases while preserving `mediaTimeMs`.
- Calibration is still paused and multi-sample so decoder stalls do not become skip configuration values.
- Rebuild/install the debug APK for v0.5.14; this release changes `android-tv/src/debug`.


## v0.5.15 MCP output-health rule

- Do **not** use SageTV timeline movement alone as proof that playback recovered; it can advance during black-screen or silent stalls.
- `PlaybackHealthProbe` is debug-only and on-demand. It uses reflection over the already-live Exo2/Media3 renderers and AudioTrack; it installs no permanent listeners.
- For supported Exo paths, `dev_run_seek_check` passes only when video output and audio output recover, player/surface are healthy, and both continue advancing for the verification window.
- Results include decoder names/kinds, renderer counters, AudioTrack head/state, Surface state, buffering/loading, datasource, buffer/file read position, decoder reinit/release changes, and explicit failure reasons.
- Default `mcp-seek-test` no longer calibrates timeline. Optional `--calibrate-timeline` is for timing diagnostics only.
- IJK/System get useful basic snapshots but strict renderer/audio-output health is currently authoritative only where Exo2/Media3 internals can be inspected.
- v0.5.15 changes debug Android code, so rebuild/install the debug APK before testing.


## v0.5.16 Comskip MCP rule

- Use `./dev.sh mcp-comskip-test` to exercise direct SageTV `right` / `left` Comskip commands with A/V recovery instrumentation.
- Android resolves `videoplaying_right` / `videoplaying_left` at runtime; do not hard-code FF/REW in the test.
- Comskip/STV marker start/end metadata is not exposed semantically to this MiniClient. Do not invent it.
- The authoritative landing is `outputRecoveryTimelineMs`: SageTV timeline at the point real video + audio output have recovered. `videoRecoveryTimelineMs` and `audioRecoveryTimelineMs` are also returned separately.
- PASS requires real A/V recovery and continued playback. Optional `expected_target_ms` can validate a known marker target when supplied externally.


## v0.5.17 device-evidence correction

- Exo2/Pull single REW produced hundreds of rendered video frames and returned to player-ready state, but the health check timed out only on `audio_not_advancing`. The active stream was AC-3/passthrough and reused the same AudioTrack session while resetting the playback-head counter. v0.5.17 treats that reset as audio progress and exposes reset flags in the result.
- Media3/Pull and GSY Auto/Pull crash reports were preceded by GFX/session disconnects while long MCP health operations were active. v0.5.17 removes `--receiver-foreground` only for long skip/comskip checks so Android's tight foreground-broadcast timeout does not interfere with slow Pull recovery. This is a harness correction; device retest is required before changing Media3 player code.
- If either Media3/Pull or GSY Auto/Pull still crashes with v0.5.17, collect the automatic checkpoint plus `./dev.sh player-diag <label>` immediately; then investigate the actual Media3 Pull player/datasource path rather than the MCP transport.
- If a basic FF or REW health check fails, the suite now checkpoints and stops before rapid stress so the failure state is preserved.

## v0.5.18 MCP session/play-by-name rule

- Preferred playback-start entry point: `dev_play_video(video_name)`. The MCP caller sends only a video/recording title.
- Android debug snapshot exposes `serverAddress`, fixed Dev `clientId=44:45:56:30:30:31`, and `uiContextHint=444556303031`.
- Host MCP auto-discovers Sagex Remote API, calls `GetUIContextNames`, resolves the client context, pages `GetMediaFiles` using `MediaTitle|MediaFileID`, and invokes `Watch(mediafile:<id>)` with that context.
- Exact case-insensitive title match wins. Unique partial match is allowed. Ambiguous results are returned to the caller and playback is not started.
- After real A/V output is healthy, MCP attempts `GetCurrentMediaFile` in the same context to verify the current MediaFile ID.
- `mcp-session-test` order: launch -> configure -> connect -> play by name -> output-health verification -> optional seek/comskip -> exit. STV menu navigation is no longer required for the normal path.
- Custom Sagex endpoint/auth are environment configuration, not required MCP call arguments: `SAGETV_SAGEX_BASE`, `SAGETV_SAGEX_USER`, `SAGETV_SAGEX_PASSWORD`.

### v0.5.21 MCP Search correction
- Default Search automation does not send keyboard Enter/Next after text entry. It sends Play first, then Down, Right, Right, Right, Play, Play.
## v0.5.38 debug time seek

The debug APK exposes a generic absolute time seek through MCP. Use `dev_seek_time(target_ms=...)` or `./dev.sh mcp-seek-time --target-ms N`; the target is always supplied by the caller (`0` = beginning). The Media3 Comskip matrix passes `--start-ms` into this same command before Comskip checks so test playback can start from a deterministic time without hard-coding the seek target inside Android. The older `dev_local_seek_absolute` remains for compatibility/backend isolation.



## v0.5.44 - Restore proven Search text timing
- Standard recording startup now restores the proven Android OS `adb input text` path (`dev_type_text` / legacy `sendtext`) immediately after SageTV reports `hasTextInput=true`.
- IME visibility remains instrumented, but is verified after text injection instead of gating text entry.
- Startup no longer uses `dev_sage_command_sequence` before playback; post-Search UI commands are issued individually with `dev_sage_command`, because the sequence measurement helper requires an active player timeline.
- No Android APK source changes in this update.

### v0.5.45 seek-verification rule
Never treat a one-shot `mediaTimeMs == 0` immediately after `player.seek()` as a successful landing. `BaseMediaPlayerImpl` deliberately returns 0 while not ready/flushing. `dev_seek_time` must require backend READY, clear seek/flush flags, and a stable target-relative timeline before PASS.

## v0.5.46 player-matrix harness
A new host-side `mcp-player-matrix` command generates the complete 63-case player configuration matrix: 27 non-GSY cases plus 36 GSY cases covering `auto`, `media3`, `system`, and `legacy_exo`. Default media watchdog is 180000 ms (3 minutes). `WATCHDOG_EXPIRED` is deliberately observational and non-fatal; cause stays `undetermined` until Android/SageTV server/FFmpeg evidence proves otherwise. The harness continues through later cases and only treats automation/infrastructure errors as harness failures. No APK rebuild is required for v0.5.46.

## v0.5.49 Search text timing rule
For the native recording-start automation, do not wait for `hasTextInput` or `imeVisible` before text injection. On the tested Fire TV/OpenGL MiniClient, those debug signals can arrive only after focus has shifted enough that `adb input text` becomes unreliable. Preserve the proven timing exactly: Search command -> 50 ms -> `adb input text` -> 50 ms -> Android BACK -> 100 ms -> `ff`, `right`, `play_pause`, `down`, `select`. UI/IME snapshots after injection are diagnostic only. `dev_type_text` command completion is not field-content verification; successful recording playback is the end-to-end proof.

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

## v0.5.53 matrix progress rule
`mcp-player-matrix` can legitimately block inside one media operation for the complete per-step watchdog (default 180000 ms). Never leave that period silent: print `STEP`, 10-second `WAIT` heartbeats, and `DONE`. The heartbeat is host-side only and must not issue concurrent MCP/ADB commands while the worker owns the blocking request. Also print `adb_session_status` at matrix start so persistent-shell reuse is visible.

### v0.5.54 native Search text rule
Normal MCP/player-matrix recording startup must use the debug APK native MiniClient keyboard-event path (`dev_input_text_native`, via `dev_type_text` when `char_delay_ms=0`). Do not depend on `adb input text` or Android IME focus for normal repeated tests. The correct SageTV text event encoding mirrors `KeyMapProcessor.handleDefaultEvent()`: letters use uppercase Java/AWT virtual keyCode while keyChar carries the actual character. Legacy ADB `sendtext` remains available only for diagnostics/fallback testing.

### v0.5.55 Fire TV wake/launch rule
Before standalone Dev-app launch or deterministic test startup, wake the device and send HOME. Prefer package-manager launcher resolution + `am start -W` for `./dev.sh launch`; do not use Fire OS `monkey` as the primary launcher because it may return 251 (`SYS_KEYS has no physical keys`). Keep `monkey` only as fallback.

### v0.5.56 MCP native text / IME behavior
MCP native SageTV text entry temporarily suppresses the Android/Fire TV soft keyboard before opening Search. The SageTV `hasTextInput` hint still works, but `showHideKeyboard(true)` is ignored while the debug suppression flag is active. If an IME was already visible it is hidden directly; otherwise no hide/BACK action is sent. Suppression is restored after the automated Search/start sequence, so manual app text entry remains unchanged. Debug snapshots report `imeSuppressedForDebug` (status version 5).

### v0.5.57 seek-coordinate correction
Direct debug seeks now use one coordinate system end-to-end: backend-local player milliseconds. `dev_seek_time(0)` is verified against `health_playerPositionMs` (or a local-position fallback), not SageTV's anchored timeline. Relative debug seeks also start from local player position before applying deltas. `sageTimelineMs` and `serverAnchorMs` remain diagnostic context only. This fixes false long watchdog waits where playback was healthy but the anchored timeline could never be near the local target.

Debug APK capability marker: `debugStatusVersion=6`. MCP direct seek tools reject older debug APKs so the full matrix cannot silently use the old anchored-coordinate relative seek implementation.
### v0.5.58 seek-verifier wrapper-state rule
Direct absolute-seek verification must not require `BaseMediaPlayerImpl.playerReady`, `seekPending`, or `flushed` to clear. Legacy Exo can leave `seekPending=true` indefinitely after a seek that is effectively a no-op (for example, seeking to 0 when already near 0) because no `DISCONTINUITY_REASON_SEEK` callback is guaranteed. Use backend-local position + backend READY/error state for the verdict. Keep wrapper flags diagnostic-only and report stale-sample counts. Host/MCP-only; debug status version 6 remains sufficient.
### v0.5.59 A/V-counter recovery rule

For player-matrix recovery tests, landing position is not a pass/fail criterion. After a seek or other media action, recovery is proven by the existing playback-health counters: rendered video advances and audio playback-head/render counters advance again. Wrapper flags and timeline/position remain diagnostic only. The CLI prints each step before execution and prints the A/V counter verdict after completion.



## v0.5.61 configuration contract
Use user-facing selections `streaming=push|pull|fixed` and `decoding=hardware|software|fallback`. The debug APK maps Push to persisted `dynamic` and Fallback to persisted `hardware_preferred`; do not change those stored values.

For Fixed testing, always pass the complete encoding block. Defaults are: encoding preference `always`, format `matroska`, video bitrate 4000 kbps, FPS `source`, key-frame interval 10, B-frames true, resolution `source`, audio codec `ac3`, audio bitrate 128 kbps, channels `source`, remux preference `off`, remux format `matroska`. The full matrix verifies the response from `dev_set_player_config` before connecting/playing. Debug status version is 8.

## v0.5.62 matrix recovery rule
All full-matrix post-startup actions are now host-polled through repeated `dev_player_state` calls over the same persistent ADB shell. Do not reintroduce long blocking Android recovery broadcasts for matrix seeks/Comskip. The authoritative PASS signal is expected A/V output counters advancing between adjacent post-action samples. Position/landing and wrapper/surface/error fields are diagnostic-only. Watchdog progress must rewrite one CLI line dynamically and show the live `waitingFor` reason. Final per-step output must retain detailed counters, decoder state, and Pull I/O deltas. Host-only; debugStatusVersion 8 is sufficient.

## v0.5.63 exact-event MCP/Codex diagnostics

This is the continuation baseline after v0.5.62 hardware Push/Pull review.

### Verdict rule
A/V output recovery is authoritative. Do not fail seek/skip/Comskip merely because the landing position is not exact. Seek position sanity is diagnostic only (default +/-10 seconds).

### Timeline fields
- `serverRequestedSeekMs`: last SageTV `MEDIACMD_SEEK` target.
- `serverAnchorMs`: Push anchor derived from post-flush SageTV mux time.
- `health_playerPositionMs`: Android/backend local position.
- `sageTimelineMs`: value reported back to SageTV by the MiniClient timeline calculation.
- Server seek/flush/anchor sequences and monotonic ages are exposed in debug status v9.

### Exact-event traps
Debug APK only. A 32-event ring captures video/audio counters at important callbacks. `dev_clear_player_events` should be called before a focused action; `dev_player_events` returns the ring afterward. The full player matrix does this automatically and embeds it under each result's `eventTraps`.

The Exo2 and Media3 implementations reuse their existing `Player.Listener`; no continuous AnalyticsListener is installed. Protocol-command traps are non-blocking: event time is latched immediately, then the renderer/audio snapshot is taken on the main thread and `snapshotLagMs` records the delay.

### Recovery hardening
- Counter decrease/reset to 0 is not progress.
- Counter decrease/reset to a positive value can prove a restarted output pipeline.
- AudioTrack session/head reset requires playing + non-zero new head.
- Expected audio/video streams are latched from pre-action state so temporary metadata disappearance cannot create a PASS.
- Default `SLOW_RECOVERY` threshold is 5000 ms; configurable.

### Required next device validation
Rebuild/install debug APK, verify `debugStatusVersion=9`, then rerun the hardware Push/Pull matrix. Use the event ring to identify the exact state/counter transition during any long Media3-family Comskip recovery before changing player code.


## v0.5.65 focused regression mode

`mcp-player-matrix --issues-only` now runs the known abnormal subset from the latest v0.5.64 hardware Push/Pull report. `--issues-only <previous-report.json>` is preferred during iteration because it derives the exact non-RECOVERED startup/case/check combinations from that report. The current profile is 9 configurations / 16 media operations / 2 startup-only System cases. This is host-only; `debugStatusVersion=10` remains current.

Android behavior was reviewed but not speculatively changed in v0.5.65. Highest-priority experiments are: (1) prove PUSH server-seek/FLUSH ownership before suppressing Media3 local seekTo in PUSH, (2) test Media3 Pull TS timestamp search 8x -> 4x as a single-variable experiment against the repeatable 191 x 256 KiB read burst and ~33 s Comskip recovery, and (3) instrument GSY/System MediaDataSource reads around its `what=1 / extra=-2147483648` startup failure. See `MATRIX_REVIEW_20260827_232446.md`.
