# Player, server, and FFmpeg/MIM compatibility

This is the durable compatibility matrix for the OpenSageTV Vibe Android
client. A row is `PASS` only when the exact combination was exercised on a
physical client and the evidence proved advancing output. Similar player
engines are not treated as equivalent. `SKIPPED` means the required hardware
was unavailable; `UNTESTED` is not a pass.

Current physical commissioning environment:

- Android client: Amazon AFTMM / Android API 25, Dev package only
- SageTV server: `sagetv-vibe-server-u26-gpu-j11`, Ubuntu 26.04 / Java 11
- server GPU: Intel i915 through `/dev/dri/renderD128`
- completed recording: `MeetthePress-65149351-0.ts`
- deterministic recording: `VibeSeekTest-1080i-MPEG2-AC3-CC.ts` with generated
  `.edl` intervals at 120-180, 360-420, and 660-720 seconds
- commissioned live channels: 2.1 and 5.1 only

The final September 7 recovery pass used only the commissioned non-Pro client.
All four selectable backends passed Vibe-server transitions
2.1 -> 5.1 -> 2.1 with hardware video. Stock Core cannot acknowledge the
disabled-by-default Vibe channel-selection test event, so its compatibility
gate uses the normal Live TV command and verifies SageTV's current channel;
Media3 and legacy Exo both produced advancing hardware A/V. This distinction
keeps ordinary stock playback separate from Vibe-only deterministic test
control.

## Feature intent and test-only controls

The tables below use these intent labels. A feature being used during physical
commissioning does not by itself make that feature test-only.

| Label | Meaning |
|---|---|
| **Normal feature** | User-facing playback, settings, compatibility, or recovery behavior intended for ordinary builds and daily use. |
| **Diagnostic** | Optional troubleshooting information or an administrator query. It may be useful in normal builds, but playback never depends on it. |
| **TEST ONLY** | Deterministic automation or fault-injection control. It is disabled by default, restricted to the Dev/debug workflow, or both. Users do not need it for normal SageTV operation. |
| **REGRESSION TEST MEDIA** | Generated media, captions, Comskip markers, or authored discs used to reproduce the same test conditions. These are test inputs, not application features, and are never applied to real recordings or live TV. |

The following items are specifically **TEST ONLY** or **REGRESSION TEST MEDIA**:

| Item | Classification | Production boundary |
|---|---|---|
| Exact server-file commissioning event | **TEST ONLY** | Opens a known file deterministically for automation. Normal users select recordings through the STV; the event is disabled by default in Vibe Core. |
| Exact dotted-channel event and positive acknowledgment | **TEST ONLY** | Restricts repeatable live testing to an explicitly requested channel. Normal users tune through the STV; the event is disabled by default. |
| Exact server-owned DVD seek event | **TEST ONLY** | Creates a reproducible DVD seek without replacing ordinary STV timeline, chapter, or remote commands. |
| Android Dev MCP commands, debug broadcast receiver, health probes, screenshots, and automated assertions | **TEST ONLY** | Present for commissioning the Dev/debug APK. They are not playback requirements and must not be exposed as production remote-control interfaces. |
| MCP watched/resume reset | **TEST ONLY** | Clears one explicitly identified MediaFile's complete SageTV Watched record to recreate a never-watched startup. It requires a second call with `confirm=true` because it changes server watch history. |
| MCP `codec_capabilities` snapshot and test-state counters | **TEST ONLY** | On-demand evidence for the selected decoder and current test. No background probe or server protocol dependency is added. |
| Deliberate missing/old-MIM, decoder-failure, network-failure, and malformed-input injection | **TEST ONLY** | Exercises bounded fallback and error handling. It is never enabled during ordinary playback. |
| `VibeSeekTest-1080i-MPEG2-AC3-CC.ts`, its generated `.edl`, timestamp captions, and synthetic live source | **REGRESSION TEST MEDIA** | Deterministic A/V, caption, seek, and Comskip evidence. The generator never modifies a real recording or real ATSC caption stream. |
| Generated authored-DVD test disc and its timestamp subtitles/chapters/menus | **REGRESSION TEST MEDIA** | Exercises DVD presentation and navigation. It is separate from users' DVD media. |

The active-player statistics overlay and MIM `--mim-status` query are
**Diagnostic**, not test-only. Push, Pull, SMB Direct, Fixed/MIM playback,
native and Hybrid DVD playback, caption handling, safe seek, Home recovery,
decoder selection, and their user settings are **Normal features**, even when
the fixtures and MCP controls above are used to validate them.

## What does not work with an unmodified SageTV server

The Vibe client remains usable with stock SageTV. Native DVD playback is not a
Vibe-Core-only feature: stock Core already contains `MiniDVDPlayer` and its
server-side DVD VM. Vibe advertises the original extender-style
`INPUT_DEVICES=IR,KEYBOARD,TV` response (not `MOUSE`), so stock Core selects
that original path. On September 6, 2026, unmodified server
`192.168.10.175` physically passed menu-less Aladdin playback and the authored
Scooby root-menu/navigation path with Media3 hardware MPEG-2 decode, AC-3,
CLUT/SPU/highlight commands, pause/play, and zero dropped video frames. The
authenticated Web/Sagex plugin was used only to invoke the ordinary STV
`Watch` operation; no Vibe protocol event, patched `Sage.jar`, or MIM was used.

The following optional Vibe features are unavailable on stock Core:

