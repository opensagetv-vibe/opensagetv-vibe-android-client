#!/usr/bin/env python3
"""Physical remote gate for DVD TS accept/cancel and dedicated scan separation."""
from __future__ import annotations
import argparse
import json
import time
from pathlib import Path
from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "playbackRate", "serverFlushSequence", "dvdTimeScrollActive",
          "dvdTimeScrollEntryPositionMs", "dvdTimeScrollSteps", "health_isPlaying",
          "health_videoRendered", "health_audioRendered", "health_playerPositionMs",
          "health_playerError", "inputLastMappedCommand")


def cursor_target_ms(cursor, step_ms):
    """Retain key-down intent, never a late server seek-guess/landing echo."""
    if step_ms <= 0:
        raise ValueError("Cursor step must be positive")
    return max(0, int(cursor["dvdTimeScrollEntryPositionMs"])
               + int(cursor["dvdTimeScrollSteps"]) * step_ms)


def source_anchor_error_ms(state, target_ms):
    """Compare the decoded replacement epoch, not time since ADB submission."""
    return (int(state.get("mediaTimeMs", 0)) - target_ms
            - int(state.get("health_playerPositionMs", 0)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--step-ms", type=int, default=150000)
    parser.add_argument("--start-ms", type=int)
    parser.add_argument("--landing-tolerance-ms", type=int, default=4000,
                        help="Decoded DVD/VOBU landing allowance; does not change playback or keys")
    parser.add_argument("--skip-dedicated-scan", action="store_true",
                        help="Run cursor/accept/cancel only; preserve user-selected timed-skip mode")
    parser.add_argument("--capture-cursor", action="store_true",
                        help="Retain the actual STV cursor display as an independent target witness")
    args = parser.parse_args()
    if not 1 <= args.landing_tolerance_ms <= 20000:
        parser.error("--landing-tolerance-ms must be 1..20000")
    client = MCPProcess()
    result = {"passed": False, "rows": [], "failures": []}
    try:
        initialize(client)
        def state(): return call_dict(client, "dev_player_state", timeout=30)
        def key(value): return call_dict(client, "firetv_key", {"key": value})
        current = state()
        if not current.get("dvdSessionPending") or not current.get("health_isPlaying"):
            raise RuntimeError("Advancing native DVD title required")
        control = discover_sage_control(str(current["serverAddress"]))
        context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
        key("PLAY")
        if args.start_ms is not None:
            control.seek(context, args.start_ms)
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                positioned = state()
                if (positioned.get("health_isPlaying") and not positioned.get("health_playerError")
                        and abs(int(positioned.get("mediaTimeMs", 0)) - args.start_ms) < 10000):
                    break
                time.sleep(.25)
            else: raise RuntimeError("Public stock seek did not reach the test scene")
            time.sleep(2)
        for direction, sign in (("RIGHT", 1), ("LEFT", -1)):
            before = state()
            server_before = control.ui_state(context)
            pre_key_server_estimate = int(server_before["mediaTimeMs"]) + sign * args.step_ms
            key(direction)
            cursor = state()
            result["lastCursorCheck"] = {k: cursor.get(k) for k in FIELDS}
            if not cursor.get("dvdTimeScrollActive") or float(cursor.get("playbackRate", 1)) != 1:
                raise RuntimeError(f"{direction}: did not enter TS at normal playback rate")
            if int(cursor.get("serverFlushSequence", 0)) != int(before.get("serverFlushSequence", 0)):
                raise RuntimeError("Cursor adjustment sought before Center accepted")
            # Use the source clock captured at the actual key DOWN. A prior
            # HTTP/snapshot measurement can be seconds old by the time the
            # queued UI gesture executes. Keep that older estimate as evidence,
            # not as a false exact destination oracle for moving playback.
            if int(cursor["dvdTimeScrollSteps"]) != sign:
                raise RuntimeError("One arrow press did not produce one directional cursor step")
            target = cursor_target_ms(cursor, args.step_ms)
            cursor_image = call_dict(client, "take_screenshot", {
                "label": "dvd-cursor-" + direction.lower(),
            }) if args.capture_cursor else None
            key("SELECT")
            committed = control.ui_state(context)
            # Read the STV/Core's destination echo after accepting its cursor.
            # It is a requested-position oracle only, not proof of playback;
            # the fresh FLUSH, independently decoded source anchor and A/V
            # counters below are still required. Slow UI handling can move
            # the initial cursor beyond the client's earlier key DOWN clock.
            observed_step = int(committed["mediaTimeMs"]) - int(before["mediaTimeMs"])
            # Android acknowledges the queued key, not completion of the
            # server's asynchronous TS/Seek. This immediate echo may still be
            # the old NAV clock. Retain it as evidence, but let the fresh FLUSH,
            # decoded source anchor and advancing A/V below decide the result.
            # Failing here also sent finally-PLAY before the queued accept ran.
            # The server clock after Center is not a stable requested-target
            # oracle: its short seek guess may have expired while the ADB key
            # helper delivers its response. Keep the key-down clock/step intent
            # and the optional visible cursor witness, not that later NAV time.
            deadline = time.monotonic() + 18
            row = {"direction": direction, "targetMs": target,
                   "cursorScreenshot": cursor_image,
                   "preKeyServerEstimateMs": pre_key_server_estimate,
                   "serverCommitState": committed,
                   "immediateServerStepMs": observed_step,
                   "landingToleranceMs": args.landing_tolerance_ms,
                   "before": {k: before.get(k) for k in FIELDS},
                   "cursor": {k: cursor.get(k) for k in FIELDS}, "samples": []}
            result["rows"].append(row)
            while time.monotonic() < deadline:
                after = state()
                error = source_anchor_error_ms(after, target)
                row["samples"].append({"anchorErrorMs": error, **{k: after.get(k) for k in FIELDS}})
                if (not after.get("dvdTimeScrollActive") and after.get("health_isPlaying")
                        and not after.get("health_playerError")
                        and int(after.get("serverFlushSequence", 0)) > int(before.get("serverFlushSequence", 0))
                        and int(after.get("health_videoRendered", 0)) > 2
                        and int(after.get("health_audioRendered", 0)) > 2
                        and abs(error) < args.landing_tolerance_ms):
                    row["anchorErrorMs"] = error
                    break
                time.sleep(.25)
            else:
                result["failures"].append(f"{direction}: Center did not reach the decoded TS destination")
            print(json.dumps(row), flush=True)
            time.sleep(2)
        for cancellation in ("BACK", "PLAY"):
            before = state()
            key("RIGHT")
            key("RIGHT")
            key("LEFT")
            cursor = state()
            if not cursor.get("dvdTimeScrollActive") or int(cursor.get("dvdTimeScrollSteps", 0)) != 1:
                raise RuntimeError("Repeated/opposite arrows incorrectly toggled TS")
            key(cancellation)
            after = state()
            if (after.get("dvdTimeScrollActive") or float(after.get("playbackRate", 1)) != 1
                    or not after.get("health_isPlaying") or after.get("health_playerError")
                    or int(after.get("serverFlushSequence", 0)) != int(before.get("serverFlushSequence", 0))
                    or abs(int(after.get("mediaTimeMs", 0)) - int(before.get("mediaTimeMs", 0))) > 20000):
                raise RuntimeError(f"{cancellation}: cancel caused a seek, exit or scan")
            result["rows"].append({"cancel": cancellation, "cursor": {k: cursor.get(k) for k in FIELDS},
                                   "after": {k: after.get(k) for k in FIELDS}})
        for scan in (() if args.skip_dedicated_scan else ("FF", "REWIND")):
            key("RIGHT")
            key(scan)
            deadline = time.monotonic() + 8
            while time.monotonic() < deadline:
                after = state()
                rate = float(after.get("playbackRate", 1))
                if not after.get("dvdTimeScrollActive") and (rate > 1 if scan == "FF" else rate < 0):
                    break
                time.sleep(.25)
            else: raise RuntimeError(f"{scan}: dedicated key adjusted TS instead of scanning")
            result["rows"].append({"dedicatedScan": scan, "state": {k: after.get(k) for k in FIELDS}})
            key("PLAY")
            time.sleep(3)
        result["passed"] = not result["failures"]
    except Exception as error: result["error"] = str(error)
    finally:
        try: call_dict(client, "firetv_key", {"key": "PLAY"})
        except Exception: pass
        client.close()
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "rows"}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__": raise SystemExit(main())
