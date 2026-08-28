# MCP Playback Automation — v0.5.14

v0.5.14 moves skip measurement into the Android debug APK.

## What is measured

For every `dev_run_seek_check`, the debug receiver:

1. Reads `player.getMediaTimeMillis(mediaCmd.getLastServerStartPosition())`. This is the same calculation returned by `MediaCmd.MEDIACMD_GETMEDIATIME`, which drives SageTV's displayed playback timeline/time bar.
2. Sends the requested SageTV command sequence from inside the MiniClient process.
3. Waits the requested command delay/settle interval.
4. Reads the same timeline again.
5. Returns:
   - `timelineBeforeMs`
   - `timelineAfterMs`
   - `timelineDeltaMs`
   - `elapsedMs`
   - `playbackAdjustedDeltaMs` when both samples remained in PLAY state
   - `beforeState` / `afterState`
   - `timelineSource=MEDIACMD_GETMEDIATIME`

There are no continuous listeners or callbacks.

## Snapshot fields

`dev_player_state` now includes both:

- `mediaTimeMs` — compatibility field
- `sageTimelineMs` — explicit SageTV/UI timeline field
- `timelineSource=MEDIACMD_GETMEDIATIME`

## Test

After applying v0.5.14, rebuild/install the debug APK because `DevTestReceiver` changed:

```bash
./test_valitdate_build_install_lanuch.sh
```

Start the known test recording, then run:

```bash
./dev.sh mcp-seek-test
```

Calibration pauses playback, obtains multiple Android-side FF/REW measurements, rejects wrong-direction/zero outliers, uses the median, then resumes playback for normal and rapid tests.

The rapid semantic sequence remains:

```text
+30 +30 -10 +60 -30 = +80 seconds
```

## Expected debug result shape

```text
timelineSource=MEDIACMD_GETMEDIATIME
timelineBeforeMs=120000
timelineAfterMs=152200
timelineDeltaMs=32200
elapsedMs=2150
playbackAdjustedDeltaMs=30050
```

That means SageTV's displayed timeline moved 32.2 seconds during the whole measurement window, of which about 2.15 seconds was ordinary playback time, giving a measured skip contribution of about +30.05 seconds.
