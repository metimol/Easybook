package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BookWithDate {
    @SerializedName("title")
    private String title;
    @SerializedName("data")
    private List<Book> data;

    public String getTitle() {
        return title;
    }

    public List<Book> getData() {
        return data;
    }
}
