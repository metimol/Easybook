package com.metimol.easybook.api;
import com.metimol.easybook.api.models.response.ApiResponse;
import com.metimol.easybook.api.models.response.BookData;
import com.metimol.easybook.api.models.response.BooksWithDatesData;
import com.metimol.easybook.api.models.response.SearchData;
import com.metimol.easybook.api.models.response.SourceData;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {
    @GET("graphql/")
    Call<ApiResponse<SearchData>> searchBooks(
            @Query("query") String query,
            @Query("ru_audioknigi_app") int appId
    );

    @GET("graphql/")
    Call<ApiResponse<BookData>> getBookDetails(
            @Query("query") String query,
            @Query("ru_audioknigi_app") int appId
    );

    @GET("graphql/3/")
    Call<ApiResponse<SourceData>> getBooksBySourceSorted(
            @Query("query") String query,
            @Query("ru_audioknigi_app") int appId
    );

    @GET("graphql/3/")
    Call<ApiResponse<BooksWithDatesData>> getBooksWithDates(
            @Query("query") String query,
            @Query("ru_audioknigi_app") int appId
    );
}