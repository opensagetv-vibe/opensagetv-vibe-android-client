# Decoder stability source audit

This is an implementation-source audit, not a player shootout. Kodi, VLC, MX
Player, and the old MiniClient are never launched by Vibe automation. Physical
playback validation runs only in OpenSageTV Vibe after this audit identifies a
specific, independently implemented correction. FFmpeg/FFprobe may inspect a
fixture, but is not launched as a competing UI player.

MX Player is proprietary and is not a source-level reference. A user report
that a file works there establishes only that the device and media are capable.
No implementation rule is inferred or copied from MX Player.

## Source snapshots and license boundary

| Project | Reviewed revision | Principal paths | License consequence |
| --- | --- | --- | --- |
| Kodi | `b08930bb0056b235e3b45c80113046721896694b` (2026-09-05) | `xbmc/cores/VideoPlayer/DVDDemuxers/DVDDemuxFFmpeg.cpp`, `DVDCodecs/Video/DVDVideoCodecAndroidMediaCodec.cpp`, `DVDCodecs/Audio/DVD*AudioCodec*.cpp`, `VideoPlayerVideo.cpp`, `VideoPlayerAudio.cpp`, `DVDClock.cpp`, `VideoReferenceClock.cpp`, `DVDInputStreams/DVDInputStreamNavigator.cpp`, `VideoPlayerTeletext.cpp`, and overlay codecs | GPL-2.0-or-later: behavior may inform an independent design; source is not copied into Apache-licensed Vibe. |
| VLC | `0a544554996ae900813ba92c70cd8c9408062497` (2026-09-12) | `modules/codec/omxil/mediacodec.c`, `modules/audio_output/android/audiotrack.c`, `modules/demux/mpeg/{ts,ts_pes,ts_hotfixes,ps}.c`, `modules/access/dvdnav.c`, `modules/codec/{dvbsub,telx,cea708}.c`, and `modules/demux/mkv/*` | VLC contains GPL and LGPL components. Findings are behavioral specifications only; no source is copied. |
| AndroidX Media3 | 1.11.0 dependency used by Vibe | `MediaCodecRenderer`, `MediaCodecVideoRenderer`, `DefaultExtractorsFactory`, `TsExtractor`, `TsBinarySearchSeeker`, `H262Reader`, audio sink/renderers | Apache-2.0 library behavior should be used directly rather than recreated in Vibe. |
| Legacy ExoPlayer | 2.18.1 dependency used by Vibe | corresponding MediaCodec renderer, TS extractor/seeker, H.262 reader, and audio sink | Apache-2.0 library behavior should be used directly rather than recreated in Vibe. |

The reference checkouts live outside the project and are not shipped. Exact
paths and revisions above make the audit repeatable without adding GPL source
to the repository.

## Disposition legend

- **Handled**: the pinned player library or existing Vibe code already owns it.
- **Implement**: a bounded Vibe gap exists and has an independent correction.
- **Telemetry/test**: evidence is insufficient for a behavior change; measure it.
- **Reject**: inappropriate, redundant, disproven, or too broad.
- **Separate feature**: valid work, but not a decoder-stability patch.

## Complete findings

### Container, demux, growing files, and seeking

