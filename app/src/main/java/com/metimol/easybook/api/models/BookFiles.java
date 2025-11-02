package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BookFiles {
    @SerializedName("full")
    private List<BookFile> full;
    @SerializedName("mobile")
    private List<BookFile> mobile;

    // Getters
    public List<BookFile> getFull() { return full; }
    public List<BookFile> getMobile() { return mobile; }
}