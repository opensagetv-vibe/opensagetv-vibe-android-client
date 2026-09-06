package opensagetv.vibe.miniclient.android;

import android.view.KeyEvent;

import opensagetv.vibe.miniclient.util.StaticFieldMapper;

/**
 * Created by seans on 22/11/15.
 */
public class AndroidKeyEventMapper extends StaticFieldMapper<Integer> {
    public AndroidKeyEventMapper() {
        super(KeyEvent.class, "KEYCODE", true);
    }
}
