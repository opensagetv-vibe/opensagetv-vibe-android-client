package opensagetv.vibe.miniclient;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import static org.junit.Assert.*;

public class MediaTransportDiagnosticsTest
{
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final MediaTransportDiagnostics trace = new MediaTransportDiagnostics(now::get);
    private void advance(long ms) { now.addAndGet(ms * 1_000_000L); }
    private void field(String name, long value)
    { assertTrue(trace.snapshotJson(), trace.snapshotJson().contains("\"" + name + "\":" + value)); }

    @Test public void disabledDoesNotCollectCommandActivity()
    {
        trace.waitingForHeader(); trace.headerReceived(23, 100); trace.payloadProgress(40);
        trace.payloadCompleted(20); trace.dispatchCompleted(); trace.replyStarted(9, 123);
        trace.replyCompleted(); trace.failed();
        field("headerCount", 0); field("payloadBytes", 0); field("replyCount", 0); field("failureCount", 0);
        assertTrue(trace.snapshotJson().contains("\"phase\":\"disabled\""));
        assertEquals("unavailable", trace.workerStack());
    }

    @Test public void partialPayloadIsDifferentFromWaitingForANewCommand()
    {
        trace.setEnabled(true); trace.socketOpened(); trace.waitingForHeader(); advance(13000);
        assertTrue(trace.snapshotJson().contains("\"phase\":\"waiting_header\"")); field("phaseAgeMs", 13000);
        trace.headerReceived(23, 32780); trace.payloadProgress(12000); advance(9000);
        assertTrue(trace.snapshotJson().contains("\"phase\":\"reading_payload\""));
        field("expectedBytes", 32780); field("receivedBytes", 12000); field("phaseAgeMs", 9000);
    }

    @Test public void zeroBytePollsStaySeparateFromRealMediaArrival()
    {
        trace.setEnabled(true); trace.headerReceived(23, 18); trace.payloadProgress(18);
        trace.payloadCompleted(0); advance(2000);
        field("emptyPushPollCount", 1); field("dataPushCount", 0); field("lastDataPushAgeMs", -1);
        field("lastEmptyPushAgeMs", 2000);
        trace.headerReceived(23, 118); trace.payloadProgress(118); trace.payloadCompleted(100);
        field("dataPushCount", 1); field("lastDataPushAgeMs", 0); field("payloadBytes", 136);
    }

    @Test public void processingDelayIsNotAReplyWriteDelay()
    {
        trace.setEnabled(true); trace.headerReceived(23, 100); advance(3);
        trace.payloadProgress(100); trace.payloadCompleted(82); advance(13000);
        assertTrue(trace.snapshotJson().contains("\"phase\":\"dispatching\""));
        trace.dispatchCompleted(); trace.replyStarted(9, 4194304); advance(2); trace.replyCompleted();
        field("lastPayloadReadMs", 3); field("lastDispatchMs", 13000);
        field("maximumDispatchMs", 13000); field("lastReplyWriteMs", 2);
        field("lastPushReplyFreeBytes", 4194304); field("lastReplyCommand", 23); field("lastReplyBytes", 9);
    }

    @Test public void aBlockedReplyIsVisibleBeforeItCompletes()
    {
        trace.setEnabled(true); trace.headerReceived(23, 18); trace.payloadCompleted(0);
        trace.dispatchCompleted(); trace.replyStarted(9, 4194304); advance(13139);
        assertTrue(trace.snapshotJson().contains("\"phase\":\"writing_reply\""));
        field("phaseAgeMs", 13139); field("replyCount", 0); field("lastReplyAgeMs", -1);
        field("lastPushReplyFreeBytes", -1); field("pendingPushFreeBytes", 4194304);
        trace.replyCompleted(); field("maximumReplyWriteMs", 13139); field("replyCount", 1);
        field("lastPushReplyFreeBytes", 4194304); field("pendingPushFreeBytes", -1);
    }

    @Test public void noReplyCommandAndReconnectDoNotInventAReply()
    {
        trace.setEnabled(true); trace.socketOpened(); trace.headerReceived(5, 0);
        trace.payloadCompleted(-1); trace.dispatchCompleted(); trace.waitingForHeader();
        field("replyCount", 0); trace.failed(); trace.socketClosed(); field("failureCount", 1);
        trace.socketOpened(); trace.waitingForHeader(); field("socketGeneration", 2);
        assertTrue(trace.snapshotJson().contains("\"consistent\":true"));
    }

    @Test public void reenableDoesNotExposeStaleArrivalAges()
    {
        trace.setEnabled(true); trace.headerReceived(23, 8); trace.payloadCompleted(0);
        trace.setEnabled(false); advance(30000); trace.setEnabled(true);
        field("lastHeaderAgeMs", -1); field("lastEmptyPushAgeMs", -1);
    }

    @Test public void protocolWordDecodingPreservesSigns()
    {
        assertEquals(4194304, MediaTransportDiagnostics.firstWord(new byte[] {0,64,0,0}));
        assertEquals(-2, MediaTransportDiagnostics.firstWord(new byte[] {-1,-1,-1,-2}));
    }

    @Test public void fragmentedInputRetainsExactBytesAndProgress() throws IOException
    {
        trace.setEnabled(true); trace.headerReceived(23, 6);
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[] {1,2,3,4,5,6}) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(2, length));
            }
        };
        byte[] actual = new byte[6];
        trace.readPayload(new DataInputStream(input), actual, 6, true);
        assertArrayEquals(new byte[] {1,2,3,4,5,6}, actual);
        field("receivedBytes", 6); field("payloadBytes", 6);
    }

    @Test public void eofIsNotRetriedOrConvertedToAPartialCommand() throws IOException
    {
        trace.setEnabled(true); trace.headerReceived(23, 6);
        try {
            trace.readPayload(new DataInputStream(new ByteArrayInputStream(new byte[] {1,2})), new byte[6], 6, true);
            fail("Expected EOF, never dispatch partial command");
        } catch (EOFException expected) { field("receivedBytes", 2); }
        trace.setEnabled(false);
        byte[] actual = new byte[2];
        trace.readPayload(new DataInputStream(new ByteArrayInputStream(new byte[] {3,4})), actual, 2, false);
        assertArrayEquals(new byte[] {3,4}, actual);
        field("receivedBytes", 0);
    }
}
