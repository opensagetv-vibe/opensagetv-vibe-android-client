package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class GpuDeinterlacePolicyTest {
    @Test public void displayTickPacedWovenPairRequiresNonblockingSwapSupport() {
        assertEquals(0,GpuDeinterlacePolicy.swapInterval(true,0,1,25,"mantis","PowerVR Rogue GE8300",true));
        assertEquals(0,GpuDeinterlacePolicy.swapInterval(true,0,0,25,"mantis","PowerVR Rogue GE8300",true));
        assertEquals(-1,GpuDeinterlacePolicy.swapInterval(true,1,1,25,"mantis","PowerVR Rogue GE8300",true));
        assertEquals(0,GpuDeinterlacePolicy.swapInterval(true,0,1,25,"mantis","PowerVR Rogue GE8300",false));
        assertEquals(0,GpuDeinterlacePolicy.swapInterval(false,0,1,30,"sif","NVIDIA Tegra",false));
    }
    @Test public void onlyProvenWovenFirstFieldMaySubmitItsRealSecondField() {
        assertEquals(20_000_000L,GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,0,25,1,50));
        assertEquals(0,GpuDeinterlacePolicy.wovenSecondFieldDelayNs(false,0,25,1,50));
        assertEquals(0,GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,1,25,1,50));
        assertEquals(0,GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,-1,25,1,50));
        assertEquals(0,GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,0,25,2,50));
        assertEquals(0,GpuDeinterlacePolicy.wovenSecondFieldDelayNs(true,0,25,1,30));
    }
    @Test public void wovenFieldRateEvidenceDoesNotApplyToOtherDecoders() {
        assertTrue(GpuDeinterlacePolicy.duplicatedWovenOutput(25,"video/mpeg2","OMX.MTK.VIDEO.DECODER.MPEG2"));
        assertFalse(GpuDeinterlacePolicy.duplicatedWovenOutput(30,"video/mpeg2","OMX.Nvidia.mpeg2v.decode"));
        assertFalse(GpuDeinterlacePolicy.duplicatedWovenOutput(25,"video/avc","OMX.MTK.VIDEO.DECODER.MPEG2"));
        assertFalse(GpuDeinterlacePolicy.duplicatedWovenOutput(23,"video/mpeg2","OMX.MTK.VIDEO.DECODER.MPEG2"));
    }
    @Test public void admissionWindowFitsOneFieldAndNeverCreatesANewClock() {
        assertEquals(20_000L, GpuDeinterlacePolicy.releaseLeadUs(25));
        assertEquals(16_683L, GpuDeinterlacePolicy.releaseLeadUs(30000f / 1001));
        assertEquals(8_333L, GpuDeinterlacePolicy.releaseLeadUs(60));
        assertEquals(20_000L, GpuDeinterlacePolicy.releaseLeadUs(1));
        assertEquals(1_000L, GpuDeinterlacePolicy.releaseLeadUs(1000));
        for (float unknown : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            assertEquals(15_000L, GpuDeinterlacePolicy.releaseLeadUs(unknown));
    }
    @Test public void unsupportedMimeRestoresNativeWithoutClaimingFilterSupport() {
        for (String mime : new String[]{"video/avc", "video/hevc", "video/av01"})
            assertEquals("gpu_input_video_mime_unsupported", GpuDeinterlacePolicy.unsupportedInputReason(mime));
        for (String unknownOrSupported : new String[]{null, "", "video/mpeg2"})
            assertEquals("", GpuDeinterlacePolicy.unsupportedInputReason(unknownOrSupported));
    }
    @Test public void onlyExplicitClientOwnsGpuProcessing() {
        assertTrue(GpuDeinterlacePolicy.requested("client_gpu", false));
        for (String mode : new String[]{"auto", "off", "client", "mim", "", null})
            assertFalse(GpuDeinterlacePolicy.requested(mode, false));
        assertFalse(GpuDeinterlacePolicy.requested("client_gpu", true));
    }
    @Test public void unknownProgressiveFilmMixedAndSeparatedFieldsBypass() {
        assertTrue(GpuDeinterlacePolicy.wovenMpeg2("video/mpeg2",
                "interlaced_sequence_interlaced_frames", 0));
        assertFalse(GpuDeinterlacePolicy.wovenMpeg2("video/avc",
                "interlaced_sequence_interlaced_frames", 0));
        assertFalse(GpuDeinterlacePolicy.wovenMpeg2("video/mpeg2",
                "interlaced_sequence_interlaced_frames", 1));
        for (String scan : new String[]{"unknown", "progressive_sequence",
                "interlaced_sequence_telecine", "interlaced_sequence_field_pictures"})
            assertFalse(GpuDeinterlacePolicy.wovenMpeg2("video/mpeg2", scan, 0));
    }
    @Test public void fieldOrderOverridesAndUnknownDefaultsAreBounded() {
        assertEquals(0, GpuDeinterlacePolicy.firstField("auto", true));
        assertEquals(1, GpuDeinterlacePolicy.firstField("auto", false));
        assertEquals(0, GpuDeinterlacePolicy.firstField("top", false));
        assertEquals(1, GpuDeinterlacePolicy.firstField("bottom", true));
        assertEquals("auto", GpuDeinterlacePolicy.parity("invalid"));
        assertEquals("fast", GpuDeinterlacePolicy.filter("invalid"));
    }
    @Test public void positiveProgressiveFilmOrSeparateFieldEvidenceRestoresNativeOutput() {
        for(String scan:new String[]{"progressive_sequence","interlaced_sequence_progressive_frames",
                "interlaced_sequence_telecine","interlaced_sequence_field_pictures"})
            assertEquals("gpu_native_bypass_"+scan,GpuDeinterlacePolicy.nativeBypassReason("video/mpeg2",scan,0));
        assertEquals("gpu_native_bypass_interlaced_sequence_interlaced_frames",
                GpuDeinterlacePolicy.nativeBypassReason("video/mpeg2","interlaced_sequence_interlaced_frames",1));
        for(String scan:new String[]{"unknown","interlaced_sequence","interlaced_sequence_interlaced_frames"})
            assertEquals("",GpuDeinterlacePolicy.nativeBypassReason("video/mpeg2",scan,0));
        assertEquals("",GpuDeinterlacePolicy.nativeBypassReason("video/avc","progressive_sequence",1));
    }
    @Test public void doubleOnlyObservedFrameRateAtSupportedDisplayAndNormalSpeed() {
        assertEquals(16_683_333L, GpuDeinterlacePolicy.secondFieldDelayNs(
                30000f / 1001, 33_366_667L, 1f, 59.94f), 10L);
        assertEquals(20_000_000L, GpuDeinterlacePolicy.secondFieldDelayNs(
                25f, 40_000_000L, 1f, 50f));
        for (long delta : new long[]{0, 16_683_333, 100_000_000})
            assertEquals(0, GpuDeinterlacePolicy.secondFieldDelayNs(29.97f, delta, 1f, 60f));
        assertEquals(0, GpuDeinterlacePolicy.secondFieldDelayNs(29.97f, 33_366_667, 2f, 60f));
        assertEquals(0, GpuDeinterlacePolicy.secondFieldDelayNs(29.97f, 33_366_667, 1f, 30f));
        assertEquals(0, GpuDeinterlacePolicy.secondFieldDelayNs(59.94f, 16_683_333, 1f, 60f));
        assertEquals(0, GpuDeinterlacePolicy.secondFieldDelayNs(Float.NaN, 33_366_667, 1f, 60f));
    }
    @Test public void aMissingNativeFieldNeverCreatesSyntheticSecondFields() {
        GpuDeinterlacePolicy.Cadence gate=new GpuDeinterlacePolicy.Cadence();
        for(int i=0;i<11;i++)assertFalse(gate.allowSecondField(29.97f,33_366_667));
        assertTrue(gate.allowSecondField(29.97f,33_366_667));
        gate.reset();
        assertFalse(gate.allowSecondField(29.97f,16_683_333));
        assertFalse(gate.allowSecondField(29.97f,16_683_333));
        assertTrue(gate.alreadyFieldRate());
        for(int i=0;i<50;i++)assertFalse(gate.allowSecondField(29.97f,33_366_667));
        gate.reset();
        assertFalse(gate.alreadyFieldRate());
    }
    @Test public void gpuFlushWorkaroundIsExactObservedCodecAndApiOnly() {
        assertTrue(GpuDeinterlacePolicy.releaseBeforeTransition(25,"video/mpeg2","OMX.MTK.VIDEO.DECODER.MPEG2"));
        for(int sdk:new int[]{23,24,26,33})
            assertFalse(GpuDeinterlacePolicy.releaseBeforeTransition(sdk,"video/mpeg2","OMX.MTK.VIDEO.DECODER.MPEG2"));
        assertFalse(GpuDeinterlacePolicy.releaseBeforeTransition(25,"video/avc","OMX.MTK.VIDEO.DECODER.MPEG2"));
        assertFalse(GpuDeinterlacePolicy.releaseBeforeTransition(25,"video/mpeg2","c2.amlogic.mpeg2.decoder"));
    }
    @Test public void tubeGpuReplacementDoesNotBroadenSeekOrNativeWorkarounds() {
        assertTrue(GpuDeinterlacePolicy.refreshOnGpuReplacement(30,"sif","video/mpeg2","OMX.Nvidia.mpeg2v.decode"));
        assertFalse(GpuDeinterlacePolicy.refreshOnGpuReplacement(30,"sif","video/avc","OMX.Nvidia.h264.decode"));
        for(int sdk:new int[]{25,29,31,34})
            assertFalse(GpuDeinterlacePolicy.refreshOnGpuReplacement(sdk,"sif","video/mpeg2","OMX.Nvidia.mpeg2v.decode"));
        for(String device:new String[]{"darcy","foster","mantis",null})
            assertFalse(GpuDeinterlacePolicy.refreshOnGpuReplacement(30,device,"video/mpeg2","OMX.Nvidia.mpeg2v.decode"));
        assertFalse(GpuDeinterlacePolicy.refreshOnGpuReplacement(30,"sif","video/avc","OMX.Nvidia.mpeg2v.decode"));
        assertFalse(GpuDeinterlacePolicy.refreshOnGpuReplacement(30,"sif","video/mpeg2","OMX.Nvidia.h264.decode"));
        assertTrue(GpuDeinterlacePolicy.refreshOnGpuReplacement(25,"mantis","video/mpeg2","OMX.MTK.VIDEO.DECODER.MPEG2"));
        assertFalse(GpuDeinterlacePolicy.releaseBeforeTransition(30,"video/mpeg2","OMX.Nvidia.mpeg2v.decode"));
        assertFalse(GpuDeinterlacePolicy.releaseBeforeTransition(30,"video/avc","OMX.Nvidia.h264.decode"));
    }
    @Test public void tubeFramePacingIsScopedAndEglCapabilityBounded() {
        assertEquals(0,GpuDeinterlacePolicy.swapInterval(false,0,30,"sif","NVIDIA Tegra"));
        assertEquals(1,GpuDeinterlacePolicy.swapInterval(false,1,30,"sif","NVIDIA Tegra"));
        for(int sdk:new int[]{23,25,29,31,34})
            assertEquals(1,GpuDeinterlacePolicy.swapInterval(false,0,sdk,"sif","NVIDIA Tegra"));
        for(String device:new String[]{"mantis","sti6140d360","darcy",null})
            assertEquals(1,GpuDeinterlacePolicy.swapInterval(false,0,30,device,"NVIDIA Tegra"));
        for(String renderer:new String[]{"Mali-G31","unknown",null})
            assertEquals(1,GpuDeinterlacePolicy.swapInterval(false,0,30,"sif",renderer));
        // Existing Field behavior is unchanged, including devices outside this
        // boundary. Frame cadence/second-field manufacture remains separate.
        assertEquals(0,GpuDeinterlacePolicy.swapInterval(true,0,25,"mantis","unknown"));
        assertEquals(1,GpuDeinterlacePolicy.swapInterval(true,1,30,"sif","NVIDIA Tegra"));
    }
    @Test public void sustainedSubmissionDeficitFailsButBriefSlowWindowDoesNot() {
        GpuDeinterlacePolicy.PresentationBudget budget=new GpuDeinterlacePolicy.PresentationBudget();
        assertFalse(budget.belowBudget(0,0,60));
        assertFalse(budget.belowBudget(2_000_000_000L,70,60));
        assertFalse(budget.belowBudget(4_000_000_000L,140,60));
        assertTrue(budget.belowBudget(6_000_000_000L,210,60));
        budget.reset();
        assertFalse(budget.belowBudget(0,0,60));
        assertFalse(budget.belowBudget(2_000_000_000L,70,60));
        assertFalse(budget.belowBudget(4_000_000_000L,190,60));
        assertFalse(budget.belowBudget(6_000_000_000L,260,60));
        assertFalse(budget.belowBudget(8_000_000_000L,330,60));
    }
    @Test public void budgetResetCounterDiscontinuityAndUnknownRateReanchor() {
        GpuDeinterlacePolicy.PresentationBudget budget=new GpuDeinterlacePolicy.PresentationBudget();
        assertFalse(budget.belowBudget(0,0,30));
        assertFalse(budget.belowBudget(2_000_000_000L,60,30));
        assertFalse(budget.belowBudget(4_000_000_000L,120,30));
        assertFalse(budget.belowBudget(6_000_000_000L,1,30));
        assertFalse(budget.belowBudget(8_000_000_000L,1,Float.NaN));
        assertFalse(budget.belowBudget(10_000_000_000L,2,30));
        budget.reset();
        assertFalse(budget.belowBudget(20_000_000_000L,2,30));
    }
}
