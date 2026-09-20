package opensagetv.vibe.miniclient.media;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded, user-initiated probe that proves whether DVB Teletext PES data
 * survives the active playback transport. It stores stream metadata and
 * counters only; media payload and decoded subtitle text are never retained.
 */
public final class TeletextPesProbe
{
    private static final long MAX_ANALYZED_BYTES = 64L * 1024L * 1024L;
    private static final AtomicReference<Session> ACTIVE =
            new AtomicReference<Session>();

    private TeletextPesProbe() { }

    /** Replaces any previous probe and begins one bounded diagnostic session. */
    public static void begin()
    {
        ACTIVE.set(new Session());
    }

    public static boolean isActive()
    {
        return ACTIVE.get() != null;
    }

    /** Called by playback datasources; this is a near-zero-cost no-op when inactive. */
    public static void observe(String source, long absolutePosition,
            byte[] bytes, int offset, int length)
    {
        Session session = ACTIVE.get();
        if (session != null) session.observe(source, absolutePosition, bytes, offset, length);
    }

    /** Marks a seek, Push FLUSH, or source replacement without ending the probe. */
    public static void discontinuity(String reason)
    {
        Session session = ACTIVE.get();
        if (session != null) session.discontinuity(reason);
    }

    /** Stops the active probe and returns an immutable metadata-only result. */
    public static Snapshot finish()
    {
        Session session = ACTIVE.getAndSet(null);
        return session == null ? Snapshot.inactive() : session.snapshot();
    }

    /** Stops and discards an active probe. */
    public static void cancel()
    {
        ACTIVE.set(null);
    }

    public static final class Snapshot
    {
        public final String status;
        public final long offeredBytes;
        public final long analyzedBytes;
        public final int packetSize;
        public final long transportPackets;
        public final long patSections;
        public final long pmtSections;
        public final long teletextDescriptors;
        public final long subtitleServices;
        public final long teletextPackets;
        public final long pesPackets;
        public final long teletextDataUnits;
        public final long ptsCount;
        public final long firstPts90Khz;
        public final long lastPts90Khz;
        public final long ptsRegressions;
        public final long continuityErrors;
        public final long syncLosses;
        public final long discontinuities;
        public final boolean capped;
        public final String sources;
        public final String services;

        private Snapshot(String status, Session session)
        {
            this.status = status;
            offeredBytes = session.offeredBytes;
            analyzedBytes = session.analyzedBytes;
            packetSize = session.packetSize;
            transportPackets = session.transportPackets;
            patSections = session.patSections;
            pmtSections = session.pmtSections;
            teletextDescriptors = session.teletextDescriptors;
            subtitleServices = session.subtitleServices;
            teletextPackets = session.teletextPackets;
            pesPackets = session.pesPackets;
            teletextDataUnits = session.teletextDataUnits;
            ptsCount = session.ptsCount;
            firstPts90Khz = session.firstPts90Khz;
            lastPts90Khz = session.lastPts90Khz;
            ptsRegressions = session.ptsRegressions;
            continuityErrors = session.continuityErrors;
            syncLosses = session.syncLosses;
            discontinuities = session.discontinuities;
            capped = session.analyzedBytes >= MAX_ANALYZED_BYTES;
            sources = join(session.sources);
            services = join(session.services.values());
        }

        private static Snapshot inactive()
        {
            return new Snapshot("inactive", new Session());
        }

        public boolean isTeletextDetected()
        {
            return teletextDescriptors > 0;
        }

        public boolean isPreserved()
        {
            return "preserved".equals(status);
        }

        public String exportText()
        {
            return "status=" + status + '\n'
                    + "sources=" + sources + '\n'
                    + "offeredBytes=" + offeredBytes + '\n'
                    + "analyzedBytes=" + analyzedBytes + '\n'
                    + "analysisCapped=" + capped + '\n'
                    + "transportPacketSize=" + packetSize + '\n'
                    + "transportPackets=" + transportPackets + '\n'
                    + "patSections=" + patSections + '\n'
                    + "pmtSections=" + pmtSections + '\n'
                    + "teletextDescriptors=" + teletextDescriptors + '\n'
                    + "teletextSubtitleServices=" + subtitleServices + '\n'
                    + "teletextServices=" + services + '\n'
                    + "teletextTransportPackets=" + teletextPackets + '\n'
                    + "teletextPesPackets=" + pesPackets + '\n'
                    + "teletextDataUnits=" + teletextDataUnits + '\n'
                    + "ptsCount=" + ptsCount + '\n'
                    + "firstPts90Khz=" + firstPts90Khz + '\n'
                    + "lastPts90Khz=" + lastPts90Khz + '\n'
                    + "ptsRegressions=" + ptsRegressions + '\n'
                    + "continuityErrors=" + continuityErrors + '\n'
                    + "syncLosses=" + syncLosses + '\n'
                    + "discontinuities=" + discontinuities + '\n';
        }

