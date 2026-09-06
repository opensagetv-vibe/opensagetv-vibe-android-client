/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package opensagetv.vibe.miniclient.android.tv;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.android.AddServerFragment.OnAddServerListener;
import opensagetv.vibe.miniclient.android.AutoConnectDialog;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.gdx.MiniClientGDXActivity;
import opensagetv.vibe.miniclient.android.opengl.MiniClientOpenGLActivity;
import opensagetv.vibe.miniclient.prefs.PrefStore;

/*
 * OpenSageTV Vibe TV launcher that loads MainFragment.
 */
public class MainActivity extends FragmentActivity implements OnAddServerListener {
    /**
     * Called when the activity is first created.
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (MiniclientApplication.get(this).getClient().properties().getBoolean(PrefStore.Keys.auto_connect_to_last_server, false)) {
            ServerInfo si = MiniclientApplication.get(this).getClient().getServers().getLastConnectedServer();
            if (si != null) {
                // show the connect dialog
                AutoConnectDialog dialog = new AutoConnectDialog();
                dialog.show(getSupportFragmentManager(), "autoconnect");
            }
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        MiniclientApplication app = MiniclientApplication.get(this);
        if (app.getClient().isConnected()
                && app.getBackgroundSessionOwner().hasPendingPreservedSession())
        {
            Class<?> renderer = app.getClient().properties().getBoolean(PrefStore.Keys.use_opengl_ui, true)
                    ? MiniClientOpenGLActivity.class : MiniClientGDXActivity.class;
            Intent resume = new Intent(this, renderer);
            resume.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(resume);
        }
    }

    @Override
    public void onAddServer(String name, String addr) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.main_browse_fragment);
        if (fragment instanceof OnAddServerListener) {
            ((OnAddServerListener) fragment).onAddServer(name, addr);
        }
    }

}
