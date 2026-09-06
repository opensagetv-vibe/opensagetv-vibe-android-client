# Android playback diagnostic standard

This document defines the evidence and experiment discipline for diagnosing
OpenSageTV Vibe Android playback. Active work is tracked only in `TASKS.md`.
Historical implementation results remain in `CHANGELOG.md`, `HANDOFF.md`, and
the versioned matrix-review documents.

## Safety boundary

- Test only `opensagetv.vibe.miniclient.debug`.
- Never stop, uninstall, clear, replace, or modify a package beginning with
  `jvl.sage.miniclient`.
- Complete first-time setup manually after a fresh install or cleared app data.
  Automated tests must not attempt onboarding.
- Normal app use keeps the generated and persisted client ID. Scripted launch
  and MCP/player tests default to `44:45:56:30:30:31` (`DEV001`); an explicit
  `--client-id` wins.
- Do not delete recordings or alter the SageTV media library during testing.
- Keep legacy ExoPlayer as the default until physical parity gates approve a
  replacement.

## Evidence-first rule

A timeline change is not proof of successful playback. A playback operation
passes only when the expected video and audio output counters resume advancing
and remain healthy through the observation window. Keep position, buffer,
surface, and wrapper flags as diagnostic context.

Before changing code after a failure, capture:

- player family and effective backend;
- Push, Pull, or Fixed streaming mode;
- hardware/software/fallback decode request and actual decoder;
- recording/container/codecs and completed-versus-growing state;
- requested action and requested target;
- visible symptom and screenshot where useful;
- Android player errors, renderer state, first-frame and audio counters;
- datasource open/read/wait/byte counters;
- SageTV server request, transfer, mux, and transcoder evidence;
- process/crash status and exact timestamps.

The on-screen **Playback Stats** panel is the preferred first look during a
physical reproduction. Use compact mode while watching for a symptom and
detailed mode to correlate it with decoder, datasource, synchronization, and
transport counters. The three bars show measured media activity, buffered
playback time, and CPU. The CPU bar uses a fixed 0-100% scale with Vibe and the
remainder of device CPU in separate colors. The `Vibe` and `Other` label values
use those same colors, with a neutral `Total`. Values appear once above each bar;
duplicate detail rows and the invariant estimated link-capacity bar are omitted.
Detail rows use the same 10.5sp normal typeface as the graph labels.
Export produces a bounded redacted snapshot with no media path, server address,
credential, or client ID. CPU sampling starts
and stops with the overlay. Preserve the normal MCP/server evidence as well
when a root-cause claim depends
on another process.

The long-press navigation panel's bar-chart icon, the submenu's checked/unchecked
row, and MCP use the same overlay controller. The icon is white while disabled
and green while enabled. Use
`dev_set_active_player_overlay(mode="toggle")` for an interactive toggle, or select
`off`, `compact`, `detailed`, or `detailed_30s` when a test needs deterministic state.
The legacy MCP `visible` Boolean remains supported for existing automation.

The panel is a troubleshooting view, not a copy of YouTube's consumer-facing
"Stats for nerds" display. It deliberately excludes video/session IDs,
viewport-versus-optimal-resolution labels, normalized volume, generic color
labels, mystery text, wall-clock date/time, and other fields that do not help
isolate a SageTV transport, buffering, decoder, cadence, seek, caption, or A/V
sync fault.

If one of these sources is unavailable, record it as unavailable rather than
inferring a cause from another layer.

For legacy event-225 captions, advancing protocol counters alone are not a
post-seek pass. Inspect the captured frame: CEA-608 lines must remain in
timestamp order, occupy the normal authored/STV lower-screen rows, and stay
within two seconds of the generated fixture's burned-in PTS. Malformed or
top-screen text with advancing counters indicates packet ordering or retained
decoder state and must fail the physical caption gate.

## Ownership model

### Pull

The Android client opens and reads the SageTV MediaServer stream. Diagnose the
complete chain:

1. SageTV receives the file open/read/seek request.
2. MediaServer reads the requested bytes and writes them to the socket.
3. Android receives those bytes through its Pull datasource.
4. Extractor/player consumes the data.
5. Decoder and renderer resume output.

Separate server starvation or short writes from client extractor preroll,
decoder backlog, surface loss, or stale callbacks.

### Push/Fixed

The server owns mux/transcode and feed timing. Distinguish a genuine
server-originated seek/flush/rebase sequence from a debug-only local player
seek. Do not generalize Push results to Pull-only buffer, read-size, timestamp
search, or seek-policy settings.

## Timeline fields

