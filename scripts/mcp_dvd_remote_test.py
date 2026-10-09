#!/usr/bin/env python3
"""Focused physical-key DVD scan gate on the currently loaded stock-server DVD."""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "sageTimelineMs", "playbackRate", "state",
          "inputLastMappedCommand", "dvdPushedBytes", "dvdLatestVideoSampleUs",
          "health_playerPositionMs", "health_videoRendered", "health_isPlaying",
          "health_playerError", "health_audioRendered", "dvdScanTiming")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--observe-s", type=float, default=5.0)
    parser.add_argument("--settle-s", type=float, default=4.0)
    parser.add_argument("--phase", choices=("full", "forward", "reverse", "coarse"), default="full")
    parser.add_argument("--start-ms", type=int)
    args = parser.parse_args()
    client = MCPProcess()
    rows: list[dict] = []
    failures: list[str] = []
    result: dict = {"passed": False, "rows": rows, "failures": failures}
    try:
        initialize(client)
        def state() -> dict:
            return call_dict(client, "dev_player_state", timeout=30.0)
        def key(value: str) -> None:
            call_dict(client, "firetv_key", {"key": value}, timeout=30.0)
        def expect_rate(rate: float, label: str) -> None:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                current = state()
                if float(current.get("playbackRate", 1)) == rate:
                    row = {"label": label, **{k: current.get(k) for k in FIELDS}}
                    rows.append(row)
                    print(json.dumps(row), flush=True)
                    return
                time.sleep(.25)
            raise RuntimeError(f"{label}: rate {rate} not observed")
        current = state()
        if not current.get("dvdSessionPending") or not current.get("playerActive"):
            raise RuntimeError("An active native DVD title is required")
        key("PLAY")
        expect_rate(1, "normal")
        if args.start_ms is not None:
            control = discover_sage_control(str(current["serverAddress"]))
            context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
            control.seek(context, args.start_ms)
            time.sleep(8)
            positioned = state()
            if (not positioned.get("health_isPlaying")
                    or abs(int(positioned.get("mediaTimeMs", 0)) - args.start_ms) > 20_000):
                raise RuntimeError("Public DVD seek did not reach requested scene")
            result["positioning"] = {k: positioned.get(k) for k in FIELDS}
        def measure(rate: float, label: str) -> None:
            time.sleep(args.settle_s)
            # The flags acknowledge server ownership before the queued old
            # epoch has reached the renderer. Do not call that old 1x/2x
            # reserve a measurement of the newly requested rate.
            epoch_deadline = time.monotonic() + 20
            while time.monotonic() < epoch_deadline:
                ready = state()
                timing = str(ready.get("dvdScanTiming", ""))
                if (timing.startswith(f"rate={float(rate)},")
                        and ",count=0," not in timing
                        and ready.get("health_isPlaying")):
                    break
                time.sleep(.25)
            else:
                failures.append(f"{label}: requested scan epoch did not reach output")
            before_start = time.monotonic()
            before = state()
            before_time = (before_start + time.monotonic()) / 2
            # Decoder counters restart at authored cell transitions. Sum
            # bounded samples so a legitimate replacement is not reported as
            # negative rendering progress across the observation window.
            previous = int(before.get("health_videoRendered", 0))
            frames = 0
            deadline = time.monotonic() + args.observe_s
            while time.monotonic() < deadline:
                time.sleep(min(1.0, max(0.0, deadline - time.monotonic())))
                sample = state()
                rendered = int(sample.get("health_videoRendered", 0))
                frames += max(0, rendered - previous) if rendered >= previous else max(0, rendered)
                previous = rendered
            after_start = time.monotonic()
            after = state()
            after_time = (after_start + time.monotonic()) / 2
            elapsed = after_time - before_time
            delta = int(after.get("mediaTimeMs", 0)) - int(before.get("mediaTimeMs", 0))
            rendered = int(after.get("health_videoRendered", 0))
            frames += max(0, rendered - previous) if rendered >= previous else max(0, rendered)
            measured = delta / (elapsed * 1000)
            row = {"label": label, "elapsedSeconds": elapsed,
                   "mediaDeltaMs": delta, "measuredRate": measured,
                   "renderedFrames": frames, "before": {k: before.get(k) for k in FIELDS},
                   "after": {k: after.get(k) for k in FIELDS}}
            rows.append(row)
            print(json.dumps(row), flush=True)
            # Quantized disc navigation and sparse previews preclude exact
            # frame-by-frame equality. Check direction, output, and bounded
            # rate agreement rather than trusting the selected label alone.
            ratio = measured / rate
            if frames <= 0 or not .65 <= ratio <= 1.35 or after.get("health_playerError"):
                failures.append(f"{label}: source/output rate mismatch: {measured:.2f} vs {rate}")

        if args.phase == "coarse":
            control = discover_sage_control(str(current["serverAddress"]))
            context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
            for rate in (64, 128, 256):
                # Test-only public API isolates actual scan-clock throughput
                # from remote ramp timing; production still uses stock keys.
                if rate == 64:
                    control.media_control(context, "rate", rate=rate)
                else:
                    # The installed MCP rate API deliberately caps at 64;
                    # use the existing stock Faster event, never bypass its
                    # validator or modify Core just to position this test.
                    call_dict(client, "dev_sage_command", {"command": "faster"})
                expect_rate(rate, f"coarse_{rate}")
                measure(rate, f"coarse_{rate}_progress")
        for rate in ((2, 4, 8, 16) if args.phase in ("full", "forward") else ()):
            key("FF")
            expect_rate(rate, f"forward_{rate}")
            measure(rate, f"forward_{rate}_progress")
        for rate in ((8, 4, 2, 1) if args.phase == "full" else ()):
            key("REWIND")
            expect_rate(rate, f"opposite_down_{rate}")
        if args.phase == "reverse":
            key("PLAY")
            expect_rate(1, "normal_before_reverse")
        for rate in ((-2, -4, -8, -16) if args.phase in ("full", "reverse") else ()):
            key("REWIND")
            expect_rate(rate, f"reverse_{abs(rate)}")
            measure(rate, f"reverse_{abs(rate)}_progress")
        for rate in ((-8, -4, -2, 1) if args.phase == "full" else ()):
            key("FF")
            expect_rate(rate, f"reverse_opposite_down_{rate}")
        key("PLAY")
        expect_rate(1, "normal_before_cancellation")
        key("FF")
        expect_rate(2, "scan_before_playpause")
        key("PLAY_PAUSE")
        expect_rate(1, "playpause_cancels_scan")
        time.sleep(3)
        final = state()
        if not final.get("health_isPlaying"):
            raise RuntimeError("Play/Pause cancelled rate but did not resume playback")
        result["passed"] = not failures
    except Exception as failure:
        result["error"] = str(failure)
    finally:
        try:
            call_dict(client, "firetv_key", {"key": "PLAY"}, timeout=30.0)
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
