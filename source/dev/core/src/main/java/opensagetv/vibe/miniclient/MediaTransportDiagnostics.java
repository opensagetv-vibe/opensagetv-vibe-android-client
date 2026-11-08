package opensagetv.vibe.miniclient;

import java.util.function.LongSupplier;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

/**
 * Numeric, connection-local observations of the existing media socket loop.
 * No payload is retained and no timer, socket operation, retry or player command
 * is introduced. A reply completion means local write/flush returned, NOT that
 * the remote server acknowledged it. Readers never acquire the media lock.
 */
public final class MediaTransportDiagnostics
{
    private final LongSupplier clock;
    private volatile boolean enabled;
    private volatile long revision;
    private volatile String phase = "disabled";
    private volatile long phaseStarted;
    private volatile Thread worker;
    private volatile long socketGeneration;
    private volatile int command = -1, expectedBytes, receivedBytes;
    private volatile long headers, payloadBytes, dataPushes, emptyPushPolls;
    private volatile long lastHeader, lastPayload, lastDataPush, lastEmptyPush, lastReply;
    private volatile long payloadStarted, dispatchStarted, replyStarted;
    private volatile long lastPayloadReadMs, lastDispatchMs, maximumDispatchMs;
    private volatile long lastReplyWriteMs, maximumReplyWriteMs, replies, failures;
    private volatile int lastReplyCommand = -1, lastReplyBytes, lastPushReplyFree = -1;
    private volatile int pendingReplyBytes, pendingPushFree = -1;

    public MediaTransportDiagnostics() { this(System::nanoTime); }
    MediaTransportDiagnostics(LongSupplier clock) { this.clock = clock; }

    public void setEnabled(boolean value)
    {
        if (enabled == value) return;
        revision++;
        enabled = value;
        phase = value ? "idle" : "disabled";
        phaseStarted = clock.getAsLong();
        // Never present old enabled-session progress as fresh after re-enabling.
        lastHeader = lastPayload = lastDataPush = lastEmptyPush = lastReply = 0;
        expectedBytes = receivedBytes = 0;
        pendingReplyBytes = 0;
        pendingPushFree = -1;
        revision++;
    }

    public void socketOpened()
    {
        revision++;
        socketGeneration++;
        worker = Thread.currentThread();
        revision++;
    }

    public void waitingForHeader()
    {
        if (!enabled) return;
        revision++;
        command = -1;
        expectedBytes = receivedBytes = 0;
        phase("waiting_header");
        revision++;
    }

    public void headerReceived(int value, int length)
    {
        if (!enabled) return;
        revision++;
        command = value;
        headers++;
        lastHeader = clock.getAsLong();
        expectedBytes = length;
        receivedBytes = 0;
        phase("reading_payload");
        payloadStarted = phaseStarted;
        revision++;
    }

    public void payloadProgress(int count)
    {
        if (!enabled || count <= 0) return;
        revision++;
        receivedBytes += count;
        payloadBytes += count;
        revision++;
    }

    /** Existing ordered readFully semantics, with optional partial-read counters. */
    public void readPayload(DataInputStream input, byte[] buffer, int length, boolean observe)
            throws IOException
    {
        if (!observe) { input.readFully(buffer, 0, length); return; }
        int offset = 0;
        while (offset < length)
        {
            int count = input.read(buffer, offset, length - offset);
            if (count < 0) throw new EOFException();
            offset += count;
            payloadProgress(count);
        }
    }

    public void payloadCompleted(int pushBytes)
    {
        if (!enabled) return;
        revision++;
        long now = clock.getAsLong();
        lastPayload = now;
        lastPayloadReadMs = duration(now, payloadStarted);
        if (command == MediaCmd.MEDIACMD_PUSHBUFFER)
        {
            if (pushBytes > 0) { dataPushes++; lastDataPush = now; }
            else if (pushBytes == 0) { emptyPushPolls++; lastEmptyPush = now; }
        }
        phase("dispatching");
        dispatchStarted = phaseStarted;
        revision++;
    }

    public void dispatchCompleted()
    {
        if (!enabled) return;
        revision++;
        lastDispatchMs = duration(clock.getAsLong(), dispatchStarted);
        maximumDispatchMs = Math.max(maximumDispatchMs, lastDispatchMs);
        phase("dispatch_complete");
        revision++;
    }

