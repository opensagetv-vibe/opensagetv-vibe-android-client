#!/usr/bin/env python3
"""Bounded current-native-DVD physical arrow-key skip gate (no preference edits)."""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "playbackRate", "inputLastMappedCommand", "health_isPlaying",
          "health_audioRendered", "health_videoRendered", "health_playerError", "dvdScanTiming",
          "dvdVirtualSkipActive", "dvdVirtualSkipTargetMs", "dvdVirtualSkipResult")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--start-ms", type=int, default=540000)
    args = parser.parse_args()
    client = MCPProcess()
    rows: list[dict] = []
    result = {"passed": False, "rows": rows}
    try:
        initialize(client)
        def state() -> dict:
            return call_dict(client, "dev_player_state", timeout=30)
        def key(value: str) -> None:
            call_dict(client, "firetv_key", {"key": value}, timeout=30)
        current = state()
        if not current.get("dvdSessionPending") or not current.get("playerActive"):
            raise RuntimeError("Active native DVD required")
        control = discover_sage_control(str(current["serverAddress"]))
        context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
        key("PLAY")
        # Public Seek() can be accepted while the native DVD is still loading.
        # Core's short-lived time guess is not decoded landing evidence. Wait
        # for A/V first and verify the settled client clock, never an immediate
        # accepted=true UI snapshot, before measuring arrow skips.
        ready_deadline = time.monotonic() + 45
        while time.monotonic() < ready_deadline:
            current = state()
            if current.get("health_isPlaying") and int(current.get("health_videoRendered", 0)) > 0:
                break
            time.sleep(.5)
        else:
            raise RuntimeError("DVD did not reach advancing playback before positioning")
        control.seek(context, args.start_ms)
        time.sleep(8)
        positioned = state()
        result["positioning"] = {k: positioned.get(k) for k in FIELDS}
        if abs(int(positioned.get("mediaTimeMs", 0)) - args.start_ms) > 20_000:
            raise RuntimeError("Public DVD seek did not land; refusing to test a different scene")
        for direction, button in ((1, "RIGHT"), (-1, "LEFT")):
            before = state()
            started = time.monotonic()
            key(button)
            snapshots: list[dict] = []
            deadline = time.monotonic() + 18
            after = before
            while time.monotonic() < deadline:
                after = state()
                snapshots.append({k: after.get(k) for k in FIELDS})
                if (after.get("dvdVirtualSkipActive") is False
                        and after.get("dvdVirtualSkipResult") == "target_reached"
                        and float(after.get("playbackRate", 1)) == 1
                        and after.get("health_isPlaying")
                        and time.monotonic() - started > 2):
                    break
                time.sleep(.25)
            delta = int(after.get("mediaTimeMs", 0)) - int(before.get("mediaTimeMs", 0))
            row = {"button": button, "deltaMs": delta,
                   "elapsedSeconds": time.monotonic() - started,
                   "before": {k: before.get(k) for k in FIELDS},
                   "after": {k: after.get(k) for k in FIELDS}, "snapshots": snapshots,
                   "serverState": control.ui_state(context)}
            rows.append(row)
            print(json.dumps(row), flush=True)
            # Virtual scan stops at authored navigation blocks; it is not an
            # exact public Seek(long). Require a bounded jump in the right
            # direction and a return to advancing decoded A/V, not a rate label.
            if (not 4000 <= delta * direction <= 25000
                    or float(after.get("playbackRate", 1)) != 1
                    or not after.get("health_isPlaying") or after.get("health_playerError")
                    or after.get("dvdVirtualSkipResult") != "target_reached"):
                raise RuntimeError(f"{button}: virtual skip/normal-play landing failed: {delta}ms")
            time.sleep(3)
        result["passed"] = True
    except Exception as failure:
        result["error"] = str(failure)
    finally:
        try:
            call_dict(client, "firetv_key", {"key": "PLAY"}, timeout=30)
        except Exception:
            pass
        client.close()
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "rows"}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
