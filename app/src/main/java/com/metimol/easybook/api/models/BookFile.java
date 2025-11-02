package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;

public class BookFile {
    @SerializedName("id")
    private int id;
    @SerializedName("index")
    private int index;
    @SerializedName("title")
    private String title;
    @SerializedName("fileName")
    private String fileName;
    @SerializedName("duration")
    private int duration;
    @SerializedName("url")
    private String url; // URL to audio File
    @SerializedName("size")
    private int size;

    // Getters
    public int getId() { return id; }
    public int getIndex() { return index; }
    public String getTitle() { return title; }
    public String getFileName() { return fileName; }
    public int getDuration() { return duration; }
    public String getUrl() { return url; }
    public int getSize() { return size; }
}