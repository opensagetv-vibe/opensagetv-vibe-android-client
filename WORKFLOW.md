# Common project workflow

HLS-007 uses the same exact Non-Pro/stock232 gate with --task-id HLS-007,
--phase fixed-routes --route-set hls-stats and the reported completed source.
It selects only the four affected HLS delegates, retaining actual output/seek/
pause checks, verifies adjacent icon bounds and independent toggles, then
observes28s of advancing playback/refill after near-target warmup. Require at
least14s actual reserve for the20s ceiling, not a spoofed full bar or an average
that hides depletion. This is a focused LAN gate, not remote/WAN certification.
Respect its independent server-idle guard; do not interrupt another client's
playback to bypass it. Completed HLS classification requires positive provider
burst evidence, exact ownership and current responsive negotiation. Core's
timeshifted bit also describes Fixed for completed files, so never reapply that
ambiguous hint after the provider independently proves isRecording=false.
Live/unknown cases report no burst and remain unchanged. Save actual refill
sample arrays before assertions, including on failure, not just startup state.
Raw Exo position/duration are relative to the sliding playlist window. Preserve
those raw observations, but verify progression with actual file-clock plus
decoded audio/video counters and real media bytes. A180s moving window is not
recording EOF. Both delegates use observed position-in-period for owned HLS
playback/caption clocks and matching buffer coordinates; no guessed epoch or
wall-clock correction. Include rollover in affected qualification; do not
mislabel a healthy constant window-relative position as frozen video.

HLS-006 qualification uses the exact Non-Pro/stock232 settings gate with
`--task-id HLS-006 --phase fixed-routes --route-set hls-stats` and the supported
completed reported source. Test Direct control plus Media3/legacy and both GSY
HLS delegates, actual HTTP counters, reserve, independent graph/info icons,
pause/resume, confirmed file-clock seeks and a bounded repeated-remote-skip
sequence. The compact MCP state omits overlay visibility: use the existing raw
on-device snapshot for that field. A focused navigation dialog also hides the
Activity overlay from accessibility dumps. Verify live toggle/icon state and
inspect screenshots for all display modes, not absent accessibility text.
Debug-created navigation is not the Activity's retained instance: event-only
Hide does not dismiss that test instance, and Back is an STV event. Exit through
its existing Playback Settings button (explicit self-dismiss), then close that
hub. Never treat a visible overlay as absent because a projection or focused
window omitted it. Preserve all preferences/CC/power.
Do not label a session exit a process crash without crash-buffer/PID evidence,
or claim a GFX socket root cause from a successful seek test alone.
For owned HLS only, a slow ADB poll can miss a short seek-landing window after
successful playback resumes. The existing verifier may use a fresh server-Seek
and identity-fenced Direct first-frame event plus the actually observed file/
decoder clocks (at most10s old), then still requires recovered advancing A/V.
It does not use the requested target as an anchor, widen tolerance, replay Seek,
alter a clock, or apply this witness to stock/basic Fixed, Push, DVD, stale
events, a pending seek, changed connection or a new OPENURL. Retain its
landingWitness fields so an oracle correction is auditable.

SETTINGS-005 Hardware Encoder qualification uses the shared settings catalog,
not a second player-specific preference or a global server backend edit.
Run focused output/catalog/revision/status JVM and source-contract tests, then
the exact Non-Pro/stock232 `mcp_playback_settings_gate.py` transcoding phase and
fixed-routes `--task-id SETTINGS-005 --route-set encoder-pair` with the exact
reported source and `--hardware-encoder qsv|vaapi --policy hardware_only`.
Require observed matching server encoding, actual decoded output and file-
relative seeks; a Web GPU pass or an Android hardware decoder is not that proof.
The last row checks a next-default Auto change does not mutate the current
session or its subsequent seek. Use `hardware-unavailable --hardware-encoder amf`
for the bounded Linux negative case. All calls checkpoint/restore preferences,
captions and power. Auto remains compatible with old/missing providers, while
explicit pins require exact hardwareBackendRevision1 at connection and next
source boundaries. No original-Pull masking, Core/INI edits or matrix replay.

