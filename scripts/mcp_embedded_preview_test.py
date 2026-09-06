#!/usr/bin/env python3
"""Physically verify fullscreen-to-SageTV-preview playback through MCP.

The regression this gate covers presented the embedded video as a green square
after leaving fullscreen playback.  A screenshot alone cannot prove motion, so
the gate also requires the existing bounded player-health probe to observe
advancing hardware video/audio output after SageTV has changed its destination
rectangle to the Main Menu preview.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import sys
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_config_values import (
    DECODING_SELECTIONS,
    STREAMING_SELECTIONS,
    add_fixed_encoding_args,
    decoding_preference,
    fixed_config_from_args,
    normalize_decoding,
    normalize_streaming,
    streaming_preference,
    validate_fixed_config,
)


def is_embedded(state: dict) -> bool:
    try:
        width = int(state.get("videoDestWidth", 0) or 0)
        height = int(state.get("videoDestHeight", 0) or 0)
        ui_width = int(state.get("videoUiWidth", 0) or 0)
        ui_height = int(state.get("videoUiHeight", 0) or 0)
    except (TypeError, ValueError):
        return False
    if min(width, height, ui_width, ui_height) <= 0:
        return False
    return width * 100 < ui_width * 75 and height * 100 < ui_height * 75


def wait_for_embedded(client: MCPProcess, timeout_s: float) -> dict:
    deadline = time.monotonic() + timeout_s
    last: dict = {}
    stable_since: float | None = None
    while time.monotonic() < deadline:
        last = call_dict(client, "dev_player_state", timeout=30.0)
        if bool(last.get("playerActive")) and is_embedded(last):
            if stable_since is None:
                stable_since = time.monotonic()
            elif time.monotonic() - stable_since >= 0.75:
                return last
        else:
            stable_since = None
        time.sleep(0.10)
    raise RuntimeError(
        "SageTV did not retain an active embedded video destination: "
        f"menu={last.get('menuName')} dest="
        f"{last.get('videoDestWidth')}x{last.get('videoDestHeight')} ui="
        f"{last.get('videoUiWidth')}x{last.get('videoUiHeight')}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify real A/V while leaving fullscreen for SageTV's embedded preview"
    )
    parser.add_argument("--server-address", default="192.168.10.232")
    parser.add_argument("--server-port", type=int, default=31099)
    parser.add_argument("--server-path", required=True)
    parser.add_argument("--player", choices=("exoplayer", "media3"), default="exoplayer")
    parser.add_argument(
        "--streaming", type=normalize_streaming,
        choices=STREAMING_SELECTIONS, default="pull",
    )
    parser.add_argument(
        "--decoding", type=normalize_decoding,
        choices=DECODING_SELECTIONS, default="hardware",
    )
    parser.add_argument("--smb-mappings", default="")
    parser.add_argument(
        "--disc-policy",
        choices=("auto", "native", "hybrid", "mim"),
        default="auto",
    )
    parser.add_argument("--disc-skip-menus", action="store_true")
    parser.add_argument("--disc-skip-previews", action="store_true")
    parser.add_argument("--playback-timeout-s", type=float, default=75.0)
    parser.add_argument("--preview-timeout-s", type=float, default=20.0)
    parser.add_argument("--verify-ms", type=int, default=2500)
    parser.add_argument(
        "--screenshot-settle-s",
        type=float,
        default=2.0,
        help="Additional bounded playback time before the visual evidence capture",
    )
    parser.add_argument(
        "--start-ms",
        type=int,
        default=-1,
        help="Optional client-side seek target; disabled by default so server-owned Push playback is not reset",
    )
    parser.add_argument("--label", default="embedded-preview")
    parser.add_argument("--output", default="")
    add_fixed_encoding_args(parser)
    args = parser.parse_args()
    fixed_config = fixed_config_from_args(args)
    try:
        validate_fixed_config(fixed_config)
    except ValueError as exc:
        parser.error(str(exc))

    client = MCPProcess()
    evidence: dict = {
        "serverPath": args.server_path,
        "player": args.player,
        "streaming": args.streaming,
        "decoding": args.decoding,
    }
    try:
        negotiated, _ = initialize(client)
        evidence["mcpProtocol"] = negotiated
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(
            client, "dev_prepare_clean_start",
            {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0,
        )
        if not clean.get("readyToLaunch"):
            raise RuntimeError(f"Dev app clean start failed: {clean}")

        configured = call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": streaming_preference(args.streaming),
            "decoding": decoding_preference(args.decoding),
            "smb_mappings": args.smb_mappings,
            "disc_playback_policy": args.disc_policy,
            "disc_skip_menus": args.disc_skip_menus,
            "disc_skip_previews": args.disc_skip_previews,
            **fixed_config,
        })
        evidence["configured"] = configured
        ready: dict = {}
        for attempt in range(1, 4):
            evidence[f"connectAttempt{attempt}"] = call_dict(client, "dev_connect_server", {
                "address": args.server_address,
                "port": args.server_port,
                "save": True,
            }, timeout=30.0)
            ready = call_dict(client, "dev_wait_for_ui", {
                "connected": True,
                "timeout_s": 20.0,
            }, timeout=30.0)
            if ready.get("passed"):
                break
            time.sleep(1.0)
        if not ready.get("passed"):
            raise RuntimeError(f"SageTV connection failed after three attempts: {ready}")

        ready = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "automation_ready": True,
            "stable_ms": 1500,
            "timeout_s": 20.0,
        }, timeout=30.0)
        if not ready.get("passed") and bool(ready.get("state", {}).get("connected")):
            call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)
            ready = call_dict(client, "dev_wait_for_ui", {
                "connected": True,
                "automation_ready": True,
                "stable_ms": 1500,
                "timeout_s": 20.0,
            }, timeout=30.0)
        if not ready.get("passed"):
            raise RuntimeError(f"SageTV UI did not become automation-ready: {ready}")

        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path,
            "timeout_s": args.playback_timeout_s,
            "verify_ms": args.verify_ms,
            "restart_from_beginning": False,
        }, timeout=args.playback_timeout_s + 35.0)
        evidence["fullscreenStart"] = started
        if not started.get("passed"):
            raise RuntimeError(f"Fullscreen playback did not start: {started.get('reason')}")

        if args.start_ms >= 0:
            seek = call_dict(client, "dev_seek_time", {
                "target_ms": args.start_ms,
                "tolerance_ms": 6000,
                "timeout_s": 30.0,
                "stable_ms": 1000,
            }, timeout=60.0)
            evidence["normalizedStart"] = seek
            if not seek.get("passed"):
                raise RuntimeError(f"Could not normalize playback position: {seek}")

        evidence["beforeHome"] = call_dict(client, "dev_player_state", timeout=30.0)
        evidence["homeCommand"] = call_dict(
            client, "dev_sage_command", {"command": "home"}, timeout=30.0
        )
        embedded = wait_for_embedded(client, args.preview_timeout_s)
        evidence["embeddedState"] = embedded

        health = call_dict(client, "dev_wait_for_playback_started", {
            "timeout_s": args.preview_timeout_s,
            "verify_ms": args.verify_ms,
        }, timeout=args.preview_timeout_s + 35.0)
        evidence["embeddedHealth"] = health
        if not health.get("passed"):
            raise RuntimeError(f"Embedded preview A/V did not remain healthy: {health}")
        final_state = call_dict(client, "dev_player_state", timeout=30.0)
        evidence["finalState"] = final_state
        if not is_embedded(final_state):
            raise RuntimeError("Playback left the embedded destination during health verification")
        if not bool(final_state.get("health_surfaceValid")):
            raise RuntimeError("Embedded preview Surface is invalid")
        if bool(final_state.get("health_errorState")) or final_state.get("health_playerError"):
            raise RuntimeError(f"Embedded preview player error: {final_state.get('health_playerError')}")

        if args.screenshot_settle_s > 0:
            time.sleep(args.screenshot_settle_s)
            settled_health = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.preview_timeout_s,
                "verify_ms": min(args.verify_ms, 1500),
            }, timeout=args.preview_timeout_s + 35.0)
            evidence["settledHealth"] = settled_health
            if not settled_health.get("passed"):
                raise RuntimeError(
                    f"Embedded preview stopped before visual capture: {settled_health}"
                )

        screenshot = call_dict(client, "take_screenshot", {"label": args.label}, timeout=30.0)
        evidence["screenshot"] = screenshot
        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        evidence["crash"] = crash
        if crash.get("signatureDetected"):
            raise RuntimeError(f"Crash signature detected: {crash}")
        evidence["passed"] = True
        print("EMBEDDED PREVIEW: PASS")
        return 0
    except Exception as exc:
        evidence["passed"] = False
        evidence["error"] = str(exc)
        try:
            evidence["diagnostics"] = call_dict(
                client, "collect_playback_diagnostics",
                {"label": args.label + "-failure"}, timeout=90.0,
            )
        except Exception as diag_exc:
            evidence["diagnosticsError"] = str(diag_exc)
        print(f"EMBEDDED PREVIEW: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        output = Path(args.output or f"artifacts/firetv/{args.label}.json")
        if not output.is_absolute():
            output = Path(os.environ.get("SAGETV_WORKSPACE", Path.cwd())) / output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        print(f"Evidence: {output}")
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
        except Exception as cleanup_exc:
            print(f"WARN: cleanup failed: {cleanup_exc}", file=sys.stderr)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
