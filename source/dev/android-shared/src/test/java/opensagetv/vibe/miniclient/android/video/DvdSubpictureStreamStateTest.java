package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;

import static org.junit.Assert.*;

/** Stream-state retransmission must not invalidate already queued DVD cues. */
public class DvdSubpictureStreamStateTest {
    @Test public void repeatedEnabledStreamKeepsQueuedGeneration() {
        DvdSubpictureDecoder decoder = new DvdSubpictureDecoder(null);
        decoder.setSubpictureStream(64);
        assertTrue(decoder.isEventCurrent(0));
        decoder.setSubpictureStream(65);
        assertTrue(decoder.isEventCurrent(1));
        decoder.setSubpictureStream(65);
        assertTrue(decoder.isEventCurrent(1));
    }

    @Test public void changedVisibilityAndStreamStillInvalidate() {
        DvdSubpictureDecoder decoder = new DvdSubpictureDecoder(null);
        decoder.setSubpictureStream(128);
        assertTrue(decoder.isEventCurrent(1));
        decoder.setSubpictureStream(64);
        assertTrue(decoder.isEventCurrent(2));
        decoder.setSubpictureStream(65);
        assertTrue(decoder.isEventCurrent(3));
        assertFalse(decoder.isEventCurrent(2));
    }

    @Test public void resetAlwaysInvalidatesEvenWhenStreamIsUnchanged() {
        DvdSubpictureDecoder decoder = new DvdSubpictureDecoder(null);
        decoder.setSubpictureStream(64);
        decoder.reset();
        assertTrue(decoder.isEventCurrent(1));
        decoder.setSubpictureStream(64);
        assertTrue(decoder.isEventCurrent(1));
    }

    @Test public void legacyDisableRetainsSelectionAndIsIdempotent() {
        DvdSubpictureDecoder decoder = new DvdSubpictureDecoder(null);
        decoder.setSubpictureStream(65);
        decoder.setSubpictureStream(62);
        assertTrue(decoder.isEventCurrent(2));
        decoder.setSubpictureStream(62);
        assertTrue(decoder.isEventCurrent(2));
        decoder.setSubpictureStream(65);
        assertTrue(decoder.isEventCurrent(3));
    }
}
