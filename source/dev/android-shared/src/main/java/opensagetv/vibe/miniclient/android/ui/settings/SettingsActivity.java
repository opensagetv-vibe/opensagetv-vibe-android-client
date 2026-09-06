package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;

import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.R;

public class SettingsActivity extends AppCompatActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);
        AppUtil.hideSystemUIOnTV(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUtil.hideSystemUIOnTV(this);
    }
}
