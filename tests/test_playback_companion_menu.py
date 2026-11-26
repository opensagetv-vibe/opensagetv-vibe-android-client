from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
CORE=ROOT/'source/dev/core/src/main/java/opensagetv/vibe/miniclient'
UI=ROOT/'source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/ActivePlayerAdjustmentsDialog.java'

class CompanionMenuTests(unittest.TestCase):
    def test_no_player_clock_mutation_or_background_polling(self):
        text=(CORE/'PlaybackCompanionClient.java').read_text()
        for marker in ('Proxy.NO_PROXY','setInstanceFollowRedirects(false)','body.size()>=2048',
                       'new ArrayBlockingQueue<Runnable>(4)','current.getAsBoolean()','not renderer clock'):
            self.assertIn(marker,text)
        for marker in ('.seek(','.load(','.setRate(', 'scheduleAtFixedRate', 'Bearer', 'core_mcp'):
            self.assertNotIn(marker,text)

    def test_exact_load_guard_survives_ui_thread_handoff(self):
        text=(CORE/'MediaCmd.java').read_text()
        block=text.split('public void requestCompanionMenu',1)[1].split('private void startCompanionCaptionBridge',1)[0]
        for marker in ('playa==expected','generation==companionCaptionGeneration','(!dvd||dvdSessionPending)'):
            self.assertIn(marker,block)
        ui=UI.read_text()
        self.assertGreaterEqual(ui.count('expected.getCompanionMenuLoadId()!=loadId'),3)
        self.assertIn('expected.getCompanionMenuLoadId()==loadId',ui)
        self.assertIn('row.setEnabled(reply.available)',ui)
        self.assertIn('"beginning","save_resume","resume","clear_resume"',ui)
        self.assertIn('holder[0].isShowing()',ui)

if __name__=='__main__':unittest.main()
