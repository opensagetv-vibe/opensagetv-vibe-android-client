package sagex.miniclient.android.tv.debug;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Deque;

import sagex.miniclient.MiniPlayerPlugin;

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
        Event trapped = newEvent(event);
        fillSnapshot(trapped, PlaybackHealthProbe.capture(player));
        addEvent(trapped);
    }

    /** Record the protocol-event timestamp immediately, then capture counters on the UI thread. */
    public static void recordAsync(String event, final MiniPlayerPlugin player)
    {
        final Event trapped = newEvent(event);
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

    private static final class Event
    {
        long sequence;
        String event = "";
        long monotonicMs;
        long wallMs;
        long snapshotMonotonicMs = -1;
        long snapshotLagMs = -1;
        long playerPositionMs = -1;
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
