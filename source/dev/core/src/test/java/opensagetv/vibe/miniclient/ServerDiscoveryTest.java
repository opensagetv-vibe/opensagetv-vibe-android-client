package opensagetv.vibe.miniclient;

import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void discoveryUsesLimitedAndUniqueDirectedBroadcasts() throws Exception
    {
        List<InetAddress> targets = ServerDiscovery.normalizeBroadcastTargets(Arrays.asList(
                InetAddress.getByName("192.168.10.255"),
                InetAddress.getByName("192.168.10.255"),
                InetAddress.getByName("10.20.30.255"),
                InetAddress.getLoopbackAddress(),
                null));

        assertEquals(Arrays.asList(
                InetAddress.getByName("255.255.255.255"),
                InetAddress.getByName("192.168.10.255"),
                InetAddress.getByName("10.20.30.255")), targets);
    }

    @Test
    public void discoveryAlwaysRetainsLimitedBroadcastFallback()
    {
        List<InetAddress> targets = ServerDiscovery.normalizeBroadcastTargets(null);
        assertEquals(1, targets.size());
        assertEquals("255.255.255.255", targets.get(0).getHostAddress());
    }
}