| Finding | Kodi/VLC behavior | Vibe disposition |
| --- | --- | --- |
| MPEG-TS program identity and changes | Kodi scopes streams to the selected program and preserves stream-change generations. VLC tracks PAT/PMT programs and selected PIDs independently. | **Handled/test.** Media3/Exo own PAT/PMT and track-group changes. Keep the existing live/file-transition matrix and record track identities before/after a transition; do not add a second TS demuxer. |
| TS discontinuities and corrupt/duplicate packets | VLC explicitly checks continuity, resets affected PES state, handles PCR discontinuity/wrap, and can derive a clock candidate from timestamps when PCR is unusable. Kodi delegates demux to FFmpeg and resets player queues on discontinuity/stream change. | **Telemetry/test.** Media3/Exo already reset TS payload readers and timestamp adjusters on seek. Add fixture assertions and expose transition/error class; do not port VLC's TS demux into Vibe. |
| PCR-guided TS seek | VLC owns a probe/binary-search seeker; Kodi/FFmpeg use FFmpeg indexes/probes. Media3 and legacy Exo use `TsBinarySearchSeeker`. | **Handled.** Vibe already expands the Media3/Exo PCR search window and retains a bounded exact-byte cache. Physical 8/16 MiB and search-multiplier tests rejected guessed byte positions and a larger cache. `DefaultExtractorsFactory.setConstantBitrateSeekingEnabled(true)` does not enable TS constant-bitrate seeking; TS still uses PCR search. |
| Failed seek target | VLC restores the initial byte position if its time search cannot produce a valid landing. Kodi discards preroll until the requested time. | **Handled/test.** Media3/Exo preserve the last valid player position and Vibe's recovery reattaches the Pull source at the captured position. Retain landing-error checks in Test Current Video. |
| Growing/live source EOF | Kodi treats realtime/growing input differently from terminal EOF and handles FFmpeg `EAGAIN`. VLC distinguishes live demux timing from fixed duration. | **Handled.** Vibe's growing-source policy, dynamic MediaServer length, bounded EOF retry, program-transition recovery, and position-preserving reattach already cover this boundary. |
| Matroska cues and missing/late duration | VLC loads cues/seek heads, builds fallback cluster seek points, prerolls, and derives duration when necessary; Kodi delegates to FFmpeg. | **Handled.** Media3/legacy Exo Matroska extractors own cues/preroll. Stock-server MKV source length and seek corrections are below the player and must remain server-independent. Do not repair user files or add a second Matroska demuxer. |
| Missing PAT/PMT repair | VLC can synthesize tables as an optional demux hotfix. | **Reject until fixture evidence.** Adding PAT/PMT synthesis above Media3 would duplicate a demuxer and can select the wrong program. A failing original sample plus packet-level proof is required first. |

### Timestamps, clock, cadence, interlace, and display

| Finding | Kodi/VLC behavior | Vibe disposition |
| --- | --- | --- |
| Missing video PTS | Kodi uses DTS as PTS for MPEG-2/MPEG-4 paths until valid PTS appears. VLC pairs decoder output with an input timestamp FIFO and substitutes DTS when the oldest input lacked PTS. | **Handled.** Media3/Exo `H262Reader` extrapolates missing PES timestamps. Vibe additionally has the physically proven, decoder-family-bounded native-DVD MPEG-2 repair. Keep it family/stream based, not device-model based. |
| MPEG-2 sequence changes | Kodi parses sequence headers during input and refreshes size, aspect, and frame rate. VLC accepts MediaCodec output-format changes and repairs MPEG display aspect. | **Handled/test.** Media3/Exo H.262 readers emit format changes and their renderer reconfigures. Retain format-change telemetry and generated sequence-change fixture; do not duplicate sequence parsing outside the custom DVD extractor. |
| Frame cadence and refresh matching | Kodi owns a PTS tracker/pullup model, render clock, vblank clock, dropped-frame policy, and optional clock-speed adjustment. VLC owns its clock and video output scheduling. | **Handled/reject duplication.** Media3 1.11 owns frame-release estimation/scheduling and Vibe already has bounded display-refresh matching. Never port another master clock or release scheduler above Media3. Change behavior only from physical dropped/repeated-frame evidence. |
| Interlace/deinterlace | Kodi/VLC can select renderer/deinterlacer paths unavailable through the same Android `Surface` contract. | **Telemetry/test.** Vibe reports scan type, decoder, cadence, and display rate. Android hardware decoder output remains authoritative; do not pretend a player-side deinterlacer exists. |
| Playback rate, FF/RW, pause | Kodi changes clock and queue policy by rate; VLC separates demux/decoder/output rate handling. | **Handled.** Vibe's shared SageTV rate/seek policies, player-native rate support, frame-step fallback, pause state, and server-owned Push position remain authoritative. |

