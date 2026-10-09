# OpenSageTV Vibe Android Client contributor rules

## Task fix and server-boundary policy

Apply this policy to every task workflow, including dependency fixes across
Vibe repositories. Fix and test necessary plugins and update the test-server
plugin without asking again solely for repository-boundary approval.

Prefer the Android client, then a stock-compatible plugin. Change non-stock
`.232` Core only for a proven production defect that neither can correct;
document the API gap and alternatives, keep optional negotiation and safe
stock/older-client fallback, and run affected compatibility tests. Never patch
Core merely to simplify testing.

Stock `.175` installation changes are limited to plugin installation/update.
Do not modify its stock Sage.jar, stock FFmpeg, Core binaries, or server
installation/configuration files. Preserve user settings, recordings and
unrelated clients; reversible supported SageTV playback APIs remain allowed.

Non-stock `.232` restarts are authorized for task updates without asking
again; coordinate them with active test guards and preserve data/settings.
  Always ask the user before restarting stock `.175`, even when it appears idle,
  unless an explicit user-granted bounded restart window is active. Record
  its UTC expiry in the task/handoff, and check expiry and revocation before
  every restart. After expiry or revocation, ask again; stock files stay protected.

Update owning TASKS.md, linked dependencies and the workspace suggested order
as work changes; move completed checkoffs into the checklist change ledger.
Test only affected gates, preserve unrelated completed matrices, and do not
stop independent authorized work for a status question or a dependency-only
permission request. Unrelated work, publication, destructive actions and
interruption of recordings/other users still require their own authority.

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

`TASKS.md` is the only authoritative subproject backlog and durable master
checklist. Do not create `TASK_CODEX.md`, prompt-specific task files,
review-note backlogs, or duplicate task lists. Retain completed work in its
`## Checklist change ledger` with a checked box and stable task ID; also record
release-relevant results in `CHANGELOG.md`/`HANDOFF.md`.

When a task's acceptance criteria pass, update task tracking in the same
change: change its existing box to `[x]` without renumbering or deleting it,
update any mirrored entry in the workspace-wide `task.md`, add a dated entry to
the `TASKS.md` change ledger, and record the result and evidence in
`CHANGELOG.md` and `HANDOFF.md`. Immediately before commit, move that checked
item from its active section into the ledger. New work receives a new ID.
Reordering retains the original ID and is recorded in the ledger. Remove a task
only when the user explicitly requests removal, and preserve that decision in
the ledger.

Whenever an Android task is added, completed, removed, deferred, unblocked, or
changes dependencies, review the **Suggested Android execution order** in the
workspace-wide `../../task.md`. Update it only when the dependency graph or
matrix timing actually changes; keep stable IDs and keep detailed acceptance
criteria authoritative in this repository's `TASKS.md`. During implementation,
run focused affected gates. Run broad pre-release matrices only after all
release-bound client changes are stable, then publish, then run the affected-
only post-release device matrix. A late shared-player change reruns only the
rows and final matrix portion whose evidence it invalidates, never every
completed matrix by default.

In the same task-status edit, increment the checklist revision, add its ledger
entry, reconcile both suggested orders, and update both order-review stamps to
that revision. Remove completed task IDs from the order; when a sub-gate is
complete but its parent remains open, describe the next unfinished gate rather
than the completed prerequisite. Run `python scripts/task_order_check.py` before
commit. The validator fails on a stale revision or completed ordered task.

## Workspace boundary

- Normally modify only `opensagetv-vibe-android-client`. The user's standing
  rule also authorizes bounded fixes/tests in a Vibe plugin repository when
  its proven defect blocks the active Android task, including plugin updates
  on the test server. Do not ask again solely because the fix crosses that
  repository boundary. Follow the workspace plugin-defect rule; stock `.175`
  changes are limited to plugin installation/update, never stock Core or
  server installation/configuration file changes. Unrelated work stays out
  of scope; preserve user settings and test only the affected gates.
  The user also permits necessary Core/non-stock `.232` fixes after proving
  that client/plugin alternatives cannot express the production correction.
  Document that gap, preserve optional negotiation and stock/older-client
  fallback, and test affected compatibility. This never permits changing
  stock `.175` or using Core modifications only to make testing easier.
