package com.metimol.easybook.api;

import com.metimol.easybook.api.models.Author;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.BookRelation;
import com.metimol.easybook.api.models.Genre;
import com.metimol.easybook.api.models.Serie;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface SupabaseService {

    @GET("genres?select=*")
    Call<List<Genre>> getGenres();

    @GET("books?select=*,genre:genres(*),serie:series(*),book_authors(authors(*)),book_readers(readers(*))")
    Call<List<Book>> getBooks(
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset);

    @GET("books?select=*,genre:genres(*),serie:series(*),book_authors(authors(*)),book_readers(readers(*))")
    Call<List<Book>> getBooksByGenre(
            @Query("genre_id") String genreId,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset);

    @GET("books?select=*,genre:genres(*),serie:series(*),book_authors(authors(*)),book_readers(readers(*))")
    Call<List<Book>> getBooksBySerie(
            @Query("serie_id") String serieId,
            @Query("order") String order,
            @Query("limit") int limit,
            @Query("offset") int offset);

    @GET("book_authors?select=book_id")
    Call<List<BookRelation>> getAuthorBookIds(
            @Query("author_id") String authorId,
            @Query("limit") int limit,
            @Query("offset") int offset);

    @GET("book_readers?select=book_id")
    Call<List<BookRelation>> getReaderBookIds(
            @Query("reader_id") String readerId,
            @Query("limit") int limit,
            @Query("offset") int offset);

    @GET("books?select=*,genre:genres(*),serie:series(*),book_authors(authors(*)),book_readers(readers(*))")
    Call<List<Book>> getBookDetails(
            @Query("id") String id);

    @GET("books?select=*,genre:genres(*),serie:series(*),book_authors(authors(*)),book_readers(readers(*))")
    Call<List<Book>> searchBooks(
            @Query("name") String query,
            @Query("limit") int limit);

    @GET("series?select=*")
    Call<List<Serie>> searchSeries(
            @Query("name") String query,
            @Query("limit") int limit);

    @GET("authors?select=*")
    Call<List<Author>> searchAuthors(
            @Query("or") String query,
            @Query("limit") int limit);
}