package com.metimol.easybook.api.models.response;

import com.google.gson.annotations.SerializedName;

public class SeriesSearchData {
    @SerializedName("seriesSearch")
    private SeriesSearchListResponse seriesListResponse;

    public SeriesSearchListResponse getSeriesListResponse() { return seriesListResponse; }
}