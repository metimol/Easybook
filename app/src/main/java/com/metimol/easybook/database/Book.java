package com.metimol.easybook.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "books")
public class Book {
    @PrimaryKey
    @NonNull
    public String id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "author")
    public String author;

    @ColumnInfo(name = "coverUrl")
    public String coverUrl;

    @ColumnInfo(name = "totalDuration")
    public int totalDuration;

    @ColumnInfo(name = "isFavorite")
    public boolean isFavorite;

    @ColumnInfo(name = "currentChapterId")
    public String currentChapterId;

    @ColumnInfo(name = "currentTimestamp")
    public long currentTimestamp;

    @ColumnInfo(name = "isFinished")
    public boolean isFinished;

    @ColumnInfo(name = "lastListened")
    public long lastListened;

    @ColumnInfo(name = "progressPercentage")
    public int progressPercentage;

    @ColumnInfo(name = "isDownloaded")
    public boolean isDownloaded;
}