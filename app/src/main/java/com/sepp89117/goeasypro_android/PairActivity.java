package com.sepp89117.goeasypro_android;

import static com.sepp89117.goeasypro_android.GoProDevice.BT_CONNECTED;
import static com.sepp89117.goeasypro_android.GoProDevice.BT_CONNECTING;
import static com.sepp89117.goeasypro_android.GoProDevice.BT_FETCHING_DATA;
import static com.sepp89117.goeasypro_android.GoProDevice.BT_NOT_CONNECTED;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public class PairActivity extends AppCompatActivity {


    private ListView listView;
    private ArrayList<String> mDeviceStrList;
    private ArrayList<GoProDevice> goProDevices;
    private BluetoothAdapter bluetoothAdapter;
    private ImageView bt_search_symbol;
    private Button btn_scan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair);

        //get globals
        bluetoothAdapter = ((MyApplication) this.getApplication()).getBluetoothAdapter();
        goProDevices = ((MyApplication) this.getApplication()).getGoProDevices();

        //set mDeviceStrList
        mDeviceStrList = new ArrayList<String>();
        for (int i = 0; i < goProDevices.size(); i++) {
            GoProDevice goProDevice = goProDevices.get(i);

            if (goProDevice.btConnectionStage == BT_CONNECTED) {
                mDeviceStrList.add(i, goProDevice.name + " (connected)\n" + goProDevice.Address); //green
            } else if (goProDevice.btConnectionStage == BT_CONNECTING || goProDevice.btConnectionStage == BT_FETCHING_DATA) {
                mDeviceStrList.add(goProDevice.name + " (connecting...)\n" + goProDevice.Address); //red
            } else if (goProDevice.btPaired) {
                mDeviceStrList.add(goProDevice.name + " (paired but not found)\n" + goProDevice.Address); //red
            } else {
                mDeviceStrList.add(i, goProDevice.name + " (disconnected)\n" + goProDevice.Address); //red
            }

        }

        listView = (ListView) findViewById(R.id.file_listView);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            GoProDevice goProDevice = goProDevices.get(position);
            if (!goProDevice.btPaired) {
                AlertDialog alert = new AlertDialog.Builder(PairActivity.this)
                        .setTitle("Pairing")
                        .setMessage("Please wait while the pairing is established!")
                        .setCancelable(true)
                        .create();
                alert.show();

                //Toast.makeText(getApplicationContext(), "Pairing device...", Toast.LENGTH_SHORT).show();
                mDeviceStrList.set(position, goProDevice.name + " (pairing...)\n" + goProDevice.Address); //yellow
                goProDevice.pair(paired -> {
                    if (paired) {
                        goProDevice.connectBt(connected -> {
                            alert.dismiss();
                            if (connected)
                                mDeviceStrList.set(position, goProDevice.name + " (connected)\n" + goProDevice.Address); //green
                            else
                                mDeviceStrList.set(position, goProDevice.name + " (disconnected)\n" + goProDevice.Address); //red

                            setListAdapter();
                        });
                    } else {
                        alert.dismiss();
                        Toast.makeText(getApplicationContext(), "Pairing '" + goProDevice.name + "' failed!", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                if (goProDevice.btConnectionStage != BT_NOT_CONNECTED) {
                    Toast.makeText(getApplicationContext(), "Device always connected!", Toast.LENGTH_SHORT).show();
                } else {
                    mDeviceStrList.set(position, goProDevice.name + " (connecting...)\n" + goProDevice.Address); //yellow
                    setListAdapter();
                    goProDevice.connectBt(connected -> {
                        if (connected) {
                            if (goProDevice.btConnectionStage == BT_CONNECTED) {
                                mDeviceStrList.set(position, goProDevice.name + " (connected)\n" + goProDevice.Address); //green
                            } else if (goProDevice.btConnectionStage == BT_CONNECTING || goProDevice.btConnectionStage == BT_FETCHING_DATA) {
                                mDeviceStrList.set(position, goProDevice.name + " (connecting...)\n" + goProDevice.Address); //red
                            } else {
                                mDeviceStrList.set(position, goProDevice.name + " (disconnected)\n" + goProDevice.Address); //red
                            }
                        } else {
                            mDeviceStrList.set(position, goProDevice.name + " (disconnected)\n" + goProDevice.Address); //red
                        }
                        setListAdapter();
                    });
                }
            }
        });
        setListAdapter();

        bt_search_symbol = (ImageView) findViewById(R.id.bt_search_symbol);
        btn_scan = (Button) findViewById(R.id.buttonScan);

        if (isBtEnabled()) {
            findBtDevices();
        }
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
                        //try {
                        //Method m = device.getClass().getMethod("isConnected", (Class[]) null);
                        //boolean connected = (boolean) m.invoke(device, (Object[]) null);
                        if (MyApplication.checkBtDevConnected(device)) {
                            goProDevice.disconnectBt();
                            goProDevice.btConnectionStage = BT_NOT_CONNECTED;
                        }
                        //} catch (Exception e) {
                        //    //throw new IllegalStateException(e);
                        //}

                        goProDevices.add(goProDevice);
                        if (goProDevice.btConnectionStage == BT_CONNECTED) {
                            mDeviceStrList.add(deviceName + " (connected)\n" + deviceHardwareAddress); //green
                        } else if (goProDevice.btConnectionStage == BT_CONNECTING || goProDevice.btConnectionStage == BT_FETCHING_DATA) {
                            mDeviceStrList.add(goProDevice.name + " (connecting...)\n" + goProDevice.Address); //red
                        } else {
                            mDeviceStrList.add(deviceName + " (paired but not found)\n" + deviceHardwareAddress); //red
                        }
                        setListAdapter();
                    }
                }
            }
        }

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
                                textView.setTextColor(LIGHTBLUE);
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

    private static final int LIGHTBLUE = Color.argb(255, 0, 0x9F, 0xe0);
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
                for (int i = 0; i < goProDevices.size(); i++) {
                    String iAddress = goProDevices.get(i).bluetoothDevice.getAddress();
                    if (Objects.equals(iAddress, deviceHardwareAddress)) {
                        //gopro is paired & found
                        if (goProDevices.get(i).btConnectionStage != BT_CONNECTED) {
                            mDeviceStrList.set(i, deviceName + " (found paired)\n" + deviceHardwareAddress);
                            setListAdapter();
                        }
                        return;
                    }
                }
                //is not a paired device
                if (deviceName != null && deviceName.contains("GoPro ")) {
                    GoProDevice goProDevice = new GoProDevice();
                    goProDevice.context = getApplicationContext();
                    goProDevice.bluetoothDevice = device;
                    goProDevice.name = deviceName;
                    goProDevice.Address = deviceHardwareAddress;
                    goProDevices.add(goProDevice);
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
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                btn_scan.setEnabled(true);
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