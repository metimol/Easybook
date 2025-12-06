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
    private String url;
    @SerializedName("size")
    private int size;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}