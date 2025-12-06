package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class BookFiles {
    @SerializedName("full")
    private List<BookFile> full;
    @SerializedName("mobile")
    private List<BookFile> mobile;

    public List<BookFile> getFull() { return full; }
    public void setFull(List<BookFile> full) { this.full = full; }

    public List<BookFile> getMobile() { return mobile; }
    public void setMobile(List<BookFile> mobile) { this.mobile = mobile; }
}