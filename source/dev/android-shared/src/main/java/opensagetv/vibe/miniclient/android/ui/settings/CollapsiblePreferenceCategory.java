package opensagetv.vibe.miniclient.android.ui.settings;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

/** Inline, independently persisted sections; never remove children or their values. */
public final class CollapsiblePreferenceCategory extends PreferenceCategory {
    private boolean expanded = true;
    private CharSequence sectionTitle;

    public CollapsiblePreferenceCategory(Context context, AttributeSet attrs) {
        super(context, attrs);
        sectionTitle = getTitle();
        setSelectable(true);
        setLayoutResource(opensagetv.vibe.miniclient.android.R.layout.preference_section_header);
    }
    @Override public boolean isEnabled() { return true; }
    @Override public boolean shouldDisableDependents() { return false; }
    public void restoreExpansion() {
        expanded = getSharedPreferences().getBoolean(getKey() + "/expanded", true);
        applyExpansion();
    }
    private void applyExpansion() {
        for (int i = 0; i < getPreferenceCount(); i++) getPreference(i).setVisible(expanded);
        setTitle((expanded ? "▼ " : "▶ ") + sectionTitle);
        notifyChanged();
    }
    @Override public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) title.setTextColor(Color.rgb(129, 212, 250));
        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);
        holder.itemView.setContentDescription(sectionTitle + (expanded ? ", expanded" : ", collapsed")
                + (getSummary() == null ? "" : ", " + getSummary()));
        holder.itemView.setOnClickListener(view -> {
            expanded = !expanded;
            getSharedPreferences().edit().putBoolean(getKey() + "/expanded", expanded).apply();
            applyExpansion();
            view.requestFocus();
        });
        // PreferenceCategory's normal key dispatch is for non-selectable
        // headings. Consume the complete remote key pair and activate once.
        holder.itemView.setOnKeyListener((view,key,event)->{
            if(key!=android.view.KeyEvent.KEYCODE_DPAD_CENTER && key!=android.view.KeyEvent.KEYCODE_ENTER
                    && key!=android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)return false;
            if(event.getAction()==android.view.KeyEvent.ACTION_UP&&!event.isCanceled())view.performClick();
            return true;
        });
    }
}
