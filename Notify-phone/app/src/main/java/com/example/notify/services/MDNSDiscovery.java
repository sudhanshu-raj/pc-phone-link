package com.example.notify.services;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.ext.SdkExtensions;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class MDNSDiscovery {

    private static final String TAG = "Notifi:MDNSDiscovery";
    private static final String SERVICE_TYPE = "_notifypcws._tcp.";
    private static  final String SERVICE_NAME = "notify-my-pc";
    private static final int DEFAULT_WS_PORT = 8090;

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;

    public interface OnServiceFoundListener {
        void onServiceFound(String ip, int port);
    }

    public MDNSDiscovery(Context context) {
        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void startDiscovery(OnServiceFoundListener listener) {

        discoveryListener = new NsdManager.DiscoveryListener() {

            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Discovery started");
            }
            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "Service found: " + service.getServiceName());

                if (SERVICE_TYPE.equals(service.getServiceType()) && service.getServiceName().contains(SERVICE_NAME)) {
                    nsdManager.resolveService(service, new NsdManager.ResolveListener() {

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            List<InetAddress> hostAddresses = null;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7) {
                                hostAddresses = serviceInfo.getHostAddresses();
                            }

                            String ip = null;
                            if (hostAddresses != null) {
                                for (InetAddress address : hostAddresses) {
                                    if (isUsableLanAddress(address)) {
                                        ip = address.getHostAddress();
                                        Log.d(TAG, "Selected usable LAN IP: " + ip);
                                        break;
                                    }
                                }
                            }
                            String ipFromName = getIPFromServiceName(service.getServiceName());
                            if(ip == null || !ip.equals(ipFromName)){
                                Log.d(TAG,"Wi-fi IP not matching between  ip from service name and ip from the service host address");
                            }

                            int port = serviceInfo.getPort();
                            Log.d(TAG, "Resolved: " + ipFromName + ":" + port);
                            listener.onServiceFound(ipFromName, port);
                        }
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            Log.e(TAG, "Resolve failed: " + errorCode);
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.d(TAG, "Service lost: " + service.getServiceName());
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped");
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Start discovery failed: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed: " + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }
        };

        nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
        );
    }

    public void stopDiscovery() {
        if (discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener);
        }
    }

    private boolean isUsableLanAddress(InetAddress address) {
        return address instanceof Inet4Address
                && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isMulticastAddress();
    }

    public String getIPFromServiceName(String name){
        String[] spilt = name.split("ip-");
        String rawIP = spilt[1];
        return rawIP.replace("-", ".");
    }


}