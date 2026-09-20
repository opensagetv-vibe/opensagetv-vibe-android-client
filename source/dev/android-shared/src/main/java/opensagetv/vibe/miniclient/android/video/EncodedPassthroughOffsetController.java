package opensagetv.vibe.miniclient.android.video;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session-local A/V timestamp policy for encoded audio passthrough.
 *
 * <p>The encoded access unit bytes are never changed. Positive offsets delay
 * audio timestamps. Negative offsets delay video timestamps, because audio
 * already submitted to an external receiver cannot be pulled earlier.</p>
 */
public final class EncodedPassthroughOffsetController
{
    public static final int MIN_OFFSET_MS = -4_000;
    public static final int MAX_OFFSET_MS = 4_000;

    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicInteger offsetMs = new AtomicInteger();
    private final AtomicLong audioSamplesShifted = new AtomicLong();
    private final AtomicLong videoSamplesShifted = new AtomicLong();

    public EncodedPassthroughOffsetController(boolean enabled, int offsetMs)
    {
        this.enabled.set(enabled);
        this.offsetMs.set(clamp(offsetMs));
    }

    public boolean isEnabled()
    {
        return enabled.get();
    }

    public void setEnabled(boolean value)
    {
        enabled.set(value);
    }

    public int getOffsetMillis()
    {
        return offsetMs.get();
    }

    public void setOffsetMillis(int value)
    {
        offsetMs.set(clamp(value));
    }

    public int getAppliedOffsetMillis()
    {
        return isEnabled() ? getOffsetMillis() : 0;
    }

    public long shiftAudioSampleTimeUs(long timeUs)
    {
        int value = getAppliedOffsetMillis();
        if (value <= 0) return timeUs;
        audioSamplesShifted.incrementAndGet();
        return addDelay(timeUs, value * 1_000L);
    }

    public long shiftVideoSampleTimeUs(long timeUs)
    {
        int value = getAppliedOffsetMillis();
        if (value >= 0) return timeUs;
        videoSamplesShifted.incrementAndGet();
        return addDelay(timeUs, -(value * 1_000L));
    }

    public long getAudioSamplesShifted()
    {
        return audioSamplesShifted.get();
    }

    public long getVideoSamplesShifted()
    {
        return videoSamplesShifted.get();
    }

    public String describe()
    {
        if (!isEnabled()) return "passthrough clock offset disabled";
        int value = getOffsetMillis();
        String side = value > 0 ? "audio delayed" : value < 0 ? "video delayed" : "zero";
        return "passthrough clock offset " + value + " ms (" + side
                + ", shifted audio=" + getAudioSamplesShifted()
                + ", video=" + getVideoSamplesShifted() + ")";
    }

    static int clamp(int value)
    {
        return Math.max(MIN_OFFSET_MS, Math.min(MAX_OFFSET_MS, value));
    }

    private static long addDelay(long timeUs, long delayUs)
    {
        if (delayUs <= 0L || timeUs == Long.MIN_VALUE
                || timeUs == Long.MIN_VALUE + 1L)
            return timeUs;
        if (timeUs > Long.MAX_VALUE - delayUs) return Long.MAX_VALUE;
        return timeUs + delayUs;
    }
}