- Never create release downloads, extracted verification trees, or independent
  Git clones as sibling directories under `C:\TMP_SAGETV_DOCKER\projects`.
  Put disposable work under the workspace-level `artifacts/temp` directory
  (or a system-created temporary directory) and clean it in `finally`/trap
  handling. The `projects` directory is reserved for active project roots.
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

## Artifact retirement after testing

- Follow `WORKFLOW.md`'s capture and artifact retirement procedure after every
  test session and before commit/release. Move no-longer-needed files to the
  workspace-root `deleteme/`, preserving workspace-relative paths; do not
  automatically delete that folder.
- Use workspace `artifacts/temp/` for disposable staging and project
  `artifacts/active/<stable-task-ID>/` for current captures/logs with explicit
  output paths. Do not create new cleanup-quarantine folders or loose root
  screenshots. At completion/correction, retire raw logs and snapshots too;
  keep only the written result and an optional compact final report in
  `artifacts/results/<stable-task-ID>/`.
- Completed gates and corrected failures retain their written result,
  build/device/server/settings and meaningful observations, not every raw
  screenshot/video forever. Preserve only raw evidence required by a release,
  published documentation, open investigation or active A/B comparison.
- Do not retire canonical fixtures, current APK/build inputs, settings,
  persistent ADB keys, databases, source, or unique open-failure evidence.
  Inspect mixed folders and tracked-file ownership, validate exact paths and
  reject reparse-point traversal. Record retirement in workspace
  `artifacts/CLEANUP_REPORT.md` and annotate historical raw-evidence references.

## Documentation and release policy

- `CHANGELOG.md` is the only version-history document.
- `WORKFLOW.md` defines the root interface shared with every Vibe repository;
  preserve its command semantics and `artifacts/downloads` package location.
- Markdown retained below `source/existing` is frozen comparison material.
  Upstream/source documentation below `source/dev` is application source, not
  OpenSageTV Vibe release tracking; never add Vibe release entries there.
- `HANDOFF.md` contains current state and the exact resume point, not a second
  chronological changelog.
- `TASKS.md` contains unchecked work in its active sections and completed
  checked work in its change ledger. Checked tasks remain visible in that
  ledger so status reports do not appear to change from one turn to the next.
- Stable operational guidance belongs in README, AGENTS, `docs/`, or component
  READMEs; update those files in place.
- Do not create `UPDATE_v*.txt`, version-named Markdown notes, dated matrix
  reviews, AI start prompts, or per-tool handoff documents.
- Update `VERSION`, `release.properties`, `CHANGELOG.md`, `HANDOFF.md`, affected
  durable docs, tests, and `PROJECT_MANIFEST.sha256` together.
- Write every GitHub release description as concise bullet points grouped by
  changes, fixes, compatibility, validation, and known limitations. Never
  publish the release description as one large paragraph.
- `release.properties` must contain exactly one matching `VERSION=x.y.z` and
  one `REQUIRES_BUILD=true|false`. Use `true` whenever APK-packaged source,
  resources, manifests, dependencies, or build configuration changed.

## Build and update workflow

