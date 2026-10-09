from __future__ import annotations

import json
import os
import time
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from .config import load_test_environment
from .sagex_api import MediaMatch, SagexApiClient, SagexApiError


class CoreMcpApiError(SagexApiError):
    pass


class CoreMcpApiClient:
    """Control adapter for the stock-compatible Vibe Core MCP plugin.

    The Java plugin exposes a deliberately bounded HTTP bridge.  This adapter
    gives the existing commissioning MCP the same small method surface it used
    for Sagex. When the plugin is enabled it is authoritative: configuration,
    authentication, or health failures are surfaced and never hidden by a
    Sagex/Web fallback.
    """

    transport = "core_mcp_plugin"

    def __init__(self, base_url: str, token: str, timeout_s: float = 15.0,
                 legacy: SagexApiClient | None = None):
        self.base_url = base_url.rstrip("/")
        self.token = token.strip()
        self.timeout_s = max(0.5, float(timeout_s))
        self._legacy = legacy
        self.plugin_version = ""

    @classmethod
    def discover(cls, host: str) -> "CoreMcpApiClient | SagexApiClient":
        environment = load_test_environment()
        server = environment.server_for_address(host)
        env_base = os.environ.get("SAGETV_CORE_MCP_BASE", "").strip()
        env_token = os.environ.get("SAGETV_CORE_MCP_TOKEN", "").strip()
        enabled = bool(env_base and env_token) or server.get("core_mcp_enabled") is True
        if not enabled:
            return SagexApiClient.discover(host)
        base = env_base or str(server.get("core_mcp_base_url", "")).strip()
        token = env_token or str(server.get("core_mcp_token", "")).strip()
        if not base:
            base = f"http://{host}:8270"
        if not token:
            raise CoreMcpApiError(
                f"Core MCP is enabled for {host}, but no core_mcp_token is configured"
            )
        client = cls(base, token)
        health = client.health()
        client.plugin_version = str(health.get("version", ""))
        return client

    def _request(
        self,
        action: str,
        *,
        request_timeout_s: float | None = None,
        **parameters: Any,
    ) -> dict[str, Any]:
        values: dict[str, str] = {"action": action}
        for key, value in parameters.items():
            if value is None:
                continue
            values[key] = str(value).lower() if isinstance(value, bool) else str(value)
        request = Request(
            self.base_url + "/v1/control",
            data=urlencode(values).encode("utf-8"),
            headers={
                "Accept": "application/json",
                "Authorization": "Bearer " + self.token,
                "Content-Type": "application/x-www-form-urlencoded; charset=utf-8",
            },
            method="POST",
        )
        try:
            timeout_s = self.timeout_s if request_timeout_s is None else max(
                self.timeout_s, float(request_timeout_s)
            )
            with urlopen(request, timeout=timeout_s) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise CoreMcpApiError(f"HTTP {exc.code}: {detail[:1000]}") from exc
        except (URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
            raise CoreMcpApiError(str(exc)) from exc
        if not isinstance(payload, dict):
            raise CoreMcpApiError(f"Invalid Core MCP response: {payload!r}")
        if not payload.get("ok", False):
            raise CoreMcpApiError(str(payload.get("error", payload)))
        return payload

    def health(self) -> dict[str, Any]:
        request = Request(self.base_url + "/health", headers={"Accept": "application/json"})
        try:
            with urlopen(request, timeout=self.timeout_s) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
            raise CoreMcpApiError(str(exc)) from exc
        if not isinstance(payload, dict) or payload.get("status") != "ok":
            raise CoreMcpApiError(f"Unhealthy Core MCP response: {payload!r}")
        return payload

    def ui_context_names(self) -> list[str]:
        payload = self._request("ui.list")
        contexts = payload.get("contexts", [])
        return [str(value) for value in contexts] if isinstance(contexts, list) else []

    def plugin_caption_listener(self, enabled: bool | None = None,
                                expected: bool | None = None,
                                plugin_id: str = "sagetvffmpegpluginlinux") -> dict[str, Any]:
        """Bounded commissioning setting, never an arbitrary plugin/API proxy."""
        if enabled is None:
            return self._request("plugin.config_get", plugin_id=plugin_id,
                                 setting="caption_side_channel.enabled")
        if type(enabled) is not bool or type(expected) is not bool:
            raise ValueError("Boolean enabled and expected checkpoint required")
        return self._request("plugin.config_set", plugin_id=plugin_id,
                             setting="caption_side_channel.enabled", value=enabled,
                             expected=expected, confirm=True)

    def resolve_context(self, client_id: str) -> str:
        contexts = self.ui_context_names()
        normalized = "".join(ch for ch in client_id.lower() if ch.isalnum())
        exact = [c for c in contexts if "".join(ch for ch in c.lower() if ch.isalnum()) == normalized]
        if len(exact) == 1:
            return exact[0]
        suffix = [c for c in contexts if "".join(ch for ch in c.lower() if ch.isalnum()).endswith(normalized)]
        if len(suffix) == 1:
            return suffix[0]
        non_local = [c for c in contexts if c != "SAGETV_PROCESS_LOCAL_UI"]
        if len(non_local) == 1:
            return non_local[0]
        raise CoreMcpApiError(
            f"Unable to uniquely resolve MiniClient UI context for {client_id}; contexts={contexts}"
        )

    def _legacy_client(self) -> SagexApiClient:
        if self._legacy is None:
            parsed_host = self.base_url.split("://", 1)[-1].split("/", 1)[0].split(":", 1)[0]
            self._legacy = SagexApiClient.discover(parsed_host)
        return self._legacy

    def find_media(self, video_name: str, max_items: int = 5000,
                   page_size: int = 250) -> tuple[list[MediaMatch], str]:
        return self._legacy_client().find_media(video_name, max_items, page_size)

    def find_media_many(self, video_names: Iterable[str], max_items: int = 5000,
                        page_size: int = 250) -> dict[str, tuple[list[MediaMatch], str]]:
        return self._legacy_client().find_media_many(video_names, max_items, page_size)

    def resolve_exact_path(self, path: str) -> dict[str, Any]:
        # A cold Windows server may need to build the plugin's bounded media
        # path index before answering the first exact lookup.  Do not fall back
        # to title/UI search merely because that one deterministic operation
        # legitimately exceeds the ordinary control-call timeout.
        # A newly connected MiniClient can also overlap the server's UI/STV
        # initialization. The stock API can transiently reject that first
        # lookup with HTTP 400 even though the same exact path resolves moments
        # later. Retry only that bounded transient; all other failures remain
        # immediate and visible.
        for attempt in range(4):
            try:
                return self._request(
                    "media.resolve_exact_path", request_timeout_s=180.0, path=path
                )
            except CoreMcpApiError as exc:
                if "HTTP 400:" not in str(exc) or attempt >= 3:
                    raise
                time.sleep(0.5 * (attempt + 1))
        raise AssertionError("unreachable")

    def refresh_media_index(self, wait_until_done: bool = False) -> dict[str, Any]:
        # A stock library scan may legitimately exceed an ordinary control
        # request. Bound an explicitly blocking commissioning call separately;
        # never replay it after an ambiguous timeout. The default remains the
        # nonblocking public scan plus independently verified exact lookup.
        return self._request("library.scan", wait_until_done=wait_until_done,
                             request_timeout_s=120.0 if wait_until_done else None)

    def watch(self, context: str, media_file_id: int,
              from_beginning: bool = False) -> dict[str, Any]:
        # Bridge 0.1.4 can acknowledge ordinary Watch without querying a
        # decoder that is still replacing its media socket. Older installed
        # bridges require at least 1000 ms and may still append a synchronous
        # UI snapshot; keep their protocol valid until they can be upgraded.
        try:
            version = tuple(int(part) for part in self.plugin_version.split(".")[:3])
        except ValueError:
            version = ()
        nonblocking_watch = version >= (0, 1, 4)
        wait_ms = (15000 if from_beginning else 0) if nonblocking_watch else (
            60000 if from_beginning else 1000
        )
        return self._request(
            "media.watch", context=context, media_id=int(media_file_id),
            from_beginning=from_beginning, wait_ms=wait_ms,
            request_timeout_s=75.0 if not nonblocking_watch else 30.0,
        )

    def remote_command(self, context: str, command: str) -> dict[str, Any]:
        return self._request("ui.command", context=context, command=command)

    def seek(self, context: str, target_ms: int) -> dict[str, Any]:
        return self._request("media.seek", context=context, target_ms=int(target_ms))

    def media_control(
        self,
        context: str,
        operation: str,
        rate: float | None = None,
    ) -> dict[str, Any]:
        return self._request(
            "media.control", context=context, operation=operation, rate=rate
        )

    def tune_channel(self, context: str, channel: str) -> dict[str, Any]:
        return self._request("channel.tune", context=context, channel=channel)

    def ui_state(self, context: str) -> dict[str, Any]:
        return self._request("ui.state", context=context)

    def closed_caption_state(self, context: str) -> str:
        return str(self._request("captions.get", context=context).get("state", "")).strip()

    def set_closed_caption_state(self, context: str, state: str) -> dict[str, Any]:
        return self._request("captions.set", context=context, state=state)

    def current_media_file_id(self, context: str) -> int | None:
        media = self.ui_state(context).get("media")
        if isinstance(media, dict):
            raw = media.get("mediaFileId")
            try:
                return int(raw) if raw is not None else None
            except (TypeError, ValueError):
                return None
        return None

    def clear_watched(self, media_file_id: int) -> dict[str, Any]:
        return self._request("media.clear_watched", media_id=int(media_file_id), confirm=True)


def discover_sage_control(host: str) -> CoreMcpApiClient | SagexApiClient:
    return CoreMcpApiClient.discover(host)


def core_mcp_required(host: str) -> bool:
    """Return whether Core MCP is the authoritative control for ``host``.

    This intentionally mirrors :meth:`CoreMcpApiClient.discover` without
    exposing or validating credentials. Callers use it only to prevent a
    cached legacy control or UI-search fallback from masking a required Core
    MCP failure.
    """
    environment = load_test_environment()
    server = environment.server_for_address(host)
    env_base = os.environ.get("SAGETV_CORE_MCP_BASE", "").strip()
    env_token = os.environ.get("SAGETV_CORE_MCP_TOKEN", "").strip()
    return bool(env_base and env_token) or server.get("core_mcp_enabled") is True
