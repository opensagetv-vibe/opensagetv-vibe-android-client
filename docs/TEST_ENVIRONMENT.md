# Local test environment

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

## Multiple clients and servers

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
  Web/Sagex endpoints, Web Remote context, and local credentials;
- per-server SMB shares, credentials, configuration directory, and path maps;
- multiple named media fixtures with one generic `path`, a `path_type`, a
  master `enabled` switch, and independently enabled test modes;
- exact generated/UK-broadcast server paths plus repeatable stock-library MKV
  MediaFile searches for strict player matrices;
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
