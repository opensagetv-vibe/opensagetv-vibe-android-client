#!/usr/bin/env python3
"""Verify stock STV Time Scroll / primary Skip / Time Scroll DVD seek sequences."""
from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from mcp_seek_suite import MCPProcess, call_dict, initialize
from sagetv_dev_mcp.core_mcp_api import discover_sage_control

FIELDS = ("mediaTimeMs", "playbackRate", "serverFlushSequence", "dvdNewCellCount",
          "health_isPlaying", "health_videoRendered", "health_audioRendered",
          "health_playerPositionMs", "health_playerError", "inputEventSequence")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True)
    parser.add_argument("--step-ms", type=int, default=150000,
                        help="Existing STV time-scroll step; does not change server settings")
    parser.add_argument("--client-events", action="store_true",
                        help="Send ordinary MiniClient events via the existing debug command tool; no server plugin deployment")
    args = parser.parse_args()
    client = MCPProcess()
    result = {"passed": False, "rows": []}
    control = None
    context = None
    try:
        initialize(client)
        def state():
            return call_dict(client, "dev_player_state", timeout=30)
        current = state()
        if (not current.get("dvdSessionPending") or not current.get("health_isPlaying")
                or float(current.get("playbackRate", 1)) != 1):
            raise RuntimeError("Advancing native DVD title at 1x required")
        control = discover_sage_control(str(current["serverAddress"]))
        context = str(current.get("uiContextHint") or current["clientId"].replace(":", ""))
        # Play cancels any preexisting Time Scroll cursor in SageMC without
        # committing it. Never blindly toggle TS to 'ensure it is off'.
        control.remote_command(context, "Play")
        time.sleep(.5)
        for command, delta in (("Skip Fwd/Page Right", args.step_ms),
                               ("Skip Bkwd/Page Left", -args.step_ms)):
            # Freeze the source clock so round-trip diagnostic latency cannot
            # be mistaken for a wrong cursor offset. Resume after committing.
            control.remote_command(context, "Pause")
            time.sleep(.75)
            before = state()
            # TS selects GetMediaTime() in the STV, i.e. Core's NAV clock,
            # not the client's independent PES/render timestamp snapshot.
            server_before = control.ui_state(context)
            target = int(server_before["mediaTimeMs"]) + delta
            if target < 0: raise RuntimeError("Fixture must have room for both directions")
            commands = []
            for event in ("Time Scroll", command, "Time Scroll"):
                if args.client_events:
                    key = "time_scroll" if event == "Time Scroll" else (
                        "ff" if delta > 0 else "rew")
                    commands.append(call_dict(client, "dev_sage_command", {"command": key}))
                else:
                    # Bridge exposes the short stock command aliases.
                    canonical = "Time Scroll" if event == "Time Scroll" else (
                        "Skip Fwd" if delta > 0 else "Skip Bkwd")
                    commands.append(control.remote_command(context, canonical))
                time.sleep(.5)
            control.remote_command(context, "Play")
            deadline = time.monotonic() + 18
            row = {"command": command, "expectedDeltaMs": delta,
                   "expectedTargetMs": target, "events": commands,
                   "serverBefore": server_before,
                   "before": {k: before.get(k) for k in FIELDS}, "samples": []}
            result["rows"].append(row)
            while time.monotonic() < deadline:
                now = state()
                if now.get("inputEventSequence") != before.get("inputEventSequence"):
                    raise RuntimeError("Concurrent physical remote input invalidated TS command measurement")
                error = int(now.get("mediaTimeMs", 0)) - target - int(now.get("health_playerPositionMs", 0))
                row["samples"].append({"sourceAnchorErrorMs": error,
                                       **{k: now.get(k) for k in FIELDS}})
                if (int(now.get("serverFlushSequence", 0)) > int(before.get("serverFlushSequence", 0))
                        and now.get("health_isPlaying") and not now.get("health_playerError")
                        and int(now.get("health_videoRendered", 0)) > 2
                        and int(now.get("health_audioRendered", 0)) > 2
                        and float(now.get("playbackRate", 1)) == 1 and abs(error) < 3000):
                    row["after"] = {k: now.get(k) for k in FIELDS}
                    row["sourceAnchorErrorMs"] = error
                    row["serverState"] = control.ui_state(context)
                    break
                time.sleep(.25)
            else:
                raise RuntimeError(f"{command}: TS sequence did not land at expected decoded source")
            print(json.dumps(row), flush=True)
            time.sleep(2)
        result["passed"] = True
    except Exception as error:
        result["error"] = str(error)
    finally:
        if control is not None and context:
            try: control.remote_command(context, "Play")
            except Exception: pass
        client.close()
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "rows"}), flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__": raise SystemExit(main())
