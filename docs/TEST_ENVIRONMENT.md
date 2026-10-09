# Local test environment

For an already-running unified container, owned in-container Python test
drivers use both Docker working directory /workspace/android-client and
SAGETV_WORKSPACE=/workspace/android-client, with explicit device/server aliases
and the persisted ADB identity. dev.cmd/dev.sh orchestrate Docker from the
host; do not nest dev.sh inside docker exec or substitute a host adb.
Bracket such a driver with the settings checkpoint/restore and automated
keep-awake begin/end helpers, including failure cleanup. Capture pictures at
the live stage before the child harness exits, not after its final STOP.

Prefer nonblocking public library.scan followed by verified exact-path
resolution. Explicit wait_until_done=True commissioning gets a120s HTTP
budget; a timeout is ambiguous completion, not permission to replay the scan.
Check the resulting index/fixture first. Newly generated caption controls
must have a configured fixture case, owned shared-media path, hash checks,
and exact temporary-import/file retirement after qualification. The500ms
caption stress source is not the sustained2s visual readability oracle.

For APK backups/transfers as well as installation, use the selected device's
commissioned `install_timeout_seconds` budget. MiniMX's older storage/Wi-Fi
transferred only23 MB in90s; that is a transfer-budget failure before app
mutation, not a playback failure. Independently compare a fresh rollback's
SHA-256 with the device before installing. A deliberately reused pre-trial
baseline is checked against its recorded baseline hash, not the intervening
candidate currently installed. Keep persistent keys and user settings intact.

`config/firetv.toml` is the single local source for Android commissioning and
physical regression settings. It is ignored by Git and excluded from source,
release, update, and AI-handoff packages. The tracked
`config/firetv.example.toml` contains the complete schema with documentation
addresses and blank credentials.

Create the local file once:

```powershell
Copy-Item config/firetv.example.toml config/firetv.toml
dev.cmd config-check
```

The same commands work through `./dev.sh` on Linux/WSL. `dev test`, MCP, ADB,
preflight, scripted launch, and physical-test scripts all use
`SAGETV_MCP_CONFIG`, which the root workflow sets to this file.

ADB is intentionally supplied by the unified `opensagetv-vibe-dev` development
container, not the Windows host PATH. Do not run bare `adb` or search the host
for `adb.exe`.
Use `dev.cmd connect` and `dev.cmd adb <device-command>` on Windows, or
`./dev.sh connect` and `./dev.sh adb <device-command>` on Linux/WSL. These
commands use the configured device and the persistent key directory under this
workspace. The Windows wrapper invokes ADB directly in an already-running
`opensagetv-vibe-dev` container; WSL is not required for those two commands once
that container exists.

Raw wrapper commands remain device-scoped even when a subcommand reuses an ADB
global option name. For example, `dev.cmd adb logcat -d` treats `-d` as
logcat's dump-and-exit option and still injects the configured serial; only a
device selector placed before the ADB subcommand (`-s`, global `-d`, or global
`-e`) overrides automatic scoping.

For this direct Windows path, `/workspace/android-client` is both the Docker
working directory and the required `SAGETV_WORKSPACE` value passed to the
entrypoint. Do not remove that environment assignment from `dev.ps1`: the
entrypoint resolves its marker files from `SAGETV_WORKSPACE`, not from the
process working directory. A mismatch produces a false "workspace bind is
missing" error even when the persistent ADB identity and device authorization
are healthy.

Immediately after `adb connect`, the MCP helper applies and verifies
`adb_allowed_connection_time=0` with idempotent one-shot shell commands before
opening its reusable interactive shell. Keep that bootstrap ordering: some Fire
OS builds close an interactive shell created while the freshly connected
transport is still settling, which otherwise makes a healthy authorized device
look disconnected during install or commissioning. Runtime commands still use
the serialized persistent shell and are never implicitly replayed.

An already-connected TCP transport can also hang during this one-shot bootstrap
without reporting `device offline`. After the failed call has closed its owned
shell and no gate is running, disconnect only that commissioned serial through
`dev.cmd adb disconnect <serial>` (or `./dev.sh adb disconnect <serial>`), then
use `connect` again with the same device alias and confirm its model. This
recovered MiniMX's15s authorization-query timeout without resetting the shared
ADB server or replacing keys. Do not replay runtime commands automatically,
disconnect other devices, or claim a failed pre-checkpoint attempt mutated
preferences. Keep the original failure and retry result distinct.

Android6 may require a remote PTY even for piped ADB. The MCP shell performs
a bounded startup handshake that suppresses echo, prompts and mksh redraw,
then discards only startup preamble before the ready marker. Runtime output
is not destructively cleaned and ordered commands are never replayed. MiniMX
API23 and Samsung API33 read-only connection gates pass alongside65 focused
ADB tests and the130-test MCP suite in its configured Python environment.

