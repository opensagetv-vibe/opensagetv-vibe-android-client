package opensagetv.vibe.miniclient;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class OrderedListenerRegistryTest
{
    @Test
    public void dispatchesInRegistrationOrderAndIgnoresDuplicateRegistration()
    {
        OrderedListenerRegistry<String> registry = new OrderedListenerRegistry<String>();
        registry.register("first");
        registry.register("second");
        registry.register("first");
        final List<String> calls = new ArrayList<String>();
        registry.dispatch(new OrderedListenerRegistry.Dispatcher<String>()
        {
            @Override public void dispatch(String listener) { calls.add(listener); }
        });
        assertEquals(Arrays.asList("first", "second"), calls);
        assertEquals(2, registry.size());
    }

    @Test
    public void unregisterAffectsTheNextSnapshotWithoutReorderingSurvivors()
    {
        OrderedListenerRegistry<String> registry = new OrderedListenerRegistry<String>();
        registry.register("first");
        registry.register("second");
        registry.register("third");
        registry.unregister("second");
        final List<String> calls = new ArrayList<String>();
        registry.dispatch(new OrderedListenerRegistry.Dispatcher<String>()
        {
            @Override public void dispatch(String listener) { calls.add(listener); }
        });
        assertEquals(Arrays.asList("first", "third"), calls);
    }
}
