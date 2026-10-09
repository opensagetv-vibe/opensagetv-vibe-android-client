package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import opensagetv.vibe.miniclient.video.LegacyExtenderCaptionBridge;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FixedCaptionSideChannelClientTest
{
    private static final String SESSION_TOKEN =
            "abcdef0123456789abcdef0123456789abcdef0123456789";

    @Test
    public void parsesBoundedCaptionPackets() throws Exception
    {
        FixedCaptionSideChannelClient.CaptionBatch batch =
                FixedCaptionSideChannelClient.parseCaptions(
                        "{\"contractVersion\":1,\"packets\":[" +
                                "[8,1250,\"04c849\"],[9,1283,\"05d061\"]]," +
                                "\"cursor\":9,\"reset\":false}");

        assertEquals(2, batch.packets.length);
        assertEquals(8L, batch.packets[0].sequence);
        assertEquals(1250L, batch.packets[0].ptsMs);
        assertArrayEquals(new byte[] { 0x04, (byte) 0xc8, 0x49 },
                batch.packets[0].data);
        assertEquals(9L, batch.cursor);
        assertFalse(batch.reset);
    }

    @Test
    public void retainsServerCursorResetSignal() throws Exception
    {
        FixedCaptionSideChannelClient.CaptionBatch batch =
                FixedCaptionSideChannelClient.parseCaptions(
                        "{\"contractVersion\":1,\"packets\":[]," +
                                "\"cursor\":31,\"reset\":true}");
        assertEquals(31L, batch.cursor);
        assertTrue(batch.reset);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownContract() throws Exception
    {
        FixedCaptionSideChannelClient.parseCaptions(
                "{\"contractVersion\":2,\"packets\":[],\"cursor\":0}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedOrUnboundedPayload() throws Exception
    {
        FixedCaptionSideChannelClient.parseCaptions(
                "{\"contractVersion\":1,\"packets\":[[1,0,\"not-hex\"]]," +
                        "\"cursor\":1}");
    }

    @Test
    public void reservesClaimsForwardsAndTearsDownWithoutMediaData() throws Exception
    {
        final AtomicInteger teardownCount = new AtomicInteger();
        SimpleHttpServer server = new SimpleHttpServer(teardownCount);
        server.start();

        FixedCaptionSideChannelClient client = new FixedCaptionSideChannelClient();
        Object owner = new Object();
        CountDownLatch forwarded = new CountDownLatch(1);
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge(
                new LegacyExtenderCaptionBridge.Sink()
                {
                    @Override public void postSubtitleInfo(long pts45Khz,
                            long duration45Khz, byte[] data, int flags)
                    {
                        if ((flags & LegacyExtenderCaptionBridge.CC_SUBTITLE) != 0 &&
                                data != null && data.length == 8)
                            forwarded.countDown();
                    }
                });
        try
        {
            client.startForTest("127.0.0.1", server.port());
            assertTrue(client.attach(owner, bridge));
            client.updatePlaybackClock(owner, 2_000L);
            assertTrue("caption record was not forwarded", forwarded.await(4, TimeUnit.SECONDS));
            assertEquals("active", client.stateForDiagnostics());
            client.detach(owner, true);
            long deadline = System.currentTimeMillis() + 2_000L;
            while (teardownCount.get() == 0 && System.currentTimeMillis() < deadline)
                Thread.sleep(25L);
            assertEquals(1, teardownCount.get());
        }
        finally
        {
            client.stop();
            server.close();
        }
    }

    @Test
    public void deliversPrefetchedPairsBetweenHttpPollsAndHonorsPause() throws Exception
    {
        SimpleHttpServer server = new SimpleHttpServer(new AtomicInteger(),
                "{\"contractVersion\":1,\"packets\":[[1,1000,\"04c849\"]," +
                "[2,1033,\"04c849\"],[3,1066,\"04c849\"]],\"cursor\":3,\"reset\":false}");
        server.start();
        FixedCaptionSideChannelClient client = new FixedCaptionSideChannelClient();
        Object owner = new Object();
        AtomicInteger count = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge((pts, dur, data, flags) -> {
            int number = count.incrementAndGet();
            if (number == 1) first.countDown();
            if (number == 2) second.countDown();
        });
        try
        {
            client.startClaimed("http://127.0.0.1:" + server.port(), SESSION_TOKEN);
            assertTrue(client.attach(owner, bridge));
            client.updatePlaybackClock(owner, 1000L);
            assertTrue(first.await(4, TimeUnit.SECONDS));
            int polls = server.captionRequests.get();
            client.updatePlaybackClock(owner, 1034L);
            // Old250ms network-owned draining cannot satisfy this budget.
            assertTrue("prefetched caption still waits for HTTP polling",
                    second.await(180, TimeUnit.MILLISECONDS));
            assertEquals("finer delivery must not increase HTTP traffic", polls,
                    server.captionRequests.get());
            client.setPaused(owner, true);
            client.updatePlaybackClock(owner, 1067L);
            Thread.sleep(200L);
            assertEquals("paused service emitted a future pair", 2, count.get());
            client.setPaused(owner, false);
            long deadline = System.currentTimeMillis() + 1000L;
            while (count.get() < 3 && System.currentTimeMillis() < deadline)
                Thread.sleep(20L);
            assertEquals(3, count.get());
        }
        finally
        {
            client.stop();
            server.close();
        }
    }

    /** Dependency-free loopback HTTP stub; Android unit tests use a bootclasspath without jdk.httpserver. */
    private static final class SimpleHttpServer implements AutoCloseable, Runnable
    {
        private final ServerSocket socket;
        private final AtomicInteger teardownCount;
        private final String captionsBody;
        private final AtomicInteger captionRequests = new AtomicInteger();
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Thread thread;
        private final CountDownLatch blockedPoll = new CountDownLatch(1);
        private final CountDownLatch releasePoll = new CountDownLatch(1);
        private boolean blockSecondPoll;

        SimpleHttpServer(AtomicInteger teardownCount) throws IOException
        {
            this(teardownCount, "{\"contractVersion\":1," +
                    "\"packets\":[[1,1250,\"04c849\"]],\"cursor\":1,\"reset\":false}");
        }

        SimpleHttpServer(AtomicInteger teardownCount, String captionsBody) throws IOException
        {
            this.socket = new ServerSocket(0);
            this.teardownCount = teardownCount;
            this.captionsBody = captionsBody;
        }

        int port() { return socket.getLocalPort(); }

        void start()
        {
            thread = new Thread(this, "fixed-caption-test-http");
            thread.setDaemon(true);
            thread.start();
        }

        @Override public void run()
        {
            while (running.get())
            {
                try
                {
                    handle(socket.accept());
                }
                catch (IOException failure)
                {
                    if (running.get()) throw new RuntimeException(failure);
                }
            }
        }

        private void handle(Socket client) throws IOException
        {
            try
            {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        client.getInputStream(), StandardCharsets.US_ASCII));
                String request = reader.readLine();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                String path = request == null ? "" : request.split(" ")[1];
                if (path.startsWith("/v1/capabilities"))
                    reply(client, 200, "{\"contractVersion\":1,\"state\":\"ready\"," +
                            "\"reservationAvailable\":true}");
                else if (path.startsWith("/v1/sessions/reserve"))
                    reply(client, 200, "{\"contractVersion\":1," +
                            "\"reservationToken\":\"" + SESSION_TOKEN +
                            "\",\"expiresInMs\":60000}");
                else if (path.startsWith("/v1/sessions/claim"))
                    reply(client, 200, "{\"contractVersion\":1," +
                            "\"sessionToken\":\"" + SESSION_TOKEN + "\"}");
                else if (path.startsWith("/v1/captions"))
                {
                    int number = captionRequests.incrementAndGet();
                    if (blockSecondPoll && number == 2)
                    {
                        blockedPoll.countDown();
                        try { releasePoll.await(2L, TimeUnit.SECONDS); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    }
                    reply(client, 200, captionsBody);
                }
                else if (path.startsWith("/v1/sessions/status"))
                    reply(client, 200, "{\"contractVersion\":1," +
                            "\"state\":\"active\",\"nextCursor\":2}");
                else if (path.startsWith("/v1/sessions/teardown"))
                {
                    teardownCount.incrementAndGet();
                    reply(client, 200, "{\"ok\":true,\"state\":\"released\"}");
                }
                else
                    reply(client, 404, "{\"ok\":false}");
            }
            finally { client.close(); }
        }

        private static void reply(Socket client, int status, String body) throws IOException
        {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : "Not Found";
            OutputStream output = client.getOutputStream();
            output.write(("HTTP/1.1 " + status + " " + reason + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.flush();
        }

        @Override public void close() throws IOException
        {
            releasePoll.countDown();
            running.set(false);
            socket.close();
            if (thread != null)
            {
                try { thread.join(1_000L); }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Test
    public void slowHttpPollDoesNotBatchAlreadyBufferedCaptionPairs() throws Exception
    {
        SimpleHttpServer server = new SimpleHttpServer(new AtomicInteger(),
                "{\"contractVersion\":1,\"packets\":[[1,1000,\"04c849\"]," +
                "[2,1033,\"04c849\"]],\"cursor\":2,\"reset\":false}");
        server.blockSecondPoll = true;
        server.start();
        FixedCaptionSideChannelClient client = new FixedCaptionSideChannelClient();
        Object owner = new Object();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        LegacyExtenderCaptionBridge bridge = new LegacyExtenderCaptionBridge((pts, duration, data, flags) -> {
            if (count.incrementAndGet() == 1) first.countDown();
            else second.countDown();
        });
        try
        {
            client.startForTest("127.0.0.1", server.port());
            assertTrue(client.attach(owner, bridge));
            client.updatePlaybackClock(owner, 1000L);
            assertTrue(first.await(3L, TimeUnit.SECONDS));
            assertTrue(server.blockedPoll.await(2L, TimeUnit.SECONDS));
            client.updatePlaybackClock(owner, 1034L);
            assertTrue("HTTP blocked presentation of an already-buffered CEA pair",
                    second.await(180L, TimeUnit.MILLISECONDS));
            assertEquals("delivery must not add HTTP polls", 2, server.captionRequests.get());
        }
        finally
        {
            server.releasePoll.countDown();
            client.stop();
            server.close();
        }
    }
}
