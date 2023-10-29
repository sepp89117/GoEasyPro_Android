package com.sepp89117.goeasypro_android;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentCallbacks2;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.sepp89117.goeasypro_android.gopro.GoMediaFile;
import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        ignoreAutoOff = sharedPref.getBoolean("ignore_auto_off", false);
        autoConnect = sharedPref.getBoolean("auto_connect", false);
        checkFirmware = sharedPref.getBoolean("check_firmware", true);
        checkAppUpdate = sharedPref.getBoolean("check_app_update", true);
        keepAliveWhenPaused = sharedPref.getBoolean("keep_alive_when_paused", true);

        loadSettingsValuesFromRes();
    }

    private boolean ignoreAutoOff = false;
    private boolean autoConnect = false;
    private boolean checkFirmware = true;
    private boolean checkAppUpdate = true;
    private boolean keepAliveWhenPaused = true;
    private boolean appIsPaused = false;

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            appIsPaused = true;
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    public boolean isAppPaused() {
        return appIsPaused;
    }

    public void resetIsAppPaused() {
        appIsPaused = false;
    }

    public boolean isAutoOffToIgnore() {
        return ignoreAutoOff;
    }

    public boolean shouldAutoConnect() {
        return autoConnect;
    }

    public boolean shouldCheckFirmware() {
        return checkFirmware;
    }

    public boolean shouldCheckAppUpdate() {
        return checkAppUpdate;
    }

    public boolean shouldKeptAliveWhenPaused() {
        return keepAliveWhenPaused;
    }

    private BluetoothAdapter _bluetoothAdapter;

    public BluetoothAdapter getBluetoothAdapter() {
        return _bluetoothAdapter;
    }

    public void setBluetoothAdapter(BluetoothAdapter bluetoothAdapter) {
        _bluetoothAdapter = bluetoothAdapter;
    }

    private ArrayList<GoProDevice> _goProDevices = new ArrayList<>();

    public ArrayList<GoProDevice> getGoProDevices() {
        return _goProDevices;
    }

    public void setGoProDevices(ArrayList<GoProDevice> goProDevices) {
        _goProDevices = goProDevices;
    }

    public static boolean checkBtDevConnected(BluetoothDevice device) {
        Method m;
        try {
            m = device.getClass().getMethod("isConnected", (Class[]) null);
            return (boolean) m.invoke(device, (Object[]) null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return false;
        }

    }

    public static String getReadableFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private GoProDevice _focusedDevice = null;

    public GoProDevice getFocusedDevice() {
        return _focusedDevice;
    }

    public void setFocusedDevice(GoProDevice goProDevice) {
        _focusedDevice = goProDevice;
    }

    private ArrayList<GoMediaFile> _goMediaFiles = new ArrayList<>();

    public ArrayList<GoMediaFile> getGoMediaFiles() {
        return _goMediaFiles;
    }

    public void setGoMediaFiles(ArrayList<GoMediaFile> goMediaFiles) {
        _goMediaFiles = goMediaFiles;
    }

    public JSONObject getSettingsValues() {
        return settingsValues;
    }

    private JSONObject settingsValues = null;

    private void loadSettingsValuesFromRes() {
        InputStream is = getResources().openRawResource(R.raw.go_settings);
        Writer writer = new StringWriter();
        char[] buffer = new char[1024];
        try {
            Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        } catch (Exception ignored) {

        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String jsonString = writer.toString();
        try {
            settingsValues = new JSONObject(jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
