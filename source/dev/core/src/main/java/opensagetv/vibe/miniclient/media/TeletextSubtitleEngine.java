package opensagetv.vibe.miniclient.media;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small, player-independent DVB Teletext subtitle decoder.
 *
 * <p>The engine implements the Level-1 subtitle-page subset carried in DVB PES
 * data units. It intentionally does not implement the interactive Teletext
 * browser, news pages, or graphics extensions. A datasource feeds the same TS
 * bytes that it gives the active player; decoded page changes are then timed by
 * the Android presentation layer against the player clock.</p>
 */
public final class TeletextSubtitleEngine
{
    public static final int TRACK_BASE = 0x5400;
    private static final Object LOCK = new Object();
    private static Session active;

    private TeletextSubtitleEngine() { }

    public interface Listener
    {
        void onTeletextServicesChanged(Service[] services);
        void onTeletextCue(Cue cue);
    }

    public static final class Service
    {
        public final int trackId;
        public final int pid;
        public final String language;
        public final int type;
        public final int page;

        private Service(int trackId, int pid, String language, int type, int page)
        {
            this.trackId = trackId;
            this.pid = pid;
            this.language = language == null ? "" : language;
            this.type = type;
            this.page = page;
        }

        public String label()
        {
            return String.format(Locale.US, "Teletext %d", page);
        }
    }

    public static final class Cue
    {
        public final long generation;
        public final long presentationTimeMs;
        public final String text;
        public final int trackId;

        private Cue(long generation, long presentationTimeMs, String text, int trackId)
        {
            this.generation = generation;
            this.presentationTimeMs = presentationTimeMs;
            this.text = text == null ? "" : text;
            this.trackId = trackId;
        }
    }

    public static void activate(Listener listener)
    {
        synchronized (LOCK)
        {
            if (listener == null)
                active = null;
            else if (active == null || active.listener != listener)
                active = new Session(listener);
        }
    }

    public static void deactivate(Listener listener)
    {
        synchronized (LOCK)
        {
            if (active != null && active.listener == listener) active = null;
        }
    }

    public static void observe(String source, long absolutePosition,
            byte[] bytes, int offset, int length)
    {
        Session session;
        synchronized (LOCK) { session = active; }
        if (session != null) session.observe(source, absolutePosition, bytes, offset, length);
    }

    public static void discontinuity(String reason)
    {
        Session session;
        synchronized (LOCK) { session = active; }
        if (session != null) session.discontinuity();
    }

    public static void setPlaybackAnchor(long positionMs)
    {
        Session session;
        synchronized (LOCK) { session = active; }
        if (session != null) session.setPlaybackAnchor(positionMs);
    }

    public static boolean selectTrack(int trackId)
    {
        Session session;
        synchronized (LOCK) { session = active; }
        return session != null && session.selectTrack(trackId);
    }

    public static void disable()
    {
        Session session;
        synchronized (LOCK) { session = active; }
        if (session != null) session.disable();
    }

    public static Service[] services()
    {
        Session session;
        synchronized (LOCK) { session = active; }
        return session == null ? new Service[0] : session.services();
    }

    /** Bounded counters for explicit diagnostics; decoded media text is excluded. */
    public static String diagnostics()
    {
        Session session;
        synchronized (LOCK) { session = active; }
        return session == null ? "active=false" : session.diagnostics();
    }

    public static boolean isTeletextTrack(int trackId)
    {
        return trackId >= TRACK_BASE && trackId < TRACK_BASE + 1024;
    }

    private static final class Session
    {
        private static final int MAX_PES_BYTES = 256 * 1024;
        private final Listener listener;
        private final Set<Integer> pmtPids = new LinkedHashSet<Integer>();
        private final Set<Integer> videoPids = new LinkedHashSet<Integer>();
        private final Set<Integer> teletextPids = new LinkedHashSet<Integer>();
        private final Map<Integer, SectionAssembler> sections =
                new LinkedHashMap<Integer, SectionAssembler>();
        private final Map<Integer, PesAssembler> pes =
                new LinkedHashMap<Integer, PesAssembler>();
        private final Map<String, Service> serviceByKey =
                new LinkedHashMap<String, Service>();
        private final Map<Integer, PageState> pages =
                new LinkedHashMap<Integer, PageState>();
        private byte[] carry = new byte[0];
        private int packetSize;
        private String lastSource;
        private long expectedPosition = -1L;
        private int selectedTrack = -1;
        private long generation = 1L;
        private long playbackAnchorMs;
        private long firstMediaPts = -1L;
        private long observeCalls;
        private long observedBytes;
        private long transportPackets;
        private long patSections;
        private long pmtSections;
        private long teletextDescriptors;
        private long teletextPesPackets;
        private long teletextDataUnits;
        private long emittedCues;
        private long framingResets;

