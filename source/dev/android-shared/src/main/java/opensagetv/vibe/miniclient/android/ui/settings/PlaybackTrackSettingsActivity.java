package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.R;

/** Audio-language and caption-service preferences shared by all capable players. */
public class PlaybackTrackSettingsActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        AppUtil.hideSystemUIOnTV(this);
        setContentView(R.layout.activity_playback_track_settings);
        if (savedInstanceState == null)
        {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.playback_track_settings_container,
                            new PlaybackTrackSettingsFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        AppUtil.hideSystemUIOnTV(this);
    }
}
