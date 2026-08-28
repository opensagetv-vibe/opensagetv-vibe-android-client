# Player Architecture — v0.5.7

## Goal

Keep SageTV's `MiniPlayerPlugin` lifecycle and custom PUSH/PULL sources while comparing independent engines under one SageTV seek/timeline contract.

```text
PlayerFactory
  -> EXOPLAYER   -> Exo2MediaPlayerImpl        [2.18.1, default]
  -> MEDIA3      -> Media3MediaPlayerImpl      [1.11.0]
  -> IJKPLAYER   -> IJKMediaPlayerImpl         [original SageTV IJK 0.8.8]
  -> GSYPLAYER   -> GSYMediaPlayerImpl
                       -> Auto
                       -> Media3 delegate
                       -> Android System MediaPlayer
                       -> Legacy Exo delegate
```

Legacy IJK and GSY are independent. GSY's IJK/ex_so runtime is not packaged.

## Phase A / v0.5.7 findings

- Exo Legacy Pull startup now passes after the v0.5.6 range fix, but SageTV FLUSH exposed a second bug: the Exo2/Media3 backend `flush()` reset the MediaSource to position zero after the local seek. v0.5.7 makes that destructive reset PUSH-only.
- Media3 shares the same corrected Pull seek/flush contract.
- IJK Pull remains the passing control path and is unchanged.
- GSY Auto now resolves to Media3, the currently known-good SageTV-aware engine, rather than automatically selecting System for custom streams. System stays explicitly selectable.
- GSY/System PUSH waits for real bytes in the Android MediaDataSource bridge; GSY/System Pull teardown is now idempotent/thread-safe.
- Phase B should centralize SageTV absolute/backend-relative timeline state. Phase C should normalize PUSH/Dynamic server rebase versus local player seek behavior.

## Shared Decoding Method

Decoder policy remains separate from seek/timeline work. Hardware / Software / Hardware Preferred stay exposed for all top-level backends, with each independent engine applying the policy it can actually support. No decoder changes are part of v0.5.6.

## Transport reserve

Current SageTV PUSH/PULL remains the baseline. Future HTTP MPEG-TS, HLS, RTMP, and SRT paths should remain possible. Unused dependencies can be excluded from today's runtime graph, but future transport hooks/capabilities should not be removed solely because they are not currently active.

## v0.5.2 player isolation update

Legacy **IJKPlayer** and **GSYVideoPlayer** are separate. IJK uses the original local 0.8.8 Java/JNI AARs and never uses GSY. GSY has its own `gsy_player_engine` setting with Auto / Media3-Exo / Android System / Legacy Exo. Auto currently chooses Media3 for SageTV sources; System remains explicitly selectable while its custom MediaDataSource compatibility is hardened. GSY's modern IJK/ex_so runtime is intentionally not packaged because it collides with the legacy `tv.danmaku.ijk` namespace and `libijk*.so` names.
## Seek/timeline refactor boundary (v0.5.5 Phase A)

Phase A intentionally fixes only low-risk backend behavior. ExoPlayer Legacy and Media3 issue asynchronous seeks without blocking the UI thread and accept exact zero seeks. Their `seekPending` state is cleared only by a seek discontinuity callback, not by arbitrary timeline changes. IJK repeated-Pause frame stepping uses an assumed 30 fps step (~33 ms) instead of the previous 1000 ms jump.

The larger architectural problem remains deliberately unfixed in Phase A: SageTV absolute time, backend-relative time, and PUSH stream rebasing are still handled differently by each backend. A later phase will centralize that SageTV timeline/seek contract without forcing the backend player APIs themselves to become identical.



## v0.5.8 Pull progressive seek-map tuning

The Exo-based Pull paths use a custom progressive extractor configuration because SageTV PVR recordings commonly use MPEG-TS. The default `TsExtractor` timestamp search window can fail to find PCR timestamps on some broadcast files, leaving the progressive timeline non-seekable or resolving seeks back to the default position. Pull therefore uses an 8x timestamp search window plus closest-sync seeks. Dynamic/PUSH keeps the default extractor configuration. This is deliberately backend-specific plumbing; the later shared timeline refactor must not hide whether the underlying player can actually seek.

## v0.5.9 Exo Pull resume-latency profile

v0.5.8 established correct MPEG-TS seek maps for Exo-based Pull playback. Device testing then showed correct timeline/seek targets but delayed visible-frame resumption. v0.5.9 keeps seek semantics unchanged and optimizes the refill path:

- `BufferedPullDataSource` default remains 32 KiB.
- Exo2/Media3 Pull instantiate it with 256 KiB to reduce SageTV protocol round trips.
- Exo2/Media3 Pull use a dedicated low-latency LoadControl (5 s min, 20 s max, 500 ms after seek, 1000 ms after rebuffer).
- Dynamic/PUSH, IJK, and GSY System do not inherit these tuning values.
- GSY Auto receives the behavior only because it delegates to Media3.

This remains an optimization of the existing SageTV Pull transport, not a replacement for future HTTP MPEG-TS/HLS/RTMP/SRT work.
