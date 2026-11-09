package opensagetv.vibe.miniclient.video;

/** Fences real native A53 payloads; does not infer languages or caption services. */
public final class NativeCeaSampleGate
{
    private int lastSerial = -1;
    private int minimumSerial;
    private boolean seeking;
    private boolean closed;
    private int index608 = -1, index708 = -1, formatCount;

    public synchronized boolean accept(int version, int serial, long timeUs, byte[] data)
    {
        if (closed || seeking || version != 1 || serial < minimumSerial
                || serial < lastSerial || timeUs < 0L || timeUs > Long.MAX_VALUE / 45L
                || data == null || data.length < 3 || data.length > 93 || data.length % 3 != 0)
            return false;
        boolean content = false;
        for (int i = 0; i < data.length; i += 3)
        {
            int header = data[i] & 255;
            if ((header & 4) == 0) continue;
            // Valid 608 padding is not proof that the programme has captions.
            boolean digital = (header & 3) >= 2;
            boolean validContent = digital ? data[i + 1] != 0 || data[i + 2] != 0
                    : (data[i + 1] & 127) != 0 || (data[i + 2] & 127) != 0;
            if (!validContent) continue;
            content = true;
            // Discovery order, not codec sort order, owns stable native IDs.
            // A later 608 packet must not renumber an already selected 708 row.
            if (digital && index708 < 0) index708 = formatCount++;
            if (!digital && index608 < 0) index608 = formatCount++;
        }
        if (!content) return false;
        lastSerial = serial;
        return true;
    }

    public synchronized void beginSeek()
    {
        seeking = true;
        minimumSerial = lastSerial < 0 ? 0
                : lastSerial == Integer.MAX_VALUE ? Integer.MAX_VALUE : lastSerial + 1;
        // FLUSH can also be stock fastLoad's new file, not only a seek. Do
        // not carry the old programme's caption inventory into replacement
        // media. New serial data re-discovers formats and reapplies the slot.
        index608 = index708 = -1;
        formatCount = 0;
    }

    public synchronized void completeSeek() { seeking = false; }
    public synchronized int index608() { return index608; }
    public synchronized int index708() { return index708; }
    public synchronized void close() { closed = true; }
}
