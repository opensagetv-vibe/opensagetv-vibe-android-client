package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.SageCommand;
import opensagetv.vibe.miniclient.android.events.BackPressedEvent;
import opensagetv.vibe.miniclient.android.events.ChangePlayerOneTime;
import opensagetv.vibe.miniclient.android.events.CloseAppEvent;
import opensagetv.vibe.miniclient.android.events.HideNavigationEvent;
import opensagetv.vibe.miniclient.android.events.HideSystemUIEvent;
import opensagetv.vibe.miniclient.android.events.ToggleAspectRatioEvent;
import opensagetv.vibe.miniclient.android.preferences.MediaMappingPreferences;
import opensagetv.vibe.miniclient.android.video.PlayerBackend;
import opensagetv.vibe.miniclient.events.ShowKeyboardEvent;
import opensagetv.vibe.miniclient.events.VideoInfoShow;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.uibridge.EventRouter;

/**
 * Created by seans on 05/12/15.
 */
public class NavigationDialog extends Dialog
{
    static final Logger log = LoggerFactory.getLogger(NavigationDialog.class);
    private final Activity activity;
    private final MiniClient client;
    private final Runnable showHelpAction;

    private View navView;

    View navOptions = null;

    View navPause = null;

    ImageView navSmartRemote;
    ImageView navPlaybackStats;
    MediaMappingPreferences prefs;

    public NavigationDialog(Activity activity)
    {
        this(activity, null);
    }

