import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "index.html"


class GitHubPagesDownloadTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.page = PAGE.read_text(encoding="utf-8")

    def test_page_uses_the_vibe_latest_release_api(self):
        self.assertIn(
            "https://api.github.com/repos/opensagetv-vibe/"
            "opensagetv-vibe-android-client/releases/latest",
            self.page,
        )

    def test_page_matches_the_published_versioned_apk_contract(self):
        self.assertIn(
            r"/^OpenSageTV-Vibe-Android-Client-v.*\.apk$/i",
            self.page,
        )
        release_script = (ROOT / "scripts" / "create_github_release_bundle.py").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            'apk_name = f"OpenSageTV-Vibe-Android-Client-v{version}.apk"',
            release_script,
        )

    def test_page_has_automatic_and_manual_download_paths(self):
        self.assertIn("window.location.replace(apk.browser_download_url)", self.page)
        self.assertRegex(self.page, r'id="download"[^>]+hidden')
        self.assertIn(
            "https://github.com/opensagetv-vibe/"
            "opensagetv-vibe-android-client/releases/latest",
            self.page,
        )

    def test_error_text_is_not_injected_as_html(self):
        self.assertNotIn("innerHTML", self.page)
        self.assertIn("details.textContent", self.page)


if __name__ == "__main__":
    unittest.main()
