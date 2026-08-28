#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_ENV_ROOT="${OPENSAGETV_VIBE_BUILD_ENV_ROOT:-$ROOT/../opensagetv-vibe-build-env}"
UNIFIED_CONTAINER="${OPENSAGETV_VIBE_DEV_CONTAINER:-opensagetv-vibe-dev}"
USE_UNIFIED=false
CONTAINER_WORKSPACE=/workspace

if [[ "${OPENSAGETV_VIBE_ANDROID_STANDALONE:-false}" != true && -x "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" ]]; then
  USE_UNIFIED=true
  CONTAINER_WORKSPACE=/workspace/android-client
fi

# Always give Docker Compose an absolute bind source. The legacy
# SAGETV_WINDOWS_ROOT variable remains a compatibility fallback only.
export OPENSAGETV_VIBE_ANDROID_ROOT="${OPENSAGETV_VIBE_ANDROID_ROOT:-${SAGETV_WINDOWS_ROOT:-$ROOT}}"
export SAGETV_WINDOWS_ROOT="${SAGETV_WINDOWS_ROOT:-$OPENSAGETV_VIBE_ANDROID_ROOT}"

COMPOSE=(docker compose -f "$ROOT/docker-compose.yml" --project-directory "$ROOT" --project-name opensagetv-vibe-android-client)

ensure_dev_container() {
  if [[ "$USE_UNIFIED" == true ]]; then
    "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" start >/dev/null
  else
    "${COMPOSE[@]}" up -d dev >/dev/null
  fi
}

dev_exec() {
  ensure_dev_container
  if [[ "$USE_UNIFIED" == true ]]; then
    docker exec -i "$UNIFIED_CONTAINER" env \
      JAVA_HOME=/opt/java/jdk17 \
      JDK_HOME=/opt/java/jdk17 \
      GRADLE_USER_HOME=/work/.gradle/android \
      ANDROID_USER_HOME="$CONTAINER_WORKSPACE/adb" \
      SAGETV_WORKSPACE="$CONTAINER_WORKSPACE" \
      SAGETV_DEV_SOURCE="$CONTAINER_WORKSPACE/source/dev" \
      SAGETV_EXISTING_SOURCE="$CONTAINER_WORKSPACE/source/existing" \
      SAGETV_MCP_CONFIG="$CONTAINER_WORKSPACE/config/firetv.toml" \
      SAGETV_ARTIFACT_DIR="$CONTAINER_WORKSPACE/artifacts/firetv" \
      PYTHONPATH="$CONTAINER_WORKSPACE/mcp/src" \
      PYTHONDONTWRITEBYTECODE=1 \
      PYTHONUNBUFFERED=1 \
      PATH="/opt/java/jdk17/bin:/opt/opensagetv-vibe/android-python/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/36.0.0:/opt/android-sdk/cmdline-tools/latest/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
      "$@"
  else
    "${COMPOSE[@]}" exec -T dev "$@"
  fi
}

dev_command() {
  if [[ "$USE_UNIFIED" == true ]]; then
    dev_exec bash "$CONTAINER_WORKSPACE/docker/entrypoint.sh" "$@"
  else
    dev_exec /usr/local/bin/opensagetv-vibe-android-dev "$@"
  fi
}

ensure_debug_keystore() {
  dev_exec "$CONTAINER_WORKSPACE/scripts/ensure_debug_keystore.sh"
}

repair_dev_gradle() {
  # Run from the bind-mounted workspace so this works with the existing Docker image.
  dev_exec python3 "$CONTAINER_WORKSPACE/scripts/repair_dev_gradle.py" --workspace "$CONTAINER_WORKSPACE"
}

DEFAULT_AUTOMATED_TEST_CLIENT_ID="44:45:56:30:30:31"

