#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_ENV_ROOT="${OPENSAGETV_VIBE_BUILD_ENV_ROOT:-$ROOT/../opensagetv-vibe-build-env}"
UNIFIED_CONTAINER="${OPENSAGETV_VIBE_DEV_CONTAINER:-opensagetv-vibe-dev}"
CONTAINER_WORKSPACE=/workspace/android-client

if [[ ! -f "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" ]]; then
  echo "ERROR: unified build environment not found: $BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" >&2
  echo "Place opensagetv-vibe-build-env beside this repository or set OPENSAGETV_VIBE_BUILD_ENV_ROOT." >&2
  exit 2
fi

run_build_env() {
  # Tell the unified environment which checkout invoked it. This keeps every
  # command rooted in the current project directory, including independent
  # extracted-source and changed-files verification checkouts.
  OPENSAGETV_VIBE_ANDROID_PROJECT_ROOT="$ROOT" \
    bash "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" "$@"
}

ensure_dev_container() {
  run_build_env start >/dev/null
}

dev_exec() {
  ensure_dev_container
  docker exec -i -w "$CONTAINER_WORKSPACE" "$UNIFIED_CONTAINER" env \
      JAVA_HOME=/opt/java/jdk17 \
      JDK_HOME=/opt/java/jdk17 \
      GRADLE_USER_HOME=/work/.gradle/android \
      ANDROID_USER_HOME="$CONTAINER_WORKSPACE/adb" \
      ADB_VENDOR_KEYS="$CONTAINER_WORKSPACE/adb" \
      SAGETV_WORKSPACE="$CONTAINER_WORKSPACE" \
      SAGETV_DEV_SOURCE="$CONTAINER_WORKSPACE/source/dev" \
      SAGETV_EXISTING_SOURCE="$CONTAINER_WORKSPACE/source/existing" \
      SAGETV_MCP_CONFIG="$CONTAINER_WORKSPACE/config/firetv.toml" \
      SAGETV_ARTIFACT_DIR="$CONTAINER_WORKSPACE/artifacts/firetv" \
      OPENSAGETV_VIBE_BUILD_ENV_ROOT=/workspace/release-manifest \
      PYTHONPATH="$CONTAINER_WORKSPACE/mcp/src" \
      PYTHONDONTWRITEBYTECODE=1 \
      PYTHONUNBUFFERED=1 \
      PATH="/opt/java/jdk17/bin:/opt/opensagetv-vibe/android-python/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/build-tools/36.0.0:/opt/android-sdk/cmdline-tools/latest/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
      "$@"
}

dev_command() {
  dev_exec bash "$CONTAINER_WORKSPACE/docker/entrypoint.sh" "$@"
}

ensure_debug_keystore() {
  dev_exec "$CONTAINER_WORKSPACE/scripts/ensure_debug_keystore.sh"
}

repair_dev_gradle() {
  # Run from the bind-mounted workspace so this works with the existing Docker image.
  dev_exec python3 "$CONTAINER_WORKSPACE/scripts/repair_dev_gradle.py" --workspace "$CONTAINER_WORKSPACE"
}

DEFAULT_AUTOMATED_TEST_CLIENT_ID="44:45:56:30:30:31"

