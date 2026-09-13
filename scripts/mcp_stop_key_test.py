#!/usr/bin/env python3
"""Verify Android MEDIA_STOP mapping and SageTV playback teardown."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import os
from pathlib import Path
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the physical Android MEDIA_STOP gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int,
                        default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--video-name", default="ReconstructionAmericaAftertheCivilWar")
    parser.add_argument("--player", choices=("media3", "exoplayer", "gsyplayer"),
                        default="media3")
    parser.add_argument("--gsy-engine", choices=("media3", "legacy_exo"), default="media3")
    parser.add_argument("--timeout-s", type=float, default=60.0)
    parser.add_argument("--report", default="")
    args = parser.parse_args()
    alias = os.environ.get("SAGETV_TEST_DEVICE_ALIAS", "non_pro").strip() or "non_pro"
    report = Path(args.report) if args.report else Path(
        f"artifacts/firetv/media-stop-key-{alias.replace('_', '-')}.json"
    )
    evidence: dict = {"deviceAlias": alias, "status": "RUNNING"}
    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        evidence["mcpProtocol"] = negotiated
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True, "graceful_timeout_s": 1.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")
        config = {"player": args.player, "streaming": "pull", "decoding": "hardware"}
        if args.player == "gsyplayer":
            config["gsy_engine"] = args.gsy_engine
        call_dict(client, "dev_set_player_config", config, timeout=30.0)
        call_dict(client, "dev_connect_server", {
            "address": args.server_address, "port": args.server_port, "save": False,
        }, timeout=30.0)
        wait_automation_ready(client, timeout_s=60.0)
        resolved = call_dict(client, "dev_resolve_video_names", {
            "video_names": [args.video_name],
        }, timeout=90.0)
        details = (resolved.get("matches") or {}).get(args.video_name) or {}
        ids = sorted(int(match.get("mediaFileId") or 0)
                     for match in (details.get("matches") or [])
                     if int(match.get("mediaFileId") or 0) > 0)
        require(bool(ids), f"video not found: {args.video_name}")
        started = call_dict(client, "dev_play_media_file_id", {
            "media_file_id": ids[-1], "timeout_s": args.timeout_s, "verify_ms": 1500,
        }, timeout=args.timeout_s + 40.0)
        require(bool(started.get("passed")), f"playback failed: {started}")
        before = call_dict(client, "dev_player_state", timeout=30.0)
        require(bool(before.get("health_isPlaying")), f"video was not playing: {before}")
        before_sequence = int(before.get("inputEventSequence") or 0)
        injected = call_dict(client, "firetv_key_sequence", {
            "keys": ["STOP"], "delay_ms": 0,
        }, timeout=30.0)
        deadline = time.monotonic() + 15.0
        after = {}
        while time.monotonic() < deadline:
            after = call_dict(client, "dev_player_state", timeout=30.0)
            stopped = (not bool(after.get("playerActive")) or (
                after.get("health_isPlaying") is False
                and after.get("health_playWhenReady") is False
            ))
            mapped = (
                int(after.get("inputEventSequence") or 0) > before_sequence
                and int(after.get("inputLastKeyCode") or 0) == 86
                and str(after.get("inputLastMappedCommand") or "") == "STOP"
            )
            if stopped and mapped:
                break
            time.sleep(0.20)
        require(int(after.get("inputEventSequence") or 0) > before_sequence,
                f"MEDIA_STOP did not reach the Activity: {after}")
        require(int(after.get("inputLastKeyCode") or 0) == 86,
                f"last Android key was not KEYCODE_MEDIA_STOP: {after}")
        require(str(after.get("inputLastMappedCommand") or "") == "STOP",
                f"MEDIA_STOP did not map to SageTV STOP: {after}")
        require(not bool(after.get("playerActive")) or (
                    after.get("health_isPlaying") is False
                    and after.get("health_playWhenReady") is False),
                f"SageTV STOP did not quiesce playback: {after}")
        evidence.update({
            "status": "PASS", "mediaFileId": ids[-1], "injected": injected,
            "before": before, "after": after,
            "control4Boundary": (
                "Android KEYCODE_MEDIA_STOP is proven; a Control4 report must include "
                "inputLastKeyCode/inputLastScanCode to prove what its driver emits."
            ),
        })
        print("PASS: KEYCODE_MEDIA_STOP reached Vibe, mapped to SageTV STOP, and stopped playback")
        return 0
    except Exception as exc:
        evidence.update({"status": "FAIL", "reason": str(exc)})
        print(f"STOP KEY TEST: FAIL: {exc}")
        return 1
    finally:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(evidence, indent=2, sort_keys=True), encoding="utf-8")
        try:
            call_dict(client, "dev_sage_command", {"command": "home"}, timeout=15.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
