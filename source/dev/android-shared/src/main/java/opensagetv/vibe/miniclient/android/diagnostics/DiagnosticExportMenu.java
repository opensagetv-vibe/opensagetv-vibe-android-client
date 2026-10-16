package opensagetv.vibe.miniclient.android.diagnostics;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.preference.PreferenceManager;
import com.google.zxing.common.BitMatrix;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.android.prefs.AndroidPrefStore;

/** Shared compact export chooser. Merely opening it never changes preferences,
 * starts a web server or uploads. Disabled choices remain visible with reasons.
 * Server discovery is bounded, one-shot/off-main and tied to the exact session.
 */
final class DiagnosticExportMenu {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor DISCOVERY=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,"diagnostic-export-discovery");t.setDaemon(true);return t;});
    private final Activity activity;
    private final String details;
    private final SharedPreferences prefs;
    private Dialog parent;
    private TextView serverRow,webRow;
    private MiniClientConnection confirmedSession;
    private boolean checking;
    private DiagnosticExportMenu(Activity activity,String details) {
        this.activity=activity;this.details=details;
        prefs=PreferenceManager.getDefaultSharedPreferences(activity);
    }
    static void show(Activity activity,String details) { new DiagnosticExportMenu(activity,details).open(); }
    private boolean alive(){return !activity.isFinishing() && !activity.isDestroyed();}
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout panel(){LinearLayout p=new LinearLayout(activity);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(14),dp(6),dp(14),dp(6));return p;}
    private TextView text(String value,int size) {
        TextView t=new TextView(activity);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);
        t.setGravity(Gravity.START);t.setPadding(dp(8),dp(5),dp(8),dp(5));t.setIncludeFontPadding(false);return t;
    }
    private TextView choice(String title,String reason,boolean enabled,Runnable action) {
        TextView row=text(title+"\n"+reason,17);
        row.setPadding(dp(10),dp(9),dp(10),dp(9));row.setMinimumHeight(dp(52));
        row.setOnClickListener(v->{if(row.isEnabled())action.run();});
        row.setOnFocusChangeListener((v,focus)->row.setBackgroundColor(focus?0xff34556e:Color.TRANSPARENT));
        setChoice(row,title,reason,enabled);return row;
    }
    private void setChoice(TextView row,String title,String reason,boolean enabled) {
        row.setText(title+"\n"+reason);row.setEnabled(enabled);row.setFocusable(enabled);
        row.setFocusableInTouchMode(enabled);row.setTextColor(enabled?Color.WHITE:0xffaaaaaa);
        row.setContentDescription(title+(enabled?"":" (unavailable): "+reason));
    }
    private Button button(String title,Runnable action) {
        Button b=new Button(activity);b.setText(title);b.setTextSize(14);b.setAllCaps(false);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),0,dp(10),0);
        DiagnosticDialogFocus.style(activity,b);
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(34)));
        b.setOnClickListener(v->action.run());return b;
    }
    private Dialog dialog(String title,LinearLayout content,int height,String[] labels,Runnable[] actions) {
        LinearLayout root=panel();GradientDrawable background=new GradientDrawable();background.setColor(0xee1b1b1b);
        background.setCornerRadius(dp(4));background.setStroke(dp(1),0xff707070);root.setBackground(background);
        root.addView(text(title,21));ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout footer=new LinearLayout(activity);footer.setGravity(Gravity.END);
        Dialog d=new Dialog(activity);d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setContentView(root);
        Button first=null,back=null;boolean confirmsUpload=false;
        for(int i=0;i<labels.length;i++){
            final Runnable action=actions[i];Button item=button(labels[i],()->{if(action==null)d.dismiss();else action.run();});
            footer.addView(item);if(first==null)first=item;if("Back".equals(labels[i]))back=item;
            if("Send once".equals(labels[i]))confirmsUpload=true;
        }
        root.addView(footer);d.show();Window w=d.getWindow();
        if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setDimAmount(.35f);
            android.util.DisplayMetrics m=activity.getResources().getDisplayMetrics();w.setLayout(Math.min(dp(480),(int)(m.widthPixels*.85f)),Math.min(dp(height),(int)(m.heightPixels*.84f)));}
        // Show a focus target immediately, without requiring a preliminary
        // arrow press. Confirmation starts on Back, never on the sending action.
        Button initial=confirmsUpload && back!=null?back:first;
        if(initial!=null)initial.requestFocus();
        return d;
    }
    private void open() {
        LinearLayout content=panel();
        content.addView(choice("Create and share ZIP","Choose an Android sharing app",true,()->DiagnosticExportController.share(activity,details)));
        String mode=prefs.getString(AndroidPrefStore.SMB_DIAGNOSTICS_MODE,AndroidPrefStore.DIAGNOSTICS_MODE_OFF);
        boolean smb=!AndroidPrefStore.DIAGNOSTICS_MODE_OFF.equals(mode);
        content.addView(choice("Export to SMB",smb?"Use the configured diagnostic destination":"SMB export is Off",smb,()->DiagnosticExportController.uploadSmb(activity,details)));
        serverRow=choice("Export to SageTV server","Checking receiver...",false,this::confirmServer);
        webRow=choice("Download in web browser","Web server is Off",false,this::web);
        content.addView(serverRow);content.addView(webRow);
        parent=dialog("Export Diagnostics",content,370,new String[]{"Refresh","Back"},new Runnable[]{this::refresh,null});
        refresh();
    }
    private void refresh() {
        if(!alive() || !parent.isShowing())return;
        String url=ClientDiagnosticWebService.exportAddress();
        boolean web=DiagnosticExportPolicy.webAvailable(prefs.getBoolean(AndroidPrefStore.CLIENT_WEB_ENABLED,false),url);
        setChoice(webRow,"Download in web browser",web?"Show this device's address and QR code":"Client web server is Off or unavailable",web);
        if(checking)return;
        MiniClientConnection session=ServerDiagnosticExporter.exportSession();confirmedSession=null;
        if(session==null){setChoice(serverRow,"Export to SageTV server","Connect to a SageTV server first",false);return;}
        checking=true;setChoice(serverRow,"Export to SageTV server","Checking receiver...",false);
        try { DISCOVERY.execute(()->{
            boolean available=false;
            try{available=ServerDiagnosticExporter.receiverAvailable(session);}catch(Exception unavailable){ }
            final boolean found=available;
            MAIN.post(()->{
                checking=false;if(!alive() || !parent.isShowing())return;
                boolean valid=DiagnosticExportPolicy.serverAvailable(ServerDiagnosticExporter.exportSession()==session,found);
                confirmedSession=valid?session:null;
                setChoice(serverRow,"Export to SageTV server",valid?"Send one report; automatic setting unchanged":"Receiver missing, disabled or unreachable",valid);
            });
        });}catch(RuntimeException busy){checking=false;setChoice(serverRow,"Export to SageTV server","Discovery busy; use Refresh",false);}
    }
    private void confirmServer() {
        final MiniClientConnection target=confirmedSession;
        if(target==null || target!=ServerDiagnosticExporter.exportSession()){refresh();return;}
        LinearLayout p=panel();p.addView(text("Send one redacted diagnostic ZIP to the connected server's Client Extension?\n\nAutomatic uploads and your saved settings are unchanged.",17));
        final Dialog[] child=new Dialog[1];
        child[0]=dialog("Export to SageTV server",p,260,new String[]{"Send once","Back"},new Runnable[]{()->{
            child[0].dismiss();
            ServerDiagnosticExporter.uploadOnce(target,details,status->{if(!alive() || !parent.isShowing())return;
                LinearLayout result=panel();TextView t=text(status,16);t.setTextIsSelectable(true);result.addView(t);
                dialog("Server export result",result,260,new String[]{"Back"},new Runnable[]{null});refresh();});
            Toast.makeText(activity,"Sending one redacted report...",Toast.LENGTH_SHORT).show();
        },null});
    }
    private void web() {
        String url=ClientDiagnosticWebService.exportAddress();
        if(!DiagnosticExportPolicy.webAvailable(prefs.getBoolean(AndroidPrefStore.CLIENT_WEB_ENABLED,false),url)){refresh();return;}
        try {
            LinearLayout p=panel();String ip=new URI(url).getHost();TextView address=text("Device IP: "+ip+"\n"+url,18);address.setTextIsSelectable(true);p.addView(address);
            BitMatrix matrix=DiagnosticUrlQr.encode(url);
            int limit=Math.min(dp(260),(int)(activity.getResources().getDisplayMetrics().heightPixels*.4f));
            int scale=Math.max(1,limit/matrix.getWidth()),size=matrix.getWidth()*scale;
            int[] pixels=new int[size*size];for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=matrix.get(x/scale,y/scale)?Color.BLACK:Color.WHITE;
            Bitmap qr=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
            ImageView image=new ImageView(activity);image.setImageBitmap(qr);image.setContentDescription("Diagnostic web QR: "+url);
            LinearLayout.LayoutParams placement=new LinearLayout.LayoutParams(size,size);placement.gravity=Gravity.CENTER_HORIZONTAL;p.addView(image,placement);
            p.addView(text("Scan with your phone on the same trusted LAN. Keep Vibe open; download the ZIP from its webpage.",15));
            dialog("Diagnostic web download",p,510,new String[]{"Copy URL","Back"},new Runnable[]{()->{
                ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Vibe diagnostic web address",url));
                Toast.makeText(activity,"Diagnostic web URL copied",Toast.LENGTH_SHORT).show();
            },null});
        }catch(Exception unavailable){Toast.makeText(activity,"Web QR unavailable; use the diagnostic web address in Settings",Toast.LENGTH_LONG).show();}
    }
}
