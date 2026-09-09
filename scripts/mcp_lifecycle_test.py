#!/usr/bin/env python3
"""Commission completed-file playback across Android background/return/teardown.

This runs entirely through the debug MCP surface and deliberately targets only
the isolated Dev package. It proves real A/V output before and after HOME,
repeats exact-path playback, checks for crashes, and verifies final process
teardown.
"""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import sys
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize, tool_call
from mcp_playback_test import start_recording_via_search
from mcp_ui_roots import wait_automation_root


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


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


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android completed-playback lifecycle gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--server-path", default="")
    parser.add_argument("--search-text", default="",
                        help="Start an indexed recording through the stock SageTV Search UI")
    parser.add_argument("--search-media-type", choices=("tv", "videos"), default="videos")
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer", "gsyplayer"), default="media3")
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
    args = parser.parse_args()
    require(bool(args.server_path.strip()) != bool(args.search_text.strip()),
            "exactly one of --server-path or --search-text is required")
    args.repeat = max(0, min(args.repeat, 10))
    args.background_seconds = max(1.0, min(args.background_seconds, 30.0))
    args.session_timeout_seconds = max(0, min(args.session_timeout_seconds, 86400))
    if args.expect_session_timeout:
        require(args.session_timeout_seconds > 0,
                "--expect-session-timeout requires a non-zero timeout")
        require(args.background_seconds > args.session_timeout_seconds,
                "--background-seconds must exceed --session-timeout-seconds")

    client = MCPProcess()
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

        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": args.decoding,
            "keep_session_in_background": True,
            "resume_background_playback": args.resume_background_playback,
            "background_session_timeout_seconds": args.session_timeout_seconds,
        })
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
        if args.search_text:
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
            }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"Initial playback failed: {started}")
        surface_before = call_dict(client, "dev_player_state", timeout=30.0)
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
        print("PASS: manual pause survived HOME/return without an automatic PLAY")
        call_dict(client, "dev_sage_command", {"command": "play"}, timeout=30.0)
        user_resume = call_dict(client, "dev_wait_for_playback_started", {
            "timeout_s": args.playback_timeout_s,
            "verify_ms": args.verify_ms,
            "expect_video": True,
            "expect_audio": True,
        }, timeout=args.playback_timeout_s + 20.0)
        require(bool(user_resume.get("passed")),
                f"Explicit PLAY did not resume the preserved user-paused session: {user_resume}")

        for iteration in range(1, args.repeat + 1):
            if args.search_text:
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
            print(f"PASS: repeated exact-path playback {iteration}/{args.repeat}")

        final_crash = call_dict(client, "dev_crash_probe", timeout=30.0)
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
        print("ANDROID LIFECYCLE PLAYBACK: PASS")
        return 0
    except Exception as exc:
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
