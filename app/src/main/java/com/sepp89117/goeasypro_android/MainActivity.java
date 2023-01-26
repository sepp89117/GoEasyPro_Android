package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.GoProDevice.BT_NOT_CONNECTED;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Objects;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {
    private Button btn_pair;
    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<GoProDevice> goProDevices = new ArrayList<>();
    private GoListAdapter goListAdapter;
    private ListView goListView;
    private View mLayout;
    private OkHttpClient client = new OkHttpClient();
    private boolean wifi_granted = false;
    JSONObject settingsValues = null;
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

        btn_pair = findViewById(R.id.btn_pair);
        btn_pair.setEnabled(false);

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
        registerForContextMenu(goListView);
        goListView.setOnItemClickListener((parent, view, position, id) -> {
            lastCamClickedIndex = position;
            view.showContextMenu();
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

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        MenuInflater inflater = getMenuInflater();
        if (lastCamClickedIndex < 0) {
            lastCamClickedIndex = info.position;
        }

        menu.setHeaderTitle(goProDevices.get(lastCamClickedIndex).displayName);

        if (goProDevices.get(lastCamClickedIndex).btConnectionStage == BT_CONNECTED)
            inflater.inflate(R.menu.connected_dev_menu, menu);
        else if (goProDevices.get(lastCamClickedIndex).btConnectionStage == BT_NOT_CONNECTED)
            inflater.inflate(R.menu.not_connected_dev_menu, menu);
        else
            Toast.makeText(this, "Please wait until the connection has been established!", Toast.LENGTH_SHORT).show();
    }

    // menu item select listener
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = lastCamClickedIndex;
        GoProDevice goProDevice = goProDevices.get(position);
        ((MyApplication) this.getApplication()).setFocusedDevice(goProDevice);

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
                        .setTitle("Rename " + goProDevice.displayName)
                        .setMessage("Enter a new name!\nMin. 1 and max. 10 characters!")
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
                        "Display name" + goProDevice.displayName + "\n" +
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
                if (goProDevice.isBusy) {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), "The camera is currently busy.\nPlease try again later!", Toast.LENGTH_SHORT).show();
                    });
                    return true;
                }

                if (isGpsEnabled()) {
                    if (hasWifiPermissions()) {
                        if (Objects.equals(goProDevice.startStream_query, "")) {
                            Log.e("goProDevices", "ModelID unknown!");
                            return true;
                        }

                        AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Connecting")
                                .setMessage("Please wait while the WiFi connection is established!")
                                .setCancelable(true)
                                .create();

                        alert.show();

                        goProDevice.connectWifi(() -> {
                            alert.dismiss();
                            //onWifiConnected
                            if(goProDevice.isWifiConnected) {
                                Request request = new Request.Builder()
                                        .url(goProDevice.getMediaList_query)
                                        .build();
                                Log.d("HTTP GET", goProDevice.getMediaList_query);
                                try (Response response = client.newCall(request).execute()) {
                                    if (response.isSuccessful()) {
                                        Log.d("HTTP GET", "successful");
                                        String resp_Str = response.body().string();
                                        JSONObject mainObject = new JSONObject(resp_Str);
                                        parseMediaList(mainObject, goProDevice);
                                    } else if (response.code() == 404) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(getApplicationContext(), "Doesn't work! Is the GoPro firmware up to date?", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                } catch (Exception ex) {
                                    Log.e("HTTP GET error", ex.toString());
                                    runOnUiThread(() -> {
                                        Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                                }
                            }
                        });
                    } else {
                        requestWifiPermissions();
                    }
                }

                break;
            case R.id.preview:
                if (goProDevice.isBusy) {
                    runOnUiThread(() -> {
                        Toast.makeText(getApplicationContext(), "The camera is currently busy.\nPlease try again later!", Toast.LENGTH_SHORT).show();
                    });
                    return true;
                }

                // Live view
                if (isGpsEnabled()) {
                    if (hasWifiPermissions()) {
                        if (Objects.equals(goProDevice.startStream_query, "")) {
                            Log.e("goProDevices", "ModelID unknown!");
                            return true;
                        }

                        AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Connecting")
                                .setMessage("Please wait while the WiFi connection is established!")
                                .setCancelable(true)
                                .create();

                        alert.show();

                        goProDevice.connectWifi(() -> {
                            alert.dismiss();
                            //onWifiConnected
                            Request request = new Request.Builder()
                                    .url(goProDevice.stopStream_query)
                                    .build();
                            Log.d("HTTP GET", goProDevice.stopStream_query);
                            try (Response response = client.newCall(request).execute()) {
                                if (response.isSuccessful()) {
                                    String resp_Str = "" + response.body().string();
                                    Log.d("HTTP GET response", resp_Str);
                                    // preview stream available
                                    runOnUiThread(() -> {
                                        Intent previewActivityIntent = new Intent(MainActivity.this, PreviewActivity.class);
                                        startActivity(previewActivityIntent);
                                    });
                                } else if (response.code() == 404) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(getApplicationContext(), "Doesn't work! Is the GoPro firmware up to date?", Toast.LENGTH_SHORT).show();
                                    });
                                } else {
                                    runOnUiThread(() -> {
                                        Toast.makeText(getApplicationContext(), "The preview stream is currently unavailable", Toast.LENGTH_SHORT).show();
                                    });
                                }
                            } catch (Exception ex) {
                                Log.e("HTTP GET error", ex.toString());
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(), "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                                });
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
                        runOnUiThread(() -> Toast.makeText(this, "Something went wrong. Is the GoPro powered on?", Toast.LENGTH_SHORT).show());

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

    private void parseMediaList(JSONObject medialist, GoProDevice goProDevice) {
        ArrayList<GoMediaFile> goMediaFiles = new ArrayList<>();

        try {
            JSONArray medias = medialist.getJSONArray("media");

            for (int i1 = 0; i1 < medias.length(); i1++) {
                JSONObject media = medias.getJSONObject(i1);
                String dir_name = media.getString("d");
                JSONArray files = media.getJSONArray("fs");

                for (int i2 = 0; i2 < files.length(); i2++) {
                    GoMediaFile goMediaFile = new GoMediaFile();
                    JSONObject file = files.getJSONObject(i2);

                    if (file.has("g")) {
                        goMediaFile.isGroup = true;
                        if (file.has("b") && file.has("l")) {
                            goMediaFile.groupBegin = file.getString("b");
                            goMediaFile.groupLast = file.getString("l");
                        }
                    }

                    goMediaFile.fileName = file.getString("n");
                    goMediaFile.extension = goMediaFile.fileName.substring(goMediaFile.fileName.lastIndexOf('.')).toLowerCase();
                    goMediaFile.url = "http://10.5.5.9:8080/videos/DCIM/" + dir_name + "/" + goMediaFile.fileName;
                    goMediaFile.thumbNail_path = goProDevice.getThumbNail_query + dir_name + "/" + goMediaFile.fileName;

                    long lastModifiedS = Long.parseLong(file.getString("mod"));
                    long lastModifiedMs = lastModifiedS * 1000;
                    goMediaFile.lastModified = new Date(lastModifiedMs);

                    goMediaFile.fileByteSize = Long.parseLong(file.getString("s"));

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

    public void onClickPair(View v) {
        Intent i = new Intent(getApplicationContext(), PairActivity.class);
        startActivity(i);
    }

    @SuppressLint("MissingPermission")
    public void onClickShutterOn(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), "Please connect/pair devices", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
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
            goProDevices.get(i).highlight();
        }
    }

    private void startApp() {
        loadSettingsValuesFromRes();

        btn_pair.setEnabled(true);
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
                        GoProDevice goProDevice = new GoProDevice(getApplicationContext(), deviceName);
                        goProDevice.bluetoothDevice = device;
                        goProDevice.btPaired = true;
                        goProDevice.name = deviceName;
                        goProDevice.btMacAddr = deviceHardwareAddress;
                        goProDevice.settingsValues = settingsValues;
                        if (MyApplication.checkBtDevConnected(device)) {
                            goProDevice.disconnectBt();
                            goProDevice.btConnectionStage = BT_NOT_CONNECTED;
                        }
                        goProDevices.add(goProDevice);
                        goListView.setAdapter(goListAdapter);
                    }
                }
            }
        }
    }

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

        if (settingsValues == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "An error happened. Please restart the app!", Toast.LENGTH_SHORT).show();
                finish();
            });
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