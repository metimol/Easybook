package com.metimol.easybook;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;

public class EasyBookApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
    }
}