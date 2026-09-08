# Commission the development and test workflow

This guide creates a repeatable OpenSageTV Vibe Android environment for a
human, Codex, or another AI tool. It uses the repository root commands, the
sibling unified `opensagetv-vibe-build-env`, one reusable
`opensagetv-vibe-dev` container, and one ignored local TOML.

## Prerequisites

- Docker Desktop/Engine is running.
- `opensagetv-vibe-build-env` is beside this repository.
- ADB debugging is enabled and authorized on each test device.
- Test recordings/shares are owned by the user and safe to access.
- Fresh installs are manually taken through first-time setup before automated
  playback tests.

## One-command workflow

On Windows:

```bat
commission_test_environment.cmd
```

On Linux/WSL:

```bash
./commission_test_environment.sh
```

On the first run, the command copies the sanitized tracked example to the
ignored `config/firetv.toml` and exits. Edit that file, keeping every SMB field
directly within its `[servers.<name>]` area, then rerun the command.

The normal run performs:

1. TOML schema, alias, mapping, and package-safety validation;
2. unified Docker/JDK/Android/ADB/MCP preflight;
3. creation or reuse of the canonical seek/CC/Comskip transport fixture, Kodi
   codec fixtures, and authored DVD fixture;
4. complete host/unit/static tests, project validation, and clean APK build.

Existing fixture outputs are reused. Use `-Regenerate` on Windows or
`--regenerate` on Linux only when they must be rebuilt. Use `-SkipFixtures` /
`--skip-fixtures` or `-SkipBuild` / `--skip-build` for a bounded rerun.

Installation is deliberately opt-in:

```bat
commission_test_environment.cmd -Install
```

```bash
./commission_test_environment.sh --install
```

The guarded installer accepts only `opensagetv.vibe.miniclient.debug` and
refuses the protected JVL namespace. It does not automate first-time setup.

## Publish fixtures to the selected server

Generated files remain under ignored `artifacts/test-media`. The TOML's active
server provides `smb_url`, credentials, and `smb_mappings`, but the
commissioning command does not overwrite a remote share automatically. Copy
or synchronize the generated fixtures to that server's configured SMB media
location, import/rescan them in SageTV, and set the corresponding paths in the
TOML `[fixtures]` table. This explicit step prevents an AI or new contributor
from overwriting recordings merely by running setup.

Then run `dev.cmd config-check`, followed by the relevant MCP physical gate.
Every physical result must follow `docs/PLAYBACK_DIAGNOSTICS.md` and record the
APK hash, selected device/server aliases, backend, transport, hardware decoder,
fixture, visible result, and captured diagnostics.

## GitHub and handoff safety

Commit `config/firetv.example.toml`, this guide, and the workflow scripts. Never
commit `config/firetv.toml`, generated fixtures, captures, APKs, ADB keys, or
credentials. GitHub source/APK bundles and AI handoffs include the sanitized
example so another user can reproduce the workflow with their own values.
