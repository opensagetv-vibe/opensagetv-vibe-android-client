#!/usr/bin/env bash
set -euo pipefail

WORKSPACE="${SAGETV_WORKSPACE:-/workspace}"
PROJECT="$WORKSPACE"
DEV_SRC="${SAGETV_DEV_SOURCE:-$WORKSPACE/source/dev}"
EXISTING_SRC="${SAGETV_EXISTING_SOURCE:-$WORKSPACE/source/existing}"
ARTIFACTS="$WORKSPACE/artifacts"
INCOMING="$WORKSPACE/incoming"
export PYTHONPATH="$PROJECT/mcp/src${PYTHONPATH:+:$PYTHONPATH}"
export SAGETV_MCP_CONFIG="${SAGETV_MCP_CONFIG:-$WORKSPACE/config/firetv.toml}"
export SAGETV_ARTIFACT_DIR="${SAGETV_ARTIFACT_DIR:-$WORKSPACE/artifacts/firetv}"

mkdir -p \
  "$WORKSPACE/source/dev" \
  "$WORKSPACE/source/existing" \
  "$INCOMING" \
  "$WORKSPACE/artifacts/firetv" \
  "$WORKSPACE/artifacts/existing" \
  "$WORKSPACE/logs" \
  "$WORKSPACE/screenshots" \
  "$WORKSPACE/recordings" \
  "$WORKSPACE/config" \
  "$WORKSPACE/adb" \
  /gradle-cache

require_project_mount() {
  local missing=0 marker
  for marker in dev.sh scripts/run_unit_tests.sh mcp/pyproject.toml docker-compose.yml; do
    if [[ ! -f "$WORKSPACE/$marker" ]]; then
      echo "ERROR: workspace bind is missing $marker" >&2
      missing=1
    fi
  done
  if [[ "$missing" -ne 0 ]]; then
    cat >&2 <<EOF
ERROR: /workspace is not the OpenSageTV Vibe Android Client repository root.
The Docker bind source is pointing at the wrong host directory.

Current container workspace: $WORKSPACE
Expected project markers: dev.sh, scripts/, mcp/, docker-compose.yml

Normally no path configuration is required; the directory containing dev.sh is mounted automatically.
To override it explicitly from WSL:
  OPENSAGETV_VIBE_ANDROID_ROOT=/mnt/c/path/to/opensagetv-vibe-android-client ./dev.sh <command>
EOF
    exit 2
  fi
}

require_project_mount

# Persist the ADB host identity on the Windows workspace so Fire TV authorization
# survives short-lived containers and image rebuilds.
if [[ -e /root/.android && ! -L /root/.android ]]; then
  rm -rf /root/.android
fi
ln -sfn "$WORKSPACE/adb" /root/.android

if [[ ! -f "$WORKSPACE/config/firetv.toml" && -f "$PROJECT/config/firetv.example.toml" ]]; then
  cp "$PROJECT/config/firetv.example.toml" "$WORKSPACE/config/firetv.toml"
fi

usage() {
  cat <<'USAGE'
OpenSageTV Vibe Android Client development environment

All Android/ADB/Python/MCP/Gradle work runs in this image.
Workspace bind: current repository by default (OPENSAGETV_VIBE_ANDROID_ROOT can override)

Commands:
  import-source ZIP [--replace]
                           Import exact repo ZIP into untouched baseline + Dev trees
  bootstrap [args...]     Clone pinned GitHub source into Dev tree and refactor
  bootstrap-existing     Clone pinned untouched GitHub baseline
  bootstrap-both         Clone pinned baseline into both trees; refactor Dev tree
  validate               Validate isolated app identity/Firebase removal
  build                  Build isolated Dev debug APK + SHA-256
  build-existing         Build untouched baseline APK + SHA-256 (never installs)
  test                   Run scaffold unit/static tests
  preflight              Verify toolchain/workspace/config/Fire TV connectivity
  connect                Connect configured Fire TV through container ADB
  install [apk]          Safely verify and install Dev APK only
  launch|stop|uninstall  Control isolated Dev app only
  device-info            Print Fire TV identity/build information
  logcat [args...]       Read Fire TV logcat
  adb [args...]          Raw ADB diagnostic escape hatch
  mcp                    Start Python Fire TV MCP over stdio (waits for a client)
  mcp-test               Run real MCP stdio handshake/tool-call smoke test
  shell                  Interactive development shell
USAGE
}

require_dev_source() {
  [[ -f "$DEV_SRC/gradlew" ]] || { echo "Dev source missing. Import exact source ZIP or run bootstrap-both." >&2; exit 2; }
}

