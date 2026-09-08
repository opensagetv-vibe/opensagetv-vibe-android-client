# OpenSageTV Vibe Android Client

This repository contains the actively maintained Android/Fire TV SageTV
MiniClient, its Docker build workflow, host tests, and guarded MCP/ADB tooling.
It is self-contained and can be developed independently from Codex or any
other AI tool.

The protected source project `SageTV-MiniClient-Dev` is a known-good reference,
not a build dependency. Do not modify it from this repository.

The sibling `opensagetv-vibe-logo` repository is the authoritative branding
source. Android test, validation, build, and bundle commands regenerate its
assets in the unified container, copy 34 validated resources into this source
tree, and update `config/logo-assets.sha256`. Do not hand-edit those PNG/XML
resources.

## Project status

- Current checkout version: see `VERSION`.
- Release history: `CHANGELOG.md`.
- Active work: `TASKS.md` (the only task backlog in this repository).
- Current takeover state: `HANDOFF.md`.
- Contributor/AI rules: `AGENTS.md`.
- Common takeover/update commands: `WORKFLOW.md`.
- Legacy ExoPlayer remains the default player until physical-device gates pass.
- Production package identities under `jvl.sage.miniclient` are protected.
- The selected application IDs are `opensagetv.vibe.miniclient` for production
  and `opensagetv.vibe.miniclient.debug` for development; both display as
  `OpenSageTV Vibe`.
- Media3, legacy ExoPlayer, and GSY's extractor-backed delegates implement the
  standard hardware-extender subtitle callback. They return raw CEA packets to
  SageTV through event 225, so an unmodified server/STV owns CC On/Off and
  CC1/CC2/DTVCC selection and renders the result. The optional Vibe
  `VIDEO_CC_STATE` extension remains backward compatible but is no longer
  required for these players. IJK cannot expose raw extractor packets; its
  long-press CC menu retains explicit `Off`, `CC1`, and `CC2` fallback choices.
- `Settings > Playback Settings > Audio and Caption Track Settings` optionally
  selects preferred BCP-47 audio/subtitle languages and a CEA-608 channel or
  CEA-708 service. These preferences select among tracks only after the STV
  enables captions; the standard event-225 path works with an unmodified
  SageTV server. The same page also stores the legacy server fallback used by
  the long-press overlay.

Completing a task includes updating task tracking immediately: remove the
finished item from `TASKS.md`, synchronize the parent workspace `task.md` when
it mirrors that work, and record evidence in `CHANGELOG.md`/`HANDOFF.md`.

## Required workspace layout

Normal development uses the sibling unified build environment:

```text
projects/
  opensagetv-vibe-build-env/
  opensagetv-vibe-android-client/
```

The unified image is `opensagetv-vibe-build-env:u26-j11`; the reusable
container is `opensagetv-vibe-dev`. Android commands select JDK 17 inside that
container. Source edits do not require an image rebuild.

## Quick start

Linux or WSL Bash:

```bash
./dev.sh test
./dev.sh validate
./dev.sh build
./dev.sh bundle
```

Windows Command Prompt or PowerShell:

```bat
dev.cmd test
dev.cmd validate
dev.cmd build
dev.cmd bundle
```

The PowerShell wrapper passes the real sibling build-environment path into WSL,
avoiding Docker Desktop temporary bind paths that can hide sibling projects.

Common commands:

```text
image                 build/update the unified development image
test                  run host/unit/static tests
validate              validate structure, frozen baseline, and manifests
build                 build the Dev APK
bundle                build/validate debug and release-candidate AABs plus debug APK set
bundle-install        guarded install of the package-verified debug AAB APK set
shell                 open a shell in the reusable container
stop-dev              stop the development container
remove-dev            remove only the development container
install               guarded Dev APK install
launch                launch Dev with deterministic DEV001 test identity
client-id              inspect or explicitly manage the test client identity
mcp-test              exercise the MCP protocol
mcp-player-matrix     run device-backed playback cases
```

For a new machine or AI handoff, run `commission_test_environment.cmd` on
Windows or `./commission_test_environment.sh` on Linux/WSL. The first run
creates the ignored local TOML; after it is edited, the same command validates
Docker/ADB, creates or reuses every canonical test fixture, runs all local
gates, and builds the APK. See `docs/COMMISSIONING.md`.

`bundle` uses the checksum-pinned bundletool in the unified image. It produces
a debug AAB, a package-installable debug `.apks` set, and a clearly named
development-signed release candidate under `artifacts/firetv`. The release
candidate proves the pipeline only; it is not publishable until the approved
Play upload/signing key and remaining store requirements are configured.

