# Phase A Seek / Timeline Verification — v0.5.5

Phase A intentionally changes only low-risk backend seek behavior. It does **not** yet normalize SageTV PUSH timeline rebasing across players; that is reserved for a later phase.

## What Phase A changes

- ExoPlayer Legacy: allow seek-to-zero, remove the UI-thread seek wait loop, and mark seek completion only on Exo's seek discontinuity callback.
- Media3 ExoPlayer: same changes as ExoPlayer Legacy.
- IJKPlayer: paused frame-step changes from an incorrect +1000 ms jump to approximately one 30 fps frame (+33 ms).
- GSYVideoPlayer: no functional seek change in Phase A; test it as a regression baseline.

## Test setup

Use the **same known-good recorded program** for every player. Prefer a recording at least 15 minutes long with visible motion and/or an on-screen clock so the landed frame is easy to judge.

For the primary Phase A test, set **Streaming Mode = Pull**. Pull mode isolates the player's local seek behavior and avoids mixing the later PUSH-rebase work into this test.

Before each player test:

1. Select the player engine.
2. Stop playback completely and reopen the same recording.
3. Start near a known timeline position, preferably around `10:00`.
4. Confirm normal playback for 10 seconds before testing seeks.
5. If collecting diagnostics, run `./dev.sh player-diag clear` before the test and capture a named diagnostic immediately afterward.

## Pass criteria used for all players

A seek passes when:

- the UI remains responsive;
- playback resumes without a black screen, player restart, or disconnect;
- the timeline moves in the requested direction and does not snap back to the pre-seek position;
- the final visible content corresponds reasonably to the requested position (keyframe-level variance is acceptable);
- rapid skip commands do not leave the player stuck in buffering/seeking;
- audio/video remain synchronized after playback resumes.

Do **not** require the first decoded frame to equal the requested millisecond exactly. Compressed video normally lands on or around a seekable/key frame.

---

## Test 1 — Basic forward/back seek

Start at approximately `10:00`.

1. Skip `+30s` once. Expected final timeline: approximately `10:30`.
2. Let playback run for 5 seconds.
3. Skip `-10s` once. Expected timeline: approximately 10 seconds earlier than the position immediately before the command.
4. Let playback run for 10 seconds.

**PASS:** both directions work, playback resumes quickly, and the timeline does not jump back to the old position.

---

## Test 2 — Rapid skip sequence

Restart the recording and pause/observe the starting timeline position. Call that position `T0`.

Press this sequence as quickly as practical without waiting for each seek to finish:

```text
+30
+30
-10
+60
-30
```

Net requested movement is **+50 seconds**.

**Expected final position:** approximately `T0 + 50s`.

**PASS:** the final playback location is near the net target, the app remains responsive, and stale/earlier seeks do not visibly pull playback back after the final command.

Record whether the player instead behaves like queued seeks, latest-command-wins, or lands at an obviously incorrect location. That distinction will guide the later shared seek coordinator.

---

## Test 3 — Repeated same-direction skips

Restart near `10:00`.

### Forward stress

Press `+30s` five times rapidly.

Expected net movement: approximately **+150 seconds**.

### Reverse stress

After playback stabilizes, press `-10s` five times rapidly.

Expected net movement: approximately **-50 seconds** from the stabilized position.

**PASS:** playback reaches approximately the net target and does not remain stuck in a seek/buffering state.

---

## Test 4 — Seek to beginning / zero

This test is especially important for **ExoPlayer Legacy** and **Media3**, because Phase A fixes a code path that previously discarded an exact `0 ms` seek.

1. Play the recording until at least `05:00`.
2. Use the SageTV command/UI action that seeks/jumps to the beginning of the recording (`0:00`).
3. Confirm the timeline returns to the beginning and playback resumes from the start.

**PASS:** playback reaches the beginning rather than ignoring the command or remaining at the old position.

---

## Test 5 — Pause / frame-step behavior

### IJKPlayer — required

Use **Streaming Mode = Pull**.

1. Pause on a scene with visible motion.
2. Press Pause again once.
3. Observe the frame/timeline movement.
4. Repeat several times slowly.

Phase A changes the old IJK behavior from roughly **+1 second per repeated Pause** to approximately **+33 ms per repeated Pause** (one assumed 30 fps frame).

**PASS:** a repeated Pause while already paused advances only a tiny amount / approximately one frame, not about one second.

### ExoPlayer Legacy / Media3 — regression check

Repeat the same paused frame-step test.

**PASS:** behavior remains approximately one frame per repeated Pause and playback resumes normally afterward.

### GSYVideoPlayer

No frame-step behavior was changed in Phase A. Record its current behavior for comparison; do not fail Phase A solely because GSY behaves differently here.

---

## Test 6 — UI responsiveness during seek — ExoPlayer Legacy and Media3

This verifies removal of the old UI-thread `Thread.sleep()` seek wait loop.

1. Start playback.
2. Rapidly perform several skips.
3. During/just after the skips, open/close the SageTV OSD or move through a menu that normally responds immediately.

**PASS:** the UI does not freeze in roughly 100 ms steps or stall for up to about one second per seek.

---

# Player-by-player run order

Run the complete sequence in this order so results are easy to compare:

1. **ExoPlayer Legacy**
   - Tests 1–4 and 6 are required.
   - Test 5 is a regression check.
2. **Media3 ExoPlayer**
   - Tests 1–4 and 6 are required.
   - Test 5 is a regression check.
3. **IJKPlayer**
   - Tests 1–3 are regression checks.
   - Test 5 is required and is the direct IJK Phase A fix.
   - Test 4 is useful as a general baseline.
4. **GSYVideoPlayer → Auto**
   - Tests 1–4 are regression/baseline checks.
   - Record any timeline offset, double-seek, or skip weirdness; those are candidates for the later shared PUSH/PULL timeline work.

After the Pull tests pass, repeat **Tests 1–3 once using your normal Streaming Mode (Dynamic/Fixed)** for each player. Do not try to fix differences in PUSH behavior as part of Phase A; just record them for Phase B/C comparison.

## Suggested result table

| Player | Basic +/- | Rapid net +50 | +30 x5 | -10 x5 | Seek 0 | Pause step | UI responsive | Notes |
|---|---|---|---|---|---|---|---|---|
| ExoPlayer Legacy | | | | | | | | |
| Media3 | | | | | | | | |
| IJKPlayer | | | | | | | | |
| GSY Auto | | | | | | | | |

## Diagnostic capture when something fails

Immediately after a failure, before changing players:

```bash
./dev.sh player-diag <player>-phase-a-fail
```

Examples:

```bash
./dev.sh player-diag exo2-phase-a-fail
./dev.sh player-diag media3-phase-a-fail
./dev.sh player-diag ijk-phase-a-fail
./dev.sh player-diag gsy-phase-a-fail
```

Upload the generated `*_logcat.txt` plus the terminal result and describe:

- player engine;
- Streaming Mode;
- starting timeline position;
- exact skip sequence;
- expected final position;
- actual final position;
- whether video/audio continued, froze, restarted, or disconnected.
