package opensagetv.vibe.miniclient;

import org.junit.Test;

/** Regression coverage for activity/service recreation in one Android process. */
public class ServerDiscoveryTest
{
    @Test
    public void discoveryCanRestartAfterClientShutdown()
    {
        ServerDiscovery discovery = new ServerDiscovery();
        discovery.shutdown();

        // This previously threw RejectedExecutionException on the main thread.
        discovery.discoverServersAsync(1, null);
        discovery.shutdown();
    }
}
