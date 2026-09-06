package opensagetv.vibe.miniclient.android.video.media3;

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

import opensagetv.vibe.miniclient.net.HasClose;
import opensagetv.vibe.miniclient.net.HasPushBuffer;
import opensagetv.vibe.miniclient.net.PushBufferDataSource;

/**
 * Created by seans on 08/12/15.
 */
public class Media3PushDataSource extends PushBufferDataSource implements DataSource, HasPushBuffer, HasClose {
    private static final Logger log = LoggerFactory.getLogger(Media3PushDataSource.class);
    private Uri uri;

    public Media3PushDataSource() {
        log.debug("ExoNative datasource being created.");
    }

    public DataSource.Factory readerFactory(final long generation)
    {
        return new DataSource.Factory()
        {
            @Override
            public DataSource createDataSource()
            {
                return new ReaderLease(Media3PushDataSource.this, generation);
            }
        };
    }

    public void activateReaderGeneration(long generation)
    {
        activateReadGeneration(generation);
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

    private static final class ReaderLease implements DataSource
    {
        private final Media3PushDataSource owner;
        private final long generation;

        ReaderLease(Media3PushDataSource owner, long generation)
        {
            this.owner = owner;
            this.generation = generation;
        }

        @Override
        public void addTransferListener(TransferListener transferListener)
        {
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException
        {
            return owner.open(dataSpec);
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException
        {
            return owner.readBlockingForGeneration(generation, 0, buffer, offset, readLength);
        }

        @Override
        public Uri getUri()
        {
            return owner.getUri();
        }

        @Override
        public Map<String, List<String>> getResponseHeaders()
        {
            return owner.getResponseHeaders();
        }

        @Override
        public void close()
        {
            // This object is only a Media3 loader lease. The owner is the
            // long-lived SageTV PUSH session and is reused by the next DVD
            // reader generation after FLUSH. Closing the owner here makes the
            // replacement extractor observe immediate EOF before it can parse
            // the next cell.
            owner.releaseReadGeneration(generation);
        }
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
