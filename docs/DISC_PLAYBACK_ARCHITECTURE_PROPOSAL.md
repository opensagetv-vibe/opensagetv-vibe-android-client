# Server-controlled DVD and Blu-ray playback proposal

Status: active. The Native Media3/hardware DVD path is physically commissioned;
Hybrid/MIM policies, old-component fallback, non-Media3 commissioning, and
physical Blu-ray remain incomplete. Blu-ray main-title support remains the existing SageTV path;
physical BDMV commissioning is SKIPPED because the supplied private share has
no lawful BDMV/ISO fixture. BD-J and full HDMV menu execution remain deferred.

## DVD implementation

- Supporting Android clients advertise `DVD_REMOTE_NAV=TRUE`; updated Core
  selects `MiniDVDPlayer` only for that negotiated remote client. Windows local
  DVD remains `DShowDVDPlayer`.
- Android replies to the complete legacy command family 32-37 and lazily opens
  one `push:dvd` player when DVD metadata/data arrives after `INIT`.
- Core normalizes disc roots and `VIDEO_TS`, preserves the Ogle Java VM, and
  starts the deterministic playable title for a menu-less DVD.
- Media3 uses a DVD-specific MPEG-PS extractor. It separates AC-3 private
  substreams 0x80-0x87, honors the DVD first-access-unit pointer, and rejects
  malformed sync candidates before they reach old hardware Dolby decoders.
- A from-beginning MCP request suppresses only the matching disc's saved resume
  point. Ordinary STV resume behavior is unchanged.
- Server CLUT/SPU metadata now drives bounded authored SPU assembly, strict
  control/RLE decoding, CLUT/`CHG_COLCON` composition, and the active authored
  button highlight. Remote navigation remains server-authoritative.

Physical Amazon AFTMM/API-25 development runs prove hardware MPEG-2/AC-3 on
all 51 populated Unraid DVD structures, expected safe failure of the one empty
structure, authored root/Languages/Special Features navigation, menu-less
ALADDIN, title/chapter/skip/FF/REW/pause/play, three AC-3 selections, STV-owned
subtitle state, fullscreen, five stop/restart cycles, and a six-disc strict
control matrix. ADB screenshots are useful only when the device compositor
includes the hardware video plane; they are not the sole A/V gate. Acceptance
therefore combines decoder input/output, rendered-frame, media-time,
surface-validity, process evidence, screenshots where representative, and an
independent HDMI-capture check.

## Goal and boundary

Add capability-negotiated DVD and Blu-ray playback for remote SageTV
MiniClients, including OpenSageTV Vibe. The SageTV server owns disc parsing,
navigation decisions, title/chapter/cell or clip state, menu state, stream
selection, and lifecycle. The Android client keeps the existing MiniPlayer
control session and owns decoding, rendering, playback clock, input forwarding,
and negotiated overlay rendering.

This must not replace or redirect the full/local Windows client. Local Windows
DVD playback must continue through `DShowDVDPlayer` and the DirectShow DVD
Navigator. Existing local Windows Blu-ray behavior must remain unchanged.

Initial scope:

- unencrypted `VIDEO_TS` directories and DVD ISO images;
- BDMV directories and Blu-ray ISO images;
- DVD main-title playback, navigation, SPU/subpicture, stills, and menus;
- menu-less or damaged-menu DVD folders, with deterministic main-title
  selection and ordinary playback controls instead of a hard failure;
- Blu-ray main-title/playlist playback and HDMV menus where practical;
- Push/Pull transport without requiring SMB Direct;
- Media3 and legacy ExoPlayer client coverage;
- graceful unsupported behavior for old clients and missing server support.

Out of initial scope:

- CSS/libdvdcss bundling or circumvention;
- AACS/BD+ decryption;
- BD-J, except detecting it and degrading cleanly;
- replacing Windows local-client disc playback;
- unrelated player tuning or FFmpeg/MIM changes;
- continuously server-rendered menu screenshots.

