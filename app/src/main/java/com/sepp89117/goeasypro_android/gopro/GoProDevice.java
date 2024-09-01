package com.sepp89117.goeasypro_android.gopro;

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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sepp89117.goeasypro_android.MyApplication;
import com.sepp89117.goeasypro_android.R;
import com.sepp89117.goeasypro_android.helpers.BtActionHelper;
import com.sepp89117.goeasypro_android.helpers.ShutterLog;
import com.sepp89117.goeasypro_android.helpers.WiFiHelper;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.GZIPInputStream;

@SuppressLint("MissingPermission")
public class GoProDevice {
    //region BT connection stats
    public static final int BT_NOT_CONNECTED = 0;
    public static final int BT_CONNECTING = 1;
    public static final int BT_FETCHING_DATA = 2;
    public static final int BT_CONNECTED = 3;
    //endregion

    public static final int UNK_MODEL = 0;
    public static final int FUSION = 23;
    public static final int HERO2018 = 34;
    public static final int HERO8_BLACK = 50;
    public static final int HERO_MAX = 51;
    public static final int HERO9_BLACK = 55;
    public static final int HERO11_BLACK = 58;
    public static final int HERO12_BLACK = 62;

    private static final Map<String, byte[]> presetCmds = new HashMap<>();

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

    // region protobuf IDs
    private static final int[] featureIDs = {0x02 /* NETWORK_MANAGEMENT */, 0xF1 /* COMMAND */, 0xF3 /* SETTING */, 0xF5 /* QUERY */};
    // RELEASE_NETWORK_RSP = 0xF8
    private static final int[][] actionIDs = {
            /* NETWORK_MANAGEMENT */
            {0x02 /* SCAN_WIFI_NETWORKS */, 0x03 /* GET_AP_ENTRIES */, 0x04 /* REQUEST_WIFI_CONNECT */, 0x05 /* REQUEST_WIFI_CONNECT_NEW */, 0x0B /* NOTIF_START_SCAN */, 0x0C /* NOTIF_PROVIS_STATE */, 0x78 /* RELEASE_NETWORK */, 0x82 /* SCAN_WIFI_NETWORKS_RSP */, 0x83 /* GET_AP_ENTRIES_RSP */, 0x84 /* REQUEST_WIFI_CONNECT_RSP */, 0x85 /* REQUEST_WIFI_CONNECT_NEW_RSP */, 0xF8 /* RELEASE_NETWORK_RSP */},
            /* COMMAND */
            {0x69 /* SET_CAMERA_CONTROL */, 0x6B /* SET_TURBO_MODE */, 0x79 /* SET_LIVESTREAM_MODE */, 0xE9 /* SET_CAMERA_CONTROL_RSP */, 0xEB /* SET_TURBO_MODE_RSP */, 0xF9 /* SET_LIVESTREAM_MODE_RSP */},
            /* SETTING */
            {

            },
            /* QUERY */
            {0x72 /* GET_PRESET_STATUS */, 0x74 /* GET_LIVESTREAM_STATUS */, 0xF2 /* GET_PRESET_STATUS_RSP */, 0xF3 /* PRESET_MODIFIED_NOTIFICATION */, 0xF4 /* LIVESTREAM_STATUS_RSP */, 0xF5 /* LIVESTREAM_STATUS_NOTIF */}};
    // endregion

    private final Context _context;
    private final Resources res;

    private final SharedPreferences sharedPreferences;

    private Timer actionTimer = new Timer();
    private Timer execWatchdogTimer = new Timer();

    private BtActionHelper btActionHelper;

    private final BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    public boolean btPaired = false;
    public int btConnectionStage = BT_NOT_CONNECTED;
    public String btDeviceName;
    public String displayName;
    public String btMacAddress;
    public String modelName;
    public int modelID = UNK_MODEL;
    public String boardType = "";
    public String fwVersion = "";
    public String serialNumber = "";

    public GoPreset preset;
    public GoProtoPreset protoPreset = null;
    public GoMode mode;

    public int remainingBatteryPercent = 0;
    public String remainingMemory;
    private boolean autoValueUpdatesRegistered = false;
    public int btRssi = 0;
    private String LE = "None"; // last error
    private Date lastKeepAlive = new Date();
    private int keepAliveInterval = 60000; // ms
    private Date lastOtherQueries = new Date();
    private Date lastMemoryQuery = new Date();
    private Date lastBatteryRead = new Date();
    private Date lastSettingUpdate = new Date();
    private BluetoothGattCharacteristic wifiSsidCharacteristic = null; // 0x0022
    private BluetoothGattCharacteristic wifiPwCharacteristic = null; // 0x0024
    private BluetoothGattCharacteristic wifiStateCharacteristic = null;
    private BluetoothGattCharacteristic commandCharacteristic = null; // 0x002e
    private BluetoothGattCharacteristic commandRespCharacteristic = null; // 0x0030
    private BluetoothGattCharacteristic settingsCharacteristic = null;
    private BluetoothGattCharacteristic settingsRespCharacteristic = null;
    private BluetoothGattCharacteristic queryCharacteristic = null;
    private BluetoothGattCharacteristic queryRespCharacteristic = null;
    private BluetoothGattCharacteristic modelNoCharacteristic = null;
    private BluetoothGattCharacteristic nwManCmdCharacteristic = null;
    private int registerForAutoValueUpdatesCounter = 0;
    private ByteBuffer packBuffer;
    private boolean communicationInitiated = false;
    public boolean camBtAvailable = false;
    public boolean isBusy = false;
    public boolean isRecording = false;
    public boolean isCharging = false;
    public boolean isHot = false;
    public boolean isCold = false;
    private boolean freshPaired = false;
    public boolean firmwareChecked = false;
    public boolean providesAvailableOptions = true;
    private final Application _application;
    public ArrayList<GoSetting> goSettings = new ArrayList<>();
    private ArrayList<Pair<Integer, Integer>> availableSettingsOptions = null;
    ArrayList<GoProtoPreset> goProtoPresets = new ArrayList<>();
    private boolean autoOffDisableAsked = false;
    private boolean btRetryConnect = false;
    private int lastCommandId = -1;
    private int lastPackNo = -1;
    public Calendar deviceTime;
    public boolean shouldNotBeReconnected = false;

