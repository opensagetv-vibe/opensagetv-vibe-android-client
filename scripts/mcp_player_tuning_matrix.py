#!/usr/bin/env python3
"""Sweep Dev runtime player tuning combinations through MCP without rebuilding between tests."""
from __future__ import annotations

from sagetv_dev_mcp.config import default_server_address, default_server_value

import argparse
import itertools
import json
import sys
import time
from datetime import datetime
import os
from pathlib import Path
from typing import Any

from mcp_media3_matrix import MCPProcess, call_dict, initialize, safe_checkpoint
from mcp_config_values import (
    add_fixed_encoding_args,
    decoding_preference,
    fixed_config_from_args,
    fixed_expected_snapshot,
    streaming_preference,
    validate_fixed_config,
)
from mcp_player_matrix import (
    PlayerCase,
    run_absolute_seek,
    run_comskip,
    run_pause_resume,
    run_relative_seek,
    start_case,
)

CHECKS = ("absolute_seek", "seek_forward", "seek_backward", "pause_resume", "comskip_right", "comskip_left")
CODEC_MODES = ("auto", "async", "sync")
SEEK_POLICIES = ("closest", "next", "previous", "directional")
PROFILE_NAMES = ("default", "next_sync", "directional", "large_read", "auto_codec", "async_codec", "sync_codec", "no_reprepare", "fast_buffer")
STREAMING_MODES = ("pull", "push")
PUSH_IGNORED_TUNING_KEYS = (
    "tsSearchMultiplier", "seekPolicy", "pullReadKb", "minBufferMs", "maxBufferMs",
    "playbackBufferMs", "rebufferMs", "seekRecovery", "seekRecoveryMs",
    "directionalSyncMinDeltaMs",
)


def csv_ints(value: str, *, label: str) -> list[int]:
    try:
        out = [int(x.strip()) for x in str(value).split(",") if x.strip()]
    except ValueError as exc:
        raise ValueError(f"{label} must be comma-separated integers") from exc
    if not out:
        raise ValueError(f"{label} must not be empty")
    return list(dict.fromkeys(out))


def csv_choices(value: str, allowed: tuple[str, ...], label: str) -> list[str]:
    out = [x.strip().lower() for x in str(value).split(",") if x.strip()]
    if not out:
        raise ValueError(f"{label} must not be empty")
    invalid = [x for x in out if x not in allowed]
    if invalid:
        raise ValueError(f"invalid {label}: {', '.join(invalid)}")
    return list(dict.fromkeys(out))


def csv_bools(value: str) -> list[bool]:
    out: list[bool] = []
    for raw in str(value).split(","):
        v = raw.strip().lower()
        if not v:
            continue
        if v in ("on", "true", "1", "yes", "enabled"):
            b = True
        elif v in ("off", "false", "0", "no", "disabled"):
            b = False
        else:
            raise ValueError(f"invalid seek-recovery value: {raw}")
        if b not in out:
            out.append(b)
    if not out:
        raise ValueError("--seek-recovery must not be empty")
    return out


def backend_defaults(player: str) -> dict[str, Any]:
    if player == "media3":
        return {
            "tsSearchMultiplier": 16, "seekPolicy": "closest", "pullReadKb": 256,
            "minBufferMs": 5000, "maxBufferMs": 20000, "playbackBufferMs": 500,
            "rebufferMs": 1000, "seekRecovery": False, "seekRecoveryMs": 10000,
            "codecMode": "sync", "directionalSyncMinDeltaMs": 2000,
        }
    return {
        "tsSearchMultiplier": 16, "seekPolicy": "directional", "pullReadKb": 512,
        "minBufferMs": 5000, "maxBufferMs": 20000, "playbackBufferMs": 500,
        "rebufferMs": 1000, "seekRecovery": True, "seekRecoveryMs": 10000,
        "codecMode": "sync", "directionalSyncMinDeltaMs": 2000,
    }


def named_profile(player: str, name: str) -> dict[str, Any]:
    c = backend_defaults(player)
    if name == "default":
        pass
    elif name == "next_sync":
        c["seekPolicy"] = "next"
    elif name == "directional":
        c["seekPolicy"] = "directional"
    elif name == "large_read":
        c["pullReadKb"] = 512 if player == "media3" else 1024
    elif name == "auto_codec":
        c["codecMode"] = "auto"
    elif name == "async_codec":
        c["codecMode"] = "async"
    elif name == "sync_codec":
        c["codecMode"] = "sync"
    elif name == "no_reprepare":
        c["seekRecovery"] = False
    elif name == "fast_buffer":
        c.update({"minBufferMs": 2500, "maxBufferMs": 10000, "playbackBufferMs": 250, "rebufferMs": 500})
    else:
        raise ValueError(f"unknown profile {name}")
    c["profile"] = name
    return c