| Feature unavailable on stock Core | Required component | Safe stock behavior |
|---|---|---|
| Exact server-file commissioning event (**TEST ONLY**) | Vibe `Sage.jar` | Select the item in the STV, or use the standard Web/Sagex `Watch` command. Normal playback is unaffected. |
| Exact dotted-channel commissioning event and positive acknowledgment (**TEST ONLY**) | Vibe `Sage.jar` | Tune through the STV or standard Sage commands. |
| Per-client Native/Hybrid/MIM-main-feature DVD transport negotiation | Vibe `Sage.jar`; MIM for transformed media | Stock Core uses its original native `MiniDVDPlayer` MPEG-PS push path. |
| Client-requested DVD Skip Menus and Skip Previews | Vibe `Sage.jar` | Stock Core follows authored DVD navigation. The user can select the main feature normally. |
| Explicit Hybrid-to-Native DVD fallback reason and MIM-failure marker | Vibe `Sage.jar` and MIM | Select Native DVD. Stock native playback does not need this fallback. |
| Debug/MCP exact server-owned DVD seek event (**TEST ONLY**) | Vibe `Sage.jar` | Normal STV DVD chapter/timeline/navigation commands remain server-owned; only the private deterministic test event is missing. |
| `MEDIA_STATE_URL` completed/growing URL metadata | Vibe `Sage.jar` | The client retains normal MiniPlayer behavior and uses the older available hints; diagnostics are less explicit. |
| Native playback-rate negotiation through `VIBE_PLAYBACK_RATE` | Vibe `Sage.jar` | Historical SageTV FF/REW/skip commands remain available; the Vibe native-rate extension is not advertised. |

The following depend on FFmpeg/MIM rather than `Sage.jar` and therefore do not
work when MIM is absent:

| Feature unavailable without MIM | Safe stock behavior |
|---|---|
| Modern Ubuntu FFmpeg option/codec translation | Stock Fixed transcoding works only if the server's bundled FFmpeg accepts the historical request. Push, Pull, SMB Direct, and native DVD remain available. |
| Intel VAAPI/QSV, AMD VAAPI, or NVIDIA NVENC policy and verified fallback | Use client hardware decode with Push/Pull/native DVD, or whatever transcoder the stock server already supports. |
| Machine-readable active transcoder/backend status (**Diagnostic**) | Playback can still run, but Vibe cannot prove which server encoder is active. |
| Fixed-output CEA-608/708 retention through transcoding | Use non-transcoded Push/Pull/SMB playback for the standard legacy caption callback path. |
| Hybrid DVD or MIM main-feature transform | Use stock native DVD playback. Interactive DVD menus cannot be preserved by a simple transcoded main-title stream. |

These are limitations rather than connection gates. A missing extension must be
ignored or fail with a bounded explanation; it must never break normal stock
Push, Pull, native DVD, STV, or remote-control behavior.

### SageMC startup progress display

SageMC 169 can briefly draw a completed-file progress marker at the file end
when a never-watched recording is opened, then correct it to zero as playback
starts. A synchronized physical capture on stock server `.175` showed
`1:22:00` against a `1:02:00` airing before returning to `0:00:00`. Android's
bounded startup trace returned exactly `0` for every `GETMEDIATIME` reply; no
EOS, end seek, or duration-sized value was sent. The same APK does not show the
flash with the stock SageTV7 STV. This is therefore a cosmetic SageMC OSD
initialization race, not player seeking or failed resume behavior. The optional
**Wait for playback before first OSD** client setting suppresses that one stale
presentation without changing the reported clock: it releases when playback
actually presents its first video frame or after a hard five-second failure
timeout. It is enforced at the render boundary, re-arms after every successful
MiniPlayer load when an STV retains the same playback OSD, and has no ongoing
per-frame cost. Tests can recreate a genuinely untouched item
with MCP `dev_reset_media_watch_state(media_file_id, confirm=true)`, which uses
standard `ClearWatched` through Sagex or Nielm's stock Web Interface.

## Historical extender / desktop behavior audit

This audit uses the released SageTV Core, Windows player contracts, Java
PlaceShifter and wire protocol as primary evidence. The Linux MiniClient is not
used as a DVD correctness reference: Android, Linux and Java PlaceShifter never
had complete authored-DVD playback. The proprietary hardware-extender firmware
was not released, so firmware-internal buffering, decoder and rendering details
remain inference even when the server contains explicit extender workarounds.