build_dev_apk() {
  require_dev_source
  "$PROJECT/scripts/ensure_debug_keystore.sh"
  python3 "$PROJECT/scripts/validate_project.py"
  mkdir -p "$ARTIFACTS/firetv"
  chmod +x "$DEV_SRC/gradlew"
  cd "$DEV_SRC"
  ./gradlew --no-daemon clean :android-tv:assembleDebug
  APK="$(find android-tv/build/outputs/apk -type f -name '*debug*.apk' | head -n 1)"
  [[ -n "$APK" ]] || { echo "Gradle completed but no Dev debug APK was found" >&2; exit 3; }
  OUT="$ARTIFACTS/firetv/OpenSageTV-Vibe-Android-Client-debug.apk"
  cp -f "$APK" "$OUT"
  sha256sum "$OUT" | tee "$OUT.sha256"
  echo "Built isolated Dev APK: $OUT"
}

build_existing_apk() {
  if [[ ! -f "$EXISTING_SRC/gradlew" ]]; then
    echo "Untouched baseline source is absent; bootstrapping pinned upstream baseline..."
    python3 "$PROJECT/scripts/bootstrap.py" --dest "$EXISTING_SRC" --skip-refactor
  fi
  "$PROJECT/scripts/ensure_debug_keystore.sh"
  mkdir -p "$ARTIFACTS/existing"
  chmod +x "$EXISTING_SRC/gradlew"
  cd "$EXISTING_SRC"
  # The untouched baseline intentionally remains on Gradle 6.1.1 / AGP 4.0.2.
  # Run it with the bundled JDK 8 while the Dev/Media3 build uses container JDK 17.
  JAVA_HOME=/opt/java/jdk8 PATH=/opt/java/jdk8/bin:$PATH ./gradlew --no-daemon :android-tv:assembleDebug
  APK="$(find android-tv/build/outputs/apk -type f -name '*debug*.apk' | head -n 1)"
  [[ -n "$APK" ]] || { echo "Gradle completed but no baseline debug APK was found" >&2; exit 3; }
  OUT="$ARTIFACTS/existing/OpenSageTV-Vibe-Android-Client-v0.5.75-baseline-debug.apk"
  cp -f "$APK" "$OUT"
  sha256sum "$OUT" | tee "$OUT.sha256"
  [[ -f "$EXISTING_SRC/UPSTREAM_BASELINE.txt" ]] && cp -f "$EXISTING_SRC/UPSTREAM_BASELINE.txt" "$ARTIFACTS/existing/UPSTREAM_BASELINE.txt"
  [[ -f "$WORKSPACE/source/SOURCE_IMPORT.json" ]] && cp -f "$WORKSPACE/source/SOURCE_IMPORT.json" "$ARTIFACTS/existing/SOURCE_IMPORT.json"
  echo "Built UNMODIFIED baseline APK: $OUT"
  echo "This command DOES NOT install or replace the working MiniClient on the Fire TV."
}

case "${1:-shell}" in
  import-source)
    shift
    [[ $# -ge 1 ]] || { echo "Usage: import-source /workspace/incoming/repo.zip [--replace]" >&2; exit 2; }
    archive="$1"; shift
    exec python3 "$PROJECT/scripts/import_source_zip.py" "$archive" --existing "$EXISTING_SRC" --dev "$DEV_SRC" "$@"
    ;;
  bootstrap)
    shift
    exec python3 "$PROJECT/scripts/bootstrap.py" --dest "$DEV_SRC" "$@"
    ;;
  bootstrap-existing)
    shift
    exec python3 "$PROJECT/scripts/bootstrap.py" --dest "$EXISTING_SRC" --skip-refactor "$@"
    ;;
  bootstrap-both)
    shift
    python3 "$PROJECT/scripts/bootstrap.py" --dest "$EXISTING_SRC" --skip-refactor "$@"
    exec python3 "$PROJECT/scripts/bootstrap.py" --dest "$DEV_SRC" "$@"
    ;;
  validate)
    exec python3 "$PROJECT/scripts/validate_project.py"
    ;;
  build)
    build_dev_apk
    ;;
  build-existing)
    build_existing_apk
    ;;
  test)
    exec bash "$PROJECT/scripts/run_unit_tests.sh"
    ;;
  preflight)
    exec python3 "$PROJECT/scripts/container_preflight.py"
    ;;
  connect|install|launch|stop|uninstall|device-info)
    cmd="$1"; shift
    exec python3 -m sagetv_dev_mcp.cli "$cmd" "$@"
    ;;
  logcat)
    shift
    exec python3 -m sagetv_dev_mcp.cli logcat "$@"
    ;;
  adb)
    shift
    exec adb "$@"
    ;;
  mcp)
    exec python3 -m sagetv_dev_mcp.server
    ;;
  mcp-test)
    exec python3 "$PROJECT/scripts/mcp_smoke_test.py"
    ;;
  shell|bash)
    exec bash
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    exec "$@"
    ;;
esac
