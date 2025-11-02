package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;

public class ApiResponse<T> {
    @SerializedName("data")
    private T data;

    // Getters
    public T getData() { return data; }
}
