package opensagetv.vibe.miniclient.android.video;

import android.graphics.Bitmap;

import java.util.Arrays;
import java.util.List;

import opensagetv.vibe.miniclient.dvd.DvdArgbFrame;
import opensagetv.vibe.miniclient.dvd.DvdDiagnostics;
import opensagetv.vibe.miniclient.dvd.DvdHighlight;
import opensagetv.vibe.miniclient.dvd.DvdSpuAssembler;
import opensagetv.vibe.miniclient.dvd.DvdSpuCompositor;
import opensagetv.vibe.miniclient.dvd.DvdSpuDecoder;
import opensagetv.vibe.miniclient.dvd.DvdSpuFrame;
import opensagetv.vibe.miniclient.dvd.DvdSpuPacket;

/**
 * Android adapter around the bounded, platform-neutral DVD SPU presentation
 * core. SageTV remains the DVD VM; this class only assembles, decodes and
 * renders the selected private-stream subpicture over the video surface.
 */
public final class DvdSubpictureDecoder
{
    public interface Listener
    {
        void onSubpicture(Bitmap bitmap, boolean visible, long presentationTimeUs,
                long eventGeneration);
    }

    private static final int WIDTH = 720;
    public static final long TIME_UNSET = Long.MIN_VALUE + 1;

    private final Listener listener;
    private final DvdDiagnostics diagnostics = new DvdDiagnostics();
    private final DvdSpuAssembler assembler = new DvdSpuAssembler(diagnostics);
    private final DvdSpuDecoder decoder = new DvdSpuDecoder(diagnostics);
    private final DvdSpuCompositor compositor = new DvdSpuCompositor();
    private final int[] clut = new int[16];

    private boolean clutValid;
    private DvdHighlight highlight = DvdHighlight.hidden();
    private DvdSpuFrame visibleFrame;
    private int selectedSubstreamId = 0x20;
    private boolean subpicturesDisabled;
    private long eventGeneration;
    private int canvasHeight = 480;

    public DvdSubpictureDecoder(Listener listener)
    {
        this.listener = listener;
    }

    public synchronized void reset()
    {
        eventGeneration++;
        assembler.reset();
        visibleFrame = null;
        highlight = DvdHighlight.hidden();
        recordHighlightDiagnostics();
        publish(null, false, TIME_UNSET, eventGeneration);
    }

    public synchronized void clearHighlight()
    {
        if (!highlight.visible)
            return;
        highlight = DvdHighlight.hidden();
        recordHighlightDiagnostics();
        recomposeImmediately();
    }

    public synchronized boolean isHighlightActive()
    {
        return highlight.visible;
    }

    public synchronized boolean isEventCurrent(long generation)
    {
        return generation == eventGeneration;
    }

    public synchronized void noteOverlayScheduled(long presentationTimeUs)
    {
        diagnostics.overlayEventsScheduled++;
        diagnostics.lastOverlayEventUs = presentationTimeUs;
    }

    public synchronized void noteOverlayApplied(long presentationTimeUs, long clockUs)
    {
        diagnostics.overlayEventsApplied++;
        diagnostics.lastOverlayEventUs = presentationTimeUs;
        diagnostics.lastOverlayClockUs = clockUs;
    }

    public synchronized void noteOverlayStale(long presentationTimeUs, long clockUs)
    {
        diagnostics.overlayEventsStale++;
        diagnostics.lastOverlayEventUs = presentationTimeUs;
        diagnostics.lastOverlayClockUs = clockUs;
    }

    public synchronized void setVideoHeight(int videoHeight)
    {
        int nextHeight = videoHeight > 480 ? 576 : 480;
        if (canvasHeight == nextHeight)
            return;
        canvasHeight = nextHeight;
        eventGeneration++;
        recomposeImmediately();
    }

    /** Selects the DVD SPU channel sent by MiniDVDPlayer.DVDStream(type=1). */
    public synchronized void setSubpictureStream(int streamPosition)
    {
        int previousStream = selectedSubstreamId;
        if (streamPosition == 62)
        {
            // Legacy PS_SUBPIC_DISABLE_STREAM. Retain the last physical stream
            // so authored menu button SPUs can still be composed.
            subpicturesDisabled = true;
        }
        else
        {
            // The HD MiniDVDPlayer VM sets bit 7 when title subpictures are off
            // and bit 6 when enabled; the low five bits select 0x20..0x3f.
            subpicturesDisabled = (streamPosition & 0x80) != 0
                    || (streamPosition & 0x40) == 0;
            selectedSubstreamId = 0x20 | (streamPosition & 0x1f);
        }
        if (selectedSubstreamId != previousStream)
            assembler.reset();
        visibleFrame = null;
        eventGeneration++;
        publish(null, false, TIME_UNSET, eventGeneration);
    }

    public synchronized void setClut(byte[] payload)
    {
        if (payload == null || payload.length < 64)
            return;
        boolean changed = !clutValid;
        for (int i = 0; i < 16; i++)
        {
            int value = readInt(payload, i * 4);
            changed |= clut[i] != value;
            clut[i] = value;
        }
        clutValid = true;
        // CLUT commands are presentation state, not a new SPU stream. DVD
        // servers repeat the same CLUT at cell/VOBU boundaries while the
        // extractor has already queued future subtitle events. Invalidating
        // their generation here made correctly decoded cues disappear before
        // reaching their PTS.
        if (changed)
            recomposeImmediately();
    }

    public synchronized void setHighlight(byte[] payload)
    {
        if (payload == null || payload.length < 20)
        {
            clearHighlight();
            return;
        }
        DvdHighlight next = DvdHighlight.fromLegacy(
                readInt(payload, 0), readInt(payload, 4),
                readInt(payload, 8), readInt(payload, 12),
                readInt(payload, 16));
        if (sameHighlight(highlight, next))
            return;
        highlight = next;
        recordHighlightDiagnostics();
        // A button change recomposes the currently retained authored menu
        // frame. It must not invalidate independently scheduled SPU events.
        recomposeImmediately();
    }

