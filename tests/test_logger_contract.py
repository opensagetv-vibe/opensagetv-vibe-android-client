from __future__ import annotations

from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
LOGGER = ROOT / "source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/util/Logger.java"
ILOGGER = ROOT / "source/dev/core/src/main/java/opensagetv/vibe/miniclient/logging/ILogger.java"


class LoggerContractTests(unittest.TestCase):
    def test_logger_compiles_against_real_ilogger_contract(self):
        javac = shutil.which("javac")
        if javac is None:
            self.skipTest("javac is not available")

        with tempfile.TemporaryDirectory() as td:
            temp = Path(td)
            slf4j = temp / "org/slf4j"
            slf4j.mkdir(parents=True)
            (slf4j / "Logger.java").write_text(textwrap.dedent("""\
                package org.slf4j;
                public interface Logger {
                    void error(String message);
                    void error(String message, Throwable t);
                    void warn(String message);
                    void warn(String message, Throwable t);
                    void debug(String message);
                    void debug(String message, Throwable t);
                    void info(String message);
                    void info(String message, Throwable t);
                    void trace(String message);
                    void trace(String message, Throwable t);
                }
            """), encoding="utf-8")
            (slf4j / "LoggerFactory.java").write_text(textwrap.dedent("""\
                package org.slf4j;
                public final class LoggerFactory {
                    private LoggerFactory() {}
                    public static Logger getLogger(Class cls) { return null; }
                    public static Logger getLogger(String name) { return null; }
                }
            """), encoding="utf-8")

            classes = temp / "classes"
            classes.mkdir(parents=True, exist_ok=True)

            result = subprocess.run(
                [
                    javac,
                    "-d", str(classes),
                    str(slf4j / "Logger.java"),
                    str(slf4j / "LoggerFactory.java"),
                    str(ILOGGER),
                    str(LOGGER),
                ],
                text=True,
                capture_output=True,
            )
            self.assertEqual(
                result.returncode,
                0,
                msg="Logger.java does not satisfy the real ILogger contract:\n"
                    + result.stdout + result.stderr,
            )


if __name__ == "__main__":
    unittest.main()
