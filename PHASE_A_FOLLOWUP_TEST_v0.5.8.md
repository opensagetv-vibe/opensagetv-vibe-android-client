# Phase A Follow-up Test — v0.5.8

## Goal

Verify whether widening the Exo MPEG-TS seek-map search fixes the remaining Pull behavior where playback jumps toward the requested point and then returns to `0:00`.

Use the same recording for every backend. Do not change decoder settings during this matrix.

## 1. Build/install

```bash
./test_valitdate_build_install_lanuch.sh
```

Confirm the project reports version `0.5.8`.

## 2. ExoPlayer Legacy — Pull

Set:

- Player: ExoPlayer Legacy
- Streaming Mode: Pull

Start a recording and wait until its timeline is stable. Prefer a recording at least 20 minutes long. Start testing around 10:00 if possible.

Run in order:

1. Start Play — PASS/FAIL
2. `+30` — verify video and timeline remain near the requested target rather than returning to 0:00.
3. `-10` — same check.
4. Rapid: `+30, +30, -10, +60, -30` — final target should be about +50 seconds from the starting point.
5. Seek/move to start — should intentionally end at 0:00 and remain there.
6. Pause / repeated pause frame-step / Play.
7. Large skip using a comskip marker.

If any seek returns to the beginning unexpectedly, immediately run:

```bash
./dev.sh player-diag exo2-pull-seek-v058
```

In the log, preserve lines containing:

```text
Pull seek capability: seekable=
Open: Offset:
```

The most important value is whether `seekable=true` or `seekable=false`.

## 3. Media3 — Pull

Repeat the exact same sequence with Media3. On failure:

```bash
./dev.sh player-diag media3-pull-seek-v058
```

Again record `seekable`, `durationMs`, `bufferedPositionMs`, and the Pull `Open` offsets.

## 4. GSYVideoPlayer — Auto — Pull

GSY Auto currently delegates to Media3. Repeat the same sequence. Its result should match top-level Media3. A different result is a GSY delegation/lifecycle issue.

On failure:

```bash
./dev.sh player-diag gsy-auto-pull-seek-v058
```

## 5. Dynamic regression check

Because v0.5.8 changes only Pull extractor configuration, perform a short Dynamic smoke test with:

- ExoPlayer Legacy
- Media3
- GSY Auto

For each: Start Play, `+30`, `-10`, large comskip. Dynamic should behave exactly as v0.5.7.

## 6. IJK control

Run one Pull smoke test on IJK (`+30`, `-10`, large comskip). It should remain unchanged and passing.

## Result format

```text
# ExoPlayer Legacy / Pull
Start Play:
seekable=
+30:
-10:
rapid sequence:
move to start:
pause/start:
large comskip:

# Media3 / Pull
Start Play:
seekable=
+30:
-10:
rapid sequence:
move to start:
pause/start:
large comskip:

# GSY Auto / Pull
Start Play:
seekable=
rapid sequence:
large comskip:

# Dynamic smoke
Exo Legacy:
Media3:
GSY Auto:

# IJK Pull control
Result:
```
