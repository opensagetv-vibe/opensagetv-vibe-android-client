# Phase 1 validation

Date: 2026-08-28

| Gate | Result |
|---|---|
| Compose configuration | PASS |
| Shell syntax | PASS |
| Project manifest | PASS, 1,209 files |
| Scaffold/static tests | PASS, 150 tests |
| MCP tests | PASS, 35 tests |
| Full source validator | PASS |
| Clean Gradle build | PASS, 60 tasks |
| Dev APK identity isolation | PASS |
| Reusable-container invariant | PASS, one named container |
| Original-project operational dependency | PASS, none |
| Device install/playback | SKIPPED — no device operation authorized for structural migration |

## Reproducible environment

- Image: `opensagetv-vibe-android-client-dev:jammy-j17`
- Image ID: `sha256:f8c7f4275375e0a36160cc5f1c3a6de0d64663ba9755d8e969caf67ea6922b18`
- Platform: `linux/amd64`
- Base reported in image: Ubuntu 22.04.5 LTS (Jammy)
- Primary Java: Eclipse Temurin OpenJDK 17.0.20+8
- Frozen baseline Java: Eclipse Temurin OpenJDK 8u502
- Temurin 17 base digest: `sha256:400014962ad7224461f945bb1cc3d7d5a1927ce15b8245b72d9cedcda554cd2a`
- Temurin 8 base digest: `sha256:7cb1137d4a02aeb7ca85faae69c5f0720703936cbfb0b4af21067c73174f9b5e`
- Reusable container: `opensagetv-vibe-android-dev`
- Gradle cache: `opensagetv-vibe-android-gradle-cache`

Ubuntu 26.04/OpenJDK 11 is the SageTV server and unified server-build baseline.
This Android toolchain is accurately labeled Jammy/JDK 17 and retains JDK 8
only to compile the frozen upstream comparison tree. It does not claim to be an
Ubuntu 26 image.

## Artifact equivalence

The migrated clean build produced:

```text
artifacts/firetv/OpenSageTV-Vibe-Android-Client-debug.apk
SHA-256 839113f460fed5e6f37ec244ea6a2fbc574c32e5f9b131085c95a349bb364a69
```

That hash is identical to the pre-refactor copied-project APK. Phase 1 therefore
changed repository/build identities and orchestration without changing the
compiled Android application.
