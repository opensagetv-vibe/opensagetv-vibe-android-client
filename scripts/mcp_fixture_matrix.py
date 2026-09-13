#!/usr/bin/env python3
"""Run every enabled TOML fixture for a named regression mode.

Fixture IDs label reports only. Selection is mode-driven, so adding a new
enabled ``[[fixtures.cases]]`` entry automatically adds it to the requested
mode without changing this script.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
from typing import Any

from sagetv_dev_mcp.config import (
    default_fixture_cases,
    default_server_address,
    default_server_value,
)


PLAYER_MATRIX = Path(__file__).with_name("mcp_player_matrix.py")


def selected_checks(case: dict[str, Any]) -> list[str]:
    modes = case.get("modes", {}) if isinstance(case.get("modes"), dict) else {}
    checks: list[str] = []
    if modes.get("seek") is True:
        checks.append("absolute_seek")
    if modes.get("skip") is True:
        checks.extend(("seek_forward", "seek_backward"))
    if modes.get("pause_resume") is True:
        checks.append("pause_resume")
    if modes.get("comskip") is True:
        checks.extend(("comskip_right", "comskip_left"))
    return checks


def expected_media_issues(case: dict[str, Any], report: dict[str, Any]) -> list[str]:
    issues: list[str] = []
    results = report.get("results", {})
    if not isinstance(results, dict):
        return ["child report has no results table"]
    states = []
    for value in results.values():
        if not isinstance(value, dict):
            continue
        startup = value.get("startup", {})
        if isinstance(startup, dict) and isinstance(startup.get("state"), dict):
            states.append(startup["state"])
    if not states:
        return ["child report has no successful startup state"]

    expected_video = str(case.get("expected_video_mime") or "")
    expected_audio = str(case.get("expected_audio_mime") or "")
    minimum_seconds = int(case.get("minimum_duration_seconds") or 0)
    if expected_video and not any(str(state.get("health_videoMime") or "") == expected_video for state in states):
        issues.append(f"expected video MIME {expected_video} was not observed")
    if expected_audio and not any(str(state.get("health_audioMime") or "") == expected_audio for state in states):
        issues.append(f"expected audio MIME {expected_audio} was not observed")
    if minimum_seconds and not any(int(state.get("health_durationMs") or 0) >= minimum_seconds * 1000 for state in states):
        issues.append(f"minimum duration {minimum_seconds}s was not observed")
    return issues


def main() -> int:
    parser = argparse.ArgumentParser(description="Run all enabled fixtures for one TOML test mode")
    parser.add_argument("--fixture-mode", required=True)
    parser.add_argument("--fixture-id", action="append", default=[])
    parser.add_argument("--server", default=default_server_address())
    parser.add_argument("--port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    parser.add_argument("--players", default="exoplayer,media3,ijkplayer,gsyplayer")
    parser.add_argument("--streaming", default="pull")
    parser.add_argument("--gsy-engines", default="auto,media3,legacy_exo,system")
    parser.add_argument("--checks", default="", help="Explicit player checks; otherwise fixture mode switches decide")
    parser.add_argument("--list-fixtures", action="store_true")
    parser.add_argument("--report", default="")
    args = parser.parse_args()

    fixtures = default_fixture_cases(mode=args.fixture_mode, enabled_only=True)
    wanted = {value.strip() for value in args.fixture_id if value.strip()}
    if wanted:
        fixtures = [case for case in fixtures if str(case.get("id") or "") in wanted]

    if args.list_fixtures:
        for case in fixtures:
            print(f"{case.get('id')} [{case.get('path_type')}] {case.get('path')}")
        print(f"TOTAL ENABLED FIXTURES: {len(fixtures)}")
        return 0

    artifact_root = Path(os.environ.get("SAGETV_ARTIFACT_DIR", "artifacts/firetv"))
    artifact_root.mkdir(parents=True, exist_ok=True)
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    destination = Path(args.report) if args.report else artifact_root / f"{run_id}_fixture_{args.fixture_mode}.json"
    rows: list[dict[str, Any]] = []
    passed = True

    for index, case in enumerate(fixtures, 1):
        case_id = str(case.get("id") or f"fixture-{index}")
        path_type = str(case.get("path_type") or "")
        media_path = str(case.get("path") or "")
        child = artifact_root / f"{run_id}_{args.fixture_mode}_{index:02d}_{case_id}.json"
        if path_type == "server_root":
            rows.append({
                "id": case_id,
                "status": "SKIPPED_SPECIALIZED_DIRECTORY_FIXTURE",
                "reason": "use mcp-hardware-codec-matrix for server_root fixture collections",
            })
            continue
        command = [
            sys.executable, str(PLAYER_MATRIX),
            "--server", args.server,
            "--port", str(args.port),
            "--media-selection-mode", str(default_server_value("media_selection_mode", "auto")),
            "--webserver-installed" if bool(default_server_value("webserver_installed", False)) else "--no-webserver-installed",
            "--players", args.players,
            "--streaming", args.streaming,
            "--hardware-only",
            "--gsy-engines", args.gsy_engines,
            "--report", str(child),
        ]
        command.extend(("--server-path", media_path) if path_type == "server_path" else ("--video-name", media_path))
        checks = [value.strip() for value in args.checks.split(",") if value.strip()] or selected_checks(case)
        if checks:
            command.extend(("--checks", ",".join(checks)))
        else:
            command.append("--startup-only")
        completed = subprocess.run(command, check=False)
        child_report = {}
        if child.is_file():
            try:
                child_report = json.loads(child.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                child_report = {}
        issues = expected_media_issues(case, child_report) if completed.returncode == 0 else [f"child exit={completed.returncode}"]
        status = "PASS" if not issues else "FAIL"
        passed = passed and not issues
        rows.append({
            "id": case_id,
            "status": status,
            "pathType": path_type,
            "path": media_path,
            "enabledModes": sorted(name for name, enabled in case.get("modes", {}).items() if enabled is True),
            "checks": checks,
            "expected": {key: value for key, value in case.items() if key.startswith("expected_") or key == "minimum_duration_seconds"},
            "issues": issues,
            "childReport": str(child),
        })

    status = "PASS" if passed else "FAIL"
    if not fixtures:
        status = "SKIPPED_NO_ENABLED_FIXTURES"
    report = {
        "schema": 1,
        "suite": "configured_fixture_matrix",
        "status": status,
        "passed": passed,
        "fixtureMode": args.fixture_mode,
        "server": args.server,
        "fixtures": rows,
    }
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")
    print(f"{status}: {destination}")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
