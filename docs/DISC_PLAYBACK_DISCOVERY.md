# Disc playback discovery and compatibility boundary

This document records the executable discovery behind OpenSageTV Vibe remote
DVD/Blu-ray playback. It is an implementation reference, not a copy of private
media or a promise that Java-authored Blu-ray menus are supported.

## Existing SageTV paths

- Windows local DVD playback remains `DShowDVDPlayer`; this project does not
  redirect or alter it.
- SageTV's remote authored-DVD path is `MiniDVDPlayer` plus the Java Ogle
  `sage.dvd.VM`. The server reads IFO/VOB/NAV data, owns navigation state, and
  pushes MPEG program-stream bytes to the MiniClient.
- `MiniDVDPlayer` starts the media socket with `MEDIACMD_INIT` and does **not**
  send `MEDIACMD_OPENURL`. It then uses Push, Play/Pause/Stop/Flush and DVD
  commands 32-37.
- `MiniPlayer` contains the existing Blu-ray main-title/playlist path using
  `BluRayParser` and `BluRayStreamer`. It is not a complete BD-J/HDMV menu VM.
- The native Linux MiniClient contains handlers for the DVD command numbers in
  `native/elf/newminiclient/mediacmd.c`, but it is not evidence of a complete or
  correct Linux DVD implementation and is not used as the Android presentation
  reference. The historical Java desktop MiniClient delegates `dvd:` playback
  to Ogle/MPlayer and likewise is not the Android design. Proven server protocol
  behavior and the Windows `DShowDVDPlayer` user-visible semantics are the
  comparison sources; firmware-only extender behavior is labeled as inference.

## Wire contract

Every DVD metadata command receives a four-byte reply:

| Command | Value | Payload |
|---|---:|---|
| new cell | 32 | `size:int`, then bounded bytes (current server sends a 32-bit PTS offset) |
| CLUT | 33 | `size:int`, then bounded palette bytes |
| SPU/highlight | 34 | `size:int`, then bounded highlight bytes; current server sends five big-endian integers |
| STC | 35 | one 32-bit value |
| stream | 36 | stream type and stream index |
| display format | 37 | one 32-bit value |

An absent reply can block the server until its media-socket timeout. Android
therefore validates payload lengths and replies even if a backend cannot apply
optional metadata.

## Selected architecture

SageTV remains the disc/session authority. A supporting Android client
advertises `DVD_REMOTE_NAV=TRUE`. Updated Core uses `MiniDVDPlayer` for that
client even when it also advertises mouse input; legacy extender selection and
Windows local playback remain unchanged.

Android defers player creation after `INIT`. If the server sends `OPENURL`, the
ordinary Push/Pull/Fixed/SMB path remains authoritative. If DVD metadata or a
Push buffer arrives first, Android creates one backend-neutral `push:dvd`
player and consumes the server's MPEG bytes. This distinction avoids changing
the established normal-media handshake.

The authored menu background/audio is part of the MPEG stream. Android
assembles and decodes the authored DVD SPU, applies the server CLUT and
`CHG_COLCON` regions, and composites the server-selected button palette over
that bitmap. Remote direction/Select/DVD commands continue through the
ordinary SageTV event channel so the server VM remains authoritative.

Blu-ray initially uses SageTV's existing main-title/playlist streamer and the
ordinary Android MPEG-TS player. BD-J and full HDMV Java menu execution are not
implemented by the existing Core and are explicitly separate from main-title
playback.

## Private fixture inventory

Read-only inspection on 2026-08-30 found these DVD structures below the private
Unraid test tree. No media is copied into source, artifacts, manifests, or
release packages.

| Fixture | Files | Bytes | Root menu VOB | Title VOBs | Purpose |
|---|---:|---:|---|---:|---|
| `ALADDIN` | 11 | 6,030,127,031 | absent | 6 | valid menu-less main-title fallback |
| `LEGO_SD_BLOWOUT_BEACH_BASH` | 19 | 6,620,924,161 | present | 8 | authored menu |
| `LEGO_SD_HAUNTED_HOLLYWOOD` | 23 | 7,156,358,934 | present | 10 | authored menu |
| `SCOOBY_DOO_AND_BATMAN` | 22 | 5,949,045,692 | present | 9 | primary authored-menu fixture |

