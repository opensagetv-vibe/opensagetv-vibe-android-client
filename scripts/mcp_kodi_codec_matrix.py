#!/usr/bin/env python3
"""Run the generated Kodi-derived codec matrix on the commissioned Fire TV.

The harness deliberately requires a real hardware decoder for every positive
row.  Unsupported AV1/VC-1/DRM cases are not silently converted to software
passes.  The malformed row passes only when playback fails safely and the app
remains alive without a crash signature.
"""
from __future__ import annotations

from sagetv_dev_mcp.config import configured_device_serial, default_server_address, default_server_value

import argparse
import json
import os
from pathlib import Path
import sys
import time

from mcp_lifecycle_test import MCPProcess, call_dict, initialize, require, wait_automation_ready


CASES = (
    ("mpeg2-interlaced-bframes.ts", "video/mpeg2", 2500),
    ("mpeg4-part2-bframes.mp4", "video/mp4v-es", 2500),
    ("h263-baseline.3gp", "video/3gpp", 2500),
    ("h264-baseline-avcc.mp4", "video/avc", 2500),
    ("h264-high-bframes-annexb.ts", "video/avc", 2500),
    ("hevc-main-hvcc.mp4", "video/hevc", 2500),
    ("hevc-main10-hdr10.mkv", "video/hevc", 2500),
    ("vp8-profile0.mkv", "video/x-vnd.on2.vp8", 2500),
    ("vp9-profile0.mkv", "video/x-vnd.on2.vp9", 2500),
    ("vp9-profile2-10bit.mkv", "video/x-vnd.on2.vp9", 2500),
    # Wait across the segment boundary so one successful first frame cannot
    # falsely pass a decoder that fails to reconfigure at the second size.
    ("h264-resolution-switch-annexb.ts", "video/avc", 7500),
)


def validate_mpeg4_missing_pts_injection(plan_path: Path) -> dict:
    """Apply the Kodi PTS-missing rule to recorded extractor packet metadata.

    This intentionally does not corrupt the MP4 or rewrite production player
    timestamps.  The real MP4 is device-tested above; this controlled adapter
    proves that selected PTS values are removed while their independent DTS is
    retained and selected as the bounded fallback.
    """
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    require(plan.get("status") == "READY_FOR_CONTROLLED_EXTRACTOR_INJECTION",
            f"fault plan is not ready: {plan.get('status')}")
    packets = plan.get("packets") or []
    require(bool(packets), "fault plan has no selected packets")
    for packet in packets:
        require(packet.get("injectedPts") is None,
                f"PTS was not removed: {packet}")
        require(packet.get("sourceDts") is not None,
                f"DTS was not retained: {packet}")
        require(packet.get("sourcePts") != packet.get("sourceDts"),
                f"fixture does not exercise reordered PTS/DTS: {packet}")
        resolved = packet.get("sourceDts") if packet.get("injectedPts") is None \
                else packet.get("injectedPts")
        require(resolved == packet.get("expectedFallbackTimestamp"),
                f"DTS fallback mismatch: {packet}")
    return {
        "name": "mpeg4-part2-missing-pts",
        "status": "PASS_CONTROLLED_EXTRACTOR_INJECTION",
        "fixture": plan.get("fixture"),
        "injectedPacketCount": len(packets),
        "policy": plan.get("fallback"),
        "scope": (
            "Real MPEG-4 B-frame playback is hardware-tested separately; "
            "no production rewrite is enabled because current Media3 exposes "
            "one TrackOutput presentation timestamp rather than Kodi's PTS/DTS pair."
        ),
    }


def compact_state(state: dict) -> dict:
    keys = (
        "health_videoMime", "health_videoDecoder", "health_videoDecoderKind",
        "health_videoWidth", "health_videoHeight", "health_videoRendered",
        "health_videoDropped", "health_playerError", "health_errorState",
        "health_surfaceValid", "health_isPlaying", "mediaTimeMs",
    )
    return {key: state.get(key) for key in keys}


