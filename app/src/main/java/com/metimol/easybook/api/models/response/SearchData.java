package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;

public class SearchData {
    @SerializedName("booksSearch")
    private BookListResponse bookListResponse;

    // Getters
    public BookListResponse getBookListResponse() { return bookListResponse; }
}
