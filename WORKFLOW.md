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

Download changed-files packages to `artifacts/downloads`. The hardened Android
runner follows the common contract and verifies paths, duplicates, metadata,
payload/full-manifest hashes and
baseline drift before extraction, then resumes test, validate, build, and
install. The package ID remains `opensagetv-vibe-android` for compatibility.
Android then performs its guarded DEV001 launch as its commissioning action.
The sibling build environment's `WORKFLOW.md` defines the common contract.
