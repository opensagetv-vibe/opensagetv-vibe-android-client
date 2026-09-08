#!/usr/bin/env python3
"""Launch/configure/connect the SageTV MiniClient and start the standard test video natively.

This intentionally does NOT use Sagex/HTTP to start playback.  It connects the Android
MiniClient directly to the requested SageTV server, then runs the native Search sequence using direct SageTV commands plus the MiniClient native keyboard-event path
and verifies that playback starts. Legacy sendkey/sendtext remain available for diagnostics.
"""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import json
import sys
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize
from mcp_ui_roots import wait_automation_root
from mcp_config_values import (
    DECODING_SELECTIONS,
    STREAMING_SELECTIONS,
    add_fixed_encoding_args,
    fixed_config_from_args,
    validate_fixed_config,
    decoding_preference,
    normalize_decoding,
    normalize_streaming,
    streaming_preference,
)

def build_sequence(text: str) -> str:
    """Legacy explicit sequence retained for mcp-send-sequence diagnostics only."""
    requested = str(text).strip()
    if not requested:
        raise ValueError("--text must not be empty")
    if "\n" in requested or "\r" in requested:
        raise ValueError("--text must be a single line")
    return f"""command search
delay 50
sendtext {requested}
delay 50
sendkey BACK
delay 100
command right
command right
command right
command select
delay 250
command select
"""