## Test-session keep-awake

### Server capacity is part of the gate, not a client verdict

Stock server `.175` / alias `stock-compatibility` is CPU-constrained and has
no usable hardware transcoding in its current Docker environment (user
confirmation,2026-10-07). Do not use it as a full-GPU Transcode benchmark or
silently compare its startup/control latency with GPU-enabled `.232`.
Keep `.175` as the unmodified stock compatibility reference; run hardware
Transcode/decode/filter/encode gates on `.232` / `vibe-test` and prove the
actual server backend and hardware flags independently.

Record the real transport and server workload for a stall. Pull, native DVD
Push and owned Copy do not become server video transcoding merely because
Android uses a hardware decoder. Native DVD still uses server DVD navigation,
I/O and Push processing; CPU contention/throttling can delay its controls.
Capture available container CPU quota/cpuset, utilization/throttling and
concurrent transcode jobs alongside decoder/input/drain state. Missing GPU
drivers alone does not prove the cause of a native-playback failure, and a
passing `.232` comparison does not isolate CPU when Core/STV also differ.
Investigate client evidence and server capacity together without changing
Core, resource limits, user settings or declaring either side faulty by guess.

Every command routed through the automated MCP test wrapper snapshots the
selected device's `stay_on_while_plugged_in` and `screen_off_timeout` values,
wakes the display, and applies bounded keep-awake overrides. The wrapper
restores the exact prior values on pass, failure, Ctrl+C, or termination. A
checkpoint under ignored test artifacts also lets the next test repair an
interrupted session before capturing a new one.

An automated gate nested inside a manual session borrows that session. It does
not replace its checkpoint or restore its settings; the outer manual `end`
remains the owner of restoration.

For physical evidence that spans multiple commands, bracket the work manually:

```powershell
dev.cmd test-awake begin
# Run the playback, HDMI/webcam capture, and inspection commands.
dev.cmd test-awake end
```

`dev.cmd test-awake status` reports the current values. Always use `end` when
the multi-command test is complete; it restores user settings rather than
installing a permanent device policy.

## Multiple clients and servers

Persist older-device install allowances as `[devices.<name>].install_timeout_seconds`.
It accepts finite numbers30..900/default180; MiniMX uses600, other current
devices180. This changes only the single SDK install observation budget, not
ADB command replay, app data or playback gates. An outer MCP caller must wait
longer than that configured budget (use630s for600). After a timed-out install,
check optimizer/package status and exact APK hash before any explicit retry.

Add any number of entries below `[devices.<key>]` and `[servers.<key>]`. Each
entry may also have a unique, human-readable `alias`. Select the normal pair
with the root `active_device` and `active_server` values; either the table key
or alias is accepted.

For one command, override the selection without editing the TOML:

```powershell
$env:SAGETV_TEST_DEVICE_ALIAS = "firetv-non-pro"
$env:SAGETV_TEST_SERVER_ALIAS = "stock-compatibility"
dev.cmd config-check
dev.cmd mcp-session-test --server-path "/path/from/the/selected/server"
```

Remove those environment variables afterward to return to the active entries.
`SAGETV_ADB_SERIAL` and `SAGETV_TEST_SERVER_ADDRESS` remain explicit
lowest-level overrides. Each device can have its own `client_id`; otherwise
`identities.automated_client_id` is used. Interactive app launches still keep
the client ID stored by the app.

The wrappers forward `SAGETV_ADB_SERIAL` through both native-Windows reusable
Docker and Linux/WSL paths. Do not drop it from either environment allowlist:
an explicit readiness probe must not silently connect the previously active
device instead. Prefer a persistent commissioned alias for normal gates.

## Server-owned SMB settings

Each `[servers.<key>]` directly contains `smb_url`, `smb_username`,
`smb_password`, `smb_configuration_url`, and `smb_mappings`.
There is no separate SMB header or separately selected SMB profile. This keeps
the share and SageTV-to-SMB path mappings aligned with the server whose
`OPENURL` paths are being tested. The earlier root/nested SMB forms remain
readable for local backward compatibility but must not be used in new files.

Each server also declares `media_selection_mode`. Use `stock_web` for an
unmodified server: automation resolves a MediaFile through Sagex or the stock
Web Interface and sends the ordinary `Watch` command. `vibe_exact_path`
requires the opt-in modified-sage.jar exact-path event. `auto` may try that
extension and then fall back to stock-compatible indexed control. This server
setting is independent of playback transport (Push, Pull, Fixed, or SMB).
Set `webserver_installed = true` only when that server exposes Sagex or the
stock SageTV Web Interface used for MediaFile lookup/Watch control. A
`stock_web` selection requires it. A modified server using
`vibe_exact_path` can be tested with it false.

