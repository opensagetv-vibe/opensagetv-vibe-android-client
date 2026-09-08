from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
from typing import Any

try:
    import tomllib
except ModuleNotFoundError:  # Python 3.10
    import tomli as tomllib

DEFAULT_DEV_PACKAGE = "opensagetv.vibe.miniclient.debug"
DEFAULT_CLIENT_ID = "44:45:56:30:30:31"
DEFAULT_SERVER_ADDRESS = "192.0.2.20"
SENSITIVE_KEY_PARTS = ("password", "username", "credential", "secret", "token", "key")


def config_path() -> Path | None:
    value = os.environ.get("SAGETV_MCP_CONFIG", "").strip()
    return Path(value).expanduser().resolve() if value else None


def load_raw_config(path: Path | None = None) -> dict[str, Any]:
    selected = path or config_path()
    if selected is None:
        return {}
    if not selected.is_file():
        raise RuntimeError(f"Test-environment TOML does not exist: {selected}")
    try:
        with selected.open("rb") as stream:
            data = tomllib.load(stream)
    except (OSError, tomllib.TOMLDecodeError) as exc:
        raise RuntimeError(f"Cannot read test-environment TOML {selected}: {exc}") from exc
    if not isinstance(data, dict):
        raise RuntimeError(f"Test-environment TOML root must be a table: {selected}")
    return data


def _table(data: dict[str, Any], *keys: str) -> dict[str, Any]:
    value: Any = data
    for key in keys:
        if not isinstance(value, dict):
            return {}
        value = value.get(key, {})
    return value if isinstance(value, dict) else {}


def _string(value: Any, default: str = "") -> str:
    return str(value).strip() if value is not None else default


@dataclass(frozen=True)
class TestEnvironment:
    data: dict[str, Any]
    path: Path | None = None

    @property
    def active_device(self) -> str:
        return _string(os.environ.get("SAGETV_TEST_DEVICE_ALIAS"), _string(self.data.get("active_device"), "default"))

    @property
    def active_server(self) -> str:
        return _string(os.environ.get("SAGETV_TEST_SERVER_ALIAS"), _string(self.data.get("active_server"), "default"))

    @staticmethod
    def _named_entry(entries: dict[str, Any], selected: str) -> dict[str, Any]:
        direct = entries.get(selected, {})
        if isinstance(direct, dict) and direct:
            return direct
        matches = [
            value for value in entries.values()
            if isinstance(value, dict) and _string(value.get("alias")) == selected
        ]
        return matches[0] if len(matches) == 1 else {}

    def device(self, name: str | None = None) -> dict[str, Any]:
        devices = _table(self.data, "devices")
        selected = name or self.active_device
        return self._named_entry(devices, selected)

    def device_serial(self, name: str | None = None) -> str:
        override = os.environ.get("SAGETV_ADB_SERIAL", "").strip()
        if override and name is None:
            return override
        value = _string(self.device(name).get("serial"))
        if value:
            return value
        # Schema-1 compatibility.
        return _string(self.data.get("device")) if name is None else ""

    def server(self, name: str | None = None) -> dict[str, Any]:
        servers = _table(self.data, "servers")
        selected = name or self.active_server
        return self._named_entry(servers, selected)

    def server_for_address(self, address: str) -> dict[str, Any]:
        wanted = _string(address).casefold()
        matches = [
            value for value in _table(self.data, "servers").values()
            if isinstance(value, dict) and _string(value.get("address")).casefold() == wanted
        ]
        return matches[0] if len(matches) == 1 else {}

    def server_address(self, name: str | None = None) -> str:
        override = os.environ.get("SAGETV_TEST_SERVER_ADDRESS", "").strip()
        if override and name is None:
            return override
        return _string(self.server(name).get("address"), DEFAULT_SERVER_ADDRESS)

    def fixture(self, name: str, default: str = "") -> str:
        return _string(_table(self.data, "fixtures").get(name), default)

    def test_default(self, name: str, default: Any = None) -> Any:
        return _table(self.data, "test_defaults").get(name, default)

    def identity(self, name: str, default: str = "") -> str:
        return _string(_table(self.data, "identities").get(name), default)

    def automated_client_id(self) -> str:
        return _string(
            self.device().get("client_id"),
            self.identity("automated_client_id", DEFAULT_CLIENT_ID),
        )

    @staticmethod
    def _smb_config_for_server(server: dict[str, Any]) -> dict[str, Any]:
        if any(key in server for key in ("smb_url", "smb_username", "smb_password", "smb_configuration_url", "smb_mappings")):
            return {
                "default_share": "media",
                "shares": {
                    "media": {
                        "url": server.get("smb_url", ""),
                        "username": server.get("smb_username", ""),
                        "password": server.get("smb_password", ""),
                        "configuration_url": server.get("smb_configuration_url", ""),
                    }
                },
                "mappings": server.get("smb_mappings", []),
            }
        server_smb = server.get("smb", {})
        if isinstance(server_smb, dict) and server_smb:
            return server_smb
        return {}

    def smb_config(self, server_name: str | None = None) -> dict[str, Any]:
        server_smb = self._smb_config_for_server(self.server(server_name))
        if server_smb:
            return server_smb
        # Schema-2 preview/schema-1 compatibility. New files keep SMB settings
        # with the server that owns the SageTV paths.
        return _table(self.data, "smb")

    def smb_share(self, name: str | None = None, server_name: str | None = None) -> dict[str, Any]:
        smb = self.smb_config(server_name)
        shares = smb.get("shares", {})
        if not isinstance(shares, dict):
            return {}
        selected = name or _string(smb.get("default_share"), "default")
        value = shares.get(selected, {})
        return value if isinstance(value, dict) else {}

    def smb_mappings(self, server_name: str | None = None) -> list[dict[str, Any]]:
        mappings = self.smb_config(server_name).get("mappings", [])
        return mappings if isinstance(mappings, list) else []

    def capture_value(self, name: str, default: Any = None) -> Any:
        return _table(self.data, "capture").get(name, default)

    def validate(self, *, require_device: bool = True) -> list[str]:
        errors: list[str] = []
        schema = self.data.get("schema", 1)
        if not isinstance(schema, int) or schema not in (1, 2):
            errors.append("schema must be 1 or 2")
        if require_device and not self.device_serial():
            errors.append(
                "select active_device and set devices.<name>.serial, or set legacy device/SAGETV_ADB_SERIAL"
            )
        if self.data.get("active_device") and not self.device():
            errors.append(f"active_device '{self.active_device}' is not present under [devices]")
        if self.data.get("active_server") and not self.server():
            errors.append(f"active_server '{self.active_server}' is not present under [servers]")
        for kind in ("devices", "servers"):
            entries = _table(self.data, kind)
            aliases = [_string(value.get("alias")) for value in entries.values() if isinstance(value, dict)]
            aliases = [alias for alias in aliases if alias]
            duplicates = sorted({alias for alias in aliases if aliases.count(alias) > 1})
            if duplicates:
                errors.append(f"{kind} aliases must be unique: {', '.join(duplicates)}")
        package = _string(self.data.get("dev_package"), DEFAULT_DEV_PACKAGE)
        if package.startswith("jvl.sage.miniclient"):
            errors.append("dev_package must not use the protected jvl.sage.miniclient namespace")
        channels = self.test_default("live_channels", [])
        if channels and not isinstance(channels, list):
            errors.append("test_defaults.live_channels must be an array")
        servers = _table(self.data, "servers")
        configs = [(name, self._smb_config_for_server(server)) for name, server in servers.items() if isinstance(server, dict)]
        if not configs:
            configs = [("legacy", _table(self.data, "smb"))]
        for server_name, smb in configs:
            mappings = smb.get("mappings", [])
            if mappings and not isinstance(mappings, list):
                errors.append(f"servers.{server_name}.smb_mappings must be an array of tables")
            elif isinstance(mappings, list):
                for index, mapping in enumerate(mappings):
                    if not isinstance(mapping, dict) or not _string(mapping.get("sage_prefix")) or not _string(mapping.get("smb_root")):
                        errors.append(f"servers.{server_name}.smb_mappings[{index}] requires sage_prefix and smb_root")
        return errors

    def redacted(self) -> dict[str, Any]:
        def clean(value: Any) -> Any:
            if isinstance(value, dict):
                return {
                    key: "<redacted>" if any(part in key.lower() for part in SENSITIVE_KEY_PARTS) else clean(item)
                    for key, item in value.items()
                }
            if isinstance(value, list):
                return [clean(item) for item in value]
            return value

        return clean(self.data)


