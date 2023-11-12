package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;
import static com.sepp89117.goeasypro_android.gopro.GoProDevice.HERO12_BLACK;
import static com.sepp89117.goeasypro_android.gopro.GoProDevice.HERO8_BLACK;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;
import com.sepp89117.goeasypro_android.adapters.GoListAdapter;
import com.sepp89117.goeasypro_android.gopro.GoMediaFile;
import com.sepp89117.goeasypro_android.gopro.GoProDevice;
import com.sepp89117.goeasypro_android.gopro.GoProtoPreset;
import com.sepp89117.goeasypro_android.gopro.GoSetting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import me.saket.cascade.CascadePopupMenu;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


@RequiresApi(api = Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<GoProDevice> goProDevices = new ArrayList<>();
    private GoListAdapter goListAdapter;
    private ListView goListView;
    private View mLayout;
    private final OkHttpClient httpClient = new OkHttpClient();
    private int lastCamClickedIndex = -1;
    private final Map<Integer, String> firmwareCatalog = new HashMap<>();

    private static final int BT_PERMISSIONS_CODE = 87;
    private static final int WIFI_PERMISSIONS_CODE = 41;
    private static final String[] BT_PERMISSIONS_S_UP = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
    private static final String[] BT_PERMISSIONS_R_DOWN = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
    private static final String[] WIFI_PERMISSIONS_Q_UP = {Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.CHANGE_NETWORK_STATE};
    private static final String[] WIFI_PERMISSIONS_P_DOWN = {Manifest.permission.INTERNET, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLayout = findViewById(R.id.main_layout);

        //get bluetoothManager and bluetoothAdapter
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, getResources().getString(R.string.str_no_bt), Toast.LENGTH_LONG).show();

            this.finish();
            return;
        }

        ((MyApplication) this.getApplication()).setBluetoothAdapter(bluetoothAdapter);
        ((MyApplication) this.getApplication()).setGoProDevices(goProDevices);

        goListAdapter = new GoListAdapter(goProDevices, this);

        goListView = findViewById(R.id.goListView);
        goListView.setAdapter(goListAdapter);
        goListView.setOnItemClickListener((parent, view, position, id) -> {
            lastCamClickedIndex = position;

            CascadePopupMenu popupMenu = new CascadePopupMenu(MainActivity.this, view, Gravity.BOTTOM);
            popupMenu.setOnMenuItemClickListener(camMenuItemClickListener);

            if (goProDevices.get(lastCamClickedIndex).btConnectionStage == BT_CONNECTED) {
                popupMenu.inflate(R.menu.connected_dev_menu);

                setControlMenuDependingOnModelID(popupMenu);
            } else if (goProDevices.get(lastCamClickedIndex).btConnectionStage == BT_NOT_CONNECTED) {
                popupMenu.inflate(R.menu.not_connected_dev_menu);
            } else {
                Toast.makeText(MainActivity.this, getResources().getString(R.string.str_wait_bt_con), Toast.LENGTH_SHORT).show();
                return;
            }

            popupMenu.show();
        });

        //request maybe bluetooth permission
        if (!hasBtPermissions()) {
            requestBtPermissions();
            return;
        }

        if (isBtEnabled()) {
            startApp();
        }

        setOnDataChanged();

        Log.i("MainActivity", "onCreate()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        goListView.setAdapter(goListAdapter);
        setOnDataChanged();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
        if (hasBtPermissions())
            bluetoothAdapter.startDiscovery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (hasBtPermissions())
            bluetoothAdapter.cancelDiscovery();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage != BT_NOT_CONNECTED)
                goProDevices.get(i).disconnectGoProDevice();
        }
        try {
            unregisterReceiver(broadcastReceiver);
        } catch (Exception ignored) {

        }

        Log.i("MainActivity", "onDestroy()");
    }

    private final PopupMenu.OnMenuItemClickListener camMenuItemClickListener = new PopupMenu.OnMenuItemClickListener() {
        @SuppressLint("NonConstantResourceId")
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (item.hasSubMenu()) return true;

            int position = lastCamClickedIndex;
            GoProDevice goProDevice = goProDevices.get(position);
            ((MyApplication) MainActivity.this.getApplication()).setFocusedDevice(goProDevice);

            if(item.getGroupId() <= 0) {
                switch (item.getItemId()) {
                    case R.id.dev_settings:
                        if (goProDevice.goSettings.size() > 0) {
                            Intent goSettingsActivityIntent = new Intent(MainActivity.this, GoSettingsActivity.class);
                            startActivity(goSettingsActivityIntent);
                        } else {
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Settings are currently not available", Toast.LENGTH_SHORT).show());
                        }
                        break;
                    case R.id.sync_settings:
                        if (goProDevices.size() > 1) {
                            final AlertDialog alert = new AlertDialog.Builder(MainActivity.this).setTitle("Sync to " + goProDevice.displayName).setMessage("Please wait until all cameras have been synchronized!").setCancelable(false).create();

                            alert.show();

                            ArrayList<GoSetting> goSettings = goProDevice.goSettings;
                            for (GoSetting goSetting : goSettings) {
                                int devIndex = goProDevices.indexOf(goProDevice);
                                int settingId = goSetting.getSettingId();
                                int currentOptionId = goSetting.getCurrentOptionId();

                                for (int i = 0; i < goProDevices.size(); i++) {
                                    if (i == devIndex) continue;

                                    GoProDevice dev = goProDevices.get(i);
                                    if (dev.btConnectionStage == BT_CONNECTED)
                                        dev.setSetting(settingId, currentOptionId);
                                }
                            }

                            alert.dismiss();
                        }
                        break;
                    case R.id.dev_rename:
                        final EditText name_input = new EditText(MainActivity.this);
                        name_input.setText(goProDevice.displayName);
                        name_input.requestFocus();
                        name_input.selectAll();
                        name_input.setFilters(new InputFilter[]{new ssidInputFilter()});

                        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

                        final AlertDialog devRenameAlert = new AlertDialog.Builder(MainActivity.this).setTitle(String.format(getResources().getString(R.string.str_Rename), goProDevice.displayName)).setMessage(String.format(getResources().getString(R.string.str_enter_new_name), goProDevice.btDeviceName)).setView(name_input).setPositiveButton(getResources().getString(R.string.str_OK), (dialog, which) -> {
                            inputMethodManager.hideSoftInputFromWindow(name_input.getWindowToken(), 0);
                            String newName = name_input.getText().toString();
                            if (newName.length() >= 8)
                                goProDevice.setNewCamName(newName);
                            else
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "At least 8 characters are required!", Toast.LENGTH_SHORT).show());
                        }).setNegativeButton(getResources().getString(R.string.str_Cancel), (dialog, which) -> {
                            inputMethodManager.hideSoftInputFromWindow(name_input.getWindowToken(), 0);
                            dialog.cancel();
                        }).setCancelable(true).create();

                        devRenameAlert.show();
                        break;
                    case R.id.dev_info:
                        SimpleDateFormat sdf = ((SimpleDateFormat) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG, Locale.getDefault()));
                        String localTimeAsString = sdf.format(goProDevice.deviceTime.getTime());

                        String sb = "Device name: " + goProDevice.btDeviceName + "\n" + "Display name: " + goProDevice.displayName + "\n" + "Model name: " + goProDevice.modelName + "\n" + "Model ID: " + goProDevice.modelID + "\n" + "\n" + "Board type: " + goProDevice.boardType + "\n" + "Firmware version: " + goProDevice.fwVersion + "\n" + "Serial number: " + goProDevice.serialNumber + "\n" + "\n" + "Device time: " + localTimeAsString + "\n" + "\n" + "WiFi AP SSID: " + goProDevice.wifiSSID + "\n" + "WiFi AP password: " + goProDevice.wifiPSK + "\n" + "WiFi MAC address: " + goProDevice.wifiBSSID + "\n" + "\n" + "BT MAC address: " + goProDevice.btMacAddress;

                        final AlertDialog devInfoAlert = new AlertDialog.Builder(MainActivity.this).setTitle(getResources().getString(R.string.str_Dev_info)).setMessage(sb).setCancelable(true).create();

                        devInfoAlert.show();
                        break;
                    case R.id.locate_on:
                        //Locate on
                        goProDevice.locateOn();
                        break;
                    case R.id.locate_off:
                        //Locate: off
                        goProDevice.locateOff();
                        break;
                    case R.id.shutter_on:
                        //Shutter: on
                        goProDevice.shutterOn();
                        break;
                    case R.id.shutter_off:
                        //Shutter: off
                        goProDevice.shutterOff();
                        break;
                    case R.id.ap_on:
                        //Shutter: on
                        goProDevice.wifiApOn();
                        break;
                    case R.id.ap_off:
                        //Shutter: off
                        goProDevice.wifiApOff();
                        break;
                    case R.id.put_sleep:
                        //Put to sleep
                        goProDevice.sleep();
                        break;
                    case R.id.set_date_time:
                        //Set Date/Time
                        goProDevice.setUTCDateTime();
                        break;
                    case R.id.browse:
                        // Browse storage
                        browseStorage(goProDevice);
                        break;
                    case R.id.preview:
                        startPreview(goProDevice);
                        break;
                    case R.id.disconnect:
                        //Disconnect
                        goProDevice.disconnectGoProDeviceByUser();
                        break;
                    case R.id.try_connect:
                        //Try to connect
                        goProDevice.connectBt(connected -> {
                            if (!connected)
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), String.format(getResources().getString(R.string.str_powered_on), goProDevice.displayName), Toast.LENGTH_SHORT).show());

                            runOnUiThread(() -> goListView.setAdapter(goListAdapter));
                        });
                        break;
                    case R.id.del_device:
                        //Delete device
                        final AlertDialog alert = new AlertDialog.Builder(MainActivity.this).setTitle(getResources().getString(R.string.str_Delete_device)).setMessage(String.format(getResources().getString(R.string.str_sure_delete), goProDevice.displayName)).setPositiveButton(getResources().getString(R.string.str_Yes), (dialog, which) -> {
                            if (goProDevice.removeBtBond()) goProDevices.remove(position);
                        }).setNegativeButton(getResources().getString(R.string.str_Cancel), (dialog, which) -> dialog.dismiss()).create();
                        alert.show();
                        break;
                    case R.id.mode_video_single:
                        goProDevice.setSubMode(0, 0);
                        break;
                    case R.id.mode_video_timelapse:
                        goProDevice.setSubMode(0, 1);
                        break;
                    case R.id.mode_photo_single:
                        goProDevice.setSubMode(1, 1);
                        break;
                    case R.id.mode_photo_night:
                        goProDevice.setSubMode(1, 2);
                        break;
                    case R.id.mode_multishot_burst:
                        goProDevice.setSubMode(2, 0);
                        break;
                    case R.id.mode_multishot_timelapse:
                        goProDevice.setSubMode(2, 1);
                        break;
                    case R.id.mode_multishot_nightlapse:
                        goProDevice.setSubMode(2, 2);
                        break;
                    case R.id.preset_standard:
                        goProDevice.setPreset("standard");
                        break;
                    case R.id.preset_activity:
                        goProDevice.setPreset("activity");
                        break;
                    case R.id.preset_cinematic:
                        goProDevice.setPreset("cinematic");
                        break;
                    case R.id.preset_photo:
                        goProDevice.setPreset("photo");
                        break;
                    case R.id.preset_liveBurst:
                        goProDevice.setPreset("liveBurst");
                        break;
                    case R.id.preset_burstPhoto:
                        goProDevice.setPreset("burstPhoto");
                        break;
                    case R.id.preset_nightPhoto:
                        goProDevice.setPreset("nightPhoto");
                        break;
                    case R.id.preset_timeWarp:
                        goProDevice.setPreset("timeWarp");
                        break;
                    case R.id.preset_timeLapse:
                        goProDevice.setPreset("timeLapse");
                        break;
                    case R.id.preset_nightLapse:
                        goProDevice.setPreset("nightLapse");
                        break;
                }
            } else if(item.getGroupId() == 1) {
                // protoPreset
                goProDevice.setPresetById(item.getItemId());
            }
            return true;
        }
    };

    private static class ssidInputFilter implements InputFilter {
        private static final int MAX_LENGTH = 32;

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            // Check each letter of the source
            for (int i = start; i < end; i++) {
                char character = source.charAt(i);
                // Check whether the character is a letter or a number and the length is within the allowed range
                if (!isValidSSIDCharacter(character) || dest.length() + (end - start) > MAX_LENGTH) {
                    return ""; // Remove characters
                }
            }
            // Check whether the total length is within the permitted range
            int keep = MAX_LENGTH - dest.length();
            if (keep <= 0) {
                return ""; // Remove characters
            } else if (keep >= end - start) {
                return null; // Accept the signs
            } else {
                return source.subSequence(start, start + keep); // Limit the length
            }
        }

        private boolean isValidSSIDCharacter(char character) {
            return Character.isLetterOrDigit(character) || character == '-' || character == '_';
        }
    }

    private void startPreview(GoProDevice goProDevice) {
        WifiManager wifi2 = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi2 != null && !wifi2.isWifiEnabled()) {
            if (!wifi2.setWifiEnabled(true)) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_enable_wifi), Toast.LENGTH_SHORT).show());
                return;
            }
        }

        if (goProDevice.isBusy) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_cam_busy), Toast.LENGTH_SHORT).show());
            goProDevice.queryAllStatusValues();
            return;
        }

        // Live view
        if (isGpsEnabled()) {
            if (hasWifiPermissions()) {
                if (Objects.equals(goProDevice.startStream_query, "")) {
                    Log.e("goProDevices", "ModelID unknown!");
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_cam_unk), Toast.LENGTH_SHORT).show());
                    return;
                }

                final boolean[] streamCanceled = {false};

                final AlertDialog alert = new AlertDialog.Builder(MainActivity.this).setTitle("Connecting to " + goProDevice.wifiSSID).setMessage("Please wait while the WiFi connection is established!\nIf the GoPro has just been started, this can take a while!").setCancelable(false).setNegativeButton("Cancel", (dialog, which) -> streamCanceled[0] = true).create();

                alert.show();

                goProDevice.connectWifi(() -> {
                    //onWifiConnected
                    if (goProDevice.isWifiConnected() && !streamCanceled[0]) {
                        Request request = new Request.Builder().url(goProDevice.stopStream_query).build();

                        httpClient.newCall(request).enqueue(new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                Log.e("HTTP GET error", e.toString());
                                alert.dismiss();
                            }

                            @Override
                            public void onResponse(Call call, Response response) {
                                if (response.isSuccessful()) {
                                    // preview stream available
                                    if (!streamCanceled[0]) {
                                        runOnUiThread(() -> {
                                            Intent previewActivityIntent = new Intent(MainActivity.this, PreviewActivity.class);
                                            startActivity(previewActivityIntent);
                                        });
                                    }
                                } else if (response.code() == 404) {
                                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_firmware_uptodate), Toast.LENGTH_SHORT).show());
                                } else {
                                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_stream_unavailable), Toast.LENGTH_SHORT).show());
                                    Log.e("HTTP GET", "Not successful. Status code: " + response.code());
                                }
                                alert.dismiss();
                                response.close();
                            }
                        });
                    } else {
                        alert.dismiss();
                    }
                });
            } else {
                requestWifiPermissions();
            }
        }
    }

    private void browseStorage(GoProDevice goProDevice) {
        WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifi != null && !wifi.isWifiEnabled()) {
            if (!wifi.setWifiEnabled(true)) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_enable_wifi), Toast.LENGTH_SHORT).show());
                return;
            }
        }

        if (goProDevice.isBusy) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_cam_busy), Toast.LENGTH_SHORT).show());
            goProDevice.queryAllStatusValues();
            return;
        }

        if (isGpsEnabled()) {
            if (hasWifiPermissions()) {
                if (Objects.equals(goProDevice.startStream_query, "")) {
                    Log.e("goProDevices", "ModelID unknown!");
                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_cam_unk), Toast.LENGTH_SHORT).show());
                    return;
                }

                final boolean[] browsingCanceled = {false};

                final AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(String.format(getResources().getString(R.string.str_connecting_to), goProDevice.wifiSSID))
                        .setMessage(getResources().getString(R.string.str_wait_wifi_con))
                        .setCancelable(false)
                        .setNegativeButton(getResources().getString(R.string.str_Cancel), (dialog, which) -> browsingCanceled[0] = true)
                        .create();

                alert.show();

                goProDevice.connectWifi(() -> {
                    //onWifiConnected
                    if (goProDevice.isWifiConnected() && !browsingCanceled[0]) {
                        Request request = new Request.Builder().url(goProDevice.getMediaList_query).build();

                        httpClient.newCall(request).enqueue(new Callback() {
                            @Override
                            public void onFailure(Call call, IOException e) {
                                Log.e("HTTP GET error", e.toString());
                                alert.dismiss();
                            }

                            @Override
                            public void onResponse(Call call, Response response) {
                                if (response.isSuccessful()) {
                                    try {
                                        String resp_Str = response.body().string();
                                        JSONObject mainObject = new JSONObject(resp_Str);
                                        if (!browsingCanceled[0])
                                            parseMediaList(mainObject, goProDevice);
                                    } catch (Exception ex) {
                                        Log.e("JSON", ex.toString());
                                    }
                                } else if (response.code() == 404) {
                                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_firmware_uptodate), Toast.LENGTH_SHORT).show());
                                } else {
                                    runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Error! HTTP status code: " + response.code(), Toast.LENGTH_SHORT).show());
                                    Log.e("HTTP GET", "Not successful. Status code: " + response.code());
                                }
                                alert.dismiss();
                                response.close();
                            }
                        });
                    } else {
                        alert.dismiss();
                    }
                });
            } else {
                requestWifiPermissions();
            }
        }
    }

    private void parseMediaList(JSONObject mediaList, GoProDevice goProDevice) {
        ArrayList<GoMediaFile> goMediaFiles = new ArrayList<>();

        try {
            JSONArray medias = mediaList.getJSONArray("media");

            for (int i1 = 0; i1 < medias.length(); i1++) {
                JSONObject media = medias.getJSONObject(i1);
                String directory = media.getString("d");
                JSONArray filesDataJson = media.getJSONArray("fs");

                for (int i2 = 0; i2 < filesDataJson.length(); i2++) {
                    JSONObject fileDataJson = filesDataJson.getJSONObject(i2);
                    GoMediaFile goMediaFile = new GoMediaFile(fileDataJson, directory, goProDevice);

                    goMediaFiles.add(goMediaFile);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (goMediaFiles.size() > 0) {
            ((MyApplication) this.getApplication()).setGoMediaFiles(goMediaFiles);
            Intent storageBrowserActivityIntent = new Intent(MainActivity.this, StorageBrowserActivity.class);
            startActivity(storageBrowserActivityIntent);
        } else {
            runOnUiThread(() -> Toast.makeText(this, getResources().getString(R.string.str_no_media), Toast.LENGTH_LONG).show());
        }
    }

    private void setOnDataChanged() {
        goProDevices.forEach(goProDevice -> goProDevice.getDataChanges(() -> runOnUiThread(() -> {
            goListView.setAdapter(goListAdapter);
            if (goProDevice.btConnectionStage == BT_NOT_CONNECTED)
                bluetoothAdapter.cancelDiscovery(); // cancelDiscovery (restarts Discovery) to check more quickly whether the camera is still available.
            else if (!goProDevice.firmwareChecked && ((MyApplication) getApplication()).shouldCheckFirmware())
                checkFirmware(goProDevice);
        })));
    }

    public void onMenuClick(View v) {
        CascadePopupMenu popupMenu = new CascadePopupMenu(MainActivity.this, v, Gravity.BOTTOM);
        popupMenu.setOnMenuItemClickListener(mainMenuItemClickListener);
        popupMenu.inflate(R.menu.main_menu);
        popupMenu.show();
    }

    @SuppressLint("NonConstantResourceId")
    private final PopupMenu.OnMenuItemClickListener mainMenuItemClickListener = item -> {
        if (item.hasSubMenu()) return true;

        switch (item.getItemId()) {
            case R.id.main_menu_add_cam:
                Intent pairIntent = new Intent(getApplicationContext(), PairActivity.class);
                startActivity(pairIntent);
                break;
            case R.id.main_menu_about:
                String sb = String.format(getResources().getString(R.string.str_about), BuildConfig.VERSION_NAME);

                final AlertDialog appInfoAlert = new AlertDialog.Builder(MainActivity.this).setTitle(getResources().getString(R.string.str_App_info)).setPositiveButton(getResources().getString(R.string.str_check_release), (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sepp89117/GoEasyPro_Android/releases/latest"));
                    startActivity(browserIntent);
                }).setMessage(sb).setCancelable(true).create();

                appInfoAlert.show();
                break;
            case R.id.main_menu_settings:
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
                break;
            case R.id.main_menu_shutter_log:
                Intent logIntent = new Intent(MainActivity.this, LogActivity.class);
                startActivity(logIntent);
                break;
            case R.id.main_menu_sleep:
                for (int c = 0; c < goProDevices.size(); c++) {
                    if (goProDevices.get(c).btConnectionStage == BT_CONNECTED)
                        goProDevices.get(c).sleep();
                }
                break;
            case R.id.main_menu_issue:
                String body = "Short description of the problem: %0A%0A" + "What steps triggered the problem?%0A%0A" + "Are there any error messages?%0A%0A" + "Do you have an extract from the android log?%0A%0A" + "Camera model: %0A" + "Firmware version of the camera: %0A%0A" + "App version: " + BuildConfig.VERSION_NAME + "%0A" + "Device manufacturer: " + Build.MANUFACTURER + "%0A" + "Device model: " + Build.MODEL + "%0A" + "Android API: " + Build.VERSION.SDK_INT + " (Android " + Build.VERSION.RELEASE + ")%0A";

                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sepp89117/GoEasyPro_Android/issues/new?body=" + body));
                startActivity(browserIntent);
                break;
        }

        return true;
    };

    public void onClickShutterOn(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_to_pair_dev), Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).shutterOn();
        }
    }

    public void onClickShutterOff(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_to_pair_dev), Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).shutterOff();
        }
    }

    public void onClickSleep(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_to_pair_dev), Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED) goProDevices.get(i).sleep();
        }
    }

    public void onClickHighlight(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_to_pair_dev), Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).highlight();
        }
    }

    public void onClickSetMode(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_to_pair_dev), Toast.LENGTH_SHORT).show();
            return;
        }

        CascadePopupMenu popupMenu = new CascadePopupMenu(MainActivity.this, v);
        popupMenu.setOnMenuItemClickListener(modeMenuItemClickListener);
        popupMenu.inflate(R.menu.mode_menu);
        popupMenu.show();
    }

    private final PopupMenu.OnMenuItemClickListener modeMenuItemClickListener = item -> {
        if (item.hasSubMenu()) return true;

        switch (item.getItemId()) {
            case R.id.mode_video_single:
                setModes(0, 0);
                break;
            case R.id.mode_video_timelapse:
                setModes(0, 1);
                break;
            case R.id.mode_photo_single:
                setModes(1, 1);
                break;
            case R.id.mode_photo_night:
                setModes(1, 2);
                break;
            case R.id.mode_multishot_burst:
                setModes(2, 0);
                break;
            case R.id.mode_multishot_timelapse:
                setModes(2, 1);
                break;
            case R.id.mode_multishot_nightlapse:
                setModes(2, 2);
                break;
        }

        return true;
    };

    private void setModes(int mode, int subMode) {
        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).setSubMode(mode, subMode);
        }
    }

    public void onClickSetPreset(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.str_to_pair_dev), Toast.LENGTH_SHORT).show();
            return;
        }

        CascadePopupMenu popupMenu = new CascadePopupMenu(MainActivity.this, v);
        popupMenu.setOnMenuItemClickListener(presetMenuItemClickListener);
        popupMenu.inflate(R.menu.preset_menu);

        // Make all elements visible
        Menu menu = popupMenu.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setVisible(true);
        }

        popupMenu.show();
    }

    public void setControlMenuDependingOnModelID(CascadePopupMenu popupMenu) {
        Menu conDevMenu = popupMenu.getMenu();
        Menu presetSelectMenu = conDevMenu.findItem(R.id.preset_select).getSubMenu();
        Menu controlMenu = conDevMenu.findItem(R.id.control).getSubMenu();

        GoProDevice goProDevice = goProDevices.get(lastCamClickedIndex);

        if (goProDevice.modelID < HERO8_BLACK) {
            controlMenu.findItem(R.id.mode_select).setVisible(true);
            controlMenu.findItem(R.id.preset_select).setVisible(false);
        } else {
            controlMenu.findItem(R.id.mode_select).setVisible(false);
            controlMenu.findItem(R.id.preset_select).setVisible(true);

            // Make all presetSelectMenu elements invisible
            for (int i = 0; i < presetSelectMenu.size(); i++) {
                MenuItem subItem = presetSelectMenu.getItem(i);
                //subItem.setVisible(true);
                subItem.setVisible(false);
            }

            if (goProDevice.hasProtoPresets()) {
                for (GoProtoPreset goProtoPreset : goProDevice.getGoProtoPresets()) {
                    // int groupId, int itemId, int order, CharSequence title
                    presetSelectMenu.add(1, goProtoPreset.getId(), Menu.NONE, goProtoPreset.getPresetTitle());
                }
            } else {
                switch (goProDevice.modelID) {
                    case HERO12_BLACK:
                        // Make unavailable presets invisible
                        presetSelectMenu.findItem(R.id.preset_activity).setVisible(false);
                        presetSelectMenu.findItem(R.id.preset_cinematic).setVisible(false);
                        presetSelectMenu.findItem(R.id.preset_liveBurst).setVisible(false);
                        break;
                }
            }
        }
    }

    private final PopupMenu.OnMenuItemClickListener presetMenuItemClickListener = item -> {
        if (item.hasSubMenu()) return true;

        GoProDevice goProDevice = goProDevices.get(lastCamClickedIndex);

        if (goProDevice.hasProtoPresets()) {

        } else {
            switch (item.getItemId()) {
                case R.id.preset_standard:
                    setPresets("standard");
                    break;
                case R.id.preset_activity:
                    setPresets("activity");
                    break;
                case R.id.preset_cinematic:
                    setPresets("cinematic");
                    break;
                case R.id.preset_photo:
                    setPresets("photo");
                    break;
                case R.id.preset_liveBurst:
                    setPresets("liveBurst");
                    break;
                case R.id.preset_burstPhoto:
                    setPresets("burstPhoto");
                    break;
                case R.id.preset_nightPhoto:
                    setPresets("nightPhoto");
                    break;
                case R.id.preset_timeWarp:
                    setPresets("timeWarp");
                    break;
                case R.id.preset_timeLapse:
                    setPresets("timeLapse");
                    break;
                case R.id.preset_nightLapse:
                    setPresets("nightLapse");
                    break;
            }
        }
        return true;
    };

    private void setPresets(String presetName) {
        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).setPreset(presetName);
        }
    }

    private void startApp() {
        if (((MyApplication) getApplication()).shouldCheckFirmware()) getFirmwareCatalog();

        if (((MyApplication) getApplication()).shouldCheckAppUpdate()) checkAppUpdate();

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if (deviceName != null && deviceName.contains("GoPro ")) {
                    boolean inList = false;
                    if (goProDevices.size() > 0) {
                        for (int i = 0; i < goProDevices.size(); i++) {
                            GoProDevice goProDevice = goProDevices.get(i);
                            if (Objects.equals(goProDevice.btMacAddress, device.getAddress())) {
                                inList = true;
                            }
                        }
                    }
                    if (!inList) {
                        GoProDevice goProDevice = new GoProDevice(MainActivity.this, this.getApplication(), device);
                        goProDevice.btPaired = true;
                        goProDevice.btMacAddress = deviceHardwareAddress;
                        if (MyApplication.checkBtDevConnected(device)) {
                            goProDevice.disconnectGoProDevice();
                            goProDevice.btConnectionStage = BT_NOT_CONNECTED;
                        }
                        goProDevices.add(goProDevice);
                        goListView.setAdapter(goListAdapter);
                    }
                }
            }
        }

        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        registerReceiver(broadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        bluetoothAdapter.startDiscovery();
    }

    private boolean hasBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (String permission : BT_PERMISSIONS_S_UP) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        } else {
            for (String permission : BT_PERMISSIONS_R_DOWN) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasWifiPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (String permission : WIFI_PERMISSIONS_Q_UP) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        } else {
            for (String permission : WIFI_PERMISSIONS_P_DOWN) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BT_PERMISSIONS_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                switch (permission) {
                    case Manifest.permission.BLUETOOTH:
                        if (grantResult != PackageManager.PERMISSION_GRANTED)
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH}, BT_PERMISSIONS_CODE);
                        break;
                    case Manifest.permission.BLUETOOTH_ADMIN:
                        if (grantResult != PackageManager.PERMISSION_GRANTED)
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, BT_PERMISSIONS_CODE);
                        break;
                    case Manifest.permission.BLUETOOTH_SCAN:
                        if (grantResult != PackageManager.PERMISSION_GRANTED)
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, BT_PERMISSIONS_CODE);
                        break;
                    case Manifest.permission.BLUETOOTH_CONNECT:
                        if (grantResult != PackageManager.PERMISSION_GRANTED)
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BT_PERMISSIONS_CODE);
                        break;
                    case Manifest.permission.ACCESS_FINE_LOCATION:
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, BT_PERMISSIONS_CODE);
                        }
                        break;
                    case Manifest.permission.ACCESS_COARSE_LOCATION:
                        if (grantResult != PackageManager.PERMISSION_GRANTED)
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, BT_PERMISSIONS_CODE);
                        break;
                }

                if (hasBtPermissions()) {
                    if (isBtEnabled()) {
                        startApp();
                    }
                    setOnDataChanged();
                }
            }
        } /*else if (requestCode == WIFI_PERMISSIONS_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                switch (permission) {
                    case Manifest.permission.INTERNET:
                    case Manifest.permission.ACCESS_WIFI_STATE:
                    case Manifest.permission.CHANGE_WIFI_STATE:
                    case Manifest.permission.ACCESS_NETWORK_STATE:
                    case Manifest.permission.CHANGE_NETWORK_STATE:
                    case Manifest.permission.ACCESS_FINE_LOCATION:
                    case Manifest.permission.FOREGROUND_SERVICE:
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        }
                        break;
                }

            }
        }*/
    }

    private void requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                Snackbar.make(mLayout, getResources().getString(R.string.str_BT_permission_required), Snackbar.LENGTH_INDEFINITE).setAction("OK", view -> ActivityCompat.requestPermissions(MainActivity.this, BT_PERMISSIONS_S_UP, BT_PERMISSIONS_CODE)).show();
            } else {
                ActivityCompat.requestPermissions(this, BT_PERMISSIONS_S_UP, BT_PERMISSIONS_CODE);
            }
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
                Snackbar.make(mLayout, getResources().getString(R.string.str_BT_permission_required), Snackbar.LENGTH_INDEFINITE).setAction("OK", view -> ActivityCompat.requestPermissions(MainActivity.this, BT_PERMISSIONS_R_DOWN, BT_PERMISSIONS_CODE)).show();
            } else {
                ActivityCompat.requestPermissions(this, BT_PERMISSIONS_R_DOWN, BT_PERMISSIONS_CODE);
            }
        }
    }

    public void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                Snackbar.make(mLayout, getResources().getString(R.string.str_Wifi_permission_required), Snackbar.LENGTH_INDEFINITE).setAction("OK", view -> ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_Q_UP, WIFI_PERMISSIONS_CODE)).show();
            } else {
                ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_Q_UP, WIFI_PERMISSIONS_CODE);
            }
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
                Snackbar.make(mLayout, getResources().getString(R.string.str_Wifi_permission_required), Snackbar.LENGTH_INDEFINITE).setAction("OK", view -> ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_P_DOWN, WIFI_PERMISSIONS_CODE)).show();
            } else {
                ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_P_DOWN, WIFI_PERMISSIONS_CODE);
            }
        }
    }

    private boolean isBtEnabled() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityIntent.launch(enableBtIntent);
            return false;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return isGpsEnabled();
        }
        return true;
    }

    private boolean isGpsEnabled() {
        LocationManager lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = false;
        boolean network_enabled = false;

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
        }

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ignored) {
        }

        if (!gps_enabled && !network_enabled) {
            new AlertDialog.Builder(this).setMessage(getResources().getString(R.string.str_Localization_required)).setPositiveButton("OK", (paramDialogInterface, paramInt) -> MainActivity.this.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton("Cancel", null).show();
            return false;
        }
        return true;
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case BluetoothAdapter.ACTION_STATE_CHANGED:
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF)
                        isBtEnabled();
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    goProDevices.stream().filter(goProDevice -> Objects.equals(goProDevice.btMacAddress, device.getAddress())).findFirst().ifPresent(goProDevice -> {
                        goProDevice.camBtAvailable = true;
                        if (((MyApplication) getApplication()).shouldAutoConnect() && goProDevice.btConnectionStage == BT_NOT_CONNECTED && !goProDevice.shouldNotBeReconnected) {
                            goProDevice.connectBt(connected -> runOnUiThread(() -> goListView.setAdapter(goListAdapter)));
                        }
                        goListView.setAdapter(goListAdapter);
                    });
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    if (!((MyApplication) getApplication()).isAppPaused())
                        bluetoothAdapter.startDiscovery();
                    break;
            }
        }
    };

    ActivityResultLauncher<Intent> startActivityIntent = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (isBtEnabled()) {
            startApp();
        }
    });

    private void getFirmwareCatalog() {
        OkHttpClient client = new OkHttpClient();

        String url = "https://api.gopro.com/firmware/v2/catalog";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("HTTP GET Firmware Catalog error", e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonResponse = response.body().string();
                    try {
                        JSONObject jFwCatalog = new JSONObject(jsonResponse);
                        JSONArray jCamerasArray = jFwCatalog.getJSONArray("cameras");
                        for (int i = 0; i < jCamerasArray.length(); i++) {
                            try {
                                JSONObject jCamera = jCamerasArray.getJSONObject(i);
                                int modelID = jCamera.getInt("model");
                                String model_string = jCamera.getString("model_string");
                                String fwVersion = jCamera.getString("version");
                                firmwareCatalog.put(modelID, model_string + "." + fwVersion);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e("getFirmwareCatalog", "Response is unsuccessful with status code: " + response.code());
                }
            }
        });
    }

    private void checkAppUpdate() {
        OkHttpClient client = new OkHttpClient();

        String url = "https://api.github.com/repos/sepp89117/GoEasyPro_Android/releases/latest";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("HTTP GET Latest app release error", e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String jsonResponse = response.body().string();
                    try {
                        JSONObject jLatestRelease = new JSONObject(jsonResponse);
                        String tag_name = jLatestRelease.getString("tag_name");
                        String body = jLatestRelease.getString("body");
                        JSONArray jAssets = jLatestRelease.getJSONArray("assets");
                        JSONObject jAsset0 = jAssets.getJSONObject(0);
                        String content_type = jAsset0.getString("content_type");
                        String latestVersionName = tag_name.substring(1);
                        String currentVersionName = BuildConfig.VERSION_NAME;

                        if (content_type.equals("application/vnd.android.package-archive") && isNewerVersion(currentVersionName, latestVersionName)) {
                            new Thread(() -> {
                                Looper.prepare();
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    final AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setTitle("App update available").setMessage("A new update for this app is available.\nDo you want to go to the download page?\n\nRelease notes:\n" + body).setPositiveButton("Yes", (dialog, which) -> {
                                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sepp89117/GoEasyPro_Android/releases/latest"));
                                        startActivity(browserIntent);
                                    }).setNegativeButton("No", (dialog, which) -> dialog.dismiss()).setCancelable(true).create();

                                    alertDialog.show();
                                });
                                Looper.loop();
                            }).start();
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e("checkAppUpdate", "Response is not successful with status code: " + response.code());
                }
            }
        });
    }

    private void checkFirmware(GoProDevice goProDevice) {
        if (!Objects.equals(goProDevice.fwVersion, "")) {
            String catalogFW = firmwareCatalog.getOrDefault(goProDevice.modelID, "");
            String currentFW = goProDevice.fwVersion;

            if (!Objects.equals(catalogFW, "")) {
                goProDevice.firmwareChecked = true;
                if (isNewerFW(currentFW, catalogFW)) {
                    Toast.makeText(this, getResources().getString(R.string.str_new_fw_available), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private boolean isNewerFW(String currentVersion, String testVersion) {
        int[] currentVersionSegments = getFwSegments(currentVersion);
        int[] testVersionSegments = getFwSegments(testVersion);

        if (currentVersionSegments.length == testVersionSegments.length) {
            for (int i = 0; i < testVersionSegments.length; i++) {
                if (testVersionSegments[i] > currentVersionSegments[i]) {
                    return true;
                }
            }
        }

        return false;
    }

    private int[] getFwSegments(String fw) {
        String[] segments = fw.split("\\.");
        int[] parsedSegments = new int[segments.length - 1];
        for (int i = 1; i < segments.length; i++) {
            parsedSegments[i - 1] = Integer.parseInt(segments[i]);
        }
        return parsedSegments;
    }

    private boolean isNewerVersion(String currentVersion, String testVersion) {
        int[] currentVersionSegments = getVersionSegments(currentVersion);
        int[] testVersionSegments = getVersionSegments(testVersion);

        if (currentVersionSegments.length == testVersionSegments.length) {
            for (int i = 0; i < testVersionSegments.length; i++) {
                if (currentVersionSegments[i] > testVersionSegments[i]) {
                    return false;
                } else if (testVersionSegments[i] > currentVersionSegments[i]) {
                    return true;
                }
            }
        }

        return false;
    }

    private int[] getVersionSegments(String v) {
        String[] segments = v.split("\\.");
        int[] parsedSegments = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            parsedSegments[i] = Integer.parseInt(segments[i]);
        }
        return parsedSegments;
    }

}