def config_to_mcp(player: str, c: dict[str, Any]) -> dict[str, Any]:
    prefix = "media3" if player == "media3" else "exo2"
    return {
        f"{prefix}_ts_search_multiplier": c["tsSearchMultiplier"],
        f"{prefix}_pull_read_kb": c["pullReadKb"],
        f"{prefix}_min_buffer_ms": c["minBufferMs"],
        f"{prefix}_max_buffer_ms": c["maxBufferMs"],
        f"{prefix}_playback_buffer_ms": c["playbackBufferMs"],
        f"{prefix}_rebuffer_ms": c["rebufferMs"],
        f"{prefix}_seek_recovery_enabled": c["seekRecovery"],
        f"{prefix}_seek_recovery_delay_ms": c["seekRecoveryMs"],
        f"{prefix}_seek_policy": c["seekPolicy"],
        f"{prefix}_codec_mode": c["codecMode"],
        "directional_sync_min_delta_ms": c["directionalSyncMinDeltaMs"],
    }


def streaming_modes(value: str) -> list[str]:
    return csv_choices(value, STREAMING_MODES, "streaming mode")


def normalize_for_streaming(player: str, streaming: str, combo: dict[str, Any]) -> dict[str, Any]:
    if streaming == "pull":
        return dict(combo)
    defaults = backend_defaults(player)
    out = dict(combo)
    for key in PUSH_IGNORED_TUNING_KEYS:
        out[key] = defaults[key]
    return out


def _dedupe_configs(configs: list[dict[str, Any]]) -> list[dict[str, Any]]:
    deduped: list[dict[str, Any]] = []
    seen: dict[tuple[Any, ...], dict[str, Any]] = {}
    keys = tuple(backend_defaults("media3").keys())
    for config in configs:
        signature = tuple(config.get(k) for k in keys)
        existing = seen.get(signature)
        if existing is not None:
            alias = config.get("profile")
            if alias:
                aliases = existing.setdefault("profileAliases", [])
                if alias != existing.get("profile") and alias not in aliases:
                    aliases.append(alias)
            continue
        copy = dict(config)
        seen[signature] = copy
        deduped.append(copy)
    return deduped


def make_grid(args: argparse.Namespace, streaming: str = "pull") -> list[dict[str, Any]]:
    if streaming not in STREAMING_MODES:
        raise ValueError(f"invalid streaming mode: {streaming}")
    if args.profiles:
        names = csv_choices(args.profiles, PROFILE_NAMES, "profiles")
        configs = [normalize_for_streaming(args.player, streaming, named_profile(args.player, name)) for name in names]
        return _dedupe_configs(configs)

    d = backend_defaults(args.player)
    if streaming == "push":
        # Push does not use the Pull extractor/read/load-control/seek-recovery tuning.
        # Only codec adapter mode is meaningful from this runtime tuning surface.
        codec_modes = csv_choices(args.codec_mode or d["codecMode"], CODEC_MODES, "codec mode")
        return [normalize_for_streaming(args.player, streaming, {**d, "codecMode": codec}) for codec in codec_modes]

    values = {
        "tsSearchMultiplier": csv_ints(args.ts_search or str(d["tsSearchMultiplier"]), label="--ts-search"),
        "seekPolicy": csv_choices(args.seek_policy or d["seekPolicy"], SEEK_POLICIES, "seek policy"),
        "pullReadKb": csv_ints(args.pull_read_kb or str(d["pullReadKb"]), label="--pull-read-kb"),
        "minBufferMs": csv_ints(args.min_buffer_ms or str(d["minBufferMs"]), label="--min-buffer-ms"),
        "maxBufferMs": csv_ints(args.max_buffer_ms or str(d["maxBufferMs"]), label="--max-buffer-ms"),
        "playbackBufferMs": csv_ints(args.playback_buffer_ms or str(d["playbackBufferMs"]), label="--playback-buffer-ms"),
        "rebufferMs": csv_ints(args.rebuffer_ms or str(d["rebufferMs"]), label="--rebuffer-ms"),
        "seekRecovery": csv_bools(args.seek_recovery or ("on" if d["seekRecovery"] else "off")),
        "seekRecoveryMs": csv_ints(args.seek_recovery_ms or str(d["seekRecoveryMs"]), label="--seek-recovery-ms"),
        "codecMode": csv_choices(args.codec_mode or d["codecMode"], CODEC_MODES, "codec mode"),
        "directionalSyncMinDeltaMs": csv_ints(args.directional_sync_min_delta_ms or str(d["directionalSyncMinDeltaMs"]), label="--directional-sync-min-delta-ms"),
    }
    keys = list(values)
    combos: list[dict[str, Any]] = []
    for vals in itertools.product(*(values[k] for k in keys)):
        c = dict(zip(keys, vals))
        if c["maxBufferMs"] < c["minBufferMs"]:
            continue
        if c["playbackBufferMs"] > c["minBufferMs"] or c["rebufferMs"] > c["minBufferMs"]:
            continue
        combos.append(c)
    if not combos:
        raise ValueError("no valid tuning combinations remain after buffer validation")
    return combos


