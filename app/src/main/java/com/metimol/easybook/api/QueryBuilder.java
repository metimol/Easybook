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

    public static String buildBookDetailsQuery(int bookId) {
        String format = "{book(id:%1$s){id,name,urlName,genre{id,name},serie{id,name,booksCount}," +
                "serieIndex,authors{id,name,surname},readers{id,name,surname}," +
                "files{full{id,index,title,fileName,duration,url,size}," +
                "mobile{id,index,title,fileName,duration,url,size}}," +
                "defaultPoster,defaultPosterMain,totalDuration,aboutBb,likes,dislikes}}";
        return String.format(Locale.US, format, bookId);
    }

    public static String buildFilteredSortQuery(int offset, int count, String source, String id, String sort) {
        String format = "{booksBySource(offset:%1$s,count:%2$s,source:%3$s,id:%4$s,sort:%5$s)" +
                "{count,items{id,name,urlName,genre{id,name},serie{id,name,booksCount}," +
                "serieIndex,authors{id,name,surname},readers{id,name,surname},subgenres{id,name}," +
                "likes,dislikes,defaultPoster,defaultPosterMain,totalDuration,aboutBb}}}";
        return String.format(Locale.US, format, offset, count, source, id, sort);
    }

    public static final String SOURCE_GENRE = "GENRE";
    public static final String SOURCE_AUTHOR = "AUTHOR";
    public static final String SOURCE_SERIE = "SERIE";
    public static final String SOURCE_READER = "READER";

    public static final String SORT_POPULAR = "POPULAR";
    public static final String SORT_NEW = "NEW";
    public static final String SORT_LIKES = "LIKES";
}