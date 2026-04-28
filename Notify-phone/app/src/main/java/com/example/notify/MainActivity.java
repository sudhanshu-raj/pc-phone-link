package com.example.notify;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.notify.services.AuthenticateConnection;
import com.example.notify.services.MyNotificationListener;
import com.example.notify.utils.NetworkDiscovery;

import okhttp3.OkHttpClient;
import okhttp3.WebSocket;

public class MainActivity extends AppCompatActivity {

    private static final String  TAG = "Notifi:MainActivity";
    private static final String MDNS_tag = "MDNSDiscovery";
    private String serverIP;

    OkHttpClient client = new OkHttpClient();
    AuthenticateConnection authenticateConnection;
    NetworkDiscovery networkDiscovery;
    WebSocket ws;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //to keep screen awake , useful for dev
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        authenticateConnection = new AuthenticateConnection(this);
        networkDiscovery = new NetworkDiscovery(this);
        sharedPreferences = getSharedPreferences("Notify_shared_pref", MODE_PRIVATE);


        // Check if the service is enabled
        ComponentName cn = new ComponentName(this, MyNotificationListener.class);
        String flat = Settings.Secure.getString(this.getContentResolver(), "enabled_notification_listeners");
        boolean isEnabled = flat != null && flat.contains(cn.flattenToString());

        if (!isEnabled) {
            // Take user to the settings screen
            Log.d(TAG,"Notification listener service not enabled, try enabling it");
            this.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        }

        if(sharedPreferences.getBoolean("isDeviceSetup",false) == false){
            Log.d(TAG,"Device not setup, try setting it up");
            Intent intent = new Intent(this, SetupInstructionsActivity.class);
            startActivity(intent);
        }
        else{
            Log.d(TAG,"Device already setup");
//            if(networkDiscovery.isWifiConnected()) {
//                try{
//                    tryConnectLAN();
//                }
//                catch (Exception e){
//                    Log.d(TAG, "Exception : " + e.getMessage());
//                }
//            }
//            else{
//                Log.d(TAG,"Client not connected to wifi, try on Cellular connection");
//            }
        }

    }

    public void tryConnectLAN() {
        if(!NetworkDiscovery.isConnectedToLAN) {
            Log.d(TAG, "Attempting to connect to LAN...");
            networkDiscovery.connectLAN((deviceName,ip, port) -> {
                Log.d(TAG, "Found server at: " + ip);
                runOnUiThread(() -> authenticateConnection.verifyConnection());
            });
        }
        else{
            Log.d(TAG, "Already connected to LAN");
        }
    }

    public void sendMsg(View v){
        // For testing purposes, launch SetupInstructions activity
        Intent intent = new Intent(this, SetupInstructionsActivity.class);
        startActivity(intent);

        if(AuthenticateConnection.isLANConAuthenticated) {
            Log.d(TAG, "Sending message...");
            EditText txt = findViewById(R.id.sendMsgInp);
            String msg = txt.getText().toString();
            ws = AuthenticateConnection.ws;
            if (ws != null) {
                ws.send(msg);
            }
        }
        else{
            Toast.makeText(this, "Wifi not connected or not authenticated", Toast.LENGTH_SHORT).show();
        }
    }
    public void submitLANPIN(View v){
        authenticateConnection.submitLANPIN();
    }


    @Override
    public void onDestroy(){
        super.onDestroy();
        networkDiscovery.unregister();
    }

}