#!/usr/bin/env python3
"""Open the embedded A/V-sync screen for physical camera preflight.

This uses the debug-only MCP control instead of menu coordinates so a camera
framing check always targets the same full-screen calibration surface. It does
not alter the saved player/audio configuration.
"""
from __future__ import annotations

import argparse
import json
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", choices=("decoded", "passthrough"))
    parser.add_argument("--offset-ms", type=int)
    parser.add_argument(
        "--passthrough-offset-enabled",
        choices=("true", "false"),
    )
    parser.add_argument(
        "--stress-immediate-open",
        action="store_true",
        help="Debug gate: deliberately open calibration during an audio rebuild",
    )
    args = parser.parse_args()
    if args.offset_ms is not None and not -4000 <= args.offset_ms <= 4000:
        parser.error("--offset-ms must be between -4000 and 4000")

    client = MCPProcess()
    try:
        initialize(client)
        state = call_dict(client, "dev_player_state", timeout=30.0)
        if not state.get("playerActive"):
            raise RuntimeError(
                "the embedded A/V-sync screen requires active playback; "
                "start the exact server fixture first"
            )
        audio_arguments: dict[str, object] = {}
        if args.output is not None:
            audio_arguments["output"] = args.output
        if args.offset_ms is not None:
            audio_arguments["offset_ms"] = args.offset_ms
        if args.passthrough_offset_enabled is not None:
            audio_arguments["passthrough_offset_enabled"] = (
                args.passthrough_offset_enabled == "true"
            )
        if audio_arguments:
            audio = call_dict(
                client,
                "dev_set_active_audio",
                audio_arguments,
                timeout=45.0,
            )
            print(json.dumps(audio, indent=2, sort_keys=True))
            if not audio.get("accepted"):
                raise RuntimeError(f"active audio adjustment was rejected: {audio}")
            if not args.stress_immediate_open:
                # The command acknowledges an asynchronous output/extractor
                # rebuild. Opening calibration immediately can race direct
                # AC-3 AudioTracks. Only the explicit stress gate skips this.
                time.sleep(2.0)
                deadline = time.monotonic() + 12.0
                while True:
                    state = call_dict(client, "dev_player_state", timeout=30.0)
                    if state.get("health_playerReady") and not state.get("health_errorState"):
                        break
                    if time.monotonic() >= deadline:
                        raise RuntimeError(
                            "active player did not recover after audio adjustment: "
                            f"ready={state.get('health_playerReady')} "
                            f"error={state.get('health_errorState')}"
                        )
                    time.sleep(0.5)
        result = call_dict(client, "dev_show_av_sync_test", timeout=30.0)
        print(json.dumps(result, indent=2, sort_keys=True))
        print("PASS: embedded A/V-sync screen requested through MCP")
        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
