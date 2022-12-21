package com.sepp89117.goeasypro_android;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import android.widget.AdapterView;
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

            if (goProDevice.connected)
                mDeviceStrList.add(i, goProDevice.Name + " (connected)\n" + goProDevice.Address); //green
            else if (goProDevice.paired) {
                mDeviceStrList.add(goProDevice.Name + " (paired but not found)\n" + goProDevice.Address); //red
            } else {
                mDeviceStrList.add(i, goProDevice.Name + " (disconnected)\n" + goProDevice.Address); //green
            }

        }


        listView = (ListView) findViewById(R.id.listView);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            GoProDevice goProDevice = goProDevices.get(position);
            if (!goProDevice.paired) {
                Toast.makeText(getApplicationContext(), "Pairing device...", Toast.LENGTH_SHORT).show();
                goProDevice.pair(paired -> {
                    if (paired) {
                        Toast.makeText(getApplicationContext(), "Pairing '" + goProDevice.Name + "' succesfull!", Toast.LENGTH_SHORT).show();
                        goProDevice.connect(connected -> {
                            if (connected)
                                mDeviceStrList.set(position, goProDevice.Name + " (connected)\n" + goProDevice.Address); //green
                            else
                                mDeviceStrList.set(position, goProDevice.Name + " (disconnected)\n" + goProDevice.Address); //red

                            setListAdapter();
                        });
                    } else {
                        Toast.makeText(getApplicationContext(), "Pairing '" + goProDevice.Name + "' failed!", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                if (goProDevice.connected) {
                    Toast.makeText(getApplicationContext(), "Device always connected!", Toast.LENGTH_SHORT).show();
                } else {
                    goProDevice.connect(connected -> {
                        if (connected)
                            mDeviceStrList.set(position, goProDevice.Name + " (connected)\n" + goProDevice.Address); //green
                        else
                            mDeviceStrList.set(position, goProDevice.Name + " (disconnected)\n" + goProDevice.Address); //red

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
        } catch (Exception ex) {

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
                        goProDevice.paired = true;
                        goProDevice.Name = deviceName;
                        goProDevice.Address = deviceHardwareAddress;
                        //try {
                        //Method m = device.getClass().getMethod("isConnected", (Class[]) null);
                        //boolean connected = (boolean) m.invoke(device, (Object[]) null);
                        goProDevice.connected = MyApplication.checkBtDevConnected(device);
                        //} catch (Exception e) {
                        //    //throw new IllegalStateException(e);
                        //}

                        goProDevices.add(goProDevice);
                        if (goProDevice.connected)
                            mDeviceStrList.add(deviceName + " (connected)\n" + deviceHardwareAddress); //green
                        else
                            mDeviceStrList.add(deviceName + " (paired but not found)\n" + deviceHardwareAddress); //red

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
                                //red
                                textView.setTextColor(Color.RED);
                                break;
                            case "found paired":
                                //yellow
                                textView.setTextColor(Color.YELLOW);
                                break;
                            default:
                                textView.setTextColor(Color.BLUE);
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
                for (int i = 0; i < goProDevices.size(); i++) {
                    String iAddress = goProDevices.get(i).bluetoothDevice.getAddress();
                    if (Objects.equals(iAddress, deviceHardwareAddress)) {
                        //gopro is paired & found
                        if (!goProDevices.get(i).connected) {
                            mDeviceStrList.set(i, deviceName + " (found paired)\n" + deviceHardwareAddress);
                            setListAdapter();
                            //listView.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, mDeviceStrList)); //yellow
                        }
                        return;
                    }
                }
                //is not a paired device
                if (deviceName != null && deviceName.contains("GoPro ")) {
                    GoProDevice goProDevice = new GoProDevice();
                    goProDevice.context = getApplicationContext();
                    goProDevice.bluetoothDevice = device;
                    goProDevice.Name = deviceName;
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