    public void replyStarted(int bytes, int pushFree)
    {
        if (!enabled) return;
        revision++;
        pendingReplyBytes = bytes;
        pendingPushFree = command == MediaCmd.MEDIACMD_PUSHBUFFER ? pushFree : -1;
        phase("writing_reply");
        replyStarted = phaseStarted;
        revision++;
    }

    public void replyCompleted()
    {
        if (!enabled) return;
        revision++;
        long now = clock.getAsLong();
        lastReplyWriteMs = duration(now, replyStarted);
        maximumReplyWriteMs = Math.max(maximumReplyWriteMs, lastReplyWriteMs);
        lastReply = now;
        lastReplyCommand = command;
        lastReplyBytes = pendingReplyBytes;
        if (command == MediaCmd.MEDIACMD_PUSHBUFFER) lastPushReplyFree = pendingPushFree;
        pendingReplyBytes = 0;
        pendingPushFree = -1;
        replies++;
        phase("reply_complete");
        revision++;
    }

    public void failed()
    {
        if (!enabled) return;
        revision++;
        failures++;
        phase("failed");
        revision++;
    }

    public void socketClosed()
    {
        if (enabled) { revision++; phase("closed"); revision++; }
    }

    private void phase(String value) { phaseStarted = clock.getAsLong(); phase = value; }
    private static long duration(long now, long then) { return then == 0 ? -1 : Math.max(0, now - then) / 1_000_000L; }

    /** Sampling may race a stage transition; explicitly mark it, never block I/O. */
    public String snapshotJson()
    {
        long before = revision, now = clock.getAsLong();
        StringBuilder out = new StringBuilder(900);
        out.append("{\"enabled\":").append(enabled).append(",\"phase\":\"").append(phase).append('"');
        field(out, "socketGeneration", socketGeneration);
        field(out, "command", command);
        field(out, "phaseAgeMs", duration(now, phaseStarted));
        field(out, "expectedBytes", expectedBytes); field(out, "receivedBytes", receivedBytes);
        field(out, "headerCount", headers); field(out, "payloadBytes", payloadBytes);
        field(out, "dataPushCount", dataPushes); field(out, "emptyPushPollCount", emptyPushPolls);
        field(out, "lastHeaderAgeMs", duration(now, lastHeader));
        field(out, "lastPayloadAgeMs", duration(now, lastPayload));
        field(out, "lastDataPushAgeMs", duration(now, lastDataPush));
        field(out, "lastEmptyPushAgeMs", duration(now, lastEmptyPush));
        field(out, "lastReplyAgeMs", duration(now, lastReply));
        field(out, "lastPayloadReadMs", lastPayloadReadMs);
        field(out, "lastDispatchMs", lastDispatchMs); field(out, "maximumDispatchMs", maximumDispatchMs);
        field(out, "lastReplyWriteMs", lastReplyWriteMs); field(out, "maximumReplyWriteMs", maximumReplyWriteMs);
        field(out, "replyCount", replies); field(out, "lastReplyCommand", lastReplyCommand);
        field(out, "lastReplyBytes", lastReplyBytes); field(out, "lastPushReplyFreeBytes", lastPushReplyFree);
        field(out, "pendingReplyBytes", pendingReplyBytes); field(out, "pendingPushFreeBytes", pendingPushFree);
        field(out, "failureCount", failures);
        out.append(",\"consistent\":").append(before == revision && (before & 1L) == 0L).append('}');
        return out.toString();
    }

    private static void field(StringBuilder out, String name, long value)
    { out.append(",\"").append(name).append("\":").append(value); }

    static int firstWord(byte[] bytes)
    {
        return (bytes[0] & 255) << 24 | (bytes[1] & 255) << 16
                | (bytes[2] & 255) << 8 | (bytes[3] & 255);
    }

    /** Last completed local Push reply, not a remote ACK or physical ring size. */
    public int lastCompletedPushCreditBytes()
    {
        return enabled ? lastPushReplyFree : -1;
    }

    /** Called only by the bounded incident writer, never by the socket worker. */
    public String workerStack()
    {
        Thread thread = worker;
        if (!enabled || thread == null) return "unavailable";
        try
        {
            StringBuilder out = new StringBuilder(thread.getState().name());
            StackTraceElement[] stack = thread.getStackTrace();
            for (int i = 0; i < Math.min(16, stack.length); i++) out.append('\n').append(stack[i]);
            return out.toString();
        }
        catch (RuntimeException ignored) { return "unavailable"; }
    }
}
