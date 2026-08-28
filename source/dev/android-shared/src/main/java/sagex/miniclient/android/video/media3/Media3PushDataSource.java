package sagex.miniclient.android.video.media3;

import android.net.Uri;

import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sagex.miniclient.net.HasClose;
import sagex.miniclient.net.HasPushBuffer;
import sagex.miniclient.net.PushBufferDataSource;

/**
 * Created by seans on 08/12/15.
 */
public class Media3PushDataSource extends PushBufferDataSource implements DataSource, HasPushBuffer, HasClose {
    private static final Logger log = LoggerFactory.getLogger(Media3PushDataSource.class);
    private Uri uri;

    public Media3PushDataSource() {
        log.debug("ExoNative datasource being created.");
    }
    
    
    @Override
    public void addTransferListener(TransferListener transferListener)
    {
    
    }
    
    @Override
    public long open(DataSpec dataSpec) throws IOException {
        this.uri=dataSpec.uri;
        open(dataSpec.uri.toString());
        return C.LENGTH_UNSET;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException
    {
        return readBlocking(0, buffer, offset, readLength);
    }

    @Override
    public Uri getUri() {
        return uri;
    }
    
    @Override
    public Map<String, List<String>> getResponseHeaders()
    {
        return new HashMap<String, List<String>>();
        //return null;
    }


//    @Override
//    public Map<String, List<String>> getResponseHeaders() {
//        return Collections.emptyMap();
//    }
//
//    @Override
//    public void addTransferListener(TransferListener transferListener) {
//    }
}
