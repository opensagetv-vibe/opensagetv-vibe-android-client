package opensagetv.vibe.miniclient.android.video.exoplayer2;

import org.junit.Test;
import com.google.android.exoplayer2.upstream.DataSource;
import static org.junit.Assert.*;

public class Exo2MimDirectHttpDataSourceTest
{
    @Test public void observesOnlyDirectTransportSegments()
    {
        assertTrue(Exo2MimDirectHttpDataSource.shouldObservePath(
                "/v1/direct/media/token/seg_000001.ts"));
        assertFalse(Exo2MimDirectHttpDataSource.shouldObservePath(
                "/v1/direct/media/token/stream.m3u8"));
        assertFalse(Exo2MimDirectHttpDataSource.shouldObservePath("/video.ts"));
        assertFalse(Exo2MimDirectHttpDataSource.shouldObservePath(null));
    }

    @Test public void reportsTheActualFactoryCreatedDataSource()
    {
        final DataSource[] observed = new DataSource[1];
        DataSource created = Exo2MimDirectHttpDataSource.factory(
                new Exo2MimDirectHttpDataSource.CreationListener()
                {
                    @Override public void onCreated(DataSource source)
                    { observed[0] = source; }
                }).createDataSource();
        assertNotNull(created);
        assertSame(created, observed[0]);
        assertTrue(created instanceof Exo2MimDirectHttpDataSource);
    }
}
