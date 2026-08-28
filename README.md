# OpenSageTV Vibe Android Client

This is the Android MiniClient component of OpenSageTV Vibe. The current active
behavioral baseline is v0.5.75. It is being migrated in this independent Git
repository without modifying the known-good `SageTV-MiniClient-Dev` rollback
copy.

The repository is self-contained: active and frozen source, Gradle wrappers,
native/Java dependencies, MCP tooling, tests, and Docker build tooling are all
present here. See [MIGRATION_TO_OPENSAGETV_VIBE.md](MIGRATION_TO_OPENSAGETV_VIBE.md)
for provenance and phase status, [docs/BASELINE_VALIDATION.md](docs/BASELINE_VALIDATION.md)
for verified copy/build evidence, and
[docs/REPOSITORY_LAYOUT.md](docs/REPOSITORY_LAYOUT.md) for ownership boundaries.
The project is licensed under the [Apache License 2.0](LICENSE).

## Quick start

Docker with Compose is the only host build dependency.

```bash
./dev.sh image
./dev.sh test
./dev.sh validate
./dev.sh build
```

These commands reuse `opensagetv-vibe-android-dev`; source edits do not create a
new container or require an image rebuild. APKs are written under `artifacts/`.
Use `./dev.sh shell`, `./dev.sh stop-dev`, or `./dev.sh remove-dev` for explicit
container lifecycle control.

Device-backed install, launch, and playback commands are separate commissioning
operations. They may target only `org.opensagetv.miniclient.dev.debug`;
production package identities remain protected.

## Preserved v0.5.75 engineering record

The chronological material below is retained intact as migration evidence. It
will be indexed only after this working copy passes independent device
equivalence.

### Tuning-matrix ADB preflight

`mcp-player-tuning-matrix` connects ADB once before the first combination and verifies the MCP ADB session before any `dev_prepare_clean_start` call. If the Fire TV/Android device is unavailable, the suite now fails immediately instead of generating an infrastructure error for every tuning combination.

# SageTV MiniClient Dev — current v0.5.75

Current debug capability: `debugStatusVersion=14`. v0.5.75 restores the original generated/persisted SageTV client-ID behavior and adds an explicit debug CLI override for deterministic testing. MediaCodec queueing remains `Sync (Recommended)` by default. A/V output recovery remains the verdict; seek landing remains diagnostic only.

## First-time setup is required before automated tests

**On every fresh install, reinstall that clears app data, or manual Clear Data, launch the Dev app and complete its first-time setup before any automated MCP/player test.** Select/confirm the SageTV server and reach the normal MiniClient UI once. Do not use `mcp-player-matrix`, tuning matrices, Search automation, session tests, or any other automated test as the first-time setup procedure.

This setup step allows the app to create and persist its normal client ID. **After setup, automated MCP/player test commands default to the deterministic test ID `44:45:56:30:30:31` (`DEV001`) unless `--client-id` is explicitly provided.** Normal interactive app use still uses the generated/persisted ID until a test or CLI command intentionally changes it.

## v0.5.75 original client ID behavior + CLI override

The Dev app no longer hard-codes one client ID in source. It has returned to the original OpenSageTV behavior: if `client_id` does not exist, a random six-character identity is generated, converted to SageTV's six-byte colonized form, saved, and reused. The original per-server client-ID override behavior is also restored.

Upgrading from an older fixed-ID Dev build does **not** erase the saved preference. If the current Fire TV already has the former test ID, it stays in use until you explicitly change or regenerate it. The former/current deterministic test example is:

```text
DEV001 -> 44:45:56:30:30:31
```

Automated tests use the deterministic ID by default:

```bash
# Uses 44:45:56:30:30:31 automatically
./dev.sh mcp-player-matrix --server 192.168.10.175 --text "meet the press"

# Explicit test override
./dev.sh mcp-player-matrix --server 192.168.10.175 --text "meet the press" --client-id 41:42:43:44:45:46
```

The same `--client-id` rule applies to all automated MCP/player test wrappers. If omitted, `44:45:56:30:30:31` is ensured before the test begins; if it is already active, the app is not restarted. First-time setup must still be completed manually before any of these tests.

Use the standalone CLI after first-time setup:

```bash
# Show configured and currently active IDs
./dev.sh client-id --show

# Set the same deterministic ID previously hard-coded for testing
./dev.sh client-id --set DEV001

# Equivalent explicit SageTV byte form
./dev.sh client-id --set 44:45:56:30:30:31

# Return to a newly generated original-style random ID
./dev.sh client-id --generate
```

`--set` and `--generate` persist the value and restart the Dev app so the next SageTV connection uses it. Automated test wrappers internally use `--ensure` so the app is restarted only when the requested test ID is not already configured/active. The Android Settings client-ID field is editable again. This debug CLI requires `debugStatusVersion >= 14`.

## v0.5.74 codec queueing setting

Media3 and legacy ExoPlayer now expose **MediaCodec Queueing Mode** in their player-specific settings. `Sync (Recommended)` is the default; `Auto` and `Async` remain selectable. The saved preference is applied when a new player is created. Dev MCP runtime tuning can temporarily override it, and a tuning reset returns the next player to the saved preference.


## v0.5.73 fast tuning startup

`mcp-player-tuning-matrix` now defaults to `--startup-mode fast`. The first combination performs the normal full clean start and Search, then caches the exact SageTV MediaFile ID. Later combinations disconnect/reconnect the SageTV session, create a fresh player with the new tuning, and replay that exact MediaFile directly through Sagex instead of force-stopping the app and repeating Search.

Use `--startup-mode isolated` to retain the old maximum-isolation behavior for every combination. Fast mode automatically falls back to isolated startup if its replay verification fails. Console/report output includes `startupPath` and `startupMs` so setup time is separated from playback recovery time. `--fast-ui-stable-ms` defaults to 500 ms.

No APK rebuild is required for v0.5.73; it uses the existing `debugStatusVersion=13` APK.

## v0.5.71 combined Push/Pull tuning matrix

`mcp-player-tuning-matrix --streaming` accepts comma-separated modes. Pull uses the complete tuning grid; Push automatically collapses the grid to parameters that actually affect Push. From the current runtime tuning surface, only MediaCodec queueing mode varies in Push.

