"""Keep local capture-retirement rules visible in the durable workflow."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ArtifactRetirementWorkflowTest(unittest.TestCase):
    def test_workflow_preserves_results_not_every_raw_capture(self):
        text = (ROOT / "WORKFLOW.md").read_text(encoding="utf-8")
        for required in (
            "## Capture and artifact retirement",
            "Completed gates do not require retaining every raw screenshot",
            "Corrected failures",
            "workspace-root `deleteme/`",
            "preserving their",
            "before commit/release",
            "unique evidence for open failures",
            "persistent ADB keys",
            "database caches",
            "canonical fixtures",
            "reject reparse-point traversal",
            "artifacts/CLEANUP_REPORT.md",
            "artifacts/active/<stable-task-ID>/",
            "artifacts/results/<stable-task-ID>/",
            "Retire old run logs and diagnostic snapshots",
            "do not create a new cleanup-quarantine directory",
        ):
            with self.subTest(required=required):
                self.assertIn(required, text)

    def test_contributor_rules_require_end_of_test_retirement(self):
        text = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn("## Artifact retirement after testing", text)
        self.assertIn("after every\n  test session", text)
        self.assertIn("do not\n  automatically delete that folder", text)
        self.assertIn("annotate historical raw-evidence references", text)


if __name__ == "__main__":
    unittest.main()
