package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.MyApplication.getReadableFileSize;

import android.annotation.SuppressLint;
import android.app.Application;
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
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

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

    private static final Map<String, byte[]> presetCmds = new HashMap<String, byte[]>();

    //region model depending strings
    private final static String streamStart_query_v1 = "http://10.5.5.9/gp/gpControl/execute?p1=gpStream&a1=proto_v2&c1=restart"; // -Hero 7 TODO ?
    private final static String streamStart_query_v2 = "http://10.5.5.9/gp/gpControl/execute?p1=gpStream&c1=restart"; // Hero 8 TODO and Max ?
    private final static String streamStart_query_v3 = "http://10.5.5.9:8080/gopro/camera/stream/start"; // Hero 9+

    private final static String streamStop_query_v1 = "http://10.5.5.9/gp/gpControl/execute?p1=gpStream&c1=stop";
    private final static String streamStop_query_v2 = "http://10.5.5.9:8080/gopro/camera/stream/stop";

    private final static String mediaList_query_v1 = "http://10.5.5.9/gp/gpMediaList"; // -Hero 8 TODO and Max ?
    private final static String mediaList_query_v2 = "http://10.5.5.9:8080/gopro/media/list"; // TODO Hero 9+ ? //On Hero 10 - Firmware 1.20 it doesn't work -> updated to 1.50 and it works

    private final static String thumbNail_query_v1 = "http://10.5.5.9/gp/gpMediaMetadata?p="; // -Hero 8 TODO and Max ?
    private final static String thumbNail_query_v2 = "http://10.5.5.9:8080/gopro/media/thumbnail?path="; // TODO Hero 9+ ?

    private final static String keepAlive_v1 = "_GPHD_:0:0:2:0.000000\n"; // -Hero 7 TODO ?
    private final static String keepAlive_v2 = "_GPHD_:1:0:2:0.000000\n"; // Hero 8+
    //endregion

    //TODO check if firmware update available "https://api.gopro.com/firmware/v2/catalog"

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

    private final Context _context;
    private final Resources res;

    private final SharedPreferences sharedPreferences;

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
    public String name;
    public String displayName;
    public String btMacAddress = "";
    public String modelName;
    public int modelID = UNK_MODEL;
    public String boardType = "";
    public String firmware = "";
    public String serialNumber = "";

    public GoPreset preset;
    public GoMode mode;

    public int remainingBatteryPercent = 0;
    public String remainingMemory;
    private boolean autoValueUpdatesRegistered = false;
    public int btRssi = 0;
    private String LE = "None"; // last error
    private Date lastKeepAlive = new Date();
    private Date lastOtherQueries = new Date();
    private Date lastMemoryQuery = new Date();
    private Date lastBatteryRead = new Date();
    private Date lastSettingUpdate = new Date();
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
    private int registerForAutoValueUpdatesCounter = 0;
    private ByteBuffer packBuffer;
    private static final int btActionDelay = 125;
    private boolean communicationInitiated = false;
    public boolean btIsAvailable = false;
    public boolean isBusy = false;
    public boolean isRecording = false;
    public boolean isCharging = false;
    public boolean isHot = false;
    public boolean isCold = false;
    private boolean freshPaired = false;
    public JSONObject settingsValues;
    public boolean providesAvailableOptions = true;
    private final Application _application;
    public ArrayList<GoSetting> goSettings = new ArrayList<>();
    private ArrayList<Pair<Integer, Integer>> availableSettingsOptions = null;
    private boolean autoOffDisableAsked = false;

    //Wifi
    public static final String goProIp = "10.5.5.9";
    public Integer wifiApState = -1;
    public boolean isWifiConnected = false;
    public String wifiSSID = "";
    public String wifiPSK = "";
    public String wifiBSSID = "";
    private ConnectivityManager connectivityManager = null;
    public String startStream_query = "";
    public String stopStream_query = "";
    public String getMediaList_query = "";
    public String getThumbNail_query = "";
    public String keepAlive_msg = "";
    public String myClientIP;
    private BroadcastReceiver pairingReceiver;

    public GoProDevice(Context context, Application application, String deviceName) {
        _application = application;
        settingsValues = ((MyApplication) _application).getSettingsValues();
        _context = context;
        res = _context.getResources();
        name = deviceName;
        sharedPreferences = _context.getSharedPreferences("GoProDevices", Context.MODE_PRIVATE);
        displayName = sharedPreferences.getString("display_name_" + name, name);
        modelName = res.getString(R.string.str_NC);
        remainingMemory = res.getString(R.string.str_NC);
        modelName = sharedPreferences.getString("model_name_" + name, modelName);
        preset = new GoPreset(_context);
        mode = new GoMode(_context);

        presetCmds.put("standard", new byte[]{0x06, 0x40, 0x04, 0x00, 0x00, 0x00, 0x00});
        presetCmds.put("activity", new byte[]{0x06, 0x40, 0x04, 0x00, 0x00, 0x00, 0x01});
        presetCmds.put("cinematic", new byte[]{0x06, 0x40, 0x04, 0x00, 0x00, 0x00, 0x02});
        presetCmds.put("photo", new byte[]{0x06, 0x40, 0x04, 0x00, 0x01, 0x00, 0x00});
        presetCmds.put("liveBurst", new byte[]{0x06, 0x40, 0x04, 0x00, 0x01, 0x00, 0x01});
        presetCmds.put("burstPhoto", new byte[]{0x06, 0x40, 0x04, 0x00, 0x01, 0x00, 0x02});
        presetCmds.put("nightPhoto", new byte[]{0x06, 0x40, 0x04, 0x00, 0x01, 0x00, 0x03});
        presetCmds.put("timeWarp", new byte[]{0x06, 0x40, 0x04, 0x00, 0x02, 0x00, 0x00});
        presetCmds.put("timeLapse", new byte[]{0x06, 0x40, 0x04, 0x00, 0x02, 0x00, 0x01});
        presetCmds.put("nightLapse", new byte[]{0x06, 0x40, 0x04, 0x00, 0x02, 0x00, 0x02});
    }

    public void saveNewDisplayName(String newName) {
        displayName = newName;
        sharedPreferences.edit().putString("display_name_" + name, displayName).apply();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt _gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    requestHighPriorityConnection();
                    btConnectionStage = BT_FETCHING_DATA;

                    if (dataChangedCallback != null) dataChangedCallback.onDataChanged();

                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i("onConnectionStateChange", "Camera " + name + " disconnected");
                    setDisconnected();
                }
            } else {
                Log.e("onConnectionStateChange", "Gatt connect unsuccessful with status " + status);
                showToast(res.getString(R.string.str_gatt_connect_problem), Toast.LENGTH_SHORT);
                setDisconnected();
            }
        }

        @SuppressLint("SuspiciousIndentation")
        @Override
        public void onServicesDiscovered(BluetoothGatt _gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (connectBtCallback != null) connectBtCallback.onBtConnectionStateChange(true);

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
                                    // nwManRespCharacteristic = characteristic; // currently not used
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

                if (btPaired) initCommunication();
            } else {
                Log.e("onServicesDiscovered", "onServicesDiscovered unsuccessful with status " + status);
                showToast(res.getString(R.string.str_get_ble_services_problem), Toast.LENGTH_SHORT);
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
                btRssi = rssi;

                if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
            } else {
                Log.e("BLE", "onReadRemoteRssi status " + status);
            }
            gattInProgress = false;
        }
    };

    private void initCommunication() {
        if (!communicationInitiated && commandCharacteristic != null && commandRespCharacteristic != null && settingsCharacteristic != null && settingsRespCharacteristic != null && queryCharacteristic != null && queryRespCharacteristic != null && modelNoCharacteristic != null && wifiSsidCharacteristic != null && wifiPwCharacteristic != null && wifiStateCharacteristic != null) {

            communicationInitiated = true;

            gattInProgress = false;
            execBtActionLoop = getNewExecThread();
            execBtActionLoop.start();

            startCrcNotification();
            getHardwareInfo();
            readWifiApPw();
            startSrcNotification();
            startQrcNotification();
            registerForAllStatusValueUpdates();
        }
    }

    private void parseBtData(String characteristicUuid, byte[] valueBytes, boolean isCustomUuid) {
        if (isCustomUuid) { // onCharacteristicChanged
            boolean isContPack = (valueBytes[0] & 128) > 0;

            if (isContPack) {
                int msgLen = valueBytes.length - 1;
                if (packBuffer != null) {
                    try {
                        int putLen = Math.min(packBuffer.remaining(), msgLen);
                        if (putLen != msgLen)
                            Log.e("parseBtData", "Message length is " + (msgLen - putLen) + " larger than the buffer");
                        packBuffer.put(valueBytes, 1, putLen);
                    } catch (Exception e) {
                        Log.e("parseBtData", "Error: " + e);
                        return;
                    }

                    if (packBuffer.remaining() <= 0) {
                        //all data received
                        parseBtResponsePack();
                        gattInProgress = false;
                    }
                } else {
                    gattInProgress = false;
                }
            } else {
                GoHeader header = new GoHeader(valueBytes);

                int headerLength = header.getHeaderLength();
                int msgLen = header.getMsgLength();
                int commandId = valueBytes[headerLength];
                int error = valueBytes[headerLength + 1];

                packBuffer = ByteBuffer.allocate(msgLen);
                packBuffer.put(valueBytes, headerLength, valueBytes.length - headerLength);

                if (packBuffer.remaining() <= 0) {
                    //all data received
                    parseBtResponsePack();
                    gattInProgress = false;

                    if (error == 0) {
                        switch (characteristicUuid) {
                            case commandRespUUID:
                                if (commandId == 0x0d)
                                    showToast(_context.getResources().getString(R.string.str_set_dt_success), Toast.LENGTH_SHORT);
                                break;
                            case settingsRespUUID:
                                if (valueBytes.length >= 4 && valueBytes[3] == 91) {
                                    // ignore keepAlive response
                                } else {
                                    // A setting was changed. Get an update of the current settings
                                    if (providesAvailableOptions) getAvailableOptions();
                                    else getAllSettings();
                                }
                                break;
                            case queryRespUUID:
                                if (valueBytes.length >= 5) {
                                    handleQueryResponse(valueBytes);
                                } else if (commandId == 50 && valueBytes.length == 3 && valueBytes[2] == 0) {
                                    // GoPro does not provide available setting options
                                    providesAvailableOptions = false;
                                    Log.i("GoProDevice", "'" + name + "', model '" + modelName + "' does not provide the available setting options!");
                                    /* getSettingsJson(); */
                                }
                                break;
                        }
                    } else {
                        if (Objects.equals(characteristicUuid, commandRespUUID) && commandId == 0x0d)
                            showToast(_context.getResources().getString(R.string.str_set_dt_not_success), Toast.LENGTH_SHORT);

                        switch (error) {
                            case 1:
                                LE = "Error";
                                break;
                            case 2:
                                LE = "Invalid Parameter";
                                break;
                            default:
                                LE = "Unknown error";
                        }
                        Log.e("GoProDevice", name + " responds with error code " + error + " (" + LE + ") to command id " + commandId);
                    }
                }
            }
        } else { // onCharacteristicRead
            switch (characteristicUuid) {
                case modelNoUUID:
                    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
                    byteBuffer.put(valueBytes);
                    String decoded = new String(byteBuffer.array(), StandardCharsets.UTF_8);
                    modelID = Integer.parseInt(decoded, 16);
                    handleModelId();
                    break;
                case wifiSsidUUID:
                    ByteBuffer byteBuffer2 = ByteBuffer.allocate(33);
                    byteBuffer2.put(valueBytes);
                    wifiSSID = new String(byteBuffer2.array(), StandardCharsets.UTF_8).trim();
                    break;
                case wifiPwUUID:
                    ByteBuffer byteBuffer3 = ByteBuffer.allocate(64);
                    byteBuffer3.put(valueBytes);
                    wifiPSK = new String(byteBuffer3.array(), StandardCharsets.UTF_8).trim();
                    break;
                case wifiStateUUID:
                    wifiApState = (int) valueBytes[0];
                    break;
                //endregion
            }
            gattInProgress = false;
        }

        doIfBtConnected();

        if (dataChangedCallback != null) dataChangedCallback.onDataChanged();

        if (modelID > UNK_MODEL && freshPaired) {
            sendBtPairComplete();
        }
    }

    private void handleQueryResponse(byte[] valueBytes) {
        GoHeader header = new GoHeader(valueBytes);

        int headerLength = header.getHeaderLength();
        int commandId = valueBytes[headerLength];

        int id1 = valueBytes[3];
        int len1 = valueBytes[4];

        if (commandId == (byte) 0x13 && id1 == 54 && len1 == 8) { // Remaining space
            ByteBuffer buffer = ByteBuffer.allocate(len1);
            buffer.put(valueBytes, 5, len1);
            buffer.flip();
            long memBytes = buffer.getLong();
            if (memBytes != 0) remainingMemory = getReadableFileSize(memBytes * 1024);
            else remainingMemory = res.getString(R.string.str_NA);
        } else if (commandId == (byte) 0x13 && id1 == 70 /*&& len1 == 1*/) { // Battery level
            remainingBatteryPercent = valueBytes[5];
        } else if (commandId == (byte) 0x13 && id1 == 43 && len1 == 1) { // Current mode
            mode = new GoMode(_context, valueBytes[5]);
        } else if (commandId == (byte) 0x13 && id1 == 97 && len1 == 4) { // Current preset
            ByteBuffer buffer = ByteBuffer.allocate(len1);
            buffer.put(valueBytes, 5, len1);
            buffer.flip();
            int presetID = buffer.getInt();

            preset = new GoPreset(_context, presetID);
        } else if (commandId == (byte) 0x53) { // response for registerForAutoValueUpdates()
            autoValueUpdatesRegistered = true;
        } else if (commandId == (byte) 0x93) { // Multi-Value query response
            int nextLen;

            for (int index = headerLength; index < valueBytes.length; ) {
                int statusID = valueBytes[index] & 0xff;
                if (valueBytes.length > index + 1) nextLen = valueBytes[index + 1];
                else break;

                if (nextLen > 0) {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(nextLen);
                    byteBuffer.put(valueBytes, index + 2, nextLen);
                    handleStatusData(statusID, byteBuffer);
                }

                index += nextLen + 2;
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void sendBtPairComplete() {
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                //send to GP-0091: {msg_len, 0x03, 0x01, 0x08, 0x00, 0x12, dev_name_len, dev_name}
                byte[] msg = {0x0f, 0x03, 0x01, 0x08, 0x00, 0x12, 0x09, 'G', 'o', 'E', 'a', 's', 'y', 'P', 'r', 'o'};
                if (nwManCmdCharacteristic != null && nwManCmdCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(nwManCmdCharacteristic)) {
                    freshPaired = false;
                } else {
                    Log.e("sendBtPairComplete", "not sent");
                    gattInProgress = false;
                }
            });
        }
    }

    private void handleModelId() {
        if (modelID >= HERO9_BLACK) {
            // Hero 9+
            keepAlive_msg = keepAlive_v2;
            startStream_query = streamStart_query_v3;
            stopStream_query = streamStop_query_v2; //TODO test
            getMediaList_query = mediaList_query_v2; //TODO test
            getThumbNail_query = thumbNail_query_v2;
        } else if (modelID >= HERO8_BLACK) {
            // Hero 8 TODO and Max ?
            keepAlive_msg = keepAlive_v2;
            startStream_query = streamStart_query_v2;
            stopStream_query = streamStop_query_v1;
            getMediaList_query = mediaList_query_v1;
            getThumbNail_query = thumbNail_query_v1;
        } else {
            // -Hero 7
            keepAlive_msg = keepAlive_v1; //TODO test
            startStream_query = streamStart_query_v1; //TODO test
            stopStream_query = streamStop_query_v1; //TODO test
            getMediaList_query = mediaList_query_v1;
            getThumbNail_query = thumbNail_query_v1;
            preset = new GoPreset(_context, -2);
        }

        queryAllStatusValues();
        getAvailableOptions();
    }

    private void parseBtResponsePack() {
        byte[] byteArray = packBuffer.array();
        packBuffer.clear();
        packBuffer = null;
        int commandId = byteArray[0];
        int error = byteArray[1];
        int nextStart = 2;

        if (error == 0) { // success
            if (commandId == 83) { // response for registerForAllStatusValueUpdates()
                autoValueUpdatesRegistered = true;
                int nextLen;

                for (int index = nextStart; index < byteArray.length; ) {
                    int statusID = byteArray[index] & 0xff;
                    if (byteArray.length > index + 1) nextLen = byteArray[index + 1];
                    else break;

                    if (nextLen > 0) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(nextLen);
                        byteBuffer.put(byteArray, index + 2, nextLen);
                        handleStatusData(statusID, byteBuffer);
                    }

                    index += nextLen + 2;
                }
            } else if (commandId == 60) { // Hardware info -> Model ID, model name, board type, firmware version, serial number, AP SSID, AP MAC Address
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
                sharedPreferences.edit().putString("model_name_" + name, modelName).apply();

                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // board type
                byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                boardType = new String(byteBuffer.array(), StandardCharsets.UTF_8).trim();

                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // firmware
                byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                firmware = new String(byteBuffer.array(), StandardCharsets.UTF_8).trim();

                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // serial number
                byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                serialNumber = new String(byteBuffer.array(), StandardCharsets.UTF_8).trim();

                nextStart = nextStart + 1 + nextLen;
                nextLen = byteArray[nextStart];

                // AP SSID
                byteBuffer = ByteBuffer.allocate(nextLen);
                byteBuffer.put(byteArray, nextStart + 1, nextLen);
                wifiSSID = new String(byteBuffer.array(), StandardCharsets.UTF_8).trim();

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
                wifiBSSID = sb.toString().toUpperCase();
            } else if (commandId == 19) { // All status values
                int nextLen;

                for (int index = nextStart; index < byteArray.length; ) {
                    int statusID = byteArray[index] & 0xff;
                    if (byteArray.length > index + 1) nextLen = byteArray[index + 1];
                    else break;

                    if (nextLen > 0) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(nextLen);
                        byteBuffer.put(byteArray, index + 2, nextLen);
                        handleStatusData(statusID, byteBuffer);
                    }

                    index += nextLen + 2;
                }
            } else if (commandId == 50) { // Available option IDs for all settings
                int nextLen;
                availableSettingsOptions = new ArrayList<>();

                for (int index = nextStart; index < byteArray.length; ) {
                    int settingId = byteArray[index] & 0xff;
                    if (byteArray.length > index + 1) nextLen = byteArray[index + 1];
                    else break;

                    if (nextLen > 0) {
                        int optionId = byteArray[index + 2];

                        availableSettingsOptions.add(new Pair<>(settingId, optionId));
                    }

                    index += nextLen + 2;
                }

                getAllSettings();
            } else if (commandId == 18) { // All settings
                goSettings = new ArrayList<>();
                int nextLen;

                for (int index = nextStart; index < byteArray.length; ) {
                    int settingID = byteArray[index] & 0xff;
                    if (byteArray.length > index + 1) nextLen = byteArray[index + 1];
                    else break;

                    if (nextLen > 0) {
                        ByteBuffer byteBuffer = ByteBuffer.allocate(nextLen);
                        byteBuffer.put(byteArray, index + 2, nextLen);
                        handleSettingData(settingID, byteBuffer);
                    }

                    index += nextLen + 2;
                }

                goSettings.sort(Comparator.comparingInt(GoSetting::getSettingId));

                if (settingsChangedCallback != null) settingsChangedCallback.onSettingsChanged();
            }
            /*else if (commandId == 59 *//* 0x3B *//*) { // Settings JSON gzip
                ByteBuffer byteBuffer = ByteBuffer.allocate(byteArray.length - 2);
                byteBuffer.put(byteArray, 2, byteArray.length - 2);
                byteBuffer.flip();

                String jsonRsp = decompress(byteBuffer.array());
                Log.e("JSON", jsonRsp);
            }*/
        } else {
            switch (error) {
                case 1:
                    LE = "Error";
                    break;
                case 2:
                    LE = "Invalid Parameter";
                    break;
                default:
                    LE = "Unknown error";
            }
            Log.e("GoProDevice", name + " responds with error code " + error + " (" + LE + ") to command id " + commandId);
        }
    }

    /*
    public static String decompress(byte[] compressed) {
        final int BUFFER_SIZE = 32;
        ByteArrayInputStream is = new ByteArrayInputStream(compressed);
        StringBuilder string = new StringBuilder();
        try {
            GZIPInputStream gis = new GZIPInputStream(is, BUFFER_SIZE);
            byte[] data = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = gis.read(data)) != -1) {
                string.append(new String(data, 0, bytesRead));
            }
            gis.close();
            is.close();
        } catch (Exception ignore) {

        }
        return string.toString();
    }
    */

    private void handleStatusData(int statusID, ByteBuffer buffer) {
        switch (statusID) {
            case 2:
                isCharging = buffer.get(0) == 4;
            case 6:
                isHot = buffer.get(0) != 0;
                break;
            case 85:
                isCold = buffer.get(0) != 0;
                break;
            case 8: // Is the camera busy?
                isBusy = buffer.get(0) != 0;
                break;
            case 13: // Video progress counter
                lastVideoProgressReceived = new Date();
                buffer.flip();
                int videoProgress = buffer.getInt();

                boolean wasRecording = isRecording;
                isRecording = videoProgress != 0;

                if (isRecording && !wasRecording) {
                    initShutterWatchdog();
                }
                break;
            case 43:
            case 89:
                int modeId = buffer.get(0);
                mode = new GoMode(_context, modeId);
                break;
            case 54: // Remaining space on the sdcard in Kilobytes as integer
                buffer.flip();
                long memBytes = buffer.getLong();
                if (memBytes != 0) remainingMemory = getReadableFileSize(memBytes * 1024);
                else remainingMemory = res.getString(R.string.str_NA);
                break;
            case 69: // AP state as boolean
                wifiApState = (int) buffer.get(0);
                break;
            case 70: // Internal battery percentage as percent
                remainingBatteryPercent = buffer.get(0);
                break;
            case 97: // Current Preset ID as integer
                buffer.flip();
                int presetId = buffer.getInt();
                preset = new GoPreset(_context, presetId);
                break;
        }
    }

    private void handleSettingData(int settingID, ByteBuffer buffer) {
        GoSetting goSetting = new GoSetting(settingID, buffer.get(0), settingsValues, availableSettingsOptions);

        if (goSetting.isValid()) {
            goSettings.add(goSetting);

            if (goSetting.getSettingId() == 59 /* Auto Off */ && goSetting.getCurrentOptionId() != 0 /* NEVER */ && !autoOffDisableAsked && !((MyApplication) _application).isAutoOffToIgnore() && modelID < HERO9_BLACK) {
                autoOffDisableAsked = true;
                askAutoOffDisable(goSetting.getCurrentOptionName());
            }
        }
    }

    private void askAutoOffDisable(String offTime) {
        new Thread(() -> {
            Looper.prepare();
            new Handler(Looper.getMainLooper()).post(() -> {
                final AlertDialog alert = new AlertDialog.Builder(_context).setTitle(res.getString(R.string.str_enabled_auto_off_title)).setMessage(String.format(res.getString(R.string.str_enabled_auto_off_msg), displayName, offTime)).setPositiveButton(res.getString(R.string.str_Disable), (dialog, which) -> setSetting(59, 0)).setNegativeButton(res.getString(R.string.str_Ignore), (dialog, which) -> dialog.dismiss()).create();

                alert.show();
            });
            Looper.loop();
        }).start();
    }

    private void doIfBtConnected() {
        if (btConnectionStage != BT_CONNECTED && modelID > 0 && !Objects.equals(wifiPSK, "") && !Objects.equals(wifiSSID, "")) {
            btConnectionStage = BT_CONNECTED;

            if (checkConnected != null) {
                checkConnected.cancel();
                checkConnected.purge();
            }

            //start timed actions
            initTimedActions();
            initExecWatchdog();

            /*if (connectBtCallback != null)
                connectBtCallback.onBtConnectionStateChange(true);*/
            if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
        }
    }

    //region BT Communication tools
    private void requestHighPriorityConnection() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.getClass().getMethod("requestConnectionPriority", new Class[]{Integer.TYPE}).invoke(bluetoothGatt, new Object[]{1});
            } catch (Exception e) {
                Log.e("requestHighPriorityConnection", "Failed!");
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnectGoProDevice() {
        Log.d("disconnectBt", "Disconnect forced");
        if (isWifiConnected) disconnectWifi();

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

        try {
            _context.unregisterReceiver(mConnectionReceiver);
        } catch (Exception ignored) {

        }
    }

    @SuppressLint("MissingPermission")
    private void setDisconnected() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        try {
            if (execBtActionLoop != null) execBtActionLoop.interrupt();
        } catch (Exception e) {
            if (execBtActionLoop != null && !execBtActionLoop.isInterrupted()) try {
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
        if (packBuffer != null) {
            packBuffer.clear();
            packBuffer = null;
        }

        btConnectionStage = BT_NOT_CONNECTED;
        autoValueUpdatesRegistered = false;
        registerForAutoValueUpdatesCounter = 0;

        wifiApState = -1;
        isWifiConnected = false;
        isHot = false;
        isCold = false;

        gattInProgress = false;

        if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
    }

    @SuppressLint("MissingPermission")
    private void startCrcNotification() {
        synchronized (btActionBuffer) {
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
                            showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                            disconnectGoProDevice();
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
    }

    @SuppressLint("MissingPermission")
    private void startSrcNotification() {
        synchronized (btActionBuffer) {
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
                            showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                            disconnectGoProDevice();
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
    }

    @SuppressLint("MissingPermission")
    private void startQrcNotification() {
        synchronized (btActionBuffer) {
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
                            showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                            disconnectGoProDevice();
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
    }
    //endregion

    //region BT Readings
    @SuppressLint("MissingPermission")
    private void readWifiApSsid() {
        synchronized (btActionBuffer) {
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
    }

    @SuppressLint("MissingPermission")
    private void readWifiApPw() {
        synchronized (btActionBuffer) {
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
    }

    @SuppressLint("MissingPermission")
    private void readWifiApState() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
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
    }

    private void readBtRssi() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE || btActionBuffer.contains(readBtRssi)) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(readBtRssi);
        }
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
        synchronized (btActionBuffer) {
            btActionBuffer.add(keepAlive);
        }
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
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                Date now = new Date();
                int year = now.getYear() + 1900;
                int month = now.getMonth() + 1;
                byte yyH = (byte) ((year >> 8) & 0xFF);
                byte yyL = (byte) (year & 0xFF);
                byte[] msg = {0x09, 0x0d, 0x07, yyH, yyL, (byte) month, (byte) now.getDate(), (byte) now.getHours(), (byte) now.getMinutes(), (byte) now.getSeconds()};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void setSetting(int settingId, int optionId) {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x03, (byte) settingId, 0x01, (byte) optionId};
                if (settingsCharacteristic == null || !settingsCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(settingsCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }
    //endregion

    //region BT Commands (0x0072)
    @SuppressLint("MissingPermission")
    public void getHardwareInfo() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x01, 0x3C};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }

    // TODO get and parse Date/Time; byte[] msg = {0x01, 0x0E}; https://gopro.github.io/OpenGoPro/ble_2_0#get-datetime

    @SuppressLint("MissingPermission")
    public void setSubMode(int mode, int subMode) {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x05, 0x03, 0x01, (byte) mode, 0x01, (byte) subMode};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                } else {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    queryAllStatusValues();
                }
            });
        }
    }

    /*@SuppressLint("MissingPermission")
    public void getSettingsJson() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x01, 0x3B};
                if (commandCharacteristic != null && commandCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {

                } else {
                    gattInProgress = false;
                }
            });
        }
    }*/

    @SuppressLint("MissingPermission")
    public void shutterOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] shutterOn = {0x03, 0x01, 0x01, 0x01};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(shutterOn) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void shutterOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] shutterOff = {0x03, 0x01, 0x01, 0x00};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(shutterOff) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void sleep() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] camSleep = {0x01, 0x05};
                if (commandCharacteristic != null && commandCharacteristic.setValue(camSleep) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    connectBtCallback = null;
                    //bluetoothGatt.disconnect();
                    //wifiApState = -1;
                    //isWifiConnected = false;
                    disconnectGoProDevice();
                } else {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void wifiApOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }
                byte[] wifiApOn = {0x03, 0x17, 0x01, 0x01};
                if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOn) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    if (!autoValueUpdatesRegistered) readWifiApState();
                } else {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void wifiApOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }
                byte[] wifiApOff = {0x03, 0x17, 0x01, 0x00};
                if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOff) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    if (!autoValueUpdatesRegistered) readWifiApState();
                } else {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void locateOn() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x03, 0x16, 0x01, 0x01};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void locateOff() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x03, 0x16, 0x01, 0x00};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void highlight() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] highlight = {0x01, 0x18};
                if (commandCharacteristic == null || !commandCharacteristic.setValue(highlight) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void setPreset(String presetName) {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] cmd = presetCmds.getOrDefault(presetName, new byte[]{0x06, 0x40, 0x04, 0x00, 0x00, 0x00, 0x00}); // "standard" as default
                if (commandCharacteristic == null || !commandCharacteristic.setValue(cmd) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                    gattInProgress = false;
                } else {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    queryAllStatusValues();
                }
            });
        }
    }
    //endregion

    //region BT Queries (0x0076)
    @SuppressLint("MissingPermission")
    private void registerForAllStatusValueUpdates() {
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x01, 0x53 /* 83 */};

                if (queryCharacteristic == null || !queryCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                    Log.e("registerForAllStatusValueUpdates", "failed action");
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    public void queryAllStatusValues() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x01, 0x13};

                if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                    lastMemoryQuery = new Date();
                } else {
                    Log.e("getAllStatusValues", "failed action");
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void getAvailableOptions() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x01, 0x32};

                if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                    lastSettingUpdate = new Date();
                } else {
                    Log.e("getAllSettings", "failed action");
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void getAllSettings() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x01, 0x12};

                if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                    lastSettingUpdate = new Date();
                } else {
                    Log.e("getAllSettings", "failed action");
                    gattInProgress = false;
                }
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void getMemory() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
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
    }

    @SuppressLint("MissingPermission")
    private void getBatteryLevel() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
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
    }

    @SuppressLint("MissingPermission")
    private void getPreset() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        if (modelID >= HERO8_BLACK) {
            synchronized (btActionBuffer) {
                btActionBuffer.add(() -> {
                    if (btConnectionStage < 2) {
                        gattInProgress = false;
                        return;
                    }

                    byte[] msg = {0x02, 0x13, 0x61};
                    if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                        lastOtherQueries = new Date();
                    } else {
                        Log.e("getPreset", "failed action");
                        gattInProgress = false;
                    }
                });
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getMode() {
        if (btActionBuffer.size() > MAX_FIFO_SIZE) {
            gattInProgress = false;
            return;
        }
        synchronized (btActionBuffer) {
            btActionBuffer.add(() -> {
                if (btConnectionStage < 2) {
                    gattInProgress = false;
                    return;
                }

                byte[] msg = {0x02, 0x13, 43};
                if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                    lastOtherQueries = new Date();
                } else {
                    Log.e("getMode", "failed action");
                    gattInProgress = false;
                }
            });
        }
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

                                if (pairCallback != null) pairCallback.onPairingFinished(true);

                                try {
                                    context.unregisterReceiver(pairingReceiver);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                };
                _context.registerReceiver(pairingReceiver, filter);
            } else {
                showToast(String.format(res.getString(R.string.str_pairing_failed), displayName), Toast.LENGTH_SHORT);
            }
        } catch (Exception e) {
            if (pairCallback != null) pairCallback.onPairingFinished(false);
        }
    }

    interface ConnectBtCallbackInterface {
        void onBtConnectionStateChange(boolean connected);
    }

    private ConnectBtCallbackInterface connectBtCallback;
    private Timer checkConnected = null;

    @SuppressLint("MissingPermission")
    public void connectBt(ConnectBtCallbackInterface _connectBtCallback) {
        connectBtCallback = _connectBtCallback;
        bluetoothGatt = bluetoothDevice.connectGatt(_context, true, gattCallback);
        if (bluetoothGatt != null) {
            btConnectionStage = BT_CONNECTING;

            if (dataChangedCallback != null) dataChangedCallback.onDataChanged();

            // Timeout connecting
            checkConnected = new Timer();
            checkConnected.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (btConnectionStage != BT_CONNECTED) {
                        Log.e("connectBt", "BT connection timeout");
                        disconnectGoProDevice();
                        if (connectBtCallback != null)
                            connectBtCallback.onBtConnectionStateChange(false);
                    }
                }
            }, 35000);
        } else {
            btConnectionStage = BT_NOT_CONNECTED;

            if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
        }
    }

    interface DataChangedCallbackInterface {
        void onDataChanged();
    }

    private DataChangedCallbackInterface dataChangedCallback;

    public void getDataChanges(DataChangedCallbackInterface _dataChangedCallback) {
        dataChangedCallback = _dataChangedCallback;
    }

    interface SettingsChangedCallbackInterface {
        void onSettingsChanged();
    }

    private SettingsChangedCallbackInterface settingsChangedCallback;

    public void getSettingsChanges(SettingsChangedCallbackInterface _settingsChangedCallback) {
        settingsChangedCallback = _settingsChangedCallback;
    }

    interface WifiConnectedCallbackInterface {
        void onWifiConnected();
    }

    private WifiConnectedCallbackInterface wifiConnectedCallback;

    public void connectWifi(WifiConnectedCallbackInterface _wifiConnectedCallback) {
        wifiConnectedCallback = _wifiConnectedCallback;
        if (wifiApState != 1) wifiApOn();

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
            showToast(String.format(res.getString(R.string.str_connect_first), displayName), Toast.LENGTH_SHORT);
            return;
        } else if (Objects.equals(wifiSSID, "")) {
            readWifiApSsid();
            showToast(String.format(res.getString(R.string.str_ssid_unk), displayName), Toast.LENGTH_SHORT);
            return;
        } else if (Objects.equals(wifiPSK, "")) {
            readWifiApPw();
            showToast(String.format(res.getString(R.string.str_psk_unk), displayName), Toast.LENGTH_SHORT);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiAndroidQUp();
        } else {
            new Thread(this::connectWifiAndroidPDown).start();
        }

    }

    private BroadcastReceiver mConnectionReceiver;

    @SuppressLint("MissingPermission")
    private void connectWifiAndroidPDown() {
        WifiManager wifiManager = (WifiManager) _context.getSystemService(Context.WIFI_SERVICE);
        boolean mustConnect = false;
        int netId = -1;
        // Check if this network is already known
        WifiConfiguration wifiConfiguration = null;
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks(); // deprecated in API level 29 (Android 10/Q)
        for (WifiConfiguration _wifiConfiguration : list) {
            if (_wifiConfiguration.SSID != null && _wifiConfiguration.SSID.equals('"' + wifiSSID + '"')) {
                wifiConfiguration = _wifiConfiguration;
                break;
            }
        }

        boolean otherWifiIsConnected = false;

        if (wifiConfiguration == null) {
            // create WifiConfiguration instance
            WifiConfiguration newWifiConfiguration = new WifiConfiguration(); // deprecated in API level 29 (Android 10/Q)
            newWifiConfiguration.SSID = String.format("\"%s\"", wifiSSID);//'"' + wifiSSID + '"';
            //newWifiConfiguration.BSSID = '"' + wifiBSSID + '"';
            newWifiConfiguration.preSharedKey = String.format("\"%s\"", wifiPSK);//'"' + wifiPSK + '"';

            //Add it to Android wifi manager settings
            netId = wifiManager.addNetwork(newWifiConfiguration);
            mustConnect = true;
        } else {
            // Check if already connected
            int connectedId = wifiManager.getConnectionInfo().getNetworkId(); // getConnectionInfo() is deprecated in API level 31 (Android 12/S)

            // If not already connected, connect it
            if (connectedId != wifiConfiguration.networkId) {
                otherWifiIsConnected = connectedId != -1;
                mustConnect = true;
                netId = wifiConfiguration.networkId;
                //wifiConfiguration.BSSID = '"' + wifiBSSID + '"';
                wifiConfiguration.preSharedKey = String.format("\"%s\"", wifiPSK);//;
            }
        }

        if (mustConnect) {
            // Connect to it
            if (otherWifiIsConnected) wifiManager.disconnect();

            mConnectionReceiver = new WifiConnectionMonitor();
            IntentFilter aFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            aFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            _context.registerReceiver(mConnectionReceiver, aFilter);

            if (wifiManager.enableNetwork(netId, true)) {
                wifiManager.reconnect();
            } else {
                isWifiConnected = false;

                if (wifiConnectedCallback != null) wifiConnectedCallback.onWifiConnected();
            }
        } else {
            isWifiConnected = true;

            if (wifiConnectedCallback != null) wifiConnectedCallback.onWifiConnected();
        }
    }

    private final class WifiConnectionMonitor extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent in) {
            String action = in.getAction();
            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo networkInfo = in.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);

                if (networkInfo == null) return;

                String eventSSID = networkInfo.getExtraInfo();
                String goproSSID = String.format("\"%s\"", wifiSSID);

                if (!Objects.equals(eventSSID, goproSSID)) return;

                String reason = networkInfo.getReason();

                if (reason != null) {
                    Log.e("WiFi-Connect", "Error reason: " + reason + "; State: " + networkInfo.getState().name());

                    showToast(res.getString(R.string.str_something_wrong) + "\n" + reason, Toast.LENGTH_SHORT);

                    isWifiConnected = false;

                    if (wifiConnectedCallback != null) wifiConnectedCallback.onWifiConnected();

                    try {
                        _context.unregisterReceiver(mConnectionReceiver);
                    } catch (Exception ignored) {

                    }
                } else if (networkInfo.isConnected()) {
                    WifiManager wifiManager = (WifiManager) _context.getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                    if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                        isWifiConnected = true;

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {
                        }

                        if (wifiConnectedCallback != null) wifiConnectedCallback.onWifiConnected();

                        try {
                            _context.unregisterReceiver(mConnectionReceiver);
                        } catch (Exception ignored) {

                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void connectWifiAndroidQUp() {
        if (isWifiConnected) {
            if (wifiConnectedCallback != null)
                new Thread(() -> wifiConnectedCallback.onWifiConnected()).start();
            return;
        }

        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null || Objects.equals(wifiBSSID, "")) return;

        final WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder().setSsid(wifiSSID).setBssid(MacAddress.fromString(wifiBSSID)).setWpa2Passphrase(wifiPSK).build();

        final NetworkRequest networkRequest = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(wifiNetworkSpecifier).build();

        HandlerThread handlerThread = new HandlerThread("WiFi Manager Network Update Handler");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        freshPaired = false;
        connectivityManager.requestNetwork(networkRequest, networkCallback, handler);
    }

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            LinkProperties prop = connectivityManager.getLinkProperties(network);
            List<LinkAddress> linkAddresses = prop.getLinkAddresses();
            if (linkAddresses.size() > 1) {
                myClientIP = linkAddresses.get(1).getAddress().toString().substring(1);
            } else {
                myClientIP = linkAddresses.get(0).getAddress().toString().substring(1);
            }
            connectivityManager.bindProcessToNetwork(network);
            isWifiConnected = true;
            if (wifiConnectedCallback != null) wifiConnectedCallback.onWifiConnected();
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            isWifiConnected = false;
            if (wifiConnectedCallback != null) wifiConnectedCallback.onWifiConnected();
            showToast(String.format(res.getString(R.string.str_ap_na), displayName), Toast.LENGTH_SHORT);
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            isWifiConnected = false;
            if (wifiConnectedCallback != null) wifiConnectedCallback.onWifiConnected();
            showToast(String.format(res.getString(R.string.str_wifi_lost), displayName), Toast.LENGTH_SHORT);
        }
    };

    private void disconnectWifi() {
        if (!isWifiConnected) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            WifiManager wifiManager = (WifiManager) _context.getSystemService(Context.WIFI_SERVICE);
            wifiManager.disconnect();
        }
    }

    public boolean removeBtBond() {
        disconnectGoProDevice();

        try {
            Method m;
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

    private Timer shutterWatchdogTimer = new Timer();
    private Date lastVideoProgressReceived = new Date();

    private void initShutterWatchdog() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                long now = new Date().getTime();

                if (now - lastVideoProgressReceived.getTime() > 1500) {
                    isRecording = false;
                    shutterWatchdogTimer.cancel();
                    shutterWatchdogTimer.purge();
                }
            }
        };

        shutterWatchdogTimer = new Timer();
        shutterWatchdogTimer.schedule(timerTask, 0, 500);
    }

    private void initTimedActions() {
        actionTimer.cancel();
        actionTimer.purge();

        lastKeepAlive = new Date();
        lastMemoryQuery = new Date();
        lastOtherQueries = new Date();
        lastBatteryRead = new Date();
        lastSettingUpdate = new Date();

        TimerTask timedActions = new TimerTask() {
            @Override
            public void run() {
                if (btConnectionStage < BT_CONNECTED) return;

                if (settingsValues == null)
                    settingsValues = ((MyApplication) _application).getSettingsValues();

                long now = new Date().getTime();

                // Send "keep alive" every 60 seconds; only for models newer then hero 8
                if (now - lastKeepAlive.getTime() > 60000) {
                    keepAlive();
                    lastKeepAlive = new Date();
                }

                // Query "Remaining space"
                if ((!autoValueUpdatesRegistered || Objects.equals(remainingMemory, "NC")) && now - lastMemoryQuery.getTime() > 7000) {
                    getMemory();
                    lastMemoryQuery = new Date();
                }

                if (now - lastSettingUpdate.getTime() > 5000) {
                    // update settings
                    if (providesAvailableOptions) getAvailableOptions();
                    else getAllSettings();
                }

                // Some other queries
                if (now - lastOtherQueries.getTime() > 3000) {
                    if (!autoValueUpdatesRegistered || Objects.equals(preset.getTitle(), "NC")) {
                        getPreset();
                        getMode();
                    }

                    if (Objects.equals(wifiPSK, "")) {
                        readWifiApPw();
                    }

                    if (!autoValueUpdatesRegistered && registerForAutoValueUpdatesCounter < 4) {
                        registerForAutoValueUpdatesCounter++;
                        registerForAllStatusValueUpdates();
                    } else if (!autoValueUpdatesRegistered) {
                        showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);

                        Log.e("registerForAutoValueUpdates", "max tries reached");
                        disconnectGoProDevice();
                    }

                    readBtRssi();

                    lastOtherQueries = new Date();
                }

                // Read "Battery level"
                if ((!autoValueUpdatesRegistered || remainingBatteryPercent == 0) && now - lastBatteryRead.getTime() > 10000) {
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
                try {
                    Runnable runnable;
                    synchronized (btActionBuffer) {
                        runnable = btActionBuffer.get(0);

                        if (runnable == null) {
                            Log.e("execNextBtAction", "Runnable was 'null'");
                            continue;
                        } else {
                            btActionBuffer.remove(0); // Remove the runnable from btActionBuffer
                        }
                    }

                    gattInProgress = true; // Set gattInProgress flag
                    gattProgressStart = new Date(); // Store time since Gatt is in progress for the watchdog
                    runnable.run(); // Run the runnable
                    Thread.sleep(btActionDelay);
                } catch (Exception ignore) {
                    //Log.e("execNextBtAction", "Exception caught: " + e);
                }
            }
        });
    }

    private void showToast(String text, int duration) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(_context, text, duration).show();
            } catch (Exception ignore) {

            }
        });
    }

    //endregion
}
