package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_CONNECTING;
import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_FETCHING_DATA;
import static com.sepp89117.goeasypro_android.gopro.GoProDevice.BT_NOT_CONNECTED;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import java.util.ArrayList;
import java.util.Objects;

public class PairActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayList<String> mDeviceStrList;
    private ArrayList<GoProDevice> pairedGoProDevices;
    private ArrayList<GoProDevice> newGoProDevices;
    private BluetoothAdapter bluetoothAdapter;
    private ImageView bt_search_symbol;
    private Button btn_scan;
    private Animation fadeAnimation;

    private static final int LIGHT_BLUE = Color.argb(255, 0, 0x9F, 0xe0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair);

        //get globals
        bluetoothAdapter = ((MyApplication) this.getApplication()).getBluetoothAdapter();
        pairedGoProDevices = ((MyApplication) this.getApplication()).getGoProDevices();

        newGoProDevices = new ArrayList<>();

        //set mDeviceStrList
        mDeviceStrList = new ArrayList<>();
        /*for (int i = 0; i < pairedGoProDevices.size(); i++) {
            GoProDevice goProDevice = pairedGoProDevices.get(i);

            if (goProDevice.btConnectionStage == BT_CONNECTED) {
                mDeviceStrList.add(i, goProDevice.name + " (connected)\n" + goProDevice.btMacAddress); //green
            } else if (goProDevice.btConnectionStage == BT_CONNECTING || goProDevice.btConnectionStage == BT_FETCHING_DATA) {
                mDeviceStrList.add(goProDevice.name + " (connecting...)\n" + goProDevice.btMacAddress); //red
            } else if (goProDevice.btPaired) {
                mDeviceStrList.add(goProDevice.name + " (paired but not found)\n" + goProDevice.btMacAddress); //red
            } else {
                mDeviceStrList.add(i, goProDevice.name + " (disconnected)\n" + goProDevice.btMacAddress); //red
            }
        }*/

        listView = findViewById(R.id.file_listView);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            GoProDevice newGoProDevice = newGoProDevices.get(position);
            if (!newGoProDevice.btPaired) {
                AlertDialog alert = new AlertDialog.Builder(PairActivity.this)
                        .setTitle("Pairing")
                        .setMessage("Please wait while the pairing is established!")
                        .setCancelable(true)
                        .create();
                alert.show();

                mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (pairing...)\n" + newGoProDevice.btMacAddress); //yellow
                newGoProDevice.pair(
                        paired -> {
                            if (paired) {
                                pairedGoProDevices.add(newGoProDevice);
                                newGoProDevice.connectBt(
                                        connected -> {
                                            alert.dismiss();
                                            if (connected)
                                                mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (connected)\n" + newGoProDevice.btMacAddress); //green
                                            else
                                                mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (disconnected)\n" + newGoProDevice.btMacAddress); //red

                                            setListAdapter();
                                        });
                            } else {
                                alert.dismiss();
                                Toast.makeText(getApplicationContext(), "Pairing '" + newGoProDevice.btDeviceName + "' failed!", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                if (newGoProDevice.btConnectionStage != BT_NOT_CONNECTED) {
                    Toast.makeText(getApplicationContext(), "Device always connected!", Toast.LENGTH_SHORT).show();
                } else {
                    mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (connecting...)\n" + newGoProDevice.btMacAddress); //yellow
                    setListAdapter();
                    newGoProDevice.connectBt(connected -> {
                        if (connected) {
                            if (newGoProDevice.btConnectionStage == BT_CONNECTED) {
                                mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (connected)\n" + newGoProDevice.btMacAddress); //green
                            } else if (newGoProDevice.btConnectionStage == BT_CONNECTING || newGoProDevice.btConnectionStage == BT_FETCHING_DATA) {
                                mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (connecting...)\n" + newGoProDevice.btMacAddress); //red
                            } else {
                                mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (disconnected)\n" + newGoProDevice.btMacAddress); //red
                            }
                        } else {
                            mDeviceStrList.set(position, newGoProDevice.btDeviceName + " (disconnected)\n" + newGoProDevice.btMacAddress); //red
                        }
                        setListAdapter();
                    });
                }
            }
        });
        setListAdapter();

        bt_search_symbol = findViewById(R.id.bt_search_symbol);
        btn_scan = findViewById(R.id.buttonScan);
        fadeAnimation = AnimationUtils.loadAnimation(this, R.anim.tween);

        if (isBtEnabled()) {
            findBtDevices();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        ((MyApplication) this.getApplication()).resetIsAppPaused();
    }

    public void onClickScan(View v) {
        if (isBtEnabled()) {
            findBtDevices();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(receiver);
        } catch (Exception ignored) {

        }
    }

    @SuppressLint("MissingPermission")
    private void findBtDevices() {
        /*Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                if (deviceName != null && deviceName.contains("GoPro ")) {
                    boolean inList = false;
                    if (pairedGoProDevices.size() > 0) {
                        for (int i = 0; i < pairedGoProDevices.size(); i++) {
                            GoProDevice goProDevice = pairedGoProDevices.get(i);
                            if (Objects.equals(goProDevice.btMacAddress, device.getAddress())) {
                                inList = true;
                            }
                        }
                    }

                    if (!inList) {
                        GoProDevice goProDevice = new GoProDevice(PairActivity.this, this.getApplication(), deviceName);
                        goProDevice.bluetoothDevice = device;
                        goProDevice.btPaired = true;
                        //goProDevice.name = deviceName;
                        goProDevice.btMacAddress = deviceHardwareAddress;

                        if (MyApplication.checkBtDevConnected(device)) {
                            goProDevice.disconnectBt();
                            goProDevice.btConnectionStage = BT_NOT_CONNECTED;
                        }

                        pairedGoProDevices.add(goProDevice);
                        if (goProDevice.btConnectionStage == BT_CONNECTED) {
                            mDeviceStrList.add(deviceName + " (connected)\n" + deviceHardwareAddress); //green
                        } else if (goProDevice.btConnectionStage == BT_CONNECTING || goProDevice.btConnectionStage == BT_FETCHING_DATA) {
                            mDeviceStrList.add(goProDevice.name + " (connecting...)\n" + goProDevice.btMacAddress); //red
                        } else {
                            mDeviceStrList.add(deviceName + " (paired but not found)\n" + deviceHardwareAddress); //red
                        }
                        setListAdapter();
                    }
                }
            }
        }*/

        // Register for broadcasts when a device is discovered.
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        registerReceiver(receiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(receiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        registerReceiver(receiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        bluetoothAdapter.startDiscovery();
    }

    private void setListAdapter() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                listView.setAdapter(new ArrayAdapter<String>(getApplicationContext(), R.layout.bt_listitem, mDeviceStrList) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        TextView textView = (TextView) super.getView(position, convertView, parent);

                        String text = textView.getText().toString();
                        text = text.substring(text.indexOf('(') + 1);
                        text = text.substring(0, text.indexOf(')'));
                        switch (text) {
                            case "connected":
                                //green
                                textView.setTextColor(Color.GREEN);
                                break;
                            case "paired but not found":
                            case "found but not paired":
                            case "disconnected":
                                //red
                                textView.setTextColor(Color.RED);
                                break;
                            case "found paired":
                                //yellow
                                textView.setTextColor(Color.YELLOW);
                                break;
                            case "pairing...":
                            case "connecting...":
                                textView.setTextColor(LIGHT_BLUE);
                                break;
                            default:
                                textView.setTextColor(Color.WHITE);
                        }


                        return textView;
                    }
                });
            }
        });
    }

    @SuppressLint("MissingPermission")
    // Create a BroadcastReceiver for ACTION_FOUND.
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address

                //iterate paired devices for equals
                for (int i = 0; i < pairedGoProDevices.size(); i++) {
                    String iAddress = pairedGoProDevices.get(i).btMacAddress;
                    if (Objects.equals(iAddress, deviceHardwareAddress)) {
                        pairedGoProDevices.get(i).camBtAvailable = true;

                        //gopro is paired & found
                        /*if (pairedGoProDevices.get(i).btConnectionStage != BT_CONNECTED) {
                            mDeviceStrList.set(i, deviceName + " (found paired)\n" + deviceHardwareAddress);
                            setListAdapter();
                        }*/
                        return;
                    }
                }

                //is not a paired device
                if (deviceName != null && deviceName.contains("GoPro ")) {
                    GoProDevice goProDevice = new GoProDevice(PairActivity.this, PairActivity.this.getApplication(), device);
                    goProDevice.btMacAddress = deviceHardwareAddress;
                    goProDevice.camBtAvailable = true;

                    for (int i = 0; i < newGoProDevices.size(); i++) {
                        String iAddress = newGoProDevices.get(i).btMacAddress;
                        if (Objects.equals(iAddress, deviceHardwareAddress)) {
                            return;
                        }
                    }

                    newGoProDevices.add(goProDevice);
                    mDeviceStrList.add(deviceName + " (found but not paired)\n" + deviceHardwareAddress); //red
                    setListAdapter();
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    isBtEnabled();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                btn_scan.setEnabled(false);
                Toast.makeText(getApplicationContext(), "Discovering nearby bluetooth devices...", Toast.LENGTH_SHORT).show();
                bt_search_symbol.setVisibility(View.VISIBLE);
                bt_search_symbol.startAnimation(fadeAnimation);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                btn_scan.setEnabled(true);
                fadeAnimation.cancel();
                bt_search_symbol.setVisibility(View.INVISIBLE);
                Toast.makeText(getApplicationContext(), "Discovery finished!", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private boolean isBtEnabled() {
        //check bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            //request bluetooth enable
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityIntent.launch(enableBtIntent);
            return false;
        }
        return true;
    }

    //on activate bt result
    ActivityResultLauncher<Intent> startActivityIntent = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (isBtEnabled()) {
                    findBtDevices();
                }
            });
}