package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;

public class Author {
    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("surname")
    private String surname;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getSurname() {
        return surname;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }
}