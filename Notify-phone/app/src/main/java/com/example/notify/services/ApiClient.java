package com.example.notify.services;

import com.example.notify.interfaces.ApiService;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Cache services by their base URL to handle multiple devices/IPs
    private static final Map<String, ApiService> serviceCache = new HashMap<>();

    public static ApiService getService(String baseURL) {
        if (!serviceCache.containsKey(baseURL)) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(baseURL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            serviceCache.put(baseURL, retrofit.create(ApiService.class));
        }
        return serviceCache.get(baseURL);
    }
}
