package com.cmx.adblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PkgChangeReceiver extends BroadcastReceiver {

    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
//        Log.d(TAG, action);
        if(action.equals(Intent.ACTION_PACKAGE_ADDED) || action.equals(Intent.ACTION_PACKAGE_REMOVED)) {
            ADBlockerService.dispatchAction(ADBlockerService.ACTION_REFRESH_PACKAGE);
        }
    }
}

