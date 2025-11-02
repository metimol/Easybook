package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;

public class Serie {
    @SerializedName("id")
    private int id;
    @SerializedName("name")
    private String name;
    @SerializedName("booksCount")
    private int booksCount;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getBooksCount() {
        return booksCount;
    }
}