An isolated container-backed server may also define `unraid_host`,
`unraid_ssh_port`, `unraid_ssh_username`, `unraid_ssh_password`,
`unraid_container_name`, and `unraid_appdata_path`. These values let local
commissioning tools address the host that owns the selected SageTV server
without confusing that host with the server's own MiniClient/Web address.
Real credentials belong only in the ignored `firetv.toml`; tools must redact
them from output and diagnostic bundles.

For a Windows-hosted read-only Unraid query, use those stored server fields
with host Paramiko and existing known-host verification; never invent an
ephemeral SSH key or print credential values. Query MIM through the installed
`/opt/sagetv/server/SageTVTranscoder --mim-status` launcher. The server's
`/opt/sagetv/server/ffmpeg` can be the stock executable and does not necessarily
support that command. Inspect `lastTranscodeJob` (or the actual active job),
including input, backend, encoder, hardwareDecode and hardwareEncode; Android
hardware decoding or selected Transcode mode alone does not prove server GPU
execution. The older `query_unraid_mim_status.sh` default temporary-key/stock
ffmpeg assumptions are not authoritative for this installed plugin layout.

When a fixture supplies a `server_path`, playback automation always tries that
exact path before title lookup. If exact-path control is unavailable, it may
fall back to an indexed MediaFile Watch and finally to the legacy STV Search
screen. Use `mcp-playback-test --direct-only` for deterministic fixture gates:
an exact-path failure is then reported immediately and Search is never opened.
Use `--force-ui-search` only when the Search workflow itself is the subject of
the test.

## Covered settings

The schema records:

- multiple ADB devices/clients with aliases, model/API notes, and test IDs;
- multiple SageTV servers with aliases, MiniClient port, stock/Vibe status,
  Web/Sagex endpoints, Web Remote context, optional Unraid host-management
  details, and local credentials;
- per-server SMB shares, credentials, configuration directory, and path maps;
- multiple named media fixtures with one generic `path`, a `path_type`, a
  master `enabled` switch, and independently enabled test modes;
- exact generated/UK-broadcast server paths plus repeatable stock-library MKV
  MediaFile searches for strict player matrices, including the common-clock
  `pbs_av_sync` MPEG-2/AC-3 fixture used by the physical A/V gate;
- HDMI capture backend/device names and artifact directory;
- player, transport, hardware-decoding, renderer, live-channel, timeout, and
  observation defaults;
- protected package and destructive-test safety settings.

Command-line arguments continue to override TOML defaults. Specialized tests
still require an explicit media path when automatically choosing a file would
be unsafe. The config checker reports missing selections, duplicate aliases,
malformed fixture records/mode switches, malformed SMB mappings, and protected
package IDs with actionable messages. `path_type = "server_path"` means an
exact SageTV-side file/DVD path, `server_root` means a generated fixture
directory, and `search` means SageTV resolves the generic `path` text to a
MediaFile.

## Storage warm-up in playback matrices

Storage handling is adaptive and independent for every server/file identity.
On its first use, a file receives up to the extended startup allowance. If it
starts within the normal threshold, that first start remains the measured
result. If it is slow, the harness lets playback become healthy, discards only
that startup timing, rebuilds the bounded playback session, and measures the
second start under normal gates. Every successful use refreshes the same
file's sliding recent-use timeout; another file starts its own interval. JSON
reports distinguish normal-first, slow-discard/retry, recent-file, and explicit
cold-start paths. Use `--skip-storage-warmup` only when cold startup itself is
the intended measurement.

## HDMI capture

Capture independent HDMI evidence on Windows from the project root with the
Python-backed command below. It does not depend on PowerShell script execution
policy:

```cmd
capture_hdmi_validation.cmd --output artifacts\firetv\validation.mp4 --duration 45
```

Use `--video-device` and `--audio-device` when the DirectShow names differ
from the commissioned USB capture adapter defaults. The command uses the
compiled sibling `opensagetv-vibe-ffmpeg-mim` Windows binary by default;
`VIBE_FFMPEG_PATH` can point to another Vibe `ffmpeg.real.exe` build.

## Secrets and release safety

Put real Web and SMB credentials only in `config/firetv.toml` or supported
environment overrides. `dev.cmd config-check --summary` redacts usernames,
passwords, tokens, keys, and credentials. Do not paste the local TOML into
issues or commits.

GitHub source/APK releases contain `config/firetv.example.toml`, never the
local file. The APK inspector and workflow tests enforce that boundary. The
schema-1 root keys (`device`, `dev_package`, `adb`, `artifact_dir`, and `aapt`)
remain accepted so existing local files continue to work.
