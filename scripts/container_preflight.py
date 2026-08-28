#!/usr/bin/env python3
from __future__ import annotations

import importlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

PROJECT = Path(os.environ.get("SAGETV_WORKSPACE", "/workspace"))
WORKSPACE = Path(os.environ.get("SAGETV_WORKSPACE", "/workspace"))
DEV_SRC = Path(os.environ.get("SAGETV_DEV_SOURCE", str(WORKSPACE / "source/dev")))
EXISTING_SRC = Path(os.environ.get("SAGETV_EXISTING_SOURCE", str(WORKSPACE / "source/existing")))
CFG = Path(os.environ.get("SAGETV_MCP_CONFIG", str(WORKSPACE / "config/firetv.toml")))


def version(cmd: list[str]) -> str:
    cp = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
    return cp.stdout.strip().splitlines()[0] if cp.stdout.strip() else f"exit={cp.returncode}"


def main() -> int:
    failures: list[str] = []
    warnings: list[str] = []
    print("SageTV Docker development preflight")
    print(f"workspace: {WORKSPACE}")
    print(f"python:    {sys.version.split()[0]}")
    print(f"java dev:  {version(['java', '-version'])}")
    legacy_java = Path("/opt/java/jdk8/bin/java")
    print(f"java base: {version([str(legacy_java), '-version']) if legacy_java.exists() else 'MISSING'}")
    if not legacy_java.exists():
        failures.append("bundled JDK 8 for untouched baseline is missing")

    for sdk_component in [
        Path("/opt/android-sdk/platforms/android-29/android.jar"),
        Path("/opt/android-sdk/build-tools/29.0.2/aapt"),
        Path("/opt/android-sdk/platforms/android-36/android.jar"),
        Path("/opt/android-sdk/build-tools/36.0.0/aapt"),
    ]:
        print(f"sdk:       {sdk_component} {'OK' if sdk_component.exists() else 'MISSING'}")
        if not sdk_component.exists():
            failures.append(f"missing Android SDK component: {sdk_component}")

    for tool in ["git", "adb", "aapt", "sdkmanager", "python3", "keytool"]:
        p = shutil.which(tool)
        print(f"{tool:10s}: {p or 'MISSING'}")
        if not p:
            failures.append(f"missing tool: {tool}")

    try:
        mcp = importlib.import_module("mcp")
        print(f"mcp:        import OK ({getattr(mcp, '__version__', 'version not exposed')})")
    except Exception as exc:
        print(f"mcp:        FAIL: {exc}")
        failures.append("Python MCP SDK unavailable")

    print(f"dev source: {'READY' if (DEV_SRC / 'gradlew').exists() else 'not imported/bootstrapped'} -> {DEV_SRC}")
    print(f"baseline:   {'READY' if (EXISTING_SRC / 'gradlew').exists() else 'not imported/bootstrapped'} -> {EXISTING_SRC}")

    debug_keystore = Path.home() / ".android" / "debug.keystore"
    print(f"debug key:  {'READY' if debug_keystore.exists() else 'will be auto-created on build'} -> {debug_keystore}")
    if not (DEV_SRC / "gradlew").exists():
        warnings.append("Dev source is not ready; import the exact repo ZIP or run bootstrap")
    if not (EXISTING_SRC / "gradlew").exists():
        warnings.append("untouched baseline source is not ready")

    print(f"config:     {CFG} {'OK' if CFG.exists() else 'MISSING'}")
    if not CFG.exists():
        failures.append("Fire TV config missing; initialize workspace and edit /workspace/config/firetv.toml")
    else:
        try:
            os.environ["SAGETV_MCP_CONFIG"] = str(CFG)
            from sagetv_dev_mcp.config import load_config
            from sagetv_dev_mcp.adb import AdbClient

            c = load_config()
            print(f"target:     {c.device}")
            print(f"dev pkg:    {c.dev_package}")
            adb = AdbClient(serial=c.device, dev_package=c.dev_package, adb=c.adb, aapt=c.aapt)
            print(f"adb connect: {adb.connect()}")
            print("device info:")
            print(json.dumps(adb.device_info(), indent=2))
        except Exception as exc:
            print(f"Fire TV connectivity: FAIL: {exc}")
            failures.append("Fire TV ADB connectivity/authorization failed")

    if warnings:
        print("\nWARNINGS:")
        for item in warnings:
            print(f"- {item}")
    if failures:
        print("\nPRECHECK FAILED:")
        for item in failures:
            print(f"- {item}")
        return 1
    print("\nPASS: Docker toolchain, workspace, and Fire TV path are ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
