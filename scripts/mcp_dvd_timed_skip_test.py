#!/usr/bin/env python3
"""Bounded DVD timed-skip source/server/STV witnesses; no profile edits."""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

FIELDS = ("mediaTimeMs", "playbackRate", "state", "menuName", "popupName",
          "dvdLogicalClockBaseMs", "dvdNormalSourceClock", "dvdStc45Khz",
          "serverFlushSequence", "dvdNewCellCount",
          "health_capturedMonotonicMs", "health_playerPositionMs", "health_isPlaying",
          "health_playWhenReady", "health_flushed", "health_videoRendered",
          "health_audioRendered", "health_videoDecoderInitCount", "health_playerError",
          "dvdLatestVideoSampleUs", "dvdLatestAudioSampleUs", "inputLastMappedCommand")


def verify_settled_timeline(row):
    """Require real post-FLUSH A/V clocks, not an immediate seek-time guess."""
    baseline_flush = int(row["before"].get("serverFlushSequence", 0))
    eligible = [s for s in row["samples"]
                if int(s["client"].get("serverFlushSequence", 0)) > baseline_flush
                and s["client"].get("health_isPlaying")
                and s["client"].get("health_flushed") is False
                and not s["client"].get("health_playerError")
                and int(s["client"].get("health_videoRendered", 0)) > 2
                and int(s["client"].get("health_audioRendered", 0)) > 2]
    if not eligible:
        raise RuntimeError("No decoded post-FLUSH DVD landing")
    final_flush = eligible[-1]["client"]["serverFlushSequence"]
    same_epoch = [s for s in eligible if s["client"]["serverFlushSequence"] == final_flush]
    # The first decoded frame can precede expiry of Core's short seek-time
    # guess. Require a genuinely settled clock window, not immediate agreement
    # at that first frame. Only trim the startup prefix: any later divergence
    # remains in the window and fails the existing bound below.
    initial_clock_samples = 0
    while same_epoch and abs(int(same_epoch[0]["serverTimeMs"])
                            - int(same_epoch[0]["client"]["mediaTimeMs"])) > 2000:
        same_epoch = same_epoch[1:]
        initial_clock_samples += 1
    if initial_clock_samples and not same_epoch:
        raise RuntimeError("Settled server clock differs from decoded source clock; never converged")
    if len(same_epoch) < 2:
        raise RuntimeError("No sustained post-FLUSH clock window")
    first, last = same_epoch[0], same_epoch[-1]
    a, b = first["client"], last["client"]
    elapsed = int(b["health_capturedMonotonicMs"]) - int(a["health_capturedMonotonicMs"])
    client_delta = int(b["mediaTimeMs"]) - int(a["mediaTimeMs"])
    server_delta = int(last["serverTimeMs"]) - int(first["serverTimeMs"])
    if elapsed < 1500 or not .7 <= client_delta / elapsed <= 1.3:
        raise RuntimeError("Settled DVD source clock froze, reversed or changed rate")
    if (server_delta < 500 or int(b["health_videoRendered"]) <= int(a["health_videoRendered"])
            or int(b["health_audioRendered"]) <= int(a["health_audioRendered"])):
        raise RuntimeError("Server timeline or settled DVD A/V failed to advance")
    differences = [abs(int(s["serverTimeMs"]) - int(s["client"]["mediaTimeMs"])) for s in same_epoch]
    if max(differences) > 2000:
        raise RuntimeError("Settled server clock differs from decoded source clock")
    return {"flush": final_flush, "observedDeviceMs": elapsed,
            "initialClockReanchorSamples": initial_clock_samples,
            "sourceAdvanceMs": client_delta, "serverAdvanceMs": server_delta,
            "realtimeRatio": client_delta / elapsed, "maxServerClientDifferenceMs": max(differences)}


