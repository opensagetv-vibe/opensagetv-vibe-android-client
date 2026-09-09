#!/usr/bin/env python3
"""Capture the physical MiniClient connection/protocol/lifecycle ordering baseline."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_fixture, default_server_address, default_server_value

import argparse
from datetime import datetime
import json
from pathlib import Path
import sys
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_ui_roots import wait_automation_root


ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def event_names(state: dict) -> list[str]:
    names: list[str] = []
    for item in str(state.get("connectionRecent") or "").split("|"):
        parts = item.split("@", 3)
        if len(parts) == 4:
            names.append(parts[1])
    return names


def require_order(names: list[str], expected: list[str], label: str) -> None:
    offset = -1
    for marker in expected:
        try:
            offset = names.index(marker, offset + 1)
        except ValueError as exc:
            raise RuntimeError(
                f"{label}: missing/out-of-order {marker}; events={names}"
            ) from exc


def wait_ready(client: MCPProcess, timeout_s: float = 35.0) -> dict:
    return wait_automation_root(client, timeout_s=timeout_s, stable_ms=1500)


def wait_closed_trace(client: MCPProcess, timeout_s: float = 10.0) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        workers_stopped = not any(bool(last.get(key)) for key in (
            "connectionMediaWorkerRunning",
            "connectionGfxWorkerRunning",
            "connectionGfxReadWorkerRunning",
            "connectionEventRouterRunning",
        ))
        if last.get("connectionCloseComplete") and workers_stopped:
            return last
        time.sleep(0.2)
    raise RuntimeError(f"connection workers did not stop after close: {last}")


def validate_startup(state: dict) -> None:
    require(bool(state.get("connectionTraceAvailable")), f"trace unavailable: {state}")
    names = event_names(state)
    require_order(names, [
        "connection_created", "connect_started", "codec_discovery_complete",
        "socket_1_connect_started", "socket_1_accepted", "media_socket_ready",
        "socket_0_connect_started", "socket_0_accepted", "gfx_socket_ready",
        "connection_alive", "connection_published", "media_worker_launch",
        "media_worker_started", "media_before_gfx_ready",
        "gfx_worker_launch", "gfx_worker_started", "event_router_launch",
    ], "startup")
    require(any(name.startswith("gfx_read_first_") for name in names),
            f"startup lacked first GFX read: {names}")
    require(any(name.startswith("gfx_dispatch_first_") for name in names),
            f"startup lacked first GFX dispatch: {names}")
    require(int(state.get("connectionMediaCommandCount") or 0) >=
            int(state.get("connectionMediaReplyCount") or 0),
            f"media replies exceeded commands: {state}")
    require(int(state.get("connectionGfxReadCount") or 0) >=
            int(state.get("connectionGfxDispatchCount") or 0),
            f"GFX dispatch exceeded reads: {state}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run physical connection ordering gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument(
        "--server-path",
        default=default_fixture("seek_server_path", "/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts"),
        help="Exact SageTV path; defaults to the canonical captioned/comskip fixture",
    )
    parser.add_argument("--player", choices=("media3", "exoplayer"), default="media3")
    parser.add_argument("--renderer", choices=("opengl", "gdx"), default="opengl")
    parser.add_argument("--streaming", choices=("pull", "push"), default="pull")
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    args = parser.parse_args()

    client = MCPProcess()
    report: dict = {"passed": False, "server": args.server_address,
                    "player": args.player, "streaming": args.streaming,
                    "renderer": args.renderer}
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        call_dict(client, "dev_prepare_clean_start",
                  {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
        call_dict(client, "dev_set_player_config", {
            "player": args.player, "streaming": args.streaming,
            "decoding": "hardware",
            # This gate validates the historical HOME-close/reconnect ordering.
            # Do not let a persisted opt-in background-session preference turn
            # that deterministic teardown case into a retention test.
            "keep_session_in_background": False,
        })
        call_dict(client, "dev_connect_server", {
            "address": args.server_address, "port": args.server_port, "save": False,
            "renderer": args.renderer,
        })
        ready = wait_ready(client)
        startup = call_dict(client, "dev_player_state", timeout=30.0)
        validate_startup(startup)
        print("PASS: media-before-GFX startup and first command/reply ordering")

        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path,
            "timeout_s": args.playback_timeout_s,
            "verify_ms": 1500,
        }, timeout=args.playback_timeout_s + 35.0)
        require(bool(started.get("passed")), f"playback failed: {started}")

        call_dict(client, "dev_sage_command", {"command": "pause"}, timeout=30.0)
        time.sleep(0.5)
        call_dict(client, "dev_sage_command", {"command": "play"}, timeout=30.0)
        resumed = call_dict(client, "dev_wait_for_playback_started", {
            "timeout_s": 30.0, "verify_ms": 1500,
            "expect_video": True, "expect_audio": True,
        }, timeout=50.0)
        require(bool(resumed.get("passed")), f"pause/play did not recover: {resumed}")
        active = call_dict(client, "dev_player_state", timeout=30.0)
        active_names = event_names(active)
        require(any(name.startswith("media_command_first_") for name in active_names),
                f"playback lacked first media command: {active_names}")
        require(any(name.startswith("media_reply_first_") for name in active_names),
                f"playback lacked first media reply: {active_names}")
        require(int(active.get("connectionEventQueuedCount") or 0) >= 2,
                f"event queue did not observe pause/play: {active}")
        require(int(active.get("connectionEventDequeuedCount") or 0) >= 2,
                f"event router did not dequeue pause/play: {active}")
        require(int(active.get("connectionEventQueueDepth", -1)) == 0,
                f"event queue did not drain: {active}")
        print("PASS: ordered event queue drained after pause/play and A/V recovered")

        first_generation = int(active.get("connectionGeneration") or 0)
        first_created_ms = int(active.get("connectionCreatedMonotonicMs") or 0)
        before_home_status = call_dict(client, "dev_app_status", timeout=30.0)
        call_dict(client, "firetv_key", {"key": "HOME"}, timeout=30.0)
        background_closed = wait_closed_trace(client)
        require(not bool(background_closed.get("connectionAlive")),
                f"background connection remained alive: {background_closed}")
        print("PASS: HOME/pause closed connection and all four workers stopped")

        call_dict(client, "launch_dev_app", timeout=30.0)
        auto = call_dict(client, "dev_wait_for_ui", {
            "connected": True, "timeout_s": 10.0,
        }, timeout=20.0)
        if not auto.get("passed"):
            call_dict(client, "dev_connect_server", {
                "address": args.server_address, "port": args.server_port, "save": False,
                "renderer": args.renderer,
            })
        wait_ready(client)
        reconnected = call_dict(client, "dev_player_state", timeout=30.0)
        after_reconnect_status = call_dict(client, "dev_app_status", timeout=30.0)
        validate_startup(reconnected)
        first_pids = {str(pid) for pid in before_home_status.get("pids", [])}
        reconnected_pids = {str(pid) for pid in after_reconnect_status.get("pids", [])}
        same_process = bool(first_pids.intersection(reconnected_pids))
        reconnect_generation = int(reconnected.get("connectionGeneration") or 0)
        reconnect_created_ms = int(reconnected.get("connectionCreatedMonotonicMs") or 0)
        if same_process:
            require(reconnect_generation > first_generation,
                    f"same-process reconnect did not create a newer generation: {reconnected}")
            reconnect_mode = "same_process_new_generation"
        else:
            # Fire OS API 30 may reclaim the app process after HOME once the
            # intentionally non-retained session closes. Static generation
            # counters correctly restart at one in the new process, so prove a
            # fresh, later connection instead of requiring an impossible
            # cross-process counter increase.
            require(reconnect_generation > 0 and reconnect_created_ms > first_created_ms,
                    f"new-process reconnect did not create a fresh ordered connection: {reconnected}")
            reconnect_mode = "new_process_fresh_generation"
        print(f"PASS: foreground reconnect created an ordered connection ({reconnect_mode})")

        call_dict(client, "dev_exit_session", {"stop_app": False}, timeout=30.0)
        final_closed = wait_closed_trace(client)
        require(not bool(final_closed.get("connectionAlive")),
                f"final connection remained alive: {final_closed}")
        print("PASS: explicit teardown closed connection and stopped all workers")

        report.update({
            "passed": True, "ready": ready.get("state", ready), "startup": startup,
            "active": active, "backgroundClosed": background_closed,
            "beforeHomeStatus": before_home_status,
            "reconnected": reconnected, "afterReconnectStatus": after_reconnect_status,
            "reconnectMode": reconnect_mode, "finalClosed": final_closed,
        })
        output = ROOT / "artifacts" / "firetv" / (
            "connection-ordering-" + datetime.now().strftime("%Y%m%d-%H%M%S") + ".json"
        )
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"CONNECTION ORDERING: PASS ({output})")
        return 0
    except Exception as exc:
        report["error"] = str(exc)
        print(f"CONNECTION ORDERING: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
