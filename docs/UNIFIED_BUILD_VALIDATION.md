# Unified Ubuntu 26 build validation

Validated on 2026-08-28 with Windows Docker Desktop and the shared
`opensagetv-vibe-build-env:u26-j11` image.

## Architecture

- Development image: `opensagetv-vibe-build-env:u26-j11`
- Image ID: `sha256:fcfc91e941a7eadf4d2c3e670cc2761e8ac15e02b480e3dedb179dcd3820dd05`
- Reusable container: `opensagetv-vibe-dev`
- Shared cache: `opensagetv-vibe-gradle-cache` under `/work/.gradle/android`
- Android repository mount: `/workspace/android-client`
- Default image Java: OpenJDK 11.0.32
- Active Android Java: Temurin 17.0.20 under `/opt/java/jdk17`
- Frozen baseline Java: Temurin 8u502 under `/opt/java/jdk8`
- Android SDK: platforms/build tools 29 and 36
- Android NDK: 21.0.6113669
- Android platform tools: 37.0.1
- Python MCP: 2.1.1 in an isolated virtual environment

Java 17 and Java 8 are selected only for Android commands. `JAVA_HOME` and
`JDK_HOME` remain OpenJDK 11 globally for SageTV Core and server work.

## Results

| Gate | Result |
|---|---|
| Unified environment/Java isolation | PASS |
| Project manifest (1,209 files) | PASS |
| Scaffold/static tests (151) | PASS |
| MCP tests (35) | PASS |
| Full project validator | PASS |
| Clean Gradle build (60 tasks) | PASS |
| APK present and signed with Dev key | PASS |
| Phase 1 APK byte equivalence | PASS |
| Device install/launch/playback | SKIPPED — explicit commissioning gate |

APK:

```text
artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk
SHA-256 839113f460fed5e6f37ec244ea6a2fbc574c32e5f9b131085c95a349bb364a69
```

The hash is identical to the pre-refactor and Phase 1 standalone-image APK.
The build-environment migration therefore changed toolchain ownership and
orchestration without changing the generated application.

## Commands

From the sibling build-environment repository:

```bash
./opensagetv-vibe-dev.sh android-info
./opensagetv-vibe-dev.sh android-all
```

From this repository, `./dev.sh test`, `validate`, `build`, and MCP/device
commands automatically use the sibling unified environment when it is present.
Set `OPENSAGETV_VIBE_ANDROID_STANDALONE=true` only for the isolated-checkout
fallback.
