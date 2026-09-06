package opensagetv.vibe.miniclient.android.video;

import opensagetv.vibe.miniclient.prefs.PrefStore;

/** Shared bounded policy for locally rendered Media3/Exo text subtitles. */
public final class TextSubtitlePresentation
{
    public static final int DEFAULT_SAFE_AREA_PERCENT = 18;
    public static final int DEFAULT_TEXT_SCALE_PERCENT = 100;
    public static final String STYLE_SYSTEM = "system";
    public static final String STYLE_OUTLINE = "outline";
    public static final String STYLE_BLACK_BOX = "black_box";

    private TextSubtitlePresentation() { }

    public static int safeAreaPercent(PrefStore prefs)
    {
        Integer active = ActivePlayerSessionOverrides.getSubtitleSafeAreaPercent();
        return clampSafeArea(active == null ? prefs.getInt(
                PrefStore.Keys.playback_subtitle_safe_area_percent,
                DEFAULT_SAFE_AREA_PERCENT) : active);
    }

    public static int textScalePercent(PrefStore prefs)
    {
        Integer active = ActivePlayerSessionOverrides.getSubtitleTextScalePercent();
        return clampTextScale(active == null ? prefs.getInt(
                PrefStore.Keys.playback_subtitle_text_scale_percent,
                DEFAULT_TEXT_SCALE_PERCENT) : active);
    }

    public static String style(PrefStore prefs)
    {
        String active = ActivePlayerSessionOverrides.getSubtitleTextStyle();
        return normalizeStyle(active == null ? prefs.getString(
                PrefStore.Keys.playback_subtitle_text_style, STYLE_SYSTEM) : active);
    }

    public static int clampSafeArea(int value)
    {
        return Math.max(5, Math.min(35, value));
    }

    public static int clampTextScale(int value)
    {
        return Math.max(75, Math.min(150, value));
    }

    public static String normalizeStyle(String value)
    {
        if (STYLE_OUTLINE.equals(value) || STYLE_BLACK_BOX.equals(value))
            return value;
        return STYLE_SYSTEM;
    }
}
