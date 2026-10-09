#!/usr/bin/env python3
"""Focused held Up/Down chapter gate; Left/Right TS uses mcp_dvd_cursor_test.py."""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control
from sagetv_dev_mcp.sagex_api import SagexApiClient

FIELDS = ("mediaTimeMs", "playbackRate", "serverFlushSequence", "dvdNewCellCount",
          "health_isPlaying", "health_videoRendered", "health_audioRendered",
          "health_playerError", "inputLastMappedCommand", "dvdScanTiming")


def chapter_repeat_delta(before: int, after: int, key: str) -> int:
    """Use authored chapter ordinals, not decoder NEWCELL coalescing."""
    if before < 1 or after < 1 or key not in ("UP", "DOWN"):
        raise ValueError("Valid DVD chapter ordinals and UP/DOWN are required")
    return (after - before) * (1 if key == "UP" else -1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--phase", choices=("chapters",), default="chapters")
    parser.add_argument("--chapter-index-witness", action="store_true",
                        help="Supplement Core MCP controls with explicit read-only Sagex chapter ordinals")
    args = parser.parse_args()
    client = MCPProcess()
    result: dict = {"passed": False, "rows": []}
    try:
        initialize(client)
        def state() -> dict:
            return call_dict(client, "dev_player_state", timeout=30)
        current = state()
        if not current.get("dvdSessionPending") or not current.get("health_isPlaying"):
            raise RuntimeError("Advancing native DVD title required")
        control = discover_sage_control(str(current["serverAddress"]))
        context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
        chapter_api = SagexApiClient.discover(str(current["serverAddress"])) if args.chapter_index_witness else None
        def chapter():
            value = chapter_api.call("GetDVDCurrentChapter", context=context)
            return int(value["Result"])  # Unsupported/unreadable API fails, never silently falls back.
        if chapter_api is not None:
            result["chapterWitnessTransport"] = "explicit_sagex_public_read_only"
        call_dict(client, "firetv_key", {"key": "PLAY"})
        gestures = (("UP", 200), ("DOWN", 200), ("UP", 3400), ("DOWN", 3400))
        for key, duration in gestures:
            before = state()
            chapter_before = chapter() if chapter_api is not None else None
            if float(before.get("playbackRate", 1)) != 1:
                raise RuntimeError("Unexpected scan before gesture; concurrent remote input invalidates this gate")
            started = time.monotonic()
            queued = call_dict(client, "dev_dvd_arrow_hold", {"key": key, "hold_ms": duration})
            samples = []
            peak = 1.0
            while time.monotonic() - started < duration / 1000 + 1:
                sample = state()
                peak = max(peak, abs(float(sample.get("playbackRate", 1))))
                samples.append({k: sample.get(k) for k in FIELDS})
                time.sleep(.1)
            deadline = time.monotonic() + 12
            while time.monotonic() < deadline:
                after = state()
                if (float(after.get("playbackRate", 1)) == 1 and after.get("health_isPlaying")
                        and not after.get("health_playerError")):
                    break
                time.sleep(.25)
            else:
                raise RuntimeError(f"{key}: release did not restore advancing 1x A/V")
            row = {"key": key, "holdMs": duration, "peakRate": peak, "queued": queued,
                   "before": {k: before.get(k) for k in FIELDS}, "samples": samples,
                   "after": {k: after.get(k) for k in FIELDS}, "serverState": control.ui_state(context)}
            if chapter_api is not None:
                row["chapterBefore"] = chapter_before
                row["chapterAfter"] = chapter()
            result["rows"].append(row)
            if duration == 200:
                if (after.get("serverFlushSequence") != before.get("serverFlushSequence")
                        or after.get("dvdNewCellCount") != before.get("dvdNewCellCount")):
                    raise RuntimeError(f"{key}: short tap changed a chapter")
            elif chapter_api is not None:
                if chapter_repeat_delta(chapter_before, row["chapterAfter"], key) < 2:
                    raise RuntimeError(f"{key}: authored chapter ordinals did not repeat directionally")
            elif int(after.get("dvdNewCellCount", 0)) - int(before.get("dvdNewCellCount", 0)) < 2:
                raise RuntimeError(f"{key}: held chapters did not repeat")
            print(json.dumps(row), flush=True)
            time.sleep(2)
        result["passed"] = True
    except Exception as failure:
        result["error"] = str(failure)
    finally:
        try: call_dict(client, "firetv_key", {"key": "PLAY"})
        except Exception: pass
        client.close()
        path = Path(args.output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "rows"}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