        private Session(Listener listener)
        {
            this.listener = listener;
            sections.put(Integer.valueOf(0), new SectionAssembler());
        }

        private synchronized void observe(String source, long absolutePosition,
                byte[] bytes, int offset, int length)
        {
            if (bytes == null || length <= 0 || offset < 0 || offset > bytes.length - length)
                return;
            observeCalls++;
            observedBytes += length;
            String safeSource = source == null ? "" : source;
            boolean sourceChanged = lastSource != null && !lastSource.equals(safeSource);
            boolean positionJumped = absolutePosition >= 0L && expectedPosition >= 0L
                    && absolutePosition != expectedPosition;
            // A non-contiguous read is a seek/probe discontinuity. Drop the
            // prior page state as well as partial PES data so captions from
            // the old media position cannot be emitted after the jump.
            if (sourceChanged || positionJumped) resetFraming(true);
            lastSource = safeSource;
            expectedPosition = absolutePosition < 0L ? -1L : absolutePosition + length;

            byte[] combined = new byte[carry.length + length];
            System.arraycopy(carry, 0, combined, 0, carry.length);
            System.arraycopy(bytes, offset, combined, carry.length, length);
            parse(combined);
        }

        private synchronized void discontinuity()
        {
            resetFraming(true);
            expectedPosition = -1L;
        }

        private synchronized void setPlaybackAnchor(long positionMs)
        {
            playbackAnchorMs = Math.max(0L, positionMs);
            resetFraming(true);
        }

        private synchronized boolean selectTrack(int trackId)
        {
            for (Service service : serviceByKey.values())
            {
                if (service.trackId == trackId)
                {
                    selectedTrack = trackId;
                    PageState page = pages.get(Integer.valueOf(trackId));
                    emit(page == null ? "" : page.text(), page == null ? -1L : page.lastPts,
                            trackId);
                    return true;
                }
            }
            return false;
        }

        private synchronized void disable()
        {
            selectedTrack = -1;
            emit("", -1L, -1);
        }

        private synchronized Service[] services()
        {
            return serviceByKey.values().toArray(new Service[serviceByKey.size()]);
        }

        private synchronized String diagnostics()
        {
            return "active=true,calls=" + observeCalls
                    + ",bytes=" + observedBytes
                    + ",tsPackets=" + transportPackets
                    + ",pat=" + patSections
                    + ",pmt=" + pmtSections
                    + ",descriptors=" + teletextDescriptors
                    + ",services=" + serviceByKey.size()
                    + ",pes=" + teletextPesPackets
                    + ",units=" + teletextDataUnits
                    + ",cues=" + emittedCues
                    + ",resets=" + framingResets;
        }

        private void resetFraming(boolean newGeneration)
        {
            framingResets++;
            carry = new byte[0];
            packetSize = 0;
            for (SectionAssembler assembler : sections.values()) assembler.reset();
            for (PesAssembler assembler : pes.values()) assembler.reset();
            firstMediaPts = -1L;
            if (newGeneration)
            {
                generation++;
                for (PageState page : pages.values()) page.clear();
                emit("", -1L, -1);
            }
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
            int control = (bytes[start + 3] >> 4) & 3;
            if (control != 1 && control != 3) return;
            int payload = start + 4;
            if (control == 3) payload += 1 + (bytes[payload] & 0xff);
            int end = start + 188;
            if (payload >= end) return;

            if (pid == 0 || pmtPids.contains(Integer.valueOf(pid)))
            {
                SectionAssembler assembler = sections.get(Integer.valueOf(pid));
                if (assembler == null)
                {
                    assembler = new SectionAssembler();
                    sections.put(Integer.valueOf(pid), assembler);
                }
                for (byte[] section : assembler.consume(bytes, payload, end, payloadStart))
                    parseSection(pid, section);
            }

            if (videoPids.contains(Integer.valueOf(pid)) && payloadStart)
            {
                long pts = pesPts(bytes, payload, end);
                if (pts >= 0L && firstMediaPts < 0L) firstMediaPts = pts;
            }

            if (teletextPids.contains(Integer.valueOf(pid)))
            {
                PesAssembler assembler = pes.get(Integer.valueOf(pid));
                if (assembler == null)
                {
                    assembler = new PesAssembler(pid);
                    pes.put(Integer.valueOf(pid), assembler);
                }
                assembler.consume(bytes, payload, end, payloadStart);
            }
        }

