package opensagetv.vibe.miniclient.dvd;

/** Mutable counters intended for logs and telemetry, not synchronization. */
public final class DvdDiagnostics {
    public long psBytesDiscarded;
    public long malformedPsPackets;
    public long privateStreamPackets;
    public long spuFragments;
    public long malformedSpuPackets;
    public long completedSpuPackets;
    public long decodedSpuEvents;
    public long droppedSpuEvents;
    public long overlaysPresented;
    public long overlaysCleared;
    public long overlayEventsScheduled;
    public long overlayEventsApplied;
    public long overlayEventsStale;
    public long lastOverlayEventUs;
    public long lastOverlayClockUs;
    public long lastOverlayOpaquePixels;
    public boolean highlightVisible;
    public int highlightX1;
    public int highlightY1;
    public int highlightX2 = -1;
    public int highlightY2 = -1;
    public int highlightPaletteWord;
    public long drainRequests;
    public long drainAcks;

    public DvdDiagnostics copy() {
        DvdDiagnostics d = new DvdDiagnostics();
        d.psBytesDiscarded = psBytesDiscarded;
        d.malformedPsPackets = malformedPsPackets;
        d.privateStreamPackets = privateStreamPackets;
        d.spuFragments = spuFragments;
        d.malformedSpuPackets = malformedSpuPackets;
        d.completedSpuPackets = completedSpuPackets;
        d.decodedSpuEvents = decodedSpuEvents;
        d.droppedSpuEvents = droppedSpuEvents;
        d.overlaysPresented = overlaysPresented;
        d.overlaysCleared = overlaysCleared;
        d.overlayEventsScheduled = overlayEventsScheduled;
        d.overlayEventsApplied = overlayEventsApplied;
        d.overlayEventsStale = overlayEventsStale;
        d.lastOverlayEventUs = lastOverlayEventUs;
        d.lastOverlayClockUs = lastOverlayClockUs;
        d.lastOverlayOpaquePixels = lastOverlayOpaquePixels;
        d.highlightVisible = highlightVisible;
        d.highlightX1 = highlightX1;
        d.highlightY1 = highlightY1;
        d.highlightX2 = highlightX2;
        d.highlightY2 = highlightY2;
        d.highlightPaletteWord = highlightPaletteWord;
        d.drainRequests = drainRequests;
        d.drainAcks = drainAcks;
        return d;
    }
}
