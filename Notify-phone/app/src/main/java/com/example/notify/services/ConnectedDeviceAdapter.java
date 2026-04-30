package com.example.notify.services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notify.R;
import com.example.notify.utils.ServerDeviceModel;

import java.util.List;

public class ConnectedDeviceAdapter extends RecyclerView.Adapter<ConnectedDeviceAdapter.ConnDeviceViewHolder> {

    private List<ServerDeviceModel> deviceList;
    private Context context;

    public ConnectedDeviceAdapter(List<ServerDeviceModel> deviceList, Context context) {
        this.deviceList = deviceList;
        this.context = context;
    }

    @NonNull
    @Override
    public ConnDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_managed_device, parent, false);
        return new ConnectedDeviceAdapter.ConnDeviceViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ConnDeviceViewHolder holder, int position) {
        ServerDeviceModel device = deviceList.get(position);
        holder.deviceName.setText(device.getDeviceName());

        if (device.getConnected()) {
            holder.btnStatus.setText("Connected");
        } else {
            holder.btnStatus.setText("Disconnected");
        }

        holder.btnSync.setOnClickListener(v -> {
            // Handle re-connect logic
        });

        holder.btnDisconnect.setOnClickListener(v -> {
            // Handle disconnect logic, ex,

        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }


    public class ConnDeviceViewHolder extends RecyclerView.ViewHolder {

        private ImageView deviceIcon;
        private TextView deviceName;
        private ImageView btnSync;
        private AppCompatButton btnStatus;
        private AppCompatButton btnDisconnect;

        public ConnDeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceIcon = itemView.findViewById(R.id.deviceIcon);
            deviceName = itemView.findViewById(R.id.managedDeviceName);
            btnSync = itemView.findViewById(R.id.btnSync);
            btnStatus = itemView.findViewById(R.id.btnStatus);
            btnDisconnect = itemView.findViewById(R.id.btnDisconnect);
        }
    }
}
