package com.lseboard.app;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;

import com.lseboard.app.Model.HistoryPack;
import com.lseboard.app.Model.Sticker;
import com.lseboard.app.Util.SharedPrefHelper;

import static com.lseboard.app.FetchService.BROADCAST_ACTION;

public class MainActivity extends AppCompatActivity {

    Activity activity = this;
    View rootView;
    MaterialCardView cardEnableKeyboard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 啟用 Material 3 動態顏色 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        
        super.onCreate(savedInstanceState);
        
        // 檢查是否已同意使用者條款，若無則跳轉至初始設定
        if (!SharedPrefHelper.getDisclaimerStatus(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        
        rootView = findViewById(android.R.id.content);
        
        // 設定工具列
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // 我的收藏（整合頁面）
        MaterialCardView cardCollection = findViewById(R.id.cardCollection);
        cardCollection.setOnClickListener(v -> {
            startActivity(new Intent(activity, CollectionActivity.class));
        });
        
        // 啟用鍵盤
        cardEnableKeyboard = findViewById(R.id.cardEnableKeyboard);
        cardEnableKeyboard.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });
        
        // 設定按鈕 - 2026 新增
        MaterialCardView cardSettings = findViewById(R.id.cardSettings);
        if (cardSettings != null) {
            cardSettings.setOnClickListener(v -> {
                startActivity(new Intent(activity, SettingsActivity.class));
            });
        }
        
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cardEnableKeyboard != null) {
            if (isKeyboardEnabled()) {
                cardEnableKeyboard.setVisibility(View.GONE);
            } else {
                cardEnableKeyboard.setVisibility(View.VISIBLE);
            }
        }
    }

    private boolean isKeyboardEnabled() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            java.util.List<android.view.inputmethod.InputMethodInfo> imes = imm.getEnabledInputMethodList();
            for (android.view.inputmethod.InputMethodInfo ime : imes) {
                if (ime.getPackageName().equals(getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }
}

