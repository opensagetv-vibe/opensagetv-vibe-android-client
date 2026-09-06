# Unified Android build validation

Validated on 2026-08-28 from Windows Docker Desktop through the repository's
`dev.cmd` entry point.

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
| Full project manifest | PASS (1,094 files before final documentation pass) |
| Scaffold/static tests | PASS (174) |
| MCP tests | PASS (35) |
| Full source validator | PASS |
| Clean Gradle build | PASS (60/60 tasks) |
| Dev package/min/target/compile SDK | PASS (`opensagetv.vibe.miniclient.debug`, 23/36/36) |
| FileProvider/permissions/exported component inspection | PASS with follow-up audits in `TASKS.md` |
| Device install/launch/playback | SKIPPED - physical commissioning remains open |

Latest clean APK after log-sharing hardening:

```text
artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk
SHA-256 60e1d19ab15968ef48e24691cfd14f8998ce0bc6e6e8bda960f6d65e8d8aa668
```

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
