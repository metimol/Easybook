package com.metimol.easybook.utils;

import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class WebViewCookieJar implements CookieJar {
    @Override
    public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        for (Cookie cookie : cookies) {
            cookieManager.setCookie(url.toString(), cookie.toString());
        }
        cookieManager.flush();
    }

    @NonNull
    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        String cookieString = CookieManager.getInstance().getCookie(url.toString());
        if (cookieString == null || cookieString.isEmpty()) {
            return Collections.emptyList();
        }
        String[] header = cookieString.split(";");
        List<Cookie> cookies = new ArrayList<>();
        for (String cookie : header) {
            Cookie c = Cookie.parse(url, cookie);
            if (c != null) {
                cookies.add(c);
            }
        }
        return cookies;
    }
}