package sagex.miniclient.android.ui.settings;

import android.app.Activity;
import android.os.Bundle;

import sagex.miniclient.android.AppUtil;
import sagex.miniclient.android.R;

/** Settings/information page for the AndroidX Media3 backend. */
public class Media3PlayerSettingsActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        AppUtil.hideSystemUIOnTV(this);
        setContentView(R.layout.activity_media3player_settings);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        AppUtil.hideSystemUIOnTV(this);
    }
}
