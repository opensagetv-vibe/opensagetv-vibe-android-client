package opensagetv.vibe.miniclient.dvd;

/** Immutable presentation event containing a decoded 2-bit DVD SPU bitmap. */
public final class DvdSpuFrame {
    public final long serial;
    public final int streamId;
    public final long event90k;
    public final boolean display;
    public final boolean forced;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final byte[] pixels;
    public final int[] colorIndex;
    public final int[] alpha;
    public final DvdColorContrastTable colorContrastTable;

    public DvdSpuFrame(
            long serial, int streamId, long event90k, boolean display, boolean forced,
            int x, int y, int width, int height, byte[] pixels,
            int[] colorIndex, int[] alpha, DvdColorContrastTable colorContrastTable) {
        this.serial = serial;
        this.streamId = streamId;
        this.event90k = event90k;
        this.display = display;
        this.forced = forced;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.pixels = pixels == null ? new byte[0] : pixels;
        this.colorIndex = colorIndex.clone();
        this.alpha = alpha.clone();
        this.colorContrastTable = colorContrastTable == null ? DvdColorContrastTable.EMPTY : colorContrastTable;
    }

    public static DvdSpuFrame hidden(long serial, int streamId, long event90k, boolean forced) {
        return new DvdSpuFrame(serial, streamId, event90k, false, forced,
                0, 0, 0, 0, new byte[0], new int[4], new int[4], DvdColorContrastTable.EMPTY);
    }
}

