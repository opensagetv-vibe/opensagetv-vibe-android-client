# Common project workflow

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
