package com.metimol.easybook.api;

import android.content.Context;
import com.metimol.easybook.BuildConfig;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String SUPABASE_URL = BuildConfig.AUDIOBOOKS_BASE_URL;
    private static final String SUPABASE_KEY = BuildConfig.AUDIOBOOKS_ANON_KEY;

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

                if (original.url().toString().contains("supabase")) {
                    requestBuilder
                            .header("apikey", SUPABASE_KEY)
                            .header("Authorization", "Bearer " + SUPABASE_KEY)
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
            retrofit = new Retrofit.Builder()
                    .baseUrl(SUPABASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(getOkHttpClient(context))
                    .build();
        }
        return retrofit;
    }
}