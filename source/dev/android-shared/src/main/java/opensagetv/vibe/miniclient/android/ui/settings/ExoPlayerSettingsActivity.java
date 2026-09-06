package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.R;

public class ExoPlayerSettingsActivity extends AppCompatActivity
{
        @Override
        protected void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            AppUtil.hideSystemUIOnTV(this);
            setContentView(R.layout.activity_exoplayer_settings);
        }

        @Override
        protected void onResume()
        {
            super.onResume();
            AppUtil.hideSystemUIOnTV(this);

        }

}
