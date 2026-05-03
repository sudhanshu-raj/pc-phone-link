package com.example.notify;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.notify.services.AuthenticateConnection;
import com.example.notify.services.MyNotificationListener;
import com.example.notify.utils.NetworkDiscovery;

import okhttp3.OkHttpClient;
import okhttp3.WebSocket;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Notifi:MainActivity";
    private SharedPreferences sharedPreferences;
    private AuthenticateConnection authenticateConnection;
    private NetworkDiscovery networkDiscovery;
    private OkHttpClient client = new OkHttpClient();
    private WebSocket ws;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

         getSharedPreferences("Notify_shared_pref", MODE_PRIVATE).edit().clear().apply();
        // Keep screen awake for development
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        authenticateConnection = new AuthenticateConnection(this);
        networkDiscovery = new NetworkDiscovery(this);
        sharedPreferences = getSharedPreferences("Notify_shared_pref", MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check permissions and setup in onResume so it re-triggers when returning from Settings
        checkPermissionsAndSetup();
    }

    private void checkPermissionsAndSetup() {
        if (!isNotificationServiceEnabled()) {
            Log.d(TAG, "Notification listener service not enabled, showing dialog");
            showNotificationPermissionDialog();
        } else {
            Log.d(TAG, "Notification listener service is enabled");
            rebindService();
            
            // ONLY proceed to Setup Instructions if notification permission is granted
            if (!sharedPreferences.getBoolean("isDeviceSetup", false)) {
                Log.d(TAG, "Device not setup, launching instructions");
                Intent intent = new Intent(this, SetupInstructionsActivity.class);
                startActivity(intent);
            } else {
                Log.d(TAG, "Device already setup, attempting auto-reconnect");
                authenticateConnection.reconnectLastDevice();
                Intent intent = new Intent(this, ConnectedDeviceListActivity.class);
                startActivity(intent);
            }
        }
    }

    private boolean isNotificationServiceEnabled() {
        ComponentName cn = new ComponentName(this, MyNotificationListener.class);
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    private void showNotificationPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notification Access Required")
                .setMessage("To sync notifications to your PC, this app needs 'Notification Access'. Please enable it for 'My Notification Sync App' in the settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    Toast.makeText(this, "Notification sync will not work without this permission.", Toast.LENGTH_LONG).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Workaround for "Service not registered" IllegalArgumentException.
     * Uses the official requestRebind API.
     */
    private void rebindService() {
        try {
            ComponentName cn = new ComponentName(this, MyNotificationListener.class);
            MyNotificationListener.requestRebind(cn);
            Log.d(TAG, "NotificationListenerService rebind requested via API");
        } catch (Exception e) {
            Log.e(TAG, "Failed to rebind service", e);
        }
    }


    public void sendMsg(View v) {
        if (AuthenticateConnection.isLANConAuthenticated) {
            Log.d(TAG, "Sending message...");
            EditText txt = findViewById(R.id.sendMsgInp);
            String msg = txt.getText().toString();
            ws = AuthenticateConnection.ws;
            if (ws != null) {
                ws.send(msg);
            }
        } else {
            Toast.makeText(this, "Wifi not connected or not authenticated", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkDiscovery.unregister();
    }
}
