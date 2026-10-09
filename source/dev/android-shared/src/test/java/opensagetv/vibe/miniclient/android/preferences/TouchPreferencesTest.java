package opensagetv.vibe.miniclient.android.preferences;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TouchPreferencesTest {
    private TouchPreferences preferences(final Map<String, String> values) {
        PrefStore store = (PrefStore) Proxy.newProxyInstance(
                PrefStore.class.getClassLoader(), new Class<?>[] {PrefStore.class},
                (proxy, method, args) -> {
                    // Resolving a default must never migrate/write user settings.
                    if (!"getString".equals(method.getName()) || args.length != 2)
                        throw new AssertionError("Unexpected preference operation: " + method.getName());
                    return values.containsKey(args[0]) ? values.get(args[0]) : args[1];
                });
        return new TouchPreferences(store);
    }

    @Test public void absentSingleFingerHoldOpensClientNavigationWithoutWriting() {
        Map<String, String> values = new HashMap<>();
        assertEquals(SageCommand.NAV_OSD, preferences(values).getLongPress());
        assertTrue(values.isEmpty());
    }

    @Test public void explicitLegacyOptionsRemainsTheUserChoice() {
        Map<String, String> values = new HashMap<>();
        values.put("long_press", SageCommand.OPTIONS.getKey());
        assertEquals(SageCommand.OPTIONS, preferences(values).getLongPress());
        assertEquals(1, values.size());
    }

    @Test public void disabledAndCustomMappingsAreNotReplaced() {
        Map<String, String> values = new HashMap<>();
        values.put("long_press", SageCommand.NONE.getKey());
        assertEquals(SageCommand.NONE, preferences(values).getLongPress());
        values.put("long_press", SageCommand.INFO.getKey());
        assertEquals(SageCommand.INFO, preferences(values).getLongPress());
    }

    @Test public void multiFingerDefaultsAndExplicitChoicesAreUnchanged() {
        Map<String, String> values = new HashMap<>();
        TouchPreferences prefs = preferences(values);
        assertEquals(SageCommand.NONE, prefs.getDoubleLongPress());
        assertEquals(SageCommand.NONE, prefs.getTripleLongPress());
        values.put("long_press_2", SageCommand.OPTIONS.getKey());
        values.put("long_press_3", SageCommand.KEYBOARD_OSD.getKey());
        assertEquals(SageCommand.OPTIONS, prefs.getDoubleLongPress());
        assertEquals(SageCommand.KEYBOARD_OSD, prefs.getTripleLongPress());
    }
}
