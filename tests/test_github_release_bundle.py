import importlib.util
import pathlib
import subprocess
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[1]


class GithubReleaseBundleTest(unittest.TestCase):
    @staticmethod
    def _load_packager():
        path = ROOT / "scripts/create_github_release_bundle.py"
        spec = importlib.util.spec_from_file_location("github_release_packager_test", path)
        module = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(module)
        return module

    def test_packager_enforces_manifest_legal_material_and_archive_hashes(self):
        text = (ROOT / "scripts/create_github_release_bundle.py").read_text(encoding="utf-8")
        for marker in (
            "PROJECT_MANIFEST.sha256",
            "THIRD_PARTY_NOTICES.md",
            "third_party/RUNTIME_DEPENDENCIES.csv",
            "third_party/licenses/LGPL-2.1.txt",
            "third_party/source-offers/README.md",
            "unsafe or duplicate manifest path",
            "publication source archive hash mismatch",
            "nested APK hash mismatch",
            "release worktree is dirty",
            "info.create_system = 3",
            "source_mode(name, data)",
            'path.name == "gradlew"',
            '".sh", ".py"',
            '"sourceRevisionKind"',
            '"project-manifest"',
            "canonical_content(ROOT / name)",
        ):
            self.assertIn(marker, text)

    def test_gitless_checkout_uses_checked_manifest_provenance(self):
        packager = self._load_packager()
        failure = subprocess.CalledProcessError(128, ["git", "rev-parse", "HEAD"])
        with mock.patch.object(packager.subprocess, "run", side_effect=failure):
            revision, dirty = packager.git_state()
        self.assertTrue(revision.startswith("manifest-sha256:"))
        self.assertIsNone(dirty)

    def test_release_material_is_present_and_not_an_ignored_runtime_report(self):
        for relative in (
            "THIRD_PARTY_NOTICES.md",
            "third_party/README.md",
            "third_party/RUNTIME_DEPENDENCIES.csv",
            "third_party/licenses/LGPL-2.1.txt",
            "third_party/source-offers/README.md",
        ):
            self.assertTrue((ROOT / relative).is_file(), relative)
        self.assertNotIn(
            "artifacts/reports/gradle-runtime-dependencies.txt",
            (ROOT / "third_party/README.md").read_text(encoding="utf-8").split("`RUNTIME_DEPENDENCIES.csv`")[0],
        )


if __name__ == "__main__":
    unittest.main()
