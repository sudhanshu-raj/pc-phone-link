package com.example.notify.utils;

public class ScannedDeviceModel {
    private String deviceName;
    private boolean isPairing;

    public ScannedDeviceModel(String deviceName, boolean isPairing) {
        this.deviceName = deviceName;
        this.isPairing = isPairing;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public boolean isPairing() {
        return isPairing;
    }

    public void setPairing(boolean pairing) {
        isPairing = pairing;
    }
}