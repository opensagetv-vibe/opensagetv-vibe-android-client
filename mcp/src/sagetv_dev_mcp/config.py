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
        fixtures = _table(self.data, "fixtures")
        direct = _string(fixtures.get(name))
        if direct:
            return direct
        legacy_case_ids = {
            "seek_server_path": "seek_caption",
            "caption_server_path": "seek_caption",
            "dvd_server_path": "authored_dvd",
            "dvd_motion_server_path": "dvd_motion",
            "prerecorded_search": "long_ota_mpeg2",
            "long_ota_mpeg2_search": "long_ota_mpeg2",
            "hardware_codec_server_root": "hardware_codec_matrix",
            "uk_taskmaster_search": "uk_taskmaster",
            "uk_breakfast_search": "uk_breakfast",
            "uk_classic_holby_search": "uk_classic_holby",
        }
        case = self.fixture_case(legacy_case_ids.get(name, name))
        return _string(case.get("path"), default)

    def fixture_list(self, name: str, default: list[str] | None = None) -> list[str]:
        value = _table(self.data, "fixtures").get(name)
        if value is None:
            if name == "mkv_searches":
                values = [
                    _string(case.get("path"))
                    for case in self.fixture_cases(mode="stock_mkv", enabled_only=True)
                    if _string(case.get("path_type")) == "search"
                ]
                if values:
                    return values
            return list(default or [])
        if isinstance(value, list):
            return [item for item in (_string(item) for item in value) if item]
        single = _string(value)
        return [single] if single else list(default or [])

    def fixture_cases(self, *, mode: str | None = None,
                      enabled_only: bool = False) -> list[dict[str, Any]]:
        raw = _table(self.data, "fixtures").get("cases", [])
        if not isinstance(raw, list):
            return []
        result: list[dict[str, Any]] = []
        for value in raw:
            if not isinstance(value, dict):
                continue
            if enabled_only and value.get("enabled") is not True:
                continue
            modes = value.get("modes", {})
            if mode is not None and (
                not isinstance(modes, dict) or modes.get(mode) is not True
            ):
                continue
            result.append(value)
        return result

    def fixture_case(self, name: str) -> dict[str, Any]:
        matches = [
            value for value in self.fixture_cases()
            if _string(value.get("id")) == name
        ]
        return matches[0] if len(matches) == 1 else {}

    def fixture_enabled(self, name: str, default: bool = True,
                        mode: str | None = None) -> bool:
        """Return whether a commissioned regression case may run.

        Missing switches remain enabled for compatibility with schema-1 and
        older schema-2 files. Values must be real TOML booleans; ``validate``
        reports strings such as ``"false"`` instead of treating them as true.
        """
        case = self.fixture_case(name)
        if case:
            if case.get("enabled") is not True:
                return False
            if mode is None:
                return True
            modes = case.get("modes", {})
            return isinstance(modes, dict) and modes.get(mode) is True
        value = _table(self.data, "fixture_enabled").get(name)
        return value if isinstance(value, bool) else default

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
        for device_name, device in _table(self.data, "devices").items():
            if not isinstance(device, dict):
                continue
            stv_by_server = device.get("stv_by_server", {})
            if stv_by_server and (
                not isinstance(stv_by_server, dict)
                or not all(isinstance(value, str) and value.strip()
                           for value in stv_by_server.values())
            ):
                errors.append(
                    f"devices.{device_name}.stv_by_server must map server names to non-empty STV names"
                )
        package = _string(self.data.get("dev_package"), DEFAULT_DEV_PACKAGE)
        if package.startswith("jvl.sage.miniclient"):
            errors.append("dev_package must not use the protected jvl.sage.miniclient namespace")
        channels = self.test_default("live_channels", [])
        if channels and not isinstance(channels, list):
            errors.append("test_defaults.live_channels must be an array")
        mkv_searches = _table(self.data, "fixtures").get("mkv_searches", [])
        if mkv_searches and (
            not isinstance(mkv_searches, list)
            or not all(isinstance(item, str) and item.strip() for item in mkv_searches)
        ):
            errors.append("fixtures.mkv_searches must be an array of non-empty search strings")
        fixture_enabled = _table(self.data, "fixture_enabled")
        for name, enabled in fixture_enabled.items():
            if not isinstance(enabled, bool):
                errors.append(f"fixture_enabled.{name} must be true or false")
        raw_cases = _table(self.data, "fixtures").get("cases", [])
        if raw_cases and not isinstance(raw_cases, list):
            errors.append("fixtures.cases must be an array of tables")
        elif isinstance(raw_cases, list):
            case_ids: list[str] = []
            for index, case in enumerate(raw_cases):
                prefix = f"fixtures.cases[{index}]"
                if not isinstance(case, dict):
                    errors.append(f"{prefix} must be a table")
                    continue
                case_id = _string(case.get("id"))
                if not case_id:
                    errors.append(f"{prefix}.id must be a non-empty string")
                else:
                    case_ids.append(case_id)
                if not isinstance(case.get("enabled"), bool):
                    errors.append(f"{prefix}.enabled must be true or false")
                if _string(case.get("path_type")) not in ("server_path", "server_root", "search"):
                    errors.append(f"{prefix}.path_type must be server_path, server_root, or search")
                if not _string(case.get("path")):
                    errors.append(f"{prefix}.path must be a non-empty string")
                for expected_name in (
                    "expected_container", "expected_video_mime",
                    "expected_audio_mime", "expected_subtitle",
                ):
                    if expected_name in case and not _string(case.get(expected_name)):
                        errors.append(f"{prefix}.{expected_name} must be a non-empty string")
                minimum_duration = case.get("minimum_duration_seconds")
                if (minimum_duration is not None
                        and (not isinstance(minimum_duration, int)
                             or isinstance(minimum_duration, bool)
                             or minimum_duration < 0)):
                    errors.append(
                        f"{prefix}.minimum_duration_seconds must be a non-negative integer"
                    )
                modes = case.get("modes")
                if not isinstance(modes, dict) or not modes:
                    errors.append(f"{prefix}.modes must be a non-empty boolean table")
                elif any(not isinstance(enabled, bool) for enabled in modes.values()):
                    errors.append(f"{prefix}.modes values must be true or false")
            duplicate_ids = sorted({value for value in case_ids if case_ids.count(value) > 1})
            if duplicate_ids:
                errors.append(f"fixtures.cases ids must be unique: {', '.join(duplicate_ids)}")
        servers = _table(self.data, "servers")
        for server_name, server in servers.items():
            if not isinstance(server, dict):
                continue
            selection_mode = _string(server.get("media_selection_mode"), "auto")
            if selection_mode not in ("auto", "stock_web", "vibe_exact_path"):
                errors.append(
                    f"servers.{server_name}.media_selection_mode must be auto, "
                    "stock_web, or vibe_exact_path"
                )
            if "webserver_installed" in server and not isinstance(
                server.get("webserver_installed"), bool
            ):
                errors.append(
                    f"servers.{server_name}.webserver_installed must be true or false"
                )
            if (selection_mode == "stock_web"
                    and server.get("webserver_installed") is False):
                errors.append(
                    f"servers.{server_name}.stock_web requires webserver_installed=true"
                )
            server_type = _string(server.get("server_type"), "custom")
            if server_type not in ("stock", "vibe", "custom"):
                errors.append(
                    f"servers.{server_name}.server_type must be stock, vibe, or custom"
                )
            web_control_mode = _string(server.get("web_control_mode"), "auto")
            if web_control_mode not in ("auto", "sagex", "web_interface", "none"):
                errors.append(
                    f"servers.{server_name}.web_control_mode must be auto, sagex, "
                    "web_interface, or none"
                )
            if (web_control_mode != "none"
                    and server.get("webserver_installed") is False):
                errors.append(
                    f"servers.{server_name}.{web_control_mode} requires webserver_installed=true"
                )
            for extension in ("vibe_sage_jar", "vibe_mim", "vibe_ffmpeg"):
                if extension in server and not isinstance(server.get(extension), bool):
                    errors.append(f"servers.{server_name}.{extension} must be true or false")
            if "core_mcp_enabled" in server and not isinstance(
                server.get("core_mcp_enabled"), bool
            ):
                errors.append(f"servers.{server_name}.core_mcp_enabled must be true or false")
            if server.get("core_mcp_enabled") is True:
                if not _string(server.get("core_mcp_base_url")):
                    errors.append(f"servers.{server_name}.core_mcp_base_url is required")
                if not _string(server.get("core_mcp_token")):
                    errors.append(f"servers.{server_name}.core_mcp_token is required")
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


def default_fixture_list(name: str, default: list[str] | None = None) -> list[str]:
    return load_test_environment().fixture_list(name, default)


def default_fixture_enabled(name: str, default: bool = True,
                            mode: str | None = None) -> bool:
    return load_test_environment().fixture_enabled(name, default, mode)


def default_fixture_cases(*, mode: str | None = None,
                          enabled_only: bool = False) -> list[dict[str, Any]]:
    return load_test_environment().fixture_cases(mode=mode, enabled_only=enabled_only)


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
