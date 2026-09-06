# Repository layout

```text
opensagetv-vibe-android-client/
  source/dev/          active Android client source; the only source to modify
  source/existing/     frozen v0.5.75 comparison source; never modify
  mcp/                 Python MCP server and protocol tests
  tests/               host/build/static regression tests
  scripts/             build, validation, MCP, and device-safe helpers
  docker/              entrypoint used inside the unified build container
  config/              tracked examples; local firetv.toml is ignored
  artifacts/           ignored APK/checksum output
  adb/                 ignored ADB identity and debug signing key
  logs/                 ignored diagnostic output
  recordings/          ignored optional media-test material
  screenshots/         ignored device evidence
  patches/             frozen reconstruction/comparison patches
```

`source/existing` is intentionally retained even though it duplicates much of
`source/dev`. It is the in-repository behavioral baseline and permits the
validator and baseline APK build to work without the old workspace.

The local `../../SageTV-MiniClient-Dev` directory is not part of the build
graph. It exists solely as an external emergency rollback and comparison copy.

## Docker identities

- Normal image: `opensagetv-vibe-build-env:u26-j11`
- Normal reusable container: `opensagetv-vibe-dev`
- Android mount: `/workspace/android-client`
- Shared Android Gradle cache: `/work/.gradle/android`

Normal `dev.sh`/`dev.cmd` commands delegate to the sibling unified build
environment and reuse that container. Bind-mounted source edits need no image
rebuild.

There is no component Dockerfile or Compose project. Toolchain/image ownership
belongs exclusively to the sibling `opensagetv-vibe-build-env` repository.