Normal Linux/WSL commands use `./dev.sh`; native Windows uses `dev.cmd`
(which invokes the repository's `dev.ps1` without changing machine policy).
Both must select the sibling unified build environment and reuse
`opensagetv-vibe-dev`. This repository must not define or build a second Android
development image/container.

Never call an unqualified host `adb`. A host installation and host PATH entry
are deliberately unnecessary: the supported platform-tools binary and
persistent authorization keys are inside `opensagetv-vibe-dev`. On Windows use
`dev.cmd connect` and `dev.cmd adb <device-command>`; on Linux/WSL use
`./dev.sh connect` and `./dev.sh adb <device-command>`. For a one-command device
or server change, set `SAGETV_TEST_DEVICE_ALIAS` and
`SAGETV_TEST_SERVER_ALIAS` before invoking the wrapper, then remove the
overrides. Do not change `config/firetv.toml` just to run a temporary gate.
For explicit new-device readiness, preserve `SAGETV_ADB_SERIAL` in both native
Windows environment lists and the Linux container allowlist. Do not silently
drop that override and test the previously active device. Prefer a persistent
alias once commissioned, and verify reported serial/model before any mutation.
When native Windows reuses the already-running container, preserve both Docker
working directory `/workspace/android-client` and
`SAGETV_WORKSPACE=/workspace/android-client`. The entrypoint resolves its
marker files from that environment variable, not from `-w`; omitting it causes
a false missing-workspace failure even when the ADB key and device are valid.
Keep the `dev.ps1` source-contract assertion for this paired invariant.
The MCP ADB client must verify `adb_allowed_connection_time=0` with one-shot,
idempotent shell commands before opening its reusable interactive shell. Fire OS
may close a persistent shell created immediately after `adb connect`; do not
move authorization bootstrap back into that startup race or add implicit retry
of ordered runtime commands.
On Android6 a remote PTY can echo/redraw input even with local pipes. Preserve
the bounded startup-only echo/prompt/ready-marker handshake before runtime
commands; do not strip arbitrary runtime output or replay a failed command.
CLI command dispatch must close its owned AdbClient in `finally` on success,
early return and exceptions. Do not reset the shared ADB server or persistent
keys to clean up a diagnostic shell; the MCP server already registers exit cleanup.

Use `./update.sh` or `update.cmd` for incremental packages. Before extraction,
the runner must reject corrupt ZIPs, unsafe/duplicate paths, missing/mismatched
release metadata, incomplete manifests, payload hash mismatches, and baseline
drift. Never use an update ZIP to hide an unvalidated overwrite.

Before delivery:

```text
affected dev test/validate targets
dev build (when REQUIRES_BUILD=true or packaged inputs changed)
PROJECT_MANIFEST.sha256 regenerated and checked
git diff --check
```

Release validation is impact-based. Rerun every compile, unit, source-contract,
packaging, and physical gate that the change could affect, but do not repeat
unrelated completed matrices. Run the full project/device matrix only when the
user explicitly requests it or a broad dependency/architecture change makes it
necessary; record that reason and the exact selected gates in the release
evidence.

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
- Investigate every failed affected matrix row before closure. Prefer a
  bounded Android-client correction when the existing server protocol provides
  enough information; do not change Core/plugins first or count playable
  fallback as proof that a requested transport passed. If the fixture or test
  oracle is wrong, prove and document that distinction with corrected physical
  evidence instead of changing production playback to hide the measurement.
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


## Stock-server test-control policy

- For any new testing, commissioning, diagnostic, or automation control, first
  implement or extend the stock-compatible `opensagetv-vibe-core-MCP-Plugin`
  using supported `sage.SageTV.api`/`apiUI` calls and verify it against an
  unmodified stock SageTV server.
- Do not patch `Sage.jar`, add private MiniClient events, or change Core merely
  to make a test easier. Existing public APIs, the bounded MCP bridge, and
  external test tooling are the required first option.
- Change Core only when the required production runtime behavior cannot be
  expressed through the stock plugin/API boundary. Document the proven API
  gap, keep the extension optional and negotiated with a safe stock fallback,
  and verify older clients and installations remain unaffected.

## Pre-commit task-list maintenance

Immediately before every repository commit, clean `TASKS.md`: move every
completed `[x]` item out of the active task sections and into
`## Checklist change ledger`. Preserve stable IDs, acceptance evidence, order,
and enough source/parent context to understand the result. Never delete
completion history. Active task sections must contain unchecked work only;
checked boxes may appear only inside the checklist change ledger. Regenerate
the project manifest when the repository tracks one.
