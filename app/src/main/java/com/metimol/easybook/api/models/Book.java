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

    @SerializedName("files")
    private BookFiles files;

    private int progressPercentage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrlName() { return urlName; }
    public void setUrlName(String urlName) { this.urlName = urlName; }

    public Genre getGenre() { return genre; }
    public void setGenre(Genre genre) { this.genre = genre; }

    public Serie getSerie() { return serie; }
    public void setSerie(Serie serie) { this.serie = serie; }

    public String getSerieIndex() { return serieIndex; }
    public void setSerieIndex(String serieIndex) { this.serieIndex = serieIndex; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Author> getAuthors() { return authors; }
    public void setAuthors(List<Author> authors) { this.authors = authors; }

    public List<Author> getReaders() { return readers; }
    public void setReaders(List<Author> readers) { this.readers = readers; }

    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }

    public int getDislikes() { return dislikes; }
    public void setDislikes(int dislikes) { this.dislikes = dislikes; }

    public String getDefaultPoster() { return defaultPoster; }
    public void setDefaultPoster(String defaultPoster) { this.defaultPoster = defaultPoster; }

    public String getDefaultPosterMain() { return defaultPosterMain; }
    public void setDefaultPosterMain(String defaultPosterMain) { this.defaultPosterMain = defaultPosterMain; }

    public int getTotalDuration() { return totalDuration; }
    public void setTotalDuration(int totalDuration) { this.totalDuration = totalDuration; }

    public BookFiles getFiles() { return files; }
    public void setFiles(BookFiles files) { this.files = files; }

    public int getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }
}