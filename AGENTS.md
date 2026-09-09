# OpenSageTV Vibe Android Client contributor rules

These instructions apply to humans, Codex, and other AI tools working in this
repository.

## Read first

Before changing code, read:

1. `README.md`
2. `HANDOFF.md`
3. `TASKS.md`
4. `docs/PLAYBACK_DIAGNOSTICS.md` for playback work
5. `docs/TEST_ENVIRONMENT.md` before commissioning or physical tests
6. `docs/COMMISSIONING.md` before creating a new test environment
7. the relevant architecture/workflow document

`TASKS.md` is the only authoritative subproject backlog. Do not create
`TASK_CODEX.md`, prompt-specific task files, review-note backlogs, or duplicate
task lists. Completed work is removed from `TASKS.md` and recorded in
`CHANGELOG.md`/`HANDOFF.md`.

When a task's acceptance criteria pass, update task tracking in the same
change: remove it from this repository's `TASKS.md`, remove/update the mirrored
entry in the workspace-wide `task.md` when applicable, and record the result
and evidence in `CHANGELOG.md` and `HANDOFF.md`. Do not leave completed
checkboxes or wait for a later documentation pass.

## Workspace boundary

- Modify only `opensagetv-vibe-android-client` unless the user explicitly puts
  another repository in scope.
- Before asking the user for a shared environment, commissioning, artifact, or
  release fact, search the workspace `task.md` and sibling
  `opensagetv-vibe-*` README/CHANGELOG/HANDOFF/config files read-only. Record
  the confirmed fact in this repository's durable handoff when Android work
  depends on it; never guess from an old Android-only default.
- Never modify, move, rename, delete, or depend on
  `../../SageTV-MiniClient-Dev`. It is the known-good rollback/comparison source.
- `source/dev` is active. `source/existing` is a frozen comparison tree.
- Preserve checked-in Gradle wrappers, MCP tooling, tests, scripts, required
  `.aar`/`.jar`/`.so` files, and baseline manifests.

## Documentation and release policy

- `CHANGELOG.md` is the only version-history document.
- `WORKFLOW.md` defines the root interface shared with every Vibe repository;
  preserve its command semantics and `artifacts/downloads` package location.
- Markdown retained below `source/existing` is frozen comparison material.
  Upstream/source documentation below `source/dev` is application source, not
  OpenSageTV Vibe release tracking; never add Vibe release entries there.
- `HANDOFF.md` contains current state and the exact resume point, not a second
  chronological changelog.
- `TASKS.md` contains only active unchecked tasks.
- Stable operational guidance belongs in README, AGENTS, `docs/`, or component
  READMEs; update those files in place.
- Do not create `UPDATE_v*.txt`, version-named Markdown notes, dated matrix
  reviews, AI start prompts, or per-tool handoff documents.
- Update `VERSION`, `release.properties`, `CHANGELOG.md`, `HANDOFF.md`, affected
  durable docs, tests, and `PROJECT_MANIFEST.sha256` together.
- `release.properties` must contain exactly one matching `VERSION=x.y.z` and
  one `REQUIRES_BUILD=true|false`. Use `true` whenever APK-packaged source,
  resources, manifests, dependencies, or build configuration changed.

## Build and update workflow

Normal Linux/WSL commands use `./dev.sh`; native Windows uses `dev.cmd`
(which invokes the repository's `dev.ps1` without changing machine policy).
Both must select the sibling unified build environment and reuse
`opensagetv-vibe-dev`. This repository must not define or build a second Android
development image/container.

Use `./update.sh` or `update.cmd` for incremental packages. Before extraction,
the runner must reject corrupt ZIPs, unsafe/duplicate paths, missing/mismatched
release metadata, incomplete manifests, payload hash mismatches, and baseline
drift. Never use an update ZIP to hide an unvalidated overwrite.

Before delivery:

```text
dev test
dev validate
dev build (when REQUIRES_BUILD=true)
PROJECT_MANIFEST.sha256 regenerated and checked
git diff --check
```

Record skipped device tests as SKIPPED, never PASS.

## Android/package safety

- Automation may mutate only the configured development package, normally
  `opensagetv.vibe.miniclient.debug`.
- Refuse `jvl.sage.miniclient*` package install, launch, stop, clear, or remove.
- Use a non-production debug key only.
- A fresh install/Clear Data requires manual first-time setup before automation.
- Automated runs default to `DEV001`; normal interactive startup must preserve
  generated/persisted client-ID behavior.

## Playback safety

- Treat an unmodified stock SageTV server as the primary compatibility target
  and first physical test gate. Resolve playback and protocol issues in the
  Android client whenever the stock protocol provides enough information.
  Change Sage.jar, FFmpeg/MIM, or another server component only as a last
  resort for behavior that stock SageTV cannot express; keep every such
  extension optional, negotiated, and backed by a safe stock fallback.
- Legacy ExoPlayer remains the default until physical commissioning says
  otherwise.
- Preserve Push/Pull/Fixed behavior and do not merge their ownership semantics.
- Keep Media3, Legacy Exo, IJK, and GSY engines independently testable.
- Every optional client/server extension must be explicitly capability- or
  version-negotiated. `Auto` must preserve or restore the established stock
  path; an unsupported explicit mode must fail closed without damaging the
  MiniPlayer session and must provide bounded user feedback plus MCP/log
  diagnostics. Re-run old/current Core, missing/old/current MIM, and FFmpeg
  startup-failure gates when a change touches those boundaries.
- Do not alter `BaseMediaPlayerImpl` or redesign playback while doing structural
  Android framework cleanup unless explicitly authorized.
- No continuous/background player instrumentation. Bounded debug snapshots and
  existing callback event traps are permitted only when they do not alter
  startup or rendering.
- A watchdog is an observation budget, not fault attribution.
- Use single-variable experiments, capture before/after evidence, and preserve
  failure artifacts as specified by `docs/PLAYBACK_DIAGNOSTICS.md`.

## Change discipline

- Preserve behavior first; split/refactor giant classes before redesigning them.
- Keep preference keys/defaults and JNI/native ABI stable unless a documented
  compatibility change is required.
- Put intentional tracked-file removals in stable `release-deletions.lst` so
  incremental updates reproduce the checkout. Never use globs or directories.
- Update or add tests with every behavior/workflow change.
- Do not bulk-upgrade dependencies or target behavior in the same patch as a
  player fix.
- Do not delete user settings, device captures, or unrelated workspace data.
