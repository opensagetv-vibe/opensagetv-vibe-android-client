# Common project workflow

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

This repository uses the same root interface as every OpenSageTV Vibe project:

```text
dev.cmd test|validate|build|install|all
./dev.sh test|validate|build|install|all
update.cmd
./update.sh
create_ai_handoff_zip.cmd
```

All launchers resolve this checkout from their own location and reuse the
sibling unified image/container. Android install is guarded to the Dev package;
complete first-time setup manually before MCP playback automation.

Unmodified stock SageTV is the default compatibility baseline. Test and fix the
client against stock first. Server-side Sage.jar or FFmpeg/MIM changes are a
last resort, must be optional and capability-negotiated, and must preserve a
bounded stock fallback. A Vibe server may prove an optional enhancement, but
must not replace the stock-server regression gate.

Local commissioning uses only the ignored `config/firetv.toml`; the GitHub and
handoff-safe schema is `config/firetv.example.toml`. Run `dev.cmd config-check`
or `./dev.sh config-check` before device work. Named device/server aliases,
per-server credentials/SMB mappings, fixtures, capture inputs, identities, and
defaults are documented in `docs/TEST_ENVIRONMENT.md`.

Physical audio/video offset work uses the reproducible embedded and
PBS-profile server fixtures in `docs/AV_SYNC_PHYSICAL_GATE.md`. The unmodified
stock Push path is the required baseline; optional Fixed/MIM testing is a
separate result and must not replace it.
Caption gates also snapshot the connected server's public CC state before
test controls and restore/verify it before disconnect. That setting persists
in SageTV independently of Android preference checkpoints; a failed restore
is a failed gate. Preserve the captured value, never assume Off or claim an
uncaptured earlier baseline was restored. Keep this production-neutral test
workflow behind the existing supported caption/MCP API boundary.

## ADB command path

Do not install, discover, or invoke a bare host `adb` for this project. The
supported Android platform-tools binary runs inside `opensagetv-vibe-dev`, and
the repository wrapper supplies the persistent ADB key directory and selects a
configured device. On native Windows, `dev.cmd connect` and `dev.cmd adb`
execute directly in an already-running unified container, so they do not depend
on a host PATH entry or WSL instance creation. If the container is not running,
the established WSL workflow remains responsible for starting it. Use:

```powershell
# Native Windows
dev.cmd connect
dev.cmd adb shell getprop ro.product.model

# One-command Pro/stock-server selection without editing firetv.toml
$env:SAGETV_TEST_DEVICE_ALIAS = "firetv-pro"
$env:SAGETV_TEST_SERVER_ALIAS = "stock-compatibility"
dev.cmd connect
dev.cmd adb shell getprop ro.product.model
Remove-Item Env:SAGETV_TEST_DEVICE_ALIAS,Env:SAGETV_TEST_SERVER_ALIAS
```

The Linux/WSL equivalents are `./dev.sh connect` and
`./dev.sh adb <device-command>`. Use `connect` to establish/list the configured
network device; the raw `adb` wrapper is intentionally scoped to that device so
commands cannot land on another attached Android target.

The native-Windows fast path must pass
`SAGETV_WORKSPACE=/workspace/android-client` into the reusable container. Its
working directory alone is not sufficient: `docker/entrypoint.sh` validates
  project markers relative to `SAGETV_WORKSPACE` and otherwise defaults to
`/workspace`. Keep this environment value aligned with the Android bind mount
whenever `dev.ps1`, the container destination, or the entrypoint changes. The
source-contract tests enforce this pairing so a healthy ADB key/device is not
misreported as an invalid workspace bind.

New environments use `commission_test_environment.cmd` or
`./commission_test_environment.sh`; this creates the ignored local config on
first run, then validates/preflights, creates or reuses canonical fixtures,
tests, validates, and builds. Installation remains an explicit option. See
`docs/COMMISSIONING.md`.

Download changed-files packages to `artifacts/downloads`. The hardened Android
runner follows the common contract and verifies paths, duplicates, metadata,
payload/full-manifest hashes and
baseline drift before extraction, then resumes test, validate, build, and
install. The package ID remains `opensagetv-vibe-android` for compatibility.
Android then performs its guarded DEV001 launch as its commissioning action.
The sibling build environment's `WORKFLOW.md` defines the common contract.

## Capture and artifact retirement

For a focused direct-container Gradle command from native PowerShell, pass
`bash ./gradlew <tasks>` as Docker arguments rather than piping a here-string
into Bash. PowerShell's CRLF pipe can append a literal carriage return to a
task name (`assembleDebug\r`), which is an invocation failure, not a source
compile error. Normal `dev.cmd` remains the preferred build entry point; keep
the paired working directory/workspace and unified JDK/SDK/cache values for
any focused invocation.

