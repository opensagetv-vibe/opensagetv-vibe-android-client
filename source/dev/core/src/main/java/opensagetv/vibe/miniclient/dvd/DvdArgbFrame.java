package opensagetv.vibe.miniclient.dvd;

/** Ready-to-render straight-alpha ARGB overlay in DVD-video coordinates. */
public final class DvdArgbFrame {
    public final long serial;
    public final int x;
    public final int y;
    public final int width;
    public final int height;
    public final int[] argb;
    public final long event90k;
    public final boolean display;

    public DvdArgbFrame(long serial, int x, int y, int width, int height,
                        int[] argb, long event90k, boolean display) {
        this.serial = serial;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.argb = argb;
        this.event90k = event90k;
        this.display = display;
    }
}

