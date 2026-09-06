package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.android.events.ToggleAspectRatioEvent;
import opensagetv.vibe.miniclient.events.VideoInfoRefresh;
import opensagetv.vibe.miniclient.util.AspectHelper;
import opensagetv.vibe.miniclient.video.HasVideoInfo;
import opensagetv.vibe.miniclient.video.VideoInfoResponse;

/**
 * Created by seans on 05/12/15.
 */
public class VideoInfoDialog extends Dialog implements VibeEventListener {
    static final Logger log = LoggerFactory.getLogger(VideoInfoDialog.class);
    private final MiniClient client;
    private View navView;
    private boolean eventBusRegistered;


    public VideoInfoDialog(Activity activity) {
        super(activity, R.style.Theme_Dialog_DoNotDim);
        this.client = MiniclientApplication.get().getClient();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(true);
        navView = LayoutInflater.from(getContext()).inflate(R.layout.video_info, null, false);
        setContentView(navView);

        refresh(VideoInfoRefresh.INSTANCE);

        connect(R.id.vib_Close, new Runnable() {
            public void run() {
                dismiss();
            }
        });

        connect(R.id.vib_refresh, new Runnable() {
            public void run() {
                refresh(VideoInfoRefresh.INSTANCE);
            }
        });

        connect(R.id.vib_toggleAR, new Runnable() {
            @Override
            public void run() {
                onToggleAspectRatio();
            }
        });

        connect(R.id.vib_set16_9, new Runnable() {
            @Override
            public void run() {
                client.getUIRenderer().setUIAspectRatio(AspectHelper.ar_16_9);
            }
        });

        connect(R.id.vib_set4_3, new Runnable() {
            @Override
            public void run() {
                client.getUIRenderer().setUIAspectRatio(AspectHelper.ar_4_3);
            }
        });

        connect(R.id.vib_set2_dot_4, new Runnable() {
            @Override
            public void run() {
                client.getUIRenderer().setUIAspectRatio(2.4f);
            }
        });

        setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismiss();
                    return true;
                }
                return false;
            }
        });
    }

    public void refresh(VideoInfoRefresh refresh)
    {
        VideoInfoResponse resp = ((HasVideoInfo) client.getUIRenderer()).getVideoInfo();

        log.debug("Got a Request for Video Info", resp);

        if (resp!=null)
        {
            if (resp.videoInfo != null)
            {
                setText(R.id.vi_videoSize, resp.videoInfo.size);
                setText(R.id.vi_aspectMode, resp.videoInfo.aspectMode);
                setText(R.id.vi_videoPixelAspect, resp.videoInfo.size.getAR());
                setText(R.id.vi_aspectRatio, resp.videoInfo.aspectRatio);
                setText(R.id.vi_sagetvDestRect, resp.videoInfo.destRect);
            }
            setText(R.id.vi_sagetvScreenAspect, resp.uiAspectRatio);
            if (resp.uiScreenSizePixels != null)
            {
                setText(R.id.vi_screenAdjustedSize, resp.uiScreenSizePixels.copy().updateHeightUsingAspectRatio(resp.uiAspectRatio));
                setText(R.id.vi_screenPixelAR, resp.uiScreenSizePixels.getAR());
                setText(R.id.vi_screenPixelSize, resp.uiScreenSizePixels);
            }
            setText(R.id.vi_uri, resp.uri);
        }
    }

    public void connect(int id, final Runnable runnable) {
        Button b = (Button) navView.findViewById(id);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    runnable.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    void setText(int id, Object text) {
        TextView tv = (TextView) navView.findViewById(id);
        if (tv!=null) {
            String val = (text==null)?"":text.toString();
            tv.setText(val);
        }
    }

    // @OnClick(R.id.nav_toggle_ar)
    public void onToggleAspectRatio() {
        client.eventbus().post(ToggleAspectRatioEvent.INSTANCE);
    }

    private void configureWindow() {
        Dialog dialog = this;
        WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams();
        wmlp.copyFrom(dialog.getWindow().getAttributes());

        wmlp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        wmlp.x = 0;   //x position
        wmlp.y = 0;   //y position
        wmlp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmlp.height = WindowManager.LayoutParams.MATCH_PARENT;
        dialog.getWindow().setAttributes(wmlp);
    }

    @Override
    protected void onStart() {
        super.onStart();
        configureWindow();
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (!eventBusRegistered) {
            client.eventbus().register(this);
            eventBusRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        if (eventBusRegistered) {
            client.eventbus().unregister(this);
            eventBusRegistered = false;
        }
        super.onStop();
    }

    public static VideoInfoDialog showDialog(Activity activity) {
        log.debug("Showing Video Info");
        VideoInfoDialog dialog = new VideoInfoDialog(activity);
        dialog.show();
        return dialog;
    }
}
