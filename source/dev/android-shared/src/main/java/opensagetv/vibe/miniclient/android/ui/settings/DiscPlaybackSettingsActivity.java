package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import opensagetv.vibe.miniclient.android.AppUtil;

/** Dedicated settings screen for authored-disc and DVD playback. */
public final class DiscPlaybackSettingsActivity extends AppCompatActivity
{
    @Override protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        AppUtil.hideSystemUIOnTV(this);
        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new DiscPlaybackSettingsFragment())
                    .commit();
    }

    @Override protected void onResume()
    {
        super.onResume();
        AppUtil.hideSystemUIOnTV(this);
    }
}