def ensure_idle(client: MCPProcess, args: argparse.Namespace) -> dict:
    """Stop the prior short fixture or rebuild only the MiniClient session."""
    state = call_dict(client, "dev_player_state", timeout=30.0)
    if not bool(state.get("playerActive")):
        return {"method": "already_idle"}
    call_dict(client, "dev_sage_command", {"command": "stop"}, timeout=30.0)
    deadline = time.monotonic() + 18.0
    while time.monotonic() < deadline:
        state = call_dict(client, "dev_player_state", timeout=30.0)
        if not bool(state.get("playerActive")):
            return {"method": "sage_stop"}
        time.sleep(0.25)

    # A completed short item can leave an STV/player teardown pending. Rebuild
    # the control session rather than attributing that race to the next codec.
    call_dict(client, "dev_exit_session", {"stop_app": False}, timeout=30.0)
    call_dict(client, "dev_connect_server", {
        "address": args.server_address,
        "port": args.server_port,
        "save": False,
    }, timeout=30.0)
    wait_automation_ready(client, timeout_s=60.0)
    state = call_dict(client, "dev_player_state", timeout=30.0)
    require(not bool(state.get("playerActive")),
            f"player remained active after bounded session rebuild: {compact_state(state)}")
    return {"method": "session_rebuild"}


