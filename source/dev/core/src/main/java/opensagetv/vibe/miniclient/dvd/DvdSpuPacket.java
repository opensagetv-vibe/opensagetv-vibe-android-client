package opensagetv.vibe.miniclient.dvd;

/** Complete or partial DVD subpicture payload extracted from private_stream_1. */
public final class DvdSpuPacket {
    public final int streamId;
    public final long pts90k;
    public final byte[] data;

    public DvdSpuPacket(int streamId, long pts90k, byte[] data) {
        this.streamId = streamId;
        this.pts90k = pts90k;
        this.data = data;
    }
}

