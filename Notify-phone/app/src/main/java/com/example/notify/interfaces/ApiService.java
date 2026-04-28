package com.example.notify.interfaces;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface ApiService {

    @GET("/ping")
    Call<Map<String, Object>> ping();

    @GET("/pong")
    Call<Map<String, Object>> pong();

    @POST("/phonesFound")
    Call<Map<String, Object>> phonesFound(@Body Map<String, String> body);


    @POST("/verifyLANToken")
    Call<Map<String, Object>> verifyToken(@Body Map<String, Object> body);

    @POST("/generatePIN")
    Call<Map<String,Object>> generatePIN(@Body Map<String, Object> body);

    @POST("/authenticateLAN")
    Call<Map<String,Object>> authenticateLAN(@Body Map<String,Object> body);

}
