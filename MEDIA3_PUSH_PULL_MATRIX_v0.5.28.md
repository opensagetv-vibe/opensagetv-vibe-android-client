# Media3 Push/Dynamic vs Pull Seek-Resume Matrix — v0.5.28

## Purpose

Measure Media3 seek and resume behavior in Push/Dynamic and Pull with the same recording and the same commands before making any additional player changes. The v0.5.27 stable-UI gate is mandatory so Search is never sent while the app is still loading.

## Run both modes

```bash
./dev.sh mcp-media3-matrix --server 192.168.10.175 --text "<recording search text>"
```

`--text` is required; there is no default recording title. Other defaults: Media3, Hardware decoding, Push/Dynamic followed by Pull, 2000 ms UI stability, 8000 ms recovery timeout, and 2500 ms post-recovery verification.

## Matrix

For each streaming mode the harness starts a fresh app/session and performs:

1. Wait for a real SageTV menu to be stable before Search.
2. Start the known recording through the exact native Search/text/key sequence.
3. Verify real video and audio output.
4. Single FF and verify output recovers.
5. Single REW and verify output recovers.
6. Rapid `FF, FF, REW, FF, REW` and verify output recovers.
7. Pause, then Play, and verify real output resumes.

Timeline movement is diagnostic only. Real renderer/audio output health is authoritative.

## Slow recovery rule

A successful recovery above 2000 ms is marked `slowRecovery=true` and automatically captures a diagnostic checkpoint. This is intentionally a warning rather than FAIL because the current Phase A question is whether Pull is correct but visibly slower than Push. Override with `--slow-resume-ms`.

## Report

The default report is written to:

```text
artifacts/firetv/<timestamp>_media3_push_pull_matrix.json
```

The `recoveryComparisonMs` section puts Push/Dynamic and Pull recovery measurements side by side. Keep the report and any checkpoint bundle when asking the next AI chat to diagnose a slow/failing mode.

## Focused retest

```bash
./dev.sh mcp-media3-matrix --modes pull --text "<recording search text>"
./dev.sh mcp-media3-matrix --modes dynamic --text "<recording search text>"
```

Do not tune Media3 Pull buffering/load control merely because its SageTV timeline differs from Push. Tune only from real A/V recovery evidence and the captured decoder/buffer/data-source diagnostics.