- `serverRequestedSeekMs`: most recent SageTV protocol seek target.
- `serverAnchorMs`: server-provided Push timeline anchor.
- `health_playerPositionMs`: backend-local Android playback position.
- `sageTimelineMs`: value returned to SageTV by the MiniClient protocol.

Never compare these as if they were interchangeable. Every seek report should
record the requested target, actual backend landing position, applicable server
anchor, duration/live edge, and timestamps for invoke, return, discontinuity,
READY, first frame, and resumed audio.

## Controlled-experiment rules

- Change one behavioral variable at a time.
- Keep recording, target, backend, decode mode, streaming mode, and observation
  window fixed unless one is the variable under test.
- Use a fresh player/session for settings applied at player construction.
- Run controls before and after the candidate when practical.
- Repeat a candidate at least three times before promoting a default.
- Record invalid startup, EOF/delete-prompt, transport, or ADB failures as
  infrastructure failures, not player results.
- Do not start a measured operation until playback is safely before EOF and
  both expected A/V counters are advancing.
- Preserve contrary evidence; do not select only the fastest observation.

## Current evidence baseline

The `20260828_034725_player_tuning_matrix.json` results showed that the tested
Media3 Pull timestamp-search, read-size, seek-policy, buffering, and codec-mode
combinations did not fix long Comskip recovery. Eight-times TS search was the
more stable baseline, 512 KiB reads did not improve the case, `NEXT_SYNC` did
not beat `CLOSEST_SYNC`, and asynchronous MediaCodec queueing trended worse.
Do not run another broad sweep of the same knobs without new evidence.

Media3 Push Sync versus Auto has now been repeated three times on the same
generated fixture. Sync recovered in 7196/6477/7418 ms. Auto recovered in
8752/4236 ms and expired once at 50650 ms. Sync remains the saved default
because it won two runs and avoided the unbounded stall, although neither mode
is considered a fast Push-seek solution.

On the Cast/Session-pruned v0.5.83 APK, the generated fixture passes hardware
start, fullscreen, single FF, single REW, pause, and resume. One legacy Exo
Pull 16-command rapid mixed FF/REW run recovered video within 314 ms but then
reported `playback_did_not_stay_active,audio_stalled_after_recovery`; preserve
`artifacts/firetv/20260830-080924_mcp_rapid_output_health_*` as the intermittent
baseline. The harness was extended with rapid-only repeats and a fixed fixture
reset. At zero inter-command delay, three subsequent fixed-position repeats
passed on every combination of Media3/legacy ExoPlayer and Pull/SMB Direct.
Pull recovery ranged from 906-1662 ms and SMB Direct from 2541-3636 ms, with
sustained hardware-decoded video and audio after every repeat. No speculative
player sequencing change was made for a failure that could not be reproduced.

The same generated fixture's 120-180 second `.edl` interval passes a strict
server-owned marker test on both backends over Pull and SMB Direct. The test
requires `serverRequestedSeekMs=180000`, fullscreen playback, and sustained
hardware-decoded A/V; an advancing client timeline alone is insufficient.
Measured first physical source read / first rendered frame deltas were:

| Backend | Source | First read | First frame |
| --- | --- | ---: | ---: |
| Media3 | SMB Direct | 625 ms | 799 ms |
| Legacy ExoPlayer | SMB Direct | 539 ms | 728 ms |
| Media3 | SageTV Pull | 911 ms | 1112 ms |
| Legacy ExoPlayer | SageTV Pull | 2483 ms | 2560 ms |

The first two setup attempts were correctly rejected because their narrow
pre-position tolerance expired while playback continued advancing; they were
not product crashes or false passes. The passing command allows bounded elapsed
playback around the marker while still requiring the exact server seek target.

The active technical investigations are maintained in `TASKS.md`. A possible
partial socket write in SageTV `MediaServer.java` was tested with a controlled
NIO/non-NIO A/B and server/client byte correlation. Both paths delivered
consistent positive byte counts and recovered A/V; NIO was slower in the
measured case, so this experiment does not support the partial-write hypothesis.

## Minimum current experiment set

1. Verify current APK/API/branding/client-ID behavior and establish a fresh
   physical baseline.
2. Fix or verify the matrix startup gate so EOF and `AskToDeleteRecording`
   cannot enter a measured case.
3. Preserve the captured server-driven Push seek/flush/anchor ordering while
   investigating the remaining slow recovery; do not transfer seek ownership
   to the local Push backend without new protocol evidence.

## Server partial-write hypothesis

For the non-NIO MediaServer path, record the requested offset/length, disk bytes
read, socket bytes written, connection identity, and whether the normal,
read-ahead, remux, or transcoder path was used. A single `SocketChannel.write`
is not proof that the entire buffer was sent.

