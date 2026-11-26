package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.PlaybackCompanionClient;
import opensagetv.vibe.miniclient.MiniClient;
import opensagetv.vibe.miniclient.MiniPlayerPlugin;
import opensagetv.vibe.miniclient.android.events.ToggleAspectRatioEvent;
import opensagetv.vibe.miniclient.android.diagnostics.DiagnosticExportController;
import opensagetv.vibe.miniclient.android.video.ActivePlayerSessionOverrides;
import opensagetv.vibe.miniclient.android.video.DecodingMethod;
import opensagetv.vibe.miniclient.android.video.DisplayRefreshController;
import opensagetv.vibe.miniclient.android.video.PlayerBackend;
import opensagetv.vibe.miniclient.android.video.TextSubtitlePresentation;
import opensagetv.vibe.miniclient.android.video.gsy.GSYPlayerEngine;
import opensagetv.vibe.miniclient.android.video.ijkplayer.IJKMediaPlayerImpl;
import opensagetv.vibe.miniclient.video.DeinterlacePolicy;
import opensagetv.vibe.miniclient.android.video.ClientDeinterlaceStatus;
import opensagetv.vibe.miniclient.android.video.media3.Media3AvSyncTestDialog;
import opensagetv.vibe.miniclient.prefs.PrefStore;
import opensagetv.vibe.miniclient.media.SubtitleTrack;
import opensagetv.vibe.miniclient.media.SubtitleCodec;
import opensagetv.vibe.miniclient.media.CaptionSlotPolicy;
import opensagetv.vibe.miniclient.media.TrackPreferencePolicy;
import opensagetv.vibe.miniclient.android.ui.settings.PlaybackSettingCatalog;
import opensagetv.vibe.miniclient.android.ui.settings.PlaybackSettingPreferences;
import androidx.preference.PreferenceManager;

/** Runtime-safe playback controls reachable from the long-press navigation UI. */
public final class ActivePlayerAdjustmentsDialog
{
    private interface ValueSetter { void set(String value); }

    private final Activity activity;
    private final MiniClient client;
    private boolean fromHub;

    /** Complete playback settings; shortcuts below enter the very same panels. */
    public static void showHub(Activity activity){
        ActivePlayerAdjustmentsDialog controller=new ActivePlayerAdjustmentsDialog(activity);
        controller.fromHub=true;controller.showHubMenu();
    }
    /** Closed UI routing also used by the debug bridge; never controls playback. */
    public static void showSection(Activity activity,String section){
        ActivePlayerAdjustmentsDialog c=new ActivePlayerAdjustmentsDialog(activity);
        if("navigation".equals(section)){NavigationDialog.showDialog(activity);return;}
        if("hub".equals(section)){c.fromHub=true;c.showHubMenu();}
        else if("video".equals(section))c.showMain();
        else if("audio".equals(section))c.showAudioMenu();
        else if("captions".equals(section))c.chooseCaptions(c.media());
        else if("transport".equals(section)){c.fromHub=true;c.showTransportMenu();}
        else if("diagnostics".equals(section)){c.fromHub=true;c.showDiagnosticsMenu();}
        else if("recovery".equals(section)){c.fromHub=true;c.showRecoveryMenu();}
        else throw new IllegalArgumentException("Unsupported playback settings section");
    }

    private ActivePlayerAdjustmentsDialog(Activity activity)
    {
        this.activity = activity;
        this.client = MiniclientApplication.get().getClient();
    }

    public static void show(Activity activity)
    {
        new ActivePlayerAdjustmentsDialog(activity).showMain();
    }

    /** Open the complete live caption selector directly from the CC icon. */
    public static void showCaptions(Activity activity)
    {
        ActivePlayerAdjustmentsDialog dialog = new ActivePlayerAdjustmentsDialog(activity);
        dialog.chooseCaptions(dialog.media());
    }

    /** Open decoded-audio and A/V sync controls directly from the audio icon. */
    public static void showAudio(Activity activity)
    {
        new ActivePlayerAdjustmentsDialog(activity).showAudioMenu();
    }

    /** Open the embedded common-clock A/V fixture directly for debug automation. */
    public static void showAvSyncTest(Activity activity)
    {
        new ActivePlayerAdjustmentsDialog(activity).showAvSyncTestDialog();
    }

    public static void testCurrentVideo(Activity activity)
    {
        ActivePlayerAdjustmentsDialog dialog = new ActivePlayerAdjustmentsDialog(activity);
        dialog.testCurrentVideo(dialog.media());
    }

    private MediaCmd media()
    {
        return client == null || client.getCurrentConnection() == null
                ? null : client.getCurrentConnection().getMediaCmd();
    }

    private void showMain()
    {
        MiniPlayerPlugin active=player(media());
        PlaybackSettingsPanel.Section video=PlaybackSettingsPanel.section("video","Playback Settings",true);
        addSetting(video,PlaybackSettingCatalog.PLAYER,playerValue(),true,null,()->chooseBackend(media()));
        addSetting(video,PlaybackSettingCatalog.DECODING,DecodingMethod.fromPreference(ActivePlayerSessionOverrides.resolveDecodingMethod(client.properties().getString(PrefStore.Keys.decoding_method,"hardware"))).displayName(),active!=null,"No active player",this::chooseDecoding);
        addSetting(video,PlaybackSettingCatalog.DEINTERLACE,deinterlaceValue(),active!=null,"No active player",this::chooseDeinterlace);
        video.add("filters","Video Filters",videoFilterValue(),active!=null,this::showVideoFilters);
        PlaybackSettingsPanel.Section display=PlaybackSettingsPanel.section("display","Display Settings",false);
        addSetting(display,PlaybackSettingCatalog.REFRESH,refreshValue(active),active!=null,"No active player",()->chooseRefreshRate(media()));
        PlaybackSettingsPanel.Section dvd=PlaybackSettingsPanel.section("dvd","DVD Settings",false);
        boolean isDvd=media()!=null&&media().isDvdSessionPending();
        dvd.add("dvd_controls","DVD Controls",isDvd?"Optional companion actions; stock controls remain available":"Available during DVD playback",isDvd,this::showVideoDvdMenu);
        addSetting(dvd,PlaybackSettingCatalog.DVD_REPAIR,dvdRepairValue(),isDvd,"Available during DVD playback",this::chooseDvdRepair);
        addSetting(dvd,PlaybackSettingCatalog.SETTLE,refreshSettleValue(),isDvd,"Available during DVD playback",this::chooseRefreshSettle);
        PlaybackSettingsPanel.Section advanced=PlaybackSettingsPanel.section("advanced","Advanced Compatibility",false);
        addSetting(advanced,queueDefinition(),codecQueueingValue(),isExoFamily(),"Media3 / legacy Exo delegates only; saved choice retained",this::chooseCodecQueueing);
        showPanel("video","Video Settings","Changes are session-only unless explicitly saved.",java.util.Arrays.asList(video,display,dvd,advanced),fromHub?this::showHubMenu:null);
    }

    private void showHubMenu(){
        PlaybackSettingsPanel.Section playback=PlaybackSettingsPanel.section("playback","Playback Settings",true);
        playback.add("video","Video",playerValue(),true,this::showMain)
            .add("audio","Audio","Output, tracks and sync",true,this::showAudioMenu)
            .add("captions","Captions / Subtitles","Broadcast CC, streams and appearance",true,()->chooseCaptions(media()))
            .add("display","Display",refreshValue(player(media())),true,()->showVideoDisplayMenu(media()))
            .add("dvd","DVD Settings",media()!=null&&media().isDvdSessionPending()?"Current DVD":"Available during DVD playback",media()!=null&&media().isDvdSessionPending(),this::showVideoDvdMenu);
        PlaybackSettingsPanel.Section transport=PlaybackSettingsPanel.section("transport","Transport Settings",true);
addTransportEntries(transport);
        PlaybackSettingsPanel.Section diagnostics=PlaybackSettingsPanel.section("diagnostics","Diagnostics",false);
addDiagnosticsEntries(diagnostics);
        PlaybackSettingsPanel.Section recovery=PlaybackSettingsPanel.section("recovery","Recovery",false);
addRecoveryEntries(recovery);
        showPanel("hub","Playback Settings",transportSummary(),java.util.Arrays.asList(playback,transport,diagnostics,recovery),null);
    }

