"""Android-owned web viewer contracts; physical proof is tracked separately."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android'
A = '{http://schemas.android.com/apk/res/android}'

class ClientDiagnosticWebContracts(unittest.TestCase):
    def test_default_off_persistent_main_settings_and_readonly_address(self):
        entries = {v.get(A + 'key'): v for v in ET.parse(ROOT / 'source/dev/android-shared/src/main/res/xml/prefs.xml').iter()}
        self.assertEqual('false', entries['client_diagnostics_web/enabled'].get(A + 'defaultValue'))
        self.assertEqual('8272', entries['client_diagnostics_web/port'].get(A + 'defaultValue'))
        self.assertEqual('false', entries['client_diagnostics_web/status'].get(A + 'selectable'))
        self.assertIn('server_diagnostics/enabled', entries)

    def test_foreground_owner_has_no_playback_or_power_mutations(self):
        text = (JAVA / 'diagnostics/ClientDiagnosticWebService.java').read_text(encoding='utf-8')
        for required in ('foreground <= 0', 'onActivityStopped', 'server.close()', 'DiagnosticRedactor.redact',
                         'DiagnosticBundleManager.create(app', 'CLIENT_WEB_ENABLED, false'):
            self.assertIn(required, text)
        for forbidden in ('player.seek(', 'player.play(', 'player.pause(', 'WakeLock', 'startForeground(', 'SMB_PASSWORD'):
            self.assertNotIn(forbidden, text)
        for required in ('visibleActivity.clear()', 'listening.close()', '!server.isClosed()', 'visibleActivity.get() == null'):
            self.assertIn(required, text)
        handler = (JAVA / 'UIActivityLifeCycleHandler.java').read_text(encoding='utf-8')
        pause = handler.split('public void onPause(Activity activity)', 1)[1].split('public ', 1)[0]
        self.assertLess(pause.index('ClientDiagnosticWebService.pauseActivity(activity)'),
                        pause.index('dismissPlaybackDialogs()'))

    def test_bounded_android_transport_never_exposes_arbitrary_files_or_jdk_server(self):
        text = (JAVA / 'diagnostics/DiagnosticWebServer.java').read_text(encoding='utf-8')
        for required in ('MAX_HEADER = 8192', 'ArrayBlockingQueue<Runnable>(4)', 'setSoTimeout(2000)',
                         '10, TimeUnit.SECONDS', 'Use the device IP address', 'Read-only: GET only',
                         'Cache-Control: no-store', 'escape(new String'):
            self.assertIn(required, text)
        for forbidden in ('com.sun.net.httpserver', 'FileInputStream(', 'getQuery(', 'Runtime.getRuntime().exec'):
            self.assertNotIn(forbidden, text)

    def test_main_thread_shared_cache_and_explicit_settings_allowlist(self):
        text = (JAVA / 'diagnostics/ClientDiagnosticWebService.java').read_text(encoding='utf-8')
        for required in ('main.post(capture)', 'capture.get(750,TimeUnit.MILLISECONDS)',
                         'now-dataTime<2000L', 'caption_cc1_type', 'uploadStatus', 'history.download(id)'):
            self.assertIn(required, text)
        self.assertNotIn('prefs.getAll(', text)
        self.assertIn('out.remove("_timelineOwner")', text)
        for forbidden in ('SMB_PASSWORD', 'SMB_USERNAME', 'SERVER_DIAGNOSTICS_TOKEN', 'System.getenv('):
            self.assertNotIn(forbidden, text)

    def test_html5_offline_polling_and_text_only_rendering(self):
        assets = ROOT / 'source/dev/android-shared/src/main/assets/diagnostics'
        script = (assets / 'diagnostics.js').read_text(encoding='utf-8')
        for required in ('doc.hidden', 'visibilitychange', 'if(busy || doc.hidden)', 'textContent',
                         'filterLines', 'issueReport', 'manually', 'new AbortController()', '8000'):
            self.assertIn(required, script)
        for forbidden in ('innerHTML', 'eval(', 'new Function(', 'http://cdn', 'https://cdn'):
            self.assertNotIn(forbidden, script)
        html = (assets / 'index.html').read_text(encoding='utf-8')
        self.assertIn('No remote playback', html)
        self.assertIn('rel="noopener noreferrer"', html)

    def test_caption_declared_is_not_observed_and_sync_is_not_measured(self):
        text = (JAVA / 'ClientDiagnosticSnapshot.java').read_text(encoding='utf-8')
        for required in ('hasObservedCeaCaptionData()', 'Extractor-declared; no CEA packets observed',
                         'File/DVD subtitle', 'measuredLipSyncMs",null', 'getMappedSubtitleTrackForClosedCaptionChannel',
                         'DiagnosticCaptionMetadata.transportPid(codec == SubtitleCodec.DVB'):
            self.assertIn(required, text)
        self.assertIn('stats.backendPlaybackState == 2', text)
        self.assertNotIn('p.put("buffering",stats.loading)', text)

if __name__ == '__main__': unittest.main()
