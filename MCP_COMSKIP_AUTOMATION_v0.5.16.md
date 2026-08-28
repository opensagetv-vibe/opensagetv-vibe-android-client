# MCP Comskip Automation — v0.5.16

> **v0.5.31 correction:** do not use the normal configured video left/right mapping for Comskip. Device testing proved the working action is direct native SageTV `right` / `left` (the same as `mcp-send-sequence` `command right` / `command left`). v0.5.31 keeps the output-health instrumentation but sends those commands directly.

## Goal
Verify the same Comskip jump the user performs with the left/right video arrows, and confirm real video/audio output resumes and stays healthy.

## What Android can read
The MiniClient can read its configured `videoplaying_right` and `videoplaying_left` mappings and the SageTV media timeline. It can inspect real decoder/video/audio output health.

The MiniClient **does not receive semantic Comskip marker start/end metadata** from the SageTV STV/plugin. Therefore the test does not guess a marker target. It reports the actual A/V recovery landing. If a known marker target is available externally, pass it with `--expected-target-ms`.

## Run
Start a recording that has visible Comskip markers and begin playback.

```bash
./dev.sh mcp-comskip-test
```

Default direction is right/forward. Test left/backward instead:

```bash
./dev.sh mcp-comskip-test --direction left
```

Test both sequentially:

```bash
./dev.sh mcp-comskip-test --direction both
```

If the expected marker landing is known, validate it directly:

```bash
./dev.sh mcp-comskip-test --direction right --expected-target-ms 615000 --tolerance-ms 5000
```

## Returned landing checkpoints
- `timelineBeforeMs` — SageTV/UI timeline before the arrow command.
- `videoRecoveryTimelineMs` — timeline when decoded video output first advances again.
- `audioRecoveryTimelineMs` — timeline when Android audio output first advances again.
- `outputRecoveryTimelineMs` — timeline when video + audio + player/surface health are all recovered.
- `landingTimelineMs` — authoritative recovery landing (full A/V recovery when available).
- `landingDeltaMs` — landing minus pre-command timeline.

## PASS rule
PASS is output-health-first: video and audio must recover, Surface/player must be healthy, and both outputs must continue advancing through the verification window. Timeline distance is diagnostic unless an explicit expected marker target is supplied.

## Debug data
The result includes the resolved Android arrow mapping, video/audio decoder identity/type, renderer counters, AudioTrack playback-head movement, buffering/loading, datasource/file position, decoder reinitialization, player errors, and failure reasons. Failed checks should use the existing MCP diagnostic checkpoint bundle.
