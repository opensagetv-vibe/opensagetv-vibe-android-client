package opensagetv.vibe.miniclient.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackPreferencePolicyTest
{
    @Test
    public void normalizesAndMatchesBcp47LanguageTags()
    {
        assertEquals("en-us", TrackPreferencePolicy.normalizeLanguage(" EN_us "));
        assertTrue(TrackPreferencePolicy.languageMatches("en", "en-US"));
        assertFalse(TrackPreferencePolicy.languageMatches("spa", "eng"));
        assertTrue(TrackPreferencePolicy.isValidLanguage("pt-BR"));
        assertFalse(TrackPreferencePolicy.isValidLanguage("not a language"));
    }

    @Test
    public void selectsRequestedCaptionStandardAndService()
    {
        SubtitleTrack[] tracks = new SubtitleTrack[] {
                new SubtitleTrack(0, SubtitleCodec.CEA608, "eng", "", true, 1),
                new SubtitleTrack(1, SubtitleCodec.CEA708, "eng", "", true, 1),
                new SubtitleTrack(2, SubtitleCodec.CEA708, "spa", "", true, 2)
        };
        assertEquals(2, TrackPreferencePolicy.findPreferredSubtitleTrack(
                tracks, "es", "cea708", 2));
        assertEquals(0, TrackPreferencePolicy.findPreferredSubtitleTrack(
                tracks, "eng", "cea608", 1));
    }

    @Test
    public void safelyFallsBackAndIgnoresUnsupportedTracks()
    {
        SubtitleTrack[] tracks = new SubtitleTrack[] {
                new SubtitleTrack(4, SubtitleCodec.CEA708, "eng", "", false, 1),
                new SubtitleTrack(7, SubtitleCodec.SUBRIP, "", "", true)
        };
        assertEquals(7, TrackPreferencePolicy.findPreferredSubtitleTrack(
                tracks, "fra", "cea608", 4));
        assertEquals(1, TrackPreferencePolicy.normalizeCaptionService("cea608", 55));
        assertEquals(55, TrackPreferencePolicy.normalizeCaptionService("cea708", 55));
        assertEquals(1, TrackPreferencePolicy.parseCaptionService("cea708", "invalid"));
        assertTrue(TrackPreferencePolicy.isValidCaptionStandard("CEA-708"));
        assertFalse(TrackPreferencePolicy.isValidCaptionStandard("bogus"));
    }
}
