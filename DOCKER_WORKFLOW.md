# Android Docker workflow

## Normal architecture

The Android repository is a mounted component of the sibling
`opensagetv-vibe-build-env` project. Normal commands reuse:

```text
image:     opensagetv-vibe-build-env:u26-j11
container: opensagetv-vibe-dev
workspace: /workspace/android-client
JDK:       /opt/java/jdk17
```

Java 11 remains the unified server/build-environment default. Android selects
JDK 17 only for its Gradle tasks. The frozen `source/existing` build selects its
required legacy toolchain separately.

## Host commands

Linux/WSL:

```bash
./dev.sh image
./dev.sh test
./dev.sh validate
./dev.sh build
./dev.sh shell
```

Windows Command Prompt or PowerShell:

```bat
dev.cmd image
dev.cmd test
dev.cmd validate
dev.cmd build
dev.cmd shell
```

The PowerShell wrapper converts the sibling build-environment path to an
explicit WSL path. This is required because Docker Desktop can expose the
current repository through a temporary `/mnt/wsl/...` bind path whose parent
does not contain sibling repositories.

The unified image is rebuilt only when its toolchains/dependencies change.
Source edits reuse the running container and Gradle caches.

## Device commands

Configure `config/firetv.toml`, complete manual first-time setup after a fresh
install, and then use the guarded install/launch/MCP commands. Package mutation
must remain restricted to `opensagetv.vibe.miniclient.debug`.

## Incremental update

Run `./update.sh` or `update.cmd`. The Windows `.cmd` entry points invoke their
repository PowerShell implementation without changing the machine-wide
execution policy. The runner:

1. selects the highest newer changed-files ZIP;
2. validates ZIP integrity, safe/unique paths, `VERSION`,
   `release.properties`, `release-deletions.lst`, symbolic-link safety, and the
   full manifest before extraction;
3. applies it once;
4. resumes TEST, VALIDATE, optional BUILD/INSTALL, and LAUNCH from durable state
   under `artifacts/update_runner`.

Every package uses the stable `release.properties`; release history is updated
only in `CHANGELOG.md`. Never create per-version update text/Markdown files.
