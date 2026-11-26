# Shared startup and playback settings

Startup settings retain the six light-blue collapsible headings. The added
long-press gear opens a playback hub with Playback Settings, Transport Settings,
Diagnostics and Recovery. Video, Audio, Captions and diagnostics icons remain
shortcuts; they enter the same panels, not independent copies of each setting.

## Menu organization

```text
Playback Settings
  Video
    Player / Decoding Method / Player Deinterlace / Video Filters
    Display Settings / DVD Settings / Decoder Queueing Mode
    DVD Settings includes Timestamp Repair and HDMI Settle
  Audio
    Audio Stream / Audio Output / Passthrough Offset / Audio Offset
    A/V Sync Test / Restart Audio Output / Save as Device Defaults
  Captions / Subtitles
    Broadcast CC / Subtitle Stream / Subtitle Appearance / Subtitle Offset
  Display Settings
    Refresh-rate Matching / Aspect Ratio shortcut note
  DVD Settings
    Optional DVD companion controls
Transport Settings
  Selected / Actual Transport
  Source Buffering
  Processing Method / Automatic Bitrate Adjustment
  Minimum / Maximum Video Bitrate
  Video Encoder / Hardware Encoder / Video Codec / Resolution / Frame Rate
  Audio Codec / Audio Bitrate / Audio Channels
  Container / Remuxing / Reset Transcoding Settings Only
  Current-session Bitrate / Reduce Current Session / Recover Current Session
Diagnostics
  Playback Statistics / Export Diagnostics / Server Playback Snapshot
  Source Previews
Recovery
  Restart Video Decoder / Restart Audio Output
  Restore Saved Playback Defaults
```

The long-press statistics graph and information icons are adjacent in the same
row, with information on the right, in both TV and touch layouts. They remain
independent toggles and keep the menu open. The graphs panel is labelled Playback
Stats, not Graphs only.

Network activity measures media bytes actually delivered over the sampling
interval, not the video's encoded bitrate or a speed test. Segmented requests
and buffer refills naturally produce bursts and quiet intervals. A quiet network
bar alone is not a playback stall; compare actual buffer reserve and progress.

Development HLS-007 refills server-confirmed completed responsive HLS at80% of
the existing maximum (or a higher configured minimum), rather than waiting for
the ordinary Pull minimum. With5s/20s settings the effective refill threshold is
16s; maximum remains20s, with normal segment-sized variation. Startup/rebuffer
reserves and saved settings remain unchanged. Both Exo delegates and their GSY
wrappers share this policy. Live/growing/unknown or older unconfirmed sources
retain their existing policy. This cannot maintain a full reserve on a connection
that persistently delivers less than the encoded bitrate; shared MIM adaptation
remains responsible for reducing demand when enabled. No new parallel encoder,
network polling, prefetch worker or server change is introduced.

For owned responsive HLS, the player maps its observed sliding-window position
through Timeline.Window's position-in-period before publishing recording and
caption clocks. A moving180s playlist is not the recording duration or a clock
reset. Buffer edges use the same coordinate; the Direct controller still owns
the separate seek/start epoch. Ordinary transports/DVD and unknown metadata
remain unchanged. No wall-clock progress or guessed recording anchor is used.

The long-press hub expands Transport, Diagnostics and Recovery directly beneath
their blue headings. There is no navigation-only wrapper panel beneath them.
Direct icon shortcuts reuse those same entry builders; choice dialogs and
Cancel-first confirmations remain. Startup settings retain their established
Fixed Playback Settings page. Existing keys, defaults and saved values survive.

Unavailable rows stay gray with a reason, preserving saved choices. For example,
IJK software and supported GPU filters are not interchangeable; buffering and
queueing require their supported Exo delegates. Fixed-only settings are not
offered as active controls on Pull. `dynamic` always means selected **Push**;
the actual negotiated transport is displayed separately.
Bounded subtitle offsets require ahead-decoded native DVD subpictures. Ordinary
CEA/text delivery must not pretend it can present cues before they arrive;
its offset row remains disabled while supported text appearance stays usable.

## Saving and apply timing

### SETTINGS-003 development implementation and boundaries

SETTINGS-003 adds the same Video Encoder setting to main and long-press Fixed
Playback Settings: **Use Server Default** (new-key default), **Prefer Hardware**
(allow software fallback), **Software Only**, **Hardware Only**. It applies to
Transcode and Transcode HLS only. Hardware Only must fail clearly rather than
fall back to software or stock processing. Policies are per session, never
global INI changes; DIAG-014 displays actual execution separately.

