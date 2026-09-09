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

## Covered settings

The schema records:

- multiple ADB devices/clients with aliases, model/API notes, and test IDs;
- multiple SageTV servers with aliases, MiniClient port, stock/Vibe status,
  Web/Sagex endpoints, Web Remote context, and local credentials;
- per-server SMB shares, credentials, configuration directory, and path maps;
- prerecorded, caption, DVD, and other deterministic media fixtures;
- repeatable stock-library MKV MediaFile names for the strict all-player matrix;
- HDMI capture backend/device names and artifact directory;
- player, transport, hardware-decoding, renderer, live-channel, timeout, and
  observation defaults;
- protected package and destructive-test safety settings.

Command-line arguments continue to override TOML defaults. Specialized tests
still require an explicit media path when automatically choosing a file would
be unsafe. The config checker reports missing selections, duplicate aliases,
malformed SMB mappings, and protected package IDs with actionable messages.

## HDMI capture

Capture independent HDMI evidence on Windows from the project root with the
Python-backed command below. It does not depend on PowerShell script execution
policy:

```cmd
capture_hdmi_validation.cmd --output artifacts\firetv\validation.mp4 --duration 45
```

Use `--video-device` and `--audio-device` when the DirectShow names differ
from the commissioned USB capture adapter defaults. `VIBE_VLC_PATH` can point
to a non-default `vlc.exe` installation.

## Secrets and release safety

Put real Web and SMB credentials only in `config/firetv.toml` or supported
environment overrides. `dev.cmd config-check --summary` redacts usernames,
passwords, tokens, keys, and credentials. Do not paste the local TOML into
issues or commits.

GitHub source/APK releases contain `config/firetv.example.toml`, never the
local file. The APK inspector and workflow tests enforce that boundary. The
schema-1 root keys (`device`, `dev_package`, `adb`, `artifact_dir`, and `aapt`)
remain accepted so existing local files continue to work.