### Fire TV sideload launcher artwork

The project builds and installs one Android client APK. It owns both the
standard `LAUNCHER` entry and the TV `LEANBACK_LAUNCHER` entry; there is no
separate launcher package. The generated mask-safe square/adaptive Vibe icon is
the APK and inherited activity icon, while the generated 16:9 image is assigned
only to the Leanback activity's banner and logo.

Current Fire OS represents a newly sideloaded package with its square APK icon
inside a gray tile. It does not use an embedded APK banner as Amazon catalog
art. Fire OS can retain stale or empty artwork across update installs; the
physical device gate therefore removes the Vibe package before validating a
new launcher-artwork revision. Clean API-25 and API-30 tests also proved that
forcing the 16:9 banner into `android:icon` omits Vibe from the launcher. The
logo pipeline therefore keeps all wordmark pixels inside the square/adaptive
mask and also prepares the opaque,
safe-area-compliant
1280x720 submission asset at
`source/dev/android-tv/store-assets/amazon-fire-tv-app-icon-1280x720.png`.
The same directory contains the 114x114/512x512 tablet icons and title-free
opaque 1920x1080 Fire TV background. These files are for an eventual Amazon
Appstore listing and are not packaged into the APK. Three to ten real,
non-private 1920x1080 app screenshots remain a publication gate.

Run `./dev.sh help` or `dev.cmd help` for the complete command set. `dev.cmd`
uses the repository's `dev.ps1` with an execution-policy override limited to
that process; it does not change the machine policy.

## Incremental update workflow

Place a changed-files ZIP in `artifacts/downloads`, then run:

```bash
./update.sh
```

or on Windows:

```bat
update.cmd
```

The workflow selects only a newer semantic version, validates safe and unique
archive paths, verifies the complete checkout manifest before extraction,
resumes after a failed test/build/install step, and launches with the scripted
DEV001 identity after preparation. This final guarded Dev launch is the
Android-specific install/commission action; the four preparation gates remain
the same as the other repositories.

An update ZIP must contain:

- `VERSION`;
- stable machine metadata `release.properties`;
- stable, validated removal metadata `release-deletions.lst` (empty when no
  tracked files are removed);
- the complete updated `PROJECT_MANIFEST.sha256`;
- `CHANGELOG.md` and any durable documentation affected by the change;
- every changed payload file.

`release.properties` contains exactly one `VERSION=x.y.z` and one
`REQUIRES_BUILD=true|false`. Release history belongs only in `CHANGELOG.md`.
Markdown inside the frozen or active imported source trees remains upstream
application/source documentation and must not be used for Vibe release notes.
Do not create `UPDATE_v*.txt`, version-named Markdown notes, AI prompt files, or
separate task lists for future releases.

`release-deletions.lst` contains explicit repository-relative file paths only.
The runner rejects unsafe paths, directories, symlink escapes, control-file
deletion, and paths still present in the new full manifest.

## Device safety and first-time setup

Automation may install, launch, stop, clear, or uninstall only the configured
development package, normally `opensagetv.vibe.miniclient.debug`. It must
refuse any `jvl.sage.miniclient*` target.

After a fresh install or Clear Data, complete first-time setup before any automated MCP/player test.
Manually launch the Dev app, select the SageTV server, and reach the normal
MiniClient UI. Scripted tests default to client ID
`44:45:56:30:30:31` (`DEV001`); normal interactive use retains the app's
generated/persisted ID unless explicitly changed.

## MCP and playback testing

The MCP server and ADB tools run in the same unified container. Configure
`config/firetv.toml`, then use `./dev.sh mcp-test` and the relevant playback
command. Read `mcp/README.md` for tool usage and
`docs/PLAYBACK_DIAGNOSTICS.md` before diagnosing playback.

Physical-test scripts use the one ignored `config/firetv.toml` for named and
aliased Android clients, SageTV servers/Web credentials, per-server SMB
shares/mappings, fixtures, HDMI capture, identities, safety policy, and test
defaults. Run `dev.cmd config-check` before commissioning. Command-line and
supported environment overrides still win. The checked-in complete example
contains only documentation addresses and blank credentials; see
`docs/TEST_ENVIRONMENT.md`.

Physical tests must record the APK hash, device/API, server build, backend,
streaming mode, decoder, media state, visible A/V result, and correlated
Android/server evidence. Watchdog expiration is an observation, not automatic
fault attribution.

The commissioned Vibe server supports an opt-in debug-only exact-path start
that avoids Search UI navigation:

```bat
dev.cmd mcp-session-test --server-address 192.168.10.232 --no-save-server --player media3 --streaming push --decoding hardware --server-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts" --exit none
```

