package opensagetv.vibe.miniclient;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import opensagetv.vibe.miniclient.prefs.PrefStore;

public class RetainedPushRestartPolicyTest {
    private MediaCmd command(int state, String mode, boolean push, boolean dvd) throws Exception {
        MiniClient client = mock(MiniClient.class);
        PrefStore prefs = mock(PrefStore.class);
        when(client.properties()).thenReturn(prefs);
        when(prefs.getStreamingMode()).thenReturn(mode);
        MiniPlayerPlugin player = mock(MiniPlayerPlugin.class);
        when(player.getState()).thenReturn(state);
        MediaCmd command = new MediaCmd(client);
        for (String name : new String[]{"playa", "pushMode", "dvdSessionPending"}) {
            java.lang.reflect.Field field = MediaCmd.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(command, name.equals("playa") ? player : name.equals("pushMode") ? push : dvd);
        }
        return command;
    }

    private void flush(MediaCmd command) {
        byte[] response = new byte[16];
        assertEquals(4, command.ExecuteMediaCommand(MediaCmd.MEDIACMD_FLUSH, 0, new byte[0], response));
        assertEquals(1, MediaCmd.readInt(0, response));
    }

    @Test public void sharedMediaCommandArmsStoppedAndEndedBackends() throws Exception {
        for (int state : new int[]{MiniPlayerPlugin.STOPPED_STATE, MiniPlayerPlugin.EOS_STATE}) {
            MediaCmd c = command(state, "dynamic", true, false);
            flush(c);
            assertTrue(c.takeRetainedPushRestartReconnect());
            assertFalse(c.takeRetainedPushRestartReconnect());
        }
    }

    @Test public void sharedBoundaryDoesNotArmNormalSeeksOrOtherTransports() throws Exception {
        for (int state : new int[]{MiniPlayerPlugin.PLAY_STATE, MiniPlayerPlugin.PAUSE_STATE}) {
            MediaCmd c = command(state,"dynamic",true,false);
            flush(c);
            assertFalse(c.takeRetainedPushRestartReconnect());
        }
        for (String mode : new String[]{"fixed","pull"}) {
            MediaCmd c = command(MiniPlayerPlugin.STOPPED_STATE,mode,true,false);
            flush(c);
            assertFalse(c.takeRetainedPushRestartReconnect());
        }
        MediaCmd dvd = command(MiniPlayerPlugin.STOPPED_STATE,"dynamic",true,true);
        flush(dvd);
        assertFalse(dvd.takeRetainedPushRestartReconnect());
        MediaCmd pull = command(MiniPlayerPlugin.STOPPED_STATE,"dynamic",false,false);
        flush(pull);
        assertFalse(pull.takeRetainedPushRestartReconnect());
    }

    @Test public void explicitWireStopSurvivesAnEngineStateThatMasksStopped() throws Exception {
        MediaCmd c = command(MiniPlayerPlugin.PLAY_STATE,"dynamic",true,false);
        c.ExecuteMediaCommand(MediaCmd.MEDIACMD_STOP,0,new byte[0],new byte[16]);
        flush(c);
        assertTrue(c.takeRetainedPushRestartReconnect());
    }

    @Test public void stoppedPushFlushQualifiesOnceWithoutASeekTarget() {
        RetainedPushRestartPolicy p = new RetainedPushRestartPolicy();
        p.flush(true, true, false, true, 1000L);
        assertTrue(p.take(1010L));
        assertFalse(p.take(1011L));
        p.flush(true, true, false, true, 1100L);
        assertFalse(p.take(1101L));
    }

    @Test public void newOpenUrlPermitsAnotherIndependentRetainedRestart() {
        RetainedPushRestartPolicy p = new RetainedPushRestartPolicy();
        p.flush(true, true, false, true, 1000L);
        assertTrue(p.take(1000L));
        p.reset();
        assertFalse(p.take(1001L));
        p.flush(true, true, false, true, 2000L);
        assertTrue(p.take(2001L));
    }

    @Test public void normalSeekDvdPullAndFixedDoNotReconnect() {
        boolean[][] excluded = {{true,true,false,false}, {true,true,true,true},
                {true,false,false,true}, {false,true,false,true}};
        for (boolean[] c : excluded) {
            RetainedPushRestartPolicy p = new RetainedPushRestartPolicy();
            p.flush(c[0], c[1], c[2], c[3], 1000L);
            assertFalse(p.take(1001L));
        }
    }

    @Test public void delayedOrBackwardsClockNeverReplaysARecovery() {
        RetainedPushRestartPolicy p = new RetainedPushRestartPolicy();
        p.flush(true,true,false,true,1000L);
        assertFalse(p.take(1251L));
        assertFalse(p.take(1252L));
        p.flush(true,true,false,true,2000L);
        assertFalse(p.take(1999L));
    }

    @Test public void anotherIneligibleFlushClearsPendingIntent() {
        RetainedPushRestartPolicy p = new RetainedPushRestartPolicy();
        p.flush(true,true,false,true,1000L);
        p.flush(true,true,false,false,1001L);
        assertFalse(p.take(1002L));
    }
}
