from __future__ import annotations

import os
from pathlib import Path
import hashlib
import shutil
import stat
import subprocess
import tempfile
import time
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "update.sh"


class UpdateRunnerTests(unittest.TestCase):
    def test_help_is_side_effect_free(self):
        result = subprocess.run(
            ["wsl.exe", "--cd", str(ROOT), "bash", "./update.sh", "--help"]
            if os.name == "nt"
            else [str(RUNNER), "--help"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=True,
        )
        self.assertIn("Usage: ./update.sh", result.stdout)

    def make_workspace(self, version: str = "0.5.78") -> Path:
        tmp = Path(tempfile.mkdtemp(prefix="vibe-update-runner-"))
        self.addCleanup(lambda: shutil.rmtree(tmp, ignore_errors=True))
        shutil.copy2(RUNNER, tmp / RUNNER.name)
        os.chmod(tmp / RUNNER.name, 0o755)
        (tmp / "VERSION").write_text(version + "\n", encoding="utf-8")
        (tmp / "release.properties").write_text(
            f"VERSION={version}\nREQUIRES_BUILD=false\n", encoding="utf-8"
        )
        (tmp / "release-deletions.lst").write_text("", encoding="utf-8")
        (tmp / "baseline.txt").write_bytes(
            b"portable baseline\nsecond line\n"
        )
        (tmp / "artifacts" / "downloads").mkdir(parents=True)
        mock_dev = (
            "#!/usr/bin/env bash\n"
            "set -euo pipefail\n"
            "mkdir -p artifacts\n"
            "printf '%s\\n' \"$*\" >> artifacts/mock-dev.log\n"
            "step=${1:-unknown}\n"
            "if [[ -f \".fail-${step}-once\" ]]; then\n"
            "  rm -f -- \".fail-${step}-once\"\n"
            "  exit 17\n"
            "fi\n"
        )
        (tmp / "dev.sh").write_bytes(mock_dev.encode("utf-8"))
        os.chmod(tmp / "dev.sh", 0o755)
        return tmp

    def write_update(
        self,
        workspace: Path,
        version: str,
        requires_build: bool,
        extra_name: str = "",
        deletions: tuple[str, ...] = (),
        marker_value: str | None = None,
    ) -> Path:
        suffix = f"-{extra_name}" if extra_name else ""
        zip_path = workspace / "artifacts" / "downloads" / (
            f"opensagetv-vibe-android-v{version}{suffix}-changed-files-only.zip"
        )
        flag = 'true' if requires_build else 'false'
        release_metadata = (
            "# Machine-readable release metadata.\n"
            f"VERSION={version}\n"
            f"REQUIRES_BUILD={flag}\n"
        )
        members = {
            "VERSION": (version + "\n").encode(),
            "release.properties": release_metadata.encode(),
            "release-deletions.lst": ("".join(f"{name}\n" for name in deletions)).encode(),
            "applied-marker.txt": ((marker_value or version) + "\n").encode(),
        }
        manifest_sources = {
            RUNNER.name: (workspace / RUNNER.name).read_bytes(),
            "dev.sh": (workspace / "dev.sh").read_bytes(),
            "baseline.txt": (workspace / "baseline.txt").read_bytes(),
            **members,
        }
        manifest = "".join(
            f"{hashlib.sha256(data).hexdigest()}  {name}\n"
            for name, data in sorted(manifest_sources.items())
        ).encode()
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in members.items():
                archive.writestr(name, data)
            archive.writestr("PROJECT_MANIFEST.sha256", manifest)
        return zip_path

    def run_runner(self, workspace: Path, *, check: bool = True) -> subprocess.CompletedProcess[str]:
        if os.name == "nt":
            command = [
                "wsl.exe",
                "--cd",
                str(workspace),
                "bash",
                "-lc",
                f"bash ./{RUNNER.name}",
            ]
        else:
            command = [str(workspace / RUNNER.name)]
        return subprocess.run(
            command,
            cwd=workspace,
            text=True,
            capture_output=True,
            check=check,
        )

    def dev_log(self, workspace: Path) -> list[str]:
        path = workspace / "artifacts" / "mock-dev.log"
        return path.read_text(encoding="utf-8").splitlines() if path.exists() else []

    def test_host_only_update_unzips_once_then_rerun_only_launches(self):
        workspace = self.make_workspace()
        self.write_update(workspace, "0.5.79", requires_build=False)

        first = self.run_runner(workspace)
        self.assertEqual("0.5.79", (workspace / "VERSION").read_text().strip())
        self.assertEqual("0.5.79", (workspace / "applied-marker.txt").read_text().strip())
        self.assertIn("REQUIRES_BUILD=false", first.stdout)
        self.assertEqual(
            ["test", "validate", "launch --client-id 44:45:56:30:30:31"],
            self.dev_log(workspace),
        )

        second = self.run_runner(workspace)
        self.assertIn("preparation is already complete; launching only", second.stdout)
        self.assertEqual(
            [
                "test",
                "validate",
                "launch --client-id 44:45:56:30:30:31",
                "launch --client-id 44:45:56:30:30:31",
            ],
            self.dev_log(workspace),
        )

    def test_build_required_update_builds_and_installs_once(self):
        workspace = self.make_workspace()
        self.write_update(workspace, "0.5.79", requires_build=True)

        self.run_runner(workspace)
        self.assertEqual(
            [
                "test",
                "validate",
                "build",
                "install",
                "launch --client-id 44:45:56:30:30:31",
            ],
            self.dev_log(workspace),
        )

        self.run_runner(workspace)
        self.assertEqual(
            [
                "test",
                "validate",
                "build",
                "install",
                "launch --client-id 44:45:56:30:30:31",
                "launch --client-id 44:45:56:30:30:31",
            ],
            self.dev_log(workspace),
        )

    def test_older_or_same_version_zip_is_not_applied(self):
        workspace = self.make_workspace(version="0.5.78")
        self.write_update(workspace, "0.5.77", requires_build=True)
        self.write_update(workspace, "0.5.78", requires_build=True, extra_name="same")

        result = self.run_runner(workspace)
        self.assertIn("no changed-files ZIP newer than v0.5.78", result.stdout)
        self.assertEqual("0.5.78", (workspace / "VERSION").read_text().strip())
        self.assertEqual(
            ["test", "validate", "launch --client-id 44:45:56:30:30:31"],
            self.dev_log(workspace),
        )

    def test_update_without_full_manifest_is_rejected_before_extraction(self):
        workspace = self.make_workspace()
        zip_path = workspace / "artifacts" / "downloads" / (
            "opensagetv-vibe-android-v0.5.79-changed-files-only.zip"
        )
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("VERSION", "0.5.79\n")
            archive.writestr(
                "release.properties",
                "VERSION=0.5.79\nREQUIRES_BUILD=false\n",
            )
            archive.writestr("applied-marker.txt", "must-not-extract\n")

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing the complete PROJECT_MANIFEST.sha256", result.stderr)
        self.assertFalse((workspace / "applied-marker.txt").exists())

    def test_update_payload_hash_mismatch_is_rejected_before_extraction(self):
        workspace = self.make_workspace()
        zip_path = self.write_update(workspace, "0.5.79", requires_build=False)
        with zipfile.ZipFile(zip_path, "a", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("unlisted-file.txt", "unexpected\n")

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing from the full manifest", result.stderr)
        self.assertFalse((workspace / "applied-marker.txt").exists())

    def test_corrupt_zip_is_rejected_before_extraction(self):
        workspace = self.make_workspace()
        zip_path = workspace / "artifacts" / "downloads" / (
            "opensagetv-vibe-android-v0.5.79-changed-files-only.zip"
        )
        zip_path.write_bytes(b"not a zip archive\n")

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((workspace / "applied-marker.txt").exists())

    def test_unsafe_parent_path_is_rejected_before_extraction(self):
        workspace = self.make_workspace()
        zip_path = self.write_update(workspace, "0.5.79", requires_build=False)
        with zipfile.ZipFile(zip_path, "a") as archive:
            archive.writestr("../outside.txt", "must-not-extract\n")

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unsafe path", result.stderr)
        self.assertFalse((workspace.parent / "outside.txt").exists())

    def test_duplicate_path_is_rejected_before_extraction(self):
        workspace = self.make_workspace()
        zip_path = self.write_update(workspace, "0.5.79", requires_build=False)
        with self.assertWarns(UserWarning), zipfile.ZipFile(zip_path, "a") as archive:
            archive.writestr("VERSION", "0.5.79\n")

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("duplicate entry: VERSION", result.stderr)
        self.assertEqual("0.5.78", (workspace / "VERSION").read_text().strip())

    def test_release_metadata_version_mismatch_is_rejected(self):
        workspace = self.make_workspace()
        zip_path = self.write_update(workspace, "0.5.79", requires_build=False)
        with zipfile.ZipFile(zip_path, "r") as source:
            entries = {
                info.filename: source.read(info)
                for info in source.infolist()
                if info.filename != "release.properties"
            }
            original_metadata = source.read("release.properties")
        mismatched_metadata = b"VERSION=0.5.80\nREQUIRES_BUILD=false\n"
        entries["release.properties"] = mismatched_metadata
        entries["PROJECT_MANIFEST.sha256"] = entries[
            "PROJECT_MANIFEST.sha256"
        ].replace(
            hashlib.sha256(original_metadata).hexdigest().encode(),
            hashlib.sha256(mismatched_metadata).hexdigest().encode(),
            1,
        )
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in entries.items():
                archive.writestr(name, data)

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("exactly one VERSION=0.5.79", result.stderr)
        self.assertFalse((workspace / "applied-marker.txt").exists())

    def test_manifest_payload_hash_mismatch_is_rejected(self):
        workspace = self.make_workspace()
        zip_path = self.write_update(workspace, "0.5.79", requires_build=False)
        with zipfile.ZipFile(zip_path, "r") as source:
            entries = {info.filename: source.read(info) for info in source.infolist()}
        manifest = entries["PROJECT_MANIFEST.sha256"].decode()
        entries["PROJECT_MANIFEST.sha256"] = manifest.replace(
            hashlib.sha256(b"0.5.79\n").hexdigest(),
            "0" * 64,
            1,
        ).encode()
        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name, data in entries.items():
                archive.writestr(name, data)

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("packaged file hash does not match", result.stderr)
        self.assertFalse((workspace / "applied-marker.txt").exists())

    def test_untouched_baseline_drift_is_rejected(self):
        workspace = self.make_workspace()
        self.write_update(workspace, "0.5.79", requires_build=False)
        with (workspace / "dev.sh").open("ab") as stream:
            stream.write(b"# local drift\n")

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("baseline hash mismatch: dev.sh", result.stderr)
        self.assertFalse((workspace / "applied-marker.txt").exists())

    def test_windows_crlf_baseline_is_accepted_when_text_is_otherwise_identical(self):
        workspace = self.make_workspace()
        self.write_update(workspace, "0.5.79", requires_build=False)
        baseline = workspace / "baseline.txt"
        baseline.write_bytes(baseline.read_bytes().replace(b"\n", b"\r\n"))

        result = self.run_runner(workspace)

        self.assertIn("REQUIRES_BUILD=false", result.stdout)
        self.assertEqual("0.5.79", (workspace / "VERSION").read_text().strip())
        self.assertEqual("0.5.79", (workspace / "applied-marker.txt").read_text().strip())
        self.assertEqual(
            b"portable baseline\nsecond line\n", baseline.read_bytes()
        )

    def test_crlf_normalization_does_not_hide_content_drift(self):
        workspace = self.make_workspace()
        self.write_update(workspace, "0.5.79", requires_build=False)
        baseline = workspace / "baseline.txt"
        baseline.write_bytes(
            baseline.read_bytes().replace(b"\n", b"\r\n") + b"drift\r\n"
        )

        result = self.run_runner(workspace, check=False)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("baseline hash mismatch: baseline.txt", result.stderr)
        self.assertFalse((workspace / "applied-marker.txt").exists())

    def test_highest_version_then_newest_same_version_package_is_selected(self):
        workspace = self.make_workspace()
        self.write_update(
            workspace, "0.5.79", requires_build=False, marker_value="lower-version"
        )
        older = self.write_update(
            workspace,
            "0.5.80",
            requires_build=False,
            extra_name="older",
            marker_value="older-same-version",
        )
        newer = self.write_update(
            workspace,
            "0.5.80",
            requires_build=False,
            extra_name="newer",
            marker_value="newer-same-version",
        )
        now = time.time()
        os.utime(older, (now - 10, now - 10))
        os.utime(newer, (now, now))

        result = self.run_runner(workspace)
        self.assertIn(newer.name, result.stdout)
        self.assertEqual(
            "newer-same-version",
            (workspace / "applied-marker.txt").read_text().strip(),
        )

    def test_failed_preparation_resumes_at_first_incomplete_step(self):
        workspace = self.make_workspace()
        self.write_update(workspace, "0.5.79", requires_build=True)
        (workspace / ".fail-validate-once").write_text("fail once\n", encoding="utf-8")

        first = self.run_runner(workspace, check=False)
        self.assertEqual(17, first.returncode)
        self.assertEqual(["test", "validate"], self.dev_log(workspace))

        second = self.run_runner(workspace)
        self.assertIn("STEP: VALIDATE", second.stdout)
        self.assertNotIn("STEP: TEST", second.stdout)
        self.assertEqual(
            [
                "test",
                "validate",
                "validate",
                "build",
                "install",
                "launch --client-id 44:45:56:30:30:31",
            ],
            self.dev_log(workspace),
        )

    def test_interruption_after_extraction_resumes_as_current_release(self):
        workspace = self.make_workspace()
        # Simulate process termination after the archive replaced release files
        # but before update_runner/state.env could be written.
        (workspace / "VERSION").write_text("0.5.79\n", encoding="utf-8")
        (workspace / "release.properties").write_text(
            "VERSION=0.5.79\nREQUIRES_BUILD=true\n", encoding="utf-8"
        )
        (workspace / "applied-marker.txt").write_text(
            "already extracted\n", encoding="utf-8"
        )

        result = self.run_runner(workspace)
        self.assertIn("WORKFLOW COMPLETE: v0.5.79", result.stdout)
        self.assertEqual(
            [
                "test",
                "validate",
                "build",
                "install",
                "launch --client-id 44:45:56:30:30:31",
            ],
            self.dev_log(workspace),
        )

    def test_validated_deletion_list_removes_only_named_file(self):
        workspace = self.make_workspace()
        obsolete = workspace / "obsolete-review.md"
        obsolete.write_text("old\n", encoding="utf-8")
        keep = workspace / "keep.md"
        keep.write_text("keep\n", encoding="utf-8")
        self.write_update(
            workspace,
            "0.5.79",
            requires_build=False,
            deletions=(obsolete.name,),
        )

        self.run_runner(workspace)
        self.assertFalse(obsolete.exists())
        self.assertTrue(keep.exists())

    def test_symbolic_link_zip_entry_is_rejected_before_extraction(self):
        workspace = self.make_workspace()
        zip_path = self.write_update(workspace, "0.5.79", requires_build=False)
        link = zipfile.ZipInfo("unsafe-link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        with zipfile.ZipFile(zip_path, "a") as archive:
            archive.writestr(link, "outside")

        result = self.run_runner(workspace, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("symbolic link", result.stderr)
        self.assertFalse((workspace / "applied-marker.txt").exists())


if __name__ == "__main__":
    unittest.main()
