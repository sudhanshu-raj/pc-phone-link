package com.example.notify.utils;

import java.util.Date;

public class ServerDeviceModel {

    private String deviceName;
    private String deviceID;
    private String deviceIP;
    private Boolean isConnected = false;
    private Integer httpPort;
    private Integer wsPort;
    private Date lastSeen;
    private String token;

    public ServerDeviceModel(){};

    public ServerDeviceModel(String deviceName, String deviceID, String deviceIP, Boolean isConnected,
                             Integer httpPort, Integer wsPort, Date lastSeen, String token) {
        this.deviceName = deviceName;
        this.deviceID = deviceID;
        this.deviceIP = deviceIP;
        this.isConnected = isConnected;
        this.httpPort = httpPort;
        this.wsPort = wsPort;
        this.lastSeen = lastSeen;
        this.token = token;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceID() {
        return deviceID;
    }

    public void setDeviceID(String deviceID) {
        this.deviceID = deviceID;
    }

    public String getDeviceIP() {
        return deviceIP;
    }

    public void setDeviceIP(String deviceIP) {
        this.deviceIP = deviceIP;
    }

    public Boolean getConnected() {
        return isConnected;
    }

    public void setConnected(Boolean connected) {
        isConnected = connected;
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(Integer httpPort) {
        this.httpPort = httpPort;
    }

    public Integer getWsPort() {
        return wsPort;
    }

    public void setWsPort(Integer wsPort) {
        this.wsPort = wsPort;
    }

    public Date getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Date lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
