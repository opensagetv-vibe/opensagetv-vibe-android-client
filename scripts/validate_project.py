#!/usr/bin/env python3
from __future__ import annotations
import csv
import json
import os
import re
from pathlib import Path

DEFAULT_WORKSPACE = Path(__file__).resolve().parents[1]
WORKSPACE = Path(os.environ.get("SAGETV_WORKSPACE", str(DEFAULT_WORKSPACE)))
SRC = Path(os.environ.get("SAGETV_DEV_SOURCE", str(WORKSPACE / "source/dev")))
EXISTING = Path(os.environ.get("SAGETV_EXISTING_SOURCE", str(WORKSPACE / "source/existing")))
FROZEN_BASELINE = Path(os.environ.get("SAGETV_FROZEN_BASELINE", str(WORKSPACE / "source/FROZEN_BASELINE.sha256")))


def unified_dockerfile() -> Path:
    candidates: list[Path] = []
    configured = os.environ.get("OPENSAGETV_VIBE_BUILD_ENV_ROOT")
    if configured:
        candidates.append(Path(configured) / "Dockerfile")
    candidates.extend(
        (
            WORKSPACE.parent / "opensagetv-vibe-build-env" / "Dockerfile",
            WORKSPACE.parent / "release-manifest" / "Dockerfile",
        )
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    fail("opensagetv-vibe-build-env/Dockerfile is unavailable")
    raise AssertionError("unreachable")


def fail(msg: str) -> None:
    print(f"FAIL: {msg}")
    raise SystemExit(1)


def load_sha256_manifest(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for raw in path.read_text(encoding="ascii").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or len(parts[0]) != 64:
            fail(f"invalid frozen baseline manifest line: {raw}")
        digest, rel = parts
        entries[rel.lstrip("*").replace("\\", "/")] = digest.lower()
    return entries


def active_firebase_hits(root: Path):
    excluded_dirs = {".git", ".gradle", "build", "playstore"}
    include_suffixes = {".java", ".kt", ".gradle", ".xml", ".properties", ".yml", ".yaml"}
    include_names = {"Jenkinsfile", ".gitignore"}
    for p in root.rglob("*"):
        if (
            not p.is_file()
            or p.name == "verification-metadata.xml"
            or any(part in excluded_dirs for part in p.parts)
        ):
            continue
        if p.suffix.lower() not in include_suffixes and p.name not in include_names:
            continue
        for i, line in enumerate(p.read_text(errors="ignore").splitlines(), 1):
            low = line.lower()
            if "firebase" in low or "crashlytics" in low or "google-services.json" in low:
                yield p.relative_to(root), i, line.strip()


def main() -> int:
    if not SRC.exists() or not (SRC / ".dev-refactor.json").exists():
        fail("Dev source/refactor is missing. The full project should include source/dev.")
    full_existing = EXISTING.exists() and (EXISTING / "gradlew").exists()
    if not full_existing and not FROZEN_BASELINE.exists():
        fail("Neither full source/existing nor source/FROZEN_BASELINE.sha256 is available.")
    frozen_hashes = {} if full_existing else load_sha256_manifest(FROZEN_BASELINE)

    report = json.loads((SRC / ".dev-refactor.json").read_text())
    tv = (SRC / "android-tv" / "build.gradle").read_text(errors="ignore")
    if 'applicationId "opensagetv.vibe.miniclient"' not in tv:
        fail("development applicationId not set")
    if tv.count("OpenSageTV Vibe") < 2:
        fail("development app label not set")
    if report.get("expected_debug_application_id") != "opensagetv.vibe.miniclient.debug":
        fail("unexpected Dev debug package identity in refactor report")
    if report.get("behavioral_player_code_modified") is not False:
        fail("source-import baseline refactor unexpectedly reports player behavior changes")
    if report.get("firebase_removed_from_active_code") is not True:
        fail("refactor report does not confirm complete active Firebase removal")

    hits = list(active_firebase_hits(SRC))
    if hits:
        rel, line, text = hits[0]
        fail(f"active Firebase/Crashlytics reference remains: {rel}:{line}: {text}")

    logger = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/util/Logger.java").read_text(errors="ignore")
    if "FirebaseCrashlytics" in logger:
        fail("Logger still references FirebaseCrashlytics")
    if 'log.error("Unhandled exception", t);' not in logger:
        fail("Logger no longer preserves exception stack traces locally")
    if 'ILogger getLoggerInstance(String name)' not in logger:
        fail("Logger does not implement ILogger.getLoggerInstance(String)")
    if 'ILogger getLoggerInstance(Class cls)' not in logger:
        fail("Logger does not implement ILogger.getLoggerInstance(Class)")
    if 'boolean isTraceEnabled()' in logger or 'boolean isDebugEnabled()' in logger:
        fail("Logger contains @Override methods not present in the current ILogger contract")


    client_gen = (SRC / "core/src/main/java/opensagetv/vibe/miniclient/util/ClientIDGenerator.java").read_text(errors="ignore")
    ui_lifecycle = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java").read_text(errors="ignore")
    connection = (SRC / "core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java").read_text(errors="ignore")
    random_resolver = (SRC / "core/src/main/java/opensagetv/vibe/miniclient/util/RandomMACAddressResolver.java").read_text(errors="ignore")
    settings = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/settings/SettingsFragment.java").read_text(errors="ignore")
    if "DEV_FIXED_CLIENT_ID" in client_gen:
        fail("ClientIDGenerator still forces the old Dev test client ID")
    if "id = gen.generateId();" not in ui_lifecycle or "client.properties().setString(Keys.client_id, id);" not in ui_lifecycle:
        fail("Android client ID resolver does not use generated/persisted original behavior")
    if "id = gen.generateId();" not in random_resolver or "prefStore.setString(PrefStore.Keys.client_id, id);" not in random_resolver:
        fail("shared client ID resolver does not use generated/persisted original behavior")
    if "this.myID = myID;" not in connection or "this.myID = msi.macAddress;" not in connection:
        fail("MiniClientConnection no longer preserves original base/per-server client ID behavior")
    if "clientid.setEnabled(false);" in settings:
        fail("client ID preference is incorrectly disabled")
    if "clientid.setOnPreferenceChangeListener" not in settings:
        fail("client ID preference is no longer editable")
    if report.get("client_id_mode") != "generated_persisted_with_cli_override":
        fail("refactor report does not record generated/persisted client ID mode")
    if report.get("automated_test_client_id_default") != "44:45:56:30:30:31":
        fail("refactor report does not record the deterministic automated-test client ID")
    dev_sh = (WORKSPACE / "dev.sh").read_text(errors="ignore")
    if 'DEFAULT_AUTOMATED_TEST_CLIENT_ID="44:45:56:30:30:31"' not in dev_sh:
        fail("automated MCP/player tests no longer default to DEV001 / 44:45:56:30:30:31")
    if 'run_automated_mcp_test()' not in dev_sh or not re.search(
        r'mcp_client_id\.py"? --ensure "\$client_id" --quiet', dev_sh
    ):
        fail("automated MCP/player test client-ID ensure wrapper is missing")
    if 'run_scripted_launch()' not in dev_sh or 'run_scripted_launch "$@"' not in dev_sh:
        fail("scripted launch client-ID wrapper is missing")
    if 'SCRIPTED LAUNCH CLIENT ID' not in dev_sh:
        fail("scripted launch no longer reports its deterministic client ID")

    pref_store = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/prefs/AndroidPrefStore.java").read_text(errors="ignore")
    prefs_xml = (SRC / "android-shared/src/main/res/xml/prefs.xml").read_text(errors="ignore")
    if 'STREAMING_MODE_DEFAULT = "dynamic";' not in pref_store:
        fail("Dev streaming mode default is not dynamic in AndroidPrefStore")
    streaming_match = re.search(r'<ListPreference\b[^>]*android:key="streaming_mode"[^>]*/>', prefs_xml, flags=re.S)
    streaming_block = streaming_match.group(0) if streaming_match else ""
    if 'android:defaultValue="dynamic"' not in streaming_block:
        fail("Dev streaming_mode preference XML default is not dynamic")
    if report.get("default_streaming_mode") != "dynamic":
        fail("refactor report does not record dynamic Dev streaming mode default")

    adb_py = (WORKSPACE / "mcp/src/sagetv_dev_mcp/adb.py").read_text(errors="ignore")
    install_block = adb_py.split("def install_dev_apk", 1)[-1].split("\n    def ", 1)[0]
    if '["uninstall", self.dev_package]' not in install_block:
        fail("Dev APK installer does not uninstall the Dev package before install")
    if '["install", "-r", str(apk)]' in install_block:
        fail("Dev APK installer still uses replacement install instead of clean install")

    # Keep the shared SageTV player lifecycle frozen while allowing backend-specific
    # codec selector/refactor work. Both GDX and OpenGL renderers must route through
    # the centralized four-backend PlayerFactory. Internal telemetry remains prohibited.
    import hashlib

    frozen_legacy_rels = (
        (
            "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java",
            "android-shared/src/main/java/sagex/miniclient/android/video/BaseMediaPlayerImpl.java",
        ),
    )
    for rel, baseline_rel in frozen_legacy_rels:
        # The active application owns the new OpenSageTV Vibe namespace. Normalize
        # only that mechanical namespace migration before comparing the frozen
        # behavioral player implementation with the known-good source baseline.
        src_bytes = (SRC / rel).read_bytes().replace(
            b"opensagetv.vibe.miniclient", b"sagex.miniclient"
        )
        src_digest = hashlib.sha256(src_bytes).hexdigest()
        if full_existing:
            baseline_path = EXISTING / baseline_rel
            if not baseline_path.exists():
                fail(f"full source/existing is missing frozen baseline file: {baseline_rel}")
            baseline_digest = hashlib.sha256(baseline_path.read_bytes()).hexdigest()
        else:
            baseline_digest = frozen_hashes.get(baseline_rel)
            if not baseline_digest:
                fail(f"frozen baseline manifest is missing: {baseline_rel}")
        # Reviewed BaseMediaPlayerImpl exceptions are pinned by whole-file hash:
        # embedded-preview fullscreen promotion, clearing old-session EOS after
        # release, the state-aware delayed promotion that cannot toggle an
        # existing SageTV OSD back to Main Menu, and the explicitly authorized
        # generation-based session controller that rejects stale queued work,
        # the capability-negotiated DVD highlight lifecycle which is
        # attached/detached with the owning playback session, and the bounded
        # read-only DVD presentation diagnostics accessor. The newest reviewed
        # form adds only negotiated DVD MIM transport identification and routes
        # its DVB subtitles through the ordinary player track selector.
        reviewed_digests = {
            baseline_digest,
            "d270708006d02379b2ebcbc2ff5390a17d303ab22cf7072c31de9ab43adea3b4",
            "a4a2cd8ca0ffca84cc9b5dccb8681d5acabbdf3df7a8e3798e464d7d31e18494",
            "5697071e39aa4fa5366a8142609539b7fa1745968c597395ea7cb809d6d19b45",
            "acfb0c04c5dc3c967a5eaac9b814a5274fbe1534f3fd8fadf1c7067bd5f0a0a8",
            "8665d330cf3864e1564a1bcb4e1d5ac5d718816bbf15985c9bd5e86729b6f253",
            "63fc269f13e7e5a75f4e55b8e3b39016e9056ffc6c71baf117e6c8b596ebc9dd",
            # DVD overlay scheduling diagnostics used to prove buffered SPU
            # generation/clock behavior on the physical Fire TV gate.
            "adfd6d1d958a438ddaf1d0cfffabf88e7ff93b9357891455f74491b7b63d41bc",
            "5f3222fe3ae48b589793b74c33c93348a743f5c48c869754b1c09d97836dd2e8",
            # Reviewed DVD presentation form after the complete remote-DVD
            # implementation: session ownership, negotiated Native/MIM
            # transport identity, clocked SPU/highlight scheduling, and
            # read-only presentation diagnostics. Codec timing repair remains
            # below the backend/extractor layer and does not alter this pin.
            "a9f89123ce37b9cf0bbe9071f18a6bdd8dc670a1dc6ecaf1870b212e5428529d",
            # Reviewed Active Player Adjustments form: bounded subtitle
            # presentation offset, session-scoped runtime tuning, and
            # current-resolution display refresh apply/restore. These remain
            # presentation-only and preserve the playback-session generation,
            # transport, seek, and fullscreen ownership invariants above.
            "61f477c818b640ac2f4cba547baa5db38717319efe2104314e1bbb2ff97c5afe",
            # Reviewed legacy-extender caption callback form. The only shared
            # lifecycle change is a protected per-load hook used by extractor
            # backends to flush caption state before a new playback session.
            "f34e82d2118603672a02ef1a5141603d0387f3c21b53ea0aa989706b3f5337f8",
        }
        if src_digest not in reviewed_digests:
            fail(f"known-good legacy playback runtime changed: {rel}")

    exo_rel = "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/exoplayer2/Exo2MediaPlayerImpl.java"
    ijk_rel = "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java"
    gsy_rel = "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/GSYMediaPlayerImpl.java"
    exo_text = (SRC / exo_rel).read_text(errors="ignore")
    ijk_text = (SRC / ijk_rel).read_text(errors="ignore")
    gsy_text = (SRC / gsy_rel).read_text(errors="ignore")
    for rel, text in ((exo_rel, exo_text), (ijk_rel, ijk_text), (gsy_rel, gsy_text)):
        if "PlayerTelemetry" in text or "telemetry." in text:
            fail(f"playback runtime contains internal telemetry hooks: {rel}")

    player_backend = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/PlayerBackend.java").read_text(errors="ignore")
    player_factory = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/PlayerFactory.java").read_text(errors="ignore")
    opengl_renderer = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/opengl/OpenGLRenderer.java").read_text(errors="ignore")
    gdx_renderer = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/gdx/MiniClientGDXRenderer.java").read_text(errors="ignore")
    for backend_id in ('EXOPLAYER("exoplayer"', 'MEDIA3("media3"', 'IJKPLAYER("ijkplayer"', 'GSYPLAYER("gsyplayer"'):
        if backend_id not in player_backend:
            fail(f"PlayerBackend missing stable backend ID: {backend_id}")
    if 'DEFAULT_PREFERENCE = "exoplayer"' not in player_backend:
        fail("known-good legacy ExoPlayer is not the default backend")
    for marker in ("new Exo2MediaPlayerImpl(activity)", "new Media3MediaPlayerImpl(activity)", "new IJKMediaPlayerImpl(activity)", "new GSYMediaPlayerImpl(activity)"):
        if marker not in player_factory:
            fail(f"PlayerFactory backend mapping missing: {marker}")
    for name, renderer in (("OpenGLRenderer", opengl_renderer), ("MiniClientGDXRenderer", gdx_renderer)):
        if "PlayerBackend.fromPreference" not in renderer or "PlayerFactory.create" not in renderer:
            fail(f"{name} bypasses centralized four-backend PlayerFactory")

    media3_dir = SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3"
    media3_files = list(media3_dir.glob("*.java"))
    if not media3_files or not (media3_dir / "Media3MediaPlayerImpl.java").exists():
        fail("Media3 backend source is missing")
    media3_text = "\n".join(f.read_text(errors="ignore") for f in media3_files)
    if "androidx.media3" not in media3_text:
        fail("Media3 backend does not use AndroidX Media3")
    if "com.google.android.exoplayer2" in media3_text:
        fail("Media3 backend is source-coupled to legacy ExoPlayer")
    if "PlayerTelemetry" in media3_text or "telemetry." in media3_text:
        fail("Media3 backend contains prohibited internal telemetry hooks")
    if "extension-ffmpeg" in media3_text.lower() or "FfmpegLibrary" in media3_text:
        fail("Media3 backend is coupled to the legacy ExoPlayer FFmpeg extension")

    media3_player = (media3_dir / "Media3MediaPlayerImpl.java").read_text(errors="ignore")
    if "player.setVideoSurfaceView((SurfaceView) context.getVideoView())" not in media3_player:
        fail("Media3 does not bind a SurfaceView video output")

    gdx_activity = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/gdx/MiniClientGDXActivity.java").read_text(errors="ignore")
    if "glView.setZOrderMediaOverlay(true)" not in gdx_activity or "PixelFormat.RGBA_8888" not in gdx_activity:
        fail("GDX video/OSD media-overlay ordering is missing")
    if "glView.setZOrderOnTop(true)" in gdx_activity:
        fail("GDX UI is above normal Android caption overlays")
    for player_name in ("exoplayer2/Exo2MediaPlayerImpl.java", "media3/Media3MediaPlayerImpl.java"):
        player_text = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video" / player_name).read_text(errors="ignore")
        if ("subView.bringToFront();" not in player_text
                or "subView.setElevation(100.0f);" not in player_text
                or "findViewById(android.R.id.content)" not in player_text):
            fail(f"caption overlay ordering is incomplete in {player_name}")

    player_diag = (WORKSPACE / "scripts/player_diagnostics.py").read_text(errors="ignore")
    if "dumpsys SurfaceFlinger" not in player_diag or "adb.logcat_tail(10000)" not in player_diag:
        fail("external player diagnostics script is missing crash/surface collection")

    root_gradle = (SRC / "build.gradle").read_text(errors="ignore")
    core_gradle = (SRC / "core/build.gradle").read_text(errors="ignore")
    shared_gradle = (SRC / "android-shared/build.gradle").read_text(errors="ignore")
    canonical_gradle_path = WORKSPACE / "config/dev-root-build.gradle.canonical"
    if not canonical_gradle_path.is_file():
        fail("Canonical Dev root Gradle recovery file is missing")
    canonical_gradle = canonical_gradle_path.read_text(errors="ignore")
    if root_gradle != canonical_gradle:
        fail("Dev root build.gradle does not match the canonical recovery copy; run ./dev.sh validate again to auto-repair")
    if 'apply plugin: "java"' in root_gradle or root_gradle == core_gradle:
        fail("Dev root build.gradle appears to have been replaced by core/build.gradle")
    if "buildscript {" not in root_gradle:
        fail("Dev root build.gradle is missing the Android buildscript block")
    wrapper = (SRC / "gradle/wrapper/gradle-wrapper.properties").read_text(errors="ignore")
    dockerfile = unified_dockerfile().read_text(errors="ignore")
    if "media3Version = '1.11.0'" not in root_gradle:
        fail("Media3 is not pinned to 1.11.0")
    if "exoVersion = '2.18.1'" not in root_gradle:
        fail("known-good legacy ExoPlayer 2.18.1 baseline changed")
    if "details.requested.group == 'com.google.android.exoplayer'" not in root_gradle or "details.useVersion exoVersion" not in root_gradle:
        fail("legacy ExoPlayer dependency graph is not strictly version-aligned")
    legacy_exo_modules = (
        "exoplayer-common", "exoplayer-extractor", "exoplayer-core", "exoplayer-ui",
        "exoplayer-datasource", "exoplayer-decoder", "exoplayer-dash", "exoplayer-rtsp",
        "exoplayer-transformer", "exoplayer-smoothstreaming", "exoplayer-hls",
    )
    for module in legacy_exo_modules:
        coordinate = f"com.google.android.exoplayer:{module}:${{exoVersion}}"
        if coordinate not in shared_gradle:
            fail(f"legacy ExoPlayer dependency missing: {module}")
        if coordinate + "@aar" in shared_gradle:
            fail(f"legacy ExoPlayer {module} still uses artifact-only @aar notation")
    if "exoplayer-testutils" in shared_gradle:
        fail("ExoPlayer testutils is packaged in the production android-shared module")
    if 'api(name: "ijkplayer-exo-' in shared_gradle:
        fail("obsolete IJK Exo wrapper is still packaged and can introduce legacy Exo API coupling")
    if "ijkVersionDev = '0.8.8-SNAPSHOT'" not in root_gradle:
        fail("legacy IJK 0.8.8-SNAPSHOT baseline changed")
    if 'androidMinSdkVersion = 23' not in root_gradle:
        fail("Media3-capable minSdk 23 not configured")
    if 'androidCompileSdkVersion = 36' not in root_gradle or 'androidBuildToolsVersion = "36.0.0"' not in root_gradle:
        fail("Android SDK 36 / Build Tools 36.0.0 modernization missing")
    if "com.android.tools.build:gradle:8.13.2" not in root_gradle:
        fail("Android Gradle Plugin 8.13.2 not configured")
    if "gradle-8.13-bin.zip" not in wrapper:
        fail("Gradle wrapper is not 8.13")
    if not re.search(r"ARG ANDROID_JDK17_IMAGE=eclipse-temurin:17-jdk-jammy@sha256:[0-9a-f]{64}", dockerfile):
        fail("Docker Dev build image is not digest-pinned JDK 17")
    if (
        not re.search(
            r"ARG ANDROID_JDK8_IMAGE=eclipse-temurin:8-jdk-jammy@sha256:[0-9a-f]{64}",
            dockerfile,
        )
        or "/opt/java/jdk8" not in dockerfile
    ):
        fail("Docker image does not preserve digest-pinned JDK 8 for baseline builds")
    for sdk_marker in ('"platforms;android-29"', '"build-tools;29.0.2"', '"platforms;android-36"', '"build-tools;36.0.0"'):
        if sdk_marker not in dockerfile:
            fail(f"Docker dual Android SDK toolchain missing: {sdk_marker}")
    for module in ("media3-common", "media3-datasource", "media3-exoplayer", "media3-ui"):
        if f"androidx.media3:{module}:${{media3Version}}" not in shared_gradle:
            fail(f"Media3 dependency missing: {module}")
    if "gsyVersion = '13.1.0'" not in root_gradle:
        fail("GSYVideoPlayer is not pinned to 13.1.0")
    for coordinate in ("io.github.carguo:gsyvideoplayer-java:${gsyVersion}", "io.github.carguo:gsyvideoplayer-exo2:${gsyVersion}"):
        if coordinate not in shared_gradle:
            fail(f"GSY framework dependency missing: {coordinate}")
    if "gsyvideoplayer-ex_so" in shared_gradle:
        fail("GSY IJK ex_so must not be packaged next to legacy IJK")
    if 'exclude group: "io.github.carguo", module: "gsyijkjava"' not in shared_gradle:
        fail("GSY modern IJK Java bridge is not excluded")
    for legacy_ijk in ('api(name: "ijkplayer-java-${ijkVersionDev}"', 'api(name: "ijkplayer-armv7a-${ijkVersionDev}"', 'api(name: "ijkplayer-arm64-${ijkVersionDev}"'):
        if legacy_ijk not in shared_gradle:
            fail("legacy IJK 0.8.8 ARM runtime is not fully restored")
    if 'api(name: "ijkplayer-x86-${ijkVersionDev}"' in shared_gradle:
        fail("unsupported incomplete x86 IJK runtime is still packaged")
    if "org.slf4j:slf4j-api:1.7.6" not in shared_gradle:
        fail("android-shared does not explicitly depend on SLF4J under Gradle 8")
    if "androidx.appcompat:appcompat:1.3.1" not in shared_gradle:
        fail("android-shared does not explicitly depend on AppCompat for its dialog theme")
    auto_connect = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/AutoConnectDialog.java").read_text(errors="ignore")
    if "androidx.appcompat.R.style.Theme_AppCompat_Dialog" not in auto_connect:
        fail("AutoConnectDialog still relies on a transitive/internal AppCompat R style")
    if "R.style.Base_Theme_AppCompat_Dialog" in auto_connect:
        fail("AutoConnectDialog still references removed Base_Theme_AppCompat_Dialog")

    tv_resource_files = [
        SRC / "android-tv/src/main/java/opensagetv/vibe/miniclient/android/phone/ServersAdapter.java",
        SRC / "android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/MainFragment.java",
        SRC / "android-tv/src/main/java/opensagetv/vibe/miniclient/android/tv/ServerItemPresenter.java",
    ]
    tv_resource_text = "\n".join(path.read_text(errors="ignore") for path in tv_resource_files)
    for drawable in ("iconbutton_background", "ic_add_to_queue_white_60dp", "ic_tv_white_60dp", "sage_logo_256"):
        shared_ref = f"opensagetv.vibe.miniclient.android.R.drawable.{drawable}"
        if shared_ref not in tv_resource_text:
            fail(f"android-tv does not reference shared drawable through android-shared R: {drawable}")
        stripped = tv_resource_text.replace(shared_ref, "")
        if f"R.drawable.{drawable}" in stripped:
            fail(f"android-tv still relies on transitive R for android-shared drawable: {drawable}")

    ijk_player = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IJKMediaPlayerImpl.java").read_text(errors="ignore")
    ijk_options = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/IjkDecoderOptions.java").read_text(errors="ignore")
    decoding_method = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DecodingMethod.java").read_text(errors="ignore")
    pref_store_keys = (SRC / "core/src/main/java/opensagetv/vibe/miniclient/prefs/PrefStore.java").read_text(errors="ignore")
    ijk_prefs = (SRC / "android-shared/src/main/res/xml/ijkplayer_prefs.xml").read_text(errors="ignore")
    gsy_prefs = (SRC / "android-shared/src/main/res/xml/gsyplayer_prefs.xml").read_text(errors="ignore")
    if 'String decoding_method = "decoding_method"' not in pref_store_keys:
        fail("shared Decoding Method preference key is missing")
    if 'DEFAULT_PREFERENCE = "hardware"' not in decoding_method:
        fail("Decoding Method does not default to Hardware")
    for marker in ('HARDWARE("hardware", "Hardware")', 'SOFTWARE("software", "Software")', 'HARDWARE_PREFERRED("hardware_preferred", "Fallback")'):
        if marker not in decoding_method:
            fail(f"Decoding Method option missing: {marker}")
    if "IjkDecoderOptions.apply" not in ijk_player:
        fail("IJK backend does not use the shared decoder option mapper")
    for marker in ('"mediacodec-mpeg2", mpeg2Hardware ? 1 : 0', 'CodecSelector.isKnownBrokenMpeg2HardwareDevice()', 'PrefStore.Keys.ijk_mediacodec_mpeg2'):
        if marker not in ijk_options:
            fail(f"maintained IJK hardware/fallback option missing: {marker}")
    for filename, text in (("ijkplayer_prefs.xml", ijk_prefs), ("gsyplayer_prefs.xml", gsy_prefs)):
        if 'android:key="decoding_method"' not in text or 'android:defaultValue="hardware"' not in text:
            fail(f"{filename} does not expose Hardware-default shared Decoding Method")
    if "return true;" not in ijk_player.split("setOnErrorListener", 1)[-1].split("});", 1)[0]:
        fail("IJK player error callback is not marked handled")

    gsy_impl = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/GSYMediaPlayerImpl.java").read_text(errors="ignore")
    gsy_engine = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/GSYPlayerEngine.java").read_text(errors="ignore")
    gsy_system = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/GSYSystemMediaPlayerImpl.java").read_text(errors="ignore")
    if "extends IJKMediaPlayerImpl" in gsy_impl or "useGsyProfile" in gsy_impl:
        fail("GSY backend still inherits or configures legacy IJK")
    if "PrefStore.Keys.gsy_player_engine" not in gsy_impl:
        fail("GSY engine selector preference is not used")
    for marker in ('AUTO("auto"', 'MEDIA3("media3"', 'SYSTEM("system"', 'LEGACY_EXO("legacy_exo"'):
        if marker not in gsy_engine:
            fail("GSY selectable engine missing: " + marker)
    if "MediaDataSource" not in gsy_system or "IJKMediaPlayerImpl" in gsy_system:
        fail("GSY System engine is not independently SageTV data-source aware")
    ijk_selector = (SRC / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/ijkplayer/CodecSelector.java").read_text(errors="ignore")
    if "com.google.android.exoplayer2.util.Util" in ijk_selector or "Util.SDK_INT" in ijk_selector:
        fail("IJK codec selector still depends on legacy ExoPlayer Util")
    if "Build.VERSION.SDK_INT" not in ijk_selector:
        fail("IJK codec selector Android API decoupling missing")
    if "isKnownBrokenMpeg2HardwareDevice()" not in ijk_selector:
        fail("IJK Fire TV mantis MPEG-2 safety fallback is missing")
    if '"mantis".equalsIgnoreCase(Build.DEVICE)' not in ijk_selector or '"AFTMM".equalsIgnoreCase(Build.MODEL)' not in ijk_selector:
        fail("IJK Fire TV AFTMM/mantis safety guard is missing")
    if "Rejecting broken Amazon AFTMM/mantis MPEG-2 MediaCodec" not in ijk_selector:
        fail("IJK Fire TV MPEG-2 rejection log is missing")
    if "FIRE_TV_MANTIS_MPEG2_CODEC" in ijk_selector:
        fail("IJK still force-selects the confirmed-broken Fire TV MPEG-2 hardware decoder")

    prefs_xml = (SRC / "android-shared/src/main/res/xml/prefs.xml").read_text(errors="ignore")
    arrays_xml = (SRC / "android-shared/src/main/res/values/arrays.xml").read_text(errors="ignore")
    if 'android:defaultValue="exoplayer"' not in prefs_xml:
        fail("player preference default is no longer legacy ExoPlayer")
    for value in ("exoplayer", "media3", "ijkplayer", "gsyplayer"):
        if f">{value}<" not in arrays_xml:
            fail(f"player preference value missing: {value}")
    for settings_key in ("media3_settings", "ijkplayer_settings", "gsyplayer_settings"):
        if f'android:key="{settings_key}"' not in prefs_xml:
            fail(f"player settings entry missing: {settings_key}")
    if 'android:key="decoding_method"' not in prefs_xml or 'android:defaultValue="hardware"' not in prefs_xml:
        fail("global Decoding Method preference is missing or not Hardware-default")
    for settings_xml in ("exoplayer_prefs.xml", "media3player_prefs.xml", "ijkplayer_prefs.xml", "gsyplayer_prefs.xml"):
        text = (SRC / f"android-shared/src/main/res/xml/{settings_xml}").read_text(errors="ignore")
        if 'android:key="decoding_method"' not in text or 'android:defaultValue="hardware"' not in text:
            fail(f"shared Decoding Method missing from {settings_xml}")
    entrypoint = (WORKSPACE / "docker/entrypoint.sh").read_text(errors="ignore")
    if "./gradlew --no-daemon clean :android-tv:assembleDebug" not in entrypoint:
        fail("Dev APK build is not configured as a clean deterministic build")
    if "JAVA_HOME=/opt/java/jdk8 PATH=/opt/java/jdk8/bin:$PATH ./gradlew --no-daemon :android-tv:assembleDebug" not in entrypoint:
        fail("untouched baseline build is not pinned to bundled JDK 8")

    inventory_path = WORKSPACE / "third_party/RUNTIME_DEPENDENCIES.csv"
    if not inventory_path.is_file():
        fail("complete runtime dependency inventory is missing")
    with inventory_path.open(encoding="utf-8", newline="") as source:
        runtime_inventory = list(csv.DictReader(source))
    if len(runtime_inventory) != 122:
        fail(f"runtime dependency inventory count changed: {len(runtime_inventory)}")
    for row in runtime_inventory:
        if not row.get("license") or "UNKNOWN" in row["license"].upper():
            fail(f"unreviewed runtime dependency license: {row.get('coordinate')}")
        if "ostermiller" in row.get("coordinate", "").lower():
            fail("removed GPL Ostermiller dependency remains in runtime inventory")
        for license_file in row.get("license_files", "").split(";"):
            if not license_file or not (WORKSPACE / license_file).is_file():
                fail(f"runtime dependency license file is missing: {license_file}")
    for required_release_file in (
        "THIRD_PARTY_NOTICES.md",
        "third_party/source-offers/README.md",
        "scripts/create_github_release_bundle.py",
    ):
        if not (WORKSPACE / required_release_file).is_file():
            fail(f"release dependency material is missing: {required_release_file}")

    provenance = EXISTING.parent / "SOURCE_IMPORT.json"
    if not provenance.exists():
        fail("source/SOURCE_IMPORT.json provenance metadata missing")

    if full_existing:
        print("PASS: full source trees present")
    else:
        print("PASS: compact AI handoff source present; frozen baseline verified by source/FROZEN_BASELINE.sha256")
    print("PASS: Dev application identity isolated from existing MiniClient")
    print("PASS: active Firebase/Crashlytics/Google Services references removed")
    print("PASS: shared BaseMediaPlayerImpl is pinned to a reviewed known-good form")
    print("PASS: legacy ExoPlayer modules strictly aligned to 2.18.1; obsolete Exo wrappers/testutils removed")
    print("PASS: four-backend PlayerFactory configured for both GDX and OpenGL renderers")
    print("PASS: Media3 1.11.0 backend isolated from legacy ExoPlayer/FFmpeg and telemetry")
    print("PASS: IJK codec selection decoupled from legacy ExoPlayer utility classes")
    print("PASS: shared Decoding Method configured: Hardware / Software / Fallback (Hardware default)")
    print("PASS: GSYVideoPlayer 13.1.0 selector is isolated from legacy IJK and offers Auto/Media3/System/Legacy Exo")
    print("PASS: original IJK 0.8.8 runtime restored with legacy-only decoder policy and AFTMM MPEG-2 guard")
    print("PASS: dual Docker toolchain preserves JDK 8/SDK 29 baseline and JDK 17/SDK 36 Dev build")
    print("PASS: Dev uses AGP 8.13.2 / Gradle 8.13 for Media3 modernization")
    print("PASS: android-shared explicitly carries SLF4J and public AppCompat dialog resources")
    print("PASS: android-tv explicitly references android-shared drawables through the shared R class")
    print("PASS: original generated/persisted SageTV client ID behavior configured; CLI override supported")
    print("PASS: fresh Dev installs default to dynamic streaming mode")
    print("PASS: Dev APK install is clean uninstall-then-install")
    print("PASS: clean deterministic Dev APK build configured")
    print("PASS: legacy Exo remains the default known-good playback backend")
    print("PASS: local SLF4J exception logging retained")
    print("PASS: 122-coordinate runtime license inventory and native source material packaged")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
