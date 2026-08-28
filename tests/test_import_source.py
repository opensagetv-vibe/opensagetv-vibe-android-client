from __future__ import annotations
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "import_source_zip.py"
spec = importlib.util.spec_from_file_location("source_import", SCRIPT)
source_import = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(source_import)


class SourceImportTests(unittest.TestCase):
    def test_locates_single_wrapped_repo(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td) / "x" / "repo-main"
            (root / "android-tv").mkdir(parents=True)
            (root / "android-shared").mkdir(parents=True)
            (root / "gradlew").write_text("#!/bin/sh\n")
            found = source_import.locate_repo(Path(td))
            self.assertEqual(found, root.resolve())

    def test_safe_extract_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as td:
            archive = Path(td) / "bad.zip"
            with zipfile.ZipFile(archive, "w") as zf:
                zf.writestr("../escape.txt", "no")
            with zipfile.ZipFile(archive) as zf:
                with self.assertRaises(RuntimeError):
                    source_import.safe_extract(zf, Path(td) / "extract")


if __name__ == "__main__":
    unittest.main()