```bash
./dev.sh mcp-player-tuning-matrix \
  --server 192.168.10.175 \
  --text "meet the press" \
  --player media3 \
  --streaming push,pull \
  --ts-search 4,8 \
  --seek-policy closest,next \
  --pull-read-kb 256,512 \
  --codec-mode auto,async,sync \
  --seek-recovery off \
  --slow-recovery-ms 5000 \
  --watchdog-ms 50000
```

This example executes 24 Pull combinations and only 3 Push combinations (`codecMode=auto/async/sync`) for 27 total tests. Push does not use the TS-search, Pull read-size, Pull load-control, Pull seek-recovery, or Pull directional-seek tuning values, so those dimensions are intentionally ignored rather than multiplied into redundant Push cases. Reports preserve the ignored dimensions and per-streaming combination counts.

No APK rebuild is required for v0.5.71; it uses the existing v0.5.70 `debugStatusVersion=13` runtime hooks.

## v0.5.70 runtime player tuning

One debug APK rebuild installs the tuning hooks. After that, `mcp-player-tune` changes in-memory values and `mcp-player-tuning-matrix` runs fresh playback for each parameter combination.

Tunable parameters per Media3/legacy Exo2 backend:

- MPEG-TS timestamp-search multiplier.
- Pull network read block size.
- seek policy: `closest`, `next`, `previous`, `directional`.
- Pull min/max/playback/rebuffer thresholds.
- one-shot seek recovery enable/delay.
- directional-sync minimum delta.
- MediaCodec queueing mode: `auto`, `async`, `sync`.

Examples:

```bash
./dev.sh mcp-player-tune --backend media3 --ts-search-multiplier 8 --seek-policy next --pull-read-kb 512 --codec-mode auto

./dev.sh mcp-player-tuning-matrix --server 192.168.10.175 --text "meet the press" --player media3 --streaming pull --check comskip_right --ts-search 4,8 --seek-policy closest,next --pull-read-kb 256,512 --slow-recovery-ms 5000

./dev.sh mcp-player-tuning-matrix --server 192.168.10.175 --text "meet the press" --player exoplayer --streaming pull --check comskip_right --profiles default,large_read,async_codec,sync_codec,no_reprepare --slow-recovery-ms 5000
```

Every tuning run resets to compiled defaults before applying the next combination. The selected/effective tuning values are included in `dev_player_state` and the tuning report. Process restart also restores compiled defaults.

## v0.5.67 Pull seek recovery experiment

- Media3 and Exo2 Pull choose `NEXT_SYNC` for large forward seeks and `PREVIOUS_SYNC` for large backward seeks; seeks under 2 seconds keep `CLOSEST_SYNC`.
- A Pull seek still in `STATE_BUFFERING` after 10 seconds gets one reprepare at the same target. This is generation-scoped and cannot loop.
- Exo2 Pull now reports the same server READ/open counters as Media3.
- Non-zero Pull reads never return an invalid 0 when the backing source is gone.
- Rebuild/install the Dev APK and verify `debugStatusVersion=11` before evaluating v0.5.67.

## v0.5.66 focused issue isolation

- `mcp-player-matrix --issues-only [previous-report.json]` isolates every selected operation with a full clean playback startup.
- Add `--exclude-players`, `--exclude-gsy-engines`, or `--exclude-case-id` to omit unchanged backends from focused retests.
- Per-operation startup data is reported under `checkStartups`.
- A backend that cannot become healthy for an isolated operation is reported as `STARTUP_PLAYBACK_FAILED` / `STARTUP_PLAYER_CRASHED`; only MCP/ADB/control failures are `INFRA_ERROR`.
- Media3 Pull now uses a 4x TS timestamp search window instead of 8x. This is an A/B experiment for the repeatable ~33-34 second large Comskip recovery; other Media3 Pull tuning remains unchanged.
- Android source changed, so rebuild/install the Dev APK before evaluating the experiment. `debugStatusVersion` remains 10.

## v0.5.43 explicit Search startup

- Media3 playback/comskip automation no longer wraps Search/type/start inside one `dev_send_sequence` call.
- New `dev_open_search` sends SageTV Search directly and waits for both `hasTextInput=true` and verified `imeVisible=true`.
- Search text is then entered with `dev_input_text_keyboard` through Android OS `input text`, the IME is hidden directly, and `dev_wait_for_ime(false)` verifies it is gone.
- `_compact_state()` now preserves `imeRequested`, `imeVisibleKnown`, and `imeVisible`.
- The standard startup reports the exact failing phase instead of a generic `dev_send_sequence isError=true`.
- `mcp-media3-matrix` and `mcp-media3-comskip-matrix` both use the explicit Search path.
- Host/MCP-only update; the v0.5.42 debug APK can remain installed.

## v0.5.42 automation text input

Automated Search text uses the Android OS input service only. The standard sequence waits for both the SageTV text-input state and a positively visible Android IME before executing `keyboardtext`. The previous debug-receiver raw-character and in-app `dispatchKeyEvent()` text paths have been removed. `directtext` remains an alias to `keyboardtext`; `sendtext` and `sendkey` remain available for diagnostics.

## v0.5.41 Android OS keyboard text injection

- `keyboardtext` / `dev_input_text_keyboard(text)` now use Android's OS `input text` service after MCP verifies `imeVisible=true`.
- Do **not** dispatch synthetic `KeyEvent`s from the debug BroadcastReceiver. The SageTV search field is not a native Android `EditText`; synchronous `View.dispatchKeyEvent()` can stall the receiver on Fire TV.
- The path is Android input service -> focused MiniClient view -> normal `MiniClientKeyListener` / key mapping.
- `sendtext` remains available as the raw diagnostic primitive; `keyboardtext` is the synchronized automation wrapper. `directtext` remains a compatibility alias.
- v0.5.41 is host/MCP-only; the installed v0.5.40 APK does not need rebuilding solely for this change.

## v0.5.40 Android keyboard text path

- Normal automation must use `dev_input_text_keyboard(text)` / `keyboardtext <text>` after `hasTextInput=true` and verified `imeVisible=true`.
- Do not send raw character values directly with `MiniClientConnection.postKeyEvent(...)`; lowercase ASCII is not a valid Java/Sage keycode mapping.
- The debug APK generates Android virtual-keyboard `KeyEvent`s and dispatches them to the focused MiniClient view, so the existing Android key listener performs the same conversion as real Fire TV keyboard input.
- `dev_input_text_direct` / `directtext` are compatibility aliases to the Android-keyboard path. `sendkey` / `sendtext` remain diagnostic input-simulation tools.
- v0.5.40 changes Android shared/debug code; rebuild/install before device testing.

