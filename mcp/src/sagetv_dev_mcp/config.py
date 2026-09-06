from __future__ import annotations
from dataclasses import dataclass
import os
from pathlib import Path

try:
    import tomllib
except ModuleNotFoundError:  # Python 3.10
    import tomli as tomllib

DEFAULT_DEV_PACKAGE = "opensagetv.vibe.miniclient.debug"

@dataclass(frozen=True)
class Config:
    device: str
    dev_package: str = DEFAULT_DEV_PACKAGE
    adb: str = "adb"
    artifact_dir: Path = Path("artifacts/firetv")
    aapt: str = ""


def load_config() -> Config:
    path = os.environ.get("SAGETV_MCP_CONFIG", "")
    data: dict = {}
    if path:
        with open(path, "rb") as f:
            data = tomllib.load(f)
    device = os.environ.get("SAGETV_ADB_SERIAL", data.get("device", ""))
    if not device:
        raise RuntimeError("Set SAGETV_ADB_SERIAL or device= in SAGETV_MCP_CONFIG")
    dev_package = os.environ.get("SAGETV_DEV_PACKAGE", data.get("dev_package", DEFAULT_DEV_PACKAGE))
    if dev_package != DEFAULT_DEV_PACKAGE:
        # Custom dev IDs are possible, but block the known upstream namespace by default.
        if dev_package.startswith("jvl.sage.miniclient"):
            raise RuntimeError("Refusing production/upstream SageTV package namespace as MCP dev_package")
    artifact_dir = Path(os.environ.get("SAGETV_ARTIFACT_DIR", data.get("artifact_dir", "artifacts/firetv"))).expanduser().resolve()
    artifact_dir.mkdir(parents=True, exist_ok=True)
    return Config(
        device=device,
        dev_package=dev_package,
        adb=os.environ.get("SAGETV_ADB", data.get("adb", "adb")),
        artifact_dir=artifact_dir,
        aapt=os.environ.get("SAGETV_AAPT", data.get("aapt", "")),
    )
