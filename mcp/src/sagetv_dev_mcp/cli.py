from __future__ import annotations

import argparse
import json
from pathlib import Path

from .adb import AdbClient
from .config import load_config


def client() -> tuple[AdbClient, object]:
    cfg = load_config()
    return AdbClient(serial=cfg.device, dev_package=cfg.dev_package, adb=cfg.adb, aapt=cfg.aapt), cfg


def connect(adb: AdbClient) -> None:
    print(adb.connect())


def main() -> int:
    ap = argparse.ArgumentParser(description="Safe Fire TV control for the SageTV Dev development app")
    sub = ap.add_subparsers(dest="command", required=True)
    sub.add_parser("connect")
    p_install = sub.add_parser("install")
    p_install.add_argument("apk", nargs="?", default="")
    sub.add_parser("launch")
    sub.add_parser("stop")
    sub.add_parser("uninstall")
    sub.add_parser("device-info")
    p_log = sub.add_parser("logcat")
    p_log.add_argument("--lines", type=int, default=800)
    p_log.add_argument("--pattern", default="")
    args = ap.parse_args()

    adb, cfg = client()

    if args.command == "connect":
        connect(adb)
        print(adb.devices())
        print(json.dumps(adb.device_info(), indent=2))
        return 0

    # Every device operation reconnects because docker-compose commands normally use
    # short-lived containers. The persistent /root/.android volume keeps the ADB RSA key.
    connect(adb)

    if args.command == "install":
        apk = (Path(args.apk).expanduser().resolve() if args.apk else cfg.artifact_dir / "SageTV-MiniClient-Dev-debug.apk")
        detected = adb.detect_apk_package(apk)
        print(f"Verified APK package: {detected}")
        print(adb.install_dev_apk(apk))
    elif args.command == "launch":
        print(adb.launch())
    elif args.command == "stop":
        print(adb.force_stop())
    elif args.command == "uninstall":
        print(adb.uninstall())
    elif args.command == "device-info":
        print(json.dumps(adb.device_info(), indent=2))
    elif args.command == "logcat":
        print(adb.logcat_tail(args.lines, args.pattern))
    else:
        raise AssertionError(args.command)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
