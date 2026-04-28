package com.example.notify.utils;

import android.content.Context;
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
    public static int  httpPort;

    // Track which transports are currently active to handle onLost correctly
    private final Set<Integer> activeTransports = new HashSet<>();
    public static boolean isConnectedToLAN;

    public NetworkDiscovery(Context context) {
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

    public void unregister() {
        try {
            cm.unregisterNetworkCallback(this);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering network callback", e);
        }
    }

    public boolean isWifiConnected() {
        Network network = cm.getActiveNetwork();
        boolean isWifi = false;
        if (network != null) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            isWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        }
        return isWifi;
    }

    public boolean isCellularConnected() {
        Network network = cm.getActiveNetwork();
        boolean isCellular = false;
        if (network != null) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            isCellular = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);

        }
        return isCellular;
    }

    @Override
    public void onAvailable(Network network) {
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        if (caps == null) return;

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            Log.d(TAG, "WiFi available");
            activeTransports.add(NetworkCapabilities.TRANSPORT_WIFI);
            if(!isConnectedToLAN) {
                connectLAN();
            }

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

    public  void connectLAN(){
        if(!isConnectedToLAN){
            Log.d(TAG,"Connecting the LAN Server...");
            connectLAN((serverDeviceName,ip, port) -> {
                Log.d(TAG, "Connected to the device of LAN with IP: " + serverDeviceName + " at: " + ip + ":" + port);
                new Handler(Looper.getMainLooper()).post(() -> new AuthenticateConnection(context).verifyConnection());
            });
        }
        else{
            Log.d(TAG,"Already connected to the LAN");
        }
    }

    public void connectLAN(MDNSDiscovery.OnServiceFoundListener listener){
        if(!isWifiConnected()){
            Log.d(TAG,"Wi-Fi not connected");
            return;
        }
        activeTransports.add(NetworkCapabilities.TRANSPORT_WIFI);

        MDNSDiscovery mdnsDiscovery = new MDNSDiscovery(context);

        try {
            mdnsDiscovery.startDiscovery((deviceName,ip, port) -> {
                serverIP = ip;
                httpPort = port;
                serverDeviceName = deviceName;
                isConnectedToLAN = true;
                Log.d(TAG,"Now the discovery got done with ip: " + serverIP + " and port: " + httpPort );

                if (listener != null) {
                    listener.onServiceFound(deviceName,ip, port);
                }
            });
        }
        catch (Exception e){
            Log.d(TAG, "Exception : " + e.getMessage());
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
