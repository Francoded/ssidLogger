package org.zordius.ssidlogger;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class WifiReceiver extends BroadcastReceiver {

    // Helper WiFiScan class for Fingerprint
    public class WiFiScan {

        public WiFiScan(){ accessPoints = new HashMap<String, String>(); }

        public void addAccessPoint(String BSSID, String SSID){
            accessPoints.put(BSSID,SSID);
        }

        // Key is BSSID - Unique
        // Value is SSID
        HashMap<String,String> accessPoints;

    }

    // Mode can be PASSIVE - 0
    // Mode can be ACTIVE - 1
    // Mode can be FSU - 2
    public enum  MODE { ACTIVE, PASSIVE, FSU }

    public static final String PREFERENCES = "org.zordius.ssidlogger.preference";
    public static final String LOGFILE = "SSIDLogger/test.csv";
    public static final String PREF_LOGFILE = "SSIDLogger/test.csv";
    public static final String PREF_MODE = "0";
    public static final String PREF_SCANINTERVAL = "scanInterval";
    public static final String PREF_FREQUENT = "false";


    public static WifiManager wifi = null;
    public static AlarmManager alarm = null;
    public static PendingIntent pending = null;

    public static int frequency = 60;
    public static boolean frequent = false;
    public static boolean receiving = false;

    public static MODE currentMode = MODE.PASSIVE;
    public static BlockingQueue<WiFiScan> fingerprint;
    public static boolean hasFingerprint = false;

    public static SharedPreferences pref = null;
    public static String logFile = null;

    public static void init(Context context) {

        // Initialize WiFi Manager
        // FIXME: Add .getApplicationContext() ?
        wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        fingerprint = new ArrayBlockingQueue<WiFiScan>(4);

        // Load saved preferences
        pref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        logFile = pref.getString(PREF_LOGFILE, null);
        frequency = pref.getInt(PREF_SCANINTERVAL, 60);
        frequent = pref.getBoolean(PREF_FREQUENT,false);

        switch(pref.getInt(PREF_MODE,0))
        {
            case 0:
            default:
                currentMode = MODE.PASSIVE;
                break;
            case 1:
                currentMode = MODE.ACTIVE;
                break;
            case 2:
                currentMode = MODE.FSU;
                break;
        }


        if (logFile == null) {
            logFile = Environment.getExternalStorageDirectory().toString()
                    + File.separator + LOGFILE;
        }
    }

    public static boolean isEnabled(Context context) {
        return context.getPackageManager().getComponentEnabledSetting(
                new ComponentName(context, WifiReceiver.class)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    public static void toggleScan(Context context, boolean enable) {
        if (enable) {
            hasFingerprint = false;
            fingerprint.clear();

            doScan(context);
            if (currentMode != MODE.PASSIVE) {
                setAlarm(context);
            }
        } else {
            receiveWifi(context, false);
            if (currentMode != MODE.PASSIVE) {
                cancelAlarm(context);
            }
        }
    }

    public static void doScan(Context context) {
        // If already receiving, do nothing
        if (receiving)
            return;

        receiveWifi(context, true);
        if (wifi.isWifiEnabled()) {
            wifi.startScan();
        } else {
            wifi.setWifiEnabled(true);
        }
    }

    public static void toggleFingerprint(Context context) {
        hasFingerprint = !hasFingerprint;
        writeLog("FINGERPRINT");
    }

    public static void toggleMode(Context context, int mode) {
        switch (mode)
        {
            case 0:
                currentMode = MODE.PASSIVE;
                pref.edit().putInt(PREF_MODE, mode).commit();
                break;
            case 1:
                currentMode = MODE.ACTIVE;
                pref.edit().putInt(PREF_MODE, mode).commit();
                break;
            case 2:
                currentMode = MODE.FSU;
                pref.edit().putInt(PREF_MODE, mode).commit();
                break;
            default:
                break;
        }
    }

    public static String getMode(Context context) {
        if (currentMode == MODE.ACTIVE)
            return context.getResources().getString(R.string.active);
        else if (currentMode == MODE.FSU)
            return context.getResources().getString(R.string.parkingMode);
        else
            return context.getResources().getString(R.string.passive);
    }

    public static void toggleLongScan(boolean isFrequent) {

        if (currentMode == MODE.ACTIVE)
        {
            frequency = isFrequent ? 30 : 60;
        } else if (currentMode == MODE.FSU)
        {
            frequency = isFrequent ? 2 : 30;
        }

        frequent = isFrequent;
        pref.edit().putInt(PREF_SCANINTERVAL, frequency).commit();
        pref.edit().putBoolean(PREF_FREQUENT, frequent).commit();
    }

    public static boolean setLogFile(Context context, String name) {
        logFile = name;
        if (writeLog("SETFILE")) {
            return pref.edit().putString(PREF_LOGFILE, name).commit();
        }
        logFile = null;
        return false;
    }

    public static int getFreeSize() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        int sdAvailSize = stat.getAvailableBlocks() * stat.getBlockSize();
        return sdAvailSize >> 20;
    }

    public static boolean writeLog( String text) {
        // skip empty log text
        if ((text == null) || (text.length() == 0)) {
            return false;
        }

        try {
            FileWriter log = new FileWriter(logFile, true);
            log.write(new SimpleDateFormat("HH:mm:ss z", Locale.US)
                    .format(new Date()) + "," + text + "\n");
            log.close();
            return true;
        } catch (Exception e) {
            Log.d("logerr", text);
        }

        return false;
    }

    public static int getLogSize() {
        if (logFile == null)
            return 0;
        return (int) ((new File(logFile)).length() / 1024);
    }

    protected static void readyAlarm(Context context) {
        if (alarm == null) {
            alarm = (AlarmManager) context
                    .getSystemService(Context.ALARM_SERVICE);
            pending = PendingIntent.getBroadcast(context, 0, new Intent(
                    context, WifiReceiver.class), 0);
        }
    }

    protected static void setAlarm(Context context) {

        readyAlarm(context);

        // Set Frequency based on mode
        if (currentMode == MODE.ACTIVE) {
            frequency = frequent ? 30 : 60;
        } else if (currentMode == MODE.FSU) {
            frequency = frequent ? 2 : 30;
        }
        pref.edit().putInt(PREF_SCANINTERVAL, frequency).commit();

        alarm.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                1000 * frequency, pending);
    }

    protected static void cancelAlarm(Context context) {
        readyAlarm(context);
        alarm.cancel(pending);
    }

    protected static void receiveWifi(Context context, boolean enable) {
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, WifiReceiver.class),
                enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        // Handle wifi scan result
        String action = intent.getAction();
        System.out.println("GOT INTENT: " + action);
        if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
            // Receiving Lock?
            receiving = true;

            writeLog("SCAN");
            // get wifi scan results and write/store them
            List<ScanResult> results = wifi.getScanResults();
            WiFiScan current = new WiFiScan();
            for (ScanResult R : results) {
                current.addAccessPoint(R.BSSID,R.SSID);
                writeLog(R.BSSID + "," + R.level + "," + R.SSID);
            }

            // Modify fingerprint if needed
            if (!hasFingerprint || fingerprint.remainingCapacity() != 0) {
                System.out.println("Still no valid fingerprint!");
                if (fingerprint.remainingCapacity() == 0) {
                    fingerprint.remove();
                }
                fingerprint.add(current);
            } else if (hasFingerprint) {
                System.out.println("Valid fingerprint!");

            }

            context.sendBroadcast(new Intent("ACTION_UPDATE"));

            // No longer receiving results
            receiving = false;
            return;
        }

        doScan(context);
    }
}