    private void showPanel(String id,String title,String note,List<PlaybackSettingsPanel.Section> sections,Runnable parent){
        PlaybackSettingsPanel.show(activity,id,title,note,sections,parent);
    }
    private void addSetting(PlaybackSettingsPanel.Section section,PlaybackSettingCatalog.Definition d,
            String value,boolean enabled,String reason,Runnable action){
        MiniPlayerPlugin active=player(media());
        PlaybackSettingCatalog.RuntimeSupport support=new PlaybackSettingCatalog.RuntimeSupport(active!=null,isExoFamily(),hasClientFilterChoice(),hasGpuFilterChoice(),
            active!=null&&(active.supportsTextSubtitlePresentation()||active.supportsSubtitleOffset()),active!=null,"fixed".equals(client.properties().getStreamingMode()));
        String unavailable=PlaybackSettingCatalog.runtimeUnavailable(d,support);
        if(unavailable!=null){enabled=false;reason=unavailable;}
        section.add(d.key,d.title,enabled?value+" • "+d.timing.label:reason,enabled,action);
    }
    private boolean isExoFamily(){
        MiniPlayerPlugin active=player(media());
        if(active instanceof opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl){
            String engine=((opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)active).getResolvedEngineForDebug();
            return "media3".equals(engine)||"legacy_exo".equals(engine);
        }
        return active instanceof opensagetv.vibe.miniclient.android.video.media3.Media3MediaPlayerImpl
            ||active instanceof opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl;
    }
    private PlaybackSettingCatalog.Definition queueDefinition(){
        MiniPlayerPlugin active=player(media());
        boolean legacy=active instanceof opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl;
        if(active instanceof opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)
            legacy="legacy_exo".equals(((opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)active).getResolvedEngineForDebug());
        if(active==null)legacy=PlayerBackend.EXOPLAYER.preferenceValue().equals(resolvedBackend());
        return legacy?PlaybackSettingCatalog.EXO_QUEUE:PlaybackSettingCatalog.MEDIA3_QUEUE;
    }
    private String transportSummary(){
        String selected=client.properties().getStreamingMode();
        String configured="dynamic".equals(selected)?"Push":"pull".equals(selected)?"Pull":"fixed".equals(selected)?"Fixed":selected;
        ActivePlayerStatsSnapshot actual=ActivePlayerStatsSnapshot.capture(activity,client,media());
        return "Selected: "+configured+" • Actual: "+(actual.active?actual.transport:"No active playback");
    }
    private void showTransportMenu(){
        if(fromHub){showHubMenu();return;}
        PlaybackSettingsPanel.Section transport=PlaybackSettingsPanel.section("transport","Transport Settings",true);
        addTransportEntries(transport);
        showPanel("transport","Transport Settings","Saved output changes apply next playback; transport selection requires reconnect.",java.util.Arrays.asList(transport),this::showMain);
    }
    private void showFixedPlaybackMenu(){showTransportMenu();}
    private void addTransportEntries(PlaybackSettingsPanel.Section transport){
        transport.add("actual","Selected / Actual Transport",transportSummary(),false,null);
        String actualTransport=ActivePlayerStatsSnapshot.capture(activity,client,media()).transport;
        boolean buffered=isExoFamily()&&("Pull".equals(actualTransport)||actualTransport.startsWith("SMB"))&&!(media()!=null&&media().isDvdSessionPending());
        addSetting(transport,PlaybackSettingCatalog.BUFFER,bufferPresetValue(),buffered,"Media3 / Exo Pull or SMB playback only; DVD uses its interactive buffer",()->chooseBufferPreset(media()));
        android.content.SharedPreferences saved=PreferenceManager.getDefaultSharedPreferences(activity);
        String mode=PlaybackSettingPreferences.read(saved,PlaybackSettingCatalog.PROCESSING);
        java.util.List<PlaybackSettingCatalog.Definition> definitions=new java.util.ArrayList<>(java.util.Arrays.asList(PlaybackSettingCatalog.PROCESSING,PlaybackSettingCatalog.ADJUSTMENT,PlaybackSettingCatalog.MINIMUM,PlaybackSettingCatalog.MAXIMUM));
        definitions.addAll(PlaybackSettingCatalog.FIXED_OUTPUT);
        for(PlaybackSettingCatalog.Definition d:definitions){
            String reason=PlaybackSettingCatalog.fixedUnavailable(d,mode,!"off".equals(PlaybackSettingPreferences.read(saved,PlaybackSettingCatalog.ADJUSTMENT)),PlaybackSettingPreferences.read(saved,PlaybackSettingCatalog.REMUX),PlaybackSettingPreferences.read(saved,PlaybackSettingCatalog.ENCODER));
            addSetting(transport,d,d.display(activity,PlaybackSettingPreferences.read(saved,d)),reason==null,reason,()->chooseSavedPlaybackDefault(d));
        }
        transport.add("reset_transcoding","Reset Transcoding Settings Only","Next playback; connections and other preferences preserved",true,()->
            confirm("Reset Transcoding Settings Only?","Current playback will not change.",()->{
                opensagetv.vibe.miniclient.android.ui.settings.SettingsDefaults.restoreTranscoding(saved);showFixedPlaybackMenu();
            },this::showFixedPlaybackMenu));
        opensagetv.vibe.miniclient.android.video.MimDirectSessionClient direct=MiniclientApplication.get().getMimDirectSession();
        int current=direct.qualityVideoTargetForDiagnostics(),target=Math.max(250,current*7/10),recovery=direct.qualityRecoveryTarget();
        transport.add("quality_status","Current-session Bitrate",current>0?direct.qualityObservationForDiagnostics():"Requires an owned compatible MIM transcode",false,null);
        transport.add("quality_reduce","Reduce Current Session",target+" kbps • Session only",current>0,()->{requestDirectQuality(target);showTransportMenu();});
        transport.add("quality_recover","Recover Current Session",recovery>current?recovery+" kbps • Session only":"Already at ceiling or recovery unavailable",recovery>current,()->{requestDirectQuality(recovery);showTransportMenu();});
    }
    private void chooseSavedPlaybackDefault(PlaybackSettingCatalog.Definition d){
        android.content.SharedPreferences saved=PreferenceManager.getDefaultSharedPreferences(activity);
        if(d.labels!=0){
            String[] labels=d.labels(activity),values=d.values(activity);
            chooseAudioChoice(d.title,labels,index->confirmSavedPlaybackDefault(d,values[Integer.parseInt(index)]),this::showFixedPlaybackMenu);
        }else{
            android.widget.EditText field=new android.widget.EditText(activity);field.setSingleLine(true);
            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setText(PlaybackSettingPreferences.read(saved,d));
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(d.title).setView(field)
                .setNegativeButton("Cancel",(unused,which)->showFixedPlaybackMenu())
                .setPositiveButton("Continue",(unused,which)->confirmSavedPlaybackDefault(d,field.getText().toString().trim())).create();
            dialog.setOnCancelListener(unused->showFixedPlaybackMenu());dialog.show();
        }
    }
    private void confirmSavedPlaybackDefault(PlaybackSettingCatalog.Definition d,String value){
        confirm("Save as device default?",d.title+": "+d.display(activity,value)+"\n"+d.timing.label+". Current session is unchanged.",()->{
            PlaybackSettingPreferences.write(PreferenceManager.getDefaultSharedPreferences(activity),d,value,activity);showFixedPlaybackMenu();
        },this::showFixedPlaybackMenu);
    }
    private void showDiagnosticsMenu(){
        if(fromHub){showHubMenu();return;}
        PlaybackSettingsPanel.Section section=PlaybackSettingsPanel.section("diagnostics","Diagnostics",true);
        addDiagnosticsEntries(section);
        showPanel("diagnostics","Diagnostics","No background polling or server changes.",java.util.Arrays.asList(section),fromHub?this::showHubMenu:this::showMain);
    }
    private void addDiagnosticsEntries(PlaybackSettingsPanel.Section section){
        section.add("stats","Playback Status","Show / hide detailed statistics",true,()->choosePlaybackStats(media()))
            .add("effective_output","Effective Transcoding Output","Observed server encoding/output, separate from client decoding",true,()->{
                final opensagetv.vibe.miniclient.android.video.MimDirectSessionClient direct=MiniclientApplication.get().getMimDirectSession();
                direct.refreshServerOutput(value->activity.runOnUiThread(()->{
                    if(!activity.isFinishing())new AlertDialog.Builder(activity).setTitle("Effective Transcoding Output").setMessage(value).setPositiveButton("Back",(dialog,which)->showDiagnosticsMenu()).show();
                }));
            })
            .add("snapshot","Server Playback Snapshot","Optional public API observation",true,this::showServerPlayback)
            .add("test","Test Current Video","Requires active playback",player(media())!=null,()->testCurrentVideo(media()))
            .add("export","Export Diagnostic Report","Choose Web, server or SMB when available",true,()->DiagnosticExportController.show(activity,diagnosticsTextForExport(activity,media())));
        boolean preview=MiniclientApplication.get().getMimDirectSession().previewHandle()!=null;
        section.add("previews","Source Previews",preview?"Read-only":"Compatible provider required",preview,()->MimSourcePreviewDialog.show(activity,media(),this::showSettingsPanel));
    }
    private void showRecoveryMenu(){
        if(fromHub){showHubMenu();return;}
        PlaybackSettingsPanel.Section recovery=PlaybackSettingsPanel.section("recovery","Recovery",true);
        addRecoveryEntries(recovery);
        showPanel("recovery","Recovery","Recovery never resets saved device settings.",java.util.Arrays.asList(recovery),fromHub?this::showHubMenu:this::showMain);
    }
    private void addRecoveryEntries(PlaybackSettingsPanel.Section recovery){
        MiniPlayerPlugin active=player(media());
        recovery.add("video","Restart Video Decoder","Current position when supported",active!=null,()->confirm("Restart Video Decoder?","A brief playback interruption may occur.",()->{reload(media());showRecoveryMenu();},this::showRecoveryMenu));
        recovery.add("audio","Restart Audio Output",active!=null&&active.supportsAudioOutputRestart()?"Keep current settings":"Unavailable on active output",active!=null&&active.supportsAudioOutputRestart(),()->{AppUtil.message(active.restartAudioOutput()?"Restarting audio output":"Audio restart was not accepted");showRecoveryMenu();});
        recovery.add("reset","Restore Saved Playback Defaults","Clear session overrides only; saved settings are not erased",true,()->confirm("Restore saved playback defaults?","This clears current-session overrides, not device defaults, credentials or profiles. Video changes apply on the next safe reload.",()->{
            ActivePlayerSessionOverrides.reset();DisplayRefreshController.cancelPendingRefresh();
            if(active!=null){
                if(active.supportsAudioPassthroughControl())active.setAudioPassthroughEnabled(!client.properties().getBoolean(PrefStore.Keys.disable_audio_passthrough,true));
                if(active.supportsPassthroughAudioOffset())active.setPassthroughAudioOffsetEnabled(client.properties().getBoolean(PrefStore.Keys.playback_passthrough_audio_offset_enabled,false));
                if(active.supportsAudioOffset())active.setAudioOffsetMillis(client.properties().getInt(PrefStore.Keys.playback_audio_offset_ms,0));
                if(active.supportsSubtitleOffset())active.setSubtitleOffsetMillis(client.properties().getInt(PrefStore.Keys.playback_subtitle_offset_ms,0));
                if(active.supportsTextSubtitlePresentation())active.setTextSubtitlePresentation(client.properties().getInt(PrefStore.Keys.playback_subtitle_safe_area_percent,18),client.properties().getInt(PrefStore.Keys.playback_subtitle_text_scale_percent,100),client.properties().getString(PrefStore.Keys.playback_subtitle_text_style,"system"));
            }
            // Existing backend setters remember their applied audio offsets as
            // session overrides. Restore must leave saved-default ownership,
            // not recreate an override with today's saved value.
            ActivePlayerSessionOverrides.reset();
            reload(media());showRecoveryMenu();
        },this::showRecoveryMenu));
    }
    private void confirm(String title,String message,Runnable accepted,Runnable cancelled){
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel",(unused,which)->cancelled.run()).setPositiveButton("Confirm",(unused,which)->accepted.run()).create();
        dialog.setOnCancelListener(unused->cancelled.run());dialog.show();
        android.widget.Button cancel=dialog.getButton(DialogInterface.BUTTON_NEGATIVE);PlaybackSettingsPanel.styleFocusable(activity,cancel);cancel.requestFocus();
        PlaybackSettingsPanel.styleFocusable(activity,dialog.getButton(DialogInterface.BUTTON_POSITIVE));
    }

    private String directQualityTitle() {
        MiniPlayerPlugin active = player(media());
        if (active instanceof opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)
            active = ((opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl) active)
                    .getDelegateForDebug();
        return MiniclientApplication.get().getMimDirectSession().ownsHls(active)
                ? "HLS quality" : "Direct quality";
    }

    private void showDirectQuality(){
        opensagetv.vibe.miniclient.android.video.MimDirectSessionClient service=MiniclientApplication.get().getMimDirectSession();
        int current=service.qualityVideoTargetForDiagnostics(),target=Math.max(250,current*7/10);
        PlaybackSettingsPanel.Section quality=PlaybackSettingsPanel.section("quality","Current-session Bitrate",true);
        quality.add("status","Provider State",service.qualityObservationForDiagnostics(),false,null);
        quality.add("reduce","Reduce Current Session",target+" kbps • Session only",current>0,()->{requestDirectQuality(target);showDirectQuality();});
        int recovery=service.qualityRecoveryTarget();
        quality.add("recover","Recover Current Session",recovery>current?recovery+" kbps • Session only":"Already at ceiling or recovery unavailable",recovery>current,()->{requestDirectQuality(recovery);showDirectQuality();});
        quality.add("defaults","Fixed Playback Settings","Automatic adjustment / limits • Next playback; explicit save",true,this::showFixedPlaybackMenu);
        showPanel("quality",directQualityTitle(),"MIM owns bitrate decisions from buffer fill/trend. Manual requests do not change saved limits.",java.util.Arrays.asList(quality),this::showTransportMenu);
    }

    private void requestDirectQuality(int target){
        MiniPlayerPlugin active=player(media());boolean accepted=false;
        if(active instanceof opensagetv.vibe.miniclient.android.video.BaseMediaPlayerImpl)
            accepted=((opensagetv.vibe.miniclient.android.video.BaseMediaPlayerImpl<?,?>)active).requestMimDirectQualityReduction(target);
        else if(active instanceof opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)
            accepted=((opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)active).requestMimDirectQualityReduction(target);
        boolean inPlace=MiniclientApplication.get().getMimDirectSession()
                .qualityStateForDiagnostics().startsWith("in_place_");
        AppUtil.message(accepted?(inPlace?"Bitrate change requested; current encoder and playback retained":
                "Preparing replacement stream; saved quality unchanged"):
                "Quality change unavailable or cooling down; current playback retained");
    }

    private void showVideoDisplayMenu(final MediaCmd media)
    {
        PlaybackSettingsPanel.Section display=PlaybackSettingsPanel.section("display","Display Settings",true);
        addSetting(display,PlaybackSettingCatalog.REFRESH,refreshValue(player(media)),player(media)!=null,"No active player",()->chooseRefreshRate(media));
        display.add("aspect","Aspect Ratio","Use the existing quick aspect-ratio icon",false,null);
        showPanel("display","Display Settings","Current resolution is retained.",java.util.Arrays.asList(display),fromHub?this::showHubMenu:this::showMain);
    }

    private boolean hasClientFilterChoice()
    {
        return ClientDeinterlaceStatus.supportsSoftwareSelection(player(media()))
                && MiniclientApplication.get().getMimDirectSession()
                        .acceptedDeinterlaceForDiagnostics().isEmpty();
    }

    private boolean hasGpuFilterChoice() {
        String server = MiniclientApplication.get().getMimDirectSession().acceptedDeinterlaceForDiagnostics();
        return ClientDeinterlaceStatus.gpuOwner(player(media())) != null
                && (server.isEmpty() || "off".equals(server));
    }

    private String gpuChoice(String key, String fallback) {
        String value=client.properties().getString(key,fallback);
        return PrefStore.Keys.video_gpu_filter.equals(key) ? ActivePlayerSessionOverrides.resolveGpuFilter(value)
                : PrefStore.Keys.video_gpu_field_order.equals(key) ? ActivePlayerSessionOverrides.resolveGpuFieldOrder(value)
                : ActivePlayerSessionOverrides.resolveGpuOutputRate(value);
    }

    private String videoFilterValue()
    {
        if (hasGpuFilterChoice()) {
            String actual=ClientDeinterlaceStatus.filter(player(media()));
            String state=ClientDeinterlaceStatus.current(player(media()),MiniclientApplication.get().getMimDirectSession());
            return ("motion".equals(gpuChoice(PrefStore.Keys.video_gpu_filter,"fast")) ? "Motion-adaptive" : "Fast")
                    + " ("+(actual.isEmpty()?state:actual)+")";
        }
        if (!hasClientFilterChoice())
            return MiniclientApplication.get().getMimDirectSession()
                    .acceptedDeinterlaceForDiagnostics().isEmpty() ? "Device controlled" : "Server controlled";
        String requested = DeinterlacePolicy.normalizeFilter(
                ActivePlayerSessionOverrides.resolveIjkDeinterlaceFilter(
                        client.properties().getString(PrefStore.Keys.ijk_deinterlace_filter, "auto")));
        String actual = ClientDeinterlaceStatus.filter(player(media()));
        return ("yadif".equals(requested) ? "YADIF" : "fast".equals(requested) ? "Fast" : "Auto")
                + " (" + (actual.isEmpty() ? "Inactive / device" : actual) + ")";
    }

