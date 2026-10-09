#!/usr/bin/env python3
"""Commission completed-file playback across Android background/return/teardown.

This runs entirely through the debug MCP surface and deliberately targets only
the isolated Dev package. It proves real A/V output before and after HOME,
repeats exact-path playback, checks for crashes, and verifies final process
teardown.
"""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

import argparse
import json
from pathlib import Path
import sys
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize, tool_call
from mcp_playback_test import start_recording_via_search
from mcp_ui_roots import wait_automation_root


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def authored_dvd_root(path: str) -> str:
    """Constrain automatic menu activation to the generated, labelled fixture."""
    normalized = path.replace("\\", "/").rstrip("/")
    if normalized.endswith("/VIDEO_TS"):
        normalized = normalized[:-len("/VIDEO_TS")]
    return normalized if normalized.endswith("/OpenSageTV_Vibe_Test_DVD") else ""


def select_authored_dvd_title(client: MCPProcess, server: str, path: str) -> dict:
    """Verify the exact public MediaFile before activating its known Play item."""
    expected = authored_dvd_root(path)
    require(bool(expected), "--authored-dvd-title requires the generated authored DVD")
    initial = call_dict(client, "dev_player_state", timeout=30.0)
    require(bool(initial.get("dvdSessionPending")), "No active DVD for title selection")
    control = discover_sage_control(server)
    context = control.resolve_context(str(initial["clientId"]))
    media = control.ui_state(context).get("media", {})
    require(any(authored_dvd_root(str(p)) == expected for p in media.get("paths", [])),
            "Refusing lifecycle title keys on an unverified DVD volume")
    control.remote_command(context, "DVD Menu")
    root = wait_for_player_predicate(
        client, lambda s: bool(s.get("dvdHighlightVisible"))
        and bool(s.get("health_isPlaying")), "authored root menu", timeout_s=30)
    cell = int(root.get("dvdNewCellCount", -1))
    control.remote_command(context, "Select")
    title = wait_for_player_predicate(
        client, lambda s: not bool(s.get("dvdHighlightVisible"))
        and int(s.get("dvdNewCellCount", -1)) > cell
        and bool(s.get("health_isPlaying"))
        and int(s.get("health_videoRendered", -1)) > 2
        and int(s.get("health_audioRendered", -1)) > 2,
        "actual authored main title", timeout_s=45)
    return {"verifiedMediaFileId": media.get("mediaFileId"),
            "titleCell": title.get("dvdNewCellCount"), "menu": False}


def require_encoded_offset(state: dict, offset_ms: int, phase: str) -> None:
    """Assert the negotiated encoded clock policy survived a lifecycle phase."""
    observed = {key: state.get(key) for key in (
        "audioOffsetMs", "passthroughAudioOffsetEnabled", "audioOffsetPath",
        "health_errorState", "health_playerError")}
    require(int(state.get("audioOffsetMs", 999999)) == offset_ms,
            f"{phase} lost encoded offset {offset_ms} ms: {observed}")
    require(bool(state.get("passthroughAudioOffsetEnabled")),
            f"{phase} disabled encoded offset: {observed}")
    require("passthrough clock offset" in str(state.get("audioOffsetPath", "")),
            f"{phase} did not apply the encoded clock path: {observed}")


def wait_automation_ready(client: MCPProcess, timeout_s: float = 30.0) -> dict:
    """Compatibility name retained for all existing physical test runners."""
    return wait_automation_root(client, timeout_s=timeout_s, stable_ms=2000)


def clear_restored_playback(client: MCPProcess, settle_s: float = 12.0) -> None:
    """Stop a server-restored session before opening the lifecycle fixture.

    SageTV can restore DVD/live/file playback a few seconds after the UI first
    reports automation-ready.  Waiting for a stable idle interval prevents that
    asynchronous restore from racing the exact-path test request.
    """
    deadline = time.monotonic() + max(3.0, settle_s)
    idle_since: float | None = None
    stopped = False
    while time.monotonic() < deadline:
        state = call_dict(client, "dev_player_state", timeout=30.0)
        if bool(state.get("playerActive")):
            call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
            stopped = True
            idle_since = None
        elif idle_since is None:
            idle_since = time.monotonic()
        elif time.monotonic() - idle_since >= 2.0 and (stopped or time.monotonic() + 2.0 >= deadline):
            break
        time.sleep(0.25)
    final_state = call_dict(client, "dev_player_state", timeout=30.0)
    require(not bool(final_state.get("playerActive")),
            f"Server-restored playback did not become idle: {final_state}")
    if stopped:
        call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)
        wait_automation_ready(client, timeout_s=60.0)