| Behavior | Evidence classification | Reusable Android result | Status |
|---|---|---|---|
| Capability negotiation | Proven protocol: `MiniClientSageRenderer` queries codec/container, input, aspect, subtitle, frame-step, reconnect, detailed-buffer and buffer-limit properties before constructing `MiniPlayer`. | Advertise only implemented behavior; leave unknown extensions empty and preserve old-client defaults. | Implemented and old/new Core matrix tested. |
| Growing/live and circular files | Proven player contract: every `MediaPlayer.load`/`fastLoad` carries major/minor type, encoding hint, `timeshifted`, and circular `bufferSize`; `MiniPlayer` waits for source growth instead of emitting premature EOS. | Preserve the complete media context and apply growing/live seek bounds and bounded SIZE retry. | Implemented; prerecorded and live gates pass. |
| Buffering and adaptive Push | Proven wire behavior: detailed Push adds channel, stream and target bandwidth plus server mux time; `MiniPlayer` uses returned free space/play state and contains explicit low-bandwidth/transcode decisions. | Decode the complete detailed reply and expose server/client/datasource rates and wait time through MCP. Do not invent Android-side server rate control. | Implemented and physically observed. |
| Seek/skip near live edge | Proven desktop behavior: `VideoFrame` keeps skip/FF behind the live edge using encoder-delay-aware margins and prevents unsafe completed-file EOF seeks. | Shared `PlaybackSeekPolicy`, exact requested/clamped telemetry, serialized seek recovery. | Implemented and physically tested across Pull, Push, SMB and Fixed. |
| Frame stepping | Proven command/property contract (`FRAME_STEP`, media command 28); backend mechanics are platform-specific. | Advertise only for a player/streaming combination that can apply a bounded paused step. | Implemented behind capability policy; physical release gate remains separate where applicable. |
| Captions/subtitles | Proven protocol: legacy hardware extenders advertised `GFX_SUBTITLES`, returned raw decoder-extracted caption packets through callback type 225, and let SageTV select/render CC1/CC2/DTVCC. Stock `MiniPlayer.setClosedCaptioningState()` returns `false` because it does not need to publish state to that callback producer. | Media3, legacy Exo, and GSY extractor delegates now tap raw TS CEA samples, convert them to the proven eight-byte SageTV records, and send event 225. Local text rendering is suppressed only after callback packets are active. IJK advertises `FALSE` and retains explicit client fallback. Extractor fragments are cleared on seeks, duplicate parallel-track samples are suppressed, and MPEG-2 caption packets are delivered in PTS rather than decode order. | PASS on stock `.175`: Media3 and legacy Exo hardware Pull each pass Off/CC1/CC2/Off/CC1 and FF/REW/FF2/REW2 with ordered lower-screen captions within the two-second fixture timing gate. |
| Aspect and interlace | Proven capabilities: server queries supported/source/advanced aspect and deinterlace-control properties; Windows provides Source/Stretch/Zoom and explicit deinterlace choices. | Keep Source/Stretch/Zoom under STV control. Observe H.262 sequence/picture headers and report progressive/interlaced/telecine/field-picture state, but explicitly report that Android exposes no selectable desktop deinterlacer. | Implemented; 1080i Media3/legacy-Exo Pull and native DVD physically report interlaced sequence/frame state with hardware MTK MPEG-2. Deinterlace quality remains hardware-specific. |
| Standby/reconnect | Proven protocol: server announces `RECONNECT_SUPPORTED`; Core has a forced-media-reconnect branch. Extender firmware recovery timing is unknown. | Generation-owned connection/session state, selectable Home behavior, bounded resume, optional timeout disconnect, and old behavior opt-out. | Implemented and physically tested on API 25; API 26+ background limit gate remains external. |
| Remote input | Proven protocol: `INPUT_DEVICES` differentiates IR/keyboard/mouse/touch/TV and server events own STV actions. | Advertise TV without falsely implying mouse/desktop extender identity; switch arrow semantics only while the server DVD VM is in a menu. | Implemented and authored-menu tested. |
| Fast/seamless switching | Proven desktop contract: `canFastLoad`/`fastLoad` retains a player only after compatibility checks. Firmware decoder reuse details are unknown. | Media3 retains its player/Surface only for completed random-access Pull/SMB files. Push, live/growing, circular, HTTP, external-link, and DVD loads fail closed to normal replacement; error or an eight-second no-frame timeout gets exactly one full-player fallback. | Implemented and physically passed distinct-file Pull/SMB switches with hardware MPEG-2 on AFTMM/API-25. |
| Audio decode and passthrough | Proven protocol: codec playback support and extender `AUDIO_OUTPUTS`/HBR selection are different capabilities. Android MediaCodec availability does not prove encoded HDMI output, and a stream MIME does not prove the active AudioSink mode. | Inventory platform audio decoders and connected encoded-sink formats separately; negotiate a codec only when it is decodable or accepted by the sink. Do not advertise extender HBR output modes. | AC3/EAC3 direct Pull and DTS exclusion physically passed on AFTMM/API-25; passthrough remains unadvertised. |
| DVD navigation | Proven server behavior: `MiniDVDPlayer` plus Java/Ogle VM owns title/cell/menu state and sends DVD metadata commands with pushed MPEG-PS. Windows `DShowDVDPlayer` supplies the user-visible semantic reference. | Preserve server VM/session authority and port presentation to Android Media3; never infer correctness from the old Linux client. | Native commissioned; opt-in Hybrid/MIM and explicit MIM main-feature authored-fixture gates pass with negotiated fallback. |
| Server-specific extender workarounds | Proven only where branches and comments exist (for example pause/flush behavior and media-extender mute restoration); the reason inside closed firmware is inference. | Apply a workaround only when Android reproduces the behavior, behind a focused test and capability gate. | No unproven firmware workaround was copied globally. |

### Legacy-extender branches rechecked

Android already follows the server's legacy-extender path because its dynamic
`INPUT_DEVICES` reply contains `TV`, `IR`, and `KEYBOARD`, but not `MOUSE`.
`MiniClientSageRenderer.isMediaExtender()` classifies every no-mouse client as
an extender. The following consequences were rechecked against current Core;
they are not inferred firmware behavior:

