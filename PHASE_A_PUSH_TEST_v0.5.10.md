# v0.5.10 Exo PUSH verification

Use the same recording and Decoding Method = Hardware used for the prior tests.

## 1. ExoPlayer Legacy — Dynamic/PUSH
- Start playback. Expected: picture and audio begin normally; no persistent black screen.
- Let it play 15 seconds.
- Run `+30`, `-10`, then `+30,+30,-10,+60,-30`.
- Run one large comskip jump.
- Expected: playback resumes with audio/video after each operation.

## 2. Media3 ExoPlayer — Dynamic/PUSH
Repeat the same sequence and expectations.

## 3. GSY Auto — Dynamic/PUSH
GSY Auto delegates to Media3, so repeat the same sequence.

## 4. Pull regressions
Quick smoke test Exo2 Pull, Media3 Pull, GSY Auto Pull and IJK Pull. The v0.5.8/v0.5.9 seek/timeline behavior must remain unchanged.

## Diagnostics if PUSH is still black
While the black screen is present run:

```bash
./dev.sh player-diag exo2-push-v0510-black
```

or

```bash
./dev.sh player-diag media3-push-v0510-black
```

Report whether audio is also absent and whether the SageTV timeline is advancing.
