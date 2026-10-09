package opensagetv.vibe.miniclient.android.ui.keymaps;

import org.junit.Test;
import opensagetv.vibe.miniclient.SageCommand;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

public class DvdRemoteScanPolicyTest
{
    @Test public void oppositeDirectionStepsDownBeforeReversing()
    {
        assertArrayEquals(new SageCommand[]{SageCommand.PLAY, SageCommand.FF,
                SageCommand.FF, SageCommand.FF},
                DvdRemoteScanPolicy.sequence(SageCommand.REW, 16f));
        assertArrayEquals(new SageCommand[]{SageCommand.PLAY, SageCommand.REW,
                SageCommand.REW}, DvdRemoteScanPolicy.sequence(SageCommand.FF, -8f));
        assertArrayEquals(new SageCommand[]{SageCommand.PLAY, SageCommand.FF},
                DvdRemoteScanPolicy.sequence(SageCommand.REW, 4f));
        assertEquals(SageCommand.PLAY,
                DvdRemoteScanPolicy.resolve(SageCommand.REW, 2f));
        assertEquals(SageCommand.PLAY,
                DvdRemoteScanPolicy.resolve(SageCommand.FF, -2f));
    }

    @Test public void playPauseCancelsEitherScanButTogglesAtNormalSpeed()
    {
        for (SageCommand key : new SageCommand[]{SageCommand.PLAY_PAUSE,
                SageCommand.PLAY, SageCommand.PAUSE})
            for (float rate : new float[]{2f, 16f, -2f, -16f})
                assertEquals(SageCommand.PLAY, DvdRemoteScanPolicy.resolve(key, rate));
        assertEquals(SageCommand.PLAY_PAUSE,
                DvdRemoteScanPolicy.resolve(SageCommand.PLAY_PAUSE, 1f));
    }

    @Test public void sameDirectionIncreasesMagnitudeAndCapsAt256()
    {
        assertEquals(SageCommand.FF,
                DvdRemoteScanPolicy.resolve(SageCommand.FF, 1f));
        assertEquals(SageCommand.FF,
                DvdRemoteScanPolicy.resolve(SageCommand.FF, 8f));
        assertEquals(SageCommand.REW,
                DvdRemoteScanPolicy.resolve(SageCommand.REW, -8f));
        assertEquals(SageCommand.FASTER,
                DvdRemoteScanPolicy.resolve(SageCommand.FF, 16f));
        assertEquals(SageCommand.FASTER,
                DvdRemoteScanPolicy.resolve(SageCommand.REW, -16f));
        assertEquals(SageCommand.NONE, DvdRemoteScanPolicy.resolve(SageCommand.FF, 256f));
        assertEquals(SageCommand.NONE, DvdRemoteScanPolicy.resolve(SageCommand.REW, -256f));
    }

    @Test public void tapTargetsAndLogicalZeroNeverSendWireZero()
    {
        assertEquals(2f, DvdRemoteScanPolicy.tapTarget(SageCommand.FF, 1f), 0f);
        assertEquals(-2f, DvdRemoteScanPolicy.tapTarget(SageCommand.REW, 1f), 0f);
        assertEquals(256f, DvdRemoteScanPolicy.tapTarget(SageCommand.FF, 256f), 0f);
        assertEquals(-256f, DvdRemoteScanPolicy.tapTarget(SageCommand.REW, -256f), 0f);
        assertEquals(1f, DvdRemoteScanPolicy.tapTarget(SageCommand.REW, 2f), 0f);
        assertEquals(1f, DvdRemoteScanPolicy.tapTarget(SageCommand.FF, -2f), 0f);
        assertEquals(64f, DvdRemoteScanPolicy.tapTarget(SageCommand.REW, 128f), 0f);
        assertArrayEquals(new SageCommand[]{SageCommand.SLOWER},
                DvdRemoteScanPolicy.sequence(SageCommand.FF, -256f));
        org.junit.Assert.assertFalse(DvdRemoteScanPolicy.isHold(999));
        org.junit.Assert.assertTrue(DvdRemoteScanPolicy.isHold(1000));
    }
}