## v0.5.39 direct debug automation controls

MCP automation now prefers debug-only Android/MiniClient APIs instead of simulated Android keys wherever the client can perform the operation directly. New direct MCP controls include `dev_input_text_keyboard` (`dev_input_text_direct` compatibility alias), `dev_hide_ime`, `dev_player_control`, `dev_seek_relative`, `dev_skip_forward`, `dev_skip_backward`, and `dev_comskip`, plus instrumented `dev_run_relative_seek_check`. Absolute `dev_seek_time` remains direct.

The standard Search/startup path now uses direct SageTV commands, Android virtual-keyboard text dispatched to the focused MiniClient view, and direct IME hide. `sendkey` and `sendtext` remain available for diagnostics but are not required by the normal playback-start path. Media3 FF/REW matrix tests now use caller-defined relative millisecond seeks and direct player pause/play.

Comskip is the one server-owned exception: the Android player does not receive commercial-marker timestamps. The dedicated debug Comskip operation therefore posts SageTV RIGHT/LEFT internally through `EventRouter`; it does not inject an Android key or resolve the configurable video arrow mapping. SageTV/STV chooses the marker and the normal media seek then reaches the player.

This changes Android debug/shared source and requires rebuilding/reinstalling the debug APK.

## v0.5.37 automation startup

Fresh automated sessions now bypass the generic Android launcher/server-picker. After verifying the Dev app is stopped and waking Fire TV, MCP applies debug configuration and uses the explicit SageTV direct-connect command to launch the real MiniClient UI. `dev_app_status` also tolerates Fire OS `pidof` false-negatives by checking `ps -A` and the resumed Dev Activity.

`launch_dev_app` is still available for manual testing, but playback/session/Media3 automation must not use it.

## Debug Search/IME synchronization (v0.5.36)

The debug APK exposes actual Android soft-keyboard visibility separately from SageTV text-input state. MCP automation waits for both an active Search text field and a verified-visible IME before typing, then verifies the IME is hidden after BACK. Snapshot fields are `imeRequested`, `imeVisibleKnown`, and `imeVisible`.

## v0.5.35 Android debug receiver compile repair

- Fixes the v0.5.34 `DevTestReceiver.java` Javac failure caused by missing `configuredValues(PrefStore)` and `appendPlaybackHealth(...)` helper definitions.
- Restores the configuration helper and directly appends the existing `PlaybackHealthProbe` compact snapshot.
- Deterministic Android/MCP startup/readiness behavior from v0.5.34 is unchanged.
- Rebuild/install the v0.5.35 debug APK before rerunning Pull Comskip diagnostics.

## v0.5.34 deterministic test startup

Fresh MCP playback automation no longer assumes that a connected/stable SageTV menu is safe for input. The debug APK now reports `uiState` and `automationReady`; automation begins only when `automationReady=true` (connected Main Menu, no popup/text input, no active player). Before every fresh matrix/playback session the MCP checks whether the Dev process is running, tries a normal exit first, force-stops only if it remains alive, verifies it is stopped, wakes the Fire TV, relaunches, and verifies the process is running. If SageTV restores the fixed client ID into a stale Search screen, the harness sends direct SageTV `home` and waits for readiness before sending Search.

New MCP tools: `firetv_wake`, `dev_app_status`, `kill_dev_app`, `dev_prepare_clean_start`. This is an Android debug-build change, so rebuild/install the APK before the next device test.

## v0.5.33 Pull seek diagnostics

Media3 Pull uses the proven 256 KiB read buffer again. The Comskip matrix now prints per-command Pull I/O deltas (`opens`, `openWait`, SageTV `READs`, bytes requested/received, `readWait`, max READ size, and errors) so large-seek latency can be diagnosed before further tuning.

## v0.5.32 Media3 Pull Comskip-latency experiment

Direct SageTV Comskip is now proven correct. The remaining device symptom is Pull-only recovery latency after large marker jumps (~3.63 s versus 0.57-0.82 s in Dynamic). v0.5.32 changes only the Media3 Pull SageTV network read buffer from 256 KiB to 1 MiB so the existing MPEG-TS seek scan can be serviced with fewer synchronous READ round trips. Rebuild/install the debug APK, then retest Pull with:

```bash
./dev.sh mcp-media3-comskip-matrix \
  --server 192.168.10.175 \
  --text "<recording search text>" \
  --modes pull
```

Keep the v0.5.31 direct `right` / `left` Comskip path unchanged.

## v0.5.31 direct SageTV Comskip commands

The Comskip automation now sends the native SageTV `right` / `left` commands directly, matching the proven manual `mcp-send-sequence` sequence (`command right` / `command left`). It no longer resolves the normal `videoplaying_right` / `videoplaying_left` mapping, because those normal short-press mappings are FF/REW; Comskip is reached by the long-press/native RIGHT/LEFT Sage command. `mcp-comskip-test` and `mcp-media3-comskip-matrix` retain the existing real decoded A/V recovery and landing-timeline instrumentation around the direct command. Host/MCP-only: no APK rebuild is required.

## v0.5.30 explicit test-recording selection

All native recording-start test commands now require `--text`. There is **no default recording title** and no hidden fallback. Omitting `--text` is an argument error before MCP/ADB/session activity begins. This keeps the automation reusable when the test recording changes.

Example:

```bash
./dev.sh mcp-media3-comskip-matrix \
  --server 192.168.10.175 \
  --text "<recording search text>"
```

The same requirement applies to `mcp-playback-test`, `mcp-media3-matrix`, and `mcp-search-test`.

## v0.5.29 Media3 Comskip matrix

The v0.5.28 Fire TV matrix passed Media3 seek recovery in both Push/Dynamic and Pull. Use `./dev.sh mcp-media3-comskip-matrix --server 192.168.10.175 --text "<recording search text>"` for the next fresh-session comparison of direct SageTV `right`/`left` Comskip behavior. PASS/FAIL is real decoded A/V recovery; landing/jump timelines are diagnostic because SageTV does not expose semantic Comskip marker timestamps to the MiniClient. This is host/MCP-only and does not require an APK rebuild.

## v0.5.27 stable UI readiness gate

