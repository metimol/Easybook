package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;
import com.metimol.easybook.api.models.Serie;
import java.util.List;

public class SeriesSearchListResponse {
    @SerializedName("count")
    private int count;

    @SerializedName("items")
    private List<Serie> items;

    public int getCount() { return count; }
    public List<Serie> getItems() { return items; }
}