package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;

public class BooksWithDatesData {

    @SerializedName("booksWithDates")
    private BookListResponse booksWithDates;

    public BookListResponse getBooksWithDates() {
        return booksWithDates;
    }
}