package opensagetv.vibe.miniclient.android.video;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PlayerFactoryDvdPolicyTest
{
    @Test public void everyPlayerSelectionUsesSharedNativeDvdEngine()
    {
        for (PlayerBackend requested : PlayerBackend.values())
            for (String url : new String[]{"push:dvd", "stv://server/push:dvd"})
                assertEquals(PlayerBackend.MEDIA3, PlayerFactory.resolveForUrl(requested, url));
    }

    @Test public void ordinaryVideoKeepsEachSavedPlayerSelection()
    {
        for (PlayerBackend requested : PlayerBackend.values())
            assertEquals(requested, PlayerFactory.resolveForUrl(requested, "http://server/video.ts"));
    }
}
