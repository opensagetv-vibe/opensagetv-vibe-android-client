from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT/'source/dev/core/src/main/java/opensagetv/vibe/miniclient'
ANDROID = ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video'


class PushSeekDispatchContractTest(unittest.TestCase):
    def test_wire_seek_is_guarded_without_adding_reply_bytes(self):
        block = (CORE/'MediaCmd.java').read_text().split('case MEDIACMD_SEEK:', 1)[1].split('case MEDIACMD_SETRATE:', 1)[0]
        self.assertLess(block.index('playa.canHandleServerSeek()'), block.index('playa.seek(seekTime)'))
        self.assertIn('server_seek_unsupported', block)
        self.assertIn('return 0;', block)
        self.assertNotIn('writeInt(', block)

    def test_android_guard_is_actual_transport_and_gsy_delegates(self):
        source = (ANDROID/'BaseMediaPlayerImpl.java').read_text()
        block = source.split('boolean canHandleServerSeek()', 1)[1].split('@Override', 1)[0]
        self.assertIn('canHandleServerSeek(pushMode,', block)
        self.assertIn('lastUri.startsWith("push:dvd")', block)
        self.assertNotIn('getStreamingMode', block)
        self.assertIn('d().canHandleServerSeek()', (ANDROID/'gsy/GSYMediaPlayerImpl.java').read_text())

    def test_physical_probe_injects_wire_command_and_restores_state(self):
        gate = (ROOT/'scripts/mcp_buffer_seek_qualification_test.py').read_text()
        for token in ('wire_command29=True', "'replyLength'", "'serverSeekSequence'",
                      "'serverFlushSequence'", 'dev_restore_settings', 'powerRestore', 'local.close()'):
            self.assertIn(token, gate)
        debug = (ROOT/'source/dev/android-tv/src/debug/java/opensagetv/vibe/miniclient/android/tv/debug/DebugPlayerCommands.java').read_text()
        self.assertIn('ExecuteMediaCommand(MediaCmd.MEDIACMD_SEEK', debug)
        self.assertIn('Boolean.parseBoolean(clean(intent.getStringExtra("wire_command29")))', debug)
        self.assertNotIn('getBooleanExtra("wire_command29"', debug)
        self.assertIn('delta <= elapsed+1500', gate)
        self.assertIn('elapsed < 45000', gate)
        self.assertIn("int(before['mediaTimeMs'])+60000", gate)


if __name__ == '__main__': unittest.main()
