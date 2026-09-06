package opensagetv.vibe.miniclient.dvd;

/** Maps 33-bit MPEG PTS values, SageTV NEWCELL offsets, STC and player time. */
public final class DvdPtsClock {
    public static final long MODULO = 1L << 33;
    private static final long MASK = MODULO - 1;
    private static final long HALF = MODULO >>> 1;

    private long ptsOffset90k;
    private long currentClock90k = -1;
    private long lastMappedPts90k = -1;
    private long playerAnchorMs = Long.MIN_VALUE;
    private long clockAnchor90k = -1;
    private boolean anchorOnNextPlayerClock;

    public synchronized void onNewCell(int wireOffset45k) {
        ptsOffset90k = ((long) wireOffset45k) * 2L;
        lastMappedPts90k = -1;
        playerAnchorMs = Long.MIN_VALUE;
        clockAnchor90k = -1;
        anchorOnNextPlayerClock = currentClock90k >= 0;
    }

    public synchronized long getPtsOffset90k() {
        return ptsOffset90k;
    }

    public synchronized long mapPesPts(long rawPts90k) {
        if (rawPts90k < 0) return -1;
        long wrapped = wrap33(rawPts90k + ptsOffset90k);
        long reference = currentClock90k >= 0 ? currentClock90k : lastMappedPts90k;
        long mapped = reference >= 0 ? unwrapNear(wrapped, reference) : wrapped;
        lastMappedPts90k = mapped;
        return mapped;
    }

    /** SageTV's wire value is a 32-bit STC in 45-kHz units. */
    public synchronized long setServerStc45k(long unsignedWireValue) {
        long wrapped = (unsignedWireValue & 0xffffffffL) * 2L;
        wrapped &= MASK;
        currentClock90k = currentClock90k >= 0 ? unwrapNear(wrapped, currentClock90k) : wrapped;
        anchorOnNextPlayerClock = true;
        return currentClock90k;
    }

    /**
     * Anchors the player's millisecond timeline to the most recent server STC.
     * If no STC has been received, player time is treated as a zero-based 90-kHz clock.
     */
    public synchronized long updatePlayerClockMillis(long positionMs) {
        if (positionMs < 0) return currentClock90k;
        if (playerAnchorMs == Long.MIN_VALUE || anchorOnNextPlayerClock) {
            playerAnchorMs = positionMs;
            clockAnchor90k = currentClock90k >= 0 ? currentClock90k : positionMs * 90L;
            anchorOnNextPlayerClock = false;
        }
        currentClock90k = clockAnchor90k + (positionMs - playerAnchorMs) * 90L;
        return currentClock90k;
    }

    public synchronized long updateExactClock90k(long clock90k) {
        if (clock90k >= 0) {
            currentClock90k = clock90k;
            playerAnchorMs = Long.MIN_VALUE;
            clockAnchor90k = -1;
            anchorOnNextPlayerClock = false;
        }
        return currentClock90k;
    }

    public synchronized long currentClock90k() {
        return currentClock90k;
    }

    public synchronized void resetPresentationClock() {
        currentClock90k = -1;
        lastMappedPts90k = -1;
        playerAnchorMs = Long.MIN_VALUE;
        clockAnchor90k = -1;
        anchorOnNextPlayerClock = false;
    }

    public synchronized void resetAll() {
        ptsOffset90k = 0;
        resetPresentationClock();
    }

    public static long wrap33(long value) {
        return value & MASK;
    }

    public static long unwrapNear(long wrappedValue, long reference) {
        long wrappedReference = reference & MASK;
        long delta = wrappedValue - wrappedReference;
        if (delta > HALF) delta -= MODULO;
        else if (delta < -HALF) delta += MODULO;
        return reference + delta;
    }
}
