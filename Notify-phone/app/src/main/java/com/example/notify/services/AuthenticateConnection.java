package com.example.notify.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.notify.interfaces.ApiService;
import com.example.notify.utils.ScannedDeviceModel;
import com.example.notify.utils.ServerDeviceModel;
import com.example.notify.utils.Constants;
import com.example.notify.utils.NetworkDiscovery;

import com.google.gson.Gson;

import java.util.Date;
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
        this.sharedPref = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }

    public void verifyConnection(String serverDeviceID){
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

        ServerDeviceModel serverInfo = getSavedDeviceData(serverDeviceID);
        if(serverInfo == null || serverInfo.getToken() == null){
            Log.d(TAG, "Invalid request, cannot authenticate");
            return;
        }

        String token = serverInfo.getToken();
        String deviceID = sharedPref.getString("deviceID", null);

        verifyToken(token,deviceID, api);


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
                         startWebSocket(webSocketURL,deviceID);
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
        body.put("clientDeviceID", sharedPref.getString(Constants.THIS_DEVICE_ID, null));
        api.generatePIN(body).enqueue(new Callback<Map<String, Object>>() {

            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String, Object> result = response.body();
                if(result != null && result.get("status") != null && result.get("status").equals("success")){
                    Log.d(TAG, "PIN generated successfully");
                    String serverDeviceID = (String) result.get("deviceID");
                    HashMap<String,Object> deviceInfo = new HashMap<>();
                    deviceInfo.put(Constants.KEY_LAST_SEEN,new Date());
                    storeDeviceData(serverDeviceID,deviceInfo);
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

    public void authenticateLAN(String pinInput, ApiService api, GetAuthenticateResponse callback){
        Map<String, Object> body = new HashMap<>();
        body.put("pin", pinInput);
        body.put("clientDeviceID",getThisDeviceID());

        api.authenticateLAN(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Map<String,Object> result = response.body();
                if(result != null && result.get("status") != null && result.get("status").equals("success")){
                    Log.d(TAG, "Authentication successful");
                    isTokenValid = true;
                    isLANConAuthenticated = true;
                    String token = (String) result.get("token");
                    
                    // Safely handle Double-to-Integer conversion from GSON
                    Object wsPortObj = result.get("webSocketPort");
                    Integer wsPort = (wsPortObj instanceof Number) ? ((Number) wsPortObj).intValue() : null;
                    HashMap<String,Object> deviceInfo = new HashMap<>();
                    deviceInfo.put(Constants.KEY_TOKEN,token);
                    deviceInfo.put(Constants.KEY_WS_PORT,wsPort);
                    deviceInfo.put(Constants.KEY_LAST_SEEN,new Date());
                    deviceInfo.put(Constants.KEY_IS_CONNECTED,true);
                    String serverDeviceID = NetworkDiscovery.serverDeviceID;
                    if(serverDeviceID==null){
                        Log.e(TAG,"Server device ID is null, cannot store the data");
                    }
                    storeDeviceData(serverDeviceID,deviceInfo);

                    sharedPref.edit().putBoolean("isDeviceSetup",true).apply();
                    webSocketURL = "ws://" + NetworkDiscovery.serverIP + ":" + wsPort ;
                    startWebSocket(webSocketURL,serverDeviceID);

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

    private void startWebSocket(String url,String serverDeviceID) {
        ServerDeviceModel serverInfo = getSavedDeviceData(serverDeviceID);
        if(serverInfo == null || serverInfo.getToken() == null){
            Log.d(TAG, "Invalid request, cannot start WebSocket");
            return;
        }
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization",serverInfo.getToken())
                .build();

        ws = okHttpClient.newWebSocket(request, new WebSocketService(context));
        Log.d(TAG, "WebSocket client started on URL: " + url);
    }

    public void reconnectLastDevice() {
        Log.d(TAG, "Attempting to reconnect to the last used device...");
        // 1. Find the last seen device from SharedPreferences
        Map<String, ?> allEntries = sharedPref.getAll();
        ServerDeviceModel lastDevice = null;
        Date lastSeenDate = new Date(0);

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith("ID")) {
                try {
                    ServerDeviceModel device = new Gson().fromJson((String) entry.getValue(), ServerDeviceModel.class);
                    if (device != null && device.getLastSeen() != null && device.getLastSeen().after(lastSeenDate)) {
                        Log.d(TAG, "Found last seen device: " + device.getDeviceName() + " (" + device.getDeviceID() + ")");
                        lastSeenDate = device.getLastSeen();
                        lastDevice = device;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing device data during reconnection", e);
                }
            }
        }

        if (lastDevice != null && lastDevice.getDeviceID() != null) {
            final String targetDeviceId = lastDevice.getDeviceID();
            Log.d(TAG, "Last device found: " + lastDevice.getDeviceName() + " (" + targetDeviceId + ")");

            // 2. Start mDNS to find the device's current IP
            NetworkDiscovery discovery = new NetworkDiscovery(context);
            discovery.connectLAN((deviceName, ip, port) -> {
                // Here while re-connecting we need to check if this device which we connecting is same which
                // we have already connected with
                Log.d(TAG, "Found server during auto-reconnect: " + deviceName + " at " + ip);
                verifyConnection(targetDeviceId);
                discovery.unregister();
            });
        } else {
            Log.d(TAG, "No previously paired device found to reconnect.");
        }
    }


    public void storeDeviceData(String deviceId, HashMap<String,Object> data) {
        try {
            ServerDeviceModel deviceInfo = getSavedDeviceData(deviceId);
            if (deviceInfo == null) {
                deviceInfo = new ServerDeviceModel();
            }

            for (String key : data.keySet()) {
                switch (key) {
                    case Constants.KEY_DEVICE_NAME:
                        deviceInfo.setDeviceName((String) data.get(key));
                        break;
                    case Constants.KEY_DEVICE_ID:
                        deviceInfo.setDeviceID((String) data.get(key));
                        break;
                    case Constants.KEY_DEVICE_IP:
                        deviceInfo.setDeviceIP((String) data.get(key));
                        break;
                    case Constants.KEY_IS_CONNECTED:
                        deviceInfo.setConnected((Boolean) data.get(key));
                        break;
                    case Constants.KEY_HTTP_PORT:
                        Object httpPortObj = data.get(key);
                        deviceInfo.setHttpPort(httpPortObj instanceof Number ? ((Number) httpPortObj).intValue() : null);
                        break;
                    case Constants.KEY_WS_PORT:
                        Object wsPortObj = data.get(key);
                        deviceInfo.setWsPort(wsPortObj instanceof Number ? ((Number) wsPortObj).intValue() : null);
                        break;
                    case Constants.KEY_LAST_SEEN:
                        deviceInfo.setLastSeen((Date) data.get(key));
                        break;
                    case Constants.KEY_TOKEN:
                        deviceInfo.setToken((String) data.get(key));
                        break;
                }
            }
            Gson gson = new Gson();
            String json = gson.toJson(deviceInfo);

            sharedPref.edit().putString(("ID"+deviceId), json).apply();
        }
        catch (Exception e){
            Log.e(TAG,"Error while storing device data"+e.getMessage());
        }
    }

    public ServerDeviceModel getSavedDeviceData(String deviceId) {
        String json = sharedPref.getString("ID" + deviceId, null);
        if (json == null) return null;

        return new Gson().fromJson(json, ServerDeviceModel.class);
    }

    public String getThisDeviceID(){
        return sharedPref.getString(Constants.THIS_DEVICE_ID, null);
    }


    public String getBaseURL(){
        if(NetworkDiscovery.serverIP == null) return null;
        return "http://" + NetworkDiscovery.serverIP + ":" + NetworkDiscovery.httpPort + "/api/v1/";
    }

    public interface GetAuthenticateResponse {
        void onResponse(boolean isAuthenticated);
    }
}
