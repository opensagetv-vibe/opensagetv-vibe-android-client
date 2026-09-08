from __future__ import annotations
from dataclasses import dataclass, field
from pathlib import Path
import re
import select
import shlex
import shutil
import subprocess
import threading
import time
from typing import Any, Iterable


TELEMETRY_TAG = "SageTVDevTelemetry"
TELEMETRY_FILE = "sagetv_dev_player_telemetry.log"
DEV_CONTROL_ACTION = "opensagetv.vibe.miniclient.DEBUG_CONTROL"
DEV_CONTROL_COMPONENT = "opensagetv.vibe.miniclient.android.tv.debug.DevTestReceiver"
def normalize_streaming_config(value: str) -> str:
    """Map user-facing streaming selection to the stable Android preference value."""
    key = str(value or "").strip().lower()
    aliases = {
        "": "",
        "push": "dynamic",
        "dynamic": "dynamic",
        "push/dynamic": "dynamic",
        "pull": "pull",
        "fixed": "fixed",
        "smb": "smb_direct",
        "smb_direct": "smb_direct",
        "smb direct": "smb_direct",
        "smb_auto": "smb_auto",
        "smb auto": "smb_auto",
    }
    if key not in aliases:
        raise ValueError(f"unsupported streaming selection: {value}")
    return aliases[key]


def normalize_decoding_config(value: str) -> str:
    """Map user-facing decoding selection to the stable Android preference value."""
    key = str(value or "").strip().lower()
    aliases = {
        "": "",
        "hardware": "hardware",
        "software": "software",
        "fallback": "hardware_preferred",
        "hardware_preferred": "hardware_preferred",
        "hardware-preferred": "hardware_preferred",
        "hardware preferred": "hardware_preferred",
    }
    if key not in aliases:
        raise ValueError(f"unsupported decoding selection: {value}")
    return aliases[key]


def _typed_telemetry_value(value: str) -> Any:
    lower = value.lower()
    if lower == "true":
        return True
    if lower == "false":
        return False
    if lower == "null":
        return None
    try:
        return int(value)
    except ValueError:
        pass
    try:
        return float(value)
    except ValueError:
        return value


def parse_telemetry_line(line: str) -> dict[str, Any] | None:
    marker = TELEMETRY_TAG + ":"
    if marker in line:
        payload = line.split(marker, 1)[1].strip()
    else:
        # App-private telemetry files contain the structured payload directly,
        # without the Android logcat prefix/tag.
        payload = line.strip()
        if not payload.startswith("schema="):
            return None
    event: dict[str, Any] = {}
    for token in payload.split():
        if "=" not in token:
            continue
        key, value = token.split("=", 1)
        if key:
            event[key] = _typed_telemetry_value(value)
    event["raw"] = line
    return event if event else None


def _parse_dev_control_data(data: str) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for token in data.split(";"):
        if "=" not in token:
            continue
        key, value = token.split("=", 1)
        if key:
            out[key] = _typed_telemetry_value(value)
    return out


def parse_broadcast_result(text: str) -> dict[str, Any]:
    """Parse `am broadcast` completion output from the DevTestReceiver."""
    match = re.search(r'Broadcast completed:\s*result=(-?\d+)(?:,\s*data=\"(.*)\")?', text)
    if not match:
        raise RuntimeError(f"Dev control broadcast did not return a result: {text.strip()}")
    code = int(match.group(1))
    data = (match.group(2) or "").replace('\\"', '"')
    result = _parse_dev_control_data(data)
    result["result_code"] = code
    result["raw_broadcast"] = text.strip()
    if code != 1 or result.get("ok") is not True:
        raise RuntimeError(f"Dev control failed: {data or text.strip()}")
    return result

KEYS = {
    "UP": "KEYCODE_DPAD_UP", "DOWN": "KEYCODE_DPAD_DOWN", "LEFT": "KEYCODE_DPAD_LEFT",
    "RIGHT": "KEYCODE_DPAD_RIGHT", "SELECT": "KEYCODE_DPAD_CENTER", "CENTER": "KEYCODE_DPAD_CENTER",
    "BACK": "KEYCODE_BACK", "HOME": "KEYCODE_HOME", "MENU": "KEYCODE_MENU",
    "ENTER": "KEYCODE_ENTER", "NEXT": "KEYCODE_ENTER",
    "PLAY": "KEYCODE_MEDIA_PLAY", "PAUSE": "KEYCODE_MEDIA_PAUSE", "PLAY_PAUSE": "KEYCODE_MEDIA_PLAY_PAUSE",
    "FAST_FORWARD": "KEYCODE_MEDIA_FAST_FORWARD", "FF": "KEYCODE_MEDIA_FAST_FORWARD",
    "REWIND": "KEYCODE_MEDIA_REWIND", "RW": "KEYCODE_MEDIA_REWIND", "STOP": "KEYCODE_MEDIA_STOP",
}