def fast_start_case(
    client: MCPProcess,
    case: PlayerCase,
    *,
    media_file_id: int,
    server: str,
    port: int,
    connect_timeout_s: float,
    ui_stable_ms: int,
    playback_timeout_s: float,
    verify_ms: int,
    fixed_config: dict[str, Any],
    tuning_config: dict[str, Any],
) -> dict[str, Any]:
    """Create a fresh player for the cached MediaFile without killing/searching the app.

    The SageTV session is disconnected first so the prior player is fully disposed. The
    runtime tuning/config is then applied, the same server is reconnected, and the exact
    cached MediaFile ID is started directly through Sagex. This preserves player-instance
    isolation while removing the expensive force-stop + Search UI workflow.
    """
    print(f"\n=== CASE {case.id} [fast replay] ===")
    exited = call_dict(client, "dev_exit_session", {"stop_app": False}, timeout=30.0)

    tuning = call_dict(client, "dev_set_player_tuning", {"reset": True, **tuning_config})
    if not tuning.get("ok", True):
        raise RuntimeError(f"player tuning was not applied: {tuning}")

    config_request = {
        "player": case.player,
        "streaming": streaming_preference(case.streaming),
        "decoding": decoding_preference(case.decoder),
        "gsy_engine": case.gsy_engine,
        **fixed_config,
    }
    configured = call_dict(client, "dev_set_player_config", config_request)
    expected_configured = {
        "player": case.player,
        "streaming": streaming_preference(case.streaming),
        "decoding": decoding_preference(case.decoder),
        "gsyEngine": case.gsy_engine,
        **fixed_expected_snapshot(fixed_config),
    }
    for key, value in expected_configured.items():
        actual = str(configured.get(key, "")).lower()
        if actual != value:
            raise RuntimeError(
                f"configuration parameter was not applied: {key} selected={value} response={actual or '<missing>'}"
            )

    connected = call_dict(client, "dev_connect_server", {"address": server, "port": port, "save": False}, timeout=30.0)
    ready = call_dict(client, "dev_wait_for_ui", {
        "connected": True,
        "automation_ready": True,
        "stable_ms": ui_stable_ms,
        "timeout_s": connect_timeout_s,
    }, timeout=connect_timeout_s + 10.0)
    if not ready.get("passed"):
        raise RuntimeError(f"fast replay automationReady not reached: {ready}")

    call_dict(client, "dev_clear_player_events", {}, timeout=30.0)
    playback = call_dict(client, "dev_play_media_file_id", {
        "media_file_id": int(media_file_id),
        "timeout_s": playback_timeout_s,
        "verify_ms": verify_ms,
    }, timeout=playback_timeout_s + 15.0)
    if not playback.get("passed"):
        raise RuntimeError(f"fast replay playback did not become healthy: {playback}")

    state = call_dict(client, "dev_player_state")
    expected = {
        "player": case.player,
        "streaming": streaming_preference(case.streaming),
        "decoding": decoding_preference(case.decoder),
    }
    for key, value in expected.items():
        actual = str(state.get(key, "")).lower()
        if actual and actual != value:
            raise RuntimeError(f"configured {key}={value} but active snapshot reports {actual}")
    print(
        f"PASS: fast replay {case.id}; mediaFileId={media_file_id} "
        f"playerClass={state.get('playerClass', '')} videoDecoder={state.get('health_videoDecoder', '')}"
    )
    return {
        "startupMode": "fast_media_file_id",
        "exitSession": exited,
        "tuning": tuning,
        "selectedConfig": {"player": case.player, "streaming": case.streaming, "decoding": case.decoder, "gsyEngine": case.gsy_engine},
        "appliedConfig": {"player": case.player, "streaming": streaming_preference(case.streaming), "decoding": decoding_preference(case.decoder), "gsyEngine": case.gsy_engine},
        "configured": configured,
        "connected": connected,
        "ready": ready,
        "mediaFileId": int(media_file_id),
        "playback": playback,
        "state": state,
    }


