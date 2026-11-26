package opensagetv.vibe.miniclient.dvd;

import opensagetv.vibe.miniclient.uibridge.Rectangle;

/** Authoritative DVD wire-session viewport, not renderer-instance state. */
public final class DvdViewportState {
    private Rectangle source, destination;
    public synchronized void set(Rectangle src, Rectangle dst) {
        if (src == null || dst == null) return;
        source = src.copy(); destination = dst.copy();
    }
    public synchronized Rectangle[] snapshot() {
        return source == null ? null : new Rectangle[]{source.copy(), destination.copy()};
    }
    public synchronized void clear() { source = destination = null; }
}
