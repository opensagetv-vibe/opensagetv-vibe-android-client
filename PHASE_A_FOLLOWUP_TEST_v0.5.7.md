# Phase A Follow-up Device Test — v0.5.7

Use the **same recording** and keep **Decoding Method = Hardware**. This revision does not change decoder policy.

## 1. Build/install

```bash
./test_valitdate_build_install_lanuch.sh
```

Confirm version `0.5.7`.

## 2. ExoPlayer Legacy — Pull (primary v0.5.7 fix)

1. Start playback and let it run 10 seconds.
2. `+30`, wait for stable playback. **PASS:** lands forward and does not snap to `0:00`.
3. `-10`. **PASS:** lands backward and does not snap to `0:00`.
4. Rapid sequence `+30, +30, -10, +60, -30`. Expected net movement about **+50 seconds** from the starting point.
5. Seek/jump to beginning. **PASS:** reaches `0:00` only when explicitly requested.
6. Pause/start/frame-step regression.
7. Large comskip marker jump. Record displayed timeline and actual visible location.

If any command first lands correctly and then returns to the beginning, capture immediately:

```bash
./dev.sh player-diag exo2-pull-v057-snap
```

## 3. Media3 — Pull

Repeat the exact Exo Legacy Pull matrix. The Pull FLUSH implementation was identical and received the same correction.

```bash
./dev.sh player-diag media3-pull-v057-fail
```

## 4. GSYVideoPlayer — Auto

Auto now resolves to the SageTV-aware **Media3** engine.

### Dynamic

- Start Play: expected PASS.
- Basic `+30/-10`: expected same behavior as top-level Media3 Dynamic.
- Rapid sequence: record result.
- Large comskip: record timeline vs visible video.

### Pull

Repeat the full Pull matrix. Expected behavior should match top-level Media3 Pull.

## 5. GSYVideoPlayer — Android System MediaPlayer (explicit compatibility test)

This is no longer chosen automatically. Test it explicitly.

### Dynamic

Start playback. The v0.5.7 PUSH bridge no longer returns `0` for a non-zero Android `MediaDataSource.readAt()` request while waiting for SageTV bytes.

**Primary pass criterion:** playback starts.

**Minimum failure criterion:** if the Fire TV System MediaPlayer still rejects the stream/codec, the app must fail cleanly; it must not process-crash.

### Pull

Start, stop, reopen, and exit playback several times. This stresses MediaPlayer Binder close plus normal teardown.

**PASS:** no `JNI DETECTED ERROR`, `remoteServer.close()` NPE, SIGABRT, or process exit.

If System fails:

```bash
./dev.sh player-diag gsy-system-v057-fail
```

## 6. IJKPlayer — Pull control

Do only a smoke regression: Start, `+30`, `-10`, rapid sequence, large comskip. It passed the v0.5.6 matrix and was not modified.

## 7. What remains deferred

Do not treat these as Phase A regressions yet:

- IJK Dynamic large-comskip timeline behavior.
- Any Dynamic/PUSH case where the visible video and SageTV displayed timeline disagree after a large skip.
- GSY Legacy Exo Dynamic large-skip timeline mismatch.

Those are the inputs for Phase B/C once the Pull snap-to-zero regression is closed.
