package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;
import com.metimol.easybook.api.models.BookWithDate;

import java.util.List;

public class BookListResponse {
    @SerializedName("count")
    private int count;
    @SerializedName("items")
    private List<BookWithDate> items;

    public int getCount() { return count; }
    public List<BookWithDate> getItems() { return items; }
}
