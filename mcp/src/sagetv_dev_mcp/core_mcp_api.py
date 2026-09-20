from __future__ import annotations

import json
import os
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
    for Sagex, while retaining Sagex/Web only as the title-search fallback.
    """

    transport = "core_mcp_plugin"

    def __init__(self, base_url: str, token: str, timeout_s: float = 15.0,
                 legacy: SagexApiClient | None = None):
        self.base_url = base_url.rstrip("/")
        self.token = token.strip()
        self.timeout_s = max(0.5, float(timeout_s))
        self._legacy = legacy

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
        client.health()
        return client

    def _request(self, action: str, **parameters: Any) -> dict[str, Any]:
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
            with urlopen(request, timeout=self.timeout_s) as response:
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
        return self._request("media.resolve_exact_path", path=path)

    def refresh_media_index(self, wait_until_done: bool = False) -> dict[str, Any]:
        return self._request("library.scan", wait_until_done=wait_until_done)

    def watch(self, context: str, media_file_id: int,
              from_beginning: bool = False) -> dict[str, Any]:
        return self._request(
            "media.watch", context=context, media_id=int(media_file_id),
            from_beginning=from_beginning,
        )

    def remote_command(self, context: str, command: str) -> dict[str, Any]:
        return self._request("ui.command", context=context, command=command)

    def seek(self, context: str, target_ms: int) -> dict[str, Any]:
        return self._request("media.seek", context=context, target_ms=int(target_ms))

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
