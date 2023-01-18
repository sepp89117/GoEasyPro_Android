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
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private boolean wifi_granted = false;

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

    private int lastCamClickedIndex = -1;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        MenuInflater inflater = getMenuInflater();
        if(lastCamClickedIndex < 0) {
            lastCamClickedIndex = info.position;
        }

        if (goProDevices.get(lastCamClickedIndex).btConnectionStage == BT_CONNECTED)
            inflater.inflate(R.menu.connected_dev_menu, menu);
        else if (goProDevices.get(lastCamClickedIndex).btConnectionStage == BT_NOT_CONNECTED)
            inflater.inflate(R.menu.not_connected_dev_menu, menu);
    }

    // menu item select listener
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = lastCamClickedIndex;

        switch (item.getItemId()) {
            case R.id.locate_on:
                //Locate on
                goProDevices.get(position).locateOn();
                break;
            case R.id.locate_off:
                //Locate: off
                goProDevices.get(position).locateOff();
                break;
            case R.id.shutter_on:
                //Shutter: on
                goProDevices.get(position).shutterOn();
                break;
            case R.id.shutter_off:
                //Shutter: off
                goProDevices.get(position).shutterOff();
                break;
            case R.id.ap_on:
                //Shutter: on
                goProDevices.get(position).wifiApOn();
                break;
            case R.id.ap_off:
                //Shutter: off
                goProDevices.get(position).wifiApOff();
                break;
            case R.id.put_sleep:
                //Put to sleep
                goProDevices.get(position).sleep();
                break;
            case R.id.set_date_time:
                //Set Date/Time
                goProDevices.get(position).setDateTime();
                break;
            case R.id.browse:
                // Browse storage
                if (isGpsEnabled()) {
                    if (hasWifiPermissions()) {
                        if (Objects.equals(goProDevices.get(position).startStream_query, "")) {
                            Log.e("goProDevices", "ModelID unknown!");
                            return true;
                        }

                        AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Connecting")
                                .setMessage("Please wait while the WiFi connection is established!")
                                .setCancelable(true)
                                .create();

                        alert.show();

                        goProDevices.get(position).connectWifi(() -> {
                            alert.dismiss();
                            //onWifiConnected
                            Request request = new Request.Builder()
                                    .url(goProDevices.get(position).getMediaList_query)
                                    .build();
                            Log.d("HTTP GET", goProDevices.get(position).getMediaList_query);
                            try (Response response = client.newCall(request).execute()) {
                                if (response.isSuccessful()) {
                                    Log.d("HTTP GET", "successful");
                                    String resp_Str = response.body().string();
                                    JSONObject mainObject = new JSONObject(resp_Str);

                                    runOnUiThread(() -> {
                                        ((MyApplication) this.getApplication()).setFocusedDevice(goProDevices.get(position));
                                    });
                                    parseMediaList(mainObject, goProDevices.get(position));
                                }
                            } catch (Exception ex) {
                                Log.e("HTTP GET error", ex.toString());
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                            }

                        });
                    } else {
                        requestWifiPermissions();
                    }
                }

                break;
            case R.id.preview:
                // Live view
                if (isGpsEnabled()) {
                    if (hasWifiPermissions()) {
                        if (Objects.equals(goProDevices.get(position).startStream_query, "")) {
                            Log.e("goProDevices", "ModelID unknown!");
                            return true;
                        }

                        AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Connecting")
                                .setMessage("Please wait while the WiFi connection is established!")
                                .setCancelable(true)
                                .create();

                        alert.show();

                        goProDevices.get(position).connectWifi(() -> {
                            alert.dismiss();
                            //onWifiConnected
                            Request request = new Request.Builder()
                                    .url(goProDevices.get(position).stopStream_query)
                                    .build();
                            Log.d("HTTP GET", goProDevices.get(position).stopStream_query);
                            try (Response response = client.newCall(request).execute()) {
                                if (response.isSuccessful()) {
                                    String resp_Str = response.body().string();
                                    Log.d("HTTP GET response", resp_Str);
                                    JSONObject mainObject = new JSONObject(resp_Str);
                                    if (mainObject.getString("status").equals("0")) {
                                        //live stream available
                                        runOnUiThread(() -> {
                                            ((MyApplication) this.getApplication()).setFocusedDevice(goProDevices.get(position));
                                            startStream();
                                        });
                                    } else {
                                        runOnUiThread(() -> {
                                            Toast.makeText(getApplicationContext(), "The live stream is currently unavailable", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                }
                            } catch (Exception ex) {
                                Log.e("HTTP GET error", ex.toString());
                                runOnUiThread(() -> {
                                    Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_SHORT).show();
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
                goProDevices.get(position).disconnectBt();
                break;
            case R.id.try_connect:
                //Try to connect
                goProDevices.get(position).connectBt(connected -> {
                    runOnUiThread(() -> goListView.setAdapter(goListAdapter));
                });
                break;
            case R.id.del_device:
                //Delete device
                AlertDialog alert = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Delete device")
                        .setMessage("Are you sure you want to delete " + goProDevices.get(position).name + "?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (goProDevices.get(position).removeBtBond())
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

                    goMediaFile.fileName = file.getString("n");
                    goMediaFile.path = "http://10.5.5.9:8080/videos/DCIM/" + dir_name + "/" + goMediaFile.fileName;
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

    private void startStream() {
        Intent previewActivityIntent = new Intent(MainActivity.this, PreviewActivity.class);
        startActivity(previewActivityIntent);
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

    /*@SuppressLint("MissingPermission")
    public void onClickWifiApOn(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), "Please connect/pair devices", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            goProDevices.get(i).wifiApOn();
        }
    }

    @SuppressLint("MissingPermission")
    public void onClickWifiApOff(View v) {
        if (goProDevices.size() <= 0) {
            Toast.makeText(getApplicationContext(), "Please connect/pair devices", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < goProDevices.size(); i++) {
            goProDevices.get(i).wifiApOff();
        }
    }*/

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
                            if (Objects.equals(goProDevice.Address, device.getAddress())) {
                                inList = true;
                            }
                        }
                    }
                    if (!inList) {
                        GoProDevice goProDevice = new GoProDevice();
                        goProDevice.context = getApplicationContext();
                        goProDevice.bluetoothDevice = device;
                        goProDevice.btPaired = true;
                        goProDevice.name = deviceName;
                        goProDevice.Address = deviceHardwareAddress;
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