    public NavigationDialog(Activity activity, Runnable showHelpAction)
    {
        super(activity, R.style.Theme_Dialog_DoNotDim);
        this.activity = activity;
        this.showHelpAction = showHelpAction;
        this.client = MiniclientApplication.get().getClient();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(false);

        //getActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        navView = LayoutInflater.from(getContext()).inflate(R.layout.navigation, null, false);
        setContentView(navView);

        prefs = new MediaMappingPreferences(this.client.properties());

        navOptions = navView.findViewById(R.id.nav_options);
        navPause = navView.findViewById(R.id.nav_media_pause);
        navSmartRemote = (ImageView) navView.findViewById(R.id.nav_remote_mode);
        navPlaybackStats = (ImageView) navView.findViewById(R.id.nav_playback_stats);

        View.OnClickListener buttonClickListener = new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                buttonClick(v);
            }
        };

        for (int id : new int[]{R.id.nav_up, R.id.nav_down, R.id.nav_left, R.id.nav_right, R.id.nav_select, R.id.nav_pgdn, R.id.nav_pgup,
                R.id.nav_options, R.id.nav_home, R.id.nav_media_pause, R.id.nav_media_play, R.id.nav_media_skip_back, R.id.nav_media_skip_back_2,
                R.id.nav_media_skip_forward, R.id.nav_media_skip_forward_2,
                R.id.nav_media_stop, R.id.nav_back, R.id.nav_info, R.id.nav_video_info})
        {
            try
            {
                navView.findViewById(id).setOnClickListener(buttonClickListener);
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }

        navView.findViewById(R.id.nav_switch_player).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onSwitchPlayer();
            }
        });

        navView.findViewById(R.id.nav_toggle_ar).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onToggleAspectRatio();
            }
        });

        navView.findViewById(R.id.nav_closed_captions).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onClosedCaptions();
            }
        });

        View activePlayerAdjustments = navView.findViewById(R.id.nav_active_player_adjustments);
        if (activePlayerAdjustments != null)
            activePlayerAdjustments.setOnClickListener(new View.OnClickListener()
                {
                    @Override public void onClick(View v)
                    {
                        dismiss();
                        ActivePlayerAdjustmentsDialog.show(activity);
                    }
                });

        if (navPlaybackStats != null)
        {
            updatePlaybackStatsToggle();
            navPlaybackStats.setOnClickListener(new View.OnClickListener()
                {
                    @Override public void onClick(View v)
                    {
                        MediaCmd currentMedia = client == null
                                || client.getCurrentConnection() == null ? null
                                : client.getCurrentConnection().getMediaCmd();
                        dismiss();
                        ActivePlayerProcessOverlay.setMode(activity, currentMedia, "toggle");
                    }
                });
        }

        navView.findViewById(R.id.nav_video_info).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onVideoInfo();
            }

        });


        navView.findViewById(R.id.nav_remote_mode).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onToggleSmartRemote();
            }
        });

        navView.findViewById(R.id.nav_help).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                onHelp();
            }
        });

        for (int id : new int[]{R.id.nav_keyboard, R.id.nav_close, R.id.nav_hide})
        {
            navView.findViewById(id).setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    buttonClickInternal(v);
                }
            });
        }

        if (client != null && client.getCurrentConnection() != null)
        {
            if (client.getCurrentConnection().getMenuHint().isOSDMenuNoPopup() && (client.isVideoPlaying() || client.isVideoPaused()))
            {
                navPause.requestFocus();
            }
            else
            {
                navOptions.requestFocus();
            }

            updateSmartRemoteToggle();
        }

        AppUtil.hideSystemUI(activity);
        configureWindow();
    }

    private void onVideoInfo()
    {
        log.debug("Showing Video Info View");
        client.eventbus().post(new VideoInfoShow());
    }

    public void buttonClick(View v)
    {
        try
        {
            log.debug("Clicked: {}", v.getTag());
            String key = v.getTag().toString().toLowerCase();
            boolean hide = key.startsWith("_");

            if (hide)
            {
                key = key.substring(1);
            }
            if (hide) dismiss();

            // unique case... if the video is paused, and this pause/play button is clicked,
            // the hide the navigation.
            if ("play_pause".equalsIgnoreCase(key) && client.isVideoPaused())
            {
                dismiss();
            }

            int sageCommand = SageCommand.parseByKey(key).getEventCode();

            if (sageCommand == -1)
            {
                log.warn("Invalid SageTV Command '{}'", key);
            }
            else
            {
                EventRouter.postCommand(client, sageCommand);
            }
        }
        catch (Throwable t)
        {
            log.error("Button Not Implemented for {} with ID {}", v.getTag(), v.getId(), t);
        }
    }

    // @OnClick(R.id.nav_switch_player)
    public void onSwitchPlayer()
    {
        dismiss();

        new AlertDialog.Builder(activity)
                .setTitle(R.string.title_switch_player)
                .setMessage(activity.getResources().getString(R.string.msg_switch_player, getPlayerName(), getOtherPlayerName()))
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dismiss();
                    }
                })
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        PlayerBackend next = getPlayerBackend().next();
                        client.properties().setString(PrefStore.Keys.default_player, next.preferenceValue());
                        AppUtil.message(MiniclientApplication.get().getString(R.string.msg_player_changed, next.displayName()));
                        dismiss();
                    }
                })
                .setNeutralButton(R.string.yes_once, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        client.eventbus().post(new ChangePlayerOneTime());
                        AppUtil.message(MiniclientApplication.get().getString(R.string.msg_player_changed_one_time, getOtherPlayerName(), getPlayerName()));
                        dismiss();
                    }
                }).show();
    }

    // @OnClick(R.id.nav_toggle_ar)
    public void onToggleAspectRatio()
    {
        client.eventbus().post(ToggleAspectRatioEvent.INSTANCE);
    }

    /** Shows the caption compatibility selector from the long-press overlay. */
    public void onClosedCaptions()
    {
        final MediaCmd mediaCmd = client == null || client.getCurrentConnection() == null
                ? null : client.getCurrentConnection().getMediaCmd();
        if (mediaCmd == null)
        {
            AppUtil.message(activity.getString(R.string.caption_control_player_unavailable));
            return;
        }

        final String[] values = activity.getResources().getStringArray(
                R.array.entryvalues_legacy_server_caption_mode);
        String selectedMode = mediaCmd.getLegacyServerCaptionMode();
        int checkedItem = 0;
        for (int i = 0; i < values.length; i++)
        {
            if (values[i].equals(selectedMode))
            {
                checkedItem = i;
                break;
            }
        }

        dismiss();
        new AlertDialog.Builder(activity)
                .setTitle(R.string.caption_control_title)
                .setSingleChoiceItems(R.array.entries_legacy_server_caption_mode, checkedItem,
                        new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                if (which < 0 || which >= values.length)
                                    return;
                                mediaCmd.setLegacyServerCaptionMode(values[which]);
                                if (mediaCmd.hasSageTvClosedCaptionState()
                                        || (client.getCurrentConnection() != null
                                        && client.getCurrentConnection().isSubtitleCallbackEnabled()))
                                {
                                    AppUtil.message(activity.getString(
                                            R.string.caption_control_stv_authoritative));
                                }
                                else if ("stv".equals(values[which]))
                                {
                                    AppUtil.message(activity.getString(
                                            R.string.caption_control_legacy_stv_unavailable));
                                }
                                else
                                {
                                    AppUtil.message(activity.getString(
                                            R.string.caption_control_applied, values[which].toUpperCase()));
                                }
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // @OnClick(R.id.nav_remote_mode)
    public void onToggleSmartRemote()
    {
        prefs.setSmartRemoteEnabled(!prefs.isSmartRemoteEnabled());
        updateSmartRemoteToggle();
    }

    private void updateSmartRemoteToggle()
    {
        if (prefs.isSmartRemoteEnabled())
        {
            navSmartRemote.setImageResource(R.drawable.ic_open_with_white_24dp);
        }
        else
        {
            navSmartRemote.setImageResource(R.drawable.ic_open_with_red_24dp);
        }
    }

    private void updatePlaybackStatsToggle()
    {
        if (navPlaybackStats == null) return;
        boolean visible = ActivePlayerProcessOverlay.isVisible();
        navPlaybackStats.setImageResource(visible
                ? R.drawable.ic_equalizer_green_24dp
                : R.drawable.ic_equalizer_white_24dp);
        navPlaybackStats.setContentDescription(visible
                ? "Hide Playback Stats" : "Show Playback Stats");
    }


    // @OnClick(R.id.nav_help)
    public void onHelp()
    {
        dismiss();
        if (showHelpAction != null)
            showHelpAction.run();
        else
            HelpDialog.showDialog(activity);
    }

    private PlayerBackend getPlayerBackend()
    {
        return PlayerBackend.fromPreference(
                client.properties().getString(PrefStore.Keys.default_player, PlayerBackend.DEFAULT_PREFERENCE));
    }

    private String getPlayerName()
    {
        return getPlayerBackend().displayName();
    }

    private String getOtherPlayerName()
    {
        return getPlayerBackend().next().displayName();
    }

    @Override
    public void dismiss()
    {
        super.dismiss();
        client.eventbus().post(HideSystemUIEvent.INSTANCE);
    }

    // @OnClick({R.id.nav_keyboard, R.id.nav_close, R.id.nav_hide})
    public void buttonClickInternal(View v)
    {
        String tag = v.getTag().toString().toLowerCase();

        if ("_keyboard".equalsIgnoreCase(tag))
        {
            dismiss();
            client.eventbus().post(ShowKeyboardEvent.INSTANCE);
        }
        else if ("_close".equalsIgnoreCase(tag))
        {
            client.eventbus().post(CloseAppEvent.INSTANCE);
        }
        else if ("_hide".equalsIgnoreCase(tag))
        {
            client.eventbus().post(HideNavigationEvent.INSTANCE);
        }
        else
        {
            log.warn("Nothing Handled Internal Event: {}", tag);
        }
    }

    private void configureWindow()
    {
        Dialog dialog = this;
        WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams();
        wmlp.copyFrom(dialog.getWindow().getAttributes());

        wmlp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        wmlp.x = 0;   //x position
        wmlp.y = 0;   //y position
        wmlp.width = WindowManager.LayoutParams.MATCH_PARENT;
        wmlp.height = WindowManager.LayoutParams.MATCH_PARENT;
        dialog.getWindow().setAttributes(wmlp);

        dialog.setOnKeyListener(new DialogInterface.OnKeyListener()
        {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event)
            {
                if (keyCode == KeyEvent.KEYCODE_BACK)
                {
                    client.eventbus().post(BackPressedEvent.INSTANCE);
                    return true;
                }
                return false;
            }
        });

    }

    @Override
    protected void onStart()
    {
        super.onStart();
        Dialog dialog = this;

        if (dialog.getWindow() != null)
        {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            //dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        AppUtil.hideSystemUI(activity);
    }

    public static NavigationDialog showDialog(Activity activity)
    {
        return showDialog(activity, null);
    }

    public static NavigationDialog showDialog(Activity activity, Runnable showHelpAction)
    {
        log.debug("Showing Navigation");
        NavigationDialog dialog = new NavigationDialog(activity, showHelpAction);
        dialog.show();
        return dialog;
    }
}
