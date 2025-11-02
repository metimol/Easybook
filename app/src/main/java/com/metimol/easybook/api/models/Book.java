package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Book {
    @SerializedName("id")
    private int id;
    @SerializedName("name")
    private String name;
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
    @SerializedName("totalDuration")
    private int totalDuration;

    // For detailed book request
    @SerializedName("files")
    private BookFiles files;

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<Author> getAuthors() { return authors; }
    public List<Author> getReaders() { return readers; }
    public int getLikes() { return likes; }
    public int getDislikes() { return dislikes; }
    public int getTotalDuration() { return totalDuration; }
    public BookFiles getFiles() { return files; }
}