### Android MediaCodec and Surface lifecycle

| Finding | Kodi/VLC behavior | Vibe disposition |
| --- | --- | --- |
| Ordered decoder initialization | Kodi iterates compatible codec candidates. Media3/Exo iterate their ordered list only when decoder fallback is enabled. | **Implement D2.** Vibe already filters candidates by Hardware/Software/Fallback and user exclusions. Enable library fallback after filtering for every mode, so strict Hardware can try another hardware codec without crossing into software. |
| Decoder discovery/profile checks | Kodi filters by codec/profile and configurable decoder rules. VLC refuses unusable zero-size configuration and lets later packetizer metadata restart decoding. | **Handled/test.** Media3/Exo query profile/level/secure/tunneling support. Vibe applies capability-family and explicit user exclusions. Record candidates and failures; do not import static device blacklists. |
| Codec-specific data after flush | VLC retains CSD, resends it after flush, and synchronizes its output thread with flush. Kodi reinjects extra data and waits for keyframes where required. | **Handled/reject duplication.** Media3/Exo own CSD, codec reuse, keyframe-after-flush, and adapter synchronization. Vibe must not inject codec configuration into those renderers. |
| Async/sync adapters | Kodi and VLC use their own threading models. Media3/Exo choose adapters by API/device workarounds. | **Handled.** Vibe already exposes Auto/Async/Sync as a user/session adjustment. Auto remains default; no device profile is added. |
| Flush/drain/EOS | VLC acknowledges flush, ignores stale output dequeue errors during flush/close, and bounds drain to three seconds. Kodi flushes player queues and resets/reconfigures a failed codec. | **Implement D1/D4 only above the library.** Classify errors before retry, suppress only transition-stale notifications, and quarantine only a proven fatal codec for the current playback session. Do not replace library flush/drain. |
| Output Surface | Kodi and VLC explicitly own Surface/texture lifetime and transformation paths. Media3 contains set-output-surface and placeholder-surface device workarounds. | **Handled/test.** Vibe's shared surface/session tokens prevent stale callbacks. Retain fullscreen, subwindow, HOME/return, and file-transition tests; add no speculative surface workaround. |
| Runtime decoder failure | VLC marks a failed MediaCodec for that elementary stream/session. Kodi can reset/reopen a failed implementation. | **Implement D4.** After error classification and telemetry, exclude the identified fatal decoder only from the current player session and rebuild once at the preserved position, then try the next policy-eligible decoder. Never persist the automatic exclusion. |

### Audio selection, decode, passthrough, and A/V sync

| Finding | Kodi/VLC behavior | Vibe disposition |
| --- | --- | --- |
| Encoded passthrough | Kodi tests the real sink and prefers passthrough only when supported. VLC tries IEC61937 then codec-specific encodings and falls back if AudioTrack construction fails. | **Handled.** Vibe defaults AC-3/E-AC-3/DTS to decoded PCM because physical ONN/NVIDIA evidence proved vendor raw-passthrough claims can be silent. Passthrough remains explicit opt-in for a real external sink. |
| Software audio with hardware video | Kodi can use FFmpeg audio independently from MediaCodec video. VLC's MediaCodec audio is disabled by default and its normal audio decoder feeds AudioTrack PCM. | **Handled.** Vibe's isolated FFmpeg audio extensions retain hardware video while decoding encoded audio to PCM for Media3/legacy Exo and their GSY delegates. |
| Channel layout and track identity | Kodi avoids decoders that silently downmix unless allowed and retains stream identity. VLC validates channel count/rate and reorders/extracts channels. | **Handled/test.** Vibe uses stable track IDs and language/role preference, including UK primary AC-3 versus NAR. Test stereo/5.1 and format transitions; do not implement a replacement AudioTrack sink. |
| AudioTrack loss/underrun | VLC recreates AudioTrack on `ERROR_DEAD_OBJECT`, tracks the playback head including wrap, and reports latency. Media3/Exo audio sinks already contain platform dead-object/retry/position tracking. | **Telemetry/test.** Add audio underrun/output-error counters to Vibe diagnostics before considering recovery outside the library. No VLC AudioTrack port. |
| A/V and subtitle offsets | Kodi applies audio/subtitle delay around its master clock. | **Handled.** Vibe exposes bounded active audio and subtitle offsets and keeps server/DVD clocks authoritative. |

