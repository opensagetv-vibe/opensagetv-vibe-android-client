# Windows Workspace Setup

Clone or extract the repository into any Windows folder. For example:

`C:\source\opensagetv-vibe-android-client`

or

`C:\TMP_SAGETV_DOCKER\projects\opensagetv-vibe-android-client`

Expected layout:

```text
C:\source\opensagetv-vibe-android-client\
├─ docker\
├─ mcp\
├─ scripts\
├─ source\
│  ├─ existing\
│  ├─ dev\
│  └─ SOURCE_IMPORT.json
├─ incoming\
├─ artifacts\
├─ adb\
├─ config\
├─ logs\
├─ screenshots\
└─ recordings\
```

The source trees are already included. `incoming` is only for future replacement source ZIPs.

The Gradle cache remains a Docker named volume for performance.

## WSL workflow

```bash
cd /mnt/c/TMP_SAGETV_DOCKER/projects/opensagetv-vibe-android-client
./dev.sh image
./dev.sh test
./dev.sh validate
```

No `.env` file is required. The directory containing `dev.sh` is mounted to `/workspace` automatically.

## Windows Docker CLI workflow

Run from the repository root. Docker mounts that directory to `/workspace`
automatically. Use `.env.example` only if you intentionally need an explicit
`OPENSAGETV_VIBE_ANDROID_ROOT` override.

## Build untouched/current app

```bash
./compile_existing_app.sh
```

It builds only `source/existing` and does not install it.

## Build Dev app

```bash
./dev.sh build
```

Output:

`<project-root>\artifacts\firetv\OpenSageTV-Vibe-Android-Client-debug.apk`
