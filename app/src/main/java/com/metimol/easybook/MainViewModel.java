package com.metimol.easybook;

import android.app.Application;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.metimol.easybook.api.ApiClient;
import com.metimol.easybook.api.SupabaseService;
import com.metimol.easybook.api.models.Author;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.BookFile;
import com.metimol.easybook.api.models.BookRelation;
import com.metimol.easybook.api.models.Genre;
import com.metimol.easybook.api.models.Serie;
import com.metimol.easybook.database.AppDatabase;
import com.metimol.easybook.database.AudiobookDao;
import com.metimol.easybook.database.Chapter;
import com.metimol.easybook.firebase.FirebaseRepository;
import com.metimol.easybook.service.PlaybackService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainViewModel extends AndroidViewModel {
    private enum SourceType {
        NONE, GENRE, SERIES, AUTHOR, READER, FAVORITES, LISTENED, LISTENING, DOWNLOADED
    }

    private final FirebaseRepository firebaseRepository;
    private final MutableLiveData<Integer> statusBarHeight = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Book>> books = new MutableLiveData<>();
    private final MutableLiveData<List<Serie>> series = new MutableLiveData<>();
    private final MutableLiveData<List<Author>> authors = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadError = new MutableLiveData<>(false);
    private final MutableLiveData<Book> selectedBookDetails = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isBookLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> bookLoadError = new MutableLiveData<>(false);
    private int currentPage = 0;
    private boolean isLastPage = false;
    private boolean isSearchActive = false;
    private String currentSearchQuery;

    private String currentSourceId = null;
    private SourceType currentSourceType = SourceType.NONE;

    private final AudiobookDao audiobookDao;
    private final ExecutorService databaseExecutor;
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(4);

    private LiveData<Boolean> isBookFavorite;
    private LiveData<Boolean> isBookFinished;
    private LiveData<com.metimol.easybook.database.Book> currentDbBookLiveData;

    private final MutableLiveData<PlaybackService> playbackService = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlayerVisible = new MutableLiveData<>(false);
    private boolean hasRestoreAttempted = false;

    private final MutableLiveData<com.metimol.easybook.database.Book> currentDbBookProgress = new MutableLiveData<>();
    private final MutableLiveData<Integer> downloadProgress = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isDownloading = new MutableLiveData<>(false);

    public MainViewModel(@NonNull Application application) {
        super(application);
        audiobookDao = AppDatabase.getDatabase(application).audiobookDao();
        databaseExecutor = Executors.newSingleThreadExecutor();

        firebaseRepository = new FirebaseRepository(audiobookDao);
        firebaseRepository.startSync();
    }

    public void setPlaybackService(PlaybackService service) {
        playbackService.setValue(service);
        if (service != null) {
            isPlayerVisible.setValue(service.currentBook.getValue() != null);
            service.currentBook.observeForever(book -> isPlayerVisible.postValue(book != null));

            if (service.currentBook.getValue() == null && !hasRestoreAttempted) {
                hasRestoreAttempted = true;
                restoreLastPlayerState(service);
            }
        } else {
            isPlayerVisible.setValue(false);
        }
    }

    public void updatePlaybackSpeed(float speed) {
        PlaybackService service = playbackService.getValue();
        if (service != null) {
            service.setPlaybackSpeed(speed);
        }
    }

    public void setSleepTimer(int minutes) {
        PlaybackService service = playbackService.getValue();
        if (service != null) {
            service.setSleepTimerMinutes(minutes);
        }
    }

    public void setSleepTimerEndOfChapter() {
        PlaybackService service = playbackService.getValue();
        if (service != null) {
            service.setSleepTimerEndOfChapter();
        }
    }

    public void cancelSleepTimer() {
        PlaybackService service = playbackService.getValue();
        if (service != null) {
            service.cancelSleepTimer();
        }
    }

    private void restoreLastPlayerState(PlaybackService service) {
        databaseExecutor.execute(() -> {
            com.metimol.easybook.database.Book lastDbBook = audiobookDao.getLastListenedBook();
            if (lastDbBook != null && lastDbBook.currentChapterId != null) {
                loadBookDetailsForPlayer(lastDbBook, service);
            }
        });
    }

    private void loadBookDetailsForPlayer(com.metimol.easybook.database.Book dbBook, PlaybackService service) {
        try {
            SupabaseService apiService = ApiClient.getClient(getApplication()).create(SupabaseService.class);
            Call<List<Book>> call = apiService.getBookDetails("eq." + dbBook.id);
            Response<List<Book>> response = call.execute();

            if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                Book apiBook = response.body().get(0);
                preparePlayerWithBook(apiBook, dbBook, service);
            } else {
                restoreFromOfflineIfPossible(dbBook, service);
            }
        } catch (Exception e) {
            restoreFromOfflineIfPossible(dbBook, service);
        }
    }

    private void restoreFromOfflineIfPossible(com.metimol.easybook.database.Book dbBook, PlaybackService service) {
        if (dbBook.isDownloaded) {
            Book offlineBook = convertDbBookToApiBook(dbBook);
            preparePlayerWithBook(offlineBook, dbBook, service);
        }
    }

    private Book convertDbBookToApiBook(com.metimol.easybook.database.Book dbBook) {
        Book book = new Book();
        book.setId(dbBook.id);
        book.setName(dbBook.name);
        book.setDefaultPosterMain(dbBook.coverUrl);
        book.setDefaultPoster(dbBook.coverUrl);
        book.setTotalDuration(dbBook.totalDuration);
        book.setDescription(dbBook.description);

        if (dbBook.author != null) {
            Book.BookAuthorRelation relation = new Book.BookAuthorRelation();
            relation.author = new Author();
            relation.author.setId(dbBook.authorId);
            String[] parts = dbBook.author.split(" ", 2);
            if (parts.length > 0)
                relation.author.setName(parts[0]);
            if (parts.length > 1)
                relation.author.setSurname(parts[1]);
            List<Book.BookAuthorRelation> list = new ArrayList<>();
            list.add(relation);
            book.setBookAuthorsRelations(list);
        }

        if (dbBook.reader != null) {
            Book.BookReaderRelation relation = new Book.BookReaderRelation();
            relation.reader = new Author();
            relation.reader.setId(dbBook.readerId);
            String[] parts = dbBook.reader.split(" ", 2);
            if (parts.length > 0)
                relation.reader.setName(parts[0]);
            if (parts.length > 1)
                relation.reader.setSurname(parts[1]);
            List<Book.BookReaderRelation> list = new ArrayList<>();
            list.add(relation);
            book.setBookReadersRelations(list);
        }

        if (dbBook.genreId != null && dbBook.genreName != null) {
            Genre g = new Genre();
            g.setId(dbBook.genreId);
            g.setName(dbBook.genreName);
            book.setGenre(g);
        }

        if (dbBook.serieId != null && dbBook.serieName != null) {
            Serie s = new Serie();
            s.setId(dbBook.serieId);
            s.setName(dbBook.serieName);
            book.setSerie(s);
            book.setSerieIndex(dbBook.serieIndex);
        }

        List<Chapter> dbChapters = audiobookDao.getChaptersForBook(dbBook.id);
        List<BookFile> apiChapters = new ArrayList<>();

        for (Chapter ch : dbChapters) {
            BookFile bf = new BookFile();
            bf.setTitle(ch.title);
            bf.setUrl(ch.url);
            bf.setDuration((int) ch.duration);
            bf.setIndex(ch.chapterIndex);
            apiChapters.add(bf);
        }

        book.setFiles(apiChapters);

        return book;
    }

    private void preparePlayerWithBook(Book apiBook, com.metimol.easybook.database.Book dbBook,
                                       PlaybackService service) {
        if (apiBook != null && apiBook.getFiles() != null && !apiBook.getFiles().isEmpty()) {
            int chapterIndex = 0;
            for (int i = 0; i < apiBook.getFiles().size(); i++) {
                if (String.valueOf(apiBook.getFiles().get(i).getIndex()).equals(dbBook.currentChapterId)) {
                    chapterIndex = i;
                    break;
                }
            }
            long timestamp = dbBook.currentTimestamp;
            int finalChapterIndex = chapterIndex;
            new Handler(Looper.getMainLooper()).post(() -> {
                service.prepareBookFromProgress(apiBook, finalChapterIndex, timestamp);
            });
        }
    }

    public LiveData<PlaybackService> getPlaybackService() {
        return playbackService;
    }

    public LiveData<Boolean> getIsPlayerVisible() {
        return isPlayerVisible;
    }

    public void setStatusBarHeight(int height) {
        statusBarHeight.setValue(height);
    }

    public LiveData<Integer> getStatusBarHeight() {
        return statusBarHeight;
    }

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public LiveData<List<Book>> getBooks() {
        return books;
    }

    public LiveData<List<Serie>> getSeries() {
        return series;
    }

    public LiveData<List<Author>> getAuthors() { return authors; }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<Boolean> getLoadError() {
        return loadError;
    }

    public LiveData<Book> getSelectedBookDetails() {
        return selectedBookDetails;
    }

    public LiveData<Boolean> getIsBookLoading() {
        return isBookLoading;
    }

    public LiveData<Boolean> getBookLoadError() {
        return bookLoadError;
    }

    public boolean isGenreSearchActive() {
        return currentSourceType != SourceType.NONE;
    }

    public LiveData<com.metimol.easybook.database.Book> getBookProgress() {
        return currentDbBookProgress;
    }

    public LiveData<com.metimol.easybook.database.Book> getLiveBookProgress(String bookId) {
        return audiobookDao.getBookByIdLiveData(bookId);
    }

    public void loadBookProgress(String bookId) {
        databaseExecutor.execute(() -> {
            com.metimol.easybook.database.Book dbBook = audiobookDao.getBookById(bookId);
            currentDbBookProgress.postValue(dbBook);
        });
    }

    public void clearBookProgress() {
        currentDbBookProgress.setValue(null);
    }

    public LiveData<Boolean> getIsBookFavorite(String bookId) {
        if (isBookFavorite == null) {
            isBookFavorite = audiobookDao.isBookFavorite(bookId);
        }
        return isBookFavorite;
    }

    public LiveData<Boolean> getIsBookFinished(String bookId) {
        if (isBookFinished == null) {
            isBookFinished = audiobookDao.isBookFinished(bookId);
        }
        return isBookFinished;
    }

    public void toggleFavoriteStatus() {
        Book apiBook = selectedBookDetails.getValue();
        if (apiBook == null)
            return;

        Boolean currentStatus = isBookFavorite != null ? isBookFavorite.getValue() : null;

        databaseExecutor.execute(() -> {
            String bookId = apiBook.getId();
            boolean bookExists = audiobookDao.bookExists(bookId);
            boolean newStatus;

            if (!bookExists) {
                com.metimol.easybook.database.Book dbBook = createDbBookFromApi(apiBook);
                dbBook.isFavorite = true;
                audiobookDao.insertBook(dbBook);
                newStatus = true;
            } else {
                newStatus = currentStatus == null || !currentStatus;
                audiobookDao.updateFavoriteStatus(bookId, newStatus);
            }

            com.metimol.easybook.database.Book updatedBook = audiobookDao.getBookById(bookId);
            if (updatedBook != null) {
                firebaseRepository.updateBookInCloud(updatedBook);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (currentSourceType == SourceType.FAVORITES && !newStatus) {
                    removeBookFromList(bookId);
                }
            });
        });
    }

    public void toggleFinishedStatus() {
        Book apiBook = selectedBookDetails.getValue();
        if (apiBook == null)
            return;

        Boolean currentStatus = isBookFinished != null ? isBookFinished.getValue() : null;

        databaseExecutor.execute(() -> {
            String bookId = apiBook.getId();
            boolean bookExists = audiobookDao.bookExists(bookId);
            long now = System.currentTimeMillis();
            boolean newStatus;

            if (!bookExists) {
                com.metimol.easybook.database.Book dbBook = createDbBookFromApi(apiBook);
                dbBook.isFinished = true;
                dbBook.lastListened = now;
                dbBook.progressPercentage = 100;
                audiobookDao.insertBook(dbBook);
                newStatus = true;
            } else {
                newStatus = currentStatus == null || !currentStatus;
                audiobookDao.updateFinishedStatus(bookId, newStatus, newStatus ? 100 : 0);
                if (newStatus) {
                    audiobookDao.updateBookProgress(bookId, null, 0, now, true, 100);
                } else {
                    audiobookDao.updateBookProgress(bookId, null, 0, now, false, 0);
                }
            }

            com.metimol.easybook.database.Book updatedBook = audiobookDao.getBookById(bookId);
            if (updatedBook != null) {
                firebaseRepository.updateBookInCloud(updatedBook);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (currentSourceType == SourceType.LISTENED && !newStatus) {
                    removeBookFromList(bookId);
                } else if (currentSourceType == SourceType.LISTENING && newStatus) {
                    removeBookFromList(bookId);
                }
            });
        });
    }

    private com.metimol.easybook.database.Book createDbBookFromApi(Book apiBook) {
        com.metimol.easybook.database.Book dbBook = new com.metimol.easybook.database.Book();
        dbBook.id = apiBook.getId();
        dbBook.name = apiBook.getName();
        if (apiBook.getAuthors() != null && !apiBook.getAuthors().isEmpty()) {
            dbBook.author = apiBook.getAuthors().get(0).getName() + " " + apiBook.getAuthors().get(0).getSurname();
            dbBook.authorId = apiBook.getAuthors().get(0).getId();
        }
        if (apiBook.getReaders() != null && !apiBook.getReaders().isEmpty()) {
            dbBook.reader = apiBook.getReaders().get(0).getName() + " " + apiBook.getReaders().get(0).getSurname();
            dbBook.readerId = apiBook.getReaders().get(0).getId();
        }
        dbBook.coverUrl = apiBook.getDefaultPosterMain();
        dbBook.totalDuration = apiBook.getTotalDuration();
        dbBook.description = apiBook.getDescription();

        if (apiBook.getGenre() != null) {
            dbBook.genreName = apiBook.getGenre().getName();
            dbBook.genreId = apiBook.getGenre().getId();
        }

        if (apiBook.getSerie() != null) {
            dbBook.serieName = apiBook.getSerie().getName();
            dbBook.serieId = apiBook.getSerie().getId();
            dbBook.serieIndex = apiBook.getSerieIndex();
        }

        return dbBook;
    }

    public void fetchListeningBooksFromApi() {
        if (Boolean.TRUE.equals(isLoading.getValue()))
            return;
        isLoading.setValue(true);
        loadError.setValue(false);
        clearBookList();
        isLastPage = true;
        isSearchActive = false;
        currentSourceType = SourceType.LISTENING;

        databaseExecutor.execute(() -> {
            try {
                List<com.metimol.easybook.database.Book> listeningDbBooks = audiobookDao.getListeningBooksList();
                processDbBooksList(listeningDbBooks);
            } catch (Exception e) {
                e.printStackTrace();
                books.postValue(new ArrayList<>());
                loadError.postValue(true);
                isLoading.postValue(false);
            }
        });
    }

    public void fetchListenedBooksFromApi() {
        if (Boolean.TRUE.equals(isLoading.getValue()))
            return;
        isLoading.setValue(true);
        loadError.setValue(false);
        clearBookList();
        isLastPage = true;
        isSearchActive = false;
        currentSourceType = SourceType.LISTENED;

        databaseExecutor.execute(() -> {
            try {
                List<com.metimol.easybook.database.Book> finishedDbBooks = audiobookDao.getFinishedBooksList();
                processDbBooksList(finishedDbBooks);
            } catch (Exception e) {
                e.printStackTrace();
                books.postValue(new ArrayList<>());
                loadError.postValue(true);
                isLoading.postValue(false);
            }
        });
    }

    public void fetchFavoriteBooksFromApi() {
        if (Boolean.TRUE.equals(isLoading.getValue()))
            return;
        isLoading.setValue(true);
        loadError.setValue(false);
        clearBookList();
        isLastPage = true;
        isSearchActive = false;
        currentSourceType = SourceType.FAVORITES;

        databaseExecutor.execute(() -> {
            try {
                List<com.metimol.easybook.database.Book> favoriteDbBooks = audiobookDao.getFavoriteBooksList();
                processDbBooksList(favoriteDbBooks);
            } catch (Exception e) {
                e.printStackTrace();
                books.postValue(new ArrayList<>());
                loadError.postValue(true);
                isLoading.postValue(false);
            }
        });
    }

    public void fetchDownloadedBooks() {
        if (Boolean.TRUE.equals(isLoading.getValue()))
            return;
        isLoading.setValue(true);
        loadError.setValue(false);
        clearBookList();
        isLastPage = true;
        isSearchActive = false;
        currentSourceType = SourceType.DOWNLOADED;

        databaseExecutor.execute(() -> {
            List<com.metimol.easybook.database.Book> downloaded = audiobookDao.getDownloadedBooks();
            List<Book> result = new ArrayList<>();
            for (com.metimol.easybook.database.Book db : downloaded) {
                List<Chapter> chapters = audiobookDao.getChaptersForBook(db.id);
                boolean allFilesExist = true;

                if (chapters == null || chapters.isEmpty()) {
                    allFilesExist = false;
                } else {
                    for (Chapter chapter : chapters) {
                        if (chapter.localPath == null || !new File(chapter.localPath).exists()) {
                            allFilesExist = false;
                            break;
                        }
                    }
                }

                if (allFilesExist) {
                    Book b = convertDbBookToApiBook(db);
                    b.setProgressPercentage(db.progressPercentage);
                    result.add(b);
                } else {
                    audiobookDao.updateBookDownloadStatus(db.id, false);
                    if (chapters != null) {
                        for (Chapter chapter : chapters) {
                            if (chapter.localPath != null && !new File(chapter.localPath).exists()) {
                                audiobookDao.updateChapterPath(chapter.id, null);
                            }
                        }
                    }
                }
            }
            books.postValue(result);
            isLoading.postValue(false);
        });
    }

    private void processDbBooksList(List<com.metimol.easybook.database.Book> dbBooks) {
        if (dbBooks == null || dbBooks.isEmpty()) {
            books.postValue(new ArrayList<>());
            isLoading.postValue(false);
            return;
        }

        List<Book> apiBooksToShow = new ArrayList<>();
        SupabaseService apiService = ApiClient.getClient(getApplication()).create(SupabaseService.class);

        for (com.metimol.easybook.database.Book dbBook : dbBooks) {
            try {
                Call<List<Book>> call = apiService.getBookDetails("eq." + dbBook.id);
                Response<List<Book>> response = call.execute();

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Book apiBook = response.body().get(0);
                    updateBookProgress(apiBook, dbBook);
                    apiBooksToShow.add(apiBook);
                } else if (dbBook.isDownloaded) {
                    Book offline = convertDbBookToApiBook(dbBook);
                    offline.setProgressPercentage(dbBook.progressPercentage);
                    apiBooksToShow.add(offline);
                }
            } catch (Exception e) {
                if (dbBook.isDownloaded) {
                    Book offline = convertDbBookToApiBook(dbBook);
                    offline.setProgressPercentage(dbBook.progressPercentage);
                    apiBooksToShow.add(offline);
                }
            }
        }

        books.postValue(apiBooksToShow);
        isLoading.postValue(false);
    }

    public void fetchCategories() {
        SupabaseService apiService = ApiClient.getClient(getApplication()).create(SupabaseService.class);
        Call<List<Genre>> call = apiService.getGenres();
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Genre>> call, @NonNull Response<List<Genre>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Category> categoryList = new ArrayList<>();

                    for (Genre g : response.body()) {
                        int icon = getCategoryIcon(g.getId());
                        categoryList.add(new Category(g.getId(), g.getName(), icon));
                    }
                    categories.setValue(categoryList);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Genre>> call, @NonNull Throwable t) {

            }
        });
    }

    private int getCategoryIcon(String genreId) {
        return switch (genreId) {
            case "1" -> R.drawable.ic_category_fantasy;
            case "4" -> R.drawable.ic_category_thriller;
            case "7" -> R.drawable.ic_category_drama;
            case "14" -> R.drawable.ic_category_business;
            case "22" -> R.drawable.ic_category_biography;
            case "6" -> R.drawable.ic_category_children;
            case "12" -> R.drawable.ic_category_history;
            case "13" -> R.drawable.ic_category_classic;
            case "21" -> R.drawable.ic_category_health;
            case "11" -> R.drawable.ic_category_nonfiction;
            case "17" -> R.drawable.ic_category_education;
            case "18" -> R.drawable.ic_category_poetry;
            case "8" -> R.drawable.ic_category_adventure;
            case "2" -> R.drawable.ic_category_psychology;
            case "16" -> R.drawable.ic_category_miscellaneous;
            case "20" -> R.drawable.ic_category_ranobe;
            case "19" -> R.drawable.ic_category_religion;
            case "3" -> R.drawable.ic_category_novel;
            case "24" -> R.drawable.ic_category_crime;
            case "10" -> R.drawable.ic_category_horror;
            case "5" -> R.drawable.ic_category_esoteric;
            case "9" -> R.drawable.ic_category_humor;
            default -> R.drawable.ic_globe;
        };
    }

    public void clearBookList() {
        books.setValue(new ArrayList<>());
        series.setValue(new ArrayList<>());
        authors.setValue(new ArrayList<>());
        currentPage = 0;
        isLastPage = false;
    }

    public void loadMoreBooks() {
        if (Boolean.TRUE.equals(isLoading.getValue()) || isLastPage || isSearchActive) {
            return;
        }

        isLoading.setValue(true);

        if (currentPage == 0) {
            loadError.setValue(false);
        }

        SupabaseService apiService = ApiClient.getClient(getApplication()).create(SupabaseService.class);
        Call<List<Book>> call;

        AtomicInteger runningRequests = new AtomicInteger(1);
        AtomicBoolean anyRequestFailed = new AtomicBoolean(false);

        if (currentSourceType == SourceType.GENRE) {
            call = apiService.getBooksByGenre("eq." + currentSourceId, "created_at.desc", 60, currentPage * 60);
            enqueueBookCall(call, runningRequests, anyRequestFailed);
        } else if (currentSourceType == SourceType.SERIES) {
            call = apiService.getBooksBySerie("eq." + currentSourceId, "created_at.desc", 60, currentPage * 60);
            enqueueBookCall(call, runningRequests, anyRequestFailed);
        } else if (currentSourceType == SourceType.AUTHOR) {
            Call<List<BookRelation>> relationsCall = apiService.getAuthorBookIds("eq." + currentSourceId, 60,
                    currentPage * 60);
            relationsCall.enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<List<BookRelation>> call,
                                       @NonNull Response<List<BookRelation>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<String> ids = new ArrayList<>();
                        for (BookRelation br : response.body()) {
                            ids.add(br.getBookId());
                        }
                        fetchBooksByIds(ids, runningRequests, anyRequestFailed);
                    } else {
                        handleBookResponse(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<BookRelation>> call, @NonNull Throwable t) {
                    handleBookResponse(new ArrayList<>());
                }
            });
        } else if (currentSourceType == SourceType.READER) {
            Call<List<BookRelation>> relationsCall = apiService.getReaderBookIds("eq." + currentSourceId, 60,
                    currentPage * 60);
            relationsCall.enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<List<BookRelation>> call,
                                       @NonNull Response<List<BookRelation>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        List<String> ids = new ArrayList<>();
                        for (BookRelation br : response.body()) {
                            ids.add(br.getBookId());
                        }
                        fetchBooksByIds(ids, runningRequests, anyRequestFailed);
                    } else {
                        handleBookResponse(new ArrayList<>());
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<BookRelation>> call, @NonNull Throwable t) {
                    handleBookResponse(new ArrayList<>());
                }
            });
        } else {
            call = apiService.getBooks("created_at.desc", 60, currentPage * 60);
            enqueueBookCall(call, runningRequests, anyRequestFailed);
        }
    }

    private void fetchBooksByIds(List<String> ids, AtomicInteger runningRequests, AtomicBoolean anyRequestFailed) {
        if (ids.isEmpty()) {
            handleBookResponse(new ArrayList<>());
            return;
        }

        SupabaseService apiService = ApiClient.getClient(getApplication()).create(SupabaseService.class);
        String idsQuery = "in.(" + String.join(",", ids) + ")";
        Call<List<Book>> call = apiService.getBookDetails(idsQuery);
        enqueueBookCall(call, runningRequests, anyRequestFailed);
    }

    private void enqueueBookCall(Call<List<Book>> call, AtomicInteger runningRequests, AtomicBoolean anyRequestFailed) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Book>> call, @NonNull Response<List<Book>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    loadError.setValue(false);
                    handleBookResponse(response.body());
                } else {
                    handleLoadError();
                    isLoading.setValue(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Book>> call, @NonNull Throwable t) {
                handleLoadError();
                isLoading.setValue(false);
            }
        });
    }

    private void handleBookResponse(List<Book> newBooks) {
        databaseExecutor.execute(() -> {
            if (!newBooks.isEmpty()) {
                List<com.metimol.easybook.database.Book> dbBooks = audiobookDao.getAllBooksProgress();
                Map<String, com.metimol.easybook.database.Book> dbMap = new HashMap<>();
                for (com.metimol.easybook.database.Book dbBook : dbBooks) {
                    dbMap.put(dbBook.id, dbBook);
                }

                for (Book apiBook : newBooks) {
                    if (dbMap.containsKey(apiBook.getId())) {
                        updateBookProgress(apiBook, Objects.requireNonNull(dbMap.get(apiBook.getId())));
                    }
                }
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (newBooks.isEmpty()) {
                    isLastPage = true;
                } else {
                    List<Book> currentBooks = books.getValue();
                    List<Book> updatedBooks = new ArrayList<>();
                    if (currentBooks != null) {
                        updatedBooks.addAll(currentBooks);
                    }
                    updatedBooks.addAll(newBooks);
                    books.setValue(updatedBooks);

                    currentPage++;
                }
                isLoading.setValue(false);
            });
        });
    }

    private void updateBookProgress(Book apiBook, com.metimol.easybook.database.Book dbBook) {
        if (dbBook.isFinished) {
            apiBook.setProgressPercentage(100);
        } else {
            apiBook.setProgressPercentage(dbBook.progressPercentage);
        }
    }

    private void handleLoadError() {
        if (currentPage == 0) {
            loadError.setValue(true);
        }
    }

    public void fetchBooksByGenre(String genreId) {
        currentSourceId = genreId;
        currentSourceType = SourceType.GENRE;
        isSearchActive = false;
        clearBookList();
        loadError.setValue(false);

        loadMoreBooks();
    }

    public void fetchBooksBySeries(String seriesId) {
        currentSourceId = seriesId;
        currentSourceType = SourceType.SERIES;
        isSearchActive = false;
        clearBookList();
        loadError.setValue(false);

        loadMoreBooks();
    }

    public void fetchBooksByAuthor(String authorId) {
        currentSourceId = authorId;
        currentSourceType = SourceType.AUTHOR;
        isSearchActive = false;
        clearBookList();
        loadError.setValue(false);

        loadMoreBooks();
    }

    public void fetchBooksByReader(String readerId) {
        currentSourceId = readerId;
        currentSourceType = SourceType.READER;
        isSearchActive = false;
        clearBookList();
        loadError.setValue(false);

        loadMoreBooks();
    }

    public void searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            if (isSearchActive) {
                resetBookList();
            }
            return;
        }

        if (isSearchActive && query.equals(currentSearchQuery)) {
            return;
        }
        currentSearchQuery = query;

        isSearchActive = true;
        currentSourceId = null;
        currentSourceType = SourceType.NONE;
        isLastPage = true;
        isLoading.setValue(true);
        clearBookList();
        loadError.setValue(false);

        SupabaseService apiService = ApiClient.getClient(getApplication()).create(SupabaseService.class);

        AtomicInteger runningRequests = new AtomicInteger(3);
        AtomicBoolean anyRequestFailed = new AtomicBoolean(false);

        Call<List<Book>> booksCall = apiService.searchBooks("ilike.*" + query + "*", 50);
        booksCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Book>> call, @NonNull Response<List<Book>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Book> searchResults = response.body();

                    databaseExecutor.execute(() -> {
                        List<com.metimol.easybook.database.Book> dbBooks = audiobookDao.getAllBooksProgress();
                        Map<String, com.metimol.easybook.database.Book> dbMap = new HashMap<>();
                        for (com.metimol.easybook.database.Book dbBook : dbBooks) {
                            dbMap.put(dbBook.id, dbBook);
                        }

                        for (Book apiBook : searchResults) {
                            if (dbMap.containsKey(apiBook.getId())) {
                                updateBookProgress(apiBook, Objects.requireNonNull(dbMap.get(apiBook.getId())));
                            }
                        }

                        new Handler(Looper.getMainLooper()).post(() -> books.setValue(searchResults));
                    });

                } else {
                    anyRequestFailed.set(true);
                    books.setValue(new ArrayList<>());
                }

                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(anyRequestFailed.get());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Book>> call, @NonNull Throwable t) {
                anyRequestFailed.set(true);
                books.setValue(new ArrayList<>());
                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(true);
                }
            }
        });

        Call<List<Serie>> seriesCall = apiService.searchSeries("ilike.*" + query + "*", 5);
        seriesCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Serie>> call, @NonNull Response<List<Serie>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    series.setValue(response.body());
                } else {
                    series.setValue(new ArrayList<>());
                }

                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(anyRequestFailed.get());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Serie>> call, @NonNull Throwable t) {
                series.setValue(new ArrayList<>());
                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(anyRequestFailed.get());
                }
            }
        });

        String authorQuery = "(name.ilike.*" + query + "*,surname.ilike.*" + query + "*)";
        Call<List<Author>> authorsCall = apiService.searchAuthors(authorQuery, 5);
        authorsCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Author>> call, @NonNull Response<List<Author>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    authors.setValue(response.body());
                } else {
                    authors.setValue(new ArrayList<>());
                }

                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(anyRequestFailed.get());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Author>> call, @NonNull Throwable t) {
                authors.setValue(new ArrayList<>());
                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(anyRequestFailed.get());
                }
            }
        });
    }

    private void onAllSearchRequestsFinished(boolean hasError) {
        isLoading.setValue(false);
        if (hasError) {
            loadError.setValue(true);
        } else {
            loadError.setValue(false);
        }
    }

    public void fetchBookDetails(String bookId) {
        isBookLoading.setValue(true);
        bookLoadError.setValue(false);
        selectedBookDetails.setValue(null);

        isBookFavorite = null;
        isBookFinished = null;

        databaseExecutor.execute(() -> {
            com.metimol.easybook.database.Book dbBook = audiobookDao.getBookById(bookId);
            if (dbBook != null && dbBook.isDownloaded) {
                Book offline = convertDbBookToApiBook(dbBook);
                new Handler(Looper.getMainLooper()).post(() -> {
                    selectedBookDetails.setValue(offline);
                    bookLoadError.setValue(false);
                    isBookLoading.setValue(false);
                });
            } else {
                fetchBookDetailsNetwork(bookId);
            }
        });
    }

    private void fetchBookDetailsNetwork(String bookId) {
        SupabaseService apiService = ApiClient.getClient(getApplication()).create(SupabaseService.class);
        Call<List<Book>> call = apiService.getBookDetails("eq." + bookId);

        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Book>> call, @NonNull Response<List<Book>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    selectedBookDetails.setValue(response.body().get(0));
                    bookLoadError.setValue(false);
                } else {
                    bookLoadError.setValue(true);
                }
                isBookLoading.setValue(false);
            }

            @Override
            public void onFailure(@NonNull Call<List<Book>> call, @NonNull Throwable t) {
                bookLoadError.setValue(true);
                isBookLoading.setValue(false);
            }
        });
    }

    public void clearBookDetails() {
        selectedBookDetails.setValue(null);
        bookLoadError.setValue(false);
        isBookLoading.setValue(false);
        isBookFavorite = null;
        isBookFinished = null;
        clearBookProgress();
    }

    public void resetBookList() {
        isSearchActive = false;
        currentSearchQuery = null;
        currentSourceId = null;
        currentSourceType = SourceType.NONE;
        isLoading.setValue(false);
        clearBookList();
        loadError.setValue(false);

        loadMoreBooks();
    }

    public boolean isCurrentRequest(String type, String id) {
        if (type == null)
            return false;
        SourceType targetType = SourceType.NONE;
        switch (type) {
            case "GENRE" -> targetType = SourceType.GENRE;
            case "SERIES" -> targetType = SourceType.SERIES;
            case "AUTHOR" -> targetType = SourceType.AUTHOR;
            case "READER" -> targetType = SourceType.READER;
            case "FAVORITES" -> targetType = SourceType.FAVORITES;
            case "LISTENED" -> targetType = SourceType.LISTENED;
            case "LISTENING" -> targetType = SourceType.LISTENING;
            case "DOWNLOADED" -> targetType = SourceType.DOWNLOADED;
        }

        if (currentSourceType != targetType)
            return false;

        if (id != null && currentSourceId != null) {
            return id.equals(currentSourceId);
        }
        return id == null && currentSourceId == null;
    }

    public void logout() {
        firebaseRepository.logout();

        databaseExecutor.execute(() -> {
            AppDatabase.getDatabase(getApplication()).clearAllTables();
        });
    }

    public LiveData<Boolean> getIsDownloading() {
        return isDownloading;
    }

    public LiveData<Integer> getDownloadProgress() {
        return downloadProgress;
    }

    public void downloadBook(Book book) {
        isDownloading.setValue(true);
        databaseExecutor.execute(() -> {
            boolean exists = audiobookDao.bookExists(book.getId());
            if (!exists) {
                com.metimol.easybook.database.Book dbBook = createDbBookFromApi(book);
                audiobookDao.insertBook(dbBook);
            } else {
                com.metimol.easybook.database.Book dbBook = audiobookDao.getBookById(book.getId());
                dbBook.name = book.getName();
                if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                    dbBook.author = book.getAuthors().get(0).getName() + " " + book.getAuthors().get(0).getSurname();
                    dbBook.authorId = book.getAuthors().get(0).getId();
                }
                if (book.getReaders() != null && !book.getReaders().isEmpty()) {
                    dbBook.reader = book.getReaders().get(0).getName() + " " + book.getReaders().get(0).getSurname();
                    dbBook.readerId = book.getReaders().get(0).getId();
                }
                dbBook.coverUrl = book.getDefaultPosterMain();
                dbBook.totalDuration = book.getTotalDuration();
                dbBook.description = book.getDescription();

                if (book.getGenre() != null) {
                    dbBook.genreName = book.getGenre().getName();
                    dbBook.genreId = book.getGenre().getId();
                }

                if (book.getSerie() != null) {
                    dbBook.serieName = book.getSerie().getName();
                    dbBook.serieId = book.getSerie().getId();
                    dbBook.serieIndex = book.getSerieIndex();
                }

                audiobookDao.insertBook(dbBook);
            }

            List<Chapter> chapters = new ArrayList<>();
            if (book.getFiles() != null && !book.getFiles().isEmpty()) {
                for (BookFile bf : book.getFiles()) {
                    Chapter ch = new Chapter();
                    ch.id = book.getId() + "_" + bf.getIndex();
                    ch.bookOwnerId = book.getId();
                    ch.url = bf.getUrl();
                    ch.title = bf.getTitle();
                    ch.chapterIndex = bf.getIndex();
                    ch.duration = bf.getDuration();
                    chapters.add(ch);
                }
                audiobookDao.insertChapters(chapters);
            }

            startDownloadService(book, chapters);
        });
    }

    private void startDownloadService(Book book, List<Chapter> chapters) {
        Context context = getApplication();
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        SharedPreferences prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE);
        boolean useAppFolder = prefs.getBoolean("download_to_app_folder", true);
        int namingMode = prefs.getInt(SettingsFragment.FOLDER_NAMING_KEY, SettingsFragment.FOLDER_NAMING_TITLE);

        String folderName = getFolderName(book, namingMode);

        File rootDir;
        if (useAppFolder) {
            rootDir = new File(context.getExternalFilesDir(null), "EasyBook/" + folderName);
        } else {
            rootDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "EasyBook/" + folderName);
        }
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }

        int totalChapters = chapters.size();
        AtomicInteger downloaded = new AtomicInteger(0);

        for (Chapter chapter : chapters) {
            if (chapter.localPath != null && new File(chapter.localPath).exists()) {
                int count = downloaded.incrementAndGet();
                downloadProgress.postValue((count * 100) / totalChapters);
                if (count == totalChapters) {
                    updateDownloadComplete(context, book.getId());
                }
                continue;
            }

            String safeFileName = chapter.title.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (safeFileName.length() > 128) {
                safeFileName = safeFileName.substring(0, 128);
            }
            safeFileName += ".mp3";

            File expectedFile = new File(rootDir, safeFileName);
            if (expectedFile.exists()) {
                audiobookDao.updateChapterPath(chapter.id, expectedFile.getAbsolutePath());
                int count = downloaded.incrementAndGet();
                downloadProgress.postValue((count * 100) / totalChapters);
                if (count == totalChapters) {
                    updateDownloadComplete(context, book.getId());
                }
                continue;
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(chapter.url));
            request.setTitle(book.getName());
            request.setDescription(chapter.title);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.addRequestHeader("User-Agent", ApiClient.REAL_USER_AGENT);
            request.addRequestHeader("Referer", ApiClient.REFERER);

            if (useAppFolder) {
                request.setDestinationInExternalFilesDir(context, null, "EasyBook/" + folderName + "/" + safeFileName);
            } else {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                        "EasyBook/" + folderName + "/" + safeFileName);
            }

            File destinationFile;
            if (useAppFolder) {
                destinationFile = new File(context.getExternalFilesDir(null), "EasyBook/" + folderName + "/" + safeFileName);
            } else {
                destinationFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "EasyBook/" + folderName + "/" + safeFileName);
            }

            long downloadId = downloadManager.enqueue(request);

            downloadExecutor.execute(() -> {
                boolean downloading = true;
                while (downloading) {
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(downloadId);
                    android.database.Cursor cursor = downloadManager.query(q);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            if (statusIndex != -1) {
                                int status = cursor.getInt(statusIndex);
                                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                    downloading = false;

                                    String finalPath = destinationFile.getAbsolutePath();

                                    if (!destinationFile.exists()) {
                                        int localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                                        if (localUriIndex != -1) {
                                            String localUri = cursor.getString(localUriIndex);
                                            if (localUri != null) {
                                                finalPath = Uri.parse(localUri).getPath();
                                            }
                                        }
                                    }

                                    audiobookDao.updateChapterPath(chapter.id, finalPath);

                                    int count = downloaded.incrementAndGet();
                                    downloadProgress.postValue((count * 100) / totalChapters);
                                    if (count == totalChapters) {
                                        updateDownloadComplete(context, book.getId());
                                    }
                                } else if (status == DownloadManager.STATUS_FAILED) {
                                    downloading = false;
                                    isDownloading.postValue(false);
                                }
                            }
                        }
                        cursor.close();
                    } else {
                        downloading = false;
                        isDownloading.postValue(false);
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        downloading = false;
                    }
                }
            });
        }
    }

    private void updateDownloadComplete(Context context, String bookId) {
        audiobookDao.updateBookDownloadStatus(bookId, true);
        isDownloading.postValue(false);
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, R.string.download_complete, Toast.LENGTH_SHORT).show()
        );
    }

    public void deleteBook(String bookId) {
        databaseExecutor.execute(() -> {
            com.metimol.easybook.database.Book dbBook = audiobookDao.getBookById(bookId);
            if (dbBook == null)
                return;

            List<Chapter> chapters = audiobookDao.getChaptersForBook(bookId);
            if (chapters != null) {
                for (Chapter chapter : chapters) {
                    if (chapter.localPath != null) {
                        File file = new File(chapter.localPath);
                        if (file.exists()) {
                            file.delete();
                        }
                        audiobookDao.updateChapterPath(chapter.id, null);
                    }
                }
            }

            audiobookDao.updateBookDownloadStatus(bookId, false);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (selectedBookDetails.getValue() != null && selectedBookDetails.getValue().getId().equals(bookId)) {
                    loadBookProgress(bookId);
                }

                if (currentSourceType == SourceType.DOWNLOADED) {
                    removeBookFromList(bookId);
                }
            });
        });
    }

    private void removeBookFromList(String bookId) {
        List<Book> currentList = books.getValue();
        if (currentList != null) {
            List<Book> newList = new ArrayList<>(currentList);
            boolean removed = newList.removeIf(b -> b.getId().equals(bookId));
            if (removed) {
                books.setValue(newList);
            }
        }
    }

    private String getFolderName(Book book, int namingMode) {
        String folderName = book.getName();
        String author = (book.getAuthors() != null && !book.getAuthors().isEmpty())
                ? book.getAuthors().get(0).getName() + " " + book.getAuthors().get(0).getSurname()
                : "";
        String reader = (book.getReaders() != null && !book.getReaders().isEmpty())
                ? book.getReaders().get(0).getName() + " " + book.getReaders().get(0).getSurname()
                : "";

        if (namingMode == SettingsFragment.FOLDER_NAMING_AUTHOR_TITLE) {
            if (!author.isEmpty()) {
                folderName = author + " - " + folderName;
            }
        } else if (namingMode == SettingsFragment.FOLDER_NAMING_AUTHOR_TITLE_READER) {
            StringBuilder sb = new StringBuilder();
            if (!author.isEmpty()) {
                sb.append(author).append(" - ");
            }
            sb.append(folderName);
            if (!reader.isEmpty()) {
                sb.append(" (").append(reader).append(")");
            }
            folderName = sb.toString();
        }

        folderName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (folderName.length() > 128) {
            folderName = folderName.substring(0, 128);
        }
        return folderName.trim();
    }
}