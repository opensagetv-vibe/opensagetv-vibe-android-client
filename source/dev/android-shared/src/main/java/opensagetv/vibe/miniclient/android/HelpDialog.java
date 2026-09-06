package opensagetv.vibe.miniclient.android;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

import opensagetv.vibe.miniclient.MiniClientConnection;
import opensagetv.vibe.miniclient.util.IOUtil;

/**
 * Created by seans on 31/01/16.
 */
public class HelpDialog extends Dialog {
    TextView helpText;

    public HelpDialog(Activity activity) {
        super(activity, R.style.Theme_Dialog_DoNotDim);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View v = LayoutInflater.from(getContext()).inflate(R.layout.dialog_help, null, false);
        setContentView(v);
        helpText = (TextView) v.findViewById(R.id.help_text);
        try {
            updateText();
        } catch (IOException e) {
            e.printStackTrace();
            helpText.setText("Can't find Help File");
        }
    }

    private void updateText() throws IOException {
        String input = "TOUCH_NAVIGATION.md";
        if (getContext().getResources().getBoolean(R.bool.istv)) {
            input = "REMOTE_NAVIGATION.md";
        }
        InputStream is = MiniClientConnection.class.getClassLoader().getResourceAsStream(input);
        CharSequence text = prettyText(IOUtil.toString(is));
        is.close();

        helpText.setText(text);
    }

    private CharSequence prettyText(String text) {
        text = text.replaceAll("#\\s*(.*)\n", "<h2><font color=\"#009688\">$1</font></h2>");
        text = text.replaceAll("\\*", "\u2022 ");
        text = text.replaceAll("```([^`]+)```", "<font color=\"#B2DFDB\">$1</font>");
        text = text.replaceAll("\n", "<br>");
        return Html.fromHtml(text);
    }

    public static HelpDialog showDialog(Activity context) {
        HelpDialog dialog = new HelpDialog(context);
        dialog.show();
        return dialog;
    }
}
