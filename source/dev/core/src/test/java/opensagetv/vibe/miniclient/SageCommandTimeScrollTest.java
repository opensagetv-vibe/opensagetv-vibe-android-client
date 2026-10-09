package opensagetv.vibe.miniclient;

import org.junit.Test;
import static org.junit.Assert.*;

public class SageCommandTimeScrollTest
{
    @Test public void timeScrollUsesExistingStockEventAndExplicitKey()
    {
        assertEquals(UserEvent.TIME_SCROLL, SageCommand.TIME_SCROLL.getEventCode());
        assertEquals(10, SageCommand.TIME_SCROLL.getEventCode());
        assertEquals(SageCommand.TIME_SCROLL, SageCommand.parseByKey("time_scroll"));
    }
}
