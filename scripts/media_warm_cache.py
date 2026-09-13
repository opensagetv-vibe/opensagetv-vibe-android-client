#!/usr/bin/env python3
"""Short-lived, credential-free media warm-state cache for physical tests."""
from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any


def media_identity(*, server: str, port: int, video_name: str = "",
                   server_path: str = "", search_text: str = "") -> str:
    if server_path.strip():
        media = "path:" + server_path.strip()
    elif video_name.strip():
        media = "name:" + video_name.strip()
    else:
        media = "search:" + search_text.strip()
    return f"{server.strip().lower()}:{int(port)}|{media}"


def load_cache(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (FileNotFoundError, OSError, ValueError, TypeError):
        return {}


def recent_entry(path: Path, identity: str, ttl_seconds: float,
                 now: float | None = None) -> dict[str, Any] | None:
    current = time.time() if now is None else float(now)
    entry = load_cache(path).get(identity)
    if not isinstance(entry, dict):
        return None
    try:
        age = current - float(entry["verifiedAtEpochSeconds"])
    except (KeyError, TypeError, ValueError):
        return None
    if age < 0 or age > float(ttl_seconds):
        return None
    return {**entry, "ageSeconds": age}


def mark_recent(path: Path, identity: str, *, startup_ms: int,
                now: float | None = None) -> dict[str, Any]:
    current = time.time() if now is None else float(now)
    values = load_cache(path)
    entry = {
        "verifiedAtEpochSeconds": current,
        "startupMs": int(startup_ms),
    }
    values[identity] = entry
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(values, indent=2, sort_keys=True), encoding="utf-8")
    return entry