        private void parseSection(int pid, byte[] section)
        {
            if (section.length < 8) return;
            int table = section[0] & 0xff;
            if (pid == 0 && table == 0)
            {
                patSections++;
                for (int p = 8; p + 4 <= section.length - 4; p += 4)
                {
                    int program = ((section[p] & 0xff) << 8) | (section[p + 1] & 0xff);
                    if (program == 0) continue;
                    int pmt = ((section[p + 2] & 0x1f) << 8) | (section[p + 3] & 0xff);
                    pmtPids.add(Integer.valueOf(pmt));
                    if (!sections.containsKey(Integer.valueOf(pmt)))
                        sections.put(Integer.valueOf(pmt), new SectionAssembler());
                }
            }
            else if (table == 2)
            {
                pmtSections++;
                parsePmt(section);
            }
        }

        private void parsePmt(byte[] section)
        {
            if (section.length < 16) return;
            int p = 12 + (((section[10] & 15) << 8) | (section[11] & 0xff));
            int end = section.length - 4;
            while (p + 5 <= end)
            {
                int type = section[p] & 0xff;
                int pid = ((section[p + 1] & 31) << 8) | (section[p + 2] & 0xff);
                int infoLength = ((section[p + 3] & 15) << 8) | (section[p + 4] & 0xff);
                if (isVideoType(type)) videoPids.add(Integer.valueOf(pid));
                int d = p + 5;
                int dEnd = Math.min(end, d + infoLength);
                while (d + 2 <= dEnd)
                {
                    int tag = section[d] & 0xff;
                    int length = section[d + 1] & 0xff;
                    int value = d + 2;
                    if (value + length > dEnd) break;
                    if (type == 6 && tag == 0x56)
                        parseTeletextDescriptor(pid, section, value, value + length);
                    d = value + length;
                }
                p = dEnd;
            }
        }

        private void parseTeletextDescriptor(int pid, byte[] data, int start, int end)
        {
            boolean changed = false;
            teletextPids.add(Integer.valueOf(pid));
            for (int p = start; p + 5 <= end; p += 5)
            {
                teletextDescriptors++;
                String language = ascii(data, p, 3);
                int typeMagazine = data[p + 3] & 0xff;
                int type = (typeMagazine >> 3) & 31;
                if (type != 2 && type != 5) continue;
                int magazine = typeMagazine & 7;
                if (magazine == 0) magazine = 8;
                int bcd = data[p + 4] & 0xff;
                int page = magazine * 100 + ((bcd >> 4) & 15) * 10 + (bcd & 15);
                String key = pid + ":" + language + ":" + type + ":" + page;
                if (!serviceByKey.containsKey(key))
                {
                    Service service = new Service(TRACK_BASE + serviceByKey.size(), pid,
                            language, type, page);
                    serviceByKey.put(key, service);
                    pages.put(Integer.valueOf(service.trackId), new PageState(service));
                    changed = true;
                }
            }
            if (changed) listener.onTeletextServicesChanged(services());
        }

        private void parsePes(int pid, byte[] data, int length)
        {
            if (length < 10 || data[0] != 0 || data[1] != 0 || data[2] != 1) return;
            teletextPesPackets++;
            int headerLength = data[8] & 0xff;
            int payload = 9 + headerLength;
            if (payload >= length) return;
            long pts = (data[7] & 0x80) != 0 ? parsePts(data, 9, length) : -1L;
            if (firstMediaPts < 0L && pts >= 0L) firstMediaPts = pts;
            int identifier = data[payload] & 0xff;
            if ((identifier >= 0x10 && identifier <= 0x1f)
                    || (identifier >= 0x99 && identifier <= 0x9b)) payload++;
            Set<Integer> changed = new LinkedHashSet<Integer>();
            while (payload + 2 <= length)
            {
                int unit = data[payload] & 0xff;
                int unitLength = data[payload + 1] & 0xff;
                int value = payload + 2;
                if (value + unitLength > length) break;
                if ((unit == 2 || unit == 3) && unitLength == 44)
                {
                    teletextDataUnits++;
                    decodeUnit(pid, data, value, pts, changed);
                }
                payload = value + unitLength;
            }
            for (Integer track : changed)
            {
                PageState page = pages.get(track);
                if (page != null)
                    emit(page.text(), pts, page.service.trackId);
            }
        }

