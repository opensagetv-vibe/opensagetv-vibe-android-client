#!/usr/bin/env python3
"""Verify bounded original-PlaceShifter Push telemetry on a real client."""
from __future__ import annotations

import argparse
import json
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


def wait_snapshot(client: MCPProcess, timeout_s: float) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        if (
            int(last.get("detailedPushSampleSequence", 0)) > 0
            and int(last.get("serverChannelBandwidthKbps", -1)) >= 0
            and int(last.get("serverStreamBandwidthKbps", -1)) >= 0
            and int(last.get("serverTargetBandwidthKbps", -1)) >= 0
            and int(last.get("serverMuxTimeMs", -1)) >= 0
            and int(last.get("health_dataSourcePushCount", -1)) > 0
            and int(last.get("health_dataSourcePushedBytes", -1)) > 0
            and int(last.get("health_dataSourceReadCount", -1)) > 0
            and int(last.get("health_dataSourceReadBytes", -1)) > 0
            and int(last.get("health_dataSourceReadRateKbps", -1)) > 0
        ):
            return last
        time.sleep(0.25)
    raise RuntimeError(f"timed out waiting for complete Push telemetry: {last}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android detailed Push telemetry physical gate")
    parser.add_argument("--server-address", default="192.168.10.232")
    parser.add_argument("--server-port", type=int, default=31099)
    parser.add_argument("--server-path", required=True)
    parser.add_argument("--player", choices=("media3", "exoplayer"), default="media3")
    parser.add_argument("--decoding", choices=("hardware", "software", "hardware_preferred"), default="hardware")
    parser.add_argument("--timeout-s", type=float, default=60.0)
    args = parser.parse_args()

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"Dev app clean-start preparation failed: {clean}")
        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": "dynamic",
            "decoding": args.decoding,
        })
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        wait_automation_ready(client)
        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path,
            "timeout_s": args.timeout_s,
            "verify_ms": 2500,
        }, timeout=args.timeout_s + 35.0)
        require(bool(started.get("passed")), f"Push playback failed: {started}")
        state = wait_snapshot(client, args.timeout_s)
        evidence = {
            key: state.get(key)
            for key in (
                "serverChannelBandwidthKbps", "serverStreamBandwidthKbps",
                "serverTargetBandwidthKbps", "serverMuxTimeMs", "clientBufferTimeMs",
                "clientBufferAvailableBytes", "detailedPushSampleSequence",
                "health_dataSourcePushCount", "health_dataSourcePushedBytes",
                "health_dataSourceReadCount", "health_dataSourceReadBytes",
                "health_dataSourceReadWaitMs", "health_dataSourceReadRateKbps",
            )
        }
        print(json.dumps(evidence, indent=2, sort_keys=True))
        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        require(not bool(crash.get("signatureDetected")), f"Crash signature detected: {crash}")
        print(f"ANDROID PUSH TELEMETRY ({args.player}): PASS")
        return 0
    except Exception as exc:
        print(f"ANDROID PUSH TELEMETRY ({args.player}): FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
