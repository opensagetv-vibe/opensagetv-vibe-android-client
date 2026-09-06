package opensagetv.vibe.miniclient.android.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lifecycle-owned audio focus with stale-callback and user-pause protection. */
public final class AudioFocusController implements AudioManager.OnAudioFocusChangeListener
{
    public interface PlaybackCallbacks
    {
        boolean isPlaying();
        void pauseForFocusLoss();
        void resumeAfterFocusGain();
    }

    private static final Logger log = LoggerFactory.getLogger(AudioFocusController.class);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PlaybackCallbacks playbackCallbacks;
    private boolean focusGranted;
    private boolean resumeOnGain;
    private int generation;

    public boolean request(Context context, PlaybackCallbacks callbacks)
    {
        abandon();
        if (context == null || callbacks == null)
            return false;

        AudioManager manager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        if (manager == null)
            return false;

        int result;
        synchronized (this)
        {
            generation++;
            audioManager = manager;
            playbackCallbacks = callbacks;
            resumeOnGain = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(this, mainHandler)
                        .build();
                result = manager.requestAudioFocus(audioFocusRequest);
            }
            else
            {
                result = manager.requestAudioFocus(
                        this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
            focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        if (focusGranted)
            log.debug("Audio focus granted");
        else
            log.warn("Audio focus request was not granted");
        return focusGranted;
    }

    public void abandon()
    {
        AudioManager manager;
        AudioFocusRequest request;
        synchronized (this)
        {
            generation++;
            manager = audioManager;
            request = audioFocusRequest;
            audioManager = null;
            audioFocusRequest = null;
            playbackCallbacks = null;
            focusGranted = false;
            resumeOnGain = false;
        }
        if (manager == null)
            return;
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && request != null)
                manager.abandonAudioFocusRequest(request);
            else
                manager.abandonAudioFocus(this);
        }
        catch (Throwable t)
        {
            log.warn("Failed to abandon audio focus", t);
        }
    }

    @Override
    public void onAudioFocusChange(final int focusChange)
    {
        final int callbackGeneration;
        synchronized (this)
        {
            callbackGeneration = generation;
        }
        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                dispatchFocusChange(callbackGeneration, focusChange);
            }
        });
    }

    private void dispatchFocusChange(int callbackGeneration, int focusChange)
    {
        PlaybackCallbacks callbacks;
        synchronized (this)
        {
            if (callbackGeneration != generation || playbackCallbacks == null)
                return;
            callbacks = playbackCallbacks;
        }

        try
        {
            switch (focusChange)
            {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    boolean wasPlaying = callbacks.isPlaying();
                    synchronized (this)
                    {
                        resumeOnGain = wasPlaying;
                    }
                    if (wasPlaying)
                        callbacks.pauseForFocusLoss();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    synchronized (this)
                    {
                        resumeOnGain = false;
                        focusGranted = false;
                    }
                    if (callbacks.isPlaying())
                        callbacks.pauseForFocusLoss();
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    boolean shouldResume;
                    synchronized (this)
                    {
                        shouldResume = resumeOnGain;
                        resumeOnGain = false;
                        focusGranted = true;
                    }
                    if (shouldResume)
                        callbacks.resumeAfterFocusGain();
                    break;
                default:
                    break;
            }
        }
        catch (Throwable t)
        {
            log.warn("Failed to apply audio focus change {}", focusChange, t);
        }
    }

    public synchronized boolean hasFocus()
    {
        return focusGranted;
    }
}
