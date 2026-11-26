from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
MEDIA=ROOT/'source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java'
BRIDGE=MEDIA.parent/'PlaybackCaptionBridge.java'

class CaptionCompanionTests(unittest.TestCase):
    def test_exact_current_player_generation_and_wire_priority(self):
        source=MEDIA.read_text(encoding='utf-8')
        self.assertIn('generation == companionCaptionGeneration && playa == expected && !dvdSessionPending',source)
        self.assertIn('if (!current() || wireCaptionSource) return;',source)
        self.assertIn('synchronized (MediaCmd.this)',source)
        self.assertIn('wireCaptionSource = true;',source)
    def test_stop_and_open_cancel_only_optional_state(self):
        source=MEDIA.read_text(encoding='utf-8')
        for marker in ('case MEDIACMD_STOP:', 'case MEDIACMD_OPENURL:', 'case MEDIACMD_INIT:'):
            self.assertIn('stopCompanionCaptionBridge();',source.split(marker,1)[1].split('case MEDIACMD_',1)[0])
        self.assertIn('if (companionCaptionSource) sageTvClosedCaptionStateReceived = false;',source)
        self.assertIn('else if (!dvdSessionPending && playa != null)',source)
    def test_optional_worker_is_bounded_and_never_rebuilds_playback(self):
        source=BRIDGE.read_text(encoding='utf-8')
        for value in ('new Semaphore(2)', 'setConnectTimeout(500)', 'setReadTimeout(500)',
                      'setInstanceFollowRedirects(false)', 'out.size() >= 256',
                      'target.current()', 'worker.interrupt()', 'connection.disconnect()'):
            self.assertIn(value,source)
        for forbidden in ('SystemClock', 'newPlayerPlugin', '.seek(', '.load(', 'mcp', 'Bearer'):
            self.assertNotIn(forbidden,source)

    def test_android_icu_requires_escaped_json_braces_and_optional_linkage_guard(self):
        source=BRIDGE.read_text(encoding='utf-8')
        self.assertIn(r'\\}\\s*',source)
        self.assertNotIn(r'\\s*}\\s*',source)
        self.assertIn('catch (RuntimeException | LinkageError unavailable)',MEDIA.read_text(encoding='utf-8'))

    def test_mapping_edit_preserves_companion_authority(self):
        source=(ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerAdjustmentsDialog.java').read_text()
        self.assertIn('media.refreshCaptionSelection();',source)
        self.assertNotIn('media.setSageTvClosedCaptionState(media.getSageTvClosedCaptionState())',source)
        refresh=MEDIA.read_text().split('public synchronized void refreshCaptionSelection()',1)[1].split('}',1)[0]
        self.assertNotIn('wireCaptionSource',refresh)

    def test_stale_setup_cannot_crash_android_main_thread(self):
        source=(ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java').read_text()
        self.assertEqual(2,source.count('setupPlayer(finalUrl);\n'))
        self.assertGreaterEqual(source.count('if (!playbackSessions.isCurrentSession(loadSession)) return;'),2)
        self.assertNotIn('throw new RuntimeException("setupPlayer must create a datasource")',source)
        self.assertEqual(2,source.count('Current load has no datasource; failing this player, not Android'))

    def test_stv_broadcast_slot_survives_independent_subtitles_off(self):
        source=(ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/BaseMediaPlayerImpl.java').read_text()
        method=source.split('protected boolean applyPendingServerSubpictureStream()',1)[1].split('int command = pendingServerSubpictureCommand',1)[0]
        self.assertIn('boolean stvBroadcastCaption = "stv".equals(captionMode)',method)
        self.assertIn('hasSageTvClosedCaptionState()',method)
        self.assertIn('!lastUri.startsWith("push:dvd")',method)
        self.assertIn('|| stvBroadcastCaption',method)

    def test_gsy_forwards_virtual_caption_slot_to_actual_engine(self):
        source=(ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/gsy/GSYMediaPlayerImpl.java').read_text()
        self.assertIn('return d() != null && d().applyClosedCaptionSlot(channel, type, language);',source)

if __name__=='__main__': unittest.main()