### Captions, subtitles, and DVD overlays

| Finding | Kodi/VLC behavior | Vibe disposition |
| --- | --- | --- |
| CEA-608/708 | Kodi and VLC decode caption services separately from ordinary subtitle tracks. | **Handled.** Vibe implements CEA extraction/service selection, legacy extender callback compatibility, STV/Off/CC1/CC2 authority, and flush/transition clearing. |
| DVB bitmap | Kodi uses FFmpeg's bitmap subtitle decoder; VLC has a DVB subtitle decoder with region/palette positioning. | **Handled.** The pinned Media3/Exo TS paths expose DVB bitmap cues and Vibe selects real DVB tracks ahead of descriptor-only CEA fallbacks. |
| DVB Teletext | Kodi has a dedicated Teletext stream/player/cache. VLC has a dedicated Teletext decoder with page/subtitle-flag logic. It is not DVB bitmap or CEA. | **Separate feature.** Neither pinned Media3 nor legacy Exo exposes a Teletext PES/page decoder. Correct support requires a separately licensed decoder/dependency, page selection, rendering, tests, and notices. Do not relabel Teletext as CEA or DVB bitmap. |
| DVD SPU and menu highlight | Kodi/VLC distinguish subtitle visibility from forced/menu highlights and clear overlays on flush/cell changes. | **Handled/test.** Vibe's SPU assembler/compositor, CLUT, forced/menu highlight path, STV subtitle authority, generation tokens, and cell timestamp normalization already implement this. Retain authored-disc language/off/menu-return tests. |

### DVD navigation and presentation

Kodi's `DVDInputStreamNavigator` and VLC's `dvdnav` access module both use
libdvdnav events rather than treating a disc as one ordinary file. Both cover
BLOCK/NOP, STILL, WAIT, CLUT, SPU/audio stream changes, VTS/cell changes, NAV
packets, highlights, non-seamless HOP, STOP, chapters, angles, language defaults,
and menu activation. Both enable dvdnav read-ahead and whole-PGC positioning.

Vibe's server-side DVD VM and Android presentation engine already expose the
corresponding control/event stream, native menu highlight, still-frame holding,
cell/title transitions, audio/subtitle selection, PGC time, and main-title
fallback. The accepted work is therefore **test-only**: retain event-by-event
authored-disc coverage and physical menu/menu-less discs. Replacing the SageTV
DVD VM or embedding Kodi/VLC/libdvdnav in the client is rejected because it
would bypass the SageTV-controlled session and create a separate player backend.

### Lifecycle, retries, and diagnostics

| Finding | Reference behavior | Vibe disposition |
| --- | --- | --- |
| Seek/flush stale events | Kodi flushes message/overlay queues; VLC ignores output errors known to race with flush/close. | **Implement D1.** Tag active seek/flush/teardown epochs and suppress only matching stale user notifications; retain the diagnostic event. |
| Retry bounds | Kodi/VLC separate demux, decoder, and output failures and do not blindly repeat the same recovery forever. | **Implement D1/D4.** Replace Vibe's generic same-player reprepare loop with class-specific bounded decisions. Datasource failures must not blacklist codecs; parser failures must not be presented as decoder failures. |
| Session isolation | Both reference players discard state on close/reopen. | **Handled/extend.** Vibe session tokens already reject stale callbacks; decoder quarantine must use the same session boundary. |
| Useful counters | Kodi/VLC expose selected codec, queue/cache state, dropped frames, timing, and error context. | **Implement D3/D5.** Add ordered/attempted/selected decoder and recovery result, plus audio underrun/output errors and TS transition context, to existing stats/MCP/bundles. Do not add continuous packet capture. |

