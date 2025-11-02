package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Book {
    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("urlName")
    private String urlName;
    @SerializedName("genre")
    private Genre genre;
    @SerializedName("serie")
    private Serie serie;
    @SerializedName("serieIndex")
    private String serieIndex;
    @SerializedName("aboutBb")
    private String description;
    @SerializedName("authors")
    private List<Author> authors;
    @SerializedName("readers")
    private List<Author> readers;
    @SerializedName("likes")
    private int likes;
    @SerializedName("dislikes")
    private int dislikes;
    @SerializedName("defaultPoster")
    private String defaultPoster;
    @SerializedName("defaultPosterMain")
    private String defaultPosterMain;
    @SerializedName("totalDuration")
    private int totalDuration;

    // For detailed book request
    @SerializedName("files")
    private BookFiles files;

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getUrlName() { return urlName; }
    public Genre getGenre() { return genre; }
    public Serie getSerie() { return serie; }
    public String getSerieIndex() { return serieIndex; }
    public String getDescription() { return description; }
    public List<Author> getAuthors() { return authors; }
    public List<Author> getReaders() { return readers; }
    public int getLikes() { return likes; }
    public int getDislikes() { return dislikes; }
    public String getDefaultPoster() { return defaultPoster; }
    public String getDefaultPosterMain() { return defaultPosterMain; }
    public int getTotalDuration() { return totalDuration; }
    public BookFiles getFiles() { return files; }
}