from pathlib import Path
import os
import unittest


ROOT = Path(__file__).resolve().parents[1]


class WorkspaceMountTests(unittest.TestCase):
    def test_component_has_no_second_docker_project(self):
        self.assertFalse((ROOT / "docker-compose.yml").exists())
        self.assertFalse((ROOT / "docker" / "Dockerfile").exists())
        self.assertTrue((ROOT / "docker" / "entrypoint.sh").is_file())

    def test_root_workflow_requires_unified_sibling(self):
        text = (ROOT / "dev.sh").read_text(encoding="utf-8")
        self.assertIn('$ROOT/../opensagetv-vibe-build-env', text)
        self.assertIn('if [[ ! -f "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh" ]]', text)
        self.assertIn('bash "$BUILD_ENV_ROOT/opensagetv-vibe-dev.sh"', text)
        self.assertIn('OPENSAGETV_VIBE_ANDROID_PROJECT_ROOT="$ROOT"', text)
        self.assertIn('CONTAINER_WORKSPACE=/workspace/android-client', text)
        self.assertIn('docker exec -i -w "$CONTAINER_WORKSPACE" "$UNIFIED_CONTAINER"', text)
        self.assertIn('ADB_VENDOR_KEYS="$CONTAINER_WORKSPACE/adb"', text)
        self.assertNotIn("docker compose", text)
        self.assertNotIn("OPENSAGETV_VIBE_ANDROID_STANDALONE", text)
        self.assertNotIn("run --rm", text)

    def test_root_interfaces_are_cwd_independent(self):
        dev = (ROOT / "dev.sh").read_text(encoding="utf-8")
        update = (ROOT / "update.sh").read_text(encoding="utf-8")
        self.assertIn('dirname "${BASH_SOURCE[0]}"', dev)
        self.assertIn('dirname "${BASH_SOURCE[0]}"', update)
        self.assertTrue((ROOT / "dev.cmd").is_file())
        self.assertTrue((ROOT / "update.cmd").is_file())

    def test_windows_dev_wrapper_preserves_dash_prefixed_tool_arguments(self):
        powershell = (ROOT / "dev.ps1").read_text(encoding="utf-8")
        self.assertIn("$CommandArguments = @($args)", powershell)
        self.assertNotIn("ValueFromRemainingArguments", powershell)
        self.assertNotIn("CmdletBinding", powershell)

    def test_windows_dev_wrapper_forwards_commissioning_aliases(self):
        powershell = (ROOT / "dev.ps1").read_text(encoding="utf-8")
        for name in (
            "SAGETV_TEST_DEVICE_ALIAS",
            "SAGETV_TEST_SERVER_ALIAS",
            "SAGETV_TEST_SERVER_ADDRESS",
        ):
            self.assertIn(name, powershell)

    def test_unified_wrapper_rebinds_when_sibling_checkout_changes(self):
        candidates = []
        if os.environ.get("OPENSAGETV_VIBE_BUILD_ENV_ROOT"):
            candidates.append(Path(os.environ["OPENSAGETV_VIBE_BUILD_ENV_ROOT"]))
        candidates.extend((
            ROOT.parent / "opensagetv-vibe-build-env",
            ROOT.parent / "release-manifest",
        ))
        build_env = next(
            (candidate for candidate in candidates
             if (candidate / "opensagetv-vibe-dev.sh").is_file()),
            candidates[0],
        )
        shell = (build_env / "opensagetv-vibe-dev.sh").read_text(encoding="utf-8")
        powershell = (build_env / "opensagetv-vibe-dev.ps1").read_text(
            encoding="utf-8"
        )
        for script in (shell, powershell):
            self.assertIn("org.opensagetv.vibe.projects-root", script)
            self.assertIn("sibling workspace changed", script)

        self.assertIn("OPENSAGETV_VIBE_ANDROID_PROJECT_ROOT", shell)
        self.assertIn("org.opensagetv.vibe.android-project-root", shell)
        self.assertIn("active Android checkout changed", shell)

        entrypoint = (build_env / "scripts" / "dev-entrypoint.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            'OPENSAGETV_VIBE_BUILD_ENV_ROOT="$manifest"', entrypoint
        )

    def test_test_runner_is_packaged(self):
        self.assertTrue((ROOT / "scripts" / "run_unit_tests.sh").is_file())


if __name__ == "__main__":
    unittest.main()