def main():
    # Keep the pure clock oracle importable without a commissioned MCP runtime.
    from mcp_seek_suite import MCPProcess, call_dict, initialize
    from mcp_disc_test import wait_player_state
    from sagetv_dev_mcp.core_mcp_api import discover_sage_control

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--input", choices=("android", "remote", "public"), default="android")
    parser.add_argument("--observe-s", type=float, default=12)
    parser.add_argument("--capture", action="store_true")
    parser.add_argument("--pause-resume", action="store_true")
    parser.add_argument("--show-info", action="store_true",
                        help="Show normal SageMC Info after each skip so the elapsed label is a visible witness")
    args = parser.parse_args()
    if not 5 <= args.observe_s <= 30:
        parser.error("observation must be 5..30 seconds")
    p = MCPProcess()
    result = {"passed": False, "scope": "Settled timed-skip clock/A-V gate; visible label needs independent review; not exact DVD landing", "rows": []}
    try:
        initialize(p)
        current = call_dict(p, "dev_player_state")
        if not current.get("dvdSessionPending"):
            raise RuntimeError("Advancing native DVD required")
        if not current.get("health_isPlaying"):
            result["initialState"] = {k: current.get(k) for k in FIELDS}
            call_dict(p, "firetv_key", {"key": "PLAY"})
            ready = call_dict(p, "dev_wait_for_playback_started", {
                "timeout_s": 20, "verify_ms": 1500,
                "expect_video": True, "expect_audio": True}, timeout=30)
            if not ready.get("passed"):
                raise RuntimeError("Explicit PLAY did not restore DVD A/V")
        control = discover_sage_control(str(current["serverAddress"]))
        context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
        result["server"] = current["serverAddress"]
        result["input"] = args.input
        for command, operation in (("ff", "skip_forward"), ("rew", "skip_backward")):
            before = call_dict(p, "dev_player_state")
            row = {"command": command, "before": {k: before.get(k) for k in FIELDS}, "samples": []}
            result["rows"].append(row)
            started = time.monotonic()
            if args.input == "android":
                call_dict(p, "dev_sage_command", {"command": command})
            elif args.input == "remote":
                call_dict(p, "firetv_key", {"key": "FF" if command == "ff" else "RW"})
            else:
                control.media_control(context, operation)
            deadline = started + args.observe_s
            captured = set()
            info_sent = False
            while time.monotonic() < deadline:
                state = call_dict(p, "dev_player_state")
                server = control.ui_state(context)
                sample = {"elapsedSeconds": time.monotonic() - started,
                          "client": {k: state.get(k) for k in FIELDS},
                          "serverTimeMs": server.get("mediaTimeMs"),
                          "serverRate": server.get("playbackRate")}
                row["samples"].append(sample)
                result["lastObservedState"] = state
                if (args.show_info and not info_sent
                        and int(state.get("serverFlushSequence", 0)) > int(before.get("serverFlushSequence", 0))
                        and state.get("health_isPlaying") and state.get("health_flushed") is False):
                    # One normal Info press after the actual landing; never a
                    # repeated Refresh/keepalive that could mask the defect.
                    call_dict(p, "dev_sage_command", {"command": "info"})
                    info_sent = True
                    print("VISIBLE_WINDOW: " + command, flush=True)
                if args.capture:
                    for threshold in (2, 7, 11):
                        if sample["elapsedSeconds"] >= threshold and threshold not in captured:
                            captured.add(threshold)
                            sample["screenshot"] = call_dict(p, "take_screenshot", {
                                "label": f"dvd-timed-{command}-{threshold}s"})
                if state.get("health_playerError") or float(state.get("playbackRate", 0)) != 1:
                    raise RuntimeError("Timed skip errored or became a rate scan; preserve profile")
                time.sleep(.5)
            row["verification"] = verify_settled_timeline(row)
            print(json.dumps({"command": command, "samples": len(row["samples"]),
                              "last": row["samples"][-1]}), flush=True)
        if args.pause_resume:
            call_dict(p, "firetv_key", {"key": "PAUSE"})
            before, pause_latency = wait_player_state(p, 3)
            time.sleep(3)
            after = call_dict(p, "dev_player_state")
            held = int(after["mediaTimeMs"]) - int(before["mediaTimeMs"])
            if after.get("health_playWhenReady") or abs(held) > 300:
                raise RuntimeError("DVD pause did not hold actual source clock")
            call_dict(p, "firetv_key", {"key": "PLAY"})
            wait_player_state(p, 2)
            ready = call_dict(p, "dev_wait_for_playback_started", {
                "timeout_s": 20, "verify_ms": 2000,
                "expect_video": True, "expect_audio": True}, timeout=30)
            if not ready.get("passed"):
                raise RuntimeError("DVD PLAY did not restore advancing A/V")
            result["pauseResume"] = {"pauseLatencyMs": pause_latency,
                                     "pausedSourceDeltaMs": held, "resumedAv": True}
        result["passed"] = True
    except Exception as exc:
        result["error"] = str(exc)
    finally:
        p.close()
        Path(args.output).parent.mkdir(parents=True, exist_ok=True)
        Path(args.output).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k not in ("rows", "lastObservedState")}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
