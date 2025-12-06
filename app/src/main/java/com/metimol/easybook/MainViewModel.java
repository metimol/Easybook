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
import com.metimol.easybook.api.ApiService;
import com.metimol.easybook.api.QueryBuilder;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.BookFile;
import com.metimol.easybook.api.models.Serie;
import com.metimol.easybook.api.models.response.ApiResponse;
import com.metimol.easybook.api.models.response.BookData;
import com.metimol.easybook.api.models.response.BooksWithDatesData;
import com.metimol.easybook.api.models.response.SearchData;
import com.metimol.easybook.api.models.response.SeriesSearchData;
import com.metimol.easybook.api.models.response.SourceData;
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
    private enum SourceType { NONE, GENRE, SERIES, AUTHOR, READER, FAVORITES, LISTENED, LISTENING, DOWNLOADED }

    private final FirebaseRepository firebaseRepository;
    private final MutableLiveData<Integer> statusBarHeight = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Book>> books = new MutableLiveData<>();
    private final MutableLiveData<List<Serie>> series = new MutableLiveData<>();
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
            int bookId = Integer.parseInt(dbBook.id);
            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            String query = QueryBuilder.buildBookDetailsQuery(bookId);
            Call<ApiResponse<BookData>> call = apiService.getBookDetails(query, 1);
            Response<ApiResponse<BookData>> response = call.execute();

            if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                Book apiBook = response.body().getData().getBook();
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

        List<Chapter> dbChapters = audiobookDao.getChaptersForBook(dbBook.id);
        List<BookFile> apiChapters = new ArrayList<>();

        for (Chapter ch : dbChapters) {
            BookFile bf = new BookFile();
            bf.setId(Integer.parseInt(ch.id));
            bf.setTitle(ch.title);
            bf.setUrl(ch.url);
            bf.setDuration((int) ch.duration);
            bf.setIndex(ch.chapterIndex);
            apiChapters.add(bf);
        }

        book.setFiles(new com.metimol.easybook.api.models.BookFiles());
        book.getFiles().setFull(apiChapters);

        return book;
    }

    private void preparePlayerWithBook(Book apiBook, com.metimol.easybook.database.Book dbBook, PlaybackService service) {
        if (apiBook != null && apiBook.getFiles() != null && apiBook.getFiles().getFull() != null) {
            int chapterIndex = 0;
            for (int i = 0; i < apiBook.getFiles().getFull().size(); i++) {
                if (String.valueOf(apiBook.getFiles().getFull().get(i).getId()).equals(dbBook.currentChapterId)) {
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
        if (apiBook == null) return;

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
        if (apiBook == null) return;

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
        if(apiBook.getAuthors() != null && !apiBook.getAuthors().isEmpty()) {
            dbBook.author = apiBook.getAuthors().get(0).getName() + " " + apiBook.getAuthors().get(0).getSurname();
        }
        dbBook.coverUrl = apiBook.getDefaultPosterMain();
        dbBook.totalDuration = apiBook.getTotalDuration();
        return dbBook;
    }

    public void fetchListeningBooksFromApi() {
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
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
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
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
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
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
        if (Boolean.TRUE.equals(isLoading.getValue())) return;
        isLoading.setValue(true);
        loadError.setValue(false);
        clearBookList();
        isLastPage = true;
        isSearchActive = false;
        currentSourceType = SourceType.DOWNLOADED;

        databaseExecutor.execute(() -> {
            List<com.metimol.easybook.database.Book> downloaded = audiobookDao.getDownloadedBooks();
            List<Book> result = new ArrayList<>();
            for(com.metimol.easybook.database.Book db : downloaded) {
                Book b = convertDbBookToApiBook(db);
                b.setProgressPercentage(db.progressPercentage);
                result.add(b);
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
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        for (com.metimol.easybook.database.Book dbBook : dbBooks) {
            try {
                String query = QueryBuilder.buildBookDetailsQuery(Integer.parseInt(dbBook.id));
                Call<ApiResponse<BookData>> call = apiService.getBookDetails(query, 1);
                Response<ApiResponse<BookData>> response = call.execute();

                if (response.isSuccessful() && response.body() != null && response.body().getData() != null && response.body().getData().getBook() != null) {
                    Book apiBook = response.body().getData().getBook();
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
        List<Category> categoryList = new ArrayList<>();
        String[] categoryNames = getApplication().getResources().getStringArray(R.array.category_names);
        int[] categoryIcons = {
                R.drawable.ic_category_fantasy,
                R.drawable.ic_category_thriller,
                R.drawable.ic_category_drama,
                R.drawable.ic_category_business,
                R.drawable.ic_category_biography,
                R.drawable.ic_category_children,
                R.drawable.ic_category_history,
                R.drawable.ic_category_classic,
                R.drawable.ic_category_health,
                R.drawable.ic_globe,
                R.drawable.ic_category_nonfiction,
                R.drawable.ic_category_education,
                R.drawable.ic_category_poetry,
                R.drawable.ic_category_adventure,
                R.drawable.ic_category_psychology,
                R.drawable.ic_category_miscellaneous,
                R.drawable.ic_category_ranobe,
                R.drawable.ic_category_religion,
                R.drawable.ic_category_novel,
                R.drawable.ic_category_crime,
                R.drawable.ic_category_horror,
                R.drawable.ic_category_esoteric,
                R.drawable.ic_category_humor
        };
        String[] categoryIds = {"1", "4", "7", "14", "22", "6", "12", "13", "21", "15", "11", "17", "18", "8", "2", "16", "20", "19", "3", "24", "10", "5", "9"};

        for (int i = 0; i < categoryNames.length; i++) {
            categoryList.add(new Category(categoryIds[i], categoryNames[i], categoryIcons[i]));
        }

        categories.setValue(categoryList);
    }

    public void clearBookList() {
        books.setValue(new ArrayList<>());
        series.setValue(new ArrayList<>());
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

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String query;

        if (currentSourceType == SourceType.GENRE) {
            query = QueryBuilder.buildBooksByGenreQuery(currentSourceId, currentPage * 60, 60, QueryBuilder.SORT_NEW);
        } else if (currentSourceType == SourceType.SERIES) {
            query = QueryBuilder.buildBooksBySeriesQuery(currentSourceId, currentPage * 60, 60, QueryBuilder.SORT_NEW);
        } else if (currentSourceType == SourceType.AUTHOR) {
            query = QueryBuilder.buildBooksByAuthorQuery(currentSourceId, currentPage * 60, 60, QueryBuilder.SORT_NEW);
        } else if (currentSourceType == SourceType.READER) {
            query = QueryBuilder.buildBooksByReaderQuery(currentSourceId, currentPage * 60, 60, QueryBuilder.SORT_NEW);
        } else {
            query = QueryBuilder.buildBooksWithDatesQuery(currentPage * 60, 60, QueryBuilder.SORT_NEW);
            Call<ApiResponse<BooksWithDatesData>> call = apiService.getBooksWithDates(query, 1);
            call.enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<BooksWithDatesData>> call, @NonNull Response<ApiResponse<BooksWithDatesData>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                        loadError.setValue(false);

                        List<Book> newBooks = new ArrayList<>();
                        BooksWithDatesData data = response.body().getData();
                        if (data.getBooksWithDates() != null && data.getBooksWithDates().getItems() != null) {
                            data.getBooksWithDates().getItems().forEach(bookWithDate -> {
                                if (bookWithDate != null && bookWithDate.getData() != null) {
                                    newBooks.addAll(bookWithDate.getData());
                                }
                            });
                        }

                        handleBookResponse(newBooks);
                    } else {
                        handleLoadError();
                    }
                    isLoading.setValue(false);
                }

                @Override
                public void onFailure(@NonNull Call<ApiResponse<BooksWithDatesData>> call, @NonNull Throwable t) {
                    handleLoadError();
                    isLoading.setValue(false);
                }
            });
            return;
        }

        Call<ApiResponse<SourceData>> call = apiService.getBooksBySourceSorted(query, 1);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<SourceData>> call, @NonNull Response<ApiResponse<SourceData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    loadError.setValue(false);

                    List<Book> newBooks = new ArrayList<>();
                    SourceData data = response.body().getData();

                    if (data.getBookListResponse() != null && data.getBookListResponse().getItems() != null) {
                        newBooks.addAll(data.getBookListResponse().getItems());
                    }

                    handleBookResponse(newBooks);
                } else {
                    handleLoadError();
                }
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<SourceData>> call, @NonNull Throwable t) {
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

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        AtomicInteger runningRequests = new AtomicInteger(2);
        AtomicBoolean anyRequestFailed = new AtomicBoolean(false);

        String booksQuery = QueryBuilder.buildSearchQuery(0, 50, query);
        Call<ApiResponse<SearchData>> booksCall = apiService.searchBooks(booksQuery, 1);
        booksCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<SearchData>> call, @NonNull Response<ApiResponse<SearchData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    SearchData data = response.body().getData();
                    List<Book> searchResults = new ArrayList<>();
                    if (data.getBookListResponse() != null && data.getBookListResponse().getItems() != null) {
                        searchResults.addAll(data.getBookListResponse().getItems());
                    }

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
            public void onFailure(@NonNull Call<ApiResponse<SearchData>> call, @NonNull Throwable t) {
                anyRequestFailed.set(true);
                books.setValue(new ArrayList<>());
                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(true);
                }
            }
        });

        String seriesQuery = QueryBuilder.buildSeriesSearchQuery(0, 5, query);
        Call<ApiResponse<SeriesSearchData>> seriesCall = apiService.searchSeries(seriesQuery, 1);
        seriesCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<SeriesSearchData>> call, @NonNull Response<ApiResponse<SeriesSearchData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    SeriesSearchData data = response.body().getData();
                    List<Serie> searchSeriesResults = new ArrayList<>();
                    if (data.getSeriesListResponse() != null && data.getSeriesListResponse().getItems() != null) {
                        searchSeriesResults.addAll(data.getSeriesListResponse().getItems());
                    }
                    series.setValue(searchSeriesResults);
                } else {
                    series.setValue(new ArrayList<>());
                }

                if (runningRequests.decrementAndGet() == 0) {
                    onAllSearchRequestsFinished(anyRequestFailed.get());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<SeriesSearchData>> call, @NonNull Throwable t) {
                series.setValue(new ArrayList<>());
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
            if(dbBook != null && dbBook.isDownloaded) {
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
        int id;
        try {
            id = Integer.parseInt(bookId);
        } catch (NumberFormatException e) {
            isBookLoading.postValue(false);
            bookLoadError.postValue(true);
            return;
        }

        String query = QueryBuilder.buildBookDetailsQuery(id);
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<ApiResponse<BookData>> call = apiService.getBookDetails(query, 1);

        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<BookData>> call, @NonNull Response<ApiResponse<BookData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    selectedBookDetails.setValue(response.body().getData().getBook());
                    bookLoadError.setValue(false);
                } else {
                    bookLoadError.setValue(true);
                }
                isBookLoading.setValue(false);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<BookData>> call, @NonNull Throwable t) {
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
        if (type == null) return false;
        SourceType targetType = SourceType.NONE;
        targetType = switch (type) {
            case "GENRE" -> SourceType.GENRE;
            case "SERIES" -> SourceType.SERIES;
            case "AUTHOR" -> SourceType.AUTHOR;
            case "READER" -> SourceType.READER;
            case "FAVORITES" -> SourceType.FAVORITES;
            case "LISTENED" -> SourceType.LISTENED;
            case "LISTENING" -> SourceType.LISTENING;
            case "DOWNLOADED" -> SourceType.DOWNLOADED;
            default -> targetType;
        };

        if (currentSourceType != targetType) return false;

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
                if(book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                    dbBook.author = book.getAuthors().get(0).getName() + " " + book.getAuthors().get(0).getSurname();
                }
                dbBook.coverUrl = book.getDefaultPosterMain();
                dbBook.totalDuration = book.getTotalDuration();
                audiobookDao.insertBook(dbBook);
            }

            List<Chapter> chapters = new ArrayList<>();
            if(book.getFiles() != null && book.getFiles().getFull() != null) {
                for(BookFile bf : book.getFiles().getFull()) {
                    Chapter ch = new Chapter();
                    ch.id = String.valueOf(bf.getId());
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

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.APP_PREFERENCES, Context.MODE_PRIVATE);
        boolean useAppFolder = prefs.getBoolean(SettingsFragment.DOWNLOAD_TO_APP_FOLDER_KEY, true);

        File rootDir;
        if (useAppFolder) {
            rootDir = new File(context.getExternalFilesDir(null), "EasyBook/" + book.getId());
        } else {
            rootDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EasyBook/" + book.getName());
        }

        if (!rootDir.exists()) rootDir.mkdirs();

        int totalChapters = chapters.size();
        AtomicInteger downloaded = new AtomicInteger(0);

        for (Chapter chapter : chapters) {
            String fileName = chapter.title.replaceAll("[\\\\/:*?\"<>|]", "_") + ".mp3";
            File targetFile = new File(rootDir, fileName);

            if(targetFile.exists()) {
                audiobookDao.updateChapterPath(chapter.id, targetFile.getAbsolutePath());
                int count = downloaded.incrementAndGet();
                downloadProgress.postValue((count * 100) / totalChapters);
                if(count == totalChapters) {
                    audiobookDao.updateBookDownloadStatus(book.getId(), true);
                    isDownloading.postValue(false);
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, R.string.download_complete, Toast.LENGTH_SHORT).show());
                }
                continue;
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(chapter.url));
            request.setTitle(book.getName());
            request.setDescription(chapter.title);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);

            if (useAppFolder) {
                request.setDestinationInExternalFilesDir(context, null, "EasyBook/" + book.getId() + "/" + fileName);
            } else {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "EasyBook/" + book.getName() + "/" + fileName);
            }

            long downloadId = downloadManager.enqueue(request);

            String finalPath;
            if (useAppFolder) {
                finalPath = new File(context.getExternalFilesDir(null), "EasyBook/" + book.getId() + "/" + fileName).getAbsolutePath();
            } else {
                finalPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EasyBook/" + book.getName() + "/" + fileName).getAbsolutePath();
            }

            audiobookDao.updateChapterPath(chapter.id, finalPath);

            new Thread(() -> {
                boolean downloading = true;
                while (downloading) {
                    DownloadManager.Query q = new DownloadManager.Query();
                    q.setFilterById(downloadId);
                    android.database.Cursor cursor = downloadManager.query(q);
                    if (cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (statusIndex != -1) {
                            int status = cursor.getInt(statusIndex);
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                downloading = false;
                                int count = downloaded.incrementAndGet();
                                downloadProgress.postValue((count * 100) / totalChapters);
                                if(count == totalChapters) {
                                    audiobookDao.updateBookDownloadStatus(book.getId(), true);
                                    isDownloading.postValue(false);
                                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, R.string.download_complete, Toast.LENGTH_SHORT).show());
                                }
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                downloading = false;
                                isDownloading.postValue(false);
                            }
                        }
                    } else {
                        downloading = false;
                        isDownloading.postValue(false);
                    }
                    cursor.close();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public void deleteBook(String bookId) {
        databaseExecutor.execute(() -> {
            com.metimol.easybook.database.Book dbBook = audiobookDao.getBookById(bookId);
            if (dbBook == null) return;

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
}