## Review findings that correct the original proposal

The feature must not start by assuming a new `SageDiscNav.dll` or
`libSageDiscNav.so` is required. Current SageTV already contains substantial
server-side DVD infrastructure:

- `java/sage/MiniDVDPlayer.java` implements the remote DVD player path;
- `java/sage/DVDReader.java` and `third_party/Ogle/java/sage/dvd/VM.java`
  implement DVD reading/navigation logic;
- `MiniDVDPlayer` handles ISO release through `FSManager`;
- `VideoFrame` distinguishes remote `MiniDVDPlayerIdentifier` behavior from
  the Windows local `DShowDVDPlayer` path;
- `BluRayStreamer` and the existing `MiniPlayer` Blu-ray logic already handle
  some playlist/clip behavior.

Discovery must compare three choices before native code is approved:

1. complete and modernize the existing Java/Ogle `MiniDVDPlayer` path;
2. wrap current system `libdvdnav`/`libdvdread`/`libbluray` libraries;
3. use a narrow hybrid where existing SageTV logic remains authoritative and
   a native library fills only a proven gap.

Any native-library proposal must include license, dynamic/static linkage,
Windows binary provenance, Ubuntu package availability, ABI, architecture,
and redistribution review. The build must not silently turn SageTV or the
container into an incompatible derivative, and optional decryption libraries
must not be bundled.

The existing MiniClient DVD media protocol is also broader than the original
draft stated. Current command IDs are:

| ID | Existing command |
|---:|---|
| 32 | `MEDIACMD_DVD_NEWCELL` |
| 33 | `MEDIACMD_DVD_CLUT` |
| 34 | `MEDIACMD_DVD_SPUCTRL` |
| 35 | `MEDIACMD_DVD_STC` |
| 36 | `MEDIACMD_DVD_STREAM` / client `MEDIACMD_DVD_STREAMS` |
| 37 | `MEDIACMD_DVD_FORMAT` |

The singular/plural name mismatch at command 36 is retained in documentation;
the wire ID and payload are the compatibility contract. The Android client now
implements and acknowledges commands 32–37, lazily creates the DVD Push player
without requiring OPENURL, and has executable protocol characterization. Its
SPU path uses the reviewed Apache-2.0 v2.1 presentation core for bounded
per-stream assembly, strict control-command/RLE decoding, `CHG_COLCON`, CLUT,
and highlight composition. The existing physically proven Media3 MPEG-PS,
cell-clock, Surface, and drain handling remains authoritative rather than being
replaced by the package's alternative end-to-end bridge.

Android `MediaCodecList` does not report deinterlacing quality. Disc acceptance
must therefore use rendered interlaced test media on real devices rather than
inferring support from codec enumeration.

### Kodi Android MediaCodec rule audit

The native DVD path was compared with Kodi's current Android MediaCodec
implementation rather than treating successful decoder creation as proof of
correct playback:

| Kodi behavior | Vibe mapping |
|---|---|
| MPEG-2 uses decode timestamps when presentation timestamps are absent; MediaTek OMX/Codec2 and NVIDIA OMX retain that behavior after intermittent valid PTS | `Mpeg2PictureTimestampCompleter` preserves authored PTS and reconstructs only missing telecined I/P/B display times from bounded sequence/GOP/picture metadata, `temporal_reference`, and `repeat_first_field`. Metadata remains in byte order with no payload buffering. `Auto` is limited to matching MediaTek/NVIDIA hardware; `On` and `Off` remain available for physical A/B testing. |
| Reject a decoder before configuration when dimensions are zero | Media3's H.262 reader withholds the `Format` until the sequence header supplies width and height, so MediaCodec is not created from the empty MiniPlayer URL hint. |
| Validate MIME, secure/tunneled requirements, codec profile, and capabilities before selection | Media3 and legacy Exo start from their library `MediaCodecSelector.DEFAULT` results and retain renderer-level format support checks. Vibe then applies only the selected Hardware/Software policy. |
| Allow explicit decoder filtering | The existing `disabled_<codec-name>` setting is now enforced consistently by Media3, legacy Exo, and IJK. Runtime observations never create automatic deny rules. |
| Disable tunneled playback for the independently scheduled Surface renderer | Media3 track selection explicitly sets tunneling off. SageTV audio, video, DVD SPU, and STV clocks therefore remain independently observable. |
| Wait for a SurfaceTexture frame callback before `updateTexImage()` in Kodi's EGL renderer | Not applicable: Vibe gives MediaCodec a direct `SurfaceView`; it does not call `updateTexImage()` or own Kodi's texture renderer. |
| Flush and reconfigure a codec after a fatal MediaCodec error | Media3 owns the codec lifecycle. Calling `prepare()` from its error/idle state recreates failed renderers/codecs; Vibe's bounded session retry does not invoke raw MediaCodec flush APIs. |
| Parse MPEG-2 sequence changes and update dimensions/aspect/frame rate | Each DVD Push epoch uses Media3's `H262Reader`; SageTV cell replacement reparses the sequence header and `onVideoSizeChanged` applies the resulting dimensions/aspect. |
| Wait for a decodable keyframe after a flush and drain output before end-of-stream | Already owned by Media3's `MediaCodecRenderer` and sample queues. Vibe must provide correct keyframe flags and the DVD drain marker, but must not duplicate MediaCodec's internal state machine. |
| Release decoded frames against the display clock and recreate timing state when a codec is replaced | Already owned by Media3's video renderer. Vibe records frame-release evidence but does not call raw `releaseOutputBufferAtTime()` or maintain a competing renderer clock. |

The non-DVD MPEG-4 compatibility rule has now been fixture-tested without
changing healthy production playback:

- MPEG-4 Part 2 can prefer DTS while presentation timestamps are initially
  missing. The generated Advanced Simple Profile/B-frame MP4 passes on the
  AFTMM hardware decoder. Its controlled fault plan selects eight packets whose
  container PTS and DTS genuinely differ, removes only PTS, retains DTS, and
  proves the expected DTS fallback. Media3 exposes only one timestamp at
  `TrackOutput`, so no global production rewrite was added and the DVD
  timestamp completer remains MPEG-2-specific.

The following Kodi rule remains deliberately unmapped because it is not a DVD
MPEG-2 rule and neither commissioned Fire TV advertises matching hardware:

- VC-1 hardware decoders sometimes advertise `video/VC1` or `video/vc1`
  instead of Android's `video/wvc1`. Test Media3's current MIME handling on a
  real VC-1 fixture before adding aliases because a selector cannot safely
  rewrite the stream `Format` by codec name alone.

The official Kodi sample catalog links three suitable real VC-1 inputs for
local interoperability testing: 1080p/23.976, 1080i/29.97, and a 1080p/23.976
VC-1 clip with DTS-HRA audio. Kodi claims fair use for non-copyleft clips used
for testing, technical evaluation, and documentation; that is not a
redistribution license. `scripts/remux_vc1_fixture.py` therefore records the
source URL, SHA-256, codec probe, usage note, and an explicit redistribution
classification. Kodi-derived media remains ignored local test data and must
not enter source, handoff, APK, or release archives. The commissioned
AFTMM/API-25 device advertises no VC-1 MediaCodec, so it can validate safe
unsupported-codec behavior but cannot produce a VC-1 hardware `PASS`.

Kodi also contains HDR/Dolby Vision profile, secure-decoder, and codec-specific
bitstream conversion branches. Those are format-specific playback features,
not DVD rules. Media3 currently owns their capability checks; any Vibe override
requires a legal fixture and physical display/decoder evidence.

