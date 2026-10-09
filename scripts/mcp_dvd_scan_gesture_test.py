#!/usr/bin/env python3
"""Affected DVD dedicated-button tap, held ramp, release, Play and TS separation gates."""
from __future__ import annotations
import argparse
import json
import time
from pathlib import Path
from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "playbackRate", "health_isPlaying", "health_videoRendered",
          "health_audioRendered", "health_playerError", "dvdTimeScrollActive", "inputLastMappedCommand",
          "state", "menuName", "popupName", "health_playWhenReady", "health_isLoading",
          "health_videoQueuedInput", "health_videoDecoderInitCount", "health_videoDecoderReleaseCount",
          "dvdDecoderBufferedAheadMs", "dvdPushedBytes", "dvdLastReadBytes", "dvdEpochPushedBytes",
          "dvdDrainPollCount", "dvdDrainReadyCount", "serverFlushSequence", "lastPushReply",
          "lastPushPayloadBytes", "lastPushFlags", "bufferLeft", "health_bufferLeft",
          "health_playerPositionMs", "health_bufferedPositionMs", "connectionMediaCommandCount",
          "health_capturedMonotonicMs")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--decoder-only", action="store_true",
                        help="Isolate native DVD extraction with public rate API up to64x; not a remote-key/256x gate")
    parser.add_argument("--decoder-direction", choices=("both", "forward", "reverse"), default="both",
                        help="Select affected decoder-only rows without repeating completed direction")
    parser.add_argument("--decoder-start-ms", type=int, default=None,
                        help="Optional public DVD seek before decoder-only scans, leaving room for reverse")
    args = parser.parse_args()
    if not args.decoder_only and (args.decoder_direction != "both" or args.decoder_start_ms is not None):
        parser.error("decoder direction/start selection requires --decoder-only")
    if args.decoder_start_ms is not None and args.decoder_start_ms < 0:
        parser.error("--decoder-start-ms must be nonnegative")
    client = MCPProcess()
    result = {"passed": False, "rows": []}
    try:
        initialize(client)
        def state():
            sample = call_dict(client, "dev_player_state", timeout=30)
            # These are the already bounded gate probes, not extra live polling.
            # Keep the full last probe so a stalled rate has byte/decoder/UI
            # evidence before finally sends PLAY and destroys the condition.
            result["lastObservedState"] = sample
            return sample
        def key(value): return call_dict(client, "firetv_key", {"key": value})
        def compact(value): return {k: value.get(k) for k in FIELDS}
        def wait_rate(rate, budget=15):
            deadline = time.monotonic() + budget
            while time.monotonic() < deadline:
                sample = state()
                if abs(float(sample.get("playbackRate", 0)) - rate) < .01:
                    if rate != 1 or (sample.get("health_isPlaying") and not sample.get("health_playerError")):
                        return sample
                time.sleep(.15)
            raise RuntimeError(f"Expected acknowledged rate {rate}, got {compact(sample)}")
        current = state()
        if not current.get("dvdSessionPending") or not current.get("health_isPlaying"):
            raise RuntimeError("Advancing native DVD title required")
        result["server"] = current.get("serverAddress")
        result["player"] = current.get("playerClass")
        if args.decoder_only:
            # SageMC Default DVD FF/REW deliberately turns FF/RW into timed
            # skips. Never change that user preference to make a rate-key
            # oracle pass. Isolate the shared extractor via the existing
            # stock-compatible plugin's bounded public SetPlaybackRate API.
            # Its64x limit remains intact; this is not physical key evidence.
            result["scope"] = "Decoder scan via public rate API; not remote keys or256x"
            control = discover_sage_control(str(current["serverAddress"]))
            context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
            if args.decoder_start_ms is not None:
                before_seek = state()
                control.seek(context, args.decoder_start_ms)
                deadline = time.monotonic() + 30
                while time.monotonic() < deadline:
                    positioned = state()
                    if (int(positioned.get("serverFlushSequence", 0)) > int(before_seek.get("serverFlushSequence", 0))
                            and abs(int(positioned.get("mediaTimeMs", 0)) - args.decoder_start_ms) <= 15000
                            and positioned.get("health_isPlaying") and not positioned.get("health_playerError")
                            and int(positioned.get("health_videoRendered", 0)) > 2
                            and int(positioned.get("health_audioRendered", 0)) > 2):
                        result["positioning"] = compact(positioned)
                        break
                    time.sleep(.25)
                else:
                    raise RuntimeError("Public DVD scene seek did not recover output")
            directions = (1, -1) if args.decoder_direction == "both" else ((1,) if args.decoder_direction == "forward" else (-1,))
            for direction in directions:
                row = {"kind": "decoder_scan", "direction": direction, "rates": []}
                result["rows"].append(row)
                for magnitude in (2, 4, 8, 16, 32, 64):
                    rate = direction * magnitude
                    control.media_control(context, "rate", rate=rate)
                    before = wait_rate(rate)
                    entry = {"rate": rate, "before": compact(before), "samples": [], "counterRebases": 0}
                    row["rates"].append(entry)
                    deadline = time.monotonic() + 15
                    while time.monotonic() < deadline:
                        time.sleep(.35)
                        after = state()
                        entry["samples"].append(compact(after))
                        if after.get("health_playerError") or float(after.get("playbackRate", 0)) != rate:
                            raise RuntimeError(f"Public DVD scan{rate} lost its rate or errored")
                        if int(after.get("health_videoRendered", 0)) < int(before.get("health_videoRendered", 0)):
                            # Authored cell/decoder replacement resets counters.
                            # Wait for new-epoch output; do not add old counters
                            # or count an acknowledged rate alone as a PASS.
                            entry["counterRebases"] += 1
                        elif (after.get("health_isPlaying")
                              and int(after.get("health_videoRendered", 0)) > int(before.get("health_videoRendered", 0))):
                            entry["after"] = compact(after)
                            break
                        before = after
                    else:
                        raise RuntimeError(f"Public DVD scan{rate} did not advance preview video")
                control.media_control(context, "rate", rate=1)
                before = wait_rate(1, 25)
                time.sleep(3)
                after = state()
                row["normalAfter"] = compact(after)
                if (after.get("health_playerError") or not after.get("health_isPlaying")
                        or int(after.get("health_videoRendered", 0)) <= int(before.get("health_videoRendered", 0))
                        or int(after.get("health_audioRendered", 0)) <= int(before.get("health_audioRendered", 0))):
                    raise RuntimeError("Public scan release did not restore advancing A/V")
            result["passed"] = True
            print(json.dumps({k: v for k, v in result.items() if k not in ("rows", "lastObservedState")}), flush=True)
            return 0
        key("PLAY")
        wait_rate(1)
        # Every short gesture changes exactly one rate; UP cannot also change it.
        for direction, target in (("FF", 2), ("FF", 4), ("RW", 2), ("RW", 1)):
            call_dict(client, "dev_dvd_arrow_hold", {"key": direction, "hold_ms": 200})
            observed = wait_rate(target)
            time.sleep(1.2)
            after = state()
            result["rows"].append({"kind": "tap", "key": direction, "target": target,
                                   "ack": compact(observed), "after": compact(after)})
            if float(after.get("playbackRate", 0)) != target:
                raise RuntimeError("Tap changed again or resumed on short release")
        for direction in ("FF", "RW"):
            wait_rate(1)
            started = time.monotonic()
            call_dict(client, "dev_dvd_arrow_hold", {"key": direction, "hold_ms": 10000})
            row = {"kind": "hold", "key": direction, "samples": []}
            result["rows"].append(row)
            peak = 1
            while time.monotonic() - started < 10.8:
                sample = state()
                rate = float(sample.get("playbackRate", 1))
                peak = max(peak, abs(rate))
                row["samples"].append(compact(sample))
                if sample.get("health_playerError") or abs(rate) > 256:
                    raise RuntimeError("Held scan errored or exceeded the maximum")
                time.sleep(.15)
            row["peak"] = peak
            row["after"] = compact(wait_rate(1, 20))
            if peak != 256:
                raise RuntimeError(f"{direction} hold did not reach capped 256x: {peak}")
            # Require actual advancing replacement A/V, not just a rate echo.
            before = state()
            time.sleep(2)
            after = state()
            row["normalAfter"] = compact(after)
            if (int(after.get("mediaTimeMs", 0)) <= int(before.get("mediaTimeMs", 0))
                    or int(after.get("health_videoRendered", 0)) <= int(before.get("health_videoRendered", 0))
                    or int(after.get("health_audioRendered", 0)) <= int(before.get("health_audioRendered", 0))):
                raise RuntimeError("Hold release did not resume advancing A/V")
        started = time.monotonic()
        call_dict(client, "dev_dvd_arrow_hold", {"key": "FF", "hold_ms": 4000})
        time.sleep(1.5)
        key("PLAY")
        observed = wait_rate(1)
        while time.monotonic() - started < 5:
            time.sleep(.2)
            after = state()
            if float(after.get("playbackRate", 0)) != 1:
                raise RuntimeError("Cancelled hold reactivated after Play")
        result["rows"].append({"kind": "play_cancels_hold", "after": compact(observed)})
        key("RIGHT")
        cursor = state()
        if not cursor.get("dvdTimeScrollActive"):
            raise RuntimeError("Left/Right TS ownership regressed")
        call_dict(client, "dev_dvd_arrow_hold", {"key": "FF", "hold_ms": 200})
        scan = wait_rate(2)
        if scan.get("dvdTimeScrollActive"):
            raise RuntimeError("Dedicated scan did not exit the TS cursor")
        key("PLAY")
        result["rows"].append({"kind": "timescroll_separation", "cursor": compact(cursor),
                               "scan": compact(scan), "after": compact(wait_rate(1))})
        result["passed"] = True
    except Exception as failure:
        result["error"] = str(failure)
        try:
            evidence = call_dict(client, "collect_playback_diagnostics",
                                 {"label": "dvd-scan-gesture-failure"}, timeout=90)
            result["diagnostics"] = {k: v for k, v in evidence.items() if k != "state_snapshot"}
        except Exception as diagnostic_error:
            result["diagnosticError"] = str(diagnostic_error)
    finally:
        try: call_dict(client, "firetv_key", {"key": "PLAY"})
        except Exception: pass
        client.close()
        path = Path(args.output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k not in ("rows", "lastObservedState")}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__": raise SystemExit(main())
