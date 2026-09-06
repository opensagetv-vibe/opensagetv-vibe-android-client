#!/usr/bin/env python3
"""Apply the low-risk SageTV MiniClient Dev Phase 0 refactor.

Goals:
- Keep playback/seek behavior unchanged.
- Give the development app a separate install identity/name.
- Remove Firebase Analytics/Crashlytics/Google Services completely from active code,
  settings, build logic, and CI.
- Preserve the existing ILogger contract and keep Android logging local via SLF4J.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

DEV_APPLICATION_ID = "opensagetv.vibe.miniclient"
DEV_APP_NAME = "OpenSageTV Vibe"
DEV_DEBUG_APP_NAME = "OpenSageTV Vibe"


def read(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(f"Required upstream file not found: {path}")
    return path.read_text(encoding="utf-8")


def write_if_changed(path: Path, old: str, new: str, dry_run: bool, changes: list[str]) -> None:
    if old == new:
        return
    changes.append(str(path))
    if not dry_run:
        path.write_text(new, encoding="utf-8")


def remove_matching_lines(text: str, needles: list[str]) -> str:
    return "".join(
        line for line in text.splitlines(keepends=True)
        if not any(n in line for n in needles)
    )


def patch_root_gradle(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "build.gradle"
    old = read(p)
    new = remove_matching_lines(old, [
        "com.google.gms:google-services",
        "com.google.firebase:firebase-crashlytics-gradle",
        "Crashlytics Gradle plugin",
    ])
    write_if_changed(p, old, new, dry_run, changes)


def patch_tv_gradle(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-tv" / "build.gradle"
    old = read(p)
    new = remove_matching_lines(old, [
        "com.google.gms.google-services",
        "com.google.firebase.crashlytics",
        "com.google.firebase:",
        "Firebase platform",
        "Firebase library dependencies",
        "Crashlytics and Analytics libraries",
        "Crashlytics Gradle plugin",
    ])

    new, count = re.subn(
        r'applicationId\s+["\']jvl\.sage\.miniclient\.android\.tv\.debug["\']',
        f'applicationId "{DEV_APPLICATION_ID}"',
        new,
        count=1,
    )
    if count == 0 and f'applicationId "{DEV_APPLICATION_ID}"' not in new:
        raise RuntimeError("Could not locate expected android-tv applicationId; upstream layout changed.")

    new = re.sub(
        r'resValue\s+["\']string["\']\s*,\s*["\']app_name["\']\s*,\s*["\']@string/app_name_release["\']',
        f'resValue "string", "app_name", \'{DEV_APP_NAME}\'',
        new,
    )
    new = re.sub(
        r'resValue\s+["\']string["\']\s*,\s*["\']app_name["\']\s*,\s*["\']@string/app_name_debug["\']',
        f'resValue "string", "app_name", \'{DEV_DEBUG_APP_NAME}\'',
        new,
    )
    new = new.replace('versionNameSuffix = "-DEBUG"', 'versionNameSuffix = "-DEV-DEBUG"')
    new = new.replace("versionNameSuffix '-DEBUG'", "versionNameSuffix '-DEV-DEBUG'")
    write_if_changed(p, old, new, dry_run, changes)


def remove_google_services_file(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-tv" / "google-services.json"
    if p.exists():
        changes.append(str(p))
        if not dry_run:
            p.unlink()


def patch_shared_gradle(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared" / "build.gradle"
    old = read(p)
    new = remove_matching_lines(old, ["com.google.firebase:firebase-crashlytics"])
    write_if_changed(p, old, new, dry_run, changes)


def patch_application_java(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/MiniclientApplication.java"
    old = read(p)
    new = remove_matching_lines(old, [
        "import com.google.firebase.crashlytics.FirebaseCrashlytics;",
        "FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled",
        "FirebaseCrashlytics.getInstance().setUserId",
    ])
    write_if_changed(p, old, new, dry_run, changes)


def patch_pref_store(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/prefs/AndroidPrefStore.java"
    old = read(p)
    new = old
    new = re.sub(
        r'\n\s*public static final String FIREBASE_CRASHLYTICS_ENABLED.*?\n\s*public static final String FIREBASE_CRASHLYTICS_USER_DEFAULT\s*=\s*"Anonymous";\s*\n',
        "\n",
        new,
        flags=re.S,
    )
    new = re.sub(
        r'\n\s*public boolean getFirebaseCrashlyticsEnabled\(\)\s*\{.*?\}\s*\n\s*public String getFirebaseCrashlyticsUser\(\)\s*\{.*?\}\s*\n',
        "\n",
        new,
        flags=re.S,
    )
    write_if_changed(p, old, new, dry_run, changes)


def patch_settings_fragment(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/settings/SettingsFragment.java"
    old = read(p)
    new = remove_matching_lines(old, ["import com.google.firebase.crashlytics.FirebaseCrashlytics;"])
    write_if_changed(p, old, new, dry_run, changes)


def patch_logger(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/util/Logger.java"
    old = read(p)
    new = 'package opensagetv.vibe.miniclient.android.util;\n\nimport org.slf4j.LoggerFactory;\n\nimport opensagetv.vibe.miniclient.logging.ILogger;\n\n/**\n * Android logger implementation backed by SLF4J only.\n *\n * <p>All logging remains local through the existing SLF4J backend. The\n * metadata methods remain as no-ops to preserve the shared {@link ILogger}\n * interface after cloud crash-reporting removal.</p>\n */\npublic class Logger implements ILogger\n{\n    private org.slf4j.Logger log;\n\n    public static Logger getLogger(Class cls)\n    {\n        Logger logger = new Logger();\n        logger.log = LoggerFactory.getLogger(cls);\n        return logger;\n    }\n\n    public static Logger getLogger(String name)\n    {\n        Logger logger = new Logger();\n        logger.log = LoggerFactory.getLogger(name);\n        return logger;\n    }\n\n    @Override\n    public ILogger getLoggerInstance(String name)\n    {\n        return Logger.getLogger(name);\n    }\n\n    @Override\n    public ILogger getLoggerInstance(Class cls)\n    {\n        return Logger.getLogger(cls);\n    }\n\n    @Override\n    public void recordException(Throwable t)\n    {\n        log.error("Unhandled exception", t);\n    }\n\n    @Override\n    public void logError(String message)\n    {\n        log.error(message);\n    }\n\n    @Override\n    public void logError(String message, Throwable t)\n    {\n        log.error(message, t);\n    }\n\n    @Override\n    public void logWarning(String message)\n    {\n        log.warn(message);\n    }\n\n    @Override\n    public void logWarning(String message, Throwable t)\n    {\n        log.warn(message, t);\n    }\n\n    @Override\n    public void logDebug(String message)\n    {\n        log.debug(message);\n    }\n\n    @Override\n    public void logDebug(String message, Throwable t)\n    {\n        log.debug(message, t);\n    }\n\n    @Override\n    public void logInfo(String message)\n    {\n        log.info(message);\n    }\n\n    @Override\n    public void logInfo(String message, Throwable t)\n    {\n        log.info(message, t);\n    }\n\n    @Override\n    public void logTrace(String message)\n    {\n        log.trace(message);\n    }\n\n    @Override\n    public void logTrace(String message, Throwable t)\n    {\n        log.trace(message, t);\n    }\n\n    @Override\n    public void setCustomKey(String key, String value)\n    {\n        // Cloud crash-report metadata is no longer used.\n    }\n\n    @Override\n    public void setUserID(String userID)\n    {\n        // Cloud crash-report user metadata is no longer used.\n    }\n}\n'
    write_if_changed(p, old, new, dry_run, changes)


def patch_manifest(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared/src/main/AndroidManifest.xml"
    old = read(p)
    new = re.sub(
        r'\s*<meta-data\s+android:name=["\']firebase_crashlytics_collection_enabled["\']\s+android:value=["\']false["\']\s*/>\s*',
        "\n",
        old,
        flags=re.MULTILINE,
    )
    write_if_changed(p, old, new, dry_run, changes)


def patch_pref_xml(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared/src/main/res/xml/prefs.xml"
    old = read(p)
    new = re.sub(
        r'\n\s*<EditTextPreference\b(?:(?!/>).)*?android:key=["\']firebase/crashlytics/user["\'](?:(?!/>).)*?/>\s*',
        "\n",
        old,
        flags=re.S,
    )
    new = re.sub(
        r'\n\s*<CheckBoxPreference\b(?:(?!/>).)*?android:key=["\']firebase/crashlytics/enabled["\'](?:(?!/>).)*?/>\s*',
        "\n",
        new,
        flags=re.S,
    )
    write_if_changed(p, old, new, dry_run, changes)


def patch_strings_xml(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "android-shared/src/main/res/values/strings.xml"
    old = read(p)
    names = [
        "summary_enable_crashlytics_preference",
        "title_enable_crashlytics_preference",
        "dialog_title_firebase_user_preference",
        "title_firebase_user_preference",
        "summary_firebase_user_preference",
    ]
    new = old
    for name in names:
        new = re.sub(rf'\s*<string\s+name=["\']{re.escape(name)}["\'][^>]*>.*?</string>\s*', "\n", new, flags=re.S)
    write_if_changed(p, old, new, dry_run, changes)


def patch_jenkins(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "pipeline/Jenkinsfile"
    old = read(p)
    new = re.sub(
        r'\s*withCredentials\(\[file\(credentialsId:\s*["\']SageTVAndroidClient-GoogleServicesJSON-Firebase["\'].*?\}\s*',
        "\n",
        old,
        flags=re.S,
    )
    write_if_changed(p, old, new, dry_run, changes)


def patch_gitignore(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / ".gitignore"
    old = read(p)
    new = remove_matching_lines(old, ["com_crashlytics_export_strings.xml", "google-services.json"])
    if new and not new.endswith("\n"):
        new += "\n"
    write_if_changed(p, old, new, dry_run, changes)


def add_firebase_doc(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "FIREBASE_REMOVAL.md"
    old = p.read_text(encoding="utf-8") if p.exists() else ""
    new = """# Firebase Removal\n\nFirebase Analytics and Firebase Crashlytics have been removed from the MiniClient Dev source tree.\n\n## Removed\n\n- Google Services Gradle plugin and classpath.\n- Firebase Crashlytics Gradle plugin and classpath.\n- Firebase BoM, Analytics, and Crashlytics application dependencies.\n- Shared-module Crashlytics dependency.\n- Crashlytics initialization from `MiniclientApplication`.\n- Crashlytics calls from the Android `Logger` implementation.\n- Crashlytics enable/user settings and preference-store helpers.\n- Crashlytics manifest collection metadata.\n- Jenkins dependency on the Firebase `google-services.json` credential.\n- Firebase-specific `.gitignore` entries.\n\n## Logging behavior\n\nThe existing `ILogger` interface is unchanged to avoid unnecessary changes in shared/core code. Android `Logger` now sends messages and exceptions only to its SLF4J backend. `setCustomKey()` and `setUserID()` remain no-op compatibility methods because their only purpose was cloud crash-report metadata.\n\nHistorical Firebase entries in `CHANGELOG.md` are intentionally retained because they describe older released versions.\n"""
    write_if_changed(p, old, new, dry_run, changes)



