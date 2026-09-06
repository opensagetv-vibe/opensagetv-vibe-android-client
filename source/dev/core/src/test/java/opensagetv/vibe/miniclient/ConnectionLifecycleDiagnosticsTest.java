package opensagetv.vibe.miniclient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionLifecycleDiagnosticsTest
{
    @Test
    public void recordsBoundedOrderingAndCountersWithoutPayloads()
    {
        ConnectionLifecycleDiagnostics.Trace trace =
                ConnectionLifecycleDiagnostics.begin("192.0.2.10");
        trace.lifecycle("connect_started");
        trace.alive(true);
        trace.workerStarted("media");
        trace.mediaCommand(16);
        trace.mediaReply(16);
        trace.gfxRead(1, 1);
        trace.gfxDispatch(1);
        trace.gfxReply(1);
        trace.eventQueued(2);
        trace.eventDequeued(1);
        trace.workerStopped("media");
        trace.alive(false);
        trace.closeRequested();
        trace.closeRequested();
        trace.closeComplete();
        trace.closeComplete();

        String wire = ConnectionLifecycleDiagnostics.latestCompactWire();
        assertTrue(wire.contains("connectionTraceAvailable=true"));
        assertTrue(wire.contains("connectionServer=192.0.2.10"));
        assertTrue(wire.contains("connectionMediaCommandCount=1"));
        assertTrue(wire.contains("connectionMediaReplyCount=1"));
        assertTrue(wire.contains("connectionGfxReadCount=1"));
        assertTrue(wire.contains("connectionGfxDispatchCount=1"));
        assertTrue(wire.contains("connectionGfxReplyCount=1"));
        assertTrue(wire.contains("connectionEventQueueMaxDepth=2"));
        assertEquals(1, occurrences(wire, "@close_requested@"));
        assertEquals(1, occurrences(wire, "@close_complete@"));
        assertFalse(wire.contains("password"));
    }

    @Test
    public void recentLifecycleRingIsBounded()
    {
        ConnectionLifecycleDiagnostics.Trace trace =
                ConnectionLifecycleDiagnostics.begin("test");
        for (int i = 0; i < 100; i++)
            trace.lifecycle("event_" + i);

        String wire = ConnectionLifecycleDiagnostics.latestCompactWire();
        String recent = wire.substring(wire.indexOf("connectionRecent=")
                + "connectionRecent=".length());
        assertTrue(recent.split("\\|").length <= 64);
        assertFalse(recent.contains("@event_0@"));
        assertTrue(recent.contains("@event_99@"));
    }

    private static int occurrences(String value, String needle)
    {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0)
        {
            count++;
            offset += needle.length();
        }
        return count;
    }
}
