#!/usr/bin/env python3
"""Bounded tablet posture/touch-layout gate; no Core changes or persistent settings."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import struct
import time
import uuid
import xml.etree.ElementTree as ET

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready
from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import load_config, default_server_address


def icon_bounds(xml: str) -> dict[str, tuple[int, int, int, int]]:
    start = xml.find("<?xml")
    end = xml.rfind("</hierarchy>")
    require(start >= 0 and end >= start, "Accessibility hierarchy is unavailable")
    root = ET.fromstring(xml[start:end + len("</hierarchy>")])
    result = {}
    for node in root.iter("node"):
        name = node.get("resource-id", "").rsplit("/", 1)[-1]
        if name.startswith("nav_"):
            values = tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            if len(values) == 4:
                result[name] = values
    return result


def assert_aligned_icons(bounds: dict) -> None:
    pairs = (("nav_video_info", "nav_toggle_ar"),
             ("nav_export_diagnostics", "nav_active_player_adjustments"),
             ("nav_test_current_video", "nav_audio_output"),
             ("nav_playback_stats", "nav_closed_captions"))
    for upper, lower in pairs:
        require(upper in bounds and lower in bounds, f"Missing icon pair {upper}/{lower}")
        a, b = bounds[upper], bounds[lower]
        require(abs((a[0] + a[2]) - (b[0] + b[2])) <= 2,
                f"Misaligned icon column {upper}/{lower}: {a}/{b}")
        require(a[3] <= b[1], f"Overlapping icon rows: {a}/{b}")


def read_icon_bounds(adb: AdbClient) -> dict:
    # Android's uiautomator does not reliably return XML when /dev/tty is
    # supplied. Own one unique device-temporary file and remove exactly that
    # file even on parse/dump failure; never accumulate device captures.
    temporary = "/data/local/tmp/vibe-tablet-layout-" + uuid.uuid4().hex + ".xml"
    try:
        adb.shell(f"uiautomator dump --compressed {temporary}", timeout=25)
        return icon_bounds(adb.shell(f"cat {temporary}", timeout=10))
    finally:
        adb.shell(f"rm -f {temporary}", timeout=10)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-path", required=True)
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    config = load_config()
    adb = AdbClient(config.device, config.dev_package,
                    adb="/opt/android-sdk/platform-tools/adb")
    client = MCPProcess()
    report = {"passed": False, "rows": [], "layout": "declared landscape activity"}
    rotation_settings = {}
    began = time.monotonic()
    try:
        initialize(client)
        call_dict(client, "adb_connect", timeout=45)
        call_dict(client, "dev_prepare_clean_start", {"wake": True}, timeout=45)
        call_dict(client, "dev_set_player_config", {
            "player": "media3", "streaming": "pull", "decoding": "Hardware",
        }, timeout=30)
        call_dict(client, "dev_connect_server", {
            "address": args.server_address, "save": False}, timeout=30)
        wait_automation_ready(client, timeout_s=60)
        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path, "timeout_s": 40,
            "verify_ms": 1500, "restart_from_beginning": True}, timeout=360)
        require(bool(started.get("passed")), "Layout fixture did not start actual A/V")
        for name in ("accelerometer_rotation", "user_rotation"):
            rotation_settings[name] = adb.shell(f"settings get system {name}").strip()
        adb.shell("settings put system accelerometer_rotation 0")
        for rotation in (0, 1, 2, 3):
            adb.shell(f"settings put system user_rotation {rotation}")
            healthy = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": 15, "verify_ms": 1500,
                "expect_video": True, "expect_audio": True}, timeout=40)
            require(bool(healthy.get("passed")), f"Output lost after rotation request {rotation}")
            image = args.report.parent / f"tablet-rotation-{rotation}.png"
            adb.screenshot(image)
            width, height = struct.unpack(">II", image.read_bytes()[16:24])
            require(width > height, "Declared-landscape activity escaped to portrait")
            adb.shell(f"input touchscreen swipe {width // 2} {height // 2} "
                      f"{width // 2} {height // 2} 900")
            bounds = read_icon_bounds(adb)
            assert_aligned_icons(bounds)
            menu_image = args.report.parent / f"tablet-rotation-{rotation}-navigation.png"
            adb.screenshot(menu_image)
            adb.key("back")
            recovered = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": 15, "verify_ms": 1500,
                "expect_video": True, "expect_audio": True}, timeout=40)
            require(bool(recovered.get("passed")), "Back from navigation lost actual A/V")
            report["rows"].append({"requestedRotation": rotation, "width": width,
                                   "height": height, "iconBounds": bounds,
                                   "video": str(image), "menu": str(menu_image),
                                   "actualVideoDecoder": adb.player_state_snapshot().get(
                                       "health_videoDecoder"), "passed": True})
        report["passed"] = True
    except Exception as failure:
        report["error"] = str(failure)
    finally:
        restored = {}
        for name, value in rotation_settings.items():
            try:
                if value == "null":
                    adb.shell(f"settings delete system {name}")
                else:
                    require(value.isdigit(), "Unexpected rotation setting value")
                    adb.shell(f"settings put system {name} {value}")
                restored[name] = adb.shell(f"settings get system {name}").strip() == value
            except Exception as failure:
                report["passed"] = False
                restored[name] = str(failure)
        report["rotationSettingsRestored"] = restored
        if any(value is not True for value in restored.values()):
            report["passed"] = False
        try:
            call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=35)
        finally:
            client.close()
            report["elapsedSeconds"] = round(time.monotonic() - began, 3)
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
            print(json.dumps(report, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
