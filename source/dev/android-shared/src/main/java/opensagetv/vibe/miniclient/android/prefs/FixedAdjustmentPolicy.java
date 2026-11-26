package opensagetv.vibe.miniclient.android.prefs;

/** Single UI selection mapped onto stable legacy playback flags. No player changes. */
public final class FixedAdjustmentPolicy {
    private FixedAdjustmentPolicy() { }
    public static String selection(boolean adaptive, boolean inPlace) {
        return !adaptive ? "off" : inPlace ? "in_place" : "switch";
    }
    public static boolean valid(String value) {
        return "off".equals(value) || "switch".equals(value) || "in_place".equals(value);
    }
    public static boolean adaptive(String value) { return "switch".equals(value) || "in_place".equals(value); }
    public static boolean inPlace(String value) { return "in_place".equals(value); }
}
