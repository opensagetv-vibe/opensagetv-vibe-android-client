# SageTV Fire TV MCP

This Python MCP server exposes a guarded Fire TV/ADB and debug-playback surface.
It runs inside the same unified Docker environment as ADB, Android SDK tools,
and the Android build. Codex is optional; any compatible MCP client can use it.

## Safety

- The configured package defaults to `opensagetv.vibe.miniclient.debug`.
- Package mutation refuses `jvl.sage.miniclient*`.
- APK installation verifies the application ID with `aapt`/`aapt2`.
- Debug controls are compiled only in the Android debug source set.
- After fresh install/Clear Data, complete first-time setup manually before
  running playback automation.
- Scripted tests default to `DEV001`; normal app use keeps its persisted ID.

## Run

Configure `config/firetv.toml`, then from the repository root:

```bash
./dev.sh mcp-test
./dev.sh mcp
```

PowerShell uses the same commands through `.\dev.ps1`. `mcp` uses stdio and
therefore intentionally waits on an apparently blank terminal.

To register with Codex CLI when desired:

```bash
./scripts/register_mcp.sh
```

## Current capabilities

- ADB connection, device identity, wake, app status, and guarded package tools.
- Fire TV remote keys, exact SageTV commands, native text/Search entry, and
  explicit command sequences.
- Logcat, screenshot, screen recording, focused-window, codec, Surface, and
  combined failure capture.
- One-shot player state/configuration, absolute/relative seek, Comskip,
  protocol command 28 frame-step, checkpoint, crash-probe, bounded event-ring,
  and recovery checks. `./dev.sh mcp-frame-step-test --server-path PATH`
  physically requires paused hardware Pull to advance a rendered frame and
  verifies that playing and Push requests fail safely.
- Filterable player/tuning matrices covering Legacy Exo, Media3, IJK, and GSY
  engines across Push/Pull/Fixed and decoder policies.
- Client-ID inspection/override tools for deterministic test sessions.
- Credential-free named profile list/save/load/delete through the same production
  SMB2/SMB3 repository used by the settings UI. Run
  `python3 scripts/mcp_smb_profile_test.py` for physical share acceptance.
- Exact-source Pull/SMB seek A/B timing on the generated fixture through
  `python3 scripts/mcp_smb_pull_ab_test.py`; it records source read, first
  observed decoder input, exact first-frame, and sustained A/V recovery times.
- Debug-only compatible Media3 replacement through
  `./dev.sh mcp-fast-switch-test --initial-path PATH --switch-path PATH`. The
  gate verifies exact target selection, datasource ownership, first rendered
  frame, hardware playback, attempt/success/fallback counters, and process
  survival for both Pull and SMB Direct.
- A completed-file lifecycle gate covering HOME/background, activity return,
  repeated exact-path playback, crash detection, and final process teardown.
- Restricted generation and optional SMB publication of the canonical
  `VibeSeekTest-1080i-MPEG2-AC3-CC.ts` fixture plus its prerecorded Comskip
  `.edl`. The generated MPEG-2 carries one ATSC A/53 GA94 block per picture,
  with CEA-608 CC1 and CEA-708 Service 1 timestamp cues every 0.5 seconds.

The generated fixture is used for repeatable completed-file, caption, seek,
Comskip, Pull/SMB, Push, and Fixed/MIM comparisons. It is not accepted as
channel-change evidence; growing live-TV transitions use the real HDHomeRun
channels 2.1 and 5.1.

Use `./dev.sh help` and MCP tool discovery for exact current arguments. Search
tests require explicit non-empty recording text; there is no production default
recording. A known recording must already be available when a command does not
include navigation.

## Diagnostic rules

Continuous player telemetry remains disabled because instrumentation has
previously changed playback startup. Use one-shot state, bounded existing-event
traps, logcat, codec/surface dumps, screenshots, and server-correlated evidence.
Every operation gets its own watchdog; expiry is `WATCHDOG_EXPIRED` with an
undetermined cause, not an automatic Android/server/FFmpeg failure.

Read `../docs/PLAYBACK_DIAGNOSTICS.md` before running or interpreting a player
experiment.
