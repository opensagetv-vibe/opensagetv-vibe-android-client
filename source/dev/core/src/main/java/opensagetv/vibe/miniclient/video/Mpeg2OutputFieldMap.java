package opensagetv.vibe.miniclient.video;

import java.util.ArrayDeque;

/** Optional, bounded source-picture PTS associations for explicit GPU output.
 * A codec can emit a WOVEN picture twice at half-frame PTS intervals. Output
 * cadence alone then cannot determine which source field belongs at that time.
 * This map never repairs timestamps or advances a clock. It only associates
 * the existing source sample timestamp with field zero/one, including B-picture
 * decode ordering and fractional frame periods. Unknown phase returns -1.
 */
public final class Mpeg2OutputFieldMap {
    private static final int LIMIT=2048;
    private final ArrayDeque<Long> pictures=new ArrayDeque<Long>();
    public synchronized void clear() { pictures.clear(); }
    public synchronized void note(long ptsUs) {
        if (ptsUs==Long.MIN_VALUE) return;
        if (pictures.size()==LIMIT) pictures.removeFirst();
        pictures.addLast(ptsUs);
    }
    public synchronized long pictureTime(long ptsUs,double codedFps) {
        if (Double.isNaN(codedFps) || Double.isInfinite(codedFps)
                || codedFps<23 || codedFps>31) return Long.MIN_VALUE;
        double half=500_000.0/codedFps;
        long chosen=Long.MIN_VALUE;
        for(long picture: pictures) {
            // Allow rounding at one-microsecond decoder/sample boundaries,
            // but never use a future frame to relabel a missing past field.
            if (picture<=ptsUs+2 && (chosen==Long.MIN_VALUE || picture>chosen)) chosen=picture;
        }
        if (chosen==Long.MIN_VALUE) return Long.MIN_VALUE;
        long delta=ptsUs-chosen;
        return Math.abs(delta)<=half*.10 || Math.abs(delta-half)<=half*.10 ? chosen : Long.MIN_VALUE;
    }
    public synchronized int phase(long ptsUs,double codedFps) {
        long chosen=pictureTime(ptsUs,codedFps);
        if(chosen==Long.MIN_VALUE)return -1;
        return ptsUs-chosen<250_000.0/codedFps ? 0 : 1;
    }
}
