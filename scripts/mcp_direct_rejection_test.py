#!/usr/bin/env python3
"""Bounded owned-seek rejection gate using disposable Copy slots, never Core changes."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
from mcp_seek_suite import MCPProcess, initialize, call_dict
from mcp_caption_test import mim_direct_owned

FIELDS = ("mediaTimeMs", "health_playerPositionMs", "health_capturedMonotonicMs",
          "health_videoRendered", "health_audioRendered", "health_isPlaying",
          "health_playerError", "mimDirectSessionState", "playbackSource", "playerClass")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--api-port", type=int, default=31910)
    args = parser.parse_args()
    client = MCPProcess()
    owned_tokens = []
    result = {"passed": False, "scope": "Controlled session-limit rejection; Copy only"}
    base = ""

    def request(path):
        req = urllib.request.Request(base + path, data=b"", method="POST")
        try:
            with urllib.request.urlopen(req, timeout=25) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            return error.code, json.loads(error.read())

    def snapshot():
        return call_dict(client, "dev_player_state", timeout=30)

    def compact(state):
        return {k: state.get(k) for k in FIELDS}

    try:
        initialize(client)
        current = snapshot()
        if not mim_direct_owned(current, "copy"):
            raise RuntimeError("A proven owned Copy session must already be playing")
        base = "http://" + str(current["serverAddress"]) + ":" + str(args.api_port)
        with urllib.request.urlopen(base + "/v1/capabilities", timeout=8) as response:
            capabilities = json.load(response)
        if capabilities.get("mimDirect", {}).get("activeSessions") != 1:
            raise RuntimeError("Another Direct owner exists; do not occupy shared slots")
        # Establish a nonzero HLS source epoch before rejection. This uses the
        # stock-compatible public VideoFrame Seek API, not a private event.
        landed = call_dict(client, "dev_server_seek_time", {
            "target_ms": 60000, "tolerance_ms": 5000, "timeout_s": 30,
        }, timeout=45)
        if not landed.get("passed"):
            raise RuntimeError("Initial owned60-second seek did not pass")
        current = snapshot()
        if not mim_direct_owned(current, "copy"):
            raise RuntimeError("Initial seek lost Copy ownership")
        origin = int(current["mediaTimeMs"]) - int(current["health_playerPositionMs"])
        if origin < 50000:
            raise RuntimeError("Nonzero retained source epoch was not established")
        # The verified provider has four bounded slots. Occupy only the three
        # remaining slots with the copyright-free indexed fixture. Never touch
        # the Android-owned token or publish any token in evidence/log output.
        for _ in range(3):
            status, response = request("/v1/direct/start?" + urllib.parse.urlencode({
                "source": "/var/media/OpenSageTV_Vibe_Tests/VibeSeekTest-1080i-MPEG2-AC3-CC.ts",
                "mode": "copy", "deinterlace": "off", "active": "false", "startMs": 0,
            }))
            if status != 200:
                raise RuntimeError("Disposable Copy slot creation failed HTTP" + str(status))
            owned_tokens.append(response["sessionToken"])
        before = snapshot()
        result["before"] = compact(before)
        # Backend-isolation injection executes the same player seek callback.
        # No remote input or production Core configuration is rewritten.
        call_dict(client, "dev_local_seek_absolute", {"target_ms": 150000}, timeout=30)
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline:
            after = snapshot()
            if after.get("mimDirectSessionState") == "restart_failed_session_retained_http_409_direct_session_limit":
                break
            time.sleep(.2)
        else:
            raise RuntimeError("Expected safe HTTP409 session-limit evidence was not observed")
        time.sleep(2)
        after = snapshot()
        result["after"] = compact(after)
        progress = int(after["mediaTimeMs"]) - int(before["mediaTimeMs"])
        elapsed = int(after["health_capturedMonotonicMs"]) - int(before["health_capturedMonotonicMs"])
        result["progressMs"] = progress
        result["elapsedMs"] = elapsed
        if abs(progress - elapsed) > 5000:
            raise RuntimeError("Rejected seek changed the retained representation's clock")
        if (int(after["health_videoRendered"]) <= int(before["health_videoRendered"])
                or int(after["health_audioRendered"]) <= int(before["health_audioRendered"])
                or not after.get("health_isPlaying") or after.get("health_playerError")):
            raise RuntimeError("Rejected seek did not preserve advancing A/V")
        result["passed"] = True
    except Exception as error:
        result["error"] = str(error)
    finally:
        # Only tokens created by this gate are retired. No global stop/prune.
        result["temporaryTeardownStatuses"] = []
        for token in owned_tokens:
            try:
                status, _ = request("/v1/direct/teardown?" + urllib.parse.urlencode({"token": token}))
                result["temporaryTeardownStatuses"].append(status)
                if status != 200:
                    result["passed"] = False
            except Exception:
                result["passed"] = False
                result["cleanupFailed"] = True
        client.close()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result), flush=True)
    return 0 if result["passed"] else 1

if __name__ == "__main__":
    raise SystemExit(main())