    private void recordHighlightDiagnostics()
    {
        diagnostics.highlightVisible = highlight.visible;
        diagnostics.highlightX1 = highlight.x1;
        diagnostics.highlightY1 = highlight.y1;
        diagnostics.highlightX2 = highlight.x2;
        diagnostics.highlightY2 = highlight.y2;
        diagnostics.highlightPaletteWord = highlight.paletteWord;
    }

    /** Adds one SPU fragment after its private-stream id. */
    public synchronized void pushFragment(int substreamId, byte[] data, int offset, int length,
            long presentationTimeUs)
    {
        if (substreamId < 0x20 || substreamId > 0x3f || data == null
                || offset < 0 || length <= 0 || offset + length > data.length)
            return;

        byte[] fragment = Arrays.copyOfRange(data, offset, offset + length);
        long pts90k = presentationTimeUs == TIME_UNSET
                ? -1L : presentationTimeUs * 90L / 1_000L;
        diagnostics.spuFragments++;
        List<DvdSpuPacket> packets = assembler.feed(
                new DvdSpuPacket(substreamId, pts90k, fragment));
        for (DvdSpuPacket packet : packets)
        {
            if (packet.streamId != selectedSubstreamId)
                continue;
            // The extractor normally delivers several future SPU events while
            // playback is buffered ahead.  Those events belong to the same
            // selected-stream generation and must remain independently
            // scheduled.  Incrementing here caused each later packet to make
            // an earlier, not-yet-presented subtitle look stale on the UI
            // thread.  Session/stream/highlight changes still increment the
            // generation and invalidate genuinely stale work.
            long generation = eventGeneration;
            for (DvdSpuFrame frame : decoder.decode(packet))
            {
                long eventTimeUs = frame.event90k < 0
                        ? TIME_UNSET : frame.event90k * 1_000_000L / 90_000L;
                if (!frame.display)
                {
                    // A DVD STP_DSP event ends the currently visible SPU.
                    // Keeping it would allow a later highlight update to
                    // resurrect a stale menu/subtitle bitmap.
                    if (visibleFrame == null || visibleFrame.streamId == frame.streamId
                            || frame.forced)
                        visibleFrame = null;
                    publish(null, false, eventTimeUs, generation);
                    continue;
                }
                // Retain the decoded frame even while title subtitles are off:
                // a later VM button highlight may need that same authored SPU.
                visibleFrame = frame;
                if (shouldRenderFrame())
                    publishFrame(frame, eventTimeUs, generation);
                else
                    publish(null, false, eventTimeUs, generation);
            }
        }
    }

    public synchronized void pushFragment(int substreamId, byte[] data, int offset, int length)
    {
        pushFragment(substreamId, data, offset, length, TIME_UNSET);
    }

    private boolean shouldRenderFrame()
    {
        // User/STV subtitle-off is authoritative even for a forced movie SPU.
        // An active server-VM menu highlight is the only bypass.
        return visibleFrame != null && (!subpicturesDisabled || highlight.visible);
    }

    private void recomposeImmediately()
    {
        if (shouldRenderFrame())
            publishFrame(visibleFrame, TIME_UNSET, eventGeneration);
        else
            publish(null, false, TIME_UNSET, eventGeneration);
    }

    private void publishFrame(DvdSpuFrame frame, long presentationTimeUs, long generation)
    {
        DvdArgbFrame composed = compositor.compose(frame, clut, clutValid, highlight);
        if (!composed.display || composed.width <= 0 || composed.height <= 0)
        {
            publish(null, false, presentationTimeUs, generation);
            return;
        }
        int[] canvas = new int[WIDTH * canvasHeight];
        long opaquePixels = 0;
        int copyLeft = Math.max(0, composed.x);
        int copyTop = Math.max(0, composed.y);
        int copyRight = Math.min(WIDTH, composed.x + composed.width);
        int copyBottom = Math.min(canvasHeight, composed.y + composed.height);
        for (int y = copyTop; y < copyBottom; y++)
        {
            int source = (y - composed.y) * composed.width + copyLeft - composed.x;
            int target = y * WIDTH + copyLeft;
            System.arraycopy(composed.argb, source, canvas, target, copyRight - copyLeft);
        }
        for (int pixel : composed.argb)
        {
            if ((pixel >>> 24) != 0)
                opaquePixels++;
        }
        diagnostics.lastOverlayOpaquePixels = opaquePixels;
        Bitmap bitmap = Bitmap.createBitmap(canvas, 0, WIDTH, WIDTH, canvasHeight,
                Bitmap.Config.ARGB_8888);
        diagnostics.overlaysPresented++;
        publish(bitmap, true, presentationTimeUs, generation);
    }

    private void publish(Bitmap bitmap, boolean visible, long presentationTimeUs,
            long generation)
    {
        if (!visible)
            diagnostics.overlaysCleared++;
        if (listener != null)
            listener.onSubpicture(bitmap, visible, presentationTimeUs, generation);
    }

    public synchronized DvdDiagnostics diagnosticsForDebug()
    {
        return diagnostics.copy();
    }

    private static int readInt(byte[] data, int offset)
    {
        return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
    }

    private static boolean sameHighlight(DvdHighlight left, DvdHighlight right)
    {
        return left.visible == right.visible
                && left.x1 == right.x1 && left.y1 == right.y1
                && left.x2 == right.x2 && left.y2 == right.y2
                && left.paletteWord == right.paletteWord;
    }
}