run_automated_mcp_test() {
  local script="$1"
  shift
  local client_id="$DEFAULT_AUTOMATED_TEST_CLIENT_ID"
  local id_source="default"
  local filtered=()

  while (($#)); do
    case "$1" in
      --client-id)
        if (($# < 2)); then
          echo "ERROR: --client-id requires a value" >&2
          exit 2
        fi
        client_id="$2"
        id_source="explicit"
        shift 2
        ;;
      --client-id=*)
        client_id="${1#--client-id=}"
        id_source="explicit"
        shift
        ;;
      *)
        filtered+=("$1")
        shift
        ;;
    esac
  done

  if [[ -z "$client_id" ]]; then
    echo "ERROR: --client-id must not be empty" >&2
    exit 2
  fi

  echo "TEST CLIENT ID ($id_source): $client_id"
  echo "NOTE: first-time MiniClient setup must already be complete before automated testing."
  dev_exec python3 "$CONTAINER_WORKSPACE/scripts/mcp_client_id.py" --ensure "$client_id" --quiet
  dev_exec python3 "$CONTAINER_WORKSPACE/scripts/$script" "${filtered[@]}"
}

case "${1:-help}" in
  image)
    if [[ "$USE_UNIFIED" == true ]]; then
      "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" image
      "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" start
    else
      "${COMPOSE[@]}" build dev
      "${COMPOSE[@]}" up -d --force-recreate dev
    fi
    ;;
  start)
    ensure_dev_container
    ;;
  stop-dev)
    if [[ "$USE_UNIFIED" == true ]]; then "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" stop; else "${COMPOSE[@]}" stop dev; fi
    ;;
  remove-dev)
    if [[ "$USE_UNIFIED" == true ]]; then "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" remove-dev; else "${COMPOSE[@]}" rm -sf dev; fi
    ;;
  mcp)
    shift
    # MCP stdio cannot use a pseudo-TTY and must keep stdout clean for protocol frames.
    dev_command mcp "$@"
    ;;
  mcp-test)
    shift
    # Do not rely on the image entrypoint knowing the newly added mcp-test verb.
    # The smoke-test script is bind-mounted from the workspace, so this works
    # with an existing image and requires no Docker rebuild.
    run_automated_mcp_test mcp_smoke_test.py "$@"
    ;;
  mcp-telemetry)
    shift
    # Read structured player telemetry through the real MCP stdio protocol.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/mcp_player_telemetry.py" "$@"
    ;;
  mcp-seek-test)
    shift
    # Run repeatable current-playback SageTV seek/pause checks through MCP.
    run_automated_mcp_test mcp_seek_suite.py "$@"
    ;;
  mcp-seek-time)
    shift
    # Debug-only direct player seek to a required caller-supplied absolute media time.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/mcp_seek_time.py" "$@"
    ;;
  mcp-search-test)
    shift
    # Initialize MCP, connect ADB, Search/type, dismiss the Android keyboard, then send navigation/Play-Pause Android remote keys.
    run_automated_mcp_test mcp_search_test.py "$@"
    ;;
  mcp-send-sequence)
    shift
    # Read an explicit multiline command/sendkey/sendtext/directtext/hideime/delay/wait script and execute it as one MCP tool call.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/mcp_send_sequence.py" "$@"
    ;;
  mcp-comskip-test)
    shift
    # Send direct SageTV right/left Comskip command and verify A/V recovery.
    run_automated_mcp_test mcp_comskip_test.py "$@"
    ;;
  mcp-playback-test)
    shift
    # Launch/configure/connect to a specific SageTV server, run the exact native test-video sequence, then verify playback.
    run_automated_mcp_test mcp_playback_test.py "$@"
    ;;
  mcp-media3-matrix)
    shift
    # Fresh-session Media3 Push/Dynamic vs Pull seek/resume comparison with real A/V output-health checks.
    run_automated_mcp_test mcp_media3_matrix.py "$@"
    ;;
  mcp-media3-comskip-matrix)
    shift
    # Fresh-session Media3 Push/Dynamic vs Pull direct SageTV right/left Comskip comparison.
    run_automated_mcp_test mcp_media3_comskip_matrix.py "$@"
    ;;
  mcp-player-matrix)
    shift
    # Complete player/backend matrix including all GSY engine selections; media watchdog outcomes are observational.
    run_automated_mcp_test mcp_player_matrix.py "$@"
    ;;
  mcp-player-tune)
    shift
    # Set/show Dev-only in-memory player tuning; requires v0.5.70 debugStatusVersion>=13.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/mcp_player_tune.py" "$@"
    ;;
  client-id)
    shift
    # Show/set the persisted SageTV MiniClient ID through the debug-only MCP control surface.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/mcp_client_id.py" "$@"
    ;;
  mcp-player-tuning-matrix)
    shift
    # Sweep runtime tuning combinations with fresh playback per combination.
    run_automated_mcp_test mcp_player_tuning_matrix.py "$@"
    ;;
  mcp-session-test)
    shift
    # End-to-end: launch, set player options, connect, play a named SageTV MediaFile on this client, verify playback, exit.
    run_automated_mcp_test mcp_session_test.py "$@"
    ;;
  player-diag)
    shift
    # External-only player diagnostics. Does not add hooks to the Android player.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/player_diagnostics.py" "$@"
    ;;
  test)
    shift
    repair_dev_gradle
    dev_command test "$@"
    ;;
  validate)
    shift
    repair_dev_gradle
    dev_command validate "$@"
    ;;
  build)
    shift
    repair_dev_gradle
    dev_command build "$@"
    ;;
  build-existing)
    shift
    dev_command build-existing "$@"
    ;;
  ensure-debug-keystore)
    shift
    dev_exec "$CONTAINER_WORKSPACE/scripts/ensure_debug_keystore.sh" "$@"
    ;;
  shell)
    shift
    ensure_dev_container
    if [[ "$USE_UNIFIED" == true ]]; then
      exec docker exec -it "$UNIFIED_CONTAINER" bash "$@"
    else
      exec "${COMPOSE[@]}" exec dev bash "$@"
    fi
    ;;
  help|-h|--help)
    cat <<'USAGE'