        private static String join(Iterable<?> values)
        {
            StringBuilder text = new StringBuilder();
            for (Object value : values)
            {
                if (text.length() > 0) text.append(',');
                text.append(value);
            }
            return text.length() == 0 ? "none" : text.toString();
        }
    }

    private static final class Session
    {
        private final Set<String> sources = new LinkedHashSet<String>();
        private final Set<Integer> pmtPids = new LinkedHashSet<Integer>();
        private final Set<Integer> teletextPids = new LinkedHashSet<Integer>();
        private final Set<String> subtitleServiceKeys = new LinkedHashSet<String>();
        private final Map<String, String> services = new LinkedHashMap<String, String>();
        private final Map<Integer, SectionAssembler> sections =
                new LinkedHashMap<Integer, SectionAssembler>();
        private final int[] lastContinuity = new int[8192];
        private byte[] carry = new byte[0];
        private int packetSize;
        private long offeredBytes;
        private long analyzedBytes;
        private long transportPackets;
        private long patSections;
        private long pmtSections;
        private long teletextDescriptors;
        private long subtitleServices;
        private long teletextPackets;
        private long pesPackets;
        private long teletextDataUnits;
        private long ptsCount;
        private long firstPts90Khz = -1L;
        private long lastPts90Khz = -1L;
        private long ptsRegressions;
        private long continuityErrors;
        private long syncLosses;
        private long discontinuities;
        private String lastSource;
        private long expectedPosition = -1L;

        private Session()
        {
            Arrays.fill(lastContinuity, -1);
            sections.put(Integer.valueOf(0), new SectionAssembler());
        }

        private synchronized void observe(String source, long absolutePosition,
                byte[] bytes, int offset, int length)
        {
            if (bytes == null || length <= 0 || offset < 0
                    || offset > bytes.length - length) return;
            offeredBytes += length;
            if (analyzedBytes >= MAX_ANALYZED_BYTES) return;
            int allowed = (int) Math.min((long) length,
                    MAX_ANALYZED_BYTES - analyzedBytes);
            analyzedBytes += allowed;
            String safeSource = source == null || source.length() == 0 ? "unknown" : source;
            sources.add(safeSource);
            boolean changed = lastSource != null && !lastSource.equals(safeSource);
            boolean jumped = absolutePosition >= 0 && expectedPosition >= 0
                    && absolutePosition != expectedPosition;
            if (changed || jumped) resetFraming();
            lastSource = safeSource;
            expectedPosition = absolutePosition < 0 ? -1L : absolutePosition + allowed;

            byte[] combined = new byte[carry.length + allowed];
            System.arraycopy(carry, 0, combined, 0, carry.length);
            System.arraycopy(bytes, offset, combined, carry.length, allowed);
            parse(combined);
        }

        private synchronized void discontinuity(String reason)
        {
            discontinuities++;
            resetFraming();
            expectedPosition = -1L;
        }

        private void resetFraming()
        {
            carry = new byte[0];
            packetSize = 0;
            Arrays.fill(lastContinuity, -1);
            for (SectionAssembler assembler : sections.values()) assembler.reset();
            lastPts90Khz = -1L;
        }

        private void parse(byte[] bytes)
        {
            int position = 0;
            if (packetSize == 0)
            {
                int[] sync = findSync(bytes, 0);
                if (sync == null)
                {
                    carry = tail(bytes, 816);
                    return;
                }
                position = sync[0];
                packetSize = sync[1];
            }

            while (position + packetSize <= bytes.length)
            {
                if ((bytes[position] & 0xff) != 0x47)
                {
                    syncLosses++;
                    packetSize = 0;
                    int[] sync = findSync(bytes, position + 1);
                    if (sync == null)
                    {
                        carry = tail(bytes, 816);
                        return;
                    }
                    position = sync[0];
                    packetSize = sync[1];
                    continue;
                }
                parsePacket(bytes, position);
                position += packetSize;
            }
            carry = Arrays.copyOfRange(bytes, position, bytes.length);
        }

