package opensagetv.vibe.miniclient.android.tv.debug;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Deque;

import opensagetv.vibe.miniclient.MiniPlayerPlugin;

/**
 * Debug-build-only exact-event ring buffer for MCP/Codex diagnostics.
 *
 * Player callbacks call this through PlaybackDebugTrap. Each event captures the same
 * renderer/audio counters used by PlaybackHealthProbe at the instant the Android event
 * happens, avoiding host-poll latency when diagnosing seek/rebuffer/recovery behavior.
 */
public final class PlaybackEventTraps
{
    private static final int MAX_EVENTS = 32;
    private static final Deque<Event> EVENTS = new ArrayDeque<Event>(MAX_EVENTS);
    private static long sequence;

    private PlaybackEventTraps()
    {
    }

    public static void record(String event, MiniPlayerPlugin player)
    {
        recordDetailed(event, player, "");
    }

    public static void recordDetailed(String event, MiniPlayerPlugin player, String detail)
    {
        Event trapped = newEvent(event);
        trapped.detail = safeDetail(detail);
        fillSnapshot(trapped, PlaybackHealthProbe.capture(player));
        addEvent(trapped);
        PersistentPlaybackTrace.append(toJsonLine(trapped));
    }

    /** Record the protocol-event timestamp immediately, then capture counters on the UI thread. */
    public static void recordAsync(String event, final MiniPlayerPlugin player)
    {
        recordAsyncDetailed(event, player, "");
    }

    public static void recordAsyncDetailed(String event, final MiniPlayerPlugin player,
                                           String detail)
    {
        final Event trapped = newEvent(event);
        trapped.detail = safeDetail(detail);
        addEvent(trapped);
        new Handler(Looper.getMainLooper()).post(new Runnable()
        {
            @Override
            public void run()
            {
                PlaybackHealthProbe.Snapshot snapshot = PlaybackHealthProbe.capture(player);
                synchronized (EVENTS)
                {
                    fillSnapshot(trapped, snapshot);
                }
                PersistentPlaybackTrace.append(toJsonLine(trapped));
            }
        });
    }

    private static Event newEvent(String event)
    {
        Event trapped = new Event();
        trapped.sequence = nextSequence();
        trapped.event = safeToken(event);
        trapped.monotonicMs = monotonicMs();
        trapped.wallMs = System.currentTimeMillis();
        return trapped;
    }

    private static void fillSnapshot(Event trapped, PlaybackHealthProbe.Snapshot snapshot)
    {
        trapped.snapshotMonotonicMs = monotonicMs();
        trapped.snapshotLagMs = Math.max(0L, trapped.snapshotMonotonicMs - trapped.monotonicMs);
        trapped.playerPositionMs = snapshot.playerPositionMs;
        trapped.bufferedPositionMs = snapshot.bufferedPositionMs;
        trapped.durationMs = snapshot.durationMs;
        trapped.connectionGeneration = snapshot.connectionGeneration;
        trapped.connectionReconnectCount = snapshot.connectionReconnectCount;
        trapped.playbackSessionGeneration = snapshot.playbackSessionGeneration;
        trapped.miniState = snapshot.miniState;
        trapped.playbackState = snapshot.playbackState;
        trapped.isPlaying = snapshot.isPlaying || snapshot.basicIsPlaying;
        trapped.isLoading = snapshot.isLoading;
        trapped.videoExpected = snapshot.expectsVideo();
        trapped.audioExpected = snapshot.expectsAudio();
        trapped.videoRendered = snapshot.videoRendered;
        trapped.videoQueuedInput = snapshot.videoQueuedInput;
        trapped.videoDecoderInitCount = snapshot.videoDecoderInitCount;
        trapped.videoDecoderReleaseCount = snapshot.videoDecoderReleaseCount;
        trapped.audioRendered = snapshot.audioRendered;
        trapped.audioQueuedInput = snapshot.audioQueuedInput;
        trapped.audioDecoderInitCount = snapshot.audioDecoderInitCount;
        trapped.audioDecoderReleaseCount = snapshot.audioDecoderReleaseCount;
        trapped.audioHeadFrames = snapshot.audioPlaybackHeadFrames;
        trapped.audioSessionId = snapshot.audioSessionId;
        trapped.surfaceValid = snapshot.surfaceValid;
        trapped.probeSupported = snapshot.supported;
        trapped.videoDecoderKind = safeToken(snapshot.videoDecoderKind);
        trapped.audioDecoderKind = safeToken(snapshot.audioDecoderKind);
    }

    private static void addEvent(Event trapped)
    {
        synchronized (EVENTS)
        {
            while (EVENTS.size() >= MAX_EVENTS)
                EVENTS.removeFirst();
            EVENTS.addLast(trapped);
        }
    }


    public static String compactWire()
    {
        StringBuilder out = new StringBuilder();
        Event last;
        synchronized (EVENTS)
        {
            last = EVENTS.peekLast();
            out.append("trapEventCount=").append(EVENTS.size());
            out.append(";trapSequence=").append(sequence);
            if (last != null)
            {
                appendLast(out, last);
                out.append(";trapRecent=");
                boolean first = true;
                for (Event event : EVENTS)
                {
                    if (!first)
                        out.append('|');
                    first = false;
                    appendCompactEvent(out, event);
                }
            }
        }
        return out.toString();
    }

