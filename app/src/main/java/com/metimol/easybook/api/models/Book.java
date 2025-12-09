package com.metimol.easybook.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class Book {
    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("url_name")
    private String urlName;
    @SerializedName("genre")
    private Genre genre;
    @SerializedName("serie")
    private Serie serie;
    @SerializedName("serie_index")
    private String serieIndex;
    @SerializedName("description")
    private String description;
    @SerializedName("book_authors")
    private List<BookAuthorRelation> bookAuthorsRelations;
    @SerializedName("book_readers")
    private List<BookReaderRelation> bookReadersRelations;
    @SerializedName("likes")
    private int likes;
    @SerializedName("dislikes")
    private int dislikes;
    @SerializedName("default_poster")
    private String defaultPoster;
    @SerializedName("default_poster_main")
    private String defaultPosterMain;
    @SerializedName("total_duration")
    private int totalDuration;
    @SerializedName("files")
    private List<BookFile> files;

    private int progressPercentage;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrlName() {
        return urlName;
    }

    public Genre getGenre() {
        return genre;
    }

    public Serie getSerie() {
        return serie;
    }

    public String getSerieIndex() {
        return serieIndex;
    }

    public String getDescription() {
        return description;
    }

    public int getLikes() {
        return likes;
    }

    public int getDislikes() {
        return dislikes;
    }

    public String getDefaultPoster() {
        return defaultPoster;
    }

    public String getDefaultPosterMain() {
        return defaultPosterMain;
    }

    public int getTotalDuration() {
        return totalDuration;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(int progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDefaultPosterMain(String defaultPosterMain) {
        this.defaultPosterMain = defaultPosterMain;
    }

    public void setDefaultPoster(String defaultPoster) {
        this.defaultPoster = defaultPoster;
    }

    public void setTotalDuration(int totalDuration) {
        this.totalDuration = totalDuration;
    }

    public List<Author> getAuthors() {
        List<Author> authors = new ArrayList<>();
        if (bookAuthorsRelations != null) {
            for (BookAuthorRelation relation : bookAuthorsRelations) {
                if (relation.author != null)
                    authors.add(relation.author);
            }
        }
        return authors;
    }

    public List<Author> getReaders() {
        List<Author> readers = new ArrayList<>();
        if (bookReadersRelations != null) {
            for (BookReaderRelation relation : bookReadersRelations) {
                if (relation.reader != null)
                    readers.add(relation.reader);
            }
        }
        return readers;
    }

    public List<BookFile> getFiles() {
        if (files == null)
            return new ArrayList<>();
        files.sort((o1, o2) -> Integer.compare(o1.getIndex(), o2.getIndex()));
        return files;
    }

    public void setFiles(List<BookFile> files) {
        this.files = files;
    }

    public static class BookAuthorRelation {
        @SerializedName("authors")
        public Author author;
    }

    public static class BookReaderRelation {
        @SerializedName("readers")
        public Author reader;
    }
}