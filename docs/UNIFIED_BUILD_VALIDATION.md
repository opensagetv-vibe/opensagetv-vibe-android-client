# Unified Android build validation

The baseline build was validated on 2026-08-28 from Windows Docker Desktop
through the repository's `dev.cmd` entry point. The unified-graphics A/B gate
below was rerun on 2026-09-16 after the format-256 renderer continuity fix.

## Environment

- Unified image: `opensagetv-vibe-build-env:u26-j11`
- Image ID: `sha256:922da6854807a919ddf4467542e46903bda00d6f787fe6727a8c237c0216d39d`
- Build-environment version: `u26-j11-release-v4`
- Running container image ID: identical to the tagged image ID
- Reusable container: `opensagetv-vibe-dev`
- Android workspace: `/workspace/android-client`
- Default unified Java: OpenJDK 11
- Android Java: Temurin 17 at `/opt/java/jdk17`
- Frozen comparison Java: Temurin 8 at `/opt/java/jdk8`
- Android compile/build tools: 36 / 36.0.0

The Android workflow has no component-owned Dockerfile or Compose image to
start. It resolves the sibling checkout from the launcher location and reuses
the currently installed unified image/container.

## Results

| Gate | Result |
|---|---|
| Windows CMD/PowerShell to WSL path handoff | PASS |
| Independent committed sibling checkout and automatic rebind | PASS |
| Unified image selected and reused | PASS |
| Full project manifest | PASS (1,449 files, regenerated after source/docs changes) |
| Scaffold/static tests | PASS (552) |
| MCP tests | PASS (86) |
| Full source validator | PASS |
| Clean Gradle build | PASS (60/60 tasks) |
| Dev package/min/target/compile SDK | PASS (`opensagetv.vibe.miniclient.debug`, 23/36/36) |
| FileProvider/permissions/exported component inspection | PASS with follow-up audits in `TASKS.md` |
| Device install/launch/playback | PASS - non-Pro Fire TV `.25` against stock `.175` (A/B below) |

Latest clean APK after log-sharing hardening:

```text
artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk
SHA-256 0f827b7b3955594e67fef4a763b5644c8fca5a555bfc81b63262b7bfe2769489
```

## Unified-graphics physical A/B gate (2026-09-16)

The debug APK above was installed in place on the commissioned non-Pro Fire
TV (`192.168.10.25:5555`, AFTMM/API 25) without clearing application data.
Both runs used stock SageTV `.175` (`192.168.10.175:31099`), the stock SageX
Watch control, Media3 Pull, hardware video decoding, the GDX renderer, and the
`Meet the Press` recording. The setting was changed through the debug/MCP
control and the client was relaunched so the next connection renegotiated it.

| Setting | Result |
|---|---|
| `unified_graphics_surfaces=false` | PASS: playback, audio, fullscreen, seek, pause/resume, stop/teardown, and reconnect; no player error or crash |
| `unified_graphics_surfaces=true` | PASS: same gates; `unifiedGraphicsSurfaces=true`, one video/audio decoder init with zero releases during seek, advancing video/audio, valid surface, no retries or error state |
| MCP/debug control | PASS: `dev_set_unified_graphics` and `dev_set_player_config` both report the persisted value and `unifiedGraphicsAppliesNextConnection=true` |

The ordinary title does not cause stock Core to send a format-256 image plane;
that command is used for eligible high-resolution UI/video-surface operations.
The format-256 Y/UV decoder and subsequent-row texture updates are therefore
covered by Core/Android unit tests, while the physical run proves the opt-in
handshake does not regress normal stock playback. A video-plane handle from an
HD300 is intentionally logged and follows Android's normal `SurfaceView`
rectangle path because a MediaCodec surface cannot safely compose an HD300
handle directly.

## Commands

Windows:

```bat
dev.cmd test
dev.cmd validate
dev.cmd build
```

Linux/WSL:

```bash
./dev.sh test
./dev.sh validate
./dev.sh build
```

These commands must resolve to `/workspace/android-client` in
`opensagetv-vibe-dev`.

The same final gate also passed from the unified build-environment root with
`opensagetv-vibe-dev.ps1 android-all`. Dependency resolution is guarded by the
Gradle distribution SHA-256, per-project lockfiles,
`gradle/verification-metadata.xml`, the exact Python lock installed by the
image, and an exact Android platform-tools revision check.
