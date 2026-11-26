package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import opensagetv.vibe.miniclient.MediaCmd;
import opensagetv.vibe.miniclient.android.video.MimDirectSessionClient;

/** Explicit source-image browser. It is deliberately NOT a local seek: stock
 * SageTV must remain owner of playback/file time and its normal Time Scroll.
 * No timer, background sampling, extra playback producer or saved preference.
 */
final class MimSourcePreviewDialog {
    interface Panel { Dialog show(String title,LinearLayout content,String[] sizing); }
    private final Activity activity;
    private final MediaCmd media;
    private final MimDirectSessionClient service;
    private final MimDirectSessionClient.PreviewHandle handle;
    private final LinearLayout panel;
    private final ImageView image;
    private final TextView status;
    private final List<Button> buttons=new ArrayList<>();
    private Dialog dialog;private Bitmap bitmap;private boolean closed,busy;private long position;

    static void show(Activity activity,MediaCmd media,Panel factory){
        MimDirectSessionClient service=MiniclientApplication.get().getMimDirectSession();
        MimDirectSessionClient.PreviewHandle handle=service.previewHandle();
        if(media==null||handle==null){AppUtil.message("Source previews unavailable; playback unchanged");return;}
        new MimSourcePreviewDialog(activity,media,service,handle).open(factory);
    }
    private MimSourcePreviewDialog(Activity activity,MediaCmd media,MimDirectSessionClient service,MimDirectSessionClient.PreviewHandle handle){
        this.activity=activity;this.media=media;this.service=service;this.handle=handle;
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),0,dp(16),dp(8));
        TextView note=text("Preview only; playback position is unchanged. Use SageTV Time Scroll to seek.");panel.addView(note);
        image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription("Source preview");panel.addView(image,new LinearLayout.LayoutParams(-1,dp(144)));
        status=text("Choose a source position");panel.addView(status);
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER);
        button(row,"−60 s",()->request(position-60000));button(row,"−10 s",()->request(position-10000));
        Button current=button(row,"Current",()->request(media.getMediaTimeMillis()));
        button(row,"+10 s",()->request(position+10000));button(row,"+60 s",()->request(position+60000));panel.addView(row);
        LinearLayout actions=new LinearLayout(activity);actions.setGravity(Gravity.END);
        Button close=new Button(activity);close.setText("Close");close.setOnClickListener(v->dialog.dismiss());actions.addView(close);panel.addView(actions);
        current.requestFocus();
    }
    private void open(Panel factory){
        dialog=factory.show("Source previews",panel,new String[]{"−60 s  −10 s  Current  +10 s  +60 s"});
        dialog.setOnDismissListener(ignored->{closed=true;service.cancelPreview(handle);image.setImageDrawable(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}});
        request(media.getMediaTimeMillis());
    }
    private void request(long requested){
        if(closed||busy)return;
        busy=true;for(Button button:buttons)button.setEnabled(false);
        status.setText("Loading source preview…");
        boolean accepted=service.requestPreview(handle,Math.max(0,Math.min(14L*86400000L,requested)),result->activity.runOnUiThread(()->{
            if(closed||activity.isFinishing()||activity.isDestroyed())return;
            busy=false;for(Button button:buttons)button.setEnabled(true);
            if(!service.previewCurrent(handle,result)){
                clearImage();status.setText("Source changed. Reopen previews for the current recording.");for(Button button:buttons)button.setEnabled(false);return;
            }
            if(!result.available()){status.setText("Preview unavailable or cooling down; playback unchanged");return;}
            // Validate the advertised dimensions BEFORE allocating pixels. An
            // optional server JPEG must not be able to trigger an unbounded
            // bitmap allocation on an older Android device.
            byte[] jpeg=result.image.jpeg;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(jpeg,0,jpeg.length,bounds);
            if(bounds.outWidth!=192||bounds.outHeight!=108){status.setText("Preview image unavailable; playback unchanged");return;}
            Bitmap decoded=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
            if(decoded==null){status.setText("Preview image unavailable; playback unchanged");return;}
            clearImage();bitmap=decoded;image.setImageBitmap(bitmap);position=result.image.positionMs;
            status.setText(String.format(Locale.US,"Source %d:%02d  ·  10-second preview buckets",position/60000,(position/1000)%60));
        }));
        if(!accepted){busy=false;for(Button button:buttons)button.setEnabled(true);status.setText("Preview unavailable; current playback retained");}
    }
    private void clearImage(){image.setImageDrawable(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
    private TextView text(String value){TextView view=new TextView(activity);view.setText(value);view.setTextColor(Color.WHITE);view.setTextSize(13);view.setPadding(0,dp(4),0,dp(4));return view;}
    private Button button(LinearLayout row,String label,Runnable action){
        Button button=new Button(activity);button.setText(label);button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);
        button.setPadding(dp(2),0,dp(2),0);button.setOnClickListener(v->action.run());row.addView(button,new LinearLayout.LayoutParams(0,dp(36),1));buttons.add(button);return button;
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
