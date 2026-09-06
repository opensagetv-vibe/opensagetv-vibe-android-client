# Contributing to OpenSageTV Vibe Android Client

Thank you for helping modernize the SageTV MiniClient while preserving its
protocol and playback compatibility.

Read `AGENTS.md`, `README.md`, `HANDOFF.md`, and `TASKS.md` before changing the
project. `TASKS.md` is the only active backlog; completed work belongs in
`CHANGELOG.md` and the current resume state belongs in `HANDOFF.md`.

Use the shared `opensagetv-vibe-build-env` through the repository-root
interface:

```text
dev.cmd test
dev.cmd validate
dev.cmd build
```

On Linux or WSL, use the equivalent `./dev.sh` commands. Do not add a second
Android build image. Changes that affect the APK must set
`REQUIRES_BUILD=true` in `release.properties`, add focused tests, regenerate
`PROJECT_MANIFEST.sha256`, and pass `git diff --check`.

Preserve the stock SageTV control session and keep optional extensions
capability-negotiated. Physical playback claims must identify the device,
backend, transport, decoder, fixture, and observable result; skipped hardware
checks must be recorded as `SKIPPED`, not `PASS`.

Do not commit credentials, production settings, private media, device captures,
keystores, generated APKs, or local `config/firetv.toml` files.
