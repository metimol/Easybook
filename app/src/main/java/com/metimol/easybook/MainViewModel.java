package com.metimol.easybook;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.metimol.easybook.api.ApiClient;
import com.metimol.easybook.api.ApiService;
import com.metimol.easybook.api.QueryBuilder;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.response.ApiResponse;
import com.metimol.easybook.api.models.response.BooksWithDatesData;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<Integer> statusBarHeight = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<List<Book>> books = new MutableLiveData<>();
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean isLastPage = false;

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

    public void fetchCategories() {
        List<Category> categoryList = new ArrayList<>();

        categoryList.add(new Category("1", "Fantasy", R.drawable.ic_category_fantasy));
        categoryList.add(new Category("2", "Detective", R.drawable.ic_category_detective));
        categoryList.add(new Category("3", "Business", R.drawable.ic_category_business));
        categoryList.add(new Category("4", "Audio Play", R.drawable.ic_category_audiospektakl));
        categoryList.add(new Category("5", "Biography", R.drawable.ic_category_biography));
        categoryList.add(new Category("6", "Kids", R.drawable.ic_category_kids));

        categories.setValue(categoryList);
    }

    public void fetchBooks() {
        if (isLoading || isLastPage) {
            return;
        }
        isLoading = true;
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String query = QueryBuilder.buildBooksWithDatesQuery(currentPage * 60, 60, "NEW");
        Call<ApiResponse<BooksWithDatesData>> call = apiService.getBooksWithDates(query, 1);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<BooksWithDatesData>> call, @NonNull Response<ApiResponse<BooksWithDatesData>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Book> newBooks = new ArrayList<>();
                    response.body().getData().getBooksWithDates().getItems().forEach(bookWithDate -> {
                        newBooks.addAll(bookWithDate.getData());
                    });

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
                isLoading = false;
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<BooksWithDatesData>> call, Throwable t) {
                isLoading = false;
            }
        });
    }
}
