package opensagetv.vibe.miniclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server discovery enables the auto discovery of SageTV servers on the same network.  When a server's a discovered they are passed to the
 * ServerDiscoveryCallback callback.
 */
public class ServerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(ServerDiscovery.class);

    private final Object discoveryLock = new Object();
    private final AtomicInteger nextThreadId = new AtomicInteger(1);
    private ExecutorService discoveryExecutor;
    private volatile java.net.DatagramSocket sock = null;
    private Future<?> pendingDiscovery;

    ServerDiscovery() {
        discoveryExecutor = createDiscoveryExecutor();
    }

    private ExecutorService createDiscoveryExecutor() {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "SageTV-ServerDiscovery-" + nextThreadId.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private ExecutorService activeDiscoveryExecutor() {
        if (discoveryExecutor == null || discoveryExecutor.isShutdown()
                || discoveryExecutor.isTerminated()) {
            discoveryExecutor = createDiscoveryExecutor();
        }
        return discoveryExecutor;
    }

    public synchronized void discoverServersAsync(final int discoveryTimeout, final ServerDiscoverCallback callback) {
        cancelPendingDiscovery();
        closeSocket();
        pendingDiscovery = activeDiscoveryExecutor().submit(new Runnable() {
            @Override
            public void run() {
                if (Thread.currentThread().isInterrupted())
                    return;
                discoverServers(discoveryTimeout, callback);
            }
        });
    }

    public ServerInfo[] discoverServers(int discoveryTimeout, ServerDiscoverCallback callback) {
        synchronized (discoveryLock) {
            return discoverServersLocked(discoveryTimeout, callback);
        }
    }

    private ServerInfo[] discoverServersLocked(int discoveryTimeout, ServerDiscoverCallback callback) {
        List<ServerInfo> servers = (callback == null) ? new ArrayList<ServerInfo>() : null;
        log.debug("Sending out discovery packets to find SageTVPlaceshifter Servers...");
        try {
            // Try on the encoder discovery port which is less likely to be in
            // use
            try {
                sock = new java.net.DatagramSocket(8271);
            } catch (java.net.BindException be2) {
                // Just create it wherever
                sock = new java.net.DatagramSocket();
            }
            java.net.DatagramPacket pack = new java.net.DatagramPacket(new byte[512], 512);
            byte[] data = pack.getData();
            data[0] = 'S';
            data[1] = 'T';
            data[2] = 'V';
            data[3] = 1;
            pack.setLength(32);
            sock.setBroadcast(true);
            // Find the broadcast address for this subnet.
            // String myIP = SageTV.api("GetLocalIPAddress", new
            // Object[0]).toString();
            // int lastIdx = myIP.lastIndexOf('.');
            // myIP = myIP.substring(0, lastIdx) + ".255";
            pack.setAddress(java.net.InetAddress.getByName("255.255.255.255"));
            pack.setPort(31100);
            sock.send(pack);
            long startTime = System.currentTimeMillis();
            do {
                int currTimeout = (int) Math.max(1, (startTime + discoveryTimeout) - System.currentTimeMillis());
                sock.setSoTimeout(currTimeout);
                sock.receive(pack);
                if (pack.getLength() >= 4) {
                    log.debug("Discovery packet received: {}", pack);
                    ServerInfo si = new ServerInfo();
                    if (data[0] == 'S' && data[1] == 'T' && data[2] == 'V' && data[3] == 2) {
                        si.name = pack.getAddress().getHostName();
                        si.address = pack.getAddress().getHostAddress();
                        if (pack.getLength() >= 13) // it also has locator ID in
                        // it
                        {
                            int locatorID1 = ((data[5] & 0xFF) << 24) | ((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 8)
                                    | (data[8] & 0xFF);
                            int locatorID2 = ((data[9] & 0xFF) << 24) | ((data[10] & 0xFF) << 16) | ((data[11] & 0xFF) << 8)
                                    | (data[12] & 0xFF);
                            long locatorID = (((long) locatorID1) << 32) | locatorID2;
                            String prettyGuid = "";
                            for (int i = 0; i < 4; i++) {
                                String subGuid = Long.toString((locatorID >> ((3 - i) * 16)) & 0xFFFF, 16);
                                while (subGuid.length() < 4)
                                    subGuid = "0" + subGuid;
                                if (i != 0)
                                    prettyGuid += "-";
                                prettyGuid += subGuid;
                            }
                            si.locatorID = prettyGuid.toUpperCase();
                        }
                        if (pack.getLength() >= 15) // it also has the port in
                        // it
                        {
                            si.port = ((data[13] & 0xFF) << 8) | (data[14] & 0xFF);
                        }
                        log.debug("Added server info: {}", si);
                        if (callback != null)
                            callback.serverDiscovered(si);
                        else
                            servers.add(si);
                    }
                }
            } while (true);
        } catch (Throwable e) {
            log.error("Error discovering servers:", e);
        } finally {
            if (sock != null) {
                try {
                    sock.close();
                } catch (Exception e) {
                }
                sock = null;
            }
        }
        return (servers == null) ? null : servers.toArray(new ServerInfo[0]);
    }

    public synchronized void close() {
        cancelPendingDiscovery();
        closeSocket();
    }

    private void cancelPendingDiscovery() {
        if (pendingDiscovery != null) {
            pendingDiscovery.cancel(true);
            pendingDiscovery = null;
        }
    }

    private void closeSocket() {
        java.net.DatagramSocket activeSocket = sock;
        if (activeSocket == null)
            return;
        try {
            activeSocket.close();
        } catch (Throwable t) {
        }
    }

    public synchronized void shutdown() {
        close();
        if (discoveryExecutor != null) {
            discoveryExecutor.shutdownNow();
            discoveryExecutor = null;
        }
    }

    public interface ServerDiscoverCallback {
        void serverDiscovered(ServerInfo si);
    }

}