The primary UNC source is
`\\192.168.10.175\sagemedia\videos\Aidens_Movies\SCOOBY_DOO_AND_BATMAN`.
Inside the commissioned server container, the same share is mounted at
`/var/media`; fixture commissioning must use the indexed server path rather
than exposing SMB credentials to logs.

A recursive read-only scan on 2026-08-31 found 52 `VIDEO_TS` directories and
no `BDMV` directory. One entry (`KID_MOVIES_M/ROGUE_ONE`) has an empty
`VIDEO_TS` directory and is an intentional invalid/empty-structure failure
case, not a playable-disc PASS. The other 51 range from two IFO/four VOB files
to five IFO/fifteen VOB files and cover menu-less, simple authored-menu, and
multi-title layouts. Automated commissioning must perform a bounded startup/
STOP/crash pass over all indexed structures, then run the full navigation,
stream, subtitle, chapter, and teardown matrix on representative distinct
layouts. Blu-ray physical commissioning is therefore SKIPPED until a lawful
private BDMV/ISO fixture is supplied or generated; host parser and ordinary
MPEG-TS regressions are still required.

The read-only inventory groups are: four under `Aidens_Movies`, three under
`KID_CARTOON_PL`, one empty structure under `KID_MOVIES_M`, 29 under `NewDVD`,
and 15 under `__UNWATCHED`. Private media remains excluded from source,
artifacts, manifests, and release packages.

The executable inventory used by the private commissioning run is retained as
`artifacts/firetv/unraid-dvd-playable-paths.txt`; it contains server-visible
paths only and no SMB credentials. The invalid empty structure is isolated in
`artifacts/firetv/unraid-dvd-invalid-paths.txt` so it is checked for safe
failure rather than mislabeled as a playable title.

## Commissioning result

The later Fire TV Pro investigation isolated the remaining cadence fault below
the DVD VM and menu layer. DVD PES packets did not author a PTS for every
MPEG-2 picture, so Media3 extrapolated missing timestamps in decode order. The
MediaTek hardware decoder reordered I/P/B pictures into display order and
therefore received an irregular/non-monotonic presentation schedule. The
delivered completer preserves authored values and derives only missing
telecined display times from bounded MPEG-2 GOP/picture metadata. It commits
metadata immediately in byte order and never buffers media payloads. Strict
native-hardware cadence plus pause/play, 2x FF/RW, and chapter recovery pass on
the exact clean `0.5.85` APK on both AFTKRT/API-30 and AFTMM/API-25. The final
20-second cadence ratios were 1.0217x and 1.0131x respectively, with zero
dropped frames, non-positive release intervals, or long gaps. The authored DVD
menu, audio, subtitle, and chapter transitions also pass on AFTKRT.

The 2026-08-31 Amazon AFTMM/API-25 hardware run proved both fixture classes
through the real `MiniDVDPlayer` session. `ALADDIN` started at the beginning
without a resume prompt and advanced with hardware
`OMX.MTK.VIDEO.DECODER.MPEG2` plus hardware `OMX.dolby.ac3.decoder`; pause/play,
STOP, exact restart, fullscreen, and crash checks passed. The authored Scooby
fixture advanced A/V, accepted remote navigation, issued server-owned cell and
flush transitions, and recovered without replacing the MiniClient connection.

The post-integration physical evidence also includes a 15-second independent
HDMI capture at 1920x1080 and approximately 29.75 fps with stereo audio. Its
decoded frame shows the authored Scooby root menu and the active Play button
aligned with the disc artwork. The smaller H.264/AAC evidence copy is
`artifacts/firetv/dvd-presentation-v21-hdmi-av-15s.mp4`; the extracted frame is
`artifacts/firetv/dvd-presentation-v21-hdmi-frame.png`. This complements rather
than replaces decoder, protocol, crash, and teardown telemetry.

The menu-less failure found during commissioning was not invalid media. Stock
Media3 `PsExtractor` combined every DVD private-stream-1 AC-3 substream into a
single track and accepted false `0B77` candidates. In addition, the client had
tested the normalized `stv://server/push:dvd` URI only with
`startsWith("push:dvd")`, bypassing its DVD extractor. The delivered path uses
the preserved original protocol URI, separates substreams, aligns using the
DVD first-access-unit pointer, and validates AC-3 headers before emitting a
sample. Hardware A/V then passed with hundreds of queued/rendered audio access
units instead of one fatal access unit.

