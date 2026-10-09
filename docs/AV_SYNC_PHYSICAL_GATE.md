# A/V synchronization physical gate

This gate compares two deterministic fixtures whose two-second ball impact and
25 ms audio click are generated from the same clock. It distinguishes local player/output
delay from SageTV transport, codec, interlace, and recording-timestamp delay.

## Fixtures

Regenerate the embedded fixture with:

```bash
python3 scripts/generate_av_sync_fixture.py \
  --output source/dev/android-shared/src/main/assets/vibe_av_sync_ball.ts
```

It is a 60-second 1280x720 progressive H.264 fixture at 60000/1001 with stereo
48 kHz AC-3. The length permits physical checks at either end of the
`-4.000..+4.000 s` slider without crossing the local player's repeat boundary.
Open it
from the Audio menu or the debug MCP tool `dev_show_av_sync_test` while normal
playback is active.

For a deterministic camera-framing preflight after starting the exact server
fixture, use `dev.cmd mcp-av-sync-screen` on Windows or
`./dev.sh mcp-av-sync-screen` on Linux/WSL. Optional `--output`, `--offset-ms`,
and `--passthrough-offset-enabled` arguments configure the active diagnostic
session directly; restore the user's original values when the run ends. The
script normally waits for an audio-output rebuild to become ready before
opening calibration. `--stress-immediate-open` deliberately skips that wait
only for the debug AudioTrack handoff gate.

Regenerate the server fixture matching the measured PBS NewsHour path with:

```bash
python3 scripts/generate_pbs_av_sync_fixture.py \
  --duration 120 \
  --output /tmp/OpenSageTV-Vibe-PBS-1080i-MPEG2-AC3-AVSync.ts \
  --metadata /tmp/OpenSageTV-Vibe-PBS-1080i-MPEG2-AC3-AVSync.json
```

Its validated profile is MPEG-TS, 1920x1080 top-field-first MPEG-2 at
30000/1001, 6.4 Mbit/s video, and stereo 48 kHz 384 kbit/s AC-3. The overall TS
mux rate is approximately 7 Mbit/s. Both fixtures hold the ball stationary on
the impact line for 200 ms, animate the lower-luminance outlined ball every
output frame, and pause naturally for 200 ms at the lower apex. The two short
holds give a 30 fps camera stable endpoint frames without making the visible
ascent and descent choppy or pushing the ring into the title area.

## Stock-server operation

Copy the server fixture into an existing SageTV video import directory and run
an ordinary library import scan. No Sage.jar change, FFmpeg/MIM plugin, private
MiniClient event, or Vibe server is required. On a server with Sagex/Web only,
launch the uniquely named imported MediaFile through the public context-aware
`Watch` API:

```bash
python3 scripts/mcp_playback_test.py \
  --server SERVER_ADDRESS --port 31099 \
  --player gsyplayer --gsy-engine system \
  --streaming push --decoding hardware \
  --text OpenSageTV-Vibe-PBS-1080i-MPEG2-AC3-AVSync \
  --direct-only --leave-running
```

`--streaming push` requests the normal Dynamic policy; it does not prove that
the selected backend ultimately used Push. Record `playbackSource` and the
datasource class from the live snapshot. For example, the commissioned stock
server resolves Media3 Dynamic to `SAGETV_PULL`, while legacy Exo can negotiate
`SAGETV_PUSH`. Label evidence with the observed transport, not the requested
preference.

If the stock-compatible Core MCP plugin is installed, exact-path resolution may
be used as well. Exact path is preferred when available; unique-title public
Watch is the deterministic fallback and never opens on-screen Search.

The optional FFmpeg/MIM plugin is tested separately with `--streaming fixed`.
That result must not replace the stock Push result because transcoding/remuxing
can normalize timestamps and hide the original path's behavior.

## Physical evidence

Point a direct webcam at the display and let its microphone hear the actual TV,
soundbar, or receiver. The helper uses the Vibe FFmpeg build to open both C920
pins in one DirectShow graph; do not use GStreamer, Logi Capture, or another
virtual-camera application. Preserve the
device's existing audio setting before each run, test at zero and at the user's
reported offset, and restore it afterward.

The Windows capture helper drains its pipeline cleanly:

```powershell
python scripts/capture_av_sync_webcam.py `
  --duration 20 `
  --output artifacts/firetv/av-sync/server-push.mkv
```