| Server branch | Android disposition |
|---|---|
| MPEG-2 High-Level support marks the connection as an HD extender | Retained. It enables compatible native MPEG-2 Push/DVD behavior and existing seek/time guards. Codec advertisement remains device-derived. |
| Push/Pull/transcode selection uses codec/container properties | Retained. Android advertises only its selected mode and actual platform capabilities; Fixed remains an explicit request. |
| Detailed Push reply and buffer limit alter server fill/pause behavior | Retained. Detailed fields are parsed and exposed to MCP. An absent `PUSH_BUFFER_LIMIT` deliberately uses Core's bounded 32 KiB default. |
| `FRAME_STEP` changes paused Push behavior because old HD firmware resumed after flush | Capability-gated. Android advertises it only for implemented random-access modes, never unsupported Push/Fixed paths. |
| Extender video plane disables desktop color bars; GFX scaling/no-transform choices alter clipping/caching | Retained through the existing `GFX_SCALING=HARDWARE`, no-transform, video-update, and no-mouse capability combination. OpenGL/GDX physical overlay gates cover it. |
| Image-allocation failures on a dead extender connection fail the frame instead of continuing | Implemented with a bounded client recovery: evict one disposable LRU UI image, retry once, and rethrow a terminal OOM. Surfaces are excluded; closed connections receive no unload callback; MCP reports all four recovery counters. |
| Forced reconnect, auth cache, offline image cache, and compressed GFX transport | Retained only where implemented and independently tested. |
| HD-extender reverse near the PTS rollover has a server-side media-time clamp | Left server-owned. Android does not add a competing clock correction. |
| Audio-output/HBR selection, HDMI mode switching, RC5, remote filesystem, screenshots, advanced deinterlace, YUV unified cache, and 3D transforms | Not advertised. Android must not inherit firmware-specific behavior merely by being classified as an extender. Existing player/OS controls remain separate. |
| `GFX_SUBTITLES` and event 225 | Newly implemented for extractor-backed players using raw CEA packets. IJK returns `FALSE`; decoded cue text is never mislabeled as a legacy packet. |

No other firmware-only branch is safe to enable without a physical
reproduction. New compiled-firmware observations must remain labeled as
inference until matched to protocol/server source or a focused physical test.

## Server dependency classes

| Capability | Unmodified SageTV server | Vibe `Sage.jar` | FFmpeg/MIM | Notes |
|---|---:|---:|---:|---|
| Normal MiniClient discovery/connect/UI | Yes | No | No | Standard MiniClient protocol. |
| Push/Dynamic playback | Yes | No | No | Server-controlled MiniPlayer push path. |
| Pull playback | Yes | No | No | Standard SageTV MediaServer path. |
| Fixed transcoding protocol | Yes | No | Optional | The protocol is historical; the tested Ubuntu 26 modern FFmpeg path uses MIM. |
| Ubuntu 26 modern Fixed codec mapping | No | No | Yes | Maps obsolete requests such as `libfaac` and selects modern encoders/mux options. |
| Intel QSV / AMD VAAPI / NVIDIA NVENC selection and fallback | No | No | Yes | Intel VAAPI is commissioned. Intel QSV is reproducibly unstable on this host; AMD/NVIDIA remain SKIPPED without hardware. |
| Fixed-output CEA-608/708 retention | Not established | No | Yes | PASS with MIM 0.4.7 `-a53cc 1`, software MPEG-2 decode, Intel VAAPI H.264 encode, and the safe 512 KiB/500 ms warm-probe policy. |
| SageTV STV caption-state authority | Yes on extractor-backed players | Optional | No | Standard `GFX_SUBTITLES` + event 225 works with stock SageTV. `VIDEO_CC_STATE` remains an optional compatibility extension; IJK uses explicit client fallback. |
| Exact server-file automation event (**TEST ONLY**) | No | Yes | No | Opt-in test control; normal UI playback does not require it. |
| Exact dotted-channel automation event and positive channel acknowledgment (**TEST ONLY**) | No | Yes | No | Opt-in test control; normal STV tuning does not require it. |
| MIM active-backend status query (**Diagnostic**) | No | No | Yes | `ffmpeg --mim-status` returns machine-readable job status. |

The Vibe-only exact-file/channel events are commissioning controls. They are
disabled unless the server administrator enables the corresponding properties;
they are not required for ordinary playback against a stock SageTV server.

## Compatibility boundary

The Android client remains a standard SageTV MiniClient first. Selecting a
player, decoder policy, Push/Pull mode, caption service, or ordinary SageTV UI
command does not require a modified server. The Vibe `Sage.jar` adds opt-in,
positive-acknowledgment controls for repeatable automation; it does not replace
the normal STV playback path. FFmpeg/MIM is server-side and is consulted only
when SageTV starts its FFmpeg transcoder.

