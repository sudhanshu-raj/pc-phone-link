package com.example.notify;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notify.interfaces.ApiService;
import com.example.notify.services.ApiClient;
import com.example.notify.services.AuthenticateConnection;
import com.example.notify.services.MDNSDiscovery;
import com.example.notify.services.ScannedDeviceAdapter;
import com.example.notify.utils.Constants;
import com.example.notify.utils.NetworkDiscovery;
import com.example.notify.utils.ScannedDeviceModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DeviceSearchingActivity extends AppCompatActivity {

    private static String TAG = "Notifi:DeviceSearchingActivity";
    private RecyclerView devicesRecyclerView;
    private ScannedDeviceAdapter scannedDeviceAdapter;
    private List<ScannedDeviceModel> deviceList;
    private NetworkDiscovery networkDiscovery;
    private SharedPreferences sharedPref;
    private final Set<String> discoveredDevices = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_device_searching);

        devicesRecyclerView = findViewById(R.id.devicesRecyclerView);
        deviceList = new ArrayList<>();
        scannedDeviceAdapter = new ScannedDeviceAdapter(deviceList, this);

        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(scannedDeviceAdapter);

        sharedPref = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);

        // START THE SERVER HERE
        MDNSDiscovery.OnServiceFoundListener listener = (serverDeviceName,ip,port) -> {
            String deviceKey = serverDeviceName + "@" + ip + ":" + port;
            synchronized (discoveredDevices) {
                if (discoveredDevices.contains(deviceKey)) {
                    Log.d(TAG, "Duplicate service ignored: " + deviceKey);
                    return;
                }
                discoveredDevices.add(deviceKey);
            }

            Log.d(TAG, "Found  the device on LAN with IP: " + serverDeviceName + " at: " + ip + ":" + port);

            //Inform the server device that I found you on LAN
            String baseURL = "http://" + ip + ":" + port + "/api/v1/";
            ApiService api = ApiClient.getService(baseURL);
            Log.d(TAG, "Sending phonesFound request to: " + baseURL);
            String thisDeviceName = sharedPref.getString(Constants.THIS_DEVICE_NAME, null);
            if(thisDeviceName != null){
                Map<String,String>  body = new HashMap<>();
                body.put("clientDeviceName", thisDeviceName);
                body.put("clientDeviceIP",networkDiscovery.getPhoneIP());
                body.put("clientDeviceID",sharedPref.getString(Constants.THIS_DEVICE_ID, null));

                api.phonesFound(body).enqueue(new Callback<Map<String, Object>>(){
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        Log.d(TAG, "Sent phonesFound request for: " + serverDeviceName);
                        Map<String, Object> result = response.body();
                        if(result != null && result.get("status") != null && result.get("status").equals("success")){
                            String serverDeviceID = (String) result.get("serverDeviceID");
                            HashMap<String,Object> deviceInfo = new HashMap<>();
                            Log.d(TAG, "Server device ID: " + serverDeviceID);
                            deviceInfo.put(Constants.KEY_DEVICE_ID,serverDeviceID);
                            deviceInfo.put(Constants.KEY_DEVICE_NAME,serverDeviceName);
                            deviceInfo.put(Constants.KEY_DEVICE_IP,ip);
                            deviceInfo.put(Constants.KEY_HTTP_PORT,port);
                            deviceInfo.put(Constants.KEY_LAST_SEEN,new Date());
                            new AuthenticateConnection(DeviceSearchingActivity.this).storeDeviceData(serverDeviceID,deviceInfo);
                        }
                        else{
                            Log.d(TAG, "Error sending phonesFound request: " + result.get("message"));


                        }

                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Log.d(TAG, "Error sending phonesFound request: " + t.getMessage());
                    }
                });
            }
            else{
                Log.d(TAG, "Device name is null, can't send phonesFound request");
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                addDevice(serverDeviceName, false);
            });

        };
        networkDiscovery = new NetworkDiscovery(listener,this);
        networkDiscovery.register();



        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // From dp to pixels
            int densityPadding = (int) (24 * getResources().getDisplayMetrics().density);

            // system safe zone insets to design margin (24dp)
            v.setPadding(
                    systemBars.left + densityPadding,
                    systemBars.top + densityPadding,
                    systemBars.right + densityPadding,
                    systemBars.bottom + densityPadding
            );
            return insets;
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkDiscovery != null) {
            networkDiscovery.unregister();
            Log.d(TAG, "NetworkDiscovery unregistered in onDestroy");
        }
    }

    private void addDevice(String name, boolean isPairing) {
        for (int i = 0; i < deviceList.size(); i++) {
            if (deviceList.get(i).getDeviceName().equals(name)) {
                deviceList.get(i).setPairing(isPairing);
                scannedDeviceAdapter.notifyItemChanged(i);
                return;
            }
        }
        deviceList.add(0, new ScannedDeviceModel(name, isPairing));
        scannedDeviceAdapter.notifyItemInserted(0);
        // Optional: Scroll to top so the user sees the new device immediately
        devicesRecyclerView.scrollToPosition(0);
    }
    
}