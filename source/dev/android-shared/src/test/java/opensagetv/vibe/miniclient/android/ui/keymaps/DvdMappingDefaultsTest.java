package opensagetv.vibe.miniclient.android.ui.keymaps;

import java.lang.reflect.Proxy;
import org.junit.Test;
import opensagetv.vibe.miniclient.android.preferences.MediaMappingPreferences;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import static org.junit.Assert.*;

public class DvdMappingDefaultsTest
{
    private MediaMappingPreferences preferences(final boolean storedOff)
    {
        PrefStore store = (PrefStore) Proxy.newProxyInstance(PrefStore.class.getClassLoader(),
                new Class<?>[]{PrefStore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBoolean") && args.length == 2)
                        return storedOff ? false : args[1];
                    throw new AssertionError("Unexpected preference mutation/read: " + method);
                });
        return new MediaMappingPreferences("dvdplaying", store);
    }

    @Test public void cleanPreferencesEnableBothDvdPresetsByDefault()
    {
        assertTrue(preferences(false).isDvdHeldArrowControlsEnabled());
        assertTrue(preferences(false).isDvdScanHoldControlsEnabled());
    }

    @Test public void existingDisabledChoicesRemainDisabled()
    {
        assertFalse(preferences(true).isDvdHeldArrowControlsEnabled());
        assertFalse(preferences(true).isDvdScanHoldControlsEnabled());
    }
}
