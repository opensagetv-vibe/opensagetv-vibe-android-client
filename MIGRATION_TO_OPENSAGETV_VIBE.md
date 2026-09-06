# Migration to OpenSageTV Vibe

This repository is the migration working copy of the Android MiniClient. The
known-good source at `../../SageTV-MiniClient-Dev` is a read-only comparison and
rollback source; it is not a build dependency and must not be modified, moved,
renamed, or deleted by this project.

## Provenance

- Source workspace: `SageTV-MiniClient-Dev`
- Source version: `0.5.75`
- Source Git state: no repository metadata was present
- Migration repository: `opensagetv-vibe-android-client`
- Exact imported baseline commit: `e770f9fb79606e2b21af3e83497bc5e74a652f0f`
- Imported files: 1,201
- Imported content comparison: 0 missing, 0 extra, 0 SHA-256 mismatches

The import includes both `source/dev` and the frozen `source/existing` tree,
Gradle wrappers, Android sources/resources, MCP implementation, tests, scripts,
documentation, patches, and every checked-in `.aar`, `.jar`, and `.so` needed
by the project. The first commit is intentionally a historyless snapshot because
the source directory had no usable `.git` history to migrate.

Generated build directories, Gradle caches, APKs, ADB state, logs, recordings,
screenshots, ZIP packages, local device configuration, and signing keys were
not imported. Empty `.gitkeep` markers were retained. A new ignored debug key
was generated only in this working repository for the baseline build.

## Baseline gate

The original and copied trees were validated before any Vibe changes. The
complete evidence is in [docs/BASELINE_VALIDATION.md](docs/BASELINE_VALIDATION.md).
The copied project passed the same validator and tests and produced a clean APK
in Docker before Phase 1 began.

## Migration phases

1. **Baseline preservation — complete.** Verify the original read-only, copy
   eligible content, prove byte equivalence, test, build, and commit the exact
   snapshot.
2. **Repository identity and operations — complete.** Adopt the
   `opensagetv-vibe-android-client` repository/image/container names, remove
   operational references to the old workspace path, make Docker commands
   reuse one named development container, and establish current documentation.
3. **Source-layout cleanup — complete.** Retain `source/existing` as the frozen
   comparison tree, define `source/dev` as the only active Android source, move
   release history into `CHANGELOG.md`, and keep durable diagnostic rules in
   stable documents rather than version-named notes.
4. **Unified build integration — complete locally.** This repository is a
   mounted component of `opensagetv-vibe-build-env`; normal build, test,
   validation, MCP, release-artifact, and provenance work uses the one
   `opensagetv-vibe-dev` container. Component-owned Dockerfile/Compose
   definitions were removed so no command can silently create a second image.
5. **Independent Android regression gates.** Run unit, validator, clean Gradle,
   APK identity, MCP protocol, install/launch, and physical player matrices.
   Device-backed installation and playback are explicit commissioning gates;
   they are not faked when no Android target is available.

## Compatibility decisions

- The frozen comparison baseline remains v0.5.75; the active checkout version
  is always read from `VERSION` and recorded in `CHANGELOG.md`.
- The Dev application ID remains `opensagetv.vibe.miniclient.debug` so it
  stays isolated from production and remains comparable to the known-good APK.
- No package under `jvl.sage.miniclient` may be installed, launched, stopped,
  uninstalled, cleared, or overwritten by project automation.
- Legacy Exo remains the default player while Media3 stays isolated until the
  device-backed release gates pass.
- Host paths are resolved from the root scripts and the sibling unified build
  environment; no legacy Windows-root environment variable is required.
- The original directory may be used for explicit comparison, but no build,
  test, MCP, or packaging command may require it.

## Git publication

The repository is local-only. No remote has been added and nothing has been
pushed. When the owner approves publication, the intended remote is
`https://github.com/opensagetv-vibe/opensagetv-vibe-android-client.git`.
Preserve the exact baseline import commit and all subsequent logical migration
commits when publishing.

Initial post-change equivalence evidence is consolidated in
[docs/BASELINE_VALIDATION.md](docs/BASELINE_VALIDATION.md). The migrated APK was
byte-identical to the pre-refactor baseline build.

## Intentional source replacement

Normal build/test/package operations must never import from the old workspace.
If an owner explicitly authorizes replacing the frozen or active source,
perform it as a reviewed migration: record the upstream repository/tag/commit,
stage the new tree inside this repository, compare required libraries and build
files, run validation and clean builds, update provenance and the full project
manifest, and commit it separately. Do not use the emergency bootstrap path as
an implicit network dependency.
