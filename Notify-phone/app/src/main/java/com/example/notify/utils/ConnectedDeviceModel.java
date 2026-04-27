package com.example.notify.utils;

import java.util.Date;

public class ConnectedDeviceModel {

    private String deviceName;
    private String deviceID;
    private String deviceIP;
    private boolean isConnected;
    private int httpPort;
    private int wsPort;
    private Date lastSeen;

    public ConnectedDeviceModel(String deviceName,String deviceID, String deviceIP, boolean isConnected, int httpPort, int wsPort, Date lastSeen) {
        this.deviceName = deviceName;
        this.deviceID = deviceID;
        this.deviceIP = deviceIP;
        this.isConnected = isConnected;
        this.httpPort = httpPort;
        this.wsPort = wsPort;
        this.lastSeen = lastSeen;
    }


    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceIP() {
        return deviceIP;
    }

    public void setDeviceIP(String deviceIP) {
        this.deviceIP = deviceIP;
    }

    public boolean isConnected() {
        return isConnected;
    }
    public String getDeviceID() {
        return deviceID;
    }
    public void setDeviceID(String deviceID) {
        this.deviceID = deviceID;
    }
    public void setConnected(boolean connected) {
        isConnected = connected;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getWsPort() {
        return wsPort;
    }

    public void setWsPort(int wsPort) {
        this.wsPort = wsPort;
    }

    public Date getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Date lastSeen) {
        this.lastSeen = lastSeen;
    }
}
