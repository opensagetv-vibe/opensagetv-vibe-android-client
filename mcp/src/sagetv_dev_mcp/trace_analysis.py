from __future__ import annotations

from collections import Counter
import json
import re
from typing import Any


DETAIL_NUMBER = re.compile(r"(?:^|;)([A-Za-z][A-Za-z0-9]*)=(-?\d+)(?:;|$)")


def parse_jsonl(text: str) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for line_number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line:
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as exc:
            records.append({
                "event": "trace_parse_error",
                "line": line_number,
                "error": str(exc),
            })
            continue
        if isinstance(value, dict):
            records.append(value)
    return records


def _detail_numbers(record: dict[str, Any]) -> dict[str, int]:
    detail = str(record.get("detail", ""))
    return {name: int(value) for name, value in DETAIL_NUMBER.findall(detail)}


def analyze_records(records: list[dict[str, Any]]) -> dict[str, Any]:
    event_counts = Counter(str(record.get("event", "unknown")) for record in records)
    errors = [
        record for record in records
        if any(token in str(record.get("event", "")).lower()
               for token in ("error", "exception", "timeout", "stuck"))
    ]
    warnings: list[str] = []
    sequences = [record.get("sequence") for record in records
                 if isinstance(record.get("sequence"), int)]
    # Synchronous player events and asynchronous protocol snapshots share one
    # sequence generator but can reach the single file writer in a different
    # order. Analyze by event time and detect actual missing sequence numbers,
    # rather than misreporting normal writer reordering as lost evidence.
    unique_sequences = sorted(set(sequences))
    sequence_gaps = []
    for previous, current in zip(unique_sequences, unique_sequences[1:]):
        if current > previous + 1:
            sequence_gaps.append({"after": previous, "before": current,
                                  "missing": current - previous - 1})

    pending_deinit: dict[str, Any] | None = None
    switch_cycles: list[dict[str, Any]] = []
    pending_seek: dict[str, Any] | None = None
    seeks: list[dict[str, Any]] = []
    ordered_records = sorted(records, key=lambda record: (
        record.get("monotonicMs") if isinstance(record.get("monotonicMs"), int) else 2**63,
        record.get("sequence") if isinstance(record.get("sequence"), int) else 2**63,
    ))
    for record in ordered_records:
        event = str(record.get("event", ""))
        when = record.get("monotonicMs")
        if event == "server_deinit_command":
            pending_deinit = {
                "deinitMonotonicMs": when,
                "connectionGeneration": record.get("connectionGeneration"),
                "socketRecycled": False,
                "nextOpenUrlMonotonicMs": None,
            }
            switch_cycles.append(pending_deinit)
        elif event == "media_socket_recycle_after_deinit" and pending_deinit:
            pending_deinit["socketRecycled"] = True
            pending_deinit["recycleMonotonicMs"] = when
        elif event == "server_openurl_command" and pending_deinit:
            pending_deinit["nextOpenUrlMonotonicMs"] = when
            if isinstance(when, int) and isinstance(pending_deinit.get("deinitMonotonicMs"), int):
                pending_deinit["switchToOpenUrlMs"] = when - pending_deinit["deinitMonotonicMs"]
            pending_deinit = None

        if event == "server_seek_command":
            values = _detail_numbers(record)
            pending_seek = {
                "requestedMs": values.get("requestedMs"),
                "serverCommandMonotonicMs": when,
                "connectionGeneration": record.get("connectionGeneration"),
                "playbackSessionGeneration": record.get("playbackSessionGeneration"),
            }
            seeks.append(pending_seek)
        elif event in ("backend_seek_invoke", "initial_seek_attached_to_source") and pending_seek:
            values = _detail_numbers(record)
            pending_seek.update({
                "appliedMs": values.get("appliedMs"),
                "durationMs": values.get("durationMs", record.get("durationMs")),
                "bufferedMs": values.get("bufferedMs", record.get("bufferedPositionMs")),
                "backendInvokeMonotonicMs": when,
            })
        elif event == "first_video_frame" and pending_seek:
            pending_seek["firstFrameMonotonicMs"] = when
            if isinstance(when, int) and isinstance(pending_seek.get("serverCommandMonotonicMs"), int):
                pending_seek["seekToFirstFrameMs"] = when - pending_seek["serverCommandMonotonicMs"]
            pending_seek = None

    missing_recycles = sum(1 for cycle in switch_cycles if not cycle["socketRecycled"])
    missing_open = sum(1 for cycle in switch_cycles if cycle["nextOpenUrlMonotonicMs"] is None)
    if missing_recycles:
        warnings.append(f"{missing_recycles} DEINIT cycle(s) have no recorded media-socket recycle")
    if missing_open:
        warnings.append(f"{missing_open} DEINIT cycle(s) have no later OPENURL in this trace window")
    if errors:
        warnings.append(f"{len(errors)} error/exception/timeout event(s) recorded")
    if sequence_gaps:
        warnings.append(f"{len(sequence_gaps)} trace sequence gap(s); a rotation boundary may be missing")

    wall_values = [record.get("wallMs") for record in records
                   if isinstance(record.get("wallMs"), int)]
    return {
        "recordCount": len(records),
        "firstWallMs": min(wall_values) if wall_values else None,
        "lastWallMs": max(wall_values) if wall_values else None,
        "connectionGenerations": sorted({record.get("connectionGeneration") for record in records
                                         if isinstance(record.get("connectionGeneration"), int)
                                         and record.get("connectionGeneration") >= 0}),
        "playbackSessionGenerations": sorted({record.get("playbackSessionGeneration") for record in records
                                              if isinstance(record.get("playbackSessionGeneration"), int)
                                              and record.get("playbackSessionGeneration") >= 0}),
        "eventCounts": dict(sorted(event_counts.items())),
        "switchCycles": switch_cycles,
        "seeks": seeks,
        "errors": errors[-20:],
        "sequenceGaps": sequence_gaps,
        "outOfOrderRecordCount": sum(
            1 for previous, current in zip(sequences, sequences[1:])
            if current < previous
        ),
        "warnings": warnings,
    }


def analyze_jsonl(text: str) -> dict[str, Any]:
    return analyze_records(parse_jsonl(text))
