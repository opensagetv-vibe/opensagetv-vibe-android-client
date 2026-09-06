package opensagetv.vibe.miniclient.dvd;

public final class DvdAudioStreamCodeSelfTest {
    public static void main(String[] args) {
        DvdAudioStreamCode mpeg = DvdAudioStreamCode.decode(0xc200);
        TestData.check(mpeg.codecFamily == DvdAudioStreamCode.CodecFamily.MPEG_AUDIO,
                "MPEG audio family");
        TestData.check(mpeg.pesStreamId == 0xc2 && mpeg.familyOrdinal == 2,
                "MPEG stream id and ordinal");
        TestData.check(mpeg.matchesPhysicalIds(0xc2, -1), "MPEG physical match");

        DvdAudioStreamCode ac3 = DvdAudioStreamCode.decode(0xbd83);
        TestData.check(ac3.codecFamily == DvdAudioStreamCode.CodecFamily.AC3,
                "AC3 family");
        TestData.check(ac3.privateSubstreamId == 0x83 && ac3.familyOrdinal == 3,
                "AC3 private stream and ordinal");
        TestData.check(ac3.matchesPhysicalIds(0xbd, 0x83), "AC3 physical match");
        TestData.check(!ac3.matchesPhysicalIds(0xbd, 0x84), "AC3 mismatch");
        TestData.check(ac3.matchesTranscodedMime("audio/ac3"),
                "MIM AC3 MIME family match");
        TestData.check(!ac3.matchesTranscodedMime("audio/mpeg"),
                "MIM AC3 must not match MPEG audio");

        TestData.check(DvdAudioStreamCode.decode(0xbd89).codecFamily
                        == DvdAudioStreamCode.CodecFamily.DTS,
                "DTS family");
        TestData.check(DvdAudioStreamCode.decode(0xbd89)
                        .matchesTranscodedMime("audio/vnd.dts.hd"),
                "MIM DTS MIME family match");
        TestData.check(DvdAudioStreamCode.decode(0xbd92).codecFamily
                        == DvdAudioStreamCode.CodecFamily.SDDS,
                "SDDS family");
        TestData.check(DvdAudioStreamCode.decode(0xbda4).codecFamily
                        == DvdAudioStreamCode.CodecFamily.LPCM,
                "LPCM family");
        TestData.check(DvdAudioStreamCode.decode(0xc100)
                        .matchesTranscodedMime("audio/mpeg-l2"),
                "MIM MPEG audio MIME family match");
        TestData.check(DvdAudioStreamCode.decode(0xbd70).codecFamily
                        == DvdAudioStreamCode.CodecFamily.PRIVATE_UNKNOWN,
                "unknown private family");
        System.out.println("PASS DvdAudioStreamCodeSelfTest");
    }
}
