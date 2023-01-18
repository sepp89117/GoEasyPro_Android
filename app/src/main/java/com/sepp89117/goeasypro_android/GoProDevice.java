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
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
//import androidx.appcompat.app.AlertDialog;

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

import okhttp3.OkHttpClient;

public class GoProDevice {
    //region BT connection stats
    public static final int BT_NOT_CONNECTED = 0;
    public static final int BT_CONNECTING = 1;
    public static final int BT_FETCHING_DATA = 2;
    public static final int BT_CONNECTED = 3;
    //endregion

    public static final int UNK_MODEL = 0;
    public static final int HERO8_BLACK = 50;
    public static final int HERO9_BLACK = 55;

    private static final int MAX_FIFO_SIZE = 15;

    private static final Map<Integer, String> presets = new HashMap<Integer, String>() {{
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

    private static final Map<Integer, String> modes = new HashMap<Integer, String>() {{
        put(0, "Video");
        put(1, "Photo");
        put(2, "Multishot");
        put(3, "Broadcast");
        put(4, "Playback");
        put(5, "Setup");
        put(6, "FW Update");
        put(7, "USB MTP");
        put(8, "SOS");
        put(9, "MEdit");
        put(10, "Calibration");
        put(11, "Direct Offload");
        put(12, "Video");
        put(13, "Time Lapse Video");
        put(14, "Video + Photo");
        put(15, "Looping");
        put(16, "Single Photo");
        put(17, "Photo");
        put(18, "Night Photo");
        put(19, "Burst Photo");
        put(20, "Time Lapse Photo");
        put(21, "Night Lapse Photo");
        put(22, "Broadcast Record");
        put(23, "Broadcast");
        put(24, "Time Warp Video");
    }};

    //region model depending strings
    private final static String streamStart_query_v1 = "http://10.5.5.9/gp/gpControl/execute?p1=gpStream&a1=proto_v2&c1=restart"; // -Hero 7 TODO ?
    private final static String streamStart_query_v2 = "http://10.5.5.9/gp/gpControl/execute?p1=gpStream&c1=restart"; // Hero 8 TODO and Max ?
    private final static String streamStart_query_v3 = "http://10.5.5.9/gopro/camera/stream/start"; // Hero 9+

    private final static String streamStop_query_v1 = "http://10.5.5.9/gp/gpControl/execute?p1=gpStream&c1=stop";
    private final static String streamStop_query_v2 = "http://10.5.5.9/gopro/camera/stream/stop";

    private final static String mediaList_query_v1 = "http://10.5.5.9/gp/gpMediaList"; // -Hero 8 TODO and Max ?
    private final static String mediaList_query_v2 = "http://10.5.5.9/gopro/media/list"; // TODO Hero 9+ ?

    private final static String thumNail_query_v1 = "http://10.5.5.9/gp/gpMediaMetadata?p="; // -Hero 8 TODO and Max ?
    private final static String thumNail_query_v2 = "http://10.5.5.9/gopro/media/thumbnail?path="; // TODO Hero 9+ ?

    private final static String keepAlive_v1 = "_GPHD_:0:0:2:0.000000\n"; // -Hero 7 TODO ?
    private final static String keepAlive_v2 = "_GPHD_:1:0:2:0.000000\n"; // Hero 8+
    //endregion

    //region BT UUIDs
    // Custom GoPro services
    private final static String wifiServiceUUID = "b5f90001-aa8d-11e3-9046-0002a5d5c51b";    // GoPro WiFi Access Point
    private final static String controlServiceUUID = "0000fea6-0000-1000-8000-00805f9b34fb"; // Cam control service
    // Standard services
    private final static String defInfoUUID = "0000180a-0000-1000-8000-00805f9b34fb";  // Device information
    // Custom GoPro characteristics
    private final static String wifiSsidUUID = "b5f90002-aa8d-11e3-9046-0002a5d5c51b";      // Wifi [READ | WRITE]
    private final static String wifiPwUUID = "b5f90003-aa8d-11e3-9046-0002a5d5c51b";        // Wifi [READ | WRITE]
    private final static String wifiStateUUID = "b5f90005-aa8d-11e3-9046-0002a5d5c51b";        // Wifi [READ | INDICATE]
    private final static String commandUUID = "b5f90072-aa8d-11e3-9046-0002a5d5c51b";       // Command [WRITE]
    private final static String commandRespUUID = "b5f90073-aa8d-11e3-9046-0002a5d5c51b";   // Command response [NOTIFY]
    private final static String settingsUUID = "b5f90074-aa8d-11e3-9046-0002a5d5c51b";      // Settings [WRITE]
    private final static String settingsRespUUID = "b5f90075-aa8d-11e3-9046-0002a5d5c51b";  // Settings response [NOTIFY]
    private final static String queryUUID = "b5f90076-aa8d-11e3-9046-0002a5d5c51b";         // Query [WRITE]
    private final static String queryRespUUID = "b5f90077-aa8d-11e3-9046-0002a5d5c51b";     // Query response [NOTIFY]
    private final static String nwManUUID = "b5f90090-aa8d-11e3-9046-0002a5d5c51b";     // Query [WRITE]
    private final static String nwManCmdUUID = "b5f90091-aa8d-11e3-9046-0002a5d5c51b";     // Query [WRITE]
    private final static String nwManRespUUID = "b5f90092-aa8d-11e3-9046-0002a5d5c51b";     // Query response [NOTIFY]
    // Standard characteristics
    private final static String modelNoUUID = "00002a24-0000-1000-8000-00805f9b34fb";   // Model number
    //endregion

    public Context context;

    private Timer actionTimer = new Timer();
    private Timer execWatchdogTimer = new Timer();
    private final ArrayList<Runnable> btActionBuffer = new ArrayList<>();
    private Date gattProgressStart = new Date();
    private boolean gattInProgress = false;
    private Thread execBtActionLoop;

    public BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    public boolean btPaired = false;
    public int btConnectionStage = BT_NOT_CONNECTED;
    public String name = "";
    public String Address = "";
    public String modelName = "NC";
    private int modelID = UNK_MODEL;
    public String Preset = "NC";
    public int BatteryPercent = 0;
    public String Memory = "NC";
    private boolean autoValueUpdatesRegistered = false;
    public int Rssi = 0;
    private String LE = "None"; // last error
    private Date lastKeepAlive = new Date();
    private Date lastOtherQueries = new Date();
    private Date lastMemoryQuery = new Date();
    private Date lastBatteryRead = new Date();
    private BluetoothGattCharacteristic wifiSsidCharacteristic = null;
    private BluetoothGattCharacteristic wifiPwCharacteristic = null;
    private BluetoothGattCharacteristic wifiStateCharacteristic = null;
    private BluetoothGattCharacteristic commandCharacteristic = null;
    private BluetoothGattCharacteristic commandRespCharacteristic = null;
    private BluetoothGattCharacteristic settingsCharacteristic = null;
    private BluetoothGattCharacteristic settingsRespCharacteristic = null;
    private BluetoothGattCharacteristic queryCharacteristic = null;
    private BluetoothGattCharacteristic queryRespCharacteristic = null;
    private BluetoothGattCharacteristic modelNoCharacteristic = null;
    private BluetoothGattCharacteristic nwManCmdCharacteristic = null;
    private BluetoothGattCharacteristic nwManRespCharacteristic = null;
    private int registerForAutoValueUpdatesCounter = 0;
    private ByteBuffer contPackBuffer;
    private static final int btActionDelay = 125;
    private boolean communicationInitiated = false;
    byte[] statusIDs = {43, 54, 69, 70, 97}; // https://gopro.github.io/OpenGoPro/ble_2_0#status-ids

    private boolean freshPaired = false;

    //Wifi
    public static final String goProIp = "10.5.5.9";
    public Integer wifiApState = -1;
    public boolean isWifiConnected = false;
    private String wifiSsid = "";
    private String wifiPw = "";
    private String apMacAddr = "";
    private ConnectivityManager connectivityManager = null;
    public String startStream_query = "";
    public String stopStream_query = "";
    public String getMediaList_query = "";
    public String getThumbNail_query = "";
    public String keepAlive_msg = "";
    public String myClientIP;
    private BroadcastReceiver pairingReceiver;

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt _gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    btConnectionStage = BT_FETCHING_DATA;

                    if (dataChangedCallback != null)
                        dataChangedCallback.onDataChanged();

                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    setDisconnected();
                }
            } else {
                setDisconnected();
            }
        }

        @SuppressLint("SuspiciousIndentation")
        @Override
        public void onServicesDiscovered(BluetoothGatt _gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (connectBtCallback != null)
                    connectBtCallback.onBtConnectionStateChange(true);

                List<BluetoothGattService> services = bluetoothGatt.getServices();

                for (BluetoothGattService service : services) {
                    List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                    String serviceUuid = service.getUuid().toString();

                    if (serviceUuid.equals(controlServiceUUID) || serviceUuid.equals(defInfoUUID) || serviceUuid.equals(wifiServiceUUID) || serviceUuid.equals(nwManUUID)) {
                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            String characteristicUuid = characteristic.getUuid().toString();

                            switch (characteristicUuid) {
                                case commandUUID:
                                    commandCharacteristic = characteristic;
                                    break;
                                case commandRespUUID:
                                    commandRespCharacteristic = characteristic;
                                    break;
                                case settingsUUID:
                                    settingsCharacteristic = characteristic;
                                    break;
                                case settingsRespUUID:
                                    settingsRespCharacteristic = characteristic;
                                    break;
                                case nwManCmdUUID:
                                    nwManCmdCharacteristic = characteristic;
                                    break;
                                case nwManRespUUID:
                                    nwManRespCharacteristic = characteristic;
                                    break;
                                case queryUUID:
                                    queryCharacteristic = characteristic;
                                    break;
                                case queryRespUUID:
                                    queryRespCharacteristic = characteristic;
                                    break;
                                case modelNoUUID:
                                    modelNoCharacteristic = characteristic;
                                    break;
                                case wifiSsidUUID:
                                    wifiSsidCharacteristic = characteristic;
                                    break;
                                case wifiPwUUID:
                                    wifiPwCharacteristic = characteristic;
                                    break;
                                case wifiStateUUID:
                                    wifiStateCharacteristic = characteristic;
                                    break;
                            }
                        }
                    }
                }

                if (btPaired)
                    initCommunication();
            } else {
                Log.e("BLE", "onServicesDiscovered status " + status);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                parseBtData(characteristic.getUuid().toString(), characteristic.getValue(), false);
            } else if (status == BluetoothGatt.GATT_FAILURE) {
                Log.e("BLE", "onCharacteristicRead status GATT_FAILURE");
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                Log.e("BLE", "onCharacteristicRead status INSUFFICIENT_ENCRYPTION");
            } else {
                Log.e("BLE", "onCharacteristicRead status " + status);
            }
            gattInProgress = false;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                parseBtData(characteristic.getUuid().toString(), characteristic.getValue(), false);
            } else if (status == BluetoothGatt.GATT_FAILURE) {
                Log.e("BLE", "onCharacteristicRead status GATT_FAILURE");
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                Log.e("BLE", "onCharacteristicRead status INSUFFICIENT_ENCRYPTION");
            } else {
                Log.e("BLE", "onCharacteristicRead status " + status);
            }
            gattInProgress = false;
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            gattInProgress = false;
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            parseBtData(characteristic.getUuid().toString(), characteristic.getValue(), true);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            gattInProgress = false;
            if (status != BluetoothGatt.GATT_SUCCESS)
                Log.e("BLE", "onDescriptorWrite status " + status);
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
            gattInProgress = false;
        }
    };

    private void initCommunication() {
        if (!communicationInitiated &&
                commandCharacteristic != null &&
                commandRespCharacteristic != null &&
                settingsCharacteristic != null &&
                settingsRespCharacteristic != null &&
                queryCharacteristic != null &&
                queryRespCharacteristic != null &&
                modelNoCharacteristic != null &&
                wifiSsidCharacteristic != null &&
                wifiPwCharacteristic != null &&
                wifiStateCharacteristic != null) {

            communicationInitiated = true;

            gattInProgress = false;
            execBtActionLoop = getNewExecThread();
            execBtActionLoop.start();

            startCrcNotification();
            getHardwareInfo();
            readWifiApPw();
            startSrcNotification();
            startQrcNotification();
            registerForAutoValueUpdates();
        }
    }

    private void parseBtData(String characteristicUuid, byte[] vlaueBytes, boolean isCustomUuid) {
        if (isCustomUuid) { // onCharacteristicChanged
            boolean isContPack = (vlaueBytes[0] & 128) > 0;

            if (isContPack) {
                if (contPackBuffer != null) {
                    try {
                        contPackBuffer.put(vlaueBytes, 1, vlaueBytes.length - 1);
                    } catch (Exception e) {
                        Log.e("parseBtData", "Error: " + e);
                    }

                    if (contPackBuffer.remaining() <= 0) {
                        //all datas received
                        parseContResponsePack();
                        gattInProgress = false;
                    }
                } else {
                    gattInProgress = false;
                }
            } else {
                int headerLength = getHeaderLength(vlaueBytes); // 1-2 bytes
                int commandId = vlaueBytes[headerLength];
                int error = vlaueBytes[headerLength + 1];
                int msgLen = vlaueBytes[headerLength - 1];

                contPackBuffer = ByteBuffer.allocate(msgLen);
                contPackBuffer.put(vlaueBytes, headerLength, vlaueBytes.length - headerLength);

                if(error == 0) {
                    switch (characteristicUuid) {
                        case commandRespUUID:
                        /*switch (error) {
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
                        }*/

                            break;
                        case settingsRespUUID:
                            //if (bytes[3] == 91) { // Keep alive
                            // do nothing with keep alive response
                            //}
                            break;
                        case queryRespUUID:
                            int id1 = vlaueBytes[3];
                            int len1 = vlaueBytes[4];

                            if (commandId == (byte) 0x13 && id1 == 54 && len1 == 8) { // Remaining space
                                ByteBuffer buffer = ByteBuffer.allocate(len1);
                                buffer.put(vlaueBytes, 5, len1);
                                buffer.flip();
                                long memBytes = buffer.getLong();

                                Memory = String.format("%.2f", ((memBytes / 1024.00) / 1024.00)) + " GB";
                            } else if (commandId == (byte) 0x13 && id1 == 70 /*&& len1 == 1*/) { // Battery level
                                BatteryPercent = vlaueBytes[5];
                            } else if (commandId == (byte) 0x13 && id1 == 43 && len1 == 1) { // Active preset
                                Preset = modes.getOrDefault((int) vlaueBytes[5], "unknown mode ID " + (int) vlaueBytes[5]);
                            } else if (commandId == (byte) 0x13 && id1 == 97 && len1 == 4) { // Active preset
                                ByteBuffer buffer = ByteBuffer.allocate(len1);
                                buffer.put(vlaueBytes, 5, len1);
                                buffer.flip();
                                int presetID = buffer.getInt();

                                Preset = presets.getOrDefault(presetID, "unknown preset ID " + presetID);
                            } else if (commandId == (byte) 0x53 && error == 0) { // response for registerForAutoValueUpdates()
                                autoValueUpdatesRegistered = true;
                                Log.d("NOTIFIED", "autoValueUpdatesRegistered = true");
                            } else if (commandId == (byte) 0x93 && error == 0) { // Multi-Value query response
                                // format: [MESSAGE LENGTH]:[QUERY ID]:[COMMAND STATUS]:[ID1]:[LENGTH1]:[VALUE1]:[ID2]:[LENGTH2]:[VALUE2]:...
                                ByteBuffer buffer = ByteBuffer.allocate(len1);
                                buffer.put(vlaueBytes, 5, len1);

                                handleStatusData(id1, buffer);

                                if (vlaueBytes[0] > 5 + len1) {
                                    int offset1 = 5 + len1;
                                    //multi value response
                                    int id2 = vlaueBytes[offset1];
                                    int len2 = vlaueBytes[offset1 + 1];
                                    if (id2 == 54 || id2 == 69 || id2 == 70 || id2 == 97) {
                                        Log.e("NOTIFIED", "Unhandled multi-value response in automatic update with command ID=" + id2 + ", length=" + len2);
                                    }
                                }
                            }
                            break;
                    }
                } else {
                    Log.e("GoPro response", "Error: " + error);
                }

                gattInProgress = false;
            }
        } else { // onCharacteristicRead
            switch (characteristicUuid) {
                case modelNoUUID:
                    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
                    byteBuffer.put(vlaueBytes);
                    String decoded = new String(byteBuffer.array(), StandardCharsets.UTF_8);
                    modelID = Integer.parseInt(decoded, 16);
                    handleModelId();
                    break;
                case wifiSsidUUID:
                    ByteBuffer byteBuffer2 = ByteBuffer.allocate(33);
                    byteBuffer2.put(vlaueBytes);
                    wifiSsid = new String(byteBuffer2.array(), StandardCharsets.UTF_8).trim();
                    break;
                case wifiPwUUID:
                    ByteBuffer byteBuffer3 = ByteBuffer.allocate(64);
                    byteBuffer3.put(vlaueBytes);
                    wifiPw = new String(byteBuffer3.array(), StandardCharsets.UTF_8).trim();
                    break;
                case wifiStateUUID:
                    wifiApState = (int) vlaueBytes[0];
                    break;
                //endregion
            }
            gattInProgress = false;
        }

        doIfBtConnected();

        if (dataChangedCallback != null)
            dataChangedCallback.onDataChanged();

        if (modelID > UNK_MODEL && freshPaired) {
            sendBtPairComplete();
        }
    }

    @SuppressLint("MissingPermission")
    private void sendBtPairComplete() {
        btActionBuffer.add(() -> {
            //send to GP-0091: {msg_len, 0x03, 0x01, 0x08, 0x00, 0x12, dev_name_len, dev_name}
            byte[] msg = {0x0f, 0x03, 0x01, 0x08, 0x00, 0x12, 0x09, 'G', 'o', 'E', 'a', 's', 'y', 'P', 'r', 'o'};
            if (nwManCmdCharacteristic != null && nwManCmdCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(nwManCmdCharacteristic)) {
                freshPaired = false;
            } else {
                Log.e("sendBtPairComplete1","not sent");
                gattInProgress = false;
            }
        });
    }

    private void handleModelId() {
        if (modelID >= HERO9_BLACK) {
            // Hero 9+
            keepAlive_msg = keepAlive_v2;
            startStream_query = streamStart_query_v3;
            stopStream_query = streamStop_query_v2; //TODO test
            getMediaList_query = mediaList_query_v2; //TODO test
            getThumbNail_query = thumNail_query_v2;
        } else if (modelID >= HERO8_BLACK) {
            // Hero 8 TODO and Max ?
            keepAlive_msg = keepAlive_v2;
            startStream_query = streamStart_query_v2;
            stopStream_query = streamStop_query_v1;
            getMediaList_query = mediaList_query_v1;
            getThumbNail_query = thumNail_query_v1;
        } else {
            // -Hero 7
            keepAlive_msg = keepAlive_v1; //TODO test
            startStream_query = streamStart_query_v1; //TODO test
            stopStream_query = streamStop_query_v1; //TODO test
            getMediaList_query = mediaList_query_v1;
            getThumbNail_query = thumNail_query_v1;
        }

        if (modelID >= HERO8_BLACK) { //TODO From which model does the query command "0x13 - Get all status values" work?
            getStatusValues();
        } else {
            readWifiApState();
            getMemory();
            getBatteryLevel();
            getPreset();
        }
    }

    private void parseContResponsePack() {
        byte[] byteArray = contPackBuffer.array();
        contPackBuffer.clear();
        contPackBuffer = null;
        int commandId = byteArray[0];
        int error = byteArray[1];
        int nextStart = 2;

        if (error == 0) {
            if (commandId == 0x3c /* 60 */) { // Hardware info -> Model ID, model name, board type, firmware version, serial number, AP SSID, AP MAC Address
                int nextLen = byteArray[nextStart];

                // Model ID
                ByteBuffer byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                byteBuffer.flip();
                modelID = byteBuffer.getInt();
                handleModelId();

                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // model name
                byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                modelName = new String(byteBuffer.array(), StandardCharsets.UTF_8).trim();

                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // board type
                // skip
                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // firmware
                // skip
                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // serial number
                // skip
                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // AP SSID
                byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                wifiSsid = new String(byteBuffer.array(), StandardCharsets.UTF_8).trim();

                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // AP MAC Address
                byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                StringBuilder sb = new StringBuilder(new String(byteBuffer.array(), StandardCharsets.UTF_8).trim());
                sb.insert(2, ':');
                sb.insert(5, ':');
                sb.insert(8, ':');
                sb.insert(11, ':');
                sb.insert(14, ':');
                apMacAddr = sb.toString();
            } else if (commandId == 0x13 /* 19 */) { // All status values
                int nextLen = 0;

                for (int index = nextStart; index < byteArray.length; ) {
                    int statusID = byteArray[index];
                    nextLen = byteArray[index + 1];

                    if(nextLen > 0) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(nextLen);
                        byteBuffer.put(byteArray, index + 2, nextLen);
                        handleStatusData(statusID, byteBuffer);
                    }

                    index += nextLen + 2;
                }
            }
        }
    }

    private void handleStatusData(int statusID, ByteBuffer buffer) {
        switch (statusID) {
            case 43:
                int presetId = buffer.get(0);
                Preset = modes.getOrDefault(presetId, "unknown mode ID " + presetId);
                break;
            case 54: // Remaining space on the sdcard in Kilobytes as integer
                buffer.flip();
                long memBytes = buffer.getLong();
                Memory = String.format("%.2f", ((memBytes / 1024.00) / 1024.00)) + " GB";
                break;
            case 69: // AP state as boolean
                wifiApState = (int) buffer.get(0);
                break;
            case 70: // Internal battery percentage as percent
                BatteryPercent = buffer.get(0);
                break;
            case 97: // Current Preset ID as integer
                buffer.flip();
                int presetID = buffer.getInt();
                Preset = presets.getOrDefault(presetID, "unknown preset ID " + presetID);
                break;
        }
    }

    private void doIfBtConnected() {
        if (btConnectionStage != BT_CONNECTED && modelID > 0 && !Objects.equals(wifiPw, "") && !Objects.equals(wifiSsid, "")) {
            btConnectionStage = BT_CONNECTED;

            //start timed actions
            initTimedActions();
            initExecWatchdog();

            if (connectBtCallback != null)
                connectBtCallback.onBtConnectionStateChange(true);
            if (dataChangedCallback != null)
                dataChangedCallback.onDataChanged();
        }
    }

    //region BT Communication tools
    @SuppressLint("MissingPermission")
    public void disconnectBt() {
        Log.e("disconnectBt", "Disconnect forced");
        if (bluetoothGatt != null) {
            if (commandRespCharacteristic != null)
                bluetoothGatt.setCharacteristicNotification(commandRespCharacteristic, false);

            if (settingsRespCharacteristic != null)
                bluetoothGatt.setCharacteristicNotification(settingsRespCharacteristic, false);

            if (queryRespCharacteristic != null)
                bluetoothGatt.setCharacteristicNotification(queryRespCharacteristic, false);

            bluetoothGatt.disconnect();
        }

        commandCharacteristic = null;
        commandRespCharacteristic = null;
        settingsCharacteristic = null;
        settingsRespCharacteristic = null;
        queryCharacteristic = null;
        queryRespCharacteristic = null;
        modelNoCharacteristic = null;
        wifiSsidCharacteristic = null;
        wifiPwCharacteristic = null;
        wifiStateCharacteristic = null;

        communicationInitiated = false;

        setDisconnected();
    }

    private void setDisconnected() {
        try {
            if (execBtActionLoop != null)
                execBtActionLoop.interrupt();
        } catch (Exception e) {
            if (execBtActionLoop != null && !execBtActionLoop.isInterrupted())
                try {
                    execBtActionLoop.interrupt();
                } catch (Exception e2) {
                    Log.e("setDisconnected", "execBtActionLoop.interrupt() failed");
                }
        }
        actionTimer.cancel();
        actionTimer.purge();

        execWatchdogTimer.cancel();
        execWatchdogTimer.purge();

        btActionBuffer.clear();

        btConnectionStage = BT_NOT_CONNECTED;
        autoValueUpdatesRegistered = false;
        registerForAutoValueUpdatesCounter = 0;

        wifiApState = -1;
        isWifiConnected = false;


        gattInProgress = false;

        if (connectBtCallback != null)
            connectBtCallback.onBtConnectionStateChange(false);
        if (dataChangedCallback != null)
            dataChangedCallback.onDataChanged();
    }

    @SuppressLint("MissingPermission")
    private void startCrcNotification() {
        btActionBuffer.add(() -> {
            if (commandRespCharacteristic == null || bluetoothGatt == null) {
                gattInProgress = false;
                return;
            }

            if (bluetoothGatt.setCharacteristicNotification(commandRespCharacteristic, true)) {
                BluetoothGattDescriptor descriptor = commandRespCharacteristic.getDescriptors().get(0);
                if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Log.e("startCrcNotification", "Error 3");
                        showToast("There is a problem connecting to the camera " + name + ". Please try to connect again.\nIf the problem persists, please restart the camera.", Toast.LENGTH_SHORT);
                        disconnectBt();
                        gattInProgress = false;
                    }
                } else {
                    Log.e("startCrcNotification", "Error 2");
                    gattInProgress = false;
                }
            } else {
                Log.e("startCrcNotification", "Error 1");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startSrcNotification() {
        btActionBuffer.add(() -> {
            if (settingsRespCharacteristic == null || bluetoothGatt == null) {
                gattInProgress = false;
                return;
            }

            if (bluetoothGatt.setCharacteristicNotification(settingsRespCharacteristic, true)) {
                BluetoothGattDescriptor descriptor = settingsRespCharacteristic.getDescriptors().get(0);
                if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Log.e("startSrcNotification", "Error 3");
                        showToast("There is a problem connecting to the camera " + name + ". Please try to connect again.\nIf the problem persists, please restart the camera.", Toast.LENGTH_SHORT);
                        disconnectBt();
                        gattInProgress = false;
                    }
                } else {
                    Log.e("startSrcNotification", "Error 2");
                    gattInProgress = false;
                }
            } else {
                Log.e("startSrcNotification", "Error 1");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startQrcNotification() {
        btActionBuffer.add(() -> {
            if (queryRespCharacteristic == null || bluetoothGatt == null) {
                gattInProgress = false;
                return;
            }

            if (bluetoothGatt.setCharacteristicNotification(queryRespCharacteristic, true)) {
                BluetoothGattDescriptor descriptor = queryRespCharacteristic.getDescriptors().get(0);
                if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Log.e("startQrcNotification", "Error 3");
                        showToast("There is a problem connecting to the camera " + name + ". Please try to connect again.\nIf the problem persists, please restart the camera.", Toast.LENGTH_SHORT);
                        disconnectBt();
                        gattInProgress = false;
                    }
                } else {
                    Log.e("startQrcNotification", "Error 2");
                    gattInProgress = false;
                }
            } else {
                Log.e("startQrcNotification", "Error 1");
                gattInProgress = false;
            }
        });
    }
    //endregion

    //region BT Readings
    @SuppressLint("MissingPermission")
    private void readModelID() {
        btActionBuffer.add(() -> {
            if (bluetoothGatt == null || modelNoCharacteristic == null || btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            if (!bluetoothGatt.readCharacteristic(modelNoCharacteristic)) {
                Log.e("readModelID", "failed action");
                gattInProgress = false;
            }
        });
    }

    /*@SuppressLint("MissingPermission")
    private void readBattLevel() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (bluetoothGatt == null || battLevelCharacteristic == null || btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            if (bluetoothGatt.readCharacteristic(battLevelCharacteristic)) {
                lastBatteryRead = new Date();
            } else {
                Log.e("readBattLevel", "failed action");
                gattInProgress = false;
            }
        });
    }*/

    @SuppressLint("MissingPermission")
    private void readWifiApSsid() {
        btActionBuffer.add(() -> {
            if (bluetoothGatt == null || wifiSsidCharacteristic == null || btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiSsidCharacteristic)) {
                Log.e("readWifiApSsid", "failed action");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void readWifiApPw() {
        btActionBuffer.add(() -> {
            if (bluetoothGatt == null || wifiPwCharacteristic == null || btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiPwCharacteristic)) {
                Log.e("readWifiApPw", "failed action");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void readWifiApState() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (bluetoothGatt == null || wifiStateCharacteristic == null || btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiStateCharacteristic)) {
                Log.e("readWifiApState", "failed action");
                gattInProgress = false;
            }
        });
    }

    private void readBtRssi() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE || btActionBuffer.contains(readBtRssi)) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(readBtRssi);
    }

    private final Runnable readBtRssi = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            if (bluetoothGatt == null || btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            if (!bluetoothGatt.readRemoteRssi()) {
                gattInProgress = false;
            }
        }
    };
    //endregion

    //region BT Settings (0x0074)
    @SuppressLint("MissingPermission")
    private void keepAlive() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE || btActionBuffer.contains(keepAlive)) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(keepAlive);
    }

    private final Runnable keepAlive = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            if (modelID > HERO8_BLACK) {
                byte[] msg = {0x03, 0x5B, 0x01, 0x42};
                if (settingsCharacteristic == null || !settingsCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(settingsCharacteristic)) {
                    gattInProgress = false;
                } else {
                    lastKeepAlive = new Date();
                }
            } else {
                // TODO another way to keep alive for hero 8 and downwards?
                // Even the GoPro app writes 03 5b 01 42 as Keep Alive and gets an error back from the GoPro
                gattInProgress = false;
            }
        }
    };

    @SuppressLint("MissingPermission")
    public void setDateTime() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            Date now = new Date();
            byte yyH = (byte) (now.getYear() & 0xFF);
            byte yyL = (byte) ((now.getYear() >> 8) & 0xFF);
            // Set date/time to 2022-01-02 03:04:05 	Command: 09: 0D: 07: 07:E6: 01: 02: 03: 04: 05
            byte[] msg = {0x09, 0x0d, 0x07, yyL, yyH, (byte) (now.getMonth() + 1), (byte) now.getDate(), (byte) now.getHours(), (byte) now.getMinutes(), (byte) now.getSeconds()};
            if (settingsCharacteristic == null || !settingsCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(settingsCharacteristic)) {
                gattInProgress = false;
            }
        });

    }
    //endregion

    //region BT Commands (0x0072)
    @SuppressLint("MissingPermission")
    public void getHardwareInfo() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] msg = {0x01, 0x3C};
            if (commandCharacteristic != null && commandCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void shutterOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] shutterOn = {0x03, 0x01, 0x01, 0x01};
            if (commandCharacteristic != null && commandCharacteristic.setValue(shutterOn) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void shutterOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] shutterOff = {0x03, 0x01, 0x01, 0x00};
            if (commandCharacteristic != null && commandCharacteristic.setValue(shutterOff) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void sleep() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] camSleep = {0x01, 0x05};
            if (commandCharacteristic != null && commandCharacteristic.setValue(camSleep) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                bluetoothGatt.disconnect();
                wifiApState = -1;
                isWifiConnected = false;
            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void wifiApOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            byte[] wifiApOn = {0x03, 0x17, 0x01, 0x01};
            if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOn) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (!autoValueUpdatesRegistered)
                    readWifiApState();
            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void wifiApOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }
            byte[] wifiApOff = {0x03, 0x17, 0x01, 0x00};
            if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOff) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (!autoValueUpdatesRegistered)
                    readWifiApState();
            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void locateOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] msg = {0x03, 0x16, 0x01, 0x01};
            if (commandCharacteristic != null && commandCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void locateOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] msg = {0x03, 0x16, 0x01, 0x00};
            if (commandCharacteristic != null && commandCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    public void highlight() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] highlight = {0x01, 0x18};
            if (commandCharacteristic != null && commandCharacteristic.setValue(highlight) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

            } else {
                gattInProgress = false;
            }
        });
    }
    //endregion

    //region BT Queries (0x0076)
    @SuppressLint("MissingPermission")
    private void registerForAutoValueUpdates() {
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] msg = new byte[2 + statusIDs.length];
            msg[0] = (byte) (statusIDs.length + 1);
            msg[1] = 0x53;
            System.arraycopy(statusIDs, 0, msg, 2, statusIDs.length);
            //byte[] queryStatusUpdates = {0x05, 0x53, 54, 69, 70, 97}; //54 (Remaining space on the sdcard in Kilobytes as integer), 69 AP state as boolean, 70 (Internal battery percentage as percent), 97 (Current Preset ID as integer)

            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
            } else {
                Log.e("registerForAutoValueUpdates", "failed action");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getStatusValues() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] msg = new byte[2 + statusIDs.length];
            msg[0] = (byte) (statusIDs.length + 1);
            msg[1] = 0x13;
            System.arraycopy(statusIDs, 0, msg, 2, statusIDs.length);

            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                Log.e("getAllStatusValues", "failed action");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getMemory() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] msg = {0x02, 0x13, 0x36};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                Log.e("getMemory", "failed action");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getBatteryLevel() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            byte[] msg = {0x02, 0x13, 0x46};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                Log.e("getBatteryLevel", "failed action");
                gattInProgress = false;
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getPreset() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        btActionBuffer.add(() -> {
            if (btConnectionStage < 2) {
                gattInProgress = false;
                return;
            }

            if (modelID >= HERO8_BLACK) {
                byte[] msg = {0x02, 0x13, 0x61};
                if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                    lastOtherQueries = new Date();
                } else {
                    Log.e("getPreset", "failed action");
                    gattInProgress = false;
                }
            } else {
                byte[] msg = {0x02, 0x13, 43};
                if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                    lastOtherQueries = new Date();
                } else {
                    Log.e("getPreset", "failed action");
                    gattInProgress = false;
                }
            }
        });
    }
    //endregion

    //region Callback Interfaces
    interface PairCallbackInterface {
        void onPairingFinished(boolean paired);
    }

    private PairCallbackInterface pairCallback;

    public void pair(PairCallbackInterface _pairCallback) {
        pairCallback = _pairCallback;

        try {
            Method m = bluetoothDevice.getClass().getMethod("createBond", (Class[]) null);
            boolean willBegin = (boolean) m.invoke(bluetoothDevice, (Object[]) null);

            if (willBegin) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

                pairingReceiver = new BroadcastReceiver() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            int state = device.getBondState();

                            if (state == BluetoothDevice.BOND_BONDED) {
                                freshPaired = true;
                                btPaired = true;
                                connectBt(null);

                                if (pairCallback != null)
                                    pairCallback.onPairingFinished(true);

                                try {
                                    context.unregisterReceiver(pairingReceiver);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                };
                context.registerReceiver(pairingReceiver, filter);

            } else {
                showToast("Pairing with " + name + " failed!", Toast.LENGTH_SHORT);
            }
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
        if (bluetoothGatt != null) {
            btConnectionStage = BT_CONNECTING;

            // Timeout connecting
            Timer checkConnected = new Timer();
            checkConnected.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (btConnectionStage != BT_CONNECTED) {
                        Log.e("connectBt", "BT connection timeout");
                        disconnectBt();
                    }
                }
            }, 30000);
        }
    }

    interface DataChangedCallbackInterface {
        void onDataChanged();
    }

    private DataChangedCallbackInterface dataChangedCallback;

    public void getDataChanges(DataChangedCallbackInterface _dataChangedCallback) {
        dataChangedCallback = _dataChangedCallback;
    }


    interface WifiConnectedCallbackInterface {
        void onWifiConnected();
    }

    private WifiConnectedCallbackInterface wifiConnectedCallback;

    public void connectWifi(WifiConnectedCallbackInterface _wifiConnectedCallback) {
        wifiConnectedCallback = _wifiConnectedCallback;
        if (wifiApState != 1)
            wifiApOn();

        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            connectWifi();
        }).start();
    }
    //endregion

    //region Wifi connection
    @SuppressLint("MissingPermission")
    private void connectWifi() {
        // Check if bt connected and SSID and PSK are known
        if (btConnectionStage < 2) {
            showToast("Not connected! Please first connect!", Toast.LENGTH_SHORT);
            return;
        } else if (Objects.equals(wifiSsid, "")) {
            readWifiApSsid();
            showToast("SSID is unknown. I retrieve it... Please try again!", Toast.LENGTH_SHORT);
            return;
        } else if (Objects.equals(wifiPw, "")) {
            readWifiApPw();
            showToast("PSK is unknown. I retrieve it... Please try again!", Toast.LENGTH_SHORT);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiAndroidQup();
        } else {
            new Thread(() -> {
                if (!connectWifiAndroidPdown())
                    showToast("Something went wrong!", Toast.LENGTH_SHORT);
                else {
                    if (wifiConnectedCallback != null)
                        wifiConnectedCallback.onWifiConnected();
                }
            }).start();
        }

    }

    @SuppressLint("MissingPermission")
    private boolean connectWifiAndroidPdown() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        boolean mustConnect = false;
        // Check if this network is already known
        WifiConfiguration wifiConfiguration = null;
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks(); // deprecated in API level 29 (Android 10/Q)
        for (WifiConfiguration _wifiConfiguration : list) {
            if (_wifiConfiguration.SSID != null && _wifiConfiguration.SSID.equals('"' + wifiSsid + '"')) {
                wifiConfiguration = _wifiConfiguration;
                break;
            }
        }

        if (wifiConfiguration == null) {
            // create WifiConfiguration instance
            WifiConfiguration newWifiConfiguration = new WifiConfiguration(); // deprecated in API level 29 (Android 10/Q)
            newWifiConfiguration.SSID = '"' + wifiSsid + '"';
            newWifiConfiguration.preSharedKey = '"' + wifiPw + '"';

            //Add it to Android wifi manager settings
            wifiManager.addNetwork(newWifiConfiguration);

            mustConnect = true;
        } else {
            // Check if already connected
            int connectedId = wifiManager.getConnectionInfo().getNetworkId(); // getConnectionInfo() is deprecated in API level 31 (Android 12/S)

            // If not already connected, connect it
            if (connectedId != wifiConfiguration.networkId) {
                mustConnect = true;
            }
        }
        if (mustConnect) {
            // Connect to it
            wifiManager.disconnect();
            wifiManager.enableNetwork(wifiConfiguration.networkId, true);
            return wifiManager.reconnect();
        } else {
            return true;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void connectWifiAndroidQup() {
        if (isWifiConnected) {
            if (wifiConnectedCallback != null)
                new Thread(() -> wifiConnectedCallback.onWifiConnected()).start();
            return;
        }

        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null || Objects.equals(apMacAddr, ""))
            return;

        final WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                .setSsid(wifiSsid)
                .setBssid(MacAddress.fromString(apMacAddr))
                .setWpa2Passphrase(wifiPw)
                .build();

        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build();

        final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                //client = new OkHttpClient();
                LinkProperties prop = connectivityManager.getLinkProperties(network);
                List<LinkAddress> linkAddresses = prop.getLinkAddresses();
                if (linkAddresses.size() > 1) {
                    myClientIP = linkAddresses.get(1).getAddress().toString().substring(1);
                } else {
                    myClientIP = linkAddresses.get(0).getAddress().toString().substring(1);
                }
                connectivityManager.bindProcessToNetwork(network);
                isWifiConnected = true;
                if (wifiConnectedCallback != null)
                    wifiConnectedCallback.onWifiConnected();
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                isWifiConnected = false;
                showToast("The cameras AP is not available!", Toast.LENGTH_SHORT);
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                isWifiConnected = false;
                showToast("WiFi-AP connection lost!", Toast.LENGTH_SHORT);
            }
        };

        HandlerThread handlerThread = new HandlerThread("WiFi Manager Network Update Handler");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        freshPaired = false;
        connectivityManager.requestNetwork(networkRequest, networkCallback, handler);
    }

    public boolean removeBtBond() {
        disconnectBt();

        try {
            Method m = null;
            m = bluetoothDevice.getClass().getMethod("removeBond", (Class[]) null);
            if ((boolean) m.invoke(bluetoothDevice, (Object[]) null)) {
                btPaired = false;
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    //endregion

    //region Helpers
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

    private void initExecWatchdog() {
        execWatchdogTimer.cancel();
        execWatchdogTimer.purge();

        TimerTask timedExecWatchdog = new TimerTask() {
            @Override
            public void run() {
                if (btConnectionStage < 2) {
                    return;
                }

                if (new Date().getTime() - gattProgressStart.getTime() >= 10000 && gattInProgress) {
                    Log.e("ExecWatchdog", "'Gatt in progress' timeout triggered!");
                    gattInProgress = false;
                }
            }
        };

        execWatchdogTimer = new Timer();
        execWatchdogTimer.schedule(timedExecWatchdog, 0, 1000);
    }

    private void initTimedActions() {
        actionTimer.cancel();
        actionTimer.purge();

        lastKeepAlive = new Date();
        lastMemoryQuery = new Date();
        lastOtherQueries = new Date();
        lastBatteryRead = new Date();

        TimerTask timedActions = new TimerTask() {
            @Override
            public void run() {
                if (btConnectionStage < BT_CONNECTED)
                    return;

                long now = new Date().getTime();

                // Send "keep alive" every 60 seconds; only for models newer then hero 8
                if (now - lastKeepAlive.getTime() > 60000) {
                    keepAlive();
                    lastKeepAlive = new Date();
                }

                // Query "Remaining space"
                if ((!autoValueUpdatesRegistered || Objects.equals(Memory, "NC")) && now - lastMemoryQuery.getTime() > 7000) {
                    getMemory();
                    lastMemoryQuery = new Date();
                }

                // Some other queries
                if (now - lastOtherQueries.getTime() > 3000) {
                    if (!autoValueUpdatesRegistered) {
                            getPreset();
                    } else {
                        if (Objects.equals(Preset, "NC"))
                            getPreset();
                    }

                    if (Objects.equals(wifiPw, "")) {
                        readWifiApPw();
                    }

                    if (!autoValueUpdatesRegistered && registerForAutoValueUpdatesCounter < 4) {
                        registerForAutoValueUpdatesCounter++;
                        registerForAutoValueUpdates();
                    } else if (!autoValueUpdatesRegistered) {
                        showToast("There is a problem connecting to the camera " + name + ". Please try to connect again.\nIf the problem persists, please restart the camera.", Toast.LENGTH_SHORT);

                        Log.e("registerForAutoValueUpdates", "max tries reached");
                        disconnectBt();
                    }

                    readBtRssi();

                    lastOtherQueries = new Date();
                }

                // Read "Battery level"
                if ((!autoValueUpdatesRegistered || BatteryPercent == 0) && now - lastBatteryRead.getTime() > 10000) {
                    getBatteryLevel();
                    lastBatteryRead = new Date();
                }

            }
        };

        actionTimer = new Timer();
        actionTimer.schedule(timedActions, 0, 250);
    }

    private Thread getNewExecThread() {
        return new Thread(() -> {
            while (true) {
                if (btConnectionStage == BT_NOT_CONNECTED) {
                    break;
                }

                // Wait until gatt is no longer in progress
                while (gattInProgress) {
                    if (btConnectionStage == BT_NOT_CONNECTED) {
                        break;
                    }
                }

                // Wait for a new runnable to become available
                while (btActionBuffer.size() < 1) {
                    if (btConnectionStage == BT_NOT_CONNECTED) {
                        break;
                    }
                }

                if (btConnectionStage == BT_NOT_CONNECTED) {
                    break;
                }

                // Get a non-null runnable
                Runnable runnable = null;
                try {
                    while (runnable == null) {
                        runnable = btActionBuffer.get(0);
                    }
                    btActionBuffer.remove(0); // Remove the runnable from btActionBuffer

                    gattInProgress = true; // Set gattInProgress flag
                    gattProgressStart = new Date(); // Store time since Gatt is in progress for the watchdog
                    runnable.run(); // Run the runnable
                    Thread.sleep(btActionDelay);
                } catch (Exception e) {
                    Log.e("execNextBtAction", "Exception caught: " + e);
                }
            }
        });
    }

    private void showToast(String text, int duration) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context, text, duration).show();
            } catch (Exception ignore) {

            }
        });
    }

    //endregion
}