    public static String clearWire()
    {
        synchronized (EVENTS)
        {
            EVENTS.clear();
        }
        return "cleared=true;trapSequence=" + sequence;
    }

    public static String traceStatusWire(android.content.Context context)
    {
        return PersistentPlaybackTrace.statusWire(context);
    }

    public static String clearTraceWire(android.content.Context context)
    {
        return PersistentPlaybackTrace.clearWire(context);
    }

    public static String setTraceEnabledWire(android.content.Context context, boolean enabled)
    {
        return PersistentPlaybackTrace.setEnabledWire(context, enabled);
    }

    static long currentSequence()
    {
        synchronized (EVENTS)
        {
            return sequence;
        }
    }

    static long firstEventMonotonicAfter(long afterSequence, String event)
    {
        String expected = safeToken(event);
        synchronized (EVENTS)
        {
            for (Event trapped : EVENTS)
            {
                if (trapped.sequence > afterSequence && expected.equals(trapped.event))
                    return trapped.monotonicMs;
            }
        }
        return -1;
    }

    private static long monotonicMs()
    {
        return System.nanoTime() / 1000000L;
    }

    private static synchronized long nextSequence()
    {
        return ++sequence;
    }

    private static void appendLast(StringBuilder out, Event e)
    {
        out.append(";trapLastEvent=").append(e.event);
        out.append(";trapLastEventSequence=").append(e.sequence);
        out.append(";trapLastEventMonotonicMs=").append(e.monotonicMs);
        out.append(";trapLastEventWallMs=").append(e.wallMs);
        out.append(";trapLastSnapshotMonotonicMs=").append(e.snapshotMonotonicMs);
        out.append(";trapLastSnapshotLagMs=").append(e.snapshotLagMs);
        out.append(";trapLastPlayerPositionMs=").append(e.playerPositionMs);
        out.append(";trapLastMiniState=").append(e.miniState);
        out.append(";trapLastPlaybackState=").append(e.playbackState);
        out.append(";trapLastIsPlaying=").append(e.isPlaying);
        out.append(";trapLastIsLoading=").append(e.isLoading);
        out.append(";trapLastVideoExpected=").append(e.videoExpected);
        out.append(";trapLastAudioExpected=").append(e.audioExpected);
        out.append(";trapLastVideoRendered=").append(e.videoRendered);
        out.append(";trapLastVideoQueuedInput=").append(e.videoQueuedInput);
        out.append(";trapLastVideoDecoderInitCount=").append(e.videoDecoderInitCount);
        out.append(";trapLastVideoDecoderReleaseCount=").append(e.videoDecoderReleaseCount);
        out.append(";trapLastAudioRendered=").append(e.audioRendered);
        out.append(";trapLastAudioQueuedInput=").append(e.audioQueuedInput);
        out.append(";trapLastAudioDecoderInitCount=").append(e.audioDecoderInitCount);
        out.append(";trapLastAudioDecoderReleaseCount=").append(e.audioDecoderReleaseCount);
        out.append(";trapLastAudioHeadFrames=").append(e.audioHeadFrames);
        out.append(";trapLastAudioSessionId=").append(e.audioSessionId);
        out.append(";trapLastSurfaceValid=").append(e.surfaceValid);
        out.append(";trapLastProbeSupported=").append(e.probeSupported);
        out.append(";trapLastVideoDecoderKind=").append(e.videoDecoderKind);
        out.append(";trapLastAudioDecoderKind=").append(e.audioDecoderKind);
    }

    private static void appendCompactEvent(StringBuilder out, Event e)
    {
        // Keep each event single-token so the ADB broadcast parser can return it intact.
        out.append(e.sequence).append('@')
                .append(e.event).append('@')
                .append(e.monotonicMs).append('@')
                .append(e.snapshotLagMs).append('@')
                .append(e.playerPositionMs).append('@')
                .append(e.videoRendered).append('@')
                .append(e.videoQueuedInput).append('@')
                .append(e.videoDecoderInitCount).append('@')
                .append(e.videoDecoderReleaseCount).append('@')
                .append(e.audioRendered).append('@')
                .append(e.audioQueuedInput).append('@')
                .append(e.audioDecoderInitCount).append('@')
                .append(e.audioDecoderReleaseCount).append('@')
                .append(e.audioHeadFrames).append('@')
                .append(e.audioSessionId).append('@')
                .append(e.playbackState).append('@')
                .append(e.isPlaying ? 1 : 0).append('@')
                .append(e.isLoading ? 1 : 0).append('@')
                .append(e.videoExpected ? 1 : 0).append('@')
                .append(e.audioExpected ? 1 : 0).append('@')
                .append(e.surfaceValid ? 1 : 0).append('@')
                .append(e.probeSupported ? 1 : 0).append('@')
                .append(e.videoDecoderKind).append('@')
                .append(e.audioDecoderKind);
    }


