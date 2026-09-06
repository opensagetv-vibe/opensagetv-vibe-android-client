package opensagetv.vibe.miniclient.android.phone;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.FragmentActivity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.ServerDiscovery;
import opensagetv.vibe.miniclient.ServerInfo;
import opensagetv.vibe.miniclient.android.AddServerFragment;
import opensagetv.vibe.miniclient.android.AddServerFragment.OnAddServerListener;
import opensagetv.vibe.miniclient.android.AppUtil;
import opensagetv.vibe.miniclient.android.AutoConnectDialog;
import opensagetv.vibe.miniclient.android.HelpDialog;
import opensagetv.vibe.miniclient.android.MiniclientApplication;
import opensagetv.vibe.miniclient.android.gdx.MiniClientGDXActivity;
import opensagetv.vibe.miniclient.android.opengl.MiniClientOpenGLActivity;
import opensagetv.vibe.miniclient.android.ui.settings.SettingsActivity;
import opensagetv.vibe.miniclient.android.tv.MainActivity;
import opensagetv.vibe.miniclient.android.tv.R;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.prefs.PrefStore.Keys;

/**
 * Created by seans on 20/09/15.
 */
public class ServersActivity extends FragmentActivity implements OnAddServerListener {
    private static final Logger log = LoggerFactory.getLogger(ServersActivity.class);

    RecyclerView list;
    View header;
    ImageView addServerButton;
    ImageView settingsButton;

    ServersAdapter adapter = null;
    boolean paused = true;

    public ServersActivity() {
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.servers_layout);
        AppUtil.hideSystemUIOnTV(this);

        if (getResources().getBoolean(R.bool.istv)) {
            // server activity started on a TV
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                Intent i = new Intent(this, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                finish();
                return;
            }
        }

        if (MiniclientApplication.get().getClient().properties().getBoolean(Keys.use_tv_ui_on_tablet, false)) {
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
            return;
        }

        list = findViewById(R.id.list);
        header = findViewById(R.id.header);
        addServerButton = findViewById(R.id.btn_add_server);
        settingsButton = findViewById(R.id.btn_settings);

        settingsButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                gotoSettingsAction();
            }
        });

        findViewById(R.id.btn_help).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onhelp();
            }
        });

        addServerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                addServerAction();
            }
        });

        // now show the server selector dialog
        adapter = new ServersAdapter(this);

        //list.setFocusable(true);
        //list.requestFocus();
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        list.setHasFixedSize(true);
        list.setAdapter(adapter);

        paused = false;

        header.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {

            }
        });


//        Drawable addIcon = new IconicsDrawable(this)
//                .icon(GoogleMaterial.Icon.gmd_collection_add)
//                .color(Color.RED)
//                .sizeDp(24);
//        addServerButton.setImageDrawable(addIcon);
//
//        Drawable settingsIcon = new IconicsDrawable(this)
//                .icon(GoogleMaterial.Icon.gmd_settings)
//                .color(Color.RED)
//                .sizeDp(24);
//        settingsButton.setImageDrawable(settingsIcon);


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
    protected void onResume() {
        super.onResume();
        MiniclientApplication app = MiniclientApplication.get(this);
        if (app.getClient().isConnected()
                && app.getBackgroundSessionOwner().hasPendingPreservedSession()) {
            Class<?> renderer = app.getClient().properties().getBoolean(Keys.use_opengl_ui, true)
                    ? MiniClientOpenGLActivity.class : MiniClientGDXActivity.class;
            Intent resume = new Intent(this, renderer);
            resume.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(resume);
            return;
        }
        paused = false;
        refreshServers();
        AppUtil.hideSystemUIOnTV(this);
    }

    @Override
    protected void onPause() {
        paused = true;
        MiniclientApplication.get(this).getClient().getServerDiscovery().close();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        paused = true;
        super.onDestroy();
    }

    public void refreshServers() {
        // refresh the data in case last connected changed, etc
        adapter.notifyDataSetChanged();

        log.debug("Looking for Servers...");
        MiniclientApplication.get(this).getClient().getServerDiscovery().discoverServersAsync(10000, new ServerDiscovery.ServerDiscoverCallback() {
            @Override
            public void serverDiscovered(final ServerInfo si) {
                if (!paused) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.addServer(si);
                        }
                    });
                }
            }
        });
    }

    // @OnClick(R.id.btn_settings)
    public void gotoSettingsAction() {
        Intent i = new Intent(getBaseContext(), SettingsActivity.class);
        startActivity(i);
    }

    // @OnClick(R.id.btn_help)
    public void onhelp() {
        HelpDialog.showDialog(this);
    }

    // @OnClick(R.id.btn_add_server)
    public void addServerAction() {
        // add new server
        AddServerFragment f = AddServerFragment.newInstance("My Server", "");

        f.setRetainInstance(true);
        f.show(getSupportFragmentManager(), "addserver");
    }

    public void deleteServer(final ServerInfo serverInfo) {
        adapter.items.remove(serverInfo);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onAddServer(String name, String addr) {
        if (addr != null && addr.trim().length() > 0) {
            ServerInfo si = new ServerInfo();
            si.name = name;
            si.address = addr;
            MiniclientApplication.get(this).getClient().getServers().saveServer(si);
            adapter.addServer(si);
            Toast.makeText(this, "Server Added", Toast.LENGTH_LONG).show();
        }
    }

}
