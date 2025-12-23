package com.metimol.easybook.api;

import android.content.Context;
import com.metimol.easybook.BuildConfig;
import com.metimol.easybook.utils.MirrorManager;

import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String DATABASE_KEY = BuildConfig.AUDIOBOOKS_ANON_KEY;

    private static Retrofit retrofit = null;
    private static OkHttpClient okHttpClient = null;
    private static OkHttpClient fileOkHttpClient = null;

    public static synchronized OkHttpClient getOkHttpClient(Context context) {
        if (okHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);

            builder.addInterceptor(chain -> {
                MirrorManager.getInstance().ensureInitialized();

                Request original = chain.request();
                HttpUrl originalHttpUrl = original.url();

                String currentMirrorUrl = MirrorManager.getInstance().getDbUrl();
                HttpUrl newBaseUrl = null;

                if (currentMirrorUrl != null && !currentMirrorUrl.isEmpty()) {
                    newBaseUrl = HttpUrl.parse(currentMirrorUrl);
                }

                Request.Builder requestBuilder = original.newBuilder();

                if (newBaseUrl != null) {
                    HttpUrl newUrl = originalHttpUrl.newBuilder()
                            .scheme(newBaseUrl.scheme())
                            .host(newBaseUrl.host())
                            .port(newBaseUrl.port())
                            .build();

                    requestBuilder.url(newUrl);

                    if (newUrl.toString().startsWith(currentMirrorUrl)) {
                        requestBuilder
                                .header("apikey", DATABASE_KEY)
                                .header("Authorization", "Bearer " + DATABASE_KEY)
                                .header("Prefer", "count=exact");
                    }
                } else {
                    if (!currentMirrorUrl.isEmpty() && originalHttpUrl.toString().startsWith(currentMirrorUrl)) {
                        requestBuilder
                                .header("apikey", DATABASE_KEY)
                                .header("Authorization", "Bearer " + DATABASE_KEY)
                                .header("Prefer", "count=exact");
                    }
                }

                return chain.proceed(requestBuilder.build());
            });

            okHttpClient = builder.build();
        }
        return okHttpClient;
    }

    public static synchronized OkHttpClient getFileOkHttpClient(Context context) {
        if (fileOkHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);

            builder.addInterceptor(chain -> {
                MirrorManager.getInstance().ensureInitialized();
                return chain.proceed(chain.request());
            });

            fileOkHttpClient = builder.build();
        }
        return fileOkHttpClient;
    }

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            String baseUrl = MirrorManager.getInstance().getDbUrl();
            if (baseUrl.isEmpty()) {
                baseUrl = BuildConfig.AUDIOBOOKS_BASE_URL;
            }
            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(getOkHttpClient(context))
                    .build();
        }
        return retrofit;
    }
}