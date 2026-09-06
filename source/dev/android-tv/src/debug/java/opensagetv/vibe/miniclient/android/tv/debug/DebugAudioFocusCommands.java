package opensagetv.vibe.miniclient.android.tv.debug;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

/** Bounded debug-only competing focus owner used by the physical MCP gate. */
final class DebugAudioFocusCommands
{
    private static AudioManager audioManager;
    private static AudioFocusRequest audioFocusRequest;
    private static AudioManager.OnAudioFocusChangeListener listener;

    private DebugAudioFocusCommands()
    {
    }

    static synchronized String request(Context context, Intent intent)
    {
        abandon(context);
        String mode = DebugValueParser.clean(intent.getStringExtra("mode"));
        int gain;
        if ("transient".equals(mode))
            gain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
        else if ("duck".equals(mode))
            gain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
        else if ("permanent".equals(mode))
            gain = AudioManager.AUDIOFOCUS_GAIN;
        else
            throw new IllegalArgumentException("mode_must_be_transient_duck_or_permanent");

        audioManager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null)
            throw new IllegalStateException("audio_manager_unavailable");
        listener = new AudioManager.OnAudioFocusChangeListener()
        {
            @Override
            public void onAudioFocusChange(int focusChange)
            {
            }
        };

        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(gain)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(listener)
                    .build();
            result = audioManager.requestAudioFocus(audioFocusRequest);
        }
        else
        {
            result = audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, gain);
        }
        return "op=audio_focus_request;mode=" + DebugValueParser.safe(mode)
                + ";granted=" + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    static synchronized String abandon(Context context)
    {
        int result = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (audioManager != null && listener != null)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null)
                result = audioManager.abandonAudioFocusRequest(audioFocusRequest);
            else
                result = audioManager.abandonAudioFocus(listener);
        }
        audioManager = null;
        audioFocusRequest = null;
        listener = null;
        return "op=audio_focus_abandon;abandoned="
                + (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }
}
