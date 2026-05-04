package com.example.notify.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("Notifi:BootReceiver", "Boot completed, initiating reconnection...");
            // Attempt to reconnect to the last device
            new AuthenticateConnection(context).reconnectLastDevice();
        }
    }
}
