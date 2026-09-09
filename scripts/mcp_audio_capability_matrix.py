#!/usr/bin/env python3
"""Physically characterize AC3/EAC3/DTS without equating playback with passthrough."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import os
from pathlib import Path
import sys

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


FIXTURES = {
    "AC3": "/var/media/OpenSageTV_Vibe_Tests/VibeAudioCapability/vibe-audio-ac3.ts",
    "EAC3": "/var/media/OpenSageTV_Vibe_Tests/VibeAudioCapability/vibe-audio-eac3.ts",
    "DTS": "/var/media/OpenSageTV_Vibe_Tests/VibeAudioCapability/vibe-audio-dts.ts",
}


def evidence_for(profile: dict, codec: str) -> dict[str, bool]:
    for item in str(profile.get("audioCodecEvidence", "")).split("|"):
        fields = item.split(",")
        if fields and fields[0].upper() == codec.upper():
            parsed: dict[str, bool] = {}
            for field in fields[1:]:
                key, _, value = field.partition("=")
                parsed[key] = value.lower() == "true"
            return parsed
    return {}


def clean_start(client: MCPProcess, args: argparse.Namespace) -> None:
    clean = call_dict(client, "dev_prepare_clean_start", {
        "wake": True,
        "graceful_timeout_s": 2.0,
    }, timeout=30.0)
    require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")
    call_dict(client, "dev_set_player_config", {
        "player": "media3",
        "streaming": "pull",
        "decoding": "hardware_preferred",
    })
    call_dict(client, "dev_connect_server", {
        "address": args.server_address,
        "port": args.server_port,
        "save": False,
    })
    wait_automation_ready(client)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the physical Android audio capability matrix")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    args = parser.parse_args()

    client = MCPProcess()
    results: dict[str, object] = {}
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        call_dict(client, "adb_connect", timeout=30.0)

        for codec, server_path in FIXTURES.items():
            clean_start(client, args)
            started = call_dict(client, "dev_play_server_path", {
                "server_path": server_path,
                "timeout_s": 35.0,
                "verify_ms": 2500,
                "restart_from_beginning": True,
            }, timeout=70.0)
            state = call_dict(client, "dev_player_state", timeout=30.0)
            profile = call_dict(client, "dev_codec_capabilities", timeout=30.0)
            codec_evidence = evidence_for(profile, codec)

            require(profile.get("audioPassthroughAdvertised") in (False, "false"),
                    f"{codec}: client must not advertise unproven passthrough: {profile}")
            require(profile.get("audioPassthroughState") == "not_inferred_from_mime",
                    f"{codec}: MIME-only passthrough inference returned: {profile}")

            if codec in ("AC3", "EAC3"):
                require(bool(started.get("passed")), f"{codec}: direct Pull playback failed: {started}")
                require(state.get("playbackSource") == "SAGETV_PULL",
                        f"{codec}: expected direct Pull, got {state.get('playbackSource')}")
                require(codec_evidence.get("playable") is True,
                        f"{codec}: device profile did not report playable: {codec_evidence}")
                require(str(state.get("health_audioMime", "")).lower() in (
                    "audio/ac3", "audio/eac3", "audio/e-ac3"),
                    f"{codec}: unexpected resolved audio MIME: {state.get('health_audioMime')}")
                print(f"PASS: {codec} direct Pull; device evidence={codec_evidence}")
            else:
                require(codec_evidence.get("playable") is False,
                        f"DTS must remain unsupported on this device: {codec_evidence}")
                require(state.get("playbackSource") != "SAGETV_PULL",
                        "DTS unexpectedly negotiated direct Pull despite unsupported device evidence")
                print("PASS: DTS excluded from direct Pull; SageTV selected fallback transport")

            results[codec] = {
                "serverPath": server_path,
                "playback": started,
                "state": state,
                "deviceEvidence": codec_evidence,
                "capabilities": profile,
            }

        artifact_root = Path(os.environ.get("SAGETV_ARTIFACT_DIR", "artifacts/firetv"))
        artifact_root.mkdir(parents=True, exist_ok=True)
        artifact = artifact_root / "audio-capability-media3-pull.json"
        artifact.write_text(json.dumps(results, indent=2, sort_keys=True), encoding="utf-8")
        print(f"PASS: physical audio evidence {artifact}")
        return 0
    except Exception as exc:
        print(f"ANDROID AUDIO CAPABILITY MATRIX: FAIL: {exc}", file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
