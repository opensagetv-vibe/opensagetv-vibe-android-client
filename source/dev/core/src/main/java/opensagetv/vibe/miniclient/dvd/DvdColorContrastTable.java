package opensagetv.vibe.miniclient.dvd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Region-specific palette changes from the DVD SPU CHG_COLCON (0x07) command.
 *
 * Each line region contains one to eight horizontal change points. At a change
 * point, the supplied 32-bit palette/alpha word replaces the normal mapping.
 */
public final class DvdColorContrastTable {
    public static final DvdColorContrastTable EMPTY = new DvdColorContrastTable(Collections.<LineRegion>emptyList());

    public static final class PixelChange {
        public final int left;
        public final int paletteWord;

        public PixelChange(int left, int paletteWord) {
            this.left = left;
            this.paletteWord = paletteWord;
        }
    }

    public static final class LineRegion {
        public final int top;
        public final int bottom;
        public final List<PixelChange> changes;

        public LineRegion(int top, int bottom, List<PixelChange> changes) {
            this.top = top;
            this.bottom = bottom;
            ArrayList<PixelChange> sorted = new ArrayList<PixelChange>(changes);
            Collections.sort(sorted, new Comparator<PixelChange>() {
                @Override
                public int compare(PixelChange a, PixelChange b) {
                    return Integer.compare(a.left, b.left);
                }
            });
            this.changes = Collections.unmodifiableList(sorted);
        }

        int paletteWordAt(int x) {
            int word = -1;
            for (PixelChange change : changes) {
                if (change.left > x) break;
                word = change.paletteWord;
            }
            return word;
        }
    }

    private final List<LineRegion> regions;

    public DvdColorContrastTable(List<LineRegion> regions) {
        this.regions = Collections.unmodifiableList(new ArrayList<LineRegion>(regions));
    }

    public List<LineRegion> regions() {
        return regions;
    }

    public boolean isEmpty() {
        return regions.isEmpty();
    }

    /** Returns -1 when the normal SPU palette applies. */
    public int paletteWordAt(int x, int y) {
        for (LineRegion region : regions) {
            if (y >= region.top && y <= region.bottom) {
                return region.paletteWordAt(x);
            }
        }
        return -1;
    }

    static DvdColorContrastTable parse(byte[] data, int start, int end) throws DvdProtocolException {
        ArrayList<LineRegion> regions = new ArrayList<LineRegion>();
        int p = start;
        while (p < end) {
            if (p + 4 > end) {
                throw new DvdProtocolException("Truncated CHG_COLCON line header");
            }
            long code = DvdByteUtil.u32be(data, p);
            if (code == 0x0fffffffL) break;

            int changeCount = DvdByteUtil.clamp(DvdByteUtil.u8(data, p + 2) >>> 4, 1, 8);
            int top = ((DvdByteUtil.u8(data, p) & 0x03) << 8) | DvdByteUtil.u8(data, p + 1);
            int bottom = ((DvdByteUtil.u8(data, p + 2) & 0x03) << 8) | DvdByteUtil.u8(data, p + 3);
            p += 4;

            if (bottom < top) {
                throw new DvdProtocolException("Invalid CHG_COLCON vertical region");
            }
            if (p + changeCount * 6 > end) {
                throw new DvdProtocolException("Truncated CHG_COLCON pixel controls");
            }

            ArrayList<PixelChange> changes = new ArrayList<PixelChange>(changeCount);
            for (int i = 0; i < changeCount; i++) {
                int left = ((DvdByteUtil.u8(data, p) & 0x03) << 8) | DvdByteUtil.u8(data, p + 1);
                int paletteWord = DvdByteUtil.i32be(data, p + 2);
                changes.add(new PixelChange(left, paletteWord));
                p += 6;
            }
            regions.add(new LineRegion(top, bottom, changes));
        }
        return regions.isEmpty() ? EMPTY : new DvdColorContrastTable(regions);
    }
}

