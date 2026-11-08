package opensagetv.vibe.miniclient;

/**
 * Limits outstanding ordinary Push work without shrinking the receive ring.
 *
 * Stock MiniPlayer pipelines bytes according to this credit, and drains those
 * replies while holding the same decoder lock needed by seek. Advertising an
 * empty4MiB ring therefore permits a large network burst to delay a skip when
 * delivery/replies slow down.128KiB limits that outstanding work while fitting
 * any stock payload (four default32KiB packets). A512KiB trial still exposed
 * TCP retransmission backoff on the Pro. This is flow control, not
 * a new seek, retry, decoder buffer size or a guarantee against network loss.
 */
public final class OrdinaryPushCreditPolicy
{
    public static final int CREDIT_BYTES = 128 * 1024;
    private int largestPayload;

    public void reset() { largestPayload = 0; }

    public int available(int physicalFree, boolean ordinaryPush, int payloadBytes)
    {
        if (!ordinaryPush) return physicalFree;
        if (payloadBytes > largestPayload) largestPayload = payloadBytes;
        // Preserve startup/probe capacity before the first real payload. This
        // also lets a server with a nonstandard forced payload reveal its size:
        // never advertise less than that size or it could poll forever. Retain
        // the observed size across FLUSH, reset only for INIT/new OPENURL.
        if (physicalFree <= 0 || largestPayload <= 0) return physicalFree;
        return Math.min(physicalFree, Math.max(CREDIT_BYTES, largestPayload));
    }
}
