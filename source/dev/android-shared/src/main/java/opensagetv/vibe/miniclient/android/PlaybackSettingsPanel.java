package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;

/** Presentation-only playback panels, shared by gear and direct icon routes.
 * No player callbacks, polling, server calls or configuration writes (except
 * explicitly namespaced navigation expansion/focus) live in this renderer.
 */
final class PlaybackSettingsPanel {
    static final class Entry {
        final String id,title,value;final boolean enabled;final Runnable action;
        Entry(String id,String title,String value,boolean enabled,Runnable action){
            this.id=id;this.title=title;this.value=value;this.enabled=enabled;this.action=action;
        }
    }
    static final class Section {
        final String id,title;final boolean initiallyExpanded;final List<Entry> entries=new ArrayList<>();
        Section(String id,String title,boolean expanded){this.id=id;this.title=title;initiallyExpanded=expanded;}
        Section add(String id,String title,String value,boolean enabled,Runnable action){
            entries.add(new Entry(id,title,value,enabled,action));return this;
        }
    }
    static Section section(String id,String title,boolean expanded){return new Section(id,title,expanded);}
    static Dialog show(Activity activity,String panelId,String title,String summary,List<Section> sections,Runnable parent){
        boolean choice=panelId.startsWith("choice/");
        SharedPreferences navigation=PreferenceManager.getDefaultSharedPreferences(activity);
        String prefix="settings/runtime/"+panelId+"/";
        Dialog dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=column(activity);root.setPadding(dp(activity,16),dp(activity,10),dp(activity,16),dp(activity,10));
        GradientDrawable background=new GradientDrawable();background.setColor(0xf21b1b1b);
        background.setCornerRadius(dp(activity,5));background.setStroke(dp(activity,1),0xff707070);root.setBackground(background);
        TextView heading=text(activity,title,21,Color.WHITE);heading.setPadding(dp(activity,4),0,0,dp(activity,6));root.addView(heading);
        if(summary!=null&&!summary.isEmpty()){TextView note=text(activity,summary,13,Color.LTGRAY);note.setPadding(dp(activity,4),0,0,dp(activity,8));root.addView(note);}
        BoundedScroll scroll=new BoundedScroll(activity);scroll.setFillViewport(false);
        LinearLayout body=column(activity);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        for(Section section:sections){
            TextView toggle=text(activity,"",17,0xff81d4fa);toggle.setTag("section/"+section.id);
            toggle.setPadding(dp(activity,8),dp(activity,10),dp(activity,8),dp(activity,10));
            styleFocusable(activity,toggle);body.addView(toggle,new LinearLayout.LayoutParams(-1,-2));
            if(choice)toggle.setVisibility(View.GONE);
            LinearLayout children=column(activity);body.addView(children);
            Runnable sync=()->{
                boolean open=choice||navigation.getBoolean(prefix+section.id+"/expanded",section.initiallyExpanded);
                toggle.setText((open?"▼ ":"▶ ")+section.title);
                toggle.setContentDescription(section.title+(open?", expanded":", collapsed"));
                children.setVisibility(open?View.VISIBLE:View.GONE);
            };
            toggle.setOnClickListener(view->{
                boolean open=navigation.getBoolean(prefix+section.id+"/expanded",section.initiallyExpanded);
                navigation.edit().putBoolean(prefix+section.id+"/expanded",!open).putString(prefix+"focus","section/"+section.id).apply();
                sync.run();view.requestFocus();
            });
            toggle.setOnKeyListener((view,key,event)->{
                if(key!=KeyEvent.KEYCODE_DPAD_CENTER&&key!=KeyEvent.KEYCODE_ENTER&&key!=KeyEvent.KEYCODE_NUMPAD_ENTER)return false;
                if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled())view.performClick();return true;
            });
            for(Entry entry:section.entries){
                LinearLayout row=column(activity);row.setTag(entry.id);row.setPadding(dp(activity,14),dp(activity,7),dp(activity,8),dp(activity,7));
                TextView label=text(activity,entry.title,16,entry.enabled?Color.WHITE:Color.GRAY);row.addView(label);
                if(entry.value!=null&&!entry.value.isEmpty())row.addView(text(activity,entry.value,13,entry.enabled?Color.LTGRAY:Color.GRAY));
                row.setMinimumHeight(dp(activity,44));
                if(entry.enabled&&entry.action!=null){
                    styleFocusable(activity,row);
                    row.setOnClickListener(view->{if(!choice)navigation.edit().putString(prefix+"focus",entry.id).apply();dialog.dismiss();entry.action.run();});
                }else{row.setEnabled(false);row.setFocusable(false);}
                children.addView(row,new LinearLayout.LayoutParams(-1,-2));
            }
            sync.run();
        }
        LinearLayout footer=new LinearLayout(activity);footer.setGravity(Gravity.END);footer.setPadding(0,dp(activity,8),0,0);
        Button back=new Button(activity);back.setText(parent==null?"Close":"Back");styleFocusable(activity,back);
        back.setOnClickListener(view->{dialog.dismiss();if(parent!=null)parent.run();});footer.addView(back);root.addView(footer);
        dialog.setContentView(root);dialog.setOnCancelListener(unused->{if(parent!=null)parent.run();});dialog.show();
        Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setDimAmount(.28f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,32),dp(activity,620)),-2);}
        root.post(()->{
            String saved=choice?"":navigation.getString(prefix+"focus","");View focused=body.findViewWithTag(saved);
            if(focused==null||!focused.isShown()||!focused.isFocusable())focused=firstFocusable(body);
            if(focused==null)focused=back;focused.requestFocus();
        });
        AppUtil.hideSystemUI(activity);return dialog;
    }
    private static View firstFocusable(ViewGroup group){
        for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child.getVisibility()!=View.VISIBLE)continue;
            if(child.isFocusable()&&child.isEnabled())return child;
            if(child instanceof ViewGroup){View found=firstFocusable((ViewGroup)child);if(found!=null)return found;}}
        return null;
    }
    static void styleFocusable(Activity activity,View view){
        view.setFocusable(true);view.setFocusableInTouchMode(true);view.setClickable(true);
        // Focus-on-up consumes the first tap on focusable-in-touch-mode Views.
        // Acquire focus on DOWN so the normal UP can click once, while retaining
        // visible TV/keyboard focus and normal pressed/cancel handling.
        view.setOnTouchListener((target,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)target.requestFocus();
            return false;
        });
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},background(activity,true));
        states.addState(new int[]{android.R.attr.state_focused},background(activity,true));
        states.addState(new int[]{},background(activity,false));view.setBackground(states);
        view.setBackgroundTintList(null);view.setStateListAnimator(null);
    }
    private static GradientDrawable background(Activity activity,boolean focus){
        GradientDrawable d=new GradientDrawable();d.setColor(focus?0xff34556e:Color.TRANSPARENT);
        d.setCornerRadius(dp(activity,3));if(focus)d.setStroke(dp(activity,2),0xffb4d9ff);return d;
    }
    private static LinearLayout column(Activity activity){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private static TextView text(Activity activity,String value,int size,int color){
        TextView text=new TextView(activity);text.setText(value);text.setTextColor(color);text.setTextSize(size);
        text.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);text.setMaxLines(3);return text;
    }
    private static int dp(Activity activity,int value){return Math.round(activity.getResources().getDisplayMetrics().density*value);}
    private static final class BoundedScroll extends ScrollView {
        BoundedScroll(Activity activity){super(activity);}
        @Override protected void onMeasure(int width,int height){
            int max=Math.max(180,(int)(getResources().getDisplayMetrics().heightPixels*.65f));
            super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.AT_MOST));
        }
    }
    private PlaybackSettingsPanel(){}
}
