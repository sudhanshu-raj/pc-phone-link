package com.example.notify.services;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.notify.MainActivity;
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

    boolean isTokenValid =false;
    public String webSocketURL;

    OkHttpClient okHttpClient = new OkHttpClient();
    public static WebSocket ws = null;
    public static boolean isLANConAuthenticated = false;
    public AuthenticateConnection(Context context) {
        this.context = context;
        this.sharedPref = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }

    private void verifyToken(String token, String serverDeviceID,ApiService api){

        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("deviceID", getThisDeviceID());
        Log.d(TAG,"Sending the token and deviceID to the server");

        try {

            api.verifyToken(body).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    Map<String, Object> result = response.body();
                    if (result != null) {
                        String status = (String) result.get("status");
                        if (status != null && status.equals("success")) {
                            Log.d(TAG, "Token is valid");
                            isTokenValid = true;
                            webSocketURL = (String) result.get("webSocketURL");
                            isLANConAuthenticated = true;
                            startWebSocket(webSocketURL, serverDeviceID);
                        } else {
                            Log.d(TAG, "Token is invalid");
                            Log.d(TAG, Objects.requireNonNull(result.get("message")).toString());
                        }
                    }
                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    Log.d(TAG, "Error verifying token: " + t.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error while verifying token: " + e.getMessage());
        }

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

                    sharedPref.edit().putBoolean(Constants.IS_DEVICE_SETUP,true).apply();
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

        // Close existing connection if it exists
        if (ws != null) {
            Log.d(TAG, "Closing existing WebSocket before opening new one");
            ws.close(1000, "New connection starting");
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization",serverInfo.getToken())
                .build();

        ws = okHttpClient.newWebSocket(request, new WebSocketService(context));
        Log.d(TAG, "WebSocket client started on URL: " + url);
    }

    // It will reconnect to the last device that was used to connect to the server, it must be already authenticated
    public void reconnectLastDevice() {
        // Check if WiFi is connected before attempting to reconnect
        NetworkDiscovery networkDiscovery = new NetworkDiscovery(context);
        if (!networkDiscovery.isWifiConnected()) {
            Log.d(TAG, "Not connected to WiFi. Skipping reconnection attempt.");
            return;
        }

        Log.d(TAG, "Attempting to reconnect to the last used device...");
        try {
            // 1. Find the last seen device from SharedPreferences
            Map<String, ?> allEntries = sharedPref.getAll();
            ServerDeviceModel lastDevice = null;
            Date lastSeenDate = null;

            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                if (entry.getKey().startsWith("ID")) {
                    try {
                        ServerDeviceModel device = new Gson().fromJson((String) entry.getValue(), ServerDeviceModel.class);
                        if (device != null && device.getLastSeen() != null) {
                            if (lastDevice == null || device.getLastSeen().after(lastSeenDate)) {
                                Log.d(TAG, "Found more recent device: " + device.getDeviceName() + " (" + device.getDeviceID() + ")");
                                lastSeenDate = device.getLastSeen();
                                lastDevice = device;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing device data during reconnection", e);
                    }
                }
            }

            if (lastDevice != null && lastDevice.getDeviceID() != null) {
                String targetDeviceId = lastDevice.getDeviceID();
                String targetDeviceName = lastDevice.getDeviceName();
                String serverIP = lastDevice.getDeviceIP();
                Integer httpPort = lastDevice.getHttpPort();
                String token = lastDevice.getToken();

                Log.d(TAG, "Last device found: " + lastDevice.getDeviceName() + " (" + targetDeviceId + ")");

                if (serverIP != null && httpPort != null && token != null) {
                    ApiService api = ApiClient.getService("http://" + serverIP + ":" + httpPort + "/api/v1/");
                    Log.d(TAG, "Pinging at server on api :"+api.toString());

                    pingServer(api, isAvailable -> {
                        if (isAvailable) {
                            // Server is up and same ip address to just verify the token to connect
                            verifyToken(token, targetDeviceId, api);
                        } else {
                            Log.d(TAG, "Ping failed,have to found the server again to re-connect");

                            // Starting mDNS to find the device's current IP
                            NetworkDiscovery discovery = new NetworkDiscovery(context);
                            discovery.connectLAN((deviceName, ip, port) -> {
                                if (targetDeviceName.equals(deviceName)) {
                                    ApiService newAPI = ApiClient.getService("http://" + ip + ":" + port + "/api/v1/");
                                    Log.d(TAG, "Found server during auto-reconnect: " + deviceName + " at " + ip);
                                    verifyToken(token, targetDeviceId, newAPI);
                                    discovery.unregister();
                                }

                            });
                        }
                    });

                }


            } else {
                Log.d(TAG, "No previously paired device found to reconnect.");
            }
        } catch (Exception e){
            Log.e(TAG, "Error during reconnect the last device", e);
        }
    }


    public void pingServer(ApiService api,PingResponseCallback callback){

        try {
            api.ping().enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    Log.d(TAG, "Ping sent successfully");
                    Map<String, Object> res = response.body();
                    if (res != null && res.get("message") != null && res.get("message").equals("pong")) {
                        callback.onResponse(true);
                    } else {
                        callback.onResponse(false);
                    }

                }

                @Override
                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    Log.d(TAG, "Error sending ping: " + t.getMessage());
                    callback.onResponse(false);
                }
            });
        }
        catch (Exception e){
            Log.d(TAG, "Error sending ping: " + e.getMessage());
            callback.onResponse(false);
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

    public interface PingResponseCallback {
        void onResponse(boolean isAvailable);
    }
}
