# AGENTS.md — SageTV MiniClient Dev Rules

## Codex playback diagnostics

Before diagnosing playback startup, seek/skip, Comskip, buffering, Push/Pull, or transcoder behavior, read `TASK_CODEX.md`. It defines the required client/server evidence, attribution rules, and controlled-experiment workflow.

## Primary goal

Improve SageTV MiniClient playback startup, seek/skip reliability, and media-engine modernization using the user's real Fire TV Stick as the hardware-in-the-loop target.

## Environment

Use the Docker workflow for Android builds, Java, Python, ADB, APK inspection, MCP and Fire TV operations. The host should not need a separate Android toolchain.

`/workspace/source/dev` is the only Android source tree to modify. A full workspace may also contain `/workspace/source/existing` as the untouched source baseline. Compact AI handoff packages intentionally omit that duplicate tree and use `/workspace/source/FROZEN_BASELINE.sha256` for required frozen-file validation.

## Absolute safety rules

1. Never modify, stop, uninstall, clear, or replace the user's existing production SageTV MiniClient.
2. Dev automation targets `org.opensagetv.miniclient.dev.debug`.
3. Treat every package beginning `jvl.sage.miniclient` as protected.
4. Never run `pm clear` against a protected package.
5. Do not copy production private app data into Dev.
6. Do not delete recordings or alter the SageTV media library for tests.
7. Use the guarded Dev installer for APK deployment.

## Playback architecture rules — v0.4.0

1. Preserve the known-good legacy playback invariants. `BaseMediaPlayerImpl` is currently enforced byte-for-byte against `source/existing` when that tree is available, or against `source/FROZEN_BASELINE.sha256` in a compact AI handoff. Do not change frozen legacy behavior unless the user explicitly starts a separate legacy-backend experiment.
2. Legacy ExoPlayer remains the default backend until Media3 passes real Fire TV parity tests.
3. New Google playback work belongs in the isolated `video/media3` backend.
4. Keep Media3 independent of the legacy ExoPlayer 2.18 FFmpeg extension until a matching Media3 extension strategy is deliberately designed and tested.
5. IJK remains a legacy fallback; do not claim an upstream version update when none exists.
6. Do not reintroduce internal player telemetry callbacks as a group. Fire TV testing proved those hooks can block startup.
7. Prefer external MCP/ADB diagnostics for startup/seek investigation.
8. Firebase/Crashlytics must remain removed.

## Build rules

- Dev/Media3: JDK 17, AGP 8.13.2, Gradle 8.13, SDK 36, Build Tools 36.0.0.
- Untouched baseline: bundled JDK 8 with its original Gradle 6.1.1/AGP 4.0.2 and SDK 29/Build Tools 29.0.2.
- v0.4.0 Docker changes require `./dev.sh image` before building.
- `./dev.sh build` must stay clean/deterministic.
- `./dev.sh install` must uninstall only the Dev package before fresh install.

## Required validation before hardware testing

```bash
./dev.sh test
./dev.sh validate
./dev.sh build
./dev.sh install
./dev.sh launch
```

For a new Docker/toolchain revision, run `./dev.sh image` first.

## Player validation order

1. Leave default at ExoPlayer Legacy and verify startup.
2. Select Media3 and verify the same recording.
3. Select IJKPlayer and verify the same recording.
4. Select GSYVideoPlayer and verify the same recording with its dedicated profile.
4. Only after startup parity, run seek/skip regression testing.

Minimum seek sequence: `+30,+30,-10,+60,-30`, repeated with a settling delay.

A changed playback position alone does not prove video recovery. Use visible output plus external diagnostics.

## Failure handling

Before modifying code after a failure:

1. capture logcat/MediaCodec/surface/focus/screenshot or recording diagnostics
2. record backend, media, action and visible symptom
3. isolate changes to the failing backend
4. preserve the known-good legacy baseline
5. rerun the exact case after the smallest fix