SETTINGS-005 adds **Hardware Encoder** to both shared menus: Auto (Server
Default), Intel Quick Sync (QSV), NVIDIA NVENC, VAAPI (Linux), AMD AMF (Windows)
and Apple VideoToolbox (macOS), matching Web's named accelerators. Software Only
is the existing Video Encoder policy, not a hardware backend; it disables and
ignores the retained hardware choice. Copy/Stock likewise do not request an
encoder. Video Codec still means H.264/HEVC, not Android's decoding backend.
Explicit backend selection applies next playback and requires separately
negotiated hardwareBackendRevision1 plus observed matching hardware output.
An explicit backend pin is strict even with Prefer Hardware selected; use Auto
to permit the policy's normal fallback. Seek/quality retain the session's pin,
and changing a future default never changes the active encoder or global INI.
Web retains its existing explicit Allow Fallback profile control; disabling it
provides the same strict selection. Existing Auto priority is path-specific and
unchanged: it is not a universal hardware guarantee. The shipped MIM runtime
targets Linux/Windows, so VideoToolbox refuses there rather than claiming macOS
support. Missing/older plugins retain Auto stock compatibility; unavailable
explicit choices fail clearly, never masquerading as a successful hardware run.

True per-client routing to original stock FFmpeg remains pending. The user
explicitly chose to preserve existing legacy clients rather than change the
shared launcher globally. Stock SageTV Pipeline currently preserves the stock
protocol path; an installed legacy MIM launcher may still select its modern
engine. Do not label that engine original stock FFmpeg.
Copy permits MPEG-TS remux without re-encoding. Copy with Remuxing Off uses
native original-source Pull, without allocating a MIM producer.
Legacy Transcode exposes output controls with Maximum, not Minimum. HLS exposes
applicable output controls and Minimum for automatic adjustment, restricted to
genuinely compatible packaging/caption formats. Audio Bitrate gains Use Source
when measurable and valid for the chosen codec, otherwise an explicit Auto
fallback; it is not audio stream-copy. Audio Codec also gains Use Source:
retain the detected codec when encoder/container/player-compatible, permitting
re-encoding for bitrate/channel changes. Unknown or unsupported codecs require
an explained compatible fallback or refusal, not a guessed value. Existing
AAC/128kbps and other saved defaults
remain. Video Codec offers Auto (H.264), H.264 and HEVC; actual encoder and player
support are required. Explicit unavailable encoder policies fail without a
silent Stock/software fallback. Scaling preserves aspect ratio without automatic
upscaling. Encoder/output options are immutable within a source and retained by
seek/quality replacement; saved changes apply at the next original OPENURL.

Current MIM delivery is segmented MPEG-TS for both Legacy and HLS. MKV/DVD
choices remain saved but are explicitly refused on this transport, not silently
converted to TS. Source audio codec/rate need the negotiated MIM output API;
the original stock Fixed handshake cannot resolve them safely and requires an
explicit audio codec/rate. Source does not mean audio stream-copy. Unknown
bitrate uses the encoder default and is reported as such; unknown codec refuses.
No Core/stock FFmpeg or shared live INI modification is allowed.

After an explicit Transcode/HLS producer startup fails, playback stops with a
bounded explanation instead of silently opening the original file through
Pull. Absent/older provider negotiation still retains the ordinary stock Fixed
compatibility path. Non-strict Copy retains its existing Pull fallback; Copy
with Remuxing Off deliberately opens the original source without encoding.

Effective Transcoding Output and statistics/export distinguish server encoding,
server decoding and client decoding. Only owned observed metadata is displayed;
unknown bitrate is not replaced by its requested target. An explicit diagnostic
refresh and source-commit callbacks use bounded reads, never polling timers.
The same closed cached observation is included in text/JSON diagnostics, the
shared ZIP's server-output.txt, web statistics, server uploads and session logs.
Export generation itself never refreshes the provider or polls the player.
Reset Transcoding Settings Only is confirmed/Cancel-first and resets only its
allowlisted keys, preserving credentials, identity and unrelated preferences.

Main settings edit saved device defaults. Playback changes use current-session
overrides unless the user explicitly chooses Save as Device Defaults. Restart-
dependent choices say so; the existing safe reload path is reused. Scope choices
always begin with This playback session, never a remembered Save selection.

Fixed output, adjustment and bitrate limits explicitly edit **next-playback
defaults**, with a Cancel-first confirmation. Processing Method instead says
**Reconnect required**, because it changes protocol negotiation. Saving must not replace or
reconfigure the active producer. Current-session Bitrate is a separate,
capability-negotiated action using the existing owned MIM quality interface.
No new adaptation policy is implemented by these menus.

