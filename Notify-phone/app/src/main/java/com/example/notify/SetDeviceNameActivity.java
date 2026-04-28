package com.example.notify;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.notify.utils.NetworkDiscovery;

public class SetDeviceNameActivity extends AppCompatActivity {

    private static final String TAG = "Notifi:SetDeviceNameActivity";
    private EditText deviceNameInput;
    private TextView hintText;
    private AppCompatButton setButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_set_device_name);

        deviceNameInput = findViewById(R.id.editTextText);
        hintText = findViewById(R.id.textView);
        setButton = findViewById(R.id.button);

        // 1. Listen for text changes to update color and line dynamically
        deviceNameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 10) {
                    deviceNameInput.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF5555")));
                } else {
                    deviceNameInput.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 2. Validate on click
        setButton.setOnClickListener(v -> {
            String name = deviceNameInput.getText().toString().trim();
            
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            } else if (name.length() > 10) {
                Toast.makeText(this, "Name too long!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Device name set: " + name, Toast.LENGTH_SHORT).show();

                SharedPreferences sharedPref = getSharedPreferences("Notify_shared_pref", MODE_PRIVATE);
                sharedPref.edit().putString("deviceName", name).apply();
                Intent intent = new Intent(this, DeviceSearchingActivity.class);
                startActivity(intent);

            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            //From dp to pixels
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
}