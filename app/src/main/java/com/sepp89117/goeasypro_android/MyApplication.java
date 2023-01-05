package com.sepp89117.goeasypro_android;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class MyApplication extends Application {
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
}
