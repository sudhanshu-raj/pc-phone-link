package com.example.notify;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notify.services.ScannedDeviceAdapter;
import com.example.notify.utils.ScannedDeviceModel;

import java.util.ArrayList;
import java.util.List;

public class DeviceSearchingActivity extends AppCompatActivity {

    private RecyclerView devicesRecyclerView;
    private ScannedDeviceAdapter scannedDeviceAdapter;
    private List<ScannedDeviceModel> deviceList;

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

        // Add a test device
        addDevice("Alen PC", true);
        addDevice("Home Desktop", false);
        addDevice("Home Desktop", false);
        addDevice("Home Desktop", false);


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

    private void addDevice(String name, boolean isPairing) {
        deviceList.add(new ScannedDeviceModel(name, isPairing));
        scannedDeviceAdapter.notifyItemInserted(deviceList.size() - 1);
    }
}