package com.example.notify.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.notify.services.AuthenticateConnection;
import com.example.notify.services.MDNSDiscovery;

import java.util.HashSet;
import java.util.Set;

public class NetworkDiscovery extends ConnectivityManager.NetworkCallback {

    public final String TAG = "Notifi:NetworkDiscovery";
    private final ConnectivityManager cm;
    private final Context context;
    public static String serverIP;
    public static String serverDeviceName;
    public static String serverDeviceID;
    public static int httpPort;

    private final Set<Integer> activeTransports = new HashSet<>();
    public static boolean isConnectedToLAN = false;
    private static boolean isSearching = false;
    private MDNSDiscovery.OnServiceFoundListener listener;
    private static MDNSDiscovery activeMdnsDiscovery;
    private static final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private static final Runnable timeoutRunnable = () -> {
        Log.w("Notifi:NetworkDiscovery", "MDNS Discovery timed out after 30s");
        stopMdnsDiscovery();
    };

    private boolean isRegistered = false;

    public NetworkDiscovery(Context context) {
        this.context = context.getApplicationContext();
        this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public NetworkDiscovery(MDNSDiscovery.OnServiceFoundListener listener, Context context) {
        this.listener = listener;
        this.context = context.getApplicationContext();
        this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void register() {
        if (isRegistered) return;
        try {
            // Register for all networks to ensure we catch every transition.
            // Filtering happens in onAvailable.
            NetworkRequest networkRequest = new NetworkRequest.Builder().build();
            cm.registerNetworkCallback(networkRequest, this);
            isRegistered = true;
            Log.d(TAG, "NetworkCallback registered for background monitoring");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }

    public void unregister() {
        try {
            stopMdnsDiscovery();
            this.listener = null;
            if (isRegistered) {
                cm.unregisterNetworkCallback(this);
                isRegistered = false;
                Log.d(TAG, "NetworkCallback unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network callback", e);
        }
    }

    public static void stopMdnsDiscovery() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        if (activeMdnsDiscovery != null) {
            Log.d("Notifi:NetworkDiscovery", "Stopping active MDNS discovery");
            activeMdnsDiscovery.stopDiscovery();
            activeMdnsDiscovery = null;
        }
        isSearching = false;
    }

    public boolean isWifiConnected() {
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public boolean isCellularConnected() {
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onAvailable(Network network) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return;
        
        Log.d(TAG, "onAvailable: " + network + " | Caps: " + caps);
        SharedPreferences sharedPref = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Log.d(TAG, "WiFi detected");
            activeTransports.add(NetworkCapabilities.TRANSPORT_WIFI);

            // 1s delay to ensure routing tables are ready
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (listener != null) {
                    connectLAN(listener);
                } else if (sharedPref.getBoolean(Constants.IS_DEVICE_SETUP, false)) {
                    Log.d(TAG, "Service detected WiFi. Auto-reconnecting to PC...");
                    new AuthenticateConnection(context).reconnectLastDevice();
                }
            }, 1000);
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            Log.d(TAG, "Cellular detected");
            activeTransports.add(NetworkCapabilities.TRANSPORT_CELLULAR);
        }
    }

    @Override
    public void onLost(Network network) {
        Log.d(TAG, "Network lost event");
        if (activeTransports.contains(NetworkCapabilities.TRANSPORT_WIFI) && !isWifiConnected()) {
            Log.d(TAG, "WiFi connection lost");
            activeTransports.remove(NetworkCapabilities.TRANSPORT_WIFI);
            isConnectedToLAN = false;
        }
        if (activeTransports.contains(NetworkCapabilities.TRANSPORT_CELLULAR) && !isCellularConnected()) {
            Log.d(TAG, "Cellular connection lost");
            activeTransports.remove(NetworkCapabilities.TRANSPORT_CELLULAR);
        }
    }

    public void connectLAN(MDNSDiscovery.OnServiceFoundListener listener) {
        if (!isWifiConnected()) return;
        if (isSearching) {
            Log.d(TAG, "MDNS search already in progress, skipping start");
            return;
        }

        stopMdnsDiscovery(); // Ensure clean state

        isSearching = true;
        activeMdnsDiscovery = new MDNSDiscovery(context);
        Log.d(TAG, "Starting MDNS discovery with 30s timeout");

        try {
            activeMdnsDiscovery.startDiscovery((deviceName, ip, port) -> {
                Log.d(TAG, "MDNS Service Found: " + deviceName + " at " + ip);
                
                if (listener == null) {
                    // Default behavior: just update global state and stop
                    serverIP = ip;
                    httpPort = port;
                    serverDeviceName = deviceName;
                    isConnectedToLAN = true;
                    stopMdnsDiscovery();
                } else {
                    // If a listener is provided, let it handle the result.
                    // We don't stop discovery here because the listener might be 
                    // looking for a SPECIFIC device name among many.
                    // The timeout or the listener's own cleanup will stop it.
                    listener.onServiceFound(deviceName, ip, port);
                }
            });

            // Start timeout to prevent battery drain if nothing is found
            timeoutHandler.postDelayed(timeoutRunnable, 30000);

        } catch (Exception e) {
            Log.e(TAG, "MDNS Start Failed", e);
            stopMdnsDiscovery();
        }
    }

    public  String getPhoneIP(){
        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        int ipInt = wifiInfo.getIpAddress();

        // convert int → readable IP
        String ipAddress = String.format(
                "%d.%d.%d.%d",
                (ipInt & 0xff),
                (ipInt >> 8 & 0xff),
                (ipInt >> 16 & 0xff),
                (ipInt >> 24 & 0xff)
        );
        return ipAddress;
    }

    public class WifiManagerLock {
        private final WifiManager.MulticastLock lock;
        public WifiManagerLock() {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            this.lock = wifi.createMulticastLock("mdns-lock");
        }
        public void startWifiLock() {
            lock.setReferenceCounted(true);
            lock.acquire();
        }
        public void stopWifiLock() {
            if (lock.isHeld()) lock.release();
        }
    }
}
