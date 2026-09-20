package opensagetv.vibe.miniclient.video;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import opensagetv.vibe.miniclient.media.TeletextSubtitleEngine;

/**
 * Maps the first two DVB Teletext subtitle services onto the stock SageTV
 * CC1/CC2 control surface by producing ordinary legacy-extender CEA-608 records.
 */
public final class TeletextCea608Bridge
{
    public interface Sink
    {
        void postSubtitleInfo(long pts45Khz, long duration45Khz, byte[] data, int flags);
    }

    private final Sink sink;
    private final ArrayDeque<Pending> pending = new ArrayDeque<Pending>();
    private int cc1Track = -1;
    private int cc2Track = -1;
    private long generation = -1L;

    public TeletextCea608Bridge(Sink sink)
    {
        if (sink == null) throw new IllegalArgumentException("sink must not be null");
        this.sink = sink;
    }

    /** Stable default: first declared subtitle service is CC1, second is CC2. */
    public synchronized void setServices(TeletextSubtitleEngine.Service[] services)
    {
        cc1Track = services != null && services.length > 0 ? services[0].trackId : -1;
        cc2Track = services != null && services.length > 1 ? services[1].trackId : -1;
    }

    public synchronized void setTrackMappings(int cc1Track, int cc2Track)
    {
        this.cc1Track = cc1Track;
        // A stock SageTV server cannot tell the MiniClient which CC channel
        // its STV selected. Allow one Teletext service to be mirrored onto
        // both legacy CEA-608 channels so either stock CC1 or CC2 can render
        // it. Distinct services still map independently when available.
        this.cc2Track = cc2Track;
    }

    public synchronized void enqueue(TeletextSubtitleEngine.Cue cue)
    {
        if (cue == null) return;
        if (cue.generation != generation)
        {
            pending.clear();
            generation = cue.generation;
        }
        if (cue.trackId == cc1Track) enqueue(cue, 0);
        if (cue.trackId == cc2Track) enqueue(cue, 1);
    }

    private void enqueue(TeletextSubtitleEngine.Cue cue, int channel)
    {
        Pending item = new Pending(cue.presentationTimeMs, channel, cue.text);
        if (pending.isEmpty() || pending.peekLast().timeMs <= item.timeMs)
            pending.addLast(item);
        else
        {
            ArrayDeque<Pending> sorted = new ArrayDeque<Pending>(pending.size() + 1);
            boolean inserted = false;
            while (!pending.isEmpty())
            {
                Pending old = pending.removeFirst();
                if (!inserted && item.timeMs < old.timeMs)
                {
                    sorted.addLast(item);
                    inserted = true;
                }
                sorted.addLast(old);
            }
            if (!inserted) sorted.addLast(item);
            pending.addAll(sorted);
        }
    }

    /**
     * Deliver every cue due at the supplied player clock, never read-ahead.
     *
     * <p>The independent Android playback-clock pump and SageTV's media-time
     * request can arrive on different threads. Serialize the whole drain,
     * including sink submission, so a later batch can never overtake an
     * earlier batch on the legacy event-225 wire.</p>
     */
    public synchronized void drainTo(long playbackTimeMs)
    {
        List<Pending> due = new ArrayList<Pending>();
        while (!pending.isEmpty() && pending.peekFirst().timeMs <= playbackTimeMs + 80L)
            due.add(pending.removeFirst());
        for (Pending item : due)
        {
            long pts45 = Math.max(0L, item.timeMs) * 45L;
            sink.postSubtitleInfo(pts45, 0L, encode(item.text, item.channel, (int) pts45),
                    LegacyExtenderCaptionBridge.CC_SUBTITLE
                            | LegacyExtenderCaptionBridge.PTS_VALID);
        }
    }

    public synchronized void clearPending()
    {
        pending.clear();
        generation++;
    }