    private static String safeToken(String value)
    {
        if (value == null)
            return "";
        return value.replace(';', '_').replace('|', '_').replace('@', '_')
                .replace(' ', '_').replace('\n', '_').replace('\r', '_');
    }

    private static String toJsonLine(Event e)
    {
        StringBuilder out = new StringBuilder(640);
        out.append('{');
        appendJson(out, "schema", 1).append(',');
        appendJson(out, "sequence", e.sequence).append(',');
        appendJson(out, "event", e.event).append(',');
        appendJson(out, "detail", e.detail).append(',');
        appendJson(out, "wallMs", e.wallMs).append(',');
        appendJson(out, "monotonicMs", e.monotonicMs).append(',');
        appendJson(out, "snapshotLagMs", e.snapshotLagMs).append(',');
        appendJson(out, "playerPositionMs", e.playerPositionMs).append(',');
        appendJson(out, "bufferedPositionMs", e.bufferedPositionMs).append(',');
        appendJson(out, "durationMs", e.durationMs).append(',');
        appendJson(out, "connectionGeneration", e.connectionGeneration).append(',');
        appendJson(out, "connectionReconnectCount", e.connectionReconnectCount).append(',');
        appendJson(out, "playbackSessionGeneration", e.playbackSessionGeneration).append(',');
        appendJson(out, "miniState", e.miniState).append(',');
        appendJson(out, "playbackState", e.playbackState).append(',');
        appendJson(out, "isPlaying", e.isPlaying).append(',');
        appendJson(out, "isLoading", e.isLoading).append(',');
        appendJson(out, "videoExpected", e.videoExpected).append(',');
        appendJson(out, "audioExpected", e.audioExpected).append(',');
        appendJson(out, "videoRendered", e.videoRendered).append(',');
        appendJson(out, "videoQueuedInput", e.videoQueuedInput).append(',');
        appendJson(out, "videoDecoderInitCount", e.videoDecoderInitCount).append(',');
        appendJson(out, "videoDecoderReleaseCount", e.videoDecoderReleaseCount).append(',');
        appendJson(out, "audioRendered", e.audioRendered).append(',');
        appendJson(out, "audioQueuedInput", e.audioQueuedInput).append(',');
        appendJson(out, "audioDecoderInitCount", e.audioDecoderInitCount).append(',');
        appendJson(out, "audioDecoderReleaseCount", e.audioDecoderReleaseCount).append(',');
        appendJson(out, "audioHeadFrames", e.audioHeadFrames).append(',');
        appendJson(out, "audioSessionId", e.audioSessionId).append(',');
        appendJson(out, "surfaceValid", e.surfaceValid).append(',');
        appendJson(out, "probeSupported", e.probeSupported).append(',');
        appendJson(out, "videoDecoderKind", e.videoDecoderKind).append(',');
        appendJson(out, "audioDecoderKind", e.audioDecoderKind);
        return out.append('}').toString();
    }

    private static StringBuilder appendJson(StringBuilder out, String name, long value)
    {
        return out.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder appendJson(StringBuilder out, String name, boolean value)
    {
        return out.append('"').append(name).append("\":").append(value);
    }

    private static StringBuilder appendJson(StringBuilder out, String name, String value)
    {
        out.append('"').append(name).append("\":\"");
        if (value != null)
        {
            for (int index = 0; index < value.length(); index++)
            {
                char c = value.charAt(index);
                if (c == '"' || c == '\\')
                    out.append('\\');
                if (c >= 0x20)
                    out.append(c);
            }
        }
        return out.append('"');
    }

    private static String safeDetail(String value)
    {
        if (value == null)
            return "";
        String redacted = value
                .replaceAll("(?i)(password|passwd|pwd|token|secret)=([^;&\\s]+)", "$1=<redacted>")
                .replaceAll("(?i)(smb://)([^/@:]+):([^/@]+)@", "$1<redacted>@");
        return redacted.replace('\n', ' ').replace('\r', ' ');
    }

    private static final class Event
    {
        long sequence;
        String event = "";
        String detail = "";
        long monotonicMs;
        long wallMs;
        long snapshotMonotonicMs = -1;
        long snapshotLagMs = -1;
        long playerPositionMs = -1;
        long bufferedPositionMs = -1;
        long durationMs = -1;
        long connectionGeneration = -1;
        long connectionReconnectCount = -1;
        long playbackSessionGeneration = -1;
        int miniState = -1;
        int playbackState = -1;
        boolean isPlaying;
        boolean isLoading;
        boolean videoExpected;
        boolean audioExpected;
        long videoRendered = -1;
        long videoQueuedInput = -1;
        long videoDecoderInitCount = -1;
        long videoDecoderReleaseCount = -1;
        long audioRendered = -1;
        long audioQueuedInput = -1;
        long audioDecoderInitCount = -1;
        long audioDecoderReleaseCount = -1;
        long audioHeadFrames = -1;
        int audioSessionId = -1;
        boolean surfaceValid;
        boolean probeSupported;
        String videoDecoderKind = "unknown";
        String audioDecoderKind = "unknown";
    }
}
