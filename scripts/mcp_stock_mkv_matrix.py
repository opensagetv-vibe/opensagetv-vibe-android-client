#!/usr/bin/env python3
"""Run strict MKV playback gates against an unmodified SageTV server.

This is a small orchestrator around ``mcp_player_matrix.py``. Each configured
MediaFile name receives a fresh complete player/backend run and an independent
child report. Unlike the exploratory matrix, this command fails when playback
startup or any requested media operation does not recover.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any

from sagetv_dev_mcp.config import (
    default_fixture_list,
    default_server_address,
    default_server_value,
    load_test_environment,
)


MATRIX_SCRIPT = Path(__file__).with_name("mcp_player_matrix.py")
DEFAULT_PLAYERS = "exoplayer,media3,ijkplayer,gsyplayer"
DEFAULT_GSY_ENGINES = "auto,media3,legacy_exo,system"
DEFAULT_CHECKS = "absolute_seek,seek_forward,seek_backward,pause_resume"
PASS_STATUSES = frozenset(("RECOVERED", "SLOW_RECOVERY"))


def slug(value: str) -> str:
    cleaned = re.sub(r"[^a-z0-9]+", "-", value.casefold()).strip("-")
    return cleaned or "mkv"


def fixture_texts(command_line: list[str] | None) -> list[str]:
    values = command_line if command_line else default_fixture_list("mkv_searches")
    result: list[str] = []
    for value in values:
        cleaned = str(value).strip()
        if cleaned and cleaned not in result:
            result.append(cleaned)
    return result


def build_child_command(args: argparse.Namespace, text: str, report: Path) -> list[str]:
    return [
        sys.executable,
        str(MATRIX_SCRIPT),
        "--server", args.server,
        "--port", str(args.port),
        "--video-name", text,
        "--players", args.players,
        "--streaming", "pull",
        "--hardware-only",
        "--gsy-engines", args.gsy_engines,
        "--checks", args.checks,
        "--start-ms", str(args.start_ms),
        "--skip-forward-ms", str(args.skip_forward_ms),
        "--skip-backward-ms", str(args.skip_backward_ms),
        "--watchdog-ms", str(args.watchdog_ms),
        "--slow-recovery-ms", str(args.slow_recovery_ms),
        "--playback-timeout-s", str(args.playback_timeout_s),
        "--verify-playback-ms", str(args.verify_playback_ms),
        "--text-char-delay-ms", str(args.text_char_delay_ms),
        "--report", str(report),
    ]


def evaluate_report(report: dict[str, Any]) -> dict[str, Any]:
    issues: list[str] = []
    cases: dict[str, Any] = {}
    raw_cases = report.get("results", {})
    if not isinstance(raw_cases, dict) or not raw_cases:
        issues.append("matrix report contains no player cases")
        raw_cases = {}

    for case_id, raw_case in raw_cases.items():
        case = raw_case if isinstance(raw_case, dict) else {}
        startup = case.get("startup", {}) if isinstance(case.get("startup"), dict) else {}
        state = startup.get("state", {}) if isinstance(startup.get("state"), dict) else {}
        requested = case.get("requestedChecks", [])
        requested = requested if isinstance(requested, list) else []
        checks = case.get("checks", {}) if isinstance(case.get("checks"), dict) else {}
        startup_status = str(case.get("startupStatus", "MISSING"))
        check_statuses = {
            name: str(checks.get(name, {}).get("status", "MISSING"))
            if isinstance(checks.get(name), dict) else "MISSING"
            for name in requested
        }
        if startup_status != "RECOVERED":
            issues.append(f"{case_id}: startup={startup_status}")
        for name, status in check_statuses.items():
            if status not in PASS_STATUSES:
                issues.append(f"{case_id}/{name}: {status}")
        if not bool(case.get("executionComplete", False)):
            issues.append(f"{case_id}: execution incomplete")

        cases[str(case_id)] = {
            "startupStatus": startup_status,
            "checks": check_statuses,
            "topLevelPlayerClass": state.get("health_topLevelPlayerClass", ""),
            "effectiveBackendClass": state.get("health_backendClass", ""),
            "backendPlayerClass": state.get("health_backendPlayerClass", ""),
            "videoDecoder": state.get("health_videoDecoder", ""),
            "videoDecoderKind": state.get("health_videoDecoderKind", ""),
            "audioDecoder": state.get("health_audioDecoder", ""),
            "gsySystemFallbackCount": state.get("gsySystemFallbackCount", 0),
            "gsySystemFallbackReason": state.get("gsySystemFallbackReason", ""),
        }

    if int(report.get("infrastructureFailures", 0) or 0) != 0:
        issues.append(f"infrastructureFailures={report.get('infrastructureFailures')}")
    if int(report.get("startupMediaFailures", 0) or 0) != 0:
        issues.append(f"startupMediaFailures={report.get('startupMediaFailures')}")
    return {"passed": not issues, "issues": issues, "cases": cases}


def report_path(requested: str) -> Path:
    if requested:
        return Path(requested).expanduser().resolve()
    artifact_dir = Path(os.environ.get(
        "SAGETV_ARTIFACT_DIR",
        Path(__file__).resolve().parents[1] / "artifacts" / "firetv",
    ))
    return artifact_dir / f"{datetime.now().strftime('%Y%m%d_%H%M%S')}_stock_mkv_matrix.json"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Strict all-player hardware Pull MKV matrix on stock SageTV"
    )
    parser.add_argument("--server", default=default_server_address())
    parser.add_argument("--port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--text", action="append", dest="texts", help="MKV MediaFile name; repeat for multiple files")
    parser.add_argument("--players", default=DEFAULT_PLAYERS)
    parser.add_argument("--gsy-engines", default=DEFAULT_GSY_ENGINES)
    parser.add_argument("--checks", default=DEFAULT_CHECKS)
    parser.add_argument("--start-ms", type=int, default=60000)
    parser.add_argument("--skip-forward-ms", type=int, default=30000)
    parser.add_argument("--skip-backward-ms", type=int, default=10000)
    parser.add_argument("--watchdog-ms", type=int, default=45000)
    parser.add_argument("--slow-recovery-ms", type=int, default=5000)
    parser.add_argument("--playback-timeout-s", type=float, default=60.0)
    parser.add_argument("--verify-playback-ms", type=int, default=3000)
    parser.add_argument("--text-char-delay-ms", type=int, default=0)
    parser.add_argument("--report", default="")
    parser.add_argument("--list-cases", action="store_true")
    parser.add_argument("--allow-non-stock", action="store_true", help="Diagnostic override; stock is required by default")
    args = parser.parse_args()

    texts = fixture_texts(args.texts)
    if not texts:
        parser.error("provide --text at least once or configure fixtures.mkv_searches")

    environment = load_test_environment()
    server_config = environment.server_for_address(args.server)
    if not args.allow_non_stock and server_config.get("stock") is not True:
        parser.error(
            f"server {args.server} is not marked stock=true in the selected test configuration; "
            "use an unmodified server or the explicit diagnostic --allow-non-stock override"
        )

    destination = report_path(args.report)
    destination.parent.mkdir(parents=True, exist_ok=True)
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")

    if args.list_cases:
        print("STOCK MKV FIXTURES:")
        for text in texts:
            print(f"  - {text}")
        command = build_child_command(args, texts[0], destination.with_suffix(".preview.json"))
        command.extend(("--list-cases",))
        return subprocess.run(command, check=False).returncode

    fixture_results: list[dict[str, Any]] = []
    passed = True
    for index, text in enumerate(texts, 1):
        child = destination.parent / f"{run_id}_stock_mkv_{index:02d}_{slug(text)}.json"
        command = build_child_command(args, text, child)
        print(f"\n=== STOCK MKV {index}/{len(texts)}: {text} ===", flush=True)
        completed = subprocess.run(command, check=False)
        if completed.returncode != 0 or not child.is_file():
            evaluation = {
                "passed": False,
                "issues": [f"matrix process exit={completed.returncode}, reportExists={child.is_file()}"],
                "cases": {},
            }
        else:
            try:
                evaluation = evaluate_report(json.loads(child.read_text(encoding="utf-8")))
            except (OSError, json.JSONDecodeError) as exc:
                evaluation = {"passed": False, "issues": [f"invalid child report: {exc}"], "cases": {}}
        passed = passed and bool(evaluation["passed"])
        fixture_results.append({
            "searchText": text,
            "childReport": str(child),
            **evaluation,
        })

    aggregate = {
        "schema": 1,
        "suite": "stock_mkv_all_player_matrix",
        "server": args.server,
        "stockServerRequired": not args.allow_non_stock,
        "deviceSelection": os.environ.get("SAGETV_TEST_DEVICE_ALIAS", "configured-active-device"),
        "streaming": "pull",
        "decoding": "hardware",
        "players": args.players,
        "gsyEngines": args.gsy_engines,
        "checks": args.checks,
        "passed": passed,
        "fixtures": fixture_results,
    }
    destination.write_text(json.dumps(aggregate, indent=2, sort_keys=True), encoding="utf-8")
    print(f"\nSTOCK MKV MATRIX: {'PASS' if passed else 'FAIL'}")
    print(f"REPORT: {destination}")
    for fixture in fixture_results:
        print(f"  {fixture['searchText']}: {'PASS' if fixture['passed'] else 'FAIL'}")
        for issue in fixture["issues"]:
            print(f"    - {issue}")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