def run_check(client: MCPProcess, args: argparse.Namespace, label: str) -> dict[str, Any]:
    common = dict(label=label, watchdog_ms=args.watchdog_ms, health_poll_ms=args.health_poll_ms,
                  slow_ms=args.slow_recovery_ms, crash_probe_milestones_ms=(5000, 15000, 30000))
    if args.check == "absolute_seek":
        return run_absolute_seek(client, target_ms=args.target_ms, seek_tolerance_ms=args.seek_tolerance_ms, **common)
    if args.check == "seek_forward":
        return run_relative_seek(client, delta_ms=args.skip_forward_ms, seek_tolerance_ms=args.seek_tolerance_ms, **common)
    if args.check == "seek_backward":
        return run_relative_seek(client, delta_ms=-abs(args.skip_backward_ms), seek_tolerance_ms=args.seek_tolerance_ms, **common)
    if args.check == "pause_resume":
        return run_pause_resume(client, **common)
    if args.check == "comskip_right":
        return run_comskip(client, direction="right", **common)
    if args.check == "comskip_left":
        return run_comskip(client, direction="left", **common)
    raise RuntimeError(args.check)


def recovery_ms(obs: dict[str, Any]) -> int:
    r = obs.get("result") if isinstance(obs.get("result"), dict) else obs
    try: return int(r.get("recoveryMs", r.get("resumeMs", 999999999)))
    except Exception: return 999999999


def write_report(report: dict[str, Any], requested: str) -> Path:
    if requested:
        path = Path(requested)
    else:
        root = Path(os.environ.get("SAGETV_ARTIFACT_DIR", Path(__file__).resolve().parents[1] / "artifacts" / "firetv"))
        root.mkdir(parents=True, exist_ok=True)
        path = root / (datetime.now().strftime("%Y%m%d_%H%M%S") + "_player_tuning_matrix.json")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    return path


