package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;

public class SourceData {
    @SerializedName("booksBySource")
    private CategoryBookListResponse bookListResponse;

    public CategoryBookListResponse getBookListResponse() { return bookListResponse; }
}