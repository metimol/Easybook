package com.metimol.easybook;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.metimol.easybook.api.ApiClient;
import com.metimol.easybook.api.ApiService;
import com.metimol.easybook.api.QueryBuilder;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.response.ApiResponse;
import com.metimol.easybook.api.models.response.BooksWithDatesData;
import com.metimol.easybook.api.models.response.SearchData;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainViewModel extends AndroidViewModel {
    private final MutableLiveData<Integer> statusBarHeight = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Book>> books = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private int currentPage = 0;
    private boolean isLastPage = false;
    private boolean isSearchActive = false;

    public MainViewModel(@NonNull Application application) {
        super(application);
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

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
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

        for (int i = 0; i < categoryNames.length; i++) {
            categoryList.add(new Category(String.valueOf(i + 1), categoryNames[i], categoryIcons[i]));
        }

        categories.setValue(categoryList);
    }

    public void fetchBooks() {
        if (Boolean.TRUE.equals(isLoading.getValue()) || isLastPage || isSearchActive) {
            return;
        }
        isLoading.setValue(true);
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String query = QueryBuilder.buildBooksWithDatesQuery(currentPage * 60, 60, "NEW");
        Call<ApiResponse<BooksWithDatesData>> call = apiService.getBooksWithDates(query, 1);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<BooksWithDatesData>> call, @NonNull Response<ApiResponse<BooksWithDatesData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Book> newBooks = new ArrayList<>();
                    BooksWithDatesData data = response.body().getData();
                    if (data != null && data.getBooksWithDates() != null && data.getBooksWithDates().getItems() != null) {
                        data.getBooksWithDates().getItems().forEach(bookWithDate -> {
                            if (bookWithDate != null && bookWithDate.getData() != null) {
                                newBooks.addAll(bookWithDate.getData());
                            }
                        });
                    }

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
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<BooksWithDatesData>> call, Throwable t) {
                isLoading.setValue(false);
            }
        });
    }

    public void searchBooks(String query) {
        if (query == null || query.trim().isEmpty()) {
            if (isSearchActive) {
                resetBookList();
            }
            return;
        }

        isSearchActive = true;
        isLastPage = true;
        isLoading.setValue(true);
        books.setValue(new ArrayList<>());

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        String graphQlQuery = QueryBuilder.buildSearchQuery(0, 50, query);

        Call<ApiResponse<SearchData>> call = apiService.searchBooks(graphQlQuery, 1);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<SearchData>> call, @NonNull Response<ApiResponse<SearchData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    List<Book> searchResults = new ArrayList<>();
                    SearchData data = response.body().getData();
                    if (data.getBookListResponse() != null && data.getBookListResponse().getItems() != null) {
                        data.getBookListResponse().getItems().forEach(bookWithDate -> {
                            if (bookWithDate != null && bookWithDate.getData() != null) {
                                searchResults.addAll(bookWithDate.getData());
                            }
                        });
                    }
                    books.setValue(searchResults);
                } else {
                    books.setValue(new ArrayList<>());
                }
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<SearchData>> call, Throwable t) {
                books.setValue(new ArrayList<>());
                isLoading.setValue(false);
            }
        });
    }

    public void resetBookList() {
        isSearchActive = false;
        isLoading.setValue(false);
        isLastPage = false;
        currentPage = 0;
        books.setValue(new ArrayList<>());
        fetchBooks();
    }
}
