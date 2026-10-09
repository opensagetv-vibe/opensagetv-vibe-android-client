#!/usr/bin/env python3
"""Keep one commissioned Android device awake for a bounded test session.

The checkpoint is stored outside the APK and keyed by ADB serial. A later
``begin`` first restores an interrupted session, so a killed host process does
not turn the test override into a permanent user setting.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from typing import Any

from sagetv_dev_mcp.config import configured_device_serial


SETTINGS = (
    ("global", "stay_on_while_plugged_in", "7"),
    ("system", "screen_off_timeout", "2147483647"),
)


def state_path(serial: str) -> Path:
    workspace = Path(os.environ.get("SAGETV_WORKSPACE", Path(__file__).resolve().parents[1]))
    identity = hashlib.sha256(serial.encode("utf-8")).hexdigest()[:16]
    return workspace / "artifacts" / "firetv" / "test-session-state" / f"keep-awake-{identity}.json"


def adb(serial: str, *arguments: str, check: bool = True) -> str:
    completed = subprocess.run(
        ["adb", "-s", serial, *arguments],
        check=False,
        capture_output=True,
        text=True,
    )
    if check and completed.returncode != 0:
        detail = (completed.stderr or completed.stdout).strip()
        raise RuntimeError(f"ADB command failed ({completed.returncode}): {detail}")
    return completed.stdout.strip()


def get_setting(serial: str, namespace: str, name: str) -> dict[str, Any]:
    value = adb(serial, "shell", "settings", "get", namespace, name)
    return {"namespace": namespace, "name": name, "present": value != "null", "value": value}


def write_checkpoint(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temporary.replace(path)


def restore(serial: str, path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"action": "restore", "serial": serial, "restored": False, "reason": "no_checkpoint"}
    checkpoint = json.loads(path.read_text(encoding="utf-8"))
    if checkpoint.get("serial") != serial:
        raise RuntimeError("keep-awake checkpoint serial does not match selected device")
    restored: list[dict[str, Any]] = []
    for item in checkpoint.get("settings", []):
        namespace = str(item["namespace"])
        name = str(item["name"])
        if bool(item.get("present")):
            adb(serial, "shell", "settings", "put", namespace, name, str(item.get("value", "")))
        else:
            adb(serial, "shell", "settings", "delete", namespace, name)
        current = get_setting(serial, namespace, name)
        expected = str(item.get("value", "")) if bool(item.get("present")) else "null"
        if current["value"] != expected:
            raise RuntimeError(
                f"failed to restore {namespace}.{name}: expected {expected!r}, got {current['value']!r}"
            )
        restored.append(current)
    path.unlink()
    return {"action": "restore", "serial": serial, "restored": True, "settings": restored}


def checkpoint_scope(path: Path) -> str:
    checkpoint = json.loads(path.read_text(encoding="utf-8"))
    # Version 1 checkpoints predate explicit ownership and were only created by
    # automated gates. Treat them as automated so the next gate repairs them.
    return str(checkpoint.get("scope") or "automated")


def begin(serial: str, path: Path, scope: str = "manual") -> dict[str, Any]:
    if path.exists():
        active_scope = checkpoint_scope(path)
        if active_scope == "manual" and scope == "automated":
            return {
                "action": "begin",
                "serial": serial,
                "scope": scope,
                "active": True,
                "borrowed": True,
                "ownerScope": active_scope,
                "recoveredInterruptedSession": False,
            }
        if active_scope == "manual" and scope == "manual":
            return {
                "action": "begin",
                "serial": serial,
                "scope": scope,
                "active": True,
                "borrowed": False,
                "alreadyActive": True,
                "ownerScope": active_scope,
                "recoveredInterruptedSession": False,
            }
        stale = restore(serial, path)
    else:
        stale = None
    checkpoint = {
        "version": 2,
        "serial": serial,
        "scope": scope,
        "settings": [get_setting(serial, namespace, name) for namespace, name, _ in SETTINGS],
    }
    # Persist the original values before making either device mutation.
    write_checkpoint(path, checkpoint)
    try:
        for namespace, name, value in SETTINGS:
            adb(serial, "shell", "settings", "put", namespace, name, value)
        adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb(serial, "shell", "wm", "dismiss-keyguard", check=False)
        applied = [get_setting(serial, namespace, name) for namespace, name, _ in SETTINGS]
        expected = {(namespace, name): value for namespace, name, value in SETTINGS}
        for item in applied:
            key = (str(item["namespace"]), str(item["name"]))
            if item["value"] != expected[key]:
                raise RuntimeError(f"keep-awake override was not applied to {key[0]}.{key[1]}")
    except BaseException:
        restore(serial, path)
        raise
    return {
        "action": "begin",
        "serial": serial,
        "scope": scope,
        "active": True,
        "borrowed": False,
        "ownerScope": scope,
        "recoveredInterruptedSession": bool(stale and stale.get("restored")),
        "settings": applied,
    }


def end(serial: str, path: Path, scope: str = "manual") -> dict[str, Any]:
    if not path.exists():
        return {"action": "end", "serial": serial, "scope": scope, "restored": False, "reason": "no_checkpoint"}
    active_scope = checkpoint_scope(path)
    if active_scope != scope:
        return {
            "action": "end",
            "serial": serial,
            "scope": scope,
            "restored": False,
            "borrowed": scope == "automated" and active_scope == "manual",
            "reason": "owned_by_other_scope",
            "ownerScope": active_scope,
        }
    result = restore(serial, path)
    result.update({"action": "end", "scope": scope, "borrowed": False, "ownerScope": active_scope})
    return result


def status(serial: str, path: Path) -> dict[str, Any]:
    return {
        "action": "status",
        "serial": serial,
        "active": path.exists(),
        "settings": [get_setting(serial, namespace, name) for namespace, name, _ in SETTINGS],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Manage a scoped Android test keep-awake session")
    parser.add_argument("action", choices=("begin", "end", "restore", "status"))
    parser.add_argument("--scope", choices=("manual", "automated"), default="manual")
    args = parser.parse_args()
    serial = configured_device_serial()
    if not serial:
        parser.error("no commissioned Android device is selected")
    path = state_path(serial)
    try:
        if args.action == "begin":
            result = begin(serial, path, args.scope)
        elif args.action == "end":
            result = end(serial, path, args.scope)
        elif args.action == "restore":
            result = restore(serial, path)
            result.update({"action": args.action, "scope": args.scope})
        else:
            result = status(serial, path)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
