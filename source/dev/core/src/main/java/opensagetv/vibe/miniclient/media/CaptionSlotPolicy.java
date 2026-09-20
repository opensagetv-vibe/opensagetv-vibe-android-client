package opensagetv.vibe.miniclient.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves SageTV's two virtual caption slots onto tracks in the active stream. */
public final class CaptionSlotPolicy
{
    public static final String TYPE_AUTO = "auto";
    public static final String TYPE_TELETEXT = "teletext";
    public static final String TYPE_DVB = "dvb";
    public static final String TYPE_CEA608 = "cea608";
    public static final String TYPE_CEA708 = "cea708";

    private CaptionSlotPolicy() { }

    public static String normalizeType(String type)
    {
        if (type == null) return TYPE_AUTO;
        String value = type.trim().toLowerCase(Locale.US).replace("-", "");
        if (TYPE_TELETEXT.equals(value) || TYPE_DVB.equals(value)
                || TYPE_CEA608.equals(value) || TYPE_CEA708.equals(value))
            return value;
        return TYPE_AUTO;
    }

    public static SubtitleCodec codecForType(String type)
    {
        String value = normalizeType(type);
        if (TYPE_TELETEXT.equals(value)) return SubtitleCodec.TELETEXT;
        if (TYPE_DVB.equals(value)) return SubtitleCodec.DVB;
        if (TYPE_CEA608.equals(value)) return SubtitleCodec.CEA608;
        if (TYPE_CEA708.equals(value)) return SubtitleCodec.CEA708;
        return null;
    }

    /**
     * Finds the underlying track for a virtual CC1/CC2 slot. With fully automatic
     * settings CC2 selects the second-best distinct service when one exists.
     */
    public static SubtitleTrack findTrack(SubtitleTrack[] tracks, int slot,
            String requestedType, String requestedLanguage)
    {
        return findTrack(tracks, slot, requestedType, requestedLanguage, true);
    }

    /**
     * Finds a virtual caption slot, optionally excluding CEA compatibility
     * tracks. UK DVB recordings commonly advertise empty CEA-608/708 tracks
     * even though the real captions are Teletext or DVB bitmap services.
     */
    public static SubtitleTrack findTrack(SubtitleTrack[] tracks, int slot,
            String requestedType, String requestedLanguage, boolean includeCea)
    {
        return findTrack(tracks, slot, requestedType, requestedLanguage, includeCea, true);
    }

    /**
     * Finds a caption track with independently controlled CEA and DVB
     * eligibility. Virtual CC1/CC2 slots exclude DVB because bitmap subtitles
     * are selected by the separate DVB mode (or the legacy STV Subtitles
     * command), never by SageTV's CC1/CC2 callback channels.
     */
    public static SubtitleTrack findTrack(SubtitleTrack[] tracks, int slot,
            String requestedType, String requestedLanguage, boolean includeCea,
            boolean includeDvb)
    {
        if (tracks == null || tracks.length == 0) return null;
        SubtitleCodec requiredCodec = codecForType(requestedType);
        String language = TrackPreferencePolicy.normalizeLanguage(requestedLanguage);
        List<SubtitleTrack> candidates = new ArrayList<SubtitleTrack>();
        List<Integer> scores = new ArrayList<Integer>();
        for (SubtitleTrack track : tracks)
        {
            if (track == null || !track.isSupported() || !isBroadcastCaption(track)) continue;
            if (!includeCea && (track.getSubtitleCodec() == SubtitleCodec.CEA608
                    || track.getSubtitleCodec() == SubtitleCodec.CEA708)) continue;
            if (!includeDvb && track.getSubtitleCodec() == SubtitleCodec.DVB) continue;
            if (requiredCodec != null && requiredCodec != track.getSubtitleCodec()) continue;
            String actualLanguage = reliableLanguage(track);
            if (!language.isEmpty()
                    && !TrackPreferencePolicy.languageMatches(language, actualLanguage))
                continue;

            int score = codecScore(track.getSubtitleCodec());
            if (!language.isEmpty()) score += 100;
            if (track.getAccessibilityChannel() > 0) score += 2;
            int insertAt = 0;
            while (insertAt < scores.size() && scores.get(insertAt) >= score) insertAt++;
            scores.add(insertAt, score);
            candidates.add(insertAt, track);
        }
        if (candidates.isEmpty()) return null;
        boolean fullyAutomatic = language.isEmpty();
        if (fullyAutomatic && slot == 1)
        {
            for (SubtitleTrack candidate : candidates)
                if (isEnglishLanguage(reliableLanguage(candidate))) return candidate;
        }
        if (fullyAutomatic && slot == 2 && candidates.size() > 1)
        {
            // Keep CC1 as the English virtual slot when available, then use
            // the first distinct non-English service for CC2. If the stream
            // only has English services, retain the second distinct track.
            boolean hasEnglish = false;
            for (SubtitleTrack candidate : candidates)
                if (isEnglishLanguage(reliableLanguage(candidate))) { hasEnglish = true; break; }
            if (hasEnglish)
                for (SubtitleTrack candidate : candidates)
                {
                    String candidateLanguage = reliableLanguage(candidate);
                    // An empty language is unknown (not a non-English
                    // service). Do not let an unlabeled CEA-608 fallback
                    // displace a described secondary-language service.
                    if (!candidateLanguage.isEmpty()
                            && !isEnglishLanguage(candidateLanguage))
                        return candidate;
                }
            return candidates.get(1);
        }
        return candidates.get(0);
    }

    public static boolean isBroadcastCaption(SubtitleTrack track)
    {
        if (track == null) return false;
        SubtitleCodec codec = track.getSubtitleCodec();
        return codec == SubtitleCodec.TELETEXT || codec == SubtitleCodec.DVB
                || codec == SubtitleCodec.CEA608 || codec == SubtitleCodec.CEA708;
    }

    /** CEA-608 services do not carry an authoritative per-service language. */
    public static String reliableLanguage(SubtitleTrack track)
    {
        return track == null || track.getSubtitleCodec() == SubtitleCodec.CEA608
                ? "" : TrackPreferencePolicy.normalizeLanguage(track.getLanguage());
    }

    public static boolean isEnglishLanguage(String language)
    {
        String normalized = TrackPreferencePolicy.normalizeLanguage(language);
        return "en".equals(normalized) || "eng".equals(normalized)
                || normalized.startsWith("en-") || normalized.startsWith("eng-");
    }

    private static int codecScore(SubtitleCodec codec)
    {
        // Auto follows the user-facing caption priority: CEA (native 708 before
        // legacy 608), then Teletext, then DVB bitmap. Explicit type mappings
        // remain authoritative and do not use this ranking.
        if (codec == SubtitleCodec.CEA708) return 40;
        if (codec == SubtitleCodec.CEA608) return 35;
        if (codec == SubtitleCodec.TELETEXT) return 30;
        if (codec == SubtitleCodec.DVB) return 20;
        return 0;
    }
}