def start_recording_via_search(
    client: MCPProcess,
    text: str,
    *,
    search_timeout_s: float = 8.0,
    text_char_delay_ms: int = 0,
    media_type: str = "videos",
) -> dict:
    """Start the requested recording through SageTV Search.

    Normal automation uses the MiniClient's native keyboard-event protocol directly.
    It waits only for SageTV to advertise ``hasTextInput``; Android IME focus is not
    part of the text path.  A positive ``text_char_delay_ms`` explicitly selects the
    legacy Android/ADB diagnostic injector.
    """
    requested = str(text).strip()
    if not requested:
        raise ValueError("--text must not be empty")
    if "\n" in requested or "\r" in requested:
        raise ValueError("--text must be a single line")

    result: dict = {}
    search = call_dict(client, "dev_open_search", {
        "timeout_s": search_timeout_s,
        "require_ime": False,
        "suppress_ime": True,
    }, timeout=search_timeout_s + 10.0)
    result["openSearch"] = search
    if not search.get("passed"):
        raise RuntimeError(f"SageTV Search did not reach text-input state: {search}")

    normalized_media_type = str(media_type).strip().lower()
    if normalized_media_type not in {"tv", "videos"}:
        raise ValueError("media_type must be tv or videos")

    # Search remembers its last media type and field. Normalize from the text
    # box to Title and then to the left-most TV tab; select Videos explicitly
    # when requested. Repeated LEFT at the first item is intentionally safe.
    filter_commands = ["up", "left", "left", "up", "left", "left", "left"]
    if normalized_media_type == "videos":
        filter_commands.append("right")
    filter_commands.extend(["down", "down"])
    filter_results = []
    for command in filter_commands:
        filter_results.append(call_dict(
            client, "dev_sage_command", {"command": command}, timeout=30.0
        ))
        # SageTV7 rebuilds the category/results widgets after media-type
        # changes.  The old 50 ms burst could outrun that rebuild and leave
        # the search on TV even though Videos was requested.
        time.sleep(0.25)
    result["searchMediaType"] = normalized_media_type
    result["filterCommands"] = filter_results

    injected = call_dict(client, "dev_type_text", {
        "text": requested,
        "submit": False,
        "char_delay_ms": text_char_delay_ms,
    }, timeout=max(30.0, 10.0 + (len(requested) * max(0, text_char_delay_ms) / 1000.0)))
    result["textInjection"] = injected
    result["textInputPath"] = injected.get("inputPath", "")
    time.sleep(0.10)

    # Native MiniClient text does not need the Android IME. MCP enabled debug IME
    # suppression before opening Search. Only hide explicitly if Android reports an
    # already-visible IME; do not send BACK and do not perform an unnecessary hide.
    try:
        post_text_state = call_dict(client, "dev_player_state", {}, timeout=30.0)
        result["postTextState"] = post_text_state
        if bool(post_text_state.get("imeVisibleKnown", False)) and bool(post_text_state.get("imeVisible", False)):
            result["hideIme"] = call_dict(client, "dev_hide_ime", {}, timeout=30.0)
        else:
            result["hideIme"] = {"skipped": True, "reason": "ime_not_visible"}

        # SageTV7's Search keyboard leaves focus on Back after native text
        # entry. Three deterministic Right events focus the first visible
        # result. The prior ff/right/play_pause/down sequence depended on the
        # current keypad focus and could select a non-Watch popup action.
        post_commands = []
        for command in ("right", "right", "right", "select"):
            try:
                command_result = call_dict(client, "dev_sage_command", {"command": command}, timeout=30.0)
            except Exception as exc:
                raise RuntimeError(f"post-Search SageTV command failed: command={command}: {exc}") from exc
            post_commands.append({"command": command, "result": command_result})
            time.sleep(0.25)

        options = call_dict(client, "dev_wait_for_ui", {
            "popup_contains": "option",
            "timeout_s": search_timeout_s,
        }, timeout=search_timeout_s + 10.0)
        result["resultOptions"] = options
        if not options.get("passed"):
            raise RuntimeError(f"Search result did not open its Options popup: {options}")

        watch_now = call_dict(client, "dev_sage_command", {"command": "select"}, timeout=30.0)
        post_commands.append({"command": "select_watch_now", "result": watch_now})

        # A partially watched recording adds a second prompt. Its default is
        # Resume Playback; choose it once. A never-watched recording starts
        # immediately and needs no extra command.
        prompt_deadline = time.monotonic() + search_timeout_s
        resume_selected = False
        final_start_state = {}
        while time.monotonic() < prompt_deadline:
            final_start_state = call_dict(client, "dev_player_state", {}, timeout=30.0)
            popup = str(final_start_state.get("popupName") or "").strip()
            normalized_popup = popup.lower()
            if "resume" in normalized_popup and "restart" in normalized_popup and not resume_selected:
                resume = call_dict(client, "dev_sage_command", {"command": "select"}, timeout=30.0)
                post_commands.append({"command": "select_resume_default", "popup": popup, "result": resume})
                resume_selected = True
            elif bool(final_start_state.get("playerActive")) and not popup:
                break
            time.sleep(0.20)
        result["postSelectionState"] = final_start_state
        result["resumePromptSelected"] = resume_selected
        result["postSearchCommands"] = post_commands
    finally:
        try:
            result["imeSuppressionRestore"] = call_dict(
                client, "dev_set_ime_suppression", {"enabled": False}, timeout=30.0
            )
        except Exception as exc:
            result["imeSuppressionRestoreError"] = str(exc)

    result["textEntryVerified"] = False
    result["textEntryVerification"] = "deferred_until_recording_playback_starts"
    return result


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Launch/configure/connect MiniClient and start the standard test recording through native client input"
    )
    parser.add_argument("--server", default=default_server_address(), help="SageTV server IP/address")
    parser.add_argument("--port", type=int, default=int(default_server_value("miniclient_port", 31099)), help="SageTV MiniClient port")
    parser.add_argument("--player", choices=("exoplayer", "media3", "ijkplayer", "gsyplayer"), default="media3")
    parser.add_argument("--streaming", type=normalize_streaming, choices=STREAMING_SELECTIONS, default="push", help="Streaming selection: push, pull, smb_direct, smb_auto, fixed (legacy dynamic accepted)")
    parser.add_argument("--decoding", "--decoder", dest="decoding", type=normalize_decoding, choices=DECODING_SELECTIONS, default="hardware", help="Decoding selection: hardware, software, fallback (legacy hardware_preferred accepted)")
    add_fixed_encoding_args(parser)
    parser.add_argument("--gsy-engine", choices=("auto", "media3", "system", "legacy_exo"), default="auto")
    parser.add_argument("--gsy-system-probe", action="store_true",
                        help="Debug-only: exercise Android System MediaPlayer and verify its fail-safe fallback")
    parser.add_argument("--text", required=True, help="Required SageTV Search text used to start the test recording")
    parser.add_argument("--server-path", default="", help="Prefer an exact server-side MediaFile path through the opt-in Vibe protocol extension")
    parser.add_argument("--text-char-delay-ms", type=int, default=0, help="Text input mode: 0 uses MiniClient native key events; >0 enables legacy Android/ADB diagnostic pacing (ms)")
    parser.add_argument("--connect-timeout-s", type=float, default=30.0)
    parser.add_argument(
        "--ui-stable-ms",
        type=int,
        default=2000,
        help="Require a non-empty SageTV menu hint to remain stable this long before Search (default: 2000 ms)",
    )
    parser.add_argument("--playback-timeout-s", type=float, default=45.0)
    parser.add_argument("--verify-ms", type=int, default=1500)
    parser.add_argument("--leave-running", action="store_true", help="Leave the MiniClient running after a failed test too")
    args = parser.parse_args()
    if args.gsy_system_probe and not (
        args.player == "gsyplayer" and args.gsy_engine == "system"
        and args.streaming == "pull"
    ):
        parser.error("--gsy-system-probe requires --player gsyplayer --gsy-engine system --streaming pull")
    fixed_config = fixed_config_from_args(args)
    try:
        validate_fixed_config(fixed_config)
    except ValueError as exc:
        parser.error(str(exc))
    if args.text_char_delay_ms < 0 or args.text_char_delay_ms > 2000:
        parser.error("--text-char-delay-ms must be between 0 and 2000")

    client = MCPProcess()
    passed = False
    try:
        negotiated, _ = initialize(client)
        print(f"PASS: MCP initialize handshake ({negotiated})")

        call_dict(client, "adb_connect", timeout=30.0)
        print("PASS: adb_connect")

        print("STEP: prepare clean Dev MiniClient start")
        clean = call_dict(client, "dev_prepare_clean_start", {"wake": True, "graceful_timeout_s": 2.0}, timeout=30.0)
        print(json.dumps(clean, indent=2, sort_keys=True))
        if not clean.get("readyToLaunch") or clean.get("stopped", {}).get("running"):
            raise RuntimeError(f"Dev app could not be stopped cleanly before launch: {clean}")

        print("STEP: set next-playback configuration")
        configured = call_dict(client, "dev_set_player_config", {
            "player": args.player,
            "streaming": streaming_preference(args.streaming),
            "decoding": decoding_preference(args.decoding),
            "gsy_engine": args.gsy_engine,
            "gsy_system_probe": args.gsy_system_probe,
            **fixed_config,
        })
        print(json.dumps(configured, indent=2, sort_keys=True))

        print(f"STEP: connect directly to SageTV server {args.server}:{args.port} (save=false)")
        connected = call_dict(client, "dev_connect_server", {
            "address": args.server,
            "port": args.port,
            "save": False,
        })
        print(json.dumps(connected, indent=2, sort_keys=True))

        print("STEP: wait for Android debug readiness")
        connected_ready = call_dict(client, "dev_wait_for_ui", {
            "connected": True,
            "timeout_s": args.connect_timeout_s,
        }, timeout=args.connect_timeout_s + 10.0)
        if not connected_ready.get("passed"):
            raise RuntimeError(f"MiniClient did not connect within timeout: {connected_ready}")
        app_status = call_dict(client, "dev_app_status", {}, timeout=30.0)
        if not app_status.get("running"):
            raise RuntimeError(f"Dev app is not running after direct SageTV connect: {app_status}")
        print("PASS: Dev app running after direct connect: " + json.dumps(app_status, sort_keys=True))
        ready = wait_automation_root(
            client,
            timeout_s=args.connect_timeout_s,
            stable_ms=args.ui_stable_ms,
        )
        state = ready.get("state", {})
        actual_server = str(state.get("serverAddress", "")).strip()
        if actual_server and actual_server != args.server:
            raise RuntimeError(f"Connected to wrong SageTV server: expected {args.server}, got {actual_server}")
        print(f"PASS: connected to requested server {args.server}; supported automation root ready")
        print(json.dumps(state, indent=2, sort_keys=True))

        if args.server_path.strip():
            print(f"STEP: start exact server MediaFile path: {args.server_path!r}")
            direct_start = call_dict(client, "dev_play_server_path", {
                "server_path": args.server_path,
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
            }, timeout=args.playback_timeout_s + 35.0)
            print(json.dumps(direct_start, indent=2, sort_keys=True))
            if not direct_start.get("passed"):
                raise RuntimeError(f"Direct server-path playback failed: {direct_start}")
        else:
            print("STEP: open Search, verify Android keyboard, type recording name, and start recording")
            search_start = start_recording_via_search(client, args.text, text_char_delay_ms=args.text_char_delay_ms)
            print(json.dumps(search_start, indent=2, sort_keys=True))

            print("STEP: wait for real playback to start")
            playback = call_dict(client, "dev_wait_for_playback_started", {
                "timeout_s": args.playback_timeout_s,
                "verify_ms": args.verify_ms,
            }, timeout=args.playback_timeout_s + 15.0)
            print(json.dumps(playback, indent=2, sort_keys=True))
            if not playback.get("passed"):
                raise RuntimeError(f"Playback did not become healthy: {playback}")

        if args.gsy_system_probe:
            probe = call_dict(client, "dev_player_state", {}, timeout=30.0)
            resolved = str(probe.get("gsyResolvedEngine", "")).strip().lower()
            fallback_count = int(probe.get("gsySystemFallbackCount", 0))
            fallback_reason = str(probe.get("gsySystemFallbackReason", ""))
            backend = str(probe.get("health_backendClass", ""))
            if resolved == "system":
                if "GSYSystemMediaPlayerImpl" not in backend:
                    raise RuntimeError(f"System probe reported inconsistent active backend: {probe}")
                outcome = "SYSTEM_ACTIVE"
            elif resolved == "media3":
                if fallback_count != 1 or fallback_reason != "android_system_player_error":
                    raise RuntimeError(f"System probe did not prove one bounded fail-safe fallback: {probe}")
                if "Media3MediaPlayerImpl" not in backend:
                    raise RuntimeError(f"System fallback did not resolve to Media3: {probe}")
                outcome = "MEDIA3_FALLBACK"
            else:
                raise RuntimeError(f"System probe did not expose its resolved backend: {probe}")
            print("GSY SYSTEM PROBE: PASS " + json.dumps({
                "outcome": outcome,
                "resolvedEngine": resolved,
                "fallbackCount": fallback_count,
                "fallbackReason": fallback_reason,
                "backendClass": backend,
                "videoDecoder": probe.get("health_videoDecoder", ""),
            }, sort_keys=True))

        print("MCP PLAYBACK START TEST: PASS")
        passed = True
        return 0
    except Exception as exc:
        print(f"MCP PLAYBACK START TEST: FAIL\n{exc}", file=sys.stderr)
        return 1
    finally:
        if not passed and not args.leave_running:
            try:
                checkpoint = call_dict(client, "dev_test_checkpoint", {"label": "mcp_playback_start_fail"}, timeout=30.0)
                print("FAILURE CHECKPOINT: " + json.dumps(checkpoint, sort_keys=True), file=sys.stderr)
            except Exception as exc:
                print(f"WARN: failure checkpoint failed: {exc}", file=sys.stderr)
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
