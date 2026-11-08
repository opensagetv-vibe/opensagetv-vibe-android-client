#!/usr/bin/env python3
"""Focused ordinary-Push stale-duration regression, using real MCP and stock APIs.

Reuses the selected device's current recording/settings. An optional guarded
update install preserves app data. Only the selected playback is interrupted;
no server restart, tuner, history reset or persistent preference change occurs.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import time

from mcp_seek_suite import MCPProcess, call_dict, initialize, tool_call
from sagetv_dev_mcp.config import load_test_environment
from sagetv_dev_mcp.core_mcp_api import CoreMcpApiClient


PREFERENCES = ("player", "streaming", "decoding", "gsyEngine", "audioOffsetMs",
               "audioPassthroughEnabled", "passthroughAudioOffsetEnabled", "clientId")


def relative_server_ms(state: dict) -> int:
    # ui.state's mediaTimeMs is absolute for recordings. Keep that separate
    # from both its relative raw clock and the decoder's zero-based Push epoch.
    return int(state["mediaTimeMs"]) - int(state["seekStartMs"])


def sample_verdict(first: dict, last: dict, elapsed: float) -> None:
    if not first.get("health_pushMode") or not last.get("health_pushMode"):
        raise RuntimeError("requested ordinary Push was not observed")
    delta = int(last["mediaTimeMs"]) - int(first["mediaTimeMs"])
    if abs(delta - elapsed * 1000) > 6000:
        raise RuntimeError(f"timeline did not follow elapsed playback: {delta}ms/{elapsed:.1f}s")
    for key in ("health_videoRendered", "health_audioRendered"):
        if int(last.get(key, -1)) <= int(first.get(key, -1)):
            raise RuntimeError(f"output did not advance: {key}")


def restore_playback(client, core, context, media_id, target, before, report):
    """An accepted seek after real EOS is not proof playback was restored."""
    current = core.ui_state(context)
    if current.get("media", {}).get("mediaFileId") != media_id:
        raise RuntimeError("source changed; never overwrite unrelated playback")
    if report.get("eosVerified"):
        # The test deliberately ended this exact file. Stock redundant Watch/
        # Seek can leave an ended MiniPlayer without a producer. Close only this
        # test-owned playback before reopening through existing public APIs.
        report["restoreCloseEnded"] = core.media_control(context, "stop")
        report["restoreEndedWatch"] = call_dict(client, "dev_play_media_file_id",
            {"media_file_id": media_id, "timeout_s": 45}, timeout=90)
        if not report["restoreEndedWatch"].get("passed"):
            raise RuntimeError("ended test recording did not reopen with A/V")
    report["restoreSeek"] = core.seek(context, target)
    started = time.monotonic()
    previous = None
    while time.monotonic() - started < 25:
        state = call_dict(client, "dev_player_state")
        expected = target + round((time.monotonic() - started) * 1000)
        if (state.get("health_playbackState") == 3 and state.get("health_isPlaying")
                and abs(int(state.get("mediaTimeMs", -1)) - expected) < 5000
                and previous is not None
                and all(int(state.get(k, -1)) > int(previous.get(k, -1))
                        for k in ("health_videoRendered", "health_audioRendered"))):
            report["restoredPlayback"] = state
            break
        previous = state
        time.sleep(1)
    if "restoredPlayback" not in report:
        raise RuntimeError("restore ACK did not produce advancing A/V at saved position")
    if not before.get("health_isPlaying"):
        report["restorePause"] = core.media_control(context, "pause")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--install-apk", default="")
    parser.add_argument("--force-push", action="store_true",
                        help="temporarily select Push, then restore exact preferences and connection")
    parser.add_argument("--seconds", type=int, default=150)
    parser.add_argument("--seek-latency", action="store_true",
                        help="also measure four isolated forward/back seeks on the same recording")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("use a new output path; never overwrite failure evidence")
    if not 130 <= args.seconds <= 300:
        parser.error("observation must cover at least two watchdog intervals, at most300s")
    report: dict = {"passed": False, "samples": [], "settingsChanged": False}
    client = MCPProcess()
    core = None
    context = ""
    original_target = None
    checkpointed = False
    try:
        initialize(client)
        report["adb"] = call_dict(client, "adb_connect")
        before = call_dict(client, "dev_player_state")
        report["before"] = before
        environment = load_test_environment()
        configured = environment.device()
        if str(before.get("clientId")) != str(configured.get("client_id")):
            raise RuntimeError("selected ADB device does not own the configured client identity")
        if not before.get("connected") or (not args.force_push and not before.get("health_pushMode")):
            raise RuntimeError("begin with the affected recording already playing through Push")
        server = environment.server_for_address(str(before["serverAddress"]))
        core = CoreMcpApiClient(server["core_mcp_base_url"], server["core_mcp_token"])
        context = core.resolve_context(before["clientId"])
        original = core.ui_state(context)
        report["serverBefore"] = original
        if original.get("dvd") or original.get("dvdMenu"):
            raise RuntimeError("DVD is deliberately outside this ordinary-Push gate")
        media_id = int(original["media"]["mediaFileId"])
        duration = int(original["durationMs"])
        original_target = relative_server_ms(original)
        if duration < 300000:
            raise RuntimeError("recording too short for safe near-tail and sustained observation")
        if args.install_apk:
            report["installation"] = call_dict(client, "install_dev_apk",
                                              {"apk_path": args.install_apk}, timeout=180)
        if args.force_push:
            report["checkpoint"] = call_dict(client, "dev_checkpoint_settings")
            checkpointed = True
            # Qualify the real UI selection with the original backend throughout
            # negotiation and playback. Never spoof another engine's capability
            # list to turn a mislabeled Pull session into a passing Push gate.
            report["temporaryConfig"] = call_dict(client, "dev_set_player_config",
                {"streaming": "push"})
            report["settingsChanged"] = True
            call_dict(client, "dev_exit_session", timeout=45)
        if args.install_apk or args.force_push:
            tool_call(client, "launch_dev_app", {}, timeout=45)
            report["connection"] = call_dict(client, "dev_connect_server",
                {"address": before["serverAddress"], "save": False}, timeout=45)
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                state = call_dict(client, "dev_player_state")
                if state.get("connected") and state.get("menuName"):
                    break
                time.sleep(1)
            # The existing tool handles the real STV Resume prompt and verifies
            # the same exact client context, actual source and advancing output.
            report["watch"] = call_dict(client, "dev_play_media_file_id",
                {"media_file_id": media_id, "timeout_s": 45}, timeout=90)
            if not report["watch"].get("passed"):
                raise RuntimeError("replacement APK did not resume healthy playback")
        if args.seek_latency:
            report["seekLatency"] = []
            # Reuse the reported slow request destinations when they fit this
            # recording. Otherwise keep the same ~28-second direction changes
            # safely away from either boundary. Do not change remote bindings.
            high = min(1357641, duration - 120000)
            low = high - 28258
            for target_ms in (high, low, high, low):
                previous = call_dict(client, "dev_player_events").get("events", [])
                after_sequence = max((int(e.get("sequence", 0)) for e in previous), default=0)
                start = time.monotonic()
                ack = core.seek(context, target_ms)
                row = {"targetMs": target_ms, "ack": ack}
                report["seekLatency"].append(row)
                while time.monotonic() - start < 15:
                    events = [e for e in call_dict(client, "dev_player_events").get("events", [])
                              if int(e.get("sequence", 0)) > after_sequence]
                    flush = next((e for e in events if e.get("event") == "server_flush_command"), None)
                    frame = next((e for e in events if e.get("event") == "first_video_frame"), None)
                    ready = next((e for e in events if e.get("event") == "playback_state_3"), None)
                    if flush and frame and ready:
                        row.update(hostObservedMs=round((time.monotonic() - start) * 1000),
                                   flushToFirstFrameMs=int(frame["monotonicMs"]) - int(flush["monotonicMs"]),
                                   flushToReadyMs=int(ready["monotonicMs"]) - int(flush["monotonicMs"]),
                                   events=events)
                        break
                    time.sleep(0.25)
                if "hostObservedMs" not in row:
                    raise RuntimeError("seek did not produce fresh Push FLUSH/first-frame/READY evidence")
                time.sleep(1)
                state = call_dict(client, "dev_player_state")
                row["after"] = state
                row["landingErrorMs"] = int(state["mediaTimeMs"]) - target_ms - round((time.monotonic() - start) * 1000)
                if not state.get("health_pushMode") or abs(row["landingErrorMs"]) > 5000:
                    raise RuntimeError("seek transport or target continuity did not match request")
                if row["hostObservedMs"] > 6500 or row["flushToReadyMs"] > 4000:
                    raise RuntimeError("Push seek remained slow; do not claim latency correction")
                print(f"Seek {target_ms}: FLUSH->frame {row['flushToFirstFrameMs']}ms, "
                      f"READY {row['flushToReadyMs']}ms, host <= {row['hostObservedMs']}ms", flush=True)
        # Fill a short final epoch, then FLUSH back to an earlier recording
        # position. This reproduces the old cached-duration precondition without
        # waiting for the entire recording to end or clearing watch history.
        report["tailSeek"] = core.seek(context, duration - 30000)
        time.sleep(5)
        report["tail"] = call_dict(client, "dev_player_state")
        report["tailHadFiniteDuration"] = int(report["tail"].get("health_durationMs", -1)) > 0
        target = max(60000, min(original_target, duration - 240000))
        report["seekTargetMs"] = target
        report["restartSeek"] = core.seek(context, target)
        time.sleep(5)
        first = call_dict(client, "dev_player_state")
        if not first.get("health_pushMode"):
            raise RuntimeError("capability negotiation selected Pull, not a Push qualification")
        started = time.monotonic()
        report["first"] = first
        sequence = max((int(e.get("sequence", 0)) for e in
                        call_dict(client, "dev_player_events").get("events", [])), default=0)
        while time.monotonic() - started < args.seconds:
            time.sleep(max(0, min(10, args.seconds - (time.monotonic() - started))))
            state = call_dict(client, "dev_player_state")
            server_state = core.ui_state(context)
            if int(server_state["media"]["mediaFileId"]) != media_id:
                raise RuntimeError("another file replaced the test recording")
            elapsed = time.monotonic() - started
            report["samples"].append({"elapsed": elapsed, "client": state,
                                       "server": server_state})
            sample_verdict(first, state, elapsed)
            error = abs(relative_server_ms(server_state) - int(state["mediaTimeMs"]))
            if error > 3000:
                raise RuntimeError(f"server/client timeline disagreement: {error}ms")
            print(f"{elapsed:.1f}s: Push A/V advancing, timeline gap={error}ms", flush=True)
        events = call_dict(client, "dev_player_events").get("events", [])
        report["events"] = events
        if any(int(e.get("sequence", 0)) > sequence and
               str(e.get("event", "")).startswith("player_error_") for e in events):
            raise RuntimeError("player error during sustained observation")
        last = report["samples"][-1]["client"]
        report["preferencesPreserved"] = all(before.get(k) == last.get(k) for k in PREFERENCES
                                              if not (args.force_push and k == "streaming"))
        if not report["preferencesPreserved"]:
            raise RuntimeError("user playback preferences/identity changed")
        report["picture"] = tool_call(client, "take_screenshot",
                                      {"label": args.output.stem + "-playing"})
        # Finite EOF remains real: only the invalid guessed-duration watchdog
        # changed. Require an actual decoder ENDED event from a final short
        # server-owned epoch, not an accepted seek or a clock near the tail.
        end_sequence = max((int(e.get("sequence", 0)) for e in events), default=0)
        report["eosSeek"] = core.seek(context, duration - 5000)
        deadline = time.monotonic() + 25
        while time.monotonic() < deadline:
            time.sleep(1)
            end_events = call_dict(client, "dev_player_events").get("events", [])
            if any(int(e.get("sequence", 0)) > end_sequence and
                   e.get("event") == "playback_state_4" for e in end_events):
                report["eosVerified"] = True
                report["eosEvents"] = end_events
                break
        if not report.get("eosVerified"):
            raise RuntimeError("server EOS did not produce an observed Media3 ENDED event")
        report["passed"] = True
    except Exception as exc:
        report["failure"] = str(exc)
        print(f"FAIL: {exc}", flush=True)
    finally:
        # Restore position through the same server-owned coordinate system, not
        # a local player seek or the absolute SageTV recording epoch.
        if core is not None and context and original_target is not None:
            try:
                if checkpointed:
                    report["restorePreferences"] = call_dict(client, "dev_restore_settings")
                    restored = call_dict(client, "dev_player_state")
                    report["allPreferencesRestored"] = all(
                        report["before"].get(k) == restored.get(k) for k in PREFERENCES)
                    if not report["allPreferencesRestored"]:
                        raise RuntimeError("private preference restore did not match original state")
                    call_dict(client, "dev_exit_session", timeout=45)
                    report["restoredConnection"] = call_dict(client, "dev_connect_server",
                        {"address": before["serverAddress"], "save": False}, timeout=45)
                    time.sleep(3)
                    report["restoredWatch"] = call_dict(client, "dev_play_media_file_id",
                        {"media_file_id": media_id, "timeout_s": 45}, timeout=90)
                    if not report["restoredWatch"].get("passed"):
                        raise RuntimeError("normal original-settings playback failed to restore")
                restore_playback(client, core, context, media_id, original_target,
                                 before, report)
            except Exception as exc:
                report["restoreFailure"] = str(exc)
                report["passed"] = False
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        client.close()
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