The reusable image's default JDK is **11 for SageTV Core**, not Android's JDK.
For direct Android Gradle invocations explicitly supply
`-e JAVA_HOME=/opt/java/jdk17 -e JDK_HOME=/opt/java/jdk17` and put
`/opt/java/jdk17/bin` first in the command's PATH. Android AGP rejects JDK11
before source compilation. Prefer the normal wrapper, which selects the
unified Android JDK automatically; do not change the container's Core default.

Run full Android source validation through `dev.cmd validate`/`./dev.sh
validate` or the documented reusable-container command with both
`-w /workspace/android-client` and
`-e SAGETV_WORKSPACE=/workspace/android-client`. Native Windows host Python
can run the small task-order/source-contract checks, but should not scan the
generated Gradle/native build tree: its Linux symlinks can cause WinError1920
even when the container build/source validation is healthy. Do not remove
build inputs or change source to work around that host-only invocation error.

At the end of every test session, review the files created by that session.

If an active Android gate proves a dependency-plugin defect, fix and test the
owning Vibe plugin and update the test plugin without asking again solely for
cross-repository permission. Record the linked task, cause, focused validation
and preserved settings. On stock `.175`, plugin installation/update is the
only authorized installation change: stock Core, Sage.jar, stock FFmpeg and
server installation/configuration files must remain untouched. This does not
authorize unrelated work, publication or interrupting recordings/other users.

If a proven production defect cannot be corrected in the client or a stock-
compatible plugin, the user's standing rule permits the necessary non-stock
`.232` Core correction and affected tests. Document the API gap/alternatives
and preserve optional negotiation plus safe stock/older-client fallback.
Core remains the last option, never a shortcut for test control; `.175` is
still protected from Core/server file changes.

Retain the written gate result, tested build/revision, device/server, relevant
settings and measured observations in the task ledger/handoff or structured
report. Completed gates do not require retaining every raw screenshot or
recording. Corrected failures do not require retaining every failed-run capture
either; preserve the cause, correction and validation result instead.

Use these locations for new work:

- Workspace `../../artifacts/temp/`: temporary downloads, extracted verification
  trees and one-use helper scripts. Retire each owned staging tree when its work
  completes; do not create a new cleanup-quarantine directory.
- Project `artifacts/active/<stable-task-ID>/`: current captures, recordings,
  snapshots and logs. Pass an explicit output path to each test/capture helper.
  Do not put new raw files loose in the workspace root or legacy firetv folders.
- Project `artifacts/results/<stable-task-ID>/`: a compact final report when
  useful. The existing TASKS ledger/HANDOFF retain the written completion record.
- Workspace `../../deleteme/`: raw output and staging no longer needed for a
  completed gate or corrected failure, moved during that same task.

Retire old run logs and diagnostic snapshots as well as images/video; preserving
the final result does not mean retaining the full raw run directory. If a move
is blocked by an open file, report its exact path and retry after release instead
of creating another temporary/quarantine copy. Existing fixture/download and
authorization paths are unchanged.

ADB persistent shells on Android6 can be PTYs despite local pipes. After the
one-shot authorization bootstrap, initialize each new shell once: suppress
TTY echo/output translation and mksh redraw, clear prompts, and consume a
unique bounded ready marker before any runtime command. Never repair echoed
properties by stripping arbitrary backspaces from legitimate command output
or by replaying ordered commands. Keep pipe/PTY, timeout, closure and concurrent
serialization tests together. Use the established Android Python environment,
explicit `SAGETV_MCP_CONFIG` and `PYTHONPATH=mcp/src` for MCP tests; config
fixtures isolate ambient device/server aliases without altering live settings.
For debug broadcasts use `-f 0x10000000` for Intent.FLAG_RECEIVER_FOREGROUND:
Android6's Am CLI rejects the newer `--receiver-foreground` option. Preserve
the same foreground semantics without probing/replaying a runtime command.
Every independently invoked CLI command owns its AdbClient and closes it in
`finally`, including early returns and connection/diagnostic exceptions. Close
only that client's diagnostic shell; never restart the shared ADB server or
remove persistent keys to clean up an orphan. The MCP server retains its
existing process-exit cleanup registration.
For explicit MCP Dev shutdown, send HOME once and preserve bounded read-only
background observation before the single force-stop. Browser-settle-only
trial failed actual post-playback on Android6: its previous phone task starts
as the foreground activity is killed. Do not replay the
exit/stop or count that restarted background process as successful teardown;
require actual final package/process state. Ordinary session-only exit keeps
its existing behavior; this coordination does not change production playback.
On legacy Android, `cmd package resolve-activity` may be absent and a
package-restricted implicit MAIN launch may also fail. Accept only validated
components owned by the Dev package; use installed package metadata plus
firmware Leanback features for the read-only legacy resolution fallback.
Never interpret `/system/bin/sh: cmd: not found` as a component. Legacy vendor
`pidof` can return every system PID even for a nonexistent name: reject PID0/1
and mixed diagnostic text, then use exact-name process matching. Android6
toolbox `ps -A` can return only its legacy VSIZE/WCHAN header; plain `ps` is
the read-only fallback in that case. Do not count system PIDs as app crashes.
Each `[devices.<name>]` can save `install_timeout_seconds` (finite numeric
30..900; default180 unchanged). MiniMX's private profile uses600 after real
old-device dex optimization exceeded180. CLI and MCP pass that selected budget
to one verified settings-preserving install, never an automatic retry/reset.
Allow the outer MCP install caller the configured budget plus at least30s
(MiniMX630s), otherwise it can abandon a still-running install. After a timeout, inspect the still-
running owned installer and installed APK hash before any explicit retry;
never start overlapping installs or clear app data to work around a slow update.

