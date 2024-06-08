package com.cmx.adblocker;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

public class ADBlockerApp extends Application {

    @SuppressLint("StaticFieldLeak")
    private static Context context;

    public ADBlockerApp() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ADBlockerApp.context = getApplicationContext();
    }

    public static Context getAppContext() {
        return context;
    }
}