    //Wifi
    private WiFiHelper wiFiHelper = null;
    public static final String goProIp = "10.5.5.9";
    public Integer wifiApState = -1;
    public String wifiSSID = "";
    public String wifiPSK = "";
    public String wifiBSSID = "";
    public String startStream_query = "";
    public String stopStream_query = "";
    public String getMediaList_query = "";
    public String getThumbNail_query = "";
    public String keepAlive_msg = "";
    private BroadcastReceiver pairingReceiver;

    public GoProDevice(Context context, Application application, BluetoothDevice device) {
        _application = application;
        _context = context;
        res = _context.getResources();
        bluetoothDevice = device;
        btMacAddress = bluetoothDevice.getAddress();
        btDeviceName = bluetoothDevice.getName();
        sharedPreferences = _context.getSharedPreferences("GoProDevices", Context.MODE_PRIVATE);
        displayName = sharedPreferences.getString("display_name_" + btDeviceName, btDeviceName);
        modelName = res.getString(R.string.str_NC);
        remainingMemory = res.getString(R.string.str_NC);
        modelName = sharedPreferences.getString("model_name_" + btDeviceName, modelName);
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

    private void saveNewDisplayName(String newName) {
        displayName = newName;
        sharedPreferences.edit().putString("display_name_" + btDeviceName, displayName).apply();
    }

    public String getCurrentSettingsString() {
        if (hasProtoPresets() && protoPreset != null) {
            return protoPreset.getSettingsString();
        } else {
            String modeTitle = mode.getTitle();

            final String[] currentOptions = new String[3];
            goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 3).findFirst().ifPresent(goSetting -> currentOptions[1] = goSetting.getCurrentOptionName());
            goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 4).findFirst().ifPresent(goSetting -> currentOptions[2] = goSetting.getCurrentOptionName());

            if (modeTitle.contains("Video")) {
                goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 2).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
            } else if (modeTitle.contains("Photo")) {
                goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 17).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
            } else if (modeTitle.contains("Lapse")) {
                goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 17).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
            } else if (modeTitle.contains("Multi")) {
                goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 29).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
                if (currentOptions[0] == null || currentOptions[0].isEmpty())
                    goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 30).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
                if (currentOptions[0] == null || currentOptions[0].isEmpty())
                    goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 31).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
                if (currentOptions[0] == null || currentOptions[0].isEmpty())
                    goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 32).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
                if (currentOptions[0] == null || currentOptions[0].isEmpty())
                    goSettings.stream().filter(goSetting -> goSetting.getSettingId() == 28).findFirst().ifPresent(goSetting -> currentOptions[0] = goSetting.getCurrentOptionName());
            }

            final StringBuilder presetTitle = new StringBuilder();
            for (String option : currentOptions) {
                if (option != null && !option.isEmpty()) {
                    if (presetTitle.length() > 0)
                        presetTitle.append(" | ");

                    presetTitle.append(option);
                }
            }

            return presetTitle.toString();
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt _gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    requestHighPriorityConnection();
                    btConnectionStage = BT_FETCHING_DATA;
                    if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED && !btRetryConnect) {
                    Log.i("onConnectionStateChange", "Camera " + btDeviceName + " disconnected");
                    showToast(String.format(res.getString(R.string.str_cam_disconnected), displayName), Toast.LENGTH_SHORT);
                    setDisconnected();
                }

                btRetryConnect = false;
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
            if (btActionHelper != null) {
                btActionHelper.resetGattInProgress();
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                parseBtData(characteristic.getUuid().toString(), characteristic.getValue(), false);
            } else if (status == BluetoothGatt.GATT_FAILURE) {
                Log.e("BLE", "onCharacteristicRead status GATT_FAILURE");
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                Log.e("BLE", "onCharacteristicRead status INSUFFICIENT_ENCRYPTION");
            } else {
                Log.e("BLE", "onCharacteristicRead status " + status);
            }
            if (btActionHelper != null) {
                btActionHelper.resetGattInProgress();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (characteristic == wifiSsidCharacteristic && btActionHelper != null) {
                btActionHelper.resetGattInProgress();
            } else if (characteristic == wifiPwCharacteristic && status == BluetoothGatt.GATT_SUCCESS) {
                readWifiApSsid();
                wifiApOn();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            parseBtData(characteristic.getUuid().toString(), characteristic.getValue(), true);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (btActionHelper != null) {
                btActionHelper.resetGattInProgress();
            }
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
            if (btActionHelper != null) {
                btActionHelper.resetGattInProgress();
            }
        }
    };

    private void initCommunication() {
        if (!communicationInitiated && commandCharacteristic != null && commandRespCharacteristic != null && settingsCharacteristic != null && settingsRespCharacteristic != null && queryCharacteristic != null && queryRespCharacteristic != null && modelNoCharacteristic != null && wifiSsidCharacteristic != null && wifiPwCharacteristic != null && wifiStateCharacteristic != null) {

            communicationInitiated = true;

            btActionHelper = new BtActionHelper();
            btActionHelper.startExecution();

            startCrcNotification();
            startSrcNotification();
            startQrcNotification();

            getHardwareInfo();
            readWifiApPw();
            registerForAllStatusValueUpdates();
            getUTCDateTime();
        }
    }

    private final Handler deviceTimeUpdateHandler = new Handler();
    Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            deviceTime.add(Calendar.SECOND, 1);

            deviceTimeUpdateHandler.postDelayed(this, 1000);
        }
    };

    private void parseBtData(String characteristicUuid, byte[] valueBytes, boolean isCustomUuid) {
        if (isCustomUuid) { // onCharacteristicChanged
            boolean isContPack = (valueBytes[0] & 128) > 0;
            int currPackNo = valueBytes[0] & 0xff;

            if (isContPack) {
                if ((lastPackNo == -1 && currPackNo == 128) || (lastPackNo == 143 && currPackNo == 128) || (lastPackNo + 1 == currPackNo)) {
                    lastPackNo = currPackNo;

                    int dataLen = valueBytes.length - 1;

                    if (packBuffer != null) {
                        try {
                            int putLen = Math.min(packBuffer.remaining(), dataLen);
                            if (putLen != dataLen)
                                Log.e("parseBtData", "Received data length is " + (dataLen - putLen) + " larger than the buffer");
                            packBuffer.put(valueBytes, 1, putLen);
                        } catch (Exception e) {
                            Log.e("parseBtData", "Error: " + e);
                            if (btActionHelper != null) btActionHelper.resetGattInProgress();
                            return;
                        }

                        //Log.d("GOPRO RESPONSE", "parseBtData() ContPack packNo: " + currPackNo + ", dataLen: " + dataLen + ", commandId: " + lastCommandId + ", packBuffer.remaining: " + packBuffer.remaining());

                        if (packBuffer.remaining() <= 0) {
                            //all data received
                            parseBtResponsePack();
                            if (btActionHelper != null) btActionHelper.resetGattInProgress();
                        }
                    } else {
                        //Log.e("parseBtData", "Cont pack buffer was null");
                        if (btActionHelper != null) btActionHelper.resetGattInProgress();
                    }
                } else if (packBuffer != null) {
                    Log.e("parseBtData", "Invalid ContPack pack number received! lastPackNo: " + lastPackNo + ", currPackNo: " + currPackNo + " for commandId: " + lastCommandId);
                    packBuffer = null;
                }
            } else {
                GoHeader header = new GoHeader(valueBytes);

                int headerLength = header.getHeaderLength();
                int msgLen = header.getMsgLength();
                int commandId = valueBytes[headerLength];
                int error = valueBytes[headerLength + 1];
                int dataLen = valueBytes.length - headerLength;

                if (packBuffer != null && packBuffer.remaining() > 0) {
                    // handle unhandled data
                    Log.e("parseBtData", String.format("New Response with command ID 0x%02X received while awaiting contPack data!", commandId));
                }

                packBuffer = ByteBuffer.allocate(msgLen);
                int putLen = Math.min(packBuffer.remaining(), dataLen);
                if (putLen != dataLen)
                    Log.e("parseBtData", "Received data length is " + (dataLen - putLen) + " larger than the buffer");
                packBuffer.put(valueBytes, headerLength, putLen);

                lastCommandId = commandId;
                lastPackNo = -1;
                // Log.e("GOPRO RESPONSE", "parseBtData() msgLen: " + msgLen + ", dataLen: " + dataLen + ", commandId: " + commandId + ", error: " + error + ", packBuffer.remaining: " + packBuffer.remaining());

                if (packBuffer.remaining() <= 0) {
                    //all data received
                    parseBtResponsePack();

                    if (btActionHelper != null) btActionHelper.resetGattInProgress();

                    if (error == 0) {
                        switch (characteristicUuid) {
                            case commandRespUUID:
                                if (commandId == 0x0d) {
                                    showToast(_context.getResources().getString(R.string.str_set_dt_success), Toast.LENGTH_SHORT);
                                    getUTCDateTime();
                                } else if (commandId == 0x0E && valueBytes.length >= 11) {
                                    // UTC DateTime received
                                    int year = ((valueBytes[4] & 0xFF) << 8) | (valueBytes[5] & 0xFF);
                                    int month = valueBytes[6];
                                    int day = valueBytes[7];
                                    int hour = valueBytes[8];
                                    int minute = valueBytes[9];
                                    int second = valueBytes[10];

                                    deviceTime = Calendar.getInstance();
                                    deviceTime.set(year, month - 1, day, hour, minute, second);

                                    // Check whether the difference from the current time is 15 seconds or more
                                    Calendar currentTime = Calendar.getInstance();
                                    long timeDifference = Math.abs(currentTime.getTimeInMillis() - deviceTime.getTimeInMillis());
                                    if (timeDifference >= 15000) {
                                        setUTCDateTime();
                                        showToast(String.format(_context.getResources().getString(R.string.str_clock_is_wrong), displayName), Toast.LENGTH_LONG);
                                    }

                                    // Update the device time continuously
                                    deviceTimeUpdateHandler.removeCallbacksAndMessages(null);
                                    deviceTimeUpdateHandler.post(timeUpdater);
                                } else if (commandId == 0x17) {
                                    // AP Setting written
                                    queryAllStatusValues();
                                }
                                break;
                            case settingsRespUUID:
                                if (!((valueBytes.length >= 4 && valueBytes[3] == 91) || (valueBytes.length >= 3 && valueBytes[1] == 91))) {
                                    // A setting was changed. Get an update of the current settings
                                    if (providesAvailableOptions) {
                                        getAvailableOptions();
                                    } else {
                                        getAllSettings();
                                    }
                                }
                                break;
                            case queryRespUUID:
                                if (valueBytes.length >= 5) {
                                    handleQueryResponse(valueBytes);
                                } else if (commandId == 50) {
                                    if (valueBytes.length == 3 && valueBytes[2] == 0) {
                                        // GoPro does not provide available setting options
                                        providesAvailableOptions = false;
                                        Log.i("GoProDevice", "'" + btDeviceName + "', model '" + modelName + "' does not provide the available setting options!");
                                    }
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
                        Log.e("GoProDevice", btDeviceName + " responds with error code " + error + " (" + LE + ") to command id " + commandId);
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
                    saveNewDisplayName(wifiSSID);
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
        }

        doIfBtConnected();

        if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
    }

    private boolean isProtobuf(byte[] response) {
        if (response.length > 2) {
            int msgFeatureID = response[0] & 0xff;
            int msgActionID = response[1] & 0xff;

            for (int i1 = 0; i1 < featureIDs.length; i1++) {
                int currentFeatureID = featureIDs[i1];
                int[] currentActionIDs = actionIDs[i1];

                if (msgFeatureID == currentFeatureID) {
                    for (int currentActionID : currentActionIDs) {
                        if (msgActionID == currentActionID) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
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
        } else if (commandId == (byte) 0x13 && id1 == 70) { // Battery level
            remainingBatteryPercent = valueBytes[5];
        } else if (commandId == (byte) 0x13 && id1 == 43 && len1 == 1) { // Current mode
            mode = new GoMode(_context, valueBytes[5]);
        } else if (commandId == (byte) 0x13 && id1 == 97 && len1 == 4) { // Current preset
            ByteBuffer buffer = ByteBuffer.allocate(len1);
            buffer.put(valueBytes, 5, len1);
            buffer.flip();
            int presetID = buffer.getInt();

            preset = new GoPreset(_context, presetID);
            for (GoProtoPreset goProtoPreset : goProtoPresets) {
                if (goProtoPreset.getId() == presetID)
                    protoPreset = goProtoPreset;
            }
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

    private void sendBtPairComplete() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            //send to GP-0091: {msg_len, 0x03, 0x01, 0x08, 0x00, 0x12, dev_name_len, dev_name}
            byte[] msg = {0x0f, 0x03, 0x01, 0x08, 0x00, 0x12, 0x09, 'G', 'o', 'E', 'a', 's', 'y', 'P', 'r', 'o'};
            if (nwManCmdCharacteristic != null && nwManCmdCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(nwManCmdCharacteristic)) {
                freshPaired = false;
            } else {
                Log.e("sendBtPairComplete", "not sent");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }

    private void handleModelId() {
        if (modelID >= HERO11_BLACK) {
            // Hero 11+
            keepAliveInterval = 3000;
            keepAlive_msg = keepAlive_v2;
            startStream_query = streamStart_query_v3;
            stopStream_query = streamStop_query_v2;
            getMediaList_query = mediaList_query_v2;
            getThumbNail_query = thumbNail_query_v2;
        } else if (modelID >= HERO9_BLACK) {
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

        if (modelID > UNK_MODEL && freshPaired) {
            sendBtPairComplete();
        }

        // getSettingsJson(); // Comment line out -> Only used to get Settings-JSON from new Models
        queryAllStatusValues();
        getAvailableOptions();
    }

    private void parseBtResponsePack() {
        byte[] byteArray = packBuffer.array();
        packBuffer.clear();
        packBuffer = null;

        if (isProtobuf(byteArray)) {
            // TODO parse protobuf response
            // int msgFeatureID = valueBytes[0] & 0xff;
            int responseActionID = byteArray[1] & 0xff;

            Log.d("parseBtData", "Protobuf msg received with responseActionID: " + responseActionID);

            switch (responseActionID) {
                // Network Management Feature 0x02
                case 11:
                    // Handle case 0x0B
                    // Async status update response
                    break;
                case 12:
                    // Handle case 0x0C
                    // Async status update response
                    break;
                case 130:
                    // Handle case 0x82
                    // Start scan response
                    break;
                case 131:
                    // Handle case 0x83
                    // Get AP entries response
                    break;
                case 132:
                    // Handle case 0x84
                    // Connect AP response
                    break;
                case 133:
                    // Handle case 0x85
                    // Connect new AP response
                    break;

                // Command Feature 0xF1
                case 233:
                    // Handle case 0xE9
                    // Request set camera control status response
                    break;
                case 235:
                    // Handle case 0xEB
                    // Request set turbo active response

                    // Implemented and it works
                    break;
                case 249:
                    // Handle case 0xF9
                    // Request set live stream response
                    break;

                // Query Feature 0xF5
                case 242: // Handle case 0xF2 (Request get preset status response)
                case 243: // Handle case 0xF3 (Async status update response)
                    handlePresetStatusResponse(byteArray);
                    break;
                case 244:
                    // Handle case 0xF4
                    // Request get live stream status response
                    break;
                case 245:
                    // Handle case 0xF5
                    // Async status update response
                    break;
                default:
                    break;
            }
        } else {
            int commandId = byteArray[0] & 0xFF;
            int error = byteArray[1];
            int nextStart = 2;

            if (error == 0) { // success
                ByteBuffer byteBuffer;
                int nextLen;

                switch (commandId) {
                    case 83:
                        // response for registerForAllStatusValueUpdates()
                        autoValueUpdatesRegistered = true;

                        for (int index = nextStart; index < byteArray.length; ) {
                            int statusID = byteArray[index] & 0xff;
                            if (byteArray.length > index + 1) nextLen = byteArray[index + 1];
                            else break;

                            if (nextLen > 0) {
                                byteBuffer = ByteBuffer.allocate(nextLen);
                                byteBuffer.put(byteArray, index + 2, nextLen);
                                handleStatusData(statusID, byteBuffer);
                            }

                            index += nextLen + 2;
                        }
                        break;
                    case 60:
                        // Hardware info -> Model ID, model name, board type, firmware version, serial number, AP SSID, AP MAC Address
                        nextLen = byteArray[nextStart];

                        // Model ID
                        byteBuffer = ByteBuffer.allocate(nextLen);
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
                        sharedPreferences.edit().putString("model_name_" + btDeviceName, modelName).apply();

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
                        fwVersion = new String(byteBuffer.array(), StandardCharsets.UTF_8).trim();

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
                        saveNewDisplayName(wifiSSID);

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
                        break;
                    case 147: // Why does Hero12 send 147 instead of 19?
                    case 19:
                        // All status values
                        for (int index = nextStart; index < byteArray.length; ) {
                            int statusID = byteArray[index] & 0xff;
                            if (byteArray.length > index + 1) nextLen = byteArray[index + 1];
                            else break;

                            if (nextLen > 0) {
                                byteBuffer = ByteBuffer.allocate(nextLen);
                                byteBuffer.put(byteArray, index + 2, nextLen);
                                handleStatusData(statusID, byteBuffer);
                            }

                            index += nextLen + 2;
                        }
                        break;
                    case 162:
                    case 50:
                        // Available option IDs for all settings
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
                        break;
                    case 146:
                    case 18:
                        // All settings
                        goSettings = new ArrayList<>();

                        for (int index = nextStart; index < byteArray.length; ) {
                            int settingID = byteArray[index] & 0xff;
                            if (byteArray.length > index + 1) {
                                nextLen = byteArray[index + 1];
                                if (byteArray.length < index + 2 + nextLen) {
                                    //nextLen = byteArray.length - index - 2;
                                    break;
                                }
                            } else {
                                break;
                            }

                            if (nextLen > 0) {
                                byteBuffer = ByteBuffer.allocate(nextLen);
                                byteBuffer.put(byteArray, index + 2, nextLen);
                                handleSettingData(settingID, byteBuffer);
                            }

                            index += nextLen + 2;
                        }

                        goSettings.sort(Comparator.comparing(GoSetting::getGroupName));
                        goSettings.sort(Comparator.comparingInt(GoSetting::getSettingId));

                        if (settingsChangedCallback != null)
                            settingsChangedCallback.onSettingsChanged();
                        break;
                    case 59:
                        // Settings JSON gzip
                        byteBuffer = ByteBuffer.allocate(byteArray.length - 2);
                        byteBuffer.put(byteArray, 2, byteArray.length - 2);
                        byteBuffer.flip();

                        String json_str = decompress(byteBuffer.array());
                        Log.e("JSON", json_str);
                        break;
                }
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
                Log.e("GoProDevice", btDeviceName + " responds with error code " + error + " (" + LE + ") to command id " + commandId);
            }
        }
    }

    private void handlePresetStatusResponse(byte[] byteArray) {
        ArrayList<GoProtoPreset> newGoProtoPresets = new ArrayList<>();
        try {
            PresetStatus.NotifyPresetStatus notifyPresetStatus = PresetStatus.NotifyPresetStatus.parseFrom(byteArray);
            List<PresetStatus.PresetGroup> presetGroupArrayList = notifyPresetStatus.getPresetGroupArrayList();
            PresetStatus.PresetGroup notGroupedPresetGroup = PresetStatus.PresetGroup.parseFrom(byteArray);

            for (PresetStatus.PresetGroup presetGroup : presetGroupArrayList) {
                List<PresetStatus.Preset> presetList = presetGroup.getPresetArrayList();
                for (PresetStatus.Preset preset : presetList) {
                    GoProtoPreset goProtoPreset = new GoProtoPreset(_context, preset, _application);
                    if (goProtoPreset.isValid()) newGoProtoPresets.add(goProtoPreset);
                }
            }

            List<PresetStatus.Preset> notGroupedPresets = notGroupedPresetGroup.getPresetArrayList();
            for (PresetStatus.Preset notGroupedPreset : notGroupedPresets) {
                GoProtoPreset goProtoPreset = new GoProtoPreset(_context, notGroupedPreset, _application);
                if (goProtoPreset.isValid()) newGoProtoPresets.add(goProtoPreset);
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (newGoProtoPresets.size() > 0) {
            if (goProtoPresets.size() <= 0) {
                goProtoPresets = newGoProtoPresets;
            } else {
                // replace by id
                for (GoProtoPreset newPreset : newGoProtoPresets) {
                    for (GoProtoPreset oldPreset : goProtoPresets) {
                        if (newPreset.getId() == oldPreset.getId()) {
                            oldPreset.setSettingsFromNewPreset(newPreset);
                        }
                    }
                }
            }
        }
    }

    public boolean hasProtoPresets() {
        return goProtoPresets.size() > 0;
    }

    public ArrayList<GoProtoPreset> getGoProtoPresets() {
        return goProtoPresets;
    }

    private static String decompress(byte[] compressed) {
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

    private void handleStatusData(int statusID, ByteBuffer buffer) {
        switch (statusID) {
            case 2:
                isCharging = buffer.get(0) == 4;
                break;
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
                    ShutterLog.logShutter(_context, displayName, "started");
                    initShutterWatchdog();
                } else if (!isRecording && wasRecording) {
                    ShutterLog.logShutter(_context, displayName, "stopped");
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
                int presetID = buffer.getInt();
                preset = new GoPreset(_context, presetID);
                for (GoProtoPreset goProtoPreset : goProtoPresets) {
                    if (goProtoPreset.getId() == presetID)
                        protoPreset = goProtoPreset;
                }
                break;
            case 114: // Camera control status ID as integer
                // 0: Camera Idle: No one is attempting to change camera settings
                // 1: Camera Control: Camera is in a menu or changing settings. To intervene, app must request control
                // 2: Camera External Control: An outside entity (app) has control and is in a menu or modifying settings
                int ccStatus = buffer.get(0);
                if (ccStatus == 0) {
                    getCurrentPreset();
                }
                break;
            /*default:
                Log.e("handleStatusData", String.format("unhandled statusID 0x%02X received", statusID));*/
        }
    }

    private void handleSettingData(int settingID, ByteBuffer buffer) {
        GoSetting goSetting = new GoSetting(_application, settingID, buffer.get(0), availableSettingsOptions);

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
            if (retryConnectBt != null) {
                retryConnectBt.cancel();
                retryConnectBt.purge();
            }

            //start timed actions
            initTimedActions();
            initExecWatchdog();

            requestGetPresetStatus();

            if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
        }
    }

    //region BT Communication tools
    private void requestHighPriorityConnection() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.getClass().getMethod("requestConnectionPriority", Integer.TYPE).invoke(bluetoothGatt, 1);
            } catch (Exception e) {
                Log.e("requestHighPriorityConnection", "Failed!");
            }
        }
    }

    public void disconnectGoProDevice() {
        _disconnectGoProDevice(false);
    }

    public void disconnectGoProDeviceByUser() {
        _disconnectGoProDevice(true);
    }

    private void _disconnectGoProDevice(boolean withoutReconnect) {
        Log.d("disconnectBt", "Disconnect forced");
        this.shouldNotBeReconnected = withoutReconnect;
        if (isWifiConnected()) wiFiHelper.disconnectWifi();

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

        deviceTimeUpdateHandler.removeCallbacksAndMessages(null);

        setDisconnected();
    }

    private void setDisconnected() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        execWatchdogTimer.cancel();
        execWatchdogTimer.purge();

        actionTimer.cancel();
        actionTimer.purge();

        if (btActionHelper != null) {
            btActionHelper.release();
            btActionHelper = null;
        }

        if (packBuffer != null) {
            packBuffer.clear();
            packBuffer = null;
        }

        btConnectionStage = BT_NOT_CONNECTED;
        autoValueUpdatesRegistered = false;
        communicationInitiated = false;
        camBtAvailable = false;
        registerForAutoValueUpdatesCounter = 0;
        goProtoPresets = new ArrayList<>();

        wifiApState = -1;
        isHot = false;
        isCold = false;

        if (dataChangedCallback != null) dataChangedCallback.onDataChanged();
    }

    private void startCrcNotification() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (commandRespCharacteristic == null || bluetoothGatt == null) {
                Log.e("startCrcNotification", "Error 4");
                showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                disconnectGoProDevice();
                return;
            }

            if (bluetoothGatt.setCharacteristicNotification(commandRespCharacteristic, true)) {
                BluetoothGattDescriptor descriptor = commandRespCharacteristic.getDescriptors().get(0);
                if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Log.e("startCrcNotification", "Error 3");
                        showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                        disconnectGoProDevice();
                    }
                } else {
                    Log.e("startCrcNotification", "Error 2");
                    if (btActionHelper != null) {
                        btActionHelper.resetGattInProgress();
                    }
                }
            } else {
                Log.e("startCrcNotification", "Error 1");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }

    private void startSrcNotification() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (settingsRespCharacteristic == null || bluetoothGatt == null) {
                Log.e("startSrcNotification", "Error 4");
                showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                disconnectGoProDevice();
                return;
            }

            if (bluetoothGatt.setCharacteristicNotification(settingsRespCharacteristic, true)) {
                BluetoothGattDescriptor descriptor = settingsRespCharacteristic.getDescriptors().get(0);
                if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Log.e("startSrcNotification", "Error 3");
                        showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                        disconnectGoProDevice();
                    }
                } else {
                    Log.e("startSrcNotification", "Error 2");
                    if (btActionHelper != null) {
                        btActionHelper.resetGattInProgress();
                    }
                }
            } else {
                Log.e("startSrcNotification", "Error 1");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }

    private void startQrcNotification() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (queryRespCharacteristic == null || bluetoothGatt == null) {
                Log.e("startQrcNotification", "Error 4");
                showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                disconnectGoProDevice();
                return;
            }

            if (bluetoothGatt.setCharacteristicNotification(queryRespCharacteristic, true)) {
                BluetoothGattDescriptor descriptor = queryRespCharacteristic.getDescriptors().get(0);
                if (descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    if (!bluetoothGatt.writeDescriptor(descriptor)) {
                        Log.e("startQrcNotification", "Error 3");
                        showToast(String.format(res.getString(R.string.str_connect_cam_problem), displayName), Toast.LENGTH_SHORT);
                        disconnectGoProDevice();
                    }
                } else {
                    Log.e("startQrcNotification", "Error 2");
                    if (btActionHelper != null) {
                        btActionHelper.resetGattInProgress();
                    }
                }
            } else {
                Log.e("startQrcNotification", "Error 1");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }

    public void setNewCamName(String newName) {
        if (wiFiHelper != null) {
            wiFiHelper.disconnectWifi();
        }
        wifiApOff();

        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            byte[] msg = newName.getBytes(StandardCharsets.UTF_8);

            if (!(wifiSsidCharacteristic != null && wifiSsidCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(wifiSsidCharacteristic))) {
                Log.e("setCamName", "not sent");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });

        // The password must then also be written so that the camera accepts the new SSID
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            byte[] msg = wifiPSK.getBytes(StandardCharsets.UTF_8);

            if (!(wifiPwCharacteristic != null && wifiPwCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(wifiPwCharacteristic))) {
                Log.e("setCamName", "not sent");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }
    //endregion

    //region BT Readings

    private void readWifiApSsid() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (bluetoothGatt == null || wifiSsidCharacteristic == null || btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiSsidCharacteristic)) {
                Log.e("readWifiApSsid", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void readWifiApPw() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (bluetoothGatt == null || wifiPwCharacteristic == null || btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiPwCharacteristic)) {
                Log.e("readWifiApPw", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void readWifiApState() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (bluetoothGatt == null || wifiStateCharacteristic == null || btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }
            if (!bluetoothGatt.readCharacteristic(wifiStateCharacteristic)) {
                Log.e("readWifiApState", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void readBtRssi() {
        if (btActionHelper != null && !((MyApplication) _application).isAppPaused())
            btActionHelper.queueActionIfNotQueued(() -> {
                if (bluetoothGatt == null || btConnectionStage < 2) {
                    if (btActionHelper != null) {
                        btActionHelper.resetGattInProgress();
                    }
                    return;
                }
                if (!bluetoothGatt.readRemoteRssi()) {
                    if (btActionHelper != null) {
                        btActionHelper.resetGattInProgress();
                    }
                }
            });
    }
    //endregion

    //region BT Settings (0x0074)

    private void keepAlive() {
        if (btActionHelper != null) btActionHelper.queueActionIfNotQueued(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            if (modelID > HERO8_BLACK) {
                byte[] msg = {0x03, 0x5B, 0x01, 0x42};
                if (settingsCharacteristic == null || !settingsCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(settingsCharacteristic)) {
                    if (btActionHelper != null) {
                        btActionHelper.resetGattInProgress();
                    }
                } else {
                    lastKeepAlive = new Date();
                }
            } else {
                // TODO another way to keep alive for hero 8 and downwards?
                // Even the GoPro app writes 03 5b 01 42 as Keep Alive and gets an error back from the GoPro
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void setSetting(int settingId, int optionId) {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x03, (byte) settingId, 0x01, (byte) optionId};
            if (settingsCharacteristic == null || !settingsCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(settingsCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }
    //endregion

    //region BT Commands (0x0072)

    public void getHardwareInfo() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x01, 0x3C};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void getUTCDateTime() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x01, 0x0E};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void setUTCDateTime() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            Calendar currentTime = Calendar.getInstance();

            int year = currentTime.get(Calendar.YEAR);
            int month = currentTime.get(Calendar.MONTH) + 1; // Month is 0 indexed
            int day = currentTime.get(Calendar.DAY_OF_MONTH);
            int hour = currentTime.get(Calendar.HOUR_OF_DAY);
            int minute = currentTime.get(Calendar.MINUTE);
            int second = currentTime.get(Calendar.SECOND);

            byte yyH = (byte) ((year >> 8) & 0xFF);
            byte yyL = (byte) (year & 0xFF);
            byte[] msg = {0x09, 0x0d, 0x07, yyH, yyL, (byte) month, (byte) day, (byte) hour, (byte) minute, (byte) second};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void setSubMode(int mode, int subMode) {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x05, 0x03, 0x01, (byte) mode, 0x01, (byte) subMode};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
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

    /*public void getSettingsJson() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x01, 0x3B};
            if (commandCharacteristic != null && commandCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                Log.d("JSON", "getSettingsJson() sent");
            } else {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }*/

    public void shutterOn() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] shutterOn = {0x03, 0x01, 0x01, 0x01};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(shutterOn) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void shutterOff() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] shutterOff = {0x03, 0x01, 0x01, 0x00};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(shutterOff) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void sleep() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] camSleep = {0x01, 0x05};
            if (commandCharacteristic != null && commandCharacteristic.setValue(camSleep) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                connectBtCallback = null;
                disconnectGoProDeviceByUser();
            } else {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void wifiApOn() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }
            byte[] wifiApOn = {0x03, 0x17, 0x01, 0x01};
            if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOn) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (!autoValueUpdatesRegistered) readWifiApState();
            } else {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void wifiApOff() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }
            if (isWifiConnected()) wiFiHelper.disconnectWifi();

            byte[] wifiApOff = {0x03, 0x17, 0x01, 0x00};
            if (commandCharacteristic != null && commandCharacteristic.setValue(wifiApOff) && bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (!autoValueUpdatesRegistered) readWifiApState();
            } else {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void locateOn() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x03, 0x16, 0x01, 0x01};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void locateOff() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x03, 0x16, 0x01, 0x00};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void highlight() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] highlight = {0x01, 0x18};
            if (commandCharacteristic == null || !commandCharacteristic.setValue(highlight) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void setPreset(String presetName) {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] cmd = presetCmds.getOrDefault(presetName, new byte[]{0x06, 0x40, 0x04, 0x00, 0x00, 0x00, 0x00}); // "standard" as default
            if (commandCharacteristic == null || !commandCharacteristic.setValue(cmd) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
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

    public void setPresetById(int id) {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            ByteBuffer buffer = ByteBuffer.allocate(4);
            buffer.putInt(id);
            byte[] idBytes = buffer.array();

            byte[] cmd = new byte[]{(byte) (idBytes.length + 2), 0x40, 0x04};

            byte[] combined = new byte[cmd.length + idBytes.length];
            System.arraycopy(cmd, 0, combined, 0, cmd.length);
            System.arraycopy(idBytes, 0, combined, cmd.length, idBytes.length);

            if (commandCharacteristic == null || !commandCharacteristic.setValue(combined) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
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
    //endregion

    //region BT Queries (0x0076)

    private void registerForAllStatusValueUpdates() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x01, 0x53 /* 83 */};

            if (queryCharacteristic == null || !queryCharacteristic.setValue(msg) || !bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                Log.e("registerForAllStatusValueUpdates", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    public void queryAllStatusValues() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x01, 0x13};

            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                Log.e("getAllStatusValues", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void getAvailableOptions() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x01, 0x32};

            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastSettingUpdate = new Date();
            } else {
                Log.e("getAvailableOptions", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void getAllSettings() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x01, 0x12};

            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastSettingUpdate = new Date();
            } else {
                Log.e("getAllSettings", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void getMemory() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x02, 0x13, 0x36};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                Log.e("getMemory", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void getBatteryLevel() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x02, 0x13, 0x46};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastMemoryQuery = new Date();
            } else {
                Log.e("getBatteryLevel", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void getCurrentPreset() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x02, 0x13, 0x61};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastOtherQueries = new Date();
            } else {
                Log.e("getPreset", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }


    private void getCurrentMode() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] msg = {0x02, 0x13, 43};
            if (queryCharacteristic != null && queryCharacteristic.setValue(msg) && bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                lastOtherQueries = new Date();
            } else {
                Log.e("getMode", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }
    //endregion

    //region Protobuf
    public void requestSetTurboActive(boolean active) {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }

            byte[] proto = new TurboTransfer.RequestSetTurboActive.Builder().setActive(active).build().toByteArray();

            byte[] cmd = {(byte) (proto.length + 2), (byte) 0xF1, 0x6B};

            byte[] combined = new byte[cmd.length + proto.length];
            System.arraycopy(cmd, 0, combined, 0, cmd.length);
            System.arraycopy(proto, 0, combined, cmd.length, proto.length);

            if (commandCharacteristic == null || !commandCharacteristic.setValue(combined) || !bluetoothGatt.writeCharacteristic(commandCharacteristic)) {
                Log.e("requestSetTurboActive", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }

    private void requestGetPresetStatus() {
        if (btActionHelper != null) btActionHelper.queueAction(() -> {
            if (btConnectionStage < 2) {
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
                return;
            }
            byte[] proto = new RequestGetPresetStatusOuterClass.RequestGetPresetStatus.Builder()
                    .addRegisterPresetStatus(RequestGetPresetStatusOuterClass.EnumRegisterPresetStatus.REGISTER_PRESET_STATUS_PRESET)
                    .addRegisterPresetStatus(RequestGetPresetStatusOuterClass.EnumRegisterPresetStatus.REGISTER_PRESET_STATUS_PRESET_GROUP_ARRAY)
                    .build()
                    .toByteArray();

            byte[] cmd = {(byte) (proto.length + 2), (byte) 0xF5, 0x72};

            byte[] combined = new byte[cmd.length + proto.length];
            System.arraycopy(cmd, 0, combined, 0, cmd.length);
            System.arraycopy(proto, 0, combined, cmd.length, proto.length);

            if (queryCharacteristic == null || !queryCharacteristic.setValue(combined) || !bluetoothGatt.writeCharacteristic(queryCharacteristic)) {
                Log.e("notifyPresetStatus", "failed action");
                if (btActionHelper != null) {
                    btActionHelper.resetGattInProgress();
                }
            }
        });
    }
    //endregion

    //region Callback Interfaces
    public interface PairCallbackInterface {
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

    public interface ConnectBtCallbackInterface {
        void onBtConnectionStateChange(boolean connected);
    }

    private ConnectBtCallbackInterface connectBtCallback;
    private Timer checkConnected = null;
    private Timer retryConnectBt = null;


    public void connectBt(ConnectBtCallbackInterface _connectBtCallback) {
        shouldNotBeReconnected = false;
        connectBtCallback = _connectBtCallback;
        bluetoothGatt = bluetoothDevice.connectGatt(_context, false, gattCallback);
        if (bluetoothGatt != null) {
            btConnectionStage = BT_CONNECTING;

            if (dataChangedCallback != null) dataChangedCallback.onDataChanged();

            // Timeout retryConnectBt
            retryConnectBt = new Timer();
            retryConnectBt.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (bluetoothGatt != null && btConnectionStage != BT_CONNECTED) {
                        Log.e("connectBt", "retryConnectBt");
                        btRetryConnect = true;
                        bluetoothGatt.disconnect();
                        bluetoothGatt = bluetoothDevice.connectGatt(_context, false, gattCallback);
                    }
                }
            }, 10000);

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

    public interface DataChangedCallbackInterface {
        void onDataChanged();
    }

    private DataChangedCallbackInterface dataChangedCallback;

    public void getDataChanges(DataChangedCallbackInterface _dataChangedCallback) {
        dataChangedCallback = _dataChangedCallback;
    }

    public interface SettingsChangedCallbackInterface {
        void onSettingsChanged();
    }

    private SettingsChangedCallbackInterface settingsChangedCallback;

    public void getSettingsChanges(SettingsChangedCallbackInterface _settingsChangedCallback) {
        settingsChangedCallback = _settingsChangedCallback;
    }

    public interface WifiConnectionChangedInterface {
        void onWifiConnectionChanged();
    }

    public void connectWifi(WifiConnectionChangedInterface _wifiConnectionChangedCallback) {
        if (wifiApState != 1 && !isWifiConnected()) {
            wifiApOn();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            wiFiHelper = new WiFiHelper(_context, _wifiConnectionChangedCallback);
        } catch (Exception e) {
            e.printStackTrace();
            showToast("WiFi error: " + e.getMessage(), Toast.LENGTH_SHORT);
            return;
        }

        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                wiFiHelper.connectWifi(wifiSSID, wifiPSK, wifiBSSID);
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Error connectWifi!", Toast.LENGTH_SHORT);
            }
        }).start();
    }

    public boolean isWifiConnected() {
        return wiFiHelper != null && wiFiHelper.isConnected();
    }

    //endregion

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

    //region Helpers
    private void initExecWatchdog() {
        execWatchdogTimer.cancel();
        execWatchdogTimer.purge();

        TimerTask timedExecWatchdog = new TimerTask() {
            @Override
            public void run() {
                if (btConnectionStage < 2 || btActionHelper == null) {
                    return;
                }

                if (System.currentTimeMillis() - btActionHelper.getLastExecMillis() >= 10000 && btActionHelper.isGattInProgress()) {
                    Log.e("ExecWatchdog", "'Gatt in progress' timeout triggered!");
                    btActionHelper.resetGattInProgress();
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

                long now = new Date().getTime();

                // Send "keep alive" every 60 or 3 seconds
                if (now - lastKeepAlive.getTime() > keepAliveInterval) {
                    if (!isRecording && (!((MyApplication) _application).isAppPaused() || ((MyApplication) _application).shouldKeptAliveWhenPaused()))
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
                        getCurrentPreset();
                        getCurrentMode();
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