        private void decodeUnit(int pid, byte[] data, int start, long pts,
                Set<Integer> changed)
        {
            // data_field_line_address and framing_code precede the 42-byte packet.
            int a = unham(data[start + 2] & 0xff);
            int b = unham(data[start + 3] & 0xff);
            if (a < 0 || b < 0) return;
            int address = reverse8((a << 4) | b);
            int magazine = (address & 7) == 0 ? 8 : address & 7;
            int row = address >> 3;
            if (row == 0)
            {
                int ones = unham(data[start + 4] & 0xff);
                int tens = unham(data[start + 5] & 0xff);
                if (ones < 0 || tens < 0) return;
                // Hamming-protected header nibbles are transmitted least-
                // significant bit first, independently of the packet address.
                ones = reverse4(ones);
                tens = reverse4(tens);
                int pageNumber = magazine * 100 + tens * 10 + ones;
                for (PageState page : pages.values())
                {
                    if (page.service.pid != pid || page.service.page / 100 != magazine)
                        continue;
                    page.currentPage = pageNumber;
                    if (pageNumber == page.service.page)
                    {
                        // C4 is the erase-page control bit in a Level-1 header.
                        int c4 = unham(data[start + 7] & 0xff);
                        if (c4 >= 0) c4 = reverse4(c4);
                        if (c4 >= 0 && (c4 & 8) != 0) page.clearRows();
                        page.lastPts = pts;
                        changed.add(Integer.valueOf(page.service.trackId));
                    }
                }
                return;
            }
            if (row < 1 || row > 23) return;
            for (PageState page : pages.values())
            {
                if (page.service.pid != pid || page.currentPage != page.service.page) continue;
                String line = decodeText(data, start + 4, 40);
                if (!line.equals(page.rows[row]))
                {
                    page.rows[row] = line;
                    page.lastPts = pts;
                    changed.add(Integer.valueOf(page.service.trackId));
                }
            }
        }

        private void emit(String text, long pts, int trackId)
        {
            emittedCues++;
            long position = playbackAnchorMs;
            if (pts >= 0L && firstMediaPts >= 0L)
            {
                long delta = ptsDelta(pts, firstMediaPts);
                position += delta / 90L;
            }
            listener.onTeletextCue(new Cue(generation, position, text, trackId));
        }

        private final class PesAssembler
        {
            private final int pid;
            private byte[] bytes = new byte[4096];
            private int length;

            private PesAssembler(int pid) { this.pid = pid; }

            private void consume(byte[] source, int start, int end, boolean payloadStart)
            {
                if (payloadStart)
                {
                    finish();
                    length = 0;
                }
                int amount = end - start;
                if (amount <= 0 || length + amount > MAX_PES_BYTES)
                {
                    if (length + amount > MAX_PES_BYTES) reset();
                    return;
                }
                if (length + amount > bytes.length)
                    bytes = Arrays.copyOf(bytes, Math.min(MAX_PES_BYTES,
                            Math.max(length + amount, bytes.length * 2)));
                System.arraycopy(source, start, bytes, length, amount);
                length += amount;
                int declared = declaredPesLength(bytes, length);
                if (declared > 0 && length >= declared) finish();
            }

            private void finish()
            {
                if (length > 0) parsePes(pid, bytes, length);
                length = 0;
            }

            private void reset() { length = 0; }
        }
    }

    private static final class PageState
    {
        private final Service service;
        private final String[] rows = new String[24];
        private int currentPage = -1;
        private long lastPts = -1L;

        private PageState(Service service)
        {
            this.service = service;
            clearRows();
        }

        private void clear()
        {
            currentPage = -1;
            lastPts = -1L;
            clearRows();
        }

        private void clearRows() { Arrays.fill(rows, ""); }

        private String text()
        {
            StringBuilder text = new StringBuilder();
            for (int row = 1; row <= 23; row++)
            {
                if (rows[row].length() == 0) continue;
                if (text.length() > 0) text.append('\n');
                text.append(rows[row]);
            }
            return text.toString();
        }
    }

    private static final class SectionAssembler
    {
        private byte[] section = new byte[4096];
        private int length;
        private int expected = -1;

        private List<byte[]> consume(byte[] source, int start, int end, boolean payloadStart)
        {
            List<byte[]> result = new ArrayList<byte[]>();
            int p = start;
            if (payloadStart)
            {
                if (p >= end) return result;
                int pointer = source[p++] & 0xff;
                int oldEnd = Math.min(end, p + pointer);
                if (length > 0) append(source, p, oldEnd, result);
                p = oldEnd;
                if (length > 0) reset();
            }
            append(source, p, end, result);
            return result;
        }

