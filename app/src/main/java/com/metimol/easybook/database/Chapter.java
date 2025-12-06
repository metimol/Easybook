package com.metimol.easybook.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "chapters",
        foreignKeys = @ForeignKey(entity = Book.class,
                parentColumns = "id",
                childColumns = "bookOwnerId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = "bookOwnerId")}
)
public class Chapter {
    @PrimaryKey
    @NonNull
    public String id;

    @ColumnInfo(name = "bookOwnerId")
    @NonNull
    public String bookOwnerId;

    public String url;

    public String title;

    @ColumnInfo(name = "chapterIndex")
    public int chapterIndex;

    public long duration;

    @ColumnInfo(name = "localPath")
    public String localPath;

    @ColumnInfo(name = "downloadId")
    public long downloadId;
}