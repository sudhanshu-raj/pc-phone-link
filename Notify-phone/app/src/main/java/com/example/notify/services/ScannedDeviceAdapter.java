package com.example.notify.services;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.RecyclerView;

import com.example.notify.PIN_VerificationActivity;
import com.example.notify.R;
import com.example.notify.interfaces.ApiService;
import com.example.notify.utils.ScannedDeviceModel;

import java.util.List;

public class ScannedDeviceAdapter extends RecyclerView.Adapter<ScannedDeviceAdapter.ScanDeviceViewHolder> {

    private List<ScannedDeviceModel> deviceList;
    private Context context;

    public ScannedDeviceAdapter(List<ScannedDeviceModel> deviceList, Context context) {
        this.deviceList = deviceList;
        this.context = context;
    }

    @NonNull
    @Override
    public ScanDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new ScanDeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanDeviceViewHolder holder, int position) {
        ScannedDeviceModel device = deviceList.get(position);
        holder.deviceName.setText(device.getDeviceName());

        if (device.isPairing()) {
            holder.statusCircle.setVisibility(View.GONE);
            holder.statusCircleGlowing.setVisibility(View.VISIBLE);
            Animation twinkle = AnimationUtils.loadAnimation(context, R.anim.twinkle);
            holder.statusCircleGlowing.startAnimation(twinkle);
        } else {
            holder.statusCircleGlowing.clearAnimation();
            holder.statusCircleGlowing.setVisibility(View.GONE);
            holder.statusCircle.setVisibility(View.VISIBLE);
        }

        holder.pairButton.setOnClickListener(v -> {
            // Handle pair logic, ex,
            AuthenticateConnection authConn= new AuthenticateConnection(context);
            ApiService apiService = ApiClient.getService(authConn.getBaseURL());
            authConn.generatePIN(apiService);

            Intent intent = new Intent(context, PIN_VerificationActivity.class);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    public static class ScanDeviceViewHolder extends RecyclerView.ViewHolder {
        private TextView deviceName;
        private AppCompatButton pairButton;
        private View statusCircle, statusCircleGlowing;

        public ScanDeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.deviceName);
            pairButton = itemView.findViewById(R.id.pairButton);
            statusCircle = itemView.findViewById(R.id.statusCircle);
            statusCircleGlowing = itemView.findViewById(R.id.statusCircleGlowing);
        }
    }
}