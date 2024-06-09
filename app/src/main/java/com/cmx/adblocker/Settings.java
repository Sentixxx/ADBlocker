package com.cmx.adblocker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Settings {
    private final String TAG = "Settings";

    private final String PREFERENCE_NAME = "ADBlockerConfig";

    private final String SKIP_AD_NOTIFICATION = "SKIP_AD_NOTIFICATION";
    private final String SKIP_AD_DURATION = "SKIP_AD_DURATION";
    private final String KEY_WORDS_LIST = "KEY_WORDS_LIST";
    private final String PKG_POSITIONS = "PKG_POSITIONS";

    private SharedPreferences mprefer;
    private SharedPreferences.Editor meditor;
    private Gson mgson;

    private static final Settings ourInstance = new Settings();

    public static Settings getInstance() {
        return ourInstance;
    }

    private Settings() {

        initSettings();
    }


    private boolean ifSkipAdNotification;
    public boolean getIfSkipAdNotification() {
        return ifSkipAdNotification;
    }
    public void setIfSkipAdNotification(boolean ifSkipAdNotification) {
        if(this.ifSkipAdNotification != ifSkipAdNotification) {
            this.ifSkipAdNotification = ifSkipAdNotification;
            meditor.putBoolean(SKIP_AD_NOTIFICATION,ifSkipAdNotification);
            meditor.apply();
        }
    }

    private boolean ifSkipAdByWord;
    public boolean getIfSkipAdByWord() {
        return ifSkipAdByWord;
    }
    public void setIfSkipAdByWord(boolean ifSkipAdByWord) {
        if(this.ifSkipAdByWord != ifSkipAdByWord) {
            this.ifSkipAdByWord = ifSkipAdByWord;
            meditor.putBoolean(SKIP_AD_NOTIFICATION,ifSkipAdByWord);
            meditor.apply();
        }
    }

    private boolean ifSkipAdByPos;
    public boolean getIfSkipAdByPos() {
        return ifSkipAdByPos;
    }
    public void setIfSkipAdByPos(boolean ifSkipAdByPos) {
        if(this.ifSkipAdByPos != ifSkipAdByPos) {
            this.ifSkipAdByPos = ifSkipAdByPos;
            meditor.putBoolean(SKIP_AD_NOTIFICATION,ifSkipAdByPos);
            meditor.apply();
        }
    }


    private int SkipAdDuration;

    public int getSkipAdDuration() {
        return SkipAdDuration;
    }

    public void setSkipAdDuration(int duration) {
        if(this.SkipAdDuration != duration) {
            this.SkipAdDuration = duration;
            meditor.putInt(SKIP_AD_DURATION,duration);
            meditor.apply();
        }
    }

    private ArrayList<String> KeyWords;
    public ArrayList<String> getKeyWords() {
        return KeyWords;
    }

    public void setKeyWord(String text) {
        String keys[] = text.split(" ");
        KeyWords.clear();
        KeyWords.addAll(Arrays.asList(keys));
        String json = mgson.toJson(KeyWords);
        meditor.putString(KEY_WORDS_LIST, json);
        meditor.apply();
    }

    public String getKeyWordsAsString() {
        return String.join(" ", KeyWords);
    }


    private Map<String,PkgPosDescription> PkgPos;
    public Map<String,PkgPosDescription> getPkgPos() {
        return PkgPos;
    }

    public void setPkgPos(Map<String,PkgPosDescription> pos) {
        PkgPos = pos;
        String json = mgson.toJson(PkgPos);
        meditor.putString(PKG_POSITIONS,json);
        meditor.apply();
    }


    private void initSettings() {
        mprefer = ADBlockerApp.getAppContext().getSharedPreferences(PREFERENCE_NAME, Activity.MODE_PRIVATE);
        mgson = new Gson();
        meditor = mprefer.edit();
        ifSkipAdByPos = mprefer.getBoolean("skip_ad_by_pos",false);
        ifSkipAdByWord = mprefer.getBoolean("skip_ad_by_word",true);
        ifSkipAdNotification = mprefer.getBoolean(SKIP_AD_NOTIFICATION,true);
        SkipAdDuration = mprefer.getInt(SKIP_AD_DURATION,4);
        String json = mprefer.getString(KEY_WORDS_LIST,"[\"跳过\"]");
        if(json != null) {
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            KeyWords = mgson.fromJson(json,type);
        } else {
            KeyWords = new ArrayList<>();
        }

        json = mprefer.getString(PKG_POSITIONS,null);
        if (json != null) {
            Type type = new TypeToken<TreeMap<String,PkgPosDescription>>() {}.getType();
            PkgPos = mgson.fromJson(json,type);
        } else {
            PkgPos = new TreeMap<>();
        }
    }
}