| Feature or parameter | Android-only / stock server | Vibe `Sage.jar` needed | MIM needed | Current status |
|---|---:|---:|---:|---|
| Player selection and Android decoder policy | Yes | No | No | Implemented; each engine still needs its own physical matrix row. |
| Standard discovery, connection, UI and remote commands | Yes | No | No | PASS against the isolated Vibe server; protocol remains stock-compatible. |
| `majorHint`, `minorHint`, `encodingHint`, `timeshifted`, and circular `bufferSize` supplied by MiniPlayer load | Yes | No | No | Preserved in `PlaybackMediaContext`; used for growing/completed classification and diagnostics. |
| Push/Dynamic and MediaServer Pull transport | Yes | No | No | Legacy Exo and Media3 PASS on the commissioned client. |
| Fixed-stream request parameters (`FIXED_PUSH_MEDIA_FORMAT`) | Yes | No | No for protocol | Stock protocol. A stock server can use its own compatible transcoder; Ubuntu 26 FFmpeg compatibility and GPU selection require MIM. |
| Modern Ubuntu 26 FFmpeg option/codec translation | No | No | Yes | Implemented and unit/integration tested. |
| Intel/AMD/NVIDIA server GPU backend selection and deterministic fallback | No | No | Yes | Intel VAAPI commissioned; deliberate device failure selected reported software fallback. AMD/NVIDIA remain SKIPPED. |
| Machine-readable active transcoder/backend query (**Diagnostic**) | No | No | Yes | Implemented as `ffmpeg --mim-status`; matrix rejects stale jobs and silent fallback. |
| Normal STV file selection and channel tuning | Yes | No | No | Standard behavior. |
| Direct exact server-file automation (**TEST ONLY**) | No | Yes | No | Implemented as a disabled-by-default private commissioning event. |
| Exact dotted-channel automation with positive acknowledgment (**TEST ONLY**) | No | Yes | No | Implemented as a disabled-by-default private commissioning event. |
| Detailed Push buffer/bandwidth telemetry | Yes | No | No | Restored from the standard detailed Push reply and exposed in the Dev MCP state. |
| STV-controlled captions in Push/Pull | Yes on Media3/legacy Exo/GSY | Optional | No | The standard extender path returns encoded CEA data with event 225 for SageTV/STV rendering. `VIDEO_CC_STATE` is retained for compatibility. IJK cannot provide raw samples and uses explicit `Off`/`CC1`/`CC2` fallback. |
| Caption retention through Fixed transcoding | No | No | Yes | PASS for the tested MPEG-2/A53 to H.264/VAAPI path using software decode plus VAAPI encode. |
| Completed/growing safe-seek policy in Android | Yes | No | No | Implemented client-side; physical regression remains part of every player row. |
| Completed MPEG-TS seek preroll after Fixed FF/REW | No | No | Optional | Disabled by default because its hidden five-second input seek shifted the visible clock and captions. Exact SageTV seek time is preserved; preroll remains an explicit compatibility opt-in only. |
| SMB Direct / Shadow Pull datasource | Yes | No | No | PASS on Media3 and legacy ExoPlayer. It retains stock MiniPlayer control, sources media from SMB2/3, and uses only bounded one-byte same-position shadow reads on non-sequential accesses so stock SageTV emits STV-owned seeks/Comskip. |
| Seamless/fast file switching | Potentially | No | No | NOT IMPLEMENTED. Do not infer from normal load/reload success. |
| Frame-step command | Yes for random-access Pull/SMB | No | No | Implemented for paused Media3 and legacy Exo with bounded one-frame seek; physical MPEG-2 Pull gates pass. Push/Fixed and unsupported engines do not advertise it. |
| Preferred language and full CEA-608/708 service selection | Yes | No | Fixed retention only | Implemented client-side for Media3 and legacy Exo2. Explicit CEA-608 CC1 and CEA-708 Service 1 physically pass hardware Pull while the STV retains caption On/Off authority. |
| Native remote DVD with authored menus | Yes | No | No | Stock Core's original `MiniDVDPlayer` path physically passes menu-less and authored-menu DVDs on `.175`; Vibe Core adds policy/automation controls, not the native decoder requirement. |
| DISC Hybrid / MIM main feature | No | Yes | Yes | Opt-in authored-fixture title/control and explicit MIM-main-feature gates pass. `Auto`/allowed fallback restores Native on absent/old/failed MIM; explicit unavailable/no-fallback fails closed. MIM remains disabled by default pending its separate promotion decision. |
| New client with preserved pre-DISC Core | Standard modes yes | Optional DISC limitation | Fixed only | Pull, SMB Direct, Fixed/MIM, Native DISC and Hybrid-to-Native pass. Old Core cannot query the per-client DISC policy, so Android reports the limitation and retains Native. |
| Missing MIM/FFmpeg in explicit Fixed | No automatic explicit-mode substitution | No | Missing | Startup is rejected without an active Android player; SageTV stays healthy and the next ordinary hardware Pull passes. |
| Preserved MIM 0.4.5 with current client/Core | Normal modes yes | No | Incompatible for commissioned Fixed request | Fixed startup fails safely; it is not mislabeled as current MIM support. MIM 0.4.7 is restored and checksum-verified afterward. |

An original, unmodified SageTV server should ignore the absence of the two Vibe
commissioning events because the client never sends them during normal use.
Conversely, enabling the events on a Vibe server does not alter how stock
MiniClients tune or open media.

## Android player and transport parameters

These are stable preference values, not display labels. Compatibility reports
must record the stored value so results are reproducible.

