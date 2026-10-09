package opensagetv.vibe.miniclient.android.ui.keymaps;

import org.junit.Test;
import static org.junit.Assert.*;

public class DvdInputContextTest
{
    @Test public void fullScreenAndLetterboxingRetainDvdControls()
    {
        assertTrue(DvdInputContext.allowsControls("MediaPlayer OSD", null, 1920,1080,1920,1080));
        assertTrue(DvdInputContext.allowsControls("DVD OSD", null, 1440,1080,1920,1080));
        assertTrue(DvdInputContext.allowsControls("DVD OSD", null, 1920,810,1920,1080));
    }

    @Test public void previewRejectsEvenAStaleFullScreenHint()
    {
        assertFalse(DvdInputContext.allowsControls("MediaPlayer OSD", null, 408,322,1920,1080));
        assertFalse(DvdInputContext.allowsControls("Main Menu", null, 1920,1080,1920,1080));
    }

    @Test public void popupsAndUnknownGeometryKeepNormalStvNavigation()
    {
        assertFalse(DvdInputContext.allowsControls("DVD OSD", "Options", 1920,1080,1920,1080));
        assertFalse(DvdInputContext.allowsControls("DVD OSD", null, 0,0,1920,1080));
        assertFalse(DvdInputContext.allowsControls(null, null, 1920,1080,1920,1080));
    }
}
