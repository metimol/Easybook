package com.metimol.easybook.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Book.class, Chapter.class}, version = 2, exportSchema = false) // <-- DB version
public abstract class AppDatabase extends RoomDatabase {
    public abstract AudiobookDao audiobookDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "audiobook_database")
                            .fallbackToDestructiveMigration() // <-- destructive migration
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}