def main() -> int:
    p = argparse.ArgumentParser(description="Sweep runtime Media3/legacy Exo2 tuning combinations through MCP")
    p.add_argument("--server", default=default_server_address())
    p.add_argument("--port", type=int, default=int(default_server_value("miniclient_port", 31099)))
    p.add_argument("--text", default="", help="SageTV Search text when --server-path is not supplied")
    p.add_argument("--server-path", default="", help="Exact SageTV server-side MediaFile path for deterministic fixture runs")
    p.add_argument("--player", choices=("media3", "exoplayer"), default="media3")
    p.add_argument("--streaming", default="pull", help="Comma list: pull,push (either order accepted)")
    p.add_argument("--decoding", choices=("hardware", "software", "fallback"), default="hardware")
    p.add_argument("--check", choices=CHECKS, default="comskip_right")
    p.add_argument("--profiles", default="", help="Comma list: " + ",".join(PROFILE_NAMES))
    p.add_argument("--ts-search", default="")
    p.add_argument("--seek-policy", default="")
    p.add_argument("--pull-read-kb", default="")
    p.add_argument("--min-buffer-ms", default="")
    p.add_argument("--max-buffer-ms", default="")
    p.add_argument("--playback-buffer-ms", default="")
    p.add_argument("--rebuffer-ms", default="")
    p.add_argument("--seek-recovery", default="")
    p.add_argument("--seek-recovery-ms", default="")
    p.add_argument("--codec-mode", default="")
    p.add_argument("--directional-sync-min-delta-ms", default="")
    p.add_argument("--watchdog-ms", type=int, default=50000)
    p.add_argument("--slow-recovery-ms", "--slow_recover_ms", dest="slow_recovery_ms", type=int, default=5000)
    p.add_argument("--health-poll-ms", type=int, default=250)
    p.add_argument("--target-ms", type=int, default=0)
    p.add_argument("--skip-forward-ms", type=int, default=10000)
    p.add_argument("--skip-backward-ms", type=int, default=10000)
    p.add_argument("--seek-tolerance-ms", type=int, default=10000)
    p.add_argument("--connect-timeout-s", type=float, default=30.0)
    p.add_argument("--ui-stable-ms", type=int, default=2000)
    p.add_argument("--playback-timeout-s", type=float, default=45.0)
    p.add_argument("--startup-verify-ms", type=int, default=1500)
    p.add_argument("--startup-mode", choices=("fast", "isolated"), default="fast",
                   help="fast replays the first Search-selected MediaFile ID with a fresh player; isolated force-stops/searches every combination")
    p.add_argument("--fast-ui-stable-ms", type=int, default=500,
                   help="UI stability window used by fast replay reconnects")
    p.add_argument("--text-char-delay-ms", type=int, default=0)
    p.add_argument("--max-combinations", type=int, default=64)
    p.add_argument("--report", default="")
    p.add_argument("--leave-running", action="store_true")
    add_fixed_encoding_args(p)
    args = p.parse_args()
    if not args.text.strip() and not args.server_path.strip():
        p.error("one of --text or --server-path is required")

    try:
        fixed = fixed_config_from_args(args)
        validate_fixed_config(fixed)
        modes = streaming_modes(args.streaming)
        grids = {mode: make_grid(args, mode) for mode in modes}
        total_combinations = sum(len(grid) for grid in grids.values())
        if total_combinations > args.max_combinations:
            raise ValueError(f"generated {total_combinations} combinations; raise --max-combinations above {args.max_combinations} or narrow the grid")
    except ValueError as exc:
        p.error(str(exc))

    client = MCPProcess()
    results: list[dict[str, Any]] = []
    cached_media_file_id: int | None = None
    fast_start_fallback_count = 0
    try:
        initialize(client)
        # Match the regular player matrix: establish the ADB transport before the
        # first device-backed MCP tool. Without this, dev_prepare_clean_start is
        # invoked against an unconnected ADB session and every tuning combination
        # is misreported as an infrastructure failure.
        call_dict(client, "adb_connect", {}, timeout=30.0)
        session = call_dict(client, "adb_session_status", {}, timeout=30.0)
        print("PASS: adb_connect")
        if session.get("connected") is False:
            raise RuntimeError(f"ADB session is not connected after adb_connect: {session}")
        overall_index = 0
        for streaming in modes:
            case = PlayerCase(args.player, streaming, args.decoding)
            combos = grids[streaming]
            for stream_index, combo in enumerate(combos, 1):
                overall_index += 1
                base_label = combo.get("profile") or f"combo_{stream_index:03d}"
                label = f"{streaming}_{base_label}"
                print(f"\n=== TUNING {overall_index}/{total_combinations} {label} ===")
                if streaming == "push":
                    print("Push mode: Pull-only tuning dimensions ignored; codecMode is the only varied runtime tuning parameter.")
                print(json.dumps(combo, sort_keys=True))
                try:
                    startup_started = time.monotonic()
                    tuning_config = config_to_mcp(args.player, combo)
                    startup_path = "isolated"
                    if args.startup_mode == "fast" and cached_media_file_id is not None:
                        try:
                            startup = fast_start_case(
                                client, case, media_file_id=cached_media_file_id,
                                server=args.server, port=args.port, connect_timeout_s=args.connect_timeout_s,
                                ui_stable_ms=args.fast_ui_stable_ms, playback_timeout_s=args.playback_timeout_s,
                                verify_ms=args.startup_verify_ms, fixed_config=fixed, tuning_config=tuning_config,
                            )
                            startup_path = "fast_media_file_id"
                        except Exception as fast_exc:
                            fast_start_fallback_count += 1
                            print(f"WARN: fast replay failed; falling back to full isolated startup: {fast_exc}", file=sys.stderr)
                            startup = start_case(
                                client, case, server=args.server, port=args.port, text=args.text, server_path=args.server_path,
                                text_char_delay_ms=args.text_char_delay_ms, connect_timeout_s=args.connect_timeout_s,
                                ui_stable_ms=args.ui_stable_ms, playback_timeout_s=args.playback_timeout_s,
                                verify_ms=args.startup_verify_ms, fixed_config=fixed, tuning_config=tuning_config,
                            )
                            startup_path = "isolated_fallback"
                    else:
                        startup = start_case(
                            client, case, server=args.server, port=args.port, text=args.text, server_path=args.server_path,
                            text_char_delay_ms=args.text_char_delay_ms, connect_timeout_s=args.connect_timeout_s,
                            ui_stable_ms=args.ui_stable_ms, playback_timeout_s=args.playback_timeout_s,
                            verify_ms=args.startup_verify_ms, fixed_config=fixed, tuning_config=tuning_config,
                        )

                    state = startup["state"]
                    startup_ms = int((time.monotonic() - startup_started) * 1000.0)
                    print(f"STARTUP {label}: {startup_path} {startup_ms} ms")
                    if args.startup_mode == "fast" and cached_media_file_id is None and not args.server_path:
                        current_media = call_dict(client, "dev_current_media_file", {}, timeout=30.0)
                        raw_media_id = current_media.get("mediaFileId")
                        if current_media.get("ok") and raw_media_id is not None:
                            cached_media_file_id = int(raw_media_id)
                            print(f"PASS: cached MediaFile ID {cached_media_file_id} for fast tuning replays")
                        else:
                            print(f"WARN: could not cache current MediaFile ID; remaining combinations will use isolated startup: {current_media}", file=sys.stderr)
                    elif args.startup_mode == "fast" and args.server_path:
                        print("PASS: exact server path will be replayed with an isolated player for each tuning case")
                    if int(state.get("debugStatusVersion", 0) or 0) < 13:
                        raise RuntimeError(f"debugStatusVersion={state.get('debugStatusVersion')} need >=13 / v0.5.70")
                    obs = run_check(client, args, f"tuning/{streaming}/{base_label}/{args.check}")
                    item = {
                        "index": overall_index, "streaming": streaming, "label": label,
                        "tuning": combo, "effectiveState": state, "startup": startup,
                        "startupPath": startup_path, "startupMs": startup_ms, "observation": obs,
                    }
                    if obs.get("status") not in ("RECOVERED",):
                        item["checkpoint"] = safe_checkpoint(client, f"tuning_{overall_index:03d}_{args.player}_{streaming}_{args.check}_{obs.get('status','unknown').lower()}")
                    results.append(item)
                    print(f"RESULT {label}: {obs.get('status')} recoveryMs={recovery_ms(obs)}")
                except Exception as exc:
                    print(f"INFRA_ERROR {label}: {exc}", file=sys.stderr)
                    results.append({
                        "index": overall_index, "streaming": streaming, "label": label,
                        "tuning": combo, "status": "INFRA_ERROR", "error": str(exc),
                    })

        ranked = sorted(
            [x for x in results if isinstance(x.get("observation"), dict)],
            key=lambda x: (0 if x["observation"].get("status") == "RECOVERED" else 1,
                           recovery_ms(x["observation"]))
        )
        summary = [{
            "rank": i+1, "streaming": x["streaming"], "label": x["label"],
            "status": x["observation"].get("status"), "recoveryMs": recovery_ms(x["observation"]),
            "tuning": x["tuning"],
        } for i, x in enumerate(ranked)]
        report = {
            "schema": 1, "suite": "player_runtime_tuning_matrix", "server": args.server, "port": args.port,
            "searchText": args.text, "serverPath": args.server_path,
            "player": args.player, "streaming": args.streaming,
            "streamingModes": modes, "decoding": args.decoding, "check": args.check,
            "combinationCount": total_combinations,
            "combinationCountByStreaming": {mode: len(grids[mode]) for mode in modes},
            "ignoredTuningDimensionsByStreaming": {
                mode: (list(PUSH_IGNORED_TUNING_KEYS) if mode == "push" else []) for mode in modes
            },
            "watchdogMs": args.watchdog_ms, "slowRecoveryMs": args.slow_recovery_ms,
            "startupMode": args.startup_mode,
            "fastReplayCachedMediaFileId": cached_media_file_id,
            "fastReplayFallbackCount": fast_start_fallback_count,
            "freshPlayerPerCombination": True,
            "fullAppRestartPerCombination": args.startup_mode == "isolated",
            "compiledDefaultsResetBeforeEachCombination": True,
            "ranking": summary, "results": results,
        }
        path = write_report(report, args.report)
        print("\n=== TUNING RANKING ===")
        for row in summary[:10]:
            print(f"{row['rank']:>2}. {row['label']}: {row['status']} {row['recoveryMs']} ms")
        print(f"REPORT: {path}")
        return 0 if all(x.get("status") != "INFRA_ERROR" for x in results) else 1
    finally:
        if not args.leave_running:
            try: call_dict(client, "dev_exit_session", {"stop_app": True}, timeout=30.0)
            except Exception: pass
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
