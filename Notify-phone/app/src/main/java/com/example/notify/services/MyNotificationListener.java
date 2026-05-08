package com.example.notify.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.notify.MainActivity;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Base64;
import android.util.Log;

import com.example.notify.utils.NetworkDiscovery;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.WebSocket;

public class MyNotificationListener extends NotificationListenerService {

    private final String TAG = "Notifi:NotificationSync";

    private static final String CHANNEL_ID = "NotificationSyncChannel";
    private static final int NOTIFICATION_ID = 1;
    private NetworkDiscovery networkDiscovery;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getStickyNotification());

        // Initialize and register network discovery to handle background connectivity.
        // This will automatically trigger reconnectLastDevice() via onAvailable when registered.
        networkDiscovery = new NetworkDiscovery(this);
        networkDiscovery.register();
    }

    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (networkDiscovery != null) {
            networkDiscovery.unregister();
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Notification Sync Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private android.app.Notification getStickyNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Notify is active")
                .setContentText("Syncing notifications to your PC...")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "Notification Listener Connected");
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "Notification Listener Disconnected");
        // Request rebind if disconnected unexpectedly
        requestRebind(new ComponentName(this, MyNotificationListener.class));
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn.getPackageName().equals(getPackageName()) || sbn.isOngoing()) return;

        Bundle extras = sbn.getNotification().extras;
        String title = extras.getString("android.title");
        CharSequence textChar = extras.getCharSequence("android.text");
        String text = (textChar != null) ? textChar.toString() : null;

        boolean isGroupSummary = (sbn.getNotification().flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0;
        if (isGroupSummary || title == null || text == null || sbn.getPackageName().equals("android")) {
            return;
        }

        String lowerText = text.toLowerCase();
        if (lowerText.equals("image") || lowerText.equals("photo") || lowerText.equals("video")) {
            Log.d(TAG, "Skipping media placeholder with no caption");
            return;
        }
        String appPackage = sbn.getPackageName();
        String appName = appPackage;
        PackageManager pm = getPackageManager();
        try {
            appName = pm.getApplicationLabel(
                    pm.getApplicationInfo(appPackage, 0)
            ).toString();
        } catch (PackageManager.NameNotFoundException e) {
            if (appPackage.contains(".")) {
                appName = appPackage.substring(appPackage.lastIndexOf(".")+1);
                appName = appName.substring(0,1).toUpperCase()+appName.substring(1);
            } else {
                appName = appPackage;
            }
            Log.w(TAG, "Failed to get app name for " + appPackage, e);
        }

        String imageString = null;
        try {
            android.graphics.Bitmap largeIcon = sbn.getNotification().largeIcon;
            Drawable iconDrawable;
            if (largeIcon != null) {
                iconDrawable = new BitmapDrawable(getResources(), largeIcon);
            } else {
                iconDrawable = getPackageManager().getApplicationIcon(appPackage);
            }
            imageString = getIconBase64String(iconDrawable);
        } catch (Exception e) {
            Log.w(TAG, "Icon extraction failed", e);
        }

        ArrayList<String> actions = getActionsList(sbn);

        Log.d(TAG, "New Notification from: " + appPackage);
        Log.d(TAG, "Title: " + title + " | Content: " + text);
        Log.d(TAG,"Actions available are:"+actions.toString());

        WebSocket currentWs = AuthenticateConnection.ws;
        if (currentWs != null) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "notification");
            json.addProperty("appName", appName);
            json.addProperty("package", appPackage);
            json.addProperty("id", sbn.getId());
            json.addProperty("deviceID", new AuthenticateConnection(this).getThisDeviceID());
            json.addProperty("title", title);
            json.addProperty("content", text);
            json.addProperty("image", imageString);
            json.add("actions", new Gson().toJsonTree(actions));
            json.addProperty("timestamp", System.currentTimeMillis());
            currentWs.send(json.toString());
        } else {
            Log.d(TAG, "WebSocket is not connected, cannot sync notification");
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "Notification cleared: " + sbn.getPackageName());
        WebSocket currentWs = AuthenticateConnection.ws;
        if (currentWs != null) {
            currentWs.send("Notification event cleared: " + sbn.getPackageName());
        }
    }

    public String getIconBase64String(Drawable drawable){
        if(drawable == null) return null;

        try{
            Bitmap bitmap;
            if(drawable instanceof BitmapDrawable){
                bitmap = ((BitmapDrawable)drawable).getBitmap();
            } else {
                // Use a default size if intrinsic dimensions are not available
                int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 100;
                int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 100;
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            byte[] bytes= stream.toByteArray();
            return Base64.encodeToString(bytes,Base64.NO_WRAP);

        } catch (Exception e){
            return null;
        }
    }

    public ArrayList<String> getActionsList(StatusBarNotification sbn) {
        Notification.Action[] actions = sbn.getNotification().actions;

        ArrayList<String> actionLists = new ArrayList<>();
        if (actions == null) {
            return actionLists;
        }

        for (Notification.Action action : actions) {
            actionLists.add(action.title.toString());
        }

        return actionLists;
    }

}