    public void postFlush()
    {
        sink.postSubtitleInfo(0L, 0L, LegacyExtenderCaptionBridge.buildCea608ResetRecords(),
                LegacyExtenderCaptionBridge.CC_SUBTITLE
                        | LegacyExtenderCaptionBridge.FLUSH_SUBTITLE_QUEUE);
    }

    static byte[] encode(String text, int channel, int pts45Khz)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeControl(out, pts45Khz, channel, 0x2c); // erase displayed memory
        writeControl(out, pts45Khz, channel, 0x2e); // erase non-displayed memory
        writeControl(out, pts45Khz, channel, 0x20); // resume caption loading

        List<String> source = formatLines(text);
        int row = 12 + Math.max(0, 3 - source.size());
        for (String line : source)
        {
            writePac(out, pts45Khz, channel, Math.min(14, row));
            writeText(out, pts45Khz, channel, line);
            row++;
        }
        writeControl(out, pts45Khz, channel, 0x2f); // end of caption / swap memories
        return out.toByteArray();
    }

    private static void writeControl(ByteArrayOutputStream out, int pts, int channel, int second)
    {
        int first = 0x14 | (channel == 1 ? 0x08 : 0);
        writeRecord(out, pts, first, second);
        writeRecord(out, pts, first, second); // CEA-608 controls are repeated.
    }

    private static void writePac(ByteArrayOutputStream out, int pts, int channel, int row)
    {
        final int[] rowFirst = {0, 0x11, 0x11, 0x12, 0x12, 0x15, 0x15, 0x16,
                0x16, 0x17, 0x17, 0x10, 0x13, 0x13, 0x14, 0x14};
        boolean lower = row == 2 || row == 4 || row == 6 || row == 8
                || row == 10 || row == 13 || row == 15;
        int first = rowFirst[row] | (channel == 1 ? 0x08 : 0);
        int second = 0x40 | (lower ? 0x20 : 0) | (8 << 1);
        writeRecord(out, pts, first, second);
    }

    private static void writeText(ByteArrayOutputStream out, int pts, int channel, String text)
    {
        String safe = text == null ? "" : text;
        for (int i = 0; i < safe.length(); i += 2)
        {
            int a = ceaCharacter(safe.charAt(i));
            int b = i + 1 < safe.length() ? ceaCharacter(safe.charAt(i + 1)) : 0;
            writeRecord(out, pts, a, b);
        }
    }

    /** Reflow 40-column Teletext text into at most three 32-column CEA rows. */
    static List<String> formatLines(String text)
    {
        ArrayList<String> lines = new ArrayList<String>();
        String normalized = text == null ? "" : text.replace('\n', ' ').trim()
                .replaceAll("\\s+", " ");
        StringBuilder line = new StringBuilder();
        for (String word : normalized.split(" "))
        {
            if (word.length() == 0) continue;
            while (word.length() > 32)
            {
                if (line.length() > 0)
                {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, 32));
                word = word.substring(32);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > 32)
            {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        if (lines.size() <= 3) return lines;
        return new ArrayList<String>(lines.subList(lines.size() - 3, lines.size()));
    }

    private static int ceaCharacter(char value)
    {
        return value >= 0x20 && value <= 0x7f ? value : 0x20;
    }

    private static void writeRecord(ByteArrayOutputStream out, int pts, int first, int second)
    {
        out.write((pts >>> 24) & 0xff);
        out.write((pts >>> 16) & 0xff);
        out.write((pts >>> 8) & 0xff);
        out.write(pts & 0xff);
        out.write(0); // field one: CC1 and CC2
        out.write(withOddParity(first));
        out.write(withOddParity(second));
        out.write(1);
    }

    private static int withOddParity(int value)
    {
        int seven = value & 0x7f;
        return (Integer.bitCount(seven) & 1) == 0 ? seven | 0x80 : seven;
    }

    private static final class Pending
    {
        final long timeMs;
        final int channel;
        final String text;
        Pending(long timeMs, int channel, String text)
        {
            this.timeMs = timeMs;
            this.channel = channel;
            this.text = text == null ? "" : text;
        }
    }
}
