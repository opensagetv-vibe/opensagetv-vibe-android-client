package opensagetv.vibe.miniclient.android.tv.debug;

/** Centralizes the stable ordered-broadcast response envelope. */
final class DebugResponseFormatter
{
    private DebugResponseFormatter()
    {
    }

    static String success(String data)
    {
        return "ok=true;" + data;
    }

    static String failure(String data)
    {
        return "ok=false;" + data;
    }
}
