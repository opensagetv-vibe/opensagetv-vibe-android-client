#!/usr/bin/env python3
"""End-to-end SageTV MiniClient Dev session automation through MCP.

Workflow:
  launch app -> apply player settings -> connect server -> play MediaFile by name on this client ->
  verify real playback -> optional health tests -> exit.

The caller supplies a video/recording name only. MCP resolves the connected SageTV server API,
this MiniClient's UI context, the unique MediaFile, and invokes Watch on that context.
"""
from __future__ import annotations

import argparse
import json
import sys

from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_config_values import (
    DECODING_SELECTIONS,
    STREAMING_SELECTIONS,
    add_fixed_encoding_args,
    fixed_config_from_args,
    validate_fixed_config,
    decoding_preference,
    normalize_decoding,
    normalize_streaming,
    streaming_preference,
)


def main() -> int:
    parser = argparse.ArgumentParser(description="Automate a complete SageTV MiniClient Dev test session")
    server = parser.add_mutually_exclusive_group()
    server.add_argument("--server-name", default="", help="Saved MiniClient server name")
    server.add_argument("--server-address", default="", help="Direct SageTV server address/IP")
    parser.add_argument("--server-port", type=int, default=31099)
    parser.add_argument("--no-save-server", action="store_true")
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer", "gsyplayer"), default="")
    parser.add_argument("--streaming", type=normalize_streaming, choices=STREAMING_SELECTIONS, default="", help="Streaming selection: push, pull, fixed (legacy dynamic accepted)")
    parser.add_argument("--decoding", type=normalize_decoding, choices=DECODING_SELECTIONS, default="", help="Decoding selection: hardware, software, fallback (legacy hardware_preferred accepted)")
    add_fixed_encoding_args(parser)
    parser.add_argument("--gsy-engine", choices=("auto", "media3", "system", "legacy_exo"), default="")
    parser.add_argument("--video-name", required=True, help="Exact or uniquely matching SageTV video/recording name")
    parser.add_argument("--connect-timeout-s", type=float, default=30.0)
    parser.add_argument(
        "--ui-stable-ms",
        type=int,
        default=2000,
        help="Require a non-empty SageTV menu hint to remain stable this long after connection (default: 2000 ms)",
    )
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    parser.add_argument("--verify-ms", type=int, default=1500)
    parser.add_argument("--run-seek-health", action="store_true", help="Run the basic single-FF/single-REW health checks after playback starts")
    parser.add_argument("--run-comskip", choices=("", "right", "left", "both"), default="")
    parser.add_argument("--exit", choices=("session", "stop", "none"), default="session")
    args = parser.parse_args()
    fixed_config = fixed_config_from_args(args)
    try:
        validate_fixed_config(fixed_config)
    except ValueError as exc:
        parser.error(str(exc))

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        print("PASS: adb_connect")
        call_dict(client, "adb_connect", timeout=30.0)

        print("STEP: prepare clean Dev app start")
        clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
        print(json.dumps(clean, indent=2, sort_keys=True))
        if not clean.get("readyToLaunch") or clean.get("stopped", {}).get("running"):
            raise RuntimeError(f"Dev app could not be stopped cleanly before launch: {clean}")

        print("STEP: apply playback settings before connection/video start")
        configured = call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": streaming_preference(args.streaming) if args.streaming else "",
            "decoding": decoding_preference(args.decoding) if args.decoding else "",
            "gsy_engine": args.gsy_engine,
            **fixed_config,
        })
        print(json.dumps(configured, indent=2, sort_keys=True))

        print("STEP: connect SageTV server")
        connected = call_dict(client, "dev_connect_server", {
            "server_name": args.server_name,
            "address": args.server_address,
            "port": args.server_port,
            "save": not args.no_save_server,
        })
        print(json.dumps(connected, indent=2, sort_keys=True))
        connected_ready = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "timeout_s": args.connect_timeout_s,
        }, timeout=args.connect_timeout_s + 10.0)
        if not connected_ready.get("passed"):
            raise RuntimeError(f"MiniClient did not connect within timeout: {connected_ready}")
        app_status = call_dict(client, "dev_app_status", {}, timeout=30.0)
        if not app_status.get("running"):
            raise RuntimeError(f"Dev app is not running after direct SageTV connect: {app_status}")
        print("PASS: Dev app running after direct connect: " + json.dumps(app_status, sort_keys=True))
        if not connected_ready.get("state", {}).get("automationReady", False):
            stale = connected_ready.get("state", {})
            print("INFO: normalizing stale SageTV UI with direct HOME command: "
                  f"uiState={stale.get('uiState')} menu={stale.get('menuName')} "
                  f"hasTextInput={stale.get('hasTextInput')}")
            call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)
        ready = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "automation_ready": True,
            "stable_ms": args.ui_stable_ms,
            "timeout_s": args.connect_timeout_s,
        }, timeout=args.connect_timeout_s + 10.0)
        if not ready.get("passed"):
            raise RuntimeError(f"Android debug app did not report automationReady within timeout: {ready}")
        print("PASS: SageTV connection ready; Android debug automationReady=true")
        print(json.dumps(ready.get("state", {}), indent=2, sort_keys=True))

        print(f"STEP: play video by name on this MiniClient: {args.video_name!r}")
        started = call_dict(client, "dev_play_video", {
            "video_name": args.video_name,
            "timeout_s": args.playback_timeout_s,
            "verify_ms": args.verify_ms,
        }, timeout=args.playback_timeout_s + 30.0)
        print(json.dumps(started, indent=2, sort_keys=True))
        if not started.get("passed"):
            raise RuntimeError(f"Video did not start with verified A/V playback: {started.get('reason')}")
        print("PASS: requested video started on this MiniClient and real playback is advancing")

        if args.run_seek_health:
            print("STEP: basic seek health")
            for label, command in (("FF", "ff"), ("REW", "rew")):
                result = call_dict(client, "dev_run_seek_check", {
                    "commands": [command],
                    "expected_net_ms": 0,
                }, timeout=90.0)
                print(f"{label}: " + json.dumps(result, indent=2, sort_keys=True))
                if not result.get("passed"):
                    raise RuntimeError(f"{label} playback health failed")

        if args.run_comskip:
            directions = [args.run_comskip] if args.run_comskip != "both" else ["right", "left"]
            print("STEP: Comskip health")
            for direction in directions:
                result = call_dict(client, "dev_run_comskip_check", {"direction": direction}, timeout=90.0)
                print(f"Comskip {direction}: " + json.dumps(result, indent=2, sort_keys=True))
                if not result.get("passed"):
                    raise RuntimeError(f"Comskip {direction} playback health failed")

        print("MCP SESSION AUTOMATION: PASS")
        return 0
    except Exception as exc:
        print(f"MCP SESSION AUTOMATION: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        if args.exit != "none":
            try:
                result = call_dict(client, "dev_exit_session", {"stop_app": args.exit == "stop"}, timeout=30.0)
                print(f"EXIT ({args.exit}): {json.dumps(result, sort_keys=True)}")
            except Exception as exc:
                print(f"WARN: exit cleanup failed: {exc}", file=sys.stderr)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
