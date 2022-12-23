package com.sepp89117.goeasypro_android;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class GoProDevice {
    private static final int MAX_FIFO_SIZE = 11;
    public BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    public boolean paired = false;
    public boolean connected = false;
    private List<BluetoothGattService> services;
    public String Name;
    public String Address;
    public String Model = "?";
    private int ModelID = 0;
    public String Preset = "N/A";
    public String Battery = "?";
    public String Memory = "?";
    private boolean gotAutomaticUpdate = false;
    public int Rssi = 0;
    private String LE = "None"; // last error
    private String wifiSsid = "";
    private String wifiPw = "";
    private Date lastKeepAlive = new Date();
    private Date lastPresetAndRssiQuery = new Date();
    private Date lastMemoryQuery = new Date();
    private Date lastBatteryRead = new Date();
    private BluetoothGattCharacteristic wifiSsidCharacteristic;
    private BluetoothGattCharacteristic wifiPwCharacteristic;
    private BluetoothGattCharacteristic battLevelCharacteristic;
    private BluetoothGattCharacteristic commandCharacteristic;
    private BluetoothGattCharacteristic commandRespCharacteristic;
    private BluetoothGattCharacteristic settingsCharacteristic;
    private BluetoothGattCharacteristic settingsRespCharacteristic;
    private BluetoothGattCharacteristic queryCharacteristic;
    private BluetoothGattCharacteristic queryRespCharacteristic;
    private BluetoothGattCharacteristic modelNoCharacteristic;

    // Custom GoPro services
    private final static String wifiServiceUUID = "b5f90001-aa8d-11e3-9046-0002a5d5c51b";    // GoPro WiFi Access Point
    private final static String controlServiceUUID = "0000fea6-0000-1000-8000-00805f9b34fb"; // Cam control service
    // Standard services
    private final static String defInfoUUID = "0000180a-0000-1000-8000-00805f9b34fb";  // Device information
    private final static String battInfoUUID = "0000180f-0000-1000-8000-00805f9b34fb"; // Battery service

    // Custom GoPro characteristics
    private final static String wifiSsidUUID = "b5f90002-aa8d-11e3-9046-0002a5d5c51b";      // Wifi [READ | WRITE]
    private final static String wifiPwUUID = "b5f90003-aa8d-11e3-9046-0002a5d5c51b";        // Wifi [READ | WRITE]
    private final static String commandUUID = "b5f90072-aa8d-11e3-9046-0002a5d5c51b";       // Command [WRITE]
    private final static String commandRespUUID = "b5f90073-aa8d-11e3-9046-0002a5d5c51b";   // Command response [NOTIFY]
    private final static String settingsUUID = "b5f90074-aa8d-11e3-9046-0002a5d5c51b";      // Settings [WRITE]
    private final static String settingsRespUUID = "b5f90075-aa8d-11e3-9046-0002a5d5c51b";  // Settings response [NOTIFY]
    private final static String queryUUID = "b5f90076-aa8d-11e3-9046-0002a5d5c51b";         // Query [WRITE]
    private final static String queryRespUUID = "b5f90077-aa8d-11e3-9046-0002a5d5c51b";     // Query response [NOTIFY]
    // Standard characteristics
    private final static String modelNoUUID = "00002a24-0000-1000-8000-00805f9b34fb";   // Model number
    private final static String battLevelUUID = "00002a19-0000-1000-8000-00805f9b34fb"; // Battery level [READ | NOTIFY] -> "100% battery level"

    public Context context;

    private final Timer actionTimer = new Timer();
    private final Timer execWatchdogTimer = new Timer();

    private final ArrayList<Runnable> btActionBuffer = new ArrayList<>();

    private Date lastExecWhile = new Date();

    private void execNextBtAction() {
        new Thread(() -> {
            while (btActionBuffer.size() < 1) {
                lastExecWhile = new Date();
            }
            lastExecWhile = new Date();

            Runnable runnable = btActionBuffer.get(0);
            if (runnable == null) {
                Log.e("execNextBtAction", "runnable was 'null'"); //How can that happen? Only if pause at breakpoint?
            } else {
                try {
                    runnable.run();
                    btActionBuffer.remove(runnable);
                } catch (Exception e) {
                    Log.e("execNextBtAction", e.getMessage());
                }
            }
        }).start();
    }

    private BroadcastReceiver pairingRequestReceiver;
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt _gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (bluetoothGatt == _gatt)
                    bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (bluetoothGatt == _gatt) {
                    if (connectBtCallback != null)
                        connectBtCallback.onBtConnectionStateChange(false);

                    connected = false;

                    if (dataChangedCallback != null)
                        dataChangedCallback.onDataChanged();
                }
            }
        }

        @SuppressLint("SuspiciousIndentation")
        @Override
        public void onServicesDiscovered(BluetoothGatt _gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (bluetoothGatt == _gatt) {
                    connected = true;

                    if (connectBtCallback != null)
                        connectBtCallback.onBtConnectionStateChange(true);

                    if (dataChangedCallback != null)
                        dataChangedCallback.onDataChanged();

                    services = bluetoothGatt.getServices();

                    execNextBtAction(); // first call initiates buffer executions

                    for (BluetoothGattService service : services) {
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        String serviceUuid = service.getUuid().toString();

                        if (serviceUuid.equals(controlServiceUUID) || serviceUuid.equals(defInfoUUID) || serviceUuid.equals(battInfoUUID) || serviceUuid.equals(wifiServiceUUID)) {
                            for (BluetoothGattCharacteristic characteristic : characteristics) {
                                String characteristicUuid = characteristic.getUuid().toString();

                                switch (characteristicUuid) {
                                    case commandUUID:
                                        commandCharacteristic = characteristic;
                                        break;
                                    case commandRespUUID:
                                        commandRespCharacteristic = characteristic;
                                        startCrcNotification();
                                        break;
                                    case settingsUUID:
                                        settingsCharacteristic = characteristic;
                                        break;
                                    case settingsRespUUID:
                                        settingsRespCharacteristic = characteristic;
                                        startSrcNotification();
                                        break;
                                    case queryUUID:
                                        queryCharacteristic = characteristic;
                                        break;
                                    case queryRespUUID:
                                        queryRespCharacteristic = characteristic;
                                        startQrcNotification();
                                        registerForSettingsValueUpdates();
                                        getPreset();
                                        getMemory();
                                        break;
                                    case modelNoUUID:
                                        modelNoCharacteristic = characteristic;
                                        readModelNo();
                                        break;
                                    case battLevelUUID:
                                        battLevelCharacteristic = characteristic;
                                        readBattLevel();
                                        break;
                                    case wifiSsidUUID:
                                        wifiSsidCharacteristic = characteristic;
                                        readWifiSsid();
                                        break;
                                    case wifiPwUUID:
                                        wifiPwCharacteristic = characteristic;
                                        readWifiPw();
                                        break;
                                    default:
                                        //Log.d("???","Unknown characteristic UUID in Service discovered. UUID: " + characteristicUuid);
                                }
                            }
                        }
                        //else {Log.d("???","Unknown Service discovered. UUID: " + serviceUuid);}
                    }

                    //start timed actions
                    actionTimer.schedule(timedActions, 0, 250);
                    execWatchdogTimer.schedule(timedExecWatchdog, 0, 1000);
                }
            } else {
                Log.e("BLE", "onServicesDiscovered status " + status);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String characteristicUuid = characteristic.getUuid().toString();
                byte[] value = characteristic.getValue();

                switch (characteristicUuid) {
                    case modelNoUUID: //only
                        ByteBuffer byteBuffer = ByteBuffer.allocate(8);
                        byteBuffer.put(value);
                        String decoded = new String(byteBuffer.array(), StandardCharsets.UTF_8);
                        ModelID = Integer.parseInt(decoded, 16);
                        Model = gopro_models.getOrDefault(ModelID, "Unknown model");
                        break;
                    case battLevelUUID: //only
                        Battery = Integer.toString(value[0]) + "%";
                        break;
                    case wifiSsidUUID:
                        ByteBuffer byteBuffer2 = ByteBuffer.allocate(33);
                        byteBuffer2.put(value);
                        wifiSsid = new String(byteBuffer2.array(), StandardCharsets.UTF_8);
                        break;
                    case wifiPwUUID:
                        ByteBuffer byteBuffer3 = ByteBuffer.allocate(64);
                        byteBuffer3.put(value);
                        wifiPw = new String(byteBuffer3.array(), StandardCharsets.UTF_8);
                        break;
//                    default:
//                        Log.d("???", "Unknown characteristic UUID in Service discovered. UUID: " + characteristicUuid);
                }

                if (dataChangedCallback != null)
                    dataChangedCallback.onDataChanged();
            } else if (status == BluetoothGatt.GATT_FAILURE) {
                Log.e("BLE", "onCharacteristicRead status GATT_FAILURE");
            } else {
                Log.e("BLE", "onCharacteristicRead status " + status);
            }
            execNextBtAction();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            execNextBtAction();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String characteristicUuid = characteristic.getUuid().toString();

            byte[] bytes = characteristic.getValue();

            int headerLength = getHeaderLength(bytes); // 1-2 bytes
            byte commandId = bytes[headerLength];
            int error = bytes[headerLength + 1];

            switch (characteristicUuid) {
                case commandRespUUID:
                    switch (error) {
                        case 0:
                            LE = "Success";
                            break;
                        case 1:
                            LE = "Error";
                            break;
                        case 2:
                            LE = "Inv. Param.";
                            break;
                        default:
                            LE = "Unknown";
                    }
                    break;
                case settingsRespUUID:
                    //if (bytes[3] == 0x5B) { // Keep alive
                    // do nothing with keep alive response
                    //}
                    break;
                case queryRespUUID:
                    if (commandId == (byte) 0x13 && bytes[3] == 0x36 && bytes[4] == 8) { // Remaining space
                        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                        buffer.put(bytes, 5, 8);
                        buffer.flip();
                        long memBytes = buffer.getLong();

                        Memory = String.format("%.2f", ((memBytes / 1024.00) / 1024.00)) + " GB";
                    } else if (commandId == (byte) 0x13 && bytes[3] == 0x61 && bytes[4] == 4) { // Active preset
                        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
                        buffer.put(bytes, 5, 4);
                        buffer.flip();
                        int presetBytes = buffer.getInt();

                        Preset = presets.getOrDefault(presetBytes, "unknown");
                    } else if (commandId == (byte) 0x93) {
                        // Query Multi-Value response format: [MESSAGE LENGTH]:[QUERY ID]:[COMMAND STATUS]:[ID1]:[LENGTH1]:[VALUE1]:[ID2]:[LENGTH2]:[VALUE2]:...
                        if(error == 0) {
                            gotAutomaticUpdate = true;
                            Log.d("NOTIFIED", "got automatic value update");
                            int id1 = bytes[3];
                            int len1 = bytes[4];
                            ByteBuffer buffer = ByteBuffer.allocate(len1);
                            buffer.put(bytes, 5, len1);
                            buffer.flip();

                            switch (id1) {
                                case 54:
                                    // Remaining space on the sdcard in Kilobytes as integer
                                    long memBytes = buffer.getLong();
                                    Memory = String.format("%.2f", ((memBytes / 1024.00) / 1024.00)) + " GB";
                                    break;
                                case 70:
                                    // Internal battery percentage as percent
                                    int battLevel = bytes[5];
                                    Battery = battLevel + "%";
                                    break;
                                case 97:
                                    // Current Preset ID as integer
                                    int presetId = buffer.getInt();
                                    Preset = presets.getOrDefault(presetId, "unknown");
                                    break;
                                default:
                            }

                            if (bytes[0] > 4 + len1) {
                                int offset1 = 4 + len1;
                                //multi value response
                                int id2 = bytes[offset1];
                                int len2 = bytes[offset1 + 1];
                                if (id2 == 54 || id2 == 70 || id2 == 97) {
                                    Log.e("NOTIFIED", "Unhandled multi-value response in automatic update with command ID=" + id2 + ", length=" + len2);
                                }
                            }
                        }
                    }
                    break;
//                default: Log.d("???","Unknown characteristic UUID in Service discovered. UUID: " + characteristicUuid);
            }

            if (dataChangedCallback != null)
                dataChangedCallback.onDataChanged();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            execNextBtAction();
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Rssi = rssi;

                if (dataChangedCallback != null)
                    dataChangedCallback.onDataChanged();
            } else {
                Log.e("BLE", "onReadRemoteRssi status " + status);
            }
            execNextBtAction();
        }
    };

    private static int getHeaderLength(byte[] command) {
        int headerLength = 0; // 11: Reserved (no msg)
        if (getBit(command[0], 6) == 1 && getBit(command[0], 5) == 0)
            headerLength = 3; // 10: Extended (16-bit)
        else if (getBit(command[0], 6) == 0 && getBit(command[0], 5) == 1)
            headerLength = 2; // 01: Extended (13-bit)
        else if (getBit(command[0], 6) == 0 && getBit(command[0], 5) == 0)
            headerLength = 1; // 00: General

        return headerLength;
    }

    private static int getBit(int num, int bit) {
        return ((num >> bit) % 2 != 0 ? 1 : 0);
    }

    //region timers
    private final TimerTask timedExecWatchdog = new TimerTask() {
        @Override
        public void run() {
            if (!connected) execWatchdogTimer.cancel();

            Date now = new Date();
            if (now.getTime() - lastExecWhile.getTime() >= 2000) {
                //the execThred is not more running -> start again
                Log.d("ExecWatchdog", "ExecWatchdog triggered!");
                execNextBtAction();
            }
        }
    };

    private final TimerTask timedActions = new TimerTask() {
        @Override
        public void run() {
            if (!connected) actionTimer.cancel();

            long now = new Date().getTime();

            // Send "keep alive" every 60 seconds; only for models newer then hero 8
            if (now - lastKeepAlive.getTime() > 60000) {
                keepAlive();
                lastKeepAlive = new Date();
            }

            // Query "Remaining space"
            if (!gotAutomaticUpdate && now - lastMemoryQuery.getTime() > 7000) {
                getMemory();
                lastMemoryQuery = new Date();
            }

            // Query "Active preset" & read Rssi
            if (now - lastPresetAndRssiQuery.getTime() > 2000) {
                if (!gotAutomaticUpdate) {
                    if (ModelID > 21) { // qPreset query not supported from Hero 5 Black
                        getPreset();
                    } else {
                        // TODO other way to get preset from GoPro Hero 5 Black?
                    }
                }
                readRssi();
                lastPresetAndRssiQuery = new Date();
            }

            // Read "Battery level"
            if (!gotAutomaticUpdate && now - lastBatteryRead.getTime() > 10000) {
                readBattLevel();
                lastBatteryRead = new Date();
            }
        }
    };
    //endregion timers

    // TODO get all GoPro model IDs
    Map<Integer, String> gopro_models = new HashMap<Integer, String>() {{
        put(19, "Hero 5 Black");
        put(21, "Hero 5 Session");
        put(24, "Hero 6 Black");
        put(30, "Hero 7 Black");
        put(50, "Hero 8 Black");
        put(51, "GoPro Max");
        put(55, "Hero 9 Black");
        put(57, "Hero 10 Black");
        put(58, "Hero 11 Black");
        put(60, "Hero 11 Black Mini");
    }};

    Map<Integer, String> presets = new HashMap<Integer, String>() {{
        put(0x00000000, "Standard");
        put(0x00000001, "Activity");
        put(0x00000002, "Cinematic");
        put(0x00000003, "Slo-Mo");
        put(0x00000004, "Ultra Slo-Mo");
        put(0x00000005, "Basic");
        put(0x00010000, "Photo");
        put(0x00010001, "Live Burst");
        put(0x00010002, "Burst Photo");
        put(0x00010003, "Night Photo");
        put(0x00020000, "Time Warp");
        put(0x00020001, "Time Lapse");
        put(0x00020002, "Night Lapse");
        put(0x00030000, "Max Video");
        put(0x00040000, "Max Photo");
        put(0x00050000, "Max Timewarp");
    }};

    @SuppressLint("MissingPermission")
    private void startCrcNotification() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (commandRespCharacteristic == null || bluetoothGatt == null) {
                execNextBtAction();
                return;
            }

            bluetoothGatt.setCharacteristicNotification(commandRespCharacteristic, true);
            BluetoothGattDescriptor descriptor = commandRespCharacteristic.getDescriptors().get(0);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!bluetoothGatt.writeDescriptor(descriptor)) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void startSrcNotification() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (settingsRespCharacteristic == null || bluetoothGatt == null) {
                execNextBtAction();
                return;
            }

            bluetoothGatt.setCharacteristicNotification(settingsRespCharacteristic, true);
            BluetoothGattDescriptor descriptor = settingsRespCharacteristic.getDescriptors().get(0);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!bluetoothGatt.writeDescriptor(descriptor)) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void startQrcNotification() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (queryRespCharacteristic == null || bluetoothGatt == null) {
                execNextBtAction();
                return;
            }

            bluetoothGatt.setCharacteristicNotification(queryRespCharacteristic, true);
            BluetoothGattDescriptor descriptor = queryRespCharacteristic.getDescriptors().get(0);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!bluetoothGatt.writeDescriptor(descriptor)) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    /*
        Readings
     */
    @SuppressLint("MissingPermission")
    private void readModelNo() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (bluetoothGatt == null || modelNoCharacteristic == null || !connected) {
                execNextBtAction();
                return;
            }
            if (!bluetoothGatt.readCharacteristic(modelNoCharacteristic)) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void readBattLevel() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (bluetoothGatt == null || battLevelCharacteristic == null || !connected) {
                execNextBtAction();
                return;
            }
            if (bluetoothGatt.readCharacteristic(battLevelCharacteristic)) {
                lastBatteryRead = new Date();
            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void readWifiSsid() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (bluetoothGatt == null || wifiSsidCharacteristic == null || !connected) {
                execNextBtAction();
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiSsidCharacteristic)) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void readWifiPw() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (bluetoothGatt == null || wifiPwCharacteristic == null || !connected) {
                execNextBtAction();
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiPwCharacteristic)) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void readRssi() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (bluetoothGatt == null || !connected) {
                execNextBtAction();
                return;
            }
            if (!bluetoothGatt.readRemoteRssi()) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    /*
        Settings (0x0074)
     */
    @SuppressLint("MissingPermission")
    private void keepAlive() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            if (ModelID > 50) {
                byte[] msg = {0x03, 0x5B, 0x01, 0x42};
                if (settingsCharacteristic == null || !settingsCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(settingsCharacteristic)) {
                    execNextBtAction();
                }
            } else {
                // TODO another way to keep alive for hero 8 and downwards?

                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void setDateTime() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            Date now = new Date();
            byte yyH = (byte) (now.getYear() & 0xFF);
            byte yyL = (byte) ((now.getYear() >> 8) & 0xFF);
            // Set date/time to 2022-01-02 03:04:05 	Command: 09: 0D: 07: 07:E6: 01: 02: 03: 04: 05
            byte[] msg = {0x09, 0x0d, 0x07, yyL, yyH, (byte) (now.getMonth() + 1), (byte) now.getDate(), (byte) now.getHours(), (byte) now.getMinutes(), (byte) now.getSeconds()};
            if (settingsCharacteristic == null || !settingsCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(settingsCharacteristic)) {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    /*
        Commands (0x0072)
     */
    @SuppressLint("MissingPermission")
    public void shutterOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] shutterOn = {0x03, 0x01, 0x01, 0x01};
            if (commandCharacteristic != null && commandCharacteristic.setValue(shutterOn) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void shutterOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] shutterOff = {0x03, 0x01, 0x01, 0x00};
            if (commandCharacteristic != null && commandCharacteristic.setValue(shutterOff) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void sleep() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] camSleep = {0x01, 0x05};
            if (commandCharacteristic != null && commandCharacteristic.setValue(camSleep) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                bluetoothGatt.disconnect();
            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void wifiApOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] wifiApOn = {0x03, 0x17, 0x01, 0x01};
            if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOn) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void wifiApOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] wifiApOff = {0x03, 0x17, 0x01, 0x00};
            if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOff) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void locateOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] msg = {0x03, 0x16, 0x01, 0x01};
            if (commandCharacteristic != null && commandCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void locateOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] msg = {0x03, 0x16, 0x01, 0x00};
            if (commandCharacteristic != null && commandCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    public void highlight() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] highlight = {0x01, 0x18};
            if (commandCharacteristic != null && commandCharacteristic.setValue(highlight) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    /*
        Queries (0x0076)
     */
    @SuppressLint("MissingPermission")
    private void registerForSettingsValueUpdates() {
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] queryStatusUpdates = {0x04, 0x53, 54, 70, 97}; //54 (Remaining space on the sdcard in Kilobytes as integer), 70 (Internal battery percentage as percent), 97 (Current Preset ID as integer)
            if (queryCharacteristic != null && queryCharacteristic.setValue(queryStatusUpdates) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void getMemory() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] msg = {0x02, 0x13, 0x36};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    @SuppressLint("MissingPermission")
    private void getPreset() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            execNextBtAction();
            return;
        }
        if (!btActionBuffer.add(() -> {
            if (!connected) {
                execNextBtAction();
                return;
            }

            byte[] msg = {0x02, 0x13, 0x61};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastPresetAndRssiQuery = new Date();
            } else {
                execNextBtAction();
            }
        }))
            execNextBtAction();
    }

    /*
        Callback Interfaces
     */
    interface PairCallbackInterface {
        void onPairingFinished(boolean paired);
    }

    private PairCallbackInterface pairCallback;

    public void pair(PairCallbackInterface _pairCallback) {
        pairCallback = _pairCallback;

        try {
            Method m = bluetoothDevice.getClass().getMethod("createBond", (Class[]) null);
            m.invoke(bluetoothDevice, (Object[]) null);

            IntentFilter filter = new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST");
            pairingRequestReceiver = new BroadcastReceiver() {
                @SuppressLint("MissingPermission")
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                        try {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            device.connectGatt(context, true, gattCallback);
                            if (pairCallback != null)
                                pairCallback.onPairingFinished(true);
                        } catch (Exception e) {
                            e.printStackTrace();
                            if (pairCallback != null)
                                pairCallback.onPairingFinished(false);
                        }

                        try {
                            context.unregisterReceiver(pairingRequestReceiver);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            context.registerReceiver(pairingRequestReceiver, filter);
        } catch (Exception e) {
            if (pairCallback != null)
                pairCallback.onPairingFinished(false);
        }
    }


    interface ConnectBtCallbackInterface {
        void onBtConnectionStateChange(boolean connected);
    }

    private ConnectBtCallbackInterface connectBtCallback;

    @SuppressLint("MissingPermission")
    public void connectBt(ConnectBtCallbackInterface _connectBtCallback) {
        connectBtCallback = _connectBtCallback;
        bluetoothGatt = bluetoothDevice.connectGatt(context, true, gattCallback);
    }


    interface DataChangedCallbackInterface {
        void onDataChanged();
    }

    private DataChangedCallbackInterface dataChangedCallback;

    public void getDataChanges(DataChangedCallbackInterface _dataChangedCallback) {
        dataChangedCallback = _dataChangedCallback;
    }

    public void getLiveStream() {
        wifiApOn();
        connectWifi();
    }

    @SuppressLint("MissingPermission")
    private void connectWifi() {
        // Check if SSID and PSK are known
        if (Objects.equals(wifiSsid, "")) {
            readWifiSsid();
            Toast.makeText(context, "SSID is unknown. Please try again!", Toast.LENGTH_SHORT).show();
            return;
        } else if (Objects.equals(wifiPw, "")) {
            readWifiPw();
            Toast.makeText(context, "PSK is unknown. Please try again!", Toast.LENGTH_SHORT).show();
            return;
        }

        //This only works with Android 9 and below

        // Check if this network is already known
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration wifiConfiguration = null;
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks(); // deprecated in API level 29 (Android 10)
        for (WifiConfiguration _wifiConfiguration : list) {
            if (_wifiConfiguration.SSID != null && _wifiConfiguration.SSID.equals('"' + wifiSsid + '"')) {
                wifiConfiguration = _wifiConfiguration;
                break;
            }
        }

        if (wifiConfiguration == null) {
            // create WifiConfiguration instance
            WifiConfiguration newWifiConfiguration = new WifiConfiguration(); // deprecated in API level 29 (Android 10)
            newWifiConfiguration.SSID = '"' + wifiSsid + '"';
            newWifiConfiguration.preSharedKey = '"' + wifiPw + '"';

            //Add it to Android wifi manager settings
            wifiManager.addNetwork(newWifiConfiguration);

            // Connect to it
            wifiManager.disconnect();
            wifiManager.enableNetwork(newWifiConfiguration.networkId, true);
            wifiManager.reconnect();
        } else {
            // Check if already connected
            int connectedId = wifiManager.getConnectionInfo().getNetworkId(); // getConnectionInfo() is deprecated in API level 31 (Android 12)

            // If not already connected, connect it
            if (connectedId != wifiConfiguration.networkId) {
                wifiManager.disconnect();
                wifiManager.enableNetwork(wifiConfiguration.networkId, true);
                wifiManager.reconnect();
            }
        }
    }
}