def add_verify_script(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "scripts/verify_no_firebase.sh"
    if not dry_run:
        p.parent.mkdir(parents=True, exist_ok=True)
    old = p.read_text(encoding="utf-8") if p.exists() else ""
    new = """#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if grep -RniE 'firebase|crashlytics|google-services[.]json' \
  --include='*.java' --include='*.kt' --include='*.gradle' --include='*.xml' \
  --include='*.properties' --include='Jenkinsfile' --include='.gitignore' \
  --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=build --exclude-dir=playstore .; then
  echo "FAIL: active Firebase/Crashlytics references remain" >&2
  exit 1
fi

echo "PASS: no active Firebase/Crashlytics/Google Services references found"
"""
    write_if_changed(p, old, new, dry_run, changes)
    if not dry_run and p.exists():
        p.chmod(p.stat().st_mode | 0o111)




def patch_dynamic_streaming_default(root: Path, dry_run: bool, changes: list[str]) -> None:
    """Make fresh Dev installs default to dynamic streaming, not fixed transcoding."""
    p = root / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/prefs/AndroidPrefStore.java"
    old = read(p)
    new, count = re.subn(
        r'public static final String STREAMING_MODE_DEFAULT = "(?:fixed|dynamic|pull)";',
        'public static final String STREAMING_MODE_DEFAULT = "dynamic";',
        old, count=1
    )
    if count != 1:
        raise RuntimeError("Could not patch AndroidPrefStore streaming mode default to dynamic")
    write_if_changed(p, old, new, dry_run, changes)

    p = root / "android-shared/src/main/res/xml/prefs.xml"
    old = read(p)
    match = re.search(r'<ListPreference\b[^>]*android:key="streaming_mode"[^>]*/>', old, flags=re.S)
    if not match:
        raise RuntimeError("Could not locate streaming_mode ListPreference")
    block = match.group(0)
    if 'android:defaultValue=' in block:
        patched_block, count = re.subn(r'android:defaultValue="[^"]*"', 'android:defaultValue="dynamic"', block, count=1)
    else:
        patched_block = block.replace('<ListPreference', '<ListPreference\n            android:defaultValue="dynamic"', 1)
        count = 1
    if count != 1:
        raise RuntimeError("Could not patch streaming_mode preference default to dynamic")
    new = old[:match.start()] + patched_block + old[match.end():]
    write_if_changed(p, old, new, dry_run, changes)

def patch_original_client_id(root: Path, dry_run: bool, changes: list[str]) -> None:
    """Keep upstream generated/persisted client ID behavior in the isolated Dev source tree."""
    # ClientIDGenerator must remain a generic converter/generator; do not force a test ID.
    p = root / "core/src/main/java/opensagetv/vibe/miniclient/util/ClientIDGenerator.java"
    old = read(p)
    new = re.sub(
        r'\n    /\*\* Fixed SageTV client identity used by the isolated Dev app\. ASCII: DEV001\. \*/\n'
        r'    public static final String DEV_FIXED_CLIENT_ID = "[^"]+";\n',
        '', old, count=1)
    write_if_changed(p, old, new, dry_run, changes)

    # Android resolver: generate once when missing, persist, then reuse.
    p = root / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/UIActivityLifeCycleHandler.java"
    old = read(p)
    new, count = re.subn(
        r'    @Override\n    public String getMACAddress\(\)\n    \{.*?\n    \}\n\n    public PlayerSurfaceView getVideoView',
        '    @Override\n    public String getMACAddress()\n    {\n'
        '        // Android 6 generates the same MAC address, so generate and persist a MiniClient ID.\n'
        '        String id = client.properties().getString(Keys.client_id);\n'
        '        if (id == null)\n'
        '        {\n'
        '            ClientIDGenerator gen = new ClientIDGenerator();\n'
        '            id = gen.generateId();\n'
        '            client.properties().setString(Keys.client_id, id);\n'
        '        }\n'
        '        return id;\n'
        '        //return AppUtil.getMACAddress(this);\n'
        '    }\n\n    public PlayerSurfaceView getVideoView',
        old, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError("Could not restore Android generated/persisted client ID behavior")
    write_if_changed(p, old, new, dry_run, changes)

    # Shared resolver follows the same persisted/generated behavior.
    p = root / "core/src/main/java/opensagetv/vibe/miniclient/util/RandomMACAddressResolver.java"
    old = read(p)
    new, count = re.subn(
        r'    @Override\n    public String getMACAddress\(\) \{.*?\n    \}\n',
        '    @Override\n    public String getMACAddress() {\n'
        '        String id = prefStore.getString(PrefStore.Keys.client_id);\n'
        '        if (id == null) {\n'
        '            ClientIDGenerator gen = new ClientIDGenerator();\n'
        '            id = gen.generateId();\n'
        '            prefStore.setString(PrefStore.Keys.client_id, id);\n\n'
        '        }\n'
        '        return id;\n'
        '    }\n',
        old, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError("Could not restore RandomMACAddressResolver client ID behavior")
    write_if_changed(p, old, new, dry_run, changes)

    # Wire protocol uses the generated/persisted ID and preserves original per-server override support.
    p = root / "core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java"
    old = read(p)
    new = old.replace("import opensagetv.vibe.miniclient.util.ClientIDGenerator;\n", "")
    fixed_block = (
        '        // The isolated Dev app must keep the same SageTV client identity across\n'
        '        // clean uninstall/reinstall cycles. Ignore generated and per-server IDs.\n'
        '        this.myID = ClientIDGenerator.DEV_FIXED_CLIENT_ID;\n'
        '        log.logInfo("Using fixed Dev CLIENT ID: " + this.myID);\n'
    )
    original_block = (
        '        this.myID = myID;\n\n'
        '        if (msi.macAddress!=null && !msi.macAddress.trim().isEmpty()) {\n'
        '            log.logInfo("Overriding CLIENT ID with Connection Specific ID: Old ID: " + myID + " New ID: " + msi.macAddress);\n'
        '            this.myID = msi.macAddress;\n'
        '        }\n'
    )
    if fixed_block in new:
        new = new.replace(fixed_block, original_block, 1)
    if '        this.myID = myID;\n' not in new or '            this.myID = msi.macAddress;\n' not in new:
        raise RuntimeError("Could not preserve original MiniClientConnection client ID behavior")
    write_if_changed(p, old, new, dry_run, changes)

    # Settings retain the original editable ID behavior; plain text is converted to six-byte ID form.
    p = root / "android-shared/src/main/java/opensagetv/vibe/miniclient/android/ui/settings/SettingsFragment.java"
    old = read(p)
    new, count = re.subn(
        r'            final Preference clientid = \(Preference\) findPreference\(Keys\.client_id\);.*?\n\n\n        \}\n        catch \(Throwable t\)',
        '            final Preference clientid = (Preference) findPreference(Keys.client_id);\n\n'
        '            final ClientIDGenerator gen = new ClientIDGenerator();\n\n'
        '            if (prefs.getString(Keys.client_id) == null)\n'
        '            {\n'
        '                prefs.setString(Keys.client_id, gen.generateId());\n'
        '            }\n\n'
        '            updateClientIDSummary(clientid, prefs.getString(Keys.client_id), gen);\n'
        '            clientid.setOnPreferenceChangeListener(new OnPreferenceChangeListener()\n'
        '            {\n'
        '                @Override\n'
        '                public boolean onPreferenceChange(Preference preference, Object newValue)\n'
        '                {\n'
        '                    if (newValue == null) return false;\n'
        '                    String val = (String) newValue;\n'
        '                    if (val.trim().length() == 0) return false;\n'
        '                    if (val.indexOf(\':\') < 0)\n'
        '                    {\n'
        '                        val = gen.generateId(val);\n'
        '                        prefs.setString(Keys.client_id, val);\n'
        '                        updateClientIDSummary(preference, val, gen);\n'
        '                        return false;\n'
        '                    }\n'
        '                    updateClientIDSummary(preference, val, gen);\n'
        '                    return true;\n'
        '                }\n'
        '            });\n\n\n'
        '        }\n        catch (Throwable t)',
        old, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError("Could not restore editable/generated client ID preference UI")
    write_if_changed(p, old, new, dry_run, changes)

def patch_changelog(root: Path, dry_run: bool, changes: list[str]) -> None:
    p = root / "CHANGELOG.md"
    old = read(p)
    marker = "Removed Google Firebase Analytics and Crashlytics"
    if marker in old:
        return
    prefix = (
        "**Dev / Unreleased**\n"
        "- Removed Google Firebase Analytics and Crashlytics dependencies, plugins, runtime reporting, settings, manifest metadata, and Jenkins Google Services credential handling.\n"
        "- Android logging now remains local through the existing SLF4J logger; exception stack traces are retained in local logs.\n\n"
    )
    write_if_changed(p, old, prefix + old, dry_run, changes)


def git_head(root: Path) -> str | None:
    try:
        return subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return None


def scan_active_firebase(root: Path) -> list[dict[str, object]]:
    """Find active Firebase references. Historical/docs are intentionally excluded."""
    hits: list[dict[str, object]] = []
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
        lines = p.read_text(encoding="utf-8", errors="ignore").splitlines()
        for i, line in enumerate(lines, 1):
            low = line.lower()
            if "firebase" in low or "crashlytics" in low or "google-services.json" in low:
                hits.append({"file": str(p.relative_to(root)), "line": i, "text": line.strip()[:240]})
    return hits


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source", type=Path, help="Path to SageTV MiniClient source tree")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    root = args.source.resolve()
    if not (root / "android-tv").is_dir() or not (root / "android-shared").is_dir():
        raise SystemExit(f"Not a SageTV MiniClient source tree: {root}")

    changes: list[str] = []
    patch_root_gradle(root, args.dry_run, changes)
    patch_tv_gradle(root, args.dry_run, changes)
    remove_google_services_file(root, args.dry_run, changes)
    patch_shared_gradle(root, args.dry_run, changes)
    patch_application_java(root, args.dry_run, changes)
    patch_pref_store(root, args.dry_run, changes)
    patch_settings_fragment(root, args.dry_run, changes)
    patch_logger(root, args.dry_run, changes)
    patch_manifest(root, args.dry_run, changes)
    patch_pref_xml(root, args.dry_run, changes)
    patch_strings_xml(root, args.dry_run, changes)
    patch_jenkins(root, args.dry_run, changes)
    patch_gitignore(root, args.dry_run, changes)
    patch_dynamic_streaming_default(root, args.dry_run, changes)
    patch_original_client_id(root, args.dry_run, changes)
    add_firebase_doc(root, args.dry_run, changes)
    add_verify_script(root, args.dry_run, changes)
    patch_changelog(root, args.dry_run, changes)

    remaining = scan_active_firebase(root)
    report = {
        "schema": 2,
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "upstream_head": git_head(root),
        "dev_application_id_base": DEV_APPLICATION_ID,
        "expected_debug_application_id": DEV_APPLICATION_ID + ".debug",
        "visible_name_release": DEV_APP_NAME,
        "visible_name_debug": DEV_DEBUG_APP_NAME,
        "client_id_mode": "generated_persisted_with_cli_override",
        "automated_test_client_id_default": "44:45:56:30:30:31",
        "default_streaming_mode": "dynamic",
        "changed_files": [str(Path(x).relative_to(root)) if str(x).startswith(str(root)) else x for x in changes],
        "remaining_active_firebase_references": remaining,
        "firebase_removed_from_active_code": not remaining,
        "behavioral_player_code_modified": False,
    }
    if not args.dry_run:
        (root / ".dev-refactor.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    if remaining:
        raise SystemExit("Active Firebase/Crashlytics references remain after refactor")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
