#!/usr/bin/env python3
"""Commission the on-device MediaCodec profile and fallback candidate policy."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_fixture, default_server_address, default_server_value

import argparse
import json
import os
from pathlib import Path
import sys

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Android MediaCodec capability gate")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument(
        "--server-path",
        default=default_fixture("seek_server_path", "/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts"),
    )
    parser.add_argument("--player", choices=("media3", "exoplayer"), default="media3")
    parser.add_argument("--streaming", choices=("pull", "push"), default="pull")
    parser.add_argument("--expected-interlace", choices=("interlaced", "progressive", "any"),
                        default="interlaced")
    args = parser.parse_args()

    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True,
            "graceful_timeout_s": 2.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")
        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": "hardware_preferred",
        })
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        })
        wait_automation_ready(client)
        started = call_dict(client, "dev_play_server_path", {
            "server_path": args.server_path,
            "timeout_s": 60.0,
            "verify_ms": 2500,
            "restart_from_beginning": True,
        }, timeout=95.0)
        require(bool(started.get("passed")), f"fallback-mode playback failed: {started}")
        state = call_dict(client, "dev_player_state", timeout=30.0)
        require(state.get("health_videoDecoderKind") in ("hardware", "software"),
                f"actual decoder classification unavailable: {state}")

        profile = call_dict(client, "dev_codec_capabilities", timeout=30.0)
        count = int(profile.get("codecProfileCount", 0) or 0)
        entries = str(profile.get("codecProfiles", ""))
        observations = str(profile.get("codecObservations", ""))
        require(count > 0, f"empty codec profile: {profile}")
        require("video/mpeg2,hw" in entries, "hardware MPEG-2 decoder missing from profile")
        require("video/mpeg2,sw" in entries, "software MPEG-2 fallback missing from profile")
        require(profile.get("codecInterlaceCapability") == "not_reported_by_android",
                "interlace support must not be inferred from MediaCodecList")
        observed = str(profile.get("codecInterlaceObserved", "unknown"))
        require(profile.get("codecDeinterlaceControl") == "not_exposed_by_android",
                "Android must not claim a selectable desktop deinterlacer")
        require(profile.get("codecInterlaceSequenceExtensionSeen") in (True, "true"),
                f"MPEG-2 sequence extension was not observed: {profile}")
        if args.expected_interlace == "interlaced":
            require(observed.startswith("interlaced_sequence"),
                    f"expected interlaced MPEG-2 bitstream, observed {observed}")
        elif args.expected_interlace == "progressive":
            require(observed == "progressive_sequence",
                    f"expected progressive MPEG-2 bitstream, observed {observed}")
        require(args.player in observations or (args.player == "exoplayer" and "legacy_exo" in observations),
                f"decoder initialization observation missing: {observations}")
        print(
            "PASS: MediaCodec profile "
            f"device={profile.get('codecDevice')} api={profile.get('codecApi')} entries={count} "
            f"actualDecoder={state.get('health_videoDecoder')} "
            f"actualKind={state.get('health_videoDecoderKind')}"
        )
        print(f"PASS: runtime observations {observations}")
        print(f"PASS: MPEG-2 bitstream observation {observed}")
        print("INFO: Android does not expose deinterlacer selection/quality; physical output is the gate")
        artifact_root = Path(os.environ.get("SAGETV_ARTIFACT_DIR", "artifacts/firetv"))
        artifact_root.mkdir(parents=True, exist_ok=True)
        artifact = artifact_root / f"codec-capability-{args.player}-{args.streaming}.json"
        artifact.write_text(json.dumps({
            "player": args.player,
            "streaming": args.streaming,
            "requestedDecoding": "hardware_preferred",
            "playback": started,
            "state": state,
            "codecCapabilities": profile,
        }, indent=2, sort_keys=True), encoding="utf-8")
        print(f"PASS: evidence {artifact}")
        return 0
    except Exception as exc:
        print(f"ANDROID CODEC CAPABILITIES: FAIL: {exc}", file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