| Parameter | Allowed values | Sent to / affects | Stock-server compatibility |
|---|---|---|---|
| `default_player` | `exoplayer`, `media3`, `ijkplayer`, `gsyplayer` | Selects the Android player implementation. | Fully client-side. |
| `streaming_mode` | `dynamic`, `pull`, `smb_direct`, `smb_auto`, `fixed` | Changes advertised Push/Pull capabilities and Fixed format properties. SMB modes negotiate ordinary Pull and replace only the byte source. | Uses existing MiniClient property negotiation; SMB Direct itself needs no Vibe server extension. |
| `smb_direct/mappings` | Multiple `SageTV prefix => smb://server/share/root/` lines | Longest-prefix path mapping, including Windows paths and practical UNC conversion. | Client-side. Credentials are separate and redacted. |
| `smb_direct/read_ahead_kb` | 32 through 8192 (commissioned: 1024) | Sequential SMB read-ahead; random extractor probes remain bounded to 64 KiB minimum. | Client-side. |
| `decoding_method` | `hardware`, `software`, `hardware_preferred` | Selects Android video decoders; fallback is allowed only for `hardware_preferred`. | Fully client-side. It does not select the server GPU. |
| MCP `codec_capabilities` snapshot (**TEST ONLY**) | On-demand decoder inventory and selected-decoder evidence | Reports platform hardware/software classification, MIME/profile/dimension/features, queueing mode, and current player health without background instrumentation. | Debug builds only; no server or wire-protocol change. |
| `preferred_audio_language` / `preferred_subtitle_language` | empty/auto or a BCP-47 language tag | Applies language preference through the selected Exo track selector; missing-language tracks remain safe fallbacks. | Fully client-side. |
| `preferred_caption_standard` / `preferred_caption_service` | `auto`, `cea608` CC1-CC4, or `cea708` Service 1-63 | Resolves the preferred broadcast-caption track only after SageTV/STV enables captions. | Fully client-side; it is not a second caption enable gate. |
| `legacy_server_caption_mode` | `stv`, `off`, `cc1`, `cc2` | Fallback available in Audio and Caption settings and the MiniClient long-press CC submenu. Negotiated event-225 rendering or received `VIDEO_CC_STATE` wins. | `stv` works on stock SageTV with Media3/legacy Exo/GSY; explicit modes remain for IJK or failed negotiation. |
| `gsy_player_engine` | `auto`, `media3`, `system`, `legacy_exo` | Selects the delegate only when `default_player=gsyplayer`. | Fully client-side. The resolved delegate must be recorded. |
| `media3_codec_mode` / `exo2_codec_mode` | `auto`, `async`, `sync` | Selects MediaCodec queueing behavior for the corresponding engine. | Fully client-side; device/API dependent. |
| `exoplayer_ffmpeg_extension` | `0` off, `1` if needed, `2` preferred | Controls the legacy Exo FFmpeg audio extension. | Fully client-side and does not refer to server MIM. |
| `fixed_encoding/preference` | `needed`, `always` | Controls whether compatible source codecs may bypass encode. | Existing `VIDEO_CODECS`, `AUDIO_CODECS`, and container negotiation. |
| `fixed_encoding/format` | `matroska`, `dvd`, `mpegts` | `FIXED_PUSH_MEDIA_FORMAT.container`. | Existing protocol; current MIM commissioning uses `mpegts`. |
| `fixed_encoding/video_bitrate_kbps` | 1000 through 10000 | `videobitrate` in the Fixed format request. | Existing protocol. |
| `fixed_encoding/video_fps` | `SOURCE`, `24`, `29.97`, `59.94` | `fps` and calculated GOP in the Fixed format request. | Existing protocol. |
| `fixed_encoding/key_frame_interval` | positive seconds | Multiplied by explicit FPS to form the requested GOP. | Existing protocol; MIM caps unsafe legacy GOP values. |
| `fixed_encoding/use_b_frames` | Boolean | Adds the historical `bframes` request. | Existing protocol; MIM defaults to zero B-frames for live Android safety. |
| `fixed_encoding/video_resolution` | `SOURCE`, `CIF`, `D1`, `720`, `1080` | Requested Fixed output size. | Existing protocol. |
| `fixed_encoding/audio_codec` | `aac`, `ac3`, `mp2` | Requested wire audio codec. | Existing protocol; MIM maps removed `libfaac` implementation requests to native `aac`. |
| `fixed_encoding/audio_bitrate_kbps` | `96`, `112`, `128`, `160`, `192` | Requested Fixed audio bitrate. | Existing protocol. |
| `fixed_encoding/audio_channels` | source/empty, `1`, `2`, `6` | Requested channel count. | Existing protocol. |
| `fixed_remuxing/preference` | `needed`, `always`, `off` | Controls Fixed stream-copy/remux eligibility. | Existing protocol on supporting SageTV releases. |
| `fixed_remuxing/format` | `matroska`, `dvd`, `mpegts` | `FIXED_PUSH_REMUX_FORMAT.container`. | Existing protocol on supporting SageTV releases. |

"Hardware" exists at two independent layers: `decoding_method=hardware` means
the Android device must use MediaCodec/native hardware decoding; MIM
`backend=qsv|vaapi|nvenc` means the server must hardware-encode the Fixed
stream. A test passes only when both requested layers are verified separately.

## Physical player matrix

The Fixed rows below use Intel VAAPI (`h264_vaapi`) on the server and hardware
decode on the Fire TV unless the row says otherwise. Historical detailed rows
include four exact 2.1/5.1 changes; the final all-engine 0.4.7 hardware matrix
uses two bounded exact changes per selection. GSY System currently resolves
to the safe Media3 delegate; it is not evidence for native Android
`MediaPlayer` compatibility.

| Player selection | Resolved engine / Android decode | Push/Dynamic | Pull | Fixed prerecorded | Fixed live 2.1/5.1 | Captions | Current result |
|---|---|---:|---:|---:|---:|---:|---|
| Legacy ExoPlayer | ExoPlayer 2.18.1 / hardware | PASS | PASS | PASS | PASS | PASS | VAAPI start, pause/play, FF, REW, jump, restart, teardown, and four live changes pass. |
| Legacy ExoPlayer | ExoPlayer 2.18.1 / software | PASS | PASS | PASS | PASS | PASS on captioned fixture | Forced `OMX.google.h264.decoder` passes the complete generated prerecorded Fixed controls/restarts; synthetic captions are separately marked skipped because that fixture has no cue text. |
| Legacy ExoPlayer | ExoPlayer 2.18.1 / fallback | PASS | PASS | PASS | PASS | PASS | Capability profile orders hardware before software; commissioned hardware Pull selected `OMX.MTK.VIDEO.DECODER.MPEG2`. |
| Media3 | Media3 / hardware | PASS | PASS | PASS | PASS | PASS | VAAPI full prerecorded and live matrix passes. |
| Media3 | Media3 / software | PASS | PASS | PASS | PASS | PASS | Android software decode passes with server VAAPI encode. |
| Media3 | Media3 / fallback | PASS | PASS | PASS | PASS | PASS | Capability profile orders hardware before software; commissioned hardware Pull selected `OMX.MTK.VIDEO.DECODER.MPEG2`. |
| IJK | IJK 0.8.8 / hardware | Baseline available | UNTESTED in this release | PASS | PASS | SKIPPED unsupported | After two retained historical failures, exact MIM 0.4.6 passed generated prerecorded controls and four live changes in three consecutive runs. |
| GSY Auto | Media3 delegate / hardware | Baseline available | Baseline available | PASS | PASS | PASS | One caption-timing miss was followed by a strict two-start PASS; selected delegate is recorded. |
| GSY Media3 | Media3 delegate / hardware | Baseline available | Baseline available | PASS | PASS | PASS | Physically tested independently from direct Media3. |
| GSY Legacy Exo | Exo delegate / hardware | Baseline available | Baseline available | PASS | PASS | PASS | Physically tested independently from direct Exo. |
| GSY System | Media3 safe fallback / hardware | Limited baseline | Limited live Pull A/V | PASS | PASS | PASS through delegate | Bounded last run passed, but this is deliberately a Media3 fallback rather than native System playback. |

