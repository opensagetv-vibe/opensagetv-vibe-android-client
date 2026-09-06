from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
DEV = ROOT / "source" / "dev"
INVENTORY = ROOT / "docs" / "ANDROID_FRAMEWORK_MODERNIZATION.md"

ACTIVE_JAVA_ROOTS = (
    DEV / "core" / "src" / "main" / "java",
    DEV / "android-shared" / "src" / "main" / "java",
    DEV / "android-tv" / "src" / "main" / "java",
)

SCHEDULING_PATTERN = re.compile(
    r"new\s+Thread\b|extends\s+Thread\b|new\s+Timer\b|"
    r"\bTimerTask\b|Thread\.sleep\b|new\s+Handler\s*\(|\.postDelayed\s*\("
)


def active_scheduling_owners():
    owners = []
    for source_root in ACTIVE_JAVA_ROOTS:
        for path in source_root.rglob("*.java"):
            # Strip comments only well enough to avoid counting the explicitly
            # disabled historical sleep examples in MiniClientConnection.
            text = re.sub(r"//.*", "", path.read_text(encoding="utf-8"))
            if SCHEDULING_PATTERN.search(text):
                owners.append(path.relative_to(ROOT).as_posix())
    return sorted(set(owners))


class FrameworkModernizationInventoryTests(unittest.TestCase):
    def test_production_sources_do_not_use_framework_fragments(self):
        forbidden = (
            "import android.app.Fragment;",
            "import android.app.DialogFragment;",
            "import android.app.FragmentTransaction;",
            ".getFragmentManager()",
        )
        offenders = []
        for source_root in ACTIVE_JAVA_ROOTS:
            for path in source_root.rglob("*.java"):
                text = path.read_text(encoding="utf-8")
                for token in forbidden:
                    if token in text:
                        offenders.append(
                            f"{path.relative_to(ROOT).as_posix()}: {token}"
                        )
        self.assertFalse(
            offenders,
            "framework-fragment APIs remain: " + ", ".join(offenders),
        )

    def test_every_active_scheduling_owner_is_in_the_inventory(self):
        inventory = INVENTORY.read_text(encoding="utf-8")
        owners = active_scheduling_owners()
        self.assertTrue(
            owners, "scheduling scan unexpectedly found no active Java owners"
        )
        missing = [owner for owner in owners if f"`{owner}`" not in inventory]
        self.assertFalse(
            missing, "unlisted scheduling owners: " + ", ".join(missing)
        )

    def test_inventory_records_required_migration_dimensions(self):
        inventory = INVENTORY.read_text(encoding="utf-8")
        for term in (
            "Current work and affinity",
            "Cancellation / lifecycle today",
            "Risk and migration boundary",
            "Fragment and settings inventory",
            "Permissions and exported surface",
            "Audio focus",
            "Fullscreen and window handling",
            "Dependency boundary",
        ):
            self.assertIn(term, inventory)


if __name__ == "__main__":
    unittest.main()
