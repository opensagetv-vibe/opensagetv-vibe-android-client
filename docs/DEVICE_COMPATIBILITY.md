# Android device compatibility

Last verified: 2026-09-13

## Install boundary

The assembled Vibe APK declares `minSdkVersion=23`, `targetSdkVersion=36`, and
contains native libraries for both `armeabi-v7a` and `arm64-v8a`. This was read
from the built APK with Android `apkanalyzer` and `aapt`, not inferred from the
Gradle source.

Consequences:

- Android 6.0/API 23 and newer can pass the OS version/ABI install gate.
- Android 5.0/5.1 (API 21/22) cannot install this APK. A Galaxy Tab reporting
  that the APK cannot be opened is therefore expected when it runs Android 5.
- Android 8.0/API 26 is inside the supported install range. Intermittent video
  cadence or silent Dolby Digital on that device is a playback/device-routing
  issue and must not be conflated with the Android 5 install boundary.

Lowering the manifest to API 21 without building and physically testing the
complete native dependency set would create an unverified APK. Vibe therefore
documents API 23 as the current boundary rather than claiming Android 5
compatibility based only on compilation.

The boundary is also enforced by the current playback dependencies. The
resolved manifests for Media3 1.11.0 and GSYVideoPlayer 13.1.0 declare
`minSdkVersion=23`; Legacy ExoPlayer 2.18.1 remains compatible with older API
levels. The project setting first arrived with the imported Vibe v0.5.75
baseline, whereas upstream MiniClient used API 21, but the modern player stack
now makes API 23 a concrete dependency requirement. An Android 5 build would
need a separately tested variant that excludes/replaces the API-23 player
modules, not merely a manifest override.

## What to collect for cadence or silent AC-3

From the long-press player menu, enable tracing, run **Test Current Video**
while the affected recording is loaded, then export the redacted diagnostic
bundle. The bundle records:

- Android SDK, manufacturer, model, product, and supported ABIs;
- selected player/delegate, datasource, video/audio MIME and decoder names;
- hardware/software decoder classification and decoder attempts/failures;
- rendered/dropped/skipped video output and frame/cadence observations;
- selected audio track, channels, passthrough state, AudioTrack state/playback
  head, audio queued/rendered/dropped output, underruns, and output failures;
- buffer/network activity, timestamps, seek/flush/transition events, and the
  bounded current-video cadence/pause/resume/seek test results.

The default is decoded PCM for AC-3/E-AC-3/DTS. Encoded passthrough is opt-in
for a verified external receiver. This avoids devices that advertise an
encoded output route which accepts writes but produces silence. Media3 also
ships the matching FFmpeg audio extension as a fallback when the platform has
no usable decoder; video remains on the selected hardware MediaCodec path.

Without the affected tablet or its diagnostic bundle, DEVICE-001 can establish
the install boundary and collection contract but cannot honestly claim the
reported cadence/audio fault is physically resolved.