The expanded inventory exposed a second extractor edge case. Some video-first
cells carry SPU packets in `private_stream_1` well before their first physical
AC-3 substream. Treating the `0xBD` PES id itself as audio finalized Media3's
TrackGroups without audio even though the later AC-3 reader continued parsing
valid samples. Discovery now marks audio only when a `0x80..0x87` substream
actually creates a track, and checks its byte limit after consuming the current
PES. `RAYA_AND_THE_LAST_DRAGON`, `SOUL`, and `F9_UPD75` reproduce the old fault
and pass after the correction.

The final bounded sweep after that correction started, rendered hardware A/V,
stopped, and checked crash state for all 51 populated DVD structures. Evidence
is `artifacts/firetv/dvd-presentation-v21-all-unraid-dvds-final.json`. The
single empty ROGUE_ONE structure is separately retained as an expected safe
failure in `artifacts/firetv/dvd-empty-structure-safe-failure.json`.

The exact latest installed APK was swept again after the old/new Core and
missing/old/current MIM fault matrices. All 51 populated structures passed in
`dvd-all-unraid-final-latest-apk-current-core.json`; the empty structure failed
safely in `dvd-empty-structure-final-latest-apk-current-core.json`. A fresh
independent HDMI capture (`dvd-final-current-apk-current-core-hdmi-18s.avi`)
contains 1920x1080 moving video and non-silent stereo 44.1 kHz PCM. Bounded
analysis found 29 unique sampled frames, mean audio -22.5 dB and maximum audio
-3.7 dB; the extracted frame visibly shows clean feature playback.

Strict command recovery then exposed a server boundary bug: Skip Back near the
start passed a negative time through `VideoFrame.timeSelected()` to the
Java/Ogle DVD VM, producing a negative target sector and a flushed session with
no replacement bytes. Vibe Core clamps disc time to zero before invoking
`MiniDVDPlayer.seek()`. The former failure and the complete FF/REW/chapter
sequence now pass real A/V counter recovery. Five stop/restart cycles and a
six-disc representative chapter/control matrix pass under
`dvd-scooby-five-cycle-stop-restart-soak.json` and
`dvd-representative-six-strict-controls.json`.

The final independent HDMI capture is the reproducible AVI produced by
`capture_hdmi_validation.cmd`. It contains 1920x1080 H.264 and stereo
44.1 kHz PCM; the extracted frame shows clean Scooby title playback without a
stale authored overlay. These files are
`dvd-presentation-v21-final-hdmi-av-18s.avi`, its `.ffprobe.json`, and
`dvd-presentation-v21-final-hdmi-frame.png` under `artifacts/firetv`.

## Policy and backend commissioning

Android exposes Native, Hybrid, and MIM-main-feature requests, while Core
queries the exact client properties before choosing the server path. Because
MIM is still disabled and does not advertise a commissioned DISC capability,
Hybrid with native fallback physically returns to Native; Hybrid with fallback
disabled refuses startup safely. Skip menus uses the longest authored title
measured by the Ogle VM, while skip previews jumps to the authored root menu.
These are distinct operations and both were confirmed by physical screenshots.
Direct legacy Exo DVD startup passed only two of six representative structures,
so the production resolver routes DVD to the commissioned Media3 implementation
instead of risking a hardware decoder initialization failure.

## DVD Presentation Engine v2.1 merge audit

The complete `SageTV-Android-DVD-Presentation-Engine-v2.1` handoff was reviewed,
including every Markdown file, source file, test, build script, protocol note,
and license. Its own self-test reports 19 passing tests and its Android source
compiles in the unified build environment. The archive is Apache-2.0 licensed.
The merge was deliberately performed component by component instead of replacing
the physically commissioned Android transport and player path wholesale.

