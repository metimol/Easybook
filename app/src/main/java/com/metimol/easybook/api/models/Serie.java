package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;

public class Serie {
    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("books_count")
    private int booksCount;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getBooksCount() {
        return booksCount;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }
}