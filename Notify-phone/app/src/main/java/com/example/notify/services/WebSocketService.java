package com.example.notify.services;

import android.app.Activity;
import android.util.Log;
import android.widget.TextView;

import com.example.notify.R;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketService extends WebSocketListener {

    private final String TAG = "Notifi:WebSocketService";


    private Activity activity;
    public WebSocketService(Activity activity)  {
        this.activity = activity;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "Connected to server!");
        webSocket.send("Hello from phone");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG,"From PC: " + text);
        activity.runOnUiThread(() -> {
            TextView tv2 = activity.findViewById(R.id.txtOutput);
            if (tv2 != null) {
                tv2.setText(text);
            }
        });
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "Connection Failed: " + t.getMessage(), t);
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "Closing: " + reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "Closed: " + reason);
    }
}