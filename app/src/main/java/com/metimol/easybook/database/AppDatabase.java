package com.metimol.easybook.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Book.class, Chapter.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AudiobookDao audiobookDao();

    private static volatile AppDatabase INSTANCE;

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN progressPercentage INTEGER NOT NULL DEFAULT 0");
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN name TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN author TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN coverUrl TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN totalDuration INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE books ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE chapters ADD COLUMN localPath TEXT");
            database.execSQL("ALTER TABLE chapters ADD COLUMN downloadId INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "audiobook_database")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}