package com.metimol.easybook.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.metimol.easybook.database.relations.BookWithChapters;

import java.util.List;

@Dao
public interface AudiobookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBook(Book book);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChapters(List<Chapter> chapters);

    @Query("UPDATE books SET isFavorite = :isFavorite WHERE id = :bookId")
    void updateFavoriteStatus(String bookId, boolean isFavorite);

    @Query("UPDATE books SET currentChapterId = :chapterId, currentTimestamp = :timestamp, lastListened = :lastListened, isFinished = :isFinished, progressPercentage = :progressPercentage WHERE id = :bookId")
    void updateBookProgress(String bookId, String chapterId, long timestamp, long lastListened, boolean isFinished, int progressPercentage);

    @Query("UPDATE books SET isDownloaded = :isDownloaded WHERE id = :bookId")
    void updateBookDownloadStatus(String bookId, boolean isDownloaded);

    @Query("UPDATE chapters SET localPath = :path WHERE id = :chapterId")
    void updateChapterPath(String chapterId, String path);

    @Query("SELECT * FROM books WHERE id = :bookId")
    Book getBookById(String bookId);

    @Query("SELECT * FROM books WHERE id = :bookId")
    LiveData<Book> getBookByIdLiveData(String bookId);

    @Query("SELECT isFavorite FROM books WHERE id = :bookId")
    LiveData<Boolean> isBookFavorite(String bookId);

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE id = :bookId)")
    boolean bookExists(String bookId);

    @Query("SELECT * FROM books WHERE isFavorite = 1")
    List<Book> getFavoriteBooksList();

    @Query("SELECT isFinished FROM books WHERE id = :bookId")
    LiveData<Boolean> isBookFinished(String bookId);

    @Query("UPDATE books SET isFinished = :isFinished, progressPercentage = :progressPercentage WHERE id = :bookId")
    void updateFinishedStatus(String bookId, boolean isFinished, int progressPercentage);

    @Query("SELECT * FROM books WHERE isFinished = 1")
    List<Book> getFinishedBooksList();

    @Query("SELECT * FROM books WHERE isFinished = 0 AND currentTimestamp > 0 ORDER BY lastListened DESC")
    List<Book> getListeningBooksList();

    @Query("SELECT * FROM books WHERE isFinished = 0 AND currentTimestamp > 0 ORDER BY lastListened DESC LIMIT 1")
    Book getLastListenedBook();

    @Query("SELECT * FROM books")
    List<Book> getAllBooksProgress();

    @Transaction
    @Query("SELECT * FROM books WHERE id = :bookId")
    BookWithChapters getBookWithChapters(String bookId);

    @Query("SELECT * FROM chapters WHERE bookOwnerId = :bookId ORDER BY chapterIndex ASC")
    List<Chapter> getChaptersForBook(String bookId);

    @Query("SELECT * FROM books WHERE isDownloaded = 1")
    List<Book> getDownloadedBooks();
}