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

    public final String TAG= "Notifi:NetworkDiscovery";
    private final ConnectivityManager cm;
    private final Context context;
    public static String serverIP;
    public static String serverDeviceName;
    public static  String serverDeviceID;
    public static int  httpPort;

    // Track which transports are currently active to handle onLost correctly
    private final Set<Integer> activeTransports = new HashSet<>();

    // Here this var means , whether it is on LAN and connected to the mdns service of server  or not,
    // this doesn't answer whether it's authenticated or not or connected to websocket server or not
    public static boolean isConnectedToLAN= false;
    private static boolean isSearching = false;
    private MDNSDiscovery.OnServiceFoundListener listener;
    private MDNSDiscovery activeMdnsDiscovery; // Track the current discovery session


    public NetworkDiscovery(Context context) {
        this.context = context.getApplicationContext();
        this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public NetworkDiscovery(MDNSDiscovery.OnServiceFoundListener listener,Context context) {
        this.listener = listener;
        this.context = context;
        this.cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void register() {

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();
        cm.registerNetworkCallback(networkRequest, this);
    }

    // it will stop the mDNS discovery to the energy in foreground services and remove the existing on device discovery listener
    public void unregister() {
        try {
            stopMdnsDiscovery();
            this.listener = null;

        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network callback", e);
        }
    }

    private void stopMdnsDiscovery() {
        if (activeMdnsDiscovery != null) {
            Log.d(TAG, "Stopping active MDNS discovery");
            activeMdnsDiscovery.stopDiscovery();
            activeMdnsDiscovery = null;
        }
        isSearching = false;
    }

    public boolean isWifiConnected() {
        for (Network network : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return true;
            }
        }
        return false;
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
        SharedPreferences sharedPref = context.getSharedPreferences("Notify_shared_pref", Context.MODE_PRIVATE);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Log.d(TAG, "WiFi available");

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                activeTransports.add(NetworkCapabilities.TRANSPORT_WIFI);
                if(listener != null) {
                    connectLAN(listener);
                }
                // if app in running on background and device is already setup then need to re-connect server
                else if(sharedPref.getBoolean(Constants.IS_DEVICE_SETUP,false)){
                    new AuthenticateConnection(context).reconnectLastDevice();
                }
                else{
                    Log.d(TAG,"Already connected to the LAN");
                }
            }, 1000);
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            Log.d(TAG, "Cellular available");
            activeTransports.add(NetworkCapabilities.TRANSPORT_CELLULAR);
        }
    }

    @Override
    public void onLost(Network network) {
        Log.d(TAG, "Network lost");

        // Note: On some Android versions, cm.getNetworkCapabilities(network) returns null in onLost.
        // We use our tracked state and verify current connectivity.

        if (activeTransports.contains(NetworkCapabilities.TRANSPORT_WIFI) && !isWifiConnected()) {
            Log.d(TAG, "WiFi lost");
            activeTransports.remove(NetworkCapabilities.TRANSPORT_WIFI);
            isConnectedToLAN = false;

        }

        if (activeTransports.contains(NetworkCapabilities.TRANSPORT_CELLULAR) && !isCellularConnected()) {
            Log.d(TAG, "Cellular lost");
            activeTransports.remove(NetworkCapabilities.TRANSPORT_CELLULAR);
        }
    }


    public void connectLAN(MDNSDiscovery.OnServiceFoundListener listener){
        if(!isWifiConnected()){
            Log.d(TAG,"Wi-Fi not connected");
            return;
        }

        if (isSearching ) {
            Log.d(TAG, "Search already in progress");
            return;
        }

        isSearching = true;
        activeTransports.add(NetworkCapabilities.TRANSPORT_WIFI);

        // Track this instance so we can stop it later
        activeMdnsDiscovery = new MDNSDiscovery(context);

        try {
            activeMdnsDiscovery.startDiscovery((deviceName,ip, port) -> {
                serverIP = ip;
                httpPort = port;
                serverDeviceName = deviceName;
                isConnectedToLAN = true;
                
                // If we're not in a "Searching Activity" means listener == null,
                // then should stop discovery immediately to save power/prevent duplicates.
                if (listener == null) {
                    stopMdnsDiscovery();
                } else {
                    isSearching = false; 
                }

                Log.d(TAG,"Now the discovery got done with ip: " + ip + " and port: " + port );

                if (listener != null) {
                    listener.onServiceFound(deviceName, ip, port);
                }
            });
        }
        catch (Exception e){
            Log.e(TAG, "Exception during MDNS start", e);
            isSearching = false;
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

    // This tells android not to ignore the mdns packets on Wi-Fi and
    // acquire the extra power to capture the packets.
    public class WifiManagerLock {
        private final WifiManager.MulticastLock lock;

        public WifiManagerLock() {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            this.lock = wifi.createMulticastLock("mdns-lock");
        }

        public void startWifiLock() {
            lock.setReferenceCounted(true);
            lock.acquire();
        }

        public void stopWifiLock() {
            if (lock.isHeld()) {
                lock.release();
            }
        }
    }

}
