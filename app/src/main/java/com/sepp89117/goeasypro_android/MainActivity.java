package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.GoProDevice.BT_NOT_CONNECTED;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

import me.saket.cascade.CascadePopupMenu;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<GoProDevice> goProDevices = new ArrayList<>();
    private GoListAdapter goListAdapter;
    private ListView goListView;
    private View mLayout;
    private OkHttpClient client = new OkHttpClient();
    private boolean wifi_granted = false;
    private int lastCamClickedIndex = -1;

    private static final int BT_PERMISSIONS_CODE = 87;
    private static final int WIFI_PERMISSIONS_CODE = 41;
    private static final String[] BT_PERMISSIONS_S_UP = {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
    };
    private static final String[] BT_PERMISSIONS_R_DOWN = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    private static final String[] WIFI_PERMISSIONS_Q_UP = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
    };
    private static final String[] WIFI_PERMISSIONS_P_DOWN = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLayout = findViewById(R.id.main_layout);

        //get bluetoothManager and bluetoothAdapter
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported! Please use other device!", Toast.LENGTH_LONG).show();
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
            } else if (goProDevices.get(lastCamClickedIndex).btConnectionStage == BT_NOT_CONNECTED) {
                popupMenu.inflate(R.menu.not_connected_dev_menu);
            } else {
                Toast.makeText(MainActivity.this, "Please wait until the connection has been established!", Toast.LENGTH_SHORT).show();
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
    }

    private final PopupMenu.OnMenuItemClickListener camMenuItemClickListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (item.hasSubMenu())
                return true;

            int position = lastCamClickedIndex;
            GoProDevice goProDevice = goProDevices.get(position);
            ((MyApplication) MainActivity.this.getApplication()).setFocusedDevice(goProDevice);

            switch (item.getItemId()) {
                case R.id.dev_settings:
                    Intent goSettingsActivityIntent = new Intent(MainActivity.this, GoSettingsActivity.class);
                    startActivity(goSettingsActivityIntent);
                    break;
                case R.id.dev_rename:
                    final EditText name_input = new EditText(MainActivity.this);
                    name_input.setText(goProDevice.displayName);
                    name_input.requestFocus();
                    name_input.selectAll();

                    InputFilter[] filterArray = new InputFilter[1];
                    filterArray[0] = new InputFilter.LengthFilter(10);
                    name_input.setFilters(filterArray);

                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

                    AlertDialog devRenameAlert = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Rename '" + goProDevice.displayName + "'")
                            .setMessage("Enter a new name for device '" + goProDevice.name + "'\n\nMin. 1 and max. 10 characters\n")
                            .setView(name_input)
                            .setPositiveButton("OK", (dialog, which) -> {
                                inputMethodManager.hideSoftInputFromWindow(name_input.getWindowToken(), 0);
                                String newName = name_input.getText().toString();
                                if (newName.length() > 0)
                                    goProDevice.saveNewDisplayName(name_input.getText().toString());
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                inputMethodManager.hideSoftInputFromWindow(name_input.getWindowToken(), 0);
                                dialog.cancel();
                            })
                            .setCancelable(true)
                            .create();

                    devRenameAlert.show();
                    break;
                case R.id.dev_info:
                    String sb = "Device name: " + goProDevice.name + "\n" +
                            "Display name: " + goProDevice.displayName + "\n" +
                            "Model name: " + goProDevice.modelName + "\n" +
                            "Model ID: " + goProDevice.modelID + "\n" + "\n" +
                            "Board type: " + goProDevice.boardType + "\n" +
                            "Firmware version: " + goProDevice.firmware + "\n" +
                            "Serial number: " + goProDevice.serialNumber + "\n" + "\n" +
                            "WiFi AP SSID: " + goProDevice.wifiSsid + "\n" +
                            "WiFi AP password: " + goProDevice.wifiPw + "\n" +
                            "WiFi MAC address: " + goProDevice.wifiMacAddr + "\n" + "\n" +
                            "BT MAC address: " + goProDevice.btMacAddr;

                    AlertDialog devInfoAlert = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Device Info")
                            .setMessage(sb)
                            .setCancelable(true)
                            .create();

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
                    goProDevice.setDateTime();
                    break;
                case R.id.browse:
                    // Browse storage
                    WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    if (wifi != null && !wifi.isWifiEnabled()) {
                        if (!wifi.setWifiEnabled(true)) {
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Please enable WiFi!", Toast.LENGTH_SHORT).show());
                            return true;
                        }
                    }

                    if (goProDevice.isBusy) {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "The camera is currently busy.\nPlease try again later!", Toast.LENGTH_SHORT).show());
                        return true;
                    }

                    if (isGpsEnabled()) {
                        if (hasWifiPermissions()) {
                            if (Objects.equals(goProDevice.startStream_query, "")) {
                                Log.e("goProDevices", "ModelID unknown!");
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Unknown cam model!", Toast.LENGTH_SHORT).show());
                                return true;
                            }

                            final boolean[] browsingCanceled = {false};

                            AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Connecting to " + goProDevice.wifiSsid)
                                    .setMessage("Please wait while the WiFi connection is established!\nIf the GoPro has just been started, this can take a while!")
                                    .setCancelable(false)
                                    .setNegativeButton("Cancel", (dialog, which) -> browsingCanceled[0] = true)
                                    .create();

                            alert.show();

                            goProDevice.connectWifi(() -> {
                                //onWifiConnected
                                if (goProDevice.isWifiConnected && !browsingCanceled[0]) {
                                    Request request = new Request.Builder()
                                            .url(goProDevice.getMediaList_query)
                                            .build();
                                    Log.d("HTTP GET", goProDevice.getMediaList_query);
                                    try (Response response = client.newCall(request).execute()) {
                                        if (response.isSuccessful()) {
                                            String resp_Str = response.body().string();
                                            JSONObject mainObject = new JSONObject(resp_Str);
                                            if (!browsingCanceled[0])
                                                parseMediaList(mainObject, goProDevice);
                                        } else if (response.code() == 404) {
                                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Doesn't work! Is the GoPro firmware up to date?", Toast.LENGTH_SHORT).show());
                                        } else {
                                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Something went wrong! HTTP status code: " + response.code(), Toast.LENGTH_SHORT).show());
                                            Log.e("HTTP GET", "Not successful. Status code: " + response.code());
                                        }
                                        alert.dismiss();
                                    } catch (Exception ex) {
                                        Log.e("HTTP GET error", ex.toString());
                                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_SHORT).show());
                                        alert.dismiss();
                                    }
                                } else {
                                    alert.dismiss();
                                }
                            });
                        } else {
                            requestWifiPermissions();
                        }
                    }

                    break;
                case R.id.preview:
                    WifiManager wifi2 = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                    if (wifi2 != null && !wifi2.isWifiEnabled()) {
                        if (!wifi2.setWifiEnabled(true)) {
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Please enable WiFi!", Toast.LENGTH_SHORT).show());
                            return true;
                        }
                    }

                    if (goProDevice.isBusy) {
                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "The camera is currently busy.\nPlease try again later!", Toast.LENGTH_SHORT).show());
                        return true;
                    }

                    // Live view
                    if (isGpsEnabled()) {
                        if (hasWifiPermissions()) {
                            if (Objects.equals(goProDevice.startStream_query, "")) {
                                Log.e("goProDevices", "ModelID unknown!");
                                runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Unknown cam model!", Toast.LENGTH_SHORT).show());
                                return true;
                            }

                            final boolean[] streamCanceled = {false};

                            AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Connecting to " + goProDevice.wifiSsid)
                                    .setMessage("Please wait while the WiFi connection is established!\nIf the GoPro has just been started, this can take a while!")
                                    .setCancelable(false)
                                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            streamCanceled[0] = true;
                                        }
                                    })
                                    .create();

                            alert.show();

                            goProDevice.connectWifi(() -> {
                                //onWifiConnected
                                if (goProDevice.isWifiConnected && !streamCanceled[0]) {
                                    Request request = new Request.Builder()
                                            .url(goProDevice.stopStream_query)
                                            .build();
                                    Log.d("HTTP GET", goProDevice.stopStream_query);
                                    try (Response response = client.newCall(request).execute()) {
                                        if (response.isSuccessful()) {
                                            String resp_Str = "" + response.body().string();
                                            Log.d("HTTP GET response", resp_Str);
                                            // preview stream available
                                            if (!streamCanceled[0]) {
                                                runOnUiThread(() -> {
                                                    Intent previewActivityIntent = new Intent(MainActivity.this, PreviewActivity.class);
                                                    startActivity(previewActivityIntent);
                                                });
                                            }
                                        } else if (response.code() == 404) {
                                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Doesn't work! Is the GoPro firmware up to date?", Toast.LENGTH_SHORT).show());
                                        } else {
                                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "The preview stream is currently unavailable", Toast.LENGTH_SHORT).show());
                                            Log.e("HTTP GET", "Not successful. Status code: " + response.code());
                                        }
                                        alert.dismiss();
                                    } catch (Exception ex) {
                                        Log.e("HTTP GET error", ex.toString());
                                        runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show());
                                        alert.dismiss();
                                    }
                                } else {
                                    alert.dismiss();
                                }
                            });
                        } else {
                            requestWifiPermissions();
                        }
                    }
                    break;
                case R.id.disconnect:
                    //Disconnect
                    goProDevice.disconnectBt();
                    break;
                case R.id.try_connect:
                    //Try to connect
                    goProDevice.connectBt(connected -> {
                        if (!connected)
                            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Something went wrong. Is the GoPro powered on?", Toast.LENGTH_SHORT).show());

                        runOnUiThread(() -> goListView.setAdapter(goListAdapter));
                    });
                    break;
                case R.id.del_device:
                    //Delete device
                    AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Delete device")
                            .setMessage("Are you sure you want to delete " + goProDevice.displayName + "?")
                            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (goProDevice.removeBtBond())
                                        goProDevices.remove(position);
                                }
                            })
                            .create();
                    alert.show();
                    break;
            }

            return true;
        }
    };

    private void parseMediaList(JSONObject medialist, GoProDevice goProDevice) {
        ArrayList<GoMediaFile> goMediaFiles = new ArrayList<>();

        try {
            JSONArray medias = medialist.getJSONArray("media");

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
            runOnUiThread(() -> {
                Toast.makeText(this, "There is no media on the storage", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void setOnDataChanged() {
        for (int i = 0; i < goProDevices.size(); i++) {
            GoProDevice goProDevice = goProDevices.get(i);
            goProDevice.getDataChanges(() -> runOnUiThread(() -> goListView.setAdapter(goListAdapter)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        goListView.setAdapter(goListAdapter);
        setOnDataChanged();
    }

    public void onMenuClick(View v) {
        CascadePopupMenu popupMenu = new CascadePopupMenu(MainActivity.this, v, Gravity.BOTTOM);
        popupMenu.setOnMenuItemClickListener(mainMenuItemClickListener);
        popupMenu.inflate(R.menu.main_menu);
        popupMenu.show();
    }

    private final PopupMenu.OnMenuItemClickListener mainMenuItemClickListener = item -> {
        if (item.hasSubMenu())
            return true;

        switch (item.getItemId()) {
            case R.id.main_menu_add_cam:
                Intent pairIntent = new Intent(getApplicationContext(), PairActivity.class);
                startActivity(pairIntent);
                break;
            case R.id.main_menu_about:
                String sb = "App name:\t\t\tGoEasyPro Android\n" +
                        "Author:\t\t\t\t\t\t\tSebastian Balzer\n" +
                        "Github user:\t\tsepp89117\n" +
                        "Version:\t\t\t\t\t\tv" + BuildConfig.VERSION_NAME + "\n";

                AlertDialog appInfoAlert = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("App Info")
                        .setPositiveButton("Check latest release", (dialog, which) -> {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sepp89117/GoEasyPro_Android/releases/latest"));
                            startActivity(browserIntent);
                        })
                        .setMessage(sb)
                        .setCancelable(true)
                        .create();

                appInfoAlert.show();
                break;
            case R.id.main_menu_settings:
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
                break;
            case R.id.main_menu_sleep:
                for (int c = 0; c < goProDevices.size(); c++) {
                    if(goProDevices.get(c).btConnectionStage == BT_CONNECTED)
                        goProDevices.get(c).sleep();
                }
                break;
            case R.id.main_menu_issue:
                String body = "App version: " + BuildConfig.VERSION_NAME + "%0A" +
                        "Device manufacturer: " + Build.MANUFACTURER + "%0A" +
                        "Device model: " + Build.MODEL + "%0A" +
                        "Android API: " + Build.VERSION.SDK_INT + " (Android " + Build.VERSION.RELEASE + ")%0A";

                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sepp89117/GoEasyPro_Android/issues/new?body=" + body));
                startActivity(browserIntent);
                break;
        }

        return true;
    };

    @SuppressLint("MissingPermission")
    public void onClickShutterOn(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), "Please connect/pair devices", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).shutterOn();
        }
    }

    @SuppressLint("MissingPermission")
    public void onClickShutterOff(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), "Please connect/pair devices", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).shutterOff();
        }
    }

    @SuppressLint("MissingPermission")
    public void onClickSleep(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), "Please connect/pair devices", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).sleep();
        }
    }

    @SuppressLint("MissingPermission")
    public void onClickHighlight(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), "Please connect/pair devices", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            if (goProDevices.get(i).btConnectionStage == BT_CONNECTED)
                goProDevices.get(i).highlight();
        }
    }

    private void startApp() {
        @SuppressLint("MissingPermission") Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                if (deviceName != null && deviceName.contains("GoPro ")) {
                    boolean inList = false;
                    if (goProDevices.size() > 0) {
                        for (int i = 0; i < goProDevices.size(); i++) {
                            GoProDevice goProDevice = goProDevices.get(i);
                            if (Objects.equals(goProDevice.btMacAddr, device.getAddress())) {
                                inList = true;
                            }
                        }
                    }
                    if (!inList) {
                        GoProDevice goProDevice = new GoProDevice(MainActivity.this, this.getApplication(), deviceName);
                        goProDevice.bluetoothDevice = device;
                        goProDevice.btPaired = true;
                        goProDevice.name = deviceName;
                        goProDevice.btMacAddr = deviceHardwareAddress;
                        if (MyApplication.checkBtDevConnected(device)) {
                            goProDevice.disconnectBt();
                            goProDevice.btConnectionStage = BT_NOT_CONNECTED;
                        }
                        goProDevices.add(goProDevice);
                        if (((MyApplication) getApplication()).shouldAutoConnect()) {
                            goProDevice.connectBt(connected -> runOnUiThread(() -> goListView.setAdapter(goListAdapter)));
                        }
                        goListView.setAdapter(goListAdapter);
                    }
                }
            }
        }
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
        } else if (requestCode == WIFI_PERMISSIONS_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int grantResult = grantResults[i];

                wifi_granted = true;

                switch (permission) {
                    case Manifest.permission.INTERNET:
                    case Manifest.permission.ACCESS_WIFI_STATE:
                    case Manifest.permission.CHANGE_WIFI_STATE:
                    case Manifest.permission.ACCESS_NETWORK_STATE:
                    case Manifest.permission.CHANGE_NETWORK_STATE:
                    case Manifest.permission.ACCESS_FINE_LOCATION:
                    case Manifest.permission.FOREGROUND_SERVICE:
                        if (grantResult != PackageManager.PERMISSION_GRANTED)
                            wifi_granted = false;
                        break;
                }

            }
        }
    }

    private void requestBtPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                Snackbar.make(mLayout, "Bluetooth permission is needed to connect GoPros.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", view -> ActivityCompat.requestPermissions(MainActivity.this, BT_PERMISSIONS_S_UP, BT_PERMISSIONS_CODE))
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, BT_PERMISSIONS_S_UP, BT_PERMISSIONS_CODE);
            }
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
                Snackbar.make(mLayout, "Bluetooth permission is needed to connect GoPros.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", view -> ActivityCompat.requestPermissions(MainActivity.this, BT_PERMISSIONS_R_DOWN, BT_PERMISSIONS_CODE))
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, BT_PERMISSIONS_R_DOWN, BT_PERMISSIONS_CODE);
            }
        }
    }

    public void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                Snackbar.make(mLayout, "Wifi permission is needed to connect GoPros.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", view -> ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_Q_UP, WIFI_PERMISSIONS_CODE))
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_Q_UP, WIFI_PERMISSIONS_CODE);
            }
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
                Snackbar.make(mLayout, "Wifi permission is needed to connect GoPros.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", view -> ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_P_DOWN, WIFI_PERMISSIONS_CODE))
                        .show();
            } else {
                ActivityCompat.requestPermissions(this, WIFI_PERMISSIONS_P_DOWN, WIFI_PERMISSIONS_CODE);
            }
        }
    }

    private boolean isBtEnabled() {
        //check bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            //request bluetooth enable
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityIntent.launch(enableBtIntent);
            return false;
        }
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver, filter);

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
            new AlertDialog.Builder(this)
                    .setMessage("Localization is required. Please turn localization on and try again!")
                    .setPositiveButton("OK", (paramDialogInterface, paramInt) -> MainActivity.this.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Cancel", null)
                    .show();
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    isBtEnabled();
                }
            }
        }
    };

    ActivityResultLauncher<Intent> startActivityIntent = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (isBtEnabled()) {
                    startApp();
                }
            });

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {

        }
    }
}