package com.metimol.easybook.api;

import android.content.Context;
import com.metimol.easybook.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String DATABASE_KEY = BuildConfig.AUDIOBOOKS_ANON_KEY;

    private static Retrofit retrofit = null;
    private static OkHttpClient okHttpClient = null;

    public static synchronized OkHttpClient getOkHttpClient(Context context) {
        if (okHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            builder.connectTimeout(30, TimeUnit.SECONDS);
            builder.readTimeout(30, TimeUnit.SECONDS);
            builder.writeTimeout(30, TimeUnit.SECONDS);

            builder.addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();
                String url = original.url().toString();
                String dbUrl = com.metimol.easybook.utils.MirrorManager.getInstance().getDbUrl();

                if (!dbUrl.isEmpty() && url.startsWith(dbUrl)) {
                    requestBuilder
                            .header("apikey", DATABASE_KEY)
                            .header("Authorization", "Bearer " + DATABASE_KEY)
                            .header("Prefer", "count=exact");
                }

                return chain.proceed(requestBuilder.build());
            });

            okHttpClient = builder.build();
        }
        return okHttpClient;
    }

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            String baseUrl = com.metimol.easybook.utils.MirrorManager.getInstance().getDbUrl();
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