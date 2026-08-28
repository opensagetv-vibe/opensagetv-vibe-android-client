# Repository layout

```text
opensagetv-vibe-android-client/
  source/dev/          active Android client source; the only source to modify
  source/existing/     frozen v0.5.75 comparison source; never modify
  mcp/                 Python MCP server and protocol tests
  tests/               host/build/static regression tests
  scripts/             build, validation, MCP, and device-safe helpers
  docker/              reproducible Android/JDK/ADB/Python toolchain
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

- Image: `opensagetv-vibe-android-client-dev:jammy-j17`
- Reusable container: `opensagetv-vibe-android-dev`
- Gradle cache: `opensagetv-vibe-android-gradle-cache`
- Bind mount: this repository to `/workspace`

Normal commands use `docker compose exec` against the same named container.
They do not create a phase container per invocation. `./dev.sh image` is needed
when the Dockerfile changes; bind-mounted source edits need no image rebuild.
