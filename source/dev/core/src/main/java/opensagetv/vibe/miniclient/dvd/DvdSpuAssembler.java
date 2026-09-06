package opensagetv.vibe.miniclient.dvd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded per-substream reassembler for SPU packets spanning PES packets. */
public final class DvdSpuAssembler {
    private static final int STREAM_COUNT = 32;
    private static final int MAX_SPU_PACKET = 0xffff;
    private static final int MAX_PENDING_PER_STREAM = 128 * 1024;
    private static final int MAX_PTS_MARKERS = 8192;

    private final StreamBuffer[] streams = new StreamBuffer[STREAM_COUNT];
    private final DvdDiagnostics diagnostics;

    public DvdSpuAssembler(DvdDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
        for (int i = 0; i < streams.length; i++) streams[i] = new StreamBuffer();
    }

    public synchronized List<DvdSpuPacket> feed(DvdSpuPacket fragment) {
        ArrayList<DvdSpuPacket> out = new ArrayList<DvdSpuPacket>();
        if (fragment == null || fragment.data == null || fragment.data.length == 0) return out;
        int index = fragment.streamId - 0x20;
        if (index < 0 || index >= STREAM_COUNT) return out;

        StreamBuffer stream = streams[index];
        if (stream.size + fragment.data.length > MAX_PENDING_PER_STREAM) {
            diagnostics.malformedSpuPackets++;
            stream.reset();
            if (fragment.data.length > MAX_PENDING_PER_STREAM) return out;
        }
        stream.append(fragment.data, fragment.pts90k);

        int cursor = 0;
        while (stream.size - cursor >= 2) {
            int packetSize = DvdByteUtil.u16be(stream.data, cursor);
            if (packetSize < 4 || packetSize > MAX_SPU_PACKET) {
                diagnostics.malformedSpuPackets++;
                cursor++;
                continue;
            }
            if (stream.size - cursor < packetSize) break;

            byte[] packet = Arrays.copyOfRange(stream.data, cursor, cursor + packetSize);
            out.add(new DvdSpuPacket(fragment.streamId, stream.ptsAt(cursor), packet));
            diagnostics.completedSpuPackets++;
            cursor += packetSize;
        }
        stream.compact(cursor);
        return out;
    }

    public synchronized void resetStream(int physicalStreamId) {
        int index = physicalStreamId - 0x20;
        if (index >= 0 && index < streams.length) streams[index].reset();
    }

    public synchronized void reset() {
        for (StreamBuffer stream : streams) stream.reset();
    }

    public synchronized int pendingBytes() {
        int total = 0;
        for (StreamBuffer stream : streams) total += stream.size;
        return total;
    }

    private static final class PtsMarker {
        int offset;
        final long pts90k;

        PtsMarker(int offset, long pts90k) {
            this.offset = offset;
            this.pts90k = pts90k;
        }
    }

    private static final class StreamBuffer {
        byte[] data = new byte[4096];
        int size;
        final ArrayList<PtsMarker> markers = new ArrayList<PtsMarker>();

        void append(byte[] source, long pts90k) {
            if (pts90k >= 0) addMarker(size, pts90k);
            ensure(size + source.length);
            System.arraycopy(source, 0, data, size, source.length);
            size += source.length;
        }

        void addMarker(int offset, long pts90k) {
            int last = markers.size() - 1;
            if (last >= 0) {
                PtsMarker marker = markers.get(last);
                if (marker.offset == offset) {
                    markers.set(last, new PtsMarker(offset, pts90k));
                    return;
                }
                if (marker.pts90k == pts90k) return;
            }
            markers.add(new PtsMarker(offset, pts90k));
            // Preserve the oldest anchor and newest markers under pathological input.
            if (markers.size() > MAX_PTS_MARKERS) markers.remove(1);
        }

        long ptsAt(int offset) {
            long value = -1;
            for (PtsMarker marker : markers) {
                if (marker.offset > offset) break;
                value = marker.pts90k;
            }
            return value;
        }

        void ensure(int needed) {
            if (needed <= data.length) return;
            int capacity = data.length;
            while (capacity < needed) capacity = Math.min(MAX_PENDING_PER_STREAM, capacity * 2);
            data = Arrays.copyOf(data, capacity);
        }

        void compact(int consumed) {
            if (consumed <= 0) return;
            if (consumed >= size) {
                size = 0;
                markers.clear();
                return;
            }
            System.arraycopy(data, consumed, data, 0, size - consumed);
            size -= consumed;

            PtsMarker anchor = null;
            ArrayList<PtsMarker> remaining = new ArrayList<PtsMarker>();
            for (PtsMarker marker : markers) {
                if (marker.offset <= consumed) {
                    anchor = marker;
                } else {
                    remaining.add(new PtsMarker(marker.offset - consumed, marker.pts90k));
                }
            }
            markers.clear();
            if (anchor != null) markers.add(new PtsMarker(0, anchor.pts90k));
            for (PtsMarker marker : remaining) {
                if (!markers.isEmpty() && marker.offset == 0) {
                    markers.set(markers.size() - 1, marker);
                } else {
                    markers.add(marker);
                }
            }
        }

        void reset() {
            size = 0;
            markers.clear();
        }
    }
}

