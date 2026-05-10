package com.example.notify.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import java.util.Random;

public class AppHelper {

    private static final String TAG = "Notifi:AppHelper";

    public static String getRandomIDKeys() {
        return getRandomIDKeys(10);
    }

    public static String getRandomIDKeys(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();

        StringBuilder key = new StringBuilder(10);
        for (int i = 0; i < length; i++) {
            key.append(chars.charAt(random.nextInt(chars.length())));
        }
        return key.toString();
    }

    public String getDeviceModel() {
        try {
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;

            return manufacturer + " " + model;
        } catch (Exception e) {
            Log.d(TAG, "Exception : " + e.getMessage());
            return null;
        }
    }

    public static int getBatteryPercentage(Context context) {
        if (context == null) return 0;

        int percentage = -1;

        // 1. Try sticky broadcast (most reliable for current state on many devices)
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                if (level != -1 && scale > 0) {
                    percentage = Math.round((level / (float) scale) * 100);
                    Log.d(TAG, "Battery level from Broadcast: " + percentage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting battery intent: " + e.getMessage());
        }

        // 2. Try BatteryManager property as fallback
        if (percentage < 0 || percentage > 100) {
            try {
                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null) {
                    int capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (capacity >= 0 && capacity <= 100) {
                        percentage = capacity;
                        Log.d(TAG, "Battery capacity from BatteryManager: " + percentage);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting battery property: " + e.getMessage());
            }
        }

        if (percentage < 0 || percentage > 100) {
            Log.w(TAG, "Could not determine battery percentage, returning 0.");
            percentage = 0;
        }

        return percentage;
    }

    public static boolean isBatteryCharging(Context context) {
        if (context == null) return false;

        // 1. Try modern API if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                return bm.isCharging();
            }
        }

        // 2. Fallback to sticky broadcast
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting charging status: " + e.getMessage());
        }

        return false;
    }

}
