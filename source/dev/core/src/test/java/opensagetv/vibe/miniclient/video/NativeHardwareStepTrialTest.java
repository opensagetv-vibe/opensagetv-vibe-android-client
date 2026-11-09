package opensagetv.vibe.miniclient.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class NativeHardwareStepTrialTest
{
    @Test public void onlyNormalizedBasicAndActualNewOutputCanResetTheClockOrigin()
    {
        assertTrue(NativeHardwareStepTrial.trustNormalizedEpoch(true, false, true));
        assertFalse(NativeHardwareStepTrial.trustNormalizedEpoch(false, false, true));
        assertFalse(NativeHardwareStepTrial.trustNormalizedEpoch(true, true, true));
        assertFalse(NativeHardwareStepTrial.trustNormalizedEpoch(true, false, false));
    }
    @Test public void explicitNonpersistentTrialDoesNotClaimOtherPipelines()
    {
        NativeHardwareStepTrial.setEnabled(false);
        assertFalse(NativeHardwareStepTrial.eligible(true, false, "hardware", "armv7l"));
        try {
            NativeHardwareStepTrial.setEnabled(true);
            assertTrue(NativeHardwareStepTrial.eligible(true, false, "hardware", "armv7l"));
            assertTrue(NativeHardwareStepTrial.eligible(true, false, "hardware", "aarch64"));
            assertFalse(NativeHardwareStepTrial.eligible(false, false, "hardware", "armv7l"));
            assertFalse(NativeHardwareStepTrial.eligible(true, true, "hardware", "armv7l"));
            assertFalse(NativeHardwareStepTrial.eligible(true, false, "software", "armv7l"));
            assertFalse(NativeHardwareStepTrial.eligible(true, false, "hardware_preferred", "armv7l"));
            assertFalse(NativeHardwareStepTrial.eligible(true, false, "hardware", "x86_64"));
            assertFalse(NativeHardwareStepTrial.eligible(true, false, "hardware", null));
        } finally { NativeHardwareStepTrial.setEnabled(false); }
    }
}