Usage: ./dev.sh COMMAND [args...]

Host requirement: Docker Desktop/Engine with Compose. Android/JDK/Python/ADB/MCP live in Docker.
Workspace bind: this repository by default (OPENSAGETV_VIBE_ANDROID_ROOT can override)

  start                         Create/start the one reusable development container
  stop-dev                      Stop the reusable development container
  remove-dev                    Deliberately remove only that development container

  image                         Build/update development image
  import-source ZIP [--replace] Import exact source ZIP into baseline + baseline + Dev trees
  bootstrap-both                Download pinned public baseline into both trees
  validate                      Validate isolated Dev source
  build                         Create/reuse debug keystore, then compile isolated Dev APK
  build-existing                Create/reuse debug keystore, then compile untouched baseline APK
  ensure-debug-keystore         Create/verify persistent Android debug signing key
  test                          Run scaffold tests
  preflight                     Check Docker toolchain + Fire TV connection
  connect                       Connect/report Fire TV through container ADB
  install [apk]                 Safe package-verified Dev APK install
  launch|stop|uninstall         Control isolated Dev app only
  device-info                   Print Fire TV model/API/fingerprint
  logcat [args...]              Read Fire TV logcat
  adb [args...]                 Raw containerized ADB diagnostic command
  mcp                           Start Dockerized MCP server (stdio; waits for a client)
  client-id [--show|--set ID|--generate]  Show/set/generate the persisted SageTV client ID
  mcp-test                      Run end-to-end MCP protocol smoke test (no Codex/Node)
  mcp-telemetry [max_events]    Read legacy telemetry status through MCP
  mcp-seek-test [options]       Automate seek/pause checks on the currently playing recording
  mcp-seek-time --target-ms N   Debug seek active playback to exact passed time (0 = beginning)
  mcp-search-test --text X      Search/type workflow; --text is required
  mcp-send-sequence [options]     Execute explicit multiline direct/legacy input and wait actions
  mcp-comskip-test [options]    Test Comskip using direct SageTV right/left commands
  mcp-playback-test --text X [options]  Launch/configure/connect/run native recording sequence/verify playback
  mcp-media3-matrix --text X [options]  Compare Media3 Push/Dynamic vs Pull seek and pause/resume recovery
  mcp-media3-comskip-matrix --text X [options]  Compare Media3 Push/Dynamic vs Pull direct-command Comskip recovery
  mcp-player-matrix --text X [options]  Full matrix or --issues-only focused regression rerun
  mcp-player-tune [options]            Set/show Dev runtime player tuning without rebuilding
  mcp-player-tuning-matrix --text X [options]  Sweep runtime tuning; --streaming accepts pull,push
  mcp-session-test [options]    Launch/configure/connect/play-by-name/verify/exit an end-to-end test session
  player-diag clear|LABEL       Clear logcat or collect external player/crash diagnostics
  shell                         Interactive development shell

Automated MCP/player test commands accept --client-id ID. If omitted, tests default to
44:45:56:30:30:31 (DEV001). Normal app use still uses the original generated/persisted ID.
First-time MiniClient setup must be completed manually before any automated test.
USAGE
    ;;
  *)
    cmd="$1"; shift
    dev_command "$cmd" "$@"
    ;;
esac
