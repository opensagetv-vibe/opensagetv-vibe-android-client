package opensagetv.vibe.miniclient.android.video;

import android.content.Context;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.session.MediaSessionCompat;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.EventRouter;

/**
 * This class handles the events from the Android Media Session commands and transfers them back
 * to SageTV.  This is meant to be used with all Player implementations
 */
public class MediaSessionCallbackHandler extends MediaSessionCompat.Callback
{
    private MiniPlayerPlugin player;
    private MiniClient client;
    private Context context;

    public MediaSessionCallbackHandler(MiniPlayerPlugin player, MiniClient client, Context context)
    {
        this.player = player;
        this.client = client;
        this.context = context;
    }

    @Override
    public void onCommand(String command, Bundle extras, ResultReceiver cb)
    {
        super.onCommand(command, extras, cb);
    }

    @Override
    public void onPlay()
    {
        super.onPlay();
        EventRouter.postCommand(client, SageCommand.PLAY);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (backgroundOwnerWillOwnMediaSessionPause())
            return;
        EventRouter.postCommand(client, SageCommand.PAUSE);
    }

    private boolean backgroundOwnerWillOwnMediaSessionPause()
    {
        if (client == null || client.properties() == null
                || !client.properties().getBoolean(
                        PrefStore.Keys.keep_session_in_background, false))
            return false;
        MiniclientApplication application = MiniclientApplication.get(context);
        // Active Fire-TV media keys are mapped by MiniClientKeyListener. Fire
        // OS also invokes MediaSession.onPause as a side effect of HOME before
        // normal Activity lifecycle callbacks. With preservation enabled the
        // single background owner must decide PAUSE/PLAY; forwarding this
        // second path would misclassify HOME as an explicit user pause.
        return application != null && application.getBackgroundSessionOwner() != null;
    }

    @Override
    public void onSkipToNext()
    {
        super.onSkipToNext();
        EventRouter.postCommand(client, SageCommand.FF);
    }

    @Override
    public void onSkipToPrevious()
    {
        super.onSkipToPrevious();
        EventRouter.postCommand(client, SageCommand.REW);
    }

    @Override
    public void onFastForward()
    {
        super.onFastForward();
        EventRouter.postCommand(client, SageCommand.FF);
    }

    @Override
    public void onRewind()
    {
        super.onRewind();
        EventRouter.postCommand(client, SageCommand.REW);
    }

    @Override
    public void onStop()
    {
        super.onStop();
        EventRouter.postCommand(client, SageCommand.STOP);
    }

    @Override
    public void onSeekTo(long pos)
    {
        super.onSeekTo(pos);
        player.seek(pos);
    }

    @Override
    public void onSetCaptioningEnabled(boolean enabled)
    {
        super.onSetCaptioningEnabled(enabled);
        //TODO: Set first english closed caption/subtitle
    }
}