The software and hardware-preferred rows above are retained as historical
evidence. Current commissioning is hardware-decoder-only by owner direction.

The IJK blocking-read contract correction is retained because returning a
temporary zero-length read is invalid for the native datasource. The two old
fourth-channel failures remain useful regression evidence, but three
consecutive exact-0.4.6 runs now pass that boundary. Intel VAAPI Fixed is
supported as an opt-in configuration. MIM remains disabled by default; AMD and
NVIDIA require their own physical commissioning.

## Fixed-stream Android parameters

| Setting | Commissioned value | Compatibility purpose |
|---|---|---|
| Streaming method | `fixed` | Requests SageTV's transcoded MiniPlayer push path. |
| Fixed encoding preference | `always` | Ensures the Fixed/MIM path is actually exercised. |
| Output container | `mpegts` | Provides predictable live/growing behavior and caption carriage. |
| Audio codec | `aac` | Supported by Android; MIM translates obsolete SageTV `libfaac` requests to native FFmpeg `aac`. |
| Fixed remuxing | `off` | Prevents the remux preference from bypassing the encode case under test. |
| Android decoding | `hardware` | Requires a hardware video decoder on the Android device; audio may remain software-decoded. |
| MIM backend | `auto` on Intel host | Resolves to stable `vaapi`; a normal hardware case fails closed on silent software fallback. |
| MIM active-file decode | software | Preserves MPEG-2 A/53 captions and avoids unstable growing-file QSV decode. |
| MIM active-file encode | VAAPI | Performs H.264 video filtering/encode on Intel GPU. |

## Required evidence for a PASS

Every Fixed hardware row must retain:

1. Android state proving the expected backend class, advancing video frames,
   advancing audio output, a valid/shown surface, and no player/process error.
2. Caption discovery and non-empty cue progression where the engine supports
   CEA captions.
3. MIM status/log evidence naming the expected backend and encoder; on the
   Intel test host this is `backend=vaapi`, `encoder=h264_vaapi`, and
   `hardwareEncode=true`.
4. Server process evidence showing no orphaned MIM or `ffmpeg.real` process
   after stop/teardown.
5. Separate fallback evidence. A forced bad hardware device must select and
   report software; that result must not be confused with the normal hardware
   PASS.

AMD VAAPI and NVIDIA NVENC remain `SKIPPED` until matching physical GPUs are
commissioned. Compiler availability or an FFmpeg encoder listing alone is not
runtime compatibility evidence.

## Physical evidence retained

- Same-APK preserved/current Core matrix:
  `old-server-exoplayer-pull-preview.json`,
  `old-server-exoplayer-smb-preview.json`,
  `old-server-exoplayer-fixed-preview.json`,
  `old-server-disc-native.json`,
  `old-server-disc-hybrid-native-fallback.json`,
  `old-server-disc-hybrid-safe-native-warning-final2.json`, and
  `current-server-disc-hybrid-no-fallback-safe-failure.json`.
- Missing/old MIM fault injection and recovery:
  `compat-missing-mim-fixed-result.json`,
  `compat-missing-mim-pull-pass.json`, and
  `compat-old-mim-045-fixed.json`. A failed explicit Fixed result is retained
  as negative evidence and is not labeled PASS.
- Embedded-preview Surface/video-rectangle matrix:
  `embedded-preview-exoplayer-push.json`,
  `embedded-preview-exoplayer-pull.json`,
  `embedded-preview-exoplayer-smb.json`,
  `embedded-preview-exoplayer-fixed.json`, and
  `embedded-preview-media3-disc-native-settled.json`, with corresponding PNG
  screenshots under `artifacts/firetv`.

- SMB Direct generated-fixture full session matrices:
  `artifacts/firetv/smb-media3-generated-post-seekprobe.log` and
  `artifacts/firetv/smb-exoplayer-generated-post-seekprobe.log`
- Controlled Pull/SMB source and first-read A/B:
  `artifacts/firetv/smb-pull-ab-generated-firstread.json`
- Generated-fixture large-jump Pull/SMB timing and byte-ownership A/B on both
  Exo pipelines:
  `artifacts/firetv/generated-pull-smb-seek-ab-exoplayer.log` and
  `artifacts/firetv/generated-pull-smb-seek-ab-media3.log`
- MediaServer non-NIO/NIO comparison (two physical repeats per setting):
  `artifacts/firetv/generated-pull-smb-nio-false*.log` and
  `artifacts/firetv/generated-pull-smb-nio-true*.log`