Restore Saved Playback Defaults clears session overrides, not stored defaults,
credentials, profiles or files. The startup Reset Settings to Defaults remains
a separate, confirmed, allowlisted operation. Existing saved keys and values
survive APK updates; only missing keys use the code defaults.

## Shared ownership

`PlaybackSettingCatalog` owns setting keys, labels, resource choices, defaults,
numeric bounds, capabilities and timing. `PlaybackSettingPreferences` handles
typed startup persistence; it never rewrites a value just because a page opens.
`PlaybackSettingsPanel` renders collapsible, bounded runtime panels and persists
only namespaced focus/expansion state. `ActivePlayerAdjustmentsDialog` retains
the existing player actions and routes. Avoid adding parallel preference keys
or hard-coded choice lists for these settings.

The Android-only debug route accepts a closed panel enum and opens these same
production dialogs; it does not replace them with test-only menus. Run
`scripts/mcp_playback_settings_gate.py` in the reusable dev container for the
commissioned Non-Pro/stock232 main, runtime and HLS affected gates. It verifies
the installed candidate bytes, owns only its fixture/client context, and
restores preferences, captions, power and test sessions in `finally`.
The `recovery` phase verifies confirmed session restoration, paused intent and
caption/diagnostic return routes. The `reset` phase verifies the newly exposed
typed startup defaults and protected-value preservation. Test only affected
phases; do not replay unrelated completed player matrices.
`--phase final` combines only reset/recovery acceptance under one private
checkpoint, without replaying completed main-menu or HLS rows.
`--phase transcoding --task-id SETTINGS-003` checks the shared output choices,
mode applicability, typed Source/policy persistence, and confirmed reset under
an exact Non-Pro/stock232 checkpoint.
Use the reusable container's `/opt/opensagetv-vibe/android-python/bin/python3`;
the Web Python environment does not include the Android MCP server. `--phase
output --task-id SETTINGS-003` checks actual software/QSV playback, shared
runtime controls, cached text/JSON diagnostics and clearing on native Pull.

For FIXED-001 use `--phase fixed-routes --task-id FIXED-001`: Stock/Copy/Legacy/
HLS across supported Exo delegates, plus IJK Stock/Copy. Require actual producer
ownership/codec and advancing A/V, Pause/Play, and exactly one verified public
file-relative seek. `--route` reruns only an affected row; `--policy` isolates
an encoder choice. `--capture-hold` requires that exact route and writes a live
identity/expiry marker for independent HDMI capture. `fixed-failure` injects a
one-shot client startup failure and checks that explicit encoding never becomes
original Pull. `fixed-edges` distinguishes no-remux Copy, unavailable provider,
unsupported IJK encoded-mode compatibility, and adjusted HLS startup. Neither a
compatibility fallback nor an in-place preference proves native rate control.
`--phase inline --task-id SETTINGS-004` checks the three direct blue hub
sections, confirmation/return and unchanged playback/defaults.
IJK Stock/Copy use native first-frame/output-FPS/playing/clock evidence plus
post-seek HDMI motion/audio, not nonexistent Exo buffer counters. Its row
report explicitly requires that independent review; native clocks alone are
not audio/video output proof. The single hls phase also accepts --capture-hold.

The menus require neither a modified server nor MCP for normal playback.
Optional provider actions remain unavailable when their capability is absent.

Owned MIM playlists start at producer-local zero; the producer already applies
the requested file/live offset. Do not apply a second default HLS live-edge
jump, including Legacy Transcode. Explicit quality joins remain authoritative.
Multichannel AAC with ADTS config0 needs its actual in-band PCE. Both client
HLS extractor adapters complete ASC before sample metadata reaches the queue,
so negative-time preroll cannot discard the only channel configuration. Pure
bounded playback-owned state survives extractor rebuilds, not a new OPENURL.
Original complete pinned audio AARs and native libraries remain unchanged.
The debug-only output-channel witness is
separate from the extractor's incomplete channel0 input metadata. Audio decode
errors must not quarantine the working video MediaCodec. Verify these paths
with actual A/V and public file-relative seeking, not optimistic server clocks.

Stock SageTV FFmpeg is software-only with legacy feature limits; MIM's
Transcode (Legacy) is a different provider path and may use modern encoders.
Requested Hardware Only or a hardware Android decoder is not server-encoding
proof. Read Effective Transcoding Output for the current owned MIM session.
Stock-protocol Fixed without that observation stays Unknown; do not infer the
original stock binary when a shared MIM launcher is installed.
