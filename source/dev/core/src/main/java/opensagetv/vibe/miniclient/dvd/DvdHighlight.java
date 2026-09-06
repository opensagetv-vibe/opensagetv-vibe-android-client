package opensagetv.vibe.miniclient.dvd;

/** Immediate button-highlight state sent by SageTV's SPUCTRL command. */
public final class DvdHighlight {
    public final boolean visible;
    public final int x1;
    public final int y1;
    public final int x2;
    public final int y2;
    public final int paletteWord;

    public DvdHighlight(boolean visible, int x1, int y1, int x2, int y2, int paletteWord) {
        this.visible = visible;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.paletteWord = paletteWord;
    }

    public static DvdHighlight fromLegacy(int x1, int x2, int y1, int y2, int paletteWord) {
        boolean hidden = x1 == 0 && x2 == 0 && y1 == 0 && y2 == 0 && paletteWord == 0;
        return hidden ? hidden() : new DvdHighlight(x2 >= x1 && y2 >= y1, x1, y1, x2, y2, paletteWord);
    }

    public static DvdHighlight hidden() {
        return new DvdHighlight(false, 0, 0, -1, -1, 0);
    }

    public boolean contains(int x, int y) {
        return visible && x >= x1 && x <= x2 && y >= y1 && y <= y2;
    }
}

