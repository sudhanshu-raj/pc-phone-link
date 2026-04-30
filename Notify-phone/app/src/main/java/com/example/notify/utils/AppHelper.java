package com.example.notify.utils;

import android.os.Build;
import android.util.Log;

import java.util.Random;

public class AppHelper {

    private final  String TAG = "Notifi:AppHelper";

    public static String getRandomIDKeys(){
        return getRandomIDKeys(10);
    }

    public static String getRandomIDKeys(int length){
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();

        StringBuilder key = new StringBuilder(10);
        for (int i = 0; i < length; i++) {
            key.append(chars.charAt(random.nextInt(chars.length())));
        }
        return key.toString();
    }

    public String getDeviceModel(){
        try {
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;

            return manufacturer + " " + model;
        }
        catch (Exception e){
            Log.d(TAG, "Exception : " + e.getMessage());
            return null;
        }
    }


}
