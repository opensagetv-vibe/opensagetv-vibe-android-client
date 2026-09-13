import importlib.util
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "mcp" / "src"))
sys.path.insert(0, str(ROOT / "scripts"))
SPEC = importlib.util.spec_from_file_location(
    "mcp_disc_test", ROOT / "scripts" / "mcp_disc_test.py"
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class DiscFixtureRegistryTests(unittest.TestCase):
    def test_stock_web_uses_directory_name_and_normal_watch(self):
        cases = [{
            "id": "disc_one",
            "path_type": "server_path",
            "path": "/var/media/tests/My_DVD",
        }]
        self.assertEqual(
            [("name", "My_DVD", "disc_one")],
            MODULE.configured_disc_targets(cases, "stock_web"),
        )

    def test_vibe_server_keeps_exact_path(self):
        cases = [{
            "id": "disc_one",
            "path_type": "server_path",
            "path": "/var/media/tests/My_DVD",
        }]
        self.assertEqual(
            [("path", "/var/media/tests/My_DVD", "disc_one")],
            MODULE.configured_disc_targets(cases, "vibe_exact_path"),
        )

    def test_search_fixture_is_server_independent(self):
        cases = [{"id": "disc_one", "path_type": "search", "path": "My DVD"}]
        self.assertEqual(
            [("name", "My DVD", "disc_one")],
            MODULE.configured_disc_targets(cases, "stock_web"),
        )

    def test_collection_root_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "server_root collection"):
            MODULE.configured_disc_targets([{
                "id": "disc_root",
                "path_type": "server_root",
                "path": "/var/media/tests",
            }], "stock_web")

    def test_disc_harness_uses_the_shared_adaptive_storage_contract(self):
        source = (ROOT / "scripts" / "mcp_disc_test.py").read_text(encoding="utf-8")
        for expected in (
            'default_test_value("storage_warmup_timeout_seconds", 120)',
            'default_test_value("storage_slow_startup_ms", 10000)',
            'default_test_value("storage_warm_cache_seconds", 600)',
            '"slow_start_discarded_and_retried"',
            '"normal_start_used_as_measured_result"',
            '"recent_media_uses_normal_gate"',
            'mark_recent(',
            'recent_entry(',
        ):
            self.assertIn(expected, source)


if __name__ == "__main__":
    unittest.main()
