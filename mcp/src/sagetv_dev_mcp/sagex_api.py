from __future__ import annotations

from dataclasses import dataclass
import base64
import html
import json
import os
import re
from typing import Any, Iterable
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

from .config import load_test_environment


DEFAULT_PORTS = (8080, 8081, 80)


class SagexApiError(RuntimeError):
    pass


@dataclass(frozen=True)
class MediaMatch:
    media_file_id: int
    title: str

    def as_dict(self) -> dict[str, Any]:
        return {"mediaFileId": self.media_file_id, "title": self.title}


class SagexApiClient:
    """Small standard-library client for the Sagex Remote API used only by the MCP dev harness."""

    def __init__(self, base_url: str, username: str = "", password: str = "", timeout_s: float = 4.0):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.timeout_s = max(0.5, float(timeout_s))

    @staticmethod
    def candidate_bases(host: str) -> list[str]:
        explicit = os.environ.get("SAGETV_SAGEX_BASE", "").strip()
        if explicit:
            return [explicit.rstrip("/")]
        environment = load_test_environment()
        server = environment.server_for_address(host)
        configured = str(server.get("sagex_base_url", "")).strip()
        if configured:
            return [configured.rstrip("/")]
        ports_text = os.environ.get("SAGETV_SAGEX_PORTS", "").strip()
        ports: list[int] = []
        if ports_text:
            for part in ports_text.split(","):
                try:
                    port = int(part.strip())
                except ValueError:
                    continue
                if 1 <= port <= 65535:
                    ports.append(port)
        if not ports:
            ports = list(DEFAULT_PORTS)
        out: list[str] = []
        for port in ports:
            scheme = "http"
            suffix = "" if (scheme == "http" and port == 80) else f":{port}"
            out.append(f"{scheme}://{host}{suffix}/sagex/api")
        return out

    @classmethod
    def discover(cls, host: str) -> "SagexApiClient":
        server = load_test_environment().server_for_address(host)
        user = os.environ.get("SAGETV_SAGEX_USER", str(server.get("web_username", "")))
        password = os.environ.get("SAGETV_SAGEX_PASSWORD", str(server.get("web_password", "")))
        errors: list[str] = []
        for base in cls.candidate_bases(host):
            client = cls(base, user, password)
            try:
                client.call("GetUIContextNames")
                return client
            except Exception as exc:
                errors.append(f"{base}: {exc}")
        # Many otherwise stock SageTV installations have Nielm's historical
        # Web Interface but not the separate Sagex Remote API. Its WatchNow
        # command still executes the ordinary SageTV/STV Watch operation and
        # is therefore the right commissioning fallback for an unmodified
        # server. Playback remains on the MiniClient wire protocol.
        try:
            return SageWebApiClient.discover(host, user, password)
        except Exception as exc:
            errors.append(f"SageTV Web Interface: {exc}")
        raise SagexApiError(
            "Unable to discover Sagex Remote API or SageTV Web Interface on "
            "connected SageTV server. Set SAGETV_SAGEX_BASE or "
            "SAGETV_WEB_BASE for custom URLs. Tried: " + " | ".join(errors)
        )

    def _request(self, params: dict[str, Any]) -> Any:
        q = {k: str(v) for k, v in params.items() if v is not None}
        q.setdefault("encoder", "json")
        url = self.base_url + "?" + urlencode(q)
        request = Request(url, headers={"Accept": "application/json"})
        if self.username:
            token = base64.b64encode(f"{self.username}:{self.password}".encode("utf-8")).decode("ascii")
            request.add_header("Authorization", f"Basic {token}")
        try:
            with urlopen(request, timeout=self.timeout_s) as response:
                text = response.read().decode("utf-8", errors="replace")
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            raise SagexApiError(str(exc)) from exc
        try:
            payload = json.loads(text)
        except json.JSONDecodeError as exc:
            raise SagexApiError(f"Non-JSON response from {self.base_url}: {text[:300]!r}") from exc
        if isinstance(payload, dict) and payload.get("error"):
            raise SagexApiError(str(payload.get("error")))
        return payload

    def call(self, command: str, *args: Any, context: str = "", start: int | None = None,
             size: int | None = None, fields: Iterable[str] | None = None) -> Any:
        params: dict[str, Any] = {"c": command}
        for index, arg in enumerate(args, 1):
            params[str(index)] = arg
        if context:
            params["context"] = context
        if start is not None:
            params["start"] = int(start)
        if size is not None:
            params["size"] = int(size)
        if fields:
            params["filter"] = "|".join(fields)
        return self._request(params)

    @staticmethod
    def _walk(value: Any):
        yield value
        if isinstance(value, dict):
            for child in value.values():
                yield from SagexApiClient._walk(child)
        elif isinstance(value, list):
            for child in value:
                yield from SagexApiClient._walk(child)

    @staticmethod
    def _string_values(payload: Any) -> list[str]:
        out: list[str] = []
        for value in SagexApiClient._walk(payload):
            if isinstance(value, str) and value not in out:
                out.append(value)
        return out

    def ui_context_names(self) -> list[str]:
        payload = self.call("GetUIContextNames")
        return [v for v in self._string_values(payload) if v and v != "Result"]

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
        raise SagexApiError(f"Unable to uniquely resolve MiniClient UI context for {client_id}; contexts={contexts}")

    @staticmethod
    def _extract_media_matches(payload: Any) -> list[MediaMatch]:
        matches: list[MediaMatch] = []
        seen: set[int] = set()
        for node in SagexApiClient._walk(payload):
            if not isinstance(node, dict):
                continue
            lower = {str(k).lower(): v for k, v in node.items()}
            raw_id = lower.get("mediafileid")
            title = lower.get("mediatitle")
            if raw_id is None or title is None:
                continue
            try:
                media_id = int(raw_id)
            except (TypeError, ValueError):
                continue
            if media_id in seen:
                continue
            seen.add(media_id)
            matches.append(MediaMatch(media_id, str(title)))
        return matches

    def find_media(self, video_name: str, max_items: int = 5000, page_size: int = 250) -> tuple[list[MediaMatch], str]:
        wanted = video_name.strip()
        if not wanted:
            raise ValueError("video_name is required")
        collected: list[MediaMatch] = []
        start = 0
        while start < max_items:
            payload = self.call(
                "GetMediaFiles",
                start=start,
                size=min(page_size, max_items - start),
                fields=("MediaTitle", "MediaFileID"),
            )
            page = self._extract_media_matches(payload)
            if not page:
                break
            collected.extend(page)
            if len(page) < page_size:
                break
            start += page_size
        wanted_fold = wanted.casefold()
        exact = [m for m in collected if m.title.strip().casefold() == wanted_fold]
        if exact:
            return exact, "exact"
        contains = [m for m in collected if wanted_fold in m.title.casefold()]
        return contains, "contains"

    def watch(self, context: str, media_file_id: int) -> Any:
        try:
            return self.call("Watch", f"mediafile:{int(media_file_id)}", context=context)
        except SagexApiError as exc:
            # Older Sagex releases successfully dispatch Watch but cannot encode
            # SageTV's asynchronous Catbert task handle as JSON.  The response is
            # therefore an API serialization error even though playback has been
            # accepted.  Recognize only that exact compatibility failure; all real
            # authentication, lookup, transport, and SageTV errors still propagate.
            detail = str(exc)
            if "Cannot Serialize" in detail and "Catbert$AsyncTaskID" in detail:
                return {
                    "accepted": True,
                    "asynchronous": True,
                    "serializationCompatibility": "Catbert$AsyncTaskID",
                }
            raise

    def remote_command(self, context: str, command: str) -> Any:
        """Send a normal STV command through the context-aware Web Remote."""
        explicit = os.environ.get("SAGETV_WEB_BASE", "").strip()
        if explicit:
            web_base = explicit.rstrip("/")
            if web_base.endswith("/Home"):
                web_base = web_base[:-5]
        else:
            parsed = urlparse(self.base_url)
            web_base = f"{parsed.scheme}://{parsed.netloc}/sage"
        return SageWebApiClient(
            web_base, self.username, self.password, self.timeout_s
        ).remote_command(context, command)

    def seek(self, context: str, target_ms: int) -> Any:
        """Ask SageTV's VideoFrame to seek the active UI session.

        This is intentionally different from seeking the Android backend
        directly.  Server-owned Push/DVD sessions must reposition their reader
        and begin sending bytes for the new timeline coordinate.
        """
        target_ms = int(target_ms)
        if target_ms < 0:
            raise ValueError("target_ms must be >= 0")
        try:
            return self.call("Seek", target_ms, context=context)
        except SagexApiError as exc:
            detail = str(exc)
            if "Cannot Serialize" in detail and "Catbert$AsyncTaskID" in detail:
                return {
                    "accepted": True,
                    "asynchronous": True,
                    "serializationCompatibility": "Catbert$AsyncTaskID",
                }
            raise

    @staticmethod
    def _result_value(payload: Any) -> Any:
        if isinstance(payload, dict) and "Result" in payload:
            return payload["Result"]
        return payload

    def closed_caption_state(self, context: str) -> str:
        payload = self.call("GetMediaPlayerClosedCaptionState", context=context)
        return str(self._result_value(payload) or "").strip()

    def set_closed_caption_state(self, context: str, state: str) -> Any:
        requested = str(state or "").strip()
        if requested.casefold() == "off":
            requested = "Captions Off"
        elif requested.upper() in ("CC1", "CC2", "TEXT1", "TEXT2"):
            requested = requested.upper().replace("TEXT", "Text")
        else:
            raise ValueError("caption state must be Off, CC1, CC2, Text1, or Text2")
        return self.call("SetMediaPlayerClosedCaptionState", requested, context=context)

    def current_media_file_id(self, context: str) -> int | None:
        payload = self.call("GetCurrentMediaFile", context=context, fields=("MediaFileID", "MediaTitle"))
        matches = self._extract_media_matches(payload)
        if matches:
            return matches[0].media_file_id
        for node in self._walk(payload):
            if isinstance(node, int):
                return node
            if isinstance(node, str) and node.isdigit():
                return int(node)
        return None

    def clear_watched(self, media_file_id: int) -> Any:
        """Remove SageTV's complete watched/resume record for one MediaFile."""
        media_file_id = int(media_file_id)
        if media_file_id <= 0:
            raise ValueError("media_file_id must be > 0")
        return self.call("ClearWatched", f"mediafile:{media_file_id}")


