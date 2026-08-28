## v0.5.41 text-entry rule

For automated SageTV Search text, wait for verified IME visibility and use `keyboardtext`. This now uses Android's OS `input text` service rather than in-app `View.dispatchKeyEvent()`. Keep `sendtext` for diagnostics.

## Direct debug automation controls (v0.5.39)

Preferred MCP automation no longer injects Android keys when the MiniClient/player can perform the operation directly:

- `dev_input_text_keyboard(text)` — generate Android virtual-keyboard events and dispatch them to the focused MiniClient view.
- `dev_input_text_direct(text)` — compatibility alias for `dev_input_text_keyboard`.
- `dev_hide_ime()` — hide the Android IME directly; no BACK key.
- `dev_player_control(action)` — direct `play`, `pause`, or `stop`.
- `dev_seek_time(target_ms)` — direct absolute player seek.
- `dev_seek_relative(delta_ms)` — direct signed relative player seek.
- `dev_skip_forward(skip_ms)` / `dev_skip_backward(skip_ms)` — direct relative seek with a required caller-supplied duration.
- `dev_comskip(direction)` — dedicated Comskip trigger. No Android key is injected; the debug APK posts SageTV RIGHT/LEFT internally because only the STV/server knows the commercial marker.
- `dev_run_relative_seek_check(...)` / `dev_run_comskip_check(...)` — same direct controls wrapped with real A/V recovery diagnostics.

`dev_sage_command`, `sendkey`, `sendtext`, and the legacy command-based seek suite remain available for UI/STV semantics and troubleshooting. `mcp-send-sequence` accepts `keyboardtext <text>` (preferred), `directtext <text>` (compatibility alias), and `hideime`.

## Android IME visibility debug (v0.5.36)

Debug snapshots report `imeRequested`, `imeVisibleKnown`, and `imeVisible`. Use `waittextinput`, `waitimevisible`, and `waitimehidden` in `mcp-send-sequence` when automating Search text entry. `imeVisibleKnown=true` plus `imeVisible=false` explicitly verifies that the Android/Fire TV keyboard is not displayed.

## v0.5.34 device/startup control tools

The MCP exposes `firetv_wake`, `dev_app_status`, `kill_dev_app`, and `dev_prepare_clean_start`. For fresh automation prefer `dev_prepare_clean_start`: it checks whether the Dev package is running, requests a normal exit first, force-stops only if the process remains, verifies the package is stopped, and optionally wakes the Fire TV. After launch, use `dev_wait_for_ui` with `automation_ready=true`; the debug APK owns that readiness decision.

# Codex MCP Setup — Dockerized

The SageTV Fire TV MCP server runs **inside the same Docker image** as ADB, `aapt`, Android SDK and the MiniClient build environment. No host Python environment is required.

The project uses MCP Python SDK v2 (`MCPServer`) and stdio transport. MCP v2 can serve legacy client initialization as well as the current protocol, which is useful while Codex client support evolves.

## Prerequisites

1. Docker/Compose available.
2. Windows workspace exists.
3. Docker image built.
4. `<project-root>\config\firetv.toml` contains the Fire TV address.

From WSL, run from the extracted project root:

```bash
./dev.sh image
```

No `.env` is required unless you intentionally override the workspace bind path.

## Verify MCP manually

Do not allocate a pseudo-TTY for stdio MCP:

```bash
./dev.sh mcp
```

It will wait for MCP protocol input; a blank terminal is expected.

## Register with Codex CLI

```bash
./scripts/register_mcp.sh
```

This registers the launcher pattern:

```bash
codex mcp add sagetv-dev-firetv -- /absolute/path/to/dev.sh mcp
```

Codex launches Docker on demand; Docker then starts the Python MCP server with `-T` so a pseudo-TTY cannot corrupt stdio protocol framing.

Verify:

```bash
codex mcp get sagetv-dev-firetv
codex mcp list
```

## Fire TV tools

Current MCP tools cover:

- ADB connect/device identity
- package information for the dev app
- verified dev APK install
- launch/stop/uninstall dev app only
- Fire TV remote keys/key sequences
- clear/read/wait-for logcat
- screenshot/screenrecord
- MediaCodec/Surface diagnostics
- focused-window diagnostics
- one-command playback failure collection