        private void append(byte[] source, int start, int end, List<byte[]> result)
        {
            for (int p = start; p < end; p++)
            {
                if (length == 0 && (source[p] & 0xff) == 0xff) return;
                if (length >= section.length) { reset(); return; }
                section[length++] = source[p];
                if (length == 3)
                {
                    expected = 3 + (((section[1] & 15) << 8) | (section[2] & 0xff));
                    if (expected < 3 || expected > section.length) { reset(); return; }
                }
                if (expected > 0 && length == expected)
                {
                    result.add(Arrays.copyOf(section, length));
                    reset();
                }
            }
        }

        private void reset() { length = 0; expected = -1; }
    }

    private static final int[] HAMMING = {
            0xA8, 0x0B, 0x26, 0x85, 0x92, 0x31, 0x1C, 0xBF,
            0x40, 0xE3, 0xCE, 0x6D, 0x7A, 0xD9, 0xF4, 0x57
    };

    private static int unham(int value)
    {
        int best = -1;
        int distance = 9;
        for (int nibble = 0; nibble < HAMMING.length; nibble++)
        {
            int d = Integer.bitCount((value & 0xff) ^ HAMMING[nibble]);
            if (d < distance) { distance = d; best = nibble; }
        }
        return distance <= 1 ? best : -1;
    }

    private static String decodeText(byte[] bytes, int start, int length)
    {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            int value = reverse8(bytes[start + i] & 0xff) & 0x7f;
            // Level-1 spacing attributes occupy a character cell.
            result.append(value >= 0x20 && value < 0x7f ? (char) value : ' ');
        }
        int first = 0;
        int last = result.length();
        while (first < last && result.charAt(first) == ' ') first++;
        while (last > first && result.charAt(last - 1) == ' ') last--;
        return result.substring(first, last);
    }

    private static int reverse8(int value)
    {
        value = ((value >>> 1) & 0x55) | ((value << 1) & 0xaa);
        value = ((value >>> 2) & 0x33) | ((value << 2) & 0xcc);
        return (((value >>> 4) & 0x0f) | ((value << 4) & 0xf0)) & 0xff;
    }

    private static int reverse4(int value)
    {
        return ((value & 1) << 3) | ((value & 2) << 1)
                | ((value & 4) >>> 1) | ((value & 8) >>> 3);
    }

    private static int[] findSync(byte[] bytes, int start)
    {
        int[] sizes = {188, 192, 204};
        for (int p = Math.max(0, start); p < bytes.length; p++)
        {
            if ((bytes[p] & 0xff) != 0x47) continue;
            for (int size : sizes)
            {
                if (p + size * 3 < bytes.length
                        && (bytes[p + size] & 0xff) == 0x47
                        && (bytes[p + size * 2] & 0xff) == 0x47
                        && (bytes[p + size * 3] & 0xff) == 0x47)
                    return new int[]{p, size};
            }
        }
        return null;
    }

    private static byte[] tail(byte[] bytes, int maximum)
    {
        int length = Math.min(bytes.length, maximum);
        return Arrays.copyOfRange(bytes, bytes.length - length, bytes.length);
    }

    private static boolean isVideoType(int type)
    {
        return type == 1 || type == 2 || type == 0x10 || type == 0x1b
                || type == 0x20 || type == 0x24 || type == 0xea;
    }

    private static int declaredPesLength(byte[] bytes, int length)
    {
        if (length < 6 || bytes[0] != 0 || bytes[1] != 0 || bytes[2] != 1) return -1;
        int payload = ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff);
        return payload == 0 ? -1 : payload + 6;
    }

    private static long pesPts(byte[] bytes, int start, int end)
    {
        if (start + 14 > end || bytes[start] != 0 || bytes[start + 1] != 0
                || bytes[start + 2] != 1 || (bytes[start + 7] & 0x80) == 0) return -1L;
        return parsePts(bytes, start + 9, end);
    }

    private static long parsePts(byte[] bytes, int p, int end)
    {
        if (p < 0 || p + 5 > end) return -1L;
        return ((long) (bytes[p] & 0x0e) << 29)
                | ((long) (bytes[p + 1] & 0xff) << 22)
                | ((long) (bytes[p + 2] & 0xfe) << 14)
                | ((long) (bytes[p + 3] & 0xff) << 7)
                | ((long) (bytes[p + 4] & 0xfe) >> 1);
    }

    private static long ptsDelta(long value, long origin)
    {
        long delta = value - origin;
        long wrap = 1L << 33;
        if (delta < -(wrap / 2)) delta += wrap;
        else if (delta > wrap / 2) delta -= wrap;
        return Math.max(0L, delta);
    }

    private static String ascii(byte[] bytes, int start, int length)
    {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            int value = bytes[start + i] & 0xff;
            text.append(value >= 0x20 && value <= 0x7e ? (char) value : '?');
        }
        return text.toString();
    }
}
