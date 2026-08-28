#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from datetime import datetime
from pathlib import Path

from sagetv_dev_mcp.adb import AdbClient
from sagetv_dev_mcp.config import load_config


def make_client():
    cfg = load_config()
    return AdbClient(serial=cfg.device, dev_package=cfg.dev_package, adb=cfg.adb, aapt=cfg.aapt), cfg


def safe_text(fn):
    try:
        return fn()
    except Exception as exc:
        return f"ERROR: {type(exc).__name__}: {exc}\n"


def main() -> int:
    label = sys.argv[1] if len(sys.argv) > 1 else "player"
    adb, cfg = make_client()
    print(adb.connect())

    if label.lower() == "clear":
        adb.clear_logcat()
        print("Player diagnostic logcat cleared. Reproduce the problem, then run: ./dev.sh player-diag <label>")
        return 0

    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    safe_label = re.sub(r"[^A-Za-z0-9_.-]+", "_", label).strip("_") or "player"
    base = cfg.artifact_dir / f"{stamp}_{safe_label}"

    outputs = {
        "logcat": Path(str(base) + "_logcat.txt"),
        "crash": Path(str(base) + "_crash.txt"),
        "surface": Path(str(base) + "_surface.txt"),
        "media": Path(str(base) + "_media.txt"),
        "window": Path(str(base) + "_window.txt"),
        "package": Path(str(base) + "_package.txt"),
        "screenshot": Path(str(base) + "_screen.png"),
    }

    logcat = safe_text(lambda: adb.logcat_tail(10000))
    outputs["logcat"].write_text(logcat, encoding="utf-8")

    crash_rx = re.compile(
        r"FATAL EXCEPTION|AndroidRuntime|SIGSEGV|SIGABRT|Fatal signal|UnsatisfiedLinkError|"
        r"ExoPlaybackException|PlaybackException|MediaCodec|OMX\.|ACodec|CCodec|libffmpeg|ijk|sage|miniclient",
        re.IGNORECASE,
    )
    crash_lines = [line for line in logcat.splitlines() if crash_rx.search(line)]
    outputs["crash"].write_text("\n".join(crash_lines[-2500:]) + "\n", encoding="utf-8")

    surface = safe_text(lambda: adb.shell("dumpsys SurfaceFlinger --list", timeout=45))
    surface += "\n===== SurfaceFlinger dump =====\n"
    surface += safe_text(lambda: adb.shell("dumpsys SurfaceFlinger", timeout=60))
    outputs["surface"].write_text(surface, encoding="utf-8")

    outputs["media"].write_text(safe_text(adb.dumpsys_media_codec), encoding="utf-8")
    outputs["window"].write_text(safe_text(adb.focused_window), encoding="utf-8")
    outputs["package"].write_text(safe_text(adb.package_info), encoding="utf-8")
    try:
        adb.screenshot(outputs["screenshot"])
    except Exception as exc:
        outputs["screenshot"].with_suffix(".error.txt").write_text(str(exc), encoding="utf-8")

    print("PLAYER DIAGNOSTICS SAVED")
    for key, path in outputs.items():
        if path.exists():
            print(f"{key}: {path}")
    print("\nCrash-focused tail:")
    if crash_lines:
        print("\n".join(crash_lines[-80:]))
    else:
        print("No crash/player/codec lines matched. Full logcat was still saved.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
