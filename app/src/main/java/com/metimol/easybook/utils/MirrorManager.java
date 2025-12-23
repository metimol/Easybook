package com.metimol.easybook.utils;

import android.util.Log;
import com.metimol.easybook.BuildConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class MirrorManager {
    private static final String TAG = "MirrorManager";
    private static MirrorManager instance;

    private final List<String> authMirrors;
    private final List<String> dbMirrors;
    private final List<String> filesMirrors;

    private int selectedIndex = 0;

    private MirrorManager() {
        authMirrors = parseConfig(BuildConfig.YANDEX_AUTH_BACKEND_URL);
        dbMirrors = parseConfig(BuildConfig.AUDIOBOOKS_BASE_URL);
        filesMirrors = parseConfig(BuildConfig.FILES_BASE_URL);

        Log.d(TAG, "Initialized with mirrors: " +
                "Auth=" + authMirrors.size() + ", " +
                "DB=" + dbMirrors.size() + ", " +
                "Files=" + filesMirrors.size());
    }

    public static synchronized MirrorManager getInstance() {
        if (instance == null) {
            instance = new MirrorManager();
        }
        return instance;
    }

    private List<String> parseConfig(String configValue) {
        List<String> list = new ArrayList<>();
        if (configValue == null || configValue.isEmpty() || configValue.contains("placeholder")) {
            return list;
        }
        String[] parts = configValue.split(",");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.endsWith("/")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                list.add(trimmed);
            }
        }
        return list;
    }

    public void selectBestMirror() {
        boolean isInitialized = false;
        if (filesMirrors.isEmpty()) {
            selectedIndex = 0;
            isInitialized = true;
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(filesMirrors.size());
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        int[] healthStatus = new int[filesMirrors.size()];
        CountDownLatch latch = new CountDownLatch(filesMirrors.size());

        for (int i = 0; i < filesMirrors.size(); i++) {
            final int index = i;
            final String url = filesMirrors.get(i);
            executor.execute(() -> {
                boolean healthy = checkHealth(client, url);
                healthStatus[index] = healthy ? 1 : -1;
                latch.countDown();
            });
        }

        try {
            latch.await(6, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while checking mirrors", e);
        } finally {
            executor.shutdownNow();
        }

        int bestIndex = 0;
        boolean found = false;
        for (int i = 0; i < filesMirrors.size(); i++) {
            if (healthStatus[i] == 1) {
                bestIndex = i;
                found = true;
                break;
            }
        }

        if (!found) {
            Log.w(TAG, "No healthy mirrors found. Defaulting to index 0.");
            bestIndex = 0;
        }

        selectedIndex = bestIndex;
        isInitialized = true;
        Log.i(TAG, "Selected mirror index: " + selectedIndex +
                " (" + filesMirrors.get(selectedIndex) + ")");
    }

    private boolean checkHealth(OkHttpClient client, String baseUrl) {
        String healthUrl = baseUrl + "/health";
        Request request = new Request.Builder().url(healthUrl).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                try {
                    JSONObject json = new JSONObject(body);
                    return "healthy".equalsIgnoreCase(json.optString("status"));
                } catch (Exception e) {
                    return body.contains("healthy");
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Health check failed for " + healthUrl + ": " + e.getMessage());
        }
        return false;
    }

    public String getAuthUrl() {
        return getUrlAtIndex(authMirrors);
    }

    public String getDbUrl() {
        return getUrlAtIndex(dbMirrors);
    }

    public String getFilesUrl() {
        return getUrlAtIndex(filesMirrors);
    }

    private String getUrlAtIndex(List<String> list) {
        if (list.isEmpty())
            return "";
        if (selectedIndex >= 0 && selectedIndex < list.size()) {
            return list.get(selectedIndex);
        }
        if (!list.isEmpty())
            return list.get(0);
        return "";
    }
}
