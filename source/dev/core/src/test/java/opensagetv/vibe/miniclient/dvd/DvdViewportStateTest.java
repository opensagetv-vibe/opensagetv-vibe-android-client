package opensagetv.vibe.miniclient.dvd;

import org.junit.Test;
import opensagetv.vibe.miniclient.uibridge.Rectangle;
import static org.junit.Assert.*;

public class DvdViewportStateTest {
    @Test public void previewSurvivesRendererReplacementWithoutMutableAliases() {
        DvdViewportState state = new DvdViewportState();
        Rectangle source = new Rectangle(0,0,720,480);
        Rectangle target = new Rectangle(1193,124,610,320);
        state.set(source,target); target.width = 1920;
        Rectangle[] first = state.snapshot();
        assertEquals(610,first[1].width); assertEquals(1193,first[1].x);
        first[1].width = 10;
        assertEquals(610,state.snapshot()[1].width);
        assertEquals(720,state.snapshot()[0].width);
    }
    @Test public void latestServerLayoutWinsAndSessionEndClearsCustody() {
        DvdViewportState state = new DvdViewportState();
        assertNull(state.snapshot());
        state.set(new Rectangle(0,0,720,480),new Rectangle(0,0,1920,1080));
        state.set(new Rectangle(0,0,720,480),new Rectangle(1193,124,610,320));
        assertEquals(610,state.snapshot()[1].width);
        state.clear(); assertNull(state.snapshot());
    }
}