If a short write is observed, the server fix must drain the buffer without
advancing the file position prematurely. Blocking and non-blocking socket modes
need correct, separate backpressure behavior. Repeat the same client case after
the correction and compare byte counts and first-frame recovery.

Evidence supporting the hypothesis would include an observed short write, a
repeatable improvement with NIO transfers, or a corrected full-drain path that
removes the stall. NIO merely being faster is insufficient by itself.

The completed generated-fixture A/B produced non-NIO recovery of 340/434 ms
and NIO recovery of 447/638 ms. First physical reads were 85/167 ms versus
558/178 ms; first rendered frames were 621/692 ms versus 728/828 ms. Because
all four runs recovered with consistent MediaServer bytes and NIO did not win,
do not patch Core transfer loops without new direct evidence of a short write.

## Active-player controls derived from Kodi

Kodi's current player settings and Android display implementation were reviewed
as behavior references, not as code to copy. The Vibe control surface uses the
following narrower mappings:

| Kodi behavior | Vibe behavior |
| --- | --- |
| Adjust display refresh rate: Off, Always, On start/stop, On start | `Off`, `Seamless`, and `Always`, plus an explicit restore-on-stop policy. Show content FPS, active display Hz, supported modes, selected mode, and reason. Preserve resolution by default. |
| Display refresh-change delay | Bounded HDMI-settle delay before decoder start/resume. It is not a general playback sleep. |
| Exact and multiple refresh-rate matching | Prefer an exact fractional match (for example 23.976/59.94), then a clean integer multiple. Never invent a mode the display does not report. |
| Audio passthrough toggle and audio information | Current-session passthrough toggle plus resolved codec/output/channel status. Unsupported backends report `unsupported`; a change requiring recreation uses one controlled same-position reload. |
| Audio amplification and centre-channel downmix | Offer only when the active backend is producing decoded PCM and can prove the adjustment is applied. Hide/disable during encoded passthrough rather than silently changing the output path. |
| Video stream selection | Enumerate actual alternate video streams or DVD angles only when both Media3 and the SageTV DVD session expose them. Never present a synthetic selector. |
| Video cache/read-ahead sizing | Source-scoped `Low latency`, `Balanced`, and `Resilient` presets. Raw unbounded byte/time controls remain debug-only. |
| Preferred/default audio language | Preferred language plus prefer-authored-default behavior, without overriding an explicit SageTV track command. |
| Subtitle language/forced-only and subtitle style | `Off`, `Forced only`, preferred language, and authored/default selection. Position, size, background, and opacity apply only to text captions/subtitles. Authored DVD SPU and PGS bitmap overlays retain their authored geometry and palette. |
| Player process information | Live audio/video queue, datasource rate/wait, bitrate, A/V correction, decoder, dropped/rendered frames, content FPS, display Hz, transport, and buffering state, with an export action. |
| View mode/aspect controls | Preserve the existing SageTV aspect commands and bounded Source/Fit/Zoom/Stretch behavior. |

Kodi options deliberately not exposed are its local subtitle browser/search,
brightness/contrast/gamma without a controlled shader path, stereoscopic and
orientation controls, desktop display calibration, desktop software post-processing,
noise/sharpness filters, arbitrary codec parameters, decoder filters, and
deinterlace modes Android does not reliably control. Interlace and HDR state
are diagnostic/read-only unless the Android platform exposes a verified control.
Kodi's speed-based sync-to-display remains experimental because it changes
program speed and conflicts with encoded-audio passthrough. SageTV/STV
continues to own skip intervals and the caption on/off state.

References: [Kodi player/video settings](https://kodi.wiki/view/Settings/Player/Videos),
[Kodi settings definitions](https://github.com/xbmc/xbmc/blob/master/system/settings/settings.xml),
and [Kodi's Android display-mode implementation](https://github.com/xbmc/xbmc/blob/master/xbmc/platform/android/activity/XBMCApp.cpp).

## Result format

Every device or integration run should report:

1. environment, APK version/hash, device/API, server version, and recording;
2. exact configuration and command;
3. startup verdict and whether the case was eligible for measurement;
4. per-operation A/V recovery verdict and time;
5. datasource/server byte and wait evidence;
6. player/decoder/surface/error evidence;
7. likely ownership with confidence and alternatives;
8. files and settings changed;
9. repeatability status;
10. next smallest controlled experiment.

## Validation before delivery

Run, in order:

```bash
./dev.sh test
./dev.sh validate
./dev.sh build
```

Device changes additionally require guarded Dev-only install and launch,
followed by the relevant physical matrix. A build without physical evidence
must say that device gates are pending.
