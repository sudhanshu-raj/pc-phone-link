package com.example.notify.services;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.example.notify.R;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketService extends WebSocketListener {

    private final String TAG = "Notifi:WebSocketService";


    private Activity activity;
    private final Context context;

    public WebSocketService(Context context)  {
        this.context = context.getApplicationContext();
        if (context instanceof Activity) {
            this.activity = (Activity) context;
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "Connected to server!");
        webSocket.send("Hello from phone");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG,"From PC: " + text);
        if (activity != null) {
            activity.runOnUiThread(() -> {
                TextView tv2 = activity.findViewById(R.id.txtOutput);
                if (tv2 != null) {
                    tv2.setText(text);
                }
            });
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "Connection Failed: " + t.getMessage());
        retryConnection();
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "Closing: " + reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "Closed: " + reason);
        // Only retry if it wasn't a normal closure (e.g. 1000)
        if (code != 1000) {
            retryConnection();
        }
    }

    private void retryConnection() {
        if (!new com.example.notify.utils.NetworkDiscovery(context).isWifiConnected()) {
            Log.d(TAG, "WiFi not connected, skipping reconnection retry.");
            return;
        }
        Log.d(TAG, "Scheduling reconnection attempt in 5 seconds...");
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            new AuthenticateConnection(context).reconnectLastDevice();
        }, 5000);
    }
}