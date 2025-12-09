package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;

public class BookFile {
    @SerializedName("i")
    private int index;
    @SerializedName("t")
    private String title;
    @SerializedName("f")
    private String fileName;
    @SerializedName("d")
    private int duration;
    @SerializedName("s")
    private long size;
    @SerializedName("u")
    private String url;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}