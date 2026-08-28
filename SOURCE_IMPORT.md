# Exact Source Import

The exact MiniClient repository supplied by the user is already bundled in this full workspace.

Archive SHA-256:

`f45c930f3810b56d88f751120ca6fa692ae9b01dd2adce62a3d926b2ab336a79`

Current trees:

- `/workspace/source/existing` — untouched source content from the supplied ZIP
- `/workspace/source/dev` — copied from the same ZIP, then Dev identity/Firebase refactor applied
- `/workspace/source/SOURCE_IMPORT.json` — provenance metadata

Normal setup does **not** require a source import.

## Replacing the source intentionally

If a newer/different repository ZIP is supplied later:

```bash
./dev.sh import-source /workspace/incoming/<repo>.zip --replace
```

The importer validates ZIP paths, requires exactly one SageTV MiniClient repository root, hashes the archive, recreates both source trees, writes provenance beside them, and applies the complete Dev isolation/Firebase removal transform to `source/dev`.

The operation fails if active Firebase/Crashlytics/Google Services references remain after the transform.

Playback behavior is not modified by the import/refactor step.

## v0.4.0 modernization note

`import-source` intentionally applies only the safe Dev identity/Firebase transform. It does **not** blindly transplant the v0.4.0 player factory, Media3 backend, fixed playback baseline guards, or modern Gradle/Docker changes into an unknown newer source layout.

After an intentional source replacement, `./dev.sh validate` is expected to fail until the v0.4.0 player-modernization changes are reviewed and deliberately ported. Internal player telemetry must remain disabled unless explicitly revalidated one hook at a time. This is fail-closed by design.


## v0.5.0 runtime note

The imported `source/existing` tree remains untouched. `source/dev` now intentionally replaces the active old local IJK 0.8.8 runtime dependencies with GSYVideoPlayer 13.1.0 `gsyvideoplayer-java` + `gsyvideoplayer-ex_so`; the original IJK version marker is retained only as source provenance.
