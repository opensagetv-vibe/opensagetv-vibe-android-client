package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackFailureClassifierTest
{
    @Test
    public void datasourceFailureReattachesWithoutDecoderExclusion()
    {
        PlaybackFailureClassifier.Decision result = classify(
                "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", "IOException", "socket", false, false, false, false);
        assertEquals(PlaybackFailureClassifier.Kind.DATASOURCE, result.kind);
        assertEquals(PlaybackFailureClassifier.Recovery.REATTACH_SOURCE, result.recovery);
        assertFalse(result.mayExcludeVideoDecoder);
        assertEquals(2, result.maxAutomaticAttempts);
    }

    @Test
    public void parserFailureDuringSeekIsSilentButRecoverable()
    {
        PlaybackFailureClassifier.Decision result = classify(
                "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED", "ParserException", "", true, true, false, false);
        assertEquals(PlaybackFailureClassifier.Kind.CONTAINER, result.kind);
        assertEquals(PlaybackFailureClassifier.Recovery.REATTACH_SOURCE, result.recovery);
        assertTrue(result.transitionStale);
        assertFalse(result.showToUser);
        assertFalse(result.mayExcludeVideoDecoder);
    }

    @Test
    public void parserFailureAtStartupRemainsVisible()
    {
        PlaybackFailureClassifier.Decision result = classify(
                "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED", "ParserException", "", false, false, false, false);
        assertEquals(PlaybackFailureClassifier.Kind.CONTAINER, result.kind);
        assertTrue(result.showToUser);
    }

    @Test
    public void initializationFailureDoesNotRepeatExhaustedCandidateList()
    {
        PlaybackFailureClassifier.Decision result = classify(
                "ERROR_CODE_DECODER_INIT_FAILED", "DecoderInitializationException", "", false, false, false, false);
        assertEquals(PlaybackFailureClassifier.Kind.DECODER_INITIALIZATION, result.kind);
        assertEquals(PlaybackFailureClassifier.Recovery.NONE, result.recovery);
        assertFalse(result.mayExcludeVideoDecoder);
    }

    @Test
    public void fatalRuntimeCodecFailureMayUseSessionExclusion()
    {
        PlaybackFailureClassifier.Decision result = classify(
                "ERROR_CODE_DECODING_FAILED", "MediaCodec$CodecException", "fatal", true, false, false, false);
        assertEquals(PlaybackFailureClassifier.Kind.DECODER_RUNTIME, result.kind);
        assertEquals(PlaybackFailureClassifier.Recovery.REBUILD_PLAYER_WITH_DECODER_EXCLUSION, result.recovery);
        assertTrue(result.mayExcludeVideoDecoder);
        assertEquals(1, result.maxAutomaticAttempts);
    }

    @Test
    public void audioTrackFailureNeverExcludesVideoDecoder()
    {
        PlaybackFailureClassifier.Decision result = classify(
                "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED", "AudioTrack$WriteException", "", true, false, false, false);
        assertEquals(PlaybackFailureClassifier.Kind.AUDIO_OUTPUT, result.kind);
        assertEquals(PlaybackFailureClassifier.Recovery.REPREPARE_PLAYER, result.recovery);
        assertFalse(result.mayExcludeVideoDecoder);
    }

    @Test
    public void teardownSuppressesAndDisablesRecovery()
    {
        PlaybackFailureClassifier.Decision result = classify(
                "ERROR_CODE_DECODING_FAILED", "MediaCodec$CodecException", "", true, false, false, true);
        assertEquals(PlaybackFailureClassifier.Recovery.NONE, result.recovery);
        assertFalse(result.showToUser);
        assertFalse(result.mayExcludeVideoDecoder);
    }

    private static PlaybackFailureClassifier.Decision classify(String code,
            String causeClass, String message, boolean rendered, boolean seek,
            boolean flush, boolean teardown)
    {
        return PlaybackFailureClassifier.classify(code, causeClass, message,
                rendered, seek, flush, teardown);
    }
}