`mcp-playback-test` no longer treats `connected=true` as proof that the SageTV UI has finished loading. Before the native Search sequence begins, MCP now waits for a real non-empty SageTV menu hint and requires that UI state to remain stable for 2000 ms. Use `--ui-stable-ms` to override the interval. The validated Search/text/key sequence is unchanged.

## v0.5.26 native end-to-end playback start test

Use one command to launch the Dev MiniClient, set playback preferences, connect directly to the desired SageTV server, start the standard test recording through native MiniClient input, and verify playback:

```bash
./dev.sh mcp-playback-test \
  --server 192.168.10.175 \
  --player media3 \
  --streaming push \
  --decoder hardware \
  --text "<recording search text>"
```

The server is addressed explicitly; the saved-server list position is never assumed. The direct connection uses `save=false`, so the existing `192.168.10.175` / `192.168.10.232` entries are not modified or duplicated.

The native start sequence uses the explicitly supplied `--text` value:

```text
command search
delay 50
sendtext <recording search text>
delay 50
sendkey BACK
delay 100
sendkey FAST_FORWARD
sendkey RIGHT
sendkey PLAY_PAUSE
sendkey PLAY_PAUSE
```

This workflow intentionally does not use Sagex/HTTP or `dev_play_video`; the SageTV server starts playback as a result of the same client UI/input path used manually.

# SageTV MiniClient Dev Fire TV Development Project

## v0.5.28 Media3 Push/Pull seek-resume matrix

The next device phase is now automated without changing Media3 itself. Run:

```bash
./dev.sh mcp-media3-matrix --server 192.168.10.175 --text "<recording search text>"
```

The command starts the explicitly selected recording natively in a fresh **Media3 + Push/Dynamic** session and then a fresh **Media3 + Pull** session. Each mode must pass real A/V recovery after a single FF, single REW, rapid mixed seeks, and pause -> play. A JSON recovery comparison is saved under `artifacts/firetv`; recovery slower than 2000 ms is captured as a diagnostic warning rather than an automatic failure. Use `--modes pull` or `--modes dynamic` for a focused retest. No APK rebuild is required solely for v0.5.28.


## v0.5.24 explicit MCP input sequence

Use one MCP call for a fully explicit multiline input script:

```bash
./dev.sh mcp-send-sequence <<'EOF'
command search
delay 1000
sendtext meet the press
delay 500
sendkey BACK
delay 500
sendkey DOWN
sendkey RIGHT
sendkey RIGHT
sendkey RIGHT
sendkey PLAY_PAUSE
sendkey PLAY_PAUSE
EOF
```

Supported actions are `command`, `sendkey`, `sendtext`, and `delay` (milliseconds). No other actions are sent implicitly.


> v0.5.18 adds end-to-end MCP session control and client-centric play-by-name. MCP can apply player settings, connect the MiniClient, resolve a SageTV MediaFile by name, start it on this same MiniClient UI context, verify real A/V output, run seek/comskip health checks, and exit. v0.5.17 hardens the output-health MCP checks for Pull playback: encoded AudioTrack playback-head resets after REW are recognized correctly, and long skip/comskip checks no longer run as foreground broadcasts. v0.5.16 adds Comskip arrow testing on top of the output-health-first MCP checks. PASS now requires real decoded video output and real Android audio output to recover and keep advancing after FF/REW; SageTV timeline movement is retained only as diagnostic context. The visible streaming option remains **Push** (persisted value remains `dynamic`).

Version **0.5.30** — Docker-only SageTV Android MiniClient development, Fire TV hardware testing, four-backend player modernization, seek/timeline stabilization, and MCP regression automation.

## Current state

Fire TV v0.5.8 testing confirms the Pull seek-map work is functionally correct:

- **ExoPlayer Legacy / Pull:** startup, rapid sequence, seek-to-start, pause/start and large comskip all pass. The remaining issue is that visible video can take several seconds to resume after a seek even though the timeline stays correct.
- **Media3 / Pull:** same result as Legacy ExoPlayer.
- **GSY Auto / Pull:** same result because Auto delegates to Media3.
- **IJK / Pull:** remains the clean functional control path. Decoder quality/performance concerns remain separate and are not changed here.
- **Push** (persisted value remains `dynamic`): continues to be the faster PUSH control path.

v0.5.9 keeps the successful 8x MPEG-TS timestamp search from v0.5.8, increases only the Exo-based SageTV Pull network buffer from 32 KiB to 256 KiB, and applies a Pull-only low-latency LoadControl profile (500 ms after seek / 1000 ms after rebuffer). The historical 32 KiB Pull buffer remains the default for IJK and System paths.

Selectable backends remain ExoPlayer Legacy 2.18.1, Media3 1.11.0, original isolated IJK 0.8.8, and GSYVideoPlayer 13.1.0. Future HTTP MPEG-TS, HLS, RTMP, and SRT capability remains preserved.

See `PLAYER_ARCHITECTURE.md` and `PHASE_A_FOLLOWUP_TEST_v0.5.9.md`.

## Safety invariants

- Production/working SageTV MiniClient packages are never installed, removed, or modified by Dev package tools.
- Dev debug package is `org.opensagetv.miniclient.dev.debug`.
- Dev install always uninstalls the Dev package first, then performs a fresh install.
- SageTV client identity is fixed as `DEV001` (`44:45:56:30:30:31`) so the server can reuse the same client profile after a clean reinstall.
- Fresh installs default to **Push** streaming mode (persisted value remains `dynamic`).
- Firebase/Crashlytics remains removed from active Dev code/build paths.
- Internal player telemetry hooks remain prohibited because Fire TV testing showed they can stop playback startup.

## Source trees

- `source/existing` — untouched exact supplied repository baseline.
- `source/dev` — isolated Dev source.
- `source/SOURCE_IMPORT.json` — source provenance metadata.

Original supplied source archive SHA-256:

`f45c930f3810b56d88f751120ca6fa692ae9b01dd2adce62a3d926b2ab336a79`

## Docker toolchains

v0.4.0 requires rebuilding the Docker image.

The image contains **both** toolchains so modernization does not break the untouched baseline:

### Dev build

- JDK 17
- AGP 8.13.2
- Gradle 8.13
- Android platform/compile SDK 36
- Build Tools 36.0.0
- minSdk 23
- targetSdk 30
- NDK 21.0.6113669

