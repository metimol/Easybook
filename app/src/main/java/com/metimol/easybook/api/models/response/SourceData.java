package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;

public class SourceData {
    @SerializedName("booksBySource")
    private BookListResponse bookListResponse;

    // Getters
    public BookListResponse getBookListResponse() { return bookListResponse; }
}
