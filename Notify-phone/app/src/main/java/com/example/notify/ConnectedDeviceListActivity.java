package com.example.notify;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notify.services.ConnectedDeviceAdapter;
import com.example.notify.utils.ConnectedDeviceModel;

import java.util.ArrayList;
import java.util.List;

public class ConnectedDeviceListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ConnectedDeviceAdapter connectedDeviceAdapter;
    private List<ConnectedDeviceModel> connectedDeviceModelList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_connected_device_list);

        recyclerView = findViewById(R.id.rvConnectedDevices);
        connectedDeviceModelList = new ArrayList<>();
        connectedDeviceAdapter = new ConnectedDeviceAdapter(connectedDeviceModelList, this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(connectedDeviceAdapter);

        // Add test devices
        addDevice(new ConnectedDeviceModel("Alen PC", null,null,true,8080,8080,null));


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


    }

    private void addDevice(ConnectedDeviceModel device) {
        connectedDeviceModelList.add(device);
        connectedDeviceAdapter.notifyItemInserted(connectedDeviceModelList.size() - 1);
    }
}