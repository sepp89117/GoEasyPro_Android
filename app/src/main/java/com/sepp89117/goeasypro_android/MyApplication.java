package com.sepp89117.goeasypro_android;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.widget.Toast;

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
import java.util.ArrayList;

public class MyApplication extends Application {
    @Override
    public void onCreate () {
        super.onCreate();

        loadSettingsValuesFromRes();
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
            Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
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

        /*if (settingsValues == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "An error happened. Please restart the app!", Toast.LENGTH_SHORT).show();
                finish();
            });
        }*/
    }
}