### Untouched baseline build

- bundled JDK 8
- original Gradle 6.1.1 / AGP 4.0.2
- Android platform 29
- Build Tools 29.0.2
- NDK 21.0.6113669

The Fire TV AFTMM is API 25, which remains above Media3 1.11's minSdk 23 requirement.

## Upgrade/build procedure

After extracting the v0.4.0 update over the existing project root:

```bash
./dev.sh image
./dev.sh test
./dev.sh validate
./dev.sh build
./dev.sh install
./dev.sh launch
```

`./dev.sh image` is required because the JDK/Android SDK Docker image changed.

First verify video playback **without changing the player preference**. The default remains the known-good legacy ExoPlayer backend.

Then test the same recording with **Media3 ExoPlayer**, **IJKPlayer**, and **GSYVideoPlayer**. On Amazon AFTMM/`mantis`, IJK/GSY MPEG-2 intentionally falls back to FFmpeg software decode even when Decoding Method is Hardware; other codecs still exercise hardware decoding normally.

## Build outputs

Dev APK:

`artifacts/firetv/SageTV-MiniClient-Dev-debug.apk`

Untouched baseline build, never automatically installed:

```bash
./compile_existing_app.sh
```

Output:

`artifacts/existing/SageTV-MiniClient-Existing-debug.apk`

## Fire TV / MCP

### v0.5.18 end-to-end client control / play by name

The preferred start-playback MCP tool is now `dev_play_video(video_name)`. The caller supplies only the visible recording/video title. MCP reads the connected SageTV server and fixed Dev MiniClient client ID from the Android debug snapshot, discovers the Sagex Remote API, resolves this MiniClient's UI context, looks up the MediaFile, invokes `Watch` for that UI context, and then verifies real video/audio output. Exact case-insensitive title matches are preferred; ambiguous names return candidates instead of guessing.

Typical MCP flow:

```text
launch_dev_app
dev_set_player_config(player=..., streaming=..., decoding=..., gsy_engine=...)
dev_connect_server()
dev_play_video(video_name="Known Test Recording")
dev_run_seek_check(...) / dev_run_comskip_check(...)
dev_exit_session()
```

The shell wrapper does the same end-to-end flow:

```bash
./dev.sh mcp-session-test --video-name "Known Test Recording" --player media3 --streaming pull --decoding hardware --exit session
```

Sagex discovery tries the connected server on ports 8080, 8081 and 80. For custom web ports/paths set `SAGETV_SAGEX_BASE` (for example `http://server:8090/sagex/api`). Optional Basic Auth can be supplied through `SAGETV_SAGEX_USER` and `SAGETV_SAGEX_PASSWORD`.


Typical commands:

```bash
./dev.sh connect
./dev.sh device-info
./dev.sh install
./dev.sh launch
./dev.sh mcp-test
```

v0.5.11 introduced a debug-only on-demand control receiver. v0.5.14 moved timeline sampling into Android. **v0.5.15 makes real playback output authoritative:** for Exo2/Media3-based paths the debug receiver inspects existing renderer counters, actual decoder identity, Android `AudioTrack` playback-head movement, Surface validity, player state, buffering/loading state, datasource, and player errors only while MCP asks for a check. No permanent player or analytics listeners are installed.

**v0.5.16 adds dedicated Comskip testing:** `./dev.sh mcp-comskip-test` resolves the actual Android video-playing left/right mapping, sends that same SageTV command, and reports the SageTV timeline at first video recovery, first audio recovery, and full A/V recovery. The SageTV STV/plugin owns commercial marker metadata; the MiniClient does not receive semantic marker start/end times, so the test records the actual recovery landing and can optionally compare it to a manually known marker target.

**v0.5.17 hardens slow Pull tests:** same-session AudioTrack playback-head resets (seen with AC-3/passthrough on backward seeks) count as valid audio recovery when the new head is actively playing. Long `skip_check` / `comskip_check` operations use a normal explicit broadcast instead of `--receiver-foreground`, and the seek suite preserves a basic FF/REW failure instead of piling a rapid stress sequence on top of it.

Start one known recording, then run:

```bash
./dev.sh mcp-search-test
./dev.sh mcp-seek-test
```

By default `./dev.sh mcp-seek-test` now skips numeric timeline calibration and runs **single FF, single REW, and rapid mixed FF/REW output-health checks**. A check passes only when video frames and audio output recover and continue advancing through the verification window. Timeline deltas are still printed for debugging. Use `--calibrate-timeline` (plus optional `--ff-ms` / `--rew-ms`) only when numeric SageTV timing itself is under investigation. Large/comskip can be added with `--large-command` once its SageCommand key is identified. Continuous `mcp-telemetry` remains disabled.

## Media update policy

Standalone `com.google.android.exoplayer2` is deprecated. The final release is 2.19.1, while current ExoPlayer development is in AndroidX Media3. This project therefore adds current Media3 as a separate backend rather than immediately modifying the only confirmed-working legacy Exo/FFmpeg combination.

IJKPlayer has no newer official upstream release beyond 0.8.8, so v0.4.0 treats it as a legacy fallback and removes unnecessary coupling instead of claiming a nonexistent version upgrade.

## Next hardware gate

For one representative recording, test in this order:

1. ExoPlayer Legacy — confirmed working; must remain unchanged.
2. Media3 ExoPlayer — confirmed working; must remain unchanged.
3. IJKPlayer — validate the upgraded GSY IJK MediaCodec hardware path.
4. GSYVideoPlayer — validate the dedicated GSY advanced profile with the same stream.
4. If Media3 starts correctly, run the same rapid seek sequence on Exo and Media3: `+30, +30, -10, +60, -30`.

Do not make Media3 the default until Fire TV parity is demonstrated.

### v0.4.4 Fire TV video output repair

The multi-player build now uses explicit SurfaceView lifecycle/ordering rules for older Fire OS: Legacy Exo and Media3 attach video output before prepare, IJK restores the video view before display binding, and the GDX OSD uses translucent media-overlay compositing. This change does not re-enable internal player telemetry.

### v0.4.5 Fire TV routing/diagnostic note