On the commissioned C920, opening a DirectShow graph can reapply stale camera
Zoom/Pan/Tilt values after a successful pre-open reset. The helper therefore
resets the controls once before open and again after FFmpeg owns the graph. Do
not remove the post-open reset: it is what makes every capture use the full
physical field of view.
Inspect a frame at least two seconds into a new capture. A frame near the
one-second post-open reset can still show the driver's transient crop even
though the settled capture sees all four authored corners. The framing
analyzer skips that settling interval and prefers the corners' warm color
bias over bright neutral bezel/reflection pixels; a genuinely cropped older
capture still fails its four-corner gate. Close an existing calibration dialog
before changing its initial offset through `mcp-av-sync-screen`: the open
dialog owns a separate offset and does not inherit a new player setting.

The commissioned C920 opens its camera and microphone together at
1280x720/30. A physical recording is eligible to close a gate only when all
four authored yellow corners are visible; this proves that the camera sees the
complete TV raster. `--diagnostic-only-allow-cropped` may quantify a cropped
recording, but the resulting JSON deliberately sets `physicalGateEligible` to
false. `--video-only` remains a motion-clarity preflight and cannot measure or
close an A/V-sync gate.

Capture and report these independent cases:

1. Embedded fixture, decoded PCM, zero offset.
2. Embedded fixture, encoded passthrough, zero offset.
3. Server fixture, stock Dynamic, encoded passthrough, zero offset; record the
   observed Push or Pull transport.
4. Server fixture, the same observed transport and calibrated offset.
5. Optional server fixture, Fixed/MIM, using the same output settings.

Measure a completed camera/microphone capture with:

```powershell
python scripts/analyze_av_sync_webcam.py `
  artifacts/firetv/av-sync/server-push.mkv `
  --expected-offset-ms -400 `
  --baseline-offset-ms 428 `
  --json-output artifacts/firetv/av-sync/server-push.json
```

The analyzer uses the capture file's common Matroska clock. A positive
`audioMinusVideoMilliseconds` result means the audible click followed the ball
impact; a negative value means audio arrived first. At least three impact/click
pairs are required. Supply the offset selected in Vibe with
`--expected-offset-ms`. First measure the same route with the player at zero,
then pass that raw result as `--baseline-offset-ms` for adjusted captures. The
analyzer pairs against baseline plus the selected player offset and reports the
raw physical delay, baseline-corrected player effect, and residual from the
expected result. The expected value normally disambiguates the repeating
two-second pattern, but an exact multiple of that period still needs player
state/counter evidence because identical events have no intrinsic sequence
number. The 60-second embedded asset prevents the offset test itself from
crossing a repeat boundary. The C920's 30 fps image limits physical
precision to about 33 ms, so this gate identifies route-scale delay and
validates the direction of 25 ms adjustments; it does not certify every
individual 25 ms step.

An offset is a player/output-path defect only when the common-clock server
fixture reproduces it. If the server fixture is synchronized but a broadcast
recording is not, retain the recording as evidence of source PTS/timestamp
behavior instead of applying a universal player correction.

### Speech recording cross-check

For a real talking-head recording, match both the webcam video and microphone
audio back to the same original TS. This produces a diagnostic result, not a
replacement for the calibrated ball/click fixture. Use an exact MediaFile path,
seek to the requested interval, and keep the Audio settings menu closed during
the capture. Change live audio settings without opening a menu via
`dev.cmd mcp-set-active-audio --output passthrough --offset-ms -400
--passthrough-offset-enabled true`. The command waits for a healthy player.

For `PBSNewsHour-65670461-0.ts` at 23 minutes, a private local capture can be
matched with:

```powershell
python scripts/analyze_recording_av_sync_webcam.py `
  "\\192.168.10.175\sagemedia\tv\PBSNewsHour-65670461-0.ts" `
  "C:\TMP_SAGETV_DOCKER\artifacts\temp\pbsnews_pro_23min_encoded_minus400.mkv" `
  --source-start-s 1370 --source-duration-s 130 --camera-duration-s 20
