# MCP Playback Automation v0.5.17

## Why this release exists

Device testing of v0.5.16 exposed two debug-harness problems during Pull playback:

1. AC-3/passthrough AudioTrack can reset its playback-head counter during a backward seek while reusing the same audio session. The old health probe incorrectly called this `audio_not_advancing` even when video and player state had recovered.
2. Long `skip_check` / `comskip_check` operations were sent with `--receiver-foreground`. Slow Pull recovery can exceed Android's tighter foreground-broadcast execution window and destabilize the test session.

v0.5.17 fixes both without changing production player behavior.

## Test

Rebuild/install because debug Android code changed:

```bash
./test_valitdate_build_install_lanuch.sh
```

Start the known recording, then run:

```bash
./dev.sh mcp-seek-test
```

For Comskip:

```bash
./dev.sh mcp-comskip-test --direction both
```

## New diagnostics

Seek/comskip results now include:

- `audioHeadResetDuringRecovery`
- `audioHeadResetDuringVerify`

If these are `true`, the AudioTrack playback-head counter reset during the seek but the new counter was actively advancing. This is valid audio recovery, not a stall.

## Failure preservation

If single FF or single REW fails real output-health validation, the suite captures diagnostics and stops before the rapid 16-command stress and pause/resume checks. This prevents a basic failure from being obscured by follow-on commands.

## Media3 / GSY Pull retest

Retest both Media3/Pull and GSY Auto/Pull. The long health broadcast is now a normal explicit broadcast rather than a foreground broadcast. If either still crashes, capture:

```bash
./dev.sh player-diag media3-pull-v0517-crash
```

or:

```bash
./dev.sh player-diag gsy-auto-pull-v0517-crash
```

A remaining crash after this harness correction should be treated as a real Media3/Pull runtime issue.