Run the repeatable completed-file background/return/replay/teardown gate with:

```powershell
dev.cmd mcp-lifecycle-test --server-address 192.168.10.232 --player media3 --streaming dynamic --decoding hardware --server-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts"
dev.cmd mcp-eof-test --server-address 192.168.10.232 --player media3 --decoding hardware --server-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts"
dev.cmd mcp-caption-test --server-address 192.168.10.232 --player media3 --streaming dynamic --decoding hardware --server-path "/var/media/tv/MeetthePress-65149351-0.ts"
dev.cmd mcp-frame-step-test --server-address 192.168.10.232 --player media3 --streaming pull --server-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts"
dev.cmd mcp-fast-switch-test --initial-path "/var/media/videos/OpenSageTV-Vibe-Kodi-Codec-Test/mpeg2-interlaced-bframes.ts" --switch-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts" --streaming pull
dev.cmd mcp-fast-switch-test --initial-path "/var/media/videos/OpenSageTV-Vibe-Kodi-Codec-Test/mpeg2-interlaced-bframes.ts" --switch-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts" --streaming smb_direct
dev.cmd mcp-playback-rate-test --server-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts" --player media3 --streaming pull --server-negotiation
dev.cmd mcp-playback-rate-test --server-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts" --player exoplayer --streaming smb_direct
dev.cmd mcp-codec-capability-test --player media3 --streaming pull
dev.cmd mcp-codec-capability-test --player exoplayer --streaming pull
```

The Android Options screen has a **Home and background recovery** submenu.
Session preservation is opt-in. Its separate resume switch only resumes media
that Home interrupted while actively playing; a user-paused or idle session is
left alone. The MCP gate can prove the opt-out path with
`--no-resume-background-playback`, and can prove timeout cleanup with
`--session-timeout-seconds 2 --background-seconds 4 --expect-session-timeout`.

The capability tests take one bounded decoder inventory/snapshot, verify both
hardware and software MPEG-2 candidates exist, and record the decoder actually
selected. They do not enable continuous player telemetry.

`mcp-fast-switch-test` is a debug-only physical diagnostic for Media3's
compatible completed-file replacement. It starts the first exact server path,
replaces it with the second path below the SageTV navigation layer, and requires
a rendered first frame, advancing hardware playback, the requested datasource,
the exact target URL, no process crash, and zero fallback. Production SageTV
loads still decide eligibility conservatively and use the normal full-load path
whenever compatibility is not proven.

`mcp-playback-rate-test` is the hardware-only completed-file gate for native
0.5x-2x forward playback and seek-based +/-4x-256x scan. The optional
`--server-negotiation` flag also requires SageTV Smooth FF/REW to negotiate and
send MiniPlayer command 30. Push, Fixed/MIM, DVD, live/growing, circular,
external-link, and GSY/System paths deliberately keep their established
fallback behavior.

`mcp-caption-test` defaults to `--authority stv`: it does not invoke the
Android debug track selector and fails unless the SageTV STV's caption state
selects and renders a real cue. Use `--authority debug` only for isolated
decoder diagnostics.

Long-press the remote's navigation key during playback and select the bar-chart
icon to toggle detailed Playback Stats directly, or open **Active Player
Adjustments > Playback Stats overlay** for compact or detailed live
troubleshooting. The overlay supports persistent and bounded 30-second views
plus a redacted export. The first submenu row shows a checked/unchecked icon
and toggles the detailed view directly. MCP can perform the same operation with
`dev_set_active_player_overlay(mode="toggle")`; deterministic `off`, `compact`,
`detailed`, and `detailed_30s` modes are also available, while the older `visible`
Boolean remains compatible. It always shows only available common playback data and
adds Pull, SMB Direct, Push/Fixed, caption, error/recovery, or DVD rows only for
the active mode. Its three live bars show actual media-byte activity, buffered
playback time, and CPU. The CPU bar uses a fixed 0-100% scale with the Vibe
process and the remainder of total device usage in separate colors; the `Vibe`
and `Other` label values use those matching colors and `Total` remains neutral.
The values are shown once above the bar. Duplicate text rows and the unhelpful estimated
link-capacity bar are omitted. Sampling starts with the visible overlay and is
cancelled when it is hidden or the playback Activity leaves the foreground.

Exercise an explicit service without bypassing STV authority:

```powershell
dev.cmd mcp-caption-test --server-address 192.168.10.232 --player media3 --streaming pull --decoding hardware --authority stv --preferred-caption-standard cea708 --preferred-caption-service 1 --server-path "/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts"
```