v0.5.11 adds current-playback automation through a debug-only on-demand receiver. v0.5.12 makes the seek suite self-calibrating for the SageTV server's configured FF/REW intervals. v0.5.13 hardens calibration with a 5-second preference quantum and closest-practical command planning so seek landing error is not learned into the expected result. It can set player preferences, query a one-shot state snapshot, send exact SageTV commands, validate seek deltas, and capture checkpoints. Fully unattended recording selection remains deferred until a stable UI/test-recording path is configured.

## Safety

The MCP server rejects package-management operations on `jvl.sage.miniclient...`. APK installation requires `aapt` to prove that the APK package is `org.opensagetv.miniclient.dev.debug` (or another explicitly configured non-upstream development namespace).

## Start Codex only after baseline source is ready

Use `CODEX_START_PROMPT.md` after importing the exact source and passing:

```bash
./dev.sh test
./dev.sh validate
./compile_existing_app.sh
./dev.sh build
```
## v0.5.14 Android-side skip measurement

`dev_run_seek_check` no longer infers a skip from two host snapshots. It invokes the debug APK `skip_check` receiver operation, which captures SageTV's `MEDIACMD_GETMEDIATIME` timeline before/after the sequence and returns `timelineDeltaMs` and `playbackAdjustedDeltaMs`. Rebuild the debug APK after applying v0.5.14.


## v0.5.15 output-health-first skip checks

`dev_run_seek_check` now treats the SageTV/UI timeline as diagnostic context. For Exo2/Media3-based playback, PASS requires actual video renderer output and Android AudioTrack output to resume and keep advancing. The debug receiver also returns decoder identity/type, renderer counters, audio playback-head state, Surface state, buffering/loading, datasource, retry/error state, and before/post/final snapshots. No permanent listeners are installed.

Default:

```bash
./dev.sh mcp-seek-test
```

Optional numeric timeline diagnostics:

```bash
./dev.sh mcp-seek-test --calibrate-timeline
```


## v0.5.16 Comskip test

Run `./dev.sh mcp-comskip-test` while a recording with Comskip markers is playing. As corrected in v0.5.31, the test sends direct native SageTV `right` / `left` commands—the same working path as `mcp-send-sequence` `command right` / `command left`—and reports timeline checkpoints at video recovery, audio recovery, and full A/V recovery. It does not resolve normal short-press video arrow mappings (FF/REW). Marker start/end metadata itself is owned by the SageTV STV/plugin and is not semantically exposed to this MiniClient.
### Debug seek to an explicit media time (v0.5.38)

The debug APK exposes `dev_seek_time(target_ms, tolerance_ms=2000, timeout_s=15)`. `target_ms` is required and is always supplied by the caller; use `0` for the beginning, `30000` for 30 seconds, etc. The Android debug receiver performs the local player seek and MCP verifies the SageTV-visible media timeline reaches the target tolerance.

Host convenience:

```bash
./dev.sh mcp-seek-time --target-ms 0
./dev.sh mcp-seek-time --target-ms 30000
```

The Media3 Comskip matrix accepts `--start-ms N` and passes that value through the same MCP tool before Comskip checks.


## Complete player-matrix harness

For broad regression coverage, Codex should use `./dev.sh mcp-player-matrix` rather than hand-building individual remote-key tests. First enumerate cases with `--list-cases`, then run filtered or exact `--case-id` cases as needed. The default 180000 ms watchdog is observational: `WATCHDOG_EXPIRED` does not assign fault to Android, SageTV server, or FFmpeg. Future server/FFmpeg collectors should correlate their evidence with the matrix case ID and timestamps rather than changing this attribution rule.

## v0.5.47 watchdog rule for Codex

Treat `--watchdog-ms` as a per-operation observation budget. Never subtract time used by one media action from later actions. For a 180000 ms watchdog, every selected seek, pause/resume, Comskip RIGHT, and Comskip LEFT receives its own fresh 180000 ms window. Use `watchdog_requested_ms`, `watchdog_applied_ms`, `watchdog_step_elapsed_ms`, and `watchdog_verified` when evaluating long Android debug checks. If the installed APK advertises insufficient `maxRecoveryWatchdogMs`, treat that as test infrastructure/version mismatch rather than a media failure.

### Native Search text
For normal automated Search entry, prefer `dev_input_text_native` (or `dev_type_text` with `char_delay_ms=0`). It writes correctly encoded MiniClient keyboard events directly to SageTV and does not depend on Android IME focus. `dev_input_text_keyboard`, `sendtext`, and positive `text-char-delay-ms` modes are diagnostic fallbacks only.
