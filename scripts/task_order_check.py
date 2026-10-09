#!/usr/bin/env python3
"""Fail when an Android task change was not reconciled with execution order."""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TASK_ID = r"[A-Z][A-Z0-9]*-\d{3}"


def _required_revision(pattern: str, text: str, label: str) -> int:
    match = re.search(pattern, text)
    if not match:
        raise ValueError(f"missing {label}")
    return int(match.group(1))


def _section(text: str, heading: str) -> str:
    match = re.search(rf"(?m)^#{{2,3}} {re.escape(heading)}\s*$", text)
    if not match:
        raise ValueError(f"missing {heading} section")
    tail = text[match.end():]
    next_heading = re.search(r"(?m)^#{2,3} ", tail)
    return tail[:next_heading.start()] if next_heading else tail


def _ordered_ids(section: str) -> list[str]:
    return re.findall(rf"(?m)^\d+\. \*\*({TASK_ID})\*\*", section)


def check_task_order(root: Path = ROOT, workspace_task: Path | None = None) -> str:
    tasks = (root / "TASKS.md").read_text(encoding="utf-8")
    revision = _required_revision(
        r"Checklist revision:\s*\*\*(\d+)\*\*", tasks, "checklist revision"
    )
    reviewed = _required_revision(
        r"Order reviewed against checklist revision\s*\*\*(\d+)\*\*",
        tasks,
        "Android order-review revision",
    )
    ledger_heading = re.search(r"(?m)^## Checklist change ledger\s*$", tasks)
    if not ledger_heading:
        raise ValueError("missing checklist change ledger")
    active_text = tasks[:ledger_heading.start()]
    ledger_text = tasks[ledger_heading.end():]
    ledger_revisions = [
        int(value) for value in re.findall(r"(?m)^\|\s*(\d+)\s*\|", ledger_text)
    ]
    if not ledger_revisions:
        raise ValueError("checklist change ledger has no revision entries")
    latest = max(ledger_revisions)
    if revision != latest or reviewed != revision:
        raise ValueError(
            f"stale Android order review: checklist={revision}, "
            f"ledger={latest}, reviewed={reviewed}"
        )

    order = _ordered_ids(_section(tasks, "Suggested execution order (avoid repeated matrices)"))
    active = set(re.findall(rf"(?m)^- \[ \] \*\*({TASK_ID})\s+-", active_text))
    completed_in_order = sorted(set(order) - active)
    if completed_in_order:
        raise ValueError(
            "completed/missing task still appears in Android order: "
            + ", ".join(completed_in_order)
        )

    if workspace_task is None:
        workspace_task = root.parent.parent / "task.md"
    if workspace_task.is_file():
        workspace = workspace_task.read_text(encoding="utf-8")
        workspace_reviewed = _required_revision(
            r"Execution-order review:\s*\*\*Android checklist revision (\d+)",
            workspace,
            "workspace Android order-review revision",
        )
        workspace_order = _ordered_ids(
            _section(workspace, "Suggested Android execution order (avoid repeated matrices)")
        )
        if workspace_reviewed != revision or workspace_order != order:
            raise ValueError(
                f"workspace Android order is stale: reviewed={workspace_reviewed}, "
                f"checklist={revision}, order_matches={workspace_order == order}"
            )
        return f"PASS: Android task order and workspace mirror reviewed at revision {revision}"
    return f"PASS: Android task order reviewed at revision {revision} (workspace mirror absent)"


if __name__ == "__main__":
    try:
        print(check_task_order())
    except ValueError as exc:
        raise SystemExit(f"FAIL: {exc}") from exc
