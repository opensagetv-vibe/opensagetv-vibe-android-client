#!/usr/bin/env python3
"""Set live audio output/offset without opening a calibration or settings menu."""

from __future__ import annotations

import argparse
import json
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", choices=("decoded", "passthrough"))
    parser.add_argument("--offset-ms", type=int)
    parser.add_argument("--passthrough-offset-enabled", choices=("true", "false"))
    args = parser.parse_args()
    if args.output is None and args.offset_ms is None and args.passthrough_offset_enabled is None:
        parser.error("at least one audio setting is required")
    if args.offset_ms is not None and not -4000 <= args.offset_ms <= 4000:
        parser.error("--offset-ms must be between -4000 and 4000")

    requested: dict[str, object] = {}
    if args.output is not None:
        requested["output"] = args.output
    if args.offset_ms is not None:
        requested["offset_ms"] = args.offset_ms
    if args.passthrough_offset_enabled is not None:
        requested["passthrough_offset_enabled"] = args.passthrough_offset_enabled == "true"

    client = MCPProcess()
    try:
        initialize(client)
        before = call_dict(client, "dev_player_state", timeout=30.0)
        if not before.get("playerActive"):
            raise RuntimeError("live playback is required")
        result = call_dict(client, "dev_set_active_audio", requested, timeout=45.0)
        if not result.get("accepted"):
            raise RuntimeError(f"audio adjustment rejected: {result}")
        deadline = time.monotonic() + 20.0
        last_error = ""
        while time.monotonic() < deadline:
            try:
                state = call_dict(client, "dev_player_state", timeout=30.0)
            except RuntimeError as exc:
                last_error = str(exc)
                time.sleep(0.5)
                continue
            if (state.get("playerActive") and not state.get("health_errorState") and
                    state.get("health_playerReady") and
                    (args.offset_ms is None or state.get("audioOffsetMs") == args.offset_ms)):
                print(json.dumps({
                    "accepted": True,
                    "playerActive": state.get("playerActive"),
                    "healthPlayerReady": state.get("health_playerReady"),
                    "healthErrorState": state.get("health_errorState"),
                    "audioOffsetMs": state.get("audioOffsetMs"),
                    "audioOffsetPath": state.get("audioOffsetPath"),
                    "audioOutputMode": state.get("audioOutputMode"),
                }, indent=2))
                print("PASS: live audio adjustment ready without menu navigation")
                return 0
            time.sleep(0.5)
        raise RuntimeError(f"live audio adjustment did not settle: {last_error}")
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
