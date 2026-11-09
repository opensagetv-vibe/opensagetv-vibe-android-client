package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeCeaSampleGateTest
{
    private static final byte[] TEXT = {4, (byte) 0x94, (byte) 0xAD};

    @Test public void realDataOnly()
    {
        NativeCeaSampleGate gate = new NativeCeaSampleGate();
        assertFalse(gate.accept(1, 1, 0, new byte[]{4, (byte) 128, (byte) 128}));
        assertFalse(gate.accept(1, 1, 0, new byte[]{0, 65, 66}));
        assertFalse(gate.accept(1, 1, 0, new byte[]{6, 0, 0}));
        assertTrue(gate.accept(1, 1, 0, TEXT));
        assertTrue(gate.accept(1, 1, 100, new byte[]{7, 1, 2}));
    }

    @Test public void rejectsMalformedAndFutureContracts()
    {
        NativeCeaSampleGate gate = new NativeCeaSampleGate();
        assertFalse(gate.accept(2, 1, 0, TEXT));
        assertFalse(gate.accept(1, -1, 0, TEXT));
        assertFalse(gate.accept(1, 1, -1, TEXT));
        assertFalse(gate.accept(1, 1, Long.MAX_VALUE, TEXT));
        assertFalse(gate.accept(1, 1, 0, null));
        assertFalse(gate.accept(1, 1, 0, new byte[94]));
        assertFalse(gate.accept(1, 1, 0, new byte[4]));
    }

    @Test public void seekRequiresCompletionAndANewNativeSerial()
    {
        NativeCeaSampleGate gate = new NativeCeaSampleGate();
        assertTrue(gate.accept(1, 3, 100, TEXT));
        gate.beginSeek();
        assertFalse(gate.accept(1, 4, 100, TEXT));
        gate.completeSeek();
        assertFalse(gate.accept(1, 3, 100, TEXT));
        assertTrue(gate.accept(1, 4, 100, TEXT));
        assertFalse(gate.accept(1, 2, 100, TEXT));
        // B-frame presentation order is handled by the existing bridge, not
        // rejected just because timestamps arrive out of presentation order.
        assertTrue(gate.accept(1, 4, 50, TEXT));
    }

    @Test public void unopenedSeekAndCloseFailSafe()
    {
        NativeCeaSampleGate gate = new NativeCeaSampleGate();
        gate.beginSeek();
        assertFalse(gate.accept(1, 1, 0, TEXT));
        gate.completeSeek();
        assertTrue(gate.accept(1, 2, 0, TEXT));
        gate.close();
        gate.completeSeek();
        assertFalse(gate.accept(1, 3, 0, TEXT));
    }

    @Test public void lateFormatDoesNotRenumberExistingSelection()
    {
        NativeCeaSampleGate gate = new NativeCeaSampleGate();
        assertEquals(-1, gate.index608());
        assertEquals(-1, gate.index708());
        assertTrue(gate.accept(1, 1, 0, new byte[]{7, 1, 2}));
        assertEquals(0, gate.index708());
        assertTrue(gate.accept(1, 1, 10, TEXT));
        assertEquals(0, gate.index708());
        assertEquals(1, gate.index608());
        gate.beginSeek(); gate.completeSeek();
        assertEquals(-1, gate.index708());
        assertEquals(-1, gate.index608());
        assertFalse(gate.accept(1, 1, 0, TEXT));
        assertEquals(-1, gate.index608());
        assertTrue(gate.accept(1, 2, 0, new byte[]{7, 1, 2}));
        assertEquals(0, gate.index708());
        assertEquals(-1, gate.index608()); // replacement has no observed608 yet
    }
}
