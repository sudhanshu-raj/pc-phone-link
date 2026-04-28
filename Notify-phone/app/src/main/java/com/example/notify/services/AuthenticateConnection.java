package com.example.notify.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.EditText;

import com.example.notify.R;
import com.example.notify.interfaces.ApiService;
import com.example.notify.utils.NetworkDiscovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthenticateConnection {

    private static final String TAG = "Notifi:AuthenticateConnection";

    private final Context context;
    private final SharedPreferences sharedPref;

    boolean isTokenValid;
    public String webSocketURL;

    OkHttpClient okHttpClient = new OkHttpClient();
    public static WebSocket ws = null;
    public static boolean isLANConAuthenticated = false;

    public AuthenticateConnection(Context context) {
        this.context = context;
        this.sharedPref = context.getSharedPreferences("Notify_shared_pref", Context.MODE_PRIVATE);
    }

    public void verifyConnection(){
        Log.d(TAG, "Verifying connection...");
        if(!NetworkDiscovery.isConnectedToLAN){
            Log.d(TAG, "Not connected to LAN, cannot authenticate");
            return;
        }
        if(getBaseURL()==null){
            Log.d(TAG, "Base URL is null, cannot authenticate");
            return;
        }

        ApiService api = ApiClient.getService(getBaseURL());

        String token = getDeviceToken();
        String deviceID = sharedPref.getString("deviceID", null);
        if(token != null && deviceID != null){
            // Device has token, so verify that it is valid or not
            Log.d(TAG, "Device has token, so verify that it is valid or not");
            verifyToken(token,deviceID, api);
        }
        else{
            // Device has no token, so it must authenticate via PIN first
            Log.d(TAG, "Device has no token, so it must authenticate via PIN first");
            generatePIN(api);
        }
    }

    private void verifyToken(String token, String deviceID,ApiService api){
        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("deviceID", deviceID);
        Log.d(TAG,"Sending the token:"+token+" and deviceID:"+deviceID+" to the server");

        api.verifyToken(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> result = response.body();
                if(result != null){
                     String status = (String) result.get("status");
                     if(status != null && status.equals("success")){
                         Log.d(TAG, "Token is valid");
                         isTokenValid = true;
                         webSocketURL = (String) result.get("webSocketURL");
                         isLANConAuthenticated = true;
                         startWebSocket(webSocketURL);
                     }
                     else{
                         Log.d(TAG, Objects.requireNonNull(result.get("message")).toString());
                         isTokenValid = false;
                         generatePIN(api);
                     }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.d(TAG, "Error verifying token: " + t.getMessage());
                isTokenValid = false;
                generatePIN(api);
            }
        });

    }

    public void generatePIN(ApiService api){
        try{
        Map<String, Object> body = new HashMap<>();

        body.put("deviceName", sharedPref.getString("deviceName", null));
        body.put("phoneIP",new NetworkDiscovery(context).getPhoneIP());
        api.generatePIN(body).enqueue(new Callback<Map<String, Object>>() {

            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> result = response.body();
                if(result != null && result.get("status") != null && result.get("status").equals("success")){
                    Log.d(TAG, "PIN generated successfully");
                    String deviceID = (String) result.get("deviceID");
                    sharedPref.edit().putString("deviceID", deviceID).apply();
                }
                else{
                    Log.d(TAG, Objects.requireNonNull(result.get("message")).toString());
                }

            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.d(TAG, "Error generating PIN: " + t.getMessage());
            }
        });
        }
        catch (Exception e){
            Log.e(TAG, "Error while generating PIN : " + e.getMessage());
        }

    }
    public void submitLANPIN(){
        try {
            ApiService api = ApiClient.getService(getBaseURL());
            String pinInput = getPINInput();
            if(pinInput != null) {
                authenticateLAN(pinInput, api, isAuthenticated -> {});
            }
            else{
                Log.d(TAG, "PIN input is null");
            }
        }
        catch (Exception e){
            Log.d(TAG, "Exception : " + e.getMessage());
        }
    }
    public void authenticateLAN(String pinInput, ApiService api, GetAuthenticateResponse callback){
        Map<String, Object> body = new HashMap<>();
        body.put("pin", pinInput);
        body.put("deviceID",getDeviceID());

        api.authenticateLAN(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String,Object> result = response.body();
                if(result != null && result.get("status") != null && result.get("status").equals("success")){
                    Log.d(TAG, "Authentication successful");
                    String token = (String) result.get("token");
                    sharedPref.edit().putString("token", token).apply(); //HERE WE SHOULD PUT THIS IN ENCRYPTED FORMAT
                    isTokenValid = true;
                    webSocketURL = (String) result.get("webSocketURL");
                    startWebSocket(webSocketURL);
                    isLANConAuthenticated = true;
                    callback.onResponse(true);
                }
                else{
                    Log.d(TAG, Objects.requireNonNull(result.get("message")).toString());
                    callback.onResponse(false);
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.d(TAG, "Error authenticating LAN: " + t.getMessage());
                callback.onResponse(false);
            }
        });
    }

    private void startWebSocket(String url) {
        String token = getDeviceToken();
        if(token == null){
            Log.d(TAG, "Token is null, cannot start WebSocket");
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization",token)
                .build();

        ws = okHttpClient.newWebSocket(request, new WebSocketService((Activity) context));
        Log.d(TAG, "WebSocket started on URL: " + url);

        // Optional: Trigger shutdown of the dispatcher when finished to avoid memory leaks
        // client.dispatcher().executorService().shutdown();
    }

    public String getPINInput(){

        EditText pinInput = (EditText) ((Activity) context).findViewById(R.id.PNINPTXT);
        return pinInput.getText().toString();
    }

    public String getDeviceName(){
        try {
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
            String deviceName = manufacturer + " " + model;

            String userDeviceName = Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
            if (userDeviceName == null) {
                userDeviceName = Settings.Secure.getString(context.getContentResolver(), "bluetooth_name");
            }
            return deviceName + "-" + userDeviceName;
        }
        catch (Exception e){
            Log.d(TAG, "Exception : " + e.getMessage());
            return null;
        }
    }

    public String getDeviceID(){
        return sharedPref.getString("deviceID", null);
    }

    public String getDeviceToken(){
        return sharedPref.getString("token",null);
    }


    public String getBaseURL(){
        if(NetworkDiscovery.serverIP == null) return null;
        return "http://" + NetworkDiscovery.serverIP + ":" + NetworkDiscovery.httpPort + "/api/v1/";
    }

    public interface GetAuthenticateResponse {
        void onResponse(boolean isAuthenticated);
    }
}
