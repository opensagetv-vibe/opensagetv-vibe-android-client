# MCP Playback Automation — v0.5.12

v0.5.12 adds automation to the **Dev debug APK only**. It does not re-enable the callback-based player telemetry that previously affected playback startup.

## What is automated now

Once a known test recording is already playing, MCP can:

- read current player/backend/stream/decode configuration;
- read MiniPlayer state, SageTV media timeline, server anchor, buffer-left, last file read position and video dimensions;
- send exact SageTV commands (`ff`, `rew`, `ff_2`, `rew_2`, `pause`, `play`, etc.) without depending on Android remote mappings;
- run repeatable command sequences and compare timeline movement with an expected delta;
- capture screenshot, logcat, codec/media dump and focused window when a test fails;
- change Default Player / Streaming Mode / Decoding Method / GSY engine for the **next playback**.

## Safety design

`DevTestReceiver` lives under `android-tv/src/debug` and is not present in release/main source sets. Snapshot requests are on-demand only. No player listener, frame callback, analytics listener or background telemetry loop is installed.

## First automated test

1. Build/install v0.5.12 normally.
2. Start the MiniClient and manually start the same known recording used for the current player testing.
3. Let it play for about 10 seconds.
4. From the project root run:

```bash
./dev.sh mcp-seek-test
```

The default suite validates:

- server calibration: one primary `ff` and one primary `rew` are measured first;
- semantic **+30 s**, expanded from the calibrated primary FF increment;
- semantic **-10 s**, expanded from the calibrated primary REW increment;
- rapid `+30,+30,-10,+60,-30`, expanded from those calibrated increments, expected net **+80 s**;
- Pause state (`MiniPlayerPlugin.PAUSE_STATE = 3`);
- Play state (`MiniPlayerPlugin.PLAY_STATE = 2`).

Default tolerance is ±5 seconds because MPEG-TS seeks land on sync frames and playback continues while the sequence runs. The MCP tool subtracts wall-clock playback time when both before/after states are playing.

## Useful MCP tools

- `dev_player_state`
- `dev_set_player_config`
- `dev_sage_command`
- `dev_sage_command_sequence`
- `dev_run_seek_check`
- `dev_wait_for_media_position`
- `dev_local_seek_absolute` (backend isolation only; bypasses SageTV server navigation)
- `dev_test_checkpoint`

Example configuration values:

```text
player:      exoplayer | media3 | ijkplayer | gsyplayer
streaming:   dynamic | pull | fixed
decoding:    hardware | software | hardware_preferred
gsy_engine:  auto | media3 | system | legacy_exo
```

The settings UI displays `dynamic` as **Push/Dynamic**; the stored value remains `dynamic` for compatibility.

## Large/comskip testing

The large/comskip action depends on the SageTV command/mapping used by the user's server/profile. Once that exact SageCommand key is confirmed, it can be added to the automatic suite:

```bash
./dev.sh mcp-seek-test --large-command ff_2 --large-expected-ms 150000
```

Replace the command and expected time with the actual configured behavior.

## Still manual

- choosing/opening the known test recording (until a stable UI path is configured);
- judging IJK small-window vertical jitter/tearing;
- judging a frozen/black hardware Surface when Fire OS screenshots do not capture the video plane reliably.

These are the next candidates for automation only after the control/state layer proves stable on the Fire TV.


## v0.5.12 correction

Do not infer a player failure from v0.5.11's `skip +30` result when the server's primary `ff` interval is not 30 seconds. SageTV skip intervals are configurable. v0.5.12 measures them first.

Useful overrides:

```bash
./dev.sh mcp-seek-test --ff-ms 10000 --rew-ms 10000
```

Normal semantic steps use `--paced-delay-ms 900` by default; the rapid stress sequence keeps `--delay-ms 350`.