```

The analyzer compares band-limited speech audio and changing video pixels, not
just a mostly static interview background. `--camera-crop W:H:X:Y` removes a
visible TV bezel from the matcher when needed; it does not turn an incomplete
four-corner calibration recording into primary evidence. It samples video at
5 fps, so report its 200 ms resolution and correlation scores. Keep the source
and webcam recordings private; do not add copyrighted frames to the project.

On 2026-10-03 the user confirmed that the Pro/C920 was on the original
TV/surround path. This exact-path stock `.175` recording produced these
diagnostic speech correlations at the 23-minute interval:

| Output setting | Audio after picture | Audio correlation | Temporal video correlation |
| --- | ---: | ---: | ---: |
| DIRECT AC-3, 0 ms | 662 ms | 0.352 | 0.258 |
| DIRECT AC-3, -400 ms | 288 ms | 0.337 | 0.568 |
| DIRECT AC-3, -650 ms | 119 ms | 0.440 | 0.239 |
| Decoded PCM, 0 ms | 730 ms | 0.409 | 0.189 |

The -400 ms change reduced the measured speech delay by about 374 ms, close to
the previously measured -360 ms change in the authored full-frame pulse test.
The remaining delay is route-specific evidence, not a reason to impose an
automatic Fire TV Pro offset. The Pro was returned to its saved encoded
-400 ms setting, and its original Android stay-awake values were restored.

The compiled Vibe FFprobe read the original TS packet timestamps around
23:00: the primary AC-3 packet starts at `1380.000000 s`, and the nearby
MPEG-2 video packet has PTS `1379.995833 s` (a roughly 4 ms nominal gap, not
a 500 ms source gap). B-frame ordering means a single packet pair is not a
lip-sync measurement; the source-matched webcam check above provides that
separate physical observation. During the corresponding live Media3/Pull
playback at 23 minutes, the debug snapshot reported `audioUnderrunCount=0`,
`audioOutputErrorCount=0`, `errorState=false`, and advancing `mediaTimeMs`.
The authored fixture reported the same zero error counters. AudioFlinger
separately identified active DIRECT AC-3 for encoded playback and a PCM mixer
thread for decoded playback. These observations close AUDIO-001's source,
player, and output-path characterization, but not AUDIO-005's alternate-route
comparison or AUDIO-006's full-range/lifecycle acceptance.

A repeated stock-server pulse fixture captured nine consistent events at
encoded -400 ms: median audio-after-impact 218.667 ms, or -392.666 ms from the
older 611.333 ms route baseline. That capture clipped a registration arm and
remains diagnostic-only. After the webcam was centered, a new independent
full-frame -400 ms capture measured 208.0 ms over nine events and passed the
four-corner gate. The corrected analyzer accepts the complete TV within a
visible bezel; it still rejects the clipped earlier capture.

### Centered original-TV/surround matrix

The user confirmed that the C920 filmed the original TV/surround path. On Pro
`.29` with stock `.175`, exact-path playback of the authored 1080i MPEG-2/AC-3
fixture used Pull and the requested player backend. Each 20-second capture
contained eight to ten matched impacts/clicks, all four frame corners, and
passed `physicalGateEligible`. Positive values below mean sound followed the
picture. These are observed absolute delays on this output route, not a model
default or proof of 25 ms slider precision.

| Player | 0 ms | -400 ms | +400 ms |
| --- | ---: | ---: | ---: |
| Direct Media3 | +514.7 ms | +183.0 ms | +974.7 ms |
| Direct legacy Exo | +604.7 ms | +201.3 ms | +1048.7 ms |
| GSY Media3 | +588.0 ms | +224.7 ms | +1001.3 ms |
| GSY legacy Exo | +604.7 ms | +208.0 ms | +988.0 ms |

Direct Media3's -400/+400 endpoint span was 791.7 ms against an expected
800 ms. Legacy Exo's -400 ms row moved 403.3 ms relative to its zero row;
the GSY legacy row moved 396.7 ms. A separate direct Media3 decoded-PCM zero
capture measured +628.0 ms on the same physical path; AudioFlinger identified
an active PCM mixer thread rather than DIRECT AC-3. Thus the route delay is
not exclusively an encoded-passthrough problem. A live direct Media3 reset to
0 ms after an encoded run measured +408.7 ms over nine events, with all four
corners visible and no player error. The baseline had drifted from the earlier
+514.7 ms, so compare nearby captures for adjustment response and do not
hard-code either absolute value.

These rows close the centered-camera measurement for the original output
path. The alternate HDMI-capture PCM comparison is recorded below; the final
seek/lifecycle matrix remains under AUDIO-005 after MIMFIX-003. AUDIO-006 still needs its
full-range/end-state physical acceptance. Existing pause, seek, track,
format, live transition, and HOME results remain supporting targeted gates,
not a claim that the final broad matrix has run.

### Alternate direct-HDMI PCM route

On 2026-10-03 the Pro was moved to the USB HDMI capture. Fire TV displayed
its "New TV Detected" prompt; choosing "Do later in Settings" left equipment
control untouched. The Vibe FFmpeg DirectShow capture saw a complete
1920x1080 raster and 48 kHz stereo audio. The direct-HDMI analyzer mode
requires that full raster plus all four authored corners, but does not require
the webcam's visible-bezel clearance. The same stock `.175` exact-path
fixture at Media3 Pull, hardware video, decoded PCM, and zero offset measured
`-91.667 ms` median audio-after-picture over ten stable events (4.0 ms event
standard deviation), with `physicalGateEligible=true`. Positive means audio
late, so this direct-capture result has audio slightly early. This absolute
number includes any capture-card A/V alignment bias.

The real `PBSNewsHour-65670461-0.ts` was then played from the same stock
server and sought to 23 minutes. Source-matched direct-HDMI speech at decoded
PCM zero measured `-179.4 ms` audio-after-picture; correlations were 0.9948
for audio, 0.9884 for image, and 0.9886 for temporal image motion. It is a
diagnostic 5 fps/200 ms video match, not a 25 ms calibration. Player telemetry
at that interval showed an advancing timeline and `audioUnderrunCount=0`,
`audioOutputErrorCount=0`, `errorState=false`, with the decoded PCM offset
path active.

| Same Pro and stock server | Original TV/surround C920 | Direct HDMI capture |
| --- | ---: | ---: |
| Authored fixture, Media3 decoded PCM, 0 ms | +628.0 ms | -91.7 ms |
| PBS speech at 23 min, Media3 decoded PCM, 0 ms | +730 ms | -179 ms |

The authored comparison differs by about 720 ms between these two complete
output routes. That is strong evidence against applying a fixed Pro-wide Vibe
offset, but the different HDMI sink/EDID and capture-card timing mean it does
not isolate the TV or AVR as the sole cause. No software correction follows
from this A/B alone. The user's saved encoded -400 ms setting and Android
stay-awake values were restored; playback was exited cleanly. The remaining
AUDIO-005 work is its final post-MIMFIX-003 seek/lifecycle matrix.

For direct HDMI fixture captures, use `--capture-mode hdmi` with
`analyze_av_sync_webcam.py`. The default `camera` mode intentionally retains
the stricter visible-bezel requirement, and a missing authored corner fails
both modes.

### Original-route full-range encoded-offset recheck

On 2026-10-03 the Pro was back on the original TV/surround output, with the
C920 video and microphone captured together through Vibe FFmpeg at
1920x1080/30. Every capture logged an in-graph reset from the C920's reapplied
Zoom 144/Pan 1/Tilt -10 to Zoom 100/Pan 0/Tilt 0. Inspecting a one-second
frame initially made the camera appear cropped; a later frame from the same
recording visibly contained the complete TV and four authored marks. The
analyzer now excludes those early frames and distinguishes warm authored marks
from neutral bezel reflections. Its relaxed warm-mark threshold still rejects
an older genuinely cropped recording, which remains diagnostic-only.

Direct Media3's embedded AC-3 calibration dialog was closed and reopened for
each initial offset; changing the player setting under an already-open dialog
does not change that dialog's local offset. Against a nearby zero baseline of
`+278.667 ms`, the physical `-3.750 s` capture measured `-3498.667 ms` raw,
or `-3777.334 ms` baseline-corrected (27.334 ms from request, three impact/
click pairs). The `+3.750 s` capture measured `+3998.000 ms` raw, or
`+3719.333 ms` baseline-corrected (30.667 ms from request, four pairs). Both
passed the automated four-corner gate. The exact `-4.000 s` and `+4.000 s`
endpoints were accepted and visibly shown as applied in full-TV C920 frames;
their exact multiple-of-two-second displacement is sequence-ambiguous in the
repeating fixture, so do not claim endpoint timing from those still frames.

After returning to zero, a 20-second retry measured `+311.333 ms` over five
events, one 30 fps frame from the first zero baseline. Its top-left mark was
visibly present but washed to neutral white by glare, so the strict automated
framing check correctly left this particular retry diagnostic-only. An earlier
full-frame live zero reset remains the primary reset evidence. The Pro's
encoded `-400 ms` setting and original Android sleep values were restored,
and playback exited. At revision 112, focused cross-player encoded lifecycle
acceptance remained for AUDIO-006 and AUDIO-005's planned matrix followed
MIMFIX-003; revision 113 closes both on the current build at user direction.
Private generated-fixture captures and analyzer JSON are retained under
`artifacts/firetv/av-sync/2026-10-03-original-route/` outside the project;
temporary preflights and failed attempts were cleaned from `artifacts/temp/`.

## AUDIO-005/006 closure on the current Pro and stock-server build

At Android checklist revision 113, the remaining encoded lifecycle test used
the authored stock-server 1080i MPEG-2/AC-3 pulse fixture on Fire TV Pro `.29`
against unmodified `.175`. Direct Media3 Pull, direct legacy Exo Pull, GSY
Media3 Pull, and GSY legacy Exo Pull each applied `-400 ms` to the encoded
clock path, retained that setting through HOME/foreground return, a distinct
user pause across another HOME/return, explicit PLAY, and clean teardown.
Every row reported advancing audio/video on return, kept the original
MiniClient connection, detected no new crash, and restored 234 client settings
plus Android's original stay-awake policy. The physical same-clock C920
measurements above independently establish direction, scale, full range, and
zero reset on the original TV/surround output route; the lifecycle harness
asserts applied-path retention rather than substituting telemetry for those
physical measurements.

Combined with the earlier Pro stock-server seek/pause, dual-AC-3 track,
PMT/audio-format, live-source replacement, immediate-open AudioTrack handoff,
original-route PBS speech, and alternate HDMI-capture PCM A/B rows, this
closes AUDIO-005 and AUDIO-006 for the present build. The user explicitly
requested this audio gate before MIMFIX-003. If that later compatibility work
changes shared A/V scheduling or lifecycle code, rerun only the affected audio
rows before release. This does not certify the repeating fixture's exact
`-4000/+4000 ms` timing from sequence-ambiguous still frames, and the encoded
option stays opt-in rather than becoming a device-wide default.

## Current commissioned evidence

The 2026-10-03 Pro `.29` / stock `.175` run used the C920 camera and microphone
in one DirectShow graph. Early rows were cropped because graph open reapplied
Zoom 144/Pan 1/Tilt -10; they remain useful diagnostic history. The corrected
helper resets those controls after open to Zoom 100/Pan 0/Tilt 0, and a
1920x1080/30 capture now contains all four registration corners:

- Direct Media3 Pull: `-416.667 ms` for requested `-400 ms`; `+410.000 ms` for
  requested `+400 ms`.
- Direct Media3 Pull after pause/resume: `+389.333 ms`; after exact seek:
  `+382.667 ms`.
- GSY Media3 Pull: `+430.000 ms` for requested `+400 ms`.
- Direct legacy Exo Pull: `-470.666 ms` and `+342.667 ms` at requested
  `-400/+400 ms`; the endpoint-to-endpoint scale is 1.016 even though the route
  baseline moved between player rebuilds.
- GSY legacy Exo Pull: `-389.333 ms` for requested `-400 ms`.
- Embedded Media3: decoded PCM zero remained stable; encoded changes measured
  `-370 ms`, `+410 ms`, and `+4034 ms` relative to matched zero baselines.
- A later encoded full-range diagnostic accepted `-4000 ms`, then reset to
  zero without a crash. The reset capture measured a `-44.667 ms` median on
  the present route. The `-4000 ms` capture measured `-4298.667 ms` raw; an
  exact multiple of the fixture's two-second period is sequence-ambiguous and
  the crop prevents primary evidence, so this row is retained as a stress and
  teardown result rather than claimed as timing certification.
- HOME produced only the two clicks scheduled before the transition and no
  later clicks, confirming immediate diagnostic-audio teardown.
- Corrected full-frame embedded zero: `+611.333 ms` physical route baseline.
- Same-dialog encoded `-400 ms`: `+251.333 ms` raw, `-360 ms`
  baseline-corrected, `40 ms` residual. This proves the adjustment direction
  and approximate scale. AudioFlinger separately showed a live DIRECT AC-3
  output; the `FORCE_NONE` policy flag alone does not disprove passthrough.

The initial immediate diagnostic-open test exposed a real Fire OS direct
AudioTrack contention: the calibration AC-3 player and an in-flight playback
rebuild both attempted to acquire the encoded sink, ending in
`ERROR_CODE_AUDIO_TRACK_INIT_FAILED`. The corrected Media3/legacy-Exo players
carry diagnostic audio suspension across replacement-player setup and
selected-track restoration. An immediate-open Pro `.29` / stock `.175` rerun
recorded suspension before output rebuild, no audio-output error, and normal
end of the server fixture. This is a focused handoff gate, not a substitute
for the centered synchronized HDMI-path measurement recorded above.

Direct C920 framing was also retried at `640x480`, `800x600`, `960x720`, and
`1920x1080`. Those pre-fix rows all saw the same center crop because changing
sensor mode did not clear the graph-applied camera controls. The required fix
is the post-open control reset, not physical camera movement or a larger mode.

Legacy Exo Dynamic used real SageTV Push. Its live adjustment remained
explicitly deferred rather than rebuilding a server-owned Push datasource.
Use Pull for the current live-offset physical gate, or configure an offset
before starting Push playback and label that separate startup-only behavior.