FIXED-002 uses the existing settings gate on exact Non-Pro25/stock232 with
--phase fixed-routes --task-id FIXED-002 --reported-source
/var/media/tv/FacetheNation-66420956-0.ts --seek-target-ms 153090
--seek-target-ms 419527 --seek-target-ms 30000 --route-set vaapi-seek
--reuse-installed --capture-hold. This selects four delegates in Direct/HLS
plus Stock/Copy controls, and holds only the last post-seek row for HDMI.
It does not clear a real recording's watch history, invent six source channels,
replace the APK, or treat normal clock advance as a successful remote FF skip.
Preserve the canonical-fixture matrix: reported-source acceptance is separate.
Stock Fixed also maps to VAAPI on232; follow the ten-row Exo/GSY batch with
the affected --route stock-ijkplayer-auto and its independent HDMI hold. IJK's
Copy/native MPEG-2 row is unchanged and does not need another replay.

For FIXED-001 use the commissioned Non-Pro/stock232 runner's fixed-routes,
fixed-failure, fixed-edges, inline and affected hls phases. Record requested
processing, actual transport, actual server encoder and Android decoder
separately. Stock FFmpeg is legacy/software-only; modern MIM Legacy Transcode
is a different route. Do not demand unsupported hardware/features from stock
FFmpeg, infer encoder execution from client hardware decoding, or identify the
original stock executable solely from stock-protocol Fixed when a shared MIM
launcher exists. The explicit Effective Transcoding Output observation proves
only the owned provider session; unavailable observation stays Unknown.
For AAC config0 regressions retain Source channels and preroll/seek tests with
original complete pinned audio AARs. Do not mask errors with Stereo or an
unrequested decoder-library fork. The public extractor boundary must preserve
actual configuration before the queue can discard negative-time samples.
IJK does not expose Exo rendered-buffer counters or Exo playWhenReady. Use its
existing native first-frame/FPS/basic-playing/clock observations and actual
decoder name, then require independent post-seek HDMI motion and audio proof.
Run its exact Stock/Copy rows with --capture-hold; do not change production
playback or rerun already passed Exo rows to correct an oracle mismatch.
Positive fixed-edges fallback checks require --policy server. A strict
Hardware Only/output request must refuse an absent/unsupported provider;
expecting ordinary Stock fallback in that configuration is a test error, not
a reason to weaken the production policy. Check explicit strict failures
separately and always restore the user's original saved encoder selection.

For SETTINGS-003 / DIAG-014, use the final candidate and exact non_pro/vibe
aliases with `scripts/mcp_playback_settings_gate.py --phase transcoding
--task-id SETTINGS-003`, then `--phase output --task-id SETTINGS-003`.
Run only one device actor at a time.
Use `/opt/opensagetv-vibe/android-python/bin/python3` inside the reusable dev
container for these phases; Web Python lacks the MCP server dependency. The
runner checks it before mutating a device, rather than failing after install.
The first phase verifies shared options,
mode availability, typed policy/Source storage and confirmed reset; the second
verifies actual owned software/QSV output, advancing A/V, status and stale-data
clearing on native Pull. Both checkpoint/restore preferences/power and verify
installed bytes plus independent server idle. Provider actual-output proof is
separate: FFmpeg Plugin's commission-output-settings.py and real CPU test.
Never substitute a requested bitrate/encoder for observed output, or change
the legacy global launcher to satisfy a per-client stock-routing checkbox.