        private void parsePacket(byte[] bytes, int start)
        {
            transportPackets++;
            if (start + 188 > bytes.length) return;
            int b1 = bytes[start + 1] & 0xff;
            if ((b1 & 0x80) != 0) return;
            boolean payloadStart = (b1 & 0x40) != 0;
            int pid = ((b1 & 0x1f) << 8) | (bytes[start + 2] & 0xff);
            int b3 = bytes[start + 3] & 0xff;
            int adaptationControl = (b3 >> 4) & 0x03;
            boolean hasPayload = adaptationControl == 1 || adaptationControl == 3;
            boolean discontinuity = false;
            int payload = start + 4;
            if (adaptationControl == 2 || adaptationControl == 3)
            {
                int adaptationLength = bytes[payload] & 0xff;
                if (adaptationLength > 0 && payload + 1 < start + 188)
                    discontinuity = (bytes[payload + 1] & 0x80) != 0;
                payload += 1 + adaptationLength;
            }
            if (!hasPayload || payload >= start + 188) return;

            int continuity = b3 & 0x0f;
            int previous = lastContinuity[pid];
            if (!discontinuity && previous >= 0 && continuity != previous
                    && continuity != ((previous + 1) & 0x0f)) continuityErrors++;
            lastContinuity[pid] = continuity;

            if (pid == 0 || pmtPids.contains(Integer.valueOf(pid)))
            {
                SectionAssembler assembler = sections.get(Integer.valueOf(pid));
                if (assembler == null)
                {
                    assembler = new SectionAssembler();
                    sections.put(Integer.valueOf(pid), assembler);
                }
                List<byte[]> completed = assembler.consume(bytes, payload, start + 188,
                        payloadStart);
                for (byte[] section : completed) parseSection(pid, section);
            }

            if (teletextPids.contains(Integer.valueOf(pid)))
            {
                teletextPackets++;
                parseTeletextPayload(bytes, payload, start + 188, payloadStart);
            }
        }

        private void parseSection(int pid, byte[] section)
        {
            if (section.length < 8) return;
            int tableId = section[0] & 0xff;
            if (pid == 0 && tableId == 0x00)
            {
                patSections++;
                int end = section.length - 4;
                for (int pos = 8; pos + 4 <= end; pos += 4)
                {
                    int program = ((section[pos] & 0xff) << 8) | (section[pos + 1] & 0xff);
                    if (program == 0) continue;
                    int pmtPid = ((section[pos + 2] & 0x1f) << 8)
                            | (section[pos + 3] & 0xff);
                    pmtPids.add(Integer.valueOf(pmtPid));
                    if (!sections.containsKey(Integer.valueOf(pmtPid)))
                        sections.put(Integer.valueOf(pmtPid), new SectionAssembler());
                }
            }
            else if (tableId == 0x02)
            {
                pmtSections++;
                parsePmt(section);
            }
        }

        private void parsePmt(byte[] section)
        {
            if (section.length < 16) return;
            int programInfoLength = ((section[10] & 0x0f) << 8) | (section[11] & 0xff);
            int position = 12 + programInfoLength;
            int end = section.length - 4;
            while (position + 5 <= end)
            {
                int streamType = section[position] & 0xff;
                int elementaryPid = ((section[position + 1] & 0x1f) << 8)
                        | (section[position + 2] & 0xff);
                int infoLength = ((section[position + 3] & 0x0f) << 8)
                        | (section[position + 4] & 0xff);
                int descriptor = position + 5;
                int descriptorEnd = Math.min(end, descriptor + infoLength);
                while (descriptor + 2 <= descriptorEnd)
                {
                    int tag = section[descriptor] & 0xff;
                    int length = section[descriptor + 1] & 0xff;
                    int value = descriptor + 2;
                    int valueEnd = value + length;
                    if (valueEnd > descriptorEnd) break;
                    if (streamType == 0x06 && tag == 0x56)
                        parseTeletextDescriptor(elementaryPid, section, value, valueEnd);
                    descriptor = valueEnd;
                }
                position = descriptorEnd;
            }
        }

        private void parseTeletextDescriptor(int pid, byte[] bytes, int start, int end)
        {
            teletextPids.add(Integer.valueOf(pid));
            for (int position = start; position + 5 <= end; position += 5)
            {
                teletextDescriptors++;
                String language = ascii(bytes, position, 3);
                int typeAndMagazine = bytes[position + 3] & 0xff;
                int type = (typeAndMagazine >> 3) & 0x1f;
                int magazine = typeAndMagazine & 0x07;
                if (magazine == 0) magazine = 8;
                int pageByte = bytes[position + 4] & 0xff;
                int pagePart = ((pageByte >> 4) & 0x0f) * 10 + (pageByte & 0x0f);
                int page = magazine * 100 + pagePart;
                boolean subtitle = type == 0x02 || type == 0x05;
                String key = pid + ":" + language + ":" + type + ":" + page;
                if (subtitle && subtitleServiceKeys.add(key)) subtitleServices++;
                services.put(key, String.format(Locale.US,
                        "pid=0x%04x/lang=%s/type=%d/page=%d/subtitle=%s",
                        pid, language, type, page, subtitle));
            }
        }