On the AFTKRT/API-30 Fire TV Pro, the exact clean `0.5.85` APK used
`OMX.MTK.VIDEO.DECODER.MPEG2` and held 1.0217x real time during the final
20-second Aladdin Native/hardware measurement, with 756 video outputs and zero
dropped frames, non-positive release intervals, or long release gaps. The
matching AFTMM/API-25 run held 1.0131x real time with 1,019 outputs and zero
drops, skips, non-positive intervals, or gaps. Both devices recovered after
pause/play, 2x FF/RW, and next/previous chapter. Evidence is
`artifacts/firetv/dvd-firetv-pro-29-aladdin-final-clean.json` and
`artifacts/firetv/dvd-firetv-25-aladdin-final-clean.json`.

## Intended architecture, subject to discovery

```text
SageTV VideoFrame / MiniPlayer
        |
        +-- existing or modernized server DiscSession
        |       +-- DVD navigator/reader
        |       `-- Blu-ray playlist/navigation owner
        |
        +-- existing MiniPlayer media transport (Push/Pull)
        `-- capability-negotiated disc state/overlay commands
                         |
                  Android disc-aware session adapter
                         |
              existing Media3 or legacy Exo player
```

The client must not implement a DVD VM or Blu-ray navigator. It must not treat
`dvd:` as an ordinary file URL. It may open disc media only when both peers
have negotiated the exact required capability.

Animated menus use original menu video/audio through the media path plus
server-controlled overlay state. DVD SPU/button highlights and Blu-ray
Presentation/Interactive Graphics remain separate from the moving background.
Full-frame PNG/JPEG menu streaming is not an acceptable steady-state design.

Direction keys are routed to the disc navigator only while an interactive disc
menu owns navigation. During title playback, normal SageTV STV behavior stays
authoritative unless the STV issues a specific disc control.

## Implemented Native policy boundary

Core owns the Java/Ogle VM and Android owns presentation/decoding. Hybrid and
MIM-main-feature are capability-gated; until MIM advertises a commissioned
DISC capability they either fall back to Native or fail closed according to
the client preference. `Auto` never silently promotes experimental MIM. Native
DVD resolves to Media3 because a physical six-disc direct legacy-Exo matrix was
not reliable. Skip menus selects the longest authored VM title, whereas skip
previews enters the authored root menu and retains navigation.

## Discovery gate

Before implementation, produce a cross-repository architecture report that
maps and characterizes:

- `VideoFrame`, `DVDMediaPlayer`, `MiniDVDPlayer`, `DVDReader`, the Ogle VM,
  `DShowDVDPlayer`, `BluRayStreamer`, `MediaFile`, and `FSManager`;
- exact player selection on Windows local, Windows remote, and Linux remote;
- directory/ISO mounting and cleanup, including container permissions;
- all DVD control codes and MiniClient commands 32–37, including payloads,
  replies, timing, CLUT/SPU/STC/format semantics, and malformed inputs;
- current extender/Placeshifter capabilities and fallback behavior;
- current Blu-ray title, playlist, clip-transition, chapter, and seek logic;
- Android's lazy no-OPENURL DVD startup, complete commands 32-37 handling,
  overlay/rendering ownership, remote input, and Media3/legacy Exo transport
  behavior;
- applicable third-party licenses and whether dynamic system libraries are
  feasible on Ubuntu and Windows;
- legal, redistributable, unencrypted test fixtures. Test-media copyright and
  encryption status must be recorded.

