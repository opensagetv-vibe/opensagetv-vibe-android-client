# Docker Workflow — v0.4.0

All Android/JDK/ADB/Python/MCP build and deployment work remains inside Docker.

## Rebuild image after v0.4.0 update

The player modernization changes the Android build toolchain, so run once after extracting the update:

```bash
./dev.sh image
```

## Dual toolchain

The single Dev Docker image intentionally contains two Java/Android SDK toolchains.

### Dev / Media3

- JDK 17
- AGP 8.13.2
- Gradle 8.13 wrapper
- platform 36
- Build Tools 36.0.0
- NDK 21.0.6113669

Normal `./dev.sh build` uses this toolchain.

### Untouched source/existing

- JDK 8 at `/opt/java/jdk8`
- source's original Gradle 6.1.1 / AGP 4.0.2
- platform 29
- Build Tools 29.0.2
- NDK 21.0.6113669

`build-existing` prepends `/opt/java/jdk8/bin` and sets `JAVA_HOME=/opt/java/jdk8`, preserving the old baseline even though the container default is Java 17.

## Normal Dev flow

```bash
./dev.sh test
./dev.sh validate
./dev.sh build
./dev.sh install
./dev.sh launch
```

`build` always performs a clean Gradle build. `install` verifies the Dev package, uninstalls only `org.opensagetv.miniclient.dev.debug`, then performs a fresh install.

## Untouched baseline build

```bash
./compile_existing_app.sh
```

This never installs the resulting APK.

## Preflight

```bash
./dev.sh preflight
```

The preflight reports the default Dev Java runtime, the bundled legacy JDK 8, old/new Android SDK components, ADB tooling, MCP runtime, source trees and Fire TV connectivity.
