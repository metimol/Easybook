package com.metimol.easybook.database;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = { Book.class, Chapter.class }, version = 6, exportSchema = false)
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

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN description TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN genreName TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN genreId TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN serieName TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN serieId TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN serieIndex TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN reader TEXT");
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE books ADD COLUMN authorId TEXT");
            database.execSQL("ALTER TABLE books ADD COLUMN readerId TEXT");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "audiobook_database")
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}