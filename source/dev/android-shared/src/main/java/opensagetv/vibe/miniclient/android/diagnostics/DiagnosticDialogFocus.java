package opensagetv.vibe.miniclient.android.diagnostics;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;

/** Explicit TV focus feedback for both custom and SMB confirmation footers.
 * Fire OS theme tint can otherwise make focused/unfocused buttons identical.
 * This changes rendering only, not Select/Back actions or upload consent.
 */
final class DiagnosticDialogFocus {
    private DiagnosticDialogFocus() { }
    static void style(Context context,Button button) {
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},background(context,false,false));
        states.addState(new int[]{android.R.attr.state_pressed},background(context,true,true));
        states.addState(new int[]{android.R.attr.state_focused},background(context,true,true));
        states.addState(new int[]{},background(context,false,true));
        button.setBackgroundTintList(null);button.setBackground(states);
        button.setTextColor(Color.WHITE);button.setFocusable(true);
        button.setFocusableInTouchMode(true);button.setStateListAnimator(null);
    }
    private static GradientDrawable background(Context context,boolean highlighted,boolean enabled) {
        float density=context.getResources().getDisplayMetrics().density;
        GradientDrawable background=new GradientDrawable();background.setCornerRadius(3*density);
        background.setColor(!enabled?0xff303030:highlighted?0xff34556e:0xff494949);
        background.setStroke(Math.round(density*(highlighted?2:1)),highlighted?0xffb4d9ff:0xff707070);
        return background;
    }
}
