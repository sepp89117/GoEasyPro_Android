package com.sepp89117.goeasypro_android.helpers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.IpConfiguration;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.StaticIpConfiguration;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.sepp89117.goeasypro_android.gopro.GoProDevice;

import java.util.List;
import java.util.Objects;

public class WiFiHelper {
    private static final String TAG = "WiFiHelper";
    public static final int WIFI_DISCONNECTED = 0;
    public static final int WIFI_CONNECTED = 1;

    private final Context _context;
    private final WifiManager _wifiManager;
    private ConnectivityManager _connectivityManager = null;
    private BroadcastReceiver _mConnectionReceiver;
    private int _wifiConnectionState = WIFI_DISCONNECTED;
    private String _ssid = "";
    private String _psk = "";
    private String _bssid = "";
    private final GoProDevice.WifiConnectionChangedInterface _wifiConnectionChangedInterface;

    public WiFiHelper(Context context, GoProDevice.WifiConnectionChangedInterface wifiConnectionChangedCallback) throws Exception {
        _context = context;
        _wifiConnectionChangedInterface = wifiConnectionChangedCallback;

        _wifiManager = (WifiManager) _context.getSystemService(Context.WIFI_SERVICE);
        if (_wifiManager == null)
            throw new Exception("WifiManager was null");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            _connectivityManager = (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (_connectivityManager == null)
                throw new Exception("ConnectivityManager was null");
        }
    }

    public boolean isWifiEnabled() {
        // return Settings.Global.getInt(_context.getContentResolver(), Settings.Global.WIFI_ON, 0) == 1;
        return _wifiManager.isWifiEnabled();
    }

    public void enableWifi() {
        if (!isWifiEnabled())
            _wifiManager.setWifiEnabled(true);
    }

    public void disableWifi() {
        if (isWifiEnabled())
            _wifiManager.setWifiEnabled(false);
    }

    public boolean isConnected() {
        return _wifiConnectionState == WIFI_CONNECTED;
    }

    @SuppressLint("MissingPermission")
    public void connectWifi(String SSID, String PSK, String BSSID) throws Exception {
        _ssid = SSID;
        _psk = PSK;
        _bssid = BSSID;

        if (_ssid == null || Objects.equals(_ssid, "")) {
            throw new Exception("SSID can't be null or empty");
        } else if (_psk == null || Objects.equals(_psk, "")) {
            throw new Exception("PSK can't be null or empty");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (_bssid == null || Objects.equals(_bssid, ""))
                throw new Exception("BSSID can't be null or empty");

            new Thread(this::connectWifiAndroidQUp).start();
        } else {
            new Thread(this::connectWifiAndroidPDown).start();
        }
    }

    public void disconnectWifi() {
        if (_wifiConnectionState == WIFI_DISCONNECTED) return;

        disconnect();
    }

    private void disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            _connectivityManager.unregisterNetworkCallback(networkCallback);
            _connectivityManager.bindProcessToNetwork(null);
        } else {
            try {
                _context.unregisterReceiver(_mConnectionReceiver);
            } catch (Exception ignored) {
            }
            try {
                _wifiManager.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "An exception occurred while attempting to disconnect build version < Q.", e);
            }
        }

        setState(WIFI_DISCONNECTED);
    }

    private void setState(int wifiConnectionState) {
        _wifiConnectionState = wifiConnectionState;

        if (_wifiConnectionChangedInterface != null)
            _wifiConnectionChangedInterface.onWifiConnectionChanged();
    }

    @SuppressLint("MissingPermission")
    private void connectWifiAndroidPDown() {
        WifiManager wifiManager = (WifiManager) _context.getSystemService(Context.WIFI_SERVICE);
        boolean mustConnect = false;
        int netId = -1;
        // Check if this network is already known
        WifiConfiguration wifiConfiguration = null;
        List<WifiConfiguration> list = wifiManager.getConfiguredNetworks(); // deprecated in API level 29 (Android 10/Q)
        for (WifiConfiguration _wifiConfiguration : list) {
            if (_wifiConfiguration.SSID != null && _wifiConfiguration.SSID.equals('"' + _ssid + '"')) {
                wifiConfiguration = _wifiConfiguration;
                break;
            }
        }

        boolean otherWifiIsConnected = false;

        if (wifiConfiguration == null) {
            // Create WifiConfiguration instance
            WifiConfiguration newWifiConfiguration = new WifiConfiguration(); // deprecated in API level 29 (Android 10/Q)
            newWifiConfiguration.SSID = String.format("\"%s\"", _ssid);
            newWifiConfiguration.preSharedKey = String.format("\"%s\"", _psk);
            // Add it to Android wifi manager settings
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
                wifiConfiguration.preSharedKey = String.format("\"%s\"", _psk);
            }
        }

        if (mustConnect) {
            // Connect to it
            if (otherWifiIsConnected) wifiManager.disconnect();

            _mConnectionReceiver = new WifiConnectionMonitor();
            IntentFilter aFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            aFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            _context.registerReceiver(_mConnectionReceiver, aFilter);

            if (wifiManager.enableNetwork(netId, true)) {
                wifiManager.reconnect();
            } else {
                setState(WIFI_DISCONNECTED);
            }
        } else {
            setState(WIFI_CONNECTED);
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
                String goproSSID = String.format("\"%s\"", _ssid);

                if (!Objects.equals(eventSSID, goproSSID)) return;

                String reason = networkInfo.getReason();
                if (reason != null) {
                    Log.e(TAG, "WiFi-Connect error reason: " + reason + "; State: " + networkInfo.getState().name());
                    setState(WIFI_DISCONNECTED);

                    try {
                        _context.unregisterReceiver(_mConnectionReceiver);
                    } catch (Exception ignored) {
                    }
                } else if (networkInfo.isConnected()) {
                    WifiManager wifiManager = (WifiManager) _context.getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                    if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored) {
                        }

                        setState(WIFI_CONNECTED);

                        try {
                            _context.unregisterReceiver(_mConnectionReceiver);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void connectWifiAndroidQUp() {
        if (_wifiConnectionState == WIFI_CONNECTED) {
            if (_wifiConnectionChangedInterface != null)
                _wifiConnectionChangedInterface.onWifiConnectionChanged();
            return;
        }
        final WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                .setSsid(_ssid)
                .setBssid(MacAddress.fromString(_bssid))
                .setWpa2Passphrase(_psk)
                .build();

        final NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .setNetworkSpecifier(wifiNetworkSpecifier)
                .build();

        HandlerThread handlerThread = new HandlerThread("WiFi Manager Network Update Handler");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        _connectivityManager.requestNetwork(networkRequest, networkCallback, handler);
    }

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            _connectivityManager.bindProcessToNetwork(network);
            setState(WIFI_CONNECTED);
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            disconnect();
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            disconnect();
        }
    };

}
