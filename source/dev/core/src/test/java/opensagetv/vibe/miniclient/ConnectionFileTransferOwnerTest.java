package opensagetv.vibe.miniclient;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.Socket;

import opensagetv.vibe.miniclient.logging.ILogger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ConnectionFileTransferOwnerTest
{
    @Test
    public void uploadCompletesAndUnregisters() throws Exception
    {
        File temporary = File.createTempFile("sagetv-fs-transfer", ".bin");
        RandomAccessFile file = new RandomAccessFile(temporary, "rw");
        file.write(new byte[] {1, 2, 3, 4});
        file.seek(0L);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConnectionFileTransferOwner owner = new ConnectionFileTransferOwner(mock(ILogger.class));
        assertTrue(owner.start(false, new Socket(), output,
                new ByteArrayInputStream(new byte[0]), 4L, file));
        long deadline = System.currentTimeMillis() + 2000L;
        while (owner.activeCount() != 0 && System.currentTimeMillis() < deadline)
            Thread.sleep(5L);
        assertTrue(owner.activeCount() == 0);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, output.toByteArray());
        temporary.delete();
    }

    @Test
    public void closedOwnerRejectsNewWorkAndClosesResources() throws Exception
    {
        ConnectionFileTransferOwner owner = new ConnectionFileTransferOwner(mock(ILogger.class));
        owner.close();
        Socket socket = new Socket();
        File temporary = File.createTempFile("sagetv-fs-transfer", ".bin");
        RandomAccessFile file = new RandomAccessFile(temporary, "rw");
        assertFalse(owner.start(false, socket, new ByteArrayOutputStream(),
                new ByteArrayInputStream(new byte[0]), 0L, file));
        assertTrue(socket.isClosed());
        temporary.delete();
    }
}
