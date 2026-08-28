# MCP Playback Output-Health Automation — v0.5.15

v0.5.15 changes the automated playback test from **timeline-first** to **real output-health-first**.

## Why

The SageTV timeline can advance while the video surface is black or audio is silent. Therefore timeline movement is useful context, but it is not sufficient proof that playback recovered after a skip.

## What a skip check verifies

For ExoPlayer Legacy, Media3, and GSY engines that delegate to them, the debug APK captures on demand:

- actual video decoder name and hardware/software classification
- video renderer decoded/rendered/dropped/queued buffer counters
- actual audio decoder name
- Android `AudioTrack` state, play state, session, sample rate and playback-head position
- player READY/playing/loading state and player error
- video Surface known/valid/shown state and dimensions
- SageTV datasource class, Push/Pull mode, buffer remaining and last file-read position
- seek-pending, retry and backend error state
- SageTV displayed timeline and server anchor as diagnostic context

The check then sends the MCP FF/REW commands and waits for:

1. video renderer output to advance again;
2. audio output / AudioTrack playback head to advance again;
3. player to return to READY + playing;
4. Surface to remain valid;
5. no player error;
6. video and audio to **continue advancing** for the verification window (default 2.5 s).

If any condition fails, the result includes an explicit reason such as `video_not_rendering`, `audio_not_advancing`, `player_not_ready_playing`, `surface_not_valid`, or a post-recovery stall.

## No continuous telemetry

`PlaybackHealthProbe` is under the Android **debug source set only**. It does not add a permanent `Player.Listener` or `AnalyticsListener`. MCP asks for a snapshot/check, the code inspects the already-live player/renderers/AudioTrack, then returns.

## Run

Because Android debug code changed, rebuild/install first:

```bash
./test_valitdate_build_install_lanuch.sh
```

Start the known recording and run:

```bash
./dev.sh mcp-seek-test
```

The default suite no longer performs timeline calibration. It runs:

- one FF output-health check
- one REW output-health check
- rapid mixed FF/REW output-health stress
- pause/resume state check
- optional large/comskip check when `--large-command` is provided

Timeline deltas are still printed, but they do not fail a supported Exo health check.

## Optional timeline diagnostics

Only when numeric seek distance matters:

```bash
./dev.sh mcp-seek-test --calibrate-timeline
```

This retains paused FF/REW calibration and the semantic +30/-10/+80 sequence. Output health remains the authoritative verdict whenever the Exo health probe is supported.

## Typical result

```text
PASS: single FF: output=output_health ...
  output health: recovery=850 ms videoRecovered=True audioRecovered=True stillPlaying=True videoStillAdvancing=True audioStillAdvancing=True failure=none
  decoder/output: videoDecoder=c2.mtk.mpeg2.decoder videoKind=hardware audioDecoder=c2.android.mp3.decoder ... surfaceValid=True audioTrackPlayState=3
  output counters: videoRecoveryFrames=... videoVerifyFrames=... audioRecoveryHeadFrames=... audioVerifyHeadFrames=...
```

## Failure artifacts

A failed check automatically saves:

- logcat
- crash log buffer
- MediaCodec / media extractor / SurfaceFlinger dumps
- Android audio-system dumps
- focused window
- screen capture
- compact player/decoder/output state

These are intended to distinguish datasource stalls, decoder stalls, invalid surfaces, audio-output failure, player errors and long rebuffering without manually reproducing the failure again.

## IJK / Android System note

The probe returns useful basic state for non-Exo backends, but the strict renderer-counter + AudioTrack health verdict is currently designed for Exo2/Media3 paths. Do not add intrusive continuous hooks to legacy IJK merely to match the Exo diagnostics.