def load_test_environment(path: Path | None = None) -> TestEnvironment:
    selected = path or config_path()
    return TestEnvironment(load_raw_config(selected), selected)


def default_server_address() -> str:
    return load_test_environment().server_address()


def default_server_value(name: str, default: Any = None) -> Any:
    return load_test_environment().server().get(name, default)


def configured_device_serial(name: str | None = None) -> str:
    return load_test_environment().device_serial(name)


def default_fixture(name: str, default: str = "") -> str:
    return load_test_environment().fixture(name, default)


def default_test_value(name: str, default: Any = None) -> Any:
    return load_test_environment().test_default(name, default)


def default_smb_value(name: str, default: str = "") -> str:
    return _string(load_test_environment().smb_share().get(name), default)


def default_smb_mappings() -> str:
    mappings = load_test_environment().smb_mappings()
    return "\n".join(
        f"{_string(mapping.get('sage_prefix'))} => {_string(mapping.get('smb_root'))}"
        for mapping in mappings
        if isinstance(mapping, dict)
    )


@dataclass(frozen=True)
class Config:
    device: str
    dev_package: str = DEFAULT_DEV_PACKAGE
    adb: str = "adb"
    artifact_dir: Path = Path("artifacts/firetv")
    aapt: str = ""


def load_config() -> Config:
    environment = load_test_environment()
    errors = environment.validate(require_device=True)
    if errors:
        location = str(environment.path) if environment.path else "SAGETV_MCP_CONFIG"
        raise RuntimeError(f"Invalid test environment {location}: " + "; ".join(errors))
    data = environment.data
    device = environment.device_serial()
    dev_package = os.environ.get("SAGETV_DEV_PACKAGE", _string(data.get("dev_package"), DEFAULT_DEV_PACKAGE))
    if dev_package != DEFAULT_DEV_PACKAGE and dev_package.startswith("jvl.sage.miniclient"):
        raise RuntimeError("Refusing production/upstream SageTV package namespace as MCP dev_package")
    artifact_dir = Path(os.environ.get("SAGETV_ARTIFACT_DIR", data.get("artifact_dir", "artifacts/firetv"))).expanduser().resolve()
    artifact_dir.mkdir(parents=True, exist_ok=True)
    return Config(
        device=device,
        dev_package=dev_package,
        adb=os.environ.get("SAGETV_ADB", _string(data.get("adb"), "adb")),
        artifact_dir=artifact_dir,
        aapt=os.environ.get("SAGETV_AAPT", _string(data.get("aapt"))),
    )
