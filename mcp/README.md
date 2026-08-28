## Client ID debug controls (v0.5.75)

`dev_client_id` reports the configured and active SageTV MiniClient IDs. `dev_set_client_id` persists a plain-text (up to six characters) or colonized six-byte ID; plain text `DEV001` becomes `44:45:56:30:30:31`. Use `./dev.sh client-id` for the host CLI wrapper. The app must complete first-time setup before automated testing on a fresh/cleared install. Automated test wrappers default to `44:45:56:30:30:31` (`DEV001`) unless `--client-id` is supplied; the standalone app still uses the original generated/persisted ID behavior.

## v0.5.70 runtime player tuning

New MCP tools: `dev_set_player_tuning` and `dev_player_tuning`. Runtime tuning is in-memory, resets on process restart, and is visible in `dev_player_state`. Host commands `mcp-player-tune` and `mcp-player-tuning-matrix` can adjust/sweep Media3 or legacy Exo2 TS search, Pull read size, seek policy, load-control thresholds, seek recovery, directional threshold, and codec queueing mode without rebuilding between combinations.

## v0.5.65 issue-only player matrix

`mcp-player-matrix --issues-only` focuses the current known abnormal cases. Passing a previous matrix JSON after `--issues-only` derives the exact abnormal startup/case/check set from that report. v0.5.68 adds `--exclude-players`, `--exclude-gsy-engines`, and `--exclude-case-id` so unchanged backends can be omitted from retesting. Android playback remains v0.5.67 / debugStatusVersion 11.

## v0.5.64 long-wait crash probes

`dev_crash_probe` returns a compact Dev-package process state and crash-buffer fingerprint/signature summary. The full player matrix captures it at 5/15/30-second milestones during long recovery waits and startup playback. A new Dev-specific crash signature, process death, or PID change is treated as a proven player crash and ends the wait early. Full checkpoints are still captured after the result.

# SageTV Fire TV MCP

Python MCP server exposing a deliberately small, guarded Fire TV/ADB and debug-build playback-regression surface to any MCP client. The configured package defaults to `org.opensagetv.miniclient.dev.debug`; package install/stop/uninstall tools refuse the upstream `jvl.sage.miniclient...` namespace.

## Install

```bash
cd mcp
python -m venv .venv
. .venv/bin/activate
pip install -e .
export SAGETV_MCP_CONFIG=../config/firetv.toml
sagetv-dev-firetv-mcp
```

The server uses stdio transport. Codex is optional; `./dev.sh mcp-test` exercises the real MCP protocol and `./dev.sh mcp-seek-test` runs playback checks against the currently playing recording. The current MCP Python SDK v2 is required.

## Debug playback automation

The debug APK contains `DevTestReceiver` only in the Android `debug` source set. MCP invokes it explicitly over ADB. The receiver does **not** register player listeners or continuous telemetry hooks. Each request reads existing state once and returns immediately.

Available high-level tools include:

- `dev_player_state` — current backend/configuration plus player state, SageTV media time, server anchor, buffer left, file read position and video dimensions.
- `dev_set_player_config` — set backend / streaming / decoding / GSY engine for the next playback.
- `dev_search` — open native SageTV Search on the currently connected MiniClient.
- `dev_type_text` — inject text through Android keyboard input and optionally press Next/Enter.
- `dev_search_text` — open Search, wait for text input, type a query, and optionally press Android keyboard Next/Enter.
- `dev_send_sequence` — execute one explicit multiline `command` / `sendkey` / `sendtext` / `delay` sequence with no implicit actions.
- `dev_sage_command` and `dev_sage_command_sequence` — send exact SageTV commands without depending on Android key mappings.
- `dev_run_seek_check` — verify real post-skip A/V output health with SageTV timeline context.
- `dev_run_comskip_check` — exercise the configured video left/right arrow mapping and report the timeline where real A/V output recovers.
- `dev_wait_for_media_position` — poll snapshots until a target timeline is reached.
- `dev_test_checkpoint` — save snapshot, logcat, media/codec dump, focused window and screenshot.

The legacy `get_player_telemetry` / `wait_for_player_event` tools remain disabled because the previous continuous callback instrumentation affected playback startup.

`./dev.sh mcp-seek-test` assumes one known recording is already playing. v0.5.13 calibrates the server's primary FF/REW response to a 5-second preference quantum by default, then expands the semantic +30/-10/rapid skip tests. It reports the represented command target when a custom interval cannot exactly express a semantic step. Fully unattended "open recording X" navigation is intentionally deferred until a stable recording/menu path is configured.

## Package-install safety

`install_dev_apk` verifies the APK application ID with Android SDK `aapt`/`aapt2` before installation. If it cannot prove the APK is the configured development package, it refuses the install. Set `SAGETV_AAPT` if `aapt` is not on `PATH`.

## Why there is no "play recording X" tool yet

The current automation starts from an already-playing known recording. Once a stable recording/menu path is configured, the same MCP layer can automate player matrices end-to-end without guessing UI state.

## v0.5.63 exact-event playback evidence

`dev_player_state` (debug status v9) now separates SageTV's requested seek, Push stream anchor, Android-local player position, and MiniClient-reported SageTV timeline. It also includes the latest exact-event trap summary.

New tools:

- `dev_player_events` — return the bounded debug event ring with event-time video/audio counters.
- `dev_clear_player_events` — clear the ring before a focused seek/pause/Comskip action.

The event ring is diagnostic evidence. Matrix PASS/FAIL is still based on recovered A/V output, not exact seek landing. `mcp-player-matrix` clears/collects the ring automatically around every media action.
Codec queueing default is now `sync` in v0.5.74. Use `--codec-mode auto` or the `auto_codec` named profile to compare against the recommended default; explicit MCP tuning temporarily overrides the saved app preference.