    /** Algorithms are a compact child of Video, separate from filter location. */
    private void showVideoFilters()
    {
        PlaybackSettingsPanel.Section filters=PlaybackSettingsPanel.section("filters","Video Filters",true);
        for(PlaybackSettingCatalog.Definition d:new PlaybackSettingCatalog.Definition[]{PlaybackSettingCatalog.GPU_FILTER,PlaybackSettingCatalog.GPU_ORDER,PlaybackSettingCatalog.GPU_RATE}){
            addSetting(filters,d,d.display(activity,gpuChoice(d.key,d.defaultValue)),hasGpuFilterChoice(),"Requires a supported Player GPU path; server filtering is separate",()->chooseGpuOption(d.title,d.key,d.values(activity),d.labels(activity)));
        }
        addSetting(filters,PlaybackSettingCatalog.IJK_FILTER,videoFilterValue(),hasClientFilterChoice(),"IJK software interlaced frames only; device/server filtering is read-only",this::chooseClientFilter);
        showPanel("filters","Video Filters","Current: "+videoFilterValue(),java.util.Arrays.asList(filters),this::showMain);
    }

    private void chooseGpuOption(final String title, final String key, final String[] values, String[] labels) {
        chooseAudioChoice(title,labels,index -> {
            final String value=values[Integer.parseInt(index)];
            chooseAudioChoice(title,new String[]{"This playback session","Save as this device's default"},scope -> {
                if(PrefStore.Keys.video_gpu_filter.equals(key)) ActivePlayerSessionOverrides.setGpuFilter(value);
                else if(PrefStore.Keys.video_gpu_field_order.equals(key)) ActivePlayerSessionOverrides.setGpuFieldOrder(value);
                else ActivePlayerSessionOverrides.setGpuOutputRate(value);
                if(Integer.parseInt(scope)==1) client.properties().setString(key,value);
                reload(media());
                activity.getWindow().getDecorView().postDelayed(() -> showVideoFilters(),75);
            },() -> showVideoFilters());
        },() -> showVideoFilters());
    }

    private void chooseClientFilter()
    {
        final String[] values = PlaybackSettingCatalog.IJK_FILTER.values(activity);
        chooseAudioChoice(PlaybackSettingCatalog.IJK_FILTER.title, PlaybackSettingCatalog.IJK_FILTER.labels(activity), new ValueSetter()
        {
            @Override public void set(String index)
            {
                final String value = values[Integer.parseInt(index)];
                chooseAudioChoice("Filter: " + ("yadif".equals(value) ? "YADIF" : "fast".equals(value) ? "Fast" : "Auto"),
                        new String[] { "This playback session", "Save as this device's default" },
                        new ValueSetter()
                        {
                            @Override public void set(String scope)
                            {
                                ActivePlayerSessionOverrides.setIjkDeinterlaceFilter(value);
                                if (Integer.parseInt(scope) == 1)
                                    client.properties().setString(PrefStore.Keys.ijk_deinterlace_filter, value);
                                if ("yadif".equals(value))
                                    AppUtil.message("Temporal YADIF can slow HD playback; choose Auto if video falls behind");
                                reload(media());
                                activity.getWindow().getDecorView().postDelayed(new Runnable()
                                { @Override public void run() { showVideoFilters(); } }, 75L);
                            }
                        }, new Runnable()
                        { @Override public void run() { showVideoFilters(); } });
            }
        }, new Runnable()
        { @Override public void run() { showVideoFilters(); } });
    }

    private void showVideoDvdMenu()
    {
        final Dialog[] holder = new Dialog[1];
        LinearLayout panel = captionPanel();
        addVideoRow(panel, holder, "Timestamp repair", dvdRepairValue(), new Runnable()
                { @Override public void run() { chooseDvdRepair(); } });
        addVideoRow(panel, holder, "HDMI settle", refreshSettleValue(), new Runnable()
                { @Override public void run() { chooseRefreshSettle(); } });
        final MediaCmd expected=media();
        final long loadId=expected==null?-1:expected.getCompanionMenuLoadId();
        final String[] challenge={null};
        final TextView status=captionRow("Checking optional DVD controls...",false);
        status.setMaxLines(3);panel.addView(status);
        final List<TextView> controls=new ArrayList<>();
        String[] operations={"beginning","save_resume","resume","clear_resume"};
        String[] labels={"Start main feature from beginning","Save current DVD resume point",
                "Resume saved DVD point","Clear saved DVD point"};
        for (int i=0;i<operations.length;i++) {
            final String operation=operations[i];
            final TextView row=captionRow(labels[i],true);
            row.setEnabled(false);row.setFocusable(false);row.setAlpha(0.45f);
            row.setOnClickListener(view->{
                if (challenge[0]==null || media()!=expected || expected.getCompanionMenuLoadId()!=loadId) {
                    status.setText("DVD load changed; Refresh controls");return;
                }
                for(TextView item:controls) { item.setEnabled(false);item.setFocusable(false);item.setAlpha(0.45f); }
                status.setText("Sending DVD action...");
                expected.requestCompanionMenu(true,challenge[0],operation,reply->activity.runOnUiThread(()->{
                    if (holder[0]==null||!holder[0].isShowing()||media()!=expected||expected.getCompanionMenuLoadId()!=loadId) return;
                    status.setText(reply.available ? "DVD action accepted; Refresh for current controls" : reply.text);
                    challenge[0]=null;
                }));
                // A representation OPEN/Stop deliberately discards the old
                // load's response. Do not leave the menu claiming it is pending.
                status.postDelayed(()->{
                    if (holder[0]!=null && holder[0].isShowing() && "Sending DVD action...".contentEquals(status.getText()))
                        status.setText("DVD load changed or request ended; Refresh controls");
                },3000);
            });
            controls.add(row);panel.addView(row);
        }
        addVideoRow(panel,holder,"Refresh DVD controls","",()->{showVideoDvdMenu();});
        addPanelBack(panel, holder,fromHub?this::showHubMenu:this::showMain);
        holder[0] = showSettingsPanel("DVD playback", panel, new String[] {
                "Timestamp repair  " + dvdRepairValue(),
                "HDMI settle  " + refreshSettleValue()
        });
        panel.getChildAt(0).requestFocus();
        holder[0].setOnCancelListener(new DialogInterface.OnCancelListener()
        { @Override public void onCancel(DialogInterface dialog) { if(fromHub)showHubMenu();else showMain(); } });
        if (expected!=null) expected.requestCompanionMenu(true,null,null,reply->activity.runOnUiThread(()->{
            if (!holder[0].isShowing()||media()!=expected||expected.getCompanionMenuLoadId()!=loadId) return;
            status.setText(reply.text);challenge[0]=reply.challenge;
            for(TextView row:controls) {
                row.setEnabled(reply.available);row.setFocusable(reply.available);row.setAlpha(reply.available?1f:0.45f);
            }
        }));
        else status.setText("No active DVD; optional controls unavailable");
        status.postDelayed(()->{
            if(holder[0].isShowing()&&"Checking optional DVD controls...".contentEquals(status.getText()))
                status.setText("DVD load changed or service unavailable; Refresh controls");
        },3000);
    }

