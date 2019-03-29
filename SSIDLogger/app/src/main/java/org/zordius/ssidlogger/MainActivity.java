package org.zordius.ssidlogger;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize view and initialize WiFiReceiver
        setContentView(R.layout.activity_main);
        WifiReceiver.init(this);

        // Thread to sync WiFiReceiver and Activity UI
        new Thread() {
            @Override
            public void run() {
                syncStatus();
                bindDone();
            }
        }.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateFileStatus();
    }

    // Listen for changes to text fields
    public void bindDone() {
        // handle set logfile
        ((EditText) findViewById(R.id.editFilename))
                .setOnEditorActionListener(new EditText.OnEditorActionListener() {
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            if (!WifiReceiver.setLogFile(v.getContext(),
                                    ((EditText) v).getText().toString())) {
                                syncStatus();
                            }
                        }
                        return false;
                    }
                });

        // handle comment
        ((EditText) findViewById(R.id.editComment))
                .setOnEditorActionListener(new EditText.OnEditorActionListener() {
                    public boolean onEditorAction(TextView v, int actionId,
                                                  KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_SEND) {
                            doComment();
                            return true;
                        }
                        return false;
                    }
                });
    }

    // Syncs UI with WiFi Receiver status
    public void syncStatus() {
        ((ToggleButton) findViewById(R.id.logSwitch)).setChecked(WifiReceiver
                .isEnabled(this));
        ((Button) findViewById(R.id.btnMode)).setText(WifiReceiver.getMode(this));
        ((ToggleButton) findViewById(R.id.frequencySwitch))
                .setChecked(WifiReceiver.frequent);
        ((EditText) findViewById(R.id.editFilename))
                .setText(WifiReceiver.logFile,
                        TextView.BufferType.EDITABLE);

        setupLoggingUI();
    }

    // Enable/Disable UI Elements
    public void setupLoggingUI() {
        boolean isLogging = ((ToggleButton) findViewById(R.id.logSwitch)).isChecked();
        ((ToggleButton) findViewById(R.id.frequencySwitch)).setEnabled(!isLogging);
        ((ToggleButton) findViewById(R.id.frequencySwitch)).setEnabled(!isLogging);
        ((EditText) findViewById(R.id.editFilename)).setEnabled(!isLogging);
        ((Button) findViewById(R.id.btnMode)).setEnabled(!isLogging);

        if (!isLogging) {
            ((Button) findViewById(R.id.btnFingerprint)).setEnabled(false);
        } else if (!WifiReceiver.hasFingerprint
                && WifiReceiver.getMode(this).equals(getResources().getString(R.string.parkingMode))) {
            ((Button) findViewById(R.id.btnFingerprint)).setEnabled(true);
            ((Button) findViewById(R.id.btnFingerprint)).setClickable(true);
        }
        WifiReceiver.setLogFile(this, ((EditText) findViewById(R.id.editFilename)).getText().toString());
    }

    public void updateFileStatus() {
        ((TextView) findViewById(R.id.textLSize))
                .setText(WifiReceiver.getLogSize() + "KB");
        ((TextView) findViewById(R.id.textLFree))
                .setText(WifiReceiver.getFreeSize() + "MB");
    }

    public void onClickLog(View v) {
        WifiReceiver.toggleScan(this, ((ToggleButton) v).isChecked());
        setupLoggingUI();
    }

    public void onClickMode(View v) {
        String modeText = ((Button) v).getText().toString();

        modeText = modeText.trim();

        if (modeText.equals(getResources().getString(R.string.passive)))
        {
            WifiReceiver.toggleMode(this, 1);
        } else if (modeText.equals(getResources().getString(R.string.active)))
        {
            WifiReceiver.toggleMode(this, 2);
        } else {
            WifiReceiver.toggleMode(this, 0);
        }
        ((Button) v).setText(WifiReceiver.getMode(this));
    }

    public void onClickFrequency(View v) {
        WifiReceiver.toggleLongScan(((ToggleButton) v).isChecked());
    }

    public void onClickFingerprint(View v) {
        if (WifiReceiver.isEnabled(this)) {
            WifiReceiver.toggleFingerprint(this);
            v.setClickable(false);
            v.setEnabled(false);
        }
    }

    public void doComment() {
        EditText cmt = (EditText) findViewById(R.id.editComment);
        WifiReceiver.writeLog("COMMENT " + cmt.getText().toString());
        cmt.setText("", TextView.BufferType.EDITABLE);
    }
}
