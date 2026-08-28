# Complete MCP Player Test Harness — v0.5.46

## Goal

Provide one repeatable MCP-driven harness that Codex can later use to exercise the Android MiniClient against the SageTV server and, in a later phase, correlate FFmpeg/transcoder behavior without prematurely blaming any subsystem.

## Full configuration matrix

`./dev.sh mcp-player-matrix --text "<recording>"` expands to 63 configuration cases:

- Top-level players: `exoplayer`, `media3`, `ijkplayer`, `gsyplayer`
- Streaming modes: `dynamic`, `pull`, `fixed`
- Decoding policies: `hardware`, `software`, `hardware_preferred`
- GSYVideoPlayer engines: `auto`, `media3`, `system`, `legacy_exo`

Non-GSY players are 3 × 3 × 3 = 27 cases. GSY is 3 × 3 × 4 = 36 cases. Total = 63.

## Checks per configuration

Default checks are:

1. fresh deterministic app/server startup and real A/V verification
2. absolute direct Android-player seek (`--start-ms`)
3. direct relative forward seek
4. direct relative backward seek
5. direct pause/resume
6. dedicated Comskip RIGHT observation
7. dedicated Comskip LEFT observation

Android keys are not used for direct player operations. Search text retains the proven Android OS text-input path. Comskip posts the SageTV RIGHT/LEFT event internally because the SageTV STV/server owns commercial-marker selection.

## Watchdog behavior

The default media observation watchdog is 180000 ms (3 minutes):

```bash
./dev.sh mcp-player-matrix \
  --server 192.168.10.175 \
  --text "meet the press" \
  --watchdog-ms 180000
```

The Media3 Comskip matrix uses the same default and can also be set explicitly:

```bash
./dev.sh mcp-media3-comskip-matrix \
  --server 192.168.10.175 \
  --text "meet the press" \
  --modes pull \
  --watchdog-ms 180000
```

`WATCHDOG_EXPIRED` is an observation, not a harness failure. The report keeps `cause=undetermined` unless collected evidence proves the delay is in Android, the SageTV server, or FFmpeg/transcoding.

## Outcome separation

Media outcomes:

- `RECOVERED`
- `SLOW_RECOVERY`
- `WATCHDOG_EXPIRED`
- `MEDIA_NOT_RECOVERED`

Harness/infrastructure outcome:

- `INFRA_ERROR`

The process continues to later configurations after media recovery problems. It returns nonzero only when an automation/infrastructure error occurred. Use `--stop-on-infra-error` only when deliberately debugging the harness itself.

## Case discovery and filtering

List the generated matrix without touching the device:

```bash
./dev.sh mcp-player-matrix --text placeholder --list-cases
```

Run only selected dimensions:

```bash
./dev.sh mcp-player-matrix \
  --text "meet the press" \
  --players gsyplayer \
  --streaming pull \
  --decoders hardware \
  --gsy-engines auto,media3,system,legacy_exo
```

Run one exact generated case:

```bash
./dev.sh mcp-player-matrix \
  --text "meet the press" \
  --case-id gsyplayer__pull__hardware__gsy_media3
```

## Future Codex / SageTV / FFmpeg phase

The JSON report deliberately separates execution success, Android player telemetry, Pull I/O, decoder state, recovery outcome, watchdog expiration, and cause attribution. This is the base for a later harness phase that also collects SageTV server and FFmpeg/transcoder-side evidence for the same case ID and time window.