This requires `VIBE_TEST_CONTROL=true` in the isolated test server container.
Keep it false for normal installations. Success requires advancing video and
audio output and entry into the normal full-screen playback UI.

### Deterministic prerecorded/live transport fixture

Generate the canonical MPEG-TS fixture entirely in the unified development
container:

```powershell
dev.cmd seek-fixture artifacts/test-media/VibeSeekTest-1080i-MPEG2-AC3-CC.ts 900
```

It contains 1080i MPEG-2, 5.1 and stereo AC-3, real in-band ATSC A/53 CEA-608
CC1 and CEA-708 Service 1, a burned PTS/frame clock, and deterministic Comskip
EDL intervals. A synchronized white visual flash and louder audio pulse occur
for 120 ms at every whole second, making A/V and caption offset visible in the
same HDMI capture. The MCP `generate_seek_fixture` tool can recreate and
publish the same fixture to the configured SMB test share without returning
credentials.

Generate the short Kodi-derived codec/profile/bitstream matrix with the same
unified container:

```powershell
dev.cmd codec-fixtures --duration 6 --output-dir artifacts/test-media/kodi-codec
```

The resulting `fixture-manifest.json` records SHA-256 and `ffprobe` metadata.
Cases that cannot be created honestly from FFmpeg are recorded as unsupported
or fault-injection-only rather than reported as passes. VC-1 is not an Android
client completion gate because neither commissioned Fire TV advertises VC-1
hardware support.

### Deterministic authored DVD fixture

Create the complete DVD-Video fixture with the unified build environment:

```powershell
dev.cmd dvd-fixture
```

The default output is `artifacts/test-media/VIBE_AUTHORED_DVD`. It contains a
root menu plus language, chapter, and special-feature submenus; four main-title
chapters; English and Spanish-tagged AC-3 streams; English and Spanish
timestamped DVD SPU streams; and a burned PTS/frame counter for visual sync and
seek verification. Its `VIBE_DVD_TEST_MANIFEST.json` records every authored
file's size and SHA-256. Publish the directory to the commissioned media share
and test the indexed server path with `dev.cmd mcp-disc-test`.

Each visible English/Spanish subtitle includes `CUE`, `PTS`, nearest `FRAME`,
and `CHAPTER`. Compare that printed subtitle PTS/frame with the simultaneously
burned video PTS/frame in a screenshot; both are generated from the fixture's
shared cue clock.

The currently commissioned copy is:

```text
SMB:    \\192.168.10.175\sagemedia\videos\OpenSageTV_Vibe_Test_DVD
SageTV: /var/media/videos/OpenSageTV_Vibe_Test_DVD
```

DVD authoring uses FFmpeg/FFprobe, dvdauthor/spumux, ImageMagick, and DejaVu
fonts supplied by `opensagetv-vibe-build-env`; no host media tools are needed.

## Artifacts

Generated APKs, logs, test results, screenshots, and update-runner state live
under `artifacts/` and other ignored runtime directories. The expected debug
APK name is:

```text
OpenSageTV-Vibe-Android-Client-debug.apk
```

## Documentation map

- `CHANGELOG.md` — all version history.
- `TASKS.md` — sole active Android backlog.
- `HANDOFF.md` — current state and exact resume point.
- `AGENTS.md` — mandatory rules for humans and AI tools.
- `DOCKER_WORKFLOW.md` — build/container/update operations.
- `MIGRATION_TO_OPENSAGETV_VIBE.md` — source provenance and migration boundary.
- `PLAYER_ARCHITECTURE.md` — current backend and transport architecture.
- `PLAYER_TELEMETRY.md` — safe diagnostic instrumentation policy.
- `docs/PLAYER_SERVER_COMPATIBILITY.md` — tested player, transport, Android
  decoder, SageTV/Core, FFmpeg/MIM, server-GPU, and fallback boundaries.
- `docs/PLAYBACK_DIAGNOSTICS.md` — evidence and experiment standard.
- `docs/CONNECTION_PROTOCOL_LIFECYCLE.md` — characterized connection,
  command-order, reconnect, and teardown contract for safe class splitting.
- `docs/TEST_ENVIRONMENT.md` — single local TOML schema, client/server aliases,
  per-server SMB, validation, overrides, and release-secret boundary.
- `docs/COMMISSIONING.md` — one-command environment, fixture, validation, and
  optional guarded-install workflow for humans and AI tools.
- `docs/BASELINE_VALIDATION.md` — migration/copy equivalence evidence.
- `mcp/README.md` — MCP setup, safety, and current commands.

The project is licensed under Apache License 2.0; see `LICENSE`.
