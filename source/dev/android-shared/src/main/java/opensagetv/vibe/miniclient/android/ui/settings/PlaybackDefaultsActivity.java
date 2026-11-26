package opensagetv.vibe.miniclient.android.ui.settings;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Startup-only device defaults; never applies a change to an active player. */
public final class PlaybackDefaultsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(state==null)getSupportFragmentManager().beginTransaction().replace(android.R.id.content,new PlaybackDefaultsFragment()).commit();
    }
}
