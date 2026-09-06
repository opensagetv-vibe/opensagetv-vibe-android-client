#!/usr/bin/env python3
"""No-false-pass physical commissioning matrix for SageTV Fixed/FFmpeg/MIM.

Each case owns a fresh MCP process and app session. Failures are recorded and
the matrix continues; the final process exits non-zero if any required
prerecorded or live gate fails. GSY System is always reordered last.
"""
from __future__ import annotations

import argparse
import json
import os
import shlex
import subprocess
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path

from mcp_config_values import (
    DECODING_SELECTIONS,
    add_fixed_encoding_args,
    fixed_config_from_args,
    validate_fixed_config,
)

PLAYERS = ("exoplayer", "media3", "ijkplayer", "gsyplayer")
GSY_ENGINES = ("auto", "media3", "legacy_exo", "system")
CAPTION_ENGINES = {("exoplayer", "auto"), ("media3", "auto"),
                   ("gsyplayer", "auto"), ("gsyplayer", "media3"),
                   ("gsyplayer", "legacy_exo")}


@dataclass(frozen=True)
class Case:
    player: str
    decoder: str
    gsy_engine: str = "auto"

    @property
    def case_id(self) -> str:
        suffix = f"__gsy_{self.gsy_engine}" if self.player == "gsyplayer" else ""
        return f"{self.player}__fixed__{self.decoder}{suffix}"

    @property
    def captions_required(self) -> bool:
        return (self.player, self.gsy_engine) in CAPTION_ENGINES


def csv_values(value: str, allowed: tuple[str, ...], label: str) -> list[str]:
    values = [item.strip().lower() for item in value.split(",") if item.strip()]
    if not values:
        raise ValueError(f"{label} must not be empty")
    invalid = [item for item in values if item not in allowed]
    if invalid:
        raise ValueError(f"unsupported {label}: {','.join(invalid)}")
    return list(dict.fromkeys(values))


def build_cases(players: list[str], decoders: list[str], engines: list[str]) -> list[Case]:
    cases = [
        Case(player, decoder, engine)
        for player in players
        for decoder in decoders
        for engine in (engines if player == "gsyplayer" else ["auto"])
    ]
    # Android System has a history of destabilizing the commissioned Fire TV.
    return sorted(cases, key=lambda case: case.player == "gsyplayer" and case.gsy_engine == "system")


def fixed_cli(config: dict) -> list[str]:
    args = [
        "--fixed-encoding-preference", str(config["fixed_encoding_preference"]),
        "--fixed-encoding-format", str(config["fixed_encoding_format"]),
        "--fixed-video-bitrate-kbps", str(config["fixed_video_bitrate_kbps"]),
        "--fixed-video-fps", str(config["fixed_video_fps"]),
        "--fixed-key-frame-interval", str(config["fixed_key_frame_interval"]),
        "--fixed-video-resolution", str(config["fixed_video_resolution"]),
        "--fixed-audio-codec", str(config["fixed_audio_codec"]),
        "--fixed-audio-bitrate-kbps", str(config["fixed_audio_bitrate_kbps"]),
        "--fixed-audio-channels", str(config["fixed_audio_channels"]),
        "--fixed-remuxing-preference", str(config["fixed_remuxing_preference"]),
        "--fixed-remuxing-format", str(config["fixed_remuxing_format"]),
    ]
    args.append("--fixed-use-b-frames" if config["fixed_use_b_frames"] else "--fixed-no-b-frames")
    return args


