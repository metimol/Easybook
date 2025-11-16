package com.metimol.easybook;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.metimol.easybook.database.AppDatabase;
import com.metimol.easybook.database.AudiobookDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.metimol.easybook.api.ApiClient;
import com.metimol.easybook.api.ApiService;
import com.metimol.easybook.api.QueryBuilder;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.Serie;
import com.metimol.easybook.api.models.response.ApiResponse;
import com.metimol.easybook.api.models.response.BookData;
import com.metimol.easybook.api.models.response.BooksWithDatesData;
import com.metimol.easybook.api.models.response.SearchData;
import com.metimol.easybook.api.models.response.SeriesSearchData;
import com.metimol.easybook.api.models.response.SourceData;
import com.metimol.easybook.service.PlaybackService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainViewModel extends AndroidViewModel {
    private enum SourceType { NONE, GENRE, SERIES, FAVORITES, LISTENED, LISTENING }

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

    private String currentSourceId = null;
    private SourceType currentSourceType = SourceType.NONE;

    private final AudiobookDao audiobookDao;
    private final ExecutorService databaseExecutor;
    private LiveData<Boolean> isBookFavorite;
    private LiveData<Boolean> isBookFinished;

    private final MutableLiveData<PlaybackService> playbackService = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlayerVisible = new MutableLiveData<>(false);

    public MainViewModel(@NonNull Application application) {
        super(application);
        audiobookDao = AppDatabase.getDatabase(application).audiobookDao();
        databaseExecutor = Executors.newSingleThreadExecutor();
    }

    public void setPlaybackService(PlaybackService service) {
        playbackService.setValue(service);
        if (service != null) {
            isPlayerVisible.setValue(service.currentBook.getValue() != null);
            service.currentBook.observeForever(book -> isPlayerVisible.postValue(book != null));
        } else {
            isPlayerVisible.setValue(false);
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

        databaseExecutor.execute(() -> {
            String bookId = apiBook.getId();
            boolean bookExists = audiobookDao.bookExists(bookId);

            if (!bookExists) {
                com.metimol.easybook.database.Book dbBook = new com.metimol.easybook.database.Book();
                dbBook.id = bookId;
                dbBook.isFavorite = true;
                dbBook.isFinished = false;
                dbBook.currentChapterId = null;
                dbBook.currentTimestamp = 0;
                dbBook.lastListened = 0;
                audiobookDao.insertBook(dbBook);
            } else {
                Boolean currentStatus = isBookFavorite.getValue();
                boolean newStatus = currentStatus == null || !currentStatus;
                audiobookDao.updateFavoriteStatus(bookId, newStatus);
            }
        });
    }

    public void toggleFinishedStatus() {
        Book apiBook = selectedBookDetails.getValue();
        if (apiBook == null) return;

        databaseExecutor.execute(() -> {
            String bookId = apiBook.getId();
            boolean bookExists = audiobookDao.bookExists(bookId);

            if (!bookExists) {
                com.metimol.easybook.database.Book dbBook = new com.metimol.easybook.database.Book();
                dbBook.id = bookId;
                dbBook.isFavorite = false;
                dbBook.isFinished = true;
                dbBook.currentChapterId = null;
                dbBook.currentTimestamp = 0;
                dbBook.lastListened = 0;
                audiobookDao.insertBook(dbBook);
            } else {
                Boolean currentStatus = isBookFinished.getValue();
                boolean newStatus = currentStatus == null || !currentStatus;
                audiobookDao.updateFinishedStatus(bookId, newStatus);
            }
        });
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
                if (listeningDbBooks == null || listeningDbBooks.isEmpty()) {
                    books.postValue(new ArrayList<>());
                    isLoading.postValue(false);
                    return;
                }

                List<Book> apiBooksToShow = new ArrayList<>();
                ApiService apiService = ApiClient.getClient().create(ApiService.class);

                for (com.metimol.easybook.database.Book dbBook : listeningDbBooks) {
                    try {
                        String query = QueryBuilder.buildBookDetailsQuery(Integer.parseInt(dbBook.id));
                        Call<ApiResponse<BookData>> call = apiService.getBookDetails(query, 1);
                        Response<ApiResponse<BookData>> response = call.execute();

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null && response.body().getData().getBook() != null) {
                            apiBooksToShow.add(response.body().getData().getBook());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                books.postValue(apiBooksToShow);
                isLoading.postValue(false);

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
                if (finishedDbBooks == null || finishedDbBooks.isEmpty()) {
                    books.postValue(new ArrayList<>());
                    isLoading.postValue(false);
                    return;
                }

                List<Book> apiBooksToShow = new ArrayList<>();
                ApiService apiService = ApiClient.getClient().create(ApiService.class);

                for (com.metimol.easybook.database.Book dbBook : finishedDbBooks) {
                    try {
                        String query = QueryBuilder.buildBookDetailsQuery(Integer.parseInt(dbBook.id));
                        Call<ApiResponse<BookData>> call = apiService.getBookDetails(query, 1);
                        Response<ApiResponse<BookData>> response = call.execute();

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null && response.body().getData().getBook() != null) {
                            apiBooksToShow.add(response.body().getData().getBook());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                books.postValue(apiBooksToShow);
                isLoading.postValue(false);

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
                if (favoriteDbBooks == null || favoriteDbBooks.isEmpty()) {
                    books.postValue(new ArrayList<>());
                    isLoading.postValue(false);
                    return;
                }

                List<Book> apiBooksToShow = new ArrayList<>();
                ApiService apiService = ApiClient.getClient().create(ApiService.class);

                for (com.metimol.easybook.database.Book dbBook : favoriteDbBooks) {
                    try {
                        String query = QueryBuilder.buildBookDetailsQuery(Integer.parseInt(dbBook.id));
                        Call<ApiResponse<BookData>> call = apiService.getBookDetails(query, 1);
                        Response<ApiResponse<BookData>> response = call.execute();

                        if (response.isSuccessful() && response.body() != null && response.body().getData() != null && response.body().getData().getBook() != null) {
                            apiBooksToShow.add(response.body().getData().getBook());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                books.postValue(apiBooksToShow);
                isLoading.postValue(false);

            } catch (Exception e) {
                e.printStackTrace();
                books.postValue(new ArrayList<>());
                loadError.postValue(true);
                isLoading.postValue(false);
            }
        });
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

    public void searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            if (isSearchActive) {
                resetBookList();
            }
            return;
        }

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
                    books.setValue(searchResults);
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

        int id;
        try {
            id = Integer.parseInt(bookId);
        } catch (NumberFormatException e) {
            isBookLoading.setValue(false);
            bookLoadError.setValue(true);
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
    }

    public void resetBookList() {
        isSearchActive = false;
        currentSourceId = null;
        currentSourceType = SourceType.NONE;
        isLoading.setValue(false);
        clearBookList();
        loadError.setValue(false);

        loadMoreBooks();
    }
}