run_scripted_launch() {
  local client_id="$DEFAULT_AUTOMATED_TEST_CLIENT_ID"
  local id_source="default"

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
        echo "ERROR: unknown launch option: $1" >&2
        echo "Usage: ./dev.sh launch [--client-id ID]" >&2
        exit 2
        ;;
    esac
  done

  if [[ -z "$client_id" ]]; then
    echo "ERROR: --client-id must not be empty" >&2
    exit 2
  fi

  echo "SCRIPTED LAUNCH CLIENT ID ($id_source): $client_id"
  # mcp_client_id --ensure launches the Dev app if needed, persists the requested
  # ID, and restarts only when the active connection is using a different ID.
  # Launch once more afterward to guarantee the activity is foregrounded.
  dev_exec python3 "$CONTAINER_WORKSPACE/scripts/mcp_client_id.py" --ensure "$client_id" --quiet
  dev_command launch
}

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
    run_build_env image
    run_build_env start
    ;;
  start)
    ensure_dev_container
    ;;
  stop-dev)
    run_build_env stop
    ;;
  remove-dev)
    run_build_env remove-dev
    ;;
  launch)
    shift
    # Every host/scripted launch uses deterministic DEV001 by default.
    # Tapping the app normally on the device still uses its persisted ID.
    run_scripted_launch "$@"
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
  mcp-fixed-mim-test)
    shift
    # No-false-pass Fixed/MIM physical matrix. Continues all cases; GSY System is last.
    run_automated_mcp_test mcp_fixed_mim_matrix.py "$@"
    ;;
  mcp-smb-pull-ab-test)
    shift
    # Compare one generated recording over SageTV Pull and SMB Direct with exact seek timings and byte ownership.
    run_automated_mcp_test mcp_smb_pull_ab_test.py "$@"
    ;;
  mcp-lifecycle-test)
    shift
    # Completed-file physical gate: playback, HOME/background, return, replay, crash check, teardown.
    run_automated_mcp_test mcp_lifecycle_test.py "$@"
    ;;
  mcp-embedded-preview-test)
    shift
    # Fullscreen-to-Main-Menu preview regression: require embedded destination plus advancing hardware A/V.
    run_automated_mcp_test mcp_embedded_preview_test.py "$@"
    ;;
  mcp-connection-order-test)
    shift
    # Bounded physical baseline for startup, queues, reconnect generation, and teardown.
    run_automated_mcp_test mcp_connection_order_test.py "$@"
    ;;
  mcp-audio-focus-test)
    shift
    # Real playback gate for transient/duck/permanent focus and user-pause ownership.
    run_automated_mcp_test mcp_audio_focus_test.py "$@"
    ;;
  mcp-caption-test)
    shift
    # Require the SageTV STV to select captions and verify non-empty rendered cues.
    run_automated_mcp_test mcp_caption_test.py "$@"
    ;;
  mcp-codec-capability-test)
    shift
    # Inventory device codecs, then prove fallback-mode hardware playback and runtime observation.
    run_automated_mcp_test mcp_codec_capability_test.py "$@"
    ;;
  mcp-audio-capability-matrix)
    shift
    # Physically separate decode/sink evidence from passthrough claims for AC3/EAC3/DTS.
    run_automated_mcp_test mcp_audio_capability_matrix.py "$@"
    ;;
  mcp-kodi-codec-matrix)
    shift
    # Strict generated-fixture matrix on the commissioned non-Pro Fire TV.
    run_automated_mcp_test mcp_kodi_codec_matrix.py "$@"
    ;;
  mcp-push-telemetry-test)
    shift
    # Require server bandwidth/mux and client datasource metrics during real Push playback.
    run_automated_mcp_test mcp_push_telemetry_test.py "$@"
    ;;
  mcp-live-test)
    shift
    # Server-driven live-TV gate: tune, require real A/V, optionally change channels, and tear down.
    run_automated_mcp_test mcp_live_tv_test.py "$@"
    ;;
  mcp-eof-test)
    shift
    # Completed-file physical gate: seek to exact duration and require clean EOF without a crash.
    run_automated_mcp_test mcp_eof_test.py "$@"
    ;;
  mcp-disc-test)
    shift
    # Remote MiniDVDPlayer startup/STOP/crash gate; accepts repeated exact disc paths.
    run_automated_mcp_test mcp_disc_test.py "$@"
    ;;
  dvd-fixture)
    shift
    # Deterministic authored DVD: menus, chapters, two audio languages and
    # timestamped DVD SPU subtitles. Tooling is owned by the unified image.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/create_authored_dvd_fixture.py" "$@"
    ;;
  seek-fixture)
    shift
    # Canonical prerecorded/live transport fixture with timestamped A/53
    # captions, whole-second A/V pulses, dual AC-3, and deterministic EDL.
    dev_exec bash "$CONTAINER_WORKSPACE/scripts/generate_seek_fixture.sh" "$@"
    ;;
  codec-fixtures)
    shift
    # Deterministic Kodi-derived codec/profile/bitstream test matrix. Generated
    # media remains ignored test data and is never put in release archives.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/generate_kodi_codec_fixtures.py" "$@"
    ;;
  mcp-frame-step-test)
    shift
    # Command-28 gate: paused hardware frame advance plus explicit unsupported behavior.
    run_automated_mcp_test mcp_frame_step_test.py "$@"
    ;;
  mcp-fast-switch-test)
    shift
    run_automated_mcp_test mcp_media3_fast_switch_test.py "$@"
    ;;
  mcp-playback-rate-test)
    shift
    # Hardware-only completed-file gate for native rate and seek-based scan.
    run_automated_mcp_test mcp_playback_rate_test.py "$@"
    ;;
  player-diag)
    shift
    # External-only player diagnostics. Does not add hooks to the Android player.
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/player_diagnostics.py" "$@"
    ;;
  inspect-apk)
    shift
    # Enforce debug/release package, permission, exported-surface, secret, and signer boundaries.
    if [ "$#" -lt 1 ]; then
      echo "inspect-apk requires an APK path" >&2
      exit 2
    fi
    inspect_apk_path="$1"
    shift
    case "$inspect_apk_path" in
      /*) ;;
      *) inspect_apk_path="$CONTAINER_WORKSPACE/$inspect_apk_path" ;;
    esac
    dev_exec python3 "$CONTAINER_WORKSPACE/scripts/inspect_apk.py" "$inspect_apk_path" "$@"
    ;;
  install)
    shift
    if [ "$#" -eq 0 ]; then
      dev_command install
    else
      install_apk_path="$1"
      shift
      case "$install_apk_path" in
        /*) ;;
        *) install_apk_path="$CONTAINER_WORKSPACE/$install_apk_path" ;;
      esac
      dev_command install "$install_apk_path" "$@"
    fi
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
  bundle)
    shift
    repair_dev_gradle
    dev_command bundle "$@"
    ;;
  bundle-install)
    shift
    dev_command bundle-install "$@"
    ;;
  all)
    shift
    bash "$ROOT/dev.sh" test "$@"
    bash "$ROOT/dev.sh" validate "$@"
    bash "$ROOT/dev.sh" build "$@"
    bash "$ROOT/dev.sh" bundle "$@"
    bash "$ROOT/dev.sh" install "$@"
    exec bash "$ROOT/dev.sh" bundle-install "$@"
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
    exec docker exec -it "$UNIFIED_CONTAINER" bash "$@"
    ;;
  help|-h|--help)
    cat <<'USAGE'
Usage: ./dev.sh COMMAND [args...]

Host requirement: Docker Desktop/Engine plus the sibling opensagetv-vibe-build-env repository.
Android/JDK/Python/ADB/MCP live in the unified opensagetv-vibe-dev container.

  start                         Create/start the one reusable development container
  stop-dev                      Stop the reusable development container
  remove-dev                    Deliberately remove only that development container

  image                         Build/update development image
  import-source ZIP [--replace] Import exact source ZIP into baseline + Dev trees
  bootstrap-both                Download pinned public baseline into both trees
  validate                      Validate isolated Dev source
  build                         Compile the Dev client APK
  bundle                        Build/validate debug and release-candidate AABs plus debug APK set
  bundle-install                Safely install the package-verified debug AAB APK set
  build-existing                Create/reuse debug keystore, then compile untouched baseline APK
  ensure-debug-keystore         Create/verify persistent Android debug signing key
  test                          Run scaffold tests
  all                           Test, validate, build, then guarded install of the Dev APK
  preflight                     Check Docker toolchain + Fire TV connection
  connect                       Connect/report Fire TV through container ADB
  install [apk]                 Safe package-verified Dev client APK install
  launch [--client-id ID]       Launch the Vibe app; default identity is DEV001
  stop|uninstall                Control isolated Dev app only
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
  mcp-fixed-mim-test [options]  Run Fixed/MIM prerecorded+live backend/decoder commissioning; GSY System last
  mcp-smb-pull-ab-test [options] Compare Pull and SMB Direct seek recovery on the same generated fixture
  mcp-lifecycle-test [options]  Verify completed playback across HOME/return/replay/teardown
  mcp-embedded-preview-test [options] Verify healthy A/V after fullscreen becomes SageTV's embedded preview
  mcp-connection-order-test [options] Capture connection worker/command/queue ordering on a real device
  mcp-caption-test --server-path PATH [options]  Verify STV-driven caption discovery, selection, and rendered cues
  mcp-codec-capability-test [options] Verify MediaCodec inventory and fallback-mode playback evidence
  mcp-audio-capability-matrix [options] Characterize AC3/EAC3/DTS decode, sink, and fallback evidence
  mcp-kodi-codec-matrix [options] Run generated codecs with strict hardware evidence on non-Pro .25
  mcp-push-telemetry-test --server-path PATH      Verify detailed Push bandwidth/buffer/datasource telemetry
  mcp-live-test [options]       Verify server-driven live TV and optional channel changes
  mcp-eof-test [options]        Verify exact completed-file EOF without process death
  mcp-disc-test [options]       Verify remote DVD startup/STOP/crash; use --start-ms 480000 for Aladdin motion tests
  dvd-fixture [options]         Author the deterministic DVD test fixture in the unified container
  seek-fixture [OUTPUT] [SECONDS] Generate the captioned A/V-sync/Comskip MPEG-TS fixture
  codec-fixtures [options]       Generate and ffprobe the Kodi-derived hardware-codec matrix
  mcp-frame-step-test --server-path PATH [options]  Verify paused command 28 and unsupported behavior
  mcp-fast-switch-test --initial-path PATH --switch-path PATH [options]  Verify retained Media3 Pull/SMB replacement
  mcp-playback-rate-test --server-path PATH [options]  Verify hardware native-rate and seek-scan playback
  player-diag clear|LABEL       Clear logcat or collect external player/crash diagnostics
  inspect-apk APK --variant V   Inspect debug/release APK package, permissions, debug surface, secrets, and signer
  shell                         Interactive development shell

Scripted ./dev.sh launch and automated MCP/player test commands accept --client-id ID.
If omitted, they default to 44:45:56:30:30:31 (DEV001). Tapping/launching the app
directly on the device still uses the original generated/persisted ID.
First-time MiniClient setup must be completed manually before any automated test.
USAGE
    ;;
  *)
    cmd="$1"; shift
    dev_command "$cmd" "$@"
    ;;
esac
