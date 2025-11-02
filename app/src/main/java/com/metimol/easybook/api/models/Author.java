package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;

public class Author {
    @SerializedName("id")
    private int id;
    @SerializedName("name")
    private String name;
    @SerializedName("surname")
    private String surname;

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public String getSurname() { return surname; }
}