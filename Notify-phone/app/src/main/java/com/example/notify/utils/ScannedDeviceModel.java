package com.example.notify.utils;

public class ScannedDeviceModel {
    private String deviceName;
    private String ip;
    private int port;
    private boolean isPairing;

    public ScannedDeviceModel(String deviceName, String ip, int port, boolean isPairing) {
        this.deviceName = deviceName;
        this.ip = ip;
        this.port = port;
        this.isPairing = isPairing;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isPairing() {
        return isPairing;
    }

    public void setPairing(boolean pairing) {
        isPairing = pairing;
    }
}
