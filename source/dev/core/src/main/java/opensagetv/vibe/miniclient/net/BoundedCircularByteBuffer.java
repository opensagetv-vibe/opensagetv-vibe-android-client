package opensagetv.vibe.miniclient.net;

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Fixed-capacity byte ring used by SageTV Push transport.
 *
 * <p>Reads consume only currently available bytes. Writes apply backpressure
 * when the ring is full and wake as soon as a reader consumes data or the
 * buffer is cleared. This keeps the historical Push contract without pulling
 * a GPL-licensed third-party utility library into the APK.</p>
 */
final class BoundedCircularByteBuffer
{
    private final byte[] data;
    private int readPosition;
    private int writePosition;
    private int size;
    private boolean closed;

    BoundedCircularByteBuffer(int capacity)
    {
        if (capacity <= 0)
        {
            throw new IllegalArgumentException("capacity must be positive");
        }
        data = new byte[capacity];
    }

    synchronized int available()
    {
        return size;
    }

    synchronized int getSpaceLeft()
    {
        return data.length - size;
    }

    synchronized void clear()
    {
        readPosition = 0;
        writePosition = 0;
        size = 0;
        notifyAll();
    }

    synchronized void close()
    {
        closed = true;
        notifyAll();
    }

    synchronized int read(byte[] destination, int offset, int length)
    {
        checkRange(destination, offset, length);
        if (length == 0)
        {
            return 0;
        }
        if (size == 0)
        {
            return closed ? -1 : 0;
        }

        int count = Math.min(length, size);
        int first = Math.min(count, data.length - readPosition);
        System.arraycopy(data, readPosition, destination, offset, first);
        int second = count - first;
        if (second > 0)
        {
            System.arraycopy(data, 0, destination, offset + first, second);
        }
        readPosition = (readPosition + count) % data.length;
        size -= count;
        notifyAll();
        return count;
    }

    void write(byte[] source, int offset, int length) throws IOException
    {
        checkRange(source, offset, length);
        int remaining = length;
        int sourceOffset = offset;
        while (remaining > 0)
        {
            synchronized (this)
            {
                while (size == data.length && !closed)
                {
                    try
                    {
                        wait();
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        InterruptedIOException interrupted = new InterruptedIOException(
                                "Interrupted while waiting for Push buffer space");
                        interrupted.initCause(e);
                        throw interrupted;
                    }
                }
                if (closed)
                {
                    throw new IOException("Push buffer is closed");
                }

                int count = Math.min(remaining, data.length - size);
                int first = Math.min(count, data.length - writePosition);
                System.arraycopy(source, sourceOffset, data, writePosition, first);
                int second = count - first;
                if (second > 0)
                {
                    System.arraycopy(source, sourceOffset + first, data, 0, second);
                }
                writePosition = (writePosition + count) % data.length;
                size += count;
                sourceOffset += count;
                remaining -= count;
                notifyAll();
            }
        }
    }

    private static void checkRange(byte[] bytes, int offset, int length)
    {
        if (bytes == null)
        {
            throw new NullPointerException("bytes");
        }
        if ((offset | length) < 0 || length > bytes.length - offset)
        {
            throw new IndexOutOfBoundsException(
                    "offset=" + offset + ", length=" + length + ", size=" + bytes.length);
        }
    }
}
