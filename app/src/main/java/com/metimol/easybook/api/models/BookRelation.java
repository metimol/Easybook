package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;

public class BookRelation {
    @SerializedName("book_id")
    private String bookId;

    public String getBookId() {
        return bookId;
    }
}