Move unused intermediate captures, superseded packages and raw evidence from
completed/corrected cases to workspace-root `deleteme/`, preserving their
workspace-relative paths. Do this after inspection/analysis, not while a
capture or analyzer is running, and review it again before commit/release.
For example, `artifacts/firetv/example.mp4` from this checkout moves to
`../../deleteme/projects/opensagetv-vibe-android-client/artifacts/firetv/example.mp4`.
The user may empty `deleteme/`; do not delete it automatically.

Preserve unique evidence for open failures and active A/B comparisons, any
specific raw evidence needed for a release, and documentation/listing assets.
Never retire source, credentials, persistent ADB keys, configuration backups,
database caches, canonical fixtures, or the current installable build. An old
timestamp alone is not proof that a file is disposable. Check tracked-file
ownership and resolved paths, reject reparse-point traversal, and never move
a whole mixed artifact directory without inspecting its contents.

Record each retirement category in workspace `artifacts/CLEANUP_REPORT.md`.
Before retiring referenced raw captures, annotate the owning handoff/document
so historical evidence paths are not presented as still available. Keep the
minimum useful open-failure evidence on failed runs; do not accumulate unrelated
temporary data merely because the overall test failed.

## Task ordering and matrix workflow

Repository CI and physical A/V-analysis unit tests use the pinned host-only
NumPy/SciPy versions in tests/requirements.txt. They are not APK dependencies
or changes to the MCP lock. The standard unit-test launcher creates and cleans
an isolated system-temporary test environment when needed, preserving the SDK
Python/key state; direct CI-equivalent tests install those exact requirements.
The GitHub sideload APK continues the existing Dev package/signing identity;
it is not a production-store release or a new upload key.

`TASKS.md` is the authoritative Android backlog. Its **Suggested execution
order** must be reviewed whenever a task is added, completed, removed, deferred,
unblocked, or changes dependencies. Update the order only when the dependency
graph or matrix timing actually changes; preserve stable IDs and keep detailed
acceptance criteria in the existing task sections. Mirror workspace-level
dependencies in `../../task.md`.

At each such change, update the checklist revision and ledger, reconcile both
suggested-order sections immediately, and stamp them with that same Android
checklist revision. Remove a completed task from the order; if only a sub-gate
completed, replace stale prerequisite wording with the parent's next unfinished
gate. Do this when the status changes, not only at commit time. Run
`python scripts/task_order_check.py` before a commit; project validation also
checks the local stamp and checks the workspace mirror when `../../task.md` is
available. A revision mismatch or a completed task still listed in the order
is a failing gate.

During implementation, run focused automated and physical gates for the code
being changed. Do not start `MIMFIX-003`, `AUDIO-005`, or another broad
pre-release matrix until all release-bound Android changes ahead of it are
stable. Publish only after the applicable pre-release matrices pass. Run
`MATRIX-003` after publication using affected device rows only, then complete
`MATRIX-004` evidence synchronization, unless the user explicitly moves a
device gate before publication. The2026-10-07 affected closure follows that
exception for Shield/ONN v1/ONN Pro; both tasks are now in the checklist ledger.
Do not restart those completed matrices by following historical order text.

When positive codec rows are already complete but negative containment remains,
use `mcp_hardware_codec_matrix.py --safety-only` with the commissioned device/
server and preference guard. Do not combine it with `--start-at` or `--max-cases`.
It requests zero positive rows and retains the existing malformed-input and
negative-capability scope. Report the actual malformed-stream result separately
from static fault-plan validation, hardware exclusions and unavailable DRM
assets; an aggregate harness PASS does not make those physical playback PASS.

If a later shared-player change invalidates completed evidence, identify the
specific transports, players, codecs, captions, devices, or lifecycle rows it
could affect. Rerun those rows and the final matrix portion they invalidate;
never restart every completed matrix by default. Immediately before a commit,
move completed checkoffs into the `TASKS.md` checklist ledger, reconcile the
workspace task mirror/order, and regenerate `PROJECT_MANIFEST.sha256`.

GitHub release notes always use concise bullet points grouped by changes,
fixes, compatibility, validation, and known limitations. Do not publish one
large release-note paragraph. Playback issue instructions must keep the
long-press diagnostic-export screenshots and no-email SMB retrieval steps
current with the application UI.
