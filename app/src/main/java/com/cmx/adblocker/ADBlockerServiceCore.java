package com.cmx.adblocker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ADBlockerServiceCore {

    private static final String TAG = "ADBlockerServiceCore";
    private static final String selfPkgName = "广告助手";

    private final AccessibilityService service;

    private Settings mSetting;
    private UsrPresentReceiver usrPresentReceiver;
    private PkgChangeReceiver pkgChangeReceiver;
    public Handler recvHandler;


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
            Log.i(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()));
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
                    if(skipAdByWord) {
                        Log.e(TAG, "skipAdByWord");
                        final AccessibilityNodeInfo root = service.getRootInActiveWindow();
                        taskExecutor.execute(() -> iterateNodesToSkipAd(root));
                    }
                    if(skipAdByPos) {
                        Log.e(TAG, "skipAdByPos");
                        skipAdByPos = false;
                        PkgPosDescription pkgPosDescription = mapPkgPos.get(curPkgName);
                        if(pkgPosDescription != null) {
                            final WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
                            final DisplayMetrics metrics = new DisplayMetrics();
                            wm.getDefaultDisplay().getMetrics(metrics);
                            final int x = pkgPosDescription.x;
                            final int y = pkgPosDescription.y;
                            final int duration = PACKAGE_POSITION_CLICK_FIRST_DELAY;
                            taskExecutor.execute(() -> {
                                click_simulate(x, y, duration);
                                for (int i = 0; i < PACKAGE_POSITION_CLICK_RETRY; i++) {
                                    try {
                                        Thread.sleep(PACKAGE_POSITION_CLICK_RETRY_INTERVAL);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    click_simulate(x, y, duration);
                                }
                            });
                            showToastInIntentService("正在根据位置跳过广告...");
                        }
                    }
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if(!setPackages.contains(tmpPkgName)) {
                        break;
                    }
                    if(skipAdByPos) {
                        PkgPosDescription pkgPosDescription = mapPkgPos.get(curPkgName);
                        if(pkgPosDescription != null) {
                            showToastInIntentService("正在根据位置跳过广告...");
                            final WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
                            final DisplayMetrics metrics = new DisplayMetrics();
                            wm.getDefaultDisplay().getMetrics(metrics);
                            final int x = pkgPosDescription.x;
                            final int y = pkgPosDescription.y;
                            final int duration = PACKAGE_POSITION_CLICK_FIRST_DELAY;
                            taskExecutor.execute(() -> {
                                click_simulate(x, y, duration);
                                for (int i = 0; i < PACKAGE_POSITION_CLICK_RETRY; i++) {
                                    try {
                                        Thread.sleep(PACKAGE_POSITION_CLICK_RETRY_INTERVAL);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    click_simulate(x, y, duration);
                                }
                            });
                        }
                    }
                    if(skipAdByWord) {
                        final AccessibilityNodeInfo root = event.getSource();
                        taskExecutor.execute(() -> iterateNodesToSkipAd(root));
                    }
                    break;
                default:
                    Log.i(TAG, AccessibilityEvent.eventTypeToString(event.getEventType()));
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    private void showDialog() {
        if(isShow) {
            return;
        }

        final WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);

        boolean isPortrait = metrics.widthPixels < metrics.heightPixels;
        final int width = isPortrait ? metrics.widthPixels : metrics.heightPixels;
        final int height = isPortrait ? metrics.heightPixels : metrics.widthPixels;

        final PkgPosDescription posDescription = new PkgPosDescription();

        final LayoutInflater inflater = LayoutInflater.from(service);
        final View view = inflater.inflate(R.layout.activity_customization, null);
        final TextView tvPkgName = view.findViewById(R.id.tv_package_name);
        final TextView tvActName = view.findViewById(R.id.tv_activity_name);
        final TextView tvPosInfo = view.findViewById(R.id.tv_position_info);
        Button btQuit = view.findViewById(R.id.button_quit);
        final Button btAddPos = view.findViewById(R.id.button_add_position);
        Button btShowTarget = view.findViewById(R.id.button_show_target);

        final ImageView ivTarget = new ImageView(service);
        ivTarget.setImageResource(R.drawable.ic_target);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.TOP | Gravity.START;
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.width = width;
        params.height = height / 5;
        params.x = metrics.widthPixels - params.width;
        params.y = metrics.heightPixels - params.height;
        params.alpha = 0.8f;


        final WindowManager.LayoutParams targetParams = new WindowManager.LayoutParams();
        targetParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        targetParams.format = PixelFormat.TRANSLUCENT;
        targetParams.gravity = Gravity.TOP | Gravity.START;
        targetParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        targetParams.width = width / 10;
        targetParams.height = width / 10;
        targetParams.x = (metrics.widthPixels - targetParams.width) / 2;
        targetParams.y = (metrics.heightPixels - targetParams.height) / 2;
        targetParams.alpha = 0f;

        view.setOnTouchListener(new View.OnTouchListener(){
            int x = 0;
            int y = 0;


            @Override
            public boolean onTouch(View v,MotionEvent event){
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        x = (int)event.getRawX();
                        y = (int)event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        params.x = Math.round(params.x + event.getRawX() - x);
                        params.y = Math.round(params.y + event.getRawY() - y);
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        wm.updateViewLayout(view,params);
                        break;
                }
                return true;
            }
        });

        ivTarget.setOnTouchListener(new View.OnTouchListener(){
            int x = 0,y = 0, width = targetParams.width / 2, height = targetParams.height / 2;
            @Override
            public boolean onTouch(View v,MotionEvent event){
                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        btAddPos.setEnabled(true);
                        targetParams.alpha = 0.9f;
                        wm.updateViewLayout(ivTarget, targetParams);
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        targetParams.x = Math.round(targetParams.x + (event.getRawX() - x));
                        targetParams.y = Math.round(targetParams.y + (event.getRawY() - y));
                        x = Math.round(event.getRawX());
                        y = Math.round(event.getRawY());
                        wm.updateViewLayout(ivTarget, targetParams);
                        posDescription.pkgName = curPkgName;
                        posDescription.actName = curActName;
                        posDescription.x = targetParams.x + width;
                        posDescription.y = targetParams.y + height;
                        tvPkgName.setText(posDescription.pkgName);
                        tvActName.setText(posDescription.actName);
                        tvPosInfo.setText("X轴：" + posDescription.x + "    " + "Y轴：" + posDescription.y );
                        break;
                case MotionEvent.ACTION_UP:
                    targetParams.alpha = 0.5f;
                    wm.updateViewLayout(ivTarget, targetParams);
                    break;
                }
                return true;
            }
        });

        btShowTarget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Button button = (Button) v;
                if (targetParams.alpha == 0) {
                    posDescription.pkgName = curPkgName;
                    posDescription.actName= curActName;
                    targetParams.alpha = 0.5f;
                    targetParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    wm.updateViewLayout(ivTarget, targetParams);
                    tvPkgName.setText(posDescription.pkgName);
                    tvActName.setText(posDescription.actName);
                    button.setText("隐藏准心");
                } else {
                    targetParams.alpha = 0f;
                    targetParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    wm.updateViewLayout(ivTarget, targetParams);
                    btAddPos.setEnabled(false);
                    button.setText("显示准心");
                }
            }
        });

        btAddPos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mapPkgPos.put(posDescription.pkgName, new PkgPosDescription(posDescription));
                btAddPos.setEnabled(false);
                tvPkgName.setText(posDescription.pkgName+ " (以下坐标数据已保存)");
                // save
                Settings.getInstance().setPkgPos(mapPkgPos);
            }
        });

        btQuit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wm.removeViewImmediate(view);
                wm.removeViewImmediate(ivTarget);
                isShow = false;
            }
        });

        wm.addView(view,params);
        wm.addView(ivTarget, targetParams);
        isShow = true;
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
                    showDialog();
                    break;
                case ADBlockerService.ACTION_STOP_SERVICE:
                    service.disableSelf();
                    break;
            }
            return true;
        });
    }

    private void iterateNodesToSkipAd(AccessibilityNodeInfo root) {
//        Log.e(TAG, "iterateNodesToSkipAd");
        ArrayList<AccessibilityNodeInfo> vis = new ArrayList<>();
        vis.add(root);
        ArrayList<AccessibilityNodeInfo> next = new ArrayList<>();

        int num = vis.size();
        int index = 0;
        AccessibilityNodeInfo node;
        boolean handled = false;
//        Log.e(TAG, "skipAdRunning: " + skipAdRunning);
//        Log.e(TAG, "num: " + num);
        while(skipAdRunning && index < num) {
            node = vis.get(index++);
            if(node == null) {
//                Log.e(TAG, "node is null");
            }
            if(node != null) {
//                Log.e(TAG, "node is not null");
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
//            Log.e(TAG, "description: " + description);
//            Log.e(TAG, "text: " + text);
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
            Log.e(TAG, "Skip by word: " + node.toString() + " " + node.getClassName().toString() + " " + node.getText() + " " + node.getContentDescription());
            String nodeDesc = describeAccessibilityNode(node);
//            Log.d(TAG, "Skip by word: " + nodeDesc);
            if(clickedWidgets.contains(nodeDesc)) {
                Log.d(TAG, "Skip by word: " + nodeDesc + " already clicked");
            }
            if (!clickedWidgets.contains(nodeDesc)) {
                clickedWidgets.add(nodeDesc);

                showToastInIntentService("正在根据关键字跳过广告...");
                boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (!clicked) {
                    Rect rect = new Rect();
                    node.getBoundsInScreen(rect);
                    click_simulate(rect.centerX(), rect.centerY(), 20);
                }
                else {
                    Log.e(TAG, "Click Failed!");
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
    private void click_simulate(int x, int y, int duration) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        service.dispatchGesture(builder.build(), null, null);
    }

    private void startSkipAdProcess() {
        skipAdRunning = true;
        skipAdByPos = mSetting.getIfSkipAdByPos();
        skipAdByWord = mSetting.getIfSkipAdByWord();
        clickedWidgets.clear();

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
            service.unregisterReceiver(pkgChangeReceiver);
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