The active Fire TV renderer is `MiniClientGDXActivity`. Both GDX and OpenGL renderers now resolve `PlayerBackend` and create players only through `PlayerFactory`, so `media3` is a real Media3 selection instead of falling through to IJK. The unsuccessful v0.4.4 surface experiment was rolled back. For crashes or audio-only playback, use external diagnostics: `./dev.sh player-diag clear`, reproduce once, then `./dev.sh player-diag <label>`.

### Legacy ExoPlayer dependency alignment

The Legacy Exo backend is pinned to one coherent ExoPlayer 2.18.1 module graph under Gradle 8. Production builds do not package `exoplayer-testutils` or IJK's obsolete Exo wrapper. This prevents binary API mismatches between extractor/core/source classes while retaining the v0.3.8 legacy player implementation for A/B testing.

### v0.4.7 Gradle layout self-repair

`./dev.sh test`, `./dev.sh validate`, and `./dev.sh build` now verify the Dev root Gradle file before running. If `source/dev/build.gradle` has been replaced by the `android-shared` module Gradle file, it is restored from `config/dev-root-build.gradle.canonical` and the damaged file is preserved as `source/dev/build.gradle.bad`.

## v0.4.8 IJK MPEG-2 compatibility

Fire TV testing confirmed Legacy ExoPlayer and Media3 playback. IJK remained audio-only with a black video surface and then stopped on the same MPEG-2/H.262 recording. v0.4.8 leaves the working Google backends untouched and defaults IJK MPEG-2 to the bundled FFmpeg software decoder. H.264 and HEVC continue to use IJK MediaCodec hardware decode. The IJK settings page exposes an opt-in **MPEG-2 Hardware Decoder** switch for device-specific testing. The IJK error callback also marks the existing error path handled to avoid a duplicate completion/stop cascade.


## v0.4.9 IJK MPEG-2 hardware compatibility

IJK MPEG-2 MediaCodec is enabled by default again. The Dev build gives MPEG-TS additional probe time before hardware codec creation, enables synchronized MediaCodec, and explicitly prefers the MTK MPEG-2 decoder on the Amazon `mantis` Fire TV Stick 4K. Disable **IJK: MPEG-2 Hardware Decoder** to return to the known-working FFmpeg software fallback.

## v0.5.3 GSY Exo build fix

GSYVideoPlayer Exo support no longer resolves the unused legacy RTMP client (`LibRtmp-Client-for-Android`) or Media3 RTMP data-source module. SageTV playback does not use RTMP, and excluding these dependencies avoids the obsolete Sonatype certificate path that caused `checkDebugAarMetadata` / `compileDebugJavaWithJavac` dependency resolution to fail. No player-engine behavior from v0.5.2 was otherwise changed.

## v0.5.2 player isolation update

Legacy **IJKPlayer** and **GSYVideoPlayer** are separate. IJK uses the original local 0.8.8 Java/JNI AARs and never uses GSY. GSY has its own `gsy_player_engine` setting with Auto / Media3-Exo / Android System / Legacy Exo. Auto chooses System for SageTV custom PUSH/PULL data sources and Media3 for ordinary HTTP. GSY's modern IJK/ex_so runtime is intentionally not packaged because it collides with the legacy `tv.danmaku.ijk` namespace and `libijk*.so` names.

### Native Search MCP test (v0.5.19)

`./dev.sh mcp-search-test` initializes the real MCP server, connects ADB to the configured Fire TV in that same container, and calls the first-class `dev_search` tool. `dev_search` sends SageTV's native `search` command over the existing MiniClient event path. No Android/APK change is required from v0.5.18.

### Native Search text sequence (v0.5.21)

`./dev.sh mcp-search-test --text "<recording search text>"` requires the search text explicitly, then performs: Search -> type the supplied text -> dismiss the Android keyboard with BACK -> Down -> Right -> Right -> Right -> Play/Pause -> Play/Pause. BACK is consumed by the visible IME to close the keyboard; Android keyboard Enter/Next is not sent unless `--next` is explicitly requested.
## v0.5.38 debug time seek

The debug APK exposes a generic absolute time seek through MCP. Use `dev_seek_time(target_ms=...)` or `./dev.sh mcp-seek-time --target-ms N`; the target is always supplied by the caller (`0` = beginning). The Media3 Comskip matrix passes `--start-ms` into this same command before Comskip checks so test playback can start from a deterministic time without hard-coding the seek target inside Android. The older `dev_local_seek_absolute` remains for compatibility/backend isolation.



## v0.5.44 - Restore proven Search text timing
- Standard recording startup now restores the proven Android OS `adb input text` path (`dev_type_text` / legacy `sendtext`) immediately after SageTV reports `hasTextInput=true`.
- IME visibility remains instrumented, but is verified after text injection instead of gating text entry.
- Startup no longer uses `dev_sage_command_sequence` before playback; post-Search UI commands are issued individually with `dev_sage_command`, because the sequence measurement helper requires an active player timeline.
- No Android APK source changes in this update.

### Verified debug seek
`dev_seek_time` uses the direct Android player seek API, but PASS requires a stable post-seek landing after the backend returns to READY. Temporary zero media times while Media3 is seeking are explicitly ignored. The Comskip matrix exposes `--seek-stable-ms` (default 1200 ms).

### Complete MCP player matrix

Run the complete player/backend configuration harness (63 configuration cases):

```bash
./dev.sh mcp-player-matrix --server 192.168.10.175 --text "<recording>" --watchdog-ms 180000
```

It covers Legacy Exo, Media3, IJK, and all GSYVideoPlayer engine selections (`auto`, `media3`, `system`, `legacy_exo`) across `dynamic`, `pull`, `fixed` and all three decoder policies. Watchdog expiry is recorded as an observation and does not abort the matrix. See `PLAYER_TEST_HARNESS_v0.5.46.md`.

### v0.5.47 per-step watchdog semantics


### Persistent ADB runtime session

`adb_connect` now establishes one persistent `adb shell` that is reused for runtime MCP operations across the full matrix. Use `adb_session_status` to verify the same shell PID is being reused and to inspect command/restart counters. This removes repeated ADB-shell process startup overhead. APK install/uninstall/pull and the initial `adb connect` remain separate ADB subcommands.

`--watchdog-ms` is a watchdog for **each individual media action**, not for the full matrix. With `--watchdog-ms 180000`, Comskip RIGHT may observe for up to 3 minutes, then Comskip LEFT starts with a new 3-minute watchdog, and the same applies to direct seeks and pause/resume. The installed debug APK advertises `maxRecoveryWatchdogMs`; MCP rejects a request that the APK cannot actually honor. Long ADB debug broadcasts also extend their transport timeout beyond the requested step watchdog.

