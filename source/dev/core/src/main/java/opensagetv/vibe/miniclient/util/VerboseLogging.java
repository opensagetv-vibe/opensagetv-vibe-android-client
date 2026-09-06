package opensagetv.vibe.miniclient.util;

/**
 * Created by seans on 22/12/15.
 */
public class VerboseLogging {
    public static boolean LOG_GL_ERRORS = false;
    public static boolean DETAILED_MEDIA_COMMAND_PUSHBUFFER = false;
    public static boolean DETAILED_MEDIA_COMMAND = false;
    public static boolean DETAILED_GFX_TEXTURES = false;
    public static boolean DETAILED_GFX = false;
    public static boolean DETAILED_PUSHBUFFER_LOGGING = false;
    public static boolean DETAILED_PLAYER_LOGGING = true;
    public static boolean DETAILED_IMAGE_CACHE = false;
    public static boolean DATASOURCE_LOGGING = false;
    public static boolean LOG_DATASOURCE_BYTES_TO_FILE = false;
    public static String DATASOURCE_CAPTURE_PATH = "";
    // Debug captures must be large enough to span a DVD FLUSH/cell transition.
    // The historical 10 MiB limit was exhausted by the outgoing title before
    // the replacement authored menu bytes arrived.
    public static long DATASOURCE_CAPTURE_MAX_BYTES = 32L * 1024L * 1024L;
    public static boolean LOG_KEYS = true;
}
