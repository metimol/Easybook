package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;

public class SearchData {
    @SerializedName("booksSearch")
    private SearchBookListResponse bookListResponse;

    // Getters
    public SearchBookListResponse getBookListResponse() { return bookListResponse; }
}