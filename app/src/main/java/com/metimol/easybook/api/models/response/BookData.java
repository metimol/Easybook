package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;
import com.metimol.easybook.api.models.Book;

public class BookData {
    @SerializedName("book")
    private Book book;

    // Getters
    public Book getBook() { return book; }
}