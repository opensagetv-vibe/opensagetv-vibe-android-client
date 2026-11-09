package opensagetv.vibe.miniclient.android.video.ijkplayer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import opensagetv.vibe.miniclient.video.CaptionClockCadence;
import opensagetv.vibe.miniclient.video.LegacyExtenderCaptionBridge;
import opensagetv.vibe.miniclient.video.NativeCeaSampleGate;

/** One native player lease. Raw bytes go to stock event225, never timed-text. */
final class IjkCaptionSession
{
    static final int NATIVE_EVENT = 12062;
    interface Clock {
        boolean current();
        boolean playing();
        long positionMs();
    }
    private final NativeCeaSampleGate gate = new NativeCeaSampleGate();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor executor;
    private final Clock clock;
    private final LegacyExtenderCaptionBridge.Sink output;
    private volatile Epoch epoch;
    private boolean closed, scheduled, worker;
    private long pendingClock = -1;
    private boolean pendingFlush;
    private long retiredSamples, retiredBytes;

    private final class Epoch {
        final LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(
                new LegacyExtenderCaptionBridge.Sink() {
            @Override public void postSubtitleInfo(long pts, long duration, byte[] data, int flags) {
                if (epoch == Epoch.this && clock.current())
                    output.postSubtitleInfo(pts, duration, data, flags);
            }
        });
    }

    IjkCaptionSession(Executor executor, Clock clock, LegacyExtenderCaptionBridge.Sink output)
    {
        this.executor = executor;
        this.clock = clock;
        this.output = output;
        epoch = new Epoch();
    }

    synchronized boolean sample(Bundle args)
    {
        if (closed || args == null || !clock.current()) return false;
        byte[] bytes = args.getByteArray("cc_data");
        long timeUs = args.getLong("time_us", -1L);
        int previous608 = gate.index608(), previous708 = gate.index708();
        if (!gate.accept(args.getInt("version", -1), args.getInt("serial", -1), timeUs, bytes))
            return false;
        epoch.bridge.onCeaSample(timeUs, bytes);
        if (!scheduled) { scheduled = true; handler.post(tick); }
        return previous608 != gate.index608() || previous708 != gate.index708();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            synchronized (IjkCaptionSession.this) {
                if (closed || !clock.current()) { scheduled = false; return; }
                pendingClock = Math.max(0L, clock.positionMs()) * 1000L;
                scheduleWorker();
                handler.postDelayed(this, CaptionClockCadence.delayMs(clock.playing(), true));
            }
        }
    };

    // One consumer coalesces clocks and serializes FLUSH before new samples.
    // No network operation runs on the player/UI/native decode thread.
    private synchronized void scheduleWorker()
    {
        if (closed || worker) return;
        worker = true;
        try { executor.execute(new Runnable() {
            @Override public void run() {
                try {
                    while (true) {
                        Epoch selected;
                        long position;
                        boolean flush;
                        synchronized (IjkCaptionSession.this) {
                            if (closed || (pendingClock < 0 && !pendingFlush)) return;
                            selected = epoch; position = pendingClock; flush = pendingFlush;
                            pendingClock = -1; pendingFlush = false;
                        }
                        if (flush) selected.bridge.postFlush();
                        if (position >= 0 && selected == epoch) selected.bridge.drainTo(position);
                    }
                } finally {
                    synchronized (IjkCaptionSession.this) {
                        worker = false;
                        if (!closed && (pendingClock >= 0 || pendingFlush)) scheduleWorker();
                    }
                }
            }
        }); }
        catch (RejectedExecutionException stopped) { worker = false; }
    }

    synchronized void beginSeek()
    {
        gate.beginSeek();
        retiredSamples += epoch.bridge.getForwardedSamples();
        retiredBytes += epoch.bridge.getForwardedBytes();
        epoch.bridge.clearPending();
        epoch = new Epoch();
        pendingClock = -1;
        pendingFlush = true;
        scheduleWorker();
    }

    synchronized void completeSeek() { gate.completeSeek(); }
    boolean observed608() { return gate.index608() >= 0; }
    boolean observed708() { return gate.index708() >= 0; }
    int index608() { return gate.index608(); }
    int index708() { return gate.index708(); }
    synchronized int forwardedSamples() {
        return (int) Math.min(Integer.MAX_VALUE, retiredSamples
                + (epoch == null ? 0 : epoch.bridge.getForwardedSamples()));
    }
    synchronized long forwardedBytes() {
        return retiredBytes + (epoch == null ? 0 : epoch.bridge.getForwardedBytes());
    }
    boolean active() {
        Epoch selected = epoch;
        return selected != null && selected.bridge.isForwardingCurrentStream();
    }
    synchronized void close()
    {
        closed = true;
        gate.close();
        handler.removeCallbacks(tick);
        epoch.bridge.clearPending();
        epoch = null;
    }
}