class SageWebApiClient:
    """Compatibility adapter for Nielm's stock-era SageTV Web Interface 4.x."""

    def __init__(self, base_url: str, username: str = "", password: str = "",
                 timeout_s: float = 4.0):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.timeout_s = max(0.5, float(timeout_s))

    @classmethod
    def candidate_bases(cls, host: str) -> list[str]:
        explicit = os.environ.get("SAGETV_WEB_BASE", "").strip()
        if explicit:
            base = explicit.rstrip("/")
            if base.endswith("/Home"):
                base = base[:-5]
            return [base]
        server = load_test_environment().server_for_address(host)
        configured = str(server.get("web_base_url", "")).strip()
        if configured:
            base = configured.rstrip("/")
            if base.endswith("/Home"):
                base = base[:-5]
            return [base]
        ports_text = os.environ.get("SAGETV_SAGEX_PORTS", "").strip()
        ports = []
        for part in ports_text.split(",") if ports_text else DEFAULT_PORTS:
            try:
                port = int(part)
            except (TypeError, ValueError):
                continue
            suffix = "" if port == 80 else f":{port}"
            ports.append(f"http://{host}{suffix}/sage")
        return ports

    @classmethod
    def discover(cls, host: str, username: str = "", password: str = "") -> "SageWebApiClient":
        errors = []
        for base in cls.candidate_bases(host):
            client = cls(base, username, password)
            try:
                body = client._request("Home")
                if "SageTV Web Interface" not in body and "Sage Webserver" not in body:
                    raise SagexApiError("response is not the SageTV Web Interface")
                return client
            except Exception as exc:
                errors.append(f"{base}: {exc}")
        raise SagexApiError(" | ".join(errors))

    def _request(self, path: str, params: dict[str, Any] | None = None) -> str:
        url = f"{self.base_url}/{path.lstrip('/')}"
        if params:
            url += "?" + urlencode({k: str(v) for k, v in params.items() if v is not None})
        request = Request(url, headers={"Accept": "text/html,application/xhtml+xml"})
        if self.username:
            token = base64.b64encode(
                f"{self.username}:{self.password}".encode("utf-8")
            ).decode("ascii")
            request.add_header("Authorization", f"Basic {token}")
        try:
            with urlopen(request, timeout=self.timeout_s) as response:
                return response.read().decode("utf-8", errors="replace")
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            raise SagexApiError(str(exc)) from exc

    def ui_context_names(self) -> list[str]:
        body = self._request("Home")
        contexts = []
        for value in re.findall(r"ExtenderDetails\?context=([A-Za-z0-9_.:-]+)", body):
            if value not in contexts:
                contexts.append(value)
        return contexts

    def resolve_context(self, client_id: str) -> str:
        contexts = self.ui_context_names()
        normalized = "".join(ch for ch in client_id.lower() if ch.isalnum())
        exact = [c for c in contexts
                 if "".join(ch for ch in c.lower() if ch.isalnum()) == normalized]
        if len(exact) == 1:
            return exact[0]
        suffix = [c for c in contexts
                  if "".join(ch for ch in c.lower() if ch.isalnum()).endswith(normalized)]
        if len(suffix) == 1:
            return suffix[0]
        raise SagexApiError(
            f"Unable to uniquely resolve MiniClient UI context for {client_id}; contexts={contexts}"
        )

    @staticmethod
    def _plain_text(value: str) -> str:
        return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", html.unescape(value))).strip()

    def find_media(self, video_name: str, max_items: int = 5000,
                   page_size: int = 250) -> tuple[list[MediaMatch], str]:
        del max_items, page_size
        wanted = video_name.strip()
        if not wanted:
            raise ValueError("video_name is required")
        body = self._request("Search", {
            "SearchString": wanted,
            "searchType": "MediaFiles",
            "DVD": "on",
            "Video": "on",
            "Music": "on",
            "Picture": "on",
            "pagelen": 500,
        })
        ids = []
        for raw_id in re.findall(r"DetailedInfo\?MediaFileId=(\d+)", body):
            media_id = int(raw_id)
            if media_id not in ids:
                ids.append(media_id)
        matches = []
        for media_id in ids:
            detail = self._request("DetailedInfo", {"MediaFileId": media_id})
            found = re.search(
                r"<title>\s*Detailed Information for\s+(.*?)\s*</title>",
                detail, re.IGNORECASE | re.DOTALL,
            )
            title = self._plain_text(found.group(1)) if found else str(media_id)
            matches.append(MediaMatch(media_id, title))
        wanted_fold = wanted.casefold()
        exact = [m for m in matches if m.title.strip().casefold() == wanted_fold]
        if exact:
            return exact, "exact_web"
        return [m for m in matches if wanted_fold in m.title.casefold()], "contains_web"

    def watch(self, context: str, media_file_id: int) -> Any:
        self._request("MediaFileCommand", {
            "command": "WatchNow",
            "context": context,
            "MediaFileId": int(media_file_id),
            "returnto": "Home",
        })
        return {"accepted": True, "transport": "sage_web_watch_now"}

    def remote_command(self, context: str, command: str) -> Any:
        if not str(context or "").strip():
            raise ValueError("context is required")
        if not str(command or "").strip():
            raise ValueError("command is required")
        self._request("SageCommand", {
            "RetImage": "yes",
            "command": str(command).strip(),
            "context": str(context).strip(),
        })
        return {
            "accepted": True,
            "transport": "sage_web_remote",
            "command": str(command).strip(),
            "context": str(context).strip(),
        }

    def seek(self, context: str, target_ms: int) -> Any:
        raise SagexApiError(
            "Exact server seek is unavailable through the stock SageTV Web Interface"
        )

    def current_media_file_id(self, context: str) -> int | None:
        body = self._request("Home", {"xml": "currplaying", "context": context})
        found = re.search(r"MediaFileId[=\"']+(\d+)", body, re.IGNORECASE)
        return int(found.group(1)) if found else None

    def clear_watched(self, media_file_id: int) -> Any:
        """Use the stock Web Interface ClearWatched command for one MediaFile."""
        media_file_id = int(media_file_id)
        if media_file_id <= 0:
            raise ValueError("media_file_id must be > 0")
        self._request("MediaFileCommand", {
            "command": "ClearWatched",
            "MediaFileId": media_file_id,
            "returnto": "Home",
        })
        return {
            "accepted": True,
            "transport": "sage_web_clear_watched",
            "mediaFileId": media_file_id,
        }
