package opensagetv.vibe.miniclient.video;

/** Pure decisions shared by the independently owned Android GPU output adapters. */
public final class GpuDeinterlacePolicy {
    private GpuDeinterlacePolicy() { }
    public static String filter(String value) {
        return "motion".equals(value) ? "motion" : "fast";
    }
    public static String parity(String value) {
        return "top".equals(value) || "bottom".equals(value) ? value : "auto";
    }
    public static boolean requested(String mode, boolean serverFiltered) {
        return "client_gpu".equals(mode) && !serverFiltered;
    }
    /** Allow one decoded field period, not the native several-frame burst.
     * A five-millisecond admission window was shorter than the backend's
     * ordinary render-loop wake interval and caused late SD field drops even
     * though the GPU draw itself took only a few milliseconds. Never hold a
     * codec buffer until after its deadline merely to avoid a texture burst.
     * Keep the lead bounded to one field (20ms PAL, ~16.68ms NTSC), with a
     * conservative finite startup value until actual coded FPS is known.
     * This is admission to the existing backend scheduler, not a new clock,
     * a promise of field retention, or a change to native Auto/Off playback.
     */
    public static long releaseLeadUs(float codedFps) {
        if (Float.isNaN(codedFps) || Float.isInfinite(codedFps) || codedFps <= 0)
            return 15_000L;
        return Math.max(1_000L, Math.min(20_000L, Math.round(500_000.0 / codedFps)));
    }
    /** Unknown/mixed/film/field-picture input is not proof of a woven frame. */
    public static boolean wovenMpeg2(String mime, String scan, long progressivePictures) {
        return "video/mpeg2".equals(mime)
                && "interlaced_sequence_interlaced_frames".equals(scan)
                && progressivePictures == 0;
    }
    /** Positive MPEG-2 evidence requiring native output, not a slow GPU copy.
     * Unknown startup metadata must not permanently disable an interlaced
     * stream before its sequence/picture headers have actually been observed.
     */
    public static String nativeBypassReason(String mime,String scan,long progressivePictures) {
        if(!"video/mpeg2".equals(mime))return "";
        if(progressivePictures>0 || "progressive_sequence".equals(scan)
                || "interlaced_sequence_progressive_frames".equals(scan)
                || "interlaced_sequence_telecine".equals(scan)
                || "interlaced_sequence_field_pictures".equals(scan))
            return "gpu_native_bypass_"+scan;
        return "";
    }
    /** The current shaders are qualified for MPEG-2 picture-header evidence.
     * Do not keep copying an AVC/HEVC stream through an unqualified GPU stage
     * while reporting that no deinterlacing is needed. Restore native output
     * and explicitly report the requested processing as unavailable. Unknown
     * startup MIME is not positive evidence and must wait for a real format.
     */
    public static String unsupportedInputReason(String mime) {
        return mime != null && !mime.isEmpty() && !"video/mpeg2".equals(mime)
                ? "gpu_input_video_mime_unsupported" : "";
    }
    public static int firstField(String parity, boolean observedTopFirst) {
        return "bottom".equals(parity) ? 1 : "top".equals(parity) ? 0
                : observedTopFirst ? 0 : 1;
    }
    /** Proven by per-field-ID pixel probes: MTK/API25 repeats WOVEN textures
     * at half-frame PTS intervals. It does not provide two separated fields.
     * Keep this distinction scoped; do not guess another vendor's geometry
     * from cadence, codec preference, or compressed interlace flags alone.
     */
    public static boolean duplicatedWovenOutput(int sdk,String mime,String decoder) {
        return sdk==25 && "video/mpeg2".equals(mime)
                && "OMX.MTK.VIDEO.DECODER.MPEG2".equalsIgnoreCase(decoder);
    }
    /** EGL producer pacing only, not frame count, PTS or playback speed.
     * The observed Shield Tube/API30/NVIDIA Tegra Frame path misses its coded
     * frame budget with interval1 while the otherwise equivalent interval0
     * Field path sustains ~60fps. Use that timestamp-scheduled producer path
     * only on this commissioned boundary and when EGL explicitly permits0.
     * Other devices retain their existing Frame/Field choices; this is not
     * a guessed codec option or a promise that every GPU supports the mode.
     */
    public static int swapInterval(boolean fieldOutput,int minimum,int sdk,
            String device,String renderer) {
        boolean shieldTube=sdk==30 && "sif".equals(device) && "NVIDIA Tegra".equals(renderer);
        return minimum==0 && (fieldOutput || shieldTube) ? 0 : 1;
    }
    /** The proven woven-pair route retains fields in GPU textures and submits
     * ONE per Choreographer tick, rather than bursting both into BufferQueue.
     * Avoid a second, blocking swap-interval wait on that same GL thread:
     * it can prevent the next tick/input callback from draining the bounded
     * queue. Require EGL's explicit interval0 support; unsupported devices
     * fail closed. This is not permission to emit both fields immediately.
     * Other existing single-output/native policies remain unchanged. Physical
     * per-field IDs, not submission counts, must qualify the queued path.
     */
    public static int swapInterval(boolean fieldOutput,int minimum,int maximum,int sdk,
            String device,String renderer,boolean provenWovenPair) {
        if(fieldOutput && provenWovenPair)return minimum==0 && maximum>=0 ? 0 : -1;
        int existing=swapInterval(fieldOutput,minimum,sdk,device,renderer);
        return existing>=minimum && existing<=maximum ? existing : -1;
    }
    /** Observed API25 MTK MPEG-2 GPU-output lifecycle only, not native Auto/Off. */
    public static boolean releaseBeforeTransition(int sdk,String mime,String decoder) {
        return sdk==25 && "video/mpeg2".equals(mime)
                && "OMX.MTK.VIDEO.DECODER.MPEG2".equalsIgnoreCase(decoder);
    }
    /** Main-thread display-layer refresh on actual GPU -> native handoff only.
     * On Tube/API30, NVIDIA MPEG-2 counters advance after retarget while HDMI
     * retains the first picture. Codec reinitialization ALONE also fails;
     * refreshing the SurfaceView layer restores the independent HDMI picture.
     * Preserve the proven MTK refresh boundary. Never broaden MTK's separate
     * codec-release policy to NVIDIA's ordinary seek/pause/native playback.
     */
    public static boolean refreshOnGpuReplacement(int sdk,String device,String mime,String decoder) {
        return releaseBeforeTransition(sdk,mime,decoder)
                || (sdk==30 && "sif".equals(device) && "video/mpeg2".equals(mime)
                    && "OMX.Nvidia.mpeg2v.decode".equals(decoder));
    }
    /** Do not manufacture a second field from already field-rate decoder output. */
    public static long secondFieldDelayNs(float codedFps, long incomingDeltaNs,
            float speed, float displayHz) {
        if (Float.isNaN(codedFps) || Float.isInfinite(codedFps) || codedFps < 23f || codedFps > 31f
                || Float.isNaN(speed) || Float.isInfinite(speed) || speed <= 0f || speed > 1.01f
                || displayHz + 0.5f < codedFps * 2f) return 0;
        long frameNs = Math.round(1_000_000_000.0 / codedFps / speed);
        // Require observed whole-frame delivery, not nominal track metadata.
        if (incomingDeltaNs < frameNs * 3 / 4 || incomingDeltaNs > frameNs * 5 / 4)
            return 0;
        return frameNs / 2;
    }
    /** A pixel-qualified woven texture contains its second ORIGINAL field.
     * The duplicated codec callback is not a second separated image. Only a
     * mapped first-field PTS may emit the pair; an already-late second-field
     * callback must not replay the first field or make a new clock anchor.
     */
    public static long wovenSecondFieldDelayNs(boolean proven,int phase,float fps,float speed,float displayHz) {
        return proven && phase==0 ? secondFieldDelayNs(fps,
                fps>0 ? Math.round(1_000_000_000.0/fps) : 0,speed,displayHz) : 0;
    }
    /** Stable decoder-cadence classification, never Surface callback spacing. */
    public static final class Cadence {
        private int wholeFrames, halfFrames;
        private boolean fieldRate;
        public void reset() { wholeFrames=halfFrames=0;fieldRate=false; }
        public boolean alreadyFieldRate() { return fieldRate; }
        public boolean allowSecondField(float codedFps,long metadataStepNs) {
            if(Float.isNaN(codedFps) || Float.isInfinite(codedFps) || codedFps<23f || codedFps>31f)return false;
            long whole=Math.round(1_000_000_000.0/codedFps),half=whole/2;
            if(metadataStepNs>=half*3/4 && metadataStepNs<=half*5/4) {
                if(++halfFrames>=2)fieldRate=true;
                wholeFrames=0;return false;
            }
            if(metadataStepNs>=whole*3/4 && metadataStepNs<=whole*5/4) {
                // A missed field yields one whole-frame gap even on a field-
                // rate producer. Once proven, keep that classification until
                // a real format/session discontinuity; never manufacture it.
                halfFrames=0;
                return !fieldRate && ++wholeFrames>=12;
            }
            wholeFrames=0;halfFrames=0;return false;
        }
    }

    /** Three sustained two-second submission deficits, not a slow startup or
     * a proof that every submitted field was actually scanned out. No polling.
     */
    public static final class PresentationBudget {
        private long startedNs=-1,startedFrames;
        private int slowWindows;
        private double lastRate;
        public double lastRate() {return lastRate;}
        public void reset() {startedNs=-1;startedFrames=0;slowWindows=0;lastRate=0;}
        public boolean belowBudget(long nowNs,long frames,float requiredFps) {
            if(Float.isNaN(requiredFps) || Float.isInfinite(requiredFps) || requiredFps<=0) {
                reset();return false;
            }
            if(startedNs<0 || nowNs<startedNs || frames<startedFrames) {
                startedNs=nowNs;startedFrames=frames;slowWindows=0;return false;
            }
            long elapsed=nowNs-startedNs;
            if(elapsed<2_000_000_000L)return false;
            double rate=(frames-startedFrames)*1_000_000_000.0/elapsed;
            lastRate=rate;
            slowWindows=rate<requiredFps*.90 ? slowWindows+1 : 0;
            startedNs=nowNs;startedFrames=frames;
            return slowWindows>=3;
        }
    }
}
