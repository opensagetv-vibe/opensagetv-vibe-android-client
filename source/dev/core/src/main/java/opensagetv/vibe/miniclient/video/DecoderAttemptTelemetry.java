package opensagetv.vibe.miniclient.video;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, session-owned decoder selection and recovery telemetry. */
public final class DecoderAttemptTelemetry
{
    private static final int MAX_EVENTS = 12;

    private final Map<String, String> candidatesByMime = new LinkedHashMap<>();
    private final List<String> events = new ArrayList<>();
    private final Set<String> exclusions = new LinkedHashSet<>();
    private String selectedVideo = "";
    private String selectedAudio = "";
    private String fallbackReason = "";
    private String recoveryResult = "";
    private int codecErrorCount;
    private int audioUnderrunCount;
    private int audioOutputErrorCount;
    private int formatChangeCount;
    private int trackChangeCount;

    public synchronized void reset()
    {
        candidatesByMime.clear();
        events.clear();
        exclusions.clear();
        selectedVideo = "";
        selectedAudio = "";
        fallbackReason = "";
        recoveryResult = "";
        codecErrorCount = 0;
        audioUnderrunCount = 0;
        audioOutputErrorCount = 0;
        formatChangeCount = 0;
        trackChangeCount = 0;
    }

    public synchronized void recordCandidates(String mimeType, List<String> names)
    {
        StringBuilder value = new StringBuilder();
        if (names != null)
        {
            for (String name : names)
            {
                if (value.length() > 0) value.append(',');
                value.append(clean(name));
            }
        }
        candidatesByMime.put(clean(mimeType), value.toString());
        addEvent("candidates:" + clean(mimeType) + "=" + value);
    }

    public synchronized void recordSelectedVideo(String name)
    {
        selectedVideo = clean(name);
        addEvent("video-selected:" + selectedVideo);
    }

    public synchronized void recordSelectedAudio(String name)
    {
        selectedAudio = clean(name);
        addEvent("audio-selected:" + selectedAudio);
    }

    public synchronized void recordCodecError(String error)
    {
        codecErrorCount++;
        addEvent("codec-error:" + clean(error));
    }

    public synchronized void recordAudioUnderrun()
    {
        audioUnderrunCount++;
        addEvent("audio-underrun");
    }

    public synchronized void recordAudioOutputError(String error)
    {
        audioOutputErrorCount++;
        addEvent("audio-output-error:" + clean(error));
    }

    public synchronized void recordFormatChange(String description)
    {
        formatChangeCount++;
        addEvent("format-change:" + clean(description));
    }

    public synchronized void recordTrackChange()
    {
        trackChangeCount++;
        addEvent("track-change");
    }

    public synchronized void recordExclusion(String decoder)
    {
        String name = clean(decoder);
        if (!name.isEmpty()) exclusions.add(name);
        addEvent("session-excluded:" + name);
    }

    public synchronized void recordFallback(String reason, String result)
    {
        fallbackReason = clean(reason);
        recoveryResult = clean(result);
        addEvent("fallback:" + fallbackReason + "=" + recoveryResult);
    }

    public synchronized String candidates()
    {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, String> item : candidatesByMime.entrySet())
        {
            if (value.length() > 0) value.append(';');
            value.append(item.getKey()).append('=').append(item.getValue());
        }
        return value.toString();
    }

    public synchronized String events() { return String.join(";", events); }
    public synchronized String exclusions() { return String.join(",", exclusions); }
    public synchronized String selectedVideo() { return selectedVideo; }
    public synchronized String selectedAudio() { return selectedAudio; }
    public synchronized String fallbackReason() { return fallbackReason; }
    public synchronized String recoveryResult() { return recoveryResult; }
    public synchronized int codecErrorCount() { return codecErrorCount; }
    public synchronized int audioUnderrunCount() { return audioUnderrunCount; }
    public synchronized int audioOutputErrorCount() { return audioOutputErrorCount; }
    public synchronized int formatChangeCount() { return formatChangeCount; }
    public synchronized int trackChangeCount() { return trackChangeCount; }

    private void addEvent(String event)
    {
        if (events.size() == MAX_EVENTS) events.remove(0);
        events.add(event);
    }

    private static String clean(String value)
    {
        if (value == null) return "";
        return value.replace(';', '_').replace('\n', '_').replace('\r', '_');
    }
}
