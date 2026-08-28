from __future__ import annotations

from dataclasses import dataclass
import shlex


VALID_SEQUENCE_ACTIONS = {"command", "sendkey", "sendtext", "directtext", "hideime", "delay", "waittextinput", "waitimevisible", "waitimehidden"}
MAX_SEQUENCE_ACTIONS = 100
MAX_DELAY_MS = 60000


@dataclass(frozen=True)
class SequenceAction:
    action: str
    value: str | int | None
    line: int


def parse_sequence_script(sequence: str) -> list[SequenceAction]:
    """Parse the line-oriented MCP input sequence.

    Supported forms:
      command <sage-command>
      sendkey <android-key>
      sendtext <text>
      directtext <text>
      hideime
      delay <milliseconds>
      waittextinput <timeout-ms>
      waitimevisible <timeout-ms>
      waitimehidden <timeout-ms>

    Blank lines and lines beginning with # are ignored.  sendtext/directtext accept either
    unquoted remainder-of-line text or one shell-style quoted string.
    """
    if not isinstance(sequence, str) or not sequence.strip():
        raise ValueError("sequence must not be empty")

    actions: list[SequenceAction] = []
    for line_no, raw in enumerate(sequence.splitlines(), start=1):
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue

        head, sep, rest = stripped.partition(" ")
        action = head.lower()
        if action not in VALID_SEQUENCE_ACTIONS:
            raise ValueError(
                f"line {line_no}: unsupported action {head!r}; "
                "use command, sendkey, sendtext, directtext, hideime, delay, waittextinput, waitimevisible, or waitimehidden"
            )
        if action == "hideime":
            if sep and rest.strip():
                raise ValueError(f"line {line_no}: hideime does not take a value")
            actions.append(SequenceAction(action=action, value=None, line=line_no))
            if len(actions) > MAX_SEQUENCE_ACTIONS:
                raise ValueError(f"sequence is limited to {MAX_SEQUENCE_ACTIONS} actions")
            continue
        if not sep or not rest.strip():
            raise ValueError(f"line {line_no}: {action} requires a value")

        rest = rest.strip()
        if action in {"sendtext", "directtext"}:
            # Preserve normal unquoted text exactly.  If the whole value is quoted,
            # remove the shell-style quotes for convenient multiline scripts.
            if rest[:1] in {"\"", "'"}:
                parts = shlex.split(rest)
                if len(parts) != 1:
                    raise ValueError(f"line {line_no}: quoted {action} must contain one value")
                value: str | int | None = parts[0]
            else:
                value = rest
            if not str(value):
                raise ValueError(f"line {line_no}: {action} must not be empty")
        elif action in {"delay", "waittextinput", "waitimevisible", "waitimehidden"}:
            try:
                value = int(rest, 10)
            except ValueError as exc:
                raise ValueError(f"line {line_no}: {action} must be milliseconds as an integer") from exc
            minimum = 0 if action == "delay" else 1
            if value < minimum or value > MAX_DELAY_MS:
                raise ValueError(f"line {line_no}: {action} must be between {minimum} and {MAX_DELAY_MS} ms")
        else:
            parts = shlex.split(rest)
            if len(parts) != 1:
                raise ValueError(f"line {line_no}: {action} accepts exactly one value")
            value = parts[0]

        actions.append(SequenceAction(action=action, value=value, line=line_no))
        if len(actions) > MAX_SEQUENCE_ACTIONS:
            raise ValueError(f"sequence is limited to {MAX_SEQUENCE_ACTIONS} actions")

    if not actions:
        raise ValueError("sequence contained no actions")
    return actions