The commissioned private integration fixture is available from Unraid at
`\\192.168.10.175\sagemedia\videos\Aidens_Movies\SCOOBY_DOO_AND_BATMAN\`.
It is test input only and must never be copied into the repository, an update
ZIP, or a public release. Discovery must also inventory sibling DVD folders
that lack a working menu and select representative menu-less fixtures without
hard-coding their names into production code.

The report must recommend retaining, replacing, or wrapping each existing
component. It must not make a new native wrapper the default merely for
architectural symmetry.

## Phased implementation after approval

### Phase A: protocol and lifecycle foundation

- Add explicit server/client capability negotiation using existing SageTV
  naming conventions.
- Characterize old-client fallback and preserve prior unsupported behavior.
- Introduce one server-owned `DiscSession` lifecycle with bounded close on
  STOP, client disconnect, parse failure, or ISO unmount.
- Add payload-free server/client telemetry without filesystem credentials.
- Keep the feature disabled by a development safety property until physical
  gates pass.

### Phase B: DVD main-title path

- Open unencrypted VIDEO_TS and ISO sources.
- When a DVD has no usable first-play/menu program chain, choose the main title
  deterministically (prefer the longest playable title, with explicit tie and
  parse-failure handling) and keep Play/Pause/Seek/Stop operational.
- Enumerate titles, chapters, cells, angles, audio, and subpictures.
- Play, pause, seek, stop/resume, title/chapter transition, audio/subtitle
  selection, still frames, and timestamp discontinuities.
- Complete Android handling for the existing command set before extending it.

### Phase C: DVD menus

- Implement SPU/subpicture, CLUT, button rectangles/highlights, stills, menu
  domain changes, animated menu video/audio, arrows, activate, and return.
- Prove nested and moving menus without full-frame screenshot streaming.

### Phase D: Blu-ray main-title path

- Reuse working `BluRayStreamer` playlist/clip behavior where possible.
- Support BDMV/ISO title and playlist selection, multi-M2TS transitions,
  chapters, seeking, audio/subtitles, angles where present, and cleanup.
- Do not require menus for this phase to pass.

### Phase E: Blu-ray HDMV menus

- Add popup/top menu, Interactive Graphics, Presentation Graphics, buttons,
  navigation, and moving video/audio underneath the overlay.
- Capability-negotiate every new payload that commands 32–37 cannot express.

### Separately approved phase: BD-J

Investigate Java/runtime, graphics callbacks, lifecycle, storage, network,
input, security, and distribution implications. Unsupported BD-J must not
block main-title or HDMV playback.

## Required telemetry

At minimum expose, without secrets:

- negotiated server/client disc capabilities and protocol version;
- active disc type/source type, session generation, native/Java backend, and
  last navigation command;
- DVD domain/title/chapter/cell/menu/still/button/angle/audio/subtitle state;
- NEWCELL, CLUT, SPUCTRL, STC, STREAM, and FORMAT command counts/errors;
- Blu-ray title/playlist/play-item/clip/chapter/menu/popup/audio/subtitle state;
- transition, discontinuity, overlay, malformed-command, and cleanup counters;
- media source, player backend, decoder, clock, first-frame, and error evidence.

## Acceptance gates

- Windows local DVD still instantiates `DShowDVDPlayer`, never the remote disc
  session; existing local Blu-ray behavior is unchanged.
- Windows and Linux remote DVD paths open, navigate, seek, change streams, and
  cleanly close through the selected server architecture.
- DVD static, animated/audio, still, nested, multi-title/chapter, subtitle,
  and angle fixtures pass where present.
- A DVD folder without a usable menu starts its deterministic main title and
  supports Play/Pause/Seek/Stop without inventing menu state.
- Windows and Linux remote Blu-ray paths play the correct playlist, transition
  clips without false EOS, seek across clips, change streams, and cleanly stop.
- HDMV moving menus retain video/audio while real overlays and selected buttons
  respond to remote input.
- Old clients, unsupported/malformed commands, absent libraries, parse errors,
  mount errors, and disconnects fail without crashing SageTV or affecting any
  other client.
- Ordinary recordings, Live TV, Pull, Push, SMB Direct, Comskip, captions,
  Media3, and legacy Exo regression gates remain green.
- BD-J status is explicit (`supported`, `partial`, or `deferred`) and never
  represented as passing without a physical test.

This proposal spans `opensagetv-vibe-core`, `opensagetv-vibe-build-env`, the
server container/release manifest, and this Android client. Implementation and
release ordering must be mirrored in the workspace-wide `task.md`.
