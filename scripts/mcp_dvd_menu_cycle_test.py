#!/usr/bin/env python3
"""Bounded authored-DVD menu/title gate; wait for real output before the next key."""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "dvdNewCellCount", "dvdHighlightVisible", "serverFlushSequence",
          "health_isPlaying", "health_videoRendered", "health_audioRendered",
          "health_videoDecoderInitCount", "health_videoDecoderReleaseCount",
          "health_videoQueuedInput", "health_playerError", "health_capturedMonotonicMs")


def phase_ready(state, menu, before_cell, require_new_cell):
    return (bool(state.get("dvdHighlightVisible")) == menu
            and bool(state.get("health_isPlaying"))
            and not state.get("health_playerError")
            and int(state.get("health_videoRendered", -1)) > 2
            and int(state.get("health_audioRendered", -1)) > 2
            and (not require_new_cell
                 or int(state.get("dvdNewCellCount", -1)) > before_cell))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--cycles", type=int, default=3)
    parser.add_argument("--timeout-s", type=float, default=30)
    parser.add_argument("--capture", action="store_true")
    parser.add_argument("--command-input", choices=("firetv", "server"), default="firetv",
                        help="Server isolates decoder transitions without delayed ADB keys; not a remote-input pass")
    args = parser.parse_args()
    if not 1 <= args.cycles <= 8 or not 5 <= args.timeout_s <= 40:
        parser.error("cycles must be 1..8; timeout must be 5..40 seconds")
    p = MCPProcess()
    report = {"passed": False, "cycles": [], "scope": "Current generated authored DVD; no settings changes"}
    try:
        initialize(p)
        initial = call_dict(p, "dev_player_state")
        if not initial.get("dvdSessionPending"):
            raise RuntimeError("An active generated authored DVD is required")
        control = discover_sage_control(str(initial["serverAddress"]))
        # Resolve the actual connected client through the installed public
        # control boundary, then verify its MediaFile before sending keys.
        # A diagnostic hint is not authoritative server UI-context discovery.
        context = control.resolve_context(str(initial["clientId"]))
        media = control.ui_state(context).get("media", {})
        paths = [str(path).replace("\\", "/").rstrip("/") for path in media.get("paths", [])]
        if not any(path.endswith("/OpenSageTV_Vibe_Test_DVD/VIDEO_TS") for path in paths):
            raise RuntimeError("Refusing authored-fixture keys on an unverified DVD volume")
        report["server"] = initial["serverAddress"]
        report["media"] = media
        report["commandInput"] = args.command_input

        def menu_command():
            if args.command_input == "server":
                return control.remote_command(context, "DVD Menu")
            return call_dict(p, "dev_sage_command", {"command": "dvd_menu"})

        def key(name):
            if args.command_input == "server":
                return control.remote_command(context, {"DOWN": "Down", "SELECT": "Select"}[name])
            return call_dict(p, "firetv_key", {"key": name})

        def phase(label, menu, command, require_new_cell=True):
            before = call_dict(p, "dev_player_state")
            started = time.monotonic()
            command()
            entry = {"phase": label, "before": {k: before.get(k) for k in FIELDS}, "samples": []}
            active_cycle["phases"].append(entry)
            deadline = started + args.timeout_s
            while time.monotonic() < deadline:
                state = call_dict(p, "dev_player_state")
                entry["samples"].append({k: state.get(k) for k in FIELDS})
                if phase_ready(state, menu, int(before.get("dvdNewCellCount", -1)), require_new_cell):
                    entry["startSeconds"] = time.monotonic() - started
                    # Distinguish a latched frame from a moving, audible epoch.
                    time.sleep(2)
                    after = call_dict(p, "dev_player_state")
                    entry["verified"] = {k: after.get(k) for k in FIELDS}
                    if (after.get("health_isPlaying") and not after.get("health_playerError")
                            and int(after.get("health_videoRendered", -1)) > int(state["health_videoRendered"])
                            and int(after.get("health_audioRendered", -1)) > int(state["health_audioRendered"])
                            and bool(after.get("dvdHighlightVisible")) == menu):
                        if args.capture:
                            entry["screenshot"] = call_dict(p, "take_screenshot", {
                                "label": "menu-cycle-{}-{}".format(active_cycle["cycle"], label)})
                        return
                time.sleep(.25)
            raise RuntimeError(label + ": no verified moving/audible replacement epoch")

        for cycle in range(1, args.cycles + 1):
            active_cycle = {"cycle": cycle, "phases": [], "passed": False}
            report["cycles"].append(active_cycle)
            phase("root", True, menu_command, False)
            def languages():
                key("DOWN")
                key("SELECT")
            phase("languages", True, languages)
            phase("return-root", True, menu_command)
            phase("main-title", False, lambda: key("SELECT"))
            active_cycle["passed"] = True
            print(json.dumps({"cycle": cycle, "passed": True,
                              "phaseStartSeconds": {r["phase"]: r["startSeconds"] for r in active_cycle["phases"]}}), flush=True)
        report["passed"] = True
    except Exception as error:
        report["error"] = str(error)
        try:
            checkpoint = call_dict(p, "dev_test_checkpoint", {
                "label": "authored-menu-cycle-failure", "log_lines": 650})
            report["failureArtifacts"] = checkpoint.get("artifacts", {})
        except Exception as capture_error:
            report["captureError"] = str(capture_error)
    finally:
        p.close()
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "cycles"}), flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
