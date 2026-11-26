package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import opensagetv.vibe.miniclient.android.AppUtil;

public final class PushSettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppUtil.hideSystemUIOnTV(this);
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PushSettingsFragment()).commit();
    }
    @Override protected void onResume() { super.onResume(); AppUtil.hideSystemUIOnTV(this); }
}