For HLS-005 automatic buffer-policy qualification, run the host-owned
scripts/direct_network_reverse_gate.py --run-non-pro-stock232 --hls
--buffer-adaptive --player media3 (or another supported delegate), with the
explicit /workspace/android-client/source/dev/android-tv/build/outputs/apk/debug/android-tv-debug.apk
candidate. The actor must pass that Linux container path as a string, not a
Windows Path. It owns/restores the exact232 namespace's temporary31911 listener;
only Non-Pro25 media bodies are constrained. The child uses shared MIM min/max/
buffer reports, never the manual override, and verifies actual-frame down/up,
seek/pause/resume and settings/power/zero-session restoration. Preserve captured
child failures in its bounded report instead of silently filtering tracebacks.

For HLS-005 bounded MIM target testing use the reusable container, exact
non_pro/vibe aliases and `mcp_direct_quality_gate.py --hls
--mim-test-override --candidate-apk /workspace/android-client/source/dev/android-tv/build/outputs/apk/debug/android-tv-debug.apk`,
with artifacts/active/HLS-005. The gate propagates mcp/src to its real stdio
child, verifies installed APK bytes/model and checkpoints settings/power.
Debug config supports typed adaptive opt-in and a minimum within the saved
Fixed maximum. Exact-session/revision/lease MIM control retains real encoder
cooldown/recovery limits. Clear/restore and verify zero owned sessions in
finally; no host adb, arbitrary preference edit or stock Core mutation.
Add `--runtime-rate` only to REQUIRE in-place control; a handoff must never be
counted as passing that row. Without it the two-change/cooldown handoff path is
tested explicitly. Unknown/unsupported runtime readiness remains a separate row.

## Shared buffer adaptation (HLS-005)

Do not reintroduce client bitrate advisors or measured-bandwidth prerequisites.
MIM ADAPT-001 owns the policy. Clients send min/max at source start, then scoped
reserve/buffering/eligibility reports from existing callbacks (one pending,
at most once per3s, no extra player getter/timer/probe). Pause/seek/source change
retire stale advice; lost responses hold the stream. Preserve native Fixed,
Copy/DVD refusal and the existing actual-frame handoff/in-place custody.
Use focused provider HTTP/policy, Android quality/session/transport and build
gates, then affected stock232/Non-Pro rows. Never call a manual reduction a
buffer-policy or physical WAN pass. Dynamic remains Push.

Serialize Gradle build/clean/test operations against the same source tree.
Do not launch focused Gradle tests while `dev.cmd build`/`dev.sh build` is
still running: its deterministic clean can remove another task's outputs,
even when both use the same Gradle cache. Wait for actual completion, then
run focused tests. Use the wrapper's JDK17, persistent Android/signing home
and Gradle cache; a bare DEV-container Java11 command is not the Android build
workflow. Keep independent server/browser read-only work separate.

## Stock232 qualification and tooling

EXPERIENCE-003 reuses mcp_direct_quality_gate.py --preview-only with output
artifacts/active/EXPERIENCE-003. This drives the actual optional Video menu,
compares reported source buckets, checks continuing A/V/one playback producer,
then verifies public seek invalidates the old dialog and reopening captures
current custody. Source previews title is NOT a position label: require
`Source <minutes>:<seconds>`. Android Button labels may be transformed uppercase;
match their case-insensitive exact labels, never bypass the real menu with a
test-only callback/image/ACK. Preserve the existing installed-byte witness,
preferences/CC/power finally and independent final activity proof. Device
screenshots are not HDMI/audible-sync certification. Preview use is explicit
and optional, not a dependency for normal playback/quality adaptation.

For authorized BaseMediaPlayerImpl changes, review the bounded playback diff
and focused lifecycle/transport tests, then add its exact namespace-normalized
whole-file digest to BOTH validate_project.py and test_player_backends.py.
Never remove/relax that characterization guard to make a build pass. A stale
reviewed pin is not a missing SDK/container problem. Version-consistency tests
compare VERSION/release.properties, not a stale hardcoded released-version
literal. Build approval remains distinct from physical playback qualification.

