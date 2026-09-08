package opensagetv.vibe.miniclient.android;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by seans on 12/10/15.
 */
public class MiniclientService extends Service {
    private static final Logger log = LoggerFactory.getLogger(MiniclientService.class);

    public MiniclientService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        log.debug("Starting MiniClient Service");
        try {
            MiniclientApplication.get(this).getClient();
        } catch (Throwable t) {
            log.error("Failed to start miniclient service", t);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log.debug("Stopping MiniClient Service");
        // A started background service is not the owner of the Application
        // singleton. Newer Fire OS releases may destroy this service while the
        // process and its foreground Activity remain alive. Shutting down the
        // MiniClient here permanently terminates its executors; a later
        // OPENURL in the still-running Activity then fails before playback can
        // start. Process death already reclaims these resources, while explicit
        // application teardown remains responsible for MiniClient.shutdown().
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // not used
        return null;
    }
}
