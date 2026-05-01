package com.example.notify.services;

import android.content.ComponentName;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.example.notify.utils.NetworkDiscovery;

import okhttp3.WebSocket;

public class MyNotificationListener extends NotificationListenerService {

    private final String TAG = "Notifi:NotificationSync";

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
        // 1. Skip our own app's notifications and ongoing system alerts (like "App is displaying over other apps")
        if (sbn.getPackageName().equals(getPackageName()) || sbn.isOngoing()) return;

        Bundle extras = sbn.getNotification().extras;
        String title = extras.getString("android.title");
        CharSequence textChar = extras.getCharSequence("android.text");
        String text = (textChar != null) ? textChar.toString() : null;

        // 3. Filter out Group Summaries, null content, or generic system alerts
        boolean isGroupSummary = (sbn.getNotification().flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0;
        if (isGroupSummary || title == null || text == null || sbn.getPackageName().equals("android")) {
            return;
        }


        String lowerText = text.toLowerCase();
        if (lowerText.equals("image") || lowerText.equals("photo") || lowerText.equals("video")) {
            Log.d(TAG, "Skipping media placeholder with no caption");
            return;
        }

        Log.d(TAG, "New Notification from: " + sbn.getPackageName());
        Log.d(TAG, "Title: " + title + " | Content: " + text);

        // 4. Send via WebSocket if available
        WebSocket currentWs = AuthenticateConnection.ws;
        if (currentWs != null) {
            String message = "Title: " + title + " | Content: " + text;
            currentWs.send(message);
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
}
