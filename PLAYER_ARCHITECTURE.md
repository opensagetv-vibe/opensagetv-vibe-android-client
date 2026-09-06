# Android player architecture

The Android client preserves SageTV's `MiniPlayerPlugin` lifecycle and custom
Push/Pull data sources while keeping playback engines independently selectable.

```text
PlayerFactory
  -> EXOPLAYER -> Exo2MediaPlayerImpl (default)
  -> MEDIA3    -> Media3MediaPlayerImpl
  -> IJKPLAYER -> IJKMediaPlayerImpl (original SageTV IJK runtime)
  -> GSYPLAYER -> GSYMediaPlayerImpl
                    -> Auto
                    -> Media3 delegate
                    -> Android System MediaPlayer (commissioning probe only)
                    -> Legacy Exo delegate
```

Legacy IJK and GSY are independent. GSY's conflicting IJK/ex_so native runtime
is intentionally not packaged.

Android System MediaPlayer cannot consume the SageTV Pull `MediaDataSource` on
the commissioned AFTMM/API-25 device: it returns error `1/-2147483648` before
rendering. The real System path is therefore reachable only through the
debug-only `gsy_system_probe` commissioning gate. A failure is contained and
reopens the same load once through Media3. The ordinary UI `System` choice and
`Auto` remain fail-safe on Media3.

## Transport ownership

- Pull reads a SageTV file/data source and performs Android-local seeks.
- Push/Fixed receives a server-owned stream and FLUSH/rebase semantics.
- HTTP and future transport hooks must remain possible without pretending they
  share the SageTV Push/Pull timeline contract.

## SMB Direct / Shadow Pull audit

SMB Direct is an alternative byte source for ordinary SageTV Pull playback;
it is not another player backend and does not bypass the MiniPlayer session.
The implemented call path is:

```text
MiniClientConnection property negotiation
  -> streaming_mode controls PUSH_AV_CONTAINERS/PULL_AV_CONTAINERS
MediaCmd.MEDIACMD_OPENURL
  -> MediaUrlContext removes Vibe metadata and preserves the original path
  -> MiniPlayerPlugin.load(..., url, server, active, bufferSize)
Media3MediaPlayerImpl / Exo2MediaPlayerImpl
  -> BaseMediaPlayerImpl constructs stv://server/original-path
  -> Media3PullDataSource / Exo2PullDataSource
  -> SmbSourceSelector
       -> SageTV Pull: BufferedPullDataSource -> SimplePullDataSource
       -> SMB Direct: ReadAheadDataSource -> SmbDirectSession -> SMBJ
                         + ShadowMediaServerSession
```

The selector owns the SMB file and shadow MediaServer connection across Exo
extractor range closes; those closes flush only read-ahead state.
`BaseMediaPlayerImpl.releasePlayer()` is the final playback-session cleanup
boundary. The shadow sends normal `OPEN`, `SIZE`, and `CLOSE`. Stock SageTV
also requires its file-position state to follow non-sequential player reads for
STV-owned seek/Comskip decisions, so each non-sequential SMB access issues one
same-offset, one-byte shadow `READ`. These bytes are counted explicitly as
`shadowReadBytes`; ordinary media bytes remain SMB-owned and continuous fake
reads are prohibited.

For explicit SMB Direct, property negotiation behaves like forced Pull for
container selection while continuing to report the normal video/audio codecs.
It must never use `VIDEO_CODECS=NONE`, `AUDIO_CODECS=NONE`, or a fixed
`videocodec=NONE`/`audiocodec=NONE` request. Auto fallback changes only the byte
source after SageTV has selected ordinary Pull and records its reason.

