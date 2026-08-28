# Windows Workspace Setup

Extract the full package into any Windows folder. For example:

`C:\SageTV-MiniClient-Dev`

or

`C:\TMP_SAGETV_DOCKER\SageTV-MiniClient-Dev`

Expected layout:

```text
C:\SageTV-MiniClient-Dev\
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
cd /mnt/c/TMP_SAGETV_DOCKER/SageTV-MiniClient-Dev   # use your actual extracted path
./dev.sh image
./dev.sh test
./dev.sh validate
```

No `.env` file is required. The directory containing `dev.sh` is mounted to `/workspace` automatically.

## Windows Docker CLI workflow

Run from the extracted project root. Docker mounts that directory to `/workspace` automatically. Use `.env.example` only if you intentionally need an explicit `SAGETV_WINDOWS_ROOT` override.

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

`<project-root>\artifacts\firetv\SageTV-MiniClient-Dev-debug.apk`