EXPERIENCE-002 uses scripts/mcp_direct_quality_gate.py with exact Non-Pro25/
stock232 aliases and artifacts/active/EXPERIENCE-002. Existing MCP/Public Watch/
Seek plus the production Video->Direct quality UI supply the stimulus; no fake
frame ACK, raw-token report or new private Core control. Preserve its preference
checkpoint/automated awake lease/finally restoration. Generic UIAutomator dumps
use uniquely owned /data/local/tmp XML removed in finally. Installed APK Success
and actual optional qualitySeekRevision1 must precede device mutation/gates.
The gate also reads the exact installed development package's pm path and
SHA256 before checkpoint/mutation: matching DEV version text alone cannot
distinguish iterative builds with the same version. Refuse a canonical-vs-
installed mismatch; never silently attribute an old APK to a newly built file.
The Direct producer intentionally uses real-time pacing. Its quality consumer
prepares ahead, waits until the actual old clock reaches candidate start, then
requires published reserve and exact current-renderer presentation. Never
"fix" readiness by treating prepare200 as a frame, skipping to a future point,
changing Core, or disabling pacing globally. Closed diagnostic failure stage
identifies preparation/publication/render/commit without leaking lease URLs.

For measured-network qualification use scripts/direct_network_reverse_gate.py
--run-non-pro-stock232. It verifies the exact idle stock232 container, executes
the existing Unraid Python interpreter in that container's network namespace
only, and temporarily forwards source25-only port31911 to127.0.0.1:31910.
Only published TS bodies are shaped; APIs/captions retain normal delivery.
The owning driver uses the production Automatic quality UI and restores app
preferences, selected API port, captions and power in finally. The forwarder
has stdin shutdown and420s expiry; verify listener retirement, unchanged qdisc
and all20 protected stock hashes. Never install packages, load host kernel
modules, alter global firewall policy or log session handles/URLs. Fire OS
ignored the former Windows/global-http_proxy experiment (zero requests); do
not reuse that as a network test. The host lacks sch_prio: do not retry tc or
load modules. Shaped LAN is NOT physical cellular/VPN evidence. The longer
canonical VibeSeekTest fixture avoids90s EOF during cooldown qualification.
Before attributing a shaped-network failure to production playback, compare
--healthy-only with native delivery and the external forwarder, with no
intermediate snapshots. The former HTTP1.0/nonreused test path itself caused
starvation (3.5s/14.7s versus native13.5s/14.8s); known-length HTTP1.1 reuse
restores13.6s/14.9s. Preserve that delimiter/reuse behavior, bound unknown-length
replies by connection close, and retain closed route timing counters. Never
weaken playback/recovery thresholds to accommodate a broken test transport.
The parent qualification budget is360s plus bounded55s cleanup, within420s
remote expiry. An owned cancellation must enter the driver's settings/power
finally; never kill the shared ADB server or another client's process.
Never edit a running harness file; finish/cancel its owned session first so
traceback line references remain trustworthy. Normal playback never needs this
external test helper, MCP, or a device proxy setting.

STARTUP-001's bounded driver uses existing public nonblocking Watch and optional
callback-ring reads, never a production polling loop or a server Core change.
Forward SAGETV_MCP_CONFIG=/workspace/android-client/config/firetv.toml and
PYTHONPATH=/workspace/android-client/mcp/src together with workspace/aliases
for direct in-container invocation. Its exact Non-Pro25/stock232/output-directory
guard aborts sanitized/missing-config defaults before mutation. Keep its own
preference checkpoint/automated awake lease/finally restoration, not a second
nested lease. Preserve default buffering unless callback-level comparison
demonstrates a benefit; broad helper prompt/fullscreen waits are not player
startup. Current measured250ms trial gave no benefit over500ms. See owning
results/STARTUP-001/RESULT.md; no need to rerun unrelated matrices.

