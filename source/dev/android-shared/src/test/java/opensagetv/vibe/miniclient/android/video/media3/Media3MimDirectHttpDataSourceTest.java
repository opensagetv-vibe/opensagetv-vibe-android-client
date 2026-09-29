package opensagetv.vibe.miniclient.android.video.media3;

import org.junit.Test;
import androidx.media3.datasource.DataSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

public class Media3MimDirectHttpDataSourceTest
{
    @Test public void observesOnlyDirectTransportSegments()
    {
        assertTrue(Media3MimDirectHttpDataSource.shouldObservePath(
                "/v1/direct/media/token/seg_000001.ts"));
        assertFalse(Media3MimDirectHttpDataSource.shouldObservePath(
                "/v1/direct/media/token/stream.m3u8"));
        assertFalse(Media3MimDirectHttpDataSource.shouldObservePath("/video.ts"));
        assertFalse(Media3MimDirectHttpDataSource.shouldObservePath(null));
    }

    @Test public void reportsTheActualFactoryCreatedDataSource()
    {
        final DataSource[] observed = new DataSource[1];
        DataSource created = Media3MimDirectHttpDataSource.factory(
                new Media3MimDirectHttpDataSource.CreationListener()
                {
                    @Override public void onCreated(DataSource dataSource)
                    { observed[0] = dataSource; }
                }).createDataSource();
        assertNotNull(created);
        assertTrue(created == observed[0]);
        assertTrue(created instanceof Media3MimDirectHttpDataSource);
    }
}
