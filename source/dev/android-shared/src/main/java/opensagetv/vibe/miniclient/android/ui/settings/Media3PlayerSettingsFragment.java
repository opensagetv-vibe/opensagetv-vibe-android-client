package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import android.text.Html;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import opensagetv.vibe.miniclient.android.R;
import opensagetv.vibe.miniclient.android.video.PlayerBackend;
import opensagetv.vibe.miniclient.media.AudioCodec;
import opensagetv.vibe.miniclient.media.VideoCodec;

/**
 * Settings and decoder information for the isolated Media3 backend.
 *
 * Media3 intentionally uses Android platform MediaCodec decoders only in v0.4.0.
 * The legacy ExoPlayer FFmpeg extension is not shared across the backend boundary.
 */
public class Media3PlayerSettingsFragment extends PreferenceFragmentCompat
{
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.media3player_prefs, rootKey);

        Preference decoders = findPreference("show_media3_decoders");
        decoders.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                showMedia3CodecInfo();
                return true;
            }
        });

        Preference version = findPreference("media3version");
        version.setSummary(PlayerBackend.MEDIA3_VERSION);
    }

    private void showMedia3CodecInfo()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Media3 MediaCodecs");
        StringBuilder sb = new StringBuilder();

        sb.append("<B>Video Decoders</B><br/>\n");
        for (VideoCodec codec : VideoCodec.values())
        {
            sb.append(codec.getName()).append(": ")
                    .append(getDecoderName(codec.getAndroidMimeType()))
                    .append("<br/>\n");
        }

        sb.append("<B>Audio Decoders</B><br/>\n");
        for (AudioCodec codec : AudioCodec.values())
        {
            sb.append(codec.getName()).append(": ")
                    .append(getDecoderName(codec.getAndroidMimeType()))
                    .append("<br/>\n");
        }

        sb.append("<br/><i>Media3 v0.4 backend uses Android platform decoders only.</i>");
        builder.setMessage(Html.fromHtml(sb.toString()));
        builder.setCancelable(true);
        builder.show();
    }

    private String getDecoderName(String mimeType)
    {
        try
        {
            MediaCodecInfo info = MediaCodecUtil.getDecoderInfo(mimeType, false, false);
            return info == null ? "<i>Unsupported</i>" : info.name;
        }
        catch (Exception ex)
        {
            return "<i>Unsupported</i>";
        }
    }
}