`.232` now uses protected official stock Core. Historical non-stock/Core-change
exceptions below do not apply. All server fixes use container OS/launcher or
real SageTV plugins; client-first fixes remain highest priority.

Launch MCP Python helpers through `dev.cmd`/the canonical entrypoint using
`/opt/opensagetv-vibe/android-python/bin/python3`, not an unqualified base Python
whose subprocess may lack MCP. Preserve paired working directory/workspace,
device/server aliases and private commissioning configuration. For HDMI capture
pass an absolute owning-project `artifacts/active/<ID>/` output path: relative
paths resolve against the Android project, not the workspace root. Run device-
mutating gates serially through restoration; build/source tests may overlap.
An exec session ID is not operation completion: poll it through its final exit
and inspect install Success before starting connect/navigation or another
device mutation. After connect completes, separately verify the exact server
UI context exists before Watch; connected=true alone can precede UI readiness.

For power-lease inspection/restoration, use `dev.cmd test-awake status` and
`dev.cmd test-awake end --scope automated` (Linux: `./dev.sh`), with the same
device/server alias overrides as the test. Do not invoke the helper from a
bare container Python: it needs the wrapper's MCP configuration, package path,
SDK PATH and exact commissioned serial. Restore captured values, even when
those originals already keep the device awake; never substitute sleep defaults.

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

## Client-first production fixes (highest priority)

For every fix, investigate and implement a safe client-only correction first
using the existing stock protocol, across affected player backends. Server
plugins, Client Extension and FFmpeg/MIM runtime changes are a last resort.
Before using one, record the exact missing client/protocol capability,
alternatives examined and evidence that a safe client-only fix cannot meet
the requirement. Convenience or easier testing is not proof. Core follows
only after both client and stock-compatible plugin alternatives are exhausted.
Keep extensions optional with safe stock fallback and preserve restart/activity
guards. MCP is the first boundary for new server test controls, not a required
normal-playback dependency or a substitute for a client fix. Fix proven defects
owned by existing plugins without duplicating their functionality in clients.

## Common interface and testing

Update/install preservation must include active session overrides, not only
the SharedPreferences checkpoint. Process replacement legitimately clears
ActivePlayerSessionOverrides; the live audio offset can therefore differ from
the saved device default. Capture the effective offset/output and original
source/position before interruption. After guarded install, restore persisted
settings, reconnect the same exact client/source, and reapply any captured
session-only audio choice through existing `dev_set_active_audio` controls.
Verify the effective result without saving over the device default. Refuse
restoration over a newly selected source/value. GROW-002 caught/restored a
Pro session-600ms versus saved-300ms difference; a249-key restore alone was
not proof that the active listening state survived. Record the corrected
restoration separately from the initial failed preservation check.

This repository uses the same root interface as every OpenSageTV Vibe project:

MCP ADB unit tests include executable POSIX mock scripts. Run that suite inside
the supported dev container, not native Windows Python; WinError193 from a
host mock executable is not a physical ADB or client failure. Cross-platform
source-contract tests may still run with host Python. Keep device-mutating
gates serial through final preference restoration, even after a live capture
marker or provisional verdict appears.

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

For live capture, guard every server sharing the physical/network encoder
before playback mutation, not only the selected server. A second server's
recording can replace the first server's capture file despite healthy player
counters. Follow docs/TEST_ENVIRONMENT.md; unknown/active shared ownership
aborts without stopping the existing client or encoder. Do not interrupt a
recording to manufacture growth evidence.

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

## Streaming-mode terminology

`streaming_mode=dynamic` is the backward-compatible saved value for **Push**.
It is not a Pull or Auto preference. The UI label and MCP `push` mapping are
authoritative for user intent. Log/report both selected mode and observed
transport: for example, "Selected Push (`dynamic`), actual Pull" is a client
negotiation mismatch, not a user-selected Pull session. Confirm actual runtime
ownership/data source before assigning transport-specific causes or gate passes.

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

