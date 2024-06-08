package com.cmx.adblocker.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.cmx.adblocker.ADBlockerService;
import com.cmx.adblocker.R;

import java.util.Objects;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";

    private ImageView imageAccessibilityPermission;
    private ImageView imagePowerPermission;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home,container,false);

        imageAccessibilityPermission = root.findViewById(R.id.image_accessibility_permission);
        imagePowerPermission = root.findViewById(R.id.image_power_permission);

        ImageButton btAccessibilityPermission = root.findViewById(R.id.button_accessibility_permission);
        ImageButton btPowerPermission = root.findViewById(R.id.button_power_permission);

        updatePermissionsStatus();
        btAccessibilityPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });

        btPowerPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatteryOptimizationSettings();
            }
        });

        return root;
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openBatteryOptimizationSettings() {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG,"Resume!!");
        updatePermissionsStatus();
    }

    private void updatePermissionsStatus() {
        // 更新辅助功能权限状态
        boolean hasAccessibilityPermission = checkAccessibilityPermission();
        imageAccessibilityPermission.setImageResource(hasAccessibilityPermission ? R.drawable.ic_right : R.drawable.ic_wrong);

        // 更新电源优化权限状态
        boolean ifPowerOptimized = checkPowerPermission();
        imagePowerPermission.setImageResource((ifPowerOptimized ? R.drawable.ic_right : R.drawable.ic_wrong));
    }

    private boolean checkAccessibilityPermission() {
        // 检查辅助功能权限逻辑
        return ADBlockerService.isServiceRunning();
    }

    private boolean checkPowerPermission() {

        // 检查电源优化逻辑
        PowerManager pm = (PowerManager) requireContext().getSystemService(Context.POWER_SERVICE);
        if(pm == null) {
            Log.e(TAG,"无法获取PM服务");
        }
//        boolean isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(requireContext().getPackageName());
//        Log.d(TAG,"电源优化权限检查: " + (isIgnoringOptimizations ? "已忽略" : "未忽略"));
        assert pm != null;
        return pm.isIgnoringBatteryOptimizations(requireContext().getPackageName());
    }
}
