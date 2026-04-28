package com.example.notify;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.notify.interfaces.ApiService;
import com.example.notify.services.ApiClient;
import com.example.notify.services.AuthenticateConnection;
import com.example.notify.utils.NetworkDiscovery;

public class PIN_VerificationActivity extends AppCompatActivity {

    private String TAG = "Notifi:PIN_VerificationActivity";
    private EditText pin1, pin2, pin3, pin4;
    private Button btnSubmit;

    private AuthenticateConnection authenticateConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pin_verification);

        authenticateConnection = new AuthenticateConnection(this);

        TextView targetDeviceName = findViewById(R.id.targetDeviceName);
        if(NetworkDiscovery.serverDeviceName != null) {
            targetDeviceName.setText(NetworkDiscovery.serverDeviceName);
        }
        else{
            Log.e(TAG, "Server device name is null, it should not at this point");
        }



        pin1 = findViewById(R.id.pinDigit1);
        pin2 = findViewById(R.id.pinDigit2);
        pin3 = findViewById(R.id.pinDigit3);
        pin4 = findViewById(R.id.pinDigit4);
        btnSubmit = findViewById(R.id.btnSubmitPin);

        setupPinAutoMove();

        btnSubmit.setOnClickListener(v -> verifyPin());

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

    private void setupPinAutoMove() {
        pin1.addTextChangedListener(new PinTextWatcher(pin1, pin2));
        pin2.addTextChangedListener(new PinTextWatcher(pin2, pin3));
        pin3.addTextChangedListener(new PinTextWatcher(pin3, pin4));
        pin4.addTextChangedListener(new PinTextWatcher(pin4, null));

        // Listen for backspace/delete key
        pin2.setOnKeyListener(new PinKeyListener(pin1));
        pin3.setOnKeyListener(new PinKeyListener(pin2));
        pin4.setOnKeyListener(new PinKeyListener(pin3));
    }

    private void resetPinBackgrounds() {
        pin1.setBackgroundResource(R.drawable.pin_box_bg);
        pin2.setBackgroundResource(R.drawable.pin_box_bg);
        pin3.setBackgroundResource(R.drawable.pin_box_bg);
        pin4.setBackgroundResource(R.drawable.pin_box_bg);
    }

    private void verifyPin() {
        String pin = pin1.getText().toString() +
                pin2.getText().toString() +
                pin3.getText().toString() +
                pin4.getText().toString();

        ApiService apiService = ApiClient.getService(authenticateConnection.getBaseURL());
        authenticateConnection.authenticateLAN(pin, apiService, isAuthenticated -> {
            if (isAuthenticated) {
                Toast.makeText(this, "Connection Successful!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, ConnectedDeviceListActivity.class);
                startActivity(intent);
            } else {
                pin1.setBackgroundResource(R.drawable.pin_box_error);
                pin2.setBackgroundResource(R.drawable.pin_box_error);
                pin3.setBackgroundResource(R.drawable.pin_box_error);
                pin4.setBackgroundResource(R.drawable.pin_box_error);
                Toast.makeText(this, "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class PinTextWatcher implements TextWatcher {
        private final View currentView;
        private final View nextView;

        public PinTextWatcher(View currentView, View nextView) {
            this.currentView = currentView;
            this.nextView = nextView;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            resetPinBackgrounds();

            if (s.length() == 1) {
                if (nextView != null) {
                    nextView.requestFocus();
                } else {
                    hideKeyboard(currentView);
                }
            }
        }



        @Override
        public void afterTextChanged(Editable s) {}
    }

    private class PinKeyListener implements View.OnKeyListener {
        private final EditText prevView;

        PinKeyListener(EditText prevView) {
            this.prevView = prevView;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                EditText currentEditText = (EditText) v;
                if (currentEditText.getText().toString().isEmpty() && prevView != null) {
                    prevView.requestFocus();
                    prevView.setText("");
                    return true;
                }
            }
            return false;
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}