Server controls are a separate scope boundary: resolve the selected device's
complete client ID to its own public UI context before Watch or another
playback mutation. Record serial/client-ID/context in the gate. Never select
the sole other remote UI while registration is pending; bounded exact
read-only discovery may retry, ordered Watch/Seek commands may not.

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

Serialize Gradle work that shares this checkout: do not overlap `dev build`
(which cleans generated module outputs) with focused Gradle compile/unit tasks.
That race removes classes/resources while the other compiler reads them and
can produce misleading missing-package errors or an undeletable clean tree.
Wait for each invocation's actual completion before the next Gradle command.
Keep `config/dev-root-build.gradle.canonical` and `source/dev/build.gradle`
version codes synchronized: the normal repair preflight deliberately restores
the canonical file before building. A change to only the generated root copy
will not survive that preflight.

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
This also applies to one-shot android_test_keep_awake.py invocations outside
the entrypoint: use the Android Python environment, PYTHONPATH pointing to
/workspace/android-client/mcp/src and the explicit commissioned config. A bare
docker-exec Python omits the entrypoint exports and cannot restore the lease.
Verify end --scope automated reports restored and status reports active=false;
never mistake an import failure for restored device power settings.
For debug broadcasts use `-f 0x10000000` for Intent.FLAG_RECEIVER_FOREGROUND:
Android6's Am CLI rejects the newer `--receiver-foreground` option. Preserve
the same foreground semantics without probing/replaying a runtime command.
Every independently invoked CLI command owns its AdbClient and closes it in
`finally`, including early returns and connection/diagnostic exceptions. Close
only that client's diagnostic shell; never restart the shared ADB server or
remove persistent keys to clean up an orphan. The MCP server retains its
existing process-exit cleanup registration.
One-shot SDK subprocesses must use stdin=DEVNULL, never inherit the MCP
JSON-RPC input pipe. The interactive shell alone owns its explicit stdin=PIPE.
MCP drivers must drain child stderr into a bounded background buffer. Never
leave stderr PIPE unread until failure or read-to-EOF from a request timeout:
long diagnostics can block the server's reply, and a live server has no EOF.
Keep partial-line/backpressure and live-timeout regression tests.
MCP stdout also needs one framed reader, not select(fd) followed by a buffered
readline: a notification can prefetch the following response into TextIO while
the descriptor appears empty. The bounded reader/queue in MCPProcess preserves
notifications/replies, applies request deadlines even for a partial line and
fails visibly on oversized frames/queue overflow. Keep its real-child burst,
partial-line and overflow tests; never resolve this by replaying a runtime call.
The finite scripts/mcp_adb_connection_test.py gate initializes a REAL MCP child
per iteration, verifies expiry0/selected serial/reused shell, closes each child
and never launches playback. Use explicit aliases and active/ADB-003 outputs.
Device offline/unauthorized state remains a real readiness failure; do not
label a different device's success as affected-device timeout closure.
Source validation prunes generated build/.gradle trees before file stat; never
change a generated Linux build symlink to make native Windows validation pass.
The native SurfaceView check accepts the guarded binding helper only with its
callsite/native fallback. Keep both positive and negative binding tests.

Align the outer clean-start deadline with the bounded cleanup it invokes; do
not terminate it early or implicitly replay an ordered exit/playback command.
Preference restoration is independent of server-caption restoration. Recover
an interrupted private preference checkpoint before taking another checkpoint.
Keep that isolation test with setup/authorization and ordered-runtime tests;
never resolve a setup timeout by replaying a playback command.
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
Before installing over active playback, use the bounded Dev clean-start/
graceful teardown path and preserve settings. Wait for actual install Success
and process completion before invoking a physical gate; a session ID is not
success. Record the installed candidate hash before another build overwrites
the local APK. If hardware initialization wedges, capture a bounded thread
witness and compare filter Off before attributing it to the GPU stage; recovery
after a test-device reboot does not prove the original cause.

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

