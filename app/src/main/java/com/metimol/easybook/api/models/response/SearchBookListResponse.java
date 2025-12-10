package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;
import com.metimol.easybook.api.models.Book;

import java.util.List;

public class SearchBookListResponse {
    @SerializedName("count")
    private int count;
    @SerializedName("items")
    private List<Book> items;

    public int getCount() { return count; }
    public List<Book> getItems() { return items; }
}