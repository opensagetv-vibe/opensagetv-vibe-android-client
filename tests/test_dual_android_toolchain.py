import os
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def unified_dockerfile() -> Path:
    candidates = []
    if os.environ.get("OPENSAGETV_VIBE_BUILD_ENV_ROOT"):
        candidates.append(Path(os.environ["OPENSAGETV_VIBE_BUILD_ENV_ROOT"]) / "Dockerfile")
    candidates.extend(
        (
            ROOT.parent / "opensagetv-vibe-build-env" / "Dockerfile",
            ROOT.parent / "release-manifest" / "Dockerfile",
        )
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise AssertionError("opensagetv-vibe-build-env/Dockerfile is unavailable")


class DualAndroidToolchainTests(unittest.TestCase):
    def test_docker_carries_dev_and_legacy_jdks(self):
        text = unified_dockerfile().read_text()
        self.assertRegex(
            text,
            r"ARG ANDROID_JDK8_IMAGE=eclipse-temurin:8-jdk-jammy@sha256:[0-9a-f]{64}",
        )
        self.assertRegex(
            text,
            r"ARG ANDROID_JDK17_IMAGE=eclipse-temurin:17-jdk-jammy@sha256:[0-9a-f]{64}",
        )
        self.assertIn("COPY --from=android-jdk8 /opt/java/openjdk /opt/java/jdk8", text)
        self.assertIn("COPY --from=android-jdk17 /opt/java/openjdk /opt/java/jdk17", text)

    def test_docker_carries_old_and_new_android_sdks(self):
        text = unified_dockerfile().read_text()
        for marker in (
            '"platforms;android-29"',
            '"build-tools;29.0.2"',
            '"platforms;android-36"',
            '"build-tools;36.0.0"',
            '"ndk;21.0.6113669"',
        ):
            self.assertIn(marker, text)
        self.assertIn("ARG ANDROID_PLATFORM_TOOLS_VERSION=37.0.1", text)
        self.assertIn("Pkg.Revision=${ANDROID_PLATFORM_TOOLS_VERSION}", text)

    def test_python_and_gradle_download_graphs_are_locked(self):
        dockerfile = unified_dockerfile()
        text = dockerfile.read_text(encoding="utf-8")
        python_lock = dockerfile.parent / "android-requirements.lock"
        self.assertTrue(python_lock.is_file())
        self.assertIn("COPY android-requirements.lock", text)
        self.assertIn("-r /opt/opensagetv-vibe/android-requirements.lock", text)
        for line in python_lock.read_text(encoding="utf-8").splitlines():
            if line and not line.startswith("#"):
                self.assertRegex(line, r"^[A-Za-z0-9_.-]+==[^<>= ]+$")

        dev = ROOT / "source" / "dev"
        wrapper = (dev / "gradle/wrapper/gradle-wrapper.properties").read_text()
        self.assertRegex(wrapper, r"distributionSha256Sum=[0-9a-f]{64}")
        self.assertTrue((dev / "gradle/verification-metadata.xml").is_file())
        for project in ("core", "android-shared", "android-tv"):
            self.assertTrue((dev / project / "gradle.lockfile").is_file())

    def test_existing_build_explicitly_uses_jdk8(self):
        text = (ROOT / "docker/entrypoint.sh").read_text()
        self.assertIn(
            "JAVA_HOME=/opt/java/jdk8 PATH=/opt/java/jdk8/bin:$PATH ./gradlew --no-daemon :android-tv:assembleDebug",
            text,
        )
        self.assertIn("./gradlew --no-daemon clean :android-tv:assembleDebug", text)


if __name__ == "__main__":
    unittest.main()