## Ordered accepted implementation tasks

These are deliberately dependency ordered and independently testable.

1. **D1 - Playback error classification.** Classify datasource/open/read,
   container/parser, codec initialization, fatal runtime codec, audio output,
   and active seek/flush/teardown errors. Unit-test recovery and presentation
   decisions before changing the player listeners.
2. **D2 - Policy-filtered decoder initialization fallback.** Enable Media3 and
   legacy Exo fallback after Hardware/Software/Fallback filtering. Prove strict
   Hardware never crosses into software. GSY delegates inherit this behavior;
   IJK is unchanged.
3. **D3 - Decoder attempt telemetry.** Record ordered candidates, attempts,
   selected video/audio decoder, initialization failures, session exclusions,
   fallback reason, and result in the existing stats, MCP state, Test Current
   Video result, and diagnostic bundle.
4. **D4 - Session-local fatal-decoder quarantine.** Only after D1/D3 can prove a
   fatal codec error, exclude that decoder for the current playback session,
   rebuild once at the preserved position, and try the next eligible decoder.
   Never persist the automatic exclusion and never quarantine on datasource,
   parser, seek, flush, or teardown errors.
5. **D5 - Transition/audio evidence.** Add bounded counters for audio underruns
   and output failures plus track/program/format changes. Extend generated TS
   discontinuity, PMT/track-change, MPEG-2 sequence-change, and seek/flush tests.
   A behavior change requires a failing physical fixture.
6. **D6 - Physical hardware matrix.** Run Vibe-only validation on MediaTek Fire
   TV, ONN/Amlogic, and NVIDIA with the same hardware mode, transport, and
   fixture. Cover MPEG-2, H.264, HEVC, long/growing TS, discontinuity, MKV seek,
   DVD menu/title, captions, audio, seek/FF/RW/Stop, transition, and HOME/return.

### D6 physical evidence (2026-09-13)

- MediaTek Fire TV `.25`: the complete Media3, legacy Exo, GSY/Media3, and
  GSY/legacy-Exo codec/transition reports pass with hardware video decoders.
  The four-configuration seek/FLUSH report has `executionPassed=true`, and all
  four HOME/background/return lifecycle reports have `status=PASS`.
- NVIDIA Shield Tube `.68`: both GSY delegate reports pass as complete runs;
  the direct Media3 and legacy-Exo coverage is the union of the initial and
  focused-resume reports after stock-server control timeouts. VP9 Profile 2 is
  skipped when the device advertises only Profile 0.
- ONN v1 `.141`: both GSY delegate reports pass as complete runs; direct
  Media3 and legacy-Exo coverage is closed by focused resume reports after
  isolated stock-server control/harness failures.
- FFmpeg HDMI evidence on `.25` confirms labelled fixtures render fullscreen.
  Short files naturally reach SageMC's StopPopup at EOF. The MCP runner now
  dismisses a stale popup only after the next exact MediaFile ID is current and
  its replacement player is active; it never sends pre-Watch STOP/BACK.
- These results prove the generated codec/transition, seek/FLUSH, and lifecycle
  D6 scope. Long/growing media, UK captions, and DVD remain in MATRIX-001 through
  MATRIX-003 and are not implied by this checkbox.

## Explicitly rejected or deferred

- Launching Kodi, VLC, MX Player, or the old MiniClient during automation.
- Using MX Player as a source or reverse-engineering its proprietary behavior.
- Copying Kodi/VLC code or wholesale device/codec blacklists.
- Adding a second general TS, PS, Matroska, master-clock, frame-release, CSD,
  AudioTrack, or MediaCodec state machine above Media3/legacy Exo.
- Growing the Pull cache, generating fake MediaServer reads, or using guessed
  byte offsets without extractor validation.
- Treating DVB Teletext as CEA/DVB bitmap. Teletext is a separate licensed
  feature and does not block decoder-stability work.
- Reintroducing VC-1 validation; it was explicitly removed from active scope.
