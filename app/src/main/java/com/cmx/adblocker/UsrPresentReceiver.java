package com.cmx.adblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UsrPresentReceiver extends BroadcastReceiver {
    private final String TAG = getClass().getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_USER_PRESENT)) {
            // Sent when the user is present after device wakes up (e.g when the keyguard is gone)
            ADBlockerService.dispatchAction(ADBlockerService.ACTION_START_SKIPAD);
        }
    }
}
