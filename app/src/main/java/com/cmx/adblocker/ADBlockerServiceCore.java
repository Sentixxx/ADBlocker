package com.cmx.adblocker;

import android.accessibilityservice.AccessibilityButtonController;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ADBlockerServiceCore {

    private static final String TAG = "ADBlockerServiceCore";
    private static final String selfPkgName = "广告助手";

    private final AccessibilityService service;

    private Settings mSetting;
    private UsrPresentReceiver usrPresentReceiver;
    private PkgChangeReceiver pkgChangeReceiver;
    private Handler recvHandler;


    private ScheduledExecutorService taskExecutor;
    private volatile boolean skipAdRunning,skipAdByPos,skipAdByWord;
    private PackageManager pm;
    private String curPkgName,curActName;
    private String pkgName;
    private Set<String> setPackages,setIMEApps;
    private ArrayList<String> keyWords;
    private Set<String> clickedWidgets;
    private Map<String,PkgPosDescription> mapPkgPos;

    private static final int PACKAGE_POSITION_CLICK_FIRST_DELAY = 300;
    private static final int PACKAGE_POSITION_CLICK_RETRY_INTERVAL = 500;
    private static final int PACKAGE_POSITION_CLICK_RETRY = 6;
    private boolean isShow = false;

    public ADBlockerServiceCore(AccessibilityService service) {
        this.service = service;
    }

    public void onServiceConnected() {
        try {
            curPkgName = "Random PkgName";
            curActName = "Random ActName";
            pkgName = service.getPackageName();
            mSetting = Settings.getInstance();
            keyWords = mSetting.getKeyWords();
            mapPkgPos = mSetting.getPkgPos();
            pm = service.getPackageManager();
            clickedWidgets = new HashSet<>();

            updatePackage();
            initReceiver();

            taskExecutor = Executors.newSingleThreadScheduledExecutor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onInterrupt() {
        //stopSkipAdProcess();
    }


    public void onAccessibilityEvent(AccessibilityEvent event) {
        //Log.e(TAG, "onAccessibilityEvent");
        try {
            if (event == null) {
                return;
            }
            String tmpPkgName = event.getPackageName() == null ? "" : event.getPackageName().toString();
            String tmpActName = event.getClassName() == null ? "" : event.getClassName().toString();
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    Log.e(TAG, "TYPE_WINDOW_STATE_CHANGED");
//                    Log.e(TAG, "tmpPkgName: " + tmpPkgName);
//                    Log.e(TAG, "tmpActName: " + tmpActName);
                    if(setIMEApps.contains(tmpPkgName)) {
                        Log.e(TAG, "skip IME");
                        break;
                    }
//                    Log.e(TAG, "skip by customized activity");
                    boolean isAct = !tmpActName.startsWith("android.") && !tmpActName.startsWith("androidx");
//                    Log.e(TAG, "isAct: " + isAct);
//                    Log.e(TAG, "curPkgName: " + curPkgName);
//                    Log.e(TAG, "curActName: " + curActName);
                    if(curPkgName.equals(tmpPkgName)) {
                        if(isAct && !curActName.equals(tmpActName)) {
                            //Log.e(TAG, "curActName: " + curActName);
                            curActName = tmpActName;
                            break;
                        }
                    }
                    else {
                        if(isAct) {
                            curPkgName = tmpPkgName;
                            curActName = tmpActName;
                            stopSkipAdProcess();
                            if(setPackages.contains(curPkgName)) {
//                                Log.e(TAG, "startSkipAdProcess");
                                startSkipAdProcess();
                            }
                        }
                    }

                    //skip by position
//                    Log.e(TAG, "skipAdByPos");
                    if(skipAdByWord) {
//                        Log.e(TAG, "skipAdByWord");
                        final AccessibilityNodeInfo root = service.getRootInActiveWindow();
                        taskExecutor.execute(() -> iterateNodesToSkipAd(root));
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if(!setPackages.contains(tmpPkgName)) {
                        break;
                    }

                    if(skipAdByWord) {
                        final AccessibilityNodeInfo root = event.getSource();
                        taskExecutor.execute(() -> iterateNodesToSkipAd(root));
                    }
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void initReceiver() {
        usrPresentReceiver =  new UsrPresentReceiver();
        service.registerReceiver(usrPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));

        pkgChangeReceiver = new PkgChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        service.registerReceiver(pkgChangeReceiver, filter);

        recvHandler = new Handler(Looper.getMainLooper(), msg -> {
            switch (msg.what) {
                case ADBlockerService.ACTION_REFRESH_KEYWORDS:
                    keyWords = mSetting.getKeyWords();
                    break;
                case ADBlockerService.ACTION_START_SKIPAD:
                    startSkipAdProcess();
                    break;
                case ADBlockerService.ACTION_STOP_SKIPAD:
                    stopSkipAdProcessService();
                    break;
                case ADBlockerService.ACTION_REFRESH_PACKAGE:
                    updatePackage();
                    break;
                case ADBlockerService.ACTION_REFRESH_CUSTOMIZED_ACTIVITY:
                    mapPkgPos = mSetting.getPkgPos();
                    break;
                case ADBlockerService.ACTION_ACTIVITY_CUSTOMIZATION:
                    //showDialog();
                    break;
                case ADBlockerService.ACTION_STOP_SERVICE:
                    service.disableSelf();
                    break;
            }
            return true;
        });
    }

    private void iterateNodesToSkipAd(AccessibilityNodeInfo root) {
        Log.e(TAG, "iterateNodesToSkipAd");
        ArrayList<AccessibilityNodeInfo> vis = new ArrayList<>();
        vis.add(root);
        ArrayList<AccessibilityNodeInfo> next = new ArrayList<>();

        int num = vis.size();
        int index = 0;
        AccessibilityNodeInfo node;
        boolean handled = false;
//        Log.e(TAG, "skipAdRunning: " + skipAdRunning);
        Log.e(TAG, "num: " + num);
        while(skipAdRunning && index < num) {
            node = vis.get(index++);
            if(node == null) {
                Log.e(TAG, "node is null");
            }
            if(node != null) {
                Log.e(TAG, "node is not null");
                handled = skipAdByWord(node);
                if(handled) {
                    node.recycle();
                    break;
                }
                for(int i = 0; i < node.getChildCount(); i++) {
                    next.add(node.getChild(i));
                }
                node.recycle();
            }
            if(index == num) {
                vis.clear();
                vis.addAll(next);
                next.clear();
                index = 0;
                num = vis.size();

            }
        }
        while (index < num) {
            node = vis.get(index++);
            if (node != null) node.recycle();
        }
        index = 0;
        num = next.size();
        while (index < num) {
            node = next.get(index++);
            if (node != null) node.recycle();
        }
    }

    private boolean skipAdByWord(AccessibilityNodeInfo node) {
        CharSequence description = node.getContentDescription();
        CharSequence text = node.getText();

        if (TextUtils.isEmpty(description) && TextUtils.isEmpty(text)) {
            return false;
        }
//        Log.e(TAG, "skipAdByWord");
        boolean isFound = false;
        for (String keyWord : keyWords) {
            Log.e(TAG, "keyWord: " + keyWord);
            Log.e(TAG, "description: " + description);
            Log.e(TAG, "text: " + text);
            if (text.toString().length() <= keyWord.length() + 6 && text.toString().contains(keyWord) && !text.toString().equals(pkgName)) {
                isFound = true;
            }
            if (description != null && (description.toString().length() <= keyWord.length() + 6) && description.toString().contains(keyWord)  && !description.toString().equals(pkgName)) {
                isFound = true;
            }
            if (isFound) {
                break;
            }
        }
        if (isFound) {
            String nodeDesc = describeAccessibilityNode(node);
            Log.d(TAG, "Skip by word: " + nodeDesc);
            if(clickedWidgets.contains(nodeDesc)) {
                Log.d(TAG, "Skip by word: " + nodeDesc + " already clicked");
            }
            if (!clickedWidgets.contains(nodeDesc)) {
                clickedWidgets.add(nodeDesc);

                showToastInIntentService("正在根据关键字跳过广告...");
                boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                boolean success = false;
                if (!clicked) {
                    Rect rect = new Rect();
                    node.getBoundsInScreen(rect);
                    click_simulate(rect.centerX(), rect.centerY(), 20);
                }
                return true;
            }
        }
        return false;
    }

    /*
    核心功能
    模拟点击
     */
    private boolean click_simulate(int x, int y, int duration) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        return service.dispatchGesture(builder.build(), null, null);
    }


    private void startSkipAdProcess() {
        skipAdRunning = true;
        skipAdByPos = true;
        skipAdByWord = true;

        recvHandler.removeMessages(ADBlockerService.ACTION_STOP_SKIPAD);
        recvHandler.sendEmptyMessageDelayed(ADBlockerService.ACTION_STOP_SKIPAD, mSetting.getSkipAdDuration() * 1000);
    }

    private void stopSkipAdProcess() {
        stopSkipAdProcessService();
        recvHandler.removeMessages(ADBlockerService.ACTION_STOP_SKIPAD);
    }
    private void stopSkipAdProcessService() {
        skipAdRunning = false;
        skipAdByPos = false;
        skipAdByWord = false;
    }

    public void onUnbind(Intent intent) {
        try {
            service.unregisterReceiver(usrPresentReceiver);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void showToastInIntentService(final String sText) {
        // show one toast in 5 seconds only
        if(mSetting.getIfSkipAdNotification()) {
            recvHandler.post(() -> {
                Toast toast = Toast.makeText(service, sText, Toast.LENGTH_SHORT);
                toast.show();
            });
        };
    };

    public String describeAccessibilityNode(AccessibilityNodeInfo e){
        if(e == null) {
            return "null";
        }

        String result = "Node";

        result += " class =" + e.getClassName().toString();

        final Rect rect = new Rect();
        e.getBoundsInScreen(rect);
        result += String.format(" Position=[%d, %d, %d, %d]", rect.left, rect.right, rect.top, rect.bottom);


        CharSequence id = e.getViewIdResourceName();
        if(id != null) {
            result += " ResourceId=" + id.toString();
        }

        CharSequence description = e.getContentDescription();
        if(description != null) {
            result += " Description=" + description.toString();
        }

        CharSequence text = e.getText();
        if(text != null) {
            result += " Text=" + text.toString();
        }

        return result;
    }

    private void updatePackage() {

        setPackages = new HashSet<>();
        setIMEApps = new HashSet<>();
        Set<String> setTemps = new HashSet<>();

        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ResolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            setPackages.add(e.activityInfo.packageName);
        }

        intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo e : ResolveInfoList) {
            setTemps.add(e.activityInfo.packageName);
        }

        List<InputMethodInfo> inputMethodInfoList = ((InputMethodManager) service.getSystemService(AccessibilityService.INPUT_METHOD_SERVICE)).getInputMethodList();
        for (InputMethodInfo e : inputMethodInfoList) {
            setIMEApps.add(e.getPackageName());
        }

        setTemps.add(this.pkgName);
        setTemps.add("com.android.settings");

        setPackages.removeAll(setIMEApps);
        setPackages.removeAll(setTemps);

    }



}