def wait_for_player_predicate(client: MCPProcess, predicate, description: str,
                              timeout_s: float = 20.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        if predicate(last):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"Timed out waiting for {description}: {last}")


def wait_for_app_stopped(client: MCPProcess, timeout_s: float = 20.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_app_status", timeout=30.0)
        if (not bool(last.get("running"))
                or (bool(last.get("forceStopped")) and not bool(last.get("foreground")))):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"Timed out waiting for Fire OS process teardown: {last}")


def capture_phase_images(client: MCPProcess, label: str) -> dict:
    first=call_dict(client,"take_screenshot",{"label":label+"-first"},timeout=30.0)
    time.sleep(2.0)
    second=call_dict(client,"take_screenshot",{"label":label+"-settled"},timeout=30.0)
    return {"first":first,"second":second,"visualStatus":"PENDING_INDEPENDENT_REVIEW"}


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android completed-playback lifecycle gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--server-path", default="")
    parser.add_argument("--authored-dvd-title", action="store_true",
                        help="Verify generated authored DVD and activate its main title before lifecycle checks")
    parser.add_argument("--video-name", default="",
                        help="Start an exact indexed MediaFile through Sagex/WebRemote on stock SageTV")
    parser.add_argument("--search-text", default="",
                        help="Start an indexed recording through the stock SageTV Search UI")
    parser.add_argument("--search-media-type", choices=("tv", "videos"), default="videos")
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer", "gsyplayer"), default="media3")
    parser.add_argument("--gsy-engine", choices=("auto", "media3", "system", "legacy_exo"), default="auto")
    parser.add_argument("--streaming", choices=("dynamic", "pull", "fixed"), default="dynamic")
    parser.add_argument("--decoding", choices=("hardware", "software", "hardware_preferred"), default="hardware")
    parser.add_argument("--background-seconds", type=float, default=3.0)
    parser.add_argument("--session-timeout-seconds", type=int, default=300)
    parser.add_argument("--expect-session-timeout", action="store_true")
    resume_group = parser.add_mutually_exclusive_group()
    resume_group.add_argument("--resume-background-playback", dest="resume_background_playback",
                              action="store_true",
                              help="Resume playback that Home caused the app to pause (default)")
    resume_group.add_argument("--no-resume-background-playback", dest="resume_background_playback",
                              action="store_false",
                              help="Preserve the session but leave Home-interrupted playback paused")
    parser.set_defaults(resume_background_playback=True)
    parser.add_argument(
        "--repeat", type=int, default=3,
        help=("Number of post-lifecycle playback restarts; use 0 when gating "
              "HOME/pause persistence independently from server watch/restart behavior"),
    )
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    parser.add_argument("--verify-ms", type=int, default=1500)
    parser.add_argument("--encoded-offset-ms", type=int,
                        help="Apply encoded passthrough offset and assert retention across HOME/pause/resume")
    parser.add_argument("--report", default="",
                        help="Optional JSON evidence path")
    parser.add_argument("--capture-phase-images", action="store_true",
                        help="Bounded screenshots for independent visual review, not automatic picture PASS")
    parser.add_argument("--restart-from-beginning", action="store_true",
                        help="Start exact-path fixtures at origin; default keeps saved-position behavior")
    args = parser.parse_args()
    start_selectors = sum(bool(value.strip()) for value in
                          (args.server_path, args.video_name, args.search_text))
    require(start_selectors == 1,
            "exactly one of --server-path, --video-name, or --search-text is required")
    require(not args.authored_dvd_title or bool(authored_dvd_root(args.server_path)),
            "--authored-dvd-title requires an exact generated authored-DVD path")
    args.repeat = max(0, min(args.repeat, 10))
    args.background_seconds = max(1.0, min(args.background_seconds, 30.0))
    args.session_timeout_seconds = max(0, min(args.session_timeout_seconds, 86400))
    if args.expect_session_timeout:
        require(args.session_timeout_seconds > 0,
                "--expect-session-timeout requires a non-zero timeout")
        require(args.background_seconds > args.session_timeout_seconds,
                "--background-seconds must exceed --session-timeout-seconds")
    if args.encoded_offset_ms is not None:
        require(-4000 <= args.encoded_offset_ms <= 4000,
                "--encoded-offset-ms must be within -4000..4000")
        require(args.player != "ijkplayer", "IJK cannot apply encoded clock offsets")

    client = MCPProcess()
    evidence = {
        "status": "STARTING",
        "player": args.player,
        "gsyEngine": args.gsy_engine if args.player == "gsyplayer" else "",
        "streaming": args.streaming,
        "decoding": args.decoding,
        "videoName": args.video_name,
        "serverPath": args.server_path,
    }

    def write_evidence() -> None:
        if not args.report:
            return
        report = Path(args.report)
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")

    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        # Fire OS retains crash-buffer entries across process restarts and APK
        # upgrades. Scope this physical gate to failures produced by this run,
        # matching the session and player-matrix runners.
        tool_call(client, "clear_logcat", {}, timeout=30.0)
        print("PASS: stale device logcat cleared before lifecycle session")
        try:
            clean = call_dict(
                client,
                "dev_prepare_clean_start",
                {"wake": True, "graceful_timeout_s": 2.0},
                timeout=30.0,
            )
        except Exception as clean_error:
            # Fire OS occasionally tears down the debug broadcast receiver while
            # the graceful-exit request is in flight. A bounded Dev-only
            # force-stop is the documented deterministic fallback.
            print(f"WARN: graceful clean-start failed; using Dev-only fallback: {clean_error}")
            killed = call_dict(client, "kill_dev_app", timeout=30.0)
            require(bool(killed.get("stopped")), f"Dev-only fallback did not stop the app: {killed}")
            call_dict(client, "firetv_wake", timeout=30.0)
            clean = {"readyToLaunch": True, "fallback": "kill_dev_app+firetv_wake"}
        require(bool(clean.get("readyToLaunch")), f"Dev app clean-start preparation failed: {clean}")

        player_config = {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": args.decoding,
            "keep_session_in_background": True,
            "resume_background_playback": args.resume_background_playback,
            "background_session_timeout_seconds": args.session_timeout_seconds,
        }
        if args.player == "gsyplayer":
            player_config["gsy_engine"] = args.gsy_engine
        call_dict(client, "dev_set_player_config", player_config)
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        # The test server may need one reconnect interval after a prior disc
        # commissioning session before its GFX stream begins.
        wait_automation_ready(client, timeout_s=60.0)
        clear_restored_playback(client)

        print("STEP: establish real completed-file playback")
        if args.video_name:
            started = call_dict(client, "dev_play_video", {
                "video_name": args.video_name,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
            }, timeout=args.playback_timeout_s + 35.0)
        elif args.search_text:
            search_start = start_recording_via_search(
                client, args.search_text, search_timeout_s=15.0,
                media_type=args.search_media_type,
            )
            playback = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "expect_video": True,
                "expect_audio": True,
            }, timeout=args.playback_timeout_s + 20.0)
            started = {"passed": bool(playback.get("passed")),
                       "search": search_start, "playback": playback}
        else:
            started = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "restart_from_beginning": args.restart_from_beginning,
            }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"Initial playback failed: {started}")
        if args.authored_dvd_title:
            evidence["dvdTitleSelection"] = select_authored_dvd_title(
                client, args.server_address, args.server_path)
            title_output = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.playback_timeout_s, "verify_ms": args.verify_ms,
                "expect_video": True, "expect_audio": True,
            }, timeout=args.playback_timeout_s + 20)
            require(bool(title_output.get("passed")), "Authored title did not establish real A/V")
            print("PASS: verified authored main title before lifecycle, not a looping menu")
        if args.encoded_offset_ms is not None:
            adjusted = call_dict(client, "dev_set_active_audio", {
                "output": "passthrough",
                "offset_ms": args.encoded_offset_ms,
                "passthrough_offset_enabled": True,
            }, timeout=45.0)
            require(bool(adjusted.get("accepted")),
                    f"Encoded offset adjustment failed: {adjusted}")
            settled = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "expect_video": True,
                "expect_audio": True,
            }, timeout=args.playback_timeout_s + 20.0)
            require(bool(settled.get("passed")),
                    f"Encoded output did not settle after adjustment: {settled}")
        surface_before = call_dict(client, "dev_player_state", timeout=30.0)
        if args.encoded_offset_ms is not None:
            require_encoded_offset(surface_before, args.encoded_offset_ms, "initial playback")
        evidence["initialPlayback"] = surface_before
        if args.capture_phase_images:
            evidence["initialImage"] = capture_phase_images(client,"lifecycle-"+args.player+"-initial")
        connection_generation = int(surface_before.get("connectionGeneration", -1))
        require(connection_generation > 0, f"Missing connection generation: {surface_before}")
        require(
            bool(surface_before.get("health_surfaceValid"))
            and bool(surface_before.get("health_surfaceShown")),
            f"Initial video surface was not valid and visible: {surface_before}",
        )

        baseline_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        print("STEP: HOME/background then return to the existing Dev activity")
        call_dict(client, "firetv_key", {"key": "HOME"}, timeout=30.0)
        time.sleep(args.background_seconds)
        background = call_dict(client, "dev_app_status", timeout=30.0)
        require(bool(background.get("running")), f"Dev process died in background: {background}")
        require(not bool(background.get("foreground")), f"Dev app did not enter background: {background}")
        surface_background = call_dict(client, "dev_player_state", timeout=30.0)
        evidence["background"] = surface_background
        if args.expect_session_timeout:
            expired = wait_for_player_predicate(
                client,
                lambda state: not bool(state.get("connected"))
                and int(state.get("backgroundSessionTimeoutCount", 0)) >= 1,
                "the configured background session timeout",
                timeout_s=8.0,
            )
            require(expired.get("appVisibilityState") in
                    ("BACKGROUND_SESSION_LOST", "EXPLICIT_EXIT"),
                    f"Expired session did not enter a terminal state: {expired}")
            require(int(expired.get("backgroundLostCount", 0)) >= 1,
                    f"Expired session was not recorded as lost before teardown: {expired}")
            require(not bool(expired.get("playerActive")),
                    f"Expired session retained a player: {expired}")
            call_dict(client, "launch_dev_app", timeout=30.0)
            foreground = call_dict(client, "dev_app_status", timeout=30.0)
            require(bool(foreground.get("running")) and bool(foreground.get("foreground")),
                    f"Expired session did not return to the launcher: {foreground}")
            disconnected = call_dict(client, "dev_player_state", timeout=30.0)
            require(not bool(disconnected.get("connected")),
                    f"Expired session automatically reconnected: {disconnected}")
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
            wait_for_app_stopped(client, timeout_s=20.0)
            print("PASS: configured background timeout closed the connection and returned disconnected")
            print("ANDROID LIFECYCLE TIMEOUT: PASS")
            return 0
        require(
            int(surface_background.get("connectionGeneration", -2)) == connection_generation,
            f"HOME replaced the MiniClient connection: {surface_background}",
        )
        require(bool(surface_background.get("connectionAlive")),
                f"HOME closed the preserved connection: {surface_background}")
        require(
            surface_background.get("appVisibilityState") == "BACKGROUND_APP_PAUSED",
            f"Background policy did not own exactly one pause: {surface_background}",
        )
        require(int(surface_background.get("backgroundPauseRequestCount", 0)) >= 1,
                f"Background PAUSE was not recorded: {surface_background}")
        surface_released_or_hidden = (
            not bool(surface_background.get("playerActive"))
            or not bool(surface_background.get("health_surfaceKnown"))
            or not bool(surface_background.get("health_surfaceShown"))
            or not bool(surface_background.get("health_surfaceValid"))
        )
        require(
            surface_released_or_hidden,
            f"Background activity retained a visible valid playback surface: {surface_background}",
        )
        call_dict(client, "launch_dev_app", timeout=30.0)
        automatically_reconnected = call_dict(
            client,
            "dev_wait_for_ui",
            {"connected": True, "timeout_s": 12.0},
            timeout=22.0,
        )
        require(bool(automatically_reconnected.get("passed")),
                f"Preserved session did not return: {automatically_reconnected}")
        returned_state = automatically_reconnected.get("state", {})
        require(int(returned_state.get("connectionGeneration", -2)) == connection_generation,
                f"Foreground return created a second connection: {returned_state}")
        require(bool(returned_state.get("playerActive")),
                f"Foreground return lost the player: {returned_state}")
        if args.resume_background_playback:
            resumed = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "expect_video": True,
                "expect_audio": True,
            }, timeout=args.playback_timeout_s + 20.0)
            require(bool(resumed.get("passed")),
                    f"Playback did not recover after background return: {resumed}")
            resumed_state = resumed.get("state", resumed.get("after", {}))
            print("PASS: foreground return preserved the exact connection and resumed advancing A/V output")
        else:
            resume_count_before = int(surface_background.get("backgroundPlayRequestCount", 0))
            resumed_state = wait_for_player_predicate(
                client,
                lambda state: state.get("appVisibilityState") == "FOREGROUND"
                and bool(state.get("playerActive"))
                and not bool(state.get("health_isPlaying")),
                "foreground return with automatic playback resume disabled",
            )
            require(int(resumed_state.get("backgroundPlayRequestCount", -1))
                    == resume_count_before,
                    f"Disabled recovery emitted PLAY: {resumed_state}")
            print("PASS: foreground return preserved the exact connection without resuming playback")
            call_dict(client, "dev_sage_command", {"command": "play"}, timeout=30.0)
            resumed = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
                "expect_video": True,
                "expect_audio": True,
            }, timeout=args.playback_timeout_s + 20.0)
            require(bool(resumed.get("passed")),
                    f"Explicit PLAY did not resume after disabled recovery: {resumed}")
            resumed_state = resumed.get("state", resumed.get("after", resumed_state))
            print("PASS: explicit PLAY resumed A/V after disabled automatic recovery")
        if isinstance(resumed_state, dict) and resumed_state:
            require(int(resumed_state.get("connectionGeneration", connection_generation)) == connection_generation,
                    f"A/V recovery used a replacement connection: {resumed_state}")

        surface_after = call_dict(client, "dev_player_state", timeout=30.0)
        if args.capture_phase_images:
            evidence["homeReturnImage"] = capture_phase_images(client,"lifecycle-"+args.player+"-home-return")
        if args.encoded_offset_ms is not None:
            require_encoded_offset(surface_after, args.encoded_offset_ms, "HOME/return")
        evidence["foregroundReturn"] = surface_after
        require(
            bool(surface_after.get("health_surfaceValid"))
            and bool(surface_after.get("health_surfaceShown")),
            f"Foreground playback surface was not recreated: {surface_after}",
        )
        print("PASS: HOME hid/released the playback surface and foreground recreated it")

        print("STEP: preserve an explicit user pause across HOME without auto-resume")
        play_requests_before_user_pause = int(
            surface_after.get("backgroundPlayRequestCount", 0)
        )
        pause_requests_before_user_pause = int(
            surface_after.get("backgroundPauseRequestCount", 0)
        )
        call_dict(client, "dev_sage_command", {"command": "pause"}, timeout=30.0)
        user_paused = wait_for_player_predicate(
            client,
            lambda state: bool(state.get("playerActive"))
            and not bool(state.get("health_isPlaying")),
            "the server-authoritative user pause",
        )
        require(int(user_paused.get("connectionGeneration", -2)) == connection_generation,
                f"User pause replaced the MiniClient connection: {user_paused}")
        call_dict(client, "firetv_key", {"key": "HOME"}, timeout=30.0)
        time.sleep(args.background_seconds)
        user_pause_background = call_dict(client, "dev_player_state", timeout=30.0)
        require(user_pause_background.get("appVisibilityState") == "BACKGROUND_USER_PAUSED",
                f"Background policy did not preserve the user pause: {user_pause_background}")
        require(int(user_pause_background.get("backgroundPauseRequestCount", -1))
                == pause_requests_before_user_pause,
                f"HOME emitted a duplicate PAUSE for a user-paused session: {user_pause_background}")
        call_dict(client, "launch_dev_app", timeout=30.0)
        user_pause_return = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "player_active": True,
            "timeout_s": 20.0,
        }, timeout=30.0)
        require(bool(user_pause_return.get("passed")),
                f"User-paused session did not return: {user_pause_return}")
        preserved_user_pause = wait_for_player_predicate(
            client,
            lambda state: state.get("appVisibilityState") == "FOREGROUND"
            and bool(state.get("playerActive"))
            and not bool(state.get("health_isPlaying")),
            "foreground preservation of the user pause",
        )
        require(int(preserved_user_pause.get("connectionGeneration", -2)) == connection_generation,
                f"User-paused return replaced the connection: {preserved_user_pause}")
        require(int(preserved_user_pause.get("backgroundPlayRequestCount", -1))
                == play_requests_before_user_pause,
                f"User-paused return incorrectly auto-resumed playback: {preserved_user_pause}")
        if args.encoded_offset_ms is not None:
            require_encoded_offset(preserved_user_pause, args.encoded_offset_ms,
                                   "user-pause HOME/return")
        print("PASS: manual pause survived HOME/return without an automatic PLAY")
        evidence["userPauseReturn"] = preserved_user_pause
        call_dict(client, "dev_sage_command", {"command": "play"}, timeout=30.0)
        user_resume = call_dict(client, "dev_wait_for_playback_started", {
            "timeout_s": args.playback_timeout_s,
            "verify_ms": args.verify_ms,
            "expect_video": True,
            "expect_audio": True,
        }, timeout=args.playback_timeout_s + 20.0)
        require(bool(user_resume.get("passed")),
                f"Explicit PLAY did not resume the preserved user-paused session: {user_resume}")
        if args.capture_phase_images:
            evidence["userResumeImage"] = capture_phase_images(client,"lifecycle-"+args.player+"-user-resume")
        if args.encoded_offset_ms is not None:
            resumed_offset_state = call_dict(client, "dev_player_state", timeout=30.0)
            require_encoded_offset(resumed_offset_state, args.encoded_offset_ms,
                                   "explicit PLAY")
            evidence["encodedOffsetAfterResume"] = resumed_offset_state
            print(f"PASS: encoded {args.encoded_offset_ms} ms offset retained across HOME, pause, and resume")

        for iteration in range(1, args.repeat + 1):
            if args.video_name:
                replay = call_dict(client, "dev_play_video", {
                    "video_name": args.video_name,
                    "timeout_s": args.playback_timeout_s,
                    "verify_ms": args.verify_ms,
                }, timeout=args.playback_timeout_s + 35.0)
            elif args.search_text:
                replay_search = start_recording_via_search(
                    client, args.search_text, search_timeout_s=15.0,
                    media_type=args.search_media_type,
                )
                replay_health = call_dict(client, "dev_wait_for_playback_started", {
                    "timeout_s": args.playback_timeout_s,
                    "verify_ms": args.verify_ms,
                    "expect_video": True,
                    "expect_audio": True,
                }, timeout=args.playback_timeout_s + 20.0)
                replay = {"passed": bool(replay_health.get("passed")),
                          "search": replay_search, "playback": replay_health}
            else:
                replay = call_dict(client, "dev_play_server_path", {
                    "server_path": args.server_path,
                    "timeout_s": args.playback_timeout_s,
                    "verify_ms": args.verify_ms,
                }, timeout=args.playback_timeout_s + 35.0)
            require(bool(replay.get("passed")), f"Repeated playback {iteration} failed: {replay}")
            if args.authored_dvd_title:
                select_authored_dvd_title(client, args.server_address, args.server_path)
                replay_title = call_dict(client, "dev_wait_for_playback_started", {
                    "timeout_s": args.playback_timeout_s, "verify_ms": args.verify_ms,
                    "expect_video": True, "expect_audio": True,
                }, timeout=args.playback_timeout_s + 20)
                require(bool(replay_title.get("passed")),
                        f"Repeated authored title {iteration} did not establish real A/V")
            print(f"PASS: repeated exact-path playback {iteration}/{args.repeat}")

        final_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        evidence["finalCrashProbe"] = final_crash
        require(not bool(final_crash.get("signatureDetected")), f"Dev crash signature detected: {final_crash}")
        require(
            baseline_crash.get("signatureFingerprint", "") == final_crash.get("signatureFingerprint", ""),
            f"Crash signature changed during lifecycle test: {final_crash}",
        )

        exited = call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        status = wait_for_app_stopped(client, timeout_s=20.0)
        require(bool(exited.get("stopped")), f"MCP did not report a stopped session: {exited}")
        require(not bool(status.get("running"))
                or (bool(status.get("forceStopped")) and not bool(status.get("foreground"))),
                f"Dev package remained executable after teardown: {status}")
        print("PASS: disconnect/force-stop teardown committed; Fire OS may briefly retain a terminating PID")
        evidence["status"] = "PASS"
        evidence["teardown"] = {"exit": exited, "appStatus": status}
        write_evidence()
        print("ANDROID LIFECYCLE PLAYBACK: PASS")
        return 0
    except Exception as exc:
        evidence["status"] = "FAIL"
        evidence["reason"] = str(exc)
        write_evidence()
        print(f"ANDROID LIFECYCLE PLAYBACK: FAIL\n{exc}", file=sys.stderr)
        try:
            diagnostics = call_dict(client, "collect_playback_diagnostics", {"label": "lifecycle-failure"}, timeout=90.0)
            print(json.dumps(diagnostics, indent=2, sort_keys=True), file=sys.stderr)
        except Exception as diagnostic_exc:
            print(f"WARN: lifecycle diagnostics failed: {diagnostic_exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