The recording Search/start sequence now sends `ff`, `right`, `play_pause`, `down`, `select` after text entry. The first `play_pause` opens the resume/start-choice flow, `down` highlights Start From Beginning, and `select` confirms the highlighted choice.

Because v0.5.47 changes `DevTestReceiver.java`, rebuild/install the debug APK before validating a 3-minute watchdog.

## v0.5.50 - paced Android text entry
- Recording Search text is now injected one character at a time through Android's OS input service instead of one whole fast phrase.
- Spaces use `KEYCODE_SPACE`; normal characters use `adb shell input text <char>`.
- New CLI option `--text-char-delay-ms` controls pacing for playback, Media3, Comskip, and full player-matrix harnesses. Default is 0, which uses the proven one-shot Android `input text` path; values 1-2000 ms enable the experimental per-character diagnostic mode.
- The full player matrix prints the active text-character delay so repeated cases are reproducible.

### Player-matrix long-step progress
`mcp-player-matrix` uses the configured watchdog independently for each media check. As of v0.5.62 every post-startup media action is observed by a host-side A/V-counter loop. The watchdog status rewrites one terminal line dynamically (instead of appending WAIT lines) and reports exactly what is still missing, for example `waitingFor=audio_counter`, together with live video/audio advancement, playback state, surface state, and error diagnostics.

### Reliable MCP Search text (v0.5.54)
Normal automated Search text is sent through the debug APK's native MiniClient keyboard-event channel, not through Fire TV `adb input text`. The implementation mirrors `KeyMapProcessor`: letters use uppercase Java/AWT keyCode while `keyChar` contains the actual character. This avoids intermittent IME/focus failures during repeated player-matrix restarts. A positive `--text-char-delay-ms` still selects the legacy Android/ADB diagnostic path; leave it at the default `0` for normal testing.

### Fire TV wake and launch (v0.5.55)
`firetv_wake` and deterministic clean-start now send `KEYCODE_WAKEUP` followed by `KEYCODE_HOME`, which exits Fire TV Dreaming/screensaver state before tests continue. `./dev.sh launch` resolves the Dev package launcher activity and uses `am start -W`; `monkey` is fallback-only because some Fire OS builds return code 251 even for a valid installed package.

### v0.5.56 MCP native text / IME behavior
MCP native SageTV text entry temporarily suppresses the Android/Fire TV soft keyboard before opening Search. The SageTV `hasTextInput` hint still works, but `showHideKeyboard(true)` is ignored while the debug suppression flag is active. If an IME was already visible it is hidden directly; otherwise no hide/BACK action is sent. Suppression is restored after the automated Search/start sequence, so manual app text entry remains unchanged. Debug snapshots report `imeSuppressedForDebug` (status version 5).

### v0.5.57 seek-coordinate correction
Direct debug seeks now use one coordinate system end-to-end: backend-local player milliseconds. `dev_seek_time(0)` is verified against `health_playerPositionMs` (or a local-position fallback), not SageTV's anchored timeline. Relative debug seeks also start from local player position before applying deltas. `sageTimelineMs` and `serverAnchorMs` remain diagnostic context only. This fixes false long watchdog waits where playback was healthy but the anchored timeline could never be near the local target.

Debug APK capability marker: `debugStatusVersion=6`. MCP direct seek tools reject older debug APKs so the full matrix cannot silently use the old anchored-coordinate relative seek implementation.
### v0.5.58 seek-verifier wrapper-state rule
Direct absolute-seek verification must not require `BaseMediaPlayerImpl.playerReady`, `seekPending`, or `flushed` to clear. Legacy Exo can leave `seekPending=true` indefinitely after a seek that is effectively a no-op (for example, seeking to 0 when already near 0) because no `DISCONTINUITY_REASON_SEEK` callback is guaranteed. Use backend-local position + backend READY/error state for the verdict. Keep wrapper flags diagnostic-only and report stale-sample counts. Host/MCP-only; debug status version 6 remains sufficient.
### v0.5.59 A/V-counter recovery rule

For player-matrix recovery tests, landing position is not a pass/fail criterion. After a seek or other media action, recovery is proven by the existing playback-health counters: rendered video advances and audio playback-head/render counters advance again. Wrapper flags and timeline/position remain diagnostic only. The CLI prints each step before execution and prints the A/V counter verdict after completion.


## v0.5.60 hardware-only selection and Comskip recovery

The complete player matrix still defaults to all 63 configuration cases. To test only hardware decoding, use either:

```bash
./dev.sh mcp-player-matrix --server 192.168.10.175 --text "<recording>" --hardware-only --watchdog-ms 180000
```

or the existing explicit decoder selector:

```bash
./dev.sh mcp-player-matrix --server 192.168.10.175 --text "<recording>" --decoding hardware --watchdog-ms 180000
```

Hardware-only runs 21 cases: 9 non-GSY configurations plus 12 GSY engine configurations. Use `--decoding software` or `--decoding fallback` for the other decoding selections; legacy aliases remain accepted for compatibility.

Player-matrix Comskip now posts a short native SageTV `right`/`left` command and performs recovery observation on the host using the same real decoded video/audio counters used by the other matrix checks. The old matrix path held one Android debug broadcast open for the full watchdog and could fail around the Android broadcast lifetime even while playback had already recovered. Landing position is diagnostic-only.


## v0.5.61 playback selection names and Fixed encoding parameters

User-facing test selections are now consistent across the harness:

- `--decoding hardware|software|fallback` (legacy persisted `hardware_preferred` still backs `fallback`)
- `--streaming push|pull|fixed` (legacy persisted `dynamic` still backs `push`)

When `fixed` is selected, the harness passes the complete Fixed encoding/remux configuration to the debug APK and verifies it before playback. Matrix defaults deliberately force an encoding test (`--fixed-encoding-preference always`, `--fixed-remuxing-preference off`) with Matroska, 4000 kbps video, source FPS/resolution, AC3 128 kbps source channels, 10-second key-frame interval, and B-frames enabled. All Fixed values can be overridden from the CLI.

Example Fixed hardware encoding test:

