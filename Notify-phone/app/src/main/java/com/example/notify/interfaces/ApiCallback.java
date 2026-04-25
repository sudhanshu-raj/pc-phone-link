package com.example.notify.interfaces;

public interface ApiCallback<T> {
    void onSuccess(T result);
    void onError(String errorMessage);
}
