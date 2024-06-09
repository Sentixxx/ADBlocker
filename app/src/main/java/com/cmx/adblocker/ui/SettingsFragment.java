package com.cmx.adblocker.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;


import java.util.HashSet;

import java.util.Map;
import java.util.Set;


import com.cmx.adblocker.ADBlockerService;
import com.cmx.adblocker.PkgPosDescription;
import com.cmx.adblocker.R;
import com.cmx.adblocker.Settings;


public class SettingsFragment extends PreferenceFragmentCompat {
    private final String TAG = getClass().getName();


    LayoutInflater inflater;

    PackageManager pkgManager;
    WindowManager winManager;

    Settings mSetting;

    MultiSelectListPreference actPos;
    Map<String, PkgPosDescription> mapActPos;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.adblocker_preference, rootKey);
        mSetting = Settings.getInstance();
        initPreferences();

        winManager = (WindowManager) requireActivity().getSystemService(Context.WINDOW_SERVICE);
        inflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        pkgManager = requireActivity().getPackageManager();
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,  ViewGroup container,  Bundle savedInstanceState) {
        View view = super.onCreateView(inflater,container,savedInstanceState);

        int rscId = getResources().getIdentifier("design_bottom_navigation_height","dimen",requireActivity().getPackageName());
        int height = 147;
        if(rscId > 0) {
            height = getResources().getDimensionPixelSize(rscId);
        }

        view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom() + height);
        return view;
    }

    private void initPreferences() {

        CheckBoxPreference notification = findPreference("skip_ad_notification");
        if (notification != null) {
            notification.setChecked(mSetting.getIfSkipAdNotification());
            notification.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    mSetting.setIfSkipAdNotification((Boolean) newValue);
                    return true;
                }
            });
        }

        CheckBoxPreference skipAdByWord = findPreference("skip_ad_by_word");
        if(skipAdByWord != null) {
            skipAdByWord.setChecked(mSetting.getIfSkipAdByWord());
            skipAdByWord.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    mSetting.setIfSkipAdByWord((Boolean) newValue);
                    return true;
                }
            });
        }

        CheckBoxPreference skipAdByPos = findPreference("skip_ad_by_pos");
        if(skipAdByPos != null) {
            skipAdByPos.setChecked(mSetting.getIfSkipAdByPos());
            skipAdByPos.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    mSetting.setIfSkipAdByPos((Boolean) newValue);
                    return true;
                }
            });
        }
        final SeekBarPreference skipAdDuration = findPreference("skip_ad_duration");
        if (skipAdDuration != null) {
            skipAdDuration.setMax(10);
            skipAdDuration.setMin(1);
            skipAdDuration.setUpdatesContinuously(true);
            skipAdDuration.setValue(mSetting.getSkipAdDuration());
            skipAdDuration.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    mSetting.setSkipAdDuration((int) newValue);
                    return true;
                }
            });

            EditTextPreference keyWords = findPreference("setting_key_words");
            if (keyWords != null) {
                keyWords.setOnPreferenceClickListener(preference -> {
                    // 当用户点击 EditTextPreference 时显示软键盘
                    showKeyboard();
                    return true;
                });
                keyWords.setOnBindEditTextListener(editText -> {
                    // 当对话框中的 EditText 被绑定时，显示软键盘
                    editText.requestFocus();
                    showKeyboard();
                });
                keyWords.setOnPreferenceChangeListener((preference, newValue) -> {
                    // 当用户完成编辑时，隐藏软键盘
                    hideKeyboard();
                    return true;
                });
                keyWords.setText(mSetting.getKeyWordsAsString());
                keyWords.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                        mSetting.setKeyWord(newValue.toString());
                        //通知服务刷新关键词
                        Log.e(TAG,"onPreferenceChange: ACTION_REFRESH_KEYWORDS");
                        ADBlockerService.dispatchAction(ADBlockerService.ACTION_REFRESH_KEYWORDS);
                        return true;
                    }
                });

            }


            Preference activity_customization = findPreference("setting_activity_customization");
            if(activity_customization != null) {
                activity_customization.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(@NonNull Preference preference) {
                        if(!ADBlockerService.dispatchAction(ADBlockerService.ACTION_ACTIVITY_CUSTOMIZATION)) {
                            Toast.makeText(getContext(),"广告助手服务未运行，请打开无障碍服务!", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                });
            }

            actPos = (MultiSelectListPreference) findPreference("setting_activity_positions");
            mapActPos = Settings.getInstance().getPkgPos();
            updateMultiSelectListPreferenceEntries(actPos, mapActPos.keySet());
            actPos.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    HashSet<String> results = (HashSet<String>) newValue;
                    Set<String> keys = new HashSet<>(mapActPos.keySet());
                    for(String key: keys){
                        if(!results.contains(key)) {
                            mapActPos.remove(key);
                        }
                    }
                    Settings.getInstance().setPkgPos(mapActPos);

                    // refresh MultiSelectListPreference
                    updateMultiSelectListPreferenceEntries(actPos, mapActPos.keySet());

                    ADBlockerService.dispatchAction(ADBlockerService.ACTION_REFRESH_PACKAGE);
                    return true;
                }
            });
        }



    }
    void updateMultiSelectListPreferenceEntries(MultiSelectListPreference preference, Set<String> keys){
        if(preference == null || keys == null)
            return;
        CharSequence[] entries = keys.toArray(new CharSequence[keys.size()]);
        preference.setEntries(entries);
        preference.setEntryValues(entries);
        preference.setValues(keys);
    }


    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getActivity().getCurrentFocus();
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        mapActPos = Settings.getInstance().getPkgPos();
        updateMultiSelectListPreferenceEntries(actPos, mapActPos.keySet());
    }
}