| Handoff component | Vibe disposition | Verification / reason |
|---|---|---|
| `DvdArgbFrame`, `DvdAudioStreamCode`, `DvdButtonPalette`, `DvdByteUtil`, `DvdColorContrastTable`, `DvdDiagnostics`, `DvdHighlight`, `DvdProtocolException`, `DvdPtsClock`, `DvdSpuAssembler`, `DvdSpuCompositor`, `DvdSpuDecoder`, `DvdSpuFrame`, `DvdSpuPacket`, `MpegPsUtil`, `NibbleReader`, `YuvPalette` | Integrated | Sixteen selected source files are byte-identical to the reviewed handoff except for a trailing newline where applicable; `DvdPtsClock` is byte-identical. Host tests plus the physical 51-disc matrix exercise strict bounds, RLE, palette, highlight, audio-stream and PTS behavior. |
| Strict DVD media commands and four-byte replies | Retain Vibe `MediaCmd` implementation | Already generation-scoped and physically proven against `MiniDVDPlayer`; replacing it with a second bridge would risk blocking the server media socket. |
| `MpegPsPrivateStreamParser` | Retain `DvdPsExtractor` equivalent | Vibe additionally separates physical AC-3 substreams, honors first-access-unit pointers, rejects false sync words, and delays TrackGroup completion until real audio exists. These fixes were required by the private six-disc and 51-disc matrices. |
| `DvdPresentationEngine` / stream selection | Merge parsing primitives; retain Vibe presentation authority | Vibe couples decoded SPU state to SageTV/STV subtitle checkbox, language and `None` selection. The handoff's independent forced-subtitle policy would override that proven authority and was therefore not imported. |
| `DvdTimestampRewriter` | Retain Vibe observe/offset/rebase policy | Payload timestamp rewriting was unnecessary after physical A/V sync and title/cell transition gates passed, and would create another clock authority beside SageTV and Media3. |
| input/drain gate | Retain generation-scoped `PushBufferDataSource` and command `0x100` handling | All populated discs pass startup, STOP and repeated teardown without stale packets. The current owner also covers ordinary Push playback. |
| overlay geometry / Android Surface adapter | Retain Vibe `DvdHighlightOverlay` and player-owned `SurfaceView` | The overlay uses the actual video-view rectangle and a full transparent canvas; authored fullscreen/windowed menus and highlight movement were physically verified. A second Surface owner previously caused lifecycle and embedded-preview failures. |
| diagnostics and malformed-input containment | Integrated into existing telemetry/MCP surface | Malformed packets are counted and dropped without crashing the MiniClient; compact evidence keeps stream, SPU, overlay, decoder and compatibility state together. |

This comparison was repeated against the current working tree after the initial
merge, after the subtitle-authority fixes, and after the old/new Core binary
matrix. No handoff source or documented requirement was silently omitted: every
component is either integrated above or has an explicit, tested reason for
retaining the existing Vibe implementation.

## Old/new Core compatibility result

The same Android APK was commissioned against the preserved pre-DISC Core JAR
and the current negotiated Core JAR. With the old JAR, ordinary Pull, SMB
Direct, Fixed/MIM, Native DISC and Hybrid-with-fallback all retained working
playback. An old Core that globally forces its historical DVD path cannot query
the new per-client Hybrid policy; Android now reports that limitation and
retains safe Native playback instead of claiming Hybrid was selected. With the
current JAR, explicit Hybrid plus disabled fallback fails closed before media
startup, while Auto/fallback returns to Native. The current server JAR was
restored after the matrix and its SHA-256 is
`3ac9592dcfbf10cd65a72f91334ea663a979afcb153d1b1f8deaab2829b818bc`.

## Menu-less fallback

A DVD folder with `VIDEO_TS.IFO` and title VOBs remains a DVD even when
`VIDEO_TS.VOB` is absent. The first attempt uses the existing Ogle VM, whose
first-play/title logic preserves authored navigation when available. If VM
startup cannot enter a playable program chain, fallback selects a bounded main
title rather than treating the folder as an arbitrary collection of VOB files.
The selection policy must prefer the longest playable title and report ties or
complete failure; it must not concatenate filesystem order blindly.

## Compatibility

- New Android with old Core can use the historical MiniDVD path when that Core
  already classifies it as an extender; otherwise the negotiated selection
  requires the updated `Sage.jar`.
- New Core with old MiniClients receives an empty unknown capability and keeps
  its previous selection behavior.
- Normal recordings, Live TV, Push, Pull, Fixed, SMB Direct, captions, and
  Windows `DShowDVDPlayer` do not consume this capability.
