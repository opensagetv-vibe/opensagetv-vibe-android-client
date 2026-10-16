"""Default-off settings and session-custody source contracts; not device proof."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android'
ANDROID = '{http://schemas.android.com/apk/res/android}'

class ServerDiagnosticContracts(unittest.TestCase):
    def test_main_settings_have_persistent_opt_in_port_and_manual_action(self):
        root = ET.parse(ROOT / 'source/dev/android-shared/src/main/res/xml/prefs.xml')
        entries = {item.get(ANDROID + 'key'): item for item in root.iter()}
        self.assertEqual('false', entries['server_diagnostics/enabled'].get(ANDROID + 'defaultValue'))
        self.assertEqual('8272', entries['server_diagnostics/port'].get(ANDROID + 'defaultValue'))
        self.assertIn('server_diagnostics/upload_now', entries)

    def test_upload_owns_no_playback_or_smb_controls(self):
        text = (JAVA / 'diagnostics/ServerDiagnosticExporter.java').read_text(encoding='utf-8')
        self.assertIn('getBoolean(AndroidPrefStore.SERVER_DIAGNOSTICS_ENABLED, false)', text)
        self.assertIn('connection() == current', text)
        self.assertIn('DiagnosticBundleManager.create', text)
        self.assertIn('foreground > 0', text)
        self.assertIn('busy.compareAndSet(false, true)', text)
        for forbidden in ('player.seek(', 'player.play(', 'player.pause(', 'SMB_DIAGNOSTICS_PASSWORD', 'clear().apply()'):
            self.assertNotIn(forbidden, text)

    def test_upload_has_bounded_discovery_no_redirects_and_checksum_receipt(self):
        text = (JAVA / 'diagnostics/ServerDiagnosticTransport.java').read_text(encoding='utf-8')
        for required in ('setInstanceFollowRedirects(false)', 'setConnectTimeout(3000)', 'setReadTimeout(5000)',
                         'X-Content-SHA256', 'getResponseCode() != 201', 'requireAllowed(allowed)', 'isSiteLocalAddress'):
            self.assertIn(required, text)

if __name__ == '__main__':
    unittest.main()
