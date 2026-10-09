#!/usr/bin/env python3
"""Gate one local codec restart on the current generated DVD, never a server seek."""
import argparse
import json
import time
from pathlib import Path

from mcp_seek_suite import MCPProcess, initialize, call_dict
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "health_playerPositionMs", "health_isPlaying", "health_videoRendered",
          "health_audioRendered", "health_videoQueuedInput", "health_videoDecoderInitCount",
          "health_videoDecoderReleaseCount", "health_playerError", "serverFlushSequence")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    p = MCPProcess()
    report = {"passed": False, "syntheticFault": "Local debug output withholding, not actual firmware failure", "samples": []}
    try:
        initialize(p)
        initial = call_dict(p, "dev_player_state", timeout=45)
        if not initial.get("dvdSessionPending"):
            raise RuntimeError("Current generated DVD required")
        control = discover_sage_control(str(initial["serverAddress"]))
        ctx = str(initial.get("uiContextHint") or initial["clientId"].replace(":", ""))
        media = control.ui_state(ctx).get("media", {})
        if not any(str(path).replace("\\", "/").rstrip("/").endswith("/OpenSageTV_Vibe_Test_DVD/VIDEO_TS")
                   for path in media.get("paths", [])):
            raise RuntimeError("Refusing fault/navigation on another DVD volume")
        report["server"] = initial["serverAddress"]
        report["media"] = media
        control.remote_command(ctx, "DVD Menu")
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            before = call_dict(p, "dev_player_state", timeout=45)
            if (before.get("dvdHighlightVisible") and before.get("health_isPlaying")
                    and int(before.get("health_videoRendered", 0)) > 2):
                break
            time.sleep(.5)
        else:
            raise RuntimeError("No healthy authored root before injection")
        report["before"] = {k: before.get(k) for k in FIELDS}
        report["arm"] = call_dict(p, "dev_set_native_dvd_codec_fault", {"enabled": True}, timeout=45)
        control.remote_command(ctx, "Down")
        control.remote_command(ctx, "Select")
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            state = call_dict(p, "dev_player_state", timeout=45)
            report["samples"].append({k: state.get(k) for k in FIELDS})
            if (int(state.get("serverFlushSequence", 0)) > int(before.get("serverFlushSequence", 0))
                    and state.get("health_isPlaying") and not state.get("health_playerError")
                    and int(state.get("health_videoDecoderInitCount", 0)) == 2
                    and int(state.get("health_videoDecoderReleaseCount", 0)) == 1
                    and int(state.get("health_videoRendered", 0)) > 2
                    and int(state.get("health_audioRendered", 0)) > 2):
                time.sleep(3)
                after = call_dict(p, "dev_player_state", timeout=45)
                report["after"] = {k: after.get(k) for k in FIELDS}
                if (after.get("health_isPlaying") and not after.get("health_playerError")
                        and int(after.get("health_videoRendered", 0)) > int(state["health_videoRendered"])
                        and int(after.get("health_audioRendered", 0)) > int(state["health_audioRendered"])
                        and int(after.get("serverFlushSequence", 0)) == int(state["serverFlushSequence"])
                        and int(after.get("health_videoDecoderInitCount", 0)) == 2):
                    report["passed"] = True
                    break
            time.sleep(.5)
        if not report["passed"]:
            raise RuntimeError("One retained-queue restart did not recover advancing A/V")
        report["screenshot"] = call_dict(p, "take_screenshot", {"label": "one-shot-codec-recovery-pass"}, timeout=45)
    except Exception as error:
        report["error"] = str(error)
        try:
            checkpoint = call_dict(p, "dev_test_checkpoint", {"label": "one-shot-codec-recovery-failure", "log_lines": 900}, timeout=45)
            report["failureArtifacts"] = checkpoint.get("artifacts", {})
        except Exception as capture_error:
            report["captureError"] = str(capture_error)
    finally:
        try:
            report["disarm"] = call_dict(p, "dev_set_native_dvd_codec_fault", {"enabled": False}, timeout=45)
        except Exception as disarm_error:
            report["disarmError"] = str(disarm_error)
            report["passed"] = False
        p.close()
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k not in ("samples", "arm", "disarm")}), flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