- Media3 Push Auto/Sync queueing repeats:
  `artifacts/firetv/generated-push-codec-repeat1.json` through
  `generated-push-codec-repeat3.json`
- Post-restart MIM 0.4.6 persistence, Intel VAAPI Fixed controls, stable
  fullscreen, and teardown:
  `artifacts/firetv/fixed-mim-20260830_132155/FIXED_MIM_MATRIX.json` and
  `artifacts/firetv/fullscreen-regression-20260830/passive-grace-stable-2.png`
- Exact MIM 0.4.7 seven-selection hardware-only matrix with fresh Intel
  VAAPI/`h264_vaapi` jobs and zero orphans:
  `artifacts/firetv/fixed-mim-20260830_180555/FIXED_MIM_MATRIX.json`
- Row-14 caption/timeline evidence after the safe MIM warm-probe correction:
  `artifacts/firetv/20260830-190054_caption-media3-fixed-visible.png` and
  `artifacts/firetv/20260830-190557_caption-media3-push-visible.png`. The stable
  middle CEA-608 cue is within one second of the visible STV timeline and does
  not cover it.
- Repeated Fixed/MIM FF, REW, and FF_2 caption-retention/sync evidence:
  `artifacts/firetv/20260830-191129_caption-media3-fixed-visible.png`. Real A/V
  recovered in 328-331 ms per command; final cue/media-clock cadence drift was
  4 ms and the visible stable cue remained within two seconds of the STV time.
- Repeated Push FF, REW, and FF_2 evidence:
  `artifacts/firetv/20260830-191636_caption-media3-push-visible.png`. Push uses a
  3,000 ms replacement-stream settle so the second server FLUSH is not judged
  before new bytes arrive; stable cue 3:37.0 versus STV 3:38 and final cadence
  drift 4 ms pass.
- Physical SMB profile repository/UI acceptance:
  `artifacts/firetv/smb-profile-physical-acceptance.log`,
  `artifacts/firetv/smb-profile-settings-ui-save-load.png`,
  `artifacts/firetv/smb-profile-import-settings-confirmation.png`, and
  `artifacts/firetv/smb-profile-client-id-final-confirmation.png`
- Real marker endpoints (`863.860`-`985.220` s), strict server-seek and A/V
  recovery evidence:
  `artifacts/firetv/smb-media3-meetpress-comskip-serverposition.log` and
  `artifacts/firetv/smb-exoplayer-meetpress-comskip-left-local-seekprobe.log`
- Deterministic generated-marker commissioning at the 120-180 second interval
  now passes Media3 and legacy ExoPlayer over both Pull and SMB Direct. Every
  run observed the exact SageTV-owned 180000 ms request and sustained
  fullscreen hardware-decoded A/V. First read/first frame deltas were 625/799
  ms (Media3 SMB), 539/728 ms (legacy Exo SMB), 911/1112 ms (Media3 Pull), and
  2483/2560 ms (legacy Exo Pull).
- Explicit SMB failure, Auto fallback, valid mapping restore, sustained
  playback, and STOP cleanup:
  `artifacts/firetv/smb-explicit-invalid-mapping.log`,
  `artifacts/firetv/smb-auto-invalid-mapping-fallback.log`,
  `artifacts/firetv/smb-valid-mapping-restored.log`, and the
  `smb-*-generated-sustained60.log` files
- Legacy Exo hardware VAAPI PASS:
  `artifacts/firetv/fixed-mim-20260829_215258/FIXED_MIM_MATRIX.json`
- Media3 hardware VAAPI PASS:
  `artifacts/firetv/fixed-mim-20260829_215815/FIXED_MIM_MATRIX.json`
- Final exact FFmpeg/MIM 0.4.6 Media3 hardware VAAPI PASS with fresh-job,
  matching-input, stopped-state, and zero-orphan telemetry:
  `artifacts/firetv/fixed-mim-20260829_234208/FIXED_MIM_MATRIX.json`
- Exact 0.4.6 generated-fixture legacy Exo software PASS:
  `artifacts/firetv/fixed-mim-20260830_085632/FIXED_MIM_MATRIX.json`
- Three consecutive exact 0.4.6 IJK prerecorded/four-change-live PASS reports:
  `fixed-mim-20260830_085919`, `fixed-mim-20260830_090300`, and
  `fixed-mim-20260830_090610`
- IJK fourth-channel failures (two reproductions):
  `fixed-mim-20260829_220147` and `fixed-mim-20260829_223414`
- GSY delegate commissioning and strict Auto repeat:
  `fixed-mim-20260829_222324`, `fixed-mim-20260829_222857`, and
  `fixed-mim-20260829_223107`
- Android software/fallback decoder matrix:
  `artifacts/firetv/fixed-mim-20260829_225837/FIXED_MIM_MATRIX.json`
- Deliberately unavailable Intel device to reported server software fallback:
  `artifacts/firetv/fixed-mim-20260829_231222/FIXED_MIM_MATRIX.json`

The fallback case temporarily pointed MIM at a nonexistent render device. MIM
reported `backend=software`, `encoder=libx264`, and
`hardwareEncode=false`; Media3 passed prerecorded and live gates. Both live
configuration copies were then restored to `/dev/dri/renderD128`, and a final
dry run again selected `backend=vaapi` with `h264_vaapi`.

Intel QSV remains an experimental option but is not the commissioned default:
direct physical runs reproducibly exited with signal/return code 139. The
stable default order is `vaapi,qsv,nvenc,software`, with hardware decode off so
A/53 caption data survives the MPEG-2 input path.