def main() -> int:
    parser = argparse.ArgumentParser(description="Run generated codec fixtures with strict hardware evidence")
    parser.add_argument("--server-address", default=default_server_address())
    parser.add_argument("--server-port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument(
        "--server-root",
        default="/var/media/videos/OpenSageTV-Vibe-Kodi-Codec-Test",
    )
    parser.add_argument("--player", choices=("media3", "exoplayer"), default="media3")
    parser.add_argument("--streaming", choices=("pull", "push"), default="pull")
    parser.add_argument("--timeout-s", type=float, default=60.0)
    args = parser.parse_args()

    results: list[dict] = []
    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")
        connection = call_dict(client, "adb_connect", timeout=30.0)
        serial = str(connection.get("serial") or connection.get("device") or "")
        required_serial = configured_device_serial("non_pro") or configured_device_serial()
        require(serial.endswith(required_serial) or required_serial in json.dumps(connection),
                f"physical codec matrix is restricted to configured non-Pro device {required_serial}, got: {connection}")
        clean = call_dict(client, "dev_prepare_clean_start", {
            "wake": True, "graceful_timeout_s": 2.0,
        }, timeout=30.0)
        require(bool(clean.get("readyToLaunch")), f"clean start failed: {clean}")
        call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": args.streaming,
            "decoding": "hardware",
        }, timeout=30.0)
        call_dict(client, "dev_connect_server", {
            "address": args.server_address,
            "port": args.server_port,
            "save": False,
        }, timeout=30.0)
        wait_automation_ready(client, timeout_s=60.0)

        for name, expected_mime, verify_ms in CASES:
            path = args.server_root.rstrip("/") + "/" + name
            row = {"name": name, "serverPath": path, "expectedMime": expected_mime}
            try:
                row["priorCleanup"] = ensure_idle(client, args)
                started = call_dict(client, "dev_play_server_path", {
                    "server_path": path,
                    "timeout_s": args.timeout_s,
                    "verify_ms": verify_ms,
                    "restart_from_beginning": True,
                }, timeout=args.timeout_s + 40.0)
                require(bool(started.get("passed")), f"playback did not pass: {started.get('reason')}")
                state = call_dict(client, "dev_player_state", timeout=30.0)
                actual_mime = str(state.get("health_videoMime") or "")
                require(actual_mime == expected_mime,
                        f"expected MIME {expected_mime}, got {actual_mime or '<empty>'}")
                require(state.get("health_videoDecoderKind") == "hardware",
                        f"not hardware decoded: {state.get('health_videoDecoder')}")
                require(not state.get("health_playerError") and not bool(state.get("health_errorState")),
                        f"player error: {state.get('health_playerError')}")
                row.update({"status": "PASS", "state": compact_state(state)})
                print(f"PASS: {name}: {actual_mime} -> {state.get('health_videoDecoder')}")
            except Exception as exc:
                row.update({"status": "FAIL", "reason": str(exc)})
                print(f"FAIL: {name}: {exc}", file=sys.stderr)
            results.append(row)

        malformed_path = args.server_root.rstrip("/") + "/h264-truncated-start.ts"
        malformed = {"name": "h264-truncated-start.ts", "serverPath": malformed_path}
        try:
            malformed["priorCleanup"] = ensure_idle(client, args)
            started = call_dict(client, "dev_play_server_path", {
                "server_path": malformed_path,
                "timeout_s": 15.0,
                "verify_ms": 2000,
                "restart_from_beginning": True,
            }, timeout=55.0)
            crash = call_dict(client, "dev_crash_probe", timeout=30.0)
            require(not bool(crash.get("signatureDetected")), f"crash signature: {crash}")
            status = call_dict(client, "dev_app_status", timeout=30.0)
            require(bool(status.get("running")), f"app died on malformed fixture: {status}")
            malformed.update({
                "status": "PASS_SAFE_FAILURE_OR_RECOVERY",
                "playbackPassed": bool(started.get("passed")),
                "crash": crash,
            })
            print("PASS: malformed startup failed/recovered without process death")
        except Exception as exc:
            malformed.update({"status": "FAIL", "reason": str(exc)})
            print(f"FAIL: malformed containment: {exc}", file=sys.stderr)
        results.append(malformed)

        capabilities = call_dict(client, "dev_codec_capabilities", timeout=30.0)
        profiles = str(capabilities.get("codecProfiles") or "")
        fault_plan = Path(os.environ.get(
            "SAGETV_CODEC_FIXTURE_DIR", "artifacts/test-media/kodi-codec"
        )) / "mpeg4-part2-missing-pts.fault-plan.json"
        try:
            injection = validate_mpeg4_missing_pts_injection(fault_plan)
            results.append(injection)
            print("PASS: controlled MPEG-4 missing-PTS extractor injection")
        except Exception as exc:
            results.append({
                "name": "mpeg4-part2-missing-pts",
                "status": "FAIL",
                "reason": str(exc),
            })
            print(f"FAIL: MPEG-4 missing-PTS injection: {exc}", file=sys.stderr)

        results.extend([
            {
                "name": "av1-main8.mkv",
                "status": "SKIPPED_UNSUPPORTED_HARDWARE" if "video/av01,hw" not in profiles else "NOT_RUN",
                "reason": "AFTMM/API-25 advertises no AV1 hardware decoder",
            },
            {
                "name": "secure-decoder-drm",
                "status": "SKIPPED_AUTHORIZED_ASSET_REQUIRED",
                "reason": "clear FFmpeg media cannot validate a secure DRM session",
            },
        ])
        crash = call_dict(client, "dev_crash_probe", timeout=30.0)
        passed = all(str(row.get("status", "")).startswith("PASS") or
                     str(row.get("status", "")).startswith("SKIPPED") or
                     str(row.get("status", "")).startswith("PENDING")
                     for row in results) and not bool(crash.get("signatureDetected"))
        report = {
            "passed": passed,
            "deviceRestriction": required_serial,
            "player": args.player,
            "streaming": args.streaming,
            "decoding": "hardware",
            "capabilityDevice": capabilities.get("codecDevice"),
            "capabilityApi": capabilities.get("codecApi"),
            "results": results,
            "finalCrashProbe": crash,
        }
        artifact_root = Path(os.environ.get("SAGETV_ARTIFACT_DIR", "artifacts/firetv"))
        artifact_root.mkdir(parents=True, exist_ok=True)
        artifact = artifact_root / f"kodi-codec-matrix-{args.player}-{args.streaming}-aftmm.json"
        artifact.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
        print(f"{'PASS' if passed else 'FAIL'}: evidence {artifact}")
        return 0 if passed else 1
    except Exception as exc:
        print(f"KODI CODEC MATRIX: FAIL: {exc}", file=sys.stderr)
        return 1
    finally:
        try:
            call_dict(client, "dev_exit_session", {"stop_app": False}, timeout=30.0)
        except Exception:
            pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
