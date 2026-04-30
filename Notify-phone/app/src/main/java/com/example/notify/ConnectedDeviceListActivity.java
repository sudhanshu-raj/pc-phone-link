package com.example.notify;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notify.services.ConnectedDeviceAdapter;
import com.example.notify.utils.ServerDeviceModel;
import com.example.notify.utils.Constants;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConnectedDeviceListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ConnectedDeviceAdapter connectedDeviceAdapter;
    private List<ServerDeviceModel> connectedDeviceModelList;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_connected_device_list);

        recyclerView = findViewById(R.id.rvConnectedDevices);
        connectedDeviceModelList = new ArrayList<>();
        connectedDeviceAdapter = new ConnectedDeviceAdapter(connectedDeviceModelList, this);
        sharedPreferences = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);


        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(connectedDeviceAdapter);

        // Load saved devices from SharedPreferences
        loadDevices();


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


    }

    private void addDevice(ServerDeviceModel device) {
        connectedDeviceModelList.add(device);
        connectedDeviceAdapter.notifyItemInserted(connectedDeviceModelList.size() - 1);
    }

    private void loadDevices() {
        connectedDeviceModelList.clear();
        Map<String, ?> allEntries = sharedPreferences.getAll();
        Gson gson = new Gson();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("ID")) {
                try {
                    String deviceId = key.substring(2); // Remove "ID" prefix
                    String json = (String) entry.getValue();

                    // Parse the JSON data directly into the Model class
                    ServerDeviceModel device = gson.fromJson(json, ServerDeviceModel.class);
                    
                    if (device != null) {
                        connectedDeviceModelList.add(device);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        connectedDeviceAdapter.notifyDataSetChanged();
    }

    public List<ServerDeviceModel> getConnectedDeviceModelList() {
        return connectedDeviceModelList;
    }
}