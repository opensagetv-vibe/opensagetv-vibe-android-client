# Phase A Pull Resume-Latency Test — v0.5.9

## Goal

v0.5.8 fixed Pull seek correctness for Legacy ExoPlayer, Media3, and GSY Auto. The remaining device symptom is delayed video resumption after a successful seek: the SageTV timeline stays at the correct target, but video can remain stalled before playback resumes.

v0.5.9 changes only Exo-based Pull performance:

- SageTV Pull network read buffer: 32 KiB -> 256 KiB for Exo2/Media3 only.
- Pull LoadControl: 5 s minimum / 20 s maximum forward buffer.
- Resume after user seek: 500 ms buffered target.
- Resume after rebuffer: 1000 ms buffered target.
- Existing 8x MPEG-TS timestamp search and CLOSEST_SYNC seek remain unchanged.
- Dynamic/PUSH and IJK are unchanged.

## Build

Confirm `VERSION` reports `0.5.9`, then run:

```bash
./test_valitdate_build_install_lanuch.sh
```

Use the same MPEG-2 recording used for v0.5.8 testing and keep **Decoding Method = Hardware** so results are comparable.

## Test 1 — Legacy ExoPlayer / Pull

1. Select **ExoPlayer Legacy**.
2. Select **Streaming Mode = Pull**.
3. Start playback and let it stabilize for about 10 seconds.
4. Press `+30` once.
5. Observe how long the picture remains frozen before visible motion resumes.
6. Press `-10` once and repeat the observation.
7. Run the rapid sequence: `+30, +30, -10, +60, -30`.
8. Run one large comskip-marker jump.

Pass criteria:

- Timeline remains at the requested position and never returns to `0:00`.
- Picture resumes automatically after every seek.
- Normal single seeks should visually resume in roughly 0-2 seconds on the LAN; record anything consistently longer than 2 seconds.
- Rapid sequence and comskip should not require another Play/Pause command to restart playback.

## Test 2 — Media3 / Pull

Repeat Test 1 with **Media3 ExoPlayer**.

Pass criteria are identical. Compare seek-to-picture latency against Legacy ExoPlayer.

## Test 3 — GSYVideoPlayer / Auto / Pull

1. Select **GSYVideoPlayer**.
2. Select **GSY Player Engine = Auto**.
3. Select **Streaming Mode = Pull**.
4. Repeat the same single-seek, rapid-seek, and large-comskip sequence.

GSY Auto currently delegates SageTV playback to Media3, so it should be very close to the top-level Media3 result.

## Test 4 — Dynamic regression

For Legacy ExoPlayer, Media3, and GSY Auto, perform one `+30`, one `-10`, and one large comskip in **Dynamic** mode.

Expected: no behavior change from v0.5.8.

## Test 5 — IJK Pull regression

Run a short IJK Pull smoke test only:

- startup
- `+30`
- `-10`
- one large comskip

Expected: unchanged from v0.5.8.

## If video still stalls after a correct seek

While the picture is stalled, collect diagnostics before it resumes if possible:

```bash
./dev.sh player-diag media3-pull-v059-stall
```

or

```bash
./dev.sh player-diag exo2-pull-v059-stall
```

Record:

- requested seek amount/target;
- approximate number of seconds until visible video resumed;
- whether audio resumed before video;
- whether the timeline kept advancing during the stall;
- whether another Play/Pause command was required.

The v0.5.9 log now includes `Pull playback state=` transitions, current position, buffered position, and `playWhenReady`. If a stall remains, these values distinguish network-read latency from decoder/render readiness.
