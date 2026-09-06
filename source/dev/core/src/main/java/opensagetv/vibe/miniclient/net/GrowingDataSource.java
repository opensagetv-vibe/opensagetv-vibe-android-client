package opensagetv.vibe.miniclient.net;

import java.io.IOException;

/** A random-access source whose readable length may increase. */
public interface GrowingDataSource
{
    long waitForGrowth(long position, long timeoutMs) throws IOException;
}