```bash
./dev.sh mcp-player-matrix \
  --server 192.168.10.175 \
  --text "<recording>" \
  --streaming fixed \
  --decoding hardware \
  --fixed-encoding-preference always \
  --fixed-encoding-format matroska \
  --fixed-video-bitrate-kbps 6000 \
  --fixed-video-fps source \
  --fixed-video-resolution 720 \
  --fixed-audio-codec ac3 \
  --fixed-audio-bitrate-kbps 192 \
  --fixed-audio-channels 6 \
  --fixed-remuxing-preference off \
  --watchdog-ms 180000
```

## v0.5.62 matrix recovery and live watchdog output

The full player matrix uses one recovery rule for `absolute_seek`, `seek_forward`, `seek_backward`, `pause_resume`, `comskip_right`, and `comskip_left`: expected decoded video/audio output counters must advance again. Where the seek or Comskip lands is diagnostic only. Wrapper state (`ready`, `isPlaying`, `seekPending`, `flushed`), surface validity, buffering/loading, and reported player errors are printed but do not block PASS while real A/V counters are moving.

While waiting, the CLI continuously rewrites one line rather than adding log spam, for example:

```text
WAIT exoplayer__push__hardware/seek_forward  3.2s/180s | waitingFor=audio_counter | video=advancing | audio=stalled | state=READY | surface=ok | error=none
```

After the step finishes, the CLI prints the final counter before/after/deltas, decoder information, diagnostic wrapper state, and Pull data-source deltas when those metrics are available.

## v0.5.63 exact-event MCP diagnostics

The matrix still answers one primary question: **did real video/audio output recover after the action?** A/V output counters remain the PASS/FAIL verdict. Seek landing is telemetry only; the default sanity tolerance is +/-10 seconds and produces `NORMAL`, `WARN`, or `SUSPICIOUS` without failing a recovered action.

The debug APK now exposes four separate timeline concepts so Codex does not have to infer them from one position value:

- `serverRequestedSeekMs` — latest explicit SageTV `MEDIACMD_SEEK` target.
- `serverAnchorMs` — Push stream anchor established from SageTV buffer metadata after flush/rebase.
- `health_playerPositionMs` — backend-local Android player position.
- `sageTimelineMs` — timeline the MiniClient reports back through the `MEDIACMD_GETMEDIATIME` calculation.

`debugStatusVersion=9` also exposes seek/flush/anchor sequence and timestamps. Debug builds maintain a 32-event exact-event ring. `dev_player_events` returns it and `dev_clear_player_events` resets it before a focused action. Event records capture renderer/audio counters when seek, flush, READY/playing, first-frame, pause/play, completion, and error events occur. SageTV protocol events are timestamped immediately and capture their player counters asynchronously on the UI thread; `snapshotLagMs` shows that capture delay.

`mcp-player-matrix` clears this ring before every check and saves the event evidence under `eventTraps`. Host polling remains the recovery verdict, while the event traps explain *when* the player entered buffering/READY/playing and *when* real output counters restarted.



## v0.5.69 focused validation

The 20260828_015224 report showed backend-specific behavior rather than one common Pull fix. Media3's 10-second reprepare made its long Pull Comskip worse, while legacy Exo2's directional seek changed its same forward Comskip from a watchdog into an ~8.5-second recovery. v0.5.69 therefore keeps the Exo2-specific win and backs out the Media3-specific regression.

The full player matrix slow label is configurable from the CLI. All of these forms are accepted:

```bash
--slow-recovery-ms 5000
--slow_recover_ms 5000
--slow-recover-ms 5000
```

For the first v0.5.69 validation, retest only cases whose relevant code changed. Native/GYS Media3 Push behavior was not changed, so exclude those two cases from the previous report-derived set:

```bash
./dev.sh mcp-player-matrix \
  --server 192.168.10.175 \
  --text "meet the press" \
  --hardware-only \
  --streaming push,pull \
  --issues-only /workspace/artifacts/firetv/20260828_015224_player_full_matrix.json \
  --exclude-case-id gsyplayer__push__hardware__gsy_media3,media3__push__hardware \
  --slow_recover_ms 5000 \
  --watchdog-ms 50000
```

Expected selected set: 7 cases — Exo2 Pull, Media3 Pull, GSY Auto Pull, GSY Media3 Pull, GSY Legacy Exo Pull, and the two GSY/System startup cases (now changed because System falls back to Media3). Rebuild/install the Dev APK and verify `debugStatusVersion=12` first.

## v0.5.65 focused issue reruns

To rerun only the currently known abnormal hardware Push/Pull cases from the 2026-08-27 matrix:

```bash
./dev.sh mcp-player-matrix \
  --server 192.168.10.175 \
  --text "<recording>" \
  --hardware-only \
  --streaming push,pull \
  --issues-only \
  --watchdog-ms 70000
```

The built-in profile currently selects 9 configurations, 16 media operations, and 2 startup-only GSY/System cases.

Prefer deriving the focused set from the immediately previous report during fix iteration:

```bash
./dev.sh mcp-player-matrix \
  --server 192.168.10.175 \
  --text "<recording>" \
  --hardware-only \
  --streaming push,pull \
  --issues-only /workspace/artifacts/firetv/<previous>_player_full_matrix.json \
  --watchdog-ms 70000
```

If a backend was not changed, omit it from the focused retest. For example, an unchanged GSY engine can be skipped while retaining the others:

```bash
./dev.sh mcp-player-matrix \
  --server 192.168.10.175 \
  --text "<recording>" \
  --hardware-only \
  --streaming push,pull \
  --issues-only /workspace/artifacts/firetv/<previous>_player_full_matrix.json \
  --exclude-gsy-engines system \
  --watchdog-ms 50000
```

Use `--exclude-players exoplayer,ijkplayer` to omit whole player families, or `--exclude-case-id <case-id>[,<case-id>...]` for exact cases. The JSON report records the effective removals under `retestExclusions`.

Every `SLOW_RECOVERY`, `WATCHDOG_EXPIRED`, `PLAYER_CRASHED`, `MEDIA_NOT_RECOVERED`, `INFRA_ERROR`, or non-`RECOVERED` startup case in that report is selected. Per-case operation lists are preserved, so a case that only had a Comskip problem does not waste time rerunning seek and pause/resume. Startup-only failures are started and diagnosed without attempting post-startup actions. Existing `--checks`, `--case-id`, player, streaming, and decoder selectors can further narrow the derived issue set.