def run_logged(command: list[str], log_path: Path, timeout_s: int) -> dict:
    started = datetime.now(timezone.utc)
    started_epoch_ms = int(time.time() * 1000)
    try:
        completed = subprocess.run(
            command,
            cwd=Path(__file__).resolve().parent.parent,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout_s,
            check=False,
        )
        output = completed.stdout or ""
        return_code = completed.returncode
        timed_out = False
    except subprocess.TimeoutExpired as exc:
        output = (exc.stdout or "") if isinstance(exc.stdout, str) else (exc.stdout or b"").decode(errors="replace")
        output += f"\nMATRIX TIMEOUT after {timeout_s}s\n"
        return_code = 124
        timed_out = True
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_text(output, encoding="utf-8", errors="replace")
    return {
        "status": "PASS" if return_code == 0 else "FAIL",
        "returnCode": return_code,
        "timedOut": timed_out,
        "startedUtc": started.isoformat(),
        "startedEpochMs": started_epoch_ms,
        "finishedUtc": datetime.now(timezone.utc).isoformat(),
        "log": str(log_path),
    }


def query_mim_status(
    command_text: str,
    expected_backend: str,
    *,
    min_started_epoch_ms: int,
    expected_input: str = "",
    timeout_s: int = 15,
) -> dict:
    """Query the server-side MIM source of truth after a Fixed test stage."""
    if not command_text.strip():
        return {"status": "SKIPPED", "reason": "no --mim-status-command configured"}
    try:
        completed = subprocess.run(
            shlex.split(command_text),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout_s,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return {"status": "FAIL", "reason": "status command failed", "error": str(exc)}
    output = (completed.stdout or "").strip()
    if completed.returncode != 0:
        return {
            "status": "FAIL", "reason": "status command returned non-zero",
            "returnCode": completed.returncode, "stdout": output,
            "stderr": (completed.stderr or "").strip(),
        }
    try:
        payload = json.loads(output.splitlines()[-1])
    except (IndexError, json.JSONDecodeError) as exc:
        return {"status": "FAIL", "reason": "invalid MIM status JSON", "error": str(exc), "stdout": output}
    job = payload.get("lastTranscodeJob")
    if not isinstance(job, dict):
        return {"status": "FAIL", "reason": "MIM reported no transcode job", "payload": payload}
    actual_backend = str(job.get("backend", ""))
    expected_hardware = expected_backend != "software"
    job_started = int(job.get("startedEpochMs", 0) or 0)
    job_input = str(job.get("input", ""))
    active_jobs = payload.get("activeJobs")
    no_orphan_jobs = isinstance(active_jobs, list) and len(active_jobs) == 0
    stopped_cleanly = str(job.get("state", "")) == "stopped"
    fresh = job_started >= min_started_epoch_ms
    input_matches = not expected_input or job_input == expected_input
    passed = (
        actual_backend == expected_backend
        and bool(job.get("hardwareEncode")) == expected_hardware
        and no_orphan_jobs
        and stopped_cleanly
        and fresh
        and input_matches
    )
    if expected_hardware:
        encoder_prefix = {"qsv": "h264_qsv", "vaapi": "h264_vaapi", "nvenc": "h264_nvenc"}.get(expected_backend, "")
        if encoder_prefix:
            passed = passed and str(job.get("encoder", "")) == encoder_prefix
    return {
        "status": "PASS" if passed else "FAIL",
        "reason": "ok" if passed else "unexpected, stale, or unrelated MIM job",
        "expectedBackend": expected_backend,
        "minimumStartedEpochMs": min_started_epoch_ms,
        "expectedInput": expected_input,
        "freshJob": fresh,
        "inputMatches": input_matches,
        "noOrphanJobs": no_orphan_jobs,
        "stoppedCleanly": stopped_cleanly,
        "activeJobs": active_jobs,
        "job": job,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the complete Fixed FFmpeg/MIM Android commissioning matrix")
    parser.add_argument("--server-address", default="192.168.10.232")
    parser.add_argument("--server-port", type=int, default=31099)
    parser.add_argument("--server-path", default="/var/media/videos/VibeSeekTest-1080i-MPEG2-AC3-CC.ts")
    parser.add_argument("--players", default=",".join(PLAYERS))
    parser.add_argument("--decoding", default=",".join(DECODING_SELECTIONS))
    parser.add_argument("--gsy-engines", default=",".join(GSY_ENGINES))
    parser.add_argument("--case-id", default="", help="Comma-separated exact case IDs to run")
    parser.add_argument("--channels", default="2.1,5.1")
    parser.add_argument("--live-channel-changes", type=int, default=2)
    parser.add_argument("--skip-live", action="store_true")
    parser.add_argument("--repeated-starts", type=int, default=1)
    parser.add_argument("--recovery-timeout-ms", type=int, default=30000)
    parser.add_argument("--case-timeout-s", type=int, default=900)
    parser.add_argument("--server-gpu", choices=("intel", "amd", "nvidia", "software"), default="intel")
    parser.add_argument("--expected-mim-backend", choices=("qsv", "vaapi", "nvenc", "software"), default="",
                        help="Exact MIM backend required for PASS (defaults to the stable vendor mapping)")
    parser.add_argument("--mim-status-command", default=os.environ.get("SAGETV_MIM_STATUS_COMMAND", ""),
                        help="Command returning `ffmpeg --mim-status` JSON from the tested server")
    parser.add_argument("--require-mim-status", action="store_true",
                        help="Fail every Fixed stage if a MIM status command is unavailable")
    parser.add_argument("--caption-gate", choices=("auto", "required", "skip"), default="auto",
                        help="Caption verdict policy; use skip only for a fixture known to contain no non-empty cues")
    parser.add_argument("--report", default="")
    add_fixed_encoding_args(parser)
    args = parser.parse_args()

    try:
        players = csv_values(args.players, PLAYERS, "players")
        decoders = csv_values(args.decoding, DECODING_SELECTIONS, "decoding modes")
        engines = csv_values(args.gsy_engines, GSY_ENGINES, "GSY engines")
        fixed = fixed_config_from_args(args)
        validate_fixed_config(fixed)
        cases = build_cases(players, decoders, engines)
        requested = {item.strip() for item in args.case_id.split(",") if item.strip()}
        if requested:
            known = {case.case_id for case in cases}
            missing = sorted(requested - known)
            if missing:
                raise ValueError(f"unknown --case-id values: {','.join(missing)}")
            cases = [case for case in cases if case.case_id in requested]
        if not cases:
            raise ValueError("no cases selected")
        if not 0 <= args.repeated_starts <= 10:
            raise ValueError("--repeated-starts must be between 0 and 10")
        if not 0 <= args.live_channel_changes <= 10:
            raise ValueError("--live-channel-changes must be between 0 and 10")
    except ValueError as exc:
        parser.error(str(exc))

    root = Path(__file__).resolve().parent.parent
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    artifact_root = Path(os.environ.get("SAGETV_ARTIFACT_DIR", root / "artifacts" / "firetv")) / f"fixed-mim-{stamp}"
    fixed_args = fixed_cli(fixed)
    results: dict[str, dict] = {}
    failures = 0
    expected_mim_backend = args.expected_mim_backend or {
        "intel": "vaapi",
        "amd": "vaapi",
        "nvidia": "nvenc",
        "software": "software",
    }[args.server_gpu]

    print(f"FIXED/MIM MATRIX: {len(cases)} case(s); GSY System runs last", flush=True)
    print("FIXED CONFIG: " + json.dumps(fixed, sort_keys=True), flush=True)
    for index, case in enumerate(cases, 1):
        print(f"\n[{index}/{len(cases)}] {case.case_id}", flush=True)
        captions_required = args.caption_gate == "required" or (
            args.caption_gate == "auto" and case.captions_required
        )
        if captions_required:
            caption_gate = "REQUIRED"
        elif args.caption_gate == "skip":
            caption_gate = "SKIPPED_FIXTURE_HAS_NO_CUES"
        else:
            caption_gate = "SKIPPED_UNSUPPORTED"
        case_result = {"case": asdict(case), "captionGate": caption_gate}
        results[case.case_id] = case_result
        session_cmd = [
            sys.executable, str(root / "scripts" / "mcp_session_test.py"),
            "--server-address", args.server_address,
            "--server-port", str(args.server_port),
            "--no-save-server",
            "--player", case.player,
            "--streaming", "fixed",
            "--decoding", case.decoder,
            "--gsy-engine", case.gsy_engine,
            "--server-path", args.server_path,
            "--restart-from-beginning",
            "--run-seek-health", "--run-jump-health", "--run-pause-health",
            "--run-repeated-start", str(args.repeated_starts),
            "--run-stop-restart",
            "--recovery-timeout-ms", str(args.recovery_timeout_ms),
            "--exit", "stop",
            *fixed_args,
        ]
        if captions_required:
            session_cmd.append("--require-captions")
        prerecorded = run_logged(session_cmd, artifact_root / f"{case.case_id}__prerecorded.log", args.case_timeout_s)
        case_result["prerecorded"] = prerecorded
        prerecorded_status = query_mim_status(
            args.mim_status_command,
            expected_mim_backend,
            min_started_epoch_ms=int(prerecorded["startedEpochMs"]),
            expected_input=args.server_path,
        )
        case_result["prerecordedMimStatus"] = prerecorded_status
        print(f"  prerecorded: {prerecorded['status']} ({prerecorded['log']})", flush=True)
        if prerecorded["status"] != "PASS" or prerecorded_status["status"] == "FAIL" or (
                args.require_mim_status and prerecorded_status["status"] != "PASS"):
            failures += 1

        if args.skip_live:
            case_result["live"] = {"status": "SKIPPED", "reason": "--skip-live requested"}
        else:
            live_cmd = [
                sys.executable, str(root / "scripts" / "mcp_live_tv_test.py"),
                "--server-address", args.server_address,
                "--server-port", str(args.server_port),
                "--player", case.player,
                "--streaming", "fixed",
                "--decoding", "hardware_preferred" if case.decoder == "fallback" else case.decoder,
                "--gsy-engine", case.gsy_engine,
                "--channels", args.channels,
                "--channel-changes", str(args.live_channel_changes),
                *fixed_args,
            ]
            live = run_logged(live_cmd, artifact_root / f"{case.case_id}__live.log", args.case_timeout_s)
            case_result["live"] = live
            live_status = query_mim_status(
                args.mim_status_command,
                expected_mim_backend,
                min_started_epoch_ms=int(live["startedEpochMs"]),
            )
            case_result["liveMimStatus"] = live_status
            print(f"  live: {live['status']} ({live['log']})", flush=True)
            if live["status"] != "PASS" or live_status["status"] == "FAIL" or (
                    args.require_mim_status and live_status["status"] != "PASS"):
                failures += 1

    gpu_results = {
        vendor: ({"status": "TESTED", "role": "server FFmpeg/MIM hardware backend"}
                 if vendor == args.server_gpu else
                 {"status": "SKIPPED", "reason": f"no commissioned physical {vendor.upper()} device on this server"})
        for vendor in ("intel", "amd", "nvidia")
    }
    report = {
        "schema": 1,
        "suite": "fixed_ffmpeg_mim_android_commissioning",
        "startedUtc": stamp,
        "server": args.server_address,
        "serverPath": args.server_path,
        "fixedConfig": fixed,
        "captionGatePolicy": args.caption_gate,
        "channels": args.channels.split(","),
        "gsySystemOrdering": "last",
        "gpuVendors": gpu_results,
        "caseCount": len(cases),
        "requiredFailureCount": failures,
        "result": "PASS" if failures == 0 else "FAIL",
        "results": results,
    }
    report_path = Path(args.report) if args.report else artifact_root / "FIXED_MIM_MATRIX.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    print(f"\nFIXED/MIM MATRIX: {report['result']}; required failures={failures}; report={report_path}", flush=True)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