@dataclass
class AdbClient:
    serial: str
    dev_package: str
    adb: str = "adb"
    aapt: str = ""
    _shell_proc: subprocess.Popen | None = field(default=None, init=False, repr=False)
    _shell_lock: threading.RLock = field(default_factory=threading.RLock, init=False, repr=False)
    _shell_command_id: int = field(default=0, init=False, repr=False)
    _shell_restart_count: int = field(default=0, init=False, repr=False)

    def _base(self, device: bool = True) -> list[str]:
        return [self.adb, "-s", self.serial] if device else [self.adb]

    def run(self, args: Iterable[str], *, timeout: float = 30, check: bool = True, device: bool = True) -> subprocess.CompletedProcess[str]:
        cmd = self._base(device) + list(args)
        cp = subprocess.run(cmd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and cp.returncode:
            raise RuntimeError(f"Command failed ({cp.returncode}): {' '.join(cmd)}\n{cp.stderr.strip()}")
        return cp

    def _persistent_shell_alive(self) -> bool:
        return self._shell_proc is not None and self._shell_proc.poll() is None

    def _start_persistent_shell(self) -> None:
        if self._persistent_shell_alive():
            return
        self._stop_persistent_shell()
        cmd = self._base(True) + ["shell"]
        self._shell_proc = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            bufsize=0,
        )
        self._shell_restart_count += 1

    def _stop_persistent_shell(self) -> None:
        proc = self._shell_proc
        self._shell_proc = None
        if proc is None:
            return
        try:
            if proc.stdin:
                proc.stdin.close()
        except Exception:
            pass
        try:
            proc.terminate()
            proc.wait(timeout=1.0)
        except Exception:
            try:
                proc.kill()
                proc.wait(timeout=1.0)
            except Exception:
                pass
        finally:
            try:
                if proc.stdout:
                    proc.stdout.close()
            except Exception:
                pass

    def close(self) -> None:
        with self._shell_lock:
            self._stop_persistent_shell()

    def persistent_shell_status(self) -> dict[str, Any]:
        proc = self._shell_proc
        return {
            "persistentShell": self._persistent_shell_alive(),
            "persistentShellPid": proc.pid if proc is not None and proc.poll() is None else None,
            "persistentShellRestarts": self._shell_restart_count,
            "persistentShellCommands": self._shell_command_id,
        }

    def _persistent_shell_command(self, command: str, *, timeout: float = 30) -> tuple[str, int]:
        if not str(command).strip():
            return "", 0
        with self._shell_lock:
            self._start_persistent_shell()
            proc = self._shell_proc
            if proc is None or proc.stdin is None or proc.stdout is None:
                raise RuntimeError("ADB persistent shell failed to start")
            self._shell_command_id += 1
            marker = f"__SAGETV_MCP_DONE_{self._shell_command_id}_{time.monotonic_ns()}__"
            # Runtime commands are serialized by _shell_lock.  Use byte-level framing
            # instead of TextIO.readline/select because TextIO buffering can hide the
            # marker after the first line and make a completed command look hung.
            framed = command + "; __mcp_rc=$?; printf '\\n" + marker + ":%s\\n' \"$__mcp_rc\"\n"
            try:
                proc.stdin.write(framed.encode("utf-8"))
                proc.stdin.flush()
            except (BrokenPipeError, OSError) as exc:
                self._stop_persistent_shell()
                raise RuntimeError(f"ADB persistent shell write failed: {exc}") from exc

            deadline = time.monotonic() + max(0.1, float(timeout))
            buffer = bytearray()
            token = b"\n" + marker.encode("utf-8") + b":"
            while True:
                marker_at = buffer.find(token)
                if marker_at >= 0:
                    status_start = marker_at + len(token)
                    status_end = buffer.find(b"\n", status_start)
                    if status_end >= 0:
                        try:
                            rc = int(bytes(buffer[status_start:status_end]).decode("ascii", errors="strict").strip())
                        except (ValueError, UnicodeDecodeError):
                            rc = 1
                        output = bytes(buffer[:marker_at]).decode("utf-8", errors="replace")
                        return output, rc

                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    self._stop_persistent_shell()
                    raise subprocess.TimeoutExpired(self._base(True) + ["shell", command], timeout)
                ready, _, _ = select.select([proc.stdout], [], [], min(0.25, remaining))
                if not ready:
                    if proc.poll() is not None:
                        self._stop_persistent_shell()
                        raise RuntimeError(f"ADB persistent shell exited while running: {command}")
                    continue
                chunk = proc.stdout.read(4096)
                if not chunk:
                    self._stop_persistent_shell()
                    raise RuntimeError(f"ADB persistent shell closed while running: {command}")
                buffer.extend(chunk)

    def connect(self) -> str:
        result = self.run(["connect", self.serial], device=False).stdout.strip()
        # Establish one reusable device shell for the MCP server lifetime.
        with self._shell_lock:
            self._start_persistent_shell()
        return result

    def devices(self) -> str:
        return self.run(["devices", "-l"], device=False).stdout

    def shell(self, command: str, *, timeout: float = 30, check: bool = True) -> str:
        output, rc = self._persistent_shell_command(command, timeout=timeout)
        if check and rc:
            raise RuntimeError(f"Persistent ADB shell command failed ({rc}): {command}\n{output.strip()}")
        return output

    def device_info(self) -> dict[str, str]:
        props = {}
        for key in ["ro.product.manufacturer", "ro.product.model", "ro.product.device", "ro.build.version.release", "ro.build.version.sdk", "ro.build.fingerprint"]:
            props[key] = self.shell(f"getprop {key}").strip()
        return props

    def _ensure_dev_package(self, package: str) -> None:
        if package != self.dev_package:
            raise PermissionError(f"Refusing package operation on {package!r}; MCP is restricted to {self.dev_package!r}")
        if package.startswith("jvl.sage.miniclient"):
            raise PermissionError("Refusing operation on upstream/production SageTV package namespace")

    def package_info(self) -> str:
        self._ensure_dev_package(self.dev_package)
        return self.shell(f"dumpsys package {self.dev_package}", timeout=45)

    def force_stop(self) -> str:
        self._ensure_dev_package(self.dev_package)
        return self.shell(f"am force-stop {self.dev_package}")

    def _resolve_launcher_component(self) -> str:
        """Resolve the TV launcher first, then the ordinary phone launcher."""
        package = shlex.quote(self.dev_package)
        for category in (
            "android.intent.category.LEANBACK_LAUNCHER",
            "android.intent.category.LAUNCHER",
        ):
            output = self.shell(
                "cmd package resolve-activity --brief "
                "-a android.intent.action.MAIN "
                f"-c {category} "
                + package,
                timeout=15,
                check=False,
            ).strip()
            for line in reversed(output.splitlines()):
                candidate = line.strip()
                if "/" in candidate and not candidate.lower().startswith("no activity"):
                    return candidate
        return ""

    def launch(self) -> str:
        """Wake Fire TV, return HOME, then launch the Dev app directly.

        Fire OS ``monkey`` is not reliable on every device/build (it can return 251
        when SYS_KEYS reports no physical keys). Prefer Android's normal Activity
        manager and keep monkey only as a last-resort fallback.
        """
        self._ensure_dev_package(self.dev_package)
        self.wake()
        component = self._resolve_launcher_component()
        if component:
            command = f"am start -W -n {shlex.quote(component)}"
        else:
            command = (
                "am start -W -a android.intent.action.MAIN "
                "-c android.intent.category.LAUNCHER -p "
                + shlex.quote(self.dev_package)
            )
        try:
            return self.shell(command, timeout=30)
        except Exception as direct_error:
            try:
                return self.shell(
                    f"monkey -p {shlex.quote(self.dev_package)} -c android.intent.category.LAUNCHER 1",
                    timeout=30,
                )
            except Exception as monkey_error:
                raise RuntimeError(
                    "Unable to launch Dev app after WAKEUP+HOME. "
                    f"Direct Activity launch failed: {direct_error}; monkey fallback failed: {monkey_error}"
                ) from monkey_error

    def app_status(self) -> dict[str, Any]:
        """Return process/foreground state without starting the Dev package.

        Fire OS can transiently return no PID from ``pidof`` even while an Activity from
        the package is resumed.  Treat a resumed Dev Activity as authoritative running
        evidence and fall back to ``ps -A`` for background/process-name variants.
        """
        self._ensure_dev_package(self.dev_package)
        pid_text = self.shell(f"pidof {shlex.quote(self.dev_package)}", check=False).strip()
        pids = [item for item in pid_text.split() if item.isdigit()]
        pid_source = "pidof" if pids else "none"

        if not pids:
            ps_text = self.shell("ps -A", timeout=30, check=False)
            for line in ps_text.splitlines():
                parts = line.split()
                if not parts:
                    continue
                process_name = parts[-1]
                if process_name != self.dev_package and not process_name.startswith(self.dev_package + ":"):
                    continue
                pid = next((item for item in parts[1:] if item.isdigit()), "")
                if pid and pid not in pids:
                    pids.append(pid)
            if pids:
                pid_source = "ps"

        activity_text = self.shell("dumpsys activity activities", timeout=45, check=False)
        resumed = ""
        for line in activity_text.splitlines():
            if "mResumedActivity" in line or "topResumedActivity" in line:
                resumed = line.strip()
                if self.dev_package in resumed:
                    break
        foreground = bool(resumed and self.dev_package in resumed)
        package_text = self.shell(
            f"dumpsys package {shlex.quote(self.dev_package)}", timeout=45, check=False
        )
        force_stopped = any("stopped=true" in line for line in package_text.splitlines())
        # An explicit debug broadcast can start the Dev receiver/process while
        # Fire OS leaves PackageUserState.stopped set from an earlier force-stop.
        # A real PID is therefore authoritative running evidence. Callers that
        # force-stop must wait for the PID to disappear instead of guessing from
        # the package bit.
        running = foreground or bool(pids)
        running_source = (
            "resumedActivity" if foreground else
            (pid_source if running else ("packageStopped" if force_stopped else "none"))
        )
        return {
            "package": self.dev_package,
            "running": running,
            "runningSource": running_source,
            "pids": pids,
            "foreground": foreground,
            "resumedActivity": resumed,
            "forceStopped": force_stopped,
        }

    def wake(self) -> dict[str, Any]:
        """Wake the configured Android/Fire TV without launching an application."""
        def power_summary() -> str:
            raw = self.shell("dumpsys power", timeout=30)
            return " | ".join(
                line.strip() for line in raw.splitlines()
                if "mWakefulness=" in line or "Display Power: state=" in line
            )

        before = power_summary()
        # HOME itself wakes many Fire TV builds; WAKEUP first keeps this portable to
        # standard Android devices too. HOME also exits Dreaming/screensaver state and
        # leaves a deterministic foreground launcher before the MiniClient starts.
        self.shell("input keyevent KEYCODE_WAKEUP", check=False)
        self.shell("wm dismiss-keyguard", timeout=15, check=False)
        self.shell("input keyevent KEYCODE_HOME", check=False)
        time.sleep(0.35)
        after = power_summary()
        return {
            "before": before,
            "after": after,
            "wakeKey": "KEYCODE_WAKEUP",
            "homeKey": "KEYCODE_HOME",
        }

    def request_graceful_exit(self) -> dict[str, Any]:
        """Ask the running Dev app to disconnect and close its task without force-stop."""
        before = self.app_status()
        result: dict[str, Any] = {"before": before, "requested": False}
        if not before["running"]:
            result["after"] = before
            return result
        result["requested"] = True
        try:
            result["exitSession"] = self.exit_session()
        except Exception as exc:
            result["exitError"] = str(exc)
        # exit_session returns to MainActivity; BACK requests a normal Activity finish.
        time.sleep(0.35)
        try:
            result["back"] = self.key("BACK")
        except Exception as exc:
            result["backError"] = str(exc)
        time.sleep(0.5)
        result["after"] = self.app_status()
        return result

    def prepare_clean_start(self, *, wake: bool = True, graceful_timeout_s: float = 2.0) -> dict[str, Any]:
        """Ensure the Dev app is stopped before a deterministic automation launch.

        If it is running, request a normal session/task exit first. If the process remains
        alive after the grace period, force-stop it and verify again.
        """
        self._ensure_dev_package(self.dev_package)
        graceful_timeout_s = max(0.25, min(float(graceful_timeout_s), 10.0))
        result: dict[str, Any] = {"initial": self.app_status(), "forceStopUsed": False}
        final_status: dict[str, Any] | None = None
        if result["initial"]["running"]:
            result["gracefulExit"] = self.request_graceful_exit()
            deadline = time.monotonic() + graceful_timeout_s
            status = self.app_status()
            while status["running"] and time.monotonic() < deadline:
                time.sleep(0.25)
                status = self.app_status()
            result["afterGraceful"] = status
            if status["running"]:
                result["forceStopUsed"] = True
                result["forceStop"] = self.force_stop()
                deadline = time.monotonic() + 15.0
                status = self.app_status()
                while status["running"] and time.monotonic() < deadline:
                    time.sleep(0.25)
                    status = self.app_status()
                final_status = status
        result["stopped"] = final_status if final_status is not None else self.app_status()
        force_stop_committed = bool(
            result["forceStopUsed"]
            and result["stopped"].get("forceStopped")
            and not result["stopped"].get("foreground")
        )
        result["terminationPending"] = bool(
            force_stop_committed and result["stopped"].get("running")
        )
        if result["stopped"]["running"] and not force_stop_committed:
            raise RuntimeError(f"Dev app is still running after force-stop: {result['stopped']}")
        if wake:
            result["wake"] = self.wake()
        result["readyToLaunch"] = True
        return result

    def uninstall(self) -> str:
        self._ensure_dev_package(self.dev_package)
        return self.run(["uninstall", self.dev_package], timeout=60).stdout.strip()

    def key(self, name: str) -> str:
        key = name.strip().upper()
        code = KEYS.get(key, key if key.startswith("KEYCODE_") else "")
        if not code:
            raise ValueError(f"Unsupported key {name!r}; supported aliases: {', '.join(sorted(KEYS))}")
        return self.shell(f"input keyevent {code}").strip()

    def key_sequence(self, keys: list[str], delay_ms: int = 300) -> list[dict[str, str]]:
        delay_ms = max(0, min(delay_ms, 10000))
        out = []
        for key in keys:
            out.append({"key": key, "result": self.key(key)})
            time.sleep(delay_ms / 1000.0)
        return out

    def input_text(self, text: str) -> str:
        """Inject normal keyboard text into the currently focused Android view.

        Android's ``input text`` command uses ``%s`` to represent a space. This path
        generates normal key input for the MiniClient view and therefore follows the
        same SageTV text-input path as the Android/Fire TV keyboard.
        """
        value = str(text)
        if not value:
            raise ValueError("text must not be empty")
        if "\n" in value or "\r" in value:
            raise ValueError("text must be a single line")
        encoded = value.replace("%", "%25").replace(" ", "%s")
        return self.shell(f"input text {shlex.quote(encoded)}").strip()

    def input_text_paced(self, text: str, char_delay_ms: int = 100) -> dict[str, Any]:
        """Inject paced text using one ADB shell session.

        The previous implementation launched a separate ``adb shell`` process for every
        character, so a requested 10 ms delay was dominated by host/ADB round-trip
        overhead.  Build one device-side shell command instead: Android still receives
        the same ``input text`` / SPACE events, but the sleeps happen on the Fire TV and
        there is only one ADB transport round trip.
        """
        value = str(text)
        if not value:
            raise ValueError("text must not be empty")
        if "\n" in value or "\r" in value:
            raise ValueError("text must be a single line")
        delay_ms = max(0, min(int(char_delay_ms), 2000))
        delay_s = delay_ms / 1000.0
        commands: list[str] = []
        events: list[dict[str, Any]] = []
        for index, ch in enumerate(value):
            if ch == " ":
                action = "input keyevent KEYCODE_SPACE"
                path = "keyevent_space"
            else:
                encoded = ch.replace("%", "%25")
                action = f"input text {shlex.quote(encoded)}"
                path = "input_text_char"
            commands.append(action)
            events.append({"index": index, "char": ch, "path": path})
            if delay_ms > 0 and index + 1 < len(value):
                commands.append(f"sleep {delay_s:.3f}")

        device_command = "; ".join(commands)
        timeout_s = max(30.0, 10.0 + (len(value) * delay_s))
        started = time.monotonic()
        raw = self.shell(device_command, timeout=timeout_s).strip()
        elapsed_ms = int(round((time.monotonic() - started) * 1000.0))
        effective_char_ms = (elapsed_ms / max(1, len(value) - 1)) if len(value) > 1 else 0.0
        return {
            "ok": True,
            "charsRequested": len(value),
            "charsIssued": len(value),
            "charDelayMs": delay_ms,
            "elapsedMs": elapsed_ms,
            "effectiveCharMs": round(effective_char_ms, 1),
            "inputPath": "android_os_input_text_paced_single_shell",
            "events": events,
            "raw": raw,
        }

    def dev_control(self, op: str, *, foreground: bool = True, timeout_s: float | None = None, **extras: Any) -> dict[str, Any]:
        """Invoke the debug-build-only MiniClient control receiver through ADB.

        Short snapshot/config commands use a foreground broadcast for responsiveness. Long-running
        playback health checks deliberately use a normal explicit broadcast because Android gives
        foreground broadcasts a much tighter execution/ANR window, which can terminate or destabilize
        a slow Pull-mode test before its recovery probe finishes.
        """
        self._ensure_dev_package(self.dev_package)
        component = f"{self.dev_package}/{DEV_CONTROL_COMPONENT}"
        parts = ["am", "broadcast"]
        if foreground:
            parts.append("--receiver-foreground")
        parts.extend([
            "-a", DEV_CONTROL_ACTION,
            "-n", component,
            "--es", "op", str(op),
        ])
        for key, value in extras.items():
            if value is None or value == "":
                continue
            parts.extend(["--es", str(key), str(value)])
        command = " ".join(shlex.quote(part) for part in parts)

        # Long-running media observations need the ADB shell/broadcast transport to
        # outlive the requested per-step watchdog.  A fixed 90-second shell timeout
        # silently truncated 180-second watchdog requests.  Derive a safe transport
        # timeout from the requested recovery/verify/settle window unless the caller
        # explicitly supplies one.  Each dev_control call gets a fresh independent
        # timeout, so watchdog time is per media step rather than shared by a suite.
        if timeout_s is None:
            timeout_s = 90.0
            try:
                recovery_ms = int(extras.get("recovery_timeout_ms", 0) or 0)
                verify_ms = int(extras.get("verify_playback_ms", 0) or 0)
                settle_ms = int(extras.get("settle_ms", 0) or 0)
            except (TypeError, ValueError):
                recovery_ms = verify_ms = settle_ms = 0
            if recovery_ms > 0:
                timeout_s = max(timeout_s, (recovery_ms + verify_ms + settle_ms) / 1000.0 + 30.0)
        return parse_broadcast_result(self.shell(command, timeout=float(timeout_s)))

    def player_state_snapshot(self) -> dict[str, Any]:
        return self.dev_control("snapshot")

    def codec_capabilities(self) -> dict[str, Any]:
        """Collect the debug APK's on-device MediaCodec profile on demand."""
        return self.dev_control("codec_capabilities")

    def smb_profile(self, operation: str, *, name: str = "", overwrite: bool = False) -> dict[str, Any]:
        normalized = str(operation).strip().lower()
        if normalized not in {"list", "save", "load", "delete"}:
            raise ValueError("profile operation must be list/save/load/delete")
        if normalized != "list" and not str(name).strip():
            raise ValueError("profile name is required")
        return self.dev_control(
            "profile_" + normalized,
            foreground=False,
            timeout_s=45.0,
            name=str(name).strip(),
            overwrite="true" if overwrite else "false",
        )

    def open_smb_profile_settings(self) -> dict[str, Any]:
        return self.dev_control("profile_ui", foreground=False)

    def player_event_traps(self) -> dict[str, Any]:
        return self.dev_control("events")

    def clear_player_event_traps(self) -> dict[str, Any]:
        return self.dev_control("events_clear")

    def playback_trace_status(self) -> dict[str, Any]:
        return self.dev_control("trace_status")

    def clear_playback_trace(self) -> dict[str, Any]:
        return self.dev_control("trace_clear")

    def set_playback_trace_enabled(self, enabled: bool) -> dict[str, Any]:
        return self.dev_control("trace_enable", enabled="true" if enabled else "false")

    def export_playback_trace(self, output: Path) -> Path:
        """Export the bounded debug-app trace oldest-first without storage permission."""
        self._ensure_dev_package(self.dev_package)
        trace_dir = "files/diagnostics"
        parts: list[str] = []
        for suffix in (".3", ".2", ".1", ""):
            name = f"playback-trace.jsonl{suffix}"
            data = self.shell(
                f"run-as {shlex.quote(self.dev_package)} cat {trace_dir}/{name} 2>/dev/null",
                check=False,
            )
            if data:
                parts.append(data.rstrip("\n"))
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text("\n".join(parts) + ("\n" if parts else ""), encoding="utf-8")
        return output

    def set_datasource_capture(self, enabled: bool) -> dict[str, Any]:
        """Enable the bounded raw Push-byte capture for the next playback."""
        return self.dev_control(
            "capture_datasource",
            enabled="true" if enabled else "false",
        )

    def set_player_config(
        self,
        *,
        player: str = "",
        streaming: str = "",
        decoding: str = "",
        gsy_engine: str = "",
        gsy_system_probe: bool | None = None,
        preferred_audio_language: str = "",
        preferred_subtitle_language: str = "",
        preferred_caption_standard: str = "",
        preferred_caption_service: int | str = "",
        fixed_encoding_preference: str = "",
        fixed_encoding_format: str = "",
        fixed_video_bitrate_kbps: int | str = "",
        fixed_video_fps: str = "",
        fixed_key_frame_interval: int | str = "",
        fixed_use_b_frames: bool | str | None = None,
        fixed_video_resolution: str = "",
        fixed_audio_codec: str = "",
        fixed_audio_bitrate_kbps: int | str = "",
        fixed_audio_channels: str = "",
        fixed_remuxing_preference: str = "",
        fixed_remuxing_format: str = "",
        smb_mappings: str = "",
        smb_username: str = "",
        smb_password: str = "",
        smb_domain: str = "",
        smb_clear_auth: bool = False,
        smb_profile_directory: str = "",
        smb_profile_username: str = "",
        smb_profile_password: str = "",
        smb_profile_domain: str = "",
        smb_profile_clear_auth: bool = False,
        keep_session_in_background: bool | None = None,
        resume_background_playback: bool | None = None,
        background_session_timeout_seconds: int | str | None = "",
        disc_playback_policy: str = "",
        disc_skip_menus: bool | None = None,
        disc_skip_previews: bool | None = None,
        disc_compatibility_fallback: bool | None = None,
        disc_mpeg2_timestamp_repair: str = "",
        wait_for_playback_before_first_osd: bool | None = None,
    ) -> dict[str, Any]:
        requested_streaming = str(streaming or "").strip().lower()
        requested_decoding = str(decoding or "").strip().lower()
        applied_streaming = normalize_streaming_config(streaming)
        applied_decoding = normalize_decoding_config(decoding)
        extras = {
            "player": player,
            "streaming": applied_streaming,
            "decoding": applied_decoding,
            "gsy_engine": gsy_engine,
            "gsy_system_probe": (
                "true" if gsy_system_probe else "false"
            ) if isinstance(gsy_system_probe, bool) else "",
            "preferred_audio_language": preferred_audio_language,
            "preferred_subtitle_language": preferred_subtitle_language,
            "preferred_caption_standard": preferred_caption_standard,
            "preferred_caption_service": preferred_caption_service,
            "fixed_encoding_preference": fixed_encoding_preference,
            "fixed_encoding_format": fixed_encoding_format,
            "fixed_video_bitrate_kbps": fixed_video_bitrate_kbps,
            "fixed_video_fps": fixed_video_fps,
            "fixed_key_frame_interval": fixed_key_frame_interval,
            "fixed_use_b_frames": ("true" if fixed_use_b_frames else "false") if isinstance(fixed_use_b_frames, bool) else fixed_use_b_frames,
            "fixed_video_resolution": fixed_video_resolution,
            "fixed_audio_codec": fixed_audio_codec,
            "fixed_audio_bitrate_kbps": fixed_audio_bitrate_kbps,
            "fixed_audio_channels": fixed_audio_channels,
            "fixed_remuxing_preference": fixed_remuxing_preference,
            "fixed_remuxing_format": fixed_remuxing_format,
            "smb_mappings": smb_mappings,
            "smb_username": smb_username,
            "smb_password": smb_password,
            "smb_domain": smb_domain,
            "smb_clear_auth": "true" if smb_clear_auth else "",
            "smb_profile_directory": smb_profile_directory,
            "smb_profile_username": smb_profile_username,
            "smb_profile_password": smb_profile_password,
            "smb_profile_domain": smb_profile_domain,
            "smb_profile_clear_auth": "true" if smb_profile_clear_auth else "",
            "keep_session_in_background": (
                "true" if keep_session_in_background else "false"
            ) if isinstance(keep_session_in_background, bool) else "",
            "resume_background_playback": (
                "true" if resume_background_playback else "false"
            ) if isinstance(resume_background_playback, bool) else "",
            "background_session_timeout_seconds": (
                max(0, min(int(background_session_timeout_seconds), 86400))
                if background_session_timeout_seconds not in (None, "") else ""
            ),
            "disc_playback_policy": disc_playback_policy,
            "disc_skip_menus": (
                "true" if disc_skip_menus else "false"
            ) if isinstance(disc_skip_menus, bool) else "",
            "disc_skip_previews": (
                "true" if disc_skip_previews else "false"
            ) if isinstance(disc_skip_previews, bool) else "",
            "disc_compatibility_fallback": (
                "true" if disc_compatibility_fallback else "false"
            ) if isinstance(disc_compatibility_fallback, bool) else "",
            "disc_mpeg2_timestamp_repair": disc_mpeg2_timestamp_repair,
            "wait_for_playback_before_first_osd": (
                "true" if wait_for_playback_before_first_osd else "false"
            ) if isinstance(wait_for_playback_before_first_osd, bool) else "",
        }
        result = self.dev_control("config", **extras)
        if requested_streaming:
            result["requestedStreaming"] = requested_streaming
            result["appliedStreaming"] = applied_streaming
        if requested_decoding:
            result["requestedDecoding"] = requested_decoding
            result["appliedDecoding"] = applied_decoding
        return result

    def get_player_tuning(self) -> dict[str, Any]:
        return self.dev_control("tuning_get")

    def get_client_id(self) -> dict[str, Any]:
        return self.dev_control("client_id_get")

    def set_client_id(self, *, value: str = "", generate: bool = False) -> dict[str, Any]:
        return self.dev_control(
            "client_id_set",
            value=value,
            generate="true" if generate else "false",
        )

    def set_player_tuning(
        self,
        *,
        reset: bool = False,
        media3_ts_search_multiplier: int | str = "",
        exo2_ts_search_multiplier: int | str = "",
        media3_pull_read_kb: int | str = "",
        exo2_pull_read_kb: int | str = "",
        media3_min_buffer_ms: int | str = "",
        media3_max_buffer_ms: int | str = "",
        media3_playback_buffer_ms: int | str = "",
        media3_rebuffer_ms: int | str = "",
        exo2_min_buffer_ms: int | str = "",
        exo2_max_buffer_ms: int | str = "",
        exo2_playback_buffer_ms: int | str = "",
        exo2_rebuffer_ms: int | str = "",
        directional_sync_min_delta_ms: int | str = "",
        media3_seek_recovery_enabled: bool | str | None = None,
        exo2_seek_recovery_enabled: bool | str | None = None,
        media3_seek_recovery_delay_ms: int | str = "",
        exo2_seek_recovery_delay_ms: int | str = "",
        media3_seek_policy: str = "",
        exo2_seek_policy: str = "",
        media3_codec_mode: str = "",
        exo2_codec_mode: str = "",
    ) -> dict[str, Any]:
        def bool_arg(value: bool | str | None):
            return ("true" if value else "false") if isinstance(value, bool) else value
        return self.dev_control(
            "tuning",
            reset="true" if reset else "false",
            media3_ts_search_multiplier=media3_ts_search_multiplier,
            exo2_ts_search_multiplier=exo2_ts_search_multiplier,
            media3_pull_read_kb=media3_pull_read_kb,
            exo2_pull_read_kb=exo2_pull_read_kb,
            media3_min_buffer_ms=media3_min_buffer_ms,
            media3_max_buffer_ms=media3_max_buffer_ms,
            media3_playback_buffer_ms=media3_playback_buffer_ms,
            media3_rebuffer_ms=media3_rebuffer_ms,
            exo2_min_buffer_ms=exo2_min_buffer_ms,
            exo2_max_buffer_ms=exo2_max_buffer_ms,
            exo2_playback_buffer_ms=exo2_playback_buffer_ms,
            exo2_rebuffer_ms=exo2_rebuffer_ms,
            directional_sync_min_delta_ms=directional_sync_min_delta_ms,
            media3_seek_recovery_enabled=bool_arg(media3_seek_recovery_enabled),
            exo2_seek_recovery_enabled=bool_arg(exo2_seek_recovery_enabled),
            media3_seek_recovery_delay_ms=media3_seek_recovery_delay_ms,
            exo2_seek_recovery_delay_ms=exo2_seek_recovery_delay_ms,
            media3_seek_policy=media3_seek_policy,
            exo2_seek_policy=exo2_seek_policy,
            media3_codec_mode=media3_codec_mode,
            exo2_codec_mode=exo2_codec_mode,
        )

    def connect_server(
        self,
        *,
        server_name: str = "",
        address: str = "",
        port: int = 31099,
        save: bool = True,
        renderer: str = "",
    ) -> dict[str, Any]:
        # Android 10+ blocks an Activity launch requested by a background
        # BroadcastReceiver.  The API 30 Fire TV therefore needs the Dev app
        # brought to the foreground before the receiver starts the selected
        # renderer.  Older Fire OS allowed this and hid the automation bug.
        status = self.app_status()
        foreground_launch = False
        launch_result = ""
        if not status.get("foreground"):
            launch_result = self.launch()
            foreground_launch = True
            deadline = time.monotonic() + 10.0
            while time.monotonic() < deadline:
                status = self.app_status()
                if status.get("foreground"):
                    break
                time.sleep(0.25)
            if not status.get("foreground"):
                raise RuntimeError(
                    "Dev app did not become foreground before server connection; "
                    "Android would block the renderer Activity launch"
                )

        result = self.dev_control(
            "connect",
            server_name=server_name,
            address=address,
            port=max(1, min(int(port), 65535)),
            save="true" if save else "false",
            renderer=renderer,
        )
        result["automationForegroundLaunched"] = foreground_launch
        if launch_result:
            result["automationForegroundLaunchResult"] = launch_result.strip()
        return result

    def exit_session(self) -> dict[str, Any]:
        return self.dev_control("exit")

    def sage_command(self, command: str) -> dict[str, Any]:
        return self.dev_control("command", command=command)

    def native_input_text(self, text: str) -> dict[str, Any]:
        """Send text over the MiniClient native keyboard-event channel.

        This reproduces the KeyMapProcessor keyCode/keyChar encoding inside the
        debug APK and does not depend on Android IME focus or adb ``input text``.
        """
        value = str(text)
        if not value:
            raise ValueError("text must not be empty")
        if "\n" in value or "\r" in value:
            raise ValueError("text must be a single line")
        return self.dev_control("input_text_native", text=value)

    def keyboard_input_text(self, text: str) -> dict[str, Any]:
        """Type through Android's OS input system into the focused MiniClient view.

        The SageTV search field is rendered by the MiniClient and is not a native
        Android EditText/InputConnection.  Therefore debug-receiver-side
        View.dispatchKeyEvent() is unsafe and can stall the receiver.  Use the
        Android shell input service after MCP has positively verified that the
        Fire TV IME is visible.
        """
        value = str(text)
        if not value:
            raise ValueError("text must not be empty")
        if "\n" in value or "\r" in value:
            raise ValueError("text must be a single line")
        raw = self.input_text(value)
        return {
            "ok": True,
            "op": "input_text_keyboard",
            "charsSent": len(value),
            "inputPath": "android_os_input_text",
            "raw": raw,
        }

    def direct_input_text(self, text: str) -> dict[str, Any]:
        # Compatibility alias retained for older callers.  It intentionally uses
        # the same Android OS keyboard/input-service path as keyboard_input_text.
        return self.keyboard_input_text(text)

    def hide_ime(self) -> dict[str, Any]:
        return self.dev_control("ime_hide")

    def set_ime_suppression(self, enabled: bool) -> dict[str, Any]:
        return self.dev_control("ime_suppress", enabled="true" if enabled else "false")

    def player_control(self, action: str) -> dict[str, Any]:
        value = str(action).strip().lower()
        if value not in {"play", "pause", "stop"}:
            raise ValueError("action must be play, pause, or stop")
        return self.dev_control("player_control", action=value)

    def request_competing_audio_focus(self, mode: str) -> dict[str, Any]:
        value = str(mode).strip().lower()
        if value not in {"transient", "duck", "permanent"}:
            raise ValueError("mode must be transient, duck, or permanent")
        return self.dev_control("audio_focus_request", foreground=False, mode=value)

    def abandon_competing_audio_focus(self) -> dict[str, Any]:
        return self.dev_control("audio_focus_abandon", foreground=False)

    def set_subtitle_track(self, index: int) -> dict[str, Any]:
        value = int(index)
        if value < -1:
            raise ValueError("subtitle index must be -1 (off) or >= 0")
        return self.dev_control("subtitle_control", foreground=False, index=value)

    def seek_relative(self, delta_ms: int) -> dict[str, Any]:
        return self.dev_control("seek_relative", delta_ms=int(delta_ms))

    def frame_step(self, amount: int) -> dict[str, Any]:
        value = int(amount)
        if value == 0:
            raise ValueError("amount must be a non-zero signed frame count")
        return self.dev_control("frame_step", amount=value)

    def playback_rate(self, rate: float) -> dict[str, Any]:
        value = float(rate)
        if not -256.0 <= value <= 256.0:
            raise ValueError("rate must be between -256 and 256")
        return self.dev_control("playback_rate", rate=value)

    def media3_fast_switch_file(self, server_path: str) -> dict[str, Any]:
        value = str(server_path or "").strip()
        if not value:
            raise ValueError("server_path is required")
        return self.dev_control("fast_switch_file", server_path=value)

    def comskip(self, direction: str) -> dict[str, Any]:
        requested = str(direction).strip().lower()
        if requested in {"right", "forward", "next"}:
            normalized = "right"
        elif requested in {"left", "backward", "previous", "prev"}:
            normalized = "left"
        else:
            raise ValueError("direction must be right/forward/next or left/backward/previous")
        return self.dev_control("comskip", direction=normalized)

    def android_skip_check(
        self,
        commands: list[str],
        delay_ms: int = 350,
        settle_ms: int = 300,
        recovery_timeout_ms: int = 8000,
        verify_playback_ms: int = 2500,
        health_poll_ms: int = 250,
    ) -> dict[str, Any]:
        cleaned = [str(command).strip().lower() for command in commands if str(command).strip()]
        if not cleaned:
            raise ValueError("At least one SageTV command is required for skip_check")
        return self.dev_control(
            "skip_check",
            foreground=False,
            commands=",".join(cleaned),
            delay_ms=max(0, min(int(delay_ms), 10000)),
            settle_ms=max(0, min(int(settle_ms), 30000)),
            recovery_timeout_ms=max(250, min(int(recovery_timeout_ms), 300000)),
            verify_playback_ms=max(250, min(int(verify_playback_ms), 10000)),
            health_poll_ms=max(100, min(int(health_poll_ms), 2000)),
        )

    def android_relative_seek_check(
        self,
        deltas_ms: list[int],
        delay_ms: int = 350,
        settle_ms: int = 300,
        recovery_timeout_ms: int = 8000,
        verify_playback_ms: int = 2500,
        health_poll_ms: int = 250,
    ) -> dict[str, Any]:
        values = [int(value) for value in deltas_ms]
        if not values:
            raise ValueError("At least one relative seek delta is required")
        return self.dev_control(
            "relative_seek_check",
            foreground=False,
            deltas_ms=",".join(str(value) for value in values),
            delay_ms=max(0, min(int(delay_ms), 10000)),
            settle_ms=max(0, min(int(settle_ms), 30000)),
            recovery_timeout_ms=max(250, min(int(recovery_timeout_ms), 300000)),
            verify_playback_ms=max(250, min(int(verify_playback_ms), 10000)),
            health_poll_ms=max(100, min(int(health_poll_ms), 2000)),
        )

    def android_comskip_check(
        self,
        direction: str = "right",
        delay_ms: int = 350,
        settle_ms: int = 300,
        recovery_timeout_ms: int = 12000,
        verify_playback_ms: int = 3000,
        health_poll_ms: int = 250,
    ) -> dict[str, Any]:
        """Run the debug APK's dedicated Comskip operation and instrument A/V recovery.

        No Android key is injected and no configurable arrow mapping is consulted.
        The debug receiver posts SageTV RIGHT/LEFT directly through EventRouter because
        commercial-marker targets are owned by the SageTV STV/server, not the player.
        """
        requested = str(direction).strip().lower() or "right"
        if requested in {"right", "forward", "next"}:
            normalized = "right"
        elif requested in {"left", "backward", "previous", "prev"}:
            normalized = "left"
        else:
            raise ValueError("direction must be right/forward/next or left/backward/previous")

        result = self.dev_control(
            "comskip_check",
            foreground=False,
            direction=normalized,
            delay_ms=max(0, min(int(delay_ms), 10000)),
            settle_ms=max(0, min(int(settle_ms), 30000)),
            recovery_timeout_ms=max(250, min(int(recovery_timeout_ms), 300000)),
            verify_playback_ms=max(250, min(int(verify_playback_ms), 10000)),
            health_poll_ms=max(100, min(int(health_poll_ms), 2000)),
        )
        # Keep the legacy fields for report compatibility, but their value now
        # explicitly represents the direct SageTV command rather than an arrow map.
        result.update({
            "comskipCheck": True,
            "arrowDirection": normalized,
            "resolvedArrowCommand": normalized,
            "directSageCommand": normalized,
            "inputPath": "android_debug_direct_sage_event",
            "comskipMarkerDataAvailable": False,
            "comskipMarkerSource": "server_stv_marker_metadata_not_exposed_to_miniclient",
        })
        return result

    def seek_time(self, target_ms: int) -> dict[str, Any]:
        return self.dev_control("seek_time", target_ms=max(0, int(target_ms)))

    def server_seek_time(self, target_ms: int) -> dict[str, Any]:
        return self.dev_control("server_seek_time", target_ms=max(0, int(target_ms)))

    def show_active_player_adjustments(self) -> dict[str, Any]:
        return self.dev_control("active_player_adjustments")

    def set_active_player_overlay(
        self,
        visible: bool = True,
        mode: str = "",
    ) -> dict[str, Any]:
        """Control Playback Stats while preserving the legacy Boolean operation."""
        normalized = str(mode).strip().lower()
        if normalized:
            allowed = {"toggle", "off", "compact", "detailed", "detailed_30s"}
            if normalized not in allowed:
                raise ValueError(
                    "mode must be toggle, off, compact, detailed, or detailed_30s"
                )
            return self.dev_control("active_player_overlay", mode=normalized)
        return self.dev_control(
            "active_player_overlay",
            visible="true" if visible else "false",
        )

    def local_player_seek(self, target_ms: int) -> dict[str, Any]:
        # Compatibility alias for older backend-isolation callers.
        return self.dev_control("seek_absolute", target_ms=max(0, int(target_ms)))

    def clear_logcat(self) -> None:
        self.run(["logcat", "-c"])

    def logcat_tail(self, lines: int = 500, pattern: str = "") -> str:
        lines = max(1, min(int(lines), 10000))
        text = self.run(["logcat", "-d", "-t", str(lines)], timeout=45).stdout
        if pattern:
            rx = re.compile(pattern, re.IGNORECASE)
            text = "\n".join(line for line in text.splitlines() if rx.search(line))
        return text

    def wait_for_log(self, pattern: str, timeout_s: float = 15.0) -> str:
        rx = re.compile(pattern, re.IGNORECASE)
        end = time.monotonic() + max(0.1, min(float(timeout_s), 120.0))
        while time.monotonic() < end:
            text = self.logcat_tail(1000)
            matches = [x for x in text.splitlines() if rx.search(x)]
            if matches:
                return "\n".join(matches[-20:])
            time.sleep(0.5)
        raise TimeoutError(f"Timed out waiting for log pattern: {pattern}")

    def telemetry_file_text(self) -> tuple[str, str]:
        self._ensure_dev_package(self.dev_package)
        cp = self.run(
            ["shell", "run-as", self.dev_package, "cat", f"files/{TELEMETRY_FILE}"],
            timeout=30,
            check=False,
        )
        if cp.returncode == 0:
            return cp.stdout, "run-as-file"
        # Missing file is expected before the first telemetry-enabled playback event.
        return "", f"run-as-unavailable: {cp.stderr.strip() or cp.stdout.strip() or 'unknown error'}"

    def player_telemetry(self, max_events: int = 80, log_lines: int = 5000) -> dict[str, Any]:
        # Baseline-recovery mode: Android player telemetry hooks are intentionally
        # disabled. Do not read the old app-private telemetry file because
        # `adb install -r` preserves it across APK updates and would surface stale
        # events from a previous broken telemetry build.
        return {
            "tag": TELEMETRY_TAG,
            "file": TELEMETRY_FILE,
            "transport": "disabled-pretelemetry-baseline",
            "active_player": None,
            "active_session": None,
            "state": {},
            "sessions": {},
            "events": [],
            "event_count": 0,
        }

    def wait_for_player_event(self, event_name: str, timeout_s: float = 15.0) -> dict[str, Any]:
        raise RuntimeError(
            "Player telemetry is disabled while restoring the pre-telemetry playback baseline"
        )

    def screenshot(self, path: Path) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        cmd = self._base(True) + ["exec-out", "screencap", "-p"]
        cp = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        if cp.returncode:
            raise RuntimeError(cp.stderr.decode(errors="ignore"))
        path.write_bytes(cp.stdout)
        return path

    def screenrecord(self, path: Path, seconds: int = 15) -> Path:
        seconds = max(1, min(int(seconds), 180))
        path.parent.mkdir(parents=True, exist_ok=True)
        remote = "/sdcard/sagetv_dev_test.mp4"
        self.shell(f"rm -f {remote}")
        self.shell(f"screenrecord --time-limit {seconds} {remote}", timeout=seconds + 20)
        cp = self.run(["pull", remote, str(path)], timeout=90)
        self.shell(f"rm -f {remote}")
        if not path.exists():
            raise RuntimeError(f"screenrecord pull failed: {cp.stdout} {cp.stderr}")
        return path

    def dumpsys_media_codec(self) -> str:
        # Command availability varies by Fire OS build; return best available diagnostic text.
        candidates = ["dumpsys media.codec", "dumpsys media.extractor", "dumpsys SurfaceFlinger"]
        chunks = []
        for cmd in candidates:
            raw = self.shell(cmd, timeout=45, check=False)
            chunks.append(f"===== {cmd} =====\n{raw}")
        return "\n".join(chunks)

    def dumpsys_audio(self) -> str:
        candidates = ["dumpsys media.audio_flinger", "dumpsys audio_flinger", "dumpsys audio"]
        chunks = []
        for cmd in candidates:
            raw = self.shell(cmd, timeout=45, check=False)
            chunks.append(f"===== {cmd} =====\n{raw}")
        return "\n".join(chunks)

    def crash_log(self, lines: int = 500) -> str:
        lines = max(1, min(int(lines), 5000))
        cp = self.run(["logcat", "-b", "crash", "-d", "-t", str(lines)], timeout=30, check=False)
        return cp.stdout + cp.stderr

    def focused_window(self) -> str:
        # Some Fire OS releases (notably the API 30 AFTKRT build) omit both
        # mCurrentFocus and mFocusedApp from `dumpsys window windows`.  A grep
        # pipeline then exits 1 even though ADB and the foreground app are
        # healthy.  Read the unfiltered service output and fall back to the
        # activity manager's resumed-activity record instead.
        window = self.shell("dumpsys window windows", timeout=30, check=False)
        matches = [
            line.strip()
            for line in window.splitlines()
            if "mCurrentFocus" in line or "mFocusedApp" in line
        ]
        if matches:
            return "\n".join(matches) + "\n"

        activities = self.shell("dumpsys activity activities", timeout=30, check=False)
        matches = [
            line.strip()
            for line in activities.splitlines()
            if "mResumedActivity" in line or "topResumedActivity" in line
        ]
        if matches:
            return "\n".join(matches) + "\n"
        return "Focused window unavailable in window/activity service output\n"

    def detect_apk_package(self, apk: Path) -> str:
        if not apk.exists():
            raise FileNotFoundError(apk)
        candidates = []
        if self.aapt:
            candidates.append(self.aapt)
        for name in ["aapt", "aapt2"]:
            found = shutil.which(name)
            if found:
                candidates.append(found)
        for tool in dict.fromkeys(candidates):
            cp = subprocess.run([tool, "dump", "badging", str(apk)], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if cp.returncode == 0:
                m = re.search(r"package:\s+name='([^']+)'", cp.stdout)
                if m:
                    return m.group(1)
        raise RuntimeError("Cannot verify APK package. Configure SAGETV_AAPT to an Android SDK aapt executable; unverified APK installs are intentionally refused.")

    def install_dev_apk(self, apk: Path) -> str:
        package = self.detect_apk_package(apk)
        self._ensure_dev_package(package)

        # Always perform a clean Dev-app install. This intentionally clears the
        # Dev package data so each test run starts from a known APK state. The
        # Dev client ID is compiled in and therefore remains stable for SageTV.
        uninstall = self.run(["uninstall", self.dev_package], timeout=60, check=False)
        uninstall_text = (uninstall.stdout + uninstall.stderr).strip()
        install = self.run(["install", str(apk)], timeout=180)
        install_text = install.stdout.strip()
        return f"uninstall: {uninstall_text or 'not installed'}\ninstall: {install_text}"
