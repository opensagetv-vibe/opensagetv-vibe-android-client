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
  for marker in dev.sh update.sh scripts/run_unit_tests.sh mcp/pyproject.toml release.properties; do
    if [[ ! -f "$WORKSPACE/$marker" ]]; then
      echo "ERROR: workspace bind is missing $marker" >&2
      missing=1
    fi
  done
  if [[ "$missing" -ne 0 ]]; then
    cat >&2 <<EOF
ERROR: the unified Android workspace is not the OpenSageTV Vibe Android Client repository root.
The opensagetv-vibe-build-env mount is pointing at the wrong sibling directory.

Current container workspace: $WORKSPACE
Expected project markers: dev.sh, update.sh, scripts/, mcp/, release.properties

Place opensagetv-vibe-android-client beside opensagetv-vibe-build-env and rerun the root workflow.
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

All Android/ADB/Python/MCP/Gradle work runs in opensagetv-vibe-build-env.
Workspace bind: /workspace/android-client from the sibling repository layout

Commands:
  import-source ZIP [--replace]
                           Import exact repo ZIP into untouched baseline + Dev trees
  bootstrap [args...]     Clone pinned GitHub source into Dev tree and refactor
  bootstrap-existing     Clone pinned untouched GitHub baseline
  bootstrap-both         Clone pinned baseline into both trees; refactor Dev tree
  validate               Validate isolated app identity/Firebase removal
  build                  Build the Vibe client debug APK
  bundle                 Build/validate debug and release-candidate AABs plus debug APK set
  bundle-install         Install the package-verified debug AAB APK set on the configured device
  build-existing         Build untouched baseline APK + SHA-256 (never installs)
  test                   Run scaffold unit/static tests
  preflight              Verify toolchain/workspace/config/Fire TV connectivity
  connect                Connect configured Fire TV through container ADB
  install [apk]          Safely verify and install Dev APK only
  launch                 Launch the Vibe client APK
  stop|uninstall         Control isolated Dev app only
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
  [[ -f "$EXISTING_SRC/UPSTREAM_BASELINE.properties" ]] && cp -f "$EXISTING_SRC/UPSTREAM_BASELINE.properties" "$ARTIFACTS/existing/UPSTREAM_BASELINE.properties"
  [[ -f "$WORKSPACE/source/SOURCE_IMPORT.json" ]] && cp -f "$WORKSPACE/source/SOURCE_IMPORT.json" "$ARTIFACTS/existing/SOURCE_IMPORT.json"
  echo "Built UNMODIFIED baseline APK: $OUT"
  echo "This command DOES NOT install or replace the working MiniClient on the Fire TV."
}

build_dev_bundles() {
  require_dev_source
  "$PROJECT/scripts/ensure_debug_keystore.sh"
  python3 "$PROJECT/scripts/validate_project.py"
  : "${BUNDLETOOL_JAR:?The unified image does not provide BUNDLETOOL_JAR}"
  [[ -s "$BUNDLETOOL_JAR" ]] || { echo "ERROR: bundletool is missing: $BUNDLETOOL_JAR" >&2; exit 3; }
  mkdir -p "$ARTIFACTS/firetv" "$ARTIFACTS/reports"
  chmod +x "$DEV_SRC/gradlew"
  cd "$DEV_SRC"
  ./gradlew --no-daemon \
    :android-tv:bundleDebug \
    :android-tv:bundleRelease \
    -Pkeystore="$ANDROID_USER_HOME/debug.keystore" \
    -PstorePass=android \
    -Palias=client \
    -PkeyPass=android

  local debug_source release_source debug_out release_out apks_out
  debug_source="$(find android-tv/build/outputs/bundle/debug -type f -name '*.aab' | head -n 1)"
  release_source="$(find android-tv/build/outputs/bundle/release -type f -name '*.aab' | head -n 1)"
  [[ -n "$debug_source" && -n "$release_source" ]] || {
    echo "ERROR: Gradle completed but both Android App Bundles were not found" >&2
    exit 3
  }

  debug_out="$ARTIFACTS/firetv/OpenSageTV-Vibe-Android-Client-debug.aab"
  release_out="$ARTIFACTS/firetv/OpenSageTV-Vibe-Android-Client-release-candidate.aab"
  apks_out="$ARTIFACTS/firetv/OpenSageTV-Vibe-Android-Client-debug.apks"
  cp -f "$debug_source" "$debug_out"
  cp -f "$release_source" "$release_out"

  /opt/java/jdk17/bin/java -jar "$BUNDLETOOL_JAR" validate --bundle="$debug_out" \
    > "$ARTIFACTS/reports/bundletool-debug-validate.txt"
  /opt/java/jdk17/bin/java -jar "$BUNDLETOOL_JAR" validate --bundle="$release_out" \
    > "$ARTIFACTS/reports/bundletool-release-candidate-validate.txt"
  /opt/java/jdk17/bin/java -jar "$BUNDLETOOL_JAR" build-apks \
    --bundle="$debug_out" \
    --output="$apks_out" \
    --mode=universal \
    --ks="$ANDROID_USER_HOME/debug.keystore" \
    --ks-pass=pass:android \
    --ks-key-alias=client \
    --key-pass=pass:android \
    --overwrite

  sha256sum "$debug_out" | tee "$debug_out.sha256"
  sha256sum "$release_out" | tee "$release_out.sha256"
  sha256sum "$apks_out" | tee "$apks_out.sha256"
  echo "PASS: bundletool validated both AABs and generated the debug universal APK set"
  echo "NOTICE: $release_out uses the development identity/signer and is not publishable"
}

install_dev_bundle() {
  local debug_out="$ARTIFACTS/firetv/OpenSageTV-Vibe-Android-Client-debug.aab"
  local apks_out="$ARTIFACTS/firetv/OpenSageTV-Vibe-Android-Client-debug.apks"
  : "${BUNDLETOOL_JAR:?The unified image does not provide BUNDLETOOL_JAR}"
  [[ -s "$debug_out" && -s "$apks_out" ]] || {
    echo "ERROR: debug AAB/APK set is absent; run bundle first" >&2
    exit 3
  }
  local package_name
  package_name="$(/opt/java/jdk17/bin/java -jar "$BUNDLETOOL_JAR" dump manifest \
    --bundle="$debug_out" --xpath=/manifest/@package)"
  [[ "$package_name" = opensagetv.vibe.miniclient.debug ]] || {
    echo "ERROR: refusing bundle install for unexpected package: $package_name" >&2
    exit 4
  }
  python3 -m sagetv_dev_mcp.cli connect
  /opt/java/jdk17/bin/java -jar "$BUNDLETOOL_JAR" install-apks --apks="$apks_out"
  echo "PASS: installed package-verified debug AAB APK set: $package_name"
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
  bundle)
    build_dev_bundles
    ;;
  bundle-install)
    install_dev_bundle
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
