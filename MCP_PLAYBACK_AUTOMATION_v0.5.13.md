# MCP Playback Automation — v0.5.13

v0.5.13 changes only the host-side MCP seek-test harness. The v0.5.11+ debug APK receiver is unchanged, so no APK rebuild is required when updating from v0.5.12.

## Run

Start a known recording, then:

```bash
./dev.sh mcp-seek-test
```

## Calibration behavior

The suite measures one primary `ff` and `rew` response. By default it rounds those measurements to a 5000 ms preference quantum. This deliberately avoids learning small player landing errors into the expected skip configuration.

Example from device testing:

```text
observed FF  = +8846 ms -> configured intent +10000 ms
observed REW = -11112 ms -> configured intent -10000 ms
```

The semantic checks then expand as:

```text
+30 => ff,ff,ff
-10 => rew
+30,+30,-10,+60,-30 => net +80 seconds
```

For unusual server settings, use:

```bash
./dev.sh mcp-seek-test --calibration-quantum-ms 1000
./dev.sh mcp-seek-test --ff-ms 7000 --rew-ms 12000
```

If an interval cannot exactly express a semantic step, v0.5.13 no longer aborts. It prints both the semantic target and the closest practical represented target, then validates the player against the exact commands sent.

Example for calibrated +9/-11 seconds:

```text
Plan: skip +30: semantic=+30000 ms represented=+27000 ms commands=['ff','ff','ff']
Rapid plan totals: semantic=+80000 ms represented=+73000 ms
```

## Pass/fail meaning

A seek failure now means the observed SageTV timeline did not match the actual command plan within tolerance. Calibration/planning mismatch is not reported as a player failure. Pause/resume and automatic failure checkpoints remain unchanged.
