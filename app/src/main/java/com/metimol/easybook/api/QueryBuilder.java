package com.metimol.easybook.api;

import java.util.Locale;

public class QueryBuilder {

    private static final String BOOK_FRAGMENT_FIELDS = "{id,name,urlName,genre{id,name}," +
            "serie{id,name,booksCount},serieIndex,authors{id,name,surname}," +
            "readers{id,name,surname},likes,dislikes,defaultPoster," +
            "defaultPosterMain,totalDuration,aboutBb}";

    public static String buildSearchQuery(int offset, int count, String searchText) {
        String format = "{booksSearch(offset:%1$s,count:%2$s,q:\"%3$s\"){count,items" + BOOK_FRAGMENT_FIELDS + "}}";
        return String.format(Locale.US, format, offset, count, searchText);
    }

    public static String buildSeriesSearchQuery(int offset, int count, String searchText) {
        String format = "{seriesSearch(offset:%1$s,count:%2$s,q:\"%3$s\"){count,items{id,name,booksCount}}}";
        return String.format(Locale.US, format, offset, count, searchText);
    }

    public static String buildBookDetailsQuery(int bookId) {
        String format = "{book(id:%1$s){id,name,urlName,genre{id,name},serie{id,name,booksCount}," +
                "serieIndex,authors{id,name,surname},readers{id,name,surname}," +
                "files{full{id,index,title,fileName,duration,url,size}," +
                "mobile{id,index,title,fileName,duration,url,size}}," +
                "defaultPoster,defaultPosterMain,totalDuration,aboutBb,likes,dislikes}}";
        return String.format(Locale.US, format, bookId);
    }

    public static String buildBooksWithDatesQuery(int offset, int count, String sort) {
        String format = "{booksWithDates(offset:%1$s,count:%2$s,sort:%3$s)" +
                "{count,items{data" + BOOK_FRAGMENT_FIELDS + "}}}";
        return String.format(Locale.US, format, offset, count, sort);
    }

    public static String buildBooksByGenreQuery(String genreId, int offset, int count, String sort) {
        String format = "{booksBySource(offset:%1$d, count:%2$d, source:%3$s, id:%4$s, sort:%5$s)" +
                "{count,items" + BOOK_FRAGMENT_FIELDS + "}}";

        return String.format(Locale.US, format,
                offset,
                count,
                SOURCE_GENRE,
                genreId,
                sort
        );
    }

    public static String buildBooksBySeriesQuery(String seriesId, int offset, int count, String sort) {
        String format = "{booksBySource(offset:%1$d, count:%2$d, source:%3$s, id:%4$s, sort:%5$s)" +
                "{count,items" + BOOK_FRAGMENT_FIELDS + "}}";

        return String.format(Locale.US, format,
                offset,
                count,
                SOURCE_SERIE,
                seriesId,
                sort
        );
    }

    public static final String SOURCE_GENRE = "GENRE";
    public static final String SOURCE_SERIE = "SERIE";
    public static final String SORT_NEW = "NEW";
}