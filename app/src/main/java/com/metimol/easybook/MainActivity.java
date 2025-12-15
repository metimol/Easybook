package com.metimol.easybook;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.metimol.easybook.service.PlaybackService;

public class MainActivity extends AppCompatActivity {
    public static final String APP_PREFERENCES = "MyAppPrefs";
    private MainViewModel mainViewModel;

    private boolean mBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            PlaybackService.PlaybackBinder binder = (PlaybackService.PlaybackBinder) service;
            PlaybackService mService = binder.getService();
            mBound = true;
            mainViewModel.setPlaybackService(mService);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mainViewModel.setPlaybackService(null);
        }
    };

    private final android.content.BroadcastReceiver cloudflareReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String url = intent.getStringExtra("url");
            showChallengeSolver(url);
        }
    };

    private void showChallengeSolver(String url) {
        if (isFinishing()) return;

        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(R.layout.dialog_cloudflare);

        android.webkit.WebView webView = dialog.findViewById(R.id.cloudflare_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                String cookies = android.webkit.CookieManager.getInstance().getCookie(url);
                if (cookies != null && cookies.contains("cf_clearance")) {
                    dialog.dismiss();
                }
            }
        });

        webView.loadUrl("https://izib.uk");
        dialog.show();

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(dialog::dismiss, 15000);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
        super.onCreate(savedInstanceState);
        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);

        LocalBroadcastManager.getInstance(this).registerReceiver(cloudflareReceiver, new IntentFilter("CLOUDFLARE_CHALLENGE_NEEDED"));

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.main_layout);

        var main_layout = findViewById(R.id.main_activity_screen);
        ViewCompat.setOnApplyWindowInsetsListener(main_layout, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            mainViewModel.setStatusBarHeight(systemBars.top);
            view.setPadding(
                    systemBars.left,
                    0,
                    systemBars.right,
                    systemBars.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        Intent intent = new Intent(this, PlaybackService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound) {
            unbindService(connection);
            mBound = false;
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(cloudflareReceiver);
    }

    private void applyTheme() {
        SharedPreferences sharedPreferences = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        int themeMode = sharedPreferences.getInt(SettingsFragment.THEME_KEY, SettingsFragment.THEME_AUTO);

        if (themeMode == SettingsFragment.THEME_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == SettingsFragment.THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static int dpToPx(float dp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}