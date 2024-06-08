package com.cmx.adblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;

public class ADBlockerService extends AccessibilityService {

    private final String TAG = getClass().getName();

    private static WeakReference<ADBlockerService> serviceRef;

    public final static int ACTION_START_SKIPAD = -1;
    public final static int ACTION_STOP_SKIPAD = -2;
    public final static int ACTION_REFRESH_KEYWORDS = -3;
    public final static int ACTION_REFRESH_CUSTOMIZED_ACTIVITY = -4;
    public final static int ACTION_ACTIVITY_CUSTOMIZATION = -5;
    public final static int ACTION_STOP_SERVICE = -6;
    public final static int ACTION_REFRESH_PACKAGE = -7;

    private ADBlockerServiceCore serviceCore;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.e(TAG, "onServiceConnected");
        serviceRef = new WeakReference<>(this);
        if (serviceCore == null) {
            serviceCore = new ADBlockerServiceCore(this);
        }
        serviceCore.onServiceConnected();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (serviceCore != null) {
            serviceCore.onAccessibilityEvent(event);
        }
    }

    @Override
    public void onInterrupt() {
        if (serviceCore != null) {
            serviceCore.onInterrupt();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (serviceCore != null) {
            serviceCore.onUnbind(intent);
            serviceCore = null;
        }
        serviceRef = null;
        return super.onUnbind(intent);
    }
    public static boolean dispatchAction(int action) {
        final ADBlockerService service = serviceRef != null ? serviceRef.get() : null;
        return service != null;
    }

    public static boolean isServiceRunning() {
        return serviceRef != null && serviceRef.get() != null;
    }
}