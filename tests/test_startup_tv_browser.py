from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "source/dev/android-tv/src/main/java/opensagetv/vibe/miniclient/android/phone/ServersActivity.java"
RES = ROOT / "source/dev/android-shared/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def method(source, name):
    start = re.search(r"(?:private|protected)\s+\w+\s+" + name + r"\([^)]*\)\s*\{", source).start()
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


class StartupTvBrowserTests(unittest.TestCase):
    def test_create_and_settings_return_route_before_browser_work(self):
        source = ACTIVITY.read_text(encoding="utf-8")
        create = method(source, "onCreate")
        resume = method(source, "onResume")
        for body in (create, resume):
            self.assertRegex(body, r"if \(redirectToTvBrowserIfSelected\(\)\)\s*\{\s*return;")
        self.assertLess(create.index("redirectToTvBrowserIfSelected"), create.index("new ServersAdapter"))
        self.assertLess(resume.index("isFinishing()"), resume.index("hasPendingPreservedSession"))
        self.assertLess(resume.index("hasPendingPreservedSession"), resume.index("redirectToTvBrowserIfSelected"))
        self.assertLess(resume.index("redirectToTvBrowserIfSelected"), resume.index("refreshServers"))

    def test_actual_redirect_handles_all_device_choices_and_finishing_activity(self):
        javac, java = shutil.which("javac"), shutil.which("java")
        if not javac or not java:
            self.skipTest("JDK required for the production-method lifecycle harness")
        redirect = method(ACTIVITY.read_text(encoding="utf-8"), "redirectToTvBrowserIfSelected")
        # Execute the production method with small Android API stand-ins, rather
        # than duplicating its selection logic in a separate policy function.
        harness = '''
public class BrowserHarness {
    static BrowserHarness current;
    boolean tv, leanback, selected, finishing;
    int launches, flags;
    static class R { static class bool { static final int istv = 1; } }
    static class Keys { static final String use_tv_ui_on_tablet = "use_tv_ui_on_tablet"; }
    static class MainActivity {}
    static class PackageManager { static final String FEATURE_LEANBACK = "leanback"; }
    static class MiniclientApplication { static BrowserHarness get() { return current; } }
    static class Intent {
        static final int FLAG_ACTIVITY_NEW_TASK = 1, FLAG_ACTIVITY_CLEAR_TASK = 2,
                FLAG_ACTIVITY_CLEAR_TOP = 4;
        int flags;
        Intent(Object owner, Class<?> target) { check(target == MainActivity.class); }
        void setFlags(int value) { flags = value; }
    }
    BrowserHarness getResources() { return this; }
    BrowserHarness getPackageManager() { return this; }
    BrowserHarness getClient() { return this; }
    BrowserHarness properties() { return this; }
    boolean getBoolean(int key) { check(key == R.bool.istv); return tv; }
    boolean getBoolean(String key, boolean fallback) {
        check(key.equals(Keys.use_tv_ui_on_tablet) && !fallback); return selected;
    }
    boolean hasSystemFeature(String key) { check(key.equals(PackageManager.FEATURE_LEANBACK)); return leanback; }
    boolean isFinishing() { return finishing; }
    void startActivity(Intent intent) { launches++; flags = intent.flags; }
    void finish() { finishing = true; }
    static void check(boolean value) { if (!value) throw new AssertionError(); }
''' + redirect + '''
    public static void main(String[] args) {
        for (int mask = 0; mask < 8; mask++) {
            current = new BrowserHarness();
            current.tv = (mask & 1) != 0;
            current.leanback = (mask & 2) != 0;
            current.selected = (mask & 4) != 0;
            boolean expected = mask == 3 || mask >= 4;
            check(current.redirectToTvBrowserIfSelected() == expected);
            check(current.launches == (expected ? 1 : 0));
            check(current.finishing == expected);
            if (expected) {
                check(current.flags == 7);
                check(current.redirectToTvBrowserIfSelected());
                check(current.launches == 1);
            }
        }
        current = new BrowserHarness();
        check(!current.redirectToTvBrowserIfSelected());
        current.selected = true; // Settings changes choice while launcher is paused.
        check(current.redirectToTvBrowserIfSelected());
        check(current.launches == 1);
        current = new BrowserHarness();
        current.finishing = true;
        check(current.redirectToTvBrowserIfSelected());
        check(current.launches == 0);
    }
}
'''
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "BrowserHarness.java"
            path.write_text(harness, encoding="utf-8")
            subprocess.run([javac, str(path)], check=True, capture_output=True, timeout=30)
            subprocess.run([java, "-cp", temp, "BrowserHarness"], check=True, capture_output=True, timeout=15)

    def test_existing_preference_key_default_and_clear_label(self):
        preferences = ET.parse(RES / "xml/prefs.xml").getroot()
        preference = next(node for node in preferences.iter()
                          if node.get(ANDROID + "key") == "use_tv_ui_on_tablet")
        self.assertEqual(preference.get(ANDROID + "defaultValue"), "false")
        strings = {node.get("name"): node.text for node in ET.parse(RES / "values/strings.xml").getroot()}
        self.assertEqual(strings["use_tv_ui_on_tablet_title"], "Use TV server browser")
        summary = strings["use_tv_ui_on_tablet_summary"]
        self.assertIn("remote control", summary)
        self.assertIn("Android boxes", summary)
        self.assertIn("tablets", summary)


if __name__ == "__main__":
    unittest.main()