The selected protocol library is [SMBJ](https://github.com/hierynomus/smbj),
an Apache-2.0 Java SMB2/SMB3 client. Its configuration explicitly detects
Android, excludes the desktop SPNEGO authenticator there, retains NTLM, and
supports SMB 2.0.2 through SMB 3.1.1. This matches the repository license and
avoids SMB1-only operation. The dependency remains isolated in
`android-shared`; pure path mapping and shadow-session protocol code remain in
`core` so they can be host-unit-tested without Android.

Physical Amazon AFTMM/API 25 testing against the isolated server proves both
Media3 and legacy ExoPlayer start, seek, FF/REW, large-jump, pause/resume,
repeat-start, stop/restart, and teardown using the generated deterministic
`VibeSeekTest-1080i-MPEG2-AC3-CC.ts` fixture. Real Meet the Press marker tests
prove server-owned Comskip in both directions. `OPEN + SIZE` and a byte-zero-
only probe were both measured and rejected because the server did not issue
the required seek.

The Pull/SMB A/B harness records the exact server seek-command time, the first
physical datasource read after that command, the first observed queued decoder
input, the exact rendered-first-frame callback, and sustained recovery. The
decoder-input timestamp is a bounded 100 ms polling observation; it is not
misrepresented as a direct MediaCodec callback. Datasources expose the last
physical read time and position separately from buffered reads, so a seek that
is satisfied entirely by read-ahead does not create false transport evidence.
Byte ownership must also agree: Pull requires positive MediaServer media bytes,
while SMB Direct requires positive SMB bytes and zero ordinary MediaServer
media bytes apart from the explicitly counted bounded shadow reads.

SageTV absolute time, backend-relative time, growing files, and Push rebasing
remain backend-specific. The shared `PlaybackSessionController` now assigns a
monotonic generation to each load and operation. Base, Media3, and legacy Exo
reject listener, progress, recovery, queued UI, and surface callbacks from a
replaced or stopped session without changing those backend timeline rules.
Reconnect keeps the active playback generation unless SageTV supplies a new
load; stop and free invalidate it.

## Compatible Media3 file replacement

Media3 can retain its player and Surface when a new load is proven safe by the
pure `MediaReplacementPolicy`. The policy accepts only initialized, ready,
completed, non-circular Pull or SMB Direct media. The implementation constructs
a new source/session, calls `setMediaSource(..., true)`, and releases the prior
datasource only after the replacement is installed. First rendered frame marks
success. A synchronous setup failure or asynchronous player error performs one
normal full-load fallback; it cannot loop back into fast replacement.

Push, Fixed/MIM, DVD, HTTP/external-link, live/growing, circular, and legacy
loads with unknown metadata deliberately remain full loads. Debug builds expose
bounded counters and target/reason state, and `mcp-fast-switch-test` supplies a
repeatable physical Pull/SMB gate without changing the production SageTV
control-session contract.

## Current behavior constraints

- Legacy Exo remains the default.
- Media3/Legacy Exo use custom MPEG-TS Pull extractor/load-control behavior.
- IJK retains device/codec-specific MPEG-2 compatibility handling.
- GSY Auto uses the SageTV-aware engine selected by current implementation;
  Android System remains an explicit compatibility path.
- Decoder policy stays separate from transport/timeline refactoring.
- Completed forced Pull/SMB sessions may negotiate command 30. Native forward
  rate is bounded to 0.5x-2x; 4x-256x forward/reverse uses a three-second
  seek-scan cadence so a hardware frame can render between discontinuities.
  STOP/free invalidates the controller, pause cancels its scheduled tick, and
  unsupported rates retain the prior accepted rate. Other transports and
  GSY/System do not advertise this capability.
- `BaseMediaPlayerImpl` remains protected by an explicit reviewed-hash gate;
  its authorized session-controller plus bounded DVD diagnostics integration
  SHA-256 is
  `63fc269f13e7e5a75f4e55b8e3b39016e9056ffc6c71baf117e6c8b596ebc9dd`.

## Refactor direction

First preserve media load metadata (`timeshifted`, buffer size, major/minor and
encoding hints), add measured live-edge/EOF seek protection, and expose existing
server/client buffer evidence. Only after behavior is characterized should
common runtime configuration, seek policy, recovery, and telemetry snapshots
move out of the engine classes. Session generation has moved into the shared
controller after matching Media3/legacy Exo hardware baselines and is now a
preserved invariant.

See `TASKS.md` for active work and `docs/PLAYBACK_DIAGNOSTICS.md` for the
required evidence standard.
