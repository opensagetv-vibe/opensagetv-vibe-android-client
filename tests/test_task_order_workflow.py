from pathlib import Path
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from task_order_check import check_task_order  # noqa: E402


class TaskOrderWorkflowTests(unittest.TestCase):
    def test_current_project_and_workspace_order_match(self):
        self.assertIn("PASS:", check_task_order(ROOT))

    def test_completed_task_or_stale_revision_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "projects" / "android-client"
            root.mkdir(parents=True)
            workspace = root.parent.parent / "task.md"
            tasks = (
                "Checklist revision: **2**\n"
                "## Suggested execution order (avoid repeated matrices)\n"
                "Order reviewed against checklist revision **2**\n"
                "1. **AUDIO-001** - open\n"
                "## Active\n"
                "- [ ] **AUDIO-001 - Open gate.**\n"
                "## Checklist change ledger\n"
                "| Revision | Date | Change |\n|---|---|---|\n"
                "| 2 | 2026-10-03 | Review. |\n"
            )
            (root / "TASKS.md").write_text(tasks, encoding="utf-8")
            workspace.write_text(
                "Execution-order review: **Android checklist revision 2**\n"
                "### Suggested Android execution order (avoid repeated matrices)\n"
                "1. **AUDIO-001** - open\n",
                encoding="utf-8",
            )
            self.assertIn("PASS:", check_task_order(root, workspace))
            workspace.write_text(
                "Execution-order review: **Android checklist revision 1**\n"
                "### Suggested Android execution order (avoid repeated matrices)\n"
                "1. **AUDIO-001** - open\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "workspace Android order is stale"):
                check_task_order(root, workspace)
            workspace.write_text(
                "Execution-order review: **Android checklist revision 2**\n"
                "### Suggested Android execution order (avoid repeated matrices)\n"
                "1. **AUDIO-001** - open\n",
                encoding="utf-8",
            )
            (root / "TASKS.md").write_text(
                tasks.replace("- [ ] **AUDIO-001", "- [x] **AUDIO-001"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "completed/missing task"):
                check_task_order(root, workspace)
            (root / "TASKS.md").write_text(
                tasks.replace("checklist revision **2**", "checklist revision **1**"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "stale Android order review"):
                check_task_order(root, workspace)


if __name__ == "__main__":
    unittest.main()