    private void showServerPlayback()
    {
        final MediaCmd expected=media();
        final long loadId=expected==null?-1:expected.getCompanionMenuLoadId();
        final Dialog[] holder=new Dialog[1];
        LinearLayout panel=captionPanel();
        final TextView text=captionRow("Reading optional stock playback snapshot...",false);
        text.setMaxLines(32);text.setTextSize(12.5f);
        android.widget.ScrollView scroll=new android.widget.ScrollView(activity);
        scroll.addView(text);panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(235)));
        addVideoRow(panel,holder,"Refresh","On demand only",()->showServerPlayback());
        addPanelBack(panel,holder,this::showDiagnosticsMenu);
        holder[0]=showSettingsPanel("Server playback",panel,new String[]{"Stock API observation (not renderer clock)"});
        panel.getChildAt(1).requestFocus(); // Refresh, not a read-only text object.
        holder[0].setOnCancelListener(dialog->showDiagnosticsMenu());
        if(expected==null) text.setText("No active playback");
        else expected.requestCompanionMenu(false,null,null,reply->activity.runOnUiThread(()->{
            if(holder[0].isShowing()&&media()==expected&&expected.getCompanionMenuLoadId()==loadId) text.setText(reply.text);
        }));
        text.postDelayed(()->{
            if(holder[0].isShowing()&&"Reading optional stock playback snapshot...".contentEquals(text.getText()))
                text.setText("Playback load changed or service unavailable; Refresh snapshot");
        },3000);
    }

    private void addVideoBack(LinearLayout panel, final Dialog[] holder)
    {
        addPanelBack(panel,holder,this::showMain);
    }

    private void addPanelBack(LinearLayout panel, final Dialog[] holder,final Runnable parent)
    {
        LinearLayout actions = captionActions();
        Button back = captionButton("Back");
        back.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                if (holder[0] != null) holder[0].dismiss();
                parent.run();
            }
        });
        actions.addView(back);
        panel.addView(actions);
    }

    private void addVideoRow(LinearLayout panel, final Dialog[] holder,
            String label, String value, final Runnable action)
    {
        LinearLayout row = audioSettingRow(label, value);
        row.setOnClickListener(new View.OnClickListener()
        {
            @Override public void onClick(View view)
            {
                if (holder[0] != null) holder[0].dismiss();
                action.run();
            }
        });
        panel.addView(row);
    }

    private void choosePlaybackStats(final MediaCmd media)
    {
        MiniclientApplication.get().getMimDirectSession().refreshServerOutput(null);
        final boolean wasVisible = ActivePlayerProcessOverlay.isVisible();
        final String[] labels = {
                wasVisible ? "☑ Playback Stats enabled" : "☐ Playback Stats disabled",
                "Show compact until turned off",
                "Show detailed until turned off",
                "Show detailed for 30 seconds",
                "Hide Playback Stats",
                "Export redacted detailed snapshot",
                "Show statistics graphs until turned off"
        };
        choose("Playback Stats", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                switch (Integer.parseInt(index))
                {
                    case 0:
                        if (wasVisible)
                        {
                            ActivePlayerProcessOverlay.hide();
                            AppUtil.message("Playback Stats disabled");
                        }
                        else
                        {
                            ActivePlayerProcessOverlay.showPersistent(activity, media, true);
                            AppUtil.message("Detailed Playback Stats enabled");
                        }
                        break;
                    case 1:
                        ActivePlayerProcessOverlay.showPersistent(activity, media, false);
                        AppUtil.message("Compact Playback Stats enabled");
                        break;
                    case 2:
                        ActivePlayerProcessOverlay.showPersistent(activity, media, true);
                        AppUtil.message("Detailed Playback Stats enabled");
                        break;
                    case 3:
                        ActivePlayerProcessOverlay.showForThirtySeconds(activity, media, true);
                        AppUtil.message("Detailed Playback Stats enabled for 30 seconds");
                        break;
                    case 4:
                        ActivePlayerProcessOverlay.hide();
                        AppUtil.message("Playback Stats disabled");
                        break;
                    case 5:
                        String result = ActivePlayerProcessOverlay.exportCurrent(activity);
                        AppUtil.message(result.startsWith("ERROR:")
                                ? result : "Playback Stats exported: " + result);
                        break;
                    case 6:
                        ActivePlayerProcessOverlay.setMode(activity, media, "graphs");
                        AppUtil.message("Playback Stats graphs enabled");
                        break;
                    default: break;
                }
            }
        });
    }

    private String status(MediaCmd media)
    {
        MiniPlayerPlugin player = media == null ? null : media.getPlaya();
        String backend = player == null ? "No active player" : player.getClass().getSimpleName();
        String source = media != null && media.isDvdSessionPending() ? "DVD Push"
                : client.properties().getStreamingMode();
        return "Now: " + backend + " / " + source;
    }

    private MiniPlayerPlugin player(MediaCmd media)
    {
        return media == null ? null : media.getPlaya();
    }

    private String offsetStatus(String label, MiniPlayerPlugin player, boolean subtitle)
    {
        boolean supported = player != null && (subtitle
                ? player.supportsSubtitleOffset() : player.supportsAudioOffset());
        int value = player == null ? 0 : (subtitle
                ? player.getSubtitleOffsetMillis() : player.getAudioOffsetMillis());
        return label + " sync offset (" + (supported ? signed(value) + " ms" : "unsupported") + ")";
    }

    private String refreshStatus(MiniPlayerPlugin player)
    {
        DisplayRefreshController.Result result = DisplayRefreshController.inspect(activity, player);
        return "Refresh-rate matching (" + String.format(java.util.Locale.US,
                "%.3f fps / %.3f Hz", result.contentFps, result.displayHz) + ")";
    }

    private String refreshSettleStatus()
    {
        int value = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                client.properties().getInt(PrefStore.Keys.playback_refresh_settle_ms, 0));
        return "HDMI settle before local DVD video refresh (" + value + " ms)";
    }

    private String codecQueueingValue()
    {
        String value = ActivePlayerSessionOverrides.getCodecMode();
        if (value != null) return value;
        PlaybackSettingCatalog.Definition queue=queueDefinition();
        return queue.display(activity,client.properties().getString(queue.key,queue.defaultValue));
    }

    private String playerValue()
    {
        PlayerBackend backend = PlayerBackend.fromPreference(resolvedBackend());
        MiniPlayerPlugin active = player(media());
        String configured = ActivePlayerSessionOverrides.resolveGsyEngine(
                client.properties().getString(PrefStore.Keys.gsy_player_engine,
                        GSYPlayerEngine.DEFAULT_PREFERENCE));
        String requested=backend==PlayerBackend.GSYPLAYER?backend.displayName() + " ("
                + shortGsyEngineName(GSYPlayerEngine.fromPreference(configured)) + ")":backend.displayName();
        if(active==null)return "Saved / next: "+requested;
        PlayerBackend actual=active instanceof IJKMediaPlayerImpl?PlayerBackend.IJKPLAYER:
            active instanceof opensagetv.vibe.miniclient.android.video.exoplayer2.Exo2MediaPlayerImpl?PlayerBackend.EXOPLAYER:
            active instanceof opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl?PlayerBackend.GSYPLAYER:PlayerBackend.MEDIA3;
        String label=actual.displayName();
        if(actual==PlayerBackend.GSYPLAYER)label+=" ("+shortGsyEngineName(GSYPlayerEngine.fromPreference(((opensagetv.vibe.miniclient.android.video.gsy.GSYMediaPlayerImpl)active).getResolvedEngineForDebug()))+")";
        return actual==backend?label:label+" (next: "+requested+")";
    }

    private static String shortGsyEngineName(GSYPlayerEngine engine)
    {
        if (engine == GSYPlayerEngine.MEDIA3) return "Media3";
        if (engine == GSYPlayerEngine.SYSTEM) return "Android System";
        if (engine == GSYPlayerEngine.LEGACY_EXO) return "Legacy ExoPlayer";
        return "Auto";
    }

    private String dvdRepairValue()
    {
        return ActivePlayerSessionOverrides.resolveDvdTimestampRepair(
                client.properties().getString(
                        PrefStore.Keys.disc_mpeg2_timestamp_repair, "auto"));
    }

    private String deinterlaceValue()
    {
        String value = DeinterlacePolicy.normalize(DeinterlacePolicy.effectiveMode(
                ActivePlayerSessionOverrides.resolveVideoDeinterlaceMode(
                        client.properties().getString(PrefStore.Keys.video_deinterlace_mode, "")),
                fixedDeinterlaceChoices()));
        String label = "mim".equals(value) ? "MIM" : "off".equals(value) ? "Off"
                : "client_gpu".equals(value) ? "Client GPU" : "client".equals(value) ? "Client" : "Auto";
        String state = ClientDeinterlaceStatus.current(player(media()),
                MiniclientApplication.get().getMimDirectSession());
        String implementation = ClientDeinterlaceStatus.implementation(player(media()),
                MiniclientApplication.get().getMimDirectSession());
        return label + " (" + (label.equals(state) ? "" : state + " / ") + implementation + ")";
    }
    private boolean fixedDeinterlaceChoices()
    {
        return "fixed".equals(client.properties().getStreamingMode());
    }

    private String bufferPresetValue()
    {
        String value = ActivePlayerSessionOverrides.getBufferPreset();
        if (value == null)
            value = client.properties().getString(PrefStore.Keys.playback_buffer_preset,
                    ActivePlayerSessionOverrides.BUFFER_BALANCED);
        if (ActivePlayerSessionOverrides.BUFFER_LOW_LATENCY.equals(value)) return "Low latency";
        if (ActivePlayerSessionOverrides.BUFFER_RESILIENT.equals(value)) return "Resilient";
        return "Balanced";
    }

    private String refreshValue(MiniPlayerPlugin active)
    {
        DisplayRefreshController.Result result = DisplayRefreshController.inspect(activity, active);
        String policy = ActivePlayerSessionOverrides.resolveRefreshRatePolicy(
                client.properties().getString(PrefStore.Keys.playback_refresh_rate_matching,
                        DisplayRefreshController.OFF));
        return policy + " / " + String.format(Locale.US, "%.3f Hz", result.displayHz);
    }

    private String refreshSettleValue()
    {
        int value = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                client.properties().getInt(PrefStore.Keys.playback_refresh_settle_ms, 0));
        return value == 0 ? "Off" : value + " ms";
    }

    private void reopenVideoMenu()
    {
        activity.getWindow().getDecorView().postDelayed(new Runnable()
        {
            @Override public void run() { showMain(); }
        }, 75L);
    }

    private String audioTrackStatus(MiniPlayerPlugin player)
    {
        if (player == null) return "Audio track (no active player)";
        int[] ids = player.getAudioTrackIds();
        String[] labels = player.getAudioTrackLabels();
        int selected = player.getSelectedAudioTrack();
        for (int i = 0; i < ids.length && i < labels.length; i++)
            if (ids[i] == selected) return "Audio track (" + labels[i] + ")";
        return "Audio track (" + (ids.length == 0 ? "unavailable" : "automatic") + ")";
    }

    private String textSubtitleStatus(MiniPlayerPlugin player, MediaCmd media)
    {
        if (media != null && media.isDvdSessionPending())
            return "Text subtitle presentation (DVD bitmap subtitles unaffected)";
        if (player == null || !player.supportsTextSubtitlePresentation())
            return "Text subtitle presentation (unsupported)";
        return "Text subtitle presentation (safe "
                + player.getTextSubtitleSafeAreaPercent() + "%, size "
                + player.getTextSubtitleScalePercent() + "%, "
                + player.getTextSubtitleStyle() + ")";
    }

    private void chooseTextSubtitlePresentation(final MediaCmd media)
    {
        final MiniPlayerPlugin active = player(media);
        if (media != null && media.isDvdSessionPending())
        {
            AppUtil.message("DVD SPU subtitles are authored bitmaps; text style was not changed");
            reopenCaptionMenu();
            return;
        }
        if (active == null || !active.supportsTextSubtitlePresentation())
        {
            AppUtil.message("The active backend does not expose local text-subtitle styling");
            reopenCaptionMenu();
            return;
        }
        String[] rows = {
                "Bottom safe area: " + active.getTextSubtitleSafeAreaPercent() + "%",
                "Text size: " + active.getTextSubtitleScalePercent() + "%",
                "Style: " + active.getTextSubtitleStyle()
        };
        chooseCaptionChoice("Text subtitle appearance", rows, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int selected = Integer.parseInt(index);
                if (selected == 0) chooseTextSubtitleSafeArea(active);
                else if (selected == 1) chooseTextSubtitleScale(active);
                else chooseTextSubtitleStyle(active);
            }
        });
    }

    private void chooseTextSubtitleSafeArea(final MiniPlayerPlugin active)
    {
        final int[] values = { 8, 12, 18, 25, 32 };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i] + "% above bottom";
        chooseCaptionChoice("Text subtitle bottom safe area", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int value = values[Integer.parseInt(index)];
                chooseTextPresentationScope(active, value,
                        active.getTextSubtitleScalePercent(), active.getTextSubtitleStyle());
            }
        });
    }

    private void chooseTextSubtitleScale(final MiniPlayerPlugin active)
    {
        final int[] values = { 75, 100, 125, 150 };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i] + "%";
        chooseCaptionChoice("Text subtitle size", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                int value = values[Integer.parseInt(index)];
                chooseTextPresentationScope(active, active.getTextSubtitleSafeAreaPercent(),
                        value, active.getTextSubtitleStyle());
            }
        });
    }

    private void chooseTextSubtitleStyle(final MiniPlayerPlugin active)
    {
        final String[] labels=PlaybackSettingCatalog.SUBTITLE_STYLE.labels(activity);
        final String[] values=PlaybackSettingCatalog.SUBTITLE_STYLE.values(activity);
        chooseCaptionChoice(PlaybackSettingCatalog.SUBTITLE_STYLE.title, labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                String value = values[Integer.parseInt(index)];
                chooseTextPresentationScope(active, active.getTextSubtitleSafeAreaPercent(),
                        active.getTextSubtitleScalePercent(), value);
            }
        });
    }

    private void chooseTextPresentationScope(final MiniPlayerPlugin active,
            final int safeArea, final int scale, final String style)
    {
        chooseCaptionChoice("Apply text subtitle appearance",
                new String[] { "This playback session",
                        "Save as this device's default" }, new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        int which = Integer.parseInt(index);
                        ActivePlayerSessionOverrides.setSubtitleSafeAreaPercent(safeArea);
                        ActivePlayerSessionOverrides.setSubtitleTextScalePercent(scale);
                        ActivePlayerSessionOverrides.setSubtitleTextStyle(style);
                        if (which == 1)
                        {
                            client.properties().setInt(
                                    PrefStore.Keys.playback_subtitle_safe_area_percent,
                                    safeArea);
                            client.properties().setInt(
                                    PrefStore.Keys.playback_subtitle_text_scale_percent,
                                    scale);
                            client.properties().setString(
                                    PrefStore.Keys.playback_subtitle_text_style, style);
                        }
                        AppUtil.message(active.setTextSubtitlePresentation(
                                safeArea, scale, style)
                                ? "Text subtitle appearance applied immediately"
                                : "Text subtitle appearance was rejected");
                        reopenCaptionMenu();
                    }
                });
    }

    private String audioOutputStatus(MiniPlayerPlugin player)
    {
        return "Audio output / passthrough (" + (player == null
                ? "no active player" : player.getAudioOutputSummary()) + ")";
    }

    private void chooseAudioTrack(MediaCmd media)
    {
        chooseAudioTrack(media, false);
    }

    private void chooseAudioTrack(MediaCmd media, final boolean returnToAudioMenu)
    {
        final MiniPlayerPlugin active = player(media);
        if (active == null)
        {
            AppUtil.message("Audio-track selection requires an active player");
            return;
        }
        final int[] ids = active.getAudioTrackIds();
        final String[] labels = active.getAudioTrackLabels();
        if (ids.length == 0 || labels.length != ids.length)
        {
            AppUtil.message("This active stream does not expose selectable audio tracks");
            return;
        }
        ValueSetter setter = new ValueSetter()
        {
            @Override public void set(String index)
            {
                int selected = Integer.parseInt(index);
                active.setAudioTrack(ids[selected]);
                AppUtil.message("Audio track applied: " + labels[selected]);
                if (returnToAudioMenu) showAudioMenu();
                else showMain();
            }
        };
        if (returnToAudioMenu)
            chooseAudioChoice("Active audio track", labels, setter, new Runnable()
            {
                @Override public void run() { showAudioMenu(); }
            });
        else
            choose("Active audio track", labels, setter);
    }

    private void chooseAudioPassthrough(MediaCmd media)
    {
        final MiniPlayerPlugin active = player(media);
        if (active == null || !active.supportsAudioPassthroughControl())
        {
            AppUtil.message("Passthrough cannot be changed safely for "
                    + (active == null ? "this inactive player" : active.getAudioOutputSummary())
                    + "; no output mode was changed");
            return;
        }
        choose("Audio passthrough", new String[] { "Off", "On" }, new ValueSetter()
        {
            @Override public void set(String index)
            {
                boolean enabled = Integer.parseInt(index) == 1;
                AppUtil.message(active.setAudioPassthroughEnabled(enabled)
                        ? "Audio passthrough changed for this playback session"
                        : "Audio passthrough change was rejected by the active output");
                showMain();
            }
        });
    }

    private void showAudioMenu()
    {
        final MiniPlayerPlugin active=player(media());
        PlaybackSettingsPanel.Section audio=PlaybackSettingsPanel.section("audio","Audio Settings",true);
        audio.add("stream","Audio Stream",audioTrackValue(active)+" • Immediate",active!=null,()->chooseAudioTrack(media(),true));
        addSetting(audio,PlaybackSettingCatalog.AUDIO_OUTPUT,active==null?"Unavailable":active.isAudioPassthroughEnabled()?"Encoded passthrough":"Decoded PCM stereo",active!=null&&active.supportsAudioPassthroughControl(),"Unavailable on the active output",()->chooseDecodedAudio(media(),active));
        addSetting(audio,PlaybackSettingCatalog.PASSTHROUGH_OFFSET,active!=null&&active.isPassthroughAudioOffsetEnabled()?"On":"Off",active!=null&&active.isAudioPassthroughEnabled()&&active.supportsPassthroughAudioOffset(),"Requires supported encoded passthrough",()->choosePassthroughAudioOffset(active));
        addSetting(audio,PlaybackSettingCatalog.AUDIO_OFFSET,active==null?"Unavailable":audioOffsetValue(active.getAudioOffsetMillis()),active!=null&&active.supportsAudioOffset(),"Unavailable on this output; enable supported passthrough offset first",()->chooseAudioOffset(media(),active));
        PlaybackSettingsPanel.Section test=PlaybackSettingsPanel.section("test","Test and Recovery",false);
        test.add("sync","A/V Sync Test",active!=null&&active.isAudioPassthroughEnabled()?"Bouncing ball / encoded":"Bouncing ball / PCM",active!=null&&(active.supportsAudioOffset()||active.supportsPassthroughAudioOffset()),this::showAvSyncTestDialog)
            .add("restart","Restart Audio Output",active!=null&&active.supportsAudioOutputRestart()?"Keep current settings":"Unavailable on active output",active!=null&&active.supportsAudioOutputRestart(),()->{AppUtil.message(active.restartAudioOutput()?"Restarting audio output with current settings":"Audio output restart could not be started");showAudioMenu();});
        PlaybackSettingsPanel.Section defaults=PlaybackSettingsPanel.section("defaults","Device Defaults",false);
        defaults.add("save","Save as Device Defaults","Save current audio output and supported offsets",active!=null,()->confirm("Save audio defaults?","Save this output and supported audio offset as this device's defaults? Other settings are unchanged.",()->{
            client.properties().setBoolean(PrefStore.Keys.disable_audio_passthrough,!active.isAudioPassthroughEnabled());
            client.properties().setBoolean(PrefStore.Keys.playback_passthrough_audio_offset_enabled,active.isPassthroughAudioOffsetEnabled());
            if(active.supportsAudioOffset())client.properties().setInt(PrefStore.Keys.playback_audio_offset_ms,active.getAudioOffsetMillis());
            AppUtil.message("Audio settings saved as this device's default");showAudioMenu();
        },this::showAudioMenu));
        showPanel("audio","Audio Settings","Adjustments affect this session; saving device defaults is explicit.",java.util.Arrays.asList(audio,test,defaults),fromHub?this::showHubMenu:null);
    }

    private void showAvSyncTestDialog()
    {
        final MiniPlayerPlugin active = player(media());
        if (active == null || (!active.supportsAudioOffset()
                && !active.supportsPassthroughAudioOffset()))
        {
            AppUtil.message("The active backend cannot apply a calibrated audio offset");
            return;
        }
        Media3AvSyncTestDialog.show(activity, active,
                new Media3AvSyncTestDialog.Listener()
                {
                    @Override public void onFinished(int offsetMs)
                    {
                        ActivePlayerSessionOverrides.setAudioOffsetMs(offsetMs);
                        if (active.isAudioPassthroughEnabled()
                                && offsetMs != 0
                                && !active.isPassthroughAudioOffsetEnabled())
                        {
                            ActivePlayerSessionOverrides
                                    .setPassthroughAudioOffsetEnabled(true);
                            active.setPassthroughAudioOffsetEnabled(true);
                        }
                        active.setAudioOffsetMillis(offsetMs);
                        showAudioMenu();
                    }
                });
    }

    private void choosePassthroughAudioOffset(final MiniPlayerPlugin active)
    {
        if (active == null || !active.isAudioPassthroughEnabled()
                || !active.supportsPassthroughAudioOffset())
        {
            AppUtil.message(active == null ? "No active audio output"
                    : !active.isAudioPassthroughEnabled()
                    ? "Select encoded passthrough before enabling its clock offset"
                    : "Passthrough clock offset is unavailable in this player");
            showAudioMenu();
            return;
        }
        final boolean previous = active.isPassthroughAudioOffsetEnabled();
        final String[] labels = {
                previous ? "Off" : "Off  [active]",
                previous ? "On  [active]" : "On"
        };
        chooseAudioChoice("Passthrough audio offset", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final boolean enabled = Integer.parseInt(index) == 1;
                boolean accepted = active.setPassthroughAudioOffsetEnabled(enabled);
                AppUtil.message(accepted
                        ? (enabled ? "Enabling passthrough clock offset"
                        : "Disabling passthrough clock offset")
                        : "The active output rejected this setting");
                activity.getWindow().getDecorView().postDelayed(new Runnable()
                {
                    @Override public void run() { showAudioMenu(); }
                }, accepted ? 700L : 0L);
            }
        }, new Runnable()
        {
            @Override public void run() { showAudioMenu(); }
        });
    }

    private void chooseDecodedAudio(final MediaCmd media, final MiniPlayerPlugin active)
    {
        if (active == null || !active.supportsAudioPassthroughControl())
        {
            AppUtil.message(active == null ? "No active audio output"
                    : "This player cannot enforce decoded PCM: "
                    + active.getAudioOutputSummary());
            showAudioMenu();
            return;
        }
        final boolean previousPassthrough = active.isAudioPassthroughEnabled();
        final String[] base=PlaybackSettingCatalog.AUDIO_OUTPUT.labels(activity);
        final String[] labels = {base[0]+(!previousPassthrough?"  [active]":""),base[1]+(previousPassthrough?"  [active]":"")};
        chooseAudioChoice(PlaybackSettingCatalog.AUDIO_OUTPUT.title, labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final boolean passthrough = Integer.parseInt(index) == 1;
                ActivePlayerSessionOverrides.setAudioPassthroughEnabled(passthrough);
                boolean accepted = active.setAudioPassthroughEnabled(passthrough);
                if (!accepted)
                    ActivePlayerSessionOverrides.setAudioPassthroughEnabled(previousPassthrough);
                AppUtil.message(accepted
                        ? (passthrough
                        ? "Switching this program to encoded passthrough"
                        : "Switching this program to decoded PCM stereo")
                        : "The active output rejected this mode; nothing changed");
                activity.getWindow().getDecorView().postDelayed(new Runnable()
                {
                    @Override public void run() { showAudioMenu(); }
                }, accepted ? 700L : 0L);
            }
        }, new Runnable()
        {
            @Override public void run() { showAudioMenu(); }
        });
    }

    private LinearLayout audioSettingRow(String label, String value)
    {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setFocusable(true);
        row.setClickable(true);
        TypedValue selectable = new TypedValue();
        if (activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, selectable, true)
                && selectable.resourceId != 0)
            row.setBackgroundResource(selectable.resourceId);

        TextView name = new TextView(activity);
        name.setText(label);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17.0f);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        name.setIncludeFontPadding(false);
        name.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                name, 11, 17, 1, TypedValue.COMPLEX_UNIT_SP);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1.6f));
        TextView current = new TextView(activity);
        current.setText(value);
        current.setTextColor(Color.WHITE);
        current.setTextSize(16.0f);
        current.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        current.setIncludeFontPadding(false);
        current.setSingleLine(true);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                current, 11, 16, 1, TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams currentParams =
                new LinearLayout.LayoutParams(0, dp(42), 1.0f);
        currentParams.leftMargin = dp(24);
        row.addView(current, currentParams);
        return row;
    }

    private String audioTrackValue(MiniPlayerPlugin active)
    {
        String status = audioTrackStatus(active);
        int open = status.indexOf('(');
        return open >= 0 && status.endsWith(")")
                ? status.substring(open + 1, status.length() - 1) : status;
    }

    private String audioOffsetValue(int valueMs)
    {
        return String.format(Locale.US, "%+.3f s", valueMs / 1000.0f)
                .replace("+0.000", "0.000");
    }

    private void chooseAudioOffset(final MediaCmd media, final MiniPlayerPlugin active)
    {
        if (active == null || !active.supportsAudioOffset())
        {
            AppUtil.message("Audio offset requires decoded PCM, or enabled passthrough "
                    + "clock offset on Media3 or legacy ExoPlayer");
            showAudioMenu();
            return;
        }
        showAudioOffsetSlider(active);
    }

    private void showAudioOffsetSlider(final MiniPlayerPlugin active)
    {
        final int minMs = -4_000;
        final int maxMs = 4_000;
        final int stepMs = 25;
        int currentMs = Math.max(minMs, Math.min(maxMs, active.getAudioOffsetMillis()));
        currentMs = Math.round(currentMs / (float) stepMs) * stepMs;
        final int[] valueMs = { currentMs };
        final boolean[] finished = { false };

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.setFocusableInTouchMode(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xe61b1b1b);
        background.setCornerRadius(dp(3));
        background.setStroke(dp(1), 0xff505050);
        root.setBackground(background);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText("Audio offset");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20.0f);
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        final TextView status = new TextView(activity);
        status.setText(audioOffsetSliderLabel(currentMs));
        status.setTextColor(Color.WHITE);
        status.setTextSize(18.0f);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        status.setIncludeFontPadding(false);
        header.addView(status, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        root.addView(header);

        LinearLayout sliderRow = new LinearLayout(activity);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView left = audioOffsetArrow("\u2039");
        TextView right = audioOffsetArrow("\u203a");
        final SeekBar slider = new SeekBar(activity);
        slider.setMax((maxMs - minMs) / stepMs);
        slider.setProgress((currentMs - minMs) / stepMs);
        slider.setFocusable(false);
        sliderRow.addView(left, new LinearLayout.LayoutParams(dp(28), dp(38)));
        sliderRow.addView(slider, new LinearLayout.LayoutParams(0, dp(38), 1.0f));
        sliderRow.addView(right, new LinearLayout.LayoutParams(dp(28), dp(38)));
        root.addView(sliderRow);

        TextView help = new TextView(activity);
        help.setText("Left/Right: adjust 0.025 s     Back: return (session)");
        help.setTextColor(Color.LTGRAY);
        help.setTextSize(12.0f);
        help.setGravity(Gravity.CENTER);
        help.setIncludeFontPadding(false);
        root.addView(help, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser)
            {
                int requested = minMs + progress * stepMs;
                if (requested == valueMs[0]) return;
                valueMs[0] = requested;
                ActivePlayerSessionOverrides.setAudioOffsetMs(requested);
                if (!active.setAudioOffsetMillis(requested))
                {
                    AppUtil.message("Audio offset was not applied");
                    return;
                }
                status.setText(audioOffsetSliderLabel(requested));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        dialog.setOnKeyListener(new DialogInterface.OnKeyListener()
        {
            @Override public boolean onKey(DialogInterface ignored, int keyCode,
                    KeyEvent event)
            {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                {
                    if (event.getAction() == KeyEvent.ACTION_DOWN)
                    {
                        int delta = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                        slider.setProgress(Math.max(0,
                                Math.min(slider.getMax(), slider.getProgress() + delta)));
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK)
                {
                    if (event.getAction() == KeyEvent.ACTION_UP && !finished[0])
                    {
                        finished[0] = true;
                        dialog.dismiss();
                        showAudioMenu();
                    }
                    return true;
                }
                return false;
            }
        });
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null)
        {
            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.15f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params = window.getAttributes();
            params.y = dp(12);
            window.setAttributes(params);
            window.setLayout(Math.min((int) (metrics.widthPixels * 0.44f), dp(760)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        root.requestFocus();
    }

    private TextView audioOffsetArrow(String label)
    {
        TextView arrow = new TextView(activity);
        arrow.setText(label);
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(34.0f);
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);
        return arrow;
    }

    private String audioOffsetSliderLabel(int valueMs)
    {
        if (valueMs == 0) return "0.000 s";
        return (valueMs > 0 ? "Later by: " : "Earlier by: ")
                + String.format(Locale.US, "%.3f s", Math.abs(valueMs) / 1000.0f);
    }

    private void chooseAudioChoice(String title, final String[] labels,
            final ValueSetter setter, final Runnable backAction)
    {
        PlaybackSettingsPanel.Section options=PlaybackSettingsPanel.section("choices","Choices",true);
        for(int i=0;i<labels.length;i++){
            final int selected=i;
            options.add("choice/"+i,labels[i],"",true,()->setter.set(Integer.toString(selected)));
        }
        // Choice lists deliberately do not remember a previous Save selection.
        // Scope prompts always focus This playback session; confirmations Cancel.
        PlaybackSettingsPanel.show(activity,"choice/"+title,title,"",java.util.Arrays.asList(options),backAction);
    }

    private void chooseVideoChoice(String title, String[] labels, ValueSetter setter)
    {
        chooseAudioChoice(title, labels, setter, new Runnable()
        {
            @Override public void run() { showMain(); }
        });
    }

    private void chooseRefreshRate(final MediaCmd media)
    {
        final String[] labels = PlaybackSettingCatalog.REFRESH.labels(activity);
        final String[] values = PlaybackSettingCatalog.REFRESH.values(activity);
        chooseVideoChoice(PlaybackSettingCatalog.REFRESH.title, labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final String value = values[Integer.parseInt(index)];
                chooseScope("Refresh-rate matching", PrefStore.Keys.playback_refresh_rate_matching,
                        value, new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            {
                                ActivePlayerSessionOverrides.setRefreshRatePolicy(selectedValue);
                                DisplayRefreshController.Result result = DisplayRefreshController.apply(
                                        activity, player(media), selectedValue);
                                int settleMs = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                                        client.properties().getInt(
                                                PrefStore.Keys.playback_refresh_settle_ms, 0));
                                boolean scheduled = result.applied && result.modeChanged
                                        && DisplayRefreshController.scheduleLocalDvdOutputRefresh(
                                                activity, media, settleMs);
                                AppUtil.message((result.applied ? "Applied: " : "Not applied: ")
                                        + result.summary() + (scheduled
                                        ? "; local DVD video refresh in " + settleMs + " ms" : ""));
                            }
                        }, false);
            }
        });
    }

    private void chooseRefreshSettle()
    {
        final String[] values=PlaybackSettingCatalog.SETTLE.values(activity);
        String[] labels=PlaybackSettingCatalog.SETTLE.labels(activity);
        chooseVideoChoice(PlaybackSettingCatalog.SETTLE.title, labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final int value = Integer.parseInt(values[Integer.parseInt(index)]);
                chooseScope("HDMI settle", PrefStore.Keys.playback_refresh_settle_ms,
                        Integer.toString(value), new ValueSetter()
                        {
                            @Override public void set(String ignored)
                            {
                                ActivePlayerSessionOverrides.setRefreshSettleMs(value);
                                AppUtil.message("HDMI settle applies to the next active DVD display-mode change");
                            }
                        }, false);
            }
        });
    }

    private void chooseBackend(final MediaCmd media)
    {
        String[] values=PlaybackSettingCatalog.PLAYER.values(activity);
        chooseVideoChoice(PlaybackSettingCatalog.PLAYER.title,PlaybackSettingCatalog.PLAYER.labels(activity),index->{
            PlayerBackend selected=PlayerBackend.fromPreference(values[Integer.parseInt(index)]);
            if(selected==PlayerBackend.GSYPLAYER){
                chooseVideoChoice(PlaybackSettingCatalog.GSY_ENGINE.title,PlaybackSettingCatalog.GSY_ENGINE.labels(activity),engine->{
                    String chosen=PlaybackSettingCatalog.GSY_ENGINE.values(activity)[Integer.parseInt(engine)];
                    applyBackendChoice(media,selected,GSYPlayerEngine.fromPreference(chosen));
                });
            }else applyBackendChoice(media,selected,null);
        });
    }
    private void applyBackendChoice(MediaCmd media,PlayerBackend selected,GSYPlayerEngine gsyEngine){
        boolean pinnedDvd=media!=null&&media.isDvdSessionPending()&&selected!=PlayerBackend.MEDIA3;
        chooseVideoChoice("Player: "+selected.displayName()+" • "+(pinnedDvd?"Next non-DVD playback":PlaybackSettingCatalog.PLAYER.timing.label),
            new String[]{"This playback session","Save as this device's default"},scope->{
                ActivePlayerSessionOverrides.setBackend(selected.preferenceValue());
                if(gsyEngine!=null)ActivePlayerSessionOverrides.setGsyEngine(gsyEngine.preferenceValue());
                if(Integer.parseInt(scope)==1){
                    client.properties().setString(PrefStore.Keys.default_player,selected.preferenceValue());
                    if(gsyEngine!=null)client.properties().setString(PrefStore.Keys.gsy_player_engine,gsyEngine.preferenceValue());
                }
                if(pinnedDvd)AppUtil.message("Native DVD remains on Media3; this player applies to the next non-DVD video");
                else reload(media());
                reopenVideoMenu();
            });
    }

    private void chooseDecoding()
    {
        final DecodingMethod[] values = DecodingMethod.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = values[i].displayName();
        chooseVideoChoice(PlaybackSettingCatalog.DECODING.title, PlaybackSettingCatalog.DECODING.labels(activity), new ValueSetter()
        {
            @Override public void set(String value)
            {
                DecodingMethod selected = values[Integer.parseInt(value)];
                chooseScope("Video decoding policy", PrefStore.Keys.decoding_method,
                        selected.preferenceValue(), new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            { ActivePlayerSessionOverrides.setDecodingMethod(selectedValue); }
                        }, true);
            }
        });
    }

    private void chooseCodecQueueing()
    {
        final PlaybackSettingCatalog.Definition definition=queueDefinition();
        final String[] values=definition.values(activity);
        chooseVideoChoice(definition.title, definition.labels(activity), new ValueSetter()
        {
            @Override public void set(String value)
            {
                String key=definition.key;
                chooseScope(definition.title, key, values[Integer.parseInt(value)], new ValueSetter()
                {
                    @Override public void set(String selectedValue)
                    { ActivePlayerSessionOverrides.setCodecMode(selectedValue); }
                }, true);
            }
        });
    }

    private void chooseDvdRepair()
    {
        final String[] values=PlaybackSettingCatalog.DVD_REPAIR.values(activity);
        chooseVideoChoice(PlaybackSettingCatalog.DVD_REPAIR.title,PlaybackSettingCatalog.DVD_REPAIR.labels(activity), new ValueSetter()
        {
            @Override public void set(String value)
            {
                chooseScope(PlaybackSettingCatalog.DVD_REPAIR.title, PrefStore.Keys.disc_mpeg2_timestamp_repair,
                        values[Integer.parseInt(value)], new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            { ActivePlayerSessionOverrides.setDvdTimestampRepair(selectedValue); }
                        }, true);
            }
        });
    }

    private void chooseDeinterlace()
    {
        final boolean fixed = fixedDeinterlaceChoices();
        final String[] base=PlaybackSettingCatalog.DEINTERLACE.labels(activity);
        final String[] labels = fixed ? new String[] { base[0],base[1],base[2],base[3],"MIM" } : base;
        final String[] values = fixed ? new String[] { "auto", "off", "client", "client_gpu", "mim" }
                : new String[] { "auto", "off", "client", "client_gpu" };
        chooseVideoChoice(PlaybackSettingCatalog.DEINTERLACE.title, labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final String value = values[Integer.parseInt(index)];
                if ("mim".equals(value))
                {
                    String server = MiniclientApplication.get().getMimDirectSession().stateForDiagnostics();
                      // An owned producer may carry a diagnostic suffix after
                      // a harmless redundant startup seek. Its accepted request
                      // proves availability; a literal state label does not.
                      String accepted = MiniclientApplication.get().getMimDirectSession()
                              .acceptedDeinterlaceForDiagnostics();
                      if (!("ready_transcode".equals(server) || !accepted.isEmpty()))
                    {
                        AppUtil.message("MIM deinterlacing requires an available Fixed Direct Transcode session; current setting unchanged");
                        reopenVideoMenu();
                        return;
                    }
                }
                chooseScope("Deinterlace", PrefStore.Keys.video_deinterlace_mode,
                        value, new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            {
                                ActivePlayerSessionOverrides.setVideoDeinterlaceMode(selectedValue);
                                if ("off".equals(selectedValue))
                                    AppUtil.message("Optional filters off; Android codec field processing remains device-controlled");
                                else if ("client".equals(selectedValue))
                                    AppUtil.message("Client: backend software filter or device-controlled fields; applies on next playback");
                                else if ("client_gpu".equals(selectedValue))
                                    AppUtil.message("Client GPU: hardware decode and GPU filtering on supported backends; applies on next playback");
                            }
                        }, true);
            }
        });
    }

    private void chooseBufferPreset(final MediaCmd media)
    {
        final String[] labels=PlaybackSettingCatalog.BUFFER.labels(activity);
        final String[] values=PlaybackSettingCatalog.BUFFER.values(activity);
        chooseVideoChoice(PlaybackSettingCatalog.BUFFER.title, labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                chooseScope("Source buffering preset", PrefStore.Keys.playback_buffer_preset,
                        values[Integer.parseInt(index)], new ValueSetter()
                        {
                            @Override public void set(String selectedValue)
                            {
                                ActivePlayerSessionOverrides.setBufferPreset(selectedValue);
                                if (media != null && media.isDvdSessionPending())
                                    AppUtil.message("Saved for Pull/SMB; native DVD uses its bounded interactive buffer");
                            }
                        }, media == null || !media.isDvdSessionPending());
            }
        });
    }

    private void chooseCaptions(final MediaCmd media)
    {
        final MiniPlayerPlugin active=player(media);
        PlaybackSettingsPanel.Section broadcast=PlaybackSettingsPanel.section("broadcast","Broadcast Captions",true);
        List<String> available=new ArrayList<>();addAvailableCaptionRows(available,media,active);
        broadcast.add("available","Available Broadcast CC",available.isEmpty()?"None reported":android.text.TextUtils.join("; ",available),false,null);
        broadcast.add("mode","Broadcast CC",media==null?"No active SageTV connection":captionModeLabel(media.getLegacyServerCaptionMode())+" • Immediate / STV authority",media!=null,()->chooseCaptionMode(media,active));
        broadcast.add("refresh","Refresh Caption Services","Read the current stream inventory",media!=null,()->chooseCaptions(media()));
        for(int channel=1;channel<=2;channel++){
            final int slot=channel;
            broadcast.add("type"+slot,"CC"+slot+" Type",captionTypeLabel(captionType(slot))+" • Saved slot mapping",media!=null,()->confirm("Save CC"+slot+" mapping?","This changes the device's saved caption slot mapping, not just the current programme.",()->chooseCaptionType(media,active,slot),()->chooseCaptions(media())));
            broadcast.add("language"+slot,"CC"+slot+" Language",captionLanguageLabel(captionLanguage(slot))+" • Saved slot mapping",media!=null,()->confirm("Save CC"+slot+" language?","This changes the device's saved caption slot language.",()->chooseCaptionLanguage(media,active,slot),()->chooseCaptions(media())));
        }
        PlaybackSettingsPanel.Section subtitles=PlaybackSettingsPanel.section("subtitles","Subtitles",true);
        subtitles.add("stream","Subtitle Stream",subtitleTrackValue(active,media)+" • Immediate",active!=null,()->chooseSubtitleTrack(media,active));
        subtitles.add("appearance","Subtitle Appearance",subtitleAppearanceValue(active,media)+" • Immediate",active!=null&&active.supportsTextSubtitlePresentation(),()->chooseTextSubtitlePresentation(media));
        addSetting(subtitles,PlaybackSettingCatalog.SUBTITLE_OFFSET,active==null?"Unavailable":signed(active.getSubtitleOffsetMillis())+" ms",active!=null&&active.supportsSubtitleOffset(),"Unavailable on this backend",()->chooseOffset(media,true));
        showPanel("captions","Captions / Subtitles","Programme tracks are session choices; virtual CC slot mappings are saved explicitly.",java.util.Arrays.asList(broadcast,subtitles),fromHub?this::showHubMenu:null);
    }

    private String subtitleTrackValue(MiniPlayerPlugin active, MediaCmd media)
    {
        if (media != null && media.isDvdSessionPending()) return "DVD/STV controlled";
        if (active == null) return "Unavailable";
        int selected = active.getSelectedSubtitleTrack();
        if (selected == MiniPlayerPlugin.DISABLE_TRACK) return "Off";
        for (SubtitleTrack track : active.getSubtitleTracks())
            if (track != null && track.getIndex() == selected
                    && !CaptionSlotPolicy.isBroadcastCaption(track))
                return compactSubtitleTrackLabel(track);
        return "Broadcast CC controls active";
    }

    private String subtitleAppearanceValue(MiniPlayerPlugin active, MediaCmd media)
    {
        if (media != null && media.isDvdSessionPending()) return "DVD bitmap";
        if (active == null || !active.supportsTextSubtitlePresentation()) return "Unavailable";
        return "safe " + active.getTextSubtitleSafeAreaPercent() + "% / size "
                + active.getTextSubtitleScalePercent() + "%";
    }

    private void chooseSubtitleTrack(final MediaCmd media, final MiniPlayerPlugin active)
    {
        if (media != null && media.isDvdSessionPending())
        {
            AppUtil.message("DVD subtitle streams are selected by the SageTV/DVD subtitle menu");
            reopenCaptionMenu();
            return;
        }
        if (active == null)
        {
            AppUtil.message("Subtitle selection requires an active player");
            reopenCaptionMenu();
            return;
        }
        final List<Integer> ids = new ArrayList<Integer>();
        final List<String> labels = new ArrayList<String>();
        ids.add(MiniPlayerPlugin.DISABLE_TRACK);
        labels.add(active.getSelectedSubtitleTrack() == MiniPlayerPlugin.DISABLE_TRACK
                ? "Off  [active]" : "Off");
        for (SubtitleTrack track : active.getSubtitleTracks())
        {
            if (track == null || CaptionSlotPolicy.isBroadcastCaption(track)) continue;
            ids.add(track.getIndex());
            labels.add(compactSubtitleTrackLabel(track)
                    + (track.getIndex() == active.getSelectedSubtitleTrack()
                    ? "  [active]" : ""));
        }
        chooseCaptionChoice("Subtitle stream (SRT/PGS/DVD)",
                labels.toArray(new String[labels.size()]), new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        int id = ids.get(Integer.parseInt(index));
                        active.setSubtitleTrack(id);
                        AppUtil.message(id == MiniPlayerPlugin.DISABLE_TRACK
                                ? "Subtitles off" : "Subtitle stream selected");
                        reopenCaptionMenu();
                    }
                });
    }

    private static String compactSubtitleTrackLabel(SubtitleTrack track)
    {
        String language = CaptionSlotPolicy.reliableLanguage(track);
        String prefix = language.isEmpty() ? "Unknown" : captionLanguageLabel(language);
        SubtitleCodec codec = track.getSubtitleCodec();
        if (codec == SubtitleCodec.SUBRIP) return prefix + " SRT";
        if (codec == SubtitleCodec.PGS) return prefix + " PGS";
        return prefix + " " + (codec == null ? "subtitle" : codec.getName());
    }

    private void chooseCaptionMode(final MediaCmd media, final MiniPlayerPlugin active)
    {
        final String[] labels = { "OFF", "CC1", "CC2", "STV", "DVB" };
        final String[] values = { "off", "cc1", "cc2", "stv", "dvb" };
        chooseCaptionChoice("Broadcast captions (CC)", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                String value = values[Integer.parseInt(index)];
                media.setLegacyServerCaptionMode(value);
                if ("off".equals(value) && active != null)
                    active.setSubtitleTrack(MiniPlayerPlugin.DISABLE_TRACK);
                if ("dvb".equals(value) && active != null
                        && !active.applyDvbCaptionTrack())
                    AppUtil.message("No DVB bitmap subtitle service is available in this video");
                if ("stv".equals(value) && !media.hasSageTvClosedCaptionState())
                    AppUtil.message(activity.getString(
                            R.string.caption_control_legacy_stv_unavailable));
                else if ("stv".equals(value) && media.hasSageTvClosedCaptionState())
                    AppUtil.message(activity.getString(
                            R.string.caption_control_stv_authoritative));
                else
                    AppUtil.message("Broadcast CC: " + captionModeLabel(value));
                reopenCaptionMenu();
            }
        });
    }

    private void chooseCaptionType(final MediaCmd media, final MiniPlayerPlugin active,
            final int channel)
    {
        final List<String> values = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        addCaptionType(values, labels, CaptionSlotPolicy.TYPE_AUTO);
        SubtitleTrack[] tracks = verifiedCaptionTracks(active);
        // These are virtual broadcast slots. Optional public-API state relay
        // makes STV CC selection usable even for a locally rendered bitmap.
        String[] preferredTypes = {
                CaptionSlotPolicy.TYPE_CEA708,
                CaptionSlotPolicy.TYPE_CEA608,
                CaptionSlotPolicy.TYPE_TELETEXT,
                CaptionSlotPolicy.TYPE_DVB
        };
        for (String preferredType : preferredTypes)
        {
            SubtitleCodec preferredCodec = CaptionSlotPolicy.codecForType(preferredType);
            for (SubtitleTrack track : tracks)
            {
                if (track != null && track.getSubtitleCodec() == preferredCodec)
                {
                    addCaptionType(values, labels, preferredType);
                    break;
                }
            }
        }
        String current = captionType(channel);
        addCaptionType(values, labels, current);
        chooseCaptionChoice("CC" + channel + " Type",
                labels.toArray(new String[labels.size()]),
                new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        client.properties().setString(captionTypeKey(channel),
                                values.get(Integer.parseInt(index)));
                        reapplyCaptionSlot(media, channel);
                        reopenCaptionMenu();
                    }
                });
    }

    private static void addCaptionType(List<String> values, List<String> labels, String type)
    {
        String normalized = CaptionSlotPolicy.normalizeType(type);
        if (values.contains(normalized)) return;
        values.add(normalized);
        labels.add(captionTypeLabel(normalized));
    }

    private void chooseCaptionLanguage(final MediaCmd media, MiniPlayerPlugin active,
            final int channel)
    {
        final List<String> values = new ArrayList<String>();
        final List<String> labels = new ArrayList<String>();
        values.add("");
        labels.add("Auto");
        SubtitleCodec requestedCodec = CaptionSlotPolicy.codecForType(captionType(channel));
        SubtitleTrack[] tracks = verifiedCaptionTracks(active);
        List<String> discoveredLanguages = new ArrayList<String>();
        for (SubtitleTrack track : tracks)
        {
            if (!CaptionSlotPolicy.isBroadcastCaption(track)
                    || track.getSubtitleCodec() == SubtitleCodec.DVB
                    || (requestedCodec != null && requestedCodec != track.getSubtitleCodec()))
                continue;
            String language = CaptionSlotPolicy.reliableLanguage(track);
            if (language.isEmpty() || discoveredLanguages.contains(language)) continue;
            discoveredLanguages.add(language);
        }
        Collections.sort(discoveredLanguages, new Comparator<String>()
        {
            @Override public int compare(String left, String right)
            {
                boolean leftEnglish = isEnglishLanguage(left);
                boolean rightEnglish = isEnglishLanguage(right);
                if (leftEnglish != rightEnglish) return leftEnglish ? -1 : 1;
                return captionLanguageLabel(left).compareToIgnoreCase(captionLanguageLabel(right));
            }
        });
        for (String language : discoveredLanguages)
        {
            values.add(language);
            labels.add(captionLanguageLabel(language));
        }
        String current = TrackPreferencePolicy.normalizeLanguage(captionLanguage(channel));
        if (!current.isEmpty() && !values.contains(current))
        {
            values.add(current);
            labels.add(captionLanguageLabel(current) + " (not in current video)");
        }
        chooseCaptionChoice("CC" + channel + " Language",
                labels.toArray(new String[labels.size()]), new ValueSetter()
                {
                    @Override public void set(String index)
                    {
                        client.properties().setString(captionLanguageKey(channel),
                                values.get(Integer.parseInt(index)));
                        reapplyCaptionSlot(media, channel);
                        reopenCaptionMenu();
                    }
                });
    }

    private LinearLayout captionPanel()
    {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), 0, dp(20), dp(6));
        return panel;
    }

    private TextView captionRow(String label, boolean selectable)
    {
        TextView text = new TextView(activity);
        text.setText(label);
        text.setTextColor(selectable ? Color.WHITE : Color.LTGRAY);
        text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        text.setIncludeFontPadding(false);
        text.setHorizontallyScrolling(false);
        text.setAlpha(selectable ? 1.0f : 0.68f);
        text.setTextSize(selectable ? 16.0f : 13.5f);
        text.setSingleLine(selectable);
        text.setMaxLines(selectable ? 1 : 2);
        text.setPadding(dp(8), selectable ? dp(2) : 0, dp(8),
                selectable ? dp(2) : 0);
        text.setFocusable(selectable);
        text.setClickable(selectable);
        if (selectable)
        {
            TypedValue value = new TypedValue();
            if (activity.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, value, true)
                    && value.resourceId != 0)
                text.setBackgroundResource(value.resourceId);
        }
        text.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                selectable ? dp(28) : LinearLayout.LayoutParams.WRAP_CONTENT));
        return text;
    }

    private LinearLayout captionActions()
    {
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(6), 0, 0);
        actions.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return actions;
    }

    private Button captionButton(String label)
    {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(13.0f);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void chooseCaptionChoice(String title, final String[] labels,
            final ValueSetter setter)
    {
        chooseAudioChoice(title,labels,setter,this::reopenCaptionMenu);
    }

    private Dialog showCaptionPanel(String title, LinearLayout panel, String[] rows)
    {
        return showCompactPanel(title, panel, rows, false);
    }

    private Dialog showSettingsPanel(String title, LinearLayout panel, String[] rows)
    {
        return showCompactPanel(title, panel, rows, true);
    }

    private Dialog showCompactPanel(String title, LinearLayout panel, String[] rows,
            boolean leftHeading)
    {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xee1b1b1b);
        background.setCornerRadius(dp(4));
        background.setStroke(dp(1), 0xff707070);
        root.setBackground(background);

        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(21.0f);
        heading.setGravity(leftHeading
                ? Gravity.START | Gravity.CENTER_VERTICAL : Gravity.CENTER);
        if (leftHeading) heading.setPadding(dp(20), 0, dp(20), 0);
        heading.setIncludeFontPadding(false);
        heading.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        root.addView(heading);
        root.addView(panel);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.show();
        compactPanelDialog(dialog, rows, leftHeading);
        return dialog;
    }

    private void compactPanelDialog(Dialog dialog, String[] rows, boolean settingsPanel)
    {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.35f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setLayout(settingsPanel ? settingsDialogWidth(rows) : captionDialogWidth(rows),
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private int settingsDialogWidth(String[] rows)
    {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int desired = Math.max(captionDialogWidth(rows), dp(520));
        return Math.min(desired,
                Math.min((int) (metrics.widthPixels * 0.54f), dp(900)));
    }

    private int captionDialogWidth(String[] rows)
    {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int longest = 0;
        if (rows != null)
            for (String row : rows)
                if (row != null) longest = Math.max(longest, row.length());
        int desired = dp(Math.max(300, 64 + Math.min(longest, 64) * 9));
        return Math.min(desired,
                Math.min((int) (metrics.widthPixels * 0.42f), dp(700)));
    }

    private int dp(int value)
    {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void reopenCaptionMenu()
    {
        activity.getWindow().getDecorView().postDelayed(new Runnable()
        {
            @Override public void run() { chooseCaptions(media()); }
        }, 75L);
    }

    private void reapplyCaptionSlot(MediaCmd media, int channel)
    {
        if (media != null && ("cc" + channel).equals(media.getLegacyServerCaptionMode()))
            media.setLegacyServerCaptionMode("cc" + channel);
        else if (media != null && "dvb".equals(media.getLegacyServerCaptionMode()))
            media.setLegacyServerCaptionMode("dvb");
        else if (media != null && media.hasSageTvClosedCaptionState())
            // Keep STV as the authority for enabled/disabled state, but
            // immediately re-resolve the newly edited CEA/Teletext CC1/CC2
            // mapping in the current playback session.
            media.refreshCaptionSelection();
        AppUtil.message("CC" + channel + " mapping saved");
    }

    private void addAvailableCaptionRows(List<String> rows, MediaCmd media,
            MiniPlayerPlugin active)
    {
        SubtitleTrack[] tracks = verifiedCaptionTracks(active);
        String cc1 = captionSlotDetected(active, 1);
        String cc2 = captionSlotDetected(active, 2);
        int count = 0;
        for (SubtitleTrack track : tracks)
        {
            if (!CaptionSlotPolicy.isBroadcastCaption(track)) continue;
            String label = compactCaptionTrackLabel(track);
            StringBuilder row = new StringBuilder(label);
            if (track.getSubtitleCodec() == SubtitleCodec.DVB
                    && "dvb".equals(media == null ? "" : media.getLegacyServerCaptionMode()))
            {
                row.append(" (DVB selected)");
            }
            else
            {
                if (label.equals(cc1)) row.append(" (CC1)");
                if (label.equals(cc2)) row.append(" (CC2)");
            }
            rows.add(row.toString());
            count++;
        }
        if (count == 0)
            rows.add(active == null ? "None - start playback and Refresh"
                    : "None detected yet - select Refresh after playback starts");
    }

    /**
     * Exclude extractor-declared CEA compatibility tracks until valid CEA data
     * has actually been observed. This prevents DVB/Teletext-only UK streams
     * from being mislabeled as CEA-608/708 in the current-video inventory.
     */
    private SubtitleTrack[] verifiedCaptionTracks(MiniPlayerPlugin active)
    {
        if (active == null) return new SubtitleTrack[0];
        SubtitleTrack[] tracks = active.getSubtitleTracks();
        if (active.hasObservedCeaCaptionData()) return tracks;
        List<SubtitleTrack> verified = new ArrayList<SubtitleTrack>();
        for (SubtitleTrack track : tracks)
        {
            SubtitleCodec codec = track == null ? null : track.getSubtitleCodec();
            if (codec != SubtitleCodec.CEA608 && codec != SubtitleCodec.CEA708)
                verified.add(track);
        }
        return verified.toArray(new SubtitleTrack[verified.size()]);
    }

    private String captionSlotDetected(MiniPlayerPlugin active, int channel)
    {
        if (active == null) return "Not detected";
        SubtitleTrack track = CaptionSlotPolicy.findTrack(verifiedCaptionTracks(active), channel,
                captionType(channel), captionLanguage(channel), true, true);
        return track == null ? "No matching service" : compactCaptionTrackLabel(track);
    }

    private static String compactCaptionTrackLabel(SubtitleTrack track)
    {
        String language = CaptionSlotPolicy.reliableLanguage(track);
        String suffix = language.isEmpty() ? "" : " " + captionLanguageLabel(language);
        SubtitleCodec codec = track.getSubtitleCodec();
        if (codec == SubtitleCodec.TELETEXT)
            return track.getLabel().isEmpty() ? "Teletext" + suffix
                    : track.getLabel() + suffix;
        if (codec == SubtitleCodec.DVB) return "DVB" + suffix;
        if (codec == SubtitleCodec.CEA608)
            return "CEA-608" + (track.getAccessibilityChannel() > 0
                    ? " CC" + track.getAccessibilityChannel() : "");
        if (codec == SubtitleCodec.CEA708)
            return "CEA-708" + (track.getAccessibilityChannel() > 0
                    ? " Service " + track.getAccessibilityChannel() : "") + suffix;
        return captionTrackLabel(track);
    }

    private String captionType(int channel)
    {
        String value = CaptionSlotPolicy.normalizeType(client.properties().getString(
                captionTypeKey(channel), CaptionSlotPolicy.TYPE_AUTO));
        return value;
    }

    private String captionLanguage(int channel)
    {
        return client.properties().getString(captionLanguageKey(channel), "");
    }

    private static String captionTypeKey(int channel)
    {
        return channel == 1 ? PrefStore.Keys.caption_cc1_type : PrefStore.Keys.caption_cc2_type;
    }

    private static String captionLanguageKey(int channel)
    {
        return channel == 1 ? PrefStore.Keys.caption_cc1_language
                : PrefStore.Keys.caption_cc2_language;
    }

    private static String captionModeLabel(String mode)
    {
        if ("off".equals(mode)) return "OFF";
        if ("cc1".equals(mode)) return "CC1";
        if ("cc2".equals(mode)) return "CC2";
        if ("dvb".equals(mode)) return "DVB";
        return "STV";
    }

    private static String captionTypeLabel(String type)
    {
        String value = CaptionSlotPolicy.normalizeType(type);
        if (CaptionSlotPolicy.TYPE_TELETEXT.equals(value)) return "Teletext";
        if (CaptionSlotPolicy.TYPE_DVB.equals(value)) return "DVB";
        if (CaptionSlotPolicy.TYPE_CEA608.equals(value)) return "CEA-608";
        if (CaptionSlotPolicy.TYPE_CEA708.equals(value)) return "CEA-708";
        return "Auto";
    }

    private static String typeForCodec(SubtitleCodec codec)
    {
        if (codec == SubtitleCodec.TELETEXT) return CaptionSlotPolicy.TYPE_TELETEXT;
        if (codec == SubtitleCodec.DVB) return CaptionSlotPolicy.TYPE_DVB;
        if (codec == SubtitleCodec.CEA608) return CaptionSlotPolicy.TYPE_CEA608;
        if (codec == SubtitleCodec.CEA708) return CaptionSlotPolicy.TYPE_CEA708;
        return "";
    }

    private static String captionLanguageLabel(String language)
    {
        String normalized = TrackPreferencePolicy.normalizeLanguage(language);
        if (normalized.isEmpty()) return "Auto";
        Locale locale = Locale.forLanguageTag(normalized);
        String display = locale.getDisplayLanguage();
        if (display == null || display.isEmpty()) display = normalized;
        return display + " (" + normalized + ")";
    }

    private static boolean isEnglishLanguage(String language)
    {
        String normalized = TrackPreferencePolicy.normalizeLanguage(language);
        return "en".equals(normalized) || "eng".equals(normalized)
                || normalized.startsWith("en-") || normalized.startsWith("eng-");
    }

    private static String captionStatus(MediaCmd media, MiniPlayerPlugin active)
    {
        if (media == null) return "no active playback";
        String mode = media.getLegacyServerCaptionMode();
        if ("cc1".equals(mode)) return "SageTV CC1";
        if ("cc2".equals(mode)) return "SageTV CC2";
        if ("dvb".equals(mode)) return "DVB bitmap broadcast CC";
        if ("off".equals(mode))
        {
            int selected = active == null ? MiniPlayerPlugin.DISABLE_TRACK
                    : active.getSelectedSubtitleTrack();
            if (selected != MiniPlayerPlugin.DISABLE_TRACK)
                return "device track " + selected;
            return "Off";
        }
        return media.hasSageTvClosedCaptionState()
                ? "following SageTV STV" : "following SageTV when available";
    }

    private static String captionTrackLabel(SubtitleTrack track)
    {
        String reliableLanguage = CaptionSlotPolicy.reliableLanguage(track);
        String language = reliableLanguage.isEmpty() ? "Unknown language"
                : captionLanguageLabel(reliableLanguage);
        SubtitleCodec codec = track.getSubtitleCodec();
        if (codec == SubtitleCodec.TELETEXT)
            return "Teletext - " + language + " - " + track.getLabel();
        if (codec == SubtitleCodec.DVB)
            return "DVB bitmap - " + language;
        if (codec == SubtitleCodec.PGS)
            return "PGS bitmap - " + language;
        if (codec == SubtitleCodec.SUBRIP)
            return "Text subtitle - " + language;
        if (codec == SubtitleCodec.CEA608)
            return "CEA-608 - " + language
                    + (track.getAccessibilityChannel() > 0
                            ? " - CC" + track.getAccessibilityChannel() : "");
        if (codec == SubtitleCodec.CEA708)
            return "CEA-708 - " + language
                    + (track.getAccessibilityChannel() > 0
                            ? " - Service " + track.getAccessibilityChannel() : "");
        return track.toString();
    }

    private void chooseScope(final String title, final String preferenceKey,
            final String value, final ValueSetter sessionSetter,
            final boolean reloadRequired)
    {
        chooseScope(title,preferenceKey,value,sessionSetter,reloadRequired,this::reopenVideoMenu);
    }
    private void chooseScope(final String title,final String preferenceKey,
            final String value,final ValueSetter sessionSetter,final boolean reloadRequired,final Runnable parent)
    {
        final PlaybackSettingCatalog.Definition definition=PlaybackSettingCatalog.find(preferenceKey);
        String display=definition==null?value:definition.display(activity,value);
        String timing=definition==null?(reloadRequired?"Restarts playback when supported":"Immediate"):definition.timing.label;
        chooseAudioChoice((definition==null?title:definition.title)+": "+display+" • "+timing,
            new String[]{"This playback session","Save as this device's default"},index->{
                if(Integer.parseInt(index)==1){
                    if(definition==null)client.properties().setString(preferenceKey,value);
                    else if(!PlaybackSettingPreferences.write(PreferenceManager.getDefaultSharedPreferences(activity),definition,value,activity)){parent.run();return;}
                }
                sessionSetter.set(value);
                if(reloadRequired)reload(media());
                parent.run();
            },parent);
    }

    private void reload(MediaCmd media)
    {
        if (media != null && media.requestControlledPlayerReload())
            AppUtil.message("Refreshing local DVD video without a server seek");
        else
            AppUtil.message("Saved. This transport cannot reload safely in place; it applies to the next video");
    }

    private void unsupportedOffset(String kind)
    {
        AppUtil.message(kind + " offset is unavailable on this backend; no false offset was applied");
    }

    private void chooseOffset(final MediaCmd media, final boolean subtitle)
    {
        final MiniPlayerPlugin player = player(media);
        boolean supported = player != null && (subtitle
                ? player.supportsSubtitleOffset() : player.supportsAudioOffset());
        final String label = subtitle ? "Caption/subtitle" : "Audio";
        final Runnable parent=subtitle?this::reopenCaptionMenu:this::showAudioMenu;
        if (!supported)
        {
            unsupportedOffset(label);
            return;
        }
        final int[] values = { -2000, -1000, -500, -250, 0, 250, 500, 1000, 2000 };
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++)
            labels[i] = signed(values[i]) + " ms";
        chooseAudioChoice(label + " sync offset", labels, new ValueSetter()
        {
            @Override public void set(String index)
            {
                final int value = values[Integer.parseInt(index)];
                chooseScope(label + " sync offset",
                        subtitle ? PrefStore.Keys.playback_subtitle_offset_ms
                                : PrefStore.Keys.playback_audio_offset_ms,
                        Integer.toString(value), new ValueSetter()
                        {
                            @Override public void set(String ignored)
                            {
                                boolean applied;
                                if (subtitle)
                                {
                                    ActivePlayerSessionOverrides.setSubtitleOffsetMs(value);
                                    applied = player.setSubtitleOffsetMillis(value);
                                }
                                else
                                {
                                    ActivePlayerSessionOverrides.setAudioOffsetMs(value);
                                    applied = player.setAudioOffsetMillis(value);
                                }
                                AppUtil.message(applied
                                        ? label + " offset applied: " + signed(value) + " ms"
                                        : label + " offset is unsupported by this active output");
                            }
                        }, false,parent);
            }
        },parent);
    }

    private static String signed(int value)
    {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private void testCurrentVideo(final MediaCmd media)
    {
        CurrentVideoDiagnosticTest.confirmAndRun(activity, client, media,
                new CurrentVideoDiagnosticTest.Completion()
                {
                    @Override public void complete(final String summary, final String report)
                    {
                        new AlertDialog.Builder(activity)
                                .setTitle("Current Video Test")
                                .setMessage(summary)
                                .setPositiveButton("Export diagnostics",
                                        new DialogInterface.OnClickListener()
                                        {
                                            @Override public void onClick(DialogInterface dialog,
                                                    int which)
                                            {
                                                exportDiagnostics(diagnosticsText(media())
                                                        + "\n\n" + summary);
                                            }
                                        })
                                .setNeutralButton("View report",
                                        new DialogInterface.OnClickListener()
                                        {
                                            @Override public void onClick(DialogInterface dialog,
                                                    int which)
                                            { showCurrentVideoReport(report); }
                                        })
                                .setNegativeButton(android.R.string.ok, null)
                                .show();
                    }
                });
    }

    private void showCurrentVideoReport(final String report)
    {
        new AlertDialog.Builder(activity)
                .setTitle("Current Video Test Report")
                .setMessage(report)
                .setPositiveButton("Export diagnostics",
                        new DialogInterface.OnClickListener()
                        {
                            @Override public void onClick(DialogInterface dialog, int which)
                            { exportDiagnostics(diagnosticsText(media())); }
                        })
                .setNegativeButton(android.R.string.ok, null)
                .show();
    }

    private String diagnosticsText(MediaCmd media)
    {
        MiniPlayerPlugin player = media == null ? null : media.getPlaya();
        DisplayRefreshController.Result display =
                DisplayRefreshController.inspect(activity, player);
        boolean dvd = media != null && media.isDvdSessionPending();
        StringBuilder text = new StringBuilder();
        text.append("Player: ").append(player == null
                ? "none" : player.getClass().getSimpleName());
        text.append("\nSource: ").append(dvd
                ? "SageTV native DVD Push" : client.properties().getStreamingMode());
        text.append("\nDecoder: ").append(ActivePlayerSessionOverrides.resolveDecodingMethod(
                client.properties().getString(PrefStore.Keys.decoding_method,
                        DecodingMethod.DEFAULT_PREFERENCE)));
        text.append("\n").append(MiniclientApplication.get().getMimDirectSession().serverOutputForDiagnostics());
        if (dvd)
        {
            text.append("\nDVD buffer: ").append(media.getDvdDecoderBufferedAheadMs())
                    .append(" ms");
            text.append("\nDVD Push: ").append(media.getDvdPushedBytes()).append(" bytes");
        }
        text.append("\nCaptions: ").append(captionStatus(media, player));
        text.append("\nSubtitles: ").append(subtitleTrackValue(player, media));
        text.append("\nAudio: ").append(player == null
                ? "none" : player.getAudioOutputSummary());
        if (player != null)
            text.append(" / ").append(audioOffsetValue(player.getAudioOffsetMillis()));
        text.append("\nAudio stream: ").append(audioTrackValue(player));
        text.append("\nDisplay: ").append(display.summary());
        int settle = ActivePlayerSessionOverrides.resolveRefreshSettleMs(
                client.properties().getInt(PrefStore.Keys.playback_refresh_settle_ms, 0));
        if (dvd || settle != 0 || DisplayRefreshController.getPendingRefreshMs() != 0)
            text.append("\nDVD HDMI settle: ").append(settle).append(" ms / pending ")
                    .append(DisplayRefreshController.getPendingRefreshMs()).append(" ms");
        return text.toString();
    }

    public static String diagnosticsTextForExport(Activity activity, MediaCmd media)
    {
        return new ActivePlayerAdjustmentsDialog(activity).diagnosticsText(media);
    }

    private void exportDiagnostics(String text)
    {
        DiagnosticExportController.show(activity, text);
    }

    private String resolvedBackend()
    {
        return ActivePlayerSessionOverrides.resolveBackend(client.properties().getString(
                PrefStore.Keys.default_player, PlayerBackend.DEFAULT_PREFERENCE));
    }

    private void chooseValues(String title, final String[] values, final ValueSetter setter)
    {
        chooseVideoChoice(title, values, new ValueSetter()
        {
            @Override public void set(String index)
            { setter.set(values[Integer.parseInt(index)]); }
        });
    }

    private void choose(String title, String[] labels, final ValueSetter setter)
    {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(labels, new DialogInterface.OnClickListener()
                {
                    @Override public void onClick(DialogInterface dialog, int which)
                    { setter.set(Integer.toString(which)); }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
