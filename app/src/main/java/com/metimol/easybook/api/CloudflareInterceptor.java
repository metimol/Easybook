package com.metimol.easybook.api;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class CloudflareInterceptor implements Interceptor {
    private final Context context;

    public CloudflareInterceptor(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);

        if (response.code() == 403 || response.code() == 503) {
            response.close();

            Intent intent = new Intent("CLOUDFLARE_CHALLENGE_NEEDED");
            intent.putExtra("url", request.url().toString());
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return chain.proceed(request);
        }
        return response;
    }
}