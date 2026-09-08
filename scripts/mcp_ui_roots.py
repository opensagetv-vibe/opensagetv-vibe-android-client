#!/usr/bin/env python3
"""Shared physical-test handling for stock and alternate SageTV STV roots."""
from __future__ import annotations

from mcp_seek_suite import MCPProcess, call_dict


AUTOMATION_ROOT_MENUS = {"main menu", "dynamic menu by nielm"}


def is_automation_root(state: dict) -> bool:
    """Return true only for a connected, idle, unblocked supported STV root."""
    if bool(state.get("automationReady")):
        return True
    return (
        bool(state.get("connected"))
        and not bool(state.get("playerActive"))
        and not bool(state.get("hasTextInput"))
        and not str(state.get("popupName") or "").strip()
        and str(state.get("menuName") or "").strip().lower() in AUTOMATION_ROOT_MENUS
    )


def wait_automation_root(
    client: MCPProcess,
    timeout_s: float = 30.0,
    stable_ms: int = 2000,
) -> dict:
    """Normalize HOME and wait for a stock or SageMC idle automation root."""
    connected = call_dict(
        client,
        "dev_wait_for_ui",
        {"connected": True, "timeout_s": timeout_s},
        timeout=timeout_s + 10.0,
    )
    if not bool(connected.get("passed")):
        raise RuntimeError(f"MiniClient connection failed: {connected}")
    if not is_automation_root(connected.get("state", {})):
        call_dict(client, "dev_sage_command", {"command": "home"}, timeout=30.0)
    ready = call_dict(
        client,
        "dev_wait_for_ui",
        {
            "connected": True,
            "player_active": False,
            "menu_present": True,
            "stable_ms": stable_ms,
            "timeout_s": timeout_s,
        },
        timeout=timeout_s + 10.0,
    )
    if not bool(ready.get("passed")) or not is_automation_root(ready.get("state", {})):
        raise RuntimeError(f"MiniClient UI did not reach a supported automation root: {ready}")
    return ready
