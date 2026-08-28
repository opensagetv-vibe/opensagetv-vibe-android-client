import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class DualAndroidToolchainTests(unittest.TestCase):
    def test_docker_carries_dev_and_legacy_jdks(self):
        text = (ROOT / "docker/Dockerfile").read_text()
        self.assertIn("FROM eclipse-temurin:8-jdk-jammy AS legacy-jdk8", text)
        self.assertIn("FROM eclipse-temurin:17-jdk-jammy", text)
        self.assertIn("COPY --from=legacy-jdk8 /opt/java/openjdk /opt/java/jdk8", text)

    def test_docker_carries_old_and_new_android_sdks(self):
        text = (ROOT / "docker/Dockerfile").read_text()
        for marker in (
            '"platforms;android-29"',
            '"build-tools;29.0.2"',
            '"platforms;android-36"',
            '"build-tools;36.0.0"',
            '"ndk;21.0.6113669"',
        ):
            self.assertIn(marker, text)

    def test_existing_build_explicitly_uses_jdk8(self):
        text = (ROOT / "docker/entrypoint.sh").read_text()
        self.assertIn(
            "JAVA_HOME=/opt/java/jdk8 PATH=/opt/java/jdk8/bin:$PATH ./gradlew --no-daemon :android-tv:assembleDebug",
            text,
        )
        self.assertIn("./gradlew --no-daemon clean :android-tv:assembleDebug", text)


if __name__ == "__main__":
    unittest.main()
