package opensagetv.vibe.miniclient.video;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DecoderAttemptTelemetryTest
{
    @Test
    public void recordsBoundedSessionStateAndSanitizesDelimiters()
    {
        DecoderAttemptTelemetry telemetry = new DecoderAttemptTelemetry();
        telemetry.recordCandidates("video/mpeg2", Arrays.asList("codec.one", "codec;two"));
        telemetry.recordSelectedVideo("codec.one");
        telemetry.recordSelectedAudio("ffmpeg\naudio");
        telemetry.recordExclusion("codec.one");
        telemetry.recordFallback("fatal_codec", "next_decoder");
        telemetry.recordCodecError("failure");
        telemetry.recordAudioUnderrun();
        telemetry.recordAudioOutputError("dead");
        telemetry.recordFormatChange("1920x1080");
        telemetry.recordTrackChange();

        assertEquals("video/mpeg2=codec.one,codec_two", telemetry.candidates());
        assertEquals("codec.one", telemetry.selectedVideo());
        assertEquals("ffmpeg_audio", telemetry.selectedAudio());
        assertEquals("codec.one", telemetry.exclusions());
        assertEquals(1, telemetry.codecErrorCount());
        assertEquals(1, telemetry.audioUnderrunCount());
        assertEquals(1, telemetry.audioOutputErrorCount());
        assertEquals(1, telemetry.formatChangeCount());
        assertEquals(1, telemetry.trackChangeCount());
        assertFalse(telemetry.events().contains("\n"));
    }

    @Test
    public void retainsOnlyTheLatestTwelveEvents()
    {
        DecoderAttemptTelemetry telemetry = new DecoderAttemptTelemetry();
        for (int i = 0; i < 20; i++) telemetry.recordCodecError("e" + i);
        assertFalse(telemetry.events().contains("codec-error:e0;"));
        assertTrue(telemetry.events().contains("codec-error:e19"));
    }

    @Test
    public void resetClearsThePlaybackSession()
    {
        DecoderAttemptTelemetry telemetry = new DecoderAttemptTelemetry();
        telemetry.recordSelectedVideo("codec");
        telemetry.recordExclusion("codec");
        telemetry.reset();
        assertEquals("", telemetry.selectedVideo());
        assertEquals("", telemetry.exclusions());
        assertEquals("", telemetry.events());
    }
}