        private void parseTeletextPayload(byte[] bytes, int start, int end,
                boolean payloadStart)
        {
            int scanStart = start;
            if (payloadStart && end - start >= 9 && bytes[start] == 0
                    && bytes[start + 1] == 0 && bytes[start + 2] == 1)
            {
                pesPackets++;
                int flags = bytes[start + 7] & 0xff;
                int headerLength = bytes[start + 8] & 0xff;
                if ((flags & 0x80) != 0 && start + 14 <= end)
                {
                    long pts = parsePts(bytes, start + 9);
                    if (pts >= 0)
                    {
                        if (firstPts90Khz < 0) firstPts90Khz = pts;
                        if (lastPts90Khz >= 0 && pts + 90000L < lastPts90Khz)
                            ptsRegressions++;
                        lastPts90Khz = pts;
                        ptsCount++;
                    }
                }
                scanStart = Math.min(end, start + 9 + headerLength);
                if (scanStart < end)
                {
                    int identifier = bytes[scanStart] & 0xff;
                    if ((identifier >= 0x10 && identifier <= 0x1f)
                            || (identifier >= 0x99 && identifier <= 0x9b)) scanStart++;
                }
            }
            for (int position = scanStart; position + 2 < end; position++)
            {
                int unit = bytes[position] & 0xff;
                int length = bytes[position + 1] & 0xff;
                if ((unit == 0x02 || unit == 0x03) && length == 0x2c)
                {
                    teletextDataUnits++;
                    position += Math.min(length + 1, end - position - 1);
                }
            }
        }

        private Snapshot snapshot()
        {
            String status;
            if (transportPackets == 0) status = "not-transport-stream";
            else if (teletextDescriptors == 0) status = "not-detected";
            else if (pesPackets == 0) status = "descriptor-only";
            else if (teletextDataUnits == 0) status = "pes-without-data-units";
            else status = "preserved";
            return new Snapshot(status, this);
        }
    }

    private static final class SectionAssembler
    {
        private byte[] section = new byte[4096];
        private int length;
        private int expected = -1;

        private List<byte[]> consume(byte[] source, int start, int end, boolean payloadStart)
        {
            List<byte[]> completed = new ArrayList<byte[]>();
            int position = start;
            if (payloadStart)
            {
                if (position >= end) return completed;
                int pointer = source[position++] & 0xff;
                int oldEnd = Math.min(end, position + pointer);
                if (length > 0) append(source, position, oldEnd, completed);
                position = oldEnd;
                if (length > 0) reset();
            }
            append(source, position, end, completed);
            return completed;
        }

        private void append(byte[] source, int start, int end, List<byte[]> completed)
        {
            int position = start;
            while (position < end)
            {
                if (length == 0 && (source[position] & 0xff) == 0xff) return;
                if (length >= section.length) { reset(); return; }
                section[length++] = source[position++];
                if (length == 3)
                {
                    expected = 3 + (((section[1] & 0x0f) << 8) | (section[2] & 0xff));
                    if (expected < 3 || expected > section.length) { reset(); return; }
                }
                if (expected > 0 && length == expected)
                {
                    completed.add(Arrays.copyOf(section, length));
                    reset();
                }
            }
        }

        private void reset()
        {
            length = 0;
            expected = -1;
        }
    }

    private static int[] findSync(byte[] bytes, int start)
    {
        int[] sizes = new int[]{188, 192, 204};
        for (int position = Math.max(0, start); position < bytes.length; position++)
        {
            if ((bytes[position] & 0xff) != 0x47) continue;
            for (int size : sizes)
            {
                if (position + size * 3 < bytes.length
                        && (bytes[position + size] & 0xff) == 0x47
                        && (bytes[position + size * 2] & 0xff) == 0x47
                        && (bytes[position + size * 3] & 0xff) == 0x47)
                    return new int[]{position, size};
            }
        }
        return null;
    }

    private static byte[] tail(byte[] bytes, int maximum)
    {
        int length = Math.min(bytes.length, maximum);
        return Arrays.copyOfRange(bytes, bytes.length - length, bytes.length);
    }

    private static long parsePts(byte[] bytes, int position)
    {
        if (position < 0 || position + 5 > bytes.length) return -1L;
        return ((long) (bytes[position] & 0x0e) << 29)
                | ((long) (bytes[position + 1] & 0xff) << 22)
                | ((long) (bytes[position + 2] & 0xfe) << 14)
                | ((long) (bytes[position + 3] & 0xff) << 7)
                | ((long) (bytes[position + 4] & 0xfe) >> 1);
    }

    private static String ascii(byte[] bytes, int start, int length)
    {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++)
        {
            int character = bytes[start + index] & 0xff;
            value.append(character >= 0x20 && character <= 0x7e
                    ? (char) character : '?');
        }
        return value.toString();
    }
}
