#!/usr/bin/env python3
"""End-to-end MCP stdio smoke test for the SageTV Dev Fire TV server.

This intentionally speaks JSON-RPC/MCP over stdio instead of calling the ADB helper
classes directly. It proves that a real MCP client can initialize the server,
discover tools, and call safe Fire TV tools without requiring Codex or Node.
"""
from __future__ import annotations

import json
import os
import select
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

PROTOCOL_VERSIONS = ("2025-06-18", "2025-03-26")
DEFAULT_TIMEOUT = 20.0


class MCPProcess:
    def __init__(self) -> None:
        env = os.environ.copy()
        self.proc = subprocess.Popen(
            [sys.executable, "-m", "sagetv_dev_mcp.server"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
            env=env,
        )
        assert self.proc.stdin is not None
        assert self.proc.stdout is not None
        assert self.proc.stderr is not None
        self._next_id = 1

    def send(self, message: dict[str, Any]) -> None:
        assert self.proc.stdin is not None
        self.proc.stdin.write(json.dumps(message, separators=(",", ":")) + "\n")
        self.proc.stdin.flush()

    def request(self, method: str, params: dict[str, Any] | None = None, timeout: float = DEFAULT_TIMEOUT) -> dict[str, Any]:
        request_id = self._next_id
        self._next_id += 1
        msg: dict[str, Any] = {"jsonrpc": "2.0", "id": request_id, "method": method}
        if params is not None:
            msg["params"] = params
        self.send(msg)
        return self._wait_for_id(request_id, timeout)

    def notify(self, method: str, params: dict[str, Any] | None = None) -> None:
        msg: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            msg["params"] = params
        self.send(msg)

    def _wait_for_id(self, request_id: int, timeout: float) -> dict[str, Any]:
        assert self.proc.stdout is not None
        deadline = time.monotonic() + timeout
        while True:
            if self.proc.poll() is not None:
                raise RuntimeError(self._failure_text("MCP server exited unexpectedly"))
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError(self._failure_text(f"Timed out waiting for MCP response id={request_id}"))
            ready, _, _ = select.select([self.proc.stdout], [], [], min(0.5, remaining))
            if not ready:
                continue
            line = self.proc.stdout.readline()
            if not line:
                continue
            try:
                msg = json.loads(line)
            except json.JSONDecodeError:
                # stdout must normally be protocol-only. Keep looking, but include the
                # unexpected line in an error if the request ultimately fails.
                continue
            if msg.get("id") == request_id:
                return msg

    def _failure_text(self, prefix: str) -> str:
        stderr = ""
        if self.proc.stderr is not None:
            try:
                ready, _, _ = select.select([self.proc.stderr], [], [], 0)
                if ready:
                    stderr = self.proc.stderr.read() or ""
            except Exception:
                pass
        return prefix + (f"\nMCP server stderr:\n{stderr.strip()}" if stderr.strip() else "")

    def close(self) -> None:
        try:
            if self.proc.stdin is not None and not self.proc.stdin.closed:
                self.proc.stdin.close()
        except Exception:
            pass
        try:
            self.proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(timeout=2)


def require_result(response: dict[str, Any], label: str) -> dict[str, Any]:
    if "error" in response:
        raise RuntimeError(f"{label} failed: {json.dumps(response['error'], indent=2)}")
    result = response.get("result")
    if not isinstance(result, dict):
        raise RuntimeError(f"{label} returned no result object: {response!r}")
    return result


def tool_call(client: MCPProcess, name: str, arguments: dict[str, Any] | None = None, timeout: float = DEFAULT_TIMEOUT) -> dict[str, Any]:
    response = client.request(
        "tools/call",
        {"name": name, "arguments": arguments or {}},
        timeout=timeout,
    )
    result = require_result(response, f"tool {name}")
    if result.get("isError"):
        raise RuntimeError(f"tool {name} reported isError=true: {json.dumps(result, indent=2)}")
    return result


def compact_tool_result(result: dict[str, Any]) -> str:
    if "structuredContent" in result:
        return json.dumps(result["structuredContent"], indent=2)
    content = result.get("content", [])
    texts = []
    if isinstance(content, list):
        for item in content:
            if isinstance(item, dict) and item.get("type") == "text":
                texts.append(str(item.get("text", "")))
    return "\n".join(texts).strip() or json.dumps(result, indent=2)


def initialize(client: MCPProcess) -> tuple[str, dict[str, Any]]:
    last_error: Exception | None = None
    for version in PROTOCOL_VERSIONS:
        try:
            response = client.request(
                "initialize",
                {
                    "protocolVersion": version,
                    "capabilities": {},
                    "clientInfo": {"name": "sagetv-dev-mcp-smoke", "version": "0.3.0"},
                },
            )
            if "error" in response:
                last_error = RuntimeError(json.dumps(response["error"]))
                continue
            result = require_result(response, "initialize")
            client.notify("notifications/initialized")
            negotiated = str(result.get("protocolVersion", version))
            return negotiated, result
        except Exception as exc:  # try the older protocol before failing
            last_error = exc
    raise RuntimeError(f"MCP initialize failed for supported smoke-test versions: {last_error}")


def main() -> int:
    client = MCPProcess()
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")

        tools_result = require_result(client.request("tools/list"), "tools/list")
        tools = tools_result.get("tools", [])
        names = [t.get("name") for t in tools if isinstance(t, dict)]
        required = {
            "adb_connect",
            "adb_session_status",
            "firetv_device_info",
            "firetv_wake",
            "dev_package_info",
            "dev_app_status",
            "kill_dev_app",
            "dev_prepare_clean_start",
            "focused_window",
            "take_screenshot",
            "get_player_telemetry",
            "wait_for_player_event",
            "dev_player_state",
            "dev_set_player_config",
            "dev_connect_server",
            "dev_exit_session",
            "dev_wait_for_ui",
            "dev_wait_for_playback_started",
            "dev_start_recording",
            "dev_search",
            "dev_open_search",
            "dev_input_text_native",
            "dev_input_text_keyboard",
            "dev_hide_ime",
            "dev_wait_for_ime",
            "dev_sage_command",
            "dev_run_seek_check",
            "dev_run_comskip_check",
            "dev_test_checkpoint",
        }
        missing = sorted(required.difference(names))
        if missing:
            raise RuntimeError(f"MCP tools/list missing required tools: {', '.join(missing)}")
        print(f"PASS: MCP tool discovery ({len(names)} tools)")
        print("Tools: " + ", ".join(str(n) for n in names))

        adb_result = tool_call(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")
        print(compact_tool_result(adb_result))

        info_result = tool_call(client, "firetv_device_info")
        print("PASS: firetv_device_info")
        print(compact_tool_result(info_result))

        pkg_result = tool_call(client, "dev_package_info")
        print("PASS: dev_package_info")
        print(compact_tool_result(pkg_result))

        window_result = tool_call(client, "focused_window")
        print("PASS: focused_window")
        print(compact_tool_result(window_result))

        shot_result = tool_call(client, "take_screenshot", {"label": "mcp_smoke"}, timeout=30.0)
        print("PASS: take_screenshot")
        print(compact_tool_result(shot_result))

        print("MCP FIRE TV SMOKE TEST: PASS")
        return 0
    except Exception as exc:
        print(f"MCP FIRE TV SMOKE TEST: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
