package opensagetv.vibe.miniclient.dvd;

/**
 * Decodes the 16-bit physical audio selector sent by SageTV MiniDVDPlayer.
 *
 * <p>The upper byte is the MPEG Program Stream/PES stream id. For
 * private_stream_1 (0xbd), the lower byte is the DVD private substream id.
 * This value is <strong>not</strong> a zero-based MiniPlayer track index.</p>
 */
public final class DvdAudioStreamCode {
    public enum CodecFamily {
        MPEG_AUDIO,
        AC3,
        DTS,
        SDDS,
        LPCM,
        PRIVATE_UNKNOWN,
        UNKNOWN
    }

    public final int rawCode;
    public final int pesStreamId;
    public final int privateSubstreamId;
    public final CodecFamily codecFamily;
    /** Ordinal within the encoded codec family, or -1 when unknown. */
    public final int familyOrdinal;

    private DvdAudioStreamCode(int rawCode, int pesStreamId,
                               int privateSubstreamId,
                               CodecFamily codecFamily, int familyOrdinal) {
        this.rawCode = rawCode;
        this.pesStreamId = pesStreamId;
        this.privateSubstreamId = privateSubstreamId;
        this.codecFamily = codecFamily;
        this.familyOrdinal = familyOrdinal;
    }

    public static DvdAudioStreamCode decode(int wireCode) {
        int raw = wireCode & 0xffff;
        int pes = (raw >>> 8) & 0xff;
        int sub = raw & 0xff;

        if (pes >= 0xc0 && pes <= 0xdf) {
            return new DvdAudioStreamCode(
                    raw, pes, -1, CodecFamily.MPEG_AUDIO, pes - 0xc0);
        }
        if (pes != 0xbd) {
            return new DvdAudioStreamCode(
                    raw, pes, sub, CodecFamily.UNKNOWN, -1);
        }
        if (sub >= 0x80 && sub <= 0x87) {
            return new DvdAudioStreamCode(raw, pes, sub, CodecFamily.AC3, sub - 0x80);
        }
        if (sub >= 0x88 && sub <= 0x8f) {
            return new DvdAudioStreamCode(raw, pes, sub, CodecFamily.DTS, sub - 0x88);
        }
        if (sub >= 0x90 && sub <= 0x97) {
            return new DvdAudioStreamCode(raw, pes, sub, CodecFamily.SDDS, sub - 0x90);
        }
        if (sub >= 0xa0 && sub <= 0xa7) {
            return new DvdAudioStreamCode(raw, pes, sub, CodecFamily.LPCM, sub - 0xa0);
        }
        return new DvdAudioStreamCode(
                raw, pes, sub, CodecFamily.PRIVATE_UNKNOWN, -1);
    }

    public boolean isPrivateStream1() {
        return pesStreamId == 0xbd;
    }

    /**
     * Matches extractor metadata when both the PES and private-substream ids are
     * exposed. For MPEG audio, pass a negative private id.
     */
    public boolean matchesPhysicalIds(int candidatePesStreamId,
                                      int candidatePrivateSubstreamId) {
        if ((candidatePesStreamId & 0xff) != pesStreamId) return false;
        return !isPrivateStream1()
                || (candidatePrivateSubstreamId & 0xff) == privateSubstreamId;
    }

    /** Match FFmpeg/MIM-renumbered tracks by codec family. */
    public boolean matchesTranscodedMime(String sampleMimeType) {
        if (sampleMimeType == null) return false;
        String mime = sampleMimeType.toLowerCase(java.util.Locale.ROOT);
        switch (codecFamily) {
            case AC3:
                return "audio/ac3".equals(mime) || "audio/eac3".equals(mime)
                        || "audio/eac3-joc".equals(mime);
            case DTS:
                return mime.startsWith("audio/vnd.dts");
            case LPCM:
                return "audio/raw".equals(mime) || mime.contains("lpcm");
            case MPEG_AUDIO:
                return "audio/mpeg".equals(mime) || "audio/mpeg-l1".equals(mime)
                        || "audio/mpeg-l2".equals(mime);
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "DvdAudioStreamCode{raw=0x" + Integer.toHexString(rawCode)
                + ", pes=0x" + Integer.toHexString(pesStreamId)
                + (privateSubstreamId >= 0
                    ? ", sub=0x" + Integer.toHexString(privateSubstreamId) : "")
                + ", family=" + codecFamily
                + ", ordinal=" + familyOrdinal + '}';
    }
}
