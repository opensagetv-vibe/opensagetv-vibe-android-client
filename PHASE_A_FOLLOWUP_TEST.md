# Phase A Follow-up Device Test — v0.5.6

Use the **same recording** used for the v0.5.5 matrix. Keep **Decoding Method = Hardware** so this retest changes only the v0.5.6 Surface/PULL fixes.

## 1. Build/install

```bash
./test_valitdate_build_install_lanuch.sh
```

Confirm the app reports version `0.5.6`.

## 2. ExoPlayer Legacy — Pull (primary PULL fix)

Set top-level player **ExoPlayer Legacy**, Streaming Mode **Pull**.

Expected now:

- Start Play: PASS (no `ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE`)
- rapid sequence `+30, +30, -10, +60, -30`: PASS
- move to start / 0:00: PASS
- pause/start: PASS
- Large Skip with comskip marker: record result

If startup still fails, immediately run:

```bash
./dev.sh player-diag exo2-pull-v056-fail
```

The important log line is the custom DataSource `Open: Offset: ..., Requested Length: ..., Size: ...`.

## 3. Media3 — Pull (same shared fix)

Repeat the exact matrix with **Media3 ExoPlayer**, Streaming Mode **Pull**.

If it fails:

```bash
./dev.sh player-diag media3-pull-v056-fail
```

## 4. GSY Auto — Dynamic and Pull (Surface fix)

Select **GSYVideoPlayer -> Auto**. Test Dynamic first, then Pull.

Expected now:

- No process exit/crash on Start Play.
- No `IllegalArgumentException: The surface has been released`.
- Log may briefly say `GSY/System waiting for video surface before prepare`; playback should start when the Surface is created.
- Run the same rapid/zero/pause/comskip matrix and record results.

If it crashes:

```bash
./dev.sh player-diag gsy-auto-v056-fail
```

## 5. GSY explicit Android System MediaPlayer

Select **GSYVideoPlayer -> Android System MediaPlayer** and repeat Start Play in Dynamic and Pull. This verifies Auto is not hiding a System-specific issue.

## 6. GSY Media3 / Legacy Exo — Pull

Retest both GSY delegates in Pull. They use the same corrected Exo2/Media3 custom DataSources, so the previous out-of-range startup failure should be gone.

## 7. Regression smoke only

Do one Start Play + one `+30/-10` in Dynamic for top-level Exo Legacy, Media3, and IJK. These paths already passed and were not changed by v0.5.6.

## Deferred — do not treat as v0.5.6 failure

The following remain Phase B/C targets and were intentionally not changed:

- IJK Dynamic large comskip failure.
- GSY Legacy Exo Dynamic large-comskip timeline mismatch/snap.
- Any PUSH/Dynamic timeline offset where the visible frame and SageTV timeline disagree after a large skip.

For each deferred mismatch, record **start timeline, skip/comskip target, displayed timeline after jump, and where the video actually plays**. That data will drive the shared timeline state in Phase B.