When incrementing the Android versionCode, update both
`source/dev/build.gradle` and `config/dev-root-build.gradle.canonical` together.
The startup repair validates their exact bytes; changing only the active copy
will deliberately restore the previous canonical value. Retire any owned
`.gradle.bad` repair backup after confirming it contains no unique work, and
regenerate the project manifest before validation or publication.
Native rebuild patch payloads retain unified-diff blank context lines. Their
narrow `.gitattributes` rule exempts only blank-at-EOL in that patch directory;
do not strip the diff syntax or weaken whitespace validation for runtime code.

Repository CI and physical A/V-analysis unit tests use the pinned host-only
NumPy/SciPy versions in tests/requirements.txt. They are not APK dependencies
or changes to the MCP lock. The standard unit-test launcher creates and cleans
an isolated system-temporary test environment when needed, preserving the SDK
Python/key state; direct CI-equivalent tests install those exact requirements.
The GitHub sideload APK continues the existing Dev package/signing identity;
it is not a production-store release or a new upload key.

### Startup settings changes and upgrade preservation

Keep existing preference keys and explicitly saved values when reorganizing
menus. A missing value on an upgrade uses the current declared default, just
as on a fresh install; do not interpret an absent boolean as an explicit Off.
The approved OpenGL UI and Unified HD Graphics defaults are both On. Their
idempotent missing-key migration must preserve an explicitly saved Off or On.
Keep `docs/SETTINGS_MENU_DEFAULTS.txt` synchronized with runtime defaults;
stars describe code defaults, not the commissioned device's saved choices.

Run the affected settings source contracts and `FixedAdjustmentPolicyTest`,
then build with the normal wrapper. For physical menu changes, run
`scripts/mcp_settings_menu_gate.py` inside the reusable dev container with the
commissioned Non-Pro/server overrides. It verifies installed APK bytes,
preservation, remote focus/expansion, selected-player routes, applicability,
adjustment mappings and Cancel/confirmed reset using a private on-device
checkpoint. `--reuse-installed` requires the same candidate byte witness; it
does not waive any UI gate. `--reset-only` reruns just reset/default acceptance
after the other rows already passed on that exact APK; record both reports,
never label the narrower report as a full navigation rerun. Restore all
original preferences and power settings
in `finally`; never export credentials or clear app data to test defaults.
Reset must be confirmed with Cancel initially focused, and reset only its
allowlisted settings while retaining identity, credentials, SMB profiles,
files and persistent authorization keys. No server update is required.

For shared startup/long-press changes, use `PlaybackSettingCatalog` and its
typed startup binder rather than duplicating labels, keys or defaults. Route
gear and direct icons through the same runtime panels. Preserve navigation
focus/expansion independently of saved playback values. Session changes must
not persist unless explicitly saved; next-playback-only Fixed settings must
not claim an immediate application. Keep unavailable rows explained and gray.
See `docs/PLAYBACK_SETTINGS.md` and update the editable defaults text together.

Run the shared catalog JVM tests, affected source contracts and normal build,
then `scripts/mcp_playback_settings_gate.py --phase main|runtime|hls` on the
exact commissioned Non-Pro/stock232. A phase split permits affected-only reruns;
`--reuse-installed` still requires a byte-identical APK. Check actual A/V,
session/default isolation and complete restoration, not only API acceptance.
Fire TV's numeric IME can overlay an AndroidX dialog while its underlying
buttons remain in the accessibility tree: explicitly hide the keyboard and
allow its close animation before activating OK/Cancel. Never weaken the
saved-value/cancellation assertions to hide a test-driver failure.
Use its `recovery` and `reset` phases when session restoration, return routes or
the shared reset allowlist changes. Touch-focusable runtime rows acquire focus
on pointer DOWN so the first UP clicks once; do not work around a first-tap
application defect by silently double-tapping every test action.

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
