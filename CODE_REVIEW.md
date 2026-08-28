# SageTV MiniClient Code Review — Phase 0

Review date: 2026-08-25

Authoritative source reviewed: user-supplied `sagetv-miniclient-master.zip` (SHA-256 `f45c930f3810b56d88f751120ca6fa692ae9b01dd2adce62a3d926b2ab336a79`). The source corresponds to the v1.14.0-era codebase.

## Executive findings

The current MiniClient already has a useful player abstraction. `BaseMediaPlayerImpl` owns common SageTV player behavior, while `Exo2MediaPlayerImpl` and `IJKMediaPlayerImpl` implement the two playback engines. The first modernization step should therefore **not** be a player rewrite. Instrument the existing abstraction, establish reproducible Fire TV tests, and then change one player implementation at a time.

The Android build is old but internally coherent: Android Gradle Plugin 4.0.2, compile SDK 29, target SDK 30, build tools 29.0.2, NDK 21.0.6113669, ExoPlayer 2.18.1, and local IJKPlayer 0.8.8-SNAPSHOT AARs. Updating all build tooling and media engines at once would make regressions difficult to isolate.

Firebase was wired into Gradle, runtime logging/application startup, settings preferences/resources, manifest metadata, and Jenkins credential handling. The exact source is now available and Phase 0 removes all identified active Firebase/Crashlytics/Google Services wiring from the Dev tree, including the orphan settings/UI entries. Local SLF4J logging is preserved.

## Build structure observed

Root modules currently included by `settings.gradle`:

- `core`
- `android-shared`
- `android-tv`

Other repository directories include historical player/build support such as `exoplayer`, `ijkplayer`, `desktop`, `pipeline`, `playstore`, and `libs`.

The Android TV module is the application. The shared Android module contains the common activities, player implementations, settings, and most Android code.

## Current application identity

The reviewed `android-tv/build.gradle` uses an application ID under:

`jvl.sage.miniclient.android.tv.debug`

and its debug build applies an additional `.debug` suffix. The current project also has separate debug/release app-name resources.

For this project, only the **Android application ID** is changed; the Java package hierarchy is left intact to avoid a large namespace refactor.

New base application ID:

`org.opensagetv.miniclient.dev`

Expected debug package:

`org.opensagetv.miniclient.dev.debug`

Visible names:

- Release/dev: `SageTV MiniClient Dev`
- Debug: `SageTV MiniClient Dev Debug`

This gives Android separate app data and installation identity from the existing MiniClient.

## Firebase / Crashlytics findings

Active wiring was found in these areas:

1. Root `build.gradle`
   - Google Services Gradle plugin classpath.
   - Firebase Crashlytics Gradle plugin classpath.

2. `android-tv/build.gradle`
   - Google Services plugin.
   - Firebase Crashlytics plugin.
   - Firebase BoM.
   - Crashlytics dependency.
   - Analytics dependency.

3. `android-shared/build.gradle`
   - Firebase Crashlytics dependency.

4. `android-shared/.../MiniclientApplication.java`
   - `FirebaseCrashlytics` import.
   - Crashlytics collection enable call.
   - Crashlytics user ID call.

5. Shared Android manifest
   - Crashlytics collection metadata.

6. Android TV Firebase configuration
   - `google-services.json` is removed when present because the development build no longer uses Google Services/Firebase.

Phase 0 removes these dependencies/runtime calls plus the Firebase preference constants/getters, settings UI/resources, Jenkins Google Services credential step, and Firebase-specific ignore entries. A fail-closed active-source scan confirms no active Firebase/Crashlytics/Google Services references remain.

## ExoPlayer findings

`Exo2MediaPlayerImpl` is based on ExoPlayer 2.x, currently 2.18.1. Important observations for the seek/startup work:

- Startup builds a `ProgressiveMediaSource`, optionally seeks to a requested starting position, sets the media source, calls `prepare()`, attaches the video surface, and enables playback.
- The implementation contains player-error retry behavior that can change position and re-prepare the player.
- The seek implementation calls `player.seekTo(timeInMillis)` and then loops briefly waiting for the reported media position to approach the target.
- A dedicated `onRenderedFirstFrame` telemetry path was not present in the reviewed implementation.

### Why this matters

The current seek call can report a target position without proving that the first post-seek video frame was actually rendered. This matches the type of failure we need to distinguish on Fire TV: position/audio can recover while video output stalls.

The first ExoPlayer change should therefore be **instrumentation**, not a seek algorithm change.

Recommended telemetry:

- player creation timestamp
- source prepare timestamp
- `STATE_BUFFERING` / `STATE_READY` transitions
- first rendered video frame timestamp
- video size
- decoder name / initialization
- rendered and dropped frame counters where exposed
- seek request position and target
- position discontinuity / seek completion
- first rendered frame after seek
- player errors and retry path

## IJKPlayer findings

`IJKMediaPlayerImpl` has materially different timing/seek behavior from ExoPlayer and should remain a separate backend during initial work.

Observed behavior includes:

- `preSeekPos` handling before the player becomes ready.
- Immediate seek in appropriate player states.
- Special handling/comments around TS/PS timing behavior and PTS clock/rollover concerns.
- `onPrepared` starts playback and applies deferred pull-mode seeking.

Do not mechanically make IJK seek behavior match ExoPlayer. The same black-box regression cases should be run against both implementations first.

## Base player findings

`BaseMediaPlayerImpl` already centralizes behavior useful to a common telemetry layer.

One important semantic is that media time can intentionally return zero in states such as player-not-ready/flushed/seeking because SageTV expects that behavior. Old comments also indicate that seek state checks were adjusted because IJK could report problematic values.

Therefore telemetry should **observe** raw backend state separately from SageTV-facing compatibility values. Do not change `getMediaTimeMillis()` semantics merely to make tests easier.

## Recommended refactor boundary

### Safe Phase 0 changes — included in this project

- New application ID and visible app name.
- Existing Java package namespace preserved.
- Firebase/Crashlytics/Analytics active build/runtime wiring removed.
- Dockerized legacy Android build environment.
- Python MCP server controlling the configured Fire TV over ADB.
- Package-operation safeguards so the MCP cannot intentionally manage the old upstream package namespace.
- Diagnostic artifact collection.
- Task list, questions, Codex rules, handoff/runbook.

### Phase 1 player changes — deliberately deferred until baseline

- Add dependency-free player telemetry model/logger.
- Instrument `BaseMediaPlayerImpl` without changing return semantics.
- Instrument ExoPlayer first-frame, state, seek and decoder events.
- Instrument equivalent IJK lifecycle/seek events.
- Define structured `SAGE_DIAG` log records readable via MCP/logcat.
- Create deterministic startup/seek regression scenarios using a known recording.

### Later changes

- Reproduce/fix startup and rapid-skip defects on current players.
- Update ExoPlayer in controlled steps and migrate to AndroidX Media3.
- Evaluate whether IJKPlayer still provides necessary codec/container coverage before attempting a risky IJK modernization or removal.
- Modernize Gradle/SDK independently from playback changes when practical.

## Sources reviewed

- https://github.com/OpenSageTV/sagetv-miniclient
- Upstream `build.gradle`
- `android-tv/build.gradle`
- `android-shared/build.gradle`
- `android-shared/src/main/AndroidManifest.xml`
- `MiniclientApplication.java`
- `BaseMediaPlayerImpl.java`
- `Exo2MediaPlayerImpl.java`
- `IJKMediaPlayerImpl.java`
- Upstream release/changelog information through v1.14.0

The bundled `source/existing` tree is the authoritative untouched comparison source. `source/SOURCE_IMPORT.json` records the exact uploaded ZIP hash. Public GitHub bootstrap is retained only as a fallback recovery path.
