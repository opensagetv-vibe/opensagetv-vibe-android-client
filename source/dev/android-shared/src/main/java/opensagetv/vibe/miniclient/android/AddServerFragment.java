package opensagetv.vibe.miniclient.android;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnAddServerListener} interface
 * to handle interaction events.
 * Use the {@link AddServerFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AddServerFragment extends DialogFragment {
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_SERVER_NAME = "servername";
    private static final String ARG_SERVER_ADDR = "serveraddr";

    EditText serverName;
    EditText serverAddr;

    private OnAddServerListener mListener;

    public AddServerFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param serverName Parameter 1.
     * @param serverAddr Parameter 2.
     * @return A new instance of fragment AddServerFragment.
     */
    public static AddServerFragment newInstance(String serverName, String serverAddr) {
        AddServerFragment fragment = new AddServerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SERVER_NAME, serverName);
        args.putString(ARG_SERVER_ADDR, serverAddr);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_add_server, container, false);
        serverName = (EditText) v.findViewById(R.id.server_name);
        serverAddr = (EditText) v.findViewById(R.id.server_address);
        View addButton = v.findViewById(R.id.button_ok);
        addButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onButtonPressed();
            }
        });

        // Fire OS API 25 EditText consumes DPAD_UP/DOWN instead of following
        // nextFocusDown/nextFocusUp. Handle those keys explicitly so this TV
        // dialog remains usable without a touchscreen or mouse.
        serverName.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    serverAddr.requestFocus();
                }
                return true;
            }
            return false;
        });
        serverAddr.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    serverName.requestFocus();
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    addButton.requestFocus();
                }
                return true;
            }
            return false;
        });
        addButton.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    serverAddr.requestFocus();
                }
                return true;
            }
            return false;
        });
        serverName.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                serverAddr.requestFocus();
                return true;
            }
            return false;
        });
        serverAddr.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onButtonPressed();
                return true;
            }
            return false;
        });
        if (getArguments() != null) {
            serverName.setText(getArguments().getString(ARG_SERVER_NAME));
            serverAddr.setText(getArguments().getString(ARG_SERVER_ADDR));
        }
        return v;
    }

    // @OnClick(R.id.button_ok)
    public void onButtonPressed() {
        if (mListener != null) {
            mListener.onAddServer(serverName.getText().toString(), serverAddr.getText().toString());
        }
        dismiss();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnAddServerListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnAddServerListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnAddServerListener {
        void onAddServer(String name, String addr);
    }
}
