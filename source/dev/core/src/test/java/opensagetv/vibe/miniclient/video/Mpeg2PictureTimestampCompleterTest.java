package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Mpeg2PictureTimestampCompleterTest
{
    @Test
    public void repairsMissingForwardPicturePtsFromOpenGopTemporalReference()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();

        feed(completer, concat(sequenceHeader(4), sequenceExtension(false), gopHeader(),
                pictureWithCodingExtension(0, 1, false, true)), 280_633L);
        Mpeg2PictureTimestampCompleter.PictureTiming pending = observe(completer,
                pictureWithCodingExtension(2, 2, false, true), 314_000L);
        feed(completer, pictureWithCodingExtension(1, 3, true, true), 314_000L);
        long repaired = completer.completeTimestamp(pending, 314_000L);

        assertEquals(364_050L, repaired);
        assertTrue(completer.wasLastTimestampCorrected());
    }

    @Test
    public void treatsNtscDvdSixtyHzSequenceRateAsTelecineFieldClock()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();

        feed(completer, concat(sequenceHeader(7), sequenceExtension(false), gopHeader(),
                pictureWithCodingExtension(0, 1, false, true)), 280_633L);
        Mpeg2PictureTimestampCompleter.PictureTiming pending = observe(completer,
                pictureWithCodingExtension(2, 2, false, true), 314_000L);
        feed(completer, pictureWithCodingExtension(1, 3, true, true), 314_000L);
        long repaired = completer.completeTimestamp(pending, 314_000L);

        // The authored 2/3-field cadence is 33.367 + 50.050 ms, not half of it.
        assertEquals(364_050L, repaired);
        assertTrue(completer.wasLastTimestampCorrected());
    }

    @Test
    public void retainsMedia3SixtyHzFieldClockAcrossTwentyNineHzSequenceHeader()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();
        completer.setFrameRate(60_000.0f / 1_001.0f);

        feed(completer, concat(sequenceHeader(4), sequenceExtension(false), gopHeader(),
                pictureWithCodingExtension(0, 1, false, true)), 280_633L);
        Mpeg2PictureTimestampCompleter.PictureTiming pending = observe(completer,
                pictureWithCodingExtension(2, 2, false, true), 314_000L);
        feed(completer, pictureWithCodingExtension(1, 3, true, true), 314_000L);
        long repaired = completer.completeTimestamp(pending, 314_000L);

        assertEquals(364_050L, repaired);
        assertTrue(completer.wasLastTimestampCorrected());
    }

    @Test
    public void preservesAuthoredBFrameAndPlausibleForwardPicturePts()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();
        feed(completer, concat(sequenceHeader(4), sequenceExtension(false), gopHeader(),
                pictureWithCodingExtension(0, 1, false, true)), 280_633L);

        assertEquals(314_000L, feed(completer,
                pictureWithCodingExtension(1, 3, true, true), 314_000L));
        assertFalse(completer.wasLastTimestampCorrected());
        completer.notePacketTimestamp(true);
        assertEquals(364_050L, feed(completer,
                pictureWithCodingExtension(2, 2, false, true), 364_050L));
        assertFalse(completer.wasLastTimestampCorrected());
    }

    @Test
    public void recognizesPictureHeaderSplitAcrossInputChunks()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();
        byte[] first = concat(sequenceHeader(4), sequenceExtension(false), gopHeader(),
                pictureWithCodingExtension(0, 1, false, true));
        feedSplit(completer, first, 280_633L);
        Mpeg2PictureTimestampCompleter.PictureTiming pending = observeSplit(completer,
                pictureWithCodingExtension(2, 2, false, true), 314_000L);
        feedSplit(completer, pictureWithCodingExtension(1, 3, true, true), 314_000L);
        assertEquals(364_050L, completer.completeTimestamp(pending, 314_000L));
        assertTrue(completer.wasLastTimestampCorrected());
    }

    @Test
    public void usesPreviousReferenceDisplayTimeForLaterMissingForwardPicture()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();
        feed(completer, concat(sequenceHeader(4), sequenceExtension(false), gopHeader(),
                pictureWithCodingExtension(0, 1, false, true)), 280_633L);
        feed(completer, pictureWithCodingExtension(2, 2, false, true), 314_000L);
        feed(completer, pictureWithCodingExtension(1, 3, true, true), 314_000L);
        Mpeg2PictureTimestampCompleter.PictureTiming p5 = observe(completer,
                pictureWithCodingExtension(5, 2, true, true), 347_367L);
        assertEquals(480_833L, completer.completeTimestamp(p5, 347_367L));
        feed(completer, pictureWithCodingExtension(3, 3, true, true), 397_422L);
        feed(completer, pictureWithCodingExtension(4, 3, false, true), 447_478L);

        Mpeg2PictureTimestampCompleter.PictureTiming p8 = observe(completer,
                pictureWithCodingExtension(8, 2, false, true), 480_844L);
        long repaired = completer.completeTimestamp(p8, 480_844L);

        assertEquals(614_300L, repaired);
        assertTrue(completer.wasLastTimestampCorrected());
    }

    @Test
    public void doesNotRepairWithoutAnIntraPictureAnchor()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();
        byte[] sample = concat(sequenceHeader(4), gopHeader(), picture(5, 2));
        assertEquals(512_634L, feed(completer, sample, 512_634L));
        assertFalse(completer.wasLastTimestampCorrected());
    }

    @Test
    public void repairsUntimestampedOpenGopBPictureBeforeIntraReference()
    {
        Mpeg2PictureTimestampCompleter completer = new Mpeg2PictureTimestampCompleter();
        feed(completer, concat(sequenceHeader(4), sequenceExtension(false), gopHeader(),
                pictureWithCodingExtension(0, 1, false, true)), 500_000L);
        feed(completer, pictureWithCodingExtension(1, 3, true, true), 533_366L);

        feed(completer, concat(gopHeader(),
                pictureWithCodingExtension(2, 1, false, true)), 1_000_000L);
        long corrected = feed(completer,
                pictureWithCodingExtension(1, 3, true, true), 1_033_366L);

        assertEquals(949_950L, corrected);
        assertTrue(completer.wasLastTimestampCorrected());
    }

    @Test
    public void reportsBitstreamInterlaceWithoutClaimingDecoderBehavior()
    {
        Mpeg2PictureTimestampCompleter progressive = new Mpeg2PictureTimestampCompleter();
        progressive.consume(concat(sequenceExtension(true),
                pictureWithCodingExtension(0, 1, false, true)), 0,
                concat(sequenceExtension(true), pictureWithCodingExtension(0, 1, false, true)).length);
        assertEquals("progressive_sequence", progressive.getInterlaceObservation());
        assertTrue(progressive.isSequenceExtensionSeen());

        Mpeg2PictureTimestampCompleter telecine = new Mpeg2PictureTimestampCompleter();
        byte[] telecineBytes = concat(sequenceExtension(false),
                pictureWithCodingExtension(0, 1, true, true));
        telecine.consume(telecineBytes, 0, telecineBytes.length);
        assertEquals("interlaced_sequence_telecine", telecine.getInterlaceObservation());
        assertEquals(1L, telecine.getProgressiveFrameCount());

        Mpeg2PictureTimestampCompleter interlaced = new Mpeg2PictureTimestampCompleter();
        byte[] interlacedBytes = concat(sequenceExtension(false),
                pictureWithCodingExtension(0, 1, false, false));
        interlaced.consume(interlacedBytes, 0, interlacedBytes.length);
        assertEquals("interlaced_sequence_interlaced_frames",
                interlaced.getInterlaceObservation());
        assertEquals(1L, interlaced.getInterlacedFrameCount());
        assertEquals(0L, interlaced.getFieldPictureCount());
    }

    private static long feed(Mpeg2PictureTimestampCompleter completer, byte[] sample, long timeUs)
    {
        long start = completer.getBytesConsumed();
        completer.consume(sample, 0, sample.length);
        return completer.completeTimestamp(start, completer.getBytesConsumed(), timeUs);
    }

    private static Mpeg2PictureTimestampCompleter.PictureTiming observe(
            Mpeg2PictureTimestampCompleter completer, byte[] sample, long timeUs)
    {
        long start = completer.getBytesConsumed();
        completer.consume(sample, 0, sample.length);
        return completer.observeSample(start, completer.getBytesConsumed(), timeUs);
    }

    private static long feedSplit(Mpeg2PictureTimestampCompleter completer, byte[] sample,
            long timeUs)
    {
        long start = completer.getBytesConsumed();
        for (int i = 0; i < sample.length; i++)
            completer.consume(sample, i, 1);
        return completer.completeTimestamp(start, completer.getBytesConsumed(), timeUs);
    }

    private static Mpeg2PictureTimestampCompleter.PictureTiming observeSplit(
            Mpeg2PictureTimestampCompleter completer, byte[] sample, long timeUs)
    {
        long start = completer.getBytesConsumed();
        for (int i = 0; i < sample.length; i++)
            completer.consume(sample, i, 1);
        return completer.observeSample(start, completer.getBytesConsumed(), timeUs);
    }

    private static byte[] sequenceHeader(int frameRateCode)
    {
        return new byte[] { 0, 0, 1, (byte) 0xB3, 0x2D, 0x01, (byte) 0xE0,
                (byte) (frameRateCode & 0x0F) };
    }

    private static byte[] gopHeader()
    {
        return new byte[] { 0, 0, 1, (byte) 0xB8, 0, 0, 0, 0 };
    }

    private static byte[] sequenceExtension(boolean progressive)
    {
        // extension id=1, profile/level=0, progressive_sequence follows.
        return new byte[] { 0, 0, 1, (byte) 0xB5, 0x10,
                (byte) (progressive ? 0x08 : 0x00), 0, 0, 0 };
    }

    private static byte[] pictureWithCodingExtension(int temporalReference, int pictureType,
            boolean repeatFirstField, boolean progressiveFrame)
    {
        byte[] extension = new byte[] { 0, 0, 1, (byte) 0xB5,
                (byte) 0x80, 0, 0x03,
                (byte) (repeatFirstField ? 0x02 : 0x00),
                (byte) (progressiveFrame ? 0x80 : 0x00) };
        return concat(picture(temporalReference, pictureType), extension);
    }

    private static byte[] picture(int temporalReference, int pictureType)
    {
        return new byte[] { 0, 0, 1, 0,
                (byte) ((temporalReference >>> 2) & 0xFF),
                (byte) (((temporalReference & 0x03) << 6) | ((pictureType & 0x07) << 3)),
                0, 0 };
    }

    private static byte[] concat(byte[]... arrays)
    {
        int length = 0;
        for (byte[] array : arrays) length += array.length;
        byte[] joined = new byte[length];
        int offset = 0;
        for (byte[] array : arrays)
        {
            System.arraycopy(array, 0, joined, offset, array.length);
            offset += array.length;
        }
        return joined;
    }
}
