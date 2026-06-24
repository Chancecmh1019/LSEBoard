package com.lseboard.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import com.lseboard.app.Util.SharedPrefHelper;

/**
 * 設定頁面 - Material 3 2026 設計
 * 提供進階設定選項
 */
public class SettingsActivity extends AppCompatActivity {

    private View rootView;
    private MaterialSwitch switchHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 啟用 Material 3 動態顏色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this);
        }
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        rootView = findViewById(android.R.id.content);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        setupSwitches();
        setupCards();
    }

    private void setupSwitches() {
        // 歷史紀錄開關
        switchHistory = findViewById(R.id.switchHistory);
        if (switchHistory != null) {
            // getDisableHistory==true 表示已關閉，Switch 應為 OFF
            switchHistory.setChecked(!SharedPrefHelper.getDisableHistory(this));
            switchHistory.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // isChecked=true 表示「啟用歷史紀錄」，反過來 disable=false
                SharedPrefHelper.setDisableHistory(this, !isChecked);

                // 廣播讓鍵盤立即重建，反映最新設定
                Intent refreshIntent = new Intent(FetchService.BROADCAST_ACTION);
                refreshIntent.putExtra("message", "refresh");
                LocalBroadcastManager.getInstance(this).sendBroadcast(refreshIntent);

                String msg = isChecked ? "歷史紀錄已啟用" : "歷史紀錄已關閉";
                Snackbar.make(rootView, msg, Snackbar.LENGTH_SHORT).show();
            });
        }
    }

    private void setupCards() {
        // 清除快取
        MaterialCardView cardClearCache = findViewById(R.id.cardClearCache);
        if (cardClearCache != null) {
            cardClearCache.setOnClickListener(v -> showClearCacheDialog());
        }

        // 權限設定
        MaterialCardView cardPermissions = findViewById(R.id.cardPermissions);
        if (cardPermissions != null) {
            cardPermissions.setOnClickListener(v -> openAppSettings());
        }
    }

    private void showClearCacheDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings)
                .setMessage("確定要清除所有快取嗎？這將釋放儲存空間，但下次載入貼圖時可能需要更多時間。")
                .setPositiveButton(R.string.positive_confirm, (dialog, which) -> {
                    // TODO: 實作清除快取功能
                    clearCache();
                })
                .setNegativeButton(R.string.negative_cancel, null)
                .show();
    }

    private void clearCache() {
        try {
            // 清除 Glide 快取
            new Thread(() -> {
                com.bumptech.glide.Glide.get(this).clearDiskCache();
                runOnUiThread(() -> {
                    Snackbar.make(rootView, "快取已清除", Snackbar.LENGTH_SHORT).show();
                });
            }).start();
            
            // 清除記憶體快取
            com.bumptech.glide.Glide.get(this).clearMemory();
        } catch (Exception e) {
            Snackbar.make(rootView, "清除快取失敗", Snackbar.LENGTH_SHORT).show();
        }
    }



    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }
}

