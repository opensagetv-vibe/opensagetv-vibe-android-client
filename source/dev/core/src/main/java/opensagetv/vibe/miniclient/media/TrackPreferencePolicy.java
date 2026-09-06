package opensagetv.vibe.miniclient.media;

import java.util.Locale;

/**
 * Host-testable policy for language and broadcast-caption track preferences.
 * Caption preferences select a track only after SageTV has enabled captions;
 * they never act as an independent caption on/off switch.
 */
public final class TrackPreferencePolicy
{
    public static final String CAPTION_STANDARD_AUTO = "auto";
    public static final String CAPTION_STANDARD_CEA608 = "cea608";
    public static final String CAPTION_STANDARD_CEA708 = "cea708";

    private TrackPreferencePolicy()
    {
    }

    public static String normalizeLanguage(String language)
    {
        if (language == null)
            return "";
        return language.trim().replace('_', '-').toLowerCase(Locale.US);
    }

    public static boolean isValidLanguage(String language)
    {
        String normalized = normalizeLanguage(language);
        return normalized.isEmpty()
                || normalized.matches("[a-z]{2,8}(-[a-z0-9]{1,8})*");
    }

    public static boolean languageMatches(String preferred, String actual)
    {
        String wanted = normalizeLanguage(preferred);
        String available = normalizeLanguage(actual);
        if (wanted.isEmpty())
            return true;
        if (available.isEmpty())
            return false;
        if (wanted.equals(available))
            return true;
        return primaryLanguage(wanted).equals(primaryLanguage(available));
    }

    public static boolean isValidCaptionStandard(String standard)
    {
        if (standard == null)
            return true;
        String value = standard.trim().toLowerCase(Locale.US).replace("-", "");
        return value.isEmpty()
                || CAPTION_STANDARD_AUTO.equals(value)
                || "608".equals(value)
                || "708".equals(value)
                || CAPTION_STANDARD_CEA608.equals(value)
                || CAPTION_STANDARD_CEA708.equals(value);
    }

    public static String normalizeCaptionStandard(String standard)
    {
        if (standard == null)
            return CAPTION_STANDARD_AUTO;
        String value = standard.trim().toLowerCase(Locale.US).replace("-", "");
        if ("608".equals(value))
            return CAPTION_STANDARD_CEA608;
        if ("708".equals(value))
            return CAPTION_STANDARD_CEA708;
        if (CAPTION_STANDARD_CEA608.equals(value) || CAPTION_STANDARD_CEA708.equals(value))
            return value;
        return CAPTION_STANDARD_AUTO;
    }

    public static int normalizeCaptionService(String standard, int requestedService)
    {
        String normalized = normalizeCaptionStandard(standard);
        int maximum = CAPTION_STANDARD_CEA608.equals(normalized) ? 4 : 63;
        if (requestedService < 1 || requestedService > maximum)
            return 1;
        return requestedService;
    }

    public static int parseCaptionService(String standard, String requestedService)
    {
        try
        {
            return normalizeCaptionService(standard, Integer.parseInt(
                    requestedService == null ? "" : requestedService.trim()));
        }
        catch (NumberFormatException ignored)
        {
            return 1;
        }
    }

    public static SubtitleCodec preferredCaptionCodec(String standard)
    {
        String normalized = normalizeCaptionStandard(standard);
        if (CAPTION_STANDARD_CEA608.equals(normalized))
            return SubtitleCodec.CEA608;
        if (CAPTION_STANDARD_CEA708.equals(normalized))
            return SubtitleCodec.CEA708;
        return null;
    }

    public static int findPreferredSubtitleTrack(SubtitleTrack[] tracks, String preferredLanguage,
                                                  String captionStandard, int captionService)
    {
        if (tracks == null || tracks.length == 0)
            return -1;

        SubtitleCodec preferredCodec = preferredCaptionCodec(captionStandard);
        int preferredChannel = normalizeCaptionService(captionStandard, captionService);
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;

        for (SubtitleTrack track : tracks)
        {
            if (track == null || !track.isSupported())
                continue;

            int score = 0;
            if (!normalizeLanguage(preferredLanguage).isEmpty())
                score += languageMatches(preferredLanguage, track.getLanguage()) ? 20 : -20;

            if (preferredCodec != null)
            {
                if (preferredCodec == track.getSubtitleCodec())
                {
                    score += 40;
                    if (track.getAccessibilityChannel() == preferredChannel)
                        score += 20;
                    else if (track.getAccessibilityChannel() > 0)
                        score -= 10;
                }
                else
                {
                    score -= 40;
                }
            }

            if (bestIndex < 0 || score > bestScore)
            {
                bestIndex = track.getIndex();
                bestScore = score;
            }
        }
        return bestIndex;
    }

    private static String primaryLanguage(String language)
    {
        int separator = language.indexOf('-');
        return separator < 0 ? language : language.substring(0, separator);
    }
}
