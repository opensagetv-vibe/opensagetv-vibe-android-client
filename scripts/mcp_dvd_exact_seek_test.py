#!/usr/bin/env python3
"""Prove public stock Seek(long) DVD landings, not an accepted-time echo."""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "playbackRate", "serverFlushSequence", "health_isPlaying",
          "health_videoRendered", "health_audioRendered", "health_playerError",
          "health_playerPositionMs", "dvdLogicalClockBaseMs")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    client = MCPProcess()
    result: dict = {"passed": False, "rows": []}
    try:
        initialize(client)
        def state() -> dict:
            return call_dict(client, "dev_player_state", timeout=30)
        current = state()
        if (not current.get("dvdSessionPending") or not current.get("health_isPlaying")
                or float(current.get("playbackRate", 1)) != 1):
            raise RuntimeError("Advancing native DVD title at 1x required")
        control = discover_sage_control(str(current["serverAddress"]))
        context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
        for delta in (10_000, -10_000):
            before = state()
            target = max(0, int(before["mediaTimeMs"]) + delta)
            started = time.monotonic()
            accepted = control.seek(context, target)
            deadline = started + 15
            landed = None
            samples = []
            while time.monotonic() < deadline:
                now = state()
                elapsed = time.monotonic() - started
                # The stock VM may spend time draining old data before it
                # performs the seek. Wall time since submission is not time
                # spent playing the destination. A fresh DVD period starts at
                # zero; subtract its decoded presentation clock to compare
                # the real source anchor with the requested destination.
                error = (int(now.get("mediaTimeMs", 0)) - target
                         - int(now.get("health_playerPositionMs", 0)))
                samples.append({"elapsedSeconds": elapsed, "landingErrorMs": error,
                                **{k: now.get(k) for k in FIELDS}})
                # Require a real replacement epoch and decoded A/V. A stock
                # UI's immediate GetMediaTime() timeGuess is not this evidence.
                if (int(now.get("serverFlushSequence", 0)) > int(before.get("serverFlushSequence", 0))
                        and now.get("health_isPlaying") and not now.get("health_playerError")
                        and int(now.get("health_videoRendered", 0)) > 2
                        and int(now.get("health_audioRendered", 0)) > 2
                        and abs(error) < 3000):
                    landed = now
                    break
                time.sleep(.25)
            row = {"deltaMs": delta, "requestedTargetMs": target, "accepted": accepted,
                   "before": {k: before.get(k) for k in FIELDS}, "samples": samples}
            result["rows"].append(row)
            if landed is None:
                raise RuntimeError(f"Public DVD seek {delta:+d} did not reach decoded target")
            verification_start = time.monotonic()
            time.sleep(3)
            verified = state()
            wall_s = time.monotonic() - verification_start
            advance_ms = int(verified["mediaTimeMs"]) - int(landed["mediaTimeMs"])
            row["verified"] = {k: verified.get(k) for k in FIELDS}
            row["normalRate"] = advance_ms / (wall_s * 1000)
            row["serverState"] = control.ui_state(context)
            if (not verified.get("health_isPlaying") or verified.get("health_playerError")
                    or not .75 <= row["normalRate"] <= 1.25):
                raise RuntimeError("Seek landed but normal A/V clock did not continue")
            print(json.dumps(row), flush=True)
        result["passed"] = True
    except Exception as failure:
        result["error"] = str(failure)
    finally:
        client.close()
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "rows"}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
